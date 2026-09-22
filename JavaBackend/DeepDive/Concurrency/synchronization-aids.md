# 🧵 Synchronization Aids — CountDownLatch, CyclicBarrier, Semaphore, Phaser — Deep Dive

> After this note you can explain the difference between CountDownLatch (one-shot gate) and CyclicBarrier (reusable rendezvous), implement a rate limiter with Semaphore, and know when Phaser replaces both Latch and Barrier.

---

## 🎯 The Problem This Solves

`synchronized` and `Lock` control access to shared state. But what about coordinating WHEN threads proceed? "Wait until all 5 services are initialized before accepting traffic." "Wait until all 4 workers finish phase 1 before anyone starts phase 2." "Allow at most 10 concurrent connections to the database." These are coordination problems, not mutual exclusion problems. `java.util.concurrent` provides purpose-built aids for each.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **CountDownLatch** | A one-shot gate. Initialized with a count N. Threads call `countDown()` to decrement. Other threads call `await()` to block until the count reaches 0. Cannot be reset — fire once, done forever. |
| **CyclicBarrier** | A reusable rendezvous point. N threads all call `await()` — all block until the Nth arrives. Then all N are released simultaneously. The barrier resets automatically for the next round. |
| **Semaphore** | A permit-based concurrency limiter. Initialized with N permits. `acquire()` takes a permit (blocks if none available). `release()` returns a permit. Controls HOW MANY threads access a resource concurrently — not WHICH one. |
| **Phaser** | A flexible, reusable, multi-phase synchronization barrier (Java 7). Combines CountDownLatch and CyclicBarrier capabilities. Supports dynamic registration/deregistration of parties. |
| **AbstractQueuedSynchronizer (AQS)** | The internal framework that CountDownLatch, Semaphore, ReentrantLock, and CyclicBarrier are built on. Manages a FIFO wait queue of blocked threads and an integer state variable. |

---

## 🧠 Mental Model

**CountDownLatch** = a rocket launch countdown. Count starts at N. Each system reports ready (`countDown()`). The launch controller waits (`await()`). When count hits 0 — launch. You can't re-launch (one-shot).

**CyclicBarrier** = runners at a track meet. All N runners line up at the start line (`await()`). Nobody runs until the last runner arrives. Then all start simultaneously. Next race: everyone lines up again (reusable).

**Semaphore** = parking lot with N spaces. Each car entering takes a space (`acquire()`). If full, cars wait in line. Each car leaving frees a space (`release()`). Controls concurrency, not order.

> If you can say "Latch: N signal, M wait, one-shot. Barrier: N wait for each other, reusable. Semaphore: N permits, controls concurrent access count. All built on AQS" without notes, you have sync aids.

---

## 🎨 Visual — Latch vs Barrier vs Semaphore

```
  COUNTDOWN LATCH (N=3, one-shot):
  Thread A: countDown() ──┐
  Thread B: countDown() ──┤  count: 3 → 2 → 1 → 0
  Thread C: countDown() ──┘
                           │
  Main thread: await() ───►  UNBLOCKED when count = 0
                              (latch is spent — cannot reuse)

  CYCLIC BARRIER (N=3, reusable):
  Thread A: await() ──┐
  Thread B: await() ──┤  waiting: 1 → 2 → 3 = N
  Thread C: await() ──┘
                       │
                       ALL THREE unblocked simultaneously
                       barrier RESETS for next round

  SEMAPHORE (permits=3):
  Thread A: acquire() → permit 1 used → enters
  Thread B: acquire() → permit 2 used → enters
  Thread C: acquire() → permit 3 used → enters
  Thread D: acquire() → NO permits → BLOCKS
  Thread A: release() → permit returned → Thread D unblocks → enters

KEY INVARIANT:
   Latch:     N producers signal, M consumers wait. One-shot.
   Barrier:   N threads ALL wait for each other. Reusable.
   Semaphore: N permits limit concurrency. No ownership.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Coordinating startup with a shared volatile boolean — fragile
volatile boolean service1Ready = false;
volatile boolean service2Ready = false;
volatile boolean service3Ready = false;

// Main thread: busy-wait (wastes CPU)
while (!(service1Ready && service2Ready && service3Ready)) {
    Thread.sleep(100);   // polling — wastes CPU + adds latency
}
// ❌ Busy-wait burns CPU. Adding a 4th service requires changing the code.
// ❌ No timeout. No exception handling. No composability.
```

---

### Level 2 — The real mechanism

#### 2.1 — CountDownLatch

```java
// Tier 2 — Production: wait for N services to initialize
CountDownLatch startupLatch = new CountDownLatch(3);   // 3 services

// Each service thread:
public void initService(String name) {
    try {
        performInitialization(name);
        log.info("{} initialized", name);
    } finally {
        startupLatch.countDown();   // decrement — safe in finally
    }
}

// Main thread:
startupLatch.await(30, TimeUnit.SECONDS);   // block until count = 0, or timeout
if (startupLatch.getCount() > 0) {
    throw new StartupTimeoutException("Not all services initialized within 30s");
}
log.info("All services ready — accepting traffic");

// ⚠️ countDown() doesn't block the calling thread — it just decrements.
// await() blocks until count reaches 0.
// The latch is one-shot — once count = 0, await() never blocks again.
// Cannot reset. Create a new latch for a new countdown.
```

**Use cases:**
- Wait for N worker threads to complete before aggregating results
- Wait for all services to initialize before accepting requests
- Test harness: ensure N threads start simultaneously (each thread awaits a "go" latch)

#### 2.2 — CyclicBarrier

```java
// Tier 2 — Production: parallel data processing with phase synchronization
int workerCount = 4;
CyclicBarrier barrier = new CyclicBarrier(workerCount, () -> {
    // This Runnable runs ONCE when all parties arrive — "barrier action"
    log.info("All {} workers completed phase — merging results", workerCount);
    mergePartialResults();
});

// Each worker:
public void worker(int workerId) {
    for (int phase = 0; phase < 10; phase++) {
        processPhase(workerId, phase);   // each worker processes its chunk
        try {
            barrier.await();   // wait for all 4 workers to finish this phase
            // ALL 4 unblock simultaneously when the 4th calls await()
            // Barrier resets automatically — ready for the next phase
        } catch (BrokenBarrierException e) {
            // Another thread was interrupted or timed out — barrier is broken
            // All waiting threads receive BrokenBarrierException
            log.error("Barrier broken in phase {}", phase, e);
            return;
        }
    }
}

// CyclicBarrier vs CountDownLatch:
// Latch: N signal (countDown), M wait (await). Asymmetric. One-shot.
// Barrier: N all wait (await). Symmetric. Reusable. Has barrier action.
```

**BrokenBarrierException:** if any thread is interrupted while waiting, or if `await(timeout)` expires, the barrier is BROKEN. All waiting threads immediately receive `BrokenBarrierException`. The barrier cannot be used again unless explicitly `reset()`.

#### 2.3 — Semaphore

```java
// Tier 2 — Production: connection pool limiter
Semaphore connectionPermits = new Semaphore(10);   // max 10 concurrent connections

public Data queryDatabase(String sql) throws InterruptedException {
    connectionPermits.acquire();   // blocks if 10 connections are in use
    try {
        Connection conn = dataSource.getConnection();
        try {
            return executeQuery(conn, sql);
        } finally {
            conn.close();
        }
    } finally {
        connectionPermits.release();   // return permit — even on exception
    }
}

// tryAcquire — non-blocking
if (connectionPermits.tryAcquire(5, TimeUnit.SECONDS)) {
    try {
        // use resource
    } finally {
        connectionPermits.release();
    }
} else {
    throw new ResourceUnavailableException("Connection pool exhausted");
}

// Fair semaphore — FIFO ordering
Semaphore fair = new Semaphore(10, true);   // fair = true
// Threads acquire in the order they called acquire(). Prevents starvation.
// ~2x overhead vs non-fair.
```

**Semaphore ≠ Lock:**
- Lock: one thread holds it. Reentrancy. Ownership (only holder can release).
- Semaphore: N permits. No ownership — any thread can release (even one that never acquired). No reentrancy concept. A binary semaphore (permits=1) resembles a lock but has no ownership.

#### 2.4 — Phaser (Java 7)

```java
// Phaser = CountDownLatch + CyclicBarrier + dynamic party management
Phaser phaser = new Phaser(1);   // 1 = the main thread (self-registered)

// Dynamically register workers
for (int i = 0; i < workerCount; i++) {
    phaser.register();   // add a party dynamically
    new Thread(() -> {
        for (int phase = 0; phase < 3; phase++) {
            processPhase(phase);
            phaser.arriveAndAwaitAdvance();   // like barrier.await()
        }
        phaser.arriveAndDeregister();   // done — remove self from party count
    }).start();
}

// Main thread waits for all phases to complete
phaser.arriveAndAwaitAdvance();   // wait for phase 0
phaser.arriveAndAwaitAdvance();   // wait for phase 1
phaser.arriveAndAwaitAdvance();   // wait for phase 2

// Advantages over CyclicBarrier:
// - Parties can register/deregister dynamically
// - No BrokenBarrierException — more resilient
// - Can terminate phases conditionally (override onAdvance())
```

---

### Level 3 — The subtleties

#### 3.1 — Testing with CountDownLatch (the "go" pattern)

```java
// Ensure N threads start SIMULTANEOUSLY (for race condition testing):
@Test
void testConcurrentAccess() throws InterruptedException {
    int threadCount = 100;
    CountDownLatch readyLatch = new CountDownLatch(threadCount);   // all threads ready
    CountDownLatch goLatch = new CountDownLatch(1);                 // start signal
    CountDownLatch doneLatch = new CountDownLatch(threadCount);     // all threads done

    for (int i = 0; i < threadCount; i++) {
        new Thread(() -> {
            readyLatch.countDown();          // "I'm ready"
            try {
                goLatch.await();             // wait for the start signal
                serviceUnderTest.doWork();   // all threads execute simultaneously
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();       // "I'm done"
            }
        }).start();
    }

    readyLatch.await();    // wait for all threads to be ready
    goLatch.countDown();   // fire the starting gun — all 100 threads unblock at once
    doneLatch.await(10, TimeUnit.SECONDS);   // wait for all to finish
    // Assert results
}
```

#### 3.2 — Semaphore for rate limiting

```java
// Simple rate limiter: max N requests per window
// (Production: use Guava RateLimiter or Resilience4j for token-bucket)
Semaphore rateLimiter = new Semaphore(100);   // 100 permits

// On each request:
if (rateLimiter.tryAcquire()) {
    try {
        processRequest();
    } finally {
        rateLimiter.release();
    }
} else {
    return ResponseEntity.status(429).body("Too many requests");
}

// Periodically replenish permits (scheduled task):
// This is a simple sliding-window approach — not production-grade
```

#### 3.3 — All sync aids are built on AQS

```
  AbstractQueuedSynchronizer (AQS):
  ┌────────────────────────────────────┐
  │  volatile int state               │  ← the synchronization variable
  │  CLH queue (FIFO of waiting threads)│  ← parked threads
  └────────────────────────────────────┘

  CountDownLatch: state = count. countDown() decrements via CAS.
                  await() parks if state > 0.
  Semaphore:      state = available permits. acquire() decrements.
                  release() increments. Fair: FIFO queue.
  ReentrantLock:  state = hold count. lock() increments (CAS).
                  unlock() decrements. State 0 = unlocked.
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "CountDownLatch and CyclicBarrier are interchangeable" | Latch: N producers count down, M consumers wait. One-shot. Asymmetric (signalers ≠ waiters). Barrier: N threads all wait for each other. Reusable. Symmetric (every thread is both signaler and waiter). Different coordination patterns. |
| "Semaphore is a lock" | Semaphore has no ownership — any thread can `release()`, even one that never `acquire()`d. A lock has ownership — only the holder can unlock. Binary semaphore (1 permit) resembles a lock but lacks reentrancy and ownership checking. |
| "CyclicBarrier resets on its own when a thread is interrupted" | If any thread is interrupted during `await()`, the barrier BREAKS — all waiting threads get `BrokenBarrierException`. The barrier stays broken until explicitly `reset()`. It does NOT auto-recover. |
| "Phaser is just a better CyclicBarrier" | Phaser adds dynamic party registration/deregistration, which CyclicBarrier can't do. But Phaser is more complex and has higher overhead for simple cases. Use CyclicBarrier when the party count is fixed; Phaser when threads join/leave dynamically. |

---

## 🐞 Production Footguns

---

> **Footgun: CountDownLatch without timeout**
> **Cost:** Thread hangs forever
>
> A service startup sequence used `latch.await()` (no timeout). One of the 5 services failed silently during initialization and never called `countDown()`. The main thread waited forever — the service appeared to start but never accepted traffic. No error message, no timeout, no health check failure.

```java
// ❌ The trap: await() without timeout
startupLatch.await();   // blocks FOREVER if any service fails to countDown()

// ✅ The fix: always use await with timeout
if (!startupLatch.await(30, TimeUnit.SECONDS)) {
    long remaining = startupLatch.getCount();
    throw new StartupException(remaining + " services failed to initialize within 30s");
}
```

---

> **Footgun: Semaphore release without acquire**
> **Cost:** Permit leak — concurrency limit broken
>
> A connection limiter used `Semaphore(10)`. On an error path, `release()` was called without a preceding `acquire()` — a bug in exception handling. Each occurrence added a phantom permit. After 100 errors, the semaphore had 110 permits instead of 10 — 110 concurrent connections were allowed, overwhelming the database.

```java
// ❌ The trap: release without acquire
try {
    connectionPermits.acquire();
    processRequest();
} catch (Exception e) {
    // Bug: release in catch AND finally → double release on error
    connectionPermits.release();
} finally {
    connectionPermits.release();   // always runs → double release on error path
}

// ✅ The fix: acquire outside try, release ONLY in finally
connectionPermits.acquire();   // if this throws InterruptedException, finally doesn't release
try {
    processRequest();
} finally {
    connectionPermits.release();   // exactly one release per acquire
}
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `locks-reentrant-readwrite.md` | CountDownLatch, Semaphore, and ReentrantLock are all built on AQS (AbstractQueuedSynchronizer). Understanding AQS explains how all three park/unpark threads and manage their internal state. |
| `java-memory-model.md` | `countDown()` happens-before `await()` returns. `release()` happens-before `acquire()`. These guarantees come from AQS's volatile state variable — same JMM happens-before as volatile writes/reads. |
| `thread-pool-executor.md` | Semaphore can throttle task submission to a thread pool — limiting how many tasks enter the pool. CountDownLatch can coordinate pool shutdown — await all submitted tasks before shutting down. |
| `completable-future.md` | CompletableFuture.allOf() is conceptually similar to CountDownLatch — wait for N async operations to complete. But allOf() is non-blocking (returns a CF), while latch.await() blocks the calling thread. |

---

## 🎙️ Interview Deep Questions

**Q1. What is the difference between CountDownLatch and CyclicBarrier?**

> CountDownLatch is asymmetric and one-shot: N threads call `countDown()` (non-blocking), M threads call `await()` (blocking). The signaling threads and waiting threads can be different groups. Once the count reaches 0, the latch is spent — it can never block again. CyclicBarrier is symmetric and reusable: all N threads call `await()` (blocking). They all block until the Nth thread arrives, then all N are released simultaneously. The barrier resets automatically for the next round. Use Latch when you're waiting for events (service started, task completed). Use Barrier when threads must synchronize at a rendezvous point before proceeding together.

**Q2. How does Semaphore differ from a Lock? When would you use each?**

> A Lock provides mutual exclusion — only ONE thread holds it. It has ownership (only the holder can release), reentrancy (same thread can re-acquire), and typically fairness options. A Semaphore provides concurrency limiting — N threads can hold permits simultaneously. It has no ownership (any thread can release a permit, even without acquiring one), no reentrancy, and no concept of "who holds what." Use Lock when only one thread should access a resource. Use Semaphore when N threads can access a resource concurrently (e.g., connection pool with 10 connections, rate limiter with 100 permits per second).

**Q3. What happens if a thread is interrupted while waiting at a CyclicBarrier?**

> The barrier BREAKS. The interrupted thread receives `InterruptedException`. All OTHER threads waiting at the barrier receive `BrokenBarrierException`. The barrier stays broken — subsequent calls to `await()` throw `BrokenBarrierException` immediately. The barrier can be reused only after calling `reset()`, but reset also throws `BrokenBarrierException` for any threads currently waiting. This all-or-nothing behavior ensures consistency — if one thread can't participate, the entire synchronization point fails rather than proceeding with incomplete results.

**Q4. What is AQS and why does it matter?**

> `AbstractQueuedSynchronizer` is the internal framework that `ReentrantLock`, `CountDownLatch`, `Semaphore`, `ReentrantReadWriteLock`, and `CyclicBarrier` (indirectly) are built on. It provides: a volatile `int state` variable (the synchronization state — lock hold count, latch count, semaphore permits), and a CLH FIFO queue of waiting threads (parked via `LockSupport.park()`). Each sync primitive defines what the state means and when threads should block/unblock. Understanding AQS means you can reason about the performance and behavior of ALL sync aids from a single mental model — they're all variations on "CAS the state, park if CAS fails, unpark when state allows."

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** CountDownLatch = one-shot gate (N count down, M wait). CyclicBarrier = reusable rendezvous (N threads wait for each other). Semaphore = permit-based concurrency limiter (N concurrent permits).
>
> **Part 2 — How/Why (30s):** All three are built on AQS — a volatile state variable + FIFO thread wait queue. Latch: state = count, `countDown()` decrements via CAS, `await()` parks until 0. Barrier: all N threads `await()`, last arrival triggers release + reset. Semaphore: state = permits, `acquire()` decrements, `release()` increments — any thread can release (no ownership). Phaser extends both patterns with dynamic party management — threads can register/deregister between phases.
>
> **Part 3 — Gotcha (20s):** Two traps: `await()` without timeout — if any thread fails to count down, the waiter blocks forever. Always use `await(timeout)`. And Semaphore `release()` without `acquire()` — adds phantom permits, breaking the concurrency limit. Structure as `acquire()` outside try, `release()` in finally.

---

## 🧾 TL;DR

- **CountDownLatch:** N count down, M wait. One-shot. Asymmetric (signalers ≠ waiters).
- **CyclicBarrier:** N all wait. Reusable. Symmetric. Has barrier action. Breaks on interrupt.
- **Semaphore:** N permits. No ownership. `acquire()` blocks when empty. `release()` adds permit.
- **Phaser:** dynamic parties, multi-phase. Combines Latch + Barrier. Use for complex phased coordination.
- **All built on AQS:** volatile state + CLH wait queue + CAS + LockSupport.park().
- **Always use `await(timeout)`** — never `await()` alone. Prevent infinite hangs.
- **Semaphore: `acquire()` outside try, `release()` in finally** — exactly one release per acquire.
- **Barrier breaks on interrupt** — all waiting threads get BrokenBarrierException.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #20 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: CountDownLatch (one-shot gate, testing "go" pattern), CyclicBarrier (reusable rendezvous, BrokenBarrierException semantics, barrier action), Semaphore (permit-based limiting, no ownership, rate limiter pattern, fair mode), Phaser (dynamic registration, multi-phase, onAdvance), AQS internals (volatile state + CLH queue). Two production footguns: await without timeout (infinite hang), release without acquire (phantom permits). |
