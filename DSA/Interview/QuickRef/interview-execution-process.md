# Interview Execution Process — The 5-Minute Wall

> **Why this file exists:** Salesforce SMTS R1 (June 2026). Knew the pattern, derived the math, recognized it's a pair-finding problem — but jumped straight to coding, pre-loaded a HashSet (Bug 16), and couldn't produce working code. The problem was Medium-level. **The gap wasn't knowledge — it was execution under pressure.**

---

## 🎯 The Core Problem

```
Practicing ≠ Performing

Reading patterns, understanding solutions, building mental models
  → trains the RECOGNIZER (which pattern?)

Writing code from scratch, under time pressure, with no notes
  → trains the EXECUTOR (can you produce it?)

You trained the recognizer. The executor was undertrained.
```

---

## 🧠 The 5-Minute Wall — Do This EVERY Time

**Never write code before minute 5.** This is non-negotiable.

```
MINUTE 1 — CLARIFY
  "Let me make sure I understand..."
  Repeat the problem in your own words.
  Ask 2-3 questions:
    □ Input size? (affects time complexity choice)
    □ Duplicates allowed?
    □ Can a number pair with itself?
    □ Return indices or values?
    □ What if no solution exists?

MINUTE 2 — BRUTE FORCE (say it out loud)
  "The brute force would be..."
  State it, state its complexity.
  This shows the interviewer you can analyze, AND it's your safety net.
  Example: "Check every pair (i,j), test if sum == product. O(n²)."

MINUTE 3 — OPTIMIZE (say the pattern out loud)
  "I notice [trigger word] — this is [pattern name]."
  Derive the key insight.
  Example: "a + b = ab → a = b/(b-1). So for each b,
            I just need to look up the complement. One-pass HashSet."

MINUTE 4 — TRACE A SMALL EXAMPLE (this catches bugs)
  Pick 3-4 elements. Walk through your algorithm BY HAND.
  Say each step out loud:
    "b=2.0 → complement = 2.0 → seen = {} → not found → add 2.0"
    "b=3.0 → complement = 1.5 → not in seen → add 3.0"
    "b=1.5 → complement = 3.0 → YES in seen → pair found!"
  
  THIS STEP catches:
    - Pre-load bug (you'll see "wait, when do I add to the set?")
    - Off-by-one errors
    - Missing edge cases (b=1 → division by zero)
    - Wrong data structure choice

MINUTE 5 — NOW CODE
  "I'll start coding now."
  You've already traced the algorithm — the code writes itself.
```

---

## ⚠️ What Goes Wrong When You Skip the Wall

```
Skip minute 1 (clarify)
  → You solve the wrong problem. Or miss "can a number pair with itself?"

Skip minute 2 (brute force)
  → You freeze trying to find the optimal. Brute force IS your safety net.

Skip minute 3 (say pattern out loud)
  → You code without a plan. The code meanders.

Skip minute 4 (trace example)     ← THIS IS THE ONE YOU SKIPPED
  → You write structurally correct code with subtle bugs.
  → Pre-load bug, off-by-one, wrong comparison — all catchable by tracing.

Skip minute 5 (just jump to code)
  → Everything above. This is what happened at Salesforce.
```

---

## 🔧 The "Check-Then-Add" Discipline

For ANY problem that says "find a pair / find two elements / find complement":

```
WRONG mental model:  "I need fast lookup → put everything in a Set → scan"
RIGHT mental model:  "I need to check PREVIOUSLY SEEN → check first → add after"

The pattern:
  Set<X> seen = new HashSet<>();
  for (X current : array) {
      X complement = computeComplement(current);
      if (seen.contains(complement)) {
          // found pair: (complement, current)
      }
      seen.add(current);  // add AFTER checking
  }
```

This applies to: Two Sum, 3Sum inner loop, Subarray Sum, any pair-finding problem.

---

## 🏋️ How to Train the Executor

**The 25-Minute Drill (do 2-3 per day):**

```
1. Pick a problem you "know" (solved before, recognize the pattern)
2. Close ALL notes, all browser tabs
3. Open blank editor. Start 25-minute timer
4. Follow the 5-Minute Wall → code → test with edge cases
5. Score yourself:
   ✅ Solved clean in <20 min                → pattern is locked
   ⚠️ Solved but needed >20 min or had bugs → needs 2 more reps
   ❌ Couldn't finish or got stuck            → re-read pattern, then do this problem again tomorrow
```

**The key insight:** You don't need to learn more patterns. You need to **convert pattern recognition into code production under pressure.** That only happens by writing code with no safety net.

**Recommended ratio for interview prep:**

```
BEFORE (what you did):     90% reading → 10% writing → ❌ failed execution
TARGET (what works):       40% reading → 60% writing → ✅ execution ready
```

---

## 🧩 Pre-Interview Warm-Up (30 min before)

```
1. Solve ONE easy problem cold (LC 1 Two Sum or LC 121 Best Time to Buy Stock)
   — Gets your fingers typing and brain in "produce code" mode

2. Read the 30-second pre-submit checklist (common-bugs-checklist.md)
   — Primes your bug radar

3. Read THIS file's 5-Minute Wall section
   — Primes the process

4. STOP. Don't cram. A calm mind codes better than a crammed one.
```

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Bug 16 — HashMap pre-load | `DSA/Interview/QuickRef/common-bugs-checklist.md` — HashMap/HashSet Bugs |
| 30-second pre-submit checklist | `DSA/Interview/QuickRef/common-bugs-checklist.md` — bottom section |
| Pattern recognition crash courses | `DSA/Interview/QuickRef/graph-dp-crash-course.md`, `DSA/Interview/QuickRef/remaining-topics-crash-course.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** Written after Salesforce SMTS R1 failure. Root cause: jumped to coding without tracing, pre-loaded HashSet (Bug 16). Process: 5-Minute Wall, Check-Then-Add discipline, 25-Minute Drill for executor training. |
