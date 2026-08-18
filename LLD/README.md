# LLD Knowledge Base — Start Here

> Low-Level Design for senior backend interviews. This README is the map: **what each folder
> is, the order to read it in, and how the pieces connect.** If you're rebuilding LLD from the
> ground up, follow the path top-to-bottom.

---

## 🎯 The One Idea

Every LLD problem is the same three questions, in order:

```
1. WHAT are the objects?      → entities, and the RELATIONSHIPS between them
2. WHY is it shaped that way? → OOP + SOLID + a design PATTERN, named out loud
3. How does it stay correct   → CONCURRENCY: the shared mutable state and its guard
   under concurrent access?
```

Foundations teach questions 1–2. DesignPatterns give you the named tools for question 2. Concurrency owns question 3. Problems are where you rehearse all three under time. The InterviewPlaybook is how you *perform* it in the room.

---

## 📁 Folder Map

```
LLD/
├── README.md              ← you are here (the map)
├── STUDY-PLAN.md          ← the schedule + status tracker (start each session here)
├── notes-standards.md     ← how notes in this folder are written
├── resources.md           ← curated external resources per pattern/problem
│
├── Foundations/           ← the "understand WHY + HOW" core — read these FIRST
│   ├── 01-oop-concepts.md
│   ├── 02-solid-principles.md
│   ├── 03-design-principles.md
│   ├── 04-relationships.md          (composition vs aggregation — the #1 fumbled follow-up)
│   ├── 05-java-building-blocks.md
│   ├── 06-concurrency.md            (single-JVM + distributed)
│   └── 07-uml-for-interviews.md
│
├── DesignPatterns/        ← the named tools (each: what/why/when/code)
│   ├── 01-factory-strategy.md
│   ├── 02-observer.md
│   ├── 03-command.md
│   ├── 04-builder.md
│   ├── 05-state.md
│   ├── 06-singleton.md
│   └── 07-composite.md
│
├── InterviewPlaybook/     ← how to PERFORM in the room
│   ├── reading-guide.md             (annotated read-order + universal LLD pipeline)
│   └── execution-guide.md           (60-minute minute-by-minute playbook)
│
├── Problems/              ← worked problems: markdown approach + runnable Java
│   ├── parking-lot/  bookmyshow/  lru-cache/  rate-limiter/
│   ├── job-scheduler/  pubsub/  tictactoe/
│
└── Interview/             ← company-specific battle files (NOT general knowledge)
    ├── ebay-mts1-lld.md
    └── salesForce/                  (the "why" drill: 00-cheatsheet … 05-drill)
```

---

## 🪜 The Learning Path (in order)

**Phase 0 — Foundations (understand the vocabulary).** Read `Foundations/01`→`07` in order. These files answer *why* a design is shaped a certain way; every problem note assumes them. The annotated version of this read-order (with the probe each file answers) is **InterviewPlaybook/reading-guide.md**.

**Phase 1 — Patterns + Problems, interleaved.** Learn a pattern in `DesignPatterns/`, then immediately apply it in a `Problems/` problem. The pattern sticks because you *use* it. The day-by-day schedule and status tracker live in **STUDY-PLAN.md**.

**Phase 2 — Perform.** The morning of an interview, read **InterviewPlaybook/execution-guide.md** (the 60-minute script). For a specific company, read its file under **Interview/**.

---

## 🧭 How the Pieces Connect

| When you… | The relevant file(s) |
|---|---|
| Need to name entities and their wiring | `Foundations/01-oop-concepts.md`, `Foundations/04-relationships.md` |
| Are asked *"why did you design it this way?"* | `Foundations/02-solid-principles.md`, `Foundations/03-design-principles.md` |
| Recognise a swappable algorithm / event fan-out / lifecycle / tree | `DesignPatterns/` (Strategy / Observer / State / Composite) |
| Hear *"now make it thread-safe"* or *"now make it multi-node"* | `Foundations/06-concurrency.md` |
| Need to draw the class diagram | `Foundations/07-uml-for-interviews.md` |
| Want to know which Java collection / primitive to pick | `Foundations/05-java-building-blocks.md` |
| Are about to walk into an interview | `InterviewPlaybook/execution-guide.md`, then the `Interview/<company>/` file |

---

## 📝 Conventions

- **Paths in cross-references** are written relative to the referencing file (clickable on GitHub). Where a note lives in a subfolder, links to siblings are bare filenames and links to other folders use `../`.
- **General knowledge** lives in `Foundations/`, `DesignPatterns/`, `Problems/`. **Company-specific** strategy lives only under `Interview/`.
- **Note format** (both pattern notes and problem notes) is defined in `notes-standards.md`. Read it before writing a new note.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | **Created during the LLD folder restructure** as the single entry point. Root was flattened into `Foundations/` (7 concept files) and `InterviewPlaybook/` (reading + execution guides); the two SOLID files were merged; a canonical `Foundations/04-relationships.md` replaced 5 fragmented treatments; `DesignPatterns/07-composite.md` was added; `TODO.md` was merged into `STUDY-PLAN.md`; the DocuSign-branded reading guide was de-branded. |
