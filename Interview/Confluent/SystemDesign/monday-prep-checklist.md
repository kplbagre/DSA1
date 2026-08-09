# Monday Interview Prep — Step-by-Step Checklist

> **Interview:** Monday Aug 10, 12:30–1:30pm IST | 1 round (System Design) | Pass → Hiring Manager
> **What recruiter said matters most:** Requirement gathering + capacity estimation (said twice), CAP theorem, SQL vs NoSQL, HA/failover, caching, concurrency, monitoring/observability.
> **Total focused time needed:** ~3 hours

---

## 🎯 The Only Goal for Today

You already have the knowledge. The notes exist. The 5 solution files are done. Today is about converting knowledge → verbal fluency. **Read → close the note → say it out loud.**

---

## 🪜 Step 1 — Capacity Estimation (45 min)
**Why:** Recruiter named this a "KEY AREA" explicitly. It's a practiced skill, not recall.

**What to read first:**
→ [`52-numbers-to-know-scale-triggers.md`](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/52-numbers-to-know-scale-triggers.md)

**Then close it and run these 3 drills out loud. Time yourself — target < 3 min each.**

---

### Drill A — TempMail (solution: [`tempmail-disposable-email.md`](./tempmail-disposable-email.md))

```
Q: 10M DAU, each creates 2 temp addresses/day, each inbox gets ~5 emails.
   Emails expire after 10 minutes.
   Estimate: storage, writes/sec, active inbox count at any moment.

Your envelope:
  - Writes: 10M × 2 = 20M new inboxes/day = ~230 /sec
  - Emails received: 10M × 2 × 5 = 100M emails/day = ~1,200 emails/sec
  - Avg email size: 50KB → 100M × 50KB = 5TB/day (large — use S3 for body, Postgres for metadata)
  - Active at any moment: 10-min TTL → 20M inboxes/day × (10/1440) = ~139K active inboxes → Redis fits easily (< 1GB)
```

---

### Drill B — Aggregate News Feed (solution: [`aggregate-news-feed.md`](./aggregate-news-feed.md))

```
Q: 10M subscribers, 500K publishers, each publishes 5 articles/day.
   Users read their feed 3x/day.
   Estimate: ingestion rate, feed reads/sec, Kafka partition count.

Your envelope:
  - Articles/day: 500K × 5 = 2.5M articles/day = ~29 articles/sec (ingestion — light)
  - Feed reads: 10M × 3 = 30M reads/day = ~350 reads/sec
  - Kafka: 29 writes/sec → 1 partition is fine; scale to 10 if publishers burst
  - Storage (30 days): 2.5M/day × 30 × 2KB avg = 150GB — single Postgres with read replicas
```

---

### Drill C — Distributed KV Store (solution: [`distributed-kv-store.md`](./distributed-kv-store.md))

```
Q: 100M keys, 80% reads / 20% writes, P99 read < 10ms, globally distributed.
   Estimate: QPS split, cache size, node count.

Your envelope:
  - Assume 10K QPS total → 8K reads / 2K writes
  - Cache: 100M keys × avg 256 bytes = 25GB → fits in Redis cluster (3 nodes × 16GB = 48GB)
  - With 80% cache hit: only 1,600 reads/sec hit DB → single Cassandra node handles 10K+ ops/sec
  - Nodes: 3 Cassandra nodes (RF=3) for global durability — 1 per region
```

---

## 🧠 Step 2 — CAP Theorem × Kafka (30 min)
**Why:** Recruiter named CAP explicitly. Confluent will ask it product-specifically.

**What to read:**
→ [`34-cap-theorem-consistency-models.md`](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md)

**Then close it and memorize this exact verbal answer:**

---

### The Kafka–CAP Answer (say this cold)

> "Kafka is **CP — Consistent + Partition-Tolerant**, not CA.
>
> Here's the mechanism: Kafka uses ISR — In-Sync Replicas (a set of replicas that are caught up with the leader). When a partition leader fails and the new leader is not in the ISR, Kafka **refuses writes** rather than serve potentially stale data. Availability drops; consistency is preserved.
>
> The knob that controls this is `unclean.leader.election.enable`:
> - `false` (default): ISR-only election → **CP** — no data loss, but leader election may stall if ISR is empty
> - `true`: any replica can become leader → flips to **AP** — faster failover but may lose recent writes
>
> For Tableflow specifically: we'd keep `unclean.leader.election.enable=false` because data loss in a pipeline that materializes topics into Iceberg tables means the Iceberg snapshot and the Kafka topic diverge — you can't easily detect which records were lost."

---

**Follow-up the interviewer will ask:**
> "What about Zookeeper / KRaft — does that change anything?"

> "KRaft (Kafka without ZooKeeper) uses Raft consensus for the controller quorum — same CP guarantees, but eliminates the external ZooKeeper dependency. The ISR-based replication for data partitions is unchanged."

---

## 🔧 Step 3 — Kafka Internals: ISR + Leader Election + HA (30 min)
**Why:** Covers recruiter's HA/failover + replication requirements. The answers live in your Kafka note.

**What to read:**
→ [`60-kafka-internals.md`](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/60-kafka-internals.md)

Focus specifically on: **ISR, leader election, `min.insync.replicas`, log compaction**.

**Then memorize these 5 monitoring metrics (for when they ask "how would you monitor this system?"):**

---

### The 5 Kafka Pipeline Metrics (Confluent-Specific Observability Answer)

> "For a Kafka-based pipeline like Tableflow, I'd instrument these five signals:
>
> 1. **Consumer group lag per partition** — primary SLA indicator. If lag grows, the pipeline is falling behind its input; set an alert at `lag > 10,000 messages`.
> 2. **End-to-end latency** — time from Kafka produce → record visible in Iceberg snapshot. This is Tableflow's core SLA metric. Instrument via timestamp in the message header vs snapshot commit time.
> 3. **ISR shrinkage events** — when a replica falls out of ISR, availability risk is imminent. Alert immediately; don't wait for the leader to fail.
> 4. **Producer retry rate** — retries increasing = downstream pressure, broker instability, or network partition. Leading indicator, not a lagging one.
> 5. **Throughput per partition (MB/s and msg/s)** — uneven distribution signals a hot partition. If one partition handles 10× the others, re-partition or apply write salting."

→ Also see: [`25-monitoring-observability-fundamentals.md`](../../../SystemDesignConcepts/Production-Grade/Observability/25-monitoring-observability-fundamentals.md)

---

### "What processes the Kafka stream?" (cross-probe — stream processing layer)

> "It depends on complexity. For simple per-key transformations — filter, map, per-key aggregations — I'd use **Kafka Streams**: it runs as a library inside my service, no extra cluster, exactly-once semantics built-in, output goes back to Kafka. For stateful aggregations across multiple keys, multi-stream joins, or writing to external sinks like Iceberg or Postgres with exactly-once guarantees, I'd reach for **Apache Flink** — it has event-time semantics with watermarks for late-arriving events, and checkpointing to S3 means it survives crashes. If the team already runs Spark and latency of seconds is acceptable, **Spark Structured Streaming** handles backfill and live data with the same code."

→ Full note: [`61-stream-processing-flink-kafka-streams.md`](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/61-stream-processing-flink-kafka-streams.md)

---

## 🔧 Step 4 — Job Scheduling + Concurrency (45 min)
**Why:** Recruiter called out "concurrency and synchronization" explicitly. Job Scheduling is a confirmed Confluent question. This is the most likely concurrency round scenario.

**What to read (in order):**
1. → [`47-job-scheduling-at-scale.md`](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md)
2. → [`concurrency-fundamentals.md`](../DSA/concurrency-fundamentals.md)
3. → [`06-distributed-locking.md`](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/06-distributed-locking.md)

**The one concurrency answer Confluent always probes:**

---

### "How do two service instances avoid processing the same Kafka message?"

> "Kafka's consumer group protocol handles this at the partition level. Each partition is assigned to exactly one consumer in a group — so two instances in the same consumer group never see the same message from the same partition. No application-level distributed lock is needed.
>
> The edge case is rebalancing: during a rebalance (instance joins or leaves), ownership of partitions shifts. For a brief window (< session.timeout.ms, default 10s), a partition may be unassigned. To handle this: commit offsets only after processing is confirmed (not before), and make your consumer idempotent — if the same message is processed twice during a rebalance, the second processing produces the same result."

→ Also see: [`04-idempotency.md`](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md)

---

### "How do you handle two workers claiming the same job in a job scheduling system?"

> "CAS (Compare-And-Set) on the job status field in Postgres. Each worker does:
> `UPDATE jobs SET status='RUNNING', claimed_by=$worker_id WHERE id=$job_id AND status='PENDING'`
> The DB serializes concurrent updates — exactly one worker gets rowcount=1 (the claim succeeds); others get rowcount=0 and move on. No distributed lock, no Redlock, no external coordination needed."

→ See detail in `47-job-scheduling-at-scale.md`.

---

## 🔬 Step 5 — HA / Failover Fluency (20 min)
**Why:** Recruiter said "plan for high availability and recovery; failover and disaster-recovery strategies." JD says 99.99%.

**What to read:**
→ [`29-db-replication-failover.md`](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md)
→ [`56-availability.md`](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/56-availability.md)

**The availability math you'll need live:**

```
99.99% uptime = 52.6 minutes downtime/year = 4.4 minutes/month

To achieve 99.99% with services in series:
  availability = A1 × A2 × A3
  99.99% = 0.9999

Two services each at 99.99% in series:
  combined = 0.9999 × 0.9999 = 0.9998 = 99.98% (worse than one!)

Fix: redundancy in parallel:
  parallel availability = 1 - (1-A)^N
  Two 99.9% instances in parallel:
  = 1 - (0.001)^2 = 1 - 0.000001 = 99.9999% (six nines)
```

**The Kafka-specific DR answer:**

> "For Kafka multi-region DR: Confluent uses Cluster Linking — topics are replicated from the primary cluster to a DR cluster asynchronously. On failover, the DR cluster is promoted to primary. The trade-off: RPO is non-zero (minutes of data may not have replicated at the point of failure). To minimize RPO, set `min.insync.replicas=2` with synchronous replication on the primary. RTO is ~5–10 minutes for DNS propagation + consumer group rebalance on the DR cluster."

---

## 🧠 Step 6 — Quick Verbal Drill on "SQL vs NoSQL" (15 min)
**Why:** Recruiter specifically called out "know your databases."

→ Skim: [`06-databases-types-and-selection.md`](../../../SystemDesignConcepts/Core-Architecture/Database-Core/06-databases-types-and-selection.md)

**Decision tree to say out loud:**

```
Need ACID transactions?          → PostgreSQL
Need TTL + cache?                → Redis
Need flexible schema + documents?→ MongoDB / DynamoDB
Need time-series / append-only?  → Kafka topic (as a log) or TimescaleDB
Need global low-latency reads?   → Cassandra (wide-column, tunable consistency)
Need full-text search?           → Elasticsearch
Need open table format + analytics? → Iceberg on S3 / GCS (Tableflow output)
```

---

## ⚠️ The Anti-Patterns — Don't Do These Tomorrow

- **Don't start the feed/pipeline question with ML ranking.** Start with ingestion reliability. Candidates who led with ML were dinged. Start with: "First let me make sure we can get the data in reliably, then we discuss what we do with it."
- **Don't use a Bloom filter for liveness/health-check.** Bloom filter = set membership (has this key ever been seen?). Health check = time-window liveness. They are different problems.
- **Don't say 200 for a POST that creates a resource.** POST → 201 Created + Location header. No exceptions.
- **Don't offset-paginate a feed.** Cursor pagination for any time-ordered list that receives concurrent inserts.
- **Don't skip clarifying questions.** The recruiter said this explicitly. Ask them. Even if the problem is clear, ask 2–3 just to demonstrate the habit.

---

## 🧾 TL;DR — The 30-Minute Version If You're Short on Time

If you only have 30 minutes tomorrow morning before the call, do this:

1. **Say the Kafka-CAP answer out loud** (Section 2 above) — 3 min
2. **Say the 5 monitoring metrics out loud** (Section 3 above) — 2 min
3. **Run Drill A (TempMail capacity)** in your head — 3 min
4. **Say the CAS-based job claim answer out loud** (Section 4 above) — 2 min
5. **Read the anti-patterns section above** — 3 min
6. **Skim your solution file for whichever problem you think is most likely** — 15 min

That's 28 minutes. The rest is showing up calm.

---

## 🗺️ All Existing Solution Files (bookmarks)

| Problem | File | Type | Tier |
|---|---|---|---|
| TempMail / Disposable Email | [`tempmail-disposable-email.md`](./tempmail-disposable-email.md) | Type 2 — Full HLD | ⭐⭐⭐ |
| Aggregate News Feed / RSS | [`aggregate-news-feed.md`](./aggregate-news-feed.md) | Type 2 — Full HLD | ⭐⭐⭐ |
| API Design (cheatsheet) | [`api-design-cheatsheet.md`](./api-design-cheatsheet.md) | Reference | ⭐⭐⭐ |
| Distributed KV Store | [`distributed-kv-store.md`](./distributed-kv-store.md) | Type 2 — Full HLD | ⭐⭐ |
| Feedly / Podcast | [`feedly-podcast-api-design.md`](./feedly-podcast-api-design.md) | Type 1 — API + Data Model | ⭐⭐ |
| Health Check / wasAlive | [`health-check-monitoring.md`](./health-check-monitoring.md) | Type 2 — Full HLD | ⭐ |

---

## 🧭 Concept Quick-Reference — Must / Good / Skip

> **How to use this section:** Skim each concept title. If you know what it is and can say one sentence about it, move on. If you blank — click the link and read for 5 minutes. This is for cross-question depth probes, not primary prep.
>
> 🎯 = recruiter specifically called out this topic in the audio call. Prioritize these if skimming.
>
> Full rationale for every assignment: [`concepts-priority-map.md`](./concepts-priority-map.md)

---

### ✅ Must Do — 18 concepts (know cold)

| # | Note | One-liner trigger |
|---|---|---|
| 52 🎯 | [Numbers to Know & Scale Triggers](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/52-numbers-to-know-scale-triggers.md) | Back-of-envelope before ANY infra decision. Recruiter said capacity estimation is a key area. |
| 11 | [API Design — REST, pagination, versioning](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/11-api-design.md) | Pure API design round confirmed 3×. Every verb/code/header is tested. |
| 08 | [Bloom Filter](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/08-bloom-filter.md) | TempMail (May 2026) — Bloom filter consumed the entire round. Deep-dive risk. |
| 03 🎯 | [Caching — 5 strategies, eviction, stampede](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md) | Recruiter named caching. URL Shortener + KV Store + TempMail all use Cache-Aside via Redis. |
| 04 | [Idempotency — HTTP + Kafka consumer](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md) | Idempotency-Key header for POST retries. Kafka consumer dedup. Universal probe. |
| 43 | [Pagination — Cursor-Based](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/43-pagination-cursor-based.md) | "How do you paginate the feed?" Cursor vs offset — cursor wins at scale. |
| 02 | [Rate Limiting — token bucket, sliding window](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md) | TempMail abuse prevention. URL Shortener. 429 in API contract. |
| 50 🎯 | [Database Indexing — B-tree, composite, covering](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/50-database-indexing.md) | Recruiter named indexing. "How do you query by short code?" — index design follows every schema. |
| 34 🎯 | [CAP Theorem & Consistency Models](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md) | Recruiter named this explicitly. KV Store forces the choice. Kafka = CP — know why. |
| 38 🎯 | [Sharding Strategy — range, hash, directory](../../../SystemDesignConcepts/Core-Architecture/Database-Core/38-sharding-strategy.md) | Recruiter named sharding. KV Store + URL Shortener. Hash sharding + consistent hashing. |
| 05 | [Consistent Hashing — ring, virtual nodes](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/05-consistent-hashing.md) | KV Store distributed lookup. How to add nodes without reshuffling all keys. |
| 19 | [Message Queues — Kafka vs RabbitMQ](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md) | News Feed ingestion. "Why Kafka and not RabbitMQ?" — Tableflow team must know this cold. |
| 12 | [Relational Data Modeling](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md) | DB+SQL+API round (Apr 2026). Schema for TempMail, URL Shortener, News Feed. |
| 25 🎯 | [Monitoring & Observability — logs, metrics, traces](../../../SystemDesignConcepts/Production-Grade/Observability/25-monitoring-observability-fundamentals.md) | Recruiter named observability. Health Check IS a monitoring system. Three pillars + SLOs. |
| 56 🎯 | [Availability — nines table, serial/parallel math](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/56-availability.md) | Recruiter named HA. JD says 99.99%. Know the nines table + series vs parallel math. |
| 07 | [CDC + Outbox Pattern](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/07-cdc-outbox.md) | News Feed — dual-write risk (write DB + publish event). Outbox = Kafka-native fix. |
| 47 🎯 | [Job Scheduling at Scale — CAS claim, heartbeat, SKIP LOCKED](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md) | Recruiter named concurrency. Confirmed Confluent question (Blind 2025). CAS-based job claim. |
| 22 | [Event Sourcing — immutable log, replay, temporal queries](../../../SystemDesignConcepts/Core-Architecture/Database-Core/22-event-sourcing.md) | Tableflow IS event sourcing. Kafka topic → Iceberg table. The product-specific depth probe. |

---

### 🟡 Good to Have — 12 concepts (know the trigger + one answer)

These will come up as follow-up probes when you name the parent concept. You don't need to read deeply — just know the trigger phrase.

| # | Note | When it gets probed |
|---|---|---|
| 06b 🎯 | [Database Types & Selection — SQL, NoSQL, Redis, Kafka, ES](../../../SystemDesignConcepts/Core-Architecture/Database-Core/06-databases-types-and-selection.md) | "Why Postgres here? Why Redis for TTL?" Every storage decision needs justification. Recruiter named SQL vs NoSQL. |
| 29 🎯 | [DB Replication — master-slave, WAL, RPO/RTO](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md) | Recruiter named replication. KV Store globally distributed. Health Check DB going down. |
| 57 | [Single Point of Failure — SPOF](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/57-spof.md) | "What if your monitoring service goes down?" SPOF identification = 99.99% uptime path. |
| 55 | [Scalability — 7 levers (LB, cache, replicas, sharding, async, CDN, auto-scale)](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/55-scalability.md) | "How does this scale to 100M users?" — use the 7-lever mental model as the answer structure. |
| 45 | [Hot Partition Problem — write salting, hot-key caching](../../../SystemDesignConcepts/Core-Architecture/Database-Core/45-hot-partition-problem.md) | URL Shortener — viral link = 10M reads/sec to one short code. News Feed — popular publisher floods one Kafka partition. |
| 10 | [Backpressure — bounded queues, load shedding](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/10-backpressure.md) | "What if publishers burst?" Kafka consumer lag → backpressure. Producer outpacing consumer. |
| 35 | [Retry & Exponential Backoff](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md) | News Feed polling unreliable publishers. Job Scheduling retry. Universal follow-up probe. |
| 41 🎯 | [Isolation Levels & Dirty Reads](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md) | Recruiter named concurrency. "Which isolation level for TempMail inbox creation?" Read Committed vs Serializable. |
| 01 🎯 | [Optimistic + Pessimistic Locking](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md) | Recruiter named concurrency. URL Shortener collision on short code. Row-level concurrency probe. |
| 06a 🎯 | [Distributed Locking — Redis SETNX, Redlock, fencing token](../../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/06-distributed-locking.md) | Recruiter named concurrency. Job Scheduling — CAS vs distributed lock comparison. |
| 54 | [Redis Internals — single-threaded, 5 atomic weapons, RDB vs AOF](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/54-redis-internals.md) | TempMail — EXPIRE (TTL), SET NX EX (atomic reservation), BITSET (Bloom filter). Redis is the implementation layer. |
| 40 | [Multi-Region & Geo-Failover](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md) | KV Store question is "globally distributed." Route reads to nearest replica. Handle region failure. |

---

### ❌ Can Skip — safe to ignore today

These are not in Confluent's confirmed question bank. Prepping these over the above is pure opportunity cost.

[Geospatial Indexing](../../../SystemDesignConcepts/Core-Architecture/Database-Core/51-geospatial-indexing.md) · [Push Notifications / Fanout](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/46-push-notifications-fanout.md) · [Inventory & Booking](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/42-inventory-management-booking.md) · [Elasticsearch](../../../SystemDesignConcepts/Production-Grade/Performance-Optimization/32-elasticsearch-inverted-index.md) · [WebSocket](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md) · [Consensus Algorithms — Raft/Paxos](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/37-consensus-algorithms-raft-vs-paxos.md) · [Two-Phase Commit vs Saga](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/36-two-phase-commit-vs-saga.md) · [Saga Pattern](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md) · [gRPC & Protobuf](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/33-grpc-protocol-buffers.md) · [Service Discovery](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/18-service-discovery-dns.md) · [Auth & AuthZ](../../../SystemDesignConcepts/Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md) · [JWT Token Storage](../../../SystemDesignConcepts/Production-Grade/Auth-and-Security/27-jwt-token-storage-reference.md) · [Security/PKI](../../../SystemDesignConcepts/Production-Grade/Auth-and-Security/13-security-pki.md) · [Bulkheads](../../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/39-bulkheads-resource-isolation.md) · [API Gateway](../../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/24-api-gateway-pattern.md) · [Sharded Counters](../../../SystemDesignConcepts/Foundations/Performance-and-Scale/09-sharded-counters.md) · [Blob/S3 Storage](../../../SystemDesignConcepts/Foundations/Data-Fundamentals/14-document-blob-storage.md) · [Connection Pooling](../../../SystemDesignConcepts/Core-Architecture/Database-Core/16-connection-pooling-db-performance.md) · [Leader Election](../../../SystemDesignConcepts/Core-Architecture/Distributed-Systems/21-leader-election-consensus.md) · [Webhooks](../../../SystemDesignConcepts/Core-Architecture/Service-Communication/53-webhooks.md)

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | Created from recruiter audio call transcript analysis. Covers 5 gap areas: capacity estimation (3 drills), CAP×Kafka verbal answer, 5 Kafka monitoring metrics, concurrency (CAS + consumer group), HA math. All cross-refs verified as clickable relative paths. |
