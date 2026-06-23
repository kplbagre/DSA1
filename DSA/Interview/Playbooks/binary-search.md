# Binary Search — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to recognize binary search patterns beyond "find element in sorted array." Not for learning binary search from scratch — for that, see `DSA/DeepDive/binary-search-fundamentals.md` (planned).

---

## 🎯 Why You're Reading This

Binary search is deceptively simple. Everyone knows the basic idea — but interviews test whether you can recognize it in disguise. "Minimize the maximum" → binary search on answer space. "First position where..." → bisect left. "Sorted but rotated" → modified binary search. This file teaches you to spot those disguises in under 30 seconds.

After reading this file, you should be able to:
1. Recognize the 5 binary search variants from problem wording alone
2. Write the `lo < hi` vs `lo <= hi` template correctly every time (this is the #1 bug source)
3. Handle rotated arrays, answer-space searches, and 2D matrices

---

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `int mid = lo + (hi - lo) / 2` | Overflow-safe midpoint | All patterns |
| `Arrays.sort(arr)` | Sort before binary search (if not already sorted) | Patterns 1, 2 |
| `Math.ceil()` / `(a + b - 1) / b` | Round-up division for answer space | Pattern 4 |
| `arr.length` | Array length — boundary for `hi` | All patterns |
| `matrix[mid / cols][mid % cols]` | Flatten 2D index to 1D for binary search | Pattern 5 |

> **Full reference:** `../Reference/dsa-collections-notes.md`

---

## 🧠 The Mental Model — When to Think "Binary Search"

Binary search works whenever there's a **monotonic condition** (a condition that flips from false→true or true→false as you move along a sorted axis). The "sorted axis" might be:
- An actual sorted array (classic)
- An answer space like "can I eat all bananas at speed K?" (yes for large K, no for small K)
- A rotated sorted array (sorted in two halves)

### Pattern Recognition — Which Binary Search Variant?

```
Problem involves searching
│
├── "Sorted array, find target"
│   └── Pattern 1: Classic Binary Search
│
├── "Find first/last position" or "lower/upper bound"
│   └── Pattern 2: Bisect Left / Bisect Right ⭐
│
├── "Sorted but rotated"
│   ├── "Find target"     → Pattern 3: Rotated Array Search ⭐
│   └── "Find minimum"   → Pattern 3 variant
│
├── "Minimize the maximum" or "maximize the minimum"
│   or "smallest X such that condition is true"
│   └── Pattern 4: Binary Search on Answer Space ⭐
│
└── "Search in a 2D sorted matrix"
    └── Pattern 5: Matrix Binary Search
```

### 🎨 Visual — The lo/hi/mid Dance

```
Classic Binary Search — find target = 7

Array: [1, 3, 5, 7, 9, 11, 13]
        ↑           ↑          ↑
       lo          mid         hi

Step 1: mid = 7 → found!

Bisect Left — find first position of target = 5

Array: [1, 3, 5, 5, 5, 7, 9]
        ↑        ↑        ↑
       lo       mid       hi

Step 1: mid = 5 → EQUAL, but move hi = mid (keep searching LEFT)
Step 2: [1, 3, 5, 5]  → mid = 3 → too small, lo = mid + 1
Step 3: [5, 5] → mid = 5 → hi = mid → lo == hi → FOUND at index 2

KEY INVARIANT:
   Bisect left: when arr[mid] == target, move hi = mid (search left half)
   Bisect right: when arr[mid] == target, move lo = mid + 1 (search right half)
   This is the ONLY difference between finding first vs last occurrence.
```

---

## 🧭 Pattern 1: Classic Binary Search

**What this solves:** You have a sorted array and need to find a target value's index. Without using the sorted property, you'd scan every element. The sorted order is the property that makes halving possible.

**Recognition cues — reach for this when:**
- "Find target in a sorted array"
- "Determine if element exists"
- Problem explicitly states array is sorted

**Brute force:** Scan every element left to right until target is found. O(n) time, O(1) space. Works on any array — but ignores the sorted property entirely.

**Key insight:** Since the array is sorted, comparing `arr[mid]` with target tells you which half to discard. One comparison eliminates half the remaining search space — that's the log factor.

**Steps in plain English:**

1. **Set boundaries** — `lo = 0`, `hi = arr.length - 1`.
2. **Loop while `lo <= hi`** — this is the "search until exhausted" variant.
3. **Compute mid** — `lo + (hi - lo) / 2` (overflow-safe).
4. **Compare** — if `arr[mid] == target`, return. If less, search right. If more, search left.

```java
public int binarySearch(int[] arr, int target) {
    int lo = 0;
    int hi = arr.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] == target) {
            return mid;
        } else if (arr[mid] < target) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    return -1;
}
```

**Complexity (optimal):** O(log n) time, O(1) space

**🏷️ Problems:** LC 704 (Binary Search), LC 374 (Guess Number Higher or Lower).

---

## 🧭 Pattern 2: Bisect Left / Bisect Right (First & Last Position) ⭐

**What this solves:** You have a sorted array with duplicates and need the FIRST or LAST occurrence of a value — not just any occurrence. Classic binary search stops at the first match it finds; it can't tell you whether there are earlier or later copies.

**Recognition cues — reach for this when:**
- "Find the first/last occurrence of target"
- "Find the insertion point"
- "How many elements are less than X?"
- "Find the leftmost/rightmost position"

**Brute force:** Scan left to right, record first index where `arr[i] == target`; scan again for last. O(n) time, O(1) space.

**Key insight:** When `arr[mid] == target`, don't return. Instead bias the boundary — move `hi = mid` to keep searching left (bisect-left) or `lo = mid + 1` to keep searching right (bisect-right). This is the ONLY difference from classic binary search.

**Steps in plain English:**

1. **Set boundaries** — `lo = 0`, `hi = arr.length - 1`.
2. **Loop while `lo < hi`** — note: strict less-than, NOT `<=`. This converges `lo` and `hi` to the answer.
3. **On match** — DON'T return immediately. For bisect-left: `hi = mid` (keep searching left). For bisect-right: `lo = mid + 1` (keep searching right).
4. **After loop** — `lo == hi` is the answer position.

```java
// Bisect Left — find FIRST position of target
public int bisectLeft(int[] arr, int target) {
    int lo = 0;
    int hi = arr.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] < target) {
            lo = mid + 1;
        } else {
            // arr[mid] >= target → answer is mid or to the left
            hi = mid;
        }
    }
    return lo;
}

// Bisect Right — find position AFTER last occurrence of target
public int bisectRight(int[] arr, int target) {
    int lo = 0;
    int hi = arr.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] <= target) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }
    return lo;
}
```

**Complexity (optimal):** O(log n) time, O(1) space

**🏷️ Problems:** LC 34 (Find First and Last Position), LC 278 (First Bad Version), LC 35 (Search Insert Position).

---

## 🧭 Pattern 3: Rotated Sorted Array ⭐

**What this solves:** A sorted array was cut at some unknown point and the two pieces were swapped. Classic binary search breaks because the array is no longer globally sorted. You need to find a target or the minimum element despite the rotation.

**Recognition cues — reach for this when:**
- "Sorted array that has been rotated"
- "Find target in rotated array"
- "Find the minimum element in rotated array"
- Array was sorted, then some rotation happened

**Brute force:** Linear scan through the array. O(n) time, O(1) space. You don't need the sorted property to find an element by brute force.

**Key insight:** Even after rotation, at least ONE half (left or right of `mid`) is always completely sorted — the rotation creates at most one "cliff." Identify the sorted half by comparing `arr[lo]` with `arr[mid]`, then check if target falls in that sorted half to decide direction.

**Steps in plain English:**

1. **Set boundaries** — `lo = 0`, `hi = arr.length - 1`.
2. **Compute mid** — check which half is sorted by comparing `arr[lo]` with `arr[mid]`.
3. **If left half sorted** (`arr[lo] <= arr[mid]`) — check if target is in `[lo, mid)`. If yes, search left. If no, search right.
4. **If right half sorted** — check if target is in `(mid, hi]`. If yes, search right. If no, search left.

```java
public int searchRotated(int[] nums, int target) {
    int lo = 0;
    int hi = nums.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] == target) {
            return mid;
        }
        // Left half is sorted
        if (nums[lo] <= nums[mid]) {
            if (target >= nums[lo] && target < nums[mid]) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        // Right half is sorted
        else {
            if (target > nums[mid] && target <= nums[hi]) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
    }
    return -1;
}
```

### 🎨 Visual — Rotated Array Structure

```
Original sorted: [1, 2, 3, 4, 5, 6, 7]
Rotated by 3:    [5, 6, 7, 1, 2, 3, 4]

    7 ┤       ●
    6 ┤    ●
    5 ┤ ●                          ← left sorted half
    4 ┤                      ●
    3 ┤                   ●
    2 ┤                ●           ← right sorted half
    1 ┤             ●
      └──┬──┬──┬──┬──┬──┬──
         0  1  2  3  4  5  6

The "cliff" between index 2 and 3 is the rotation point.
For any mid, ONE side of mid is guaranteed sorted (no cliff).
Check that sorted side to decide where the target could be.

KEY INVARIANT:
   arr[lo] <= arr[mid] → left half is sorted (no cliff on this side)
   arr[lo] > arr[mid]  → right half is sorted (cliff is on the left side)
```

**Complexity (optimal):** O(log n) time, O(1) space — degrades to O(n) worst case when duplicates prevent determining which half is sorted (LC 81)

**🏷️ Problems:** LC 33 (Search in Rotated Sorted Array), LC 153 (Find Minimum in Rotated Sorted Array), LC 81 (Search in Rotated Sorted Array II — with duplicates).

---

## 🧭 Pattern 4: Binary Search on Answer Space ⭐

**What this solves:** The answer isn't in the array — it's a value you need to find (like minimum speed, minimum capacity, or optimal split). You can check whether a candidate answer "works," but you'd need to try every possible value. The trick: the "works/doesn't work" boundary is monotonic.

**Recognition cues — reach for this when:**
- "Minimize the maximum" or "Maximize the minimum"
- "Smallest value such that [some condition]"
- "Can you do it with capacity/speed K?" — and the answer is monotonic (if K works, K+1 also works)
- "Split array into K parts minimizing the largest sum"

**Brute force:** Try every possible answer from `lo` to `hi`, run the feasibility check for each. O(answer_range × n) time, O(1) space. Correct but completely impractical for large ranges.

**Key insight:** The feasibility function has exactly one flip point — below it, infeasible; above it, feasible (or vice versa). Binary search finds that flip point in O(log(answer_range)) checks instead of O(answer_range).

**Steps in plain English:**

1. **Define the search range** — `lo` = minimum possible answer, `hi` = maximum possible answer.
2. **Binary search on this range** — for each `mid`, run a feasibility check.
3. **Feasibility check** — a helper function that returns true/false for "can I achieve the goal with this candidate value?"
4. **Narrow the range** — if feasible, search for a smaller answer (`hi = mid`). If not feasible, need a larger answer (`lo = mid + 1`).

```java
// Template: Binary Search on Answer Space
public int binarySearchOnAnswer(int[] input, int constraint) {
    int lo = minPossibleAnswer(input);
    int hi = maxPossibleAnswer(input);
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (isFeasible(input, mid, constraint)) {
            // mid works → try smaller
            hi = mid;
        } else {
            // mid doesn't work → need bigger
            lo = mid + 1;
        }
    }
    return lo;
}
```

**Complexity (optimal):** O(n × log(answer_range)) time, O(1) space — the feasibility check (O(n)) runs log(answer_range) times

**🏷️ Problems:** LC 875 (Koko Eating Bananas), LC 410 (Split Array Largest Sum), LC 1011 (Capacity to Ship Packages Within D Days).

---

## 🧭 Pattern 5: Matrix Binary Search

**What this solves:** A 2D matrix where each row is sorted left→right AND the first element of each row is greater than the last element of the previous row. This means the matrix, read row-by-row, is one big sorted 1D array — but you can't index it directly like an array.

**Recognition cues — reach for this when:**
- "Search in a 2D matrix" where rows and columns are sorted
- "Each row is sorted and first element of next row > last element of previous row"
- Can treat the matrix as a flattened sorted array

**Brute force:** Scan every cell left to right, top to bottom until target is found. O(m × n) time, O(1) space.

**Key insight:** Because each row ends before the next row begins, the whole matrix is globally sorted. Map 1D index `i` to 2D using `row = i / cols`, `col = i % cols`, then run standard binary search on the virtual 1D array.

**Steps in plain English:**

1. **Treat matrix as 1D** — total elements = `rows * cols`.
2. **Map 1D index to 2D** — `row = mid / cols`, `col = mid % cols`.
3. **Standard binary search** — on the virtual 1D array.

```java
public boolean searchMatrix(int[][] matrix, int target) {
    int rows = matrix.length;
    int cols = matrix[0].length;
    int lo = 0;
    int hi = rows * cols - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        int val = matrix[mid / cols][mid % cols];
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

**Complexity (optimal):** O(log(m × n)) time, O(1) space — note: LC 240 (rows and columns independently sorted, not globally ordered) uses staircase search instead: O(m + n) time, O(1) space

**🏷️ Problems:** LC 74 (Search a 2D Matrix), LC 240 (Search a 2D Matrix II — different approach: staircase search).

---

## 🔬 Canonical Problem — LC 875: Koko Eating Bananas

> **Problem:** Koko has `n` piles of bananas. She can eat at most `k` bananas per hour from one pile. If a pile has fewer than `k` bananas, she finishes it in one hour (she doesn't start another pile that hour). She has `h` hours to eat all bananas. Find the minimum `k` (eating speed) such that she can finish all piles within `h` hours. Example: `piles = [3,6,7,11], h = 8` → answer is `4`.

### Step 1 — Read and identify triggers

"Find the **minimum** speed such that she **can finish** within `h` hours." This is a classic "smallest value such that condition is true" — triggers **Pattern 4: Binary Search on Answer Space**. The feasibility condition is monotonic: if speed `k` works, then speed `k+1` also works.

### Step 2 — Choose the template

I'll use the answer-space template. I need to define:
- **lo** = 1 (minimum speed — must eat at least 1 per hour)
- **hi** = max(piles) (if she eats the largest pile in one hour, she can definitely finish)
- **isFeasible(k)** = "total hours needed at speed k ≤ h"

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Set search range** — `lo = 1`, `hi = max(piles)`.
2. **For each candidate speed `mid`** — calculate total hours needed: for each pile, `ceil(pile / mid)` hours.
3. **If total hours ≤ h** — speed `mid` works, try smaller (`hi = mid`).
4. **If total hours > h** — too slow, need faster (`lo = mid + 1`).

```java
class Solution {
    public int minEatingSpeed(int[] piles, int h) {
        int lo = 1;
        int hi = 0;
        for (int pile : piles) {
            hi = Math.max(hi, pile);
        }
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

    private boolean canFinish(int[] piles, int speed, int h) {
        int hours = 0;
        for (int pile : piles) {
            // Ceiling division without Math.ceil
            hours += (pile + speed - 1) / speed;
        }
        return hours <= h;
    }
}
```

### Step 4 — Verify with example

`piles = [3,6,7,11], h = 8`:
- `lo = 1, hi = 11`
- `mid = 6`: hours = 1+1+2+2 = 6 ≤ 8 → hi = 6
- `mid = 3`: hours = 1+2+3+4 = 10 > 8 → lo = 4
- `mid = 5`: hours = 1+2+2+3 = 8 ≤ 8 → hi = 5
- `mid = 4`: hours = 1+2+2+3 = 8 ≤ 8 → hi = 4
- `lo == hi == 4` → answer is **4** ✅

### Complexity

- **Brute force** (try every speed 1..max(piles)): O(n × max(piles)) time, O(1) space
- **Binary search on answer space**: O(n × log(max(piles))) time, O(1) space — log(max(piles)) iterations × O(n) feasibility check each

---

## ⚡ Problem Bank — Key Twists

---

### LC 704: Binary Search

> **Problem:** Given a sorted array of integers and a target, return the index of target if found, otherwise return -1. Example: `nums = [-1,0,3,5,9,12], target = 9` → `4`.

> **Brute force:** Linear scan every element until target is found. O(n) time, O(1) space.
> **Key insight:** Sorted order means one comparison tells you which half to discard — that's the log factor.
> **Approach:** Classic Pattern 1 — `lo <= hi`, compare `nums[mid]` with target, narrow half.

```java
int lo = 0, hi = nums.length - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (nums[mid] == target) {
        return mid;
    } else if (nums[mid] < target) {
        // Target is in the right half
        lo = mid + 1;
    } else {
        // Target is in the left half
        hi = mid - 1;
    }
}
return -1;
```

**Complexity (optimal):** O(log n) time, O(1) space

---

### LC 34: Find First and Last Position of Element in Sorted Array

> **Problem:** Given a sorted array and a target, find the starting and ending position of target. Return `[-1,-1]` if not found. Example: `nums = [5,7,7,8,8,10], target = 8` → `[3,4]`.

> **Brute force:** Scan left to right to record first occurrence, scan again right to left for last. O(n) time, O(1) space.
> **Key insight:** When `arr[mid] == target`, don't return — bias the boundary. `hi = mid` keeps searching left (first), `lo = mid + 1` keeps searching right (last). One binary search per direction.
> **Approach:** Run bisect-left to find first position, bisect-right to find last position. Two binary searches, both O(log n).

```java
// Find leftmost occurrence of target
int first = bisectLeft(nums, target);
// bisectRight returns one past the last occurrence, so subtract 1
int last = bisectRight(nums, target) - 1;
// Verify target actually exists at the found position
if (first < nums.length && nums[first] == target) {
    return new int[]{first, last};
}
return new int[]{-1, -1};
```

**Complexity (optimal):** O(log n) time, O(1) space

---

### LC 278: First Bad Version

> **Problem:** You have `n` versions `[1, 2, ..., n]`. One version is bad, and all versions after it are also bad. Find the first bad version using an API `isBadVersion(version)`. Example: `n = 5, firstBad = 4` → `4`.

> **Brute force:** Check versions 1, 2, 3... until the first bad one is found. O(n) API calls, O(1) space.
> **Key insight:** All bad versions form a suffix (once bad, stays bad) — the condition `isBadVersion` is monotonic. Bisect-left finds the exact flip point.
> **Approach:** Bisect-left on the condition `isBadVersion(mid)`. When true, `hi = mid`. When false, `lo = mid + 1`.

```java
int lo = 1, hi = n;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    // Bad version found — answer is mid or earlier
    if (isBadVersion(mid)) {
        hi = mid;
    } else {
        // Good version — first bad must be to the right
        lo = mid + 1;
    }
}
// lo == hi converges to the first bad version
return lo;
```

**Complexity (optimal):** O(log n) API calls, O(1) space

---

### LC 33: Search in Rotated Sorted Array

> **Problem:** A sorted array is rotated at an unknown pivot. Find the index of a target value, or return -1. No duplicates. Example: `nums = [4,5,6,7,0,1,2], target = 0` → `4`.

> **Brute force:** Linear scan through the array. O(n) time, O(1) space.
> **Key insight:** One half is always fully sorted after rotation. Identify it with `nums[lo] <= nums[mid]`, then check if target falls in that sorted half to pick direction.
> **Approach:** Pattern 3 — check which half is sorted (`nums[lo] <= nums[mid]`), then check if target falls in the sorted half to decide direction.

```java
// Key decision: which half is sorted?
if (nums[lo] <= nums[mid]) {
    // Left half sorted — is target in [lo, mid)?
    if (target >= nums[lo] && target < nums[mid]) hi = mid - 1;
    else lo = mid + 1;
} else {
    // Right half sorted — is target in (mid, hi]?
    if (target > nums[mid] && target <= nums[hi]) lo = mid + 1;
    else hi = mid - 1;
}
```

**Complexity (optimal):** O(log n) time, O(1) space

---

### LC 153: Find Minimum in Rotated Sorted Array

> **Problem:** Find the minimum element in a rotated sorted array (no duplicates). Example: `nums = [3,4,5,1,2]` → `1`.

> **Brute force:** Linear scan, track the minimum element seen so far. O(n) time, O(1) space.
> **Key insight:** The minimum is at the rotation point (the one "drop"). Compare `nums[mid]` with `nums[hi]`: if `nums[mid] > nums[hi]`, the drop is to the right of mid; otherwise mid could be the minimum.
> **Approach:** The minimum is at the rotation point. Compare `nums[mid]` with `nums[hi]`: if `nums[mid] > nums[hi]`, the minimum is in the right half; otherwise it's in the left half (including mid).

```java
int lo = 0, hi = nums.length - 1;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    // mid > hi means the rotation point (minimum) is to the right
    if (nums[mid] > nums[hi]) {
        lo = mid + 1;
    } else {
        // mid could be the minimum, so keep it in range
        hi = mid;
    }
}
// lo == hi converges to the minimum element
return nums[lo];
```

**Complexity (optimal):** O(log n) time, O(1) space

---

### LC 162: Find Peak Element

> **Problem:** A peak element is strictly greater than its neighbors. Find any peak's index. The array may have multiple peaks. `nums[-1] = nums[n] = -∞`. Example: `nums = [1,2,3,1]` → `2`.

> **Brute force:** Check every element against its neighbors to find where it is strictly greater than both. O(n) time, O(1) space.
> **Key insight:** Moving toward the uphill neighbor always leads to a peak — the -∞ at both boundaries guarantees one must exist in that direction.
> **Approach:** If `nums[mid] < nums[mid+1]`, a peak must exist to the right (uphill direction). Otherwise, a peak exists at `mid` or to the left. Binary search converges to a peak.

```java
int lo = 0, hi = nums.length - 1;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    // Uphill slope — a peak must exist to the right
    if (nums[mid] < nums[mid + 1]) {
        lo = mid + 1;
    } else {
        // Downhill or flat — mid itself could be the peak
        hi = mid;
    }
}
return lo;
```

**Complexity (optimal):** O(log n) time, O(1) space

---

### LC 875: Koko Eating Bananas

> **Problem:** Koko eats from banana piles at speed `k` per hour (one pile at a time). Find the minimum `k` to finish all piles within `h` hours. Example: `piles = [3,6,7,11], h = 8` → `4`.

> **Brute force:** Try every speed from 1 to max(piles), simulate hours needed at each speed. O(n × max(piles)) time, O(1) space.
> **Key insight:** If speed k is fast enough, any speed > k is also fast enough — the feasibility condition is monotonic. Binary search on the speed space.
> **Approach:** Pattern 4 — binary search on answer space `[1, max(piles)]`. Feasibility: for each pile, `ceil(pile/k)` hours. Sum ≤ h means feasible.

```java
// Ceiling division trick — no floating point
hours += (pile + speed - 1) / speed;
// Binary search: if feasible → hi = mid, else → lo = mid + 1
```

**Complexity (optimal):** O(n log max(piles)) time, O(1) space

---

### LC 1011: Capacity to Ship Packages Within D Days

> **Problem:** Conveyor belt has packages with weights. Ship all packages in order within `d` days. Find the minimum ship capacity. Example: `weights = [1,2,3,4,5,6,7,8,9,10], days = 5` → `15`.

> **Brute force:** Try every capacity from max(weights) to sum(weights), simulate days needed at each. O(n × sum(weights)) time, O(1) space.
> **Key insight:** If capacity C is enough to ship in d days, any capacity > C also works — monotonic. Binary search on capacity space.
> **Approach:** Binary search on capacity `[max(weights), sum(weights)]`. Feasibility: greedily load packages until capacity exceeded, count days.

```java
// Feasibility check: can we ship everything in ≤ d days at this capacity?
int days = 1, load = 0;
for (int w : weights) {
    // Package won't fit on current day — start a new day
    if (load + w > capacity) {
        days++;
        load = 0;
    }
    load += w;
}
return days <= d;
```

**Complexity (optimal):** O(n log sum(weights)) time, O(1) space

---

### LC 74: Search a 2D Matrix

> **Problem:** Each row is sorted left to right. First integer of each row > last integer of previous row. Determine if a target exists. Example: `matrix = [[1,3,5,7],[10,11,16,20],[23,30,34,60]], target = 3` → `true`.

> **Brute force:** Check every cell in the matrix. O(m × n) time, O(1) space.
> **Key insight:** Row-by-row order is globally sorted (last of row i < first of row i+1), so the entire matrix is one flat sorted array. Map 1D index to 2D with `row = mid / cols`, `col = mid % cols`.
> **Approach:** Pattern 5 — treat as 1D sorted array of `rows * cols` elements. Convert index: `row = mid / cols`, `col = mid % cols`.

```java
// Convert 1D index to 2D: row = mid/cols, col = mid%cols
int val = matrix[mid / cols][mid % cols];
// Then standard binary search comparison
```

**Complexity (optimal):** O(log(m × n)) time, O(1) space

---

### LC 410: Split Array Largest Sum

> **Problem:** Split array into `k` non-empty contiguous subarrays to minimize the largest subarray sum. Example: `nums = [7,2,5,10,8], k = 2` → `18` (split as [7,2,5] and [10,8]).

> **Brute force:** Try every possible max-sum limit, check if k splits are achievable at that limit. Exponential time.
> **Key insight:** If a max-sum limit M allows splitting into ≤ k parts, any M' > M also allows it — monotonic. Binary search on M in range `[max(nums), sum(nums)]`.
> **Approach:** Binary search on the answer (the largest sum allowed). `lo = max(nums)`, `hi = sum(nums)`. Feasibility: greedily fill subarrays — if current sum exceeds candidate, start a new subarray. Count subarrays ≤ k?

```java
// Feasibility: can we split into ≤ k parts with max sum ≤ candidate?
int parts = 1, currentSum = 0;
for (int num : nums) {
    // Adding this element would exceed the candidate limit — start a new part
    if (currentSum + num > candidate) {
        parts++;
        currentSum = 0;
    }
    currentSum += num;
}
return parts <= k;
```

**Complexity (optimal):** O(n log sum(nums)) time, O(1) space

---

### LC 374: Guess Number Higher or Lower

> **Problem:** I pick a number from 1 to n. You call `guess(num)` which returns -1 (too high), 1 (too low), or 0 (correct). Find the number. Example: `n = 10, pick = 6` → `6`.

> **Brute force:** Try every number 1, 2, 3... until the API returns 0. O(n) guesses, O(1) space.
> **Key insight:** The API gives the same directional feedback as an array comparison (too high / too low / correct) — it's just Pattern 1 with the API call replacing `arr[mid] vs target`.
> **Approach:** Classic binary search on range `[1, n]`. Call `guess(mid)` instead of array comparison.

```java
int lo = 1, hi = n;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    // API returns: 0 = correct, 1 = guess too low, -1 = guess too high
    int result = guess(mid);
    if (result == 0) {
        return mid;
    } else if (result == 1) {
        // Picked number is higher — search right
        lo = mid + 1;
    } else {
        // Picked number is lower — search left
        hi = mid - 1;
    }
}
```

**Complexity (optimal):** O(log n) guesses, O(1) space

---

### LC 35: Search Insert Position

> **Problem:** Given a sorted array and target, return the index where target is found or would be inserted. Example: `nums = [1,3,5,6], target = 5` → `2`. `target = 2` → `1`.

> **Brute force:** Scan left to right, return the first index where the element is >= target. O(n) time, O(1) space.
> **Key insight:** "Where would target be inserted?" is the same as "first index where arr[i] >= target" — that's exactly bisect-left.
> **Approach:** Bisect-left. Find the first position where `nums[mid] >= target`. Same as `bisectLeft` template.

```java
// hi = nums.length (not length-1) because insertion point can be past the end
int lo = 0, hi = nums.length;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (nums[mid] < target) {
        lo = mid + 1;
    } else {
        // nums[mid] >= target — this is a candidate insertion point
        hi = mid;
    }
}
// lo = first index where nums[lo] >= target (or nums.length if all are smaller)
return lo;
```

**Complexity (optimal):** O(log n) time, O(1) space

---

### LC 81: Search in Rotated Sorted Array II (with duplicates)

> **Problem:** Same as LC 33 but array may contain duplicates. Return true if target exists. Example: `nums = [2,5,6,0,0,1,2], target = 0` → `true`.

> **Brute force:** Linear scan. O(n) time, O(1) space.
> **Key insight:** Same as LC 33 but duplicates can make both `nums[lo] == nums[mid]` AND `nums[mid] == nums[hi]` — you can't tell which half is sorted. Shrink both ends by 1 and try again.
> **Approach:** Same as LC 33 but when `nums[lo] == nums[mid] == nums[hi]`, you can't determine which half is sorted. Shrink both: `lo++, hi--`. Worst case O(n).

```java
// Extra condition before the normal rotated-array logic:
// All three equal — can't determine which half is sorted, shrink both ends
if (nums[lo] == nums[mid] && nums[mid] == nums[hi]) {
    lo++;
    hi--;
    continue;
}
```

**Complexity (optimal):** O(log n) avg / O(n) worst (all duplicates) time, O(1) space

---

### LC 240: Search a 2D Matrix II

> **Problem:** Each row is sorted left→right, each column sorted top→bottom. But first element of next row is NOT necessarily > last of previous. Find target. Example: `matrix, target = 5` → `true`.

> **Brute force:** Check every cell. O(m × n) time, O(1) space.
> **Key insight:** Top-right corner is larger than everything in its column and smaller than everything in its row — one comparison eliminates an entire row or column. This is NOT the 1D-flatten trick; rows are not globally ordered here.
> **Approach:** Staircase search (NOT the 1D-flatten trick from LC 74). Start at top-right corner. If value > target → go left. If value < target → go down. O(m + n).

```java
// Start at top-right corner — each step eliminates a row or column
int row = 0, col = matrix[0].length - 1;
while (row < matrix.length && col >= 0) {
    if (matrix[row][col] == target) {
        return true;
    } else if (matrix[row][col] > target) {
        // Too large — eliminate this column
        col--;
    } else {
        // Too small — eliminate this row
        row++;
    }
}
return false;
```

**Complexity (optimal):** O(m + n) time, O(1) space

---

### LC 981: Time Based Key-Value Store

> **Problem:** Design a key-value store where each key can hold multiple values, each stored with a timestamp. `set(key, value, timestamp)` stores the value. `get(key, timestamp)` returns the value whose timestamp is the **largest timestamp ≤ the given timestamp**, or `""` if none exists. Timestamps in `set()` are always increasing (guaranteed).

> **Brute force:** For each key, store a list of `(timestamp, value)` pairs. On `get`, scan the list backwards from the end to find the latest timestamp ≤ target. O(n) per `get`.

> **Key insight:** Since `set()` always provides increasing timestamps, the list per key is always sorted. This makes it a classic **bisect-right - 1** problem — find the rightmost position where `timestamp ≤ target`, then step back one. Two implementations: TreeMap (uses `floorKey()` — cleanest) or manual binary search on a List.

> **Approach A — TreeMap (cleanest):** `HashMap<String, TreeMap<Integer, String>>`. `set()` does `treeMap.put(timestamp, value)`. `get()` does `treeMap.floorKey(timestamp)` — returns the largest key ≤ timestamp, or null if none.

```java
class TimeMap {
    private Map<String, TreeMap<Integer, String>> map;

    public TimeMap() {
        map = new HashMap<>();
    }

    public void set(String key, String value, int timestamp) {
        // computeIfAbsent: create TreeMap only if key is new
        map.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
    }

    public String get(String key, int timestamp) {
        if (!map.containsKey(key)) {
            return "";
        }
        TreeMap<Integer, String> timeMap = map.get(key);
        // floorKey: largest key <= timestamp, null if none
        Integer floor = timeMap.floorKey(timestamp);
        return floor == null ? "" : timeMap.get(floor);
    }
}
```

> **Approach B — Binary Search (teaches the pattern):** `HashMap<String, List<Integer>>` for timestamps + `HashMap<String, List<String>>` for values. On `get`, bisect-right on the timestamp list — `lo` lands at the first index > target, so `lo - 1` is the answer.

```java
class TimeMap {
    private Map<String, List<Integer>> times;
    private Map<String, List<String>> values;

    public TimeMap() {
        times = new HashMap<>();
        values = new HashMap<>();
    }

    public void set(String key, String value, int timestamp) {
        times.computeIfAbsent(key, k -> new ArrayList<>()).add(timestamp);
        values.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public String get(String key, int timestamp) {
        if (!times.containsKey(key)) {
            return "";
        }
        List<Integer> ts = times.get(key);
        // Bisect-right: find first index where ts[mid] > timestamp
        int lo = 0, hi = ts.size();
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (ts.get(mid) <= timestamp) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        // lo is first index > timestamp; lo-1 is latest index <= timestamp
        return lo == 0 ? "" : values.get(key).get(lo - 1);
    }
}
```

**Complexity (optimal):** O(1) set, O(log n) get — both approaches. TreeMap approach is cleaner; binary search approach is better if you forget `floorKey()`.

**When the interviewer asks:** "Why not just sort on every get?" → because `set()` guarantees increasing timestamps, so the list is always already sorted — no sort needed.

---

## ⚠️ Interview Gotchas

### Edge cases interviewers probe
- **Empty array** — return -1 or appropriate default
- **Single element** — `lo == hi` on first iteration
- **Target not found** — bisect returns insertion point, not the target itself — verify `arr[lo] == target` after
- **All elements identical** — bisect left returns first, bisect right returns last+1
- **Rotated by 0** (not actually rotated) — the `nums[lo] <= nums[mid]` check still works because the whole array is the "sorted left half"

### The `lo < hi` vs `lo <= hi` trap (most common bug)
- `lo <= hi` → search until exhausted, shrink both sides (`hi = mid - 1`, `lo = mid + 1`). Used when you return INSIDE the loop on exact match.
- `lo < hi` → converge to a single point. Used for bisect-left/right and answer-space search where you return AFTER the loop (`return lo`). **Never use `hi = mid - 1` with this variant** — it skips candidates.

### Follow-up questions to expect
- "What if there are duplicates?" (LC 81 — rotated with duplicates, worst case O(n))
- "Can you do this in O(log n) space?" — binary search is already O(1) space
- "What if the array is too large to fit in memory?" — binary search only needs `lo`, `hi`, `mid` — stream-friendly
- "What's the time complexity of your feasibility check?" — this determines total complexity

### Complexity traps
- LC 81 (rotated with duplicates): looks O(log n) but worst case is O(n) when all elements are the same — interviewer will ask about this
- Answer-space problems: total complexity is O(n · log(answer_range)) — don't forget the feasibility check cost

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**
For each problem description, name the pattern in under 5 seconds:

1. "Find target in a sorted array" → ___
2. "Find the first occurrence of 5 in [1,3,5,5,5,7]" → ___
3. "Minimum speed to eat all bananas in H hours" → ___
4. "Search in a rotated sorted array" → ___
5. "Find the peak element" → ___

**Part 2 — Write the Template (3 minutes)**
From memory, write the bisect-left template. Then write the answer-space template.

**Part 3 — Adapt (3 minutes)**
Solve LC 278 (First Bad Version) using bisect-left. Time yourself.

**Scoring:**
- Part 1: 5/5 correct → ready. <4 → re-read mental model.
- Part 2: Both templates correct with `lo < hi` and `hi = mid` → ready. Used `lo <= hi` for bisect → re-read Pattern 2.
- Part 3: Under 2 minutes → ready. Over 3 minutes → drill the template more.

---

## 🔗 Cross-References

- **Companion Reference:** `../Reference/dsa-collections-notes.md` — array method signatures
- **Integer overflow:** `../DeepDive/integer-overflow-and-limits.md` — why `lo + (hi - lo) / 2` instead of `(lo + hi) / 2`
- **Two Pointers:** `../Interview/two-pointers-and-sliding-window.md` — converging pointers on sorted arrays (related but different)
- **Arrays & Hashing:** `../Interview/arrays-and-hashing.md` — when the problem is about elements, not positions

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Binary Search Interview Playbook — 5 patterns (Classic, Bisect Left/Right, Rotated Array, Answer Space, Matrix), canonical walkthrough (LC 875 Koko Eating Bananas), 10 problems with expanded definitions. |
| June 2026 | **Brute force + optimal complexity added** to all 5 pattern sections and all 14 problem bank entries. Format: brute force baseline → optimal with explanation of what drives the log factor. |
| June 2026 | **LC 981 Time Based Key-Value Store added.** Two implementations: TreeMap with `floorKey()` (cleanest) and manual bisect-right on List (teaches the pattern). Added pre-DocuSign interview gap fill. |
