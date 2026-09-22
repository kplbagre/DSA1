# ☕ Resilience4j Patterns — Deep Dive

> After this note you can configure CircuitBreaker (3 states + transitions), Retry with exponential backoff, RateLimiter, Bulkhead, and TimeLimiter in Spring Boot — and explain why Hystrix is dead and Resilience4j replaced it.

---

## 🎯 The Problem This Solves

Your service calls a downstream payment API. The API goes down. Without resilience: every request blocks for 30 seconds (connection timeout), your thread pool fills with waiting threads, your service becomes unresponsive, upstream callers timeout on YOU, and the failure cascades. One slow downstream service takes down the entire call chain.

Resilience4j provides 5 patterns to prevent cascade failure: **CircuitBreaker** (stop calling a dead service), **Retry** (retry transient failures), **RateLimiter** (limit request rate), **Bulkhead** (isolate resource pools), **TimeLimiter** (bound execution time).

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **CircuitBreaker** | Monitors failure rate of downstream calls. When failures exceed a threshold, the circuit OPENS — subsequent calls fail immediately (fail-fast) without attempting the downstream. After a wait period, a probe call is allowed (HALF-OPEN). If it succeeds → circuit CLOSES. If it fails → circuit stays OPEN. |
| **Retry** | Automatically retries a failed call N times with configurable delay. For TRANSIENT failures (network blip, 503). NOT for permanent failures (400, 404). |
| **RateLimiter** | Limits the rate of calls to a downstream service — e.g., max 100 calls/second. Prevents overwhelming a recovering service. |
| **Bulkhead** | Limits the number of CONCURRENT calls to a downstream service. Prevents one slow service from consuming all threads. Two types: semaphore-based (limit concurrency) and thread-pool-based (dedicated threads). |
| **TimeLimiter** | Bounds execution time. If the call doesn't complete within N seconds → timeout exception. Prevents threads from blocking indefinitely on a hung downstream. |

---

## 🧠 Mental Model

Think of these as **electrical safety devices in a building:**
- **CircuitBreaker** = the circuit breaker in your fuse box. Too many short circuits (failures) → breaker trips (opens) → power cut to that circuit (fail-fast) → prevents fire (cascade failure). After a cooldown, you flip it back (half-open probe).
- **Retry** = auto-reconnect on a phone call. Call drops (transient failure) → redial automatically. Don't redial if the number is disconnected (permanent failure).
- **RateLimiter** = water flow regulator. Don't blast a recovering pipe with full pressure. Slowly increase flow.
- **Bulkhead** = watertight compartments on a ship. One compartment floods (slow service) → others stay dry (other services unaffected).
- **TimeLimiter** = a timer on a phone call. If no one answers in 5 seconds → hang up. Don't wait forever.

> If you can say "CircuitBreaker: CLOSED → OPEN (fail-fast) → HALF-OPEN (probe) → CLOSED; Retry: transient failures only, exponential backoff; Bulkhead: limit concurrent calls; RateLimiter: limit calls/second; always combine: Retry inside CircuitBreaker, TimeLimiter wrapping everything" without notes, you have resilience patterns.

---

## 🎨 Visual — CircuitBreaker State Machine

```
  ┌──────────┐  failure rate > threshold   ┌──────────┐
  │  CLOSED  │ ──────────────────────────► │   OPEN   │
  │ (normal) │                             │(fail-fast)│
  └──────────┘                             └──────────┘
       ▲                                        │
       │                                   wait duration
       │                                   (e.g., 60s)
       │                                        │
       │    probe succeeds                      ▼
       └──────────────────────────────── ┌───────────┐
                                         │ HALF-OPEN │
       ┌──────────────────────────────── │  (probe)  │
       │    probe fails                  └───────────┘
       │
       ▼
  ┌──────────┐
  │   OPEN   │  (back to OPEN — wait again)
  └──────────┘

  CLOSED: all calls go through. Failure rate tracked in sliding window.
  OPEN:   all calls rejected immediately (fallback). No downstream load.
  HALF-OPEN: limited calls allowed (permittedNumberOfCallsInHalfOpenState).
             If they succeed → CLOSED. If they fail → back to OPEN.

KEY INVARIANT:
   CircuitBreaker PROTECTS the downstream AND your thread pool.
   In OPEN state: no network call, no thread blocked, instant response.
   The downstream has time to recover without load.
```

---

## 🪜 Build-up

---

### Level 2 — The real mechanism

#### 2.1 — CircuitBreaker

```java
// Tier 2 — Production: CircuitBreaker with Spring Boot
// application.yml:
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10            # last 10 calls
        failure-rate-threshold: 50         # 50% failure rate → OPEN
        wait-duration-in-open-state: 60s   # stay OPEN for 60s before HALF-OPEN
        permitted-number-of-calls-in-half-open-state: 3  # 3 probe calls in HALF-OPEN
        minimum-number-of-calls: 5         # need at least 5 calls before evaluating rate
        record-exceptions:                 # which exceptions count as "failure"
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignore-exceptions:                 # which exceptions DON'T count as failure
          - com.walmart.BusinessValidationException  # 400-level = not a downstream failure

// Java:
@Service
public class PaymentService {

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public PaymentResult charge(Order order) {
        return paymentClient.charge(order.getTotal());   // downstream call
    }

    // Fallback: called when circuit is OPEN or call fails after exhausting retries
    private PaymentResult paymentFallback(Order order, Exception ex) {
        log.warn("Payment circuit open or failed: {}", ex.getMessage());
        return PaymentResult.deferred(order.getId());   // queue for later processing
    }
}
```

#### 2.2 — Retry

```java
// application.yml:
resilience4j:
  retry:
    instances:
      paymentService:
        max-attempts: 3
        wait-duration: 1s                # 1s between retries
        exponential-backoff-multiplier: 2  # 1s → 2s → 4s
        retry-exceptions:
          - java.io.IOException          # retry network errors
          - org.springframework.web.client.HttpServerErrorException  # retry 5xx
        ignore-exceptions:
          - org.springframework.web.client.HttpClientErrorException  # DON'T retry 4xx

// Java:
@Retry(name = "paymentService", fallbackMethod = "paymentFallback")
@CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
public PaymentResult charge(Order order) {
    return paymentClient.charge(order.getTotal());
}
// Order of decoration: Retry is INSIDE CircuitBreaker.
// Call fails → Retry retries 3 times → all fail → CircuitBreaker records failure.
// If failure rate crosses threshold → circuit OPENS → subsequent calls skip Retry entirely.
```

**Retry vs CircuitBreaker — different purposes:**

```
  Retry:           for TRANSIENT failures (network blip — likely succeeds next attempt)
  CircuitBreaker:  for SUSTAINED outages (downstream is DOWN — retrying adds load)

  COMBINE THEM: Retry inside CircuitBreaker.
  - Transient failure: Retry handles it (2nd attempt succeeds).
  - Sustained failure: Retry exhausted → CircuitBreaker tracks the failure.
  - After threshold: Circuit OPENS → calls fail fast (no Retry, no network call).
  - Downstream recovers → HALF-OPEN → probe succeeds → CLOSED → normal flow.
```

#### 2.3 — Bulkhead

```java
// application.yml — Semaphore Bulkhead (simpler, recommended for most cases):
resilience4j:
  bulkhead:
    instances:
      paymentService:
        max-concurrent-calls: 20          # max 20 concurrent calls to payment API
        max-wait-duration: 500ms          # wait up to 500ms for a permit

// Java:
@Bulkhead(name = "paymentService", fallbackMethod = "paymentFallback")
public PaymentResult charge(Order order) {
    return paymentClient.charge(order.getTotal());
}
// If 20 calls are in-flight → 21st call waits up to 500ms → if still no permit → fallback
// This prevents a slow payment API from consuming ALL your service's threads.
// Other APIs (inventory, shipping) continue normally — they have their own bulkheads.
```

#### 2.4 — RateLimiter

```java
// application.yml:
resilience4j:
  ratelimiter:
    instances:
      paymentService:
        limit-for-period: 100             # 100 calls per period
        limit-refresh-period: 1s          # period = 1 second → 100 calls/second
        timeout-duration: 500ms           # wait up to 500ms for a permit

// Java:
@RateLimiter(name = "paymentService", fallbackMethod = "paymentFallback")
public PaymentResult charge(Order order) {
    return paymentClient.charge(order.getTotal());
}
// Prevents overwhelming a recovering downstream or respecting API rate limits.
```

#### 2.5 — Combining patterns (decoration order)

```
  RECOMMENDED ORDER (outermost to innermost):

  TimeLimiter → CircuitBreaker → RateLimiter → Bulkhead → Retry → Actual call

  WHY THIS ORDER:
  1. TimeLimiter: bounds total execution time (including retries)
  2. CircuitBreaker: fail-fast if downstream is down (before wasting time on retries)
  3. RateLimiter: don't exceed downstream's rate limit
  4. Bulkhead: don't use too many concurrent threads for this call
  5. Retry: retry transient failures WITHIN the above constraints
  6. Actual call: the downstream HTTP/gRPC call

  In Spring Boot, annotation order is controlled by configuration:
  resilience4j:
    circuitbreaker:
      circuitBreakerAspectOrder: 1    # lower = outer
    retry:
      retryAspectOrder: 2             # higher = inner
```

---

### Level 3 — The subtleties

#### 3.1 — Hystrix is dead — why Resilience4j

```
  Hystrix: Netflix's circuit breaker library. Entered MAINTENANCE MODE in 2018.
  No new features. No bug fixes. Thread-pool-based bulkhead (heavyweight).

  Resilience4j: lightweight, functional, Java 8+. Semaphore-based bulkhead (lightweight).
  Uses decorators (Function/Supplier wrapping), not thread pools.
  Works with CompletableFuture, Reactor, RxJava.
  Spring Boot starter: spring-cloud-starter-circuitbreaker-resilience4j.

  If an interviewer asks about Hystrix → mention it's deprecated since 2018 →
  Resilience4j is the modern replacement → same patterns, lighter implementation.
```

#### 3.2 — Sliding window types

```
  COUNT-BASED: failure rate computed over the last N calls (e.g., last 10 calls).
  → Good when call frequency is consistent.

  TIME-BASED: failure rate computed over the last N seconds (e.g., last 60 seconds).
  → Good when call frequency varies (low traffic hours vs peak).

  RULE: use COUNT-BASED for high-traffic services (consistent call rate).
        use TIME-BASED for variable-traffic services (the rate adapts to traffic).
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "CircuitBreaker retries failed calls" | CircuitBreaker does NOT retry. It TRACKS failure rate and OPENS the circuit when it's too high. Retry is a separate pattern. Combine them: Retry inside CircuitBreaker. |
| "Retry is always safe" | Retry on a non-idempotent operation (POST creating an order) can create duplicates. Retry only idempotent operations, or ensure the downstream is idempotent (idempotency key). |
| "Bulkhead = thread pool" | Resilience4j supports TWO bulkhead types: semaphore (limits concurrency with a counter — lightweight, no thread overhead) and thread-pool (dedicated thread pool per downstream — heavier, provides true isolation). Semaphore is the default and recommended for most cases. |
| "I need all 5 patterns on every call" | Use what the problem demands. Internal service with reliable network → maybe just TimeLimiter. External third-party API → CircuitBreaker + Retry + TimeLimiter. Don't over-configure. |

---

## 🐞 Production Footguns

---

> **Footgun: Retry on non-idempotent POST**
> **Cost:** Duplicate orders / double charges
>
> A service retried failed POST requests to a payment API. The first attempt succeeded but the response was lost (network timeout). The retry sent the same request again → customer charged twice. Fix: send an idempotency key with each request. The API deduplicates using the key.

```java
// ❌ The trap: retrying POST without idempotency key
@Retry(name = "paymentService")
public PaymentResult charge(Order order) {
    return paymentClient.post("/charge", new ChargeRequest(order.getTotal()));
    // First attempt: charge succeeds, response times out
    // Retry: charge sent again → duplicate charge
}

// ✅ The fix: idempotency key
@Retry(name = "paymentService")
public PaymentResult charge(Order order) {
    String idempotencyKey = UUID.randomUUID().toString();
    return paymentClient.post("/charge",
        new ChargeRequest(order.getTotal(), idempotencyKey));
    // API: if idempotencyKey already processed → return original result, don't charge again
}
```

---

> **Footgun: CircuitBreaker ignoring business exceptions**
> **Cost:** Circuit opens on valid business responses
>
> A CircuitBreaker was configured with `record-exceptions: [Exception.class]`. A `BusinessValidationException` (e.g., "insufficient funds" — a 400-level response) was thrown by the downstream. The CircuitBreaker recorded it as a failure. After 5 such legitimate business responses, the circuit opened — blocking ALL payment calls, including valid ones.

```yaml
# ❌ The trap: recording all exceptions
resilience4j.circuitbreaker.instances.paymentService:
  record-exceptions:
    - java.lang.Exception   # catches EVERYTHING including business exceptions

# ✅ The fix: record only infrastructure failures, ignore business exceptions
resilience4j.circuitbreaker.instances.paymentService:
  record-exceptions:
    - java.io.IOException
    - java.util.concurrent.TimeoutException
  ignore-exceptions:
    - com.walmart.BusinessValidationException   # 400-level = valid response, not a failure
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `../Spring/DeepDive/03-spring-mvc-boot.md` | Resilience4j integrates with Spring Boot via starters. `@CircuitBreaker`, `@Retry` annotations work through Spring AOP (same proxy mechanism as `@Transactional`). |
| `../Concurrency/thread-pool-executor.md` | Bulkhead (thread-pool type) creates a dedicated ThreadPoolExecutor per downstream — isolating resource consumption. Understanding pool sizing from Note #19 applies to bulkhead configuration. |
| `completable-future.md` (in `StreamsFunctional/`) | Resilience4j decorates `Supplier` and `CompletableFuture`. `CircuitBreaker.decorateCompletionStage()` wraps async calls. Understanding CF chaining explains how resilience decorators compose with async code. |
| `../Spring/DeepDive/05-spring-security-jwt.md` | Both SecurityFilterChain and Resilience4j use the decorator/chain pattern — wrapping calls with cross-cutting concerns. Same architectural idea: request → security → resilience → business logic. |

---

## 🎙️ Interview Deep Questions

**Q1. Explain the CircuitBreaker pattern. What are the 3 states?**

> CircuitBreaker monitors the failure rate of calls to a downstream service. CLOSED (normal): all calls go through. The failure rate is tracked in a sliding window (last N calls or last N seconds). When the failure rate exceeds the threshold (e.g., 50%), the circuit transitions to OPEN. OPEN (fail-fast): all calls are immediately rejected — no network call, no thread blocked, instant fallback response. The downstream has time to recover without load from your service. After a wait duration (e.g., 60 seconds), the circuit transitions to HALF-OPEN. HALF-OPEN (probe): a limited number of calls (e.g., 3) are allowed through. If they succeed, the circuit transitions back to CLOSED. If they fail, it transitions back to OPEN for another wait duration.

**Q2. What is the difference between Retry and CircuitBreaker? How do you combine them?**

> Retry handles TRANSIENT failures — a single network blip where the next attempt will likely succeed. It retries N times with configurable delay (typically exponential backoff). CircuitBreaker handles SUSTAINED outages — the downstream is DOWN and retrying just adds load. It fails fast after detecting a high failure rate. Combine them: Retry INSIDE CircuitBreaker. A call fails → Retry retries 3 times → all fail → CircuitBreaker records one failure. If failure rate crosses the threshold → circuit OPENS → subsequent calls skip Retry entirely and fail immediately. This way: transient failures are retried transparently, but sustained outages trigger fail-fast without wasting retry attempts.

**Q3. What is a Bulkhead and why is it needed?**

> Bulkhead limits the number of concurrent calls to a specific downstream service. Without it: a slow downstream (responding in 10 seconds instead of 100ms) ties up threads. If your service has 200 threads and ALL of them are waiting for the slow downstream, the service is effectively dead — it can't handle ANY other requests. With a bulkhead (e.g., max 20 concurrent calls to payment API): only 20 threads can be waiting for payment at a time. The other 180 threads serve other requests normally. The name comes from ship construction: watertight compartments that prevent one flooded section from sinking the entire ship.

**Q4. Why is Hystrix deprecated? What replaced it?**

> Netflix's Hystrix entered maintenance mode in 2018 — no new features, no active development. It used thread-pool-based bulkheads (creating a separate thread pool per downstream — heavyweight memory/thread overhead) and was designed before Java 8 (no functional interfaces, no CompletableFuture support). Resilience4j replaced it: lightweight (semaphore-based bulkhead by default — no extra threads), functional (decorates Supplier/Function/CompletableFuture), modular (use only the patterns you need — CircuitBreaker without Retry if you want), and integrates with Spring Boot, Reactor, and RxJava. Same patterns, modern implementation.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Resilience4j provides 5 patterns to prevent cascade failure: CircuitBreaker (fail-fast when downstream is down), Retry (retry transient failures), RateLimiter (limit call rate), Bulkhead (limit concurrent calls), TimeLimiter (timeout). Replaces deprecated Hystrix.
>
> **Part 2 — How/Why (30s):** CircuitBreaker tracks failure rate in a sliding window. Above threshold → OPEN (fail-fast, no network call). After wait → HALF-OPEN (probe). Probe succeeds → CLOSED. Retry handles transient failures with exponential backoff. Combine: Retry INSIDE CircuitBreaker — transient failures are retried, sustained outages trigger fail-fast. Bulkhead limits concurrency per downstream — prevents one slow service from consuming all threads. All configured in application.yml + `@CircuitBreaker`/`@Retry` annotations on methods.
>
> **Part 3 — Gotcha (20s):** Two traps: retrying non-idempotent POST requests → duplicate orders. Fix: send an idempotency key. And recording business exceptions (400-level) as failures → circuit opens on valid responses. Fix: use `ignore-exceptions` for business validation exceptions — only record infrastructure failures (IOException, TimeoutException).

---

## 🧾 TL;DR

- **CircuitBreaker:** CLOSED → OPEN (fail-fast at threshold) → HALF-OPEN (probe) → CLOSED.
- **Retry:** transient failures only, exponential backoff, idempotent operations only.
- **Bulkhead:** limit concurrent calls per downstream. Semaphore (lightweight) or thread-pool (isolated).
- **RateLimiter:** limit calls/second to respect downstream capacity.
- **TimeLimiter:** bound execution time. Prevent indefinite blocking.
- **Combine:** TimeLimiter → CircuitBreaker → RateLimiter → Bulkhead → Retry → Call.
- **Hystrix is deprecated (2018).** Resilience4j is the replacement.
- **`ignore-exceptions`:** business exceptions (400-level) should NOT count as failures.
- **Idempotency key:** mandatory when retrying POST/PUT operations.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #41 (Phase 6, Tier 2) of the JavaBackend KB completion roadmap. Staff-level depth: CircuitBreaker 3 states with sliding window (count vs time based), Retry with exponential backoff, Bulkhead (semaphore vs thread-pool), RateLimiter, TimeLimiter, decoration order, Spring Boot YAML configuration, Hystrix deprecation context, record vs ignore exceptions. Two production footguns: retry on non-idempotent POST, circuit opening on business exceptions. |
