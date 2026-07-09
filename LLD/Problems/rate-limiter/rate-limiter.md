# Rate Limiter

> **Standard followed:** `LLD/notes-standards.md`

---

## 🎯 Problem Statement

Design a configurable rate limiter that allows an API to reject requests exceeding a defined threshold. Support two algorithms: Token Bucket (steady token refill, allows short bursts) and Sliding Window (precise rolling-window counting). The algorithm must be swappable without changing the caller.

---

## 📖 Requirements

**Functional:**
- `allowRequest(clientId)` — returns `true` if within limit, `false` to reject
- **Token Bucket**: tokens refill at a fixed rate; burst allowed up to bucket capacity
- **Sliding Window**: count requests in the last N seconds; reject if count ≥ limit
- Algorithm is selected at configuration time, not hardcoded in the service

**Non-functional:**
- Thread-safe — concurrent requests from the same client must not both "pass" when only one token remains
- New algorithm = one new class + one factory case, no other changes
- Each client has its own limiter instance — limiters never share state

---

## 🏗️ Class Design

### 🎨 Visual — Class Structure

```
┌────────────────────────────────────────────────────────┐
│                 RateLimiterService                     │
│  - limiters: ConcurrentHashMap<clientId, RateLimiter> │
│  - factory:  RateLimiterFactory                        │
│  - config:   RateLimiterConfig                         │
│  + allowRequest(clientId): boolean                     │
└────────────────────────────────────────────────────────┘
         │ uses
         ▼
┌───────────────────────┐      ┌───────────────────────────┐
│  RateLimiterFactory   │─────▶│  <<interface>>            │
│  + create(config)     │      │  RateLimiter              │
└───────────────────────┘      │  + allowRequest(): boolean│
                               └───────────────┬───────────┘
                                               │ implements
                               ┌───────────────┴───────────────┐
                               │                               │
                  TokenBucketRateLimiter       SlidingWindowRateLimiter

RateLimiterConfig (record)
  - type: RateLimiterType         (TOKEN_BUCKET | SLIDING_WINDOW)
  - capacity: int                 (max tokens OR max requests per window)
  - refillRatePerSecond: int      (TOKEN_BUCKET only)
  - windowSizeSeconds: int        (SLIDING_WINDOW only)

KEY INVARIANT:
   Each clientId gets exactly one RateLimiter instance (computeIfAbsent).
   The limiter's internal state (tokens / timestamp log) is private.
   Concurrent calls to allowRequest() on the same limiter are synchronized.
```

---

## 🔌 Key Interfaces

```java
/**
 * Contract for all rate-limiting algorithms.
 * The caller invokes allowRequest() — it never knows which algorithm runs.
 * Strategy pattern: swap the algorithm by injecting a different implementation.
 */
public interface RateLimiter {

    // Returns true if the request is within rate limits; false to reject
    boolean allowRequest();
}
```

---

## 🧭 Design Decisions

| Decision | Why |
|---|---|
| **Strategy for algorithm** | TokenBucket and SlidingWindow have different internal state structures — one refills tokens, the other maintains a rolling timestamp log. They are structurally different algorithms, not just parameterically different. Strategy encapsulates each. Open-Closed: new algorithm = new class. |
| **Factory for creation** | `RateLimiterService` never calls `new TokenBucketRateLimiter()` directly. `RateLimiterFactory` reads `config.type` and returns the right implementation. Caller is decoupled from construction. |
| **Per-client instances** | Limiters do not share state across clients. `ConcurrentHashMap.computeIfAbsent` creates exactly one limiter per clientId atomically — no race on first-request creation. |
| **`synchronized` on `allowRequest()`** | Two threads for the same client reading `tokens = 1` simultaneously is a read-modify-write race — both pass, both decrement. Synchronizing `allowRequest()` makes refill + check + decrement one atomic block. |

---

## 🎨 Visual — Algorithm Animations

```
TOKEN BUCKET (capacity=3, refill 1 token/sec):

t=0s  [●][●][●]   3 tokens     request → ALLOW  (tokens=2)
t=0s  [●][●][ ]   2 tokens     request → ALLOW  (tokens=1)
t=0s  [●][ ][ ]   1 token      request → ALLOW  (tokens=0)
t=0s  [ ][ ][ ]   0 tokens     request → REJECT
t=1s  [●][ ][ ]   1 refilled   request → ALLOW  (tokens=0)

→ Allows short bursts up to capacity; then throttles to refill rate.
→ Good for: APIs that want to absorb small traffic spikes.

SLIDING WINDOW (limit=3 requests, window=10 seconds):

timeline:  |──0──────5────10────15────20──▶
requests:  R  R  R        |       R  R

At t=11:  window covers [1s → 11s] → 0 requests in window → ALLOW
At t=14:  window covers [4s → 14s] → 0 requests in window → ALLOW

→ Counts only requests inside the rolling window. No burst allowance.
→ Good for: strict per-second SLAs, billing APIs.

KEY INVARIANT:
   Token Bucket: tokens ≥ 0 is the guard. refill+check+decrement must be atomic.
   Sliding Window: count-in-window < limit is the guard. add+count must be atomic.
```

---

## 🖊️ Coding Skeleton

**Interview coding order:**

1. **Enum** — `RateLimiterType` (TOKEN_BUCKET, SLIDING_WINDOW)
2. **Config** — `RateLimiterConfig` (record: capacity, refillRate, windowSize)
3. **Interface** — `RateLimiter` (one method: `allowRequest()`)
4. **`TokenBucketRateLimiter`** — simpler algorithm; code in full
5. **`SlidingWindowRateLimiter`** — LinkedList of timestamps; code or stub with explanation
6. **`RateLimiterFactory`** — one switch statement
7. **`RateLimiterService`** — ConcurrentHashMap + `computeIfAbsent`

**TokenBucketRateLimiter — the critical class:**

```java
// thread-safe: synchronized on allowRequest (refill + check + decrement are atomic)
public class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final int refillRatePerSecond;
    private int tokens;
    private long lastRefillTimestamp;

    public TokenBucketRateLimiter(int capacity, int refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean allowRequest() {
        refill();
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedSeconds = (now - lastRefillTimestamp) / 1000;
        int tokensToAdd = (int) (elapsedSeconds * refillRatePerSecond);
        if (tokensToAdd > 0) {
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTimestamp = now;
        }
    }
}
```

**RateLimiterService — orchestrator:**

```java
public class RateLimiterService {

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final RateLimiterFactory factory;
    private final RateLimiterConfig defaultConfig;

    public boolean allowRequest(String clientId) {
        // computeIfAbsent is atomic — only one limiter created per clientId
        RateLimiter limiter = limiters.computeIfAbsent(
            clientId, id -> factory.create(defaultConfig)
        );
        return limiter.allowRequest();   // synchronized inside the limiter
    }
}
```

---

## 🔁 Concurrency — Making It Thread-Safe

**Shared mutable state:**

| Field | Problem without lock | Fix |
|---|---|---|
| `TokenBucketRateLimiter.tokens` | Thread A and B both read `tokens = 1`, both return true, both decrement — two requests pass, one token | `synchronized` on `allowRequest()` — refill + check + decrement is one atomic block |
| `SlidingWindowRateLimiter.requestLog` | Thread A and B both count N-1 in window, both pass — N+1 requests through | `synchronized` on `allowRequest()` |
| `limiters` ConcurrentHashMap | Two threads for the same new clientId both try to create a limiter | `ConcurrentHashMap.computeIfAbsent` is atomic — safe without extra lock |

**Why synchronized and not AtomicInteger for tokens:**
`tokens--` is only step 2 of 3: (1) refill, (2) check, (3) decrement. Steps 1-2-3 are a compound action — `AtomicInteger.decrementAndGet()` covers step 3 only. The full check-and-act needs `synchronized` to stay atomic.

---

## 📐 "What Would You Do Differently?"

> *"With more time, I'd replace `synchronized` with a lock-free CAS loop on `AtomicLong` for the token count: read → if > 0, try `compareAndSet(current, current-1)` → if CAS fails (another thread modified it), retry. This avoids blocking on high-traffic endpoints. I'd also add per-client configuration — VIP clients get higher limits — by storing a `Map<clientId, RateLimiterConfig>`. For distributed rate limiting across multiple app servers, I'd shift state to Redis: `INCR` + `EXPIRE` for fixed window; sorted set `ZADD` + `ZCOUNT` for sliding window. The `RateLimiter` interface stays unchanged — the Redis-backed implementation just delegates to Redis commands."*

---

## 🔬 Interview Q&As

### Q: "What's the difference between Token Bucket and Sliding Window?"
> Token Bucket refills at a fixed rate and allows bursting up to bucket capacity — 10 tokens means 10 requests can fire in one millisecond. Sliding Window counts requests in the last N seconds — no burst, more precise. Token Bucket is better for APIs that want to absorb small traffic spikes. Sliding Window is better for strict per-second SLAs or billing APIs where every request counts exactly.

### Q: "Two threads call allowRequest() simultaneously. What breaks without synchronization?"
> Both threads read `tokens = 1`. Both see 1 > 0. Both return true. Both decrement. Two requests are allowed when only one token existed — the rate limit is violated. This is a read-modify-write race: the read (check) and the write (decrement) are not atomic. Fix: synchronize the entire refill + check + decrement block as one unit.

### Q: "How would you distribute this across 5 app servers?"
> In-memory token state doesn't survive across servers — each server has its own counter, so the effective limit becomes 5× the intended limit. Move state to Redis: `INCRBY` + `EXPIRE` for fixed window; sorted set for sliding window. Redis commands are atomic so no distributed lock is needed. The Java `RateLimiter` interface stays unchanged — the implementation becomes a Redis client wrapper.

### Q: "How would you support per-client rate limits — VIP users get 1000 req/s, free users get 10?"
> Replace `defaultConfig` in `RateLimiterService` with a `ConfigRepository.getConfigFor(clientId)` lookup. The factory uses the client-specific config to create the limiter. `ConcurrentHashMap.computeIfAbsent` ensures the right config is used on first request. VIP clients get `capacity=1000`, free clients get `capacity=10`. Limiter implementations are unchanged.

### Q: "Why Strategy pattern here instead of an if-else in allowRequest()?"
> TokenBucket and SlidingWindow have completely different internal state — one holds a refillable token count, the other maintains a rolling timestamp log. An if-else in `allowRequest()` would require the service to know both algorithms' internals and manage both data structures. Strategy encapsulates each in its own class with its own state. Open-Closed: adding a FixedWindow algorithm is one new class, not a new branch in existing code.

---

## 🧾 TL;DR — 30-Second Pitch

> *"I have a `RateLimiter` interface with two implementations — `TokenBucketRateLimiter` (refills at fixed rate, allows bursts) and `SlidingWindowRateLimiter` (rolling timestamp count, strict). A `RateLimiterFactory` picks the right one from config. `RateLimiterService` keeps a `ConcurrentHashMap` of per-client limiters — `computeIfAbsent` guarantees exactly one limiter per client. `allowRequest()` is synchronized inside each limiter — the read-modify-write race on the token count is the critical concurrency issue. Adding a new algorithm is one new class, nothing else changes."*

---

## 🔗 Patterns Used

- **Strategy** — `RateLimiter` interface, two algorithm implementations. See **`LLD/DesignPatterns/01-factory-strategy.md`** (Strategy section).
- **Factory** — `RateLimiterFactory.create(config)` selects the right algorithm. See **`LLD/DesignPatterns/01-factory-strategy.md`** (Factory section).

---

## 🖊️ Full Implementation

> All classes in one place. Read top to bottom — enum → config → interface → implementations → factory → service.

### RateLimiterType.java

```java
public enum RateLimiterType {
    TOKEN_BUCKET,
    SLIDING_WINDOW
}
```

### RateLimiterConfig.java

```java
/**
 * Configuration for one rate limiter.
 * TOKEN_BUCKET uses: capacity + refillRatePerSecond
 * SLIDING_WINDOW uses: capacity (max requests) + windowSizeSeconds
 */
public class RateLimiterConfig {

    private final RateLimiterType type;
    private final int capacity;
    private final int refillRatePerSecond;
    private final int windowSizeSeconds;

    public RateLimiterConfig(
            RateLimiterType type, int capacity,
            int refillRatePerSecond, int windowSizeSeconds) {
        this.type = type;
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public RateLimiterType getType()           { return type; }
    public int getCapacity()                   { return capacity; }
    public int getRefillRatePerSecond()        { return refillRatePerSecond; }
    public int getWindowSizeSeconds()          { return windowSizeSeconds; }
}
```

### RateLimiter.java

```java
/**
 * Strategy interface — the caller never knows which algorithm runs.
 * Swap algorithms by injecting a different implementation.
 */
public interface RateLimiter {

    // Returns true to allow; false to reject
    boolean allowRequest();
}
```

### TokenBucketRateLimiter.java

```java
/**
 * Token Bucket algorithm.
 * Tokens refill at a fixed rate; burst is allowed up to bucket capacity.
 *
 * thread-safe: synchronized on allowRequest.
 * Critical: refill + check + decrement must be one atomic block.
 * Without synchronization: two threads read tokens=1, both pass — race condition.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final int refillRatePerSecond;
    private int tokens;
    private long lastRefillTimestamp;

    public TokenBucketRateLimiter(int capacity, int refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean allowRequest() {
        refill();
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedSeconds = (now - lastRefillTimestamp) / 1000;
        int tokensToAdd = (int) (elapsedSeconds * refillRatePerSecond);
        if (tokensToAdd > 0) {
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTimestamp = now;
        }
    }
}
```

### SlidingWindowRateLimiter.java

```java
import java.util.LinkedList;
import java.util.Queue;

/**
 * Sliding Window algorithm.
 * Tracks timestamps of recent requests; rejects if count in window >= limit.
 * No burst — every request in the last windowSizeSeconds counts.
 *
 * thread-safe: synchronized on allowRequest.
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long windowSizeMillis;
    private final Queue<Long> requestLog = new LinkedList<>();

    public SlidingWindowRateLimiter(int maxRequests, int windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = (long) windowSizeSeconds * 1000;
    }

    @Override
    public synchronized boolean allowRequest() {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeMillis;

        // Evict timestamps that have fallen outside the window
        while (!requestLog.isEmpty() && requestLog.peek() < windowStart) {
            requestLog.poll();
        }

        if (requestLog.size() < maxRequests) {
            requestLog.add(now);
            return true;
        }
        return false;
    }
}
```

### RateLimiterFactory.java

```java
/**
 * Factory — RateLimiterService never calls new TokenBucketRateLimiter() directly.
 * Adding a new algorithm = one new class + one case here. Open-Closed.
 */
public class RateLimiterFactory {

    public RateLimiter create(RateLimiterConfig config) {
        switch (config.getType()) {
            case TOKEN_BUCKET:
                return new TokenBucketRateLimiter(
                    config.getCapacity(), config.getRefillRatePerSecond()
                );
            case SLIDING_WINDOW:
                return new SlidingWindowRateLimiter(
                    config.getCapacity(), config.getWindowSizeSeconds()
                );
            default:
                throw new IllegalArgumentException("Unknown type: " + config.getType());
        }
    }
}
```

### RateLimiterService.java

```java
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service layer: one RateLimiter per clientId.
 * computeIfAbsent is atomic — exactly one limiter created per clientId even under concurrency.
 */
public class RateLimiterService {

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final RateLimiterFactory factory;
    private final RateLimiterConfig defaultConfig;

    public RateLimiterService(RateLimiterFactory factory, RateLimiterConfig defaultConfig) {
        this.factory = factory;
        this.defaultConfig = defaultConfig;
    }

    public boolean allowRequest(String clientId) {
        // computeIfAbsent: atomic — only one limiter created per clientId
        RateLimiter limiter = limiters.computeIfAbsent(
            clientId, id -> factory.create(defaultConfig)
        );
        return limiter.allowRequest();   // synchronized inside the limiter
    }
}
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 9, 2026 | Canonical note created. All classes in single MD. Status: canonical reference — Kapil has not self-attempted yet. |
