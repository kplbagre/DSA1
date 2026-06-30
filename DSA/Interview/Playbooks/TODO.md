# Interview Playbooks — TODO

> **Purpose:** Track missing problems and improvements identified during study sessions. Add to this file when a gap is found. Resolve by adding the problem to the correct playbook in standard format (brute force + key insight + 4-6 line code + complexity).

---

## ⚡ Missing Problems — Add to Problem Banks

Problems confirmed absent from playbooks but high-frequency in FAANG interviews. Add in standard `### LC XXX` format.

### 🔴 Must Add (blockers for interview readiness)

| LC | Problem | Add to file | Pattern section | Why it matters |
| --- | --- | --- | --- | --- |
| LC 22 | Generate Parentheses | `backtracking.md` | Pattern 3 — Constraint Satisfaction | Constraints on open/close counts — distinct from N-Queens; very frequently asked at Amazon/Google/Meta |
| LC 17 | Letter Combinations of a Phone Number | `backtracking.md` | Pattern 2 — Combinations | Combination over a character mapping — different structure from subsets; shows up at every company |
| LC 72 | Edit Distance | `dp.md` | Pattern 3 — String DP | `dp-fundamentals.md` calls this "THE DP interview problem"; unlocks ~10 variants (LCS family) |

### 🟡 Should Add (coverage gaps, asked at senior rounds)

| LC | Problem | Add to file | Pattern section | Why it matters |
| --- | --- | --- | --- | --- |
| LC 695 | Max Area of Island | `graphs.md` | Pattern 1 — Grid BFS/DFS | DFS that accumulates a return value — distinct from "count components" (LC 200) |
| LC 130 | Surrounded Regions | `graphs.md` | Pattern 1 — Grid BFS/DFS | Flood from boundary then flip — inverse thinking; asked at Google/Meta |
| LC 139 | Word Break | `dp.md` | Pattern 5 — Counting DP | Boolean "can it be segmented?" — different from LC 140 (all segmentations in backtracking); clean 1D DP |

---

## 🔍 Needs Deeper Audit

These areas need a more thorough cross-check — pull 🟡 problems from DeepDive roadmaps and verify coverage:

- [ ] **State Machine DP** (buy/sell stocks variants — LC 121, 122, 123, 188, 309) — not covered as a pattern in `dp.md`. Currently only in `dp-fundamentals.md` Family 7. Decide: add a Pattern 6 to dp.md or leave in DeepDive only?
- [ ] **graphs.md** — cross-check full 🟡 roadmap in `backtracking-fundamentals.md` and `recursion-fundamentals.md` for any more grid DFS/BFS problems missed
- [ ] **dp.md** — check if LCS family variants (Shortest Common Supersequence, Distinct Subsequences) need entries or are covered by the Edit Distance + LCS entries
- [ ] **strings.md** — check if any 🟡 string problems from `recursion-fundamentals.md` roadmap are absent

---

## 🧠 Missing Motivation Paragraphs — DeepDive Files

Audit triggered June 2026: grepped all DeepDive files for "motivation / why / what does this solve" language. Most files are light on the "why does this family exist as a group?" paragraph. Files to check:

| File | Status | What to add |
| --- | --- | --- |
| `dp-fundamentals.md` | ✅ Fixed | Family 3 (Knapsack) + Family 5 (LCS) now have motivation paragraphs |
| `arrays-fundamentals.md` | 🔴 Check | Low motivation phrase count — does it explain WHY arrays behave the way they do before diving into patterns? |
| `backtracking-fundamentals.md` | 🔴 Check | Low motivation phrase count — does it explain the "why backtrack vs pure recursion" distinction before code? |
| `two-pointers-sliding-window-fundamentals.md` | 🔴 Check | Low motivation phrase count — does it explain WHY two pointers reduce O(n²) to O(n) before patterns? |
| `heap-fundamentals.md` | 🟡 Check | Spot-check: does it explain what heaps are FOR (top-K, streaming min/max) before the API? |
| `queue-fundamentals.md` | 🟡 Check | Spot-check: does it explain BFS-first vs stack-first distinction up front? |

---

## 🔄 Changelog

| Date | Entry |
| --- | --- |
| June 2026 | **File created.** Initial gap audit found 3 must-add and 3 should-add problems. Triggered by LC 22 not being in backtracking.md during study session. Full audit across all 15 playbooks (174 total problems). |
| June 2026 | **Motivation audit section added.** Grepped all DeepDive files for motivation language; found arrays, backtracking, two-pointers-sliding-window light. dp-fundamentals.md Family 3 + Family 5 motivation paragraphs fixed in same session. |
