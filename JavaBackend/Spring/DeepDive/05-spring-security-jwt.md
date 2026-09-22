# 🌱 Spring Security + JWT — Deep Dive

> After this note you can trace a JWT-authenticated request through the Spring Security filter chain, write a modern `SecurityFilterChain` bean (Spring Security 6 — no `WebSecurityConfigurerAdapter`), and explain 3 strategies for revoking a stateless JWT.

---

## 🎯 The Problem This Solves

Your API is public. Anyone can call `DELETE /api/orders/42`. Without security, there's no identity (who is calling?), no access control (are they allowed?), and no audit trail (who did what?). Spring Security adds a chain of servlet filters that intercept every request BEFORE it reaches your controller — checking credentials, validating tokens, enforcing roles — and rejecting unauthorized requests with proper HTTP status codes (401 for "not authenticated", 403 for "not authorized").

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Authentication (AuthN)** | Verifying identity — "who are you?" Validates credentials (JWT, username/password). Failure → **HTTP 401**. |
| **Authorization (AuthZ)** | Verifying permissions — "are you allowed?" Checks roles/authorities after identity is established. Failure → **HTTP 403**. |
| **SecurityFilterChain** | The ordered chain of servlet filters Spring Security registers. Every HTTP request passes through this chain before reaching DispatcherServlet. |
| **SecurityContext** | Per-request holder of the authenticated user's details. Stored in `SecurityContextHolder` using a `ThreadLocal`. Cleared after each response. |
| **JWT (JSON Web Token)** | A compact, self-contained token: `header.payload.signature`. Base64-encoded (NOT encrypted). The signature ensures integrity — tampering is detectable. |
| **Bearer token** | A JWT sent in the `Authorization` header: `Authorization: Bearer <token>`. The server extracts, validates, and uses it to set the SecurityContext. |
| **HS256** | Symmetric signing — same secret key signs and verifies. Simple but secret must be shared with every verifier. |
| **RS256** | Asymmetric signing — private key signs, public key verifies. Verifiers never need the private key. Better for microservices. |

---

## 🧠 Mental Model

Spring Security is a **chain of bouncers at a nightclub door**. Each bouncer checks one thing. The JWT filter bouncer reads the `Authorization: Bearer <token>` header. If valid, it stamps the guest's hand (sets `SecurityContext`). The authorization bouncer checks the hand stamp against the VIP list (role requirements). The exception bouncer converts rejections into 401/403 responses. If the guest makes it through all bouncers, they reach the dance floor (your controller).

The SecurityContext is stored in a `ThreadLocal` — each request-handling thread has its own isolated copy. After the response, the filter chain clears it to prevent leaking to the next request on the same thread.

> If you can say "SecurityFilterChain is an ordered chain of servlet filters; JWT filter reads Bearer token → validates → sets SecurityContext (ThreadLocal); Spring Security 6 uses SecurityFilterChain @Bean — WebSecurityConfigurerAdapter is removed" without notes, you have Spring Security.

---

## 🎨 Visual — Request Flow Through Security

```
  HTTP Request: GET /api/orders/42
  Authorization: Bearer eyJhbG...
       │
       ▼
  ┌──────────────────────────────────────────────────┐
  │           SecurityFilterChain (ordered)           │
  │                                                   │
  │  1. SecurityContextPersistenceFilter              │
  │     → loads SecurityContext (empty for stateless)  │
  │                                                   │
  │  2. JwtAuthenticationFilter (YOUR custom filter)  │
  │     → extracts Bearer token from header           │
  │     → validates signature + expiry                │
  │     → sets Authentication in SecurityContext      │
  │                                                   │
  │  3. ExceptionTranslationFilter                    │
  │     → AuthenticationException → 401               │
  │     → AccessDeniedException  → 403                │
  │                                                   │
  │  4. AuthorizationFilter                           │
  │     → checks: does authenticated user have        │
  │       required role for this endpoint?             │
  └──────────────────────────────────────────────────┘
       │ (all filters passed)
       ▼
  DispatcherServlet → @RestController → response

KEY INVARIANT:
   Filters run in strict order. Earlier filters set up context
   for later filters. SecurityContext is ThreadLocal — one per
   request thread, cleared after response.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// ❌ Pre-Spring-Security: manual auth check in every controller method
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable Long id, HttpServletRequest request) {
    String token = request.getHeader("Authorization");
    if (token == null || !validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String role = extractRole(token);
    if (!"ADMIN".equals(role)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    return orderService.findById(id);
}
// Repeated in EVERY endpoint. One missed check = security hole.
```

Spring Security centralizes this: one filter chain handles auth for ALL endpoints. Controllers are clean — no security code.

---

### Level 2 — The real mechanism

#### 2.1 — Modern SecurityFilterChain config (Spring Security 6 / Spring Boot 3)

```java
// ⚠️ WebSecurityConfigurerAdapter was REMOVED in Spring Security 6.
// Writing it in an interview signals 3+ years behind.

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)   // disable CSRF for stateless APIs
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

**Key Spring Security 6 changes:**
- `authorizeHttpRequests()` replaces `authorizeRequests()`
- `requestMatchers()` replaces `antMatchers()` (removed)
- Lambda DSL (`.csrf(c -> c.disable())`) replaces method chaining
- Returns `SecurityFilterChain` as `@Bean` — no inheritance

#### 2.2 — Custom JWT filter

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;   // no token → let other filters handle (may result in 401)
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtUtil.extractUsername(token);

            if (username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails)) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            // Token expired — don't set context → 401 downstream
        }

        filterChain.doFilter(request, response);
    }
}
```

#### 2.3 — JWT structure and signing

```
  JWT = header.payload.signature

  header:    {"alg":"RS256","typ":"JWT"}    → base64url encoded
  payload:   {"sub":"user123",              → base64url encoded
              "iat":1727000000,                (NOT encrypted — anyone
              "exp":1727003600,                 can decode with base64)
              "roles":["USER","ADMIN"]}
  signature: RS256(base64(header) + "." + base64(payload), privateKey)

  The PAYLOAD is readable by anyone. NEVER put passwords or secrets in it.
  The SIGNATURE proves the token wasn't tampered with.
```

**HS256 vs RS256:**

| | HS256 (symmetric) | RS256 (asymmetric) |
|---|---|---|
| Signs with | Shared secret | Private key |
| Verifies with | Same shared secret | Public key |
| Risk | Every verifier has the secret → any compromise = total forgery | Only auth server has private key → compromise is contained |
| Use case | Monolith (one service signs + verifies) | Microservices (auth service signs, many services verify with public key) |

#### 2.4 — JWT revocation strategies

JWTs are stateless — you can't revoke them before expiry without re-introducing state.

**Strategy 1 — Short access + refresh token (standard):**
- Access token: 15-minute TTL. Even if stolen, damage window is short.
- Refresh token: 7-day TTL, stored server-side in DB.
- On logout: delete refresh token. Client can't get new access tokens.

**Strategy 2 — Blocklist (denylist):**
- Store revoked JTI (JWT ID) in Redis with TTL = remaining token lifetime.
- Every request checks Redis. Cost: one Redis lookup per request.

**Strategy 3 — Accept the trade-off (honest senior answer):**
- Short-lived tokens (15 min). On logout, clear client-side token.
- Accept 15-minute worst-case window. Statelessness preserved.

---

### Level 3 — The subtleties

#### 3.1 — 401 vs 403

```
  401 Unauthorized (actually means "not authenticated"):
  → No credentials provided, OR credentials invalid
  → Response should include: WWW-Authenticate: Bearer

  403 Forbidden:
  → Authenticated, but insufficient permissions
  → "You are logged in as USER, but this endpoint requires ADMIN"

  Common mistake: returning 401 for "access denied" when user IS authenticated. That's 403.
```

#### 3.2 — SecurityContext and ThreadLocal

```java
// Access the current user anywhere in the call chain:
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();
Collection<? extends GrantedAuthority> roles = auth.getAuthorities();

// ⚠️ ThreadLocal → each thread has its own copy
// Spring clears the context after each response via its filter chain.
// If you create your own threads (CompletableFuture, @Async),
// the SecurityContext is NOT propagated automatically.

// Fix for @Async: configure SecurityContextHolder strategy
SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
// Or pass context explicitly in async tasks
```

#### 3.3 — OAuth2 / OIDC basics

```
  OAuth2 is an AUTHORIZATION framework (not authentication).
  OIDC (OpenID Connect) adds AUTHENTICATION on top of OAuth2.

  Common flow (Authorization Code):
  1. User clicks "Login with Google"
  2. App redirects to Google's auth page
  3. User authenticates with Google
  4. Google redirects back with an authorization code
  5. App exchanges code for tokens (access + ID + refresh)
  6. App uses ID token for user identity, access token for API calls

  In Spring Boot:
  spring.security.oauth2.client.registration.google.client-id=xxx
  spring.security.oauth2.client.registration.google.client-secret=xxx
  → Spring Boot auto-configures the entire OAuth2 login flow
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "JWT payload is encrypted" | JWT payload is Base64-encoded — NOT encrypted. Anyone can decode it. Never put passwords, secrets, or sensitive PII in the payload. The SIGNATURE ensures integrity (tamper-proof), not confidentiality. |
| "JWT can be revoked like a session" | JWT is stateless — the server doesn't store it. You can't revoke it before expiry without re-introducing state (blocklist, short-lived + refresh pattern). |
| "`WebSecurityConfigurerAdapter` is the way to configure security" | It was REMOVED in Spring Security 6 (Spring Boot 3). The modern way: return a `SecurityFilterChain` as a `@Bean` using the lambda DSL. |
| "401 means 'not authorized'" | 401 means "not authenticated" (despite its name `Unauthorized`). 403 means "not authorized" (authenticated but insufficient permissions). |

---

## 🐞 Production Footguns

---

> **Footgun: SecurityContext not propagated to async threads**
> **Cost:** Silent authentication loss
>
> A service used `@Async` to send notifications after order processing. The async method tried to read `SecurityContextHolder.getContext().getAuthentication()` to log which user triggered the notification. It was null — `@Async` runs on a different thread, and `ThreadLocal` doesn't propagate to child threads by default. The notification was sent without audit attribution.

```java
// ❌ The trap: @Async loses SecurityContext
@Async
public void sendNotification(Long orderId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // auth is null — different thread, ThreadLocal not inherited
}

// ✅ The fix: propagate context explicitly
@Async
public void sendNotification(Long orderId, String username) {
    // Pass the username explicitly — don't rely on ThreadLocal in async contexts
}
// Or configure SecurityContextHolder.MODE_INHERITABLETHREADLOCAL at app startup
```

---

> **Footgun: Missing signature verification**
> **Cost:** Security vulnerability — token forgery
>
> A developer decoded the JWT payload (Base64) to extract the user ID but didn't verify the signature. An attacker crafted a JWT with `"role": "ADMIN"` in the payload, base64-encoded it, and sent it with any signature. The server accepted it — the payload looked valid because it was never verified against the signing key.

```java
// ❌ The trap: decoding without verifying
String payload = new String(Base64.getDecoder().decode(token.split("\\.")[1]));
JsonNode claims = objectMapper.readTree(payload);
String userId = claims.get("sub").asText();
// Signature NOT verified — attacker can put anything in the payload

// ✅ The fix: always verify signature before trusting claims
Claims claims = Jwts.parserBuilder()
    .setSigningKey(publicKey)
    .build()
    .parseClaimsJws(token)    // throws if signature is invalid or token is expired
    .getBody();
String userId = claims.getSubject();
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `01-web-servlet-foundation.md` | Spring Security's filter chain is built on the Servlet API's `Filter` interface. Each security filter is a servlet filter registered before DispatcherServlet. Understanding the servlet model explains how security intercepts requests before they reach controllers. |
| `02-spring-core.md` | `SecurityFilterChain` is configured as a `@Bean` — IoC container wires it. The JWT filter is a `@Component` — discovered by component scanning. AOP proxies can add method-level security (`@PreAuthorize`). |
| `03-spring-mvc-boot.md` | Spring Boot auto-configures security defaults when `spring-boot-starter-security` is on the classpath. `@ControllerAdvice` can handle security exceptions globally. |
| `../../DeepDive/exception-hierarchy.md` | `AuthenticationException` and `AccessDeniedException` are the two exception types Spring Security's `ExceptionTranslationFilter` maps to 401 and 403. Understanding exception hierarchy explains the mapping. |

---

## 🎙️ Interview Deep Questions

**Q1. How does Spring Security work? Walk me through the filter chain.**

> Spring Security registers a chain of servlet filters that run before DispatcherServlet. Each request passes through: `SecurityContextPersistenceFilter` (loads/saves security context), your custom `JwtAuthenticationFilter` (extracts Bearer token, validates, sets Authentication in SecurityContext), `ExceptionTranslationFilter` (converts `AuthenticationException` → 401 and `AccessDeniedException` → 403), and `AuthorizationFilter` (checks if the authenticated user has required roles). The SecurityContext is stored in a ThreadLocal via `SecurityContextHolder` — each request thread has its own copy. After the response, the context is cleared to prevent leaking between requests on thread pool reuse.

**Q2. What is the structure of a JWT? Is the payload encrypted?**

> A JWT has three base64url-encoded parts separated by dots: header (algorithm + type), payload (claims — sub, iat, exp, roles), and signature (HMAC or RSA over header + payload). The payload is NOT encrypted — anyone can base64-decode it and read the claims. Never put secrets or sensitive PII in the payload. The signature provides integrity, not confidentiality — if the payload is modified, the signature won't match. For confidentiality, use JWE (JSON Web Encryption) — a separate standard that encrypts the payload.

**Q3. How do you configure Spring Security in Spring Boot 3 / Spring Security 6?**

> Return a `SecurityFilterChain` as a `@Bean` using the lambda DSL. `WebSecurityConfigurerAdapter` was removed in Security 6. The config: disable CSRF for stateless APIs, set session policy to STATELESS, define authorization rules with `authorizeHttpRequests()` and `requestMatchers()` (not `antMatchers()` — removed), and add your JWT filter before `UsernamePasswordAuthenticationFilter`. The key difference from pre-6: no inheritance, everything is a bean, and the lambda DSL replaces method chaining.

**Q4. How do you revoke a JWT before it expires?**

> You can't — not without re-introducing state. The standard pattern: short-lived access tokens (15 min) + server-stored refresh tokens. On logout, delete the refresh token from the database. When the client tries to refresh, it's denied → forced re-login. For immediate revocation (compromised token), store the JTI (JWT ID) in a Redis blocklist with TTL = remaining token lifetime. Every request checks the blocklist — one Redis lookup per request. The honest answer: you pick based on security requirements vs latency budget. Most CRUD APIs use short-lived + refresh.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Spring Security adds a filter chain before DispatcherServlet. Each filter handles one concern: token validation, exception translation, authorization. SecurityContext stores the authenticated user in a ThreadLocal per request thread.
>
> **Part 2 — How/Why (30s):** In Spring Security 6, configure via `SecurityFilterChain` @Bean (no `WebSecurityConfigurerAdapter` — removed). For JWT: a custom `OncePerRequestFilter` extracts the Bearer token, validates the signature and expiry, and sets `Authentication` in the SecurityContext. Downstream filters check roles via `authorizeHttpRequests()`. JWT is stateless — payload is base64 (not encrypted), signature ensures integrity. HS256 for monoliths (symmetric), RS256 for microservices (asymmetric).
>
> **Part 3 — Gotcha (20s):** Two traps: SecurityContext doesn't propagate to `@Async` threads (ThreadLocal is per-thread — pass user info explicitly). And JWT can't be revoked before expiry without state — use short-lived access tokens (15 min) + server-stored refresh tokens. On logout, delete the refresh token.

---

## 🧾 TL;DR

- **SecurityFilterChain** = ordered servlet filters before DispatcherServlet. Configured as `@Bean` in Security 6.
- **`WebSecurityConfigurerAdapter` REMOVED** in Spring Security 6 / Boot 3. Don't use it.
- **JWT payload = Base64, NOT encrypted.** Signature ensures integrity. Never put secrets in payload.
- **HS256** = symmetric (shared secret). **RS256** = asymmetric (private signs, public verifies — microservices).
- **401 = not authenticated.** **403 = not authorized** (authenticated but no permission).
- **JWT revocation:** short-lived access (15 min) + server-stored refresh token. Or Redis blocklist.
- **SecurityContext** = ThreadLocal. Doesn't propagate to @Async threads.
- **OAuth2** = authorization. **OIDC** = authentication on top of OAuth2.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #14 (Phase 3) of the JavaBackend KB completion roadmap. Covers: SecurityFilterChain modern config (Spring Security 6 lambda DSL), custom JWT filter (OncePerRequestFilter), JWT structure (header/payload/signature), HS256 vs RS256, JWT revocation (3 strategies), 401 vs 403 distinction, SecurityContext ThreadLocal (async propagation trap), OAuth2/OIDC basics. Follows DeepDive standards (not Spring 8-section arc). Two production footguns: SecurityContext lost in @Async, missing signature verification. |
