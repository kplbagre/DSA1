# 59 — NoSQL Deep Dive: Cassandra vs MongoDB vs DynamoDB

---

## 📖 What is this?

**Full form:** This covers the three dominant NoSQL databases — Apache Cassandra (wide-column store), MongoDB (document store), Amazon DynamoDB (managed key-value + document store).

**Simple analogy:** Choosing between these is like choosing between three different filing systems. Cassandra is a warehouse with aisles (partitions) — finding everything in one aisle is blazing fast, but hunting across aisles requires checking each one separately. MongoDB is a filing cabinet with rich labels on each folder — you can search by any label, but the cabinet doesn't scale as wide. DynamoDB is a hotel key-card system maintained by a hotel chain (AWS) — the hotel manages everything, your only design decision is which rooms you put together.

**Core principle:** All three are NoSQL (no rigid schema, horizontal write scaling) but they differ on three axes: query flexibility, operational ownership, and consistency tuning model. Note their *default* consistency postures differ — Cassandra and DynamoDB read eventually by default, MongoDB reads from the primary and is **strongly consistent by default**.

**Why it matters in system design:** "SQL or NoSQL?" has a clear answer in most cases. The hard question — the one that separates senior candidates from midlevel — is "which NoSQL and why?" This is the most debated database question in Staff+ and Principal-level interviews.

---

## 🎯 Why This Matters

In every system design round above IC3 (Senior SWE), the interviewer will push past "use NoSQL for scale" and ask: *"Why Cassandra and not DynamoDB? Why not MongoDB?"* Not knowing the distinguishing axis is an immediate down-level signal. This note gives you the crisp 3-axis framework and the internals to survive the follow-up probe.

**Which round:** R2 System Design — database selection and its justification.
**Why senior engineers own this:** Junior engineers pick "any NoSQL" based on vague familiarity. Senior engineers pick the right NoSQL based on query model, operational overhead, and consistency requirements — and can defend the choice under pressure.

---

## 🧠 The Mental Model

**The three different postal systems analogy.**

Imagine three ways to send and retrieve mail for a billion customers.

**Cassandra — the sorted warehouse with designated aisles:**
Every customer is assigned to one aisle (partition) based on a hash of their account number. All their mail is sorted chronologically on that aisle's shelf (clustering columns within the partition). Finding "all of Alice's mail from March" is instant — walk to Alice's aisle, scan the March section. But "all mail sent on March 10th by anyone" is slow — you must check every aisle in the warehouse independently and merge the results.

**MongoDB — the intelligent filing room:**
Mail arrives as packets (documents) of any shape — some have attachments, some don't, some have headers others lack. Every packet has rich labels. You can search by any label combination ("sender = Alice AND subject contains 'invoice'"). The downside: as the filing room grows to multiple warehouses, routing the search request to the right warehouse becomes complex.

**DynamoDB — the outsourced hotel concierge:**
Someone else (AWS) manages the entire physical building — maintenance, scaling, replication. You tell the concierge "this is John's room number" (partition key) and "here's the item date" (sort key). Within John's room, items are sorted so you can ask "give me John's items from last week" efficiently. But searching by anything other than the room number requires the concierge to check every room (GSI — expensive scan).

**The key insight is:** Cassandra gives you tunable consistency and raw write scale. DynamoDB gives you zero operations overhead with a constrained query model. MongoDB gives you query flexibility with multi-document ACID transactions — at the cost of per-shard write throughput at extreme volumes.

---

## 🎨 Visual — The 3-Axis Selection Framework

```
AXIS 1: QUERY FLEXIBILITY
Low ─────────────────────────────────────────────▶ High
Cassandra          DynamoDB             MongoDB
(partition key     (partition key       (any field,
 + clustering       + sort key,          rich aggregation
 columns only;      GSI for others)      pipeline)
 cross-partition
 scans = slow)

AXIS 2: OPERATIONAL OVERHEAD
High ────────────────────────────────────────────▶ Low (managed)
Cassandra          MongoDB              DynamoDB
(self-host;        (Atlas = managed,    (fully managed by
 ring, repair,     or self-host)         AWS, no tuning)
 compaction,
 tombstone mgmt)

AXIS 3a: HOW TUNABLE IS CONSISTENCY?
Coarse ──────────────────────────────────────────▶ Fine-grained
DynamoDB           MongoDB              Cassandra
(2 choices:        (read concern +      (per-query CL:
 eventual, or       write concern,       ONE / QUORUM / ALL
 strong read        per operation)       + LWT/SERIAL for
 at 2x RCU)                              compare-and-set)

AXIS 3b: DEFAULT POSTURE (different question — don't conflate)
Strong by default ───────────────────────▶ Eventual by default
MongoDB            DynamoDB             Cassandra
(reads go to       (eventual reads      (driver default is
 the primary)       unless you ask       LOCAL_ONE)
                    for strong)

KEY INVARIANT:
  No single winner. Cassandra wins on write scale + tunable consistency.
  DynamoDB wins on zero-ops and AWS-native integration.
  MongoDB wins on query flexibility and multi-document transactions.
  Tunability and default posture are SEPARATE axes: Cassandra is the most
  tunable AND the weakest by default. Never state one as if it implies
  the other.
```

---

### 🎨 Visual — Cassandra Ring Topology & Write Path

```
CASSANDRA RING (RF=3, 6 nodes):

           Token: 0
              │
     Node A ──┼── Node B
    /          │          \
Node F         │           Node C
    \          │          /
     Node E ──┼── Node D
              │
           Token: 360

  Partition key "alice_123" is hashed → token 45
  Token 45 falls between Node A (token 0) and Node B (token 60)
  With RF=3: data written to Node B + Node C + Node D (next 3 nodes clockwise)

WRITE PATH (per node):
  Request arrives
      │
      ▼
  Commit Log (WAL — appended before the write is acked)  ← see caveat below
      │
      ▼
  MemTable (in-memory sorted tree — fast writes, no disk I/O)
      │
      ▼ (when MemTable is full)
  SSTable (immutable sorted file on disk) — append-only, never updates in place
      │
      ▼ (background, periodic)
  Compaction — merges SSTables, removes tombstones (deleted record markers)

QUORUM FORMULA:
  RF = Replication Factor (number of copies)
  Quorum = floor(RF/2) + 1   →  RF=3 → Quorum = 2

  W + R > RF → STRONG CONSISTENCY
  W=2, R=2, RF=3 → 2 + 2 = 4 > 3 → Strong ✅ (always see latest write)
  W=1, R=1, RF=3 → 1 + 1 = 2 ≤ 3 → Eventual ❌ (might read stale replica)

KEY INVARIANT:
  Cassandra writes are always appends (SSTable is immutable).
  Deletes create tombstone markers, not actual deletes.
  Compaction eventually removes tombstones — heavy delete patterns
  accumulate tombstones and slow reads until compaction catches up.
  Range queries on clustering columns WITHIN a partition = fast (sorted).
  Range queries ACROSS partitions = scatter-gather across the ring = slow.
```

---

## ⚙️ How It Actually Works

### Part 1 — Cassandra: Partition Key + Clustering Columns

**The most important design decision in Cassandra is the primary key.**

```
PRIMARY KEY = (partition_key [, clustering_columns...])
```

```sql
-- Example: User activity log
-- Requirement: "Get all activities for user_id=123 ordered by timestamp"

-- GOOD: partition key = user_id, clustering column = event_time
CREATE TABLE user_activity (
    user_id    UUID,
    event_time TIMESTAMP,
    action     TEXT,
    device     TEXT,
    PRIMARY KEY (user_id, event_time)  -- partition on user_id, sort by event_time
) WITH CLUSTERING ORDER BY (event_time DESC);

-- Query: all activities for user_id=123 in the last hour → FAST
-- SELECT * FROM user_activity WHERE user_id = 123 AND event_time > (now - 1h)
-- Touches exactly ONE partition. Scans within-partition sorted data.

-- BAD: partition key = event_date
-- All of today's activity across all users hits ONE partition → hot partition
-- Range query by date across users → scatter-gather across all partitions
```

**Cross-partition range scan — the anti-pattern:**

```sql
-- BAD QUERY: "Get all activities between two dates across all users"
-- SELECT * FROM user_activity WHERE event_time > '2026-07-01'
-- Cassandra must scatter to ALL partitions and gather results
-- This is why Cassandra is wrong for "give me yesterday's sales across all customers"
-- Use Elasticsearch or BigQuery for cross-entity analytics instead
```

**Commit log durability caveat — the follow-up that catches people:** the default is `commitlog_sync: periodic` with a 10-second sync period, which means Cassandra **acks the write before the commit log is fsync'd to disk**. A power loss can therefore drop up to 10 seconds of acknowledged writes on that node. Replication is what saves you in practice (RF=3 means 2 other nodes also took the write). If you need a true per-write durability guarantee on a single node, set `commitlog_sync: batch` — and pay the fsync latency on every write. Say *"durable once replicated,"* not *"durable on ack."*

**What is a Tombstone?** A tombstone is a special marker Cassandra writes when you delete a row — instead of removing the row immediately (which would require finding and updating multiple SSTable files), it appends a deletion marker. During reads, Cassandra merges SSTables and filters out tombstoned rows. During compaction (the background process that merges and rewrites SSTables), tombstones are finally removed.

**Why tombstones matter:** If your access pattern deletes and reinserts rows at high frequency (e.g., "update a user's session by delete + reinsert"), tombstones accumulate faster than compaction removes them. A partition with 10,000+ tombstones causes visible read latency spikes — Cassandra must scan and discard each one during a read.

**Interview-safe statement:** *"In Cassandra, updates and deletes are appends under the hood — an UPDATE writes a new SSTable cell with a higher timestamp; a DELETE writes a tombstone. Compaction reconciles all versions. Heavy delete patterns accumulate tombstones, causing read slowdown until compaction catches up. The fix: model data so deletes are rare (use TTL instead of explicit DELETE for expiring data)."*

---

### Part 2 — DynamoDB: Partition Key + Sort Key Model

**What is a GSI?** A Global Secondary Index (GSI) is an alternate index on a DynamoDB table that allows querying by non-primary key attributes. It projects a copy of the data, so it has its own separate read/write throughput.

⚠️ **GSIs are *always* eventually consistent — there is no strong-read option.** `ConsistentRead=true` is rejected on a GSI query. Only a **LSI** (Local Secondary Index — an alternate *sort* key sharing the table's partition key) supports strongly consistent reads. This is a hard constraint, not a default you can flip, and it is the single most common DynamoDB modeling mistake.

```js
// DynamoDB table design example: Order management
// Requirement: (1) Get all orders for customer_id X
//              (2) Get all PENDING orders (across all customers)

// Primary key: partition_key = customer_id, sort_key = order_date
// → Requirement 1 is fast: query by customer_id (+ optional sort_key range)

// GSI for requirement 2:
// GSI partition key = status, GSI sort key = order_date
// → "all PENDING orders after 2026-07-01" → query GSI

// WARNING: status = "PENDING" with 1M orders → HOT PARTITION on GSI
// Fix: composite partition key: status#shard (e.g., "PENDING#0" through "PENDING#9")
// App randomly picks a shard on write; queries all 10 shards + merges

Table: Orders
  PK: customer_id (String)
  SK: order_date (String, ISO format for natural sort)

  Attributes: order_id, status, total, items...

GSI "status-index":
  PK: status
  SK: order_date
  → eventual consistency, separate throughput, projects needed columns only
```

**DynamoDB vs Cassandra — the critical difference:**

| | Cassandra | DynamoDB |
|---|---|---|
| **Consistency tuning** | Per-query: ONE, QUORUM, ALL | Per-request: eventual (default) or strong (costs 2× RCU) |
| **Ops overhead** | High: manage ring, repair, compaction, tombstones | Zero: AWS manages everything |
| **Cross-cloud** | Any cloud / on-premise | AWS lock-in |
| **Pricing model** | Hardware cost (self-host) | RCU/WCU (on-demand or provisioned) |
| **Transactions** | No *cross-partition* ACID. Single-partition logged batches are atomic (not isolated); LWT gives compare-and-set — see below | Single-item atomic; multi-item via TransactWrite (up to 100 items) |

**What is a Cassandra LWT?** A **lightweight transaction** (LWT) is Cassandra's compare-and-set, implemented with Paxos consensus across the replicas of a *single partition*. It's how you express "insert only if this row doesn't already exist" — the classic unique-username claim:

```sql
INSERT INTO users (username, user_id)
VALUES ('kapil', 550e8400-e29b-41d4-a716-446655440000)
IF NOT EXISTS;

UPDATE accounts
SET status = 'CLOSED'
WHERE account_id = 123
IF status = 'ACTIVE';
```

The cost is real: a Paxos round is **~4 network round-trips** instead of 1, so an LWT is roughly 4× the latency of a normal write, and LWTs against the same partition contend with each other. Use them for the handful of operations that genuinely need atomicity, never as a general write path.

**Interview framing:** *"Cassandra has no cross-partition transactions. Within a single partition I get atomic batches and Paxos-backed compare-and-set via LWT — enough for uniqueness constraints and state-machine transitions, at ~4× write latency. If I need multi-partition atomicity, Cassandra is the wrong database."* Saying flatly "Cassandra has no transactions" is the answer an interviewer corrects.

---

### Part 3 — MongoDB: Document Model + When It Wins

**What is WiredTiger?** WiredTiger is MongoDB's default storage engine since v3.2. Unlike Cassandra's append-only SSTable model, WiredTiger uses MVCC (Multi-Version Concurrency Control — a system where reads see a consistent snapshot of data at a specific point in time, while writes create new versions rather than overwriting, allowing readers and writers to operate concurrently without blocking each other). This makes MongoDB competitive at document-level concurrent writes without the tombstone accumulation problem.

```js
// MongoDB: flexible schema + aggregation pipeline
// Good fit: product catalog (attributes vary per category)

// Electronics: { _id, name, price, brand, wattage, voltage }
// Clothing:    { _id, name, price, brand, size, material, color }
// Both in same "products" collection — no ALTER TABLE needed

// Aggregation pipeline: revenue per category last 30 days
db.orders.aggregate([
    { $match: { created_at: { $gte: thirtyDaysAgo } } },     // filter
    { $unwind: "$items" },                                     // flatten array
    { $group: {
        _id: "$items.category",
        total_revenue: { $sum: { $multiply: ["$items.price", "$items.qty"] } }
    }},                                                        // aggregate
    { $sort: { total_revenue: -1 } }                           // sort descending
]);

// MongoDB shines here: variable schema per document, rich aggregation, no joins needed
// MongoDB is WRONG here: sustained 100K+ writes/sec on one shard
// (that is ~8.6B/day — note 10M orders/day is only ~116/sec avg, well inside MongoDB's range)
// → at extreme write scale, Cassandra's append-only model outperforms WiredTiger
```

**When MongoDB ACID transactions apply:**

```js
// Multi-document transaction (MongoDB 4.0+)
// Use case: transfer credit between user accounts
const session = client.startSession();
session.startTransaction();
try {
    await users.updateOne({ _id: fromUser }, { $inc: { balance: -amount } }, { session });
    await users.updateOne({ _id: toUser }, { $inc: { balance: +amount } }, { session });
    await session.commitTransaction();
} catch (error) {
    await session.abortTransaction();
}

// Multi-partition ACID like this is unavailable in Cassandra
// (Cassandra can only do single-partition: atomic batches + LWT compare-and-set)
// BUT: multi-document transactions in MongoDB carry a performance cost
// (locks, 2-phase commit coordination across replica set members)
// Use only when truly needed; single-document atomicity is free
```

---

## 🏢 Real World — Where Companies Use This

- **Netflix** (Cassandra): Stores all viewing history — "all shows Alice watched, sorted by date" maps perfectly to partition key = `user_id`, clustering key = `watch_date`. 1B+ events/day. Eventual consistency is acceptable (viewing history doesn't need to be instantly consistent across all Netflix regions). Manages their own Cassandra clusters — they have the ops capacity.

- **Amazon Retail** (DynamoDB): Product inventory system. Partition key = `product_id`, sort key = `warehouse_id`. Zero ops team required; DynamoDB auto-scales for Prime Day traffic spikes. AWS-native — fits naturally into the existing Lambda + API Gateway stack. Write volume is massive but DynamoDB's on-demand mode absorbs it.

- **eBay** (MongoDB): Merchandising and catalog metadata services, where item attributes vary wildly by category (electronics have wattage/voltage; clothing has size/material/color). Flexible document schema means no migration when a new category is onboarded, and the aggregation pipeline powers merchant-facing analytics. ⚠️ **Note:** an earlier version of this file attributed this pattern to **Shopify** — that was wrong. Shopify is a well-documented Rails + **MySQL** shop, horizontally sharded into isolated "pods." Same correction is recorded in `06-databases-types-and-selection.md`. Say *"a common pattern is…"* rather than asserting a company's internal stack.

- **Discord** (Cassandra → ScyllaDB): Chat message storage. Partition key = `channel_id`, clustering key = `message_timestamp`. "Get last 50 messages in this channel" = single-partition query on the most recent clustering column values. 100B+ messages stored. Migrated from Cassandra to ScyllaDB (drop-in compatible, written in C++ for lower GC pause) for latency improvements.

- **Uber** (Cassandra): Driver location history. Partition key = `driver_id`, clustering key = `timestamp`. Writes at 100K+ events/sec across all drivers; Cassandra's tunable consistency lets Uber pick W=1 for location writes (eventual — a slightly stale location is fine) vs W=QUORUM for trip state updates (strong — trip status must be consistent).

---

## 🧭 When to Use vs When NOT to Use

### Cassandra

| Use Cassandra when | Do NOT use Cassandra when |
|---|---|
| Known partition key for every query (user_id, device_id, session_id) | You need cross-partition range scans (analytics over all users) |
| Massive write throughput (100K+ writes/sec) | Your team is < 10 engineers (ops overhead is real) |
| Multi-region active-active writes (no single primary) | You need ACID across multiple partitions (LWT only covers one) |
| You can tolerate eventual consistency (or tune to quorum) | You need flexible queries by arbitrary fields |
| Time-series per-entity (IoT, activity logs, events) | Your delete rate is high (tombstone accumulation) |

### DynamoDB

| Use DynamoDB when | Do NOT use DynamoDB when |
|---|---|
| You're already deep in AWS (Lambda, API Gateway, ECS) | You need to run on-premise or multi-cloud |
| Your team has no DB ops capacity | Query patterns are unknown or frequently changing |
| Access patterns are well-defined and stable | You need rich aggregation (aggregation pipeline equivalent) |
| Variable traffic (DynamoDB on-demand auto-scales) | Your budget is sensitive to per-read/write pricing at extreme scale |
| Single-item atomicity is sufficient | You need multi-row joins across entities |

### MongoDB

| Use MongoDB when | Do NOT use MongoDB when |
|---|---|
| Schema is genuinely flexible (different fields per document type) | You need 100K+ writes/sec sustained on a single shard (Cassandra beats it here) |
| Rich ad-hoc queries matter (filter by any field combination) | You need multi-region active-active writes (MongoDB is primary-based) |
| Multi-document ACID transactions are required occasionally | You're locked into AWS and want managed zero-ops |
| You want a balance: NoSQL flexibility + some RDBMS guarantees | You need to scale *reads* off secondaries without giving up consistency |

> ⚠️ **Do not say "avoid MongoDB if you need strong consistency" — that's backwards.** MongoDB reads from the primary by default, so it is strongly consistent out of the box. The actual tension: the way you scale reads is to send them to secondaries (`readPreference: secondary`), and *that* is what costs you consistency. The ceiling is read-scaling-vs-consistency, not consistency itself.

**The common mistake:** Picking MongoDB "because the schema might change." If the schema stabilizes after a month (it usually does), you gave up write performance for a flexibility you don't use.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **Cassandra: You gain** | Highest raw write throughput of any NoSQL; tunable consistency (choose correctness vs speed per query); multi-region active-active (no primary node); no single point of failure |
| **Cassandra: You lose** | High operational complexity (ring repair, compaction tuning, tombstone management); all queries must include partition key; cross-partition analytics require a separate system |
| **Cassandra: Failure mode** | Choosing Cassandra for a system where "find all orders in status=PENDING today" is the core query — that's a cross-partition scan, and Cassandra will destroy latency for it |
| **DynamoDB: You gain** | Zero operational overhead; automatic scaling; AWS-native integrations (Streams, Lambda, DAX); single-item transactions are free |
| **DynamoDB: You lose** | AWS lock-in; per-request cost model surprises at scale; rigid data modeling (everything flows through the partition+sort key); GSI eventual consistency catches people |
| **DynamoDB: Failure mode** | Hot partition: all writes go to partition key="PENDING" (status field). Fix: composite partition key "PENDING#shard_id" + randomize on write + query fan-out |
| **MongoDB: You gain** | Flexible schema; rich aggregation; multi-document ACID; fastest iteration for early-stage products; Atlas managed service |
| **MongoDB: You lose** | One primary **per shard** — a single unsharded replica set caps at that primary's write throughput, and scaling writes means sharding (config servers, mongos routers, balancer, and a shard key you can't easily change); $lookup (join) is expensive and not distributed; schema flexibility becomes a liability at large team sizes |
| **MongoDB: Failure mode** | A poorly chosen shard key (low cardinality, or monotonically increasing like a timestamp) funnels all writes into one shard — you pay for N shards and get 1 shard's throughput. Cassandra's hash-partitioned ring spreads this by default |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "Why would you pick Cassandra over DynamoDB for a chat system?"
> Two reasons: (1) Multi-region active-active writes. Cassandra has no single primary — any node accepts writes. Discord serves users globally; a user in Singapore writing to a channel shouldn't need to hit a primary in US-East. (2) Tunable consistency. Message delivery can be eventual (W=1, R=1 — fastest path), but reading the last unread message marker can be QUORUM. DynamoDB's consistency model is coarser — eventual vs. strongly consistent per read, not per write pattern. The cost: Discord runs their own Cassandra/ScyllaDB infrastructure.

### Q: "When would you pick DynamoDB over Cassandra?"
> When operational overhead is a hard constraint. Cassandra requires deep expertise: managing ring topology, running nodetool repair regularly, tuning compaction strategies, monitoring tombstone counts. A team of 5 building a startup can't afford that. DynamoDB is zero-ops — AWS handles replication, failover, scaling, and backups. The trade-off is AWS lock-in and a constrained query model: you must design your data around known access patterns upfront.

### Q: "Design the primary key for a Cassandra table that stores IoT sensor readings."
> Requirements: "Get all readings from sensor X in the last hour" and "Get the most recent reading for sensor X." Primary key: `(sensor_id, reading_timestamp)` — partition key = `sensor_id`, clustering key = `reading_timestamp DESC`. All readings for one sensor are in one partition, sorted newest-first. "Last hour" = single-partition range scan on `reading_timestamp`. "Most recent" = `LIMIT 1` on the same partition. The anti-pattern would be partition key = `date` — all readings on July 15 hit one partition (hot partition) and "all readings for sensor X" requires a cross-partition scatter-gather.

### Q: "Why is MongoDB a better fit than Cassandra for a product catalog?"
> Product catalogs have genuinely variable schemas — an electronics product has wattage and voltage; a clothing product has size, material, and color. MongoDB's flexible document model stores these naturally in the same collection without forcing a sparse-column design. More importantly, catalog queries are ad-hoc: "find all blue cotton T-shirts under ₹500 in stock" — this query filters on color, material, price, and inventory simultaneously. Cassandra would require either a pre-defined query pattern per index or a full-table scan. MongoDB's aggregation pipeline handles this directly.

---

### Tier 2 — Cross/Probe Questions

### Q: "You said Cassandra has tunable consistency. If I set W=QUORUM and R=QUORUM with RF=3, am I guaranteed to always see the latest write?"
> Yes for staleness — no for linearizability. Quorum = floor(3/2) + 1 = 2. If the write *returned success* at W=2, then 2 replicas durably hold it. A read at R=2 touches 2 of the 3 replicas, so by pigeonhole the overlap is at least `W + R − RF = 2 + 2 − 3 = 1` node. Every possible pair — {A,B}, {A,C}, {B,C} — contains a writer, and Cassandra resolves by highest cell timestamp. So you always see the latest **successful** write.
>
> **Do not reach for hinted handoff as the caveat** — it's a common wrong answer. Hints do *not* count toward the consistency level (the sole exception is `CL=ANY`). If enough replicas were down that the quorum couldn't be met, the write **fails** with `UnavailableException`; it never silently degrades into hints. The three caveats that are actually real:
> 1. **Last-write-wins on wall-clock timestamps.** Two coordinators with skewed clocks writing the same cell — the one with the higher timestamp wins, even if it happened *first* in real time. Quorum does not fix this; only LWT does.
> 2. **No isolation.** A write acked by 1 replica and then returning an error to the client is still sitting on that replica. Read repair can later propagate it. You can read the result of a write that officially *failed*.
> 3. **Not linearizable.** Read-modify-write ("claim this username if free") needs `SERIAL`/lightweight transactions, not QUORUM. QUORUM gives you freshness, not atomic compare-and-set.

### Q: "DynamoDB GSI has eventual consistency. In a payment system, you have a GSI on payment_status. A payment is confirmed — the GSI update is lagged 200ms. What's the risk and how do you handle it?"
> The risk: within 200ms of confirmation, a query on the GSI for `status=PENDING` might still return this payment — a duplicate confirmation attempt could process it again. Three mitigations: (1) Idempotency key: the primary table item has an `idempotency_key` with UNIQUE constraint — duplicate confirms hit a ConditionalCheckFailedException. (2) Never rely on GSI for financial reads — always read payment status from the primary table (strongly consistent by partition key + sort key). GSI is for analytics/dashboard queries only. (3) DynamoDB TransactWrite for status transitions: `condition_expression = "attribute_exists(payment_id) AND status = :pending"` — the atomic condition prevents double-confirm.

### Q: "MongoDB has multi-document ACID transactions. So why not always use MongoDB instead of PostgreSQL for financial systems?"
> MongoDB's multi-document transactions work, but they carry a performance cost: they use a two-phase commit across replica set members, holding locks across documents for the transaction duration. PostgreSQL's transaction isolation is native to its row-level locking model — it's been refined for 30 years. At scale, PostgreSQL transactions at 10K/sec are mature and predictable; MongoDB transactions at 10K/sec add coordination overhead and lock contention that you don't get with a purpose-built RDBMS. Also: PostgreSQL's query planner is vastly more sophisticated — complex multi-table aggregations, CTEs, window functions. MongoDB's `$lookup` (join) is a per-document nested loop, not a hash join. For finance, PostgreSQL's maturity and optimizer wins. MongoDB transactions are the right answer when you already have MongoDB for document flexibility and need occasional multi-document atomicity — not as a general PostgreSQL replacement.

### Q: "What is a tombstone in Cassandra and when does it cause a production incident?"
> A tombstone is the marker Cassandra writes when you delete a row. Because SSTables are immutable (append-only), Cassandra can't remove a row in place — it writes a tombstone with a timestamp. During reads, Cassandra merges multiple SSTable files and filters out tombstoned entries. During compaction (the background process that rewrites and merges SSTables), tombstones older than `gc_grace_seconds` (default: 10 days) are actually removed. The incident pattern: a table with a high delete-then-reinsert access pattern (e.g., a "current state" table that deletes and rewrites a row on every state change) accumulates thousands of tombstones per partition. A partition with 100K+ tombstones causes Cassandra to scan and discard each one on every read — latency spikes from 5ms to 5 seconds. Fix: redesign to use TTL instead of explicit DELETE (TTL generates a more efficient tombstone type), or redesign the table to append new state rather than overwrite.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"Cassandra for write-scale + per-entity time-series with known partition keys; DynamoDB for zero-ops AWS-native with known access patterns; MongoDB for flexible schema and rich ad-hoc queries — the decision axis is query model first, operational overhead second, consistency model third."*

---

## 🔗 Related Concepts

- **`06-databases-types-and-selection.md`** — SQL vs NoSQL framework; use that file first for the initial SQL/NoSQL decision. This file is the second step after you've decided NoSQL.
- **`34-cap-theorem-consistency-models.md`** — CAP theorem deep dive; the consistency model section directly explains why Cassandra is AP and when that matters.
- **`38-sharding-strategy.md`** — Sharding internals; Cassandra's consistent hashing ring is a specific sharding implementation covered there.
- **`45-hot-partition-problem.md`** — The hot partition problem is a first-order concern in both Cassandra (hot partition key) and DynamoDB (hot partition key). Read after this file.
- **`54-redis-internals.md`** — Redis is the fourth NoSQL tier (key-value cache). Read for the cache layer placement that sits above all three of these.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Cassandra: The Definitive Guide" Ch. 4-6** — Carpenter & Hewitt (O'Reilly) | Ring topology, compaction strategies, and tunable consistency math with worked replication scenarios | ~40 min |
| **"DynamoDB: The Definitive Guide"** — Alex DeBrie (dynamodbbook.com) | Single-table design patterns, GSI projection strategies, hot partition detection | ~30 min read |
| **"Building with Patterns"** — MongoDB Docs | The 12 official MongoDB schema design patterns (bucket, outlier, computed, pre-allocation) | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | **Correctness pass — 9 fixes.** (1) **Hinted-handoff caveat was wrong** and had been propagated into `06-databases-types-and-selection.md`: hints do not count toward consistency level (except `CL=ANY`), so a successful `W=QUORUM` write always overlaps a `R=QUORUM` read by pigeonhole. Replaced with the three real caveats — LWW clock skew, no isolation (failed writes readable via read repair), and non-linearizability. Fixed in both files. (2) `10M orders/day = 100K writes/sec` was off ~1000× (it's ~116/sec) *and* inverted the conclusion. (3) **Shopify attributed to MongoDB** — Shopify is Rails + sharded MySQL; same claim was already corrected in file 06, so the repo contradicted itself. (4) GSIs described as eventually consistent "by default" — they are *always* eventual; only LSIs support `ConsistentRead`. (5) "eventual consistency by default" applied to MongoDB, and "avoid MongoDB if you need strong consistency" — both backwards; MongoDB reads the primary by default. (6) "single primary → vertical write scale limit" ignored sharding; ceiling is per-shard and shard-key skew. (7) **Added LWT/Paxos section** — the deep-dive never mentioned it while `Database-Types-Complete-Guide.md` and `52-numbers-to-know` both did. (8) Commit log labelled a durability guarantee — default `commitlog_sync: periodic` acks before fsync (10s window). (9) Consistency axis split into 3a tunability / 3b default posture, which were conflated. Also added `sql`/`js` language tags to 5 untagged fences (AGENTS.md Rule 1). |
| Jul 19, 2026 | **Stale-fact fix.** DynamoDB `TransactWrite` limit corrected from 25 → **100 items** (AWS raised the limit in 2022; 25 was the original cap). Aligned with `../../Database-Types-Complete-Guide.md`. |
| Jul 15, 2026 | **File created.** NoSQL selection deep dive: Cassandra vs MongoDB vs DynamoDB. Covers 3-axis decision framework (query model / operational overhead / consistency tuning), Cassandra ring topology + SSTable write path + quorum formula + tombstone explanation, DynamoDB partition+sort key model + GSI + hot partition mitigation, MongoDB flexible schema + WiredTiger MVCC + aggregation pipeline. Triggered by gap analysis: existing `06-databases-types-and-selection.md` covers SQL vs NoSQL well but does not distinguish within NoSQL at the depth needed for Staff+ system design rounds. |
