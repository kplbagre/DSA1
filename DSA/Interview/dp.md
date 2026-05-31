# Dynamic Programming — Interview Playbook

> **Read this file when:** You have an interview coming up and need to recognize DP patterns fast. Companion to `DSA/DeepDive/dp-fundamentals.md` — this file focuses on pattern recognition and problem mapping, not teaching DP from scratch.

---

## 🎯 Why You're Reading This

DP problems are the most feared interview topic. But 80% of interview DP problems fall into just 5 families. If you can identify which family a problem belongs to in 30 seconds, the template writes itself. This file builds that instinct.

**Prerequisite:** You should have read `DSA/DeepDive/dp-fundamentals.md` through at least Family 2. If you haven't — read that first, then come back here.

---

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `Math.max(a, b)` / `Math.min(a, b)` | Choose optimal value at each state | All patterns |
| `Arrays.fill(array, value)` | Initialize DP array (e.g., fill with `Integer.MAX_VALUE`) | Patterns 1, 4 |
| `new int[n]` / `new int[m][n]` | Create 1D or 2D DP table | All patterns |
| `s.charAt(i)` | Access character for string DP comparisons | Pattern 3 |
| `int prev1, prev2, current` | Space-optimized variables replacing `dp[i-1]`, `dp[i-2]` | Pattern 1 |

> **Full reference:** `../Reference/dsa-collections-notes.md`

---

## 🧠 The Mental Model — The 4-Step DP Recipe

Every DP problem follows the same recipe:

1. **Define state** — "What do I need to know at position `i` to make a decision?"
2. **Define recurrence** — "How does `dp[i]` relate to smaller subproblems?"
3. **Define base case** — "What's the answer when I can't recurse further?"
4. **Define answer** — "Which cell holds the final answer? `dp[n-1]`? `max(dp[...])`?"

### The State vs Result Rule (Lesson Learned the Hard Way)

> **Lesson learned the hard way (May 2026):** Kapil carried `sum` as a parameter in House Robber, making memoization impossible because the state space exploded to `(index, sum)` instead of just `(index)`.

**The rule:** "Does this value affect my future CHOICES? If YES → it's a STATE (parameter). If NO → it's a RESULT (return value)."

- ✅ Index `i` (which house I'm at) → affects choices → STATE
- ❌ Running sum (total stolen so far) → doesn't affect choices → RESULT (return it)

### Pattern Recognition — Which DP Family?

```
DP problem
│
├── "Linear sequence" (array / 1D)
│   ├── "Rob houses / climb stairs / coin change"
│   │   └── Family 1: Linear DP (Pattern 1)
│   └── "Longest increasing subsequence"
│       └── Family 1 variant: O(n²) or O(n log n)
│
├── "2D grid"
│   ├── "Count paths / minimum cost path"
│   │   └── Family 2: Grid DP (Pattern 2)
│   └── "Unique paths with obstacles"
│       └── Family 2 with blocked cells
│
├── "Two strings"
│   ├── "Longest common subsequence / edit distance"
│   │   └── Family 3: String DP (Pattern 3)
│   └── "Is string interleaving of two others?"
│       └── Family 3 variant
│
├── "Choose items with constraint"
│   ├── "Subset sum / partition equal / coin change (count)"
│   │   └── Family 4: 0/1 Knapsack / Unbounded Knapsack (Pattern 4)
│   └── "Can I make target from these numbers?"
│       └── Family 4
│
└── "Count ways to decode / partition"
    └── Family 5: Counting DP (Pattern 5)
```

---

## 🧭 Pattern 1: Linear DP (1D Array) ⭐

**Recognition cues — reach for this when:**
- "Maximum/minimum value considering elements in sequence"
- "Rob houses" — can't take adjacent
- "Climb stairs" — ways to reach the top
- "Coin change" — minimum coins to make amount
- Decision at each step depends only on previous 1-2 states

**The template (forward direction, 0→n):**

**Steps in plain English:**

1. **Define `dp[i]`** — the answer for the subproblem ending at / starting from index `i`.
2. **Recurrence** — `dp[i]` uses `dp[i-1]`, `dp[i-2]`, etc.
3. **Base cases** — `dp[0]`, `dp[1]`.
4. **Answer** — `dp[n-1]` or `dp[n]`.

```java
public int solve(int[] nums) {
    int n = nums.length;
    if (n == 0) {
        return 0;
    }

    // Step 3 — base cases
    int[] dp = new int[n];
    dp[0] = nums[0];
    if (n > 1) {
        dp[1] = Math.max(nums[0], nums[1]);
    }

    // Step 2 — fill forward
    for (int i = 2; i < n; i++) {
        dp[i] = Math.max(dp[i - 1], dp[i - 2] + nums[i]);
    }

    // Step 4 — answer
    return dp[n - 1];
}
```

**Space optimization:** When `dp[i]` only depends on `dp[i-1]` and `dp[i-2]`, replace the array with two variables (`prev1`, `prev2`).

```java
public int solve(int[] nums) {
    if (nums.length == 0) {
        return 0;
    }
    int prev2 = 0;
    int prev1 = nums[0];

    for (int i = 1; i < nums.length; i++) {
        int current = Math.max(prev1, prev2 + nums[i]);
        prev2 = prev1;
        prev1 = current;
    }
    return prev1;
}
```

---

## 🧭 Pattern 2: Grid DP (2D) ⭐

**Recognition cues — reach for this when:**
- "Count paths from top-left to bottom-right"
- "Minimum cost path in grid"
- "Can only move right or down"

**The template:**

**Steps in plain English:**

1. **Define `dp[r][c]`** — answer for reaching cell `(r, c)`.
2. **Recurrence** — `dp[r][c]` comes from `dp[r-1][c]` (above) and `dp[r][c-1]` (left).
3. **Base case** — first row and first column have only one path.
4. **Answer** — `dp[m-1][n-1]`.

```java
public int uniquePaths(int m, int n) {
    int[][] dp = new int[m][n];

    // Step 3 — base case: first row and first column
    for (int r = 0; r < m; r++) {
        dp[r][0] = 1;
    }
    for (int c = 0; c < n; c++) {
        dp[0][c] = 1;
    }

    // Step 2 — fill grid
    for (int r = 1; r < m; r++) {
        for (int c = 1; c < n; c++) {
            dp[r][c] = dp[r - 1][c] + dp[r][c - 1];
        }
    }

    // Step 4 — answer
    return dp[m - 1][n - 1];
}
```

---

## 🧭 Pattern 3: String DP (Two Strings) ⭐

**Recognition cues — reach for this when:**
- "Longest common subsequence" of two strings
- "Edit distance" (min operations to transform)
- "Interleaving string"
- Two strings compared character by character

**The template:**

`dp[i][j]` = answer considering `s1[0..i-1]` and `s2[0..j-1]`.

```java
public int longestCommonSubsequence(String text1, String text2) {
    int m = text1.length();
    int n = text2.length();
    int[][] dp = new int[m + 1][n + 1];

    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                dp[i][j] = dp[i - 1][j - 1] + 1;
            } else {
                dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
    }
    return dp[m][n];
}
```

---

## 🧭 Pattern 4: 0/1 Knapsack / Subset Sum

**Recognition cues — reach for this when:**
- "Can you partition into two equal-sum subsets?"
- "Count ways to reach target sum using given numbers"
- "Each item can be used once (0/1) or unlimited (unbounded)"

**The template (0/1 knapsack — space-optimized):**

```java
public boolean canPartition(int[] nums) {
    int total = 0;
    for (int n : nums) {
        total += n;
    }
    if (total % 2 != 0) {
        return false;
    }
    int target = total / 2;

    boolean[] dp = new boolean[target + 1];
    dp[0] = true;

    for (int num : nums) {
        // Iterate RIGHT to LEFT to avoid using same item twice
        for (int j = target; j >= num; j--) {
            dp[j] = dp[j] || dp[j - num];
        }
    }
    return dp[target];
}
```

**Unbounded knapsack (each item unlimited):** Iterate LEFT to RIGHT instead.

```java
// Coin Change — minimum coins to make amount
public int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);
    dp[0] = 0;

    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            if (coin <= i) {
                dp[i] = Math.min(dp[i], dp[i - coin] + 1);
            }
        }
    }
    return dp[amount] > amount ? -1 : dp[amount];
}
```

---

## 🧭 Pattern 5: Counting / Decision DP

**Recognition cues — reach for this when:**
- "Number of ways to decode"
- "Word break — can string be segmented?"
- "Number of unique BSTs"

**Decode Ways (LC 91):**

```java
public int numDecodings(String s) {
    int n = s.length();
    int[] dp = new int[n + 1];
    dp[0] = 1;
    dp[1] = s.charAt(0) == '0' ? 0 : 1;

    for (int i = 2; i <= n; i++) {
        int oneDigit = Integer.parseInt(s.substring(i - 1, i));
        int twoDigit = Integer.parseInt(s.substring(i - 2, i));
        if (oneDigit >= 1) {
            dp[i] += dp[i - 1];
        }
        if (twoDigit >= 10 && twoDigit <= 26) {
            dp[i] += dp[i - 2];
        }
    }
    return dp[n];
}
```

---

## 🔬 Canonical Problem — LC 198: House Robber

> **Problem:** Given an array of non-negative integers representing money in each house, return the maximum amount you can rob without robbing two adjacent houses.

### Step 1 — Read and identify triggers

"Linear sequence, maximum value, can't take adjacent. This is **Pattern 1: Linear DP**."

### Step 2 — Define the state

"`dp[i]` = maximum money I can rob from houses `[0..i]`. The state is just the index — the running sum is the RESULT I return, not a parameter."

### Step 3 — Recurrence

"At house `i`, I either skip it (`dp[i-1]`) or rob it (`dp[i-2] + nums[i]`). Take the max."

### Step 4 — Code

```java
public int rob(int[] nums) {
    if (nums.length == 1) {
        return nums[0];
    }
    int prev2 = 0;
    int prev1 = nums[0];

    for (int i = 1; i < nums.length; i++) {
        int current = Math.max(prev1, prev2 + nums[i]);
        prev2 = prev1;
        prev1 = current;
    }
    return prev1;
}
```

### Step 5 — Verify

```
nums = [2, 7, 9, 3, 1]

i=0: prev1=2, prev2=0
i=1: current=max(2, 0+7)=7.   prev2=2, prev1=7
i=2: current=max(7, 2+9)=11.  prev2=7, prev1=11
i=3: current=max(11, 7+3)=11. prev2=11, prev1=11
i=4: current=max(11, 11+1)=12. prev2=11, prev1=12

Answer: 12 ✅ (rob houses 0, 2, 4 → 2+9+1=12)
```

### Complexity

- **Time:** O(n)
- **Space:** O(1) with space optimization

---

## ⚡ Problem Bank — Expanded

---

### LC 70: Climbing Stairs

> **Problem:** You are climbing a staircase with `n` steps. Each time you can climb 1 or 2 steps. How many distinct ways can you reach the top?

> **Approach:** `dp[i] = dp[i-1] + dp[i-2]` — Fibonacci sequence. Ways to reach step `i` = ways via one step from `i-1` + ways via two steps from `i-2`.

```java
public int climbStairs(int n) {
    if (n <= 2) {
        return n;
    }
    // prev2 = ways to reach step 1, prev1 = ways to reach step 2
    int prev2 = 1, prev1 = 2;
    for (int i = 3; i <= n; i++) {
        // Ways to reach step i = ways via 1-step from i-1 + ways via 2-step from i-2
        int curr = prev1 + prev2;
        // Slide the window forward
        prev2 = prev1;
        prev1 = curr;
    }
    return prev1;
}
```

---

### LC 198: House Robber

> **Problem:** Rob houses in a line, can't rob two adjacent. Maximize total money.

> **Approach:** `dp[i] = max(dp[i-1], dp[i-2] + nums[i])` — skip or rob current house. Space-optimize with two variables.

```java
// See canonical walkthrough above for full code
int current = Math.max(prev1, prev2 + nums[i]);
```

---

### LC 213: House Robber II

> **Problem:** Same as House Robber but houses are in a **circle** — first and last house are adjacent.

> **Approach:** Run House Robber twice: once on `nums[0..n-2]` (skip last), once on `nums[1..n-1]` (skip first). Take the max.

```java
public int rob(int[] nums) {
    if (nums.length == 1) {
        return nums[0];
    }
    // Circle means first and last are adjacent — can't rob both
    // Solve twice: once excluding last house, once excluding first house
    return Math.max(
        robRange(nums, 0, nums.length - 2),
        robRange(nums, 1, nums.length - 1)
    );
}
```

---

### LC 746: Min Cost Climbing Stairs

> **Problem:** Given array `cost` where `cost[i]` is the cost to step on stair `i`, find the minimum cost to reach the top (past the last index). You can start from index 0 or 1.

> **Approach:** `dp[i] = cost[i] + min(dp[i-1], dp[i-2])`. Answer is `min(dp[n-1], dp[n-2])` since you can step past from either of the last two.

```java
public int minCostClimbingStairs(int[] cost) {
    int n = cost.length;
    // Base cases: min cost to reach step 0 and step 1 is just their own cost
    int prev2 = cost[0], prev1 = cost[1];
    for (int i = 2; i < n; i++) {
        // Pay cost[i] plus the cheaper of arriving from 1 or 2 steps back
        int curr = cost[i] + Math.min(prev1, prev2);
        prev2 = prev1;
        prev1 = curr;
    }
    // Can step past the top from either of the last two stairs
    return Math.min(prev1, prev2);
}
```

---

### LC 62: Unique Paths

> **Problem:** Robot on an `m × n` grid starts at top-left, can only move right or down. Count the number of unique paths to bottom-right.

> **Approach:** `dp[r][c] = dp[r-1][c] + dp[r][c-1]`. First row and first column are all 1s (only one way to reach them).

```java
// See Pattern 2 template above for full code
dp[r][c] = dp[r - 1][c] + dp[r][c - 1];
```

---

### LC 64: Minimum Path Sum

> **Problem:** Given an `m × n` grid of non-negative numbers, find a path from top-left to bottom-right that minimizes the sum. Can only move right or down.

> **Approach:** `dp[r][c] = grid[r][c] + min(dp[r-1][c], dp[r][c-1])`. Like Unique Paths but add cell value and take min.

```java
public int minPathSum(int[][] grid) {
    int m = grid.length, n = grid[0].length;
    // In-place DP: accumulate min cost into each cell
    for (int r = 0; r < m; r++) {
        for (int c = 0; c < n; c++) {
            // Origin — cost is just itself
            if (r == 0 && c == 0) continue;
            // First row — can only arrive from the left
            else if (r == 0) grid[r][c] += grid[r][c - 1];
            // First column — can only arrive from above
            else if (c == 0) grid[r][c] += grid[r - 1][c];
            // Interior — take the cheaper of coming from above vs left
            else grid[r][c] += Math.min(grid[r - 1][c], grid[r][c - 1]);
        }
    }
    return grid[m - 1][n - 1];
}
```

---

### LC 322: Coin Change

> **Problem:** Given coin denominations and a target amount, return the minimum number of coins needed. Each coin can be used unlimited times. Return -1 if impossible.

> **Approach:** Unbounded knapsack. `dp[i] = min(dp[i], dp[i - coin] + 1)` for each coin. Iterate left-to-right (coins are reusable).

```java
// See Pattern 4 template above for full code
dp[i] = Math.min(dp[i], dp[i - coin] + 1);
```

---

### LC 1143: Longest Common Subsequence

> **Problem:** Given two strings, return the length of their longest common subsequence (characters in order but not necessarily contiguous).

> **Approach:** `dp[i][j]` = LCS of `s1[0..i-1]` and `s2[0..j-1]`. If chars match → `dp[i-1][j-1] + 1`. Else → `max(dp[i-1][j], dp[i][j-1])`.

```java
// See Pattern 3 template above for full code
if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
    dp[i][j] = dp[i - 1][j - 1] + 1;
} else {
    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
}
```

---

### LC 416: Partition Equal Subset Sum

> **Problem:** Given array of positive integers, determine if it can be partitioned into two subsets with equal sum.

> **Approach:** If total is odd → impossible. Else find if any subset sums to `total / 2`. 0/1 knapsack — iterate right-to-left to avoid reusing elements.

```java
// See Pattern 4 template above for full code
dp[j] = dp[j] || dp[j - num];
```

---

### LC 91: Decode Ways

> **Problem:** A message of digits can be decoded where `'A'=1, 'B'=2, ..., 'Z'=26`. Given a string of digits, count the number of ways to decode it.

> **Approach:** `dp[i]` = ways to decode `s[0..i-1]`. One-digit decode (if valid) adds `dp[i-1]`. Two-digit decode (if 10-26) adds `dp[i-2]`.

```java
// See Pattern 5 template above for full code
if (oneDigit >= 1) dp[i] += dp[i - 1];
if (twoDigit >= 10 && twoDigit <= 26) dp[i] += dp[i - 2];
```

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty input / n=0** — usually return 0
- **Single element** — House Robber with 1 house = return that value
- **All zeros** — Decode Ways: `"0"` → 0 ways (can't decode leading zero)
- **Negative numbers** — Coin Change can't have negative coins, but target could be 0

### Follow-up questions:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| House Robber | "What if houses are in a circle?" | LC 213: run twice (skip first / skip last), take max |
| Climbing Stairs | "What if you can climb 1, 2, or 3 steps?" | `dp[i] = dp[i-1] + dp[i-2] + dp[i-3]` |
| Coin Change (min coins) | "Count number of ways instead?" | Change `min` to `sum`: `dp[i] += dp[i - coin]` |
| Unique Paths | "What if some cells are obstacles?" | Set `dp[r][c] = 0` for obstacles, rest same |
| LCS | "Print the actual subsequence?" | Backtrack through the dp table |

### The 3 rookie DP mistakes:

1. **State vs Result confusion** — carrying accumulated value as parameter instead of returning it
2. **Mixing recursion directions** — commit to forward (0→n) or backward (n→0), don't mix
3. **Wrong calling convention** — read the problem for "where can I start?" to determine if you call `solve(0)` or `min(solve(0), solve(1))`

Full coverage in `DSA/DeepDive/dp-fundamentals.md` — "Three Rookie Mistakes" section.

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

1. "Maximum money robbing non-adjacent houses" → ___
2. "Count paths in grid, move right or down" → ___
3. "Minimum coins to make amount" → ___
4. "Longest common subsequence of two strings" → ___
5. "Can array be split into two equal-sum subsets?" → ___
6. "Number of ways to decode digit string" → ___

**Answers:** 1. Linear DP (Family 1), 2. Grid DP (Family 2), 3. Unbounded Knapsack (Family 4), 4. String DP (Family 3), 5. 0/1 Knapsack (Family 4), 6. Counting DP (Family 5)

**Part 2 — State Definition (3 minutes)**

For each, write what `dp[i]` or `dp[i][j]` represents:

1. House Robber: `dp[i]` = ___
2. Unique Paths: `dp[r][c]` = ___
3. Coin Change: `dp[i]` = ___
4. LCS: `dp[i][j]` = ___

**Answers:** 1. Max money from houses `[0..i]`, 2. Number of paths to cell `(r,c)`, 3. Min coins to make amount `i`, 4. LCS length of `s1[0..i-1]` and `s2[0..j-1]`

**Part 3 — Write the Recurrence (3 minutes)**

From memory, write the recurrence for House Robber with space optimization (prev1, prev2, current).

**Scoring:** All 3 parts correct = ready. Missed state definition = re-read the 4-step recipe.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| DP deep dive (full theory, all families) | `DSA/DeepDive/dp-fundamentals.md` |
| Three rookie mistakes (State vs Result) | `DSA/DeepDive/dp-fundamentals.md` — "Three Rookie Mistakes" section |
| Grid traversal (BFS/DFS) | `DSA/Interview/graphs.md` |
| Recursion fundamentals | `DSA/DeepDive/recursion-fundamentals.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for DP. 5 pattern families: linear, grid, string, knapsack, counting. Canonical walkthrough (LC 198 House Robber), expanded problem bank with 10 problems (definition + approach + code each). |
