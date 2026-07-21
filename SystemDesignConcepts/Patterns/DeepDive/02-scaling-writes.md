# Pattern Deep Dive: Scaling Writes

> **Read this when:** You need to understand how to handle write-heavy workloads — ingesting millions of events per second without losing data, killing the database, or blocking callers.
> **Pre-interview refresh:** Use `Reference/02-scaling-writes.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

Your write path is the bottleneck. Symptoms:

- DB CPU is high even without complex queries — raw INSERT volume is overwhelming it
- Write latency p99 climbing under sustained load
- Disk I/O saturated on the primary DB node
- Callers timing out waiting for write acknowledgment
- A single write-hot table (events, logs, metrics, orders) is causing lock contention

The root cause: a Postgres primary handles ~10–20K writes/sec before CPU and disk I/O saturate. At high write volume, you have two choices — reduce how often you hit the DB, or distribute writes across more DBs.

The fix is **decoupling write acceptance from write persistence**: accept writes instantly (in memory), persist them durably in the background.

---

## 💡 Core Insight

**Write volume and write durability are separate concerns.** A database conflates them: it accepts a write AND makes it durable (fsync to disk) in one synchronous operation. That's why it's the bottleneck.

The key insight: **put a fast, durable buffer in front of the database.** Accept writes at memory speed, drain to DB at disk speed. The buffer absorbs burst, smooths out spikes, and lets the DB operate within its comfortable throughput.

> **KEY INSIGHT:** "Separate where writes are accepted from where writes are persisted. Buffer absorbs bursts. DB drains at its own pace."

---

## 🗂️ The 4 Strategies (Simple → Complex)

---

### Strategy 1 — Write Buffering via Message Queue (Kafka)

🧠 **Mental model:** Uber driver location updates — 125K updates/sec from 500K active drivers. No DB absorbs that directly. Kafka accepts all updates at memory speed; consumers drain to Cassandra at DB pace. Burst absorbed; DB never sees the spike.

Add a message queue between your application and your database. Writers publish events to Kafka (milliseconds). Consumers read from Kafka and write to DB at DB's natural pace.

**When to use:**
- High write throughput with bursty patterns (event ingestion, clickstream, logs)
- Writes can be processed asynchronously (caller doesn't need DB confirmation immediately)
- Multiple downstream consumers need the same write data (fan-out)
- You want replay capability (reprocess historical events)

**When NOT to use:**
- Caller MUST confirm DB persistence before responding (payment confirmation, seat booking)
- Write order within a partition matters AND you need cross-partition ordering
- Simple CRUD with no downstream fan-out — Kafka adds complexity without benefit

**How it works:**

**Steps in plain English:**
1. **Publish** — Application writes event to Kafka topic (append to in-memory log, replicated across brokers).
2. **ACK** — Kafka returns ACK in < 5ms (write accepted, not yet in DB).
3. **Consume** — Consumer reads from Kafka at its own pace.
4. **Batch write** — Consumer batches N events and writes to DB in one INSERT statement.
5. **Commit offset** — Consumer commits Kafka offset (marks events as processed).

```
Write Path:

App Server ──▶ Kafka Topic ──▶ Consumer Group ──▶ Postgres
               (fast: ~5ms)    (steady pace)       (batched)

     ┌──────────────────────────────────────────────────────────┐
     │ Kafka Broker Cluster                                      │
     │  Topic: "order-events"                                   │
     │  ┌──────────┐ ┌──────────┐ ┌──────────┐                 │
     │  │Partition0│ │Partition1│ │Partition2│  ← parallel      │
     │  │ (append) │ │ (append) │ │ (append) │    write lanes   │
     │  └────┬─────┘ └────┬─────┘ └────┬─────┘                 │
     └───────┼─────────────┼─────────────┼───────────────────────┘
             │             │             │
     ┌───────▼─────────────▼─────────────▼───────────────────────┐
     │ Consumer Group (3 consumers, one per partition)            │
     │ Batches 500 events → INSERT into Postgres every 100ms     │
     └───────────────────────────────────────────────────────────┘

Throughput: Kafka handles 1–2M messages/sec per broker cluster
DB load: reduced to batch INSERTs (much lower than per-event INSERTs)
```

**Key trade-off: eventual persistence**
The caller gets ACK when Kafka accepts the write — not when DB persists it. If the consumer crashes after Kafka ACK but before DB write, the event sits in Kafka and will be reprocessed. Result: **at-least-once delivery** — consumers must be idempotent.

---

### Strategy 2 — Write Batching (Reduce DB Round-trips)

🧠 **Mental model:** Analytics event ingestion — 10K clicks/sec hit Kafka; consumer batches 500 events → single INSERT. 10K DB round-trips/sec → 20 DB round-trips/sec. Same data volume, 500x fewer DB operations.

Instead of one DB write per event, accumulate N events in memory and flush as a single bulk INSERT.

**When to use:**
- Already have Kafka (consumers batch naturally)
- Time-series data, analytics events, log ingestion
- INSERT volume is high but individual rows are small
- Order within a batch doesn't matter

**Impact:**
```
Without batching: 10,000 events/sec = 10,000 DB round-trips/sec (expensive)
With batching (batch=500): 10,000 events/sec = 20 DB round-trips/sec (500x reduction)

Each round-trip: ~5ms (network) + ~1ms (query parse) + disk I/O
Batching amortizes the fixed cost across 500 rows
```

**Failure mode:** If the service crashes mid-batch, buffered events are lost. Fix: use Kafka as the buffer (events survive crash) and batch from Kafka consumer, not from in-memory application buffer.

---

### Strategy 3 — Database Sharding (Horizontal Write Scale)

🧠 **Mental model:** WhatsApp messages sharded by conversation_id. Each shard handles a slice of conversations. 4 shards = 4× write capacity. A message between users A and B always hits shard(conversation_id) — no cross-shard coordination.

Distribute write load across multiple independent database primaries, each owning a partition of the data.

**When to use:**
- Single primary is the bottleneck AND batching/buffering are already applied
- Data has a natural partition key (user_id, tenant_id, geo_region)
- Cross-shard queries are rare or acceptable to be slow
- Write volume per shard is predictable

**When NOT to use:**
- You need cross-shard transactions (extremely hard to implement correctly)
- Your access pattern doesn't have a good shard key (everything ends up on one shard)
- Write volume is not yet at single-node capacity (sharding adds ops complexity for no gain)

**How it works:**
```
Shard key: user_id % N (N = number of shards)

user_id=1001 → shard 1001 % 4 = 1 → Postgres Shard 1
user_id=1002 → shard 1002 % 4 = 2 → Postgres Shard 2
user_id=1003 → shard 1003 % 4 = 3 → Postgres Shard 3
user_id=1004 → shard 1004 % 4 = 0 → Postgres Shard 0

┌────────────────────────────────────────────────────────────┐
│              Application / Shard Router                     │
│    hash(shard_key) % N → route to correct shard            │
└──────────┬──────────────┬──────────────┬───────────────────┘
           │              │              │
    ┌──────▼──────┐ ┌─────▼──────┐ ┌───▼────────┐
    │  Shard 0    │ │  Shard 1   │ │  Shard 2   │  ...N shards
    │  Postgres   │ │  Postgres  │ │  Postgres  │
    │  Primary    │ │  Primary   │ │  Primary   │
    └─────────────┘ └────────────┘ └────────────┘

Total write capacity = N × single-shard capacity
```

**Hot shard problem:** If one shard key is disproportionately popular (celebrity user, viral product), one shard gets all the traffic. Fix: composite shard key (user_id + random suffix) or consistent hashing with virtual nodes.

**Hot key splitting:** For a single viral key (e.g., tweet from a celebrity with 100M followers), split writes into K sub-keys (`tweet:789:fan:0` through `:fan:K-1`). Each write picks a random sub-key. Reads aggregate all K. Same idea as sharded counters (Pattern 04 — Contention) but applied at the write fan-out level.

---

### Strategy 4 — CQRS with Append-Only Write Model

🧠 **Mental model:** Financial audit log — every account transaction is appended as an immutable event (DEBIT $100, CREDIT $200). Current balance = sum of events. Maximum write speed (append-only, no lock contention) + complete history for auditing.

Completely separate write and read models. The write model stores events as an append-only log (no UPDATEs, no DELETEs — only INSERTs). The current state is derived by replaying events.

**When to use:**
- Write throughput is the absolute priority (append-only = maximum write speed)
- You need full audit history (every state change is recorded)
- Complex business workflows where event history matters (finance, legal, logistics)
- Read and write query patterns are fundamentally different

**When NOT to use:**
- Simple CRUD with no audit requirement — CQRS adds enormous complexity
- Team is not familiar with event-driven architecture
- Latency to derive current state from events is unacceptable

**How it works:**
```
Write model (append-only):
  POST /orders → INSERT event: {type: ORDER_PLACED, ...}
  PATCH /orders/1/confirm → INSERT event: {type: ORDER_CONFIRMED, ...}
  No UPDATEs ever. Only INSERTs.

  → Extremely fast writes (no lock contention, no UPDATE conflicts)
  → Infinite horizontal scale (event log is append-only)

Read model (materialized from events):
  Consumer reads event stream and builds read-optimized state tables
  → "Orders" table = current state, rebuilt from events

                   ┌─────────────────┐
   Write Path: App ──▶ Event Store    │ (append-only, very fast)
                   │ (Kafka or DB)    │
                   └────────┬────────┘
                            │ event stream
                   ┌────────▼────────┐
                   │ Projection      │ (async consumer)
                   │ Consumer        │
                   └────────┬────────┘
                            │ writes materialized state
                   ┌────────▼────────┐
   Read Path: App ◀── Read DB        │ (query-optimized)
                   └─────────────────┘
```

---

## 🧭 Decision Sequence

```
START: Write throughput is the bottleneck

Step 1 ── Profile first
          Is it actually write volume OR:
          - Missing indexes on write table? (slow UPDATE, not slow INSERT)
          - Inefficient write patterns? (one INSERT per row vs bulk INSERT)
          - Table too wide? Vertical partitioning: split a wide table with many
            columns (post_content + post_metrics + post_analytics) into 3
            narrower tables by access pattern. Writes to metrics don't lock
            content. Free structural win before touching infrastructure.
          Fix these first. They're free.

          Load shedding: if write volume spikes beyond sustainable rate, drop
          low-value writes rather than letting the DB fall over. Uber drops
          location updates arriving within 1 second of a previous update from
          the same driver — the second update is redundant anyway.

Step 2 ── Add write batching (if writes are from your own service)
          Group 100–1000 rows per INSERT statement.
          Immediate 10–100x reduction in DB round-trips.

Step 3 ── Add Kafka (if writes come from multiple producers or need fan-out)
          Decouple acceptance from persistence.
          Buffer absorbs bursts. Consumers drain at DB's pace.
          Gain: burst absorption, replay, fan-out to multiple consumers.

Step 4 ── Shard the database (if single primary is still saturated)
          Choose shard key carefully (avoid hot shards).
          Start with 2–4 shards. Add shards as needed.

Step 5 ── CQRS / Append-only write model (if write and read models diverge fundamentally)
          Full architectural commitment. Add only if prior steps are insufficient.
```

---

## 🎨 Visual — Full Architecture

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

> Valid alternative. DynamoDB and Cassandra are write-optimized: DynamoDB handles unlimited writes with auto-scaling; Cassandra uses an LSM tree (log-structured merge-tree — a data structure that converts random writes to sequential disk writes, dramatically improving write throughput) that converts random writes to sequential disk writes. Choose NoSQL when: write patterns are simple (key-value, time-series, no complex joins), you can accept eventual consistency, and you're willing to give up SQL query flexibility. Choose Kafka + Postgres when: you need SQL query power on the read side, you have fan-out requirements (multiple consumers), or you need event replay capability. They're not mutually exclusive — Kafka feeds into Cassandra or DynamoDB in many architectures.

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

### Q: "A celebrity posts a tweet. 10M followers need to see it. How do you handle the write fan-out?"

> Naively writing to 10M user timelines per tweet is O(N) DB writes — one celebrity tweet consumes the entire write capacity for seconds. Three approaches in order of complexity: (1) **Pull model (on-read):** Store the tweet once. On read, each user's timeline query fetches recent tweets from followed users (JOIN or sorted-set merge). Works for moderate follower counts but read latency scales with follow count. (2) **Hierarchical aggregation:** Fan out to intermediate broadcast nodes (e.g., 100 region nodes × 100K users each). Parallelizes fan-out without one writer doing 10M DB writes. (3) **Hybrid (Twitter's approach):** Pre-fan-out writes for normal users (< 1M followers) so reads are fast. For celebrities, inject their tweets at read time — most followers see from cache anyway, so the "push to 10M" cost is avoided. The insight: write fan-out and read-time assembly are both valid; choose based on follower count distribution.

---

## ⚠️ Anti-patterns

- **Synchronous DB writes on the hot path for high-volume events.** Writing a clickstream event, a view count, or a log entry synchronously per HTTP request couples your API latency to DB write latency. Under traffic spikes, the DB slows down, API p99 climbs, and your entire system degrades together. These events don't need immediate persistence — buffer them and flush asynchronously.

- **Unbounded in-memory write buffers.** Accumulating writes in application memory (a `List<Event>` that gets flushed periodically) loses all data if the process crashes. Use Kafka or the outbox pattern as your buffer — these are durable. In-memory buffering is only acceptable for truly disposable data (counters that can be re-derived, metrics that can tolerate gaps).

- **Sharding before profiling.** Sharding is operationally expensive — cross-shard queries are complex, rebalancing is painful, and schema changes require coordination across all shards. Many "write scaling" problems are actually inefficient write patterns: missing indexes causing slow UPDATEs, single-row INSERTs that could be bulk INSERTs, or write amplification from ORM-generated queries. Profile before sharding. Sharding should be the last resort, not the first instinct.

---

## 🗺️ Problems Map

| Interview Problem | Why Scaling Writes Applies | Primary Strategy |
|---|---|---|
| Design Metrics / Analytics Pipeline | Billions of events/day from all services | Kafka → ClickHouse/Cassandra |
| Design Uber / Ride Tracking | Location updates every 3s × millions of drivers | Kafka → time-series DB |
| Design Rate Limiter (distributed) | Counter writes per request across all users | Redis atomic ops (not DB writes) |
| Design Notification Service | Millions of notifications generated per event | Kafka fan-out to notification workers |
| Design Logging System | Every service emits logs constantly | Kafka → Elasticsearch / S3 |
| Design Flash Sale | Order writes spike 100x at sale start | Kafka buffer + async order processing |
| Design Twitter (write path) | 500M tweets/day + fan-out writes | Kafka + sharded write DB |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **Kafka internals** (partitions, offsets, consumer groups, retention) → `../../Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md`
- **Outbox pattern** (transactional write + event emission) → `../../Foundations/Data-Fundamentals/07-cdc-outbox.md`
- **Sharding strategy** (shard key selection, hot partition problem) → `../../Core-Architecture/Database-Core/38-sharding-strategy.md`
- **Hot partition problem** → `../../Core-Architecture/Database-Core/45-hot-partition-problem.md`
- **CQRS** → `../../Production-Grade/System-Design-Patterns/31-cqrs-read-write-separation.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Batch 1 of 8 remaining patterns. |
| July 2026 | Added 🧠 mental model anchors per strategy. Added vertical partitioning + load shedding to Step 1. Added hot key splitting to Strategy 3. Added fan-out hierarchical aggregation Q&A. |
| Jul 20, 2026 | Fixed arithmetic error in batching key numbers box: "50x reduction" contradicted the mental model anchor (500x) and the math (10,000 ÷ 500 = 20 = 500x, not 50x). |
