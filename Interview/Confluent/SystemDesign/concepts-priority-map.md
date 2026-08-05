# Confluent — System Design Concepts Priority Map

> **Scope:** All 60 concepts from `SystemDesignConcepts/INDEX.md`, prioritized for Confluent's confirmed question bank.
>
> **Based on:** [sd-research.md](./sd-research.md) — 18-month research pass (Feb 2025–Aug 2026).
>
> **Confirmed questions driving this map:**
> - **TempMail** — disposable email with TTL + Bloom filter (3 reports, latest May 2026)
> - **News Feed** — RSS ingestion pipeline, reliability-first (3 reports, latest Apr 2026)
> - **API Design Round** — pure REST contract, every verb/code/header probed (3+ reports, latest Apr 2026)
> - **KV Store** — globally distributed, read-optimized (2 reports, latest Oct 2025)
> - **URL Shortener** — reliable, unique ID generation (1 in-window + 3 historical)
> - **Job Scheduling** — concurrency round, design + code (1 report, Blind 2025)
> - **Health Check** — "wasAlive" monitoring, 5 slots × 100ms (1 report, May 2026)
> - **DB+SQL+API** — combined schema + API round (1 report, Apr 2026)
>
> **Root path:** Links resolve from `Interview/Confluent/SystemDesign/` → `../../../SystemDesignConcepts/`

---

## ✅ MUST DO — 18 concepts

> Know these cold. They map directly to a named confirmed question. If you blank on any of these mid-round, the design collapses.

| # | Concept | Confluent Will Probe This Because |
|---|---|---|
| 11 | [API Design (REST, pagination, versioning)](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/11-api-design.md) | **API Design Round** (confirmed 3x+). The standalone round with no diagrams — every verb, status code, header is a test point. "If you make any mistake they highlight it as if the world has ended." |
| 12 | [Relational Data Modeling](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md) | **DB+SQL+API Round** (Apr 2026). Schema design for TempMail inbox table, URL Shortener short-code table, News Feed article+subscription tables. |
| 08 | [Bloom Filter](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/08-bloom-filter.md) | **TempMail** (May 2026). Bloom filter discussion consumed the ENTIRE round. "Does this inbox exist?" fast-path lookup. Prepare for deep-dive on false positive rate, size calculation, hash functions, alternatives. |
| 04 | [Idempotency (HTTP + Kafka consumer)](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md) | **API Design Round** — Idempotency-Key header for safe retries is a staple. **News Feed** — at-least-once Kafka consumer must not re-publish duplicate articles. |
| 43 | [Pagination — Cursor-Based](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/43-pagination-cursor-based.md) | **API Design Round** + **News Feed** — "how do you paginate the feed?" The cursor-vs-offset probe is nearly universal. Offset breaks at scale; cursor is the answer. |
| 02 | [Rate Limiting (token bucket, sliding window)](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md) | **TempMail** (prevent address spam + inbox flood), **URL Shortener** (abuse prevention), **API Design Round** (429 Too Many Requests in the contract). |
| 03 | [Caching (5 strategies, eviction, stampede)](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md) | **URL Shortener** — 80% traffic to top 20% links; Cache-Aside is the design. **KV Store** — read optimization. **TempMail** — hot inbox lookup via Redis. |
| 19 | [Message Queues — Kafka vs RabbitMQ](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md) | **News Feed** — ingestion backbone (Kafka, not a task queue). **Tableflow team** — you are building on Kafka; this is domain fluency, not optional. "Why Kafka and not RabbitMQ?" must be crisp. |
| 50 | [Database Indexing (B-tree, composite, covering, EXPLAIN)](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/50-database-indexing.md) | **DB+SQL+API Round** — "how will you query by short code?", "how do you look up inbox by email address?" Index design follows every schema you draw. |
| 52 | [Numbers to Know & Scale Triggers](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/52-numbers-to-know-scale-triggers.md) | **Every question.** Back-of-envelope before any infrastructure decision. "How many requests per second?" → validates your choice of single vs. distributed DB, cache size, Kafka partition count. |
| 34 | [CAP Theorem & Consistency Models](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md) | **KV Store** (Oct 2025) — "globally distributed" forces a consistency model choice. "Why Cassandra and not MySQL?" is a CAP question. Prepare eventual vs. strong consistency trade-off defense. |
| 38 | [Sharding Strategy (range, hash, directory)](../../../SystemDesignConcepts/Core-Architecture/Database-Core/38-sharding-strategy.md) | **KV Store** — distributed storage requires hash sharding + consistent hashing. **URL Shortener** — short code space partitioning. |
| 05 | [Consistent Hashing (ring, virtual nodes)](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/05-consistent-hashing.md) | **KV Store** — cache ring for distributed read-optimized lookup. Consistent hashing = how you add nodes without reshuffling all keys. |
| 25 | [Monitoring & Observability (logs, metrics, traces)](../../../SystemDesignConcepts/Production-Grade/Observability/25-monitoring-observability-fundamentals.md) | **Health Check / wasAlive** (May 2026) — the design IS a monitoring system. Also: 99.99% uptime in JD requires you to articulate SLOs, alerting, and the three pillars. |
| 56 | [Availability (nines table, serial/parallel math)](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/56-availability.md) | **Health Check** — calculating "3 consecutive inactive = down" requires understanding availability math. **JD explicitly states 99.99%** — "how do you achieve four nines?" is a live probe. |
| 07 | [CDC + Outbox Pattern](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/07-cdc-outbox.md) | **News Feed** — ingesting from publishers reliably. Dual-write risk (write DB + publish event) is a real failure mode. Outbox pattern is the Kafka-native answer. |
| 47 | [Job Scheduling at Scale (CAS claim, heartbeat, SKIP LOCKED)](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md) | **Job Scheduling** — confirmed question (Blind 2025). This IS the concurrency round. CAS-based job claim, heartbeat for stuck-job detection, delayed job queue. Design + code required. |
| 22 | [Event Sourcing (immutable log, replay, temporal queries)](../../../SystemDesignConcepts/Core-Architecture/Database-Core/22-event-sourcing.md) | **Tableflow team** — you are literally materializing Kafka topics into Iceberg tables. Kafka IS an event log; Tableflow IS event sourcing into the data lake. If there is any product-specific depth probe, this is it. |

---

## 🟡 GOOD TO DO — 20 concepts

> Know the concept and the trigger. Ready to pull into any answer as a follow-up or depth signal. Not guaranteed to appear but will show up as probes when you name the parent concept.

| # | Concept | When Confluent Will Reach For It |
|---|---|---|
| 06b | [Database Types & Selection (SQL, NoSQL, Redis, Kafka, ES)](../../../SystemDesignConcepts/Core-Architecture/Database-Core/06-databases-types-and-selection.md) | Every storage decision in every question. "Why Postgres for the inbox metadata?" "Why Redis for TTL?" Justification is always required. |
| 10 | [Backpressure (bounded queues, load shedding)](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/10-backpressure.md) | **News Feed** — "what if publishers respond slowly or burst?" Producer outpacing consumer is the exact failure mode Kafka was designed for. |
| 31 | [CQRS (Command-Query separation, projections)](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/31-cqrs-read-write-separation.md) | **News Feed** — write path (ingestion pipeline) has a completely different shape than read path (user feed generation). **KV Store** — read-heavy optimization requires separate read models. |
| 35 | [Retry & Exponential Backoff Patterns](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md) | **News Feed** — polling unreliable external RSS publishers. **Job Scheduling** — failed job retry with exponential backoff + jitter. Universal follow-up probe. |
| 41 | [Isolation Levels & Dirty Reads](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md) | **DB+SQL Round** — "which isolation level do you choose for TempMail inbox creation?" Read Committed vs. Serializable trade-off with justification. |
| 45 | [Hot Partition Problem (write salting, hot-key caching)](../../../SystemDesignConcepts/Core-Architecture/Database-Core/45-hot-partition-problem.md) | **URL Shortener** — viral link = 10M reads/sec to one short code. **News Feed** — popular publisher floods one Kafka partition. |
| 29 | [Database Replication (master-slave, WAL, RPO/RTO)](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md) | **KV Store** — globally distributed requires replication. **Health Check** — "what if the monitoring DB goes down?" 99.99% uptime needs HA. |
| 20 | [Circuit Breaker & Resilience (closed/open/half-open)](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/20-circuit-breaker-resilience.md) | **News Feed** — polling external RSS publishers that can go down. Prevent cascading failure when publisher is unreachable. |
| 15 | [System Qualities — 7 Evaluation Dimensions](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/15-system-qualities.md) | The structural frame for every answer. Use to ensure you cover availability, consistency, latency, durability, scalability. Opens every design. |
| 55 | [Scalability (7 levers: LB · caching · replicas · sharding · async · CDN · auto-scale)](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/55-scalability.md) | Every question has a "how does this scale to 100M users?" follow-up. The 7-lever mental model is the answer structure. |
| 57 | [Single Point of Failure — SPOF](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/57-spof.md) | **Health Check design** requires identifying SPOFs (what if the check service itself goes down?). **99.99% uptime JD** — SPOF elimination is how you get there. |
| 58 | [Stateful vs Stateless](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/58-stateful-stateless.md) | **API Design Round** + **Job Scheduling** — "is this service stateless? what state does it carry?" Stateless services scale horizontally; stateful ones need sticky sessions or external state store. |
| 49 | [State Machines in Workflows (FSM, CAS enforcement)](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/49-state-machines-workflows.md) | **Job Scheduling** — job state transitions (PENDING → RUNNING → DONE → FAILED). **TempMail** — inbox lifecycle (ACTIVE → EXPIRED). CAS enforcement prevents illegal transitions. |
| 01 | [Optimistic + Pessimistic Locking](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md) | **URL Shortener** — collision on short code generation. **TempMail** — preventing two requests from creating the same inbox address simultaneously. Row-level concurrency probe. |
| 06a | [Distributed Locking (Redis SETNX, Redlock, fencing token)](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/06-distributed-locking.md) | **Job Scheduling** — only one worker should claim a job (CAS claim OR distributed lock). Cross-service resource contention. Distinct from row-level locking above. |
| 54 | [Redis Internals (single-threaded loop, 5 atomic weapons, RDB vs AOF)](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/54-redis-internals.md) | **TempMail** — EXPIRE weapon (TTL), SET NX EX (atomic inbox reservation), BITSET (Bloom filter). **URL Shortener** — INCR for counter, EXPIRE for cache TTL. Redis is the implementation layer for most Confluent questions. |
| 48 | [Feature Flags / A/B Testing (deterministic bucketing)](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/48-feature-flags-ab-testing.md) | **Domain-inferred** — TechPrep 2026 mentions "Distributed Feature Flag System" as Confluent prep. LOW confidence (no candidate attribution), but aligns with Confluent Cloud's product surface. |
| 40 | [Multi-Region & Geo-Failover](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md) | **KV Store** — the confirmed question is "**globally** distributed, read-optimized." That's geo-replication. How do you route reads to the nearest replica? How do you handle region failure? |
| 30 | [Distributed Tracing (trace ID, spans, sampling)](../../../SystemDesignConcepts/Production-Grade/Observability/30-distributed-tracing-spans.md) | **Health Check** + **99.99% uptime** — observability depth probe. "How do you debug a slow TempMail lookup across services?" Likely follow-up to #25 Monitoring. |
| 44 | [Graceful Degradation & Fallbacks](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/44-graceful-degradation-fallbacks.md) | **99.99% availability JD** — "what if the Bloom filter store is down?" Return partial function (reject new inboxes but serve existing ones) vs. full outage. |

---

## 📘 ASSUMED BASELINE — 2 concepts

> Tier-1 universals in the DESIGN-WEIGHT-MAP but low Confluent-specific signal. Know the concept at a surface level — it will come up in passing, not as a probed topic. Don't drill these; you'll pick them up in context from other questions.

| # | Concept | Why Baseline, Not Drill |
|---|---|---|
| 17 | [Load Balancing (Round Robin, Least Connections, Sticky)](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/17-load-balancing-algorithms.md) | Assumed infrastructure knowledge at SSE level. You'll mention it when saying "put a load balancer in front" but Confluent's confirmed questions don't probe LB algorithms specifically. |
| 28 | [CDN — Content Delivery Network (edge caching, TTL, invalidation)](../../../SystemDesignConcepts/Production-Grade/Performance-Optimization/28-cdn-edge-caching.md) | Confluent's confirmed questions (TempMail, News Feed, KV Store, URL Shortener) have no meaningful CDN angle — no static assets, no global frontend delivery. Safe to know the concept without drilling. |

---

## ❌ CAN SKIP — 20 concepts

> Not in Confluent's confirmed question bank. Prepping these over MUST DO concepts is an opportunity cost. Skip unless you have spare time after covering everything above.

| # | Concept | Why Skip for Confluent |
|---|---|---|
| 51 | [Geospatial Indexing (geohash, quad tree, H3)](../../../SystemDesignConcepts/Core-Architecture/Database-Core/51-geospatial-indexing.md) | No location-based product in any confirmed Confluent question. (Uber/Tinder question bank, not Confluent.) |
| 46 | [Push Notifications / Fanout at Scale (APNs, FCM)](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/46-push-notifications-fanout.md) | No mobile push in Confluent's question bank. News feed uses Kafka fan-out, not APNs/FCM. |
| 42 | [Inventory Management & Booking](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/42-inventory-management-booking.md) | No booking system in confirmed questions. (Hotel/ticket booking is Amazon/Flipkart territory.) |
| 32 | [Elasticsearch (inverted index, sharding, full-text search)](../../../SystemDesignConcepts/Production-Grade/Performance-Optimization/32-elasticsearch-inverted-index.md) | No full-text search question confirmed. Confluent's questions are ingestion + API design, not search. |
| 26 | [WebSocket (HTTP upgrade, bidirectional, real-time)](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md) | No real-time chat or collaborative editing in confirmed questions. |
| 37 | [Consensus Algorithms — Raft vs Paxos](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/37-consensus-algorithms-raft-vs-paxos.md) | Principal-engineer level depth. Confluent SSE loop doesn't probe consensus algorithm internals. |
| 36 | [Two-Phase Commit vs Saga](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/36-two-phase-commit-vs-saga.md) | No distributed transaction question confirmed. Saga pattern itself isn't confirmed either. Skip the deep-dive variant. |
| 23 | [Saga Pattern (orchestration vs choreography)](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md) | No multi-service distributed transaction question in confirmed bank. |
| 33 | [gRPC & Protocol Buffers](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/33-grpc-protocol-buffers.md) | Not in confirmed questions. Confluent's API round focuses on REST, not gRPC. |
| 18 | [Service Discovery (DNS, Client-side, Server-side)](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/18-service-discovery-dns.md) | Microservices infrastructure concern. Confluent's questions are product-level design, not infra architecture. |
| 27a | [Auth & AuthZ (JWT, OAuth 2.0, RBAC, ABAC)](../../../SystemDesignConcepts/Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md) | No "design an auth system" question confirmed. Confluent focuses on API design (what endpoints), not auth (who can call them). |
| 27b | [JWT Token Storage Reference](../../../SystemDesignConcepts/Production-Grade/Auth-and-Security/27-jwt-token-storage-reference.md) | Frontend/browser concern. Irrelevant for backend SSE role. |
| 13 | [Security + PKI Fundamentals (TLS, digital signatures)](../../../SystemDesignConcepts/Production-Grade/Auth-and-Security/13-security-pki.md) | No security system design confirmed. Fintech/healthtech territory, not Confluent. |
| 39 | [Bulkheads & Resource Isolation](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/39-bulkheads-resource-isolation.md) | Graceful Degradation (#44, Good-to-do) covers the outcome. Bulkhead implementation detail not probed at SSE level. |
| 24 | [API Gateway (routing, auth, rate limiting at edge)](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/24-api-gateway-pattern.md) | Not in confirmed questions. Confluent's API round focuses on the contract, not the gateway in front of it. |
| 09 | [Sharded Counters (CRDT, time-series, adaptive)](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/09-sharded-counters.md) | No leaderboard or high-write counter question confirmed. YouTube views / product ratings territory. |
| 14 | [Document & Blob Storage (S3, metadata DB, versioning)](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/14-document-blob-storage.md) | No file upload question confirmed. DocuSign, Dropbox territory. |
| 16 | [Connection Pooling (HikariCP, pool sizing, leak detection)](../../../SystemDesignConcepts/Core-Architecture/Database-Core/16-connection-pooling-db-performance.md) | Infrastructure implementation detail. Interviewers at Confluent's product level won't probe HikariCP config. |
| 21 | [Leader Election & Consensus (Raft, Zookeeper, split-brain)](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/21-leader-election-consensus.md) | Not in confirmed questions. Only relevant for stateful distributed systems where exactly one node must act — not Confluent's question shapes. |
| 53 | [Webhooks (HMAC signing, idempotency, replay prevention)](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/53-webhooks.md) | Not in confirmed question bank. RSS feed uses polling, not webhooks. No DocuSign-type question confirmed. |

---

## 🗺️ Study Sequence for Confluent

**Day 1–3 (API round survival):**
#52 → #11 → #43 → #04 → #02 → #12 → #50

**Day 4–6 (TempMail round survival):**
#08 → #03 → #54 → #56 → #25 → #49

**Day 7–9 (News Feed round survival):**
#19 → #07 → #10 → #31 → #35 → #20

**Day 10–12 (KV Store + URL Shortener):**
#05 → #38 → #34 → #29 → #40 → #45

**Day 13–14 (Job Scheduling + DB round):**
#47 → #06a → #01 → #41 → #22 → #15 → #55

**Day 15+ (depth probes, good-to-do sweep):**
#57 → #58 → #44 → #48 → #30 → #06b

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. All 60 INDEX concepts assigned to MUST DO (18) / GOOD TO DO (20) / ASSUMED BASELINE (2) / CAN SKIP (20). Mappings grounded in 8 confirmed Confluent question types from sd-research.md (18-month window). |
