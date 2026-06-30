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

## 🔨 Building the Graph — Input Format Patterns

> **The interview trap no one warns you about:** you can know BFS cold and still stall because you don't know how to BUILD the graph from the raw input the problem hands you. There are 6 common input formats interviewers use. Know all of them.

### 🧠 The Two-Phase mental model (burn this into your head)

```
Every graph interview = Phase 1 + Phase 2

  Phase 1 — BUILD the graph    ← this section
             translate raw input into adj list / matrix / implicit

  Phase 2 — RUN the algorithm  ← everything else in this file
             BFS / DFS / Topo Sort / DSU / Dijkstra...

The notes drill Phase 2 well. Phase 1 is what bites you in the interview.
Interviewers give you raw arrays, not a ready-made adjacency list.
```

---

### Format 1 — Two separate arrays, 1-indexed nodes ⚠️ Most dangerous

**What the problem gives you:**
```
n = 6 nodes, m = 4 roads
center_from = [1, 2, 4, 3]
center_to   = [2, 5, 5, 4]
```

**Real example:** HackerRank / platform interviews (the vaccine distribution problem).
Nodes go from 1 to n. This is the format that triggers the 1-indexed trap.

> ⚠️ **The 1-indexed trap — most common Phase 1 bug:** nodes start at 1, but `ArrayList` indices start at 0. If you create a list of size `n`, the last valid index is `n-1` — but node `n` exists and you'll call `adj.get(n)` → `IndexOutOfBoundsException`. This costs 10 minutes in a live interview.

```java
// ❌ WRONG — crashes when any node id equals n
List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i < n; i++) {            // creates indices 0..n-1
    adj.add(new ArrayList<>());          // node n has no slot!
}
for (int i = 0; i < m; i++) {
    adj.get(from[i]).add(to[i]);         // from[i] could be n → crash
}

// ✅ CORRECT — allocate n+1, use nodes as-is, ignore index 0
List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i <= n; i++) {          // creates indices 0..n ← extra slot
    adj.add(new ArrayList<>());         // index 0 intentionally wasted
}
for (int i = 0; i < m; i++) {
    int u = from[i];                    // node ids start at 1, use directly
    int v = to[i];
    adj.get(u).add(v);
    adj.get(v).add(u);                  // undirected: both directions
}
```

> **Lesson learned the hard way (June 2026):** this exact bug cost Kapil ~10 minutes in a real vaccine-distribution interview. He created `new ArrayList<>(n)` but nodes were labelled 1..n. The fix is one word: change `i < n` to `i <= n` in the init loop. **Mnemonic before you write the for-loop: "Are nodes 1-indexed? → n+1 lists."**

---

### Format 2 — Edge pair array `int[][] edges`, 0-indexed (LeetCode standard)

**What the problem gives you:**
```
n = 5
edges = [[0,1],[1,2],[2,3],[3,4]]
```

Simpler — node id == list index, no offset needed.

```java
List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i < n; i++) {
    adj.add(new ArrayList<>());
}
for (int[] edge : edges) {
    int u = edge[0];
    int v = edge[1];
    adj.get(u).add(v);
    adj.get(v).add(u);                  // omit this line for directed graph
}
```

**Real interview examples using this format:**
- LC 261 Graph Valid Tree (`n` nodes, `int[][] edges`, 0-indexed)
- LC 1319 Make Network Connected (`int[][] connections`)
- LC 547 Number of Provinces — but uses adjacency MATRIX, see Format 5 below

---

### Format 3 — Prerequisites pairs (topo sort problems)

**What the problem gives you:**
```
numCourses = 4
prerequisites = [[1,0],[3,1],[3,2]]
// [a, b] means "must take b before a" → edge b → a
```

Direction is counterintuitive. The arrow goes FROM the prerequisite TO the dependent, not the other way.

```java
List<List<Integer>> adj = new ArrayList<>();
int[] inDegree = new int[numCourses];
for (int i = 0; i < numCourses; i++) {
    adj.add(new ArrayList<>());
}
for (int[] pre : prerequisites) {
    int course = pre[0];
    int prereq = pre[1];
    // prereq UNLOCKS course → arrow points FORWARD in time: prereq → course
    adj.get(prereq).add(course);
    inDegree[course]++;
}
```

> **Mnemonic: "prereq UNLOCKS course" → arrow points FROM prereq TO course.** If you flip this, Kahn's starts with wrong nodes and breaks silently (no exception, just wrong answer). This is the #1 topo sort setup bug.

---

### Format 4 — Weighted edges (Dijkstra, Prim's, Bellman-Ford)

**What the problem gives you:**
```
edges = [[0,1,4],[1,2,7],[0,2,2]]
// [from, to, weight]
```

Change `List<List<Integer>>` to `List<List<int[]>>` and store `{neighbor, weight}` pairs.

```java
List<List<int[]>> adj = new ArrayList<>();
for (int i = 0; i < n; i++) {
    adj.add(new ArrayList<>());
}
for (int[] edge : edges) {
    int u = edge[0];
    int v = edge[1];
    int w = edge[2];
    adj.get(u).add(new int[]{v, w});
    adj.get(v).add(new int[]{u, w});    // undirected weighted; omit for directed
}

// Accessing in Dijkstra / BFS loop:
for (int[] neighbor : adj.get(u)) {
    int v = neighbor[0];
    int weight = neighbor[1];
    // ... relax dist[v]
}
```

---

### Format 5 — Adjacency matrix `int[][] isConnected`

**What the problem gives you:**
```
isConnected = [[1,1,0],
               [1,1,0],
               [0,0,1]]
// isConnected[i][j] == 1 → edge i—j
```

**Real interview example:** LC 547 Number of Provinces.

**Do NOT rebuild this into an adjacency list** — just iterate the matrix row directly in your DFS/BFS.

```java
// DFS directly on the adjacency matrix — no list-building phase at all
private void dfs(int u, boolean[] visited, int[][] isConnected, int n) {
    visited[u] = true;
    for (int v = 0; v < n; v++) {            // scan the entire row
        if (isConnected[u][v] == 1 && !visited[v]) {
            dfs(v, visited, isConnected, n);
        }
    }
}
```

> **Why not rebuild?** Reading the matrix is O(V²) — same cost as using it directly. Rebuilding adds code, adds bugs, adds no benefit.

---

### Format 6 — Implicit graph (no edges given — generate on the fly)

**What the problem gives you:**
```
beginWord = "hit", endWord = "cog"
wordList = ["hot","dot","dog","lot","log","cog"]
// "edge" = two words differing by exactly ONE letter
// no explicit edge list exists
```

**Real interview example:** LC 127 Word Ladder.

Never precompute the adjacency list (O(N² × L) → TLE). Generate neighbors lazily inside BFS:

```java
Set<String> wordSet = new HashSet<>(wordList);
// Inside BFS loop, for the current word u just dequeued:
char[] chars = u.toCharArray();
for (int i = 0; i < chars.length; i++) {
    char original = chars[i];
    for (char c = 'a'; c <= 'z'; c++) {
        if (c == original) {
            continue;
        }
        chars[i] = c;
        String candidate = new String(chars);
        if (wordSet.contains(candidate) && !visited.contains(candidate)) {
            // candidate is a valid neighbor
            visited.add(candidate);
            queue.offer(candidate);
        }
    }
    chars[i] = original;                     // restore for next position
}
```

---

### Quick-reference: format → build pattern

| What the problem gives you | Build pattern | Indexing trap? |
| --- | --- | --- |
| Two arrays `from[]`, `to[]`, nodes 1..n | `ArrayList` size `n+1`; ignore index 0 | ⚠️ Yes — use `i <= n` in init loop |
| `int[][] edges`, nodes 0..n-1 | `ArrayList` size `n`; use directly | ✅ No |
| `prerequisites[][]` pairs | Same as edge list; check edge DIRECTION carefully | ✅ Usually 0-indexed |
| `int[][]` with weight as 3rd element | `List<List<int[]>>`; store `{v, w}` pairs | — |
| `isConnected[][]` adjacency matrix | Don't rebuild — iterate row in DFS/BFS directly | — |
| No edges (Word Ladder style) | Generate neighbors inside BFS loop; 26-char substitution | — |

---

---

## 🧭 Pattern 1: Grid BFS/DFS (Flood Fill / Islands) ⭐

**What this solves:** Problems on a 2D grid where you need to count, mark, or spread across connected regions. The grid IS the implicit graph — cells are nodes, adjacent cells are edges. Classic triggers: counting islands, flood fill, spreading rot/fire, shortest binary-grid path.

**Recognition cues — reach for this when:**
- "Number of islands" (count connected 1-regions in a grid)
- "Flood fill" (change color of connected region)
- "Rotting oranges" (spread from multiple sources)
- "Shortest path in binary matrix"

**Brute force:** For each unvisited land cell, scan the entire grid to find all connected cells. O(m² × n²) time — O(m × n) anchor cells, each triggering a potentially full-grid scan.

**Key insight:** Marking each visited cell immediately (sink to `'0'` or flip a `visited` flag) ensures every cell is processed at most once. Total work is O(m × n) regardless of island count or shape.

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

**Complexity (optimal):** O(m × n) time, O(m × n) space for recursion stack (DFS) or queue (BFS).

---

## 🧭 Pattern 2: Topological Sort ⭐

**What this solves:** Problems where items have directed dependencies ("A requires B first") and you need a valid processing order or to detect if a cycle makes ordering impossible. Only valid for DAGs (directed acyclic graphs — graphs with no cycles).

**Recognition cues — reach for this when:**
- "Can I finish all courses with prerequisites?"
- "Order tasks with dependencies"
- "Alien dictionary" (determine character ordering)
- Any DAG (directed acyclic graph) ordering problem

**Brute force:** Generate all n! permutations of nodes and check each against all prerequisite constraints. O(n! × E) time.

**Key insight:** A node with zero in-degree (no remaining prerequisites) is always safe to process first. Kahn's algorithm greedily picks such nodes — O(V + E) by consuming the graph layer by layer.

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

**Complexity (optimal):** O(V + E) time, O(V + E) space.

---

## 🧭 Pattern 3: Graph Traversal + Cloning

**What this solves:** Problems requiring traversal while simultaneously building a deep copy of a graph. Core challenge: cycles — without tracking already-cloned nodes, DFS will loop infinitely revisiting the same node.

**Recognition cues — reach for this when:**
- "Clone graph" (deep copy)
- "Copy list with random pointer"
- Any "traverse and build a copy" problem

**Brute force:** Recursively clone without a visited map. O(V²) time on cyclic graphs — infinite revisiting and re-cloning without a cycle guard.

**Key insight:** A HashMap from `original → clone` serves double duty: cycle guard (already visited?) and lookup table for wiring clone neighbor lists. One BFS pass, O(V + E).

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

**Complexity (optimal):** O(V + E) time, O(V) space.

---

## 🧭 Pattern 4: Union-Find (Disjoint Set Union)

**What this solves:** Dynamic connectivity — quickly answer "are A and B in the same group?" or "how many groups exist?" Classic triggers: connected components, redundant edge detection, merging accounts by shared identifier.

**Recognition cues — reach for this when:**
- "Number of connected components"
- "Redundant connection" (find the edge that creates a cycle)
- "Accounts merge" (group by shared emails)
- Any problem about grouping/merging dynamically

**Brute force:** For each connectivity query, run BFS/DFS from one node and check if the other is reachable. O(V + E) per query — O(Q × (V + E)) for many queries.

**Key insight:** Path compression (flatten tree on `find`) + union by rank (attach shorter tree under taller) together amortize each operation to O(α(n)) — the inverse Ackermann function, effectively O(1).

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

**Complexity (optimal):** O(α(n)) per union/find operation, O(n) space — α(n) is the inverse Ackermann function, effectively O(1).

---

## 🧭 Pattern 5: Shortest Path (Dijkstra)

**What this solves:** Shortest path from a single source to all nodes in a weighted graph with non-negative edge weights. Triggers: "network delay," "minimum cost to reach all nodes," "cheapest path from A to B."

**Recognition cues — reach for this when:**
- "Network delay time" (shortest time to reach all nodes)
- "Cheapest flights within K stops"
- Weighted graph + shortest path

**Brute force:** DFS from source exploring all paths and tracking running cost. O(V^E) time — exponential path explosion on dense graphs.

**Key insight:** Once a node is popped from the min-heap, its distance is finalized — non-negative weights guarantee no future path can improve it. Each node finalized exactly once — O((V + E) log V).

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

**Complexity (optimal):** O((V + E) log V) time, O(V + E) space.

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

> **Brute force:** For each `'1'` cell, scan the full grid to find all connected cells. O(m² × n²) time.
> **Key insight:** Sink each visited land cell to `'0'` immediately — every cell is touched at most once, giving O(m × n) total.
> **Approach:** Walk every cell. On finding `'1'`, count++ and DFS/BFS to sink the entire island.

```java
if (grid[r][c] == '1') {
    // Found a new island — count it, then sink all its connected land
    count++;
    dfs(grid, r, c);
}
```

**Complexity (optimal):** O(m × n) time, O(m × n) space.

---

### LC 733: Flood Fill

> **Problem:** Given an image (grid of ints), a starting pixel `(sr, sc)`, and a new color, change the color of the starting pixel and all connected pixels with the same original color.

> **Brute force:** BFS/DFS but re-check every cell on each step. O(m² × n²) time without proper visited marking.
> **Key insight:** Painting to the new color IS the visited marker — no separate `visited` array needed (unless `newColor == oldColor`, which needs a special early-return check).
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

**Complexity (optimal):** O(m × n) time, O(m × n) space.

---

### LC 994: Rotting Oranges

> **Problem:** Grid with 0 (empty), 1 (fresh orange), 2 (rotten). Each minute, fresh oranges adjacent to rotten ones become rotten. Return minimum minutes until no fresh orange remains, or -1 if impossible.

> **Brute force:** Simulate minute by minute with a full grid scan each pass. O((m × n)²) time.
> **Key insight:** Multi-source BFS — seed the queue with ALL rotten oranges simultaneously so they spread in parallel. Each BFS level = exactly one minute.
> **Approach:** Multi-source BFS — enqueue ALL rotten oranges at start. Each BFS level = one minute. Track fresh count.

```java
// See Pattern 1 multi-source BFS template above
while (!queue.isEmpty() && fresh > 0) {
    // process one level = one minute
    minutes++;
}
return fresh == 0 ? minutes : -1;
```

**Complexity (optimal):** O(m × n) time, O(m × n) space.

---

### LC 207: Course Schedule

> **Problem:** There are `n` courses labeled `0..n-1`. Prerequisites given as pairs `[a, b]` meaning "to take a, you must first take b." Determine if you can finish all courses (no circular dependency).

> **Brute force:** Try all n! orderings and check each against prerequisite constraints. O(n! × E) time.
> **Key insight:** Kahn's BFS: start with in-degree-0 nodes (no prereqs), peel layers off the graph. If any node remains after BFS, it's in a cycle.
> **Approach:** Topological sort (Kahn's). Build in-degree array. If BFS processes all nodes → no cycle → possible.

```java
// See Pattern 2 template above
return count == numCourses;
```

**Complexity (optimal):** O(V + E) time, O(V + E) space.

---

### LC 210: Course Schedule II

> **Problem:** Same as Course Schedule, but return a valid ordering of courses (not just true/false).

> **Brute force:** Try all n! orderings and verify against constraints. O(n! × E) time.
> **Key insight:** Same as LC 207 — Kahn's naturally produces one valid topological order. Collect the BFS poll order into a result array.
> **Approach:** Same Kahn's algorithm, but collect the BFS order into a result array.

```java
int[] order = new int[numCourses];
// Inside BFS: order[idx++] = course — record topological order as we process
return idx == numCourses ? order : new int[]{};
// If idx < numCourses, a cycle exists — return empty array
```

**Complexity (optimal):** O(V + E) time, O(V + E) space.

---

### LC 133: Clone Graph

> **Problem:** Given a reference to a node in a connected undirected graph, return a deep copy. Each node has a value and a list of neighbors.

> **Brute force:** Recursive DFS without a visited map — infinite loop on cycles.
> **Key insight:** HashMap `original → clone` is both the cycle guard and the lookup table for wiring neighbor lists. One BFS pass, O(V + E).
> **Approach:** BFS + HashMap mapping original → clone. For each neighbor, clone if not seen, then link.

```java
// Seed the map with the first node's clone before starting BFS
map.put(node, new Node(node.val));
// BFS: for each neighbor, clone if unseen, then wire clone's neighbor list
```

**Complexity (optimal):** O(V + E) time, O(V) space.

---

### LC 323: Number of Connected Components

> **Problem:** Given `n` nodes and edges in an undirected graph, return the number of connected components.

> **Brute force:** BFS/DFS from each unvisited node to count components. O(V + E) time — valid but slower per query than Union-Find when built incrementally.
> **Key insight:** Union-Find counts components by starting at n (all isolated) and decrementing each time two previously separate groups are merged.
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

**Complexity (optimal):** O(E × α(n)) time, O(n) space.

---

### LC 684: Redundant Connection

> **Problem:** An undirected graph with `n` nodes (tree + one extra edge). Find the edge that, if removed, makes the graph a tree.

> **Brute force:** Try removing each edge one by one and check if the remaining graph is a tree. O(E × (V + E)) time.
> **Key insight:** Union-Find processes edges in order — the first edge whose two endpoints are already in the same component closes a cycle; that's the redundant edge.
> **Approach:** Union-Find. Process edges one by one. The first edge where `union()` returns false (both nodes already connected) is the redundant one.

```java
for (int[] edge : edges) {
    // union() returns false when both nodes are already connected — that edge is redundant
    if (!uf.union(edge[0], edge[1])) {
        return edge;
    }
}
```

**Complexity (optimal):** O(E × α(n)) time, O(n) space.

---

### LC 743: Network Delay Time

> **Problem:** Given a directed weighted graph of `n` nodes, send a signal from node `k`. Return the time it takes for ALL nodes to receive the signal. Return -1 if impossible.

> **Brute force:** DFS/BFS exploring all paths from `k`, tracking minimum time to each node. O(V^E) time on dense graphs.
> **Key insight:** Dijkstra from `k` finds shortest time to every node. The answer is the maximum of those times — the bottleneck node.
> **Approach:** Dijkstra's algorithm from source `k`. Answer = max distance across all nodes.

```java
// See Pattern 5 template above
return maxDist == Integer.MAX_VALUE ? -1 : maxDist;
```

**Complexity (optimal):** O((V + E) log V) time, O(V + E) space.

---

### LC 417: Pacific Atlantic Water Flow

> **Problem:** Grid of heights. Water can flow to adjacent cells with equal or lower height. Pacific ocean touches top and left edges, Atlantic touches bottom and right edges. Find cells that can flow to BOTH oceans.

> **Brute force:** For every cell, BFS/DFS forward (downhill) and check if both oceans are reachable. O((m × n)²) time.
> **Key insight:** Reverse the flow direction — start BFS from ocean edges and travel UPHILL. Cells reachable uphill from Pacific AND Atlantic are the answer. Two passes instead of m × n passes.
> **Approach:** Reverse BFS/DFS — start from ocean edges and flow UPHILL. Cells reachable from both oceans are the answer.

```java
// DFS from all Pacific edge cells → mark reachable
// DFS from all Atlantic edge cells → mark reachable
// Intersection of both sets = answer
```

**Complexity (optimal):** O(m × n) time, O(m × n) space.

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

## 🗺️ FAANG Gaps — Study These Next

> Work through the 5 core patterns above first. Each entry below is self-contained — problem statement, what's new, and where it's asked.

---

**LC 127 — Word Ladder** | 🟡 | Google, Amazon

*What it asks:* Given a `beginWord`, an `endWord`, and a word list, find the **shortest sequence** of one-letter transformations from begin → end where every intermediate word must exist in the list. Return the length; 0 if impossible.

*New twist:* BFS on an **implicit graph** — no adjacency list is given. You construct edges on the fly: for each word in the queue, try swapping every character to every letter a–z and check if the result is in the word list. The graph is never stored; BFS discovers it.

---

**LC 787 — Cheapest Flights Within K Stops** | 🟡 | Amazon, Meta

*What it asks:* Given a weighted directed graph of flight routes, find the **cheapest price** from `src` to `dst` using **at most k stops** (not k edges — one stop = one intermediate city).

*New twist:* Standard Dijkstra only tracks `(cost, node)`. Here you must track `(cost, node, stops_used)` as state — a node can be visited multiple times via different stop-counts. The cheapest path may NOT be the fewest-stops path, so you can't prune on visited alone.

---

**LC 269 — Alien Dictionary** | 🟡 | Google

*What it asks:* Given a list of words sorted in an alien language's alphabetical order, **deduce the character ordering** of that alphabet. Return any valid ordering string; return `""` if a contradiction exists.

*New twist:* Topological sort, but **you must first build the graph** — there's no adjacency list given. Compare adjacent words character-by-character; the first differing character gives you a directed edge (`char_in_word_i → char_in_word_j`). The topo sort itself is standard Kahn's after that.

---

**LC 785 — Is Graph Bipartite** | ✅ | Google, Meta

*What it asks:* Given an undirected graph (as adjacency list), determine if it can be **2-colored** such that no two adjacent nodes share the same color. Equivalently: can you split nodes into two groups where all edges go between groups, never within?

*New twist:* BFS/DFS with a `color[]` array (0 or 1). When visiting a neighbor: if uncolored → assign opposite color; if already colored → check it's the opposite. If same color as current node → return false. Handle disconnected components by iterating all nodes.

---

**LC 1584 — Min Cost to Connect All Points** | 🟡 | Meta, Amazon

*What it asks:* Given `n` points on a 2D plane, find the **minimum cost to connect all points** (like laying cables), where cost between two points = Manhattan distance `|x1-x2| + |y1-y2|`. Every point must be reachable.

*New twist:* This is **Minimum Spanning Tree (MST)** — a new algorithm family not covered in the 5 core patterns. Use Prim's: start from any node, always add the cheapest edge connecting a visited node to an unvisited node (min-heap). Kruskal's (sort all edges, Union-Find) also works but Prim's is easier to implement for dense graphs.

---

**LC 1192 — Critical Connections in a Network** | 🔴 | Meta

*What it asks:* Given `n` servers and a list of undirected connections, find all **critical connections** — edges whose removal would disconnect at least one server from the rest (bridge edges in graph theory).

*New twist:* Requires **Tarjan's bridge-finding algorithm** — a DFS that tracks two values per node: `disc[]` (discovery time — when DFS first visited this node) and `low[]` (lowest discovery time reachable from this node's subtree via back-edges). An edge `(u, v)` is a bridge if `low[v] > disc[u]` — meaning v's subtree cannot reach back above u without using the u→v edge. Hard; Senior+ territory.

---

**LC 721 — Accounts Merge** | 🟡 | Google, Amazon

*What it asks:* Given a list of accounts where each entry is `[name, email1, email2, ...]`, **merge accounts that share at least one email** (same email = same person). Return merged accounts with emails sorted, each prefixed by the account name.

*New twist:* Union-Find on **strings**, not integers. Map each email to an ID, run Union-Find on IDs to group connected emails, then reconstruct groups. The DSU mechanics are identical to LC 684 (Redundant Connection) — the difficulty is the string-mapping wrapper and the grouping + sorting step at the end.

---

**Sequence-ordered traversal (non-LC)** | ✅ | Google, Meta

*What it asks:* Given a graph of cities and a required visit sequence `[c1, c2, c3, ..., cn]`, find a valid path that visits the cities **in sequence order** — you must reach `c1` before `c2`, `c2` before `c3`, etc. Twist: cities that appear **later** in the sequence are **forbidden** until you've visited their predecessors.

*New twist:* BFS with a **forbidden-nodes set that updates per step**. Before BFS from `c_i` to `c_{i+1}`, add `{c_{i+2}, c_{i+3}, ..., c_n}` to a forbidden set. In BFS, add one line: `if (forbidden.contains(next)) continue;`. After reaching `c_{i+1}`, remove it from forbidden for the next leg. Standard BFS otherwise.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Graphs deep dive (adjacency list, BFS/DFS theory) | `DSA/DeepDive/graphs-fundamentals.md` |
| BFS/DFS templates reference | `DSA/Reference/bfs-dfs-templates-reference.md` |
| Trees (tree-specific BFS/DFS) | `DSA/Interview/Playbooks/trees-and-bfs-dfs.md` |
| Simulation patterns (grid traversal) | `DSA/Implementation/simulation-patterns.md` |
| Rotting Oranges problem deep dive | `DSA/Patterns/rotting-oranges-problem.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Graphs. 5 patterns: grid BFS/DFS, topological sort, graph cloning, Union-Find, Dijkstra. Canonical walkthrough (LC 200 Number of Islands), expanded problem bank with 10 problems. |
| May 2026 | **Lambda & Fallback pass.** Added 🔄 Lambda section with PQ comparator explanation + overflow warning. Added inline English comment + 🔄 Fallback at Dijkstra PQ usage (Pattern 5). |
| June 2026 | **Brute force / Key insight pass.** Added `**What this solves**`, `**Brute force**`, `**Key insight**`, `**Complexity (optimal)**` to all 5 patterns and all 10 problem bank entries. Format matches `binary-search.md` and `heaps.md`. |
| June 2026 | **FAANG Gaps section added.** 8 graph problems not covered in the 5 core patterns. Each entry has: problem statement (what it asks), new twist (what's different), companies, priority tag. Problems: Word Ladder, Cheapest Flights K Stops, Alien Dictionary, Bipartite, MST, Critical Connections, Accounts Merge, Sequence-ordered traversal. |
