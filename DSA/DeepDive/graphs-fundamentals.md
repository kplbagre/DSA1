# Graphs — Fundamentals (Deep Dive)

> **Curriculum alignment:** this deep-dive mirrors **Striver's Graph Series** (`take U forward` YouTube playlist, 56 videos, G-1 through G-56). Watch his videos for video-first learning; use this doc for written reinforcement, our project's code style, and bug callouts gathered from real practice. Cross-references in each section cite the exact `G-N` video.

> **Credit:** topic ordering, problem selection, and the canonical algorithm progressions come from Raj Vikramaditya (`take U forward` / Striver). Code is rewritten in our project style (one statement per line, instance fields over `int[] holder`, Java conventions). Bug callouts and the decision frameworks are this doc's contribution.

---

## 🎯 Why You're Reading This (The Goal)

By the end of this doc, you should:

1. **Recognize the four foundational graph traversals:** BFS, DFS, Topological Sort, Union-Find — and know which one each problem wants
2. **Know the shortest-path family cold:** unweighted-BFS, Dijkstra, Bellman-Ford, Floyd-Warshall — and the *exact* signal for picking one
3. **Implement DSU (Union-Find) by hand** with path compression and union-by-rank — interviewers ask for this directly
4. **Pattern-match grid problems** — most "islands / oranges / regions" problems are BFS/DFS on a hidden graph
5. **Spot cycle-detection traps** — undirected needs parent tracking; directed needs the recursion stack
6. **Know when to skip a topic** — Tarjan's, Kosaraju's, articulation points are 🔴 Senior+; you can defer them

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

Every problem and algorithm is tagged so you can **climb tiers in order**.

| Tag | Meaning | Use it when |
| --- | --- | --- |
| ✅ | **Try now** — covered by what you've read so far | You're learning a new section |
| 🟡 | **Try after** — needs concepts from later sections | You've finished those later sections |
| 🔴 | **Reference only / Senior+** | Don't attempt cold; come back when prerequisites are solid |

> **Lesson learned the hard way (May 2026):** I (Kapil) burned an hour on LC 124 (in trees doc) because I attempted a 🔴 cold without the prerequisite ladder. **Don't repeat this for graphs.** The tier system exists precisely so you don't waste an hour on Bellman-Ford or Tarjan's before you have BFS muscle memory.

---

## 📍 Reading Roadmap — Medium vs Advanced

> **The doc is split into two halves.** The first half is **everything you need for a medium-level SDE-2/SDE-3 interview**. The second half is senior+ territory — important to be aware of, optional to master cold.

| Section | Scope | Striver videos | When to read |
| --- | --- | --- | --- |
| **Medium-level core** ✅ | Representation → BFS/DFS → Grid problems → Cycle detection → Bipartite → Topological Sort | G-1 to G-26 | **Start here. Master before any interview.** |
| **Senior likely** 🟡 | Shortest Path family (BFS-SP, DAG-SP, Word Ladder, Dijkstra) | G-27 to G-35 | After Topo Sort; commonly asked at L5+ |
| **Advanced / Senior+** 🔴 | Dijkstra applications, Bellman-Ford, Floyd-Warshall, MST, DSU, Kosaraju, Tarjan, Articulation Points | G-36 to G-56 | Read for awareness; attempt only after Tiers 1–3 of the Practice Plan are solid |

> **Look for the `═══ ✋ END OF MEDIUM-LEVEL SCOPE ═══` divider** — that's where the medium portion ends. Everything below is for senior roles, system-design-adjacent rounds, or interview-prep round 2.
>
> **If you have ≤ 3 weeks:** master only the Medium-level core. That alone solves ~80% of LC graph problems and almost every SDE-2 graph interview question.

---

## 📖 Terminology (Memorize These) [Striver G-1]

| Term | Meaning |
| --- | --- |
| **Vertex / Node** | A point in the graph (e.g., a person in a social network) |
| **Edge** | A connection between two vertices (e.g., a friendship) |
| **Undirected** | Edges have no direction; if A connects to B, B connects to A |
| **Directed** | Edges have direction; A → B does NOT imply B → A |
| **Weighted** | Edges carry a numeric cost (distance, time, capacity) |
| **Unweighted** | All edges are equivalent (or weight = 1) |
| **Cycle** | A path that starts and ends at the same vertex with no repeats in between |
| **Acyclic** | No cycles |
| **DAG** | Directed Acyclic Graph — directed AND no cycles (critical: prerequisites, build systems) |
| **Path** | A sequence of vertices where each adjacent pair shares an edge |
| **Simple path** | A path with no repeated vertices |
| **Connected** (undirected) | Every vertex is reachable from every other via some path |
| **Strongly Connected** (directed) | Every vertex is reachable from every other respecting edge direction |
| **Connected Component** | A maximal set of vertices that are all mutually reachable |
| **Degree** (undirected) | Number of edges touching a vertex |
| **In-degree / Out-degree** (directed) | Edges entering / leaving a vertex |
| **Neighbor / Adjacent** | Two vertices connected by an edge |
| **Dense vs Sparse** | Many vs few edges relative to `V²` |
| **Tree** | A connected, acyclic, undirected graph with `V - 1` edges |
| **Self-loop** | An edge from a vertex to itself |
| **Parallel edges** | Two distinct edges between the same pair of vertices |

> **Mental model:** A graph is just a set of vertices and a relationship (the edges). Everything else — paths, cycles, connectivity — is derived from that.

### 🎨 Visual Reference — Graph Types at a Glance

```
   UNDIRECTED                      DIRECTED                       WEIGHTED
   (edges are 2-way)               (edges have arrows)            (edges carry costs)

       (1)─────(2)                   (1)─────►(2)                    (1)
       /         \                    │         ▲                  4 / \ 7
      /           \                   │         │                   /   \
    (3)──────────(4)                 (3)◄──────(4)                (3)───(4)
                                                                      2

   CYCLIC GRAPH                    ACYCLIC / DAG                  TREE
   (path returns to start)         (no cycles, directed)          (connected + acyclic)

       (1)─────(2)                   (1)─────►(2)                    (1)
        │       │                            │                      /   \
        │       │                            ▼                    (2)   (3)
       (3)─────(4)                  (3)─────►(4)                  /
                                                                (4)
   1→2→4→3→1 is a cycle          no path returns to start      V-1 edges, no cycle

   CONNECTED                       DISCONNECTED                   COMPLETE
   (one component)                 (multiple components)          (every pair connected)

       (1)─────(2)                   (1)─────(2)     (5)            (1)─────(2)
        │       │                    │                                │ ╲   ╱ │
       (3)─────(4)                  (3)─────(4)                       │  ╲ ╱  │
                                                                      │   X   │
                                                                      │  ╱ ╲  │
                                                                     (3)─────(4)

   DEGREE / IN-DEGREE / OUT-DEGREE                  SELF-LOOP & PARALLEL EDGES

   undirected: degree = edges touching v             ┌──┐
   directed:                                         │  │   ← self-loop on 1
     - in-degree = arrows pointing IN                ▼  │
     - out-degree = arrows pointing OUT             (1)─┘ ═══════ (2)
                                                                ⇧ parallel edges
     (1)──►(2)──►(3)
            ▲
            │
           (4)        v=2: in-degree=2, out-degree=1
```

---

## 🗂️ Graph Representations [Striver G-2, G-3]

Two ways. Pick based on density.

### 🎨 Visual — Same Graph, Two Representations

```
Example undirected graph (V = 5, E = 6):

         (0)
        / | \
       /  |  \
     (1)─(2)─(3)
       \     /
        \   /
         (4)

Edges: (0,1) (0,2) (0,3) (1,2) (2,3) (1,4) (3,4)


ADJACENCY LIST                          ADJACENCY MATRIX
(sparse-friendly, O(V+E) memory)        (dense, O(V²) memory)

  0 ──► [1, 2, 3]                              0  1  2  3  4
  1 ──► [0, 2, 4]                          0 [ 0  1  1  1  0 ]
  2 ──► [0, 1, 3]                          1 [ 1  0  1  0  1 ]
  3 ──► [0, 2, 4]                          2 [ 1  1  0  1  0 ]
  4 ──► [1, 3]                             3 [ 1  0  1  0  1 ]
                                           4 [ 0  1  0  1  0 ]
  Memory: 5 lists +
          14 entries (=2E)                  Memory: 25 cells (=V²)
                                            Symmetric across diagonal
                                            (because undirected)
```

> **For DIRECTED graphs:** the matrix is **not** symmetric — `matrix[u][v] = 1` only if there's an edge `u → v`. For the adjacency list, you add `v` to `adj.get(u)` only, never the reverse.

> **For WEIGHTED graphs:** the matrix stores weight (or `∞` for "no edge"). The adjacency list stores `int[]{neighbor, weight}` pairs.

### Adjacency List (default for sparse graphs — almost every interview problem)

> Each vertex stores a list of its neighbors. Memory: `O(V + E)`. Lookup of neighbors: `O(neighbors of v)` = `O(degree)`.

**Steps in plain English:**

1. Initialize a `List<List<Integer>>` of size `V` (one inner list per vertex).
2. Allocate each inner list as an `ArrayList<>()` so we can `.add()` to it.
3. For each edge `(u, v)`, append `v` to `adj.get(u)`. If undirected, **also** append `u` to `adj.get(v)`.

```java
public List<List<Integer>> buildAdjList(int V, int[][] edges, boolean directed) {
    // Step 1 — outer list of size V
    List<List<Integer>> adj = new ArrayList<>();
    // Step 2 — each slot is a fresh ArrayList
    for (int i = 0; i < V; i++) {
        adj.add(new ArrayList<>());
    }
    // Step 3 — for each edge, add both directions if undirected
    for (int[] edge : edges) {
        int u = edge[0];
        int v = edge[1];
        adj.get(u).add(v);
        if (!directed) {
            adj.get(v).add(u);
        }
    }
    return adj;
}
```

**For weighted edges:** store `int[]{neighbor, weight}` or use a `class Edge { int to; int weight; }`.

```java
// Weighted adjacency list
List<List<int[]>> adj = new ArrayList<>();
for (int i = 0; i < V; i++) {
    adj.add(new ArrayList<>());
}
for (int[] edge : edges) {
    int u = edge[0];
    int v = edge[1];
    int w = edge[2];
    adj.get(u).add(new int[]{v, w});
    if (!directed) {
        adj.get(v).add(new int[]{u, w});
    }
}
```

---

### Adjacency Matrix (dense graphs only; rarely the right interview choice)

> A `V × V` boolean (or `int`) matrix. `matrix[u][v] = 1` if edge exists. Memory: `O(V²)`. Edge lookup: `O(1)`.

```java
int[][] matrix = new int[V][V];
for (int[] edge : edges) {
    int u = edge[0];
    int v = edge[1];
    matrix[u][v] = 1;
    if (!directed) {
        matrix[v][u] = 1;
    }
}
```

### When to use which

| Property | Adjacency List | Adjacency Matrix |
| --- | --- | --- |
| Memory | `O(V + E)` | `O(V²)` |
| Add edge | `O(1)` | `O(1)` |
| Check edge `(u, v)` | `O(degree(u))` | `O(1)` |
| Iterate neighbors of `v` | `O(degree(v))` | `O(V)` |
| Best for | **Sparse graphs (E ≪ V²)** | Very dense graphs (rare in interviews) |

> **Default rule:** **always use adjacency list unless explicitly told otherwise.** Almost every LC graph problem assumes adjacency list.

---

## 🌐 Connected Components — Concept [Striver G-4]

> A **connected component** is a maximal set of mutually reachable vertices. A graph with 10 vertices might have 1 component (fully connected) or 10 components (no edges at all).

### 🎨 Visual — Components in a Disconnected Graph

```
A graph with 3 components:

   Component A          Component B          Component C
   ──────────          ──────────           ──────────

      (0)───(1)            (3)                (5)──(6)
        │                    │                  │    │
        │                   (4)                 │    │
       (2)                                     (7)──(8)


   8 vertices, 3 components.
   To visit every vertex, you MUST start a fresh BFS/DFS from each component.
   That's why the outer `for (i = 0; i < V; i++) if (!visited[i]) ...` loop exists.
```

**Why it matters:** for problems like "count islands" or "number of provinces", you're literally counting connected components.

**The universal counting pattern:**

```java
int components = 0;
boolean[] visited = new boolean[V];
for (int i = 0; i < V; i++) {
    if (!visited[i]) {
        traverse(i, adj, visited);     // BFS or DFS
        components++;
    }
}
return components;
```

> **Why the outer for-loop:** the graph may be disconnected. One BFS/DFS only covers ONE component — you need to iterate over all vertices and trigger a fresh traversal each time you find an unvisited one.

---

## 🌊 BFS — Breadth-First Search [Striver G-5]

> Visit vertices **level by level**, expanding outward from the start. Uses a **queue**. Natural fit for "shortest path in unweighted graph" because the first time you see a vertex is via the fewest edges.

### 🎨 Visual — BFS Level-by-Level (start = 0)

```
Graph:                          BFS expands like ripples in a pond,
                                level by level from the start.
     (0)
    / | \                       Level 0:  ●
  (1)(2)(3)                                          ● = currently being visited
   |     |                      Level 1:  ●  ●  ●    ○ = unvisited
  (4)   (5)                                          ◉ = already visited (recorded)
        |
       (6)                      Level 2:  ●  ●
                                Level 3:  ●


Step-by-step queue + visited evolution (BFS from 0):

  ┌─────────────────────────────────────────────────────────────────┐
  │ Step │ Poll │ Queue after poll │ Add neighbors  │ Visited        │
  ├──────┼──────┼──────────────────┼────────────────┼────────────────┤
  │  0   │  -   │ [0]              │ enqueue 0      │ {0}            │
  │  1   │  0   │ []               │ add 1,2,3      │ {0,1,2,3}      │
  │  1   │  -   │ [1, 2, 3]        │ (queue now)    │                │
  │  2   │  1   │ [2, 3]           │ add 4          │ {0,1,2,3,4}    │
  │  2   │  -   │ [2, 3, 4]        │ (queue now)    │                │
  │  3   │  2   │ [3, 4]           │ (no new nbrs)  │ {0,1,2,3,4}    │
  │  4   │  3   │ [4]              │ add 5          │ {0,1,2,3,4,5}  │
  │  4   │  -   │ [4, 5]           │ (queue now)    │                │
  │  5   │  4   │ [5]              │ (no new nbrs)  │ {0,1,2,3,4,5}  │
  │  6   │  5   │ []               │ add 6          │ {0..6}         │
  │  6   │  -   │ [6]              │ (queue now)    │                │
  │  7   │  6   │ []               │ done           │ {0..6}         │
  └──────┴──────┴──────────────────┴────────────────┴────────────────┘

  BFS order:  0 → 1 → 2 → 3 → 4 → 5 → 6
              └─── L0 ──┴── L1 ──┴── L2 ──┘  (distances: 0,1,1,1,2,2,3)


Key invariant:  THE QUEUE ALWAYS HOLDS AT MOST TWO LEVELS AT A TIME.
                That's why BFS gives shortest path in unweighted graphs —
                a vertex is dequeued only after every vertex at a smaller
                distance has been processed.
```

**Time:** `O(V + E)`. **Space:** `O(V)` for queue + visited.

**Steps in plain English:**

1. Initialize a `boolean[] visited` of size `V`.
2. Create a queue; add the start vertex and mark it visited.
3. While the queue is not empty:
   a. Poll the front vertex.
   b. For each neighbor, if not visited, mark visited and enqueue.

```java
public List<Integer> bfs(int start, List<List<Integer>> adj, int V) {
    // Step 1 — visited array
    boolean[] visited = new boolean[V];
    // Step 2 — queue with starting vertex
    Queue<Integer> queue = new ArrayDeque<>();
    queue.offer(start);
    visited[start] = true;               // ← mark AT THE SAME TIME as offering.
                                         //   Otherwise a neighbor of `start` could
                                         //   look at `start` later, see it's still
                                         //   unvisited, and re-enqueue it.
    List<Integer> order = new ArrayList<>();
    // Step 3 — drain the queue
    while (!queue.isEmpty()) {
        int u = queue.poll();            // ← guaranteed unvisited-as-recorded here,
                                         //   because the only way u got in was
                                         //   through the filter below, which marks
                                         //   visited BEFORE offering.
        order.add(u);
        for (int v : adj.get(u)) {
            if (!visited[v]) {           // ← gatekeeper: only unvisited neighbors enter
                visited[v] = true;       // ← mark BEFORE offering. This is the line
                                         //   that prevents v from being enqueued
                                         //   again by another neighbor of u.
                queue.offer(v);
            }
        }
    }
    return order;
}
```

> **Read the marking placement as a contract:** *"A node is marked visited at the exact moment it enters the queue — never later."* This guarantees every node enters the queue **exactly once**, so queue size ≤ V and total work is tight O(V + E).

> **The single most common BFS bug:** marking visited AFTER polling instead of BEFORE enqueuing. The output is still correct (the `if (visited[u]) continue;` guard catches duplicates) — but the same vertex ends up in the queue multiple times, leading to O(V·E) work in dense graphs.

**Read the annotations carefully — they pinpoint the exact moment the bug appears:**

```java
// ❌ Mark AFTER polling — same vertex enqueued multiple times

queue.offer(start);                      // ← start added to queue, but visited[start]
                                         //   is still FALSE at this moment!

while (!queue.isEmpty()) {
    int u = queue.poll();
    if (visited[u]) continue;            // ← only NOW do we check. The same node may
                                         //   have been queued multiple times before
                                         //   this point — we just throw away the dups.
    visited[u] = true;                   // ← mark on POLL, not on OFFER. This is the
                                         //   timing that breaks the "enqueued exactly
                                         //   once" invariant.
    for (int v : adj.get(u)) {
        queue.offer(v);                  // ← v could ALREADY be in the queue (added
                                         //   by an earlier neighbor of u, or even by
                                         //   another vertex two steps ago) — but
                                         //   visited[v] is only flipped AT POLL, so
                                         //   this offer happens anyway. Duplicate.
    }
}


// ✅ Mark BEFORE enqueuing — each vertex enters the queue exactly once

queue.offer(start);
visited[start] = true;                   // ← marked AT THE SAME LINE as offering.
                                         //   Now no neighbor of start can ever see
                                         //   start as unvisited.

while (!queue.isEmpty()) {
    int u = queue.poll();                // ← guaranteed to be its first-and-only
                                         //   appearance in the queue.
    for (int v : adj.get(u)) {
        if (!visited[v]) {               // ← gatekeeper at the call site
            visited[v] = true;           // ← mark NOW, before the offer. So if u has
                                         //   another neighbor w that points to v too,
                                         //   w will see v as visited — no duplicate.
            queue.offer(v);
        }
    }
}
```

> **The mental shift:** stop thinking *"I'll mark it visited when I process it"* — that's too late. Think *"I'll mark it visited the instant it earns a seat in the queue."* The seat-grab and the marking are a single atomic event.

> 🧩 **Try these (BFS warm-up):**
> - ✅ **LC 102** Binary Tree Level Order — BFS on a tree (you've already done this)
> - ✅ **LC 994** Rotten Oranges (G-10) — multi-source BFS
> - ✅ Practice problem: BFS traversal output of a graph from vertex 0

---

## 🚶 DFS — Depth-First Search [Striver G-6]

> Go as deep as possible from each vertex before backtracking. Uses **recursion** (implicit stack) or an explicit `Deque`. Natural fit for "is there a path", "find all paths", "cycle detection", "topological sort".

### 🎨 Visual — DFS Diving Deep (start = 0)

```
Same graph:                      DFS dives down ONE branch before
                                 backtracking — like exploring a maze
     (0)                         and always taking the leftmost door first.
    / | \
  (1)(2)(3)                      The recursion stack grows DEEP, not WIDE.
   |     |
  (4)   (5)
        |
       (6)


Step-by-step recursion stack + order:

  ┌──────┬──────────────────────────────┬─────────────────┐
  │ Step │ Recursion stack (top → btm)  │ Order recorded  │
  ├──────┼──────────────────────────────┼─────────────────┤
  │  1   │ [0]                          │ 0               │
  │  2   │ [1, 0]                       │ 0, 1            │
  │  3   │ [4, 1, 0]                    │ 0, 1, 4         │
  │  4   │ [1, 0]      ← 4 returns      │ 0, 1, 4         │
  │  5   │ [0]         ← 1 returns      │ 0, 1, 4         │
  │  6   │ [2, 0]                       │ 0, 1, 4, 2      │
  │  7   │ [0]         ← 2 returns      │ 0, 1, 4, 2      │
  │  8   │ [3, 0]                       │ 0, 1, 4, 2, 3   │
  │  9   │ [5, 3, 0]                    │ 0, 1, 4, 2, 3, 5│
  │ 10   │ [6, 5, 3, 0]                 │ ..., 5, 6       │
  │ 11   │ [5, 3, 0]   ← 6 returns      │                 │
  │ 12   │ [3, 0]      ← 5 returns      │                 │
  │ 13   │ [0]         ← 3 returns      │                 │
  │ 14   │ []          ← 0 returns      │ done            │
  └──────┴──────────────────────────────┴─────────────────┘

  DFS order:  0 → 1 → 4 → 2 → 3 → 5 → 6


BFS vs DFS — same graph, very different traversal:

   BFS order: 0  1  2  3  4  5  6        (level by level)
   DFS order: 0  1  4  2  3  5  6        (branch by branch)

   ┌──────────────────────────────────────────────────────────────┐
   │ BFS visualization:           DFS visualization:              │
   │                                                              │
   │     0                            0                           │
   │   / | \                        /                             │
   │  1  2  3       ←→             1                              │
   │  |     |                      |                              │
   │  4     5                      4 ← deep first                 │
   │        |                         (then backtrack)            │
   │        6                                                     │
   └──────────────────────────────────────────────────────────────┘
```

**Time:** `O(V + E)`. **Space:** `O(V)` for recursion + visited.

**Steps in plain English (recursive):**

1. Initialize `visited[]` outside.
2. Inside the helper: mark current vertex visited; add to result.
3. For each unvisited neighbor, recurse.

```java
public List<Integer> dfs(int start, List<List<Integer>> adj, int V) {
    boolean[] visited = new boolean[V];
    List<Integer> order = new ArrayList<>();
    dfsHelper(start, adj, visited, order);
    return order;
}

private void dfsHelper(int u, List<List<Integer>> adj,
                       boolean[] visited, List<Integer> order) {
    // Step 2 — mark + record ON ENTRY, before the loop
    visited[u] = true;                   // ← mark FIRST, before doing any work.
                                         //   This is the DFS analog of "mark on
                                         //   enqueue" — we mark the moment we
                                         //   "enter the function" for this node,
                                         //   not after we've explored its kids.
    order.add(u);
    // Step 3 — recurse on unvisited neighbors
    for (int v : adj.get(u)) {
        if (!visited[v]) {               // ← gatekeeper at the call site. Same role
                                         //   as `if (!visited[v])` in BFS — it
                                         //   prevents re-entering a node we've
                                         //   already started processing.
            dfsHelper(v, adj, visited, order);
        }
    }
    // No explicit return needed — implicit base case fires when every neighbor
    // is already visited and the for-loop body runs zero times.
    // (See `recursion-fundamentals.md` Pattern 3.4 for the full mental model.)
}
```

> **Why marking AFTER the loop would be catastrophic:** if you wrote `for (v) recurse(v); visited[u] = true;` instead — every neighbor of `u` would call back into `u` (because `u` isn't marked yet), and you'd get **infinite recursion → stack overflow** on any cycle. Marking on entry is what makes graph DFS safe in the presence of cycles. Trees don't need this rule because there are no cycles, but graphs absolutely do.

### The BFS-vs-DFS marking rule — one sentence

> **In both BFS and DFS, mark `visited` at the exact moment you "claim" the node** — for BFS that's the moment you offer it to the queue; for DFS that's the moment you enter the recursive call. The `if (!visited[v])` filter at the call site is the gatekeeper that turns this discipline into "enqueued/entered exactly once."

**Iterative DFS (explicit stack) — when recursion depth is a risk:**

```java
public List<Integer> dfsIterative(int start, List<List<Integer>> adj, int V) {
    boolean[] visited = new boolean[V];
    List<Integer> order = new ArrayList<>();
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(start);                   // ← note: we do NOT mark visited yet.
                                         //   Iterative DFS intentionally allows
                                         //   duplicates on the stack — read below.
    while (!stack.isEmpty()) {
        int u = stack.pop();
        if (visited[u]) {                // ← duplicate-on-stack handler. Without this,
            continue;                    //   we'd record u twice.
        }
        visited[u] = true;               // ← mark on POP, not on PUSH. This is the
                                         //   iterative-DFS convention — see note below.
        order.add(u);
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                stack.push(v);           // ← v may already be on the stack lower down,
                                         //   but the `if (visited[u]) continue;` filter
                                         //   above will skip it the second time.
            }
        }
    }
    return order;
}
```

> **"Why does iterative DFS allow duplicates but BFS doesn't?"** — Subtle but important:
>
> | | BFS (mark-on-enqueue) | Iterative DFS (mark-on-pop) |
> | --- | --- | --- |
> | **Cost of allowing duplicates** | Queue can blow up to O(V·E) — every duplicate gets processed wastefully | Stack grows a little, but the `if (visited[u]) continue;` skip is O(1) per duplicate |
> | **Why we tolerate it in DFS** | We don't — mark-on-enqueue is correct and efficient for BFS | Mark-on-push works too, but mark-on-pop **preserves the same traversal order as recursive DFS** (last-pushed neighbor is the first to be fully explored before its siblings) |
> | **Bottom line** | Always mark on enqueue | Either works; mark-on-pop is the convention you'll see in editorials |
>
> If you want **mark-on-push for iterative DFS too** (slightly more memory-efficient), the code looks just like BFS — replace `stack.push(start); visited[start] = true;` and mark inside the `if (!visited[v])` block before pushing. Just be aware the visit order may differ from recursive DFS.

> **Why `Deque` and not `Stack`:** Java's legacy `Stack` class is synchronized and slow. `Deque<Integer> stack = new ArrayDeque<>();` is the modern idiom — use `push()` / `pop()` / `peek()`.

### 🧭 Interview default — recursive or iterative?

> **Write recursive DFS by default.** Roughly **~85% of interview-prep solutions** (LeetCode editorials, Striver, NeetCode, GfG, CCI) use the recursive form. It's shorter, mirrors the problem shape, and is what interviewers expect.
>
> **Switch to iterative when** any of these is true:
>
> | Trigger | Why |
> | --- | --- |
> | `V ≥ 10⁵` and worst case is a chain | Recursion will overflow JVM's ~10⁴-frame default stack |
> | Python with default `sys.setrecursionlimit = 1000` | Recursion limit is the bottleneck, not algorithmic |
> | Interviewer asks *"what if you can't use recursion?"* | Show you know iterative + can explain the tradeoff |
>
> **The senior-engineer answer:** *"I'd write recursive by default — shorter, matches the problem shape. If V can be 10⁵+ and the worst case is a chain, I'd switch to iterative to avoid stack overflow. Both are O(V + E) time."* Say that aloud once and you've covered the question.

> 🧩 **Try these (DFS warm-up):**
> - ✅ Practice problem: DFS traversal output of a graph from vertex 0
> - ✅ **LC 200** Number of Islands (G-8) — DFS on a grid
> - ✅ **LC 547** Number of Provinces (G-7) — DFS on adjacency matrix

---

## 🧭 The Pattern Decision Framework — When to Use What

The **5-question funnel** for graph problems. Walk it top-down.

```
Q1: Is this a TWO-COLOR / BIPARTITE problem? ──── yes ──► Bipartite (BFS or DFS with 2 colors)
        │
        no
        ▼
Q2: Are we finding a SHORTEST PATH (count of edges or weighted)?
        │       ├── unweighted ──► BFS
        │       ├── non-negative weights ──► Dijkstra
        │       ├── negative weights allowed ──► Bellman-Ford
        │       └── all-pairs ──► Floyd-Warshall
        │
        no
        ▼
Q3: Is this a DEPENDENCY / ORDERING problem (prereqs, build, schedule)?
        │
        yes ──► Topological Sort (Kahn's BFS or DFS-based)
        │
        no
        ▼
Q4: Are we doing CYCLE DETECTION?
        │       ├── undirected ──► DFS/BFS with parent tracking
        │       └── directed ──► DFS with recursion stack (or Kahn's: did all V appear?)
        │
        no
        ▼
Q5: Are we MERGING SETS / counting components dynamically?
        │
        yes ──► Disjoint Set Union (Union-Find)
        │
        no
        ▼
        Default: BFS or DFS traversal (counting components, flood fill, all-paths)
```

### Keyword signals

| Keyword / phrase | Pattern |
| --- | --- |
| "level by level", "shortest in unweighted graph", "minimum steps" | **BFS** |
| "all paths from A to B", "connected components", "reachability" | **DFS** |
| "shortest path with non-negative weights" | **Dijkstra** |
| "shortest path can have negative weights" | **Bellman-Ford** |
| "shortest path between all pairs" | **Floyd-Warshall** |
| "prerequisite", "build order", "valid order", "topological" | **Topological Sort** |
| "is there a cycle", "deadlock detection" | **Cycle Detection** |
| "two groups / colors / teams", "no two adjacent same" | **Bipartite** |
| "minimum cost to connect all nodes / cities" | **Minimum Spanning Tree** (Prim or Kruskal) |
| "merge accounts", "group by relationship", "online queries about connectivity" | **DSU** |
| "islands in grid", "regions", "color flood" | **Grid BFS/DFS** |

> **Diagnostic trick:** if a problem reads like *"how many groups / regions / clusters"* → almost always BFS/DFS with a visited set + a component counter.

---

## 🧩 Grid BFS/DFS Problems [Striver G-7 to G-16]

> **First-timer bridge:** if you have NEVER applied BFS/DFS to a 2-D grid before, **do not skip this intro.** Up to now we drew graphs as circles connected by edges, with vertex IDs `0..V-1`. A grid problem hands you a `char[][]` or `int[][]` instead — and your job is to realize that this matrix **is already a graph**, just disguised. The traversal templates are exactly what you've learned; what changes is **how you name a vertex** and **how you find its neighbors**.

### 🎨 Visual — A grid IS a graph (just in disguise)

```
The SAME thing, drawn two ways:


  AS A MATRIX (what the problem gives you):

         col 0   col 1   col 2
       ┌───────┬───────┬───────┐
  row 0│   1   │   1   │   0   │
       ├───────┼───────┼───────┤
  row 1│   1   │   0   │   1   │
       ├───────┼───────┼───────┤
  row 2│   0   │   1   │   1   │
       └───────┴───────┴───────┘


  AS A GRAPH (what your traversal code sees):

       (0,0)───(0,1)                       (1,2)
         │                                   │
       (1,0)                       (2,1)───(2,2)


  TWO CONNECTED COMPONENTS:
    Component A = {(0,0), (0,1), (1,0)}     ← L-shape, top-left
    Component B = {(1,2), (2,1), (2,2)}     ← L-shape, bottom-right

  Water cells (0,2), (1,1), (2,0) are NOT in the graph at all —
  they're absent from the vertex set, not just disconnected.


  RULES OF THE DISGUISE:
    • Each LAND cell (value '1') is a VERTEX.
    • Two cells share an EDGE iff they are 4-directional neighbors
      (up / right / down / left) AND both are LAND.
    • Water cells ('0') are NOT vertices — they are absent from the graph.
    • You never build the adjacency list explicitly. You compute
      neighbors ON THE FLY using (r ± 1, c) and (r, c ± 1).

  KEY INVARIANT:
    A 2-D grid problem = a graph problem where the vertex ID is the
    pair (r, c) and adjacency is a 1-step move on the grid. Once you
    see this, every BFS / DFS template you already know applies verbatim.
```

> **What's actually different from adjacency-list graphs?**

| Adjacency-list graph | Grid graph |
| --- | --- |
| Vertex ID is an `int` (`0..V-1`) | Vertex ID is a pair `(r, c)` |
| Neighbors via `adj.get(u)` | Neighbors via `(r±1, c)` / `(r, c±1)` |
| `visited` is `boolean[V]` | `visited` is `boolean[rows][cols]` (or mutate input) |
| Edges are explicit in input | Edges are implicit — bounds + cell value define them |

> **Why this realization matters:** once you internalize *"grid = graph,"* you stop memorizing 10 different "grid problems" and instead see them as **the same 3 traversals** (single-source BFS, single-source DFS, multi-source BFS) applied to a coordinate-based vertex space. The Striver problems G-7 through G-16 are all variations of one of those three traversals.

> **⚠️ First-timer trap — DO NOT confuse a grid matrix with an adjacency matrix.** They look identical (both are `int[][]`) but mean OPPOSITE things. A grid like the one above is **not** an adjacency matrix — the value `grid[1][1] = 0` does *not* mean "vertex 1 has no self-loop." It means *"the cell at coordinate (1, 1) is water."* See the table below — burn it into your head once and you'll never confuse them again.

### 🎨 Visual — Adjacency matrix vs Grid problem matrix (same shape, opposite meaning)

```
SAME 3x3 INT MATRIX, TWO COMPLETELY DIFFERENT INTERPRETATIONS:


  ╭─ ADJACENCY MATRIX ──────────────╮     ╭─ GRID PROBLEM MATRIX ───────────╮
  │                                 │     │                                 │
  │       col=v0  col=v1  col=v2    │     │       col 0   col 1   col 2     │
  │     ┌──────┬──────┬──────┐      │     │     ┌───────┬───────┬───────┐   │
  │ v0  │  0   │  1   │  1   │      │     │  r0 │   1   │   1   │   0   │   │
  │     ├──────┼──────┼──────┤      │     │     ├───────┼───────┼───────┤   │
  │ v1  │  1   │  0   │  1   │      │     │  r1 │   1   │   0   │   1   │   │
  │     ├──────┼──────┼──────┤      │     │     ├───────┼───────┼───────┤   │
  │ v2  │  1   │  1   │  0   │      │     │  r2 │   0   │   1   │   1   │   │
  │     └──────┴──────┴──────┘      │     │     └───────┴───────┴───────┘   │
  │                                 │     │                                 │
  │ INDICES ARE: vertex IDs         │     │ INDICES ARE: cell coordinates   │
  │              (0, 1, 2 are       │     │              (r, c) — the       │
  │               vertices)         │     │              vertex's NAME      │
  │                                 │     │                                 │
  │ CELL VALUE: 1 = there is an     │     │ CELL VALUE: 1 = this cell       │
  │             edge between        │     │             is LAND             │
  │             v_i and v_j         │     │             0 = this cell       │
  │             0 = no edge         │     │             is WATER            │
  │                                 │     │                                 │
  │ DIAGONAL [i][i]: self-loop      │     │ DIAGONAL [r][r]: just one cell  │
  │   (usually 0 — we don't add     │     │   of state — no special meaning │
  │    self-loops by default)       │     │                                 │
  │                                 │     │ ARE TWO CELLS CONNECTED?        │
  │ ARE v_i AND v_j CONNECTED?      │     │   Computed on the fly:          │
  │   Read the matrix: M[i][j]      │     │   - Are they 4-adjacent?        │
  │                                 │     │     |r-r'| + |c-c'| == 1        │
  │                                 │     │   - Are BOTH land?              │
  │                                 │     │     grid[r][c] == '1'           │
  │                                 │     │     grid[r'][c'] == '1'         │
  │                                 │     │                                 │
  ╰─────────────────────────────────╯     ╰─────────────────────────────────╯


KEY INVARIANT:
   The shape of the array tells you NOTHING about what it represents.
   You have to read the problem: "is M[i][j] describing a RELATIONSHIP
   between two things, or the STATE of one thing at position (i, j)?"

     • Adjacency matrix → describes a relationship (edge yes/no).
     • Grid matrix       → describes a state (land/water, color, height).

   Grid problems are NEVER adjacency matrices — the "edges" in a grid
   problem are 4-directional moves, and that rule lives in your traversal
   code, not in the array.
```

> **The "vertex always reaches itself" intuition is correct — but for the WRONG matrix.** In an adjacency matrix, yes, every vertex trivially reaches itself (the diagonal *could* be `1`). In a grid problem, the coordinate `(1, 1)` is just a *position*; its cell value tells you what's *at* that position, not whether it has a self-loop. The vertex always reaches itself implicitly in graph traversal — you just don't store that fact in the grid array.

---

**Universal grid utilities:**

```java
// 4-directional movement (up, right, down, left)
private static final int[] DR = {-1, 0, 1, 0};
private static final int[] DC = {0, 1, 0, -1};

// Bounds check
private boolean inBounds(int r, int c, int rows, int cols) {
    return r >= 0 && r < rows && c >= 0 && c < cols;
}
```

### 🎨 Visual — Why the direction arrays are paired the way they are

```
The 4 directions, indexed:

                              d = 0
                          (UP, r-1, c)
                                ▲
                                │
   d = 3 ◀── (LEFT, r, c-1)     ●     (RIGHT, r, c+1) ──▶ d = 1
                           CELL (r, c)
                                │
                                ▼
                        (DOWN, r+1, c)
                              d = 2


PAIRED ARRAYS:                INDEX REVEALS DIRECTION:
  DR = {-1,  0,  1,  0}         d=0 → (DR[0], DC[0]) = (-1,  0) → UP
  DC = { 0,  1,  0, -1}         d=1 → (DR[1], DC[1]) = ( 0,  1) → RIGHT
                                d=2 → (DR[2], DC[2]) = ( 1,  0) → DOWN
                                d=3 → (DR[3], DC[3]) = ( 0, -1) → LEFT


HOW THE LOOP WORKS:

  for (int d = 0; d < 4; d++) {
      int nr = r + DR[d];        ← compute next row
      int nc = c + DC[d];        ← compute next col
      // ...check bounds + cell value, then recurse / enqueue
  }

KEY INVARIANT:
   DR[d] and DC[d] together encode one of the 4 unit moves. The
   loop replaces 4 hardcoded recursive calls (up/down/left/right)
   with a clean iteration — and lets you swap in 8-directional
   movement by extending both arrays to 8 entries.
```

> **🧠 Mnemonic for memorizing DR / DC (Kapil's rule):**
>
> | Array | Affects | Stays 0 for | Active for |
> | --- | --- | --- | --- |
> | **DR** (delta row) | the ROW coordinate | LEFT / RIGHT moves (col changes, row doesn't) | UP / DOWN moves |
> | **DC** (delta col) | the COL coordinate | UP / DOWN moves (row changes, col doesn't) | LEFT / RIGHT moves |
>
> In every single move, **exactly one** of `(DR[d], DC[d])` is `±1`; **the other is 0**. That's the whole rule. If `DR` is non-zero, you're moving vertically. If `DC` is non-zero, you're moving horizontally. They never both fire at once (for 4-directional).
>
> **Fallback recipe if you forget the exact arrays in an interview:** don't memorize the literals — *reconstruct* them on the spot:
>
> 1. Write `UP, RIGHT, DOWN, LEFT` on scratch paper (any cyclic order works).
> 2. For each: ask *"does row change? by how much?"* → that's `DR[d]`. *"Does col change? by how much?"* → that's `DC[d]`.
> 3. Read off the answers vertically: `DR = {-1, 0, 1, 0}`, `DC = {0, 1, 0, -1}`. Done.
>
> No memorization required — just the 4 directions and 30 seconds of derivation.

---

### 🌀 Alternative: a single 4×2 array (often easier to remember)

Many engineers find a **single 4×2 array** more intuitive than two parallel 1-D arrays — because each `{dr, dc}` pair sits together on its own row, with no risk of misaligning the two arrays.

```java
private static final int[][] DIRS = {
    {-1,  0},      // UP    (row decreases, col stays)
    { 0,  1},      // RIGHT (row stays, col increases)
    { 1,  0},      // DOWN  (row increases, col stays)
    { 0, -1}       // LEFT  (row stays, col decreases)
};
```

```java
// The loop becomes even cleaner:
for (int[] dir : DIRS) {
    int nr = r + dir[0];
    int nc = c + dir[1];
    // ...check bounds + cell value, then recurse / enqueue
}
```

> **Why this is genuinely easier to memorize:**
>
> | Two-array style (`DR[]` + `DC[]`) | Single-array style (`DIRS[][]`) |
> | --- | --- |
> | Two arrays must stay aligned | One array — pairs can never misalign |
> | Visually you read **vertically** to get one direction | Visually you read **one row** to get one direction |
> | Memorize **two** literal sequences | Memorize **four** direction-pairs you can write in any order |
> | Common in competitive-programming code | Common in production Java code |
>
> **Order doesn't matter** — `DIRS` can be `{up, right, down, left}` or `{down, left, up, right}` or any permutation. As long as all four unit moves appear exactly once, every grid algorithm in this doc still works correctly.
>
> **Use whichever style you find easier.** Both compile to identical bytecode and both are accepted in interviews. The rest of this doc shows the `DR[]` / `DC[]` style because that's the Striver convention — but feel free to mentally substitute `DIRS[][]` when you write code yourself.

---

> **For 8-directional movement** (used in some chess/king-move problems and LC 1091 Shortest Path in Binary Matrix):
>
> ```java
> // Two-array style:
> int[] DR8 = {-1, -1, -1,  0, 0,  1, 1, 1};
> int[] DC8 = {-1,  0,  1, -1, 1, -1, 0, 1};
>
> // Or single 4×2 → 8×2 alternative:
> int[][] DIRS8 = {
>     {-1, -1}, {-1, 0}, {-1, 1},        // top-left, top, top-right
>     { 0, -1},          { 0, 1},        // left,            right
>     { 1, -1}, { 1, 0}, { 1, 1}         // bottom-left, bottom, bottom-right
> };
> ```
>
> The 8-directional case is where the `DIRS[][]` style really shines — laid out as a 3×3 "punched-out center" pattern (8 entries because the center `{0,0}` doesn't count as a move), it's nearly impossible to forget.

---

## 🧱 Grid DFS and BFS Templates — your building blocks before any problem

> **Before you attempt LC 200 (Number of Islands) or any grid problem, write these two templates from memory.** They are nearly identical to the adjacency-list versions you already know — the ONLY differences are:
>
> 1. **Vertex name** is `(r, c)` instead of an `int`
> 2. **Neighbors** come from the direction arrays (`DR[d]`, `DC[d]`) instead of `adj.get(u)`
>
> Everything else — mark-on-enqueue, bounds-checking, the BFS-vs-DFS marking discipline — is **identical** to the adjacency-list templates. If you've internalized the general BFS/DFS templates earlier in this doc, you've already done 90% of the work.

---

### Grid DFS — the canonical template

**The contract:** *"Given a starting cell `(r, c)` on a grid, recursively visit every cell reachable from it via 4-directional moves on land. Mark each visited cell so we never revisit it."*

**Steps in plain English:**

1. **Entry guard (combined check)** — if `(r, c)` is out of bounds OR is water (`'0'`) OR was already visited, return immediately. This single `if` does triple duty.
2. **Mark current cell as visited** — flip the value in-place (`'1' → '0'`) so subsequent visits hit the entry guard. *(If the problem forbids mutating input, use a separate `boolean[][] visited` array — same logic.)*
3. **Recurse into all 4 neighbors** using `DR[d]` / `DC[d]`. Each recursive call re-enters at Step 1, so out-of-bounds and water cells short-circuit cleanly.

```java
private void dfs(char[][] grid, int r, int c, int rows, int cols) {
    // Step 1 — combined entry guard does triple duty:
    //          out-of-bounds + water + already-visited
    if (!inBounds(r, c, rows, cols) || grid[r][c] != '1') {
        return;
    }
    // Step 2 — mark visited IN PLACE so we never revisit this cell
    grid[r][c] = '0';          // ← mark-on-entry (DFS analog of mark-on-enqueue)
    // Step 3 — recurse into 4 neighbors
    for (int d = 0; d < 4; d++) {
        dfs(grid, r + DR[d], c + DC[d], rows, cols);
    }
    // ← no explicit return; the for-loop exhausts naturally
    //   (this is the IMPLICIT base case — see recursion-fundamentals.md §3.4)
}
```

> **Why the bounds check goes FIRST in the `||` chain:** Java's `||` short-circuits left-to-right. If we wrote `grid[r][c] != '1' || !inBounds(...)`, then for any out-of-bounds call (e.g., `dfs(-1, 0, ...)`), the FIRST condition would try to read `grid[-1][0]` and throw `ArrayIndexOutOfBoundsException`. **Bounds-check first, value-check second** — always.

### 🎨 Visual — DFS sweeping a 2×2 island

```
Tiny grid (one 2x2 island):              Land cells: (0,0), (0,1), (1,0), (1,1)
                                          Start DFS from (0, 0).
  ┌───┬───┬───┐
  │ 1 │ 1 │ 0 │
  ├───┼───┼───┤                          KEY: ✓ = marked visited at this step
  │ 1 │ 1 │ 0 │                               ↳ = recurses INTO this call
  ├───┼───┼───┤                               ⊘ = returns immediately (guard fails)
  │ 0 │ 0 │ 0 │
  └───┴───┴───┘


DFS RECURSION TREE (left-to-right = order of recursive calls):

  dfs(0,0)  ✓ mark (0,0) = '0'
    │
    ├─↳ dfs(-1, 0)   ⊘ out of bounds
    │
    ├─↳ dfs( 0, 1)  ✓ mark (0,1) = '0'
    │     │
    │     ├─↳ dfs(-1, 1)   ⊘ out of bounds
    │     ├─↳ dfs( 0, 2)   ⊘ water (grid[0][2] = '0')
    │     ├─↳ dfs( 1, 1)  ✓ mark (1,1) = '0'
    │     │     │
    │     │     ├─↳ dfs( 0, 1)   ⊘ already '0' (just marked above)
    │     │     ├─↳ dfs( 1, 2)   ⊘ water
    │     │     ├─↳ dfs( 2, 1)   ⊘ water
    │     │     └─↳ dfs( 1, 0)  ✓ mark (1,0) = '0'
    │     │           │
    │     │           ├─↳ dfs( 0, 0)   ⊘ already '0'
    │     │           ├─↳ dfs( 1, 1)   ⊘ already '0'
    │     │           ├─↳ dfs( 2, 0)   ⊘ water
    │     │           └─↳ dfs( 1,-1)   ⊘ out of bounds
    │     │     ← (1, 0) returns
    │     ← (1, 1) returns
    │     └─↳ dfs( 0, 0)   ⊘ already '0'
    │     ← (0, 1) returns
    │
    ├─↳ dfs( 1, 0)   ⊘ already '0' (marked deep in the recursion above)
    │
    └─↳ dfs( 0,-1)   ⊘ out of bounds
  ← (0, 0) returns — DFS complete.

Final grid (all 4 land cells flipped to '0'):

  ┌───┬───┬───┐
  │ 0 │ 0 │ 0 │
  ├───┼───┼───┤
  │ 0 │ 0 │ 0 │
  ├───┼───┼───┤
  │ 0 │ 0 │ 0 │
  └───┴───┴───┘


KEY INVARIANT:
   Every land cell reachable from (0, 0) is visited exactly once.
   The mark-on-entry (Step 2) is what prevents infinite recursion —
   the very NEXT time any other call tries to re-enter a marked cell,
   the entry guard short-circuits and returns immediately.
```

---

### Grid BFS — the canonical template

**The contract:** *"Given a starting cell `(r, c)`, visit every reachable land cell **in order of distance from the start**, using a queue. Mark cells the moment you enqueue them — NEVER when you dequeue them."*

**Steps in plain English:**

1. **Mark the start as visited AND enqueue it** — both in the same breath. This is the mark-on-enqueue contract; see "BFS marking-discipline" callout earlier in this doc for why mark-on-poll is a bug.
2. **While the queue is non-empty:** poll a cell, then for each of its 4 neighbors —
   - Skip if out-of-bounds, water, or already visited.
   - Otherwise **mark visited FIRST**, then enqueue.

```java
private void bfs(char[][] grid, int startR, int startC, int rows, int cols) {
    Queue<int[]> queue = new ArrayDeque<>();
    // Step 1 — mark start visited AND enqueue (same breath)
    grid[startR][startC] = '0';                  // ← mark-on-enqueue
    queue.offer(new int[]{startR, startC});

    // Step 2 — process the queue layer by layer
    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        int r = cell[0];
        int c = cell[1];
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d];
            int nc = c + DC[d];
            // Combined guard: bounds + value-is-land.
            // (Because we mark-on-enqueue, already-visited cells are '0' too —
            //  so this check covers ALL three rejections in one expression.)
            if (inBounds(nr, nc, rows, cols) && grid[nr][nc] == '1') {
                grid[nr][nc] = '0';              // ← mark BEFORE enqueueing
                queue.offer(new int[]{nr, nc});  // ← only enqueue once marked
            }
        }
    }
}
```

> **Why mark-on-enqueue is critical (not mark-on-poll):** if you wait until you `poll()` a cell to mark it visited, the SAME cell can be enqueued multiple times from multiple neighbors before its first poll. For a single component of `N` cells with average degree `4`, mark-on-poll can balloon the queue from `O(N)` to `O(4N) = O(N)` — same big-O but a constant-factor blow-up AND a real correctness bug in distance-tracking BFS where each enqueue carries `dist + 1` (you'd record the wrong distance). **Always mark the moment you enqueue.**

### 🎨 Visual — BFS layer-by-layer on the same 2×2 island

```
Same grid, BFS from (0, 0). Annotated with the queue state after each step.


Step 0: INITIAL                                   QUEUE: [(0,0)]
                                                   marked: (0,0)
  ┌───┬───┬───┐
  │ 0 │ 1 │ 0 │      ← (0,0) marked '0' at enqueue
  ├───┼───┼───┤
  │ 1 │ 1 │ 0 │
  ├───┼───┼───┤
  │ 0 │ 0 │ 0 │
  └───┴───┴───┘


Step 1: POLL (0,0). Examine 4 neighbors:           QUEUE: [(0,1), (1,0)]
   (-1, 0) ⊘ out of bounds                          marked: (0,0), (0,1), (1,0)
   ( 0, 1) ✓ land → MARK + ENQUEUE
   ( 1, 0) ✓ land → MARK + ENQUEUE
   ( 0,-1) ⊘ out of bounds

  ┌───┬───┬───┐
  │ 0 │ 0 │ 0 │
  ├───┼───┼───┤
  │ 0 │ 1 │ 0 │
  ├───┼───┼───┤
  │ 0 │ 0 │ 0 │
  └───┴───┴───┘


Step 2: POLL (0,1). Examine 4 neighbors:           QUEUE: [(1,0), (1,1)]
   (-1, 1) ⊘ out of bounds                          marked: (0,0), (0,1), (1,0), (1,1)
   ( 0, 2) ⊘ water (already '0')
   ( 1, 1) ✓ land → MARK + ENQUEUE
   ( 0, 0) ⊘ already marked '0'

  ┌───┬───┬───┐
  │ 0 │ 0 │ 0 │
  ├───┼───┼───┤
  │ 0 │ 0 │ 0 │
  ├───┼───┼───┤
  │ 0 │ 0 │ 0 │
  └───┴───┴───┘


Step 3: POLL (1,0). All neighbors rejected:        QUEUE: [(1,1)]
   ( 0, 0) ⊘ already marked
   ( 1, 1) ⊘ already marked
   ( 2, 0) ⊘ water
   ( 1,-1) ⊘ out of bounds


Step 4: POLL (1,1). All neighbors rejected:        QUEUE: []
   ( 0, 1) ⊘ already marked
   ( 1, 2) ⊘ water
   ( 2, 1) ⊘ water
   ( 1, 0) ⊘ already marked


QUEUE EMPTY → BFS done. 4 cells visited.


KEY INVARIANT:
   Each cell is marked exactly ONCE — the moment it enters the queue.
   That guarantees the queue holds at most O(N) cells total and each
   cell is processed exactly once. If we'd marked at POLL time instead,
   (1, 1) could have been enqueued TWICE (from (0,1) and (1,0)) before
   being polled — wasted work and (in distance-tracking BFS) wrong answers.
```

---

### 🧭 Marking strategy — in-place vs separate `boolean[][] visited`

Both templates above use **in-place marking** (`grid[r][c] = '0'`). That's idiomatic for LC 200, but it's not the only valid choice. A separate `boolean[][] visited` array is equally accepted and sometimes the better engineering call. Here's the decision aid.

**Two ways to track visited cells on a grid:**

| Strategy | Extra space | Modifies input? | When to pick it |
| --- | --- | --- | --- |
| **In-place** (`grid[r][c] = '0'`) | O(1) | Yes — destroys input | LC editorial style; you own the grid and don't need it after |
| **Separate `boolean[][] visited`** | O(rows × cols) | No — input untouched | Problem says "don't modify input" / grid is `final` / values aren't a clean binary / production-style code |

**The asymptotic complexity is IDENTICAL either way** — the recursion stack (DFS) or queue (BFS) is already O(rows × cols) in the worst case, so adding a visited array doesn't change Big-O. Don't let "saves O(mn) space" pressure you into the in-place variant if you're not 100% confident with it.

**🧠 Why in-place works:** the value `'0'` becomes overloaded — it means BOTH "originally water" AND "already-visited land." The entry guard `grid[r][c] != '1'` rejects both, which is exactly what we want: *"don't enter this cell, regardless of why."*

**The visited-array variant** (drop-in replacement for the DFS template):

```java
private void dfs(char[][] grid, boolean[][] visited, int r, int c, int rows, int cols) {
    // entry guard now has THREE checks: bounds, water, visited
    if (!inBounds(r, c, rows, cols) || grid[r][c] != '1' || visited[r][c]) {
        return;
    }
    visited[r][c] = true;                  // ← mark in the separate array, grid stays intact
    for (int d = 0; d < 4; d++) {
        dfs(grid, visited, r + DR[d], c + DC[d], rows, cols);
    }
}
```

Same shape, same logic — just one extra parameter and one extra check.

**🎯 Interview recommendation (the part future-Kapil should re-read on the morning of an onsite):**

1. **Pick the variant your hands are confident with.** A correct visited-array solution in 18 minutes BEATS a buggy in-place attempt at minute 22. Confidence under pressure > marginal elegance.
2. **State the trade-off upfront.** That single sentence is what signals senior thinking — NOT which variant you actually pick:

   > *"For visited tracking, I'll use a separate `boolean[][] visited` — I prefer not to mutate the caller's input, which matches production Java style. The in-place trick would save O(rows × cols) auxiliary space — happy to switch if you'd prefer that."*

3. **If the interviewer pushes back on space:** that's a prompt, not a critique. Calmly switch to in-place marking on the spot — you've now demonstrated both, which is +1 not -1.

**Framing matters as much as the choice:**

| ❌ Junior framing | ✅ Senior framing (same choice!) |
| --- | --- |
| *"I'll use a visited array because I'm more comfortable with it."* | *"I'll use a visited array — backend Java code should avoid mutating caller inputs."* |
| *"I think in-place is cleaner."* | *"I'll mark in place — saves O(mn) extra space, and the problem doesn't require preserving the input."* |

Same code. Different signal. **Pick your words deliberately.**

> **Lesson learned (May 2026):** Naming the trade-off explicitly is worth more interview points than picking the marginally more elegant variant. The interviewer is watching for *"did this candidate see the choice?"* — not *"did this candidate pick the same one I would?"*

**🧾 TL;DR for this template choice:**

- Both variants are valid. Same Big-O.
- In-place = O(1) extra space, destructive. Idiomatic for LC.
- Visited array = O(mn) extra space, non-destructive. Cleaner for production / backend code.
- **In an interview: pick whichever you can write confidently AND verbalize the trade-off upfront.** Both choices are senior-grade if you name the trade-off; both are junior-grade if you don't.

---

### 🧭 Top-down vs bottom-up DFS — `visited[][]` works for both styles

The DFS template above is **top-down**: state (the `visited[][]` array, `r`/`c`) flows DOWN through parameters; the method returns `void`; the "answer" lives in a class-level counter that the outer loop increments. Many grid problems instead need **bottom-up DFS** — where each call returns a *computed value* (an area, a max, a count) and parents combine children's returns.

**The key insight: the `visited[][]` mechanism is BYTE-FOR-BYTE IDENTICAL in both styles.** You don't learn a new pattern — visited tracking is shared mutable state, and it propagates the same way regardless of whether the DFS returns void or a value.

**Steps in plain English (bottom-up variant — LC 695 Max Area of Island):**

1. **Entry guard** — same as top-down: out-of-bounds OR water OR already-visited → return **`0`** (nothing reachable from here).
2. **Mark visited** — same as top-down, `visited[r][c] = true`.
3. **Count THIS cell** as area `1`.
4. **Recurse into 4 neighbors and ADD their returns** to the running area — this is the "bubble up" step.
5. **Return** the accumulated area to the caller.

```java
public int dfs(int r, int c, char[][] grid, boolean[][] visited, int m, int n) {
    // Step 1 — entry guard returns 0 (not void) for bottom-up
    if (!inBound(r, c, m, n) || grid[r][c] == '0' || visited[r][c]) {
        return 0;
    }
    // Step 2 — same visited mutation as top-down
    visited[r][c] = true;
    // Step 3 — count THIS cell
    int area = 1;
    // Step 4 — bubble up each child's return
    for (int[] d : DIRS) {
        area += dfs(r + d[0], c + d[1], grid, visited, m, n);
    }
    // Step 5 — return computed value
    return area;
}
```

Notice: lines 1–2 are identical to the top-down template. Only the `return` type and the accumulation logic changed.

| Style | What flows DOWN as parameters | What flows UP via return | `visited[][]` handling | Typical problem |
| --- | --- | --- | --- | --- |
| **Top-down DFS** | `r`, `c`, `grid`, `visited` | nothing (`void`) — outer counter holds the answer | Mark on entry, never read return | LC 200 — count components |
| **Bottom-up DFS** | `r`, `c`, `grid`, `visited` | the area / max / count reachable from this cell | Mark on entry, ADD children's returns | LC 695 — max island area |

**🧠 Why does the SAME `visited[][]` work in both?** Because Java passes the array reference *by value* — every recursive call (top-down or bottom-up) sees the SAME underlying array on the heap, and mutations to its contents propagate to all callers/callees. **Full mechanics + the three strategies for opting out (defensive copy / deep copy / immutability) are covered in `../../JavaBackend/DeepDive/java-pass-by-value-semantics.md`** — that's a Java-language topic, not a grid topic, so it lives in its own deep dive.

**Mental shortcut for picking the style:**

| If the problem asks for... | Use... | Outer loop does... |
| --- | --- | --- |
| **Count of components / islands** | Top-down (`void`) + outer counter | `count++` on each discovery |
| **Max / min / sum over components** | Bottom-up (returns `int`) | `max = Math.max(max, dfs(...))` |
| **Existence ("is there a path")** | Bottom-up (returns `boolean`) | short-circuit on `true` |

---

### When to use DFS vs BFS on a grid

| Use **DFS** when | Use **BFS** when |
| --- | --- |
| Counting components / islands (LC 200, LC 695) | Shortest path from a cell |
| Flood fill / recolor (LC 733) | Multi-source spreading (LC 994 Rotten Oranges) |
| Path / shape recording (LC 694 Distinct Islands) | Distance from nearest source (LC 542 01 Matrix) |
| Stack depth is fine (small/medium grids) | Need strict level-by-level processing |
| You don't care about distance from start | Distance matters |
| Code is shorter (recursion implicit) | Need explicit queue for layer state |

> **Default rule for grid problems:** if the problem asks for **distance / shortest path / "minimum minutes"** → **BFS**. If it asks for **existence / count / shape / fill** → **DFS** (usually cleaner code).
>
> **Stack-overflow caveat:** for grids larger than ~10⁶ cells (e.g., 1000×1000 worst case), recursive DFS can blow the JVM stack. If you hit `StackOverflowError`, switch to **iterative DFS with an explicit stack** (same template as iterative graph DFS, just `int[]` cells instead of `int` vertex IDs).

---

### 🐞 Common bugs hall of fame — grid DFS

Before writing your first grid DFS from scratch, internalize these four traps. **Every one was discovered the hard way during the May 2026 LC 200 attempt.** They look trivial in hindsight, but each one will silently break your algorithm without crashing — meaning unit-test failure, not a clean compile error.

| # | The bug | What it does | The fix |
| --- | --- | --- | --- |
| 1 | `int n = grid[1].length;` instead of `grid[0].length` | Crashes on 1-row grids — `grid[1]` is out of bounds when `m == 1` | Always `grid[0].length` (or guard `grid.length == 0` first) |
| 2 | Outer guard checks only `!visited[i][j]`, forgets `grid[i][j] == '1'` | **Every water cell counted as its own island** — wildly wrong count, often passes small tests and fails larger ones | Guard MUST be `grid[i][j] == '1' && !visited[i][j]` — see the 🧠 two-loops mental model in Sub-Pattern 1 for why |
| 3 | `dfs(r + dr, r + dc, ...)` — typo using `r` twice instead of `r` + `c` | Wrong adjacency / OOB crashes — algorithm visits cells that aren't 4-adjacent. Silent on small inputs, garbage on real ones | Every direction call MUST be `dfs(r + dr, c + dc, ...)` — eyeball it before submit |
| 4 | Class field named `dr` AND local `int dr = d[0]` inside the loop | Variable shadowing — legal Java but confusing in interview review; the interviewer will pause and squint | Rename the class field to `DIRS` (or `dir`). Local row/col deltas stay as `dr`/`dc` |

**The "verify before submit" checklist for every grid DFS / BFS:**

1. **Dims:** `m = grid.length`, `n = grid[0].length`. Never `grid[1]`.
2. **Outer guard:** `grid[i][j] == '1' && !visited[i][j]` — say out loud *"land AND unvisited."*
3. **Direction call:** `dfs(r + dr, c + dc, ...)`. Eyeball it — BOTH prefixes must differ. Never `r+dr, r+dc` or `r+dr, r+dr`.
4. **No shadowing:** Class field is `DIRS`. Local deltas are `dr`/`dc`.

> **Lesson learned (May 2026):** Bug #3 (the `r + dc` typo) is the most insidious — it doesn't always crash, it just silently visits the wrong neighbors. LC's grader caught it on a 4×5 test case. A real production grid algorithm with this bug would produce garbage answers for some inputs and pass code review. Treat the direction-call line as **the single line that needs the most eyeball scrutiny in any grid DFS.**
>
> **Why these bugs cluster:** `dr`/`dc`/`r`/`c` are all 1-2 character symbols — your eyes glaze over them. Add `i`/`j` from the outer loop and you have six near-identical names interacting on the same line. The fix isn't "be more careful" — it's a structural checklist (above) you run mechanically before hitting submit.

---

> **🎯 You're now ready.** With the DFS and BFS templates above in muscle memory, every problem in the next 5 sub-patterns (Number of Islands, Multi-source BFS, Reverse-direction BFS, Flood Fill, Distinct Islands) is **one of these two templates with a small twist**. Go solve LC 200 first — when you're done, come back and read Sub-Pattern 1 to compare your solution to the canonical one.

---

### Sub-Pattern 1: Counting Connected Components on a Grid

**Problems:** LC 200 Number of Islands (G-8), LC 547 Number of Provinces (G-7), LC 695 Max Area of Island.

**Pattern:** for each unvisited "land" cell, run a BFS/DFS that floods the entire island, then increment the count.

> **🧠 Mental model — two loops, two DIFFERENT questions.**
>
> The outer `for i, for j` and the inner DFS recursion look superficially similar (both touch cells, both consult `visited[][]`), but they're asking **fundamentally different questions** — and the required guards are different too. Most first-timer bugs in grid problems come from conflating the two.
>
> | Loop | Role | Question it asks | Required checks | Frequency |
> | --- | --- | --- | --- | --- |
> | **Outer double-`for`** | Discovery — find each island's entry point | *"Should I START a new DFS from this cell?"* | Cell must be **LAND** ✅ AND **unvisited** ✅ | One pass over every cell (O(rows × cols)) |
> | **Inner DFS / BFS** | Consumption — swallow the whole island | *"Should I CONTINUE expanding into this neighbor?"* | Cell must be **in-bounds** ✅ AND **LAND** ✅ AND **unvisited** ✅ | Runs once *per island* |
>
> Together they guarantee each cell is touched exactly once across the whole algorithm → **O(rows × cols)** total.

> **⚠️ The trap — water cells NEVER get marked visited.** Many first-timers write the outer guard as just `!visited[i][j]` thinking "the visited array tells me everything I need to skip." It doesn't. The DFS bails on water cells BEFORE reaching `visited[r][c] = true`, so the visited array **cannot distinguish water from unvisited land**. Only the grid value `'1'` vs `'0'` can.
>
> If the outer guard is just `!visited[i][j]`, every water cell becomes its own "island." Trace on a tiny grid:
>
> ```
> grid = 1 0     Outer scan with WRONG guard (!visited only):
>        0 0       (0,0) → is=1, DFS marks (0,0)
>                  (0,1) → is=2  ❌  (water, but !visited is still true)
>                  (1,0) → is=3  ❌
>                  (1,1) → is=4  ❌
>              
>                Result: 4 islands. Correct answer: 1.
> ```
>
> **The fix:** outer guard must be `grid[i][j] == '1' && !visited[i][j]`. Always both, never just one. Say out loud whenever you write the outer scan: *"Land **AND** unvisited — both required."*
>
> > **Lesson learned the hard way (May 2026):** Caught on the first LC 200 attempt with a separate visited array — got 4 islands on a 1×2 grid that had 1. The mistake doesn't happen with **in-place marking** (the `'0'` overload absorbs both "water" and "visited" into the single grid check), but the moment you use a separate `boolean[][] visited`, the outer guard MUST include the grid-value check. This is the #1 trap of the visited-array variant. See also: 🐞 *Common bugs hall of fame* in the Grid Templates section.

**LC 200 — Steps in plain English:**

1. Iterate every `(r, c)` in the grid.
2. If `grid[r][c] == '1'` and not visited → start a DFS, mark every connected `'1'` as visited, increment count.
3. Return count.

```java
public int numIslands(char[][] grid) {
    int rows = grid.length;
    int cols = grid[0].length;
    int count = 0;
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                dfs(grid, r, c, rows, cols);
                count++;
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c, int rows, int cols) {
    if (!inBounds(r, c, rows, cols) || grid[r][c] != '1') {
        return;
    }
    grid[r][c] = '0';                      // mark visited IN PLACE
    for (int d = 0; d < 4; d++) {
        dfs(grid, r + DR[d], c + DC[d], rows, cols);
    }
}
```

### 🎨 Visual — DFS sweeping one island, then the outer loop finding the next

```
Input grid (3 islands):                Vertex view of island A:

  ┌───┬───┬───┬───┬───┐                   (0,0)── (0,1)
  │ 1 │ 1 │ 0 │ 0 │ 1 │                    │
  ├───┼───┼───┼───┼───┤                   (1,0)
  │ 1 │ 0 │ 0 │ 1 │ 1 │
  ├───┼───┼───┼───┼───┤                Vertex view of island B:
  │ 0 │ 0 │ 0 │ 0 │ 0 │
  ├───┼───┼───┼───┼───┤                   (0,4)
  │ 0 │ 1 │ 0 │ 0 │ 0 │                    │
  └───┴───┴───┴───┴───┘                   (1,4)── (1,3)


─────────────────────────────────────────────────────────────────────
OUTER LOOP starts: (r, c) = (0, 0)
  grid[0][0] == '1' AND unvisited → RING THE BELL!
  count becomes 1. Launch DFS from (0, 0).
─────────────────────────────────────────────────────────────────────

  DFS recursion tree for island A (starting from (0,0)):

    dfs(0,0) ──── mark '0', try 4 neighbors
      ├── dfs(-1, 0) ✗ out of bounds, return
      ├── dfs( 0, 1) ──── mark '0', try 4 neighbors
      │     ├── dfs(-1, 1) ✗ out of bounds
      │     ├── dfs( 0, 2) ✗ value '0', return
      │     ├── dfs( 1, 1) ✗ value '0', return
      │     └── dfs( 0, 0) ✗ already '0' (marked!), return
      ├── dfs( 1, 0) ──── mark '0', try 4 neighbors
      │     ├── dfs( 0, 0) ✗ already '0'
      │     ├── dfs( 1, 1) ✗ value '0'
      │     ├── dfs( 2, 0) ✗ value '0'
      │     └── dfs( 1,-1) ✗ out of bounds
      └── dfs( 0,-1) ✗ out of bounds


Grid AFTER island A's DFS completes:           ← island A erased
  ┌───┬───┬───┬───┬───┐
  │ 0 │ 0 │ 0 │ 0 │ 1 │   ← (0,4) still LAND (different island)
  ├───┼───┼───┼───┼───┤
  │ 0 │ 0 │ 0 │ 1 │ 1 │
  ├───┼───┼───┼───┼───┤
  │ 0 │ 0 │ 0 │ 0 │ 0 │
  ├───┼───┼───┼───┼───┤
  │ 0 │ 1 │ 0 │ 0 │ 0 │
  └───┴───┴───┴───┴───┘

─────────────────────────────────────────────────────────────────────
OUTER LOOP continues at (0, 1), (0, 2), (0, 3) — all '0', skip.
At (0, 4): grid[0][4] == '1' AND unvisited → count becomes 2.
DFS swallows island B: (0,4), (1,4), (1,3) — all marked '0'.

OUTER LOOP eventually reaches (3, 1) → count becomes 3.
DFS marks (3,1). No reachable neighbors → returns immediately.
─────────────────────────────────────────────────────────────────────


FINAL ANSWER: count = 3.

KEY INVARIANT:
   The outer loop visits each cell exactly once. Whenever it spots
   unvisited land, that cell MUST be the entry point of a NEW
   island — because if it belonged to a previously-counted island,
   the inner DFS would have already marked it.
```

> **In-place marking vs separate `visited` array:** marking the grid itself is fine **if you're allowed to mutate input**. If not (interviewer asks "don't mutate input"), use `boolean[][] visited`.

> **A subtle elegance worth noticing:** the DFS doesn't track an explicit `visited[][]` array — it uses the cell value itself (`'1'` → `'0'`) as a visited marker. The bounds-and-value check at the top of the recursive function does triple duty: out-of-bounds guard, water-cell guard, AND already-visited guard. This is why grid DFS feels "shorter" than generic graph DFS.

---

### Sub-Pattern 2: Multi-Source BFS [G-10, G-13, G-15]

**Problems:** LC 994 Rotten Oranges, LC 542 0/1 Matrix (nearest zero), LC 1020 Number of Enclaves.

**Pattern:** instead of starting BFS from one source, **enqueue ALL sources first**, then BFS outward. Each cell's "distance" becomes the minimum distance from ANY source.

> **First-timer intuition — what does "multi-source" actually mean?**
>
> Picture **multiple pebbles dropped into a pond at the exact same instant.** Each pebble starts its own ripple. The ripples expand outward, and wherever two ripples meet, the cell was already reached by whichever pebble was closer. You don't care WHICH pebble reached a cell first — you only care about the **time of first arrival** (= shortest distance from the nearest source).
>
> Mechanically, this is **just standard BFS**, but the queue starts pre-loaded with ALL sources at distance 0 instead of one source. Everything else (mark-on-enqueue, layer-by-layer expansion, O(V + E) total work) is identical to single-source BFS.

### 🎨 Visual — Multi-source BFS as concentric ripples

```
LC 994 Rotten Oranges. Initial grid (R = rotten, F = fresh, . = empty):

         col 0  col 1  col 2  col 3
       ┌──────┬──────┬──────┬──────┐
  r=0  │  R   │  F   │  F   │  .   │
       ├──────┼──────┼──────┼──────┤
  r=1  │  F   │  F   │  F   │  R   │
       ├──────┼──────┼──────┼──────┤
  r=2  │  F   │  F   │  F   │  F   │
       └──────┴──────┴──────┴──────┘

Two rotten oranges at (0,0) and (1,3) → BOTH go in the queue with t=0.


MINUTE 0  (initial state):           queue = [(0,0,t=0), (1,3,t=0)]

  ┌────┬────┬────┬────┐
  │ R0 │ F  │ F  │ .  │             KEY: cell label "Rk" means
  ├────┼────┼────┼────┤                  "became rotten at minute k"
  │ F  │ F  │ F  │ R0 │
  ├────┼────┼────┼────┤
  │ F  │ F  │ F  │ F  │
  └────┴────┴────┴────┘

MINUTE 1  (pop both t=0 oranges, infect their fresh neighbors):

  ┌────┬────┬────┬────┐
  │ R0 │ R1 │ F  │ .  │            (0,0) infects (0,1), (1,0)
  ├────┼────┼────┼────┤            (1,3) infects (0,3 is '.'), (1,2), (2,3)
  │ R1 │ F  │ R1 │ R0 │
  ├────┼────┼────┼────┤
  │ F  │ F  │ F  │ R1 │
  └────┴────┴────┴────┘

MINUTE 2  (pop all t=1 oranges, infect their fresh neighbors):

  ┌────┬────┬────┬────┐
  │ R0 │ R1 │ R2 │ .  │
  ├────┼────┼────┼────┤
  │ R1 │ R2 │ R1 │ R0 │
  ├────┼────┼────┼────┤
  │ R2 │ F  │ R2 │ R1 │
  └────┴────┴────┴────┘
                                    Notice (1,1) — TWO ripples could
                                    reach it (from R1 at (0,1) and
                                    R1 at (1,0) and R1 at (1,2)).
                                    BFS marks it the FIRST time it's
                                    seen; the other two attempts find
                                    it already rotten and skip.

MINUTE 3  (pop all t=2 oranges, infect remaining fresh):

  ┌────┬────┬────┬────┐
  │ R0 │ R1 │ R2 │ .  │
  ├────┼────┼────┼────┤
  │ R1 │ R2 │ R1 │ R0 │
  ├────┼────┼────┼────┤
  │ R2 │ R3 │ R2 │ R1 │
  └────┴────┴────┴────┘            (2,1) infected from (2,0) or (2,2) at t=2.


ANSWER: max minute = 3.


KEY INVARIANT:
   Because BFS expands layer by layer AND we marked every fresh
   neighbor at minute t+1 the moment any rotten cell at minute t
   reached it, every cell records the MINIMUM time to be infected
   from the nearest source. Multiple sources don't break BFS — they
   just give it a "head start" from multiple points.
```

> **Why multi-source BFS beats "BFS from every source, take the min":**
>
> | Approach | Time complexity | Why |
> | --- | --- | --- |
> | BFS from each of `S` sources separately | **O(S × (rows × cols))** | Each source re-explores the whole grid |
> | Multi-source BFS (one BFS, queue pre-loaded) | **O(rows × cols)** | Each cell is dequeued exactly once across the entire BFS |
>
> Single-source BFS already computes "shortest distance from source." Multi-source BFS computes "shortest distance from **any** source" — for FREE, in the same O(V+E). This is one of those rare algorithmic wins where the generalization is strictly cheaper than the naïve "do it S times" approach.

**LC 994 Rotten Oranges — Steps in plain English:**

1. Enqueue every initially-rotten orange with time = 0.
2. Track fresh count.
3. BFS layer by layer. Each minute, all rotten oranges infect their fresh neighbors.
4. Return max time. If any fresh orange remains, return `-1`.

```java
public int orangesRotting(int[][] grid) {
    int rows = grid.length;
    int cols = grid[0].length;
    Queue<int[]> queue = new ArrayDeque<>();
    int fresh = 0;
    // Enqueue all initially-rotten oranges as sources
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == 2) {
                queue.offer(new int[]{r, c, 0});
            } else if (grid[r][c] == 1) {
                fresh++;
            }
        }
    }
    int maxTime = 0;
    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        int r = cell[0];
        int c = cell[1];
        int t = cell[2];
        maxTime = Math.max(maxTime, t);
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d];
            int nc = c + DC[d];
            if (inBounds(nr, nc, rows, cols) && grid[nr][nc] == 1) {
                grid[nr][nc] = 2;
                fresh--;
                queue.offer(new int[]{nr, nc, t + 1});
            }
        }
    }
    return fresh == 0 ? maxTime : -1;
}
```

> **Mental hook:** *"Distance from ALL these starting points to every cell"* → multi-source BFS, not multiple single-source BFS calls.

---

### Sub-Pattern 3: Reverse-Direction BFS [G-14, G-15]

**Problems:** LC 130 Surrounded Regions, LC 1020 Number of Enclaves.

**The trick:** instead of finding "regions that can't reach the boundary", find "regions that CAN reach the boundary" (start BFS from boundary cells), then everything NOT reached is the answer.

> **First-timer intuition — the "complement trick":**
>
> Some questions phrase the answer as "**find the things that can't escape**." That's a hard set to characterize directly — you'd need to prove a negative for each region. The complement-trick says: *flip the question.* It's much easier to find "things that **can** escape" (just BFS/DFS from the boundary), and everything left over is your answer.
>
> | Phrasing in the problem | Hard way (direct) | Easy way (complement / reverse BFS) |
> | --- | --- | --- |
> | "Find regions surrounded by X" | Run BFS from every interior O and check whether it ever touches the boundary | BFS from every boundary O, mark "escapable." Everything still O afterward IS surrounded. |
> | "Count cells that can't reach the boundary" (LC 1020) | Same painful per-region check | Same flip — BFS from boundary land, everything still land is enclaved |
>
> The complement-trick is a general algorithmic pattern, not a grid quirk. It also shows up in *"find rooms NOT reachable from any gate" → BFS from all gates,* and in *"unreachable nodes" → DFS from sources and complement.* Whenever you read **"the answer is what's left over,"** suspect this trick.

### 🎨 Visual — LC 130 Surrounded Regions, the complement way

The example uses a 5×6 board with **two disjoint O-regions** so you can see *both* outcomes in one picture:

- **Region A** — 4 connected O's at `(0,2)─(1,2)─(1,3)─(2,3)`. Only the head cell `(0,2)` sits on the boundary; the other 3 are interior. The whole chain escapes because the head is the seed for a DFS that cascades through all four.
- **Region B** — 2 connected O's at `(3,1)─(3,2)`. Both are interior, **neither touches any boundary**. No DFS ever reaches it → captured wholesale.

```
INPUT board (5×6):                       PHASE 1 — DFS from every boundary O,
                                         mark each visited cell as '#'.
  ┌───┬───┬───┬───┬───┬───┐
  │ X │ X │ O │ X │ X │ X │              The ONLY boundary O is (0,2) at the
  ├───┼───┼───┼───┼───┼───┤              top edge. DFS from there cascades
  │ X │ X │ O │ O │ X │ X │  ← Region A  through Region A's whole chain:
  ├───┼───┼───┼───┼───┼───┤                (0,2) → (1,2) → (1,3) → (2,3)
  │ X │ X │ X │ O │ X │ X │              All 4 cells of Region A become '#'.
  ├───┼───┼───┼───┼───┼───┤
  │ X │ O │ O │ X │ X │ X │  ← Region B  Region B has NO boundary cell to
  ├───┼───┼───┼───┼───┼───┤              seed from, so the multi-source DFS
  │ X │ X │ X │ X │ X │ X │              never enters it. Both cells stay 'O'.
  └───┴───┴───┴───┴───┴───┘
                                           ┌───┬───┬───┬───┬───┬───┐
Two disjoint O-regions:                    │ X │ X │ # │ X │ X │ X │
  Region A — 4 cells, head at (0,2)        ├───┼───┼───┼───┼───┼───┤
             touches the top boundary      │ X │ X │ # │ # │ X │ X │
             so the whole chain escapes    ├───┼───┼───┼───┼───┼───┤
  Region B — 2 cells, fully interior       │ X │ X │ X │ # │ X │ X │
             no boundary contact at all    ├───┼───┼───┼───┼───┼───┤
             → captured                    │ X │ O │ O │ X │ X │ X │ ← Region B
                                           ├───┼───┼───┼───┼───┼───┤   untouched
                                           │ X │ X │ X │ X │ X │ X │
                                           └───┴───┴───┴───┴───┴───┘


PHASE 2 — one final sweep:                FINAL board:
   • '#' → flip back to 'O'
     (Region A survives — it escaped)       ┌───┬───┬───┬───┬───┬───┐
   • remaining 'O' → flip to 'X'            │ X │ X │ O │ X │ X │ X │
     (Region B was surrounded)              ├───┼───┼───┼───┼───┼───┤
                                            │ X │ X │ O │ O │ X │ X │ ← Region A
                                            ├───┼───┼───┼───┼───┼───┤   restored
                                            │ X │ X │ X │ O │ X │ X │   (4 saved
                                            ├───┼───┼───┼───┼───┼───┤    by ONE
                                            │ X │ X │ X │ X │ X │ X │   boundary
                                            ├───┼───┼───┼───┼───┼───┤   seed)
                                            │ X │ X │ X │ X │ X │ X │ ← Region B
                                            └───┴───┴───┴───┴───┴───┘   captured


KEY INVARIANT:
   A region survives iff ANY one of its cells touches the boundary.
   Region A → 4 cells saved by a single boundary seed (DFS follows the chain).
   Region B → 0 boundary contact → captured wholesale.

   The complement trick converts the hard "is this whole region surrounded?"
   per-region check (which would need a separate flood + boundary test for
   every connected component) into a single multi-source DFS sweep from the
   boundary. The leftover O's ARE the answer — by exclusion, not by direct
   detection.
```

**LC 130 Surrounded Regions — Steps in plain English:**

1. Run BFS/DFS from every `'O'` on the **boundary** (rows 0 and `rows-1`, cols 0 and `cols-1`), marking those as safe (e.g., temporary `'#'`).
2. After traversal: all remaining `'O'` are surrounded → flip to `'X'`. All `'#'` → flip back to `'O'`.

```java
public void solve(char[][] board) {
    int rows = board.length;
    int cols = board[0].length;
    // Mark boundary-connected Os as safe
    for (int r = 0; r < rows; r++) {
        if (board[r][0] == 'O') {
            dfs(board, r, 0, rows, cols);
        }
        if (board[r][cols - 1] == 'O') {
            dfs(board, r, cols - 1, rows, cols);
        }
    }
    for (int c = 0; c < cols; c++) {
        if (board[0][c] == 'O') {
            dfs(board, 0, c, rows, cols);
        }
        if (board[rows - 1][c] == 'O') {
            dfs(board, rows - 1, c, rows, cols);
        }
    }
    // Flip remaining Os to Xs; restore safe markers
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (board[r][c] == 'O') {
                board[r][c] = 'X';
            } else if (board[r][c] == '#') {
                board[r][c] = 'O';
            }
        }
    }
}

private void dfs(char[][] board, int r, int c, int rows, int cols) {
    if (!inBounds(r, c, rows, cols) || board[r][c] != 'O') {
        return;
    }
    board[r][c] = '#';
    for (int d = 0; d < 4; d++) {
        dfs(board, r + DR[d], c + DC[d], rows, cols);
    }
}
```

> **Mental hook:** *"Find regions that CAN'T escape → easier to flip and find regions that CAN escape."* Reverse-thinking. Same trick in LC 1020.

---

#### Variant — LC 417 Pacific Atlantic Water Flow (dual-source reverse BFS/DFS)

> **Why this one earns its own callout:** LC 130 has ONE source set (all border cells). LC 417 has **TWO disjoint source sets** (Pacific borders + Atlantic borders) and the answer is the **intersection** — cells reachable from BOTH oceans. Same reverse-thinking trick as LC 130, but with two parallel traversals.

**The forward-thinking solution is brutal:** for each cell, run a BFS/DFS asking *"can water from this cell reach BOTH oceans?"* That's `O((m × n)²)` — TLE on real inputs.

**The reverse trick:** flip the question. Instead of *"can this cell reach the ocean?"* ask *"which cells can the OCEAN reach if water flowed UPHILL?"* Then a cell is in the answer iff both oceans can reach it.

**Steps in plain English:**

1. **Two visited matrices** — `pacific[m][n]` and `atlantic[m][n]`. Each will be marked independently.
2. **Seed Pacific from its borders** — top row + left column. Run reverse-DFS (uphill: `next.height >= curr.height`).
3. **Seed Atlantic from its borders** — bottom row + right column. Same reverse-DFS into `atlantic[][]`.
4. **Intersect** — any cell where BOTH `pacific[r][c]` AND `atlantic[r][c]` are true is in the answer.

```java
private static final int[][] DIRS = {
    {-1, 0}, {1, 0}, {0, -1}, {0, 1}
};

public List<List<Integer>> pacificAtlantic(int[][] heights) {
    int m = heights.length;
    int n = heights[0].length;
    boolean[][] pacific = new boolean[m][n];
    boolean[][] atlantic = new boolean[m][n];

    // Step 2 — seed Pacific from top row + left column
    for (int j = 0; j < n; j++) {
        dfs(heights, pacific, 0, j, m, n);
    }
    for (int i = 0; i < m; i++) {
        dfs(heights, pacific, i, 0, m, n);
    }

    // Step 3 — seed Atlantic from bottom row + right column
    for (int j = 0; j < n; j++) {
        dfs(heights, atlantic, m - 1, j, m, n);
    }
    for (int i = 0; i < m; i++) {
        dfs(heights, atlantic, i, n - 1, m, n);
    }

    // Step 4 — intersect
    List<List<Integer>> result = new ArrayList<>();
    for (int r = 0; r < m; r++) {
        for (int c = 0; c < n; c++) {
            if (pacific[r][c] && atlantic[r][c]) {
                result.add(List.of(r, c));
            }
        }
    }
    return result;
}

private void dfs(int[][] heights, boolean[][] visited, int r, int c, int m, int n) {
    // Already visited from THIS ocean's source set
    if (visited[r][c]) {
        return;
    }
    visited[r][c] = true;
    // Recurse into 4 neighbors with the UPHILL constraint
    for (int[] d : DIRS) {
        int nr = r + d[0];
        int nc = c + d[1];
        if (nr < 0 || nr >= m || nc < 0 || nc >= n) {
            continue;
        }
        // KEY: reverse flow means we move to cells HIGHER OR EQUAL — water flows from there to here in the original problem
        if (heights[nr][nc] < heights[r][c]) {
            continue;
        }
        dfs(heights, visited, nr, nc, m, n);
    }
}
```

> 🐞 **Where this one bites:**
> - **Forgetting the corner cells go in BOTH seed loops** — `(0, 0)` and `(m-1, n-1)` get seeded twice. Harmless because of the visited guard, but worth knowing.
> - **Wrong direction on the height comparison.** Reverse-flow means *current ≤ neighbor* (water flows from neighbor DOWN to current). Easy to flip and get wrong answers on small inputs that still look "almost right."
> - **Using one shared `visited[][]`** instead of two — collapses the two ocean reachabilities into a single set, losing the intersection logic.

**🏷️ Example problems:** LC 417 Pacific Atlantic Water Flow ⭐ (this one), LC 1020 Number of Enclaves (single-source reverse BFS, like LC 130).

> **Mental hook (LC 417):** *"Two reverse floods, one intersection."* The reverse trick is the same as LC 130; the dual-source twist is what distinguishes this problem family.

---

### Sub-Pattern 4: Flood Fill [G-9]

**LC 733 — Steps in plain English:** standard DFS/BFS from `(sr, sc)`. Recolor every reachable same-color cell.

> **First-timer mental hook:** flood fill is **literally** the MS Paint paint-bucket tool. You click a cell; every cell connected to it through same-color neighbors changes to the new color. The "graph" here is *"cells with the original color"* — exactly like Number of Islands, except the "land" is defined by matching the source's color instead of being '1'.

```java
public int[][] floodFill(int[][] image, int sr, int sc, int newColor) {
    int oldColor = image[sr][sc];
    if (oldColor == newColor) {
        return image;
    }
    dfs(image, sr, sc, oldColor, newColor);
    return image;
}

private void dfs(int[][] image, int r, int c, int oldColor, int newColor) {
    int rows = image.length;
    int cols = image[0].length;
    if (!inBounds(r, c, rows, cols) || image[r][c] != oldColor) {
        return;
    }
    image[r][c] = newColor;
    for (int d = 0; d < 4; d++) {
        dfs(image, r + DR[d], c + DC[d], oldColor, newColor);
    }
}
```

> **Gotcha:** the `if (oldColor == newColor) return` early exit prevents infinite recursion when the source is already the target color.

---

### Sub-Pattern 5: Shape Canonicalization (Distinct Islands) [G-16]

**LC 694 Distinct Islands — concept:** each island has a *shape*; count distinct shapes by canonicalizing each one as a string (path-from-start signature) and adding to a `Set<String>`.

> **First-timer mental hook:** "shape" means *"if I picked this island up and slid it on top of another island (no rotation, just translation), would they perfectly overlap?"* If yes → same shape. If no → different shape. To compare shapes cheaply, we encode each island as a **string** built from the DFS traversal, then count how many distinct strings appear in a `Set<String>`.

**The canonicalization trick:** during DFS, record the **relative direction taken** at each step (`U/D/L/R`) plus a special marker on return (e.g., `B` for backtrack). The resulting string uniquely identifies the shape.

```java
public int countDistinctIslands(int[][] grid) {
    int rows = grid.length;
    int cols = grid[0].length;
    Set<String> shapes = new HashSet<>();
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == 1) {
                StringBuilder shape = new StringBuilder();
                dfs(grid, r, c, rows, cols, shape, 'S');     // S = start
                shapes.add(shape.toString());
            }
        }
    }
    return shapes.size();
}

private void dfs(int[][] grid, int r, int c, int rows, int cols,
                 StringBuilder shape, char dir) {
    if (!inBounds(r, c, rows, cols) || grid[r][c] != 1) {
        return;
    }
    grid[r][c] = 0;
    shape.append(dir);
    dfs(grid, r - 1, c, rows, cols, shape, 'U');
    dfs(grid, r + 1, c, rows, cols, shape, 'D');
    dfs(grid, r, c - 1, rows, cols, shape, 'L');
    dfs(grid, r, c + 1, rows, cols, shape, 'R');
    shape.append('B');                                       // backtrack marker
}
```

> **Why the backtrack marker matters:** without `'B'`, two islands with different shapes can produce identical strings. The backtrack marker records the recursion structure.

### 🎨 Visual — Why the 'B' (backtrack) marker is essential

```
LEGEND — what each letter in the encoded string means:

   S = the FIRST cell where DFS starts on this island (exactly one S)
   U = "DFS moved UP    to enter this cell"   (row - 1)
   D = "DFS moved DOWN  to enter this cell"   (row + 1)
   L = "DFS moved LEFT  to enter this cell"   (col - 1)
   R = "DFS moved RIGHT to enter this cell"   (col + 1)
   B = "DFS is RETURNING from this cell"      (appended on recursion EXIT)

So every cell contributes TWO letters to the string:
   - one when DFS ENTERS it  (S, U, D, L, or R)
   - one when DFS LEAVES it  (B)

────────────────────────────────────────────────────────────────────

TWO ISLANDS that look different but encode IDENTICALLY without 'B':


   ISLAND P  (⌐ corner, top row)        ISLAND Q  (¬ corner, bottom row)

     col→  0   1                          col→  0   1
   row    ┌───┬───┐                     row    ┌───┬───┐
    0     │ ★ │ X │                      0     │ ★ │   │
          ├───┼───┤                            ├───┼───┤
    1     │ X │   │                      1     │ X │ X │
          └───┴───┘                            └───┴───┘

   Filled cells: (0,0) (0,1) (1,0)      Filled cells: (0,0) (1,0) (1,1)
   ★ = DFS start                        ★ = DFS start

────────────────────────────────────────────────────────────────────

DFS TRACE FOR ISLAND P    (code tries U, D, L, R in that order)

   step                              action            string so far
   ─────────────────────────────────────────────────────────────────
   1. enter (0,0)                    append S          "S"
   2. try U → out of bounds          (nothing)         "S"
   3. try D → enter (1,0)            append D          "SD"
   4.   from (1,0): no new neighbors (nothing)         "SD"
   5.   leave (1,0)                  append B          "SDB"
   6. try L → out of bounds          (nothing)         "SDB"
   7. try R → enter (0,1)            append R          "SDBR"
   8.   from (0,1): no new neighbors (nothing)         "SDBR"
   9.   leave (0,1)                  append B          "SDBRB"
   10. leave (0,0)                   append B          "SDBRBB"

   FULL encoding (with B):     "S D B R B B"
   STRIPPED (drop every B):    "S D R"


DFS TRACE FOR ISLAND Q    (same code, same direction order)

   step                              action            string so far
   ─────────────────────────────────────────────────────────────────
   1. enter (0,0)                    append S          "S"
   2. try U → out of bounds          (nothing)         "S"
   3. try D → enter (1,0)            append D          "SD"
   4.   from (1,0): try R → (1,1)    append R          "SDR"
   5.     from (1,1): no neighbors   (nothing)         "SDR"
   6.     leave (1,1)                append B          "SDRB"
   7.   leave (1,0)                  append B          "SDRBB"
   8. try L → out of bounds          (nothing)         "SDRBB"
   9. try R → (0,1) is empty cell    (nothing)         "SDRBB"
   10. leave (0,0)                   append B          "SDRBBB"

   FULL encoding (with B):     "S D R B B B"
   STRIPPED (drop every B):    "S D R"

────────────────────────────────────────────────────────────────────

SIDE-BY-SIDE COMPARISON

   WITHOUT 'B':   P = "SDR"        ❌ SAME → falsely counted as 1 shape
                  Q = "SDR"

   WITH    'B':   P = "SDBRBB"     ✅ DIFFERENT → correctly counted as 2
                  Q = "SDRBBB"

   The difference is WHERE the first B falls:
     - In P, B comes BETWEEN D and R  → "after D we backed up, THEN went R"
     - In Q, B comes AFTER  D and R   → "we went D then immediately R deeper"

   That single position of B encodes the recursion-tree shape — i.e.,
   whether R was a SIBLING of D (top level, P) or a CHILD of D (one
   level deeper, Q).

────────────────────────────────────────────────────────────────────

KEY INVARIANT:
   The encoded string fingerprints the recursion TREE shape, not just
   the sequence of cells visited. Two islands can visit cells in the
   same direction-order yet have different parent-child structure in
   their DFS call tree. The 'B' marker records EXIT events, which is
   exactly the information needed to reconstruct that tree shape.
   Without 'B', the tree collapses to a flat sequence and distinct
   shapes collide.
```

> 🧩 **Try these (Grid BFS/DFS — the full ladder):**
> - ✅ **LC 200** Number of Islands (G-8) — start here
> - ✅ **LC 547** Number of Provinces (G-7) — same idea on adjacency matrix
> - ✅ **LC 733** Flood Fill (G-9)
> - ✅ **LC 994** Rotten Oranges (G-10) — first multi-source BFS
> - 🟡 **LC 542** 01 Matrix (G-13) — distance from nearest 0
> - 🟡 **LC 130** Surrounded Regions (G-14) — reverse-direction BFS
> - 🟡 **LC 1020** Number of Enclaves (G-15)
> - 🟡 **LC 695** Max Area of Island
> - 🔴 **LC 694** Number of Distinct Islands (G-16) — needs shape canonicalization

---

## 🔄 Cycle Detection — Undirected Graph [Striver G-11, G-12]

> **The key insight:** in an undirected graph, an edge `(u, v)` always goes both ways. So when you DFS from `u` to `v`, you'll see `u` again from `v`'s neighbors — that's NOT a cycle. A real cycle means you reach a visited vertex that is **NOT your immediate parent.**

### 🎨 Visual — Why the Parent Check Matters

```
CASE A: NOT a cycle (just the reverse edge)
─────────────────────────────────────────────

   DFS path: 0 → 1                     (0)─────(1)
                                        ▲
   At vertex 1, neighbor list           │   ← from 1, we see 0 in neighbors.
   includes 0. But 0 is the parent.    parent of 1
   This is the SAME edge we came in    is 0
   on, not a cycle.



CASE B: REAL cycle (back edge to non-parent)
─────────────────────────────────────────────

   DFS path: 0 → 1 → 2                    (0)─────(1)
                                            ╲       │
   At vertex 2, neighbor list includes       ╲      │
   0. 0 is NOT 2's parent (1 is). So          ╲     │
   this is a "back edge" → cycle found.       (2)──┘
                                          ↑
                                  This edge (2,0) closes the cycle.



The algorithm in one picture:

      ┌─ neighbor of u is visited? ──┐
      │                              │
      ▼                              ▼
   YES, AND it IS parent          YES, AND it is NOT parent
   → IGNORE (reverse edge)        → CYCLE FOUND
```

### Via DFS (parent tracking)

**Steps in plain English:**

1. For each unvisited vertex (to handle disconnected components), kick off a DFS with parent `-1`.
2. In DFS: mark current visited, iterate neighbors.
3. If neighbor is unvisited → recurse with current as parent.
4. If neighbor is visited AND is NOT the parent → cycle found.

```java
public boolean hasCycleUndirected(int V, List<List<Integer>> adj) {
    boolean[] visited = new boolean[V];
    for (int i = 0; i < V; i++) {
        if (!visited[i]) {
            if (dfs(i, -1, adj, visited)) {
                return true;
            }
        }
    }
    return false;
}

private boolean dfs(int u, int parent, List<List<Integer>> adj, boolean[] visited) {
    visited[u] = true;
    for (int v : adj.get(u)) {
        if (!visited[v]) {
            if (dfs(v, u, adj, visited)) {
                return true;
            }
        } else if (v != parent) {
            return true;                   // cycle found
        }
    }
    return false;
}
```

### Via BFS (parent tracking with queue of pairs)

```java
private boolean bfs(int src, List<List<Integer>> adj, boolean[] visited) {
    Queue<int[]> queue = new ArrayDeque<>();
    queue.offer(new int[]{src, -1});
    visited[src] = true;
    while (!queue.isEmpty()) {
        int[] curr = queue.poll();
        int u = curr[0];
        int parent = curr[1];
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                visited[v] = true;
                queue.offer(new int[]{v, u});
            } else if (v != parent) {
                return true;
            }
        }
    }
    return false;
}
```

> **The classic bug:** forgetting the `v != parent` check. Without it, you'll instantly report a cycle on any edge because `u → v` makes `u` look "visited" to `v`.

```java
// ❌ Always returns true on the first edge — false positive
if (visited[v]) {
    return true;
}

// ✅ A visited neighbor is only a cycle if it's NOT the parent
if (visited[v] && v != parent) {
    return true;
}
```

> 🧩 **Try these (Undirected cycle detection):**
> - ✅ Practice problem: Detect cycle in undirected graph (Striver/GFG)
> - 🟡 **LC 261** Graph Valid Tree (a tree = connected + acyclic + V-1 edges)

---

## ↔️ Bipartite Graph Check [Striver G-17, G-18]

> A graph is **bipartite** if you can color every vertex with one of two colors such that no edge connects same-colored vertices. Equivalent: no odd-length cycle exists.

> **First-timer analogy — "is this a two-team setup?":** imagine the vertices are people and the edges are "must-be-on-opposite-teams" constraints. The graph is **bipartite** iff you can split everyone into exactly **Team A** and **Team B** so that every edge connects an A-person to a B-person. *Bipartite = "two teams suffice."* Real-world bipartite examples:
>
> | Domain | Team A | Team B |
> | --- | --- | --- |
> | Job market | Job applicants | Open positions |
> | Movies | Actors | Films |
> | Recommendations | Users | Products |
> | Course schedule pairing | Students | Course slots |
>
> The check itself is just **"try to 2-color via BFS/DFS; if you hit a conflict, it's not bipartite."**

### 🎨 Visual — Bipartite vs Not Bipartite

```
BIPARTITE ✅                                 NOT BIPARTITE ❌
(even-length cycles only)                    (contains an ODD cycle)


    [W]─────[B]                                 [W]─────[B]
     │       │                                   │       │
     │       │                                   │       │
    [B]─────[W]                                 [B]─────[W]
                                                  ╲     ╱
   Cycle length = 4 (EVEN)                         ╲   ╱
   2 colors suffice                                 [B]
                                                  ↑
                                            Cycle 0→1→2→0 has length 3 (ODD)
                                            Coloring conflict guaranteed:
   COLORING TRACE:                          you'd try to color the
     start at top-left: W                   triangle's 3rd vertex but
     neighbor → flip to B                   BOTH options conflict.
     neighbor → flip to W
     ...all consistent ✅                   COLORING TRACE:
                                              0 = W
                                              1 = B (flip from 0)
                                              2 = W (flip from 1)
                                              back to 0: 2's neighbor 0 = W
                                                         but 2 is also W
                                                         → CONFLICT ❌
```

> **Mental hook (also gives you the proof):** *bipartite ⇔ no odd-length cycle.* Every BFS level alternates colors. An odd cycle would force two same-colored vertices to be adjacent, breaking 2-coloring.

**The algorithm:** BFS/DFS the graph, coloring as you go. If you ever try to color a vertex with a color that conflicts with a neighbor → not bipartite.

### Via BFS

```java
public boolean isBipartite(int V, List<List<Integer>> adj) {
    int[] color = new int[V];
    Arrays.fill(color, -1);                // -1 = uncolored
    for (int i = 0; i < V; i++) {
        if (color[i] == -1) {
            if (!bfsColor(i, adj, color)) {
                return false;
            }
        }
    }
    return true;
}

private boolean bfsColor(int src, List<List<Integer>> adj, int[] color) {
    Queue<Integer> queue = new ArrayDeque<>();
    queue.offer(src);
    color[src] = 0;
    while (!queue.isEmpty()) {
        int u = queue.poll();
        for (int v : adj.get(u)) {
            if (color[v] == -1) {
                color[v] = 1 - color[u];   // flip color
                queue.offer(v);
            } else if (color[v] == color[u]) {
                return false;              // same color = not bipartite
            }
        }
    }
    return true;
}
```

### Via DFS

```java
private boolean dfsColor(int u, int currColor, List<List<Integer>> adj, int[] color) {
    color[u] = currColor;
    for (int v : adj.get(u)) {
        if (color[v] == -1) {
            if (!dfsColor(v, 1 - currColor, adj, color)) {
                return false;
            }
        } else if (color[v] == currColor) {
            return false;
        }
    }
    return true;
}
```

> **Mental hook:** *"Can I 2-color this graph without any edge having endpoints of the same color?"* → bipartite. *"Are there odd cycles?"* → same question.

> 🧩 **Try these (Bipartite):**
> - ✅ **LC 785** Is Graph Bipartite? (G-17 / G-18)
> - 🟡 **LC 886** Possible Bipartition

---

## 🔁 Cycle Detection — Directed Graph [Striver G-19, G-20]

> **Why undirected detection doesn't work here:** in a directed graph, there's no "parent" — direction matters. You can have `A → B` and `B → C` with `A` not reachable from `C` at all. The trick is tracking which vertices are **currently in the active recursion path**.

### 🎨 Visual — `visited` vs `pathVisited` (the two-array trick)

```
Three-state coloring intuition:

   □ WHITE      (not visited yet — visited[v] = false)
   ▨ GRAY       (in current DFS recursion path — pathVisited[v] = true)
   ■ BLACK      (fully processed — visited[v] = true, pathVisited[v] = false)


CASE A: No cycle — diamond DAG

   (0)──►(1)──►(3)        DFS(0):
    │          ▲            visit 0 [GRAY]
    ▼          │              recurse to 1
   (2)────────┘                visit 1 [GRAY]
                                 recurse to 3
   When 0 explores 2,             visit 3 [GRAY]
   then 2 explores 3,             return ←  3 becomes BLACK
   3 is already BLACK, NOT      return    ←  1 becomes BLACK
   GRAY — so NO cycle.            recurse to 2
                                    visit 2 [GRAY]
                                      explore 3: BLACK → skip
                                    return ← 2 becomes BLACK
                                  return ← 0 becomes BLACK


CASE B: Cycle present

   (0)──►(1)──►(2)        DFS(0):
          ▲    │            visit 0 [GRAY]
          │    │              recurse to 1
          └────┘                visit 1 [GRAY]
                                 recurse to 2
   At 2, neighbor 1 is              visit 2 [GRAY]
   GRAY (still on the                 explore 1: GRAY → CYCLE FOUND ❌
   active call stack).
                                   ↑ back edge into the current DFS path
                                     means we're going in circles


WHY THIS WORKS:

  An edge u → v that points to a GRAY vertex v means v is an
  ancestor of u in the DFS recursion. The path  v ↝...↝ u → v
  is a cycle.

  An edge u → v pointing to a BLACK vertex means v's whole subtree
  has finished — that's a "cross" or "forward" edge, NOT a cycle.

CRUCIAL backtrack step:
  Before returning from DFS(u), set pathVisited[u] = false.
  Otherwise GRAY would leak into BLACK and produce false positives.
```

### Via DFS with Recursion Stack (`pathVisited`)

> Maintain TWO arrays: `visited` (ever-explored) and `pathVisited` (currently in this DFS path). If you encounter a vertex that's already in `pathVisited` → cycle.

**Steps in plain English:**

1. For each unvisited vertex, kick off a DFS.
2. Mark current vertex in BOTH `visited` and `pathVisited`.
3. For each neighbor:
   - Unvisited → recurse; if it returns true, propagate up.
   - In `pathVisited` → cycle found.
4. **Crucially:** before returning, **remove current from `pathVisited`** (but keep `visited`). This is the "backtrack" step.

```java
public boolean hasCycleDirected(int V, List<List<Integer>> adj) {
    boolean[] visited = new boolean[V];
    boolean[] pathVisited = new boolean[V];
    for (int i = 0; i < V; i++) {
        if (!visited[i]) {
            if (dfs(i, adj, visited, pathVisited)) {
                return true;
            }
        }
    }
    return false;
}

private boolean dfs(int u, List<List<Integer>> adj,
                    boolean[] visited, boolean[] pathVisited) {
    visited[u] = true;
    pathVisited[u] = true;
    for (int v : adj.get(u)) {
        if (!visited[v]) {
            if (dfs(v, adj, visited, pathVisited)) {
                return true;
            }
        } else if (pathVisited[v]) {
            return true;
        }
    }
    pathVisited[u] = false;                // backtrack — leaving this path
    return false;
}
```

> **The single most common bug:** using ONLY `visited[]` (no `pathVisited[]`). That detects "have I seen this before" — which is not a cycle in a directed graph (a vertex can be reachable from multiple ancestors without any cycle).

```java
// ❌ Reports cycle on any "diamond" shape (A → B, A → C, B → D, C → D)
if (visited[v]) {
    return true;
}

// ✅ Only a cycle if v is in the CURRENT recursion path
if (pathVisited[v]) {
    return true;
}
```

### Application: Eventual Safe States [G-20, G-25]

**LC 802 Eventual Safe Nodes:** a node is "safe" if every path from it leads to a terminal (no outgoing edges). Equivalent: nodes NOT on any cycle.

> **Solution:** same DFS-with-pathVisited cycle detection — mark nodes that lead to a cycle as "unsafe"; everything else is safe. (BFS-based version uses Kahn's on the reversed graph — see G-25.)

> 🧩 **Try these (Directed cycle detection):**
> - ✅ Practice problem: Detect cycle in directed graph
> - 🟡 **LC 802** Find Eventual Safe States (G-20 / G-25)
> - 🟡 **LC 207** Course Schedule (G-24) — uses topological sort, see below

---

## 📊 Topological Sort [Striver G-21 to G-26]

> **What it is:** a linear ordering of vertices of a **DAG** (Directed Acyclic Graph) such that for every edge `u → v`, `u` appears before `v`. Used for prerequisite chains, build orders, task scheduling.

> **Critical:** topological sort **only exists for DAGs**. If there's a cycle, no valid topo order exists — and this fact is itself the standard way to detect a directed cycle.

### 🎨 Visual — The Prerequisite Graph

```
Example: course prerequisites

      (0: Intro CS)
       │       │
       ▼       ▼
   (1: Algos) (2: Data Struct)
       │       │
       └───┬───┘
           ▼
      (3: ML)
           │
           ▼
      (4: Capstone)


   Valid topological orders:
     0, 1, 2, 3, 4   ✅
     0, 2, 1, 3, 4   ✅   (1 and 2 are independent)

   Invalid:
     1, 0, 2, 3, 4   ❌   (1 before 0 violates 0→1)
     0, 1, 3, 2, 4   ❌   (2 must come before 3)


   Rule: for every edge u → v, u must appear BEFORE v in the order.
```

### 🎨 Visual — Kahn's Algorithm Step-by-Step

```
Same DAG. Kahn's = repeatedly pluck out vertices with in-degree 0.

Initial in-degrees:                   Queue (in-degree 0 vertices):
  0: 0   1: 1   2: 1   3: 2   4: 1     [0]


Step 1: Poll 0. Decrement nbrs of 0 (= 1 and 2):
  0: 0   1: 0   2: 0   3: 2   4: 1     [1, 2]
  Order so far: [0]
  1 and 2 hit in-degree 0 → enqueue both.

Step 2: Poll 1. Decrement 3:
  0: 0   1: 0   2: 0   3: 1   4: 1     [2]
  Order so far: [0, 1]
  3 still has in-degree 1 (waiting for 2) → do NOT enqueue.

Step 3: Poll 2. Decrement 3:
  0: 0   1: 0   2: 0   3: 0   4: 1     [3]
  Order so far: [0, 1, 2]
  3 hits in-degree 0 → enqueue.

Step 4: Poll 3. Decrement 4:
  0: 0   1: 0   2: 0   3: 0   4: 0     [4]
  Order so far: [0, 1, 2, 3]
  4 hits in-degree 0 → enqueue.

Step 5: Poll 4. No neighbors.
  Order so far: [0, 1, 2, 3, 4]    ← DONE!


Queue evolution:  [0] → [1, 2] → [2] → [3] → [4] → []
Order produced:   [0, 1, 2, 3, 4]
Size == V?        Yes → it's a valid DAG.

────────────────────────────────────────────────────────

If the order is shorter than V at the end, you had a CYCLE.
The "stuck" vertices form one or more cycles where every
vertex always has at least one incoming edge from another
stuck vertex — so none ever hits in-degree 0.

Example (cycle):
  (0)──►(1)──►(2)──►(0)

  Initial in-degrees: 0:1, 1:1, 2:1
  Queue is empty from the start → nothing to plug in.
  Order = [], size 0 < 3 → CYCLE detected.
```

### Approach 1: DFS-Based Topo Sort [G-21]

> **First-timer intuition — "stack of completion":** imagine a TODO list where you can't write a task down until **every task it depends on is already finished**. DFS naturally produces this order: when DFS at vertex `u` returns, every vertex reachable from `u` has already completed. So if we **push on completion** (i.e., after the for-loop, NOT before it), the stack ends up with the deepest dependencies at the bottom and the most "independent" vertices at the top. Pop the stack and you get a valid topological order.
>
> **Why post-order (not pre-order)?** Pre-order (push at entry) would put `u` on the stack BEFORE its dependencies completed — wrong. Post-order (push at exit) guarantees: *when you push `u`, every `v` that `u` depends on has already been pushed below it.* That's exactly the topo property: `u` ends up above its dependencies → pops out after them → satisfying "all dependencies first."

**Steps in plain English:**

1. DFS the graph. Use a `Deque<Integer>` as a stack.
2. After exploring all of a vertex's neighbors, **push** the vertex onto the stack.
3. After DFS completes, pop the stack → that's the topo order.

```java
public List<Integer> topoSortDFS(int V, List<List<Integer>> adj) {
    boolean[] visited = new boolean[V];
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < V; i++) {
        if (!visited[i]) {
            dfs(i, adj, visited, stack);
        }
    }
    List<Integer> order = new ArrayList<>();
    while (!stack.isEmpty()) {
        order.add(stack.pop());
    }
    return order;
}

private void dfs(int u, List<List<Integer>> adj,
                 boolean[] visited, Deque<Integer> stack) {
    visited[u] = true;
    for (int v : adj.get(u)) {
        if (!visited[v]) {
            dfs(v, adj, visited, stack);
        }
    }
    stack.push(u);                         // push AFTER all neighbors done
}
```

> **Why "push after all neighbors":** by the time you push `u`, every vertex reachable from `u` is already deeper in the stack. So popping gives them in dependency order.

### 🎨 Visual — DFS Topo Sort on the same prerequisite DAG

```
Recall the DAG:                       DFS exploration order from vertex 0:

      (0: Intro CS)                     DFS(0)
       │       │                          DFS(1)
       ▼       ▼                            DFS(3)
   (1: Algos) (2: Data Struct)                DFS(4)
       │       │                                no nbrs → push 4
       └───┬───┘                              push 3   (4 already below)
           ▼                                push 1     (3, 4 already below)
      (3: ML)                              DFS(2)
           │                                  DFS(3) → already visited, skip
           ▼                                push 2
      (4: Capstone)                       push 0       (1, 2, 3, 4 below)


STACK EVOLUTION (push = top adds new entry):

   []  →  [4]  →  [4, 3]  →  [4, 3, 1]  →  [4, 3, 1, 2]  →  [4, 3, 1, 2, 0]
                                                                   ↑ TOP


POP from top to produce the topo order:

   pop 0 → pop 2 → pop 1 → pop 3 → pop 4

   Final order: [0, 2, 1, 3, 4]    ← valid topological order ✅
                ↑
                Note: not the only valid order. [0, 1, 2, 3, 4] is also valid.
                Topo sort is deterministic in algorithm but not unique in answer.


KEY INVARIANT:
   When we push 'u' onto the stack, every descendant of 'u' in the DFS
   tree is already below it. Since edges go from u → v with v deeper,
   popping LIFO gives: parents before children = dependencies before
   dependents = valid topological order.

WHY IT FAILS WITH PRE-ORDER:
   If we pushed 'u' at ENTRY instead, then u would sit BELOW its
   descendants. Popping would yield descendants first, parents last
   — the REVERSE of a topo order. Hence: push at exit (post-order).
```

### Approach 2: Kahn's Algorithm — BFS with In-Degree [G-22, G-23]

**The intuition:** repeatedly pluck out a vertex with **in-degree 0** (no dependencies left) and "remove" it from the graph (decrementing in-degrees of its neighbors).

**Steps in plain English:**

1. Compute in-degree of every vertex.
2. Enqueue all vertices with in-degree 0.
3. While the queue is not empty:
   a. Poll vertex `u`, add to result.
   b. For each neighbor `v`, decrement in-degree. If it hits 0, enqueue `v`.
4. If the result has all V vertices → valid topo order. Otherwise → cycle exists.

```java
public List<Integer> kahnsTopoSort(int V, List<List<Integer>> adj) {
    int[] indegree = new int[V];
    for (int u = 0; u < V; u++) {
        for (int v : adj.get(u)) {
            indegree[v]++;
        }
    }
    Queue<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < V; i++) {
        if (indegree[i] == 0) {
            queue.offer(i);
        }
    }
    List<Integer> order = new ArrayList<>();
    while (!queue.isEmpty()) {
        int u = queue.poll();
        order.add(u);
        for (int v : adj.get(u)) {
            indegree[v]--;
            if (indegree[v] == 0) {
                queue.offer(v);
            }
        }
    }
    return order;
}
```

### Cycle Detection via Kahn's [G-23]

> **The trick:** if Kahn's produces a topo order of size `< V`, the graph has a cycle. Some vertices were stuck in mutual dependency.

```java
// At end of kahnsTopoSort
return order.size() == V;                  // true = DAG, false = has cycle
```

### Applications

| Striver | Problem | Approach |
| --- | --- | --- |
| G-24 | **LC 207** Course Schedule | Kahn's; check if topo order length == numCourses |
| G-24 | **LC 210** Course Schedule II | Kahn's; return the topo order |
| G-25 | **LC 802** Eventual Safe States | Kahn's on **reversed** graph; safe nodes appear in the topo order |
| G-26 | **LC 269** Alien Dictionary | Build adjacency from word comparisons; Kahn's for ordering |
| G-27 | Shortest Path in DAG | Topo sort + edge relaxation (covered below) |

> **LC 207 Course Schedule — the canonical interview question.** Always solvable with Kahn's: if all V appear in the order, you can finish all courses.

```java
public boolean canFinish(int numCourses, int[][] prerequisites) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }
    int[] indegree = new int[numCourses];
    for (int[] pre : prerequisites) {
        adj.get(pre[1]).add(pre[0]);       // pre[1] must come before pre[0]
        indegree[pre[0]]++;
    }
    Queue<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < numCourses; i++) {
        if (indegree[i] == 0) {
            queue.offer(i);
        }
    }
    int count = 0;
    while (!queue.isEmpty()) {
        int u = queue.poll();
        count++;
        for (int v : adj.get(u)) {
            indegree[v]--;
            if (indegree[v] == 0) {
                queue.offer(v);
            }
        }
    }
    return count == numCourses;
}
```

> 🧩 **Try these (Topological Sort):**
> - ✅ Practice problem: Topo sort (Striver/GFG)
> - ✅ **LC 207** Course Schedule (G-24) — must-do
> - ✅ **LC 210** Course Schedule II (G-24) — must-do
> - 🟡 **LC 802** Find Eventual Safe States via Kahn's (G-25)
> - 🟡 **LC 269** Alien Dictionary (G-26)
> - 🔴 LC 2115 Find All Possible Recipes from Given Supplies

---

## 🗺️ Where to Go Next in Graphs (per your 17-day plan)

> **You are here:** topo sort done. The 17-day plan's Day 4 (the last graph day before DP starts) closes the remaining graph gaps before you pivot to DP for 10 days. Everything below is **already written in this file** — Day 4 is "solve these problems while reading these sections," not "create new notes."

### ✅ Day 4 problems (the must-do four, before DP starts)

| Problem | Pattern it teaches | Read first (this file) |
| --- | --- | --- |
| **LC 547** Number of Provinces ⭐ | Adjacency-matrix DFS + first DSU exposure | § "Disjoint Set Union (DSU)" (line ~3258) — full template + path compression + union-by-rank |
| **LC 261** Graph Valid Tree | Undirected cycle + connectivity ("tree = connected ∧ E == V-1") | § "Cycle Detection — Undirected Graph" (line ~1989) |
| **LC 127** Word Ladder ⭐ | BFS on an **implicit** graph (graph not given — you build neighbors lazily) | § "Word Ladder I, II" (line ~2766) |
| LC 695 Max Area of Island (revisit) | Bottom-up grid DFS with a return value | § "Sub-Pattern 1: Counting Connected Components on a Grid" |

> **Why this order:** LC 547 introduces DSU on a clean adjacency-matrix problem (no string-key indirection yet). LC 261 reinforces "tree-ness as a property of a graph." LC 127 is the **hardest of Day 4** — the lesson is *"see the graph where none is drawn"* (each word is a node, edges exist between words differing by one letter). Don't precompute the graph — build neighbors lazily inside BFS.

### 🎯 What to LOOK FOR while solving Day 4

1. **DSU template fits on one screen.** `parent[]`, `rank[]`, `find()` with path compression, `union()` with rank. If yours doesn't fit on one screen, you're overengineering.
2. **The "is it a tree?" identity:** a graph is a tree iff (a) it's connected and (b) has exactly `V - 1` edges and (c) is acyclic. Any two of these imply the third in an undirected graph.
3. **Word Ladder's neighbor generation:** for each of the 26 letter substitutions at each position, check the dictionary. **Don't** precompute an `O(N² · L)` adjacency map — that TLEs.

### 🔄 Update the templates reference AFTER Day 4

When all four problems are solved cold, open **`../Reference/bfs-dfs-templates-reference.md`** and add:
- One row for **DSU** (signature: `parent[]`, `find` + path compression, `union` by rank — O(α(N)) per op)
- One row for **implicit-graph BFS** (signature: BFS where neighbors are *generated* on the fly, not stored)

This is the bridge that lets you spot these patterns months later.

### 🚀 What's NEXT after Day 4 (Days 5-14 — the DP block)

Once Day 4 is locked in, you're done with graphs for the prep cycle. The 17-day plan pivots to **DP foundations on Day 5** — `recursion → memo → tabulation → space-opt` as a four-stage drill applied to LC 509 / LC 70 / LC 198, kicking off the deliverable file **`DSA/DeepDive/dp-fundamentals.md`** which grows daily across Days 5-14.

> **Senior likely topics** (below this divider — Shortest Path, MST, advanced DSU apps): **skip during this 17-day window.** They're 🟡 senior-level / specialized and not on the SDE-3 critical path. Revisit only after the DP + Greedy block is complete.

---

```
═══════════════════════════════════════════════════════════════════
              ✋  END OF MEDIUM-LEVEL INTERVIEW SCOPE
═══════════════════════════════════════════════════════════════════

   You've now covered Striver G-1 through G-26:

   ✓ Graph representation         ✓ Cycle detection (both forms)
   ✓ BFS + DFS templates          ✓ Bipartite check
   ✓ Connected components         ✓ Topological Sort (DFS + Kahn's)
   ✓ Grid BFS/DFS patterns        ✓ Course Schedule + Alien Dictionary

   This alone covers ~80% of LC graph problems and almost
   every SDE-2 / SDE-3 medium graph interview question.

   STOP HERE IF:
   - You have ≤ 3 weeks of prep time
   - The target role is SDE-2 / mid-level
   - You haven't yet solved Tier 1 + Tier 2 problems cold

   CONTINUE BELOW IF:
   - You're targeting L5+ / senior roles
   - The role explicitly involves graph-heavy systems
   - You've already mastered the medium core and want depth
═══════════════════════════════════════════════════════════════════
```

> **What's below the divider** — three blocks of decreasing interview frequency:
>
> | Block | Topics | Striver | Interview reality |
> | --- | --- | --- | --- |
> | 🟡 **Senior likely** | BFS shortest path, DAG shortest path, Word Ladder, Dijkstra core | G-27 to G-35 | Commonly asked at L5+; rare at SDE-2 |
> | 🔴 **Advanced** | Dijkstra apps, Bellman-Ford, Floyd-Warshall, MST, DSU | G-36 to G-53 | Senior roles, system-design-adjacent rounds |
> | 🔴 **Rarely asked** | Kosaraju (SCC), Tarjan (Bridges), Articulation Points | G-54 to G-56 | Specialized / Staff+ roles |

---

## 📏 Shortest Path Family [Striver G-27 to G-43] 🟡 *Senior likely from here on*

The shortest-path family has **four** main algorithms, picked by graph properties:

| Algorithm | Use when | Time | Space |
| --- | --- | --- | --- |
| **BFS** | Unweighted (or all edges equal) | `O(V + E)` | `O(V)` |
| **Topo Sort + Relax** | DAG (any weights, including negative) | `O(V + E)` | `O(V)` |
| **Dijkstra** | Non-negative weights, single source | `O((V + E) log V)` | `O(V)` |
| **Bellman-Ford** | Negative weights allowed, detect negative cycles | `O(V · E)` | `O(V)` |
| **Floyd-Warshall** | All-pairs shortest paths, small V | `O(V³)` | `O(V²)` |

> **The picking rule:** **start at the top of the table; pick the first one that fits.** BFS is always fastest when applicable.

---

### Shortest Path in DAG via Topological Sort [G-27]

**The idea:** in a DAG, process vertices in topo order. By the time you reach vertex `u`, every shorter-path option ending at `u` has been considered. Then **relax** every outgoing edge.

```java
public int[] shortestPathDAG(int V, List<List<int[]>> adj, int src) {
    // Step 1 — topo sort (DFS-based)
    boolean[] visited = new boolean[V];
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < V; i++) {
        if (!visited[i]) {
            topoDfs(i, adj, visited, stack);
        }
    }
    // Step 2 — initialize distances
    int[] dist = new int[V];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;
    // Step 3 — process vertices in topo order, relaxing edges
    while (!stack.isEmpty()) {
        int u = stack.pop();
        if (dist[u] == Integer.MAX_VALUE) {
            continue;
        }
        for (int[] edge : adj.get(u)) {
            int v = edge[0];
            int w = edge[1];
            if (dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
            }
        }
    }
    return dist;
}

private void topoDfs(int u, List<List<int[]>> adj,
                     boolean[] visited, Deque<Integer> stack) {
    visited[u] = true;
    for (int[] edge : adj.get(u)) {
        int v = edge[0];
        if (!visited[v]) {
            topoDfs(v, adj, visited, stack);
        }
    }
    stack.push(u);
}
```

> **Why this beats Dijkstra on a DAG:** O(V + E) vs O((V + E) log V). And handles negative weights, which Dijkstra can't.

---

### Shortest Path in Unweighted Graph via BFS [G-28]

**The idea:** BFS visits vertices in order of distance (edge count). The first time you reach a vertex IS the shortest path.

```java
public int[] shortestPathBFS(int V, List<List<Integer>> adj, int src) {
    int[] dist = new int[V];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;
    Queue<Integer> queue = new ArrayDeque<>();
    queue.offer(src);
    while (!queue.isEmpty()) {
        int u = queue.poll();
        for (int v : adj.get(u)) {
            if (dist[u] + 1 < dist[v]) {
                dist[v] = dist[u] + 1;
                queue.offer(v);
            }
        }
    }
    return dist;
}
```

> **Why BFS finds shortest paths in unweighted graphs:** every edge has weight 1, so the "first visit" via BFS is via the fewest edges = shortest path.

---

### Word Ladder I, II [G-29, G-30, G-31]

**LC 127 Word Ladder I:** find the shortest transformation sequence from `beginWord` to `endWord`. Each step changes one letter; intermediate words must be in `wordList`.

**The trick:** model words as graph vertices. Edge between two words if they differ by exactly one letter. Then BFS from `beginWord`.

> **Optimization:** instead of comparing every pair (O(N² × L)), for each word generate all "one-letter-mutated" candidates and check if each is in `wordList`. O(N × L × 26).

```java
public int ladderLength(String beginWord, String endWord, List<String> wordList) {
    Set<String> words = new HashSet<>(wordList);
    if (!words.contains(endWord)) {
        return 0;
    }
    Queue<String> queue = new ArrayDeque<>();
    queue.offer(beginWord);
    int steps = 1;
    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            String word = queue.poll();
            if (word.equals(endWord)) {
                return steps;
            }
            char[] chars = word.toCharArray();
            for (int pos = 0; pos < chars.length; pos++) {
                char original = chars[pos];
                for (char c = 'a'; c <= 'z'; c++) {
                    chars[pos] = c;
                    String next = new String(chars);
                    if (words.contains(next)) {
                        queue.offer(next);
                        words.remove(next);     // prevent revisit
                    }
                }
                chars[pos] = original;
            }
        }
        steps++;
    }
    return 0;
}
```

> **LC 126 Word Ladder II** (find ALL shortest sequences): BFS to find distances, then DFS to reconstruct paths. The optimized version (G-31) avoids the second BFS by tracking parents during the first BFS.

> 🧩 **Try these (BFS Shortest Path):**
> - ✅ **LC 127** Word Ladder (G-29) — must-do
> - 🟡 **LC 126** Word Ladder II (G-30/G-31)
> - 🟡 **LC 1091** Shortest Path in Binary Matrix
> - 🟡 **LC 433** Minimum Genetic Mutation (Word-Ladder twin)

---

### Dijkstra's Algorithm [Striver G-32, G-33, G-34, G-35]

> **The algorithm:** greedy. Repeatedly pick the **unvisited vertex with smallest known distance**, finalize its distance, and relax its outgoing edges. Implemented with a **min-heap** (priority queue).

> **Crucial constraint: NO negative edge weights.** Dijkstra's greedy choice depends on "once finalized, the distance can't decrease" — negative edges break this invariant. Use Bellman-Ford instead.

### 🎨 Visual — Dijkstra Relaxation Trace (source = 0)

```
Weighted graph:

         4
     (0)─────(1)
      │ \      │
    1 │  \ 5   │ 1
      │   \    │
     (2)──(3)──(4)
        2    3

Edges: 0─1 (w=4), 0─2 (w=1), 0─3 (w=5),
       1─4 (w=1), 2─3 (w=2), 3─4 (w=3)


Initial state (dist[i] = ∞ except dist[0] = 0):

  dist:  0:0   1:∞   2:∞   3:∞   4:∞
  PQ:    [(0, 0)]                          ← (distance, vertex)
  done:  {}


Step 1: pop (0, 0). Relax neighbors:
  0→1 (w=4):  0 + 4 = 4  <  ∞  → dist[1] = 4, push (4, 1)
  0→2 (w=1):  0 + 1 = 1  <  ∞  → dist[2] = 1, push (1, 2)
  0→3 (w=5):  0 + 5 = 5  <  ∞  → dist[3] = 5, push (5, 3)

  dist:  0:0   1:4   2:1   3:5   4:∞
  PQ:    [(1, 2), (4, 1), (5, 3)]
  done:  {0}


Step 2: pop (1, 2). Relax neighbors:
  2→0: skip (already done)
  2→3 (w=2):  1 + 2 = 3  <  5  → dist[3] = 3, push (3, 3)

  dist:  0:0   1:4   2:1   3:3   4:∞
  PQ:    [(3, 3), (4, 1), (5, 3)]      ← (5,3) is now STALE
  done:  {0, 2}


Step 3: pop (3, 3). Relax neighbors:
  3→0: skip
  3→2: 3 + 2 = 5 > 1, no update
  3→4 (w=3):  3 + 3 = 6  <  ∞  → dist[4] = 6, push (6, 4)

  dist:  0:0   1:4   2:1   3:3   4:6
  PQ:    [(4, 1), (5, 3), (6, 4)]
  done:  {0, 2, 3}


Step 4: pop (4, 1). Relax neighbors:
  1→0: skip
  1→4 (w=1):  4 + 1 = 5  <  6  → dist[4] = 5, push (5, 4)

  dist:  0:0   1:4   2:1   3:3   4:5
  PQ:    [(5, 3), (5, 4), (6, 4)]      ← (6,4) is STALE
  done:  {0, 1, 2, 3}


Step 5: pop (5, 3). STALE — dist[3]=3, popped 5. Skip.

Step 6: pop (5, 4). Relax neighbors: all already cheaper. Done.

Step 7: pop (6, 4). STALE. Skip.

Final distances:
  dist[0]=0  dist[1]=4  dist[2]=1  dist[3]=3  dist[4]=5

  Shortest path 0→4 has cost 5 (route: 0 → 2 → 3 → 4? No, that's 1+2+3=6.
                                actual route: 0 → 1 → 4 = 4+1 = 5 ✅)


KEY OBSERVATIONS:

  1. PQ may contain MULTIPLE entries for the same vertex (stale entries).
     That's fine — we check `if (d > dist[u]) skip;` to discard them.

  2. Once a vertex pops out of PQ with its smallest distance, that
     distance is FINAL. Greedy choice is safe because all edges are
     non-negative — no future path can be shorter.

  3. This is why Dijkstra FAILS with negative weights. A finalized
     vertex's distance could later be made smaller by a negative edge,
     but Dijkstra never revisits it.
```

**Time:** `O((V + E) log V)` with binary heap. **Space:** `O(V)`.

**Steps in plain English:**

1. Initialize `dist[]` to infinity; `dist[src] = 0`.
2. Push `(0, src)` into a min-heap (priority by distance).
3. While the heap is not empty:
   a. Poll the smallest `(d, u)`.
   b. If `d > dist[u]`, skip (stale entry).
   c. Otherwise, relax every outgoing edge: if `dist[u] + w < dist[v]`, update and push `(newDist, v)`.

```java
public int[] dijkstra(int V, List<List<int[]>> adj, int src) {
    int[] dist = new int[V];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, src});
    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int d = curr[0];
        int u = curr[1];
        if (d > dist[u]) {
            continue;                      // stale; we found a better path earlier
        }
        for (int[] edge : adj.get(u)) {
            int v = edge[0];
            int w = edge[1];
            if (dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
                pq.offer(new int[]{dist[v], v});
            }
        }
    }
    return dist;
}
```

> **Why we need the `d > dist[u]` skip:** because Java's `PriorityQueue` doesn't support decrease-key. When we find a better path to `v`, we push a new entry; the older (larger) entry remains in the heap. When polled later, we skip it.

### Print the Path [G-35]

> Maintain a `parent[]` array. When you relax `(u, v)`, record `parent[v] = u`. To reconstruct the path to `target`, walk parents from `target` back to `src`.

```java
int[] parent = new int[V];
for (int i = 0; i < V; i++) {
    parent[i] = i;
}
// ... inside the relax block ...
if (dist[u] + w < dist[v]) {
    dist[v] = dist[u] + w;
    parent[v] = u;
    pq.offer(new int[]{dist[v], v});
}
// reconstruct
List<Integer> path = new ArrayList<>();
int curr = target;
while (parent[curr] != curr) {
    path.add(curr);
    curr = parent[curr];
}
path.add(src);
Collections.reverse(path);
```

### Dijkstra Applications [Striver G-36 to G-40]

| Striver | Problem | Twist |
| --- | --- | --- |
| G-36 | **LC 1091** Shortest Path in Binary Matrix | Grid; 8-directional; BFS works since weights = 1 |
| G-37 | **LC 1631** Path with Minimum Effort | Cost = max edge weight on path; Dijkstra with custom relax |
| G-38 | **LC 787** Cheapest Flights Within K Stops | **NOT Dijkstra** — Bellman-Ford or modified BFS with stop count |
| G-39 | Minimum Multiplications to Reach End | Dijkstra on implicit graph (multiply by each value) |
| G-40 | **LC 1976** Number of Ways to Arrive at Destination | Dijkstra + count of equal-distance paths |

> **LC 787 trap:** intuition screams "Dijkstra", but the K-stops constraint changes optimality. Dijkstra greedily commits to the cheapest path, which may exceed K stops. Use Bellman-Ford or BFS with `(node, stops, cost)` state.

> 🧩 **Try these (Dijkstra ladder):**
> - ✅ Practice problem: Dijkstra's algorithm (Striver/GFG)
> - ✅ **LC 743** Network Delay Time — vanilla Dijkstra
> - 🟡 **LC 1091** Shortest Path in Binary Matrix (G-36)
> - 🟡 **LC 1631** Path with Minimum Effort (G-37)
> - 🟡 **LC 787** Cheapest Flights Within K Stops (G-38)
> - 🔴 **LC 1976** Number of Ways to Arrive (G-40)

---

### Bellman-Ford Algorithm [Striver G-41]

> **What it does:** computes shortest paths from a single source, **even with negative edge weights**. Also detects negative cycles.

> **Trade-off:** O(V · E) — significantly slower than Dijkstra. Only use when Dijkstra can't (negative weights present).

**The algorithm:** relax every edge V-1 times. After V-1 iterations, all shortest paths are finalized (any shortest path has at most V-1 edges). On the V-th iteration, if any edge still relaxes → negative cycle exists.

```java
public int[] bellmanFord(int V, int[][] edges, int src) {
    int[] dist = new int[V];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;
    // Relax V-1 times
    for (int i = 0; i < V - 1; i++) {
        for (int[] edge : edges) {
            int u = edge[0];
            int v = edge[1];
            int w = edge[2];
            if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
            }
        }
    }
    // V-th iteration: if anything still relaxes, negative cycle
    for (int[] edge : edges) {
        int u = edge[0];
        int v = edge[1];
        int w = edge[2];
        if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
            return null;                   // signal: negative cycle
        }
    }
    return dist;
}
```

> **Why V-1 iterations:** any simple path has at most V-1 edges. After k iterations, every shortest path using at most k edges is finalized. So V-1 iterations suffice.

> 🧩 **Try these (Bellman-Ford):**
> - 🟡 Practice problem: Bellman-Ford (Striver/GFG)
> - 🟡 **LC 787** Cheapest Flights (Bellman-Ford with K-iteration cap)

---

### Floyd-Warshall Algorithm [Striver G-42, G-43]

> **What it does:** computes shortest paths between **all pairs** of vertices. DP-based. Handles negative weights (but not negative cycles).

> **Use when:** V is small (≤ ~400). Otherwise O(V³) becomes prohibitive.

**The algorithm:** for each intermediate vertex `k`, check if going through `k` shortens the path from `i` to `j`.

```java
public int[][] floydWarshall(int V, int[][] edges, int n) {
    int[][] dist = new int[V][V];
    for (int[] row : dist) {
        Arrays.fill(row, Integer.MAX_VALUE / 2);   // avoid overflow on additions
    }
    for (int i = 0; i < V; i++) {
        dist[i][i] = 0;
    }
    for (int[] edge : edges) {
        int u = edge[0];
        int v = edge[1];
        int w = edge[2];
        dist[u][v] = w;
    }
    // Try each intermediate vertex
    for (int k = 0; k < V; k++) {
        for (int i = 0; i < V; i++) {
            for (int j = 0; j < V; j++) {
                if (dist[i][k] + dist[k][j] < dist[i][j]) {
                    dist[i][j] = dist[i][k] + dist[k][j];
                }
            }
        }
    }
    return dist;
}
```

> **The DP insight:** `dist[i][j]` after iteration `k` = shortest `i → j` path using only intermediate vertices in `{0, 1, ..., k}`. After processing all `k`, you get true shortest paths.

> **Why `Integer.MAX_VALUE / 2`:** if `dist[i][k]` is `MAX_VALUE` and you add a weight, you'd overflow. Halving keeps additions safe.

> 🧩 **Try these (Floyd-Warshall):**
> - 🟡 Practice problem: Floyd-Warshall (Striver/GFG)
> - 🟡 **LC 1334** Find the City With Smallest Number of Neighbours at Threshold (G-43)

---

## 🌳 Minimum Spanning Tree (MST) [Striver G-44, G-45, G-47] 🔴 *Advanced*

> **What MST is:** a subset of edges that connects all vertices with **minimum total weight** and **no cycles**. Has exactly V-1 edges.

### 🎨 Visual — Prim vs Kruskal on the SAME Graph

```
Input graph (V=5, E=7):

         3
     (0)─────(1)
      │ ╲      │
    1 │  ╲ 4   │ 2
      │   ╲    │
     (2)──(3)──(4)
        5    6

Edges: (0,1,3) (0,2,1) (0,3,4) (1,4,2) (2,3,5) (3,4,6)

MST goal: pick 4 edges (V-1) connecting all 5 vertices, minimum total weight.


───────────────────────────────────────────────────────────────────────
PRIM'S — grow from a single vertex (start = 0)
───────────────────────────────────────────────────────────────────────

  Tree starts as {0}. Heap holds candidate edges from tree to non-tree.

  Step 1: tree = {0}                  Heap: [(0,1,3), (0,2,1), (0,3,4)]
  Pick min edge (0,2,1)               Add 2.

  Step 2: tree = {0, 2}               Heap: [(0,1,3), (0,3,4), (2,3,5)]
  Pick min edge (0,1,3)               Add 1.

  Step 3: tree = {0, 1, 2}            Heap: [(1,4,2), (0,3,4), (2,3,5)]
  Pick min edge (1,4,2)               Add 4.

  Step 4: tree = {0, 1, 2, 4}         Heap: [(0,3,4), (2,3,5), (3,4,6)]
  Pick min edge (0,3,4)               Add 3.

  Tree complete: {0, 1, 2, 3, 4}     Total weight = 1 + 3 + 2 + 4 = 10


───────────────────────────────────────────────────────────────────────
KRUSKAL'S — sort all edges, pick each that doesn't form a cycle (DSU)
───────────────────────────────────────────────────────────────────────

  Sorted edges (by weight):
    (0,2,1) → (1,4,2) → (0,1,3) → (0,3,4) → (2,3,5) → (3,4,6)

  Initial DSU: {0} {1} {2} {3} {4}    (5 components)

  Edge (0,2,1):  find(0)≠find(2)?  yes → union   {0,2} {1} {3} {4}     +1
  Edge (1,4,2):  find(1)≠find(4)?  yes → union   {0,2} {1,4} {3}       +2
  Edge (0,1,3):  find(0)≠find(1)?  yes → union   {0,1,2,4} {3}         +3
  Edge (0,3,4):  find(0)≠find(3)?  yes → union   {0,1,2,3,4}           +4
  ── stop: V-1 = 4 edges used ──

  Total weight = 1 + 2 + 3 + 4 = 10   ✅ same as Prim's


───────────────────────────────────────────────────────────────────────
THE MST (both algorithms produce the same tree here):
───────────────────────────────────────────────────────────────────────

         3
     (0)─────(1)
      │        │
    1 │      2 │                MST edges: (0,2,1), (0,1,3), (1,4,2), (0,3,4)
      │        │                Total: 10
     (2)      (4)
      │
    ? │  ← wait, the original had edge (0,3,4) not (2,3,5).
   (3)─┘    The MST picks (0,3,4) over (2,3,5).


MENTAL HOOK:
  Prim's   — like a spreading bushfire from one starting tree.
  Kruskal's — like a global edge auction; cheapest non-cycling
              edge wins each round.
  Both are GREEDY; both end at the same total weight (when unique).
```

> **Two algorithms** — both correct, both `O(E log V)` ish:

| Algorithm | Data structure | Best for |
| --- | --- | --- |
| **Prim's** | PriorityQueue | Dense graphs; grows MST from a single vertex |
| **Kruskal's** | DSU (Union-Find) | Sparse graphs; processes edges globally in sorted order |

### Prim's Algorithm [G-45]

**Idea:** start with any vertex. Maintain a PQ of edges-into-MST. Repeatedly pick the smallest edge that adds a new vertex.

```java
public int primMST(int V, List<List<int[]>> adj) {
    boolean[] inMST = new boolean[V];
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, 0});             // {weight, vertex}
    int totalWeight = 0;
    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int w = curr[0];
        int u = curr[1];
        if (inMST[u]) {
            continue;
        }
        inMST[u] = true;
        totalWeight += w;
        for (int[] edge : adj.get(u)) {
            int v = edge[0];
            int wt = edge[1];
            if (!inMST[v]) {
                pq.offer(new int[]{wt, v});
            }
        }
    }
    return totalWeight;
}
```

### Kruskal's Algorithm [G-47]

**Idea:** sort all edges by weight. Greedily add each edge if it doesn't form a cycle. Cycle check = DSU `find()`.

```java
public int kruskalMST(int V, int[][] edges) {
    Arrays.sort(edges, (a, b) -> a[2] - b[2]);
    DSU dsu = new DSU(V);
    int totalWeight = 0;
    int edgesUsed = 0;
    for (int[] edge : edges) {
        int u = edge[0];
        int v = edge[1];
        int w = edge[2];
        if (dsu.find(u) != dsu.find(v)) {
            dsu.union(u, v);
            totalWeight += w;
            edgesUsed++;
            if (edgesUsed == V - 1) {
                break;
            }
        }
    }
    return totalWeight;
}
```

(See DSU section below for the `DSU` class implementation.)

> **When to prefer which:**
> - **Prim's** is naturally local — easier when graph is given as an adjacency list, especially dense graphs
> - **Kruskal's** is naturally edge-list based — easier when you're given `int[][] edges` directly, and great for sparse graphs

> 🧩 **Try these (MST):**
> - 🟡 Practice problem: Prim's MST (Striver/GFG)
> - 🟡 Practice problem: Kruskal's MST (Striver/GFG)
> - 🟡 **LC 1584** Min Cost to Connect All Points — classic MST
> - 🟡 **LC 1135** Connecting Cities With Minimum Cost
> - 🔴 **LC 1489** Find Critical and Pseudo-Critical Edges in MST

---

## 🔗 Disjoint Set Union (DSU / Union-Find) [Striver G-46 through G-53] 🔴 *Advanced*

> **What it is:** a data structure that efficiently tracks **set membership** and supports two operations:
> - `find(x)` — return the representative of the set containing `x`
> - `union(x, y)` — merge the sets containing `x` and `y`

### 🎨 Visual — DSU as a Forest of Up-Pointing Trees

```
Each set is a tree. The root represents the set.
parent[root] = root (self-loop semantically).


INITIAL — 5 singleton sets:

  parent: [0, 1, 2, 3, 4]                  (0)  (1)  (2)  (3)  (4)
  rank:   [0, 0, 0, 0, 0]                   ↑    ↑    ↑    ↑    ↑
                                            └────each is its own root


AFTER union(0, 1) — attach lower-rank under higher-rank.
Both have rank 0, so we pick a root and bump its rank:

  parent: [0, 0, 2, 3, 4]                       (0)   (2)  (3)  (4)
  rank:   [1, 0, 0, 0, 0]                       /
                                              (1)

AFTER union(2, 3):

  parent: [0, 0, 2, 2, 4]                       (0)   (2)  (4)
  rank:   [1, 0, 1, 0, 0]                       /     /
                                              (1)   (3)

AFTER union(1, 3) — find(1)=0, find(3)=2. Both have rank 1.
Attach 2 under 0, bump rank of 0:

  parent: [0, 0, 0, 2, 4]                       (0)            (4)
  rank:   [2, 0, 1, 0, 0]                      / \
                                             (1) (2)
                                                  │
                                                 (3)

  find(3) walks: 3 → 2 → 0     (depth 2)
```

### 🎨 Visual — Path Compression in Action

```
BEFORE find(3):           AFTER find(3) (compression flattens path):

      (0)                        (0)
     / \                       / | \
   (1) (2)                  (1)(2)(3)        ← 3 now points directly to root!
        │
       (3)                  Future find(3) is O(1).

Recursion: find(3) = find(2) = find(0) = 0;
           on the way back, every node's parent is rewritten to 0.

  Before: parent = [0, 0, 0, 2, 4]
  After:  parent = [0, 0, 0, 0, 4]   ← only parent[3] changed
```

### 🎨 Visual — Why Both Optimizations Matter

```
WITHOUT optimizations (worst case):           WITH both (amortized O(α(N)) ≈ O(1)):

  union(1,0), union(2,1), union(3,2), ...       Any sequence of N operations
  forms a LINKED LIST:                          takes total O(N α(N)) time.

       (0)                                      Trees stay shallow because:
        ↑                                        - union by rank limits height
       (1)                                        to O(log N)
        ↑                                        - path compression flattens
       (2)                                        them further on every find
        ↑
       (3)        ← find(3) is O(N)!
        ⋮

  Without rank, every union could double the depth.
  Without compression, every find re-walks the same path.
```

> **Why it matters:** Kruskal's MST, connected-components in online queries, account merging, network connectivity — DSU is the right tool whenever the problem is *"is X in the same group as Y?"* over time.

### Canonical Implementation (with both optimizations)

> **Two optimizations are mandatory:**
> 1. **Union by rank/size** — attach smaller tree under larger tree's root
> 2. **Path compression** — during `find`, flatten the tree so future lookups are O(1)

> Together they give nearly-O(1) amortized per operation (technically O(α(N)) where α is the inverse Ackermann function — effectively constant).

```java
class DSU {
    private int[] parent;
    private int[] rank;
    private int[] size;
    private int components;

    public DSU(int n) {
        parent = new int[n];
        rank = new int[n];
        size = new int[n];
        components = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            rank[i] = 0;
            size[i] = 1;
        }
    }

    public int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);   // path compression
        }
        return parent[x];
    }

    public boolean union(int x, int y) {
        int rootX = find(x);
        int rootY = find(y);
        if (rootX == rootY) {
            return false;                  // already in same set
        }
        // union by rank
        if (rank[rootX] < rank[rootY]) {
            parent[rootX] = rootY;
            size[rootY] += size[rootX];
        } else if (rank[rootX] > rank[rootY]) {
            parent[rootY] = rootX;
            size[rootX] += size[rootY];
        } else {
            parent[rootY] = rootX;
            size[rootX] += size[rootY];
            rank[rootX]++;
        }
        components--;
        return true;
    }

    public boolean connected(int x, int y) {
        return find(x) == find(y);
    }

    public int componentSize(int x) {
        return size[find(x)];
    }

    public int countComponents() {
        return components;
    }
}
```

> **Memorize the find recursion:** the path-compression line `parent[x] = find(parent[x])` is the entire optimization. Without it, find is `O(tree depth)`; with it, amortized `O(α(N))`.

### Applications [Striver G-48 to G-53]

| Striver | Problem | DSU's role |
| --- | --- | --- |
| G-48 | **LC 547** Number of Provinces (via DSU) | Union connected pairs; count components |
| G-49 | **LC 1319** Number of Operations to Make Network Connected | Count components and redundant edges |
| G-50 | **LC 721** Accounts Merge | Union emails sharing the same account |
| G-51 | **LC 305** Number of Islands II | Online queries: each "add land" potentially merges islands |
| G-52 | **LC 827** Making a Large Island | Pre-union islands; try each water cell as bridge |
| G-53 | **LC 947** Most Stones Removed | Union stones sharing row or column; answer = stones - components |

> **LC 305 is the canonical "online queries" problem.** You can't just run BFS/DFS after each query — too slow. DSU shines here because each query is amortized O(1).

```java
// LC 1319 — Min Operations to Make Network Connected (sketch)
public int makeConnected(int n, int[][] connections) {
    if (connections.length < n - 1) {
        return -1;                         // not enough cables
    }
    DSU dsu = new DSU(n);
    for (int[] conn : connections) {
        dsu.union(conn[0], conn[1]);
    }
    return dsu.countComponents() - 1;      // edges needed to merge components
}
```

> 🧩 **Try these (DSU ladder):**
> - ✅ Practice: implement DSU from scratch (with both optimizations)
> - ✅ **LC 547** Number of Provinces — via DSU (G-48)
> - ✅ **LC 1319** Make Network Connected (G-49)
> - 🟡 **LC 721** Accounts Merge (G-50) — string-keyed DSU
> - 🟡 **LC 1584** Min Cost to Connect All Points — Kruskal's MST = sort + DSU
> - 🟡 **LC 947** Most Stones Removed (G-53)
> - 🔴 **LC 305** Number of Islands II (G-51) — online queries
> - 🔴 **LC 827** Making a Large Island (G-52)

---

## 🚀 Advanced Topics — Senior+ Only [Striver G-54, G-55, G-56]

> **Honest take:** these come up rarely in SDE / SDE2 interviews. They're worth understanding **after** the rest is solid — and possibly knowing the names without implementing from memory unless you're targeting senior+ roles or specific systems-heavy positions.

### Kosaraju's Algorithm — Strongly Connected Components [G-54]

> **SCC:** maximal sets of vertices where each vertex is reachable from every other (respecting direction).

**Steps in plain English:**

1. DFS the original graph, pushing each vertex onto a stack **after** finishing it (just like topo sort).
2. Transpose the graph (reverse all edges).
3. Pop vertices from the stack; each unvisited pop starts a DFS on the transposed graph — that DFS reveals one SCC.

```java
public List<List<Integer>> kosaraju(int V, List<List<Integer>> adj) {
    // Step 1 — DFS on original, push order
    boolean[] visited = new boolean[V];
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < V; i++) {
        if (!visited[i]) {
            dfs1(i, adj, visited, stack);
        }
    }
    // Step 2 — transpose
    List<List<Integer>> revAdj = new ArrayList<>();
    for (int i = 0; i < V; i++) {
        revAdj.add(new ArrayList<>());
    }
    for (int u = 0; u < V; u++) {
        for (int v : adj.get(u)) {
            revAdj.get(v).add(u);
        }
    }
    // Step 3 — DFS in pop order on transposed graph
    Arrays.fill(visited, false);
    List<List<Integer>> sccs = new ArrayList<>();
    while (!stack.isEmpty()) {
        int u = stack.pop();
        if (!visited[u]) {
            List<Integer> scc = new ArrayList<>();
            dfs2(u, revAdj, visited, scc);
            sccs.add(scc);
        }
    }
    return sccs;
}

private void dfs1(int u, List<List<Integer>> adj, boolean[] visited, Deque<Integer> stack) {
    visited[u] = true;
    for (int v : adj.get(u)) {
        if (!visited[v]) {
            dfs1(v, adj, visited, stack);
        }
    }
    stack.push(u);
}

private void dfs2(int u, List<List<Integer>> revAdj, boolean[] visited, List<Integer> scc) {
    visited[u] = true;
    scc.add(u);
    for (int v : revAdj.get(u)) {
        if (!visited[v]) {
            dfs2(v, revAdj, visited, scc);
        }
    }
}
```

### Tarjan's Algorithm — Bridges [G-55]

> **Bridge:** an edge whose removal disconnects the graph (increases component count). Critical for understanding "single points of failure" in networks.

> **Key insight:** during DFS, an edge `(u, v)` is a bridge iff there's no back-edge from `v`'s subtree that reaches `u` or higher. Track `disc[]` (discovery time) and `low[]` (lowest disc reachable).

```java
private int timer = 0;
private List<List<Integer>> bridges;

public List<List<Integer>> findBridges(int V, List<List<Integer>> adj) {
    bridges = new ArrayList<>();
    int[] disc = new int[V];
    int[] low = new int[V];
    Arrays.fill(disc, -1);
    for (int i = 0; i < V; i++) {
        if (disc[i] == -1) {
            dfs(i, -1, adj, disc, low);
        }
    }
    return bridges;
}

private void dfs(int u, int parent, List<List<Integer>> adj, int[] disc, int[] low) {
    disc[u] = low[u] = timer++;
    for (int v : adj.get(u)) {
        if (v == parent) {
            continue;
        }
        if (disc[v] == -1) {
            dfs(v, u, adj, disc, low);
            low[u] = Math.min(low[u], low[v]);
            if (low[v] > disc[u]) {
                bridges.add(Arrays.asList(u, v));
            }
        } else {
            low[u] = Math.min(low[u], disc[v]);
        }
    }
}
```

### Articulation Points [G-56]

> **Articulation point:** a vertex whose removal disconnects the graph. Similar logic to bridges: `u` is an articulation point if it has a child `v` where `low[v] >= disc[u]`, OR `u` is the root with ≥ 2 children.

```java
// Sketch — same structure as bridges; differs in the comparison and root-handling
if (parent == -1 && childrenCount > 1) {
    isArticulation[u] = true;
}
if (parent != -1 && low[v] >= disc[u]) {
    isArticulation[u] = true;
}
```

> 🧩 **Try these (Advanced — only if time permits):**
> - 🔴 Practice problem: Kosaraju SCC (Striver/GFG)
> - 🔴 **LC 1192** Critical Connections in a Network — bridges
> - 🔴 Practice problem: Articulation points

---

## 🎨 Style Habits — Build These From Day 1

### 🌐 Universal Habits (apply to every graph problem)

#### Habit 1 — Always initialize `visited` BEFORE any traversal

Forgetting this is the #1 source of "infinite recursion" / TLE bugs.

```java
// ❌ Missing visited — infinite recursion on cycles
private void dfs(int u, List<List<Integer>> adj) {
    for (int v : adj.get(u)) {
        dfs(v, adj);
    }
}

// ✅ Always pass visited
private void dfs(int u, List<List<Integer>> adj, boolean[] visited) {
    visited[u] = true;
    for (int v : adj.get(u)) {
        if (!visited[v]) {
            dfs(v, adj, visited);
        }
    }
}
```

---

#### Habit 2 — Mark visited BEFORE adding to queue, not after polling

Already covered in BFS section. Repeating because it's the single biggest source of BFS bugs.

---

#### Habit 3 — Use `Deque` over legacy `Stack`

```java
// ❌ Legacy, synchronized, slow
Stack<Integer> stack = new Stack<>();

// ✅ Modern, fast
Deque<Integer> stack = new ArrayDeque<>();
stack.push(x);
stack.pop();
stack.peek();
```

---

#### Habit 4 — Use direction arrays for grid problems

```java
// ❌ Four duplicate calls
dfs(grid, r - 1, c);
dfs(grid, r + 1, c);
dfs(grid, r, c - 1);
dfs(grid, r, c + 1);

// ✅ Loop over direction arrays
private static final int[] DR = {-1, 1, 0, 0};
private static final int[] DC = {0, 0, -1, 1};
for (int d = 0; d < 4; d++) {
    dfs(grid, r + DR[d], c + DC[d]);
}
```

---

#### Habit 5 — Bounds-check FIRST in DFS/BFS, before any other check

```java
// ✅ Order matters — bounds first, then content
if (!inBounds(r, c, rows, cols) || grid[r][c] != '1') {
    return;
}
```

If you content-check first, you'll get an `ArrayIndexOutOfBoundsException`.

---

### 🔧 Context-Specific Habits

#### Habit 6 — Cycle detection: undirected needs parent; directed needs recursion stack

```java
// ❌ Using only `visited` in directed → false positive cycle on diamond shape
if (visited[v]) {
    return true;
}

// ✅ Directed needs pathVisited (currently in recursion path)
if (pathVisited[v]) {
    return true;
}
```

---

#### Habit 7 — In Dijkstra, handle stale heap entries

```java
// ✅ Always check if the polled distance is current
if (d > dist[u]) {
    continue;
}
```

Without this, you'll relax via outdated distances → wrong answer or TLE.

---

#### Habit 8 — In DSU, always recompute roots after `union`

```java
// ❌ Roots can change after path compression in subsequent finds
int rootX = dsu.find(x);
int rootY = dsu.find(y);
dsu.union(x, y);
// Don't use rootX or rootY here; they may be stale

// ✅ Re-find after union
dsu.union(x, y);
int newRoot = dsu.find(x);
```

---

#### Habit 9 — Pre-size adjacency list to avoid reallocation

```java
// ❌ Default capacity, repeated grow-and-copy
List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i < V; i++) {
    adj.add(new ArrayList<>());
}

// ✅ Same code, but if you know average degree, pre-size the inner lists:
adj.add(new ArrayList<>(avgDegree));
```

Minor, but matters on very large graphs.

---

## 🐞 Common Bugs (Hall of Fame)

### Bug 1 — Infinite recursion / TLE because `visited` not used

```java
// ❌
private void dfs(int u, List<List<Integer>> adj) {
    for (int v : adj.get(u)) {
        dfs(v, adj);
    }
}

// ✅
private void dfs(int u, List<List<Integer>> adj, boolean[] visited) {
    if (visited[u]) {
        return;
    }
    visited[u] = true;
    for (int v : adj.get(u)) {
        dfs(v, adj, visited);
    }
}
```

---

### Bug 2 — BFS visits same vertex twice (marks AFTER polling)

```java
// ❌ Marks too late
while (!queue.isEmpty()) {
    int u = queue.poll();
    if (visited[u]) continue;
    visited[u] = true;
    for (int v : adj.get(u)) {
        queue.offer(v);
    }
}

// ✅ Mark BEFORE enqueueing
while (!queue.isEmpty()) {
    int u = queue.poll();
    for (int v : adj.get(u)) {
        if (!visited[v]) {
            visited[v] = true;
            queue.offer(v);
        }
    }
}
```

---

### Bug 3 — Undirected cycle detection without parent check

Covered in Cycle Detection Undirected section. Always pass parent and skip `v == parent`.

---

### Bug 4 — Directed cycle detection with only one visited array

Covered in Cycle Detection Directed section. Need BOTH `visited` and `pathVisited`.

---

### Bug 5 — Dijkstra with negative edge weights

> Dijkstra's greedy invariant breaks. Use Bellman-Ford instead.

```java
// Edge weights: {-1, 2, -3}
// Dijkstra will commit early and miss the better path through a negative-weight detour
```

---

### Bug 6 — Forgetting to use PriorityQueue in Dijkstra

```java
// ❌ Plain Queue — visits in insertion order, not shortest-distance order
Queue<int[]> queue = new ArrayDeque<>();

// ✅ Min-heap on distance
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
```

---

### Bug 7 — Integer overflow in Floyd-Warshall

```java
// ❌ MAX_VALUE + 5 = overflow → negative number → wrong answer
Arrays.fill(row, Integer.MAX_VALUE);

// ✅ Use MAX_VALUE / 2 so additions stay safe
Arrays.fill(row, Integer.MAX_VALUE / 2);
```

---

### Bug 8 — DSU without path compression → TLE on large inputs

```java
// ❌ O(tree height) per find — degenerates to O(N) on adversarial input
public int find(int x) {
    if (parent[x] == x) {
        return x;
    }
    return find(parent[x]);
}

// ✅ Path compression — flattens the tree
public int find(int x) {
    if (parent[x] != x) {
        parent[x] = find(parent[x]);
    }
    return parent[x];
}
```

---

### Bug 9 — `static` fields leaking across LeetCode test cases

```java
// ❌ persists across cases
private static int count = 0;

// ✅ instance field + reset at top of entry method
private int count;
public int solve(...) {
    count = 0;
    ...
}
```

Same lesson as trees doc.

---

### Bug 10 — Grid bounds check ordering

```java
// ❌ ArrayIndexOutOfBoundsException
if (grid[r][c] != '1' || !inBounds(r, c, rows, cols)) {
    return;
}

// ✅ Bounds first
if (!inBounds(r, c, rows, cols) || grid[r][c] != '1') {
    return;
}
```

---

## 🗺️ Practice Plan — Tiered

### Tier 1 — Foundations (must be muscle memory) ✅

Striver videos: **G-1 through G-12**.

1. ✅ Build adjacency list (directed + undirected, weighted + unweighted)
2. ✅ BFS — given a graph, output traversal from vertex 0
3. ✅ DFS — same
4. ✅ **LC 547** Number of Provinces (G-7)
5. ✅ **LC 200** Number of Islands (G-8)
6. ✅ **LC 733** Flood Fill (G-9)
7. ✅ **LC 994** Rotten Oranges (G-10)
8. ✅ Cycle detection undirected (BFS and DFS) (G-11, G-12)

> **Day 1 of your sprint targets this tier.**

---

### Tier 2 — Bipartite + Directed Cycle + Topo Sort 🟡

Striver videos: **G-13 through G-26**.

9. 🟡 **LC 542** 01 Matrix (G-13)
10. 🟡 **LC 130** Surrounded Regions (G-14)
11. 🟡 **LC 1020** Number of Enclaves (G-15)
12. 🟡 **LC 785** Bipartite Graph (G-17, G-18)
13. 🟡 Cycle detection directed (G-19)
14. 🟡 **LC 802** Eventual Safe States (G-20, G-25)
15. 🟡 Topological Sort — DFS + Kahn's (G-21, G-22)
16. 🟡 **LC 207** Course Schedule (G-24) — **must-do**
17. 🟡 **LC 210** Course Schedule II (G-24) — **must-do**
18. 🟡 **LC 269** Alien Dictionary (G-26)

> **Day 2 of your sprint targets this tier.**

---

### Tier 3 — Shortest Path Family 🟡

Striver videos: **G-27 through G-43**.

19. 🟡 SP in DAG (G-27)
20. 🟡 SP in unweighted (G-28)
21. 🟡 **LC 127** Word Ladder (G-29)
22. 🟡 Dijkstra basic (G-32, G-33, G-34)
23. 🟡 **LC 743** Network Delay Time (vanilla Dijkstra)
24. 🟡 **LC 1091** Shortest Path in Binary Matrix (G-36)
25. 🟡 **LC 1631** Path With Minimum Effort (G-37)
26. 🟡 **LC 787** Cheapest Flights With K Stops (G-38)
27. 🔴 Bellman-Ford (G-41)
28. 🔴 Floyd-Warshall (G-42, G-43)

> **Day 3 of your sprint targets the must-do subset of this tier (G-27 to G-35).**

---

### Tier 4 — MST + DSU 🟡

Striver videos: **G-44 through G-53**.

29. 🟡 DSU implementation from scratch (G-46)
30. 🟡 Prim's MST (G-45)
31. 🟡 Kruskal's MST (G-47)
32. 🟡 **LC 547** Provinces via DSU (G-48)
33. 🟡 **LC 1319** Network Connected (G-49)
34. 🟡 **LC 721** Accounts Merge (G-50)
35. 🟡 **LC 947** Most Stones Removed (G-53)
36. 🟡 **LC 1584** Min Cost to Connect All Points (Kruskal's)
37. 🔴 **LC 305** Number of Islands II (G-51)
38. 🔴 **LC 827** Making a Large Island (G-52)

---

> # 🎯 STOP HERE — Medium-Interview Cutoff
>
> **Everything from Tier 1 through Tier 4 above is the medium-interview essentials.** If you can do these confidently, you are ready for SDE2 / SDE3 medium-loop graph questions at Walmart, Amazon, Microsoft, and similar.
>
> **Why Tier 4 is non-negotiable** — DSU is a *direct ask* in medium loops. These problems are almost always solved with Union-Find and are very frequently picked by interviewers:
>
> - **LC 547** Number of Provinces — DSU
> - **LC 1319** Number of Operations to Make Network Connected — DSU
> - **LC 721** Accounts Merge — DSU
> - **LC 947** Most Stones Removed — DSU
> - **LC 1584** Min Cost to Connect All Points — Kruskal's (DSU under the hood)
>
> **Stretch (optional, weight only if time permits):** Bellman-Ford (Tier 3 last two), LC 305, LC 827.
>
> **Lesson learned the hard way (May 2026):** I (Kapil) initially planned a 3-day graph sprint targeting only through Tier 3 (Shortest Path). That plan is too short for medium loops — DSU questions show up too often to skip. **Extend the sprint to 4 days minimum**, with Day 4 covering DSU implementation + the five LC problems above.
>
> **Past this point → Tier 5 is Senior+ only. Safe to skip for medium interviews.**

---

### Tier 5 — Advanced (Senior+ Reference Only) 🔴

Striver videos: **G-54 through G-56**.

39. 🔴 Kosaraju's SCC (G-54)
40. 🔴 Tarjan's bridges (G-55) — **LC 1192**
41. 🔴 Articulation Points (G-56)

> **Do NOT attempt cold.** Read theory, understand the names, implement only if you have time after Tier 4.

---

### How to use this plan

> **Lesson learned the hard way (May 2026):** I (Kapil) attempted LC 124 (in trees doc) before completing the bottom-up DFS + two-purpose recursion ladder. It cost me an hour. **The same risk applies here — climb tiers in order.**

- **Day 1:** Tier 1 — BFS, DFS, undirected cycle, grid problems
- **Day 2:** Tier 2 — Bipartite, directed cycle, **Topo Sort + Course Schedule (highest-value)**
- **Day 3:** Tier 3 — Shortest path family, **Dijkstra (highest-value)**
- **Day 4:** Tier 4 — **DSU + MST (medium-interview essentials, do NOT skip)**
- **Eventually:** Tier 5 (only if targeting senior+ roles)

> **Minimum viable plan for medium loops = 4 days, ending at Tier 4.** Skipping DSU is the single most common gap that costs candidates a medium-loop graph question.

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

These compile and run, but produce wrong output. Each is a *real* trap.

---

**1. BFS visits same vertex multiple times.**

Mark `visited` BEFORE enqueuing. See Bug 2.

---

**2. Undirected cycle detection without parent skip.**

Every edge looks like a cycle. See Cycle Detection Undirected.

```java
// ✅ Always check v != parent
if (visited[v] && v != parent) {
    return true;
}
```

---

**3. Directed cycle detection with only `visited`.**

False positives on DAG diamond shapes. Need `pathVisited` (recursion stack). See Cycle Detection Directed.

---

**4. Dijkstra on negative weights.**

Wrong answer. Use Bellman-Ford.

---

**5. Floyd-Warshall overflow.**

`Integer.MAX_VALUE + w` → wraps to negative. Use `MAX_VALUE / 2`.

---

**6. DSU without union-by-rank AND path compression.**

O(N) per find on adversarial input → TLE.

---

**7. Topological sort on non-DAG.**

Has no valid output. Use the size-check trick: `if (order.size() < V) cycle exists`.

---

**8. Forgot to handle disconnected graphs.**

```java
// ❌ Misses unreachable components
bfs(0, adj, visited);

// ✅ Iterate over all vertices
for (int i = 0; i < V; i++) {
    if (!visited[i]) {
        bfs(i, adj, visited);
    }
}
```

---

**9. Grid 4 vs 8 directions.**

Read the problem carefully. Most are 4-directional; LC 1091 (binary matrix) is 8.

---

**10. `static` fields persist across LeetCode test cases.**

Use instance fields + reset at top of public method. Same lesson as trees doc.

---

**11. PriorityQueue comparator on `int[]` — beware of overflow.**

```java
// ❌ a[0] - b[0] overflows if values can be MIN_VALUE / MAX_VALUE
new PriorityQueue<int[]>((a, b) -> a[0] - b[0]);

// ✅ Safe with Integer.compare
new PriorityQueue<int[]>((a, b) -> Integer.compare(a[0], b[0]));
```

Cross-reference: `Reference/code-style-for-dsa-reference.md` → integer overflow gotcha.

---

**12. Mistaking matrix `[r][c]` for `(x, y)`.**

In a matrix, `r` is row (y-axis, top-to-bottom) and `c` is column (x-axis, left-to-right). Moving "up" means `r - 1`, not `c - 1`. Easy to flip in your head under pressure.

---

## ⚡ Quick Cheat Sheet

| If you need... | Use... |
| --- | --- |
| Shortest path in unweighted graph | **BFS** |
| Shortest path with non-negative weights | **Dijkstra** (PQ) |
| Shortest path with negative weights | **Bellman-Ford** |
| All-pairs shortest path (small V) | **Floyd-Warshall** |
| Shortest path in DAG | **Topo Sort + Relaxation** |
| Detect cycle, undirected | **DFS/BFS with parent tracking** |
| Detect cycle, directed | **DFS with `pathVisited`** OR **Kahn's: order.size() != V** |
| Process tasks with dependencies | **Topological Sort** |
| Two-color a graph | **Bipartite BFS/DFS** |
| Count islands / regions / components | **DFS or BFS with component counter** |
| Flood fill / region paint | **DFS or BFS from source(s)** |
| Multi-source shortest distance to nearest source | **Multi-source BFS** (all sources enqueued at start) |
| Merge groups / online connectivity queries | **DSU (Union-Find)** |
| Minimum cost to connect all nodes | **MST: Prim's (PQ) or Kruskal's (DSU)** |
| Track which vertices are reachable from start | **DFS with visited set** |
| Find all paths from A to B | **DFS + backtracking** (cap depth if very deep) |
| Strongly Connected Components | **Kosaraju's** (or Tarjan's SCC) — 🔴 |
| Bridges (critical edges) | **Tarjan's bridges** — 🔴 |
| Articulation points (critical vertices) | **Tarjan's articulation points** — 🔴 |

---

## 🧾 TL;DR — One-Page Summary

1. **Default representation:** adjacency list (`List<List<Integer>>` unweighted, `List<List<int[]>>` weighted)
2. **BFS** for unweighted shortest paths; **DFS** for "all paths", "connected components", "cycle detection"
3. **Cycle detection:** undirected → parent tracking; directed → `pathVisited` (recursion stack)
4. **Topological sort:** DFS-push-on-finish OR Kahn's BFS with in-degree. Cycle detection trick: `order.size() < V` means cycle
5. **Dijkstra:** PriorityQueue, non-negative weights, `O((V+E) log V)`. Handle stale entries with `d > dist[u]` skip
6. **Bellman-Ford:** V-1 iterations relax every edge; V-th catches negative cycles. `O(V·E)`
7. **Floyd-Warshall:** triple loop, `dist[i][j] = min(dist[i][j], dist[i][k] + dist[k][j])`. `O(V³)`. Use `MAX_VALUE / 2`
8. **DSU:** two optimizations are non-negotiable — union-by-rank + path compression. Effectively O(1) amortized
9. **MST:** Prim's (PQ, grow from one vertex) OR Kruskal's (sort edges, DSU cycle check)
10. **Grid problems:** treat cells as vertices, 4-directional default, use `DR[]`/`DC[]` arrays
11. **Multi-source BFS:** when distance is from the nearest of multiple starting points, enqueue all sources at time 0
12. **Bipartite:** 2-color via BFS/DFS; conflict = not bipartite ↔ odd cycle exists
13. **Stale-entry skip** is mandatory for Dijkstra: `if (d > dist[u]) continue;`
14. **Mark BEFORE enqueue** in BFS, not after polling
15. **No `static` fields** for problem state on LeetCode — use instance fields + reset

> **Graphs are mostly traversal problems wearing different hats.** Master BFS, DFS, topo sort, and DSU — the rest is variations on these four.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Recursion fundamentals (Pattern 7 — Two-Purpose) | `DeepDive/recursion-fundamentals.md` |
| Tree deep dive + 4 tree patterns | `DeepDive/trees-fundamentals.md` |
| Tree reference (compact cheatsheet) | `Reference/trees-reference.md` |
| Code style refactor recipes | `Reference/code-style-for-dsa-reference.md` |
| HashMap fundamentals | `Reference/hashmap-section-updated.md` |
| LC 124 pattern dive (Two-Purpose Recursion) | `Patterns/max-path-sum-binary-tree-problem.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | Initial version. Curriculum aligned to Striver's Graph Series (56 videos, G-1 through G-56). Tiered practice plan (5 tiers) with explicit difficulty tags. Code rewritten in project style. Decision frameworks (5-question funnel + keyword signals) added as this doc's pedagogical contribution. |
| May 2026 | **Visual reference pass.** Added 9 ASCII diagram blocks: graph types reference (undirected/directed/weighted/cyclic/DAG/tree/connected/disconnected/complete, self-loop, parallel edges); adjacency list vs matrix side-by-side; connected-components diagram; BFS level-by-level trace; DFS recursion-stack trace + BFS-vs-DFS comparison; undirected cycle detection (parent edge vs back edge); bipartite vs odd-cycle counter-example; directed cycle white/gray/black coloring; Kahn's topological sort step-by-step animation; Dijkstra's relaxation trace (with stale-entry handling); Prim vs Kruskal on the same graph; DSU forest visualization (initial → unions → path compression flattening). |
| May 2026 | **BFS / DFS marking-discipline annotation pass.** Heavily annotated the canonical BFS template, the ❌/✅ mark-on-poll vs mark-on-offer comparison, the recursive DFS template, and the iterative DFS template with inline `← ` arrow comments pinpointing *why* `visited[]` is mutated at each line. Added "BFS-vs-DFS marking rule — one sentence." Added a compact "🧭 Interview default — recursive or iterative?" callout citing ~85% recursive in interview-prep ecosystem with a 3-trigger switch table. |
| May 2026 | **Medium vs Advanced split.** Added "📍 Reading Roadmap" near the top showing what to read for SDE-2 vs senior+. Inserted a big visual `═══ ✋ END OF MEDIUM-LEVEL SCOPE ═══` divider after Topological Sort (G-26) — everything before it is the medium-level core (~80% of LC graph problems); everything after is senior+ territory. Tagged advanced section headers (Shortest Path, MST, DSU) with 🟡/🔴 markers so the tier is visible at a glance. |
| May 2026 | **Grid + first-timer teaching pass.** Reader feedback: grid section was too dense for first-timers. Added: (1) "First-timer bridge" callout + 🎨 *"A grid IS a graph"* visual showing same matrix drawn two ways + "what's different from adjacency-list graphs" table; (2) 🎨 *"Why the direction arrays are paired that way"* compass-style visual replacing the cryptic memorize-it note; (3) "Two-loop mental model" table for Number of Islands explaining the outer-discovery / inner-consumption pattern + 🎨 full DFS island-sweep trace with recursion tree; (4) "Multiple pebbles in a pond" intuition + 🎨 concentric-ripple visual for Multi-Source BFS + complexity table showing why one BFS beats S separate BFS calls; (5) "Complement trick" mental model + table + 🎨 LC 130 phase-by-phase visual for Reverse-Direction BFS; (6) Paint-bucket analogy for Flood Fill; (7) "Shape = picked-up-and-slid" hook for Distinct Islands + 🎨 T-vs-L collision visual showing why the 'B' marker is essential. Strengthened DFS Topological Sort with "stack of completion" intuition + "why post-order not pre-order" explanation + 🎨 step-by-step stack-evolution visual on the prerequisite DAG. Added "two-team sports league" first-timer analogy to Bipartite section with real-world bipartite examples table. |
| May 2026 | **Adjacency-matrix vs grid-matrix confusion patch.** Lesson learned the hard way: a first-timer reading "grid IS a graph" can wrongly conflate the grid-problem matrix with an adjacency matrix and ask *"why is grid[1][1] = 0 when a vertex always reaches itself?"* Added ⚠️ trap callout + 🎨 side-by-side visual contrasting the two matrices (indices, cell-value meaning, diagonal interpretation, how connectivity is determined). The shape of the array tells you nothing — you have to read whether it's encoding a relationship (adjacency) or a state (grid). |
| May 2026 | **Self-consistency fix in the "grid IS a graph" visual + DR/DC memorization upgrade.** Reader feedback: the original graph-view drawing wrongly included `(0,2)` as a vertex even though `grid[0][2] = 0` (water). Fixed the drawing to show ONLY the 6 land cells as vertices, and explicitly labelled the resulting **two connected components**. Lesson: when drawing a graph derived from a matrix, validate cell-by-cell that every land cell appears and every water cell is absent. Also rewrote the DR/DC memorization section: replaced the bare "memorize these arrays" instruction with (a) Kapil's mnemonic *"DR changes row only; DC changes col only — exactly one is non-zero per move"*, (b) a fallback reconstruction recipe for when you forget the literals in an interview, and (c) an officially-recommended alternative pattern: a single 4×2 `DIRS[][]` array where each `{dr, dc}` pair sits together — many find this easier than two parallel 1-D arrays. The 8-directional case is shown in both styles, with the `DIRS8[][]` 3×3-punched-out-center layout highlighted as nearly unforgettable. |
| May 2026 | **Compass-visual alignment fix.** The original compass in the DR/DC section had its vertical axis (UP/DOWN arrows, bars, CELL labels) at column 21 while the center cell dot `●` sat at column 29 — an 8-column drift that made the compass look skewed. Re-laid the entire compass so every vertical-axis element (`d = 0`, `(UP, r-1, c)`, `▲`, `│`, `●`, `CELL (r, c)`, `│`, `▼`, `(DOWN, r+1, c)`, `d = 2`) has its visually-central character on column 32. **Process lesson:** for any ASCII compass/diagram with a center point, count the column of each element BEFORE submitting — don't trust eyeballing in a fixed-width editor (it lies). New rule for this knowledge base: every centered ASCII diagram must include a "column verification" pass where the author tabulates each label's center column and confirms all rows agree. |
| May 2026 | **Added problem-agnostic Grid DFS & BFS template section as a dedicated building block.** Reader feedback: the original grid section dove straight into LC 200 (Number of Islands) without first showing pure, problem-agnostic DFS / BFS templates — making it hard to separate the *template* from the *application*. New `## 🧱 Grid DFS and BFS Templates — your building blocks before any problem` section inserted right before Sub-Pattern 1, containing: (a) the canonical recursive grid-DFS template with inline `← ` comments explaining mark-on-entry as the DFS analog of mark-on-enqueue + implicit-base-case cross-ref to `recursion-fundamentals.md §3.4`; (b) the canonical grid-BFS template with mark-on-enqueue arrow comments + "why mark-on-poll is a correctness bug for distance-tracking BFS" callout; (c) 🎨 full DFS recursion-tree trace on a tiny 2×2 island showing every guard rejection (OOB, water, already-marked) explicitly; (d) 🎨 BFS layer-by-layer trace on the same 2×2 island with queue state after each step + KEY INVARIANT; (e) "Bounds-check first, value-check second" gotcha with the short-circuit-evaluation explanation; (f) "When to use DFS vs BFS on a grid" decision table; (g) stack-overflow caveat for huge grids → use iterative DFS. Both traces were validated cell-by-cell against the DR/DC ordering before submission. |
| May 2026 | **Added "Marking strategy — in-place vs separate `boolean[][] visited`" decision-framework callout** inside the Grid DFS/BFS Templates section (between the BFS visual and the DFS-vs-BFS decision table). Captures the question that came up while prepping for LC 200: *"is mutating the grid actually correct, and what should I do in the interview?"* Includes (a) the trade-off table (in-place = O(1) extra space, destructive; visited array = O(mn) extra space, non-destructive) with the explicit note that **Big-O is identical** either way because recursion-stack / queue already dominates; (b) 🧠 *why in-place works* — the value `'0'` becomes overloaded ("originally water" OR "already-visited land"); (c) the visited-array drop-in variant of the DFS template; (d) the **interview-day recommendation** — pick the variant your hands are confident with, then verbalize the trade-off upfront with a senior-framing script (*"I prefer not to mutate the caller's input — matches production Java style"*) vs the junior-framing trap (*"I'm more comfortable with this"*); (e) ❌/✅ same-choice / different-framing table; (f) lesson-learned-the-hard-way callout: **naming the trade-off matters more than picking the elegant variant**; (g) 🧾 TL;DR. |
| May 2026 | **Cross-reference added** in the "Top-down vs bottom-up DFS" callout pointing to `../../JavaBackend/DeepDive/java-pass-by-value-semantics.md` for the full mechanics of why `visited[][]` mutations propagate across recursive calls. The Java-language topic was extracted into its own deep dive (bootstrapping the JavaBackend subdomain) because it applies far beyond graphs — DP memo, DSU `parent[]`, BFS visited, backend DTO/JPA/Spring patterns — and didn't fit signal-densely into graphs-fundamentals.md. The graphs doc still names the *what* in one paragraph; the full *why* + the three strategies for opting out (defensive copy / deep copy / immutability) live in the JavaBackend deep dive. |
| May 2026 | **Three-part LC 200 first-attempt pass — captured the actual bugs from a real solo attempt.** During the first solo run at LC 200 with a separate `visited[][]` array, four distinct bugs surfaced (`grid[1].length` typo, outer-guard missing `grid == '1'` check, `r + dc` typo for column delta, class-field/local-variable shadowing). All four had teaching value for **every** grid problem — so they got captured as three new sections inside the Grid DFS/BFS Templates region. (a) **🧭 Top-down vs bottom-up DFS — `visited[][]` works for both styles** — inserted right after the marking-strategy callout. Shows the bottom-up DFS template for LC 695 Max Area of Island as a drop-in variant of the top-down LC 200 template, with a side-by-side table making it explicit that visited handling is byte-for-byte identical regardless of return type, plus a "pick the style by what the problem asks for" decision table (count → top-down, max/sum → bottom-up, exists → bottom-up boolean). Forward-flags the upcoming Java-language deep dive on pass-by-value-of-reference. (b) **🐞 Common bugs hall of fame — grid DFS** — inserted between the "When to use DFS vs BFS" decision table and the "🎯 You're now ready" hand-off. Tabulates all four bugs with what-it-does / how-to-fix columns, plus a **"verify before submit" 4-item checklist** to run mechanically on every grid problem. Includes the structural insight: `dr`/`dc`/`r`/`c`/`i`/`j` are six near-identical 1-2 character symbols interacting on the same line, so "be more careful" isn't a fix — a checklist is. (c) **🧠 Upgraded "two loops, two questions" mental model in Sub-Pattern 1** — replaced the existing "discovery vs consumption" callout with a richer version that adds the *required-checks column* per loop (outer = LAND + unvisited; inner = in-bounds + LAND + unvisited), and a ⚠️ trap explanation of *why* water cells never get marked visited (the DFS bails before reaching the mark step) — which is precisely why the outer guard needs the grid-value check. Includes the tiny 1×0/0×0 trace showing how a `!visited` -only guard produces 4 islands instead of 1. Cross-references the 🐞 bugs hall of fame. |
