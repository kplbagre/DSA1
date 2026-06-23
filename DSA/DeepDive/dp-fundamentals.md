# Dynamic Programming — Fundamentals (Deep Dive)

> **Curriculum alignment:** this deep-dive blends **two YouTube playlists** that complement each other perfectly:
>
> - **Methodology / solutions:** Striver's **Dynamic Programming Series** (`take U forward` — 57 videos, DP 1 through DP 56). Every problem solved via the canonical four-stage drill: brute recursion → memoization → tabulation → space optimization. Watch his videos for video-first learning. Each section in this doc cites the relevant `DP N` video range.
> - **Pattern recognition / taxonomy:** **Aditya Verma's Dynamic Programming Playlist** (50 videos). His pattern-first organization (0/1 knapsack → unbounded → LCS → LIS → MCM → DP on trees) is what lets you walk into a cold problem and *know which template to reach for in 30 seconds*. The "Pattern Identification" section in this doc is built from his taxonomy.

> **Credit:** four-stage drill, recurrence framings, dry-run examples come from Raj Vikramaditya (`take U forward` / Striver). Pattern-identification questions, family-based organization, and the "how to spot the family from the problem statement" instinct come from Aditya Verma. Code in this doc is rewritten in our project style (one statement per line, instance fields, Java conventions). Bug callouts and decision frameworks are this doc's contribution.

---

## 🎯 Why You're Reading This (The Goal)

By the end of this doc, you should:

1. **Own the four-stage drill cold:** brute recursion → memoization → tabulation → space-optimization — applied to ANY new DP problem, live, in 25 minutes
2. **Recognize 7 DP families on sight** — within 30 seconds of reading a problem, name the family (1D Linear / 2D Grid / 0/1 Knapsack / Unbounded Knapsack / LCS / LIS / State Machine)
3. **Derive recurrences from problem statements** — not memorize them. The skill is "what is the state, what are the choices, what is the base case" — applied freshly to each problem
4. **Know when DP is the wrong tool** — many "looks like DP" array problems collapse to O(n) greedy (Day 16 of the 17-day plan). Spotting this saves your interview clock
5. **Pattern-match strings DP** — LCS, Edit Distance, Distinct Subsequences are variants of one 2D template; mastering it unlocks ~10 problems for the price of 1
6. **Know what to skip** — Interval DP (MCM, Burst Balloons), DP on Trees deep, Wildcard Matching, Egg Drop, Scrambled String are 🔴 Senior+ topics. They have their own section below the medium-scope divider; **do not attempt cold-solve** on first pass

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

Every problem and section in this doc is tagged so you can climb tiers in order.

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ | **Try now** — solvable with concepts covered up to that point | Open LeetCode, attempt cold, time-box 25-35 min |
| 🟡 | **Try after [section]** — needs concepts from a later section | Bookmark; return after the named section is solid |
| 🔴 | **Reference only / Senior+** | Read editorial for awareness; do **not** attempt cold |

> **Lesson learned the hard way (May 2026):** I burned an hour on LC 124 (Maximum Path Sum) in trees doc by attempting a 🔴 cold without the prerequisite ladder. The tier system exists precisely so you don't repeat that on **LC 312 Burst Balloons** or **LC 968 Binary Tree Cameras** here — both are 🔴 in Family 8 / 9 below the medium-scope divider. Don't touch them on first pass.

---

## 📍 Reading Roadmap — Medium vs Senior+

> **The doc is split into two halves.** The first half is **everything you need for a medium-level SDE-2/SDE-3 DP interview.** The second half is senior+ territory — important to be aware of, optional to master cold.

| Scope | Family numbers | Striver videos | Aditya Verma videos | When to read |
| --- | --- | --- | --- | --- |
| **Medium core** ✅ | Families 1-7 (1D, 2D Grid, 0/1 Knapsack, Unbounded, LCS, LIS, State Machine) | DP 1 → DP 47 | 1 → 32 | **Start here. Master before any interview.** |
| **Senior+** 🔴 | Families 8-10 (Interval/Partition, DP on Trees deep, Hard Misc) | DP 48 → DP 56 | 33 → 50 | Read editorials for awareness; revisit only after Family 1-7 is cold |

> **Look for the `═══ ✋ END OF MEDIUM-LEVEL SCOPE ═══` divider after Family 7 (State Machine DP).** Everything below is senior-level / L5+. The medium core alone covers ~80% of LC DP problems and almost every SDE-2/SDE-3 medium DP interview question.
>
> **If you have ≤ 3 weeks of prep:** master only Families 1-7. Don't even open Families 8-10.

---

## 🧠 What Is DP? (And When to Suspect It)

> **The big idea, in one sentence:** Dynamic Programming = **recursion + caching**. That's it. Every DP problem starts life as a recursive brute-force that does the same sub-computation multiple times. DP is the act of noticing the repetition and storing each answer the first time you compute it.

### The two prerequisites for DP

A problem is solvable by DP iff it satisfies **both** of:

1. **Overlapping subproblems** — the recursive call tree re-asks the same sub-question multiple times. (If every sub-question is unique, you don't need DP; recursion alone suffices.)
2. **Optimal substructure** — the optimal answer to the whole problem can be **built from optimal answers to its sub-problems**. (If not, DP can't help — you'd need exhaustive search or a different technique entirely.)

If either property is missing, DP is the wrong hammer. **Always sanity-check both before reaching for the cache.**

### The 5-signal checklist — "is this DP?" in 30 seconds

When you read a new problem, scan for these signals. Two or more = high DP probability.

| Signal | Example phrasing in the problem | Family hint |
| --- | --- | --- |
| **"Count the number of ways"** | "How many distinct ways to climb stairs?" | 1D / 2D / Knapsack-ways |
| **"Min / Max" with choices** | "Minimum cost path", "max profit with k transactions" | Grid / State Machine / Knapsack |
| **"Is it possible to achieve X"** | "Can we partition into equal-sum subsets?" | 0/1 Knapsack (boolean) |
| **"Longest / Shortest" subsequence/substring** | "Longest common subsequence", "shortest supersequence" | LCS / LIS family |
| **Choices at each step** + **finite states** | "At each step, take or skip", "buy / sell / cooldown" | Knapsack / State Machine |

> **Aditya Verma's pattern-recognition reflex:** when you spot a signal, immediately ask the **identification triplet**:
> 1. **What is changing across recursive calls?** → That's your state.
> 2. **What are the choices at each step?** → Those are your transitions.
> 3. **What is the smallest valid input?** → That's your base case.
>
> If you can answer all three in 30 seconds, you have the recursion. The rest of DP is mechanical.

### 🎨 Visual — Why Fibonacci screams "DP" (the explosion of repeated work)

The textbook example of overlapping subproblems. Watch `fib(5)` compute the same sub-trees over and over:

```
                          fib(5)
                         /      \
                    fib(4)       fib(3)              ← fib(3) computed once here
                   /    \         /    \
              fib(3)   fib(2)  fib(2)  fib(1)        ← fib(3) computed AGAIN here
              /   \     / \     / \                  ← fib(2) computed THREE times
          fib(2) fib(1) f(1)f(0) f(1)f(0)
           / \
         f(1) f(0)

Call counts for fib(5):
   fib(5) → 1×    fib(2) → 3×        ← repeated!
   fib(4) → 1×    fib(1) → 5×        ← repeated 5 times!
   fib(3) → 2×    fib(0) → 3×        ← repeated 3 times!

For fib(40), the call tree has ~204 BILLION nodes. Most are duplicates.
With memoization: ~40 nodes total. Each fib(k) computed exactly once.

KEY INVARIANT:
   In any DP problem, the "unique subproblems" count equals the state-space
   size. If brute recursion makes way more calls than that, you have
   overlapping subproblems → DP applies → caching collapses runtime.
```

> **The Fibonacci tease IS the textbook entry point for a reason.** It's the smallest example where the repetition is obvious. Every later DP problem in this doc is structurally the same observation applied to a richer state space.

---

## 📖 Terminology (Memorize These) [Striver DP 1, Aditya Verma 1]

| Term | Meaning |
| --- | --- |
| **State** | The minimum set of variables that uniquely identifies a sub-problem. E.g., `(i, j)` for a 2D grid; `(i, capacity)` for knapsack. The state is the function's parameters. |
| **State space** | The set of all possible state values. Determines memory bound. For knapsack with `n` items + `W` capacity, state space is `n × W` cells. |
| **Transition** | The recurrence — how `dp[state]` relates to `dp[smaller_states]`. E.g., `dp[i] = dp[i-1] + dp[i-2]`. |
| **Base case** | The smallest sub-problem(s) with hard-coded answers (no recursion). E.g., `dp[0] = 0, dp[1] = 1`. |
| **Recurrence** | The mathematical formula expressing `f(state) = combination_of(f(smaller_state))`. The recurrence IS the algorithm; everything else is implementation. |
| **Top-down DP** | Solve from the original problem down to base cases via recursion + memoization. Call tree grows top-down; cache stores answers as we return. Also called **memoization**. |
| **Bottom-up DP** | Start at base cases, iteratively fill a table outward to the final answer. Also called **tabulation**. No recursion — pure iteration. |
| **Memoization** | The cache used in top-down DP. Typically a `Map<State, Answer>` or an `Integer[]` / `int[]` array. **Distinct from "memorization"** — the cache memo*izes* (stores). |
| **Tabulation** | The DP table used in bottom-up DP. Pre-allocated as `int[n+1]` or `int[m+1][n+1]`. Filled in dependency order. |
| **Optimal substructure** | Property: the best answer to the whole problem is composed of best answers to sub-problems. **Required for DP.** |
| **Overlapping subproblems** | Property: the same sub-problem is asked multiple times in the recursion tree. **Required for DP** (otherwise plain recursion suffices). |
| **Space optimization** | Observing that `dp[i]` only depends on the last `k` cells (often `k=1` or `k=2`), so a full array is wasteful — collapse to `k` variables. Drops space from `O(n)` to `O(1)`. |
| **Path reconstruction** | Recovering the actual sequence of choices that led to the optimal answer (e.g., printing the LCS, not just its length). Often **lost** under space optimization — be careful. |
| **State design** | The hardest DP skill: deciding what variables go into the state. Over-state → memory explosion. Under-state → wrong answer. |
| **Decision tree** | The recursion's branching shape — at each call, what choices are being explored. Drawing this for the first 2-3 levels is the fastest way to *see* a DP problem. |

> **Mental model:** DP is a function from **state** to **answer**, defined by a **recurrence** with **base cases**. Top-down implements it as recursion + cache; bottom-up implements it as a loop + table. **Same function, two implementations.**

---

## 🪜 THE Four-Stage Drill (Mental Model — Drill This Cold)

> **This is the most important section in the entire doc.** Every DP problem from Family 1 (Fibonacci) to Family 10 (Egg Drop) uses the same four stages. **Master the drill on Fibonacci, then mechanically apply it to every later problem.** Skip Stage 1 even once and DP feels like memorization forever.

### The drill, in one picture

```
    Stage 1                Stage 2                Stage 3                Stage 4
    ────────               ────────               ────────               ────────
    Brute                  Top-down              Bottom-up              Space-
    recursion        →     memoization       →   tabulation        →   optimized
                                                                        tabulation

    Time:  O(2^n)          O(n)                  O(n)                   O(n)
    Space: O(n) stack      O(n) memo + stack     O(n) table             O(1)
                                                                        (or O(k))

    Mental load:           Mental load:           Mental load:           Mental load:
    "what's the recurrence?"  "add cache check"   "flip recursion       "observe which
                              "and cache store"    into a for loop"      cells you
                                                                         actually need"
```

> **The progression IS the interview narrative.** A senior interviewer wants to hear: *"I'll start with brute recursion to establish the recurrence. Then memoize for correctness. Then convert to tabulation to remove recursion overhead. Finally optimize space if the dependency pattern allows."* That's 4 sentences. Practiced once on Fibonacci, it transfers to every later DP problem.

---

### Stage 1 — Brute Recursion (the foundation)

> **The Stage-1 mantra:** *"Forget efficiency. Just write the function that defines the answer."* No cache, no array, no optimization. Just the recurrence as code.

**Steps in plain English (for any DP problem):**

1. **Identify the state.** Ask: *"What's the minimum set of variables that defines a sub-problem?"* For Fibonacci it's just `n`. For 2D grid it'd be `(i, j)`. For knapsack it'd be `(i, capacity)`.
2. **Identify the choices.** Ask: *"At each sub-problem, what are the possible ways to break it down into smaller sub-problems?"* For Fibonacci: only one choice (take the recurrence). For knapsack: two choices (take or skip).
3. **Write the base case.** Ask: *"What's the smallest input where I know the answer outright?"*
4. **Write the recurrence as a function.** Call yourself on smaller states, combine.

```java
public int fib(int n) {
    // Step 3 — base case: smallest inputs with hard-coded answers
    if (n <= 1) {
        return n;
    }

    // Step 4 — recurrence: combine answers from smaller states
    return fib(n - 1) + fib(n - 2);
}
```

**Time:** `O(2^n)` — call tree branches by 2 at each level, depth `n`. **Space:** `O(n)` recursion stack.

> **Why NEVER skip Stage 1:**
>
> - **The recurrence reveals the state shape.** Tabulation hides it (it's just "fill `dp[i]`"). If you can't write the recursion, you don't understand the problem.
> - **Stage 2 is a mechanical 3-line edit of Stage 1.** Memoization = recursion + cache check + cache store. You cannot do that edit without the recursion in front of you.
> - **Interview talking point.** Senior interviewers explicitly want to hear the brute approach first. Skipping it sounds like you memorized the answer.

---

### Stage 2 — Top-Down Memoization (cache the recursion)

> **The Stage-2 mantra:** *"Three lines of edit. Nothing else changes."* Take Stage 1's recursion. Add a cache. Add a check-then-return at the top. Add a cache-store before the return. Done.

**Steps in plain English:**

1. **Allocate a memo** sized to the state space. For 1D state of size `n`, that's `Integer[n+1]` (use `Integer` not `int` so `null` means "not yet computed"; alternatively use `int[]` initialized to a sentinel like `-1`).
2. **Cache check at the top of the function.** If the answer for this state is already computed, return it.
3. **Recurse as before.**
4. **Cache store before returning.** Save the computed answer into the memo.

```java
private Integer[] memo;

public int fib(int n) {
    memo = new Integer[n + 1];
    return solve(n);
}

private int solve(int n) {
    // Step 3 — base case (unchanged from Stage 1)
    if (n <= 1) {
        return n;
    }

    // Step 2 — cache check (the NEW line — return early if we've seen this state)
    if (memo[n] != null) {
        return memo[n];
    }

    // Step 4 — recurse, store, return
    memo[n] = solve(n - 1) + solve(n - 2);
    return memo[n];
}
```

**Time:** `O(n)` — each state computed exactly once. **Space:** `O(n)` memo + `O(n)` recursion stack = `O(n)` total.

> **Why `Integer[]` not `int[]`?** `int[]` defaults to `0`, which collides with the legitimate answer `fib(0) = 0`. Using `Integer[]` (default `null`) cleanly distinguishes "not yet computed" from "computed and the answer is zero." Alternative: use `int[]` with a sentinel like `-1` and check `memo[n] != -1`.

### 🎨 Visual — Memoization prunes the call tree

```
WITHOUT MEMOIZATION              WITH MEMOIZATION
   (Stage 1)                        (Stage 2)

         fib(5)                          fib(5)
        /      \                        /      \
     fib(4)   fib(3) ✓                fib(4)   [memo hit: fib(3)] ✓
     /    \                            /    \
  fib(3) fib(2) ✓                   fib(3)  [memo hit: fib(2)] ✓
   / \    / \                        / \
 fib(2)fib(1)f(1)f(0)               f(2) [memo hit: fib(1)]
  / \                                / \
 f(1)f(0)                         f(1) f(0)

 ~32 nodes (2^5)                    ~10 nodes (≈ 2 × n)
 Every duplicate sub-tree           First visit computes,
 recomputed from scratch            subsequent visits return
                                    cached answer instantly

KEY INVARIANT:
   With memoization, each unique state is computed exactly ONCE.
   Re-visits to a state are O(1) cache lookups, not full sub-tree recursions.
   Time complexity = (state space size) × (cost per transition).
```

---

### Stage 3 — Bottom-Up Tabulation (flip recursion into a loop)

> **The Stage-3 mantra:** *"Read the recurrence backwards."* The recursion goes from `n` down to base cases. Tabulation goes from base cases up to `n`. Same function, same recurrence — just a different evaluation order.

**Steps in plain English:**

1. **Allocate a DP table** sized to the state space (`int[n+1]` for Fibonacci).
2. **Seed the base cases.** Write the base cases of Stage 1 directly into the table (`dp[0] = 0; dp[1] = 1;`).
3. **Iterate from smallest state to largest**, applying the recurrence. The key trick: when you fill `dp[i]`, the cells you need (`dp[i-1]`, `dp[i-2]`) must already be filled. **The loop direction guarantees this.**
4. **Answer is at `dp[n]`** — the largest state.

```java
public int fib(int n) {
    // Edge case (the base cases handle n=0 and n=1; needed only for n=0
    // to avoid out-of-bounds when allocating size n+1)
    if (n <= 1) {
        return n;
    }

    // Step 1 — allocate the DP table
    int[] dp = new int[n + 1];

    // Step 2 — seed base cases (same as Stage 1 base case, written into the table)
    dp[0] = 0;
    dp[1] = 1;

    // Step 3 — iterate from smallest unsolved state to largest, applying recurrence
    for (int i = 2; i <= n; i++) {
        dp[i] = dp[i - 1] + dp[i - 2];
    }

    // Step 4 — final answer is at the largest state
    return dp[n];
}
```

**Time:** `O(n)`. **Space:** `O(n)` table. **No recursion stack** — that's the win over Stage 2.

### 🎨 Visual — Tabulation fills the table in dependency order

```
Bottom-up fill for fib(6):

dp index:    0   1   2   3   4   5   6
            ┌───┬───┬───┬───┬───┬───┬───┐
seeded:     │ 0 │ 1 │ . │ . │ . │ . │ . │   ← Step 2: base cases written
            └───┴───┴───┴───┴───┴───┴───┘

i=2:        │ 0 │ 1 │ 1 │ . │ . │ . │ . │   dp[2] = dp[1] + dp[0] = 1 + 0 = 1
            └───┴───┴───┴───┴───┴───┴───┘
                  ↑   ↑   ↑
                  └───┴───┘ both already filled — invariant holds

i=3:        │ 0 │ 1 │ 1 │ 2 │ . │ . │ . │   dp[3] = dp[2] + dp[1] = 1 + 1 = 2

i=4:        │ 0 │ 1 │ 1 │ 2 │ 3 │ . │ . │   dp[4] = dp[3] + dp[2] = 2 + 1 = 3

i=5:        │ 0 │ 1 │ 1 │ 2 │ 3 │ 5 │ . │   dp[5] = dp[4] + dp[3] = 3 + 2 = 5

i=6:        │ 0 │ 1 │ 1 │ 2 │ 3 │ 5 │ 8 │   dp[6] = dp[5] + dp[4] = 5 + 3 = 8
            └───┴───┴───┴───┴───┴───┴───┘
                                        ↑
                                  final answer

KEY INVARIANT:
   When the loop body fills dp[i], every dp-cell it READS (dp[i-1], dp[i-2])
   has ALREADY been filled by an earlier iteration. The loop direction
   (smallest state first) is precisely what guarantees this. Get the
   direction wrong and you read uninitialized cells.
```

---

### Stage 4 — Space Optimization (collapse the table)

> **The Stage-4 mantra:** *"How many cells of the past do I actually need?"* Look at the recurrence. `fib(i)` reads `dp[i-1]` and `dp[i-2]` — only the last two cells. Everything earlier is dead. So why allocate an array of `n+1` cells?

**Steps in plain English:**

1. **Read the recurrence.** Identify the lookback distance (`k = 2` for Fibonacci).
2. **Replace the array with `k` variables.** For Fib: `prev2` and `prev1`.
3. **In the loop body**, compute the new value, then shift the variables forward.
4. **Return the latest variable** at the end.

```java
public int fib(int n) {
    // Edge case — n = 0 or n = 1 returns directly (base cases)
    if (n <= 1) {
        return n;
    }

    // Step 2 — replace the array with the 2 cells we actually need
    int prev2 = 0;          // was dp[i-2]
    int prev1 = 1;          // was dp[i-1]

    // Step 3 — iterate; compute curr, then shift variables forward
    for (int i = 2; i <= n; i++) {
        int curr = prev1 + prev2;
        prev2 = prev1;       // shift: dp[i-2] becomes the old dp[i-1]
        prev1 = curr;        // shift: dp[i-1] becomes the new dp[i]
    }

    // Step 4 — the latest variable holds dp[n]
    return prev1;
}
```

**Time:** `O(n)`. **Space:** `O(1)` — we just dropped from `O(n)` to constant memory.

> **When space-opt is NOT possible (or is harmful):**
>
> - **Path reconstruction needed** — if you must print the actual LCS string (not just its length), you need the full table to backtrack through.
> - **Recurrence reads non-constant lookback** — if `dp[i]` reads `dp[i/2]` (e.g., LC 91 partial Decode Ways variants), the lookback distance grows; you can't collapse to k variables.
> - **2D recurrences with arbitrary access** — Edit Distance reads `dp[i-1][j-1]`, `dp[i-1][j]`, `dp[i][j-1]` — only the previous row. Space-opt to 2 rows is possible. But for interval DP that reads `dp[i+1][j-1]`, the dependency picks across the table arbitrarily — space-opt is much harder.
>
> **Default in interviews:** present Stage 3 as your final answer, then mention Stage 4 as an optional optimization. Don't volunteer Stage 4 in the first pass unless asked — code is more error-prone and the time complexity doesn't improve.

---

### The drill, summarized as a 4-row table

| Stage | What you write | Time | Space | When to show in an interview |
| --- | --- | --- | --- | --- |
| **1. Brute recursion** | Function returning the recurrence | `O(2^n)` typical | `O(n)` stack | First — verbally explain it, then write it |
| **2. Memoization** | Stage 1 + cache check + cache store | `O(state space)` | `O(state space) + O(n)` stack | After Stage 1 is on the board, as the "make it efficient" step |
| **3. Tabulation** | Loop replacing recursion | `O(state space)` | `O(state space)` | Final answer in most interviews — clean, no stack |
| **4. Space optimization** | Stage 3 with `k` vars replacing the array | `O(state space)` | `O(1)` or `O(k)` | Only if asked, or if the problem explicitly demands it |

> **Practice this drill twice on Fibonacci before opening any other problem.** Then drill it on LC 70 Climbing Stairs and LC 198 House Robber. After three problems you'll have it muscle-memoried and every subsequent DP problem will feel like applying a template, not solving a riddle.

---

## 🧭 Pattern Identification — Aditya Verma's Family Taxonomy

> **This is the Aditya Verma half of the doc.** Striver teaches you HOW to solve a DP problem (the four-stage drill). Aditya Verma teaches you HOW TO RECOGNIZE WHICH KIND of DP problem you're looking at. The two skills are independent; you need both.

The medium-scope DP world reduces to **7 families.** Internalize their signatures and you'll classify any new problem in 30 seconds.

### The 7-family decision tree

```
   ┌─────────────────────────────────────────────────────────────────────┐
   │  Read the problem. Ask the Aditya Verma identification triplet:     │
   │     (a) What is changing across recursive calls? → STATE            │
   │     (b) What are the choices at each step?       → TRANSITIONS      │
   │     (c) What is the smallest valid input?         → BASE CASE       │
   └─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
              ┌───────────────────────────────────────┐
              │  Is the state ONE integer (index/n)?  │
              └───────────────────────────────────────┘
                    │                          │
                  yes                          no
                    ▼                          ▼
          ┌─────────────────┐         ┌────────────────────┐
          │ 1D state        │         │ Multi-variable      │
          │                 │         │ state               │
          └─────────────────┘         └────────────────────┘
              │                              │
              ├─ "ways to reach n /          ├─ Is state (i, j) over a grid?
              │   max amount, no             │      → FAMILY 2: 2D Grid DP
              │   complex choice"            │
              │     → FAMILY 1:              ├─ Is state (i, j) over two strings?
              │       1D Linear DP           │      → FAMILY 5: LCS / Strings DP
              │                              │
              ├─ "stocks, buy/sell,          ├─ Is state (i, capacity)?
              │   states-as-conditions"      │  Items + capacity?
              │     → FAMILY 7:              │      → FAMILIES 3 / 4:
              │       State Machine DP       │        0/1 vs Unbounded Knapsack
              │                              │
              └─ "longest increasing         └─ Is state (i, j, k) with k a
                 subsequence ending at i"       transaction count or
                   → FAMILY 6: LIS              boolean condition?
                                                  → FAMILY 7: State Machine

   Below the medium-scope divider — 🔴 SENIOR+:
              ├─ Is state (i, j) where the LOOP picks a split point k?
              │     → FAMILY 8: Interval / Partition DP (MCM, Burst Balloons)
              │
              └─ Is the state a tree node + a state flag (e.g., "robbed"/
                "not robbed")?
                  → FAMILY 9: DP on Trees
```

### The 7 families at a glance (one-line signatures)

| # | Family | Signature problem | Recurrence shape | State |
| --- | --- | --- | --- | --- |
| **1** | 1D Linear DP | LC 198 House Robber | `dp[i] = combine(dp[i-1], dp[i-2])` | `i` |
| **2** | 2D Grid DP | LC 62 Unique Paths | `dp[i][j] = dp[i-1][j] + dp[i][j-1]` | `(i, j)` over grid |
| **3** | 0/1 Knapsack | LC 416 Partition Equal Subset | `dp[i][w] = take ∨ skip` | `(i, w)` items × capacity |
| **4** | Unbounded Knapsack | LC 322 Coin Change | Same as 0/1 BUT loop forward (item reused) | `(i, w)` |
| **5** | LCS / Strings DP | LC 1143 LCS, LC 72 Edit Distance | `dp[i][j]` on two indices into two strings | `(i, j)` over 2 strings |
| **6** | LIS | LC 300 Longest Increasing Subseq | `dp[i] = 1 + max(dp[j] : j<i, nums[j]<nums[i])` | `i` (or i × prev) |
| **7** | State Machine DP | LC 309 Best Time + Cooldown | `dp[i][state] = transition from each prior state` | `(i, state)` |
| **8** 🔴 | Interval / Partition | LC 312 Burst Balloons | `dp[i][j] = min/max over split point k in [i,j)` | `(i, j)` over interval |
| **9** 🔴 | DP on Trees | LC 337 House Robber III | `solve(node) → (robbed, not_robbed)` tuple | tree node × condition |
| **10** 🔴 | Misc Hard | LC 44 Wildcard, LC 887 Egg Drop | Problem-specific | Problem-specific |

### Aditya Verma's "identification questions" — apply at the start of EVERY problem

Before you reach for any template, ask:

1. **"What is changing across recursive calls?"** — Look at your recursive function's parameters. If `i` changes → 1D state. If `(i, j)` change → 2D state. The parameters ARE the state.
2. **"At each step, what is the choice I'm making?"** — Take or skip? Move right or down? Pick this character or that one? The choices ARE the transitions.
3. **"What's the smallest sub-problem I can answer outright?"** — Empty array? Index 0? `n = 0` or `n = 1`? That's your base case.

If you can answer all three before writing any code, **you've already solved the problem at the recurrence level.** The rest is mechanical translation through the four-stage drill.

> **Lesson learned the hard way (from Aditya Verma's intro lectures):** Most "stuck on DP" moments aren't actually stuck on DP — they're stuck on **state design**. The student doesn't yet know what variables go into the state. **Always pause and answer question 1 explicitly before writing any code.** State unclear → recurrence broken → everything downstream is garbage.

---

## 🎨 Style Habits — Build These From Day 1

> Some habits apply to **every DP problem you write** (even the simplest 1D ones). Others only matter when you encounter specific patterns (e.g., loop direction in unbounded knapsack). **Master the universal ones now**; skim the context-specific ones and revisit them when you hit the pattern.

---

### 🌐 Universal Habits (apply to every DP problem — start using today)

#### Habit 1 — Always start with brute recursion (Stage 1)

Never jump straight to tabulation. The recursion reveals the state shape; tabulation hides it.

```java
// ❌ Wrong (jumping to Stage 3 without thinking)
int[] dp = new int[n + 1];
dp[0] = 0; dp[1] = 1;
for (int i = 2; i <= n; i++) {
    dp[i] = dp[i - 1] + dp[i - 2];
}
// (You wrote this because you remembered Fibonacci, not because you derived it.)

// ✅ Right (derive Stage 1, then mechanically convert)
private int solve(int n) {
    if (n <= 1) return n;                      // base case — derived from problem
    return solve(n - 1) + solve(n - 2);        // recurrence — derived from "fib(n) = sum of two predecessors"
}
// Then convert to memoization, then tabulation, with the recurrence still on screen.
```

#### Habit 2 — Name your state precisely (in English) before writing any code

Write a one-line comment above the function:

```java
// dp[i] = number of ways to climb i stairs taking 1 or 2 steps at a time
public int climbStairs(int n) { ... }

// dp[i][w] = maximum value achievable using first (i+1) items with capacity w
public int knapsack01(int[] vals, int[] wts, int W) { ... }

// dp[i][j] = LCS length of s1[0..i) and s2[0..j)
public int lcs(String s1, String s2) { ... }
```

This single line forces you to clarify the state before the code. If you can't write the line, you don't yet know the state — go back to the identification triplet.

#### Habit 3 — Write the recurrence in English before the code

```
// recurrence (in English):
//   dp[i] = max of (rob the i-th house + dp[i-2])  OR  (skip i-th house + dp[i-1])
// base case:
//   dp[0] = 0 (no houses)
//   dp[1] = nums[0] (only one house — must rob it)
```

Then the code is a direct transcription. Mistakes get caught at the English level, not after a 30-line debug session.

#### Habit 4 — Base case before transition, always

Every DP function or loop starts with:

```java
if (smallest_state) {
    return hard_coded_answer;
}
```

Forgetting the base case is the #1 source of stack overflows and wrong answers. Write the base case **before** you write the recurrence in Stage 1.

#### Habit 5 — Memoize with `Integer[]` or `int[]`-with-sentinel, never plain `int[]`

```java
// ❌ Wrong — int[] default 0 collides with legitimate answer fib(0) = 0
int[] memo = new int[n + 1];                // default values are 0
if (memo[n] != 0) return memo[n];           // wrong! memo[0] should return 0 not recurse

// ✅ Right (Java) — Integer[] uses null for "not yet computed"
Integer[] memo = new Integer[n + 1];
if (memo[n] != null) return memo[n];

// ✅ Right (alternative) — int[] with sentinel
int[] memo = new int[n + 1];
Arrays.fill(memo, -1);
if (memo[n] != -1) return memo[n];
```

> **When the legitimate answer can be negative** (e.g., minimum path sum with negative values), the sentinel `-1` doesn't work either — use `Integer.MIN_VALUE` as the sentinel or use `Integer[]` with `null`.

---

### 🔧 Context-Specific Habits (will click as you encounter these patterns)

> These won't matter on your first 5 DP problems. **Skim them now to recognize the trap, then refer back when you actually hit the pattern.**

#### Habit 6 — In knapsack family, the loop direction encodes 0/1 vs unbounded

> Applies whenever you encounter "items + capacity" problems (Families 3 and 4).

The same outer/inner loop structure solves both, with **one** switch:

```java
// 0/1 Knapsack (each item taken at most once)
//   Inner loop iterates BACKWARD over capacity, so dp[w] reads OLD dp[w - wt[i]]
//   (i.e., the previous item's row — equivalent to not reusing item i).
for (int i = 0; i < n; i++) {
    for (int w = W; w >= wt[i]; w--) {      // ← BACKWARD
        dp[w] = Math.max(dp[w], dp[w - wt[i]] + val[i]);
    }
}

// Unbounded Knapsack (each item can be taken multiple times)
//   Inner loop iterates FORWARD over capacity, so dp[w] reads NEW dp[w - wt[i]]
//   (i.e., the SAME item's already-updated cell — equivalent to reusing item i).
for (int i = 0; i < n; i++) {
    for (int w = wt[i]; w <= W; w++) {      // ← FORWARD
        dp[w] = Math.max(dp[w], dp[w - wt[i]] + val[i]);
    }
}
```

The loop direction IS the entire conceptual difference. Memorize this — it's a senior-trap question.

#### Habit 7 — When you space-optimize, you may lose path reconstruction

> Applies whenever the problem asks to *print* the optimal sequence (LCS string, edit operations, the actual subset).

Space-optimized DP keeps only the last row/column of the table — but path reconstruction needs the whole table to backtrack through. **If the problem asks "print the LCS" not "find LCS length," keep the full table.**

#### Habit 8 — In 2D / interval DP, the fill order is NOT row-by-row

> Applies whenever the recurrence reads "across" the current row (e.g., `dp[i][j]` reads `dp[i][j+1]` or `dp[i+1][j]`).

Interval DP reads cells "inside" the current `(i, j)` range — those must be filled first. **Fill BY LENGTH** (length 1, then 2, then 3, ...) so smaller intervals are computed before larger ones that contain them. Row-by-row fill will read uninitialized cells and produce garbage.

(Worked example: Family 8 below the medium-scope divider — Burst Balloons / MCM.)

#### Habit 9 — Always sanity-check the state-space size before allocating

> Applies whenever the state has 3+ dimensions.

`dp[n][m][k]` with `n=100, m=100, k=100` is fine (1M cells). But `dp[n][m][k][l]` with each = 1000 is 1 trillion cells — TLE/MLE guaranteed. **Compute `n × m × k × ...` before allocating.** If it's > 10^8, you've over-stated. Find a way to reduce a dimension.

#### Habit 10 — Convert "is it possible?" boolean DP to bitset or `boolean[][]` — not `int[][]`

> Applies in 0/1 Knapsack boolean variants (e.g., Partition Equal Subset Sum).

```java
// ✅ Right — boolean DP for "can we reach sum S using subset of nums?"
boolean[][] dp = new boolean[n + 1][S + 1];
dp[0][0] = true;                              // empty subset reaches sum 0
for (int i = 1; i <= n; i++) {
    for (int s = 0; s <= S; s++) {
        boolean skip = dp[i - 1][s];
        boolean take = (s >= nums[i - 1]) && dp[i - 1][s - nums[i - 1]];
        dp[i][s] = skip || take;
    }
}
return dp[n][S];
```

Booleans force cleaner thinking and use 1/4 the memory of `int[][]`.

---

### Closing recap

> **Quick recap of the 5 universal habits:** brute recursion first → name the state in English → recurrence in English → base case before transition → memoize with `Integer[]` or sentinel. **Those five cover ~90% of habit benefit on your first 20 DP problems.**

---

### ⚠️ Three Rookie Mistakes — Lessons Learned the Hard Way (May 2026)

These three mistakes came from actually solving Family 1 problems cold. They're not in any textbook — they're the traps you fall into when you first try writing DP recursion on your own.

---

#### Mistake 1: Carrying the result as a parameter (State vs Result confusion)

> **Lesson learned the hard way (May 2026):** On LC 198 House Robber, I wrote `solve(sum, n, nums, i)` — carrying the accumulated money as a parameter. The recursion gave correct answers but **could never be memoized** because `sum` can be anything, making the state space `(i, sum)` instead of just `(i)`.

**The rule — ask this one question before adding ANY parameter:**

> *"If I'm standing at position i, do I need to know this value to make my decision?"*
>
> - **YES** → it's **STATE** (make it a parameter). Example: index `i`, remaining capacity `w`.
> - **NO** → it's the **RESULT** (return it, don't pass it). Example: accumulated sum, path count.

**The tell:** if a variable only INCREASES and never affects which CHOICES you can make, it's a result, not state. Money in House Robber never restricts whether you can rob a house — so it's a result.

```java
// ❌ Wrong — sum is luggage, not state
public int solve(int sum, int[] nums, int i) {
    if (i >= n) { return sum; }
    int include = solve(sum + nums[i], nums, i + 2);
    int notInclude = solve(sum, nums, i + 1);
    return Math.max(include, notInclude);
}
// Can't memoize: same i reached with different sums

// ✅ Right — solve(i) RETURNS the answer for its subproblem
public int solve(int[] nums, int i) {
    if (i >= nums.length) { return 0; }
    int include = nums[i] + solve(nums, i + 2);
    int notInclude = solve(nums, i + 1);
    return Math.max(include, notInclude);
}
// Memoizable: memo[i] has exactly one answer
```

**The mantra:** *"My function RETURNS the answer for its subproblem. It doesn't need to know what happened before it — only where it's starting from."*

---

#### Mistake 2: Mixing recursion directions (forward vs backward)

> **Lesson learned the hard way (May 2026):** On LC 746 Min Cost Climbing Stairs, I naturally think forward (0→n) but copied a backward (n→0) example. The code worked but the calling convention (`min(solve(n-1), solve(n-2))`) felt unnatural and I couldn't explain WHY it needed `min` of two calls.

**The rule — pick ONE direction and use it for everything:**

| | Forward (0→n) — recommended | Backward (n→0) |
| --- | --- | --- |
| `solve(i)` means | "best answer from index i **to the end**" | "best answer from start **to reach index i**" |
| Moves toward | `i+1`, `i+2` (toward n) | `i-1`, `i-2` (toward 0) |
| Base case | `i >= n → return 0` (past the end, nothing left) | `i < 0 → return 0` (before start) |

**Why forward is easier:**

1. **One base case** (`i >= n → 0`) vs two (`i < 0 → 0` AND `i == 0 → cost[0]`)
2. **Calling convention reads from the problem statement** — "can start from step 0 or 1" → `min(solve(0), solve(1))`
3. **Matches natural English** — "I'm standing here, what's the best I can do going forward?"

```java
// Forward House Robber:
// solve(i) = "max money from house i to the end"
// Call: solve(0)

// Forward Min Cost Stairs:
// solve(i) = "min cost to reach TOP from step i"
// Call: min(solve(0), solve(1))

// Forward Unique Paths:
// solve(i, j) = "number of ways from cell (i,j) to bottom-right"
// Call: solve(0, 0)
```

**Commit to forward (0→n). Use it for every family. Stop switching.**

---

#### Mistake 3: Not knowing WHY the calling convention needs `min` of two calls

> **Lesson learned the hard way (May 2026):** On Min Cost Stairs, I didn't understand why the answer was `min(solve(n-1), solve(n-2))` (backward) or `min(solve(0), solve(1))` (forward). It felt like magic.

**The rule — read the problem statement for the STARTING condition:**

> *"Where can I START?"* → that determines the initial call.

| Problem | Problem says... | Forward call |
| --- | --- | --- |
| House Robber | "rob from house 0 onward" | `solve(0)` — one starting point |
| Min Cost Stairs | "start from step 0 **or** step 1" | `min(solve(0), solve(1))` — two starting points, try both |
| Unique Paths | "start at top-left (0,0)" | `solve(0, 0)` — one starting point |
| Frog Jump | "frog starts at stone 0" | `solve(0)` — one starting point |

**The pattern:** If the problem gives you ONE starting point → one call. If it gives you MULTIPLE starting points → try each, take the best (`min` or `max`).

Min Cost Stairs says "you can start from step 0 **or** step 1" — that's two options. You don't know which is cheaper until you try both. Hence `min(solve(0), solve(1))`.

---

> 📍 **You've now finished the foundations (Sections 1-9).** Before continuing, do the four-stage drill cold on Fibonacci. Don't read the next family section until you can write all 4 stages of `fib(n)` without peeking.

---

## 🚶 Family 1 — 1D Linear DP

> **Striver videos:** DP 2-6 (Climbing Stairs, Frog Jump, Frog Jump K, House Robber I, House Robber II)
> **Aditya Verma:** does not treat 1D as a separate family (his playlist starts with knapsack) — get 1D from Striver and use this section's identification framing.

### Aditya Verma's identification triplet applied to 1D Linear DP

| Question | Answer for Family 1 |
| --- | --- |
| **What is changing across recursive calls?** | A single index `i` walking through an array (or a count `n` of "stairs remaining") |
| **What are the choices at each step?** | A SMALL FIXED set (2-3 choices), e.g., "rob this house or skip", "take 1 step or 2 steps" |
| **What's the smallest valid input?** | `i = 0` (first house / no stairs) and sometimes `i = 1` (second house / one stair) |

### Signature in 10 seconds

> **If the state is a single integer index** and the recurrence looks back to a **constant number of previous indices** (`dp[i-1]`, `dp[i-2]`, rarely `dp[i-3]`), it's Family 1. **The lookback distance equals the space-optimization variable count** (lookback 2 → optimize to 2 variables → O(1) space).

### The canonical problem — LC 198 House Robber

> Given an array `nums` of house values, rob a subset such that no two adjacent houses are robbed. Maximize the sum.

**The recurrence (in English) — derived from the identification triplet:**

- **State:** `dp[i]` = max money robbable considering houses `0..i`
- **Choices at house `i`:**
  - **Rob house `i`:** earn `nums[i] + dp[i-2]` (can't touch house `i-1`)
  - **Skip house `i`:** earn `dp[i-1]` (no constraint on `i-1`)
- **Transition:** `dp[i] = max(rob, skip)`
- **Base cases:** `dp[0] = nums[0]` (only house — must rob), `dp[1] = max(nums[0], nums[1])` (pick the bigger of two)

**Stage 1 — Brute recursion (for reference; never the final answer):**

```java
private int solve(int i, int[] nums) {
    if (i < 0) return 0;
    if (i == 0) return nums[0];
    int rob = nums[i] + solve(i - 2, nums);
    int skip = solve(i - 1, nums);
    return Math.max(rob, skip);
}
public int rob(int[] nums) {
    return solve(nums.length - 1, nums);
}
```

**Stage 3 — Tabulation (the typical interview deliverable):**

**Steps in plain English:**

1. **Edge cases:** empty → 0; single element → return it directly.
2. **Allocate `dp[n]`** sized to the array.
3. **Seed base cases:** `dp[0] = nums[0]`, `dp[1] = max(nums[0], nums[1])`.
4. **Fill from `i = 2` upward**, applying the rob-or-skip max.
5. **Return `dp[n - 1]`** — answer for the full array.

```java
public int rob(int[] nums) {
    int n = nums.length;
    // Step 1 — edge cases
    if (n == 0) {
        return 0;
    }
    if (n == 1) {
        return nums[0];
    }

    // Step 2 — allocate the DP table
    int[] dp = new int[n];

    // Step 3 — seed base cases
    dp[0] = nums[0];
    dp[1] = Math.max(nums[0], nums[1]);

    // Step 4 — fill upward
    for (int i = 2; i < n; i++) {
        int rob = nums[i] + dp[i - 2];
        int skip = dp[i - 1];
        dp[i] = Math.max(rob, skip);
    }

    // Step 5 — final answer
    return dp[n - 1];
}
```

**Time:** O(n). **Space:** O(n) — can be Stage-4 optimized to O(1) by keeping just `prev2` and `prev1` (identical pattern to Fibonacci's Stage 4).

### 🎨 Visual — The 1D lookback pattern

```
House Robber on nums = [2, 7, 9, 3, 1]:

index:     0    1    2    3    4
nums:    [ 2 │  7 │  9 │  3 │  1 ]

                  ┌───────┐
                  │  dp[]  │
                  ├───────┤
   dp[0] = 2     │  2    │  ← base (must rob house 0)
   dp[1] = max(2, 7) = 7  ← base (pick bigger of first two)
                ┌────┘
   dp[2] = max(9+dp[0], dp[1])      = max(11, 7) = 11
            ╲       ╱      ╲
             ╲     ╱        ╲
              rob skip       │
              house 2        │
              + reach        │
              from house 0   │
                ┌────────────┘
   dp[3] = max(3+dp[1], dp[2])      = max(10, 11) = 11
   dp[4] = max(1+dp[2], dp[3])      = max(12, 11) = 12  ← final answer

dp:      [  2 │  7 │ 11 │ 11 │ 12 ]
                                ↑
                          dp[n-1] = answer

KEY INVARIANT:
   dp[i] depends ONLY on dp[i-1] and dp[i-2]. Two-cell lookback →
   the entire dp[] array can be replaced by 2 rolling variables
   (prev1, prev2) → Stage 4 collapses memory from O(n) to O(1).
   This "constant lookback → constant space" property is what
   defines Family 1.
```

### Variants in this family

| Variant | LC | What's different from House Robber |
| --- | --- | --- |
| Climbing Stairs | LC 70 | "Add" instead of "max"; identical lookback shape. Pure Fibonacci. |
| Min Cost Climbing Stairs | LC 746 | "Min" instead of "max"; can start at step 0 OR step 1 → `min(dp[n-1], dp[n-2])` for the answer |
| House Robber II | LC 213 | **Circular array** — house 0 and house n-1 are adjacent. **Trick:** solve linear HouseRobber TWICE: once on `nums[0..n-2]` (exclude last), once on `nums[1..n-1]` (exclude first); answer = max of the two |
| Decode Ways | LC 91 | First family appearance of **invalid transitions** — leading zero in a 1-digit decode is invalid; some 2-digit decodes invalid too. Branch the recurrence on character validity. |
| Word Break | LC 139 | State = index into string; transitions = "for each j < i, is `s[j..i)` in dictionary AND `dp[j]` true?". O(n²) variant of 1D DP. |

> 🧩 **Try these (Family 1):**
> - ✅ **LC 70** Climbing Stairs — direct Fibonacci, drill all 4 stages
> - ✅ **LC 746** Min Cost Climbing Stairs — min variant
> - ✅ **LC 198** House Robber — the canonical
> - ✅ **LC 213** House Robber II — circular via two passes
> - 🟡 **LC 91** Decode Ways (after House Robber) — invalid-transition handling
> - 🟡 **LC 139** Word Break (after Decode Ways) — string-prefix DP

---

## 🚶 Family 2 — 2D Grid DP

> **Striver videos:** DP 7-13 (Ninja's Training, Unique Paths, Unique Paths II, Min Path Sum, Triangle, Falling Path Sum, Cherry Pickup II)
> **Aditya Verma:** not in his playlist — Striver is the source.

### Aditya Verma's identification triplet applied to 2D Grid DP

| Question | Answer for Family 2 |
| --- | --- |
| **What is changing across recursive calls?** | A pair of indices `(i, j)` representing cell coordinates in a 2D grid |
| **What are the choices at each step?** | A small fixed set of directions (typically "right or down" for paths from top-left to bottom-right; sometimes "down-left or down-right" for triangle / falling path) |
| **What's the smallest valid input?** | The destination cell (top-left or top-right edge); often `dp[0][0] = grid[0][0]` |

### Signature in 10 seconds

> **If the state is `(i, j)` where `i, j` are grid coordinates** and the recurrence reads from a small set of adjacent cells, it's Family 2. **Most common form:** `dp[i][j] = combine(dp[i-1][j], dp[i][j-1])` — read from top and left, fill row-by-row.

### The canonical problem — LC 62 Unique Paths

> Given an `m × n` grid, count the unique paths from top-left `(0,0)` to bottom-right `(m-1, n-1)` moving only right or down.

**The recurrence (in English):**

- **State:** `dp[i][j]` = number of unique paths from `(0,0)` to `(i,j)`
- **Choices to ARRIVE at `(i, j)`:** came from above `(i-1, j)` OR from the left `(i, j-1)`
- **Transition:** `dp[i][j] = dp[i-1][j] + dp[i][j-1]`
- **Base cases:** `dp[0][0] = 1` (one way to be at the start — start there). Entire top row and entire left column = 1 (only one way: keep going right / keep going down).

**Stage 3 — Tabulation:**

**Steps in plain English:**

1. **Allocate `dp[m][n]`.**
2. **Seed:** `dp[0][0] = 1`; first row and first column = 1.
3. **Iterate row-by-row** (i from 1, j from 1). Apply `dp[i][j] = dp[i-1][j] + dp[i][j-1]`.
4. **Return `dp[m-1][n-1]`.**

```java
public int uniquePaths(int m, int n) {
    // Step 1 — allocate
    int[][] dp = new int[m][n];

    // Step 2 — seed first row and first column
    for (int j = 0; j < n; j++) {
        dp[0][j] = 1;
    }
    for (int i = 0; i < m; i++) {
        dp[i][0] = 1;
    }

    // Step 3 — fill row-by-row
    for (int i = 1; i < m; i++) {
        for (int j = 1; j < n; j++) {
            dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
        }
    }

    // Step 4 — answer
    return dp[m - 1][n - 1];
}
```

**Time:** O(m·n). **Space:** O(m·n) — can be Stage-4 optimized to O(n) by keeping only the previous row.

### 🎨 Visual — 2D grid fill in dependency order

```
3×4 grid, computing Unique Paths from (0,0) to (2,3):

After seeding first row + first column:

          j=0   j=1   j=2   j=3
        ┌─────┬─────┬─────┬─────┐
   i=0  │  1  │  1  │  1  │  1  │   ← top row: one way (keep going right)
        ├─────┼─────┼─────┼─────┤
   i=1  │  1  │  ?  │  ?  │  ?  │   ← left col: one way (keep going down)
        ├─────┼─────┼─────┼─────┤
   i=2  │  1  │  ?  │  ?  │  ?  │
        └─────┴─────┴─────┴─────┘

Fill (i=1, j=1):  dp[1][1] = dp[0][1] + dp[1][0] = 1 + 1 = 2
                            ↑           ↑
                            top         left      (both already filled)

Fill (i=1, j=2):  dp[1][2] = dp[0][2] + dp[1][1] = 1 + 2 = 3
Fill (i=1, j=3):  dp[1][3] = dp[0][3] + dp[1][2] = 1 + 3 = 4

Row 1 done:

          j=0   j=1   j=2   j=3
        ┌─────┬─────┬─────┬─────┐
   i=0  │  1  │  1  │  1  │  1  │
        ├─────┼─────┼─────┼─────┤
   i=1  │  1  │  2  │  3  │  4  │
        ├─────┼─────┼─────┼─────┤
   i=2  │  1  │  3  │  6  │ 10  │   ← computed identically, top + left
        └─────┴─────┴─────┴─────┘
                                ↑
                          dp[m-1][n-1] = 10  ← final answer

KEY INVARIANT:
   When the loop body fills dp[i][j], the cells it reads (dp[i-1][j]
   from the row above, dp[i][j-1] from the left) have ALREADY been
   filled. Row-by-row + left-to-right iteration guarantees this.
   Get the iteration order wrong → read uninitialized zeros → wrong answer.
```

### Variants in this family

| Variant | LC | What's different |
| --- | --- | --- |
| Unique Paths II | LC 63 | **Obstacles** in the grid — when `grid[i][j] == 1`, set `dp[i][j] = 0` (no way through). Everything else identical. |
| Minimum Path Sum | LC 64 | **Min cost** instead of count. `dp[i][j] = grid[i][j] + min(dp[i-1][j], dp[i][j-1])` |
| Triangle | LC 120 | Triangle-shaped grid (row `i` has `i+1` cells). **Bottom-up fill is more elegant** — start at last row, propagate upward. |
| Falling Path Sum | LC 931 | "Variable starting and ending columns" — top row is the start, any cell in the bottom row is the destination. Recurrence reads 3 cells above (left-up, up, right-up). |
| Cherry Pickup II | LC 1463 | **3D DP** — two robots traversing the grid simultaneously. State = `(i, j1, j2)`. Worth reading once for the 3D extension experience. |

> 🧩 **Try these (Family 2):**
> - ✅ **LC 62** Unique Paths — the canonical
> - ✅ **LC 63** Unique Paths II — obstacle handling
> - ✅ **LC 64** Minimum Path Sum — min variant
> - ✅ **LC 120** Triangle — bottom-up fill
> - 🟡 **LC 931** Minimum Falling Path Sum (after Triangle) — variable start/end
> - 🔴 **LC 1463** Cherry Pickup II — 3D state, defer until later

---

## 🚶 Family 3 — 0/1 Knapsack (Subsequence DP)

> **Striver videos:** DP 14-19 (Subset Sum, Partition Equal Subset, Min Abs Sum Difference, Count Subsets Sum K, Count Partitions Given Diff, 0/1 Knapsack)
> **Aditya Verma:** videos 2-12 — this is **HIS playlist's signature family.** He treats 0/1 Knapsack as the parent template from which 5+ other problems derive. The identification framing below is his.

> **Aditya Verma's central insight:** Many DP problems that DON'T look like knapsack actually ARE 0/1 Knapsack in disguise. Subset Sum, Partition Equal Subset, Target Sum, Count Subsets with given sum — all are 0/1 Knapsack with a slightly different "goal" computation. **Recognize the family → reuse the template → save 80% of the work.**

### Aditya Verma's identification triplet applied to 0/1 Knapsack

| Question | Answer for Family 3 |
| --- | --- |
| **What is changing across recursive calls?** | TWO things — index `i` into the items, AND remaining capacity `w` (or remaining target sum). State is `(i, w)`. |
| **What are the choices at each step?** | **Exactly two choices:** TAKE the current item (if it fits) or SKIP it. **Each item is considered AT MOST ONCE** — that's what "0/1" means. |
| **What's the smallest valid input?** | No items left (`i == n`) → return 0. Or capacity exhausted (`w == 0`) → return 0. |

### Aditya Verma's "is this 0/1 Knapsack?" identification questions

> Ask these **before** writing any code. If you can answer YES to both, it's 0/1 Knapsack regardless of how the problem is worded:
>
> 1. **Are you choosing items from a list, with each item considered at most once?**
> 2. **Is there a finite capacity / target sum you must respect or hit?**
>
> If YES + YES → it's Family 3. Reuse the 0/1 Knapsack template; change only the "what we're computing" (max value vs boolean reachability vs count of ways).

### The canonical problem — 0/1 Knapsack

> Given `n` items with weights `wt[]` and values `val[]`, and a knapsack capacity `W`, pick a subset of items (each item at most once) to maximize total value such that total weight ≤ W.

**The recurrence (in English):**

- **State:** `dp[i][w]` = max value using items `0..i-1` with capacity `w`
- **Choices for item `i-1`:**
  - **Skip:** answer is `dp[i-1][w]` (don't use item `i-1`; capacity unchanged)
  - **Take** (only if `wt[i-1] ≤ w`): answer is `val[i-1] + dp[i-1][w - wt[i-1]]`
- **Transition:** `dp[i][w] = max(skip, take)` (or just `skip` if take is infeasible)
- **Base cases:** `dp[0][w] = 0` for all `w` (no items → no value)

**Stage 1 — Brute recursion (O(2^n) time, O(n) stack):**

**Before writing code — picture the take/skip tree:**

```
items = [(wt=1,val=1), (wt=3,val=4), (wt=4,val=5)],  W = 4
Call: knap(3, W=4) — consider all 3 items, capacity 4

knap(3, W=4)
├── TAKE item 3 (wt=4 ≤ 4): 5 + knap(2, 0) = 5 + 0 = 5
└── SKIP item 3:   knap(2, W=4)
                   ├── TAKE item 2 (wt=3 ≤ 4): 4 + knap(1, 1)
                   │         knap(1, 1): TAKE item 1 (wt=1 ≤ 1): 1 + 0 = 1
                   │         = 4 + 1 = 5
                   └── SKIP item 2: knap(1, W=4) = 1  (item 1 alone)
                   = max(5, 1) = 5
= max(5, 5) = 5  ✓  (take item 3 alone OR take items 1+2 — both give 5)

Why O(2^n)? At each of n items: exactly 2 choices (take / skip).
Binary branching over n levels → 2^n leaves.
```

```java
// Brute force recursion — O(2^n) time, O(n) stack
private int knap(int[] wt, int[] val, int i, int W) {
    // Base: no items left to consider, or no capacity remaining
    if (i == 0 || W == 0) {
        return 0;
    }
    // SKIP item i-1 — move to previous item, capacity unchanged
    int skip = knap(wt, val, i - 1, W);
    // TAKE item i-1 — only if it fits; move to previous item, reduce capacity
    int take = 0;
    if (wt[i - 1] <= W) {
        take = val[i - 1] + knap(wt, val, i - 1, W - wt[i - 1]);
    }
    return Math.max(skip, take);
}
// Call: knap(wt, val, n, W)
```

**Stage 2 — Memoization (O(n×W) time, O(n×W) space):**

State is `(i, W)` — two-dimensional. Use `Integer[n+1][W+1]` (null = uncomputed slot, same sizing rules as tabulation — needs indices 0 through n and 0 through W).

```java
// Memoization — O(n×W) time, O(n×W) space
private int knapMemo(int[] wt, int[] val, int i, int W, Integer[][] memo) {
    // Base: no items or no capacity
    if (i == 0 || W == 0) {
        return 0;
    }
    // Cache hit — same (i, W) reached before
    if (memo[i][W] != null) {
        return memo[i][W];
    }
    // Exact same logic as Stage 1 — nothing changes except the cache check
    int skip = knapMemo(wt, val, i - 1, W, memo);
    int take = 0;
    if (wt[i - 1] <= W) {
        take = val[i - 1] + knapMemo(wt, val, i - 1, W - wt[i - 1], memo);
    }
    memo[i][W] = Math.max(skip, take);
    return memo[i][W];
}
// Call: knapMemo(wt, val, n, W, new Integer[n + 1][W + 1])
```

**Why memoization eliminates the exponential:** In the brute tree, `knap(1, 1)` appears in multiple branches. With `memo[1][1]`, the second time it's a cache hit — O(1) instead of a full subtree. Total unique states = n × W.

**Stage 3 — Tabulation:**

**Steps in plain English:**

1. **Allocate `dp[n+1][W+1]`** — row 0 = "no items considered yet", column 0 = "zero capacity"
2. **Base case is automatic** — Java zero-initializes; row 0 and column 0 are already 0.
3. **Iterate items outer (`i`), capacity inner (`w`).** Apply skip-or-take max.
4. **Return `dp[n][W]`.**

```java
public int knapsack01(int[] wt, int[] val, int W) {
    int n = wt.length;
    // Step 1 — allocate (row 0 = "no items", col 0 = "no capacity")
    int[][] dp = new int[n + 1][W + 1];

    // Step 2 — base case automatic (dp[0][*] = 0)

    // Step 3 — iterate items outer, capacity inner
    for (int i = 1; i <= n; i++) {
        int weight = wt[i - 1];
        int value = val[i - 1];
        for (int w = 0; w <= W; w++) {
            // Skip item i-1
            int skip = dp[i - 1][w];

            // Take item i-1 (only if it fits)
            int take = Integer.MIN_VALUE;
            if (weight <= w) {
                take = value + dp[i - 1][w - weight];
            }

            dp[i][w] = Math.max(skip, take);
        }
    }

    // Step 4 — answer
    return dp[n][W];
}
```

**Time:** O(n·W). **Space:** O(n·W) — Stage-4 optimizes to O(W) by keeping just the previous row (since `dp[i][w]` reads only `dp[i-1][...]`).

### 🎨 Visual — 0/1 Knapsack DP table

```
Items: 3 items with (weight, value) = (1,1), (3,4), (4,5)
Capacity W = 4. Answer should be 5 (take item 3 alone) or 5 (take items 1+2; weight=4, value=5).

After tabulation:

           w=0   w=1   w=2   w=3   w=4
         ┌─────┬─────┬─────┬─────┬─────┐
   i=0   │  0  │  0  │  0  │  0  │  0  │  ← "no items considered" → value 0
         ├─────┼─────┼─────┼─────┼─────┤
   i=1   │  0  │  1  │  1  │  1  │  1  │  ← item (1,1): take whenever w ≥ 1
         ├─────┼─────┼─────┼─────┼─────┤
   i=2   │  0  │  1  │  1  │  4  │  5  │  ← item (3,4): take at w≥3
         ├─────┼─────┼─────┼─────┼─────┤
   i=3   │  0  │  1  │  1  │  4  │  5  │  ← item (4,5): take at w≥4 → tie with prev
         └─────┴─────┴─────┴─────┴─────┘
                                       ↑
                              dp[n][W] = 5  ← answer

How dp[2][4] = 5 was computed:
   skip item 2:   dp[1][4] = 1
   take item 2:   val[1]=4 + dp[1][4 - 3] = 4 + dp[1][1] = 4 + 1 = 5
                                                  ↑
                                            previous row, smaller capacity
   max(1, 5) = 5 ✓

KEY INVARIANT:
   dp[i][w] only reads dp[i-1][...] — the PREVIOUS row. This single-row
   dependency is why Stage 4 collapses memory to O(W). It's also why
   the inner loop direction matters: when collapsed to 1D, looping
   backward over w preserves the "previous row" semantics (0/1 knapsack);
   looping forward overwrites it with the current item's results
   (unbounded knapsack — see Family 4).
```

### Variants in this family — Aditya Verma's "all of these are 0/1 Knapsack"

| Variant | LC | What changes vs vanilla 0/1 Knapsack |
| --- | --- | --- |
| Subset Sum (boolean) | — / GFG | Goal becomes "can we hit sum S?" — `dp[i][s]` is BOOLEAN. Replace `max(skip, take)` with `skip || take`. |
| Partition Equal Subset Sum | LC 416 | Reduces to Subset Sum with target `S = total/2`. If total is odd, return false immediately. |
| Target Sum | LC 494 | Each number gets `+` or `-`. **Transform:** find subset P of nums summing to `(total + target) / 2` → reduces to Count Subsets with Given Sum. |
| Count Subsets with Given Sum | — / GFG | Counting variant of Subset Sum. Replace `||` with `+` (number of ways). |
| Min Subset Sum Difference | — / GFG | Find subset S₁ such that `|sum(S₁) - sum(S₂)|` is minimum. Solve Subset Sum for all reachable sums; pick the closest to `total/2`. |
| Count Partitions with Given Difference | — / GFG | Same transform as Target Sum — reduces to Count Subsets with Given Sum. |

> 🧩 **Try these (Family 3):**
> - ✅ **Subset Sum** (GFG / Striver DP 14) — start here; pure boolean knapsack
> - ✅ **LC 416** Partition Equal Subset Sum — reduces to Subset Sum
> - ✅ **LC 494** Target Sum — the `+/-` transform; canonical interview problem
> - 🟡 **Count Subsets with Sum K** (GFG / Striver DP 17) — count variant
> - 🟡 **Min Subset Sum Difference** (Striver DP 16) — sweep all reachable sums
> - 🟡 **LC 474** Ones and Zeroes (after vanilla 0/1) — 3D extension with TWO capacities

---

## 🚶 Family 4 — Unbounded Knapsack (Infinite Supply)

> **Striver videos:** DP 20-24 (Min Coins, Target Sum, Coin Change 2, Unbounded Knapsack, Rod Cutting)
> **Aditya Verma:** videos 13-17 (Unbounded Knapsack, Rod Cutting, Coin Change ways, Coin Change min)

> **Family 4 = Family 3 + ONE SWITCH.** Same DP table shape, same code structure — the ONLY difference is the loop direction (or equivalently, the index in the take-transition). **Master this difference and you've doubled your knapsack toolkit for free.**

### Aditya Verma's identification triplet applied to Unbounded Knapsack

| Question | Answer for Family 4 |
| --- | --- |
| **What is changing across recursive calls?** | Still `(i, w)` — items index and capacity |
| **What are the choices at each step?** | **TAKE (can be REUSED — pick item i again next time) or MOVE ON** to item i+1 |
| **What's the smallest valid input?** | Same as 0/1 — no items or no capacity |

### The 0/1 vs Unbounded identification question — ASK THIS

> **The single question that decides the family:**
>
> *"Can I use the same item more than once?"*
>
> - **NO** (each item at most once) → Family 3 (0/1 Knapsack)
> - **YES** (infinite supply of each item) → Family 4 (Unbounded Knapsack)
>
> Examples where YES:
> - Coin Change — can use each coin denomination unlimited times
> - Rod Cutting — can make multiple cuts of the same length
> - Combination Sum IV — can reuse each number unlimited times

### The canonical problem — LC 322 Coin Change

> Given coins of distinct denominations `coins[]` and a target `amount`, find the **minimum** number of coins needed to make `amount`. Each coin can be used unlimited times. Return -1 if impossible.

**The recurrence (in English):**

- **State:** `dp[i][w]` = minimum coins to make amount `w` using coins `0..i-1`
- **Choices for coin `i-1`:**
  - **Skip:** answer is `dp[i-1][w]`
  - **Take (still allow reuse):** answer is `1 + dp[i][w - coins[i-1]]` ← note **`dp[i]` not `dp[i-1]`** — we STAY on row `i` because we can reuse item `i`
- **Transition:** `dp[i][w] = min(skip, take)`
- **Base cases:** `dp[0][0] = 0` (zero coins make zero amount); `dp[0][w > 0] = INF` (no coins can't make positive amount)

---

### 🎨 Visual — Unbounded Knapsack decision tree (before any DP)

Example: `coins = [1, 2]`, `amount = 3` → **answer = 2** (one 1-coin + one 2-coin)

```
solve(i=2, w=3)   ← "minimum coins for amount=3 using coins[0..1]={1,2}"
│
├── SKIP coin 2:  solve(i=1, w=3)
│   │
│   ├── SKIP coin 1:  solve(i=0, w=3) = INF  ← no coins left, amount still > 0
│   │
│   └── TAKE coin 1 (STAY at i=1, reuse allowed):  1 + solve(i=1, w=2)
│       ├── SKIP coin 1:  solve(i=0, w=2) = INF
│       └── TAKE coin 1 (STAY at i=1):  1 + solve(i=1, w=1)
│           ├── SKIP coin 1:  solve(i=0, w=1) = INF
│           └── TAKE coin 1 (STAY at i=1):  1 + solve(i=1, w=0) = 1+0 = 1
│           → min(INF, 1+1) = 2              ← solve(i=1, w=2)
│       → min(INF, 1+2) = 3                  ← solve(i=1, w=3)
│
└── TAKE coin 2 (STAY at i=2, reuse allowed):  1 + solve(i=2, w=1)
    ├── SKIP coin 2:  solve(i=1, w=1) = 1     ← (computed in left branch above)
    └── TAKE coin 2:  w=1 < coin=2, impossible
    → min(1, INF) = 1                          ← solve(i=2, w=1)

→ min(3, 1+1) = 2  ✓

KEY INVARIANT:
   When we TAKE in Unbounded Knapsack, we STAY at the same item index i
   (not i-1 like 0/1 Knapsack) — this one switch enables unlimited reuse
   and is the ONLY structural difference between the two families.
```

> **Notice the repeated subproblems:** `solve(i=1, w=2)` and `solve(i=1, w=1)` would be recomputed multiple times in a larger tree. Memoization cuts O(2^(n+amount)) to O(n·amount).

---

**Stage 1 — Brute Recursion:** O(2^(n+amount)) time, O(n+amount) stack

```java
private int coinBrute(int[] coins, int i, int w) {
    // Base — amount reached: zero coins needed
    if (w == 0) {
        return 0;
    }
    // Base — no coins left but amount > 0: impossible
    if (i == 0) {
        // MAX_VALUE / 2 prevents overflow when caller does  1 + coinBrute(...)
        return Integer.MAX_VALUE / 2;
    }
    // Choice 1 — skip coin i (move to i-1, same amount)
    int skip = coinBrute(coins, i - 1, w);
    // Choice 2 — take coin i (STAY at i, reduce amount — reuse allowed)
    int take = Integer.MAX_VALUE / 2;
    if (coins[i - 1] <= w) {
        take = 1 + coinBrute(coins, i, w - coins[i - 1]);
    }
    return Math.min(skip, take);
}
// Call: int ans = coinBrute(coins, coins.length, amount);
//       return ans >= Integer.MAX_VALUE / 2 ? -1 : ans;
```

> **Why `Integer.MAX_VALUE / 2` not `Integer.MAX_VALUE`?** Because the caller does `1 + coinBrute(...)`. If the return is `MAX_VALUE`, adding 1 overflows to a negative — a silent wrong answer. Halving makes overflow impossible.

**Stage 2 — Memoization (top-down):** O(n·amount) time, O(n·amount) space

```java
private int coinMemo(int[] coins, int i, int w, Integer[][] memo) {
    // Base cases — same as brute
    if (w == 0) {
        return 0;
    }
    if (i == 0) {
        return Integer.MAX_VALUE / 2;
    }
    // Cache hit
    if (memo[i][w] != null) {
        return memo[i][w];
    }
    // Same choices as brute
    int skip = coinMemo(coins, i - 1, w, memo);
    int take = Integer.MAX_VALUE / 2;
    if (coins[i - 1] <= w) {
        take = 1 + coinMemo(coins, i, w - coins[i - 1], memo);
    }
    memo[i][w] = Math.min(skip, take);
    return memo[i][w];
}
// Call: Integer[][] memo = new Integer[coins.length + 1][amount + 1];
//       int ans = coinMemo(coins, coins.length, amount, memo);
//       return ans >= Integer.MAX_VALUE / 2 ? -1 : ans;
```

> **Memo sizing:** `Integer[n+1][amount+1]` — i goes 0..n (inclusive), w goes 0..amount (inclusive). Uses `Integer` (not `int`) so `null` serves as the "not yet computed" sentinel.

---

**Stage 3 — Tabulation:**

```java
public int coinChange(int[] coins, int amount) {
    int n = coins.length;
    final int INF = amount + 1;                           // sentinel for "impossible"

    int[][] dp = new int[n + 1][amount + 1];

    // Base case — row 0 means "no coins"
    for (int w = 1; w <= amount; w++) {
        dp[0][w] = INF;                                   // unreachable
    }
    // dp[0][0] = 0 is automatic

    for (int i = 1; i <= n; i++) {
        int coin = coins[i - 1];
        for (int w = 0; w <= amount; w++) {
            int skip = dp[i - 1][w];                      // don't use coin i-1
            int take = INF;
            if (coin <= w) {
                take = 1 + dp[i][w - coin];               // ← dp[i] not dp[i-1] — reuse allowed
            }
            dp[i][w] = Math.min(skip, take);
        }
    }

    int ans = dp[n][amount];
    return ans >= INF ? -1 : ans;
}
```

**Time:** O(n·amount). **Space:** O(n·amount) — Stage-4 optimizes to O(amount).

### 🎨 Visual — 0/1 vs Unbounded: the ONE loop-direction switch

```
SAME 2D dp table. SAME recurrence shape. ONE switch in space-optimized 1D form:

0/1 KNAPSACK (1D space-optimized)         UNBOUNDED KNAPSACK (1D space-optimized)
─────────────────────────────────         ──────────────────────────────────────

for each item i:                          for each item i:
    for w = W down to wt[i]:                   for w = wt[i] to W:
        dp[w] = max(dp[w],                          dp[w] = max(dp[w],
                    dp[w - wt[i]] + val[i])                     dp[w - wt[i]] + val[i])
                    ↑                                            ↑
                BACKWARD loop                                FORWARD loop
                reads OLD dp[w - wt[i]]                      reads NEW dp[w - wt[i]]
                (item i NOT yet                              (item i ALREADY taken
                applied at smaller w)                         at smaller w — reuse!)


Why the direction matters (concrete trace for W=4, item (wt=2, val=3)):

   START:           dp = [ 0 │ 0 │ 0 │ 0 │ 0 ]
                          w=0  w=1  w=2  w=3  w=4

   0/1 (loop w from 4 down to 2):
      w=4:  dp[4] = max(0, dp[4-2]=0 + 3) = 3  → dp = [0, 0, 0, 0, 3]
      w=3:  dp[3] = max(0, dp[1]=0 + 3) = 3    → dp = [0, 0, 0, 3, 3]
      w=2:  dp[2] = max(0, dp[0]=0 + 3) = 3    → dp = [0, 0, 3, 3, 3]
      ✓ each cell reads dp[w-2] from BEFORE this item was applied.

   UNBOUNDED (loop w from 2 up to 4):
      w=2:  dp[2] = max(0, dp[0]=0 + 3) = 3    → dp = [0, 0, 3, 0, 0]
      w=3:  dp[3] = max(0, dp[1]=0 + 3) = 3    → dp = [0, 0, 3, 3, 0]
      w=4:  dp[4] = max(0, dp[2]=3 + 3) = 6    → dp = [0, 0, 3, 3, 6]
                              ↑
                       NEW dp[2] (already includes this item!)
                       So dp[4] picks up TWO copies of item — exactly
                       what "unbounded supply" should produce.

KEY INVARIANT:
   The loop direction encodes whether dp[w - wt[i]] refers to the
   PREVIOUS row (item not yet applied → 0/1) or the CURRENT row
   (item already applied → reuse allowed → unbounded). This single
   character difference (`<=` vs `>=`, FORWARD vs BACKWARD) is the
   entire conceptual gap between Family 3 and Family 4.
```

### Variants in this family

| Variant | LC | What changes |
| --- | --- | --- |
| Coin Change (min coins) | LC 322 | The canonical — min count variant of unbounded knapsack |
| Coin Change II (count ways) | LC 518 | **Count combinations.** Outer loop on COINS, inner on AMOUNT → combinations (order doesn't matter). |
| Combination Sum IV | LC 377 | **Count permutations.** Outer loop on AMOUNT, inner on COINS → permutations (order matters). **The loop-order swap vs LC 518 is the trap.** |
| Rod Cutting | — / GFG | Cut a rod of length N into pieces of lengths 1..N with given prices; max profit. Pure unbounded knapsack. |
| Unbounded Knapsack proper | — / GFG | The "max value with weight limit, infinite supply" raw form. |

> **🐞 The LC 518 vs LC 377 trap — the loop-order distinction:**
>
> ```java
> // LC 518 Coin Change II — count COMBINATIONS (1+2 same as 2+1)
> // Outer = coins (consider each coin family once, then move on)
> for (int coin : coins) {
>     for (int amt = coin; amt <= amount; amt++) {
>         dp[amt] += dp[amt - coin];
>     }
> }
>
> // LC 377 Combination Sum IV — count PERMUTATIONS (1+2 different from 2+1)
> // Outer = amount (at each amount, every coin choice is a fresh permutation slot)
> for (int amt = 1; amt <= target; amt++) {
>     for (int coin : nums) {
>         if (coin <= amt) dp[amt] += dp[amt - coin];
>     }
> }
> ```
>
> Same data, two different totals. Senior-trap question — drill this distinction explicitly.

> 🧩 **Try these (Family 4):**
> - ✅ **LC 322** Coin Change — the canonical (min variant)
> - ✅ **LC 518** Coin Change II — count COMBINATIONS (coins outer)
> - ✅ **Rod Cutting** (Striver DP 24 / GFG) — direct unbounded knapsack
> - 🟡 **LC 377** Combination Sum IV (after LC 518) — count PERMUTATIONS (amount outer); learn the trap
> - 🟡 **Unbounded Knapsack proper** (Striver DP 23) — max-value variant if you want to round out

---

## 🚶 Family 5 — LCS / Strings DP (2-String 2D DP)

> **Striver videos:** DP 25-34 (LCS, Print LCS, LC Substring, LP Subseq, Min Insertions Palindrome, Min Insertions/Deletions A→B, SCS, Distinct Subsequences, Edit Distance, Wildcard Matching)
> **Aditya Verma:** videos 18-32 — second-largest section in his playlist after knapsack.

> **Family 5 is the highest-leverage family for medium DP interviews.** Master ONE template (the LCS 2D table) and you unlock ~10 problems including LC 72 Edit Distance (THE DP interview problem).

### Aditya Verma's identification triplet applied to Strings DP

| Question | Answer for Family 5 |
| --- | --- |
| **What is changing across recursive calls?** | TWO indices, one into each string — state is `(i, j)` |
| **What are the choices at each step?** | **If `s1[i] == s2[j]`** → match (consume both, recurse on `(i-1, j-1)`). **If not** → branch: skip from s1 (`(i-1, j)`) OR skip from s2 (`(i, j-1)`); pick the best. |
| **What's the smallest valid input?** | One of the strings is empty (`i == 0` or `j == 0`) — answer is hard-coded per problem (often 0 for LCS, `j` for Edit Distance) |

### Signature in 10 seconds

> **If the problem involves TWO strings (or one string compared with its reverse for palindromic problems)** AND the answer depends on matching/aligning substrings/subsequences, it's Family 5. State is `(i, j)`. Recurrence branches on `s1[i] == s2[j]`.

### The canonical problem — LC 1143 Longest Common Subsequence

> Given two strings `s1` and `s2`, find the length of their longest common subsequence (characters in same relative order, not necessarily contiguous).

**The recurrence (in English):**

- **State:** `dp[i][j]` = LCS length of `s1[0..i)` and `s2[0..j)` (prefixes of length `i` and `j`)
- **Choices:**
  - **If `s1[i-1] == s2[j-1]`** (chars match): `dp[i][j] = 1 + dp[i-1][j-1]` — take both
  - **Else (no match):** `dp[i][j] = max(dp[i-1][j], dp[i][j-1])` — drop one char from either string
- **Base cases:** `dp[0][j] = 0` and `dp[i][0] = 0` — empty string has LCS 0 with anything

---

### 🎨 Visual — LCS decision tree (before any DP)

Example: `s1 = "abc"`, `s2 = "ac"` → **answer = 2** (common subsequence "ac")

```
solve(i=3, j=2)  [s1[2]='c', s2[1]='c']  → MATCH
└── 1 + solve(i=2, j=1)  [s1[1]='b', s2[0]='a']  → NO MATCH
    ├── skip from s1:  solve(i=1, j=1)  [s1[0]='a', s2[0]='a']  → MATCH
    │   └── 1 + solve(i=0, j=0)  ← base: i==0 → return 0
    │   → returns 1
    └── skip from s2:  solve(i=2, j=0)  ← base: j==0 → return 0
        → returns 0
    → max(1, 0) = 1
→ 1 + 1 = 2  ✓

KEY INVARIANT:
   MATCH    → diagonal move (i-1, j-1): both chars consumed, count +1
   NO MATCH → two branches: skip one char from s1 (i-1, j) OR skip one
              from s2 (i, j-1); take the max of the two sub-answers
```

> **Notice the branching:** every mismatch splits into 2 calls. Strings of length m and n can have up to m+n mismatches → O(2^(m+n)) calls in the worst case. Memoization stores O(m·n) unique states and cuts it to O(m·n).

---

**Stage 1 — Brute Recursion:** O(2^(m+n)) time, O(m+n) stack

```java
private int lcsBrute(String s1, String s2, int i, int j) {
    // Base — one string exhausted: no common characters left
    if (i == 0 || j == 0) {
        return 0;
    }
    // MATCH — consume both characters, count this match
    if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
        return 1 + lcsBrute(s1, s2, i - 1, j - 1);
    }
    // NO MATCH — try dropping one char from either string, take the better result
    int skipS1 = lcsBrute(s1, s2, i - 1, j);
    int skipS2 = lcsBrute(s1, s2, i, j - 1);
    return Math.max(skipS1, skipS2);
}
// Call: lcsBrute(s1, s2, s1.length(), s2.length())
```

**Stage 2 — Memoization (top-down):** O(m·n) time, O(m·n) space

```java
private int lcsMemo(String s1, String s2, int i, int j, Integer[][] memo) {
    // Base — same as brute
    if (i == 0 || j == 0) {
        return 0;
    }
    // Cache hit
    if (memo[i][j] != null) {
        return memo[i][j];
    }
    // MATCH
    if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
        memo[i][j] = 1 + lcsMemo(s1, s2, i - 1, j - 1, memo);
        return memo[i][j];
    }
    // NO MATCH
    int skipS1 = lcsMemo(s1, s2, i - 1, j, memo);
    int skipS2 = lcsMemo(s1, s2, i, j - 1, memo);
    memo[i][j] = Math.max(skipS1, skipS2);
    return memo[i][j];
}
// Call: Integer[][] memo = new Integer[s1.length() + 1][s2.length() + 1];
//       lcsMemo(s1, s2, s1.length(), s2.length(), memo)
```

> **Memo sizing:** `Integer[m+1][n+1]` — `i` goes 0..m and `j` goes 0..n (both inclusive). `Integer` not `int` so `null` acts as the "not yet computed" sentinel.

---

**Stage 3 — Tabulation:**

**Steps in plain English:**

1. **Allocate `dp[m+1][n+1]`** where m = `s1.length()`, n = `s2.length()`. Row 0 and column 0 represent empty prefixes.
2. **Base case is automatic** — Java zero-initializes the table; empty prefix → LCS 0.
3. **Iterate `i` from 1 to m, `j` from 1 to n.** Apply the match/no-match branch.
4. **Return `dp[m][n]`.**

```java
public int longestCommonSubsequence(String s1, String s2) {
    int m = s1.length();
    int n = s2.length();
    // Step 1 — allocate
    int[][] dp = new int[m + 1][n + 1];

    // Step 2 — base case automatic (row 0, col 0 = 0)

    // Step 3 — fill
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                dp[i][j] = 1 + dp[i - 1][j - 1];
            } else {
                dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
    }

    // Step 4 — answer
    return dp[m][n];
}
```

**Time:** O(m·n). **Space:** O(m·n) — Stage-4 optimizes to O(n) using two rolling rows.

### 🎨 Visual — LCS 2D table fill

```
s1 = "abcde",  s2 = "ace"  →  expected LCS length = 3 ("ace")

                  j=0    j=1    j=2    j=3
                 (""")   "a"    "ac"   "ace"
        ┌──────┬──────┬──────┬──────┬──────┐
   i=0  │ ""    │   0  │   0  │   0  │   0  │   ← base: empty s1 → 0
        ├──────┼──────┼──────┼──────┼──────┤
   i=1  │ "a"   │   0  │  ◆1  │   1  │   1  │   ← 'a' == 'a' → 1 + dp[0][0]
        ├──────┼──────┼──────┼──────┼──────┤
   i=2  │ "ab"  │   0  │   1  │   1  │   1  │
        ├──────┼──────┼──────┼──────┼──────┤
   i=3  │ "abc" │   0  │   1  │  ◆2  │   2  │   ← 'c' == 'c' → 1 + dp[2][1]
        ├──────┼──────┼──────┼──────┼──────┤
   i=4  │ "abcd"│   0  │   1  │   2  │   2  │
        ├──────┼──────┼──────┼──────┼──────┤
   i=5  │ "abcde"│  0  │   1  │   2  │  ◆3  │   ← 'e' == 'e' → 1 + dp[4][2]
        └──────┴──────┴──────┴──────┴──────┘
                                          ↑
                                  dp[m][n] = 3  ← answer

Three cell-types:
   ◆ MATCH cell — chars at position (i-1, j-1) are equal.
                   dp[i][j] = 1 + (diagonal cell, top-left)
   ○ MISMATCH cell — chars differ.
                   dp[i][j] = max(cell above, cell to left)
   base — empty prefix; 0 by definition.

KEY INVARIANT:
   dp[i][j] reads ONLY dp[i-1][j], dp[i][j-1], dp[i-1][j-1] — three
   neighbors above and to the left. Row-by-row left-to-right fill
   guarantees all three are computed first. The diagonal read
   (dp[i-1][j-1]) is what makes Stage 4 collapse to 2 rolling rows
   (not just 1) — you need the previous row AND a "previous column"
   tracker.
```

### Variants in this family — the LCS template covers ALL of these

| Variant | LC | What changes from LCS |
| --- | --- | --- |
| Print LCS | — / GFG | Same table + backtrack from `dp[m][n]` following the match/max-pick choices |
| Longest Common Substring | LC 718 / GFG | **CONTIGUOUS** — when chars don't match, reset `dp[i][j] = 0` (not propagate from neighbors). Track the global max separately. |
| Longest Palindromic Subsequence | LC 516 | Trick: **LCS of `s` with reverse-of-`s`.** No new template needed. |
| Min Insertions to make Palindrome | LC 1312 | `n - LPS(s)` — chars not in LPS need a mirrored insert. |
| Min Insertions/Deletions to convert A→B | LC 583 | `(m + n) - 2 × LCS(A, B)` — chars not in LCS must be deleted from A or inserted to make B. |
| Shortest Common Supersequence (length) | LC 1092 | `m + n - LCS(A, B)` — LCS is shared once; the rest is concatenated. |
| Distinct Subsequences | LC 115 | "Count ways s contains t as subsequence." `dp[i][j] = dp[i-1][j-1] + dp[i-1][j]` if match; else `dp[i-1][j]`. |
| **Edit Distance** ⭐ | LC 72 | **THE DP interview problem.** Three transitions: replace, insert, delete. Recurrence: `dp[i][j] = 1 + min(replace, insert, delete)`; if chars match, no cost → `dp[i-1][j-1]` for free. |
| Wildcard Matching | LC 44 | 🔴 SR-leaning. Three matching cases: literal char, `?` (any single char), `*` (any sequence). State is still `(i, j)`. |

### LC 72 Edit Distance — the most-asked DP problem (preview)

> Given two strings `word1` and `word2`, find the minimum number of operations (insert / delete / replace) to convert `word1` into `word2`.

**The recurrence (in English):**

- **State:** `dp[i][j]` = min ops to convert `word1[0..i)` to `word2[0..j)`
- **If chars match** (`word1[i-1] == word2[j-1]`): inherit — `dp[i][j] = dp[i-1][j-1]` (no operation)
- **Otherwise**, three operations to try:
  - **Replace:** `1 + dp[i-1][j-1]`
  - **Insert into word1:** `1 + dp[i][j-1]`
  - **Delete from word1:** `1 + dp[i-1][j]`
  - Take the min.
- **Base cases:** `dp[i][0] = i` (delete all chars from word1); `dp[0][j] = j` (insert all chars from word2)

Full walkthrough comes in the "🔬 Worked Walkthroughs" section. For now, recognize: **same 2D table, same `(i, j)` state — just 3 transitions instead of 2.** That's the entire delta from LCS.

> 🧩 **Try these (Family 5):**
> - ✅ **LC 1143** Longest Common Subsequence — the archetype; drill all 4 stages
> - ✅ **LC 583** Delete Operations for Two Strings — LCS arithmetic
> - ✅ **LC 1092** Shortest Common Supersequence — LCS arithmetic + construction
> - ✅ **LC 72** Edit Distance ⭐ — **the most-asked DP interview problem.** Drill cold.
> - ✅ **LC 516** Longest Palindromic Subsequence — LCS with reverse
> - 🟡 **LC 115** Distinct Subsequences (after LCS) — counting variant
> - 🟡 **LC 718** Longest Common Substring (after LCS) — contiguity reset
> - 🔴 **LC 44** Wildcard Matching — defer to Family 10 senior section

---

## 🚶 Family 6 — Longest Increasing Subsequence (LIS)

> ⚠️ **TODO — Stage 1 + Stage 2 not yet added for this family.**
> Before studying the tabulation below, come back and add:
> - A concrete example (e.g., `nums = [10, 9, 2, 5, 3, 7, 101, 18]`) with a small decision tree showing TAKE vs SKIP branching
> - Stage 1 brute recursion: `lis(i, prev)` — `O(2^n)` time
> - Stage 2 memoization: `Integer[n][n+1]` table (i × prevIdx offset by 1) — `O(n²)` time
> - Note explaining why `prev` is needed as a parameter (to enforce the "increasing" constraint)
> Follow the same format as Families 3, 4, 5 above.

> **Striver videos:** DP 41-47 (LIS memoization, LIS tabulation, LIS binary search, Largest Divisible Subset, Longest String Chain, Bitonic, Number of LIS)
> **Aditya Verma:** does not have an LIS section — get from Striver.

### Aditya Verma's identification triplet applied to LIS

| Question | Answer for Family 6 |
| --- | --- |
| **What is changing across recursive calls?** | Two indices: `i` (current index being considered) and `prev` (last picked index, or -1 if none). State is `(i, prev)`. |
| **What are the choices at each step?** | **Take `nums[i]`** if `prev == -1` or `nums[i] > nums[prev]` (extends the subsequence). **Skip** always allowed. |
| **What's the smallest valid input?** | `i == n` (past end of array) → return 0 (no more elements). |

### Signature in 10 seconds

> **If the problem asks for "longest [monotone-condition] subsequence"** (increasing / decreasing / divisible / chain), it's Family 6. State involves an index `i` AND a previous-pick marker (sometimes collapsed to just `i` with `dp[i] = LIS ENDING at i`).

### Two formulations

LIS admits two distinct DP formulations — both worth knowing:

**Formulation A — `dp[i] = LIS length ending at index i`:** O(n²)

- For each `i`, look back at every `j < i`. If `nums[j] < nums[i]`, `dp[i]` can extend `dp[j]`. Take the max + 1.
- Final answer: `max(dp[i] for all i)`.

**Formulation B — Patience Sort + Binary Search:** O(n log n)

- Maintain a `tails[]` array where `tails[k]` = smallest possible tail value of an LIS of length `k+1`.
- For each `nums[i]`, binary-search `tails[]` for the leftmost element ≥ `nums[i]`; replace it (or append if `nums[i]` is bigger than all).
- Final LIS length = `tails.length()`.

### The canonical problem — LC 300 Longest Increasing Subsequence

> Given `nums[]`, find the length of the longest strictly increasing subsequence.

**Formulation A — Stage 3 Tabulation (O(n²)):**

**Steps in plain English:**

1. **Allocate `dp[n]`.** Each `dp[i]` = LIS length ending exactly at index `i`.
2. **Initialize every cell to 1** — each element is an LIS of length 1 by itself.
3. **For each `i`**, look back at every `j < i`. If `nums[j] < nums[i]`, this `nums[i]` extends the LIS ending at `j` → `dp[i] = max(dp[i], dp[j] + 1)`.
4. **Final answer is the max of dp[]**, not `dp[n-1]` — the LIS might end anywhere.

```java
public int lengthOfLIS(int[] nums) {
    int n = nums.length;
    // Step 1, 2 — allocate; every single element is LIS-1
    int[] dp = new int[n];
    Arrays.fill(dp, 1);

    int best = 1;

    // Step 3 — for each i, scan all j < i
    for (int i = 1; i < n; i++) {
        for (int j = 0; j < i; j++) {
            if (nums[j] < nums[i]) {
                dp[i] = Math.max(dp[i], dp[j] + 1);
            }
        }
        best = Math.max(best, dp[i]);
    }

    // Step 4 — max of dp[] (LIS might end anywhere)
    return best;
}
```

**Time:** O(n²). **Space:** O(n).

**Formulation B — Patience Sort + Binary Search (O(n log n)):**

```java
public int lengthOfLIS(int[] nums) {
    List<Integer> tails = new ArrayList<>();
    for (int x : nums) {
        // Find leftmost index in tails where tails[idx] >= x
        int idx = lowerBound(tails, x);
        if (idx == tails.size()) {
            tails.add(x);                       // x extends LIS to a new length
        } else {
            tails.set(idx, x);                  // x replaces a candidate tail (smaller is better)
        }
    }
    return tails.size();
}

private int lowerBound(List<Integer> arr, int target) {
    int lo = 0;
    int hi = arr.size();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr.get(mid) < target) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }
    return lo;
}
```

> **Note:** `tails[]` is NOT the LIS itself — it's a per-length tail-tracker. Reconstructing the actual LIS requires parent pointers (see LC 300 follow-up / Striver DP 42).

### 🎨 Visual — LIS as patience-sort piles

```
nums = [10, 9, 2, 5, 3, 7, 101, 18]  →  expected LIS length = 4 (e.g., 2, 3, 7, 18 OR 2, 3, 7, 101)

Patience sort builds these tails over the iteration:

  After processing each num, tails[] looks like:

  num= 10  :  tails = [10]              (start: pile of size 1)
  num=  9  :  tails = [ 9]              (9 replaces 10 — better tail of size-1)
  num=  2  :  tails = [ 2]              (2 replaces 9 — even better tail of size-1)
  num=  5  :  tails = [ 2,  5]          (5 extends — new pile of size 2)
  num=  3  :  tails = [ 2,  3]          (3 replaces 5 — better tail of size-2)
  num=  7  :  tails = [ 2,  3,  7]      (7 extends — new pile of size 3)
  num=101  :  tails = [ 2,  3,  7, 101] (101 extends — new pile of size 4)
  num= 18  :  tails = [ 2,  3,  7,  18] (18 replaces 101 — better tail of size-4)

  Final tails.length() = 4 ✓

INTERPRETATION:
   tails[k] = the SMALLEST possible TAIL of an increasing subsequence
              of length k+1 that we've seen so far.

   Replacing a bigger tail with a smaller one (e.g., 101 → 18) doesn't
   change the current LIS LENGTH but makes future extensions easier
   — smaller tails leave more room for future elements to extend.

KEY INVARIANT:
   At any point, tails[] is STRICTLY INCREASING and tails.length()
   equals the LIS length over the prefix processed so far. Binary
   search on tails[] is valid because of this monotone property.
   The actual LIS is NOT tails[] itself (tails[] mixes values from
   different subsequences) — reconstruction needs parent pointers.
```

### Variants in this family

| Variant | LC | What changes |
| --- | --- | --- |
| Print LIS | LC 300 follow-up | Same O(n²) DP + parent-pointer array; backtrack from the index achieving max `dp[i]` |
| Largest Divisible Subset | LC 368 | "Increasing" condition becomes "`nums[j]` divides `nums[i]`." Sort first; then LIS-style DP. |
| Longest String Chain | LC 1048 | "Increasing" becomes "word A is a predecessor of word B" (differ by 1 char). Sort by length first. |
| Longest Bitonic Subseq | — / GFG | LIS forward + LIS backward; combine at each pivot. Two passes. |
| Number of LIS | LC 673 | LIS + parallel `count[]` array — two DP arrays advance together. |
| Russian Doll Envelopes | LC 354 | 2D LIS: sort by width ascending, then LIS on heights (descending tie-break to forbid same-width pairs). |

> 🧩 **Try these (Family 6):**
> - ✅ **LC 300** LIS — drill BOTH formulations (O(n²) DP and O(n log n) patience)
> - ✅ **LC 368** Largest Divisible Subset — sort + LIS pattern
> - ✅ **LC 1048** Longest String Chain — sort by length + LIS-shape DP
> - 🟡 **LC 673** Number of LIS (after LC 300) — parallel count DP
> - 🟡 **Longest Bitonic Subseq** (Striver DP 46) — two-direction LIS
> - 🔴 **LC 354** Russian Doll Envelopes — defer; 2D extension

---

## 🚶 Family 7 — State Machine DP (Stocks)

> ⚠️ **TODO — Stage 1 + Stage 2 not yet added for this family.**
> Before studying the tabulation below, come back and add:
> - A concrete example (e.g., `prices = [3, 2, 6, 5, 0, 3]` for unlimited transactions) with a small tree showing the state transitions (HOLD vs NOT-HOLDING)
> - Stage 1 brute recursion: `solve(i, holding)` — `O(2^n)` time
> - Stage 2 memoization: `Integer[n][2]` table (day × holding flag) — `O(n)` time
> - Note explaining why `holding` is the key state variable (it restricts which choices are legal)
> Follow the same format as Families 3, 4, 5 above.

> **Striver videos:** DP 35-40 (Best Time to Buy and Sell Stock I-V, Cooldown, Transaction Fee)
> **Aditya Verma:** not in his playlist — Striver is the source.

### Aditya Verma's identification triplet applied to State Machine DP

| Question | Answer for Family 7 |
| --- | --- |
| **What is changing across recursive calls?** | Two things: a day index `i`, AND a **state flag** (holding stock vs not holding vs in-cooldown). State is `(i, state)`. |
| **What are the choices at each step?** | Depend on the current state. If holding → sell or hold. If not holding → buy or wait. The state machine restricts choices. |
| **What's the smallest valid input?** | `i == n` (past last day) → return 0 (no more profit). |

### Signature in 10 seconds

> **If the problem describes a sequence of decisions over time where you're in one of a SMALL number of mutually-exclusive states** (e.g., holding stock vs not, hungry vs full, locked vs unlocked) and each state restricts which transitions are legal, it's Family 7.

> **The hard part is NOT writing the code — it's drawing the state diagram.** Once the diagram is on paper, the code transcribes it mechanically.

### The canonical problem — LC 309 Best Time to Buy and Sell Stock with Cooldown

> Given `prices[i]` for day `i`, max profit from any number of transactions with the constraint: after selling, you must wait one day before buying again (cooldown).

**The three states:**

1. **HELD** — you own stock; you can SELL (→ JUST_SOLD) or HOLD (→ HELD).
2. **JUST_SOLD** — you sold today; tomorrow you can only REST.
3. **REST** — you own nothing AND are not in cooldown; you can BUY (→ HELD) or REST again (→ REST).

### 🎨 Visual — The 3-state diagram for LC 309 Stocks with Cooldown

```
                       buy stock (-prices[i])
                  ┌──────────────────────────────┐
                  │                              │
                  ▼                              │
              ┌───────┐                      ┌────────┐
              │ HELD  │                      │  REST  │ ◀──┐
              │       │                      │        │    │
              └───┬───┘                      └────────┘    │
                  │                                ▲       │
                  │ sell stock (+prices[i])        │       │ rest (do nothing)
                  ▼                                │       │
              ┌────────────┐                       │       │
              │ JUST_SOLD  │── one-day cooldown ───┘       │
              │            │   (forced REST tomorrow)      │
              └────────────┘                               │
                  ▲                                        │
                  └────────────────────────────────────────┘
                    (HELD can also self-loop: keep holding)
                    (REST can self-loop: keep waiting to buy)


Transition table:

   On day i, given state on day i-1:
   ┌────────────┬────────────────────────────────────────────┐
   │ Prev state │ Choices for today (and resulting state)    │
   ├────────────┼────────────────────────────────────────────┤
   │ HELD       │ sell  → JUST_SOLD  (profit += prices[i])   │
   │            │ hold  → HELD       (no change in profit)   │
   ├────────────┼────────────────────────────────────────────┤
   │ JUST_SOLD  │ rest  → REST       (forced — cooldown)     │
   ├────────────┼────────────────────────────────────────────┤
   │ REST       │ buy   → HELD       (profit -= prices[i])   │
   │            │ rest  → REST       (no change in profit)   │
   └────────────┴────────────────────────────────────────────┘

KEY INVARIANT:
   At each day i, you are in EXACTLY ONE state. The state diagram
   constrains which transitions are legal — that's what restricts
   the search to O(n × num_states) instead of O(2^n).
   "Max profit on day i" is independent across states, so each
   state needs its own dp variable: dp_held[i], dp_sold[i], dp_rest[i].
```

**The recurrence (in English):**

- `dp_held[i] = max(dp_held[i-1], dp_rest[i-1] - prices[i])` (hold OR buy today)
- `dp_sold[i] = dp_held[i-1] + prices[i]` (must have been holding to sell today)
- `dp_rest[i] = max(dp_rest[i-1], dp_sold[i-1])` (rest OR transition out of cooldown)
- **Base cases (day 0):** `dp_held[0] = -prices[0]` (bought today); `dp_sold[0] = 0`; `dp_rest[0] = 0`
- **Final answer:** `max(dp_sold[n-1], dp_rest[n-1])` — we end NOT holding stock.

**Stage 3 — Tabulation:**

```java
public int maxProfit(int[] prices) {
    int n = prices.length;
    if (n == 0) {
        return 0;
    }

    int[] held = new int[n];
    int[] sold = new int[n];
    int[] rest = new int[n];

    // Base cases (day 0)
    held[0] = -prices[0];
    sold[0] = 0;
    rest[0] = 0;

    for (int i = 1; i < n; i++) {
        held[i] = Math.max(held[i - 1], rest[i - 1] - prices[i]);
        sold[i] = held[i - 1] + prices[i];
        rest[i] = Math.max(rest[i - 1], sold[i - 1]);
    }

    // End not holding
    return Math.max(sold[n - 1], rest[n - 1]);
}
```

**Time:** O(n). **Space:** O(n) — Stage-4 optimizes to O(1) by keeping 3 scalars (each state needs only previous day's value).

### Variants in this family

| Variant | LC | What changes |
| --- | --- | --- |
| Single transaction | LC 121 | Only 2 states (held, not_held). Can also be solved by tracking running min — pure greedy. |
| Unlimited transactions | LC 122 | 2 states (held, not_held). Buy on every up-day greedy works too. |
| With Cooldown | LC 309 | The canonical — 3 states (HELD, JUST_SOLD, REST). |
| With Transaction Fee | LC 714 | 2 states (held, not_held); subtract `fee` on every sell. |
| At most 2 transactions | LC 123 | 4 states: not-held-0-tx, held-after-1-buy, not-held-after-1-sell, held-after-2-buys, not-held-after-2-sell — actually 5 conceptual states; compress to `(held, txCount)`. |
| At most k transactions | LC 188 | 3D DP `dp[day][txCount][holding]`. Full state machine generalization. **🔴 senior-leaning.** |

> 🧩 **Try these (Family 7):**
> - ✅ **LC 121** Best Time to Buy & Sell Stock I — drill DP first, THEN realize the greedy collapse
> - ✅ **LC 122** Best Time II — unlimited transactions, two-state DP
> - ✅ **LC 309** Cooldown — the canonical 3-state machine
> - ✅ **LC 714** With Transaction Fee — 2 states + fee subtraction
> - 🟡 **LC 123** At most 2 transactions (after LC 309) — 4 states
> - 🔴 **LC 188** At most k transactions — 3D DP; defer to senior section

---

```
═══════════════════════════════════════════════════════════════════
              ✋  END OF MEDIUM-LEVEL INTERVIEW SCOPE
═══════════════════════════════════════════════════════════════════

   You've now covered the 7 medium-scope DP families:

   ✓ Family 1 — 1D Linear DP        ✓ Family 5 — LCS / Strings DP (incl. Edit Distance)
   ✓ Family 2 — 2D Grid DP          ✓ Family 6 — Longest Increasing Subsequence
   ✓ Family 3 — 0/1 Knapsack        ✓ Family 7 — State Machine DP (Stocks)
   ✓ Family 4 — Unbounded Knapsack

   That maps to Striver DP 1-47 and Aditya Verma 1-32 — roughly
   80% of each playlist's content. This alone covers ~80% of LC
   medium DP problems and almost every SDE-2 / SDE-3 medium DP
   interview question.

   STOP HERE IF:
   - You have ≤ 3 weeks of prep time
   - The target role is SDE-2 / mid-level
   - You haven't yet solved the Tier-1 problems cold

   CONTINUE BELOW IF:
   - You're targeting L5+ / senior roles
   - The role explicitly mentions algorithmic depth (compilers,
     trading systems, advanced search)
   - You've already mastered the medium core and want depth
═══════════════════════════════════════════════════════════════════
```

> **What's below the divider** — three blocks of decreasing interview frequency:
>
> | Block | Topics | Striver | Aditya Verma | Interview reality |
> | --- | --- | --- | --- | --- |
> | 🔴 **Family 8 — Interval / Partition DP** | MCM, Burst Balloons, Palindrome Partition II, Min Cost to Cut Stick, Evaluate Boolean Expression | DP 48-54 | 33-40 | Senior L5+ |
> | 🔴 **Family 9 — DP on Trees** | House Robber III, Diameter via DP-lens, Max Path Sum, Binary Tree Cameras | (interleaved) | 46-50 | Senior L5+ |
> | 🔴 **Family 10 — Misc Hard** | Wildcard Matching, Egg Drop, Scrambled String, Boolean Parenthesization | DP 34, DP 55-56 | 39-44 | Specialized / Staff+ |

> 📍 **You've now finished the medium-scope core (Sections 1-16).** The remaining sections (Families 8-10 = senior scope, then worked walkthroughs, gotchas, practice plan, TL;DR) come in Turn 3. **Before continuing, drill the 7 family templates cold** — for each family, write the canonical problem's Stage 3 (tabulation) from memory.

---

## 17. 🔴 Family 8 — Interval / Partition DP

**Aditya Verma's identification triplet:**

1. You are given a sequence (array, string, expression) and must **split it into contiguous parts**.
2. The cost of a split depends on **what's on the left + what's on the right + the split point itself**.
3. You are asked for **minimum cost / maximum value / count of partitions**.

**Signature in 10 seconds:** "Try every split point `k` between `i` and `j`, combine left half `[i..k]` with right half `[k+1..j]`."

**Why it's senior-scope:** The state is a *range* `(i, j)`, not a single index — so the DP table is 2D and the fill order is **by interval length, not row-by-row**. Most candidates fumble the fill order on whiteboards.

### 17.1 Canonical — Matrix Chain Multiplication (MCM)

**Problem:** Given dimensions `arr[0..n]` where matrix `i` has dims `arr[i-1] × arr[i]`, find minimum scalar multiplications to compute `M1 × M2 × ... × Mn`.

**Steps in plain English:**

1. **Define state** — `dp[i][j]` = min cost to multiply matrices `i..j` (inclusive).
2. **Base case** — `dp[i][i] = 0` (single matrix needs zero multiplications).
3. **Transition** — for every split point `k` in `[i..j-1]`, the cost is `dp[i][k] + dp[k+1][j] + arr[i-1]*arr[k]*arr[j]`. Take the minimum across all `k`.
4. **Fill order** — by **interval length** `L = 2, 3, ..., n`. You MUST fill smaller intervals first because larger intervals depend on them.
5. **Answer** — `dp[1][n]`.

```java
public int matrixChainMultiplication(int[] arr) {
    int n = arr.length - 1;
    // dp[i][j] = min cost to multiply matrices i..j (1-indexed)
    int[][] dp = new int[n + 1][n + 1];

    // Length-1 intervals already zero (Java default)
    // Fill by increasing interval length L
    for (int L = 2; L <= n; L++) {
        for (int i = 1; i + L - 1 <= n; i++) {
            int j = i + L - 1;
            dp[i][j] = Integer.MAX_VALUE;
            // Try every split point k between i and j
            for (int k = i; k < j; k++) {
                int cost = dp[i][k] + dp[k + 1][j] + arr[i - 1] * arr[k] * arr[j];
                dp[i][j] = Math.min(dp[i][j], cost);
            }
        }
    }
    return dp[1][n];
}
```

### 17.2 🎨 Visual — Interval Fill Order (the senior-scope trap)

```
n = 4 matrices, dp table (1-indexed). Fill order is by INTERVAL LENGTH.

  j=  1    2    3    4
i=1 [ 0  | L2 | L3 | L4 ]   L2 fills first (length-2 intervals: [1,2], [2,3], [3,4])
i=2 [    |  0 | L2 | L3 ]   then L3 ([1,3], [2,4])
i=3 [    |    |  0 | L2 ]   finally L4 ([1,4]) — the answer
i=4 [    |    |    |  0 ]

Why this order works:
   dp[1][3] depends on dp[1][1]+dp[2][3]  and  dp[1][2]+dp[3][3]
                            ↑                       ↑
                       both are length-1       length-2 (must exist already)

   dp[1][4] depends on dp[1][k] + dp[k+1][4] for k = 1, 2, 3
                            ↑          ↑
                  length 1,2,3    length 3,2,1   (all smaller — already filled)

KEY INVARIANT:
   To compute dp[i][j], every dp[i'][j'] with (j'-i') < (j-i) must already exist.
   The only fill order that guarantees this is BY INCREASING INTERVAL LENGTH.
   Standard row-by-row or column-by-column DOES NOT WORK here.
```

> **Lesson learned the hard way (May 2026):** I once tried filling MCM row-by-row top-to-bottom and got garbage. The bug took 40 minutes to find. The fix is mechanical: **outer loop is length**, not row. Memorize this.

### 17.3 Variants table

| Problem | LeetCode | What changes | Why it's still MCM |
| --- | --- | --- | --- |
| Burst Balloons | LC 312 | Split picks the *last* balloon to burst in `[i..j]` (not the first) — neighbors are `arr[i-1]` and `arr[j+1]` at burst time | Same `(i, j, k)` interval enumeration |
| Min Cost to Cut Stick | LC 1547 | `cuts[]` is sorted; cost of a cut = current stick length | Interval = which cuts to make first |
| Palindrome Partition II | LC 132 | Cost of partition = number of cuts; precompute `isPalin[i][j]` first | Slightly different: it's *prefix* DP using palindrome lookups, but the precompute table IS interval DP |
| Boolean Parenthesization | GFG classic | Split by each operator; combine truth/false counts | `(i, j, isTrue)` triple state |
| Evaluate Boolean Expression | LC 1896 (variant) | Same as boolean parens | Same |

> 🧩 **Try these (senior scope):**
> - **LC 312 — Burst Balloons** — interval DP with the burst-last reframing trick
> - **LC 1547 — Min Cost to Cut a Stick** — straightforward MCM with cuts array
> - **LC 132 — Palindrome Partition II** — hybrid prefix + palindrome interval DP

---

## 18. 🔴 Family 9 — DP on Trees

**Aditya Verma's identification triplet:**

1. The input is a **tree** (binary tree, n-ary tree, or rooted tree from a graph).
2. Every node's answer depends on **decisions made about its children** — typically two choices: "include this node" vs "exclude this node."
3. You return **per-node state(s)** up the recursion and combine at the parent.

**Signature in 10 seconds:** "Post-order traversal returning a tuple `(includeMe, excludeMe)` from each subtree; the parent combines."

### 18.1 Canonical — House Robber III (LC 337)

**Problem:** Binary tree where each node has a non-negative value. You cannot rob two directly-connected nodes (parent + child). Return max value.

**Steps in plain English:**

1. **Define state** — for each node, return a 2-element array `[robbed, notRobbed]`:
   - `robbed` = max if we DO rob this node (children must NOT be robbed)
   - `notRobbed` = max if we DON'T rob this node (children may choose freely)
2. **Base case** — `null` node returns `[0, 0]`.
3. **Transition** —
   - `robbed = node.val + left.notRobbed + right.notRobbed`
   - `notRobbed = max(left.robbed, left.notRobbed) + max(right.robbed, right.notRobbed)`
4. **Answer** — `max(root.robbed, root.notRobbed)`.

```java
public int rob(TreeNode root) {
    int[] result = robHelper(root);
    return Math.max(result[0], result[1]);
}

// Returns [robbed, notRobbed]
private int[] robHelper(TreeNode node) {
    // Base case: null returns zeros
    if (node == null) {
        return new int[]{ 0, 0 };
    }

    // Post-order: solve children first
    int[] left = robHelper(node.left);
    int[] right = robHelper(node.right);

    // If we rob this node, children must NOT be robbed
    int robbed = node.val + left[1] + right[1];

    // If we skip this node, children pick their max independently
    int notRobbed = Math.max(left[0], left[1]) + Math.max(right[0], right[1]);

    return new int[]{ robbed, notRobbed };
}
```

### 18.2 🎨 Visual — Per-Node Tuple Propagation

```
         (3)                  Return = [rob, skip]
        /   \
      (2)   (3)               At each node:
       \      \                 rob  = val + leftSkip + rightSkip
       (3)   (1)                skip = max(L) + max(R)
                              where max(X) = max(Xrob, Xskip)

Traversal walks bottom-up (post-order):

   leaf (3) → [3, 0]      // rob=3, skip=0
   leaf (3) → [3, 0]
   leaf (1) → [1, 0]
   node (2) with leftNull, right=[3,0]:
       rob  = 2 + 0 + 0 = 2
       skip = max(0,0) + max(3,0) = 3
       returns [2, 3]
   node (3-right) with leftNull, right=[1,0]:
       rob  = 3 + 0 + 0 = 3
       skip = 0 + max(1,0) = 1
       returns [3, 1]
   ROOT (3) with left=[2,3], right=[3,1]:
       rob  = 3 + 3 + 1 = 7  ← (rob root, skip both children)
       skip = max(2,3) + max(3,1) = 3 + 3 = 6
       returns [7, 6]

ANSWER = max(7, 6) = 7

KEY INVARIANT:
   The parent ONLY needs the rob/skip pair from each child to make
   its own decision. No further subtree state propagates upward.
   This is why tree DP is O(N) total — every node visited once.
```

### 18.3 Variants table

| Problem | LeetCode | State returned per node | Combine rule |
| --- | --- | --- | --- |
| Binary Tree Max Path Sum | LC 124 | `maxGain` = best chain ending at this node | Global answer updated as `left + node + right`; return `node + max(left, right, 0)` |
| Diameter of Binary Tree | LC 543 | `height` | Global answer = `leftHeight + rightHeight`; return `1 + max` |
| Binary Tree Cameras | LC 968 | `(notCovered, coveredNoCam, coveredWithCam)` triple | Greedy/DP hybrid — place cameras at children of leaves |
| Longest Univalue Path | LC 687 | `arrowLength` | If child value matches, extend; otherwise reset |

> 🧩 **Try these (senior scope):**
> - **LC 337 — House Robber III** — the cleanest tree-DP intro
> - **LC 124 — Binary Tree Max Path Sum** — return-vs-global-update distinction (classic interview)
> - **LC 968 — Binary Tree Cameras** — triple-state tree DP, MAANG-favorite

> 🔗 **Cross-reference:** Tree traversal mechanics (pre/in/post-order) live in **`DSA/DeepDive/trees-fundamentals.md`**. This section assumes you already grok post-order recursion.

---

## 19. 🔴 Family 10 — Misc Hard (Specialized / Staff+)

These don't fit cleanly into the prior 9 families. Most appear in **company-specific question banks** (Google, Amazon Bar-Raiser, Bloomberg). For SDE-3 you can skip — but skim the *identification cues* so you can name the pattern in an interview even if you can't fully implement.

### 19.1 Egg Drop Puzzle (LC 887)

**Problem:** `k` eggs, `n` floors. Find min number of drops to identify the critical floor (worst-case).

**Why it's hard:** The DP table is 2D `(eggs, drops)` — not `(eggs, floors)` like the naive recurrence. Wrong state choice → TLE.

**Senior-scope key insight:** Flip the question. `dp[k][m]` = "max number of floors we can check with `k` eggs and `m` drops." Then return the smallest `m` such that `dp[k][m] >= n`. This converts an O(k·n²) problem into O(k·n·log n).

```java
public int superEggDrop(int k, int n) {
    // dp[k][m] = max floors checkable with k eggs and m drops
    int[][] dp = new int[k + 1][n + 1];
    int m = 0;
    while (dp[k][m] < n) {
        m++;
        for (int i = 1; i <= k; i++) {
            dp[i][m] = dp[i - 1][m - 1] + dp[i][m - 1] + 1;
        }
    }
    return m;
}
```

### 19.2 Wildcard / Regex Matching (LC 44, LC 10)

**Pattern recognition cue:** Two strings + special characters (`*`, `?`, `.`) that can match flexibly → it's a **2-string DP** (LCS-family cousin) with extra transition branches for the special chars.

**State:** `dp[i][j] = can s[0..i] match p[0..j]?`

**Transition for `*`** (matches 0+ chars):
- `dp[i][j] = dp[i][j-1]` (use `*` to match 0 chars) OR `dp[i-1][j]` (use `*` to match one more char).

> 🧩 **Try these (staff+ scope only):**
> - LC 887 — Super Egg Drop
> - LC 44 — Wildcard Matching
> - LC 10 — Regular Expression Matching
> - LC 87 — Scramble String

---

## 20. 🔬 Worked Walkthroughs

This section runs the **complete four-stage drill** on five canonical problems — one from each major family group. Treat these as **interview rehearsal scripts**: read each section out loud and time yourself to 8 minutes per problem.

### 20.1 LC 198 — House Robber (Family 1 — 1D Linear) ✅

**Restate the problem:** Pick a subset of array indices, no two adjacent, maximizing sum.

**Identify the family:** "Pick / not-pick each index; choice constrained by adjacency" → **1D Linear DP**.

**Stage 1 — Brute Recursion:**

```java
public int robBrute(int[] nums) {
    return solve(nums, 0);
}

private int solve(int[] nums, int i) {
    if (i >= nums.length) {
        return 0;
    }
    int pick = nums[i] + solve(nums, i + 2);
    int skip = solve(nums, i + 1);
    return Math.max(pick, skip);
}
```

Complexity: O(2^n) time, O(n) stack.

**Stage 2 — Memoization:**

```java
public int robMemo(int[] nums) {
    Integer[] memo = new Integer[nums.length];
    return solve(nums, 0, memo);
}

private int solve(int[] nums, int i, Integer[] memo) {
    if (i >= nums.length) {
        return 0;
    }
    if (memo[i] != null) {
        return memo[i];
    }
    int pick = nums[i] + solve(nums, i + 2, memo);
    int skip = solve(nums, i + 1, memo);
    return memo[i] = Math.max(pick, skip);
}
```

Complexity: O(n) time, O(n) memo + stack.

**Stage 3 — Tabulation:**

```java
public int robTab(int[] nums) {
    int n = nums.length;
    int[] dp = new int[n + 1];
    // dp[i] = max loot considering houses [0..i-1]
    dp[0] = 0;
    dp[1] = nums[0];
    for (int i = 2; i <= n; i++) {
        dp[i] = Math.max(dp[i - 1], nums[i - 1] + dp[i - 2]);
    }
    return dp[n];
}
```

**Stage 4 — Space optimization:**

```java
public int rob(int[] nums) {
    int prev2 = 0;
    int prev1 = 0;
    for (int x : nums) {
        int curr = Math.max(prev1, x + prev2);
        prev2 = prev1;
        prev1 = curr;
    }
    return prev1;
}
```

Complexity: O(n) time, O(1) space. ⭐ This is what you write in the interview.

---

### 20.2 LC 1143 — Longest Common Subsequence (Family 5 — 2-String DP) ✅

**Restate:** Length of longest subsequence appearing in both strings (not necessarily contiguous).

**Identify:** Two strings + subsequence query → **LCS family**.

**Stage 1 — Recursion:**

```java
private int solve(String a, String b, int i, int j) {
    if (i == 0 || j == 0) {
        return 0;
    }
    if (a.charAt(i - 1) == b.charAt(j - 1)) {
        return 1 + solve(a, b, i - 1, j - 1);
    }
    return Math.max(solve(a, b, i - 1, j), solve(a, b, i, j - 1));
}
```

**Stage 2 — Memoization:** Wrap with `Integer[][] memo = new Integer[m+1][n+1];` — covered in the family section already.

**Stage 3 — Tabulation:**

```java
public int longestCommonSubsequence(String a, String b) {
    int m = a.length();
    int n = b.length();
    int[][] dp = new int[m + 1][n + 1];
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                dp[i][j] = 1 + dp[i - 1][j - 1];
            } else {
                dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
    }
    return dp[m][n];
}
```

**Stage 4 — Space optimization (two rows):**

```java
public int longestCommonSubsequence(String a, String b) {
    int m = a.length();
    int n = b.length();
    int[] prev = new int[n + 1];
    int[] curr = new int[n + 1];
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                curr[j] = 1 + prev[j - 1];
            } else {
                curr[j] = Math.max(prev[j], curr[j - 1]);
            }
        }
        int[] tmp = prev;
        prev = curr;
        curr = tmp;
    }
    return prev[n];
}
```

Complexity: O(m·n) time, O(n) space.

> **Lesson learned the hard way (May 2026):** When space-optimizing 2D string DP, you MUST swap rows (or reset `curr` to zeros) — forgetting this leaks stale values into the next iteration and produces wrong answers that look "almost right." I lost 25 min on LC 583 to this exact bug.

---

### 20.3 LC 322 — Coin Change (Family 4 — Unbounded Knapsack) ✅

**Restate:** Min number of coins summing to `amount`. Each coin reusable infinitely. Return `-1` if impossible.

**Identify:** Reusable items + target = sum → **Unbounded Knapsack**.

**Stage 3 — Tabulation (skipping straight to the interview-ready stage):**

```java
public int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);  // sentinel = impossible
    dp[0] = 0;
    for (int a = 1; a <= amount; a++) {
        for (int coin : coins) {
            if (coin <= a) {
                dp[a] = Math.min(dp[a], 1 + dp[a - coin]);
            }
        }
    }
    return dp[amount] > amount ? -1 : dp[amount];
}
```

Complexity: O(amount · coins.length) time, O(amount) space.

**Senior fingerprint:** Notice the loop is `for a in 1..amount, for coin in coins` — both loops orderable either way since we're counting **min coins, not number of combinations**. Compare with LC 518 / LC 377 where loop order DOES matter (covered in the family section).

---

### 20.4 LC 72 — Edit Distance (Family 5 — 2-String DP) ✅

**Restate:** Min insert/delete/replace operations to convert `word1` → `word2`.

**Identify:** Two strings + transformation operations → **LCS-family / 2-string DP**.

**Stage 3 — Tabulation:**

```java
public int minDistance(String a, String b) {
    int m = a.length();
    int n = b.length();
    int[][] dp = new int[m + 1][n + 1];
    // dp[i][j] = min ops to convert a[0..i-1] → b[0..j-1]
    for (int i = 0; i <= m; i++) {
        dp[i][0] = i;  // delete i chars
    }
    for (int j = 0; j <= n; j++) {
        dp[0][j] = j;  // insert j chars
    }
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                dp[i][j] = dp[i - 1][j - 1];
            } else {
                // insert        delete         replace
                int op = Math.min(dp[i][j - 1], Math.min(dp[i - 1][j], dp[i - 1][j - 1]));
                dp[i][j] = 1 + op;
            }
        }
    }
    return dp[m][n];
}
```

Complexity: O(m·n) time, O(m·n) space (can be reduced to O(n) by the row-swap trick).

**Why this problem is interview gold:** It's literally the "fancy LCS." Once you see edit distance, you've seen the whole 2-string family. If asked Longest Palindromic Subsequence next, you say *"that's LCS of S and reverse(S)"* and you're done.

---

### 20.5 LC 309 — Best Time to Buy/Sell with Cooldown (Family 7 — State Machine) ✅

**Restate:** Daily prices array. Unlimited transactions. Must cooldown 1 day after sell. Max profit.

**Identify:** Sequence + multi-state finite automaton → **State Machine DP**.

**Stage 3 — Tabulation (3 states):**

```java
public int maxProfit(int[] prices) {
    int n = prices.length;
    if (n == 0) {
        return 0;
    }
    int[] held = new int[n];
    int[] sold = new int[n];
    int[] rest = new int[n];
    held[0] = -prices[0];
    sold[0] = 0;
    rest[0] = 0;
    for (int i = 1; i < n; i++) {
        held[i] = Math.max(held[i - 1], rest[i - 1] - prices[i]);
        sold[i] = held[i - 1] + prices[i];
        rest[i] = Math.max(rest[i - 1], sold[i - 1]);
    }
    return Math.max(sold[n - 1], rest[n - 1]);
}
```

**Stage 4 — Space optimization:**

```java
public int maxProfit(int[] prices) {
    int held = Integer.MIN_VALUE;
    int sold = 0;
    int rest = 0;
    for (int p : prices) {
        int prevSold = sold;
        sold = held + p;
        held = Math.max(held, rest - p);
        rest = Math.max(rest, prevSold);
    }
    return Math.max(sold, rest);
}
```

Complexity: O(n) time, O(1) space.

> **Lesson learned the hard way (May 2026):** The order of updates in the space-optimized version matters. You MUST capture `prevSold` before overwriting `sold`, or else `rest`'s update sees the new `sold` instead of yesterday's. This bug only shows up on inputs of length ≥ 3 — easy to miss in casual testing.

---

## 21. ⚠️ Gotchas — Silent Bug Hall of Fame

These are bugs that **compile cleanly and pass small test cases** but break on edge cases. Every one of them cost me real time during practice.

### 21.1 Forgetting the `Integer[]` vs `int[]` distinction in memoization 🐞

```java
// ❌ BUG — can't distinguish "uncomputed" from "value=0"
int[] memo = new int[n];

// ✅ FIX — null means uncomputed, autoboxes when assigned
Integer[] memo = new Integer[n];
if (memo[i] != null) { return memo[i]; }
```

**When it bites:** Any DP where 0 is a valid answer (counting problems, certain min-cost problems). The bug silently returns 0 for unvisited states.

### 21.2 0/1 Knapsack with the forward loop 🐞

```java
// ❌ BUG — items get reused (this is unbounded knapsack!)
for (int item = 0; item < n; item++) {
    for (int w = 0; w <= W; w++) {
        dp[w] = Math.max(dp[w], dp[w - wt[item]] + val[item]);
    }
}

// ✅ FIX — backward inner loop ensures each item used at most once
for (int item = 0; item < n; item++) {
    for (int w = W; w >= wt[item]; w--) {
        dp[w] = Math.max(dp[w], dp[w - wt[item]] + val[item]);
    }
}
```

**When it bites:** Subset Sum, Partition Equal Subset, Target Sum — all 0/1 knapsacks. Forward loop reuses the item and inflates answers.

### 21.3 Combination vs Permutation in Unbounded Knapsack 🐞

```java
// LC 518 — combinations (target = 4, coins = {1,2}: answers 1+1+1+1, 1+1+2, 2+2 = 3)
for (int coin : coins) {
    for (int a = coin; a <= amount; a++) {
        dp[a] += dp[a - coin];
    }
}

// LC 377 — permutations (target = 4, nums = {1,2}: 1+1+1+1, 1+1+2, 1+2+1, 2+1+1, 2+2 = 5)
for (int a = 1; a <= target; a++) {
    for (int num : nums) {
        if (num <= a) { dp[a] += dp[a - num]; }
    }
}
```

**When it bites:** The same arithmetic, the same recurrence — only the loop nesting differs. Swap them and your "Coin Change 2" returns "Combination Sum IV" answers.

### 21.4 Integer overflow in DP transitions 🐞

```java
// ❌ BUG — Integer.MAX_VALUE + anything wraps to negative
dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1);
// If either dp value is Integer.MAX_VALUE (sentinel), +1 wraps.

// ✅ FIX — use amount+1 or n+1 as the "impossible" sentinel
Arrays.fill(dp, amount + 1);
// OR guard before adding:
if (dp[i - 1][j] != Integer.MAX_VALUE) { ... }
```

**Cross-reference:** Full overflow rules in **`DSA/DeepDive/integer-overflow-and-limits.md`**.

### 21.5 Wrong base case for LIS-style DP 🐞

```java
// ❌ BUG — dp[i] = 0 means "no element selected" but for LIS every element is its own len-1 subseq
int[] dp = new int[n];

// ✅ FIX — initialize each cell to 1
Arrays.fill(dp, 1);
```

### 21.6 Interval DP filled row-by-row 🐞

Already covered in §17.2 — the fix is **outer loop = interval length**, not row.

### 21.7 Missing the "skip" case in tree DP 🐞

```java
// ❌ BUG — only computes "include this node" path
int rob = node.val + leftSkip + rightSkip;
return rob;

// ✅ FIX — always return BOTH options
return new int[]{ robThis, skipThis };
```

**When it bites:** Tree DP where you must propagate two states (include/exclude) up to the parent. Returning only one breaks the parent's max calculation.

### 21.8 Stack overflow on recursion-only DP 🐞

```java
// ❌ For 10^5-sized inputs, deep recursion blows the JVM stack (default ~512KB)
return solve(0, memo);

// ✅ FIX — convert to tabulation, OR increase stack with -Xss64m, OR use explicit stack
```

**Interview signal:** If the problem says `n <= 10^5`, ALWAYS prefer tabulation. Memoized recursion is fine for n <= ~10^4.

### 21.9 Bitmask DP — using `1 << n` for large n 🐞

When n > 20, `1 << n` overflows `int`. Use `1L << n` and `long` masks throughout. Bitmask DP becomes impractical past n=22 anyway (4M states each touching n transitions).

### 21.10 Re-allocating memo arrays inside recursion 🐞

```java
// ❌ BUG — allocates a fresh memo on every call, memoization is no-op
private int solve(int i) {
    Integer[] memo = new Integer[n];  // WRONG SCOPE
    if (memo[i] != null) { return memo[i]; }
    ...
}

// ✅ FIX — declare memo once at the entry point, pass it down
public int run(int[] nums) {
    Integer[] memo = new Integer[nums.length];
    return solve(0, nums, memo);
}
```

---

## 22. 🗺️ Practice Plan

The 17-day plan slots DP into **Days 5-14** (10 days). Sequence problems strictly tier-by-tier — don't jump ahead until the prior tier is solved cold (Stage 3 from memory in under 12 min).

### Tier 0 — Foundations (Day 5, ~3 hours)

Drill the four-stage drill on Fibonacci until you can write Stage 1 → 4 without looking. Then **House Robber (LC 198)** all four stages.

### Tier 1 — Medium core (Days 6-11, ~6 days)

⭐ **Must solve all 12 before moving on.** This list IS the medium-level interview scope.

| # | LeetCode | Family | Why |
| --- | --- | --- | --- |
| 1 | LC 198 — House Robber | 1D Linear | Canonical lookback |
| 2 | LC 213 — House Robber II | 1D Linear | Circular variant — split into two passes |
| 3 | LC 62 — Unique Paths | 2D Grid | Canonical grid DP |
| 4 | LC 64 — Min Path Sum | 2D Grid | Same grid + value accumulation |
| 5 | LC 416 — Partition Equal Subset Sum | 0/1 Knapsack | Subset Sum reduction |
| 6 | LC 494 — Target Sum | 0/1 Knapsack | Sign-assignment → Subset Sum reduction |
| 7 | LC 322 — Coin Change (min) | Unbounded Knapsack | Min-coins formulation |
| 8 | LC 518 — Coin Change II (count) | Unbounded Knapsack | Combination vs permutation trap |
| 9 | LC 1143 — Longest Common Subsequence | 2-String DP | LCS canonical |
| 10 | LC 72 — Edit Distance | 2-String DP | Three-way transition |
| 11 | LC 300 — Longest Increasing Subseq | LIS | Both O(n²) and O(n log n) |
| 12 | LC 309 — Stocks w/ Cooldown | State Machine | 3-state automaton |

### Tier 2 — Medium polish (Days 12-13, ~2 days)

Pick **8 more** from this list for mock-interview-style cold solves. Time yourself to 25 min each.

| LeetCode | Family |
| --- | --- |
| LC 91 — Decode Ways | 1D Linear |
| LC 152 — Max Product Subarray | 1D Linear (track min + max) |
| LC 139 — Word Break | 1D + dictionary |
| LC 5 / LC 647 — Longest Palindromic Substring/Count | Interval / 2D string |
| LC 516 — Longest Palindromic Subseq | LCS variant |
| LC 583 — Delete Operation for Two Strings | LCS variant |
| LC 188 / LC 123 — Best Time to Buy/Sell Stock III/IV | State Machine k transactions |
| LC 377 — Combination Sum IV | Permutation knapsack |
| LC 120 — Triangle | Grid DP |
| LC 221 — Maximal Square | 2D Grid with min-of-three transition |
| LC 53 — Maximum Subarray (Kadane) | 1D — the simplest DP, but reframe via DP lens |

### Tier 3 — Senior (Day 14, optional, ~1 day)

Skip if SDE-2 / mid. Solve 3-5 if SDE-3 / L5+.

| LeetCode | Family |
| --- | --- |
| LC 312 — Burst Balloons | Interval DP |
| LC 132 — Palindrome Partition II | Interval + prefix |
| LC 337 — House Robber III | Tree DP |
| LC 124 — Binary Tree Max Path Sum | Tree DP |
| LC 968 — Binary Tree Cameras | Tree DP (3-state) |
| LC 44 — Wildcard Matching | 2-string DP + wildcards |
| LC 10 — Regex Matching | 2-string DP + wildcards |
| LC 887 — Super Egg Drop | Specialized DP |

### Daily routine (Days 6-14)

1. **Warm-up (15 min)** — re-derive yesterday's canonical from memory.
2. **New problem (45 min)** — pick from the current tier. Run all four stages.
3. **Pattern reflection (10 min)** — write a 3-line note: *which family, which signature cue spotted it, which gotcha bit me.*

If a problem takes > 90 min, stop. Read the editorial, re-derive Stage 3 the next morning. Don't grind.

---

## 23. 🧾 TL;DR — One-Page Summary

| What | Cheat |
| --- | --- |
| **DP test** | Overlapping subproblems + optimal substructure |
| **Four stages** | Recursion → Memoization → Tabulation → Space-opt |
| **Memo type** | `Integer[]` not `int[]` (null = uncomputed) |
| **Loop order — 0/1 knapsack** | Outer = item, inner = weight DESCENDING |
| **Loop order — unbounded knapsack** | Outer = item, inner = weight ASCENDING |
| **Loop order — count combinations** (LC 518) | Outer = coin |
| **Loop order — count permutations** (LC 377) | Outer = amount |
| **2D grid DP** | Top-left to bottom-right, `dp[i][j] = f(dp[i-1][j], dp[i][j-1], ...)` |
| **2-string DP (LCS family)** | `dp[i][j]` on prefixes; diagonal on match, max-of-neighbors on mismatch |
| **LIS** | Patience sort = O(n log n); `Arrays.fill(dp, 1)` for O(n²) |
| **Interval DP fill order** | By interval LENGTH (NOT row-by-row) |
| **Tree DP** | Post-order, return per-node tuple |
| **State machine DP** | Enumerate states, write transitions, init `held = -prices[0]` |
| **Overflow** | Use `amount + 1` or `n + 1` as sentinel, never `Integer.MAX_VALUE + 1` |
| **Stack safety** | Tabulation for n > 10^4 |

### The 7 medium families (memorize):

1. **1D Linear** — House Robber
2. **2D Grid** — Unique Paths
3. **0/1 Knapsack** — Subset Sum / Partition Equal Subset
4. **Unbounded Knapsack** — Coin Change
5. **LCS / 2-String** — LCS, Edit Distance
6. **LIS** — Longest Increasing Subseq
7. **State Machine** — Stocks family

### The 5-signal DP checklist:

1. "Find max / min / count of ways" — typical optimization vocabulary
2. Decisions made *step by step* with constrained choices
3. Same subproblem recomputed if you'd brute-force recurse
4. Subset / subsequence / subarray / substring keywords
5. Choices feel like *include-this vs skip-this* or *pick-this-option-k*

If 3+ signals fire → it's DP. Open with brute recursion, identify the family, move through the four stages.

---

### 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Combined Striver's four-stage drill (recursion → memo → tab → space-opt) with Aditya Verma's pattern-identification taxonomy (7 medium + 3 senior families). Medium-scope cutoff banner mirrors `graphs-fundamentals.md`. |
| May 2026 | Added 5 worked walkthroughs (LC 198, LC 1143, LC 322, LC 72, LC 309) with all four stages each. |
| May 2026 | Silent Bug Hall of Fame (§21) compiled from real practice mistakes. |
| May 2026 | **Three Rookie Mistakes section added** after Style Habits. State vs Result confusion (carrying sum as parameter), forward vs backward direction (commit to 0→n), and calling convention intuition (read starting condition from problem). Triggered by real mistakes on LC 198 and LC 746. |

### 🔗 Companion files

- **`DSA/Reference/dp-reference.md`** — compact cheatsheet (TODO — create from §23 TL;DR + family signatures)
- **`DSA/DeepDive/recursion-fundamentals.md`** — recursion mental model (prereq for Stage 1)
- **`DSA/DeepDive/graphs-fundamentals.md`** — graph traversal (DP on DAGs is the bridge between the two)
- **`DSA/DeepDive/trees-fundamentals.md`** — post-order traversal (prereq for §18 Tree DP)
- **`DSA/DeepDive/integer-overflow-and-limits.md`** — sentinel & overflow rules referenced in §21.4
- **`DSA/interview-prep-17-day-plan.md`** — Days 5-14 driver

<!-- TURN-3 END: Sections 17-23 (Families 8-10 + walkthroughs + gotchas + practice plan + TL;DR + changelog + companion links). File complete. -->
