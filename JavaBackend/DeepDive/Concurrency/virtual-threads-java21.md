# 🧵 Virtual Threads (Java 21) — Deep Dive

> After this note you can explain the carrier/virtual thread relationship, why `synchronized` pins virtual threads but `ReentrantLock` doesn't, when virtual threads are wrong (CPU-bound), and how to adopt them in Spring Boot 3.2+.

---

## 🎯 The Problem This Solves

A typical web server handles each request on a dedicated thread. Platform threads (OS-managed) cost ~1 MB of stack each. A server with 200 threads = 200 MB just for stacks. At 10,000 concurrent requests, you'd need 10,000 threads = 10 GB of stacks + massive OS scheduler overhead. The traditional solution — thread pooling — requires careful sizing: too few threads = low throughput on I/O-bound workloads (threads idle waiting for DB/HTTP responses), too many = memory exhaustion.

Virtual threads (Project Loom, Java 21) decouple the thread abstraction from the OS thread. A virtual thread is a lightweight object managed by the JVM — not the OS. When it blocks on I/O, it's **unmounted** from the carrier OS thread, which immediately picks up another virtual thread. One OS thread can serve thousands of virtual threads. No thread pool sizing. No stack-per-connection memory limit.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Platform thread** | A traditional Java thread backed by a 1:1 OS thread. ~1 MB stack. Managed by the OS scheduler. Limited by OS thread limits (typically ~10K–30K). |
| **Virtual thread** | A lightweight thread managed by the JVM. ~1 KB initial stack (grows on demand). Not mapped to an OS thread permanently — mounted/unmounted dynamically. Millions can exist simultaneously. |
| **Carrier thread** | The platform (OS) thread that a virtual thread is currently running on. When the virtual thread blocks, it's unmounted from the carrier, and the carrier picks up another virtual thread. |
| **Continuation** | The execution state of a virtual thread (stack frames, local variables, program counter). When a virtual thread blocks, its continuation is saved to the heap. When rescheduled, the continuation is restored. |
| **Mounting / unmounting** | Mounting: a virtual thread's continuation is loaded onto a carrier thread — it starts executing. Unmounting: the continuation is saved to the heap, freeing the carrier for other virtual threads. |
| **Pinning** | When a virtual thread is blocked but CANNOT unmount from its carrier — the carrier thread is tied up. Caused by `synchronized` blocks and native (JNI) calls. |
| **Structured concurrency (preview)** | A programming model (Java 21 preview) where the lifetime of concurrent tasks is bounded by a scope — if the scope exits (success or failure), all child tasks are cancelled. Prevents thread leaks. |

---

## 🧠 Mental Model

Platform threads are **taxis** — each taxi has a dedicated driver (OS thread). When the passenger (task) wants to wait at a restaurant (I/O), the taxi driver waits too — tied up doing nothing.

Virtual threads are **ride-share cars** — when the passenger goes into the restaurant, the car drives away to pick up another passenger (unmounting). When the first passenger comes out, any available car picks them up (mounting). The same small fleet of cars serves thousands of passengers because most passengers spend most of their time inside restaurants (I/O-bound).

> If you can say "virtual threads are JVM-managed, unmount from carrier OS threads during I/O, millions can coexist, `synchronized` pins (use ReentrantLock instead), don't pool them (create per-task), don't use for CPU-bound work" without notes, you have virtual threads.

---

## 🎨 Visual — Platform vs Virtual Thread Model

```
  PLATFORM THREADS (thread-per-request):
  ┌────────────────────────────────────────────────────────┐
  │  OS Thread 1 ─── Request A ─── [blocked on DB 300ms]  │
  │  OS Thread 2 ─── Request B ─── [blocked on HTTP 200ms]│
  │  OS Thread 3 ─── Request C ─── [computing 10ms]       │
  │  OS Thread 4 ─── Request D ─── [blocked on DB 300ms]  │
  │  ... (200 threads, each ~1 MB stack = 200 MB)         │
  │  Request 201 → REJECTED (pool full, queue full)       │
  └────────────────────────────────────────────────────────┘
  4 OS threads, but 3 are IDLE (blocked on I/O).
  Effective utilization: 25%.

  VIRTUAL THREADS (thread-per-request, JVM-managed):
  ┌────────────────────────────────────────────────────────┐
  │  Carrier 1 ─── VT-A [computing] → VT-A blocks on DB → │
  │               unmount VT-A → mount VT-E [computing]    │
  │  Carrier 2 ─── VT-B [computing] → VT-B blocks on HTTP→│
  │               unmount VT-B → mount VT-F [computing]    │
  │  Carrier 3 ─── VT-C [computing] → done → mount VT-G   │
  │  Carrier 4 ─── VT-D [computing] → VT-D blocks on DB → │
  │               unmount VT-D → mount VT-H [computing]    │
  │                                                        │
  │  10,000 virtual threads, 4 carrier OS threads.         │
  │  When VT blocks: unmounts, carrier picks up next VT.   │
  │  No thread pool sizing. No stack memory problem.       │
  └────────────────────────────────────────────────────────┘
  4 OS threads are ALWAYS computing — never idle.
  Effective utilization: ~100%.

KEY INVARIANT:
   Virtual threads unmount from carriers on blocking I/O.
   The carrier immediately serves another virtual thread.
   N carrier threads serve M virtual threads (M >> N).
   Carrier pool ≈ availableProcessors() (auto-managed by JVM).
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// 10,000 concurrent HTTP requests with platform thread pool:
ExecutorService pool = Executors.newFixedThreadPool(200);   // 200 platform threads

for (int i = 0; i < 10_000; i++) {
    pool.submit(() -> {
        String response = httpClient.send(request, BodyHandlers.ofString()).body();
        process(response);
    });
}
// 200 threads × 1 MB = 200 MB stacks
// Each HTTP call blocks ~200ms → 200 threads process ~1000 req/sec
// 9000 tasks sit in the unbounded queue → OOM risk
// Want higher throughput? 2000 threads → 2 GB stacks + OS scheduler thrash
```

---

### Level 2 — The real mechanism

#### 2.1 — Creating virtual threads

```java
// Option 1: newVirtualThreadPerTaskExecutor (recommended for most cases)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10_000; i++) {
        executor.submit(() -> {
            String response = httpClient.send(request, BodyHandlers.ofString()).body();
            process(response);
        });
    }
}   // executor.close() waits for all tasks to complete (AutoCloseable)
// 10,000 virtual threads. ~4 carrier OS threads.
// Each blocked VT unmounts → carrier serves another → ~10,000 req/sec

// Option 2: Thread.startVirtualThread (low-level)
Thread vt = Thread.startVirtualThread(() -> {
    System.out.println("Running on: " + Thread.currentThread());
});
vt.join();   // wait for completion

// Option 3: Thread.ofVirtual() builder (naming, daemon, etc.)
Thread vt2 = Thread.ofVirtual()
    .name("order-processor-", 0)   // name prefix + counter: order-processor-0, -1, ...
    .start(() -> processOrder(orderId));
```

#### 2.2 — How unmounting works

```java
// When a virtual thread calls a BLOCKING operation:
// - Thread.sleep()
// - Socket read/write (HTTP, DB)
// - BlockingQueue.take()
// - Lock.lock() (ReentrantLock)
// - LockSupport.park()

// The JVM:
// 1. Saves the virtual thread's continuation (stack frames) to the heap
// 2. Unmounts the virtual thread from the carrier
// 3. The carrier is now free — picks up the next runnable virtual thread
// 4. When the I/O completes, the virtual thread is rescheduled
// 5. Any available carrier mounts the virtual thread and resumes execution

// This all happens transparently — your blocking code looks the same.
// You write: response = httpClient.send(request, handler).body();
// Under the hood: virtual thread unmounts during send(), carrier serves others,
// virtual thread remounts when response arrives.
```

> **What the JVM is actually doing:** Continuations are stored as heap objects. The virtual thread's stack frames are copied to/from the carrier's stack during mount/unmount. The carrier thread pool is a `ForkJoinPool` with parallelism = `availableProcessors()` (auto-sized). Scheduling is cooperative — the JVM yields the carrier at blocking points (park, sleep, I/O). The virtual thread is NOT preempted during CPU computation — a CPU-bound virtual thread occupies its carrier until it reaches a blocking point.

#### 2.3 — Pinning: when unmounting fails

```java
// PINNING occurs when a virtual thread blocks inside:
// 1. A synchronized block/method
// 2. A native (JNI) method call

// When pinned: the virtual thread CANNOT unmount.
// The carrier OS thread is blocked — just like a platform thread.
// Other virtual threads waiting for that carrier are delayed.

// Detect pinning:
// -Djdk.tracePinnedThreads=full   (JVM flag — logs pinning events)
// Or: JFR event: jdk.VirtualThreadPinned

// ❌ synchronized pins:
synchronized (lock) {
    db.query("SELECT ...");   // virtual thread blocks on I/O
    // PINNED — carrier is stuck. Other VTs waiting.
}

// ✅ ReentrantLock does NOT pin:
reentrantLock.lock();
try {
    db.query("SELECT ...");   // virtual thread unmounts during I/O
    // Carrier serves other VTs while this one waits for DB response.
} finally {
    reentrantLock.unlock();
}
```

**Why `synchronized` pins:** The JVM's monitor implementation stores state in the carrier thread's native stack frame. Unmounting would require saving/restoring this native state — not implemented (as of Java 21). `ReentrantLock` uses AQS (pure Java, heap-based state) — no native stack dependency → unmounting works.

#### 2.4 — Spring Boot integration

```yaml
# application.yml — Spring Boot 3.2+
spring:
  threads:
    virtual:
      enabled: true

# This one property:
# - Configures Tomcat to use virtual threads for request handling
# - Each incoming HTTP request gets its own virtual thread
# - No thread pool sizing needed — JVM manages carrier threads
# - Blocking I/O in controllers/services doesn't waste platform threads
```

```java
// Custom virtual thread executor for @Async:
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor asyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

// @Async methods now run on virtual threads:
@Async
public CompletableFuture<Report> generateReport(Long reportId) {
    // Blocking DB queries inside — fine with virtual threads
    List<Data> data = repository.findAll();   // VT unmounts during query
    return CompletableFuture.completedFuture(buildReport(data));
}
```

---

### Level 3 — The subtleties

#### 3.1 — Do NOT pool virtual threads

```java
// ❌ WRONG: pooling virtual threads defeats their purpose
ExecutorService pool = Executors.newFixedThreadPool(100);
// This creates 100 PLATFORM threads, not virtual threads

// ❌ ALSO WRONG: limiting virtual threads with a fixed pool
// Virtual threads are cheap — create one per task, not one per pool slot

// ✅ RIGHT: one virtual thread per task, no pooling
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // Creates one virtual thread per submitted task
    // 1 million tasks → 1 million virtual threads → ~8 carrier OS threads
}

// If you need to LIMIT concurrency (protect a downstream service):
// Use a Semaphore, not a pool:
Semaphore concurrencyLimit = new Semaphore(50);

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> {
        concurrencyLimit.acquire();
        try {
            callDownstreamService();   // max 50 concurrent
        } finally {
            concurrencyLimit.release();
        }
    });
}
```

#### 3.2 — When NOT to use virtual threads

```java
// ❌ CPU-bound work: virtual thread never blocks → never unmounts → no benefit
// Same as a platform thread, but with scheduling overhead.
// Use ForkJoinPool or platform thread pool for CPU-bound tasks.

// ❌ With synchronized blocks that contain I/O:
// synchronized pins the virtual thread to the carrier.
// Migrate to ReentrantLock before adopting virtual threads.

// ❌ With ThreadLocal for thread-pool-sized caches:
// Virtual threads create millions of ThreadLocal instances → memory explosion.
// Use ScopedValue (preview in Java 21) instead.

// ✅ I/O-bound work: HTTP calls, DB queries, file reads, message queue polling
// This is the primary use case — the reason virtual threads were created.
```

#### 3.3 — ThreadLocal with virtual threads

```java
// Platform thread pool with ThreadLocal:
// 200 threads × 1 ThreadLocal<Connection> = 200 Connection instances → fine

// Virtual threads with ThreadLocal:
// 1,000,000 virtual threads × 1 ThreadLocal<Connection> = 1M Connection instances
// → OOM (or connection pool exhaustion)

// ✅ Use ScopedValue (Java 21 preview) instead of ThreadLocal:
// ScopedValue is bounded to a scope — automatically cleaned up when scope exits
// No accumulation across millions of virtual threads

// Or: use explicit parameter passing instead of ThreadLocal
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Virtual threads replace all thread pools" | Virtual threads replace platform thread pools for I/O-bound work. For CPU-bound work, platform thread pools (ForkJoinPool) are still optimal — virtual threads add scheduling overhead with no benefit when threads never block. |
| "Virtual threads are preemptively scheduled" | Virtual threads are cooperatively scheduled at blocking points (I/O, sleep, park). A CPU-bound virtual thread that never blocks holds its carrier indefinitely — just like a platform thread. There is no time-slicing between virtual threads. |
| "You should pool virtual threads like platform threads" | Never pool virtual threads. They're designed to be created per-task — ~1 KB each, essentially free. Pooling limits concurrency without saving resources. If you need concurrency limiting, use a Semaphore. |
| "`synchronized` works fine with virtual threads" | `synchronized` blocks PIN the virtual thread to the carrier — the carrier can't serve other virtual threads while waiting. Use `ReentrantLock` instead, which allows unmounting. This is the #1 migration blocker. |
| "Virtual threads are faster than platform threads" | Virtual threads are not faster per-operation. They're better at SCALE — allowing millions of concurrent I/O-bound tasks with a handful of OS threads. A single virtual thread doing CPU work is the same speed (or slightly slower) than a platform thread. |

---

## 🐞 Production Footguns

---

> **Footgun: `synchronized` pinning under load**
> **Cost:** Performance cliff — carriers blocked, throughput collapses
>
> A service migrated to virtual threads (`spring.threads.virtual.enabled=true`). Under low load: 3x throughput improvement. Under high load: throughput dropped below the platform-thread baseline. Root cause: a `synchronized` block around a JDBC connection pool checkout. When the pool was contended, virtual threads were pinned to carriers while waiting for connections — 8 carriers × 1 pinned VT each = only 8 concurrent requests instead of thousands.

```java
// ❌ The trap: synchronized in I/O path
public Data query(String sql) {
    synchronized (connectionPool) {        // PINS virtual thread to carrier
        Connection conn = connectionPool.checkout();   // may block
        try {
            return executeQuery(conn, sql);             // I/O — also pinned
        } finally {
            connectionPool.checkin(conn);
        }
    }
}

// ✅ The fix: ReentrantLock (does NOT pin)
private final ReentrantLock poolLock = new ReentrantLock();

public Data query(String sql) {
    poolLock.lock();                         // virtual thread unmounts if contended
    try {
        Connection conn = connectionPool.checkout();
        try {
            return executeQuery(conn, sql);  // virtual thread unmounts during I/O
        } finally {
            connectionPool.checkin(conn);
        }
    } finally {
        poolLock.unlock();
    }
}

// ✅ Best fix: use a connection pool that supports virtual threads natively
// HikariCP 5.1+ and most modern pools handle this correctly
```

---

> **Footgun: ThreadLocal memory explosion**
> **Cost:** OOM
>
> A logging framework stored a per-thread `MDC` context in `ThreadLocal`. With platform threads (200 pool size): 200 MDC instances. After migrating to virtual threads: one MDC per virtual thread. At 50,000 concurrent requests: 50,000 MDC instances, each holding request context objects → heap exhaustion.

```java
// ❌ The trap: ThreadLocal with virtual threads
ThreadLocal<RequestContext> ctx = new ThreadLocal<>();
// 50,000 virtual threads × RequestContext → OOM

// ✅ The fix: use ScopedValue (Java 21 preview)
ScopedValue<RequestContext> ctx = ScopedValue.newInstance();
ScopedValue.where(ctx, new RequestContext(requestId))
    .run(() -> handleRequest());
// Automatically cleaned up when run() exits — no accumulation
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `thread-pool-executor.md` | Virtual threads replace ThreadPoolExecutor for I/O-bound work. Understanding pool sizing (core, max, queue, rejection) explains WHY virtual threads were created — to eliminate the pool-sizing puzzle. |
| `synchronized-volatile.md` | `synchronized` PINS virtual threads. `ReentrantLock` does not. This is the most critical migration concern. Understanding monitor mechanics (monitorenter in native stack) explains why pinning occurs. |
| `locks-reentrant-readwrite.md` | ReentrantLock uses AQS (heap-based state) → compatible with virtual thread unmounting. StampedLock is also compatible. `synchronized` uses native monitor → incompatible (pins). |
| `java-version-evolution.md` | Virtual threads are the headline feature of Java 21 (LTS). Preview in Java 19-20. Final in 21. ScopedValue and Structured Concurrency are related previews. |
| `fork-join-parallel-streams.md` | Virtual threads' carrier pool IS a ForkJoinPool. Understanding work-stealing explains how carriers pick up unmounted virtual threads. For CPU-bound: use ForkJoinPool directly, not virtual threads. |

---

## 🎙️ Interview Deep Questions

**Q1. What are virtual threads and how do they differ from platform threads?**

> Platform threads are 1:1 with OS threads — each costs ~1 MB stack, managed by the OS scheduler, limited to ~10K-30K per JVM. Virtual threads are JVM-managed, with ~1 KB initial stack (grows on demand), and are not permanently mapped to an OS thread. When a virtual thread blocks on I/O, the JVM saves its execution state (continuation) to the heap, unmounts it from the carrier OS thread, and the carrier immediately picks up another virtual thread. This means 4 carrier threads can serve 100,000 virtual threads — the carriers are always computing, never idle on I/O. You create one virtual thread per task, never pool them.

**Q2. What is pinning? Why does `synchronized` cause it and `ReentrantLock` doesn't?**

> Pinning occurs when a virtual thread blocks but cannot unmount from its carrier — the carrier OS thread is stuck. `synchronized` causes pinning because its monitor state is stored in the carrier thread's native stack frame — unmounting would require saving/restoring native stack state, which isn't implemented. `ReentrantLock` doesn't pin because it uses AQS, which stores all lock state in heap-allocated Java objects — no native stack dependency. When a virtual thread blocks on `reentrantLock.lock()`, it unmounts cleanly. This is why migrating from `synchronized` to `ReentrantLock` is the primary prerequisite for virtual thread adoption.

**Q3. When should you NOT use virtual threads?**

> CPU-bound work — virtual threads only benefit when threads block. A CPU-bound virtual thread never unmounts, so it occupies its carrier full-time with scheduling overhead — use ForkJoinPool instead. Workloads with `synchronized` blocks containing I/O — pinning negates the benefit. Code using ThreadLocal for per-thread caches — millions of virtual threads create millions of ThreadLocal instances → OOM. And workloads that need bounded concurrency — virtual threads are unbounded; use a Semaphore to limit how many run concurrently if a downstream resource has limited capacity.

**Q4. How do you enable virtual threads in Spring Boot?**

> In Spring Boot 3.2+: set `spring.threads.virtual.enabled=true` in `application.yml`. This configures the embedded Tomcat to handle each request on a virtual thread instead of a platform thread from the pool. No thread pool sizing needed — the JVM manages carriers automatically. For `@Async`: create a bean returning `Executors.newVirtualThreadPerTaskExecutor()`. Prerequisite: audit all `synchronized` blocks in the request path — replace with `ReentrantLock` to prevent pinning. Check library compatibility: HikariCP 5.1+, most JDBC drivers, and recent Spring versions support virtual threads.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Virtual threads are lightweight JVM-managed threads. When they block on I/O, they unmount from the carrier OS thread — the carrier serves another virtual thread. Millions of virtual threads on a handful of OS threads.
>
> **Part 2 — How/Why (30s):** Platform threads = 1:1 OS thread, ~1 MB stack, expensive. Virtual threads = JVM-managed continuation on heap, ~1 KB initial. When a virtual thread calls a blocking operation (I/O, sleep, park), the JVM saves its state, unmounts it, and schedules another virtual thread on the same carrier. Carriers are a ForkJoinPool sized to CPU count. Create one virtual thread per task — never pool them. In Spring Boot 3.2+: `spring.threads.virtual.enabled=true` switches Tomcat to virtual threads.
>
> **Part 3 — Gotcha (20s):** `synchronized` blocks PIN virtual threads to carriers — the carrier can't serve others while the VT is blocked inside `synchronized`. Replace with `ReentrantLock` before adopting virtual threads. Don't use for CPU-bound work (no unmounting → no benefit). Don't use ThreadLocal with virtual threads (millions of instances → OOM) — use ScopedValue (Java 21 preview).

---

## 🧾 TL;DR

- **Virtual threads** = JVM-managed, ~1 KB stack, unmount from carrier OS threads during I/O.
- **Carriers** = small ForkJoinPool (~CPU count). Virtual threads mount/unmount dynamically.
- **Create per-task, never pool.** Use Semaphore for concurrency limiting.
- **`synchronized` PINS** — carrier is stuck. Use `ReentrantLock` instead.
- **CPU-bound = no benefit** — virtual thread never unmounts. Use ForkJoinPool.
- **ThreadLocal → OOM** with millions of VTs. Use `ScopedValue` (preview).
- **Spring Boot 3.2+:** `spring.threads.virtual.enabled=true` — one property.
- **Detect pinning:** `-Djdk.tracePinnedThreads=full` or JFR event `jdk.VirtualThreadPinned`.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #22 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: continuation-based unmounting, carrier ForkJoinPool, pinning (synchronized native stack vs ReentrantLock AQS heap), Spring Boot 3.2+ integration (one property), ScopedValue as ThreadLocal replacement, Semaphore for concurrency limiting (not pooling), CPU-bound anti-pattern, ThreadLocal memory explosion, structured concurrency preview. Two production footguns: synchronized pinning under load, ThreadLocal OOM. |
