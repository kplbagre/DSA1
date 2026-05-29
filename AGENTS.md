# kapil-kb — Workspace Standards (Universal)

> **For any AI assistant working in this repo:** Read this file before generating any content. Then read the `AGENTS.md` inside the specific subdomain folder (e.g., `DSA/AGENTS.md`) you are working in. Both files apply.

---

## 👤 About This Repo

This is Kapil's personal **interview-preparation knowledge base**, organized by domain. Each subdomain has its own folder with its own subdomain-specific `AGENTS.md`:

- `DSA/` — Data Structures & Algorithms (active)
- `LLD/` — Low-Level Design (planned)
- `HLD/` — High-Level Design (planned)
- `JavaBackend/` — Java/Spring backend topics (planned)

**Audience:** Kapil — a Walmart Java engineer preparing for senior backend / SDE-3 interviews.

**Output medium:** GitHub-rendered Markdown (`.md` files). All code blocks must use language-tagged fences (` ```java `, ` ```sql `, etc.) so GitHub renders syntax highlighting.

**Tone:** Direct, revision-friendly, "smart-friend explaining things." Not academic, not preachy. Notes are written to be re-read under interview-prep pressure — so prioritize scannability, concrete examples, and named patterns.

---

## 📁 Workspace Structure

```
kapil-kb/
├── AGENTS.md                          ← THIS FILE (universal rules)
│
├── DSA/                               ← Active subdomain
│   ├── AGENTS.md                      ← DSA-specific rules
│   ├── DeepDive/                      ← In-depth study notes
│   ├── Reference/                     ← Compact cheatsheets
│   └── Patterns/                      ← Per-problem deep dives
│
├── LLD/                               ← Future
│   ├── AGENTS.md
│   └── ...
│
├── HLD/                               ← Future
│   ├── AGENTS.md
│   └── ...
│
└── JavaBackend/                       ← Future
    ├── AGENTS.md
    └── ...
```

**Workflow for an AI assistant:** When asked to write a new note —

1. Identify the subdomain folder (DSA, LLD, etc.)
2. Read THIS file for universal rules
3. Read that folder's `AGENTS.md` for subdomain-specific rules
4. Read any deeper standards file (e.g., `DSA/DeepDive/notes-standards-deepdive.md`)
5. Write the note following all applicable rules
6. Run the quality checklists (universal + subdomain + note-type) before delivering

---

## 🌐 Universal Rules

These apply to every note in every subdomain.

### 1. Markdown Code-Block Hygiene

Inside fenced code blocks (` ```language ... ``` `), all code must follow these rules:

| Rule | Example |
| --- | --- |
| **Always declare the language** on the opening fence | ` ```java ` not just ` ``` ` |
| **One statement per line** | Never `if (cond) return val;` — split across lines |
| **Always brace blocks** | Even single-statement `if`/`for`/`while` bodies |
| **Spaces around operators** | `int x = a + b;` not `int x=a+b;` |
| **No spaces inside generics** | `Map<String, List<Integer>>` ✅ not `Map < String , ... >` ❌ |
| **Working code only** | No `...` placeholders, no pseudo-code |
| **Inline comments on their own line** | Put `// note` on the line ABOVE the statement, not at end-of-line |

✅ **Good:**

```java
Map<Integer, Integer> map = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    int complement = target - nums[i];
    if (map.containsKey(complement)) {
        return new int[]{ map.get(complement), i };
    }
    map.put(nums[i], i);
}
```

❌ **Bad** (multi-statement, no braces, no spacing):

```java
Map<Integer,Integer> map=new HashMap<>();
for(int i=0;i<nums.length;i++){
if(map.containsKey(target-nums[i]))return new int[]{map.get(target-nums[i]),i};
map.put(nums[i],i);}
```

---

### 2. English Steps BEFORE Code (for templates and patterns)

Whenever presenting a **template** or **pattern code block** (something the reader might use as scaffolding for many problems), prepend a numbered English-steps explanation, then write the code with comments matching the steps.

**Format:**

````markdown
**Steps in plain English:**

1. **Step one** — what we do and why.
2. **Step two** — what we do and why.
3. **Step three** — what we do and why.

```java
public ReturnType solve(...) {
    // Step 1 — short comment matching the English step
    ...

    // Step 2 — short comment matching the English step
    ...
}
```
````

This applies to **every template, pattern, and reusable skeleton.** Single-line snippets and one-off demos don't need it.

> **Why this rule exists:** A reader skimming under exam pressure should grok the structure from the English steps alone, *then* read the code to fill in syntax details. Code-first templates require the reader to mentally re-parse what the author already understood.

---

### 3. Cross-References Between Docs

When a doc references a concept covered elsewhere, link explicitly using a **relative path from the current file**:

```markdown
Full explanation in **`DSA/DeepDive/integer-overflow-and-limits.md`**.
See **HashMap notes #7 — Hashable Key**.
Companion file: **`DSA/Reference/trees-reference.md`** (compact revision file).
```

Relative paths make the link clickable in any modern markdown renderer (GitHub, IDEs, Obsidian) and form a navigable knowledge graph.

---

### 4. Lesson-Learned Callouts

When the author hits a real failure (an hour lost on a problem, a subtle bug missed, a wrong mental model), embed a **dated callout** in the relevant doc so the lesson stays attached to the topic:

```markdown
> **Lesson learned the hard way (May 2026):** [the lesson, in 1–2 sentences]
```

Future-Kapil reviewing the doc gets a built-in "don't make this mistake again" reminder.

---

### 5. Approved Emoji Set

To keep notes consistent and scannable, only use emojis from this **universal palette**, plus any **subdomain additions** declared in the folder's `AGENTS.md`.

**Universal emoji palette:**

| Emoji | Use for |
| --- | --- |
| `🎯` | Goal / "why you're reading this" header |
| `📖` | Terminology / definitions header |
| `🧠` | Mental model section |
| `🎨` | Style habits section |
| `🌐` | Universal habits sub-header |
| `🔧` | Context-specific habits sub-header |
| `🧭` | Patterns / decision-framework section |
| `🔬` | Worked walkthroughs section |
| `⚠️` | Gotchas / "silent bug hall of fame" header |
| `🗺️` | Practice plan / roadmap header |
| `🧾` | TL;DR header |
| `🪜` | Prerequisite ladder / build-up section |
| `🐞` | Common bugs callout |
| `🧩` | "Try these problems" embedded callout |
| `⭐` | Top-3 markers (highest-priority items in a list) |
| `⚡` | Cheat sheet header |
| `🔹` | Major H2 sub-section in Reference notes |
| `🚀` | Optimal-approach marker |
| `❌` `✅` | Wrong vs Right code comments inside code blocks |
| `🔄` | Changelog header |

**Do NOT use** decorative emojis like 🎉, 💡, 🔥, 👍, 🙌, 📢. Keep notes professional.

---

### 6. ASCII Visualizations — Draw the Spatial / Sequential Concept

> **Established May 2026** after the "visualize all deep dives" pass. When a concept has spatial, sequential, or stateful structure, **a picture beats prose every time**. Drawing it ASCII-art-style — inside a fenced code block — is the standard.

**The rule:**

> If a concept has *any* of these properties, it MUST be paired with an ASCII visualization in the doc that introduces it:
> - **Spatial** (grids, trees, arrays, board states, matrices)
> - **Sequential** (call stacks, recursion trees, traversal orders, pointer animations)
> - **Stateful evolution** (sliding windows, queue contents, union-find forests, BST bounds tightening)
> - **Topological** (graph adjacency, cycle detection, DSU components)

**Why ASCII (not embedded images):**

| Reason | Detail |
| --- | --- |
| **Renders everywhere** | GitHub, IntelliJ, Obsidian, VS Code preview — all show monospace fenced blocks identically |
| **No broken-link risk** | The diagram lives in the same file as the explanation; never goes 404 |
| **Walmart-network-safe** | No external image hosts to whitelist or worry about |
| **Diff-friendly** | Changes to a diagram show as line-level diffs in git |
| **Editable** | Future-Kapil can tweak the diagram without launching a drawing tool |

**Format conventions (memorize):**

1. **Wrap the diagram in a plain fenced code block** (no language tag — it's ASCII art, not code):
    ````markdown
    ```
    (your diagram here, monospace alignment matters)
    ```
    ````
2. **Section header** — introduce the diagram with the heading `### 🎨 Visual — <one-line description>` (uses the existing 🎨 emoji from the approved palette)
3. **Pair the picture with the invariant** — every diagram closes with a short callout that names the **algorithmic invariant** it teaches (e.g., *"KEY INVARIANT: the queue holds at most two adjacent levels"*). The picture without the invariant is half the lesson.
4. **Use box-drawing characters consistently** — `┌ ┐ └ ┘ ├ ┤ ┬ ┴ ┼ ─ │ ━ ┃` for boxes; `→ ← ↑ ↓` for arrows; `▶ ◀ ▼ ▲` for emphasis arrows; `◆ ● ○ ✓ ✗` for markers
5. **Keep widths ≤ 80 columns** so the diagram doesn't horizontal-scroll on narrow viewports

**The "Picture + Invariant" pattern:**

````markdown
### 🎨 Visual — <what we're showing>

```
<the ASCII diagram>

<optional step-by-step animation>

KEY INVARIANT:
   <one or two lines naming the algorithmic property the picture teaches>
```
````

**Examples from the existing knowledge base** (read these for style reference):

- `DSA/DeepDive/trees-fundamentals.md` — BFS queue animation, BST bounds propagation, LCA three cases
- `DSA/DeepDive/graphs-fundamentals.md` — adjacency list vs matrix, BFS pond-ripple, DSU path compression
- `DSA/DeepDive/recursion-fundamentals.md` — fib explosion vs memoized pruning, merge-sort split-and-combine
- `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md` — worm animation, atMost(K) inclusion-exclusion
- `DSA/DeepDive/integer-overflow-and-limits.md` — 32-bit int number line + circular wrap

**When NOT to draw:**

- One-line code snippets (`set.add(5)`) — overkill
- Pure-syntax reference tables — the table IS the visualization
- Concepts that are inherently algebraic, not spatial (e.g., the `==` vs `.equals()` semantics)

> **Cross-reference:** the note-type standards files (`DSA/DeepDive/notes-standards-deepdive.md`, `DSA/Reference/notes-standards-reference.md`) extend this rule with specifics about *where* in the doc the visuals must appear and how many are expected.

---

### 7. Git / Commit Conventions

- **Do not commit unless explicitly asked.** If unclear, ask the user first.
- Use conventional commit prefixes when no ticket is involved: `chore:`, `fix:`, `refactor:`, `docs:`, `feat:`.
- **Do not add AI-tool co-author trailers** (e.g., `Co-Authored-By: Claude`, `Co-Authored-By: Cursor`, `Co-Authored-By: Copilot`). The author is Kapil.
- Keep commit messages factual — focus on the "why," not just the "what."
- Pass multi-line commit messages via heredoc (`git commit -m "$(cat <<'EOF' ... EOF)"`) so formatting is preserved.

---

### 8. First-Use Term Gloss — Define Unfamiliar Words Inline

> **Established May 2026** after Kapil flagged that words like *dispatch* were being used repeatedly without ever being explained. Future-Kapil reviewing a note under interview-prep pressure should never need to context-switch to look up a word.

**The rule:**

> When a doc introduces a technical term, jargon, or domain word the reader might not immediately recognize, provide a **one-line plain-English explanation in parentheses at the term's first appearance in that doc**.

**Format:**

```markdown
The **DispatcherServlet** (Spring MVC's front-door servlet that routes every incoming HTTP request to the right controller method — like a receptionist deciding which department handles the call) is the entry point of every Spring web request.
```

**Specifics:**

- **First use only.** Subsequent mentions of the same term in the same doc are bare. The gloss is a teaching aid, not a sticker.
- **Keep it to one line.** ≤ 20 words. If a term needs more, it deserves its own paragraph or §Terminology row, not a parenthetical.
- **Plain English, not formal definition.** A formal definition explains *what it is*; a gloss explains *what it does in the reader's head*. Lean on everyday analogies — "like a receptionist…", "like a phone directory…", "like a checkout queue at a grocery store…".
- **Skip when redundant.**
  - If the doc has a §Terminology table that defines the term earlier — no gloss needed.
  - If the term is already universal vocabulary (loop, array, function, class, method) — no gloss.
  - If the term is the doc's main subject (a note titled "BFS" doesn't gloss "BFS") — no gloss.
- **When in doubt, gloss it.** The cost of a redundant parenthetical is 10 seconds of skim; the cost of an unexplained term is a derailed review session.

**Examples (good vs bad):**

✅ **Good:**

```markdown
A **bean** (a Java object that Spring creates, configures, and manages for you — instead of you calling `new` yourself) is registered in the application context.
```

❌ **Bad** (formal-definition style, too long, no everyday hook):

```markdown
A **bean** (an object that is instantiated, assembled, and managed by a Spring IoC container as defined by the Spring framework's dependency injection mechanism) is registered in the application context.
```

✅ **Good:**

```markdown
Spring uses **AOP** (aspect-oriented programming — a way to inject cross-cutting concerns like logging or transactions into your methods without writing the code inline) to wrap your transactional methods.
```

❌ **Bad** (no gloss at all on first use):

```markdown
Spring uses AOP to wrap your transactional methods.
```

> **Cross-reference:** This rule layers on top of the §Terminology table convention in `DSA/DeepDive/notes-standards-deepdive.md`. The terminology table handles foundational vocabulary; the first-use gloss handles incidental terms that crop up mid-explanation and shouldn't break flow.

---

## 🧪 Universal Quality Checklist

Run before delivering ANY note. Subdomain-specific checklists (in each folder's `AGENTS.md`) extend this one.

- [ ] Correct subdomain folder (DSA / LLD / HLD / JavaBackend)
- [ ] Read the subdomain's `AGENTS.md` and the relevant deeper standards file
- [ ] All code blocks pass the formatting rules (language-tagged fence, one statement per line, always braced, spaced operators)
- [ ] All templates have **English steps before** the code
- [ ] All cross-references use explicit relative paths
- [ ] No emojis outside the universal palette + declared subdomain additions
- [ ] Lesson-learned callouts have a date
- [ ] Every potentially-unfamiliar term is glossed in parentheses at first use (Rule 8)
- [ ] Subdomain-specific checklist passed (see subdomain `AGENTS.md`)
- [ ] Note-type-specific checklist passed (see e.g. `DSA/DeepDive/notes-standards-deepdive.md`)

---

## 🔄 Updating Standards

When a new convention emerges from a real conversation:

1. **Update the relevant standards file** — this one if the rule is universal, the subdomain `AGENTS.md` if local, the note-type standards file if narrower
2. **Update affected existing docs** to follow the new rule — don't leave the codebase inconsistent
3. **Note the update in the file's changelog** table

### Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Repository created.** Migrated from local Notion-paste workflow to GitHub. Split monolithic AGENTS.md into universal (this file) + per-subdomain rules. |
| May 2026 | **AI-agnostic phrasing.** Removed references to specific AI tools so any assistant cloning this repo can follow the conventions and produce the same quality of notes. |
| May 2026 | **Rule 8 added — First-Use Term Gloss.** Every potentially-unfamiliar technical term must be glossed in parentheses at first use in a doc, with a one-line plain-English explanation. Triggered by Kapil flagging that words like *dispatch* were used repeatedly across notes without ever being explained. |
