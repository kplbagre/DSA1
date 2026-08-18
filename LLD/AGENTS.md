# LLD — Subdomain Standards

> **Read the universal `AGENTS.md` at the repo root first.** This file only adds LLD-specific rules on top of those.

---

## Mandatory Pre-Work (Do This Before Writing Any Note)

1. **Read `README.md`** — the folder map, how the pieces connect, and the reading path. Start here.
2. **Read `notes-standards.md`** — the complete format for pattern notes and problem notes, section order, and pre-publish checklist.
3. **Read `resources.md`** — curated primary and supplementary resources per pattern and per problem. Before writing `📚 Further Reading` in any note, check here for already-vetted resources. Do NOT invent recommendations.
4. **Read the root `AGENTS.md`** — universal formatting rules (code style, ASCII visuals, emoji palette, first-use term gloss).
5. **Check existing files** before writing — avoid duplicating content that belongs as a cross-reference. Concept content lives in `Foundations/`; do not re-explain it in a problem note — link to it.
6. **Read `Foundations/01-oop-concepts.md`** — 4 OOP pillars. Check before explaining Encapsulation/Abstraction/Polymorphism/Inheritance.
7. **Read `Foundations/02-solid-principles.md`** — the 5 SOLID principles (canonical, single source). Check before writing a Design Decisions section.
8. **Read `Foundations/04-relationships.md`** — IS-A/HAS-A/USES and composition vs aggregation (canonical). Check before naming any relationship in a class diagram.
9. **Read `Foundations/06-concurrency.md`** — races, deadlock, single-JVM primitives AND distributed locking. Check before writing a Concurrency section. (`Foundations/05-java-building-blocks.md` covers WHICH primitive; this file covers WHY.)

> **Rule: When a new reference file is added to this folder that AI should consult, AGENTS.md Mandatory Pre-Work must be updated in the same step — never separately.**
>
> **Rule: General (non-company) knowledge lives in `Foundations/`, `DesignPatterns/`, `Problems/`. Company-specific strategy lives ONLY under `Interview/<company>/`. Never place a company-branded file at the LLD root or in `Foundations/`/`DesignPatterns/`.**

---

## 🎯 What This Folder Contains

Low-Level Design (LLD) knowledge for senior backend interviews. Two types of content:

| Type | Location | Format |
|---|---|---|
| **Design Pattern notes** | `DesignPatterns/` | Markdown only — conceptual, mental model, when to use, code snippet |
| **Problem walkthroughs** | `Problems/<problem-name>/` | Markdown (approach) + `.java` files (runnable implementation) |

This is **general knowledge** — not company-specific. Company-specific battle files live in `Interview/<Company>/`.

---

## 📁 Folder Structure

```
LLD/
├── AGENTS.md                        ← this file
├── README.md                        ← folder map + reading path (entry point)
├── STUDY-PLAN.md                    ← schedule + status tracker (absorbed old TODO.md)
├── notes-standards.md               ← format rules for both note types
├── resources.md                     ← curated external resources
│
├── Foundations/                     ← concept files (read first)
│   ├── 01-oop-concepts.md
│   ├── 02-solid-principles.md       ← canonical SOLID (merged from 2 old files)
│   ├── 03-design-principles.md
│   ├── 04-relationships.md          ← canonical composition-vs-aggregation
│   ├── 05-java-building-blocks.md
│   ├── 06-concurrency.md            ← single-JVM + distributed
│   └── 07-uml-for-interviews.md
│
├── DesignPatterns/
│   ├── 01-factory-strategy.md
│   ├── 02-observer.md
│   ├── 03-command.md
│   ├── 04-builder.md
│   ├── 05-state.md
│   ├── 06-singleton.md
│   └── 07-composite.md              ← file-system / tree pattern
│
├── InterviewPlaybook/
│   ├── reading-guide.md             ← annotated read-order + universal pipeline
│   └── execution-guide.md           ← 60-minute interview playbook
│
├── Interview/                       ← company-specific battle files
│   ├── ebay-mts1-lld.md
│   └── salesForce/                  ← 00-cheatsheet … 05-drill + README
│
└── Problems/                        ← approach markdown + runnable Java
    ├── parking-lot/  bookmyshow/  lru-cache/  rate-limiter/
    ├── job-scheduler/  pubsub/  tictactoe/
```

---

## 🎨 LLD-Specific Emoji Additions

In addition to the universal palette, these are approved for LLD notes:

| Emoji | Use for |
|---|---|
| `🏗️` | Class diagram / structure section |
| `🔌` | Interface definition section |
| `🖊️` | Coding skeleton / interview order section |
| `🔁` | Concurrency / thread-safety section |
| `📐` | Design decisions / trade-offs section |

---

## 📏 LLD-Specific Code Rules

On top of universal AGENTS.md code rules:

1. **Interfaces before classes** — every pattern note shows the interface first, implementation second
2. **`@Override` always** — never omit it on implemented/overridden methods
3. **Access modifiers explicit** — never package-private by accident; always `public`, `private`, or `protected`
4. **Thread-safety annotations** — if a class is thread-safe, say so in a comment: `// thread-safe: uses synchronized`
5. **No `System.out.println` in production code** — use a `Logger` or omit output entirely
6. **Java 17+** — prefer records for DTOs, sealed classes for closed hierarchies where appropriate

---

## ✅ LLD Quality Checklist

Run before finalizing any LLD note, in addition to the universal checklist:

**For Design Pattern notes:**
- [ ] Mental model uses a concrete everyday analogy
- [ ] Shows the INTERFACE definition before any implementation
- [ ] Includes a "when NOT to use this pattern" row in the trade-offs
- [ ] Java code compiles mentally (no syntax gaps)
- [ ] At least 2 real-world company examples

**For Problem notes (markdown part):**
- [ ] Requirements section lists functional + non-functional requirements
- [ ] Design decisions explained (why this class, why this pattern)
- [ ] Concurrency section present — which fields are shared, which lock strategy
- [ ] "What would you do differently?" answer present (from AGENTS.md)
- [ ] Cross-references the patterns used (link to DesignPatterns/ file)

**For Problem notes (Java files):**
- [ ] All files compile if copied into a blank Java project
- [ ] Interfaces defined before implementations
- [ ] `@Override` on all overridden methods
- [ ] No hardcoded magic numbers — use constants or enums

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | LLD folder created. Standards defined. Study order: interleaved (pattern → problem). |
| June 2026 | **resources.md created.** Two primaries locked in: ashishps1/awesome-low-level-design (⭐) and hellointerview.com LLD. Per-pattern and per-problem resource table added. Mandatory Pre-Work section added to AGENTS.md to enforce reading resources.md before writing any note. |
| June 2026 | **interview-execution-guide.md updated.** Three additions from multi-source review: (1) Explicit "Core Entities" step (minute 5-10) — identify nouns, name IS-A/HAS-A relationships. (2) SOLID principle naming required alongside pattern naming — added principle drop-in phrases section. (3) Pre-coding use-case walkthrough in design phase — validate the flow before committing to code. |
| June 2026 | **6 new foundation files created** from hellointerview curriculum gap analysis: `DesignPatterns/03-command.md`, `DesignPatterns/04-builder.md`, `DesignPatterns/06-singleton.md`, `concurrency-deep-dive.md`, `design-principles.md`, `oop-concepts.md`. Mandatory Pre-Work updated to reference oop-concepts, design-principles, and concurrency-deep-dive. |
| Jul 11, 2026 | **Interview/ folder created.** Company-specific battle files go here. First file: `Interview/ebay-mts1-lld.md` — eBay MTS1 OOP-in-DSA strategy note. Folder structure diagram updated. |
| Aug 2026 | **Full folder restructure.** Flat root split into `Foundations/` (7 concept files) and `InterviewPlaybook/` (reading + execution guides). The two SOLID files merged into `Foundations/02-solid-principles.md`. New canonical `Foundations/04-relationships.md` (composition vs aggregation) replaced 5 fragmented treatments and reconciled the two conflicting mnemonics. `DesignPatterns/07-composite.md` added (filled the flagged gap). DocuSign-branded reading guide de-branded → `InterviewPlaybook/reading-guide.md`. `TODO.md` merged into `STUDY-PLAN.md`. `README.md` added as the entry point. Mandatory Pre-Work + folder diagram updated; added the rule that company-specific content lives only under `Interview/`. |
