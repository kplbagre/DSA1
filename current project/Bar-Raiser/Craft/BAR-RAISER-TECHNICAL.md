# Bar Raiser — Technical Round Prep
### Senior Software Engineer | Based on 2025–2026 interview research + real production work

> **What the Bar Raiser checks technically:**
> Not algorithms. They check: do you think in trade-offs, do you design for failure,
> do you own production, do you understand concurrency at depth, can you explain decisions
> you made — not just code you wrote?
>
> **Your edge:** You work on a 700K req/min JVM system in production.
> Every technical question has a real answer from your actual work.

---

## 📋 TABLE OF CONTENTS

**🔍 Category 1 — Debugging & Production Incidents**
- [Q1 — Walk me through debugging a production issue you've never seen](#q1-walk-me-through-how-you-debug-a-production-issue-youve-never-seen-before)
- [Q2 — A bug that was extremely hard to reproduce](#q2-describe-a-time-you-found-a-bug-that-was-extremely-hard-to-reproduce)
- [Q3 — How do you prevent a production incident from repeating?](#q3-how-do-you-make-sure-a-production-incident-doesnt-repeat)

**⚙️ Category 2 — Concurrency and Multithreading**
- [Q4 — Walk me through your threading model](#q4-how-does-your-system-handle-concurrency-walk-me-through-the-threading-model)
- [Q5 — What's a race condition? Have you dealt with one?](#q5-whats-a-race-condition-have-you-dealt-with-one)
- [Q6 — Callable vs Runnable vs CompletableFuture](#q6-what-is-the-difference-between-callable-vs-runnable-and-when-do-you-use-completablefuture)

**🏗️ Category 3 — System Design Concepts**
- [Q7 — Design a system that needs to be fast AND correct for inventory](#q7-how-would-you-design-a-system-that-needs-to-be-both-fast-and-correct-for-inventory)
- [Q8 — How do you design for failure?](#q8-how-do-you-design-for-failure)
- [Q9 — Distributed system consistency](#q9-how-do-you-handle-distributed-system-consistency)

**✅ Category 4 — Code Quality, Design Patterns, Technical Debt**
- [Q10 — Code review approach](#q10-how-do-you-approach-code-review-what-do-you-look-for)
- [Q11 — Managing technical debt](#q11-how-do-you-manage-technical-debt)
- [Q12 — Design pattern applied in production](#q12-walk-me-through-a-design-pattern-youve-applied-in-production)

**📊 Category 5 — Observability and Production Mindset**
- [Q13 — How do you make a system observable?](#q13-how-do-you-make-a-system-observable)
- [Q14 — What metrics do you watch for system health?](#q14-what-metrics-do-you-watch-to-know-your-system-is-healthy)
- [Q23 — How does your application emit logs? (system + application)](#q23-how-does-your-application-emit-logs-walk-me-through-both-system-logs-and-application-logs)
- [Q24 — What dashboards do you use and how do they get data?](#q24-what-kind-of-dashboards-do-you-use-and-how-do-they-get-their-data)

**🔌 Category 6 — API Design and Contracts**
- [Q15 — API design that won't break consumers](#q15-how-do-you-design-an-api-that-wont-break-consumers-when-you-add-fields)
- [Q16 — Backward compatibility in event-driven systems](#q16-whats-your-approach-to-backward-compatibility-in-event-driven-systems)

**☕ Category 7 — Java-Specific Depth**
- [Q17 — synchronized vs ReentrantLock vs ConcurrentHashMap](#q17-what-is-the-difference-between-synchronized-reentrantlock-and-concurrenthashmap)
- [Q18 — volatile and when to use it](#q18-explain-volatile-and-when-to-use-it)

**⚖️ Category 8 — Technical Trade-offs**
- [Q19 — A technical trade-off you made and why](#q19-describe-a-technical-trade-off-you-made-and-why)
- [Q20 — CCM config flag vs. just making a change](#q20-how-do-you-decide-between-adding-a-ccm-config-flag-vs-just-making-a-change)

**📨 Category 9 — Kafka Consumer Design**
- [Q21 — Kafka consumer pipeline for reliability](#q21-how-do-you-design-a-kafka-consumer-pipeline-to-be-reliable-in-production)

**🧪 Category 10 — Testing Strategy**
- [Q22 — Testing concurrent / distributed code — what slips through?](#q22-how-do-you-test-code-that-has-concurrent-or-distributed-behavior-what-slips-through)

---

## CATEGORY 1 — Debugging & Production Incidents

### Q1: Walk me through how you debug a production issue you've never seen before.

**What they're testing:** Do you have a methodology or do you guess? Do you fix the symptom or the cause?

**Answer:**
I follow a consistent sequence so I don't waste time in the wrong layer.

First, I establish scope — is this one customer, one market, one region, or global? That immediately tells me whether it's a data problem (isolated) or a code/config problem (systemic). I use Grafana dashboards for this — p95 latency, error rate, fallback rate, Kafka consumer lag — anything that breaks the pattern tells me where to look.

Second, I get a correlation ID. Every request in our system carries one end-to-end. I search the observability platform by correlation ID and look at the rejection reason codes — we have a typed enum of 40+ rejection reasons (`DELIVERY_FROM_STORE_RESTRICTION`, `SLA_TRIMMER_REJECTED`, `DC_CM_SERVICE_ELIGIBILITY`, etc.) — so I immediately know which layer rejected and why.

Third, I validate the data. If the rejection reason points to a store or distributor, I go to Cassandra and check what the DB actually has — SSE, configured_cm_map, lat/lon, capacity. Half the time the code is correct and the data is wrong.

Fourth, if it's a code issue, I trace the call path in the source. I know the component architecture well enough to go from a rejection reason to the exact class and method in under 2 minutes.

Real example: We had a CA p95 spike — latency jumped but nothing changed in traffic or deploys. Grafana showed the spike was in promise calls specifically, not sourcing. I added per-stage timing logs and traced it to `TraceEventPublisher.logSolution()`. Reading the serialization input, `boxesToPackedItemBoxMap` — hundreds of entries — was being serialized per candidate per call. 50 candidates × large object = bottleneck. Fixed with a CCM flag, CPU dropped immediately.

The meta-lesson: work top-down from scope to layer to specific cause. Never start by changing code before you know the root cause.

---

### Q2: Describe a time you found a bug that was extremely hard to reproduce.

**Answer:** Non-determinism is the hardest class of bug. We had one where the same order returned different delivery dates depending on which pod served the request. Same item, same customer zip, same timestamp — but pod A gave one date and pod B gave a different one.

The code path was `AsdFulfillmentPlanner.java`:
```java
ZipSlaCaseData zipSlaCaseData = sourcingContext.getSlaBasedContexts().keySet().iterator().next();
```

`getSlaBasedContexts()` returns a `HashMap`. `keySet().iterator().next()` on a HashMap returns an **arbitrary** key — Java's HashMap does not guarantee iteration order. The order depends on each key's hashCode and the internal bucket state, which differs across JVM instances with different GC histories.

For a multi-destination order, this map had more than one key. Different pods returned different keys → different SLA context used → different capacity pool calculated → different EDD.

To find it I had to route the same request to specific pods, capture full trace events for both responses, and diff them field-by-field to find where they diverged. Once I saw the `ZipSlaCaseData` was different, I searched every place in the codebase that called `.keySet().iterator().next()` on a map that could have multiple entries.

The fix was one line — use `getCustomerZipSlaCaseData()` which is explicitly set from the request.

Lesson: if you can't reproduce a bug on demand, look for non-determinism first — HashMap iteration, thread scheduling, clock skew, or environment differences.

---

### Q3: How do you make sure a production incident doesn't repeat?

**Answer:** Fix the incident, then fix the class of bug, then fix the visibility.

For the incident: immediate fix, rolled out incrementally if possible, or behind a CCM flag so rollback is instant.

For the class of bug: I look for every other place in the codebase where the same pattern could cause the same problem. For the HashMap non-determinism bug, I searched for all `.keySet().iterator().next()` calls on maps with multiple entries. For the unmodifiable list bug (`.toList()` in Java 16+ returns immutable), I audited every Stream collect in the affected modules.

For visibility: I add a metric or alert so the next person gets signal faster than I did. For the serialization overhead issue, I added a CCM-controlled gauge for `boxesToPackedItemBoxMap` size so we'd see if it grew again.

A post-mortem for anything that caused customer impact. Even a one-page doc — what happened, what was the root cause, what prevented earlier detection, what changes were made. The goal isn't blame — it's making the system better at catching its own problems.

---

## CATEGORY 2 — Concurrency and Multithreading

### Q4: How does your system handle concurrency? Walk me through the threading model.

**Answer:** The platform uses a `SynchronousExecutorService` — 300 core threads, 800 max. `SynchronousQueue` as the work queue, which has zero capacity. When all threads are busy, new submissions either get a thread from the expansion pool (up to 800) or fail immediately — there's no queuing. This is a deliberate design: it's fail-fast under overload rather than accepting unbounded queuing, which would hide capacity problems and make latency unpredictable.

Orchestration uses `CompletableFuture`. For each request we launch parallel futures for different sourcing routes, each on the executor. `.thenCombine()` to merge results, `.exceptionally()` on each future to catch failures and return a pre-computed fallback — so one slow downstream can't block the whole request.

Critical detail: ThreadLocals. We use `RequestContextHolder` to carry `correlationId`, `buId`, and trace context through the call chain. But `CompletableFuture` doesn't inherit ThreadLocals — when a future dispatches to a thread pool thread, it's a fresh thread with no context. So we explicitly capture the context before submitting and set it on the worker thread:
```java
final TraceLoggingContext captured = TraceLoggingContext.get();
CompletableFuture.supplyAsync(() -> {
    TraceLoggingContext.set(captured);
    return doWork();
}, orchestratorExecutorService);
```
If you don't do this, correlation IDs are missing from all async logs — which makes production debugging extremely hard.

---

### Q5: What's a race condition? Have you dealt with one?

**Answer:** A race condition is when the correctness of your program depends on the relative timing of two or more threads' execution — and the timing is not guaranteed.

In the Map overwrite bug we had in the platform — not a classic race, but the same category of reasoning. For each carrier-eligible item in a multi-item order, the code was:
```java
sourcingContext.setDcCmTntVersionMap(distributorMap);
```
This replaced the entire map on each iteration. Item A's data was overwritten by item B. That's a shared mutable state bug. In a truly concurrent version it would be a race. In this case it was sequential but still wrong because the writer didn't understand that the map was accumulative, not per-item.

The fix: `putAll()` instead of `set()`, with null-safe initialization.

A real race condition would need synchronization — either `ConcurrentHashMap`, a `synchronized` block, or a `ReentrantLock`. The choice depends on contention patterns. High-read, low-write: `ReadWriteLock`. Atomic increments: `AtomicLong`. Map accumulation across threads: `ConcurrentHashMap.merge()`.

For deadlocks: the classic pattern is two threads each holding a lock the other needs. Prevention strategies: always acquire locks in the same order, use `tryLock` with timeout, or prefer `ConcurrentHashMap`/`CopyOnWriteArrayList` over manual locking where possible.

---

### Q6: What is the difference between `Callable` vs `Runnable` and when do you use `CompletableFuture`?

**Answer:**
- `Runnable`: runs a task, no return value, can't throw checked exceptions
- `Callable`: runs a task, returns a value, can throw checked exceptions
- `CompletableFuture`: async computation, can be composed, chained, combined, and recovered from

In the platform we use `CompletableFuture` for everything because the request lifecycle requires parallel execution and fallback:
```java
CompletableFuture<PromiseDateResponse> route1 = CompletableFuture.supplyAsync(() -> solveDFS(), executor)
    .exceptionally(ex -> rapidResponseFallback());
CompletableFuture<PromiseDateResponse> route2 = CompletableFuture.supplyAsync(() -> solveSFS(), executor)
    .exceptionally(ex -> rapidResponseFallback());
CompletableFuture.allOf(route1, route2).join();
```

The `.exceptionally()` on each future is critical — if DFS lookup fails, we get a fallback, not a crashed request. The caller always gets a `PromiseDateResponse`.

`SynchronousQueue` with `SynchronousExecutorService` is our back-pressure mechanism — if we can't hand off to a thread, we fail immediately rather than queuing. That prevents the situation where latency hides a capacity problem for minutes before requests start timing out.

---

## CATEGORY 3 — System Design Concepts

### Q7: How would you design a system that needs to be both fast and correct for inventory?

**Answer:** In the platform, we don't hit inventory on the hot path. Instead, we use Hollow — an in-memory cache framework. 16 different Hollow caches are hydrated by Kafka consumers. The data is published by upstream systems, consumed by our ingestion pipeline, and loaded into memory on each pod.

Trade-off: Eventual consistency vs. latency. We accept stale data of up to a few minutes because the alternative — calling the inventory service on every sourcing request at 700K req/min — is not feasible.

Protection against stale data: we monitor Kafka consumer lag on Grafana. If a hot pipeline (offers, items) lags more than 5 minutes, we page. We also track Hollow snapshot age per pod — if a pod falls behind, it's restarted.

For the few cases where real-time inventory is necessary (pre-order reserved nodes), we do call the inventory service inline — but gated behind a CCM flag per market and consumer type.

Design principle: separate the hot read path (in-memory Hollow cache) from the write path (Kafka + Cassandra). Only call external services inline when correctness requires it and the SLA allows it.

---

### Q8: How do you design for failure?

**Answer:** Three layers in our system:

**1. Per-route fallback:** Each `CompletableFuture` route has `.exceptionally()` that returns a pre-computed snapshot result — a simplified sourcing result from a pre-computed snapshot. The caller always gets a response, never an exception.

**2. Circuit breaker:** the circuit breaker (Resilience4j) wraps the entire request flow. If downstream call failure rate exceeds threshold, the circuit opens and all requests go to the fallback path without even trying the full path.

**3. Ingestion resilience:** Kafka consumers never re-throw on bad records. They log, emit a metric, and commit past the bad offset. Re-throwing blocks the partition forever — one malformed record would stop all ingestion. Retry queues with backoff handle transient failures.

For new features and for bug fixes that affect market-specific behavior: always behind a CCM flag. The Mexico DST fix is a good example — changing how we calculate UTC offset for MX orders affects every sourcing decision for buId=2. Deploying it behind a per-market CCM flag means we validate in staging with the flag on, roll out to prod incrementally, and if something unexpected happens we flip it back in 30 seconds without a revert deploy. A fix that silently changes delivery dates for an entire market is exactly the scenario where you want that instant rollback lever — not just for features, but for any behavioral change.

---

### Q9: How do you handle distributed system consistency?

**Answer:** The honest answer is we pick the right consistency level per use case rather than trying to be consistent everywhere.

For sourcing decisions (what store to use, what date to promise): we tolerate eventual consistency. Cache data can be a few minutes stale. The customer won't notice if the inventory count is 5 minutes old.

For reservations (locking inventory for a specific order): we call Cassandra with `QUORUM` consistency — majority of replicas must agree. An over-reservation is much worse than a slow response.

For configuration (CCM flags that control behavior): these are read at request time from a hot cache refreshed every few seconds. Changes propagate in under a minute without a deploy.

Multi-market consistency: CA and MX Hollow caches used to share a `PredictiveEsdEdd3PCacheConsumer`. When a Kafka trigger fired for one market, it would reload both markets' data using the same consumer, sometimes overwriting one with the other's data. Fixed by creating a `PredictiveEsdEdd3PCACacheConsumer` — separate consumer class per market, routed by `BusinessUnit` in `CacheInit`. Lesson: in multi-market systems, cache isolation is as important as data isolation.

---

## CATEGORY 4 — Code Quality, Design Patterns, Technical Debt

### Q10: How do you approach code review? What do you look for?

**Answer:** I look at four things in order:

**1. Correctness first.** Does this handle null inputs, empty collections, concurrent access? The unmodifiable list bug (`Stream.toList()` returning immutable in Java 16+) would have been caught if the reviewer asked "what happens if downstream code tries to modify this list?" I now explicitly check collection return types in new code.

**2. Contracts and side effects.** A setter that replaces a map is different from a setter that merges into a map. The `setDcCmTntVersionMap()` overwrite bug was a contract violation — the caller assumed "set" merged, it actually replaced. Good code makes this obvious in the method name (`merge` vs `set`) or Javadoc.

**3. Failure paths.** What happens if this downstream call fails? Is there a fallback? Can this `.get()` throw NPE? Is this CompletableFuture exception swallowed?

**4. Observability.** When this code behaves unexpectedly in prod, how will we know? Is there a log line with enough context? If I add a new rejection reason, does it flow through to the typed enum that's queryable in the observability platform?

What I don't micro-manage in review: naming style, whether to use a stream vs a for-loop, minor formatting. That's what the formatter is for.

---

### Q11: How do you manage technical debt?

**Answer:** I try to make debt visible and quantified, not just complained about.

When I find something that should be fixed, I create a note or ticket with: what it is, what triggers it, what the impact is (latency? data correctness? maintainability?), and an estimate of the fix effort. I group these by risk: debt that can cause production incidents is different from debt that slows feature development.

In the platform, one example of tracked debt: we know that certain predicates in `GeneratorUtils` that were originally written for US-only behavior now have CA and MX conditions added on top. It's correct, but it's a growing mess. The right fix is extracting market-specific logic into a proper strategy pattern. That's a refactor that would take a sprint, doesn't fix a live bug, but reduces the chance of future market-specific bugs. We track it and schedule it when the team has capacity.

What I avoid: refactoring in the middle of a feature. Mixing refactor + feature in the same PR makes both harder to review and harder to roll back.

---

### Q12: Walk me through a design pattern you've applied in production.

**Answer:** Chain of Responsibility, in the sourcing filter pipeline.

When The platform evaluates whether a store is eligible for DFS, the solution passes through a sequence of filters: `DistributorCarrierEligibilityFilter` → `DfsFilter` → `SlaFilter` → etc. Each filter either passes the solution or rejects it with a typed reason from `TripletRejectedReasons` enum.

The pattern gives us:
- Each filter is independently testable
- Adding a new eligibility rule = adding a new filter class, no changes to existing ones (open-closed)
- Rejection reasons are typed and exhaustive — no "unknown rejection"
- The filter chain is configurable — you can short-circuit or reorder without touching business logic

What I added to this: making the rejection reason enum carry enough info for operational use. When I see `DELIVERY_FROM_STORE_RESTRICTION` in a log, I immediately know lat/lon is null for that store. When I see `DC_CM_SERVICE_ELIGIBILITY`, I know the CM map is empty. The enum is the contract between the code and the operator.

---

## CATEGORY 5 — Observability and Production Mindset

### Q13: How do you make a system observable?

**Answer:** Three layers: structured logs, metrics, and distributed trace events.

**Structured logs:** Every log line in the platform includes `correlationId`, `buId`, and the relevant entity ID (distributorId, offerId). When something goes wrong, I can filter by correlationId in the observability platform and get the entire request journey. Rejection reasons go to `DEBUG` — high volume, we don't emit them for every request, only when debug is enabled per-order via CCM.

**Metrics in Grafana:** p95 latency per market and route type, fallback rate (should be <5%), thread pool utilization, Kafka consumer lag per pipeline, Hollow snapshot age per pod. Each metric has an alert threshold. If CA p95 > 150ms, we get paged.

**Distributed trace events:** This is what Trace V2 added. Every sourcing decision — accepted solution, rejected solutions, capacity data seen, EDD calculated — is emitted as a structured Kafka event with a typed schema per category (ORDER, SOLUTION, TRIPLET, MOF, etc.). These land in BigQuery/the observability platform. I can write a query: "show me all TRIPLET_REJECTED events for store 3122 in the last 24 hours where rejection_reason = SLA_TRIMMER_REJECTED." This is not possible with plain logs — you'd have to grep through every log line.

The operational shift: before Trace V2, post-incident analysis required log grepping. After, it's a SQL query.

---

### Q23: How does your application emit logs? Walk me through both system logs and application logs.

**What they're testing:** Do you understand your own production observability stack end-to-end, or do you just "add log statements"?

---

#### 📚 UNDERSTAND THESE TERMS FIRST (before reading the interview answer)

**SLF4J** — Simple Logging Facade for Java. It's an *abstraction layer* — your code calls `log.info("...")` through SLF4J, and the actual logging work is done by a concrete library underneath (like Logback). Think of it as an interface. Why it exists: so your code isn't tied to one logging library — you can swap Logback for Log4j2 without touching business code.

**Logback** — The actual logging engine behind SLF4J. It reads a config file (`logback.xml`) to decide: what format should logs be in, where should they go (stdout, file, both), how often should they rotate. Most Java/Spring services use Logback by default.

**MDC (Mapped Diagnostic Context)** — A thread-local key-value store that automatically attaches to every log line. You set `MDC.put("correlationId", "abc123")` once when a request arrives, and from that point on *every* `log.info(...)` on that thread automatically includes `correlationId=abc123` without you writing it explicitly. It's how you get request context in logs without passing it as a parameter everywhere.

**ThreadLocal** — A Java mechanism where each thread has its own private copy of a variable. MDC is built on ThreadLocal. The critical implication: when your code spawns a new thread (e.g., `CompletableFuture.supplyAsync(...)`), that new thread gets a fresh, empty MDC — it does NOT inherit the parent thread's MDC. You have to explicitly copy it.

**Structured logging (JSON logs)** — Instead of plain text like `"2024-01-01 14:05 ERROR NullPointerException at FulfillmentService.java:42"`, the log line is a JSON object: `{"timestamp":"2024-01-01T14:05","level":"ERROR","correlationId":"abc123","message":"NullPointerException","class":"FulfillmentService"}`. This is machine-parseable — an observability platform can filter on any field without regex.

**Log shipping** — Application logs exist on the pod/server that wrote them. They need to get to a central place so you can search them across all pods. A log collector agent reads the log files (or pod stdout) and forwards them to a centralized log store. In Kubernetes, this is typically a **DaemonSet** — one agent pod per node that reads all other pods' logs on that node.

**DaemonSet** — A Kubernetes concept: a pod that runs on every node in the cluster. Log collector agents use this pattern so there's always exactly one log-forwarding agent per machine. Common implementations: Fluentd, Filebeat, Promtail.

**JVM GC logs** — Separate from application logs. The JVM records garbage collection events: when GC ran, how long it paused, how much heap was freed. Important because a major GC pause (200ms+) shows up in Grafana as a latency spike for every request in-flight during that pause — it looks like a code issue but is actually a JVM infrastructure issue.

---

#### 🎤 INTERVIEW ANSWER

There are two distinct log streams: application logs (what our code emits) and system logs (JVM + container).

**Application logs:**

We use SLF4J with Logback as the implementation. Log format is structured JSON — one JSON object per line — because that's what makes logs queryable in the observability platform. Plain text logs require regex; JSON logs let you filter by field.

For request context, we use MDC. When a request comes in, we put `correlationId`, `buId`, and `orderNo` into MDC immediately. Every log line from that point automatically carries those fields. The key thing we have to watch out for: MDC is ThreadLocal. When we dispatch work via `CompletableFuture` to the thread pool, the worker thread starts with empty MDC. We explicitly copy it before dispatch:

```java
Map<String, String> mdcContext = MDC.getCopyOfContextMap();
CompletableFuture.supplyAsync(() -> {
    MDC.setContextMap(mdcContext);
    return doWork();
}, executor);
```

Without this, all async log lines have no correlationId — you lose the ability to trace a request through the system.

**Log levels and what each is for:**
- `INFO` — normal lifecycle: request received, solution selected, response sent
- `WARN` — recoverable unexpected: null carrier data, config missing, fallback triggered
- `ERROR` — unrecoverable or unexpected failure: unhandled exception, thread pool rejection
- `DEBUG` — high-volume detail: per-candidate rejection reasons, capacity data, intermediate calculations. DEBUG is OFF by default because at 700K req/min, even one debug line per request would overwhelm storage. We enable it selectively via a CCM flag for a specific correlationId when actively debugging a live issue.

**System logs:**
- JVM GC logs: separate file, separate stream. First thing to check when latency spikes with no traffic change or deploy.
- Container stdout: JVM crashes (OOM, fatal signals) land here. In Kubernetes, the container runtime captures this and it's accessible via `kubectl logs`.

**How logs get to the observability platform — the shipping pipeline:**

```
Application code
  ↓ SLF4J + Logback → writes JSON to stdout
Pod stdout
  ↓ Log collector DaemonSet (one agent per K8s node)
Centralized log store (indexed by field)
  ↓
Observability platform → search by correlationId, buId, rejection reason
```

[VERIFY: What log collector does your team use? Fluentd? Filebeat? Promtail? And what is your centralized log store — Splunk? OpenSearch? Loki? Know the actual tool names before the interview.]

The design principle: don't ship DEBUG logs to the central store permanently — they'd cost a fortune in storage and slow down searches. Keep them off by default, enable per-order via config when needed.

---

### Q24: What kind of dashboards do you use and how do they get their data?

**What they're testing:** Do you understand how metrics get from your code to a graph, or do you just open dashboards someone else built?

---

#### 📚 UNDERSTAND THESE TERMS FIRST

**Metrics vs Logs** — Logs are text events ("at 14:05 request abc123 failed"). Metrics are numeric measurements over time ("at 14:05, p95 latency was 87ms, 3200 active threads"). They serve different purposes: metrics tell you the overall health trend, logs tell you what happened to a specific request.

**Micrometer** — A Java metrics instrumentation library. The SLF4J equivalent for metrics — it's an abstraction. Your code calls `counter.increment()` or `timer.record(duration)` through Micrometer, and Micrometer sends the data to whichever metrics backend you configured (Prometheus, Graphite, Datadog, etc.). It ships with Spring Boot out of the box.

**Prometheus** — A time-series database that works by **pulling** data from your app. Your app exposes a `/actuator/prometheus` endpoint that lists all current metric values. Prometheus scrapes that endpoint every ~15 seconds and stores the values with timestamps. It has its own query language (PromQL).

**Time-series data** — Data indexed by timestamp. "p95 latency at 14:05 = 85ms, at 14:06 = 92ms, at 14:07 = 88ms" is time-series. Grafana graphs this over a time window.

**Grafana** — A visualization tool. It connects to data sources like Prometheus and draws charts, histograms, and time-series graphs. You write PromQL queries to define what each panel shows. You can set alert rules — "if this metric exceeds threshold X for N minutes, fire a PagerDuty alert."

**Counter, Gauge, Timer** — The three basic metric types:
- Counter: only goes up (total requests served, total errors). Good for rates (errors per minute).
- Gauge: can go up or down (current active thread count, current queue size, cache freshness timestamp).
- Timer/Histogram: records durations and gives you percentiles (p50, p95, p99 latency).

**BigQuery** — Google's managed data warehouse. For our purposes: a place to run SQL on very large datasets (billions of rows) quickly. Our Kafka trace events land here via a consumer pipeline, creating queryable tables. Unlike logs, these are structured business events — "this store was rejected for this reason on this order" — not raw text.

---

#### 🎤 INTERVIEW ANSWER

We have three distinct observability surfaces that answer three different questions.

**1. Grafana — "Is the system healthy right now?"**

Metrics flow like this: application code → Micrometer → Prometheus → Grafana.

Micrometer is embedded in our service. We instrument the code — timers on the request handler, counters on fallback invocations, gauges on thread pool size. Micrometer exposes all of this as a `/metrics` endpoint. Prometheus scrapes that endpoint every ~15 seconds and stores the values as time-series data. Grafana queries Prometheus and renders the graphs.

What our dashboards show:
- p95 / p99 request latency per market — the primary health signal
- Thread pool: active threads, queue depth, rejected submissions — tells us if we're approaching capacity
- Fallback rate — should stay under 5%; a spike means a downstream is degraded
- Kafka consumer lag per pipeline — how stale is our in-memory cache?
- Hollow cache last-refresh timestamp per pod — which pods are falling behind?
- Rejection reason distribution — a sudden spike in one rejection type tells us something specific broke

Alert rules in Grafana page us if latency or fallback rate exceed thresholds. [VERIFY: know your exact alert thresholds — p95 threshold, fallback rate threshold. Fill from your actual dashboard.]

**2. Observability platform — "What happened to this specific order?"**

This is log-based search. We search by `correlationId` and see the full request journey: every log line from every pod that touched that request, in sequence. Because logs are structured JSON, we can filter by field — "show me all WARN logs for buId=2 in the last 10 minutes." This is where we start when a customer reports a specific order issue.

**3. BigQuery — "Why are 500 CA orders failing in the last hour?"**

Grafana tells us fallback rate spiked. BigQuery tells us which stores are responsible and for what reason.

Every sourcing decision in the platform emits a typed Kafka event. A consumer pipeline writes these to BigQuery tables in near real-time. The schema is structured by category — rejected solutions, accepted solutions, capacity data, EDD calculations. We write SQL:

```sql
SELECT store_id, rejection_reason, COUNT(*) as count
FROM triplet_rejected_events
WHERE timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
  AND market = 'CA'
GROUP BY store_id, rejection_reason
ORDER BY count DESC
LIMIT 20
```

Result: top stores rejecting CA orders and why. This used to take hours of log grepping. Now it's a 10-second query.

**Pipeline summary:**
```
Code
 ├── Micrometer → Prometheus scrape → Grafana       [real-time health / alerts]
 ├── SLF4J/Logback → Log agent → Log store           [per-request debugging]
 └── Trace V2 → Kafka → BigQuery                     [aggregate incident analysis]
```

Three different pipelines because they serve three different questions with different latency and storage trade-offs.

---

### Q14: What metrics do you watch to know your system is healthy?

Real answer from production:
- **p95 latency per market** — CA has a different SLA than US. If CA p95 goes above [VERIFY: your actual p95 alert threshold] consistently, something changed.
- **fallback rate** — the fallback path. Should be <5%. A spike means either a downstream is degraded or our thread pool hit capacity.
- **Thread pool utilization** — active threads on `orchestratorExecutorService`. 800 is max. If we trend toward 800, we're one traffic spike away from `SynchronousQueue` rejections.
- **Kafka consumer lag** — hot pipelines (offers, items) should be under a minute. If they lag 5+ minutes, Hollow caches start serving stale data. Directly affects sourcing accuracy.
- **Hollow snapshot age** — how fresh is the in-memory data on each pod. If a pod falls behind, it's silently serving stale data.
- **Rejection reason distribution** — a sudden spike in one rejection type (e.g., DELIVERY_FROM_STORE_RESTRICTION) means something specific broke — probably a data pipeline or a config change.

---

## CATEGORY 6 — API Design and Contracts

### Q15: How do you design an API that won't break consumers when you add fields?

**Answer:** Additive changes only, backward-compatible by default.

When I added `supply` and `nodeSellable` to the CA promise response (#12933), I added new optional fields. Existing consumers that didn't read them continued working. No version bump, no coordination required.

When the CA V5 slot format was introduced (multi-slot response), we added a new `slots[]` array alongside the old single-slot fields. Old consumers read the old fields. New consumers read `slots[]`. We ran dual-write (both populated) until all consumers migrated, then deprecated the old fields.

Rules I follow:
1. Never remove or rename a field in a stable API without a deprecation window
2. Add fields as optional, not required — callers that don't send them should still work
3. Version the API only when you need to break backward compatibility — not proactively
4. Use `@JsonProperty` for explicit JSON field names so Java renaming doesn't accidentally break the wire format

In the platform, adding `fetchOfferAttributesFromPayload` to `RequestPromiseDetails` for MX was purely additive — existing US callers never sent it, it defaulted to false, no behavior change.

---

### Q16: What's your approach to backward compatibility in event-driven systems?

**Answer:** Kafka consumers and producers can't be deployed atomically — there's always a window where old producers are running with new consumers or vice versa.

Strategy: schema-forward compatibility — new fields are optional, consumers handle missing fields gracefully. We use default values (`null` or `false`) for new fields so old producers that don't emit them don't break new consumers.

For Trace V2, we ran a dual-write period: both V1 (single-blob) and V2 (event-per-category) were emitted simultaneously for several weeks. This let us validate V2 output against V1 without any risk — V1 was the source of truth until V2 was proven.

For Hollow cache schema changes: the schema is versioned in the Hollow framework. We maintain a 2-version window — consumers that are one schema version behind still work. Forcing a hard upgrade on a specific date.

---

## CATEGORY 7 — Java-Specific Depth

### Q17: What is the difference between `synchronized`, `ReentrantLock`, and `ConcurrentHashMap`?

**Answer:**

`synchronized`: JVM-level monitor lock on an object. Simpler, covers a method or block. Doesn't support try-lock, timed wait, or condition variables. Non-interruptible while waiting.

`ReentrantLock`: Explicit lock with more control. Supports `tryLock(timeout)` (avoid deadlock), `lockInterruptibly()` (can be cancelled), and `Condition` objects for fine-grained signaling. Use when you need fairness, timed acquisition, or multiple wait sets.

`ConcurrentHashMap`: Lock-free for reads (uses volatile + CAS at bucket level), segmented locking for writes. Much better throughput than `Collections.synchronizedMap()` under high concurrency because reads don't block each other.

Real example from production: The `sourcingContext` is per-request and not shared across threads except for the explicitly captured `TraceLoggingContext`. When we dispatch work to the thread pool, we pass immutable inputs and collect results via `CompletableFuture` — avoiding shared mutable state entirely rather than locking it. The best concurrency design is the one that has no shared state.

---

### Q18: Explain `volatile` and when to use it.

**Answer:** `volatile` guarantees visibility across threads — a write to a `volatile` field is immediately visible to all other threads, and reads always go to main memory, not a CPU cache.

When to use: for simple flags (e.g., `volatile boolean running = true`) that are written by one thread and read by others. It's not a substitute for `synchronized` when you need atomicity across multiple operations (check-then-act, increment).

In The platform CCM: the CCM config cache is backed by a `volatile` reference. When CCM pushes a new config, the writer atomically swaps the reference (`volatile` write). All subsequent readers on any thread immediately see the new config. No lock needed because we never partially update the config — we swap the entire object at once.

---

## CATEGORY 8 — Technical Trade-offs (The Bar Raiser Favourite)

### Q19: Describe a technical trade-off you made and why.

**Answer:** For Canada's multi-slot delivery (V5 onboarding), the question was: do we build a fully generic multi-destination engine that works for all markets, or do we build a CA-specific path and generalize later?

The generic path would have been cleaner but taken 3x longer and required coordinating with 4 other teams who'd need to update their consumers. CA had a hard launch date tied to a product commitment.

I chose the CA-specific path behind `isCADiscoveryRequest()` gates. It's honest technical debt — not accidental, documented, and with a clear upgrade path. The CA-specific code is cohesive and contained. When we generalize, we have a working reference implementation.

The trade-off: we now maintain some duplication between US and CA slot-fetching paths. The payoff: we hit the product deadline, validated the feature with real traffic, and now have working code to generalize from rather than speculative architecture.

I'd make the same call again. "Ship something good now and improve it" beats "ship something perfect later" when there's a hard external deadline. But I wrote down the debt explicitly in a follow-up ticket so it doesn't stay hidden.

**Second example — sharper trade-off:** Mexico DST. When Mexico changed its DST observance law, `America/Mexico_City` in the IANA timezone database started returning UTC-5 in summer instead of UTC-6. Two options: (a) update the IANA dependency version and trust the library going forward, or (b) switch to `ZoneOffset.ofHours(-6)` — a hardcoded fixed offset.

I chose the fixed offset. The trade-off: we lose dynamic timezone behavior from IANA, but we gain explicit control over what happens when a government changes DST policy again — which they just did once. "Trust the library" is wrong here because the library's correctness depends on real-world government decisions that can change without a library release. A fixed offset makes the policy decision explicit in code, where it's visible, reviewable, and changeable via CCM if the government acts again.

---

### Q20: How do you decide between adding a CCM config flag vs. just making a change?

**Answer:** A config flag is worth its cost when the risk of the change outweighs the cost of maintaining two code paths.

I use a flag when:
- The change affects production traffic directly (sourcing behavior, date calculations)
- It's market-specific — I don't want US traffic affected by CA changes
- I want to do a percentage rollout and observe metrics before full cutover
- I need a zero-deploy rollback option

I don't use a flag when:
- It's a pure bug fix with no behavioral ambiguity — adding a null check doesn't need a flag
- It's a refactor that doesn't change observable behavior
- The extra code path makes the codebase harder to understand than the risk warrants

In the platform, every significant new behavior is behind a CCM flag. The `disable.boxWithCost.from.logging` flag was essential — if clearing the map had had an unexpected side effect, I needed to turn it off in 30 seconds without a deploy. The CA-specific capacity check flag (`sfs.store.capacity.check.enabled=true` for buId=1) means CA's behavior is explicitly configured, not implicitly inherited.

The general rule: flags are cheap when they protect expensive rollouts. They're expensive when they outlive their usefulness and become permanent dead code.

---

## CATEGORY 9 — Kafka Consumer Design and Reliability

### Q21: How do you design a Kafka consumer pipeline to be reliable in production?

**What they're testing:** Do you know what goes wrong with Kafka consumers at scale? Have you owned one that broke?

**Answer:** Three principles I've learned matter most, in order of damage they prevent:

**1. Never re-throw on a bad record.** If your consumer throws an unhandled exception on a malformed message, the partition freezes. One corrupt Kafka record stops all ingestion indefinitely. The right pattern: catch, log with full context, emit a counter metric, and commit the offset past the bad record. You lose that one event, but the pipeline keeps running. For cache-hydration pipelines, a stale-but-running cache is almost always better than a frozen one. The alert on the dropped-record counter tells you the problem exists; the log gives you context to debug it.

**2. Make consumers idempotent.** At-least-once delivery is the Kafka guarantee. On consumer restart or rebalance, you will reprocess messages. If your consumer is doing a `set()` — replacing state — reprocessing is safe. If it's doing an accumulation that's not idempotent, you'll corrupt your state. In our Hollow cache consumers, loading the same cache snapshot twice results in the same in-memory state — idempotent by design. That's not an accident, it's a requirement we hold in code review.

**3. Separate consumers per concern for blast-radius isolation.** We had `PredictiveEsdEdd3PCacheConsumer` shared across CA and MX markets. When a Kafka trigger fired for one market, it reloaded both markets' cache — sometimes overwriting one with the other's data. The fix: `PredictiveEsdEdd3PCACacheConsumer` — separate consumer class per market, routed by `BusinessUnit` in `CacheInit`. This isn't duplication — it's blast-radius containment. A bad message for CA no longer risks corrupting MX's cache state.

On offset commit strategy: commit after processing, not before. If you commit before processing and crash, you've permanently lost that message. If you commit after processing and crash, you reprocess — which is fine if you're idempotent.

---

## CATEGORY 10 — Testing Strategy

### Q22: How do you test code that has concurrent or distributed behavior? What slips through?

**What they're testing:** Do you understand the limits of unit tests? Have you shipped bugs that unit tests missed, and did you learn from them?

**Answer:** The bugs that hurt most in production are the ones that passed all the unit tests.

**The multihop routing bug** is my clearest example of this. In `checkPlannedTNTApplicable`, the code had `HopType.DIRECT` hardcoded — it worked correctly for direct shipment but returned the wrong result for `HopType.INVENTORY_NODE` multihop orders (#14702). Every unit test for that method used a direct-shipment scenario. They all passed. The bug was invisible until multihop orders hit production.

Same class of miss in the 3P fulfillment multihop release cutoff (#15214) — the `HopType.DIRECT` guard was missing for third-party carriers on multihop paths. Again, single-hop test cases gave false confidence.

What I've changed in how I write and review tests because of these:

**Test the axes that the code branches on, not just the happy path.** Any method that conditions on `HopType`, `buId`, or `BuType` needs test cases for each value of that enum, not just the one the author was thinking about. I check these axes in code review specifically: "show me the test for the INVENTORY_NODE path."

**Test contracts, not just outputs.** For `CompletableFuture` ThreadLocal propagation — `TraceLoggingContext` captured before dispatch — the contract is: correlationId visible inside the async task. The test: submit to the executor, assert correlationId is present inside the lambda. This is unit-testable even though it involves threading. You're testing the guarantee, not the timing.

**For behaviors that unit tests genuinely can't cover:** arrange observability first — correlation IDs, typed rejection reason metrics, per-pod trace events — then validate with real traffic at low percentage before full cutover. Some distributed behaviors (pod-to-pod non-determinism, timing under load, ordering across partitions) only manifest in production. The answer isn't "write more unit tests" — it's "instrument the system so production itself tells you when something is wrong."

The test that's worth most: the one that tests the boundary the author didn't think about when they wrote the code.
