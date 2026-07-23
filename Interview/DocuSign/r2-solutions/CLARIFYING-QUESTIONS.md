# Universal Clarifying Questions — System Design Interviews

> **Applies to:** DocuSign R2, Disney R3, and any 60-minute system design round.
>
> **The core insight:** Don't memorize 6 problem-specific questions per problem — memorize **8 universal dimensions** to probe on every problem. The questions are always the same; only the answers change. Each answer should flip an architectural decision. If your question's answer doesn't change anything you draw, don't ask it.
>
> **When to use this:** In the first 5 minutes of any system design interview, before you draw a single box.

---

## ⚡ The 8 Universal Dimensions (ask these in order, every time)

### Dimension 1 — Scale 📊
> **"What scale are we designing for — roughly how many users, and what's the expected requests per second?"**

| Answer | What changes in your architecture |
|---|---|
| **Low** (< 1K req/sec, < 10K users) | Single DB, no Redis, no Kafka. Monolith is fine. |
| **Medium** (1K–50K req/sec) | Add Redis cache, read replicas, maybe Kafka for async. |
| **High** (50K–1M req/sec) | Sharding required. Redis is mandatory. Kafka for decoupling. Cassandra instead of Postgres for write-heavy. |
| **Extreme** (> 1M req/sec) | LongAdder in-JVM batching. Sharded counters. Multi-region. CDN mandatory. |

**Follow-up if needed:** "What's the peak vs average ratio?" — A 10× peak means your design must handle 10× without manual intervention.

---

### Dimension 2 — Read/Write Ratio ⚖️
> **"Is this system primarily read-heavy, write-heavy, or balanced?"**

| Answer | What changes |
|---|---|
| **Read-heavy** (100:1 reads:writes) | Cache aggressively. Read replicas. CDN for static content. Denormalize for fast reads. |
| **Write-heavy** (1:1 or more writes) | Write-optimized DB (Cassandra, LSM-tree). Kafka buffer. Async writes. No synchronous joins on write path. |
| **Balanced** | Standard RDBMS with Redis cache layer. |

**Why this matters:** Determines your primary DB choice before you draw anything else.

---

### Dimension 3 — Consistency 🔒
> **"If two users update the same data simultaneously, which is more harmful — showing slightly stale data for a few seconds, or blocking the operation to guarantee accuracy?"**

This is the **AP vs CP** fork — the most important architectural decision in any distributed system.

| Answer | Architecture path |
|---|---|
| **Stale data acceptable** (AP) | Redis without distributed locks. Eventual consistency. Reconciliation at end-of-day. Fail-open on infrastructure failure. |
| **Strong consistency required** (CP) | Redis Lua scripts (atomic check+write). Distributed locks (Redlock). Fail-closed. Latency doubles. |
| **Depends on the operation** | Different consistency per entity — e.g., billing = strong; recommendations = eventual. Name them separately. |

**Disney ad pacing example:** "5% over-delivery is fine; blocking live sports ad breaks is catastrophic." → AP.
**DocuSign digital signature example:** "A signature must be exactly once; no stale state." → CP.

---

### Dimension 4 — Latency SLA ⏱️
> **"What's the acceptable response time? Is there a hard deadline — for example, a real-time auction window?"**

| Answer | What changes |
|---|---|
| **< 10ms** | Everything must be in-process or Redis. No Cassandra on hot path. No synchronous DB writes. In-JVM cache mandatory. |
| **< 100ms** | Redis allowed (~1ms). No Cassandra writes on hot path (5-20ms). Kafka is fire-and-forget (async). |
| **< 1 second** | Cassandra reads OK (~5ms). Synchronous DB reads acceptable for simple queries. |
| **Batch / async** | No latency constraint. Cassandra writes OK. Any DB fine. |

**The RTB window (Disney):** 100ms total — pacing gate must be < 5ms, leaving 95ms for auction logic. This single constraint eliminates every synchronous DB write from the hot path.

---

### Dimension 5 — Availability 🟢
> **"What happens if the system goes down? Is this a revenue-critical real-time service, or can it tolerate brief outages?"**

| Answer | What changes |
|---|---|
| **99.99%** (52 min/year downtime) | Active-active multi-region. Redis cluster with replica failover. Fail-open strategy. Circuit breakers. |
| **99.9%** (8.7 hours/year) | Redis with replicas but single region is OK. Fail-open or fail-closed depending on context. |
| **Best-effort** | Single Redis node OK. No replica required. |

**The fail-open vs fail-closed fork:** Ask yourself: "If our shared state (Redis) goes down, which failure is worse — allowing everything through, or blocking everything?"
- Revenue-critical serving (ads, checkout) → **fail-open** (temporary overage < full outage)
- Security-critical throttling (login attempts, OTP) → **fail-closed** (security breach > brief outage)

---

### Dimension 6 — Scope 🎯
> **"Before I start designing, let me confirm what's in scope — is [auth / monitoring / fraud detection / frequency capping / analytics / multi-tenancy] in scope, or should I skip those?"**

**Always explicitly exclude these unless confirmed in scope:**
- Authentication & authorization (assume auth middleware handles it)
- Monitoring / alerting (mention you'd add it, don't design it)
- Fraud detection (separate ML system)
- Analytics dashboards (separate data warehouse)
- Backfill / migration (operational, not architectural)

**Why this matters:** Without explicit scope, you waste 20 minutes designing auth when the interviewer wanted you to design the core system. Scope questions are the only ones you can ask without the answer changing your architecture — so ask them fast and move on.

---

### Dimension 7 — Data Retention & Compliance 🗃️
> **"How long should data be retained? Are there any compliance requirements — GDPR, SOX, SEC, HIPAA?"**

| Answer | What changes |
|---|---|
| **< 30 days** | Redis TTL sufficient for ephemeral state. No cold storage needed. |
| **1–7 years** | S3 Glacier for cold tier. Cassandra for warm tier. Separate retention policy per data type. |
| **Legal holds / compliance** | Immutable audit log (append-only, no DELETE). DB triggers to prevent tampering. Two-jurisdiction trap: GDPR "right to erasure" vs SEC 7-year retention — need separate PII-scrubbing path that preserves the event record. |

**DocuSign angle:** Every signed document is a legal artifact. 7-year retention. Audit log must be immutable. GDPR "right to erasure" applies to PII metadata, not the signed document itself.

---

### Dimension 8 — Geographic Scope 🌐
> **"Is this single-region or global? Do we need to serve users in the EU, APAC, or other regions with data residency requirements?"**

| Answer | What changes |
|---|---|
| **Single region** | One Redis cluster. One Cassandra cluster. No cross-region complexity. |
| **Multi-region, shared budget** | Home-region assignment (each entity assigned to one region; GeoDNS routes traffic there). Cross-region Redis sync is NOT on the hot path — RTT is 80-150ms. |
| **Multi-region, independent** | Regional isolation. Separate budgets/counters per region. Global reconciliation job runs async (every 5 minutes). |
| **Data residency required** | EU data cannot leave EU. US data cannot leave US. Separate DB clusters per jurisdiction. JWT must encode region so routing is deterministic. |

---

## 🧭 Problem-Type Add-On Questions (ask ONE of these based on the problem)

### For any budget / quota / counter system:
> **"What's the acceptable over-delivery or over-limit tolerance — zero, or is some percentage acceptable?"**
- 0% → CP required → Redis Lua / distributed lock → latency doubles
- 5% acceptable → AP → INCR without locking → fail-open → reconcile later

### For any billing / payment system:
> **"Does billing require exact counts (each event billed exactly once), or is approximate sufficient?"**
- Exact → Kafka + idempotency key + exactly-once semantics (careful offset commit after Cassandra write)
- Approximate → simpler consumer group with in-memory accumulator

### For any notification / messaging system:
> **"What's the delivery guarantee — at-least-once (duplicate acceptable) or exactly-once (no duplicate)?"**
- At-least-once → simpler; recipient handles dedup
- Exactly-once → idempotency key per notification + Redis dedup check (24h TTL)

### For any rate limiting system:
> **"Should the rate limiter fail-open (allow all when Redis is down) or fail-closed (block all)?"**
- Fail-open → quota enforcement is the goal; temporary overages < blocking all users
- Fail-closed → security is the goal; blocking is safer than letting attacks through

### For any real-time system (live sports, auctions, gaming):
> **"What's the hard latency deadline — is there an RTB window, a game tick, or an event horizon I need to design within?"**
- This number becomes the filter for every component: anything that adds more than X ms to the critical path is disqualified.

---

## ❌ Questions NOT worth asking (don't waste your 5 minutes)

| Bad question | Why it's a waste |
|---|---|
| "What programming language?" | Never changes the architecture |
| "What cloud provider — AWS, GCP, Azure?" | Mention S3/Redis/Kafka generically; provider is irrelevant |
| "What framework or library?" | Implementation detail; answer doesn't change the design |
| "How many engineers on the team?" | Organizational, not architectural |
| "Should we use microservices or monolith?" | You decide this based on the scale answer — don't ask |
| "What database should I use?" | You recommend this — that's why they're interviewing you |

---

## 🎯 The 5-minute script (say this verbatim to open any interview)

> "Before I start drawing, let me ask a few clarifying questions to make sure I'm designing the right system.
>
> **Scale:** Roughly how many users and requests per second are we targeting?
>
> **Consistency:** If two users update the same data simultaneously, is showing slightly stale data for a few seconds acceptable, or do we need strong consistency?
>
> **Latency:** Is there a hard response time requirement — for example, a real-time window I need to fit within?
>
> **Availability:** What happens if the system goes down — is this revenue-critical, or can it tolerate brief outages?
>
> **Scope:** I'll assume auth is handled by middleware. Is [frequency capping / analytics / fraud detection] in scope, or should I skip those?
>
> And one question specific to this problem: [insert Dimension add-on from the table above]
>
> Great — let me state my assumptions and start with the scale estimation."

---

## 🧠 The mental model: Every question is an architecture fork

Draw this in your head before asking:

```
                    "Is stale data OK?"
                          │
              ┌───────────┴───────────┐
             YES                     NO
              │                       │
            (AP)                    (CP)
              │                       │
    Redis INCR,                Redis Lua script,
    fail-open,                 distributed lock,
    reconcile later            fail-closed
```

If your question doesn't produce a fork like this — skip it.
Every question must reveal which of two (or three) architectural paths you take.
If both answers lead to the same design, the question is wasting interview time.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 23, 2026 | **Created.** Universal clarifying questions framework — 8 dimensions applicable to any system design interview. Triggered by observation that per-problem question lists are hard to memorize under pressure; universal dimensions + fork-based reasoning is more durable. Applies to DocuSign R2 and Disney R3. |
