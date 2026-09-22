# ⚡ Stream Operations — Quick Reference

> **Use:** look up stream operations by category, return type, and laziness. See `DeepDive/stream-pipeline-internals.md` for internals and `DeepDive/collectors-deepdive.md` for Collectors.

---

## 🔹 Intermediate Operations (lazy — return Stream)

| Operation | Signature | What it does | Stateful? |
|---|---|---|---|
| `filter` | `Stream<T> filter(Predicate<T>)` | Keep elements matching predicate | No |
| `map` | `Stream<R> map(Function<T,R>)` | Transform each element (1:1) | No |
| `flatMap` | `Stream<R> flatMap(Function<T,Stream<R>>)` | Transform + flatten (1:many) | No |
| `mapToInt` | `IntStream mapToInt(ToIntFunction<T>)` | Map to primitive int (avoids boxing) | No |
| `mapToLong` | `LongStream mapToLong(ToLongFunction<T>)` | Map to primitive long | No |
| `mapToDouble` | `DoubleStream mapToDouble(ToDoubleFunction<T>)` | Map to primitive double | No |
| `peek` | `Stream<T> peek(Consumer<T>)` | Side-effect per element (debug only) | No |
| `distinct` | `Stream<T> distinct()` | Remove duplicates (uses equals/hashCode) | Yes (HashSet internally) |
| `sorted` | `Stream<T> sorted()` | Sort by natural order | Yes (buffers ALL elements) |
| `sorted` | `Stream<T> sorted(Comparator<T>)` | Sort by comparator | Yes (buffers ALL elements) |
| `limit` | `Stream<T> limit(long n)` | Take first N elements (short-circuit) | Yes (counter) |
| `skip` | `Stream<T> skip(long n)` | Skip first N elements | Yes (counter) |
| `takeWhile` | `Stream<T> takeWhile(Predicate<T>)` | Take while predicate true (Java 9) | No |
| `dropWhile` | `Stream<T> dropWhile(Predicate<T>)` | Skip while predicate true (Java 9) | No |

---

## 🔹 Terminal Operations (trigger execution)

| Operation | Returns | Short-circuit? | What it does |
|---|---|---|---|
| `collect` | `R` | No | Accumulate into container (Collector) |
| `toList` | `List<T>` | No | Collect to unmodifiable list (Java 16+) |
| `toArray` | `Object[]` or `T[]` | No | Collect to array |
| `forEach` | `void` | No | Execute action per element (no order guarantee in parallel) |
| `forEachOrdered` | `void` | No | Execute action per element (ordered, even in parallel) |
| `reduce` | `Optional<T>` or `T` | No | Fold all elements into one value |
| `count` | `long` | No* | Count elements (*O(1) if SIZED + no filter) |
| `min` | `Optional<T>` | No | Minimum by comparator |
| `max` | `Optional<T>` | No | Maximum by comparator |
| `sum` | `int/long/double` | No | Sum (IntStream/LongStream/DoubleStream only) |
| `average` | `OptionalDouble` | No | Average (primitive streams only) |
| `findFirst` | `Optional<T>` | ✅ Yes | First element matching pipeline |
| `findAny` | `Optional<T>` | ✅ Yes | Any element (non-deterministic in parallel) |
| `anyMatch` | `boolean` | ✅ Yes | True if ANY element matches predicate |
| `allMatch` | `boolean` | ✅ Yes | True if ALL elements match predicate |
| `noneMatch` | `boolean` | ✅ Yes | True if NO elements match predicate |

---

## 🔹 Collectors (used with `collect()`)

| Collector | Returns | Use for |
|---|---|---|
| `toList()` | `List<T>` | Mutable ArrayList |
| `toUnmodifiableList()` | `List<T>` | Immutable list (Java 10+) |
| `toSet()` | `Set<T>` | HashSet (dedup) |
| `toMap(keyMapper, valueMapper)` | `Map<K,V>` | ⚠️ Throws on duplicate keys! |
| `toMap(key, val, mergeFunction)` | `Map<K,V>` | Handles duplicate keys |
| `joining(delimiter)` | `String` | Concatenate strings |
| `joining(delim, prefix, suffix)` | `String` | Concatenate with wrapping |
| `groupingBy(classifier)` | `Map<K, List<T>>` | Group into lists by key |
| `groupingBy(classifier, downstream)` | `Map<K, D>` | Group + aggregate per group |
| `partitioningBy(predicate)` | `Map<Boolean, List<T>>` | Split into true/false |
| `counting()` | `Long` | Count (downstream) |
| `summingInt/Long/Double(mapper)` | `int/long/double` | Sum a field (downstream) |
| `averagingInt/Long/Double(mapper)` | `Double` | Average a field (downstream) |
| `maxBy(comparator)` | `Optional<T>` | Max by comparator (downstream) |
| `minBy(comparator)` | `Optional<T>` | Min by comparator (downstream) |
| `mapping(mapper, downstream)` | `D` | Transform before aggregating (downstream) |
| `collectingAndThen(collector, finisher)` | `R` | Post-process collector result |
| `teeing(coll1, coll2, merger)` | `R` | Two collectors in one pass (Java 12+) |

---

## 🔹 Stream Creation

| Method | Creates | Notes |
|---|---|---|
| `collection.stream()` | Sequential stream | Most common |
| `collection.parallelStream()` | Parallel stream | Uses ForkJoinPool.commonPool() |
| `Arrays.stream(array)` | Stream from array | Supports int[], long[], double[] |
| `Stream.of(a, b, c)` | Stream from values | Varargs |
| `Stream.empty()` | Empty stream | |
| `Stream.generate(supplier)` | Infinite stream | `generate(Math::random)` |
| `Stream.iterate(seed, f)` | Infinite stream | `iterate(0, n -> n + 2)` — 0, 2, 4, ... |
| `Stream.iterate(seed, pred, f)` | Finite stream (Java 9) | `iterate(0, n -> n < 10, n -> n + 1)` |
| `IntStream.range(0, 10)` | 0 to 9 | Exclusive upper bound |
| `IntStream.rangeClosed(1, 10)` | 1 to 10 | Inclusive upper bound |
| `Stream.concat(s1, s2)` | Merged stream | |
| `Stream.builder()` | Builder pattern | Add elements, then `.build()` |

---

## 🔹 Common Patterns

```java
// Filter + Map + Collect:
List<String> names = users.stream()
    .filter(u -> u.isActive())
    .map(User::name)
    .toList();

// Group + Count:
Map<String, Long> countByCity = users.stream()
    .collect(groupingBy(User::city, counting()));

// Flatten nested lists:
List<Item> allItems = orders.stream()
    .flatMap(o -> o.getItems().stream())
    .toList();

// Find first matching:
Optional<User> admin = users.stream()
    .filter(u -> u.hasRole("ADMIN"))
    .findFirst();

// Sum a field:
double totalRevenue = orders.stream()
    .mapToDouble(Order::amount)
    .sum();

// Deduplicate by field:
Collection<User> unique = users.stream()
    .collect(toMap(User::email, Function.identity(), (a, b) -> a))
    .values();
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #33 (Phase 5). Intermediate/terminal ops tables, Collectors table, creation methods, common patterns. |
