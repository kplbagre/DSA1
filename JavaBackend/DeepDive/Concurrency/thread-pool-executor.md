# 🧵 ThreadPoolExecutor — Deep Dive

> After this note you can explain the 7 constructor parameters of `ThreadPoolExecutor`, why `Executors.newFixedThreadPool()` uses an unbounded queue (and why that causes OOM), name 4 rejection policies, and design a production thread pool with bounded queue + caller-runs backpressure.

---

## 🎯 The Problem This Solves

Creating a new OS thread per task is expensive: ~1 MB stack allocation, OS-level thread registration, scheduler overhead. At 10,000 tasks/second, you'd create 10,000 threads — each consuming 1 MB of stack = 10 GB just for stacks. The OS scheduler thrashes trying to context-switch between them. Thread pools solve this by reusing a fixed number of threads across many tasks — amortizing the creation cost and bounding resource usage.

But the JDK's `Executors` factory methods hide critical configuration behind convenience. Understanding the raw `ThreadPoolExecutor` constructor — its 7 parameters and their interactions — is what separates a developer who uses thread pools from one who sizes them correctly for production.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Core pool size** | The number of threads kept alive even when idle. These threads are never terminated (unless `allowCoreThreadTimeOut` is set). |
| **Maximum pool size** | The upper bound on threads. New threads beyond core are created ONLY when the work queue is full. |
| **Keep-alive time** | How long idle threads above core pool size wait for new tasks before being terminated. |
| **Work queue** | A `BlockingQueue<Runnable>` that holds tasks waiting to be executed. The queue type (bounded vs unbounded) critically affects behavior. |
| **Rejection policy** | What happens when a new task is submitted but both the queue is full AND the maximum number of threads are busy. 4 built-in policies. |
| **Thread factory** | Creates new threads. Customize to set thread names (critical for debugging — "order-processor-3" vs "pool-2-thread-17"), daemon status, priority, and uncaught exception handlers. |

---

## 🧠 Mental Model

Think of a thread pool as a **restaurant kitchen**. Core pool size = permanent staff (always working). Maximum pool size = permanent + temporary staff (hired during rush). Work queue = the order ticket rail (queued orders waiting for a free cook). Keep-alive time = how long a temp waits for work before going home. Rejection policy = what happens when the ticket rail is full AND all cooks (permanent + temp) are busy — refuse the order? Make the waiter cook it themselves? Silently drop it?

The critical insight: **new threads beyond core are only created when the queue is FULL, not when the queue is non-empty.** If the queue is unbounded (infinite capacity), it never fills → max pool size is never reached → you effectively have only core threads + an infinitely growing queue → OOM.

> If you can say "core threads are permanent; max threads are created ONLY when the queue is full; unbounded queue = max is never reached = OOM risk; always use bounded queue + rejection policy in production" without notes, you have ThreadPoolExecutor.

---

## 🎨 Visual — Task Submission Flow

```
  Task submitted via execute()/submit()
       │
       ▼
  ┌─────────────────────────────────────────────────────┐
  │  Active threads < corePoolSize?                     │
  │    YES → create new core thread, run task            │
  │    NO  ↓                                            │
  ├─────────────────────────────────────────────────────┤
  │  Work queue has capacity?                           │
  │    YES → enqueue task (thread picks it up later)     │
  │    NO  ↓                                            │
  ├─────────────────────────────────────────────────────┤
  │  Active threads < maximumPoolSize?                  │
  │    YES → create new thread ABOVE core, run task      │
  │    NO  ↓                                            │
  ├─────────────────────────────────────────────────────┤
  │  REJECTED — invoke RejectedExecutionHandler          │
  │    AbortPolicy:       throw RejectedExecutionException│
  │    CallerRunsPolicy:  caller thread runs the task     │
  │    DiscardPolicy:     silently drop the task          │
  │    DiscardOldestPolicy: drop oldest queued, retry     │
  └─────────────────────────────────────────────────────┘

  KEY: the queue is checked BEFORE creating new threads above core.
  This means: unbounded queue → never full → max pool size is NEVER used.

KEY INVARIANT:
   New threads above corePoolSize are created ONLY when the queue
   is full. With an unbounded queue, this never happens — you have
   corePoolSize threads + an infinitely growing queue.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Executors.newFixedThreadPool — the convenience trap
ExecutorService pool = Executors.newFixedThreadPool(10);
// What it actually creates:
// new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
//     new LinkedBlockingQueue<Runnable>())   ← UNBOUNDED queue!

// At 10,000 tasks/second, if each task takes 100ms:
// 10 threads process 100 tasks/second
// 9,900 tasks/second accumulate in the queue
// After 1 hour: 35.6 million tasks queued → ~2GB+ heap for Runnable objects → OOM

// ❌ No rejection policy — tasks queue forever
// ❌ No thread naming — debugging shows "pool-1-thread-3" → meaningless
// ❌ No bounded queue — memory grows without limit
```

```java
// Executors.newCachedThreadPool — the thread explosion trap
ExecutorService pool = Executors.newCachedThreadPool();
// What it actually creates:
// new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
//     new SynchronousQueue<Runnable>())

// SynchronousQueue: zero capacity — every task creates a new thread if no idle one available
// At 10,000 tasks/second with 100ms tasks → 1,000 concurrent threads
// At 50,000 tasks/second → 5,000 threads → OS thread limit → crash or severe thrashing
```

---

### Level 2 — The real mechanism

#### 2.1 — The 7 constructor parameters

```java
// Tier 2 — Production: ThreadPoolExecutor with all 7 parameters
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    4,                                    // 1. corePoolSize: keep 4 threads alive always
    8,                                    // 2. maximumPoolSize: burst up to 8 threads
    60L,                                  // 3. keepAliveTime: idle burst threads die after 60s
    TimeUnit.SECONDS,                     // 4. unit for keepAliveTime
    new ArrayBlockingQueue<>(100),        // 5. workQueue: bounded queue — 100 task capacity
    new ThreadFactory() {                 // 6. threadFactory: custom thread names
        private final AtomicInteger count = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "order-processor-" + count.getAndIncrement());
            t.setDaemon(false);           // non-daemon — JVM waits for these to finish
            t.setUncaughtExceptionHandler((thread, ex) ->
                log.error("Uncaught in {}", thread.getName(), ex));
            return t;
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy()  // 7. rejectionPolicy: natural backpressure
);
```

**Parameter interaction — the flow:**

| Scenario | Active threads | Queue state | Action |
|---|---|---|---|
| Startup, first 4 tasks | 0 → 4 | empty | Create core threads |
| Tasks 5–104 | 4 (all busy) | filling 1→100 | Enqueue in bounded queue |
| Task 105 (queue full) | 4 → 5 | full (100) | Create thread #5 (above core) |
| Tasks 105–108 (queue still full) | 5 → 8 | full (100) | Create threads up to max (8) |
| Task 109 (queue full + 8 threads busy) | 8 | full (100) | **REJECTED** → CallerRunsPolicy |
| Load decreases | 8 → 4 (after 60s idle) | draining | Idle burst threads die after keepAliveTime |

#### 2.2 — The 4 rejection policies

```java
// 1. AbortPolicy (default) — throw RejectedExecutionException
//    The caller's submit() throws — if not caught, task is lost AND caller's thread may crash
new ThreadPoolExecutor.AbortPolicy();

// 2. CallerRunsPolicy — the submitting thread runs the task itself
//    Natural backpressure: if the pool is overwhelmed, the caller slows down
//    because it's busy running the task instead of submitting more
new ThreadPoolExecutor.CallerRunsPolicy();
// ⭐ RECOMMENDED for most production use cases

// 3. DiscardPolicy — silently drop the task. No exception. No log. Task gone.
//    Almost NEVER correct — data loss without any indication
new ThreadPoolExecutor.DiscardPolicy();

// 4. DiscardOldestPolicy — drop the oldest queued task, then retry submit
//    Useful for "latest-value-wins" scenarios (sensor data, stock tickers)
new ThreadPoolExecutor.DiscardOldestPolicy();

// 5. Custom policy — implement RejectedExecutionHandler
new RejectedExecutionHandler() {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.error("Task rejected: pool={}, queue={}/{}, active={}",
            executor.getPoolSize(), executor.getQueue().size(),
            ((ArrayBlockingQueue<?>) executor.getQueue()).remainingCapacity(),
            executor.getActiveCount());
        // Option: write to dead-letter queue, alert, or block
    }
};
```

#### 2.3 — Shutdown semantics

```java
// shutdown(): graceful — stop accepting new tasks, let running + queued tasks finish
executor.shutdown();
boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
if (!terminated) {
    // Tasks still running after 30s — force shutdown
    List<Runnable> neverRan = executor.shutdownNow();
    // shutdownNow(): interrupt running threads + return queued tasks that never started
    log.warn("{} tasks never executed", neverRan.size());
}

// ⚠️ In Spring Boot: use @PreDestroy or implement DisposableBean
// to shut down custom executors gracefully on app shutdown
@PreDestroy
public void cleanup() {
    executor.shutdown();
    try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

#### 2.4 — Sizing the pool

```java
// CPU-bound tasks (computation, no I/O blocking):
int cpuCores = Runtime.getRuntime().availableProcessors();
int poolSize = cpuCores;   // or cpuCores + 1 (absorbs context switches)
// More threads than cores = context-switching overhead > parallelism benefit

// I/O-bound tasks (DB queries, HTTP calls, file reads):
// Threads spend most of their time WAITING, not computing
// Formula: poolSize = cpuCores × (1 + waitTime / computeTime)
// Example: 8 cores, task waits 200ms on I/O, computes 10ms:
//   poolSize = 8 × (1 + 200/10) = 8 × 21 = 168 threads
// In practice: start with 2× to 4× cores, benchmark, adjust

// Mixed: separate pools for CPU-bound and I/O-bound tasks
// Never mix blocking I/O tasks with CPU tasks in the same pool
// The I/O tasks tie up threads that CPU tasks need
```

---

### Level 3 — The subtleties

#### 3.1 — `submit()` vs `execute()`

```java
// execute(Runnable): fire-and-forget. Exceptions propagate to the thread's
// UncaughtExceptionHandler (default: printed to stderr, thread dies).
executor.execute(() -> {
    throw new RuntimeException("boom");
    // Exception goes to UncaughtExceptionHandler
    // Thread dies and is replaced by a new one
});

// submit(Callable/Runnable): returns Future. Exception is CAPTURED in the Future.
// If nobody calls future.get(), the exception is SILENTLY SWALLOWED.
Future<?> future = executor.submit(() -> {
    throw new RuntimeException("boom");
    // Exception captured in Future — NOT printed, NOT logged, NOT thrown
});
future.get();   // NOW throws ExecutionException wrapping RuntimeException
// If you never call get() — the exception vanishes. Zero indication of failure.
```

**Rule:** if you use `submit()`, ALWAYS inspect the `Future`. Otherwise use `execute()` with a proper `UncaughtExceptionHandler` on the thread factory.

#### 3.2 — ScheduledThreadPoolExecutor

```java
// Schedule tasks for delayed or periodic execution
ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2);

// Run once after 5 seconds:
scheduler.schedule(() -> cleanup(), 5, TimeUnit.SECONDS);

// Run every 10 seconds (fixed-rate — next run starts at fixed intervals):
scheduler.scheduleAtFixedRate(() -> pollMetrics(), 0, 10, TimeUnit.SECONDS);
// If execution takes 12s: next run starts immediately (already overdue)
// Runs can overlap if pool has >1 thread

// Run 10 seconds after EACH execution completes (fixed-delay):
scheduler.scheduleWithFixedDelay(() -> pollMetrics(), 0, 10, TimeUnit.SECONDS);
// If execution takes 12s: next run starts 10s after completion = 22s interval
// Runs never overlap

// ⚠️ If the task throws an exception, ALL future executions are silently cancelled.
// ALWAYS wrap scheduled tasks in try-catch:
scheduler.scheduleAtFixedRate(() -> {
    try {
        pollMetrics();
    } catch (Exception e) {
        log.error("Scheduled task failed", e);   // log but don't rethrow — keeps schedule alive
    }
}, 0, 10, TimeUnit.SECONDS);
```

#### 3.3 — Virtual threads (Java 21) vs ThreadPoolExecutor

```java
// Java 21: for I/O-bound tasks, virtual threads eliminate pool sizing entirely
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // Creates one virtual thread per task — no pool, no queue, no sizing
    // 100,000 concurrent tasks → 100,000 virtual threads on ~8 carrier OS threads
    for (Request req : requests) {
        executor.submit(() -> processRequest(req));
    }
}
// When to still use ThreadPoolExecutor:
// - CPU-bound tasks (virtual threads don't help — still need core-count threads)
// - Need bounded concurrency (virtual threads are unbounded — may overwhelm downstream)
// - Legacy Java < 21
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "`Executors.newFixedThreadPool(N)` is production-ready" | It uses an UNBOUNDED `LinkedBlockingQueue`. Under sustained load, the queue grows without limit → OOM. Always construct `ThreadPoolExecutor` directly with a bounded queue. |
| "Increasing thread count always increases throughput" | For CPU-bound tasks, threads beyond CPU core count increase context-switching overhead. For I/O-bound tasks, too many threads exhaust connection pools, file descriptors, or downstream service capacity. Pool size must match the workload. |
| "New threads are created when the queue is non-empty" | New threads above core are created ONLY when the queue is FULL. If the queue has capacity, the task is enqueued. With an unbounded queue, the queue is never full → max pool size is meaningless. |
| "`submit()` exceptions will show up in logs" | `submit()` captures exceptions in the `Future`. If nobody calls `future.get()`, the exception is silently lost. Use `execute()` for fire-and-forget tasks, or always inspect the Future. |
| "CallerRunsPolicy slows down the caller" | That's not a bug — it's the feature. When the pool is overwhelmed, CallerRunsPolicy makes the submitting thread process the task itself, naturally slowing down submission rate. This is self-regulating backpressure — the system adapts to capacity. |

---

## 🐞 Production Footguns

---

> **Footgun: Unbounded queue OOM**
> **Cost:** OutOfMemoryError — service crash
>
> A Kafka consumer service used `Executors.newFixedThreadPool(10)` to process messages. At 5,000 msg/sec with 200ms processing time per message, 10 threads processed 50 msg/sec. 4,950 messages/second accumulated in the unbounded LinkedBlockingQueue. After 1 hour: ~18 million Runnable objects queued → OOM → service killed by the orchestrator → message lag spiked → downstream cascade failure.

```java
// ❌ The trap
ExecutorService pool = Executors.newFixedThreadPool(10);   // unbounded queue

// ✅ The fix: bounded queue + rejection policy + monitoring
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    10, 20,
    60L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(500),      // bounded: max 500 queued
    namedThreadFactory("kafka-processor"),
    new CallerRunsPolicy()              // backpressure: caller slows down
);
// Monitor: pool.getQueue().size(), pool.getActiveCount(), pool.getCompletedTaskCount()
```

---

> **Footgun: Swallowed exception in submit()**
> **Cost:** Silent failure — task fails but nobody knows
>
> A notification service submitted email tasks via `executor.submit()`. The email library threw `MailSendException` for invalid addresses. Since nobody called `future.get()`, the exceptions vanished. 15% of emails were silently failing for weeks — discovered only when customers complained.

```java
// ❌ The trap: submit() without inspecting Future
executor.submit(() -> emailService.send(user.email(), body));
// MailSendException thrown inside → captured in Future → nobody calls get() → GONE

// ✅ Fix 1: use execute() with UncaughtExceptionHandler
executor.execute(() -> {
    try {
        emailService.send(user.email(), body);
    } catch (Exception e) {
        log.error("Email send failed: {}", user.email(), e);
        alertService.notify("email-failure", e);
    }
});

// ✅ Fix 2: use CompletableFuture with exceptionally()
CompletableFuture.runAsync(
    () -> emailService.send(user.email(), body), executor
).exceptionally(ex -> {
    log.error("Email failed: {}", user.email(), ex);
    return null;
});
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `concurrent-collections.md` | ThreadPoolExecutor uses `BlockingQueue<Runnable>` internally. The queue type (ArrayBlockingQueue bounded, LinkedBlockingQueue unbounded, SynchronousQueue handoff) determines pool behavior and OOM risk. |
| `synchronized-volatile.md` | ThreadPoolExecutor uses `ReentrantLock` internally for worker thread management. The work queue uses lock-based or CAS-based synchronization depending on the implementation. |
| `completable-future.md` | `CompletableFuture.supplyAsync(supplier, executor)` submits tasks to a ThreadPoolExecutor. The default executor is `ForkJoinPool.commonPool()` — understanding pool design explains why a custom executor is needed for I/O. |
| `virtual-threads-java21.md` (planned — Note #22) | Virtual threads (Java 21) replace ThreadPoolExecutor for I/O-bound workloads. Understanding pool sizing explains WHY virtual threads were created — eliminating the pool-sizing puzzle for blocking I/O. |
| `fork-join-parallel-streams.md` (planned — Note #21) | `ForkJoinPool` is a specialized ThreadPoolExecutor for divide-and-conquer work with work-stealing. Parallel streams use `ForkJoinPool.commonPool()`. |

---

## 🎙️ Interview Deep Questions

**Q1. Explain the 7 parameters of ThreadPoolExecutor. What happens when a task is submitted?**

> The 7 parameters: corePoolSize (permanent threads), maximumPoolSize (burst limit), keepAliveTime + unit (idle timeout for burst threads), workQueue (task buffer), threadFactory (names + daemon status), rejectedExecutionHandler (overflow policy). When a task is submitted: if active threads < core → create core thread. If core is full → enqueue in workQueue. If queue is full → create thread up to max. If max is reached AND queue is full → invoke rejection handler. Critical: new threads beyond core are only created when the queue is FULL. With an unbounded queue, the max is never reached.

**Q2. Why is `Executors.newFixedThreadPool()` dangerous in production?**

> It uses an unbounded `LinkedBlockingQueue` — capacity is `Integer.MAX_VALUE`. If tasks arrive faster than threads can process them, the queue grows without limit, consuming heap memory. No rejection policy triggers because the queue never fills. Eventually: OutOfMemoryError. The fix: construct `ThreadPoolExecutor` directly with `ArrayBlockingQueue(capacity)` (bounded) and a `CallerRunsPolicy` (natural backpressure — submitting thread runs the task when pool is overwhelmed).

**Q3. What is CallerRunsPolicy and why is it recommended?**

> When the pool's queue is full and all threads are busy, CallerRunsPolicy makes the submitting thread (the "caller") run the rejected task itself. This creates natural backpressure: the caller is busy running the task instead of submitting more tasks → submission rate automatically drops to match processing capacity. It's self-regulating — the system adapts without data loss. `AbortPolicy` (default) throws an exception — the task is lost unless explicitly retried. `DiscardPolicy` silently drops the task — data loss without indication. CallerRunsPolicy is the safe default for most production systems.

**Q4. How do you size a thread pool for CPU-bound vs I/O-bound tasks?**

> CPU-bound: set pool size to `Runtime.getRuntime().availableProcessors()` or +1. More threads than cores causes context-switching overhead with no benefit — the CPU is already fully utilized. I/O-bound: threads spend most of their time waiting (DB, network). Formula: `cores × (1 + waitTime/computeTime)`. If tasks wait 200ms and compute 10ms on 8 cores: 8 × 21 = 168 threads. In practice: start with 2-4× cores, benchmark, adjust. NEVER mix CPU and I/O tasks in the same pool — the I/O tasks tie up threads the CPU tasks need. Use separate pools.

**Q5. What happens to exceptions in `submit()` vs `execute()`?**

> `execute(Runnable)`: the exception propagates to the thread's `UncaughtExceptionHandler`. The thread dies and is replaced. The exception is visible (logged by the handler). `submit(Callable/Runnable)`: the exception is captured inside the returned `Future`. Calling `future.get()` throws `ExecutionException` wrapping the original. If nobody calls `get()`, the exception is silently swallowed — no log, no alert, no indication. This is a common production trap: fire-and-forget with `submit()` = silent failure. Fix: use `execute()` for fire-and-forget, or always handle the Future (e.g., `CompletableFuture.runAsync().exceptionally()`).

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** ThreadPoolExecutor reuses a fixed pool of threads across many tasks. 7 parameters control its behavior: core size, max size, keepalive, queue, thread factory, and rejection policy.
>
> **Part 2 — How/Why (30s):** Tasks go through 3 stages: create core thread (if under core count), enqueue in work queue (if core full), create burst thread (if queue full). Critically: new threads above core are only created when the queue is FULL. `Executors.newFixedThreadPool()` uses an unbounded queue — max is never reached, tasks pile up, OOM. Production pattern: `new ThreadPoolExecutor(core, max, keepalive, new ArrayBlockingQueue<>(capacity), namedFactory, CallerRunsPolicy)`. CallerRunsPolicy provides natural backpressure — the submitting thread processes the task when the pool is overwhelmed.
>
> **Part 3 — Gotcha (20s):** Two traps: unbounded queue (`Executors.newFixedThreadPool`) — tasks queue forever → OOM. And `submit()` silently swallows exceptions — the Future captures them, but if nobody calls `get()`, the exception vanishes. Use `execute()` for fire-and-forget, or wrap in `CompletableFuture.runAsync().exceptionally()`.

---

## 🧾 TL;DR

- **7 params:** core, max, keepalive, queue, threadFactory, rejectionPolicy, + time unit.
- **New threads above core created ONLY when queue is FULL.** Unbounded queue = max is meaningless.
- **`Executors.newFixedThreadPool()`** = unbounded queue → OOM. Don't use in production.
- **Production pattern:** `ArrayBlockingQueue(capacity)` + `CallerRunsPolicy` + named thread factory.
- **`submit()` captures exceptions in Future** — silent swallowing if get() never called. Use `execute()` for fire-and-forget.
- **CPU-bound:** pool size ≈ CPU cores. **I/O-bound:** pool size = cores × (1 + wait/compute).
- **`shutdown()` = graceful** (finish running + queued). **`shutdownNow()` = aggressive** (interrupt + return queued).
- **Java 21 virtual threads** replace pools for I/O-bound work — no sizing needed.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #19 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: 7 ThreadPoolExecutor parameters with interaction flow (ASCII visual), task submission 3-stage flow, Executors factory traps (newFixedThreadPool unbounded queue, newCachedThreadPool thread explosion), 4 rejection policies + custom handler, shutdown vs shutdownNow semantics, submit vs execute exception behavior, pool sizing formulas (CPU-bound vs I/O-bound), ScheduledThreadPoolExecutor (fixedRate vs fixedDelay, exception cancellation trap), virtual threads comparison (Java 21). Two production footguns: unbounded queue OOM, silent exception in submit(). |
