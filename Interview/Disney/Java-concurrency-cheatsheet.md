# Java Concurrency — Interview Quick Reference

> **Context:** Disney Ad Platforms Round 2 verbal "CS fundamentals" depth test. Confirmed probe from JioHotstar interview (May 2026): interviewer specifically tested `synchronized` vs `AtomicLong`, ConcurrentHashMap compound ops, and GC choice for latency-sensitive paths.
>
> **How to use this file:** Read top-to-bottom once to load the mental model. Each section is one likely interviewer question. The answer is the bold italic text — speak it, don't recite it verbatim.

---

## 1. `volatile` — What It Guarantees (And What It Doesn't)

**One sentence:** `volatile` guarantees **visibility** and **ordering**, NOT **atomicity**.

| Guarantee | Meaning |
|---|---|
| **Visibility** | Every thread always reads the latest written value — not a CPU-cached copy |
| **Ordering** | Writes before `volatile` write happen-before reads after `volatile` read (happens-before in JMM) |
| **NOT atomic** | `volatile counter++` is broken: it's `read + add + write` — three non-atomic steps |

```java
// ✅ CORRECT use: flag/pointer that one thread writes, others read
private volatile boolean shutdown = false;  // one writer, many readers
private volatile AdCampaignIndex activeIndex;  // pointer swap is a single store

// ❌ WRONG use: counter incremented by multiple threads
private volatile long counter = 0;
counter++;  // read + increment + write = NOT atomic — race condition
```

**When volatile is enough:** single writer, multiple readers. E.g., a shutdown flag written by one thread, read by worker threads. Also for reference swaps (pointer assignment = single store = atomic on 64-bit JVM).

**When volatile is NOT enough:** multiple writers. Need `AtomicLong` or `synchronized`.

---

## 2. `synchronized` vs `ReentrantLock` — Pick the Right Tool

| Aspect | `synchronized` | `ReentrantLock` |
|---|---|---|
| **Syntax** | `synchronized (lock) { }` | `lock.lock(); try { } finally { lock.unlock(); }` |
| **Timeout** | ❌ Blocks forever | ✅ `tryLock(timeout)` — gives up after N ms |
| **Fairness** | ❌ JVM decides | ✅ `new ReentrantLock(true)` — FIFO queue |
| **Conditions** | `wait/notify` (one queue) | `Condition` variables (multiple queues) |
| **Reentrant** | ✅ Yes | ✅ Yes |
| **Virtual threads** | ⚠️ Causes pinning in Java 21 | ✅ No pinning |

**When to use `synchronized`:**
> *"Simple mutual exclusion — one lock, straightforward critical section. No timeout needed, no multiple wait conditions. Simpler, less error-prone (can't forget to unlock)."*

**When to use `ReentrantLock`:**
> *"Need `tryLock` (non-blocking lock attempt — useful for deadlock avoidance), or multiple `Condition` variables (e.g., 'not-full' and 'not-empty' in a bounded queue), or explicit fairness, or Java 21 virtual threads where `synchronized` causes carrier thread pinning."*

---

## 3. `AtomicLong` vs `LongAdder` — Contention Matters

Both are non-blocking counters. They differ under contention.

| Aspect | `AtomicLong` | `LongAdder` |
|---|---|---|
| **Mechanism** | Single value, CAS loop | Stripes across CPU cells |
| **Low contention** | ✅ Fast (CAS usually succeeds) | ✅ Fast (same) |
| **High contention** | ❌ Many CAS retries → CPU spin waste | ✅ Each thread writes its own cell → no contention |
| **Read latency** | ✅ Single read | ❌ Sums all cells — not instantaneous snapshot |
| **Use case** | Counters with frequent reads, moderate writes | Impression/event counters: write-heavy, batch-read |

```java
// AtomicLong — good for rate limiters (frequent read + conditional write)
AtomicLong tokens = new AtomicLong(100);
tokens.decrementAndGet();

// LongAdder — good for impression counting (write-only hot path, batch read)
LongAdder impressions = new LongAdder();
impressions.increment();             // per ad serve — no CAS failure under load
long total = impressions.sumThenReset(); // per minute flush — atomic sum + reset
```

**Interview answer:**
> *"At 70M impressions/minute, a single `AtomicLong` would have massive CAS retry waste — thousands of threads competing to increment the same memory location, spinning in CAS loops. `LongAdder` internally maintains a dynamic array of cells. Each thread tends to update its own cell, avoiding cross-thread CAS. When you call `sum()`, it adds up all cells. The cost is that `sum()` is not a consistent snapshot, but for impression counting where I batch-flush every 10 seconds, that's fine."*

---

## 4. `ConcurrentHashMap` — The One Trap Interviewers Test

**ConcurrentHashMap individual operations are atomic. Compound operations are NOT.**

```java
ConcurrentHashMap<String, Long> map = new ConcurrentHashMap<>();

// ❌ WRONG — race condition: containsKey + put is NOT atomic
if (!map.containsKey(clientId)) {
    map.put(clientId, 0L);
    // Thread B passed the containsKey check simultaneously
    // Both threads call put — second overwrites first
}

// ❌ WRONG — get + conditional put is NOT atomic
Long val = map.get(clientId);
if (val == null) {
    map.put(clientId, newBucket);  // race: two threads insert simultaneously
}

// ✅ CORRECT — computeIfAbsent is ONE atomic operation
map.computeIfAbsent(clientId, id -> newBucket);

// ✅ CORRECT — merge is atomic: apply function if key exists
map.merge(clientId, 1L, Long::sum);  // atomic increment pattern

// ✅ CORRECT — compute is atomic (even more flexible)
map.compute(clientId, (k, v) -> v == null ? 1L : v + 1L);
```

**Internals:**
- Java 8+: **bucket-level locking** (not a single global lock). Different buckets can be written concurrently.
- Reads: **lock-free** via `volatile` reads on node references.
- Resize: **cooperative** — multiple threads help transfer buckets.
- Default concurrency level: effectively the number of CPU cores × 8.

**Interview answer for `computeIfAbsent`:**
> *"On `ConcurrentHashMap`, each method call is atomic in isolation — `containsKey` is atomic, `put` is atomic. But between `containsKey` returning false and `put` executing, another thread can insert the same key. That's a check-then-act race condition. `computeIfAbsent` is a single atomic operation: it checks and inserts in one indivisible step. The mapping function is called at most once per key."*

---

## 5. `ReadWriteLock` vs `StampedLock` — Read-Heavy Optimization

**Use when:** reads vastly outnumber writes (e.g., in-memory ad index: read 1M times/sec, refreshed once/30 sec).

| Aspect | `ReadWriteLock` | `StampedLock` (Java 8+) |
|---|---|---|
| **Multiple readers** | ✅ Concurrent | ✅ Concurrent |
| **Writer exclusion** | ✅ Blocks all readers | ✅ Blocks all readers |
| **Optimistic read** | ❌ Not supported | ✅ `tryOptimisticRead()` — no lock at all! |
| **Reentrant** | ✅ Yes | ❌ **NOT reentrant — deadlock risk** |
| **Performance** | Good | Better (optimistic path has zero lock overhead) |

```java
StampedLock lock = new StampedLock();

// Optimistic read — fastest path, no lock acquired
long stamp = lock.tryOptimisticRead();
// Read data here
int value = this.someField;
if (!lock.validate(stamp)) {
    // Another thread wrote during our optimistic read
    // Upgrade to real read lock
    stamp = lock.readLock();
    try {
        value = this.someField;
    } finally {
        lock.unlockRead(stamp);
    }
}

// Write path
long writeStamp = lock.writeLock();
try {
    this.someField = newValue;
} finally {
    lock.unlockWrite(writeStamp);
}
```

**Critical gotcha:**
> *"`StampedLock` is NOT reentrant. If a thread holding a write lock calls a method that also tries to acquire the write lock, it deadlocks. Unlike `ReentrantLock` where re-entry returns the same lock, `StampedLock` treats the second acquisition as a new request and waits forever for the first to release — which it never will."*

---

## 6. `ThreadLocal` — Use Carefully in Thread Pools

**What it does:** Each thread gets its own isolated copy of a variable. No sharing, no synchronization needed.

```java
ThreadLocal<DateFormat> dateFormat = ThreadLocal.withInitial(
    () -> new SimpleDateFormat("yyyy-MM-dd")
);

// Each thread gets its own DateFormat instance
// SimpleDateFormat is NOT thread-safe — ThreadLocal is the fix
String formatted = dateFormat.get().format(new Date());
```

**The pool trap:**
```java
// ❌ MEMORY LEAK in thread pools
ThreadLocal<byte[]> buffer = new ThreadLocal<>();
buffer.set(new byte[1024 * 1024]);  // 1MB
// Thread finishes task, returns to pool
// ThreadLocal NOT cleaned up — 1MB leaks until thread is destroyed
// Thread pool threads live for the application lifetime → permanent leak

// ✅ CORRECT — always remove in finally block
try {
    buffer.set(new byte[1024 * 1024]);
    // use buffer
} finally {
    buffer.remove();  // removes from current thread's map
}
```

**Java 21 alternative:** `ScopedValue` (preview in Java 21, stable in 22). Unlike `ThreadLocal`, values are bound to a scope (structured concurrency), automatically cleaned up. No manual `remove()` needed. Works correctly with virtual threads.

---

## 7. Virtual Threads (Java 21) — The Pinning Trap

**What virtual threads are:**
> *"Virtual threads are lightweight JVM-managed threads, not OS threads. You can create millions of them. When a virtual thread blocks on I/O, the JVM parks it and uses the underlying carrier OS thread for other virtual threads. This is cooperative scheduling — huge throughput for I/O-bound workloads (HTTP calls, DB queries, Kafka consumers)."*

**The pinning trap:**
```java
// ❌ PINNING — synchronized causes carrier thread to be pinned
// The OS thread cannot unmount the virtual thread while inside synchronized block
synchronized (lock) {
    // If this code does I/O or blocks:
    response = httpClient.send(request);
    // The carrier OS thread is pinned — it can't run other virtual threads
    // Defeats the purpose of virtual threads
}

// ✅ ReentrantLock — virtual threads unmount correctly while waiting
ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    response = httpClient.send(request);  // virtual thread parks, carrier frees
} finally {
    lock.unlock();
}
```

**When NOT to use virtual threads:**
> *"CPU-bound work: virtual threads don't help if the thread is computing, not waiting. You still need platform threads + ForkJoinPool for parallel computation. Virtual threads shine for I/O-bound work: handling thousands of concurrent HTTP connections, Kafka consumers, database queries."*

**GC choice for ad serving:**
> *"For Disney's ad serving path — p95 SLA of 14ms — I'd choose ZGC (Z Garbage Collector). ZGC has sub-millisecond GC pauses regardless of heap size. G1GC pauses scale with heap size and can spike to 50-200ms — unacceptable in a 14ms p95 target. ZGC trades slightly higher total CPU overhead for predictable low-pause behavior. For batch processing (Flink impression aggregation), G1GC is fine — pauses matter less when processing is async."*

---

## 8. `wait/notify` vs `Condition` vs `BlockingQueue`

**`wait/notify` — classic but error-prone:**
```java
// wait() MUST be in a while loop — not if — because of spurious wakeups
synchronized (lock) {
    while (queue.isEmpty()) {
        lock.wait();  // releases lock, waits for notify
    }
    process(queue.poll());
}
```

**`Condition` — multiple wait sets (producer-consumer with bounded queue):**
```java
ReentrantLock lock = new ReentrantLock();
Condition notFull  = lock.newCondition();
Condition notEmpty = lock.newCondition();

// Producer
lock.lock();
try {
    while (queue.size() == MAX) {
        notFull.await();  // waits only on the notFull condition
    }
    queue.add(item);
    notEmpty.signal();  // wakes only threads waiting on notEmpty
} finally {
    lock.unlock();
}
```

**`BlockingQueue` — just use this:**
> *"In production code I'd use `LinkedBlockingQueue` or `ArrayBlockingQueue` — they implement producer-consumer with bounded capacity, backpressure, and blocking take/put, internally using `ReentrantLock` + `Condition`. `wait/notify` and manual `Condition` usage is for interview demonstrations of understanding — not something to write by hand in production."*

---

## 9. Deadlock — How to Detect and Prevent

**Deadlock conditions (all 4 must be true):**
1. Mutual exclusion (locks are exclusive)
2. Hold-and-wait (thread holds one lock, waits for another)
3. No preemption (locks can't be forcibly taken)
4. Circular wait (A waits for B, B waits for A)

**Prevention — consistent lock ordering:**
```java
// ❌ DEADLOCK: Thread 1 locks accountA then accountB
//             Thread 2 locks accountB then accountA
void transfer(Account from, Account to) {
    synchronized (from) {
        synchronized (to) { ... }
    }
}

// ✅ PREVENTION: always lock by account ID order (smaller ID first)
void transfer(Account from, Account to) {
    Account first  = from.id < to.id ? from : to;
    Account second = from.id < to.id ? to   : from;
    synchronized (first) {
        synchronized (second) { ... }
    }
}
```

**Prevention — `tryLock` with timeout:**
```java
// Lock A with timeout — if can't acquire both, release and retry
if (lockA.tryLock(100, TimeUnit.MILLISECONDS)) {
    try {
        if (lockB.tryLock(100, TimeUnit.MILLISECONDS)) {
            try {
                // Critical section
            } finally {
                lockB.unlock();
            }
        }
    } finally {
        lockA.unlock();
    }
}
```

---

## 10. Executor Framework — Threadpool Sizing Rules

```java
// I/O-bound tasks: threads > CPU cores (threads wait on I/O)
// Rule of thumb: N_cpus × (1 + wait_time / compute_time)
// If 90% wait, 10% compute: 8 cores × (1 + 9) = 80 threads
ExecutorService ioPool = Executors.newFixedThreadPool(80);

// CPU-bound tasks: threads = CPU cores (more threads = context switch overhead)
ExecutorService cpuPool = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);

// Virtual threads (Java 21) — I/O-bound, unlimited scale
// JVM manages scheduling; no need to pre-size
ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor();
```

**Always use bounded queues with pools:**
```java
// ❌ Executors.newFixedThreadPool uses unbounded LinkedBlockingQueue
// If tasks arrive faster than processing: queue grows unboundedly → OOM

// ✅ Custom ThreadPoolExecutor with bounded queue + RejectedExecutionHandler
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    8,                               // corePoolSize
    16,                              // maxPoolSize
    60L, TimeUnit.SECONDS,           // keepAlive for excess threads
    new ArrayBlockingQueue<>(1000),  // bounded queue — 1000 max pending tasks
    new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure: caller executes task itself
);
```

---

## Quick Verbal Cheat Sheet — 30 Seconds Each

| Question | Answer |
|---|---|
| `volatile` vs `synchronized`? | "`volatile`: visibility only, no atomicity. `synchronized`: visibility + atomicity + mutual exclusion. Use volatile for single-writer flags, synchronized for multi-writer compound ops." |
| When does `synchronized` hurt? | "Global lock serializes all threads on that object. JVM can't optimize away. In Java 21, causes virtual thread carrier pinning — use ReentrantLock instead." |
| Best counter for impression tracking? | "`LongAdder`: stripes across CPU cells, no CAS retries under high contention. Batch `sumThenReset()` every 10 sec. `AtomicLong` loses under millions-per-second write rate." |
| `ConcurrentHashMap` thread-safe? | "Individual operations yes. Compound ops no. `containsKey + put` has race. Use `computeIfAbsent`, `merge`, or `compute` for atomic compound ops." |
| GC for ad serving? | "ZGC: sub-ms pauses at any heap size. G1GC: pauses scale with heap, can spike to 200ms — unacceptable for p95 14ms SLA." |
| Virtual threads in Java 21? | "JVM-managed, millions concurrent, great for I/O-bound. `synchronized` causes carrier thread pinning — use `ReentrantLock`. Not for CPU-bound work." |
| Deadlock prevention? | "Consistent lock ordering (always acquire in same order). Or `tryLock` with timeout and release-and-retry." |
| `StampedLock` gotcha? | "NOT reentrant — re-acquiring writeLock deadlocks the thread. Use ReentrantLock if re-entrance is possible." |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 15, 2026 | **File created.** Disney Ad Platforms verbal CS depth prep. Covers: volatile guarantees, synchronized vs ReentrantLock, AtomicLong vs LongAdder, ConcurrentHashMap compound ops trap, StampedLock gotcha, virtual thread pinning, ThreadLocal pool leak, deadlock prevention, GC choice (ZGC vs G1GC). Structured as Q&A to match interview format. |
