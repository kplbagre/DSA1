# Binary Search — Fundamentals

> **For interview prep:** Master every binary search variant — classic, bisect, rotated array, answer space, and 2D matrix — so you can write the correct template under pressure and never lose a problem to an off-by-one boundary bug.

---

> **Curriculum alignment:** This deep-dive synthesizes:
> - **Striver's Binary Search Series** (videos covering sorted array search, bisect left/right, rotated array, search on answer space, 2D matrix)
> - **LeetCode Problem Editorials** (LC 704, 34, 153, 33, 875, 1552, 74, 410)
> - **GeeksforGeeks binary search fundamentals** (loop invariant proofs, off-by-one analysis)
>
> **Credit:** Core patterns and invariant analysis from Striver + LeetCode editorials. Phase 1 decision framework, answer-space mental model, and gem-problem walkthrough are this doc's contribution.
>
> **Companion:** Interview playbook at `DSA/Interview/Playbooks/binary-search.md` (drill before a same-day interview). This doc is the learn-from-zero reference.

---

## 📋 Section Index

| Section | Topic |
| --- | --- |
| [🎯 Goal](#goal) | What you can do after reading this |
| [🚦 Difficulty Tags](#difficulty-tags) | ✅ 🟡 🔴 ratings explained |
| [🌲 What Is Binary Search?](#what-is-bs) | Monotonicity contract — the one idea you need |
| [📖 Terminology](#terminology) | lo, hi, mid, left-bias, right-bias, invariant |
| [🔨 Phase 1 — Three Decisions](#setup) | Boundary choice, mid formula, shrink rule |
| [🧠 Mental Model](#mental-model) | Eliminating half the universe each step |
| [🎨 Style Habits](#style-habits) | lo+hi>>1, lo<=hi vs lo<hi, when to use bisect variants |
| [🧭 Patterns](#patterns) | Classic, bisect-left/right, rotated, answer-space, matrix |
| [🔬 Worked Walkthroughs](#walkthroughs) | Problems traced step by step |
| [⚠️ Gotchas](#gotchas) | Infinite loop, off-by-one, wrong shrink direction |
| [🗺️ Practice Plan](#practice-plan) | Tiered progression |
| [🧾 TL;DR](#tldr) | One-page summary for revision day |
| [🔄 Changelog](#changelog) | Doc history |


---

<a id="goal"></a>
## 🎯 Why You're Reading This

After this deep dive, you will:

- **Recognize all 5 binary search variants** from the problem wording alone — in under 30 seconds
- **Write the correct template every time** — the `lo <= hi` vs `lo < hi` decision is the single biggest source of binary search bugs; you will never get it wrong again
- **Handle answer-space search** — the hardest variant: "minimize the maximum X" or "maximize the minimum X" — using the feasibility check pattern
- **Know what breaks your code** — 6 silent bugs that compile and pass small tests but fail on large inputs or edge cases

By the end, LC 704 (Classic), LC 34 (Bisect), LC 875 (Koko), and the gem-gap problem will feel like natural applications, not magic.

---

<a id="difficulty-tags"></a>
## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point in the doc | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs a later section in this same doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc | Read problem + editorial for awareness; don't attempt cold |

---

<a id="what-is-bs"></a>
## 🌲 What Is Binary Search?

**Binary search** (also called *divide and conquer search*) finds a target by repeatedly halving the search space. Instead of checking every candidate, it exploits a **monotonic property**: a condition that flips exactly once from false → true (or true → false) across the search space.

**Simplest example:**

```java
// Find 7 in [1, 3, 5, 7, 9]
// Step 1: mid = 5.  7 > 5 → discard left.  Remaining: [7, 9]
// Step 2: mid = 7.  7 == 7 → found at index 3.
// Without binary search: checked every element = O(n). With: O(log n).
```

**Why O(log n):** Each step halves the search space. Starting from n, after k steps the space is n / 2^k. When n / 2^k = 1, k = log₂(n). Every doubling of input adds just one more step.

---

<a id="terminology"></a>
## 📖 Terminology Table

| Term | Meaning | Interview context |
| --- | --- | --- |
| **lo / hi** | Current lower and upper bounds of the search space (both inclusive unless stated) | "Always confirm: are your bounds inclusive?" |
| **mid** | The candidate index computed as `lo + (hi - lo) / 2` | "Never use `(lo + hi) / 2` — it overflows for large indices" |
| **Search space** | The range of values or indices the search hasn't yet eliminated | "Goal: shrink search space to 0 or 1 element" |
| **Monotonic condition** | A boolean property that is false for all values below a threshold and true for all values at or above it (or vice versa) | "Binary search only works when the answer space is monotone" |
| **Feasibility check** | A helper function `boolean canAchieve(int mid)` used in answer-space binary search | "The check runs O(n); binary search calls it O(log n) times → total O(n log n)" |
| **Bisect left** | Find the leftmost position where a condition holds (first occurrence, insert-left) | "`hi = mid` when condition holds, `lo = mid + 1` otherwise" |
| **Bisect right** | Find the rightmost position where a condition holds (last occurrence, insert-right) | "`lo = mid + 1` when condition holds, return `lo - 1`" |
| **Answer space** | The range of possible answer values (not array indices) over which binary search runs | "e.g., for Koko: answer space is speeds 1..max(piles)" |
| **Pivot** | The index in a rotated array where the rotation break occurs | "Left of pivot: values ≥ arr[0]. Right of pivot: values < arr[0]." |

---

<a id="setup"></a>
## 🔨 Phase 1 — Three Decisions Before You Write the Loop

> **Binary search bugs are almost always Phase 1 failures** — wrong `lo`/`hi`, wrong loop terminator, or wrong mid-update. Make these three decisions before touching the loop body.

---

### Decision 1 — What are `lo` and `hi`?

| Problem type | lo | hi | Notes |
| --- | --- | --- | --- |
| **Sorted array, find target** | `0` | `n - 1` | Both indices inclusive |
| **Bisect / insert position** | `0` | `n` | `n` is valid (insert after last element) |
| **Answer space (minimize X)** | smallest possible answer | largest possible answer | e.g., Koko: `lo=1`, `hi=max(piles)` |
| **Rotated array** | `0` | `n - 1` | Same as classic; logic shifts inside loop |
| **2D matrix** | `0` | `rows*cols - 1` | Treat matrix as flattened 1D array |

---

### Decision 2 — Which loop terminator?

**This is the #1 binary search bug source.** Choose based on what the loop should return.

```
DECISION TABLE — lo <= hi  vs  lo < hi

┌──────────────────────────────┬──────────────────────────┬─────────────────────────────┐
│ Question                     │ while lo <= hi           │ while lo < hi               │
├──────────────────────────────┼──────────────────────────┼─────────────────────────────┤
│ What does loop return?       │ Explicit return inside   │ return lo (== hi) after loop │
│ When does loop exit?         │ lo > hi (range empty)    │ lo == hi (converged to 1)   │
│ Target may not exist?        │ ✅ Yes → return -1        │ ❌ Always returns an index   │
│ Finding a bound/minimum?     │ ❌ Awkward               │ ✅ Natural                   │
│ Mid update when found/equal  │ return mid               │ hi=mid  or  lo=mid+1        │
│ Wrong mid update causes?     │ hi=mid → infinite loop   │ hi=mid-1 → skips the answer │
└──────────────────────────────┴──────────────────────────┴─────────────────────────────┘
```

**Rule:** "Searching for an exact value that might not exist" → `lo <= hi`. "Finding a position/bound that always exists" → `lo < hi`.

---

### Decision 3 — Which mid formula?

```java
// Standard (lower-mid bias): use in 99% of cases
int mid = lo + (hi - lo) / 2;

// Upper-mid bias: ONLY needed for "maximize" answer-space searches
// where lo = mid would cause infinite loop when hi = lo + 1
int mid = lo + (hi - lo + 1) / 2;
```

**Pre-flight checklist:**

```
□ Is the problem monotonic? (sorted array OR answer space with one flip point)
□ lo <= hi (exact match, may return -1) or lo < hi (bound, always returns lo)?
□ Are lo and hi the correct initial bounds for this problem type?
□ Mid update: classic → return mid, bisect → hi=mid, answer-space → hi=mid or lo=mid+1
□ Overflow-safe mid: lo + (hi - lo) / 2, not (lo + hi) / 2
□ Maximize variant: upper-mid bias needed?
```

---

<a id="mental-model"></a>
## 🧠 Mental Model — Eliminating Half the Universe

> **The core idea:** Binary search works not because the array is sorted, but because the search space has a **monotonic property** — there is a SINGLE flip point. Below it: condition fails. Above it: condition holds. You binary-search for that flip point.

**Worked example — classic:**

Array `[1, 3, 5, 7, 9, 11, 13]`, target = `11`.

Start: entire array is the search space. Monotonic property: `arr[i] < 11` is true for indices 0-4, false for indices 5-6.

Each step eliminates the half that the monotonic property tells you can't contain the answer.

### 🎨 Visual — Search Space Halving

```
Binary search = HALF the search space at each step

Array:  [1,  3,  5,  7,  9,  11,  13]    target = 11
         0   1   2   3   4   5    6
         lo                       hi

Step 1: mid = 3, arr[3] = 7 < 11  →  lo = 4   (discard left half)
        [·   ·   ·   · | 9,  11,  13]
                         lo           hi

Step 2: mid = 5, arr[5] = 11 == 11  →  FOUND at index 5  ✅

Without binary search: 6 comparisons to find 11 (scan left-to-right).
With binary search:    2 comparisons.  n=7, log₂(7) ≈ 2.8. ✓

KEY INVARIANT: At every step, the target is guaranteed to be inside [lo..hi].
               Every comparison eliminates AT LEAST half the remaining space.
               After at most ⌈log₂(n)⌉ steps, lo == hi == the answer.
```

**Worked example — answer space:**

Koko has piles `[3, 6, 7, 11]` and `h = 8` hours. Find minimum eating speed.

The monotonic property here is NOT in the array. It's in the answer space (speeds 1..11): speeds below 4 are infeasible (not enough hours), speeds 4 and above are feasible. Binary search finds that flip point.

This is the key insight: **binary search doesn't need an array. It needs a monotone condition.**

---

<a id="style-habits"></a>
## 🎨 Style Habits — Build These From Day 1

> Some habits apply to every binary search you write. Others only matter for specific variants. **Master the universal ones now**; skim the context-specific ones and revisit them when you hit the pattern.

---

### 🌐 Universal Habits (apply everywhere — start using today)

#### Habit 1 — Always use `lo + (hi - lo) / 2`

Never `(lo + hi) / 2`. When `lo = 1_000_000_000` and `hi = 2_000_000_000`, the sum overflows `int`.

```java
// ❌ Wrong — overflows when lo + hi > Integer.MAX_VALUE
int mid = (lo + hi) / 2;

// ✅ Right — lo is the base; offset (hi - lo) is always ≤ Integer.MAX_VALUE
int mid = lo + (hi - lo) / 2;
```

Full explanation in `DSA/DeepDive/integer-overflow-and-limits.md`.

---

#### Habit 2 — Decide the loop terminator BEFORE writing the body

The loop body looks almost identical for `lo <= hi` and `lo < hi`. If you choose wrong and code for 5 minutes, unraveling is painful. The decision takes 5 seconds. Make it first.

---

#### Habit 3 — When loop exits, verify what `lo` points to

```java
// After while lo < hi — lo == hi, and lo IS the answer.
// After while lo <= hi — lo > hi. The target was either returned inside
//   or does not exist. Do NOT use lo as the answer index blindly.
```

---

#### Habit 4 — Mentally test on a 2-element array

Two elements is the smallest case where the mid formula and boundary updates can create infinite loops. Always trace `[a, b]` once before submitting.

---

### 🔧 Context-Specific Habits (click as you encounter each pattern)

> These won't matter on your first 5 binary search problems. **Skim now, revisit when you hit the pattern.**

#### Habit 5 — For bisect variants, never `return mid` when `arr[mid] == target`

```java
// ❌ Wrong — returns ANY occurrence, not first/last
if (arr[mid] == target) {
    return mid;
}

// ✅ Right — keep searching to find the FIRST occurrence
if (arr[mid] >= target) {
    hi = mid;  // mid could be the answer, so keep it
}
```

#### Habit 6 — For answer-space search, define feasibility as a named helper

```java
// ❌ Inline feasibility — hard to read, hard to debug
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    int hours = 0;
    for (int p : piles) {
        hours += (p + mid - 1) / mid;
    }
    if (hours <= h) { hi = mid; } else { lo = mid + 1; }
}

// ✅ Named helper — clean, testable, matches your verbal explanation
private boolean canFinish(int[] piles, int speed, int h) {
    int hours = 0;
    for (int pile : piles) {
        hours += (pile + speed - 1) / speed;
    }
    return hours <= h;
}
// In the loop:
if (canFinish(piles, mid, h)) { hi = mid; } else { lo = mid + 1; }
```

#### Habit 7 — For rotated array, always identify which half is sorted FIRST

```java
if (arr[lo] <= arr[mid]) {
    // LEFT half [lo..mid] is sorted — check if target is in it
} else {
    // RIGHT half [mid+1..hi] is sorted — check if target is in it
}
```

#### Habit 8 — For "maximize" answer-space, use upper-mid to avoid infinite loop

```java
// When lo = hi - 1 and lo = mid (lower-mid), lo never advances → infinite loop
// Fix: upper-mid bias so mid advances even when hi = lo + 1
int mid = lo + (hi - lo + 1) / 2;
```

---

### 🔄 Method Fallbacks — When You Forget the Shorthand

**Ceiling division `(a + b - 1) / b`**

```java
// What it does: divide a by b, rounding up (ceiling)
// Concise:
int hours = (int) Math.ceil((double) pile / speed);

// 🔄 Fallback — always works, no floating point:
int hours = (pile + speed - 1) / speed;
```

**`Arrays.binarySearch()` — know it exists, but avoid in interviews**

```java
// What it does: finds target in sorted array; returns negative if not found.
// Problem: returns ANY index of target (not first/last), and return value for
// "not found" is -(insertionPoint + 1) — confusing under pressure.
// 🔄 Write your own — cleaner, controlled, and the interviewer sees you know it.
```

> **Quick recap of universal habits:** overflow-safe mid → decide terminator before writing → check what lo means after loop exits → test 2-element case mentally. Those four cover ~90% of binary search bugs.

---

<a id="patterns"></a>
## 🧭 Patterns

---

### Pattern 1 — Classic Binary Search

**When you'll see this pattern:**
- LC 704 Binary Search — direct application, sorted array
- LC 374 Guess Number Higher or Lower — black-box comparison, same loop
- Real-world: sorted log search, database B-tree lookup

**Problem motivation — concrete example:**

"Given a sorted array `arr` and a target, return the index of the target or -1 if not found."

Example: `arr = [1, 3, 5, 7, 9]`, target = `7` → output `3`

**Naive approach (and why it fails):**

```java
// Linear scan: check every element
// Time: O(n), Space: O(1)
// Wastes the sorted property entirely — every comparison tells you nothing
// about the other n-1 elements. Binary search uses sorted order to eliminate half.
```

**Why this pattern solves it:**

If `arr[mid] < target`, the target CANNOT be in `[lo..mid]` because the array is sorted — everything left of mid is smaller. So we set `lo = mid + 1`. One comparison, half the space gone.

**Steps in plain English:**

1. **Set bounds** — `lo = 0`, `hi = n - 1` (both inclusive).
2. **Loop while `lo <= hi`** — search space is non-empty.
3. **Compute mid** — `lo + (hi - lo) / 2` (overflow-safe).
4. **Three-way compare** — found: return mid. Too small: lo = mid + 1. Too large: hi = mid - 1.
5. **Return -1** — loop exited without finding target.

```java
public int search(int[] arr, int target) {
    // Step 1 — set inclusive bounds
    int lo = 0;
    int hi = arr.length - 1;

    // Step 2 — loop while search space non-empty
    while (lo <= hi) {
        // Step 3 — overflow-safe mid
        int mid = lo + (hi - lo) / 2;

        // Step 4 — three-way compare
        if (arr[mid] == target) {
            return mid;
        } else if (arr[mid] < target) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }

    // Step 5 — target not found
    return -1;
}
```

**Why this works:** Each comparison eliminates the half that cannot contain the target. After ⌈log₂(n)⌉ iterations, either target is found or the search space is empty.

**Complexity:** O(log n) time, O(1) space.

**🎨 Visual — Classic Binary Search: `[1,3,5,7,9]`, target=7**

```
lo=0, hi=4

STEP 1: mid=2, arr[2]=5 < 7  →  lo=3
        [·  ·  · | 7, 9]
                   lo   hi

STEP 2: mid=3, arr[3]=7 == 7  →  return 3  ✅

Answer: 3

KEY INVARIANT: target is always in [lo..hi]. When arr[mid] < target,
               target cannot be at or left of mid (sorted array).
               When arr[mid] > target, target cannot be at or right of mid.
```

---

> 🧩 **Try these:**
> - ✅ LC 704 — Binary Search — the template above is the answer
> - ✅ LC 374 — Guess Number Higher or Lower — same loop, replace `arr[mid]` with `guess(mid)` return value

---

### Pattern 2 — Bisect Left / Bisect Right (First & Last Position)

**When you'll see this pattern:**
- LC 34 Find First and Last Position — return `[firstIdx, lastIdx]` of target in sorted array
- LC 35 Search Insert Position — where would target go if inserted?
- LC 278 First Bad Version — first version where `isBadVersion(mid) == true`
- Real-world: range queries, lower_bound / upper_bound in sorted data

**Problem motivation — concrete example:**

"Given sorted array `arr = [1, 3, 5, 5, 5, 7, 9]` and `target = 5`, return the first and last index of 5."

Output: `[2, 4]`

**Naive approach (and why it fails):**

```java
// Scan left-to-right for first occurrence, right-to-left for last.
// Time: O(n), Space: O(1)
// Correct, but ignores sorted property. With n=10^6, needs up to 10^6 checks.
// Binary search: O(log n) = ~20 checks. Needed for large inputs.
```

**Why this pattern solves it:**

When `arr[mid] == target`, classic binary search returns immediately — it can't tell whether this is the first or last occurrence. The key change: **when `arr[mid] == target`, don't return. Bias the boundary.** Move `hi = mid` (keep searching left for first) or `lo = mid + 1` (keep searching right for last). The loop converges to exactly the bound you want.

**Steps in plain English (bisect-left / first occurrence):**

1. **Set bounds** — `lo = 0`, `hi = n - 1`.
2. **Loop while `lo < hi`** — converges when lo == hi.
3. **Compute mid** — lower-mid: `lo + (hi - lo) / 2`.
4. **If `arr[mid] >= target`** — target could be at mid or to the left → `hi = mid`.
5. **If `arr[mid] < target`** — target is strictly to the right → `lo = mid + 1`.
6. **After loop** — `lo == hi`, check if `arr[lo] == target`. If yes, that's the first index.

```java
public int bisectLeft(int[] arr, int target) {
    // Step 1 — set bounds
    int lo = 0;
    int hi = arr.length - 1;

    // Step 2 — converge while range has more than 1 element
    while (lo < hi) {
        // Step 3 — lower-mid bias
        int mid = lo + (hi - lo) / 2;

        // Step 4 — mid could BE the first occurrence; keep it
        if (arr[mid] >= target) {
            hi = mid;
        // Step 5 — target definitely to the right
        } else {
            lo = mid + 1;
        }
    }

    // Step 6 — lo == hi == first occurrence (if it exists)
    return (arr[lo] == target) ? lo : -1;
}
```

**Bisect-right (last occurrence):** When `arr[mid] <= target`, `lo = mid + 1`; when `arr[mid] > target`, `hi = mid`. After loop, check `arr[lo - 1] == target`.

**Complexity:** O(log n) time, O(1) space.

**🎨 Visual — Bisect Left: `[1,3,5,5,5,7,9]`, target=5**

```
Indices:   0  1  2  3  4  5  6
Array:    [1, 3, 5, 5, 5, 7, 9]   target=5
           lo                hi

STEP 1: mid=3, arr[3]=5 >= 5  →  hi=3   (5 could be at 3, keep it)
           lo          hi
          [1, 3, 5, 5 | ·  ·  ·]

STEP 2: mid=1, arr[1]=3 < 5   →  lo=2
                 lo    hi
          [·  ·  5, 5 | ·  ·  ·]

STEP 3: mid=2, arr[2]=5 >= 5  →  hi=2
              lo=hi=2   →  loop exits

arr[2]=5 == 5  →  return 2  ✅

KEY INVARIANT: hi = mid when arr[mid] >= target (first occurrence could BE mid).
               lo = mid + 1 when arr[mid] < target (first occurrence is strictly right).
               Loop exits when lo == hi, which IS the first occurrence.
```

---

> 🧩 **Try these:**
> - ✅ LC 704 — Classic Binary Search — warm-up before bisect
> - ✅ LC 278 — First Bad Version — `isBadVersion(mid)` is the bisect-left condition
> - ✅ LC 35 — Search Insert Position — bisect-left returning lo even if target not found
> - 🟡 **Try after Pattern 2** — LC 34 — Find First and Last Position (run bisect-left + bisect-right)

---

### Pattern 3 — Rotated Sorted Array

**When you'll see this pattern:**
- LC 153 Find Minimum in Rotated Sorted Array — no duplicates
- LC 33 Search in Rotated Sorted Array — find target
- LC 154 Find Minimum with Duplicates 🔴 — same idea; duplicates force O(n) worst case

**Problem motivation — concrete example:**

"Array `[4, 5, 6, 7, 0, 1, 2]` was sorted then rotated. Find the minimum."

Output: `0` (at index 4)

**Naive approach (and why it fails):**

```java
// Linear scan for the smallest element.
// Time: O(n), Space: O(1)
// Ignores the sorted structure — each half IS sorted even after rotation.
// Binary search exploits this to cut the space in half each step.
```

**Why this pattern solves it:**

A rotated sorted array is **two sorted halves**. At any mid point, **at least one half is always sorted** (no overlap is possible). Key insight: identify which half is sorted, check if the target/minimum lies in that range, then eliminate the half where it cannot be.

**Steps in plain English (find minimum, no duplicates):**

1. **Set bounds** — `lo = 0`, `hi = n - 1`.
2. **Loop while `lo < hi`** — converges to the minimum.
3. **Compute mid** — `lo + (hi - lo) / 2`.
4. **If `arr[mid] > arr[hi]`** — the minimum is in the RIGHT half (left half is sorted and min is not there) → `lo = mid + 1`.
5. **Otherwise** — the minimum is at or to the LEFT of mid → `hi = mid`.
6. **Return `arr[lo]`** — converged at minimum.

```java
public int findMin(int[] arr) {
    // Step 1 — set bounds
    int lo = 0;
    int hi = arr.length - 1;

    // Step 2 — converge to minimum
    while (lo < hi) {
        // Step 3 — overflow-safe mid
        int mid = lo + (hi - lo) / 2;

        // Step 4 — arr[mid] > arr[hi] means pivot (min) is to the RIGHT
        if (arr[mid] > arr[hi]) {
            lo = mid + 1;
        // Step 5 — min is at mid or to the left
        } else {
            hi = mid;
        }
    }

    // Step 6 — lo == hi == minimum index
    return arr[lo];
}
```

**Complexity:** O(log n) time, O(1) space.

**🎨 Visual — Find Min in Rotated Array: `[4,5,6,7,0,1,2]`**

```
Array:  [4, 5, 6, 7, 0, 1, 2]
         lo         mid      hi

STEP 1: mid=3, arr[3]=7 > arr[6]=2
        → pivot (min) is in RIGHT half → lo=4
        [·  ·  ·  ·  0, 1, 2]
                     lo      hi

STEP 2: mid=5, arr[5]=1 ≤ arr[6]=2
        → min could be at mid or left → hi=5
        [·  ·  ·  ·  0, 1 | ·]
                     lo  hi

STEP 3: mid=4, arr[4]=0 ≤ arr[5]=1
        → min could be at mid or left → hi=4
        lo=hi=4  →  loop exits

return arr[4] = 0  ✅

KEY INVARIANT: If arr[mid] > arr[hi], the left half [lo..mid] is fully sorted
               and the rotation point (minimum) must be in [mid+1..hi].
               If arr[mid] ≤ arr[hi], the right half [mid..hi] is sorted
               and the minimum is in [lo..mid] (mid itself might be it).
```

---

> 🧩 **Try these:**
> - ✅ LC 153 — Find Minimum in Rotated Sorted Array — the template above
> - 🟡 **Try after Pattern 3** — LC 33 — Search in Rotated Array (extends Pattern 3: after identifying sorted half, check if target is in that range)
> - 🔴 LC 154 — Find Minimum with Duplicates — `arr[mid] == arr[hi]` forces `hi--`; worst case O(n)

---

### Pattern 4 — Binary Search on Answer Space ⭐

**When you'll see this pattern:**
- LC 875 Koko Eating Bananas — minimize speed such that all piles eaten in h hours
- LC 1011 Capacity to Ship Packages — minimize ship capacity to ship all in d days
- LC 1552 Magnetic Force Between Two Balls — maximize minimum force (gap)
- LC 410 Split Array Largest Sum — minimize the maximum subarray sum
- Gem gap problem — minimize the maximum adjacent gap after K removals from sorted array

**Problem motivation — concrete example:**

"Koko has `piles = [3, 6, 7, 11]` and `h = 8` hours. What is the minimum eating speed?"

Output: `4`

**Naive approach (and why it fails):**

```java
// Try every possible speed from 1 to max(piles).
// For each speed, simulate: count hours needed.
// Time: O(max(piles) * n) — up to 10^9 * 10^4 = too slow.
// Key observation: the feasibility function is MONOTONE.
// speeds 1,2,3 → infeasible. speeds 4..11 → feasible.
// This means we can binary search the speed instead of trying each one.
```

**Why this pattern solves it:**

The feasibility function `canFinish(speed)` is monotone: if speed k is feasible, speed k+1 is also feasible (eating faster only helps). This one-flip property makes the answer space searchable by binary search. Binary search calls the O(n) feasibility check O(log(max)) times → total O(n log(max)).

**Steps in plain English:**

1. **Define the answer space** — `lo = smallest possible answer`, `hi = largest possible answer`.
2. **Loop while `lo < hi`** — converges to the minimum feasible answer.
3. **Compute mid** — the candidate answer to test.
4. **Run feasibility check** — `canAchieve(mid)`: can you achieve the goal with mid as the limit?
5. **If feasible** — mid might be the answer or there's a smaller one → `hi = mid`.
6. **If infeasible** — mid is too small, need larger → `lo = mid + 1`.
7. **Return `lo`** — minimum feasible answer.

```java
public int minEatingSpeed(int[] piles, int h) {
    // Step 1 — answer space: min speed=1, max speed=max(piles)
    int lo = 1;
    int hi = Arrays.stream(piles).max().getAsInt();

    // Step 2 — converge to minimum feasible speed
    while (lo < hi) {
        // Step 3 — candidate speed to test
        int mid = lo + (hi - lo) / 2;

        // Steps 4-6 — feasibility check decides direction
        if (canFinish(piles, mid, h)) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }

    // Step 7 — lo == minimum eating speed
    return lo;
}

private boolean canFinish(int[] piles, int speed, int h) {
    int hours = 0;
    for (int pile : piles) {
        // Ceiling division: (pile + speed - 1) / speed
        hours += (pile + speed - 1) / speed;
    }
    return hours <= h;
}
```

**Why this works:** The answer space has exactly one flip point. `canFinish` is false below it, true at and above it. Binary search finds that flip point in O(log(hi-lo)) calls. Each call costs O(n). Total: O(n log(max)).

**Complexity:** O(n log(max)) time, O(1) space.

**🎨 Visual — Answer Space Monotonicity: Koko `piles=[3,6,7,11]`, `h=8`**

```
Answer space:  speed = 1  2  3  4  5  6  7  8  9  10  11
Feasible?         ✗   ✗  ✗  ✓  ✓  ✓  ✓  ✓  ✓  ✓   ✓
                           │
                           └── flip point at speed=4  (the MINIMUM we want)

Binary search trace:
  lo=1, hi=11

  STEP 1: mid=6, canFinish(6)? hours=1+1+2+2=6 ≤ 8  →  feasible → hi=6
  STEP 2: lo=1, hi=6, mid=3, canFinish(3)? hours=1+2+3+4=10 > 8 → infeasible → lo=4
  STEP 3: lo=4, hi=6, mid=5, canFinish(5)? hours=1+2+2+3=8 ≤ 8  →  feasible → hi=5
  STEP 4: lo=4, hi=5, mid=4, canFinish(4)? hours=1+2+2+3=8 ≤ 8  →  feasible → hi=4
  lo=hi=4  →  return 4  ✅

KEY INVARIANT: the feasibility function flips EXACTLY ONCE (false→true for "minimize"
               problems, true→false for "maximize" problems). Binary search homes in
               on that flip point without testing every candidate.
```

**For "maximize minimum" problems:** Flip the feasibility direction and use upper-mid.

```java
// "Maximize minimum gap" → feasible means gap ≥ mid is achievable
// If feasible, try larger (lo = mid). If infeasible, reduce (hi = mid - 1).
// Upper-mid bias prevents infinite loop when hi = lo + 1.
while (lo < hi) {
    int mid = lo + (hi - lo + 1) / 2;  // upper-mid
    if (feasible(mid)) {
        lo = mid;    // mid works, try larger
    } else {
        hi = mid - 1;  // mid too large, reduce
    }
}
return lo;
```

---

> 🧩 **Try these:**
> - ✅ LC 875 — Koko Eating Bananas — the template above
> - 🟡 **Try after Pattern 4** — LC 1011 — Capacity to Ship Packages (same structure, different feasibility check)
> - 🟡 **Try after Pattern 4** — LC 1552 — Magnetic Force Between Two Balls (maximize minimum → flip direction + upper-mid)
> - 🔴 LC 410 — Split Array Largest Sum — same P4 skeleton but partition-based feasibility

---

### Pattern 5 — 2D Matrix Binary Search

**When you'll see this pattern:**
- LC 74 Search a 2D Matrix — each row sorted, last element of row < first of next row
- LC 240 Search a 2D Matrix II — each row and column sorted (different algorithm!)

**Problem motivation — concrete example:**

"Matrix `[[1,3,5,7],[10,11,16,20],[23,30,34,60]]`, target = 3. Does 3 exist?"

Output: `true`

**Naive approach (and why it fails):**

```java
// Linear scan: check every cell.
// Time: O(m * n), Space: O(1)
// Wastes the sorted structure. The matrix is globally sorted (row-major order).
// Flatten to 1D → apply classic binary search → O(log(m*n)).
```

**Why this pattern solves it:**

LC 74's matrix is globally sorted: if you read it row by row (left→right, top→bottom), every element is in increasing order. Treat it as a 1D sorted array of length `m * n`. Convert between 1D index `i` and 2D index using: `row = i / cols`, `col = i % cols`.

**Steps in plain English:**

1. **Set bounds** — `lo = 0`, `hi = rows * cols - 1`.
2. **Loop while `lo <= hi`** — classic exact-match binary search.
3. **Compute mid** — `lo + (hi - lo) / 2`.
4. **Convert mid to 2D** — `row = mid / cols`, `col = mid % cols`.
5. **Three-way compare** — standard binary search on `matrix[row][col]`.

```java
public boolean searchMatrix(int[][] matrix, int target) {
    // Step 1 — flatten: treat m*n matrix as 1D array
    int rows = matrix.length;
    int cols = matrix[0].length;
    int lo = 0;
    int hi = rows * cols - 1;

    // Step 2 — classic binary search loop
    while (lo <= hi) {
        // Step 3 — overflow-safe mid
        int mid = lo + (hi - lo) / 2;

        // Step 4 — convert 1D index to 2D coordinates
        int val = matrix[mid / cols][mid % cols];

        // Step 5 — three-way compare
        if (val == target) {
            return true;
        } else if (val < target) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    return false;
}
```

**Complexity:** O(log(m × n)) time, O(1) space.

---

> 🧩 **Try these:**
> - ✅ LC 74 — Search a 2D Matrix — the template above
> - 🔴 LC 240 — Search a 2D Matrix II — different structure; binary search doesn't apply; use staircase search from top-right corner

---

<a id="walkthroughs"></a>
## 🔬 Worked Walkthroughs

---

### WW-1 — LC 704 Binary Search

> **Problem:** Given a sorted integer array with no duplicates and a target, return the target's index or -1 if not present.

**Brute force:** Linear scan left to right, compare each element to target. Return index on match or -1 after full scan. Time: O(n), Space: O(1). Ignores sorted property — one comparison tells you nothing about remaining elements.

**Intuition bridge — what cracks it open:** Array is sorted → if `arr[mid] < target`, EVERY element at or left of mid is also < target. One comparison eliminates the entire left half. That's the log factor.

**Steps in plain English:**

1. **Bounds** — `lo = 0`, `hi = n - 1`.
2. **Three-way compare at mid** — return, move lo, or move hi.
3. **Return -1** — loop exited without finding target.

```java
public int search(int[] nums, int target) {
    // Step 1 — set inclusive bounds
    int lo = 0;
    int hi = nums.length - 1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        // Step 2 — three-way compare
        if (nums[mid] == target) {
            return mid;
        } else if (nums[mid] < target) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }

    // Step 3 — not found
    return -1;
}
```

**Time:** O(log n) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 374 Guess Number Higher or Lower | Same loop, same three-way compare | Comparison comes from a black-box API | Replace `nums[mid]` with `guess(mid)` return value |
| LC 35 Search Insert Position | Same bounds, same loop | Return `lo` (insert position) if not found, not -1 | Remove `return -1`; return `lo` after loop |
| LC 702 Search in a Sorted Array of Unknown Size | Same logic | No `arr.length`; use reader API | Replace `nums[hi]` check with `reader.get(hi)` and double hi when out of range |

---

### WW-2 — LC 34 Find First and Last Position of Element in Sorted Array

> **Problem:** Given sorted array with duplicates and target, return `[firstIndex, lastIndex]`. Return `[-1, -1]` if not found.

**Brute force:** Scan left to right for first occurrence, right to left for last. Time: O(n), Space: O(1). Correct but wastes sorted structure — binary search does this in O(log n).

**Intuition bridge — what cracks it open:** Classic binary search stops at ANY occurrence of target. To find FIRST, when `arr[mid] == target`, keep searching left by setting `hi = mid` (mid might be the answer but there could be an earlier one). To find LAST, when `arr[mid] == target`, keep searching right by setting `lo = mid + 1`. Run the two loops independently.

**Steps in plain English:**

1. **Bisect-left** — find first occurrence: `hi = mid` when `arr[mid] >= target`.
2. **Bisect-right** — find last occurrence: `lo = mid + 1` when `arr[mid] <= target`, return `lo - 1`.
3. **Validate** — check if `arr[firstIdx] == target`; if not, return `[-1, -1]`.

```java
public int[] searchRange(int[] nums, int target) {
    // Step 1 — bisect-left: first occurrence
    int first = bisectLeft(nums, target);

    // Step 3 — if target not present, early exit
    if (first == nums.length || nums[first] != target) {
        return new int[]{ -1, -1 };
    }

    // Step 2 — bisect-right: last occurrence
    int last = bisectRight(nums, target) - 1;

    return new int[]{ first, last };
}

private int bisectLeft(int[] nums, int target) {
    int lo = 0;
    int hi = nums.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] >= target) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    return lo;
}

private int bisectRight(int[] nums, int target) {
    int lo = 0;
    int hi = nums.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] <= target) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }
    return lo;
}
```

**Time:** O(log n) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 278 First Bad Version | Bisect-left structure | Condition is `isBadVersion(mid)`, not array comparison | Replace `nums[mid] >= target` with `isBadVersion(mid)` |
| LC 35 Search Insert Position | Bisect-left returning lo | Never checks if target exists; lo IS the insert position | Drop the `nums[first] != target` guard; return lo directly |
| LC 852 Peak Index in Mountain Array | Bisect-left structure | Condition is `arr[mid] < arr[mid+1]` | `if (arr[mid] < arr[mid+1]) lo = mid+1; else hi = mid;` |

---

### WW-3 — LC 153 Find Minimum in Rotated Sorted Array

> **Problem:** Array was sorted then rotated at an unknown pivot. Find the minimum. No duplicates.

**Brute force:** Linear scan tracking running minimum. Time: O(n), Space: O(1). Ignores the key property: BOTH halves around any mid are individually sorted; the minimum is on the non-sorted side.

**Intuition bridge — what cracks it open:** Compare `arr[mid]` to `arr[hi]`. If `arr[mid] > arr[hi]`, the left half [lo..mid] is cleanly sorted (all values > arr[hi]) so the minimum MUST be in [mid+1..hi]. Otherwise, minimum is in [lo..mid] (mid itself might be it).

**Steps in plain English:**

1. **Bounds** — `lo = 0`, `hi = n - 1`.
2. **If `arr[mid] > arr[hi]`** — minimum in right half → `lo = mid + 1`.
3. **Else** — minimum at mid or left → `hi = mid`.
4. **Return `arr[lo]`** after loop.

```java
public int findMin(int[] nums) {
    // Step 1 — bounds
    int lo = 0;
    int hi = nums.length - 1;

    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;

        // Step 2 — left half sorted, min in right half
        if (nums[mid] > nums[hi]) {
            lo = mid + 1;
        // Step 3 — min at mid or to the left
        } else {
            hi = mid;
        }
    }

    // Step 4 — converged at minimum
    return nums[lo];
}
```

**Time:** O(log n) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 33 Search in Rotated Sorted Array | Same "identify sorted half" logic | After identifying sorted half, check if target is in it | Add `if (target in sorted half) search there else search other half` |
| LC 852 Peak Index in Mountain Array | Bisect on property | Condition is `arr[mid] < arr[mid+1]` (ascending) | `if (nums[mid] < nums[mid+1]) lo = mid+1; else hi = mid;` |
| LC 1095 Find in Mountain Array 🔴 | Two-phase binary search | Must find peak first, then bisect each half | Call `findPeak()` then two bisect calls |

---

### WW-4 — LC 33 Search in Rotated Sorted Array

> **Problem:** Same rotated sorted array, no duplicates. Given target, return index or -1.

**Brute force:** Linear scan. Time: O(n). Ignores that each half around any mid is sorted.

**Intuition bridge — what cracks it open:** Extend WW-3: after identifying which half is sorted, check whether the target falls inside that range. If yes, search that half. If no, search the other half. One comparison determines which half; another determines whether target is in it.

**Steps in plain English:**

1. **Bounds** — `lo = 0`, `hi = n - 1`.
2. **Identify sorted half** — if `arr[lo] <= arr[mid]`, left half [lo..mid] is sorted.
3. **Check if target is in sorted half** — if yes, search it; if no, search the other.
4. **Repeat** until found or space empty.

```java
public int search(int[] nums, int target) {
    // Step 1 — bounds
    int lo = 0;
    int hi = nums.length - 1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        if (nums[mid] == target) {
            return mid;
        }

        // Step 2 — left half [lo..mid] is sorted
        if (nums[lo] <= nums[mid]) {
            // Step 3 — is target in the sorted left half?
            if (nums[lo] <= target && target < nums[mid]) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        // Step 2 — right half [mid..hi] is sorted
        } else {
            // Step 3 — is target in the sorted right half?
            if (nums[mid] < target && target <= nums[hi]) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
    }
    return -1;
}
```

**Time:** O(log n) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 153 Find Minimum in Rotated | Same "identify sorted half" | Only need minimum, not target search | Drop target check; `arr[mid] > arr[hi]` drives lo/hi directly |
| LC 81 Search in Rotated Array II 🔴 | Same structure | Duplicates: `arr[lo] == arr[mid] == arr[hi]` is indeterminate | Add `lo++; hi--;` when duplicates block identification |

---

### WW-5 — LC 875 Koko Eating Bananas

> **Problem:** `piles[i]` bananas in pile i. Eat at most `k` per hour from one pile. Finish all piles in `h` hours. Return minimum `k`.

**Brute force:** Try every speed from 1 to `max(piles)`. For each speed, simulate hours needed. Time: O(max(piles) × n) — too slow for max(piles) = 10^9.

**Intuition bridge — what cracks it open:** The feasibility function `canFinish(speed)` is monotone: if speed k works, speed k+1 also works (eating faster can only help). One flip point exists. Binary search the speed.

**Steps in plain English:**

1. **Answer space** — `lo = 1`, `hi = max(piles)`.
2. **Feasibility check** — ceiling-divide each pile by speed; sum hours; compare to h.
3. **Binary search** — if feasible, try smaller (`hi = mid`); if not, try larger (`lo = mid + 1`).

```java
public int minEatingSpeed(int[] piles, int h) {
    // Step 1 — answer space bounds
    int lo = 1;
    int hi = 0;
    for (int p : piles) {
        hi = Math.max(hi, p);
    }

    // Step 3 — binary search on speed
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (canFinish(piles, mid, h)) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    return lo;
}

// Step 2 — feasibility check: can Koko eat all piles at this speed in h hours?
private boolean canFinish(int[] piles, int speed, int h) {
    int hours = 0;
    for (int pile : piles) {
        hours += (pile + speed - 1) / speed;
    }
    return hours <= h;
}
```

**Time:** O(n log(max(piles))) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 1011 Capacity to Ship Packages | Same binary search skeleton | Feasibility: can we ship in d days at this capacity? | Feasibility counts days: can't split a package across days |
| LC 2187 Minimum Time to Complete Trips | Same skeleton | Feasibility: can buses complete `totalTrips` trips in `time`? | `for (int t : time) trips += time / t` |
| LC 1283 Find Smallest Divisor Given Threshold | Same skeleton | Different feasibility: sum of ceil(nums[i]/d) ≤ threshold | Same ceiling division pattern |

---

### WW-6 — LC 1552 Magnetic Force Between Two Balls (Maximize Minimum)

> **Problem:** `position[]` has `n` baskets. Place `m` balls to maximize the minimum magnetic force (distance) between any two balls.

**Brute force:** Try all possible placements of m balls in n baskets. Time: O(C(n,m)) — combinatorially explosive.

**Intuition bridge — what cracks it open:** The feasibility function `canPlace(minDist)` is monotone in the OTHER direction: if minimum distance minDist is achievable, so is minDist-1 (place balls closer). The flip goes true→false as minDist increases. Use binary search on minDist, but flip the direction: when feasible, try larger (`lo = mid`); when infeasible, reduce (`hi = mid - 1`). Upper-mid bias prevents infinite loop.

**Steps in plain English:**

1. **Sort `position`** — greedy placement requires sorted order.
2. **Answer space** — `lo = 1`, `hi = (position[n-1] - position[0]) / (m - 1)`.
3. **Feasibility check** — greedily place balls: start at first basket, place next ball only when distance ≥ minDist. If we place all m balls, it's feasible.
4. **Binary search (maximize)** — upper-mid, `lo = mid` when feasible, `hi = mid - 1` when not.

```java
public int maxDistance(int[] position, int m) {
    // Step 1 — sort (greedy needs sorted positions)
    Arrays.sort(position);
    int n = position.length;

    // Step 2 — answer space for maximize-minimum
    int lo = 1;
    int hi = (position[n - 1] - position[0]) / (m - 1);

    // Step 4 — binary search: maximize minimum distance
    while (lo < hi) {
        int mid = lo + (hi - lo + 1) / 2;  // upper-mid for maximize
        if (canPlace(position, m, mid)) {
            lo = mid;    // mid works, try larger
        } else {
            hi = mid - 1;  // mid too large, reduce
        }
    }
    return lo;
}

// Step 3 — feasibility: can we place m balls with min distance >= minDist?
private boolean canPlace(int[] pos, int m, int minDist) {
    int count = 1;
    int last = pos[0];
    for (int i = 1; i < pos.length; i++) {
        if (pos[i] - last >= minDist) {
            count++;
            last = pos[i];
        }
    }
    return count >= m;
}
```

**Time:** O(n log n + n log(max_pos)) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 2616 Minimize Max Difference of Pairs | Same P4 skeleton | Minimize maximum (not maximize minimum) → standard direction | `lo < hi` + lower-mid + `hi = mid` when feasible |
| LC 1231 Divide Chocolate | Same maximize-minimum | Feasibility counts chunks ≥ mid, must equal k+1 | `if (count >= k+1) feasible` |
| LC 774 Min Distance to Gas Station (Add Stations) | Same maximize direction | Adding stations, not removing — feasibility checks how many to add | Different feasibility check |

---

### WW-7 — Gem Gap Problem (Minimize Maximum Adjacent Gap After K Removals)

> **Problem:** Sorted array of N gems. Remove exactly K gems. Minimize the largest adjacent gap among remaining gems.

**Brute force:** Try all C(N, K) subsets of K gems to remove. For each subset, compute max adjacent gap. Time: O(C(N,K) × N) — completely impractical.

**Intuition bridge — what cracks it open:** Since the array is sorted, the optimal N-K gems to KEEP are always a consecutive window (a contiguous subarray). Keeping non-consecutive elements merges two adjacent gaps into a larger one — it can never help. So: slide a window of size N-K across the sorted array; for each window, the answer is the max adjacent gap inside it. We want the window with the minimum max-adjacent-gap. This reduces to: sliding window maximum on the gaps array.

**Steps in plain English:**

1. **Build gaps array** — `gaps[i] = arr[i+1] - arr[i]` for i in 0..N-2.
2. **Window size on gaps** — `w = N - K - 1` (a window of N-K gems has N-K-1 adjacent gaps).
3. **Sliding window maximum** — use a monotonic deque; slide over `gaps[]` with window size `w`.
4. **Track minimum of all window maxes** — that's the answer.

```java
public int minMaxGap(int[] arr, int k) {
    int n = arr.length;

    // Step 1 — build gaps array
    int[] gaps = new int[n - 1];
    for (int i = 0; i < n - 1; i++) {
        gaps[i] = arr[i + 1] - arr[i];
    }

    // Step 2 — window size on gaps: keeping (n-k) gems → (n-k-1) adjacent gaps
    int w = n - k - 1;

    // Step 3 — sliding window maximum using monotonic deque
    // Deque stores indices; front = index of current window maximum
    Deque<Integer> deque = new ArrayDeque<>();
    int ans = Integer.MAX_VALUE;

    for (int i = 0; i < gaps.length; i++) {
        // Drop indices that fall outside the window
        while (!deque.isEmpty() && deque.peekFirst() < i - w + 1) {
            deque.pollFirst();
        }
        // Drop from rear any index with gaps[idx] <= gaps[i]
        while (!deque.isEmpty() && gaps[deque.peekLast()] <= gaps[i]) {
            deque.pollLast();
        }
        deque.addLast(i);

        // Step 4 — record minimum of window maxes once window is full
        if (i >= w - 1) {
            ans = Math.min(ans, gaps[deque.peekFirst()]);
        }
    }
    return ans;
}
```

**Trace for `arr=[12,16,22,31,31,38]`, K=3:**

```
gaps = [4, 6, 9, 0, 7]    window size w = 6-3-1 = 2

i=0: deque=[0] (gaps[0]=4).       Window not full yet.
i=1: deque=[0,1] (gaps[1]=6>4).   Window full. max=gaps[0]=4. ans=4.
     Wait — gaps[1]=6 > gaps[0]=4; front=0 gives max=4, but gaps[1]=6 is larger?
     Deque is DECREASING: rear drops if gaps[rear] <= gaps[i].
     gaps[0]=4 < gaps[1]=6 → do NOT drop 0. deque=[0,1]. front=0, max=gaps[0]=4.
     
     Recheck: deque stores indices in DECREASING order of gaps values.
     i=1: gaps[1]=6. Drop rear while gaps[rear]<=6: gaps[0]=4 ≤ 6 → drop 0.
     deque=[1]. front=1, max=gaps[1]=6. ans=6.

i=2: gaps[2]=9. Drop rear while gaps[rear]<=9: gaps[1]=6≤9 → drop 1.
     deque=[2]. Drop front if out of window: 2-(2-1)=1, front=2≥1, keep.
     Window [gaps[1],gaps[2]]=[6,9]. max=gaps[2]=9. ans=min(6,9)=6.

i=3: gaps[3]=0. No drops from rear (gaps[2]=9>0). deque=[2,3].
     Drop front if out: window [gaps[2],gaps[3]], front=2, 2>=3-2+1=2, keep.
     max=gaps[2]=9. ans=min(6,9)=6.

i=4: gaps[4]=7. Drop rear while gaps[rear]<=7: gaps[3]=0≤7→drop, gaps[2]=9>7→keep.
     deque=[2,4]. Drop front if out: window [gaps[3],gaps[4]], 2<4-2+1=3 → drop.
     deque=[4]. max=gaps[4]=7. ans=min(6,7)=6.

Answer: 6  ✅
```

> **Cross-reference:** The sliding window maximum technique (monotonic deque) is covered in detail in `DSA/DeepDive/stacks-queues-fundamentals.md` Pattern 4. The key observation that leads to this reduction (sorted array + remove K → consecutive window is always optimal) is covered in the interview analysis above WW-7.

**Time:** O(n) | **Space:** O(n)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 239 Sliding Window Maximum | Same monotonic deque | Return ALL window maxes, not minimum of them | Collect `gaps[deque.peekFirst()]` into result list |
| LC 774 Minimize Max Distance to Gas Station | Same "minimize max gap" goal | ADD stations instead of removing gems; binary search on answer | P4 approach: binary search on gap size + greedy count |
| LC 2294 Partition Array Such That Max Diff ≤ K | Same sliding window max idea | Different window condition | `arr[i] - arr[deque.peekFirst()] > k` triggers partition |

---

### WW-8 — LC 74 Search a 2D Matrix

> **Problem:** Matrix where each row is sorted left to right, and the first integer of each row is greater than the last integer of the previous row. Search for target.

**Brute force:** Check every cell. Time: O(m × n). Ignores globally sorted structure.

**Intuition bridge — what cracks it open:** The matrix is essentially a sorted 1D array of length m × n split into rows. Reading row by row gives a fully sorted sequence. Map any 1D index i to 2D via `row = i / cols`, `col = i % cols`. Apply classic binary search.

**Steps in plain English:**

1. **Flatten bounds** — `lo = 0`, `hi = rows * cols - 1`.
2. **Map mid to 2D** — `row = mid / cols`, `col = mid % cols`.
3. **Classic three-way compare** — found, search right, or search left.

```java
public boolean searchMatrix(int[][] matrix, int target) {
    // Step 1 — treat as 1D sorted array
    int rows = matrix.length;
    int cols = matrix[0].length;
    int lo = 0;
    int hi = rows * cols - 1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        // Step 2 — convert 1D index to 2D
        int val = matrix[mid / cols][mid % cols];

        // Step 3 — three-way compare
        if (val == target) {
            return true;
        } else if (val < target) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    return false;
}
```

**Time:** O(log(m × n)) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 378 Kth Smallest in Sorted Matrix 🔴 | Binary search on answer space | Matrix rows/cols sorted but NOT globally sorted → no 1D flatten | Binary search on value range; feasibility counts elements ≤ mid |
| LC 668 Kth Smallest Number in Multiplication Table 🔴 | Same binary search structure | Count elements ≤ mid is `sum of min(mid/i, cols)` for each row | Different feasibility check formula |

---

### WW-9 — LC 410 Split Array Largest Sum 🔴

> 🔴 **READ THIS FIRST — Do NOT attempt cold.**
>
> **This problem silently demands:** (1) recognizing "minimize the maximum" = binary search on answer space; (2) writing a greedy feasibility check for partition counting; (3) the upper bound for the answer space is the sum of the entire array, not max element.

> **Problem:** Split `nums` into exactly `k` non-empty subarrays to minimize the largest subarray sum.

**Brute force:** Try all ways to split nums into k subarrays (C(n-1, k-1) choices). For each split, compute the maximum subarray sum. Time: exponential.

**Intuition bridge — what cracks it open:** The feasibility function is monotone: if a maximum sum of `mid` is achievable with `k` subarrays, then `mid+1` is also achievable (more room = easier). Binary search on `mid`. Feasibility check: greedily partition, accumulating each subarray; start a new subarray when current sum would exceed `mid`. Count subarrays needed — if ≤ k, feasible.

**Steps in plain English:**

1. **Answer space** — `lo = max(nums)` (each element must fit in at least one subarray), `hi = sum(nums)` (one subarray = entire array).
2. **Feasibility check** — greedily count minimum partitions needed so no subarray exceeds mid.
3. **Binary search** — if feasible, try smaller (`hi = mid`); if not, try larger (`lo = mid + 1`).

```java
public int splitArray(int[] nums, int k) {
    // Step 1 — answer space: max element to total sum
    long lo = 0;
    long hi = 0;
    for (int n : nums) {
        lo = Math.max(lo, n);
        hi += n;
    }

    // Step 3 — binary search: minimize maximum subarray sum
    while (lo < hi) {
        long mid = lo + (hi - lo) / 2;
        if (canSplit(nums, k, mid)) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    return (int) lo;
}

// Step 2 — feasibility: can we split into ≤ k parts with no part > maxSum?
private boolean canSplit(int[] nums, int k, long maxSum) {
    int parts = 1;
    long current = 0;
    for (int n : nums) {
        if (current + n > maxSum) {
            parts++;
            current = n;
        } else {
            current += n;
        }
    }
    return parts <= k;
}
```

**🐞 Common Bugs:**

```java
// ❌ Wrong: lo = 0 — subarray sum can't be 0 if elements exist (or less than max element)
int lo = 0;

// ✅ Right: lo = max(nums) — every element must fit in its subarray
int lo = Arrays.stream(nums).max().getAsInt();
```

```java
// ❌ Wrong: hi = Integer.MAX_VALUE — wasteful; the sum of nums is the true upper bound
int hi = Integer.MAX_VALUE;

// ✅ Right: hi = sum(nums) — one subarray containing everything
int hi = Arrays.stream(nums).sum();
// Use long if nums can overflow int
```

**🪜 Build-up ladder:**

1. ✅ LC 875 Koko Eating Bananas — same P4 skeleton with simpler feasibility check
2. ✅ LC 1011 Capacity to Ship Packages — same structure; feasibility is nearly identical
3. 🔴 LC 410 Split Array Largest Sum — same template; master 875 first

**Time:** O(n log(sum)) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 1011 Capacity to Ship Packages | Same greedy partition feasibility | Each package is one "batch"; ship capacity = max sum per day | Same code with `weights` instead of `nums` |
| LC 2064 Minimized Maximum of Products Distributed to Any Store 🔴 | Same P4 binary search | Feasibility uses ceiling division per product type | `stores += ceil(quantities[i] / mid)` |

---

<a id="gotchas"></a>
## ⚠️ Gotchas — Silent Bug Hall of Fame

---

**Integer overflow in mid-point computation.**

```java
// ❌ Wrong — overflows when lo + hi > 2^31 - 1 (lo=1B, hi=2B → sum=3B → overflow → negative mid)
int mid = (lo + hi) / 2;

// ✅ Right — offset from lo is always ≤ (hi - lo) ≤ Integer.MAX_VALUE
int mid = lo + (hi - lo) / 2;
```

> Cross-reference: `DSA/DeepDive/integer-overflow-and-limits.md` — full analysis of mid-point overflow on the number line.

---

**Using `while lo <= hi` when you need `while lo < hi`.**

```java
// ❌ Wrong — lo <= hi with hi = mid causes infinite loop when lo == hi
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (arr[mid] >= target) {
        hi = mid;  // ← if lo == hi == mid, lo and hi don't change → infinite loop
    }
}

// ✅ Right — lo < hi exits when lo == hi; hi = mid is safe because lo < hi guarantees mid < hi
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (arr[mid] >= target) {
        hi = mid;
    }
}
```

---

**Using `hi = mid - 1` in a `lo < hi` loop.**

```java
// ❌ Wrong — hi = mid - 1 can skip the answer when arr[mid] == target
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (arr[mid] >= target) {
        hi = mid - 1;  // ← might eliminate the only valid answer at mid
    }
}

// ✅ Right — keep mid in range; hi = mid is the correct move
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (arr[mid] >= target) {
        hi = mid;  // mid could be the answer; don't exclude it
    }
}
```

---

**Wrong answer-space bounds.**

```java
// Koko: ❌ Wrong — lo = 0 means 0 speed (divides by zero in feasibility check)
int lo = 0;

// ✅ Right — minimum meaningful speed is 1
int lo = 1;

// Split Array: ❌ Wrong — lo = 1 allows subarrays smaller than the max element
int lo = 1;

// ✅ Right — every element must fit in at least one subarray
int lo = Arrays.stream(nums).max().getAsInt();
```

---

**Infinite loop in "maximize" binary search with lower-mid.**

```java
// ❌ Wrong — when lo = 4, hi = 5: mid = 4 = lo. If feasible, lo = mid = 4 → stuck.
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;  // lower-mid
    if (feasible(mid)) {
        lo = mid;  // lo never advances when hi = lo + 1
    }
}

// ✅ Right — upper-mid guarantees mid > lo when hi = lo + 1
while (lo < hi) {
    int mid = lo + (hi - lo + 1) / 2;  // upper-mid
    if (feasible(mid)) {
        lo = mid;  // now mid = hi when hi = lo + 1, so lo advances to hi → exits
    }
}
```

---

**Applying binary search to a non-monotone function.**

```java
// ❌ Wrong — if the function is not monotone, binary search gives arbitrary wrong answers
// Example: searching for a "local minimum" in an unsorted array as if it's binary-searchable
// (The function can dip and rise again — no single flip point → binary search is invalid)

// ✅ Check: Is the feasibility function monotone?
// "If answer k works, does k+1 always work?" — for minimize.
// "If answer k works, does k-1 always work?" — for maximize.
// If not, binary search is invalid here.
```

---

**`static` fields persisting across LeetCode test cases.**

```java
// ❌ Wrong — if you use class-level lo/hi (static field), they persist across test calls
static int lo = 0;  // ← carries over from previous test case

// ✅ Right — always declare loop variables as local variables inside the method
public int search(int[] nums, int target) {
    int lo = 0;  // local — reset for every call
    int hi = nums.length - 1;
    ...
}
```

---

<a id="practice-plan"></a>
## 🗺️ Practice Plan — A Progression That Works

Binary search has a steep recognition curve — the classic template is easy; knowing WHEN and WHICH variant is the real skill. Work each tier before moving on.

> **Reminder of tags:** ✅ Try Now · 🟡 Try after the named prerequisite · 🔴 Reference Only

---

### Tier 1 — Foundational (must be muscle memory)

Master these before touching rotated arrays or answer space. Until you write the classic template from memory in < 2 minutes, stay here.

1. ✅ **LC 704** Binary Search — classic `lo <= hi` template
2. ✅ **LC 374** Guess Number Higher or Lower — same loop, black-box comparison API
3. ✅ **LC 278** First Bad Version — bisect-left: `isBadVersion(mid)` as the condition
4. ✅ **LC 35** Search Insert Position — bisect-left returning `lo` (even if target absent)

---

### Tier 2 — Bisect and Range Queries

1. ✅ **LC 34** Find First and Last Position — run bisect-left + bisect-right
2. ✅ **LC 852** Peak Index in Mountain Array — bisect on `arr[mid] < arr[mid+1]`
3. ✅ **LC 1346** Check If N and Its Double Exist — bisect after sorting to find double

---

### Tier 3 — Rotated Array

1. ✅ **LC 153** Find Minimum in Rotated Sorted Array — identify sorted half by comparing to hi
2. 🟡 **Try after LC 153** — LC 33 Search in Rotated Sorted Array — add target-in-range check
3. 🔴 LC 154 Find Minimum with Duplicates — `arr[lo]==arr[mid]==arr[hi]` forces `hi--`; O(n) worst case

---

### Tier 4 — Binary Search on Answer Space ⭐

The hardest and highest-frequency variant at FAANG.

1. ✅ **LC 875** Koko Eating Bananas — canonical minimize-speed template
2. 🟡 **Try after LC 875** — LC 1011 Capacity to Ship Packages — same skeleton, different feasibility
3. 🟡 **Try after LC 875** — LC 1552 Magnetic Force Between Balls — maximize-minimum (upper-mid)
4. 🟡 **Try after LC 1552** — Gem Gap Problem — sorted-array reduction → sliding window max (see WW-7)
5. 🟡 **Try after LC 875** — LC 74 Search a 2D Matrix — Pattern 5 (simpler but cross-pattern)

---

### Tier 5 — Reference Only (advanced)

Treat as bedtime reading. Come back after Tier 4 is solid.

- 🔴 **LC 410** Split Array Largest Sum — same P4 skeleton; answer space lo = max(nums) trap
- 🔴 LC 774 Minimize Max Distance to Gas Station — add K stations variant (non-LeetCode premium; similar to gem)
- 🔴 LC 1095 Find in Mountain Array — two-phase binary search (find peak, then bisect each half)
- 🔴 LC 378 Kth Smallest in Sorted Matrix — answer space on value range, not indices
- 🔴 LC 4 Median of Two Sorted Arrays — partition-based binary search (hardest binary search problem)

---

### How to use this plan

- **Pace:** One tier before moving to the next. Don't jump to Tier 4 without Tier 1 as muscle memory.
- **When stuck:** Time-box 25 minutes. If still stuck, read editorial. Don't copy-paste — type the solution yourself after understanding it.
- **Revision:** After finishing a tier, redo the first 2 problems from memory.
- **Victory criterion:** You can write the Koko Eating Bananas solution (Pattern 4) from a blank editor in under 10 minutes, correctly handling the feasibility check, answer-space bounds, and loop terminator.

---

<a id="tldr"></a>
## 🧾 TL;DR — One-Page Summary

- **Binary search** = eliminate half the search space at each step by exploiting a monotone property
- **Mental model:** Not "sorted array" — "one flip point." Everything below the flip: condition false. Everything above: condition true. Find the flip.
- **5 patterns:** Classic (`lo<=hi`, exact match) · Bisect (`lo<hi`, first/last bound) · Rotated (identify sorted half) · Answer Space (`lo<hi`, feasibility check) · Matrix (flatten to 1D)
- **Decide before writing:** (1) What are lo and hi? (2) `lo<=hi` or `lo<hi`? (3) What does mid update do when equal/feasible?
- **Always:** `lo + (hi-lo)/2`, never `(lo+hi)/2`
- **Maximize variant:** upper-mid `lo + (hi-lo+1)/2` prevents infinite loop when `lo = hi - 1`
- **Feasibility check:** name it, extract it, test it separately — it's the hardest part to get right
- **Gotcha 1:** `hi = mid` with `lo <= hi` → infinite loop when lo == hi (use `lo < hi` instead)
- **Gotcha 2:** `hi = mid - 1` with `lo < hi` → skips the answer (use `hi = mid` instead)
- **Gotcha 3:** overflow — always `lo + (hi - lo) / 2`
- **Gem problem:** sorted array + remove K → consecutive window is optimal → sliding window max on gaps array
- **Tier 1 (Foundational) you must master:** LC 704, LC 278, LC 35, LC 875
- **Lesson learned (July 2026):** Got tripped by the gem gap problem in an interview — recognized binary search but missed the "consecutive window" observation. The sorted → consecutive reduction is the interview discrimination factor, not the binary search itself.

---

<a id="changelog"></a>
## 🔄 Changelog

| Date | Change |
| --- | --- |
| July 2026 | Created. Triggered by interview failure on gem gap problem. Covers 5 patterns + 9 walkthroughs (Classic, Bisect, Rotated ×2, Answer Space ×4 including gem problem, 2D Matrix). Phase 1 setup section + decision table for lo/hi/loop terminator. |
