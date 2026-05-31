# Interview Playbook — Master Index

> **Read this FIRST.** This is your entry point when you don't know which file to open. Given a problem, follow the decision tree below to find the right Interview Playbook file. Then open that file for patterns, templates, and practice problems.

---

## 🎯 The Master Decision Tree

Read the problem statement, then follow this tree:

```
What does the problem involve?
│
├── ARRAYS / NUMBERS
│   │
│   ├── "Sorted array" or "search/find in sorted"
│   │   └── 📄 binary-search.md
│   │
│   ├── "Contiguous subarray" + "sum/count"
│   │   └── 📄 arrays-and-hashing.md (Prefix Sum / Kadane's)
│   │
│   ├── "Find pair/group/match elements" or "duplicates"
│   │   └── 📄 arrays-and-hashing.md (HashMap / HashSet)
│   │
│   ├── "Two pointers" or "sorted + pair"
│   │   └── 📄 two-pointers-and-sliding-window.md
│   │
│   ├── "Substring/subarray with at most K" or "sliding window"
│   │   └── 📄 two-pointers-and-sliding-window.md
│   │
│   ├── "Top K" or "Kth largest/smallest"
│   │   └── 📄 heaps.md
│   │
│   ├── "Intervals" / "start-end times" / "overlapping"
│   │   └── 📄 intervals.md
│   │
│   └── "Can I reach?" / "minimum jumps" / "greedy choice"
│       └── 📄 greedy.md
│
├── STRINGS
│   │
│   ├── "Anagram" / "frequency of characters"
│   │   └── 📄 strings.md (int[26] pattern) or arrays-and-hashing.md (canonical key)
│   │
│   ├── "Palindrome"
│   │   └── 📄 strings.md (palindrome check / expand from center)
│   │
│   ├── "Substring with condition"
│   │   └── 📄 two-pointers-and-sliding-window.md (sliding window)
│   │
│   └── "Reverse / manipulate string"
│       └── 📄 strings.md (StringBuilder / reversal)
│
├── LINKED LIST
│   │
│   ├── "Cycle detection" / "middle node" / "fast/slow"
│   │   └── 📄 linked-list.md (Floyd's)
│   │
│   ├── "Reverse" / "reorder"
│   │   └── 📄 linked-list.md (reversal pattern)
│   │
│   └── "Merge sorted lists"
│       └── 📄 linked-list.md (merge pattern) or heaps.md (merge K)
│
├── STACK / QUEUE
│   │
│   ├── "Valid parentheses" / "brackets"
│   │   └── 📄 stacks-and-queues.md (bracket matching)
│   │
│   ├── "Next greater/smaller element" / "daily temperatures"
│   │   └── 📄 stacks-and-queues.md (monotonic stack)
│   │
│   └── "Design a [stack/queue] with special property"
│       └── 📄 stacks-and-queues.md (design pattern)
│
├── TREES
│   │
│   ├── "Level order" / "BFS" / "zigzag"
│   │   └── 📄 trees-and-bfs-dfs.md (BFS pattern)
│   │
│   ├── "Max depth" / "diameter" / "path sum" (bottom-up)
│   │   └── 📄 trees-and-bfs-dfs.md (bottom-up DFS)
│   │
│   ├── "Validate BST" / "Kth smallest in BST"
│   │   └── 📄 trees-and-bfs-dfs.md (BST inorder)
│   │
│   └── "Lowest common ancestor"
│       └── 📄 trees-and-bfs-dfs.md (LCA pattern)
│
├── GRAPHS
│   │
│   ├── "Number of islands" / "flood fill" / "grid traversal"
│   │   └── 📄 graphs.md (Grid BFS/DFS)
│   │
│   ├── "Course schedule" / "prerequisites" / "ordering"
│   │   └── 📄 graphs.md (topological sort)
│   │
│   ├── "Clone graph" / "deep copy"
│   │   └── 📄 graphs.md (graph cloning)
│   │
│   ├── "Connected components" / "redundant edge"
│   │   └── 📄 graphs.md (Union-Find)
│   │
│   └── "Shortest path with weights"
│       └── 📄 graphs.md (Dijkstra)
│
├── DYNAMIC PROGRAMMING
│   │
│   ├── "Max/min considering sequence" / "rob houses" / "climb stairs"
│   │   └── 📄 dp.md (Linear DP)
│   │
│   ├── "Grid paths" / "minimum cost path"
│   │   └── 📄 dp.md (Grid DP)
│   │
│   ├── "Two strings" / "LCS" / "edit distance"
│   │   └── 📄 dp.md (String DP)
│   │
│   ├── "Subset sum" / "partition" / "knapsack"
│   │   └── 📄 dp.md (0/1 Knapsack)
│   │
│   └── "Count ways" / "decode" / "partition into parts"
│       └── 📄 dp.md (Counting DP)
│
└── BACKTRACKING
    │
    ├── "All subsets" / "all combinations"
    │   └── 📄 backtracking.md (subsets/combinations)
    │
    ├── "All permutations"
    │   └── 📄 backtracking.md (permutations)
    │
    ├── "Place items on board" / "N-Queens" / "Sudoku"
    │   └── 📄 backtracking.md (constraint satisfaction)
    │
    └── "Partition string" / "palindrome partitioning"
        └── 📄 backtracking.md (string partitioning)
```

---

## 📚 Quick File Reference

| File | Patterns | Key Problems |
| --- | --- | --- |
| `arrays-and-hashing.md` | HashMap Lookup, Canonical Key, Prefix Sum, Kadane's, Freq+Bucket, HashSet | LC 1, 49, 560, 53, 347, 128 |
| `two-pointers-and-sliding-window.md` | Converging, Same-Direction, Fixed Window, Variable Window, atMost(K) | LC 15, 11, 3, 76, 992 |
| `strings.md` | Frequency Array, Palindrome, Reversal, StringBuilder, Subsequence | LC 242, 5, 151, 49, 392 |
| `linked-list.md` | Floyd's Slow/Fast, Reversal, Merge, Gap Pointer, Dummy Node | LC 141, 206, 21, 19, 143 |
| `stacks-and-queues.md` | Bracket Matching, Monotonic Stack, Expression Eval, History/Undo, Design | LC 20, 739, 150, 394, 155 |
| `trees-and-bfs-dfs.md` | Top-Down DFS, Bottom-Up DFS, BFS Level Order, BST Inorder, LCA | LC 104, 543, 102, 230, 236 |
| `binary-search.md` | Classic, Bisect Left/Right, Rotated Array, Answer Space, Matrix | LC 704, 34, 33, 875, 74 |
| `heaps.md` | Top-K, Kth Element, Merge K Sorted, Two Heaps, Greedy+Heap | LC 347, 215, 23, 295, 621 |
| `backtracking.md` | Subsets, Permutations, Constraint Satisfaction, Partitioning | LC 78, 46, 51, 131 |
| `intervals.md` | Merge, Insert, Overlap Count, Greedy Scheduling | LC 56, 57, 253, 435 |
| `dp.md` | Linear, Grid, String, 0/1 Knapsack, Counting | LC 198, 62, 1143, 416, 91 |
| `greedy.md` | Jump/Reach, Circular, Interval Schedule, Partition, Consecutive Groups | LC 55, 134, 435, 763, 846 |
| `graphs.md` | Grid BFS/DFS, Topological Sort, Clone, Union-Find, Dijkstra | LC 200, 207, 133, 323, 743 |

---

## 🚀 Suggested Study Order

If you have **1 day:** arrays-and-hashing → two-pointers → binary-search → trees

If you have **2 days:** Day 1: arrays → two-pointers → strings → binary-search. Day 2: trees → graphs → stacks → dp

If you have **3+ days:** All files in this order:
1. `arrays-and-hashing.md` (foundation — HashMap is everywhere)
2. `two-pointers-and-sliding-window.md` (builds on sorted arrays)
3. `strings.md` (applies array + two-pointer patterns to strings)
4. `binary-search.md` (extends sorted-array thinking)
5. `linked-list.md` (pointer manipulation — distinct skill)
6. `stacks-and-queues.md` (monotonic stack is a must-know)
7. `trees-and-bfs-dfs.md` (recursion patterns you'll reuse in graphs)
8. `graphs.md` (builds on BFS/DFS from trees)
9. `heaps.md` (top-K and merge-K)
10. `backtracking.md` (builds on recursion)
11. `intervals.md` (small, distinct, fast to review)
12. `dp.md` (save for last — builds on all patterns above)
13. `greedy.md` (hardest to template — review for intuition)

---

## 🔗 Companion Resources

| Resource | Where | Purpose |
| --- | --- | --- |
| **Reference files** | `../Reference/` | Method signatures and syntax for HashMap, Set, String, etc. |
| **DeepDive files** | `../DeepDive/` | Learn a topic from scratch (trees, recursion, graphs, backtracking) |
| **Implementation files** | `../Implementation/` | Java coding traps, simulation patterns |
| **Pattern files** | `../Patterns/` | Single-problem deep dives (Group Anagrams, Max Path Sum) |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Master index for 13 Interview Playbook files with decision tree, quick reference table, and suggested study order. |
