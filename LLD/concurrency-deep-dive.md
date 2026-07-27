# Concurrency Deep-Dive for LLD

> **Read before any problem note.** `java-building-blocks-for-lld.md` tells you WHICH primitive to pick. This file tells you WHY — the root causes of race conditions, deadlock, and visibility bugs, and the coordination patterns to fix them.
>
> **Scope:** This file covers the WHY and HOW. For the quick "which primitive when" decision table, see `java-building-blocks-for-lld.md` §Concurrency Primitives.

---

## 🎯 Why Concurrency Matters in Every LLD Interview

Every LLD interviewer asks some version of: *"Now make it thread-safe."* This is not optional. The problems that need it:

| Problem | Critical concurrency scenario |
|---|---|
| Parking Lot | Two cars trying to claim the last spot simultaneously |
| BookMyShow | Two users booking seat A1 for the same show at the same time |
| Rate Limiter | Two requests simultaneously reading the token count and both "passing" |
| Elevator | Floor requests arriving from multiple threads |
| Vending Machine | Two buyers pressing dispense at the same time |

The goal: identify the **shared mutable state**, lock the **minimum necessary scope**, and explain WHY that lock strategy was chosen.

---

## ⚠️ Root Cause 1 — Race Conditions

A **race condition** (a bug where the result depends on the unpredictable timing of two or more threads) happens when two threads read-then-write a shared value without coordination. Two sub-patterns:

### Check-Then-Act

"Check if a condition is true, then act on it" — but the condition can change between the check and the act.

```java
// ❌ BROKEN — BookMyShow seat booking with check-then-act race
public boolean bookSeat(String seatId, String userId) {
    // Thread A checks: seat is available → true
    // Thread B checks: seat is available → true  (BOTH pass the check)
    if (seatInventory.get(seatId) == SeatStatus.AVAILABLE) {
        // Thread A books the seat
        // Thread B also books the seat (DOUBLE BOOKING)
        seatInventory.put(seatId, SeatStatus.BOOKED);
        return true;
    }
    return false;
}
```

```java
// ✅ FIX — atomic check-then-act with synchronized
public synchronized boolean bookSeat(String seatId, String userId) {
    // Now only one thread executes this at a time
    if (seatInventory.get(seatId) == SeatStatus.AVAILABLE) {
        seatInventory.put(seatId, SeatStatus.BOOKED);
        return true;
    }
    return false;
}
```

### Read-Modify-Write

"Read a value, compute a new value, write it back" — another thread modifies it between the read and the write.

```java
// ❌ BROKEN — counter increment is three CPU operations: READ, ADD, WRITE
private int availableSpots = 10;

public void releaseSpot() {
    // Thread A reads: 10
    // Thread B reads: 10
    // Thread A writes: 11
    // Thread B writes: 11 ← lost update! should be 12
    availableSpots++;
}
```

```java
// ✅ FIX option 1 — AtomicInteger: hardware-level atomic compare-and-swap
private final AtomicInteger availableSpots = new AtomicInteger(10);

public void releaseSpot() {
    availableSpots.incrementAndGet();
}
```

```java
// ✅ FIX option 2 — synchronized: one thread at a time
public synchronized void releaseSpot() {
    availableSpots++;
}
```

**Rule:** `AtomicInteger` wins for single-counter operations. `synchronized` is needed when you modify multiple fields together and they must stay consistent.

---

## ⚠️ Root Cause 2 — Visibility (The Silent Bug)

Even without a race condition, one thread may not see another thread's writes. The JVM allows each thread to cache variables in its CPU register. Thread A writes to a field; Thread B keeps reading its stale cached copy.

```java
// ❌ BROKEN — Thread B may never see isRunning = false
public class ElevatorLoop {

    private boolean isRunning = true;  // ← not volatile

    public void stop() {
        isRunning = false;  // Thread A writes this
    }

    public void run() {
        // Thread B may loop forever — its CPU register still holds isRunning = true
        while (isRunning) {
            processNextCommand();
        }
    }
}
```

```java
// ✅ FIX — volatile forces the write to main memory, forces reads to skip cache
public class ElevatorLoop {

    private volatile boolean isRunning = true;

    public void stop() {
        isRunning = false;  // flushed to main memory immediately
    }

    public void run() {
        while (isRunning) {  // always reads from main memory
            processNextCommand();
        }
    }
}
```

**When to use `volatile`:**
- Simple flags (`isRunning`, `isShuttingDown`) — one thread writes, others read
- The Singleton DCL pattern (`volatile DatabaseConnectionPool instance`)
- NOT for compound actions (read-modify-write still needs AtomicInteger or synchronized)

---

## ⚠️ Root Cause 3 — Deadlock

A **deadlock** (a state where two or more threads are each waiting for a lock held by the other — all blocked forever) happens when:

1. Thread A holds Lock 1, waits for Lock 2
2. Thread B holds Lock 2, waits for Lock 1
3. Neither can proceed

```java
// ❌ DEADLOCK SCENARIO — Splitwise debt settlement
// Thread A: transferring from Alice → Bob (locks Alice first, then Bob)
// Thread B: transferring from Bob → Alice (locks Bob first, then Alice)

public void transfer(Account from, Account to, long amount) {
    synchronized (from) {       // Thread A locks Alice, Thread B locks Bob
        synchronized (to) {     // Thread A waits for Bob, Thread B waits for Alice
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

### Fix — Consistent Lock Ordering

```java
// ✅ FIX — always lock accounts in the same order (by ID)
// Thread A and Thread B will both try to lock the lower-ID account first
// One will get it; the other will wait — no circular wait, no deadlock

public void transfer(Account from, Account to, long amount) {
    Account first = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;

    synchronized (first) {
        synchronized (second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

**Deadlock prevention rule:** When you need to acquire multiple locks, **always acquire them in the same global order** (e.g., by ID, by hash, by name). This eliminates circular wait — one of the four necessary conditions for deadlock.

### 🎨 Visual — Deadlock vs Ordered Lock

```
  DEADLOCK                          ORDERED LOCK
  ────────                          ────────────
  Thread A  Thread B               Thread A  Thread B
  ──────    ──────                  ──────    ──────
  lock(A)   lock(B)                lock(A)   lock(A)
            ↑ holds                          ↑ blocks (Thread A holds)
  lock(B) ← waits for B            lock(B)
  ↑ Thread A waits for B           lock(B)  (Thread A releases, B acquires)
  ↓ Thread B waits for A
  CIRCULAR WAIT → DEADLOCK         NO CIRCULAR WAIT → SAFE

KEY INVARIANT:
   Acquire locks in a globally consistent order.
   If every thread locks in the same order, circular wait is impossible.
```

---

## 🧭 Coordination Pattern 1 — wait() / notify()

When one thread needs to pause until another signals "it's ready." Classic example: producer waits if buffer is full; consumer waits if buffer is empty.

```java
// Bounded buffer — producer waits when full, consumer waits when empty
public class BoundedBuffer<T> {

    private final Queue<T> buffer = new LinkedList<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }

    // Producer calls this — blocks if buffer is full
    public synchronized void put(T item) throws InterruptedException {
        // Loop (not if!) — because spurious wakeups exist; recheck the condition
        while (buffer.size() == capacity) {
            wait();  // releases the lock and waits for notify()
        }
        buffer.add(item);
        notifyAll();  // wake consumers waiting for an item
    }

    // Consumer calls this — blocks if buffer is empty
    public synchronized T take() throws InterruptedException {
        while (buffer.isEmpty()) {
            wait();  // releases the lock and waits for notify()
        }
        T item = buffer.poll();
        notifyAll();  // wake producers waiting for space
        return item;
    }
}
```

**Rules for wait/notify (memorise these):**
1. Always call `wait()` inside a `synchronized` block
2. Always loop — `while (condition)` not `if (condition)` — because of spurious wakeups (a thread can wake up even without a `notify()`)
3. Use `notifyAll()` not `notify()` unless you're certain only one thread needs to wake up

---

## 🧭 Coordination Pattern 2 — BlockingQueue (preferred over wait/notify)

`BlockingQueue` (from `java.util.concurrent`) implements the producer-consumer pattern with `put()` and `take()` that block automatically. It's safer than manual `wait/notify` because you can't forget the loop or the synchronized block.

```java
// Producer-consumer with BlockingQueue — cleaner than wait/notify
public class TaskQueue {

    // LinkedBlockingQueue: put() blocks when full, take() blocks when empty
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(100);

    // Producer thread: submit tasks (blocks if queue is full)
    public void submit(Runnable task) throws InterruptedException {
        queue.put(task);
    }

    // Consumer thread: worker loop (blocks when queue is empty)
    public void workerLoop() throws InterruptedException {
        while (true) {
            Runnable task = queue.take();
            task.run();
        }
    }
}
```

**In LLD interviews:** Say "I'd use a `LinkedBlockingQueue` — it handles producer-consumer blocking internally, so I don't need to write `wait/notify` loops manually. Bounded capacity (`new LinkedBlockingQueue<>(100)`) provides backpressure."

---

## 🧭 Coordination Pattern 3 — Semaphore (resource pool / rate limiter)

A **semaphore** (a synchronization primitive that controls access to a shared resource by limiting how many threads can use it simultaneously — like a bouncer counting capacity at a venue) limits concurrent access to a resource.

```java
// Limit concurrent charging to 3 EV spots
public class EVChargingStation {

    // 3 permits — only 3 threads can charge simultaneously
    private final Semaphore chargingSlots = new Semaphore(3);

    public void startCharging(String vehicleId) throws InterruptedException {
        chargingSlots.acquire();  // blocks if all 3 slots are taken
        try {
            // charge the vehicle
            doCharge(vehicleId);
        } finally {
            chargingSlots.release();  // ALWAYS release in finally block
        }
    }

    private void doCharge(String vehicleId) {
        // charging logic
    }
}
```

**In LLD interviews:** "I'd use a `Semaphore(n)` to limit concurrent access. `acquire()` before the resource, `release()` in `finally`. This is the canonical resource pool — same pattern for DB connection pools, thread pools, and rate limiting."

---

## 🧭 Coordination Pattern 4 — ReadWriteLock (read-heavy scenarios)

When reads vastly outnumber writes, `synchronized` is wasteful — readers block each other unnecessarily. `ReadWriteLock` allows unlimited concurrent readers OR one exclusive writer.

```java
// Config store: read thousands of times, written rarely
public class ConfigStore {

    private final Map<String, String> config = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public String get(String key) {
        lock.readLock().lock();
        try {
            return config.get(key);  // concurrent reads are fine
        } finally {
            lock.readLock().unlock();
        }
    }

    public void set(String key, String value) {
        lock.writeLock().lock();
        try {
            config.put(key, value);  // exclusive — all readers blocked during write
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

---

## 🧭 Coordination Pattern 5 — ExecutorService (Thread Pool)

An **ExecutorService** (a managed pool of reusable worker threads — instead of creating a new `Thread` per task, you submit tasks to the pool and the pool reuses threads) is the standard way to run concurrent work in Java. Creating a new `Thread` per task is expensive and uncontrolled; the pool bounds resource usage.

```java
// Two creation patterns you'll use in LLD interviews:

// Fixed pool — N threads, excess tasks queue internally
ExecutorService pool = Executors.newFixedThreadPool(4);

// Single thread — one thread, tasks execute sequentially
// Use for the "dispatcher thread" pattern (see Job Scheduler)
ExecutorService dispatcher = Executors.newSingleThreadExecutor();
```

### Key API

```java
// submit(Runnable) — fire and forget; returns Future<?> for cancellation
pool.submit(() -> processJob(job));

// submit(Callable<T>) — fire and get result; blocks on future.get()
Future<String> result = pool.submit(() -> computeResult());
String value = result.get();   // blocks until done; throws on exception

// Graceful shutdown: stop accepting new tasks, wait for in-progress to finish
pool.shutdown();

// Forceful shutdown: interrupt all running threads, drain queue
pool.shutdownNow();
```

### 🎨 Visual — Dispatcher Thread + Worker Pool (Job Scheduler Pattern)

```
  submit(job1, priority=1)  ──▶  PriorityBlockingQueue
  submit(job2, priority=5)  ──▶  [job1, job2, ...]
  submit(job3, priority=2)  ──▶

                             single dispatcher thread
                             calls queue.take() in a loop
                                         │
                             ┌───────────▼──────────────────────┐
                             │        ExecutorService           │
                             │   (fixed pool of N workers)      │
                             │                                  │
                             │  Worker 1: executing job1        │
                             │  Worker 2: executing job3        │
                             │  Worker 3: idle                  │
                             └──────────────────────────────────┘

KEY INVARIANT:
   One dispatcher serializes the priority queue — always handing the
   highest-priority available job to the pool. Workers never pull
   from the queue directly, so priority ordering is preserved.
```

**Daemon threads:** set `thread.setDaemon(true)` on background threads (like a dispatcher) so the JVM can exit without waiting for them to finish. A non-daemon thread keeps the JVM alive.

```java
Thread dispatcher = new Thread(this::dispatchLoop, "job-dispatcher");
// Without setDaemon(true), JVM won't exit until dispatchLoop() returns
dispatcher.setDaemon(true);
dispatcher.start();
```

---

## 🧭 Coordination Pattern 6 — AtomicReference + CAS

`volatile` gives visibility (every thread sees the latest write) but NOT atomicity for compound actions. If you need "check-then-write" to be a single uninterruptible operation, use `AtomicReference.compareAndSet()` (CAS — Compare-And-Swap).

**The problem volatile cannot solve:**

```java
// ❌ BROKEN — volatile doesn't help here
// Two threads call cancel() and dispatch() simultaneously
private volatile JobStatus status = JobStatus.PENDING;

// Thread A (cancel): reads PENDING → writes CANCELLED
// Thread B (dispatch): reads PENDING → writes RUNNING
// Both succeed → job is CANCELLED AND RUNNING at the same time
public void cancel() {
    if (status == JobStatus.PENDING) {   // Thread A checks: PENDING ✓
        status = JobStatus.CANCELLED;    // Thread B also already checked: PENDING ✓
    }                                    // both writes succeed — corrupted state
}
```

**The fix — AtomicReference CAS:**

```java
// ✅ FIX — AtomicReference makes check + write one atomic operation
private final AtomicReference<JobStatus> status =
    new AtomicReference<>(JobStatus.PENDING);

// Returns true only if status was PENDING at the exact moment of the swap
// Returns false if another thread already changed it
public boolean cancel() {
    return status.compareAndSet(JobStatus.PENDING, JobStatus.CANCELLED);
}

public boolean startRunning() {
    return status.compareAndSet(JobStatus.PENDING, JobStatus.RUNNING);
}
```

### 🎨 Visual — CAS Race Resolution

```
  Memory: [PENDING]

  Thread A: cancel()                    Thread B: dispatch()
  casStatus(PENDING → CANCELLED)        casStatus(PENDING → RUNNING)
         │                                       │
         └─────────────┬─────────────────────────┘
                       │ both arrive simultaneously
                       │
               CPU arbitrates: one wins, one loses
                       │
         ┌─────────────┴──────────────┐
         │ Thread A wins              │ Thread B loses
         │ Memory: [CANCELLED]        │ reads [CANCELLED] ≠ PENDING
         │ returns true               │ returns false → no-op
         └────────────────────────────┘

KEY INVARIANT:
   Exactly one CAS winner per transition.
   The loser sees false and takes no action.
   No lock needed — the CPU's atomic exchange instruction handles it.
```

**When to use AtomicReference vs synchronized:**

| Scenario | Choose |
|---|---|
| Single-field state machine (PENDING → RUNNING) | `AtomicReference` + CAS |
| Multiple fields must change together atomically | `synchronized` block |
| Simple counter increment | `AtomicInteger.incrementAndGet()` |
| Complex invariant across 3+ fields | `synchronized` or `ReentrantLock` |

---

## 🧭 Coordination Pattern 7 — CopyOnWriteArrayList

A **CopyOnWriteArrayList** (a thread-safe list where every write operation — add, remove, set — creates a brand-new copy of the underlying array, so readers always iterate a stable snapshot) is the right choice when a list is read (iterated) far more often than it is written.

**The problem with `ArrayList + synchronized`:**

```java
// ❌ BROKEN — ConcurrentModificationException
// Thread A (publish): iterating the subscriber list
// Thread B (subscribe): adding a new subscriber while A iterates
List<Subscriber> subs = Collections.synchronizedList(new ArrayList<>());

// If you forget the synchronized(subs) wrapper on iteration, CME is thrown
for (Subscriber sub : subs) {   // ← ConcurrentModificationException if B adds during this
    sub.onMessage(message);
}
```

```java
// ✅ FIX — CopyOnWriteArrayList: iteration always sees a stable snapshot
List<Subscriber> subs = new CopyOnWriteArrayList<>();

// Thread A: iterates safely — sees [S1, S2, S3] snapshot from when iteration started
for (Subscriber sub : subs) {
    sub.onMessage(message);
}

// Thread B: subs.add(S4) creates new array [S1, S2, S3, S4] atomically
// Thread A's iteration is unaffected — it reads the old snapshot
// S4 receives the NEXT message, not the current one
```

### 🎨 Visual — Snapshot Semantics

```
  Time 0:  array reference → [S1, S2, S3]

  Thread A starts iterating:
    snapshot = current array → [S1, S2, S3]   ← locked in at iteration start

  Thread B calls add(S4):
    new array = copy([S1, S2, S3]) + S4 = [S1, S2, S3, S4]
    atomically swaps array reference

  Thread A continues iterating [S1, S2, S3]:
    S1.onMessage() → S2.onMessage() → S3.onMessage()
    NO ConcurrentModificationException

  Time 1: array reference → [S1, S2, S3, S4]
    Next publish() sees [S1, S2, S3, S4] — S4 receives future messages

KEY INVARIANT:
   Write = new array copy (O(n) cost, rare).
   Read/iterate = lock-free snapshot (O(1) cost, frequent).
   Subscriber added during publish misses the current message — correct semantics.
```

**When to use vs when NOT to:**

| Use `CopyOnWriteArrayList` when | Use `ArrayList + synchronized` when |
|---|---|
| Writes are rare (subscribe at startup) | Writes are frequent (constantly changing list) |
| Reads are very frequent (publish per event) | Read/write ratio is balanced |
| Iteration must be lock-free | O(n) write cost is unacceptable |

---

## 🧭 Coordination Pattern 8 — CountDownLatch / CyclicBarrier

### CountDownLatch — wait for N tasks to finish

A **CountDownLatch** (a one-shot gate where a thread waits at `await()` until N other threads have each called `countDown()` once — like a starter pistol that fires only after all runners are in position) is used for "fan-out then wait" scenarios.

```java
// Wait for 3 services to initialize before accepting traffic
CountDownLatch readyLatch = new CountDownLatch(3);

// Each service thread calls this when ready
public void initService(String name) {
    // ... initialization work ...
    readyLatch.countDown();   // counter: 3 → 2 → 1 → 0
}

// Main thread blocks here until all 3 services are ready
public void start() throws InterruptedException {
    readyLatch.await();   // blocks until counter reaches 0
    // now safe to accept traffic
}
```

**Key property:** `CountDownLatch` is one-shot — once the counter reaches 0, `await()` returns immediately for all future callers. You cannot reset it.

### CyclicBarrier — all threads wait for each other at a checkpoint

A **CyclicBarrier** (a reusable meeting point where N threads each call `await()` and all are blocked until all N have arrived — like a group of hikers waiting at each waypoint before moving together) is used for parallel phases where all threads must synchronize before the next phase.

```java
// 4 worker threads must all finish Phase 1 before any starts Phase 2
CyclicBarrier barrier = new CyclicBarrier(4);

// Each worker thread:
public void runWorker(int workerId) throws Exception {
    doPhase1Work(workerId);
    barrier.await();   // blocks until all 4 workers reach this line
    doPhase2Work(workerId);   // all 4 start Phase 2 at the same time
}
```

**Key property:** `CyclicBarrier` is reusable — after all N threads pass a barrier, it resets automatically for the next phase.

### 🎨 Visual — Latch vs Barrier

```
  COUNTDOWNLATCH (one-shot)          CYCLICBARRIER (reusable)

  Main   W1   W2   W3                W1    W2    W3    W4
  ──── ────  ────  ────               ────  ────  ────  ────
  await()                            phase1 phase1 phase1 phase1
  │     init  init  init                │     │     │     │
  │     │     │     │                   │     │     │     │
  │   count count count              barrier.await() ×4
  │   Down() Down() Down()              │     │     │     │
  │     ▼     ▼     ▼                   └─────┴─────┴─────┘
  ▼   counter reaches 0                       │ all arrived
  unblocks                              all released together
                                       phase2 phase2 phase2 phase2

KEY INVARIANT:
   Latch: M waiters, N workers; workers count down independently.
   Barrier: all N threads are both workers AND waiters — they sync with each other.
```

| Use `CountDownLatch` when | Use `CyclicBarrier` when |
|---|---|
| One thread waits for N workers | N threads wait for each other |
| One-shot (init, startup) | Repeating phases (parallel map-reduce) |
| Workers don't need to sync with each other | All participants must reach checkpoint together |

---

## 🧾 Interview Answer Templates

### "How do you handle concurrent seat booking?"

> *"Two users booking seat A1 at the same time is a check-then-act race. The check (`seatStatus == AVAILABLE`) and the act (`seatStatus = BOOKED`) must be atomic. I'd synchronize `bookSeat()` — or use `synchronized` on the specific seat object for per-seat granularity rather than a global lock. For higher throughput, optimistic locking: CAS on seat status with `AtomicReference<SeatStatus>`, retry if the swap fails."*

### "What's the difference between synchronized and ReentrantLock?"

> *"Both provide mutual exclusion. `synchronized` is simpler — the JVM releases the lock when the block exits, even on exception. `ReentrantLock` adds: `tryLock(timeout)` (don't block forever), fair ordering (`new ReentrantLock(true)`), and `lockInterruptibly()` (cancel the wait on thread interrupt). I default to `synchronized`; I reach for `ReentrantLock` when I need try-with-timeout or fairness."*

### "Two threads both see availableSpots = 1 and both try to park. What breaks?"

> *"Classic read-modify-write race. Both read 1, both decrement to 0, both assign 0 — but two spots were allocated. Fix: `AtomicInteger` with `decrementAndGet()` — if it goes negative, increment back and return failure. Or synchronize the entire `parkVehicle()` method. I prefer the `AtomicInteger` approach for a single counter because it's lock-free and higher throughput."*

### "How does your cancel() prevent a job from being both cancelled and executed?"

> *"Both cancel() and the dispatcher compete to transition the job from PENDING to their target state. I use `AtomicReference<JobStatus>.compareAndSet(PENDING, target)`. CAS is atomic — the CPU handles the read-compare-write as one uninterruptible instruction. Exactly one caller wins; the other sees false and takes no action. No lock is needed because there's only one field transitioning and no multi-field invariant to maintain."*

### "Why CopyOnWriteArrayList for subscribers instead of synchronized?"

> *"publish() iterates the subscriber list on every event — potentially thousands of times per second. If I use ArrayList + synchronized, every publish() call acquires the lock, creating contention under high event rate. CopyOnWriteArrayList makes iteration completely lock-free — publish() reads a stable snapshot with no lock. The tradeoff is that subscribe() copies the array on write, which is O(n). Since subscribes happen once at startup but publish() runs constantly, the read-heavy tradeoff is correct."*

---

## 🧾 TL;DR — The Rules That Cover 90% of Interview Concurrency Questions

> **Rule 1 — Identify shared mutable state first.** Ask: "what fields does more than one thread read AND write?" Those fields are the danger zones. Everything else is safe.
>
> **Rule 2 — Lock the minimum scope that covers the danger zone.** A global `synchronized` on every method is correct but kills throughput. Per-object locking (one lock per seat, not one for the whole booking service) scales much better. Atomics for single values, synchronized for compound actions.
>
> **Rule 3 — Match the tool to the access pattern.**
> - Single-value state machine → `AtomicReference` + CAS
> - Read-heavy list (publish/subscribe) → `CopyOnWriteArrayList`
> - Producer-consumer → `BlockingQueue`
> - Thread pool + task queue → `ExecutorService`
> - Wait for N tasks to finish → `CountDownLatch`
> - N threads sync at a checkpoint → `CyclicBarrier`
> - Resource pool limit → `Semaphore`
> - Read-heavy map → `ConcurrentHashMap`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers race conditions, visibility, deadlock, wait/notify, BlockingQueue, Semaphore, ReadWriteLock — the depth that java-building-blocks-for-lld.md intentionally omits. |
| Jul 2026 | **Part 2 added** — 4 new coordination patterns: ExecutorService/thread pool (dispatcher+worker pattern), AtomicReference+CAS (single-field state machine), CopyOnWriteArrayList (snapshot semantics for pub-sub), CountDownLatch/CyclicBarrier (multi-phase coordination). Triggered by Job Scheduler and Pub-Sub notes using these primitives without coverage in this file. TL;DR Rule 3 (match tool to access pattern) added. 2 new interview answer templates added (cancel() CAS race, CopyOnWriteArrayList vs synchronized). |
