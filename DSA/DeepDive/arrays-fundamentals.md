# Arrays — Fundamentals (Deep Dive)

> **Curriculum alignment:** this deep-dive mirrors **Striver's Arrays Playlist** (`take U forward` YouTube, 28 videos, V-1 through V-28). Watch his videos for video-first learning; use this doc for written reinforcement, our project's Java style, mental models, and bug callouts gathered from real practice. Section headers cite the exact `V-N` video.

> **Credit:** topic ordering, problem selection, and the canonical brute → better → optimal progressions come from Raj Vikramaditya (`take U forward` / Striver). Code is rewritten in our project style (one statement per line, always-braced blocks, Java conventions). The mental-model framing (*"index space vs value space"*), the named patterns, the gotcha callouts, and the medium-interview cutoff are this doc's contribution.

---

## 🎯 Why You're Reading This (The Goal)

Arrays are the single most-tested data structure in interviews — the "warm-up round" of every onsite. But the patterns are deceptively easy to confuse. By the end of this doc you should:

1. **Recognize the 14 core array patterns cold** — two pointers (converging / same-direction), sliding window (fixed / variable), prefix sum (raw / with HashMap), Kadane's, Dutch national flag, Moore's voting, cyclic sort, matrix tricks, interval merging, modified merge sort
2. **Know which of "subarray vs subsequence vs subset" the problem is asking for** — these three words look similar but pick entirely different algorithms
3. **Pattern-match in under 60 seconds:** keyword → pattern → template
4. **Avoid the silent bugs** — integer overflow on sums, `Integer.compare` vs subtraction, off-by-one in window bounds, modifying-while-iterating
5. **Know what to skip on a first pass** — modified merge sort (inversions, reverse pairs), gap-method merge, cycle-detection on duplicate-finding are 🔴 Senior+; defer them until medium-interview essentials are muscle memory

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

Every problem and algorithm is tagged so you can **climb tiers in order**.

| Tag | Meaning | Use it when |
| --- | --- | --- |
| ✅ | **Try now** — covered by what you've read so far | You're learning a new section |
| 🟡 | **Try after** — needs concepts from later sections | You've finished those later sections |
| 🔴 | **Reference only / Senior+** | Don't attempt cold; come back when prerequisites are solid |

> **Lesson learned the hard way (May 2026):** Kapil burned an hour on LC 124 (in the trees doc) because he attempted a 🔴 cold without the prerequisite ladder. **Don't repeat this for arrays.** Modified merge sort (Count Inversions, LC 493 Reverse Pairs) looks innocent in the playlist — it's not. Treat Tier 5 as bedtime reading until Tiers 1–4 are reflex.

---

## 📖 What Is an Array? [Striver V-1]

An **array** is a **contiguous block of memory** holding fixed-size elements of the same type, addressable by an integer **index** starting at 0.

### Memory layout

```
indices:   0     1     2     3     4     5
          ┌────┬────┬────┬────┬────┬────┐
values:   │  3 │  1 │  4 │  1 │  5 │  9 │
          └────┴────┴────┴────┴────┴────┘
addr:    a+0  a+4  a+8  a+12 a+16 a+20   (each int = 4 bytes)
```

Because elements live at predictable offsets, `arr[i]` is **O(1) random access** — the CPU computes `base_address + i * element_size` and reads. That's the entire reason arrays are the foundational data structure.

### Java specifics

| Feature | Primitive array (`int[]`) | Boxed list (`ArrayList<Integer>`) |
| --- | --- | --- |
| Memory layout | Contiguous, cache-friendly | Array of pointers to `Integer` objects |
| Size | **Fixed** at creation | **Dynamic** (auto-grows) |
| Random access | `arr[i]` — O(1) | `list.get(i)` — O(1) |
| Insert at end | N/A (fixed size) | `list.add(x)` — amortized O(1) |
| Insert at middle | N/A | `list.add(i, x)` — O(n) |
| Null elements | Cannot store null | Can store null |
| Default initial values | `0` (int), `false` (boolean), `null` (Object) | None — list is empty |
| Length | `arr.length` (field) | `list.size()` (method) |
| Sort | `Arrays.sort(arr)` — dual-pivot quicksort, O(n log n) | `Collections.sort(list)` — Timsort |

> **Rule of thumb for interviews:** if the problem says "array of integers", use `int[]`. If it says "list of integers" or you need to grow it, use `ArrayList<Integer>`. Mixing them costs autoboxing overhead — see **`DSA/DeepDive/integer-overflow-and-limits.md`** for boxing pitfalls.

### 2D arrays

```java
int[][] matrix = new int[rows][cols];
```

Internally this is **an array of arrays** — each row is a separately allocated 1D array. Rows can theoretically have different lengths (a "jagged array"), though in interview problems they always have the same column count.

```
matrix:   ┌─────┐    ┌────┬────┬────┐
          │ ref │───→│  1 │  2 │  3 │   row 0
          ├─────┤    └────┴────┴────┘
          │ ref │───→┌────┬────┬────┐
          ├─────┤    │  4 │  5 │  6 │   row 1
          │ ref │───→└────┴────┴────┘
          └─────┘    ┌────┬────┬────┐
                    │  7 │  8 │  9 │   row 2
                    └────┴────┴────┘
```

This is why `matrix.length` gives **row count** and `matrix[0].length` gives **column count**.

---

## 📖 Terminology (Memorize These)

| Term | Meaning |
| --- | --- |
| **Element** | A single value at some index |
| **Index** | The position (0-based in Java) |
| **Length** | Total number of elements; `arr.length` |
| **Subarray** | A **contiguous** slice: `arr[i..j]`. Order preserved, no skipping. |
| **Subsequence** | A selection in **original order** but indices may **skip**. Not contiguous. |
| **Subset** | Any selection; order doesn't matter. (Subset of `[1,2,3]` includes `{2}`, `{1,3}`, `{}`.) |
| **Prefix** | `arr[0..i]` — everything from the start up to some point |
| **Suffix** | `arr[i..n-1]` — everything from some point to the end |
| **Window** | A range `[l, r]` that slides through the array (used in sliding window problems) |
| **In-place** | An algorithm that mutates the input without allocating a new array (O(1) extra space) |
| **Stable sort** | Equal elements keep their original relative order |
| **Pivot** | The boundary element in a partition (Dutch flag, quicksort) |
| **Partition** | Reorder so all elements satisfying property X are on one side |
| **Boundary** | The dividing index between a "processed" region and "unprocessed" region |

### ⭐ The most important terminology callout

> **Subarray vs Subsequence vs Subset.** Get this wrong and you'll pick the wrong algorithm.

| Term | Order matters? | Contiguous? | Example from `[1, 2, 3, 4]` |
| --- | --- | --- | --- |
| **Subarray** | Yes | **Yes** | `[2, 3]` ✅ · `[1, 3]` ❌ (skipped 2) |
| **Subsequence** | Yes | No | `[1, 3]` ✅ · `[3, 1]` ❌ (out of order) |
| **Subset** | No | No | `{1, 3}` = `{3, 1}` ✅ |

| If the problem says... | Pattern family |
| --- | --- |
| "Find subarray with sum K" | Sliding window (positive nums) **or** Prefix sum + HashMap (any sign) |
| "Longest subsequence with property X" | DP, usually 2D |
| "All subsets" | Backtracking (see `DSA/DeepDive/backtracking-fundamentals.md`) |

This deep dive focuses on **subarrays** — that's what Striver's playlist covers. Subsequence and subset live in the DP and Backtracking docs.

---

## 🛠️ Java Array Skeleton & Idioms

The 10 idioms you'll use in 90%+ of array problems.

### Creation

```java
int[] a = new int[5];                  // zeros: [0,0,0,0,0]
int[] b = new int[]{1, 2, 3, 4, 5};    // literal
int[] c = {1, 2, 3};                   // shorthand (only at declaration)
int[][] grid = new int[3][4];          // 3 rows × 4 cols, all zeros
```

### Read / write

```java
int n = nums.length;                   // length is a FIELD, no parens
int first = nums[0];
nums[i] = nums[i] + 1;
```

### Copy / slice

```java
int[] copy = Arrays.copyOf(arr, arr.length);
int[] slice = Arrays.copyOfRange(arr, 2, 5);   // indices [2, 5) — end exclusive
```

### Fill

```java
int[] dp = new int[n];
Arrays.fill(dp, -1);                   // useful for memo tables
```

### Sort

```java
Arrays.sort(arr);                                  // primitive — O(n log n) dual-pivot quicksort
Arrays.sort(boxed, (a, b) -> Integer.compare(a, b));   // boxed Integer[] — Timsort
Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));   // 2D array by first column
```

> **Gotcha — never use subtraction in comparators.** `(a, b) -> a - b` overflows for `Integer.MIN_VALUE`. Always `Integer.compare(a, b)`. Cross-ref: **`DSA/DeepDive/integer-overflow-and-limits.md`**.

### Print / debug

```java
System.out.println(Arrays.toString(arr));         // 1D
System.out.println(Arrays.deepToString(grid));    // 2D
```

> **Don't** use `arr.toString()` — that prints `[I@1234abcd` (the JVM hash). Always wrap in `Arrays.toString(...)`.

### ArrayList essentials

```java
List<Integer> list = new ArrayList<>();
list.add(5);                           // append
list.add(0, 99);                       // insert at index — O(n)
int v = list.get(2);                   // read
list.set(2, 7);                        // overwrite
list.remove(0);                        // remove by INDEX — O(n)
list.remove(Integer.valueOf(7));       // remove by VALUE — O(n), watch the boxing
int size = list.size();
list.clear();
```

> **Gotcha:** `list.remove(7)` removes **index** 7 (treats 7 as int). `list.remove(Integer.valueOf(7))` removes the **value** 7. This bites everyone once.

### Convert between `int[]` and `List<Integer>`

```java
// int[] → List<Integer>
int[] arr = {1, 2, 3};
List<Integer> list = new ArrayList<>();
for (int x : arr) {
    list.add(x);
}

// List<Integer> → int[]
int[] back = new int[list.size()];
for (int i = 0; i < list.size(); i++) {
    back[i] = list.get(i);
}
```

Streams (`Arrays.stream(arr).boxed().collect(Collectors.toList())`) work too but are slower and harder to read in interviews. Stick to loops.

---

## 🧠 Mental Model — Arrays Have Two Spaces: Index and Value

> **The single biggest unlock for array problems:** an array is two interlocking spaces — the **index space** (positions `0..n-1`) and the **value space** (the data). Most clever array tricks come from **using one space to encode information about the other**.

This sounds abstract. Let's see it in three concrete tricks.

### Trick 1: Cyclic Sort — put value `v` at index `v - 1`

Problem: given `nums` containing distinct integers in `[1, n]` exactly once, sort it in O(n) time and O(1) space.

**Idea:** the value tells you where it belongs. Walk through the array; if `nums[i]` isn't at its correct position, swap it there. Repeat until everyone is home.

```
Before: [3, 1, 5, 4, 2]
         ↓ swap nums[0]=3 to index 2
        [5, 1, 3, 4, 2]
         ↓ swap nums[0]=5 to index 4
        [2, 1, 3, 4, 5]
         ↓ swap nums[0]=2 to index 1
        [1, 2, 3, 4, 5]   done
```

The **value space** (`1..n`) maps perfectly onto the **index space** (`0..n-1`). Same idea solves "find missing", "find duplicate", "first missing positive" — they're all the same trick wearing different hats.

### Trick 2: Marking by negation — encode "visited" in the sign bit

Problem: find all duplicates in an array of `[1, n]`.

**Idea:** for each value `v = nums[i]`, go to index `v - 1` and **flip its sign to negative**. Next time you see a negative there, you know `v` was visited before.

```
nums = [4, 3, 2, 7, 8, 2, 3, 1]
         ↓
After processing nums[0]=4: nums[3] becomes -7 (mark "4 visited")
After processing nums[1]=3: nums[2] becomes -2 (mark "3 visited")
...
At nums[5]=2 we look at nums[1] = -3 (already negative!) → 2 is a duplicate
```

We **encoded boolean state inside the sign bit of the value**. That's the index → value channel.

### Trick 3: Set Matrix Zeros in O(1) space — use first row/col as metadata

Problem: if any cell is 0, zero out its entire row and column. Do it without an extra `O(m + n)` flag array.

**Idea:** use the first row and first column themselves as the flag arrays. `matrix[0][j] = 0` means "column `j` needs to be zeroed". `matrix[i][0] = 0` means "row `i` needs to be zeroed". Two extra booleans handle the row 0 and column 0 themselves.

The "metadata" we'd normally store in a separate array is **encoded inside specific positions of the array itself**.

### The universal skeleton for array problems

```java
// Step 1 — input validation
if (nums == null || nums.length == 0) {
    return defaultAnswer;
}

// Step 2 — set up state (pointers, accumulator, map)
int n = nums.length;
// ...

// Step 3 — iterate (single pass, two-pointer, or window)
for (int i = 0; i < n; i++) {
    // update state
}

// Step 4 — return derived answer
return result;
```

Eighty percent of medium array problems fit this shape. The trick is picking the right state in Step 2.

### The 3 questions to ask before coding

1. **What state do I need to track?** Running sum? Last-seen index? A pointer to a boundary?
2. **How do I update that state on each step?** Increment? Reset? Push to a map?
3. **When do I record the answer?** On every step? Only when a condition fires?

Answer those three before writing a single line of code. If you can't, you don't understand the problem yet.

---

## 🎨 Style Habits — Build These From Day 1

> Some habits apply to **every problem you write** (even non-array ones). Others only matter for specific array patterns. **Master the universal ones now**; skim the context-specific ones and revisit when you hit the pattern.

---

### 🌐 Universal Habits (apply everywhere — start using today)

#### Habit 1 — Always validate input first

```java
// ✅
if (nums == null || nums.length == 0) {
    return new int[0];      // or 0, or whatever makes sense
}
```

LeetCode constraints often promise non-empty input — but interviewers love to ask "what if it's empty?". Get the habit.

#### Habit 2 — Cache `nums.length` if you use it 3+ times

```java
// ✅
int n = nums.length;
for (int i = 0; i < n; i++) {
    // ...
}
```

This is for readability, not performance (JIT inlines the field access). But `n` reads cleaner than `nums.length` repeated five times.

#### Habit 3 — Use `long` for accumulators when sums or products may exceed `2 × 10⁹`

```java
// ❌ overflows if nums has 10⁵ elements each up to 10⁵
int sum = 0;
for (int x : nums) {
    sum += x;
}

// ✅
long sum = 0;
for (int x : nums) {
    sum += x;
}
```

Full coverage: **`DSA/DeepDive/integer-overflow-and-limits.md`**.

#### Habit 4 — Print with `Arrays.toString` (not `arr.toString()`)

```java
// ❌ prints [I@1d44bcfa  (the JVM hash code)
System.out.println(arr);

// ✅ prints [1, 2, 3, 4, 5]
System.out.println(Arrays.toString(arr));
```

#### Habit 5 — `Integer.compare(a, b)` over `a - b` in comparators

```java
// ❌ overflows for Integer.MIN_VALUE
Arrays.sort(boxed, (a, b) -> a - b);

// ✅
Arrays.sort(boxed, (a, b) -> Integer.compare(a, b));
```

#### Habit 6 — Verbalize the loop invariant before you write the loop

Before writing `for (int l = 0, r = n - 1; l < r; ...)`, say out loud: *"l is the smallest index not yet processed; r is the largest index not yet processed; both pointers move toward each other."* Naming the invariant catches bugs that pass review but fail at runtime.

---

### 🔧 Context-Specific Habits (will click as you encounter these patterns)

> These won't matter on your first 5 array problems. **Skim them now to recognize the trap, then refer back when you actually hit the pattern.**

#### Habit 7 — Two pointers: name them by role
> Applies whenever you use two indices.

```java
// ❌ what do i and j mean?
int i = 0, j = nums.length - 1;

// ✅ self-documenting
int left = 0, right = nums.length - 1;       // converging
int slow = 0, fast = 0;                      // same-direction (slow = write, fast = read)
int write = 0, read = 0;                     // even better when mutating in place
```

#### Habit 8 — Sliding window: write the **invariant** as a blockquote comment
> Applies in every variable-size window problem.

```java
// Invariant: [l, r] is the smallest window ending at r that satisfies sum >= target
int l = 0;
int sum = 0;
for (int r = 0; r < n; r++) {
    sum += nums[r];
    while (sum >= target) {
        // record answer
        sum -= nums[l];
        l++;
    }
}
```

When you can write down the invariant, the code writes itself.

#### Habit 9 — Prefix sum: use length `n + 1` so `prefix[0] = 0` is the empty-prefix sentinel
> Applies in any prefix-sum problem.

```java
// ✅
long[] prefix = new long[n + 1];
prefix[0] = 0;
for (int i = 0; i < n; i++) {
    prefix[i + 1] = prefix[i] + nums[i];
}
// Sum of nums[l..r] (inclusive) = prefix[r + 1] - prefix[l]
```

The sentinel removes the "what if I want the sum of the empty prefix?" special case.

#### Habit 10 — Matrix problems: clearly distinguish `row` and `col` (never `i` and `j`)
> Applies in any 2D-grid problem.

```java
// ✅
for (int row = 0; row < m; row++) {
    for (int col = 0; col < n; col++) {
        // ...
    }
}
```

`i, j` works in 1D pointer code. In 2D, `row, col` survives the re-read three days later.

#### Habit 11 — Never modify an array while iterating with a `for-each`
> Applies whenever you'd reach for `for (int x : nums)`.

```java
// ❌ ConcurrentModificationException (for ArrayList)
for (int x : list) {
    if (x < 0) {
        list.remove(Integer.valueOf(x));
    }
}

// ✅ iterate by index
for (int i = list.size() - 1; i >= 0; i--) {
    if (list.get(i) < 0) {
        list.remove(i);
    }
}
```

Iterate backwards so removed indices don't shift the unread part.

---

> **Quick recap of the 6 universal habits:** validate input → cache length → long for sums → `Arrays.toString` → `Integer.compare` → verbalize the invariant. Those six cover ~90% of habit benefit on your first 20 array problems.

---

## 🧭 Patterns — The 14 Core Shapes

The whole array playlist boils down to **14 recurring patterns**. Pattern-match in under 60 seconds: read problem → identify keyword → pick pattern → write template.

| # | Pattern | One-line tell | Striver V-N |
| --- | --- | --- | --- |
| 1 | Two Pointers (converging) | Sorted array, find pair / triple | V-2, V-5, V-20, V-21 |
| 2 | Two Pointers (same direction) | In-place mutation, dedup, partition | V-1, V-2 |
| 3 | Sliding Window (fixed) | "Window of size k" | (foundation for V-4) |
| 4 | Sliding Window (variable) | "Longest / shortest subarray with property" | V-4 |
| 5 | Prefix Sum | "Range sum query" or "sum from i to j" | V-17 (foundation) |
| 6 | Prefix Sum + HashMap | "Subarray with sum K" or "subarray with XOR K" (negatives allowed) | V-17, V-22 |
| 7 | Hashing / Frequency Map | "Has a pair / first repeat / count occurrences" | V-5, V-13 |
| 8 | Kadane's Algorithm | "Max sum subarray" | V-8, V-28 |
| 9 | Dutch National Flag | "Sort 0/1/2 in one pass" | V-6 |
| 10 | Moore's Voting | "Majority element (> n/2 or n/3)" | V-7, V-19 |
| 11 | Cyclic Sort | Values in `[1, n]`, find missing/duplicate | V-3 |
| 12 | Matrix Patterns | 2D traversal, rotation, zero-marking | V-14, V-15, V-16 |
| 13 | Interval Patterns | "Merge / overlap / insert intervals" | V-23 |
| 14 | Modified Merge Sort 🔴 | "Count pairs (i, j) where i < j and condition" | V-26, V-27 |

---

### Pattern 1 — Two Pointers (Converging) [Striver V-2, V-5]

> **When to use:** sorted array, and you're searching for a pair / triple / quadruple satisfying a sum or comparison property.

**Mental picture:** two pointers start at opposite ends and walk toward each other. At each step, one of the three decisions: move `left` right (need bigger), move `right` left (need smaller), or you found a match.

**Steps in plain English:**

1. **Sort the array** if it isn't already.
2. **Initialize two pointers** — `left = 0`, `right = n - 1`.
3. **Loop while `left < right`:** compute the current sum / comparison.
4. **If too small** — move `left` rightward to increase.
5. **If too large** — move `right` leftward to decrease.
6. **If equal** — record the result and move both (skip duplicates if needed).

```java
public int[] twoSumSorted(int[] nums, int target) {
    // Step 2 — pointers at opposite ends
    int left = 0;
    int right = nums.length - 1;

    // Step 3 — walk until they cross
    while (left < right) {
        int sum = nums[left] + nums[right];

        // Step 6 — match
        if (sum == target) {
            return new int[]{ left, right };
        }
        // Step 4 — need bigger
        if (sum < target) {
            left++;
        // Step 5 — need smaller
        } else {
            right--;
        }
    }
    return new int[]{ -1, -1 };
}
```

🏷️ **Example problems:** LC 167 Two Sum II (Sorted) · LC 11 Container With Most Water · LC 15 3 Sum · LC 18 4 Sum · LC 75 Sort Colors (variant)

#### 🎨 Visual — Converging Two Pointers

```
Sorted array,  target = 9:

  index:     0   1   2   3   4   5
           ┌───┬───┬───┬───┬───┬───┐
  nums:    │ 1 │ 2 │ 4 │ 7 │ 11│ 15│
           └───┴───┴───┴───┴───┴───┘
             L                   R
            1 + 15 = 16 > 9  →  R--

           ┌───┬───┬───┬───┬───┬───┐
           │ 1 │ 2 │ 4 │ 7 │ 11│ 15│
           └───┴───┴───┴───┴───┴───┘
             L              R
            1 + 11 = 12 > 9  →  R--

           ┌───┬───┬───┬───┬───┬───┐
           │ 1 │ 2 │ 4 │ 7 │ 11│ 15│
           └───┴───┴───┴───┴───┴───┘
             L          R
            1 + 7 = 8 < 9  →  L++

           ┌───┬───┬───┬───┬───┬───┐
           │ 1 │ 2 │ 4 │ 7 │ 11│ 15│
           └───┴───┴───┴───┴───┴───┘
                 L      R
            2 + 7 = 9 ✅ FOUND


WHY CONVERGING POINTERS ARE O(n):

  Each step moves AT LEAST one pointer inward. They can only cross
  once → at most n steps total. The sortedness is what lets us pick
  the right pointer to move (we never have to "go back").
```

> 🧩 **Try these:**
> - ✅ **LC 167** Two Sum II — direct application
> - ✅ **LC 11** Container With Most Water — same shape, different decision (move the **shorter** wall)
> - 🟡 **LC 15** Three Sum — needs converging pointers + duplicate skipping; tackle after the variable-window section so you're comfortable with double loops

---

### Pattern 2 — Two Pointers (Same Direction / Slow-Fast) [Striver V-1, V-2]

> **When to use:** in-place modification of an array — dedup, move zeros, partition, remove element.

**Mental picture:** a "read head" (fast) scans every element; a "write head" (slow) trails behind, only advancing when the current element should be kept.

**Steps in plain English:**

1. **Two pointers, both at 0:** `slow` (or `write`) and `fast` (or `read`).
2. **Loop `fast` from 0 to n - 1.**
3. **Decide:** does `nums[fast]` deserve to be in the output?
4. **If yes:** write it at `slow`, then advance `slow`.
5. **`fast` always advances.**

```java
public int removeDuplicates(int[] nums) {
    if (nums.length == 0) {
        return 0;
    }
    // Step 1 — slow points to the next write position
    int slow = 1;

    // Step 2 — fast scans from index 1 onward
    for (int fast = 1; fast < nums.length; fast++) {
        // Step 3, 4 — keep only if different from the last kept
        if (nums[fast] != nums[slow - 1]) {
            nums[slow] = nums[fast];
            slow++;
        }
    }
    return slow;
}
```

🏷️ **Example problems:** LC 26 Remove Duplicates · LC 27 Remove Element · LC 283 Move Zeroes · LC 80 Remove Duplicates II

> 🧩 **Try these:**
> - ✅ **LC 26** Remove Duplicates from Sorted Array — the template above
> - ✅ **LC 27** Remove Element — same shape, different keep-condition
> - ✅ **LC 283** Move Zeroes — write non-zeros first, then fill the rest with 0

---

### Pattern 3 — Sliding Window (Fixed Size)

> **When to use:** "find max/min/avg over every window of size `k`".

**Mental picture:** a window of width `k` slides one step at a time. To update efficiently, **add the entering element, subtract the leaving element** — no rebuild.

**Steps in plain English:**

1. **Compute the sum of the first window** (`nums[0..k-1]`).
2. **Slide:** for each new right edge, add `nums[r]` and subtract `nums[r - k]`.
3. **Track the answer** (max / min / etc.) after each slide.

```java
public double findMaxAverage(int[] nums, int k) {
    // Step 1 — first window
    long sum = 0;
    for (int i = 0; i < k; i++) {
        sum += nums[i];
    }
    long maxSum = sum;

    // Step 2 — slide one step at a time
    for (int r = k; r < nums.length; r++) {
        sum += nums[r];
        sum -= nums[r - k];
        // Step 3 — track best
        maxSum = Math.max(maxSum, sum);
    }
    return (double) maxSum / k;
}
```

🏷️ **Example problems:** LC 643 Maximum Average Subarray I · LC 1456 Max Vowels in Substring · LC 567 Permutation in String

---

### Pattern 4 — Sliding Window (Variable Size) [Striver V-4]

> **When to use:** "longest / shortest / count of subarrays satisfying a property" where the property is **monotonic** — once it's violated by expanding, only shrinking can fix it.

**Mental picture:** two pointers `l` and `r` define a window. `r` always advances. When the window violates the property, advance `l` until it's valid again.

**Steps in plain English:**

1. **Initialize** `l = 0`, the running state (sum / frequency map), and the answer.
2. **Loop `r` from 0 to n - 1:** add `nums[r]` to the state.
3. **While the window violates the property:** remove `nums[l]` from the state and advance `l`.
4. **Update the answer** with the current valid window size.

**Universal template — longest subarray with sum ≤ target (positive values):**

```java
public int longestSubarrayAtMostK(int[] nums, int k) {
    // Step 1 — pointers and state
    int l = 0;
    long sum = 0;
    int best = 0;

    // Step 2 — extend the window
    for (int r = 0; r < nums.length; r++) {
        sum += nums[r];

        // Step 3 — shrink while invalid
        while (sum > k) {
            sum -= nums[l];
            l++;
        }

        // Step 4 — record the best valid window
        best = Math.max(best, r - l + 1);
    }
    return best;
}
```

> **Critical warning:** this pattern only works when the property is **monotonic in the window**. "Longest subarray with sum K" where values can be **negative** is NOT monotonic — adding a negative value can fix an overshooting window. For negatives, you need **Pattern 6 (Prefix Sum + HashMap)** instead.

#### 🎨 Visual — Variable Sliding Window (expand-right, shrink-left)

```
Longest subarray with sum ≤ 8,   nums = [3, 1, 2, 5, 1, 4]:

  r = 0:   [3]                    sum=3       window=[3]            len=1
           L↑R↑

  r = 1:   [3, 1]                 sum=4       window=[3, 1]         len=2
           L↑ R↑

  r = 2:   [3, 1, 2]              sum=6       window=[3, 1, 2]      len=3
           L↑    R↑

  r = 3:   [3, 1, 2, 5]           sum=11 > 8  →  SHRINK
           L↑       R↑
           shrink: remove 3, L→1, sum=8       window=[1, 2, 5]      len=3
              L↑    R↑

  r = 4:   [1, 2, 5, 1]           sum=9 > 8  →  SHRINK
              L↑       R↑
           shrink: remove 1, L→2, sum=8       window=[2, 5, 1]      len=3
                 L↑    R↑

  r = 5:   [2, 5, 1, 4]           sum=12 > 8  →  SHRINK
                 L↑       R↑
           shrink: remove 2, L→3, sum=10 > 8  →  SHRINK
                    L↑    R↑
           shrink: remove 5, L→4, sum=5       window=[1, 4]         len=2
                       L↑ R↑

  RESULT: longest valid window length = 3


THE MONOTONICITY INVARIANT (why this is O(n)):

   Each pointer moves only FORWARD. Total work = O(n + n) = O(n).

   The window is valid at the moment we update `best`.  When the
   "expand" step pushes us into invalid territory, "shrink" steps
   walk L forward until we're valid again.  Both pointers
   monotonically increase — no work is repeated.


WHEN THIS PATTERN BREAKS (must switch to prefix-sum + hashmap):

   nums = [1, -1, 5, -2, 3],  target sum = 3

   r=0: [1]          sum=1
   r=1: [1, -1]      sum=0    ← "below target" — but window is GROWING
   r=2: [1, -1, 5]   sum=5    ← "above target"
                                shrinking won't help: removing 1
                                gives [-1, 5] sum=4, still > 3.
   The monotonic shrink-to-valid loop doesn't converge with
   negative numbers because adding/removing can move sum in EITHER
   direction.  That's why the pattern only works for positive values.
```

🏷️ **Example problems:** LC 3 Longest Substring Without Repeating · LC 209 Minimum Size Subarray Sum · LC 76 Minimum Window Substring · LC 904 Fruit Into Baskets · LC 1004 Max Consecutive Ones III

> 🧩 **Try these:**
> - ✅ **LC 3** Longest Substring Without Repeating Characters — frequency map + shrink on duplicate
> - ✅ **LC 209** Minimum Size Subarray Sum — exact template above with `<` instead of `>`
> - 🟡 **LC 76** Minimum Window Substring — same pattern but the "valid" check is non-trivial; come back after you're comfortable with map-based windows

---

### Pattern 5 — Prefix Sum (Raw) [Striver V-17 foundation]

> **When to use:** repeated **range sum queries** on a static array, or "sum from i to j" appears in the inner loop.

**Mental picture:** precompute `prefix[i] = nums[0] + nums[1] + ... + nums[i - 1]`. Then `sum(l..r) = prefix[r + 1] - prefix[l]` — every range sum becomes O(1).

**Steps in plain English:**

1. **Allocate `prefix[]` of size `n + 1`** with `prefix[0] = 0`.
2. **Build:** `prefix[i + 1] = prefix[i] + nums[i]`.
3. **Query:** sum from index `l` to `r` (inclusive) = `prefix[r + 1] - prefix[l]`.

```java
public class NumArray {
    private final long[] prefix;

    public NumArray(int[] nums) {
        // Step 1, 2 — build prefix
        prefix = new long[nums.length + 1];
        for (int i = 0; i < nums.length; i++) {
            prefix[i + 1] = prefix[i] + nums[i];
        }
    }

    // Step 3 — answer query in O(1)
    public long sumRange(int l, int r) {
        return prefix[r + 1] - prefix[l];
    }
}
```

🏷️ **Example problems:** LC 303 Range Sum Query (Immutable) · LC 304 Range Sum 2D · LC 1480 Running Sum

---

### Pattern 6 — Prefix Sum + HashMap [Striver V-17, V-22]

> **When to use:** "count subarrays with sum / XOR equal to K" — especially when negative values rule out sliding window.

**Mental picture:** if `prefix[r] - prefix[l] = K`, then the subarray `nums[l..r-1]` sums to `K`. Equivalently: `prefix[l] = prefix[r] - K`. As we scan, we keep a map `{prefix_value → count_so_far}` and ask "how many earlier prefixes equal `current - K`?"

**Steps in plain English:**

1. **Initialize a map** `prefixCount` with `{0: 1}` — the empty prefix has sum 0.
2. **Walk through the array**, maintaining a running `prefix`.
3. **At each step**, look up `prefix - K` in the map. That count is how many subarrays **ending here** sum to `K`. Add to answer.
4. **Then** add the current `prefix` to the map (after the lookup, so we don't match a zero-length subarray).

```java
public int subarraySum(int[] nums, int k) {
    // Step 1 — seed the empty prefix
    Map<Long, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0L, 1);

    long prefix = 0;
    int answer = 0;

    // Step 2 — single pass
    for (int x : nums) {
        prefix += x;

        // Step 3 — how many earlier prefixes give a subarray summing to k?
        answer += prefixCount.getOrDefault(prefix - k, 0);

        // Step 4 — record current prefix
        prefixCount.merge(prefix, 1, Integer::sum);
    }
    return answer;
}
```

> **Why seed `{0: 1}`?** It accounts for subarrays that start at index 0. Without it, a prefix exactly equal to `k` at some index `r` would be missed — that case corresponds to `prefix[l] = 0`, i.e., the empty left prefix.

🏷️ **Example problems:** LC 560 Subarray Sum Equals K · LC 525 Contiguous Array · LC 974 Subarray Sums Divisible by K · LC 1248 Count Number of Nice Subarrays · "Subarrays with XOR K" (Striver V-22 — same shape, replace `+` with `^`)

> 🧩 **Try these:**
> - ✅ **LC 560** Subarray Sum Equals K — the template above, verbatim
> - ✅ **LC 974** Subarray Sums Divisible by K — same shape; key the map on `prefix % k`
> - 🟡 **LC 525** Contiguous Array — encode 0 as -1, then find "subarrays with sum 0"; deep aha-moment

---

### Pattern 7 — Hashing / Frequency Map [Striver V-5, V-13]

> **When to use:** "is there a pair", "first repeating", "count occurrences", "anagram check" — anywhere the question reduces to "did I see X before?" or "how many of each?".

Two main shapes:

#### Shape A — "Seen-before" lookup (single pass)

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (seen.containsKey(need)) {
            return new int[]{ seen.get(need), i };
        }
        seen.put(nums[i], i);
    }
    return new int[]{ -1, -1 };
}
```

#### Shape B — Frequency-count map

```java
Map<Integer, Integer> freq = new HashMap<>();
for (int x : nums) {
    freq.merge(x, 1, Integer::sum);
}
// Now query: freq.getOrDefault(target, 0)
```

🏷️ **Example problems:** LC 1 Two Sum · LC 49 Group Anagrams · LC 128 Longest Consecutive Sequence · LC 442 Find All Duplicates

Full HashMap idioms: **`DSA/Reference/hashmap-section-updated.md`**.

---

### Pattern 8 — Kadane's Algorithm [Striver V-8]

> **When to use:** "maximum sum subarray" (or product, or XOR — anything with a running accumulator).

**Mental picture:** at each index `i`, you have two choices for the "best subarray ending at `i`":
- **Extend** the previous best subarray by adding `nums[i]` to it
- **Restart** with just `nums[i]` (if the previous best was negative, it was dragging us down)

Take the better of the two. Track the global maximum as you go.

**Steps in plain English:**

1. **`current`** = best sum ending at the current index (running accumulator).
2. **`best`** = global maximum seen so far.
3. **At each index:** `current = max(nums[i], current + nums[i])`.
4. **Update** `best = max(best, current)`.

```java
public int maxSubArray(int[] nums) {
    // Step 1, 2 — initialize with the first element
    int current = nums[0];
    int best = nums[0];

    // Step 3, 4 — single pass
    for (int i = 1; i < nums.length; i++) {
        current = Math.max(nums[i], current + nums[i]);
        best = Math.max(best, current);
    }
    return best;
}
```

> **Why `nums[0]` as the seed and not `0`?** Because the subarray must be **non-empty**. Seeding with `0` would let an all-negative array return 0 (wrong — should return the largest single element).

#### 🎨 Visual — Kadane's "Extend or Restart" Decision

```
nums = [-2, 1, -3, 4, -1, 2, 1, -5, 4]


At each index, ask: would extending the previous best HELP or HURT?

  i:  0    1     2    3    4    5    6    7    8
  v: -2    1    -3    4   -1    2    1   -5    4

  current:  -2 → 1 → -2 → 4 → 3 → 5 → 6 → 1 → 5
  decision:  -    R    E    R    E    E    E    E    E
                  ↑    ↑    ↑
              RESTART (1 > -2 + 1 = -1)
                       EXTEND (-3 + 1 = -2 vs -3 alone; -2 wins)
                            RESTART (4 > -2 + 4 = 2)

  R = RESTART:   current = nums[i]         (previous best was dragging us down)
  E = EXTEND:    current = current + nums[i]

  best:    -2 →  1 →  1 →  4 →  4 →  5 →  6 →  6 →  6   ←── tracked separately

  ANSWER:  6   (subarray [4, -1, 2, 1] from index 3 to 6)


THE TWO-CHOICES PICTURE:

   current      ───►   "best sum of a subarray that ENDS at index i"

   At each i, current asks ONE question:

         ┌──────────────────────────────────────────────┐
         │  Was the previous current ≥ 0?               │
         │     YES → extend:   current = current + v    │
         │      NO → restart:  current = v              │
         └──────────────────────────────────────────────┘

   That's literally  current = max(v, current + v).

   The "global" answer `best` is tracked SEPARATELY because the
   maximum subarray might have ended at any index, not necessarily
   the last one.


WHY KADANE'S IS O(n) AND STILL FINDS THE OPTIMAL:

   Every subarray ends at SOME index i.  By computing the best
   sum ending at every i and taking the max across all i, we
   cover every subarray exactly once.  O(n²) brute force tries
   every (l, r) pair; Kadane's collapses the inner loop because
   "best ending at i" can be built from "best ending at i-1" in O(1).
```

🏷️ **Example problems:** LC 53 Maximum Subarray · LC 152 Maximum Product Subarray (variant) · LC 121 Best Time to Buy and Sell Stock (variant)

> 🧩 **Try these:**
> - ✅ **LC 53** Maximum Subarray — the template above
> - ✅ **LC 121** Best Time to Buy and Sell Stock — same shape; track min-so-far instead of running sum
> - 🟡 **LC 152** Maximum Product Subarray — track **both** running max AND running min (negatives can flip min→max). See Walkthrough 5.

---

### Pattern 9 — Dutch National Flag (3-Way Partition) [Striver V-6]

> **When to use:** sort an array of exactly 3 distinct values (0/1/2, low/mid/high) in **one pass** and **O(1) extra space**.

**Mental picture:** three regions, separated by three pointers:
- `[0, low)` — all 0s (already sorted)
- `[low, mid)` — all 1s (already sorted)
- `[mid, high]` — unprocessed
- `(high, n - 1]` — all 2s (already sorted)

`mid` is the scanner. Based on `nums[mid]`:
- **0** → swap with `nums[low]`, advance both `low` and `mid`
- **1** → leave it, advance `mid`
- **2** → swap with `nums[high]`, decrement `high` (do NOT advance `mid` — the swapped-in value is still unprocessed)

**Steps in plain English:**

1. **Initialize:** `low = 0`, `mid = 0`, `high = n - 1`.
2. **Loop while `mid <= high`** — `mid` is the cursor scanning unprocessed.
3. **Three-way switch on `nums[mid]`:** swap-and-advance, just-advance, or swap-and-don't-advance.

```java
public void sortColors(int[] nums) {
    // Step 1 — three pointers
    int low = 0;
    int mid = 0;
    int high = nums.length - 1;

    // Step 2 — scan unprocessed region
    while (mid <= high) {
        // Step 3 — case on the current value
        if (nums[mid] == 0) {
            swap(nums, low, mid);
            low++;
            mid++;
        } else if (nums[mid] == 1) {
            mid++;
        } else {
            swap(nums, mid, high);
            high--;
            // do NOT advance mid — re-examine swapped-in value next iteration
        }
    }
}

private void swap(int[] nums, int i, int j) {
    int tmp = nums[i];
    nums[i] = nums[j];
    nums[j] = tmp;
}
```

> **The classic bug:** advancing `mid` after the `nums[mid] == 2` swap. The value swapped in from the back hasn't been examined yet — if you skip it, you'll miss a 0.

#### 🎨 Visual — Dutch National Flag Three Regions

```
The invariant maintained throughout the scan:

    ┌──────────┬──────────┬───────────────┬──────────┐
    │   0 0 0  │  1 1 1   │   ??? ??? ??? │  2 2 2   │
    └──────────┴──────────┴───────────────┴──────────┘
     0       low-1  low  mid-1  mid     high  high+1   n-1
       sorted    sorted   unprocessed    sorted
        0s        1s       (the gap)      2s


CASES (mid is the scanner):

  nums[mid] == 0:                    nums[mid] == 1:           nums[mid] == 2:
    swap nums[low] ↔ nums[mid]         keep in place              swap nums[mid] ↔ nums[high]
    low++, mid++                       mid++                      high--, mid STAYS
    (the swapped-out value at low      (1 belongs in the           (the value just swapped in
     was always 1 — already in         middle region — already     came from the unprocessed
     1-region, so it's safe)           sorted)                     region — re-examine it)


EXAMPLE walk on  [2, 0, 1, 2, 1, 0]:

  Start:   [2, 0, 1, 2, 1, 0]     low=0 mid=0 high=5
            ↑M               ↑H
  nums[mid]=2 → swap(0,5), high=4         [0, 0, 1, 2, 1, 2]
            ↑M           ↑H
  nums[mid]=0 → swap(0,0), low=1 mid=1    [0, 0, 1, 2, 1, 2]
               ↑M         ↑H
  nums[mid]=0 → swap(1,1), low=2 mid=2    [0, 0, 1, 2, 1, 2]
                  ↑M      ↑H
  nums[mid]=1 → mid=3                     [0, 0, 1, 2, 1, 2]
                     ↑M   ↑H
  nums[mid]=2 → swap(3,4), high=3         [0, 0, 1, 1, 2, 2]
                     ↑MH
  nums[mid]=1 → mid=4                     [0, 0, 1, 1, 2, 2]
                        ↑H  ↑M  → loop exits (mid > high)

  FINAL: [0, 0, 1, 1, 2, 2]   ✅ sorted in ONE pass, in place
```

🏷️ **Example problems:** LC 75 Sort Colors

---

### Pattern 10 — Moore's Voting Algorithm [Striver V-7, V-19]

> **When to use:** find an element appearing more than `n/2` times (majority) or more than `n/3` times.

**Mental picture (n/2 version):** imagine every element as a vote. A majority element appears more often than all others combined, so if you pair each majority vote against each non-majority vote, the majority always has leftover votes.

Maintain a candidate and a count:
- If `count == 0`, set the current element as the new candidate.
- If current matches candidate, `count++`.
- Otherwise, `count--`.

The element left as candidate at the end **may** be the majority — verify with a second pass.

**Steps in plain English:**

1. **`candidate = ?`, `count = 0`.**
2. **First pass — voting:** apply the three rules above.
3. **Second pass — verify:** count occurrences of `candidate`. If > n/2, return it. Otherwise no majority exists.

```java
public int majorityElement(int[] nums) {
    // Step 1, 2 — voting
    Integer candidate = null;
    int count = 0;

    for (int x : nums) {
        if (count == 0) {
            candidate = x;
        }
        if (x == candidate) {
            count++;
        } else {
            count--;
        }
    }

    // Step 3 — verify
    int freq = 0;
    for (int x : nums) {
        if (x == candidate) {
            freq++;
        }
    }
    return freq > nums.length / 2 ? candidate : -1;
}
```

> **For the n/3 variant (LC 229):** there can be **at most 2** elements with frequency > n/3. Track two candidates and two counts; same voting rules apply pairwise. Always verify both at the end.

🏷️ **Example problems:** LC 169 Majority Element · LC 229 Majority Element II

---

### Pattern 11 — Cyclic Sort / Index-as-Bucket [Striver V-3 foundation]

> **When to use:** the values are integers in a known range like `[1, n]` or `[0, n-1]`, **and** you need to find missing / duplicate / first-missing-positive in O(n) time and O(1) extra space.

**Mental picture:** value `v` belongs at index `v - 1` (for 1-based range). Sweep through; if `nums[i]` isn't at its correct home, swap it home. Repeat at the same `i` until the slot holds a value that belongs there (or is out of range).

**Steps in plain English:**

1. **Walk `i` from 0 to n - 1.**
2. **While `nums[i]` is in range AND not yet at its correct index:** swap it to its correct index.
3. **After sorting, scan to find the anomaly** (missing or duplicate).

```java
public int firstMissingPositive(int[] nums) {
    int n = nums.length;

    // Step 1, 2 — cyclic placement
    for (int i = 0; i < n; i++) {
        while (nums[i] >= 1 && nums[i] <= n && nums[nums[i] - 1] != nums[i]) {
            int tmp = nums[nums[i] - 1];
            nums[nums[i] - 1] = nums[i];
            nums[i] = tmp;
        }
    }

    // Step 3 — first position whose value doesn't match
    for (int i = 0; i < n; i++) {
        if (nums[i] != i + 1) {
            return i + 1;
        }
    }
    return n + 1;
}
```

> **Why is the swap inside a `while` loop?** Because after one swap, the new `nums[i]` may also belong elsewhere. Keep swapping until the slot stabilizes.

> **Why the `nums[nums[i] - 1] != nums[i]` guard?** It prevents an infinite loop when duplicates are present (the target slot already holds the same value).

#### 🎨 Visual — Cyclic Sort "Value v Belongs at Index v-1"

```
nums = [3, 1, 5, 4, 2]      (1..n distinct values)

Every value v should sit at index v-1.

   value:  1   2   3   4   5
   home:   0   1   2   3   4   ← target index


STEP-BY-STEP "send each value home":

  i=0:  [3, 1, 5, 4, 2]      nums[0]=3, home is index 2
                              swap(0, 2):  [5, 1, 3, 4, 2]
                              nums[0]=5, home is index 4
                              swap(0, 4):  [2, 1, 3, 4, 5]
                              nums[0]=2, home is index 1
                              swap(0, 1):  [1, 2, 3, 4, 5]
                              nums[0]=1, AT HOME ✅ — exit while, advance i

  i=1:  [1, 2, 3, 4, 5]      nums[1]=2, AT HOME ✅  → i++

  i=2:  ... already in place        → i++

  ...  every subsequent i finds its slot already correct.


WHY THE WHILE LOOP IS REQUIRED:

  Each swap places ONE value at home — but the value that came
  in from the home slot may itself be misplaced.  The while keeps
  swapping until nums[i] is at home OR is out of range.


CYCLE INTERPRETATION (where the name comes from):

  Treat each (index, value) as a directed edge: i → nums[i] - 1.
  An array of distinct values in [1..n] is a permutation, which
  is a union of CYCLES.  The while loop traverses one cycle,
  placing every member at home in O(cycle length) operations.

  Total work across all cycles = O(n).  Each swap moves at least
  one value to its permanent home; there are at most n swaps.


THE WIN — O(n) TIME, O(1) SPACE:

  After cyclic sort, mismatches reveal:
    nums[i] != i + 1  ⇒  index i+1 is "missing"
                          and nums[i] is "extra/duplicate"

  This is the secret behind LC 41 (First Missing Positive),
  LC 287, LC 442, LC 448 — all the "use index as a bucket" problems.
```

🏷️ **Example problems:** LC 41 First Missing Positive · LC 268 Missing Number · LC 287 Find the Duplicate · LC 442 Find All Duplicates · LC 448 Find All Numbers Disappeared

---

### Pattern 12 — Matrix Patterns [Striver V-14, V-15, V-16]

Three sub-patterns. All exploit the **index-space tricks** from the mental model.

#### 12a — Set Matrix Zeros in O(1) space [V-14]

Use the first row and first column as the metadata arrays. Two extra booleans handle whether the first row / column themselves need to be zeroed.

#### 12b — Rotate Image 90° Clockwise [V-15]

**Insight:** rotating 90° = **transpose + reverse each row**.

**Steps in plain English:**

1. **Transpose** the matrix in place: swap `matrix[i][j]` with `matrix[j][i]` for all `i < j`.
2. **Reverse each row** in place.

```java
public void rotate(int[][] matrix) {
    int n = matrix.length;

    // Step 1 — transpose
    for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
            int tmp = matrix[i][j];
            matrix[i][j] = matrix[j][i];
            matrix[j][i] = tmp;
        }
    }

    // Step 2 — reverse each row
    for (int i = 0; i < n; i++) {
        int l = 0;
        int r = n - 1;
        while (l < r) {
            int tmp = matrix[i][l];
            matrix[i][l] = matrix[i][r];
            matrix[i][r] = tmp;
            l++;
            r--;
        }
    }
}
```

> **For 90° counter-clockwise:** transpose + reverse each **column** (equivalently: reverse each row first, then transpose).

#### 12c — Spiral Traversal [V-16]

**Insight:** maintain four boundaries (top, bottom, left, right) and shrink them as you complete each side.

**Steps in plain English:**

1. **Initialize boundaries:** `top = 0`, `bottom = m - 1`, `left = 0`, `right = n - 1`.
2. **Loop while `top <= bottom` AND `left <= right`:**
   - Walk left → right across `top`; then `top++`
   - Walk top → bottom down `right`; then `right--`
   - **Check `top <= bottom`** then walk right → left across `bottom`; then `bottom--`
   - **Check `left <= right`** then walk bottom → top up `left`; then `left++`

```java
public List<Integer> spiralOrder(int[][] matrix) {
    List<Integer> result = new ArrayList<>();
    int top = 0;
    int bottom = matrix.length - 1;
    int left = 0;
    int right = matrix[0].length - 1;

    while (top <= bottom && left <= right) {
        for (int c = left; c <= right; c++) {
            result.add(matrix[top][c]);
        }
        top++;

        for (int r = top; r <= bottom; r++) {
            result.add(matrix[r][right]);
        }
        right--;

        if (top <= bottom) {
            for (int c = right; c >= left; c--) {
                result.add(matrix[bottom][c]);
            }
            bottom--;
        }

        if (left <= right) {
            for (int r = bottom; r >= top; r--) {
                result.add(matrix[r][left]);
            }
            left++;
        }
    }
    return result;
}
```

> **The classic bug:** forgetting the two `if` guards before the bottom and left walks. After `top++` and `right--`, the boundaries can cross — re-checking prevents reading rows/columns you already visited.

🏷️ **Example problems:** LC 73 Set Matrix Zeroes · LC 48 Rotate Image · LC 54 Spiral Matrix · LC 59 Spiral Matrix II

---

### Pattern 13 — Interval Patterns [Striver V-23]

> **When to use:** problems on lists of `[start, end]` pairs — merge overlaps, insert, count.

**Mental picture:** sort by start. Then sweep linearly, merging when the current interval starts before the previous one ends.

**Steps in plain English:**

1. **Sort intervals by start time.**
2. **Initialize result with the first interval.**
3. **For each subsequent interval:**
   - If it overlaps the last merged interval (start ≤ last.end), extend `last.end = max(last.end, current.end)`.
   - Else, append it as a new interval in the result.

```java
public int[][] merge(int[][] intervals) {
    if (intervals.length == 0) {
        return new int[0][];
    }

    // Step 1 — sort by start
    Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));

    // Step 2 — running result
    List<int[]> merged = new ArrayList<>();
    merged.add(intervals[0]);

    // Step 3 — sweep
    for (int i = 1; i < intervals.length; i++) {
        int[] last = merged.get(merged.size() - 1);
        int[] curr = intervals[i];

        if (curr[0] <= last[1]) {
            last[1] = Math.max(last[1], curr[1]);
        } else {
            merged.add(curr);
        }
    }

    return merged.toArray(new int[merged.size()][]);
}
```

> **Why mutate `last[1]` in place?** Because the reference inside `merged` is the same array; updating it via `last[1] = ...` updates the stored entry too. No need to remove-and-re-add.

🏷️ **Example problems:** LC 56 Merge Intervals · LC 57 Insert Interval · LC 435 Non-overlapping Intervals · LC 252 Meeting Rooms · LC 986 Interval Intersection

---

### Pattern 14 — Modified Merge Sort 🔴 [Striver V-26, V-27]

> **Reference Only on a first pass.** Used for "count pairs `(i, j)` where `i < j` and some condition" — Count Inversions, LC 493 Reverse Pairs.

**Mental picture:** during the merge step of merge sort, both halves are sorted. That sortedness gives you O(n) cross-half pair-counting "for free" — you couldn't get that with two unsorted halves.

**The skeleton:**

```java
public int countPairs(int[] nums) {
    int[] aux = new int[nums.length];
    return mergeSort(nums, aux, 0, nums.length - 1);
}

private int mergeSort(int[] nums, int[] aux, int l, int r) {
    if (l >= r) {
        return 0;
    }
    int mid = l + (r - l) / 2;
    int count = mergeSort(nums, aux, l, mid)
              + mergeSort(nums, aux, mid + 1, r);
    count += countCrossPairs(nums, l, mid, r);
    merge(nums, aux, l, mid, r);
    return count;
}
```

The exact `countCrossPairs` differs by problem (strictly greater? doubled? etc.), but the wrapper is identical to plain merge sort.

🏷️ **Example problems (all 🔴):** Count Inversions · LC 493 Reverse Pairs · LC 315 Count of Smaller Numbers After Self

Defer until medium-interview essentials are reflex.

---

## 🌳 Special Topics

### 1. Next Permutation — The 4-Step Algorithm [Striver V-11]

Problem: given an array of digits, mutate it to the lexicographically **next** permutation. If the input is the largest permutation, mutate to the smallest (sorted ascending).

**Insight:** the array has a suffix that's strictly decreasing (the "already-maxed" part). The element just before that suffix is the **pivot** — we need to bump it up by the smallest possible amount, then reset the suffix to ascending.

**Steps in plain English:**

1. **Find the pivot** — scan from the right; the first index `i` where `nums[i] < nums[i + 1]`. (If none, the whole array is descending → reverse it and return.)
2. **Find the swap partner** — scan from the right; the first index `j > i` where `nums[j] > nums[i]`.
3. **Swap** `nums[i]` and `nums[j]`.
4. **Reverse** the suffix starting at `i + 1` (this turns the descending tail into ascending).

**Visualization:**

```
Input:           [1, 3, 5, 4, 2]
                       ↑
Step 1 — pivot at index 1 (3 < 5, first such from right)

                  [1, 3, 5, 4, 2]
                              ↑
Step 2 — swap partner at index 3 (4 is smallest > 3 from right)

Step 3 — swap pivot and partner:
                  [1, 4, 5, 3, 2]

Step 4 — reverse suffix from index 2:
                  [1, 4, 2, 3, 5]
                  ↑
                  next permutation
```

```java
public void nextPermutation(int[] nums) {
    int n = nums.length;

    // Step 1 — pivot
    int i = n - 2;
    while (i >= 0 && nums[i] >= nums[i + 1]) {
        i--;
    }

    if (i >= 0) {
        // Step 2 — swap partner
        int j = n - 1;
        while (nums[j] <= nums[i]) {
            j--;
        }
        // Step 3 — swap
        swap(nums, i, j);
    }

    // Step 4 — reverse suffix
    reverse(nums, i + 1, n - 1);
}

private void swap(int[] nums, int a, int b) {
    int tmp = nums[a];
    nums[a] = nums[b];
    nums[b] = tmp;
}

private void reverse(int[] nums, int l, int r) {
    while (l < r) {
        swap(nums, l, r);
        l++;
        r--;
    }
}
```

> **Why does the suffix always end up sorted ascending after the swap?** Because the suffix was already strictly decreasing, and the swap exchanges one element with a strictly greater one from inside the suffix — preserving the descending property. Reversing a descending sequence gives ascending.

---

### 2. XOR Tricks for Arrays [Striver V-3]

XOR has three properties that are interview gold:

| Property | Why useful |
| --- | --- |
| `a ^ a = 0` | A number XOR'd with itself cancels |
| `a ^ 0 = a` | Identity element is 0 |
| Commutative & associative | Order doesn't matter — XOR a whole array in one pass |

**Single Number (every element appears twice except one):**

```java
public int singleNumber(int[] nums) {
    int result = 0;
    for (int x : nums) {
        result ^= x;
    }
    return result;
}
```

All paired elements cancel; the lonely one remains. O(n) time, O(1) space — beats the hash-set approach on space.

**Missing Number in `[0, n]` with n distinct values:**

```java
public int missingNumber(int[] nums) {
    int n = nums.length;
    int x = 0;
    // XOR all values [0, n] and all elements; missing one survives
    for (int i = 0; i <= n; i++) {
        x ^= i;
    }
    for (int v : nums) {
        x ^= v;
    }
    return x;
}
```

Same idea: XOR cancels every pair; the missing value is the only one without a partner.

🏷️ **Example problems:** LC 136 Single Number · LC 268 Missing Number · "Subarrays with XOR K" (V-22 — prefix-XOR + HashMap, mirror of Pattern 6)

---

### 3. Longest Consecutive Sequence — The Set Trick [Striver V-13]

Problem: given an unsorted array, find the length of the longest run of consecutive integers. Required: **O(n) time**.

**Naive:** sort, then sweep — O(n log n). Doesn't meet the constraint.

**Insight:** put all values in a HashSet. For each value `v`, only **start counting** if `v - 1` is **not** in the set (so `v` is the smallest of its run). Then walk `v, v+1, v+2, ...` while they're in the set.

**Steps in plain English:**

1. **Build a HashSet** of all values.
2. **For each value `v`:** if `v - 1` is NOT in the set, `v` starts a new run.
3. **Walk forward** from `v`, counting how far the run extends.
4. **Track the maximum.**

```java
public int longestConsecutive(int[] nums) {
    // Step 1 — hashset
    Set<Integer> set = new HashSet<>();
    for (int x : nums) {
        set.add(x);
    }

    int best = 0;

    // Step 2, 3, 4 — only start counting at run-starts
    for (int v : set) {
        if (!set.contains(v - 1)) {
            int current = v;
            int length = 1;
            while (set.contains(current + 1)) {
                current++;
                length++;
            }
            best = Math.max(best, length);
        }
    }
    return best;
}
```

> **Why is this O(n) and not O(n²)?** Each value is only walked **once** — either as a run-start, or as part of another run that started lower. The `set.contains(v - 1)` check is the gatekeeper.

🏷️ **Example problems:** LC 128 Longest Consecutive Sequence

---

### 4. Pascal's Triangle [Striver V-18]

Three variations show up in interviews:

| Variation | Approach | Time |
| --- | --- | --- |
| Print the whole triangle of size `n` | Build row by row from the previous | O(n²) |
| Print only row `r` (0-indexed) | Compute each `C(r, k)` with the running formula | O(r) |
| Print element at `(r, c)` | Single binomial computation | O(min(c, r - c)) |

**Running formula for one row** (the key trick):

```
C(r, k+1) = C(r, k) * (r - k) / (k + 1)
```

So row `r` can be generated by starting with `C(r, 0) = 1` and multiplying step by step.

```java
public List<Integer> getRow(int rowIndex) {
    List<Integer> row = new ArrayList<>();
    long val = 1;
    row.add(1);
    for (int k = 0; k < rowIndex; k++) {
        val = val * (rowIndex - k) / (k + 1);
        row.add((int) val);
    }
    return row;
}
```

> **Why `long`?** Intermediate products can blow past `Integer.MAX_VALUE` for `r` around 33 before the division brings them back down. Cross-ref **`DSA/DeepDive/integer-overflow-and-limits.md`**.

🏷️ **Example problems:** LC 118 Pascal's Triangle · LC 119 Pascal's Triangle II

---

### 5. Merge Sorted Arrays Without Extra Space — Gap Method 🔴 [Striver V-24]

Problem: given two sorted arrays `a` and `b`, rearrange them so combined order is sorted, with `a` holding the smaller half and `b` the larger half. **O(1) extra space.**

> **🔴 Senior+ — skip on first pass.** The "two pointers from the back" version (LC 88 with the empty tail in `a`) is the medium variant and is muscle-memory worthy. The gap method (Shell-sort-style) is the senior version.

The gap method runs a Shell sort across both arrays treating them as one:

1. Start with `gap = ceil((m + n) / 2)`.
2. Compare pairs `gap` apart (within `a`, across `a` and `b`, within `b`); swap if out of order.
3. Halve `gap`. Stop when `gap` reaches 0.

Time: `O((m + n) log(m + n))`. Space: `O(1)`.

Implementation is finicky — refer to Striver V-24 when you need it.

---

### 6. Find Missing AND Repeating 🔴 [Striver V-25]

Problem: array of `[1, n]` where exactly one number is missing and exactly one is duplicated. Find both in O(n) time, O(1) space.

> **🔴 Senior+ — skip on first pass.** Cyclic sort (Pattern 11) solves this cleanly in interview-friendly form. The math trick below is what makes the problem "elegant".

**Math approach (avoids modifying the array):**

Let `S = 1 + 2 + ... + n = n(n+1)/2` and `S2 = 1² + 2² + ... + n² = n(n+1)(2n+1)/6`. Let the array sum be `sumA` and array sum-of-squares be `sumA2`.

- `dup - missing = sumA - S`
- `dup² - missing² = sumA2 - S2`  →  `(dup + missing) = (sumA2 - S2) / (dup - missing)`

Two equations, two unknowns. Solve.

> **Why is this 🔴?** Easy to derive on paper, easy to overflow in code. Use `long` for everything. The cyclic sort version (Pattern 11) is what interviewers actually expect.

---

## 🔬 Worked Walkthroughs

Five fully-traced problems showing patterns in action. Each walkthrough follows English-steps-first.

### Walkthrough 1: LC 53 — Maximum Subarray (Kadane's, Pattern 8)

> **Problem:** Given an integer array `nums`, find the contiguous subarray (containing at least one number) with the **largest sum**, and return its sum.

Example: `nums = [-2, 1, -3, 4, -1, 2, 1, -5, 4]` → output `6` (subarray `[4, -1, 2, 1]`).

**Steps in plain English:**

1. **Initialize** `current = nums[0]` and `best = nums[0]`.
2. **For each `i` from 1 to n - 1:** decide whether to extend or restart.
3. **Extend or restart:** `current = max(nums[i], current + nums[i])`.
4. **Update best:** `best = max(best, current)`.
5. **Return `best`.**

```java
public int maxSubArray(int[] nums) {
    // Step 1
    int current = nums[0];
    int best = nums[0];

    // Step 2
    for (int i = 1; i < nums.length; i++) {
        // Step 3 — restart if previous was dragging us negative
        current = Math.max(nums[i], current + nums[i]);
        // Step 4
        best = Math.max(best, current);
    }
    // Step 5
    return best;
}
```

**Trace on the example:**

```
i:        0    1    2    3    4    5    6    7    8
nums:    -2    1   -3    4   -1    2    1   -5    4
current: -2    1   -2    4    3    5    6    1    5
best:    -2    1    1    4    4    5    6    6    6
                                              ↑
                                          answer = 6
```

**Why this works:** at each index, either the previous "best ending here" is positive (worth keeping) or negative (worth discarding). Kadane's makes that choice greedily and provably optimally.

> 🧩 **Try these (after this walkthrough):**
> - ✅ **LC 121** Best Time to Buy and Sell Stock — track running min instead of running sum
> - 🟡 **LC 152** Maximum Product Subarray — see Walkthrough 5 for the min/max-tracking variant
> - 🟡 **LC 918** Maximum Sum Circular Subarray — Kadane's twice (max + min-inversion)

---

### Walkthrough 2: LC 31 — Next Permutation

> **Problem:** rearrange the array to be the lexicographically next greater permutation. Modify in place; if the array is the largest permutation, rearrange to the smallest (ascending).

Example: `nums = [1, 2, 3]` → `[1, 3, 2]`. · `nums = [3, 2, 1]` → `[1, 2, 3]`.

Already covered as **Special Topic 1**. Re-read that section — the 4-step algorithm, the visualization, and the code.

**Trace on `[1, 3, 5, 4, 2]`:**

```
Step 1: pivot search from right →
   nums[3]=4 ≥ nums[4]=2 → skip
   nums[2]=5 ≥ nums[3]=4 → skip
   nums[1]=3 < nums[2]=5 → pivot i = 1

Step 2: swap-partner search from right →
   nums[4]=2 ≤ nums[1]=3 → skip
   nums[3]=4 > nums[1]=3 → j = 3

Step 3: swap nums[1] and nums[3] → [1, 4, 5, 3, 2]

Step 4: reverse nums[2..4] → [1, 4, 2, 3, 5]
                                          ↑
                                  the answer
```

> 🧩 **Try these (after this walkthrough):**
> - ✅ **LC 31** Next Permutation — code it from scratch without re-reading
> - 🔴 **LC 46** Permutations — different problem (backtracking, all permutations); covered in `DSA/DeepDive/backtracking-fundamentals.md`

---

### Walkthrough 3: LC 128 — Longest Consecutive Sequence

> **Problem:** given an unsorted array, return the length of the longest sequence of consecutive integers. O(n) time required.

Example: `nums = [100, 4, 200, 1, 3, 2]` → `4` (sequence `1, 2, 3, 4`).

Already covered as **Special Topic 3**. Re-read that section.

**Trace on `[100, 4, 200, 1, 3, 2]`:**

```
Set: {100, 4, 200, 1, 3, 2}

Iterate over set:
- 100: is 99 in set? No → run-start. Walk: 100 (1). 101 in set? No → length 1.
-   4: is 3 in set?  Yes → skip.
- 200: is 199 in set? No → run-start. Length 1.
-   1: is 0 in set? No → run-start. Walk: 1, 2, 3, 4. Length 4. ← BEST
-   3: is 2 in set? Yes → skip.
-   2: is 1 in set? Yes → skip.

Answer: 4
```

> **Each element is touched at most twice:** once as part of a walked run, once as the run-start gatekeeper check. Linear overall.

> 🧩 **Try these (after this walkthrough):**
> - ✅ **LC 128** Longest Consecutive Sequence — code from memory
> - 🟡 **LC 298** Binary Tree Longest Consecutive Sequence — different shape (DFS); after trees doc

---

### Walkthrough 4: LC 56 — Merge Intervals

> **Problem:** given a list of intervals, merge all overlapping intervals and return the result.

Example: `[[1,3], [2,6], [8,10], [15,18]]` → `[[1,6], [8,10], [15,18]]`.

**Steps in plain English:**

1. **Sort intervals by start time.**
2. **Initialize result with the first interval.**
3. **For each subsequent interval:** if it overlaps with the last result entry, extend the last entry's end. Otherwise, append.

```java
public int[][] merge(int[][] intervals) {
    if (intervals.length == 0) {
        return new int[0][];
    }
    // Step 1
    Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));

    // Step 2
    List<int[]> merged = new ArrayList<>();
    merged.add(intervals[0]);

    // Step 3
    for (int i = 1; i < intervals.length; i++) {
        int[] last = merged.get(merged.size() - 1);
        int[] curr = intervals[i];
        if (curr[0] <= last[1]) {
            last[1] = Math.max(last[1], curr[1]);
        } else {
            merged.add(curr);
        }
    }
    return merged.toArray(new int[merged.size()][]);
}
```

**Trace:**

```
Sorted: [[1,3], [2,6], [8,10], [15,18]]

merged = [[1,3]]
i=1: curr=[2,6], last=[1,3]. 2 <= 3 → extend last to [1, max(3,6)] = [1,6]
                                          merged = [[1,6]]
i=2: curr=[8,10], last=[1,6]. 8 > 6 → append.
                                          merged = [[1,6], [8,10]]
i=3: curr=[15,18], last=[8,10]. 15 > 10 → append.
                                          merged = [[1,6], [8,10], [15,18]]

Answer: [[1,6], [8,10], [15,18]]
```

> **The off-by-one trap:** when extending, `last[1] = Math.max(last[1], curr[1])` — DON'T just write `last[1] = curr[1]`. The current interval might end *before* the last one (e.g., `[1, 10]` merged with `[2, 5]` is `[1, 10]`, not `[1, 5]`).

> 🧩 **Try these (after this walkthrough):**
> - ✅ **LC 56** Merge Intervals — code from memory
> - ✅ **LC 57** Insert Interval — similar shape; insert one new interval into a sorted list
> - 🟡 **LC 435** Non-overlapping Intervals — greedy after sorting; classic interview question

---

### Walkthrough 5: LC 152 — Maximum Product Subarray [Striver V-28]

> **Problem:** find the contiguous subarray with the largest **product**.

Example: `[2, 3, -2, 4]` → `6` (subarray `[2, 3]`).

**The trap:** plain Kadane's doesn't work for products. A negative element makes a small (negative) running max become small (positive) — but a negative element makes a small (negative) running **min** become a large (positive). So at each step, **multiplying by a negative can flip min and max**.

**Steps in plain English:**

1. **Track both** `currentMax` and `currentMin` (the largest and smallest products ending at the current index).
2. **At each index:** the new max could be `nums[i]`, `currentMax * nums[i]`, or `currentMin * nums[i]`. Same three candidates for the new min.
3. **If `nums[i]` is negative, swap** `currentMax` and `currentMin` before applying — equivalently, compute both fresh from all three candidates.
4. **Track the global best** as you go.

```java
public int maxProduct(int[] nums) {
    // Step 1
    int currentMax = nums[0];
    int currentMin = nums[0];
    int best = nums[0];

    for (int i = 1; i < nums.length; i++) {
        int x = nums[i];

        // Step 2, 3 — three candidates for both new max and new min
        int candidate1 = currentMax * x;
        int candidate2 = currentMin * x;
        int newMax = Math.max(x, Math.max(candidate1, candidate2));
        int newMin = Math.min(x, Math.min(candidate1, candidate2));

        currentMax = newMax;
        currentMin = newMin;

        // Step 4
        best = Math.max(best, currentMax);
    }
    return best;
}
```

**Trace on `[2, 3, -2, 4]`:**

```
i:           0    1    2    3
nums:        2    3   -2    4
currentMax:  2    6   -2    4
currentMin:  2    3  -12   -48
best:        2    6    6    6
                              ↑
                          answer = 6
```

At `i = 2` (the `-2`), notice how `currentMin` becomes the most extreme negative: `-12 = 6 * -2`. If `i = 3` had been `-5` instead of `4`, `currentMax` would have become `-12 * -5 = 60`, beating everything before. That's why we track min.

> **Why must we evaluate `x` itself in the max?** Because a zero in `nums` resets the chain — at that point the best subarray ending here is just `x` (a fresh start), not the product-through-zero (which is 0).

> 🧩 **Try these (after this walkthrough):**
> - ✅ **LC 152** Maximum Product Subarray — code from memory
> - 🟡 **LC 1567** Maximum Length of Subarray With Positive Product — same flavor; track lengths of positive/negative running runs

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

> *"Could a beginner write code that compiles, runs, doesn't crash, but produces wrong output?"* If yes — it's a silent bug — it goes here.

---

**Integer overflow on sum / product accumulators.**

```java
// ❌ overflows for n = 10⁵, values up to 10⁵
int sum = 0;
for (int x : nums) {
    sum += x;
}

// ✅
long sum = 0;
for (int x : nums) {
    sum += x;
}
```

Full coverage: **`DSA/DeepDive/integer-overflow-and-limits.md`**.

---

**Subtraction in a comparator overflows for `Integer.MIN_VALUE`.**

```java
// ❌
Arrays.sort(boxed, (a, b) -> a - b);

// ✅
Arrays.sort(boxed, (a, b) -> Integer.compare(a, b));
```

---

**`list.remove(int)` removes by index; `list.remove(Integer)` removes by value.**

```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5));

// ❌ removes INDEX 2 → list becomes [1, 2, 4, 5]
list.remove(2);

// ✅ removes VALUE 2 → list becomes [1, 3, 4, 5]
list.remove(Integer.valueOf(2));
```

---

**Printing a primitive array with `arr.toString()` gives garbage.**

```java
int[] arr = {1, 2, 3};

// ❌ prints [I@1d44bcfa
System.out.println(arr);

// ✅ prints [1, 2, 3]
System.out.println(Arrays.toString(arr));
```

---

**Modifying an array while iterating with for-each.**

```java
List<Integer> list = new ArrayList<>(List.of(1, -1, 2, -2));

// ❌ ConcurrentModificationException
for (int x : list) {
    if (x < 0) {
        list.remove(Integer.valueOf(x));
    }
}

// ✅ iterate by index, backwards
for (int i = list.size() - 1; i >= 0; i--) {
    if (list.get(i) < 0) {
        list.remove(i);
    }
}
```

---

**Sliding window with negative numbers — pattern doesn't apply.**

```java
// ❌ sliding window only works when adding more elements can only increase sum
// For "subarray with sum exactly K" allowing negatives, use Prefix Sum + HashMap (Pattern 6).
```

---

**Dutch flag — advancing `mid` after a `nums[mid] == 2` swap.**

```java
// ❌
if (nums[mid] == 2) {
    swap(nums, mid, high);
    high--;
    mid++;        // BUG — swapped-in value never checked
}

// ✅
if (nums[mid] == 2) {
    swap(nums, mid, high);
    high--;
    // do NOT advance mid
}
```

---

**Reading `matrix[0].length` on an empty matrix throws.**

```java
int[][] matrix = new int[0][];

// ❌ NullPointerException — matrix[0] doesn't exist
int n = matrix[0].length;

// ✅
if (matrix.length == 0 || matrix[0].length == 0) {
    return ...;
}
int m = matrix.length;
int n = matrix[0].length;
```

---

**Spiral matrix — forgetting the inner-loop boundary checks.**

```java
// ❌ revisits rows/cols when m != n
for (int c = right; c >= left; c--) {
    result.add(matrix[bottom][c]);
}
bottom--;

// ✅ guard against bottom < top after the top++ earlier in the loop
if (top <= bottom) {
    for (int c = right; c >= left; c--) {
        result.add(matrix[bottom][c]);
    }
    bottom--;
}
```

---

**Storing `int[]` references in a list, then mutating them later.**

```java
List<int[]> result = new ArrayList<>();
int[] tmp = new int[]{1, 2};
result.add(tmp);
tmp[0] = 99;
// result now contains [99, 2] — same reference, not a snapshot

// ✅ snapshot before adding
result.add(new int[]{tmp[0], tmp[1]});
// or  result.add(tmp.clone());
```

---

**`Math.abs(Integer.MIN_VALUE)` returns `Integer.MIN_VALUE`.**

```java
int x = Integer.MIN_VALUE;

// ❌ still negative
int abs = Math.abs(x);

// ✅ promote to long
long abs = Math.abs((long) x);
```

---

**Hidden static state on LeetCode (test cases run in the same JVM).**

```java
// ❌ persists across test cases — answer pollutes
static int count = 0;

// ✅
private int count;
public int solve(...) {
    count = 0;     // reset inside the public entry method
    // ...
}
```

---

**Sorting a `2D int[][]` by column using subtraction.**

```java
// ❌ overflow for extreme values
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

// ✅
Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
```

---

**Confusing "longest subarray" (contiguous) with "longest subsequence" (skips allowed).**

These need different algorithms. Re-read the **Terminology** table whenever the problem statement uses one of these words.

---

## 🗺️ Practice Plan — A Progression That Works

Climb tiers in order. Each tier locks in a family of patterns before the next adds new ones.

> **Reminder of tags:** ✅ Try Now · 🟡 Try after the named prerequisite · 🔴 Reference Only

---

### Tier 1 — Foundational 8 (must be muscle memory)

> If you can solve these from memory in under 15 minutes each, you've earned the right to move on. These are the "warm-up round" of every interview.

1. ✅ **LC 26** Remove Duplicates from Sorted Array — slow/fast pointers (Pattern 2)
2. ✅ **LC 27** Remove Element — same shape, different keep-condition
3. ✅ **LC 283** Move Zeroes — write non-zeros, fill rest with 0
4. ✅ **LC 136** Single Number — XOR trick (Special Topic 2)
5. ✅ **LC 268** Missing Number — XOR or sum formula
6. ✅ **LC 485** Max Consecutive Ones — running counter
7. ✅ **LC 189** Rotate Array — reverse-three-times trick
8. ✅ **LC 121** Best Time to Buy and Sell Stock — Kadane-flavored

⭐ Top 3 to prioritize: **LC 26, LC 283, LC 121** — they map directly to Patterns 2, 2, 8.

---

### Tier 2 — Two Pointers, Sliding Window, Sorting

> Adds Patterns 1, 3, 4, 9 — the converging two-pointer and the variable-window template.

1. ✅ **LC 167** Two Sum II (Sorted) — converging two pointers (Pattern 1)
2. ✅ **LC 11** Container With Most Water — same shape, different move-rule
3. ✅ **LC 75** Sort Colors — Dutch national flag (Pattern 9)
4. ✅ **LC 643** Max Average Subarray I — fixed window (Pattern 3)
5. ✅ **LC 3** Longest Substring Without Repeating — variable window (Pattern 4)
6. ✅ **LC 209** Minimum Size Subarray Sum — variable window
7. ✅ **LC 169** Majority Element — Moore's voting (Pattern 10)
8. ✅ **LC 53** Maximum Subarray — Kadane's (Pattern 8, Walkthrough 1)
9. ✅ **LC 2149** Rearrange Array Elements by Sign — interleaving with two pointers

⭐ Top 3: **LC 75, LC 3, LC 53** — these three are the single most-asked array mediums in onsites.

---

### Tier 3 — Hashing, Prefix Sum, Standard Mediums

> Adds Patterns 5, 6, 7 and the canonical "subarray with sum K" trick. By the end of this tier you can solve roughly 70% of array-tagged mediums.

1. ✅ **LC 1** Two Sum — seen-before HashMap (Pattern 7)
2. ✅ **LC 49** Group Anagrams — frequency-based hashing
3. ✅ **LC 128** Longest Consecutive Sequence — set trick (Special Topic 3, Walkthrough 3)
4. ✅ **LC 560** Subarray Sum Equals K — prefix sum + HashMap (Pattern 6)
5. ✅ **LC 974** Subarray Sums Divisible by K — variation of LC 560
6. ✅ **LC 31** Next Permutation — 4-step algorithm (Special Topic 1, Walkthrough 2)
7. ✅ **LC 229** Majority Element II — Moore's voting (n/3 variant)
8. ✅ **LC 118** Pascal's Triangle — build row by row
9. ✅ **LC 119** Pascal's Triangle II — single row with running formula

⭐ Top 3: **LC 1, LC 560, LC 31** — pattern-defining problems.

---

### Tier 4 — Matrix and Intervals (medium-interview essentials)

> Adds Patterns 12 and 13. These wrap up the medium-interview core. After this tier, you've covered ~90% of array questions a typical SDE-2 / SDE-3 onsite throws.

1. ✅ **LC 73** Set Matrix Zeroes — O(1) space using first row/col as metadata
2. ✅ **LC 48** Rotate Image — transpose + reverse rows (Pattern 12b)
3. ✅ **LC 54** Spiral Matrix — four-boundary walk (Pattern 12c)
4. ✅ **LC 56** Merge Intervals — sort + sweep (Pattern 13, Walkthrough 4)
5. ✅ **LC 57** Insert Interval — variation of LC 56
6. ✅ **LC 15** 3 Sum — sort + converging pointers + duplicate skip (Pattern 1 variant)
7. ✅ **LC 18** 4 Sum — extra loop wrapping 3 Sum
8. ✅ **LC 152** Maximum Product Subarray — Kadane's with min tracking (Walkthrough 5)
9. ✅ **LC 88** Merge Sorted Array — two pointers from the back
10. 🟡 **Subarrays with XOR K** (GFG / Striver V-22) — try after LC 560; same shape with XOR instead of sum

⭐ Top 3: **LC 56, LC 48, LC 15** — universal interview classics.

---

> 🎯 **STOP HERE — Medium-Interview Cutoff** 🎯
>
> If you can solve Tiers 1–4 from memory in under 20 minutes each, you are **ready for any easy/medium array question** in a typical SDE-2 / SDE-3 onsite at FAANG, Walmart, or similar. Move on to other topics (graphs, DP, system design) before grinding Tier 5.
>
> **Why the cutoff?** Tier 5 problems show up in 5% or fewer of interview loops, and they're so distinct that the time-to-mastery ratio is much worse than just learning a brand-new topic.

---

### Tier 5 — Hard / Senior+ (Reference Only) 🔴

> Treat these as bedtime reading. Understand the **shape** of the solution; don't grind them cold. They mostly involve modified merge sort or clever math tricks that aren't transferable.

1. 🔴 **LC 41** First Missing Positive — cyclic sort (Pattern 11); attempt only after Tier 4 is reflex
2. 🔴 **LC 287** Find the Duplicate Number — Floyd's cycle detection on values; conceptually heavy
3. 🔴 **Count Inversions** (Striver V-26) — modified merge sort (Pattern 14)
4. 🔴 **LC 493** Reverse Pairs (Striver V-27) — modified merge sort (Pattern 14)
5. 🔴 **Merge Sorted Arrays Without Extra Space** (Striver V-24, gap method) — Shell-sort flavor
6. 🔴 **Find Missing AND Repeating** (Striver V-25) — math approach or XOR partition
7. 🔴 **LC 4** Median of Two Sorted Arrays — binary search on partition; senior+ classic
8. 🔴 **LC 315** Count of Smaller Numbers After Self — modified merge sort or Fenwick tree

---

### How to use this plan

- **Pace:** 2–3 problems per day takes you through Tiers 1–4 in about 2 weeks
- **When stuck:** time-box at 25 minutes. If still stuck, read the editorial, **don't accept-paste — close it and rewrite from understanding**
- **Revision:** after finishing a tier, redo problems 1–3 from memory before moving on
- **The honest victory criterion:** if you can solve Tiers 1–4 from memory under 20 minutes each, every interview easy/medium array question is in your reach. Tier 5 is bonus.

> **Lesson learned the hard way (May 2026):** the temptation to "knock out LC 4 (Median of Two Sorted Arrays)" before mastering Tiers 1–3 is real — it's a famous problem and feels prestigious. Resist it. The intuition for it builds on the binary search pattern you'll cover in a different deep dive; cold attempts cost an hour of frustration with zero transferable insight.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| HashMap full reference (used in Patterns 6, 7) | `DSA/Reference/hashmap-section-updated.md` |
| HashSet reference (used in Special Topic 3 — LC 128) | `DSA/Reference/set-section-updated.md` |
| String operations (overlap with subarray patterns on chars) | `DSA/Reference/string-operations-reference.md` |
| Lambdas (used in `Arrays.sort` comparator examples) | `DSA/Reference/lambdas-for-dsa-reference.md` |
| Integer overflow & limits (long for sums, comparator traps) | `DSA/DeepDive/integer-overflow-and-limits.md` |
| Code style refactor recipes | `DSA/Reference/code-style-for-dsa-reference.md` |
| Recursion fundamentals (foundation for Pattern 14 — modified merge sort) | `DSA/DeepDive/recursion-fundamentals.md` |
| Backtracking (for subsets / permutations — NOT subarrays) | `DSA/DeepDive/backtracking-fundamentals.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | Initial version. Curriculum aligned to Striver's Arrays Playlist (28 videos, V-1 through V-28). 14 named patterns. 5 worked walkthroughs. Tier 1–4 = medium-interview essentials; Tier 5 = Senior+. Mental-model framing ("index space vs value space") is this doc's pedagogical contribution. |

---

## 🧾 TL;DR — One-Page Summary

- **Array** = contiguous memory; `arr[i]` is O(1) random access; `arr.length` is a field, not a method
- **Mental model:** an array has **two spaces — index and value**. Most clever tricks (cyclic sort, marking by negation, set-matrix-zeros) encode one space into the other
- **Subarray ≠ Subsequence ≠ Subset.** Subarray = contiguous. Subsequence = ordered with skips. Subset = order-free. Wrong pick = wrong algorithm.
- **The universal skeleton:** validate input → set up state → iterate (single pass / two pointers / window) → return derived answer
- **14 patterns:** Two Pointers (converging / same-direction), Sliding Window (fixed / variable), Prefix Sum (raw / with HashMap), Hashing, Kadane's, Dutch flag, Moore's voting, Cyclic sort, Matrix tricks, Intervals, Modified merge sort 🔴
- **Pick the pattern in under 60 seconds:** keyword → pattern → template. "Subarray sum K positives" → window; "subarray sum K any sign" → prefix+map; "majority > n/2" → Moore's; "sort 0/1/2" → Dutch flag; "max sum subarray" → Kadane's
- **6 universal habits:** validate input → cache length → `long` for sums → `Arrays.toString` for debugging → `Integer.compare` over subtraction → verbalize the invariant
- **Tier 1 (Foundational 8) you must master:** LC 26, 27, 283, 136, 268, 485, 189, 121
- **Top 3 medium classics:** LC 75 (Dutch flag), LC 3 (variable window), LC 53 (Kadane's)
- **Most "silent bug" sources:** int overflow on sums, subtraction in comparators, `list.remove(int)` vs `Integer`, sliding window with negatives, advancing `mid` after a Dutch-flag swap-to-back
- **Medium-interview cutoff is end of Tier 4.** Tier 5 (modified merge sort, gap method, LC 4) is Senior+ — read for shape, don't grind cold.
- **Lesson learned (May 2026):** the time-to-mastery ratio for Tier 5 is so bad you're better off learning a new topic (graphs, DP) than grinding hard array variants.
