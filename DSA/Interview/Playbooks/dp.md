# Dynamic Programming — Interview Playbook

> **Read this file when:** You have an interview coming up and need to recognize DP patterns fast. Companion to `DSA/DeepDive/dp-fundamentals.md` — this file focuses on pattern recognition and problem mapping, not teaching DP from scratch.

---

## 🎯 Why You're Reading This

DP problems are the most feared interview topic. But 80% of interview DP problems fall into just 5 families. If you can identify which family a problem belongs to in 30 seconds, the template writes itself. This file builds that instinct.

**Prerequisite — CRITICAL:** This file is a pattern-recognition playbook, NOT a learning resource. It shows you templates to recognize fast — it does NOT teach you how to arrive at the recursion.

**For each pattern you're about to study, read the corresponding Family in `DSA/DeepDive/dp-fundamentals.md` FIRST:**

| This file's pattern | Read in dp-fundamentals.md FIRST |
| --- | --- |
| Pattern 1 — Linear DP | Family 1 (1D Linear) — House Robber recursion tree |
| Pattern 2 — Grid DP | Family 2 (2D Grid) — right/down DFS tree |
| Pattern 3 — String DP | **Family 5 (LCS / Strings DP)** — LCS match/mismatch decision tree visual |
| Pattern 4 — 0/1 Knapsack | **Family 3 (0/1 Knapsack)** — take/skip tree, Aditya Verma's insight |
| Pattern 5 — Counting DP | Family 1 + Family 4 (Unbounded variants) |

If you can't mentally derive the recursion for a pattern — **stop, go to dp-fundamentals.md for that Family, come back here after**. Using this file as a shortcut before understanding the recursion is how you memorize patterns without understanding them.

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

**What this solves:** Problems with a 1D sequence where the optimal value at position `i` depends only on the previous 1-2 positions. Classic examples: maximize loot while skipping adjacent items, count ways to reach a position, minimize cost while stepping through a sequence. Each decision is local — you don't need the full history, just the last state or two.

**Recognition cues — reach for this when:**
- "Maximum/minimum value considering elements in sequence"
- "Rob houses" — can't take adjacent
- "Climb stairs" — ways to reach the top
- "Coin change" — minimum coins to make amount
- Decision at each step depends only on previous 1-2 states

**Brute force:** Recursive enumeration of all include/exclude choices at each step. O(2^n) time — each element branches into two paths (take or skip), and the recursion tree has n levels.

```java
// Brute force recursion — O(2^n), no memoization
private int robHelper(int[] nums, int i) {
    // Base: no houses left to consider
    if (i < 0) {
        return 0;
    }
    // Rob house i (skip i-1) vs skip house i
    return Math.max(
        nums[i] + robHelper(nums, i - 2),
        robHelper(nums, i - 1)
    );
}
// Call: robHelper(nums, nums.length - 1)
```

**Key insight:** The optimal at position `i` depends only on the previous 1-2 positions — not the full history. Cache those O(n) subproblems instead of re-computing the same branches exponentially.

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

**Complexity (optimal):** O(n) time, O(1) space with two-variable space optimization.

---

## 🧭 Pattern 2: Grid DP (2D) ⭐

**What this solves:** 2D grid problems where you move in restricted directions (typically right/down only) and want to count paths or find a minimum/maximum cost path from one corner to another. The restriction on movement direction makes this DP — you can only arrive at each cell from a limited set of predecessors.

**Recognition cues — reach for this when:**
- "Count paths from top-left to bottom-right"
- "Minimum cost path in grid"
- "Can only move right or down"

**Brute force:** DFS/recursion exploring all right/down paths from top-left to bottom-right. O(2^(m+n)) time — at each non-boundary cell you branch right or down.

```java
// Brute force recursion — O(2^(m+n)), no memoization
private int paths(int r, int c) {
    // Base: first row or first column has exactly one path
    if (r == 0 || c == 0) {
        return 1;
    }
    // Branch: arrive from above or from the left
    return paths(r - 1, c) + paths(r, c - 1);
}
// Call: paths(m - 1, n - 1)
```

→ **Memoization (top-down, O(m×n) time, O(m×n) space):** same recursion — add a `memo[][]` array. Use `0` as the unvisited sentinel (safe because 0 paths is impossible for valid inputs; for cost problems use `Integer[][]` and check `null`).

```java
private int paths(int r, int c, int[][] memo) {
    if (r == 0 || c == 0) {
        return 1;
    }
    if (memo[r][c] != 0) {
        return memo[r][c];
    }
    memo[r][c] = paths(r - 1, c, memo) + paths(r, c - 1, memo);
    return memo[r][c];
}
// Call: paths(m - 1, n - 1, new int[m][n])
```

**Key insight:** With right/down movement only, every cell `(r, c)` can only be reached from `(r-1, c)` or `(r, c-1)`. So `dp[r][c]` has exactly two predecessors — fill row by row and each cell is computed once.

**The template (tabulation):**

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

**Complexity (optimal):** O(m × n) time, O(m × n) space (or O(n) with a 1D rolling row).

**Space optimization — 1D rolling row:**
> **Mental model:** `dp[r][c]` only needs the row directly above (`dp[r-1][c]`) and the cell to its left (`dp[r][c-1]`). Once row `r` is computed, row `r-1` is never needed again — so a single 1D array updated in-place replaces the full 2D table.
> `dp[j]` before overwriting = previous row's value at column j (= "cell above"). `dp[j-1]` already updated this iteration = "cell to the left".

```java
public int uniquePaths(int m, int n) {
    int[] dp = new int[n];
    // Seed: first row — only one path to any cell in row 0
    Arrays.fill(dp, 1);
    for (int r = 1; r < m; r++) {
        for (int c = 1; c < n; c++) {
            // dp[c] is still the value from the row above; dp[c-1] is already the left
            dp[c] = dp[c] + dp[c - 1];
        }
    }
    return dp[n - 1];
}
```

> **When to mention in an interview:** After stating your 2D solution — say *"We can reduce space to O(n) with a rolling row since each cell only depends on the row above and the cell to its left."*

---

## 🧭 Pattern 3: String DP (Two Strings) ⭐

**What this solves:** Problems comparing two strings character by character, asking for the longest/shortest/count of matching patterns between them. The defining feature: a 2D DP table where rows represent one string and columns the other — each cell answers "what's the answer considering the first `i` characters of `s1` and first `j` characters of `s2`?"

**Recognition cues — reach for this when:**
- "Longest common subsequence" of two strings
- "Edit distance" (min operations to transform)
- "Interleaving string"
- Two strings compared character by character

**Brute force:** Recursion trying all ways to align characters from both strings. O(2^(m+n)) time for LCS — at each mismatch, branch into skip-from-s1 or skip-from-s2.

```java
// Brute force recursion — O(2^(m+n)), no memoization
private int lcs(String s1, String s2, int i, int j) {
    // Base: exhausted one string
    if (i == 0 || j == 0) {
        return 0;
    }
    // Match: both pointers advance together (diagonal move in table)
    if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
        return 1 + lcs(s1, s2, i - 1, j - 1);
    }
    // Mismatch: take best single-character skip
    return Math.max(lcs(s1, s2, i - 1, j), lcs(s1, s2, i, j - 1));
}
// Call: lcs(s1, s2, s1.length(), s2.length())
```

**Key insight:** When characters match, both string indices advance together (diagonal move in the table). When they don't, take the best single-character skip. Only O(m × n) unique (i, j) states exist — cache them.

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

**Complexity (optimal):** O(m × n) time, O(m × n) space (or O(min(m, n)) with a 1D rolling row).

**Space optimization — 1D rolling row:**
> **Mental model:** Same principle as Grid DP — `dp[i][j]` only needs the row above and the diagonal cell (`dp[i-1][j-1]`). Process the shorter string as columns. One row of size `n+1` replaces the full table. Tip: make `s2` the shorter string so you allocate `O(min(m,n))` not `O(max)`.

---

## 🧭 Pattern 4: 0/1 Knapsack / Subset Sum

**What this solves:** Problems asking "can I select a subset of these numbers to hit exactly this target sum?" or "what's the minimum number of items to reach this target?" Each item is used at most once (0/1) or unlimited times (unbounded). The DP table maps possible running sums 0..target, not indices.

**Recognition cues — reach for this when:**
- "Can you partition into two equal-sum subsets?"
- "Count ways to reach target sum using given numbers"
- "Each item can be used once (0/1) or unlimited (unbounded)"

**Brute force:** Recursively try include/exclude for each item at every possible sum. O(2^n) time — each item branches into take/skip, giving an exponential recursion tree.

```java
// Brute force recursion — O(2^n), no memoization
private boolean canReach(int[] nums, int i, int target) {
    // Base: hit the target exactly
    if (target == 0) {
        return true;
    }
    // Base: no items left or overshot
    if (i == 0 || target < 0) {
        return false;
    }
    // Include nums[i-1] in subset vs exclude it
    return canReach(nums, i - 1, target - nums[i - 1])
        || canReach(nums, i - 1, target);
}
// Call: canReach(nums, nums.length, total / 2)
```

**Key insight:** Iterating the sum array right-to-left (0/1) prevents using the same item twice in one pass; left-to-right (unbounded) allows re-use. This turns O(2^n) branching into O(n × target) table fills.

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

**Complexity (optimal):** O(n × target) time, O(target) space.

---

## 🧭 Pattern 5: Counting / Decision DP

**What this solves:** Problems asking "how many distinct ways can this string be decoded/segmented?" Unlike knapsack (optimize a value), you're counting valid paths through a decision tree where the number of interpretations explodes exponentially without memoization. The defining trigger: "number of ways" + choices depending on 1 or 2 positions back.

**Recognition cues — reach for this when:**
- "Number of ways to decode"
- "Word break — can string be segmented?"
- "Number of unique BSTs"

**Brute force:** Recursive enumeration of all valid decodings at each position, branching into 1-digit and 2-digit paths. O(2^n) time — exponential branching at each character.

```java
// Brute force recursion — O(2^n), no memoization
private int decode(String s, int i) {
    // Base: decoded the entire string — one valid way
    if (i == s.length()) {
        return 1;
    }
    // Leading zero — invalid decode path
    if (s.charAt(i) == '0') {
        return 0;
    }
    // One-digit decode
    int ways = decode(s, i + 1);
    // Two-digit decode (only if valid 10-26)
    if (i + 1 < s.length()) {
        int two = Integer.parseInt(s.substring(i, i + 2));
        if (two >= 10 && two <= 26) {
            ways += decode(s, i + 2);
        }
    }
    return ways;
}
// Call: decode(s, 0)
```

**Key insight:** The number of ways to decode `s[0..i]` depends only on `dp[i-1]` (one-digit decode) and `dp[i-2]` (two-digit decode, if valid 10-26). Two lookups collapse exponential branching into a linear table fill.

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

**Complexity (optimal):** O(n) time, O(n) space (or O(1) with two rolling variables).

---

## 🔬 Canonical Problem — LC 198: House Robber

> **Problem:** Given an array of non-negative integers representing money in each house, return the maximum amount you can rob without robbing two adjacent houses.

### Step 1 — Read and identify triggers

"Linear sequence, maximum value, can't take adjacent. This is **Pattern 1: Linear DP**."

### Step 2 — Define the state

"`dp[i]` = maximum money I can rob from houses `[0..i]`. The state is just the index — the running sum is the RESULT I return, not a parameter."

### Step 3 — Recurrence

"At house `i`, I either skip it (`dp[i-1]`) or rob it (`dp[i-2] + nums[i]`). Take the max."

### Step 4 — Code (4 stages: brute recursion → memoized → tabulated → space-opt)

**Stage 1 — Brute recursion (O(2^n) time, O(n) stack):**

```java
private int robHelper(int[] nums, int i) {
    // Base: no houses left to consider
    if (i < 0) {
        return 0;
    }
    // Rob house i (skip i-1) vs skip house i
    return Math.max(
        nums[i] + robHelper(nums, i - 2),
        robHelper(nums, i - 1)
    );
}
// Call: robHelper(nums, nums.length - 1)
```

→ **Stage 2 — Memoization (top-down, O(n) time, O(n) space):** same recurrence — wrap with a `memo[]` array to short-circuit repeated calls.

```java
public int rob(int[] nums) {
    int[] memo = new int[nums.length];
    Arrays.fill(memo, -1);
    return robMemo(nums, nums.length - 1, memo);
}
private int robMemo(int[] nums, int i, int[] memo) {
    if (i < 0) {
        return 0;
    }
    if (memo[i] != -1) {
        return memo[i];
    }
    memo[i] = Math.max(
        nums[i] + robMemo(nums, i - 2, memo),
        robMemo(nums, i - 1, memo)
    );
    return memo[i];
}
```

> **Lesson learned the hard way (June 2026):** First memoization attempt used `Integer[n+1]` (tabulation sizing), which forced 1-indexed thinking (`mem[i]` = first i houses), which forced `nums[i-1]` offset. The offset is where subtle bugs live. Root cause: wrong array size imported from the next stage. See sizing rule below.

**Array sizing rule — say this out loud before writing the array:**

| Stage | Array type | Size | Index meaning | House value |
| --- | --- | --- | --- | --- |
| Memoization | `Integer[]` | `n` | `memo[i]` = answer starting at index `i` (0-based, mirrors recursion) | `nums[i]` — direct, no offset |
| Tabulation | `int[]` | `n+1` | `dp[i]` = answer for first `i` houses (1-based count) | `nums[i-1]` — offset because dp is 1-indexed |

**Why memoization is size `n`:** It mirrors the recursive function. Recursion says "I'm at index `i`" (0-based). Memo stores that result at `memo[i]`. Indices run 0 to n-1 → size `n`.

**Why tabulation is size `n+1`:** You need `dp[0] = 0` as a "zero houses" base case so `dp[1]` and `dp[2]` compute cleanly. That extra slot shifts everything 1-right, so `dp[i]` = first i houses and the house value is `nums[i-1]`.

**One-line interviewer answer:** "Memoization mirrors the 0-based recursive index so size n. Tabulation needs a dp[0] base case for zero elements so size n+1."

→ **Stage 3 — Tabulation (bottom-up, O(n) time, O(n) space):** flip the recursion into a forward loop — no call stack, same recurrence.

```java
public int rob(int[] nums) {
    int n = nums.length;
    if (n == 1) {
        return nums[0];
    }
    int[] dp = new int[n];
    dp[0] = nums[0];
    dp[1] = Math.max(nums[0], nums[1]);
    for (int i = 2; i < n; i++) {
        dp[i] = Math.max(dp[i - 1], dp[i - 2] + nums[i]);
    }
    return dp[n - 1];
}
```

→ **Stage 4 — Space optimization (O(n) time, O(1) space):** `dp[i]` only reads `dp[i-1]` and `dp[i-2]` — two variables replace the entire array.

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

> **Brute force:** Recursive tree trying all 1-step and 2-step combinations. O(2^n) time — binary branching at each step.
> **Key insight:** Fibonacci in disguise — ways to reach step `i` only depends on the previous two steps. Two variables replace the full array.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 198: House Robber

> **Problem:** Rob houses in a line, can't rob two adjacent. Maximize total money.

> **Brute force:** Try all 2^n subsets of non-adjacent houses. O(2^n) time.
> **Key insight:** At each house, the decision is rob-it-or-skip-it — only the previous two values matter, not the full history.
> **Approach:** `dp[i] = max(dp[i-1], dp[i-2] + nums[i])` — skip or rob current house. Space-optimize with two variables.

```java
// See canonical walkthrough above for full code
int current = Math.max(prev1, prev2 + nums[i]);
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 213: House Robber II

> **Problem:** Same as House Robber but houses are in a **circle** — first and last house are adjacent.

> **Brute force:** Try all 2^n non-adjacent subsets respecting the circular constraint. O(2^n) time.
> **Key insight:** First and last house are adjacent so you can never take both — break the circle by running House Robber twice (once excluding each endpoint) and take the max.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 746: Min Cost Climbing Stairs

> **Problem:** Given array `cost` where `cost[i]` is the cost to step on stair `i`, find the minimum cost to reach the top (past the last index). You can start from index 0 or 1.

> **Brute force:** Recursive exploration of all 1-step/2-step paths, accumulating cost. O(2^n) time.
> **Key insight:** Same recurrence as House Robber but minimizing instead of maximizing — only the previous two costs matter at each step.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 62: Unique Paths

> **Problem:** Robot on an `m × n` grid starts at top-left, can only move right or down. Count the number of unique paths to bottom-right.

> **Brute force:** DFS exploring all right/down paths from top-left to bottom-right. O(2^(m+n)) time.
> **Key insight:** Each cell has exactly two predecessors (above and left) — count paths by summing those two, filling row by row in O(m × n).
> **Approach:** `dp[r][c] = dp[r-1][c] + dp[r][c-1]`. First row and first column are all 1s (only one way to reach them).

```java
// See Pattern 2 template above for full code
dp[r][c] = dp[r - 1][c] + dp[r][c - 1];
```

**Complexity (optimal):** O(m × n) time, O(m × n) space.

---

### LC 64: Minimum Path Sum

> **Problem:** Given an `m × n` grid of non-negative numbers, find a path from top-left to bottom-right that minimizes the sum. Can only move right or down.

> **Brute force:** DFS exploring all right/down paths, summing cell values. O(2^(m+n)) time.
> **Key insight:** Same two-predecessor structure as Unique Paths but minimize instead of count — can modify the grid in-place with O(1) extra space.
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

**Complexity (optimal):** O(m × n) time, O(1) extra space (in-place).

---

### LC 322: Coin Change

> **Problem:** Given coin denominations and a target amount, return the minimum number of coins needed. Each coin can be used unlimited times. Return -1 if impossible.

> **Brute force:** Enumerate all combinations of coins that sum to amount. O(amount^numCoins) time.
> **Key insight:** Unbounded knapsack — iterate left-to-right so each coin can be reused. `dp[i - coin] + 1` reuses previously computed minimums.
> **Approach:** Unbounded knapsack. `dp[i] = min(dp[i], dp[i - coin] + 1)` for each coin. Iterate left-to-right (coins are reusable).

```java
// See Pattern 4 template above for full code
dp[i] = Math.min(dp[i], dp[i - coin] + 1);
```

**Complexity (optimal):** O(n × amount) time, O(amount) space.

---

### LC 1143: Longest Common Subsequence

> **Problem:** Given two strings, return the length of their longest common subsequence (characters in order but not necessarily contiguous).

> **Brute force:** Recursion trying all ways to align characters — skip from s1, skip from s2, or match both. O(2^(m+n)) time.
> **Key insight:** On a match, take the diagonal (`dp[i-1][j-1] + 1`); on a mismatch, take the best single skip. Only O(m × n) unique states.
> **Approach:** `dp[i][j]` = LCS of `s1[0..i-1]` and `s2[0..j-1]`. If chars match → `dp[i-1][j-1] + 1`. Else → `max(dp[i-1][j], dp[i][j-1])`.

```java
// See Pattern 3 template above for full code
if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
    dp[i][j] = dp[i - 1][j - 1] + 1;
} else {
    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
}
```

**Complexity (optimal):** O(m × n) time, O(m × n) space.

---

### LC 416: Partition Equal Subset Sum

> **Problem:** Given array of positive integers, determine if it can be partitioned into two subsets with equal sum.

> **Brute force:** Try all 2^n subsets, check if any sums to half the total. O(2^n) time.
> **Key insight:** Reduce to subset-sum: can any subset reach `total / 2`? Use 0/1 knapsack with right-to-left iteration to prevent reusing elements.
> **Approach:** If total is odd → impossible. Else find if any subset sums to `total / 2`. 0/1 knapsack — iterate right-to-left to avoid reusing elements.

```java
// See Pattern 4 template above for full code
dp[j] = dp[j] || dp[j - num];
```

**Complexity (optimal):** O(n × target) time, O(target) space.

---

### LC 91: Decode Ways

> **Problem:** A message of digits can be decoded where `'A'=1, 'B'=2, ..., 'Z'=26`. Given a string of digits, count the number of ways to decode it.

> **Brute force:** Recursive 1-digit/2-digit branching at each position. O(2^n) time — exponential branching at each character.
> **Key insight:** `dp[i] = dp[i-1]` (one-digit, if valid) `+ dp[i-2]` (two-digit, if 10-26). Two lookups collapse exponential branching.
> **Approach:** `dp[i]` = ways to decode `s[0..i-1]`. One-digit decode (if valid) adds `dp[i-1]`. Two-digit decode (if 10-26) adds `dp[i-2]`.

```java
// See Pattern 5 template above for full code
if (oneDigit >= 1) dp[i] += dp[i - 1];
if (twoDigit >= 10 && twoDigit <= 26) dp[i] += dp[i - 2];
```

**Complexity (optimal):** O(n) time, O(n) space.

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

### Common rookie DP mistakes:

1. **State vs Result confusion** — carrying accumulated value as parameter instead of returning it
2. **Mixing recursion directions** — commit to forward (0→n) or backward (n→0), don't mix
3. **Wrong calling convention** — read the problem for "where can I start?" to determine if you call `solve(0)` or `min(solve(0), solve(1))`
4. **Stage contamination** — using tabulation variable names (`prev1`, `prev2`) while writing brute-force recursion. Each stage has its own vocabulary: recursion has parameters + return values only. No arrays, no `prev` variables until tabulation.
5. **Memoization template improvisation** — caching sub-results (`memo[n-1]`, `memo[n-2]`) instead of the current call's own result (`memo[n]`). The template is always: check at top → compute → cache own slot → return. Never invent a variation.
6. **Carrying `Integer[]` into tabulation** — `Integer[]` (boxed) is the right type for memoization because you need `null` to detect uncomputed slots. The moment you switch to tabulation, switch to `int[]` (primitive) — no null checks, no boxing overhead, cleaner code.
7. **Wrong array size causes index offset bugs** — using `Integer[n+1]` in memoization forces you into 1-indexed thinking (`memo[i]` = first i elements → `nums[i-1]`). That off-by-one offset is where subtle bugs hide. Rule: memoization = size `n` (0-indexed, mirrors recursion), tabulation = size `n+1` (1-indexed, needs dp[0] base case). If you find yourself writing `nums[i-1]` inside a memoization function, your array is the wrong size.

> **Lesson learned the hard way (June 2026):** Mistakes 4, 5, 6, and 7 all hit during House Robber / Climbing Stairs practice. The `n+1` sizing in memo was the domino — it forced 1-indexed thinking, which forced `nums[i-1]`, which is the exact bug that costs 10 minutes in an interview. Mental checklist at each stage: array type? (`Integer[]` memo, `int[]` tab) → array size? (n memo, n+1 tab) → index offset? (none in memo, i-1 in tab).

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
| Grid traversal (BFS/DFS) | `DSA/Interview/Playbooks/graphs.md` |
| Recursion fundamentals | `DSA/DeepDive/recursion-fundamentals.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for DP. 5 pattern families: linear, grid, string, knapsack, counting. Canonical walkthrough (LC 198 House Robber), expanded problem bank with 10 problems (definition + approach + code each). |
| June 2026 | **Brute force / Key insight pass.** Added `**What this solves**`, `**Brute force**`, `**Key insight**`, `**Complexity (optimal)**` to all 5 patterns and all 10 problem bank entries. Format matches `binary-search.md` and `heaps.md`. |
| June 2026 | **Recursion bridge (Option B).** Added naked recursion code sketch to each of the 5 pattern Brute force sections. Expanded canonical walkthrough (LC 198) to show all 4 stages: brute recursion → memoization (top-down) → tabulation (bottom-up) → space optimization. |
| June 2026 | **Practice mistakes logged.** Added lesson-learned callout after Stage 2 in canonical walkthrough (memoization template + array sizing). Extended rookie mistakes list from 3 to 5: stage contamination + memoization template improvisation. |
| June 2026 | **Space optimization callouts added.** Added 1D rolling-row mental model + code to Pattern 2 (Grid DP) and a brief note to Pattern 3 (String DP). Pattern 1 and Pattern 4 already carried space optimization. Triggered by LC 64 follow-up question. |
