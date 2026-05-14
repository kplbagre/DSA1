# Reference Notes — Standards

> Conventions for writing compact daily-revision cheatsheets in this `Reference/` folder. Read this **after** the master `AGENTS.md` at the project root. Together they tell you exactly how to structure a new Reference note.

---

## 🎯 Purpose

A Reference note is the file Kapil **lives in during practice and revision** — not the file he reads to learn a topic from scratch. It must be:

- **Compact** — scannable in under 5 minutes
- **Method-first** — syntax tables, not prose
- **Pattern-organized** — grouped by reusable code shape
- **Render-friendly** — clean code blocks, no fancy formatting

Kapil typically reads the corresponding `DeepDive/<topic>-fundamentals.md` once, then migrates to `Reference/<topic>-reference.md` for daily revision.

**Length target:** 300–600 lines. Less than 300 → likely too thin. More than 600 → consider splitting (e.g., separate HashMap and TreeMap files).

---

## 📐 Document Structure (Section Order)

Every Reference note follows this template:

```
1.  H1 Title                                          [REQUIRED]
2.  Brief intro paragraph (what + when to use)        [REQUIRED]
3.  Implementation comparison table                   (when applicable)

For each major implementation/topic:
4.  ## H2 — Implementation name
5.  One-line description (backing structure + complexity summary)
6.  Canonical creation snippet (Java code block)
7.  ### Useful Methods table (Method | Description | Time)  [REQUIRED]
8.  ### DSA Use Cases (1-3 bullets — high-level)
9.  ### Common DSA Patterns (numbered, see pattern format below)  [REQUIRED]
10. ### Iteration Patterns (code snippet)

End of file:
11. ⚠️ Gotchas (Silent Bug Hall of Fame)              [REQUIRED]
12. ⚡ Quick Cheat Sheet (decision table)             [REQUIRED]
```

---

## ✍️ Pattern Block Format

Every pattern under "Common DSA Patterns" must follow this structure:

```markdown
**N. Pattern Name** ⭐ (star if it's a top-3 pattern)

> 1-3 line conceptual explanation in a blockquote — what is this pattern doing,
> what problem does it solve, why does it work. Plain English, no code.

```java
// Working, runnable Java code
// Each statement on its own line
// Always use braces for blocks
// Spaces around =, ,, and operators
```

**🏷️ Example problems:** Problem Name (LC X), Another Problem (LC Y), ...
```

### Optional additions to a pattern block

- **Variant** — alternative form of the same pattern (e.g., "Sliding Window with Last Index")
- **Walkthrough** — step-by-step trace of how state evolves (only for complex patterns)
- **Bonus** — an O(n) bucket-sort alternative to a heap-based solution, etc.
- **Mental hook** — closing blockquote with the pattern's mental model

### Long-form variant (for complex patterns)

> **Established convention** — when a pattern uses dense Stream API or `merge()` calls that Kapil might not parse on first read, **first show a long-form (verbose) version**, then a short merge version, with a "Reading the merge calls" subsection bridging them.

Example pattern (HashMap Pattern #6 — Sliding Window):

```markdown
**6. Sliding Window Character Count** ⭐

> [conceptual blockquote]

**Long form (no merge — easier to read):**

```java
// verbose code, every step explicit
```

**Short form (using merge):**

```java
// compact code with merge() / computeIfAbsent()
```

**Reading the merge calls:**
- `map.merge(c, 1, Integer::sum)` — *"add c to the map; if it's already there, add 1 to the existing value"*
- ...
```

---

## 💻 Code Formatting Rules (CRITICAL)

These mirror the universal rules in `AGENTS.md`. Repeated here for convenience because Kapil opens this file most often.

| Rule | Example |
| --- | --- |
| **One statement per line** — never put `if (...) return ...;` on one line | `if (cond) {`<br>`    return val;`<br>`}` |
| **Always use braces** for `if`, `for`, `while` blocks | Even single-statement bodies |
| **Spaces around operators** — `=`, `+`, `==`, `&&`, `,`, `:` | `int x = a + b;`, `for (int n : nums)` |
| **No spaces inside generic angle brackets** | `Map<String, List<String>>` (✅) not `Map < String , List < String > >` |
| **No inline comments at end of statement** — put on previous line | Use `// note` on its own line |
| **Working code** — no pseudo-code, no `...` placeholders | Code should compile if pasted into a class |
| **No imports** — assume `java.util.*` is imported | Reader knows what to import |
| **Variable names** — clear and short (`l`, `r` for pointers; `freq`, `seen`, `map` for collections) | |

### ✅ Good vs ❌ Bad

```java
// ✅ Good
Map<Integer, Integer> map = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    int complement = target - nums[i];
    if (map.containsKey(complement)) {
        return new int[]{ map.get(complement), i };
    }
    map.put(nums[i], i);
}
```

```java
// ❌ Bad — multiple statements per line, missing braces, no spacing
Map<Integer,Integer> map=new HashMap<>();
for(int i=0;i<nums.length;i++){
if(map.containsKey(target-nums[i]))return new int[]{map.get(target-nums[i]),i};
map.put(nums[i],i);}
```

---

## 📊 Tables

Use markdown tables for:

- **Methods reference** — Method | Description | Time
- **Comparison** — Approach | Time | Space | Notes
- **Decision cheatsheets** — Need | Use
- **Hashable Key strategies** — Item Type | Key Strategy

Always include a header row. Keep cells short — wrap to multiple bullets in the next section if a cell would be longer than 80 chars.

---

## 🎨 Visual Hierarchy (use sparingly — don't over-decorate)

Reference-specific emoji palette (subset of the master list in `AGENTS.md`):

| Element | Use For |
| --- | --- |
| `🔹` | Major H2 sub-section ("HashSet", "TreeSet") |
| `⭐` | Top-3 most important patterns in a section |
| `⚠️` | Gotchas section |
| `⚡` | Cheat sheet section |
| `🏷️` | Example problems line |
| `🚀` | Optional closing line |
| `>` blockquote | Conceptual explanations, mental models, "why this works" |
| **Bold** | Emphasis on key terms in prose |

> **Don't use** decorative emojis like 🎉, 🎯, 🚀 inside notes (only in chat). Keep notes professional and clean.

> **Exception — `❌` and `✅` are allowed inside code comments** for wrong/right comparisons in Gotchas. They make the contrast scannable.

```java
arr.toString();          // "[I@1540e19d" ❌
Arrays.toString(arr);    // "[1, 0, 1, ...]" ✅
```

---

## 🧠 Tone & Pedagogy

- **Brief and revision-friendly** — these are notes, not tutorials. Aim for 1-3 line conceptual explanations.
- **Don't shy from "small things"** — `c - 'a'` arithmetic, `Arrays.toString` vs `arr.toString()`, etc. Kapil has explicitly asked for these.
- **Always include code** — no theory-only sections. If you're explaining a concept, show it in code.
- **Show progressive complexity** for problems — brute force → optimal, with comparison table.
- **Cross-reference other notes** when a pattern uses concepts documented elsewhere — e.g., *"(See HashMap notes #7 — Hashable Key)"*.
- **Tag LeetCode numbers** — `(LC 49)`, `(LC 128)` — for quick problem lookup.
- **Use mental models** — close patterns with a quote like *"Mental model: 'Can I represent this thing as a unique key?'"*.

---

## ⚠️ Gotchas Section — Mandatory

Every Reference note **must** end with a "Gotchas (Silent Bug Hall of Fame)" section. Include:

- **Null handling differences** between similar classes (HashMap vs TreeMap, etc.)
- **Auto-unboxing NPEs** — when `int x = map.get(k)` throws on null
- **Mutation traps** — mutating keys/elements after insertion
- **API confusables** — `replace` vs `replaceAll`, `matches` vs `find`, `contains` on values, etc.
- **Comparator pitfalls** — TreeSet rejecting equal-by-comparator elements
- **Iteration safety** — ConcurrentModificationException
- **`==` vs `.equals()`** for the relevant types
- **Performance traps** — O(n) operations that look like O(1) (e.g., `containsValue`, `substring`)

The criterion: *"Could a beginner write code that compiles, runs, doesn't crash, but produces wrong output?"* If yes → it's a silent bug → document it.

### Gotcha block format

Each gotcha gets a **bold one-line title + explanation**, followed by a **brief wrong/right code snippet** (3-6 lines). Code makes it stick — pure prose explanations are forgettable.

```markdown
**Auto-unboxing NPE on missing key.**

```java
int x = map.get("missing");                 // NullPointerException ❌
int y = map.getOrDefault("missing", 0);     // 0 ✅
```

---
```

Separate each gotcha with `---` for visual breathing room. Skip code only for self-evident gotchas (e.g., "`String.format` is slow").

---

## ⚡ Quick Cheat Sheet — Mandatory

Every Reference note ends with a one-page decision table. Format:

```markdown
## ⚡ Quick Cheat Sheet

| If you need... | Use... | Why |
| --- | --- | --- |
| O(1) lookup with insertion order | `LinkedHashMap` | Hash + linked list |
| Sorted keys | `TreeMap` | Red-black tree |
| Plain unordered | `HashMap` | Fastest, no order |
| ... | ... | ... |
```

This is what Kapil scans 5 minutes before an interview. Make it ruthless.

---

## 📂 File Naming

| Pattern | Example |
| --- | --- |
| `<topic>-reference.md` | `lambdas-for-dsa-reference.md`, `string-operations-reference.md` |
| `<topic>-section-updated.md` (when patching existing notes) | `hashmap-section-updated.md` |
| `notes-standards-reference.md` (this file) | — |

---

## ✅ Quality Checklist (run before delivering a Reference note)

Universal (from `AGENTS.md`):
- [ ] All code blocks pass universal-formatting rules
- [ ] All LC references include the LC number
- [ ] No emojis outside the approved set
- [ ] No `Co-Authored-By: Claude` in commits

Reference-specific:
- [ ] Every code block uses one-statement-per-line + always-braced format
- [ ] Every pattern has: explanation blockquote + code + 🏷️ example problems
- [ ] Top-3 patterns marked with ⭐
- [ ] Methods table includes Time column
- [ ] Long-form-then-short-form variant used for any complex `merge`/Stream pattern
- [ ] Gotchas section present, covers silent bugs
- [ ] Cheat sheet at bottom
- [ ] Cross-references to related notes added where relevant
- [ ] No inline `if (...) return ...;` or other multi-statement lines

---

## 📌 How Kapil Uses This

When Kapil says *"prepare reference notes for [topic]"*:

1. Confirm with him: *"Should I create `Reference/<topic>-reference.md`?"*
2. Read `AGENTS.md` (master) + this file (Reference standards)
3. Outline the methods, patterns, and gotchas
4. Write the note
5. Run the quality checklist
6. Deliver

If something needs to deviate from the standard for a specific topic, call it out explicitly.

---

## 🔗 Companion Standards

- **Project root:** `AGENTS.md` — universal rules, folder structure, audience
- **Sister folder:** `DeepDive/notes-standards-deepdive.md` — when to write a Deep Dive instead (for new topics needing top-to-bottom learning)
- **Sister folder:** `Patterns/notes-standards-patterns.md` — when to write a per-problem dive instead (for single LC problem walkthroughs)

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| (initial) | First version with Reference + Pattern (TBD) sections combined |
| May 2026 | **Pattern/Problem section moved** to `Patterns/notes-standards-patterns.md` (now its own folder). This file is now Reference-only. |
| May 2026 | **Long-form-then-short-form variant** documented for complex patterns (after Kapil's HashMap Subarray Sum + Sliding Window feedback). |
| May 2026 | Cross-reference companions added (DeepDive + Patterns standards files now exist). |
