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
| TiB | Tebibyte — 2⁴⁰ bytes ≈ 1.1 TB; AWS uses TiB for storage limits | Aurora supports up to 256 TiB per cluster |
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

| Metric | Single-Instance Capacity | You'd Scale When... | Reach For |
|---|---|---|---|
| **Storage** | Up to 64 TiB (RDS), 256 TiB (Aurora) | Dataset approaching 50 TiB | `38-sharding-strategy.md` |
| **Read TPS** | ~50k TPS cached; 5-30ms disk read latency | Sustained reads > 40k TPS | `29-db-replication-failover.md` (add read replicas) |
| **Write TPS** | 10-20k TPS simple inserts/updates | Sustained writes > 10k WPS | `38-sharding-strategy.md` |
| **Connections** | 5-20k concurrent | App is exhausting connection pool | `16-connection-pooling-db-performance.md` |
| **Cross-region** | Single-region primary | Users across continents need < 50ms | `40-multi-region-geo-failover.md` |
| **Consistency** | Strong by default | Multi-region async replication needed | `34-cap-theorem-consistency-models.md` |

> **Interview use:** "Our dataset is ~500GB. A single Aurora instance handles 256 TiB — we're at 0.2% of its limit. No sharding needed. I'd add a read replica for high availability."

---

### 🔹 In-Memory Cache (Redis / ElastiCache)

| Metric | Single-Instance Capacity | You'd Scale When... | Reach For |
|---|---|---|---|
| **Memory** | Up to 1 TB on memory-optimized nodes | Dataset > 1 TB | `05-consistent-hashing.md` (Redis Cluster) |
| **Throughput** | 100k–200k ops/sec (single-threaded CPU is the real limit) | Sustained ops > 100k/sec | Redis Cluster (horizontal sharding) |
| **Latency** | Sub-1ms reads and writes within same region | Requirement < 0.5ms consistently | Co-locate app and cache in same AZ |
| **Hit Rate** | Target > 80% — below this, cache isn't helping | Hit rate < 80% (cache churning) | `03-caching.md` (review eviction policy) |

> **Interview use:** "The dataset is 400GB. Single Redis instance handles up to 1TB — no cluster needed. I'd use an LRU (Least Recently Used) eviction policy since we want hot items to stay warm."

> ⚠️ **Redis is single-threaded.** You'll hit CPU before you hit memory. At ~100k ops/sec on a single core, the CPU saturates before the 1TB RAM limit. Add Redis Cluster to spread CPU load, not just memory.

---

### 🔹 Message Queue (Kafka)

| Metric | Single-Broker Capacity | You'd Scale When... | Reach For |
|---|---|---|---|
| **Throughput** | Up to 1M messages/sec per broker | Approaching 800k msgs/sec per broker | Add brokers, increase partitions |
| **Latency** | 1-5ms end-to-end within region | — (Kafka is already fast) | Use only if async delivery is acceptable |
| **Storage** | Up to 50 TB per broker | — | Adjust retention policy |
| **Consumer lag** | Should be near zero | Consumer lag growing consistently | Add consumer instances (more parallelism) |

> **When to reach for Kafka at all** (vs just writing to DB directly):
> - You need **guaranteed delivery** if a downstream service is temporarily down
> - You're doing **event sourcing** (immutable log of all state changes)
> - Write spikes exceed 20k WPS (Postgres limit) — queue absorbs the burst
> - You need to **fan out** one event to multiple consumers independently
>
> See `19-message-queues-kafka-rabbitmq.md` for the full Kafka vs RabbitMQ decision.

---

### 🔹 Application Servers

| Metric | Per-Instance Capacity | You'd Scale When... | Action |
|---|---|---|---|
| **Concurrent connections** | 100k+ (well-tuned) | Approaching 100k connections/instance | Add instances behind load balancer |
| **CPU** | 8-64 cores — almost always the bottleneck | CPU utilization > 70-80% | Horizontal scale |
| **Memory** | 64-512GB standard, up to 2TB | Memory > 80% or OOM errors | Vertical scale or add instances |
| **Startup time** | 30-60s for containerized apps | — | Factor into auto-scaling warm-up time |

> **The implication:** Because app servers are stateless and cheap to spin up (30-60s), horizontal scaling here is low-risk. Scale out aggressively. Reserve distributed DB/cache complexity for when the data layer genuinely forces it.

---

### 🔹 Object Storage (S3, GCS, Azure Blob)

| Metric | Capacity | Notes |
|---|---|---|
| **Storage** | Effectively unlimited (petabyte standard) | Store ALL binary files here — PDFs, images, videos |
| **Throughput** | 3,500 PUT/sec, 5,500 GET/sec per prefix | Use multiple S3 prefixes for high-throughput uploads |
| **Latency** | 50-200ms (not for sub-10ms SLAs) | Not a database substitute — use for files, not row-level data |

> See `14-document-blob-storage.md` for the metadata-DB + S3 pattern.

---

### 🔹 Network Latency — The Fixed Physics

These don't change. Every architecture decision about sync vs async replication, cross-region reads, and consistency models lives inside these constraints.

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

Aurora single instance: 256 TiB = 256,000 GB
You're at 0.1% of limit. WHY would you shard?

Design a leaderboard (100k competitions × 100k users):
  100k × 100k × 40 bytes (ID + score) = 400GB
  Redis single instance: up to 1TB
  You fit. No cluster needed.
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

> Because sharding introduces cross-shard query complexity, distributed transaction problems, and resharding pain — all before you need it. A single Aurora instance handles 256 TiB and 50k read TPS. Most companies with 100M users never hit those limits on a single table. Premature sharding is engineering debt you pay forever. Do the math first; shard when the numbers force it.

### Q: "Our system will have 10 million users. How should we scale?"

> First, let me estimate the actual load. If 10M users each make 50 API calls/day: 10M × 50 ÷ 86,400 ≈ 5,800 RPS. For writes at 10% of that: 580 WPS. A single Postgres handles 10-20k WPS. We don't need to shard on day one. I'd start with a primary + one read replica for high availability, and a Redis cache for hot reads. We scale further only when metrics show a specific bottleneck.

### Q: "You said you'd use Kafka. Why not just write directly to the database?"

> If writes are under 20k WPS and downstream services are reliable, direct DB writes are simpler and correct. I'd add Kafka specifically when: (a) writes spike unpredictably above DB capacity, (b) a downstream consumer being temporarily down must not lose events, or (c) multiple services need to independently consume the same event stream. Without one of those three conditions, Kafka adds complexity for no gain.

### Q: "How do you think about cross-region latency in your design?"

> Cross-region latency is a physical constant: 50-150ms. If I'm doing synchronous replication across regions, every write waits that long. At 100ms round-trip, that's a 10x write latency hit. That's why most multi-region systems choose asynchronous replication (AP systems in CAP terms) — accepting eventual consistency to avoid that latency tax. The exception is payments and ledgers where consistency is non-negotiable. See `34-cap-theorem-consistency-models.md` — this is exactly the PACELC trade-off.

---

## 🧾 TL;DR

> "Do the math. A single Postgres handles 10-20k WPS and 50 TiB. A single Redis handles 100k ops/sec and 1TB. A single Kafka broker handles 1M msgs/sec. Most systems never break these limits. Reach for sharding, Redis cluster, or Kafka only when the calculation shows the single-instance threshold is genuinely crossed — not because the problem 'feels big'."

---

## 🔗 Related Concepts

| Threshold crossed | The note to reach for |
|---|---|
| DB writes > 10k WPS | `../../Core-Architecture/Database-Core/38-sharding-strategy.md` |
| DB reads overloaded | `../../Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md` |
| Cache dataset > 1TB | `./05-consistent-hashing.md` |
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
