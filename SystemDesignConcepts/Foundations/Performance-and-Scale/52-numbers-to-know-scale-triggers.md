# 52. Numbers to Know & Scale Triggers

> **How to use this note:** Read this before every mock interview. These numbers give you the math to JUSTIFY why you reach for distributed infrastructure — or why you don't. Without them, you either over-engineer or can't defend your choices.

---

## 🎯 Why This Matters

The single most common senior-level mistake is adding infrastructure the problem doesn't need. A candidate designs Yelp, mentions "10 million businesses," and immediately says "so I'd shard on `business_id`." The interviewer calculates silently: 10M × 1KB = 10GB. A laptop handles 10GB. The candidate just added a distributed database to a problem that fits on a thumb drive.

The opposite also kills: a candidate says "we'll use a single Postgres" for a system doing 50k writes per second — without knowing that a single Postgres saturates at 10-20k WPS (writes per second).

**These numbers are your court of appeal.** Do the math, compare to the threshold, then decide. Decisions made without the math are guesses.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| TPS | Transactions per second — the rate a database commits reads or writes | Postgres handles ~50k TPS (transactions per second) on reads |
| WPS | Writes per second — a write-specific subset of TPS | Payment system doing 5k WPS — Postgres handles this fine |
| RPS | Requests per second — HTTP or API-level request rate | 1M DAU × 10 API calls/day ÷ 86,400s ≈ 116 RPS |
| DAU | Daily Active Users — the standard denominator for capacity math | Instagram: ~500M DAU |
| TiB | Tebibyte — 2⁴⁰ bytes ≈ 1.1 TB; AWS uses TiB for storage limits | Aurora supports up to 128 TiB per cluster |
| AZ | Availability Zone — one isolated datacenter within a cloud region | us-east-1a and us-east-1b are separate AZs in AWS us-east-1 |
| Vertical Scaling | Making one machine bigger (more RAM, more CPU) — the right first move | Upgrade RDS from db.r5.large to db.r5.4xlarge |
| Horizontal Scaling | Adding more machines (sharding, replicas, cluster) — reach for this when the math forces it | Shard Postgres across 4 nodes when single node saturates at 50 TiB |
| Scale Trigger | The specific metric crossing a threshold that justifies adding infrastructure complexity | DB write load reaching 10k WPS → evaluate sharding |

---

## 🧠 The Core Principle — Vertical First, Horizontal When the Math Forces It

Modern hardware is dramatically more powerful than most study resources assume. Books written in 2018-2020 quote numbers that are 2-10× too conservative for 2026 hardware. This creates a systematic bias toward over-engineering.

**The mental model:** Every piece of infrastructure you add is a complexity debt. It adds failure modes, ops burden, and eventual consistency headaches. You pay that debt only when the math shows a single well-tuned instance is genuinely insufficient.

**One single Postgres instance (2026):** handles 50 TiB of data, 50k read TPS (transactions per second), 10-20k write TPS, and 5k concurrent connections. Most applications with tens of millions of users will never exceed this on writes.

---

## 🎨 Visual — The Scale Decision Ladder

```
WHEN A NEW DESIGN PROBLEM ARRIVES:
Step 1 — Estimate your load (see Back-of-Envelope section below)
Step 2 — Compare against thresholds
Step 3 — Add exactly what the math forces. Nothing more.

                    ESTIMATED LOAD
                          │
           ┌──────────────▼─────────────────┐
           │  Does a single instance handle  │
           │  this? Compare to table below.  │
           └──────────────┬─────────────────┘
                          │
             ┌────────────┴─────────────┐
             │ YES                      │ NO — which limit hit?
             ▼                          │
    ┌─────────────────┐    ┌────────────▼──────────────────────────┐
    │ Single instance │    │ READ throughput? → read replicas       │
    │ is enough.      │    │   → see 29-db-replication-failover.md  │
    │                 │    │                                        │
    │ Still add:      │    │ WRITE throughput? → sharding           │
    │ - read replica  │    │   → see 38-sharding-strategy.md        │
    │   for HA        │    │                                        │
    │ - monitoring    │    │ DATASET SIZE? → shard by key           │
    │ - backups       │    │   → see 38-sharding-strategy.md        │
    │                 │    │                                        │
    │ Do NOT add:     │    │ LATENCY too high? → cache layer        │
    │ - sharding      │    │   → see 03-caching.md                  │
    │ - Kafka         │    │                                        │
    │ - Redis cluster │    │ GLOBAL USERS? → multi-region           │
    └─────────────────┘    │   → see 40-multi-region-geo-failover   │
                           │                                        │
                           │ CACHE MISS RATE high? → cache sizing   │
                           │   → see 03-caching.md                  │
                           │                                        │
                           │ WRITE SPIKES unpredictable? → queue    │
                           │   → see 19-message-queues-kafka.md     │
                           └────────────────────────────────────────┘

KEY INVARIANT:
   The threshold is the evidence. "It feels like a lot" is not a threshold.
   Do the multiplication. Compare the result to the table. Decide.
```

---

## ⚡ Component Thresholds — 2026 Baselines

### 🔹 Relational Database (PostgreSQL / MySQL / Aurora)

**What it is:** A relational database stores structured data in tables with rows and columns, on disk, with ACID guarantees (Atomicity — all-or-nothing transactions, Consistency — rules never violated, Isolation — concurrent transactions don't corrupt each other, Durability — committed data survives crashes). It is the correct default for any structured business data.

**Why the limits exist:**
- **Write limit (10-20k WPS):** Every committed write must be flushed to the Write-Ahead Log (WAL — a sequential append file on disk) before Postgres confirms success. Disk fsync is the bottleneck. More indexes per table = more WAL entries per write = lower WPS.
- **Read limit (50k TPS simple):** Postgres keeps a portion of the table in RAM called the buffer pool. If the data you're reading is already in the buffer pool, it's fast — no disk needed. If it isn't (cache miss), Postgres must read from disk, which is slower. Complex JOINs and aggregations read many pages, so they hit the buffer pool limit faster.
- **Connection limit (200-500 raw):** Each Postgres connection spawns a dedicated OS process, consuming ~5-10 MB RAM per connection. At 500 connections, that's 2.5-5 GB just for connection overhead — before any queries run. Use PgBouncer (a connection pooler — a proxy that maintains a small pool of real Postgres connections and multiplexes thousands of app connections into them) to avoid this limit.

**3 signals that mean "scale Postgres":**
1. Sustained write load consistently above 10k WPS → evaluate sharding
2. Read latency growing as user count grows → add read replicas
3. App hitting connection pool exhaustion errors → add PgBouncer first, then replicas

| Metric | Single-Instance Capacity | You'd Scale When... | Reach For |
|---|---|---|---|
| **Storage** | Up to 64 TiB (RDS), 128 TiB (Aurora) | Dataset approaching 50 TiB | `38-sharding-strategy.md` |
| **Read TPS** | ~50k TPS for simple indexed reads (buffer pool hit); complex JOINs/aggregations: 1-10k TPS | Sustained reads > 40k TPS | `29-db-replication-failover.md` (add read replicas) |
| **Write TPS** | 10-20k TPS simple inserts/updates | Sustained writes > 10k WPS | `38-sharding-strategy.md` |
| **Connections** | 200–500 raw Postgres connections; 5–20k app connections via PgBouncer | App is exhausting connection pool | `16-connection-pooling-db-performance.md` |
| **Cross-region** | Single-region primary | Users across continents need < 50ms | `40-multi-region-geo-failover.md` |
| **Consistency** | Strong by default | Multi-region async replication needed | `34-cap-theorem-consistency-models.md` |

> **Interview use:** "Our dataset is ~500GB. A single Aurora instance handles 128 TiB — we're at 0.4% of its limit. No sharding needed. I'd add a read replica for high availability."

---

### 🔹 In-Memory Cache (Redis / ElastiCache)

**What it is:** An in-memory cache stores data entirely in RAM — not on disk. Reading from RAM is roughly 100× faster than reading from disk, which is why Redis can return results in under 1ms. You use Redis to hold your most frequently-read data (user sessions, product details, hot leaderboards) so your database doesn't have to serve every single read request. The database becomes the source of truth; Redis is the fast lane in front of it.

**Why the limits exist:**
- **Memory limit (25–50 GB fork-safe):** Redis periodically saves a snapshot of everything in memory to disk for durability — a process called BGSAVE (Background Save). To do this safely while still serving live requests, Redis *forks* the OS process (creates a temporary copy of itself). The OS uses copy-on-write during the fork: if data changes while the snapshot is in progress, it temporarily holds the old version AND the new version in memory at the same time. On a heavily-written 50 GB dataset, this fork can temporarily require up to 100 GB of machine RAM. If the machine doesn't have that headroom, the fork fails — meaning no snapshots, no persistence, potential data loss on restart. Rule of thumb: keep the Redis dataset under 50% of machine RAM so the fork always has room to breathe.
- **Throughput limit (100k–200k ops/sec):** Redis processes all commands on a single thread — one command at a time, sequentially, never in parallel. This design keeps the code simple and eliminates locking, but it means one CPU core is all you ever get from one Redis instance. At around 100k ops/sec, that single core saturates. Adding more RAM does nothing — you need more Redis nodes (Redis Cluster) to spread commands across multiple CPU cores.

**3 signals that mean "reach for Redis Cluster":**
1. Dataset exceeds 50 GB and you need persistence — BGSAVE forks become dangerous at that size
2. Sustained throughput consistently above 100k ops/sec — single CPU core is the ceiling
3. A single Redis node going down would take down your service — Cluster gives you automatic failover across nodes

| Metric | Single-Instance Capacity | You'd Scale When... | Reach For |
|---|---|---|---|
| **Memory** | 25–50 GB (fork-safe); up to ~100 GB with persistence disabled | Dataset > 50–100 GB | `05-consistent-hashing.md` (Redis Cluster) |
| **Throughput** | 100k–200k ops/sec (single-threaded CPU is the real limit) | Sustained ops > 100k/sec | Redis Cluster (horizontal sharding) |
| **Latency** | Sub-1ms reads and writes within same region | Requirement < 0.5ms consistently | Co-locate app and cache in same AZ |
| **Hit Rate** | Target > 80% — below this, cache isn't helping | Hit rate < 80% (cache churning) | `03-caching.md` (review eviction policy) |

> **Interview use:** "The dataset is 20GB. Well under the 50GB fork-safe limit for a single Redis instance — no cluster needed. I'd use an LRU (Least Recently Used — evict the item that was accessed least recently) eviction policy since we want hot items to stay warm."

> ⚠️ **The two limits that trip people up:** Memory isn't "how much RAM can your machine have" — it's "how much can Redis hold while still being able to BGSAVE-fork safely" (answer: ~50 GB, or the dataset must fit in half the machine RAM). Throughput isn't about memory either — at ~100k ops/sec, one CPU core saturates. When you need to scale Redis, you're solving one of these two specific problems. Name which one in the interview.

---

### 🔹 Message Queue (Kafka)

**What it is:** Kafka is a distributed append-only log — think of it as a very fast, durable file that you can only write to the end of, and that multiple readers can consume independently. When a producer (your service) writes a message, it's appended to a *topic* (a named log, like a channel). Consumers (other services) read from the topic at their own pace — and critically, each consumer group gets its own independent read position, so multiple different services can all consume the same event stream without interfering with each other.

The key difference from writing directly to a database: if you write to Kafka and the downstream service (or database) is slow or down, the write still succeeds — it's sitting safely in the Kafka log. The consumer picks it up when it recovers. If you write directly to the database and the database is slow, you either wait (adding latency) or fail (losing the write). Kafka breaks that tight coupling.

**Why the limits exist:**
- **Throughput (1M msgs/sec per broker):** Kafka is built entirely around sequential disk writes — all messages are appended to the end of a log file. Sequential I/O on modern SSDs is extremely fast (the OS can batch and buffer writes efficiently). This is what allows 1M msgs/sec. The bottleneck is the disk's sequential write speed.
- **Consumer lag:** The health signal to watch is not throughput — it's lag. Lag is the gap between the newest message written to the topic and the newest message your consumer has processed. If lag is growing, your consumers can't keep up. The fix: add more consumer instances (up to the number of partitions — a partition is a Kafka log shard, and one consumer instance can handle one partition at a time).

**3 signals that mean "reach for Kafka" (vs writing directly to the DB):**
1. Write spikes that would exceed 20k WPS — Kafka absorbs the burst; the consumer writes to Postgres at a steady, manageable rate
2. A downstream service being temporarily down must NOT cause data loss — Kafka retains messages until the consumer recovers and catches up
3. Multiple independent services all need to process the same events — each gets its own consumer group reading the same topic independently

| Metric | Single-Broker Capacity | You'd Scale When... | Reach For |
|---|---|---|---|
| **Throughput** | Up to 1M messages/sec per broker | Approaching 800k msgs/sec per broker | Add brokers, increase partitions |
| **Latency** | 1-5ms end-to-end within region | — (Kafka is already fast) | Use only if async delivery is acceptable |
| **Storage** | Depends on disk provisioned; typically 4–12 TB per broker in practice | — | Adjust retention policy; Kafka is not a long-term store |
| **Consumer lag** | Should be near zero | Consumer lag growing consistently | Add consumer instances (more parallelism) |

> **Interview use:** "I'd add Kafka here specifically because the order-processing service can be temporarily down during a deployment, and we can't afford to lose payment events. With Kafka, the payment events wait in the topic until the consumer recovers — guaranteed delivery. Without Kafka, we'd need the payment service to be synchronously available for every purchase attempt."
>
> See `19-message-queues-kafka-rabbitmq.md` for the full Kafka vs RabbitMQ decision.

---

### 🔹 Application Servers

**What it is:** Application servers are the compute layer — they run your business logic: receive HTTP requests, apply rules, call the database and cache, and return responses. Unlike databases, they are *stateless* — they don't store user data between requests (all persistent state lives in the DB or cache). Because they're stateless, you can add or remove app server instances at any time without data migration or resharding. This is why horizontal scaling is cheap here and expensive at the data layer.

**The framework caveat:** The "100k+ concurrent connections" number only applies to *async/event-loop frameworks* — Node.js, Go, Spring WebFlux, Vert.x, Netty. These handle each connection with a lightweight callback or coroutine and a single thread pool. *Thread-per-request frameworks* — traditional Java/Spring MVC with Tomcat — assign one OS thread per connection. Threads consume ~1 MB of stack by default, so 500 threads ≈ 500 MB of stack memory before any application code runs. This is a hard practical ceiling of 200–500 concurrent connections per instance.

| Metric | Per-Instance Capacity | You'd Scale When... | Action |
|---|---|---|---|
| **Concurrent connections** | 100k+ for async frameworks (Node.js, Go, Spring WebFlux, Netty); 200–500 for thread-per-request Java/Tomcat | Approaching the per-instance limit | Add instances behind load balancer |
| **CPU** | 8-64 cores — almost always the bottleneck | CPU utilization > 70-80% | Horizontal scale |
| **Memory** | 64-512GB standard, up to 2TB | Memory > 80% or OOM errors | Vertical scale or add instances |
| **Startup time** | 30-60s for containerized apps | — | Factor into auto-scaling warm-up time |

> **Interview use:** "App servers are the easiest layer to scale because they're stateless — spin up more instances behind the load balancer. The complexity is at the data layer. I'd scale app servers reactively on CPU utilization and save design energy for the database and cache decisions."

---

### 🔹 Object Storage (S3, GCS, Azure Blob)

**What it is:** Object storage is a service designed to store files — PDFs, images, videos, logs, backups — not structured row-level data. There's no schema, no SQL, no index. You upload a file with a *key* (a string path like `invoices/2026/user-123/receipt.pdf`) and retrieve it later by that same key. Storage is effectively unlimited and costs fractions of a cent per GB per month. Any file that doesn't need to be queried by field values belongs here, not in a database.

**What a "prefix" means — and why it matters for throughput:** S3 rate-limits are applied per *prefix*, where prefix = the path components before the final filename. For example, `invoices/2026/` is one prefix; `invoices/2025/` is a different prefix. If all your uploads go to a single prefix (e.g., `uploads/`), they all share one rate limit bucket: 3,500 PUT/sec. If you spread uploads across 10 prefixes (e.g., `uploads/a/`, `uploads/b/`, ..., by first character of a hash), you get 10× the capacity. High-throughput upload systems add random prefix sharding specifically to work around this limit.

| Metric | Capacity | Notes |
|---|---|---|
| **Storage** | Effectively unlimited (petabyte standard) | Store ALL binary files here — PDFs, images, videos |
| **Throughput** | 3,500 PUT/sec, 5,500 GET/sec per prefix | Use multiple S3 prefixes for high-throughput uploads |
| **Latency** | 50-200ms (not for sub-10ms SLAs) | Not a database substitute — use for files, not row-level data |

> **Interview use:** "User-uploaded images go to S3 — not the database. The database holds a row with the image URL and metadata (file size, content type, upload timestamp). This keeps binary blobs out of the DB, gives us effectively unlimited storage, and lets us serve images via CDN directly from S3."
>
> See `14-document-blob-storage.md` for the metadata-DB + S3 pattern.

---

### 🔹 Network Latency — The Fixed Physics

**What it is:** Network latency is the time it takes for a message to travel from one machine to another and back (round-trip). Unlike every other limit in this note, you cannot tune or scale your way past these numbers — they are bounded by the speed of light and the physical distance between datacenters. Every decision about synchronous vs asynchronous replication, cross-region reads, and CAP trade-offs is ultimately a decision about which latency taxes you are willing to pay.

**Why this matters in an interview:** When you propose "synchronous multi-region replication," you are proposing that every single write must wait 50–150ms for the remote region to confirm before the user's request can complete. At 100ms round-trip, a system doing 10k writes/sec is waiting 1,000 seconds of accumulated round-trip time per second of real time — that's why it's impractical. Async replication (accepting eventual consistency) is the standard answer for cross-region setups.

These numbers don't change. Every architecture decision about sync vs async replication, cross-region reads, and consistency models lives inside these constraints.

| Hop | Latency | What it means for design |
|---|---|---|
| **Within same AZ** | Sub-1ms | Synchronous replication is fast and cheap |
| **Cross-AZ, same region** | 1-2ms | Sync replication still viable; adds 1-2ms to write latency |
| **Cross-region** | 50-150ms | Sync replication here means EVERY write waits 100ms+ — unacceptable for most systems → async replication → CAP decision |
| **User to CDN edge** | Sub-50ms globally | Static assets always go through CDN |

> See `34-cap-theorem-consistency-models.md` — cross-region latency is exactly why synchronous multi-region replication is impractical and why AP systems exist.

---

## 🔬 Back-of-Envelope — One Reusable Template

Every capacity estimate in an interview follows this pattern. Memorize it.

```
Step 1 — Anchor on DAU (Daily Active Users)
         DAU = how many users are active per day

Step 2 — Estimate RPS (requests per second)
         RPS = DAU × requests_per_user_per_day ÷ 86,400
         (86,400 = seconds in a day)
         Quick approximation: 1M DAU ≈ 12 RPS

Step 3 — Estimate write volume
         Writes/day = DAU × writes_per_user_per_day
         Storage/day = Writes/day × avg_object_size

Step 4 — Project over time (5-year horizon is standard)
         Total storage = Storage/day × 365 × 5

Step 5 — Compare to thresholds above → decide on infrastructure
```

**Example: Design Uber (driver location updates)**
```
DAU drivers: 5M active drivers
Location updates: every 5 seconds = 12 updates/min = 720/hr
Active driving hours: ~8/day

Writes/day = 5M × 720 × 8 = 28.8 billion writes/day
Writes/sec = 28.8B ÷ 86,400 = 333,333 WPS

→ 333k WPS is WAY above Postgres 10-20k WPS limit
→ This forces: time-series DB (write-optimized) or
  Redis GEOADD + periodic flush to Postgres
→ See 51-geospatial-indexing.md for location storage strategy
```

---

## ⚠️ The 3 Interview Anti-Patterns (with worked math)

### ❌ Anti-Pattern 1 — Premature Sharding

**What it looks like:** Candidate draws a schema, mentions rows in the millions, immediately proposes shard key.

**The worked math that kills it:**

```
Design Yelp:
  10M businesses × 1KB per business  = 10GB total
  Add reviews: 10× multiplier        = 100GB total
  5-year growth at 20%/year          ≈ 250GB in 5 years

Aurora single instance: 128 TiB = 128,000 GB
You're at 0.2% of limit. WHY would you shard?

Design a leaderboard (100k competitions × 100k users):
  100k × 100k × 40 bytes (ID + score) = 400GB
  Redis single instance: fork-safe limit ~50GB
  400GB >> 50GB → Redis Cluster needed (8+ shards)
```

**Rule:** Calculate first. Propose sharding only when the math shows you're approaching 50 TiB storage or 10k sustained WPS. See `38-sharding-strategy.md` for when you do need it.

---

### ❌ Anti-Pattern 2 — Redis "to reduce latency" on Already-Fast Queries

**What it looks like:** "I'll add Redis in front of Postgres to reduce read latency."

**When this is wrong:**

```
If your read SLA is < 10ms and your query is:
  SELECT * FROM businesses WHERE id = ? (indexed primary key)
  → Postgres indexed lookup: 1-5ms

You're already inside your SLA. Adding Redis:
  + Adds cache invalidation complexity
  + Adds stale-read risk
  + Costs money for a Redis cluster
  + Adds another failure mode

Redis IS justified when:
  → Read SLA < 1ms (Redis is sub-1ms; Postgres is 1-5ms)
  → The query is expensive (JOINs, aggregations, full-text)
  → The same data is read millions of times (social graph, hot profiles)
  → You need to serve entire datasets from memory (session store)
```

See `03-caching.md` for the 5 caching strategies and when each applies.

---

### ❌ Anti-Pattern 3 — Kafka for Normal Write Loads

**What it looks like:** "We have 5k writes per second — I'll buffer them through Kafka."

**The math:**

```
5k WPS vs Postgres capacity: 10-20k WPS
→ You're at 25-50% of capacity. Postgres handles this directly.

Adding Kafka at 5k WPS means:
  + Producer writes to Kafka topic
  + Consumer reads from Kafka
  + Consumer writes to Postgres
  + Net result: same 5k WPS hitting Postgres, plus:
    - Kafka cluster to operate
    - At-least-once delivery complexity
    - Consumer lag monitoring
    - Reprocessing logic for failures
```

**Kafka IS justified when:**
- Writes spike above 20k WPS (Postgres saturation) — queue absorbs the burst
- A downstream service being down must NOT lose writes (guaranteed delivery)
- You need multiple independent consumers on the same event stream
- You're building event sourcing or audit log patterns

See `19-message-queues-kafka-rabbitmq.md` for the full decision tree.

---

## 🧭 The "Justify It" Test — Before Adding Any Infrastructure

Ask these 3 questions. If you can't answer all 3, don't add the component.

```
1. DOES THE MATH FORCE THIS?
   Show the calculation: estimated load vs. single-instance threshold.
   If the single instance handles it → don't add complexity.

2. WHAT SPECIFIC PROPERTY AM I BUYING?
   Be precise: throughput? latency? durability? geographic distribution?
   decoupling? guaranteed delivery?
   "Scalability" is not an answer — name the exact dimension.

3. WHAT AM I PAYING IN COMPLEXITY?
   Every distributed component adds:
   - A new failure mode
   - Eventual consistency (if async)
   - Ops overhead (monitoring, alerting, runbooks)
   - Engineering time for integration
   Is the property from #2 worth that cost?
```

---

## 🔬 Interview Q&As

### Q: "Why don't we just shard our database from the start?"

> Because sharding introduces cross-shard query complexity, distributed transaction problems, and resharding pain — all before you need it. A single Aurora instance handles 128 TiB and 50k read TPS. Most companies with 100M users never hit those limits on a single table. Premature sharding is engineering debt you pay forever. Do the math first; shard when the numbers force it.

### Q: "Our system will have 10 million users. How should we scale?"

> First, let me estimate the actual load. If 10M users each make 50 API calls/day: 10M × 50 ÷ 86,400 ≈ 5,800 RPS. For writes at 10% of that: 580 WPS. A single Postgres handles 10-20k WPS. We don't need to shard on day one. I'd start with a primary + one read replica for high availability, and a Redis cache for hot reads. We scale further only when metrics show a specific bottleneck.

### Q: "You said you'd use Kafka. Why not just write directly to the database?"

> If writes are under 20k WPS and downstream services are reliable, direct DB writes are simpler and correct. I'd add Kafka specifically when: (a) writes spike unpredictably above DB capacity, (b) a downstream consumer being temporarily down must not lose events, or (c) multiple services need to independently consume the same event stream. Without one of those three conditions, Kafka adds complexity for no gain.

### Q: "How do you think about cross-region latency in your design?"

> Cross-region latency is a physical constant: 50-150ms. If I'm doing synchronous replication across regions, every write waits that long. At 100ms round-trip, that's a 10x write latency hit. That's why most multi-region systems choose asynchronous replication (AP systems in CAP terms) — accepting eventual consistency to avoid that latency tax. The exception is payments and ledgers where consistency is non-negotiable. See `34-cap-theorem-consistency-models.md` — this is exactly the PACELC trade-off.

---

## 🧾 TL;DR

> "Do the math. A single Postgres handles 10-20k WPS and 50 TiB. A single Redis handles 100k ops/sec and ~50 GB fork-safe (CPU saturates before memory — and fork blows up large datasets). A single Kafka broker handles 1M msgs/sec. Most systems never break these limits. Reach for sharding, Redis cluster, or Kafka only when the calculation shows the single-instance threshold is genuinely crossed — not because the problem 'feels big'."

---

## 🔗 Related Concepts

| Threshold crossed | The note to reach for |
|---|---|
| DB writes > 10k WPS | `../../Core-Architecture/Database-Core/38-sharding-strategy.md` |
| DB reads overloaded | `../../Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md` |
| Cache dataset > 50 GB | `./05-consistent-hashing.md` |
| Write latency < 1ms needed | `./03-caching.md` |
| Write spikes or guaranteed delivery | `../../Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` |
| Counter writes bottleneck | `./09-sharded-counters.md` |
| Cross-region traffic | `../../Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md` |
| Consistency model decision | `../../Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md` |
| File/blob storage | `../../Foundations/Data-Fundamentals/14-document-blob-storage.md` |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2, 2026 | Created. 2026 hardware baselines for Postgres, Redis, Kafka, app servers, object storage, and network. Per-component scale triggers with cross-references. Back-of-envelope template. 3 anti-patterns with worked math. "Justify It" test. |
| Jul 3, 2026 | All component sections expanded with explanatory blocks: "What it is / Why the limits exist / 3 signals / Interview use" added to all 6 components (Postgres, Redis, Kafka, App Servers, S3, Network). BGSAVE fork mechanism, single-threaded CPU wall, Kafka consumer lag, S3 prefix sharding, and thread-per-request vs async framework distinction all explained in plain English. |
