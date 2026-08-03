# Confluent DSA — Concurrency Problems

> **Format:** Follow `_format.md` in this folder.
>
> **What's in this file:** "Now make it thread-safe" problems — Confluent's dedicated concurrency round. These are extensions of design-coding problems from `02-design-coding.md`, plus standalone concurrency patterns.
>
> **Prerequisite:** Read [`00-concepts-before-problems.md` §9](./00-concepts-before-problems.md#9-concurrency--from-zero-to-interview-ready) — covers all primitives used here (synchronized, volatile, AtomicReference+CAS, ReadWriteLock, BlockingQueue, ExecutorService) with code templates, visuals, and cross-question answers. Extended patterns (Semaphore, latch, wait/notify) in [`LLD/concurrency-deep-dive.md`](../../../LLD/concurrency-deep-dive.md).

---

## Table of Contents

| # | Problem | Tier | Base Problem | Status |
|---|---|---|---|---|
| 1 | [Thread-Safe KV Store with TTL](#1-thread-safe-kv-store-with-ttl) | ⭐ Tier 1 | 02 #1 KV Store | [ ] |
| 2 | [Thread-Safe LRU Cache with TTL](#2-thread-safe-lru-cache-with-ttl) | ⭐ Tier 1 | 02 #2 LRU Cache | [ ] |
| 3 | [Task Scheduler — Producer-Consumer](#3-task-scheduler--producer-consumer) | ⭐ Tier 1 | Custom | [ ] |
| 4 | [Readers-Writers with Starvation Prevention](#4-readers-writers-with-starvation-prevention) | Tier 2 | Classic | [ ] |

---

## 1. Thread-Safe KV Store with TTL

### **🎤 How It's Asked:**

> "You built the KV Store with TTL in Part 1. Now make it thread-safe — multiple threads calling put(), get(), and getAverage() concurrently."
>
> This is always Part 2 of the KV Store question from `02-design-coding.md` #1.

### **Discussion — How to arrive at the solution:**

The single-threaded KV Store has three shared mutable state areas:
1. **HashMap** — concurrent put/get/remove
2. **DLL** — concurrent node insert/remove/traverse
3. **runningSum / activeCount** — concurrent read-modify-write

The simplest correct approach: `synchronized` on every public method. One global lock. Correct but kills throughput — every get() blocks every other get().

Better: `ReentrantReadWriteLock`. get() and getAverage() are reads (can run concurrently). put() and evictExpired() are writes (need exclusive access). But getAverage() calls evictExpired() which mutates state — so it actually needs a write lock too.

Best realistic approach for interview: use `synchronized` with a comment explaining the upgrade path to ReadWriteLock. Don't over-engineer during the interview.

### **Key Insight:**

The critical race is `evictExpired()` running concurrently with `put()` — both modify the DLL and HashMap. If evictExpired() removes a node while put() is updating it, the DLL pointers corrupt. A single lock around every public method prevents this.

For higher throughput, separate the eviction into a background thread with its own lock, and use `ConcurrentHashMap` for the map. But this adds complexity — mention it, don't code it.

### **Optimal Solution:**

**Steps in plain English:**

1. **Wrap every public method with `synchronized`** — one global lock on the TTLKeyValueStore instance.
2. **put()** — same logic as single-threaded, now atomic under the lock.
3. **get()** — same logic, atomic under the lock.
4. **getAverage()** — same logic, atomic under the lock. evictExpired() runs inside the lock.
5. **No change to internal logic** — DLL and HashMap operations don't need their own locks because the outer synchronized covers everything.

```java
import java.util.HashMap;

public class ConcurrentTTLKeyValueStore {

    private static class Entry {
        String key;
        int value;
        long expiryTime;
        Entry prev;
        Entry next;

        Entry(String key, int value, long expiryTime) {
            this.key = key;
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    private final HashMap<String, Entry> map = new HashMap<>();
    private final Entry head = new Entry("", 0, Long.MAX_VALUE);
    private final Entry tail = new Entry("", 0, Long.MIN_VALUE);
    private long runningSum = 0;
    private int activeCount = 0;

    public ConcurrentTTLKeyValueStore() {
        head.next = tail;
        tail.prev = head;
    }

    // Step 1 — synchronized: one global lock per public method
    // Step 2 — put: evict → remove old → insert new → update sum
    public synchronized void put(String key, int value, long ttlMillis) {
        long now = System.currentTimeMillis();
        evictExpired(now);

        if (map.containsKey(key)) {
            Entry old = map.get(key);
            runningSum -= old.value;
            activeCount--;
            removeFromDLL(old);
        }

        Entry entry = new Entry(key, value, now + ttlMillis);
        map.put(key, entry);
        addToHead(entry);
        runningSum += value;
        activeCount++;
    }

    // Step 3 — get: evict → lookup → return
    public synchronized Integer get(String key) {
        evictExpired(System.currentTimeMillis());
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        return entry.value;
    }

    // Step 4 — getAverage: evict → return sum/count
    public synchronized Double getAverage() {
        evictExpired(System.currentTimeMillis());
        if (activeCount == 0) {
            return 0.0;
        }
        return (double) runningSum / activeCount;
    }

    // Step 5 — evictExpired: called inside the lock, no separate sync needed
    private void evictExpired(long now) {
        Entry current = tail.prev;
        while (current != head && current.expiryTime <= now) {
            Entry toRemove = current;
            current = current.prev;
            map.remove(toRemove.key);
            removeFromDLL(toRemove);
            runningSum -= toRemove.value;
            activeCount--;
        }
    }

    private void addToHead(Entry entry) {
        entry.next = head.next;
        entry.prev = head;
        head.next.prev = entry;
        head.next = entry;
    }

    private void removeFromDLL(Entry entry) {
        entry.prev.next = entry.next;
        entry.next.prev = entry.prev;
    }
}
```

- **Time:** Same O(1) amortized per operation. Lock acquisition adds constant overhead.
- **Space:** Same O(n). No additional data structures for concurrency.

### **🔄 Variants:**

- "What if TTL is per-key?" → Same DLL monotonic-assumption bug applies — `evictExpired()` stops too early when a short-TTL entry sits closer to the head than a long-TTL entry. Fix: replace the DLL with `TreeMap<Long, List<String>>` as the expiry index (see `02-design-coding.md` #1 Variants for the detailed failure trace and code delta). In the concurrent version, `TreeMap` is not thread-safe, so the same `synchronized` wrapper covers all TreeMap mutations — no additional locking needed.
- "Use `ConcurrentHashMap` instead of `synchronized`" → `ConcurrentHashMap` alone isn't enough — DLL operations and runningSum are still not atomic. You'd need `ConcurrentHashMap` + `synchronized` on DLL + `AtomicLong` for sum + `AtomicInteger` for count. More complex, marginal benefit.
- "Separate eviction into a background thread" → `ScheduledExecutorService` runs eviction every N seconds under its own lock. get() doesn't call evictExpired(). Reduces latency on get() but expired entries may linger.
- "Striped locking" → Partition keys into N buckets, one lock per bucket. get() on key "A" doesn't block get() on key "B". Used by `ConcurrentHashMap` internally.

### **❓ Cross-Questions:**

- **"Why `synchronized` and not `ReentrantReadWriteLock`?"** → Every public method calls `evictExpired()` which mutates the DLL. So even "read" operations need write access. ReadWriteLock only helps if reads are truly read-only. If you skip eviction on reads (lazy eviction only on put), then get() becomes read-only and ReadWriteLock helps.
- **"What's the contention impact?"** → Under high concurrency, all threads serialize on the one lock. Throughput ≈ single-threaded. For a cache, this is often acceptable because operations are fast (microseconds under the lock). If it's not acceptable, switch to striped locking.
- **"Does `synchronized` guarantee visibility?"** → Yes — the Java Memory Model guarantees that all writes made inside a synchronized block are visible to any subsequent thread entering a synchronized block on the same monitor.

---

## 2. Thread-Safe LRU Cache with TTL

### **🎤 How It's Asked:**

> "Design an LRU Cache. Now make it thread-safe. Now add TTL."
>
> Already covered in `02-design-coding.md` #2 with `ReentrantReadWriteLock`. This section adds the concurrency analysis.

### **Recap (from 02-design-coding.md #2):**

HashMap + DLL. `ReentrantReadWriteLock` for read/write separation. TTL check on get(): if expired, upgrade to write lock, remove, return null. put(): always write lock. Evict LRU from tail if at capacity.

**Full code:** [`02-design-coding.md` #2](./02-design-coding.md#2-thread-safe-lru-cache-with-ttl)

**Full single-threaded implementation:** [`LLD/Problems/lru-cache/lru-cache.md`](../../../LLD/Problems/lru-cache/lru-cache.md)

### **Shared Mutable State Analysis:**

| State | Writers | Readers | Protection |
|---|---|---|---|
| `HashMap<K, Node>` | put(), evict | get() | `ReentrantReadWriteLock` |
| DLL (head/tail pointers) | put(), evict, moveToHead | — (no direct reads) | Write lock only |
| `Node.value` | put() (update) | get() | Write lock on update, read lock on access |
| `Node.expiryTime` | put() (refresh TTL) | get() (check expiry) | Write lock on update, read lock on check |
| `capacity` | — (immutable) | put() | Final field — no sync needed |

### **The Read-Lock-to-Write-Lock Upgrade Problem:**

```
Thread A: get("x") → acquires READ lock → finds expired entry → needs WRITE lock to remove

WRONG approach:
  rwLock.readLock().lock();     // holds read lock
  rwLock.writeLock().lock();    // DEADLOCK — can't upgrade while holding read
                                 // (another thread also holds read lock, both try to upgrade)

CORRECT approach:
  rwLock.readLock().unlock();   // release read lock first
  rwLock.writeLock().lock();    // now acquire write lock
  // RE-CHECK condition — another thread may have removed it while we had no lock
  if (map.containsKey(key)) {
      removeNode(node);
      map.remove(key);
  }
```

The re-check after acquiring the write lock is mandatory — this is the **double-check locking** pattern applied to cache eviction.

### **🔄 Variants:**

- "Use `ConcurrentHashMap` + `ConcurrentLinkedDeque` for lock-free" → Approximate LRU (not strict). `ConcurrentLinkedDeque` doesn't support O(1) arbitrary removal. Trade strict LRU for throughput.
- "Per-segment locking like Guava Cache" → Divide the cache into N segments, each with its own lock + HashMap + DLL. Operations on different segments are fully parallel.

### **❓ Cross-Questions:**

- **"What's the throughput difference vs `synchronized`?"** → For 90% read workload: `ReadWriteLock` allows ~10x throughput (concurrent reads). For 50/50 read/write: minimal benefit. For write-heavy: no benefit.
- **"Why not `StampedLock`?"** → `StampedLock` supports optimistic reads (even faster — no blocking), but is harder to use correctly. Upgrade from optimistic to read/write lock has subtle semantics. Not worth the complexity in an interview.

---

## 3. Task Scheduler — Producer-Consumer

### **🎤 How It's Asked:**

> "Design a task scheduler where multiple producers submit tasks and a fixed pool of workers execute them. Tasks have priorities."
>
> Alternate: "Build a thread-safe job queue with priority ordering."
>
> Confluent's Tableflow team schedules data materialization jobs — this maps directly to their product.

### **Discussion — How to arrive at the solution:**

This is the classic producer-consumer pattern with priority ordering. The core data structure is `PriorityBlockingQueue` — a thread-safe priority queue where `take()` blocks when empty and `put()` never blocks (unbounded).

Already implemented in full in [`LLD/Problems/job-scheduler/job-scheduler.md`](../../../LLD/Problems/job-scheduler/job-scheduler.md). This section focuses on the concurrency patterns specific to the Confluent interview.

### **Recap of Job Scheduler design:**

- `PriorityBlockingQueue<Job>` for priority-ordered, thread-safe queueing
- Single dispatcher thread calls `queue.take()` → CAS job status PENDING→RUNNING → submit to worker pool
- `ExecutorService` with N fixed workers
- `AtomicReference<JobStatus>` for cancel-vs-dispatch race (CAS ensures exactly one wins)
- `CopyOnWriteArrayList<JobListener>` for status-change observers

**Full implementation:** [`LLD/Problems/job-scheduler/job-scheduler.md`](../../../LLD/Problems/job-scheduler/job-scheduler.md)

### **Key Concurrency Patterns in This Problem:**

**Pattern 1 — BlockingQueue as the synchronization backbone:**

```java
// Producer threads: never block (unbounded queue)
queue.put(job);

// Dispatcher thread: blocks when queue is empty — no busy-wait, no polling
Job job = queue.take();
// Wakes automatically when a producer adds a job
```

No explicit locks, no wait/notify — `PriorityBlockingQueue` handles all coordination internally.

**Pattern 2 — CAS for cancel-vs-dispatch race:**

```
Thread A (cancel):      job.casStatus(PENDING, CANCELLED)  → wins  → job never executes
Thread B (dispatcher):  job.casStatus(PENDING, RUNNING)    → loses → skips this job
```

Only one `compareAndSet` succeeds — the other sees `false` and takes no action. No locks needed for this single-field state machine.

**Pattern 3 — Defensive listener notification:**

```java
for (JobListener listener : listeners) {
    try {
        listener.onStatusChange(jobId, newStatus);
    } catch (Exception ignored) {
        // One bad listener must not prevent others from being notified
    }
}
```

`CopyOnWriteArrayList` makes iteration lock-free. Try-catch per listener isolates failures.

### **Optimal Solution (compact interview version):**

**Steps in plain English:**

1. **Queue** — `PriorityBlockingQueue` for thread-safe, priority-ordered job storage.
2. **Worker pool** — `ExecutorService.newFixedThreadPool(N)` for concurrent job execution.
3. **Dispatcher loop** — single thread calls `queue.take()` (blocks when empty), CAS status to RUNNING, submits to pool.
4. **submit()** — add job to queue (thread-safe, never blocks).
5. **cancel()** — CAS status PENDING→CANCELLED. If CAS fails, job already dispatched.
6. **executeJob()** — run job.execute() in try-catch, transition to COMPLETED or FAILED.

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class TaskScheduler {

    enum TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

    static class Task implements Comparable<Task> {
        final String id;
        final int priority;
        final Runnable work;
        final AtomicReference<TaskStatus> status =
            new AtomicReference<>(TaskStatus.PENDING);

        Task(String id, int priority, Runnable work) {
            this.id = id;
            this.priority = priority;
            this.work = work;
        }

        @Override
        public int compareTo(Task other) {
            return Integer.compare(this.priority, other.priority);
        }
    }

    // Step 1 — thread-safe priority queue
    private final PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>();
    // Step 2 — fixed worker pool
    private final ExecutorService workers;
    private volatile boolean running = false;

    public TaskScheduler(int workerCount) {
        this.workers = Executors.newFixedThreadPool(workerCount);
    }

    // Step 4 — submit: add to queue (thread-safe, never blocks)
    public void submit(Task task) {
        queue.put(task);
    }

    // Step 5 — cancel: CAS PENDING → CANCELLED
    public boolean cancel(Task task) {
        return task.status.compareAndSet(TaskStatus.PENDING, TaskStatus.CANCELLED);
    }

    public void start() {
        running = true;
        // Step 3 — dispatcher: single thread, blocks on take()
        Thread dispatcher = new Thread(() -> {
            while (running) {
                try {
                    Task task = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (task == null) {
                        continue;
                    }
                    // CAS: PENDING → RUNNING. If cancel() won, skip.
                    if (!task.status.compareAndSet(TaskStatus.PENDING, TaskStatus.RUNNING)) {
                        continue;
                    }
                    // Step 6 — execute in worker pool
                    workers.submit(() -> executeTask(task));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "task-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    // Step 6 — execute: try-catch → COMPLETED or FAILED
    private void executeTask(Task task) {
        try {
            task.work.run();
            task.status.compareAndSet(TaskStatus.RUNNING, TaskStatus.COMPLETED);
        } catch (Exception e) {
            task.status.compareAndSet(TaskStatus.RUNNING, TaskStatus.FAILED);
        }
    }

    public void shutdown() {
        running = false;
        workers.shutdown();
    }
}
```

- **Time:** submit() is O(log n) — PriorityBlockingQueue uses a heap internally. cancel() is O(1) — CAS on AtomicReference. Dispatch is O(log n) for take().
- **Space:** O(n) — queue holds all pending tasks.

### **🔄 Variants:**

- "Add retry with exponential backoff" → On FAILED, if attempts < maxAttempts, reset to PENDING, re-enqueue with delay.
- "Add task dependencies (DAG)" → `Map<taskId, Set<dependencyIds>>`. On completion, check if dependent tasks are unblocked.
- "Rate limit the dispatcher" → Add a `Semaphore(maxConcurrent)` — acquire before submit to pool, release on completion.

### **❓ Cross-Questions:**

- **"Why single dispatcher instead of N workers polling the queue?"** → Single dispatcher preserves strict priority ordering. N workers competing on `take()` could dequeue in non-priority order due to thread scheduling.
- **"Why `poll(500ms)` instead of `take()`?"** → `take()` blocks indefinitely — the `running` flag would never be checked on shutdown. `poll(timeout)` returns null after 500ms, letting the loop check `running` and exit cleanly.
- **"What if the worker pool is full?"** → `ExecutorService` has an internal unbounded queue by default. Tasks submitted to a full pool queue internally until a worker frees up. To apply backpressure, use `new ThreadPoolExecutor(N, N, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(maxQueued))` — `submit()` throws `RejectedExecutionException` when the internal queue is full.

---

## 4. Readers-Writers with Starvation Prevention

### **🎤 How It's Asked:**

> "Implement a readers-writers lock. Multiple readers can read simultaneously, but a writer needs exclusive access. How do you prevent writer starvation?"
>
> Alternate: "Design a concurrent config store where reads are frequent and writes are rare."

### **Discussion — How to arrive at the solution:**

The basic readers-writers problem: readers don't block each other, but a writer blocks everyone. Java's `ReentrantReadWriteLock` implements this out of the box. The interesting follow-up is starvation.

**Writer starvation:** If readers arrive continuously, a waiting writer never gets the lock — there's always at least one active reader. Fix: "fair" mode — `new ReentrantReadWriteLock(true)`. In fair mode, if a writer is waiting, new readers queue behind the writer instead of jumping ahead.

### **Key Insight:**

The standard `ReentrantReadWriteLock` in non-fair mode (default) allows reader barging — new readers can acquire the lock even when a writer is waiting. This maximizes reader throughput but can starve writers indefinitely.

Fair mode (`new ReentrantReadWriteLock(true)`) uses FIFO ordering — threads acquire in arrival order. Writers never starve, but reader throughput drops because consecutive readers can't barge ahead of a queued writer.

The interview question is really: "do you know about fair vs unfair, and when to pick each?"

### **Optimal Solution:**

**Steps in plain English:**

1. **Choose fair ReadWriteLock** — prevents writer starvation at the cost of reduced reader throughput.
2. **Read operations** — acquire read lock, read, release in finally.
3. **Write operations** — acquire write lock, write, release in finally.
4. **Always release in finally** — prevents deadlock on exception.

```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentConfigStore {

    private final Map<String, String> config = new HashMap<>();
    // Step 1 — fair mode: writers don't starve
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);

    // Step 2 — read: multiple threads can read concurrently
    public String get(String key) {
        rwLock.readLock().lock();
        try {
            return config.get(key);
        } finally {
            // Step 4 — always release in finally
            rwLock.readLock().unlock();
        }
    }

    // Step 2 — read: getAll is also a read operation
    public Map<String, String> getAll() {
        rwLock.readLock().lock();
        try {
            return new HashMap<>(config);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // Step 3 — write: exclusive access, blocks all readers and other writers
    public void put(String key, String value) {
        rwLock.writeLock().lock();
        try {
            config.put(key, value);
        } finally {
            // Step 4 — always release in finally
            rwLock.writeLock().unlock();
        }
    }

    // Step 3 — write: bulk update is also a write operation
    public void putAll(Map<String, String> updates) {
        rwLock.writeLock().lock();
        try {
            config.putAll(updates);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // Step 3 — write: remove needs exclusive access
    public String remove(String key) {
        rwLock.writeLock().lock();
        try {
            return config.remove(key);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
```

- **Time:** O(1) per get/put/remove — HashMap operations under the lock.
- **Space:** O(n) — config map + one lock object.

### **🔄 Variants:**

- "Use `StampedLock` for optimistic reads" → `StampedLock.tryOptimisticRead()` returns a stamp. Read without blocking. After reading, validate stamp. If valid, no one wrote — your read is consistent. If invalid, fall back to read lock. 10-50x faster for read-heavy workloads.
- "Use `ConcurrentHashMap` instead" → Simpler, but no bulk-update atomicity. `putAll()` with `ConcurrentHashMap` is NOT atomic — another thread can see a partial update. ReadWriteLock guarantees `putAll()` is all-or-nothing from any reader's perspective.
- "Add change listeners" → On write, notify subscribers. Use `CopyOnWriteArrayList<ConfigChangeListener>` — same pattern as Job Scheduler observers.

### **❓ Cross-Questions:**

- **"When is unfair mode better?"** → When writer starvation isn't a concern (writes are rare and readers are latency-sensitive). Unfair mode maximizes reader throughput by allowing reader barging.
- **"What's the throughput impact of fair mode?"** → ~20-30% reduction in reader throughput under high contention — WHY: readers must queue behind waiting writers instead of barging. Under low contention, the difference is negligible.
- **"Why not just use `synchronized`?"** → `synchronized` provides mutual exclusion — all operations are exclusive. For a config store with 95% reads and 5% writes, `synchronized` means readers block each other unnecessarily. `ReadWriteLock` allows concurrent readers → ~10x throughput.
- **"How does Confluent's Kafka use this?"** → Kafka brokers use ReadWriteLock for partition metadata: reads happen on every produce/consume request, writes happen only on leader election or partition reassignment. Read-heavy workload → ReadWriteLock is the right choice.

---

## 🧾 Concurrency Quick Reference — What to Say in the Interview

| "Make it thread-safe" follow-up | Reach for | One-sentence justification |
|---|---|---|
| Single data structure (KV Store, LRU) | `synchronized` on all public methods | "Simplest correct approach; I'd upgrade to ReadWriteLock if profiling shows read contention." |
| Read-heavy (config store, cache reads) | `ReentrantReadWriteLock(fair)` | "Concurrent reads, exclusive writes, fair mode prevents writer starvation." |
| Priority job queue | `PriorityBlockingQueue` + CAS | "BlockingQueue handles producer-consumer sync; CAS handles cancel-vs-dispatch race." |
| Counter / flag | `AtomicInteger` / `volatile` | "Single-field, no compound action — atomic or volatile is enough." |
| Listener list | `CopyOnWriteArrayList` | "Writes are rare (subscribe), reads are frequent (publish iteration)." |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | File created. 4 concurrency problems with Confluent-specific framing. Cross-references to LLD notes. |
| Aug 2026 | **Fix — Problem 1 Variants:** Added per-key TTL note — same DLL monotonic assumption applies in the concurrent version. TreeMap fix from 02 Problem 1 applies identically; synchronized wrapper covers TreeMap thread safety. |
