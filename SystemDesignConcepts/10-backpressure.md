# Backpressure

---

## 🎯 Why This Matters

Every distributed system has a point where the producer sends faster than the consumer can process. Without backpressure, that gap fills with buffered work — queues grow, memory exhausts, latency explodes, services crash in a cascade. Backpressure is the set of mechanisms that let a downstream service say "I'm overwhelmed — slow down or drop." It appears in senior design interviews whenever you sketch any async processing, streaming, or microservice fan-out. A senior engineer is expected to name the specific mechanism (bounded queue, circuit breaker, load shedding, reactive pull), not just "add a queue."

---

## 🧠 The Mental Model

Imagine a **garden hose** connected to a fire hydrant. The hydrant can push 500 litres/minute. The hose can carry 50 litres/minute. If you open the hydrant fully, two things happen: (1) water pressure builds at the hose inlet until the hose bursts, or (2) a pressure-relief valve on the hose triggers and signals the hydrant: "I can only take 50 L/min — throttle yourself."

Without the relief valve: the hose ruptures (service OOM crash, queue overflow, cascade failure).

With the relief valve: the hydrant slows to 50 L/min. Less total throughput, but the hose stays intact and water keeps flowing.

In distributed systems, the hydrant is your upstream producer (Kafka, an HTTP client, a scheduler), the hose is your downstream consumer (a microservice, a DB, a processing thread), and the pressure-relief valve is **backpressure** — the signal that travels upstream to slow or stop sending.

**But there is a subtlety:** Sometimes you can't slow the hydrant (a Kafka topic receives events regardless of consumer speed). In that case, the pressure-relief valve must instead tell the hose to **shed load** — prioritize the most important water and drain the rest. This is **load shedding**: deliberately dropping low-priority work rather than falling behind indefinitely.

**The key insight is:** Backpressure is not a single technique — it is a principle applied at every boundary in a system. The implementations differ by boundary type: bounded queues at thread-pool edges, HTTP 429 at API boundaries, circuit breakers at service call boundaries, reactive pull at stream-processing boundaries.

---

## 🎨 Visual — Overflow cascade vs backpressure applied

```
WITHOUT BACKPRESSURE — cascade failure
═══════════════════════════════════════

Producer (10,000 req/sec)
        │
        ▼
   ┌──────────────────────┐
   │  Unbounded Queue     │  ← keeps growing: 1K → 10K → 100K messages
   │  [████████████████▶] │
   └──────────────────────┘
        │
        ▼
   Service B (can handle 1,000 req/sec)
        │ processes at max speed, queue never drains
        │
        ▼
   ┌─────────────────────────────┐
   │ Heap: 4GB limit             │
   │ Queue serialized in memory  │
   │ [████████████████████████▶] │ → OutOfMemoryError ❌
   └─────────────────────────────┘
   Service B crashes → producer retries → makes it worse


WITH BACKPRESSURE — bounded queue + HTTP 429
═════════════════════════════════════════════

Producer (10,000 req/sec)
        │
        │◄──────────────────────────────── HTTP 429 / slow signal
        │   "I can only take 1,000/sec"
        ▼
   ┌──────────────────────┐
   │  Bounded Queue       │  max capacity = 1,000 messages
   │  [████████░░░░░░░░]  │  stays within bounds
   └──────────────────────┘
        │
        ▼
   Service B (1,000 req/sec)      ← stable, no OOM
        │
        ▼
   Response: processed correctly ✅


CIRCUIT BREAKER STATES
═══════════════════════

 CLOSED (normal)          OPEN (failing)           HALF-OPEN (probe)
 ┌────────────┐           ┌────────────┐            ┌────────────┐
 │ requests   │  failure  │ all calls  │   timeout  │ 1 trial    │
 │ pass       │──rate────►│ fail fast  │──expires──►│ request    │
 │ through    │  > 50%    │ (no wait)  │            │ sent       │
 └────────────┘           └────────────┘            └────────────┘
        ▲                                                 │
        │                 failure         success        │
        └─────────────────────────────────────────────────┘

KEY INVARIANT:
   Every boundary between producer and consumer needs a backpressure mechanism.
   Bounded queues protect memory. 429 protects API throughput. Circuit breakers
   protect against cascading failure when downstream is slow or dead.
```

---

## ⚙️ How It Actually Works

### Mechanism 1 — Bounded Queue (Thread Pool)

**Steps in plain English:**

1. **Define a fixed-capacity queue** in your thread pool executor — new tasks submitted when the queue is full are rejected immediately instead of being buffered indefinitely.
2. **Define a rejection policy** — what happens when capacity is reached: throw an exception (RejectedExecutionException), discard silently, discard oldest, or run in caller thread.
3. **Surface the rejection** as a meaningful response (HTTP 503 Service Unavailable or 429 Too Many Requests).

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

@Configuration
public class TaskExecutorConfig {

    @Bean
    public ThreadPoolExecutor boundedExecutor() {
        int coreThreads = 10;
        int maxThreads = 20;
        // Step 1 — bounded queue: max 500 tasks waiting; prevents unbounded memory growth
        int queueCapacity = 500;

        return new ThreadPoolExecutor(
            coreThreads,
            maxThreads,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            // Step 2 — AbortPolicy: throw RejectedExecutionException when queue is full
            new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
```

```java
@RestController
public class OrderController {

    private final ThreadPoolExecutor executor;
    private final OrderService orderService;

    public OrderController(ThreadPoolExecutor executor, OrderService orderService) {
        this.executor = executor;
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(@RequestBody OrderRequest request) {
        try {
            // Submit to bounded executor
            executor.submit(() -> orderService.process(request));
            return ResponseEntity.accepted().body("Order queued");
        } catch (RejectedExecutionException e) {
            // Step 3 — queue full: signal backpressure to client
            return ResponseEntity.status(429).body("Server busy — retry after 1 second");
        }
    }
}
```

---

### Mechanism 2 — Circuit Breaker (Resilience4j)

**Steps in plain English:**

1. **Wrap outbound calls** with a circuit breaker — counts failures over a sliding window.
2. **CLOSED state** — calls pass through normally. If failure rate exceeds threshold (e.g., 50%), transition to OPEN.
3. **OPEN state** — all calls fail fast (throw `CallNotPermittedException`) without even attempting the downstream service. Wait for a timeout (e.g., 30 seconds).
4. **HALF-OPEN state** — allow a probe request through. If it succeeds, go back to CLOSED. If it fails, go back to OPEN.

```java
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;

@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreaker paymentCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            // Step 2 — open when failure rate > 50% in last 10 calls
            .failureRateThreshold(50)
            .slidingWindowSize(10)
            // Step 3 — stay OPEN for 30 seconds before allowing probe
            .waitDurationInOpenState(Duration.ofSeconds(30))
            // Step 4 — allow 3 probe calls in HALF-OPEN before deciding
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();
        return CircuitBreakerRegistry.of(config).circuitBreaker("paymentService");
    }
}
```

```java
@Service
public class PaymentService {

    private final CircuitBreaker circuitBreaker;
    private final ExternalPaymentGateway gateway;

    public PaymentService(CircuitBreaker circuitBreaker, ExternalPaymentGateway gateway) {
        this.circuitBreaker = circuitBreaker;
        this.gateway = gateway;
    }

    public PaymentResult processPayment(PaymentRequest request) {
        // Step 1 — wrap the outbound call with the circuit breaker
        return circuitBreaker.executeSupplier(() -> gateway.charge(request));
        // If circuit is OPEN: executeSupplier throws CallNotPermittedException immediately
        // Caller catches this and returns a 503 or fallback response
    }
}
```

---

### What is Resilience4j, and why does it fit here?

**Resilience4j** is a lightweight Java library (inspired by Netflix Hystrix) that provides circuit breaker, rate limiter, retry, and bulkhead patterns as composable function decorators. It is the standard circuit breaker library for Spring Boot 3+ applications — Hystrix is now deprecated.

**Why it fits:** A circuit breaker implemented manually requires tracking failure counts, timers, and state transitions — all thread-safe. Resilience4j handles all of this in ~50 lines of configuration and wraps any supplier/function call, making it a drop-in at any service boundary.

**In an interview, if asked:** "Resilience4j is the standard circuit breaker library for Spring Boot — I wrap outbound service calls with `circuitBreaker.executeSupplier(...)`. It tracks failure rate in a sliding window, opens the circuit when threshold is exceeded (fail-fast for the timeout duration), then probes with a single test call in half-open state. This prevents cascade failure when a downstream service is degraded."

---

### Mechanism 3 — Load Shedding

**Steps in plain English:**

1. **Assign a priority** to each incoming request — critical (payment, order confirmation) vs non-critical (analytics events, recommendation refreshes).
2. **Monitor queue depth or CPU load**. When it exceeds a threshold, reject or defer non-critical requests immediately.
3. **Return 503 or 429** to non-critical callers with a `Retry-After` header.

```java
@Component
public class LoadSheddingFilter implements Filter {

    private final ThreadPoolExecutor executor;

    public LoadSheddingFilter(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        // Step 2 — check queue depth as a load signal
        int queueDepth = executor.getQueue().size();
        boolean isHighLoad = queueDepth > 400;

        // Step 1 — identify request priority from path or header
        boolean isCritical = httpReq.getRequestURI().startsWith("/api/payments")
            || httpReq.getRequestURI().startsWith("/api/orders");

        if (isHighLoad && !isCritical) {
            // Step 3 — shed non-critical load: return 503 with retry hint
            httpRes.setStatus(503);
            httpRes.setHeader("Retry-After", "5");
            httpRes.getWriter().write("Service overloaded — non-critical request deferred");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

---

### Mechanism 4 — Reactive Streams Backpressure (Project Reactor)

**Steps in plain English:**

1. **Publisher emits** items on demand — it does NOT push blindly.
2. **Subscriber requests** a specific number of items (`request(N)`) — the publisher sends at most N items.
3. **`onBackpressureDrop` / `onBackpressureBuffer`** — if publisher produces faster than subscriber requests, choose to drop or buffer up to a limit.

```java
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class BackpressureExample {

    public void processWithBackpressure() {
        Flux.range(1, 1_000_000)
            // Step 2 — subscriber pulls at most 10 items at a time
            .onBackpressureDrop(dropped -> System.out.println("Dropped: " + dropped))
            // Run on a different thread to simulate slow consumer
            .publishOn(Schedulers.boundedElastic())
            .subscribe(
                item -> {
                    // Slow consumer — takes 10ms per item
                    processItem(item);
                },
                error -> System.err.println("Error: " + error),
                () -> System.out.println("Completed")
            );
    }

    private void processItem(int item) {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### What is Project Reactor, and why does it fit here?

**Project Reactor** is a reactive programming library for Java (the foundation of Spring WebFlux) that implements the Reactive Streams specification. Its defining feature is a **pull model** — subscribers request items from publishers at a rate they can handle, rather than publishers pushing items as fast as possible.

**Why it fits backpressure:** In a traditional imperative model, data flows push-style — a Kafka consumer or HTTP client floods your handler with data. Project Reactor's pull model makes backpressure first-class: the subscriber dictates throughput. If the subscriber falls behind, the publisher pauses rather than flooding a buffer.

**In an interview, if asked:** "Project Reactor implements the Reactive Streams spec — its pull model means the subscriber calls `request(N)` to pull N items from the publisher, so the publisher never sends more than the subscriber can handle. In Spring WebFlux, this is automatic — the framework controls demand signalling. For Kafka consumers, it pairs with `reactor-kafka` to apply reactive backpressure to message processing."

---

## 🏢 Real World — Where Companies Use This

- **Netflix** (Resilience4j / formerly Hystrix): Every outbound service call (recommendations, billing, auth) is wrapped in a circuit breaker. When the recommendations service degrades, the circuit opens — users get a cached or default list instead of the app hanging. Netflix open-sourced Hystrix and now uses Resilience4j across its Spring Boot services.
- **Uber** (surge pricing as economic backpressure): When ride demand exceeds driver supply (producer > consumer capacity), Uber raises prices — this reduces demand (fewer riders request) and increases supply (more drivers activate). Price is a market-level backpressure signal.
- **Zomato** (load shedding during peak ordering): On New Year's Eve, Zomato's backend applies load shedding — restaurant menu updates, review processing, and recommendation refreshes are deferred. Only order placement and payment paths remain at full capacity.
- **Kafka producers** (acks and `max.block.ms`): When a Kafka broker is overloaded, producers block on `producer.send()` for up to `max.block.ms` milliseconds — this is Kafka's built-in backpressure signal. Setting `max.block.ms = 1000` means the producer gives up after 1 second and the application can handle the failure gracefully.
- **Amazon SQS + Lambda** (concurrency limits): Lambda has a configurable concurrency limit per function. When that limit is reached, SQS messages are not consumed — they stay in the queue. The queue growing is the backpressure signal to the upstream publisher that the consumer is saturated.
- **Razorpay** (bounded thread pools on payment processing): Payment processing threads are bounded — never more than N concurrent payments. When N is reached, additional payment requests receive a 429 and are asked to retry. This protects the downstream bank API from being overwhelmed by retries.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Producer throughput can exceed consumer capacity under any foreseeable load spike | System is single-threaded or inherently sequential — no producer-consumer gap |
| Downstream dependency is unreliable or variable in latency | All components are in the same process and share the same thread pool |
| Memory or queue size is bounded and overflow causes OOM | Drop / reject is unacceptable — every request must eventually be processed (use a durable queue + retry instead) |
| You need to protect a critical path from non-critical overload | The concept is new to the team — circuit breakers require careful threshold tuning to avoid false trips |

**The common mistake:** Setting circuit breaker thresholds without load testing. A threshold of 50% failure rate in a 10-call window means 5 failures in 10 calls opens the circuit. During a brief network glitch, this trips prematurely and causes load shedding when the service is actually fine. Use a larger window (100 calls) and a dedicated slow-call rate threshold in addition to failure rate.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | System stability under overload, predictable latency (fail fast vs hang), protection from cascade failures, clear capacity contract between services |
| **You lose** | Some requests are rejected (requires graceful client retry logic), circuit breaker tuning complexity (false trips, threshold calibration), load shedding means non-critical work is not guaranteed to complete |
| **Failure mode** | Backpressure applied too aggressively — circuit opens at the first sign of trouble, healthy downstream recovers but circuit stays open through the wait timeout. All requests fail for 30 seconds unnecessarily. Fix: tune `waitDurationInOpenState` tightly to your typical downstream recovery time, not a conservative default |

---

## 🔬 Interview Q&As

### Q: "What is backpressure and why do you need it?"

> Backpressure is the mechanism by which a slower downstream consumer signals a faster upstream producer to slow down or stop. Without it, the gap between production rate and consumption rate fills with buffered work — queues grow unboundedly, memory exhausts, and the consumer crashes. Backpressure converts an unbounded memory problem into a bounded throughput problem: some requests are deferred or rejected, but the system stays stable.

---

### Q: "What is a circuit breaker and what problem does it solve?"

> A circuit breaker wraps outbound service calls and tracks their failure rate. When failures exceed a threshold (e.g., 50% in the last 10 calls), the circuit "opens" — subsequent calls fail immediately without attempting the downstream service. This prevents two failure modes: (1) threads piling up waiting for a slow/dead downstream, exhausting the thread pool (cascade failure), and (2) overwhelming a degraded service with more requests while it's trying to recover. After a timeout, the circuit enters half-open state and lets one probe call through to test recovery.

---

### Q: "What is the difference between load shedding and rate limiting?"

> Rate limiting controls the **inbound rate** at a service boundary — enforced per user or API key, regardless of current load. It's a policy. Load shedding is a **reactive response to current system load** — when CPU or queue depth exceeds a threshold, low-priority requests are dropped. Rate limiting prevents a single client from abusing the system. Load shedding protects the system from collective overload when aggregate traffic is too high regardless of who's sending it. You typically use both: rate limiting as the first line of defence, load shedding as the last line before the service falls over.

---

### Q: "How does a bounded queue implement backpressure in Java?"

> A `ThreadPoolExecutor` with an `ArrayBlockingQueue(capacity)` and `AbortPolicy` (or `CallerRunsPolicy`) applies backpressure at the thread-pool boundary. When the queue is full, new task submissions throw `RejectedExecutionException`. The calling code catches this and returns HTTP 429 to the client. The client is responsible for retry with backoff. The key is that the queue is **bounded** — an unbounded `LinkedBlockingQueue` never signals pressure, it just grows until OOM.

---

### Q (Tier 2): "Your Kafka consumer is processing 500 messages/sec but messages arrive at 2,000/sec. Consumer lag grows to 10 million messages. How do you apply backpressure and what are the trade-offs?"

> Kafka has no native backpressure signal from consumer to producer — the topic receives messages regardless. Three approaches: (1) **Scale consumers horizontally** — add more consumer instances up to the partition count. Fastest fix, but requires enough partitions. (2) **Reduce producer rate** — if you control the producer, add a rate limiter tied to consumer lag metrics (monitor lag via `kafka.consumer.group.lag`, alert at 1M, pause producer or slow publishing at 10M). (3) **Load shedding in the consumer** — classify messages by priority; drop or skip non-critical messages when lag exceeds threshold (process payments, skip analytics events). Trade-off on (3): dropped messages are permanently lost unless you write them to a dead-letter topic. For critical data, option (1) is mandatory and (2) is added as a safety valve.

---

### Q (Tier 2): "Resilience4j circuit breaker opens on a brief 5-second network glitch. The downstream service recovers in 10 seconds but your `waitDurationInOpenState` is 60 seconds. What's the impact and how do you fix it?"

> For 50 seconds after the downstream recovered, all calls to that service fail fast with `CallNotPermittedException` — the circuit stays open even though the service is healthy. Clients get errors they didn't need to. The impact is real: in a payment flow, 50 seconds of unnecessary failures. Fix: set `waitDurationInOpenState` to match your P95 downstream recovery time — if services typically recover in 10-15 seconds, use 15 seconds, not 60. Also configure `permittedNumberOfCallsInHalfOpenState = 5` so you get statistical confidence on recovery (not just one successful probe). Monitor circuit breaker state as a metric (`circuitbreaker.state`, `circuitbreaker.calls`) and set alerts so you know when it opens unexpectedly.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Backpressure is the principle that every producer-consumer boundary needs a 'slow down' signal — implemented as bounded queues (fail with 429 when full), circuit breakers (fail fast when downstream is degraded, state: closed/open/half-open via Resilience4j), load shedding (drop non-critical requests when system load is high), or reactive pull (Project Reactor's request(N) model where the subscriber controls throughput)."

---

## 🔗 Related Concepts

- **`02-rate-limiting.md`** — rate limiting is backpressure at the API boundary enforced per-client; load shedding is backpressure enforced per-system-health
- **`09-sharded-counters.md`** — queue depth and consumer lag are counters; sharded counters are used to track load signals that trigger backpressure decisions
- **`07-cdc-outbox.md`** — the outbox processor's polling interval is a form of self-imposed backpressure — it controls how fast events are published to Kafka relative to DB write rate
- **`06-distributed-locking.md`** — circuit breakers prevent cascading lock contention by failing fast before acquiring distributed locks on an overloaded downstream

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Backpressure"** — Arpit Bhayani (YouTube: "Arpit Bhayani backpressure") | Deep dive on bounded queues, explicit signalling mechanics, Kafka consumer lag as backpressure — most technically rigorous free explanation | ~30 min |
| **"Circuit Breaker Pattern"** — ByteByteGo (YouTube: search "ByteByteGo circuit breaker") | Visual animation of closed/open/half-open states with failure rate tracking | ~8 min |
| **Resilience4j docs — Circuit Breaker** (resilience4j.readme.io) | Configuration reference for all threshold parameters — essential before production use | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers: producer-consumer mismatch root cause, bounded queue (ThreadPoolExecutor + AbortPolicy), circuit breaker (Resilience4j, closed/open/half-open states), load shedding (priority-based filter), reactive pull (Project Reactor). 6 Q&As (4 Tier 1 + 2 Tier 2). |
