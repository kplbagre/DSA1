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

## 🧾 Interview Answer Templates

### "How do you handle concurrent seat booking?"

> *"Two users booking seat A1 at the same time is a check-then-act race. The check (`seatStatus == AVAILABLE`) and the act (`seatStatus = BOOKED`) must be atomic. I'd synchronize `bookSeat()` — or use `synchronized` on the specific seat object for per-seat granularity rather than a global lock. For higher throughput, optimistic locking: CAS on seat status with `AtomicReference<SeatStatus>`, retry if the swap fails."*

### "What's the difference between synchronized and ReentrantLock?"

> *"Both provide mutual exclusion. `synchronized` is simpler — the JVM releases the lock when the block exits, even on exception. `ReentrantLock` adds: `tryLock(timeout)` (don't block forever), fair ordering (`new ReentrantLock(true)`), and `lockInterruptibly()` (cancel the wait on thread interrupt). I default to `synchronized`; I reach for `ReentrantLock` when I need try-with-timeout or fairness."*

### "Two threads both see availableSpots = 1 and both try to park. What breaks?"

> *"Classic read-modify-write race. Both read 1, both decrement to 0, both assign 0 — but two spots were allocated. Fix: `AtomicInteger` with `decrementAndGet()` — if it goes negative, increment back and return failure. Or synchronize the entire `parkVehicle()` method. I prefer the `AtomicInteger` approach for a single counter because it's lock-free and higher throughput."*

---

## 🧾 TL;DR — The Two Rules That Cover 80% of Interview Concurrency Questions

> **Rule 1 — Identify shared mutable state first.** Ask: "what fields does more than one thread read AND write?" Those fields are the danger zones. Everything else is safe.
>
> **Rule 2 — Lock the minimum scope that covers the danger zone.** A global `synchronized` on every method is correct but kills throughput. Per-object locking (one lock per seat, not one for the whole booking service) scales much better. Atomics for single values, synchronized for compound actions.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers race conditions, visibility, deadlock, wait/notify, BlockingQueue, Semaphore, ReadWriteLock — the depth that java-building-blocks-for-lld.md intentionally omits. |
