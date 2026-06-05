# SystemDesignConcepts — TODO

> **What this folder is:** Medium-depth notes on the core concepts that show up in every backend interview — not tied to any company. Stripped from the InMobi PS battle file and made universal.
>
> **Goal:** Be able to explain each concept in 2 minutes, know when to use it, name one real trade-off, and give one concrete example. Not textbook depth — interview-ready depth.

---

## Phase 0 — Research First (DO THIS BEFORE WRITING ANY NOTES)

- [ ] Search and collect highly-rated resources for each concept (YouTube channels, blog posts, GitHub repos people recommend on Reddit/LeetCode/Blind)
- [ ] Shortlist 1-2 best resources per concept (prefer: visual explainers, real-world examples, not academic papers)
- [ ] Create a `resources.md` file in this folder with the curated list
- [ ] Skim the top resources to calibrate depth — we want SDE-2/3 interview level, not PhD level

**Good resource hunters to check:**
- r/ExperiencedDevs, r/cscareerquestions — "best system design resources" threads
- LeetCode Discuss — system design tagged posts
- ByteByteGo (Alex Xu) — visual explainers
- Martin Kleppmann's blog / "Designing Data-Intensive Applications" chapters
- System Design Primer (GitHub — donnemartin)
- Arpit Bhayani's blog/YouTube — concept-level deep dives
- Jordan Has No Life (YouTube) — system design concepts

---

## Phase 1 — The 8 Core Concepts (from PS battle file)

One note per concept. Medium depth. Each note should cover:
- What it is (plain English, 3-4 lines)
- When to use it (problem shapes it maps to)
- How it works (simple ASCII visual + key mechanics)
- Trade-offs (what you gain, what you lose)
- One concrete example (real company / real system)
- Common interview questions where this concept is the answer

| # | Concept | Status | Priority |
|---|---|---|---|
| 1 | Idempotency | [ ] Not started | ⭐ Critical — asked everywhere |
| 2 | CDC (Change Data Capture) | [ ] Not started | ⭐ Critical — migration questions |
| 3 | Outbox Pattern | [ ] Not started | ⭐ Critical — dual-write questions |
| 4 | Consistent Hashing | [ ] Not started | ⭐ Critical — sharding questions |
| 5 | Backpressure | [ ] Not started | High |
| 6 | Bloom Filter | [ ] Not started | High |
| 7 | Token Bucket vs Sliding Window | [ ] Not started | ⭐ Critical — rate limiting questions |
| 8 | Sharded Counters | [ ] Not started | High |

---

## Phase 2 — Problem Shapes (from PS battle file)

One note per shape. Each note: problem description, naive approach, optimized approach, which concepts to reach for.

| # | Problem Shape | Status |
|---|---|---|
| 1 | Data Migration (move from A to B) | [ ] Not started |
| 2 | Distributed Counting / Rate Limiting | [ ] Not started |
| 3 | Stream Processing (reliable event handling) | [ ] Not started |
| 4 | Deduplication at Scale | [ ] Not started |
| 5 | Job Scheduling / Delayed Retries | [ ] Not started |
| 6 | Real-Time Analytics / Top-K | [ ] Not started |
| 7 | Feature Rollout / API Migration | [ ] Not started |
| 8 | Distributed Debugging / Failure Analysis | [ ] Not started |

---

## Phase 3 — Bonus Concepts (add only if Phase 1-2 are done)

These come up in HLD rounds more than PS, but worth having:

- [ ] Leader Election (Zookeeper, Raft basics)
- [ ] Event Sourcing vs CRUD
- [ ] CQRS
- [ ] Saga Pattern (orchestration vs choreography)
- [ ] Circuit Breaker + Bulkhead
- [ ] Database Isolation Levels (Read Committed, Repeatable Read, Serializable)
- [ ] Optimistic vs Pessimistic Locking

---

## Rules

1. **Research before writing.** Don't start Phase 1 until Phase 0 is done.
2. **Follow repo AGENTS.md standards** — ASCII visuals, English steps before code, first-use term glossing.
3. **Keep it interview-ready, not textbook.** If an explanation takes more than 2 minutes to speak aloud, it's too long.
4. **One concept = one file.** No mega-files.
5. **Cross-link** — each concept note should link back to which problem shapes use it, and vice versa.
