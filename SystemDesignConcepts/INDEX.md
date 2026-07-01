# SystemDesignConcepts — Master Index

> **53 tracked concept notes · 4 advanced companions · all ✅ Done**
>
> This file is the single source of truth for what exists. Use `Ctrl+F` / `Cmd+F` to find by keyword.
> For "where do I start," see `START-HERE.md` (coming next).

---

## All Notes (sorted by #)

| # | Concept | Folder | Companion |
|---|---------|--------|-----------|
| 01 | [Optimistic + Pessimistic Locking](./Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md) | Foundations · Concurrency | — |
| 02 | [Rate Limiting (token bucket, sliding window)](./Foundations/Performance-and-Scale/02-rate-limiting.md) | Foundations · Performance | [Advanced](./Foundations/Performance-and-Scale/02-rate-limiting_advanced.md) |
| 03 | [Caching (5 strategies, eviction, stampede)](./Foundations/Performance-and-Scale/03-caching.md) | Foundations · Performance | [Advanced](./Foundations/Performance-and-Scale/03-caching_advanced.md) |
| 04 | [Idempotency (HTTP + Kafka consumer)](./Foundations/Concurrency-and-Consistency/04-idempotency.md) | Foundations · Concurrency | [Advanced](./Foundations/Concurrency-and-Consistency/04-idempotency_advanced.md) |
| 05 | [Consistent Hashing](./Foundations/Performance-and-Scale/05-consistent-hashing.md) | Foundations · Performance | — |
| 06 | [Distributed Locking (Redis SETNX, Redlock)](./Foundations/Concurrency-and-Consistency/06-distributed-locking.md) | Foundations · Concurrency | — |
| 06 | [Database Types & Selection (SQL, NoSQL, Redis, Kafka, Elasticsearch)](./Core-Architecture/Database-Core/06-databases-types-and-selection.md) | Core · Database | — |
| 07 | [CDC + Outbox Pattern](./Foundations/Data-Fundamentals/07-cdc-outbox.md) | Foundations · Data | — |
| 08 | [Bloom Filter](./Foundations/Data-Fundamentals/08-bloom-filter.md) | Foundations · Data | — |
| 09 | [Sharded Counters](./Foundations/Performance-and-Scale/09-sharded-counters.md) | Foundations · Performance | [Advanced](./Foundations/Performance-and-Scale/09-sharded-counters_advanced.md) |
| 10 | [Backpressure](./Core-Architecture/Resilience-and-Fault-Tolerance/10-backpressure.md) | Core · Resilience | — |
| 11 | [API Design (REST, pagination, versioning)](./Foundations/Data-Fundamentals/11-api-design.md) | Foundations · Data | — |
| 12 | [Relational Data Modeling](./Foundations/Data-Fundamentals/12-data-modeling.md) | Foundations · Data | — |
| 13 | [Security + PKI Fundamentals](./Production-Grade/Auth-and-Security/13-security-pki.md) | Production · Auth | — |
| 14 | [Document & Blob Storage (S3, metadata DB, versioning)](./Foundations/Data-Fundamentals/14-document-blob-storage.md) | Foundations · Data | — |
| 15 | [System Qualities — 7 Evaluation Dimensions](./Foundations/Data-Fundamentals/15-system-qualities.md) | Foundations · Data | — |
| 16 | [Connection Pooling (HikariCP, pool sizing, leak detection)](./Core-Architecture/Database-Core/16-connection-pooling-db-performance.md) | Core · Database | — |
| 17 | [Load Balancing (Round Robin, Least Connections, Sticky Sessions)](./Core-Architecture/Service-Communication/17-load-balancing-algorithms.md) | Core · Communication | — |
| 18 | [Service Discovery (DNS, Client-side, Server-side)](./Core-Architecture/Service-Communication/18-service-discovery-dns.md) | Core · Communication | — |
| 19 | [Message Queues (RabbitMQ task queue vs Kafka event stream)](./Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md) | Core · Communication | — |
| 20 | [Circuit Breaker & Resilience (state transitions, bulkheads, retry)](./Core-Architecture/Resilience-and-Fault-Tolerance/20-circuit-breaker-resilience.md) | Core · Resilience | — |
| 21 | [Leader Election & Consensus (Raft, Zookeeper, split-brain)](./Core-Architecture/Distributed-Systems/21-leader-election-consensus.md) | Core · Distributed | — |
| 22 | [Event Sourcing (immutable logs, temporal queries, replay)](./Core-Architecture/Database-Core/22-event-sourcing.md) | Core · Database | — |
| 23 | [Saga Pattern (orchestration vs choreography, compensating transactions)](./Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md) | Core · Resilience | — |
| 24 | [API Gateway (routing, auth, rate limiting at edge)](./Production-Grade/System-Design-Patterns/24-api-gateway-pattern.md) | Production · Patterns | — |
| 25 | [Monitoring & Observability (logs, metrics, traces — three pillars)](./Production-Grade/Observability/25-monitoring-observability-fundamentals.md) | Production · Observability | — |
| 26 | [WebSocket (HTTP upgrade, bidirectional, real-time)](./Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md) | Production · Patterns | — |
| 27 | [Authentication & Authorization (JWT, OAuth 2.0, RBAC, ABAC)](./Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md) | Production · Auth | — |
| 27 | [JWT Token Storage Reference (localStorage vs httpOnly cookie, XSS/CSRF)](./Production-Grade/Auth-and-Security/27-jwt-token-storage-reference.md) | Production · Auth | — |
| 28 | [CDN — Content Delivery Network (edge caching, TTL, invalidation)](./Production-Grade/Performance-Optimization/28-cdn-edge-caching.md) | Production · Perf-Opt | — |
| 29 | [Database Replication (master-slave, WAL, RPO/RTO, automatic failover)](./Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md) | Core · Resilience | — |
| 30 | [Distributed Tracing (trace ID, spans, latency breakdown, sampling)](./Production-Grade/Observability/30-distributed-tracing-spans.md) | Production · Observability | — |
| 31 | [CQRS (Command-Query separation, eventual consistency, projections)](./Production-Grade/System-Design-Patterns/31-cqrs-read-write-separation.md) | Production · Patterns | — |
| 32 | [Elasticsearch (inverted index, sharding, full-text search, ELK)](./Production-Grade/Performance-Optimization/32-elasticsearch-inverted-index.md) | Production · Perf-Opt | — |
| 33 | [gRPC & Protocol Buffers (HTTP/2, multiplexing, streaming, binary)](./Core-Architecture/Service-Communication/33-grpc-protocol-buffers.md) | Core · Communication | — |
| 34 | [CAP Theorem & Consistency Models](./Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md) | Core · Distributed | — |
| 35 | [Retry & Exponential Backoff Patterns](./Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md) | Core · Resilience | — |
| 36 | [Two-Phase Commit vs Saga](./Core-Architecture/Resilience-and-Fault-Tolerance/36-two-phase-commit-vs-saga.md) | Core · Resilience | — |
| 37 | [Consensus Algorithms — Raft vs Paxos](./Core-Architecture/Distributed-Systems/37-consensus-algorithms-raft-vs-paxos.md) | Core · Distributed | — |
| 38 | [Sharding Strategy](./Core-Architecture/Database-Core/38-sharding-strategy.md) | Core · Database | — |
| 39 | [Bulkheads & Resource Isolation](./Core-Architecture/Resilience-and-Fault-Tolerance/39-bulkheads-resource-isolation.md) | Core · Resilience | — |
| 40 | [Multi-Region & Geo-Failover](./Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md) | Core · Distributed | — |
| 41 | [Isolation Levels & Dirty Reads](./Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md) | Foundations · Concurrency | — |
| 42 | [Inventory Management & Booking](./Production-Grade/System-Design-Patterns/42-inventory-management-booking.md) | Production · Patterns | — |
| 43 | [Pagination — Cursor-Based](./Foundations/Data-Fundamentals/43-pagination-cursor-based.md) | Foundations · Data | — |
| 44 | [Graceful Degradation & Fallbacks](./Core-Architecture/Resilience-and-Fault-Tolerance/44-graceful-degradation-fallbacks.md) | Core · Resilience | — |
| 45 | [Hot Partition Problem (write salting, hot-key caching)](./Core-Architecture/Database-Core/45-hot-partition-problem.md) | Core · Database | — |
| 46 | [Push Notifications / Fanout at Scale (APNs, FCM, Kafka fan-out)](./Core-Architecture/Service-Communication/46-push-notifications-fanout.md) | Core · Communication | — |
| 47 | [Job Scheduling at Scale (CAS claim, heartbeat, delayed jobs)](./Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md) | Production · Patterns | — |
| 48 | [Feature Flags / A/B Testing (deterministic bucketing, sticky assignment)](./Production-Grade/System-Design-Patterns/48-feature-flags-ab-testing.md) | Production · Patterns | — |
| 49 | [State Machines in Workflows (FSM transitions, CAS enforcement)](./Production-Grade/System-Design-Patterns/49-state-machines-workflows.md) | Production · Patterns | — |
| 50 | [Database Indexing (B-tree, composite, covering, selectivity, EXPLAIN)](./Foundations/Data-Fundamentals/50-database-indexing.md) | Foundations · Data | — |
| 51 | [Geospatial Indexing (geohash, quad tree, H3, Redis GEO)](./Core-Architecture/Database-Core/51-geospatial-indexing.md) | Core · Database | — |

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Jul 1, 2026 | INDEX.md created. 49 tracked notes + 4 advanced companions catalogued. |
| Jul 1, 2026 | 2 previously untracked files registered in AGENTS.md and promoted into main table: `06-databases-types-and-selection.md` and `27-jwt-token-storage-reference.md`. Total: 51 tracked notes. |
| Jul 1, 2026 | 2 new gap-closure notes created: `50-database-indexing.md` (B-tree, composite indexes, covering indexes, EXPLAIN ANALYZE) and `51-geospatial-indexing.md` (geohash, quad tree, H3 hexagonal grid, Redis GEO). Total: 53 tracked notes. |
