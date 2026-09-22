# 🧵 synchronized + volatile — Deep Dive

> After this note you can explain the 3 guarantees `synchronized` provides that `volatile` doesn't, what `monitorenter`/`monitorexit` bytecode instructions are, when to use `volatile` vs `synchronized` vs `AtomicInteger`, and why `volatile long` matters on 32-bit JVMs.

---

## 🎯 The Problem This Solves

Two threads increment the same counter. Without synchronization, the final count is wrong — not occasionally, but predictably. `count++` is 3 operations (read, add, write), and two threads can interleave them. You need a mechanism that makes compound operations atomic AND makes writes visible across threads. `synchronized` does both. `volatile` does only visibility. Choosing wrong = silent data corruption or unnecessary performance cost.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Monitor** | Every Java object has an intrinsic monitor (also called intrinsic lock). `synchronized` acquires the object's monitor. Only one thread can hold a monitor at a time. |
| **Mutual exclusion** | Only one thread executes the synchronized block at a time. All other threads attempting to enter block on the same monitor are suspended until it's released. |
| **Reentrancy** | A thread that already holds a monitor can re-enter synchronized blocks guarded by the SAME monitor without deadlocking. The monitor tracks the holding thread and increments an entry count. |
| **Lock coarsening** | JIT optimization: if multiple adjacent synchronized blocks use the same lock, the JIT merges them into one larger block to reduce lock/unlock overhead. |
| **Lock elision (biased locking)** | JIT optimization: if a lock is only ever acquired by one thread, the JVM eliminates the lock overhead entirely. (Biased locking was deprecated in Java 15, disabled by default in Java 18.) |
| **CAS (Compare-And-Swap)** | A CPU instruction that atomically reads a value, compares it to an expected value, and writes a new value ONLY if the current value matches the expected. Foundation of `AtomicInteger`, `AtomicReference`, and lock-free algorithms. |

---

## 🧠 Mental Model

Think of `synchronized` as a **bathroom with a lock on the door**. Only one person can be inside (mutual exclusion). When they leave (unlock), the room is clean — everything they changed is visible (visibility). If the same person needs to go back in immediately, the lock recognizes them (reentrancy).

`volatile` is a **shared whiteboard** — whenever you write to it, the update is immediately visible to everyone. But there's no lock on the whiteboard. Two people can read the same number, both add 1, and both write the same result — the increment is lost.

`AtomicInteger` is a **whiteboard with a compare-and-swap mechanism** — when you want to update, you read the current value, compute the new value, and write ONLY if nobody changed it in between. If someone did, you retry. No lock, but still correct.

> If you can say "synchronized = mutual exclusion + visibility + ordering (monitor enter/exit); volatile = visibility + ordering only (no mutual exclusion); AtomicInteger = atomic compound operations via CAS (lock-free)" without notes, you have the trio.

---

## 🎨 Visual — synchronized vs volatile vs Atomic

```
  ┌─────────────────────────────────────────────────────────────┐
  │                    GUARANTEE MATRIX                         │
  ├──────────────────┬──────────┬──────────┬───────────────────┤
  │                  │ Visibility│ Atomicity │ Ordering         │
  │                  │ (happens- │ (mutual   │ (prevents        │
  │                  │  before)  │ exclusion)│  reordering)     │
  ├──────────────────┼──────────┼──────────┼───────────────────┤
  │ no sync          │    ❌     │    ❌     │    ❌             │
  │ volatile         │    ✅     │    ❌*    │    ✅             │
  │ synchronized     │    ✅     │    ✅     │    ✅             │
  │ AtomicInteger    │    ✅     │    ✅**   │    ✅             │
  │ ReentrantLock    │    ✅     │    ✅     │    ✅             │
  └──────────────────┴──────────┴──────────┴───────────────────┘

  * volatile: individual reads/writes ARE atomic for reference types
    and primitives ≤ 32 bits. But compound ops (read-modify-write
    like i++) are NOT atomic.

  ** AtomicInteger: compound ops (incrementAndGet, compareAndSet)
     ARE atomic via CAS. But sequences of operations are NOT
     (check-then-act still requires external sync).

KEY INVARIANT:
   WHEN TO USE EACH:
   - Simple flag (boolean): volatile
   - Single counter: AtomicInteger
   - Check-then-act or multiple variables: synchronized or Lock
   - Read-heavy, write-rare: ReadWriteLock or StampedLock
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Tier 1 — Demo: unsynchronized counter
public class UnsafeCounter {
    private int count = 0;

    public void increment() {
        count++;   // NOT atomic: read(0) → add(1) → write(1)
        // Two threads can both read 0, both write 1 → count is 1, should be 2
    }

    public int getCount() {
        return count;   // may see stale value (no visibility guarantee)
    }
}
// ⚠️ NOT thread-safe — data race on 'count'

// Running 2 threads × 1,000,000 increments each:
// Expected: 2,000,000
// Actual:   ~1,200,000–1,900,000 (varies per run — non-deterministic)
```

---

### Level 2 — The real mechanism

#### 2.1 — `synchronized` blocks and methods

```java
// Tier 2 — Production: synchronized counter
public class SafeCounter {
    private int count = 0;

    public synchronized void increment() {
        count++;   // only one thread at a time — read+add+write is atomic within the lock
    }

    public synchronized int getCount() {
        return count;   // acquires same monitor → sees latest write
    }
}
// ✅ Thread-safe — mutual exclusion on 'this' monitor

// Equivalent explicit block form:
public void increment() {
    synchronized (this) {   // acquire monitor on 'this'
        count++;
    }                       // release monitor on 'this'
}

// Using a private lock object (preferred — prevents external code from locking on 'this'):
public class BetterCounter {
    private final Object lock = new Object();
    private int count = 0;

    public void increment() {
        synchronized (lock) {
            count++;
        }
    }

    public int getCount() {
        synchronized (lock) {
            return count;
        }
    }
}
```

> **What the JVM is actually doing:** The compiler emits `monitorenter` and `monitorexit` bytecode instructions. `monitorenter` attempts to acquire the object's monitor — if uncontested, it sets the monitor's owner to the current thread and entry count to 1. If contested (another thread holds it), the current thread is suspended and placed on the monitor's wait queue. `monitorexit` decrements the entry count — when it reaches 0, the monitor is released and the JVM wakes one waiting thread. On modern JVMs (HotSpot), uncontested locks use **thin locks** (a CAS on the object header's mark word) — no OS thread suspension. Contested locks escalate to **fat locks** (OS mutex) — actual thread parking via the OS scheduler.

```
  Object header (mark word) — 64 bits on 64-bit JVM:

  UNLOCKED:       [hashCode | age | 0 | 01]       ← last 2 bits = lock state
  BIASED:         [threadId | epoch | age | 1 | 01] ← biased to one thread
  THIN LOCK:      [pointer to lock record | 00]    ← CAS-based, no OS involvement
  FAT LOCK:       [pointer to monitor | 10]        ← OS mutex, thread parking
  GC MARK:        [forwarding pointer | 11]        ← during GC

  Progression: unlocked → thin (CAS contention) → fat (spinning fails)
  This is why uncontested synchronized is nearly free — just a CAS on the header.
```

#### 2.2 — `volatile` fields

```java
// Tier 2 — Production: volatile flag for thread communication
public class Worker {
    private volatile boolean running = true;

    public void stop() {
        running = false;   // volatile write → visible to all threads immediately
    }

    public void run() {
        while (running) {   // volatile read → always reads latest value
            processItem();
        }
    }
}
// ✅ Thread-safe for this pattern — single writer, single flag

// ❌ volatile does NOT help here:
public class BrokenCounter {
    private volatile int count = 0;

    public void increment() {
        count++;   // STILL a race condition!
        // volatile read: count = 5
        // another thread writes: count = 6
        // this thread writes: count = 6 (should be 7) → LOST UPDATE
    }
}
```

**volatile guarantees for 64-bit primitives:**

```java
// On 32-bit JVMs, reading/writing long and double is NOT atomic without volatile.
// The JVM may split a 64-bit write into two 32-bit writes.
// Thread A writes the upper 32 bits, Thread B reads between the two writes →
// B sees upper 32 bits from A's write + lower 32 bits from the old value → garbage.

// volatile long / volatile double → atomic read and write on ALL JVMs (32-bit included)
private volatile long timestamp;   // guaranteed atomic read/write

// On 64-bit JVMs: long/double are typically atomic anyway (hardware).
// But the JLS only GUARANTEES atomicity for volatile longs/doubles.
// Writing portable code: always use volatile for shared long/double fields.
```

#### 2.3 — `AtomicInteger` and CAS

```java
// Tier 2 — Production: lock-free counter
public class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();
        // Internally:
        // do {
        //     int current = count.get();        // read
        //     int next = current + 1;            // compute
        // } while (!count.compareAndSet(current, next));  // CAS: write only if still 'current'
        // If another thread changed 'count' between get() and CAS → CAS fails → retry
        // No lock. No blocking. Just retry.
    }

    public int getCount() {
        return count.get();   // volatile read semantics
    }
}
// ✅ Thread-safe — CAS-based, lock-free, no context switching

// Other useful AtomicInteger methods:
count.addAndGet(5);          // atomically add 5
count.getAndIncrement();     // return old value, then increment
count.compareAndSet(10, 20); // set to 20 ONLY if currently 10
count.updateAndGet(n -> n * 2);  // atomic function application (Java 8+)
```

**When CAS loses to synchronized:**

CAS is lock-free but not wait-free — under extreme contention (100 threads incrementing the same AtomicInteger), CAS retries create a spin loop. Each retry wastes a CPU cycle. At high contention, `LongAdder` (Java 8) splits the counter across multiple cells — each thread increments its own cell, and `sum()` adds them up. Much less contention at the cost of eventual consistency for reads.

```java
// Tier 2 — Production: LongAdder for high-contention counters
LongAdder requestCounter = new LongAdder();
requestCounter.increment();   // no global CAS contention — per-stripe increment
long total = requestCounter.sum();   // aggregates all stripes
// ✅ Thread-safe — designed for high-contention write-heavy counters
// Use for metrics/counters. Don't use for AtomicInteger's compareAndSet patterns.
```

---

### Level 3 — The subtleties

#### 3.1 — Reentrancy

```java
// A thread can re-enter a synchronized block guarded by the SAME monitor:
public class ReentrantDemo {
    public synchronized void outerMethod() {
        innerMethod();   // same monitor (this) → same thread re-enters → no deadlock
    }

    public synchronized void innerMethod() {
        // runs fine — monitor entry count is now 2
    }
}
// Without reentrancy: outerMethod holds the lock, innerMethod tries to acquire
// the SAME lock → deadlock with itself. Reentrancy prevents this.
```

#### 3.2 — synchronized and wait/notify

```java
// wait(), notify(), notifyAll() MUST be called inside synchronized on the SAME object:
synchronized (lock) {
    while (!condition) {   // WHILE, not if — spurious wakeups
        lock.wait();       // releases the monitor, suspends thread
        // when notified: re-acquires monitor, re-checks condition
    }
    // condition is true — proceed
}

// In another thread:
synchronized (lock) {
    condition = true;
    lock.notifyAll();   // wake ALL waiting threads
    // Each woken thread re-acquires the monitor and re-checks the condition
}

// ⚠️ Why WHILE not IF: Java allows "spurious wakeups" — a thread can
// wake from wait() without anyone calling notify(). The while loop
// re-checks the condition, handling both real and spurious wakeups.
```

#### 3.3 — Static synchronized methods

```java
// synchronized on an instance method → monitor is 'this' (the instance)
public synchronized void instanceMethod() { ... }

// synchronized on a static method → monitor is the CLASS object (MyClass.class)
public static synchronized void staticMethod() { ... }

// These are DIFFERENT monitors!
// Thread A in instanceMethod() does NOT block Thread B in staticMethod()
// They're synchronized on different objects: the instance vs the Class.
```

#### 3.4 — The decision matrix

```
  Use volatile when:
  - One writer, multiple readers
  - Single variable (flag, reference, timestamp)
  - No compound operations (no check-then-act, no read-modify-write)

  Use AtomicInteger/AtomicReference when:
  - Multiple writers need atomic compound operations
  - Single variable at a time
  - Lock-free performance matters (moderate contention)

  Use LongAdder when:
  - Write-heavy counter (metrics, request counts)
  - Read frequency is much lower than write frequency
  - High contention expected

  Use synchronized when:
  - Multiple variables must be updated atomically together
  - Check-then-act patterns (check condition, then modify state)
  - Need wait/notify for inter-thread coordination

  Use ReentrantLock when:
  - Need tryLock (non-blocking attempt), timeout, interruptibility
  - Need fairness (FIFO ordering)
  - Need multiple Condition objects
  (See Note #17 — Locks deep-dive)
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "`volatile` makes compound operations atomic" | `volatile` makes individual reads/writes visible. `count++` (read-add-write) on a volatile field is still a race condition. Use `AtomicInteger` or `synchronized` for compound operations. |
| "`synchronized` is slow and should be avoided" | Uncontested `synchronized` is nearly free on modern JVMs — just a CAS on the object header (thin lock). It only becomes expensive under heavy contention (fat lock → OS mutex → thread parking). Premature optimization away from `synchronized` causes more bugs than it solves. |
| "You need `synchronized` for reading shared state" | You need SOME happens-before guarantee for reads — `synchronized`, `volatile`, or `Atomic`. If the writer uses `synchronized`, the reader MUST also use `synchronized` on the SAME lock. A `synchronized` write with a non-synchronized read is still a data race. |
| "CAS is always faster than locking" | Under high contention, CAS spin-loops waste CPU. `LongAdder` splits the contention. Under extreme contention with complex critical sections, `synchronized` with OS-level parking (fat lock) actually burns less CPU than CAS retries. Profile before choosing. |
| "`synchronized(new Object())` protects a block" | Synchronizing on a new object every time creates a DIFFERENT monitor each time — no mutual exclusion. The lock must be a SHARED object visible to all threads (a field, not a local variable). |

---

## 🐞 Production Footguns

---

> **Footgun: Synchronizing on a boxed type or String**
> **Cost:** Unexpected lock contention / deadlock
>
> A service synchronized on an `Integer` field: `synchronized(userId)`. Due to Integer caching (-128 to 127), two different parts of the application using the same user ID got the same `Integer` object from the cache — they were unknowingly synchronizing on the same monitor. One held the lock while waiting for a response the other needed to produce → deadlock.

```java
// ❌ The trap: synchronizing on cached/shared objects
private final Integer userId = 42;

public void processUser() {
    synchronized (userId) {   // Integer cache: ALL Integer(42) references are the SAME object
        // Any other code synchronizing on Integer(42) shares this lock!
    }
}

// Same problem with String.intern() — interned strings share references

// ✅ The fix: use a private dedicated lock object
private final Object userLock = new Object();

public void processUser() {
    synchronized (userLock) {
        // private lock — no one else can accidentally share it
    }
}
```

---

> **Footgun: Reading without the same lock**
> **Cost:** Stale data (silent bug)
>
> A Spring singleton had a `synchronized` write method and an unsynchronized read method. The writer used `synchronized(lock)` to update a cache map. The reader returned the map value directly — no synchronization. Under the JMM, the reader had no happens-before relationship with the writer — it saw stale cached entries indefinitely. The bug was intermittent (depended on CPU cache flushing timing) and was only caught by a load test that ran for 4+ hours.

```java
// ❌ The trap: synchronized write, unsynchronized read
public class ConfigCache {
    private final Map<String, String> cache = new HashMap<>();
    private final Object lock = new Object();

    public void update(String key, String value) {
        synchronized (lock) {
            cache.put(key, value);   // write with lock
        }
    }

    public String get(String key) {
        return cache.get(key);   // ❌ read WITHOUT lock — no happens-before
        // May see stale data indefinitely
    }
}

// ✅ The fix: read with the same lock
public String get(String key) {
    synchronized (lock) {
        return cache.get(key);   // lock acquire → sees latest writes
    }
}

// ✅ Better fix: use ConcurrentHashMap (designed for concurrent read/write)
private final Map<String, String> cache = new ConcurrentHashMap<>();
// No explicit locking needed — ConcurrentHashMap provides its own happens-before
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `java-memory-model.md` | JMM defines happens-before; `synchronized` establishes it via monitor lock/unlock (Rule 2); `volatile` establishes it via write/read (Rule 3). This note is the practical application of JMM theory. |
| `locks-reentrant-readwrite.md` (planned — Note #17) | `ReentrantLock` provides the same guarantees as `synchronized` plus: tryLock, timeout, fairness, multiple Conditions. This note covers WHEN to upgrade from synchronized to Lock. |
| `hashmap-internals.md` | HashMap is NOT thread-safe — `synchronized` or ConcurrentHashMap is needed for shared maps. Understanding the lock mechanism explains WHY HashMap corrupts under concurrent access. |
| `concurrent-collections.md` (planned — Note #18) | ConcurrentHashMap uses CAS + per-bin synchronized — the techniques from this note applied to a production data structure. |
| `completable-future.md` | CompletableFuture stages may run on different threads. The framework provides happens-before between stages internally. Understanding synchronized/volatile explains what "thread-safe callback" means. |

---

## 🎙️ Interview Deep Questions

**Q1. What are the 3 guarantees `synchronized` provides?**

> Mutual exclusion (atomicity) — only one thread executes the block at a time. Visibility — on lock release, all writes are flushed to main memory; on lock acquire, all reads come from main memory. Ordering — instructions inside the synchronized block are not reordered with instructions outside it (happens-before). `volatile` provides visibility and ordering but NOT mutual exclusion — that's the fundamental difference. Use `synchronized` when you need atomicity for compound operations (check-then-act, read-modify-write on multiple variables). Use `volatile` when you need visibility for a single variable with no compound operations.

**Q2. How does `synchronized` work at the JVM/bytecode level?**

> The compiler emits `monitorenter` (acquire) and `monitorexit` (release) bytecode instructions. Every Java object has an intrinsic monitor in its object header (mark word — 64 bits). Uncontested: the JVM uses a thin lock — a single CAS on the mark word to record the owning thread. If another thread tries to acquire while held: the JVM spins briefly (adaptive spinning), then escalates to a fat lock — allocates an OS mutex, parks the waiting thread via the OS scheduler. The monitor tracks the holding thread and entry count (for reentrancy). On release: entry count decrements; at 0, the monitor is freed and one waiting thread is unparked.

**Q3. When should you use `AtomicInteger` vs `synchronized` vs `volatile`?**

> `volatile`: single variable, one writer, no compound operations. Example: a boolean shutdown flag. `AtomicInteger`: single counter or reference, multiple writers, compound operations (increment, CAS). Example: request counter, lock-free sequence generator. `synchronized`: multiple variables that must be updated atomically together, or check-then-act patterns (if map doesn't contain key → put). Example: maintaining two related fields in a consistent state. Under extreme write contention, `LongAdder` beats `AtomicInteger` by splitting the counter across CPU-local stripes — each thread increments its own stripe, and `sum()` aggregates.

**Q4. Can you synchronize on `null`? On a `String` literal? What are the risks?**

> Synchronizing on `null` throws `NullPointerException` at the `monitorenter` instruction. Synchronizing on a `String` literal (`synchronized("lock")`) is dangerous because the JVM interns string literals — ALL `"lock"` literals across the entire application resolve to the same String object. Two unrelated classes both synchronizing on `"lock"` unknowingly share the same monitor → unintended contention or deadlock. Same risk with Integer caching (-128 to 127) — `synchronized(Integer.valueOf(42))` shares the monitor with ANY code using `Integer(42)`. Always synchronize on a `private final Object` lock field that only your class can access.

**Q5. What is lock coarsening and lock elision? How does the JIT optimize synchronized?**

> Lock coarsening: if the JIT sees multiple adjacent synchronized blocks on the same lock with no meaningful work between them, it merges them into one larger block — reducing lock/unlock overhead. Lock elision (via escape analysis): if the JIT proves that a lock object never escapes the current thread (e.g., `synchronized(new Object()) { ... }` — though pointless, or a local `StringBuffer`), it eliminates the lock entirely. Biased locking (deprecated Java 15, removed Java 18): if only one thread ever acquires a lock, the JVM biases it to that thread — subsequent acquisitions are a no-op (just check the bias in the mark word). These optimizations mean uncontested `synchronized` in hot code paths is nearly zero-cost on modern JVMs.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** `synchronized` provides mutual exclusion + visibility + ordering. `volatile` provides visibility + ordering only. `AtomicInteger` provides lock-free atomic compound operations via CAS.
>
> **Part 2 — How/Why (30s):** `synchronized` emits `monitorenter`/`monitorexit` bytecodes. Uncontested: thin lock (CAS on object header — nearly free). Contested: fat lock (OS mutex — thread parking). `volatile` emits memory barriers — store-load on write, load-load on read. No mutual exclusion, so `count++` on volatile is still a race. `AtomicInteger.incrementAndGet()` uses CPU CAS: read-compare-write in one atomic instruction, retry on contention. Under high contention, `LongAdder` splits across CPU stripes — dramatically reduces CAS retries.
>
> **Part 3 — Gotcha (20s):** Two traps: synchronizing on a shared/cached object (Integer cache, String intern) — unrelated code unknowingly shares the monitor → deadlock. And synchronized write with unsynchronized read — no happens-before → reader sees stale data forever. Both reader and writer must use the same synchronization mechanism (same lock, or ConcurrentHashMap).

---

## 🧾 TL;DR

- **`synchronized`** = mutual exclusion + visibility + ordering. Uses object's intrinsic monitor.
- **`volatile`** = visibility + ordering. NO mutual exclusion. `count++` on volatile is still a race.
- **`AtomicInteger`** = atomic compound ops via CAS. Lock-free. Use for single counters.
- **`LongAdder`** = stripe-split counter for extreme write contention. Better than AtomicInteger at high throughput.
- Uncontested `synchronized` = thin lock (CAS) = nearly free. Contested = fat lock (OS mutex).
- Readers MUST use the same lock as writers — unsynchronized reads see stale data.
- Never synchronize on String literals, boxed primitives, or new Object() — use `private final Object lock`.
- For single flag/reference: volatile. Single counter: Atomic. Multiple variables: synchronized.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #16 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: monitorenter/monitorexit bytecode, object header mark word (thin lock → fat lock escalation, biased locking), volatile memory barriers (x86 store-load), CAS internals (AtomicInteger retry loop), LongAdder stripe-split design, reentrancy mechanics, wait/notify with spurious wakeups, static vs instance synchronized (different monitors), decision matrix (volatile vs Atomic vs synchronized vs Lock). Two production footguns: Integer cache shared monitor, synchronized write with unsynchronized read. |
