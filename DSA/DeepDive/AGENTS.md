# DeepDive Folder — AI Assistant Workflow

> **For any AI assistant creating or updating files in `DSA/DeepDive/`:** Read this file, then read `notes-standards-deepdive.md` for format/structure rules. Both apply.

---

## 🎯 Purpose of This File

This file codifies **HOW an AI assistant should approach writing a DeepDive note**—not the format (that's in notes-standards-deepdive.md), but the **thinking, sourcing, and validation strategy**.

**Reader:** Future AI assistants (Claude, Cursor, etc.) creating DeepDive files.  
**Not for:** Kapil directly (unless interested in the workflow).

---

## 📋 Workflow: Creating a DeepDive Note

### Phase 1: Clarification (before writing)

1. **Confirm the scope with Kapil:**
   - *"Should I create `DeepDive/<topic>-fundamentals.md`?"*
   - Confirm target audience (Kapil is preparing for FAANG interviews)
   - Confirm length target (800–1500 lines)

2. **Identify the topic's core patterns** (not format yet, just scope):
   - *"For [topic], I see patterns: X, Y, Z. Does that feel complete?"*
   - Get approval before writing 1500 lines

3. **Declare your curriculum sources** (see "Resource Sourcing Strategy" below):
   - *"I'll source from: Striver's [series], LeetCode editorials [problems], GeeksforGeeks [sections]."*
   - Get approval on the mix

---

### Phase 2: Content Writing (following notes-standards-deepdive.md)

Write the 15 required sections **with special attention to pattern motivation** (see "Pattern Structure — MANDATORY" below).

---

### Phase 3: Validation (before delivery)

Run the checklists in this file (see below) + the checklists in notes-standards-deepdive.md.

---

## 📚 Resource Sourcing Strategy

### Why it matters

A pattern introduced without sources becomes "memorization magic." A pattern sourced from Striver's structured curriculum becomes "I see why this problem needs this pattern; here's the proof."

### Approved sources (by topic)

| Topic | Primary | Secondary | Tertiary | Notes |
| --- | --- | --- | --- | --- |
| **Stacks & Queues** | Striver Stack/Queue series | LeetCode editorials (LC 20, 84, 155, 239) | GeeksforGeeks ADT basics | Striver covers all core patterns |
| **Linked Lists** | Striver LL series | LeetCode editorials (LC 206, 141, 143) | GeeksforGeeks pointer basics | Striver → problem examples → editorial walkthrough |
| **Strings** | Striver String series | LeetCode editorials (anagrams, palindromes, sliding window) | GeeksforGeeks pattern matching | Strings benefit from multiple angles |
| **Heaps** | Striver Heap series | LeetCode editorials (LC 215 Kth Largest, LC 23 Merge K Lists) | GeeksforGeeks heap structure | Striver covers min-heap + operations |
| **HashMaps & Sets** | Striver Hash series | LeetCode editorials (LC 1, 49, 560) | GeeksforGeeks hash collision handling | Striver → patterns → deep dives |
| **Trees & Graphs** | Striver Tree/Graph series | LeetCode editorials (LC 102, 124, 200) | CLRS (optional, for proofs) | Striver covers traversals + patterns |
| **Recursion & DP** | Striver Recursion/DP series | LeetCode editorials (problem-specific) | GeeksforGeeks memoization | Striver provides mental models |

### How to source (step-by-step)

1. **Identify the patterns** for this topic (3-5 core patterns for most DeepDives).
2. **For each pattern:**
   - Find the Striver video that explains it (note video #)
   - Find 2-3 LeetCode problems that require this pattern (note LC #)
   - Read the LeetCode editorial for each (note key insights)
3. **Write the pattern section** in this order:
   - **"When you'll see this"** — list the 2-3 LC problems
   - **"Problem motivation"** — one concrete problem: "Given [input], solve [output]"
   - **"Naive approach"** — brute force solution + complexity, why it fails
   - **"Why this pattern solves it"** — insight that leads to the pattern
   - **"English steps"** — how to implement (from Striver's explanation)
   - **"Code template"** — working code
   - **"Inline drill"** — practice from memory

4. **Track sources in the file's curriculum-alignment section** (top of file):
   ```markdown
   > **Curriculum alignment:** This deep-dive synthesizes:
   > - **Striver's [Topic] Series** (videos X, Y, Z covering [patterns])
   > - **LeetCode Editorials** (LC A, B, C for [pattern focus])
   > - **GeeksforGeeks** ([specific sections])
   ```

5. **Add per-pattern credits** (optional, but encouraged):
   ```markdown
   **Why this pattern works:** [1-2 sentence insight from Striver/editorial]
   ```

---

## 🧭 Pattern Structure — MANDATORY

Every pattern in every DeepDive must follow this structure. **No exceptions.**

### The structure (in order)

```markdown
### Pattern N: [Pattern Name]

**When you'll see this pattern:**
  - LC XXX Problem Name — [one-line reason why this pattern applies]
  - LC YYY Problem Name — [one-line reason]
  - [Real-world example, optional]

**Problem motivation — concrete example:**

"Given an array `arr` and window size `k`, find the maximum element in every sliding window of size `k`."

Example: `arr = [1,3,-1,-3,5,3,6,7]`, `k = 3`
Output: `[3,3,5,5,6,7]`

**Naive approach (and why it fails):**

```java
// Brute force: for each window, scan all k elements
// Time: O(n * k) — for n windows, k comparisons each
// Space: O(1)
// Problem: On LC 239 (n=100k, k=20k) → 2B operations → TLE
```

**Why this pattern solves it:**

The key insight: "We only need to track elements that could **ever** be the max in a future window. Monotonic deque maintains this in O(1) amortized per element."

[Optional: 1-2 sentences on the intuition, e.g., from Striver's explanation]

**Steps in plain English:**

1. [step 1]
2. [step 2]
3. [step 3]

```java
// Code with comments matching steps
```

**Why this works:** [1-2 sentence reinforcement of the insight]

---

> 🧩 **Drill — do this NOW before reading further:**
> [Specific exercise to practice from memory]
```

---

### Mandatory fields

| Field | Why it matters |
| --- | --- |
| **When you'll see this pattern** | Connects pattern to real problems; prevents "why does this exist?" question |
| **Problem motivation** | Concrete example reader can hold in their mind |
| **Naive approach + complexity** | Reader understands the performance gap and why the pattern is necessary |
| **Why this pattern solves it** | The insight that justifies the pattern (not "trust me," but "here's why") |
| **English steps** | How to implement |
| **Code template** | Working code |
| **Inline drill** | Active recall |

---

## 🎨 Visuals — Minimum Count & Placement

Every DeepDive must have **at least 3-5 ASCII visuals** distributed as:

| Placement | Count | Example |
| --- | --- | --- |
| After mental-model worked example | ≥1 | Show the algorithm's state progression |
| Inside spatial/sequential patterns | ≥1 per pattern | Show the pattern's "shape" (slow-fast pointers, monotonic stack, etc.) |
| Inside step-by-step walkthroughs | ≥1 per walkthrough (if state evolves) | Trace the algorithm frame-by-frame |
| **Total** | **3-5 minimum** | Distributed, not clustered |

**Anti-pattern:** A DeepDive with just 1-2 visuals (usually means one pattern is under-visualized).

---

## ✅ Validation Checklist (run before delivering)

### A. Universal Rules (from root `AGENTS.md`)

- [ ] All code blocks pass formatting rules (language-tagged, one statement per line, always braced, spaced operators)
- [ ] All templates have English steps before code
- [ ] All LC references include the `LC ###` number
- [ ] All "Try these" callouts have ✅ / 🟡 / 🔴 tags
- [ ] No emojis outside the approved set

---

### B. DeepDive Format (from `notes-standards-deepdive.md`)

- [ ] 15 required sections present (Goal, Difficulty legend, Definition, Terminology, Mental Model, Style Habits, Patterns, Walkthroughs, Gotchas, Practice Plan, TL;DR)
- [ ] Mental Model section explicitly labeled with big-idea blockquote
- [ ] Style Habits split into Universal (≥4) vs Context-Specific (≥2)
- [ ] ≥3 worked walkthroughs covering different patterns
- [ ] Gotchas section with ❌/✅ wrong-vs-right code
- [ ] Practice Plan in tiers (not weeks); Tier 1 = "Foundational N"
- [ ] Every problem tagged ✅ / 🟡 / 🔴
- [ ] Inline drills after each pattern section
- [ ] 🧾 TL;DR bullet format, scannable in 30 seconds
- [ ] Cross-references to companion docs

---

### C. Visualization (from `notes-standards-deepdive.md`)

- [ ] **At least 3-5 `### 🎨 Visual — <desc>` blocks** (count them)
- [ ] Mental Model has ≥1 visual after worked example
- [ ] Every spatial/sequential pattern has its signature visual
- [ ] Every step-by-step walkthrough (where state evolves) has trace visual
- [ ] **Each visual ends with `KEY INVARIANT:` one-liner**
- [ ] Visuals in plain fenced blocks, ≤80 columns wide

---

### D. Pattern Structure (from this file — CRITICAL)

- [ ] Every pattern has "When you'll see this pattern" section (with LC problem references)
- [ ] Every pattern has "Problem motivation" (concrete example with input/output)
- [ ] Every pattern has "Naive approach" + complexity analysis + "why it fails"
- [ ] Every pattern has "Why this pattern solves it" (the insight)
- [ ] Every pattern has English steps before code
- [ ] Every pattern has an inline drill after the code

---

### D.1 Pattern Application Gallery (NEW)

Every pattern should have a "Pattern Application Gallery" section with **3-5 most-asked problems** demonstrating the pattern in real interviews (not arbitrary problems, but the ones that appear repeatedly in FAANG loops).

**Gallery problem structure:**

Each gallery problem follows this format:

```markdown
**Problem Xa: [Problem Name] (LC XXX)**

**Problem:** [One-line statement with example input/output]

**Naive approach (when applicable — see inclusion rule):**
  Brute: [1-2 lines describing approach]
  Time: O(x²), Space: O(x)
  Why it fails: [1 line: TLE, or other limitation]

**The insight:** [2-3 lines: why pattern solves it]

**Structure:**
```java
[code skeleton showing WHERE pattern applies]
```

**Time:** O(x), **Space:** O(x)
```

**Naive approach inclusion rule:**

| Pattern | Include? | Why |
| --- | --- | --- |
| Pattern 1 (Frequency Map) | ❌ No | Brute force is obvious |
| **Pattern 2 (Complement Lookup)** | ✅ **YES** | Easy to memorize HashMap without justifying why |
| Pattern 3 (Canonical Form) | ❌ No | Grouping is clear from statement |
| **Pattern 4 (Prefix Sum)** | ✅ **YES** | Transformation needs justification |
| Pattern 5 (Two-Pass) | ❌ No | Two passes suggested naturally |
| Pattern 6 (Custom Grouping) | ❌ No | Windowing is intuitive |

**Rule:** Include naive approach only when someone might memorize the solution without understanding **why the pattern is necessary**. This interview-proofs the gallery.

---

### E. Resource Attribution (from this file)

- [ ] File top has "Curriculum alignment" section citing Striver/LeetCode/GeeksforGeeks
- [ ] Per-pattern notes credit the source (e.g., "from Striver's video 3.5")
- [ ] Optional: "Credit" section at bottom acknowledging all sources

---

### F. Scope & Length

- [ ] Document length 800–1500 lines (count with `wc -l`)
- [ ] Not bloated (no redundant sections)
- [ ] Not under-scoped (all 5 core patterns covered)

---

## ❌ Common Mistakes (catch before delivery)

| Mistake | How to spot | Fix |
| --- | --- | --- |
| Pattern without motivation | Section goes straight to "English steps" | Add "When you'll see..." + "Problem motivation" + "Naive approach" before steps |
| No source attribution | Top of file missing "Curriculum alignment" | Add sources for all patterns; cite video #s and LC editorials |
| Insufficient visuals | Count `### 🎨` — find <3 | Add visuals to patterns + walkthroughs; ensure KEY INVARIANT statement |
| Unclear gotchas | Gotchas section exists but lacks context | Make each gotcha: bad code → good code → 1-2 line explanation |
| Weak practice plan | All problems in one tier | Reorganize by difficulty; Tier 1 = foundational; later tiers add complexity |
| Missing drills | Read patterns section; no inline drills | Add one inline drill after each pattern (memory-based exercise) |
| Vague pattern names | Patterns named "Pattern A", "Pattern B" | Name them concretely: "Three-Pointer Reversal", "Monotonic Deque for Sliding Window" |

---

## 🔗 Relationship to `notes-standards-deepdive.md`

| File | Covers | Audience |
| --- | --- | --- |
| **This file (AGENTS.md)** | Workflow, sourcing strategy, pattern structure, validation checklist | AI assistants creating DeepDive files |
| **notes-standards-deepdive.md** | Document format, section order, markdown conventions, visual placement | AI + Kapil (what the final deliverable should look like) |

**Together:** They fully define how to write a DeepDive. AGENTS.md = "how to think about it"; notes-standards-deepdive.md = "how to format it."

---

## 📌 Decision Tree: Is This a DeepDive or Reference?

When Kapil says *"write notes for [topic]"*, use this tree:

```
Is this "learn from zero" (new topic not yet internalized)?
├─ YES → DeepDive (800–1500 lines, mental models, patterns, walkthroughs, practice plan)
└─ NO  → Is this "quick syntax recall"?
         ├─ YES → Reference (300–700 lines, method syntax, cheatsheet tables, speed drills)
         └─ NO  → Is this "one specific problem walkthrough"?
                  ├─ YES → Pattern (250–400 lines, single LC problem, brute→optimal approaches)
                  └─ NO  → Is this a HashMap + second-DS design problem (LRU, LFU, Rate Limiter, etc.)?
                           ├─ YES → ADD TO hybrid-design-problems.md (see Special Files below)
                           └─ NO  → Clarify with Kapil
```

---

## 📂 Special Files in DSA/DeepDive/

Some files in this folder don't follow the standard DeepDive format — they serve a specific purpose. Read this table before creating a new file to avoid duplicating content.

| File | Type | What it covers | When to use it |
| --- | --- | --- | --- |
| **`hybrid-design-problems.md`** | Hybrid Design (DSA + LLD) | Problems that pair a HashMap with a second data structure for ordering: LRU Cache (DLL), LFU Cache (frequency buckets), Hit Counter (deque/circular array), Leaderboard (TreeMap), Rate Limiter (4 algorithms). Pattern: "HashMap gives O(1) lookup; second DS gives the ordering the problem needs." | When the problem says "design a [X]" and requires both O(1) access AND ordering/eviction/windowing logic |
| **`notes-standards-deepdive.md`** | Meta / Standards | Format and section rules for every DeepDive file | Read before writing any new DeepDive note |

**hybrid-design-problems.md — extended context for AI assistants:**
- Every problem in this file follows: problem statement → DS Combo (what the two structures are) → Visual/Trace → Steps + full Java → Edge Cases → Variants → Try these
- The mental model is "HashMap + X": HashMap handles O(1) lookup; the second DS handles the behavioral requirement (recency, frequency, time window, value ranking)
- New problems belong here if they fit the "HashMap + X" pattern AND have more DSA depth than system design depth
- Problems with more system design depth (distributed rate limiting, consistent hashing) belong in SystemDesignConcepts/ (separate folder, planned)

---

## 🧾 Template Snippet (copy for quick start)

```markdown
# [Topic] — Fundamentals

> [One-sentence tagline on what + audience]

---

> **Curriculum alignment:** This deep-dive synthesizes:
> - **Striver's [Series Name]** (videos X, Y, Z covering [core patterns])
> - **LeetCode Problem Editorials** (LC A, B, C for [focus areas])
> - **GeeksforGeeks** ([specific sections])
>
> **Credit:** [Pattern sources] from Striver. Walkthroughs adapted from [LC editorials]. Mental models and interview context are this doc's contribution.

---

## 🎯 Why You're Reading This

[Goal statement]

---

[Continue with all 15 required sections from notes-standards-deepdive.md]
```

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **Initial AGENTS.md for DeepDive folder created.** Codifies AI workflow: sourcing strategy, pattern-structure requirements (with problem motivation mandate), resource attribution, and validation checklist. Clarifies relationship to notes-standards-deepdive.md. |
| June 2026 | **Added D.1 Pattern Application Gallery section.** Specifies format for gallery problems (problem statement + selective naive approach + insight + code structure), inclusion rules for when to explain naive approach (Complement Lookup, Prefix Sum patterns only), and emphasis on "most-asked problems" not arbitrary ones. |
| July 2026 | **Added Special Files section + hybrid-design-problems.md to Decision Tree.** Registers `hybrid-design-problems.md` as a non-standard special file covering HashMap+X design problems (LRU, LFU, Hit Counter, Leaderboard, Rate Limiter). Extended Decision Tree with a "Hybrid Design" branch so future AI assistants route new design problems to the correct file instead of creating a separate DeepDive. |
