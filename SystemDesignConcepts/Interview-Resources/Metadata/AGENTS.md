# SystemDesignConcepts — AGENTS.md

> **For any AI assistant writing or editing notes in this folder:** Read this file AND `notes-standards.md` before touching any file here. Both are mandatory. The root `AGENTS.md` sets universal rules; this file sets SystemDesignConcepts-specific rules.

---

## Mandatory Pre-Work (Do This Before Writing Any Note)

1. **Read `notes-standards.md` in this folder** — the complete note format, section order, and pre-publish checklist. Every note must follow it.
2. **Read `resources.md` in this folder** — the curated resource list per concept. Before writing the `📚 Further Reading` section of any note, check `resources.md` for already-vetted resources for that concept. Do NOT invent resource recommendations — use the ones already researched and listed here. If the concept has no entry in `resources.md`, add one.
3. **Read `GAP-CLOSURE-PLAN.md` in this folder** — systematic plan to close coverage gaps (Jun 2026 onwards). Before modifying core notes or creating new files, understand: which gaps are being closed inline (01, 12, 14), which are getting companion advanced files (02.1, 03.1, 04.1, 09.1), and which are being deferred. This prevents duplicate work.
4. **Read the root `AGENTS.md`** — universal formatting rules (code style, ASCII visuals, emoji palette, first-use term gloss).
5. **Check which notes already exist** (Glob on this folder) before writing — avoid duplicating content that belongs in a cross-reference link instead.

> **Rule: When a new reference file is added to this folder that AI should consult, AGENTS.md Mandatory Pre-Work must be updated in the same step — never separately.**

---

## What This Folder Is

Medium-depth concept notes on core backend/system design topics that appear in SDE-2/SDE-3 interviews. These notes are **self-contained entry points** — a reader should be able to pick up any note cold and be interview-ready on that concept. No external resource is required before reading.

**Current notes:**

| File | Concept | Status |
|---|---|---|
| `01-optimistic-pessimistic-locking.md` | Optimistic + Pessimistic Locking | ✅ Done |
| `02-rate-limiting.md` | Rate Limiting (token bucket, sliding window) | ✅ Done |
| `02-rate-limiting_advanced.md` | Rate Limiting — Advanced (adaptive, multi-dimensional, distributed) | ✅ Done |
| `03-caching.md` | Caching (5 strategies, eviction, stampede) | ✅ Done |
| `03-caching_advanced.md` | Caching — Advanced (warming, invalidation, multi-level, coherence) | ✅ Done |
| `04-idempotency.md` | Idempotency (HTTP + Kafka consumer) | ✅ Done |
| `04-idempotency_advanced.md` | Idempotency — Advanced (sagas, batch, deterministic IDs) | ✅ Done |
| `05-consistent-hashing.md` | Consistent Hashing | ✅ Done |
| `06-distributed-locking.md` | Distributed Locking (Redis SETNX, Redlock) | ✅ Done |
| `07-cdc-outbox.md` | CDC + Outbox Pattern | ✅ Done |
| `08-bloom-filter.md` | Bloom Filter | ✅ Done |
| `09-sharded-counters.md` | Sharded Counters | ✅ Done |
| `09-sharded-counters_advanced.md` | Sharded Counters — Advanced (CRDT, time-series, adaptive sharding) | ✅ Done |
| `10-backpressure.md` | Backpressure | ✅ Done |
| `11-api-design.md` | API Design (REST, pagination, versioning) | ✅ Done |
| `12-data-modeling.md` | Relational Data Modeling | ✅ Done |
| `13-security-pki.md` | Security + PKI Fundamentals | ✅ Done |
| `14-document-blob-storage.md` | Document & Blob Storage (S3, metadata DB, versioning) | ✅ Done |
| `15-system-qualities.md` | System Qualities — The 7 DocuSign Evaluation Dimensions | ✅ Done |
| `16-connection-pooling-db-performance.md` | Connection Pooling (HikariCP, pool sizing, leak detection) | ✅ Done |
| `17-load-balancing-algorithms.md` | Load Balancing (Round Robin, Least Connections, Sticky Sessions) | ✅ Done |
| `18-service-discovery-dns.md` | Service Discovery (DNS, Client-side, Server-side patterns) | ✅ Done |
| `19-message-queues-kafka-rabbitmq.md` | Message Queues (RabbitMQ task queue vs Kafka event stream) | ✅ Done |
| `20-circuit-breaker-resilience.md` | Circuit Breaker & Resilience (state transitions, bulkheads, retry) | ✅ Done |
| `21-leader-election-consensus.md` | Leader Election & Consensus (Raft, Zookeeper, split-brain prevention) | ✅ Done |
| `22-event-sourcing.md` | Event Sourcing (immutable append-only logs, temporal queries, replay) | ✅ Done |
| `23-saga-pattern.md` | Saga Pattern (orchestration vs choreography, compensating transactions) | ✅ Done |
| `24-api-gateway-pattern.md` | API Gateway (routing, authentication, rate limiting at edge) | ✅ Done |
| `25-monitoring-observability-fundamentals.md` | Monitoring & Observability (logs, metrics, traces — three pillars) | ✅ Done |
| `26-websocket-real-time-communication.md` | WebSocket (HTTP upgrade, bidirectional, real-time communication) | ✅ Done |
| `27-auth-authz-fundamentals.md` | Authentication & Authorization (JWT, OAuth 2.0, RBAC, ABAC) | ✅ Done |
| `28-cdn-edge-caching.md` | CDN — Content Delivery Network (edge caching, TTL, invalidation) | ✅ Done |
| `29-db-replication-failover.md` | Database Replication (master-slave, WAL, RPO/RTO, automatic failover) | ✅ Done |
| `30-distributed-tracing-spans.md` | Distributed Tracing (trace ID, spans, latency breakdown, sampling) | ✅ Done |
| `31-cqrs-read-write-separation.md` | CQRS (Command-Query separation, eventual consistency, projections) | ✅ Done |
| `32-elasticsearch-inverted-index.md` | Elasticsearch (inverted index, sharding, full-text search, ELK stack) | ✅ Done |
| `33-grpc-protocol-buffers.md` | gRPC & Protocol Buffers (HTTP/2, multiplexing, streaming, binary) | ✅ Done |
| `34-cap-theorem-consistency-models.md` | CAP Theorem & Consistency Models | ✅ Done |
| `35-retry-exponential-backoff-patterns.md` | Retry & Exponential Backoff Patterns | ✅ Done |
| `36-two-phase-commit-vs-saga.md` | Two-Phase Commit vs Saga | ✅ Done |
| `37-consensus-algorithms-raft-vs-paxos.md` | Consensus Algorithms — Raft vs Paxos | ✅ Done |
| `38-sharding-strategy.md` | Sharding Strategy | ✅ Done |
| `39-bulkheads-resource-isolation.md` | Bulkheads & Resource Isolation | ✅ Done |
| `40-multi-region-geo-failover.md` | Multi-Region & Geo-Failover | ✅ Done |
| `41-isolation-levels-dirty-reads.md` | Isolation Levels & Dirty Reads | ✅ Done |
| `42-inventory-management-booking.md` | Inventory Management & Booking | ✅ Done |
| `43-pagination-cursor-based.md` | Pagination — Cursor-Based | ✅ Done |
| `44-graceful-degradation-fallbacks.md` | Graceful Degradation & Fallbacks | ✅ Done |
| `45-hot-partition-problem.md` | Hot Partition Problem (write salting, hot-key caching) | ✅ Done |
| `46-push-notifications-fanout.md` | Push Notifications / Fanout at Scale (APNs, FCM, Kafka fan-out, dead tokens) | ✅ Done |
| `47-job-scheduling-at-scale.md` | Job Scheduling at Scale (CAS claim, heartbeat, delayed jobs, exactly-once) | ✅ Done |
| `48-feature-flags-ab-testing.md` | Feature Flags / A/B Testing (deterministic bucketing, sticky assignment, flag lifecycle) | ✅ Done |
| `49-state-machines-workflows.md` | State Machines in Workflows (FSM transitions, CAS enforcement, compensation) | ✅ Done |

> **Advanced Companion Files (Optional Deepeners):**
> Companion advanced files (named `NN-concept_advanced.md`) cover variant-heavy topics. These are optional deepeners — NOT required for interview prep, but useful for readers wanting algorithmic variants and advanced patterns beyond core material. Currently available: `02-rate-limiting_advanced.md`, `03-caching_advanced.md`, `04-idempotency_advanced.md`, `09-counters_advanced.md`. See `GAP-CLOSURE-PLAN.md` for closure strategy.

---

## Rules Specific to This Folder

### 1. Self-Contained — No External Prerequisites

Every note must teach the concept from scratch. Do NOT write "watch the ByteByteGo video first" and then build on it. The note IS the resource. External references go in the `📚 Further Reading` section at the BOTTOM — they are optional deeper dives after reading, not prerequisites before.

### 2. Coverage Completeness (Critical — Most Common Quality Failure)

**Before closing any note:** Count every strategy, algorithm, and pattern named anywhere in the file — visual, real-world examples, trade-offs, Q&As — and verify each one has COMPLETE coverage in the implementation section (steps + code, or equivalent explanation).

**Common trap:** Write-through in the visual, cache-aside in the code → write-through is NOT covered. Named ≠ covered.

Run this check explicitly:
- List every strategy/algorithm named in the note
- Confirm each has its own implementation section or code block
- If any is only mentioned but not implemented — add it before closing

### 3. Named Technology Must Be Explained

Any specific technology named in Section 4 (Lua, Redis SETNX, Kafka sorted set, B-tree, UUID v7, etc.) must have a `### What is X, and why does it fit here?` sub-section with:
- One sentence plain-English definition
- Explicit "In an interview, if asked:" answer sentence

### 4. Section Order Is Fixed

See `notes-standards.md` Section 2 for the exact 10-section order. Do not reorder. Do not skip required sections.

### 5. Interview Depth Calibration

These are SDE-3 level notes — not textbook depth, not introductory depth. The test: after reading the note, can the reader confidently answer both Tier 1 (surface) and Tier 2 (cross/probe) questions in an interview? If not, the note is not deep enough.

Tier 2 questions must cover at least:
- One "what breaks if..." failure mode
- One "how does X interact with Y" cross-concept question
- One "why not just use simpler alternative" question

---

## Pre-Publish Checklist (Run This Before Every File Is Saved)

Copy of the full checklist from `notes-standards.md` — abbreviated for fast review:

- [ ] All 9 required sections present (Section 10 optional)
- [ ] Mental model: everyday analogy, complete enough to retell without technical vocabulary
- [ ] Visual: ASCII diagram present where concept has state/flow/sequence. KEY INVARIANT stated.
- [ ] Steps in plain English BEFORE every code block
- [ ] Code: language-tagged, one statement per line, always braced, spaces around operators
- [ ] Every named technology has a "What is X" sub-section with interview answer
- [ ] **COVERAGE CHECK: every named strategy/algorithm has full implementation coverage**
- [ ] ≥ 5 Q&As, minimum 2 are Tier 2 cross/probe
- [ ] Further Reading at BOTTOM (not top)
- [ ] First-use term gloss for unfamiliar terms
- [ ] No emojis outside approved palette

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | AGENTS.md created for SystemDesignConcepts folder. Enforces: read notes-standards.md first, coverage completeness check, self-contained entry point design, named technology explanation rule. |
| June 2026 | Added `resources.md` to Mandatory Pre-Work (step 2). Further Reading sections must use curated resources from resources.md, not invented recommendations. Added enforcement rule: new reference files must be added to AGENTS.md in the same step they are created. |
| June 23, 2026 | **Gap Closure Plan Created.** Comprehensive audit identified 40+ coverage gaps (70% current coverage). Strategy: inline HIGH-impact gaps (01, 12, 14) + create companion advanced files (02.1, 03.1, 04.1, 09.1) + skip LOW-impact gaps. Naming convention: `NN.1-concept-advanced.md` for companions. Est. effort: 16h. See `GAP-CLOSURE-PLAN.md` for details. |
| June 23, 2026 | **Phase 1 Complete — Inline HIGH-impact gaps.** Modified 3 core notes: 01-optimistic-pessimistic-locking (+isolation levels, +1 Q&A), 12-data-modeling (+schema evolution patterns with batch backfill), 14-document-blob-storage (+multipart upload, +lifecycle policies). Total: +275 lines across 3 files. |
| June 23, 2026 | **Phase 2 Complete — Companion Advanced Files.** Created 4 companion files: 02.1-rate-limiting-advanced (447 lines, adaptive+multi-dim+distributed), 03.1-caching-advanced (439 lines, warming+invalidation+coherence), 04.1-idempotency-advanced (441 lines, sagas+batch+deterministic IDs), 09.1-counters-advanced (456 lines, CRDT+time-series+adaptive). Total: ~1,783 lines. Updated cross-references in AGENTS.md and core notes' Related Concepts sections. |
| June 25, 2026 | **Concepts 16-23 Complete.** Created 8 new system design concepts with proper two-diagram topology architecture (full system stack + component detail): 16-connection-pooling, 17-load-balancing, 18-service-discovery, 19-message-queues, 20-circuit-breaker, 21-leader-election, 22-event-sourcing, 23-saga-pattern. All follow strict notes-standards format. Total: ~8,200 lines, ~8 hours effort. |
| June 25, 2026 | **Concepts 24-26 Complete — New Two-Diagram Topology Standard (June 25).** Created 3 critical architecture concepts with mandatory system topology diagrams (showing complete stack hierarchy Client → CDN → LB → Services → Cache → DB) plus component detail diagrams: 24-api-gateway-pattern, 25-monitoring-observability-fundamentals, 26-websocket-real-time-communication. Updated notes-standards.md Section 3 to enforce two-diagram requirement for all architectural concepts. Total: ~4,800 lines, ~8 hours effort (24: 2.5h, 25: 3h, 26: 2.5h). Resources updated with curated references for all three. |
| June 25, 2026 | **Concepts 27-33 Complete — Week 2-3 Full Stack Coverage.** Created 7 advanced system design concepts, all following two-diagram topology + component detail standard: 27-auth-authz (JWT, OAuth, RBAC, bcrypt/RS256), 28-cdn (edge caching, TTL, invalidation), 29-db-replication (WAL, sync/async, RPO/RTO, failover), 30-distributed-tracing (trace ID, spans, sampling, OpenTelemetry), 31-cqrs (command-query separation, projections, eventual consistency), 32-elasticsearch (inverted index, sharding, full-text search, ELK), 33-grpc (Protocol Buffers, HTTP/2 multiplexing, streaming). Total: ~12,000 lines, ~16 hours effort (27: 3h, 28: 2h, 29: 2.5h, 30: 2h, 31: 2h, 32: 2.5h, 33: 2h). Resources added for all 7. |
| June 26, 2026 | **Concepts 45-49 Complete — Gap Closure: 5 High-Priority Missing Concepts.** Gap audit identified 5 production-critical concepts absent from the knowledge base. Written: 45-hot-partition-problem (write salting, hot-key caching, partition lag monitoring), 46-push-notifications-fanout (APNs/FCM, Kafka fan-out topology, dead token cleanup), 47-job-scheduling-at-scale (CAS claim, heartbeat TTL, delayed jobs via Redis ZADD, SKIP LOCKED), 48-feature-flags-ab-testing (deterministic bucketing, sticky assignment, ops kill switch, zombie flags), 49-state-machines-workflows (FSM transition table, CAS enforcement, saga compensation states). All 5 follow full 10-section notes-standards format with two-diagram topology requirement. Total: ~5 files, ~2,000 lines. |
