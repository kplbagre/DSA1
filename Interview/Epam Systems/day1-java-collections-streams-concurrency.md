# Day 1 — Java Collections · Streams · Concurrency
### EPAM Interview Prep · 4 Hours · Sep 18, 2026

> **Who this is for:** A Java developer who *uses* HashMap and Streams daily but has never needed to explain *how they work internally*. All analogies are from everyday life. No prior knowledge of internals assumed.

> **What you will be able to do after this:** Explain HashMap from scratch (including the Java 8 change), explain ConcurrentHashMap correctly (Java 8+), write 3 Streams problems without an IDE, and explain volatile vs synchronized with zero hesitation.

---

## 🧾 Index — Jump to Any Section

| # | Topic | Time |
| --- | --- | --- |
| [1. HashMap Internals](#hashmap-internals) | How hashing works, collisions, load factor, Java 8 tree change | ~60 min |
| [2. ConcurrentHashMap vs synchronizedMap](#concurrenthashmap) | Thread-safe maps, how Java 8 CHM actually works | ~20 min |
| [3. Java 8 Streams — Theory](#streams-theory) | Pipeline mental model, intermediate vs terminal, map vs flatMap | ~40 min |
| [4. Functional Interfaces](#functional-interfaces) | Predicate, Function, Supplier, Consumer — signatures + usage | ~20 min |
| [5. Streams Coding Drills](#streams-coding) | 3 problems to code without IDE (these exact Qs are asked at EPAM) | ~40 min |
| [6. volatile vs synchronized vs Atomic](#concurrency) | CPU cache problem, visibility, atomicity — exact guarantees | ~30 min |
| [7. CompletableFuture API](#completablefuture) | supplyAsync vs runAsync, thenApply vs thenCompose | ~10 min |

---

<a id="hashmap-internals"></a>

## 🔹 1. HashMap Internals

### 📖 Terminology

- **Bucket** — one slot in HashMap's internal array. Like a numbered drawer in a filing cabinet.
- **Hash function** — a formula that converts a key to a number (the bucket index). Like a formula that says "key K goes in drawer #42."
- **Hash collision** — when two different keys produce the same bucket index. Both need to live in the same drawer.
- **Load factor** — the maximum ratio of entries to buckets before HashMap grows. Default: 0.75 (grow when 75% full).
- **Rehashing** — when HashMap grows, it creates a larger array and recalculates every key's bucket index. Expensive but infrequent.

---

### 🧠 Mental Model — The Filing Cabinet

Imagine you work in a large office. You have thousands of documents to file. You have a **filing cabinet with 16 drawers** (numbered 0–15).

When a new document arrives with a label (key), you run a formula on the label:
```
drawer_number = formula(label) % 16
```

You open that drawer and drop the document in. When you need to retrieve it, you run the same formula, open the same drawer, and search inside.

This is exactly how HashMap works:
- The **filing cabinet** = the internal array (`Node[] table`)
- Each **drawer** = one bucket
- The **formula** = `key.hashCode()`
- Searching inside the drawer = scanning the chain in that bucket

---

### 🎨 Visual — HashMap Internal Structure

```
  key.hashCode() % capacity = bucket index

  HashMap internal array (default capacity: 16):
  ┌────────────────────────────────────────────┐
  │ Index │ Contents                           │
  ├───────┼────────────────────────────────────┤
  │  [0]  │  null                              │
  │  [1]  │  ("apple", 10)                     │
  │  [2]  │  ("banana", 20) → ("mango", 30)    │  ← collision! two keys, same bucket
  │  [3]  │  null                              │
  │  [4]  │  ("cherry", 40)                    │
  │  ...  │  ...                               │
  │ [15]  │  null                              │
  └────────────────────────────────────────────┘

  Each cell is a "bucket" (Node in a linked list or tree).
  Most buckets have 0 or 1 entry. Collisions create chains.

KEY INVARIANT:
   HashMap never stores data in a sorted structure.
   It trades predictable order for O(1) average-case lookup.
```

---

### 🔬 Step by Step: What Happens on `map.put("apple", 10)`

**Steps in plain English:**

1. Call `"apple".hashCode()` — produces a large integer (e.g., 93029210)
2. Spread the bits (`hash = (h = key.hashCode()) ^ (h >>> 16)`) — reduces clustering
3. Compute bucket index: `index = hash & (capacity - 1)` — equivalent to `hash % 16` but faster
4. Go to that bucket. If empty → create a new node there. Done.
5. If not empty → scan the chain. If a node has `.equals(key)` → update value. If no match → append new node to chain.

```java
// Internal node structure (simplified)
class Node<K, V> {
    final int hash;
    final K key;
    V value;
    Node<K, V> next;   // points to next node in case of collision
}
```

---

### 🎨 Visual — What Happens on `get("banana")`

```
  1. hash("banana") → bucket index = 2
  2. Go to bucket[2]: ("banana", 20) → ("mango", 30)
  3. Check first node: "banana".equals("banana") ✅ → return 20
  
  What if "banana" wasn't first?
  1. hash("banana") → bucket index = 2
  2. Go to bucket[2]: ("mango", 30) → ("banana", 20)
  3. Check first node: "banana".equals("mango") ❌ → move to next
  4. Check second node: "banana".equals("banana") ✅ → return 20

KEY INVARIANT:
   equals() is ONLY called inside a bucket — after hashCode() narrows down the bucket.
   This is why hashCode() and equals() must be consistent: if a.equals(b) then a.hashCode() == b.hashCode().
```

---

### ⚠️ The load factor — why 0.75?

When HashMap fills up, chains get longer → lookups slow down (O(n) in the worst case). So when the entry count crosses `capacity × loadFactor`, HashMap **doubles its array** and rebuilds (rehashes).

**Why 0.75, not 0.5 or 0.9?**

```
Load factor 0.5:
  Pro: fewer collisions (more empty buckets), faster lookups
  Con: wastes memory, doubles the array too early

Load factor 0.9:
  Pro: memory-efficient
  Con: long collision chains, slow lookups

Load factor 0.75:
  Pro: the math shows this is the point where lookup speed and
       memory usage are balanced best (proven empirically by Java engineers)
  Con: none — this is the sweet spot
```

> The default initial capacity is **16** and the default load factor is **0.75**, so HashMap grows after **12 entries** (16 × 0.75 = 12).

---

### 🎨 Visual — The Java 8 Change (What They Always Ask)

**Before Java 8 — Linked List only:**

```
  bucket[2]:  ("a",1) → ("b",2) → ("c",3) → ... → ("z",26)
  
  Worst case: hashCode() returns same value for all keys
  → Everything lands in one bucket
  → get() has to scan a chain of length N
  → O(N) lookup! Defeats the purpose of a HashMap.
  
  This is not just theoretical — it's a real attack vector.
  An attacker can craft keys that all hash to the same bucket,
  making your HashMap perform like a slow list.
```

**Java 8 and beyond — Adaptive: List first, then Tree:**

```
  STEP 1: Entries go into a Linked List first (fast insertion)
  
  bucket[2]:  A → B → C → D → E → F → G → H   (8 nodes, still a list)
  
  STEP 2: Chain length > 8 AND table capacity ≥ 64 → convert to Red-Black Tree
  
  bucket[2]:       D
                 /   \
               B       F
              / \     / \
             A   C   E   G
                          \
                           H
  
  STEP 3: If entries are removed and chain falls below 6 → convert back to list
  (Tree has overhead that isn't worth it for small chains)
  
  Result:
    List lookup:  O(n) worst case
    Tree lookup:  O(log n) worst case  ← Java 8 improvement

KEY INVARIANT:
   Two conditions BOTH required for treeification:
   1. Chain length > 8
   2. Table capacity ≥ 64
   If capacity < 64, HashMap resizes instead of treeifying (resizing is cheaper).
```

---

### ⚠️ Why HashMap Is NOT Thread-Safe

Two threads doing `put()` at the same time:

```
Thread 1: computes bucket index 5, about to write node
Thread 2: computes bucket index 5, about to write node at the SAME position
→ One write silently overwrites the other
→ You lose data with no exception thrown

Thread 1 is iterating while Thread 2 does put():
→ ConcurrentModificationException (fail-fast iterator)
```

**Fix:** Use `ConcurrentHashMap` for concurrent writes, or `Collections.synchronizedMap()` if you only need basic safety.

---

<a id="concurrenthashmap"></a>

## 🔹 2. ConcurrentHashMap vs Collections.synchronizedMap

### 📖 Terminology

- **Lock striping** (Java 7 term) — dividing the map into independent regions, each with its own lock, so multiple threads can write to different regions simultaneously.
- **CAS (Compare-And-Swap)** — a lock-free operation: "if the current value equals what I expect, swap it with my new value atomically." No lock acquired — done in CPU hardware. Very fast.
- **Synchronized block** — Java keyword that forces only one thread at a time into a code block, using the object as a monitor (lock).

---

### 🧠 Mental Model — The Bank Teller Counter

**`Collections.synchronizedMap()` is like a bank with ONE shared key:**
- Every teller (thread) needs the key to serve a customer (access the map)
- While teller 1 is serving, tellers 2–100 wait outside
- Safe, but slow under load

**`ConcurrentHashMap` is like a bank with MANY independent tills:**
- Each till (bucket/bin) has its own key
- Tellers access different tills simultaneously — no waiting
- If two tellers happen to need the SAME till, only they coordinate

---

### 🎨 Visual — How Java 8 ConcurrentHashMap Actually Works

> ⚠️ **Interviewer footgun:** Many resources describe "16 Segments" — that was **Java 7**. Java 8 completely rewrote ConcurrentHashMap. Do NOT say "16 segments" in a 2026 interview.

```
  Java 7 ConcurrentHashMap (OLD — do not describe this):
  ┌──────────────┬──────────────┬──────────────┐
  │  Segment 0   │  Segment 1   │  Segment 2   │  ← 16 segments, each with a ReentrantLock
  │  [lock]      │  [lock]      │  [lock]      │
  │  bucket[0-n] │  bucket[n-m] │  bucket[m-k] │
  └──────────────┴──────────────┴──────────────┘
  Max concurrent writers: 16

  ──────────────────────────────────────────────────

  Java 8+ ConcurrentHashMap (CURRENT — describe this):
  
  Internal array of bins (same concept as HashMap):
  ┌──────────┬──────────┬──────────┬──────────┐
  │  bin[0]  │  bin[1]  │  bin[2]  │  bin[3]  │  ... up to capacity bins
  └──────────┴──────────┴──────────┴──────────┘
  
  Write to an EMPTY bin:
    → Uses CAS (Compare-And-Swap) — no lock at all
    → Thread competes with CPU-level atomic op
    → No suspension, no context switch
  
  Write to a NON-EMPTY bin (collision):
    → synchronized(bin_head_node) { ... }
    → Only the HEAD NODE of that bin is locked
    → Other bins are completely free
  
  Reads:
    → No lock at all (volatile reads on node values)
    → Multiple threads can read concurrently with zero contention
  
KEY INVARIANT:
   Java 8 CHM can have as many concurrent writers as there are bins.
   A 1024-bin map can theoretically have 1024 threads writing simultaneously
   (one per non-empty bin) — vs Java 7's hard limit of 16.
```

---

### 📊 Comparison Table — Know This Cold

| Feature | `HashMap` | `ConcurrentHashMap` | `Collections.synchronizedMap()` |
| --- | --- | --- | --- |
| Thread-safe | ❌ No | ✅ Yes | ✅ Yes |
| Lock strategy | None | Per-bin (CAS + synchronized on head) | Whole-map lock (`synchronized(this)`) |
| Null keys | ✅ Allowed | ❌ NPE thrown | ✅ Allowed |
| Null values | ✅ Allowed | ❌ NPE thrown | ✅ Allowed |
| Read locking | None | None (volatile reads) | Full lock |
| Iteration under write | `ConcurrentModificationException` | Safe (weakly consistent) | `ConcurrentModificationException` if not manually locked |
| Performance under concurrency | Broken | ✅ High | Low |
| When to use | Single thread only | High-concurrency reads + writes | Simple safety, low concurrency |

**Why CHM does NOT allow null:** Ambiguity. If `get(key)` returns `null`, does it mean the key doesn't exist OR the value is null? In a concurrent context you can't safely `containsKey()` and `get()` atomically to disambiguate — so CHM bans null entirely.

---

### ✅ Quick oral check before moving on

Say these answers out loud without looking:

1. "How does HashMap handle two keys landing in the same bucket?" — linked list (then tree if chain > 8 AND capacity ≥ 64)
2. "What changed in HashMap in Java 8?" — linked list chains → red-black tree above threshold
3. "Why can't ConcurrentHashMap store null?" — ambiguity between absent key and null value
4. "How does Java 8 ConcurrentHashMap achieve thread safety?" — CAS for empty bins, synchronized on bin head node for collisions, volatile reads for all reads

---

<a id="streams-theory"></a>

## 🔹 3. Java 8 Streams — Theory

### 📖 Terminology

- **Stream** — a sequence of elements that you process with a pipeline of operations. Does NOT store data — it reads from a source (list, array, etc.) and transforms it.
- **Pipeline** — a chain of stream operations. Like an assembly line in a factory.
- **Intermediate operation** — an operation that transforms a stream into another stream. Does nothing by itself (lazy).
- **Terminal operation** — the final operation that triggers the whole pipeline to run and produces a result.
- **Lazy evaluation** — stream intermediate operations don't execute until a terminal operation is called.

---

### 🧠 Mental Model — The Factory Assembly Line

```
  Raw materials    Assembly Line Stages       Final Product
  (your list)   ────────────────────────►   (your result)

  [employees]  → filter(active) → map(getName) → collect(toList)
                    ↑                ↑                ↑
               intermediate    intermediate        terminal
               (lazy — waits)  (lazy — waits)   (TRIGGERS all)

  Nothing on the assembly line moves until the terminal operation says "GO."
  This is why adding filter() and map() has zero cost until collect() runs.
```

**The key insight: A stream is NOT a data structure. It describes what to do — it doesn't do it yet.**

---

### 🎨 Visual — Intermediate vs Terminal Operations

```
  INTERMEDIATE (lazy — transforms stream to stream):
  ┌──────────────────────────────────────────────────────┐
  │  filter(predicate)   → keeps elements where test=true │
  │  map(function)       → transforms each element        │
  │  flatMap(function)   → transforms + flattens          │
  │  sorted(comparator)  → sorts (stateful — needs all)   │
  │  distinct()          → removes duplicates              │
  │  peek(consumer)      → inspect without changing        │
  │  limit(n)            → take first n elements           │
  │  skip(n)             → skip first n elements           │
  └──────────────────────────────────────────────────────┘

  TERMINAL (eager — triggers the pipeline, produces a result):
  ┌──────────────────────────────────────────────────────┐
  │  collect(collector)  → gather into List/Map/Set       │
  │  forEach(consumer)   → side-effect for each element   │
  │  reduce(identity, f) → fold all elements into one     │
  │  count()             → number of elements             │
  │  findFirst()         → Optional of first element      │
  │  anyMatch(pred)      → true if any element matches    │
  │  allMatch(pred)      → true if all elements match     │
  └──────────────────────────────────────────────────────┘

KEY INVARIANT:
   A stream can only be consumed ONCE.
   After a terminal operation runs, the stream is exhausted.
   Calling any operation on it again throws IllegalStateException.
```

---

### 🔬 map() vs flatMap() — The Most Asked Difference

**Steps in plain English:**

1. `map()` applies a function to each element and produces exactly one output per input.
2. `flatMap()` applies a function to each element, where the function returns a *stream*. Then all those streams are merged (flattened) into one.

Think of it like this:
- `map()` is like opening each envelope and keeping it as one envelope with new content inside.
- `flatMap()` is like opening each envelope, taking ALL the letters out, and mixing them into one big pile.

```java
// ── map() example: one-to-one transformation ──
List<String> words = List.of("hello", "world");
List<Integer> lengths = words.stream()
    .map(String::length)       // "hello" → 5, "world" → 5
    .collect(Collectors.toList());
// Result: [5, 5]

// ── flatMap() example: one-to-many, then flatten ──
List<List<String>> nested = List.of(
    List.of("cat", "dog"),
    List.of("bird", "fish")
);
List<String> flat = nested.stream()
    .flatMap(Collection::stream)    // each inner list → stream, all merged
    .collect(Collectors.toList());
// Result: [cat, dog, bird, fish]
// NOT [[cat, dog], [bird, fish]]  ← that would be map()
```

**The real-world use case for flatMap:** you have a list of orders, each with a list of items. You want a flat list of all items across all orders.

```java
List<String> allItems = orders.stream()
    .flatMap(order -> order.getItems().stream())
    .collect(Collectors.toList());
```

---

### 🎨 Visual — map() vs flatMap() Side by Side

```
  Input:  [["a","b"], ["c","d"]]    (List of Lists)

  With map(Collection::stream):
  Stream of Stream<String>: [ Stream["a","b"] , Stream["c","d"] ]
  Type: Stream<Stream<String>>   ← WRONG, still nested

  With flatMap(Collection::stream):
  Flattened Stream<String>:  [ "a" , "b" , "c" , "d" ]
  Type: Stream<String>        ← CORRECT, all merged into one stream

KEY INVARIANT:
   map()     → Stream<R>           (each element becomes one output)
   flatMap() → Stream<R> (merged)  (each element becomes a stream, all streams merged)
   Use flatMap() when your mapping function returns a Stream or Collection.
```

---

### `peek()` — What It's For

`peek()` is an intermediate operation that lets you look at each element as it passes through — without changing it. Its primary use is **debugging the pipeline**.

```java
List<String> result = names.stream()
    .filter(name -> name.startsWith("A"))
    .peek(name -> System.out.println("After filter: " + name))   // debug
    .map(String::toUpperCase)
    .peek(name -> System.out.println("After map: " + name))       // debug
    .collect(Collectors.toList());
```

> ⚠️ Do not use `peek()` for side effects in production — it may not fire if the stream is short-circuited (e.g., with `findFirst()`).

---

<a id="functional-interfaces"></a>

## 🔹 4. Functional Interfaces

### 📖 What is a Functional Interface?

A **functional interface** (SAM interface — Single Abstract Method interface) is an interface with exactly one abstract method. The `@FunctionalInterface` annotation makes the compiler enforce this. Lambda expressions implement functional interfaces.

Java 8 ships 4 core ones you must know:

---

### 🎨 Visual — The 4 Core Functional Interfaces

```
  ┌─────────────────────────────────────────────────────────────────┐
  │ Interface      │ Abstract method    │ In/Out      │ Used in     │
  ├─────────────────────────────────────────────────────────────────┤
  │ Predicate<T>   │ boolean test(T t)  │ T → boolean │ filter()    │
  │ Function<T,R>  │ R apply(T t)       │ T → R       │ map()       │
  │ Supplier<T>    │ T get()            │ () → T      │ lazy init   │
  │ Consumer<T>    │ void accept(T t)   │ T → nothing │ forEach()   │
  └─────────────────────────────────────────────────────────────────┘

  Memory hook:
    Predicate  = TESTS something   (gives back true/false)
    Function   = TRANSFORMS input  (gives back a different type)
    Supplier   = SUPPLIES a value  (takes nothing, gives something)
    Consumer   = CONSUMES a value  (takes something, gives nothing)
```

---

### 🔬 Each With an Example

```java
// ── Predicate<T>: test a condition, return boolean ──
Predicate<String> isLong = s -> s.length() > 5;
isLong.test("hello");      // false
isLong.test("elephant");   // true

// Used in filter():
names.stream().filter(isLong).collect(Collectors.toList());


// ── Function<T, R>: transform T into R ──
Function<String, Integer> toLength = String::length;
toLength.apply("hello");   // 5

// Used in map():
names.stream().map(toLength).collect(Collectors.toList());


// ── Supplier<T>: produce a value with no input ──
Supplier<List<String>> listFactory = ArrayList::new;
List<String> newList = listFactory.get();   // creates a new ArrayList

// Common use: lazy initialization — only compute when needed
Optional<String> result = Optional.empty();
String value = result.orElseGet(() -> expensiveComputation());


// ── Consumer<T>: consume a value, produce nothing ──
Consumer<String> printer = System.out::println;
printer.accept("hello");   // prints "hello"

// Used in forEach():
names.stream().forEach(printer);
```

---

### `Optional` — Avoid NullPointerException

**Optional** (introduced in Java 8) is a container that may or may not hold a value. It forces you to explicitly handle the "no value" case instead of returning null.

```java
// BAD — returns null if not found:
User user = findUser(id);
System.out.println(user.getName());   // NPE if user is null!

// GOOD — returns Optional:
Optional<User> user = findUser(id);
String name = user
    .map(User::getName)
    .orElse("Unknown");   // safe — no NPE possible
```

Common `Optional` methods:
```java
optional.isPresent()              // true if value exists
optional.get()                    // get value (throws if empty — use carefully)
optional.orElse("default")        // return value or a default
optional.orElseGet(() -> compute) // return value or compute a default lazily
optional.map(fn)                  // transform the value if present
optional.filter(pred)             // keep value only if predicate passes
```

---

<a id="streams-coding"></a>

## 🔹 5. Streams Coding Drills

> **Rule:** Code each problem on a notepad or whiteboard. No IDE. No autocomplete. That is what EPAM gives you. Aim for 15 minutes per problem.

---

### 🔬 Problem 1: Group Anagrams (most-reported EPAM coding question)

**Problem:** Given a list of strings, group words that are anagrams (same letters, different order) together.

```
Input:  ["eat", "tea", "tan", "ate", "nat", "bat"]
Output: {
          "aet" → ["eat", "tea", "ate"],
          "ant" → ["tan", "nat"],
          "abt" → ["bat"]
        }
```

**Steps in plain English:**

1. For each word, sort its characters to get a "canonical key" (all anagrams of the same group produce the same sorted key).
2. Group all words by their canonical key using `Collectors.groupingBy()`.

```java
public Map<String, List<String>> groupAnagrams(String[] words) {
    return Arrays.stream(words)
        .collect(
            Collectors.groupingBy(word -> {
                // Step 1: sort characters to get the canonical anagram key
                char[] chars = word.toCharArray();
                Arrays.sort(chars);
                return new String(chars);
            })
        );
}

// Test it mentally:
// "eat" → sort → "aet"
// "tea" → sort → "aet"  ← same key as "eat" → grouped together
// "tan" → sort → "ant"
// "nat" → sort → "ant"  ← same key as "tan"
// "bat" → sort → "abt"
```

---

### 🔬 Problem 2: Word Frequency Count (asked July 2024 EPAM)

**Problem:** Given a sentence, count how many times each word appears.

```
Input:  "the cat sat on the mat the cat"
Output: {the=3, cat=2, sat=1, on=1, mat=1}
```

**Steps in plain English:**

1. Split the sentence into an array of words.
2. Stream the words, group by the word itself (`Function.identity()`), and count how many times each appears.

```java
public Map<String, Long> wordFrequency(String sentence) {
    return Arrays.stream(sentence.split(" "))
        .collect(
            Collectors.groupingBy(
                Function.identity(),     // group key = the word itself
                Collectors.counting()   // count how many times it appears
            )
        );
}
```

**What `Function.identity()` means:** a function that returns its input unchanged — `x → x`. It is equivalent to writing `word -> word` but cleaner.

---

### 🔬 Problem 3: Filter + Sort Employees (asked Feb 2025 EPAM)

**Problem:** From a list of employees, get those from city "Noida", sorted by name in descending (Z→A) order.

```java
// Assume Employee has: String name, String city, int id

public List<Employee> getNoida(List<Employee> employees) {
    return employees.stream()
        .filter(e -> "Noida".equals(e.getCity()))   // only Noida employees
        .sorted(Comparator.comparing(Employee::getName).reversed())   // Z→A
        .collect(Collectors.toList());
}
```

**Bonus — filter employees with ID divisible by 2, sorted by ID ascending:**

```java
public List<Employee> evenIds(List<Employee> employees) {
    return employees.stream()
        .filter(e -> e.getId() % 2 == 0)
        .sorted(Comparator.comparingInt(Employee::getId))
        .collect(Collectors.toList());
}
```

---

### 🔬 Bonus Problem: First Repeating Character (asked EPAM Gurgaon, streams)

**Problem:** Find the first character that appears more than once in a string.

```
Input:  "asdfaghjklkjhgfdsa"
Output: 'a'  (first character that repeats)
```

**Steps in plain English:**

1. Stream over each character.
2. Track which characters you've seen using a `Set`.
3. `filter()` keeps only characters already in the set (meaning they're repeating). Add to set as a side-effect before filtering.
4. `findFirst()` gets the first match.

```java
public Optional<Character> firstRepeating(String s) {
    Set<Character> seen = new HashSet<>();
    return s.chars()
        .mapToObj(c -> (char) c)
        .filter(c -> !seen.add(c))   // Set.add() returns false if already present
        .findFirst();
}

// How Set.add() works here:
// seen.add('a') → 'a' not in set → adds it → returns true → !true = false → filtered OUT
// seen.add('s') → 's' not in set → adds it → returns true → !true = false → filtered OUT
// seen.add('a') (second time) → 'a' already in set → returns false → !false = true → KEPT
// findFirst() → 'a' ← first repeating character
```

---

<a id="concurrency"></a>

## 🔹 6. volatile vs synchronized vs Atomic

### 📖 Terminology

- **Main memory (RAM)** — the single authoritative copy of a variable's value. All threads share it.
- **CPU cache** — a fast, private copy of a variable that a CPU core holds for speed. Different CPU cores may have different cached values (stale values).
- **Visibility problem** — one thread updates a variable in its CPU cache, but another thread reads a stale value from its own cache, never seeing the update.
- **Atomicity** — an operation is "atomic" if it completes in a single indivisible step. No thread can interrupt it midway.
- **Race condition** — a bug that occurs when the result depends on which thread runs first — and the order is unpredictable.

---

### 🧠 Mental Model — The Shared Whiteboard

Imagine three programmers (threads) sharing one whiteboard (main memory). Each has a personal notepad (CPU cache).

**The visibility problem:**
- Programmer A writes "x = 5" on their notepad.
- Programmer B reads x from their notepad — it still says "x = 0".
- Programmer B never sees A's update because A never transferred from notepad to whiteboard.

**`volatile` = "always read/write from the shared whiteboard directly."**

**The atomicity problem (even with volatile):**
- `i++` is NOT one step. It is three: read i, add 1, write i back.
- Two threads can both read i=5, both add 1, both write 6. Result: 6 instead of 7.
- `volatile` does not help here — the whiteboard is shared but the read-add-write is still interleaved.

---

### 🎨 Visual — What volatile Fixes and What It Doesn't

```
  WITHOUT volatile:
  
  CPU Core 1 (Thread 1)     CPU Core 2 (Thread 2)
  ┌───────────────────┐     ┌───────────────────┐
  │ cache: x = 5     │     │ cache: x = 0     │  ← stale!
  └───────┬───────────┘     └─────────┬─────────┘
          │                           │
          └──────────► RAM ◄──────────┘
                      x = 5
  
  Thread 2 reads x = 0 from its cache → BUG (visibility problem)
  
  ────────────────────────────────────────────────────────────────
  
  WITH volatile:
  
  CPU Core 1 (Thread 1)     CPU Core 2 (Thread 2)
  ┌───────────────────┐     ┌───────────────────┐
  │ (no cache used)  │     │ (no cache used)  │
  └───────┬───────────┘     └─────────┬─────────┘
          │ always writes to RAM       │ always reads from RAM
          └──────────► RAM ◄──────────┘
                      x = 5
  
  Thread 2 reads x = 5 from RAM → CORRECT (visibility fixed)
  
  ────────────────────────────────────────────────────────────────
  
  BUT: volatile does NOT fix i++ race condition
  
  Thread 1: read x=5 from RAM (step 1)
  Thread 2: read x=5 from RAM (step 1)   ← interleaved!
  Thread 1: compute 5+1 = 6
  Thread 2: compute 5+1 = 6
  Thread 1: write 6 to RAM
  Thread 2: write 6 to RAM
  Final x = 6, expected x = 7           ← BUG (atomicity problem)

KEY INVARIANT:
   volatile → fixes VISIBILITY (reads/writes go to RAM, not cache)
   volatile → does NOT fix ATOMICITY (i++ is still 3 separate steps)
```

---

### 🎨 Visual — What `synchronized` Fixes

```
  synchronized block = only one thread at a time:
  
  Thread 1 enters synchronized block → acquires lock on object
  Thread 2 tries to enter → BLOCKED (waiting for Thread 1 to exit)
  Thread 1 finishes, releases lock
  Thread 2 acquires lock → enters
  
  synchronized int increment() {   // Thread 1 completes FULLY before Thread 2 starts
      int temp = i;                 // all 3 steps are atomic as a unit
      temp = temp + 1;
      i = temp;
      return i;
  }

  synchronized also flushes changes to RAM when exiting the block
  → it fixes BOTH visibility AND atomicity.
  
  Cost: only one thread at a time → can be a bottleneck under high concurrency.

KEY INVARIANT:
   synchronized = visibility + atomicity (one thread at a time in the block)
   volatile     = visibility only (multiple threads can still interleave)
```

---

### 🔬 volatile vs synchronized vs AtomicInteger — When to Use Each

```java
// volatile: correct for a simple flag that one thread writes, others read
volatile boolean stopRequested = false;

// synchronized: correct for any compound operation (read-modify-write)
synchronized void increment() {
    count++;   // safe — one thread at a time
}

// AtomicInteger: correct for numeric operations (increment, compare-and-set)
// faster than synchronized for single-variable operations
AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();   // atomic, no lock, uses CAS under the hood
```

**Decision rule:**
- Single variable, only one thread writes? → `volatile`
- Compound operations (read + write together), or multiple variables? → `synchronized`
- Single integer counter with high concurrency? → `AtomicInteger` (fastest)

---

### 🔬 Java Memory Model (JMM) — happens-before

The **JMM** (Java Memory Model — the specification that defines how threads see each other's writes) uses the concept of **happens-before** to guarantee visibility between threads.

**happens-before in plain English:** "If action A happens-before action B, then everything A wrote to memory is guaranteed to be visible to B."

**Key happens-before rules (the ones worth knowing):**

```
1. A write to a volatile variable happens-before all subsequent reads of it.
2. An unlock on a monitor (synchronized exit) happens-before all subsequent locks.
3. Thread.start() happens-before any action in the started thread.
4. Thread.join() — all actions in a thread happen-before Thread.join() returns.
```

**Why it matters:** Without happens-before, the JVM is free to reorder instructions for performance. happens-before is the contract that prevents dangerous reordering.

---

<a id="completablefuture"></a>

## 🔹 7. CompletableFuture API

> This section is short — you already know CF from your MCSE work. This is just the interview-vocabulary layer.

### 📖 Terminology

- **CompletableFuture\<T\>** — a future computation that can complete with a value of type T. Can be chained with other futures.
- **supplyAsync** — runs a `Supplier<T>` asynchronously (has a result).
- **runAsync** — runs a `Runnable` asynchronously (no result — fire and forget).

---

### 🔬 supplyAsync vs runAsync (asked verbatim at EPAM)

```java
// supplyAsync: the task PRODUCES a value
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> fetchDataFromDB());   // returns CompletableFuture<String>

String result = future.get();   // blocks until complete


// runAsync: the task produces NOTHING
CompletableFuture<Void> voidFuture = CompletableFuture
    .runAsync(() -> sendNotificationEmail());   // returns CompletableFuture<Void>
```

**One-line answer:** `supplyAsync` = async task with a return value. `runAsync` = async task with no return value.

---

### 🔬 thenApply vs thenCompose (asked as a follow-up)

```java
// thenApply: transform the result synchronously (like Stream.map)
CompletableFuture<String> upper = CompletableFuture
    .supplyAsync(() -> "hello")
    .thenApply(s -> s.toUpperCase());   // "HELLO"
// Type: CompletableFuture<String> → thenApply(String→String) → CompletableFuture<String>


// thenCompose: chain ANOTHER CompletableFuture (like Stream.flatMap — avoids nesting)
CompletableFuture<User> user = CompletableFuture
    .supplyAsync(() -> "user-id-123")
    .thenCompose(id -> fetchUserAsync(id));   // fetchUserAsync returns CompletableFuture<User>
// WITHOUT thenCompose: you'd get CompletableFuture<CompletableFuture<User>> ← wrong
// thenCompose flattens it to CompletableFuture<User>               ← correct


// exceptionally: handle failure gracefully (fallback)
CompletableFuture<String> safe = CompletableFuture
    .supplyAsync(() -> riskyOperation())
    .exceptionally(ex -> "fallback-value");
```

**Mental model:** thenApply/thenCompose are the CF equivalent of Stream.map/flatMap.

---

## 🧾 TL;DR — The 10 Things to Say Cold Tomorrow

1. **HashMap hashing:** `hashCode()` → spread bits → `hash & (capacity-1)` → bucket index → scan chain with `equals()`
2. **Java 8 HashMap change:** chain > 8 entries AND capacity ≥ 64 → linked list → red-black tree
3. **Load factor 0.75:** grow when 75% full — balanced trade-off between speed and memory
4. **Java 8 ConcurrentHashMap:** CAS for empty bins, `synchronized(bin_head)` for collisions, volatile reads — NOT "16 segments" (that's Java 7)
5. **CHM null:** throws NPE — null is banned to avoid ambiguity between "missing key" and "null value"
6. **Streams lazy:** nothing runs until a terminal operation is called; stream is consumed exactly once
7. **map vs flatMap:** map is 1→1, flatMap is 1→many and flattens
8. **volatile:** visibility only (reads/writes to RAM). Does NOT guarantee atomicity. `i++` is still a race.
9. **synchronized:** visibility + atomicity (one thread at a time). Slower but safer than volatile for compound ops.
10. **supplyAsync vs runAsync:** supplyAsync has a result (`CompletableFuture<T>`), runAsync is fire-and-forget (`CompletableFuture<Void>`)

---

## 🎯 Interview Q&A — Day 1 Topics

### ⭐ Section A — Asked in EPAM (Reported 2024–2025)

---

**Q1. How does HashMap work internally? Walk me through what happens when you call `put("key", value)`.**

> HashMap computes `hashCode()` on the key, then applies a bit-spreading function to distribute the hash more evenly — this reduces clustering in buckets. It then takes `hash & (capacity - 1)` to find the bucket index. If the bucket is empty, the entry goes in directly. If there's already an entry (collision), Java walks the chain comparing keys using `equals()`. If an equal key is found, the value is updated; otherwise a new node is appended.
>
> In Java 8, once a single bucket chain exceeds 8 nodes AND the total table capacity is at least 64, the linked list is converted to a red-black tree — worst-case lookup drops from O(n) to O(log n). When entries are removed and the tree shrinks below 6 nodes, it converts back to a linked list.
>
> Separately, when the total number of entries exceeds `capacity × loadFactor` (default 0.75), the map doubles in size and rehashes all entries.

---

**Q2. What is the difference between ConcurrentHashMap and `Collections.synchronizedMap()`?**

> `Collections.synchronizedMap()` wraps a regular map with a single lock on the whole map. Every `get()`, `put()`, `remove()` acquires the same lock — only one thread can do anything at a time, which kills performance under concurrent load.
>
> Java 8 ConcurrentHashMap is fundamentally different. For empty buckets, it uses a CAS (compare-and-swap — a hardware atomic instruction that updates a value only if it matches the expected value) operation with no lock at all. For non-empty buckets, it locks only the head node of that specific bucket — so hundreds of threads can write to different buckets simultaneously. Reads use volatile semantics, so they never block.
>
> The practical difference: synchronizedMap is usable only for low-concurrency situations. ConcurrentHashMap is what you use in production for shared caches, counters, and any concurrent access pattern. One more difference: ConcurrentHashMap bans null keys and null values — HashMap allows one null key.

---

**Q3. What is the difference between `map()` and `flatMap()` in Java Streams?**

> `map()` is a one-to-one transform — for each element in the stream, it produces exactly one output element. The result is `Stream<R>`.
>
> `flatMap()` is a one-to-many transform — for each element, it produces a stream of results, and all those streams are flattened into one output stream. Think of it as `map()` followed by a flatten step.
>
> The classic example: given a list of sentences, `map(sentence -> sentence.split(" "))` gives you `Stream<String[]>` — a stream of arrays. `flatMap(sentence -> Arrays.stream(sentence.split(" ")))` gives you `Stream<String>` — all words in a single flat stream.
>
> In CompletableFuture terms: `thenApply` is like `map` (synchronous transform), `thenCompose` is like `flatMap` (chains another async stage without nesting).

---

**Q4. What does `volatile` guarantee? Can it replace `synchronized`?**

> `volatile` guarantees visibility — any write to a volatile variable goes straight to main memory, and any read goes straight from main memory, bypassing the CPU cache. It also prevents instruction reordering around that variable.
>
> What it does NOT guarantee is atomicity. The classic example: `i++` on a volatile `int` is still a race condition. `i++` is actually three operations — read `i`, increment, write back — and two threads can interleave these steps.
>
> `synchronized` gives both visibility and atomicity — only one thread executes the synchronized block at a time, and memory is flushed on exit.
>
> So no, `volatile` cannot replace `synchronized` for compound operations. Use `volatile` only when you have a single write and multiple reads, and the write is atomic by itself (e.g., writing a reference, writing a boolean flag). Use `AtomicInteger`/`AtomicLong` for atomic counter operations without the full cost of `synchronized`.

---

**Q5. (Coding) Find the first non-repeating character in a string using Java Streams.**

> The key insight is using a `Set` whose `add()` returns `false` when the element was already present — that's what `!seen.add(c)` exploits.

```java
public Optional<Character> firstRepeating(String s) {
    Set<Character> seen = new HashSet<>();
    return s.chars()
        .mapToObj(c -> (char) c)
        .filter(c -> !seen.add(c))
        .findFirst();
}
```

> Walk-through: `s.chars()` gives an `IntStream` of char values. `mapToObj` boxes each to `Character`. `filter(c -> !seen.add(c))` passes only characters already in the set (i.e., seen before). `findFirst()` returns the first such character as an `Optional<Character>`.

---

**Q6. (Coding) Write a stream to group words by their sorted characters (anagram grouping).**

```java
public Map<String, List<String>> groupAnagrams(String[] words) {
    return Arrays.stream(words)
        .collect(
            Collectors.groupingBy(word -> {
                char[] chars = word.toCharArray();
                Arrays.sort(chars);
                return new String(chars);
            })
        );
}
```

> The key classifier function sorts the characters of each word alphabetically — anagrams like "eat", "tea", "ate" all produce the same sorted key "aet". `groupingBy` collects words with the same key into a list.

---

### 🌐 Section B — Commonly Asked (Java Collections + Streams + Concurrency)

---

**Q7. What is the difference between HashMap and Hashtable?**

> Hashtable is the legacy (pre-Java-1.2) synchronized map. Every method acquires a lock on the whole table — it's thread-safe but very slow under concurrent load, and it predates the Collections framework.
>
> HashMap is not synchronized, significantly faster for single-threaded use, and is part of the Collections framework (implements Map). It allows one null key and multiple null values; Hashtable allows neither.
>
> In practice, use HashMap for single-threaded code and ConcurrentHashMap for multi-threaded code. Hashtable is effectively deprecated — you should never use it in new code.

---

**Q8. Why is the default load factor 0.75 in HashMap?**

> 0.75 is a deliberate trade-off between time and space. A lower load factor (say 0.5) means fewer collisions — buckets stay sparse — but you waste a lot of memory and resize frequently. A higher load factor (say 0.9) saves memory but causes more collisions, making `get()` and `put()` slower as chains grow.
>
> 0.75 was chosen empirically as the sweet spot where the average number of entries per bucket is low enough that linked list traversal is almost never needed (chains of length 1 or 2), while still using the allocated capacity reasonably. In a map with capacity 16, growth triggers at 12 entries (16 × 0.75 = 12).

---

**Q9. What is a fail-fast vs fail-safe iterator?**

> A fail-fast iterator (used by ArrayList, HashMap, HashSet iterators) throws `ConcurrentModificationException` if the collection is structurally modified while iterating — it checks an internal `modCount`. The purpose is to catch programming errors (iterating and mutating at the same time) rather than operating on inconsistent state.
>
> A fail-safe iterator (used by CopyOnWriteArrayList, ConcurrentHashMap's iterators) does not throw. It iterates over a snapshot or a weakly-consistent view of the collection. The trade-off is that changes made after the iterator was created may or may not be visible during iteration.
>
> In production: if you need to remove elements while iterating, use `iterator.remove()` (safe for fail-fast iterators) or switch to a CopyOnWriteArrayList if concurrent modification is expected.

---

**Q10. What is the difference between Callable and Runnable?**

> `Runnable` has one method — `void run()`. It can't return a value and can't throw checked exceptions. It's used for fire-and-forget tasks.
>
> `Callable<V>` has one method — `V call() throws Exception`. It returns a value and can throw checked exceptions. It's used with `ExecutorService.submit()` which returns a `Future<V>` you can block on to get the result.
>
> Practical rule: if your async task produces a result or can fail with a checked exception, use `Callable`. For side-effect-only async work, use `Runnable` (or `Runnable` wrapped as a `CompletableFuture` via `runAsync`).

---

**Q11. Explain the JMM happens-before relationship in simple terms.**

> The Java Memory Model (JMM — the specification that defines how threads interact through memory, what writes one thread makes are guaranteed visible to other threads, and in what order) uses happens-before as its core guarantee.
>
> If action A happens-before action B, then all memory writes done by A are guaranteed to be visible to B when B reads. Without a happens-before relationship, the JVM and CPU are free to reorder operations and cache values in CPU registers — so one thread's writes may not be visible to another.
>
> Key happens-before rules:
> - A `volatile` write happens-before every subsequent read of that same variable.
> - `Thread.start()` happens-before any action in the started thread.
> - `Thread.join()` happens-before the return from `join()` in the calling thread.
> - Releasing a lock happens-before acquiring the same lock by another thread.
>
> This is why `volatile` fixes visibility — the write establishes happens-before with the read. But it doesn't fix atomicity, because two `volatile` reads-and-writes can still interleave.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Sep 2026 | Day 1 notes created. ConcurrentHashMap correctly described as Java 8 (per-bin CAS + synchronized on head, not 16 segments). HashMap treeify threshold: chain > 8 AND capacity ≥ 64. |
