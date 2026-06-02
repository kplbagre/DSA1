# Graph + DP — 3-Hour Crash Course for Salesforce SMTS R1

> **When:** Night before the interview. **Goal:** Build mental models AND code memory. Each problem has: thinking approach → key code snippet → same-pattern problems with their tweak. If you can explain the approach AND write the snippet from memory — you're ready.

---

## 📅 Time Split

```
Graph:  1.5 hours  (9 problems, ~10 min each)
DP:     1.5 hours  (8 problems, ~10 min each)
```

---

# 🧭 GRAPH — 9 Must-Know Problems

---

## G1. LC 200 — Number of Islands ⭐⭐⭐

**Trigger:** "grid", "connected components", "count groups"

**How to think:** Each island = one DFS launch. Outer loop finds unvisited land → launches DFS → DFS marks entire island visited → count++.

**Key snippet — the TWO-LOOP structure:**

```java
// OUTER LOOP — "which component next?"
for (int i = 0; i < row; i++) {
    for (int j = 0; j < col; j++) {
        // MUST check BOTH: land AND unvisited
        if (grid[i][j] == '1' && !visited[i][j]) {
            dfs(i, j, grid, visited);
            count++;  // each DFS launch = one island
        }
    }
}

// INNER DFS — "explore this entire component"
void dfs(int r, int c, char[][] grid, boolean[][] visited) {
    // Bounds + water + visited check
    if (r < 0 || c < 0 || r >= grid.length || c >= grid[0].length
        || grid[r][c] == '0' || visited[r][c]) {
        return;
    }
    visited[r][c] = true;
    // Explore 4 directions
    dfs(r + 1, c, grid, visited);
    dfs(r - 1, c, grid, visited);
    dfs(r, c + 1, grid, visited);
    dfs(r, c - 1, grid, visited);
}
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 695 — Max Area of Island** | DFS returns area count instead of void. `return 1 + dfs(up) + dfs(down) + dfs(left) + dfs(right)` |
| **LC 130 — Surrounded Regions** | Reverse thinking: DFS from BORDER 'O' cells first (mark as safe). Everything else = surrounded → flip to 'X' |
| **LC 1559 — Detect Cycles** | Same outer+inner structure but pass parent `(pr, pc)` to DFS. If visited AND not parent → cycle |

---

## G2. LC 994 — Rotting Oranges ⭐⭐

**Trigger:** "spreading simultaneously", "minimum time", "wavefront"

**How to think:** Multi-source BFS — all rotten oranges start in queue at once. BFS level = one minute.

**Key snippet — multi-source BFS setup:**

```java
Queue<int[]> queue = new ArrayDeque<>();
int fresh = 0;

// Enqueue ALL rotten oranges at once + count fresh
for (int r = 0; r < row; r++) {
    for (int c = 0; c < col; c++) {
        if (grid[r][c] == 2) {
            queue.offer(new int[]{r, c});  // all sources at once
        } else if (grid[r][c] == 1) {
            fresh++;
        }
    }
}

// BFS — each level = one minute
int minutes = 0;
while (!queue.isEmpty() && fresh > 0) {
    int size = queue.size();  // snapshot level size
    for (int i = 0; i < size; i++) {
        int[] cell = queue.poll();
        for (int[] d : DIR) {
            int nr = cell[0] + d[0], nc = cell[1] + d[1];
            if (nr >= 0 && nc >= 0 && nr < row && nc < col && grid[nr][nc] == 1) {
                grid[nr][nc] = 2;  // mark rotten (doubles as visited)
                fresh--;
                queue.offer(new int[]{nr, nc});
            }
        }
    }
    minutes++;
}
return fresh == 0 ? minutes : -1;
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 286 — Walls and Gates** | Multi-source from all gates (0). BFS fills each empty room with distance to nearest gate |
| **LC 542 — 01 Matrix** | Multi-source from all 0s. BFS fills each 1-cell with distance to nearest 0 |

---

## G3. LC 207 — Course Schedule ⭐⭐⭐

**Trigger:** "prerequisites", "dependencies", "can finish all"

**How to think:** Cycle detection via Kahn's (BFS topo sort). If all courses get processed → no cycle → return true.

**Key snippet — build graph + Kahn's:**

```java
// Build adjacency list + in-degree (ONE loop)
// [a, b] → "b unlocks a" → edge b → a
List<List<Integer>> adj = new ArrayList<>();
int[] inDegree = new int[numCourses];
for (int i = 0; i < numCourses; i++) {
    adj.add(new ArrayList<>());
}
for (int[] pre : prerequisites) {
    adj.get(pre[1]).add(pre[0]);  // prereq unlocks dependent
    inDegree[pre[0]]++;
}

// Kahn's BFS — start with in-degree 0
Queue<Integer> queue = new ArrayDeque<>();
for (int i = 0; i < numCourses; i++) {
    if (inDegree[i] == 0) {
        queue.offer(i);
    }
}
int count = 0;
while (!queue.isEmpty()) {
    int course = queue.poll();
    count++;
    for (int next : adj.get(course)) {
        inDegree[next]--;
        if (inDegree[next] == 0) {
            queue.offer(next);
        }
    }
}
return count == numCourses;  // all processed = no cycle
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 210 — Course Schedule II** | Same code, but collect order: `order.add(course)` instead of `count++`. Return order array |
| **LC 269 — Alien Dictionary** | Build adjacency from comparing adjacent words letter by letter. Then same Kahn's |

---

## G4. LC 133 — Clone Graph ⭐⭐

**Trigger:** "clone", "deep copy"

**How to think:** BFS + HashMap (old→new). Map IS the visited set.

**Key snippet:**

```java
Map<Node, Node> map = new HashMap<>();
Queue<Node> queue = new ArrayDeque<>();
map.put(node, new Node(node.val));  // clone the root
queue.offer(node);

while (!queue.isEmpty()) {
    Node curr = queue.poll();
    for (Node neighbor : curr.neighbors) {
        if (!map.containsKey(neighbor)) {
            // First time seeing this neighbor → clone it
            map.put(neighbor, new Node(neighbor.val));
            queue.offer(neighbor);
        }
        // Wire the CLONE's neighbor list using the CLONE of the neighbor
        map.get(curr).neighbors.add(map.get(neighbor));
    }
}
return map.get(node);
```

---

## G5. LC 323 — Number of Connected Components ⭐⭐

**Trigger:** "count components", "number of groups"

**How to think:** Union-Find. Start with n components. Each union → components--.

**Key snippet — Union-Find core:**

```java
int[] parent = new int[n];
int components = n;
for (int i = 0; i < n; i++) {
    parent[i] = i;
}

for (int[] edge : edges) {
    int ra = find(edge[0], parent);
    int rb = find(edge[1], parent);
    if (ra != rb) {
        parent[ra] = rb;  // merge
        components--;
    }
}
return components;

// Find with path compression
int find(int x, int[] parent) {
    while (x != parent[x]) {
        parent[x] = parent[parent[x]];  // path compression
        x = parent[x];
    }
    return x;
}
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 547 — Number of Provinces** | Same Union-Find, but input is adjacency MATRIX not edge list. `if (isConnected[i][j] == 1) union(i, j)` |
| **LC 684 — Redundant Connection** | Union edges one by one. First edge where `find(a) == find(b)` = redundant (creates cycle) |

---

## G6. LC 127 — Word Ladder ⭐⭐

**Trigger:** "shortest transformation", "one letter at a time"

**How to think:** BFS shortest path. Each word = node. Edge = one letter difference. Generate neighbors by trying a-z at each position.

**Key snippet — neighbor generation:**

```java
Set<String> wordSet = new HashSet<>(wordList);
Queue<String> queue = new ArrayDeque<>();
queue.offer(beginWord);
wordSet.remove(beginWord);
int level = 1;

while (!queue.isEmpty()) {
    int size = queue.size();
    for (int i = 0; i < size; i++) {
        char[] word = queue.poll().toCharArray();
        for (int j = 0; j < word.length; j++) {
            char original = word[j];
            // Try every letter a-z at position j
            for (char c = 'a'; c <= 'z'; c++) {
                word[j] = c;
                String candidate = new String(word);
                if (candidate.equals(endWord)) {
                    return level + 1;
                }
                if (wordSet.contains(candidate)) {
                    wordSet.remove(candidate);  // mark visited by removing
                    queue.offer(candidate);
                }
            }
            word[j] = original;  // restore
        }
    }
    level++;
}
return 0;  // no path
```

---

## G7. LC 417 — Pacific Atlantic Water Flow ⭐⭐

**Trigger:** "water flows to both oceans", "can reach from border"

**How to think:** Reverse DFS. Instead of "from each cell, can water reach ocean?" → "from each ocean border, which cells can flow TO here?" Cells in BOTH sets = answer.

**Key snippet — reverse DFS from borders:**

```java
boolean[][] pacific = new boolean[m][n];
boolean[][] atlantic = new boolean[m][n];

// DFS from Pacific borders (top row + left col)
for (int j = 0; j < n; j++) dfs(0, j, pacific, matrix);
for (int i = 0; i < m; i++) dfs(i, 0, pacific, matrix);

// DFS from Atlantic borders (bottom row + right col)
for (int j = 0; j < n; j++) dfs(m - 1, j, atlantic, matrix);
for (int i = 0; i < m; i++) dfs(i, n - 1, atlantic, matrix);

// Cells reachable from BOTH
for (int i = 0; i < m; i++) {
    for (int j = 0; j < n; j++) {
        if (pacific[i][j] && atlantic[i][j]) {
            result.add(List.of(i, j));
        }
    }
}

// DFS: go to neighbor only if neighbor >= current (reverse flow: uphill)
void dfs(int r, int c, boolean[][] reachable, int[][] matrix) {
    reachable[r][c] = true;
    for (int[] d : DIR) {
        int nr = r + d[0], nc = c + d[1];
        if (nr >= 0 && nc >= 0 && nr < m && nc < n
            && !reachable[nr][nc]
            && matrix[nr][nc] >= matrix[r][c]) {  // uphill = reverse flow
            dfs(nr, nc, reachable, matrix);
        }
    }
}
```

---

## G8. LC 329 — Longest Increasing Path in Matrix ⭐⭐⭐ (Asked at Salesforce!)

**Trigger:** "longest increasing path", "grid + longest path"

**How to think:** Grid DFS + Memoization. From each cell, DFS to all 4 neighbors that are STRICTLY greater. Memo avoids recomputation.

**Key snippet — DFS + memo on grid:**

```java
int[][] memo = new int[row][col];
int maxLen = 0;

// Try starting from every cell — memo makes it O(m×n) total
for (int i = 0; i < row; i++) {
    for (int j = 0; j < col; j++) {
        maxLen = Math.max(maxLen, dfs(i, j, matrix, memo));
    }
}
return maxLen;

int dfs(int r, int c, int[][] matrix, int[][] memo) {
    if (memo[r][c] != 0) {
        return memo[r][c];  // already solved — return cached result
    }
    int best = 1;  // at minimum, the cell itself is a path of length 1
    for (int[] d : DIR) {
        int nr = r + d[0], nc = c + d[1];
        if (nr >= 0 && nc >= 0 && nr < row && nc < col
            && matrix[nr][nc] > matrix[r][c]) {  // STRICTLY increasing
            int path = 1 + dfs(nr, nc, matrix, memo);
            best = Math.max(best, path);  // track MAX across all directions
        }
    }
    memo[r][c] = best;  // cache before returning
    return best;
}
```

**Traps (you hit both of these!):**
1. `best = Math.max(best, path)` — NOT just `best = path`. Must track max across ALL 4 directions
2. No `visited[]` needed — the `matrix[nr][nc] > matrix[r][c]` check prevents revisiting (path is strictly increasing → can't go back)

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 62/63 — Unique Paths** | Grid DP but no DFS needed — just `dp[i][j] = dp[i-1][j] + dp[i][j-1]` (iterative) |
| **LC 1091 — Shortest Path in Binary Matrix** | Grid BFS (not DFS) — shortest path needs BFS, not DFS+memo |

---

# 🧭 DP — 8 Must-Know Problems

---

## The 4-Step Recipe (Every DP Problem)

```
1. STATE:      "What do I need to know at position i?"
2. RECURRENCE: "How does dp[i] relate to smaller subproblems?"
3. BASE CASE:  "What's the answer when I can't recurse further?"
4. ANSWER:     "Which cell holds the final answer?"
```

---

## D1. LC 70 — Climbing Stairs ⭐

**Trigger:** "1 or 2 steps", "count ways"

**Family:** Linear DP (Fibonacci)

**Key snippet:**

```java
// dp[i] = ways to reach step i = dp[i-1] + dp[i-2]
int prev2 = 1, prev1 = 1;  // dp[0]=1, dp[1]=1
for (int i = 2; i <= n; i++) {
    int curr = prev1 + prev2;  // can come from 1 step or 2 steps back
    prev2 = prev1;
    prev1 = curr;
}
return prev1;
```

**Same pattern:** LC 746 (Min Cost Climbing Stairs) — same structure but `min` instead of `+`, and add `cost[i]`.

---

## D2. LC 198 — House Robber ⭐⭐⭐ (Salesforce favorite)

**Trigger:** "can't take adjacent", "maximize"

**Family:** Linear DP (skip pattern)

**Key snippet:**

```java
// dp[i] = max money robbing houses 0..i
//       = max(skip house i, rob house i)
//       = max(dp[i-1],    dp[i-2] + nums[i])
int prev2 = 0, prev1 = 0;
for (int num : nums) {
    int curr = Math.max(prev1, prev2 + num);
    prev2 = prev1;
    prev1 = curr;
}
return prev1;
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 213 — House Robber II** | Houses in a circle. Run House Robber TWICE: once on `nums[0..n-2]`, once on `nums[1..n-1]`. Take max |
| **LC 740 — Delete and Earn** | Sort + frequency map → reduces to House Robber on the frequency array |

---

## D3. LC 322 — Coin Change ⭐⭐⭐ (Very likely)

**Trigger:** "minimum coins", "make amount", "unlimited use"

**Family:** Unbounded Knapsack

**Key snippet:**

```java
int[] dp = new int[amount + 1];
Arrays.fill(dp, Integer.MAX_VALUE);  // impossible until proven
dp[0] = 0;  // base: 0 coins for amount 0

for (int i = 1; i <= amount; i++) {
    for (int coin : coins) {
        // Can I use this coin? And is the remainder solvable?
        if (coin <= i && dp[i - coin] != Integer.MAX_VALUE) {
            dp[i] = Math.min(dp[i], dp[i - coin] + 1);
        }
    }
}
return dp[amount] == Integer.MAX_VALUE ? -1 : dp[amount];
```

**Traps:** Init with `MAX_VALUE` not 0. Check `dp[i-coin] != MAX_VALUE` to avoid overflow.

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 518 — Coin Change II** | COUNT ways instead of min coins. `dp[i] += dp[i - coin]`. Outer loop = coins, inner = amounts (to avoid duplicate combos) |
| **LC 279 — Perfect Squares** | Same as Coin Change but "coins" are `1, 4, 9, 16, ...` (perfect squares up to n) |

---

## D4. LC 62 — Unique Paths ⭐⭐

**Trigger:** "grid", "count paths", "right or down only"

**Family:** Grid DP

**Key snippet:**

```java
int[][] dp = new int[m][n];
// First row and first col = 1 (only one way to reach any of them)
for (int i = 0; i < m; i++) dp[i][0] = 1;
for (int j = 0; j < n; j++) dp[0][j] = 1;

for (int i = 1; i < m; i++) {
    for (int j = 1; j < n; j++) {
        dp[i][j] = dp[i - 1][j] + dp[i][j - 1];  // from above + from left
    }
}
return dp[m - 1][n - 1];
```

**Same pattern:** LC 64 (Min Path Sum) — same grid DP but `dp[i][j] = grid[i][j] + min(dp[i-1][j], dp[i][j-1])`. LC 63 (Unique Paths II) — if obstacle, `dp[i][j] = 0`.

---

## D5. LC 1143 — Longest Common Subsequence ⭐⭐⭐

**Trigger:** "two strings", "longest common", "subsequence"

**Family:** String DP (2D table)

**Key snippet:**

```java
int[][] dp = new int[m + 1][n + 1];  // +1 for empty string base case

for (int i = 1; i <= m; i++) {
    for (int j = 1; j <= n; j++) {
        if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
            dp[i][j] = dp[i - 1][j - 1] + 1;           // match → diagonal + 1
        } else {
            dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);  // no match → max(up, left)
        }
    }
}
return dp[m][n];
```

**Mnemonic:** Match → diagonal + 1. No match → max(up, left).

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 72 — Edit Distance** | Match → diagonal (0 cost). No match → `1 + min(diagonal, up, left)` for replace/delete/insert |
| **LC 583 — Delete Operations** | Answer = `m + n - 2 * LCS`. Delete everything that's NOT in the LCS |

---

## D6. LC 300 — Longest Increasing Subsequence ⭐⭐⭐

**Trigger:** "longest increasing", "subsequence"

**Family:** Linear DP (O(n²))

**Key snippet:**

```java
int[] dp = new int[n];
Arrays.fill(dp, 1);  // every element is a LIS of length 1 by itself
int maxLen = 1;

for (int i = 1; i < n; i++) {
    for (int j = 0; j < i; j++) {
        if (nums[j] < nums[i]) {
            dp[i] = Math.max(dp[i], dp[j] + 1);  // extend j's LIS with i
        }
    }
    maxLen = Math.max(maxLen, dp[i]);  // answer = max across ALL positions
}
return maxLen;
```

**Trap:** Answer is `max(dp[...])`, NOT `dp[n-1]`. The longest LIS might not include the last element.

---

## D7. LC 139 — Word Break ⭐⭐

**Trigger:** "can string be segmented", "dictionary words"

**Family:** Linear DP + Set

**Key snippet:**

```java
Set<String> wordSet = new HashSet<>(wordDict);
boolean[] dp = new boolean[n + 1];
dp[0] = true;  // empty string is trivially valid

for (int i = 1; i <= n; i++) {
    for (int j = 0; j < i; j++) {
        // Can I split at j? Everything before j valid AND s[j..i] is a word?
        if (dp[j] && wordSet.contains(s.substring(j, i))) {
            dp[i] = true;
            break;  // found one valid split — enough
        }
    }
}
return dp[n];
```

**In English:** "For each position i, try every possible last word ending at i. If the rest is valid → whole thing is valid."

---

## D8. LC 416 — Partition Equal Subset Sum ⭐⭐

**Trigger:** "partition into two equal halves", "subset sum"

**Family:** 0/1 Knapsack

**Key snippet:**

```java
int sum = 0;
for (int num : nums) sum += num;
if (sum % 2 != 0) return false;  // odd sum → impossible
int target = sum / 2;

boolean[] dp = new boolean[target + 1];
dp[0] = true;  // empty subset sums to 0

for (int num : nums) {
    // RIGHT TO LEFT — so each num is used at most once (0/1 knapsack)
    for (int j = target; j >= num; j--) {
        dp[j] = dp[j] || dp[j - num];
    }
}
return dp[target];
```

**Trap:** Inner loop MUST go RIGHT-TO-LEFT. Left-to-right = unbounded knapsack (reuses same item).

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 494 — Target Sum** | Count ways to assign +/- to reach target. `target = (sum + S) / 2`. Count variant: `dp[j] += dp[j - num]` |
| **LC 474 — Ones and Zeroes** | 2D knapsack: `dp[i][j]` = max strings using at most i zeros and j ones |

---

## ⚡ Quick Decision Tree — In the Interview

```
GRAPH — What pattern?
│
├── Grid + "count groups"            → DFS per component, count launches (G1)
├── Grid + "spreading/min time"      → Multi-source BFS (G2)
├── "Prerequisites / can finish"     → Topo Sort — Kahn's (G3)
├── "Clone / deep copy"              → BFS + HashMap (G4)
├── "Count components" (not grid)    → Union-Find (G5)
├── "Shortest transformation"        → BFS level-by-level (G6)
├── "Flow to border / reachable"     → Reverse DFS from borders (G7)
├── Grid + "longest path + memo"     → DFS + memoization (G8)
└── "Redundant edge / cycle"         → Union-Find (G5 variant)

DP — Which family?
│
├── "Can't take adjacent"            → Linear DP: max(skip, take) (D2)
├── "Min coins / unlimited items"    → Unbounded Knapsack (D3)
├── "Grid paths / min cost"          → Grid DP: above + left (D4)
├── "Two strings / LCS"              → String DP: match→diag, else→max(up,left) (D5)
├── "Longest increasing subseq"      → dp[i]=LIS ending at i, ans=max(dp) (D6)
├── "Can string be segmented?"       → Linear DP + Set (D7)
└── "Partition equal / subset sum"   → 0/1 Knapsack right-to-left (D8)
```

---

## 🔗 Cross-References

| Need more? | File |
| --- | --- |
| Full graph templates + all problems | `DSA/Interview/graphs.md` |
| Full DP templates + all problems | `DSA/Interview/dp.md` |
| Pre-submit bug checklist | `DSA/Interview/common-bugs-checklist.md` |
| Edge direction for topo sort | `DSA/Interview/graphs.md` — Pattern 2 |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** 3-hour crash course — 8 Graph + 8 DP problems. |
| June 2026 | **Enhanced.** Added LC 329 (Grid DFS + Memo — asked at Salesforce). Added key code snippets for every problem. Added "same pattern, small tweak" tables showing related problems. Total: 9 Graph + 8 DP. |
