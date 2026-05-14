# 🗓️ Trees 3-Day Sprint Plan (TEMP — delete after use)

> **Goal:** Get interview-ready on trees in 3 focused days.
> **Source of truth:** `DeepDive/trees-fundamentals.md` (1,632 lines, 6-tier practice plan).
> **Status:** Recursion foundation is solid → tree problems should now feel like "leap of faith with a `TreeNode`".

---

## 🎯 Strategic Reality Check

| Truth | Implication |
| --- | --- |
| Trees doc has **32 problems across 5 tiers + 7 Reference Only**. | Don't do all 32 in 3 days. Aim for ~22 high-yield. |
| Recursion foundation is now solid. | Tier 1 problems should take **10–12 min each**, not 25. |
| Tier 6 is "Reference Only" by design — read, don't solve cold. | Skip them entirely. Bonus material for week 2. |
| Quality > quantity. | Better to solve **20 problems clean** than 30 sloppily. |
| LC 124 walkthrough already exists. | Don't **solve** it cold — just internalize the walkthrough. |

**Core principle:** Tiers 1–3 (~22 problems) is the **interview-ready bar**. Tier 4–5 is bonus polish you can do in week 2.

---

## 🗓️ The 3-Day Plan

### **Day 1 — Foundation muscle memory (Tier 1 + traversal warmups)**

**Morning (90 min): Re-read prep**
- `trees-fundamentals.md` § **Recursion on Trees — The Mental Model** (lines ~141–248)
- `trees-fundamentals.md` § **DFS Traversals** + **BFS Level Order Traversal** (lines ~396–630)
- `trees-fundamentals.md` § **The Four Big Patterns** (lines ~632–880)

**Afternoon/Evening (3–4 hrs): Solve 9 problems**

| # | Problem | Pattern | Time Target |
| --- | --- | --- | --- |
| 1 | LC 104 Maximum Depth | Bottom-up DFS | 10 min |
| 2 | LC 144 Preorder Traversal (recursive) | DFS template | 5 min |
| 3 | LC 94 Inorder Traversal (recursive) | DFS template | 5 min |
| 4 | LC 145 Postorder Traversal (recursive) | DFS template | 5 min |
| 5 | LC 102 Level Order Traversal | BFS + size-snapshot | 15 min |
| 6 | LC 100 Same Tree | Parallel recursion | 10 min |
| 7 | LC 226 Invert Binary Tree | Postorder swap | 10 min |
| 8 | LC 101 Symmetric Tree | Mirror-axis parallel | 15 min |
| 9 | LC 110 Balanced Binary Tree | Postorder + sentinel | 15 min |

**Day 1 victory criterion:** All 9 solved. Tier 1 should feel *boring* by EOD — that's the point.

**Skip if running long:** LC 145 (mechanical once you know LC 144). Pick it up in revision.

---

### **Day 2 — BST core + BFS extensions (Tier 2 + Tier 3)**

**Morning (45 min): Re-read prep**
- `trees-fundamentals.md` § **Binary Search Tree (BST)** (lines ~884–1090) — read twice. The BST invariant must be in your head cold.
- `trees-fundamentals.md` § **BST validation — the classic trap** (LC 98) — read both approaches

**Afternoon (3 hrs): BFS extensions (5 problems)**

| # | Problem | New idea | Time Target |
| --- | --- | --- | --- |
| 10 | LC 199 Right Side View | Last node per level | 12 min |
| 11 | LC 103 Zigzag Level Order | Alternate direction flag | 15 min |
| 12 | LC 107 Level Order II | LC 102 + reverse | 8 min |
| 13 | LC 1161 Max Level Sum | Track sum + level # | 12 min |
| 14 | LC 112 Path Sum | Top-down carry remaining | 12 min |

**Evening (3 hrs): BST core (5 problems)**

| # | Problem | New idea | Time Target |
| --- | --- | --- | --- |
| 15 | LC 700 Search in BST | Walk down with comparison | 8 min |
| 16 | LC 938 Range Sum of BST | BST + pruning | 12 min |
| 17 | LC 270 Closest Value in BST | Walk + best-so-far | 12 min |
| 18 | LC 235 LCA of BST | BST property | 12 min |
| 19 | LC 98 Validate BST | Bounds approach **AND** inorder approach (do both) | 30 min |

**Day 2 victory criterion:** You can write LC 98 *both* ways from memory. BST invariant is muscle memory.

---

### **Day 3 — Top-down DFS + Two-purpose climb + LCA (Tier 4 + Tier 5)**

**Morning (45 min): Re-read prep**
- `trees-fundamentals.md` § **Pattern 1: Top-Down DFS** (carry state down)
- `trees-fundamentals.md` § **Walkthrough 2: LCA of Binary Tree** (LC 236)

**Afternoon (3 hrs): Top-down DFS family (4 problems)**

| # | Problem | New idea | Time Target |
| --- | --- | --- | --- |
| 20 | LC 572 Subtree of Another Tree | Compose `isSameTree` | 15 min |
| 21 | LC 1448 Count Good Nodes | Carry running max | 15 min |
| 22 | LC 129 Sum Root to Leaf Numbers | Carry running number | 15 min |
| 23 | LC 236 LCA of Binary Tree | Propagate match upward | 25 min |

**Evening (2 hrs): The two-purpose climb (1 solve + 2 read-throughs)**

| # | Problem | What to do |
| --- | --- | --- |
| 24 | **LC 543 Diameter of Binary Tree** | **Solve cold.** This is the gateway to two-purpose recursion. |
| 25 | LC 113 Path Sum II | Solve — first backtracking-on-tree problem |
| 26 | LC 124 Max Path Sum | **READ the walkthrough only** (line ~1200 in trees doc). Don't attempt cold. |

**Day 3 victory criterion:** LC 543 solved cleanly. You can articulate "what does this function return up vs. what does it update globally" — the two-purpose pattern.

---

## 🧪 End-of-Day-3 Self-Test (15 minutes)

Pick **3 problems blind** from this list (use random.org or close your eyes):
- LC 104, LC 102, LC 226, LC 110, LC 235, LC 543, LC 199

Solve them in <15 min each. If yes → you're interview-ready on trees. If one trips you up, that's your weak spot for the weekend.

---

## ⏭️ What to skip / save for week 2

| Tier | Problems | Why skip on first pass |
| --- | --- | --- |
| **Tier 5 partial** | LC 687, LC 124 (cold solve) | Read walkthroughs only — don't attempt blind |
| **Tier 6** | LC 297, 105, 437, 863, 1373, 99, 450 | Multi-pattern / advanced. Bedtime reading at best. |
| **Tier 2 leftovers** | LC 515, LC 951 | Mechanical extensions; pick up in revision week |
| **Tier 3 leftovers** | LC 530, LC 230, LC 701 | After LC 98 clicks, these are <15 min each — do during revision |

---

## 🛠️ Optional pre-Day-1 setup

Currently no `Reference/trees-reference.md` cheatsheet exists for daily revision. Without one, you'll keep scrolling the 1,632-line DeepDive.

A compact 300–400 line `Reference/trees-reference.md` would contain:
- 4 traversal templates (1 line each)
- BFS template (skeleton)
- Top-down vs Bottom-up signatures
- BST invariant + 5 BST problem skeletons
- LCA template
- 4-question prompt for each problem ("what carries down? what comes up?")

**Time to build:** ~15 min. **Value:** ~30% faster Day 1–3 because no DeepDive re-scrolling.

---

## ✅ Daily Self-Check (use as a checklist each morning)

**Day 1 morning:**
- [ ] Re-read mental model section
- [ ] Re-read DFS + BFS templates
- [ ] Re-read 4 patterns
- [ ] Solve all 9 problems with time-box
- [ ] EOD: Tier 1 feels boring

**Day 2 morning:**
- [ ] Re-read BST section twice
- [ ] Re-read LC 98 both approaches
- [ ] Solve 5 BFS extensions
- [ ] Solve 5 BST problems
- [ ] EOD: LC 98 both ways from memory

**Day 3 morning:**
- [ ] Re-read top-down DFS pattern
- [ ] Re-read LC 236 walkthrough
- [ ] Solve 4 top-down DFS problems
- [ ] Solve LC 543 + LC 113
- [ ] Read LC 124 walkthrough (don't attempt cold)
- [ ] Run EOD blind test (3 random problems)

---

## 🚦 Rules of the sprint

1. **Time-box at 25 min** per problem. If stuck, read editorial; then **immediately re-solve from understanding** (don't accept-paste).
2. **Group by pattern** — solve all BFS-by-level problems back-to-back, not interleaved.
3. **No Tier 6** during the sprint. Save for revision week.
4. **Re-read before solving** — every day starts with 45–90 min of focused doc review.
5. **End-of-day spot-check** — pick 1 random problem from earlier in the day, solve cold.
6. **If you fall behind:** drop bonus problems (LC 1161, LC 951, LC 113), keep the foundations. Tier 1 + Tier 3 are non-negotiable.

---

## 🧾 TL;DR

- **22 problems in 3 days** — Tiers 1–3 + a touch of 4–5
- **Skip Tier 6** entirely
- **45–90 min re-read** before solving each day
- **25-min time-box, then editorial, then re-solve from understanding**
- **Day 3 evening blind test** = interview-readiness signal
- **Optional add:** `Reference/trees-reference.md` for daily-glance cheatsheet

---

> **🗑️ This file is temporary.** Delete after the 3-day sprint completes (around 2026-05-12). The canonical practice plan lives in `DeepDive/trees-fundamentals.md` § Practice Plan.
