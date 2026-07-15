# Disney Round 2 — Study Plan

> **Role:** Sr Product Software Engineer II, Ad Platforms org, Disney Entertainment and ESPN Technology
> **Round:** System Design (60 min) — can go HLD or LLD depending on interviewer
> **Most likely question:** API Rate Limiter (LLD) or Ad Budget Pacing (HLD)
> **Date created:** Jul 15, 2026

---

## ⏱️ Session Order

### Session 1 — Java Concurrency Foundation (1–1.5 hrs)

**Goal:** Load the mental model for verbal CS depth questions.

| # | File | What to do |
|---|---|---|
| 1 | `Interview/Disney/Java-concurrency-cheatsheet.md` | Read top-to-bottom. Say answers out loud — don't just read. |
| 2 | `DSA/SystemDesignConcepts/Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md` | Skim — reinforce locking mental model |

**Key things to be able to say without notes after this session:**
- `volatile` guarantees visibility + ordering, NOT atomicity. `volatile counter++` is broken.
- `computeIfAbsent` is atomic. `containsKey + put` is NOT.
- `LongAdder` vs `AtomicLong` — and when to pick which.
- Why `synchronized` causes virtual thread carrier pinning in Java 21.
- ZGC vs G1GC — which for ad serving and why.

---

### Session 2 — Rate Limiter LLD Deep Dive (1.5–2 hrs)

**Goal:** Be able to write and explain Token Bucket from scratch. Nail the concurrency questions.

| # | File | What to do |
|---|---|---|
| 1 | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md` | Read — algorithm comparison table |
| 2 | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting_advanced.md` | Read — distributed coordination section |
| 3 | `Interview/DocuSign/r2-solutions/C1-rate-limiter.md` | Read — system-level design, numbers, decisions |
| 4 | `Interview/Disney/LLD-rate-limiter-java.md` | Read Phase 1–4. Practice explaining Phase 4 (concurrency deep dive) out loud. |

**Checkpoint — can you answer these without looking:**
- [ ] Why Token Bucket over Leaky Bucket for ad request throttling?
- [ ] Draw the `TokenBucket` class — what fields? What does `tryConsume()` do?
- [ ] Why `computeIfAbsent` in the registry?
- [ ] What happens to memory if you have 10M clients in a week?
- [ ] How do you distribute enforcement across 50 ad server instances?

---

### Session 3 — HLD Ad Pacing (1–1.5 hrs)

**Goal:** Be able to draw the 3-stage architecture on a whiteboard and defend every choice.

| # | File | What to do |
|---|---|---|
| 1 | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/09-sharded-counters.md` | Read — hot key problem |
| 2 | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md` | Skim — Redis INCR, TTL, atomic ops section |
| 3 | `DSA/SystemDesignConcepts/Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md` | Read — AP vs CP reasoning |
| 4 | `Interview/Disney/HLD-ad-impression-pacing.md` | Read full file. Practice the 60-min delivery plan out loud. |

**Checkpoint — can you answer these without looking:**
- [ ] Draw the 3-stage pipeline (Ad Request → Redis Gate → Kafka → Flink → Cassandra)
- [ ] Why AP, not CP for ad serving? What's the cost of each choice?
- [ ] What is the hot key problem? How do sharded counters fix it?
- [ ] Why `LongAdder` + batch Redis flush instead of INCR per impression?
- [ ] What does the Budget Controller do? How often does it run?

---

### Session 4 — Kafka + Streaming (45 min)

**Goal:** Defend the async impression pipeline architecture.

| # | File | What to do |
|---|---|---|
| 1 | `DSA/SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` | Read — partitioning, consumer groups, retention |
| 2 | `DSA/SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/10-backpressure.md` | Skim — backpressure patterns |

**Key talking points to internalize:**
- Why partition Kafka by `campaignId` for impression events
- What happens if Kafka consumer falls behind (lag) — billing delay is acceptable, not data loss
- At-least-once delivery → idempotent Flink writes to Cassandra (deduplicate on impression ID)

---

### Session 5 — DocuSign Cross-Prep (30 min)

**Goal:** Cherry-pick the DocuSign files with the most overlap to Disney context.

| # | File | Why |
|---|---|---|
| 1 | `Interview/DocuSign/r2-solutions/C1-rate-limiter.md` | Already covered in Session 2 — just skim the "DocuSign angle" sections |
| 2 | `Interview/DocuSign/r2-solutions/D3-notification-service.md` | Kafka outbox, fan-out patterns — same as impression pipeline |
| 3 | `Interview/DocuSign/r2-solutions/CF1-class-booking-system.md` | Redis Lua atomic ops, distributed reservation — same as frequency capping |

---

### Session 6 — Final Retrieval Test (30 min, day before interview)

No new reading. Verbal-only.

| # | What to do |
|---|---|
| 1 | Open `Interview/Disney/LLD-rate-limiter-java.md` — look only at the "Interview Talking Points Cheatsheet" table. Answer each row out loud without looking at the answers. |
| 2 | Open `Interview/Disney/HLD-ad-impression-pacing.md` — look only at the "Interview Talking Points Cheatsheet" table. Same drill. |
| 3 | Open `Interview/Disney/Java-concurrency-cheatsheet.md` — look only at the "Quick Verbal Cheat Sheet" table. Say answers out loud. |
| 4 | Time yourself doing a 10-min whiteboard sketch of the HLD pipeline. |

---

## 🗺️ File Map

```
Interview/Disney/
├── STUDY-PLAN.md                    ← THIS FILE
├── LLD-rate-limiter-java.md         ← Token Bucket Java impl + concurrency deep dive
├── HLD-ad-impression-pacing.md      ← Budget pacing + impression counting architecture
└── Java-concurrency-cheatsheet.md   ← Verbal CS fundamentals quick reference

Interview/DocuSign/r2-solutions/
├── C1-rate-limiter.md               ← System-level rate limiting (Session 2)
├── D3-notification-service.md       ← Kafka patterns (Session 5)
└── CF1-class-booking-system.md      ← Redis atomic ops (Session 5)

DSA/SystemDesignConcepts/
├── Foundations/Performance-and-Scale/02-rate-limiting.md
├── Foundations/Performance-and-Scale/02-rate-limiting_advanced.md
├── Foundations/Performance-and-Scale/03-caching.md
├── Foundations/Performance-and-Scale/09-sharded-counters.md
├── Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md
├── Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md
├── Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md
└── Core-Architecture/Resilience-and-Fault-Tolerance/10-backpressure.md
```

---

## ⚡ 3 Things That Win or Lose the Round

1. **`computeIfAbsent` without prompting** — if you use `containsKey + put` unprompted, that's an IC2 signal.
2. **CAP theorem with explicit reasoning** — don't just say "AP". Say *why*: "blocking ad breaks during partition is a contract breach; minor over-delivery is reconcilable at EOD."
3. **Mention ZGC for ad serving** — shows production-level thinking. Most candidates never get to GC choice.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 15, 2026 | **File created.** Disney Round 2 study plan. 6 sessions with specific files, checkpoints, and verbal drills. |
