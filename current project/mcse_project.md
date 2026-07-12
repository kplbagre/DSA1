# MCSE — Complete Interview Guide
### Built from actual source code. Every number and class name is real.

> **Morning-of rule:** Read sections 1, 2, 3, 4, 6, and 16 only. The rest is deep-dive ammo.

---

## 1. The One-Sentence Pitch

> "I work on my company's promise and sourcing engine — the backend system that, for every item in a customer's cart, decides which warehouse or store will ship it and what delivery date the customer sees. It handles about 700,000 requests per minute with a sub-100ms p95 latency."

Memorise that sentence. Say it first every time, unprompted.

---

## 2. The System in Plain English (30-second version)

A customer adds a 65" TV to their cart. Before that cart loads, MCSE fires:
1. **Checks every warehouse** that could ship this TV: FC Dallas, FC Atlanta, FC Chicago, WFS seller nodes — 50 to 100 candidates.
2. **For each warehouse**, asks: Do you have stock? What's the transit time to this zip? Is there delivery slot capacity?
3. **Picks the best**: lowest cost + earliest date that doesn't oversell capacity.
4. **Returns a delivery date** — "Get it by Thursday" — within 100ms.

That happens for every item in the cart, simultaneously, 700,000 times per minute.

---

## 3. The Draw Diagram (2 minutes from memory)

Draw in this exact order. Pause and talk between each step.

### Step 1 — Outer boundary (20 seconds)
```
[ Search / Item Page / Cart / Checkout ]
                   │
                   ▼
           [Unified Promise]
                   │
                   ▼
               [ MCSE ]
                   │
                   ▼
        [ Delivery date returned ]
```
*Say:* "MCSE sits in the middle. It receives a sourcing request from Unified Promise — our API gateway — and returns a sourcing decision: which warehouse ships this item and on what date."

### Step 2 — MCSE internals (40 seconds)
```
┌─────────────────────────────────────────────────┐
│  MCSE  (modular monolith, ~30 Maven modules)    │
│                                                  │
│  ┌─────────────────────────────────────────┐    │
│  │  Pre-Scatter                            │    │
│  │  Fetch all input data in parallel:      │    │
│  │  inventory, eligibility, capacity,      │    │
│  │  distributor data, offer templates      │    │
│  └─────────────────────────────────────────┘    │
│                    │                             │
│                    ▼                             │
│  ┌─────────────────────────────────────────┐    │
│  │  Orchestrator                           │    │
│  │  50–100 CompletableFuture evaluations   │    │
│  │  (item × warehouse × shipping method)   │    │
│  └─────────────────────────────────────────┘    │
│                    │                             │
│                    ▼                             │
│  ┌─────────────────────────────────────────┐    │
│  │  Gather                                 │    │
│  │  thenCombine → reduce → best answer     │    │
│  └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

### Step 3 — Caches (20 seconds)
```
[ MCSE ] ←──reads──→ [ 16 Hollow In-Memory Caches ]
                       TNT cache (transit times)
                       PoolConfigCache (warehouse config)
                       FlipsCache (capacity open/closed)
                       OfferDataCache, DateCache, ...
```
*Say:* "MCSE reads all reference data — transit times, warehouse configs, capacity signals — from 16 in-memory caches. These are memory-mapped snapshots using Netflix's Hollow framework. Sub-microsecond reads, zero network hops. This is how 50–100 lookups happen inside 100ms."

**Translation key (important for interview):**
- **FlipsCache** = capacity-flip state cache (which warehouse delivery slots are open vs. closed)
- **TNT** = Transit-Time cache (warehouse × carrier × zip → how many days to deliver)

### Step 4 — Write path (20 seconds)
```
18 Domain Teams → Kafka → Ingestion Service → Cassandra
                                                    │
                                               Spark job
                                                    │
                                            cache-generator
                                                    │
                                       All pods atomic-swap Hollow snapshot
```
*Say:* "The write side is completely separate. 18 domain teams publish events on Kafka. Our ingestion service writes to Cassandra. A Spark batch job reads Cassandra and builds new cache snapshots every ~10 minutes. Pods atomically swap. The hot request path NEVER blocks on writes."

### The complete picture — draw this on the whiteboard

```
        Search / Item Page / Cart / Checkout
                         │
                         ▼
                 [ Unified Promise ]
                   (API Gateway)
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│               MCSE  (~30 Maven modules)              │
│                                                      │
│  ┌────────────────────────────────────────────┐      │◄── [ 16 Hollow Caches ]
│  │  Pre-Scatter  (all parallel)               │      │    TNT (transit times)
│  │  Inventory (Wakanda) · Eligibility (LIMO)  │      │    FlipsCache (capacity)
│  │  Capacity (FCAP) · Distributor data        │      │    PoolConfigCache
│  │  Offer templates (3P sellers)             │      │    OfferDataCache ...
│  └────────────────────────────────────────────┘      │
│                        │                             │    Sub-microsecond reads
│                        ▼                             │    Memory-mapped, in-process
│  ┌────────────────────────────────────────────┐      │    Pre-warmed at pod startup
│  │  Orchestrator  (50–100 parallel evals)     │      │
│  │  CompletableFuture per candidate           │      │
│  │  Each: real work races timeout → fallback  │      │
│  └────────────────────────────────────────────┘      │
│                        │                             │
│                        ▼                             │
│  ┌────────────────────────────────────────────┐      │
│  │  Gather / MOF                              │      │
│  │  thenCombine reduce → best warehouse+date  │      │
│  └────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
             "Arrives by Thursday" → customer


WRITE PATH  (completely separate, never on hot path):

18 Domain Teams
      │  Kafka events
      ▼
[ Ingestion Service ]   ← 18 deployments of one JAR, each with different listeners
      │  writes
      ▼
[ Cassandra ]  (multi-DC, last-write-wins)
      │  Spark reads every ~10 min
      ▼
[ cache-generator → versioned Hollow snapshot → GCS ]
      │  Announcer notifies all pods
      ▼
[ Every MCSE pod: download → validate → atomic pointer swap ]
```

---

## 4. Callable + Future — Teach From Scratch

### Simple version (say this if asked what Callable or Future is)

> *"Imagine you hand a task to a restaurant kitchen: 'Make me a pizza.' They give you a pager. You can go sit down and do other things. When the pizza is ready, you come back and pick it up. In Java, `Callable` is the task — 'make me a pizza, and tell me if something went wrong.' `Future` is the pager — it holds the result once the task is done. If you call `future.get()` before the pizza is ready, you just stand at the counter and wait. That's blocking."*

### Code shape
```java
// The task
Callable<PromiseDateResponse> task = () -> callDownstreamService(request);

// Hand it to the thread pool
Future<PromiseDateResponse> future = executorService.submit(task);

// Do other work here...

// Now block until done
PromiseDateResponse response = future.get(1800, MILLISECONDS); // timeout after 1800ms
```

### When MCSE uses it
**Pre-Scatter phase.** Before the orchestrator can evaluate anything, we need ALL input data: inventory, eligibility, capacity slots, distributor data, offer templates. We kick off all 5–6 fetches in parallel. Then we block on all of them. We NEED all results before moving forward. `Callable + Future` (or `invokeAll()` on a thread pool) is exactly right here — parallel work, then wait for all.

### Deep-dive: What if the interviewer pushes further?

**"What's the difference between Callable and Runnable?"**
> *"Runnable can't return a value and can't throw checked exceptions — it's fire-and-forget. Callable can return a result and declare checked exceptions, so you can propagate errors back through the Future."*

**"What happens if you never call future.get()?"**
> *"The task still runs on the thread pool — submitting a Callable and getting a Future is just scheduling the task. If you never call get(), you never retrieve the result. Memory risk: if you hold many uncollected Futures, the thread pool may finish them but the result objects stay in heap until you collect or the GC runs."*

**"What does future.get() do internally?"**
> *"It calls Object.wait() on the underlying FutureTask object. When the task thread finishes, it calls Object.notifyAll() to wake up any threads blocked on get(). It's a classic producer-consumer wait/notify pattern inside java.util.concurrent.FutureTask."*

**"What is invokeAll()?"**
> *"ExecutorService.invokeAll(collection of Callables) submits all tasks at once, then blocks until ALL of them complete (or time out). Returns a List<Future<T>> in the same order as the input. Simpler than managing individual futures and joining them — especially useful for pre-scatter where we need all N results."*

---

## 5. CompletableFuture — Teach From Scratch

### Simple version (say this first, always)

> *"Think of it like a delivery tracking pipeline you set up in advance. You say: 'When the package ships, update the tracking app. When it arrives, send me a text. If anything goes wrong, leave a note.' You set up all these instructions BEFORE the package even leaves the warehouse. CompletableFuture works the same way — you chain instructions: 'when this async task finishes, do this next, and if it fails, run this fallback instead.' You never block to wait — you just describe what should happen."*

### Simple vs Future comparison
```
Future:
  result = future.get()   ← YOU BLOCK HERE until task is done
                             Thread is wasted while waiting

CompletableFuture:
  future
    .thenApply(result -> transform(result))      // "when done, transform it"
    .thenCombine(otherFuture, (a, b) -> merge(a, b))  // "combine with another"
    .exceptionally(ex -> fallback(ex))           // "if anything fails, use this"
  // No blocking. Thread is free to do other work.
```

### The real MCSE pattern — CompletableDistributor.java (actual source code)

```java
// From: orchestrator/service/completablefuture/CompletableDistributor.java

private CompletableFuture<PromiseDateResponse> getResponseCompletableFuture(
        PromiseDateRequestWrapper promiseDateRequestWrapper) {

    return CompletableFuture
        .supplyAsync(
            dateServiceResponseSupplier.blockingSupply(promiseDateRequestWrapper),
            orchestratorExecutorService   // ← our custom thread pool (300 core / 800 max)
        )
        .applyToEither(
            failAfter(timeout.ofCompletableFuture()),  // ← race against a timeout future
            Function.identity()
        )
        .exceptionally(ex -> {
            log.error("Something went wrong : ", ex);
            return orchestratorRRUtil.generateRapidResponse(promiseDateRequestWrapper); // ← fallback
        });
}
```

**What each line does — in plain English:**

| Line | What it does |
|---|---|
| `supplyAsync(supplier, orchestratorExecutorService)` | Submit the actual work to our thread pool — evaluate warehouse X for this item. Non-blocking. |
| `.applyToEither(failAfter(timeout), Function.identity())` | Race two futures: real work vs. a timer. Whichever completes first wins. If the timer wins → TimeoutException. |
| `.exceptionally(ex -> generateRapidResponse(...))` | If ANYTHING fails — timeout, NPE, downstream error — return a pre-computed fallback response instead of blowing up. |

### How the timeout racing works (applyToEither)

```java
// From: CompletableDistributor.java
private <T> CompletableFuture<T> failAfter(long timeoutInMillis) {
    CompletableFuture<T> result = new CompletableFuture<>();
    delayer.schedule(
        () -> result.completeExceptionally(new TimeoutException()),
        timeoutInMillis,
        MILLISECONDS
    );
    return result;
}

// The delayer is a 1-thread scheduled executor — just a timer
private static final ScheduledExecutorService delayer =
        Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("failAfter-%d")
                        .build());
```

**Plain English:** `failAfter(1800)` creates a "dummy future" that does nothing except complete with a TimeoutException after 1800ms. Then `applyToEither` says "take the first one to finish — either the real work or the timer." If real work finishes in 500ms, timer is discarded. If work takes 2000ms, timer fires at 1800ms → exception → `.exceptionally()` → RapidResponse.

### The gather step — CompletableGather.java (actual source code)

```java
// From: orchestrator/service/completablefuture/CompletableGather.java

public CompletableFuture<PromiseDateResponse> execute(
        List<CompletableFuture<PromiseDateResponse>> futures) {

    PromiseDateResponse errorResponse = null;
    return futures.stream()
            .reduce(combineServiceCalls())
            .orElse(CompletableFuture.completedFuture(errorResponse));
}

private BinaryOperator<CompletableFuture<PromiseDateResponse>> combineServiceCalls() {
    return (c1, c2) -> c1.thenCombine(c2, promiseDateResponseReducer::reduce);
}
```

**Plain English:** Imagine you have 5 futures: [F1, F2, F3, F4, F5]. `reduce` applies `thenCombine` pairwise:
- Step 1: F1.thenCombine(F2) → Merged12
- Step 2: Merged12.thenCombine(F3) → Merged123
- Step 3: Merged123.thenCombine(F4) → Merged1234
- Step 4: Merged1234.thenCombine(F5) → FinalResult

Each `thenCombine` fires when BOTH of its inputs complete. So if F3 finishes before F1+F2 combine, it waits — but no thread blocks. The final result is a single `CompletableFuture<PromiseDateResponse>` that resolves when all 5 are done. `promiseDateResponseReducer::reduce` is the merging logic (picks best date, combines candidates).

### The 3-step orchestrator pipeline — CompletableFutureOrchestratorService.java

```java
// From: orchestrator/service/completablefuture/CompletableFutureOrchestratorService.java

public CompletableFuture<PromiseDateResponse> process(
        PromiseDateRequestWrapper promiseDateRequestWrapper) {

    // Step 1: Split request into per-fulfillment-mode sub-requests
    List<PromiseDateRequestWrapper> scatteredRequest = scatter.execute(promiseDateRequestWrapper);

    // Step 2: Dispatch each sub-request as a CompletableFuture
    List<CompletableFuture<PromiseDateResponse>> responseFutureList =
            completableDistributor.consume(scatteredRequest);

    // Step 3: Combine all futures into a single result
    return gather.execute(responseFutureList);
}
```

**Plain English:**
1. `scatter.execute()` — If the request has SDD, SFS, and WFS fulfillment modes, split into 3 sub-requests.
2. `completableDistributor.consume()` — Each sub-request becomes a CompletableFuture. All dispatched in parallel to the thread pool.
3. `gather.execute()` — Reduce all futures via thenCombine into one final answer.

### Deep-dive: What if the interviewer pushes further on CompletableFuture?

**"What's the difference between thenApply and thenCompose?"**
> *"thenApply is for synchronous transformations — the function runs immediately in the same thread when the future completes. thenCompose is for async chaining — the function itself returns a CompletableFuture, and the result flattens the outer and inner future into one. Use thenCompose when the next step is also async; use thenApply when it's a simple transform."*

**"What thread does exceptionally() run on?"**
> *"If the upstream future completes exceptionally, the exceptionally() callback runs on the thread that completed it — usually the ForkJoinPool or your custom executor, depending on how it was set up. In our case, the TimeoutException is thrown by the delayer (ScheduledExecutorService), so exceptionally runs on the delayer thread — which is why the fallback must be fast and non-blocking."*

**"Why not just use future.get() with a timeout instead?"**
> *"future.get(timeout, unit) blocks the calling thread for the entire timeout duration. The calling thread is wasted sitting and waiting. With CompletableFuture + applyToEither, the calling thread is free — it set up the pipeline and moved on. The timeout fires on a scheduled thread pool thread, not on the calling thread. No thread wasted waiting."*

**"What happens if thenCombine's upstream future fails?"**
> *"thenCombine propagates the exception — if either input future fails, the combined future also fails with that exception. In our case, each individual future has its own .exceptionally() that catches failures before they reach gather. So by the time the futures reach thenCombine, they are always successful — either a real result or a RapidResponse fallback. The gather step never sees a failed future."*

---

## 6. Thread Pool Architecture — Real Numbers From Source Code

### The orchestrator executor (OrchestratorExecutorConfig.java)

```
Bean name:     orchestratorExecutorService
Core threads:  300   (DEFAULT_ORCHESTRATOR_EXECUTOR_POOLSIZE)
Max threads:   800   (DEFAULT_ORCHESTRATOR_EXECUTOR_MAX_POOLSIZE)
Queue:         SynchronousQueue   ← CRITICAL design decision
Keep-alive:    60 seconds
Startup:       prestartAllCoreThreads() — all 300 threads started on pod boot
```

### Why SynchronousQueue — the fail-fast design

This is the most interesting thread pool design decision in the codebase. Most thread pools use a `LinkedBlockingQueue` — tasks queue up when all threads are busy, and wait their turn. MCSE uses **SynchronousQueue** — a zero-capacity queue. There is no waiting room.

```
Normal LinkedBlockingQueue pool:
  Task arrives → all threads busy → task waits in queue → eventually runs
  Problem: queue can fill up, latency spikes, tasks stale by the time they run

SynchronousQueue (MCSE):
  Task arrives → all 800 threads busy → IMMEDIATE RejectedExecutionException
  .exceptionally() catches it → generateRapidResponse() → return fast
```

**Code comment from OrchestratorExecutorConfig.java:**
> *"adding SynchronousQueue to avoid waiting (high rt - fail first design)"*

**Interview explanation:**
> *"At 700K RPM, if all thread pool capacity is exhausted, queuing tasks makes things worse — they'll wait, pile up, and by the time they're processed the customer has already timed out. We chose to fail fast instead. If we're at capacity, we immediately return a pre-computed RapidResponse. The customer gets a date, just not the optimal one. We'd rather serve a slightly-suboptimal response in 50ms than the perfect response in 5 seconds — or a timeout."*

### SynchronousQueue → RejectedExecutionException → RapidResponse flow

```
CompletableFuture.supplyAsync(task, orchestratorExecutorService)
       ↓
  All 800 threads busy (SynchronousQueue has no queue)
       ↓
  RejectedExecutionException thrown
       ↓
  .exceptionally(ex -> orchestratorRRUtil.generateRapidResponse(wrapper))
       ↓
  Pre-computed fallback response returned in microseconds
```

### Why prestartAllCoreThreads()?

> *"Thread creation in Java takes ~1ms. At pod startup, if the first 300 requests each need to create a new thread, that's 300ms of cold-start overhead before the pool is warm. By calling prestartAllCoreThreads(), all 300 core threads are created and ready at pod boot. The first request sees the same pool performance as the millionth. Our Kubernetes readiness probe doesn't pass until this is done — so no traffic hits a cold pod."*

### Default timeout: 1800ms (Constants.java)

```java
int DEFAULT_ORCHESTRATOR_SERVICE_ROUTE_TIMEOUT = 1800; // from Constants.java
```

This is the timeout passed to `failAfter()`. If a single warehouse evaluation takes more than 1800ms, the timeout future wins, `.exceptionally()` fires, and RapidResponse is returned for that route. The other routes continue unaffected.

---

## 7. RapidResponse — The Fallback System

### What it is

RapidResponse is a pre-computed "good enough" delivery date. When the full evaluation can't complete (timeout, thread pool exhaustion, downstream failure), instead of returning an error, MCSE returns a pre-computed date from RapidResponse. The customer sees something reasonable. Not the best sourcing decision, but not an error.

### Real default values (LiteCcmDefaults.java)

```java
DEFAULT_RAPID_RESPONSE_DELAY = "1700"    // ms — how long we try before RR kicks in
DEFAULT_ITEM_PAGE_RR_TIMEOUT = "800"     // ms — tighter budget on Item Page (no cart yet)
DEFAULT_RAPID_RESPONSE_FLAG = "false"    // off by default — must be enabled via CCM
```

**Why Item Page gets 800ms?** Item page is browsing, not buying. The customer hasn't committed. A slightly-off date is less harmful than slow page load. Cart and Checkout get more time because the date shown there is the one the customer is buying against.

### Bulwark pattern — MpBulwarkImpl.java

Bulwark is a Resilience4j pattern wrapping the entire MP date service flow:

```java
// From: MpBulwarkImpl.java
// run() = the main path
protected PromiseDateResponse run() {
    return mpDateServiceManager.getDate(...);    // full evaluation
}

// fallBack() = the RapidResponse path
protected PromiseDateResponse fallBack() {
    return mcseRRManager.getDate(...);          // pre-computed fallback
}
```

**Plain English:** Bulwark is a fail-safe wrapper. Every request runs `run()` first. If `run()` throws, times out, or the circuit is open, Bulwark automatically calls `fallBack()`. The caller never sees an exception — they get either the real response or the pre-computed one.

**Interview explanation:**
> *"Bulwark is our outermost resilience layer. It's a Resilience4j pattern — not custom code. run() is the happy path; fallBack() is the RapidResponse path. What makes it powerful is that the caller doesn't need to know which path was taken. The contract is always: you get a PromiseDateResponse. Whether that's from 50 CompletableFuture evaluations or from a pre-computed cache is transparent to the caller."*

---

## 8. Hystrix on Wakanda (Inventory Calls)

### What Hystrix is (say this simply)

> *"Hystrix is a circuit-breaker library from Netflix. It wraps an external call. If the call fails too many times, Hystrix 'opens the circuit' — it stops making the call at all and immediately returns a fallback. This prevents cascading failures: if inventory is slow, Hystrix ensures our threads don't all pile up waiting for inventory — they get rejected quickly."*

### Real class: AvailabilityHystrixServiceCommand.java

```java
// extends HystrixCommand<AvailabilityServiceResponse>
// Used specifically for Wakanda (inventory service) calls
class AvailabilityHystrixServiceCommand extends HystrixCommand<AvailabilityServiceResponse> {
    // No getFallback() method
    // Failure propagates up — caught by CompletableFuture's .exceptionally()
}
```

**Key detail:** This Hystrix command has NO `getFallback()` override. This means if the Wakanda call fails, the HystrixCommand does not catch it — the exception propagates up to the CompletableFuture chain, where `.exceptionally()` handles it with RapidResponse.

**Why no fallback in Hystrix?**
> *"If we had a Hystrix fallback inside the command, we'd lose the exception context. The CompletableFuture's .exceptionally() is the single recovery point for the whole evaluation — it handles ALL failure modes (timeout, RejectedExecution, HystrixCommand failure) the same way: RapidResponse. Centralizing recovery in one place makes the system easier to reason about."*

**What Hystrix gives us even without fallback:**
- Thread pool isolation per dependency — Wakanda's pool is separate from FCAP's pool
- Automatic circuit opening after N failures
- Timeout enforcement at the Hystrix level before CompletableFuture timeout fires
- Metrics for each downstream command

---

## 9. Pre-Scatter — What Gets Fetched First

Before the orchestrator even starts, these are fetched in parallel (MpDataGeneratorWrapper.java):

| Task | What it does | Why it's needed first |
|---|---|---|
| `avsCallGenerator.generate()` | Address Validation Service — validate the customer's delivery address | All transit time calculations need a valid zip |
| `storeCatchmentGenerator.generate()` | Fetch catchment stores from Rover (store location service) | SFS/BOPIS candidates depend on what stores are near the zip |
| `mpInventoryGenerator.getSetAndEnhanceInventory()` | Sync inventory from Wakanda | Every candidate warehouse needs a stock check |
| `mpCompleteDistributorGenerator` | Fetch distributor data | Which distributors can ship this offer |
| `mpOfferTemplateConfigGenerator` | Fetch MP offer templates | For 3P sellers — which nodes are they eligible to fulfill from |
| `mpFcapGenerator.getAndSetFcap()` | Fetch FC capacity data | Which warehouse slots have delivery capacity available |

All 6 run concurrently using `@Qualifier("taskExecutor") ThreadPoolTaskExecutor`. The orchestrator starts ONLY after all complete — `PreScatterMetadata` is the data class that carries all results downstream.

**Key domain objects:**
- `PreScatterMetadata` — the data bag carrying all pre-scatter results
- `PromiseDateRequestWrapper` — the per-sub-request wrapper passed to each CompletableFuture
- `SourcingContext` / `LiteRequestContext` — per-request context objects threading through the pipeline
- `SlaBasedSingleItemSolutions` — the output of one orchestrator evaluation (one warehouse + shipping method scored)

---

## 10. Request Flow — All Three Phases With Real Class Names

### Phase 1: Pre-Scatter (~20–30ms)

```
Request arrives
      │
      ▼
MpDataGeneratorWrapper.generateData()
  ├── avsCallGenerator.generate()              → validates customer zip
  ├── storeCatchmentGenerator.generate()       → nearest stores for SFS
  ├── mpInventoryGenerator.getAndSetInventory()→ Wakanda stock check
  ├── mpCompleteDistributorGenerator           → distributor data
  ├── mpOfferTemplateConfigGenerator           → 3P seller node eligibility
  └── mpFcapGenerator.getAndSetFcap()          → FC capacity slots
      [all 6 run in parallel via taskExecutor]
      │
      ▼
PreScatterMetadata assembled
```

### Phase 2: Orchestrator — 50–100 parallel evaluations (~40–60ms)

```
CompletableFutureOrchestratorService.process()
      │
      ▼
ScatterSingleLevel.execute()
  → splits request into per-fulfillment-mode sub-requests
  → e.g. [SDD_request, SFS_request, WFS_request, S2H_request]
      │
      ▼
CompletableDistributor.consume()
  → for each sub-request:
     CompletableFuture.supplyAsync(supplier, orchestratorExecutorService)
     .applyToEither(failAfter(1800ms), Function.identity())
     .exceptionally(ex → generateRapidResponse())
  → returns List<CompletableFuture<PromiseDateResponse>>
      │
      ▼
CompletableGather.execute()
  → futures.stream()
       .reduce((c1, c2) -> c1.thenCombine(c2, promiseDateResponseReducer::reduce))
  → returns single CompletableFuture<PromiseDateResponse>
```

### Phase 3: Gather → Response (~10ms)

```
promiseDateResponseReducer::reduce
  → picks best (earliest date + lowest cost) per fulfillment type
  → MOF (Multi-Objective Function) final ranking
  → reserve inventory
  → return PromiseDateResponse to Unified Promise
```

---

## 11. Stack Justification — 4-Step Answers

Every "Why X?" question: **Problem → Why this tech → Why not alternatives → Trade-off**

---

### Why Java + Spring Boot?

**Problem:** 50–100 parallel evaluations per request, inside 100ms. CPU-bound fan-out logic.

**Why Java:** The JVM has the most mature concurrency primitives for this exact pattern — `CompletableFuture`, `ExecutorService`, per-dependency thread pool isolation. Resilience4j, Hystrix, Datastax — all first-class JVM citizens. At 700K RPM, the JVM's JIT compiles hot paths aggressively — those paths are always hot.

**Why not alternatives:**
- Node.js: Single-threaded event loop can't saturate multiple CPU cores on CPU-bound fan-out without worker threads complexity.
- Python: GIL limits true parallelism; no production story for 700K RPM at this latency target.
- Go: Strong candidate, but in 2019 our company's ecosystem and team expertise were JVM-native; framework ecosystem would need to be built from scratch.

**Trade-off:** JVM cold-start is 10–30 seconds. Memory footprint per pod is higher than Go or Node. We accept that — MCSE runs as long-lived pods on Kubernetes, not serverless functions.

---

### Why Netflix Hollow Cache and not Redis?

**Problem:** 50–100 reference lookups per request × 700K RPM = up to 70 million reference lookups per minute.

**Why Hollow:** Memory-mapped inside the process. A lookup is a pointer dereference — sub-microsecond. No network socket, no serialisation, no connection pool. All 16 caches pre-loaded at pod startup. First request as fast as the millionth.

**Why not Redis:**
- Redis is a network call. Even at 0.5ms, 50 lookups × 700K RPM = 35 million Redis GETs per minute on the hot path.
- Hollow gives one stable snapshot per pod per refresh cycle. Redis gives per-key consistency — different keys can reflect different versions in the same request. Hollow eliminates that bug class for reference data.
- Zero extra infrastructure cost.

**Trade-off:** Hollow is eventually consistent — minutes to refresh via batch. For fast-moving data (capacity flips, live signals) we layer Caffeine (JVM-local, seconds TTL) and MeghaCache (our company's internal distributed cache, cross-pod).

---

### Why Cassandra?

**Problem:** Millions of records per day from 18 domain teams. Multi-DC active-active. Sub-5ms fallthrough on hot-path misses.

**Why Cassandra:** Wide-column model — one table per access pattern, no JOINs, no cross-partition scans. Leaderless replication = no single point of failure. Linear write scale by adding nodes. Last-write-wins conflict resolution handles our concurrent-write pattern cleanly (full-record writes, not partial deltas).

**Why not a relational database:** Joins are expensive at scale. Offer × node × eligibility × transit-time requires multi-table joins or complex materialised views. Cassandra lets us pre-join at write time.

**Trade-off:** No ACID transactions. We never need "SELECT FOR UPDATE" — our model is append-and-overwrite, full records. Eventual consistency is acceptable for reference data.

---

### Why Kafka?

**Problem:** 18 domain teams push data changes without coupling their release cycles to ours. Must survive ingestion pod restarts with zero data loss.

**Why Kafka:** Durable, replayable event log. Pod restarts resume from committed offset — zero data loss. Multiple consumer types (bulk vs. reactive batched) read the same topic independently. Can replay a specific offset range to reprocess bad data.

**Why not RabbitMQ/SQS:**
- RabbitMQ: A consumed message is gone. No durable replay.
- SQS: No offset-based replay, no consumer group semantics.
- Kinesis: AWS-native; MCSE runs on Azure/GCP.

**Trade-off:** Kafka adds operational complexity — broker management, consumer lag monitoring, partition key design. We monitor consumer lag with a <5 minute SLO for hot pipelines.

---

### Why Modular Monolith and not microservices?

**Problem:** Each request fans out to 50–100 internal evaluations. Eligibility, inventory, transit time, cost calculation — all in-process, in 100ms.

**Why modular monolith:** If those were separate services, every request would make 50+ network calls. At 100ms budget, each network call costs 2–5ms minimum — the math breaks at that fan-out. By keeping ~30 Maven modules in one deployable, all evaluations happen in-process, in memory. No serialisation, no service discovery overhead.

**Boundary is correct:** Upstream (Search, Cart) and downstream (inventory, capacity) ARE microservices. MCSE is the one piece where latency makes in-process evaluation non-negotiable.

**Trade-off:** One fat JAR means a bad deploy affects all markets simultaneously. Mitigated by per-market feature flags and canary deployments (Flagger + Kubernetes).

---

## 12. The 3-Layer Cache Architecture

```
Request reads reference data
     │
     ▼
Layer 1: Hollow (sub-microsecond)
  Memory-mapped, immutable snapshot, pre-warmed at startup
  HIT → return immediately  (covers ~99% of reads)
  MISS ↓
     │
     ▼
Layer 2: Caffeine (microseconds)
  JVM-local, lazy-fill, short TTL (seconds)
  Used for: FlipsCache (live capacity signals)
  HIT → return
  MISS ↓
     │
     ▼
Layer 3: MeghaCache (1–2ms)
  Our company's internal distributed cache, cross-pod consistent
  Used for: WeatherBuffer, operational signals
  MISS → return null / default
```

| | Hollow | Caffeine | MeghaCache |
|---|---|---|---|
| Read speed | Sub-microsecond | Microseconds | ~1–2ms (network) |
| Scope | Per-pod, snapshot | Per-pod, lazy | Cross-pod |
| Freshness | ~10 min (batch) | Seconds (TTL) | Near-real-time |
| Cold start | Pre-warmed at boot | Lazy-fill | Always ready |

**FlipsCache lives in BOTH Hollow and Caffeine — by design:**
- Hollow gives a correct floor at pod startup (fail-open)
- Caffeine + Kafka delta propagates live changes within seconds
- If Kafka lags, Hollow still serves correct-if-stale flips
- The system never has zero flip state

---

## 13. How Caches Get Refreshed (Without Downtime)

### The mental model (say this first, always)

> *"Cassandra is the source of truth. The cache is a periodic snapshot of it. Every ~10 minutes, a scheduled job rebuilds the snapshot from scratch and pods atomically swap to it. Individual DB writes don't touch the cache at all — they accumulate in Cassandra, and the next rebuild picks them all up."*

**If asked: "Rebuilding the entire cache every 10 minutes — isn't that expensive?"**
> *"The rebuild runs on a separate Spark cluster, not inside the serving pods. The serving pods just download a new binary blob and swap a pointer. At 700K RPM, a 10-minute snapshot serves ~420 million requests. That's an excellent trade — pay the compute cost once, serve hundreds of millions of requests for free."*

---

### Batch path (most reference data: TNT, PoolConfig, OfferData)

```
Cassandra data changes (via ingestion pipeline)
     │
     ▼
Airflow triggers Spark job
  (Spark reads all Cassandra partitions in parallel → fast at millions of rows)
     │
     ▼
Spark assembles full dataset → versioned snapshot blob → GCS (object storage)
     │
     ▼
cache-generator reads blob → builds Hollow snapshot (memory-mappable binary)
     │
     ▼
Announcer service notifies all MCSE pods: "new snapshot version available"
     │
     ▼ (each pod simultaneously)
Pod downloads blob → memory-maps → validates checksum
   → atomic reference swap (one pointer update, microseconds)
   → old blob eligible for GC
```

**Key properties:**
- Readers never block during swap — they keep reading old snapshot
- Swap is atomic — no pod reads half-old, half-new data
- If new snapshot fails checksum validation, pod keeps old snapshot and alerts
- **Pod restart during rebuild:** Pod waits for the most recent *complete* validated snapshot. The Kubernetes readiness probe does not pass until Hollow is fully loaded. A pod never joins traffic with a partial snapshot.

---

### Incremental path (seconds latency — capacity flips only)

```
Capacity flip event (warehouse slot fills up or opens)
     │
     ▼
Published to Kafka (capacity flip topic)
     │
     ▼
FlipsInjector (in-pod Kafka consumer — applies capacity flip deltas)
     │
     ▼
Applies delta directly to the in-memory Hollow snapshot
  (no full rebuild — just updates the affected entry in place)
     │
     ▼
Next request to this pod sees updated flip — within seconds
```

**Translation:** FlipsInjector = our in-pod Kafka consumer that applies capacity flip deltas incrementally to the Hollow snapshot between full rebuilds.

**Why not use incremental for everything?**
> *"Incremental updates only work correctly if the base state is correct. If a pod restarts and rebuilds from the batch snapshot, then applies all deltas on top, we get the right state. But if we only had incrementals and a pod missed some, we'd have inconsistent state with no recovery path. Batch snapshot is the source of truth; incremental deltas are a performance optimization on top."*

---

### What happens when an offer is deleted?

**Batch path (most cases):**
> *"When an offer is deleted in Cassandra, the next full Spark rebuild naturally excludes it. The new Hollow snapshot simply doesn't contain that offer. When pods swap, it's gone. No explicit cache eviction needed — the snapshot is rebuilt from scratch, so absence is automatic."*

**Incremental path (FlipsCache):**
> *"The incremental path is delta-only — it can only apply changes to what's already in the snapshot. An explicit delete on the incremental path requires a tombstone event on Kafka. Just stopping to publish updates isn't enough — FlipsInjector won't notice an absence of events. When a tombstone arrives, FlipsInjector removes the entry from in-memory state."*

**If asked: "So stale deleted offers persist for up to 10 minutes?"**
> *"Yes — in the batch path, a deleted offer persists in Hollow until the next rebuild, up to ~10 minutes. For offers, that's acceptable — a customer seeing an offer for a few extra minutes is a minor edge case. For capacity slots that genuinely disappear, the incremental Kafka path propagates in seconds."*

---

### Staleness SLA by cache type

| Cache | Path | Max staleness | Acceptable? |
|---|---|---|---|
| TNT, PoolConfig, OfferData | Batch rebuild | ~10 minutes | Yes — reference data, small drift fine |
| FlipsCache (capacity) | Kafka incremental | ~seconds | Required — overselling slots is unacceptable |
| WeatherBuffer, operational signals | MeghaCache | Near-real-time | Required — live business signals |

---

## 14. Ingestion Pipeline

One Maven WAR deployed 18 times. Each deployment starts with a different `runAs` flag that activates a different subset of ~40 Kafka listeners.

**Three main listener groups:**
- **Offer Listener** — consumes from PNO (our internal product number orchestrator) and UBER (offer management system). Writes to `mcse_offer`, `mcse_offer_node_eligibility` in Cassandra.
- **DCC Listener** — consumes distributor/carrier changes. Writes warehouse and carrier data.
- **FCAP/CCAP Listener** — consumes capacity pool changes from CASPR (capacity reservation system). Writes capacity flip data.

**Two consumer generations coexist:**

**V1 (classic):** Single poller thread → bounded BlockingQueue → fixed-size processor thread pool.
Back-pressure: when pool is saturated, queue fills, then poll blocks. Bounded queue prevents OOM under burst.

**V2 (reactive/RK):** N poller threads (RKConsumerSerial), each processes one batch at a time and only polls again after the batch completes. Processing time IS the back-pressure — no explicit queue. Separate M-thread executor per consumer for parallel record processing within a batch.

V2 for high-throughput (offers, items). V1 for lower-volume stable pipelines — migration risk wasn't worth it.

**Failure handling:**
- Failed records → `<topic>-retry` topic → separate consumer with backoff
- Dead-letter messages → drained by a scheduled error-batch deployment overnight
- Classic listeners never re-throw on bad records — they log, metric, commit-past. Re-throwing would block the partition permanently on a poison pill.

**Scale:** Hot pipelines run on 120–200 pods each. Millions of records per day.

---

## 15. Key Stories (30-second versions)

### Story A — CA Promise V5 Multi-Slot
> "I led the design of multi-slot delivery for Canada. The platform was built to return one delivery slot per item; Canada needed Express (same-day/next-day) and Standard (2–5 days) in the same response with separate prices. I redesigned the reservation generator to emit one inventory hold per slot, co-designed the response contract with the upstream gateway team, and shipped behind a feature flag. The hard part was backward compatibility — I kept the old single-slot fields populated so four upstream consumers wouldn't break, while adding a new slots[] array for the new shape. Trade-off: dual-shape response contract for two quarters. Greenfield: I'd version at the URL level."

### Story B — 100% CPU Debug
> "Canada Promise pods hit 100% CPU at peak. Thread dumps showed most threads in GC or waiting on a logging lock. Heap dump — top retainers: String objects and char arrays. Traced to log.debug() statements constructing full payload strings even with debug off — string concatenation evaluates before the logger drops the call. Lambdas holding references to entire response objects. Fix: wrapped debug calls with isDebugEnabled() guards. CPU dropped 100% → 30% within the rolling restart. Long-term: added a Sonar rule flagging string concatenation in debug calls. Found 40 more instances across the codebase."

### Story C — Ingestion Scale
> "I operate the ingestion tier — 18 Kafka-to-Cassandra pipelines from one JAR, deployed 18 times with different startup flags. Hot pipelines run on 200 pods each, millions of records per day. Two consumer generations co-exist — classic single-threaded for low-volume pipelines, reactive batched-and-fanned-out V2 for high-volume ones. I didn't force a migration because lower-volume pipelines didn't need it and the operational risk wasn't worth it."

---

## 16. How MCSE Ensures Orchestrator Completes on Time

> This is asked as: *"How do you ensure the orchestrator completes in time?"* / *"How long do you wait?"* / *"What happens if a solution doesn't complete in time?"* — know all three layers cold.

---

### Layer 1 — Per-route hard timeout: **1800ms** (Constants.java)

```java
int DEFAULT_ORCHESTRATOR_SERVICE_ROUTE_TIMEOUT = 1800; // from Constants.java
```

Every single orchestrator evaluation — one warehouse × one shipping method — has a **hard 1800ms timeout**. Not a soft hint. A racing timer.

---

### Layer 2 — HOW the timeout works: timeout racing (not blocking)

We do NOT use `future.get(1800, MILLISECONDS)` — that would block a thread for the full 1800ms just sitting and waiting. Instead, we use **timeout racing** via `applyToEither`:

```java
// From: CompletableDistributor.java
CompletableFuture.supplyAsync(realWork, orchestratorExecutorService)
    .applyToEither(failAfter(1800ms), Function.identity())
    .exceptionally(ex -> orchestratorRRUtil.generateRapidResponse(wrapper));
```

**How it works:**
```
Two futures race simultaneously:

Future A:  Real work → evaluate warehouse Dallas for this item
Future B:  Timer → after 1800ms, complete with TimeoutException

.applyToEither(A, B) → whichever finishes FIRST wins

Dallas responds in 400ms  → Future A wins → real result used ✅
Dallas is slow (2000ms)   → Future B fires at 1800ms → TimeoutException ⏰
                                  ↓
                          .exceptionally() catches it
                                  ↓
                          generateRapidResponse() → pre-computed fallback date returned
```

**Why not future.get(timeout)?**
> *"future.get(timeout) blocks the calling thread for the full 1800ms — that thread is wasted. With applyToEither, the calling thread is never blocked. It set up the pipeline and moved on. The timeout fires on a tiny 1-thread ScheduledExecutorService called `delayer`. No thread sits and waits."*

---

### Layer 3 — What happens to incomplete solutions: RapidResponse fallback

`.exceptionally()` catches **any** failure — TimeoutException, NPE, downstream error, RejectedExecutionException:

```java
.exceptionally(ex -> {
    log.error("Something went wrong : ", ex);
    return orchestratorRRUtil.generateRapidResponse(promiseDateRequestWrapper);
});
```

- That **one route** gets a pre-computed RapidResponse fallback date
- All **other 49–99 routes** continue running in parallel — completely unaffected
- Gather picks the best from whatever completed + RapidResponse fallbacks
- Customer always gets a date — never an error

---

### Layer 4 — Thread pool full: SynchronousQueue (fail-fast, no queuing)

```
Thread pool: 300 core / 800 max / SynchronousQueue (zero capacity queue)

All 800 threads busy → new task → IMMEDIATE RejectedExecutionException
                                         ↓
                               .exceptionally() catches it
                                         ↓
                               generateRapidResponse() in microseconds
```

> *"We chose SynchronousQueue deliberately — the code comment says 'fail first design'. At 700K RPM, queuing tasks when the pool is full makes things worse. By the time a queued task runs, the customer has already timed out. We'd rather fail fast and return a pre-computed date in microseconds."*

---

### Layer 5 — Outermost safety net: Bulwark

```java
// MpBulwarkImpl.java
protected PromiseDateResponse run() {
    return mpDateServiceManager.getDate();   // full evaluation
}
protected PromiseDateResponse fallBack() {
    return mcseRRManager.getDate();          // pre-computed RapidResponse
}
```

If the entire request — all routes — can't complete, Bulwark's `fallBack()` fires at the request level. The caller never sees an exception. They always get a `PromiseDateResponse`.

**Tighter budget for Item Page:**
- Default: `DEFAULT_RAPID_RESPONSE_DELAY = 1700ms`
- Item Page: `DEFAULT_ITEM_PAGE_RR_TIMEOUT = 800ms` — browsing, not buying, so stricter

---

### Summary table — all 5 safety layers

| Layer | Mechanism | Timeout / Trigger |
|---|---|---|
| Per-route timeout | `applyToEither(failAfter(1800ms))` | 1800ms hard |
| Route failure recovery | `.exceptionally()` → `generateRapidResponse()` | Immediate on any exception |
| Thread pool full | `SynchronousQueue` → immediate rejection | 0ms wait — reject instantly |
| Full request fallback | `Bulwark.fallBack()` → RapidResponse | 1700ms default |
| Item Page tighter budget | `DEFAULT_ITEM_PAGE_RR_TIMEOUT` | 800ms |

---

### One-liner summary (say this to wrap up)

> *"We race every evaluation against a 1800ms timer using CompletableFuture's applyToEither. Whoever loses gets a RapidResponse fallback. The thread pool uses SynchronousQueue — fail fast, never queue. Bulwark catches anything that slips through at the request level. Five layers of safety nets, zero threads blocked waiting, customer always gets a response."*

---

## 17. Cross-Questions — Ready Answers

### "How do you prevent one slow downstream from timing out the whole request?"

> *"Three mechanisms. First: per-dependency thread pool isolation — Wakanda has its own Hystrix thread pool, FCAP has its own, so a slow Wakanda can't starve FCAP threads. Second: per-call timeout via applyToEither + failAfter — if a specific warehouse evaluation takes more than 1800ms, the timeout future wins and we skip that candidate, returning RapidResponse for it. Third: Bulwark — if the entire MP date service can't complete within budget, fallBack() kicks in with a pre-computed RapidResponse, so the caller always gets a response."*

---

### "How do you handle a pod starting cold?"

> *"Three layers. Hollow: pre-warmed at startup — pod downloads and memory-maps the latest snapshot before accepting traffic. Readiness probe blocks traffic until Hollow is loaded. So first request sees same performance as millionth. Caffeine: lazy-fill — first request pays the fetch cost, subsequent hits are cached. MeghaCache: always ready, it's a remote service. Pod never enters load balancer with a cold Hollow cache."*

---

### "What happens if Cassandra is down?"

> *"MCSE's hot path reads from Hollow. Cassandra is only on the write path (ingestion) and the fallthrough path (Hollow miss). If Cassandra is down but Hollow has the data — which it does for 99% of reads — requests succeed fine with slightly stale data. If both Hollow misses AND Cassandra is unavailable, we return ERR0077. That's our failure contract: stale data over no data, but never incorrect data. Cassandra is multi-DC replicated — a single DC going dark doesn't bring it down."*

---

### "Walk me through how an offer change gets reflected."

> *"1. Seller updates offer. Their system publishes event to Kafka. 2. Our Offer Listener consumes it. Writes to mcse_offer and mcse_offer_node_eligibility in Cassandra. 3. Airflow-scheduled Spark job reads updated Cassandra, builds new Hollow snapshot, cache-generator publishes it, pods swap atomically. 4. End-to-end latency: minutes for batch-path caches. For capacity signals, the incremental Kafka path delivers in seconds. 5. Until the swap happens, requests see old snapshot — stale but not wrong."*

---

### "What happens if two events for the same offer arrive at the same time?"

> *"Cassandra uses last-write-wins by default. If two ingestion pods process two events simultaneously and both write, the one with the later timestamp wins — Cassandra guarantees a single converged state. More importantly: our ingestion listener always writes the full offer state, not a partial delta. So even if two writes race, the result is always a complete self-consistent offer record. Never partial-field corruption — worst case is the slightly-earlier state gets overwritten by the slightly-later one, which is correct behavior."*

---

### "How do you serve US, Mexico, Canada, Chile from one codebase?"

> *"Same JAR, different configuration. CCM (our internal runtime config system — think Spring Cloud Config, but company-built) trees control per-market behavior: which capacity pools to call, which fulfillment types are enabled, timeout values, thread pool sizes. Per-market KITT YAML files control pod count, region routing, resource limits. Code branches are minimal — most variation is configuration. Multi-tenant context threads through every request via a thread-local so downstream calls carry the right market identifier."*

---

### "What's the hardest failure mode?"

> *"Not an infrastructure outage — a bad CCM config push. CCM controls everything: feature flags, capacity pool routing, thread pool sizes, timeouts. A bad config push can affect all markets simultaneously because there's one config service. Pod crash is isolated; config corruption is global. We mitigate with per-market config namespaces, rollout gates, and diff reviews before push.*

> *Second hardest: Kafka consumer lag growing undetected. If ingestion falls behind, Hollow caches serve increasingly stale data without anyone knowing — no errors surface, just wrong dates. We page on consumer lag >5 minutes for hot pipelines."*

---

### "How does MCSE handle 3P marketplace sellers differently from 1P?"

> *"1P items ship from our company's own warehouses — MCSE knows which ones from FCAP/CCAP data. 3P sellers ship from their own locations — MCSE gets the seller-to-node mapping from a fulfillment template (fetched via LIMO, our eligibility service) that maps seller → template → eligible nodes. The orchestrator evaluates those seller-owned nodes the same way it evaluates our company's warehouses. WFS (our company's Fulfillment Services) is hybrid — seller's inventory but our company's warehouse, so it goes through FCAP like 1P. All three tenants — 1P, 3P, WFS — run through the same orchestrator; tenancy threads through a context object called SourcingContext."*

---

## 17. Failure Scenarios — Know These Cold

| Scenario | Symptom | Root Cause | Fix |
|---|---|---|---|
| New offer not transactable | Item shows unavailable after activation | Hollow not refreshed yet (batch delay) | Wait for next rebuild, or manual replay via ingestion |
| Deleted offer still showing | Deactivated item still appears | Hollow has not rebuilt since delete | Wait for next rebuild (~10 min); for FlipsCache, verify tombstone was published to Kafka |
| Wrong delivery date | Date older than expected | Kafka ingestion lag → Cassandra stale → Hollow serving old data | Check consumer lag in Grafana; investigate pipeline health |
| ERR0077 errors | Requests failing with reference data error | Hollow miss + Cassandra unavailable simultaneously | Check Cassandra cluster health; check snapshot announcer is running |
| Capacity flips not propagating | Slots show available when they're not | FlipsInjector consumer lag | Check FlipsInjector consumer group lag |
| Thread pool exhaustion | 100% thread pool utilization, RapidResponse spike | Downstream latency spike → all 800 threads busy → SynchronousQueue rejects → RR | Identify which downstream is slow; circuit breaker should have opened |
| 100% CPU / GC death spiral | Latency spike + CPU saturation | Heap pressure — often string construction in debug logging, large object retention | Thread dump → heap dump → find top retainers → identify hot-path allocations |
| Bad CCM config push | All-market latency spike or errors | Wrong timeout / thread pool config pushed to all pods | CCM rollback to previous config snapshot; canary deployments should have caught it |

---

## 18. Terminology Cheat Sheet

| Internal name | What it actually is |
|---|---|
| FlipsCache | In-memory cache of capacity flip state (which warehouse slots are open/closed) |
| FlipsInjector | In-pod Kafka consumer that applies capacity flip delta events to Hollow snapshot |
| TNT | Transit-Time cache (warehouse × carrier × zip → delivery days) |
| CCM | Runtime config system (our company's internal equivalent of Spring Cloud Config) |
| KITT | Kubernetes deployment manifest YAML (company-internal tooling) |
| LIMO | Offer eligibility service |
| Wakanda | Inventory service |
| FCAP / CCAP | Fulfillment center / distribution center capacity reservation engines |
| CASPR | Capacity reservation system (publishes capacity events to Kafka) |
| PNO | Product Number Orchestrator (publishes offer events to Kafka) |
| UBER | Offer management system |
| Hollow | Netflix's in-memory snapshot framework (memory-mapped, immutable per refresh) |
| Caffeine | JVM-local in-process cache (Guava successor, TTL-based) |
| MeghaCache | Our company's internal distributed cross-pod cache |
| Bulwark | Resilience4j wrapper: run() = happy path, fallBack() = RapidResponse |
| RapidResponse | Pre-computed fallback delivery date served when full evaluation can't complete |
| MOF | Multi-Objective Function — final ranking logic to pick best warehouse + date + cost |
| WFS | Our company's Fulfillment Services (seller's inventory, company's warehouse) |
| SDD | Same-Day Delivery |
| SFS | Ship From Store |
| BOPIS | Buy Online, Pick Up In Store |
| S2H | Ship to Home |
| SLA | Service Level Agreement (here: timeout budget per fulfillment type) |

---

*Last updated: June 25, 2026 — full rewrite from actual MCSE source code*
*Real class names: CompletableDistributor, CompletableGather, CompletableFutureOrchestratorService, MpBulwarkImpl, MpDataGeneratorWrapper, AvailabilityHystrixServiceCommand, OrchestratorExecutorConfig*
*Real numbers: 300 core threads / 800 max / SynchronousQueue / 1800ms timeout / 1700ms RR delay / 800ms item page RR*
