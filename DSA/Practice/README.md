# DSA Practice — 25-Minute Drill

> **The Salesforce lesson:** You knew the pattern. You couldn't produce the code under pressure. This folder fixes that.

---

## 🎯 The Rules

1. **Pick one file.** Open it. Read the problem.
2. **Start your timer.** 25 minutes. No extensions.
3. **Close all notes** — no playbooks, no reference files, no browser.
4. **Follow the 5-Minute Wall** (see below) before writing a single line of code.
5. **At 25 minutes:** paste whatever you have — complete or not. Don't keep going.

---

## 🧱 The 5-Minute Wall — Non-Negotiable

```
MINUTE 1 — CLARIFY
  Repeat the problem in your own words.
  Ask yourself: duplicates? negative numbers? sorted? return indices or values?

MINUTE 2 — BRUTE FORCE (say it out loud or write it in comments)
  State the brute force + its complexity. This is your safety net.

MINUTE 3 — OPTIMIZE
  "I see [trigger word] → this is [pattern name] → approach is [X]."

MINUTE 4 — TRACE A SMALL EXAMPLE BY HAND
  Walk through 3-4 elements step by step IN COMMENTS before coding.
  This catches: pre-load bug, off-by-one, wrong data structure choice.

MINUTE 5 — NOW CODE
  Algorithm is already traced. Code writes itself.
```

---

## ⚡ 30-Second Pre-Submit Checklist

Before pasting your solution, scan these:

```
HASHMAP / PAIR-FINDING:
  □ Check map/set FIRST, then add current element (NEVER pre-load)

GRID:
  □ i < row, j < col (not j < row)
  □ BFS: mark visited ON enqueue, not after poll

TREE / DFS:
  □ max = Math.max(max, child) inside loop — track best across ALL branches
  □ result.add(new ArrayList<>(path)) — snapshot, not reference

DP:
  □ Array size: dp[n] needed? → size = n + 1
  □ Min problem: Arrays.fill(dp, Integer.MAX_VALUE), then dp[0] = 0
  □ Memo check FIRST before computing
```

---

## 📊 Scoring

| Result | Score | Action |
| --- | --- | --- |
| Solved clean in < 20 min | ✅ Locked | Move to next problem |
| Solved but > 20 min or had bugs | ⚠️ Needs reps | Redo this problem in 3 days |
| Hit 25 min, submitted incomplete | ❌ Gap found | Review with Wibey, then complete |

---

## 📁 Folder Structure

```
Practice/
├── README.md              ← YOU ARE HERE
├── LC001_TwoSum.java      ← blank starter (always reset after review)
├── LC104_MaxDepth.java
├── ...
└── Solved/
    └── LC001_TwoSum_solved.java   ← your reviewed solution (1 per problem, overwritten each attempt)
```

---

## 📚 Reading Plan — Before Drilling

### Pass 1 — Untouched (do first)
1. `arrays-and-hashing.md`
2. `binary-search.md`
3. `heaps.md`
4. `backtracking.md`

### Pass 2 — Lightly touched (do second)
1. `linked-list.md`
2. `two-pointers-and-sliding-window.md`
3. `stacks-and-queues.md`

### Pass 3 — Recently read (quick refresh before interview)
Remaining files: `trees-and-bfs-dfs`, `graphs`, `dp`, `strings`, `intervals`, `greedy`

**After Pass 1 → resume practice drills here.**

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **Folder created.** Post-Salesforce SMTS R1. 25-Minute Drill protocol. Blind 75 problem set, filtered for highest interview ROI. |
