# Advanced DP — Interview Playbook

> **Read this file when:** You've covered `dp.md` (linear, grid, string, knapsack, counting) and need the next tier: LIS, patience-sort O(n log n), and interval DP. These appear in Google hard rounds and Meta onsite deep-dives. Prerequisite: `dp.md`.

---

## 🎯 Why You're Reading This

The five patterns in `dp.md` cover roughly 70% of FAANG DP problems. This file covers the remaining hard-tier patterns that separate strong candidates from exceptional ones:

- **LIS (Longest Increasing Subsequence)** — appears at Meta, Google, Amazon, and is often a building block inside harder problems (e.g., Russian Doll Envelopes). The O(n log n) patience sort (a card-solitaire technique — each pile tracks the smallest tail for subsequences of a given length) is the key flex.
- **Interval DP** — the Burst Balloons family. Conceptually distinct from all other DP: the subproblem is a *range* of the input, and you pick what to do LAST inside that range. Seen at Google and occasionally Meta.

After reading this file, you should be able to:
1. Recognize LIS problems by wording ("longest increasing," "maximum chain," "maximum envelopes")
2. Implement both O(n²) DP and O(n log n) patience sort from memory
3. Identify interval DP by the "range subproblem" structure and set up `dp[i][j]` correctly

---

## 🔧 Essential Methods — Know These Cold

| Method | What it does | Used in |
| --- | --- | --- |
| `Arrays.binarySearch(arr, 0, len, target)` | Binary search in range [0, len) — returns index or negative | Pattern 2 |
| `Arrays.copyOf(tails, len)` | Copy first `len` elements of array | Pattern 2 result |
| `Arrays.sort(envelopes, (a, b) -> ...)` | Sort with custom comparator | Pattern 3 |
| `Math.max(a, b)` | Standard max | All patterns |
| `Integer.compare(a, b)` | Overflow-safe comparison | Comparator lambdas |

---

## 🔄 Lambda & Shorthand Explanations with Fallbacks

**1. `Arrays.binarySearch(arr, fromIndex, toIndex, key)` — range binary search**

```java
// What it does:
//   Searches arr[fromIndex..toIndex) for key
//   Returns: index if found, or -(insertion point) - 1 if not found
//   Insertion point = index where key would be inserted to maintain sorted order
//
// We use it to find where to place a new element in the 'tails' array:
int pos = Arrays.binarySearch(tails, 0, size, nums[i]);
// If pos >= 0: exact match found at pos
// If pos < 0: not found; insertion point = -(pos) - 1
if (pos < 0) {
    pos = -(pos) - 1;  // convert to actual insertion index
}

// 🔄 Fallback — manual binary search (write this if Arrays.binarySearch feels uncertain):
int lo = 0, hi = size;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (tails[mid] < nums[i]) {
        lo = mid + 1;
    } else {
        hi = mid;
    }
}
int pos = lo;
```

**2. `Arrays.sort(envelopes, (a, b) -> ...)` — two-key comparator**

```java
// Sort envelopes: ascending width, then DESCENDING height at same width
// Descending height at same width prevents two same-width envelopes both entering LIS
Arrays.sort(envelopes, (a, b) -> a[0] == b[0] ? b[1] - a[1] : a[0] - b[0]);
// 🔄 Fallback (explicit Comparator):
Arrays.sort(envelopes, (a, b) -> {
    if (a[0] != b[0]) {
        return a[0] - b[0];     // ascending width
    }
    return b[1] - a[1];         // descending height at same width
});
```

---

## 🧠 The Mental Model — When to Think "Advanced DP"

```
Is the problem asking for a longest/maximum chain or sequence?
│
├── "Longest increasing subsequence" / "maximum chain" / "maximum envelopes"
│   ├── n ≤ 2,500 → O(n²) LIS DP (Pattern 1) — simpler to code
│   └── n > 2,500 or interviewer asks for O(n log n) → Patience Sort (Pattern 2)
│
└── "Optimal value for a range/interval of the input"?
    ├── State involves [i][j] where i and j are positions in the array
    ├── You split the interval at some point k and combine left+right results
    └── → Interval DP (Pattern 3+4)
        ├── "What should be done LAST inside this range?" → Burst Balloons style
        └── "Optimal cost to split/merge this range?" → Cut the Stick / Merge Stones
```

---

## 🧭 Pattern 1: LIS — Classic O(n²) DP ⭐

**What this solves:** Find the length (or the actual sequence) of the longest strictly increasing subsequence in an array. A subsequence means elements don't have to be adjacent — you can skip elements, but must maintain relative order.

**Recognition cues — reach for this when:**
- "Longest increasing subsequence"
- "Maximum number of envelopes you can nest" (each dimension increases)
- "Maximum chain of pairs" where each pair's end < next pair's start
- "Largest divisible subset" (divisibility creates an ordering)

**Brute force:** Generate all 2^n subsequences, check each is strictly increasing, return the longest. O(2^n) time — exponential, completely impractical for n > 30.

**Key insight:** At each position i, we only need to know: "what's the longest increasing subsequence that ends specifically at index i?" That's `dp[i]`. To compute it, look back at all previous positions j < i where `nums[j] < nums[i]` — we could extend any of those subsequences. Take the best one. This gives O(n²) with O(n) space.

**Steps in plain English:**

1. **Initialize:** `dp[i] = 1` for all i — every element alone is an LIS of length 1.
2. **Fill:** For each position i (left to right), scan all j < i. If `nums[j] < nums[i]`, update `dp[i] = max(dp[i], dp[j] + 1)`.
3. **Answer:** `max(dp)` — the longest LIS can end at any index.

```java
public int lengthOfLIS(int[] nums) {
    int n = nums.length;
    // Step 1 — every element alone is a valid LIS of length 1
    int[] dp = new int[n];
    Arrays.fill(dp, 1);
    int result = 1;
    for (int i = 1; i < n; i++) {
        // Step 2 — look back at all previous elements
        for (int j = 0; j < i; j++) {
            if (nums[j] < nums[i]) {
                // nums[i] can extend the subsequence ending at j
                dp[i] = Math.max(dp[i], dp[j] + 1);
            }
        }
        // Step 3 — track global maximum
        result = Math.max(result, dp[i]);
    }
    return result;
}
```

### 🎨 Visual — LIS DP Table

```
nums = [10, 9, 2, 5, 3, 7, 101, 18]
index:   0   1  2  3  4  5    6   7

For each i, look left at all j < i where nums[j] < nums[i]:

i=0: dp[0] = 1  (no j to look back at)
i=1: dp[1] = 1  (nums[0]=10 > nums[1]=9, skip)
i=2: dp[2] = 1  (no j where nums[j] < 2)
i=3: dp[3] = 2  (j=2: nums[2]=2 < 5 → dp[2]+1=2)
i=4: dp[4] = 2  (j=2: nums[2]=2 < 3 → dp[2]+1=2)
i=5: dp[5] = 3  (j=2: 2<7 → 2, j=3: 5<7 → 3, j=4: 3<7 → 3 → max=3)
i=6: dp[6] = 4  (j=5: 7<101 → dp[5]+1=4)
i=7: dp[7] = 4  (j=5: 7<18 → dp[5]+1=4)

  i:  0    1    2    3    4    5    6    7
nums: 10    9    2    5    3    7  101   18
  dp:  1    1    1    2    2    3    4    4
                      ↑         ↑
              5 extends [2]    7 extends [2,3] or [2,5]

Answer = max(dp) = 4
One valid LIS: [2, 3, 7, 101] or [2, 5, 7, 101] or [2, 5, 7, 18]

KEY INVARIANT:
   dp[i] = length of LIS that ENDS at index i (not just passes through it).
   The global answer is the maximum over all dp[i] — the LIS can end anywhere.
```

**Complexity (optimal):** O(n²) time, O(n) space — nested loops over all pairs (i, j)

**🏷️ Problems:** LC 300 (LIS), LC 673 (Number of Longest Increasing Subsequences), LC 368 (Largest Divisible Subset).

---

## 🧭 Pattern 2: LIS — O(n log n) Patience Sort ⭐

**What this solves:** Same problem as Pattern 1 — find the LIS length — but in O(n log n) time. Required when n is large (up to 100,000), or when the interviewer explicitly asks for the optimal solution. The `tails` array maintained here does NOT store an actual LIS — it stores the smallest possible tail for each length.

**Recognition cues — reach for this when:**
- Same as Pattern 1, but n > 2,500 or O(n log n) explicitly requested
- "Find LIS length" is a subroutine inside a harder problem (Russian Doll Envelopes)

**Brute force:** O(n²) DP as in Pattern 1.

**Key insight:** Maintain a `tails` array where `tails[k]` = the smallest tail element of all increasing subsequences of length `k+1` seen so far. For each new element: binary search for the leftmost position in `tails` where we can replace (improving future extensions). The length of `tails` at the end = LIS length. This is O(n log n) because each element does one binary search over `tails`.

> **Mental model for `tails`:** Think of solitaire. You deal cards onto piles. Each pile is strictly increasing (smaller tops a larger). When a new card comes, place it on the leftmost pile whose top card is ≥ new card. If no such pile, start a new pile. Number of piles = LIS length.

**Steps in plain English:**

1. **`tails` array**, initialized empty. `size` tracks how many entries are active.
2. **For each number:** binary search in `tails[0..size)` for the leftmost index where `tails[pos] >= nums[i]`.
3. **Replace:** `tails[pos] = nums[i]`. If `pos == size`, we're extending the longest subsequence — increment `size`.
4. **Answer:** `size` at the end.

```java
public int lengthOfLIS(int[] nums) {
    int[] tails = new int[nums.length];
    int size = 0;
    for (int num : nums) {
        // Step 2 — binary search: find leftmost position where tails[pos] >= num
        // Manual lower_bound: find first index where tails[lo] >= num
        int lo = 0, hi = size;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (tails[mid] < num) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        // 🔄 Alternative using Arrays.binarySearch:
        // int pos = Arrays.binarySearch(tails, 0, size, num);
        // if (pos < 0) lo = -(pos) - 1;
        // else lo = pos;

        // Step 3 — replace existing tail (better candidate) or extend
        tails[lo] = num;
        if (lo == size) {
            // num is larger than all tails — extends the longest subsequence
            size++;
        }
    }
    // Step 4 — size = LIS length
    return size;
}
```

### 🎨 Visual — Patience Sort Step by Step

```
nums = [10, 9, 2, 5, 3, 7, 101, 18]

Process 10:  tails=[10]         size=1
  → tails is empty for lo search → lo=0=size → append: tails=[10], size=1

Process 9:   binary search in tails[0..1)=[10]: 10>=9 → lo=0
  → replace tails[0]=9: tails=[9], size=1 (better tail for length-1 sequences!)

Process 2:   binary search in tails[0..1)=[9]: 9>=2 → lo=0
  → replace tails[0]=2: tails=[2], size=1

Process 5:   binary search in tails[0..1)=[2]: all < 5 → lo=1=size
  → append: tails=[2, 5], size=2

Process 3:   binary search in tails[0..2)=[2,5]: 5>=3 but 2<3 → lo=1
  → replace tails[1]=3: tails=[2, 3], size=2 (3 is a better tail than 5 for length-2)

Process 7:   binary search in tails[0..2)=[2,3]: all < 7 → lo=2=size
  → append: tails=[2, 3, 7], size=3

Process 101: binary search in tails[0..3)=[2,3,7]: all < 101 → lo=3=size
  → append: tails=[2, 3, 7, 101], size=4

Process 18:  binary search in tails[0..4)=[2,3,7,101]: 101>=18 but 7<18 → lo=3
  → replace tails[3]=18: tails=[2, 3, 7, 18], size=4

Answer = size = 4 ✓

IMPORTANT: tails=[2,3,7,18] is NOT a valid LIS from the original array.
It's a "virtual" array where each position holds the best possible tail.
The LENGTH is correct but the elements may not form a real subsequence.

KEY INVARIANT:
   tails[k] = smallest tail element of all increasing subsequences of length k+1.
   tails is always sorted (strictly increasing) — this is what makes binary search valid.
   When we replace: we don't break tails' sorted order, we only improve a tail.
   When we extend: we append, maintaining sorted order.
```

**Complexity (optimal):** O(n log n) time, O(n) space — one binary search per element

**🏷️ Problems:** LC 300 (LIS, both approaches), LC 354 (Russian Doll Envelopes — apply patience sort on heights after sorting widths).

---

## 🧭 Pattern 3: 2D LIS — Russian Doll Envelopes

**What this solves:** Given envelopes with `[width, height]`, find the maximum number you can nest — envelope A fits inside B if A's width AND height are both strictly less than B's. This is LIS in two dimensions.

**Recognition cues — reach for this when:**
- "Maximum nesting" with two numeric dimensions (width/height, length/weight, etc.)
- Strictly increasing pairs — both values must be strictly larger
- Can be reduced to 1D LIS after a sort

**Brute force:** Generate all possible nesting sequences, check each is valid, return the longest. O(2^n) — impractical.

**Key insight:** Sort envelopes by width ascending. If we then run LIS on heights, we'd accidentally allow same-width envelopes to nest (since heights are still increasing). Fix: at equal widths, sort heights **descending**. This guarantees two envelopes of the same width can never both appear in the same LIS of heights.

**Steps in plain English:**

1. **Sort:** ascending width; at equal widths, descending height.
2. **Extract heights** into a separate array.
3. **Run O(n log n) LIS** on the heights array.
4. **Answer** = LIS length on heights = max nesting depth.

```java
public int maxEnvelopes(int[][] envelopes) {
    // Step 1 — sort: ascending width; DESCENDING height at same width
    // Descending height at equal width prevents same-width envelopes from
    // both entering the LIS (since LIS requires STRICTLY increasing heights)
    Arrays.sort(envelopes, (a, b) -> a[0] == b[0] ? b[1] - a[1] : a[0] - b[0]);
    // 🔄 Fallback: use Integer.compare for overflow safety:
    // Arrays.sort(envelopes, (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(b[1], a[1]));

    // Step 2+3 — run patience sort LIS on the heights
    int[] tails = new int[envelopes.length];
    int size = 0;
    for (int[] env : envelopes) {
        int h = env[1];
        int lo = 0, hi = size;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (tails[mid] < h) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        tails[lo] = h;
        if (lo == size) {
            size++;
        }
    }
    // Step 4 — LIS length on heights = maximum envelope nesting
    return size;
}
```

### 🎨 Visual — Why Descending Height at Equal Width Works

```
Envelopes: [3,4], [3,3], [3,2], [5,5], [5,1], [6,7]
                    (sorted ascending width, descending height at same width)

Width 3: heights = 4, 3, 2 (descending)
  → These three heights [4,3,2] are DEcreasing, so LIS can pick AT MOST ONE
  → Prevents [3,2]→[3,3]→[3,4] from sneaking into the answer (same-width can't nest!)

Width 5: heights = 5, 1 (descending)
  → Same — only one can enter LIS

Width 6: heights = 7

LIS on heights [4,3,2, 5,1, 7]:
  Process 4: tails=[4]
  Process 3: tails=[3]       (3 is better tail than 4)
  Process 2: tails=[2]       (2 is better)
  Process 5: tails=[2,5]     (extend)
  Process 1: tails=[1,5]     (1 replaces 2)
  Process 7: tails=[1,5,7]   (extend)
  Answer = 3

One valid nesting: [3,2] inside [5,5] inside [6,7] → widths: 3<5<6, heights: 2<5<7 ✓

KEY INVARIANT:
   Descending heights at equal widths = same-width envelopes form a decreasing
   sequence in the height array → LIS can include at most one of them.
   This collapses the 2D problem into a 1D LIS on heights.
```

**Complexity (optimal):** O(n log n) time, O(n) space

**🏷️ Problems:** LC 354 (Russian Doll Envelopes).

---

## 🧭 Pattern 4: Interval DP ⭐

**What this solves:** Problems where the optimal answer for a range `[i, j]` can be computed by splitting it at some interior point `k` and combining results from `[i, k]` and `[k+1, j]`. The critical mindset shift: think about what you do LAST inside the interval, not first.

**Recognition cues — reach for this when:**
- "Burst all items in an array optimally" (Burst Balloons)
- "Minimum cost to split a sequence into parts" (Cut the Stick, Merge Stones)
- "Minimum turns to print/build a string/structure" (Strange Printer)
- State requires two indices `i` and `j` representing a range of the input

**Brute force:** Try all possible orders of operations (permutations). O(n!) — completely impractical.

**Key insight:** Think about the LAST operation in the range `[i, j]`. After everything else is done, what's the final item/action? This "last one standing" trick turns exponential enumeration into polynomial DP. `dp[i][j]` = optimal value for the range `[i, j]`. Iterate by INTERVAL LENGTH, not by `i` — smaller intervals must be computed before larger ones.

**Steps in plain English:**

1. **Define state:** `dp[i][j]` = optimal value when processing elements in range `[i, j]`.
2. **Base case:** `dp[i][i]` = value for single element (often 0 or trivial).
3. **Fill by interval length:** outer loop = length from 2 to n; inner loops set `i` and `j = i + len - 1`.
4. **Try all split points k from i to j:** `dp[i][j] = optimize(dp[i][k-1] + cost(i,k,j) + dp[k+1][j])`.
5. **Answer:** `dp[0][n-1]`.

```java
// ─── Template: Interval DP ───
// Defined concretely for Burst Balloons (LC 312) below
public int maxCoins(int[] nums) {
    // Add boundary balloons with value 1 (make indexing cleaner)
    int n = nums.length;
    int[] arr = new int[n + 2];
    arr[0] = 1;
    arr[n + 1] = 1;
    for (int i = 0; i < n; i++) {
        arr[i + 1] = nums[i];
    }
    int m = n + 2;
    // dp[i][j] = max coins from bursting ALL balloons strictly between i and j
    // (arr[i] and arr[j] are the boundary balloons — NOT burst in this subproblem)
    int[][] dp = new int[m][m];

    // Step 3 — iterate by interval length (CRITICAL: smaller intervals first)
    for (int len = 2; len < m; len++) {
        for (int i = 0; i + len < m; i++) {
            int j = i + len;
            // Step 4 — try every k as the LAST balloon burst in (i, j)
            for (int k = i + 1; k < j; k++) {
                // When k is the last burst: arr[i] and arr[j] are still present
                // Coins from k: arr[i] * arr[k] * arr[j]
                // Plus coins from left subproblem (i..k) and right (k..j)
                int coins = arr[i] * arr[k] * arr[j] + dp[i][k] + dp[k][j];
                dp[i][j] = Math.max(dp[i][j], coins);
            }
        }
    }
    // Step 5 — full range (0 to m-1), which covers all original balloons
    return dp[0][m - 1];
}
```

### 🎨 Visual — Iteration Order for Interval DP

```
Array of size n=4 (indices 0..3):
Subproblems iterated by INCREASING LENGTH:

Length 1 (base cases):
  dp[0][0], dp[1][1], dp[2][2], dp[3][3]   ← trivial single elements

Length 2:
  dp[0][1], dp[1][2], dp[2][3]              ← depends only on length-1

Length 3:
  dp[0][2], dp[1][3]                         ← depends on length-1 and length-2

Length 4 (final answer):
  dp[0][3]                                   ← depends on all smaller intervals

Each dp[i][j] uses dp[i][k] and dp[k][j] for k between i and j.
Both dp[i][k] and dp[k][j] are SHORTER intervals — already computed. ✓

If you iterate by i (outer) and j (inner) instead of by LENGTH,
you'll compute dp[0][3] before dp[1][3] — wrong! dp[0][3] needs dp[1][3].

KEY INVARIANT:
   Always iterate by interval length, shortest first.
   dp[i][j] may ONLY depend on dp[i'][j'] where j'-i' < j-i.
```

### 🎨 Visual — Burst Balloons "Last Balloon" Trick

```
Balloons: [3, 1, 5, 8]   (add boundaries: [1, 3, 1, 5, 8, 1])
Indices:    0  1  2  3       0  1  2  3  4  5

Why "last balloon" and not "first"?

WRONG intuition (first balloon burst):
  Burst balloon 1 (value=3): neighbors are 1 and 1 → coins = 1*3*1 = 3
  But now neighbors of 5 CHANGE — what were they after 3 is gone?
  The state of the remaining array is complex. Hard to precompute.

RIGHT intuition (last balloon burst in range [i, j]):
  "What if balloon k is the VERY LAST burst inside (i, j)?"
  Then arr[i] and arr[j] are the boundaries when k finally bursts.
  Coins from k = arr[i] * arr[k] * arr[j]  ← boundaries are KNOWN, fixed
  Left subproblem dp[i][k]: burst everything between i and k (k is boundary)
  Right subproblem dp[k][j]: burst everything between k and j (k is boundary)
  The boundaries DON'T CHANGE for the subproblems — k is "reserved" until last.

This "last to burst" framing makes boundaries stable → clean DP.
```

**Complexity (optimal):** O(n³) time, O(n²) space — three nested loops over intervals

**🏷️ Problems:** LC 312 (Burst Balloons), LC 1547 (Minimum Cost to Cut a Stick), LC 516 (Longest Palindromic Subsequence), LC 664 (Strange Printer).

---

## 🔬 Canonical Problem — LC 300: Longest Increasing Subsequence

> **Problem:** Given an integer array, return the length of the longest strictly increasing subsequence. Example: `[10, 9, 2, 5, 3, 7, 101, 18]` → `4` (e.g., `[2, 3, 7, 101]`).

### Step 1 — Read and identify triggers

"Longest increasing subsequence" is the direct trigger for **Patterns 1 and 2**. Start with the O(n²) approach (Pattern 1) to explain the recurrence, then optimize to O(n log n) if pushed.

### Step 2 — Choose the template

O(n²) DP first — cleaner to explain. `dp[i]` = LIS length ending at index i. Fill left to right looking back at all valid extensions.

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Init:** every element is an LIS of length 1 on its own.
2. **Fill:** for each i, look back at all j < i where `nums[j] < nums[i]`. Update `dp[i] = max(dp[i], dp[j] + 1)`.
3. **Answer:** `max(dp[])`.

```java
class Solution {
    public int lengthOfLIS(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        Arrays.fill(dp, 1);
        int result = 1;
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i]) {
                    dp[i] = Math.max(dp[i], dp[j] + 1);
                }
            }
            result = Math.max(result, dp[i]);
        }
        return result;
    }
}
```

### Step 4 — Optimize to O(n log n)

Then say: "We can improve to O(n log n) with patience sort — maintain a `tails` array where `tails[k]` is the smallest tail for all LIS of length `k+1`. Binary search tells us where to slot each new number."

```java
class Solution {
    public int lengthOfLIS(int[] nums) {
        int[] tails = new int[nums.length];
        int size = 0;
        for (int num : nums) {
            int lo = 0, hi = size;
            while (lo < hi) {
                int mid = lo + (hi - lo) / 2;
                if (tails[mid] < num) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            tails[lo] = num;
            if (lo == size) {
                size++;
            }
        }
        return size;
    }
}
```

### Complexity
- O(n²) DP: **O(n²) time**, O(n) space
- Patience sort: **O(n log n) time**, O(n) space

---

## ⚡ Problem Bank — Key Twists

---

### LC 300: Longest Increasing Subsequence

> **Problem:** Find the length of the longest strictly increasing subsequence in an integer array. Example: `[10,9,2,5,3,7,101,18]` → `4` (one valid LIS: `[2,3,7,101]`).

> **Brute force:** Enumerate all 2^n subsequences, check each for increasing property, return max length. O(2^n).
> **Key insight:** `dp[i]` = LIS length ending at position i; only depends on smaller `dp[j]` for j < i. Optimal substructure holds.
> **Approach:** Pattern 1 (O(n²)) or Pattern 2 (O(n log n) patience sort). Both return length; Pattern 2 uses `tails` binary search.

```java
// O(n²) — explain this first in interviews
int[] dp = new int[n];
Arrays.fill(dp, 1);
for (int i = 1; i < n; i++) {
    for (int j = 0; j < i; j++) {
        if (nums[j] < nums[i]) {
            // Extend the LIS ending at j by appending nums[i]
            dp[i] = Math.max(dp[i], dp[j] + 1);
        }
    }
}
```

**Complexity (optimal):** O(n log n) time (patience sort), O(n) space

---

### LC 354: Russian Doll Envelopes

> **Problem:** Each envelope is `[width, height]`. You can put envelope A inside B if `A.width < B.width` AND `A.height < B.height` (strictly). Return the maximum number of envelopes you can nest. Example: `[[5,4],[6,4],[6,7],[2,3]]` → `3` (envelopes `[2,3]→[5,4]→[6,7]`).

> **Brute force:** Try all nesting orderings. O(n!) — impractical.
> **Key insight:** Sort by width ascending, then by height DESCENDING at equal widths. Then run 1D LIS on heights — the descending-height trick at equal widths prevents same-width envelopes from both entering the LIS.
> **Approach:** Pattern 3 — sort + patience sort LIS on heights.

```java
// Sort: ascending width; at SAME width, descending height
// This prevents [3,2] and [3,5] from both appearing in the LIS of heights
Arrays.sort(envelopes, (a, b) -> a[0] == b[0] ? b[1] - a[1] : a[0] - b[0]);
// 🔄 Fallback: explicit Comparator — see Lambda section above
// Then run standard patience sort on envelopes[i][1] (heights only)
```

**Complexity (optimal):** O(n log n) time, O(n) space

---

### LC 673: Number of Longest Increasing Subsequences

> **Problem:** Return the NUMBER of longest increasing subsequences (not just the length). Example: `[1,3,5,4,7]` → `2` (LIS length is 4: both `[1,3,5,7]` and `[1,3,4,7]` are valid).

> **Brute force:** Enumerate all 2^n subsequences, find max length, count those achieving it. O(2^n).
> **Key insight:** Augment the standard O(n²) LIS DP with a COUNT array: `count[i]` = number of LIS of length `dp[i]` ending at i. When extending: if `dp[j]+1 > dp[i]`, reset count. If `dp[j]+1 == dp[i]`, add `count[j]` to `count[i]`.
> **Approach:** O(n²) LIS with parallel `count[]` array. Sum `count[i]` for all i where `dp[i] == maxLen`.

```java
int[] dp = new int[n], count = new int[n];
Arrays.fill(dp, 1);
Arrays.fill(count, 1);
for (int i = 1; i < n; i++) {
    for (int j = 0; j < i; j++) {
        if (nums[j] < nums[i]) {
            if (dp[j] + 1 > dp[i]) {
                dp[i] = dp[j] + 1;
                count[i] = count[j];    // reset — found a longer LIS ending at i
            } else if (dp[j] + 1 == dp[i]) {
                count[i] += count[j];   // another way to reach same length
            }
        }
    }
}
```

**Complexity (optimal):** O(n²) time, O(n) space — no known O(n log n) solution for counting variant

---

### LC 368: Largest Divisible Subset

> **Problem:** Given a set of distinct positive integers, find the largest subset such that every pair `(a, b)` satisfies `a % b == 0` or `b % a == 0`. Example: `[1,2,3]` → `[1,2]` or `[1,3]` (length 2).

> **Brute force:** Check all 2^n subsets for the divisibility property. O(2^n × n²).
> **Key insight:** Sort the array. Divisibility in a sorted sequence is transitive: if `a|b` and `b|c`, then `a|c`. This makes the problem equivalent to LIS where the "increasing" condition is replaced by "divides." Run standard LIS DP.
> **Approach:** Sort → O(n²) LIS DP with divisibility check instead of `<`. Track parent pointers to reconstruct the actual subset.

```java
Arrays.sort(nums);
// 🔄 Fallback: int[] copy = nums.clone(); Arrays.sort(copy);
int[] dp = new int[n], parent = new int[n];
Arrays.fill(dp, 1);
Arrays.fill(parent, -1);
for (int i = 1; i < n; i++) {
    for (int j = 0; j < i; j++) {
        // Divisibility replaces the "strictly less" condition of classic LIS
        if (nums[i] % nums[j] == 0 && dp[j] + 1 > dp[i]) {
            dp[i] = dp[j] + 1;
            parent[i] = j;   // track path for reconstruction
        }
    }
}
```

**Complexity (optimal):** O(n²) time, O(n) space

---

### LC 312: Burst Balloons

> **Problem:** Given n balloons with values in `nums`, burst all balloons to maximize coins collected. Bursting balloon i gives `nums[i-1] * nums[i] * nums[i+1]` coins (treat out-of-bounds as 1). Example: `nums = [3,1,5,8]` → `167` (optimal burst order 1→5→3→8: coins 3×1×5 + 3×5×8 + 1×3×8 + 1×8×1 = 15+120+24+8 = 167).

> **Brute force:** Try all n! burst orders. O(n! × n) — impractical.
> **Key insight:** Think about the LAST balloon to burst inside any range [i,j]. When it's the last, its two neighbors are exactly the boundaries arr[i] and arr[j] — these don't change, making the cost computable. `dp[i][j]` = max coins from bursting everything strictly between i and j.
> **Approach:** Pattern 4 — add sentinel boundaries (value 1) at both ends. Fill `dp[i][j]` by trying each k as the last burst; iterate by interval length.

```java
// Pad boundaries: arr = [1] + nums + [1]
// dp[i][j] = max coins bursting strictly between indices i and j
for (int len = 2; len < m; len++) {           // interval length
    for (int i = 0; i + len < m; i++) {
        int j = i + len;
        for (int k = i + 1; k < j; k++) {    // k = last balloon burst in (i,j)
            // arr[i] and arr[j] are the two boundaries present when k is LAST burst
            int coins = arr[i] * arr[k] * arr[j] + dp[i][k] + dp[k][j];
            dp[i][j] = Math.max(dp[i][j], coins);
        }
    }
}
return dp[0][m - 1];
```

**Complexity (optimal):** O(n³) time, O(n²) space

---

### LC 516: Longest Palindromic Subsequence

> **Problem:** Find the length of the longest palindromic subsequence in a string. Example: `s = "bbbab"` → `4` (subsequence "bbbb").

> **Brute force:** Check all 2^n subsequences, test each for palindrome property. O(2^n × n).
> **Key insight:** `dp[i][j]` = length of longest palindromic subsequence in `s[i..j]`. If `s[i] == s[j]`, they contribute 2 + `dp[i+1][j-1]`. If not, take max of ignoring either end: `max(dp[i+1][j], dp[i][j-1])`.
> **Approach:** Interval DP — iterate by length. Base case: `dp[i][i] = 1`.

```java
int n = s.length();
int[][] dp = new int[n][n];
// Base case: every single character is a palindromic subsequence of length 1
for (int i = 0; i < n; i++) {
    dp[i][i] = 1;
}
for (int len = 2; len <= n; len++) {
    for (int i = 0; i <= n - len; i++) {
        int j = i + len - 1;
        if (s.charAt(i) == s.charAt(j)) {
            // Both ends match — include them + best inner palindrome
            dp[i][j] = 2 + (len == 2 ? 0 : dp[i + 1][j - 1]);
        } else {
            // Ends don't match — best of dropping either end
            dp[i][j] = Math.max(dp[i + 1][j], dp[i][j - 1]);
        }
    }
}
return dp[0][n - 1];
```

**Complexity (optimal):** O(n²) time, O(n²) space

---

### LC 1547: Minimum Cost to Cut a Stick

> **Problem:** A stick of length n. Cut positions in `cuts[]`. Cost of a cut = current stick length. Find minimum total cost. Example: `n=7, cuts=[1,3,4,5]` → `16`.

> **Brute force:** Try all orders of cuts. O(m!) where m = cuts count.
> **Key insight:** Add 0 and n to cuts, sort. `dp[i][j]` = min cost to make all cuts strictly between `cuts[i]` and `cuts[j]`. The cost of any single cut in that range = `cuts[j] - cuts[i]` (current stick length). Try each cut k as the first (or equivalently, any) cut in the range.
> **Approach:** Interval DP over sorted cut positions. Very similar structure to Burst Balloons.

```java
int m = cuts.length;
int[] c = new int[m + 2];
c[0] = 0;
c[m + 1] = n;
for (int i = 1; i <= m; i++) c[i] = cuts[i - 1];
Arrays.sort(c);
int[][] dp = new int[m + 2][m + 2];
for (int len = 2; len <= m + 1; len++) {
    for (int i = 0; i + len <= m + 1; i++) {
        int j = i + len;
        dp[i][j] = Integer.MAX_VALUE;
        for (int k = i + 1; k < j; k++) {
            // Cost of making a cut at k = current segment length = c[j] - c[i]
            dp[i][j] = Math.min(dp[i][j], dp[i][k] + dp[k][j] + c[j] - c[i]);
        }
    }
}
return dp[0][m + 1];
```

**Complexity (optimal):** O(m³) time, O(m²) space — m = number of cuts

---

## ⚠️ Interview Gotchas

### LIS-specific
- **Strictly increasing vs non-decreasing** — the binary search condition changes. For strict: `tails[mid] < num` → use `<`. For non-decreasing: `tails[mid] <= num` → use `<=`. Get this wrong and you solve the wrong problem.
- **`tails` does NOT store the actual LIS** — it stores the smallest possible tail for each length. If asked to reconstruct the actual LIS, you need a separate parent/path array. Don't claim `tails` is the answer sequence.
- **`Arrays.binarySearch` sign convention** — returns `-(insertion point) - 1` when not found, not just `-1`. Always convert: `if (pos < 0) pos = -(pos) - 1;`

### Interval DP specific
- **Iteration order** — ALWAYS iterate by interval length, not by `i`. Iterating by `i` (outer) and `j` (inner) computes `dp[0][n-1]` before `dp[1][n-1]` which it depends on.
- **Burst Balloons: "last to burst" not "first to burst"** — the reason for "last" is that boundaries are stable. If you think "first to burst," the neighbors change after that burst, making the subproblems dependent on burst order.
- **Off-by-one in boundary indexing** — Burst Balloons adds boundary elements at indices 0 and n+1. The DP covers the OPEN interval `(i, j)` — strictly between indices i and j. The loop bounds are `k in (i, j)` (exclusive), so `for (int k = i+1; k < j; k++)`.

### Follow-up questions to expect
- "Can you reconstruct the actual LIS?" → Yes — store parent pointers: `parent[i] = j` when `dp[i] = dp[j] + 1`. Trace back from the max-dp index.
- "What's the difference between LCS and LIS?" → LCS (Longest Common Subsequence) is a 2D string DP in `dp.md`. LIS is 1D array DP — you're finding increasing elements within ONE array, not common elements between two strings.
- "Can interval DP be memoized top-down instead of bottom-up?" → Yes — `dfs(i, j)` with a memo table works. Top-down is often easier to write; bottom-up avoids recursion overhead. Both are O(n³).

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**
For each problem, name the pattern in under 5 seconds:

1. "Longest strictly increasing subsequence" → ___
2. "Maximum envelopes you can nest (width and height both must increase)" → ___
3. "Maximum coins from bursting balloons in optimal order" → ___
4. "Longest palindromic subsequence in a string" → ___
5. "Minimum cost to cut a stick at given positions" → ___

**Part 2 — Write From Memory (4 minutes)**
Without looking:
1. Write the O(n²) LIS DP loop (initialization + nested loop + answer extraction)
2. Write the patience sort binary search block: initialize `tails`, process each num, return `size`

**Part 3 — Derive (2 minutes)**
For Burst Balloons: why do we think about the LAST balloon to burst rather than the first? Write 2 sentences explaining why the "first balloon" framing fails and the "last balloon" framing succeeds.

**Scoring:**
- Part 1: 5/5 → ready. Confused LIS and Interval DP → re-read mental model tree.
- Part 2: LIS DP correct on first try → ready. Binary search bounds wrong → re-read the `tails` invariant in Pattern 2 visual.
- Part 3: Correctly stated "boundaries change after first burst, but boundaries are stable for the last burst" → ready. Couldn't articulate → re-read the Burst Balloons visual.

---

## 🔗 Cross-References

- **Foundational DP:** `../Interview/dp.md` — prerequisite. Patterns here extend linear, knapsack, and string DP from that file.
- **Binary search:** `../Interview/binary-search.md` — the patience sort in Pattern 2 uses `lower_bound` binary search; review if the binary search mechanics feel shaky.
- **Backtracking:** `../Interview/backtracking.md` — Burst Balloons brute force is backtracking over all orderings; DP replaces that exponential search.
- **Sorting:** `../Interview/arrays-and-hashing.md` — Russian Doll Envelopes requires a two-key sort that interacts with LIS; review comparator lambdas if needed.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** Advanced DP Interview Playbook — 4 patterns (LIS O(n²), LIS O(n log n) patience sort, 2D LIS Russian Doll Envelopes, Interval DP), canonical walkthrough (LC 300 both approaches), 7 problem bank entries with brute force + key insight + complexity. Added as FAANG gap fill to complement `dp.md`. |
