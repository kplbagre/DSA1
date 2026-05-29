# Java Coding Traps — The Bugs That Compile But Break Everything

> **What this file is:** Every Java trap that compiles cleanly, passes small tests, and silently produces wrong output under interview pressure. Organized by root cause so you internalize the *why*, not just the fix.
>
> **When to read:** Once top-to-bottom (takes ~30 min). Then skim the companion `java-coding-traps-reference.md` before every interview.
>
> **What this is NOT:** Code style / refactoring (see `DSA/Reference/code-style-for-dsa-reference.md`). This file is about **correctness** — code that's wrong, not code that's ugly.

---

## 🎯 Why This File Exists

> **Lesson learned the hard way (May 2026):** In a 30-minute DSA interview (car-on-grid simulation with toll), every single bug was a *Java trap*, not an algorithm mistake. `==` on strings (nothing matched), `cdir = cdir++` (direction never changed), missing `else if` (multiple blocks executed), single-digit parsing (`charAt(1) - '0'` instead of `parseInt`). The algorithm was understood. The code was broken. **Knowing the pattern means nothing if your Java betrays you.**

These traps share one property: **they don't throw exceptions.** They compile, they run, they produce output — it's just the *wrong* output. That's why they're deadly under time pressure. You think your algorithm is wrong, but it's the language biting you.

---

## 📖 Terminology

| Term | Meaning |
| --- | --- |
| **Reference equality** (`==`) | Compares memory addresses — "are these the exact same object in the heap?" |
| **Value equality** (`.equals()`) | Compares contents — "do these objects hold the same data?" |
| **Autoboxing** | Java silently converting `int` → `Integer` (wrapping a primitive in an object) |
| **Auto-unboxing** | Java silently converting `Integer` → `int` (unwrapping the object back to a primitive) |
| **Integer cache** | JVM pre-creates `Integer` objects for values -128 to 127 and reuses them — so `==` works by accident in that range |
| **Post-increment** (`x++`) | Returns the OLD value, THEN increments. The increment happens as a side effect, not in the returned value. |
| **Pre-increment** (`++x`) | Increments first, THEN returns the NEW value. |
| **Narrowing cast** | Converting a wider type to a narrower one (`long` → `int`, `double` → `int`) — silently truncates |
| **String pool / intern** | JVM keeps one copy of each string literal in a shared pool — `"hello" == "hello"` is `true` by accident, but `new String("hello") == "hello"` is `false` |

---

## 🪜 The 9 Trap Families

---

### Family 1: Reference vs Value Equality ⭐

**Root cause:** In Java, `==` on objects compares **memory addresses**, not contents. For primitives (`int`, `char`, `boolean`), `==` compares values. The confusion: sometimes `==` on objects *appears* to work (String interning, Integer cache) — then breaks on a different input.

#### Trap 1a: String `==` (the interview killer)

```java
String cmd = "tl";
if (cmd == "tl") {
    // ❌ MIGHT work in some JVMs (string interning), WILL fail for
    //    strings built at runtime (user input, substring, split result)
}
```

**Why it sometimes works:** Java interns string literals — `"tl"` in your source code is the same object as another `"tl"`. But strings from `Scanner.next()`, `String.split()`, `substring()`, or any runtime construction are NEW objects. `==` compares addresses → fails.

```java
// ✅ FIX — always .equals() for strings
if (cmd.equals("tl")) { ... }

// ✅ EVEN BETTER — null-safe (put the literal first)
if ("tl".equals(cmd)) { ... }

// ✅ CASE-INSENSITIVE
if (cmd.equalsIgnoreCase("TL")) { ... }
```

**Mechanical rule:** See `==` between two objects? Replace with `.equals()`. No exceptions for strings. Ever.

#### Trap 1b: Integer `==` past the cache boundary

```java
Integer a = 127;
Integer b = 127;
System.out.println(a == b);     // true ✅ (cached — same object)

Integer c = 128;
Integer d = 128;
System.out.println(c == d);     // false ❌ (not cached — different objects!)
```

### 🎨 Visual — The Integer Cache Boundary

```
        Integer cache: JVM pre-creates objects for [-128 .. 127]

  Value:  ... -129  -128  -127  ...  0  ...  126  127  128  129 ...
                 │    ↓    ↓        ↓       ↓    ↓    │
  Cached?:       NO  YES  YES     YES     YES  YES   NO   NO
                 │                                     │
                 └── == fails here                     └── == fails here
                     (new object each time)                (new object each time)

  Integer a = 127;  Integer b = 127;  → same cached object  → a == b is TRUE
  Integer c = 128;  Integer d = 128;  → two new objects     → c == d is FALSE
  
  KEY INVARIANT:
     == on Integer works ONLY for -128..127 by accident.
     Outside that range, == compares addresses and fails.
     ALWAYS use .equals() or .intValue() == .intValue().
```

```java
// ✅ FIX — compare primitive values, not object references
if (a.intValue() == b.intValue()) { ... }
// OR
if (a.equals(b)) { ... }
```

**Where this bites in DSA:** Sliding window with `HashMap<Character, Integer>` — comparing window freq to target freq:

```java
// ❌ Fails for frequencies > 127
if (window.get(c) == need.get(c)) { formed++; }

// ✅ Safe
if (window.get(c).intValue() == need.get(c).intValue()) { formed++; }
```

> 🧩 **Drill — do this NOW before reading further:**
> On a blank notepad (no peeking), write:
> 1. A null-safe string comparison: check if variable `cmd` equals `"forward"`
> 2. A safe `Integer` comparison: check if `freq.get(a)` equals `freq.get(b)` (both return `Integer`)
>
> Then compare with the ✅ versions above. If you wrote `==` for either one, re-read this section.

---

### Family 2: Increment / Decrement Traps ⭐

**Root cause:** `x++` is an EXPRESSION that returns the OLD value. `++x` returns the NEW value. When you assign the result back to the same variable, madness ensues.

#### Trap 2a: `x = x++` — the value never changes

```java
int cdir = 0;
cdir = cdir++;      // cdir is STILL 0 ❌
```

**What happens step-by-step:**
1. `cdir++` evaluates — returns `0` (the old value), then increments `cdir` to `1` as a side effect.
2. The assignment `cdir = ...` writes the returned value (`0`) back to `cdir`.
3. Net effect: `cdir` is overwritten back to `0`. The increment is lost.

### 🎨 Visual — Why `x = x++` Is a No-Op

```
    x = x++

    Step 1: evaluate x++ 
            → returns OLD value (0)
            → side-effect: x becomes 1 (but this is about to be overwritten)
    
    Step 2: assign returned value (0) to x
            → x = 0
    
    Result: x is still 0. The increment was overwritten by the assignment.

    ┌───────────┐     ┌──────────────┐     ┌───────────┐
    │ x = 0     │ ──► │ x++ returns 0│ ──► │ x = 0     │
    │ (before)  │     │ x becomes 1  │     │ (after!)   │
    └───────────┘     │ (side effect)│     └───────────┘
                      └──────────────┘
                            ↑
                      assignment overwrites
                      the side-effect
```

```java
// ✅ FIX — just use the increment, don't assign it back
cdir++;                    // cdir is now 1
// OR
cdir = cdir + 1;           // explicit, no ambiguity
// OR
cdir = (cdir + 1) % 4;    // with wrap-around (direction problems)
```

**Mechanical rule:** NEVER write `x = x++` or `x = x--`. Use `x++` alone, or `x = x + 1`.

#### Trap 2b: `x = x--` — same trap, decrement version

```java
int cdir = 2;
cdir = cdir--;      // cdir is STILL 2 ❌

// ✅ FIX
cdir--;
// OR for direction with wrap-around:
cdir = (cdir + 3) % 4;    // turn right = (cdir - 1 + 4) % 4 = (cdir + 3) % 4
```

#### Trap 2c: Increment inside a complex expression

```java
// ❌ Confusing — which value does nums[i] use?
nums[i++] = nums[j++];
// i and j are incremented AFTER the array access? or before?
// Answer: AFTER (post-increment). But under pressure, you'll second-guess yourself.

// ✅ FIX — separate the increment from the expression
nums[i] = nums[j];
i++;
j++;
```

**Mechanical rule:** Use `x++` only as a standalone statement. Never embed it inside assignments, array accesses, or method arguments.

> 🧩 **Drill — do this NOW before reading further:**
> Fix these broken lines without looking up:
> 1. `cdir = cdir++;` — make direction actually increment
> 2. `cdir = cdir--;` — make direction decrement with wrap-around (4 directions)
> 3. `nums[i++] = nums[j++];` — rewrite without embedded increments
>
> Then compare with the ✅ versions above.

---

### Family 3: Overflow & Arithmetic

**Root cause:** Java `int` is 32-bit signed (max ~2.1 billion). Java does NOT warn or throw on overflow — it silently wraps around.

#### Trap 3a: Sum overflow

```java
int sum = 0;
for (int x : nums) {
    sum += x;   // ❌ if nums has 10^5 elements each ≈ 10^9, sum overflows
}

// ✅ FIX — use long
long sum = 0;
for (int x : nums) {
    sum += x;
}
```

**When it bites:** Any problem with `n ≤ 10^5` and values up to `10^9`. `10^5 × 10^9 = 10^14` → far beyond `int` max (~2 × 10^9).

#### Trap 3b: Midpoint overflow

```java
int mid = (lo + hi) / 2;           // ❌ lo + hi can overflow
int mid = lo + (hi - lo) / 2;      // ✅ safe — subtraction first
```

**Why does `(lo + hi)` overflow?** Both `lo` and `hi` are valid `int` values individually, but their SUM can exceed `Integer.MAX_VALUE` (2,147,483,647). When that happens, Java doesn't throw an error — it silently wraps around to a negative number, and dividing a negative by 2 gives you a negative midpoint.

**Concrete example — binary search on a large array:**

```java
// Array has 2 billion elements. You're searching the right half.
int lo = 1_500_000_000;    // 1.5 billion — valid int ✅
int hi = 2_000_000_000;    // 2.0 billion — valid int ✅

// ❌ The broken way:
int sum = lo + hi;          // 3,500,000,000 — exceeds int max (2,147,483,647)
                            // Java wraps: actual value = -794,967,296 (garbage!)
int mid = sum / 2;          // -397,483,648 — negative index! 💥

// ✅ The safe way:
int diff = hi - lo;         // 500,000,000 — safe, always fits in int
int half = diff / 2;        // 250,000,000
int mid = lo + half;        // 1,750,000,000 — correct midpoint ✅
```

### 🎨 Visual — Why lo + (hi - lo) / 2 Works

```
  The int number line:

  -2.1B          0         +2.1B (MAX_VALUE)
  ──┼────────────┼────────────┼──
                       lo        hi
                       │         │
                       1.5B      2.0B

  ❌ (lo + hi) / 2:
     lo + hi = 3.5B → OVERFLOWS past +2.1B → wraps to -0.8B
     mid = -0.8B / 2 = -0.4B   ← garbage negative index

  ✅ lo + (hi - lo) / 2:
     hi - lo = 0.5B             ← safe (smaller number)
     0.5B / 2 = 0.25B           ← safe
     lo + 0.25B = 1.75B         ← correct midpoint ✅

  What's happening algebraically:
     lo + (hi - lo) / 2
     = lo + hi/2 - lo/2
     = lo/2 + hi/2
     = (lo + hi) / 2            ← same result, but no intermediate overflow

  KEY INSIGHT:
     Both formulas compute the same answer mathematically.
     The difference is the INTERMEDIATE value:
       (lo + hi)      → can overflow (sum of two large numbers)
       (hi - lo)      → always safe (difference is smaller than either)
```

**When does this actually bite you?** Binary search on arrays with `n > 10^9` elements, or binary search on VALUE RANGES (e.g., "binary search on answer" problems where `lo = 0` and `hi = Integer.MAX_VALUE`).

**Mechanical rule:** ALWAYS write `lo + (hi - lo) / 2`. Never `(lo + hi) / 2`. It costs nothing and prevents a subtle bug that only shows up on large inputs.

#### Trap 3c: `Math.abs(Integer.MIN_VALUE)` = `Integer.MIN_VALUE`

```java
int x = Math.abs(Integer.MIN_VALUE);
// x = -2147483648 ❌ (same value! two's complement has no positive counterpart)

// ✅ FIX — widen to long first
long x = Math.abs((long) Integer.MIN_VALUE);
// x = 2147483648 ✅
```

#### Trap 3d: Integer division truncates toward zero

```java
System.out.println(7 / 2);      // 3 (not 3.5)
System.out.println(-7 / 2);     // -3 (truncates toward zero, not toward -∞)
```

#### Trap 3e: Modulo with negatives

```java
System.out.println(-7 % 3);     // -1 ❌ (not 2 like Python)
// Java modulo preserves the sign of the dividend

// ✅ FIX — force positive remainder
int mod = ((x % m) + m) % m;    // always in [0, m-1]
```

**Where this bites:** Prefix sum problems with modulo (LC 974 — Subarray Sums Divisible by K).

**Cross-reference:** Full treatment in **`DSA/DeepDive/integer-overflow-and-limits.md`**.

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory (no peeking):
> 1. Safe midpoint of `lo` and `hi` — one expression
> 2. Positive-modulo expression: make `(-7) % 3` give `2` instead of `-1`
> 3. You have `n ≤ 10^5` elements, values up to `10^9` — declare the sum variable with the correct type
>
> Then compare with the ✅ versions above.

---

### Family 4: Autoboxing & Unboxing

**Root cause:** Java silently converts between `int` ↔ `Integer`. This invisible conversion causes NPEs, performance traps, and equality bugs.

#### Trap 4a: Auto-unbox NPE on Map.get()

```java
Map<String, Integer> map = new HashMap<>();
int count = map.get("missing");     // ❌ NPE — map.get returns null, unboxing null throws

// ✅ FIX — use getOrDefault
int count = map.getOrDefault("missing", 0);

// OR check for null first
Integer val = map.get("missing");
if (val != null) { ... }
```

**Mechanical rule:** Every `map.get()` assigned to a primitive `int` is a potential NPE. Use `getOrDefault` or assign to `Integer` first.

#### Trap 4b: Autoboxing in a tight loop (performance)

```java
// ❌ Integer sum boxes on EVERY += operation
Integer sum = 0;
for (int v : nums) {
    sum += v;       // unbox sum → add v → box result → assign back
}

// ✅ FIX — use primitive
int sum = 0;
for (int v : nums) {
    sum += v;
}
```

**Where this bites:** TLE on problems with `n = 10^5+` if the hot loop uses `Integer` instead of `int`.

#### Trap 4c: `Integer[]` vs `int[]` for memoization

```java
// ❌ Can't distinguish "not computed" from "computed value is 0"
int[] memo = new int[n];
if (memo[i] != 0) { return memo[i]; }   // skips legitimate 0 answers

// ✅ FIX — use Integer[], null = not computed
Integer[] memo = new Integer[n];
if (memo[i] != null) { return memo[i]; }
```

**Cross-reference:** Covered in **`DSA/DeepDive/dp-fundamentals.md`** §21.1.

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory:
> 1. Safe way to get a value from `HashMap<String, Integer>` (key might be absent) into a primitive `int`
> 2. Declare a memoization array of size `n` where `0` is a valid computed answer — which type and how do you check "not computed yet"?
>
> Then compare with the ✅ versions above.

---

### Family 5: String & Char Traps

**Root cause:** Strings are objects (heap-allocated, immutable). Chars are primitives (16-bit unsigned int). The line between them causes type errors, off-by-one, and performance traps.

#### Trap 5a: Single-digit parsing only with `charAt - '0'`

```java
String cmd = "F12";
int n = cmd.charAt(1) - '0';    // ❌ n = 1, not 12 — only reads one char

// ✅ FIX — use parseInt on the substring
int n = Integer.parseInt(cmd.substring(1));    // n = 12
```

**Mechanical rule:** `charAt(i) - '0'` is for SINGLE digit extraction only. For multi-digit numbers, always `Integer.parseInt(s.substring(...))`.

#### Trap 5b: `String +=` in a loop is O(n²)

```java
// ❌ Each += creates a new String object — O(n²) total
String result = "";
for (int i = 0; i < n; i++) {
    result += chars[i];
}

// ✅ FIX — StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < n; i++) {
    sb.append(chars[i]);
}
return sb.toString();
```

#### Trap 5c: `substring(i, j)` — j is exclusive

```java
"hello".substring(1, 3);     // "el" (indices 1 and 2, NOT 3)
"hello".substring(1);        // "ello" (from index 1 to end)
```

**Mechanical rule:** `substring(start, end)` = `[start, end)`. Same convention as `Arrays.copyOfRange`.

#### Trap 5d: `char` is numeric — arithmetic produces `int`

```java
char c = 'a';
char d = c + 1;        // ❌ compile error — c + 1 is int, not char
char d = (char)(c + 1); // ✅ 'b'

// This is why freq[c - 'a']++ works — c - 'a' is an int expression
```

#### Trap 5e: `toCharArray()` creates a COPY

```java
String s = "hello";
char[] arr = s.toCharArray();
arr[0] = 'H';
System.out.println(s);    // still "hello" — s is unchanged
// String is immutable; toCharArray gives you a copy
```

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory:
> 1. Parse the number from command string `"F12"` into an `int` — one line
> 2. Build a result string from a `char[]` array of length `n` without O(n²) — write the loop
> 3. What does `"hello".substring(1, 3)` return? Answer without running it.
>
> Then compare with the ✅ versions above.

---

### Family 6: Array & Collection Traps

#### Trap 6a: `Arrays.asList(int[])` gives `List<int[]>`, not `List<Integer>`

```java
int[] nums = {1, 2, 3};
List<Integer> list = Arrays.asList(nums);     // ❌ compile error — wrong type

// ✅ FIX
List<Integer> list = Arrays.stream(nums).boxed().collect(Collectors.toList());

// OR manual
List<Integer> list = new ArrayList<>();
for (int n : nums) { list.add(n); }
```

**Why:** Generics don't work with primitives. `Arrays.asList(int[])` treats the entire `int[]` as ONE element of type `int[]`.

#### Trap 6b: `list.remove(int)` removes by INDEX

```java
List<Integer> list = new ArrayList<>(List.of(10, 20, 30));
list.remove(1);                    // removes INDEX 1 → removes 20, list = [10, 30]
list.remove(Integer.valueOf(20));  // removes VALUE 20 ✅
```

**Mechanical rule:** When removing by value from `List<Integer>`, wrap in `Integer.valueOf(...)`.

#### Trap 6c: `Arrays.sort(int[])` is NOT stable, CAN be O(n²)

```java
int[] arr = {...};
Arrays.sort(arr);          // dual-pivot quicksort — NOT stable, O(n²) worst case

Integer[] boxed = {...};
Arrays.sort(boxed);        // TimSort — stable, O(n log n) guaranteed
```

**Where this bites:** When the problem needs stable sort (preserve relative order of equal elements), or when adversarial input triggers quicksort's worst case. LC competitive submissions sometimes TLE on `Arrays.sort(int[])`.

**Common workaround:**

```java
// Shuffle before sorting to avoid worst-case quicksort
Collections.shuffle(Arrays.asList(boxed));
Arrays.sort(boxed);
```

#### Trap 6d: `arr.toString()` prints memory address

```java
int[] arr = {1, 2, 3};
System.out.println(arr.toString());          // "[I@1540e19d" ❌
System.out.println(Arrays.toString(arr));    // "[1, 2, 3]" ✅
```

#### Trap 6e: Sorting a 2D array — comparator pitfalls

```java
// ❌ Subtraction overflow for extreme values
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

// ✅ Safe comparator
Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));

// ✅ Even cleaner
Arrays.sort(intervals, Comparator.comparingInt(a -> a[0]));
```

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory:
> 1. Sort `int[][] intervals` by first element — safe comparator, no overflow risk
> 2. Remove the value `20` (not index 20) from a `List<Integer>`
> 3. Print the contents of `int[] arr` to console (not the memory address)
>
> Then compare with the ✅ versions above.

---

### Family 7: Control Flow Traps ⭐

**Root cause:** `if` / `else if` / `else` in Java are independent statements unless explicitly chained. Missing `else` means multiple blocks can execute for the same input.

#### Trap 7a: Missing `else if` — multiple blocks execute

```java
// ❌ If cmd is "tl", BOTH the first block AND the else block execute
if (cmd.equals("tl")) {
    // handle turn left
}
if (cmd.equals("tr")) {
    // handle turn right
}
else {
    // handle forward — THIS RUNS FOR "tl" TOO because the second if is false
}
```

### 🎨 Visual — Why Missing else-if Breaks Command Dispatch

```
cmd = "tl"

    if (cmd.equals("tl"))  ──► TRUE  → executes TL block ✅
    │
    if (cmd.equals("tr"))  ──► FALSE
    │    └── else           ──► executes! (because the SECOND if was false)
                                THIS IS THE BUG ❌

With else-if chain:

    if (cmd.equals("tl"))       ──► TRUE → executes TL block ✅
    else if (cmd.equals("tr"))  ──► SKIPPED (first branch already matched)
    else                        ──► SKIPPED

KEY INVARIANT:
   Independent if-if-else is THREE separate decisions.
   if-else if-else is ONE decision with branches.
   For command dispatch, you ALWAYS want the latter.
```

```java
// ✅ FIX — use else-if chain
if (cmd.equals("tl")) {
    // handle turn left
} else if (cmd.equals("tr")) {
    // handle turn right
} else {
    // handle forward
}
```

**Mechanical rule:** When processing commands / modes / states where exactly ONE branch should execute, ALWAYS use `if / else if / else`. Never sequential `if` blocks.

#### Trap 7b: Wrap-around direction off-by-one

```java
// ❌ Decrement wraps to 0, not 3
cdir--;
if (cdir == -1) {
    cdir = 0;       // should be 3!
}

// ✅ FIX — modular arithmetic, one line, never wrong
cdir = (cdir + 3) % 4;    // turn right (equivalent to cdir - 1 with wrap)
cdir = (cdir + 1) % 4;    // turn left
```

**Why `(cdir + 3) % 4` instead of `(cdir - 1) % 4`:** Java's `%` on negatives returns negative values. `(-1) % 4 = -1`, not `3`. Adding 3 instead of subtracting 1 avoids the negative entirely.

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory:
> 1. Command dispatch for three commands: `"TL"`, `"TR"`, and anything else (forward) — write the full `if / else if / else` skeleton with string comparison
> 2. Turn LEFT from direction index 2 (of 4 directions) using one modular arithmetic expression
> 3. Turn RIGHT from direction index 0 (of 4 directions) using one modular arithmetic expression
>
> Then compare with the ✅ versions above. If you wrote `(cdir - 1) % 4` for left turn, re-read why that's wrong.

---

### Family 8: Scope & State Traps

#### Trap 8a: Instance field not reset between LeetCode test cases

```java
class Solution {
    private int max = Integer.MIN_VALUE;     // ❌ carries over from previous test

    public int solve(TreeNode root) {
        helper(root);
        return max;
    }
}

// ✅ FIX — reset at the top of the entry method
public int solve(TreeNode root) {
    max = Integer.MIN_VALUE;    // reset every call
    helper(root);
    return max;
}
```

#### Trap 8b: Variable declared inside loop vs outside

```java
// Inside loop — resets each iteration (usually what you want for per-iteration state)
for (int i = 0; i < n; i++) {
    int count = 0;     // fresh each iteration
    // ...
}

// Outside loop — accumulates across iterations (sometimes intended, sometimes a bug)
int count = 0;
for (int i = 0; i < n; i++) {
    count += something;    // accumulates — make sure you want this
}
```

#### Trap 8c: Declaring a variable inside a block, using it outside

```java
if (condition) {
    int result = compute();
}
System.out.println(result);    // ❌ compile error — result is out of scope

// ✅ FIX — declare outside the block
int result = 0;
if (condition) {
    result = compute();
}
System.out.println(result);
```

> 🧩 **Drill — do this NOW before reading further:**
> Spot the bug in this LeetCode solution — what happens when the judge calls `solve()` twice?
> ```java
> class Solution {
>     private int max = Integer.MIN_VALUE;
>     public int solve(TreeNode root) {
>         helper(root);
>         return max;
>     }
> }
> ```
> Write the one-line fix. Then compare with the ✅ version above.

---

### Family 9: Null Traps

#### Trap 9a: NullPointerException on auto-unbox

Already covered in Family 4 (Trap 4a). The most common form:

```java
int val = map.get(key);         // ❌ NPE if key absent
int val = map.getOrDefault(key, 0);  // ✅
```

#### Trap 9b: Null-safe string comparison

```java
String s = null;
s.equals("hello");         // ❌ NPE
"hello".equals(s);         // ✅ false (no NPE — calling equals on the literal)
```

**Mechanical rule:** Put the LITERAL or the KNOWN-NOT-NULL value on the LEFT of `.equals()`.

#### Trap 9c: Linked list null check order

```java
// ❌ NPE when fast is null
while (fast.next != null) {
    fast = fast.next.next;    // fast could be null after this!
}

// ✅ Check fast first, then fast.next
while (fast != null && fast.next != null) {
    fast = fast.next.next;
}
```

**Where this bites:** Every fast/slow pointer problem (LC 141, 142, 876, 234).

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory:
> 1. Null-safe comparison: check if variable `s` equals `"hello"` — `s` might be null
> 2. The correct while-loop condition for fast/slow pointer where fast moves 2 steps per iteration
>
> Then compare with the ✅ versions above.

---

## ⚠️ The 5 Traps That Bit Me in the Car-Toll Interview

Mapping the interview bugs back to the families above, so the lesson is concrete:

| My code | Family | Trap | Fix |
| --- | --- | --- | --- |
| `inp[i] == "tl"` | Family 1 | String `==` | `.equals()` or `.equalsIgnoreCase()` |
| `cdir = cdir++` | Family 2 | Post-increment assigned back | `cdir++` alone, or `cdir = (cdir + 1) % 4` |
| `if (cdir == -1) { cdir = 0; }` | Family 7 | Wrap-around off-by-one | `cdir = (cdir + 3) % 4` |
| `char c = s.charAt(1); int n = c - '0';` | Family 5 | Single-digit only | `Integer.parseInt(s.substring(1))` |
| `if (...)` then `if (...)` then `else` | Family 7 | Missing else-if chain | `if / else if / else` |

> **Lesson learned the hard way (May 2026):** Five bugs. Every single one is in this file. None were algorithmic. I understood the problem. I understood the approach. The language betrayed me because I hadn't drilled these traps into muscle memory. **Knowing the algorithm is 50% of the job. Knowing Java's traps is the other 50%.**

---

## ⚡ Quick Cheat Sheet — Mechanical Rules

Memorize these 15 rules. They're mechanical — apply them without thinking.

| # | Rule | ❌ | ✅ |
| --- | --- | --- | --- |
| 1 | Objects: `.equals()`, never `==` | `str == "hello"` | `str.equals("hello")` |
| 2 | Null-safe: literal on the LEFT | `s.equals("hello")` | `"hello".equals(s)` |
| 3 | Integer comparison: `.intValue()` | `a == b` (Integer) | `a.intValue() == b.intValue()` |
| 4 | Never `x = x++` | `cdir = cdir++` | `cdir++` |
| 5 | Standalone increments only | `arr[i++] = arr[j++]` | separate statements |
| 6 | Use `long` for sums when n > 10^4 | `int sum = 0` | `long sum = 0` |
| 7 | Safe midpoint | `(lo + hi) / 2` | `lo + (hi - lo) / 2` |
| 8 | Positive modulo | `(x - 1) % 4` | `(x + 3) % 4` |
| 9 | Map.get → `getOrDefault` | `int v = map.get(k)` | `map.getOrDefault(k, 0)` |
| 10 | Multi-digit parse | `s.charAt(1) - '0'` | `Integer.parseInt(s.substring(1))` |
| 11 | StringBuilder in loops | `result += c` | `sb.append(c)` |
| 12 | `else if` for command dispatch | `if` then `if` then `else` | `if / else if / else` |
| 13 | Comparator: no subtraction | `(a, b) -> a - b` | `Integer.compare(a, b)` |
| 14 | Remove by value from `List<Integer>` | `list.remove(2)` | `list.remove(Integer.valueOf(2))` |
| 15 | Reset instance fields | (none) | `max = Integer.MIN_VALUE` at top |

---

## 🗺️ Practice Drill

**The goal:** make these rules reflexive — you don't think about them, your fingers just type the safe version.

### Drill 1 — Write from dictation (10 min)

Have someone (or yourself) read these prompts aloud. Write the code from scratch. Check against the ✅ column.

1. "Compare two strings for equality"
2. "Increment direction index with wrap-around for 4 directions"
3. "Parse the number from a command string like F12"
4. "Get a value from a HashMap, default to 0 if missing"
5. "Sort a 2D array by first element"
6. "Calculate midpoint of two integers safely"
7. "Compute prefix sum — what type for the accumulator?"
8. "Dispatch three different commands: TL, TR, Fn"

### Drill 2 — Bug hunt (5 min)

Read your own old code (or a friend's). Find every instance of:
- `==` on a String or Integer
- `x = x++` or `x = x--`
- Sequential `if` blocks that should be `else if`
- `charAt(i) - '0'` on a potentially multi-digit number
- `int sum` where `long` is needed

---

## 🧾 TL;DR

**The 9 families:**

1. **Reference vs Value** — `.equals()` not `==` for objects. Always.
2. **Increment traps** — never `x = x++`. Use `x++` alone or `x = x + 1`.
3. **Overflow** — `long` for sums, `lo + (hi - lo) / 2` for midpoint, `((x % m) + m) % m` for positive mod.
4. **Autoboxing** — `getOrDefault` to avoid NPE, `int` not `Integer` in hot loops, `Integer[]` for memoization.
5. **String/Char** — `parseInt(substring(...))` for multi-digit, `StringBuilder` in loops, `substring` end is exclusive.
6. **Array/Collection** — `Integer.compare` for comparators, `Integer.valueOf` for remove-by-value, `Arrays.toString` for printing.
7. **Control flow** — `else if` for dispatch, `(x + 3) % 4` for wrap-around.
8. **Scope/State** — reset instance fields, know where your variable is declared.
9. **Null** — literal-first `.equals()`, check `fast != null && fast.next != null`.

**The mantra:** *"If it compiles and the output is wrong — check these 9 families before questioning your algorithm."*

---

## 🔗 Cross-References

| Topic | See File |
| --- | --- |
| Integer overflow deep dive | `DSA/DeepDive/integer-overflow-and-limits.md` |
| Code style / refactoring (clean but not correct) | `DSA/Reference/code-style-for-dsa-reference.md` |
| `Integer[]` vs `int[]` for DP memoization | `DSA/DeepDive/dp-fundamentals.md` §21.1 |
| Linked list null-check patterns | `DSA/Reference/linkedlist-reference.md` — Gotchas |
| HashMap `.intValue()` comparison | `DSA/Reference/hashmap-section-updated.md` — Gotchas |
| Simulation patterns (direction array, command parsing) | `DSA/Implementation/simulation-patterns.md` (companion) |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** 9 trap families covering reference equality, increment traps, overflow, autoboxing, string/char, array/collection, control flow, scope/state, null. Triggered by the car-toll interview where every bug was a Java trap, not an algorithm mistake. |
