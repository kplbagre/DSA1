# DSA — Subdomain Standards

> **For any AI assistant working in this folder:** Read the root `../AGENTS.md` first for universal rules, then read THIS file for DSA-specific conventions. Both apply when generating any DSA content.

---

## 🎯 Purpose

DSA notes are written for **revision under interview-prep pressure**. The reader should be able to:

1. Skim the table of contents and find what they need in seconds
2. Read code that they could paste into a LeetCode submission and have it compile
3. Cross-jump between deep dives and cheat-sheet references

---

## 📁 Folder Structure

```
DSA/
├── AGENTS.md                              ← THIS FILE
│
├── DeepDive/                              ← In-depth study notes (read top-to-bottom once)
│   ├── notes-standards-deepdive.md        ← Detailed conventions for DeepDive notes
│   ├── trees-fundamentals.md
│   ├── recursion-fundamentals.md
│   ├── graphs-fundamentals.md
│   ├── backtracking-fundamentals.md
│   └── integer-overflow-and-limits.md
│
├── Reference/                             ← Compact cheatsheets (live in during practice)
│   ├── notes-standards-reference.md       ← Detailed conventions for Reference notes
│   ├── trees-reference.md
│   ├── hashmap-section-updated.md
│   ├── set-section-updated.md
│   ├── string-operations-reference.md
│   ├── arraydeque-and-queue-reference.md
│   ├── lambdas-for-dsa-reference.md
│   ├── code-style-for-dsa-reference.md
│   └── dsa-collections-notes.md
│
└── Patterns/                              ← Per-problem deep dives (one problem per file)
    ├── notes-standards-patterns.md        ← Detailed conventions for Pattern notes
    ├── group-anagrams-problem.md
    └── max-path-sum-binary-tree-problem.md
```

---

## 🧭 The Three Note Types

| Folder | Type | When to use | Length target | Standards file |
| --- | --- | --- | --- | --- |
| `DeepDive/` | **Deep dive (in-depth study)** | New topic not yet internalized; needs mental models + step-by-step explanations | 800–1500 lines | `DeepDive/notes-standards-deepdive.md` |
| `Reference/` | **Reference (cheatsheet)** | Method syntax + DSA patterns for a known data structure / language feature | 300–700 lines | `Reference/notes-standards-reference.md` |
| `Patterns/` | **Pattern (per-problem dive)** | Single LeetCode problem walkthrough with multiple approaches (brute → optimal) | 250–400 lines | `Patterns/notes-standards-patterns.md` |

> **Decision question:** *"Is this a topic Kapil is learning fresh (DeepDive), a syntax cheatsheet to revise daily (Reference), or one specific problem to fully understand (Pattern)?"*

---

## 💻 Code Conventions (DSA-specific)

- **Language:** Java unless explicitly stated otherwise.
- **Imports:** Do NOT declare imports inside code blocks. Assume `java.util.*` is available (HashMap, ArrayList, Arrays, Collections, Deque, etc.).
- **Methods:** Write complete, runnable methods — no `// TODO`, no abstract signatures.
- **Class scaffolding:** When showing a `class Solution { ... }` pattern, include the class wrapper so the code compiles when pasted into LeetCode.

---

## 🚦 Difficulty Tagging Legend

Every problem reference inside a 🧩 **Try these** callout must be tagged with one of these markers:

| Tag | Meaning |
| --- | --- |
| ✅ | **Try now** — prerequisites covered, comfortable starting cold |
| 🟡 | **Try after the prerequisite ladder** — needs the build-up first |
| 🔴 | **Reference only / Senior+** — read theory, don't attempt unless time permits |

> **Lesson learned the hard way (May 2026):** Kapil burned an hour on LC 124 (Binary Tree Maximum Path Sum) by attempting it before completing the bottom-up DFS + two-purpose recursion ladder. Difficulty tagging exists to prevent that mistake.

---

## 🏷️ LeetCode Numbering

Every problem reference must include the LeetCode number:

- ✅ `LC 102 Level Order Traversal`
- ❌ `Level Order Traversal` (number missing)
- ❌ `Leetcode #102` (use the canonical `LC ###` prefix)

For non-LC problems (HackerRank, GFG, etc.), use a platform prefix: `HR Two Sum`, `GFG Subset Sum`.

---

## 🎨 DSA-Specific Emoji Additions

In addition to the universal palette in the root `../AGENTS.md`, DSA notes may use:

| Emoji | Use for |
| --- | --- |
| `🟢` `🟡` `🔴` | Difficulty tags (Try Now / Try After / Reference Only) |
| `🚦` | Difficulty tagging legend header |
| `🏷️` | Example-problems line in Reference notes |

---

## 🧪 DSA-Specific Quality Checklist

Extends the universal checklist in the root `../AGENTS.md`:

- [ ] Folder is correct (DeepDive vs Reference vs Patterns)
- [ ] Folder-specific standards file (`notes-standards-*.md`) checklist passed
- [ ] All embedded problem references include the `LC ###` number
- [ ] All 🧩 "Try these" callouts have ✅ / 🟡 / 🔴 difficulty tags
- [ ] Code blocks use Java idioms (no inline imports declared)
- [ ] If introducing a pattern that's already covered elsewhere, cross-reference rather than duplicate

---

## 📌 How to Use This (workflow)

When the user asks for *"deep-dive notes on [topic]"* / *"a reference for [topic]"* / *"a pattern doc for LC X":*

1. Identify the folder (DeepDive / Reference / Patterns) using the decision question above
2. Read the root `../AGENTS.md` for universal rules
3. Read THIS file for DSA-specific rules
4. Read the folder's `notes-standards-*.md` file for note-type-specific rules
5. Write the note
6. Run ALL three checklists (universal + DSA + note-type)
7. Deliver

If something needs to deviate from the standard for a specific topic, **call it out explicitly** in the response with reasoning.

---

### Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Initial DSA-specific AGENTS.md created.** Split out from the monolithic root AGENTS.md when the repo was migrated from Notion-paste workflow to GitHub. |
| May 2026 | **English-steps-before-code rule** stays in the root universal AGENTS.md (applies across all subdomains). |
| May 2026 | **Difficulty tagging legend** (✅ / 🟡 / 🔴) and **LeetCode numbering** rule live here (DSA-specific). |
