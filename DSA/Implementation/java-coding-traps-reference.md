# Java Coding Traps — Quick Reference

> **When to read:** 5-minute scan before any coding interview or practice session. Every trap that compiles but silently breaks your output — in one page.
>
> **Full explanations:** `DSA/Implementation/java-coding-traps.md` (the deep dive).

---

## ⚡ The 9 Families — Speed Round

### 🔹 1. Reference vs Value Equality

```java
// ❌ String ==
if (cmd == "tl") { ... }

// ✅ .equals()
if ("tl".equals(cmd)) { ... }
```

```java
// ❌ Integer == past 127
Integer a = 200, b = 200;
a == b;                          // false

// ✅ .intValue() or .equals()
a.intValue() == b.intValue();    // true
a.equals(b);                     // true
```

---

### 🔹 2. Increment Traps

```java
// ❌ x = x++ is a NO-OP
cdir = cdir++;       // cdir unchanged

// ✅ standalone or explicit
cdir++;
cdir = cdir + 1;
cdir = (cdir + 1) % 4;    // with wrap
```

```java
// ❌ increment inside expression
nums[i++] = nums[j++];

// ✅ separate statements
nums[i] = nums[j];
i++;
j++;
```

---

### 🔹 3. Overflow & Arithmetic

```java
// ❌ int sum overflows at ~2.1×10⁹
int sum = 0;

// ✅ use long when n > 10⁴ and values up to 10⁹
long sum = 0;
```

```java
// ❌ midpoint overflow
int mid = (lo + hi) / 2;

// ✅ safe midpoint
int mid = lo + (hi - lo) / 2;
```

```java
// ❌ Math.abs(Integer.MIN_VALUE) = Integer.MIN_VALUE
Math.abs(Integer.MIN_VALUE);     // still negative!

// ✅ widen first
Math.abs((long) Integer.MIN_VALUE);
```

```java
// ❌ Java modulo preserves sign of dividend
-7 % 3;                          // -1

// ✅ force positive
((x % m) + m) % m;              // always in [0, m-1]
```

---

### 🔹 4. Autoboxing & Unboxing

```java
// ❌ NPE on auto-unbox
int val = map.get("missing");    // null → NPE

// ✅ getOrDefault
int val = map.getOrDefault("missing", 0);
```

```java
// ❌ int[] memo can't distinguish 0 from "not computed"
int[] memo = new int[n];

// ✅ Integer[] — null = not computed
Integer[] memo = new Integer[n];
if (memo[i] != null) { return memo[i]; }
```

```java
// ❌ Integer in tight loop — boxes every +=
Integer sum = 0;

// ✅ primitive
int sum = 0;
```

---

### 🔹 5. String & Char

```java
// ❌ single-digit only
int n = cmd.charAt(1) - '0';         // "F12" → 1, not 12

// ✅ parseInt for multi-digit
int n = Integer.parseInt(cmd.substring(1));   // "F12" → 12
```

```java
// ❌ String += in loop is O(n²)
result += chars[i];

// ✅ StringBuilder
sb.append(chars[i]);
```

```java
// substring end is EXCLUSIVE
"hello".substring(1, 3);    // "el" (indices 1,2 — NOT 3)
```

```java
// char arithmetic produces int
char d = c + 1;             // ❌ compile error
char d = (char)(c + 1);     // ✅
```

---

### 🔹 6. Array & Collection

```java
// ❌ comparator overflow
Arrays.sort(arr, (a, b) -> a[0] - b[0]);

// ✅ safe
Arrays.sort(arr, (a, b) -> Integer.compare(a[0], b[0]));
```

```java
// ❌ removes by INDEX, not value
list.remove(2);

// ✅ removes by VALUE
list.remove(Integer.valueOf(2));
```

```java
// ❌ Arrays.sort(int[]) — not stable, O(n²) worst case
// ✅ Arrays.sort(Integer[]) — TimSort, stable, O(n log n) guaranteed
```

```java
// ❌ prints memory address
arr.toString();

// ✅ prints contents
Arrays.toString(arr);
```

---

### 🔹 7. Control Flow

```java
// ❌ multiple blocks execute (second if + else are independent of first if)
if (cmd.equals("tl")) { ... }
if (cmd.equals("tr")) { ... }
else { ... }                      // runs for "tl" too!

// ✅ else-if chain — exactly one branch executes
if (cmd.equals("tl")) { ... }
else if (cmd.equals("tr")) { ... }
else { ... }
```

```java
// ❌ wrap-around direction bug
cdir--;
if (cdir == -1) { cdir = 0; }    // should be 3!

// ✅ modular arithmetic
cdir = (cdir + 3) % 4;    // turn right
cdir = (cdir + 1) % 4;    // turn left
```

---

### 🔹 8. Scope & State

```java
// ❌ instance field not reset between LeetCode test cases
class Solution {
    int max = Integer.MIN_VALUE;
    public int solve(TreeNode root) { ... }
}

// ✅ reset at entry
public int solve(TreeNode root) {
    max = Integer.MIN_VALUE;
    // ...
}
```

```java
// ❌ variable declared inside block, used outside
if (cond) { int result = compute(); }
System.out.println(result);    // compile error

// ✅ declare outside
int result = 0;
if (cond) { result = compute(); }
```

---

### 🔹 9. Null Traps

```java
// ❌ NPE if s is null
s.equals("hello");

// ✅ literal first
"hello".equals(s);
```

```java
// ❌ fast could be null
while (fast.next != null) { ... }

// ✅ check both
while (fast != null && fast.next != null) { ... }
```

---

## ⚡ 15 Mechanical Rules — Memorize These

| # | Rule | ❌ | ✅ |
| --- | --- | --- | --- |
| 1 | `.equals()` for objects | `str == "hello"` | `str.equals("hello")` |
| 2 | Null-safe: literal LEFT | `s.equals("hi")` | `"hi".equals(s)` |
| 3 | Integer: `.intValue()` | `a == b` (Integer) | `a.intValue() == b.intValue()` |
| 4 | Never `x = x++` | `cdir = cdir++` | `cdir++` |
| 5 | Standalone increments | `arr[i++] = arr[j++]` | separate statements |
| 6 | `long` for big sums | `int sum` (n > 10⁴) | `long sum = 0` |
| 7 | Safe midpoint | `(lo + hi) / 2` | `lo + (hi - lo) / 2` |
| 8 | Positive modulo | `(x - 1) % 4` | `(x + 3) % 4` |
| 9 | Map.get safe | `int v = map.get(k)` | `map.getOrDefault(k, 0)` |
| 10 | Multi-digit parse | `charAt(1) - '0'` | `Integer.parseInt(s.substring(1))` |
| 11 | StringBuilder in loops | `result += c` | `sb.append(c)` |
| 12 | `else if` for dispatch | `if` then `if` else | `if / else if / else` |
| 13 | Safe comparator | `(a, b) -> a - b` | `Integer.compare(a, b)` |
| 14 | Remove by value | `list.remove(2)` | `list.remove(Integer.valueOf(2))` |
| 15 | Reset instance fields | forgot reset | `max = MIN_VALUE` at top |

---

## 🧭 "Output Is Wrong" Decision Tree

```
Output is wrong. Algorithm looks correct. What now?

    ├── Strings not matching?
    │   └── Check: == on strings → use .equals()
    │
    ├── Value stuck / not changing?
    │   └── Check: x = x++ → use x++ alone
    │
    ├── Negative or garbage numbers?
    │   └── Check: int overflow → use long
    │   └── Check: negative modulo → ((x % m) + m) % m
    │
    ├── NullPointerException?
    │   └── Check: map.get() auto-unbox → getOrDefault
    │   └── Check: linked list fast.next → fast != null && fast.next != null
    │   └── Check: s.equals() on null → "literal".equals(s)
    │
    ├── Wrong branch executing?
    │   └── Check: if-if-else → needs else-if chain
    │   └── Check: direction wrap → (cdir + 3) % 4
    │
    ├── Parsing wrong number?
    │   └── Check: charAt - '0' on multi-digit → parseInt(substring(...))
    │
    ├── HashMap frequency comparison fails for large freq?
    │   └── Check: Integer == past 127 → .intValue() == .intValue()
    │
    ├── TLE on large input?
    │   └── Check: String += in loop → StringBuilder
    │   └── Check: Integer in tight loop → use int primitive
    │
    └── Works on some tests, fails on others?
        └── Check: instance field not reset between test cases
        └── Check: int[] memo treats 0 as uncomputed → use Integer[]
```

---

## 🧾 The Mantra

> *"If it compiles and the output is wrong — check these 9 families before questioning your algorithm."*

1. **Reference equality** — `.equals()` not `==`
2. **Increment** — never `x = x++`
3. **Overflow** — `long` for sums, safe midpoint, positive mod
4. **Autoboxing** — `getOrDefault`, `int` in loops, `Integer[]` memo
5. **String/Char** — `parseInt` for multi-digit, `StringBuilder`, exclusive end
6. **Array/Collection** — `Integer.compare`, `Integer.valueOf` for remove
7. **Control flow** — `else if` chain, modular wrap-around
8. **Scope/State** — reset fields, declare outside blocks
9. **Null** — literal-first `.equals()`, check `fast != null` first

---

## 🧩 Speed Drill — 3 Minutes (Do Before Every Interview)

**Part 1 — Cover the ✅ column** in the 15 rules table above. For each rule number, write the safe version from memory. Uncover and check. Any you missed? That's the one that'll bite you today.

**Part 2 — Write these 7 lines from scratch** (no peeking):

1. Null-safe string comparison: `cmd` equals `"forward"`
2. Safe midpoint of `lo` and `hi`
3. Turn left from direction index 1 (of 4) with positive modulo
4. Get value from `HashMap<String, Integer>` into primitive `int`, default 0
5. Parse the number from `"F12"` into an `int`
6. Sort `int[][] intervals` by first element — safe comparator
7. Command dispatch skeleton: `"TL"` → turn left, `"TR"` → turn right, else → forward

**Scoring:** 7/7 = ready. 5-6 = scan the families you missed. Below 5 = re-read the deep dive `java-coding-traps.md`.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Full explanations + ASCII visuals | `DSA/Implementation/java-coding-traps.md` |
| Integer overflow deep dive | `DSA/DeepDive/integer-overflow-and-limits.md` |
| Simulation patterns (direction array, parsing) | `DSA/Implementation/simulation-patterns.md` |
| Morning interview cheatsheet | `DSA/Reference/interview-morning-cheatsheet.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Compact reference companion for `java-coding-traps.md`. 9 families in speed-round format, 15 mechanical rules, "output is wrong" decision tree. |
| May 2026 | **Speed drill added.** 3-minute pre-interview drill: cover-and-write for 15 rules + 7 write-from-memory exercises. |
