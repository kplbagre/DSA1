# 🗓️ DSA Interview 15-Day Plan (TEMP — delete after use)

> **Goal:** Be medium-fluent across all core DSA topics for an interview ~15 days out.
> **Philosophy:** Breadth > depth on first pass. Easy-medium fluency everywhere beats expert-in-2 / unprepared-in-8.
> **Status:** Days 1–3 (Trees) handled by `trees-3day-plan-temp.md`. This plan covers Day 1 → Day 15 holistically.

---

## ✅ Strategy Endorsement — Why This Approach Wins

Three principles driving this plan:

1. **Easy-to-medium gets asked** — true for vast majority of L4/L5/Walmart-equivalent interviews. Hards are rare and often "we'll pivot if you struggle."
2. **No deep dive on everything** — deep dives in topics you'll never be tested on is wasted time. Better to be **medium-fluent everywhere** than expert in 2 topics and unprepared in 8.
3. **Iterate based on real interview feedback** — fastest learning loop. If interview 1 gets DP, deep-dive DP for interview 2. Don't pre-spend 5 days on DP when most interviews don't even ask it.

---

## ✅ What's Already Done (don't redo)

| Topic | Where | Status |
| --- | --- | --- |
| Recursion | `DeepDive/recursion-fundamentals.md` (2,236 lines) | ✅ Comprehensive |
| Backtracking | `DeepDive/backtracking-fundamentals.md` (1,605 lines) | ✅ Comprehensive |
| HashMap / HashSet | `Reference/hashmap-section-updated.md`, `Reference/set-section-updated.md` | ✅ Reference-ready |
| Strings | `Reference/string-operations-reference.md` | ✅ Reference-ready |
| Lambdas | `Reference/lambdas-for-dsa-reference.md` | ✅ Reference-ready |
| Trees | `DeepDive/trees-fundamentals.md` (1,632 lines) | ✅ DeepDive done; sprint in progress |
| Queues / Stacks / Deques | `Reference/arraydeque-and-queue-reference.md` (511 lines) | ✅ Reference-ready |
| Arrays & Two Pointers | (no doc yet) | 🟡 **Already practiced** — needs Reference cheatsheet only |

---

## 🗓️ The 15-Day Plan

### **Days 1–3: Trees** *(3 days, ~22 problems)*
See `trees-3day-plan-temp.md`. Tier 1–3 + a touch of Tier 4–5.

---

### **Days 4–8: Graphs (Tier A only)** *(5 days, ~20 problems)*

| Day | Focus | Problems | New idea |
| --- | --- | --- | --- |
| 4 | Adjacency list + DFS/BFS basics | LC 200, 695, 547, 1319, 733 | `visited` set, components |
| 5 | Grid as graph (multi-source BFS) | LC 994, 542, 1091, 286 | Multiple starts in one queue |
| 6 | Topological sort (BFS + DFS) | LC 207, 210, 269, 802 | DAGs, in-degree, Kahn's algo |
| 7 | Cycle detection + Bipartite | LC 261, 785, 886, 684 | 3-color DFS, 2-coloring |
| 8 | Union-Find + Dijkstra (intro) | LC 547 (DSU way), 684, 743, 787 | DSU with path compression, PQ-based shortest path |

**Day 4 morning task:** build `Reference/graphs-reference.md` (~400 lines, ~20 min). Single doc covers all 5 days.

---

### **Days 9–15: The Rest of the Core (7 days, ~45 problems)**

#### **Day 9 — Arrays & Two Pointers (revision + notes)** ⭐ already practiced

> **Status:** Most of these you've already solved. Day 9 is a **revision pass + Reference cheatsheet creation**, not from-scratch learning. Time-box solving at 12 min/problem. Skip any you can solve cold in <8 min.

| LC | Pattern |
| --- | --- |
| LC 1 | Two Sum (hash) |
| LC 167 | Two Sum II — Input array sorted (two-pointer) |
| LC 11 | Container With Most Water |
| LC 15 | 3Sum |
| LC 26 | Remove Duplicates from Sorted Array |
| LC 80 | Remove Duplicates II |
| LC 283 | Move Zeroes |

**Day 9 morning task:** build `Reference/arrays-two-pointers-reference.md` (~300 lines, ~15 min). Patterns to capture:
- Opposite-end two pointers (sorted array)
- Same-direction two pointers (slow/fast, in-place removal)
- Pivot-around-target
- Three-pointer for triplets

**Day 9 victory criterion:** all patterns codified in cheatsheet. You can solve any new LC easy two-pointer in <10 min.

---

#### **Day 10 — Sliding Window**

| LC | Pattern |
| --- | --- |
| LC 3 | Longest Substring Without Repeating Chars |
| LC 209 | Min Subarray Sum (≥ target) |
| LC 76 | Min Window Substring |
| LC 567 | Permutation in String |
| LC 424 | Longest Repeating Character Replacement |
| LC 438 | Find All Anagrams |

**Day 10 morning task:** build `Reference/sliding-window-reference.md` (~250 lines, ~15 min). Patterns:
- Fixed-size window
- Variable-size window (expand-then-shrink)
- "At most K" → "exactly K" trick
- Character-frequency window

---

#### **Day 11 — Binary Search**

| LC | Pattern |
| --- | --- |
| LC 704 | Standard Binary Search |
| LC 35 | Search Insert Position |
| LC 33 | Search in Rotated Sorted Array |
| LC 153 | Find Min in Rotated Sorted Array |
| LC 162 | Find Peak Element |
| LC 875 | Koko Eating Bananas (BS on answer) |
| LC 1011 | Capacity to Ship Packages |

**Day 11 morning task:** build `Reference/binary-search-reference.md` (~300 lines, ~15 min). Patterns:
- Classic BS template (one I trust, never deviate)
- Lower-bound / upper-bound
- BS on rotated sorted
- BS on answer (parametric search)

---

#### **Day 12 — Linked Lists**

| LC | Pattern |
| --- | --- |
| LC 206 | Reverse Linked List |
| LC 21 | Merge Two Sorted Lists |
| LC 141 | Linked List Cycle |
| LC 142 | Linked List Cycle II (find start) |
| LC 19 | Remove Nth From End |
| LC 92 | Reverse Linked List II (range reverse) |
| LC 86 | Partition List |
| LC 876 | Middle of Linked List |

**Day 12 morning task:** build `Reference/linked-list-reference.md` (~350 lines, ~20 min). Patterns:
- Dummy head trick
- Slow/fast pointers (cycle detection, middle, Nth-from-end)
- Reverse in-place (iterative + recursive)
- Merge two lists
- Re-pointer-surgery (insert, delete, partition)

---

#### **Day 13 — DP (1D core)**

| LC | Pattern |
| --- | --- |
| LC 70 | Climbing Stairs (intro) |
| LC 198 | House Robber |
| LC 213 | House Robber II (circular) |
| LC 322 | Coin Change |
| LC 300 | Longest Increasing Subsequence |
| LC 152 | Maximum Product Subarray |

**Day 13 morning task:** build first half of `Reference/dp-reference.md` (~250 lines focused on 1D, ~15 min). Patterns:
- Top-down (memo) → bottom-up (tabulation) conversion
- Space optimization (rolling 1–2 vars)
- "Pick or skip" DP
- "Unbounded knapsack" DP (Coin Change)
- Subsequence DP

---

#### **Day 14 — DP (2D intro)**

| LC | Pattern |
| --- | --- |
| LC 62 | Unique Paths (grid DP) |
| LC 64 | Min Path Sum |
| LC 1143 | Longest Common Subsequence |
| LC 5 | Longest Palindromic Substring |
| LC 72 | Edit Distance (stretch — read solution if stuck) |

**Day 14 morning task:** extend `Reference/dp-reference.md` with 2D section (~200 lines, ~10 min). Patterns:
- Grid DP (`dp[i][j] = f(dp[i-1][j], dp[i][j-1])`)
- String-pair DP (LCS, Edit Distance)
- Substring DP (palindrome)

> **Honest warning:** DP in 2 days = medium-fluent on 1D, intro on 2D. Hard DP (interval, bitmask, tree DP) won't fit. Defer until interview-driven.

---

#### **Day 15 — Mixed (Heap + Monotonic Stack + Greedy + Mock)**

**Morning (~3 hrs): Pick 5 problems from this menu**

| Topic | LC |
| --- | --- |
| Heap | LC 215 Kth Largest, LC 347 Top K Frequent, LC 23 Merge K Sorted Lists |
| Monotonic Stack | LC 739 Daily Temperatures, LC 84 Largest Rectangle in Histogram |
| Greedy | LC 55 Jump Game, LC 45 Jump Game II, LC 134 Gas Station |

No new Reference doc needed — heap is already in `arraydeque-and-queue-reference.md`. Just add 1-page mental notes inline.

**Afternoon (~2 hrs): Revision blitz** — re-solve 1 problem from each of Days 1–14 cold. 14 problems in 2 hrs = ~8.5 min each. **This is your readiness signal.**

**Evening: Mock interview (NON-NEGOTIABLE)** — Pramp, Interviewing.io, or a peer. 45 min, real LC medium, talking through your solution. Lock it in on Day 14 evening so it's scheduled.

---

## 📊 Topic Coverage Summary

### Tier 1 — In the 15-day plan (must cover)

| Topic | Day(s) | Doc to create |
| --- | --- | --- |
| Trees | 1–3 | ✅ already exists |
| Graphs | 4–8 | `Reference/graphs-reference.md` |
| Arrays + Two Pointers | 9 | `Reference/arrays-two-pointers-reference.md` |
| Sliding Window | 10 | `Reference/sliding-window-reference.md` |
| Binary Search | 11 | `Reference/binary-search-reference.md` |
| Linked Lists | 12 | `Reference/linked-list-reference.md` |
| DP (1D + 2D intro) | 13–14 | `Reference/dp-reference.md` |
| Heap, Mono Stack, Greedy | 15 | (use existing + inline notes) |

**Total new docs: 6 cheatsheets (~15–20 min each).** No new DeepDive docs for Days 9–15.

### Tier 2 — Skip on first pass (defer to interview-driven)

| Topic | Why defer |
| --- | --- |
| Bit Manipulation | Asked ~15%; small pattern set, learn just-in-time |
| Trie | Asked ~10%; only if it comes up |
| Segment Tree / Fenwick | Asked ~5% in advanced rounds; way too much for 15 days |
| Advanced DP (interval, bitmask, tree DP) | Asked ~10%; defer unless mock shows weakness |
| Math (modular, GCD, primes) | Learn just-in-time |
| Implement-sort algorithms | `Arrays.sort()` is fine 99% of the time |

---

## 🎯 Doc Strategy — The Critical Rule

For Days 9–15, **DO NOT write full DeepDive docs**. That's 1,500+ lines each — you don't have the time and don't need the depth.

> **Just-in-time, lightweight Reference cheatsheets only:** ~250–350 lines each, pattern-organized, with 4–5 templates + 6–8 problem examples per topic.

**Total doc-creation time over Days 9–15: ~90 min.** Negligible compared to problem-solving.

If a real interview later asks a hard DP question and you blank — *then* expand `Reference/dp-reference.md` into a full `DeepDive/dp-fundamentals.md`. Until then, the cheatsheet plus practice is sufficient for medium-fluency.

---

## 🔁 The Iteration Loop (your real superpower)

```
After each interview (mock or real):
                │
                ▼
      Did topic X come up?
        /                 \
      Yes                  No
       │                    │
   Did you struggle?     Move on, X stays at
       │                  medium-fluency level
   Yes ─┴─ No
    │      │
    │      Document the question; you're fine
    │
    ▼
THIS is your next deep-dive trigger.
Spend 1–2 days expanding that topic's
doc into a full DeepDive.
```

This loop keeps prep efficient — only deep-dive what's actually being tested in *your* interviews, not generic LC's "top 100."

---

## ⚠️ Honest Warnings

1. **DP in 2 days is tight.** You'll get medium-fluent (1D problems comfortable, 2D intro). Hard DP defers until interview-driven.
2. **Day 15 mock interview is non-negotiable.** Schedule on Day 14 evening so it's locked.
3. **Skip DSA-adjacent topics:** OS, networks, system design. If your interview includes those, build a separate plan.
4. **6 problems/day is the ceiling, not the floor.** Some days = 4 (tough topic). That's fine. **Quality > count.**
5. **Day 15 morning revision blitz is your real readiness signal.** Re-solve 1 problem from each topic cold. If 13/14 solve in <10 min — you're interview-ready.
6. **Don't fall behind by more than 1 day.** If you do, drop a topic from Day 15 (Greedy is most expendable).

---

## 📋 Per-Day Self-Check Template

Each morning:
- [ ] Re-read the relevant Reference cheatsheet (or build it if today's first day for the topic)
- [ ] Set the day's problem list (use the LC numbers in this plan)
- [ ] 25-min time-box per problem; editorial fallback at 25 min; immediately re-solve from understanding

Each evening:
- [ ] Pick 1 problem from earlier in the day, solve cold
- [ ] If <10 min → ✅ understood. If >15 min → flag for next-day revision
- [ ] Log any "I struggled with X" — that's your future deep-dive list

---

## 🚦 Sprint Rules

1. **Time-box at 25 min** per problem. Editorial → re-solve from understanding (don't accept-paste).
2. **Group by pattern** within a day — solve all sliding-window problems back-to-back.
3. **Skip Tier 2 topics** during the sprint. Save for interview-driven study.
4. **Build Reference cheatsheets just-in-time** — ~15 min each morning.
5. **Day 15 mock = readiness gate.** If mock goes badly, the next 5 days post-sprint are revision week, not new topics.

---

## 🧾 TL;DR

- **Days 1–3:** Trees (Tier 1–3)
- **Days 4–8:** Graphs (Tier A: DFS/BFS, topo sort, cycle, bipartite, DSU, Dijkstra intro)
- **Day 9:** Arrays + Two Pointers (revision + cheatsheet creation)
- **Day 10:** Sliding Window
- **Day 11:** Binary Search
- **Day 12:** Linked Lists
- **Days 13–14:** DP (1D core + 2D intro)
- **Day 15:** Heap + Mono Stack + Greedy + revision blitz + mock interview
- **Skip:** Bit manipulation, Trie, Segment Tree, advanced DP, math, sorting algorithms (defer to interview-driven)
- **End state:** Medium-fluent across all core topics. Ready for L4-equivalent interviews.
- **Doc strategy:** 6 lightweight Reference cheatsheets (~90 min total build time over 7 days). No new DeepDives.

---

> **🗑️ This file is temporary.** Delete after the 15-day sprint completes (around 2026-05-24). Canonical content lives in the per-topic DeepDive and Reference docs created during the sprint.
