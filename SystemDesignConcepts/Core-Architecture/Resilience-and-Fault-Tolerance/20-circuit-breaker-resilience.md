# Circuit Breaker & Resilience Patterns — Fundamentals

---

## 🎯 Why This Matters

Your payment service calls the fraud detection service. Fraud service is down. Your code retries 10 times, each taking 5 seconds = 50 seconds of hanging. User's payment times out. Cascading failure spreads. Circuit breaker stops this: detects the service is down, immediately fails with HTTP 503, lets traffic redirect to fallback logic. At SDE 3: you must know circuit breaker states (closed/open/half-open), how to pair with retry limits and exponential backoff, and when a service is genuinely overloaded vs temporarily degraded.

---

## 📖 What is Circuit Breaker?

**Full form:** Circuit Breaker Pattern / Fail-Fast Mechanism

**Simple analogy:** A circuit breaker in your house detects electrical overload and trips (cuts off) to prevent fire. In services, a circuit breaker detects that a downstream service is down or overloaded and **stops sending requests to it** — failing fast instead of hanging. After the service recovers, the breaker cautiously allows traffic again.

**Core principle:** When calling a service, the circuit breaker tracks success/failure rates. If failures exceed a threshold (e.g., 50% of last 10 requests failed), the breaker "trips" (opens), rejecting all new requests immediately with an error or fallback response. After a timeout, it enters "half-open" mode: test requests check if the service recovered. If tests pass, close the breaker; if they fail, reopen.

**Three states:**
- **Closed:** Service is healthy; requests pass through normally.
- **Open:** Service is down/overloaded; all requests immediately fail (fail-fast).
- **Half-open:** Testing mode; allow limited requests to check if service recovered.

**Why it matters in system design:** Circuit breakers prevent **cascading failures** — if service B is down, service A doesn't waste resources retrying forever. It fails fast, allowing the system to degrade gracefully.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Closed State** | circuit is healthy; requests pass through to downstream service normally | Service B returning 200s → circuit stays closed, all calls go through |
| **Open State** | circuit is tripped; all requests fail immediately without reaching the downstream service | 6 of last 10 calls failed → circuit opens; next 1000 calls return 503 instantly |
| **Half-Open State** | test mode after a timeout; a few requests let through to check if the service recovered | after 30s open, 3 test requests sent → all succeed → circuit closes |
| **Sliding Window** | failure rate measured over last N requests or last N seconds — not all-time history | 10-request window: 5 of last 10 failed (50%) → threshold met → open |
| **Failure Threshold** | the failure rate percentage that triggers the circuit to open | `failureRateThreshold = 50` → trip when ≥50% of sliding window requests fail |
| **Cascading Failure** | downstream failure propagates upstream; one crashed service brings down callers | Payment → Fraud → Inventory: Fraud down → Payment threads hang → Payment down |
| **Bulkhead Isolation** | separate thread pools per downstream dependency so one slow service can't exhaust all threads | 20 threads for Service B calls; if B hangs, Service C's 10 threads still work |
| **Fallback Method** | pre-defined response returned when circuit is open, instead of propagating an error | `getProductRatings()` fallback: return cached ratings or empty list |
| **Resilience4j** | Java library implementing circuit breaker, retry, rate limiter, and bulkhead patterns | `@CircuitBreaker(name = "fraudService", fallbackMethod = "defaultFraud")` |

---

## 🎨 Visual — System Topology: Circuit Breaker in Architecture

```
SERVICE A (calling downstream services)
    │
    │ HTTP request to Service B
    │
    ▼
┌────────────────────────────┐
│ Service A Container        │
│                            │
│ ┌──────────────────────┐  │
│ │ Circuit Breaker      │  │
│ │ for Service B        │  │
│ │                      │  │
│ │ State: CLOSED/OPEN   │  │ ← Tracks success/failure
│ │ Fail-fast logic      │  │
│ └──────────┬───────────┘  │
│            │               │
│            ├─ CLOSED:     │ (healthy) → pass through
│            ├─ OPEN:       │ (down) → fail immediately
│            └─ HALF-OPEN:  │ (testing) → limited requests
└────────────┼───────────────┘
             │
    ┌────────▼────────────┐
    │ Service B (remote)  │
    │ ✓ Healthy or        │
    │ ✗ Down/Overloaded   │
    └─────────────────────┘

ALSO IN SERVICE A: BULKHEAD ISOLATION
┌──────────────────────────────────────┐
│ Thread Pool (100 threads total)      │
│                                      │
│ ┌─────────────┐  ┌─────────────┐   │
│ │Bulkhead: B  │  │Bulkhead: C  │   │
│ │20 threads   │  │10 threads   │   │
│ │(for calls   │  │(for calls   │   │
│ │ to B)       │  │ to C)       │   │
│ └─────────────┘  └─────────────┘   │
│                                      │
│ If B crashes and all 20 B threads   │
│ hang, C still has 10 free threads   │
│ → C calls don't starve              │
└──────────────────────────────────────┘
```

---

## 🎨 Visual — Circuit Breaker States & Transitions (Component Detail)

Imagine a power distribution system in your house:

- **Circuit breaker (the device):** Detects electrical overload (too much current flowing). Trips (opens) to cut off the circuit, protecting the system from fire.
- **Closed state (normal):** Power flows freely.
- **Open state (tripped):** No power flows. The circuit is broken. All appliances go dark.
- **Half-open state (test mode):** After 30 seconds, the breaker cautiously closes slightly to test if the overload is fixed. If power flows normally now, fully close. If overload returns, open again.

**In services:**

- **Closed:** All requests go through to Service B. Healthy.
- **Open:** Service B is down/overloaded. Stop sending requests. Return error immediately (fail fast). This prevents cascading failure.
- **Half-open:** Service B might be healthy again. Send 1–5 test requests. If they succeed, close the circuit (resume normal traffic). If they fail, open again.

**Bulkhead isolation:** Partition your thread pool. Instead of one pool serving all downstream services, allocate 20 threads for service B, 10 for service C. If B crashes and threads hang, only B's 20 threads are stuck; C's 10 threads continue. This prevents one failing service from starving others.

**The key insight:** Circuit breaker fails fast; bulkhead isolates failures; retry with backoff recovers transient errors.

---

## 🎨 Visual — Circuit Breaker States & Transitions

```
SERVICE A calling SERVICE B

                       ┌──────────┐
                       │ START    │
                       └────┬─────┘
                            │
                            ↓
                    ┌────────────────┐
                    │ CLOSED STATE   │ ← all requests pass through
                    │ (healthy)      │
                    │ Success count++│
                    └────────┬───────┘
                             │
                   Threshold exceeded?
                   (5 failures in 10 req)
                             │
                             ↓ YES
                    ┌────────────────┐
                    │ OPEN STATE     │ ← CIRCUIT BREAKS
                    │ (trip switch)  │   ✗ Fail immediately
                    │ Reject req w/ 503 Return fallback
                    │ Start timer    │   Don't call B
                    └────────┬───────┘
                             │
                   Timeout elapsed?
                   (30 seconds)
                             │
                             ↓ YES
                    ┌────────────────┐
                    │ HALF-OPEN      │ ← TEST MODE
                    │ (cautious)     │
                    │ Allow 1-5 req  │ Limited requests to test
                    └────────┬───────┘
                    ┌────────┴─────────────────┐
                    │                          │
              All tests pass?          Test failed?
                    │                          │
                    ↓ YES                      ↓ NO
            ┌────────────────┐        ┌────────────────┐
            │ Back to CLOSED │        │ Back to OPEN   │
            │ Resume traffic │        │ Service still down
            └────────────────┘        └────────────────┘
                    │                          │
                    └──────────┬───────────────┘
                               ↓
                    (cycle repeats)

THREAD POOL BULKHEAD ISOLATION:
┌─────────────────────────────────────────────┐
│ Service A (total 100 threads)               │
├─────────────────────────────────────────────┤
│ ┌──────────────┐  ┌──────────────┐         │
│ │ Bulkhead: B  │  │ Bulkhead: C  │ ...     │
│ │ Max 20 req   │  │ Max 10 req   │         │
│ │ Active: 20/20│  │ Active: 5/10 │         │
│ │ 🔴 SATURATED │  │ 🟢 Healthy   │         │
│ └──────────────┘  └──────────────┘         │
│                                             │
│ Service B crashes ❌                        │
│ → All 20 of B's threads hang               │
│ → C still has 5 slots free!                │
│ → Other requests can reach C               │
└─────────────────────────────────────────────┘

RETRY + EXPONENTIAL BACKOFF:
Attempt 1: immediate (fail)
  delay = 100ms
Attempt 2: after 100ms (fail)
  delay = 200ms
Attempt 3: after 300ms (fail)
  delay = 400ms
Attempt 4: after 700ms (success)
  ✓ Total time = 700ms, not 50s

KEY INVARIANTS:
   Circuit breaker prevents cascading failure (fail fast)
   Bulkhead isolation limits blast radius (one service failure)
   Exponential backoff avoids thundering herd on retry
   Half-open testing allows gradual recovery
```

---

## ⚙️ How It Actually Works

**Pattern 1: Circuit Breaker (Resilience4j)**

**Steps:**
1. Track success/failure counts in a sliding window (last 10 requests).
2. If failure rate exceeds threshold (e.g., 50%), trip the circuit (open state).
3. In open state, fail all requests immediately with a fallback.
4. After timeout (e.g., 30s), transition to half-open (test a few requests).
5. If half-open tests succeed, close the circuit. If they fail, reopen.

```java
// Maven dependency
// implementation 'io.github.resilience4j:resilience4j-spring-boot3'

@Service
public class PaymentService {
    @Autowired
    private RestTemplate restTemplate;

    // Step 1-2 — define circuit breaker
    @CircuitBreaker(
        name = "fraudDetection",
        fallbackMethod = "fraudCheckFallback"
    )
    public FraudCheckResult checkFraud(String orderId) {
        // Circuit breaker watches success/failure
        try {
            // Call fraud detection service
            return restTemplate.getForObject(
                "http://fraud-service:8080/check?orderId=" + orderId,
                FraudCheckResult.class
            );
        } catch (Exception e) {
            // Step 2 — if failures exceed 50% in last 10 requests
            // circuit breaker trips (opens)
            throw new FraudDetectionException("Fraud service unavailable", e);
        }
    }

    // Step 3 — fallback method (called when circuit is open)
    public FraudCheckResult fraudCheckFallback(String orderId, Exception e) {
        // Return safe default (assume fraud = false to allow payment)
        // or cached result from last successful check
        return new FraudCheckResult(orderId, false, "Fraud service down; allowing payment");
    }
}

// Configuration (application.yml)
// resilience4j:
//   circuitbreaker:
//     instances:
//       fraudDetection:
//         registerHealthIndicator: true
//         slidingWindowSize: 10                    # last 10 requests
//         failureRateThreshold: 50                 # 50% failure → trip
//         slowCallRateThreshold: 80                # 80% slow → trip
//         waitDurationInOpenState: 30000           # 30s before half-open
//         slowCallDurationThreshold: 2000          # requests > 2s = slow
//         permittedNumberOfCallsInHalfOpenState: 3 # test with 3 requests
```

---

**Pattern 2: Bulkhead Isolation (Thread Pool)**

**Steps:**
1. Allocate separate thread pools per downstream service.
2. If service A's pool is maxed out, requests are rejected or queued (not starving service B).
3. Monitor pool utilization per service.

```java
@Service
public class MultiServiceClient {
    // Step 1 — separate executors per service
    private final Executor fraudServiceExecutor = Executors.newFixedThreadPool(20);  // 20 threads for fraud service
    private final Executor paymentGatewayExecutor = Executors.newFixedThreadPool(30); // 30 for payment
    private final Executor inventoryExecutor = Executors.newFixedThreadPool(10);      // 10 for inventory

    public void processOrder(Order order) {
        // Step 2 — each service uses its own pool
        fraudServiceExecutor.execute(() -> {
            try {
                checkFraud(order);
            } catch (RejectedExecutionException e) {
                // Pool is full; bulkhead is working; fail this request
                // but other services' pools are unaffected
                logWarning("Fraud service saturated; rejecting");
            }
        });

        paymentGatewayExecutor.execute(() -> {
            chargePayment(order);
        });

        inventoryExecutor.execute(() -> {
            updateInventory(order);
        });
    }

    // Using Spring's @Async with custom executor
    @Async("fraudServiceExecutor")
    public void checkFraud(Order order) {
        // Runs on dedicated thread pool
    }
}

// Configuration with thread pools
@Configuration
public class ThreadPoolConfig {
    @Bean(name = "fraudServiceExecutor")
    public Executor fraudServiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);  // queue up to 100 requests
        executor.setThreadNamePrefix("fraud-");
        // CallerRunsPolicy does NOT reject — it runs the task on the submitting
        // thread when the pool+queue are full, which naturally throttles the
        // caller (backpressure). Use AbortPolicy (throws RejectedExecutionException)
        // if you want fail-fast rejection instead.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

**Pattern 3: Retry with Exponential Backoff**

**Steps:**
1. On failure, wait for a base delay (e.g., 100ms).
2. Retry. If still fails, wait base * 2 (200ms), then retry.
3. Continue with exponential growth until max retries (e.g., 5) or max delay (e.g., 10s).
4. Add jitter (randomization) to prevent thundering herd.

```java
@Service
public class PaymentProcessing {
    // Use @Retryable from Spring Retry
    @Retryable(
        value = { TransientPaymentException.class },
        maxAttempts = 5,
        backoff = @Backoff(
            delay = 100,              // initial 100ms
            multiplier = 2.0,         // double each time
            maxDelay = 10000          // cap at 10s
        )
    )
    public PaymentResult chargeCard(PaymentRequest request) {
        // Step 1-4 — Spring Retry auto-retries with exponential backoff
        return callPaymentGateway(request);
    }

    // Manual retry with exponential backoff
    public PaymentResult chargeCardManual(PaymentRequest request) {
        int maxRetries = 5;
        int delay = 100; // 100ms base
        Random random = new Random();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return callPaymentGateway(request);
            } catch (TransientPaymentException e) {
                if (attempt == maxRetries) {
                    throw e; // give up
                }

                // Step 4 — add jitter: [delay * 0.5, delay * 1.5]
                int jitter = (int) (delay * 0.5 + random.nextDouble() * delay);
                Thread.sleep(jitter);

                // Step 2 — exponential growth
                delay = Math.min(delay * 2, 10000); // cap at 10s
            }
        }
        return null;
    }

    private PaymentResult callPaymentGateway(PaymentRequest request) throws TransientPaymentException {
        // Simulated call; throws on timeout/network error
        return new PaymentResult(true, "Charged");
    }
}
```

---

**What are Sliding Window, Fallback Method, and Jitter, and why do they fit here?**

- **Sliding Window:** Tracks the last N requests (e.g., 10 requests) to calculate failure rate. Avoids long-term historical bias. In an interview: *"Sliding window ensures the circuit breaker reacts to recent failures, not failures from hours ago."*

- **Fallback Method:** A backup response when circuit is open (e.g., return cached result, default value, or error). In an interview: *"Fallback allows graceful degradation — serve stale data rather than error if the service is down."*

- **Jitter:** Random delay to prevent thundering herd (all retries at the same time overwhelming the service). In an interview: *"Jitter prevents synchronized retries from overloading a recovering service."*

---

## 🏢 Real World — Where Companies Use This

- **PayPal (payment retries):** Payment service calls fraud detection and payment gateway. Both can be slow/flaky. Circuit breaker on each + exponential backoff prevents cascading timeouts during peak load.
- **Netflix (resilience everywhere):** Hystrix library (predates Resilience4j) popularized circuit breaker in microservices. All downstream calls are wrapped: circuit breaker + bulkhead + timeout + fallback.
- **Swiggy (order processing):** Call to inventory service, delivery service, payment service. If inventory is slow, circuit breaker fails fast → order marked pending → retry later. Doesn't block delivery/payment processing.
- **Amazon (distributed systems):** AWS SDK includes retry logic with exponential backoff and jitter. Prevents clients from overwhelming a recovering service.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Calling external/downstream services (HTTP, RPC, DB) | Internal in-process calls (no benefit) |
| Failures are transient (network timeout, temporary overload) | Permanent failures (authentication invalid, resource not found) |
| You have a sensible fallback (cache, default value, error response) | No fallback exists (must wait for full recovery) |
| Services can fail independently (one crash shouldn't block all) | Monolithic architecture (one failure = all fail anyway) |

**The common mistake:** Circuit breaker on *every* call (including to in-process cache or local database). Overhead with no benefit. Use circuit breaker for remote calls only. Also: not having a fallback (circuit breaker without fallback just means "fail faster," not "survive gracefully").

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Fail fast (no hanging requests). Cascading failures prevented. Bulkhead isolation limits blast radius. Gradual recovery via half-open state. |
| **You lose** | Complexity (3 states, thresholds, timeouts, fallback logic). Incorrectly tuned circuit breakers cause false positives (open when service is fine). Fallback logic must be correct (serving stale data can be worse than error). |
| **Failure mode** | Circuit breaker stays open too long → users get errors unnecessarily. Or closes too fast → service still overloaded, trips again (flapping). Exponential backoff with large jitter can cause long recovery times. |

---

## 🔬 Interview Q&As

### Q: "Design a payment service that tolerates 50% of cache failures gracefully."

> Use circuit breaker on cache calls with high failure threshold (e.g., 70% failures → open, not 50%). When cache circuit is open, fall back to database (slower but correct). Retry database calls with exponential backoff. For payment processing specifically, never fallback to bad data — if cache fails, go to source of truth. Also use bulkhead: cache thread pool separate from payment gateway pool, so cache failure doesn't starve payment processing. ⭐ **Tier 2 — system design**

### Q: "Your circuit breaker is flapping (oscillating between open/closed). Why?"

> Circuit breaker thresholds are too tight. For example: failureRateThreshold = 50%, but service is recovering (51% failures, 49% success — close; then 50.1% fail — open). Solution: increase threshold to 70% or increase sliding window (use 20 requests, not 10). Also: increase waitDurationInOpenState (how long before testing half-open). If service needs 5 minutes to recover, 30s is too aggressive. Monitor circuit state changes; adjust thresholds based on observed service behavior. ⭐ **Tier 2 — operational**

### Q: "Exponential backoff with jitter — what's the difference from fixed delay?"

> Fixed delay (always 100ms) + multiple retries = thundering herd (all retries hit the server at the same time, overloading it further). Exponential backoff (100ms, 200ms, 400ms, 800ms) spreads load over time. Jitter (randomization within the delay window) further desynchronizes retries so clients don't retry in lockstep. Example: 100 clients fail simultaneously. With jitter, they retry at 50–150ms, 100–300ms, etc. — distributed across 300ms instead of all at 100ms. ⭐ **Tier 2 — failure handling**

### Q: "How do you choose the failureRateThreshold for a circuit breaker?"

> Depends on the service's criticality and SLA. For a cache (nice-to-have), high threshold (70–80%) is fine — serve stale data, keep circuit closed longer. For payment gateway (critical), low threshold (50%) — fail fast, don't risk corrupted transactions. Also consider transient vs permanent failures: if 50% of failures are transient (network timeout), circuit breaker helps. If 50% are permanent (invalid request), circuit breaker wastes time failing fast when you should retry only transient errors. Use Bulkhead + Retry + Circuit Breaker as layers: retry for transient, bulkhead for isolation, circuit for cascading failure prevention. ⭐ **Tier 2 — design trade-off**

### Q: "What's the difference between bulkhead and rate limiting?"

> **Bulkhead:** Limits *concurrent* requests per service (thread pool size). Prevents one service from consuming all threads. **Rate limiting:** Limits *throughput* (requests per second) across all services. Prevents too many requests hitting the downstream service. Use both: rate limiting at the edge (API gateway), bulkhead internally (service-to-service calls). Rate limiting is about fairness across users; bulkhead is about isolation across services. ⭐ **Tier 2 — conceptual**

### Q: "Your fallback returns stale cache data. How do you ensure correctness?"

> For reads: stale data is often acceptable (cache miss = serve old version). For writes: never use stale data as fallback (charge card twice, create duplicate order). Mark stale responses so clients know they're not fresh. Caveat: "Data may be outdated; refresh after service recovery." Alternatively, use pessimistic fallback: return error rather than stale data for critical operations. Application logic decides: "Payment failure = don't process order." Better safe than sorry. ⭐ **Tier 2 — correctness**

---

## 🧾 TL;DR

> "Circuit breaker prevents cascading failures by failing fast when downstream service is down or overloaded. Bulkhead isolates thread pools per service so one failure doesn't starve others. Retry with exponential backoff + jitter recovers transient errors without overwhelming the recovering service. Combine all three for production resilience."

---

## 🔗 Related Concepts

- **`18-service-discovery-dns.md`** — service discovery provides instances; circuit breaker wraps calls to those instances
- **`10-backpressure.md`** — circuit breaker is a form of backpressure (reject requests when overloaded)
- **`02-rate-limiting.md`** — rate limiting + circuit breaker together prevent cascading failures
- **`19-message-queues-kafka-rabbitmq.md`** — async messaging eliminates need for circuit breaker (no synchronous calls to break)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **ByteByteGo — "Circuit Breaker Pattern"** (YouTube) | Visual walkthrough of states and transitions, real-world examples | ~9 min |
| **Resilience4j Documentation** (GitHub) | Official CircuitBreaker API, Bulkhead, Retry with examples | ~20 min reference |
| **Arpit Bhayani — "Resilience Patterns in Distributed Systems"** (YouTube) | Deep dive on circuit breaker + bulkhead + timeout interaction | ~18 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 20. Added circuit breaker states (closed/open/half-open), bulkhead isolation, retry with exponential backoff + jitter. |
| Jul 19, 2026 | **Factual fix.** Corrected the `CallerRunsPolicy` comment ("reject if full") — `CallerRunsPolicy` does NOT reject; it runs the task on the submitting thread (backpressure). Noted `AbortPolicy` as the fail-fast alternative. Also fixed the nested class reference to `ThreadPoolExecutor.CallerRunsPolicy`. |
