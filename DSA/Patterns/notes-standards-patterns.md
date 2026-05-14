# Pattern / Problem Notes — Standards

> Conventions for writing per-problem deep dives in this `Patterns/` folder. Read this **after** the master `AGENTS.md` at the project root. Together they tell you exactly how to structure a new Pattern note.

---

## 🎯 Purpose

A Pattern note is a **single LeetCode problem** explored fully, with:

- The problem statement + constraints + examples
- Pattern recognition (when to think of this approach)
- Multiple approaches walked through brute → optimal
- Approach comparison table
- Variations & follow-ups (interviewer "what if...?" questions)
- Key takeaways
- Related notes & problems
- A self-test checklist

Pattern notes are the file Kapil opens when he wants to **fully understand one specific problem** — typically a "famous" problem like Group Anagrams, Two Sum, or LCA — and use it to anchor a whole pattern in his head.

**Length target:** 250–400 lines. Less than 250 → likely too thin. More than 400 → consider whether the problem needs a DeepDive instead, or whether you're over-explaining.

---

## 📐 Document Structure (Section Order)

Every Pattern note follows this template:

```
1.  H1 — Problem Name (no LC number in title)             [REQUIRED]
2.  Header blockquote: LC link, Pattern, Uses             [REQUIRED]
3.  📌 Problem (statement + examples + constraints)       [REQUIRED]
4.  🧠 Pattern Recognition (when to think of this)        [REQUIRED]
5.  ❌ Approach 1: Brute force / instinctive solution      [REQUIRED]
6.  ✅ Approach 2: Standard / improved solution            [REQUIRED]
7.  🚀 Approach 3: Optimal solution                        (when applicable)
8.  📊 Approach Comparison table                           [REQUIRED]
9.  🔁 Variations & Follow-ups (numbered "what ifs")       [REQUIRED]
10. 🎯 Key Takeaways (numbered list, 3-5 items)            [REQUIRED]
11. 🔗 Related Notes & Problems                            [REQUIRED]
12. 🧪 Quick Self-Test (checklist)                         [REQUIRED]
```

---

## 🏷️ Header Block

Every Pattern note starts with an H1 + a 3-line metadata blockquote:

```markdown
# Group Anagrams

> **LeetCode:** [49. Group Anagrams](https://leetcode.com/problems/group-anagrams/) — Medium
> **Pattern:** Hashable Key (see HashMap notes #7)
> **Uses:** String sorting, frequency arrays, HashMap of List

---
```

Required fields in the metadata:
- **LeetCode:** Linked LC number + title + difficulty
- **Pattern:** The DSA pattern this problem teaches (cross-reference to a Reference note's pattern number when possible)
- **Uses:** Comma-separated list of techniques/data structures touched

---

## 📌 Problem Section

State the problem in Kapil's own words (don't blindly paste the LC description — paraphrase for clarity), then give 2-3 examples and the typical constraints.

### Format

```markdown
## 📌 Problem

[1-2 sentence problem statement, as plain English]

> [Optional clarifying definition in a blockquote — e.g., "An anagram is..."]

### Examples

\`\`\`
Input:  ...
Output: ...
\`\`\`

\`\`\`
Input:  ...
Output: ...
\`\`\`

### Constraints (typical)

- `1 ≤ N ≤ 10^4`
- `0 ≤ value ≤ 100`
- ...
```

Always include 2-3 examples — at least one normal case, one edge case (empty / single element).

---

## 🧠 Pattern Recognition Section

This is where the pattern note earns its keep — teach Kapil **how to spot this pattern in a future problem**.

### Format

```markdown
## 🧠 Pattern Recognition

> **"[The signal phrase that triggers this pattern]"**
>
> Whenever you see [specific keyword/structure] in a problem, your first thought should be:
> 1. **[Question 1]** — [what to look for]
> 2. **[Question 2]** — [what to look for]
> 3. **[If yes → use this pattern]**

This is the **[Pattern Name]** pattern. See [companion reference] for the general framework.
```

The signal phrase should be a quote-style heuristic Kapil can mutter in his head while reading a future problem.

---

## 🪜 Approaches — Brute → Optimal (Each with English Steps + Code)

> **Established convention from `AGENTS.md`:** Every approach's code follows the **English-steps-before-code** rule.

### Approach naming

| Marker | Approach |
| --- | --- |
| `❌` | Brute force / first instinct (the one Kapil shouldn't ship) |
| `✅` | Standard interview answer (what most candidates use) |
| `🚀` | Optimal solution (the one to mention to flex, ship if asked) |

You can have 2 or 3 approaches. Two is fine for problems where there's no meaningful "optimal beyond standard."

### Format per approach

```markdown
## ❌ Approach 1: [Name] (Brute Force)

> **One-line characterization** — *"the instinctive first solution"* / *"the standard interview answer"* / *"the no-sorting-needed optimization"*

### Idea

[Plain English bullet list explaining the approach in 4-6 steps]

### Code

**Steps in plain English:**

1. **Step name** — what we do.
2. **Step name** — what we do.
3. ...

```java
// code with step-matching comments
```

### Walkthrough (optional but recommended)

\`\`\`
[ASCII trace of the algorithm on one of the examples]
\`\`\`

### Complexity

| | |
| --- | --- |
| Time | **O(...)** — explanation |
| Space | O(...) — explanation |

> **[Why this approach is X]** — [1-line takeaway connecting to the next approach]

---
```

### Walkthrough rules

- Use the same example from the Problem section so Kapil can connect input → trace → output
- Show state evolution line by line (HashMap contents, pointers, etc.)
- Wrap in a plain code block (` ``` ` no language) so Notion preserves spacing

---

## 📊 Approach Comparison Table

After all approaches, a one-glance comparison:

```markdown
## 📊 Approach Comparison

| Approach | Time | Space | Notes |
| --- | --- | --- | --- |
| 1. [Name] | **O(n²·k)** | O(n·k) | Brute force, fails large inputs |
| 2. [Name] | **O(n·k log k)** | O(n·k) | Cleanest code, standard interview answer |
| 3. [Name] | **O(n·k)** | O(n·k) | Optimal, slightly more code |

> **Interview tip:** Start with Approach 2, mention you can optimize to Approach 3. Code Approach 2 unless asked to optimize.
```

The closing tip is **mandatory** — tell Kapil which approach to actually code in an interview, and which to mention.

---

## 🔁 Variations & Follow-ups Section

> **Anticipate the interviewer's "what if...?" questions.** This section makes the pattern note transferable beyond the literal problem.

### Format

```markdown
## 🔁 Variations & Follow-ups

### **1. What if [constraint changes]?**
- [Implication on each approach]
- [Best approach under new constraint]

### **2. What if you need [related-but-different problem]?**
- This is **[other LC problem]** — [1-line approach hint]

### **3. ...**
```

Aim for 4-7 variations. Cover:
- Edge cases (very long strings, Unicode, empty inputs)
- Adjacent problems (same pattern, different shape)
- Performance constraints (huge n, tiny memory)
- Interviewer probes ("can you do it in O(1) space?")

---

## 🎯 Key Takeaways Section

A 3-5 item numbered list of **what Kapil should remember** from this problem.

### Format

```markdown
## 🎯 Key Takeaways

1. **[Pattern name in 3 words] → think [trigger keyword].** Pick a canonical form that's identical for items in the same group.
2. **[Technique]** is the [adjective] [property] for many [class of] problems.
3. **[Helper method]** is the cleanest way to [common operation].
4. Recognize the **[anti-pattern]**: [what beginners do] is always replaceable by [better approach].
```

These should be **transferable** — usable on future problems, not just this one.

---

## 🔗 Related Notes & Problems Section

Two subsections: cross-references to other notes, and similar-problems lists.

### Format

```markdown
## 🔗 Related Notes & Problems

### Notes referenced
- [Reference note name] → **Pattern #N [pattern name]** (general framework)
- [Reference note name] → **[specific section]** (related technique)

### Similar problems (same pattern)
- **[Problem Name]** (LC X) — [1-line variation note]
- **[Problem Name]** (LC X) — [1-line variation note]

### Adjacent problems (related but different pattern)
- **[Problem Name]** (LC X) — [why it's adjacent, not same]
- ...
```

Use this section to wire the Pattern note into the wider note system. After Notion-paste, these become clickable navigation in Kapil's hub.

---

## 🧪 Quick Self-Test Section

End every Pattern note with a self-test checklist Kapil can run mentally.

### Format

```markdown
## 🧪 Quick Self-Test

Without looking, can you:
- [ ] State the pattern name?
- [ ] Write Approach N (the recommended one) from scratch in 5 minutes?
- [ ] Explain why Approach M is faster than Approach N?
- [ ] Name the helper method used to [key operation]?
- [ ] Convert the optimal solution to handle [variation]?

If yes to all → you've internalized the [Pattern Name] pattern. ✅
```

5 questions is the sweet spot. Each should be specific to this problem's lessons.

---

## 💻 Code Formatting Rules

Same as universal rules in `AGENTS.md` and Reference standards:

- One statement per line
- Always brace blocks
- Spaces around operators
- No spaces inside generic angle brackets
- Working code, no `...` placeholders
- No imports
- Comments on their own line

**Plus the universal English-steps-before-code rule** — every approach code block has numbered English steps directly above.

---

## 🎨 Visual Hierarchy (subset of the master emoji palette)

| Emoji | Use For |
| --- | --- |
| `📌` | Problem section header |
| `🧠` | Pattern recognition section header |
| `❌` | First (brute / instinct) approach marker |
| `✅` | Second (standard) approach marker |
| `🚀` | Third (optimal) approach marker |
| `📊` | Approach comparison header |
| `🔁` | Variations & follow-ups header |
| `🎯` | Key takeaways header |
| `🔗` | Related notes header |
| `🧪` | Quick self-test header |
| `🏷️` | Example problems line in cross-references |
| `>` blockquote | Conceptual explanations, mental models, problem statements |

> **Don't use** decorative emojis. Stay within the approved master palette in `AGENTS.md`.

---

## 🧠 Tone & Pedagogy

- **Pattern-first, problem-second** — the goal is for Kapil to leave with a transferable pattern, not just one problem solved
- **Brute → optimal progression** — always start with what a beginner would write, then improve
- **Show the trade-off** — every approach should have a "this approach is X but not Y" sentence
- **Anchor in examples** — use the same input throughout the file when tracing approaches
- **Cross-reference, don't re-explain** — if the problem uses HashMap's "Hashable Key" pattern, link to that pattern note rather than re-explaining

---

## 📂 File Naming

| Pattern | Example |
| --- | --- |
| `<problem-slug>-problem.md` | `group-anagrams-problem.md`, `two-sum-problem.md` |
| Problem slug = lowercase-hyphenated | "Group Anagrams" → `group-anagrams` |

Use the **problem name** (not LC number) in the slug — easier for Kapil to find by topic.

---

## ✅ Quality Checklist (run before delivering a Pattern note)

Universal (from `AGENTS.md`):
- [ ] All code blocks pass universal-formatting rules
- [ ] All approach code has English steps before code
- [ ] All LC references include the LC number
- [ ] No emojis outside the approved set

Pattern-specific:
- [ ] Header has LC link + Pattern + Uses
- [ ] Problem statement is paraphrased (not verbatim from LC), with 2-3 examples and constraints
- [ ] Pattern Recognition section has a signal-phrase quote
- [ ] At least 2 approaches walked through (brute + standard); 3 if "optimal beyond standard" exists
- [ ] Each approach has Idea / Code (with English steps) / Complexity
- [ ] Walkthrough trace included for at least the recommended approach
- [ ] Approach Comparison table at the end of approaches with a closing "interview tip"
- [ ] Variations & Follow-ups has 4-7 numbered "what if" entries
- [ ] Key Takeaways has 3-5 numbered transferable lessons
- [ ] Related Notes & Problems split into Notes Referenced + Similar Problems + Adjacent Problems
- [ ] Quick Self-Test has 5 specific questions
- [ ] Cross-references to Reference / DeepDive notes are explicit

---

## 📌 How Kapil Uses This

When Kapil says *"do a pattern doc for LC X"* or *"prepare problem notes for [problem name]"*:

1. Confirm with him: *"Should I create `Patterns/<problem-slug>-problem.md`?"*
2. Read `AGENTS.md` (master) + this file (Pattern standards)
3. Identify the underlying pattern and what Reference note it cross-references
4. Outline the approaches (brute → standard → optimal if applicable)
5. Confirm the outline with Kapil before writing 300+ lines
6. Write the doc
7. Run the quality checklist
8. Deliver

If the "problem" Kapil is asking about is actually a whole family of problems (e.g., "two-pointer technique") → suggest a **DeepDive** instead.

---

## 🔗 Companion Standards

- **Project root:** `AGENTS.md` — universal rules, folder structure, audience
- **Sister folder:** `DeepDive/notes-standards-deepdive.md` — when to write a Deep Dive instead (for new topics needing top-to-bottom learning)
- **Sister folder:** `Reference/notes-standards-reference.md` — when to write a Reference cheatsheet instead (for method-syntax + pattern catalog)

---

## 📖 Canonical Example

The current canonical example of a well-structured Pattern note is:

**`Patterns/group-anagrams-problem.md`** — covers all 12 sections, three approaches (brute → sorted-key → frequency-array), full walkthrough trace, 6 variations, 5 takeaways, 12+ related problems, 5-question self-test.

Use it as a template when creating a new Pattern note.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created** — Pattern/Problem standards split out from `Reference/notes-standards-reference.md` (which previously held a TBD placeholder for Pattern standards). Codified based on `group-anagrams-problem.md`'s structure. |
| May 2026 | **English-steps-before-code rule** propagated from master `AGENTS.md` — applies to all approach code blocks. |
