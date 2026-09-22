# 🧵 Java Memory Model (JMM) — Deep Dive

> After this note you can explain happens-before in 60 seconds, draw the CPU-cache-to-main-memory visibility problem, name 6 happens-before rules from the JLS, and explain why `volatile` solves the visibility problem but not atomicity.

---

## 🎯 The Problem This Solves

Thread A writes `flag = true`. Thread B reads `flag`. Thread B sees `false` — even though A wrote `true` BEFORE B read. No bug in the code. No race condition in the logic. The problem is hardware: modern CPUs have per-core caches, store buffers, and instruction reordering — a write by one core is NOT instantly visible to another core. Without rules governing when writes become visible across threads, multithreaded Java programs would be non-deterministic at the hardware level.

The Java Memory Model (JMM) is the contract between your code and the JVM. It defines exactly WHEN a write by one thread is guaranteed to be visible to a read by another thread. Without understanding the JMM, every multithreaded program you write is correct by accident, not by design.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Java Memory Model (JMM)** | The specification (JLS §17.4) that defines how threads interact through memory. It defines visibility guarantees, ordering guarantees, and the rules under which one thread's writes are guaranteed visible to another thread's reads. |
| **Main memory** | The conceptual shared memory that all threads can access. Corresponds roughly to RAM. JMM abstracts over the actual hardware memory hierarchy (L1/L2/L3 caches, store buffers). |
| **Working memory** | Each thread's local view of memory — conceptually includes CPU registers and caches. A thread may operate on a cached copy of a variable without writing it back to main memory immediately. |
| **Visibility** | The guarantee that when thread A writes to a variable, thread B can see the new value. Without explicit synchronization, there is NO visibility guarantee — B may see a stale cached value indefinitely. |
| **Ordering** | The guarantee that operations execute in a predictable sequence. CPUs and compilers may reorder instructions for performance. The JMM defines which reorderings are allowed and which are forbidden. |
| **Happens-before** | The central concept of the JMM. If action A happens-before action B, then A's effects (writes) are guaranteed visible to B, and A is ordered before B. If there is no happens-before relationship, there are NO guarantees — the JVM and CPU may reorder freely. |
| **Data race** | When two threads access the same variable, at least one is a write, and there is no happens-before ordering between them. A program with a data race has undefined behavior under the JMM — any result is legal. |
| **Sequential consistency** | A hypothetical execution model where all operations across all threads appear to execute in a single global order. The JMM does NOT guarantee sequential consistency for programs with data races — only for data-race-free programs. |
| **Memory barrier (fence)** | A CPU instruction that forces ordering constraints. `volatile` writes emit a store barrier; `volatile` reads emit a load barrier. `synchronized` emits barriers on both entry (acquire) and exit (release). You don't write barriers directly — the JMM rules (via volatile/synchronized) emit them for you. |

---

## 🧠 Mental Model

Imagine each thread works at its own desk with its own **notepad** (working memory / CPU cache). Main memory is a **shared whiteboard** on the wall. When thread A writes `x = 5` on its notepad, thread B's notepad might still say `x = 0` — B hasn't looked at the whiteboard yet, and A hasn't posted its update. There's no automatic sync.

**Happens-before** is a **handoff protocol**: it guarantees that when A performs a synchronization action (releasing a lock, writing to `volatile`, starting a thread), everything A wrote BEFORE that action is posted to the whiteboard AND visible to any thread that performs the corresponding action (acquiring the same lock, reading the same `volatile`, etc.).

Without happens-before, your reads are reading from each thread's private notepad — stale, cached, possibly reordered values. With happens-before, you're reading from the whiteboard — fresh, ordered, correct.

> If you can say "happens-before is the guarantee that one thread's writes are visible to another thread's reads; it's established by synchronized (lock release → lock acquire), volatile (write → read), thread start/join, and final fields after construction; without happens-before, there are NO visibility guarantees" without notes, you have the JMM.

---

## 🎨 Visual — The Visibility Problem

```
  WITHOUT happens-before:

  Thread A (Core 0)              Thread B (Core 1)
  ┌──────────────────┐           ┌──────────────────┐
  │ CPU Cache:       │           │ CPU Cache:       │
  │   flag = false   │           │   flag = false   │
  │   data = 0       │           │   data = 0       │
  └──────────────────┘           └──────────────────┘
           │                              │
  A writes: data = 42                     │
  A writes: flag = true                   │
           │                              │
           │  (writes may stay in         │
           │   A's cache / store buffer   │
           │   — NOT flushed to main mem) │
           │                              │
           │                     B reads: flag → false (stale!)
           │                     B never enters the if block
           │                     B never sees data = 42

  MAIN MEMORY: flag = false, data = 0
  (A's writes haven't propagated — perfectly legal without happens-before)


  WITH happens-before (volatile flag):

  Thread A                       Thread B
  data = 42                      │
  flag = true  ────volatile write────►  flag == true  ← volatile read
                 happens-before          │
                 GUARANTEES:             B sees data = 42
                 all writes by A         (everything A wrote BEFORE
                 before the volatile     the volatile write is visible
                 write are visible       to B after the volatile read)
                 to B after B reads
                 the volatile

KEY INVARIANT:
   Happens-before is TRANSITIVE.
   If A happens-before B, and B happens-before C, then A happens-before C.
   This is how volatile/synchronized create visibility chains.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Tier 1 — Demo: the classic visibility bug
public class StopThread {
    private static boolean stopRequested = false;   // no volatile, no synchronized

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            int i = 0;
            while (!stopRequested) {   // may read stale cached value FOREVER
                i++;
            }
            System.out.println("Stopped at i=" + i);
        });
        worker.start();

        Thread.sleep(1000);
        stopRequested = true;   // written by main thread
        // Worker thread MAY NEVER SEE this write.
        // JIT can hoist the read out of the loop:
        //   if (!stopRequested) { while(true) { i++; } }
        // Legal optimization — no happens-before → no visibility guarantee
    }
}
// Result: worker thread runs forever on some JVMs/hardware.
// No exception. No error. Just an infinite loop.
```

This is not a hypothetical — HotSpot JIT on server-mode JVMs routinely hoists non-volatile reads out of loops. The optimization is legal because there's no happens-before between the main thread's write and the worker's read.

---

### Level 2 — The real mechanism

#### 2.1 — The 6 happens-before rules (JLS §17.4.5)

These are the ONLY ways to establish happens-before. If your code doesn't use one of these, there is NO guarantee.

| # | Rule | What it means |
|---|---|---|
| 1 | **Program order** | Within a single thread, each action happens-before every subsequent action in that thread's program order. (This does NOT mean instructions execute in order — it means the RESULTS are consistent with program order.) |
| 2 | **Monitor lock** | An unlock on a monitor happens-before every subsequent lock on that same monitor. (Release → acquire on the SAME lock.) |
| 3 | **Volatile** | A write to a volatile variable happens-before every subsequent read of that same volatile variable. |
| 4 | **Thread start** | A call to `Thread.start()` happens-before any action in the started thread. |
| 5 | **Thread join** | Any action in a thread happens-before `Thread.join()` returns in the joining thread. |
| 6 | **Transitivity** | If A happens-before B, and B happens-before C, then A happens-before C. |

**Additional rules:** constructor completion happens-before finalizer start; actions in a thread happen-before that thread is detected to have terminated; interrupting a thread happens-before the interrupted thread detects the interrupt.

#### 2.2 — How `volatile` creates happens-before

```java
// Tier 1 — Demo: volatile fixes the visibility bug
public class StopThread {
    private static volatile boolean stopRequested = false;
    //                   ^^^^^^^^ volatile — every write is visible to every read

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            int i = 0;
            while (!stopRequested) {
                i++;
            }
            System.out.println("Stopped at i=" + i);
        });
        worker.start();

        Thread.sleep(1000);
        stopRequested = true;
        // volatile write happens-before volatile read (Rule 3)
        // Worker sees true → loop exits → program terminates correctly
    }
}
```

> **What the JVM is actually doing:** A `volatile` write emits a **store-load barrier** (StoreStore + StoreLoad on x86). This forces: (1) all previous writes by this thread are flushed from the store buffer to cache/main memory BEFORE the volatile write, and (2) subsequent reads by other threads that see the volatile value also see all writes that preceded it. A `volatile` read emits a **load-load barrier** + **load-store barrier** — preventing the CPU from reordering subsequent reads/writes before the volatile read. On x86 (which has a strong memory model), volatile writes are the expensive operation (full fence); volatile reads are cheap (load acquire).

#### 2.3 — How `synchronized` creates happens-before

```java
// Tier 1 — Demo: synchronized provides both visibility AND atomicity
public class Counter {
    private int count = 0;
    private final Object lock = new Object();

    public void increment() {
        synchronized (lock) {     // ACQUIRE — all variables read from main memory
            count++;              // atomic within the lock (only one thread here)
        }                         // RELEASE — all writes flushed to main memory
    }

    public int getCount() {
        synchronized (lock) {     // ACQUIRE — sees the latest count
            return count;
        }                         // RELEASE
    }
}
// Rule 2: unlock(lock) in increment() happens-before lock(lock) in getCount()
// Therefore: the write to count inside increment() is visible to the read inside getCount()
```

**Synchronized provides TWO guarantees:**
1. **Mutual exclusion (atomicity):** only one thread executes the synchronized block at a time.
2. **Visibility:** on lock release, all writes are flushed to main memory. On lock acquire, all reads come from main memory.

`volatile` provides only visibility, NOT mutual exclusion. This is why `volatile` can't fix `count++` (which is read-modify-write — 3 operations, not atomic).

#### 2.4 — `final` fields and safe publication

```java
// Tier 1 — Demo: final fields have special JMM guarantees
public class ImmutablePoint {
    private final int x;
    private final int y;

    public ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }
    // JMM guarantee: once the constructor completes,
    // the final fields x and y are visible to ANY thread
    // that obtains a reference to this object —
    // WITHOUT any additional synchronization.
    // This is the "safe publication" guarantee for final fields.
}

// ⚠️ CRITICAL: this guarantee requires the constructor to complete normally.
// If 'this' escapes during construction (e.g., passing 'this' to another thread
// inside the constructor), the guarantee is void — the other thread may see
// partially-constructed final fields.

// ❌ DON'T let 'this' escape during construction:
public class Broken {
    private final int x;
    public Broken(int x) {
        this.x = x;
        EventBus.register(this);   // 'this' escapes → other threads may see x = 0
    }
}
```

#### 2.5 — Data races and undefined behavior

```java
// A data race exists when:
// 1. Two threads access the same variable
// 2. At least one access is a write
// 3. There is no happens-before ordering between the accesses

// Data race → the JMM makes NO guarantees about the result.
// Any value is legal — including values the variable NEVER held.
// (Out-of-thin-air values are theoretically possible in the spec,
//  though current JVMs don't produce them.)

// ❌ Data race: no synchronization
int sharedVar = 0;
// Thread A: sharedVar = 42;
// Thread B: int local = sharedVar;
// B may see 0, 42, or (theoretically) any value.

// ❌ Data race: the "double-checked locking" anti-pattern (pre-Java 5)
// Before the JMM was fixed in Java 5, double-checked locking was BROKEN
// because the object reference could be published before construction completed.

// ✅ Java 5+ double-checked locking with volatile:
private volatile Singleton instance;
public Singleton getInstance() {
    if (instance == null) {                     // first check (no lock)
        synchronized (this) {
            if (instance == null) {             // second check (with lock)
                instance = new Singleton();     // volatile write — publishes safely
            }
        }
    }
    return instance;
}
// volatile ensures: the write to 'instance' happens-after construction completes.
// Without volatile: another thread could see a non-null 'instance' pointing to
// a partially-constructed object (instruction reordering).
```

---

### Level 3 — The subtleties

#### 3.1 — Happens-before is NOT "happens in time before"

```
  Happens-before is a LOGICAL ordering, not a temporal one.

  Thread A writes x = 1 at time T=0.
  Thread B reads x at time T=10.

  If there is NO happens-before between them:
  → B may see x = 0 (stale). Legal.
  → The fact that A "finished first in wall-clock time" is irrelevant.
  → The JMM does NOT care about wall-clock ordering.

  Happens-before is about GUARANTEE, not TIMING.
  "A happens-before B" means "A's effects are GUARANTEED visible to B."
  Without it: no guarantee, regardless of timing.
```

#### 3.2 — volatile does NOT provide atomicity

```java
// ❌ volatile does NOT make compound operations atomic
private volatile int count = 0;

public void increment() {
    count++;   // THIS IS NOT ATOMIC — it's 3 operations:
    // 1. READ count from main memory (volatile read)
    // 2. ADD 1 (local CPU operation)
    // 3. WRITE count+1 to main memory (volatile write)
    // Between steps 1 and 3, another thread can read the OLD value → lost update
}

// ✅ Fix: use AtomicInteger (CAS-based — lock-free atomic operations)
private final AtomicInteger count = new AtomicInteger(0);
public void increment() {
    count.incrementAndGet();   // single atomic CAS operation
}
// ✅ Thread-safe — uses CPU compare-and-swap instruction

// ✅ Fix: use synchronized (if you need atomicity for multiple variables)
private int count = 0;
public synchronized void increment() {
    count++;   // atomic within the lock
}
```

#### 3.3 — The `volatile` piggybacking pattern

Because happens-before is transitive, a volatile write/read can carry non-volatile writes along:

```java
// Advanced pattern: using volatile to publish non-volatile state
private int data;              // NOT volatile
private volatile boolean ready; // volatile — acts as the "fence"

// Thread A:
data = 42;            // non-volatile write
ready = true;         // volatile write — happens-after data = 42 (program order)
                      // and happens-before Thread B's volatile read

// Thread B:
if (ready) {          // volatile read — establishes happens-before
    int local = data; // guaranteed to see 42
    //                // because: A's data=42 happens-before A's ready=true (program order)
    //                //          A's ready=true happens-before B's read of ready (volatile rule)
    //                //          B's read of ready happens-before B's read of data (program order)
    //                //          TRANSITIVITY: A's data=42 happens-before B's read of data
}
```

This is exactly how `volatile` flag patterns work — the `volatile` variable is the synchronization point, and ALL preceding writes become visible through transitivity.

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Writes are instantly visible to all threads" | Without happens-before, a write may NEVER be visible to another thread. The CPU cache may hold a stale value indefinitely. The JIT may hoist reads out of loops. Hardware doesn't guarantee cross-core visibility without explicit barriers. |
| "Happens-before means 'happens first in time'" | Happens-before is a LOGICAL ordering guarantee, not a temporal one. It means "A's effects are guaranteed visible to B." Two actions can happen at the same wall-clock time and still have a happens-before relationship (or not). |
| "`volatile` makes everything thread-safe" | `volatile` guarantees visibility (reads see the latest write) and prevents reordering. It does NOT provide atomicity. `count++` on a volatile int is still a race condition (read-modify-write is 3 operations). Use `AtomicInteger` or `synchronized` for compound operations. |
| "Java is sequentially consistent" | Only data-race-free programs are guaranteed sequential consistency. Programs with data races have no guarantees — any result is legal. The JMM explicitly allows reordering and caching for performance, provided happens-before rules are respected. |
| "Using `synchronized` on different locks creates happens-before" | Happens-before for monitors requires the SAME lock object. `synchronized(lockA)` in thread 1 and `synchronized(lockB)` in thread 2 create NO happens-before between them — they're independent. |

---

## 🐞 Production Footguns

---

> **Footgun: Infinite loop from JIT hoisting non-volatile read**
> **Cost:** Thread hangs forever (livelock/infinite loop)
>
> A background polling thread checked a `boolean running` flag in a tight loop. The main thread set `running = false` to signal shutdown. The worker never stopped — HotSpot's C2 JIT compiler hoisted the read of `running` outside the loop (since it's not volatile, the JIT is free to assume it never changes within the loop). The thread ran the equivalent of `if (running) { while(true) {} }`. The fix was adding `volatile` to the flag.

```java
// ❌ The trap: non-volatile flag in a tight loop
private boolean running = true;

public void workerLoop() {
    while (running) {
        // JIT can optimize this to:
        // boolean cached = running;
        // while (cached) { processItem(); }
        // The field is NEVER re-read from memory
        processItem();
    }
}

// ✅ The fix: volatile flag
private volatile boolean running = true;
// volatile read on every iteration — JIT cannot hoist it out of the loop
```

---

> **Footgun: Double-checked locking without volatile (pre-Java-5 era, still found)**
> **Cost:** Partially constructed object visible to other threads
>
> A legacy Singleton implementation used double-checked locking without `volatile` on the instance field. Without `volatile`, the JVM can reorder the steps of object construction: allocate memory → assign reference → call constructor. Another thread seeing a non-null reference could access the object BEFORE its constructor completed — reading default values (0, null, false) for fields that should have been initialized.

```java
// ❌ The trap: double-checked locking without volatile
private static Singleton instance;   // NOT volatile

public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton();
                // Without volatile, this can be reordered to:
                // 1. allocate memory
                // 2. assign reference to 'instance' (non-null now!)
                // 3. call constructor (NOT DONE YET)
                // Another thread sees non-null instance at step 2,
                // skips synchronized, returns half-constructed object
            }
        }
    }
    return instance;
}

// ✅ The fix: volatile instance field
private static volatile Singleton instance;
// volatile write at step 2 happens-after constructor at step 3
// (JMM forbids reordering volatile write before preceding actions)
// Other threads see fully constructed object or null — never half-constructed

// ✅ Better fix: Holder pattern (no volatile needed — leverages classloading guarantees)
private static class Holder {
    static final Singleton INSTANCE = new Singleton();
}
public static Singleton getInstance() {
    return Holder.INSTANCE;   // class loaded on first access, thread-safe by JLS
}
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `synchronized-volatile.md` (planned — Note #16) | JMM provides the theoretical foundation; synchronized + volatile are the practical mechanisms that establish happens-before in code. Note #16 goes deep on when to use each. |
| `locks-reentrant-readwrite.md` (planned — Note #17) | `ReentrantLock.unlock()` establishes happens-before with `ReentrantLock.lock()` on the same lock — same semantics as `synchronized`, but with additional capabilities (tryLock, fairness, conditions). |
| `concurrent-collections.md` (planned — Note #18) | ConcurrentHashMap's thread-safety guarantees are defined in terms of happens-before: a put() happens-before a subsequent get() that sees that value. Understanding the JMM explains WHY ConcurrentHashMap is safe without external locking. |
| `string-internals.md` | String's immutability + `final` fields give it safe publication under the JMM — once a String reference is visible to a thread, all its fields are guaranteed visible too (final field rule). This is why String is thread-safe without synchronization. |
| `hashmap-internals.md` | HashMap is NOT thread-safe because it has no happens-before guarantees between concurrent reads and writes. Without synchronization, a reader can see a partially-updated bucket array (corrupted state). ConcurrentHashMap exists because HashMap + JMM = data race. |

---

## 🎙️ Interview Deep Questions

**Q1. What is the Java Memory Model? Why does it exist?**

> The JMM (JLS §17.4) defines the rules for how threads interact through memory. It exists because modern CPUs have per-core caches, store buffers, and instruction reordering — a write by one core is not instantly visible to another core. Without the JMM, Java programs would be non-deterministic at the hardware level. The JMM defines happens-before: the minimal set of guarantees about when one thread's writes become visible to another thread's reads. If there's a happens-before relationship, visibility is guaranteed. If not, there are no guarantees — the JVM and CPU are free to cache, buffer, and reorder as they please.

**Q2. What is happens-before? Name 4 rules that establish it.**

> Happens-before is the core ordering guarantee of the JMM. If action A happens-before action B, then all of A's writes are guaranteed visible to B. Four rules: (1) Monitor lock — unlocking a monitor happens-before locking the same monitor. (2) Volatile — writing to a volatile variable happens-before reading that same variable. (3) Thread start — `Thread.start()` happens-before any action in the started thread. (4) Thread join — any action in a thread happens-before `join()` returns. Plus transitivity: if A→B and B→C, then A→C. Without one of these rules, there is NO visibility guarantee between threads.

**Q3. What is the difference between visibility and atomicity? Give an example where volatile provides one but not the other.**

> Visibility means a write by one thread is seen by reads in other threads. Atomicity means an operation completes entirely without interruption. `volatile` provides visibility (reads see the latest write) but NOT atomicity. Example: `volatile int count; count++` — the increment is 3 operations (read, add, write). Between the read and write, another thread can read the old value and also increment → lost update. Both threads read 5, both write 6, but the correct answer is 7. Fix: `AtomicInteger.incrementAndGet()` which uses CAS (compare-and-swap) — a single atomic CPU instruction that reads, compares, and writes in one step.

**Q4. Explain the double-checked locking pattern. Why does it require `volatile`?**

> Double-checked locking creates a singleton by checking the reference twice — once without locking (fast path for the common case where it's already created), once with locking (safe path for first creation). Without `volatile`, the JVM can reorder object construction: allocate memory → assign reference (non-null) → call constructor. Another thread doing the first check sees a non-null reference and returns the object before its constructor has run — reading default values for fields. With `volatile`, the write to the instance field happens-after the constructor completes (volatile write can't be reordered before preceding actions), so any thread that sees a non-null reference is guaranteed to see a fully constructed object.

**Q5. What is a data race? How do you make a Java program data-race-free?**

> A data race occurs when two threads access the same variable, at least one is a write, and there's no happens-before ordering between them. A program with a data race has undefined behavior under the JMM — any result is legal. To make a program data-race-free: protect every shared mutable variable with synchronization (synchronized blocks, volatile fields, atomic variables, or concurrent collections). The JMM guarantees sequential consistency ONLY for data-race-free programs. In practice: make fields `final` (immutable = no writes = no data races), use `volatile` for single-variable flags/references, use `synchronized` or `Lock` for compound operations, and use `AtomicInteger`/`AtomicReference` for lock-free atomic updates.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** The JMM defines when one thread's writes are visible to another thread's reads. The core concept is happens-before — without it, there are zero visibility guarantees between threads.
>
> **Part 2 — How/Why (30s):** Modern CPUs have per-core caches — a write on core 0 stays in core 0's cache until flushed. The JMM defines 6 happens-before rules that force flushes: monitor unlock→lock on the same lock, volatile write→read on the same variable, Thread.start()→actions in started thread, thread termination→join(), and transitivity. `synchronized` provides both visibility (flush on release, refresh on acquire) and atomicity (mutual exclusion). `volatile` provides only visibility — the read always sees the latest write, but compound operations like `i++` are NOT atomic.
>
> **Part 3 — Gotcha (20s):** The classic trap: a non-volatile boolean flag used to stop a thread. The JIT hoists the read out of the loop — the thread never sees the flag change and runs forever. Fix: make the flag `volatile`. Second trap: double-checked locking without `volatile` — the reference is published before construction completes, and another thread reads a half-constructed object. Fix: `volatile` on the instance field, or use the Holder pattern.

---

## 🧾 TL;DR

- JMM defines when writes by one thread are visible to reads by another. Core concept: **happens-before**.
- **No happens-before → no guarantees.** Reads may see stale cached values forever.
- 6 rules: program order, monitor lock/unlock, volatile write/read, thread start, thread join, transitivity.
- **`synchronized`** = visibility + atomicity. Flush on release, refresh on acquire. Same lock required.
- **`volatile`** = visibility only. No atomicity. `count++` on volatile is still a race.
- **`final`** fields: visible to all threads after constructor completes (safe publication).
- **Data race** = two threads, same variable, one write, no happens-before → undefined behavior.
- Double-checked locking requires `volatile` — without it, reference is published before construction.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #15 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: JMM as hardware abstraction, 6 happens-before rules from JLS §17.4.5, volatile as store-load barrier (x86 specifics), synchronized acquire/release semantics, final field safe publication (this-escape trap), data race = undefined behavior, volatile piggybacking pattern (transitive visibility), double-checked locking (pre-Java-5 bug + volatile fix + Holder alternative). Two production footguns: JIT hoisting non-volatile read (infinite loop), double-checked locking without volatile (partially constructed object). |
