# DSA DeepDive — Pending Work

> **Purpose:** tracks known gaps discovered through real interview failures. Each task is concrete — not "improve X" but "add Y to Z because it prevents bug B."

---

## 🔄 Changelog

| Date | Entry |
| --- | --- |
| June 2026 | Created. Two root gaps identified: (1) graph construction input patterns missing from graphs-fundamentals.md; (2) same "setup before the algorithm" gap exists across ALL DeepDive files. |

---

## Task 1 — Fix 3 "Setup Before the Algorithm" Gaps in `graphs-fundamentals.md`

**Why:** Kapil got caught in a real interview on a multi-source BFS problem. He recognised the algorithm (rotten oranges pattern) but stalled on (a) building the adjacency list from two raw arrays and (b) handling 1-indexed nodes. These are Phase 1 failures — the notes only teach Phase 2.

**Root cause:** every graph problem has two phases:
- **Phase 1 — Build the graph** from whatever raw input format the problem gives → MISSING from notes
- **Phase 2 — Run the algorithm** (BFS/DFS/etc.) → well-covered

### Gap A — Add "Graph Construction from Raw Input" section ⬜

**Where:** new section `## 🔨 Building the Graph — Input Format Patterns`, inserted between the Graph Representations section and the BFS section (i.e., before `## 🌊 BFS`).

**What to include:**
1. The 6 raw input formats interviewers use, each with a code snippet:
   - Two separate arrays `from[]` / `to[]` (most common in HackerRank-style problems)
   - `int[][] edges` 2D array
   - `prerequisites[][]` pairs (topo sort problems)
   - String keys → integer ID mapping (accounts merge style)
   - Adjacency matrix `grid[i][j]` (don't rebuild — iterate directly)
   - Implicit graph — no edges given, generate neighbours on the fly (Word Ladder)
2. **1-indexed trap callout** — the single most common implementation bug:
   - Wrong: `new ArrayList<>(n)` → indices 0..n-1
   - Right: `new ArrayList<>(n+1)` → indices 0..n, use 1..n, ignore 0
   - Full working snippet with `for (int i = 0; i <= n; i++) adj.add(new ArrayList<>())`
3. **Directed vs undirected** — when to add the reverse edge and when not to
4. **Weighted graph** — `adj.get(u).add(new int[]{v, weight})` vs separate `int[][]` dist table

---

### Gap B — Add Multi-Source BFS on Adjacency List (side-by-side with grid version) ⬜

**Where:** inside the existing BFS section, right after the `BFS Variants at a Glance` table. Add a subsection `#### Multi-Source BFS on an adjacency-list graph`.

**What to include:**
1. The observation in one sentence: *"The ONLY difference from grid multi-source BFS is the neighbour-generation line — everything else is identical."*
2. A full working Java template seeding surplus/source nodes, BFS-expanding, then reading max distance among target nodes
3. Side-by-side comparison:
   - Grid: `for (int d = 0; d < 4; d++) { int nr = r + DR[d]; ... }`
   - Adjacency list: `for (int v : adj.get(u)) { ... }`
4. The answer-collection pattern: `max(dist[v])` for all target nodes; return -1 if any target has `dist == -1`

---

### Gap C — Add "Before You Code" Checklist to BFS and DFS sections ⬜

**Where:** as a small blockquote right before the code template in both `## 🌊 BFS` and `## 🚶 DFS` sections.

**What to include:**
A 3-question pre-flight checklist the reader runs before writing any BFS/DFS loop:

```
Before you write the loop, answer:
  □ What format is the graph? (two arrays / edge list / matrix / implicit)
     → build adjacency list first if needed
  □ Are nodes 1-indexed?
     → allocate n+1 lists; use nodes as-is; ignore index 0
  □ Is it multi-source?
     → seed ALL sources into the queue at time 0, each at distance 0
```

---

## Task 2 — The Same "Setup Before the Algorithm" Gap Exists in ALL DeepDive Files

**Why this is a task:** the graph interview failure is not graph-specific. Every algorithm deep-dive has the same structural hole: the notes teach the algorithm well but skip "how do you go from raw problem input to the data structure the algorithm needs?" This is the Phase 1 problem and it appears everywhere.

### Files that need a "Construction / Setup" section added ⬜

| File | What "Phase 1" means for that topic | Specific gap to fix |
| --- | --- | --- |
| `dp-fundamentals.md` | Translating problem input → DP state definition + base case setup | Add: "How to define your state from input format" — 1D array → `dp[i]`, 2D grid → `dp[r][c]`, two strings → `dp[i][j]`, etc. Add: base case population patterns (fill index 0 vs index -1 sentinel vs fill boundary row/col) |
| `trees-fundamentals.md` | Building a tree from raw input | Add: building `TreeNode` from level-order array (LeetCode format), from parent array, from `n` + edges array. The "TreeNode[] nodes = new TreeNode[n]" pattern. |
| `recursion-fundamentals.md` | Designing the recursive signature from problem input | Add: "How to decide your function signature" — what parameters carry state, what is captured in closure, how to avoid re-passing immutable input on every call |
| `two-pointers-sliding-window-fundamentals.md` | Initialising the window correctly | Add: 0-indexed vs 1-indexed window boundaries, pre-population of the initial window before the main loop, handling empty/single-element edge cases before the loop |
| `arrays-fundamentals.md` | Input parsing and pre-processing | Add: when to sort first, when to build a prefix sum first, when to build a frequency map first — these "setup" steps that unlock the algorithm |
| `backtracking-fundamentals.md` | Building the candidate structure | Add: how to construct the `choices` list from raw input before the backtrack call begins |
| `stacks-queues-fundamentals.md` | Choosing the right data structure variant | Add: when to use `Deque` as stack vs queue vs monotonic stack — the decision before writing any loop |

### Priority order for Task 2 ⬜

1. `dp-fundamentals.md` — highest interview frequency; state definition is where most DP failures start
2. `trees-fundamentals.md` — tree construction from array is asked constantly (deserialise/serialise, LCA setup)
3. `recursion-fundamentals.md` — signature design is where top-down DP stalls
4. `two-pointers-sliding-window-fundamentals.md`
5. Rest as time permits

---

> **Rule for future gaps:** whenever a new interview reveals a "Phase 1" failure (stuck on setup, not algorithm), add a row to Task 2's table above and a new Task entry describing the exact fix. Don't let gaps accumulate silently.
