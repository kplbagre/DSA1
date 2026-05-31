# Interview Playbook Notes — Standards

> Conventions for writing interview-focused pattern-recognition files in this `Interview/` folder. Read this **after** the root `../../AGENTS.md` for universal rules and `../AGENTS.md` for DSA-specific rules.

---

## 🎯 Purpose

An Interview Playbook note bridges the gap between **knowing a data structure** (Reference files) and **recognizing which pattern to use on a new problem** (what interviews test). It must:

1. **Teach pattern recognition** — "when I see X in the problem, I reach for Y"
2. **Show the thinking process** — not just the code, but the *reasoning* that leads to the code
3. **Be self-contained per topic** — read one file, drill it, walk into the interview ready on that topic
4. **Be revision-friendly** — scannable in 1-1.5 hours, not a 3-hour textbook

**What this is NOT:**
- Not a DeepDive (doesn't teach the data structure from scratch)
- Not a Reference (doesn't list method signatures)
- Not a Pattern file (doesn't deep-dive one specific problem)

**The gap it fills:** Reference says *"use `prefix[r+1] - prefix[l]` for range sums."* Interview Playbook says *"if the problem asks 'how many subarrays have sum = K' — that's a prefix sum + HashMap problem, because two prefixes differing by K means the subarray between them sums to K."*

**Length target:** 400–600 lines. Less than 400 → probably missing the canonical walkthrough. More than 600 → trim the problem bank or split the topic.

---

## 📐 Document Structure (Section Order)

Every Interview Playbook note follows this template:

```
1.  H1 Title — Interview Playbook                        [REQUIRED]
2.  Tagline blockquote (what + who + when to read)        [REQUIRED]
3.  🎯 Why You're Reading This                           [REQUIRED]
4.  🧠 The Mental Model — Pattern Recognition Cues        [REQUIRED]
       └── Decision tree or "3 questions" framework
       └── Must include ≥1 🎨 Visual if topic is spatial
5.  🧭 Pattern N: [Name]  (repeat for each pattern)      [REQUIRED, 3-6 patterns]
       └── Recognition cues (trigger words/constraints)
       └── English steps + template code
       └── 🏷️ Problems that use this pattern
6.  🔬 Canonical Problem — Full Thinking Walkthrough      [REQUIRED]
       └── Show the REASONING, not just code
       └── "I see X → triggers Y → adapt template like Z"
7.  ⚡ Problem Bank — Key Twists                          [REQUIRED]
       └── Table: LC # | Name | Pattern | Key Twist | Critical Code
       └── 8-10 problems max per file
8.  ⚠️ Interview Gotchas                                  [REQUIRED]
       └── Edge cases interviewers probe
       └── Follow-up questions to expect
9.  🧩 Speed Drill                                        [REQUIRED]
10. 🔗 Cross-References                                   [REQUIRED]
11. 🔄 Changelog                                          [REQUIRED]
```

---

## 🧭 Pattern Block Format

Each pattern section follows this structure:

```markdown
### 🧭 Pattern N: [Name] ⭐ (star if top-3 most common)

**Recognition cues — reach for this when:**
- [trigger word/constraint from problem statement]
- [another trigger]

**Steps in plain English:**

1. **Step name** — what and why.
2. **Step name** — what and why.

```java
// Template code with step-matching comments
```

**🏷️ Problems:** LC X (Name), LC Y (Name), LC Z (Name).
```

### The "Recognition Cues" section is what makes this different

This is the core value of the Interview Playbook format. Each pattern must have 2-4 bullet points answering: **"What words/constraints in the problem statement should trigger this pattern in my head?"**

Examples:
- "contiguous subarray" + "sum equals K" → Prefix Sum + HashMap
- "sorted array" + "find pair" → Two Pointers Converging
- "longest/shortest substring with at most K" → Variable Sliding Window

---

## 🔬 Canonical Problem — The Thinking Walkthrough

This is NOT just a coded solution. It's a **narrated decision process** showing how to go from reading the problem to writing the code. Format:

```markdown
## 🔬 Canonical Problem — LC X: Problem Name

> **Problem (1-2 sentences):** [plain-English restatement]

### Step 1 — Read and identify triggers

"The problem says [exact words]. This triggers Pattern N because [reason]."

### Step 2 — Choose the template

"I'll use the [Pattern Name] template. I need to decide: [what state to track], [what the window/pointer condition is]."

### Step 3 — Adapt and code

**Steps in plain English:**

1. ...
2. ...

```java
// Full solution
```

### Step 4 — Verify with example

[Trace through the given example to confirm]

### Complexity

- **Time:** O(...)
- **Space:** O(...)
```

---

## ⚡ Problem Bank Format — Expanded

Each problem gets a mini-entry with **definition** (so the reader doesn't need to open LeetCode), **approach** (1-2 lines connecting to the pattern), and **critical code** (4-6 lines).

```markdown
## ⚡ Problem Bank — Expanded

---

### LC X: Problem Name

> **Problem:** 2-3 line plain-English problem statement with a concrete example.

> **Approach:** 1-2 lines connecting to the pattern above and explaining the key twist.

```java
// 4-6 lines of critical code — the "aha" part, not the full solution
```
```

**Rules:**
- 8-10 problems per file (depth > breadth for interview prep)
- **Problem definition is mandatory** — the reader should understand the problem WITHOUT opening LeetCode
- Include a concrete example in the definition when possible (e.g., `[1,1,1], k=2 → 2`)
- Critical code = the 4-6 lines that implement the key twist, not the full solution
- Order by difficulty within each pattern group

---

## ⚠️ Interview Gotchas — What Interviewers Probe

Not just code bugs (those are in java-coding-traps.md). This section covers:

1. **Edge cases they'll ask about** — empty input, single element, all duplicates, negative numbers
2. **Follow-up questions** — "Can you do it in O(1) space?", "What if the array isn't sorted?", "What about duplicates?"
3. **Complexity traps** — solutions that look O(n) but are actually O(n²) due to hidden inner loops

---

## 🧩 Speed Drill Format

Timed drill, specific to this topic. Should take 5-10 minutes.

```markdown
## 🧩 Speed Drill — X Minutes

**Part 1 — Pattern Recognition (2 minutes)**
For each problem description, name the pattern in under 5 seconds:

1. "Find two numbers in a sorted array that sum to target" → ___
2. "Longest substring with at most K distinct characters" → ___

**Part 2 — Write the Template (3 minutes)**
From memory, write the [Pattern Name] template.

**Part 3 — Adapt (5 minutes)**
Solve [LC X] using the template. Time yourself.

**Scoring:** [rubric]
```

---

## 🔄 Lambda & Shorthand Convention (Mandatory)

Every Interview Playbook file must follow these rules for complex methods and lambda expressions:

### Rule 1 — Inline English Comment at EVERY Usage

Whenever code uses a lambda, method reference (`Integer::sum`), or complex method (`merge`, `computeIfAbsent`, `getOrDefault`, `entrySet`, `!set.add()`, PriorityQueue comparator, `Arrays.sort` with comparator, `Collections.reverseOrder()`), add a plain-English comment explaining what it does **at that exact usage point**.

```java
// merge: if key absent → put(key, 1); if present → put(key, old + 1)
// Integer::sum is shorthand for (oldVal, newVal) -> oldVal + newVal
freq.merge(ch, 1, Integer::sum);
```

### Rule 2 — 🔄 Fallback at EVERY Usage

Immediately after the complex method call, add a `// 🔄 Fallback:` comment showing the plain `if-else` or basic-method equivalent. **This must appear at every usage point, not just the first one.** The reader scanning under exam pressure should never have to scroll back to remember the workaround.

```java
freq.merge(ch, 1, Integer::sum);
// 🔄 Fallback: freq.put(ch, freq.getOrDefault(ch, 0) + 1);
```

### Rule 3 — Master Explanation in the Lambda Section

Each file that uses complex methods should have a **🔄 Lambda & Shorthand Explanations with Fallbacks** section right after the 🔧 Essential Methods table. This section provides the full breakdown of each method used in that file (how it works internally, what the lambda parameter means, when to use the fallback).

### Rule 4 — Repeat Everywhere

If the same method appears 3-4 times in the file, **write the fallback at every usage**. This is intentional redundancy — each pattern section should be self-contained for revision.

### Methods That Require This Treatment

| Method | When it appears |
| --- | --- |
| `map.merge(key, val, fn)` | Incrementing counters |
| `map.computeIfAbsent(key, fn)` | Get-or-create (grouping) |
| `map.getOrDefault(key, default)` | Safe get |
| `map.entrySet()` | Iterating key-value pairs |
| `!set.add(x)` | Add-and-check for duplicates |
| `new PriorityQueue<>((a, b) -> ...)` | Custom comparator heaps |
| `Collections.reverseOrder()` | Max-heap creation |
| `Arrays.sort(arr, (a, b) -> ...)` | Sort with comparator |
| `Integer.compare(a, b)` | Overflow-safe comparison |
| Method references (`Integer::sum`) | Shorthand for lambdas |

---

## 💻 Code Conventions

Same as DSA-wide conventions (from `../AGENTS.md`):
- Java, no imports declared
- One statement per line, always braced
- English steps before template code
- Working code only — no `...` placeholders

---

## 📂 File Naming

| Pattern | Example |
| --- | --- |
| `<topic>.md` | `arrays-and-hashing.md`, `linked-list.md` |
| `notes-standards-interview.md` | This file |

---

## ✅ Quality Checklist

Universal (from `../../AGENTS.md`):
- [ ] All code blocks language-tagged, one statement per line, always braced
- [ ] All templates have English steps before code
- [ ] All LC references include LC number
- [ ] No emojis outside approved palette
- [ ] First-use terms glossed in parentheses (Rule 8)

Interview-specific:
- [ ] Every pattern has **recognition cues** (trigger words from problem statements)
- [ ] Canonical problem shows **thinking process**, not just code
- [ ] Problem bank has 8-10 problems with **key twist** + **critical code** columns
- [ ] Interview gotchas cover **edge cases + follow-ups + complexity traps**
- [ ] Speed drill is timed with a scoring rubric
- [ ] File is 400-600 lines (1-1.5 hour read)
- [ ] Cross-references to companion Reference and DeepDive files
- [ ] Every lambda / complex method has an **inline English comment** at each usage
- [ ] Every complex method has a **🔄 Fallback** showing the plain `if-else` equivalent at each usage
- [ ] Missing methods (used in code but not in Essential Methods table) are added to the table

---

## 🔗 Companion Standards

- **Project root:** `../../AGENTS.md` — universal rules
- **DSA root:** `../AGENTS.md` — DSA-specific rules, difficulty tags, code conventions
- **Sister folder:** `../DeepDive/notes-standards-deepdive.md` — for learning new topics from scratch
- **Sister folder:** `../Reference/notes-standards-reference.md` — for method syntax and pattern syntax
- **Sister folder:** `../Patterns/notes-standards-patterns.md` — for single-problem deep dives

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Folder created.** Interview Playbook — a new note type bridging the gap between Reference (syntax) and problem-solving (pattern recognition). Format: mental model → patterns with recognition cues → canonical walkthrough → problem bank → interview gotchas → speed drill. |
| May 2026 | **Lambda & Shorthand Convention added.** New mandatory section: every lambda, `merge`, `computeIfAbsent`, PQ comparator, `Integer.compare`, `Collections.reverseOrder()`, `toArray()`, `TreeMap`, and `Arrays.sort` with comparator must have inline English comment + 🔄 Fallback at EVERY usage point, plus a master 🔄 Lambda section per file. Added 3 quality checklist items. |
