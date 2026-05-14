# Lambdas for DSA — Beginner Reference

> A from-scratch explanation of Java lambdas. Read top-to-bottom the first time. Use the **Quick Cheat Sheet** at the end for daily lookup.

---

## 📌 Notion Paste Tip (Read This First)

If pasting into Notion strips your code formatting:

1. In Notion, type `/code` and press Enter to create a **code block** first
2. Choose `Java` as the language
3. **Paste inside that code block** (not at the document level)

Notion preserves all whitespace and newlines inside code blocks. At the document level, Notion may strip spaces and merge lines — that's the source of your pain.

---

## 🤔 Why Lambdas Exist — The Long Story (with Code)

Before Java 8, if you wanted to pass **behavior** (a chunk of logic) into a method, you had to wrap it in a class. Sorting a list with custom logic looked like this:

### Level 1 — The original Java way (anonymous class)

```java
import java.util.Comparator;

Integer[] arr = { 3, 1, 4, 1, 5, 9, 2, 6 };

Arrays.sort(arr, new Comparator<Integer>() {
    @Override
    public int compare(Integer a, Integer b) {
        return a - b;
    }
});
```

That's a lot of typing for "compare two integers". The **only line that matters** is `return a - b;`. Everything else (the `new Comparator<Integer>()`, the `@Override`, the method signature) is **boilerplate** — Java's way of saying "here's a class with one method."

### Level 2 — Java 8 introduces lambdas

Java realized: *if there's only one method to implement, why force the user to write the whole class?* So they let you write **just the body**:

```java
Arrays.sort(arr, (Integer a, Integer b) -> {
    return a - b;
});
```

Same behavior, no class wrapper. The compiler now figures out:
- Which interface you mean (`Comparator<Integer>`)
- Which method you're implementing (`compare`)
- That you want to override it

### Level 3 — Drop the types (compiler infers them)

The compiler already knows `arr` is `Integer[]`, so the comparator must take two `Integer`s. You don't need to repeat that:

```java
Arrays.sort(arr, (a, b) -> {
    return a - b;
});
```

### Level 4 — Drop braces and `return` for single-expression bodies

When the body is just one expression, you can skip the braces and `return` — the lambda **auto-returns** the expression's value:

```java
Arrays.sort(arr, (a, b) -> a - b);
```

### Level 5 — Method reference (when the lambda just calls an existing method)

If your lambda body is literally `Integer.compare(a, b)`, you can write the **method reference** with `::`:

```java
Arrays.sort(arr, Integer::compare);
```

---

**That progression — Level 1 to Level 5 — is what "lambdas" really are: shorthand for an anonymous class with one method.** No magic. The bytecode generated is similar; you're just typing less.

---

## 🧠 When You Need Lambdas in DSA

Whenever a Java API asks for **"behavior"** — sorting rules, filter conditions, value transformations, default value providers.

| You need to... | Lambda role |
| --- | --- |
| Sort with custom logic | `Comparator` |
| Build a max-heap or custom-priority heap | `Comparator` |
| Auto-create a list/set when key is missing | `Function` |
| Combine values when key already exists | `BiFunction` |
| Filter / remove items by condition | `Predicate` |
| Run a side-effect on each element | `Consumer` |

You will **NOT** use lambdas for normal logic, loops, or business rules. Just these specific spots.

---

## ✏️ Lambda Syntax — Just 4 Forms

```java
// 1. No arguments
() -> System.out.println("hi")

// 2. One argument (parentheses optional when type is inferred)
x -> x * 2

// 3. Two arguments
(a, b) -> a + b

// 4. Multi-line body — needs braces and explicit return
(a, b) -> {
    int sum = a + b;
    return sum;
}
```

> **Single-arg shortcut:** parentheses are optional when there's exactly one argument with inferred type. `x -> x * 2` and `(x) -> x * 2` are identical.

---

## 🔗 Method References — Even Shorter

When the lambda just calls an existing method, use `::` to skip the boilerplate.

```java
(a, b) -> Integer.sum(a, b)        // long form
Integer::sum                       // method reference — same thing

s -> s.length()                    // long form
String::length                     // method reference

x -> System.out.println(x)         // long form
System.out::println                // method reference
```

---

## 🔤 What Do `T`, `R`, `U`, `K`, `V` Mean?

These are **generic type parameters** — placeholders for "whatever type the user plugs in." They're just **conventions**, not keywords. The compiler doesn't care what you call them.

| Letter | Meaning | Example |
| --- | --- | --- |
| `T` | **T**ype (the input type, generic stand-in) | `List<T>` — list of T |
| `R` | **R**eturn type | `Function<T, R>` — takes T, returns R |
| `U` | A **second** input type (next letter after T) | `BiFunction<T, U, R>` |
| `K` | **K**ey type (in maps) | `Map<K, V>` |
| `V` | **V**alue type (in maps) | `Map<K, V>` |
| `E` | **E**lement type (in collections) | `List<E>`, `Set<E>` |

So when you read `BiFunction<T, U, R>`, mentally translate it as **"takes a T and a U, returns an R."** That's the entire signature in one sentence.

---

## 📚 The Functional Interfaces — Deep Dive

A **functional interface** is just an interface with **exactly one abstract method**. Java provides several built-in ones for common shapes of behavior. When you see `Comparator<Integer>` or `Function<String, Integer>` in an API signature, you can pass a lambda that matches that shape.

Below, each interface is shown with:
- Its single method
- What "shape" of lambda it expects
- A real DSA example

---

### 🔹 `Comparator<T>` — "compare two T's, return an int"

**The single method:**
```java
int compare(T a, T b);
```

**What does `compare` do?** Nothing on its own. `compare` is just the **name of the slot** where YOUR lambda body lives. The lambda you write becomes the body of `compare`.

**Generic binding (read this carefully — it applies to every interface below):**
```
Comparator<Integer>     myCmp = (a, b) -> a - b;
            ^^^^^^^               ^  ^      ^^^^^
            T = Integer        a, b: Int  returns int
```
When you write `Comparator<Integer>`:
- `T` is bound to `Integer` → both `a` and `b` are `Integer`
- Return type is always `int` (built into `Comparator`)

**Behind the scenes:**
```java
// What you write (1 line)
Comparator<Integer> cmp = (a, b) -> a - b;

// What Java effectively builds
Comparator<Integer> cmp = new Comparator<Integer>() {
    @Override
    public int compare(Integer a, Integer b) {
        return a - b;       // <-- YOUR LAMBDA BODY
    }
};
```

**Call it manually:**
```java
int result = cmp.compare(5, 3);   // result = 2 (positive → 5 should come after 3)
```

**Rule of return value:**
- **Negative** → `a` should come before `b`
- **Zero** → equal (treated as duplicates in TreeSet/TreeMap!)
- **Positive** → `a` should come after `b`

**Used in DSA — someone else calls `.compare` for you:**
```java
// Sort intervals by start time
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
// Internally: Arrays.sort calls cmp.compare(...) repeatedly while sorting

// Max-heap (reverse natural order)
PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
// Internally: PQ calls cmp.compare(...) to decide who goes on top
```

> Whenever you sort, build a heap, or use a TreeSet/TreeMap with custom order, you're writing a `Comparator`. The library calls `.compare` on every pair — you just provide the rule.

---

### 🔹 `Function<T, R>` — "take a T, return an R"

**The single method:**
```java
R apply(T t);
```

**What does `apply` do?** Nothing on its own. `apply` is just the **name of the slot** where YOUR lambda body lives. The lambda body **becomes** the body of `apply`.

**Generic binding:**
```
Function<String, Integer>   getLength = s -> s.length();
          ^^^^^^  ^^^^^^^                ^   ^^^^^^^^^^^
          T       R                     T=s   returns R
```
When you write `Function<String, Integer>`:
- `T` is bound to `String` → `s` is a `String`
- `R` is bound to `Integer` → return must be `Integer`

**Behind the scenes:**
```java
// What you write (1 line)
Function<String, Integer> getLength = s -> s.length();

// What Java effectively builds
Function<String, Integer> getLength = new Function<String, Integer>() {
    @Override
    public Integer apply(String s) {
        return s.length();      // <-- YOUR LAMBDA BODY
    }
};
```

**Call it manually:**
```java
int n = getLength.apply("hello");   // n = 5
```

So `getLength.apply("hello")` is just a fancy way to run `s -> s.length()` with `s = "hello"`.

**Used in DSA — someone else calls `.apply` for you:**

#### Use #1 — `computeIfAbsent` (HashMap calls `.apply(key)` only when key is missing)

```java
Function<String, List<String>> makeList = k -> new ArrayList<>();
// Generic binding: T = String (key type), R = List<String> (value type)
// k is a String. Returns a fresh ArrayList<String>.

groups.computeIfAbsent("apple", makeList);   // HashMap calls makeList.apply("apple") internally

// Or inline (most common form):
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
```

#### Use #2 — `stream.map` (Stream calls `.apply` on EVERY element)

> ⚠️ **`stream.map()` has NOTHING to do with `HashMap`.** "Map" here means **"transform each element"** — like a math function maps an input to an output. Don't confuse the two.

```java
List<String> list = Arrays.asList("apple", "fig", "banana");

list.stream()
    .map(s -> s.length())          // Function<String, Integer> applied to each element
    .forEach(System.out::println); // prints 5, 3, 6

list.stream().map(String::length); // method reference equivalent
```

**Trace it:**
```
List: ["apple", "fig", "banana"]
        ↓ .stream()
Stream<String>: "apple", "fig", "banana"
        ↓ .map(s -> s.length())   // Function<String, Integer> applied to each
Stream<Integer>: 5, 3, 6
```

| Element | After `.map(s -> s.length())` |
| --- | --- |
| `"apple"` | `5` |
| `"fig"` | `3` |
| `"banana"` | `6` |

The Function transforms each element from one type/value to another. The stream's **type changes mid-pipeline** — was `Stream<String>`, now `Stream<Integer>`.

---

### 🔹 `BiFunction<T, U, R>` — "take a T and a U, return an R"

**The single method:**
```java
R apply(T t, U u);
```

**What does `apply` do?** Same answer — it's the **slot** YOUR lambda fills. Two-input version of `Function`. Used to **combine** two values into one.

**Generic binding:**
```
BiFunction<Integer, Integer, Integer>  add = (a, b) -> a + b;
            ^^^^^^^  ^^^^^^^  ^^^^^^^         ^  ^      ^^^^^
            T        U        R               T  U      returns R
```
When you write `BiFunction<Integer, Integer, Integer>`:
- `T` = `Integer` → `a` is Integer
- `U` = `Integer` → `b` is Integer
- `R` = `Integer` → return is Integer

**Behind the scenes:**
```java
// What you write (1 line)
BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;

// What Java effectively builds
BiFunction<Integer, Integer, Integer> add = new BiFunction<Integer, Integer, Integer>() {
    @Override
    public Integer apply(Integer a, Integer b) {
        return a + b;       // <-- YOUR LAMBDA BODY
    }
};
```

**Call it manually:**
```java
int sum = add.apply(2, 3);   // sum = 5
```

**Used in DSA — someone else calls `.apply` for you:**

```java
// merge needs a BiFunction — HashMap calls .apply(oldVal, newVal) when key already exists
freq.merge(n, 1, (oldVal, newVal) -> oldVal + newVal);
freq.merge(n, 1, Integer::sum);   // method reference equivalent
// Generic binding here: T = old value type, U = new value type, R = combined value type

// compute receives (key, currentValue) and returns the new value
map.compute(key, (k, v) -> v == null ? 1 : v + 1);
// Generic binding: T = key type, U = current value type, R = new value type
```

> Whenever a method needs **two inputs combined into one output**, the API will ask for a `BiFunction`.

---

### 🔹 `Predicate<T>` — "take a T, return a boolean"

**The single method:**
```java
boolean test(T t);
```

**What does `test` do?** Just a slot — YOUR lambda body decides yes/no for each input. Used wherever Java needs a yes/no decision per element (filters, removals, condition checks).

**Generic binding:**
```
Predicate<Integer>   isPositive = x -> x > 0;
           ^^^^^^^                 ^   ^^^^^^^
           T = Integer            T=x   returns boolean
```
When you write `Predicate<Integer>`:
- `T` = `Integer` → `x` is Integer
- Return type is always `boolean` (built into `Predicate`)

**Behind the scenes:**
```java
// What you write
Predicate<Integer> isPositive = x -> x > 0;

// What Java builds
Predicate<Integer> isPositive = new Predicate<Integer>() {
    @Override
    public boolean test(Integer x) {
        return x > 0;       // <-- YOUR LAMBDA BODY
    }
};
```

**Call it manually:**
```java
boolean ok = isPositive.test(5);    // true
boolean no = isPositive.test(-3);   // false
```

**Used in DSA — someone else calls `.test` for you:**
```java
// removeIf calls .test on each element; removes those returning true
list.removeIf(x -> x < 0);

// stream.filter calls .test on each; keeps those returning true
list.stream().filter(x -> x % 2 == 0);
```

**Trace `list.removeIf(x -> x < 0)`** on `[3, -1, 4, -2, 5]`:

| Element | `.test(x)` returns | Action |
| --- | --- | --- |
| `3` | `false` | keep |
| `-1` | `true` | remove |
| `4` | `false` | keep |
| `-2` | `true` | remove |
| `5` | `false` | keep |

Final list: `[3, 4, 5]`

---

### 🔹 `Consumer<T>` — "take a T, do something, return nothing"

**The single method:**
```java
void accept(T t);
```

**What does `accept` do?** Just a slot. YOUR lambda body runs the side effect (printing, mutating, logging). Note the return type is `void` — Consumers don't return anything.

**Generic binding:**
```
Consumer<String>   printer = s -> System.out.println(s);
          ^^^^^^             ^   ^^^^^^^^^^^^^^^^^^^^^^^
          T = String       T=s   returns void
```
When you write `Consumer<String>`:
- `T` = `String` → `s` is String
- Return type is always `void`

**Behind the scenes:**
```java
// What you write
Consumer<String> printer = s -> System.out.println(s);

// What Java builds
Consumer<String> printer = new Consumer<String>() {
    @Override
    public void accept(String s) {
        System.out.println(s);   // <-- YOUR LAMBDA BODY (no return)
    }
};
```

**Call it manually:**
```java
printer.accept("hello");   // prints: hello
```

**Used in DSA — someone else calls `.accept` for you:**
```java
// list.forEach calls .accept(element) for each element
list.forEach(x -> System.out.println(x));
list.forEach(System.out::println);   // method reference equivalent

// Stream.forEach — same idea
list.stream().filter(x -> x > 0).forEach(x -> System.out.println(x));
```

> Use a Consumer when the API says **"do this for each element, no return needed."**

---

### 🔹 `BiConsumer<T, U>` — "take a T and a U, do something, return nothing"

**The single method:**
```java
void accept(T t, U u);
```

**What does `accept` do?** Same as `Consumer` — your lambda body fills the slot. Two-input version. Most common use: iterating a `Map` (key + value).

**Generic binding:**
```
BiConsumer<String, Integer>  printer = (k, v) -> System.out.println(k + "=" + v);
            ^^^^^^  ^^^^^^^             ^  ^    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            T       U                 T=k U=v   returns void
```
When you write `BiConsumer<String, Integer>`:
- `T` = `String` → `k` is String
- `U` = `Integer` → `v` is Integer
- Return type is always `void`

**Behind the scenes:**
```java
// What you write
BiConsumer<String, Integer> printer = (k, v) -> System.out.println(k + "=" + v);

// What Java builds
BiConsumer<String, Integer> printer = new BiConsumer<String, Integer>() {
    @Override
    public void accept(String k, Integer v) {
        System.out.println(k + "=" + v);   // <-- YOUR LAMBDA BODY (no return)
    }
};
```

**Call it manually:**
```java
printer.accept("apple", 5);   // prints: apple=5
```

**Used in DSA — someone else calls `.accept` for you:**
```java
// Map.forEach calls .accept(key, value) for every entry
Map<String, Integer> freq = Map.of("apple", 5, "fig", 3);
freq.forEach((k, v) -> System.out.println(k + " -> " + v));
// prints:
// apple -> 5
// fig -> 3
```

> `Map.forEach` takes a `BiConsumer<K, V>`, while `List.forEach` takes a `Consumer<E>`. Same idea, different arity.

---

### 🔹 `Supplier<T>` — "take nothing, return a T"

**The single method:**
```java
T get();
```

**What does `get` do?** Just a slot. YOUR lambda runs (with no input) and returns a value of type `T`. Used for **lazy value creation** — when you want to defer building something until it's actually needed.

**Generic binding:**
```
Supplier<List<Integer>>  newList = () -> new ArrayList<>();
          ^^^^^^^^^^^^^             ^^   ^^^^^^^^^^^^^^^^^^
          T = List<Integer>       no input   returns T
```
When you write `Supplier<List<Integer>>`:
- `T` = `List<Integer>` → return must be `List<Integer>`
- No input arguments — that's why it's `()`

**Behind the scenes:**
```java
// What you write
Supplier<List<Integer>> newList = () -> new ArrayList<>();

// What Java builds
Supplier<List<Integer>> newList = new Supplier<List<Integer>>() {
    @Override
    public List<Integer> get() {
        return new ArrayList<>();   // <-- YOUR LAMBDA BODY
    }
};
```

**Call it manually:**
```java
List<Integer> list = newList.get();   // builds a fresh ArrayList ONLY when called
```

**Why "lazy"?** A `Supplier` is a **promise to build something later**, not the thing itself. The lambda runs **only when `.get()` is called**.

```java
// Eager — list is built right now (always)
List<Integer> eager = new ArrayList<>();

// Lazy — supplier holds the recipe; nothing is built yet
Supplier<List<Integer>> lazy = () -> new ArrayList<>();
List<Integer> built = lazy.get();   // NOW the ArrayList is built
```

**Used in DSA — someone else calls `.get` for you:**
```java
// Optional.orElseGet — calls supplier ONLY if Optional is empty
String value = optional.orElseGet(() -> computeExpensiveDefault());
// If optional has a value, supplier is NEVER called → no waste
```

> You won't use `Supplier` much in DSA, but recognize the shape: zero arguments, returns a value.

---

### 📋 The Big Picture (One-Glance Table)

| Interface | Method | Shape | Mnemonic |
| --- | --- | --- | --- |
| `Comparator<T>` | `int compare(T, T)` | `(a, b) -> int` | "Order two things" |
| `Function<T, R>` | `R apply(T)` | `t -> r` | "Transform one thing" |
| `BiFunction<T, U, R>` | `R apply(T, U)` | `(t, u) -> r` | "Combine two things" |
| `Predicate<T>` | `boolean test(T)` | `t -> bool` | "Yes or no?" |
| `Consumer<T>` | `void accept(T)` | `t -> void` | "Do something with it" |
| `BiConsumer<T, U>` | `void accept(T, U)` | `(t, u) -> void` | "Do something with both" |
| `Supplier<T>` | `T get()` | `() -> t` | "Make me one" |

---

## 🪄 Demystifying `computeIfAbsent` — Where's the Hidden `if`?

This is the question that confused you, and it's a great one. Let's open the hood.

You wrote:
```java
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
```

And asked: *"Where is the if check that decides whether to create the list?"*

**Answer: it's inside `computeIfAbsent`'s implementation.** You don't see it because it's hidden inside the `HashMap` source code. Here's a simplified version of what `computeIfAbsent` actually does:

```java
// SIMPLIFIED PSEUDO-CODE of HashMap.computeIfAbsent
public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    V existingValue = this.get(key);          // 1. Look up the current value

    if (existingValue != null) {              // 2. THE HIDDEN if CHECK
        return existingValue;                 //    Already exists — just return it
    }

    V newValue = mappingFunction.apply(key);  // 3. Run YOUR lambda to build a fresh value
    this.put(key, newValue);                  // 4. Store it in the map
    return newValue;                          // 5. Return the new value
}
```

So when you call:

```java
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
```

Here's the **moment-by-moment story:**

1. HashMap looks up `key`.
2. If the key already has a list → returns that existing list, **your lambda is never called**.
3. If the key is missing → HashMap calls **your lambda** `k -> new ArrayList<>()` (passing `key` as `k`), gets a brand-new empty list, stores it, and returns it.
4. Either way, you get back **a list** — and `.add(s)` appends `s` to it.

### Why pass a lambda instead of a list directly?

Look at the broken code first:
```java
// ❌ Builds an ArrayList every single iteration, even when one already exists
groups.computeIfAbsent(key, new ArrayList<>()).add(s);
```

This won't even compile — `computeIfAbsent` requires a `Function`, not a `List`. But conceptually: if Java *did* let you pass a value directly, you'd be building a fresh `ArrayList` on every call, even when the key already exists. **Wasted memory, wasted time.**

By passing a lambda (`Function`), you're handing HashMap a **recipe** — "*here's how to build one if you need it.*" HashMap only runs the recipe when actually needed. This is called **lazy evaluation.**

### Equivalent code without `computeIfAbsent`

This is what you'd otherwise write by hand:
```java
// Long form — what computeIfAbsent saves you from
List<String> list = groups.get(key);
if (list == null) {
    list = new ArrayList<>();
    groups.put(key, list);
}
list.add(s);
```

`computeIfAbsent` is just this 5-line block compressed into 1 line — with the conditional logic *baked into the method*.

---

## ⚡ Common DSA Patterns Using Lambdas — Annotated

The seven patterns below cover **~95% of all lambda use in DSA solutions.** Each one is annotated with what's happening behind the scenes.

---

### **1. Custom Sort with Comparator**

> Pass a comparator lambda to sort by anything: length, frequency, multiple fields.

```java
// Sort strings by length, ascending
Arrays.sort(strs, (a, b) -> a.length() - b.length());
```

**What's happening:** `Arrays.sort` repeatedly compares pairs of strings. For each pair, it calls **your lambda** with two strings and uses the returned int (negative / zero / positive) to decide which comes first.

```java
// Sort intervals by start, tiebreak by end
Arrays.sort(intervals, (a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
```

**Why ternary?** When two intervals share a start, the comparator returns `0`, which means "equal" — but they're not really equal. Tiebreak with `a[1] - b[1]` so order is fully deterministic.

```java
// Sort numbers descending (reverse natural order)
Arrays.sort(arr, (a, b) -> b - a);
```

**Trick:** swapping `a` and `b` in the subtraction reverses the order. Default ascending uses `a - b`; reversing it produces descending.

```java
// Sort by frequency map (descending), tiebreak alphabetically
Arrays.sort(words, (a, b) -> {
    if (!freq.get(a).equals(freq.get(b))) {
        return freq.get(b) - freq.get(a);   // higher frequency first
    }
    return a.compareTo(b);                  // tiebreak: alphabetical
});
```

> ⚠️ Primitive `int[]` arrays can't use Comparator directly — `Arrays.sort(int[])` only sorts ascending. Use `Integer[]` if you need custom order, or sort then reverse manually.

---

### **2. PriorityQueue with Custom Order**

> Default `PriorityQueue` is a min-heap (smallest on top). For max-heap or custom keys, pass a comparator lambda.

```java
// Min-heap (default — no comparator needed)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();

// Max-heap — biggest comes out first
PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);

// Min-heap by 2nd element of int[] (e.g., {value, frequency} — sorts by frequency)
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);

// Max-heap of strings by length
PriorityQueue<String> longest = new PriorityQueue<>((a, b) -> b.length() - a.length());
```

**Why use a heap with custom comparator?** Top-K problems. E.g. "find K most frequent" — push `{element, freq}` pairs into a min-heap of size K ordered by frequency. The smallest stays on top so you can drop it when a larger one comes in.

---

### **3. TreeSet / TreeMap with Custom Order**

> Same comparator pattern works for sorted sets and maps.

```java
// TreeSet sorted by absolute value
TreeSet<Integer> byAbs = new TreeSet<>((a, b) -> Math.abs(a) - Math.abs(b));

// TreeSet of int[] sorted by value, with index tiebreaker (avoid the silent-reject gotcha)
TreeSet<int[]> ts = new TreeSet<>((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
```

**The TreeSet trap:** TreeSet uses your comparator to decide equality too. If your comparator returns `0` for two different elements, TreeSet thinks they're duplicates and **silently rejects** the second one. Always include a tiebreaker if duplicates can occur.

---

### **4. `computeIfAbsent` — Map of List / Map of Set**

> See the dedicated explainer above for the full story. The lambda is the **recipe** for what to create when the key is missing.

```java
// Map of List — group anagrams
Map<String, List<String>> groups = new HashMap<>();
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);

// Map of Set — adjacency list with no duplicate edges
Map<Integer, Set<Integer>> adj = new HashMap<>();
adj.computeIfAbsent(u, k -> new HashSet<>()).add(v);
```

**Why the `k ->` parameter?** `computeIfAbsent` always passes the key into your lambda, in case you want to use it. Most of the time you ignore it (you don't need the key to build an empty `ArrayList`). You can name it anything — `k`, `key`, `_` — convention is `k`.

---

### **5. `merge` — Frequency Counting & Combining**

> Combines an existing value with a new one using your provided function. Cleanest way to build a frequency map.

```java
// Frequency map — for each element, add 1 to its count
for (int n : nums) {
    freq.merge(n, 1, Integer::sum);
}
```

**What `merge` does internally** (simplified):
```java
public V merge(K key, V value, BiFunction<V, V, V> remapping) {
    V old = this.get(key);
    if (old == null) {
        this.put(key, value);                 // first occurrence — just store value
        return value;
    }
    V combined = remapping.apply(old, value); // BOTH exist — combine
    this.put(key, combined);
    return combined;
}
```

So `freq.merge(n, 1, Integer::sum)` reads as:
- "Insert `n -> 1`, **but if `n` already exists**, replace the existing count with `Integer.sum(oldCount, 1)`."

```java
// Equivalent explicit form (same thing, longer)
for (int n : nums) {
    freq.merge(n, 1, (oldVal, newVal) -> oldVal + newVal);
}

// Track the max value seen for each key
maxByKey.merge(key, value, Math::max);
```

---

### **6. `removeIf` — Safe Removal During Iteration**

> Predicate lambda decides which elements to remove. Avoids `ConcurrentModificationException`.

```java
list.removeIf(x -> x < 0);
set.removeIf(s -> s.isEmpty());
map.entrySet().removeIf(e -> e.getValue() == 0);
```

**Why this exists:** if you try `for (var x : list) { if (...) list.remove(x); }` you'll crash with `ConcurrentModificationException`. `removeIf` walks the collection internally with a safe iterator and removes matching elements as it goes.

---

### **7. `forEach` — Iterate with a Side Effect**

> Less common in DSA than a regular `for` loop, but useful for printing or applying a function to all entries.

```java
// Print all entries of a map
map.forEach((k, v) -> System.out.println(k + " -> " + v));

// Apply transformation
list.forEach(x -> System.out.println(x * 2));
```

**Note:** `Map.forEach` takes a `BiConsumer<K, V>` (two args), while `List.forEach` takes a `Consumer<E>` (one arg). The lambda shape changes accordingly.

---

## 🎁 Bonus: Streams (Use Sparingly in DSA)

Streams are powerful but slower than plain loops — and harder to debug. Stick to plain loops for interview problems unless the lambda way is genuinely cleaner.

```java
// Sum of all positive numbers
int sum = list.stream()
    .filter(x -> x > 0)
    .mapToInt(Integer::intValue)
    .sum();

// Convert array to set
Set<Integer> set = Arrays.stream(nums).boxed().collect(Collectors.toSet());

// Sort and take top 3
List<Integer> top3 = list.stream()
    .sorted((a, b) -> b - a)
    .limit(3)
    .collect(Collectors.toList());
```

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

**Comparator subtraction overflow** — `(a, b) -> a - b` fails for extreme int values.

```java
// ❌ overflow when a = Integer.MIN_VALUE, b = positive
PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> a - b);

// ✅ safe — handles overflow correctly
PriorityQueue<Integer> pq = new PriorityQueue<>(Integer::compare);
PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> Integer.compare(a, b));
```

---

**Captured variables must be "effectively final"** — you can't modify a local variable inside a lambda.

```java
// ❌ compile error — counter must be effectively final
int counter = 0;
list.forEach(x -> counter++);

// ✅ workaround: wrap in array (single-element)
int[] counter = { 0 };
list.forEach(x -> counter[0]++);

// ✅ or use AtomicInteger for cleaner intent
AtomicInteger counter = new AtomicInteger();
list.forEach(x -> counter.incrementAndGet());
```

---

**Lambda body without braces = expression** (auto-returns). With braces = statements (must use `return`).

```java
(a, b) -> a + b              // ✅ expression — auto-returns a + b
(a, b) -> { return a + b; }  // ✅ statement — explicit return
(a, b) -> { a + b; }         // ❌ compile error — no return, just an orphan expression
```

---

**`computeIfAbsent` second arg is a Function, not an object.**

```java
// ❌ wrong — passes an actual ArrayList object (won't compile)
map.computeIfAbsent(key, new ArrayList<String>()).add(s);

// ✅ correct — passes a lambda that BUILDS the list when needed
map.computeIfAbsent(key, k -> new ArrayList<String>()).add(s);
```

> Why a function and not a value? **Lazy evaluation.** The lambda runs only when the key is missing, avoiding wasted allocations on every call.

---

**Method reference target-type confusion.**

```java
// ❌ Integer::sum is a BiFunction (takes 2 args), not a Function
Function<Integer, Integer> doubler = Integer::sum;

// ✅
BiFunction<Integer, Integer, Integer> sum = Integer::sum;
Function<Integer, Integer> doubler = x -> x * 2;
```

---

**Comparator must be consistent** — if `compare(a, b) == 0`, the elements are treated as equal. In TreeSet this means duplicates get rejected (see Set notes for the index-tiebreaker fix).

---

## ⚡ Quick Cheat Sheet — Which Lambda Where?

| API | Lambda Type | Common Form |
| --- | --- | --- |
| `Arrays.sort(arr, cmp)` | Comparator | `(a, b) -> a - b` |
| `Collections.sort(list, cmp)` | Comparator | same |
| `new PriorityQueue<>(cmp)` | Comparator | `(a, b) -> b - a` for max-heap |
| `new TreeSet<>(cmp)` / `new TreeMap<>(cmp)` | Comparator | same |
| `map.computeIfAbsent(k, fn)` | Function<K, V> | `k -> new ArrayList<>()` |
| `map.merge(k, v, fn)` | BiFunction | `Integer::sum` |
| `map.compute(k, fn)` | BiFunction | `(k, v) -> v == null ? 1 : v + 1` |
| `list.removeIf(p)` / `set.removeIf(p)` | Predicate | `x -> x < 0` |
| `map.entrySet().removeIf(p)` | Predicate | `e -> e.getValue() == 0` |
| `list.forEach(c)` | Consumer | `x -> println(x)` |
| `map.forEach(bc)` | BiConsumer | `(k, v) -> println(k)` |
| `stream.filter(p)` | Predicate | `x -> x > 0` |
| `stream.map(f)` | Function | `String::length` |
| `stream.sorted(cmp)` | Comparator | `(a, b) -> a - b` |

---

## 🎯 Practice Targets (Just These Three)

You're ready for DSA lambda usage if you can write these from memory:

1. **Min-heap of `int[]` ordered by 2nd element**
   ```java
   PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);
   ```

2. **Map of List using `computeIfAbsent`**
   ```java
   map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
   ```

3. **Frequency map using `merge`**
   ```java
   freq.merge(c, 1, Integer::sum);
   ```

Get these three reflexive, and you've covered 80% of DSA lambda needs. The rest is recognizing patterns when you see them.

---

## 🧾 TL;DR — One-Page Summary

- **A lambda is a short way to implement a one-method interface.** That's it.
- **`(a, b) -> a + b`** is shorthand for `new Comparator() { public int compare(a, b) { return a + b; } }`.
- **`T`, `R`, `U`, `K`, `V`** are just placeholders for "any type" — Type, Return, second-input, Key, Value.
- **Functional interfaces** = interfaces with one method. Java provides Comparator, Function, BiFunction, Predicate, Consumer, BiConsumer, Supplier.
- **`computeIfAbsent(k, fn)`** = "if `k` is missing, run `fn` to build a value, store it, return it; otherwise return the existing one."
- **`merge(k, v, fn)`** = "store `k -> v`, but if `k` already exists, replace with `fn(oldValue, v)`."
- **The three must-know forms:** custom-comparator PriorityQueue, `computeIfAbsent` for Map of List, `merge` for frequency counting.
