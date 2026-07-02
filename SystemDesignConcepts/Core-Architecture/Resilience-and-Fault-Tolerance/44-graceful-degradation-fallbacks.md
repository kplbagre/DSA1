# 44 — Graceful Degradation & Fallbacks

## 📖 What is Graceful Degradation?

**Full form:** Graceful Degradation — the design property of a system that continues serving a reduced-quality but still useful response when part of the system is unavailable, rather than returning an outright error.

**Simple analogy:** A car with a flat tire does not stop dead on the highway — it slows to 30 mph and you limp to the nearest garage. You lose speed and comfort, but you do not lose the ability to move. Without a run-flat tire or spare, the car is simply stranded. Graceful degradation is the "run-flat" design principle applied to software systems.

**Core principle:** Every call to a downstream dependency is wrapped with a fallback strategy: if the dependency is unavailable or too slow, the system returns the best available alternative — stale cached data, a default value, a simplified feature, or static content — instead of propagating the failure upward as an error.

**Why it matters in system design:** Hard failures cascade. When service B returns HTTP 500, service A should not amplify that into its own 500. Graceful degradation limits the blast radius of partial failures to the specific feature that depends on the broken service, keeping the core user journey intact.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| Graceful Degradation | Returning a reduced-quality but still useful response when a dependency fails — instead of an outright error | Recommendations widget shows stale cached list instead of returning HTTP 503 |
| Fallback | The alternative response or data source used when the primary dependency is unavailable | Tier 1: live data; Tier 2: stale cache; Tier 3: static default |
| Fallback Hierarchy | An ordered cascade of tiers from highest quality (live) to lowest (static default) — each tier is more available than the one above | Live reco → Redis cache → DB bestsellers → empty list |
| Stale Cache | Cached data that is past its freshness TTL but still returned because the live source is unavailable | Redis holds last-known recommendations from 5 minutes ago; served during outage |
| Blast Radius | The scope of users or features affected by a failure — graceful degradation limits blast radius to the specific feature's fallback chain | Recommendations service down → only "Customers also bought" section is empty; checkout unaffected |
| Timeout | A maximum wait duration for a downstream call — when exceeded, the fallback triggers immediately rather than blocking indefinitely | `CompletableFuture.get(500, TimeUnit.MILLISECONDS)` — after 500ms, use cache |
| Fail-Open | A fallback strategy that allows requests through even when a safety check (e.g., fraud scoring) is unavailable — trades safety for availability | Fraud service down → allow order and flag for manual review |
| Fail-Closed | A fallback strategy that denies requests when a safety check is unavailable — trades availability for safety | Auth service down → reject all requests rather than allow unauthenticated access |

---

## 🎯 Why This Matters

- **Problem solved:** Without fallbacks, a single slow or unavailable downstream service (recommendations, fraud scoring, personalization) causes the entire request to fail — including parts of the page or flow that do not depend on that service at all.
- **Interview signal:** Appears in every high-availability design — e-commerce checkout, streaming platforms, payment flows. Any "what happens when X is down?" follow-up question is a graceful degradation question.
- **Senior expectation:** You must describe the full fallback hierarchy (live → cache → default), know how to implement timeouts paired with fallbacks, and explain the difference between graceful degradation and circuit breakers (complementary, not synonyms).

---

## 🧠 The Mental Model

Imagine a restaurant with five features: a full à la carte menu, a daily specials board, a wine pairing recommendation, a custom birthday dessert, and ambient background music.

**Failing hard (no degradation):** The kitchen's wine cellar is locked (wine service is down). The restaurant locks the entire front door. No food, no birthday cake, no music. Every customer turned away because one dependency is unavailable.

**Graceful degradation:** The maître d' apologizes: "Our sommelier is unavailable tonight. Here's our house wine suggestion from last week's menu [stale cache]. All food orders are fully available [core flow intact]. The birthday dessert is a standard cheesecake tonight since the pastry team is short-staffed [simplified feature, not the custom one]. Music is playing from a pre-recorded playlist [static fallback]." The restaurant degrades in quality but never closes.

**The fallback hierarchy in the mental model:**
1. Full feature — live wine recommendation from sommelier (live data)
2. Cached suggestion — "last week's popular pairing" from the memory pad (recent cache)
3. Static list — the printed house wine menu that never changes (last-known-good)
4. Empty list — "no recommendation tonight, pick any you like" (safe default value)
5. Visible error only for that specific section — everything else still works (scoped failure)

**The key insight is:** Graceful degradation replaces a binary "everything or nothing" failure mode with a spectrum of degraded states — each tier still delivers value to the user, just less of it.

---

## 🎨 Visual — System Topology & Component Flow

```
FULL SYSTEM TOPOLOGY:
                                           ┌──────────────────────────────────────┐
                                           │  Service Tier (Order/Product Service) │
Client Tier     Load Balancer              │                                        │
┌─────────┐    ┌─────────────┐    ┌───────┴──────────────────────────────────────┐│
│ Browser │───▶│     LB      │───▶│  Service Pod                                  ││
│ Mobile  │    │             │    │                                                ││
└─────────┘    └─────────────┘    │  ┌─────────────────────────────────────────┐  ││
                                  │  │ DEGRADATION INTERCEPT LAYER             │  ││
                                  │  │  (Resilience4j / Hystrix / custom)      │  ││
                                  │  │                                         │  ││
                                  │  │ For each downstream call:               │  ││
                                  │  │  1. Set timeout (e.g., 500ms)           │  ││
                                  │  │  2. Try live dependency                 │  ││
                                  │  │  3. On failure/timeout → try cache      │  ││
                                  │  │  4. On cache miss → return default      │  ││
                                  │  └──────────────────┬──────────────────────┘  ││
                                  │                     │                          ││
                                  │     ┌───────────────┼───────────────────┐      ││
                                  │     ▼               ▼                   ▼      ││
                                  │  ┌──────┐    ┌────────────┐    ┌──────────┐   ││
                                  │  │Reco. │    │  Fraud /   │    │  Cache   │   ││
                                  │  │Svc   │    │  Scoring   │    │  (Redis) │   ││
                                  │  │(may  │    │  Svc       │    │          │   ││
                                  │  │ fail)│    │  (may fail)│    │  stale   │   ││
                                  │  └──────┘    └────────────┘    │  data    │   ││
                                  │                                 │  default │   ││
                                  │                                 └──────────┘   ││
                                  └──────────────────────────────────────────────┘│
                                           └──────────────────────────────────────┘

COMPONENT DETAIL — Fallback Cascade:

Request for product recommendations:

  ┌─────────────────────────────────────────────────────────────────────┐
  │ TIER 1: Live Data (Recommendation Service)                          │
  │   Call reco-service within 500ms timeout                           │
  │   ✅ Success → return personalized recommendations                  │
  │   ❌ Timeout / 5xx → fall to Tier 2                                 │
  └────────────────────────────────┬────────────────────────────────────┘
                                   │ (failure)
                                   ▼
  ┌─────────────────────────────────────────────────────────────────────┐
  │ TIER 2: Recent Cache (Redis, TTL=5min, stale-while-revalidate)      │
  │   Read last known reco list from Redis                              │
  │   ✅ Cache hit → return stale recommendations + background refresh  │
  │   ❌ Cache miss / Redis down → fall to Tier 3                       │
  └────────────────────────────────┬────────────────────────────────────┘
                                   │ (miss)
                                   ▼
  ┌─────────────────────────────────────────────────────────────────────┐
  │ TIER 3: Last-Known-Good (Static Category Bestsellers from DB)       │
  │   SELECT top 10 products by sales_count WHERE category=X           │
  │   ✅ Found → return category bestsellers (not personalized)         │
  │   ❌ DB slow / error → fall to Tier 4                               │
  └────────────────────────────────┬────────────────────────────────────┘
                                   │ (failure)
                                   ▼
  ┌─────────────────────────────────────────────────────────────────────┐
  │ TIER 4: Static Default (Hardcoded / Config-Driven Defaults)         │
  │   Return empty list or a hardcoded editorial selection              │
  │   ✅ Always succeeds — no external dependency                       │
  │   Used as last resort before showing an empty section               │
  └─────────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
  Each tier is strictly more available than the tier above it.
  The fallback chain always terminates at a tier with no external dependency.
  The core user flow (checkout, purchase, login) is NEVER blocked by
  a non-critical feature's fallback chain.
```

---

## ⚙️ How It Actually Works

**Steps — Timeout + Fallback:**
1. **Set an aggressive timeout** — define the maximum acceptable wait for the live dependency (e.g., 500ms). Do not let an unresponsive service hold threads indefinitely.
2. **Attempt the live call** — call the downstream service wrapped in a try/catch or a future with a timeout.
3. **On failure or timeout, consult cache** — read from Redis. If the cache holds a recent value (even stale), return it immediately. Trigger a background refresh to update the cache asynchronously.
4. **On cache miss, return the configured default** — an empty list, a static value, or a simplified response. Log the degradation event for observability.
5. **Return HTTP 200 with the best available data** — do not return HTTP 503 to the client unless all tiers are exhausted AND the core feature (not a supporting feature) is unavailable.

**Steps — Stale-While-Revalidate:**
1. **Check Redis TTL** — if the key exists but is past its "freshness" threshold (a secondary TTL), serve the stale value immediately.
2. **Trigger async refresh** — submit a background task (ExecutorService or CompletableFuture) to call the live service and update the cache without blocking the current request.
3. **Next request sees fresh data** — by the time the next client asks, the background refresh has completed and the cache is warm.

### What is Resilience4j, and why does it fit here?

**Plain English:** Resilience4j is a Java library that wraps method calls with fault-tolerance behaviors: circuit breakers (fail-fast when a service is down), rate limiters, retries, and bulkheads. It is the successor to Netflix Hystrix and integrates natively with Spring Boot via annotations. In an interview, if asked: "Resilience4j provides the @CircuitBreaker and @Fallback annotations that let you declare a fallback method to invoke whenever the primary method fails or times out — keeping the implementation clean and testable."

```java
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
public class RecommendationService {

    private final RecommendationClient recoClient;
    private final StringRedisTemplate redis;
    private static final String RECO_CACHE_KEY = "user:reco:";
    private static final long CACHE_TTL_SECONDS = 300L;

    public RecommendationService(RecommendationClient recoClient,
                                 StringRedisTemplate redis) {
        this.recoClient = recoClient;
        this.redis = redis;
    }

    // Step 1: Resilience4j CircuitBreaker + automatic fallback method wiring
    // If getRecommendations() fails or times out, Spring calls getRecommendationsFallback()
    @CircuitBreaker(name = "recoService", fallbackMethod = "getRecommendationsFallback")
    public List<String> getRecommendations(String userId) {
        String cacheKey = RECO_CACHE_KEY + userId;

        // Step 2: Try live service first (wrapped by circuit breaker)
        List<String> liveRecos = recoClient.fetchRecommendations(userId);

        // Step 3: Update cache on success (async — do not block response)
        CompletableFuture.runAsync(() -> {
            String serialized = String.join(",", liveRecos);
            redis.opsForValue().set(cacheKey, serialized, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        });

        return liveRecos;
    }

    // Step 4: Fallback method — invoked by Resilience4j on circuit open or exception
    // Signature must match primary method + one extra Throwable parameter
    public List<String> getRecommendationsFallback(String userId, Throwable ex) {
        String cacheKey = RECO_CACHE_KEY + userId;

        // Step 4a: Tier 2 — try stale cache
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return List.of(cached.split(","));
        }

        // Step 4b: Tier 3 — return safe static default (empty list = no recommendations shown)
        // Core checkout flow is unaffected — only the recommendations widget is empty
        return List.of();
    }
}
```

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;

// CompletableFuture with explicit timeout + fallback (no framework dependency)
public class ProductPageService {

    private final RecommendationClient recoClient;
    private final CacheService cache;

    public ProductPageService(RecommendationClient recoClient, CacheService cache) {
        this.recoClient = recoClient;
        this.cache = cache;
    }

    public List<String> getRecommendationsWithTimeout(String userId) {
        // Step 1: Start async call to live service
        CompletableFuture<List<String>> liveFuture = CompletableFuture.supplyAsync(
            () -> recoClient.fetchRecommendations(userId)
        );

        try {
            // Step 2: Wait maximum 500ms for live data
            return liveFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            // Step 3: Timeout or exception — consult cache
            List<String> cached = cache.getRecentRecos(userId);
            if (cached != null && !cached.isEmpty()) {
                // Step 3a: Serve stale cache, kick off background refresh
                CompletableFuture.runAsync(() -> cache.refresh(userId, recoClient));
                return cached;
            }
            // Step 3b: No cache — return safe default (empty, not error)
            return List.of();
        }
    }
}
```

```java
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Stale-while-revalidate pattern in Redis
public class StaleWhileRevalidateCache {

    private final StringRedisTemplate redis;
    private static final long FRESH_TTL = 60L;    // serve as-is for 60s
    private static final long STALE_TTL = 300L;   // serve stale for up to 5min

    public StaleWhileRevalidateCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(String key, java.util.function.Supplier<String> loader) {
        String value = redis.opsForValue().get(key);
        Long ttlRemaining = redis.getExpire(key, TimeUnit.SECONDS);

        if (value != null && ttlRemaining != null && ttlRemaining > (STALE_TTL - FRESH_TTL)) {
            // Step 1: Cache is fresh — return immediately, no refresh needed
            return value;
        }

        if (value != null) {
            // Step 2: Cache is stale but exists — serve immediately + refresh async
            CompletableFuture.runAsync(() -> {
                String fresh = loader.get();
                redis.opsForValue().set(key, fresh, STALE_TTL, TimeUnit.SECONDS);
            });
            return value;
        }

        // Step 3: Cache miss — load synchronously and warm cache
        String fresh = loader.get();
        redis.opsForValue().set(key, fresh, STALE_TTL, TimeUnit.SECONDS);
        return fresh;
    }
}
```

### What is stale-while-revalidate, and why does it fit here?

**Plain English:** Stale-while-revalidate is a caching strategy where the server immediately returns the cached value (even if expired) and simultaneously triggers a background refresh so the next request sees fresh data. It eliminates the "thundering herd" problem where all requests block waiting for a cache miss to be filled. In an interview, if asked: "Stale-while-revalidate serves the stale cached response in O(1) while a background task refreshes it — latency never spikes during cache expiry, and the live service is protected from burst traffic."

---

## 🏢 Real World — Where Companies Use This

- **Netflix (recommendation engine):** When the recommendation service experiences elevated latency, Netflix serves the last cached personalized list. If the cache is also unavailable, it falls back to pre-computed top-10 genre lists generated nightly. The streaming player and all playback controls remain fully functional — only the "because you watched" row degrades.
- **Amazon (product detail pages):** Product page assembly pulls from 20+ microservices (reviews, pricing, inventory, recommendations, Q&A). Each component is independently fallback-wrapped. A reviews service outage shows "reviews unavailable" in that section while pricing, add-to-cart, and product images are served from cache. The checkout flow is never blocked by a review service failure.
- **Twitter / X (home timeline under DB pressure):** When primary DB replicas lag under spike load, Twitter serves the timeline from a pre-materialized Redis cache (even if 30–60 seconds stale). Users see a slightly older timeline rather than a spinner or error. New tweet compose functionality continues working via a separate write path.
- **Stripe (fraud scoring):** Stripe's payment processing pipeline uses cached fraud scores with a TTL. If the live ML fraud-scoring service is unavailable, Stripe uses the last computed score for the card/merchant pair (may be minutes old) rather than blocking the payment entirely. Only scores outside a confidence threshold trigger a hard block.
- **Swiggy / Zomato (restaurant recommendations):** During peak dinner hours when the personalization engine is overloaded, these platforms serve cached "trending near you" lists pre-computed at 15-minute intervals. The order placement flow is never affected — only the recommendation widget temporarily shows non-personalized content.

---

## 🧭 When to Use vs When NOT to Use

| Use graceful degradation when | Do NOT use when |
|---|---|
| The failing feature is non-critical to the core user flow (recommendations, personalization, analytics) | The failed component is the core feature (payment processing, authentication, order placement) — you must fail explicitly here |
| Stale data is acceptable for the use case (cached feed, last-known price, default settings) | Data accuracy is mandatory and stale data causes direct harm (real-time stock prices, medical dosage data, financial settlement amounts) |
| You can distinguish between "degraded" and "broken" in the response (e.g., an empty recommendations list vs a missing price) | You cannot distinguish degraded quality from correctness — and showing a wrong answer is worse than showing no answer |
| The system is under temporary load spike and the service will recover shortly | The downstream service has a data corruption bug — serving stale/cached data propagates the corruption |
| You control the fallback quality and can clearly communicate degradation to the user | Regulatory requirements mandate real-time data accuracy (compliance, audit trails) |

**The common mistake:** Engineers implement fallbacks for non-critical features (recommendations, social proof) but forget to add explicit timeouts, so the fallback never triggers — instead, requests hang for 30 seconds waiting for the live service, exhausting thread pools and causing the degradation pattern to cause the very cascading failure it was meant to prevent.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | High availability for the core user journey under partial failures; reduced blast radius when downstream services degrade; lower perceived latency (stale cache is faster than a live 5-second call); circuit breaker + fallback together prevent cascading failures from propagating across service boundaries. |
| **You lose** | Increased code complexity — every fallback path must be tested, monitored, and maintained as a first-class code path; stale data can mislead users (a cached "in stock" status for an item that just sold out); debugging is harder because errors are swallowed by fallbacks and may not surface in error metrics without explicit logging. |
| **Failure mode** | Silent degradation — the system is serving degraded responses (stale cache, empty defaults) without alerting on-call engineers, because the fallbacks make everything look like HTTP 200. Always emit metrics that distinguish "live data served" from "fallback tier N served" — otherwise degradation becomes invisible until it becomes catastrophic. |

---

## 🔬 Interview Q&As

### Q: "What is the difference between a circuit breaker and graceful degradation?"

> Circuit breakers and graceful degradation are complementary, not synonymous. A circuit breaker is a state machine that detects when a downstream service is unhealthy and stops sending requests to it (fail-fast). Graceful degradation is what you do WITH that failure signal — you serve a reduced-quality response (stale cache, default value) instead of propagating the error to the client. The circuit breaker detects and isolates the failure; graceful degradation is the recovery strategy that follows.

### Q: "How would you implement a fallback for a recommendation service that can be down?"

> I would wrap the recommendation call with Resilience4j's @CircuitBreaker annotation, specifying a fallbackMethod. The fallback checks Redis for the last cached recommendation list (TTL 5 minutes). If the cache is empty, it returns an empty list rather than an error. The checkout flow never sees a failure — it just renders without a recommendations widget. I would also emit a metric each time the fallback fires, so we can alert when degradation persists beyond expected thresholds.

### Q: "What is stale-while-revalidate and when do you use it?"

> Stale-while-revalidate is a caching pattern that returns cached data immediately (even past its freshness TTL) and simultaneously triggers an async background refresh. It is ideal when: (1) occasional staleness is acceptable (feeds, recommendations, metadata), (2) the live service is slow (avoid blocking clients for fresh data), and (3) you want to protect the live service from thundering herd on cache expiry. The tradeoff is that one client per expiry cycle sees slightly stale data — subsequent clients see fresh data after the background refresh completes.

### Q: "During the Amazon Big Billion Day sale, how does Flipkart keep product pages working when 50K users hit the same product simultaneously?" (Tier 2 — real scenario)

> Flipkart pre-warms product page caches (price, inventory snapshot, images) 30 minutes before the sale. During the sale, product page assembly reads from Redis exclusively for all non-transactional fields. Only the "add to cart" button triggers the live inventory service. If the inventory service is slow, the button shows "Checking availability..." with a 500ms timeout — on timeout, it shows "Add to cart (limited stock)" using the last-seen inventory signal from cache rather than blocking. Personalized recommendations are entirely disabled via a feature flag — returning a static "Deals of the Day" editorial list — freeing up capacity for the checkout path.

### Q: "What happens if your Redis cache node goes down while you're serving fallback responses from it?" (Tier 2)

> This is a double failure: the live service is down AND the cache is down. The fallback chain must be designed with at least one tier that has no external dependency — a hardcoded static default or a local in-memory cache (Caffeine) on the service pod itself. In Resilience4j, this means the fallback method itself must not call any external system that could fail. A common implementation: the service pod holds a local Caffeine cache of the last N responses it successfully computed; if both Redis and the live service are unreachable, the local in-memory value is returned. Local cache is eventually consistent (only as fresh as the last successful response this pod saw) but always available.

### Q: "How do feature flags help with graceful degradation under load?"

> Feature flags let you disable specific expensive features (ML-based personalization, real-time pricing, live inventory checks) at runtime without a code deployment. Under load, a pre-defined degradation runbook switches flags: recommendations → cached → disabled; real-time stock check → cached snapshot; user analytics → fire-and-forget async. This turns graceful degradation from a code-level concern into an operational one — SREs can execute the runbook in seconds. Tools like LaunchDarkly or internal flag stores integrate with the fallback hierarchy so that when a flag is off, the code path immediately skips the live call and returns the configured fallback value.

### Q: "How do you monitor whether your graceful degradation is working correctly?"

> The key metric is not error rate (which will look fine if fallbacks return HTTP 200) but fallback invocation rate per tier. I emit a counter for every fallback activation: `reco.fallback.cache_hit`, `reco.fallback.cache_miss`, `reco.fallback.static_default`. A dashboard shows the ratio of live vs fallback responses over time. Alert thresholds: if fallback tier 1 (cache hit) exceeds 10% of traffic for >5 minutes, page the on-call team — it means the live service has been degraded for long enough that operational attention is needed, even though users are not seeing errors.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Graceful degradation replaces a hard failure with a fallback hierarchy — live data → stale cache → static default — so the core user journey returns HTTP 200 even when non-critical dependencies are down, at the cost of potentially stale or simplified responses."

---

## 🔗 Related Concepts

- `./20-circuit-breaker-resilience.md` — Circuit breakers detect the failure that triggers the fallback chain
- `./39-bulkheads-resource-isolation.md` — Bulkheads prevent a degraded service from starving threads needed to serve other features
- `./35-retry-exponential-backoff-patterns.md` — Retries are the first response to transient failures; fallbacks are for persistent ones
- `../../Foundations/Data-Fundamentals/03-caching.md` — Cache patterns (TTL, write-through, write-behind) underpin the stale-while-revalidate fallback tier

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Hystrix: Latency and Fault Tolerance for Distributed Systems** — Netflix Tech Blog | Netflix's original design rationale for command-pattern fallbacks; explains the thread isolation model that Resilience4j inherits | ~10 min read |
| **Resilience4j documentation — CircuitBreaker + Fallback** — resilience4j.readme.io | Official API reference for @CircuitBreaker, @TimeLimiter, and fallbackMethod wiring in Spring Boot; adds configuration detail beyond what's in the note | ~8 min read |
| **Designing for Failure in a Microservices Architecture** — AWS Architecture Blog | Broader framework covering timeout budgets, fallback hierarchies, and feature flagging as operational degradation controls | ~12 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Note created. Covers full fallback hierarchy (live → cache → static → default), Resilience4j @CircuitBreaker with fallback method, CompletableFuture timeout + fallback, stale-while-revalidate Redis pattern, feature flag degradation, shadow mode, and monitoring fallback invocations. Five real-world examples. Seven Q&As including two Tier-2 probe questions. |
