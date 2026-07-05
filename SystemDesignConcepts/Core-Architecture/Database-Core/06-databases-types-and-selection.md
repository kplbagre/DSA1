# 06 — Databases: Types & Selection for System Design

> **Master the database decision.** Pick the right database for the job (PostgreSQL for relational data, Elasticsearch for search, Redis for speed, Kafka for events). Wrong choice = architectural rework under deadline pressure. This file teaches you to reason through the decision like a senior engineer.

---

## 🎯 Why You're Reading This

Every system design question you face (URL shortener, chat system, expense reports, search, auth) has a **database layer**. Your interview answer lives or dies on: *"Why PostgreSQL for this, not MongoDB? Why not Cassandra instead?"*

**After reading, you'll:**
1. **Understand the CAP theorem** — why you can't have all three (Consistency, Availability, Partition tolerance)
2. **Map use cases to databases** — when to pick SQL, NoSQL, search engines, time-series, or OLAP systems
3. **Reason about trade-offs** — scalability vs consistency, flexibility vs structure, latency vs durability
4. **Cross-reference your 10 solutions** — understand why C2 uses PostgreSQL, E1 uses Elasticsearch, A1 uses Redis
5. **Handle interviewer probes** — "Why not DynamoDB?" "What if you need 100K writes/sec?" "How would you scale this further?"

**This is foundational material.** You'll reference it before writing any API design or data model section. Master this first.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **ACID** | Atomicity (all-or-nothing), Consistency (valid state before and after), Isolation (concurrent txns don't interfere), Durability (survives crashes). The guarantee traditional databases give you. | PostgreSQL transactions: transfer money from account A to B; both succeed or both fail, never one without the other. |
| **BASE** | Basically Available (system stays up even if replicas disagree), Soft state (replicas may not be identical), Eventual consistency (replicas converge given time). The guarantee distributed NoSQL systems give you. | DynamoDB: write succeeds immediately even if replicas haven't synced yet; they'll sync within milliseconds. |
| **CAP Theorem** | In a distributed system, you can pick two of three: Consistency (all replicas see the same data), Availability (system always responds), Partition tolerance (survives network splits). You cannot have all three. | PostgreSQL with single primary: pick Consistency + Availability (but loses Partition tolerance — if the primary fails, replicas can't form consensus). Cassandra: picks Availability + Partition tolerance (loses Consistency — replicas may disagree temporarily). |
| **Consistency (strong)** | Every read sees the latest write. If I write "balance = 100," every subsequent read sees 100. | PostgreSQL with synchronous replication: write blocks until replicas confirm. |
| **Consistency (eventual)** | Reads may see stale data temporarily, but all replicas converge to the same state eventually. | DynamoDB: write succeeds immediately and is replicated asynchronously to other regions; a read 100ms later might see the old value, but within seconds all replicas agree. |
| **OLTP** | Online Transaction Processing — lots of small, fast reads/writes. Examples: web apps, payment systems, user registrations. | A1 (URL shortener): 35K shorten requests/sec, each is a small INSERT. |
| **OLAP** | Online Analytical Processing — few large, slow queries over huge datasets (scans, aggregations). Examples: analytics, dashboards, reporting. | "How many documents were signed by region in Q2?" — scans billions of rows. |
| **Sharding** | Horizontal partitioning — split data across multiple databases by some key (e.g., user_id % 10 = shard 0-9). Each shard is smaller, can live on different servers. | At 50M documents, split into 50 shards of 1M each (C3 pagination). |
| **Replication** | Copying data to multiple servers for redundancy (high availability). **Read replica** = slave can be read from but not written to. **Primary** = the source of truth. | PostgreSQL with 1 primary + 2 read replicas: writes go to primary, reads can hit any replica. |
| **Indexing** | Data structure (typically B-tree or hash) that speeds up lookups. Without it, every query scans the entire table. | C3 pagination: compound index on (created_at, id) makes cursor queries O(1) instead of O(N). |
| **Throughput** | How many operations per second the database can handle. Measured in requests/sec (req/sec) or transactions/sec. | E2 auth: 35K token validation requests/sec; PostgreSQL with single instance maxes out around 10K req/sec. |
| **Latency** | How long a single operation takes. Measured in milliseconds (ms). | A1 cache hit: 1-5ms (Redis). DB query miss: 10-20ms (PostgreSQL). |
| **TTL (Time-To-Live)** | Auto-expiry time. Used for caching: after TTL, the entry is deleted. | A1: Redis cache with 1-hour TTL; after 1 hour, the key is gone, forcing a DB query on next access. |
| **Write Bottleneck** | When the **primary** database receives more write operations than it can process per second. Symptoms: write latency climbs from 5ms → 500ms, CPU maxes out, queries queue up. A single PostgreSQL instance handles roughly 10–20K writes/sec before hitting this wall. Reads can scale by adding replicas; writes cannot — they must all go through the one primary. | A payment system at 15K writes/sec on a single Postgres: insert latency spikes to seconds. Fix: shard or switch to Cassandra. |
| **Replication Lag** | The delay between a write landing on the **primary** and that write appearing on a **read replica**. Usually milliseconds, but can reach seconds under load. Causes "read-your-own-writes" bugs. | User updates their username, immediately reads their profile — the read hits a replica that hasn't synced yet, so they see the old username. Fix: route reads to primary for 1–2 seconds after a write (or always route writes + their immediate reads to primary). |

---

## 🧠 Mental Model: The Database Decision Framework

**The core insight:** Databases exist on a spectrum. At one extreme, **PostgreSQL optimizes for ACID + strong consistency** (you're guaranteed correct answers, but at the cost of write scalability). At the other extreme, **Cassandra optimizes for Availability + scale** (you can write to millions of nodes in parallel, but data might be stale for a moment).

**Your job:** Decide **what matters more for your problem** — consistency or availability? Strong consistency or eventual? Fast reads or fast writes?

### The CAP Theorem Visualized

```
                    CONSISTENCY
                         △
                        /│\
                       / │ \
                      /  │  \
        PostgreSQL   /   │   \
           (sync     /    │    \  Cassandra
          replication)    │     (quorum replication)
                  /       │       \
                 /        │        \
                /         │         \
    AVAILABILITY ←────────┼────────→ PARTITION
    (stays up)              │        TOLERANCE
               Redis single-node (≈CA)   (network split)
```

**KEY INVARIANT:** Every database sits at a point on this triangle. You pick two of three. The position determines how the database behaves under failure (network split), how fresh reads are, and how it scales.

> **Redis in CAP:** Redis single-node ≈ CA — one server means no partition to tolerate; it is consistent and available. Redis Cluster ≈ AP — data is partitioned across nodes; under a network split, Redis Cluster prioritizes availability over strong consistency (reads may briefly return stale values).

---

## 🎨 Visual — The Database Decision Flowchart

```
                    START: New System Design Problem

                             │
                             ▼
                      How much data?
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
      < 100GB            100GB - 1TB          > 1TB
          │                  │                  │
          ▼                  ▼                  ▼
    Single Machine    Multiple Machines    Horizontal Sharding
                             │              Required
                             │                 │
                             └────────┬────────┘
                                      │
                                      ▼
                      What's your read/write pattern?
                             │
          ┌──────────────────┼────────────────────┐
          │                  │                    │
    Structured Data   Complex Queries        Full-text Search
    (rows, columns)   on Relationships       (keyword matching)
          │                  │                    │
          ▼                  ▼                    ▼
    NoSQL Document   PostgreSQL /           Elasticsearch /
    OR               MySQL                  Solr
    PostgreSQL           │
          │          ┌────┴─────┐
          │          │          │
        Flexible?  Strong     Eventual
          │      Consistency? Consistency?
          │      Acceptable?  OK?
          │          │          │
          ▼          ▼          ▼
      MongoDB     PostgreSQL  Cassandra
     DynamoDB    (with HA)   (multi-region)


                    What's your latency SLO?
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
       < 10ms            10-100ms             > 100ms
          │                  │                  │
          ▼                  ▼                  ▼
       Redis           PostgreSQL            BigQuery
     (in-memory)       + Redis Cache        (analytical)
       or              or Elasticsearch
     Memcached


    After selection, verify against:
    ┌─────────────────────────────────────────────┐
    │ ✓ Scale (throughput at peak)                │
    │ ✓ Consistency model matches SLO             │
    │ ✓ Query patterns supported                  │
    │ ✓ Operational complexity (your team size)   │
    └─────────────────────────────────────────────┘
```

**How to use this flowchart:**
1. Start at the top
2. Follow the path based on your constraints
3. You'll land on a database (or a family of databases)
4. Then verify against the checklist at the bottom

---

## 🔹 Relational Databases (SQL)

### What They Are

**Relational databases** (PostgreSQL, MySQL, MariaDB, Oracle) store data in **tables with predefined schemas** — rows and columns. Every row must follow the schema (email column is always a string, age is always an integer). This rigidity is a feature: the database enforces correctness.

**The core guarantee:** ACID transactions. A transaction either succeeds completely or fails completely. This is how banks transfer money safely — if the DB crashes mid-transfer, the transfer either completes or rolls back entirely, never leaving money vanished from one account without reaching the other.

### When to Use

✅ **Use PostgreSQL/MySQL if:**
- Your data is **structured** (employees have name, email, salary; documents have title, owner, status)
- You need **strong consistency** (money transfers, approval workflows, audit trails)
- You'll **join tables** (find all documents by Alice that are "signed")
- You need **ACID transactions** (all-or-nothing operations)
- Reads and writes are **balanced** (not 100:1 skewed)
- Your team is **familiar with SQL** (easy to hire for)
- Data fits on a **single machine or small cluster** (< 10 TB)

❌ **Avoid if:**
- Your schema **changes constantly** (new fields every sprint — use MongoDB)
- You have **billions of rows** and need to shard (sharding is painful in SQL; NoSQL is easier)
- Your data is **semi-structured** (nested documents, arrays, mixed types)
- You need **sub-millisecond latency** (Redis is better)
- You need **full-text search** (Elasticsearch is better)

### Scaling Strategies

**Why reads scale but writes don't.** When you add a read replica, you create another server that can handle reads — three replicas means three servers serving reads in parallel, tripling read throughput. Writes are different: every write must go to the **primary** (the single source of truth). Replicas receive updates asynchronously after the write lands on the primary. So no matter how many replicas you add, the write ceiling stays at one primary's limit (~10–20K writes/sec for PostgreSQL). To scale writes, you must split the data across multiple independent primaries — that's **sharding**.

**Identifying the bottleneck:** Read-heavy load (reads swamping the DB, writes fine) → add replicas. Write-heavy load (insert queue growing, write latency spiking) → shard.

```
                Single Postgres Instance
                (~10K writes/sec, ~10K reads/sec)
                         │
                         ▼
               What is the bottleneck?
      ┌──────────────────────────────────┐
      │                                  │
 Read-heavy                         Write-heavy
 (reads > 10K/sec,                  (writes > 5K/sec,
  writes are fine)                   insert queue grows)
      │                                  │
      ▼                                  ▼
 Add Read Replicas                  Shard by Key
 (1 primary +                       (hash user_id % 10
  2–3 read-only replicas)            = 10 separate primaries)
      │                                  │
      ▼                                  ▼
 Reads now spread                   Each shard handles
 across replicas                    1/10th of all writes
 (writes still → primary)
                                         │
                              ┌──────────┴──────────┐
                              ▼                     ▼
                         App routes             Cross-shard
                         by hash key            queries need
                         (fast)                 fan-out (costly)


KEY INVARIANT:
  Replication helps reads (replicas spread read load).
  Sharding helps writes (each shard handles fewer writes).
  Together: read replicas per shard = PostgreSQL at scale.
  Cost: sharding makes cross-shard queries expensive.
```

> **⚠️ Replication Lag:** Replica writes happen asynchronously — there is a brief delay (usually milliseconds, but up to seconds under load) before a replica reflects what the primary just wrote. This causes **read-your-own-writes** bugs: user writes a new username → immediately reads profile → read hits a stale replica → they see the old name. Fix: route reads to the primary for 1–2 seconds after a write, or use sticky reads (always read from primary within a user's session window immediately after any write).

> **⚠️ Cross-Shard Queries:** Sharding by `user_id` puts all of one user's data on one shard — single-user queries are fast. But "find all documents signed today across all users" requires hitting every shard, collecting results, and merging in the application layer. This **fan-out query** (sending one logical query to multiple shards in parallel and stitching the results) costs N× the resources. Common fixes: (1) denormalize — on every write, also write a copy to a dedicated aggregation table; (2) secondary Elasticsearch index — index events as they happen; admin cross-shard queries go to Elasticsearch instead of sharded Postgres.

### Example from Our Solutions

**C2 (Expense Report System):** Uses PostgreSQL because:
- Data is structured (employees, reports, line items, approvals)
- Needs ACID transactions (update approval_state atomically)
- Needs strong consistency (once approved, can't change)
- Uses joins (find all reports by Alice that are REJECTED)
- Scale is manageable (10K employees, 2K reports/day = 0.02 req/sec, single instance is plenty)

**E2 (Authentication):** Uses PostgreSQL for:
- Users table (email, password hash, roles)
- User roles and permissions
- Audit log (immutable, append-only)
- Scale: 10M users fit on one large Postgres instance; token validation is stateless (JWT), so no DB hit on every request

### Implementation Sketch

```java
// Example: Find all documents signed by Alice
List<Document> docsByAlice = db.query(
    """
    SELECT d.id, d.title, d.status
    FROM documents d
    JOIN signings s ON d.id = s.document_id
    WHERE s.signer_name = 'alice'
    AND d.status = 'SIGNED'
    ORDER BY d.created_at DESC
    """
);

// Key insight: This JOIN is fast because:
// 1. Both tables are indexed (document_id is indexed in signings)
// 2. Single database holds both tables (no network round-trip for the join)
// 3. PostgreSQL optimizer finds the best execution plan
```

---

## 🔹 NoSQL — Document Databases

### What They Are

**Document databases** (MongoDB, CouchDB, DynamoDB) store data as **semi-structured documents** (JSON-like) instead of rigid rows. A document can have any fields; the next document can have different fields. This flexibility is useful when your schema is **evolving** or **has nested structures**.

**Typical guarantee:** Eventual consistency. A write succeeds immediately, but replicas might not have synced yet. Reads might see stale data briefly (milliseconds to seconds), but all replicas eventually agree.

### When to Use

✅ **Use MongoDB/DynamoDB if:**
- Your schema **evolves frequently** (prototyping, startups, rapid iteration)
- You have **nested structures** (a document is a user + their profile + their preferences, all in one)
- You need **horizontal write scaling** (millions of writes/sec across shards)
- You can tolerate **eventual consistency** (reads may see stale data temporarily)
- Your data is **semi-structured** or **unstructured**

❌ **Avoid if:**
- You need **ACID transactions** (money transfers, approval workflows)
- You need **strong consistency** (audit logs, legal compliance)
- You'll do complex **JOINs across collections** (much harder than SQL)
- Your team is **SQL-fluent** (switching to NoSQL is a context-switch)

### Example: MongoDB

```javascript
// Document (JSON-like) — schema is flexible
{
  _id: "user-123",
  email: "alice@example.com",
  roles: ["EDITOR", "SIGNER"],  // Array of roles
  profile: {                      // Nested object
    name: "Alice",
    avatar_url: "..."
  },
  preferences: {
    theme: "dark",
    notifications: true
  }
}

// If you need to add a new field (e.g., "phone"):
// Just insert it. No ALTER TABLE, no schema migration.
// Other documents might not have it — that's fine.
```

**Key difference from PostgreSQL:** No schema enforcement. MongoDB doesn't care if one document has a "phone" field and another doesn't. This is a double-edged sword:
- ✅ Pros: Easy to evolve, natural for nested data
- ❌ Cons: No automatic validation, easier to have bugs ("phone" might be missing in some docs, causing NullPointerExceptions)

---

## 🔹 NoSQL — Key-Value Stores

### What They Are

**Key-value stores** (Redis, Memcached) are **in-memory databases** where you store and retrieve values by key. No schema, no queries — just `GET key` and `SET key value`. Ultra-fast because they live in RAM.

**Typical use:** Caching layer (sit in front of PostgreSQL to avoid slow DB queries) or session storage.

### When to Use

✅ **Use Redis if:**
- You need **sub-millisecond latency** (caching for high-traffic reads)
- You're storing **simple key-value data** (user session, cached query result, counter)
- You need **TTL (auto-expiry)** for temporary data
- You have a **high read-to-write ratio** (100:1 reads, 1:1 writes)
- You need **atomic operations** (increment a counter, INCR key, is guaranteed atomic)

❌ **Avoid if:**
- You need **durability guarantees** (Redis data is lost on restart unless persisted to disk)
- You need **complex queries** (Redis doesn't support SQL-like queries)
- You need **transactions** (no ACID guarantees)

### TTL and Eviction

```
                          Redis Instance (e.g., 25 GB)
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
            Session Key        Cache Key       Counter
            (TTL: 1 hour)  (TTL: 5 minutes)   (no TTL)
                    │              │              │
                    ▼              ▼              ▼
            Still exists    Deleted after    Persists
                           5 minutes
                               │
                               ▼
                    If more memory needed,
                    evict oldest (LRU policy)


KEY INVARIANT:
  TTL = Time-To-Live. Key automatically deletes after TTL expires.
  No cleanup job needed. Perfect for caches and session stores.
  If memory is full and a new key arrives, Redis evicts
  the least-recently-used key to make room.
```

### Example from Our Solutions

**A1 (URL Shortener):** Uses Redis as a cache:
```
Redis cache with 1-hour TTL:
  Key: short_code (e.g., "abc123")
  Value: original_url (e.g., "https://example.com/very/long/path")
  TTL: 3600 seconds

On redirect (GET /abc123):
  1. Check Redis: FAST (< 5ms), hit rate 90%
  2. On miss, query PostgreSQL: slower (10-20ms), hit rate 10%
  3. Repopulate Redis for next time

Result: 90% of redirects are super-fast, DB doesn't bottleneck.
```

**E2 (Authentication):** Uses Redis for:
- Token blacklist (when a user logs out, add JWT to `blacklist:{jti}` with TTL = token expiry time)
- Session storage (temporary MFA codes, 5-min TTL)

---

## 🔹 Search Engines

### What They Are

**Search engines** (Elasticsearch, Solr) are databases optimized for **full-text search**. They store an **inverted index** (a mapping from every word to the documents containing it), making keyword searches fast.

If PostgreSQL is "find the employee with ID=123," Elasticsearch is "find all contracts mentioning 'liability' and 'indemnity'."

### When to Use

✅ **Use Elasticsearch if:**
- You need **full-text search** (find documents by keyword)
- You need **relevance ranking** (BM25 scoring — "relevance match quality")
- You need **filters + facets** (search contracts by date range, author, type)
- You have **billions of documents** and searches must be fast (< 500ms)
- You're building **autocomplete** suggestions

❌ **Avoid if:**
- Your queries are **simple lookups** (find user by ID — PostgreSQL is faster)
- You need **ACID transactions** (Elasticsearch has no transactions)
- You need **strong consistency** (Elasticsearch is eventually consistent)

### Inverted Index Visualization

```
Documents:
  Doc 1: "Alice signed the contract"
  Doc 2: "Bob rejected the proposal"
  Doc 3: "Alice and Bob signed"

Inverted Index (word → documents):
  "Alice" → [Doc 1, Doc 3]
  "Bob" → [Doc 2, Doc 3]
  "signed" → [Doc 1, Doc 3]
  "rejected" → [Doc 2]
  "contract" → [Doc 1]
  "proposal" → [Doc 2]

Query: "Alice signed"
  1. Look up "Alice" → [Doc 1, Doc 3]
  2. Look up "signed" → [Doc 1, Doc 3]
  3. Intersection → [Doc 1, Doc 3]
  4. Rank by relevance (BM25) → Doc 1 scores higher (both words present)

Result: [Doc 1, Doc 3] sorted by relevance

KEY INVARIANT:
  Inverted index lets us answer "find all documents with keyword X"
  in O(log N) time instead of O(N) full-table scan.
  The index is built offline when documents are indexed,
  making searches fast and scalable to billions of docs.
```

### Example from Our Solutions

**E1 (Search System):** Uses Elasticsearch:
- 50M documents indexed
- Shard by document_id hash (50 shards = 1M docs/shard)
- Each search broadcasts to all 50 shards in parallel
- BM25 scoring: "contract" in title > "contract" in snippet
- Cache top N results in Redis (5-min TTL, 80% hit rate)
- Result: sub-500ms search latency at 3.5K peak QPS

---

## 🔹 Time-Series Databases

### What They Are

**Time-series databases** (InfluxDB, Prometheus, TimescaleDB) are optimized for data with timestamps that arrives in chronological order. Perfect for **metrics** (CPU usage every second), **logs** (requests per minute), **sensor data** (temperature readings).

### When to Use

✅ **Use InfluxDB/Prometheus if:**
- Your data is **timestamped** and **append-only** (never edited)
- You need **aggregations over time windows** ("average latency per 5-minute bucket")
- You have **high write throughput** (millions of metrics/sec)
- You need **data retention policies** ("keep raw data for 7 days, then aggregate to 1-hour buckets")

### Example

```
Prometheus scraping metrics every 10 seconds:
  timestamp=2026-06-24T10:00:00Z, metric=http_requests_total, value=5000
  timestamp=2026-06-24T10:00:10Z, metric=http_requests_total, value=5050
  timestamp=2026-06-24T10:00:20Z, metric=http_requests_total, value=5100
  ...

Query: "average requests per minute over the last 24 hours?"
  Prometheus aggregates the per-second data into 1-minute buckets.
  Returns 1440 data points (one per minute).
  Fast because it's optimized for this pattern.

PostgreSQL would have to:
  1. Scan all 1M rows (10 seconds × 86,400 seconds/day)
  2. GROUP BY time bucket
  3. AVERAGE each bucket
  Much slower.

KEY INVARIANT:
  Time-series DBs pre-aggregate data so that "average over
  time window" queries are fast. They sacrifice random-access
  flexibility for optimization on the time axis.
```

---

## 🔹 Columnar / OLAP Databases

### What They Are

**Columnar databases** (Snowflake, BigQuery, Redshift) store data **by column**, not by row. Instead of storing "row 1 = (Alice, editor, 2026-01-01)," they store "Alice and 500K other names in column 1, editor and 500K other roles in column 2," etc.

This layout is terrible for row-level operations but amazing for **analytics** (aggregate a single column: "count all SIGNED documents").

### When to Use

✅ **Use BigQuery/Snowflake if:**
- You're doing **analytics** ("revenue per region in Q2?")
- You need to **scan billions of rows** but only a few columns
- You have **periodic batch processing** (data arrives in batches, not continuous writes)
- Latency is **not critical** (queries might take seconds or minutes)

❌ **Avoid if:**
- You need **sub-second latency** (these are slow for individual row lookups)
- You need **real-time transactional updates** (they're write-slow)
- Your data is **constantly changing** (designed for batch inserts, not live updates)

### Row-Oriented vs Column-Oriented

```
Row-Oriented (PostgreSQL):
┌─────────────────────────────────┐
│ Row 1: Alice, EDITOR, 2026-01   │
│ Row 2: Bob, VIEWER, 2026-02     │
│ Row 3: Charlie, SIGNER, 2026-01 │
│ Row 4: Diana, EDITOR, 2026-03   │
└─────────────────────────────────┘

To answer "how many EDITOR?", scan all 4 rows.

Column-Oriented (BigQuery):
┌────────────────────────────────┐
│ Names: [Alice, Bob, Charlie, Diana, ...] │
│ Roles: [EDITOR, VIEWER, SIGNER, EDITOR, ...] │
│ Dates: [2026-01, 2026-02, 2026-01, 2026-03, ...] │
└────────────────────────────────┘

To answer "how many EDITOR?", scan only the Roles column.
(Other columns are ignored.)

For 1B rows:
  Row-oriented: read 1B rows (all columns) → slow
  Column-oriented: read 1B values in Roles column → fast (can compress, skip)

KEY INVARIANT:
  Row-oriented optimizes for "give me everything about row X."
  Column-oriented optimizes for "give me one column across all rows."
  This is the opposite direction of optimization.
```

---

## 🧭 Decision Matrix — Which Database for Which Scenario?

| Scenario | Pick This DB | Why |
|----------|---|---|
| **Scale: < 100 GB, balanced reads/writes, structured data** | PostgreSQL | ACID, strong consistency, no sharding overhead. Simple. |
| **Scale: < 100 GB, lots of READS, structured data** | PostgreSQL + Redis cache | Cache absorbs read load; DB handles writes. |
| **Scale: 1 TB, structured, balanced** | PostgreSQL with read replicas | Reads hit replicas; primary handles writes. Single DB still fits. |
| **Scale: 1 TB+, massive WRITES, structured** | PostgreSQL + sharding OR Cassandra | Need horizontal write scaling. Cassandra is easier to shard. |
| **Flexible schema, evolving rapidly** | MongoDB | Semi-structured. Easy schema changes. |
| **Sub-millisecond latency, caching, counters** | Redis | In-memory, atomic operations, TTL. |
| **Full-text search, keyword matching, 1B+ docs** | Elasticsearch | Inverted index. BM25 ranking. |
| **Metrics, logs, time-ordered data** | InfluxDB / Prometheus | Optimized for append-only, aggregations over time. |
| **Analytics, aggregations, slow queries OK** | BigQuery / Snowflake | Column-oriented. Scan billions of rows efficiently. |
| **Audit logs, append-only, immutable** | PostgreSQL (append-only table) or Kafka | If OLTP: PostgreSQL. If streaming: Kafka. |
| **Real-time notifications, fanout, high throughput** | Kafka + message queue (SQS) | Decouples producers from consumers. Durable. |
| **Session storage, temporary data, high throughput** | Redis with TTL | In-memory. Keys auto-delete on TTL. |

---

## 🔬 Worked Examples (From Our 10 Solutions)

### Example 1: A1 — URL Shortener

**Problem:** 1M URLs/day, 100:1 read:write ratio (3,300 reads/sec peak)

**Our choice:** PostgreSQL + Redis cache

**Reasoning:**
1. Data is structured (short_code, original_url, created_at) → SQL
2. Writes are modest (33/sec peak) → single Postgres instance handles it
3. Reads are massive (3,300/sec) → need caching to avoid DB bottleneck
4. Solution: Redis cache with 1-hour TTL absorbs 90% of read traffic

**If the requirement changed:**
- "10B URLs/day" → sharding would be needed (Redis helps, but 33K writes/sec exceeds single DB). Add Cassandra or shard PostgreSQL.
- "Expect analytics (top URLs)" → add BigQuery for analytical queries; keep PostgreSQL for OLTP.

---

### Example 2: E1 — Search System

**Problem:** 50M documents, 100M searches/day (3.5K QPS), rank by relevance, filters

**Our choice:** Elasticsearch + Redis cache

**Reasoning:**
1. Need full-text search with relevance ranking → SQL can't do this efficiently
2. 50M documents → single Postgres instance struggles; Elasticsearch shards naturally
3. Searches are read-heavy → cache top results in Redis (5-min TTL, 80% hit rate)

**If the requirement changed:**
- "Just metadata search (no full-text)" → PostgreSQL with GIN index on document fields. Much simpler.
- "Need real-time indexing (< 100ms)" → Elasticsearch is already doing this.
- "Multi-language search, entity recognition" → add NLP; Elasticsearch plugins handle some of this.

---

### Example 3: D3 — Notification Service

**Problem:** 1B notifications/day (35K peak), multi-channel (email/SMS/push), at-least-once delivery

**Our choice:** PostgreSQL (notification history) + Kafka (event publishing) + per-channel SQS

**Reasoning:**
1. Notification history is structured → PostgreSQL
2. Reliably publish to Kafka → use outbox pattern (DB transaction + Kafka write atomicity)
3. Fan-out to 3 channels with different throughputs → separate SQS queues per channel (SMS is slow, email is fast)

**If the requirement changed:**
- "Exactly-once delivery required" → add idempotency table in PostgreSQL; check before sending.
- "Need historical search (find notifications by user)" → add Elasticsearch for fast full-text search on notification content.

---

### Example 4: E2 — Authentication

**Problem:** 10M users, 35K token validations/sec, strong consistency (auth failures = security issue)

**Our choice:** PostgreSQL (users, roles) + Redis (token blacklist, sessions)

**Reasoning:**
1. User data is structured, needs strong consistency → PostgreSQL with password hashing
2. Token validation is stateless (JWT signature check) → O(1), no DB hit on each request
3. Token revocation (logout) → Redis blacklist with TTL = token expiry time
4. MFA codes → Redis with 5-min TTL

**Why NOT Cassandra?** Eventual consistency is unacceptable for auth. If a user is compromised and we add their token to the blacklist, we need that visible immediately to all API servers. Cassandra's replication lag (even 10ms) is unacceptable for security.

---

## ⚠️ Gotchas — Common Mistakes

### Mistake 1: Using SQL for Everything

**The trap:** PostgreSQL works for everything, so new engineers use it for everything. Then you hit scale (100K writes/sec) and realization hits: sharding is hard.

**When it fails:** E1 (Search System) at 1B documents. PostgreSQL would need 50 shards; each shard write requires application-level routing. Elasticsearch handles sharding transparently.

**Prevention:** At 10M+ documents or 10K+ writes/sec, evaluate specialized databases. Don't default to SQL.

### Mistake 2: Forgetting the CAP Theorem

**The trap:** Engineer designs a Cassandra cluster assuming strong consistency. Oops. Cassandra picks Availability + Partition tolerance, not Consistency.

**When it fails:** E2 (Authentication). If you use Cassandra, a compromised token might still validate on one node even after being revoked on another (replication lag). Unacceptable.

**Prevention:** Be explicit: "I need strong consistency" → PostgreSQL. "Availability is more important" → Cassandra.

### Mistake 3: Choosing NoSQL Too Early

**The trap:** "Our schema might change, so let's use MongoDB." Then you have 10 fields and half the documents are missing "phone" because different parts of the code forgot to add it.

**When it fails:** If your schema **actually stabilizes** after a few months (and it usually does), the flexibility cost (no validation, harder queries) outweighs the benefit.

**Prevention:** Start with SQL. If schema changes become a constant blocker (not just a one-time rewrite), then migrate to NoSQL.

### Mistake 4: Underestimating Operational Complexity

**The trap:** "Cassandra scales better than PostgreSQL, let's use it!" Then you realize Cassandra requires 24/7 ops monitoring, careful tuning, and a team that understands distributed databases.

**When it fails:** A startup team of 5 engineers can operate PostgreSQL + 2 read replicas easily. They cannot operate Cassandra + 10 nodes without pain.

**Prevention:** Estimate operational load. If your team is small or doesn't know the technology, pick the simpler option (PostgreSQL) until you hit a hard limit.

### Mistake 5: Confusing Persistence and Durability

**The trap:** "Redis is in-memory, so it's not durable." Actually, Redis **can** be durable if you enable AOF (Append-Only File) or RDB (snapshotting).

**When it fails:** Using Redis for critical cache that must survive restarts, then Redis crashes and loses everything.

**Prevention:** If data must survive restarts, use PostgreSQL. If data can be regenerated (cache), Redis is fine.

---

## 🔬 Interview Q&As

**Q1: How do you know if your PostgreSQL instance has hit a write bottleneck?**

> Three signals: (1) **Write latency spikes** — inserts that normally take 5ms start taking 200ms+. (2) **CPU saturation** — the DB server is pegged at 80–100% even during off-peak hours. (3) **Write queue depth** — connections pile up waiting for the primary; check `pg_stat_activity` for blocked queries. A single PostgreSQL primary handles roughly 10–20K writes/sec before this wall. At that point, reads can be offloaded to replicas — but if the bottleneck is the writes themselves, you need sharding or a distributed database like Cassandra.

---

**Q2: Why does E2 (Authentication) use PostgreSQL instead of Cassandra, given that Cassandra scales better?**

> Auth requires **strong consistency**. When a user is compromised and we add their token to the revocation list, every API server must see that revocation immediately. Cassandra's eventual consistency means a replica could lag by even 10ms — during that window, a revoked token could still validate on a stale replica. That's a security hole. PostgreSQL with synchronous replication guarantees that once a write commits, every subsequent read sees it. The trade-off is write scalability — but for auth, correctness beats scale. Token validation itself is stateless (JWT signature check), so we never hit the DB per validation; we only hit it on login/logout/revocation, which is far lower volume.

---

**Q3: Your system is sharded by `user_id`. An admin query asks: "show me all documents signed today across all users." How do you handle it?**

> Three options: (1) **Fan-out** — send the query to all shards in parallel, merge results in the app layer. Works, but costs N× resources (N = shard count). (2) **Denormalize** — maintain a separate "recent activity" append-only table on one dedicated shard; write to it on every signing event. Admin queries read from this single table. (3) **Secondary Elasticsearch index** — index all signing events into Elasticsearch as they happen; admin queries go to Elasticsearch, which handles cross-shard search natively. Option 3 is the production pattern. Option 1 is the interview-safe fallback to name when asked about trade-offs.

---

**Q4: A user updates their display name. Two seconds later they reload the page and see the old name. No error was thrown. What happened, and how do you fix it?**

> Classic **replication lag / read-your-own-writes** bug. The write landed on the primary, but the read request was routed to a read replica that hadn't synced yet. The lag is usually milliseconds but can reach seconds under load. Three fixes: (1) **Sticky primary reads** — after any write, route that user's reads to the primary for 1–2 seconds. (2) **Read-after-write routing** — the app tracks "this user wrote in the last N seconds" and forces reads to primary during that window. (3) **Synchronous replication** — configure PostgreSQL to not return from the write until all replicas confirm receipt (safest but adds write latency). Option 2 is the most common production approach.

---

**Q5: What's the difference between a read replica and a shard? When do you need each?**

> A **read replica** holds a **copy of the same data** as the primary — it solves the read scaling problem. The primary is still the single write destination; replicas are read-only mirrors updated asynchronously. Use replicas when reads overwhelm the primary (e.g., 100:1 read-to-write ratio). A **shard** holds a **subset of the data** — user IDs 0–9M on shard 0, 10M–20M on shard 1, etc. Each shard has its own independent primary, distributing writes across N primaries. Use sharding when writes exceed a single primary's capacity (~10–20K writes/sec for Postgres). The combination — read replicas per shard — is how PostgreSQL handles both problems at scale. The cost: application-layer routing complexity, and cross-shard queries become expensive fan-out operations.

---

## 🗺️ Practice Plan

### Tier 1 — Foundation (Read & Understand)
- [ ] Read Sections 1-4 above (Mental Model, CAP Theorem, Decision Matrix)
- [ ] For each database type (SQL, NoSQL, Search, Cache), understand the core use case
- [ ] Map our 10 solutions to the database types (A1 → PostgreSQL + Redis, E1 → Elasticsearch, etc.)

### Tier 2 — Deeper Dives (Pick One Per Week)
- [ ] Deep dive on PostgreSQL scaling: replication + sharding strategies
- [ ] Deep dive on Elasticsearch: inverted index + BM25 scoring
- [ ] Deep dive on Redis: TTL, eviction policies, atomic operations
- [ ] Deep dive on Cassandra: eventual consistency, quorum reads

### Tier 3 — Interview Scenarios (Practice Answering)
- [ ] "Design a system with 10B documents. Why not PostgreSQL?"
- [ ] "We need exactly-once delivery. What database?"
- [ ] "How do you scale writes past 100K/sec?"
- [ ] "Our schema is changing every sprint. PostgreSQL or MongoDB?"
- [ ] "We have 1M concurrent users. How many cache servers?"

### Tier 4 — System Design Integration
- [ ] Pick one of our 10 solutions (A1, A2, C1-C3, D1-D3, E1, E2)
- [ ] For each component, write down "why this database?" in 1-2 sentences
- [ ] Prepare a 60-second explanation of the database choices
- [ ] Practice answering "what if we scaled 10×?" and "what if consistency mattered more?"

---

## 🧾 TL;DR — One-Page Database Cheat Sheet

### The Decision Tree (use this in interviews)

```
Question 1: How much data?
  < 100 GB  → PostgreSQL (single instance)
  100GB-1TB → PostgreSQL + replicas (read scale)
  > 1TB     → PostgreSQL + sharding OR Cassandra (write scale)

Question 2: What consistency do you need?
  Strong (ACID)           → PostgreSQL
  Eventual (BASE)         → Cassandra, MongoDB
  Don't care (cache)      → Redis

Question 3: What queries will you run?
  Simple lookups (ID=X)   → Anything (SQL is simplest)
  Complex joins           → PostgreSQL (or BigQuery for analytics)
  Full-text search        → Elasticsearch
  Time-based aggregations → InfluxDB, BigQuery
  Keyword matching        → Elasticsearch

Question 4: What's your latency SLO?
  < 10ms   → Redis cache (or in-memory database)
  10-100ms → PostgreSQL (or replicated cache)
  > 100ms  → BigQuery (analytical queries OK)

Question 5: How fast does data change?
  Real-time writes needed → PostgreSQL, Cassandra, Kafka
  Batch inserts (hourly)  → BigQuery, Snowflake
  Append-only (logs)      → PostgreSQL (append table), Kafka
```

### Quick Lookup Table

| Database | Best For | Avoid For | Latency | Scale |
|---|---|---|---|---|
| **PostgreSQL** | Structured data, ACID, simple schemas | Sharding at massive scale | 10-20ms | Single instance: 10K req/sec |
| **Cassandra** | Massive scale, high availability, eventual consistency | Strong consistency, small teams | 10-50ms | 100K+ req/sec (distributed) |
| **MongoDB** | Flexible schemas, nested documents | Complex joins, strong consistency | 20-50ms | Scales with sharding |
| **Redis** | Caching, counters, sessions, high throughput | Persistence, complex queries | 1-5ms | Fits in memory (25–50 GB fork-safe; up to ~100 GB with persistence disabled) |
| **Elasticsearch** | Full-text search, relevance ranking, logging | Transactional updates, strong consistency | 50-200ms | 50M+ documents (sharded) |
| **InfluxDB** | Metrics, logs, time-series data | Row-level random access | 10-50ms | Fits in memory + disk |
| **BigQuery** | Analytics, aggregations, batch processing | Real-time transactional | 1-10 seconds | 100B+ rows (columnar compression) |

### The Three Guarantees You're Choosing Between

**ACID (PostgreSQL):** All-or-nothing, consistent, isolated, durable. Slow to scale writes.
**BASE (Cassandra):** Basically available, soft state, eventually consistent. Fast to scale, briefly inconsistent.
**Cache (Redis):** Ultra-fast, temporary, TTL-based, fits in RAM. Loses data on restart.

**Your job:** Decide which matters for each component of your system.
- Auth → ACID (security)
- Cache → Cache (speed)
- Notifications → Eventually consistent + Kafka (throughput)
- Payments → ACID (correctness)

---

## 🏢 Real World — Where Companies Use This

**Amazon (AWS / Retail)**
- **Product catalog:** DynamoDB (key-value store) for lookups + Elasticsearch for search
- **Orders:** PostgreSQL (ACID transactions) for consistency; replicated to Cassandra (analytics)
- **Notifications:** Kafka for event streaming; SQS for job queues
- **Metrics:** CloudWatch (time-series) + Timestream for real-time analytics
- **Data warehouse:** Redshift (OLAP) for business intelligence

**Uber**
- **User locations:** Redis (geospatial) for live tracking + Cassandra for historical data
- **Trip data:** PostgreSQL for transactional consistency; BigQuery for analytics
- **Notifications:** Kafka topics for ride updates (fan-out to drivers/passengers)
- **Search:** Elasticsearch for restaurants, drivers, historical rides
- **Caching:** Redis cluster for surge pricing calculations (real-time)

**Stripe (Payments)**
- **Transactions:** PostgreSQL with synchronous replicas (ACID critical for payments)
- **Ledger:** BigQuery (immutable append-only log of all money movements)
- **User data:** PostgreSQL + Redis cache (session/profile lookups)
- **Search:** Elasticsearch for transaction history, fraud detection queries
- **Metrics:** InfluxDB for latency tracking, payment success rates
- **Read replicas:** Multiple PostgreSQL read replicas across regions (latency-sensitive)

**Netflix**
- **Metadata:** Cassandra (high availability, eventual consistency for titles/ratings)
- **User preferences:** Redis (session cache of watched shows)
- **Search:** Elasticsearch (full-text search over 10K+ titles)
- **Viewing history:** Kafka (immutable stream of watch events) → BigQuery (analytics)
- **Recommendations:** Redis (precalculated, cached; updated hourly via batch)

**Shopify**
- **Products/inventory:** MongoDB (flexible schema for 1M+ stores with different catalog structures)
- **Orders:** PostgreSQL (ACID for payment consistency) + Cassandra (distributed read replicas)
- **Cache layer:** Redis (product info, cart contents, checkout session)
- **Search:** Elasticsearch (product search across stores)
- **Analytics:** BigQuery (order trends, customer behavior)

**Twitter / X**
- **Tweets:** Cassandra (massive scale, eventual consistency, billions of posts)
- **User timeline:** Redis (precalculated feeds, cache refreshed every hour)
- **Search:** Elasticsearch (full-text search over tweets, hashtags, trending)
- **User graph:** Redis (following/followers, cached for speed)
- **Metrics:** InfluxDB (tweets/sec, latency metrics)

**Google**
- **Search index:** Distributed custom database (not open-source)
- **Gmail/Drive:** BigTable (column-oriented, petabyte-scale, high availability)
- **Analytics:** BigQuery (petabytes of data, SQL queries on historical analytics)
- **Real-time:** Datastore/Firestore (for user-facing apps)
- **Cache:** Memcached (distributed caching layer)

**LinkedIn**
- **Profile data:** LinkedIn's custom database (similar to Cassandra)
- **Feed:** Kafka (events); cached feed served from Redis
- **Search:** Elasticsearch (people search, job search)
- **Analytics:** Pinot (real-time analytics) + Kafka streaming
- **Messaging:** Cassandra (distributed, high-throughput)

**DocuSign (Confirmed in interviews)**
- **Document metadata:** PostgreSQL (ACID for correctness)
- **Document storage:** S3 (immutable, versioned)
- **Search:** Elasticsearch (for finding documents by content/keyword)
- **Audit trail:** Append-only log in BigQuery (compliance requirement)
- **Real-time notifications:** Kafka (document signing events)

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| June 24, 2026 | **File created.** Comprehensive database selection guide for system design interviews. Covers CAP theorem, ACID vs BASE, all major database types (SQL, NoSQL Document, Key-Value, Search, Time-Series, OLAP), decision frameworks, and cross-references to our 10 DocuSign solutions (A1, A2, C1-C3, D1-D3, E1, E2). Includes worked examples, gotchas, practice plan, and TL;DR cheat sheet. Designed for long-term retention through mental models, ASCII visuals, and practical scenarios. |
| July 1, 2026 | **Comprehension gaps fixed.** (1) Terminology table: added Write Bottleneck and Replication Lag definitions. (2) Scaling Strategies: rewrote section — added prose explaining why reads scale but writes don't, replaced ambiguous "Write Bottleneck? No/Yes" diagram with labelled Read-heavy/Write-heavy branches, added ⚠️ Replication Lag callout, added ⚠️ Cross-Shard Queries callout with fan-out definition. (3) Added `## 🔬 Interview Q&As` section (5 Q&As covering bottleneck detection, Postgres vs Cassandra for auth, cross-shard queries, read-your-own-writes, replica vs shard). (4) Fixed CAP triangle: removed incorrect "NOT a database" Redis label; corrected to Redis single-node ≈ CA, Redis Cluster ≈ AP. |
| Jul 3, 2026 | **Number consistency fixes** (cross-note scan). (1) Postgres write capacity: updated 4 occurrences of `5K–10K writes/sec` → `10–20K writes/sec` to align with `52-numbers-to-know-scale-triggers.md`. (2) Redis memory ceiling in ASCII diagram: `8 GB max` → `e.g., 25 GB` (fork-safe practical limit). (3) Redis capacity in Quick Lookup Table: `~32-256GB per instance` → `25–50 GB fork-safe; up to ~100 GB with persistence disabled`. |
