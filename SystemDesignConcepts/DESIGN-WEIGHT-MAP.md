# DESIGN-WEIGHT-MAP

> **How to use this file:**
> Every concept in this knowledge base is assigned a tier based on how universally it appears in system design interviews.
> This is NOT a difficulty ranking — it is a **study-priority ranking**.
> Use it to decide what to drill first, what to study second, and what to pull out only when the problem shape demands it.

---

## 🗺️ Study Path — Four-Week Sequence

> **Week 1:** Tier 1 — know all 11 cold. These appear in every interview regardless of the problem.
>
> **Week 2:** Tier 2 — learn the trigger clusters relevant to your target role. Microservices shop → start there. Data-heavy system → start with Data-Flow cluster.
>
> **Week 3:** Tier 3 ⭐ — the 8 high-frequency specialists. Most senior candidates miss at least 2 of these. Study them before the interview regardless of the job description.
>
> **Week 4:** Problem-specific Tier 3 — match to the JD or the interview problem type. "Booking system" → Inventory Management + State Machines. "Location app" → Geospatial Indexing. "Mobile app" → Push Notifications.

---

## Tier 1 — Universal

> **Every system design will touch these.** If the interviewer asks you to design anything, you will draw on at least 5 of these 11 concepts before finishing the first whiteboard pass. Know them cold — no "let me think about this" pauses.

| # | Concept | The trigger |
|---|---|---|
| 52 | [Numbers to Know & Scale Triggers (2026 baselines, back-of-envelope)](./Foundations/Performance-and-Scale/52-numbers-to-know-scale-triggers.md) | Before every design — do the math before adding infrastructure |
| 03 | [Caching (5 strategies, eviction, stampede)](./Foundations/Performance-and-Scale/03-caching.md) | Any read-heavy path; first optimization you propose |
| 06 | [Database Types & Selection (SQL, NoSQL, Redis, Kafka, ES)](./Core-Architecture/Database-Core/06-databases-types-and-selection.md) | You pick a storage layer — justify it |
| 11 | [API Design (REST, pagination, versioning)](./Foundations/Data-Fundamentals/11-api-design.md) | Every system has an API contract to define |
| 12 | [Relational Data Modeling](./Foundations/Data-Fundamentals/12-data-modeling.md) | Schema design comes up in every data-backed system |
| 50 | [Database Indexing (B-tree, composite, covering, EXPLAIN)](./Foundations/Data-Fundamentals/50-database-indexing.md) | "How will you query that?" — the indexing probe follows every schema |
| 16 | [Connection Pooling (HikariCP, pool sizing, leak detection)](./Core-Architecture/Database-Core/16-connection-pooling-db-performance.md) | Every service-to-DB connection at scale |
| 17 | [Load Balancing (Round Robin, Least Connections, Sticky)](./Core-Architecture/Service-Communication/17-load-balancing-algorithms.md) | Every multi-instance deployment |
| 25 | [Monitoring & Observability (logs, metrics, traces)](./Production-Grade/Observability/25-monitoring-observability-fundamentals.md) | Every production system needs SLO + alerting |
| 02 | [Rate Limiting (token bucket, sliding window)](./Foundations/Performance-and-Scale/02-rate-limiting.md) | Every public-facing API; also shows up in internal abuse prevention |
| 28 | [CDN — Content Delivery Network (edge caching, TTL, invalidation)](./Production-Grade/Performance-Optimization/28-cdn-edge-caching.md) | Any system serving static assets or global traffic |

---

## Tier 2 — Frequent (Know Your Triggers)

> **These 21 concepts are pulled in by a specific design property.** When you see that property in the problem, you reach for the matching cluster. Study the cluster most relevant to your target company type first.

---

### 🔧 Microservices Triggers
*"The system is decomposed into multiple services."*

| # | Concept | Trigger |
|---|---|---|
| 24 | [API Gateway (routing, auth, rate limiting at edge)](./Production-Grade/System-Design-Patterns/24-api-gateway-pattern.md) | Single entry point needed for multiple downstream services |
| 18 | [Service Discovery (DNS, Client-side, Server-side)](./Core-Architecture/Service-Communication/18-service-discovery-dns.md) | Services need to find each other dynamically at scale |
| 30 | [Distributed Tracing (trace ID, spans, sampling)](./Production-Grade/Observability/30-distributed-tracing-spans.md) | "How do you debug a slow request across 5 services?" |
| 33 | [gRPC & Protocol Buffers (HTTP/2, streaming, binary)](./Core-Architecture/Service-Communication/33-grpc-protocol-buffers.md) | Internal service-to-service communication needing performance |
| 20 | [Circuit Breaker & Resilience (closed/open/half-open)](./Core-Architecture/Resilience-and-Fault-Tolerance/20-circuit-breaker-resilience.md) | A downstream service can be slow or unreliable |

---

### 🔧 Scale Triggers
*"The system must handle growth — more reads, more writes, more data."*

| # | Concept | Trigger |
|---|---|---|
| 38 | [Sharding Strategy (range, hash, directory)](./Core-Architecture/Database-Core/38-sharding-strategy.md) | Single DB node can't hold the write or storage load |
| 29 | [Database Replication (master-slave, WAL, RPO/RTO)](./Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md) | Read-heavy system or HA requirement |
| 05 | [Consistent Hashing (ring, virtual nodes)](./Foundations/Performance-and-Scale/05-consistent-hashing.md) | Distributed cache or any horizontally scaled stateful tier |
| 40 | [Multi-Region & Geo-Failover](./Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md) | System must be globally available or survive region failure |
| 10 | [Backpressure (bounded queues, load shedding)](./Core-Architecture/Resilience-and-Fault-Tolerance/10-backpressure.md) | Producer can outpace consumer; async processing pipeline |
| 34 | [CAP Theorem & Consistency Models + PACELC](./Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md) | Choosing between SQL/NoSQL, replication strategy, or explaining consistency guarantees — every multi-node data store choice is a CAP decision |

---

### 🔧 Data-Flow Triggers
*"Events need to propagate reliably between systems."*

| # | Concept | Trigger |
|---|---|---|
| 19 | [Message Queues (RabbitMQ task queue vs Kafka event stream)](./Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md) | Async, decoupled communication; workload offloading |
| 07 | [CDC + Outbox Pattern](./Foundations/Data-Fundamentals/07-cdc-outbox.md) | DB write + event publish must be atomic (no dual-write risk) |
| 22 | [Event Sourcing (immutable log, temporal queries, replay)](./Core-Architecture/Database-Core/22-event-sourcing.md) | Audit trail required; state reconstructed from events |
| 31 | [CQRS (Command-Query separation, projections)](./Production-Grade/System-Design-Patterns/31-cqrs-read-write-separation.md) | Read model needs a different shape or scale than the write model |

---

### 🔧 Product-Shape Triggers
*"The product has a specific capability that drives architecture."*

| # | Concept | Trigger |
|---|---|---|
| 26 | [WebSocket (HTTP upgrade, bidirectional, real-time)](./Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md) | Live chat, collaborative editing, real-time notifications |
| 32 | [Elasticsearch (inverted index, sharding, full-text search)](./Production-Grade/Performance-Optimization/32-elasticsearch-inverted-index.md) | Full-text search, autocomplete, log analytics |
| 27 | [Auth & AuthZ (JWT, OAuth 2.0, RBAC, ABAC)](./Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md) | Any user-facing system — who can do what |
| 06dist | [Distributed Locking (Redis SETNX, Redlock, fencing token)](./Foundations/Concurrency-and-Consistency/06-distributed-locking.md) | Cross-service resource contention — only one actor can proceed at a time¹ |
| 23 | [Saga Pattern (orchestration vs choreography, compensating tx)](./Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md) | Distributed transaction spanning multiple services |
| 21 | [Leader Election & Consensus (Raft, Zookeeper, split-brain)](./Core-Architecture/Distributed-Systems/21-leader-election-consensus.md) | Stateful distributed system where exactly one node must act |

> ¹ **Locking note:** Distributed Locking (Tier 2) is cross-*service* coordination via Redis. Optimistic/Pessimistic Locking (Tier 3 ⭐) is single-DB row-level concurrency. Same word "locking," different layers — don't confuse them in an interview.

---

## Tier 3 ⭐ — High-Frequency Specialists

> **Study these before your interview, regardless of the problem type.** These 8 concepts are the most common "follow-up probes" that interviewers use to separate senior candidates from mid-level candidates. You will be asked about them even if the main problem doesn't obviously call for them.

| # | Concept | Why it's ⭐ |
|---|---|---|
| 01 | [Optimistic + Pessimistic Locking](./Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md) | DB-row concurrency; every booking, inventory, or payment schema triggers this¹ |
| 04 | [Idempotency (HTTP + Kafka consumer)](./Foundations/Concurrency-and-Consistency/04-idempotency.md) | Payment retries, at-least-once delivery — probed in almost every payment or API design |
| 08 | [Bloom Filter](./Foundations/Data-Fundamentals/08-bloom-filter.md) | DB read optimisation, URL shortener dedup, cache negative-lookup — a clean signal of depth |
| 35 | [Retry & Exponential Backoff Patterns](./Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md) | Any unreliable dependency; interviewers probe retry strategy in every resilient design |
| 41 | [Isolation Levels & Dirty Reads](./Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md) | Transaction-heavy designs; "what isolation level do you choose and why?" |
| 43 | [Pagination — Cursor-Based](./Foundations/Data-Fundamentals/43-pagination-cursor-based.md) | Every paginated API; the cursor-vs-offset probe is nearly universal |
| 45 | [Hot Partition Problem (write salting, hot-key caching)](./Core-Architecture/Database-Core/45-hot-partition-problem.md) | Write-heavy or viral-content designs; one of the most common Tier 2 probe questions |
| 48 | [Feature Flags / A/B Testing (deterministic bucketing)](./Production-Grade/System-Design-Patterns/48-feature-flags-ab-testing.md) | Large-company deployments; Walmart context makes this highly relevant |

> ¹ Optimistic/Pessimistic Locking = single-DB row concurrency (`SELECT FOR UPDATE`, `@Version`). See Distributed Locking (Tier 2) for cross-service coordination via Redis.

---

## Tier 3 — Problem-Specific Specialists

> **Pull these out when the problem shape matches.** Study them after covering Tier 1 + Tier 2 + Tier 3 ⭐. Each concept below lists the trigger — if you see that trigger in your interview problem, add it to your design.
>
> *(14 concepts — CAP Theorem was promoted to Tier 2 Scale Triggers because every database selection and replication decision is a CAP decision, making it a universal trigger rather than a problem-specific one.)*

| # | Concept | When to reach for it |
|---|---|---|
| 51 | [Geospatial Indexing (geohash, quad tree, H3, Redis GEO)](./Core-Architecture/Database-Core/51-geospatial-indexing.md) | Location-based product: Uber, Swiggy, Tinder, Airbnb, "find nearby X" |
| 09 | [Sharded Counters (CRDT, time-series, adaptive)](./Foundations/Performance-and-Scale/09-sharded-counters.md) | High-write counters: YouTube views, product ratings, leaderboards |
| 46 | [Push Notifications / Fanout at Scale (APNs, FCM, Kafka)](./Core-Architecture/Service-Communication/46-push-notifications-fanout.md) | Mobile app with notification system; fan-out to millions of devices |
| 47 | [Job Scheduling at Scale (CAS claim, heartbeat, SKIP LOCKED)](./Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md) | Background jobs, async batch processing, delayed/scheduled tasks |
| 42 | [Inventory Management & Booking](./Production-Grade/System-Design-Patterns/42-inventory-management-booking.md) | Hotel/flight/ticket booking; flash sales with limited stock |
| 49 | [State Machines in Workflows (FSM, CAS enforcement)](./Production-Grade/System-Design-Patterns/49-state-machines-workflows.md) | Order lifecycle, payment state, approval workflows |
| 14 | [Document & Blob Storage (S3, metadata DB, versioning)](./Foundations/Data-Fundamentals/14-document-blob-storage.md) | File upload system, document signing, image/video storage |
| 13 | [Security + PKI Fundamentals (TLS, digital signatures)](./Production-Grade/Auth-and-Security/13-security-pki.md) | Security-critical systems: fintech, legal docs, healthcare |
| 44 | [Graceful Degradation & Fallbacks](./Core-Architecture/Resilience-and-Fault-Tolerance/44-graceful-degradation-fallbacks.md) | High-availability design where partial function beats full outage |
| 39 | [Bulkheads & Resource Isolation](./Core-Architecture/Resilience-and-Fault-Tolerance/39-bulkheads-resource-isolation.md) | Service must isolate failure from one tenant/path to another |
| 36 | [Two-Phase Commit vs Saga](./Core-Architecture/Resilience-and-Fault-Tolerance/36-two-phase-commit-vs-saga.md) | Distributed transaction depth probe; when Saga (Tier 2) isn't enough |
| 37 | [Consensus Algorithms — Raft vs Paxos](./Core-Architecture/Distributed-Systems/37-consensus-algorithms-raft-vs-paxos.md) | Deep-dive on Leader Election (Tier 2); principal-engineer level probes |
| 15 | [System Qualities — 7 Evaluation Dimensions](./Foundations/Data-Fundamentals/15-system-qualities.md) | The evaluation framework; use to structure your interview answer |
| 27jwt | [JWT Token Storage Reference (localStorage vs httpOnly cookie)](./Production-Grade/Auth-and-Security/27-jwt-token-storage-reference.md) | Companion to Auth/AuthZ (Tier 2); deep-dive on XSS/CSRF trade-offs |

---

## 🔬 Advanced Companions (Optional Deepeners)

> **Read these only after you've mastered the core note.** These companion files cover algorithmic variants and production edge cases beyond what you need for most SDE-3 interviews. The core notes already meet interview bar.

| Companion | What it adds beyond the core note |
|---|---|
| [Rate Limiting — Advanced](./Foundations/Performance-and-Scale/02-rate-limiting_advanced.md) | Adaptive rate limiting, multi-dimensional limits, distributed rate limiters across regions |
| [Caching — Advanced](./Foundations/Performance-and-Scale/03-caching_advanced.md) | Cache warming strategies, multi-level cache coherence, distributed invalidation |
| [Idempotency — Advanced](./Foundations/Concurrency-and-Consistency/04-idempotency_advanced.md) | Saga idempotency, batch idempotency, deterministic ID generation patterns |
| [Sharded Counters — Advanced](./Foundations/Performance-and-Scale/09-sharded-counters_advanced.md) | CRDT counters, time-series aggregation, adaptive shard count |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 1, 2026 | DESIGN-WEIGHT-MAP created. 53 notes assigned across Tier 1 (10), Tier 2 (20 in 4 trigger clusters), Tier 3 ⭐ (8 high-frequency specialists), Tier 3 (15 problem-specific), plus 4 advanced companions. Notes 50 (Database Indexing → Tier 1) and 51 (Geospatial Indexing → Tier 3 problem-specific) included. |
| Jul 1, 2026 | **CAP Theorem (#34) promoted: Tier 3 Problem-Specific → Tier 2 Scale Triggers.** Rationale: every database selection and replication decision is a CAP/PACELC decision — it's a universal distributed systems trigger, not a problem-specific one. Tier 2 count: 20 → 21. Tier 3 count: 15 → 14. |
| Jul 2, 2026 | **#52 Numbers to Know & Scale Triggers added to Tier 1.** 2026 hardware baselines, back-of-envelope formula, scale trigger thresholds, and three anti-patterns with worked math. Tier 1 count: 10 → 11. Total tracked notes: 53 → 54. |
