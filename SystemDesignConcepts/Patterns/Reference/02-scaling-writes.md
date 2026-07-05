# Scaling Writes — Quick Reference

> **Read this:** 30 min before an interview involving write-heavy systems.
> **Deep study:** `DeepDive/02-scaling-writes.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **DB is the bottleneck on writes** — CPU high from raw INSERT volume, write latency climbing, disk I/O saturated, callers timing out.

Trigger words: "ingest 1M events/sec", "clickstream", "logging system", "metrics pipeline", "write-heavy", "high throughput writes", "flash sale order storm".

---

## 🧭 Decision Sequence

```
START: Write throughput is the bottleneck

Step 1 → Profile first
         Missing indexes? Inefficient write patterns? Fix these — they're free.
         Single-row INSERTs → bulk INSERTs? Do that first.

Step 2 → Add write batching
         Group 100–1000 rows per INSERT. 10–100x reduction in DB round-trips.

Step 3 → Add Kafka (if writes come from multiple producers or need fan-out)
         Decouple acceptance (memory speed) from persistence (disk speed).
         Buffer absorbs bursts. Consumers drain at DB's pace.

Step 4 → Shard the database (if single primary is still saturated)
         Choose shard key carefully (high cardinality, matches access pattern, no hot shards).
         Start with 2–4 shards.

Step 5 → CQRS / Append-only write model (if write and read models diverge fundamentally)
         Full architectural commitment. Last resort.
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Write Batching** | High INSERT volume, same service, order doesn't matter | Need row-level immediacy |
| **Kafka Buffering** | Multiple producers, fan-out, need replay, burst absorption | Simple CRUD, caller needs DB confirmation immediately |
| **DB Sharding** | Single primary saturated after batching+Kafka, clear shard key | Cross-shard queries needed, no natural partition key |
| **CQRS / Append-only** | Full audit trail, write/read patterns fundamentally different | Simple CRUD, team not ready for event-driven complexity |

**Key numbers to remember:**
- Postgres primary write cap: ~10–20K writes/sec
- Kafka throughput: 1–2M messages/sec per cluster
- Batching impact: 10K single-row INSERTs/sec → 20 bulk INSERTs/sec (batch=500) = 50x reduction
- Consumer lag = how far persistence lags behind acceptance — monitor this

---

## 🎨 Key Architecture Diagram

```
                Producers (many)
                ┌──────┐ ┌──────┐ ┌──────┐
                │App 1 │ │App 2 │ │App 3 │
                └──┬───┘ └──┬───┘ └──┬───┘
                   │        │        │  writes (< 5ms ACK)
        ┌──────────▼────────▼────────▼───────────┐
        │           Kafka Cluster                  │ ← Strategy 1: buffer
        │  Topic: writes  (N partitions)           │
        └──────────┬────────┬────────┬────────────┘
                   │        │        │ consumers drain steadily
        ┌──────────▼────────▼────────▼────────────┐
        │        Consumer Group                    │ ← Strategy 2: batch
        │  Batch 500 events → INSERT every 100ms  │
        └─────────────────────┬───────────────────┘
                              │
        ┌─────────────────────▼───────────────────┐
        │  Shard Router (hash(shard_key) % N)      │ ← Strategy 3: shard
        └──────┬──────────────┬────────────────────┘
               │              │
      ┌────────▼──┐  ┌────────▼──┐
      │ Shard 0   │  │ Shard 1   │  ...N shards
      │ Postgres  │  │ Postgres  │
      └───────────┘  └───────────┘

KEY INVARIANT:
   Writes are accepted at memory speed (Kafka), persisted at disk speed (DB).
   The gap between acceptance and persistence is the buffer — never let it grow unbounded.
   Consumer lag = how far behind persistence is from acceptance. Monitor this.
```

---

## 🔬 Interview Q&A

### Q: "How is Scaling Writes different from Scaling Reads?"

> Fundamentally different constraints. Reads are idempotent — you can serve from caches, replicas, or precomputed results. Writes must be persisted exactly once to a durable store. Scaling reads is about multiplying where you serve from. Scaling writes is about decoupling when you accept from when you persist, and distributing the persistence load. A cache helps reads; it doesn't help writes. A read replica takes read load off primary but adds zero write capacity.

---

### Q: "Kafka is down. What happens to your write pipeline?"

> Writes fail at the Kafka publish step — callers get an error. The DB is untouched (good — no data corruption). Two mitigations: (1) Retry with exponential backoff for transient failures. (2) Fallback: if Kafka is unavailable, write a small volume directly to DB (degraded mode — only works if DB can handle reduced load). The key insight: Kafka being in the write critical path means it's a reliability dependency, not just a performance optimization. Run Kafka with replication factor ≥ 3 and treat broker failures as an expected operational event.

---

### Q: "A consumer crashes after reading from Kafka but before writing to DB. What happens to those events?"

> With Kafka's offset model: the consumer hadn't committed its offset yet, so Kafka still considers those messages unprocessed. When the consumer restarts (or another consumer in the group takes over), it re-reads from the last committed offset and processes the events again. This is at-least-once delivery — the same event may be written to DB twice. Fix: make your DB writes idempotent. Use a unique constraint on event_id or an INSERT ON CONFLICT DO NOTHING. This ensures duplicate processing has no effect.

---

### Q: "How do you choose a shard key?"

> Three criteria: (1) High cardinality — many distinct values so writes distribute evenly. user_id is good; status (active/inactive) is terrible. (2) Matches access pattern — queries should target a single shard. If you query by user_id, shard by user_id. If you shard by user_id but query by email, every query hits every shard. (3) Avoids hot shards — no single value should generate disproportionate traffic. Celebrity users, viral products all fail this. Fix: composite key (user_id + random bucket) to spread a single user's writes across multiple shards.

---

### Q: "Your write latency is high. You add more application servers. Does that help?"

> Only if the bottleneck is in the application tier (CPU-bound computation before writing). If the bottleneck is the database, more app servers make things worse — they all pile onto the same DB, increasing contention. Diagnosis: check DB connections at saturation, DB CPU, and DB wait events. If DB is the bottleneck, the fix is buffering (Kafka) or sharding — not more app servers.

---

### Q: "You're ingesting 1M events/sec. Walk me through how you'd design this."

> At 1M events/sec, no single Postgres primary survives direct ingestion. My approach: (1) Producers write to Kafka (handles 1–2M msgs/sec per cluster with horizontal partition scaling). (2) Consumer groups read from Kafka partitions and batch-write to DB. With batch size 1000 and 100ms flush interval, 1M events/sec becomes 1000 INSERTs/sec — well within Postgres capacity. (3) If 1000 INSERTs/sec is still too high for one shard, shard by event type or tenant. (4) Consider a purpose-built time-series store (Cassandra, ClickHouse) if queries are time-range reads — they're 10x more write-efficient than Postgres for append-only time-series.

---

### Q: "Why not just use a NoSQL database for high write volume instead of all this Kafka complexity?"

> Valid alternative. DynamoDB and Cassandra are write-optimized: DynamoDB handles unlimited writes with auto-scaling; Cassandra uses an LSM tree that converts random writes to sequential disk writes. Choose NoSQL when: write patterns are simple (key-value, time-series, no complex joins), you can accept eventual consistency, and you're willing to give up SQL query flexibility. Choose Kafka + Postgres when: you need SQL query power on the read side, you have fan-out requirements (multiple consumers), or you need event replay capability. They're not mutually exclusive — Kafka feeds into Cassandra or DynamoDB in many architectures.

---

### Q: "Kafka consumer lag keeps growing. What do you do?"

> Consumer lag = messages in Kafka not yet processed. Growing lag means consumers are slower than producers. Diagnosis first: (1) Is consumer slow because DB writes are slow? → Add more batching, optimize INSERT, or add DB sharding. (2) Is consumer slow because downstream processing is CPU-bound? → Scale out consumer instances (add more consumers in the group, up to the number of partitions). (3) Is it a spike or sustained? → Spikes are okay; Kafka is a buffer. Sustained growth means producer throughput permanently exceeds consumer capacity — need architectural change. Never ignore growing lag — it means your buffer is filling, and once Kafka's retention period is exceeded, old events are deleted.

---

### Q: "What's the difference between Kafka and a traditional message queue (RabbitMQ) for scaling writes?"

> Key difference: Kafka retains messages after consumption (configurable retention, default 7 days). RabbitMQ deletes messages after a consumer ACKs them. This matters for writes because: (1) Kafka allows replay — if your DB had a bug last week, you can replay Kafka events from 7 days ago to reprocess correctly. (2) Kafka allows multiple independent consumer groups — the same write event can feed your analytics pipeline AND your main DB AND your search indexer simultaneously. RabbitMQ is point-to-point; Kafka is a pub/sub log. For scaling writes with fan-out requirements, Kafka wins decisively.

---

### Q: "Can you scale writes without Kafka? What's the simplest approach?"

> Yes. If Kafka feels like overengineering for your scale: (1) Write batching in the application layer — accumulate N writes in memory, flush every X milliseconds. Simple but loses data on crash. (2) Outbox pattern — write to a local "outbox" table in the same DB transaction as your business write, then a background job processes the outbox. Transactional safety without a separate broker. (3) Connection pooling + PgBouncer — ensure DB connections aren't the bottleneck before attributing problems to write volume. Many "scaling writes" problems are actually "too many connections" problems. Start simple. Add Kafka when you have fan-out requirements or when a single service's write rate genuinely exceeds DB capacity.

---

## ⚠️ Anti-patterns (don't say these)

- **Synchronous DB writes per event on the hot path** — couples API latency to DB write latency; use async buffering
- **Unbounded in-memory write buffers** — process crashes = data lost; use Kafka or outbox pattern (durable buffers)
- **Sharding before profiling** — operationally expensive; fix N+1 queries and bulk INSERTs first

---

## 🧩 Common Interview Problems

| Problem | Strategy | Key decision |
|---|---|---|
| Design Metrics / Analytics Pipeline | Kafka → ClickHouse / Cassandra | Time-series store, not Postgres |
| Design Uber location tracking | Kafka → Redis + time-series DB | 125K writes/sec from 500K drivers |
| Design Notification Service | Kafka fan-out to workers | One post event → N notification workers |
| Design Logging System | Kafka → Elasticsearch / S3 | All services write to Kafka, not DB |
| Design Flash Sale | Kafka buffer + async order processing | Burst absorption at sale start |

---

## 🔗 Full notes

`DeepDive/02-scaling-writes.md` — decision playbook, failure mode Q&A, worked examples
