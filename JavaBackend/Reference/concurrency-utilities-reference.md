# ⚡ Concurrency Utilities — Quick Reference

> **Use:** look up which concurrency tool to use for a given problem. See `DeepDive/` notes for internals.

---

## 🔹 "Which tool do I use?" Decision Table

| Problem | Tool | DeepDive note |
|---|---|---|
| Protect shared mutable state (simple) | `synchronized` | `synchronized-volatile.md` |
| Protect shared mutable state (need tryLock/timeout) | `ReentrantLock` | `locks-reentrant-readwrite.md` |
| Single counter, multiple writers | `AtomicInteger` / `AtomicLong` | `synchronized-volatile.md` |
| High-contention counter (metrics) | `LongAdder` | `synchronized-volatile.md` |
| Single flag (one writer, many readers) | `volatile boolean` | `synchronized-volatile.md` |
| Shared map (read + write) | `ConcurrentHashMap` | `concurrent-collections.md` |
| Shared list (read-heavy, write-rare) | `CopyOnWriteArrayList` | `concurrent-collections.md` |
| Producer-consumer queue (bounded) | `ArrayBlockingQueue` | `concurrent-collections.md` |
| Read-heavy, write-rare lock | `ReentrantReadWriteLock` | `locks-reentrant-readwrite.md` |
| Ultra-read-heavy with optimistic reads | `StampedLock` | `locks-reentrant-readwrite.md` |
| Wait for N events before proceeding | `CountDownLatch` | `synchronization-aids.md` |
| N threads meet at a point, then all proceed | `CyclicBarrier` | `synchronization-aids.md` |
| Limit concurrent access to N | `Semaphore` | `synchronization-aids.md` |
| Execute tasks on a thread pool | `ThreadPoolExecutor` | `thread-pool-executor.md` |
| Async computation with chaining | `CompletableFuture` | `completable-future.md` |
| I/O-bound tasks (Java 21) | Virtual threads | `virtual-threads-java21.md` |
| CPU-bound parallel computation | `ForkJoinPool` / parallel streams | `fork-join-parallel-streams.md` |
| Scheduled/periodic tasks | `ScheduledThreadPoolExecutor` | `thread-pool-executor.md` |
| Compare-and-swap on a reference | `AtomicReference<T>` | `synchronized-volatile.md` |
| Multiple conditions on one lock | `ReentrantLock` + `Condition` | `locks-reentrant-readwrite.md` |

---

## 🔹 Atomic Classes

| Class | Wraps | Key methods |
|---|---|---|
| `AtomicInteger` | `int` | `get()`, `set()`, `incrementAndGet()`, `compareAndSet(expect, update)`, `updateAndGet(fn)` |
| `AtomicLong` | `long` | Same as AtomicInteger for longs |
| `AtomicBoolean` | `boolean` | `get()`, `set()`, `compareAndSet()` |
| `AtomicReference<T>` | `T` | `get()`, `set()`, `compareAndSet()`, `updateAndGet(fn)` |
| `AtomicIntegerArray` | `int[]` | Per-index atomic ops: `get(i)`, `set(i, v)`, `incrementAndGet(i)` |
| `LongAdder` | `long` | `increment()`, `add(n)`, `sum()` — stripe-split, high contention |
| `LongAccumulator` | `long` | `accumulate(x)`, `get()` — generic accumulation with function |

---

## 🔹 Lock Comparison

| Feature | `synchronized` | `ReentrantLock` | `ReadWriteLock` | `StampedLock` |
|---|---|---|---|---|
| Auto-release | ✅ (on block exit) | ❌ (must call unlock in finally) | ❌ | ❌ |
| tryLock | ❌ | ✅ | ✅ | ✅ |
| Timeout | ❌ | ✅ | ✅ | ✅ |
| Interruptible | ❌ | ✅ | ✅ | ✅ |
| Fair mode | ❌ | ✅ | ✅ | ❌ |
| Reentrant | ✅ | ✅ | ✅ | ❌ |
| Multiple conditions | ❌ | ✅ | ✅ | ❌ |
| Concurrent reads | ❌ | ❌ | ✅ | ✅ |
| Optimistic reads | ❌ | ❌ | ❌ | ✅ |
| Virtual thread friendly | ❌ (pins) | ✅ (unmounts) | ✅ | ✅ |

---

## 🔹 ThreadPoolExecutor Quick Setup

```java
// Production template:
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    cores,                              // corePoolSize
    cores * 2,                          // maximumPoolSize
    60L, TimeUnit.SECONDS,             // keepAliveTime
    new ArrayBlockingQueue<>(1000),    // bounded work queue
    new ThreadFactory() {               // named threads
        final AtomicInteger c = new AtomicInteger(1);
        public Thread newThread(Runnable r) {
            return new Thread(r, "worker-" + c.getAndIncrement());
        }
    },
    new CallerRunsPolicy()             // backpressure
);

// Sizing:
// CPU-bound: corePoolSize = availableProcessors()
// I/O-bound: corePoolSize = cores × (1 + waitTime/computeTime)
// Java 21 I/O: Executors.newVirtualThreadPerTaskExecutor()
```

---

## 🔹 Happens-Before Quick Reference

| Action A | happens-before | Action B |
|---|---|---|
| `synchronized(lock) { ... }` unlock | → | `synchronized(lock) { ... }` lock (same lock) |
| `volatile` write | → | `volatile` read (same variable) |
| `Thread.start()` | → | First action in started thread |
| Last action in thread | → | `Thread.join()` return |
| `Lock.unlock()` | → | `Lock.lock()` (same lock) |
| `CountDownLatch.countDown()` | → | `CountDownLatch.await()` return |
| `Semaphore.release()` | → | `Semaphore.acquire()` |
| `final` field write in constructor | → | Any read after construction |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #34 (Phase 5). Decision table, Atomic classes, Lock comparison matrix, ThreadPoolExecutor template, happens-before quick reference. |
