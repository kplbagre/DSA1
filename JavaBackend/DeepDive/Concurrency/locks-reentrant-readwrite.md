# 🧵 Locks — ReentrantLock, ReadWriteLock, StampedLock — Deep Dive

> After this note you can explain when `ReentrantLock` beats `synchronized`, implement the `tryLock` timeout pattern, describe `ReadWriteLock` semantics (concurrent reads, exclusive writes), and explain `StampedLock`'s optimistic read mode.

---

## 🎯 The Problem This Solves

`synchronized` is simple and sufficient for 90% of locking needs. But it can't: attempt to acquire a lock without blocking (`tryLock`), wait with a timeout, be interrupted while waiting, guarantee FIFO fairness, use multiple wait conditions, or separate read locks from write locks. When you need any of these, you need `java.util.concurrent.locks`.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **ReentrantLock** | A mutual-exclusion lock with the same semantics as `synchronized` plus: tryLock, timed lock, interruptible lock, fairness, and multiple Condition objects. Must be unlocked in `finally`. |
| **Fairness** | A fair lock grants access in FIFO order — the longest-waiting thread gets the lock next. Non-fair (default) allows barging — a thread that just arrived can acquire the lock before threads already waiting. Non-fair has higher throughput; fair prevents starvation. |
| **ReadWriteLock** | An interface with two locks: a read lock (shared — multiple threads can hold simultaneously) and a write lock (exclusive — only one thread, and no readers). Optimizes for read-heavy workloads. |
| **StampedLock** | An advanced lock (Java 8) with three modes: write (exclusive), read (shared), and **optimistic read** (non-blocking — validate after reading, retry if a write occurred). Not reentrant. |
| **Condition** | A replacement for `wait()`/`notify()` that works with `Lock` instead of `synchronized`. Multiple Conditions per Lock — enables separate queues (e.g., "not full" and "not empty" for a bounded buffer). |
| **Optimistic read** | A read that acquires NO lock. Instead, it takes a "stamp" (version number), reads the data, then validates the stamp. If no write occurred, the read is valid — zero contention. If a write occurred, retry with a full read lock. |

---

## 🧠 Mental Model

Think of `ReentrantLock` as a `synchronized` block you can **customize**: you can try the door without waiting (`tryLock()`), wait with a timeout (`tryLock(5, SECONDS)`), walk away if interrupted (`lockInterruptibly()`), or guarantee that the longest-waiting person goes next (`new ReentrantLock(true)`).

`ReadWriteLock` is a **library with a special rule**: any number of readers can be inside at the same time (reading is safe in parallel), but a writer needs exclusive access — all readers must leave first, and no one else enters until the writer is done.

`StampedLock`'s optimistic read is a **library with a checkout stamp**: you walk in, note the stamp on the door, read the book, then check if the stamp changed. If it didn't, no one wrote anything — your read is valid without ever locking. If it did, you get a real read lock and try again.

> If you can say "ReentrantLock = synchronized + tryLock + timeout + fairness + multiple Conditions; ReadWriteLock = concurrent reads, exclusive writes; StampedLock = adds optimistic reads (no lock, validate after); always unlock in finally" without notes, you have locks.

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// synchronized can't do any of these:

// 1. Try without blocking:
// synchronized (lock) { ... }   ← blocks until acquired — no "try" option

// 2. Timeout:
// No way to say "try for 5 seconds, then give up"

// 3. Interrupt:
// Thread.interrupt() has NO effect on a thread blocked on synchronized
// The thread waits until the lock is released — uninterruptible

// 4. Fairness:
// synchronized provides no ordering guarantee — threads can starve

// 5. Separate read/write access:
// synchronized locks out ALL other threads — even concurrent readers
// For read-heavy workloads: 95% of access is reads that could safely run in parallel
```

---

### Level 2 — The real mechanism

#### 2.1 — ReentrantLock

```java
// Tier 2 — Production: ReentrantLock with tryLock
public class ResourcePool {
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Resource> pool = new ArrayList<>();

    // Basic lock/unlock — MUST unlock in finally
    public Resource acquire() {
        lock.lock();
        try {
            return pool.isEmpty() ? createNew() : pool.remove(pool.size() - 1);
        } finally {
            lock.unlock();   // ⭐ ALWAYS in finally — if body throws, lock still releases
        }
    }

    // tryLock — non-blocking attempt
    public Resource tryAcquire() {
        if (lock.tryLock()) {
            try {
                return pool.isEmpty() ? null : pool.remove(pool.size() - 1);
            } finally {
                lock.unlock();
            }
        }
        return null;   // couldn't acquire — caller can do something else
    }

    // tryLock with timeout
    public Resource acquireWithTimeout(long millis) throws InterruptedException {
        if (lock.tryLock(millis, TimeUnit.MILLISECONDS)) {
            try {
                return pool.isEmpty() ? createNew() : pool.remove(pool.size() - 1);
            } finally {
                lock.unlock();
            }
        }
        throw new TimeoutException("Could not acquire resource within " + millis + "ms");
    }

    // lockInterruptibly — caller can interrupt the waiting thread
    public Resource acquireInterruptibly() throws InterruptedException {
        lock.lockInterruptibly();   // throws InterruptedException if thread is interrupted while waiting
        try {
            return pool.isEmpty() ? createNew() : pool.remove(pool.size() - 1);
        } finally {
            lock.unlock();
        }
    }
}
```

**Fairness:**

```java
// Non-fair (default) — allows barging → higher throughput
ReentrantLock lock = new ReentrantLock();        // non-fair

// Fair — FIFO ordering → prevents starvation but lower throughput
ReentrantLock fairLock = new ReentrantLock(true); // fair
// Fair locks have ~2x overhead due to queue management.
// Use only when starvation is a real problem (not premature optimization).
```

#### 2.2 — Condition objects

```java
// Tier 2 — Production: bounded buffer with two Conditions
public class BoundedBuffer<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }

    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                notFull.await();   // wait until not full — releases lock while waiting
            }
            queue.add(item);
            notEmpty.signal();   // wake one consumer
        } finally {
            lock.unlock();
        }
    }

    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();   // wait until not empty
            }
            T item = queue.poll();
            notFull.signal();   // wake one producer
            return item;
        } finally {
            lock.unlock();
        }
    }
}
// Two conditions: producers wait on 'notFull', consumers wait on 'notEmpty'.
// With synchronized, you'd have one wait/notify channel for both — waking
// the wrong type of thread wastes CPU (spurious/unnecessary wakeups).
```

#### 2.3 — ReentrantReadWriteLock

```java
// Tier 2 — Production: read-heavy cache with ReadWriteLock
public class ConfigCache {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReadLock readLock = rwLock.readLock();
    private final WriteLock writeLock = rwLock.writeLock();
    private final Map<String, String> cache = new HashMap<>();

    // Multiple threads can read concurrently
    public String get(String key) {
        readLock.lock();
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }

    // Only one thread can write (and all readers must exit first)
    public void put(String key, String value) {
        writeLock.lock();
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
}
// Read:  95% of calls → readers don't block each other → high throughput
// Write:  5% of calls → writer waits for all readers to exit → exclusive access
```

**Semantics:**

```
  READ LOCK:
  - Multiple threads can hold simultaneously (shared)
  - Blocks if a WRITE lock is held
  - Does NOT block other readers

  WRITE LOCK:
  - Only one thread can hold (exclusive)
  - Blocks if ANY read lock OR write lock is held
  - Blocks all readers and writers until released

  DOWNGRADE: write lock → read lock is supported
  UPGRADE:   read lock → write lock is NOT supported (deadlock risk)
```

#### 2.4 — StampedLock (Java 8)

```java
// Tier 2 — Production: StampedLock with optimistic read
public class Point {
    private double x, y;
    private final StampedLock sl = new StampedLock();

    // Optimistic read — no lock acquired, validate after
    public double distanceFromOrigin() {
        long stamp = sl.tryOptimisticRead();   // non-blocking — returns a stamp
        double currentX = x;                    // read fields (no lock held!)
        double currentY = y;
        if (!sl.validate(stamp)) {             // check: did a write happen since our stamp?
            // Write occurred → our reads may be stale → fall back to full read lock
            stamp = sl.readLock();
            try {
                currentX = x;
                currentY = y;
            } finally {
                sl.unlockRead(stamp);
            }
        }
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }

    // Write — exclusive lock
    public void move(double deltaX, double deltaY) {
        long stamp = sl.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            sl.unlockWrite(stamp);
        }
    }
}
// Optimistic read: ZERO contention when no writes are happening.
// In a read-heavy scenario (99% reads, 1% writes): optimistic reads
// almost always succeed → no lock overhead at all.
```

**⚠️ StampedLock is NOT reentrant.** A thread that holds a write lock and tries to acquire another write lock → deadlock. Use ReentrantLock if reentrancy is needed.

---

### Level 3 — The subtleties

#### 3.1 — When to use which

```
  synchronized:
  → Simple critical section, no timeout/tryLock needed
  → 90% of cases — don't over-engineer

  ReentrantLock:
  → Need tryLock, timeout, interruptible, fairness, multiple Conditions
  → Need to pass lock across methods (can't do with synchronized block)

  ReadWriteLock:
  → Read-heavy workload (≥ 90% reads)
  → Read contention is the bottleneck
  → ⚠️ Write starvation possible under heavy read load (writers wait for all readers)

  StampedLock:
  → Read-heavy + very low write frequency
  → Optimistic reads avoid ALL lock overhead when writes are rare
  → ⚠️ NOT reentrant. Complex API. Easy to misuse. Use only when profiling proves benefit.

  ConcurrentHashMap:
  → If your pattern is "lock around a HashMap" → just use ConcurrentHashMap
  → It handles fine-grained locking internally — better than you can do manually
```

#### 3.2 — Deadlock with locks

```java
// Deadlock is possible with ReentrantLock — same conditions as synchronized:
// Thread A: lock1.lock() → tries lock2.lock() — blocks (held by B)
// Thread B: lock2.lock() → tries lock1.lock() — blocks (held by A)
// Both threads wait forever.

// Prevention: ALWAYS acquire locks in a consistent order across all threads.
// If both threads acquire lock1 first, then lock2, no circular wait is possible.

// Detection: tryLock with timeout
if (lock1.tryLock(100, TimeUnit.MILLISECONDS)) {
    try {
        if (lock2.tryLock(100, TimeUnit.MILLISECONDS)) {
            try {
                // critical section
            } finally {
                lock2.unlock();
            }
        } else {
            // couldn't get lock2 — release lock1, back off, retry
        }
    } finally {
        lock1.unlock();
    }
}
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "ReentrantLock is always better than synchronized" | Synchronized is simpler (auto-release, no finally needed), has JIT optimizations (lock coarsening, lock elision), and is sufficient for 90% of cases. ReentrantLock adds capabilities but also complexity (manual unlock in finally, forgetting = lock leak). Use ReentrantLock only when you need its extra features. |
| "ReadWriteLock always outperforms synchronized for reads" | ReadWriteLock has higher overhead per lock/unlock than synchronized. If critical sections are very short (< 1μs), the lock management overhead exceeds the contention savings. Profile first. Also: under heavy read load, writers can starve (readers keep arriving, writer never gets exclusive access). |
| "StampedLock is the best lock" | StampedLock is NOT reentrant (deadlock if you try to re-acquire). Its API is error-prone (stamps must be tracked). It doesn't support Condition objects. Use it only when profiling proves that optimistic reads provide measurable benefit — typically in latency-critical, read-dominated data structures. |
| "Forgetting unlock is caught by the compiler" | No — unlike `synchronized` (auto-release on block exit), `Lock.unlock()` is a manual call. If you forget it or don't put it in `finally`, the lock is held forever. Every subsequent thread blocks indefinitely. This is the #1 Lock anti-pattern. |

---

## 🐞 Production Footguns

---

> **Footgun: Missing unlock in finally**
> **Cost:** Permanent thread starvation (all threads block forever)
>
> A developer acquired a `ReentrantLock` but forgot the `finally` block. The critical section threw an exception on a rare code path. The lock was never released. Every subsequent thread that tried to acquire the lock blocked indefinitely. The service appeared "hung" — 100% of request threads blocked, zero throughput, no crash, no error message.

```java
// ❌ The trap: no finally
lock.lock();
doWork();   // throws on rare condition → lock NEVER released
lock.unlock();   // never reached

// ✅ The fix: ALWAYS unlock in finally
lock.lock();
try {
    doWork();
} finally {
    lock.unlock();   // runs even if doWork() throws
}
```

---

> **Footgun: ReadWriteLock write starvation**
> **Cost:** Writer thread never executes (indefinite delay)
>
> A configuration reload service used `ReadWriteLock`. The read lock was held by 100+ concurrent API request threads reading config. The writer (config reload) tried to acquire the write lock — but new readers kept arriving before all existing readers released. The write lock acquisition was indefinitely deferred. Config changes never took effect. The fix: use a fair ReadWriteLock or a brief read lock hold time with a StampedLock.

```java
// ❌ The trap: non-fair ReadWriteLock under heavy reads
ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();   // non-fair default
// 100 readers continuously holding read lock → writer waits forever

// ✅ The fix: fair ReadWriteLock (writers get priority when waiting)
ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);   // fair
// Fair mode: once writer starts waiting, no new readers are admitted
// Existing readers drain → writer gets access → readers resume
// Cost: ~2x overhead per lock/unlock — acceptable for correctness
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `synchronized-volatile.md` | `synchronized` provides the same guarantees as `ReentrantLock` — this note explains WHEN the extra features of Lock justify the added complexity. |
| `java-memory-model.md` | `Lock.unlock()` establishes happens-before with `Lock.lock()` (same lock) — identical JMM semantics to `synchronized`. The visibility and ordering guarantees come from the JMM, not from the Lock API itself. |
| `thread-pool-executor.md` (planned — Note #19) | ThreadPoolExecutor internally uses `ReentrantLock` for managing the work queue and worker thread lifecycle. Understanding locks explains the executor's internal synchronization. |
| `synchronization-aids.md` (planned — Note #20) | CountDownLatch, CyclicBarrier, and Semaphore are built on `AbstractQueuedSynchronizer` (AQS) — the same foundation as ReentrantLock. Understanding Lock mechanics makes the sync aids' internals transparent. |

---

## 🎙️ Interview Deep Questions

**Q1. When should you use `ReentrantLock` over `synchronized`?**

> Use `ReentrantLock` when you need: `tryLock()` (attempt without blocking — enables deadlock avoidance patterns), `tryLock(timeout)` (bounded wait — prevents indefinite blocking), `lockInterruptibly()` (waiting thread can be interrupted — enables responsive shutdown), fairness (`new ReentrantLock(true)` — FIFO ordering prevents starvation), or multiple `Condition` objects (separate wait queues for different conditions — e.g., "not full" and "not empty" in a bounded buffer). For everything else, use `synchronized` — it's simpler, auto-releases on block exit, and benefits from JIT optimizations.

**Q2. Explain ReadWriteLock. When does it outperform synchronized?**

> `ReadWriteLock` has two locks: a read lock (shared — multiple threads hold concurrently) and a write lock (exclusive — only one thread, no readers). It outperforms `synchronized` when reads vastly outnumber writes (≥ 90% reads) AND the critical section is long enough that lock management overhead is negligible relative to the contention saved. For short critical sections (< 1μs), synchronized is faster because ReadWriteLock's internal bookkeeping (tracking read count, checking write waiting) exceeds the contention cost. Caveat: under heavy read load, writers can starve — use fair mode or StampedLock to prevent this.

**Q3. What is StampedLock's optimistic read mode? When would you use it?**

> Optimistic read acquires NO lock — it takes a stamp (version number), reads the data, then calls `validate(stamp)` to check if a write occurred during the read. If no write occurred, the read is valid — zero contention, zero blocking. If a write occurred, the stamp is invalid — fall back to a full read lock and re-read. Use it for read-dominated data structures where writes are very rare (< 1%) and read latency is critical. Example: a coordinate point read by 1000 threads per second, updated once per minute. The 999 reads between updates are completely lock-free. Caveat: StampedLock is NOT reentrant and doesn't support Conditions — use only when profiling proves the benefit.

**Q4. How do you prevent deadlocks with multiple locks?**

> Three strategies: (1) Lock ordering — always acquire locks in the same order across all threads. If every thread acquires lockA before lockB, circular wait is impossible. (2) tryLock with timeout — `lock.tryLock(100, MILLISECONDS)`. If you can't get the second lock, release the first and retry (back-off). This breaks the hold-and-wait condition. (3) Single lock redesign — restructure so you never hold two locks simultaneously. For the deadlock to exist, four conditions must hold: mutual exclusion, hold-and-wait, no preemption, circular wait. Lock ordering breaks circular wait. tryLock breaks hold-and-wait. Breaking any ONE condition prevents deadlock.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** `ReentrantLock` = synchronized + tryLock + timeout + fairness + multiple Conditions. `ReadWriteLock` = concurrent reads, exclusive writes. `StampedLock` = adds optimistic reads (no lock, validate after). All must be unlocked in `finally`.
>
> **Part 2 — How/Why (30s):** `synchronized` can't try-without-blocking, can't timeout, can't interrupt waiting threads, can't do FIFO fairness. `ReentrantLock` adds all of these. `ReadWriteLock` optimizes read-heavy workloads by allowing concurrent reads — only writes are exclusive. `StampedLock` goes further with optimistic reads: take a stamp, read without locking, validate — if no write happened, the read is free. Under 99% reads, optimistic reads have zero contention overhead.
>
> **Part 3 — Gotcha (20s):** The #1 trap: forgetting `unlock()` in `finally` — the lock is held forever, every subsequent thread blocks indefinitely. The #2 trap: `ReadWriteLock` write starvation under heavy reads — new readers keep arriving, writer never gets exclusive access. Use fair mode or StampedLock. And `StampedLock` is NOT reentrant — re-acquiring a held write lock = deadlock with yourself.

---

## 🧾 TL;DR

- **ReentrantLock** = synchronized + tryLock, timeout, interruptible, fairness, Conditions. Unlock in `finally`.
- **ReadWriteLock** = concurrent reads (shared), exclusive writes. Outperforms synchronized when ≥ 90% reads.
- **StampedLock** = optimistic reads (stamp → read → validate — zero lock overhead). NOT reentrant.
- **Fair lock** = FIFO ordering, prevents starvation. ~2x overhead vs non-fair.
- **Condition** = wait/notify replacement for Lock. Multiple conditions per lock (separate queues).
- **Deadlock prevention:** lock ordering (same order everywhere), tryLock with timeout (back off and retry).
- **Use synchronized by default.** Reach for Lock only when you need its extra capabilities.
- **ConcurrentHashMap > lock + HashMap** in almost all cases.

---

## 🔬 Appendix — Deadlock Detection in Production

### Reading `jstack` deadlock output

```bash
# jstack auto-detects Java-level deadlocks and prints them at the end:
$ jstack <pid>

# If deadlock exists, you'll see:
# =============================
# Found one Java-level deadlock:
# =============================
# "order-processor-3":
#   waiting to lock monitor 0x00007f8b2c003f08 (a java.lang.Object),
#   which is held by "payment-handler-1"
# "payment-handler-1":
#   waiting to lock monitor 0x00007f8b2c004a18 (a java.lang.Object),
#   which is held by "order-processor-3"
#
# Java stack information for the threads listed above:
# "order-processor-3":
#     at com.walmart.OrderService.processPayment(OrderService.java:87)
#     - waiting to lock <0x000000076ab67890> (a java.lang.Object)
#     - locked <0x000000076ab12345> (a java.lang.Object)
# "payment-handler-1":
#     at com.walmart.PaymentService.updateOrder(PaymentService.java:42)
#     - waiting to lock <0x000000076ab12345> (a java.lang.Object)
#     - locked <0x000000076ab67890> (a java.lang.Object)

# READING:
# Thread A holds lock X, wants lock Y.
# Thread B holds lock Y, wants lock X.
# Circular wait → DEADLOCK. Neither thread will ever proceed.

# FIX: enforce consistent lock ordering (always acquire X before Y in ALL code paths)
```

### The Dining Philosophers — classic deadlock

```
  5 philosophers sit at a round table. Each needs TWO forks (left + right) to eat.
  Each picks up their LEFT fork first, then tries to pick up their RIGHT fork.

  Philosopher 0: picks up fork 0 (left), waits for fork 1 (right)
  Philosopher 1: picks up fork 1 (left), waits for fork 2 (right)
  Philosopher 2: picks up fork 2 (left), waits for fork 3 (right)
  Philosopher 3: picks up fork 3 (left), waits for fork 4 (right)
  Philosopher 4: picks up fork 4 (left), waits for fork 0 (right) ← held by Philosopher 0!

  CIRCULAR WAIT → all 5 philosophers starve (deadlock).

  FIX — lock ordering:
  Make Philosopher 4 pick up fork 0 first (lower-numbered fork first).
  Now: Philosopher 4 waits for fork 0 → doesn't hold fork 4 → Philosopher 3 gets fork 4 → eats → releases → chain unblocks.
  One philosopher breaks the cycle by acquiring locks in order.
```

### `jcmd` for deadlock detection (alternative to jstack)

```bash
# jcmd also detects deadlocks:
jcmd <pid> Thread.print
# Same output as jstack — includes deadlock detection at the end.
# Preferred over jstack in modern JDKs (more options, better integration).
```

### Deadlock vs Livelock vs Starvation

```
  DEADLOCK:  Two threads each hold what the other needs. Both wait forever.
             Detection: jstack "Found one Java-level deadlock"
             Fix: lock ordering, tryLock with timeout

  LIVELOCK:  Two threads keep changing state in response to each other,
             but neither makes progress. Like two people stepping aside
             for each other in a hallway — both move, neither passes.
             Detection: CPU is high, threads are RUNNABLE (not BLOCKED),
                        but no work completes.
             Fix: add randomized backoff (random delay before retry)

  STARVATION: One thread never gets CPU/lock because higher-priority
              threads always run first. Thread is alive but never progresses.
              Detection: one thread has very low CPU time in thread dump.
              Fix: fair locks (ReentrantLock(true)), fair semaphores,
                   avoid priority-based scheduling.
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #17 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: ReentrantLock (tryLock, timeout, interruptible, fairness), Condition objects (bounded buffer with two conditions), ReentrantReadWriteLock (read-shared/write-exclusive semantics, downgrade supported, upgrade not), StampedLock (optimistic read mode — stamp/validate pattern, not reentrant), deadlock prevention (ordering, tryLock back-off), decision matrix (when to use each), lock coarsening/elision from JIT. Two production footguns: missing unlock in finally, ReadWriteLock write starvation. |
