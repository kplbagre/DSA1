# DSA DeepDive — Pending Work

> **Purpose:** tracks known gaps discovered through real interview failures. Each task is concrete — not "improve X" but "add Y to Z because it prevents bug B."

---

## 🔄 Changelog

| Date | Entry |
| --- | --- |
| June 2026 | Created. Two root gaps identified: (1) graph construction input patterns missing from graphs-fundamentals.md; (2) same "setup before the algorithm" gap exists across ALL DeepDive files. |
| July 2026 | Task 1 completed (all 3 graph gaps A/B/C done). Task 3 added: Worked Walkthroughs for all DeepDive files — discuss before writing, do NOT start until discussion done. |
| July 2026 | Task 2 completed. Phase 1 "Setup Before the Algorithm" sections added to all 7 remaining DeepDive files: dp, two-pointers/sliding-window, trees, backtracking, recursion, arrays, stacks-queues. |
| July 2026 | Task 3 completed. Worked Walkthroughs (5-part format) added to all DeepDive files. Pairs 1-4 done: arrays (10), two-pointers/SW (11), trees (12), recursion (6), backtracking (8), stacks-queues (8). dp-fundamentals.md kept in its existing 20.X format (already complete). |
| July 2026 | Walkthroughs extended to 3 more files: hashmaps-fundamentals.md (8 WW), linked-list-fundamentals.md (8 WW), sets-fundamentals.md (7 WW). All replaced old 3-walkthrough format with 5-part WW format. |
| July 2026 | Phase 1 "Setup Before the Algorithm" sections added to hashmaps, linked-list, and sets — the 3 files missed in the original Task 2 pass. Each has: type decision table, Phase 1 code stubs, pre-flight checklist. |
| July 2026 | Phase 1 sections completed for last 2 remaining files: dp-fundamentals.md (🔨 Translating the Problem Into a DP State — 5 input formats × decision table + stubs + checklist) and two-pointers-sliding-window-fundamentals.md (renamed 🛠️ Java Skeleton to 🔨 Setup — Phase 1, added framing blockquote). All 11 DeepDive files now have Phase 1 sections. |
| July 2026 | **`binary-search-fundamentals.md` created.** New DeepDive for binary search — triggered by gem-gap problem in a real interview. Covers 5 variants (Classic, Bisect, Rotated Array, Answer Space, 2D Matrix), 9 walkthroughs (WW-1 to WW-9 including gem-gap as WW-7 and LC 410 as 🔴 reference-only WW-9), Phase 1 decision framework (lo/hi initialisation table + `lo <= hi` vs `lo < hi` decision table + mid formula), 6 gotchas, 5-tier practice plan. All standards applied from creation: 5 patterns with full motivation/naive/insight/visual/KEY INVARIANT structure, Method Fallbacks section, lesson-learned callout. |

---

## Task 1 — Fix 3 "Setup Before the Algorithm" Gaps in `graphs-fundamentals.md` ✅ DONE

**Why:** Kapil got caught in a real interview on a multi-source BFS problem. He recognised the algorithm (rotten oranges pattern) but stalled on (a) building the adjacency list from two raw arrays and (b) handling 1-indexed nodes. These are Phase 1 failures — the notes only teach Phase 2.

**Root cause:** every graph problem has two phases:
- **Phase 1 — Build the graph** from whatever raw input format the problem gives → MISSING from notes
- **Phase 2 — Run the algorithm** (BFS/DFS/etc.) → well-covered

### Gap A — Add "Graph Construction from Raw Input" section ✅ DONE

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

### Gap B — Add Multi-Source BFS on Adjacency List (side-by-side with grid version) ✅ DONE

**Where:** inside the existing BFS section, right after the `BFS Variants at a Glance` table. Add a subsection `#### Multi-Source BFS on an adjacency-list graph`.

**What to include:**
1. The observation in one sentence: *"The ONLY difference from grid multi-source BFS is the neighbour-generation line — everything else is identical."*
2. A full working Java template seeding surplus/source nodes, BFS-expanding, then reading max distance among target nodes
3. Side-by-side comparison:
   - Grid: `for (int d = 0; d < 4; d++) { int nr = r + DR[d]; ... }`
   - Adjacency list: `for (int v : adj.get(u)) { ... }`
4. The answer-collection pattern: `max(dist[v])` for all target nodes; return -1 if any target has `dist == -1`

---

### Gap C — Add "Before You Code" Checklist to BFS and DFS sections ✅ DONE

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

## Task 2 — The Same "Setup Before the Algorithm" Gap Exists in ALL DeepDive Files ✅ DONE

**Why this is a task:** the graph interview failure is not graph-specific. Every algorithm deep-dive has the same structural hole: the notes teach the algorithm well but skip "how do you go from raw problem input to the data structure the algorithm needs?" This is the Phase 1 problem and it appears everywhere.

### Files that need a "Construction / Setup" section added

| File | What "Phase 1" means for that topic | Specific gap to fix | Status |
| --- | --- | --- | --- |
| `dp-fundamentals.md` | Translating problem input → DP state definition + base case setup | Add: "How to define your state from input format" — 1D array → `dp[i]`, 2D grid → `dp[r][c]`, two strings → `dp[i][j]`, etc. Add: base case population patterns (fill index 0 vs index -1 sentinel vs fill boundary row/col) | ✅ DONE |
| `two-pointers-sliding-window-fundamentals.md` | Initialising the window correctly | Add: 0-indexed vs 1-indexed window boundaries, pre-population of the initial window before the main loop, `atMost(K)` double-call setup, handling empty/single-element edge cases before the loop | ✅ DONE |
| `trees-fundamentals.md` | Building a tree from raw input | Add: building `TreeNode` from level-order array (LeetCode format), from parent array, from `n` + edges array. The "TreeNode[] nodes = new TreeNode[n]" pattern. | ✅ DONE |
| `backtracking-fundamentals.md` | Building the candidate structure | Add: how to construct the `choices` list from raw input before the backtrack call begins | ✅ DONE |
| `recursion-fundamentals.md` | Designing the recursive signature from problem input | Add: "How to decide your function signature" — what parameters carry state, what is captured in closure, how to avoid re-passing immutable input on every call | ✅ DONE |
| `arrays-fundamentals.md` | Input parsing and pre-processing | Add: when to sort first, when to build a prefix sum first, when to build a frequency map first — these "setup" steps that unlock the algorithm | ✅ DONE |
| `stacks-queues-fundamentals.md` | Choosing the right data structure variant | Add: when to use `Deque` as stack vs queue vs monotonic stack — the decision before writing any loop | ✅ DONE |

### Priority order for Task 2

1. ✅ `dp-fundamentals.md`
2. ✅ `two-pointers-sliding-window-fundamentals.md`
3. ✅ `trees-fundamentals.md`
4. ✅ `backtracking-fundamentals.md`
5. ✅ `recursion-fundamentals.md`
6. ✅ `arrays-fundamentals.md`
7. ✅ `stacks-queues-fundamentals.md`

---

---

## Task 3 — Add Worked Walkthroughs to ALL DeepDive Files ✅ DONE

**Why:** graphs-fundamentals.md proved that canonical walkthroughs with "Transfers to" tables enable solving 40-60 problems from one pass. The same leverage exists for every topic.

**Walkthrough format (agreed July 2026):** 5-part structure for every walkthrough — codified in `notes-standards-deepdive.md § 🔬 Worked Walkthroughs`:
1. **Problem statement** — one sentence, plain English
2. **Brute force** — 2-3 sentences, NO code + Time/Space complexity
3. **Intuition bridge** — 1-2 sentences on the single insight that unlocks optimal
4. **Steps + optimal code** — English steps first, then full Java code
5. **Transfers to** — 3-4 problems: what's identical / ONE thing different / key line

> **Why the format matters:** brute force trains the "what's your naive solution?" answer every interviewer asks. Intuition bridge trains the *jump* from problem observation to algorithm choice — the hardest thing to teach. See `notes-standards-deepdive.md` for good vs bad intuition bridge examples.

**Execution order (pairs):**

| Pair | Files | Status |
| --- | --- | --- |
| 0 | `graphs-fundamentals.md` (14) | ✅ DONE (reference implementation) |
| 1 | `arrays-fundamentals.md` (10) ✅ + `two-pointers-sliding-window-fundamentals.md` (11) ✅ | ✅ DONE |
| 2 | `trees-fundamentals.md` (12) ✅ + `recursion-fundamentals.md` (6) ✅ | ✅ DONE |
| 3 | `dp-fundamentals.md` (untouched — existing 20.X format kept) ✅ + `backtracking-fundamentals.md` (8) ✅ | ✅ DONE |
| 4 | `stacks-queues-fundamentals.md` (8) ✅ | ✅ DONE |

---

### Pair 1A — `arrays-fundamentals.md` — 10 walkthroughs ✅ DONE

> Problem list agreed July 2026. Frequency-checked against FAANG interview data. Rule: never remove a problem that teaches a unique pattern — only add.

| WW# | Problem | Shape it teaches |
| --- | --- | --- |
| WW-1 | LC 53 Maximum Subarray | Kadane's — extend or restart at each index |
| WW-2 | LC 121 Best Time to Buy and Sell Stock | Track running minimum — one-pass greedy ← added |
| WW-3 | LC 238 Product of Array Except Self | Two-pass prefix/suffix product without division |
| WW-4 | LC 169 Majority Element | Moore's Voting — net count cancels minorities |
| WW-5 | LC 56 Merge Intervals | Sort by start + greedy merge |
| WW-6 | LC 560 Subarray Sum Equals K | Prefix sum + HashMap for O(n) subarray counting |
| WW-7 | LC 75 Sort Colors | Dutch National Flag — 3-way partition with 3 pointers |
| WW-8 | LC 448 Find All Disappeared Numbers | Negate-at-index — array as in-place hash map (easy variant) |
| WW-9 | LC 41 First Missing Positive | Negate-at-index — hard variant: out-of-range values, then scan |
| WW-10 | LC 128 Longest Consecutive Sequence | HashSet + sequential scan — O(n) without sorting ← added |

---

### Pair 1B — `two-pointers-sliding-window-fundamentals.md` — 11 walkthroughs ✅ DONE

> Problem list agreed July 2026. Frequency-checked; LC 424 added (high frequency, unique maxCount trick). LC 30 kept — multi-word fixed window is structurally distinct from single-char fixed window.

| WW# | Problem | Shape it teaches |
| --- | --- | --- |
| WW-1 | LC 167 Two Sum II | Classic converging pointers on sorted array |
| WW-2 | LC 15 3Sum | Outer loop + converging inner pointers |
| WW-3 | LC 11 Container With Most Water | Greedy pointer move — always move the shorter side |
| WW-4 | LC 42 Trapping Rain Water | Two-pointer height tracking — min of max walls |
| WW-5 | LC 3 Longest Substring Without Repeating | Variable window + HashSet shrink |
| WW-6 | LC 424 Longest Repeating Character Replacement | Variable window + maxCount trick — never shrink below best ← added |
| WW-7 | LC 567 Permutation in String | Fixed window + character frequency array comparison |
| WW-8 | LC 76 Minimum Window Substring | Variable window + frequency map + formed-count tracking |
| WW-9 | LC 992 Subarrays with K Different Integers | atMost(K) − atMost(K−1) double-call template |
| WW-10 | LC 209 Minimum Size Subarray Sum | Variable shrink window — minimize window meeting constraint |
| WW-11 | LC 30 Substring with Concatenation of All Words | Fixed multi-word window — slide one char, recheck full word boundary |

---

### Pair 2A — `trees-fundamentals.md` — 12 walkthroughs ✅ DONE

| WW# | Problem | Shape it teaches |
| --- | --- | --- |
| WW-1 | LC 104 Maximum Depth of Binary Tree | Postorder combine — max(left, right) + 1 |
| WW-2 | LC 226 Invert Binary Tree | Postorder modify — swap children after recursing |
| WW-3 | LC 102 Binary Tree Level Order Traversal | BFS layer snapshot — poll all nodes at current size |
| WW-4 | LC 543 Diameter of Binary Tree | Two-purpose recursion — update global max, return height |
| WW-5 | LC 112 Path Sum | Top-down carry — subtract from remaining target |
| WW-6 | LC 113 Path Sum II | Path backtracking — TRY / RECURSE / UNDO at leaf |
| WW-7 | LC 199 Binary Tree Right Side View | DFS depth tracking — add when `depth == result.size()`, right-first |
| WW-8 | LC 572 Subtree of Another Tree | Nested recursion — `isSameTree` inside `isSubtree` |
| WW-9 | LC 236 Lowest Common Ancestor | 3-case bubble-up — found p/q returns self; both non-null returns root |
| WW-10 | LC 98 Validate BST | BST bounds window — carry `(min, max)` down the tree |
| WW-11 | LC 105 Construct Binary Tree from Preorder and Inorder | Construction from traversals — inorder HashMap for O(1) root index |
| WW-12 | LC 124 Binary Tree Maximum Path Sum 🔴 | Two-purpose with negative clipping — update global, return clipped single-arm |

---

### Pair 2B — `recursion-fundamentals.md` — 6 walkthroughs ✅ DONE

| WW# | Problem | Shape it teaches |
| --- | --- | --- |
| WW-1 | LC 70 Climbing Stairs | Memoized Fibonacci — memo collapses O(2^n) tree to O(n) path |
| WW-2 | LC 21 Merge Two Sorted Lists | Linked list recursion — smaller head wins; recurse on its tail |
| WW-3 | LC 78 Subsets | Take/not-take for-loop backtracking — snapshot on entry, start param prevents duplicates |
| WW-4 | LC 50 Pow(x, n) | Divide-and-conquer halving — square the half-result; long cast for MIN_VALUE |
| WW-5 | LC 206 Reverse Linked List | Leap of faith + pointer detachment — head.next.next = head; head.next = null |
| WW-6 | LC 779 K-th Symbol in Grammar | Mathematical position reduction — never build the string; derive child from parent |

---

### Pair 3A — `backtracking-fundamentals.md` — 8 walkthroughs ✅ DONE

| WW# | Problem | Shape it teaches |
| --- | --- | --- |
| WW-1 | LC 78 Subsets | Snapshot-on-entry for-loop — add current path immediately, no base-case check |
| WW-2 | LC 46 Permutations | `used[]` boolean array — loop all positions, skip already-used |
| WW-3 | LC 39 Combination Sum | Pass `i` not `i+1` to allow reuse + prune when `remaining < 0` |
| WW-4 | LC 40 Combination Sum II | Sort + skip `nums[i] == nums[i-1]` when `i > start` to deduplicate |
| WW-5 | LC 22 Generate Parentheses | Validity-gated generation: `open < n` to push `(`; `close < open` to push `)` |
| WW-6 | LC 17 Letter Combinations of a Phone Number | Different candidate set per level — look up phone map for current digit |
| WW-7 | LC 79 Word Search | Grid backtracking: mark cell with `'#'` sentinel; restore on return |
| WW-8 | LC 51 N-Queens 🔴 | 3 boolean arrays for O(1) conflict: `cols[]`, `diag1[r+c]`, `diag2[r-c+n-1]` |

### Pair 4 — `stacks-queues-fundamentals.md` — 8 walkthroughs ✅ DONE

| WW# | Problem | Shape it teaches |
| --- | --- | --- |
| WW-1 | LC 20 Valid Parentheses | Bracket matching — push open, pop+match on close |
| WW-2 | LC 155 Min Stack | Dual-stack min tracking — parallel shadow stack fixed at push time |
| WW-3 | LC 239 Sliding Window Maximum | Monotonic deque — evict stale front, drop smaller rear |
| WW-4 | LC 739 Daily Temperatures | Monotonic stack — "next greater" pop trigger; record index distance |
| WW-5 | LC 84 Largest Rectangle in Histogram | Monotonic stack — pop-and-compute area when height drops; sentinel forces final flush |
| WW-6 | LC 394 Decode String | Stack with nesting — save (count, prefix) before `[`, restore+append on `]` |
| WW-7 | LC 150 Evaluate Reverse Polish Notation | Stack expression — pop two operands, push result |
| WW-8 | LC 232 Implement Queue using Stacks | Two-stack queue — lazy transfer from inbox to outbox on demand |

---

> **Rule for future gaps:** whenever a new interview reveals a "Phase 1" failure (stuck on setup, not algorithm), add a row to Task 2's table above and a new Task entry describing the exact fix. Don't let gaps accumulate silently.
