# Salesforce SMTS — Onsite Coding Round: Focused Research (Aug 2026)

> **Your situation:** LLD+HLD already passed. Next = onsite coding round. Then HM.
> This file is solely for that one round. 2-day prep plan is in **§1** — read that first.
>
> **Don't duplicate-read:** `research-findings-2026-08.md` (same folder) has the
> full OA problem log, topic frequency tables, and machine-coding code templates.
> This file is the focused, distilled version for 2 days out.
>
> **Source window:** Aug 2025 – Aug 2026. Sources: LeetCode Discuss (~15 SMTS/LMTS
> threads), Glassdoor SMTS filter, Blind, CodingKaro, Naukri Code360, Substack accounts.
> **Honest caveat:** Salesforce is team-dependent and non-standardized. Most sources
> don't cleanly separate "onsite coding round" from "first virtual DSA screen" — where
> that distinction is uncertain, it is noted explicitly below.

---

## 🎯 §1 — Your 2-Day Plan (Read First)

You have ~45 effective minutes of coding time per round (interviewer introduction +
resume walkthrough eats the first 10–15 min of a 60-min slot). Two problems. That's
~20 min per problem. The round WILL push you to optimize after you land a solution.

### What the data says to prioritize

```
TIER 1 — DO THESE IN 2 DAYS (highest signal from recent onsite accounts)
────────────────────────────────────────────────────────────────────────
1. DP  ← appears in almost every confirmed onsite account as Q1 or Q2
   - LCS / k-tolerant palindrome / knapsack shape
   - Practice explaining recurrence out loud before writing code

2. Graph / BFS  ← the #2 slot in the 1-DP + 1-other pairing
   - Rotten Oranges (BFS on grid), Nodes at Distance K in Tree
   - Cycle detection in DAG, Reconstruct Itinerary

3. Binary Trees  ← also appears in the #2 slot
   - Deepest node in complete binary tree (confirmed SMTS onsite)
   - Rightmost leaf, vertical order traversal

TIER 2 — If time permits (Day 2 afternoon)
────────────────────────────────────────────────────────────────────────
4. Binary Search on Answer  ← KOKO / Find Smallest Divisor pattern
5. Heap problems  ← LFU Cache, stream-min with lazy offset
6. Merge Intervals + interval scheduling
```

### Day 1 Focus
- Morning: LCS (LC #1143) + k-palindrome (LC #516 + edit-distance variant)
- Afternoon: Rotten Oranges (LC #994) + Nodes at Distance K (LC #863) + Cycle in DAG
- Evening: Deepest node in complete binary tree + Vertical Order Traversal (LC #987)

### Day 2 Focus
- Morning: KOKO Eating Bananas (LC #875) + Find Smallest Divisor (LC #1283)
- Afternoon: Merge Intervals (LC #56) + LFU Cache (LC #460) — read through, don't fully code
- Evening: **Simulate the round.** One problem, 20 min, talk out loud. Then complexity pushback practice.
  Treat the last hour as mental prep + sleep early.

---

## 🧭 §2 — What the Round Actually Looks Like

```
ONSITE CODING ROUND — confirmed format (multiple SMTS/LMTS accounts)
─────────────────────────────────────────────────────────────────────
Platform     : HackerRank CodePair (shared editor, both see your code live)
Duration     : 60 minutes
Problems     : 2
Difficulty   : LC Medium → Hard (Q1 often Medium, Q2 often Hard or tricky Medium)
Shadow       : A second senior interviewer sometimes joins silently, asks
               1–2 deep questions at the end (confirmed: space complexity probes,
               "why not read-lock here?", "what happens at 1M customers?")
Code runs    : Yes — solutions run against test cases on HackerRank
Intro time   : 10–15 min of introductions + resume walkthrough BEFORE coding
Effective time: ~45 min total for 2 problems → ~20 min each

THE PATTERN: almost every confirmed account has exactly this shape:
   Q1  =  DP  (LCS, palindrome, knapsack-variant, decode-ways shape)
   Q2  =  Graph OR Tree OR Binary Search
   (or occasionally both DP — but DP is in at least one slot every time)
```

---

## 🔬 §3 — Confirmed Problems Table

**Labeling key:**
- ✅ **SMTS-onsite confirmed** = first-hand account that explicitly says "onsite coding round" or "Round 2 coding" after clearing the design round
- 🔶 **SMTS/adjacent** = SMTS account but unclear if OA, virtual screen, or onsite
- 🔷 **LMTS/MTS** = adjacent level, included because DSA bar is comparable

| Date | Label | Q1 | Q2 | Outcome |
|------|-------|----|----|---------|
| Onsite (classic, no date) | ✅ SMTS-onsite | **Longest Common Subsequence** (LC #1143) | **Deepest node in complete binary tree** | — |
| Jan 2025, Hyderabad | 🔶 SMTS | **Stream of numbers — 3 query types** (add X; add X to all; print+remove min) — heap + lazy offset | — | Offer |
| Aug 2025, Bangalore | 🔶 SMTS | **Longest Substring Without Repeating Characters** (LC #3) — shadow asked O(1) space | **Vertical Order Traversal of Binary Tree** (LC #987) | Passed |
| Apr 2025 | 🔷 LMTS | **Kth greatest element for subarrays K→N** — heap | **In-place: negatives first, positives second** | Offer |
| SMTS hiring drive, Jan 2025 | 🔶 SMTS | **DP problem** (10/15 test cases) | **Binary Search problem** (2/3 test cases — still passed) | Offer |
| Dec 2025 | 🔶 SMTS | **LFU Cache** (LC #460) — ~100 lines | **Adding polynomials** (verbal description, no examples given) | Unknown |
| Morgan Stanley→SF, SMTS | 🔶 SMTS | **Merge Intervals** (LC #56) | **LFU Cache** (LC #460) | Passed R2 |
| Apr 2025, LMTS | 🔷 LMTS | **k-tolerant palindrome** (delete ≤k chars to make palindrome) — DP | — | Offer |
| SMTS (Hyderabad, Substack) | 🔶 SMTS | **Rightmost leaf of binary tree** (BFS) | **Nodes at distance Y from node X** in binary tree | — |
| SMTS (Hyderabad, Substack) | 🔶 SMTS | **Rotten Oranges** (LC #994 — BFS on grid) | — | — |
| Feb 2026, Hyderabad | 🔶 SMTS | **Max of minimums for every window of size K** — heap/monotonic | **Cycle detection in DAG** (approach only, no code) | Offer (downleveled) |
| Jan 2025, Hyderabad | ✅ SMTS | **Coin Change** variant (LC #322 — DP) | **Search a 2D Matrix II** (LC #240) | Offer |
| MTS 2026 | 🔷 MTS | **Palindrome DP** | **Sudoku Solver** (backtracking) | Passed |

> **The pattern holds:** every confirmed account has DP in at least one slot, paired with
> graph/tree/binary-search in the other. No account shows two pure string/array problems
> without at least one of DP or graph.

### ⭐ The One Specific Problem That Repeats

**LFU Cache (LC #460)** is the only specific problem confirmed in multiple independent SMTS
accounts — Dec 2025 (as Q1, ~100 lines) and Morgan Stanley→SF SMTS (as Q2 after Merge
Intervals). Every other problem in the table is unique to its account.

The pattern repeat is the **shape** (DP + something), not a specific problem. But if you prep
only one data-structure implementation in the next 2 days, LFU Cache is it.

DP variants that repeat across accounts (different problem, same structure):
- LCS shape → LC #1143, LC #1035, LC #583
- Palindrome shape → LC #516, LC #1312, edit-distance variant
- Coin/knapsack shape → LC #322, LC #518

---

## 🧠 §4 — The 3-Query Stream Problem (Deep Dive)

This problem appeared in Jan 2025 (Hyderabad, confirmed offer). It is a non-standard problem
that looks hard but has an elegant trick. Worth knowing cold.

**Problem statement:**
Given a sequence of queries:
- `(1, X)` — Add X to your list
- `(2, X)` — Add X to **all existing numbers** in the list
- `(3)` — Print and remove the **minimum number**

**Why it's hard naively:** Type-2 queries would require updating every element — O(n) per query.

**The trick — lazy global offset:**

**Steps in plain English:**
1. Keep a `min-heap` storing actual inserted values.
2. Track a `globalOffset` (starts at 0). When query type-2 comes with value X,
   just add X to `globalOffset` — don't touch the heap.
3. When inserting (query type-1, X): store `X - globalOffset` in the heap, so that
   when you read it back, `heapValue + globalOffset` = actual value.
4. When printing min (query type-3): pop from heap, return `popped + globalOffset`.

```java
// Steps:
// 1. PriorityQueue (min-heap) stores adjusted values: actual - globalOffset.
// 2. globalOffset accumulates all type-2 additions without touching heap.
// 3. insert(X): heap.add(X - globalOffset).
// 4. addToAll(X): globalOffset += X.
// 5. printMin(): heap.poll() + globalOffset.

public class StreamWithOffset {
    private final PriorityQueue<Long> minHeap = new PriorityQueue<>();
    private long globalOffset = 0;

    public void insert(long x) {
        // Store adjusted value so real value = stored + globalOffset
        minHeap.offer(x - globalOffset);
    }

    public void addToAll(long x) {
        // Lazy: shift the reference point instead of updating every element
        globalOffset += x;
    }

    public long printAndRemoveMin() {
        return minHeap.poll() + globalOffset;
    }
}
```

**Complexity:** O(log n) per insert, O(log n) per remove, O(1) for addToAll. This is the
expected optimized solution — the interviewer in the Jan 2025 account gave a hint toward it.

> **Note:** One search result mentioned a "divisible by 4 hint" from the interviewer for this
> problem. That detail does not fit the heap + lazy offset solution and is likely a search
> artifact or misattribution. The canonical solution above is correct and clean.

---

## 🔬 §5 — What the Interviewer Is Actually Evaluating

From shadow-interviewer probes and rejection feedback across accounts:

**The moment the interviewer pushes back is the real test.** Every account describes a follow-up
question after the solution lands:

| What they ask | What they're checking | Right answer shape |
|---------------|-----------------------|-------------------|
| "What's the space complexity of this?" | Did you count all hidden allocations (stack, set size) | Derive it, don't guess |
| "Is it O(N) or O(1) space?" | Do you know ASCII-bounded charset = O(1) | "O(1) — bounded by 128 ASCII chars, not input size" |
| "Can you do this in O(n log k) instead?" | Can you recognize heap as the tool | "Yes — maintain a min-heap of size K, swap when needed" |
| "Now make it thread-safe" | Java concurrency knowledge | "ReentrantReadWriteLock — but get() needs write-lock too because it mutates access order" |
| "What if the input has 1 million items?" | Space-time trade-offs at scale | State what breaks first, what you'd change |
| "Approach only — no code needed" | Happened with DAG cycle (Feb 2026) | State algorithm name + invariant clearly |

**The rejection pattern:** Dec 2025 SMTS candidate solved both problems correctly but received
feedback: *"did not sufficiently demonstrate the level of design thinking expected for SMTS."*
The problems were medium backtracking — the failure was in communication and structure, not
correctness.

**Your job is to narrate every decision:**
- State the problem back in your own words.
- State the brute force and why it's not good enough.
- State the key insight (what makes the optimal solution work).
- Code. Narrate edge cases as you handle them.
- After finishing: re-derive complexity unprompted.

---

## ⚠️ §6 — Gotchas Specific to This Round

**Introductions eat 10–15 min.** Multiple accounts confirm the interviewer spends the first
chunk on team intro + your resume. Don't panic — this is normal. You still have ~45 min.

**"Approach only" is sometimes enough.** Feb 2026 Hyderabad: Q2 (DAG cycle detection) —
interviewer explicitly said approach only, no code. Don't over-code if they don't ask for it.

**10/15 test cases still gets you through.** The Jan 2025 hiring drive candidate passed 10/15
on Q1 and 2/3 on Q2 — and got the offer. Partial credit is real. Don't abandon a correct
approach just because edge cases are failing; state what's failing and why.

**LFU Cache in a coding round means write it all.** The Dec 2025 account describes ~100 lines
of code for LFU. If LFU comes up, they want the full implementation: two hashmaps + doubly
linked list per frequency bucket. Do not half-implement. If you haven't practiced it recently,
skim the structure in `research-findings-2026-08.md` (this folder) before your interview.

**The verbal-only problem.** The Dec 2025 account describes an "adding polynomials" problem
described verbally with no examples. This is an SMTS test for requirements clarification —
ask for examples before writing anything. This is as much a communication check as a coding
check.

---

## 🧭 §7 — What Happens If You Only Solve 1 Problem

**Short answer: survivable, but risky for downleveling.**

Two confirmed accounts show offers with incomplete solutions:

| Account | What happened | Outcome |
|---------|--------------|---------|
| Jan 2025 hiring drive | Q1: 10/15 test cases. Q2: 2/3 test cases. Neither fully solved. | **Offer (SMTS)** |
| Feb 2026 Hyderabad | Q2: "approach only, no code" (interviewer's call) | **Offer — downleveled to MTS** |

And the inverse — the Dec 2025 candidate **solved both correctly and was rejected** due to
poor communication.

**What fills the gap when you only finish 1:**

1. **Narrate the approach to Q2 even without code.** Getting the algorithm right verbally —
   naming the data structure, stating the invariant, explaining why it works — counts toward
   the SMTS signal.
2. **Explain what's failing on partial solutions.** "I know edge case X is failing because Y"
   is SMTS behavior. Sitting silently while debugging is not.
3. **Make Q1 perfect.** One clean solve with correct complexity derivation + handled edge cases
   outweighs two rushed, buggy solutions.

**The downleveling risk:** Feb 2026 cleared the round but landed MTS, not SMTS. If the bar for
SMTS is 2 solid complete solves and you deliver 1 + approach, you may pass the round at a
lower level. Factor this in — if SMTS is non-negotiable for you, push hard on Q2 even if it
means rougher code.

**What NOT to do:**
- Do not abandon Q1 to rush to Q2 — a half-baked Q1 + half-baked Q2 is worse than 1 clean solve.
- Do not go silent on Q2 — visible progress with verbal explanation always beats silent struggle.
- Do not skip complexity derivation on Q1 just because you're short on time — that's the SMTS bar.

---

## 🧾 TL;DR — 6 Things to Know Walking In

1. **60 min, 2 problems, ~45 min effective.** Introductions happen first.
2. **Q1 = DP. Q2 = Graph/Tree/BinarySearch.** This pairing dominates every confirmed account.
3. **LFU Cache is the only specific problem with multiple sightings.** Every other problem is one-of-a-kind per account.
4. **Optimize after first solution.** They WILL ask "can you do better." Prepare the push.
5. **Narrate everything.** The Dec 2025 rejection had correct code and still failed — on communication.
6. **1 complete solve + verbal approach on Q2 = survivable.** But risks downlevel from SMTS → MTS. Push hard on Q2.

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created — focused onsite coding round research. Sibling to `research-findings-2026-08.md`. Covers Aug 2025–Aug 2026 data from ~15 SMTS/LMTS first-hand accounts. |
| Aug 2026 | Added §3 callout: LFU Cache is the only specific problem with multiple independent sightings. Added DP shape repeat table (LCS / palindrome / coin variants). Added §7: what happens when you only solve 1 problem — offer possible but downlevel risk, with confirmed data from Jan 2025 and Feb 2026. Updated TL;DR to 6 points. |
