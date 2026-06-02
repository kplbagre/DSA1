# Graphs — Interview Playbook

> **Read this file when:** You have an interview coming up and need to recognize graph/grid patterns fast. Companion to `DSA/DeepDive/graphs-fundamentals.md` — this file focuses on pattern recognition, not teaching graph theory.

---

## 🎯 Why You're Reading This

Graph problems look intimidating but interview-level graphs fall into just 5 patterns. The hardest part isn't the algorithm — it's recognizing "this IS a graph problem" when the problem says "grid" or "courses" or "network" instead of "graph." This file builds that instinct.

---

## 🧠 The Mental Model — Is This Even a Graph Problem?

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `List<List<Integer>> adj = new ArrayList<>()` | Adjacency list representation | Patterns 2, 3 |
| `adj.get(node).add(neighbor)` | Add edge to adjacency list | Patterns 2, 3 |
| `Queue<int[]> queue = new ArrayDeque<>()` | BFS queue (ArrayDeque faster than LinkedList) | Patterns 1, 2 |
| `queue.offer(item)` / `queue.poll()` | BFS add/remove — O(1) | Patterns 1, 2 |
| `boolean[] visited` / `boolean[][] visited` | Track visited nodes/cells | All patterns |
| `int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}}` | 4-directional movement on grids | Pattern 1 |
| `int[] parent, int[] rank` | Union-Find data structure arrays | Pattern 4 |
| `PriorityQueue<int[]>` with `(a,b) -> a[1]-b[1]` | Min-heap by distance for Dijkstra | Pattern 5 |

> **Full reference:** `../Reference/dsa-collections-notes.md`, `../Reference/arraydeque-and-queue-reference.md`

### 🔄 Lambda & Shorthand Explanations with Fallbacks

**1. `new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]))`** — PQ comparator for Dijkstra

**What it means in English:** "Create a min-heap of `int[]` pairs. When deciding which pair is 'smaller,' compare the element at index 1 (the distance value). The pair with the smaller distance gets polled first."

**Why `Integer.compare` instead of `a[1] - b[1]`:** Subtraction can overflow when values are near `Integer.MAX_VALUE` (which `dist[]` starts at). `Integer.compare` is always safe — it returns -1, 0, or 1 without arithmetic.

🔄 **Fallback — simpler comparator (same behavior, more readable):**

```java
PriorityQueue<int[]> pq = new PriorityQueue<>(new Comparator<int[]>() {
    @Override
    public int compare(int[] a, int[] b) {
        // Compare by distance (index 1) — smaller distance = higher priority
        return Integer.compare(a[1], b[1]);
    }
});
```

---

**Trigger words that scream "GRAPH":**
- Grid of 0s and 1s → graph where cells are nodes, adjacent cells are edges
- "Network" / "connections" / "prerequisites" → explicit graph
- "Reach from A to B" → BFS/DFS
- "Order of operations with dependencies" → Topological Sort
- "Connected components" / "groups" → Union-Find or DFS

```
Graph problem
│
├── "Grid of cells (0s/1s, land/water)"
│   ├── "Count islands / connected regions"  → DFS/BFS flood fill (Pattern 1)
│   ├── "Shortest path in grid"              → BFS (Pattern 1 variant)
│   └── "Spread from multiple sources"       → Multi-source BFS (Pattern 1 variant)
│
├── "Course schedule / task ordering"
│   └── "Can I finish all courses?"          → Topological Sort (Pattern 2)
│
├── "Clone / copy a graph"
│   └── "Deep copy a graph node by node"     → BFS/DFS + HashMap (Pattern 3)
│
├── "Connected components / union"
│   └── "Count groups / redundant edge"      → Union-Find (Pattern 4)
│
└── "Shortest path with weights"
    └── "Network delay / cheapest flights"   → Dijkstra / BFS (Pattern 5)
```

---

## 🧭 Pattern 1: Grid BFS/DFS (Flood Fill / Islands) ⭐

**Recognition cues — reach for this when:**
- "Number of islands" (count connected 1-regions in a grid)
- "Flood fill" (change color of connected region)
- "Rotting oranges" (spread from multiple sources)
- "Shortest path in binary matrix"

**The template — DFS on grid:**

**Steps in plain English:**

1. **Walk every cell** — when you find an unvisited "island" cell, start DFS/BFS from it.
2. **DFS/BFS marks all connected cells** as visited (sink the island).
3. **Count** how many times you started a new DFS/BFS.

```java
public int numIslands(char[][] grid) {
    int count = 0;
    int m = grid.length;
    int n = grid[0].length;

    for (int r = 0; r < m; r++) {
        for (int c = 0; c < n; c++) {
            if (grid[r][c] == '1') {
                count++;
                dfs(grid, r, c, m, n);
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c, int m, int n) {
    if (r < 0 || r >= m || c < 0 || c >= n || grid[r][c] != '1') {
        return;
    }
    grid[r][c] = '0';
    dfs(grid, r + 1, c, m, n);
    dfs(grid, r - 1, c, m, n);
    dfs(grid, r, c + 1, m, n);
    dfs(grid, r, c - 1, m, n);
}
```

**Direction array (for BFS):**

```java
int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
```

**Multi-source BFS (Rotting Oranges — LC 994):**

Add ALL rotten oranges to the queue at the start. BFS level = one minute of spreading.

```java
public int orangesRotting(int[][] grid) {
    Queue<int[]> queue = new ArrayDeque<>();
    int fresh = 0;

    // Enqueue all initially rotten oranges
    for (int r = 0; r < grid.length; r++) {
        for (int c = 0; c < grid[0].length; c++) {
            if (grid[r][c] == 2) {
                queue.offer(new int[]{r, c});
            } else if (grid[r][c] == 1) {
                fresh++;
            }
        }
    }

    int minutes = 0;
    int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    while (!queue.isEmpty() && fresh > 0) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cell = queue.poll();
            for (int[] d : dirs) {
                int nr = cell[0] + d[0];
                int nc = cell[1] + d[1];
                if (nr >= 0 && nr < grid.length && nc >= 0 && nc < grid[0].length
                    && grid[nr][nc] == 1) {
                    grid[nr][nc] = 2;
                    fresh--;
                    queue.offer(new int[]{nr, nc});
                }
            }
        }
        minutes++;
    }
    return fresh == 0 ? minutes : -1;
}
```

---

## 🧭 Pattern 2: Topological Sort ⭐

**Recognition cues — reach for this when:**
- "Can I finish all courses with prerequisites?"
- "Order tasks with dependencies"
- "Alien dictionary" (determine character ordering)
- Any DAG (directed acyclic graph) ordering problem

### ⚠️ Edge Direction — The #1 Topo Sort Confusion

```
prerequisites[i] = [a, b] means "b must come before a"

Think "UNLOCKS", not "depends on":
   b UNLOCKS a  →  edge goes  b → a

   [1, 0] → "0 before 1" → edge 0 → 1
   [3, 1] → "1 before 3" → edge 1 → 3

Code: adj.get(pre[1]).add(pre[0]);   // pre[1] unlocks pre[0]
      inDegree[pre[0]]++;            // pre[0] gains a prerequisite

Why this direction? Topo sort starts with in-degree 0 (no prereqs).
If edges pointed backwards, in-degree would be inverted — you'd
start with the LAST courses instead of the FIRST ones.
```

**The template — Kahn's Algorithm (BFS-based):**

**Steps in plain English:**

1. **Build adjacency list + in-degree array** — edge goes from prerequisite TO dependent course (prereq "unlocks" the next course).
2. **Enqueue** all nodes with in-degree 0 (no prerequisites — these are starting points).
3. **BFS** — poll node, add to result, decrement neighbors' in-degrees. If a neighbor hits 0, enqueue it.
4. **Check** — if result has all nodes, ordering exists. If not, there's a cycle.

### Building the adjacency list from prerequisites

The `prerequisites` array IS your edge list — just convert it:

```
Input: numCourses = 4, prerequisites = [[1,0], [2,0], [3,1], [3,2]]

After init:  adj = [ [], [], [], [] ]
                     0   1   2   3

Process [1,0]: adj.get(0).add(1)  →  adj = [ [1], [],  [],  [] ]
Process [2,0]: adj.get(0).add(2)  →  adj = [ [1,2], [], [], [] ]
Process [3,1]: adj.get(1).add(3)  →  adj = [ [1,2], [3], [], [] ]
Process [3,2]: adj.get(2).add(3)  →  adj = [ [1,2], [3], [3], [] ]

Reading: "0 unlocks 1, 2"  "1 unlocks 3"  "2 unlocks 3"  "3 unlocks nothing"

inDegree after same loop: [0, 1, 1, 2]
   Course 0: 0 prereqs → starts in queue
   Course 3: 2 prereqs → processed last
```

```java
public boolean canFinish(int numCourses, int[][] prerequisites) {
    // Step 1 — build adjacency list + in-degree in ONE loop
    // Edge direction: pre[1] → pre[0] ("pre[1] unlocks pre[0]")
    List<List<Integer>> adj = new ArrayList<>();
    int[] inDegree = new int[numCourses];
    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());   // one empty list per course
    }
    for (int[] pre : prerequisites) {
        // pre = [course, prereq] → prereq UNLOCKS course
        adj.get(pre[1]).add(pre[0]);  // prereq's list gains the dependent course
        inDegree[pre[0]]++;           // dependent course gains one prerequisite
    }

    // Step 2 — enqueue zero in-degree nodes
    Queue<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < numCourses; i++) {
        if (inDegree[i] == 0) {
            queue.offer(i);
        }
    }

    // Step 3 — BFS
    int count = 0;
    while (!queue.isEmpty()) {
        int course = queue.poll();
        count++;
        for (int neighbor : adj.get(course)) {
            inDegree[neighbor]--;
            if (inDegree[neighbor] == 0) {
                queue.offer(neighbor);
            }
        }
    }

    // Step 4 — check
    return count == numCourses;
}
```

---

## 🧭 Pattern 3: Graph Traversal + Cloning

**Recognition cues — reach for this when:**
- "Clone graph" (deep copy)
- "Copy list with random pointer"
- Any "traverse and build a copy" problem

**Steps in plain English:**

1. **HashMap** — `original node → cloned node`.
2. **BFS/DFS** — visit each node, create clone, map neighbors.

```java
public Node cloneGraph(Node node) {
    if (node == null) {
        return null;
    }

    Map<Node, Node> map = new HashMap<>();
    Queue<Node> queue = new ArrayDeque<>();
    map.put(node, new Node(node.val));
    queue.offer(node);

    while (!queue.isEmpty()) {
        Node curr = queue.poll();
        for (Node neighbor : curr.neighbors) {
            if (!map.containsKey(neighbor)) {
                map.put(neighbor, new Node(neighbor.val));
                queue.offer(neighbor);
            }
            map.get(curr).neighbors.add(map.get(neighbor));
        }
    }
    return map.get(node);
}
```

---

## 🧭 Pattern 4: Union-Find (Disjoint Set Union)

**Recognition cues — reach for this when:**
- "Number of connected components"
- "Redundant connection" (find the edge that creates a cycle)
- "Accounts merge" (group by shared emails)
- Any problem about grouping/merging dynamically

**The template:**

```java
class UnionFind {
    int[] parent;
    int[] rank;
    int components;

    UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        components = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
    }

    int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);
        }
        return parent[x];
    }

    boolean union(int x, int y) {
        int px = find(x);
        int py = find(y);
        if (px == py) {
            return false;
        }
        if (rank[px] < rank[py]) {
            parent[px] = py;
        } else if (rank[px] > rank[py]) {
            parent[py] = px;
        } else {
            parent[py] = px;
            rank[px]++;
        }
        components--;
        return true;
    }
}
```

---

## 🧭 Pattern 5: Shortest Path (Dijkstra)

**Recognition cues — reach for this when:**
- "Network delay time" (shortest time to reach all nodes)
- "Cheapest flights within K stops"
- Weighted graph + shortest path

```java
public int networkDelayTime(int[][] times, int n, int k) {
    // Build adjacency list: node → [(neighbor, weight)]
    List<List<int[]>> adj = new ArrayList<>();
    for (int i = 0; i <= n; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] t : times) {
        adj.get(t[0]).add(new int[]{t[1], t[2]});
    }

    // Dijkstra
    int[] dist = new int[n + 1];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[k] = 0;
    // Lambda: "compare int[] pairs by index 1 (distance) — smallest distance polled first"
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
    // 🔄 Fallback — anonymous Comparator:
    //   new PriorityQueue<>(new Comparator<int[]>() {
    //       public int compare(int[] a, int[] b) { return Integer.compare(a[1], b[1]); }
    //   });
    // ⚠️ Do NOT use a[1] - b[1] — dist[] starts at Integer.MAX_VALUE, subtraction overflows
    pq.offer(new int[]{k, 0});

    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int node = curr[0];
        int d = curr[1];
        if (d > dist[node]) {
            continue;
        }
        for (int[] edge : adj.get(node)) {
            int newDist = d + edge[1];
            if (newDist < dist[edge[0]]) {
                dist[edge[0]] = newDist;
                pq.offer(new int[]{edge[0], newDist});
            }
        }
    }

    int maxDist = 0;
    for (int i = 1; i <= n; i++) {
        maxDist = Math.max(maxDist, dist[i]);
    }
    return maxDist == Integer.MAX_VALUE ? -1 : maxDist;
}
```

---

## 🔬 Canonical Problem — LC 200: Number of Islands

> **Problem:** Given an `m × n` 2D binary grid where `'1'` represents land and `'0'` represents water, count the number of islands (connected land regions, connected horizontally/vertically).

### Step 1 — Read and identify triggers

"Grid of 1s and 0s, count connected regions. This is **Pattern 1: Grid DFS/BFS (Flood Fill)**."

### Step 2 — Choose approach

"I'll walk every cell. When I find a `'1'`, I increment the count and DFS to sink the entire island (mark all connected `'1'`s as `'0'`). This way each island is counted exactly once."

### Step 3 — Code

```java
public int numIslands(char[][] grid) {
    int count = 0;
    for (int r = 0; r < grid.length; r++) {
        for (int c = 0; c < grid[0].length; c++) {
            if (grid[r][c] == '1') {
                count++;
                dfs(grid, r, c);
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c) {
    if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] != '1') {
        return;
    }
    grid[r][c] = '0';
    dfs(grid, r + 1, c);
    dfs(grid, r - 1, c);
    dfs(grid, r, c + 1);
    dfs(grid, r, c - 1);
}
```

### Complexity

- **Time:** O(m × n) — each cell visited once
- **Space:** O(m × n) — worst case recursion depth for fully-land grid

---

## ⚡ Problem Bank — Expanded

---

### LC 200: Number of Islands

> **Problem:** Grid of `'1'` (land) and `'0'` (water). Count connected land regions (horizontally/vertically adjacent).

> **Approach:** Walk every cell. On finding `'1'`, count++ and DFS/BFS to sink the entire island.

```java
if (grid[r][c] == '1') {
    // Found a new island — count it, then sink all its connected land
    count++;
    dfs(grid, r, c);
}
```

---

### LC 733: Flood Fill

> **Problem:** Given an image (grid of ints), a starting pixel `(sr, sc)`, and a new color, change the color of the starting pixel and all connected pixels with the same original color.

> **Approach:** DFS from `(sr, sc)`. Only visit cells matching the original color. Change to new color.

```java
private void dfs(int[][] image, int r, int c, int oldColor, int newColor) {
    // Bounds check + only visit cells matching the original color
    if (r < 0 || r >= image.length || c < 0 || c >= image[0].length
        || image[r][c] != oldColor) return;
    // Paint this cell — also serves as "visited" marker
    image[r][c] = newColor;
    // recurse 4 directions
}
```

---

### LC 994: Rotting Oranges

> **Problem:** Grid with 0 (empty), 1 (fresh orange), 2 (rotten). Each minute, fresh oranges adjacent to rotten ones become rotten. Return minimum minutes until no fresh orange remains, or -1 if impossible.

> **Approach:** Multi-source BFS — enqueue ALL rotten oranges at start. Each BFS level = one minute. Track fresh count.

```java
// See Pattern 1 multi-source BFS template above
while (!queue.isEmpty() && fresh > 0) {
    // process one level = one minute
    minutes++;
}
return fresh == 0 ? minutes : -1;
```

---

### LC 207: Course Schedule

> **Problem:** There are `n` courses labeled `0..n-1`. Prerequisites given as pairs `[a, b]` meaning "to take a, you must first take b." Determine if you can finish all courses (no circular dependency).

> **Approach:** Topological sort (Kahn's). Build in-degree array. If BFS processes all nodes → no cycle → possible.

```java
// See Pattern 2 template above
return count == numCourses;
```

---

### LC 210: Course Schedule II

> **Problem:** Same as Course Schedule, but return a valid ordering of courses (not just true/false).

> **Approach:** Same Kahn's algorithm, but collect the BFS order into a result array.

```java
int[] order = new int[numCourses];
// Inside BFS: order[idx++] = course — record topological order as we process
return idx == numCourses ? order : new int[]{};
// If idx < numCourses, a cycle exists — return empty array
```

---

### LC 133: Clone Graph

> **Problem:** Given a reference to a node in a connected undirected graph, return a deep copy. Each node has a value and a list of neighbors.

> **Approach:** BFS + HashMap mapping original → clone. For each neighbor, clone if not seen, then link.

```java
// Seed the map with the first node's clone before starting BFS
map.put(node, new Node(node.val));
// BFS: for each neighbor, clone if unseen, then wire clone's neighbor list
```

---

### LC 323: Number of Connected Components

> **Problem:** Given `n` nodes and edges in an undirected graph, return the number of connected components.

> **Approach:** Union-Find. Start with `n` components. Each successful `union()` decreases count by 1.

```java
// Start with n components (each node is its own group)
UnionFind uf = new UnionFind(n);
for (int[] edge : edges) {
    // Each successful union merges two components into one
    uf.union(edge[0], edge[1]);
}
return uf.components;
```

---

### LC 684: Redundant Connection

> **Problem:** An undirected graph with `n` nodes (tree + one extra edge). Find the edge that, if removed, makes the graph a tree.

> **Approach:** Union-Find. Process edges one by one. The first edge where `union()` returns false (both nodes already connected) is the redundant one.

```java
for (int[] edge : edges) {
    // union() returns false when both nodes are already connected — that edge is redundant
    if (!uf.union(edge[0], edge[1])) {
        return edge;
    }
}
```

---

### LC 743: Network Delay Time

> **Problem:** Given a directed weighted graph of `n` nodes, send a signal from node `k`. Return the time it takes for ALL nodes to receive the signal. Return -1 if impossible.

> **Approach:** Dijkstra's algorithm from source `k`. Answer = max distance across all nodes.

```java
// See Pattern 5 template above
return maxDist == Integer.MAX_VALUE ? -1 : maxDist;
```

---

### LC 417: Pacific Atlantic Water Flow

> **Problem:** Grid of heights. Water can flow to adjacent cells with equal or lower height. Pacific ocean touches top and left edges, Atlantic touches bottom and right edges. Find cells that can flow to BOTH oceans.

> **Approach:** Reverse BFS/DFS — start from ocean edges and flow UPHILL. Cells reachable from both oceans are the answer.

```java
// DFS from all Pacific edge cells → mark reachable
// DFS from all Atlantic edge cells → mark reachable
// Intersection of both sets = answer
```

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty grid** — return 0 islands
- **Grid with no land** — all water → 0
- **Single cell** — `[['1']]` → 1 island
- **Disconnected graph** — make sure your traversal handles all components
- **Self-loops / parallel edges** — Union-Find handles naturally

### Follow-up questions:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| Number of Islands | "What if grid is too large for recursion?" | Use BFS (iterative) to avoid stack overflow |
| Number of Islands | "Count using Union-Find?" | Yes — union adjacent `'1'` cells, count components |
| Course Schedule | "Return one valid ordering?" | LC 210 — same algorithm, collect order array |
| Rotting Oranges | "What if grid has no rotten oranges?" | If fresh > 0 → return -1 (impossible) |
| Dijkstra | "What about negative weights?" | Dijkstra doesn't work with negatives — need Bellman-Ford |

### Graph traversal traps:

- **Forgetting visited check** — infinite loop on cycles. Mark visited BEFORE enqueueing (BFS) or at entry (DFS)
- **DFS on grid mutates the grid** — if you can't modify the input, use a separate `boolean[][] visited`
- **Topological sort on undirected graph** — doesn't apply. Topo sort is for DAGs only
- **Direction array off-by-one** — use `{{-1,0}, {1,0}, {0,-1}, {0,1}}` consistently

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

1. "Count connected regions of 1s in a grid" → ___
2. "Can I complete all courses with prerequisites?" → ___
3. "Deep copy a graph" → ___
4. "Find redundant edge in a tree + one extra edge" → ___
5. "Shortest time for signal to reach all nodes" → ___
6. "Fresh oranges rotting from adjacent rotten ones" → ___

**Answers:** 1. Grid DFS/BFS, 2. Topological Sort, 3. BFS + HashMap clone, 4. Union-Find, 5. Dijkstra, 6. Multi-source BFS

**Part 2 — Write the Template (3 minutes)**

From memory, write the DFS function for Number of Islands. Include: bounds check, visited check (sink to '0'), 4-directional recursion.

**Part 3 — The Multi-Source Trick (3 minutes)**

For Rotting Oranges, explain in one sentence: why do you add ALL rotten oranges to the queue at the START instead of BFS from each one separately?

**Answer:** Adding all rotten sources at once means they all spread simultaneously (like real rotting). BFS from each separately would process sequentially and give wrong timing.

**Scoring:** All correct = ready. Missed multi-source BFS = re-read Pattern 1.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Graphs deep dive (adjacency list, BFS/DFS theory) | `DSA/DeepDive/graphs-fundamentals.md` |
| BFS/DFS templates reference | `DSA/Reference/bfs-dfs-templates-reference.md` |
| Trees (tree-specific BFS/DFS) | `DSA/Interview/trees-and-bfs-dfs.md` |
| Simulation patterns (grid traversal) | `DSA/Implementation/simulation-patterns.md` |
| Rotting Oranges problem deep dive | `DSA/Patterns/rotting-oranges-problem.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Graphs. 5 patterns: grid BFS/DFS, topological sort, graph cloning, Union-Find, Dijkstra. Canonical walkthrough (LC 200 Number of Islands), expanded problem bank with 10 problems. |
| May 2026 | **Lambda & Fallback pass.** Added 🔄 Lambda section with PQ comparator explanation + overflow warning. Added inline English comment + 🔄 Fallback at Dijkstra PQ usage (Pattern 5). |
