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
├── Implementation/                        ← Implementation discipline (building blocks that prevent bugs)
│   ├── java-coding-traps.md               ← 9 trap families (deep dive)
│   ├── java-coding-traps-reference.md     ← Quick reference for traps
│   ├── simulation-patterns.md             ← 7 building blocks for simulation problems (deep dive)
│   └── simulation-patterns-reference.md   ← Quick reference for simulation
│
├── Interview/                             ← Interview playbooks (pattern recognition + problem mapping)
│   ├── notes-standards-interview.md       ← Detailed conventions for Interview notes
│   ├── arrays-and-hashing.md
│   ├── two-pointers-and-sliding-window.md
│   ├── strings.md
│   ├── linked-list.md
│   ├── stacks-and-queues.md
│   ├── trees-and-bfs-dfs.md
│   ├── binary-search.md
│   ├── heaps.md
│   ├── backtracking.md
│   ├── intervals.md
│   ├── greedy.md
│   ├── dp.md
│   ├── graphs.md
│   └── index.md                          ← Master decision tree (which file to open)
│
└── Patterns/                              ← Per-problem deep dives (one problem per file)
    ├── notes-standards-patterns.md        ← Detailed conventions for Pattern notes
    ├── group-anagrams-problem.md
    └── max-path-sum-binary-tree-problem.md
```

---

## 🧭 The Five Note Types

| Folder | Type | When to use | Length target | Standards file |
| --- | --- | --- | --- | --- |
| `DeepDive/` | **Deep dive (in-depth study)** | New topic not yet internalized; needs mental models + step-by-step explanations | 800–1500 lines | `DeepDive/notes-standards-deepdive.md` |
| `Reference/` | **Reference (cheatsheet)** | Method syntax + DSA patterns for a known data structure / language feature | 300–700 lines | `Reference/notes-standards-reference.md` |
| `Implementation/` | **Implementation discipline** | Building blocks that prevent coding bugs under pressure — Java traps, simulation templates, parsing patterns | Deep dive: 500–800 lines; Reference: 200–350 lines | (follows DeepDive + Reference conventions) |
| `Interview/` | **Interview playbook** | Pattern recognition + problem mapping — "I see this problem, which pattern do I use?" | 400–600 lines | `Interview/notes-standards-interview.md` |
| `Patterns/` | **Pattern (per-problem dive)** | Single LeetCode problem walkthrough with multiple approaches (brute → optimal) | 250–400 lines | `Patterns/notes-standards-patterns.md` |

> **Decision question:** *"Is this a topic Kapil is learning fresh (DeepDive), a syntax cheatsheet to revise daily (Reference), a coding discipline / trap to drill into muscle memory (Implementation), a pattern-to-problem bridge for interview prep (Interview), or one specific problem to fully understand (Pattern)?"*

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

## 🧩 Inline Drill Convention

> **Established May 2026** after Kapil identified that reading notes without practicing leads to the same interview mistakes. Drills force active recall — you write the safe version before moving on, not just read it.

**The rule:**

> Every deep dive and implementation note that introduces a **building block, template, or trap** must include an inline drill callout immediately after the section. The drill forces the reader to write the safe version from memory before reading the next section.

**Format:**

```markdown
> 🧩 **Drill — do this NOW before reading further:**
> On a blank notepad (no peeking), write:
> 1. [specific thing to write from memory]
> 2. [specific thing to write from memory]
>
> Then compare with the ✅ version above. [What to do if you got it wrong.]
```

**Rules for good drills:**

| Rule | Why |
| --- | --- |
| **Specific** — "write safe midpoint of lo and hi" | Not "think about overflow" |
| **Small** — 1-3 lines of code, takes 60 seconds | Not a full LeetCode problem |
| **Testable** — reader can immediately compare with the answer above | No ambiguity about right/wrong |
| **Covers the trap** — forces the reader to write the SAFE version | Not just the concept |

**Where they go:**

| Note type | Drill placement |
| --- | --- |
| Deep dive / Implementation deep dive | **Inline** — after each section / family / building block |
| Reference / Implementation reference | **Consolidated** — one "🧩 Speed Drill" section near the end (before cross-references) |

**Reference files use a consolidated drill** because their purpose is quick scanning — inline drills would break the flow. The speed drill should be timed (e.g., "3 minutes", "5 minutes") and have a scoring rubric.

---

## 🧪 DSA-Specific Quality Checklist

Extends the universal checklist in the root `../AGENTS.md`:

- [ ] Folder is correct (DeepDive vs Reference vs Patterns vs Implementation)
- [ ] Folder-specific standards file (`notes-standards-*.md`) checklist passed
- [ ] All embedded problem references include the `LC ###` number
- [ ] All 🧩 "Try these" callouts have ✅ / 🟡 / 🔴 difficulty tags
- [ ] Code blocks use Java idioms (no inline imports declared)
- [ ] If introducing a pattern that's already covered elsewhere, cross-reference rather than duplicate
- [ ] Deep dive / implementation notes have inline 🧩 drills after each section
- [ ] Reference / implementation reference notes have a consolidated 🧩 Speed Drill before cross-references

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
| May 2026 | **Implementation folder added.** New `Implementation/` folder for coding discipline notes (Java traps, simulation patterns). Folder structure and note type table updated. |
| May 2026 | **Inline drill convention added.** Deep dives get inline 🧩 drills after each section; references get a consolidated Speed Drill at the end. Quality checklist updated with two new items. |
| May 2026 | **Interview folder added.** New `Interview/` folder for interview playbook notes — pattern recognition + problem mapping. A fifth note type bridging Reference (syntax) and problem-solving. 6 topic files: arrays-and-hashing, two-pointers-and-sliding-window, strings, linked-list, stacks-and-queues, trees-and-bfs-dfs. Note type table updated to "Five Note Types." |
| May 2026 | **Interview folder expanded.** Added 5 new topic files (binary-search, heaps, backtracking, intervals, greedy) + master index. Added 🔧 Essential Methods sections to all 13 Interview Playbook files. Total: 13 topic files + 1 standards + 1 index = 15 files in Interview/. |
