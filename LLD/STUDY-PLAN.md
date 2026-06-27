# LLD Study Plan

> **Purpose:** Day-by-day execution plan. Read before each session to know exactly what to do next.
>
> **Practice modes** (pick per session):
> - **"You write first"** — Wibey writes the full solution. Use when short on time or studying a new pattern.
> - **"I write first"** — Kapil attempts independently, shares with Wibey for review + rewrite. **Preferred — builds real interview recall.**

---

## 🪜 Phase 0 — Foundation Reading (Before Any Problem)

Read in this order. These files give you the vocabulary every problem note assumes.

| Order | File | What you get | Time |
|---|---|---|---|
| 1 | `oop-concepts.md` | 4 OOP pillars + Composition vs Inheritance decision | 20 min |
| 2 | `design-principles.md` | KISS, DRY, YAGNI, SoC, Law of Demeter — with drop-in phrases | 20 min |
| 3 | `DesignPatterns/00-solid-principles.md` | All 5 SOLID principles with code examples | 30 min |
| 4 | `java-building-blocks-for-lld.md` | Interface vs Abstract, Collections, Concurrency primitives | 30 min |
| 5 | `concurrency-deep-dive.md` | Race conditions, deadlock, wait/notify, BlockingQueue | 30 min |
| 6 | `interview-execution-guide.md` | 60-minute interview playbook — read morning of every interview | 15 min |

**Total foundation time: ~2.5 hours.** Do this once before starting problems.

---

## 🗺️ Phase 1 — Patterns + Problems (Interleaved)

| Day | Session | What | Mode | File |
|---|---|---|---|---|
| Day 1 | Morning | Read Observer pattern | Study | `DesignPatterns/02-observer.md` |
| Day 1 | Evening | Read State pattern | Study | `DesignPatterns/05-state.md` |
| Day 2 | Morning | Read Factory + Strategy | Study | `DesignPatterns/01-factory-strategy.md` |
| Day 2 | Evening | **BookMyShow** | "I write first" | `Problems/bookmyshow/` |
| Day 3 | Morning | Review BookMyShow canonical solution | Study | `Problems/bookmyshow/bookmyshow.md` |
| Day 3 | Evening | Read Command pattern | Study | `DesignPatterns/03-command.md` |
| Day 4 | Morning | Read Builder pattern | Study | `DesignPatterns/04-builder.md` |
| Day 4 | Evening | **Elevator System** | "I write first" | `Problems/elevator/` |
| Day 5 | Morning | Review Elevator canonical | Study | `Problems/elevator/elevator.md` |
| Day 5 | Evening | Read Singleton | Study | `DesignPatterns/06-singleton.md` |
| Day 6 | All day | **Splitwise** | "I write first" | `Problems/splitwise/` |
| Day 7 | Morning | Review Splitwise canonical | Study | `Problems/splitwise/splitwise.md` |
| Day 7 | Evening | **Vending Machine** | "I write first" | `Problems/vending-machine/` |
| Day 8 | Morning | Review Vending Machine | Study | `Problems/vending-machine/vending-machine.md` |
| Day 8 | Evening | **LRU Cache** | "I write first" | `Problems/lru-cache/` |
| Day 9 | All day | **Rate Limiter** | "I write first" | `Problems/rate-limiter/` |
| Day 10 | Morning | Full review session | Revision | All pattern notes |

---

## ✅ Status Tracker

| Problem | Status | Notes |
|---|---|---|
| Parking Lot | ✅ Done | Factory + Strategy + Concurrency |
| Tic-Tac-Toe | ✅ Done (bonus) | |
| **BookMyShow** | 🔜 NEXT | Observer + State + Builder |
| Elevator System | [ ] | Command + Strategy |
| Splitwise | [ ] | Strategy (split algorithms) |
| Vending Machine | [ ] | State pattern canonical |
| LRU Cache | [ ] | No pattern — pure DS (LinkedHashMap / DLL+Map) |
| Rate Limiter | [ ] | Strategy (TokenBucket vs SlidingWindow) |

---

## 🎯 What to Do in Each "I Write First" Session

1. Open `interview-execution-guide.md` — skim the 60-minute breakdown
2. Open a blank file in `Problems/<name>/`
3. Design from scratch — follow the execution guide minute-by-minute
4. Write the markdown (approach, class diagram, concurrency, Q&A)
5. Write the Java files
6. Share with Wibey for review against `notes-standards.md` checklist
7. Wibey rewrites the canonical version

---

## 📐 Concurrency Checkpoint (Every Problem)

Before calling any problem "done", answer these 4 questions:

1. **What is the shared mutable state?** (which fields can two threads read+write?)
2. **What is the race condition scenario?** (two users do X simultaneously — what breaks?)
3. **What lock strategy did you use?** (synchronized / ReentrantLock / AtomicInteger / ConcurrentHashMap)
4. **Why that strategy?** (name the principle — "AtomicInteger because it's a single counter; no compound action needed")

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Study plan created. Phase 0 foundation reading + Phase 1 interleaved schedule. |
