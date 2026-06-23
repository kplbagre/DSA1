# Interview Playbooks — Folder Standards

> **For any AI assistant working in this folder:** Read the parent chain first, then this file. All four apply:
> 1. `../../../AGENTS.md` — universal rules (code formatting, emoji palette, cross-references)
> 2. `../../AGENTS.md` — DSA-specific rules (Java conventions, difficulty tags, LC numbering)
> 3. `../notes-standards-interview.md` — Interview Playbook structure (document sections, speed drill format, lambda conventions)
> 4. **THIS FILE** — Playbooks-specific format rules (pattern block, problem bank entry, brute force / key insight requirement)

---

## 🔧 Work in Progress — Brute Force / Key Insight Pass

> **For any AI assistant resuming this task:** A format upgrade is in progress across all 13 playbook files. The goal is to add `**What this solves**`, `**Brute force**`, `**Key insight**` to every pattern block, and `> **Brute force**`, `> **Key insight**`, `**Complexity (optimal)**` to every problem bank entry. The exact format is defined in the sections below. Do NOT start this work without first reading the full format spec in this file.

### Status

| File | Patterns done | Problem bank done |
| --- | --- | --- |
| `binary-search.md` | ✅ All 5 | ✅ All 14 |
| `heaps.md` | ✅ All 5 | ✅ All 13 |
| `arrays-and-hashing.md` | ❌ | ❌ |
| `two-pointers-and-sliding-window.md` | ❌ | ❌ |
| `trees-and-bfs-dfs.md` | ❌ | ❌ |
| `graphs.md` | ✅ All 5 | ✅ All 10 |
| `backtracking.md` | ❌ | ❌ |
| `intervals.md` | ❌ | ❌ |
| `dp.md` | ✅ All 5 | ✅ All 10 |
| `greedy.md` | ❌ | ❌ |
| `linked-list.md` | ❌ | ❌ |
| `stacks-and-queues.md` | ❌ | ❌ |
| `strings.md` | ❌ | ❌ |

### How to continue

1. Pick the next ❌ file from the table above.
2. Read this file fully (the format spec below) before touching anything.
3. For each `## 🧭 Pattern N:` section — add `**What this solves**` before recognition cues, `**Brute force**` + `**Key insight**` after recognition cues, `**Complexity (optimal):**` after the code block (before `**🏷️ Problems:**`).
4. For each problem bank entry (`### LC XXX:`) — add `> **Brute force:**` + `> **Key insight:**` before `> **Approach:**`; add `**Complexity (optimal):**` after the code block.
5. Update the table above to ✅ when done.
6. Add a changelog entry to the file with date.

---

## 🎯 What This Folder Contains

One `.md` file per DSA topic. Each file is a **pattern-recognition playbook** — not a tutorial, not a cheatsheet. The reader opens it 1-2 days before an interview to calibrate "which pattern do I reach for when I see X?"

Current files: `arrays-and-hashing.md`, `binary-search.md`, `two-pointers-and-sliding-window.md`, `trees-and-bfs-dfs.md`, `graphs.md`, `heaps.md`, `backtracking.md`, `intervals.md`, `dp.md`, `greedy.md`, `linked-list.md`, `stacks-and-queues.md`, `strings.md`

---

## 🧭 Pattern Block Format — Mandatory Structure

Every `## 🧭 Pattern N: Name` section MUST follow this order. No deviations.

```markdown
## 🧭 Pattern N: Name ⭐ (star only if top-3 most common)

**What this solves:** 2-3 plain-English sentences describing the class of problem.
No jargon. A reader who hasn't seen this pattern yet should understand what
kind of situation calls for it.

**Recognition cues — reach for this when:**
- [trigger word or constraint from problem statement]
- [another trigger]

**Brute force:** 1-3 sentences describing the naive approach + its complexity.
O(?) time, O(?) space. Serves as the mental baseline — "what would we do if we
didn't know this pattern?"

**Key insight:** 1-2 sentences — the aha moment that bridges brute force to
the optimal approach. This is what you'd say out loud in an interview when the
interviewer asks "why does this work?"

**Steps in plain English:**

1. **Step name** — what and why.
2. **Step name** — what and why.

```java
// Template code with step-matching comments
```

**Complexity (optimal):** O(?) time, O(?) space [— one-line explanation of what drives the log/n factor if non-obvious]

**🏷️ Problems:** LC X (Name), LC Y (Name), LC Z (Name).
```

### Why this order matters

| Section | Purpose in an interview |
| --- | --- |
| **What this solves** | Lets you confirm you're reading the right pattern before committing |
| **Recognition cues** | The 5-second pattern-identification reflex |
| **Brute force** | Interviewer WILL ask: "what's the naive approach?" — have it ready |
| **Key insight** | Verbal explanation of WHY the optimization works — separates strong from weak candidates |
| **Steps** | Translates insight into executable algorithm |
| **Code** | The artifact |
| **Complexity (optimal)** | Placed AFTER code because that's when you state it in an interview |

---

## ⚡ Problem Bank Entry Format — Mandatory Structure

Every problem in the `## ⚡ Problem Bank` section MUST follow this order:

```markdown
### LC XXX: Problem Name

> **Problem:** 2-3 sentence plain-English description with a concrete example.
> The reader must understand the problem WITHOUT opening LeetCode.

> **Brute force:** 1 sentence naive approach. O(?) time, O(?) space.
> **Key insight:** 1 sentence — the aha that drives the optimal approach.
> **Approach:** 1-2 sentences connecting to the pattern + naming the key twist.

```java
// 4-6 lines of critical code — the "aha" part, not the full solution
```

**Complexity (optimal):** O(?) time, O(?) space
```

### What NOT to do

❌ Old format (banned — interviewers ask brute force, this gives them nothing):
```markdown
> **Approach:** Pattern 1 — do X, Y, Z.
> **Complexity:** Brute O(n) · Optimal **O(log n)** time, O(1) space
```

✅ New format (above) — brute force and key insight are first-class, complexity is after code.

---

## 🔢 Brute Force Rule — Non-Negotiable

**Every pattern block AND every problem bank entry must have an explicit brute force.**

Reason: Salesforce R1 (June 2026) — Kapil knew the pattern and wrote the optimal code but
couldn't verbalize the naive approach when asked. The interviewer interpreted this as
pattern-memorization without understanding. This rule exists to prevent that.

**Brute force quality bar:**
- 1-3 sentences maximum
- Must state complexity: `O(?) time, O(?) space`
- Must describe WHAT you do, not just "linear scan" — e.g., "try every pair i, j and check if their sum equals target"
- Must be the approach that works WITHOUT the key data structure or insight

---

## 🔑 Key Insight Rule — Non-Negotiable

**Every pattern block AND every problem bank entry must have an explicit key insight.**

**Key insight quality bar:**
- 1-2 sentences maximum
- Must explain WHY the optimal approach works, not just WHAT it does
- The test: could you say this sentence out loud to an interviewer in under 10 seconds?
- Good: "Sorted order means one comparison eliminates half the search space — that's the log factor."
- Bad: "We use binary search because the array is sorted." (describes what, not why)

---

## ✅ Playbooks Quality Checklist

Run this BEFORE delivering any edit to a playbook file. This extends (not replaces) the checklists in the parent AGENTS files.

**Pattern blocks:**
- [ ] Has `**What this solves:**` (2-3 sentences, no jargon)
- [ ] Has `**Recognition cues:**` (2-4 trigger words/constraints)
- [ ] Has `**Brute force:**` with explicit O(?) complexity
- [ ] Has `**Key insight:**` (1-2 sentences, explains WHY)
- [ ] Has `**Steps in plain English:**` before code
- [ ] Has `**Complexity (optimal):**` AFTER code (not before, not inside the approach section)
- [ ] No duplicate key insight blocks (old `**The key insight:**` paragraph removed if new block was added)

**Problem bank entries:**
- [ ] Has `> **Problem:**` with concrete example (no LeetCode required to understand)
- [ ] Has `> **Brute force:**` with O(?) complexity
- [ ] Has `> **Key insight:**`
- [ ] Has `> **Approach:**`
- [ ] Has `**Complexity (optimal):**` AFTER the code block
- [ ] No old `> **Complexity:** Brute O(n) · ...` inline format

**File-level:**
- [ ] All 5 pattern sections follow the mandatory order
- [ ] All problem bank entries follow the mandatory order
- [ ] Changelog entry added for the current edit date
- [ ] Line count reasonable: 400-700 lines (binary-search.md ~865 lines due to expanded bank — acceptable)

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **AGENTS.md created.** Locks the pattern block and problem bank entry formats agreed on after the Salesforce R1 experience. Main additions: mandatory `**What this solves**`, `**Brute force**`, `**Key insight**` before code; mandatory `**Complexity (optimal)**` after code. Applied first to `binary-search.md` (all 5 patterns + 14 problem bank entries). Format to be applied to all 13 playbooks. |
