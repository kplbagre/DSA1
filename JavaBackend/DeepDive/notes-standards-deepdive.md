# JavaBackend Deep-Dive — Note Standards

> **For any AI assistant writing a deep-dive in `JavaBackend/DeepDive/` (or any track's DeepDive subfolder except `Spring/`):** Read this file in full before writing line one. The `Spring/` track uses the 8-section arc defined in `Spring/spring-10-hour-plan.md` — that is a separate standard. This file governs everything else.
>
> **Read order:** `../../AGENTS.md` (universal) → `../AGENTS.md` (JavaBackend) → **this file** → write.

---

## 🎯 The 3 Core Principles

These drive every standard below. If you forget everything else, remember these three.

**Principle 1 — Problem before solution. Always.**
Never introduce a concept cold. Start with the painful world that existed before it. The reader must feel the problem before they see the solution. If they don't feel the pain, the cure doesn't stick. A note that opens with the solution is a definition. A note that opens with the problem is understanding.

**Principle 2 — Mental model before code.**
Code is evidence that a mental model works. It is not the mental model itself. Build the mental model in plain English first. Then write code to validate it. A reader who only understands the code will be lost the moment they hit a variant they haven't seen before. A reader who has the mental model will derive the code.

**Principle 3 — Every concept must answer 4 questions.**
1. **What is it?** (Terminology + Mental Model sections)
2. **Why does it exist — what was broken/painful before it?** (Problem section + Level 1 of Build-up)
3. **How does it actually work internally — JVM level where relevant?** (Level 2 of Build-up)
4. **Where does it bite you in production?** (Misconceptions + Footguns sections)

A note that answers only questions 1 and 2 is a good blog post. A note that answers all 4 is understanding you carry into production.

---

## 📐 Note Structure — Full Template

Every deep-dive note follows this structure, **in this order**. Do NOT reorder sections. Do NOT skip sections — if a section is genuinely thin, merge it with an adjacent one and note that in the Changelog, but don't silently drop it.

````markdown
# ☕ [Concept Name] — Deep Dive

> [One sentence: what does reading this note give the reader? Make it concrete.
>  Example: "After this note you can explain exactly why two equal objects
>  must have the same hashCode — and exactly what breaks in a HashSet if they don't."]

---

## 🎯 The Problem This Solves

A paragraph (2–4 sentences) answering:
"Without [concept], _____ is impossible / broken / painful."

Show the world BEFORE this concept existed. Show the friction.
The reader must feel the pain before the solution arrives.
This section should make the reader want to read on.

---

## 📖 Terminology

Table — two columns — Term | Plain-English definition.

| Term | Definition |
|---|---|
| **[term]** | [Plain-English definition. One sentence. No jargon beyond what's already in the table.] |

**Rule:** EVERY term that appears in the Build-up section must appear
in this table FIRST. No exceptions. If a term is introduced mid-note
(outside Build-up), gloss it in parentheses at first use per universal
AGENTS.md Rule 8.

---

## 🧠 Mental Model

One paragraph. No code. No jargon beyond what's in the Terminology table.
The mental model the reader should hold in their head permanently — the picture
that makes the code make sense, not the code itself.

Closes with:
> "If you can say [X] out loud without looking at notes, you have [concept]."

This gives the reader a concrete self-test.

---

## 🎨 Visual — [what we're showing]

Required when the concept is spatial, structural, stateful, or sequential.
See universal `../../AGENTS.md` Rule 6 (ASCII Visualizations) for full requirements.

```
[ASCII diagram — ≤ 80 columns wide, box-drawing characters]

KEY INVARIANT:
   [One or two lines naming the algorithmic/design property the picture teaches]
```

If the concept is purely algebraic / definitional and has no spatial/structural
reality, this section can be omitted — note the omission in a comment.

---

## 🪜 Build-up

The longest section. Three levels — never collapse into fewer.

### Level 1 — The naive approach (and why it fails)

Show what a reasonable, competent developer would do WITHOUT knowing this concept.
Walk through the naive solution step by step.
Then show EXACTLY where and how it fails:
  - compile error (show the error message)
  - runtime exception (show the stack trace or the wrong output)
  - silent bug (show correct-looking output that is actually wrong)
  - performance cliff (show the Big-O or the benchmark difference)

Never mock the naive approach. It is the natural starting point and respecting it
is what makes the reader trust the note.

### Level 2 — The real mechanism

Introduce the concept as the direct solution to Level 1's specific failure.
Walk through HOW it works internally — not just what it does.

For JVM-level concepts: include the JVM Layer Callout (see Standard 1 below).
For library/framework concepts: trace through what the JDK/Spring source actually does.

This is the core of the note. It should be the longest level.

### Level 3 — The subtleties

The edge cases, the non-obvious behaviors, the second-order effects.
The "but what if I pass null?", "what about subclasses?",
"what happens under concurrent access?" cases.

These are what separate someone who read the docs from someone who has
debugged it in production. Don't skip this level to save space — it's
where the senior-level knowledge lives.

---

## ⚠️ Common Misconceptions

Table — two columns.

| You might think... | But actually... |
|---|---|
| [A wrong belief the reader is likely to bring into this note] | [The correct reality] |

3–5 rows minimum.

**Misconceptions ≠ Footguns:**
- A **misconception** is a wrong mental model the reader arrives with. It exists before they touch the code.
- A **footgun** is a trap they walk into even WITH a correct mental model, because the API design or JVM behavior is surprising.

Both sections are needed. Neither replaces the other.

---

## 🐞 Production Footguns

2–3 real production bugs. For each footgun:

> **Footgun: [short name — ≤ 5 words]**
> **Cost:** [Silent data corruption / OOM / Race condition / Performance cliff / Silent failure / Data loss]
>
> [1–2 sentences: in a production context, this pattern caused this symptom because this mechanism.
>  Use realistic Walmart-adjacent context: high-traffic API, Kafka consumer, Spring singleton, batch job.]

Followed by:

```java
// ❌ The trap
[code showing the problematic pattern]

// ✅ The fix
[code showing the correct pattern]
```

Each footgun MUST have both ❌ and ✅ code. Never show just the trap without the fix.

---

## 🔗 Concept Web

How this note connects to other notes in the KB. Turns isolated files into a navigable graph.

| Connects to | How |
|---|---|
| `[relative/path/to/note.md]` | [One-line relationship — what the connection IS, not just that it exists] |

3–5 entries minimum. Use relative paths from the current file.
If a target note doesn't exist yet, still list it with its EXPECTED path —
it becomes a pointer for what to write next.

---

## 🎙️ Interview Deep Questions

5–6 questions that test whether the reader truly internalized the Build-up.
These are NOT surface-level definition questions — they are the kind of
questions that can only be answered correctly after reading the Build-up
and understanding the internals.

**Rules:**
- 5–6 questions per note (not 12 — quality over volume)
- Each question targets a specific internal mechanism from the Build-up
- Answers are 4–6 sentences, referencing concrete numbers and JVM behavior
- The answer should make an interviewer say "this person actually understands it"
- No company-specific split (no "EPAM-asked" / "commonly asked") — these notes are universal
- Questions should be phrased as an interviewer would ask them

**Format:**

```markdown
**Q1. [Question as an interviewer would phrase it]**

> [Full answer — 4–6 sentences. Reference specific internals, concrete numbers,
>  and JVM behavior from the Build-up. Not a definition — a demonstration
>  of understanding.]
```

---

## 🎙️ Say It in 60 Seconds

The interview answer, distilled from everything above.
This section is at the END — not the top — because it only makes sense
after the reader has built the mental model. It is a distillation, not
an introduction.

Three parts, labeled explicitly:

> **Part 1 — What (10s):** [The one-sentence answer to "what is X?"]
>
> **Part 2 — How/Why (30s):** [The mechanism and the reason it was designed this way.
> Include the most important non-obvious internal detail.]
>
> **Part 3 — Example / Gotcha (20s):** [The production scenario or the most common trap.
> This is what makes the answer memorable to the interviewer.]

---

## 🧾 TL;DR

Bullet list. Max 8 bullets.
The things to reread the night before an interview.
No explanations — just the facts.
If a bullet needs an explanation to be understood, it belongs in Build-up,
not here. Move it or cut it.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| [date] | [what was written or changed and why] |
````

---

## 📋 The 7 Standards

Rules that govern the quality of every section. Read before writing. Run as a checklist when done.

---

### Standard 1 — JVM Layer Callout

Whenever a concept has a JVM-level reality — object layout, bytecode, class loading, heap allocation, GC eligibility, method dispatch table, classfile constant pool — include this callout somewhere in Level 2 or Level 3:

```markdown
> **What the JVM is actually doing:** [2–4 sentences explaining the JVM-level reality]
```

This does NOT always require a full ASCII diagram. Sometimes one paragraph is the right level of detail. But the JVM layer must always be made explicit.

**Examples of when it's needed:**
- HashMap treeifies a bucket → what `TreeNode` objects are allocated on the heap, and when they become GC-eligible
- A lambda is created → what anonymous class (or `invokedynamic` callsite) the compiler actually generates
- `synchronized` on a method → what the monitor object is, where it lives, what the JVM bytecode looks like (`monitorenter` / `monitorexit`)
- `String.intern()` → what the string pool actually is (a region in the heap since Java 7, not PermGen)

**When it's NOT needed:**
- Pure algorithm / logic concepts with no JVM-specific behavior
- Concepts where the JVM layer is fully transparent and doesn't affect behavior

---

### Standard 2 — Tiered Code Examples

Three tiers. Label each explicitly with a comment or markdown subheading.

**Tier 1 — Demo code:**
Minimal code that isolates the concept. No error handling, no production concerns. Enough to show the mechanism working. This is always required.

```java
// Tier 1 — Demo: shows the concept in isolation
Map<String, Integer> map = new HashMap<>();
map.put("a", 1);
map.put("a", 2);   // second put for same key — what happens?
System.out.println(map.get("a"));   // 2 — key was overwritten, not duplicated
```

**Tier 2 — Production code:**
How you'd actually write this at Walmart. Includes error handling, thread safety, realistic class names. Include when Tier 1 alone would leave a dangerous gap between demo and reality.

**Tier 3 — Framework internals (where applicable):**
Where Spring / JDK / JPA uses this concept internally. Shows the concept at industrial scale. This makes abstract concepts concrete by showing them in code the reader already uses daily. Include when available and illuminating; skip if it would require too much framework context to understand.

---

### Standard 3 — "Before Java X" Framing for Evolution Concepts

When a concept changed between Java versions, show the before/after side by side.

**Rule:** Java 21 is the current baseline. Show Java 21 idioms as the current correct way. Show pre-Java-21 versions as historical context so the reader understands WHY Java 21 changed things.

Format:
```markdown
**Before Java 8 (anonymous inner class — verbose, class per usage):**
[old code]

**Java 8+ (lambda — concise, same bytecode outcome):**
[new code]

**Java 16+ (record — one line for an immutable value object):**
[Java 16 code if applicable]

**Java 21 (virtual threads — changes the blocking I/O story entirely):**
[Java 21 code if applicable]
```

Never just say "Java 8 added X." Show what you had to write BEFORE X. The pain is what makes the change stick.

---

### Standard 4 — Thread-Safety Flag on Every Class / Pattern

After every code example involving a class or pattern that will run in a concurrent context, add one of these labeled lines:

```java
// ⚠️ NOT thread-safe — ConcurrentModificationException under concurrent writes
// ✅ Thread-safe — uses CAS (compare-and-swap) internally; no lock contention for reads
// ⚠️ Conditionally thread-safe — individual operations are atomic; sequences of operations are not
// ✅ Thread-safe — immutable after construction; no synchronization needed
```

This is non-negotiable because everything in JavaBackend runs in a multi-threaded Spring container. A note that shows `HashMap` without flagging "NOT thread-safe — use ConcurrentHashMap in a Spring singleton" is actively harmful to a reader who copies the pattern into production.

---

### Standard 5 — Concrete Numbers

Describe behavior with the actual numbers the JDK uses, not vague language.

| ❌ Vague | ✅ Concrete |
|---|---|
| "HashMap resizes when it gets too full" | "HashMap resizes when `size > capacity × loadFactor`. Default capacity = 16 buckets, default loadFactor = 0.75. After 12 entries (16 × 0.75), capacity doubles to 32 and ALL entries are rehashed" |
| "TreeMap is slower than HashMap" | "HashMap.get() is O(1) average. TreeMap.get() is O(log n) — a red-black tree traversal. For n=1,000,000 entries, that's ~20 comparisons vs ~1." |
| "ConcurrentHashMap has many segments" | "Java 8+ ConcurrentHashMap: no longer uses segments. Uses CAS + synchronized on the bin head. Default initial capacity = 16 bins." |

If you genuinely don't know the exact number, state `"approximately"` or cite the JDK version and source location. Never fudge.

---

### Standard 6 — Real-World Context Per Footgun

Each footgun must feel like it happened to a real team. Not a contrived textbook warning.

Format:
```
> **Footgun: [short name]**
> **Cost:** [exact cost label — not "bad" but one of: Silent data corruption / OOM / Race condition /
>            Performance cliff / Silent failure / Data loss / Security vulnerability]
>
> In a [production context — high-traffic API / Kafka consumer / Spring singleton /
>  scheduled batch job], [this pattern] caused [this symptom] because [this mechanism].
```

Use your Walmart / MCSE context wherever it fits naturally. The goal is that this reads like a war story, not a textbook warning. A reader who has heard a war story will remember it six months later. A reader who saw a generic warning will not.

---

### Standard 7 — Concept Web Is Mandatory

The Concept Web section at the end of every note is NOT optional. Missing it turns the KB into a pile of isolated files. Present it turns the KB into a graph of knowledge the reader can navigate.

Rules:
- Minimum 3 entries. No maximum.
- Use relative paths from the current file — not note names alone.
- The relationship description must name the SPECIFIC connection, not just that a connection exists.
- If the target note doesn't exist yet, list its expected path anyway — it becomes a creation pointer.

```markdown
| Connects to | How |
|---|---|
| `equals-hashcode-contract.md` | HashMap.get() calls hashCode() to find the bucket, then equals() to find the key within the bucket — if equals/hashCode are broken, HashMap silently fails |
| `../Spring/DeepDive/02-spring-core.md` | Spring's DefaultSingletonBeanRegistry stores beans in a ConcurrentHashMap — understanding HashMap internals explains why Spring bean lookup is O(1) |
| `concurrency/concurrent-collections.md` (planned) | ConcurrentHashMap is the thread-safe drop-in for HashMap; its design makes sense only after understanding HashMap's bucket structure |
| `../../DSA/DeepDive/trees-fundamentals.md` | HashMap treeifies bins with >8 entries using a red-black tree — the same tree type covered in DSA |
```

---

## 🧪 Deep-Dive Quality Checklist

Run this checklist before delivering ANY deep-dive note in `JavaBackend/DeepDive/`.
This extends the universal checklist (`../../AGENTS.md`) and the JavaBackend checklist (`../AGENTS.md`).

### Structure
- [ ] All sections present in correct order: Problem → Terminology → Mental Model → Visual (or noted omission) → Build-up → Misconceptions → Footguns → Concept Web → Interview Deep Questions → Say It → TL;DR → Changelog
- [ ] Visual present if concept is spatial / structural / stateful / sequential
- [ ] Build-up has exactly 3 levels (Level 1, Level 2, Level 3 — not collapsed into 1 or 2)
- [ ] "Say It in 60 Seconds" is at the END, not the top

### Content
- [ ] Problem section makes the reader feel the pain BEFORE the solution appears (not just names the problem)
- [ ] Mental model is code-free and uses only Terminology table terms
- [ ] Every term used in Build-up appears in Terminology table first OR is glossed inline at first use
- [ ] JVM Layer Callout present in Level 2 or Level 3 where JVM behavior is relevant (Standard 1)
- [ ] At least Tier 1 (demo) code example present for every major concept in the note
- [ ] Thread-safety flag on every class / pattern code example (Standard 4)
- [ ] Concrete numbers used — no vague descriptions (Standard 5)
- [ ] "Before Java X" framing used for concepts that evolved across Java versions (Standard 3)
- [ ] Java 21 shown as the current baseline where applicable
- [ ] Misconceptions table: ≥ 3 rows, genuinely wrong beliefs (not just restatements of facts)
- [ ] Footguns: ≥ 2, each has: short name + cost label + production context + ❌ trap code + ✅ fix code
- [ ] Concept Web: ≥ 3 entries with relative paths and specific relationship descriptions (Standard 7)
- [ ] Interview Deep Questions: 5–6 questions, each with 4–6 sentence answers referencing concrete internals
- [ ] "Say It in 60 Seconds" has all 3 labeled parts: What (10s) / How-Why (30s) / Example-Gotcha (20s)
- [ ] TL;DR: ≤ 8 bullets, no explanations, self-contained facts

### Style (from universal `../../AGENTS.md`)
- [ ] All code blocks have language tag (` ```java `, ` ```sql `, etc.)
- [ ] One statement per line; always braced blocks; spaces around operators
- [ ] First-use term gloss in parentheses for any term not in the Terminology table
- [ ] ASCII visuals: box-drawing characters, ≤ 80 columns wide, KEY INVARIANT label
- [ ] Only approved emoji palette (universal + JavaBackend additions in `../AGENTS.md`)
- [ ] All cross-references use relative paths
- [ ] Changelog entry with date and description of what was added/changed

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Created. Governs all JavaBackend/DeepDive notes written in the comprehensive knowledge-base completion effort (Phase 1–5 plan). Java 21 set as current baseline (up from Java 17 in the parent AGENTS.md). 7 standards defined: JVM Layer Callout, Tiered Code Examples, Before-Java-X Framing, Thread-Safety Flag, Concrete Numbers, Real-World Context per Footgun, Concept Web mandatory. Full 10-section note template established. |
