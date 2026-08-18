# LLD Study Plan & Tracker

> **The single planner for the LLD folder.** (Absorbs the old `TODO.md`.) Read before each
> session to know exactly what to do next. Folder index + how everything fits together:
> **README.md**.
>
> **Practice modes** (pick per session):
> - **"You write first"** — Wibey writes the full solution. Use when short on time or studying a new pattern.
> - **"I write first"** — Kapil attempts independently, shares for review + rewrite. **Preferred — builds real interview recall.**

---

## 🪜 Phase 0 — Foundation Reading (Before Any Problem)

Read in this order. These files give you the vocabulary every problem note assumes. Full annotated version: **InterviewPlaybook/reading-guide.md**.

| Order | File | What you get | Time |
|---|---|---|---|
| 1 | `Foundations/01-oop-concepts.md` | 4 OOP pillars + composition-over-inheritance | 20 min |
| 2 | `Foundations/02-solid-principles.md` | All 5 SOLID principles with violation→fix code | 30 min |
| 3 | `Foundations/03-design-principles.md` | KISS, DRY, YAGNI, SoC, Law of Demeter | 20 min |
| 4 | `Foundations/04-relationships.md` | IS-A / HAS-A / USES; composition vs aggregation | 20 min |
| 5 | `Foundations/05-java-building-blocks.md` | Interface vs Abstract, collections, concurrency primitives | 30 min |
| 6 | `Foundations/06-concurrency.md` | Races, deadlock, single-JVM + distributed locking | 40 min |
| 7 | `Foundations/07-uml-for-interviews.md` | How to draw + narrate class/sequence diagrams | 20 min |
| 8 | `InterviewPlaybook/execution-guide.md` | 60-minute interview playbook — read the morning of every interview | 15 min |

**Total foundation time: ~3 hours.** Do this once before starting problems; re-skim `06-concurrency.md` before every problem.

---

## 🗺️ Phase 1 — Patterns + Problems (Interleaved)

> **Why interleaved:** learn a pattern, immediately apply it in a problem. The pattern sticks because you see WHY it exists.

| Day | Session | What | Mode | File |
|---|---|---|---|---|
| Day 1 | Morning | Read Observer pattern | Study | `DesignPatterns/02-observer.md` |
| Day 1 | Evening | Read State pattern | Study | `DesignPatterns/05-state.md` |
| Day 2 | Morning | Read Factory + Strategy | Study | `DesignPatterns/01-factory-strategy.md` |
| Day 2 | Evening | **BookMyShow** | "I write first" | `Problems/bookmyshow/` |
| Day 3 | Morning | Review BookMyShow canonical | Study | `Problems/bookmyshow/bookmyshow.md` |
| Day 3 | Evening | Read Command pattern | Study | `DesignPatterns/03-command.md` |
| Day 4 | Morning | Read Builder pattern | Study | `DesignPatterns/04-builder.md` |
| Day 4 | Evening | **Elevator System** | "I write first" | `Problems/elevator/` |
| Day 5 | Morning | Review Elevator canonical | Study | `Problems/elevator/elevator.md` |
| Day 5 | Evening | Read Singleton | Study | `DesignPatterns/06-singleton.md` |
| Day 6 | All day | **Splitwise** | "I write first" | `Problems/splitwise/` |
| Day 7 | Morning | Review Splitwise canonical | Study | `Problems/splitwise/splitwise.md` |
| Day 7 | Evening | **Vending Machine** | "I write first" | `Problems/vending-machine/` |
| Day 8 | Morning | Read Composite pattern | Study | `DesignPatterns/07-composite.md` |
| Day 8 | Evening | **LRU Cache** | "I write first" | `Problems/lru-cache/` |
| Day 9 | All day | **Rate Limiter** | "I write first" | `Problems/rate-limiter/` |
| Day 10 | Morning | Full review session | Revision | All Foundation + pattern notes |

---

## ✅ Status Tracker — Patterns

| Pattern | File | Status | Frequency |
|---|---|---|---|
| Factory + Strategy | `DesignPatterns/01-factory-strategy.md` | ✅ Done | ⭐ Critical — used in every problem |
| Observer | `DesignPatterns/02-observer.md` | ✅ Done | ⭐ Critical — notifications, events |
| Command | `DesignPatterns/03-command.md` | ✅ Done | High |
| Builder | `DesignPatterns/04-builder.md` | ✅ Done | Medium |
| State | `DesignPatterns/05-state.md` | ✅ Done | Medium |
| Singleton | `DesignPatterns/06-singleton.md` | ✅ Done | Medium |
| Composite | `DesignPatterns/07-composite.md` | ✅ Done | High — file system, trees |

## ✅ Status Tracker — Problems

| Problem | Status | Patterns used | Frequency |
|---|---|---|---|
| Parking Lot | ✅ Done | Factory + Strategy + Concurrency | ⭐ Critical — asked everywhere |
| Tic-Tac-Toe | ✅ Done (bonus) | Strategy (win-check) | Medium |
| **BookMyShow** | 📖 Canonical — read & reproduce | Observer + State + Factory | ⭐ Critical |
| LRU Cache | 📖 Canonical — read & reproduce | Pure DS (DLL + Map) | High — technical LLD |
| Rate Limiter | 📖 Canonical — read & reproduce | Strategy (TokenBucket vs SlidingWindow) | ⭐ Critical — technical LLD |
| Job Scheduler | 📖 Canonical — read & reproduce | PriorityBlockingQueue + dispatcher + CAS cancel | High |
| Pub-Sub / EventBus | 📖 Canonical — read & reproduce | ConcurrentHashMap + CopyOnWriteArrayList | High |
| Elevator System | [ ] Not started | Command + Strategy (scheduler) | High |
| Splitwise | [ ] Not started | Strategy (split algorithms) | High |
| Vending Machine | [ ] Not started | State pattern canonical | Medium |

---

## 🎯 What to Do in Each "I Write First" Session

1. Skim `InterviewPlaybook/execution-guide.md` — the 60-minute breakdown
2. Open a blank file in `Problems/<name>/`
3. Design from scratch — follow the execution guide minute-by-minute
4. Write the markdown (approach, class diagram, concurrency, Q&A)
5. Write the Java files
6. Share with Wibey for review against the `notes-standards.md` checklist
7. Wibey rewrites the canonical version

---

## 📐 Concurrency Checkpoint (Every Problem)

Before calling any problem "done", answer these 4 questions (full depth: `Foundations/06-concurrency.md`):

1. **What is the shared mutable state?** (which fields can two threads read+write?)
2. **What is the race scenario?** (two users do X simultaneously — what breaks?)
3. **What lock strategy?** (synchronized / ReentrantLock / AtomicInteger / ConcurrentHashMap / DB / Redis)
4. **Why that strategy?** (name it — "AtomicInteger: single counter, no compound action")

---

## 🏢 Interview — Company Battle Files (`Interview/`)

Company-specific strategy notes (general knowledge stays in `Foundations/`, `DesignPatterns/`, `Problems/`).

| File | Topic | Status |
|---|---|---|
| `Interview/ebay-mts1-lld.md` | eBay MTS1 — OOP-in-DSA strategy; no dedicated LLD round; Toronto Spring Boot variant | ✅ Done |
| `Interview/salesForce/` (00–05 + README) | Salesforce — the "why" drill: interfaces, relationships, patterns, locks | ✅ Done |

---

## 📋 Phase 2 — Additional Problems (after Phase 1)

From interview-frequency research:

| Problem | Companies that asked it |
|---|---|
| Logger System | Razorpay SDE2 |
| Meeting Room Reservation | Amazon SDE2 |
| File System (`mkdir`, `ls`, `cd`) | Uber SDE2 — uses **Composite** (`DesignPatterns/07-composite.md`) |
| Stock Exchange / Order Book | Groww SDE3 |
| Cab Booking / Ride Sharing | Uber, Ola |
| Library Management | Multiple |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Study plan created. Phase 0 foundation reading + Phase 1 interleaved schedule. |
| Jul 9, 2026 | BookMyShow, Rate Limiter, LRU Cache marked 📖 Canonical. |
| Aug 2026 | **Merged `TODO.md` into this file** (single planner) during the LLD restructure. Phase 0 table updated to the new `Foundations/` + `InterviewPlaybook/` paths (added relationships, UML, and the reading-guide). Split status tracker into Patterns + Problems with frequency ratings (from TODO). Added Composite to Phase 1 and the pattern tracker. Added the Company Battle Files and Phase 2 sections absorbed from TODO. `TODO.md` deleted. |
