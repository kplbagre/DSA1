# Common Bugs Checklist — Graph, Tree, Grid & DP

> **Read this file when:** You've written a solution and want to catch silent bugs before submitting. These are not edge cases — they're **structural mistakes** that compile fine, pass small tests, and fail on real inputs. Every bug here was hit during actual practice (May–June 2026).

---

## 🎯 How to Use This File

1. Write your solution
2. Before submitting, scan the relevant section below (Grid? Tree? Graph? DP?)
3. Check each bug against your code — takes 30 seconds
4. Submit with confidence

---

## 🐞 Grid DFS / BFS Bugs

### Bug 1 — Wrong loop bound: `j < row` instead of `j < col`

```java
// ❌ WRONG — iterates columns using row count
for (int j = 0; j < row; j++)

// ✅ FIX
for (int j = 0; j < col; j++)
```

**Hit on:** LC 1559 (Detect Cycles in 2D Grid). Grid was 3×4, skipped the last column entirely. No crash, just wrong answer.

**Mnemonic:** `i < row, j < col` — "i-row, j-col" — say it every time.

---

### Bug 2 — Not skipping visited cells in the outer loop

```java
// ❌ WRONG — calls DFS on already-explored cells
for (int i = 0; i < row; i++) {
    for (int j = 0; j < col; j++) {
        dfs(i, j, ...);
    }
}

// ✅ FIX — skip cells already covered by a previous DFS
for (int i = 0; i < row; i++) {
    for (int j = 0; j < col; j++) {
        if (!visited[i][j]) {
            dfs(i, j, ...);
        }
    }
}
```

**Hit on:** LC 1559 (Detect Cycles). Re-entering visited components double-counted cycles.

**Problems this bug appears in:** LC 200 (Number of Islands), LC 1559 (Detect Cycles), LC 695 (Max Area of Island), LC 130 (Surrounded Regions) — basically ANY "process each connected component" problem.

**Rule:** If the outer loop launches DFS/BFS per component, ALWAYS guard with `!visited[i][j]` (and `grid[i][j] == target` if applicable).

---

### Bug 3 — Outer guard missing the cell-value check

```java
// ❌ WRONG — water cells get DFS'd, counted as islands
if (!visited[i][j]) { dfs(i, j); count++; }

// ✅ FIX — check BOTH land AND unvisited
if (grid[i][j] == '1' && !visited[i][j]) { dfs(i, j); count++; }
```

**Hit on:** LC 200 (Number of Islands). Water cells never get marked visited (DFS bails before marking), so `!visited` alone doesn't filter them.

---

### Bug 4 — Direction typo: `r + dc` instead of `c + dc`

```java
// ❌ WRONG — uses r for both row and col offset
dfs(r + dr, r + dc, ...);

// ✅ FIX
dfs(r + dr, c + dc, ...);
```

**Why it's dangerous:** Compiles fine. Visits wrong cells. Passes small square grids (where row=col). Fails on rectangular grids.

**Checklist:** After writing the direction call, eyeball it — the two prefixes MUST be different (`r` and `c`).

---

### Bug 5 — Marking visited AFTER polling (BFS) instead of BEFORE enqueuing

```java
// ❌ WRONG — same cell gets enqueued multiple times
while (!queue.isEmpty()) {
    int[] cell = queue.poll();
    visited[cell[0]][cell[1]] = true;  // too late!
    // ... enqueue neighbors
}

// ✅ FIX — mark visited WHEN you enqueue
visited[startR][startC] = true;
queue.offer(new int[]{startR, startC});

// ... inside loop, when adding neighbors:
if (!visited[nr][nc]) {
    visited[nr][nc] = true;       // mark HERE, not after poll
    queue.offer(new int[]{nr, nc});
}
```

**Why it matters:** Multiple cells can enqueue the same neighbor before any of them polls it. Result: correct answer but O(V·E) time instead of O(V+E). TLE on large grids.

---

## 🐞 Graph Bugs (Adjacency List / Topo Sort / DFS)

### Bug 6 — Wrong edge direction in topological sort

```java
// ❌ WRONG — "a depends on b" → edge a → b (BACKWARDS)
adj.get(pre[0]).add(pre[1]);

// ✅ FIX — "b unlocks a" → edge b → a
adj.get(pre[1]).add(pre[0]);
inDegree[pre[0]]++;
```

**Hit on:** LC 207 (Course Schedule). In-degree got inverted — the final course (most prerequisites) had in-degree 0 and got processed first. Code ran, no crash, wrong answer.

**Mnemonic:** "prerequisite UNLOCKS dependent" — arrow follows time. `[a, b]` → edge `b → a`.

---

### Bug 7 — Undirected cycle detection without parent check

```java
// ❌ WRONG — reports a cycle on EVERY edge (u→v, then v sees u as visited)
if (visited[nr][nc]) { return true; }

// ✅ FIX — skip the cell you came from
if (nr == pr && nc == pc) { continue; }
if (visited[nr][nc]) { return true; }
```

**Problems:** LC 1559 (Detect Cycles), LC 684 (Redundant Connection), any undirected cycle detection.

---

### Bug 8 — Counting cycles with boolean visited (double-counts)

```java
// ❌ WRONG — can't tell ancestor (real cycle) from finished descendant
boolean[][] visited;
if (visited[nr][nc] && !(nr == pr && nc == pc)) { cycleCount++; }

// ✅ FIX — use 3-state: 0=unvisited, 1=in-progress (gray), 2=done (black)
int[][] state;
if (state[nr][nc] == 1) { cycleCount++; }      // gray = ancestor = real cycle
// state == 2 (black) = already done = NOT a new cycle → skip
```

**Hit on:** LC 1559 when trying to count cycles. Same cycle got counted from both endpoints.

---

### Bug 9 — Directed cycle detection with only `visited[]` (no `pathVisited[]`)

```java
// ❌ WRONG — "seen before" ≠ "cycle" in directed graphs
// Node reachable from multiple ancestors looks like a cycle but isn't
if (visited[node]) { return true; }

// ✅ FIX — need TWO arrays: visited (ever seen) + pathVisited (on current DFS path)
if (pathVisited[node]) { return true; }   // on current path = real cycle
if (visited[node]) { return false; }       // seen before but not on this path = safe
```

**Problems:** LC 207 (Course Schedule via DFS), LC 802 (Eventual Safe States).

---

## 🐞 Tree DFS Bugs

### Bug 10 — Not tracking max across recursive calls

```java
// ❌ WRONG — returns the LAST neighbor's result, not the BEST
for (int[] d : DIR) {
    current = 1 + dfs(nr, nc, ...);
}
return current;   // only has the last direction's value!

// ✅ FIX — track max across ALL directions
int maxCurrent = 1;
for (int[] d : DIR) {
    int current = 1 + dfs(nr, nc, ...);
    maxCurrent = Math.max(maxCurrent, current);
}
return maxCurrent;
```

**Hit on:** LC 329 (Longest Increasing Path in Matrix). Returned the last DFS branch's length instead of the longest one. Passed small cases, failed on grids where the longest path wasn't in the last direction checked.

**Problems this bug appears in:** LC 329, LC 104 (Max Depth), LC 543 (Diameter of Binary Tree), LC 124 (Max Path Sum) — any problem where you explore multiple branches and need the best.

**Rule:** Any time DFS explores multiple children/neighbors, you need `max = Math.max(max, childResult)` inside the loop.

---

### Bug 11 — Confusing "result" with "state" in DP on trees

```java
// ❌ WRONG — carrying running sum as parameter makes memoization impossible
int dfs(int index, int sumSoFar, int[] memo) {
    // state space = (index, sumSoFar) — too many states to memoize!
}

// ✅ FIX — sum is a RESULT (return it), not a STATE (parameter)
int dfs(int index, int[] memo) {
    // state space = (index) only — memoizable!
    return Math.max(dfs(index + 1), nums[index] + dfs(index + 2));
}
```

**Rule:** "Does this value affect my future CHOICES? YES → state (parameter). NO → result (return value)."

---

### Bug 12 — Forgetting `new ArrayList<>(path)` in backtracking

```java
// ❌ WRONG — adds a reference to the same list (which gets cleared later)
result.add(path);

// ✅ FIX — snapshot the current state
result.add(new ArrayList<>(path));
```

**Problems:** LC 78 (Subsets), LC 46 (Permutations), LC 39 (Combination Sum) — every backtracking problem that collects paths.

---

## 🐞 DP Bugs

### Bug 13 — Not initializing DP array correctly

```java
// ❌ WRONG — default 0 means "free" for min-cost problems
int[] dp = new int[n + 1];  // dp[i] = 0 = "zero cost" = wrong base case!

// ✅ FIX — initialize to infinity for min problems
int[] dp = new int[n + 1];
Arrays.fill(dp, Integer.MAX_VALUE);
dp[0] = 0;  // only the real base case is 0
```

**Problems:** LC 322 (Coin Change), LC 279 (Perfect Squares) — any "minimum cost/count" DP.

---

### Bug 14 — Off-by-one in DP array size

```java
// ❌ WRONG — dp[amount] is out of bounds
int[] dp = new int[amount];

// ✅ FIX — need index 0 through amount, so size = amount + 1
int[] dp = new int[amount + 1];
```

**Rule:** If the answer is `dp[n]`, array size must be `n + 1`.

---

### Bug 15 — Forgetting the memo check (recomputing solved subproblems)

```java
// ❌ WRONG — no memo check → exponential time
int dfs(int r, int c, int[][] matrix, int[][] memo) {
    // ... compute result
    memo[r][c] = result;
    return result;
}

// ✅ FIX — check memo FIRST
int dfs(int r, int c, int[][] matrix, int[][] memo) {
    if (memo[r][c] != 0) { return memo[r][c]; }  // already solved
    // ... compute result
    memo[r][c] = result;
    return result;
}
```

**Hit on:** LC 329 (Longest Increasing Path). Without the memo check, the solution is O(4^(m×n)) instead of O(m×n). TLE guaranteed.

---

## ⚡ The 30-Second Pre-Submit Checklist

Before hitting submit on ANY DFS/BFS/DP solution, scan these:

```
GRID:
  □ i < row, j < col (not i < row, j < row)
  □ Outer loop: skip visited AND check cell value
  □ Direction call: r + dr, c + dc (not r + dr, r + dc)
  □ BFS: mark visited ON enqueue, not after poll

GRAPH:
  □ Edge direction: prerequisite → dependent (unlocks, not depends)
  □ Undirected cycle: skip parent before checking visited
  □ Directed cycle: pathVisited[] (on stack), not just visited[]

TREE / DFS:
  □ Max across ALL branches: max = Math.max(max, child) inside loop
  □ Backtracking: result.add(new ArrayList<>(path)) — snapshot!

DP:
  □ Array size: dp[n] needed? → size = n + 1
  □ Init: min problem? → Arrays.fill(dp, Integer.MAX_VALUE)
  □ Memo: check memo[i] FIRST, before computing
```

---

## 🔗 Cross-References

| Topic | Deeper coverage |
| --- | --- |
| Grid DFS bugs (full hall of fame) | `DSA/DeepDive/graphs-fundamentals.md` — "Common bugs hall of fame — grid DFS" |
| BFS mark-on-enqueue vs mark-on-poll | `DSA/DeepDive/graphs-fundamentals.md` — BFS marking discipline |
| Directed vs undirected cycle detection | `DSA/Interview/graphs.md` — Patterns 1, 2 gotchas |
| DP state vs result confusion | `DSA/Interview/dp.md` — "State vs Result Rule" |
| Backtracking snapshot bug | `DSA/Interview/backtracking.md` — Pattern 1 gotchas |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** 15 bugs across Grid, Graph, Tree, and DP — sourced from actual practice bugs (LC 1559, LC 329, LC 200, LC 207) and DeepDive gotchas. Includes 30-second pre-submit checklist. |
