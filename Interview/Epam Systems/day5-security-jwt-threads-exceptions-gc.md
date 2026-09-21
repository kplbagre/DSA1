# Day 5 — Security · JWT · Multithreading · Exception Handling · GC Deep-Dive
### EPAM Interview Prep · Sep 21, 2026

> **Who this is for:** A developer who uses Spring Security and JWT in production but hasn't mapped the internals, knows `synchronized` but hasn't explained `ReentrantLock` vs `CountDownLatch` vs `CyclicBarrier` under pressure, and hasn't thought deeply about what happens when two things throw at the same time.

> **What you will be able to do after this:** Explain the Spring Security filter chain and write modern Security config (Spring Boot 3 API), answer "how do you revoke a JWT" without freezing, distinguish CountDownLatch from CyclicBarrier, write a ThreadPoolExecutor with a bounded queue, name every class in the exception hierarchy, and explain GC roots and generational collection without re-reading Day 4.

---

## 🧾 Index — Jump to Any Section

| # | Topic | Time |
| --- | --- | --- |
| [1. Spring Security — Filter Chain + Modern Config](#spring-security) | SecurityFilterChain, filter order, AuthN vs AuthZ | ~45 min |
| [2. JWT — Structure + Signing + Revocation](#jwt) | Header/Payload/Signature, HS256 vs RS256, revocation strategies | ~45 min |
| [3. Multithreading — Deep-Dive](#multithreading) | ReentrantLock, ExecutorService, CountDownLatch vs CyclicBarrier, ThreadLocal | ~60 min |
| [4. Exception Handling — Hierarchy + Patterns](#exceptions) | Throwable tree, try-with-resources + suppressed, @ControllerAdvice | ~40 min |
| [5. GC — Deep-Dive (Beyond Day 4)](#gc) | GC roots, generational hypothesis, minor/major/full GC, mark-sweep-compact | ~30 min |
| [6. Interview Q&A](#qa) | EPAM-asked + Commonly asked | bonus |

> **Cross-references:** volatile/synchronized/ConcurrentHashMap → `day1-java-collections-streams-concurrency.md`. GC types table (Serial/Parallel/G1/ZGC) → `day4-dsa-jvm-oop.md`. @Transactional rollback rules → `day2-spring-traps.md`.

---

<a id="spring-security"></a>

## 🔹 1. Spring Security — Filter Chain + Modern Config

### 📖 Terminology

- **Authentication (AuthN)** — verifying *who you are*. "Is this person who they claim to be?" Result: an identity. HTTP status on failure: **401 Unauthorized** (misleading name — it actually means "not authenticated").
- **Authorization (AuthZ)** — verifying *what you're allowed to do*. "Is this authenticated user allowed to access this resource?" HTTP status on failure: **403 Forbidden**.
- **SecurityFilterChain** — the ordered chain of servlet filters Spring Security registers. Every HTTP request passes through this chain before reaching your controller.
- **SecurityContext** — the per-request (per-thread) holder of the currently authenticated user's details. Stored in `SecurityContextHolder`, which uses a `ThreadLocal` internally.

---

### 🧠 Mental Model — Security as a Bouncer Chain

Imagine a nightclub with a chain of bouncers at the door. Each bouncer checks one thing. If any bouncer rejects the person, they don't reach the dance floor (your controller). If they make it through all bouncers, they're authenticated and authorized.

The bouncers are the **security filters**. They run in a fixed order. `JwtAuthenticationFilter` reads the Bearer token. `UsernamePasswordAuthenticationFilter` handles form login. `ExceptionTranslationFilter` converts `AccessDeniedException` → 403 and `AuthenticationException` → 401.

---

### 🎨 Visual — Spring Security Request Flow

```
  HTTP Request
       │
       ▼
  ┌────────────────────────────────────────────────────────┐
  │             SecurityFilterChain (ordered)              │
  │                                                        │
  │  1. SecurityContextPersistenceFilter                   │
  │     → loads SecurityContext from session (if any)      │
  │                                                        │
  │  2. JwtAuthenticationFilter (your custom filter)       │
  │     → reads "Authorization: Bearer <token>"            │
  │     → validates token → sets Authentication in context │
  │                                                        │
  │  3. UsernamePasswordAuthenticationFilter               │
  │     → handles form login (POST /login)                 │
  │                                                        │
  │  4. ExceptionTranslationFilter                         │
  │     → AuthenticationException → 401                   │
  │     → AccessDeniedException    → 403                   │
  │                                                        │
  │  5. FilterSecurityInterceptor                          │
  │     → checks if authenticated user has required role   │
  └────────────────────────────────────────────────────────┘
       │
       ▼
  DispatcherServlet → @Controller

KEY INVARIANT:
   Filters run in strict order — earlier filters set up context for later filters.
   The SecurityContext is stored in a ThreadLocal — one per request-thread.
   After the response, the context is cleared (prevents leaking between requests).
```

---

### ⚠️ Spring Security 6 Config — The #1 Trap

`WebSecurityConfigurerAdapter` was **deprecated in Spring Security 5.7** and **removed in Spring Security 6 (Spring Boot 3)**. Writing it in a 2026 EPAM interview signals you haven't touched production Spring in 3 years.

**❌ OLD (Spring Security 5, do NOT write):**

```java
// ❌ WebSecurityConfigurerAdapter is REMOVED in Spring Security 6
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .antMatchers("/public/**").permitAll()
            .anyRequest().authenticated();
    }
}
```

**✅ MODERN (Spring Security 6 / Spring Boot 3 — write this):**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Return a SecurityFilterChain bean — no extends, no override
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)          // disable CSRF for stateless APIs
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
```

**Key differences:**
- Method reference lambda DSL (`.authorizeHttpRequests()` not `.authorizeRequests()`)
- `requestMatchers()` not `antMatchers()` (removed in 6)
- Returns `SecurityFilterChain` as a `@Bean` — no inheritance

---

### 🔬 Custom JWT Filter — How to Wire It

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Only process if Bearer token is present
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);   // strip "Bearer "
        String username = jwtUtil.extractUsername(token);

        // Only set context if not already authenticated
        if (username != null &&
            SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                    );
                authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );
                // Set the authentication in the context — downstream filters see it
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

---

<a id="jwt"></a>

## 🔹 2. JWT — Structure + Signing + Revocation

### 📖 Terminology

- **JWT (JSON Web Token)** — a compact, self-contained token format for transmitting claims (assertions about a user) between parties. Pronounced "jot."
- **Claim** — a key-value pair in the JWT payload: `sub` (subject/user ID), `iat` (issued at), `exp` (expiry timestamp), `roles` (custom claim).
- **HS256** — HMAC with SHA-256, a symmetric signing algorithm: the same secret key signs the token and verifies it. Simpler but the secret must be shared.
- **RS256** — RSA with SHA-256, an asymmetric algorithm: a private key signs the token; a public key verifies it. The verifier never needs the private key — safer for microservices.

---

### 🎨 Visual — JWT Structure

```
  A JWT has exactly 3 parts, separated by dots:

  eyJhbGciOiJIUzI1NiJ9  .  eyJzdWIiOiJ1c2VyMTIzIn0  .  SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
  ──────────────────────    ───────────────────────────    ─────────────────────────────────────────────
         HEADER                      PAYLOAD                          SIGNATURE
  {"alg":"HS256",           {"sub":"user123",             HMAC_SHA256(
   "typ":"JWT"}              "iat":1727000000,               base64url(header) + "." +
                             "exp":1727003600,               base64url(payload),
                             "roles":["USER"]}               secret_key
                                                           )

  ⚠️  THE PAYLOAD IS BASE64URL-ENCODED, NOT ENCRYPTED.
      Anyone can decode it with a base64 decoder.
      NEVER put passwords, secrets, or PII in the payload.

  The SIGNATURE protects integrity — it proves the token was not tampered with.
  If payload is modified, the signature no longer matches → token rejected.

KEY INVARIANT:
   JWT = proof of identity (via signature) + readable claims (via payload).
   It is NOT secret. It IS tamper-proof.
   Encryption requires a separate step (JWE — JSON Web Encryption).
```

---

### 🔬 HS256 vs RS256 — When to Use Each

```
  HS256 (symmetric):
  ┌──────────────┐  signs with secret   ┌──────────────┐
  │  Auth Server │ ───────────────────► │     Token    │
  └──────────────┘                      └──────────────┘
         │                                      │
         │  same secret                         │ same secret
         ▼                                      ▼
  ┌──────────────┐  verifies with secret ┌──────────────┐
  │ Resource     │ ◄─────────────────── │   Verified   │
  │ Server       │                      └──────────────┘
  └──────────────┘

  Problem: every service that needs to verify tokens needs the secret.
  If any service is compromised, the secret leaks → all tokens can be forged.

  RS256 (asymmetric):
  Auth Server: signs with PRIVATE key  ← keep this secret
  Resource Servers: verify with PUBLIC key ← share freely

  Any service can verify tokens without being able to forge them.
  This is why RS256 is the right choice for microservices.
```

---

### 🔬 The Senior Question — "How Do You Revoke a JWT?"

> This is asked at every senior EPAM round that touches security. The answer is non-obvious.

A JWT is stateless — you cannot revoke it before its `exp` (expiration) without re-introducing state. The signature is valid for the token's lifetime regardless of what happens on the server (user logs out, password changes, account is banned).

**Strategy 1 — Short-lived access tokens + refresh tokens (standard pattern):**

```
  Access token: expires in 15 minutes → even if stolen, damage window is short
  Refresh token: long-lived (7 days), stored server-side in DB
  
  On logout: delete the refresh token from DB
  On refresh: if refresh token not in DB → deny + force re-login
  
  This is the industry standard. Short-lived access token = stateless.
  Refresh token = the one stateful piece that enables revocation.
```

**Strategy 2 — Blocklist (denylist):**

```
  Store revoked JTI (JWT ID — a unique claim per token) in Redis.
  On every request, check: is this token's JTI in the blocklist?
  
  Con: every request hits Redis → latency + Redis becomes a SPOF.
  Better than nothing for high-security scenarios.
  Practical only when combined with short-lived tokens (blocklist entries
  expire when the token would have expired anyway).
```

**Strategy 3 — Short expiry (accept the trade-off):**

> "In our system we set access tokens to 15-minute TTL. On logout we clear the client-side token. The server doesn't revoke — there's a 15-minute worst-case window where a stolen token is still valid. We accept this trade-off in exchange for statelessness."

This is an honest, senior answer. It shows you understand the trade-off rather than pretending JWT supports instant revocation.

---

### 🔬 AuthN vs AuthZ — Status Codes

```
  AuthN (Authentication — who are you?):
  → No credentials provided, or credentials invalid
  → HTTP 401 Unauthorized  ← counterintuitive name; actually means "not authenticated"
  → Should include: WWW-Authenticate: Bearer realm="api"

  AuthZ (Authorization — are you allowed?):
  → Authenticated, but insufficient permissions
  → HTTP 403 Forbidden
  → "You are authenticated as a USER, but this endpoint requires ADMIN"

  Common mistake: returning 401 for "access denied" when the user IS authenticated.
  That's a 403.
```

---

<a id="multithreading"></a>

## 🔹 3. Multithreading — Deep-Dive

> **Cross-reference:** volatile, synchronized, ConcurrentHashMap, CompletableFuture basics are in `day1-java-collections-streams-concurrency.md`. This section covers the senior-level additions.

### 📖 Terminology

- **Monitor** — an object-level lock in Java. Every Java object has one. `synchronized` acquires the object's monitor.
- **Deadlock** — two threads each hold a lock the other needs. Both wait forever. Neither makes progress.
- **Livelock** — two threads keep changing state in response to each other, but neither makes progress (like two people stepping aside for each other in a hallway in the same direction repeatedly).
- **Starvation** — a thread is perpetually denied CPU time because higher-priority threads always run first.
- **ThreadLocal** — a variable where each thread has its own independent copy. No sharing, no synchronization needed. Used by Spring's `SecurityContextHolder` to store the current user per request-thread.

---

### 🔬 ReentrantLock — Why Use It Over `synchronized`

`synchronized` is simple and auto-releases on exit (even on exception). Use it for most cases.

`ReentrantLock` adds capabilities `synchronized` doesn't have:

| Feature | synchronized | ReentrantLock |
| --- | --- | --- |
| Auto-release | ✅ Yes | ❌ Must call `unlock()` in `finally` |
| Try-lock without blocking | ❌ No | ✅ `tryLock()` / `tryLock(timeout)` |
| Interruptible lock wait | ❌ No | ✅ `lockInterruptibly()` |
| Fairness (FIFO order) | ❌ No guarantee | ✅ `new ReentrantLock(true)` |
| Multiple condition variables | ❌ One wait/notify | ✅ Multiple `Condition` objects |

```java
public class SafeCounter {

    private final ReentrantLock lock = new ReentrantLock();
    private int count = 0;

    public void increment() {
        lock.lock();   // acquire the lock
        try {
            count++;   // only one thread here at a time
        } finally {
            lock.unlock();   // ⭐ MUST be in finally — if count++ throws, lock must still release
        }
    }

    public boolean tryIncrement() {
        // tryLock returns false immediately if lock is held — no blocking
        if (lock.tryLock()) {
            try {
                count++;
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;   // could not acquire lock — do something else
    }
}
```

**⚠️ The footgun:** if you call `lock.lock()` and forget `unlock()` in a `finally`, the lock is held forever. Every subsequent thread blocks indefinitely. This is impossible with `synchronized` — the JVM always releases it.

---

### 🔬 `wait()` / `notify()` — The Rules

`wait()` and `notify()` are the low-level inter-thread communication mechanism (pre-Java-5). They must be called inside a `synchronized` block, otherwise `IllegalMonitorStateException` is thrown.

```java
class BoundedBuffer<T> {

    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }

    // Producer
    public synchronized void put(T item) throws InterruptedException {
        // ⭐ WHILE loop, not if — spurious wakeups can occur
        // (thread wakes without being notified, condition may still not hold)
        while (queue.size() == capacity) {
            wait();   // releases lock and suspends; re-acquires lock when notified
        }
        queue.add(item);
        notifyAll();   // wake all waiting consumers
    }

    // Consumer
    public synchronized T take() throws InterruptedException {
        // ⭐ WHILE loop for the same reason
        while (queue.isEmpty()) {
            wait();
        }
        T item = queue.poll();
        notifyAll();   // wake all waiting producers
        return item;
    }
}
```

**Why `while` not `if`:** Java allows spurious wakeups (a thread can wake from `wait()` without anyone calling `notify()`). If you use `if`, you check the condition once, proceed, and potentially crash. `while` re-checks after every wakeup — safe.

---

### 🔬 ExecutorService — The Senior Pattern

`Executors.newFixedThreadPool(10)` is fine for demos. In production, it has a critical problem:

```
  Executors.newFixedThreadPool(N) uses an unbounded LinkedBlockingQueue.
  If tasks come in faster than N threads can process them:
  → queue grows without bound
  → OutOfMemoryError
  → No rejection policy — tasks just pile up silently until the JVM crashes
```

**The production pattern — `ThreadPoolExecutor` with bounded queue:**

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    4,                              // corePoolSize: always keep this many threads alive
    8,                              // maximumPoolSize: burst up to this many
    60L, TimeUnit.SECONDS,          // keepAliveTime: idle threads above core die after this
    new ArrayBlockingQueue<>(100),  // bounded queue: rejects tasks when full
    new ThreadPoolExecutor.CallerRunsPolicy()  // rejection policy when queue full
    // CallerRunsPolicy: the submitting thread runs the task itself → natural backpressure
    // Other options: AbortPolicy (throws), DiscardPolicy (silent drop), DiscardOldestPolicy
);
```

**shutdown() vs shutdownNow():**

```java
// shutdown(): graceful — stop accepting new tasks, let running + queued tasks finish
executor.shutdown();
boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

// shutdownNow(): aggressive — stop accepting new tasks, interrupt running threads,
//                return list of queued tasks that were never started
List<Runnable> neverRan = executor.shutdownNow();
```

---

### 🔬 CountDownLatch vs CyclicBarrier

Both are synchronization aids (tools that coordinate threads waiting for each other), but they're for different patterns:

```
  CountDownLatch:
  ┌─────────────────────────────────────────────────────────┐
  │  One-shot. Created with a count N.                      │
  │  N threads call countDown() → latch reaches 0 → one     │
  │  (or more) waiting threads unblock from await().        │
  │  Cannot be reset. Fire once and done.                   │
  │                                                         │
  │  Use case: "Wait until all N services have started      │
  │  before accepting traffic." Main thread awaits;         │
  │  each service calls countDown() when ready.             │
  └─────────────────────────────────────────────────────────┘

  CyclicBarrier:
  ┌─────────────────────────────────────────────────────────┐
  │  Reusable. Created with a party count N.                │
  │  N threads all call await() → all blocked until the     │
  │  last one arrives → all unblock together.               │
  │  Barrier resets automatically for the next round.       │
  │                                                         │
  │  Use case: "N worker threads each process their chunk   │
  │  of data, then all wait for each other before           │
  │  proceeding to the merge step." Repeats each iteration. │
  └─────────────────────────────────────────────────────────┘
```

```java
// CountDownLatch — waiting for N events to complete
CountDownLatch latch = new CountDownLatch(3);   // 3 services to start

// In each service thread:
startService();
latch.countDown();   // "I'm ready"

// In main thread:
latch.await();   // block until all 3 have called countDown()
System.out.println("All services started — accepting traffic");

// CyclicBarrier — N threads meeting at a point, then repeating
CyclicBarrier barrier = new CyclicBarrier(4, () -> {
    System.out.println("All 4 workers reached barrier — proceeding to merge");
});

// In each worker thread:
processMyChunk();
barrier.await();   // wait for all 4 to finish their chunk
mergeResults();
// Barrier resets → all 4 can do the next iteration
```

---

### 🎨 Visual — Deadlock and How to Prevent It

```
  DEADLOCK:
  Thread A holds Lock1, waiting for Lock2
  Thread B holds Lock2, waiting for Lock1
  
  Thread A: [holds Lock1] ──wants──► Lock2 ──held by──► Thread B
                                                              │
  Thread A ◄──held by── Lock1 ◄──wants── Thread B ──────────┘

  Neither can proceed. Both wait forever.

  THREE PREVENTION STRATEGIES:

  1. LOCK ORDERING — always acquire locks in the same order across all threads:
     Every thread: acquire Lock1 first, THEN Lock2. Never reverse.

  2. tryLock WITH TIMEOUT — if you can't get the second lock, release the first and retry:
     if (!lock2.tryLock(100, TimeUnit.MILLISECONDS)) {
         lock1.unlock();   // release what we have, retry later
     }

  3. SINGLE LOCK — redesign so you never hold two locks simultaneously.

KEY INVARIANT:
   Deadlock requires FOUR conditions: mutual exclusion + hold-and-wait +
   no preemption + circular wait. Break any ONE and deadlock is impossible.
   Lock ordering breaks circular wait.
```

---

### 🔬 ThreadLocal — The Hidden Leak

`ThreadLocal` gives each thread its own isolated copy of a variable. Spring's `SecurityContextHolder` stores the current user in a `ThreadLocal`.

```java
// Spring Security internally does something like:
public class SecurityContextHolder {
    private static final ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();

    public static void setContext(SecurityContext context) {
        contextHolder.set(context);
    }

    public static SecurityContext getContext() {
        return contextHolder.get();
    }

    public static void clearContext() {
        contextHolder.remove();   // ⭐ MUST call this after the request
    }
}
```

**The leak:** Thread pools **reuse** threads. If a ThreadLocal is `set()` in request 1 and never `remove()`d, the next request handled by that same thread still sees request 1's data — wrong user context, potential security hole.

Spring Security clears the SecurityContext at the end of every request via its filter chain. If you create your own ThreadLocals: always call `remove()` in a `finally` block.

```java
// Pattern: always clean up ThreadLocal in finally
threadLocal.set(value);
try {
    // do work that uses threadLocal.get()
} finally {
    threadLocal.remove();   // safe even if an exception occurs
}
```

**Java 21 note:** Virtual threads (Project Loom) make thread pools for blocking I/O largely unnecessary. Don't pool virtual threads — they're so cheap you create them per-task. This largely eliminates the "thread pool reuse" ThreadLocal leak vector for blocking workloads.

---

<a id="exceptions"></a>

## 🔹 4. Exception Handling — Hierarchy + Patterns

### 🎨 Visual — Java Exception Hierarchy

```
  Throwable
  │
  ├── Error                          ← JVM-level failures; DON'T catch these normally
  │   ├── OutOfMemoryError
  │   ├── StackOverflowError
  │   └── AssertionError
  │
  └── Exception
      │
      ├── RuntimeException           ← UNCHECKED — compiler doesn't require handling
      │   ├── NullPointerException
      │   ├── IllegalArgumentException
      │   ├── IllegalStateException
      │   ├── IndexOutOfBoundsException
      │   ├── ClassCastException
      │   └── UnsupportedOperationException
      │
      └── (checked exceptions)       ← CHECKED — must catch or declare with throws
          ├── IOException
          ├── SQLException
          ├── ParseException
          └── InterruptedException

KEY INVARIANT:
   Checked = extends Exception (not RuntimeException) → compiler enforces handling
   Unchecked = extends RuntimeException → no compile-time enforcement
   Error = JVM failure → almost never catch; if you do, rethrow after logging
```

**Spring @Transactional connection:**
> `@Transactional` rolls back only on `RuntimeException` (unchecked) and `Error` by default. Checked exceptions (anything that extends `Exception` but not `RuntimeException`) do NOT roll back unless you add `rollbackFor`. Full explanation in `day2-spring-traps.md`.

---

### 🔬 try-with-resources + The Suppressed Exception Gotcha

**try-with-resources** (Java 7) automatically closes any resource that implements `AutoCloseable` (a one-method interface: `void close() throws Exception`) after the try block, whether the block exits normally or via exception.

```java
// ✅ Resources are closed in REVERSE order of declaration
try (
    Connection conn = dataSource.getConnection();
    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users")
) {
    ResultSet rs = stmt.executeQuery();
    // work with rs
}
// stmt.close() called first, then conn.close() — automatically, in finally
```

**The suppressed exception — the genuine senior gotcha:**

What happens if both the try block AND `close()` throw?

```java
// Example: try block throws IOException, close() also throws IOException
try (MyResource res = new MyResource()) {
    throw new IOException("main error");   // ← this is thrown first
    // close() is called automatically → also throws IOException("close error")
}

// WITHOUT try-with-resources: close()'s exception would REPLACE the original → lost
// WITH try-with-resources: close()'s exception is SUPPRESSED — attached to the original

// Access the suppressed exceptions:
try {
    // ...
} catch (IOException e) {
    Throwable[] suppressed = e.getSuppressed();   // [IOException("close error")]
    // Original exception propagates. Close exception is preserved, not lost.
}
```

---

### 🔬 Common Anti-Patterns

```java
// ❌ Anti-pattern 1: swallowing exceptions (the worst)
try {
    riskyOperation();
} catch (Exception e) {
    // nothing — silent failure, impossible to debug
}

// ❌ Anti-pattern 2: returning from finally — SWALLOWS the exception
try {
    throw new RuntimeException("real error");
} finally {
    return "ok";   // ← this silently swallows the RuntimeException! Exception is lost.
}

// ❌ Anti-pattern 3: catch Exception and wrap unnecessarily
try {
    result = service.call();
} catch (Exception e) {
    throw new RuntimeException(e);   // fine if you're translating; bad if it's a reflex
}

// ✅ Exception chaining — always preserve the cause
catch (SQLException e) {
    throw new DataAccessException("Failed to load user", e);   // cause preserved in stack trace
}
```

---

### 🔬 Custom Exceptions — When and How

```java
// Checked: when the caller is expected to handle it (recoverable condition)
public class InsufficientFundsException extends Exception {
    private final double amount;

    public InsufficientFundsException(double amount) {
        super("Insufficient funds: need " + amount + " more");
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }
}

// Unchecked: when it's a programming error or unrecoverable condition
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long id) {
        super("Order not found: " + id);
    }
}
```

---

### 🔬 `@ControllerAdvice` — Centralized Exception Handling in REST APIs

**`@ControllerAdvice`** (a Spring annotation that marks a class as a global exception handler — its `@ExceptionHandler` methods apply to all controllers, not just one) keeps exception handling out of individual controllers.

```java
@RestControllerAdvice   // @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {

    // Handle a specific custom exception
    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(OrderNotFoundException ex) {
        return new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage());
    }

    // Handle validation errors (Spring MVC bean validation)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse("VALIDATION_FAILED", message);
    }

    // Catch-all for unexpected exceptions
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        // Log the full stack trace; return a safe message (don't expose internals)
        log.error("Unexpected error", ex);
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
    }
}

record ErrorResponse(String code, String message) {}
```

---

<a id="gc"></a>

## 🔹 5. GC — Deep-Dive (Beyond Day 4)

> **Cross-reference:** Serial, Parallel, G1, ZGC types + Java version table → `day4-dsa-jvm-oop.md`. This section covers what Day 4 intentionally skipped: the *mechanics* of how GC actually finds and collects garbage.

### 📖 Terminology

- **GC Roots** — the set of objects the GC uses as starting points for reachability analysis. Any object reachable from a GC root is alive; everything else is garbage.
- **Generational hypothesis** — the empirical observation that most objects die young. JVMs exploit this by collecting the young generation (Eden + Survivor) much more frequently than the old generation — minor GC is cheap because most objects in Eden are already dead.
- **Mark-Sweep-Compact** — the three-phase GC algorithm: mark all live objects (reachable from roots), sweep (reclaim memory of unmarked objects), compact (move surviving objects together to eliminate fragmentation).

---

### 🎨 Visual — GC Roots + Reachability

```
  GC ROOTS (the starting points of the reachability graph):
  ┌────────────────────────────────────────────────────┐
  │  • Local variables in active method stack frames   │
  │  • Static fields (class-level variables)           │
  │  • Active threads                                  │
  │  • JNI (native code) references                    │
  └────────────────────────────────────────────────────┘
              │
              │  reachability graph (follow all references)
              ▼
  GC Root → Object A → Object B → Object C   ← all LIVE (reachable)
                     → Object D              ← LIVE

  Object E (no path from any GC root)         ← GARBAGE → collected

KEY INVARIANT:
   If no GC root can reach an object (directly or through a chain of references),
   it IS garbage — even if other garbage objects reference it.
   That's why circular references between garbage objects are collected correctly.
   (This is why reference counting — used in Python — fails on cycles;
    mark-and-sweep does not.)
```

---

### 🎨 Visual — Generational GC + Minor vs Major GC

```
  HEAP REGIONS:
  ┌──────────────────────────────────┬──────────────────────────┐
  │           YOUNG GEN              │         OLD GEN           │
  │  [  Eden  ] [Survivor0] [Surv1] │   (long-lived objects)    │
  └──────────────────────────────────┴──────────────────────────┘

  MINOR GC (young generation only — fast, frequent):
  1. Eden is full → GC triggered
  2. Mark live objects in Eden + Survivor0
  3. Copy survivors to Survivor1 (increment age counter)
  4. Objects that survived N minor GCs (default N=15) → promoted to Old Gen
  5. Eden + Survivor0 are cleared completely
  
  Most objects die in Eden without ever leaving → minor GC is very cheap.
  Stop-the-world pause: milliseconds.

  MAJOR GC (old generation — slower, less frequent):
  Triggered when Old Gen fills up. Collects Old Gen.
  Stop-the-world pause: longer (more objects to scan).

  FULL GC (entire heap — young + old + metaspace):
  Most expensive. Often triggered by:
  - Old Gen full + major GC can't free enough space
  - System.gc() call (don't call this in production)
  - Metaspace exhaustion

KEY INVARIANT:
   Minor GC: young gen only, fast, frequent.
   Major GC: old gen, slower.
   Full GC: everything, avoid at all costs.
   Promotion failure (old gen full when minor GC tries to promote) → Full GC.
```

---

### 🔬 Mark-Sweep-Compact — The Algorithm

```
  Phase 1 — MARK:
  Start from GC roots, traverse all references.
  Mark every reachable object as "alive."
  Cost: proportional to number of live objects (not heap size).

  Phase 2 — SWEEP:
  Scan the entire heap.
  Any object NOT marked → reclaim its memory.
  Result: free space, but FRAGMENTED (gaps between live objects).

  Phase 3 — COMPACT (optional, but needed to prevent fragmentation):
  Move all live objects to be contiguous.
  Update all references to point to new locations.
  Result: one large free block → fast allocation (pointer bump).
  Cost: expensive — all objects moved, all references updated.

  G1 avoids compacting the full heap by working region-by-region.
  ZGC uses colored pointers + load barriers to compact concurrently
  without stopping application threads.
```

---

### 🔬 `finalize()` — Deprecated and Why

`finalize()` was the Java mechanism for cleanup code before an object is collected. It was **deprecated in Java 9** and is essentially dead in modern Java.

Problems:
- The GC calls `finalize()` on a separate finalizer thread — timing is unpredictable (may never run before JVM exits)
- Objects with `finalize()` require an extra GC cycle (resurrection risk — finalizer can add the object to a reachable reference)
- Causes GC pauses and memory retention

**Modern replacement: `java.lang.ref.Cleaner` (Java 9+) or try-with-resources.**

```java
// Use try-with-resources for deterministic cleanup
try (Connection conn = pool.getConnection()) {
    // conn.close() is called exactly here, not "whenever GC decides"
}

// For non-closeable resources: Cleaner
Cleaner cleaner = Cleaner.create();
cleaner.register(myObject, () -> releaseNativeResource());
// Runs cleanup when myObject becomes phantom-reachable — more reliable than finalize
```

---

<a id="qa"></a>

## 🎯 Interview Q&A — Day 5 Topics

### ⭐ Section A — Asked in EPAM (Reported 2024–2025)

---

**Q1. How does Spring Security work? What is the Security Filter Chain?**

> Spring Security works by registering a chain of servlet filters (the `SecurityFilterChain`) that every HTTP request passes through before reaching your controller. Each filter handles one concern: one reads the JWT from the `Authorization` header, one handles username/password form login, one translates access denied exceptions to 401/403 responses.
>
> The key class is `SecurityContextHolder`, which uses a `ThreadLocal` internally to store the currently authenticated user's details. Each request-handling thread gets its own isolated copy of the security context. After the response is written, the filter chain clears the context — this is critical, because thread pools reuse threads, and a ThreadLocal that isn't cleared leaks to the next request.
>
> In Spring Security 6 (Spring Boot 3), configuration is done by returning a `SecurityFilterChain` as a `@Bean` — the old `extends WebSecurityConfigurerAdapter` approach was removed. You use the lambda DSL with `.authorizeHttpRequests()`, `.requestMatchers()`, and `.addFilterBefore()` to wire your JWT filter before the default username/password filter.

---

**Q2. Explain the structure of a JWT. Is the payload encrypted?**

> A JWT has three parts separated by dots: `header.payload.signature`. All three are Base64URL-encoded.
>
> The header contains the algorithm (`"alg":"HS256"` or `"RS256"`) and token type. The payload contains claims — `sub` (subject/user ID), `iat` (issued at), `exp` (expiry), and any custom claims like roles.
>
> **The payload is NOT encrypted — it is only Base64-encoded.** Anyone can decode it with a base64 decoder and read its contents. Never put passwords, private keys, or sensitive PII in the payload.
>
> The signature is what provides security. It's computed over `base64(header).base64(payload)` using the secret key (HS256) or private key (RS256). The server verifies the signature on every request — if the payload was tampered with, the signature won't match and the token is rejected. The JWT guarantees integrity (wasn't tampered with), not confidentiality (not a secret).

---

**Q3. How do you revoke a JWT before it expires?**

> A JWT is stateless — the server doesn't store it. Once issued, it's valid until `exp`. You can't revoke it the way you revoke a session, without re-introducing state.
>
> The standard pattern is short-lived access tokens plus refresh tokens. The access token expires in 15 minutes — even if stolen, the damage window is short. The refresh token is long-lived (days or weeks) and stored server-side in a database. On logout, you delete the refresh token from the database. When the client tries to refresh, the server checks the DB, finds the token gone, and forces re-login.
>
> For immediate revocation (compromised token), a blocklist approach works: store the JTI (JWT ID claim — a unique token identifier) in Redis with a TTL matching the token's remaining lifetime. Every request checks the blocklist. The cost is a Redis lookup per request.
>
> The honest senior answer: you pick based on your security requirements and latency budget. In most CRUD APIs, short-lived tokens plus refresh are sufficient and stateless enough.

---

**Q4. What is the difference between `ReentrantLock` and `synchronized`?**

> `synchronized` is the simpler, older mechanism. The JVM automatically releases the monitor when the block exits — even on exception. You cannot try to acquire a lock without blocking, you cannot interrupt a thread waiting for a lock, and there's only one wait/notify condition per object.
>
> `ReentrantLock` adds capabilities: `tryLock()` attempts to acquire the lock and returns false immediately if it can't (non-blocking path), `tryLock(timeout)` waits up to a duration, `lockInterruptibly()` allows the waiting thread to be interrupted, and you can have multiple `Condition` objects for finer-grained wait/notify semantics. You can also create a fair lock (`new ReentrantLock(true)`) that grants access in FIFO order.
>
> The critical difference: `ReentrantLock` requires manual `unlock()` in a `finally` block. If you forget, the lock is held forever and every subsequent thread blocks indefinitely. With `synchronized`, the JVM always releases on exit — you can't forget.
>
> **Rule:** use `synchronized` by default — it's simpler and sufficient for 95% of cases. Reach for `ReentrantLock` only when you need tryLock, timeout, interruptibility, or multiple conditions.

---

**Q5. What is the difference between `CountDownLatch` and `CyclicBarrier`?**

> Both coordinate groups of threads, but for different patterns.
>
> `CountDownLatch` is a one-shot counter. You initialize it with N. Threads call `countDown()` (doesn't block them), and one or more waiting threads block on `await()` until the count reaches zero. Classic use: a main thread waits for N worker threads to complete initialization before proceeding. Once the count hits zero, the latch can never be reused.
>
> `CyclicBarrier` is a meeting point — all N threads call `await()` and block there. When the Nth thread arrives, all N are released simultaneously. The barrier then resets for the next round. Classic use: N worker threads process their data chunk in parallel, meet at the barrier, then all proceed to merge in the next phase — and repeat.
>
> Key distinction: CountDownLatch — N threads signal, M threads wait (M is often 1). CyclicBarrier — N threads all wait for each other. CountDownLatch is one-shot; CyclicBarrier is reusable.

---

**Q6. What is the Java exception hierarchy? What is the difference between checked and unchecked?**

> The root is `Throwable`. It has two direct subclasses: `Error` and `Exception`.
>
> `Error` represents JVM-level failures — `OutOfMemoryError`, `StackOverflowError`. You almost never catch these; they indicate the JVM itself is in trouble.
>
> `Exception` splits into two branches. `RuntimeException` (and its subclasses) is unchecked — the compiler does not require you to declare or catch them. These represent programming errors: `NullPointerException`, `IllegalArgumentException`, `IndexOutOfBoundsException`. The assumption is that the caller can't meaningfully recover from a programmer mistake.
>
> Checked exceptions extend `Exception` directly (not via `RuntimeException`) — `IOException`, `SQLException`, `InterruptedException`. The compiler forces you to either catch them or declare them with `throws`. They represent recoverable conditions the caller should handle.
>
> In practice: prefer unchecked exceptions for most application code. Checked exceptions are appropriate only when the caller genuinely can recover and the failure is expected in normal operation.

---

### 🌐 Section B — Commonly Asked (Security + Threads + Exceptions + GC)

---

**Q7. What is the difference between AuthN and AuthZ? Which HTTP status code maps to each?**

> Authentication (AuthN) is verifying identity — "who are you?" The server checks your credentials (JWT, username/password, API key) and either accepts or rejects your identity. Failure → **HTTP 401**. The name "Unauthorized" is misleading — it actually means "not authenticated."
>
> Authorization (AuthZ) is verifying permissions — "are you allowed to do this?" The server checks whether your authenticated identity has the required role or permission. Failure → **HTTP 403 Forbidden**.
>
> Common mistake: returning 401 when a logged-in user tries to access an admin endpoint. That user IS authenticated — the correct code is 403. 401 should only be returned when there are no credentials, or the credentials are invalid.

---

**Q8. What is a deadlock? How do you prevent it?**

> A deadlock occurs when two or more threads each hold a resource (lock) and are each waiting for a resource the other holds. Both are blocked indefinitely — no progress is ever made.
>
> The four necessary conditions for deadlock: mutual exclusion (only one thread can hold a resource), hold-and-wait (a thread holds one resource while waiting for another), no preemption (a resource can't be forcibly taken), and circular wait (thread A waits for thread B's resource, which waits for thread A's resource).
>
> Prevention strategies: **lock ordering** — always acquire multiple locks in the same order across all threads (eliminates circular wait). **tryLock with timeout** — if you can't get the second lock within N milliseconds, release the first and retry (eliminates hold-and-wait). **Single lock redesign** — restructure so you never need two locks at once.
>
> Detection: Java's `jstack` thread dump shows threads in `BLOCKED` state with the lock owner. A circular dependency in the BLOCKED chain is a deadlock.

---

**Q9. What is the difference between `minor GC`, `major GC`, and `full GC`?**

> **Minor GC** collects only the young generation (Eden + Survivor spaces). It's triggered when Eden fills up. Most objects die in Eden without ever being promoted, so minor GC is fast — typically milliseconds. The stop-the-world pause is short. This is the most common GC event.
>
> **Major GC** collects the old generation (long-lived objects). It's slower because the old generation is larger and contains objects that survived multiple minor GCs. G1 tries to make major collection concurrent to reduce pauses.
>
> **Full GC** collects the entire heap — young, old, and Metaspace. It's the most expensive event. Causes: the old generation fills up and major GC can't reclaim enough space, or System.gc() is called. A full GC that runs frequently is a sign of a memory leak or a heap that's too small.
>
> The goal of modern GC tuning is to keep collection in minor GC, promote objects to old gen slowly, and avoid full GC entirely in production.

---

**Q10. What are GC roots? Why do they matter?**

> GC roots are the starting points the garbage collector uses to determine which objects are alive. The collector starts from each GC root and traverses all reachable references — anything reachable is live, everything else is garbage.
>
> GC roots include: local variables in active method stack frames (every method currently on any thread's stack), static fields (class-level variables, because they live as long as the class is loaded), active thread objects, and JNI (native code) references.
>
> GC roots matter for two reasons. First, they're why a memory leak in Java isn't a dangling pointer — it's a reference that's unintentionally kept alive. If a static collection (like a cache) accumulates objects and is never cleared, those objects are GC roots → never collected. Second, they explain why circular references between garbage objects are collected correctly. If object A references B and B references A, but neither is reachable from a GC root, both are garbage. Mark-and-sweep handles this correctly; reference counting (Python's default) does not.

---

**Q11. What is try-with-resources? What happens if both the try block and `close()` throw an exception?**

> Try-with-resources (Java 7) automatically calls `close()` on any `AutoCloseable` resource declared in the `try(...)` header, whether the block exits normally or via exception. Multiple resources are closed in reverse declaration order.
>
> When both the try block and `close()` throw: without try-with-resources, the close exception would replace the original — the original exception is silently lost. With try-with-resources, the original exception propagates and the close exception is attached to it as a suppressed exception. You access it via `e.getSuppressed()`. Neither exception is lost.
>
> This matters in production: if a database operation fails AND closing the connection also fails (e.g., network is down), you need to see both. Suppressed exceptions preserve the full picture.

---

**Q12. How do you handle exceptions globally in a Spring REST API?**

> Use `@RestControllerAdvice` (a combination of `@ControllerAdvice` and `@ResponseBody`) to define a global exception handler class. Its `@ExceptionHandler` methods apply to every controller in the application — you don't handle exceptions in individual controllers.
>
> Structure: one handler per exception type you want to explicitly map (e.g., `OrderNotFoundException` → 404, `MethodArgumentNotValidException` → 400 with validation field details). A catch-all `@ExceptionHandler(Exception.class)` logs the unexpected error and returns a 500 with a safe message (never expose stack traces or internal error messages to external clients — that's an information leak).
>
> The key design principle: never let raw `Exception` messages leak to the API response. Log the full stack trace internally; return a structured error response with a code, a user-safe message, and optionally a request correlation ID for tracing.

---

## 🧾 TL;DR — 15 Things to Say Cold Tomorrow

1. **Spring Security 6:** `SecurityFilterChain` @Bean with lambda DSL — `WebSecurityConfigurerAdapter` is REMOVED in Spring Boot 3
2. **Spring Security flow:** request → filter chain → SecurityContext (ThreadLocal) → controller; filter chain clears context after response
3. **JWT payload = Base64, NOT encrypted** — anyone can decode it; never put secrets in payload; signature ensures tamper-proof
4. **HS256 vs RS256:** HS256 symmetric (same key signs+verifies); RS256 asymmetric (private signs, public verifies) — RS256 for microservices
5. **JWT revocation:** can't revoke pre-expiry without state. Standard answer: short-lived access token (15 min) + server-stored refresh token; on logout, delete refresh token
6. **AuthN failure = 401; AuthZ failure = 403** — never return 401 for "authenticated but no permission"
7. **ReentrantLock:** unlock() MUST be in finally (synchronized auto-releases); adds tryLock, timeout, interruptible, fairness, multiple Conditions
8. **CountDownLatch vs CyclicBarrier:** Latch = N signal, M wait, one-shot; Barrier = N all wait for each other, reusable
9. **ThreadPoolExecutor with bounded queue** — `Executors.newFixedThreadPool` has unbounded queue → OOM under load; production: `new ThreadPoolExecutor(core, max, ttl, new ArrayBlockingQueue<>(N), CallerRunsPolicy)`
10. **ThreadLocal + thread pools = leak** — always call `remove()` in finally; Spring Security's `SecurityContextHolder` does this in its filter
11. **Exception hierarchy:** Throwable → Error (don't catch) + Exception → RuntimeException (unchecked) + checked. @Transactional rolls back unchecked only
12. **try-with-resources suppressed exception:** if try block AND close() both throw — original propagates, close exception is suppressed (attached via `getSuppressed()`) — neither is lost
13. **GC roots:** local vars on active stacks + static fields + active threads — anything reachable from a GC root is live; circular garbage is collected correctly (unlike reference counting)
14. **Minor GC = young gen, fast; Full GC = everything, avoid** — frequent full GC = memory leak or heap too small
15. **finalize() deprecated Java 9** — use try-with-resources for closeable resources, Cleaner API for native resources

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Sep 2026 | Day 5 notes created. Spring Security 6 config used (SecurityFilterChain @Bean — WebSecurityConfigurerAdapter removed). JWT payload explicitly NOT encrypted. RS256 vs HS256 for microservices. JWT revocation three strategies. ThreadLocal leak tied to SecurityContextHolder + thread pool reuse. ReentrantLock unlock-in-finally footgun. CountDownLatch vs CyclicBarrier distinction. ThreadPoolExecutor bounded queue (newFixedThreadPool OOM risk). Suppressed exceptions in try-with-resources. finalize() deprecated Java 9 — Cleaner API as replacement. GC roots + generational hypothesis + minor/major/full GC distinction. |
