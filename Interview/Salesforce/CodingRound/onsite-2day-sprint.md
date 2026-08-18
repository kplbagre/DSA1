# Salesforce SMTS — Onsite Coding Round: 2-Day Sprint (Aug 2026)

> **Your loop:** ✅ LLD+HLD done → 🎯 Onsite Coding Round (this doc) → HM Round
>
> **This is NOT the OA.** The onsite coding round is live, with an interviewer watching,
> on HackerRank CodePair. Your code must actually run and pass test cases.
> A shadow (senior) interviewer is often present — they ask complexity questions at the end.
>
> Research window: Aug 2025 – Aug 2026. All problem reports labeled with level + date.

---

## 🧾 TL;DR — The Onsite Coding Round in One Paragraph

**60 minutes. 2 problems. HackerRank CodePair.** The interviewer watches you code live.
Both solutions are run against hidden test cases before the round ends. A shadow interviewer
may be present and will probe: *"What's your time/space complexity? Can you do better?"*

Problems are **LC Medium → Hard**. The dominant pattern across 15+ onsite accounts:
**one DP or graph/tree problem + one array/string/design-code problem.** Walk through
your brute force first, derive complexity, then optimize. Silence = bad signal.

---

## 🧭 Format Confirmed from Real SMTS Onsite Accounts (2025–2026)

```
┌─────────────────────────────────────────────────────────────────┐
│  ONSITE CODING ROUND — Confirmed Format                         │
├─────────────────────────────────────────────────────────────────┤
│  Platform      │  HackerRank CodePair (live coding)             │
│  Duration      │  60 minutes                                    │
│  Problems      │  2 (both run against test cases)               │
│  Difficulty    │  LC Medium to Hard                             │
│  Interviewers  │  1 SMTS + 1 shadow (senior) often present      │
│  Probing       │  Time/space complexity after EVERY solution     │
│  Language      │  Java, Python, C++ — your choice               │
└─────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   You pass only when BOTH solutions run all test cases.
   One candidate solved a Hard correctly and was still rejected —
   time management failed the second problem entirely.
```

---

## 🔬 Real Onsite Problem Pairs — Confirmed Accounts

These are **onsite** coding rounds (live with interviewer), not OA. Sorted newest first.

| Date | Level | Problem 1 | Problem 2 | Outcome |
|------|-------|-----------|-----------|---------|
| Jul 2026 | SMTS | (Coding post-design round — 2 clean executable problems; exact wording not shared) | | Loop ongoing |
| Feb 2026 | SMTS→LMTS | Max of minimums across all windows of size K | Cycle detection in DAG (approach only — no code) | Offer |
| Dec 2025 | SMTS | HLD of Metric Collection System (design round) | LLD of Spotify — class diagrams + BCrypt password storage | Rejected |
| Aug 2025 | SMTS | Longest Substring Without Repeating Characters (LC #3) | Vertical Order Traversal of Binary Tree (LC #987) | Passed |
| Jan 2025 | SMTS | Coin Change variant (LC #322) | Search a 2D Matrix II (LC #240) | Offer |
| Jan 2025 | SMTS hiring drive | DP problem (bottom-up/top-down) | Binary Search problem | Passed OA, DS round |
| Unknown | SMTS | Reconstruct Itinerary variant | Queue with getMax() in O(1) | Passed |
| Unknown | SMTS | Longest Common Subsequence (LC #1143) | Deepest node in a complete binary tree | Passed |
| Unknown | SMTS | Palindrome DP (LC #5 variant) | Harder non-standard DP with recursion + memoization | Passed |
| Unknown | MTS | Merge Intervals (LC #56) | LFU Cache implementation (LC #460) | Passed |
| Unknown | MTS | Zigzag String Conversion (LC #6) | Letter Combinations of Phone Number (LC #17) | Passed |

**Pattern read:**
- **Column 1 is almost always DP or graph/tree.** LCS, Coin Change, Palindrome DP, Cycle detection, Vertical Traversal.
- **Column 2 is often array/string/design-code** — sliding window, Matrix search, Queue design, Backtracking.
- The Feb 2026 account had "approach only" for Q2 — but you can't bet on that. Assume code required.

---

## 🎯 Priority Problem List — Solve These in 2 Days

Ranked by confirmed frequency + probability of showing up.

### 🔹 Tier 1 — Highest Probability (confirmed in multiple SMTS onsite accounts)

| # | Problem | LC # | Difficulty | Why It's Here |
|---|---------|------|-----------|---------------|
| 1 | Coin Change | 322 | Medium | Confirmed Jan 2025 offer. Canonical DP. |
| 2 | Longest Substring Without Repeating Characters | 3 | Medium | Confirmed Aug 2025. Space complexity trap (O(1) not O(N)). |
| 3 | Longest Common Subsequence | 1143 | Medium | Confirmed in onsite account. Classic 2D DP. |
| 4 | Vertical Order Traversal of Binary Tree | 987 | Hard | Confirmed Aug 2025. HashMap vs TreeMap tradeoff probed. |
| 5 | Search a 2D Matrix II | 240 | Medium | Confirmed Jan 2025 offer. Binary search variant. |
| 6 | Reconstruct Itinerary | 332 | Hard | Confirmed in onsite. HashMap + DFS/Euler path. |
| 7 | Queue with getMax() in O(1) | custom | Medium | Confirmed onsite. Monotonic deque pattern. |

### 🔹 Tier 2 — High Probability (appeared in virtual/OA rounds adjacent to this level)

| # | Problem | LC # | Difficulty | Why It's Here |
|---|---------|------|-----------|---------------|
| 8 | Sliding Window: Min subarray with K distinct integers | 340/904 | Hard | Feb 2026 OA. Sliding window with two-pointer. |
| 9 | Course Schedule (Cycle in DAG) | 207 | Medium | Feb 2026 onsite. BFS Kahn's or DFS-color. |
| 10 | Palindrome DP | 5 | Medium | Multiple accounts. Expand-around-center or DP. |
| 11 | Decode Ways | 91 | Medium | High frequency Salesforce tag. DP. |
| 12 | ZigZag Conversion | 6 | Medium | Multiple MTS/SMTS accounts. String simulation. |
| 13 | Letter Combinations of Phone Number | 17 | Medium | Multiple accounts. Backtracking template. |
| 14 | Merge Intervals | 56 | Medium | High frequency tag. Sort + scan. |
| 15 | Kth Largest Element | 215 | Medium | OA accounts. Heap / QuickSelect. |

### 🔹 Tier 3 — Possible (design-code / concurrency — appeared in some coding rounds)

| # | Problem | Difficulty | Why It's Here |
|---|---------|-----------|---------------|
| 16 | LRU Cache thread-safe (LC #146) | Medium | "Design coding" round variant. Known SMTS ask. |
| 17 | Deepest node in complete binary tree | Medium | Confirmed in onsite pair with LCS. |
| 18 | Serialize/Deserialize Binary Tree (LC #297) | Hard | Appeared in onsite accounts. BFS or DFS both valid. |
| 19 | Sudoku Solver (LC #37) | Hard | MTS 2026 account (interviewer pivoted to this). Backtracking. |

---

## ⚠️ Complexity Traps Salesforce Specifically Probes

These came up verbatim in debrief accounts. Know the answer before you code.

**LC #3 — Longest Substring Without Repeating Characters:**
- Shadow interviewer asked: *"Is space O(N) or O(1)?"*
- Answer: **O(1)** — the set/map is bounded by the alphabet size (128 ASCII chars), not N.
- Wrong answer here = bad signal even if code is correct.

**LC #987 — Vertical Order Traversal:**
- Interviewer asked: *"Using TreeMap — what overhead does that add?"*
- Answer: **O(log N) per insertion** vs O(1) amortized for HashMap. Know the tradeoff.

**Heap problems (Kth largest, sliding window min):**
- Always state: *"Each element is pushed/popped at most once → O(N log K) total."*
- Distinguish O(N log K) from O(N log N). At interview, say it before they ask.

**DP problems:**
- State the recurrence relation explicitly before writing code.
- Know the difference between top-down (memoization) and bottom-up (tabulation) and when each is preferable (interviewer may ask you to switch).

---

## 🧠 The Walk-Through Formula (follow this every problem)

Salesforce explicitly evaluates communication, not just correctness. One rejection note:
*"Did not sufficiently demonstrate design thinking."* That candidate solved both problems.

```
1. Read the problem — restate it in 1 sentence.
   "So we need to find the minimum coins to make amount X from a given set."

2. State clarifying assumptions.
   "Can we reuse coins? Is amount always reachable? What's the constraint on N?"

3. Brute force — name it, give its complexity, explain why it's too slow.
   "Recursive O(2^N) — exponential, not feasible for N > 30."

4. Optimized approach — explain the key insight before coding.
   "Bottom-up DP: dp[i] = min coins to make amount i. Recurrence: dp[i] = min(dp[i], dp[i - coin] + 1)."

5. Code — clean variable names, no unexplained magic numbers.

6. Dry run with the example — trace through it out loud.

7. Edge cases before submitting — empty input, 0, single element, no solution.

8. Complexity — state both time AND space before the interviewer asks.
```

---

## 🗺️ 2-Day Schedule

### Day 1 — DP + Trees (highest probability tier)

**Morning (2–3 hrs):** Solve all 7 Tier 1 problems. Don't just read solutions — write code.
- LC #322, #3, #1143, #987, #240, #332 + Queue with getMax()

**Afternoon (1–2 hrs):** For each, practice saying the walk-through formula out loud.
Pick 2 problems and do a mock "explain to interviewer" run. Time yourself: target ≤25 min each.

**Evening (1 hr):** Review complexity for all 7. Write down the tricky ones on paper.

---

### Day 2 — Tier 2 sweep + mock run

**Morning (2 hrs):** Solve Tier 2 problems you haven't done: #207, #91, #56, #215, #5, #17.
Skip ones you've done recently with full solutions — just trace through them.

**Afternoon (1.5 hrs — CRITICAL):** Do a full timed mock.
- Set a 60-minute timer.
- Open a blank editor (HackerRank practice mode).
- Pick 2 problems from Tier 1+2 you haven't solved in the last week.
- Code both, run them, simulate the shadow-interviewer complexity questions.

**Evening (30 min):** Rest. Review the walk-through formula one more time. No new problems.

---

## ⭐ The 3 Things That Separate SMTS Pass from Fail

1. **Complexity derivation** — don't wait for the interviewer to ask. State it after coding.
2. **Time management** — one solved + one attempted beats two half-solved. Target ≤25 min on Q1 to leave 35 for Q2.
3. **Edge cases** — empty input, single element, no solution, duplicate values. Run at least 2 edge cases before hitting submit.

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | Created for focused 2-day onsite coding prep. Replaces general research framing from `research-findings-2026-08.md`. Sources: LC Discuss Jul 2026 Hiring Drive thread, Jan/Aug/Dec 2025 + Feb 2026 SMTS onsite accounts, Glassdoor SMTS filter, Blind backend SMTS thread. |
