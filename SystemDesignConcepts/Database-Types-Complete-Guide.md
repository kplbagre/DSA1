# 🎯 Database Types — Complete Selection & Scaling Guide

> **Why you're reading this:** After one pass, you should be able to hear any interview scenario ("design a chat app", "store IoT sensor data", "build a product search") and immediately name the right database, defend the choice with production-grade numbers, and explain exactly when and why you'd migrate away from it.
>
> Every number below is a **production operating figure**, not a marketing maximum. Where a vendor advertises a theoretical ceiling, this note separates the **sweet spot** (where the DB runs smoothly with normal ops staffing) from the **hard limit** (where it technically still works but the pain is real).

---

## 📖 Terminology

| Term | Plain-English meaning |
| --- | --- |
| **WPS / RPS / TPS** | Writes / Requests / Transactions Per Second — the throughput units used throughout this note. |
| **Node** | A single machine (or single managed instance) running one copy of the database process. |
| **Working set** | The subset of data that queries actually touch frequently — the "hot" data. If it fits in RAM, reads are fast; if it spills to disk, they're 10–100× slower. |
| **Sharding** | Splitting one logical dataset horizontally across many nodes, each holding a slice — done when one node can no longer hold the data or serve the load. |
| **Replication** | Keeping copies of the same data on multiple nodes — for availability and read scaling, NOT for splitting load (that's sharding). |
| **OLTP** | Online Transaction Processing — many small, fast reads/writes (e.g., "fetch user 42", "insert an order"). The everyday app workload. |
| **OLAP** | Online Analytical Processing — few huge scans/aggregations (e.g., "sum revenue by region for the last 3 years"). The reporting/analytics workload. |
| **MVCC** | Multi-Version Concurrency Control — the technique (used by Postgres, MongoDB, MySQL-InnoDB) where writers create new versions instead of overwriting, so readers never block writers. |
| **LSM tree** | Log-Structured Merge tree — the write-optimized storage design (Cassandra, RocksDB, ScyllaDB) that appends writes to memory then flushes sorted immutable files to disk. |
| **B-tree** | The read-optimized, update-in-place index structure used by relational DBs — great for range scans and point lookups, more expensive on writes. |
| **Compaction** | The LSM background job that merges immutable files, discarding overwritten/deleted rows. Necessary but competes for disk I/O. |
| **Quorum** | A majority of replicas that must acknowledge a read or write for it to count as "consistent." |

---

## 🧠 Mental Model — The Whole Decision Reduces to 4 Questions

You never memorize a table of DBs. You answer four questions about the **workload**, and the DB falls out:

1. **What's the access pattern?** — Point lookups by key? Range scans? Arbitrary multi-field filters? Full-text? Graph traversal? Analytics over the whole dataset?
2. **What's the read/write ratio and volume?** — Read-heavy vs write-heavy, and how many ops/sec at peak.
3. **What consistency do you need?** — Must a read *always* see the latest write (strong), or is "a second stale" acceptable (eventual)?
4. **How big does it get, and how fast?** — Total data size and growth rate decide single-node vs sharded vs distributed-native.

> **The single most useful heuristic:** *Start with PostgreSQL. Move off it only when a specific number forces you to.* Most systems with under ~100M users and under ~10k writes/sec never need anything else for their primary store. Reaching for Cassandra/Mongo/Dynamo "because it's web-scale" before the math demands it is the most common junior mistake.

---

### 🎨 Visual — The Database Decision Tree

```
                        ┌─────────────────────────────┐
                        │   What is the ACCESS PATTERN? │
                        └───────────────┬───────────────┘
                                        │
   ┌────────────┬────────────┬──────────┼───────────┬─────────────┬──────────────┐
   │            │            │          │           │             │              │
 Relations,  Get/set     Huge write   Flexible   Full-text /   Graph        Analytics
 joins,      by key,     volume,      JSON docs, faceted       traversal    over
 ACID        sub-ms      per-entity   varied     search        (friends-of- billions
   │         cache         time-series schema      │            friends)     of rows
   │            │            │          │           │             │              │
   ▼            ▼            ▼          ▼           ▼             ▼              ▼
POSTGRES/    REDIS       CASSANDRA/  MONGODB    ELASTIC-       NEO4J        CLICKHOUSE/
MYSQL        (cache,     SCYLLA      (or        SEARCH         (or          BIGQUERY/
(default     not the     (or         DynamoDB   (never the     Neptune)     SNOWFLAKE/
 OLTP        source      DynamoDB    if AWS-     source of                  REDSHIFT
 store)      of truth)   if AWS)     native)     truth)                     (OLAP only)

  Need horizontal SQL with global strong consistency across regions?
  ──► SPANNER / COCKROACHDB / VITESS  (NewSQL)

KEY INVARIANT:
   The access pattern picks the family; the VOLUME picks single-node vs
   sharded vs distributed-native. Never let "it might get big someday"
   override "what does the query actually look like today."
```

---

## 🔹 1. Relational (PostgreSQL / MySQL) — The Default

**What it is:** A relational database stores data in tables of rows and columns with a fixed schema, enforces relationships between tables (foreign keys), and guarantees **ACID** (Atomicity, Consistency, Isolation, Durability — the property set that makes a multi-step transaction behave as one all-or-nothing, correct, isolated, permanent operation). PostgreSQL and MySQL/InnoDB both use B-tree indexes and MVCC.

**When it's the right answer:** Anything with relationships and transactional correctness — user accounts, orders, payments, inventory, bookings. If you can't articulate a *specific* reason to leave relational, stay relational.

| Dimension | Single-node smooth (sweet spot) | Hard / practical ceiling | What breaks first at the high end |
| --- | --- | --- | --- |
| **Storage** | Up to ~2–5 TB comfortably; ~10–20 TB with a dedicated DBA | RDS PostgreSQL **64 TiB**; Aurora PostgreSQL **128 TiB**; single Postgres table max **32 TB** (8 KB page) | `VACUUM`/autovacuum (the background job reclaiming dead MVCC row versions) can't keep pace → table bloat 2–5×, planner stats go stale, p99 spikes |
| **Write throughput** | 5k–15k WPS (simple indexed inserts/updates) | ~20k+ WPS with tuning, SSD, batching | WAL (Write-Ahead Log — the durability journal written before data pages) fsync stalls; index write amplification |
| **Read throughput** | 20k–50k TPS for simple indexed reads (buffer-cache hits) | Complex JOINs/aggregations: 1k–10k TPS | Buffer cache miss → random disk I/O; heavy joins saturate CPU |
| **Connections** | 200–500 raw connections | Each connection ≈ a dedicated OS process using 5–10 MB RAM | 500 conns ≈ 2.5–5 GB overhead before any query → use PgBouncer (a pooler multiplexing thousands of app connections onto a few real ones) |

**When to shard (and it's a last resort here):**
- Sustained writes **> 15–20k WPS** on a single hot table that batching and a beefier instance can't absorb.
- Active dataset genuinely exceeds single-instance storage or the working set no longer fits in RAM.
- **Order of moves before sharding:** (1) add indexes / fix queries, (2) vertical scale the box, (3) add read replicas for read load, (4) PgBouncer for connections, (5) partition large tables within one instance, (6) *then* shard (app-level or Vitess for MySQL, Citus for Postgres).

**What pushes you OFF relational:**
- **Writes stay above ~20k WPS** and sharding adds more complexity than it's worth → **Cassandra** (append-only, no single primary) or **DynamoDB**.
- **Multi-region active-active writes** (users on 3 continents all writing) → the single-primary model forces a cross-region round-trip (50–150 ms) per write → **Cassandra / DynamoDB Global Tables / Spanner / CockroachDB**.
- **Truly schemaless, per-record-varying data** → **MongoDB**.
- **Full-text relevance search at scale** (> ~50–100M docs, ranked results, facets) → **Elasticsearch** (Postgres FTS is fine below that).
- **Deep graph traversal** (> 3–4 hops) → **Neo4j** (recursive CTEs explode exponentially).
- **Analytics scanning billions of rows** → a **columnar OLAP** store; row-oriented Postgres reads whole rows to sum one column.

> **Lesson learned the hard way (Jul 2026):** "It might scale someday" is not a reason to leave Postgres. A single Aurora instance at 128 TiB and 15k WPS covers the *vast* majority of products that will ever exist. Do the arithmetic (DAU × writes-per-user ÷ 86,400) before proposing a distributed store — interviewers reward the candidate who resists premature sharding.

---

## 🔹 2. In-Memory Key-Value (Redis) — The Speed Layer, Not the Source of Truth

**What it is:** Redis holds data entirely in RAM as keyed values (strings, hashes, sorted sets, etc.), returning results in **sub-1 ms**. It is used as a **cache** (a fast copy of hot data in front of the real database), a session store, a rate limiter, and a leaderboard. The database remains the source of truth; Redis is the fast lane.

| Dimension | Single-node smooth | Hard / practical ceiling | Why the limit exists |
| --- | --- | --- | --- |
| **Memory** | 10–25 GB | ~50 GB "fork-safe"; up to ~100 GB with persistence disabled | `BGSAVE` (background snapshot to disk) *forks* the process; copy-on-write can transiently need up to **2× the dataset RAM**. Keep the dataset under ~50% of machine RAM or the fork fails → no persistence |
| **Throughput** | 50k–80k ops/sec | 100k–200k ops/sec; 300k–500k with Redis 6+ threaded I/O | **Command execution is single-threaded** — one CPU core. Redis 6 parallelizes network I/O only; one slow command (`KEYS *`, a big `LRANGE`, a heavy Lua script) freezes *everything* |
| **Latency** | Sub-1 ms same-AZ | — | It's RAM; the network hop dominates |

**When to shard (Redis Cluster):**
- Dataset **> ~50 GB** *and* you need persistence (fork risk).
- Sustained ops **> ~100k/sec** (single-core wall — more RAM won't help, you need more cores = more nodes).
- A single node dying would take the service down → Cluster gives automatic failover.

**What pushes you OFF / around Redis:**
- **Data cannot be lost** (ledger, audit) → Redis is not a primary store; even AOF-every-second can lose up to 1 s on crash. Use Postgres/Cassandra as truth, Redis only as cache.
- **Cross-slot atomic multi-key transactions in Cluster** → `MULTI/EXEC` can't span hash slots; force co-location with hash tags `{tag}:key` or redesign.
- **Arbitrary secondary-field queries** → Redis has no real secondary indexes; use Postgres/Elasticsearch.
- **Never run `KEYS` in production** — use `SCAN` (batched, non-blocking).

> ⚠️ **The two limits that trip people up:** memory ("can it BGSAVE-fork safely?" ≈ 50 GB) and throughput ("one core saturates" ≈ 100k ops/sec). When scaling Redis, name *which one* you're solving.

---

## 🔹 3. Wide-Column (Cassandra / ScyllaDB) — Write-Optimized, Horizontally Native

**What it is:** A wide-column store keeps rows identified by a **partition key** (which node holds the row) plus optional **clustering columns** (sort order within the partition). It's built on an **LSM tree**: every write is an append to an in-memory table that flushes to immutable **SSTable** files on disk — no in-place update, no per-write random I/O. A delete is a **tombstone** (a timestamped "this key is gone" marker). This makes it write-optimized and read-costlier. There is no single primary — *any* node accepts writes (masterless, via consistent hashing on the partition key). ScyllaDB is a C++ rewrite of Cassandra with the same data model but higher per-node throughput.

| Dimension | Per-node smooth | Practical ceiling | Why |
| --- | --- | --- | --- |
| **Write throughput** | 10k–20k WPS | 10k–50k WPS (Scylla higher) | Sequential LSM appends; ceiling = disk bandwidth + compaction I/O (30–50% of disk at high write rates) |
| **Read throughput** | 5k–10k reads/sec | 5k–20k reads/sec | May merge MemTable + several SSTables; quorum reads add a cross-node hop |
| **Node storage** | **1–3 TB** | ~5–10 TB physically | `nodetool repair` (anti-entropy comparing replicas via Merkle trees) takes hours at 1 TB, **10–20+ hours at 5 TB** — that's your single-copy exposure window on failure |
| **Cluster minimum** | 3 nodes (RF=3) | — | Replication Factor 3 needs 3 nodes for durability |

**Sharding is automatic** — that's the entire point. You don't shard Cassandra; you *add nodes* and the ring rebalances via consistent hashing. Scaling is a strength, not a migration.

**When it's the right answer:** Write rate > 20k WPS with a clean per-entity partition key; per-entity time-series ("all events for `device_id=X` ordered by time"); multi-region active-active writes.

**What pushes you OFF Cassandra:**
- **Cross-partition range queries** ("all PENDING orders this week across all customers") → scatter-gather across the whole ring → use **Elasticsearch** or a **warehouse**.
- **Multi-row ACID transactions** → not supported (only single-partition lightweight transactions via Paxos) → **Postgres / CockroachDB**.
- **High delete rate** → tombstone buildup degrades reads until compaction clears them; a 30–50%-delete workload suffers → use **TTL** (auto-expiry) or leave Cassandra.
- **Ad-hoc queries on non-partition fields** → local secondary indexes are slow (every node queried) → **Elasticsearch**.

> **The Cassandra golden rule:** you design the *table around the query*, not the query around the table. If you don't know your access patterns up front, Cassandra will hurt you.

---

### 🎨 Visual — LSM Write Path (why Cassandra absorbs writes so fast)

```
WRITE  ──►  Commit Log (WAL, sequential append, durability)
              │
              ▼
          MemTable (sorted, in RAM)   ── serves recent reads
              │  (flush when full)
              ▼
          SSTable_1  SSTable_2  SSTable_3   (immutable, on disk)
              └──────────┬──────────┘
                    Compaction  ── merges files, drops tombstoned/overwritten rows

READ ──► check MemTable + Bloom-filter-pruned SSTables, merge newest-wins

KEY INVARIANT:
   Writes never seek to a random disk location — they only append.
   That is the whole reason a Cassandra node eats 10k–50k WPS.
   The cost is paid on READS (multi-file merge) and on COMPACTION.
```

---

## 🔹 4. Document (MongoDB) — Flexible Schema

**What it is:** MongoDB stores **BSON** (Binary JSON) documents in collections with **no fixed schema** — two documents in one collection can have entirely different fields. Its WiredTiger engine uses MVCC and supports true in-place updates and multi-document ACID transactions (4.0+). Writes go to a single **primary** per replica set; secondaries replicate asynchronously.

| Dimension | Per-node smooth | Ceiling | Why |
| --- | --- | --- | --- |
| **Write throughput** | 5k–10k WPS | 5k–20k WPS (primary only) | Single primary is the bottleneck; each extra index adds a write (5 indexes = 6 write ops per insert) |
| **Read throughput** | 10k–50k reads/sec | Working-set-dependent | **WiredTiger cache ≈ 50% of RAM.** If the working set fits, reads are 1–5 ms; if it spills to disk, 10–100× slower |
| **Document size** | < 100 KB ideal | **16 MB hard API limit** | Large blobs → S3 + URL reference |
| **Dataset before sharding** | Working set ≤ RAM | Shard when working set > 2–5× RAM | Cache-miss cliff is the real limit |

**When to shard:** working set no longer fits in the WiredTiger cache and read latency degrades; pick a shard key with high cardinality and even distribution (a bad shard key = hot shard = you gained nothing).

**When it's the right answer:** genuinely variable schema (product catalog where electronics/clothing/food need different fields), arbitrary compound filters via the aggregation pipeline, rapid iteration where `ALTER TABLE` migrations would slow you down.

**What pushes you OFF MongoDB:**
- **Sustained writes > 20k WPS** → single primary bottleneck → **Cassandra** (multi-master).
- **Frequent cross-collection joins** → `$lookup` is a nested-loop join, no hash-join optimizer → degrades on big collections → **Postgres**.
- **Multi-region active-active writes** → one primary → cross-region latency → **Cassandra / DynamoDB Global Tables**.
- **Full-text relevance ranking + facets** → **Elasticsearch**.
- **Cluster-wide analytics** → aggregation pipeline isn't an OLAP engine → **BigQuery / Snowflake**.

---

## 🔹 5. Managed Key-Value / Document (DynamoDB) — Zero-Ops, AWS-Native

**What it is:** AWS's fully managed NoSQL store. AWS runs all infrastructure, replicates across 3 AZs, auto-scales, and never exposes a server. You design around a **partition key** (which physical partition) and optional **sort key** (range queries within a partition). Billing is per-request (Read/Write Capacity Units), not per-hour hardware.

| Dimension | Value | Note |
| --- | --- | --- |
| **Item size** | **400 KB hard limit** | Bigger → S3 reference |
| **Per-partition write** | **1000 WCU/sec** (1 WCU ≈ 1 KB) | Exceed on one key → hot-partition `ThrottlingException` |
| **Per-partition read** | **3000 RCU/sec** (1 RCU ≈ 4 KB strong / 8 KB eventual) | Same hot-key risk; DAX (in-memory accelerator) for hot reads (< 1 ms) |
| **Write latency** | 1–5 ms p50 | p99 10–20 ms without DAX |
| **GSI** (Global Secondary Index) | **Eventually consistent, 1–2 s lag** | Never use a GSI for financial/read-after-write correctness |

**Scaling is automatic** — Dynamo shards internally; you never manage nodes. Your job is a good partition key (high cardinality, even distribution) and controlling GSI write amplification (5 GSIs = 6× write cost).

**When it's right:** AWS-native stack, no DBA capacity, spiky/unpredictable traffic (on-demand mode absorbs Prime-Day spikes), access patterns known and stable up front.

**What pushes you OFF DynamoDB:**
- **Multi-cloud / on-prem** → AWS-only → **Cassandra / MongoDB**.
- **Access patterns unknown or churning** → key design is rigid → **MongoDB** (flexible schema).
- **Complex aggregations/analytics** → none native → Streams → Lambda → S3 → Athena, or keep Postgres.
- **Extreme write cost at 1M+ WPS** → self-managed Cassandra is materially cheaper per write at that scale.
- **Transactions over > 100 items** → `TransactWrite` caps at 100 → **Postgres / CockroachDB**.

---

## 🔹 6. Search Engine (Elasticsearch / OpenSearch) — Never the Source of Truth

**What it is:** A distributed search/analytics engine on Apache Lucene. Its primary structure is an **inverted index** (maps each word → the list of documents containing it — the reverse of a row table), which makes text search and faceted filtering fast. Trade-offs: writes are expensive (index rebuild), documents are **near-real-time** (1 s default refresh delay before a write is searchable), consistency is weak, and it is **not a source of truth** — always sync *to* Elasticsearch *from* a primary DB via CDC (Change Data Capture — streaming every DB write as an event, e.g. Debezium).

| Dimension | Value | Why |
| --- | --- | --- |
| **JVM heap** | **≤ ~32 GB per node** | Below 32 GB the JVM uses compressed object pointers (4 bytes); above, they balloon to 8 bytes and the *same* data needs ~50% more heap. Use 26–30 GB, leave the rest for OS page cache |
| **Shard size** | **10–50 GB per primary shard** | Smaller = per-shard JVM overhead; larger = hours to rebalance |
| **Shards per node** | < ~20 per GB of heap (≈ 600 on a 30 GB heap) | Too many → GC pressure, OOM |
| **Indexing** | 5k–50k docs/sec (1 KB docs) | Varies with mapping complexity |
| **Search latency** | 1–20 ms simple; 100 ms–seconds for heavy aggregations | Inverted index is fast for text, slow for big analytics |

**When it's right:** user-facing full-text search with relevance ranking, faceted filtering across many fields at once, log/observability analytics (the "E" in ELK).

**What pushes you OFF / around Elasticsearch:**
- **Never** use it as the primary store (1 s lag, no ACID).
- **Write-heavy, low-search** workloads → indexing overhead wasted → Cassandra for storage.
- **Simple get-by-ID** → slower than Postgres B-tree or Redis → keep those.
- **Strong read-after-write** → the 1 s refresh breaks it → Postgres FTS.

> ⚠️ **Shard-count trap:** teams over-shard ("more parallelism!") but each shard is a JVM object and searching 500 shards = 500 Lucene queries merged in RAM. Start with `ceil(dataset_GB / 30)` primary shards.

---

## 🔹 7. Time-Series (TimescaleDB / InfluxDB) — Append-Heavy, Time-Ordered

**What it is:** Databases specialized for measurements stamped with a time and written in append order (metrics, IoT sensors, financial ticks). **TimescaleDB** is a Postgres extension that auto-partitions tables into time-based "chunks" — same SQL, write-optimized for append. **InfluxDB** is a purpose-built TSDB with its own query language and aggressive compression.

| Dimension | Smooth | Note |
| --- | --- | --- |
| **Write ingest** | 100k–1M+ points/sec (batched) | Append + columnar-ish compression; time-ordered writes avoid random I/O |
| **Compression** | 90%+ typical | Adjacent time points compress extremely well |
| **Retention** | Auto-downsample + drop old chunks | Cheap to expire old high-resolution data |

**When it's right:** metrics/monitoring, IoT telemetry, anything queried as "aggregate over a time window." TimescaleDB when you want SQL + relational joins alongside time-series; InfluxDB for pure metric workloads.

**What pushes you off:** need for arbitrary non-time queries or heavy relational joins (→ Postgres proper); extreme cardinality of tag combinations (classic InfluxDB pain — millions of unique series blow up memory) → Cassandra time-series model or a warehouse.

---

## 🔹 8. Graph (Neo4j / Neptune) — Relationships Are the Data

**What it is:** A graph database stores **nodes** and **edges** (relationships) as first-class citizens, so traversing connections ("friends of friends of friends", "shortest path", fraud rings) is a pointer-hop, not a JOIN. In relational DBs each hop is another self-join whose cost multiplies; in a graph DB a hop is O(1) via stored adjacency.

| Dimension | Smooth | Note |
| --- | --- | --- |
| **Traversal depth** | Deep (5+ hops) stays fast | The whole reason it exists |
| **Write/storage** | Historically single-primary write (Neo4j causal cluster: core write nodes + read replicas) | Not built for massive write throughput |
| **Scale model** | Vertical + read replicas; sharding graphs is genuinely hard | Edges cross would-be shard boundaries |

**When it's right:** social graphs, recommendation engines, fraud/AML ring detection, network/dependency topology, knowledge graphs — anytime the *relationships* are what you query.

**What pushes you off:** shallow queries (1–2 hops) that Postgres handles fine (don't add a graph DB for a friends list); write volumes or dataset sizes that exceed single-primary + replicas (graph sharding is a hard, often deal-breaking problem).

---

## 🔹 9. NewSQL / Distributed SQL (Google Spanner / CockroachDB / Vitess) — SQL That Scales Horizontally

**What it is:** Databases that keep the relational model, SQL, and ACID transactions **but shard automatically across many nodes** using a consensus protocol (Paxos in Spanner, Raft in CockroachDB) to stay strongly consistent. Spanner adds **TrueTime** (GPS + atomic-clock synchronized time) to give globally consistent transactions across continents. Vitess is the horizontal-sharding layer for MySQL that powers YouTube and Slack.

| Dimension | Value | Note |
| --- | --- | --- |
| **Scale** | Effectively unlimited horizontal | Add nodes → more capacity, still one logical SQL DB |
| **Consistency** | Strong / serializable, even cross-region | The headline feature |
| **Write latency** | Higher than single-node Postgres | Consensus = a quorum round-trip per commit (cross-region = 10s–100+ ms) |
| **Cost/complexity** | High | Only worth it when you genuinely need *both* SQL+ACID *and* horizontal/global scale |

**When it's right:** you've outgrown single-node Postgres on writes/size **but cannot give up SQL and strong consistency** — global fintech ledgers, multi-region inventory, systems where eventual consistency is unacceptable and manual sharding is too painful.

**What pushes you off:** you don't actually need strong global consistency (then Cassandra/Dynamo are cheaper and simpler); or your scale fits single-node Postgres (then NewSQL is over-engineering with a real latency tax).

---

## 🔹 10. Columnar / OLAP Warehouse (ClickHouse / BigQuery / Snowflake / Redshift) — Analytics, Not Transactions

**What it is:** These store data **by column, not by row**, so summing one column over a billion rows reads only that column's contiguous data — not every full row. They're built for OLAP: massive scans and aggregations, not point lookups or per-row updates. **ClickHouse** is self-managed and blisteringly fast per node; **BigQuery/Snowflake** are fully managed, serverless, separate storage from compute, and scale to petabytes.

| Dimension | Value | Note |
| --- | --- | --- |
| **Storage** | Petabyte-scale routine | Columnar compression is dramatic (often 10×+) |
| **Scan throughput** | Billions of rows/sec (ClickHouse) | Column pruning + vectorized execution |
| **Point lookups / single-row updates** | **Terrible** | Never use as an OLTP store |
| **Write pattern** | Bulk/batch load, not per-row inserts | Real-time single-row writes are an anti-pattern |

**When it's right:** dashboards, BI, ad-hoc analytics over huge history, aggregations across the entire dataset. Feed it from your OLTP store via ETL/CDC.

**What pushes you off:** transactional workloads (point reads, per-row updates, ACID) → back to Postgres/Cassandra/Dynamo. OLAP and OLTP are different jobs — use both, don't force one to do the other.

---

## 🧭 Scenario → Database Cheat Sheet

| Scenario | Primary store | Why | Support cast |
| --- | --- | --- | --- |
| User accounts, orders, payments | **PostgreSQL** | ACID, relations, correctness | Redis cache, replicas |
| Session store / rate limiter / leaderboard | **Redis** | Sub-ms, TTL, sorted sets | Postgres as truth |
| Chat / messaging history (write-heavy) | **Cassandra** | Huge write volume, partition by conversation | Redis for presence |
| IoT / sensor telemetry (100k+ writes/s) | **Cassandra** or **TimescaleDB** | Append-heavy, per-entity time-series | Kafka to buffer bursts |
| Metrics / monitoring dashboards | **InfluxDB / TimescaleDB** | Time-window aggregation, downsampling | Grafana |
| Product catalog (varied fields) | **MongoDB** | Flexible schema per category | Elasticsearch for search |
| Product / document **search** | **Elasticsearch** | Inverted index, relevance, facets | Postgres/Mongo as truth |
| Social graph / recommendations / fraud rings | **Neo4j** | Multi-hop traversal is O(1)/hop | Postgres for profiles |
| AWS-native, spiky traffic, known patterns | **DynamoDB** | Zero-ops, auto-scale | DAX cache |
| Global ledger needing SQL + strong consistency at scale | **Spanner / CockroachDB** | Horizontal SQL, serializable | — |
| BI / analytics over billions of rows | **BigQuery / Snowflake / ClickHouse** | Columnar, massive scans | Fed by CDC/ETL |
| Files / images / videos / backups | **S3 / object storage** | Cheap, unlimited, not queried by field | Postgres holds the URL+metadata |

---

## ⚠️ Anti-Patterns Hall of Fame

- **Using Redis as the source of truth.** It can lose up to 1 second on crash. Cache, don't persist-critical.
- **Choosing Cassandra without knowing your queries.** You model the table around the query; get it wrong and you can't fix it without a rewrite.
- **Elasticsearch as primary DB.** 1 s refresh lag, no ACID. Always sync *from* a real store.
- **Storing blobs in the DB.** Images/videos go to S3; the DB holds a URL. (Postgres row bloat, Mongo 16 MB limit, Dynamo 400 KB limit all punish this.)
- **OLAP queries on your OLTP database.** A `GROUP BY` over 3 years of orders on the live Postgres primary will starve real traffic. Offload to a warehouse.
- **Sharding on day one.** Cross-shard joins, distributed transactions, and resharding pain — paid forever, usually before you needed it. Do the math first.
- **Picking a DB by hype.** "Web-scale NoSQL" for a 10k-user app adds operational cost and removes joins/ACID you actually wanted.

---

## 🧾 TL;DR

> **Start at Postgres. Leave only when a number forces you.**
>
> - **Postgres/MySQL** — default OLTP. Sweet spot ≤ 5 TB, 5–15k WPS, 128 TiB ceiling (Aurora). Leave for: sustained >20k WPS, multi-region active-active, schemaless, full-text-at-scale, deep graphs, big analytics.
> - **Redis** — cache/speed layer, sub-ms. Sweet spot 10–25 GB, 50–80k ops/s. Single-threaded execution; ~50 GB fork limit. Never the source of truth.
> - **Cassandra/Scylla** — write monster, masterless, auto-scales. 1–3 TB/node, 10–50k WPS/node. Breaks on cross-partition queries, ACID, high deletes.
> - **MongoDB** — flexible schema. 5–20k WPS (single primary), working set must fit WiredTiger cache (~50% RAM), 16 MB doc cap. Breaks on high writes, joins, active-active.
> - **DynamoDB** — zero-ops AWS. 400 KB item, 1000 WCU/3000 RCU per partition, GSI eventually consistent. Breaks on multi-cloud, unknown access patterns, big analytics.
> - **Elasticsearch** — search, not truth. Heap ≤ 32 GB, shards 10–50 GB, 1 s refresh. Always fed from a primary store.
> - **TimescaleDB/InfluxDB** — time-series, 100k–1M+ points/s, auto-downsample.
> - **Neo4j** — relationships as data; multi-hop traversal. Vertical scale; sharding is hard.
> - **Spanner/CockroachDB/Vitess** — horizontal SQL + strong consistency when you need both at scale (pay a consensus latency tax).
> - **BigQuery/Snowflake/ClickHouse** — columnar OLAP, petabyte analytics. Never OLTP.
>
> Most products under ~100M users and ~10k WPS never need to leave a single Postgres + Redis. Prove the bottleneck with arithmetic before adding a database.

---

## 🔗 Related Notes

| Topic | File |
| --- | --- |
| NoSQL triangle deep dive (Cassandra vs Mongo vs Dynamo internals) | `Core-Architecture/Database-Core/59-nosql-cassandra-mongo-dynamo.md` |
| Database types & selection framework | `Core-Architecture/Database-Core/06-databases-types-and-selection.md` |
| Scale numbers & thresholds (with sweet-spot vs max) | `Foundations/Performance-and-Scale/52-numbers-to-know-scale-triggers.md` |
| Sharding strategy | `Core-Architecture/Database-Core/38-sharding-strategy.md` |
| CAP / consistency models | `Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Jul 19, 2026 | Created. Independent deep-research master guide covering 10 DB families (relational, Redis, Cassandra/Scylla, MongoDB, DynamoDB, Elasticsearch, time-series, graph, NewSQL, columnar OLAP). Each with single-node smooth vs hard-ceiling numbers for storage/read/write/memory, when-to-shard guidance, and explicit "what pushes you off this DB" migration triggers. Decision tree + LSM write-path ASCII visuals. Scenario→DB cheat sheet. Production-grade figures only. |
