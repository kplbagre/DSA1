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
├── Spring/                          ← First track — 4-chapter Spring foundation
│   ├── spring-prep-log.md           ← Track's context-preservation file (read FIRST to resume)
│   ├── spring-10-hour-plan.md       ← Master plan + per-chapter format standards
│   ├── DeepDive/                    ← 4-chapter deep dives (restructured June 2026)
│   │   ├── 01-web-servlet-foundation.md  ← HTTP, TCP, servlet container, Servlet API
│   │   ├── 02-spring-core.md             ← IoC container, DI, bean scopes, AOP, proxies
│   │   ├── 03-spring-mvc-boot.md         ← DispatcherServlet, MVC, Boot, profiles (planned)
│   │   └── 04-jpa-transactions.md        ← JPA, Hibernate, transactions, lazy loading (planned)
│   ├── Reference/                   ← Track-specific cheatsheets (planned)
│   └── Practice/                    ← Runnable Java code (exercises + growing app)
│       ├── README.md
│       ├── exercises/
│       └── growing-app/
```

> **Per-chapter format standard:** each DeepDive chapter follows the 8-section arc defined in `spring-10-hour-plan.md` — Prerequisites → Mental model → Build-up → Common mistakes → Visual → Where you've seen this → Interview Q&A → TL;DR. The plan file is the source of truth for format rules; do not create a separate standards file.

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

## 📋 Deep-Dive Writing Standards

All `DeepDive/` notes (outside the `Spring/` track) follow the full 10-section template and 7 standards defined in:

**`DeepDive/notes-standards-deepdive.md`** ← read this before writing any deep-dive

Key points (full detail in that file):
- **Problem before solution** — never introduce a concept cold
- **Mental model before code** — the model is the note; code validates it
- **4 questions every note answers:** What / Why it exists / How internally / Where it bites in production
- **JVM Layer Callout** — make the JVM reality explicit in Level 2 or Level 3
- **Thread-safety flag** — on every class / pattern code example
- **Concept Web** — mandatory; makes the KB a graph, not a pile of files
- **Java 21 as current baseline** — older versions shown as historical context

The `Spring/` track follows its own format (8-section arc in `Spring/spring-10-hour-plan.md`). Do not apply `notes-standards-deepdive.md` to Spring track chapters.

---

## 🧭 The Three Note Types

Mirrors the DSA structure for consistency across the knowledge base.

| Folder | Type | When to use | Length target |
| --- | --- | --- | --- |
| `DeepDive/` | **Deep dive (in-depth study)** | New concept Kapil is learning fresh; needs mental model + worked examples + gotchas | No hard ceiling — topic drives length. Simple concepts may be 400 lines; complex ones (HashMap internals, GC) may be 900+. |
| `Reference/` | **Reference (cheatsheet)** | Quick-lookup syntax / API tables (e.g., Spring annotations, Stream operations, Collections methods) | 200–500 lines |
| `Patterns/` | **Pattern (idiom or design pattern)** | Single named pattern (Builder, Immutable Class, DTO Mapper, etc.) with motivation + canonical implementation + variants | 200–400 lines |

> **Decision question:** *"Is this a Java concept Kapil is learning fresh (DeepDive), a syntax/API cheatsheet to revise daily (Reference), or one specific reusable design pattern (Pattern)?"*

---

## 💻 Code Conventions (JavaBackend-specific)

Inherits all rules from the root `../AGENTS.md` (language-tagged fences, one statement per line, always braced blocks, spaces around operators, etc.). Additions:

- **Java version baseline:** assume **Java 21** unless explicitly stated otherwise. Java 21 is the current baseline — use virtual threads, records, sealed classes, pattern matching, text blocks, sequenced collections, `var` (Java 10+), and `List.of()` (Java 9+) where they improve clarity. Show older versions as historical context ("Before Java 8, you had to…") not as the canonical way.
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

## 🗺️ KB Completion Roadmap — All Phases

> **Context-preservation note for AI assistants:** This section is the master tracker for completing the JavaBackend knowledge base. When you resume a session, read this section to know what's done, what's next, and in what order. Update the status column after each note is created. Do NOT start notes out of order — the phases are sequenced so each note builds on the previous.

### How to resume after a context reset

1. Read `../../AGENTS.md` (universal rules)
2. Read this file `AGENTS.md` (JavaBackend rules + roadmap)
3. Read `DeepDive/notes-standards-deepdive.md` (the writing standards)
4. Check the status column below — find the first `📌 Not started` row
5. Read the 1–2 prerequisite notes listed for that topic
6. Write the note following the template in `notes-standards-deepdive.md`
7. Update the status column here when done

---

### Phase 1 — Core Java Language (Group 1) · 7 notes

> **Why first:** These are the foundational layer. HashMap needs equals/hashCode. Streams need functional interfaces. Concurrency needs JMM. Nothing in Phase 2–5 can be understood without Phase 1.

| # | Topic | File | Status | Prerequisites |
|---|---|---|---|---|
| 1 | **HashMap internals** | `DeepDive/hashmap-internals.md` | ✅ Done (Sep 2026) | Terminology table only — no prereq notes needed |
| 2 | **equals() / hashCode() contract** | `DeepDive/equals-hashcode-contract.md` | ✅ Done (Sep 2026) | Note #1 (HashMap explains WHY the contract matters) |
| 3 | **Generics + type erasure** | `DeepDive/generics-type-erasure.md` | ✅ Done (Sep 2026) | None — standalone |
| 4 | **Functional interfaces** | `DeepDive/functional-interfaces.md` | ✅ Done (Sep 2026) | None — standalone |
| 5 | **Java 8→21 evolution** | `DeepDive/java-version-evolution.md` | ✅ Done (Sep 2026) | Notes #3 + #4 (generics + functional interfaces appear in this timeline) |
| 6 | **String internals** | `DeepDive/string-internals.md` | ✅ Done (Sep 2026) | None — standalone |
| 7 | **Exception hierarchy** | `DeepDive/exception-hierarchy.md` | ✅ Done (Sep 2026) | None — standalone |

---

### Phase 2 — Streams & Functional Programming (Group 2) · 4 notes

> **Why after Phase 1:** Streams are built on functional interfaces (Note #4) and use generics extensively (Note #3). `Optional` uses generics. `CompletableFuture` uses functional interfaces + generics together.

| # | Topic | File | Status | Prerequisites |
|---|---|---|---|---|
| 8 | **Stream pipeline internals** | `DeepDive/stream-pipeline-internals.md` | ✅ Done (Sep 2026) | Notes #3 (generics) + #4 (functional interfaces) |
| 9 | **Collectors deep-dive** | `DeepDive/collectors-deepdive.md` | ✅ Done (Sep 2026) | Note #8 (streams pipeline) |
| 10 | **Optional** | `DeepDive/optional-proper-usage.md` | ✅ Done (Sep 2026) | Note #4 (functional interfaces — Optional uses Supplier/Function/Consumer) |
| 11 | **CompletableFuture** | `DeepDive/completable-future.md` | ✅ Done (Sep 2026) | Note #4 (functional interfaces) + Phase 3 Note #13 (JMM — to explain thread visibility) |

---

### Phase 3 — Spring Track Completion (Group 5) · 3 notes

> **Why here:** Spring 03 and 04 follow the existing Spring track format (8-section arc in `Spring/spring-10-hour-plan.md`), NOT the DeepDive standards. They go here in the roadmap because Phase 1–2 concepts (generics, functional interfaces, JMM basics) make the Spring internals click. Spring Security note uses the standards from `notes-standards-deepdive.md`.

| # | Topic | File | Status | Prerequisites | Format |
|---|---|---|---|---|---|
| 12 | **Spring 03 — MVC + Boot auto-config** | `Spring/DeepDive/03-spring-mvc-boot.md` | 📌 Not started | Spring chapters 01+02 (already done) | Spring 8-section arc |
| 13 | **Spring 04 — JPA + Transactions + Lazy loading** | `Spring/DeepDive/04-jpa-transactions.md` | 📌 Not started | Spring chapter 03 | Spring 8-section arc |
| 14 | **Spring Security + JWT** | `Spring/DeepDive/05-spring-security-jwt.md` | 📌 Not started | Spring chapters 01–04 | DeepDive standards |

---

### Phase 4 — JVM + Concurrency Deep-Dive (Groups 3 & 4) · 12 notes

> **Why after Phase 3:** JMM (Note #15) is the prerequisite for all concurrency notes. Class loading (Note #23) and JVM memory areas (Note #24) are prerequisites for GC (Note #25). Do this phase in the numbered order.

| # | Topic | File | Status | Prerequisites |
|---|---|---|---|---|
| 15 | **Java Memory Model (JMM)** | `DeepDive/java-memory-model.md` | 📌 Not started | Note #6 (String internals uses string pool — a JVM memory concept) |
| 16 | **synchronized + volatile** | `DeepDive/synchronized-volatile.md` | 📌 Not started | Note #15 (JMM — happens-before is the foundation) |
| 17 | **Locks deep-dive** | `DeepDive/locks-reentrant-readwrite.md` | 📌 Not started | Note #16 (synchronized — ReentrantLock is the improvement over it) |
| 18 | **Concurrent collections internals** | `DeepDive/concurrent-collections.md` | 📌 Not started | Notes #1 (HashMap) + #15 (JMM) + #16 (synchronized) |
| 19 | **ThreadPoolExecutor** | `DeepDive/thread-pool-executor.md` | 📌 Not started | Note #16 (synchronized) |
| 20 | **Synchronization aids** | `DeepDive/synchronization-aids.md` | 📌 Not started | Notes #15 + #16 (CountDownLatch/CyclicBarrier are built on JMM primitives) |
| 21 | **Fork/Join + parallel streams** | `DeepDive/fork-join-parallel-streams.md` | 📌 Not started | Notes #8 (streams) + #15 (JMM) + #19 (thread pools) |
| 22 | **Virtual threads (Java 21)** | `DeepDive/virtual-threads-java21.md` | 📌 Not started | Note #19 (thread pool executor — virtual threads replace it for I/O) |
| 23 | **Class loading + ClassLoader** | `DeepDive/class-loading.md` | 📌 Not started | None — standalone entry point |
| 24 | **JVM memory areas** | `DeepDive/jvm-memory-areas.md` | 📌 Not started | Note #23 (ClassLoader determines what goes into Metaspace) |
| 25 | **GC deep-dive** | `DeepDive/gc-deep-dive.md` | 📌 Not started | Note #24 (JVM memory areas — GC operates on the heap regions defined there) |
| 26 | **JIT compilation** | `DeepDive/jit-compilation.md` | 📌 Not started | Note #23 (class loading — JIT kicks in after class loading + interpretation) |

---

### Phase 5 — Design Patterns + Reference Sheets (Groups 6 & 7) · 10 notes

> **Why last:** Patterns require understanding of Java generics, functional interfaces, and Spring internals to explain at the right depth. Reference sheets are fastest to write — they're lookup tables, not deep dives.

| # | Topic | File | Status | Type |
|---|---|---|---|---|
| 27 | **Builder pattern** | `Patterns/builder-pattern.md` | 📌 Not started | Pattern |
| 28 | **Factory + Abstract Factory** | `Patterns/factory-patterns.md` | 📌 Not started | Pattern |
| 29 | **Proxy vs Decorator vs Adapter** | `Patterns/structural-patterns-proxy-decorator-adapter.md` | 📌 Not started | Pattern |
| 30 | **Strategy + Chain of Responsibility** | `Patterns/behavioral-strategy-chain.md` | 📌 Not started | Pattern |
| 31 | **Observer + event-driven** | `Patterns/behavioral-observer-events.md` | 📌 Not started | Pattern |
| 32 | **Collections API reference** | `Reference/collections-api-reference.md` | 📌 Not started | Reference |
| 33 | **Stream operations reference** | `Reference/stream-operations-reference.md` | 📌 Not started | Reference |
| 34 | **Concurrency utilities reference** | `Reference/concurrency-utilities-reference.md` | 📌 Not started | Reference |
| 35 | **Spring annotations reference** | `Reference/spring-annotations-reference.md` | 📌 Not started | Reference |
| 36 | **JVM flags reference** | `Reference/jvm-flags-reference.md` | 📌 Not started | Reference |

---

### Already Completed (before this roadmap was created)

| File | Status |
|---|---|
| `DeepDive/java-pass-by-value-semantics.md` | ✅ Done (May 2026) |
| `Spring/DeepDive/01-web-servlet-foundation.md` | ✅ Done (May 2026) |
| `Spring/DeepDive/02-spring-core.md` | ✅ Done (June 2026) |

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
| June 2026 | **Spring track restructured from 10-day files to 4-chapter format.** Original plan had one file per day (10 files). Replaced with 4 themed chapters: `01-web-servlet-foundation.md` (HTTP + TCP + Servlet API — merged original Days 1+2), `02-spring-core.md` (IoC + DI + AOP + proxies), `03-spring-mvc-boot.md` (MVC + Boot + Profiles), `04-jpa-transactions.md` (JPA + Hibernate + Transactions). Motivation: 10 separate files had too much navigation overhead for interview prep; 4 longer chapters keep related concepts co-located. Per-chapter format standard (8-section arc) lives in `spring-10-hour-plan.md`. |
| Sep 2026 | **KB Completion Roadmap added. DeepDive writing standards created.** Goal: build a complete senior Java backend KB at proper depth (mental models, JVM internals, production footguns) — not interview-condensed notes. `DeepDive/notes-standards-deepdive.md` created with the 10-section note template and 7 standards (JVM Layer Callout, Tiered Code Examples, Before-Java-X Framing, Thread-Safety Flag, Concrete Numbers, Real-World Context per Footgun, Concept Web mandatory). Java version baseline updated from Java 17+ to Java 21. Length ceiling removed — topic drives length. 36-note roadmap across 5 phases added to AGENTS.md for context-window survival. Phase 1 starts with HashMap internals (Note #1). |
