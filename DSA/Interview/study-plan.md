# Interview Prep — 2-Day Study Plan

> **When to read this:** Right before your study sessions. Open this file, follow the order, check off as you go. Each file takes 30-45 minutes. Total: ~7 hours across 2 days.

---

## 📅 Day 1 — The Foundation (Must-Finish)

| # | File | Time | Why first | Done |
| --- | --- | --- | --- | --- |
| 1 | `arrays-and-hashing.md` | 45 min | HashMap is in 60%+ of problems. Foundation for everything else | ☐ |
| 2 | `two-pointers-and-sliding-window.md` | 45 min | #1 most common phone screen pattern | ☐ |
| 3 | `binary-search.md` | 40 min | Biggest gap — answer-space search alone is worth the time | ☐ |
| 4 | `strings.md` | 35 min | Applies patterns from #1 and #2 to strings — fast if you've done those | ☐ |
| 5 | `stacks-and-queues.md` | 35 min | Monotonic stack is a must-know, shows up unexpectedly | ☐ |

**Day 1 total: ~3.5 hours** (take a 5-min break between files)

**End of Day 1 self-test:** Open `index.md`, cover the right column, and for each problem description say which file + pattern it maps to. 80%+ from memory → Day 1 is solid.

---

## 📅 Day 2 — Complete Coverage

| # | File | Time | Why this order | Done |
| --- | --- | --- | --- | --- |
| 6 | `trees-and-bfs-dfs.md` | 45 min | #1 most common interview topic. Bottom-up DFS + dual-purpose is key | ☐ |
| 7 | `heaps.md` | 35 min | Top-K and Merge-K are FAANG staples. Min vs max heap confusion is common | ☐ |
| 8 | `graphs.md` | 40 min | Builds on BFS/DFS from trees. Grid BFS + Topo Sort cover most graph questions | ☐ |
| 9 | `backtracking.md` | 35 min | Subsets vs Permutations template. Duplicate-handling is the #1 bug | ☐ |
| 10 | `dp.md` | 40 min | Hardest topic, but 5 families cover 80% of interview DP | ☐ |

**Day 2 total: ~3.5 hours**

---

## 🌙 If You Have Extra Time (Evening/Night)

| Priority | File | Time | Value | Done |
| --- | --- | --- | --- | --- |
| ⭐ High | `intervals.md` | 20 min | Short file, distinct pattern, very likely to appear | ☐ |
| ⭐ High | `greedy.md` | 25 min | Hardest to template but Jump Game + Partition Labels are common | ☐ |
| Medium | `linked-list.md` | 30 min | Pointer surgery — you either know it or you don't | ☐ |
| Low | `index.md` | 10 min | Final review — master decision tree, quiz yourself | ☐ |

---

## 🎯 The 80/20 Rule — If Time Runs Short

If you can only finish **6 files**, do these (covers ~80% of interview questions):

1. ⭐ `arrays-and-hashing.md`
2. ⭐ `two-pointers-and-sliding-window.md`
3. ⭐ `binary-search.md`
4. ⭐ `trees-and-bfs-dfs.md`
5. ⭐ `heaps.md`
6. ⭐ `dp.md`

---

## 🔁 How to Study Each File (30-45 min)

Follow this exact sequence for every file:

| Step | What | Time | How |
| --- | --- | --- | --- |
| 1 | Read **🔧 Essential Methods** table | 2 min | Know what methods you need before seeing the patterns |
| 2 | Read **🧠 Mental Model** decision tree | 3 min | This is the "which pattern?" router — memorize the branches |
| 3 | Read each **🧭 Pattern** section | 15 min | Focus on **recognition cues** — the trigger words from problem statements |
| 4 | Read the **🔬 Canonical Walkthrough** | 5 min | Trace the THINKING, not just the code — "I see X → triggers Y → adapt Z" |
| 5 | Skim the **⚡ Problem Bank** | 10 min | Read definitions + approach. Code is bonus — understand the key twist |
| 6 | Read **⚠️ Gotchas** | 3 min | These are exactly what interviewers probe as follow-ups |
| 7 | Do the **🧩 Speed Drill** | 5 min | Time yourself. This is your ready/not-ready signal |

**After each file:** Close it. Try to list the patterns + recognition cues from memory.
- Can name 4/5 patterns and their triggers → move on ✅
- Can't → re-read the 🧠 mental model section, then try again

---

## ✅ Daily Confidence Check

### After Day 1 — ask yourself:

- [ ] "Contiguous subarray + sum = K" → I know this is Prefix Sum + HashMap
- [ ] "Sorted array + find target" → I know this is Binary Search
- [ ] "Longest substring with at most K distinct" → I know this is Variable Sliding Window
- [ ] "Next greater element" → I know this is Monotonic Stack
- [ ] I can write the binary search `lo < hi` template from memory (bisect-left)
- [ ] I know the difference between `lo <= hi` and `lo < hi`

### After Day 2 — ask yourself:

- [ ] "Max depth of binary tree" → I know this is Bottom-Up DFS
- [ ] "Top K frequent elements" → I know this is Min-Heap of size K (NOT max-heap)
- [ ] "Number of islands" → I know this is Grid BFS/DFS
- [ ] "All subsets" → I know this is Backtracking with start index
- [ ] "Rob houses, can't take adjacent" → I know this is Linear DP
- [ ] I can write the subsets template from memory (with `new ArrayList<>(path)` snapshot)
- [ ] I know subsets = `i+1` (no reuse) vs combination sum = `i` (reuse allowed)

If all boxes are checked → you're ready. 💪

---

## 🔗 Quick Access — All Files

| # | File | Patterns covered |
| --- | --- | --- |
| 1 | `arrays-and-hashing.md` | HashMap Lookup, Canonical Key, Prefix Sum, Kadane's, Freq+Bucket, HashSet |
| 2 | `two-pointers-and-sliding-window.md` | Converging, Same-Direction, Fixed Window, Variable Window, atMost(K) |
| 3 | `binary-search.md` | Classic, Bisect Left/Right, Rotated Array, Answer Space, Matrix |
| 4 | `strings.md` | Frequency Array, Palindrome, Reversal, StringBuilder, Subsequence |
| 5 | `stacks-and-queues.md` | Bracket Matching, Monotonic Stack, Expression Eval, History/Undo, Design |
| 6 | `trees-and-bfs-dfs.md` | Top-Down DFS, Bottom-Up DFS, BFS Level Order, BST Inorder, LCA |
| 7 | `heaps.md` | Top-K, Kth Element, Merge K Sorted, Two Heaps, Greedy+Heap |
| 8 | `graphs.md` | Grid BFS/DFS, Topological Sort, Clone Graph, Union-Find, Dijkstra |
| 9 | `backtracking.md` | Subsets, Permutations, Constraint Satisfaction, Partitioning |
| 10 | `dp.md` | Linear, Grid, String, 0/1 Knapsack, Counting |
| 11 | `intervals.md` | Merge, Insert, Overlap Count, Greedy Scheduling |
| 12 | `greedy.md` | Jump/Reach, Circular, Interval Schedule, Partition, Consecutive Groups |
| 13 | `linked-list.md` | Floyd's Slow/Fast, Reversal, Merge, Gap Pointer, Dummy Node |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** 2-day study plan with priority order, per-file study method, daily confidence checks, and 80/20 shortcut. |
