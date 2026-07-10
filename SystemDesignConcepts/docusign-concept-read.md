# DocuSign Interview — Concept Reading Guide

> **Purpose:** Ordered reading list for the 15 concept files most likely to be probed in the DocuSign Commerce Backend System Design round.
> Read Tier 1 first — these apply to every question. Tier 2 + 3 are commerce/DocuSign-specific.
> **Target:** Read each file twice — once today, once tomorrow (faster on second pass).

---

## How to Read Each File

For every file, answer these 3 questions before moving on:
1. **What is the core mechanism?** (how does it actually work, not just what it is)
2. **What is the trade-off?** (what do you gain, what do you lose)
3. **What probe question does this answer?** (the interviewer sentence that triggers this knowledge)

---

## Read-First Prerequisite — Redis Internals

Read this before any other file. It is not probed directly, but four files below (Distributed Locking, Inventory, WebSocket, Caching) all assume you know *why* Redis operations are atomic. Reading it once here means you arrive at those files with the single-threaded model already cold — no context-switch mid-read.

### 0. Redis Internals
**File:** [54-redis-internals.md](Foundations/Performance-and-Scale/54-redis-internals.md)

**Unlocks:** Files 5 (distributed locking), 9 (inventory), 13 (WebSocket) and deepens File 2 (caching)

**Focus on:**
- [ ] Single-threaded event loop — why `DECR` and `SET NX EX` are atomic without any application-level locks
- [ ] Five atomic weapons and when to reach for each: `DECR` (inventory), `SET NX EX` (distributed lock), Lua (conditional atomics), `EXPIRE/SET EX` (ephemeral state), `ZADD` (sliding window / delayed queue)
- [ ] RDB vs AOF — know the `appendfsync everysec` trade-off; this is a Tier 2 probe in inventory/locking questions
- [ ] Eviction policy: `volatile-lru` for mixed cache + coordination; `allkeys-lru` for pure cache
- [ ] Problem → weapon mapping table — skim once to anchor the weapon to each prepared problem

---

## Tier 1 — Fundamentals (probe on EVERY question)

These apply regardless of which HLD question comes up. Read these deepest.

---

### 1. CAP Theorem + Consistency Models
**File:** [34-cap-theorem-consistency-models.md](Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md)

**Probe it answers:** "Do you need strong or eventual consistency here? What happens if there's a network partition?"

**Focus on:**
- [ ] The 3 properties: Consistency, Availability, Partition tolerance — and why you can only pick 2
- [ ] Eventual consistency vs strong consistency — what does "eventual" actually mean in milliseconds?
- [ ] Examples: Postgres = CP, Cassandra = AP, Redis = depends on config
- [ ] How to JUSTIFY your consistency choice in the trade-off section

---

### 2. Caching
**File:** [03-caching.md](Foundations/Performance-and-Scale/03-caching.md)

**Probe it answers:** "How do you scale reads to 5,500 req/sec?" / "What's your cache invalidation strategy?"

**Focus on:**
- [ ] Cache-aside vs write-through vs write-behind — when to use each
- [ ] TTL decisions — how do you choose TTL without causing stale reads?
- [ ] Cache stampede / thundering herd on cache miss
- [ ] Redis vs in-process cache (L1 vs L2)

---

### 3. Database Indexing
**File:** [50-database-indexing.md](Foundations/Data-Fundamentals/50-database-indexing.md)

**Probe it answers:** "How do you make the renewal scheduler query fast?" / "What index do you put on the subscriptions table?"

**Focus on:**
- [ ] B-tree index — what it speeds up, what it doesn't
- [ ] Composite index — column order matters, why
- [ ] Partial index — index only rows matching a WHERE clause (e.g., `WHERE status = 'ACTIVE'`)
- [ ] Covering index — query served entirely from the index, no table heap access
- [ ] Write overhead tradeoff — more indexes = slower writes

---

### 4. Sharding Strategy
**File:** [38-sharding-strategy.md](Core-Architecture/Database-Core/38-sharding-strategy.md)

**Probe it answers:** "What if DocuSign grows to 500M customers?" / "How do you horizontally scale the DB?"

**Focus on:**
- [ ] Range sharding vs hash sharding — pros/cons
- [ ] Shard key selection — what makes a bad shard key (hotspot risk)
- [ ] Cross-shard queries — why they're expensive, how to avoid them
- [ ] Consistent hashing for shard rebalancing
- [ ] When NOT to shard — single Postgres handles more than you think

---

### 5. Distributed Locking
**File:** [06-distributed-locking.md](Foundations/Concurrency-and-Consistency/06-distributed-locking.md)

**Probe it answers:** "Two renewal pods both try to charge the same subscription. How do you prevent that?" / "How do two services coordinate?"

**Focus on:**
- [ ] Redis SETNX + TTL — the basic pattern
- [ ] What happens if the lock holder crashes? (TTL auto-releases)
- [ ] Fencing tokens — why TTL alone is not enough for distributed safety
- [ ] Redlock — Redis multi-node locking, when it matters
- [ ] Use cases: job coordination, idempotent order creation, inventory reservation

---

### 6. Isolation Levels + Dirty Reads
**File:** [41-isolation-levels-dirty-reads.md](Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md)

**Probe it answers:** "What isolation level is your @Transactional using?" / "Can two concurrent checkouts see each other's partial state?"

**Focus on:**
- [ ] READ COMMITTED (Postgres default) — what it prevents, what it allows
- [ ] REPEATABLE READ — prevents phantom reads; when billing calculations need it
- [ ] SERIALIZABLE — strongest, slowest; when is it worth it?
- [ ] Dirty read / non-repeatable read / phantom read — know the difference by example
- [ ] How isolation level interacts with SELECT FOR UPDATE

---

## Tier 2 — Commerce Backend Patterns (commerce-specific probes)

---

### 7. Idempotency
**File:** [04-idempotency.md](Foundations/Concurrency-and-Consistency/04-idempotency.md)

**Probe it answers:** "Client retries the payment. Do we double-charge?" / "What's your idempotency strategy?"

**Focus on:**
- [ ] Idempotency key pattern — client-generated UUID, server stores result
- [ ] Redis vs DB for idempotency key storage — tradeoffs
- [ ] What happens after the idempotency key expires (24h TTL)?
- [ ] Idempotency at the payment gateway level (Stripe idempotency key)

---

### 8. State Machines + Workflows
**File:** [49-state-machines-workflows.md](Production-Grade/System-Design-Patterns/49-state-machines-workflows.md)

**Probe it answers:** "How do you model order lifecycle?" / "How do you prevent invalid status transitions?"

**Focus on:**
- [ ] State + Event → Next State transition table
- [ ] Why state machine beats free-text status field (invalid transitions become impossible)
- [ ] Append-only event log alongside current state — audit + current state in O(1)
- [ ] How to handle the PAST_DUE dunning cycle as a state machine

---

### 9. Inventory Management + Booking
**File:** [42-inventory-management-booking.md](Production-Grade/System-Design-Patterns/42-inventory-management-booking.md)

**Probe it answers:** "How do you prevent overselling during a flash sale?" / "How do you handle the last item in stock?"

**Focus on:**
- [ ] Soft reservation vs hard deduction — reserve first (TTL), deduct on confirmation
- [ ] Optimistic locking (version column) for inventory updates
- [ ] Redis atomic DECR for high-throughput inventory (hot items)
- [ ] Reservation TTL — what happens when user doesn't confirm in 5 minutes?
- [ ] Flash sale architecture — pre-load stock count in Redis, deduct there, sync to DB async

---

### 10. Optimistic vs Pessimistic Locking
**File:** [01-optimistic-pessimistic-locking.md](Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md)

**Probe it answers:** "Two users try to update the same record simultaneously. What happens?" / "Why not just use a DB lock?"

**Focus on:**
- [ ] Optimistic — version column + `UPDATE ... WHERE version = ?` — retry on conflict
- [ ] Pessimistic — `SELECT FOR UPDATE` — holds row lock until commit
- [ ] When optimistic is better: low contention, short transactions
- [ ] When pessimistic is better: high contention (two users booking same seat), can't retry
- [ ] Deadlock risk with pessimistic locking — always lock in consistent order

---

### 11. CDC + Outbox Pattern
**File:** [07-cdc-outbox.md](Foundations/Data-Fundamentals/07-cdc-outbox.md)

**Probe it answers:** "What if Kafka is down when you try to publish the payment event?" / "How do you guarantee at-least-once delivery?"

**Focus on:**
- [ ] The dual-write problem — why you can't write DB + Kafka in two separate steps
- [ ] Outbox table — write event row in same DB transaction as business data
- [ ] Outbox poller — background thread reads unprocessed rows, publishes to Kafka
- [ ] At-least-once delivery — consumers MUST be idempotent
- [ ] Debezium CDC alternative — when it's worth the operational overhead

---

## Tier 3 — Structural + DocuSign-Specific

---

### 12. Dealing with Contention (Pattern)
**File:** [04-dealing-with-contention.md](Patterns/DeepDive/04-dealing-with-contention.md)

**Probe it answers:** "10,000 users hit 'buy' simultaneously for 100 units. How do you handle that?" / "How do you prevent the thundering herd?"

**Focus on:**
- [ ] Queue-based throttling — serialize writes through a bounded queue
- [ ] Sharded counters — split one hot counter into N shards
- [ ] Pre-computed availability — cache available stock in Redis, deduct atomically
- [ ] Jitter for retry — don't retry all at the same time

---

### 13. WebSocket + Real-Time Communication
**File:** [26-websocket-real-time-communication.md](Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md)

**Probe it answers:** "How does the user see their order status update in real-time?" / (Chat messenger is a PDF example — this is mandatory)

**Focus on:**
- [ ] WebSocket vs SSE vs long polling — when to use each
- [ ] WebSocket connection at scale — sticky sessions vs pub/sub fanout
- [ ] Horizontal scaling problem — request goes to Pod A, event comes to Pod B
- [ ] Redis pub/sub as the inter-pod broadcast mechanism

---

### 14. Webhooks (DocuSign-Specific)
**File:** [53-webhooks.md](Core-Architecture/Service-Communication/53-webhooks.md)

**Probe it answers:** "How do you notify external customers when a DocuSign envelope is signed / subscription renews?" / "How do you guarantee webhook delivery?"

**Focus on:**
- [ ] At-least-once delivery with retry + exponential backoff
- [ ] Dead letter queue for failed deliveries
- [ ] Per-customer endpoint health tracking (circuit breaker if endpoint is down)
- [ ] Webhook signing — HMAC signature on payload so receiver can verify it came from DocuSign
- [ ] Tenant isolation — customer A's webhook failure must not affect customer B

---

## Reading Checklist

| # | File | Today | Tomorrow |
|---|---|---|---|
| 0 | [54-redis-internals.md](Foundations/Performance-and-Scale/54-redis-internals.md) ⚡ READ FIRST | [ ] | [ ] |
| 1 | [34-cap-theorem-consistency-models.md](Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md) | [ ] | [ ] |
| 2 | [03-caching.md](Foundations/Performance-and-Scale/03-caching.md) | [ ] | [ ] |
| 3 | [50-database-indexing.md](Foundations/Data-Fundamentals/50-database-indexing.md) | [ ] | [ ] |
| 4 | [38-sharding-strategy.md](Core-Architecture/Database-Core/38-sharding-strategy.md) | [ ] | [ ] |
| 5 | [06-distributed-locking.md](Foundations/Concurrency-and-Consistency/06-distributed-locking.md) | [ ] | [ ] |
| 6 | [41-isolation-levels-dirty-reads.md](Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md) | [ ] | [ ] |
| 7 | [04-idempotency.md](Foundations/Concurrency-and-Consistency/04-idempotency.md) | [ ] | [ ] |
| 8 | [49-state-machines-workflows.md](Production-Grade/System-Design-Patterns/49-state-machines-workflows.md) | [ ] | [ ] |
| 9 | [42-inventory-management-booking.md](Production-Grade/System-Design-Patterns/42-inventory-management-booking.md) | [ ] | [ ] |
| 10 | [01-optimistic-pessimistic-locking.md](Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md) | [ ] | [ ] |
| 11 | [07-cdc-outbox.md](Foundations/Data-Fundamentals/07-cdc-outbox.md) | [ ] | [ ] |
| 12 | [04-dealing-with-contention.md](Patterns/DeepDive/04-dealing-with-contention.md) | [ ] | [ ] |
| 13 | [26-websocket-real-time-communication.md](Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md) | [ ] | [ ] |
| 14 | [53-webhooks.md](Core-Architecture/Service-Communication/53-webhooks.md) | [ ] | [ ] |

---

## Supplementary Reads — High-Value Gaps (read after the core 15)

Ten files not in the core sequence but frequently probed in a commerce backend system design round. Two of them (`S1`, `S2`) are actually **prerequisite-grade** — they underpin files already in the core guide but are never formally covered themselves.

**Why no file from the core sequence was swapped out:** Each of the 15 core files maps to a concrete probe; removing any one creates a gap. These six are additions, not replacements.

| Why they were not in the core guide originally |
|---|
| `S1 / S2` — treated as "background knowledge" but they surface as direct probes in practice |
| `S3` — considered a nice-to-have; actually required for any capacity estimate question |
| `S4 / S5` — commerce-specific patterns added after reviewing DocuSign's likely problem spaces |
| `S6` — resilience patterns were the biggest blind spot in the core list |
| `S7 / S8 / S9 / S10` — added Jul 10 from AlgoMaster gap-closure analysis; true system-design fundamentals that every prep guide assumes you know but no note covered explicitly until now |

### Supplementary Checklist

| # | File | Why it matters for DocuSign Commerce | Read? |
|---|---|---|---|
| S1 | [06-databases-types-and-selection.md](Core-Architecture/Database-Core/06-databases-types-and-selection.md) | Every "what DB do you use and why?" probe lands here — SQL vs NoSQL, when Redis, when Kafka. Without this anchor, DB choices sound arbitrary. User-identified gap. | [ ] |
| S2 | [19-message-queues-kafka-rabbitmq.md](Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md) | Kafka is **assumed** by CDC/Outbox (#11), WebSocket (#13), and Webhooks (#14) but never formally covered. Consumer groups, partition design, Pub/Sub, and at-least-once delivery all live here. Same logic that made Redis Internals a read-first. | [ ] |
| S3 | [52-numbers-to-know-scale-triggers.md](Foundations/Performance-and-Scale/52-numbers-to-know-scale-triggers.md) | Back-of-envelope reasoning — QPS estimates, storage sizing, when to shard, when to cache. Every system design session opens with "how many requests per second are we handling?" — this file gives you the reference numbers. | [ ] |
| S4 | [23-saga-pattern.md](Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md) | DocuSign Commerce has multi-step distributed workflows: payment → provisioning → notification → envelope signing. 2PC breaks at scale. Saga (orchestration vs choreography, compensating transactions) is the answer for partial failure recovery. | [ ] |
| S5 | [47-job-scheduling-at-scale.md](Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md) | Subscription commerce runs on background jobs — renewal schedulers, dunning retry loops, subscription expiry checks. "How do you ensure exactly-once job execution across N pods?" is a live probe in any billing system design. | [ ] |
| S6 | [20-circuit-breaker-resilience.md](Core-Architecture/Resilience-and-Fault-Tolerance/20-circuit-breaker-resilience.md) | The core guide has zero resilience coverage. Commerce-specific probe: "payment gateway starts timing out — what does your system do?" Circuit breaker state transitions (closed → open → half-open), bulkheads, fallback behavior. | [ ] |
| S7 | [55-scalability.md](Foundations/Performance-and-Scale/55-scalability.md) | "How does this scale?" is asked on every system design round. Covers 7 scaling levers (LB, caching, read replicas, sharding, async, CDN, auto-scale) and the bottleneck→lever decision map. Without this, scaling answers sound like a buzzword list instead of a reasoning framework. Common DocuSign probe: "you tripled pod count but P99 got worse — why?" | [ ] |
| S8 | [56-availability.md](Core-Architecture/Resilience-and-Fault-Tolerance/56-availability.md) | The serial dependency math (three 99.9% services = 99.7% end-to-end) is a live Tier 2 probe. Covers nines table, active-passive vs active-active failover, sync vs async replication, and liveness/readiness probe semantics. DocuSign Commerce requires high availability — knowing the math to justify your architecture is the difference between "we add replicas" and a real answer. | [ ] |
| S9 | [57-spof.md](Core-Architecture/Resilience-and-Fault-Tolerance/57-spof.md) | Interviewers circle SPOFs in your design. "You have one Redis node — what happens if it goes down?" Covers 6 SPOF categories including the frequently-missed external dependency SPOFs (payment gateway, SMS provider) and operational SPOFs (one engineer holds the runbook). SPOF identification methodology: walk the dependency graph, ask "what % of requests fail if this node disappears?" | [ ] |
| S10 | [58-stateful-stateless.md](Core-Architecture/Distributed-Systems/58-stateful-stateless.md) | Every horizontal scaling discussion leads here: "where does session state live when you have N pods?" Covers sticky sessions (SPOF, uneven load), Redis centralized session (any pod, 1ms lookup), and JWT (stateless, revocation problem). JWT revocation trade-off is a frequent Tier 2 probe in auth-heavy systems like DocuSign. | [ ] |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 9, 2026 | File created. 14-file reading guide for DocuSign Commerce Backend interview. Finalized after discussion: Tier 1 = fundamentals probed on every question (CAP, caching, indexing, sharding, distributed locking, isolation levels). Tier 2 = commerce patterns (idempotency, state machines, inventory, locking, outbox). Tier 3 = structural + DocuSign-specific (contention pattern, WebSocket, webhooks). |
| Jul 9, 2026 | **`54-redis-internals.md` added as Read-First Prerequisite (item 0).** Not a direct probe but unlocks 4 files (locking, inventory, WebSocket, caching). Placed before Tier 1 as a cross-cutting enabler. Total: 15 files. |
| Jul 10, 2026 | **Supplementary Reads section added (S1–S6).** Core sequence unchanged. Six high-value gaps identified by reviewing the full index: S1 (DB selection — user-identified), S2 (message queues — Kafka prerequisite for 3 core files), S3 (numbers to know — capacity estimation), S4 (Saga pattern — multi-step commerce workflows), S5 (job scheduling — renewal/dunning jobs), S6 (circuit breaker — resilience gap in core guide). |
| Jul 10, 2026 | **S7–S10 added from AlgoMaster gap-closure analysis.** Four new supplementary reads: S7 (55-scalability.md — 7 scaling levers, bottleneck→lever decision map), S8 (56-availability.md — nines table, serial dependency math, active-passive vs active-active), S9 (57-spof.md — 6 SPOF categories, SPOF identification methodology), S10 (58-stateful-stateless.md — sticky sessions vs Redis session vs JWT, JWT revocation problem). Supplementary count: 6 → 10. |
