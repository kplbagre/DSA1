# 🧵 Fork/Join Framework + Parallel Streams — Deep Dive

> After this note you can explain work-stealing, write a `RecursiveTask`, explain why parallel streams use `ForkJoinPool.commonPool()`, and know the 5 conditions where parallel streams are SLOWER than sequential.

---

## 🎯 The Problem This Solves

You have a large array of 10 million elements to sum. A single thread iterates sequentially — O(n). With 8 CPU cores, you could split the array into 8 chunks, sum each chunk on a separate core, and combine — roughly 8x faster. But manually splitting, submitting to an executor, and combining results is tedious, error-prone, and doesn't adapt to CPU count or load. The Fork/Join framework automates divide-and-conquer parallelism with work-stealing — idle threads steal tasks from busy threads' queues.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Fork/Join** | A framework (Java 7) for divide-and-conquer parallelism. Fork = split a task into subtasks and submit them. Join = wait for subtask results and combine. Runs on `ForkJoinPool`. |
| **ForkJoinPool** | A thread pool optimized for fork/join tasks. Uses work-stealing: each thread has its own deque. Idle threads steal tasks from busy threads' deques. |
| **Work-stealing** | When a thread's local task queue is empty, it steals a task from the TAIL of another thread's deque. This balances load automatically without central coordination. |
| **RecursiveTask\<V\>** | A fork/join task that returns a result. Override `compute()`: if the problem is small enough, solve directly; otherwise, fork subtasks and join their results. |
| **RecursiveAction** | A fork/join task that returns no result (side-effect only). Same pattern as RecursiveTask but `compute()` returns void. |
| **commonPool()** | A shared `ForkJoinPool` with parallelism = `availableProcessors() - 1`. Used by `parallelStream()` and `CompletableFuture.supplyAsync()` (no executor). Shared across the ENTIRE JVM. |
| **Parallelism threshold** | The minimum problem size below which the task is solved sequentially instead of forking. Too low = excessive forking overhead. Too high = poor parallelism. |

---

## 🧠 Mental Model

Fork/Join is a **recursive tree of work**. The root task is "sum 10M elements." It forks into "sum first 5M" and "sum last 5M." Each of those forks again: "sum first 2.5M" and "sum last 2.5M." When a subtask is small enough (below threshold), it computes directly. Results join up the tree.

Work-stealing is the **load balancer**. Each thread has a personal deque (double-ended queue). It pushes new subtasks to the HEAD of its deque and pops work from the HEAD. Idle threads steal from the TAIL of other threads' deques. This keeps all cores busy even when the workload is unevenly split.

> If you can say "fork = split + submit subtasks; join = wait + combine results; work-stealing = idle threads take from busy threads' queues; commonPool is shared JVM-wide; parallel streams use it by default; blocking I/O in commonPool starves everything" without notes, you have Fork/Join.

---

## 🎨 Visual — Work-Stealing

```
  Thread 0 deque:        Thread 1 deque:        Thread 2 deque:
  ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
  │ HEAD         │       │ HEAD         │       │ HEAD         │
  │  task-0a     │       │  task-1a     │       │  (empty)     │
  │  task-0b     │       │  task-1b     │       │              │
  │  task-0c     │       │              │       │              │
  │ TAIL         │       │ TAIL         │       │ TAIL         │
  └──────────────┘       └──────────────┘       └──────────────┘
                                                       │
  Thread 0: pops from HEAD (LIFO — exploits cache locality)
  Thread 2: STEALS from Thread 0's TAIL (opposite end — minimal contention)

  After steal:
  Thread 0: [task-0a, task-0b]    Thread 2: [task-0c] (stolen)

  WHY LIFO for own work + FIFO for stealing:
  - Own work (LIFO): recently forked subtasks are small and hot in cache
  - Stolen work (FIFO): oldest tasks are the LARGEST → stealing one big task
    is more efficient than stealing many small ones

KEY INVARIANT:
   Work-stealing balances load WITHOUT central coordination.
   Each thread maintains its own deque. Stealing happens at the
   opposite end → minimal contention between the owner and the thief.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Manual parallel sum with ThreadPoolExecutor
int[] array = new int[10_000_000];
int chunks = 8;
int chunkSize = array.length / chunks;
ExecutorService pool = Executors.newFixedThreadPool(8);
List<Future<Long>> futures = new ArrayList<>();

for (int i = 0; i < chunks; i++) {
    int start = i * chunkSize;
    int end = (i == chunks - 1) ? array.length : start + chunkSize;
    futures.add(pool.submit(() -> {
        long sum = 0;
        for (int j = start; j < end; j++) { sum += array[j]; }
        return sum;
    }));
}

long total = 0;
for (Future<Long> f : futures) { total += f.get(); }
// Works. But: fixed 8 chunks, no recursive splitting, no work-stealing,
// no adaptation to uneven workloads, manual future management.
```

---

### Level 2 — The real mechanism

#### 2.1 — RecursiveTask

```java
// Tier 2 — Production: parallel sum with Fork/Join
public class ParallelSum extends RecursiveTask<Long> {
    private static final int THRESHOLD = 10_000;   // sequential threshold
    private final int[] array;
    private final int start, end;

    public ParallelSum(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Long compute() {
        int length = end - start;

        // Base case: small enough → compute sequentially
        if (length <= THRESHOLD) {
            long sum = 0;
            for (int i = start; i < end; i++) {
                sum += array[i];
            }
            return sum;
        }

        // Recursive case: split in half
        int mid = start + length / 2;
        ParallelSum leftTask = new ParallelSum(array, start, mid);
        ParallelSum rightTask = new ParallelSum(array, mid, end);

        leftTask.fork();          // submit left to the pool (async)
        long rightResult = rightTask.compute();  // compute right in THIS thread
        long leftResult = leftTask.join();       // wait for left's result

        return leftResult + rightResult;
    }
}

// Usage:
ForkJoinPool pool = new ForkJoinPool();   // or ForkJoinPool.commonPool()
long sum = pool.invoke(new ParallelSum(array, 0, array.length));
```

**⭐ The fork-compute-join pattern:**

```java
leftTask.fork();                    // fork left (async — goes to deque)
long rightResult = rightTask.compute();  // compute right LOCALLY (this thread)
long leftResult = leftTask.join();       // join left (block until done)

// Why compute right locally instead of forking both?
// If you fork BOTH:
//   leftTask.fork();
//   rightTask.fork();
//   leftResult = leftTask.join();    // THIS thread waits — idle!
//   rightResult = rightTask.join();
// The current thread submitted two tasks and then... sits idle waiting.
// Wasteful. By computing one task locally, the current thread stays busy.
```

#### 2.2 — Parallel streams = Fork/Join under the hood

```java
// parallelStream() uses ForkJoinPool.commonPool() internally
long sum = IntStream.range(0, 10_000_000)
    .parallel()
    .mapToLong(i -> array[i])
    .sum();
// Spliterator.trySplit() divides the IntStream range in half
// Each half is processed as a ForkJoinTask in commonPool()
// Results are combined via the stream's combiner (Long::sum for sum())
```

**Using a custom ForkJoinPool for parallel streams:**

```java
// Override the default commonPool for a specific parallel stream:
ForkJoinPool customPool = new ForkJoinPool(4);   // 4 threads instead of CPU-1

long sum = customPool.submit(() ->
    data.parallelStream()
        .mapToLong(this::process)
        .sum()
).get();   // .get() blocks until result is available
// The parallel stream runs on customPool, NOT commonPool
// ⚠️ This is an undocumented trick — it works because ForkJoinTask detects
//    which pool the calling thread belongs to. Not guaranteed by the spec.
```

#### 2.3 — When parallel streams are SLOWER

```
  5 CONDITIONS WHERE PARALLEL IS SLOWER:

  1. SMALL DATA (N < 10,000)
     Fork/join overhead (splitting, thread scheduling, combining)
     exceeds the computation saved by parallelism.

  2. POOR SPLITERATOR (LinkedList, Stream.iterate)
     LinkedList.spliterator() can't split by index — it must
     traverse to find the midpoint. O(n) to split is worse than
     O(n) to just process sequentially.
     Good spliterators: ArrayList, array, IntRange (index-based split).

  3. STATEFUL OPERATIONS (sorted, distinct)
     sorted() must collect ALL elements into an array, sort it,
     then re-split for downstream parallel ops. The sort is sequential.
     distinct() maintains a shared ConcurrentHashMap of seen elements —
     contention under parallel access.

  4. BLOCKING I/O
     Threads block on I/O → occupies commonPool threads → starves
     everything else in the JVM. Use CompletableFuture + custom
     executor or virtual threads instead.

  5. ORDER-DEPENDENT SIDE EFFECTS
     forEach() on parallelStream does NOT guarantee order.
     forEachOrdered() guarantees order but serializes the terminal
     operation — no parallelism benefit.
```

---

### Level 3 — The subtleties

#### 3.1 — Choosing the threshold

```java
// Too low threshold (e.g., 10):
// 10M elements / 10 = 1M tasks → excessive task creation overhead
// ForkJoinTask objects consume memory. Forking/joining has scheduling cost.

// Too high threshold (e.g., 5M):
// 10M / 5M = 2 tasks → only 2 threads used out of 8 → poor parallelism

// Rule of thumb: threshold ≈ N / (4 × parallelism)
// 10M elements, 8 cores: threshold ≈ 10M / 32 = ~300,000
// This creates ~32 tasks — each core gets ~4 tasks for good work-stealing balance

// In practice: benchmark with different thresholds on your target hardware.
// Start with 10,000–100,000 for array/collection processing.
```

#### 3.2 — `commonPool()` parallelism configuration

```java
// Default: availableProcessors() - 1
// 8-core machine: 7 threads in commonPool
// (The 8th core handles the calling thread + OS + other JVM work)

// Override via system property (set before JVM starts):
// -Djava.util.concurrent.ForkJoinPool.common.parallelism=16

// ⚠️ This affects ALL users of commonPool in the JVM:
// - parallel streams
// - CompletableFuture.supplyAsync() (no executor)
// - Any code using ForkJoinPool.commonPool()
// Changing it for one use case affects every other.
```

#### 3.3 — Spliterator quality determines parallel performance

| Source | Spliterator quality | Why |
|---|---|---|
| `ArrayList` | ⭐ Excellent | Index-based — splits by dividing index range. O(1) per split. |
| `int[]` / `IntStream.range()` | ⭐ Excellent | Same — index arithmetic. |
| `HashSet` | ⚡ Good | Splits by bucket ranges. Uneven but reasonable. |
| `TreeMap` | ⚡ Decent | Can split subtrees. Balanced tree → balanced splits. |
| `LinkedList` | ❌ Poor | Must traverse to find midpoint. O(n/2) per split. |
| `Stream.iterate()` | ❌ Terrible | Cannot split at all (sequential dependency between elements). |
| `BufferedReader.lines()` | ❌ Poor | Cannot seek — must read sequentially to find split points. |

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Parallel streams are always faster" | Parallel adds overhead (fork, join, combine, thread scheduling). For small data, poor spliterators, I/O-bound work, or stateful operations, sequential is faster. Benchmark before assuming. |
| "Fork/Join creates a thread per subtask" | Fork/Join reuses a fixed pool of threads (commonPool has ~CPU count threads). Tasks are placed on deques and picked up by existing threads. No new threads are created for subtasks. |
| "I should fork both subtasks" | Fork one, compute the other locally. Forking both wastes the current thread (it sits idle waiting). The fork-compute-join pattern keeps every thread busy. |
| "commonPool has a thread per CPU core" | commonPool has `availableProcessors() - 1` threads. On an 8-core machine: 7 pool threads. The calling thread is the 8th worker. |
| "Parallel stream ordering doesn't matter" | `forEachOrdered()` preserves encounter order in parallel streams — but at the cost of serializing the terminal operation. `forEach()` is unordered in parallel. `toList()` preserves encounter order (elements are in the right order in the result list, even if processed out of order). |

---

## 🐞 Production Footguns

---

> **Footgun: Parallel stream on LinkedList**
> **Cost:** Performance cliff — slower than sequential
>
> A data pipeline used `linkedList.parallelStream().map(...).toList()`. LinkedList's Spliterator traverses half the list for each split — O(n/2) work just to divide the data. For 1M elements, splitting alone consumed more CPU than the actual transformation. Sequential was 3x faster.

```java
// ❌ The trap: parallel on LinkedList
List<Item> items = new LinkedList<>(data);   // poor Spliterator
List<Result> results = items.parallelStream().map(this::process).toList();

// ✅ The fix: convert to ArrayList first, or use sequential
List<Item> items = new ArrayList<>(data);   // excellent Spliterator
List<Result> results = items.parallelStream().map(this::process).toList();
```

---

> **Footgun: Non-associative reduce in parallel**
> **Cost:** Wrong results (silent data corruption)
>
> A financial calculation used `parallelStream().reduce()` with subtraction as the combiner. Subtraction is not associative: `(10-3)-2 = 5` but `10-(3-2) = 9`. In parallel mode, chunks are reduced independently then combined — non-associative combining produces different results depending on how chunks are split. The calculation returned different values on different runs.

```java
// ❌ The trap: non-associative reduce in parallel
double result = values.parallelStream()
    .reduce(0.0, (a, b) -> a - b);   // subtraction is NOT associative
// Result varies per run. Sequential: deterministic. Parallel: non-deterministic.

// ✅ The fix: use associative operations only
double result = values.parallelStream()
    .reduce(0.0, Double::sum);   // addition IS associative — same result sequential or parallel
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `stream-pipeline-internals.md` | `parallelStream()` uses ForkJoinPool.commonPool() internally. Spliterator.trySplit() is the fork mechanism. Stream's combiner function is the join mechanism. |
| `thread-pool-executor.md` | ForkJoinPool is a specialized executor. Unlike ThreadPoolExecutor (which uses a shared work queue), ForkJoinPool gives each thread its own deque + work-stealing. This makes it optimal for recursive divide-and-conquer but poor for independent tasks. |
| `synchronized-volatile.md` | Work-stealing uses CAS (not synchronized) to steal from deque tails. The deque is a lock-free data structure — low contention between the owning thread (HEAD) and stealing threads (TAIL). |
| `virtual-threads-java21.md` (planned — Note #22) | Virtual threads replace parallel streams for I/O-bound parallelism. For CPU-bound: Fork/Join and parallel streams remain optimal. For I/O-bound: virtual threads avoid starving the commonPool. |
| `completable-future.md` | `CompletableFuture.supplyAsync()` without executor uses `commonPool()` — same pool as parallel streams. Blocking I/O in either starves the other. |

---

## 🎙️ Interview Deep Questions

**Q1. What is work-stealing and why does ForkJoinPool use it?**

> Each ForkJoinPool thread has its own double-ended queue (deque). When a thread forks a subtask, it pushes it to the HEAD of its deque. When it needs work, it pops from the HEAD (LIFO — recently forked subtasks are small and hot in cache). When a thread's deque is empty, it STEALS from the TAIL of another thread's deque (FIFO — oldest tasks are largest, giving the thief more work). This balances load without central coordination — no shared work queue, no global lock. The HEAD/TAIL separation minimizes contention between owner and thief. It's optimal for recursive divide-and-conquer where one thread may generate more subtasks than others.

**Q2. What is the fork-compute-join pattern? Why not fork both subtasks?**

> The pattern: `left.fork()` (submit left asynchronously), `right.compute()` (compute right in the current thread), `left.join()` (wait for left's result). If you fork BOTH subtasks, the current thread sits idle waiting for results — it contributed two tasks to the pool but does no work itself. By computing one subtask locally, the current thread stays productive. This is essential for efficiency — in a pool with N threads, you want N threads computing, not N threads all waiting for their forked children.

**Q3. When are parallel streams SLOWER than sequential?**

> Five conditions: (1) Small data — fork/join overhead exceeds computation savings for N < ~10,000. (2) Poor Spliterator — LinkedList, Stream.iterate can't split efficiently. (3) Stateful operations — sorted() must collect all elements sequentially; distinct() uses a shared ConcurrentHashMap with contention. (4) Blocking I/O — ties up commonPool threads, starving the entire JVM. (5) Non-associative operations — reduce with subtraction produces different results per run. Also: `forEachOrdered()` serializes the terminal operation, eliminating parallelism benefit.

**Q4. How can you run a parallel stream on a custom ForkJoinPool instead of commonPool()?**

> Submit the stream operation as a task to a custom ForkJoinPool: `customPool.submit(() -> data.parallelStream().map(...).toList()).get()`. The parallel stream detects which ForkJoinPool the calling thread belongs to and uses that pool instead of commonPool. This is useful for isolating CPU-bound parallel work from the shared pool that CompletableFuture and other parallel streams use. Caveat: this behavior is based on implementation details, not a specification guarantee — it works on all current JVMs but isn't formally documented.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Fork/Join is a divide-and-conquer framework. Fork = split into subtasks. Join = combine results. ForkJoinPool uses work-stealing — idle threads steal from busy threads' deques. Parallel streams use ForkJoinPool.commonPool() internally.
>
> **Part 2 — How/Why (30s):** A RecursiveTask splits work recursively until the chunk is below a threshold, then computes sequentially. The fork-compute-join pattern keeps the current thread busy: fork one subtask, compute the other locally, join the forked one. Work-stealing balances load without central coordination — each thread has its own deque, steals from the tail of others. commonPool parallelism = CPU cores - 1. Spliterator.trySplit() is how parallel streams divide data for fork/join processing.
>
> **Part 3 — Gotcha (20s):** Parallel streams are SLOWER for: small data (overhead > benefit), LinkedList (poor spliterator — O(n) to split), I/O-bound work (blocks commonPool — use CompletableFuture + custom executor), and non-associative reduce (subtraction gives wrong results in parallel). Always benchmark before parallelizing — the default sequential stream is correct and often fast enough.

---

## 🧾 TL;DR

- **Fork/Join** = recursive divide-and-conquer. Fork one subtask, compute the other locally, join.
- **Work-stealing** = each thread has a deque. Own work: pop HEAD (LIFO). Steal: take TAIL (FIFO).
- **commonPool()** = `availableProcessors() - 1` threads. Shared JVM-wide.
- **Parallel streams** use commonPool via Spliterator.trySplit(). Good for CPU-bound + large data + good Spliterator.
- **5 anti-patterns:** small data, poor Spliterator (LinkedList), stateful ops (sorted), blocking I/O, non-associative reduce.
- **Threshold** ≈ N / (4 × parallelism). Too low = fork overhead. Too high = poor parallelism.
- **Custom ForkJoinPool** for parallel streams: `pool.submit(() -> stream.parallel()....).get()`.
- **reduce() accumulator MUST be associative** for parallel correctness.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #21 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: ForkJoinPool work-stealing (per-thread deque, HEAD/TAIL separation, stealing heuristic), RecursiveTask fork-compute-join pattern (why not fork both), Spliterator quality table (ArrayList excellent, LinkedList poor), parallel stream 5 anti-patterns, commonPool sizing + configuration, custom ForkJoinPool for parallel streams (undocumented trick), threshold sizing rule of thumb. Two production footguns: parallel on LinkedList, non-associative reduce. |
