# Graph + Tree + DP — Crash Course for Salesforce SMTS R1

> **When:** Night before the interview. **Goal:** Build mental models AND code memory. Each problem has: thinking approach → key code snippet → same-pattern problems with their tweak. If you can explain the approach AND write the snippet from memory — you're ready.

---

## 📅 Time Split

```
Graph:  1 hour     (9 problems, ~7 min each)
Tree:   1 hour     (6 problems, ~10 min each)
DP:     1 hour     (8 problems, ~7 min each)
```

---

# 🧭 GRAPH — 9 Must-Know Problems

---

## G1. LC 200 — Number of Islands ⭐⭐⭐

**Trigger:** "grid", "connected components", "count groups"

**How to think:** Each island = one DFS launch. Outer loop finds unvisited land → launches DFS → DFS marks entire island visited → count++.

**Steps:**
1. Outer loop scans every cell — skip if water or already visited
2. Launch DFS on unvisited land → marks entire island as visited
3. Increment count after each DFS (one launch = one island)
4. DFS base: check bounds + water + visited → return. Otherwise mark visited → recurse 4 directions

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

**Steps:**
1. Scan grid: enqueue ALL rotten oranges at once + count fresh
2. BFS level-by-level — each level = one minute of spreading
3. For each rotten cell, rot fresh neighbors → decrement fresh count → enqueue
4. Return minutes if fresh == 0, else -1 (some unreachable)

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

**Steps:**
1. Build adjacency list + in-degree array from prerequisites (`[a,b]` → edge b→a, "b unlocks a")
2. Enqueue all courses with in-degree 0 (no prerequisites — ready to take)
3. BFS: poll course → count++ → decrement neighbors' in-degree → enqueue if becomes 0
4. Return count == numCourses (all processed = no cycle)

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

**Steps:**
1. Clone root node → put (old → new) in HashMap → enqueue original
2. BFS: poll node → iterate its neighbors
3. If neighbor not in map → clone it, add to map, enqueue original neighbor
4. Wire clone's neighbor list using map lookups: `map.get(curr).neighbors.add(map.get(neighbor))`

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

**Steps:**
1. Init parent array: each node is its own parent. components = n
2. For each edge, find roots of both endpoints (with path compression)
3. If roots differ → union (merge trees) → components--
4. Return components

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

**Steps:**
1. Put wordList in HashSet. Enqueue beginWord, remove from set, level = 1
2. BFS level-by-level: for each word, try changing each position to a–z
3. If candidate == endWord → return level + 1 (found shortest path)
4. If candidate in wordSet → remove (mark visited) → enqueue

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

**Steps:**
1. Create two boolean grids: pacific[][] and atlantic[][]
2. DFS from Pacific borders (top row + left col) — mark all reachable cells going UPHILL
3. DFS from Atlantic borders (bottom row + right col) — same uphill DFS
4. Collect cells marked true in BOTH grids → these reach both oceans

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

**Steps:**
1. Try starting DFS from every cell — memo makes total work O(m×n)
2. DFS: if memo[r][c] != 0 → return cached result (already solved)
3. Explore all 4 neighbors that are STRICTLY greater → recurse → track max across all directions
4. Cache result in memo[r][c] before returning

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

# 🌳 TREE — 6 Must-Know Problems

---

## The Two Directions of Tree DFS

```
TOP-DOWN:  Parent passes info DOWN to children (preorder)
           "I tell my children what they need to know"
           Use when: passing bounds, depth, path info downward

BOTTOM-UP: Children return info UP to parent (postorder)
           "My children tell me what I need to know"
           Use when: computing height, size, diameter, max path
```

**Pick wrong and you'll fight the recursion. Pick right and it writes itself.**

---

## T1. LC 104 — Maximum Depth of Binary Tree ⭐⭐⭐ (Warm-up, most basic)

**Trigger:** "max depth", "height of tree"

**Family:** Bottom-Up DFS (children report height up)

**Steps:**
1. Base case: null → return 0
2. Recurse left and right — each child reports its depth
3. Return 1 + max(left, right) — I'm one level above my deeper child

**Key snippet:**

```java
int maxDepth(TreeNode root) {
    if (root == null) {
        return 0;  // base case: empty tree has depth 0
    }
    int left = maxDepth(root.left);    // ask left child: "how deep are you?"
    int right = maxDepth(root.right);  // ask right child: "how deep are you?"
    return 1 + Math.max(left, right);  // I'm 1 level above the deeper child
}
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 111 — Min Depth** | `1 + Math.min(left, right)` BUT if one child is null, take the OTHER (a null branch isn't a leaf) |
| **LC 110 — Balanced Binary Tree** | Return -1 if unbalanced. `if (abs(left - right) > 1) return -1`. Propagate -1 upward |
| **LC 226 — Invert Binary Tree** | Bottom-up: swap `root.left` and `root.right` after recursing on both children |

---

## T2. LC 543 — Diameter of Binary Tree ⭐⭐⭐ (Salesforce favorite)

**Trigger:** "diameter", "longest path between any two nodes"

**Family:** Bottom-Up DFS with global variable

**How to think:**

```
Diameter = longest path between any two nodes (might NOT go through root)
At each node: diameter through me = leftHeight + rightHeight
But I return my HEIGHT to my parent (not diameter)

Key distinction:
   What I RETURN (to parent): 1 + max(left, right)     → my height
   What I UPDATE (globally):  max(diameter, left+right)  → diameter through me
```

**Steps:**
1. Base case: null → return 0
2. Recurse left and right to get heights
3. UPDATE global diameter = max(diameter, left + right) — path through this node
4. RETURN height = 1 + max(left, right) — for parent's calculation

**Key snippet:**

```java
int diameter = 0;

int height(TreeNode node) {
    if (node == null) {
        return 0;
    }
    int left = height(node.left);
    int right = height(node.right);

    // UPDATE: diameter through this node = left height + right height
    diameter = Math.max(diameter, left + right);

    // RETURN: my height to my parent
    return 1 + Math.max(left, right);
}
```

**Same pattern (return one thing, update another):**

| Problem | Tweak |
| --- | --- |
| **LC 124 — Binary Tree Max Path Sum** | Same dual-purpose. UPDATE: `max(pathSum, left + right + node.val)`. RETURN: `node.val + max(left, right)`. Clamp negatives to 0 |

**Trap:** Don't confuse what you RETURN vs what you UPDATE. Return = height (for parent). Update = diameter (global answer).

---

## T3. LC 102 — Binary Tree Level Order Traversal ⭐⭐⭐

**Trigger:** "level order", "BFS", "zigzag", "right side view"

**Family:** BFS with level snapshot

**Steps:**
1. Enqueue root
2. Each level: snapshot size → poll exactly `size` nodes into level list
3. Enqueue left and right children of each polled node
4. Add level list to result

**Key snippet:**

```java
List<List<Integer>> result = new ArrayList<>();
Queue<TreeNode> queue = new ArrayDeque<>();
queue.offer(root);

while (!queue.isEmpty()) {
    int size = queue.size();  // SNAPSHOT level size before processing
    List<Integer> level = new ArrayList<>();

    for (int i = 0; i < size; i++) {
        TreeNode node = queue.poll();
        level.add(node.val);
        if (node.left != null) {
            queue.offer(node.left);
        }
        if (node.right != null) {
            queue.offer(node.right);
        }
    }
    result.add(level);
}
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 107 — Level Order Bottom-Up** | Same BFS, then `Collections.reverse(result)` at the end |
| **LC 103 — Zigzag Level Order** | Same BFS. On odd levels: `Collections.reverse(level)` before adding |
| **LC 199 — Right Side View** | Same BFS. Only add `level.get(size - 1)` (last element of each level) |
| **LC 637 — Average of Levels** | Same BFS. Compute average of each level list |

**Trap:** `int size = queue.size()` MUST be captured BEFORE the inner loop. The queue grows during the loop — without the snapshot, you'll bleed into the next level.

---

## T4. LC 98 — Validate Binary Search Tree ⭐⭐⭐

**Trigger:** "valid BST", "is this a BST"

**Family:** Top-Down DFS (pass bounds down)

**How to think:**

```
A BST rule: every node must be within a range (min, max)
Root: range is (-∞, +∞)
Left child of node with val 10: range becomes (-∞, 10)
Right child of node with val 10: range becomes (10, +∞)

Pass bounds DOWN to children. If any node violates → false.
```

**Steps:**
1. Start with bounds (Long.MIN_VALUE, Long.MAX_VALUE)
2. If node.val is outside (min, max) → return false
3. Recurse left with tightened max = node.val
4. Recurse right with tightened min = node.val

**Key snippet:**

```java
boolean isValid(TreeNode node, long min, long max) {
    if (node == null) {
        return true;  // empty tree is valid
    }
    // Current node must be within (min, max) — EXCLUSIVE
    if (node.val <= min || node.val >= max) {
        return false;
    }
    // Left child: max tightens to node.val
    // Right child: min tightens to node.val
    return isValid(node.left, min, node.val)
        && isValid(node.right, node.val, max);
}

// Call with: isValid(root, Long.MIN_VALUE, Long.MAX_VALUE)
```

**Trap:** Use `long` for min/max, not `int`. Node values can be `Integer.MIN_VALUE` or `Integer.MAX_VALUE` — using `int` boundaries would fail on those edge cases.

**Alternative:** Inorder traversal produces a sorted sequence for a valid BST. Just check if inorder is strictly increasing.

---

## T5. LC 236 — Lowest Common Ancestor ⭐⭐⭐ (Classic interview)

**Trigger:** "lowest common ancestor", "LCA", "first shared parent"

**Family:** Bottom-Up DFS (children tell parent "I found p or q")

**How to think:**

```
Three cases:
1. p and q are in different subtrees → current node IS the LCA
2. p is ancestor of q (or vice versa) → the higher one is LCA
3. Current node is null or a leaf → return null

At each node, ask left and right: "did you find p or q?"
- Both found something → I'm the LCA
- Only left found → LCA is in left subtree
- Only right found → LCA is in right subtree
```

**Steps:**
1. Base case: null or node == p or q → return node (found one, report it up)
2. Recurse left and right subtrees
3. Both non-null → I'm the split point → return me as LCA
4. One non-null → pass it up (LCA is deeper in that subtree)

**Key snippet:**

```java
TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    // Base: null or found p or q
    if (root == null || root == p || root == q) {
        return root;
    }

    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);

    // Both sides found something → I'm the split point → I'm the LCA
    if (left != null && right != null) {
        return root;
    }
    // Only one side found → pass it up
    return left != null ? left : right;
}
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 235 — LCA of BST** | Simpler: use BST property. If both < root → go left. Both > root → go right. Split → root is LCA |

---

## T6. LC 230 — Kth Smallest Element in BST ⭐⭐

**Trigger:** "kth smallest", "BST + sorted order"

**Family:** BST Inorder (left → root → right = sorted order)

**How to think:** Inorder traversal of a BST visits nodes in ascending order. Just count until you reach k.

**Steps:**
1. Inorder: recurse left first (smaller values)
2. At current node: count++ → if count == k, record result and return
3. Recurse right (larger values)

**Key snippet:**

```java
int count = 0, result = 0;

void inorder(TreeNode node, int k) {
    if (node == null) {
        return;
    }
    inorder(node.left, k);       // visit left (smaller values)

    count++;
    if (count == k) {
        result = node.val;        // this is the kth smallest!
        return;
    }

    inorder(node.right, k);      // visit right (larger values)
}
```

**Same pattern:** LC 94 (Inorder Traversal) — same traversal, just collect all values. LC 530 (Min Diff in BST) — inorder gives sorted order → min diff is between consecutive elements.

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

**Steps:**
1. Base cases: dp[0] = 1, dp[1] = 1 (one way to stand, one way to reach step 1)
2. For each step i: dp[i] = dp[i-1] + dp[i-2] (arrive from 1 or 2 steps back)
3. Optimize space: keep only prev1 and prev2 instead of full array

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

**Steps:**
1. For each house: choose max(skip it, rob it)
2. Skip = dp[i-1] (best without this house). Rob = dp[i-2] + nums[i] (skip adjacent + take this)
3. Optimize space: two variables prev1, prev2

**🧠 See the recursion — nums = [1, 2, 3, 1]:**

```
rob(i) = max(SKIP → rob(i+1),  ROB → nums[i] + rob(i+2))

rob(0): "should I rob house 0 (val=1)?"
  SKIP → rob(1): "should I rob house 1 (val=2)?"
    SKIP → rob(2): "should I rob house 2 (val=3)?"
      SKIP → rob(3) = max(0, 1+0) = 1
      ROB  → 3 + rob(4) = 3 + 0 = 3
    rob(2) = max(1, 3) = 3
    ROB  → 2 + rob(3) = 2 + 1 = 3
  rob(1) = max(3, 3) = 3
  ROB  → 1 + rob(2) = 1 + 3 = 4
rob(0) = max(3, 4) = 4 ✓  → Rob house 0 + house 2 (1+3=4)

Bottom-up DP does this SAME logic, just reversed:
  dp[3]=1  dp[2]=3  dp[1]=3  dp[0]=4
  (or with two variables: prev2 and prev1)
```

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

**Steps:**
1. Init dp with MAX_VALUE (impossible until proven). dp[0] = 0 (0 coins for amount 0)
2. For each amount i, try every coin
3. If coin ≤ i AND remainder is solvable → dp[i] = min(dp[i], dp[i-coin] + 1)
4. Return dp[amount] (or -1 if still MAX_VALUE)

**🧠 See the recursion — coins = [1, 3], amount = 4:**

```
mc(amount) = min over each coin: 1 + mc(amount - coin)

mc(4): "min coins to make 4?"
  use coin 1 → 1 + mc(3): "min coins to make 3?"
    use coin 1 → 1 + mc(2)
      use coin 1 → 1 + mc(1)
        use coin 1 → 1 + mc(0) = 1 + 0 = 1
      mc(1) = 1
    1 + mc(2) = 1 + 2 = 3
    use coin 3 → 1 + mc(0) = 1 + 0 = 1     ← coin 3 directly!
  mc(3) = min(3, 1) = 1
  use coin 3 → 1 + mc(1) = 1 + 1 = 2
mc(4) = min(1+1, 1+1) = 2 ✓  → coin 3 + coin 1

Notice: mc(1) computed TWICE without memo → this is why we memoize!

Bottom-up DP fills the same values left-to-right:
  dp[0]=0  dp[1]=1  dp[2]=2  dp[3]=1  dp[4]=2
```

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

**Steps:**
1. First row and first col = 1 (only one way to reach them — straight right or straight down)
2. For each cell: dp[i][j] = dp[i-1][j] + dp[i][j-1] (paths from above + paths from left)
3. Return dp[m-1][n-1]

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

**Steps:**
1. Create (m+1) × (n+1) table — extra row/col for empty-string base case
2. If characters match → dp[i][j] = diagonal + 1 (extend the subsequence)
3. If no match → dp[i][j] = max(up, left) (skip one char from either string)
4. Return dp[m][n]

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

**🔄 Follow-up — "Can you optimize space to O(n)?"** Yes — you only need the previous row. Use a 1D array + a `prev` variable for the diagonal.

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 72 — Edit Distance** | Match → diagonal (0 cost). No match → `1 + min(diagonal, up, left)` for replace/delete/insert |
| **LC 583 — Delete Operations** | Answer = `m + n - 2 * LCS`. Delete everything that's NOT in the LCS |

---

## D6. LC 300 — Longest Increasing Subsequence ⭐⭐⭐

**Trigger:** "longest increasing", "subsequence"

**Family:** Linear DP (O(n²))

**Steps:**
1. Init dp with 1 — every element is a LIS of length 1 by itself
2. For each i, scan all j < i: if nums[j] < nums[i] → dp[i] = max(dp[i], dp[j] + 1)
3. Answer = max across ALL dp values (NOT just dp[n-1] — LIS might not end at last element)

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

**🔄 Follow-up — "Can you do O(n log n)?"**

Maintain a `tails` array where `tails[i]` = smallest tail of any increasing subsequence of length `i+1`. Array stays sorted → binary search works.

```java
List<Integer> tails = new ArrayList<>();
for (int num : nums) {
    int pos = Collections.binarySearch(tails, num);
    if (pos < 0) {
        pos = -(pos + 1);  // insertion point
    }
    if (pos == tails.size()) {
        tails.add(num);       // bigger than all → extend LIS
    } else {
        tails.set(pos, num);  // replace → keep tails small for future
    }
}
return tails.size();
```

`tails` is NOT the actual LIS — its **length** equals the LIS length.

---

## D7. LC 139 — Word Break ⭐⭐

**Trigger:** "can string be segmented", "dictionary words"

**Family:** Linear DP + Set

**Steps:**
1. dp[0] = true (empty string is valid). Put dictionary in HashSet
2. For each position i, try every split point j < i
3. If dp[j] is true AND s[j..i] is a dictionary word → dp[i] = true, break
4. Return dp[n]

**🧠 See the recursion — s = "leetcode", dict = ["leet", "code"]:**

```
dp[i] = "can s[0..i] be segmented into dictionary words?"

dp[0] = true  (empty string — base case)

dp[1]: try s[0..1]="l" → not in dict. dp[1] = false
dp[2]: try s[0..2]="le" → not in dict. dp[2] = false
dp[3]: try s[0..3]="lee" → not in dict. dp[3] = false

dp[4]: try split at j=0: dp[0]=true && s[0..4]="leet" in dict? YES ✓
       dp[4] = true   ← "leet" is a valid segmentation!

dp[5]: s[0..5]="leetc" — no valid split. dp[5] = false
dp[6]: s[0..6]="leetco" — no valid split. dp[6] = false
dp[7]: s[0..7]="leetcod" — no valid split. dp[7] = false

dp[8]: try split at j=4: dp[4]=true && s[4..8]="code" in dict? YES ✓
       dp[8] = true   ← "leet"+"code" = valid!

Return dp[8] = true ✓

KEY INSIGHT: dp[j]=true means "everything BEFORE j is valid."
             So we only need to check if s[j..i] is ONE dictionary word.
```

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

**🔄 Follow-up — "Return ALL valid segmentations?" (LC 140 Word Break II):** Same DP to check feasibility, then **backtrack** from dp[n] collecting all valid split paths. Return list of sentence strings.

---

## D8. LC 416 — Partition Equal Subset Sum ⭐⭐

**Trigger:** "partition into two equal halves", "subset sum"

**Family:** 0/1 Knapsack

**Steps:**
1. If total sum is odd → impossible. Target = sum / 2
2. dp[0] = true. For each num, iterate RIGHT-TO-LEFT (so each num used at most once)
3. dp[j] = dp[j] || dp[j - num] — "can I make j without this num OR by using it?"
4. Return dp[target]

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
| Full graph templates + all problems | `DSA/Interview/Playbooks/graphs.md` |
| Full DP templates + all problems | `DSA/Interview/Playbooks/dp.md` |
| Pre-submit bug checklist | `DSA/Interview/QuickRef/common-bugs-checklist.md` |
| Edge direction for topo sort | `DSA/Interview/Playbooks/graphs.md` — Pattern 2 |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** 3-hour crash course — 8 Graph + 8 DP problems. |
| June 2026 | **Enhanced.** Added LC 329 (Grid DFS + Memo — asked at Salesforce). Added key code snippets for every problem. Added "same pattern, small tweak" tables showing related problems. Total: 9 Graph + 8 DP. |
