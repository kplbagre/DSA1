# Disney Ad Platforms — LLD: API Rate Limiter in Java

> **Context:** Confirmed question from JioHotstar Staff SWE interview (May 2026, offer received). Disney Ad Platforms equivalent: throttling ad requests from DSPs (Demand Side Platforms) or protecting downstream ad exchange partners from being overwhelmed at 300K QPS.
>
> **Cross-reference:** System-level rate limiting design → `Interview/DocuSign/r2-solutions/C1-rate-limiter.md`. This file focuses on the **Java implementation** and **concurrency depth** — the actual test in the LLD round.

---

## 🎯 What the Interviewer Is Actually Testing

The problem statement is "Design and implement an API Rate Limiter in Java."

But the real question is:

> **"Do you know that `synchronized` blocks are a throughput bottleneck at 300K QPS, and can you replace them with `ConcurrentHashMap.computeIfAbsent()`, `AtomicLong`, and per-key locking?"**

The algorithm selection discussion is the warm-up. The concurrency design is the test.

---

## 🔑 Technology Quick Reference

| Term | Plain-English meaning |
|---|---|
| **Token Bucket** | A bucket holds N tokens. Each request consumes one token. Tokens refill at a fixed rate. If bucket is empty → reject. Allows controlled burst. Production standard. |
| **Sliding Window Log** | Store timestamp of every request. On each request, discard timestamps older than the window, count remaining. Most accurate; high memory (one entry per request). |
| **Sliding Window Counter** | Approximate: `requests_in_current_window = prev_window_count × (1 - elapsed%) + current_window_count`. Memory-efficient approximation of sliding log. |
| **Fixed Window Counter** | Simple INCR counter per minute. Allows 2× burst at window boundary (last second of minute N + first second of minute N+1). |
| **Leaky Bucket** | Requests enter a queue; exit at fixed rate. Smooths traffic. No burst. Good for upstream protection; bad for user-facing APIs where burst is expected. |
| **CAS** (Compare-And-Swap) | Hardware instruction: "if value == expected, write new value atomically." No OS lock. Basis of all Java Atomic classes. |
| **`computeIfAbsent`** | Atomic `ConcurrentHashMap` operation: if key missing, compute and insert — as one uninterruptible step. Prevents the `containsKey + put` race condition. |

---

## Phase 1 — Algorithm Selection (Say This in the Interview)

**The interviewer will ask you to compare algorithms before coding. Own this table:**

| Algorithm | Accuracy | Memory per client | Burst allowed? | Production use |
|---|---|---|---|---|
| Fixed Window Counter | ❌ Low — 2× burst at boundary | Very low (1 int) | ❌ No (but boundary burst exists) | Simple internal tools |
| Sliding Window Log | ✅ Highest | ❌ High — 1 entry/request | ✅ Yes (within window) | Low-traffic, billing-critical |
| Sliding Window Counter | ✅ High (approximation) | Low (2 ints) | ✅ Approximate | Most distributed systems |
| **Token Bucket** | ✅ High | Low (1 counter + timestamp) | ✅ **Yes — controlled burst** | **Production standard. Pick this.** |
| Leaky Bucket | ✅ High | Low (queue size) | ❌ None | Upstream traffic shaping |

**Why Token Bucket for ad request throttling:**

> *"I'll use Token Bucket. Ad platforms have bursty traffic — a live sports event start causes an immediate spike. Token Bucket allows controlled burst (the bucket fills up at a steady rate, but you can consume accumulated tokens in a burst) while still enforcing the average rate. Leaky Bucket would reject all burst traffic. Sliding Window Log has memory proportional to QPS — at 300K QPS that's unacceptable. Token Bucket is O(1) memory per client regardless of request rate."*

---

## Phase 2 — OOP Design (Before Writing Code)

**Interface first — makes the algorithm pluggable (Strategy pattern):**

```java
// Strategy interface — algorithm is interchangeable at runtime
public interface RateLimitAlgorithm {
    boolean allowRequest(String clientId);
}

// Factory — algorithm selection from config, not hardcoded
public class RateLimiterFactory {
    public static RateLimitAlgorithm create(String type, RateLimiterConfig config) {
        return switch (type) {
            case "token_bucket"      -> new TokenBucketLimiter(config);
            case "sliding_window"    -> new SlidingWindowLimiter(config);
            case "fixed_window"      -> new FixedWindowLimiter(config);
            default -> throw new IllegalArgumentException("Unknown algorithm: " + type);
        };
    }
}

// Config — single responsibility, immutable
public final class RateLimiterConfig {
    private final int maxRequests;       // tokens per window
    private final Duration windowSize;  // refill period
    private final Duration cleanupInterval; // how often to evict stale clients

    public RateLimiterConfig(int maxRequests, Duration windowSize, Duration cleanupInterval) {
        this.maxRequests = maxRequests;
        this.windowSize = windowSize;
        this.cleanupInterval = cleanupInterval;
    }

    public int getMaxRequests() { return maxRequests; }
    public Duration getWindowSize() { return windowSize; }
    public Duration getCleanupInterval() { return cleanupInterval; }
}
```

---

## Phase 3 — Token Bucket Implementation (The Code the Interviewer Expects)

### Step 1 — The Bucket (per client state)

```java
import java.util.concurrent.atomic.AtomicLong;

/**
 * One token bucket per client. Immutable config, mutable state via AtomicLong.
 *
 * WHY AtomicLong instead of synchronized long:
 * - AtomicLong uses CAS hardware instruction — no OS lock, no thread suspension
 * - Under low-moderate contention: 5-10x faster than synchronized
 * - Under extreme contention (thousands of threads same key): use LongAdder instead
 */
public class TokenBucket {
    private final int capacity;           // max tokens (burst size)
    private final double refillRatePerMs; // tokens added per millisecond
    private final AtomicLong availableTokens;
    private volatile long lastRefillTime; // volatile: visible across threads without lock

    public TokenBucket(int capacity, Duration windowSize) {
        this.capacity = capacity;
        // refill rate: fill completely once per window
        this.refillRatePerMs = (double) capacity / windowSize.toMillis();
        this.availableTokens = new AtomicLong(capacity); // start full
        this.lastRefillTime = System.currentTimeMillis();
    }

    /**
     * Try to consume one token.
     * Returns true if request is allowed, false if rate-limited.
     *
     * WHY synchronized here (not AtomicLong.compareAndSet loop):
     * The refill + consume is a two-step compound operation.
     * Two-step operations cannot be made atomic with a single CAS.
     * Per-bucket synchronized is fine — contention is per-client, not global.
     */
    public synchronized boolean tryConsume() {
        refill();
        long tokens = availableTokens.get();
        if (tokens > 0) {
            availableTokens.decrementAndGet();
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        if (elapsed > 0) {
            long tokensToAdd = (long) (elapsed * refillRatePerMs);
            if (tokensToAdd > 0) {
                long newTokens = Math.min(capacity, availableTokens.get() + tokensToAdd);
                availableTokens.set(newTokens);
                lastRefillTime = now;
            }
        }
    }
}
```

### Step 2 — The Registry (per-client bucket lookup)

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

public class TokenBucketLimiter implements RateLimitAlgorithm {

    // ConcurrentHashMap: bucket-level locking, lock-free reads
    // Each client has its own bucket — contention is per-client, not global
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final RateLimiterConfig config;
    private final ScheduledExecutorService cleaner;

    public TokenBucketLimiter(RateLimiterConfig config) {
        this.config = config;
        // Background cleanup — prevents unbounded memory growth
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-cleanup");
            t.setDaemon(true); // don't prevent JVM shutdown
            return t;
        });
        cleaner.scheduleAtFixedRate(
            this::evictStaleBuckets,
            config.getCleanupInterval().toSeconds(),
            config.getCleanupInterval().toSeconds(),
            TimeUnit.SECONDS
        );
    }

    @Override
    public boolean allowRequest(String clientId) {
        lastSeen.put(clientId, System.currentTimeMillis());

        // computeIfAbsent is ATOMIC — if two threads hit this simultaneously
        // for the same new clientId, only ONE bucket is created.
        // containsKey() + put() would NOT be atomic — race condition.
        TokenBucket bucket = buckets.computeIfAbsent(
            clientId,
            id -> new TokenBucket(config.getMaxRequests(), config.getWindowSize())
        );

        return bucket.tryConsume();
    }

    /**
     * Evict clients inactive for 2× the window size.
     * Prevents unbounded ConcurrentHashMap growth for inactive clients.
     */
    private void evictStaleBuckets() {
        long staleThreshold = System.currentTimeMillis()
            - (config.getWindowSize().toMillis() * 2);
        for (Map.Entry<String, Long> entry : lastSeen.entrySet()) {
            if (entry.getValue() < staleThreshold) {
                buckets.remove(entry.getKey());
                lastSeen.remove(entry.getKey());
            }
        }
    }

    public void shutdown() {
        cleaner.shutdown();
    }
}
```

---

## Phase 4 — The Concurrency Deep Dive (The Real Test)

### The `synchronized` trap — what the interviewer challenges you on

**Interviewer:** *"You used `synchronized` on `TokenBucket.tryConsume()`. That will serialize all threads on the same client's bucket. How does this affect throughput?"*

**Your answer:**

> *"The `synchronized` is per-bucket, not global. Threads accessing different clients' buckets never contend — there's no shared lock. For the same client's bucket, yes, those requests serialize. At 300K QPS across thousands of clients, that's fine — a single client might hit 100 req/sec, and a single synchronized block takes microseconds. The bottleneck would only emerge if one client generates thousands of concurrent requests, which is itself the attack scenario we're rate-limiting against.*
>
> *If I needed to eliminate even per-bucket locking, I'd restructure tryConsume() to use a CAS loop on AtomicLong instead of synchronized — but that makes the refill logic harder to reason about correctly. The per-bucket synchronized is the right trade-off here."*

---

### The `containsKey + put` compound operation trap

**Interviewer:** *"Why did you use `computeIfAbsent` instead of `containsKey` + `put`?"*

```java
// ❌ WRONG — race condition even on ConcurrentHashMap
if (!buckets.containsKey(clientId)) {
    // Thread B also passes this check for the same clientId
    buckets.put(clientId, new TokenBucket(...));
    // Both threads create a bucket — second one overwrites first
    // Client's token count resets mid-stream — silent bug
}

// ✅ CORRECT — atomic: only one lambda executes for a given key
TokenBucket bucket = buckets.computeIfAbsent(
    clientId,
    id -> new TokenBucket(config.getMaxRequests(), config.getWindowSize())
);
```

> *"Individual operations on `ConcurrentHashMap` are thread-safe. Compound operations — check-then-act — are NOT. Between `containsKey` returning false and `put` executing, another thread can insert the same key. `computeIfAbsent` is a single atomic operation: if the key is absent, the lambda runs exactly once, and the result is inserted atomically."*

---

### Memory growth trap

**Interviewer:** *"What happens if you have 10 million unique clients over a week? What happens to your ConcurrentHashMap?"*

> *"It grows unboundedly — 10M entries × ~100 bytes each = ~1GB of live heap. That will eventually trigger GC pressure or OOM. The fix is the `ScheduledExecutorService` cleanup I added — it runs every N seconds and removes clients that haven't made a request in 2× the window size. For a 1-minute window, clients inactive for 2 minutes are evicted. The `lastSeen` map tracks the last activity timestamp per client."*

**Follow-up:** *"Why not use a TTL-based cache like Caffeine or Guava Cache instead of rolling your own cleanup?"*

> *"In production, yes — I'd use Caffeine with `expireAfterAccess`. It handles eviction efficiently with a time wheel and avoids the cost of iterating the full map. My custom cleanup thread is fine for an interview implementation but would be replaced by a proper cache library in production."*

---

### Scale-out path (Redis)

**Interviewer:** *"Your implementation is in-memory. If you have 50 ad server instances, each has its own rate limiter. A client can bypass the limit by hitting different instances."*

> *"Correct — in-memory only works for single-instance. For distributed enforcement, I move the counter to Redis: `INCRBY rate:{clientId}:{window} 1` with `EXPIREAT` set to window end. Redis INCR is atomic at the server level, so all 50 instances share the same counter. The trade-off: every ad request now requires a Redis roundtrip (~1ms) vs. in-memory (~microseconds). At 300K QPS that's 300K Redis ops/sec — manageable with a Redis Cluster and connection pooling, but it's a cost. Redis Lua script bundles the INCR + limit check into a single atomic operation, eliminating the check-then-INCR race."*

---

## Interview Talking Points Cheatsheet

| Challenge | One-sentence answer |
|---|---|
| Why Token Bucket? | "Allows controlled burst — live sports event spikes; O(1) memory regardless of QPS" |
| Why `computeIfAbsent`? | "Individual ops are atomic; compound ops are not — `containsKey + put` has a race condition" |
| Why not `synchronized` on whole class? | "Per-bucket sync only — different clients never contend; only same-client concurrent requests do" |
| Memory leak? | "`ScheduledExecutorService` evicts clients inactive for 2× window; Caffeine in production" |
| Distributed enforcement? | "Redis INCR + EXPIREAT — atomic at server level; Lua script for INCR + check atomicity" |
| `AtomicLong` vs `synchronized` for counter? | "AtomicLong CAS — no OS lock, no thread suspension; loses to `LongAdder` at extreme contention" |
| Failure mode if Redis down? | "Fail-open for quota limits (temporary overage acceptable); fail-closed for security throttling" |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 15, 2026 | **File created.** Disney Ad Platforms LLD prep. Confirmed question from JioHotstar Staff SWE May 2026. Java-focused: Token Bucket implementation, Strategy+Factory OOP design, full concurrency deep dive (`computeIfAbsent` trap, per-bucket sync, memory eviction, Redis scale-out path). Cross-reference to DocuSign C1 for system-level rate limiting design. |
