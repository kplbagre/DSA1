# SystemDesignConcepts — TODO

> **What this folder is:** Medium-depth notes on the core concepts that show up in every backend interview — not tied to any company. Stripped from the InMobi PS battle file and made universal.
>
> **Goal:** Be able to explain each concept in 2 minutes, know when to use it, name one real trade-off, and give one concrete example. Not textbook depth — interview-ready depth.

---

## Phase 0 — Research First (DO THIS BEFORE WRITING ANY NOTES)

- [x] Search what topics are asked for 5-7 year Java backend engineers — DONE (June 2026)
- [x] Find top-rated resources (5 web searches, cross-referenced) — DONE (June 2026)
- [x] Create `resources.md` — DONE (June 2026)
- [ ] Skim the top resources to calibrate depth before writing each note

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

> **Writing order based on interview frequency research (June 2026)** — see `resources.md` for source.

| Write Order | # | Concept | File | Status | Frequency |
|---|---|---|---|---|---|
| 1st | 9 | **Optimistic + Pessimistic Locking** | `01-optimistic-pessimistic-locking.md` | [ ] In progress | ⭐ Critical — InMobi gap + universal |
| 2nd | 10 | **Rate Limiting** (token bucket, sliding window) | `02-rate-limiting.md` | [ ] Not started | ⭐ Critical — asked at every company |
| 3rd | 3 | **Caching** (LRU, TTL, eviction, Redis) | `03-caching.md` | [ ] Not started | ⭐ Critical — every design question |
| 4th | 1 | **Idempotency** | `04-idempotency.md` | [ ] Not started | ⭐ Critical — payments, retries, Kafka |
| 5th | 4 | **Consistent Hashing** | `05-consistent-hashing.md` | [ ] Not started | ⭐ Critical — sharding questions |
| 6th | 11 | **Distributed Locking** (Redis SETNX, Redlock) | `06-distributed-locking.md` | [ ] Not started | High |
| 7th | 2 | **CDC + Outbox Pattern** | `07-cdc-outbox.md` | [ ] Not started | High — migration + dual-write |
| 8th | 6 | **Bloom Filter** | `08-bloom-filter.md` | [ ] Not started | Medium |
| 9th | 8 | **Sharded Counters** | `09-sharded-counters.md` | [ ] Not started | Medium |
| 10th | 5 | **Backpressure** | `10-backpressure.md` | [ ] Not started | Medium |

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

## Phase 2.5 — Added from InMobi PS Round (June 2026)

Gaps identified in real interview — add these to Phase 1:

| # | Concept | Why it's needed | Priority |
|---|---|---|---|
| 9 | **Optimistic Locking (version-based CAS)** | Inventory overselling — A=10, B=25, total=30. DB-level concurrency without locks. | ⭐ Critical |
| 10 | **Pessimistic Locking (SELECT FOR UPDATE)** | When to use vs optimistic. Deadlock risks, when contention is high. | High |
| 11 | **Inventory Management pattern** | Booking systems, e-commerce stock, ticket systems — decrement with safety. | ⭐ Critical |

> Source: `Interview/InMobi/inmobi-ps-round-debrief.md`

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

## Phase 4 — AI Integration Track (Thin, Interview-Aware Only)

> **Why:** Swiggy, PhonePe, Meesho job descriptions now include RAG as a baseline. Won't be asked in DSA/LLD/PS rounds but WILL come up in project depth / bar raiser. Keep thin — enough to answer "are you familiar with AI integration in Java?" confidently.

| # | Topic | Depth needed | Priority |
|---|---|---|---|
| 1 | **What is RAG** (Retrieval Augmented Generation) | Conceptual — what it is, why it exists, when you'd use it | High |
| 2 | **Vector Databases** (pgvector, Pinecone, Weaviate) | Conceptual — how they differ from relational/NoSQL DBs | High |
| 3 | **Spring AI / LangChain4j** | Awareness — what they do, which to pick for Spring Boot projects | Medium |
| 4 | **Agentic AI basics** | Conceptual — what an agent is, orchestrator vs LLM, tool use | Medium |
| 5 | **AI integration patterns in Java services** | One code example — RAG call or LLM API call in a Spring Boot service | Medium |

> **Do Phase 1-2-3 first. Phase 4 is the polish layer.**

---

## Rules

1. **Research before writing.** Don't start Phase 1 until Phase 0 is done.
2. **Follow repo AGENTS.md standards** — ASCII visuals, English steps before code, first-use term glossing.
3. **Keep it interview-ready, not textbook.** If an explanation takes more than 2 minutes to speak aloud, it's too long.
4. **One concept = one file.** No mega-files.
5. **Cross-link** — each concept note should link back to which problem shapes use it, and vice versa.
