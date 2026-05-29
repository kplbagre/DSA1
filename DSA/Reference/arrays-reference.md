# Arrays — Reference

> Compact daily-revision companion to `DSA/DeepDive/arrays-fundamentals.md`. This is the file Kapil opens 5 minutes before an interview — every pattern, every gotcha, one decision table.

---

## ⚡ Imports — Write These First on a Blank Notepad

```java
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.Collections;
import java.util.stream.Collectors;
```

> **On LeetCode** these are pre-imported. On a **plain notepad / Google Doc / whiteboard**, write these first — they cover every pattern in this file.

---

## 🎯 The Mental Model (10 Seconds)

**Arrays have two spaces: index and value.** The most clever array tricks come from using ONE space as auxiliary storage for facts about the OTHER:

| Trick | Index → Value | Value → Index |
| --- | --- | --- |
| **Cyclic sort** | Place value `v` at index `v − 1` | Value's "home" is determined by value itself |
| **Marking by negation** | Negate `nums[v]` to mark "v was seen" | The sign at index `i` answers "did I see `i + 1`?" |
| **Set matrix zeros (O(1) space)** | First row/column = bookkeeping for the rest | The matrix itself stores the marks |

**Three questions for any array problem:**
1. Is the answer a **contiguous subarray**? → Sliding Window / Prefix Sum
2. Does the array have a **value-to-index mapping** (1..n)? → Cyclic Sort / Marking
3. Is there a **monotonicity** I can exploit? → Two Pointers / Kadane's

---

## 🧭 The 14 Core Patterns

---

### 1. Two Pointers Converging ✅ [V-2, V-5]

> Sorted array. Two pointers from opposite ends move toward each other. Each step decides which pointer to move based on comparison with target.

```java
int left = 0;
int right = nums.length - 1;
while (left < right) {
    int sum = nums[left] + nums[right];
    if (sum == target) {
        return new int[]{ left, right };
    }
    if (sum < target) {
        left++;
    } else {
        right--;
    }
}
```

**🏷️ Example problems:** LC 167 (Two Sum II), LC 15 (3Sum), LC 11 (Container With Most Water), LC 42 (Trapping Rain Water), LC 125 (Valid Palindrome), LC 26 (Remove Duplicates).

---

### 2. Two Pointers Same-Direction ✅ [V-1, V-2]

> `slow` marks write position; `fast` scans. In-place dedup, partition, removal.

```java
int slow = 0;
for (int fast = 1; fast < nums.length; fast++) {
    if (nums[fast] != nums[slow]) {
        slow++;
        nums[slow] = nums[fast];
    }
}
return slow + 1;
```

**🏷️ Example problems:** LC 26 (Remove Duplicates Sorted), LC 27 (Remove Element), LC 283 (Move Zeroes), LC 80 (Remove Duplicates II).

---

### 3. Sliding Window Fixed ✅

> Window of exactly size `k`. Build first window, then slide: add new right, remove old left.

```java
long sum = 0;
for (int i = 0; i < k; i++) {
    sum += nums[i];
}
long best = sum;
for (int right = k; right < n; right++) {
    sum += nums[right];
    sum -= nums[right - k];
    best = Math.max(best, sum);
}
```

**🏷️ Example problems:** LC 643 (Max Avg Subarray I), LC 1423 (Max Points from Cards), LC 438 (Find All Anagrams), LC 567 (Permutation in String).

> **Cross-ref:** Full coverage in `DSA/Reference/two-pointers-sliding-window-reference.md`.

---

### 4. Sliding Window Variable ⭐ [V-4]

> Expand always; while invalid, shrink from left. Record `right − left + 1` after shrinking. The workhorse pattern.

```java
int left = 0;
int answer = 0;
for (int right = 0; right < n; right++) {
    addToState(nums[right]);
    while (windowInvalid()) {
        removeFromState(nums[left]);
        left++;
    }
    answer = Math.max(answer, right - left + 1);
}
```

**🏷️ Example problems:** LC 3 (Longest Substring Without Repeat), LC 1004 (Max Consecutive Ones III), LC 904 (Fruit Into Baskets), LC 340 (Longest Substring At Most K Distinct), LC 424 (Longest Repeating Character Replacement).

> **Cross-ref:** Full Sliding Window playlist coverage in `DSA/Reference/two-pointers-sliding-window-reference.md`.

---

### 5. Prefix Sum Raw [V-17]

> Precompute cumulative sums so any range sum is `prefix[r+1] − prefix[l]` in O(1).

```java
long[] prefix = new long[n + 1];
for (int i = 0; i < n; i++) {
    prefix[i + 1] = prefix[i] + nums[i];
}
// sum of nums[l..r] inclusive:
long rangeSum = prefix[r + 1] - prefix[l];
```

**🏷️ Example problems:** LC 303 (Range Sum Query Immutable), LC 304 (2D Range Sum), LC 1480 (Running Sum), LC 724 (Find Pivot Index).

---

### 6. Prefix Sum + HashMap ⭐ [V-17, V-22]

> Counting subarrays with a property: store cumulative state in a map, look up `(currentState − target)` to find valid starts.

```java
Map<Long, Integer> count = new HashMap<>();
count.put(0L, 1);                        // empty prefix
long prefix = 0;
int answer = 0;
for (int x : nums) {
    prefix += x;
    answer += count.getOrDefault(prefix - target, 0);
    count.merge(prefix, 1, Integer::sum);
}
```

**🏷️ Example problems:** LC 560 (Subarray Sum Equals K), LC 974 (Subarray Sums Divisible by K), LC 525 (Contiguous Array), LC 1248 (also solvable with sliding window).

> **Mental model:** *"If two prefixes differ by `target`, the subarray between them sums to `target`."*

---

### 7. Hashing / Frequency Map [V-5, V-13]

> Store frequencies of values for O(1) lookup. Used when order doesn't matter or when you need to know "have I seen this before?".

```java
Map<Integer, Integer> freq = new HashMap<>();
for (int x : nums) {
    freq.merge(x, 1, Integer::sum);
}

// Set version — "have I seen this?"
Set<Integer> seen = new HashSet<>();
for (int x : nums) {
    if (!seen.add(x)) {
        return true;          // duplicate
    }
}
```

**🏷️ Example problems:** LC 1 (Two Sum), LC 217 (Contains Duplicate), LC 49 (Group Anagrams), LC 128 (Longest Consecutive Sequence).

---

### 8. Kadane's Algorithm ⭐ [V-8]

> Track the best subarray ending at each index. At each step, either extend the previous subarray or start fresh from current element.

```java
long best = nums[0];
long current = nums[0];
for (int i = 1; i < nums.length; i++) {
    current = Math.max(nums[i], current + nums[i]);
    best = Math.max(best, current);
}
return (int) best;
```

**🏷️ Example problems:** LC 53 (Maximum Subarray), LC 152 (Maximum Product Subarray — two-state Kadane), LC 918 (Circular Subarray), LC 1186 (Max Subarray with One Deletion).

> **Mental hook:** *"Should I extend or start over? Whichever gives a bigger sum at this index."*

---

### 9. Dutch National Flag ✅ [V-6]

> Three pointers (`low`, `mid`, `high`) partition the array into three regions in one pass. Used for 0/1/2 sorting, color sorting, three-way partition.

```java
int low = 0;
int mid = 0;
int high = nums.length - 1;
while (mid <= high) {
    if (nums[mid] == 0) {
        int t = nums[low]; nums[low] = nums[mid]; nums[mid] = t;
        low++;
        mid++;
    } else if (nums[mid] == 1) {
        mid++;
    } else {
        int t = nums[mid]; nums[mid] = nums[high]; nums[high] = t;
        high--;
        // don't advance mid — the swapped element is unverified
    }
}
```

**🏷️ Example problems:** LC 75 (Sort Colors), LC 905 (Sort Array By Parity), LC 215 (Quickselect — 3-way variant).

> **Invariant:** `[0..low-1]` = 0s, `[low..mid-1]` = 1s, `[mid..high]` = unknown, `[high+1..n-1]` = 2s.

---

### 10. Moore's Voting ✅ [V-7, V-19]

> One-pass majority element: cancel out non-majority votes. Works because the majority occurs more than `n/2` times — survives all cancellations.

```java
int candidate = 0;
int count = 0;
for (int x : nums) {
    if (count == 0) {
        candidate = x;
    }
    count += (x == candidate) ? 1 : -1;
}
return candidate;
```

**🏷️ Example problems:** LC 169 (Majority Element), LC 229 (Majority Element II — two candidates for `> n/3`).

> **Mental hook:** *"A majority element can survive being cancelled by every non-majority. Track the survivor."*

---

### 11. Cyclic Sort ✅ [V-3]

> When values are `1..n` (or `0..n-1`), each value has a "home" index. One pass swaps each value into its home; mismatches reveal duplicates/missing.

```java
int i = 0;
while (i < nums.length) {
    int correctIndex = nums[i] - 1;            // 1..n → 0..n-1
    if (nums[i] != nums[correctIndex]) {
        int t = nums[i]; nums[i] = nums[correctIndex]; nums[correctIndex] = t;
    } else {
        i++;
    }
}
// After: any index i where nums[i] != i + 1 → that's a missing/duplicate slot.
```

**🏷️ Example problems:** LC 268 (Missing Number), LC 287 (Find the Duplicate), LC 41 (First Missing Positive), LC 442 (Find All Duplicates), LC 448 (Find All Numbers Disappeared).

> **Variant — Marking by Negation:** for `1..n` problems where mutation is allowed, negate `nums[abs(v) - 1]` to mark "seen". Indices that stay positive reveal missing values.

```java
for (int v : nums) {
    int idx = Math.abs(v) - 1;
    if (nums[idx] > 0) {
        nums[idx] = -nums[idx];
    }
}
// Indices where nums[i] > 0 → value (i+1) was never seen.
```

---

### 12. Matrix Patterns [V-14, V-15, V-16]

#### 12a. Set Matrix Zeros — O(1) Space

> Use the first row and first column as the "memo" of which rows/columns to zero. Treat separately whether the first row/column itself should be zeroed.

```java
int m = matrix.length;
int n = matrix[0].length;
boolean firstRowZero = false;
boolean firstColZero = false;

for (int j = 0; j < n; j++) {
    if (matrix[0][j] == 0) firstRowZero = true;
}
for (int i = 0; i < m; i++) {
    if (matrix[i][0] == 0) firstColZero = true;
}

for (int i = 1; i < m; i++) {
    for (int j = 1; j < n; j++) {
        if (matrix[i][j] == 0) {
            matrix[i][0] = 0;
            matrix[0][j] = 0;
        }
    }
}

for (int i = 1; i < m; i++) {
    for (int j = 1; j < n; j++) {
        if (matrix[i][0] == 0 || matrix[0][j] == 0) {
            matrix[i][j] = 0;
        }
    }
}

if (firstRowZero) {
    for (int j = 0; j < n; j++) matrix[0][j] = 0;
}
if (firstColZero) {
    for (int i = 0; i < m; i++) matrix[i][0] = 0;
}
```

**🏷️ Example:** LC 73 (Set Matrix Zeroes).

#### 12b. Rotate Matrix 90° — Transpose + Reverse Rows

```java
// Transpose (swap across diagonal)
for (int i = 0; i < n; i++) {
    for (int j = i + 1; j < n; j++) {
        int t = matrix[i][j]; matrix[i][j] = matrix[j][i]; matrix[j][i] = t;
    }
}
// Reverse each row
for (int i = 0; i < n; i++) {
    int l = 0, r = n - 1;
    while (l < r) {
        int t = matrix[i][l]; matrix[i][l] = matrix[i][r]; matrix[i][r] = t;
        l++; r--;
    }
}
```

**🏷️ Example:** LC 48 (Rotate Image).

#### 12c. Spiral Matrix

```java
int top = 0, bottom = m - 1, left = 0, right = n - 1;
List<Integer> result = new ArrayList<>();
while (top <= bottom && left <= right) {
    for (int j = left; j <= right; j++) result.add(matrix[top][j]);
    top++;
    for (int i = top; i <= bottom; i++) result.add(matrix[i][right]);
    right--;
    if (top <= bottom) {
        for (int j = right; j >= left; j--) result.add(matrix[bottom][j]);
        bottom--;
    }
    if (left <= right) {
        for (int i = bottom; i >= top; i--) result.add(matrix[i][left]);
        left--;
    }
}
```

**🏷️ Example:** LC 54 (Spiral Matrix), LC 59 (Spiral Matrix II — generate).

---

### 13. Interval Patterns [V-23]

> Sort by start time, then sweep. Merge overlapping or detect conflicts.

```java
Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
List<int[]> merged = new ArrayList<>();
for (int[] interval : intervals) {
    if (merged.isEmpty() || merged.get(merged.size() - 1)[1] < interval[0]) {
        merged.add(interval);
    } else {
        merged.get(merged.size() - 1)[1] = Math.max(
            merged.get(merged.size() - 1)[1],
            interval[1]
        );
    }
}
return merged.toArray(new int[0][]);
```

**🏷️ Example problems:** LC 56 (Merge Intervals), LC 57 (Insert Interval), LC 252 (Meeting Rooms), LC 253 (Meeting Rooms II), LC 435 (Non-overlapping Intervals).

---

### 14. Modified Merge Sort 🔴 [V-26, V-27]

> When the answer depends on **pairs across two sorted halves**, count contributions during merge and combine recursively.

```java
public int mergeSortCount(int[] nums, int l, int r) {
    if (l >= r) return 0;
    int mid = l + (r - l) / 2;
    int count = mergeSortCount(nums, l, mid) + mergeSortCount(nums, mid + 1, r);
    // Count cross-pairs (left half × right half) BEFORE merging
    count += countCrossPairs(nums, l, mid, r);
    merge(nums, l, mid, r);
    return count;
}
```

**🏷️ Example problems:** LC 493 (Reverse Pairs), Count Inversions, LC 315 (Count of Smaller Numbers After Self).

> **🔴 Stretch tier.** Skip for medium-interview prep.

---

## 🌳 Special Topics — Quick Reference

### Next Permutation [V-11]

```java
int i = nums.length - 2;
while (i >= 0 && nums[i] >= nums[i + 1]) i--;            // find pivot
if (i >= 0) {
    int j = nums.length - 1;
    while (nums[j] <= nums[i]) j--;                       // find swap target
    int t = nums[i]; nums[i] = nums[j]; nums[j] = t;
}
// reverse suffix
int l = i + 1, r = nums.length - 1;
while (l < r) {
    int t = nums[l]; nums[l] = nums[r]; nums[r] = t;
    l++; r--;
}
```

**🏷️ Example:** LC 31 (Next Permutation).

### XOR Tricks [V-3]

```java
// Find the single number — every other appears twice
int x = 0;
for (int v : nums) x ^= v;

// Properties:
// a ^ a = 0
// a ^ 0 = a
// XOR is associative + commutative
```

**🏷️ Example problems:** LC 136 (Single Number), LC 268 (Missing Number), LC 389 (Find the Difference).

### Longest Consecutive Sequence [V-13]

> Put all in a set, then only start counting from a value `v` if `v − 1` is NOT in the set (`v` is a "start of run").

```java
Set<Integer> set = new HashSet<>();
for (int v : nums) set.add(v);
int best = 0;
for (int v : set) {
    if (!set.contains(v - 1)) {
        int len = 1;
        int curr = v;
        while (set.contains(curr + 1)) {
            curr++;
            len++;
        }
        best = Math.max(best, len);
    }
}
```

**🏷️ Example:** LC 128 (Longest Consecutive Sequence).

### Pascal's Triangle [V-18]

```java
// Row i, column j = C(i, j) = nCk
List<List<Integer>> triangle = new ArrayList<>();
for (int i = 0; i < numRows; i++) {
    List<Integer> row = new ArrayList<>();
    long val = 1;
    row.add(1);
    for (int j = 1; j <= i; j++) {
        val = val * (i - j + 1) / j;
        row.add((int) val);
    }
    triangle.add(row);
}
```

**🏷️ Example:** LC 118 / LC 119 (Pascal's Triangle).

### Gap Method 🔴 [V-24]

> Merge two sorted arrays in O(1) extra space using a "gap" that halves each iteration. Compares pairs at distance `gap` across both arrays, swap if out-of-order.

**🏷️ Example:** LC 88 (Merge Sorted Array — O(1) variant).

### Missing + Repeating in 1..n 🔴 [V-25]

> Use math: sum and sum-of-squares give two equations in two unknowns. Or XOR with index trick.

**🏷️ Example:** Find Missing And Repeating (Striver classic).

---

## ⚡ State-Container Cheat Sheet

| State | Use | Add | Remove |
| --- | --- | --- | --- |
| Running sum | Subarray sums | `sum += nums[r]` | `sum -= nums[l]` |
| `int[26]` | Lowercase chars | `freq[c-'a']++` | `freq[c-'a']--` |
| `Map<Integer, Integer>` | General freq | `map.merge(v, 1, Integer::sum)` | decrement + cleanup |
| `Map<Long, Integer>` (prefix → count) | Prefix Sum + HashMap | `count.merge(prefix, 1, Integer::sum)` | n/a (no removal) |
| `Set<Integer>` | "Have I seen this?" | `set.add(v)` | `set.remove(v)` |
| `int candidate, int count` | Moore's voting | `count++` if match else `count--` | n/a |
| `long[] prefix` | Range queries | precompute once | precompute once |

---

## ⚡ Pattern-Picker Decision Tree

```
Array problem
│
├── Sorted array + pair/triplet     → Two Pointers Converging
├── In-place modification           → Two Pointers Same-Direction
├── Subarray with sum / count       → Prefix Sum (raw or + HashMap)
├── Contiguous + monotone constraint → Sliding Window
├── Max sum subarray                → Kadane's
├── Sort 0/1/2 or partition into 3  → Dutch National Flag
├── Majority element (> n/2)        → Moore's Voting
├── Values are 1..n                 → Cyclic Sort / Marking by Negation
├── Matrix in-place ops             → First row/col as memo
├── Interval merging / scheduling    → Sort by start + sweep
├── "Have I seen X before?"         → HashSet / HashMap
├── Cross-pair count                → Modified Merge Sort 🔴
└── Lex-next permutation            → Pivot + swap + reverse suffix
```

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

---

**`int` sum overflow.**

```java
int sum = 0;                                 // ❌ n=1e5, vals=1e9 overflow
long sum = 0;                                // ✅
```

---

**`Math.abs(Integer.MIN_VALUE)` returns Integer.MIN_VALUE.**

```java
Math.abs(-2147483648);                       // ❌ still -2147483648 (overflow)
Math.abs((long) -2147483648);                // ✅ widen first
```

---

**`Arrays.asList(int[])` returns `List<int[]>`, not `List<Integer>`.**

```java
List<Integer> list = Arrays.asList(arr);     // ❌ compile error — wrong type
List<Integer> list = Arrays.stream(arr).boxed().collect(Collectors.toList());     // ✅
```

---

**`arr.toString()` prints memory address.**

```java
System.out.println(arr.toString());          // "[I@1540e19d" ❌
System.out.println(Arrays.toString(arr));    // "[1, 2, 3]" ✅
```

---

**Sorting `Integer[]` with subtraction comparator overflows.**

```java
Arrays.sort(boxedArr, (a, b) -> a - b);                   // ❌ overflow for extremes
Arrays.sort(boxedArr, (a, b) -> Integer.compare(a, b));   // ✅
Arrays.sort(boxedArr, Comparator.naturalOrder());         // ✅
```

---

**`Arrays.sort(int[])` is NOT stable AND uses dual-pivot quicksort → O(n²) worst case on adversarial input.**

```java
Arrays.sort(intArr);                         // mostly fine, but watch for stability
Arrays.sort(boxedArr);                       // stable mergesort — safer for objects
```

---

**Autoboxing in tight inner loops kills performance.**

```java
Integer sum = 0;                             // ❌ boxes on every +
for (int v : nums) sum += v;
int sum = 0;                                 // ✅ no boxing
```

---

**ConcurrentModificationException on `int[]`?** No — `int[]` doesn't throw it. But mutating `ArrayList` during a for-each does.

```java
for (Integer x : list) {
    if (x == 0) list.remove(x);              // ❌ ConcurrentModificationException
}
Iterator<Integer> it = list.iterator();
while (it.hasNext()) {
    if (it.next() == 0) it.remove();         // ✅
}
```

---

**Mutating the array AND counting on it later.**

```java
// Marking by negation destroys original values
// If you need both the answer AND the original array, copy first
int[] copy = nums.clone();                   // ✅
```

---

**Cyclic sort with values 1..n requires the swap loop, not a `for` loop.**

```java
for (int i = 0; i < n; i++) {
    if (nums[i] != i + 1) swap(...);         // ❌ misses second swap
}
int i = 0;
while (i < n) {
    int correct = nums[i] - 1;
    if (nums[i] != nums[correct]) {
        swap(nums, i, correct);              // don't increment — re-check
    } else {
        i++;                                 // ✅
    }
}
```

---

**Off-by-one on subarray length: `right − left + 1` (inclusive both ends).**

```java
int len = right - left;                      // ❌
int len = right - left + 1;                  // ✅
```

---

**Forgetting to handle empty input.**

```java
public int maxSubArray(int[] nums) {
    long best = nums[0];                     // ❌ NPE/IndexOutOfBounds on empty
    // ✅ validate at top:
    if (nums == null || nums.length == 0) return 0;
}
```

---

**`Arrays.copyOfRange(arr, from, to)` is exclusive on `to`.**

```java
int[] sub = Arrays.copyOfRange(nums, l, r);     // gets [l..r-1], not [l..r]
int[] sub = Arrays.copyOfRange(nums, l, r + 1); // ✅ for inclusive r
```

---

## ⚡ Quick Cheat Sheet

| If you need... | Use... | Why |
| --- | --- | --- |
| Sum of subarray `[l..r]` in O(1) | Prefix sum | Precompute cumulative |
| Count subarrays with sum = K | Prefix sum + HashMap | `(prefix - K)` lookup |
| Longest subarray sum ≤ K (positives) | Sliding window | Monotone in window size |
| Pair sum in sorted array | Two pointers converging | O(n), no extra space |
| In-place dedup / move zeros | Two pointers same-direction | Slow-write, fast-read |
| Max subarray sum | Kadane's | Two-state DP |
| Max product subarray | Two-state Kadane (max + min) | Negatives flip sign |
| Sort 0/1/2 in one pass | Dutch National Flag | Three-pointer partition |
| Majority element > n/2 | Moore's voting | Cancel non-majority votes |
| First missing positive (O(1) space) | Cyclic sort | Values 1..n → home indices |
| Find duplicate in 1..n | Cyclic sort or marking | Or Floyd's cycle |
| Set matrix zeros (O(1) space) | First row/col as memo | Use array itself |
| Rotate matrix 90° in place | Transpose + reverse rows | Two-step decomposition |
| Merge overlapping intervals | Sort by start + sweep | O(n log n) |
| Longest consecutive sequence O(n) | HashSet + start-of-run check | Skip mids |
| Single number (others appear twice) | XOR all | `a ^ a = 0` |
| Cross-pair counting | Modified merge sort 🔴 | O(n log n) divide-and-conquer |

---

## 🗺️ Practice Plan — At-a-Glance

| Tier | Goal | Top 3 Problems |
| --- | --- | --- |
| **1 — Foundations** ✅ | Templates from blank file | LC 1 (Two Sum), LC 26 (Remove Duplicates), LC 53 (Kadane) |
| **2 — Core Patterns** 🟡 | All 14 patterns recognized in 10 sec | LC 15 (3Sum), LC 75 (Sort Colors), LC 560 (Subarray Sum K) |
| **3 — Matrix + Intervals** 🟡 | In-place + sorting fluency | LC 48 (Rotate), LC 56 (Merge Intervals), LC 73 (Set Matrix Zeros) |
| **4 — Advanced Hashing + Optimization** 🟡 | Medium-interview ceiling | LC 128 (Longest Consecutive), LC 152 (Max Product), LC 31 (Next Permutation) |
| 🎯 **STOP — Medium-Interview Cutoff** 🎯 | | |
| **5 — Stretch 🔴** | Optional | LC 41, LC 287, LC 493, LC 315, LC 4 (Median of Two Sorted) |

---

## 🔗 Cross-References

| Concept | See File |
| --- | --- |
| Full deep dive (mental model, walkthroughs, all special topics) | `DSA/DeepDive/arrays-fundamentals.md` |
| Sliding window + Two Pointers (Patterns 1–4 expanded) | `DSA/Reference/two-pointers-sliding-window-reference.md` |
| HashMap idioms (`merge`, `getOrDefault`, `computeIfAbsent`) | `DSA/Reference/hashmap-section-updated.md` |
| HashSet idioms | `DSA/Reference/set-section-updated.md` |
| String operations | `DSA/Reference/string-operations-reference.md` |
| Lambdas / comparators / streams | `DSA/Reference/lambdas-for-dsa-reference.md` |
| Integer overflow + `long` for sums | `DSA/DeepDive/integer-overflow-and-limits.md` |
| Code style | `DSA/Reference/code-style-for-dsa-reference.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Initial version.** Companion to `arrays-fundamentals.md`. Compact pattern catalog of all 14 patterns + special topics + 13 gotchas + decision tree. |
