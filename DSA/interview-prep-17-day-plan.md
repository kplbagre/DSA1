# DSA Interview Prep — 17-Day Plan (Graphs + DP + Greedy, in depth)

> **Created:** May 2026
> **Audience:** Kapil (Walmart Java engineer, multiple interviews ahead — not a one-shot)
> **Goal:** **Depth-first mental model building.** Walk away with patterns that survive 6 months of decay, not surface coverage that evaporates after the first interview.
>
> **Already done:** ✅ Trees (full deep dive + reference) · ✅ Graph fundamentals + Grid DFS (LC 200)
>
> **Companion files** (cross-reference as you work the plan):
> - `DeepDive/trees-fundamentals.md` · `DeepDive/graphs-fundamentals.md` · `DeepDive/recursion-fundamentals.md` · `DeepDive/two-pointers-sliding-window-fundamentals.md`
> - `Reference/bfs-dfs-templates-reference.md` (the muscle-memory file)
> - `../JavaBackend/DeepDive/java-pass-by-value-semantics.md`

---

## 🎯 Why 17 Days

Three realities driving the math:

1. **DP needs ~10 days for depth, not 5.** Each pattern family (1D, 2D, LCS, LIS, knapsack 0/1, knapsack unbounded, state machine, interval, palindromic, DP-on-trees) is its own paradigm with its own state/transition shape. Cramming two patterns per day means you'll recognize them next week and forget them next month. The whole point of "in depth" is owning the **recursion → memoization → tabulation** conversion so cold that you can derive any new DP problem live in the interview.
2. **Graphs needs 4 days** (not 3) to fit in the 4 missing-but-important problems: LC 695 (bottom-up DFS), LC 547 (DSU intro), LC 417 (dual-source reverse BFS), LC 127 (implicit graph BFS). Skipping any of these leaves a gap an interviewer can find in 2 questions.
3. **Greedy needs 2 days**, not folded into DP. Proving the greedy choice property is a distinct skill — interval scheduling vs jump-game-style array greedy use very different proofs.

**Plus 1 mock day** at the end to consolidate. No final "rest day" — between interviews you'll get organic rest naturally.

---

## 🧭 Topic Allocation (high-level)

| Days | Block | Outcome |
| --- | --- | --- |
| **1-4** | Graphs medium (4 days) | Confident on grid family, topo sort, bipartite, DSU, implicit graphs |
| **5-14** | DP in depth (10 days) | Own the recursion → memo → tabulation conversion; recognize 8 pattern families on sight |
| **15-16** | Greedy in depth (2 days) | Distinguish "looks like DP, is greedy" problems; can prove greedy choice property |
| **17** | Mock + weak-spot drill (1 day) | Identify decay early; lock in fragile patterns |

---

## 🪜 The Sequence Map (why this order, not another)

```
Graphs (Days 1-4)
   │ — Already 25% covered; finishing it first while it's fresh
   ▼
DP foundations  (Day 5)
   │ — THE pivotal day. recursion → memo → tabulation muscle memory.
   │   Every later DP day reuses this drill.
   ▼
1D DP problems  (Day 6)            Builds: state design, base case discipline
   ▼
2D Grid DP      (Day 7)            Builds: 2D table, fill-order reasoning
   ▼
LCS family      (Day 8)            Builds: 2-string DP shape
   ▼
LIS + Edit Dist (Day 9)            Climax of subsequence DP
   ▼
Knapsack 0/1    (Day 10)           Builds: take-or-skip choice
   ▼
Knapsack Unbnd  (Day 11)           Same shape, ONE switch in loop order
   ▼
State machine   (Day 12)           Builds: states-as-conditions (e.g., holding stock)
   ▼
Interval DP +   (Day 13)           Builds: outside-in fill order
Palindromic DP
   ▼
DP on Trees     (Day 14)           Builds: bridges DP back to recursion
   ▼
Greedy I        (Day 15)           Sorted-by-key + interval problems
   ▼
Greedy II       (Day 16)           "Looks like DP" arrays — proof discipline
   ▼
Mock day        (Day 17)
```

The DP days are sequenced so each builds on the previous **mental model**, not just the previous syntax. Don't shuffle the order.

---

## 📅 Day-by-Day Plan

### Days 1-4 — Graphs Medium

#### Day 1 — Grid DFS family (both top-down and bottom-up styles)

**Goal:** Reinforce LC 200 muscle memory, add bottom-up DFS (which has a *return value*), and apply multi-source BFS for the first time.

| Problem | Tag | Pattern | Why this one |
| --- | --- | --- | --- |
| **LC 994** Rotten Oranges | ✅ | Multi-source BFS | First time seeding queue with ALL sources before the while loop |
| **LC 542** 01 Matrix | ✅ | Multi-source BFS | Same pattern, distance as output |
| **LC 695** Max Area of Island ⭐ | ✅ | Bottom-up grid DFS | The canonical "DFS returns a value, parents sum children" — fills the gap from LC 200 |
| **LC 733** Flood Fill | ✅ | Vanilla grid DFS | Warm-up; literal application of the template |

**Read first:** `DeepDive/graphs-fundamentals.md` § "Sub-Pattern 2: Multi-Source BFS"
**Watch for:** mark-on-enqueue (LC 994) · DFS returning `0` for the base case (LC 695) · the `nr = r + dr` typo trap

---

#### Day 2 — Reverse-direction BFS family

**Goal:** Learn the "complement" trick — sometimes the answer is what's NOT reachable from a source.

| Problem | Tag | Pattern | Why this one |
| --- | --- | --- | --- |
| **LC 130** Surrounded Regions | ✅ | Border-in BFS | Reverse logic: mark what's reachable from border, flip the rest |
| **LC 417** Pacific Atlantic Water Flow ⭐ | ✅ | Dual-source reverse BFS | TWO disjoint sources, intersection logic — uniquely instructive |

**Read first:** `DeepDive/graphs-fundamentals.md` § "Sub-Pattern 3: Reverse-Direction BFS"
**Watch for:** the "what to ASK the BFS about" inversion · running two separate visited matrices in LC 417 · the AND intersection step

> **Light day on purpose** — only 2 problems. Use the saved hours to revise Day 1's templates cold from memory (no peeking).

---

#### Day 3 — Topological Sort + Bipartite

**Goal:** First exposure to NEW algorithm shapes — Kahn's BFS and 2-color BFS.

| Problem | Tag | Pattern | Why this one |
| --- | --- | --- | --- |
| **LC 207** Course Schedule | ✅ | Kahn's algorithm | Detect cycle via "did all V appear in topo order?" |
| **LC 210** Course Schedule II | ✅ | Kahn's algorithm | Same algo, collect the order |
| **LC 785** Is Graph Bipartite | ✅ | 2-color BFS | `color[v] = 1 - color[u]` — first conflict means not bipartite |

**Read first:** `DeepDive/graphs-fundamentals.md` § "Kahn's Algorithm" + "Bipartite Check"
**Watch for:** in-degree array construction · seed queue with `inDegree[v] == 0` cells · the disconnected-graph outer loop for bipartite

---

#### Day 4 — Adjacency-list patterns + DSU intro + implicit graphs

**Goal:** Close the remaining gaps — DSU exists, BFS works on implicit graphs.

| Problem | Tag | Pattern | Why this one |
| --- | --- | --- | --- |
| **LC 547** Number of Provinces ⭐ | ✅ | Adjacency-matrix DFS + DSU intro | First DSU exposure (template only, no rank optimization needed yet) |
| **LC 261** Graph Valid Tree | ✅ | Undirected cycle + connectivity | "Is it a tree?" = `edges == V - 1 && connected` |
| **LC 127** Word Ladder ⭐ | 🟡 | BFS on implicit graph | **The hardest of the day.** Teaches "see the graph where it isn't drawn" |

**Read first:** `DeepDive/graphs-fundamentals.md` § "DSU Canonical Implementation" + Word Ladder section
**Watch for:** building neighbors lazily for LC 127 (don't precompute) · DSU's `find` + `union` template should fit on one screen

> **🐞 Update the templates reference doc** after Day 4 with one row for DSU and one row for "implicit graph BFS" — they'll be patterns you'll see again.

---

### Days 5-14 — DP In Depth (10 days)

> **The whole DP block hinges on Day 5.** Do it twice if you have to. If `recursion → memo → tabulation` doesn't click on Day 5, every later DP day will feel like memorization. If it DOES click, the later days feel like applying one mental model to new problem shapes.

#### Day 5 — DP Foundations (THE day) ⭐⭐⭐

**Goal:** Internalize the four-stage progression so cold that you can derive it for any new DP problem.

**The four-stage drill — applied to Fibonacci (LC 509) and Climbing Stairs (LC 70):**

1. **Stage 1: Brute recursion** — write `fib(n) = fib(n-1) + fib(n-2)`. Trace the call tree. Count repeated calls.
2. **Stage 2: Memoization (top-down)** — add a `Map<Integer, Integer>` or `Integer[]` cache. The function shape stays identical; only the cache check + store change.
3. **Stage 3: Tabulation (bottom-up)** — flip the recursion into a `for` loop. `dp[i] = dp[i-1] + dp[i-2]`. **Discuss aloud: how does the recursion's call tree map to this iteration order?**
4. **Stage 4: Space optimization** — observe that only `dp[i-1]` and `dp[i-2]` are needed. Collapse to two variables. `O(n) → O(1)` space.

**Apply the drill to TWO problems on the same day:**

| Problem | Tag | What the drill teaches |
| --- | --- | --- |
| **LC 509** Fibonacci | ✅ | The pure mechanical pattern — no real "state design" needed |
| **LC 70** Climbing Stairs | ✅ | Identical to Fib but the problem framing forces you to derive the recurrence yourself |
| **LC 198** House Robber | ✅ | First time the recurrence isn't obvious — `dp[i] = max(dp[i-1], dp[i-2] + nums[i])` |

**Read first:** *(no DP deep dive yet — this day IS the deep dive starting point — but read `recursion-fundamentals.md` if memoization feels shaky)*

> **Lesson learned the hard way (interview prep, May 2026):** Skipping Stage 1 (brute recursion) is the #1 reason DP feels like memorization. Always write recursion first, even when you "know" the tabulated answer. The recursion reveals the state shape; tabulation hides it.

**Deliverable at end of Day 5:** start a new file `DSA/DeepDive/dp-fundamentals.md` capturing the four-stage drill in your own words. Future-Kapil will thank you.

---

#### Day 6 — 1D DP Problems (apply the drill)

**Goal:** The four-stage drill applied to real 1D state problems.

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 213** House Robber II | ✅ | Circular array — solve as two linear House Robbers |
| **LC 746** Min Cost Climbing Stairs | ✅ | Trivial recurrence; revise the space-O(1) optimization |
| **LC 91** Decode Ways | 🟡 | First state with **invalid transitions** (leading zeros) |
| **LC 139** Word Break | 🟡 | State is index into string; transitions are dictionary lookups |

**Mental model line for the day:** *"`dp[i]` = answer for the prefix ending at index `i`. Transitions look BACK over a constant number of previous indices."*

---

#### Day 7 — 2D Grid DP

**Goal:** Move from 1D state to 2D state. Learn fill-order reasoning.

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 62** Unique Paths | ✅ | Canonical 2D table, fill row-by-row |
| **LC 63** Unique Paths II | ✅ | Same as 62 + obstacle handling (zero out the cell) |
| **LC 64** Minimum Path Sum | ✅ | Min instead of count; same fill order |
| **LC 120** Triangle | ✅ | Bottom-up fill is more elegant than top-down here |

**Mental model line for the day:** *"`dp[i][j]` answer depends on `dp[i-1][j]` and `dp[i][j-1]`. Fill row-by-row so dependencies are computed first."*

---

#### Day 8 — LCS family (Longest Common Subsequence)

**Goal:** The canonical 2-string DP shape. **LC 1143 is one of the 5 most important DP problems for interviews.**

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 1143** Longest Common Subsequence ⭐ | ✅ | THE 2-string DP archetype — memorize the recurrence |
| **LC 583** Delete Operation for Two Strings | ✅ | Trivial LCS variant — `answer = m + n - 2 * lcs` |
| **LC 712** Minimum ASCII Delete Sum | ✅ | LCS with weights instead of counts |
| **LC 392** Is Subsequence | ✅ | Two pointers solves it, but DO IT as DP first to drill the pattern |

**Mental model line for the day:** *"`dp[i][j]` = LCS of `s1[0..i)` and `s2[0..j)`. If chars match → `1 + dp[i-1][j-1]`. Else → `max(dp[i-1][j], dp[i][j-1])`."*

---

#### Day 9 — LIS + Edit Distance climax

**Goal:** Edit Distance (LC 72) is **the** DP interview problem. If you crack it cold, you've arrived.

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 300** Longest Increasing Subsequence | ✅ | O(n²) DP first; THEN O(n log n) patience sort as the optimization |
| **LC 673** Number of LIS | 🟡 | LIS with multiplicity — two parallel `dp[]` arrays |
| **LC 72** Edit Distance ⭐ | 🟡 | **Memorize the 3-transition recurrence.** Insert / Delete / Replace. |

**Mental model line for the day:** *"Edit Distance: `dp[i][j] = min(replace, insert, delete) + 1`. If chars match, no cost — `dp[i-1][j-1]`."*

> **🎯 Senior-framing for the interview room:** *"Edit Distance is the textbook 2-string DP. State = `(i, j)` indices into both strings. Three transitions: replace `dp[i-1][j-1] + 1`, insert `dp[i][j-1] + 1`, delete `dp[i-1][j] + 1`. If `s1[i-1] == s2[j-1]`, we inherit `dp[i-1][j-1]` for free."*

---

#### Day 10 — Knapsack 0/1

**Goal:** The take-or-skip choice. Foundation for combinatorial DP.

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 416** Partition Equal Subset Sum ⭐ | ✅ | THE canonical 0/1 knapsack — boolean reachability variant |
| **LC 494** Target Sum | ✅ | 0/1 knapsack with signs — transform into "find subset summing to `(sum + target) / 2`" |
| **LC 474** Ones and Zeroes | 🟡 | 0/1 knapsack with TWO capacities — a 3D DP |

**Mental model line for the day:** *"For each item, two choices: take it (sub-problem with smaller capacity) or skip it (sub-problem with same capacity). Iterate items outer, capacity inner."*

---

#### Day 11 — Knapsack Unbounded

**Goal:** Same shape as 0/1, ONE switch in loop order — that single switch is the entire concept.

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 322** Coin Change ⭐ | ✅ | Canonical unbounded knapsack — minimum count variant |
| **LC 518** Coin Change II | ✅ | Same problem, count-of-ways variant — outer loop on COINS, inner on AMOUNT (order matters for combinations vs permutations) |
| **LC 377** Combination Sum IV | 🟡 | The "permutations" twist on LC 518 — outer loop on AMOUNT, inner on COINS |

**Mental model line for the day:** *"Unbounded = same item can be taken multiple times = inner loop iterates FORWARD (so `dp[i]` can reuse `dp[i - coin]` from the SAME pass). 0/1 = iterate BACKWARD."*

> **Drill the LC 518 vs LC 377 distinction.** Loop order changes combinations into permutations. This is a senior-trap question.

---

#### Day 12 — State Machine DP (stocks family)

**Goal:** States = conditions (holding stock vs not holding). Transitions = actions (buy / sell / cooldown).

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 121** Best Time to Buy and Sell Stock | ✅ | Single transaction — solve as DP first, then realize it's also greedy (running min) |
| **LC 122** Best Time II | ✅ | Unlimited transactions |
| **LC 309** Best Time with Cooldown ⭐ | 🟡 | THE state machine — 3 states (held / sold / rest) |
| **LC 188** Best Time IV — k transactions | 🟡 | 3D DP `dp[day][txCount][holding]` — full state machine generalization |

**Mental model line for the day:** *"At each day, you're in one of these states. The transition table is small (5-7 entries). Write the table BEFORE the code."*

---

#### Day 13 — Interval DP + Palindromic DP

**Goal:** Different fill order (outside-in or by-length, not row-by-row). Palindromic DP is the most common interval-DP shape.

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 5** Longest Palindromic Substring ⭐ | ✅ | 2D `dp[i][j] = (s[i] == s[j]) && dp[i+1][j-1]`. Fill BY LENGTH, not row-by-row. |
| **LC 647** Palindromic Substrings | ✅ | Same DP as LC 5, count instead of track-max |
| **LC 516** Longest Palindromic Subsequence | 🟡 | LCS of `s` with reverse-of-`s` — clever reformulation |
| **LC 132** Palindrome Partitioning II | 🔴 | Hard — interval DP + minimum cuts. **Read theory only**, don't attempt cold. |

**Mental model line for the day:** *"Interval DP: `dp[i][j]` answers a question about substring `s[i..j]`. You can't fill row-by-row — you must fill BY INTERVAL LENGTH (length 1, then 2, then 3, ...) so smaller intervals are computed first."*

---

#### Day 14 — DP on Trees

**Goal:** Close the loop between DP and recursion. Every "bottom-up DFS that returns a tuple of subtree answers" is DP on trees in disguise.

| Problem | Tag | What's new |
| --- | --- | --- |
| **LC 337** House Robber III ⭐ | ✅ | Each call returns `int[]{robbed, notRobbed}` — TWO answers per subtree |
| **LC 124** Max Path Sum (revisit) | 🔴 | Already in trees deep dive; revisit through the DP lens — see `trees-fundamentals.md` § "Walkthrough 3" |
| **LC 968** Binary Tree Cameras | 🔴 | 3-state DP on trees: covered-by-self / covered-by-child / not-covered. **Senior-level — read theory, don't attempt cold.** |

**Mental model line for the day:** *"DP on trees = bottom-up DFS where each call returns a small tuple. The 'states' are the components of the tuple. Combine children's tuples in the parent."*

> **Re-read `trees-fundamentals.md` § "Pattern 2: Bottom-Up DFS"** at the start of this day — half of DP on trees is the same recursion pattern you already own.

---

### Days 15-16 — Greedy In Depth

> **Greedy ≠ DP.** Greedy commits to a locally optimal choice without revisiting; DP tries all choices and memoizes. Greedy works ONLY when the "greedy choice property" is provable — usually via an exchange argument. **The hard part of greedy is the PROOF, not the code.**

#### Day 15 — Greedy I: Interval problems (sort + sweep)

**Goal:** The "sort by something, then sweep" archetype.

| Problem | Tag | The greedy choice + proof sketch |
| --- | --- | --- |
| **LC 56** Merge Intervals ⭐ | ✅ | Sort by START. Sweep: if next overlaps, extend; else flush. Proof: any non-overlapping output partition can be rearranged sorted by start without changing the merge result. |
| **LC 435** Non-overlapping Intervals ⭐ | ✅ | Sort by END. Greedy keep earliest-ending; exchange argument shows it's optimal. |
| **LC 452** Min Arrows to Burst Balloons | ✅ | Same exact pattern as LC 435 — sort by end, count distinct end-points needed. |
| **LC 253** Meeting Rooms II | ✅ | Sort by start + min-heap of end times. Greedy: reuse the earliest-ending room. |

**Mental model line for the day:** *"Most interval-greedy problems sort by EITHER start or end. Try both — usually one is the trick that makes it linear."*

---

#### Day 16 — Greedy II: Array problems (the "looks like DP" trap)

**Goal:** Recognize when a 1D DP collapses to O(n) greedy. These are interview FAVORITES.

| Problem | Tag | The "looks like DP, is greedy" insight |
| --- | --- | --- |
| **LC 55** Jump Game ⭐ | ✅ | Looks like `canReach[i]` DP. Actually: track `maxReach`, fail if `i > maxReach`. O(n). |
| **LC 45** Jump Game II | ✅ | Looks like `minJumps[i]` DP. Actually BFS-flavored greedy — track current "frontier" and next frontier. O(n). |
| **LC 134** Gas Station | ✅ | Looks like brute O(n²). Greedy single-pass: if total gas ≥ total cost, the answer is the index after the last "tank went negative." |
| **LC 763** Partition Labels | ✅ | Greedy with last-occurrence preprocessing. Sweep and extend partition until current index == max last-occurrence in window. |

**Mental model line for the day:** *"If a 1D DP only ever looks at constant-many cells behind, it's a candidate for collapse to O(1) state — that's the greedy form."*

> **🎯 Senior-framing line to rehearse for the interview room (LC 55):**
>
> *"This looks like a 1D DP at first — `canReach[i]` based on previous reach — but we don't actually need to remember all of that. We only need the furthest position reachable so far. That collapses to O(n) greedy with O(1) space."*

> **Proof discipline:** For each greedy problem, BEFORE you submit, ask yourself: *"What's the exchange argument that proves this is optimal?"* If you can't articulate it in one sentence, you're guessing. Common arguments: *"Swapping any pair of choices doesn't improve the answer"* or *"This choice can't lock us out of a better future choice."*

---

### Day 17 — Mock + Weak-Spot Drill

**No new patterns. Pure consolidation.**

**Morning (90 min) — Timed mock:**
- Pick 4 problems blind from the 16 days. Mix: 1 graph, 2 DP (one easy-ish, one harder), 1 greedy.
- 25 min per problem, NO peeking at notes. Verbalize approach aloud before coding.

**Afternoon (90 min) — Weak-spot drill:**
- Identify the ONE problem from the morning that took longest or felt shakiest.
- Drill 2 more problems on JUST that pattern.

**Evening (60 min) — Templates ref doc update:**
- Open `Reference/bfs-dfs-templates-reference.md` and **add any new 🐞 callouts** from bugs that surfaced today.
- Skim trees-fundamentals.md TL;DR (trees decay without re-touch).

**Optional Day 18 (if interview is within 24 hours):**
- Light revision only (skim, no new problems).
- Stop studying by 4 PM. Sleep early.

---

## 🧠 Daily Habits (the meta-rules)

These are the rules that decide whether 17 days produces mastery or evaporation.

1. **Always start with brute recursion (DP days).** Even when you "know" the tabulated answer. The recursion reveals the state shape; tabulation hides it. Skip Stage 1 = memorization, not understanding.
2. **Verbalize before coding.** Even alone. "The state is `(i, j)` representing... The transition is... The base case is..." Interviews are 50% explanation, 50% code.
3. **Time yourself.** Medium should land in 20-25 min. If it hits 35+, look at the editorial, understand it, then re-attempt cold the NEXT day.
4. **Don't switch topics mid-day.** DP and graphs use different muscles. Context-switching kills retention.
5. **15-min daily warm-up: previous day's notes.** Open the previous day's deep-dive section, skim TL;DR, then start the new day's problems. Decay is real even at day-to-day scale.
6. **Mid-cycle Trees touch.** On the morning of Day 9 or Day 10, spend 15 min on `trees-fundamentals.md` § TL;DR. Trees were "done" by Day 0 — by Day 10 they've decayed 30%.
7. **Update the templates reference doc as you go.** Every NEW pattern (Kahn's, DSU, LCS, Edit Distance, knapsack 0/1, knapsack unbounded, state machine, LIS, interval DP, DP on trees, jump-game greedy) deserves a short entry. The doc is the long-term asset.
8. **For greedy: always ask "what's the exchange argument?"** before submitting. Proof discipline IS the greedy skill.

---

## ⚠️ Cut Criteria — If You Fall Behind

If by **end of Day 9 you've slipped 2+ days behind**, cut in this order (most-cuttable first):

1. **First cut:** Day 13 — LC 132 Palindrome Partitioning II (already marked 🔴) and LC 516 LPS variant.
2. **Second cut:** Day 14 — LC 968 Binary Tree Cameras (🔴) and skip the LC 124 revisit.
3. **Third cut:** Day 11 — drop LC 377 (combinations-vs-permutations distinction can be skipped if knapsack-unbounded core is solid).
4. **Fourth cut:** Day 9 — drop LC 673 (LIS variants).
5. **Fifth cut:** Day 6 — drop LC 139 Word Break (revisit later as a String DP problem).

**Never cut:**
- Day 5 (DP foundations — the entire DP block depends on it)
- Day 8 (LCS canonical — interview certainty)
- Day 9 LC 72 Edit Distance (THE DP interview problem)
- Day 10 LC 416 + Day 11 LC 322 (knapsack canonicals)
- Day 15-16 Greedy (the whole pair — they take only 2 days and pay back on every "looks like DP" trap)
- Day 17 Mock

---

## 🔗 Reference Files (cross-link map)

| When working on... | Open this first |
| --- | --- |
| Days 1-4 (Graphs) | `DeepDive/graphs-fundamentals.md` · `Reference/bfs-dfs-templates-reference.md` |
| Day 5 (DP foundations) | `DeepDive/recursion-fundamentals.md` (refresher on stack vs heap, recursion mechanics) |
| Day 14 (DP on Trees) | `DeepDive/trees-fundamentals.md` § "Pattern 2: Bottom-Up DFS" |
| Any DP day (state/return-value question) | `../JavaBackend/DeepDive/java-pass-by-value-semantics.md` |
| Days 15-16 (Greedy) | *(future doc — start `Patterns/greedy-fundamentals.md` after Day 16)* |

**By Day 17 you'll have created/updated:**
- New: `DSA/DeepDive/dp-fundamentals.md` (started on Day 5, grown daily)
- New: `DSA/Reference/dp-patterns-reference.md` (compact cheatsheet, daily-revisable like the BFS/DFS one)
- New: `DSA/Patterns/greedy-fundamentals.md` *(optional — only if greedy depth justifies its own file)*
- Updated: `DSA/Reference/bfs-dfs-templates-reference.md` with DSU + implicit graph rows

---

## 🧾 TL;DR — One-Page Summary

| Days | Theme | Top 3 problems |
| --- | --- | --- |
| 1-4 | Graphs medium completion | LC 695, LC 547, LC 127 |
| 5 | **DP foundations (the pivotal day)** | LC 509, LC 70, LC 198 — done four ways each |
| 6-7 | 1D DP + 2D Grid DP | LC 213, LC 91, LC 62, LC 64 |
| 8-9 | LCS + LIS + **Edit Distance** | LC 1143, LC 300, **LC 72** |
| 10-11 | Knapsack 0/1 + Unbounded | LC 416, LC 322, LC 518 |
| 12 | State machine DP (stocks) | LC 121, LC 309, LC 188 |
| 13-14 | Interval DP + DP on Trees | LC 5, LC 516, LC 337 |
| 15-16 | Greedy in depth | LC 56, LC 435, LC 55, LC 134 |
| 17 | Mock + weak-spot drill | (consolidation) |

**The single most important day:** Day 5 (DP foundations — recursion → memo → tabulation).
**The single most important problem:** LC 72 Edit Distance (Day 9).
**The single most important habit:** brute recursion FIRST, every DP day, no exceptions.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Plan created.** Trigger: post-LC-200 strategy conversation. Initial 10-day estimate was extended to 12, then to 17 once "depth, not cram" became the priority (multiple interviews ahead, not a one-shot). DP block expanded from 5 days to 10 to give each pattern family its own day. Greedy carved out as 2 separate days (not folded into DP) because greedy proof discipline is its own skill. |
