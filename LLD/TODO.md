# LLD — TODO

> **Approach:** Interleaved — learn a pattern, immediately apply it in a problem. Pattern sticks because you see WHY it exists.
>
> **Format:** Design pattern = markdown only. Problem = markdown (approach) + `.java` files (runnable code).
>
> **Standard:** `notes-standards.md` | **Execution playbook:** `interview-execution-guide.md` | **Resources:** `resources.md`

---

## 🖊️ Practice Workflow (Decided June 2026)

Two modes — Kapil picks per session:

| Mode | How it works |
|---|---|
| **"You write first"** | Wibey writes the full solution (markdown + Java). Use when short on time or studying a new pattern. |
| **"I write first"** | Kapil attempts the problem independently. Wibey reviews against the rubric, gives feedback, then rewrites the canonical version. **Preferred — builds real interview recall.** |

**When doing "I write first":**
1. Kapil opens a blank file in `Problems/<name>/` and designs from scratch (use interview-execution-guide.md)
2. Share the attempt with Wibey
3. Wibey reviews against the pre-publish checklist in `notes-standards.md`
4. Wibey rewrites the final canonical version

---

## Study Order — Patterns + Problems (Interleaved)

| Order | Type | Topic | File/Folder | Status | Interview Frequency |
|---|---|---|---|---|---|
| 1 | Pattern | Factory + Strategy | `DesignPatterns/01-factory-strategy.md` | ✅ Done | ⭐ Critical — used in every problem |
| 2 | Problem | Parking Lot | `Problems/parking-lot/` | ✅ Done | ⭐ Critical — asked everywhere |
| — | Problem | Tic-Tac-Toe | `Problems/tictactoe/` | ✅ Done (bonus) | Medium |
| 3 | Pattern | Observer | `DesignPatterns/02-observer.md` | ✅ Done | ⭐ Critical — notification, event systems |
| 4 | Problem | **BookMyShow / Movie Ticket** | `Problems/bookmyshow/` | 📖 Canonical — not self-attempted | ⭐ Critical — Flipkart SDE2 asked this |
| 5 | Pattern | Command | `DesignPatterns/03-command.md` | ✅ Done | High |
| 6 | Problem | Elevator System | `Problems/elevator/` | [ ] Not started | High |
| 7 | Pattern | Builder | `DesignPatterns/04-builder.md` | ✅ Done | Medium |
| 8 | Problem | Splitwise | `Problems/splitwise/` | [ ] Not started | High — Uber HLD asked |
| 9 | Pattern | State | `DesignPatterns/05-state.md` | ✅ Done | Medium |
| 10 | Problem | Vending Machine | `Problems/vending-machine/` | [ ] Not started | Medium |
| 11 | Problem | LRU Cache | `Problems/lru-cache/` | 📖 Canonical — not self-attempted | High — technical LLD |
| 12 | Problem | Rate Limiter (LLD) | `Problems/rate-limiter/` | 📖 Canonical — not self-attempted | ⭐ Critical — technical LLD |

---

## ✅ BookMyShow — Resources Ready

Everything needed to attempt BookMyShow is in place:

| What | Where | Status |
|---|---|---|
| Factory pattern (seat creation by tier) | `DesignPatterns/01-factory-strategy.md` | ✅ |
| Observer pattern (booking event → email, analytics) | `DesignPatterns/02-observer.md` | ✅ |
| State pattern (seat: AVAILABLE → LOCKED → BOOKED) | `DesignPatterns/05-state.md` | ✅ |
| Reference implementation | `resources.md` → ashishps1 (BookMyShow) | ✅ |
| Interview execution playbook | `interview-execution-guide.md` | ✅ |
| Note format to follow | `notes-standards.md` | ✅ |

---

## Key Horizontal Skill (applies to ALL problems)

**Concurrency** — "Now make it thread-safe" is the universal LLD follow-up. Every problem note MUST have a concurrency section.

For BookMyShow specifically: the critical concurrency scenario is **two users booking the last seat simultaneously** — seat locking with optimistic locking or synchronized check-then-book.

---

## Phase 2 — Additional Problems (after Phase 1 is done)

From interview frequency research (June 2026):

| Problem | Companies that asked it |
|---|---|
| Logger System | Razorpay SDE2 |
| Meeting Room Reservation | Amazon SDE2 (June 2025) |
| File System (mkdir, ls, cd) | Uber SDE2 (Nov 2025) |
| Stock Exchange / Order Book | Groww SDE3 |
| Cab Booking / Ride Sharing | Uber, Ola |
| Library Management | Multiple |

---

## ✅ Foundation Files (Read Before Any Problem)

These are not in the interleaved study order — they're prerequisite reading. All created June 2026.

| File | Topic | Status |
|---|---|---|
| `oop-concepts.md` | 4 OOP pillars + Composition vs Inheritance | ✅ Done |
| `design-principles.md` | KISS, DRY, YAGNI, SoC, Law of Demeter | ✅ Done |
| `concurrency-deep-dive.md` | Race conditions, deadlock, wait/notify, BlockingQueue | ✅ Done |
| `DesignPatterns/06-singleton.md` | Singleton — 3 variants + thread-safety | ✅ Done |
| `java-building-blocks-for-lld.md` | Interface vs Abstract, Collections, Concurrency primitives | ✅ Done |
| `DesignPatterns/00-solid-principles.md` | All 5 SOLID principles with code examples | ✅ Done |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | TODO created. Interleaved approach decided. Practice folder deferred. |
| June 2026 | Updated: marked 01-factory-strategy, parking-lot, observer, state as ✅ Done. Tictactoe added as bonus done. BookMyShow marked as 🔜 NEXT. Practice workflow section added — two modes: "You write first" vs "I write first" (preferred). BookMyShow resources-ready checklist added. resources.md added as a standard reference. |
| June 2026 | **Command (03) and Builder (04) marked Done.** 6 foundation files created from hellointerview gap analysis: oop-concepts, design-principles, concurrency-deep-dive, singleton. Foundation Files section added to TODO. |
| Jul 9, 2026 | **3 canonical notes created** (single-MD format — all classes inline): BookMyShow, Rate Limiter, LRU Cache. Status is 📖 Canonical (not self-attempted). All Java in one MD file per problem — separate .java files removed. |
