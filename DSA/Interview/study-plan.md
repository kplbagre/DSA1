# Salesforce SMTS R1 — 48-Hour Study Plan

> **Interview:** Wednesday, June 4, 12:00 PM
> **Target:** Salesforce R1 coding round — Senior Member of Technical Staff (SMTS)
> **Format:** 60 min, 1-2 LeetCode Medium problems (occasionally touching Hard)
> **Start:** Monday 12 PM. Arrays-and-hashing already completed (2.5 hours).

---

## 📊 Time Budget

```
48 hours total
−16 hours sleep (Mon night + Tue night)
− 4 hours office work
− 3 hours meals / breaks
─────────
~25 hours of actual study time

12 remaining files × ~1.25 hours avg  =  15 hours (content)
Practice + mock + review              =   7 hours
Buffer                                =   3 hours
```

---

## 🎯 Salesforce SMTS R1 — What Gets Asked

| Frequency | Topics |
| --- | --- |
| **Very High** | Trees, Graphs (BFS/DFS), Arrays+HashMap, Strings |
| **High** | DP (Medium-level), Two Pointers, Binary Search |
| **Medium** | Stacks, Heaps (Top-K, Merge-K), Backtracking |
| **Lower** | Intervals, Greedy, Linked List |

Priority order below is tuned to this frequency distribution.

---

## 📅 Session 1 — Monday Afternoon/Evening (8 hours)

**Goal: Lock in the highest-frequency Salesforce topics.**

| # | File | Est. Time | Why now | Done |
| --- | --- | --- | --- | --- |
| ✅ | `arrays-and-hashing.md` | — | Already completed | ✅ |
| 2 | `two-pointers-and-sliding-window.md` | 1.5h | #1 phone screen pattern, builds directly on arrays | ☐ |
| 3 | `trees-and-bfs-dfs.md` | 1.75h | **#1 Salesforce topic.** Bottom-up DFS + level-order BFS | ☐ |
| 4 | `binary-search.md` | 1.25h | Answer-space binary search catches people off guard | ☐ |
| 5 | `strings.md` | 1.5h | Applies array + two-pointer patterns to strings — fast after #2 | ☐ |
| 6 | `dp.md` | 1h | You know Families 1-3. Just finish Families 4-5. **Shortest file.** | ☐ |

**Session 1 total: ~7 hours** (take 5-min breaks between files)

**After Session 1:** 6 files done. You can handle ~75% of likely Salesforce R1 questions.

**Before bed self-test:** Close all files. For each of the 6 completed files, list the patterns + their trigger words from memory. 4/5 per file → solid. Can't → re-read that file's 🧠 Mental Model section.

---

## 📅 Session 2 — Tuesday Day (8 hours)

**Goal: Complete coverage of medium-frequency topics + first practice.**

| # | File | Est. Time | Why now | Done |
| --- | --- | --- | --- | --- |
| 7 | `stacks-and-queues.md` | 1.5h | Monotonic stack surprises people. "Next greater element" is Salesforce-worthy | ☐ |
| 8 | `graphs.md` | 1.25h | Grid BFS + Topo Sort. Builds on trees BFS from yesterday | ☐ |
| 9 | `heaps.md` | 1.5h | Top-K and Merge-K-Sorted are classic senior-level questions | ☐ |
| 10 | `backtracking.md` | 1.5h | Subsets vs Permutations template. Duplicate-skip trick is the #1 bug | ☐ |
| 11 | `intervals.md` | 1h | Shortest file. Sort-by-start → merge/insert is a distinct, self-contained pattern | ☐ |

**🔨 Practice block (1.25 hours):**

Pick 3 problems from completed files and **write the solution in a blank editor** — no peeking at the playbook. 20 min per problem:

1. One **tree** problem (e.g., LC 104 Max Depth or LC 236 LCA)
2. One **DP** problem (e.g., LC 322 Coin Change or LC 62 Unique Paths)
3. One **array/string** problem (e.g., LC 560 Subarray Sum Equals K or LC 3 Longest Substring)

Score yourself: ✅ solved clean, ⚠️ needed a hint (re-read that pattern), ❌ blanked (mark for Wednesday review).

**Session 2 total: ~8 hours**

**After Session 2:** 11 files done. You can handle ~95% of likely questions.

---

## 📅 Session 3 — Tuesday Night (3 hours)

**Goal: Sweep last two files.**

| # | File | Est. Time | Why now | Done |
| --- | --- | --- | --- | --- |
| 12 | `greedy.md` | 1.25h | Jump Game + Partition Labels are common enough to know | ☐ |
| 13 | `linked-list.md` | 1.75h | Lowest Salesforce frequency — **cut this first if behind schedule** | ☐ |

**After Session 3:** All 13 files done.

---

## 📅 Wednesday Morning — Pre-Interview (3 hours before 12 PM)

**DO NOT study new material. Review only.**

| Time | What |
| --- | --- |
| 30 min | Read `index.md` — cover the right column, quiz yourself on the decision tree |
| 30 min | Re-read the **🧠 Mental Model** section of your 3 weakest files (the ⚠️/❌ ones from practice) |
| 30 min | Skim **⚠️ Gotchas** sections across all files — these are exactly what follow-ups probe |
| 30 min | **STOP studying.** Eat, shower, walk. A calm mind codes better than a crammed one |

---

## 🎯 If Time Runs Short — Cut List

If you're behind schedule, drop files in this order (lowest interview ROI first):

| Cut order | File | Why it's safe to skip |
| --- | --- | --- |
| Cut first | `linked-list.md` | Rare at senior-level Salesforce. You know the basics already |
| Cut second | `greedy.md` | Hardest to template. Most greedy problems need ad-hoc thinking anyway |
| Cut third | `intervals.md` | Distinct pattern but low frequency. Sort + merge is learnable in 5 min if it appears |
| **Never cut** | Trees, DP, Two Pointers, Binary Search, Arrays | These cover 80%+ of Salesforce R1 |

---

## 🔁 How to Study Each File (1–1.75 hours)

Follow this exact sequence for every file:

| Step | What | Time | How |
| --- | --- | --- | --- |
| 1 | Read **🔧 Essential Methods** table | 2 min | Know what methods you need before seeing the patterns |
| 2 | Read **🧠 Mental Model** decision tree | 5 min | This is the "which pattern?" router — memorize the branches |
| 3 | Read each **🧭 Pattern** section | 20 min | Focus on **recognition cues** — the trigger words from problem statements |
| 4 | Read the **🔬 Canonical Walkthrough** | 10 min | Trace the THINKING, not just the code — "I see X → triggers Y → adapt Z" |
| 5 | Read the **⚡ Problem Bank** | 15 min | Read definition + approach + step comments. Understand the key twist per problem |
| 6 | Read **⚠️ Gotchas** | 5 min | These are exactly what interviewers probe as follow-ups |
| 7 | Do the **🧩 Speed Drill** | 5 min | Time yourself. This is your ready/not-ready signal |

**After each file:** Close it. List the patterns + recognition cues from memory.
- Can name 4/5 patterns and their triggers → move on ✅
- Can't → re-read the 🧠 Mental Model section, then try again

---

## 🧠 Salesforce Interview Tactics

**How SMTS coding rounds are scored:**

| Dimension | What they look for |
| --- | --- |
| **Problem-solving** | Can you break down the problem? Do you identify the pattern? |
| **Communication** | Think out loud: "I see sorted input → this triggers binary search" |
| **Code quality** | Clean code, good variable names, braced blocks, no spaghetti |
| **Optimization** | Start brute force → state complexity → optimize. Don't jump to optimal |
| **Edge cases** | Empty input, single element, all same, integer overflow |

**The first 5 minutes of the interview matter most:**

1. Repeat the problem back in your own words
2. Ask 2-3 clarifying questions (input range? duplicates? sorted? can be negative?)
3. Say: *"My brute force would be X at O(n²). But I see [trigger word] — this is [pattern name]. We can do O(n) using [approach]."*
4. Get a nod from the interviewer, THEN start coding

Your playbooks already train this — each pattern has "When to use" recognition cues. In the interview, just say those cues out loud.

---

## ✅ Confidence Checks

### After Session 1 (Monday night):

- [ ] "Contiguous subarray + sum = K" → Prefix Sum + HashMap
- [ ] "Sorted array + find target" → Binary Search
- [ ] "Longest substring with at most K distinct" → Variable Sliding Window
- [ ] "Max depth of binary tree" → Bottom-Up DFS
- [ ] "Rob houses, can't take adjacent" → Linear DP
- [ ] I can write the binary search `lo < hi` template from memory
- [ ] I know the difference between `lo <= hi` and `lo < hi`

### After Session 2 (Tuesday evening):

- [ ] "Next greater element" → Monotonic Stack
- [ ] "Top K frequent elements" → Min-Heap of size K (NOT max-heap)
- [ ] "Number of islands" → Grid BFS/DFS
- [ ] "Course schedule, prerequisites" → Topological Sort
- [ ] "All subsets" → Backtracking with start index
- [ ] I can write the subsets template from memory (with `new ArrayList<>(path)` snapshot)
- [ ] I know subsets = `i+1` (no reuse) vs combination sum = `i` (reuse allowed)
- [ ] "Merge overlapping intervals" → Sort by start, merge adjacent

### After Session 3 (Tuesday night):

- [ ] "Jump to end of array" → Greedy max-reach
- [ ] "Detect cycle in linked list" → Floyd's slow/fast
- [ ] I can name 4+ patterns per file from memory for at least 10 of 13 files

If all boxes are checked → you're ready. 💪

---

## 🔗 Quick Access — All Files

| # | File | Patterns covered |
| --- | --- | --- |
| 1 | `arrays-and-hashing.md` | HashMap Lookup, Canonical Key, Prefix Sum, Kadane's, Freq+Bucket, HashSet |
| 2 | `two-pointers-and-sliding-window.md` | Converging, Same-Direction, Fixed Window, Variable Window, atMost(K) |
| 3 | `trees-and-bfs-dfs.md` | Top-Down DFS, Bottom-Up DFS, BFS Level Order, BST Inorder, LCA |
| 4 | `binary-search.md` | Classic, Bisect Left/Right, Rotated Array, Answer Space, Matrix |
| 5 | `strings.md` | Frequency Array, Palindrome, Reversal, StringBuilder, Subsequence |
| 6 | `dp.md` | Linear, Grid, String, 0/1 Knapsack, Counting |
| 7 | `stacks-and-queues.md` | Bracket Matching, Monotonic Stack, Expression Eval, History/Undo, Design |
| 8 | `graphs.md` | Grid BFS/DFS, Topological Sort, Clone Graph, Union-Find, Dijkstra |
| 9 | `heaps.md` | Top-K, Kth Element, Merge K Sorted, Two Heaps, Greedy+Heap |
| 10 | `backtracking.md` | Subsets, Permutations, Constraint Satisfaction, Partitioning |
| 11 | `intervals.md` | Merge, Insert, Overlap Count, Greedy Scheduling |
| 12 | `greedy.md` | Jump/Reach, Circular, Interval Schedule, Partition, Consecutive Groups |
| 13 | `linked-list.md` | Floyd's Slow/Fast, Reversal, Merge, Gap Pointer, Dummy Node |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** 2-day study plan with priority order, per-file study method, daily confidence checks, and 80/20 shortcut. |
| June 2026 | **Complete rewrite for Salesforce SMTS R1.** Recalibrated file times from actual pace (2.5h for arrays), reordered by Salesforce question frequency, added 3-session schedule (Mon afternoon → Tue day → Tue night), added practice blocks, mock session, interview tactics, and cut-list for time pressure. |
