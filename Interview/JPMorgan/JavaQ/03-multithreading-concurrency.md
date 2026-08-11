# JPMorgan — Multithreading & Concurrency Questions

> `⭐⭐⭐` = 3+ reports | `⭐⭐` = 2 | `⭐` = once, high-signal
> **Context:** JPMC asks multithreading from a "how did you handle this in production?" angle.
> For 3–7 YOE Java devs, this is the **most differentiated topic** — few candidates answer well.

---

## 🔴 Tier 1 — Asked in Almost Every Technical Round

### Q1 `⭐⭐⭐` — What is multithreading? How did you use it in your project?

**Template answer:**
> "Multithreading lets a program run multiple tasks concurrently. In my project at [company], we had a batch processing job that read 10K records and called an external API for each. With a single thread it took ~10 minutes. I replaced it with an `ExecutorService` with a fixed thread pool of 20 threads, processing records in parallel — runtime dropped to ~35 seconds. The key design decision was choosing pool size: since the work was I/O-bound (API calls with ~500ms latency), I sized it as `Runtime.getRuntime().availableProcessors() * 2` as a starting point, then tuned via load testing."

---

### Q2 `⭐⭐⭐` — Thread safety: synchronized vs Lock vs volatile

```java
// synchronized — simplest, but coarse-grained
public synchronized void increment() { count++; }

// ReentrantLock — explicit lock, more control
private final ReentrantLock lock = new ReentrantLock();
public void increment() {
    lock.lock();
    try { count++; }
    finally { lock.unlock(); }  // ALWAYS in finally
}

// volatile — visibility only, NOT atomicity
private volatile boolean running = true;  // other threads see latest value
// running++ is NOT thread-safe even with volatile (read-modify-write is 3 ops)

// AtomicInteger — atomic compound operations
private final AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();        // atomic
count.compareAndSet(old, new);  // CAS — no lock needed
```

> **Key distinction JPMC probes:** `volatile` fixes the visibility problem (CPU cache flushing) but NOT the atomicity problem. `count++` is 3 ops: read, increment, write. Two threads can both read the same value → both write `old+1` → one increment is lost. Use `AtomicInteger` or `synchronized` for compound ops.

---

### Q3 `⭐⭐⭐` — ConcurrentHashMap internals

**Java 8+ implementation:**
- Internally uses an array of `Node[]` (like HashMap)
- **No global lock** — each bucket is locked individually using `synchronized` on the first node
- For empty buckets: uses **CAS (Compare-And-Swap)** — no lock at all
- `size()` uses a distributed counter (`CounterCell[]`) to avoid contention
- Null keys/values are rejected — ambiguity: null could mean "key not present" or "value is null" → both are disallowed for clarity

**Thread-safe operations:**
- `put`, `get`, `remove` → atomic per-bucket
- `putIfAbsent(k, v)` → atomic (safe compound op)
- **Iteration** → weakly consistent (won't throw CME, but may not see latest writes)
- `computeIfAbsent(k, fn)` → entire compute is atomic under the bucket lock

---

### Q4 `⭐⭐` — ExecutorService & Thread Pool

```java
// Fixed thread pool
ExecutorService pool = Executors.newFixedThreadPool(10);

// Submit Callable, get Future
Future<String> future = pool.submit(() -> fetchFromApi(id));
String result = future.get(5, TimeUnit.SECONDS); // timeout

// CompletableFuture (Java 8+) — non-blocking chaining
CompletableFuture.supplyAsync(() -> fetchUser(id), pool)
    .thenApply(user -> enrichUser(user))
    .thenAccept(user -> saveUser(user))
    .exceptionally(ex -> { log.error("failed", ex); return null; });

// Always shut down pool on app exit
pool.shutdown();
pool.awaitTermination(30, TimeUnit.SECONDS);
```

**JPMC follow-up:** *"What pool size would you use for I/O-bound vs CPU-bound tasks?"*
> - **CPU-bound:** `N = #cores` — adding more threads just causes context-switch overhead.
> - **I/O-bound:** `N = #cores × (1 + wait_time / compute_time)` — threads block on I/O, so more threads = more utilization. Common heuristic: `#cores × 2` as starting point.
> - **Java 21 virtual threads:** for I/O-bound, just `Thread.ofVirtual()` with an unbounded virtual thread per task — JVM multiplexes onto OS threads automatically.

---

### Q5 `⭐⭐` — Deadlock: cause + detection + prevention

**Cause:** Thread A holds Lock 1, waits for Lock 2. Thread B holds Lock 2, waits for Lock 1. Circular wait.

```java
// Deadlock-prone pattern
synchronized (lockA) {
    synchronized (lockB) { /* Thread 1 */ }
}
synchronized (lockB) {
    synchronized (lockA) { /* Thread 2 */ }  // DEADLOCK
}

// Prevention: always acquire locks in the SAME order
synchronized (lockA) {
    synchronized (lockB) { /* Both threads do A then B */ }
}

// Or use tryLock with timeout
// tryLock returns false (does NOT block) if the lock isn't acquired within the timeout
// → thread can then release lockA and retry later, breaking the circular wait
if (lockA.tryLock(100, TimeUnit.MILLISECONDS)) {
    try {
        if (lockB.tryLock(100, TimeUnit.MILLISECONDS)) {
            try { /* do work */ }
            finally { lockB.unlock(); }
        }
        // if lockB not acquired: lockA is still released in the outer finally — no deadlock
    } finally { lockA.unlock(); }
}
```

**Detection in production:** `jstack <pid>` → prints thread dump → look for `BLOCKED` threads + "waiting to lock" cycle. Or use JVisualVM / async-profiler.

---

### Q6 `⭐⭐` — Producer-Consumer problem

```java
// Using BlockingQueue — the cleanest solution JPMC expects
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100); // bounded

// Producer thread
void produce() throws InterruptedException {
    while (true) {
        Task task = generateTask();
        queue.put(task);  // blocks if queue is full
    }
}

// Consumer thread
void consume() throws InterruptedException {
    while (true) {
        Task task = queue.take();  // blocks if queue is empty
        process(task);
    }
}
```

> `BlockingQueue` eliminates the need for explicit `wait()`/`notify()`. JPMC interviewers reward this answer over the raw `synchronized` + `wait`/`notify` approach because it mirrors production code (Kafka consumer is conceptually the same pattern at distributed scale).

---

### Q7 `⭐⭐` — Race condition: explain + example + fix

**Example:**
```java
// UNSAFE — race condition
private int counter = 0;
public void increment() { counter++; } // read-modify-write, 3 ops

// Thread 1: reads counter = 5
// Thread 2: reads counter = 5
// Thread 1: writes counter = 6
// Thread 2: writes counter = 6
// Result: 6 instead of 7. One increment lost.

// FIX 1: synchronized method
public synchronized void increment() { counter++; }

// FIX 2: AtomicInteger (lock-free, preferred for single variable)
private AtomicInteger counter = new AtomicInteger(0);
public void increment() { counter.incrementAndGet(); }
```

---

### Q8 `⭐` — ThreadLocal

```java
// Each thread gets its own copy — no synchronization needed
private static final ThreadLocal<SimpleDateFormat> dateFormat =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

// Usage
String formatted = dateFormat.get().format(new Date());

// CRITICAL: remove from ThreadLocal in thread pool contexts
// Thread pool reuses threads → old ThreadLocal value leaks into next task
try {
    // use dateFormat.get()
} finally {
    dateFormat.remove();  // prevent memory leak in thread pools
}
```

> **JPMC production scenario:** SimpleDateFormat is not thread-safe. Options: (1) ThreadLocal per thread, (2) `DateTimeFormatter` (Java 8, thread-safe), (3) create new instance per call (expensive). ThreadLocal is the classic interview answer.

---

### Q9 `⭐` — CountDownLatch vs CyclicBarrier

| | CountDownLatch | CyclicBarrier |
|---|---|---|
| Reusable | ❌ One-time | ✅ Can reset |
| Who waits | Main thread waits for N workers | N workers wait for each other |
| Use case | "Wait until all services start" | "All batch threads sync at checkpoint" |

```java
// CountDownLatch — main waits for 3 services to init
// Each service calls latch.countDown() when it's ready — decrements the counter by 1
// latch.await() blocks the calling (main) thread until the counter reaches 0
CountDownLatch latch = new CountDownLatch(3);
startServiceA(() -> latch.countDown()); // passes countDown as a Runnable callback
startServiceB(() -> latch.countDown()); // each service runs on its own thread
startServiceC(() -> latch.countDown());
latch.await();  // main thread blocks here until all 3 have called countDown()
startHandlingRequests();
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created from 2024–2026 JPMC interview reports. |
