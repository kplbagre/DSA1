# ☕ Stream Pipeline Internals — Deep Dive

> After this note you can explain why streams are lazy, what a Spliterator is, why `peek()` doesn't fire without a terminal operation, how parallel streams use the ForkJoinPool, and why `Stream.sorted()` breaks the lazy pipeline.

---

## 🎯 The Problem This Solves

Before Java 8, processing a collection meant writing imperative loops — nested `for` loops with `if` checks, temporary lists, manual accumulation. The logic of WHAT you wanted (filter, transform, aggregate) was buried inside HOW you iterated (index management, null checks, result assembly). You couldn't compose operations (filter-then-map-then-sort) without creating intermediate lists at each step, wasting memory and CPU.

Streams separate the WHAT from the HOW. You declare a pipeline of operations (`filter → map → sorted → collect`), and the stream executes them in a single pass over the data — fusing operations, short-circuiting when possible, and optionally parallelizing across CPU cores. But the mechanism behind this — lazy evaluation, Spliterators, stream characteristics, and the pitfalls of parallel streams — is what separates a developer who uses streams from one who understands them.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Stream** | A sequence of elements supporting aggregate operations. NOT a data structure — a stream doesn't store data. It pulls elements from a source (collection, array, generator) and pushes them through a pipeline of operations. |
| **Pipeline** | A chain of stream operations: one source, zero or more intermediate operations, and one terminal operation. Nothing executes until the terminal operation is invoked (lazy evaluation). |
| **Intermediate operation** | An operation that returns another Stream. Lazy — doesn't process any elements when called. Examples: `filter()`, `map()`, `sorted()`, `distinct()`, `flatMap()`, `peek()`. |
| **Terminal operation** | An operation that triggers processing of the entire pipeline and produces a result (or a side effect). Examples: `collect()`, `forEach()`, `reduce()`, `count()`, `findFirst()`, `toList()`. |
| **Lazy evaluation** | Elements are processed only when needed by the terminal operation. If `findFirst()` finds a match after 3 elements, the remaining 999,997 elements are never touched. |
| **Spliterator** | "Splittable iterator" — the underlying mechanism that feeds elements to a stream. Knows how to traverse elements AND how to split the source in half for parallel processing. Every collection has a `spliterator()` method. |
| **Stream characteristics** | Metadata flags on a Spliterator that describe the source: `ORDERED` (sequence matters), `SORTED` (elements are pre-sorted), `SIZED` (exact element count known), `DISTINCT` (no duplicates). The stream pipeline uses these to optimize — e.g., `distinct()` on a `DISTINCT` source is a no-op. |
| **Short-circuiting** | An operation that can produce a result without processing all elements. `findFirst()`, `anyMatch()`, `limit()` are short-circuiting. `sorted()`, `collect()`, `forEach()` are NOT — they must see all elements. |
| **Encounter order** | The order in which elements are presented to the pipeline. Lists have encounter order (index-based). HashSets do not. `unordered()` explicitly drops encounter order — can improve parallel performance. |

---

## 🧠 Mental Model

Think of a stream pipeline as a **factory assembly line** that doesn't start until the customer places an order (terminal operation). You set up stations on the line: a filter station, a transformation station, a sorting station. But the conveyor belt doesn't move — no materials are consumed, no energy is spent — until the terminal operation says "go." Then each element from the source travels through all the stations in sequence — one element visits filter, then map, then sorted — before the next element starts. This is called **loop fusion** — instead of one complete pass per operation (N passes for N operations), all operations happen in one fused pass per element.

Short-circuiting is a "stop the line" signal: `findFirst()` says "I have what I need — shut down the conveyor belt," and the remaining source elements are never pulled.

> If you can say "streams are lazy — nothing runs until a terminal operation; elements flow vertically (one element through all ops) not horizontally (all elements through one op); Spliterator is the source feeder; parallel streams split via Spliterator and process on ForkJoinPool" without notes, you have stream internals.

---

## 🎨 Visual — Lazy Evaluation and Loop Fusion

```
  HORIZONTAL (what you might THINK happens — N passes):
  Source:  [1, 2, 3, 4, 5]
  filter:  [2, 4]           ← pass 1: check all 5
  map:     [20, 40]         ← pass 2: transform 2 elements
  find:    20               ← pass 3: pick first

  VERTICAL (what ACTUALLY happens — loop fusion, one pass):
  Element 1: filter(1) → fails → SKIP
  Element 2: filter(2) → passes → map(2) → 20 → findFirst() → DONE!
  Elements 3, 4, 5: NEVER TOUCHED

  Only 2 elements processed. Zero intermediate collections created.

  ┌────────┐    ┌────────┐    ┌────────┐    ┌──────────┐
  │ Source │──►│ filter │──►│  map   │──►│ findFirst│
  │ [1..5] │    │ x > 1  │    │ x * 10 │    │ TERMINAL │
  └────────┘    └────────┘    └────────┘    └──────────┘
       │              │              │              │
  el 1 ─────► fails ──┘              │              │
  el 2 ─────► passes ──────► 20 ───────────► DONE!  │
  el 3 ─────► never pulled                          │
  el 4 ─────► never pulled                          │
  el 5 ─────► never pulled                          │

KEY INVARIANT:
   Elements flow VERTICALLY through the pipeline (one element through
   all ops before the next starts). NOT horizontally (all elements
   through one op before the next op starts).
   This is why lazy + short-circuiting can skip most elements.
```

---

## 🎨 Visual — Parallel Stream Splitting

```
  SEQUENTIAL:
  Source [1, 2, 3, 4, 5, 6, 7, 8]
       │
       ▼ (one thread processes all 8)
  filter → map → collect → result

  PARALLEL:
  Source [1, 2, 3, 4, 5, 6, 7, 8]
       │
       ├──── Spliterator.trySplit() ────┐
       ▼                                ▼
  [1, 2, 3, 4]                    [5, 6, 7, 8]
  Thread 1: filter→map→partial     Thread 2: filter→map→partial
       │                                │
       └──────── combine ───────────────┘
                    │
                    ▼
              final result

  All parallel work happens on ForkJoinPool.commonPool().
  Default parallelism = Runtime.getRuntime().availableProcessors() - 1.

KEY INVARIANT:
   Spliterator.trySplit() divides the source in half.
   Each half is processed independently on a separate thread.
   Results are combined via the Collector's combiner function.
   The quality of the split determines the parallelism benefit.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Tier 1 — Demo: imperative approach — works but buried intent
List<String> names = List.of("Alice", "", "Bob", "Charlie", "", "David");

// Goal: non-empty names, uppercased, sorted, collected
List<String> result = new ArrayList<>();
for (String name : names) {
    if (!name.isEmpty()) {
        result.add(name.toUpperCase());
    }
}
Collections.sort(result);
// result: [ALICE, BOB, CHARLIE, DAVID]
// ⚠️ NOT thread-safe — result is mutable, shared state
```

Problems:
1. **Intermediate list** — `result` is a mutable temporary. Memory waste for large data.
2. **Intent buried** — the WHAT (filter, transform, sort) is interleaved with HOW (loop, if, add).
3. **Hard to parallelize** — parallelizing a `for` loop requires manual thread management.

```java
// Tier 1 — Demo: stream approach — declarative, no intermediate list
List<String> result = names.stream()
    .filter(name -> !name.isEmpty())
    .map(String::toUpperCase)
    .sorted()
    .toList();   // Java 16+; before: .collect(Collectors.toList())
// Same result. No temporary list. Intent is the entire statement.
// ✅ Thread-safe — toList() returns an unmodifiable list
```

---

### Level 2 — The real mechanism

#### 2.1 — Stream pipeline construction (lazy — no work yet)

When you call `.filter().map().sorted()`, no elements are processed. Each call returns a new `Stream` object that wraps the previous one — building a linked list of operations.

```java
// What ACTUALLY happens at each method call:
Stream<String> s1 = names.stream();           // creates a Head stream wrapping the source's Spliterator
Stream<String> s2 = s1.filter(n -> !n.isEmpty());  // creates a StatelessOp wrapping s1
Stream<String> s3 = s2.map(String::toUpperCase);   // creates a StatelessOp wrapping s2
Stream<String> s4 = s3.sorted();              // creates a StatefulOp wrapping s3 (sorted needs ALL elements)

// At this point: zero elements have been read from the source.
// s4 is the pipeline HEAD. It holds a chain: s4 → s3 → s2 → s1 → source.

List<String> result = s4.toList();   // TERMINAL — NOW the pipeline executes
```

> **What the JVM is actually doing:** Each intermediate operation creates a `ReferencePipeline.StatelessOp` or `StatefulOp` object. These are lightweight objects — just a function reference and a pointer to the previous stage. No data copies. When the terminal operation runs, the pipeline calls `Spliterator.forEachRemaining()` on the source, passing a chain of `Sink` objects — each Sink wraps one operation's logic and passes its output to the next Sink. This Sink chain is what achieves loop fusion: one element flows through all Sinks before the next element is pulled.

#### 2.2 — Terminal operations trigger execution

```java
// Tier 1 — Demo: nothing happens without a terminal
Stream<String> s = names.stream()
    .filter(n -> { System.out.println("filtering: " + n); return !n.isEmpty(); })
    .map(n -> { System.out.println("mapping: " + n); return n.toUpperCase(); });
// NOTHING printed yet — filter and map haven't run

s.toList();  // NOW everything runs:
// filtering: Alice
// mapping: Alice       ← Alice goes through BOTH ops before Bob starts
// filtering:           ← empty string, filter fails, map not called
// filtering: Bob
// mapping: Bob
// ... etc
```

#### 2.3 — Stateless vs stateful operations

| Type | Operations | Memory | Can short-circuit? |
|---|---|---|---|
| **Stateless** | `filter`, `map`, `flatMap`, `peek`, `mapToInt` | O(1) per element | Yes |
| **Stateful** | `sorted`, `distinct`, `limit`, `skip` | O(n) — must buffer | `sorted`: no. `limit`: yes. `distinct`: depends. |

**`sorted()` breaks the lazy pipeline:**

```java
// sorted() must see ALL elements before it can emit the first one.
// It internally collects everything into an array, sorts it, then emits.
Stream.of(5, 3, 1, 4, 2)
    .filter(n -> { System.out.println("filter: " + n); return n > 1; })
    .sorted()                // ← BARRIER: must consume all filtered elements first
    .map(n -> { System.out.println("map: " + n); return n * 10; })
    .findFirst();
// filter: 5   (passes)
// filter: 3   (passes)
// filter: 1   (fails)
// filter: 4   (passes)
// filter: 2   (passes)     ← ALL elements filtered — sorted needs them all
// map: 2      ← sorted emits smallest first
// findFirst → 20 → DONE
// map for 3, 4, 5 is NOT called — findFirst short-circuits AFTER sorted
```

#### 2.4 — Stream sources and Spliterator

Every stream starts from a source that provides a `Spliterator`:

```java
// Collection source — most common
List.of(1, 2, 3).stream();          // ArrayList.spliterator(): SIZED, ORDERED, SUBSIZED
Set.of(1, 2, 3).stream();           // HashSet.spliterator(): SIZED, DISTINCT (NOT ORDERED)

// Array source
Arrays.stream(new int[]{1, 2, 3});   // SIZED, ORDERED, SUBSIZED, IMMUTABLE

// Generator source
Stream.iterate(0, n -> n + 2);       // ORDERED only. NOT SIZED — infinite!
Stream.generate(Math::random);       // nothing — unordered, unsized, infinite

// Builder source
Stream.<String>builder().add("a").add("b").build();   // SIZED, ORDERED
```

**Characteristics matter for optimization:**

```java
// SIZED source: count() returns immediately without processing elements
long count = List.of(1, 2, 3).stream().count();   // O(1) — Spliterator knows the size

// But adding a filter removes SIZED:
long count2 = List.of(1, 2, 3).stream().filter(n -> n > 1).count();
// O(n) — must actually run the filter to know how many pass

// DISTINCT source: .distinct() on a Set is a no-op
Set.of(1, 2, 3).stream().distinct();   // no work done — already distinct
```

#### 2.5 — `flatMap()` — one-to-many transformation

`map()` is one-to-one: each element produces exactly one output. `flatMap()` is one-to-many: each element produces a stream of outputs, and all streams are flattened into one.

```java
// Tier 1 — Demo: map vs flatMap
List<List<String>> nested = List.of(
    List.of("a", "b"),
    List.of("c", "d", "e")
);

// map: [[a, b], [c, d, e]] → [Stream[a,b], Stream[c,d,e]]  ← stream of streams (wrong)
// flatMap: [[a, b], [c, d, e]] → [a, b, c, d, e]           ← flattened (right)

List<String> flat = nested.stream()
    .flatMap(Collection::stream)   // each inner list → stream → flattened
    .toList();
// [a, b, c, d, e]

// Real-world: extracting all order items across all orders
List<OrderItem> allItems = orders.stream()
    .flatMap(order -> order.getItems().stream())
    .toList();
```

#### 2.6 — `reduce()` — fold elements into a single value

```java
// Tier 1 — Demo: reduce patterns
// Sum (with identity)
int sum = List.of(1, 2, 3, 4, 5).stream()
    .reduce(0, Integer::sum);
// 0 + 1 + 2 + 3 + 4 + 5 = 15. Identity 0 is returned for empty streams.

// Max (without identity — returns Optional)
Optional<Integer> max = List.of(1, 2, 3).stream()
    .reduce(Integer::max);
// Optional[3]. Returns empty Optional for empty streams.

// String concatenation (avoid — O(n²) for large streams, use Collectors.joining())
String joined = List.of("a", "b", "c").stream()
    .reduce("", (a, b) -> a + b);   // "abc" — but creates intermediate strings
```

**The accumulator must be associative** for parallel correctness: `(a ⊕ b) ⊕ c == a ⊕ (b ⊕ c)`. Addition is associative. Subtraction is NOT — `(10 - 3) - 2 = 5` but `10 - (3 - 2) = 9`. Using non-associative operations with parallel reduce produces wrong results silently.

---

### Level 3 — The subtleties

#### 3.1 — Parallel streams: when they help, when they hurt

```java
// ✅ Parallel HELPS: CPU-bound, large data, no shared state
List<Double> results = hugeList.parallelStream()   // millions of elements
    .map(item -> expensiveComputation(item))        // CPU-bound per element
    .toList();

// ❌ Parallel HURTS: small data (overhead > benefit)
List.of(1, 2, 3).parallelStream().map(n -> n * 2).toList();
// Splitting, thread scheduling, combining costs MORE than processing 3 elements

// ❌ Parallel HURTS: I/O-bound (threads block, ForkJoinPool starves)
orders.parallelStream()
    .map(order -> httpClient.send(request, BodyHandlers.ofString()))   // blocks carrier threads
    .toList();
// ForkJoinPool.commonPool() has limited threads (# CPUs - 1).
// Blocking I/O ties up ALL of them → rest of the app starves.
// Use CompletableFuture or virtual threads (Java 21) for I/O parallelism.

// ❌ Parallel HURTS: order-dependent operations
list.parallelStream().forEach(item -> outputList.add(item));
// ⚠️ NOT thread-safe — ArrayList.add from multiple threads → data corruption
// Use .collect() or forEachOrdered() instead
```

**Rule of thumb:** parallel streams benefit when `N * Q` is large (N = element count, Q = computation per element). For N < 10,000 or Q < 100μs, sequential is usually faster.

#### 3.2 — `peek()` is for debugging ONLY

```java
// peek() is an intermediate operation — its Consumer is called only when
// elements flow through the pipeline (driven by a terminal operation).

// ❌ Anti-pattern: using peek() for side effects
stream.peek(item -> item.setProcessed(true))   // mutating state in peek
    .collect(Collectors.toList());
// This WORKS but is fragile — if the terminal changes to findFirst(),
// peek() only runs for the elements that flow through, not all elements.

// ✅ Correct use: debugging to see what's flowing
stream
    .filter(n -> n > 0)
    .peek(n -> System.out.println("after filter: " + n))
    .map(n -> n * 2)
    .peek(n -> System.out.println("after map: " + n))
    .toList();
```

#### 3.3 — Stream is single-use

```java
// A stream can be consumed ONLY ONCE:
Stream<String> s = List.of("a", "b").stream();
s.toList();       // works
s.toList();       // IllegalStateException: stream has already been operated upon or closed
// Streams are NOT reusable. Create a new stream each time.
```

#### 3.4 — `toList()` (Java 16) vs `collect(Collectors.toList())`

```java
// Java 16+: .toList() returns an UNMODIFIABLE list
List<String> list = stream.toList();
list.add("new");   // UnsupportedOperationException

// Pre-Java-16: collect(Collectors.toList()) returns a MUTABLE ArrayList
List<String> list = stream.collect(Collectors.toList());
list.add("new");   // works — it's a regular ArrayList
// ⚠️ NOT thread-safe — the returned ArrayList is mutable and unsynchronized

// Choose based on whether you need mutability:
// Need mutable → .collect(Collectors.toList()) or .collect(Collectors.toCollection(ArrayList::new))
// Need immutable → .toList() (Java 16+) or .collect(Collectors.toUnmodifiableList()) (Java 10+)
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Streams store data like collections" | Streams don't store anything. They pull elements from a source on demand. Once consumed, the elements are gone — you can't rewind or reuse a stream. |
| "Each intermediate operation processes all elements before the next starts" | Operations are fused — each element flows through ALL operations before the next element starts (vertical, not horizontal). This is why short-circuiting works. |
| "Parallel streams are always faster" | Parallel streams add overhead: splitting, thread scheduling, combining. For small data or I/O-bound work, they're SLOWER. They help only for CPU-bound work with large datasets. And they use `ForkJoinPool.commonPool()` — blocking I/O starves the shared pool. |
| "`count()` always iterates all elements" | If the stream has a SIZED Spliterator and no operations that change the size (like `filter`), `count()` returns the known size in O(1) without processing elements. Adding `filter` removes the SIZED characteristic, forcing O(n). |
| "`peek()` runs for all elements" | `peek()` is lazy — it runs only for elements that actually flow through the pipeline. If a downstream `findFirst()` stops after 1 element, `peek()` runs once, not for all elements. |

---

## 🐞 Production Footguns

---

> **Footgun: Parallel stream with shared mutable state**
> **Cost:** Race condition / data corruption
>
> In a report generation service, a developer used `parallelStream()` with `forEach()` to add items to an `ArrayList`. Multiple threads called `ArrayList.add()` concurrently — no synchronization, no atomic operations. The result: missing elements, duplicate elements, and occasionally `ArrayIndexOutOfBoundsException` from internal array resizing during concurrent writes.

```java
// ❌ The trap: mutable accumulation in parallel forEach
List<Result> results = new ArrayList<>();   // NOT thread-safe
data.parallelStream()
    .map(this::process)
    .forEach(results::add);   // concurrent add() → data corruption

// ✅ The fix: use collect() — thread-safe accumulation
List<Result> results = data.parallelStream()
    .map(this::process)
    .collect(Collectors.toList());   // Collector handles thread-safe accumulation
// Or: .toList() for unmodifiable result (Java 16+)
```

---

> **Footgun: Parallel stream blocking I/O starves ForkJoinPool**
> **Cost:** Application-wide latency spike
>
> A microservice used `parallelStream()` to make HTTP calls to an external API — 200 items, each call taking ~500ms. The `ForkJoinPool.commonPool()` has `availableProcessors() - 1` threads (typically 7). Seven calls ran in parallel, but 193 were queued. Since the pool is SHARED across the entire JVM, other parallel streams and `CompletableFuture.supplyAsync()` calls in the same application were starved — the entire app's latency spiked because all common pool threads were blocked on I/O.

```java
// ❌ The trap: I/O in parallel stream
List<ApiResponse> responses = items.parallelStream()
    .map(item -> httpClient.send(buildRequest(item), BodyHandlers.ofString()))
    .map(this::parseResponse)
    .toList();
// Blocks 7 ForkJoinPool threads on I/O → starves entire application

// ✅ The fix: use CompletableFuture with a dedicated executor
ExecutorService ioExecutor = Executors.newFixedThreadPool(20);   // dedicated pool for I/O

List<CompletableFuture<ApiResponse>> futures = items.stream()
    .map(item -> CompletableFuture.supplyAsync(
        () -> httpClient.send(buildRequest(item), BodyHandlers.ofString()),
        ioExecutor   // NOT the common pool
    ).thenApply(this::parseResponse))
    .toList();

List<ApiResponse> responses = futures.stream()
    .map(CompletableFuture::join)
    .toList();
ioExecutor.shutdown();

// ✅ Or in Java 21: virtual threads — one per task, no pool sizing needed
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<ApiResponse>> futures2 = items.stream()
        .map(item -> executor.submit(() -> parseResponse(
            httpClient.send(buildRequest(item), BodyHandlers.ofString()))))
        .toList();
}
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `functional-interfaces.md` | Every stream operation takes a functional interface: `filter(Predicate)`, `map(Function)`, `forEach(Consumer)`, `reduce(BinaryOperator)`. Understanding the 4 core shapes is prerequisite for understanding stream operations. |
| `generics-type-erasure.md` | `Stream<T>` is generic. `Collector<T,A,R>` has 3 type parameters. Wildcards appear throughout: `map(Function<? super T, ? extends R>)` uses PECS. Type erasure means the JVM sees `Object` at runtime. |
| `collectors-deepdive.md` (planned — Note #9) | `collect()` is the most powerful terminal operation. The Collectors API (`groupingBy`, `partitioningBy`, `teeing`, custom Collectors) is covered in the next note. |
| `completable-future.md` (planned — Note #11) | CompletableFuture is the async equivalent of Stream — `thenApply` ≈ `map`, `thenCompose` ≈ `flatMap`. Both share the ForkJoinPool.commonPool() by default, which is why blocking I/O in either starves the other. |
| `java-version-evolution.md` | Streams were added in Java 8 as the centerpiece feature. `toList()` was added in Java 16. Primitive specializations (`IntStream`, `LongStream`) avoid boxing overhead. |

---

## 🎙️ Interview Deep Questions

**Q1. Explain lazy evaluation in streams. Why is it important?**

> Stream intermediate operations are lazy — calling `filter()` or `map()` doesn't process any elements. They build a pipeline of operation descriptors. Processing only starts when a terminal operation (`collect()`, `findFirst()`, `count()`) is invoked. This matters for two reasons: first, loop fusion — instead of making N passes over the data (one per operation), all operations are fused into a single pass. Each element flows through filter, then map, then sorted, before the next element starts. Second, short-circuiting — if `findFirst()` finds a match after 3 elements, the remaining elements are never pulled from the source. On a list of 1 million elements, this can mean processing 3 instead of 1 million.

**Q2. What is a Spliterator? How does it enable parallel streams?**

> A Spliterator (splittable iterator) is the source mechanism behind every stream. It has two key capabilities: `tryAdvance()` which processes one element (like `Iterator.next()`), and `trySplit()` which divides the source into two halves — one stays with the current Spliterator, the other is returned as a new Spliterator. For parallel streams, the ForkJoinPool calls `trySplit()` recursively to divide work across threads. Each thread processes its sub-Spliterator independently, and results are combined via the Collector's combiner. The quality of the split matters: `ArrayList.spliterator()` splits cleanly by index range (good parallelism), while `LinkedList.spliterator()` can't split efficiently (poor parallelism). Spliterators also carry characteristics (`SIZED`, `ORDERED`, `DISTINCT`, `SORTED`) that the pipeline uses for optimization.

**Q3. Why is `sorted()` called a "stateful" operation and why does it break laziness?**

> `sorted()` must see ALL elements before it can emit the first one — it can't emit the smallest element until it knows there isn't a smaller one later. So `sorted()` acts as a barrier: it consumes everything upstream, stores it in an internal array, sorts the array, then emits elements downstream. This means operations BEFORE `sorted()` run eagerly (driven by `sorted()`'s consumption), while operations AFTER `sorted()` are still lazy (driven by the terminal). Other stateful operations: `distinct()` uses a HashSet to track seen elements (O(n) memory), `limit()` is a counter (O(1) memory but still stateful), `skip()` counts elements to skip.

**Q4. When should you NOT use parallel streams?**

> Don't use parallel streams for: small data (N < 10,000 — splitting/combining overhead exceeds computation), I/O-bound work (blocking calls tie up ForkJoinPool.commonPool threads, starving the entire JVM), operations with shared mutable state (ArrayList.add from forEach — race condition), and order-dependent operations where encounter order matters (parallel forEach doesn't guarantee order — use forEachOrdered, which serializes). Parallel streams are optimal for CPU-bound computation on large datasets with good Spliterator splitting — typically numerical processing, image transformation, or data aggregation on collections of 100K+ elements.

**Q5. What is the difference between `toList()` (Java 16) and `collect(Collectors.toList())`?**

> `toList()` returns an unmodifiable list — calling `add()`, `remove()`, or `set()` on the result throws `UnsupportedOperationException`. `collect(Collectors.toList())` returns a mutable `ArrayList` — you can modify it freely. Choose `toList()` when the result shouldn't be modified (safer, communicates intent). Choose `Collectors.toList()` when downstream code needs to add/remove elements. A third option, `Collectors.toUnmodifiableList()` (Java 10+), returns an unmodifiable list like `toList()` but additionally rejects null elements — it throws `NullPointerException` if any element is null.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** A stream is a lazy pipeline of operations on a data source. Nothing executes until a terminal operation is called. Elements flow vertically through all operations — loop fusion — not horizontally one operation at a time.
>
> **Part 2 — How/Why (30s):** Each intermediate operation returns a new Stream wrapping the previous — building a chain. The terminal operation triggers a Sink chain that fuses all operations into one pass per element. Short-circuiting terminals like `findFirst()` stop processing early. Parallel streams split the source via `Spliterator.trySplit()`, process halves on `ForkJoinPool.commonPool()` threads, and combine results via the Collector's combiner. Stateful operations like `sorted()` act as barriers — they must consume all upstream elements before emitting.
>
> **Part 3 — Gotcha (20s):** The two biggest traps: parallel stream with mutable shared state (`forEach` + `ArrayList.add` → race condition — use `collect()` instead), and parallel stream with blocking I/O (ties up the shared ForkJoinPool, starving the entire JVM — use CompletableFuture with a dedicated executor or Java 21 virtual threads instead).

---

## 🧾 TL;DR

- Streams are lazy — nothing runs until a terminal operation. Elements flow vertically (loop fusion).
- Intermediate: `filter`, `map`, `flatMap`, `peek` (lazy). Terminal: `collect`, `toList`, `forEach`, `reduce` (triggers execution).
- `sorted()` is stateful — must consume all elements before emitting. Breaks lazy pipeline.
- Spliterator: feeds elements + splits for parallelism. Characteristics enable optimizations.
- Parallel: uses `ForkJoinPool.commonPool()`. Good for CPU-bound + large data. Bad for I/O or small data.
- Never mutate shared state in `forEach()` — use `collect()` for accumulation.
- `toList()` (Java 16+) = unmodifiable. `Collectors.toList()` = mutable ArrayList.
- `reduce()` accumulator must be associative for parallel correctness.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #8 (Phase 2) of the JavaBackend KB completion roadmap. Covers: lazy evaluation + loop fusion with ASCII visual, pipeline construction (StatelessOp/StatefulOp chain, Sink chain), stateless vs stateful operations (sorted as barrier), Spliterator and stream characteristics, flatMap vs map, reduce with associativity rule, parallel stream splitting + ForkJoinPool, peek() as debug-only, single-use constraint, toList() vs Collectors.toList(). Two production footguns: shared mutable state in parallel forEach, I/O blocking starving ForkJoinPool. |
