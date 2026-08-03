# Java Concurrency — The "Why It Works" Deep Dive

> **How to use this file:**
> - New to concurrency? → read §1 → §6 in order; the rest will click
> - Revising visibility specifically? → jump straight to §3
> - Revising a specific primitive? → jump to its section
>
> **What this file covers that §9 does NOT:**
> *Why* each primitive works at the hardware/JVM level — the mental models.
> [`00-concepts-before-problems.md` §9](./00-concepts-before-problems.md#9-concurrency--from-zero-to-interview-ready) has the **code templates and cross-question answers**.
> Read this file first — then §9 becomes a revision cheat sheet rather than a wall of unfamiliar code.

---

## 🎯 Goal

After reading this file you should be able to:

- Explain what **visibility** is and why it is a completely separate problem from atomicity
- Explain what **happens-before** means — the actual Java Memory Model rule
- Explain why `volatile` fixes visibility but NOT atomicity, with a concrete example
- Explain why `synchronized` fixes both, and what the cost is
- Explain CAS at a conceptual level and when it beats `synchronized`
- Explain the lock-upgrade deadlock without looking at notes

---

## 📖 1. What Is a Thread?

A **process** (a running program with its own isolated memory space — like a separate house on its own lot) owns:

- **Heap** — the shared memory area where all objects live
- **Metaspace** — class definitions and method bytecode
- **Code** — the compiled binary

A **thread** (a lightweight unit of execution *within* a process — like a person living in the house, doing work independently) owns only:

- **Stack** — its own local variables and call frames (one frame per method call)
- **Program counter** — which instruction it is currently executing

Multiple threads in the same process **share the heap**. That shared heap is exactly where all concurrency problems live.

```
  ┌─────────────────────────────────────────────────────────────────────┐
  │                            JVM Process                              │
  │                                                                     │
  │   HEAP (shared by ALL threads)                                      │
  │   ┌─────────────────────────────────────────────────────────────┐   │
  │   │  int counter = 0  │  String name = "kafka"  │  List tasks  │   │
  │   └─────────────────────────────────────────────────────────────┘   │
  │                                                                     │
  │   Thread-1 stack      Thread-2 stack      Thread-3 stack            │
  │   ┌───────────┐        ┌───────────┐        ┌───────────┐           │
  │   │ int x = 3 │        │ int i = 0 │        │ int x = 7 │           │
  │   │ frame: A  │        │ frame: B  │        │ frame: C  │           │
  │   └───────────┘        └───────────┘        └───────────┘           │
  └─────────────────────────────────────────────────────────────────────┘
```

**KEY POINT:** Local variables on a thread's stack are invisible to all other threads — no concurrency issue. Only heap-resident fields and arrays are shared — that is where bugs live.

---

## 📖 2. The Two Root Problems

Every concurrency bug is one of two root problems — or both at once. Name them precisely in interviews; interviewers notice when candidates confuse them.

### Problem 1 — Atomicity

An **atomic operation** (an operation that appears to execute as a single, uninterruptible step — like flipping a switch: it is either on or off, never half-way) cannot be observed in an intermediate state by another thread.

`counter++` looks like one operation but is actually **three CPU instructions**:

```
  LOAD    → read counter from memory into a CPU register
  ADD     → add 1 to the register value
  STORE   → write the result from the register back to memory
```

Two threads doing `counter++` concurrently can interleave between these three steps:

```
  Thread-1                              Thread-2
  ────────────────────────────          ────────────────────────────
  LOAD   → reg = 5
                                        LOAD   → reg = 5   ← reads the same old value
  ADD    → reg = 6
  STORE  → counter = 6                  ADD    → reg = 6
                                        STORE  → counter = 6

  Expected: counter = 7
  Actual:   counter = 6    ← one increment permanently lost
```

### Problem 2 — Visibility

Even if Thread-1 completes its STORE, Thread-2 may **read the old value** from its own CPU cache. Visibility is a hardware-level problem — covered in depth in §3.

The reason the two problems are distinct:
- You can have **atomicity without visibility** — a synchronized block protects the compound operation, but another thread reading without synchronization could still see stale data
- You can have **visibility without atomicity** — `volatile int counter` makes every read go to main memory, but two threads can still lose each other's increments

---

## 🧠 3. Visibility — Why Writes Disappear

This is the most counterintuitive part of concurrency. A write can fully complete on Thread-1, and Thread-2 can still read the old value — not because of a timing race, but because of CPU architecture.

### 3a. The CPU Cache Architecture

Modern CPUs do not read and write directly to RAM. RAM takes ~100 nanoseconds per access. Each CPU core has private **caches** layered in front of it to reduce that latency:

```
  ┌────────────────────────────────────────────────────────────────────┐
  │                        RAM (main memory)                           │
  │                                                                    │
  │   counter = 5   (the "canonical" value — eventually)               │
  └──────────────┬──────────────────────────┬──────────────────────────┘
                 │    shared bus ~100ns      │
        ┌────────┴──────────┐      ┌─────────┴──────────┐
        │   CPU Core 0      │      │   CPU Core 1        │
        │                   │      │                     │
        │   L3 cache ◄──────┼──────┼──────► L3 cache    │
        │   (~30ns, shared) │      │        (~30ns)      │
        │                   │      │                     │
        │   L2 cache        │      │   L2 cache          │
        │   (~5ns, private) │      │   (~5ns, private)   │
        │                   │      │                     │
        │   L1 cache        │      │   L1 cache          │
        │   (~1ns, private) │      │   (~1ns, private)   │
        │                   │      │                     │
        │  counter = 6  ◄───┤      ├───►  counter = 5   │
        │  (T1 wrote here)  │      │      (T2 sees this) │
        └───────────────────┘      └─────────────────────┘
```

Thread-1 runs on Core 0. It executes `counter = 6` — the new value goes into Core 0's **L1 cache**. Thread-2 runs on Core 1. When Thread-2 reads `counter`, it checks Core 1's L1, then L2, then shared L3 — and if Core 0's write has not propagated yet, it reads `5`.

The write is not invisible forever. The CPU's cache-coherence protocol (called **MESI** — a state machine that keeps cache lines consistent across cores) will eventually propagate Core 0's write. "Eventually" might be microseconds — but a concurrent program can execute thousands of operations in that time.

> ⚠️ **Intuition-check:** You may read that `volatile` "flushes the CPU cache." That is a useful teaching shortcut but is not technically accurate. What `volatile` actually does is insert **memory barrier** instructions (like `MFENCE` on x86) — CPU instructions that prevent store/load reordering and guarantee that a write is globally visible to all cores before any subsequent read. The cache-flush framing is an acceptable intuition for interviews. The precise answer is "memory barriers that enforce ordering and visibility guarantees."

### 3b. The Java Memory Model — Happens-Before

The **Java Memory Model (JMM)** (the formal specification in the Java Language Specification — §17 — that defines exactly what values a thread is allowed to read from a shared variable) does not talk about caches or cache lines. It talks about **happens-before**.

**Happens-before** (a formal ordering relationship between two operations: if action A *happens-before* action B, then B is guaranteed to observe all side-effects of A — writes made by A are visible to B) is the actual contract the JVM and JIT compiler must honor.

The JMM establishes a happens-before edge in these situations:

| Situation | The Guarantee |
|-----------|---------------|
| Within a single thread | Each statement happens-before the next statement in program order |
| `monitor unlock` → next `monitor lock` on the **same** object | Everything Thread-1 did before unlocking is visible to Thread-2 after it locks |
| `volatile write` → subsequent `volatile read` of the **same** field | The write happens-before every subsequent read of that field |
| `Thread.start()` → all actions inside the started thread | The parent's state at start-time is visible to the child thread |
| All actions inside a thread → `Thread.join()` returns | The thread's final state is visible to whoever called join |

**The key rule:** If there is no happens-before edge between a write in Thread-1 and a read in Thread-2, the read is **permitted to return any value** — the old value, the new value, or even a partially-initialized value. This is a **data race**. The JMM says the program has undefined behavior under a data race.

**In plain English:** You are only guaranteed to see another thread's write if there is a formal synchronization ordering between you and that writer. Without that ordering, the compiler and the CPU are each free to reorder your reads and writes for performance — and they do.

### 3c. `volatile` Creates Happens-Before on a Field

`volatile` establishes a happens-before edge on every write-then-read pair of that specific field:

> A write to a `volatile` field happens-before every subsequent read of that same field by any thread.

This solves the visibility problem for that field. It does **not** create mutual exclusion — there is no lock — so compound operations (read-then-write) are still not atomic.

### 3d. The Broken vs Fixed Pattern for Visibility

```java
// BROKEN — without volatile, the compiler is allowed to
// cache 'running' in a register and never re-read from memory.
// Thread-2's write to running = false may never be seen by Thread-1.
private boolean running = true;

// FIXED — volatile on a boolean field:
// (1) the single-bit write is already atomic by hardware
// (2) volatile adds the happens-before edge → Thread-1 sees the write
private volatile boolean running = true;
```

### 🎨 Visual — Visibility Gap: `volatile` vs plain field

```
  WITHOUT volatile (Thread-1 may never see the update):

  Thread-1 (Core 0 — worker loop)        Thread-2 (Core 1 — controller)
  ────────────────────────────────        ──────────────────────────────
  while (running) { }                     running = false
    ↑ reads from L1 cache (Core 0)          ↓ write goes to Core 1's L1 cache
    ↑ Core 0's L1 still says TRUE           ↓ may NEVER propagate to Core 0
  → loops forever

  WITH volatile (Thread-1 is guaranteed to see the update):

  Thread-1 (Core 0 — worker loop)        Thread-2 (Core 1 — controller)
  ────────────────────────────────        ──────────────────────────────
  while (running) { }                     running = false
    ↑ volatile read = memory barrier        ↓ volatile write = memory barrier
    ↑ must go past cache, get global        ↓ forced globally visible before barrier
      value                              → Thread-1 eventually reads FALSE ✓
  → loop exits

  KEY INVARIANT:
     A volatile write happens-before a subsequent volatile read of the same field.
     The JVM inserts memory barriers to enforce this — it is a formal guarantee,
     not a best-effort hint.
```

---

## 🧠 4. Atomicity — Why `i++` Is Three Steps

Visibility is a hardware problem. Atomicity is an instruction-level problem.

```
  Thread-1                              Thread-2
  ────────────────────────────────      ────────────────────────────────
  1. LOAD   counter → reg1 = 0
  2. ADD    reg1 = 1
                                        1. LOAD   counter → reg2 = 0  ← same original value
                                        2. ADD    reg2 = 1
  3. STORE  counter = 1                 3. STORE  counter = 1

  Expected after both threads finish: counter = 2
  Actual:                             counter = 1  ← one full increment lost

  KEY INVARIANT:
     LOAD-ADD-STORE is three instructions. Another thread can execute between
     any two of them. volatile makes LOAD fresh and STORE visible, but does
     not prevent two threads from loading the same old value before either stores.
```

`volatile int counter` does NOT fix this. Both threads read the "globally visible" value of `0`, both compute `1`, both store `1`. The value should be `2` but is `1`. Visibility was fine — the issue is that the compound operation was not atomic.

**What actually fixes atomicity:**

| Tool | Mechanism | Works for `i++`? |
|------|-----------|-----------------|
| `synchronized` | Mutual exclusion — only one thread in the block | ✅ Yes |
| `AtomicInteger.incrementAndGet()` | CAS — hardware-level atomic read-modify-write | ✅ Yes |
| `volatile int` | Visibility only — no mutual exclusion | ❌ No |

---

## 🧠 5. `synchronized` — Both Problems Solved

`synchronized` uses a **monitor** (an implicit lock built into every Java object — like a hotel room key card: only one guest holds it at a time; the front desk enforces exclusivity) to provide two guarantees simultaneously:

1. **Atomicity** — only one thread executes inside the `synchronized` block at any moment
2. **Visibility** — when Thread-1 releases the monitor, everything it wrote is guaranteed visible to Thread-2 when Thread-2 acquires the same monitor (happens-before on the monitor edge)

```
  Thread-1                              Thread-2
  ────────────────────────────────      ────────────────────────────────
  acquire monitor on 'this'             ▶ blocked — monitor held by T1
    ↓ all prior writes flushed
  counter++  (LOAD → ADD → STORE)
    ↓ no other thread can interleave
  release monitor on 'this'             ◀ unblocked
                                        acquire monitor on 'this'
                                          ↓ T1's writes now visible (happens-before)
                                        counter++
                                        release monitor on 'this'

  KEY INVARIANT:
     Monitor release by Thread-1 happens-before monitor acquire by Thread-2
     on the SAME object. Thread-2 sees everything Thread-1 wrote before releasing.
```

**The cost:** When a second thread tries to acquire a contended monitor, it is **put to sleep by the OS** until the first thread releases it. This involves a context switch (~1-10 microseconds). For very short, low-contention critical sections, this overhead can dominate the actual work.

**When to use:** Any compound operation on shared state. Any time you need both atomicity AND visibility across multiple fields.

---

## 🧠 6. `volatile` — Visibility Only, No Locking Cost

`volatile` inserts memory barriers — cheaper than a monitor acquire/release, but no exclusion.

**Correct uses:**

| Use case | Why volatile is enough |
|----------|----------------------|
| `volatile boolean running` | Writing `false` is a single CPU instruction — atomic by hardware. Volatile adds visibility. |
| `volatile reference` to an immutable object | Swapping the reference is one instruction. The object's fields don't change after construction. |
| Single-write-multiple-readers flag with no compound logic | Only one thread ever writes; readers only need to see the latest value. |

**Wrong uses:**

| Use case | Why it breaks |
|----------|--------------|
| `volatile int counter; counter++` | `++` is LOAD-ADD-STORE. Two threads still read the same old value before either stores. |
| `volatile boolean flag; if (!flag) { ...; flag = true; }` | Check-then-act is a compound operation — not atomic. Both threads can pass the check. |
| `volatile` on multiple related fields that must update together | `volatile` only covers one field at a time. No way to atomically update two fields. |

---

## 🧠 7. `AtomicInteger` / `AtomicReference` — CAS Without OS Locks

**Compare-And-Swap (CAS)** (a single hardware instruction that atomically reads a memory location, compares it to an expected value, and writes a new value only if the comparison matched — like an optimistic lock baked into the CPU itself) is the foundation of every class in `java.util.concurrent.atomic`.

The `incrementAndGet()` operation uses CAS in a retry loop:

```
  loop:
    1. current = value.get()           // read current value optimistically
    2. next    = current + 1           // compute new value locally
    3. CAS(value, current, next)       // "set value = next, only if value is still current"
       ├── CAS SUCCEEDED → return next  // no interference, done
       └── CAS FAILED    → retry loop   // another thread changed value, try again
```

The CAS instruction is `LOCK CMPXCHG` on x86 — one hardware instruction that the CPU executes atomically. No OS involvement, no context switch.

### 🎨 Visual — CAS vs `synchronized` Under Contention

```
  LOW contention (2 threads):

  synchronized                          AtomicInteger (CAS)
  ─────────────────────────────         ─────────────────────────────
  T1: acquire monitor (fast, free)      T1: CAS attempt → succeeds immediately
  T1: increment, release                T1: done
  T2: acquire monitor (fast, free)      T2: CAS attempt → succeeds immediately
  T2: increment, release                T2: done

  → Both fast. CAS avoids the monitor machinery entirely → ~5-10x faster.

  HIGH contention (100 threads):

  synchronized                          AtomicInteger (CAS)
  ─────────────────────────────         ─────────────────────────────
  T1: holds monitor                     T1: CAS succeeds
  T2-T100: OS puts them to sleep        T2-T100: CAS FAILS → retry loop → FAILS
  T1 releases → OS wakes one thread     → spin, spin, spin → eventually succeed
  → sleeping threads use ~0 CPU         → spinning threads burn CPU doing nothing

  KEY INVARIANT:
     CAS wins at low contention (no OS overhead).
     synchronized wins at high contention (losers sleep instead of spin).
     When > ~10 threads contend on one AtomicInteger, prefer synchronized or
     LongAdder (which shards the counter to reduce contention).
```

**When CAS is NOT enough:** Multi-field invariants. If you must update `runningSum` AND `activeCount` together atomically, CAS on one field does not help — you need `synchronized` to cover both.

---

## 🧠 8. `ReentrantReadWriteLock` — The Upgrade Trap

`ReentrantReadWriteLock` (a lock that allows multiple concurrent readers as long as no writer holds the lock, and gives exclusive access to a single writer — optimizing for read-heavy workloads like caches and registries) is tested heavily at Confluent because their systems are read-heavy.

The **read-lock-to-write-lock upgrade** is the #1 interview trap.

### 🎨 Visual — Why Upgrade Deadlocks

```
  Both threads hold a readLock and try to upgrade:

  Thread-1                              Thread-2
  ────────────────────────────────      ────────────────────────────────
  readLock.lock()   → ✅ acquired        readLock.lock()   → ✅ acquired
  ... reads cache ...                   ... reads cache ...
  writeLock.lock()  → ⏳ waiting         writeLock.lock()  → ⏳ waiting
    "wait for all readers to release"     "wait for all readers to release"
    ↓                                     ↓
  T2 still holds readLock               T1 still holds readLock
    ↓                                     ↓
  T1 can't proceed                      T2 can't proceed
  ════════════════════ DEADLOCK ═══════════════════════

  The Fix:

  Thread-1
  ────────────────────────────────────────────────────────
  readLock.unlock()    // ← STEP 1: give up the read lock
  writeLock.lock()     // ← STEP 2: now acquire write lock
  // STEP 3: MANDATORY re-check — another thread may have
  //         acted while we held no lock between step 1 and 2
  if (cache.containsKey(key) && isExpired(cache.get(key))) {
      cache.remove(key);
  }
  writeLock.unlock()

  KEY INVARIANT:
     You cannot upgrade from readLock to writeLock.
     Always release the readLock first, then acquire the writeLock,
     then re-check the condition — because the world may have changed
     in the window between releasing and re-acquiring.
```

Code templates, fair vs unfair mode table, and cross-question answers → [`00-concepts-before-problems.md` §9e](./00-concepts-before-problems.md#9e-reentrantreadwritelock--concurrent-reads-exclusive-writes).

---

## 🧠 9. `BlockingQueue` — The `PriorityBlockingQueue` Trap

A **blocking queue** (a thread-safe queue where producers block when the queue is full and consumers block when the queue is empty — threads wait automatically instead of spinning or throwing, eliminating the need for manual `wait`/`notify`) is the standard producer-consumer primitive.

The interview trap: **`PriorityBlockingQueue` is always unbounded.**

```
  Queue variant                      Bounded?   put() blocks?    take() blocks?
  ──────────────────────────────────────────────────────────────────────────────
  ArrayBlockingQueue(capacity)       YES        YES (when full)   YES (when empty)
  LinkedBlockingQueue(capacity)      YES        YES (when full)   YES (when empty)
  LinkedBlockingQueue()              NO         NEVER             YES (when empty)
  PriorityBlockingQueue              NO         NEVER             YES (when empty)
  ──────────────────────────────────────────────────────────────────────────────
```

If a Confluent interviewer asks "what happens if producers outrun consumers on a `PriorityBlockingQueue`?" — the answer is: **the queue grows without bound until the JVM runs out of heap memory and throws `OutOfMemoryError`**. There is no back-pressure, no blocking on the producer side.

The fix is to design the producer to be aware of queue depth (poll the size, use a separate semaphore, or switch to a bounded `ArrayBlockingQueue` if strict ordering is not required).

Code templates and dispatcher pattern → [`00-concepts-before-problems.md` §9f](./00-concepts-before-problems.md#9f-blockingqueue--producer-consumer-without-manual-waitnotify).

---

## 🧠 10. Deadlock — Four Conditions and Prevention

A deadlock requires **all four** of these conditions simultaneously. Removing any one of them prevents the deadlock:

| Condition | Meaning | How to remove |
|-----------|---------|---------------|
| **Mutual exclusion** | At least one resource is held exclusively | Not always removable — sometimes required by design |
| **Hold and wait** | A thread holds one resource while waiting for another | Acquire all locks at once, or release before waiting |
| **No preemption** | A held lock cannot be forcibly taken | Use `tryLock(timeout)` — give up if you can't acquire |
| **Circular wait** | T1 waits for T2, T2 waits for T1 | Enforce consistent global lock ordering |

### Prevention 1 — Consistent Lock Ordering

```java
// BROKEN — T1 acquires lockA then lockB; T2 acquires lockB then lockA
// Both can hold one lock and wait forever for the other
// Thread-1:
synchronized (lockA) {
    synchronized (lockB) {
        // critical section
    }
}
// Thread-2:
synchronized (lockB) {
    synchronized (lockA) {
        // critical section
    }
}

// FIXED — both threads always acquire lockA first, then lockB
// Thread-1:
synchronized (lockA) {
    synchronized (lockB) {
        // critical section
    }
}
// Thread-2 — same order as Thread-1:
synchronized (lockA) {
    synchronized (lockB) {
        // critical section
    }
}
```

### Prevention 2 — `tryLock(timeout)` — Give Up Instead of Wait Forever

```java
boolean gotA = lockA.tryLock(50, TimeUnit.MILLISECONDS);
if (gotA) {
    try {
        boolean gotB = lockB.tryLock(50, TimeUnit.MILLISECONDS);
        if (gotB) {
            try {
                // critical section
            } finally {
                lockB.unlock();
            }
        }
    } finally {
        lockA.unlock();
    }
}
```

### Prevention 3 — Lock-Upgrade Deadlock

Already covered in §8. The short rule: **release readLock before acquiring writeLock, then re-check.**

---

## 🧾 TL;DR — Map Back to §9

This file explained the *why*. For *code templates and cross-question answers*, go to:

**[`00-concepts-before-problems.md` §9 — Concurrency Cheat Sheet + Code Templates](./00-concepts-before-problems.md#9-concurrency--from-zero-to-interview-ready)**

| Section here | What you learned | §9 section for the code template |
|-------------|-----------------|----------------------------------|
| §3 — Visibility + JMM | Why writes disappear; happens-before; volatile semantics | §9b (`synchronized`), §9c (`volatile`) |
| §4 — Atomicity | Why `i++` is three steps; why volatile is not enough | §9d (`AtomicInteger`) |
| §7 — CAS | How CAS works; low vs high contention tradeoff | §9d cross-Qs |
| §8 — Lock upgrade deadlock | Why it deadlocks; release-then-recheck fix | §9e (`ReentrantReadWriteLock`) |
| §9 — PriorityBlockingQueue | Why `put()` never blocks; OOM risk | §9f (`BlockingQueue`) |
| §10 — Deadlock | Four conditions; lock ordering; tryLock | §9h (Deadlock) |

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created. Conceptual deep dive companion to §9 in `00-concepts-before-problems.md`. Covers thread model, visibility/atomicity distinction, JMM happens-before, CPU cache architecture (with intuition labeled as intuition), CAS internals, lock-upgrade deadlock, and deadlock prevention. |
