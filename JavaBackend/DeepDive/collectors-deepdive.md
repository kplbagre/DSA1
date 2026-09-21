# ☕ Collectors Deep-Dive — Deep Dive

> After this note you can use `groupingBy` with downstream collectors, explain what `teeing()` does, write a custom `Collector`, and know when `toMap()` throws on duplicate keys.

---

## 🎯 The Problem This Solves

`stream.collect(Collectors.toList())` handles the simple case — elements into a list. But real production pipelines need: group orders by status, partition users into active/inactive, compute average salary per department, or build a map keyed by ID. Without the Collectors API, you'd break the stream pipeline, drop into imperative code, and lose composability. The Collectors API keeps the entire transformation declarative — from source to final aggregated result in one pipeline.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Collector** | An object that defines how to accumulate stream elements into a mutable result container. Has 4 components: supplier (create container), accumulator (add element), combiner (merge two containers for parallel), finisher (optional final transform). |
| **Downstream collector** | A collector passed as an argument to another collector. `groupingBy(key, downstream)` — the downstream collector specifies how to aggregate elements WITHIN each group (count them, sum a field, join strings, etc.). |
| **Partitioning** | Splitting elements into exactly two groups based on a Predicate: true group and false group. `partitioningBy()` returns `Map<Boolean, List<T>>`. |

---

## 🧠 Mental Model

Think of collectors as **sorted mailroom bins**. `toList()` is one big bin. `groupingBy()` creates labeled bins — one per group — and each element goes into the bin matching its label. The downstream collector is the **action performed inside each bin**: just pile them up (`toList()`), count them (`counting()`), or sum a field (`summingInt()`). `teeing()` sends every element to TWO bins simultaneously and merges the results.

> If you can say "Collector has supplier-accumulator-combiner-finisher; groupingBy creates bins by key with a downstream collector for per-bin aggregation; toMap throws on duplicate keys unless you provide a merge function" without notes, you have collectors.

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Goal: group orders by status, count per status
Map<String, Long> countByStatus = new HashMap<>();
for (Order order : orders) {
    countByStatus.merge(order.getStatus(), 1L, Long::sum);
}
// Works. But breaks the stream pipeline — back to imperative.
```

---

### Level 2 — The real mechanism

#### 2.1 — The essential collectors

```java
// toList() — collect into a List
List<String> names = users.stream().map(User::name).collect(Collectors.toList());
// ⚠️ Returns mutable ArrayList. Java 16+: .toList() returns unmodifiable.

// toSet() — collect into a Set (deduplicates)
Set<String> uniqueCities = users.stream().map(User::city).collect(Collectors.toSet());

// toMap() — collect into a Map
Map<Long, User> userById = users.stream()
    .collect(Collectors.toMap(User::id, Function.identity()));
// ⚠️ Throws IllegalStateException on DUPLICATE KEYS — see footgun below

// toMap with merge function — handles duplicates
Map<Long, User> userById2 = users.stream()
    .collect(Collectors.toMap(User::id, Function.identity(), (existing, incoming) -> incoming));
// Merge function: if same key appears twice, keep the incoming value (last wins)

// joining() — concatenate strings
String csv = users.stream().map(User::name).collect(Collectors.joining(", "));
// "Alice, Bob, Charlie"

String csvWithWrapper = users.stream().map(User::name)
    .collect(Collectors.joining(", ", "[", "]"));
// "[Alice, Bob, Charlie]"
```

#### 2.2 — `groupingBy()` — the powerhouse

```java
// Simple groupBy — values are List<T> by default
Map<String, List<Order>> ordersByStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::status));
// {"PENDING": [order1, order2], "SHIPPED": [order3], ...}

// groupBy with downstream: counting
Map<String, Long> countByStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::status, Collectors.counting()));
// {"PENDING": 2, "SHIPPED": 1, ...}

// groupBy with downstream: summing a field
Map<String, Double> totalByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::status,
        Collectors.summingDouble(Order::amount)
    ));

// groupBy with downstream: mapping + toSet (extract specific fields per group)
Map<String, Set<String>> customersByCity = orders.stream()
    .collect(Collectors.groupingBy(
        Order::city,
        Collectors.mapping(Order::customerName, Collectors.toSet())
    ));
// {"NYC": {"Alice", "Bob"}, "LA": {"Charlie"}, ...}

// Nested groupBy: group by status, then by city within each status
Map<String, Map<String, List<Order>>> nested = orders.stream()
    .collect(Collectors.groupingBy(
        Order::status,
        Collectors.groupingBy(Order::city)
    ));
```

#### 2.3 — `partitioningBy()` — binary split

```java
// Partition into exactly two groups: true and false
Map<Boolean, List<User>> partition = users.stream()
    .collect(Collectors.partitioningBy(u -> u.age() >= 18));
// {true: [adult1, adult2], false: [minor1]}

// With downstream:
Map<Boolean, Long> counts = users.stream()
    .collect(Collectors.partitioningBy(
        u -> u.age() >= 18,
        Collectors.counting()
    ));
// {true: 42, false: 7}
```

#### 2.4 — `teeing()` (Java 12) — two collectors in parallel

```java
// Process elements through TWO collectors simultaneously, merge results
// Example: compute both min and max in a single pass
record MinMax(int min, int max) {}

MinMax result = List.of(3, 1, 4, 1, 5, 9, 2, 6).stream()
    .collect(Collectors.teeing(
        Collectors.minBy(Comparator.naturalOrder()),   // collector 1
        Collectors.maxBy(Comparator.naturalOrder()),   // collector 2
        (min, max) -> new MinMax(min.orElse(0), max.orElse(0))   // merger
    ));
// MinMax[min=1, max=9]

// Real-world: count + average in one pass
record Stats(long count, double average) {}
Stats stats = salaries.stream()
    .collect(Collectors.teeing(
        Collectors.counting(),
        Collectors.averagingDouble(Double::doubleValue),
        Stats::new
    ));
```

#### 2.5 — Custom Collector (the 4 components)

```java
// A Collector has 4 functions:
// 1. supplier():    () → A           create the mutable result container
// 2. accumulator(): (A, T) → void    add one element to the container
// 3. combiner():    (A, A) → A       merge two containers (for parallel)
// 4. finisher():    A → R            transform the container into the final result

// Example: custom collector that builds a comma-separated string with count prefix
// "3 items: Alice, Bob, Charlie"
Collector<String, ?, String> countAndJoin = Collector.of(
    // supplier: create a StringJoiner
    () -> new StringJoiner(", "),
    // accumulator: add element
    StringJoiner::add,
    // combiner: merge two joiners (parallel support)
    StringJoiner::merge,
    // finisher: transform to final string
    sj -> sj.length() + " items: " + sj.toString()
);

String result = Stream.of("Alice", "Bob", "Charlie").collect(countAndJoin);
// "3 items: Alice, Bob, Charlie"
```

> **What the JVM is actually doing:** For sequential streams, the supplier creates one container, the accumulator is called once per element, and the finisher transforms the result. The combiner is never called. For parallel streams, the supplier creates one container per partition, each partition's accumulator fills its own container independently, then the combiner merges all containers pair-wise in a reduction tree, and finally the finisher is applied once to the merged result.

---

### Level 3 — The subtleties

#### 3.1 — `toMap()` duplicate key trap

```java
// ❌ Throws IllegalStateException on duplicate keys:
List<User> users = List.of(
    new User(1L, "Alice"),
    new User(1L, "Alice-duplicate")   // same ID
);
Map<Long, User> map = users.stream()
    .collect(Collectors.toMap(User::id, Function.identity()));
// IllegalStateException: Duplicate key 1

// ✅ Fix: provide merge function
Map<Long, User> map = users.stream()
    .collect(Collectors.toMap(
        User::id,
        Function.identity(),
        (existing, incoming) -> existing   // keep first on conflict
    ));

// ✅ Fix: use groupingBy if duplicates are expected
Map<Long, List<User>> grouped = users.stream()
    .collect(Collectors.groupingBy(User::id));
```

#### 3.2 — Preserving map order with `groupingBy`

```java
// Default groupingBy uses HashMap — unordered
Map<String, List<Order>> unordered = orders.stream()
    .collect(Collectors.groupingBy(Order::status));

// Use 3-arg groupingBy to specify map factory:
Map<String, List<Order>> ordered = orders.stream()
    .collect(Collectors.groupingBy(
        Order::status,
        LinkedHashMap::new,   // preserves insertion order
        Collectors.toList()
    ));
```

#### 3.3 — `collectingAndThen()` — transform after collecting

```java
// Wrap a collector's result with a final transformation
List<String> unmodifiable = stream.collect(
    Collectors.collectingAndThen(
        Collectors.toList(),
        Collections::unmodifiableList   // finisher: make the list unmodifiable
    )
);
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "`toMap()` handles duplicates like `Map.put()`" | `toMap()` THROWS `IllegalStateException` on duplicate keys. You must provide a merge function as the third argument to handle conflicts. This is the most common Collectors surprise. |
| "`groupingBy` returns a sorted map" | `groupingBy` returns a `HashMap` by default — unordered. Use the 3-arg version with `LinkedHashMap::new` for insertion order, or `TreeMap::new` for sorted keys. |
| "You need a loop for complex aggregations" | `groupingBy` with downstream collectors (`counting`, `summingDouble`, `mapping`, nested `groupingBy`) handles multi-level aggregation in a single declarative pipeline. |
| "Custom collectors are for edge cases" | Custom collectors are needed whenever the built-in collectors don't match your accumulation pattern. In practice, `teeing()` (Java 12) eliminated many custom collector needs by allowing two built-in collectors to run in parallel. |

---

## 🐞 Production Footguns

---

> **Footgun: `toMap()` duplicate key crash**
> **Cost:** Runtime crash (IllegalStateException)
>
> A product catalog API collected products into a `Map<String, Product>` keyed by SKU. During a data migration, two products accidentally had the same SKU. The stream crashed with `IllegalStateException: Duplicate key SKU-123` in production — no graceful handling, no fallback, 500 error to the client.

```java
// ❌ The trap
Map<String, Product> catalog = products.stream()
    .collect(Collectors.toMap(Product::sku, Function.identity()));
// Crashes on duplicate SKU

// ✅ The fix: merge function + logging
Map<String, Product> catalog = products.stream()
    .collect(Collectors.toMap(
        Product::sku,
        Function.identity(),
        (existing, duplicate) -> {
            log.warn("Duplicate SKU: {}, keeping first", duplicate.sku());
            return existing;
        }
    ));
```

---

> **Footgun: `groupingBy` with null keys**
> **Cost:** NullPointerException
>
> A reporting pipeline grouped transactions by `category`. Some transactions had `null` category (data quality issue). `groupingBy()` internally calls `Objects.requireNonNull` on the classifier result — NullPointerException, no graceful degradation.

```java
// ❌ The trap: null classifier value
Map<String, List<Transaction>> byCategory = transactions.stream()
    .collect(Collectors.groupingBy(Transaction::category));
// NullPointerException if any transaction has null category

// ✅ The fix: handle nulls before grouping
Map<String, List<Transaction>> byCategory = transactions.stream()
    .collect(Collectors.groupingBy(
        t -> t.category() != null ? t.category() : "UNCATEGORIZED"
    ));
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `stream-pipeline-internals.md` | `collect()` is the most common terminal operation. Collectors define HOW elements are accumulated. The Collector's combiner function is what enables parallel stream accumulation. |
| `functional-interfaces.md` | A Collector's 4 components map to functional interfaces: `Supplier`, `BiConsumer` (accumulator), `BinaryOperator` (combiner), `Function` (finisher). Understanding these interfaces explains the Collector's type signature. |
| `hashmap-internals.md` | `groupingBy` and `toMap` produce HashMaps. Understanding HashMap (bucket structure, hashCode requirement, null key rules) explains why `groupingBy` rejects null keys and why map order is unpredictable by default. |
| `generics-type-erasure.md` | `Collector<T, A, R>` has 3 type parameters: T (input element type), A (mutable accumulation type — usually internal), R (result type). Understanding generics explains the `?` in `Collector<String, ?, Map<...>>`. |

---

## 🎙️ Interview Deep Questions

**Q1. What are the 4 components of a Collector? How do they work together in parallel?**

> A Collector has: supplier (creates the mutable accumulation container — e.g., `new ArrayList<>()`), accumulator (adds one element to the container — e.g., `list.add(element)`), combiner (merges two containers — used only in parallel — e.g., `list1.addAll(list2)`), and finisher (optional final transform — e.g., `Collections.unmodifiableList(list)`). In sequential mode, one container is created, all elements are accumulated, and the finisher is applied. In parallel mode, each thread gets its own container (supplier called per partition), accumulates its subset independently, then containers are merged pair-wise via the combiner in a reduction tree, and the finisher is applied once to the final merged result.

**Q2. What happens if you use `toMap()` and two elements map to the same key?**

> `Collectors.toMap()` throws `IllegalStateException` with message "Duplicate key [value]". Unlike `Map.put()` which silently overwrites, `toMap()` treats duplicates as an error by default. To handle duplicates, pass a merge function as the third argument: `toMap(keyMapper, valueMapper, mergeFunction)`. The merge function receives both values and returns the one to keep. Common strategies: `(a, b) -> a` (keep first), `(a, b) -> b` (keep last), `(a, b) -> a + b` (combine). This is the single most common Collectors surprise in production — it passes all tests with clean data and crashes when production data has duplicates.

**Q3. How do you group by one field and compute aggregate statistics per group?**

> Use `groupingBy` with a downstream collector. `groupingBy(Order::status, counting())` gives count per status. `groupingBy(Order::status, summingDouble(Order::amount))` gives sum per status. You can nest: `groupingBy(Order::status, groupingBy(Order::city))` gives a map of maps. `groupingBy(Order::status, mapping(Order::customerName, toSet()))` extracts unique customer names per status. For multiple aggregations, use `teeing()` (Java 12) as the downstream — e.g., teeing(counting(), summingDouble(Order::amount), Stats::new) gives both count and sum in one pass per group.

**Q4. What is `teeing()` and when would you use it?**

> `teeing()` (Java 12) runs two collectors simultaneously on the same stream, then merges their results using a BiFunction. It processes each element through BOTH collectors in a single pass — no need to iterate twice. Use it when you need two different aggregations from the same stream: min and max, count and sum, average and standard deviation. Example: `teeing(minBy(comparator), maxBy(comparator), (min, max) -> new Range(min, max))`. Before `teeing()`, you'd either iterate twice (wasteful) or write a custom Collector (verbose).

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Collectors define how stream elements are accumulated into a final result. They have 4 components: supplier, accumulator, combiner, finisher. `groupingBy` with downstream collectors enables multi-level aggregation in one pipeline.
>
> **Part 2 — How/Why (30s):** `groupingBy(classifier)` creates a HashMap of lists by default. The downstream collector replaces the default `toList()` — use `counting()`, `summingDouble()`, `mapping()`, or nest another `groupingBy()`. `teeing()` (Java 12) runs two collectors in parallel and merges results — eliminates double iteration. `toMap()` creates key-value pairs but throws on duplicate keys — always provide a merge function in production. Custom collectors implement the 4-component interface directly.
>
> **Part 3 — Gotcha (20s):** Two traps: `toMap()` crashes on duplicate keys with `IllegalStateException` — always provide `(a, b) -> a` or similar merge function. And `groupingBy()` throws `NullPointerException` on null classifier values — pre-filter nulls or map them to a default string like `"UNCATEGORIZED"`.

---

## 🧾 TL;DR

- `toMap()`: THROWS on duplicate keys. Always provide merge function in production.
- `groupingBy(key)`: HashMap of Lists. Use downstream for aggregation (`counting()`, `summingDouble()`).
- `groupingBy(key, LinkedHashMap::new, downstream)`: preserves insertion order.
- `partitioningBy(predicate)`: exactly two groups — `Map<Boolean, List<T>>`.
- `teeing(collector1, collector2, merger)` (Java 12): two aggregations in one pass.
- `mapping(mapper, downstream)`: transform elements before aggregating within a group.
- `collectingAndThen(collector, finisher)`: post-process collector result.
- Custom Collector: supplier + accumulator + combiner + finisher. Combiner only used in parallel.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #9 (Phase 2) of the JavaBackend KB completion roadmap. Covers: essential collectors (toList, toSet, toMap, joining), groupingBy with downstream (counting, summingDouble, mapping, nested groupingBy), partitioningBy, teeing (Java 12), custom Collector 4-component model with parallel explanation, collectingAndThen, map order preservation with LinkedHashMap. Two production footguns: toMap duplicate key crash, groupingBy null key NPE. |
