# BFS & DFS — Templates Reference

> **The seven flavors of BFS / DFS in one place.** Live in this file for daily revision (2-3×/week). Every flavor opens with **KEY STEPS** (memorize), then code with inline "what + how" comments, then a **🐞 Where I make mistakes** callout calling out the *specific* bugs that have shown up in past attempts.
>
> Companion deep dives (read once, then come back here):
> - **`../DeepDive/trees-fundamentals.md`** — tree DFS / BFS conceptual mental models, recursion call stack, BST.
> - **`../DeepDive/graphs-fundamentals.md`** — graph representations, mark-on-enqueue contract, multi-source BFS, cycle detection.
> - **`../../JavaBackend/DeepDive/java-pass-by-value-semantics.md`** — why `visited[][]` mutation propagates across recursive calls.

---

## 🎯 The Seven Flavors (at a glance)

| # | Flavor | Vertex | Neighbors | Marking | Return |
| --- | --- | --- | --- | --- | --- |
| 1 | **Tree DFS — Top-Down** | `TreeNode` | `node.left` / `node.right` | not needed (no cycles) | `void` (carry state down) |
| 2 | **Tree DFS — Bottom-Up** | `TreeNode` | `node.left` / `node.right` | not needed (no cycles) | computed value (bubble up) |
| 3 | **Tree BFS — Level Order** | `TreeNode` | `node.left` / `node.right` | not needed | `void` / list of levels |
| 4 | **Grid DFS** | `(r, c)` | 4 / 8 directions | in-place OR `boolean[][] visited` | `void` or computed |
| 5 | **Grid BFS** | `int[]{r,c}` | 4 / 8 directions | **mark-on-enqueue** | `void` or distance |
| 6 | **Adj-List DFS** | `int u` | `adj.get(u)` | `boolean[] visited` | `void` or computed |
| 7 | **Adj-List BFS** | `int u` | `adj.get(u)` | **mark-on-enqueue** | `void` or distance |

> **The mental shortcut:** trees never need `visited` (no cycles). Graphs (grid + adjacency list) ALWAYS need `visited`. Everything else is the same skeleton with different "vertex" and "neighbor" expressions.

---

## 🧠 Naming Conventions — Memorize These (most bugs live here)

> **Lesson learned the hard way (May 2026):** `dr`, `dc`, `r`, `c`, `i`, `j` are all 1-2 character symbols that interact on the same line. Eyes glaze over them — a `r + dc` vs `c + dc` typo is invisible until tests fail. The fix is not "be more careful," it's **enforce a strict naming convention every single time**.

| Symbol | Meaning | Where it lives |
| --- | --- | --- |
| `DIRS` | 4-direction constant `{{-1,0},{1,0},{0,-1},{0,1}}` | **Class-level** `static final int[][]` |
| `dr`, `dc` | The **delta** (one entry of DIRS) | Inside the neighbor-loop ONLY |
| `r`, `c` | **Current** row / col | Method parameter or extracted from `cell` |
| `nr`, `nc` | **New** row / col after applying delta | Computed as `r + dr`, `c + dc` |
| `m`, `n` | Grid `rows`, `cols` | Method parameters |
| `i`, `j` | Outer loop indices (NOT row/col deltas) | `for (int i = 0; ...)` |

**The line that needs the most eyeball scrutiny:**

```java
int nr = r + dr;
int nc = c + dc;
```

**Both prefixes must differ.** Never `r + dr, r + dc` (same prefix `r` twice). Never `dr + dr, dc + dc`. Say it aloud as you write: *"new row is current row plus row-delta; new col is current col plus col-delta."*

---

## ✅ Verify-Before-Submit Checklist (run on every BFS/DFS solution)

1. **Dims:** `m = grid.length`, `n = grid[0].length`. NEVER `grid[1].length` — crashes on 1-row grids.
2. **Outer guard:** `grid[i][j] == '1' && !visited[i][j]` — say *"LAND and UNVISITED."* Forgetting the LAND check = every water cell becomes its own island.
3. **Direction call:** `nr = r + dr; nc = c + dc;` — prefixes must differ.
4. **Marking discipline:** BFS marks **before** `offer()`. DFS marks **before** the recursive call (or at function entry). Never mark on `poll()`.
5. **Class fields ≠ locals:** class-level direction array is `DIRS`. Local deltas are `dr` / `dc`. No shadowing.
6. **No `q.offer({r, c})`** — Java demands `q.offer(new int[]{r, c})`. The `{...}` literal is only legal in declarations.
7. **`int[]` vs `int[][]`** — direction array is `int[][]` (2D). Each delta is `int[]` (1D).

---

## 🔹 Flavor 1 — Tree DFS, Top-Down (carry state DOWN)

**KEY STEPS:**

1. **Base case:** `if (node == null) return;` — no work to do.
2. **Do work** using the state passed in (depth, running sum, ancestor max, etc.).
3. **Recurse into both children with the UPDATED state** — `depth + 1`, `sum + node.val`, etc.

```java
// Top-down DFS — state flows from parent → child via parameters.
// Method returns void. The "answer" lives in a class field or a passed-in list.

private int maxDepth;

public void dfs(TreeNode node, int depth) {
    // Step 1 — base case (no node = nothing to record)
    if (node == null) {
        return;
    }

    // Step 2 — do work using the carried state
    maxDepth = Math.max(maxDepth, depth);

    // Step 3 — recurse with UPDATED state (depth + 1 for each child)
    dfs(node.left, depth + 1);
    dfs(node.right, depth + 1);
}
```

> 🐞 **Where I make mistakes:**
> - **Reassigning a primitive accumulator parameter and expecting it to bubble up.** `int max` is copied per frame — mutation in a child frame is invisible to the parent. Use an **instance field** for accumulators that need to be shared across all frames.
> - **Forgetting the `null` base case** — every recursive call eventually hits `null` children of leaves; without the guard you NPE on `node.val`.
> - **Not resetting the instance field** at the top of the public method — LeetCode reuses `Solution` across test cases. Always reset.

**🏷️ Example problems:** LC 104 Maximum Depth (also bottom-up), LC 112 Path Sum, LC 129 Sum Root to Leaf Numbers, LC 1448 Count Good Nodes.

> See `../DeepDive/trees-fundamentals.md` § "Pattern 1: Top-Down DFS" for the LC 112 walkthrough with steps.

---

## 🔹 Flavor 2 — Tree DFS, Bottom-Up (collect & return UP) ⭐

**KEY STEPS:**

1. **Base case:** `if (node == null) return baseValue;` — neutral value that doesn't poison the combine step (`0` for sums, `Integer.MIN_VALUE` for maxes).
2. **Recurse left** — store the returned value.
3. **Recurse right** — store the returned value.
4. **Combine** this node's value with both children's returns, **return that to the parent**.

```java
// Bottom-up DFS — each call returns a computed value about its subtree.
// Parent combines children's returns. Most common tree-DFS pattern.

public int maxDepth(TreeNode node) {
    // Step 1 — base case: empty subtree has depth 0
    if (node == null) {
        return 0;
    }

    // Step 2 + 3 — collect children's answers (trust the recursion)
    int leftDepth = maxDepth(node.left);
    int rightDepth = maxDepth(node.right);

    // Step 4 — combine: this node adds 1 to the deeper subtree
    return 1 + Math.max(leftDepth, rightDepth);
}
```

**Two-purpose recursion variant** (when global answer ≠ return value — e.g., LC 543 Diameter):

```java
private int diameter;                          // GLOBAL = best bent-path so far

public int diameterOfBinaryTree(TreeNode root) {
    diameter = 0;                              // reset for this LC test case
    height(root);                              // recursion side-effects the global
    return diameter;
}

private int height(TreeNode node) {
    if (node == null) {
        return 0;
    }
    int left = height(node.left);
    int right = height(node.right);

    // Two-purpose recursion:
    //   GLOBAL update uses BOTH sides (the bent path through this node)
    diameter = Math.max(diameter, left + right);
    //   RETURN to parent uses ONE side (parent can only extend one branch)
    return 1 + Math.max(left, right);
}
```

> 🐞 **Where I make mistakes:**
> - **Passing `int diameter` as a parameter and reassigning it.** Java primitives are pass-by-value — every frame gets its own copy. The recursion runs, the global stays `0`. **Fix:** instance field (or `int[]{0}` array wrapper).
> - **Forgetting to reset the instance field** in the public method.
> - **Wrong neutral base value** — returning `0` when you need `Integer.MIN_VALUE` (for max-path-style problems where every value could be negative).

**🏷️ Example problems:** LC 104, LC 110 Balanced BT, LC 543 Diameter, LC 124 Max Path Sum (🔴 senior+).

> Full mental model + the "ladder" up to LC 124: `../DeepDive/trees-fundamentals.md` § "Pattern 2: Bottom-Up DFS".

---

## 🔹 Flavor 3 — Tree BFS, Level Order ⭐

**KEY STEPS:**

1. **Empty-tree guard** — return immediately if root is `null`.
2. **Queue with root.** Use `ArrayDeque<TreeNode>` (faster than `LinkedList`).
3. **Outer loop** while queue is non-empty.
4. **Snapshot the level size** — `int size = queue.size();` BEFORE the inner loop. (Reading `queue.size()` inside would mix levels as children get added.)
5. **Inner loop** processes exactly `size` nodes: poll, record, offer non-null children.
6. **Append the level** to the result.

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();

    // Step 1 — empty tree
    if (root == null) {
        return result;
    }

    // Step 2 — queue seeded with root
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);

    // Step 3 — outer loop = one iteration per LEVEL
    while (!queue.isEmpty()) {
        // Step 4 — SNAPSHOT level boundary (critical line)
        int size = queue.size();
        List<Integer> level = new ArrayList<>();

        // Step 5 — drain exactly `size` nodes (this level only)
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);

            // Push non-null children — they become the NEXT level
            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }

        // Step 6 — level complete
        result.add(level);
    }
    return result;
}
```

> 🐞 **Where I make mistakes:**
> - **Reading `queue.size()` inside the inner loop** — it grows as we push children, so the inner loop never ends OR processes nodes from the next level. **The snapshot at Step 4 is the entire point of level-order BFS.**
> - **Not null-checking children before offer** — `queue.offer(null)` succeeds in `ArrayDeque` ❌ but then `poll()` returns null and `node.val` NPEs.
> - **Using `LinkedList` as `Queue`** — works but slower. `ArrayDeque` is the modern idiom.

**🏷️ Example problems:** LC 102 Level Order, LC 103 Zigzag, LC 199 Right Side View, LC 515 Largest Value in Each Row, LC 1161 Max Level Sum.

> Queue animation: `../DeepDive/trees-fundamentals.md` § "🎨 Visual — BFS Queue Animation".

---

## 🔹 Flavor 4 — Grid DFS ⭐

**KEY STEPS:**

1. **Entry guard (combined check)** — out-of-bounds OR water OR already-visited → return.
2. **Mark visited** — in-place (`grid[r][c] = '0'`) or `visited[r][c] = true`.
3. **Recurse into 4 neighbors** using `DIRS`.

```java
private static final int[][] DIRS = {
    {-1, 0}, {1, 0}, {0, -1}, {0, 1}
};   // ← class-level constant. NEVER shadow this name with a local.

private void dfs(char[][] grid, boolean[][] visited, int r, int c, int m, int n) {
    // Step 1 — combined entry guard (bounds FIRST so we never index OOB)
    if (r < 0 || r >= m || c < 0 || c >= n || grid[r][c] == '0' || visited[r][c]) {
        return;
    }

    // Step 2 — mark BEFORE recursing (otherwise infinite recursion)
    visited[r][c] = true;

    // Step 3 — 4-way recursion
    for (int[] d : DIRS) {
        int dr = d[0];
        int dc = d[1];
        // CRITICAL: nr = r + dr, nc = c + dc — prefixes MUST differ
        int nr = r + dr;
        int nc = c + dc;
        dfs(grid, visited, nr, nc, m, n);
    }
}
```

**In-place variant** (drop-in replacement — no `visited[][]` parameter, mutate `grid` directly):

```java
private void dfs(char[][] grid, int r, int c, int m, int n) {
    // Bounds FIRST, then value check (water OR already-flipped = '0')
    if (r < 0 || r >= m || c < 0 || c >= n || grid[r][c] != '1') {
        return;
    }
    grid[r][c] = '0';                              // mark in place
    for (int[] d : DIRS) {
        dfs(grid, r + d[0], c + d[1], m, n);
    }
}
```

> 🐞 **Where I make mistakes (the May-2026 LC 200 bug list):**
> - **`grid[1].length` instead of `grid[0].length`** — crashes on 1-row inputs.
> - **`r + dc` typo** (using `r` prefix for the column delta too) — silently visits wrong cells. Always re-read the line: `nr = r + dr; nc = c + dc;`.
> - **Class field named `dr`** then local `int dr = d[0]` — Java legal but confusing. Class-level array stays `DIRS`.
> - **Bounds check AFTER value check** — `grid[r][c] != '1' || r < 0 ...` throws `ArrayIndexOutOfBoundsException` for negative indices. Bounds FIRST, always.
> - **Outer loop forgets `grid[i][j] == '1'`** — see Flavor 4-outer below.

### Outer driver — the "two loops, two different questions" callout

```java
public int numIslands(char[][] grid) {
    int m = grid.length;
    int n = grid[0].length;
    boolean[][] visited = new boolean[m][n];
    int count = 0;

    for (int i = 0; i < m; i++) {
        for (int j = 0; j < n; j++) {
            // OUTER guard: LAND ✅ AND UNVISITED ✅ — BOTH must hold.
            // Forgetting `grid[i][j] == '1'` makes every water cell count as an island.
            if (grid[i][j] == '1' && !visited[i][j]) {
                dfs(grid, visited, i, j, m, n);
                count++;
            }
        }
    }
    return count;
}
```

> **Inner DFS asks:** *"Should I enter THIS cell?"* (bounds + land + unvisited)
> **Outer loop asks:** *"Should I START a new DFS from THIS cell?"* (land + unvisited — already in bounds by loop construction).

**🏷️ Example problems:** LC 200 Number of Islands, LC 695 Max Area of Island (bottom-up DFS), LC 733 Flood Fill, LC 130 Surrounded Regions.

> Full visual + recursion tree: `../DeepDive/graphs-fundamentals.md` § "🎨 Visual — DFS sweeping a 2×2 island".

---

## 🔹 Flavor 5 — Grid BFS ⭐

**KEY STEPS:**

1. **Mark start visited AND offer to queue** — in the same breath.
2. **While queue non-empty:** poll a cell, examine its 4 neighbors.
3. For each neighbor: if in-bounds AND land AND unvisited → **mark visited FIRST, then offer**.

```java
private static final int[][] DIRS = {
    {-1, 0}, {1, 0}, {0, -1}, {0, 1}
};

private void bfs(char[][] grid, boolean[][] visited, int startR, int startC, int m, int n) {
    Queue<int[]> queue = new ArrayDeque<>();

    // Step 1 — MARK + OFFER in the same breath (mark-on-enqueue contract)
    visited[startR][startC] = true;
    queue.offer(new int[]{startR, startC});         // NOT q.offer({0,0}) — syntax error!

    // Step 2 — drain queue
    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        int r = cell[0];                            // EXTRACT inside loop, never declare
        int c = cell[1];                            //   r, c outside — they'd never update

        // Step 3 — examine 4 neighbors
        for (int[] d : DIRS) {
            int dr = d[0];
            int dc = d[1];
            int nr = r + dr;                        // new row — prefix must be `r`
            int nc = c + dc;                        // new col — prefix must be `c`

            // Combined guard: bounds FIRST, then value + visited
            if (nr < 0 || nr >= m || nc < 0 || nc >= n) {
                continue;
            }
            if (grid[nr][nc] != '1' || visited[nr][nc]) {
                continue;
            }

            // MARK BEFORE OFFER — never the other way around
            visited[nr][nc] = true;
            queue.offer(new int[]{nr, nc});
        }
    }
}
```

> 🐞 **Where I make mistakes (the May-2026 grid-BFS bug list):**
> - **`int[] dir = {{-1,0}, ...}`** — declared 1D, initialized 2D. Must be `int[][] dir`.
> - **`q.offer({0, 0})`** — array literals are only legal in declarations. Use `q.offer(new int[]{0, 0})`.
> - **Declaring `int r = 0, c = 0` OUTSIDE the while loop and never updating** — `r` and `c` must be **extracted from `cell`** each iteration. Otherwise they stay 0 forever.
> - **`q.offer(d)`** — accidentally enqueueing the direction vector instead of the new cell. Always `q.offer(new int[]{nr, nc})`.
> - **Mark-on-poll instead of mark-on-offer** — same cell ends up in queue multiple times. For distance-tracking BFS this is a correctness bug (wrong distances), not just an efficiency one.
> - **Using `nr`/`nc` to enqueue but `dr`/`dc` in the bounds check** — pick one naming convention and stick to it within the function.
> - **`gird` typo** (random) — keep an eye out, this has happened more than once.
> - **`ans` declared in one method, used in another** — scope error, won't compile.

**🏷️ Example problems:** LC 994 Rotten Oranges (multi-source BFS), LC 542 01 Matrix, LC 1091 Shortest Path in Binary Matrix, LC 1162 As Far From Land As Possible.

> Layer-by-layer animation + queue trace: `../DeepDive/graphs-fundamentals.md` § "🎨 Visual — BFS layer-by-layer on the same 2×2 island".

### Distance-tracking BFS variant (when level/distance matters)

```java
public int shortestPath(int[][] grid, int startR, int startC) {
    int m = grid.length;
    int n = grid[0].length;
    boolean[][] visited = new boolean[m][n];
    Queue<int[]> queue = new ArrayDeque<>();

    visited[startR][startC] = true;
    queue.offer(new int[]{startR, startC});
    int distance = 0;

    while (!queue.isEmpty()) {
        int size = queue.size();                    // SNAPSHOT level — same trick as tree BFS!
        for (int i = 0; i < size; i++) {
            int[] cell = queue.poll();
            int r = cell[0];
            int c = cell[1];

            // (target check here if needed — return distance)

            for (int[] d : DIRS) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (nr < 0 || nr >= m || nc < 0 || nc >= n) {
                    continue;
                }
                if (grid[nr][nc] == 0 || visited[nr][nc]) {
                    continue;
                }
                visited[nr][nc] = true;
                queue.offer(new int[]{nr, nc});
            }
        }
        distance++;                                 // one full level processed = +1 distance
    }
    return -1;                                      // unreachable
}
```

> **Same `int size = queue.size()` snapshot trick as Tree BFS.** Grid BFS and Tree BFS are byte-for-byte identical at the loop level — only the "neighbors" expression differs.

---

## 🔹 Flavor 6 — Adjacency-List DFS

**KEY STEPS:**

1. **Initialize `boolean[] visited`** of size `V` (in the driver).
2. **Helper: mark current vertex visited + record on entry**, BEFORE the neighbor loop.
3. **For each neighbor:** if not visited → recurse.

```java
public List<Integer> dfs(int start, List<List<Integer>> adj, int V) {
    boolean[] visited = new boolean[V];
    List<Integer> order = new ArrayList<>();
    dfsHelper(start, adj, visited, order);
    return order;
}

private void dfsHelper(int u, List<List<Integer>> adj,
                       boolean[] visited, List<Integer> order) {
    // Step 2 — MARK + RECORD on entry (before exploring neighbors)
    visited[u] = true;
    order.add(u);

    // Step 3 — recurse on unvisited neighbors only
    for (int v : adj.get(u)) {
        if (!visited[v]) {
            dfsHelper(v, adj, visited, order);
        }
    }
}
```

### Iterative variant (when V ≥ 10⁵ — stack-overflow risk)

```java
public List<Integer> dfsIterative(int start, List<List<Integer>> adj, int V) {
    boolean[] visited = new boolean[V];
    List<Integer> order = new ArrayList<>();
    Deque<Integer> stack = new ArrayDeque<>();      // Deque, NOT legacy Stack class

    stack.push(start);
    // NOTE: iterative DFS marks ON POP (not on push) — matches recursive order.
    //       Duplicates may exist on stack; the `if (visited[u]) continue` filter handles them.

    while (!stack.isEmpty()) {
        int u = stack.pop();
        if (visited[u]) {                           // duplicate-on-stack guard
            continue;
        }
        visited[u] = true;
        order.add(u);
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                stack.push(v);
            }
        }
    }
    return order;
}
```

> 🐞 **Where I make mistakes:**
> - **Using legacy `Stack` class** — synchronized + slow. Always `Deque<Integer> stack = new ArrayDeque<>();`.
> - **Forgetting the `if (visited[u]) continue` in iterative DFS** — same vertex gets recorded multiple times.
> - **Marking visited in the driver but reading `visited[start]` in the helper before marking** — usually harmless but breaks subtle invariants (e.g., counting calls).
> - **Forgetting the outer driver loop for disconnected graphs** — if the problem says "count components," wrap the recursive call in `for (int i = 0; i < V; i++)` with `if (!visited[i])`.

**🏷️ Example problems:** LC 547 Number of Provinces, LC 1971 Find if Path Exists, LC 797 All Paths from Source to Target, LC 207 Course Schedule (cycle detection variant).

> Recursion stack visual: `../DeepDive/graphs-fundamentals.md` § "🎨 Visual — DFS Diving Deep".

---

## 🔹 Flavor 7 — Adjacency-List BFS

**KEY STEPS:**

1. **`boolean[] visited` of size V.**
2. **Mark start + offer** (same breath).
3. **While queue non-empty:** poll `u`, examine `adj.get(u)`, for each unvisited neighbor → **mark FIRST, then offer**.

```java
public List<Integer> bfs(int start, List<List<Integer>> adj, int V) {
    boolean[] visited = new boolean[V];
    List<Integer> order = new ArrayList<>();
    Queue<Integer> queue = new ArrayDeque<>();

    // Step 2 — MARK + OFFER in the same breath
    visited[start] = true;
    queue.offer(start);

    // Step 3 — drain queue
    while (!queue.isEmpty()) {
        int u = queue.poll();
        order.add(u);
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                // MARK BEFORE OFFER — this is the line that prevents duplicates
                visited[v] = true;
                queue.offer(v);
            }
        }
    }
    return order;
}
```

> 🐞 **Where I make mistakes:**
> - **Mark-on-poll antipattern** — `int u = queue.poll(); if (visited[u]) continue; visited[u] = true;` works but inflates queue to O(V·E) and breaks distance-tracking BFS. **Always mark at offer time.**
> - **Forgetting the outer driver loop** for disconnected graphs.
> - **Using `LinkedList` for `Queue`** — slower than `ArrayDeque`.

**🏷️ Example problems:** LC 102 (tree BFS but same shape), LC 1971 Find Path Exists, LC 815 Bus Routes, LC 909 Snakes and Ladders, LC 127 Word Ladder.

> Wrong-vs-right mark-timing code: `../DeepDive/graphs-fundamentals.md` § "The BFS-vs-DFS marking rule".

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

**1. Array-literal syntax outside declarations.**

```java
queue.offer({0, 0});             // ❌ compile error
queue.offer(new int[]{0, 0});    // ✅
```

---

**2. `grid[1].length` for column count.**

```java
int n = grid[1].length;          // ❌ NPE on 1-row grids
int n = grid[0].length;          // ✅ (after guarding grid.length > 0)
```

---

**3. Bounds-check ordering (short-circuit matters).**

```java
if (grid[r][c] != '1' || r < 0 || r >= m) { ... }    // ❌ AIOOBE before bounds check fires
if (r < 0 || r >= m || c < 0 || c >= n || grid[r][c] != '1') { ... }   // ✅ bounds FIRST
```

---

**4. `r + dc` typo (the silent killer).**

```java
dfs(r + dr, r + dc, ...);        // ❌ visits wrong cells, sometimes crashes
dfs(r + dr, c + dc, ...);        // ✅ prefixes MUST differ
```

---

**5. Outer loop forgets the LAND check.**

```java
if (!visited[i][j]) { dfs(...); count++; }                  // ❌ counts water as islands
if (grid[i][j] == '1' && !visited[i][j]) { ... }            // ✅ LAND and UNVISITED
```

---

**6. Mark-on-poll instead of mark-on-offer (BFS).**

```java
// ❌ same vertex enqueued from multiple neighbors before its first poll
queue.offer(v);
// ...later in while:
int u = queue.poll();
if (visited[u]) continue;
visited[u] = true;

// ✅ mark IMMEDIATELY when offered — never seen as unvisited again
if (!visited[v]) {
    visited[v] = true;
    queue.offer(v);
}
```

---

**7. Reassigning a primitive parameter and expecting it to bubble up.**

```java
// ❌ int copies per frame — caller's `diameter` stays 0
private int height(TreeNode n, int diameter) { ... diameter = max(diameter, ...); }

// ✅ instance field — every frame writes to same heap slot
private int diameter;
private int height(TreeNode n) { ... this.diameter = max(this.diameter, ...); }
```

---

**8. Class field shadowed by local with the same name.**

```java
private int[][] dr = { ... };                    // ❌ class field named `dr`
for (int[] d : dr) { int dr = d[0]; ... }        //   local `int dr` shadows it — confusing

private static final int[][] DIRS = { ... };     // ✅ class field is `DIRS`
for (int[] d : DIRS) { int dr = d[0]; ... }      //   no clash
```

---

**9. `int[]` vs `int[][]` declaration mismatch.**

```java
int[] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};      // ❌ compile error
int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};    // ✅
```

---

**10. `r` / `c` declared OUTSIDE the while loop in BFS.**

```java
// ❌ r and c declared once, never updated — every neighbor lookup uses (0, 0)
int r = 0, c = 0;
while (!queue.isEmpty()) {
    int[] cell = queue.poll();
    for (int[] d : DIRS) { ... r + d[0] ... }
}

// ✅ EXTRACT inside loop — r, c reflect the current polled cell
while (!queue.isEmpty()) {
    int[] cell = queue.poll();
    int r = cell[0];
    int c = cell[1];
    for (int[] d : DIRS) { int nr = r + d[0]; int nc = c + d[1]; ... }
}
```

---

**11. Reading `queue.size()` inside the inner loop (Tree BFS / distance BFS).**

```java
// ❌ queue grows as we push children — loop never terminates / mixes levels
while (!queue.isEmpty()) {
    for (int i = 0; i < queue.size(); i++) { ... offer children ... }
}

// ✅ SNAPSHOT before the inner loop
while (!queue.isEmpty()) {
    int size = queue.size();
    for (int i = 0; i < size; i++) { ... }
}
```

---

**12. Forgetting to reset instance fields between LC test cases.**

```java
// ❌ LC re-uses the Solution instance; stale state poisons the next test
private int diameter;
public int diameterOfBinaryTree(TreeNode root) { height(root); return diameter; }

// ✅ reset at the top of every public method
public int diameterOfBinaryTree(TreeNode root) {
    diameter = 0;
    height(root);
    return diameter;
}
```

---

## ⚡ Quick Cheat Sheet

| If you need... | Use... | Marking |
| --- | --- | --- |
| **Tree depth / height / count** | Tree DFS bottom-up | none |
| **Tree path with target / running state** | Tree DFS top-down | none |
| **Tree level-by-level output** | Tree BFS | none |
| **Tree shortest distance (rare)** | Tree BFS w/ size snapshot | none |
| **Count islands / components on grid** | Grid DFS + outer driver | in-place OR `visited[][]` |
| **Max area / shape of island** | Grid DFS bottom-up (returns int) | `visited[][]` |
| **Shortest path / minimum minutes on grid** | Grid BFS w/ distance counter | mark-on-enqueue |
| **Multi-source spread (e.g., rotten oranges)** | Grid BFS, seed ALL sources first | mark-on-enqueue |
| **Count components in adjacency list** | Adj-list DFS + outer driver | `boolean[] visited` |
| **Shortest unweighted path between vertices** | Adj-list BFS | mark-on-enqueue |
| **V ≥ 10⁵ or guaranteed-chain shape** | Iterative DFS w/ `Deque` | mark-on-pop + duplicate guard |

### The one-line mental model for each flavor

| Flavor | Memorize this one line |
| --- | --- |
| Tree DFS top-down | *"Carry state DOWN as a parameter. Return void."* |
| Tree DFS bottom-up | *"Trust the children to return their answer. Combine and return up."* |
| Tree BFS | *"`int size = queue.size()` BEFORE the inner loop."* |
| Grid DFS | *"Bounds FIRST, then value, then visited. Mark before recursing."* |
| Grid BFS | *"Mark the moment you offer, never when you poll."* |
| Adj-list DFS | *"Mark on entry. For-loop neighbors with `if (!visited[v])`."* |
| Adj-list BFS | *"Mark-on-offer is a contract, not a suggestion."* |

---

## 🔗 Cross-References

- **Tree fundamentals** — call stack, base cases, two-purpose recursion: `../DeepDive/trees-fundamentals.md`
- **Graph fundamentals** — representations, marking discipline, cycle detection, topo sort: `../DeepDive/graphs-fundamentals.md`
- **Recursion fundamentals** — stack vs heap, primitive vs reference parameters: `../DeepDive/recursion-fundamentals.md`
- **Java pass-by-value semantics** — why `visited[][]` mutation propagates: `../../JavaBackend/DeepDive/java-pass-by-value-semantics.md`
- **ArrayDeque / Queue reference** — `ArrayDeque` over `LinkedList`/`Stack`: `arraydeque-and-queue-reference.md`

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Triggered by Kapil's observation that BFS/DFS "isn't coming naturally in 10 minutes" after the LC 200 + grid-BFS attempts that surfaced 10+ similar-looking-symbol bugs (`r+dc` typo, mark-on-poll, `int[]` vs `int[][]`, scope errors, `gird` typo). Goal: muscle-memory rehearsal file for 2-3×/week revision. Every flavor opens with KEY STEPS, every code block has inline "what + how" comments, every flavor closes with a **🐞 Where I make mistakes** callout tied to the actual bug patterns. |
