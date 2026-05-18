# JavaBackend — Subdomain Standards

> **For any AI assistant working in this folder:** Read the root `../AGENTS.md` first for universal rules, then read THIS file for JavaBackend-specific conventions. Both apply when generating any JavaBackend content.

---

## 🎯 Purpose

JavaBackend notes capture the **language-level and ecosystem-level knowledge** a senior Java backend engineer needs for interviews and day-to-day Walmart engineering. Topics span pure Java language semantics, the standard library, Spring / Spring Boot, JPA / Hibernate, concurrency, and JVM behavior.

These notes are **not problem-driven** (unlike DSA). They're driven by *concepts* — pass-by-value semantics, equals/hashCode contracts, generics & type erasure, immutability, Stream semantics, transaction propagation, etc.

**Audience:** Kapil — Walmart Java engineer prepping for senior backend / SDE-3 interviews and also writing production Java daily.

**Trigger pattern for new notes:** when a Java/backend concept comes up during DSA prep, day-job work, or interview prep that needs deeper understanding than a one-line answer, a new note gets created here.

---

## 📁 Folder Structure

```
JavaBackend/
├── AGENTS.md                              ← THIS FILE
│
├── DeepDive/                              ← In-depth concept study (read top-to-bottom once)
│   └── java-pass-by-value-semantics.md   ← first stone — Java memory & parameter passing
│
├── Reference/                             ← Compact cheatsheets (planned)
│   └── (e.g., spring-annotations-reference.md, collections-api-reference.md)
│
└── Patterns/                              ← Common design patterns / idioms (planned)
    └── (e.g., builder-pattern.md, immutable-class-pattern.md)
```

> **Folders are created lazily** — add `Reference/` and `Patterns/` only when their first file is written.

---

## 📁 Track-Style Sub-Folders

For **multi-session learning tracks** (focused study of one topic across many days, with hands-on code), a self-contained sub-folder lives directly under `JavaBackend/`:

```
JavaBackend/
├── Spring/                          ← First track — 10-hour Spring foundation
│   ├── spring-prep-log.md           ← Track's context-preservation file (read FIRST to resume)
│   ├── spring-10-hour-plan.md       ← Master plan (day-by-day arc)
│   ├── DeepDive/                    ← Track-specific deep dives
│   ├── Reference/                   ← Track-specific cheatsheets
│   └── Practice/                    ← Runnable Java code (exercises + growing app)
│       ├── README.md
│       ├── exercises/
│       └── growing-app/
```

**Decision rule — track sub-folder vs flat DeepDive/Reference/Patterns:**

| Use a **track sub-folder** when | Use **flat folders** when |
| --- | --- |
| Topic spans many sessions with a planned arc | One-off concept note |
| Includes hands-on runnable code | Pure conceptual / cheatsheet |
| Needs a context-preservation log to survive chat windows | Single self-contained file |
| Has its own prereqs and tooling setup | Plug into existing notes |

**Example mapping:**
- ✅ `JavaBackend/Spring/` → 10-day track, growing app, prep-log → **track sub-folder**
- ✅ `JavaBackend/DeepDive/java-pass-by-value-semantics.md` → one-off Java concept → **flat folder**
- (future) `JavaBackend/Concurrency/` → if it grows into a multi-day track with code → **track sub-folder**
- (future) `JavaBackend/DeepDive/equals-and-hashcode-contract.md` → one-off → **flat folder**

> Track sub-folders are **self-contained** — they have their own `prep-log` and plan. The root `JavaBackend/{DeepDive,Reference,Patterns}` folders remain for cross-cutting Java/JPA/JVM concept notes that don't belong to a specific track.

---

## 🧭 The Three Note Types

Mirrors the DSA structure for consistency across the knowledge base.

| Folder | Type | When to use | Length target |
| --- | --- | --- | --- |
| `DeepDive/` | **Deep dive (in-depth study)** | New concept Kapil is learning fresh; needs mental model + worked examples + gotchas | 400–800 lines |
| `Reference/` | **Reference (cheatsheet)** | Quick-lookup syntax / API tables (e.g., Spring annotations, Stream operations, Collections methods) | 200–500 lines |
| `Patterns/` | **Pattern (idiom or design pattern)** | Single named pattern (Builder, Immutable Class, DTO Mapper, etc.) with motivation + canonical implementation + variants | 200–400 lines |

> **Decision question:** *"Is this a Java concept Kapil is learning fresh (DeepDive), a syntax/API cheatsheet to revise daily (Reference), or one specific reusable design pattern (Pattern)?"*

---

## 💻 Code Conventions (JavaBackend-specific)

Inherits all rules from the root `../AGENTS.md` (language-tagged fences, one statement per line, always braced blocks, spaces around operators, etc.). Additions:

- **Java version baseline:** assume **Java 17+** unless explicitly stated otherwise. Features like records, sealed classes, pattern matching, text blocks, `var` (Java 10+), and `List.of()` (Java 9+) are fair game and should be used when they improve clarity.
- **Imports:** Do NOT declare imports inside code blocks. Assume standard packages (`java.util.*`, `java.util.stream.*`, `java.util.concurrent.*`) are available. For Spring / Jakarta / JPA / third-party packages, drop a one-line comment showing the package only if it's non-obvious:
    ```java
    // org.springframework.web.bind.annotation.RestController
    @RestController
    public class OrderController { ... }
    ```
- **Class scaffolding:** Always show enough of the surrounding class / annotations / signatures so the code is paste-runnable in a fresh project.
- **Spring annotations:** place each annotation on its own line above the element it annotates:
    ```java
    @Service
    @Transactional
    public class OrderService { ... }
    ```
- **Lombok:** prefer plain Java + records over Lombok in notes — Lombok hides the mental model and Kapil should *see* what's actually generated. Mention Lombok shortcuts in a "production shortcut" callout, not in the canonical example.

---

## 🎨 JavaBackend-Specific Emoji Additions

In addition to the universal palette in the root `../AGENTS.md`, JavaBackend notes may use:

| Emoji | Use for |
| --- | --- |
| `☕` | Pure-Java-language section (vs Spring/JPA/etc.) |
| `🌱` | Spring / Spring Boot section |
| `🗄️` | JPA / Hibernate / persistence section |
| `🧵` | Concurrency / threading section |
| `⚙️` | JVM / runtime / GC section |

> **Rule:** these emojis are for *section headers* that mark a clear sub-area. Don't sprinkle them in body text. Use the universal palette (🎯 📖 🧠 🎨 etc.) for everything else.

---

## 🧪 JavaBackend-Specific Quality Checklist

Extends the universal checklist in the root `../AGENTS.md`:

- [ ] Folder is correct (DeepDive vs Reference vs Patterns)
- [ ] Java 17+ features used where they improve clarity, not avoided out of habit
- [ ] Spring annotations placed on their own lines (never inline)
- [ ] If introducing a concept already covered elsewhere in JavaBackend or DSA, cross-reference rather than duplicate
- [ ] At least one ⚠️ *common bugs* or 🐞 *gotcha* callout per deep dive — real Java has too many footguns to skip this
- [ ] Interview-day phrasing called out explicitly (one-sentence "what to say in the room" line) for every concept that comes up in interviews

---

## 📌 How to Use This (workflow)

When the user asks for *"deep-dive on [Java/Spring concept]"* / *"a reference for [API]"* / *"a pattern doc for [idiom]":*

1. Identify the folder (DeepDive / Reference / Patterns) using the decision question above
2. Read the root `../AGENTS.md` for universal rules
3. Read THIS file for JavaBackend-specific rules
4. Write the note following the style of any existing notes in the same folder
5. Run ALL applicable checklists (universal + JavaBackend + note-type if a standards file exists)
6. Deliver

> **Cross-subdomain references are encouraged.** A JavaBackend note explaining `boolean[][]` mutation propagation should explicitly link to `../DSA/DeepDive/graphs-fundamentals.md` where the concept was first triggered. A DSA note that hits a Java-language nuance should link to the relevant JavaBackend deep dive rather than re-explain.

---

### Changelog

| Date | Change |
| --- | --- |
| May 2026 | **JavaBackend subdomain bootstrapped.** Created with `java-pass-by-value-semantics.md` as the first deep dive. Trigger: the LC 200 (Number of Islands) attempt in `DSA/DeepDive/graphs-fundamentals.md` raised the question *"is `visited[][]` passed by reference?"* — a Java-language question, not a DSA question, so it needed its own home. Folder structure mirrors DSA (DeepDive / Reference / Patterns) for cross-subdomain consistency. |
| May 2026 | **Track-style sub-folders added.** Introduced the `Spring/` sub-track for the 10-hour Spring foundation course. Established the rule that multi-session topics with hands-on code get self-contained sub-folders (with prep-log + plan + Practice), while one-off concept notes stay in the flat `DeepDive/Reference/Patterns` folders. |
