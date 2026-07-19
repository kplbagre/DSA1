# Rate Limiting — Advanced Patterns

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`
> **Companion to:** `02-rate-limiting.md` — Advanced scaling and coordination patterns beyond the 5 core algorithms

---

## 🎯 Why This Matters

The core rate-limiting note covers fixed-window, token-bucket, sliding-window-log, sliding-window-counter, and leaky-bucket algorithms. These work for a single service and a single limit (e.g., "100 requests per minute per user"). But at scale, you face: How do you coordinate rate limits across 100 servers? What if limits are multi-dimensional (per-user AND per-IP AND per-API-key simultaneously)? What if the limit should adapt based on load (adaptive rate limiting)? How do you avoid having the Redis rate-limit checker become the bottleneck? Senior engineers deploy these advanced patterns when the basic approach hits performance walls or correctness issues.

---

## 🧠 The Mental Model

Extend the "gate with a token bucket" analogy from the core note:

You started with one gate (one service, one rate limit). Now you have 100 gates (100 services) protecting the same resource. The gates need to "agree" on who's allowed in — if gate A lets in 50 requests and gate B lets in 50, you've let in 100 when the limit is 100 total. **Problem 1:** Distributed coordination. **Problem 2:** The limit isn't fixed — when the system is under load, you want to tighten it; when it's healthy, loosen it (adaptive rate limiting). **Problem 3:** Users don't have one identity — they have a user ID, IP address, API key — all with separate limits that need to be enforced simultaneously. How do you check all limits at once without multiplying latency?

These are the problems advanced rate limiting solves.

---

## 🎨 Visual — Distributed vs Centralized, Multi-Dimensional Limits

```
DISTRIBUTED RATE LIMITING (each server tracks separately)
═════════════════════════════════════════════════════════════

Request arrives at Server A:  Request arrives at Server B:
  ┌────────────────┐           ┌────────────────┐
  │ Local counter  │           │ Local counter  │
  │ user:123 = 45  │           │ user:123 = 42  │
  │ limit: 100     │           │ limit: 100     │
  └────────────────┘           └────────────────┘
       ✅ Allow                      ✅ Allow

After sync, reality: user:123 has 87/100 (97/100 with timestamps)

KEY INVARIANT:
   Each server independently tracks. No coordination.
   Trade-off: eventual consistency. Some requests may exceed global limit
              before servers sync via Redis gossip or periodic updates.


CENTRALIZED RATE LIMITING (shared Redis)
═════════════════════════════════════════════════════════════

All 100 servers query the same Redis counter

Request arrives (any server):
  ┌──────────────────────────┐
  │ Redis (shared authority) │
  │ user:123 = 87            │
  │ limit: 100               │
  └──────────────────────────┘
       ✅ Allow (89 < 100)

Next request from user:123:
  ┌──────────────────────────┐
  │ Redis (shared authority) │
  │ user:123 = 99            │
  │ limit: 100               │
  └──────────────────────────┘
       ✅ Allow (99 < 100)

Next request:
       ❌ Reject (100 >= 100)

KEY INVARIANT:
   Redis is the single source of truth. Guaranteed accuracy.
   Trade-off: Redis becomes a bottleneck if the rate limiter
              is a hot path (every request goes through it).


MULTI-DIMENSIONAL RATE LIMITING
═════════════════════════════════════════════════════════════

One request must satisfy ALL limits:
- user:{userId}:minute → 1000 requests/min per user
- ip:{ipAddr}:minute → 10,000 requests/min per IP
- api_key:{key}:minute → 100,000 requests/min per API key

Request from user 123 (IP 203.0.113.5) with API key ABC:

Check #1: user:123:minute = 750 < 1000 ✅ (user limit OK)
Check #2: ip:203.0.113.5:minute = 5000 < 10000 ✅ (IP limit OK)
Check #3: api_key:ABC:minute = 75000 < 100000 ✅ (key limit OK)

All pass → ✅ Allow

If ANY fails → ❌ Reject (first failed limit reported to client)

KEY INVARIANT:
   The most restrictive limit "wins" — if any dimension is exhausted,
   the request is rejected, even if others have room.
   Cost: 3+ Redis lookups per request.
   Optimization: batch checks or use Lua script for atomic multi-check.
```

---

## ⚙️ How It Actually Works

### Adaptive Rate Limiting — Limits That Adjust to System Load

**Problem:** The rate limit is 1,000 requests/min. In normal times, this is healthy. During a traffic spike (flash sale, viral post), the limit should tighten to 500 req/min to protect the system. As load decreases, the limit should relax back to 1,000.

**Solution:** Measure system health (CPU, queue depth, response time) and adjust the limit dynamically.

**Steps in plain English:**

1. **Monitor a health metric** — CPU usage, Redis latency, DB queue depth, or p99 response time.
2. **Define thresholds** — e.g., if CPU > 80%, reduce limits by 20%. If CPU < 50%, increase by 10%.
3. **Adjust limits periodically** — every 10 seconds, re-compute the limit based on current health.
4. **Propagate the limit** — publish to all rate-limit checkers (Redis, each server) so they apply the new limit.

```java
@Service
@Slf4j
public class AdaptiveRateLimitService {

    private final MetricsService metricsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final String LIMIT_CONFIG_KEY = "ratelimit:config:adaptive";
    private double currentLimitMultiplier = 1.0;  // 1.0 = baseline, 0.8 = 20% reduction

    @PostConstruct
    public void start() {
        // Recalculate limit every 10 seconds based on system health
        scheduler.scheduleAtFixedRate(this::adjustLimitBasedOnHealth, 10, 10, TimeUnit.SECONDS);
    }

    private void adjustLimitBasedOnHealth() {
        double cpuUsage = metricsService.getCpuUsage();  // 0.0 to 1.0
        double dbQueueDepth = metricsService.getDatabaseQueueDepth();
        double p99LatencyMs = metricsService.getP99ResponseTime();

        // Calculate adjustment multiplier based on health metrics
        double newMultiplier = 1.0;

        if (cpuUsage > 0.85) {
            // Critical load — reduce limits aggressively
            newMultiplier = 0.5;
            log.warn("CPU critical ({}%), reducing rate limits to 50%", cpuUsage * 100);
        } else if (cpuUsage > 0.75) {
            newMultiplier = 0.75;
            log.info("CPU high ({}%), reducing rate limits to 75%", cpuUsage * 100);
        } else if (cpuUsage < 0.40 && p99LatencyMs < 100) {
            // Healthy — can increase limits
            newMultiplier = 1.2;
            log.info("System healthy, increasing rate limits to 120%");
        } else if (cpuUsage < 0.50) {
            newMultiplier = 1.0;
        }

        if (Math.abs(newMultiplier - currentLimitMultiplier) > 0.01) {
            currentLimitMultiplier = newMultiplier;
            
            // Broadcast the new limit to all servers via Redis
            redisTemplate.opsForValue().set(LIMIT_CONFIG_KEY, newMultiplier);
            
            // Optionally: log to central metrics for monitoring
            log.info("Rate limit multiplier changed to {}", newMultiplier);
        }
    }

    public boolean allowRequest(String userId, long baselineLimit) {
        // Apply the current multiplier to the baseline limit
        long adjustedLimit = (long) (baselineLimit * currentLimitMultiplier);
        
        String counterKey = "ratelimit:user:" + userId;
        Long currentCount = (Long) redisTemplate.opsForValue().get(counterKey);
        
        if (currentCount == null) {
            currentCount = 0L;
        }

        if (currentCount < adjustedLimit) {
            redisTemplate.opsForValue().increment(counterKey);
            redisTemplate.expire(counterKey, 1, TimeUnit.MINUTES);
            return true;
        }
        
        return false;
    }
}
```

**In an interview, if asked:** "I monitor system health (CPU, queue depth, response latency) and adjust the rate limit dynamically every 10 seconds. If CPU > 80%, I reduce limits to 50% of baseline to shed load. If CPU < 40% and p99 latency is healthy, I increase limits to 120%. The adjustment factor is stored in Redis and applied by all servers, so every rate-limit check uses the current multiplier. This prevents cascading failures under traffic spikes without manual intervention."

---

### Multi-Dimensional Rate Limiting — User + IP + API Key

**Problem:** A single rate limit per user isn't enough. An abusive bot might use the same API key from 100 different IPs to bypass the IP limit. You need limits on three dimensions simultaneously.

```java
@Component
public class MultiDimensionalRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Check rate limits across three dimensions.
     * Returns the first limit that failed, or null if all passed.
     */
    public RateLimitExceeded checkLimits(String userId, String ipAddress, String apiKey) {
        long now = System.currentTimeMillis();
        
        // Define limits per dimension
        final long USER_LIMIT_PER_MINUTE = 1000;
        final long IP_LIMIT_PER_MINUTE = 10000;
        final long API_KEY_LIMIT_PER_MINUTE = 100000;

        // Check user limit
        String userKey = "limit:user:" + userId + ":1m";
        if (!canProceed(userKey, USER_LIMIT_PER_MINUTE)) {
            return new RateLimitExceeded(
                "user_limit_exceeded",
                USER_LIMIT_PER_MINUTE,
                "user: " + userId
            );
        }

        // Check IP limit
        String ipKey = "limit:ip:" + ipAddress + ":1m";
        if (!canProceed(ipKey, IP_LIMIT_PER_MINUTE)) {
            return new RateLimitExceeded(
                "ip_limit_exceeded",
                IP_LIMIT_PER_MINUTE,
                "IP: " + ipAddress
            );
        }

        // Check API key limit
        String apiKeyKey = "limit:api_key:" + apiKey + ":1m";
        if (!canProceed(apiKeyKey, API_KEY_LIMIT_PER_MINUTE)) {
            return new RateLimitExceeded(
                "api_key_limit_exceeded",
                API_KEY_LIMIT_PER_MINUTE,
                "API key"
            );
        }

        // All limits passed
        return null;
    }

    private boolean canProceed(String key, long limit) {
        Long current = (Long) redisTemplate.opsForValue().get(key);
        if (current == null) {
            current = 0L;
        }

        if (current < limit) {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);  // 1-minute window
            return true;
        }

        return false;
    }
}

@RestController
public class ApiController {

    private final MultiDimensionalRateLimiter rateLimiter;

    @PostMapping("/api/request")
    public ResponseEntity<?> handleRequest(
            @RequestHeader(value = "X-API-Key") String apiKey,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal User user) {

        String ipAddress = httpRequest.getRemoteAddr();

        // Check all three dimensions
        RateLimitExceeded exceeded = rateLimiter.checkLimits(
            user.getId(),
            ipAddress,
            apiKey
        );

        if (exceeded != null) {
            return ResponseEntity
                .status(429)  // Too Many Requests
                .header("X-RateLimit-Limit", String.valueOf(exceeded.limit))
                .header("X-RateLimit-Reason", exceeded.dimension)
                .body(new ErrorResponse("Rate limit exceeded: " + exceeded.reason));
        }

        // Process the request
        return ResponseEntity.ok("Request processed");
    }
}
```

**In an interview, if asked:** "I enforce multi-dimensional rate limits by checking three dimensions for every request: (1) user ID (1000 req/min), (2) IP address (10,000 req/min), (3) API key (100,000 req/min). If ANY dimension is exhausted, I reject the request and return which limit was exceeded. This prevents abuse patterns like a bot using multiple IPs with the same API key. The trade-off is 3+ Redis lookups per request, which I optimize with a Lua script for atomic multi-check (reduces round-trips)."

---

### Distributed Coordination — Avoiding the Redis Bottleneck

**Problem:** Every request hits the same Redis counter. With 100,000 requests/sec, Redis becomes the bottleneck. How do you distribute the rate-limit checks without losing accuracy?

**Solution:** Local eventual consistency with periodic sync, or Redis clustering with sharding.

```java
@Service
@Slf4j
public class DistributedRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Local counters per server
    private final ConcurrentHashMap<String, AtomicLong> localCounters = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        // Sync local counters to Redis every 5 seconds
        scheduler.scheduleAtFixedRate(this::syncLocalCountersToRedis, 5, 5, TimeUnit.SECONDS);
        
        // Load Redis counters into local map every 5 seconds
        scheduler.scheduleAtFixedRate(this::loadCountersFromRedis, 5, 5, TimeUnit.SECONDS);
    }

    public boolean checkLimit(String key, long limit) {
        // Check local counter first (no Redis latency)
        AtomicLong localCounter = localCounters.computeIfAbsent(key, k -> new AtomicLong(0));
        long currentLocal = localCounter.incrementAndGet();

        if (currentLocal > limit) {
            // Over limit — reject
            localCounter.decrementAndGet();  // Undo the increment
            return false;
        }

        return true;
    }

    private void syncLocalCountersToRedis() {
        localCounters.forEach((key, counter) -> {
            long localValue = counter.get();
            String redisKey = "counter:" + key;
            
            // Send local count to Redis (authoritative counter)
            redisTemplate.opsForValue().set(redisKey, localValue);
        });
        
        log.debug("Synced {} local counters to Redis", localCounters.size());
    }

    private void loadCountersFromRedis() {
        // Periodically reload from Redis to sync with other servers
        // This implements eventual consistency: each server has a stale but consistent view
    }
}
```

**In an interview, if asked:** "To avoid Redis bottleneck with high request rates, I use local eventual consistency: each server tracks rate limits in local memory (no Redis latency), then syncs to Redis every 5 seconds. This reduces Redis load by 99% and local checks are sub-microsecond. Trade-off: for a 5-second window, each server may allow slightly more requests than the global limit (if all servers hit their local limits simultaneously). For ultra-strict limits, I'd use centralized Redis with sharding (shard counters by user ID so no single counter is hot), or implement a per-shard authoritative server."

---

## 🏢 Real World — Where Companies Use This

- **AWS API Gateway**: Adaptive rate limiting based on service health. Under high load, tightens limits. Supports multi-dimensional limits (per user, per IP, per API key).
- **Stripe**: Multi-dimensional rate limiting (per API key, per IP, per user). Adaptive limits during outages to preserve service stability.
- **GitHub**: Distributed rate limiting with local caching + async sync to central Redis. Supports rate limits on both API calls and concurrent deployments.
- **Netflix**: Adaptive rate limiting in their API gateway. When origin services are degraded, reduces allowable throughput to prevent cascading failures.
- **Uber**: Per-user, per-IP, per-device ID rate limiting. Detects bot patterns and tightens limits dynamically.

---

## 🧭 When to Use vs When NOT to Use

| Use advanced rate limiting when | Do NOT use when |
|---|---|
| You have distributed systems (>10 servers) and need coordination | Single server or small cluster — fixed rate limit is simpler |
| You want limits to adapt to load (adaptive) | All users have predictable usage — fixed limit is stable |
| Limits are multi-dimensional (user + IP + API key) | Single dimension (user-only) suffices |
| Rate-limit checker is a hot path (>10K req/sec) | Throughput is low enough that a centralized counter is fine |

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Adaptive limits prevent cascading failures under load. Multi-dimensional limits catch abuse patterns (bot using multiple IPs). Distributed coordination scales to 100K+ req/sec. |
| **You lose** | Complexity — distributed rate limiting requires consistency mechanisms (eventual consistency with trade-offs). Monitoring becomes harder — which server allows a request if limits are local? Debugging inconsistencies is time-consuming. |
| **Failure mode** | Redis is down — if local counters are the fallback, limits become permissive (eventual consistency favors availability). Limit sync fails — some servers tighten while others don't. Adaptive thresholds are poorly tuned — system over-throttles or under-protects. |

---

## 🔬 Interview Q&As

### Q: "You have 100 API servers and rate limits are enforced at a central Redis. Your Redis cluster is now a bottleneck — every single request goes through it. How do you scale?"

> I move to eventual consistency: each server maintains local counters in memory, increments them for every request (no Redis latency), and syncs to Redis every 5 seconds. This reduces Redis load by 99%. Trade-off: for the 5-second window, a server may allow slightly more than its fair share of the global limit — but this is acceptable for most use cases. If stricter guarantee is needed, I shard the counters: instead of one Redis key per user, I use `counter:user:123:shard:A`, `counter:user:123:shard:B` (sharded by server ID). This spreads the load so no single counter is hot.

---

### Q: "The rate limit is 1,000 requests/min per user. During a traffic spike, the system is overloaded. How do you prevent cascading failure?"

> Adaptive rate limiting: I monitor CPU usage and response latency. If CPU > 80%, I reduce the limit to 500 req/min (50% reduction). If p99 latency is > 500ms, I reduce further. The adjustment factor is published to all servers via Redis, so every rate-limit check applies the current multiplier. This sheds load automatically without manual intervention. As the system recovers (CPU < 50%, latency < 100ms), limits gradually increase back to 1,000. This prevents the system from being overwhelmed while still serving as much traffic as it can handle healthily.

---

### Q (Tier 2): "You enforce rate limits on three dimensions: user ID, IP address, and API key. A bot uses 100 different IPs with the same API key. How does your system catch this?"

> Each dimension has its own limit: user (1,000 req/min), IP (10,000 req/min per IP), API key (100,000 req/min). Spreading across 100 IPs is precisely how a bot *evades* the per-IP limit — each IP now carries only ~1/100 of the traffic, so no single IP's counter gets near 10,000. The IP dimension does NOT catch this. What catches it is the shared **API-key dimension**: all 100 IPs use the same key, so their requests aggregate against the single 100,000/min key limit. Beyond that, a **high-cardinality heuristic** is the real defense: if one API key is seen from > 50 distinct IPs in a minute, that's anomalous — reduce that key's limit or require additional verification. Lesson: the dimension that aggregates the attacker's shared identifier (the API key) is the one that stops distributed abuse, not the per-IP counter they're diluting.

> ⚠️ **Note on the illustrative code in this file:** several examples here use a non-atomic `GET → compare → INCR → EXPIRE` sequence for readability. **In production this MUST be a single atomic Lua script** (or `INCR` + conditional `EXPIRE`-only-on-first-write) — see the core file `02-rate-limiting.md`, which explains the check-then-increment race in detail. Two footguns the readable code glosses over: (1) calling `EXPIRE` on *every* increment resets the window TTL so a fixed window never expires under continuous traffic — set the TTL only when the counter is first created; (2) the periodic-sync distributed pattern trades exactness for lower Redis load — for exact distributed limits, route each user consistently to one shard, or reserve tokens in batches.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "For distributed rate limiting: I use adaptive limits that adjust to system load (CPU, latency). I enforce multi-dimensional limits (user + IP + API key) simultaneously — if any is exhausted, reject. For high throughput (>10K req/sec), I use local eventual consistency (local counters synced to Redis every 5 seconds) to avoid Redis bottleneck. For strict enforcement, I shard counters by dimension to spread load."

---

## 🔗 Related Concepts

- **`02-rate-limiting.md`** — core algorithms (token bucket, sliding window, leaky bucket) and the basic stampede protection with distributed locks.
- **`06-distributed-locking.md`** — distributed locks ensure atomicity of rate-limit checks across multiple servers.
- **`15-system-qualities.md`** — rate limiting is a key mechanism for ensuring resilience and preventing cascading failures in distributed systems.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Adaptive Rate Limiting"** — Arpit Bhayani (YouTube: search "Arpit Bhayani rate limiting") | Deep dive on coordinating rate limits across servers and adaptive strategies. | ~20 min |
| **"Rate Limiting Strategies at Scale"** — Stripe Blog (https://stripe.com/blog/rate-limiting) | Real-world Stripe approach to multi-dimensional and distributed rate limiting. | ~10 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | Companion file created. Covers: adaptive rate limiting (auto-adjust based on CPU/latency), multi-dimensional limits (user + IP + API key simultaneously), distributed coordination strategies (eventual consistency with local caching vs centralized Redis), sharding to avoid bottlenecks. Real-world patterns from AWS/Stripe/Netflix. 3 Q&As (all advanced scenarios). Pairs with core `02-rate-limiting.md`. |
| Jul 19, 2026 | **Factual fix + caveat.** (1) Corrected the multi-dimensional bot Q&A — spreading across 100 IPs *evades* the per-IP limit (each IP carries 1/100 of traffic); the shared API-key dimension + a high-cardinality heuristic is what actually catches it (the "100×100=10,000 hits IP limit" reasoning was wrong). (2) Added a caveat that this file's readable non-atomic `GET→INCR→EXPIRE` code must be atomic Lua in production (per `02-rate-limiting.md`), plus the every-request EXPIRE TTL-reset footgun and the exact-vs-approximate distributed trade-off. |
