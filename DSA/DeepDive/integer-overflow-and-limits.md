# Integer Overflow & Limits in Java DSA — Deep Dive

> A from-scratch guide to integer overflow, primitive limits, boxing pitfalls, and the silent bugs they cause across DSA problems. Not data-structure-specific — applies everywhere arithmetic happens. Read top to bottom once. Refer back when problem constraints feel tight.

---

## 🎯 Why This Matters

Almost every "wrong answer" you'll get on a hard LeetCode problem isn't a logic bug — it's a **silent overflow** or a **boxing surprise**. Symptoms:

- Your code passes 100/103 test cases. The 3 failing ones have huge numbers, or `Integer.MAX_VALUE`, or empty maps
- Your comparator works in development but produces a weirdly-sorted result on one test
- A `Map.get` line throws `NullPointerException` on a key you "knew" was there
- Two `Integer` values that look equal compare as `!=`

These are not algorithm mistakes. They're **language traps**. Once you know the traps exist, you stop being surprised by them, and your accept rate jumps.

By the end of this doc you should:

1. Recognize the **three classic overflow traps** by sight (comparator subtraction, sum overflow, binary search midpoint)
2. Know **when to reach for `long`** instead of `int` (reading problem constraints)
3. Avoid the **boxing trap** when comparing `Integer` objects
4. Have a mental table of **DSA patterns where overflow bites** so you check upfront

This is **language hygiene**. It's interview gold because most candidates skip it.

---

## 🔢 What Is Integer Overflow?

In Java, `int` is **32 bits**. It can represent integers in the range:

```
Integer.MIN_VALUE = -2,147,483,648    (about -2.1 billion)
Integer.MAX_VALUE =  2,147,483,647    (about +2.1 billion)
```

That's about 4.3 billion distinct values total. Sounds like a lot — until you start adding things together.

### What happens when you go past the boundary?

Java doesn't crash, doesn't warn, doesn't throw. It just **wraps around**.

```java
int max = Integer.MAX_VALUE;     // 2,147,483,647
int next = max + 1;              // expected: 2,147,483,648
                                 // ACTUAL: -2,147,483,648 (Integer.MIN_VALUE!)
System.out.println(next);        // prints: -2147483648
```

The arithmetic silently produces the **wrong answer**. Your variable is now negative when it should have been positive. Every subsequent computation uses this wrong value.

> **Visualize it as a clock** that wraps around. After `+2,147,483,647` comes `-2,147,483,648`. Adding 1 always increments the position; the names just wrap.

```
... -2 → -1 → 0 → 1 → 2 ... → 2,147,483,647 → -2,147,483,648 → -2,147,483,647 ...
                                              ↑
                                       overflow happens here
```

This is called **two's complement representation**. You don't need to understand the bit-level details for DSA — you just need to **know it can happen and where**.

---

## 📏 Java Primitive Ranges (Memorize the Two That Matter)

| Type | Bytes | Range (rough) | Use in DSA |
| --- | --- | --- | --- |
| `byte` | 1 | -128 to 127 | Rarely (memory-tight) |
| `short` | 2 | -32K to 32K | Almost never |
| `int` | 4 | ±2.1 × 10⁹ | **Default for indexes, counts, small values** |
| `long` | 8 | ±9.2 × 10¹⁸ | **Use whenever overflow is possible** |
| `float` | 4 | Low precision | Almost never (use `double` if needed) |
| `double` | 8 | High precision | Probability / floating point |

You really only need to remember:

- **`int` ≈ ±2 billion** — fine for indexes, single values up to a few billion
- **`long` ≈ ±9 quintillion (10¹⁸)** — use when adding/multiplying ints that might exceed 2 billion

> **Rule of thumb:** if you're **summing or multiplying many ints**, declare the accumulator as `long`. The cost is one extra letter and 4 extra bytes — tiny compared to a wrong-answer submission.

---

## 🧱 The Special Constants

These show up constantly in DSA. Memorize them.

```java
Integer.MAX_VALUE    //  2,147,483,647   (≈ +2.1 × 10⁹)
Integer.MIN_VALUE    // -2,147,483,648   (≈ -2.1 × 10⁹)
Long.MAX_VALUE       //  9,223,372,036,854,775,807   (≈ +9.2 × 10¹⁸)
Long.MIN_VALUE       // -9,223,372,036,854,775,808   (≈ -9.2 × 10¹⁸)
```

### Common uses in DSA

```java
// "Max so far" tracking — initialize to MIN so any real value beats it
int maxSum = Integer.MIN_VALUE;
for (int n : nums) {
    maxSum = Math.max(maxSum, n);
}

// "Min so far" tracking — initialize to MAX so any real value is smaller
int minVal = Integer.MAX_VALUE;
for (int n : nums) {
    minVal = Math.min(minVal, n);
}

// Sentinel for "infinity" in shortest-path or DP problems
int[] dist = new int[n];
Arrays.fill(dist, Integer.MAX_VALUE);
dist[start] = 0;
```

> ⚠️ **Watch out:** if your algorithm later **adds** to one of these sentinels (like `Integer.MAX_VALUE + 1`), you overflow. See Trap 2.

---

## 🪤 The Three Classic Overflow Traps

These three account for ~95% of overflow bugs in DSA. Memorize the wrong-vs-right code.

---

### Trap 1 — Comparator Subtraction

A common pattern is to write a comparator using subtraction:

```java
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);
```

This **looks right** for "ascending by second element" — and it works for typical small numbers. But:

```java
int a = Integer.MIN_VALUE;       // -2,147,483,648
int b = 5;
int diff = a - b;                // expected: -2,147,483,653 (negative)
                                 // ACTUAL:   2,147,483,643 (POSITIVE!)
```

Because `-2,147,483,653` can't fit in an `int`, the result wraps to a positive number. The Comparator returns positive (meaning *"a should come after b"*), but the truth is `a < b` (so a should come before b). **Wrong sort order.**

**The fix — use `Integer.compare`, which is overflow-safe:**

```java
// ❌ Subtraction — overflows on extremes
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);

// ✅ Integer.compare — safe for all int values
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));

// ✅ For descending
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(b[1], a[1]));
```

> Internally, `Integer.compare(a, b)` returns `(a < b) ? -1 : ((a == b) ? 0 : 1)` — never overflows because it doesn't subtract.

**Rule:** never use `a - b` in a comparator. Always use `Integer.compare(a, b)` or `Long.compare(a, b)`.

> 🧩 **DSA places this bites:** custom comparators on `Arrays.sort`, `Collections.sort`, `PriorityQueue`, `TreeSet`, `TreeMap`, `stream.sorted`. **Anywhere with a Comparator.**

---

### Trap 2 — Sum Overflow

The most common overflow trap in DSA. Two values that fit in int can sum to a value that doesn't.

```java
int a = 2_000_000_000;
int b = 2_000_000_000;
int sum = a + b;                 // expected: 4,000,000,000
                                 // ACTUAL: -294,967,296 (wrapped!)
```

This breaks problems like:

- **Two Sum** when targets are large
- **Prefix sum** arrays on long arrays of large numbers
- **Dynamic programming** that accumulates costs/counts
- **Multiplication-based** problems (even worse — products grow fast)

**The fix — use `long` for the accumulator:**

```java
// ❌ Sum can overflow int
int sum = 0;
for (int n : nums) {
    sum += n;
}

// ✅ Use long accumulator
long sum = 0;
for (int n : nums) {
    sum += n;
}
```

**For multiplication, cast at least one operand to `long` BEFORE multiplying:**

```java
// ❌ Multiplication overflows before assignment
long product = a * b;            // a * b is computed as int, overflows, THEN assigned to long

// ✅ Cast one operand to long first
long product = (long) a * b;     // a is treated as long, multiplication done in long, no overflow
```

**Rule of thumb based on problem constraints:**

If the problem says `1 <= nums[i] <= 10^9` and `1 <= n <= 10^5`:

- A single value: up to 10⁹ → fits in int (int max ≈ 2.1 × 10⁹) ✅
- **Sum of all values:** up to 10⁵ × 10⁹ = **10¹⁴** → does **NOT** fit in int ❌
- Use `long` for the running sum.

> 🧩 **DSA places this bites:** Two Sum (target overflow), Prefix Sum (LC 560 Subarray Sum Equals K), DP arrays (LC 322 Coin Change, LC 198 House Robber on large inputs), product-based problems, anything where `n × max(value)` exceeds 2 × 10⁹.

---

### Trap 3 — Binary Search Midpoint

The textbook midpoint formula is:

```java
int mid = (low + high) / 2;
```

This is **wrong** when `low + high` overflows (e.g., both near `Integer.MAX_VALUE / 2`). The sum becomes negative, and `mid` lands at a negative index — `ArrayIndexOutOfBoundsException`.

**The fix:**

```java
// ❌ Can overflow if low + high > Integer.MAX_VALUE
int mid = (low + high) / 2;

// ✅ Subtract first, then halve, then add — never overflows
int mid = low + (high - low) / 2;
```

**Why does this work?** `high - low` is non-negative (since `low <= high` in binary search) and small relative to either `low` or `high`. Halving a small number and adding to `low` stays within int range.

**This is the canonical fix in every Java standard-library binary search implementation.** Even if interview test cases don't hit it, write the safe form by reflex.

> 🧩 **DSA places this bites:** Any binary search on indexes — LC 704 Binary Search, LC 35 Search Insert Position, LC 33 Search in Rotated Sorted Array, LC 162 Find Peak Element, LC 540 Single Element in a Sorted Array, LC 410 Split Array Largest Sum (binary search on answer).

---

## 📦 The Boxing Trap (Auto-Unboxing & Integer Cache)

Java has two number types that look the same but are deeply different:

- **`int`** — primitive (raw value, lives on the stack)
- **`Integer`** — object wrapper (heap-allocated, holds an `int` inside)

Generic collections (`Map`, `List`, `Set`, `Queue` of numbers) only work with **objects**, not primitives. So when you do this:

```java
Map<String, Integer> freq = new HashMap<>();
freq.put("apple", 5);     // 5 (int) is auto-BOXED into an Integer
int count = freq.get("apple");   // Integer is auto-UNBOXED into int
```

Java silently inserts boxing/unboxing operations for you. This is convenient — **and a source of two surprising bugs.**

---

### Bug 1 — Auto-unboxing NPE on missing key

```java
Map<String, Integer> count = new HashMap<>();
int x = count.get("missing");    // NullPointerException!
```

**Why?** `count.get("missing")` returns `null` (an Integer reference). To assign it to an `int`, Java tries to unbox `null.intValue()` → **NPE**.

**Fixes:**

```java
// ✅ Option 1 — use getOrDefault
int x = count.getOrDefault("missing", 0);

// ✅ Option 2 — keep as Integer and null-check
Integer x = count.get("missing");
if (x != null) {
    // safe to use
}

// ✅ Option 3 — containsKey check
if (count.containsKey("missing")) {
    int x = count.get("missing");
}
```

**Rule:** if you're doing any arithmetic involving `Map.get`, default to `getOrDefault`. It's almost always what you meant.

> 🧩 **DSA places this bites:** Frequency Maps, Last Seen Index, Memoization HashMaps, Group Counts.

---

### Bug 2 — `==` vs `.equals()` on Integer objects (the cache trap)

This is the most surprising overflow-adjacent bug in Java:

```java
Integer a = 127;
Integer b = 127;
System.out.println(a == b);      // true

Integer x = 128;
Integer y = 128;
System.out.println(x == y);      // FALSE !!
```

**Why?** Java caches `Integer` objects for values **-128 through 127** (the IntegerCache). Above 127, every assignment creates a **new heap object**.

`==` on objects compares **references** (memory addresses), not values:
- 127 falls in the cache → `a` and `b` point to the **same** cached object → `==` is true
- 128 falls outside → `x` and `y` are **different** objects with the same value → `==` is false

**Fixes:**

```java
Integer a = 128;
Integer b = 128;

// ❌ Reference equality — fails outside the cache
if (a == b) { ... }

// ✅ Value equality — always correct
if (a.equals(b)) { ... }

// ✅ Or compare primitives — auto-unboxes both sides
if (a.intValue() == b.intValue()) { ... }

// ✅ Or just use Integer.compare
if (Integer.compare(a, b) == 0) { ... }
```

**Rule:** never use `==` to compare two `Integer` objects. Always use `.equals()` or compare as primitives.

> 🧩 **DSA places this bites:** Comparing `Map<K, Integer>` values (`if (freq.get(a) == freq.get(b))` is a silent bug for counts ≥ 128). Iterating boxed values from collections. **Anywhere you have two `Integer` references and want to test value equality.**

---

## 🗺️ Where Overflow Bites in DSA — Pattern by Pattern

| Pattern | Risk | Fix |
| --- | --- | --- |
| **Comparator with subtraction** | Wrong sort/heap order on extreme values | Use `Integer.compare(a, b)` |
| **BST bounds validation (LC 98)** | False negatives when node values are `Integer.MIN/MAX_VALUE` | Use `Long.MIN_VALUE` / `Long.MAX_VALUE` bounds |
| **Binary search midpoint** | Negative midpoint → IndexOutOfBounds | `low + (high - low) / 2` |
| **Sum / prefix sum / DP accumulator** | Wrong totals | Declare accumulator as `long` |
| **Multiplication of two ints** | Overflows BEFORE assignment | Cast one operand to `long` first |
| **Hashing composite keys with `+` or `*`** | Hash collisions / wrong group | Use `Objects.hash(a, b)` |
| **`==` on `Integer` objects** | Reference comparison gotcha | Use `.equals()` or compare as primitives |
| **Auto-unbox `null` from Map.get** | NullPointerException | Use `getOrDefault(key, default)` |
| **Reverse Integer (LC 7)** | Reversal overflows int | Track in `long`, check bounds |
| **`Math.abs(Integer.MIN_VALUE)`** | Returns `Integer.MIN_VALUE` (negative!) — abs has no positive equivalent | Cast to `long` first: `Math.abs((long) x)` |
| **Power / factorial / combinatorics** | Multiplication explodes quickly | Use `long`, or modular arithmetic if problem says "answer mod 10⁹+7" |
| **Two pointer / sliding window with sum** | Window sum overflow | `long` window sum |

---

## 📖 Reading Problem Constraints — When Do You Need `long`?

LeetCode-style problems always state input constraints. Read them carefully.

```
Constraints:
  1 <= n <= 10^5
  -10^9 <= nums[i] <= 10^9
```

**Quick mental math:**

- **Single element:** `10⁹` → fits in int (int max ≈ `2.1 × 10⁹`) ✅
- **Sum of all elements:** `n × max|value|` = `10⁵ × 10⁹` = `10¹⁴` → **does NOT fit in int** ❌ → use `long`
- **Product of two elements:** up to `10⁹ × 10⁹` = `10¹⁸` → **doesn't fit in int** ❌ → use `long`
- **Sum of products:** much worse — definitely `long` (or modular)

**Decision tree:**

```
Does the problem involve sums?         → How many things × how large?
                                          If product > 2 × 10⁹ → use long

Does the problem involve products?      → Always cast at least one to long before *

Does the answer have a "mod 10^9 + 7"   → You're meant to keep all intermediate
clause?                                   values < 2 × 10⁹ via mod after every op.
                                          Use long during accumulation, then mod.

Does the problem have negative numbers? → All MIN/MAX traps apply
                                          (Math.abs(Integer.MIN_VALUE) is a classic)
```

> **One conservative habit:** when in doubt, **use `long` for accumulators.** The cost is 4 bytes; the upside is no overflow surprises. Cast back to int (`(int) result`) at the end if the return type requires it.

---

## 🛠️ The Fix Patterns Cheat Sheet

```java
// =========================================================
// 1. SUM ACCUMULATOR — always long when in doubt
// =========================================================
long sum = 0;
for (int n : nums) {
    sum += n;
}

// =========================================================
// 2. MULTIPLICATION — cast first, multiply in long
// =========================================================
long product = (long) a * b;

// =========================================================
// 3. COMPARATOR — never subtract
// =========================================================
Arrays.sort(arr, (a, b) -> Integer.compare(a, b));            // ascending
Arrays.sort(arr, (a, b) -> Integer.compare(b, a));            // descending

PriorityQueue<int[]> pq = new PriorityQueue<>(
    (a, b) -> Integer.compare(a[1], b[1])                     // by index 1, ascending
);

// =========================================================
// 4. BINARY SEARCH MIDPOINT — overflow-safe form
// =========================================================
int mid = low + (high - low) / 2;

// =========================================================
// 5. BST BOUNDS — use long, not int
// =========================================================
private boolean validate(TreeNode node, long min, long max) {
    if (node == null) {
        return true;
    }
    if (node.val <= min || node.val >= max) {
        return false;
    }
    return validate(node.left, min, node.val)
        && validate(node.right, node.val, max);
}
// caller: validate(root, Long.MIN_VALUE, Long.MAX_VALUE)

// =========================================================
// 6. MAP.GET WITH ARITHMETIC — getOrDefault
// =========================================================
Map<String, Integer> count = new HashMap<>();
int total = count.getOrDefault("apple", 0) + 1;

// =========================================================
// 7. INTEGER OBJECT EQUALITY — use equals or unbox
// =========================================================
Integer a = freq.get("apple");
Integer b = freq.get("banana");

// ❌ if (a == b)
// ✅
if (a.equals(b)) { ... }
// or:
if (a.intValue() == b.intValue()) { ... }

// =========================================================
// 8. Math.abs ON Integer.MIN_VALUE — cast to long first
// =========================================================
long abs = Math.abs((long) x);    // safe even when x == Integer.MIN_VALUE

// =========================================================
// 9. REVERSE INTEGER (LC 7) — track in long, check bounds
// =========================================================
public int reverse(int x) {
    long result = 0;
    while (x != 0) {
        result = result * 10 + x % 10;
        x /= 10;
    }
    if (result > Integer.MAX_VALUE || result < Integer.MIN_VALUE) {
        return 0;
    }
    return (int) result;
}
```

---

## 🧩 Practice Problems (Where Overflow Is The Test)

These problems specifically reward careful overflow handling. Try them after reading the rest of this doc.

### Direct overflow tests
1. **LC 7** Reverse Integer — track in `long`, bounds check at the end
2. **LC 8** String to Integer (atoi) — clamp to `Integer.MAX_VALUE` / `MIN_VALUE`
3. **LC 50** Pow(x, n) — `Math.abs(Integer.MIN_VALUE)` trap, cast to `long`
4. **LC 29** Divide Two Integers — cast operands to `long`

### Sum overflow tests
5. **LC 1** Two Sum — large values + targets
6. **LC 167** Two Sum II — sum can exceed int
7. **LC 560** Subarray Sum Equals K — prefix sums on large arrays
8. **LC 53** Maximum Subarray — running sum should be `long` for huge inputs
9. **LC 198 / LC 213** House Robber — DP state can grow large

### Comparator subtraction tests
10. **LC 215** Kth Largest Element in an Array — careful with PriorityQueue comparator
11. **LC 347** Top K Frequent Elements — int[] (count, value) comparator
12. **LC 692** Top K Frequent Words — alphabetic tiebreak + count compare
13. **LC 451** Sort Characters by Frequency

### Binary search midpoint tests
14. **LC 704** Binary Search — write the safe midpoint
15. **LC 35** Search Insert Position
16. **LC 33** Search in Rotated Sorted Array
17. **LC 410** Split Array Largest Sum (binary search on answer space — values up to 10⁹)

### BST bounds tests
18. **LC 98** Validate Binary Search Tree — the canonical `long` bounds problem
19. **LC 530** Minimum Absolute Difference in BST

### Boxing / equality tests
20. **LC 387** First Unique Character — comparing Integer counts (use `.equals` or `int`)
21. **LC 138** Copy List with Random Pointer — IdentityHashMap or careful equality

> Master 1–4 first (the direct overflow tests). They force you to learn the fix patterns at the language level. Then 14–19 will start feeling natural.

---

## 🎨 Style Habits — Build These From Day 1

> Universal habits — apply across **every** DSA problem, not just trees or arrays.

---

### Habit 1 — Default to `long` for accumulators

If you're summing or multiplying things, declare the accumulator as `long`. Cast back to `int` only at the end if the return type forces it. The cost is one letter and 4 bytes. The benefit is no overflow surprises.

```java
long sum = 0;
for (int n : nums) {
    sum += n;
}
return (int) sum;       // only if the problem returns int
```

---

### Habit 2 — Never use `a - b` in a comparator

Use `Integer.compare(a, b)` or `Long.compare(a, b)`. Always.

```java
// ❌
Arrays.sort(arr, (a, b) -> a - b);

// ✅
Arrays.sort(arr, (a, b) -> Integer.compare(a, b));
```

---

### Habit 3 — Use the safe binary-search midpoint by reflex

```java
int mid = low + (high - low) / 2;
```

Even when test cases don't trigger overflow, write the safe form. It costs nothing extra and trains the reflex.

---

### Habit 4 — `getOrDefault`, not `get`, when arithmetic is involved

```java
// ❌ NPE on missing key
int x = count.get(c) + 1;

// ✅ defaults to 0 cleanly
int x = count.getOrDefault(c, 0) + 1;
```

---

### Habit 5 — `.equals()` (not `==`) on `Integer` objects

```java
Integer a = freq.get("apple");
Integer b = freq.get("banana");

// ❌ fails for values ≥ 128
if (a == b) { ... }

// ✅
if (a.equals(b)) { ... }
```

---

### Habit 6 — When constraints say "≤ 10⁹" with sums, use `long`

Read constraints first. If `n × max|value|` could exceed `2 × 10⁹`, the running sum overflows `int`. Don't gamble — declare `long`.

---

> **Quick recap of the 6 habits:** long accumulators → `Integer.compare` for comparators → safe midpoint → `getOrDefault` for arithmetic → `.equals()` on Integer → read constraints, default to long if sums could exceed `2 × 10⁹`.

---

## ⚠️ Bonus Gotchas (Less Common But Worth Knowing)

**Modulo of negatives differs from math.** In Java, `(-7) % 3 == -1`, not `2`. If you need math-style positive modulo, use `Math.floorMod`:

```java
int hash = Math.floorMod(value, mod);    // always returns non-negative result
```

---

**Integer division truncates, doesn't round.** `5 / 2 == 2`, not `2.5`, not `3`. To round to nearest, do `(a + b/2) / b` or use `Math.round`.

```java
int avg = (a + b) / 2;            // truncates — be aware
double avg = (a + b) / 2.0;       // proper division
```

---

**Casting `double` to `int` truncates toward zero, not toward negative infinity.** `(int) -1.5 == -1`, not `-2`. Use `Math.floor` if you need floor.

```java
int floored = (int) Math.floor(-1.5);    // -2
int truncated = (int) -1.5;              // -1
```

---

**`Long.MIN_VALUE` has the same `Math.abs` trap.** `Math.abs(Long.MIN_VALUE)` returns `Long.MIN_VALUE` (negative). There's no overflow-safe abs for long without using `BigInteger`.

---

**Char arithmetic can produce ints, not chars.** `'a' + 1` is `98` (int), not `'b'` (char). If you need a char back, cast: `(char) ('a' + 1)`. (See String reference for full coverage.)

---

## 🧾 TL;DR — One-Page Summary

- **Java `int` is 32 bits, range ≈ ±2.1 × 10⁹.** Beyond that, arithmetic **wraps silently** — no error, no warning, just a wrong value.
- **`long` is 64 bits, range ≈ ±9.2 × 10¹⁸.** Use for accumulators when sums or products may exceed 2 × 10⁹.
- **Three classic traps:** comparator subtraction (use `Integer.compare`), sum overflow (use `long`), binary search midpoint (use `low + (high - low) / 2`).
- **Boxing trap 1:** `int x = map.get(missing)` is **NPE**. Use `getOrDefault`.
- **Boxing trap 2:** `Integer a == Integer b` works for values ≤ 127, **fails for ≥ 128**. Use `.equals()` or compare primitives.
- **For BST bounds (LC 98), use `Long.MIN_VALUE` / `MAX_VALUE`** — node values can be `Integer.MIN_VALUE` and break int bounds.
- **Read problem constraints first.** If `n × max|value|` > `2 × 10⁹`, declare your accumulator as `long`.
- **Habit 1: when in doubt, use `long` for accumulators.** The 4 bytes are free; the wrong answer isn't.
