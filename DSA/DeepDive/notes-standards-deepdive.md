# DeepDive Notes — Standards

> Conventions for writing in-depth study notes in this `DeepDive/` folder. Read this **after** the master `AGENTS.md` at the project root. Together they tell you exactly how to structure a new deep-dive note.

---

## 🎯 Purpose

A DeepDive note is the file Kapil reads **once, top-to-bottom**, when learning a new topic. It must:

- Build the mental model from zero
- Cover edge cases and gotchas thoroughly
- Tell Kapil **what to attempt now vs. what to defer**
- Cross-reference companion docs
- Include a practice plan organized by progressive difficulty

After reading a deep-dive once, Kapil migrates to a `Reference/<topic>-reference.md` file for daily revision. The deep dive is "the textbook"; the reference is "the cheatsheet."

**Length target:** 800–1500 lines. Less than 800 → likely a Reference note instead. More than 1500 → consider splitting into multiple deep dives (e.g., trees fundamentals + tree DP).

---

## 📐 Document Structure (Section Order)

Every DeepDive note follows this section template. Sections marked **[REQUIRED]** must appear; others are conditional.

```
1.  H1 Title                                     [REQUIRED]
2.  Tagline blockquote (one sentence on what + audience)  [REQUIRED]
3.  🎯 Why You're Reading This (the Goal)        [REQUIRED]
4.  🚦 Difficulty Tagging Legend                 [REQUIRED]
5.  🌲 What Is X? (definition + simplest example) [REQUIRED]
6.  📖 Terminology Table                         [REQUIRED]
7.  🛠️ Class / Skeleton (if applicable, e.g., TreeNode)
8.  🧠 Mental Model (the "most important section") [REQUIRED]
       └── must include ≥1 🎨 Visual block
9.  🎨 Style Habits — Universal vs Context-Specific [REQUIRED]
10. 🚶 / 🌊 / 🧭 Patterns — each with English-steps + template + example
       └── each spatial/sequential pattern carries a 🎨 Visual block
11. 🌳 Special Topics (e.g., BST, two-purpose recursion ladder)
12. 🔬 Worked Walkthroughs (3-4 problems, fully traced)  [REQUIRED]
       └── if state evolves step-by-step, include a 🎨 Visual trace
13. ⚠️ Gotchas (Silent Bug Hall of Fame)          [REQUIRED]
14. 🗺️ Practice Plan (in tiers)                   [REQUIRED]
15. 🧾 TL;DR — One-Page Summary                   [REQUIRED]
```

> **Visual minimum across the doc:** at least **3-5 🎨 Visual blocks** per DeepDive, distributed across mental-model / patterns / walkthroughs. See the "🎨 ASCII Visualizations" section below.

---

## 🚦 Difficulty Tagging — Mandatory

> **Every problem mentioned in a DeepDive must carry a difficulty tag.** This was made a hard rule in May 2026 after Kapil burned an hour on LC 124 (Maximum Path Sum) which was suggested in a "Try these" callout but actually needed concepts from later in the doc + a separate prerequisite ladder.

### The legend (paste this in every DeepDive)

```markdown
## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point in the doc | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs concepts from a later section in this same doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc | Read the problem and editorial for awareness; don't attempt cold |
```

### How to apply tags

- **In "🧩 Try these" callouts** — every LC problem gets a tag + 1-line reason
- **In Practice Plans** — tier-organized; tag at each item
- **In walkthroughs** — if you walk through a 🔴 problem (e.g., LC 124), put a Reference-Only banner at the top of the walkthrough
- **Be honest, not optimistic** — if the problem requires intuition not yet built in the doc, tag it 🟡 or 🔴, even if it "feels" doable

### Sample correctly-tagged "Try these" callout

```markdown
> 🧩 **Try these:**
> - ✅ LC 700 Search in BST — direct application of BST property
> - ✅ LC 938 Range Sum of BST — BST property + pruning
> - 🟡 **Try after LC 700** — LC 701 Insert into BST (small extension)
> - 🔴 LC 450 Delete Node in a BST — three-case pointer surgery; come back later
```

---

## 🪜 Templates and Pattern Code — English Steps BEFORE Code

> **Established in May 2026.** Whenever you present a template, pattern skeleton, or pattern example code, put numbered plain-English steps first, then the code with comments matching those steps.

### Why

Kapil reads the doc top-to-bottom. Diving straight into code forces him to mentally reverse-engineer what each line is doing. Steps-first means:
- He understands the *intent* before he sees the *syntax*
- Each code line has a clear "this is which step" anchor via the comment
- It mirrors how he should *speak* through the algorithm in an interview

### Format

```markdown
**Steps in plain English:**

1. **Step name** — what we do and why.
2. **Step name** — what we do and why.
3. **Step name** — what we do and why.

```java
public ReturnType solve(TreeNode root) {
    // Step 1 — short comment matching the English step
    if (root == null) {
        return baseValue;
    }

    // Step 2 — short comment matching the English step
    ReturnType left = solve(root.left);

    // Step 3 — short comment matching the English step
    return combine(root.val, left);
}
```
```

### When to apply

| Code block | English steps required? |
| --- | --- |
| **Templates** (skeletons used as scaffolding) | ✅ Yes |
| **Pattern examples** (e.g., LC 543 Diameter implementing Pattern 2) | ✅ Yes |
| **Walkthroughs** (full problem solutions) | ✅ Yes |
| **One-line method demos** (e.g., `set.add(5);`) | ❌ No |
| **Wrong-vs-Right gotcha snippets** (3-6 lines) | ❌ No |
| **ASCII trace diagrams** | ❌ No |

### Cross-check before shipping

When reviewing a draft, every code block bigger than 5 lines should be paired with an English-steps numbered list directly above it.

---

## 🎨 Style Habits — Always Split into Universal vs Context-Specific

> **Established after Kapil's feedback on the trees doc:** *"till habit 3 I'm able to follow, after that I'm not so sure."* Mixing habits of different scope creates cognitive overload.

### Format

```markdown
## 🎨 Style Habits — Build These From Day 1

> Some habits apply to **every problem you write** (even non-[topic] ones). Others only matter when you encounter specific patterns. **Master the universal ones now**; skim the context-specific ones and revisit them when you hit the pattern.

---

### 🌐 Universal Habits (apply everywhere — start using today)

#### Habit 1 — [name]
[explanation + ❌/✅ code if helpful]

#### Habit 2 — [name]
...

---

### 🔧 Context-Specific Habits (will click as you encounter these patterns)

> These won't matter on your first 5 [topic] problems. **Skim them now to recognize the trap, then refer back when you actually hit the pattern.**

#### Habit 5 — [name]
> Applies whenever you [specific situation].
[explanation + ❌/✅ code]
```

### Closing recap

End the section with a one-line recap:

```markdown
> **Quick recap of the 4 universal habits:** name intermediates → null-check first → always brace → verbalize while writing. Those four cover ~90% of habit benefit on your first 20 problems.
```

---

## 🧠 Mental Model Section — The "Most Important Section"

Every DeepDive needs one section explicitly labeled as the **mental model** for the topic. This is the section where Kapil builds intuition before any patterns.

### Format

1. **Big idea callout** — one blockquote with the key intuition
2. **Worked example** — one concrete case, fully traced
3. **Universal skeleton** — the base shape that 80%+ of problems fit
4. **3-question template** (recursion-flavored topics) — base case / recursive case / combine

### Example — from trees doc

> **Trust the recursion.** When you call `solve(root.left)`, **assume it correctly solves the left subtree**. Then combine that result with the right subtree's result and the current node's contribution.

This blockquote is the entire mental model. The rest of the section unpacks it.

---

## 🎨 ASCII Visualizations — Mandatory for Spatial / Sequential Concepts

> **Established in May 2026** — after a full retrofit pass across all six DeepDives (trees, backtracking, recursion, arrays, two-pointers-sliding-window, integer-overflow) added 15+ ASCII diagrams. Visualizations turned out to be the single highest-leverage edit: the moment a reader sees "the picture," the algorithm clicks. The universal "why ASCII" rule lives in master `AGENTS.md`; this section codifies **where** visuals must appear in a DeepDive note and **what they must teach**.

### The hard rule

Every DeepDive must contain **at least 3-5** `### 🎨 Visual — <description>` blocks, distributed across the mental-model / pattern / walkthrough sections per the placement rules below. If you find yourself writing a paragraph that describes "imagine an array with two pointers walking toward each other" or "picture a tree being explored level by level," **stop and draw it instead**.

### Mandatory placements

A 🎨 Visual block **must** appear at each of these locations (when the topic has spatial or sequential structure):

| Placement | What the visual should teach |
| --- | --- |
| **After the mental-model worked example** | The shape of the data + the algorithm's "shape of progress" (e.g., recursion's call-stack push/pop, BFS's level-by-level expansion) |
| **Inside each pattern section that has spatial structure** | The pattern's signature picture — the thing that makes it recognizable in 1 second (e.g., two pointers converging, sliding window expand/shrink, decision tree branching) |
| **Inside any walkthrough where state evolves step-by-step** | A frame-by-frame trace of how the state changes (e.g., the queue contents at each BFS step, the worm sliding across a string) |
| **Inside gotchas where the bug is geometric** | The exact pixel where the bug lives (e.g., off-by-one at boundary, mid-point overflow on the number line) |

### Optional placements

| Placement | Use when |
| --- | --- |
| Inside terminology table | A term is fundamentally visual (e.g., "BST property" — show the bounds picture) |
| Inside special-topic sections | The topic adds a new structural concept (e.g., trie shape, segment tree shape) |
| Inside TL;DR | Never. TL;DR is text-only. |

### Format (recap — full spec in master `AGENTS.md`)

````markdown
### 🎨 Visual — <what this picture teaches>

```
<box-drawn ASCII art, ≤ 80 columns wide>
```

**KEY INVARIANT:** <one-sentence statement of the algorithmic invariant the picture demonstrates.>
````

- **Plain fenced block** (no language tag) so monospace alignment is preserved
- **≤ 80 columns** — wider breaks side-by-side viewers
- **End with KEY INVARIANT** — the picture without the invariant is decoration; the invariant is what the reader carries away

### What an invariant statement looks like

| Topic | Visual | KEY INVARIANT |
| --- | --- | --- |
| BFS queue | Animation of queue contents per level | "The queue holds at most 2 consecutive levels at any moment." |
| BST bounds | Range labels propagating down the tree | "Every node has a `(min, max)` range its value must fit inside, narrowed by each turn taken." |
| Two pointers | Pointers walking toward each other | "Everything between `left` and `right` is the unexplored region; outside is already decided." |
| Kadane's algorithm | Extend-vs-restart fork at each index | "At index i, either extend the previous best subarray or restart fresh — never both." |
| Integer mid-point | Number line + circular wrap-around | "`low + (high - low) / 2` cannot overflow because the offset is non-negative and bounded by `high`." |

### When NOT to draw

- The concept is **purely arithmetic** with no spatial intuition (e.g., "modular arithmetic" — better as a formula than a picture)
- The picture would be **identical to one already in the doc** (don't duplicate; link to the earlier visual)
- It's a **one-line method demo** (e.g., `set.add(5);` — no visual needed)
- It's a **wrong-vs-right gotcha snippet** under 6 lines (the ❌/✅ diff is the visual)

### Companion to "English steps before code"

The two rules are siblings:

1. **English steps before code** — tells the reader *what the algorithm does, step by step*
2. **🎨 Visuals** — shows the reader *what the data looks like at each step*

Together they form a **prose → picture → code** progression. Skipping either one means the reader has to reconstruct it mentally. Both should appear before any non-trivial template.

### Cross-reference

The universal "why ASCII" rationale + the 5 format conventions live in **master `AGENTS.md` Section 6** ("ASCII Visualizations — Draw the Spatial / Sequential Concept"). This section adds DeepDive-specific placement rules and the minimum-count expectation.

---

## 🔬 Worked Walkthroughs — Canonical Problems, One Per Structural Shape

Each walkthrough demonstrates one structurally unique shape from the doc applied to a real LC problem. **Walkthroughs follow a 5-part format** (below). Each part has a reason — do not skip any.

> **Established July 2026:** the format was upgraded from "steps + code + Try these" to the full 5-part template below. The upgrade was driven by a recurring interview failure: Kapil could recite the optimal algorithm but stalled when asked "what's your brute force?" and "what made you think of that optimization?" The two new parts (Brute Force + Intuition Bridge) directly train those two missing muscles.

### Format

````markdown
### WW-N — LC [number] [Problem Name]

> **Problem:** [one sentence plain-English statement of what's given and what's returned]

**Brute force:** [2-3 sentences — explain the naive approach in plain English, NO code]
> **Time:** O(?) | **Space:** O(?)

**Intuition bridge — what cracks it open:** [1-2 sentences on the single observation or insight that makes the optimal approach visible. This is the "aha" — the thing that bridges brute force to optimal.]

**Steps in plain English:**

1. **Step name** — what we do and why.
2. **Step name** — what we do and why.
3. **Step name** — what we do and why.

```java
// full optimal solution, complete and runnable, with step-matching comments
```

**Time:** O(?) | **Space:** O(?)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC X [Problem Name] | [what carries over verbatim] | [the single structural difference] | `[exact line]` |
| LC Y [Problem Name] | ... | ... | `...` |
| LC Z [Problem Name] | ... | ... | `...` |
````

### Why each part exists

| Part | What it trains |
| --- | --- |
| **Brute force** | Every interviewer asks "what's the naive solution?" — and if you can't answer, you look unprepared even if you know the optimal. 2-3 sentences, no code. |
| **Intuition bridge** | The hardest part to teach. You're not memorizing the algorithm — you're training the *jump*: "I notice X, therefore Y." After 8 walkthroughs you start seeing these jumps on new problems. |
| **Steps + code** | The algorithm itself. English steps first (universal rule) so the code is annotated, not guessed at. |
| **Transfers to** | 3-4 problems that share the same skeleton. One pass through these builds a mental "this problem is LC X variant" classifier. |

### Intuition bridge — examples of good vs bad

| ❌ Too vague | ✅ Specific enough to reconstruct |
| --- | --- |
| "We use a sliding window because the problem has subarrays." | "We extend because a bigger window can only help; we shrink from the left when the constraint is violated — that's the key asymmetry that makes the O(n) bound hold." |
| "We use a HashMap for O(1) lookup." | "The complement `target - nums[i]` either exists in the map (done) or we store `nums[i]` for a future complement — so one left-to-right pass is enough." |
| "We sort first so we can use two pointers." | "Sorting lets us know the direction to move each pointer: if `arr[l] + arr[r] > target`, shrinking from the right strictly decreases the sum — no wasted moves." |

### How many walkthroughs

Aim for **6-14 walkthroughs** per DeepDive, each covering a **structurally unique shape** — not just a difficulty increment of the same pattern. Ask: *"if I removed this walkthrough, would a reader be missing a shape they haven't seen?"* If no, merge or drop it.

> **Rule (July 2026):** When reviewing a problem list for frequency or coverage gaps, **never remove a problem that teaches a unique structural pattern** — only add. A problem teaching a pattern not covered elsewhere is irreplaceable even if its interview frequency is moderate. Remove only true duplicates (same pattern, same difficulty, no new structural insight).

### Reference-Only walkthroughs (for advanced problems)

When walking through a 🔴-tagged problem (e.g., LC 124), include:

1. **Banner at top** — "🔴 READ THIS FIRST — Do NOT attempt cold"
2. **What concepts the problem silently demands** (3-bullet list)
3. **The corrected solution** (with English steps)
4. **🐞 Common Bugs** sub-section listing wrong-vs-right code patterns
5. **🪜 Build-up ladder** — prerequisite problems that lead to this one

(See `trees-fundamentals.md` LC 124 walkthrough for the canonical example.)

---

## ⚠️ Gotchas Section — Mandatory

Every DeepDive ends with (or includes near the end) a "⚠️ Gotchas (Silent Bug Hall of Fame)" section.

### Inclusion criterion

> *"Could a beginner write code that compiles, runs, doesn't crash, but produces wrong output?"* If yes → it's a silent bug → document it.

### Format per gotcha

```markdown
**[Bold one-line title].**

```java
// ❌ wrong
[3-6 lines of code]

// ✅ right
[3-6 lines of code]
```

[Optional 1-2 sentence explanation of *why*]

---
```

Separate each gotcha with `---` for visual breathing room. Group related gotchas (e.g., overflow-related gotchas together).

### Mandatory gotchas to include

If applicable to the topic, **always** cover:
- Null handling (the topic's most common NPE source)
- Auto-unboxing traps (if HashMaps / Lists of `Integer` are involved)
- Overflow (cross-reference `integer-overflow-and-limits.md`)
- LeetCode-specific traps (e.g., `static` fields persisting across test cases)
- Modifying state while traversing it
- Off-by-one base cases

---

## 🗺️ Practice Plan — Tier-Based, Not Week-Based

> **Established in May 2026** — replacing the previous "Week 1 / Week 2 / Stretch" structure, which buried difficulty signals.

### Format

```markdown
## 🗺️ Practice Plan — A Progression That Works

[Intro paragraph — pace, time-box, expectation]

> **Reminder of tags:** ✅ Try Now · 🟡 Try after the named prerequisite · 🔴 Reference Only

---

### Tier 1 — Foundational N (must be muscle memory)

[1-line description of what mastering this tier earns]

1. ✅ **LC X** Problem Name — pattern + intuition
2. ✅ **LC X** ...

---

### Tier 2 — [theme]

...

---

### Tier 3 — [theme]

...

---

### Tier 4 — [advanced theme] (tag mostly 🟡)

...

---

### Tier 5 — Reference Only (multi-pattern / advanced — skip on first pass)

[1-line caveat: "treat as bedtime reading"]

...

---

### How to use this plan

- **Pace:** [pace recommendation]
- **When stuck:** time-box at 25 minutes; if still stuck, read editorial, **don't accept-paste**
- **Revision:** after finishing a tier, redo problems 1-3 from memory
- **Victory criterion:** [specific milestone meaning "you're ready"]
```

### Tier rules

- **Tier 1 = "Foundational N"** — non-negotiable; must be muscle memory before anything else
- **Tag every problem** at the item level (not just at the tier level)
- **Order within a tier matters** — list prerequisites before dependents
- **Last tier is always 🔴 "Reference Only"** — multi-pattern problems

---

## 🧩 Embedded "Try these" Callouts

Use throughout the doc to suggest practice as concepts are introduced. Each callout:

- Is wrapped in a blockquote
- Lists 3-7 problems
- Tags every problem
- Adds a 1-line reason per problem (or a "do after X" prerequisite hint)

### Format

```markdown
> 🧩 **Try these:**
> - ✅ LC 102 Level Order Traversal — the template above is the answer
> - ✅ LC 199 Right Side View — only push the **last** node of each level into result
> - 🟡 **Try after Pattern 1 (Top-Down DFS)** — LC 116 Populating Next Right Pointers
> - 🔴 LC 994 Rotting Oranges — BFS on a 2D grid, not a tree. Needs grids deep dive.
```

### Anti-pattern

❌ Don't list problems untagged or without reasons:
```markdown
> 🧩 **Try these:** LC 102, LC 199, LC 116, LC 994
```

This is the format we used pre-May-2026 and it caused the LC 124 incident. Always tag, always reason.

---

## 🧾 TL;DR Section — Mandatory

End every DeepDive with a one-page TL;DR. This is what Kapil scans before an interview to refresh.

### Format

```markdown
## 🧾 TL;DR — One-Page Summary

- **[Topic]** = [one-line definition]
- **[Mental model phrase]** — [one-line restatement]
- **The universal skeleton:** `[code-style one-liner]`
- **N patterns:** [list]
- **[Key gotcha]:** [one line]
- **Tier 1 (Foundational N) you must master:** LC X1, LC X2, ...
- **[Lesson learned]** — [one line, optional]
```

Bullet points, no paragraphs. Each line is something Kapil can speak aloud in 5 seconds.

---

## 📂 File Naming

| File | Naming |
| --- | --- |
| Topic deep dive | `<topic>-fundamentals.md` (e.g., `trees-fundamentals.md`, `recursion-fundamentals.md`) |
| Cross-cutting concern | `<concern>-and-limits.md` or descriptive (e.g., `integer-overflow-and-limits.md`) |
| Standards | `notes-standards-deepdive.md` (this file) |

---

## ✅ Quality Checklist (run before delivering a DeepDive note)

Universal (from `AGENTS.md`):
- [ ] All code blocks pass universal-formatting rules
- [ ] All templates have English steps before code
- [ ] All LC references include the LC number
- [ ] All "Try these" callouts have ✅ / 🟡 / 🔴 tags
- [ ] No emojis outside the approved set

DeepDive-specific:
- [ ] 🎯 Goal section explains why Kapil is reading this
- [ ] 🚦 Difficulty tagging legend present
- [ ] 📖 Terminology table present
- [ ] 🧠 Mental Model section explicitly labeled
- [ ] 🎨 Style Habits split into Universal vs Context-Specific
- [ ] At least 6 worked walkthroughs (target 6-14), each covering a structurally unique shape
- [ ] Every walkthrough has all 5 parts: Problem statement / Brute force (2-3 sentences + complexity) / Intuition bridge / Steps + code / Transfers-to table
- [ ] ⚠️ Gotchas section with ❌/✅ wrong-vs-right code per gotcha
- [ ] 🗺️ Practice Plan in tiers (not weeks); Tier 1 = "Foundational N"
- [ ] Every problem in the practice plan is tagged
- [ ] Cross-references to companion docs are explicit
- [ ] 🧾 TL;DR at bottom in bullet form, scannable in 30 seconds
- [ ] Lesson-learned callouts ("May 2026: ...") embedded where relevant
- [ ] Reference-Only walkthroughs (if any) have the 🔴 banner + Common Bugs sub-section + build-up ladder

Visualization (per "🎨 ASCII Visualizations" section above):
- [ ] **At least 3-5 `### 🎨 Visual — <desc>` blocks** distributed across the doc
- [ ] Mental Model section has ≥1 visual (after the worked example)
- [ ] Every spatial/sequential pattern has its signature picture
- [ ] Every step-by-step walkthrough where state evolves has a trace visual
- [ ] **Each visual ends with a `KEY INVARIANT:` sentence** stating the algorithmic invariant the picture teaches
- [ ] Visuals are in plain fenced blocks (no language tag), ≤ 80 columns wide
- [ ] No visual duplicated across sections — duplicates are replaced by a link to the original

---

## 📌 How Kapil Uses This

When Kapil says *"prepare deep-dive notes for [topic]"*:

1. Confirm with him: *"Should I create `DeepDive/<topic>-fundamentals.md`?"*
2. Read `AGENTS.md` (master) + this file (DeepDive standards)
3. Outline the 13 sections; identify which patterns to cover
4. Confirm the outline with Kapil before writing 1500 lines
5. Write the doc
6. Run the quality checklist
7. Deliver with a summary of what's inside (don't paste the whole thing back)

If Kapil's request is ambiguous between DeepDive and Reference (e.g., "notes for streams") — ask: *"Is this a learn-from-zero deep dive or a method-syntax cheatsheet?"*

---

## 🔗 Companion Standards

- **Project root:** `AGENTS.md` — universal rules, folder structure, audience
- **Sister folder:** `Reference/notes-standards-reference.md` — when to write a Reference instead
- **Sister folder:** `Patterns/notes-standards-patterns.md` — when to write a per-problem dive instead
