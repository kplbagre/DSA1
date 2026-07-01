# Authentication & Authorization — Who Are You? What Can You Do?

> Authentication answers "who are you?" (login, verify identity). Authorization answers "what are you allowed to do?" (which resources can this user access?). At SDE 3: you must know OAuth 2.0 flow, JWT tokens, role-based access control (RBAC), and how auth/authz layer at gateway vs service.

---

## 🎯 Why This Matters

Your system has orders, users, payments. User A can see their own orders but not User B's. Admin can delete orders; customers cannot. Authentication ensures User A is really User A (not an imposter). Authorization ensures User A can only access User A's data. Both are critical security layers. In interviews, candidates conflate them; you'll explain the separation and describe a complete OAuth 2.0 flow.

---

## 🧠 The Mental Model

Imagine an airport. Two security checkpoints:

**Authentication (at entry gate):**
- You show your passport. Agent checks: is this your photo? Is the passport real? ✅ Yes, you're really you.
- Agent stamps a boarding pass (JWT token) with your name, flight, seat.

**Authorization (at each gate):**
- You try to board. Gate agent checks your boarding pass.
- Is your name on this flight? ✅ Yes. Is your seat valid? ✅ Yes.
- You board.
- Someone else tries to board with YOUR pass. ❌ Photo doesn't match. Denied.

**The key insight:** Authentication happens ONCE (at entry). Authorization happens MANY times (each resource access). Authentication = identity verification. Authorization = permission check.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Authentication** | verifying identity — "are you who you claim to be?" | user submits username + password → system verifies → "yes, this is Alice" |
| **Authorization** | verifying permissions — "are you allowed to do this?" | Alice is authenticated, but can she delete Order #99? Check her role → no |
| **JWT (JSON Web Token)** | self-contained signed token encoding user identity and claims; stateless — no DB lookup needed to validate | `Header.Payload.Signature` — payload contains `{user_id, role, exp}` |
| **OAuth 2.0** | authorization framework for delegating access; lets a user grant a third party limited access to their resources | "Sign in with Google" → Google issues access token → your app uses it |
| **Access Token** | short-lived token (minutes to hours) proving the user is authenticated; sent with every API request | `Authorization: Bearer eyJhbGci...` in HTTP header |
| **Refresh Token** | long-lived token (days to weeks) used only to get a new access token when the old one expires | stored securely (httpOnly cookie); sent to `/auth/refresh` when access token expires |
| **RBAC (Role-Based Access Control)** | permissions assigned to roles, roles assigned to users; simple and scalable | `ADMIN` role can DELETE; `USER` role can READ; assign role to user |
| **ABAC (Attribute-Based Access Control)** | permissions based on attributes of user, resource, and environment; fine-grained | `user.dept='finance' AND resource.classification='financial' AND time.hour<18` |
| **HMAC Signature** | JWT signature computed as `HMAC-SHA256(header + payload, secret)`; any tampering invalidates the signature | server re-computes signature; if it doesn't match the token's → reject `401` |
| **Scopes** | OAuth 2.0 named permissions granted to a token; limits what the token can do | `scope: read:orders write:cart` — token can read orders but not delete them |

---

## 🎨 Visual — Auth/Authz in System Architecture

### Full System Topology — Where Auth Happens

```
INTERNET / CLIENT
    ↓
    ┌─────────────────────────────────────────────────────────┐
    │ API GATEWAY — AUTHENTICATION CHECKPOINT                 │
    │ ┌───────────────────────────────────────────────────┐   │
    │ │ [Login Request] → [Hash Check] → [Issue JWT]     │   │
    │ │ credentials (username+password)                   │   │
    │ │      ↓                                             │   │
    │ │ [Hash stored in DB] == [Hash of submitted pwd]?  │   │
    │ │ ✅ Yes → create JWT token, return to client      │   │
    │ │ ❌ No  → 401 Unauthorized                         │   │
    │ └───────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────┘
    ↓ (subsequent requests: send JWT in header)
    ┌─────────────────────────────────────────────────────────┐
    │ API GATEWAY — TOKEN VALIDATION                          │
    │ ┌───────────────────────────────────────────────────┐   │
    │ │ [Verify JWT signature] [Check expiry]            │   │
    │ │ ✅ Valid → extract user_id, pass to service      │   │
    │ │ ❌ Invalid/expired → 401 Unauthorized             │   │
    │ └───────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────┘
    ↓ (forward request with X-User-ID header)
    ┌─────────────────────────────────────────────────────────┐
    │ SERVICE LAYER — AUTHORIZATION CHECKPOINT                │
    │ ┌───────────────────────────────────────────────────┐   │
    │ │ [Check Permissions] [Validate Resource Owner]    │   │
    │ │ Can user_123 access order_456?                   │   │
    │ │ ✅ Yes (is owner or admin) → return resource     │   │
    │ │ ❌ No (not owner, not admin) → 403 Forbidden     │   │
    │ └───────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────┘
    ↓
    DATABASE (returns only authorized data)

KEY INVARIANT:
   Authentication happens at gateway (centralized, once per user session).
   Authorization happens at service layer (fine-grained, per resource).
   Token carries identity; service checks permissions.
   Gateway rejects unauthenticated; service rejects unauthorized.
```

### Component Detail — Auth & Authz Flows

```
AUTHENTICATION FLOW (Login):
┌──────────────────────────────────┐
│ Client: POST /auth/login         │
│ Body: {username, password}       │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ Server: Query user table         │
│ SELECT password_hash FROM users  │
│ WHERE username = ?               │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ Hash submitted password           │
│ bcrypt(submitted_password) = ?   │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ Compare hashes:                   │
│ db_hash == computed_hash ?        │
│ ✅ Match → authentication OK      │
│ ❌ No match → authentication fail │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ Create JWT Token                 │
│ Header: {alg: "RS256", typ: "JWT"}
│ Payload: {                        │
│   sub: user_123,                 │
│   email: "user@example.com",     │
│   roles: ["user", "premium"],    │
│   exp: 1719345600,               │
│   iat: 1719259200                │
│ }                                │
│ Signature: HMAC(header.payload)  │
│ Result: header.payload.signature │
└──────────────────────────────────┘
    ↓
Return JWT to Client


AUTHORIZATION FLOW (Resource Access):
┌──────────────────────────────────┐
│ Client: GET /orders/456           │
│ Header: Authorization: Bearer JWT │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ Service: Extract JWT from header │
│ Verify signature using public key │
│ ✅ Valid signature                │
│ Check expiry (exp claim)          │
│ ✅ Not expired                    │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ Extract claims from JWT:          │
│ user_id = 123                     │
│ roles = ["user", "premium"]       │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ SERVICE CHECKS AUTHORIZATION      │
│ Question: Can user_123 view       │
│ order_456?                        │
│                                   │
│ Query: SELECT user_id FROM orders│
│ WHERE id = 456                    │
│ Result: owner_user_id = 123       │
│                                   │
│ Is user_123 == owner_123 OR       │
│ does user_123 have admin role?    │
│ ✅ Yes (is owner) → return order  │
│ ❌ No → 403 Forbidden             │
└──────────────────────────────────┘

PERMISSION TABLE EXAMPLE (RBAC):
┌─────────────┬─────────┬─────────┬──────────┐
│ Resource    │ Guest   │ User    │ Admin    │
├─────────────┼─────────┼─────────┼──────────┤
│ GET /orders │ ❌      │ own     │ all      │
│ POST /orders│ ❌      │ ✅      │ ✅       │
│ DELETE /ord │ ❌      │ own     │ all      │
│ DELETE /usr │ ❌      │ ❌      │ ✅       │
└─────────────┴─────────┴─────────┴──────────┘

JWT STRUCTURE EXAMPLE:
┌──────────────────────────────────────────────────────────┐
│ eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.                │
│ (Header: algorithm, token type)                          │
│ eyJzdWIiOiIxMjMiLCJuYW1lIjoiSm9obiIsImFkbSI6dHJ1ZX0.│
│ (Payload: subject, name, admin claim)                    │
│ SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c           │
│ (Signature: HMAC hash of header.payload)                 │
│                                                          │
│ KEY INVARIANT:                                           │
│ Only server (auth service) knows private key.            │
│ No one can forge valid signature (can't create JWT).     │
│ Anyone with public key can verify signature.             │
└──────────────────────────────────────────────────────────┘
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Client submits credentials** (username, password) to `/auth/login`.
2. **Server hashes the password** using bcrypt.
3. **Server queries DB** for stored password hash.
4. **Server compares hashes** — if match, create JWT token.
5. **JWT contains claims** (user_id, roles, expiry time).
6. **Server signs JWT** with private key (RS256 asymmetric).
7. **Server returns JWT** to client.
8. **Client stores JWT** (in memory, localStorage, or httpOnly cookie).
9. **Client includes JWT in subsequent requests** (Authorization header).
10. **Server validates JWT signature** using public key (proves it's authentic).
11. **Server checks authorization** — does user have permission for this resource?

```java
// Authentication Service (Login)

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder; // bcrypt
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // Step 1-2 — Login endpoint
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        // Step 1 — Client submits credentials
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        // Step 3 — Query DB for user
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new AuthenticationException("User not found"));

        // Step 2 + 4 — Hash submitted password, compare with stored hash
        boolean isPasswordValid = passwordEncoder.matches(password, user.getPasswordHash());
        if (!isPasswordValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid password");
        }

        // Step 5-7 — Create and sign JWT
        String token = jwtTokenProvider.generateToken(user);

        // Step 7 — Return JWT to client
        return ResponseEntity.ok(new LoginResponse(token));
    }
}

// JWT Token Provider
@Component
public class JwtTokenProvider {
    private final String secretKey; // private key (loaded from secure vault)
    private final long expirationTime = 3600000; // 1 hour in milliseconds

    // Step 5-7 — Generate JWT token
    public String generateToken(User user) {
        // Step 5 — Create claims (payload)
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", user.getId());
        claims.put("username", user.getUsername());
        claims.put("roles", user.getRoles()); // Step 5 — include roles for authz

        long now = System.currentTimeMillis();
        long expiresAt = now + expirationTime;

        // Step 6 — Sign JWT with private key (RS256)
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(user.getId().toString())
            .setIssuedAt(new Date(now))
            .setExpiration(new Date(expiresAt))
            .signWith(SignatureAlgorithm.HS256, secretKey) // sign with private key
            .compact(); // Step 7 — serialize to string
    }

    // Step 10 — Validate JWT signature
    public boolean validateToken(String token) {
        try {
            // Verify signature using public key (embedded in JWT)
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            // Signature mismatch — token forged or tampered
            return false;
        } catch (ExpiredJwtException e) {
            // Token expired
            return false;
        }
    }

    // Extract claims from token
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }
}

// Authorization Filter (at Service Layer)
@Component
public class AuthorizationFilter implements WebFilter {
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Step 9 — Extract JWT from request header
        String token = extractToken(exchange.getRequest());

        if (token == null) {
            return unauthorizedResponse(exchange, "No token provided");
        }

        // Step 10 — Validate JWT signature
        if (!jwtTokenProvider.validateToken(token)) {
            return unauthorizedResponse(exchange, "Invalid or expired token");
        }

        // Step 10 — Extract claims
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);
        String userId = claims.getSubject();
        List<String> roles = (List<String>) claims.get("roles");

        // Step 11 — Check authorization (can user access this resource?)
        String requestPath = exchange.getRequest().getPath().value();
        String httpMethod = exchange.getRequest().getMethod().toString();

        boolean isAuthorized = authorizationService.checkPermission(
            userId, roles, requestPath, httpMethod);

        if (!isAuthorized) {
            return forbiddenResponse(exchange, "Access denied");
        }

        // Pass to next filter with user context
        return chain.filter(exchange);
    }

    private String extractToken(ServerHttpRequest request) {
        // Step 9 — Extract from Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(message.getBytes())));
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(message.getBytes())));
    }
}

// Authorization Service (RBAC)
@Service
public class AuthorizationService {
    // Step 11 — Permission table
    private static final Map<String, Map<String, Set<String>>> PERMISSIONS = Map.of(
        "GET /orders", Map.of(
            "admin", Set.of("*"), // admin can view all
            "user", Set.of("own") // user can view own
        ),
        "DELETE /orders/{id}", Map.of(
            "admin", Set.of("*"),
            "user", Set.of("own")
        ),
        "DELETE /users/{id}", Map.of(
            "admin", Set.of("*")
            // user role not in map = forbidden
        )
    );

    // Step 11 — Check if user has permission
    public boolean checkPermission(String userId, List<String> roles, 
            String path, String method) {
        String resource = method + " " + path;

        // Check if resource is protected
        if (!PERMISSIONS.containsKey(resource)) {
            return true; // Public resource
        }

        Map<String, Set<String>> rolePermissions = PERMISSIONS.get(resource);

        // Check if any of user's roles have permission
        for (String role : roles) {
            if (rolePermissions.containsKey(role)) {
                Set<String> permissions = rolePermissions.get(role);
                if (permissions.contains("*")) {
                    return true; // Admin wildcard
                }
                if (permissions.contains("own")) {
                    // Check if user is owner of resource
                    return isResourceOwner(userId, path);
                }
            }
        }

        return false; // No permission
    }

    private boolean isResourceOwner(String userId, String path) {
        // Extract resource ID from path (e.g., /orders/456 → 456)
        String resourceId = path.replaceAll(".*/(\\d+).*", "$1");
        // Query DB: does userId own this resource?
        // Simplified: return true if owner
        return true; // placeholder
    }
}
```

### What is bcrypt, and why does it fit here?

Bcrypt is a **password hashing algorithm** designed to be slow and resistant to brute-force attacks. Unlike simple MD5 hashing (fast, vulnerable), bcrypt deliberately takes milliseconds per hash, making it infeasible to try millions of password guesses. In an interview, if asked: *"Bcrypt is a key-derivation function that hashes passwords with a salt and work factor. It's intentionally slow — takes ~100ms per hash — so an attacker brute-forcing passwords would need years to crack a single account. We never store plain passwords; only bcrypt hashes."*

### What is RS256, and why does it fit here?

RS256 is **asymmetric signing** (RSA with SHA-256). Server signs JWT with private key; client/services verify with public key. Server is the only one who can create valid JWTs (only one with private key). Everyone else can verify but not forge. In an interview, if asked: *"RS256 uses public-key cryptography. The auth server has the private key and signs tokens. Other services have the public key and can verify signatures but can't forge them. This means any service can independently validate a token without calling the auth server."*

---

## 🏢 Real World — Where Companies Use This

- **Google (OAuth 2.0 provider):** Users login to Google, receive authorization token. Applications can request permission to access Google services (Gmail, Drive). Scope system: "I need read-only access to emails" or "I need read-write access to calendar."
- **Stripe (API key + RBAC):** Different API keys for different access levels. Restricted key: can only charge payments. Unrestricted key: can delete customers, refund charges. Dashboard: users assign roles to team members (admin, viewer, editor).
- **Netflix (JWT + role-based features):** User JWT contains country, subscription tier, device info. Backend checks: can this device play this title in this region? Is subscription tier high enough for 4K?
- **AWS (IAM policies):** Users assume roles with attached policies. Policy: "Allow s3:GetObject on bucket/my-data/*" (fine-grained resource authorization). Principle: least privilege (grant only what's needed).
- **Facebook (OAuth + permission system):** Apps request permissions: "access your friends list", "read-write your posts". User grants/revokes. Platform checks: can this app access this data?

---

## 🧭 When to Use vs When NOT to Use

| Use Auth/Authz when | Do NOT use when |
|---|---|
| You have user accounts and role-based access | Public, read-only API (no auth needed) |
| You need fine-grained permissions per resource | Simple binary (authenticated or not) |
| You have multiple services needing to validate tokens | Monolith with centralized authorization check |
| You want delegation (OAuth 2.0 for third-party apps) | Internal-only system (no public API) |
| You need audit trails (who accessed what, when) | High-frequency operations (<1% request overhead) |

**The common mistake:** Storing plain passwords (never). Over-checking authorization (do it once at gateway if possible, not at every service). Not implementing token refresh (tokens stored forever = security risk).

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Fine-grained access control (per-user, per-resource). Separation of duties (auth service separate). Tokens reduce DB queries (validate signature, not query user table). OAuth 2.0 delegation (third-party apps without sharing passwords). Audit trail (who accessed what, when). |
| **You lose** | Added latency (password hashing slow, token validation has overhead). Complexity (multiple auth strategies: session, JWT, OAuth). Token revocation challenging (JWT valid until expiry; blacklist list adds complexity). Private key management (lose private key = can't sign tokens). |
| **Failure mode** | Auth service down → can't login or validate tokens (unless service tokens cached locally). Token leaked → attacker can impersonate user until expiry. Private key compromised → attacker can forge any token. Weak password policy → users choose "password123", easily guessed. Mitigation: auth service high availability + monitoring, token expiry short (1 hour), refresh token rotation, enforce strong passwords (12+ chars, complexity), store private key in HSM (hardware security module). |

---

## 🔬 Interview Q&As

### Q: "Why not just store plain passwords in the database?"

> If DB is breached, attacker sees all passwords in plain text. With bcrypt hashing, even if DB is exposed, attacker must hash-crack each password (infeasible — bcrypt takes 100ms per guess). Additionally, bcrypt uses a salt (unique per user) to prevent rainbow table attacks (pre-computed hash tables). Rule: never store plain text; always hash with salt. ⭐ **Tier 2 — Security**

### Q: "You have a JWT token. Can you change the payload (e.g., change user_id from 123 to 456) and still use it?"

> No. JWT structure is header.payload.signature. If you change payload, signature becomes invalid because signature is HMAC(header.payload). Server verifies: hash(modified_header.payload) != original_signature → token rejected. This is why signature is critical: it binds payload to a specific key. Only server (with private key) can create valid signatures. ⭐ **Tier 2 — Security**

### Q: "How do you handle token expiry? Why not just make tokens valid forever?"

> Tokens valid forever = risk. If token is leaked, attacker can use it indefinitely. Solution: (1) make tokens expire quickly (1 hour). (2) Use refresh tokens (valid for 30 days) to get new access tokens. When user logs out or wants to revoke, invalidate refresh token. Old access tokens still valid for 1 hour, but refresh token is gone, so attacker can't get new ones. ⭐ **Tier 2 — Security**

### Q: "Service A needs to call Service B. How does Service B know the request is from Service A?"

> Service A includes its own JWT (signed with its private key). Service B verifies signature using Service A's public key. Alternatively, use service-to-service authentication: mTLS (mutual TLS certificates), API keys (Service A has a key registered with Service B), or OAuth 2.0 (Service A authenticates as "app" not "user"). ⭐ **Tier 2 — Microservices**

### Q: "You want to implement role-based access control (RBAC). Where does the permission check happen — at gateway or service?"

> Both. Gateway does coarse-grained checks (is user authenticated?). Service does fine-grained checks (can user_123 view order_456?). Reason: gateway doesn't know context (which order?), but service does. If authorization happens only at gateway, attackers might bypass it. If only at service, gateway doesn't provide early feedback. ⭐ **Tier 2 — Architecture**

### Q: "Your app has 1M users. On each request, you do `SELECT user FROM users WHERE id=?` to check permissions. That's 1M DB queries/sec. How do you optimize?"

> Use JWT tokens to avoid DB queries. Token contains user_id and roles. Server doesn't query DB; just validates signature. Roles are cached in token (set 1-hour expiry so role changes take max 1 hour to propagate). For real-time permissions, use a cache (Redis) or database read replicas. ⭐ **Tier 2 — Performance**

---

## 🧾 TL;DR

> "Authentication (who are you?) happens at gateway via password hashing + JWT. Authorization (what can you do?) happens at service via permission checks. JWT tokens carry claims (user_id, roles), signed with private key. Services verify signature using public key; can't forge tokens without private key."

---

## 🔗 Related Concepts

- **`24-api-gateway-pattern.md`** — gateway validates JWT at entry point
- **`25-monitoring-observability-fundamentals.md`** — log authorization failures, monitor auth service latency
- **`13-security-pki.md`** — JWT uses asymmetric crypto (RS256)
- **`15-system-qualities.md`** — security is one of the 7 qualities

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **OWASP — Authentication Cheat Sheet** | Comprehensive password storage, session management, MFA best practices | ~30 min read |
| **Auth0 Blog — OAuth 2.0 Explained** | Step-by-step OAuth flow with real-world examples (Google, GitHub login) | ~15 min |
| **JWT.io — Introduction to JWT** | JWT structure breakdown, signature verification, common pitfalls | ~10 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 27. Covered authentication (password hashing, JWT signing) vs authorization (permission checks, RBAC), two-diagram topology (gateway auth + service authz), bcrypt and RS256 asymmetric signing, code examples with Spring Security. |
