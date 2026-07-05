# Pattern Deep Dive: Scaling Reads

> **Read this when:** You need to deeply understand how to scale the read path — what each strategy does, when to pick it, and how to defend your choices under interviewer pressure.
> **Pre-interview refresh:** Use `Reference/01-scaling-reads.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

Your database is the bottleneck — but only on reads. Writes are fine. Classic symptoms:

- DB CPU spikes during peak traffic but write volume is low
- Read latency p99 is high; write latency is fine
- Adding more application servers doesn't help — they all hammer the same DB
- A single "hot" record (popular product, viral post, trending URL) causes query storms

The root cause: a single Postgres primary handles ~10–20K queries/sec before saturating.
At that point, adding application servers actually makes things worse — more app servers = more DB connections = more DB pressure.

The fix is not more DB capacity. The fix is **fewer DB reads** — by serving reads from faster, cheaper, more scalable layers.

---

## 💡 Core Insight

**Reads are idempotent and tolerant of staleness.** This single property enables every strategy in this pattern.

Because reading the same data twice produces the same result, you can:
- Serve reads from **copies** of the data (replicas, caches)
- **Precompute** read results and store them separately (materialized views, denormalized stores)
- Distribute reads **geographically** (CDN, edge caches)

Writes must be singular and consistent — one source of truth. Reads can be plural, distributed, and eventually consistent.

> **KEY INSIGHT:** "The write path and read path have different constraints. Scale them independently."

---

## 🗂️ The 4 Strategies (Simple → Complex)

---

### Strategy 1 — Cache Layer (Redis)

🧠 **Mental model:** Amazon product page — the same product page is read millions of times per day. One DB read → cached for 5 minutes → 99.9%+ of traffic never touches DB.

The first and highest-ROI move. An in-memory cache sits between your application and your database and absorbs the hot read traffic before it reaches the DB.

**When to use:**
- Read:write ratio > 5:1
- Hot data fits in memory (Pareto rule: 20% of keys = 80% of requests)
- Stale reads are acceptable (seconds to minutes of lag is fine)
- Access pattern is key-value (not complex joins)

**When NOT to use:**
- Write-heavy data (cache thrash — keys invalidated faster than they're used)
- Data requiring strict consistency (bank balances, seat inventory at checkout)
- Highly random access patterns where every request hits a different key (hit rate ≈ 0%)

---

#### The 3 Cache Write Patterns

**Cache-Aside (Lazy Loading)** — most common, lowest complexity

```
Steps in plain English:
1. App checks cache for the key.
2. On HIT: return cached value immediately.
3. On MISS: app queries DB, writes result to cache with TTL, returns value.

Next request for the same key hits cache (HIT).
```

```
     App Server
         │
    ┌────▼────┐
    │  Redis  │──── HIT ────▶ return value (fast path, ~1ms)
    │  Cache  │
    └────┬────┘
         │ MISS (first time or TTL expired)
    ┌────▼───────┐
    │  Postgres  │──▶ write result to cache ──▶ return value (~20ms)
    │  Primary   │
    └────────────┘

Cache TTL: 60s–5min for most data
Target hit rate: > 90% for meaningful DB reduction
```

**Write-Through** — higher consistency, more writes

```
Steps in plain English:
1. On every write: app writes to cache AND DB synchronously.
2. Cache always reflects latest state.
3. Reads always hit cache (cold-start aside).

Trade-off: every write is slower (two writes); cache fills with cold data too.
Use when: reads must always be current and write latency is acceptable.
```

**Write-Behind (Write-Back)** — highest write performance, highest risk

```
Steps in plain English:
1. App writes to cache only (synchronous).
2. Background process flushes dirty keys to DB asynchronously.
3. Reads from cache (always fresh from app's perspective).

Trade-off: if cache crashes before flush, writes are LOST.
Use when: losing recent writes is acceptable (view counters, analytics).
Never use for: financial transactions, inventory, bookings.
```

---

#### Key Failure Mode: Thundering Herd

When a hot cache key expires, thousands of simultaneous requests all miss cache and hit the DB at the same moment. DB is overwhelmed in seconds.

**Fix 1 — Cache Locking:** Only one process is allowed to rebuild the cache key. Others wait (or serve stale while rebuilding).

**Fix 2 — Stale-While-Revalidate:** Serve the stale cached value to all requests immediately. One background process rebuilds in parallel. Users see data that's slightly stale (seconds) but never a cache miss storm.

**Fix 3 — Jittered TTL:** Add random jitter to TTL so keys don't all expire at the same moment. Instead of all keys expiring at T+60s, they expire uniformly between T+55s and T+65s.

**Fix 4 — Request Coalescing:** When N concurrent requests all miss the same cache key simultaneously, only one is allowed to proceed to DB — the rest wait and share the result. Implemented via a short-lived lock (`SET rebuild:product:123 NX EX 5`). Reduces N DB round-trips to 1 on any hot-key miss burst.

**Fix 5 — Probabilistic Early Refresh:** Worker proactively refreshes a key before TTL expires, with probability increasing as TTL runs low. Popular keys are continuously refreshed and never actually expire cold. No thundering herd because there is no simultaneous expiry.

**Cache versioning (prevent stale-write-back):** On invalidation, bump a version counter (`product:123:version`). Cache data lives at `product:123:v{version}`. A slow-read in-flight computing an old value writes back to the old version key — the new version key is unaffected. Guarantees a concurrent write can never be overwritten by a stale reader that was racing it.

---

### Strategy 2 — Read Replicas

🧠 **Mental model:** Instagram runs 30+ Postgres read replicas per primary. Each user's feed read hits a replica. Primary only handles writes (new posts, follows, likes).

Add database replicas that serve all read traffic. The primary handles only writes. Replicas receive changes via the replication log (WAL in Postgres, binlog in MySQL).

**When to use:**
- Cache hit rate < 80% (too many unique queries to cache effectively)
- Data volume is larger than available RAM (can't cache it all)
- Need full SQL capability: complex joins, aggregations, window functions
- Read load is sustained, not just spiky hot-key traffic

**When NOT to use:**
- The actual problem is N+1 queries — fix the query first, then add replicas
- Writes are the bottleneck — replicas don't help writes at all
- You need read-after-write consistency for every single operation (replicas lag)

---

#### Replication Lag: The Critical Trade-off

Postgres async replication: replicas typically lag **10ms–2s** behind primary under normal load. Under heavy write pressure, lag can grow to minutes.

**The read-after-write problem:**
```
Timeline:
T=0:    User writes a comment → goes to PRIMARY ✅
T=10ms: User reads their comment → load balancer routes to REPLICA
T=10ms: Replica hasn't received the write yet → user sees empty ❌
T=500ms: Replica catches up → comment visible ✅
```

**Fix:** Route "read-your-own-writes" to primary. Detect which user just wrote (via session or header) and for a short window (1–2s after their write), send their reads to primary.

```
                    ┌──────────────────────────────────────────────┐
                    │              Application Tier                 │
                    │                                              │
                    │  Read Router Logic:                          │
                    │  if (user.justWrote within 2s) → PRIMARY    │
                    │  else                          → REPLICA     │
                    └──────────────┬───────────────────┬──────────┘
                                   │ writes            │ reads
                    ┌──────────────▼──┐    ┌───────────▼──────────────┐
                    │  Postgres       │    │  Read Replicas (N)        │
                    │  PRIMARY        │───▶│  Replica 1  Replica 2     │
                    │  (all writes)   │    │  Replica 3  Replica 4     │
                    └─────────────────┘    └──────────────────────────┘
                    Replication lag: 10ms–2s async
                    Scale: add replicas linearly to multiply read capacity
```

**Horizontal scaling:** Each replica added doubles your read capacity (roughly). You can run 5, 10, 20 replicas if needed. Read replicas are the primary scaling mechanism for relational workloads at companies like Instagram, Pinterest, Shopify.

---

### Strategy 3 — Dedicated Read Store (Denormalized / Purpose-Built)

🧠 **Mental model:** Yelp stores restaurants in Postgres (source of truth) but serves search queries from Elasticsearch. Postgres cannot do inverted-index full-text search at Yelp's query rate. Same data, different read store for a different query pattern.

When your read query pattern fundamentally doesn't match your write schema, add a purpose-built data store optimized for reads.

**When to use:**
- Full-text search is required (Elasticsearch — relational DBs can't compete on inverted index performance)
- Time-series data with range queries (Cassandra, InfluxDB — columnar storage is 10x more efficient)
- Simple key-value access at very high throughput (DynamoDB — O(1) lookups, no joins needed)
- Need multiple different query patterns on the same data simultaneously

**How it works:**
- Writes always go to the primary DB (source of truth)
- Change events flow to the read store via CDC (Change Data Capture — captures DB row changes as events) or Kafka
- Read store maintains its own schema optimized for its query pattern
- Reads for that query type go to the read store — never to primary DB

```
Write Path:   App ──▶ Postgres (primary, source of truth)
                           │
                           │ CDC events (Debezium) or Kafka messages
                           ▼
              ┌─────────────────────────────────────────────┐
              │  Kafka Topic: "product-updates"              │
              └───────────────────┬─────────────────────────┘
                                  │
              ┌───────────────────▼──────────────────────────┐
              │           Consumers (async)                   │
              ├──────────────────────────────────────────────┤
              │  ES Indexer          Cassandra Writer         │
              │  (for search)        (for time-series)        │
              └──────┬───────────────────────┬───────────────┘
                     ▼                       ▼
              ┌─────────────┐       ┌────────────────┐
              │Elasticsearch│       │   Cassandra    │
              │(search API) │       │ (time-series)  │
              └─────┬───────┘       └───────┬────────┘
                    │                       │
Read Path:   App reads ◀───────────────────┘
             from the right store for the query type

Eventual consistency: read store lags ~100ms–5s behind primary.
```

**Key challenge: synchronization failures**
If Kafka consumer falls behind (lag grows), read store diverges from primary. Mitigations:
- Monitor consumer lag — alert if lag > 10s
- Fallback: route critical reads to primary DB if read store lag exceeds threshold
- Periodic reconciliation job to detect and repair divergence

---

### Strategy 4 — CDN / Edge Caching

🧠 **Mental model:** Netflix — the same episode file cached at 3,000+ CDN PoPs worldwide. S3 stores it once; CDN edge serves 200M users without the origin seeing repeat requests for the same content.

Push content to the network edge, geographically close to users, so requests never reach your origin servers.

**When to use:**
- Static content: images, videos, JS bundles, CSS (never changes per user)
- Semi-static content: product pages, news articles (updated hourly, same for all users)
- Globally distributed users where network latency matters
- Origin read load from geographic distribution

**When NOT to use:**
- User-specific content (your profile, your cart) — can't cache per-user at CDN layer without complexity
- Highly dynamic content (price updates every second) — TTL invalidation too slow to keep up
- Content requiring server-side auth per request — CDN bypasses application-level auth logic

```
With CDN:

User (Mumbai) ─────▶ CDN Edge (Mumbai) ──HIT──▶ return (< 10ms latency)
                                          │
                                          MISS (first request or TTL expired)
                                          │
User (NYC)    ─────▶ CDN Edge (NYC)    ──┤──▶ Origin (US-East) ──▶ DB
                                          │    (CDN caches result for next user)
User (London) ─────▶ CDN Edge (London) ──┘

Result:
  80–95% of reads served from edge
  Edge latency: ~10ms vs ~200ms to origin
  Origin load reduced by 80-95%
```

**CDN invalidation:** When you update a product image or page, CDN nodes still serve the old cached version until TTL expires. Fix: versioned URLs (`/images/product-123-v2.jpg`) — old URL serves old image, new URL immediately serves new image, no invalidation needed.

---

## 🧭 Decision Sequence (How to Escalate)

```
START: Reads are the bottleneck (DB CPU high, read latency high)

Step 0 ── Profile and index first (free wins — always do this before adding infra)
          Run EXPLAIN ANALYZE on slow queries.
          Seq Scan on a 100M-row table? → Missing index. Adding it takes minutes
          and can drop latency from 2s to 5ms — orders-of-magnitude improvement.
          N+1 query? (50 queries per API call instead of 1 JOIN) → Fix the query.
          Only proceed to cache/replicas when queries are optimal.

Step 1 ── Add Redis cache (cache-aside pattern)
          │  Works if: cache hit rate > 90%
          │  Takes: 1 engineer-day to add, immediate impact
          └─ Not enough? Cache hit rate < 80% or data > RAM?
                  │
Step 2 ── Add read replicas (1 replica → 4 replicas as needed)
          │  Works if: queries are relational, replica lag is acceptable
          │  Takes: 1 hour to provision; monitor replication lag
          └─ Not enough? Query pattern doesn't match schema?
                  │
Step 3 ── Add dedicated read store
          │  (Elasticsearch for search, Cassandra for time-series,
          │   DynamoDB for KV at scale)
          │  Works if: query pattern maps to read store's model
          │  Takes: 2–4 weeks to set up CDC pipeline + dual-write
          └─ Global users and static/semi-static content?
                  │
Step 4 ── Add CDN layer
          Works for: static/semi-static content globally
          Takes: 1 day to configure CloudFront/Akamai

IMPORTANT: Steps are ADDITIVE.
A mature system runs cache + replicas + dedicated read store + CDN simultaneously.
Each layer serves different traffic; none replaces the others.
```

---

## 🎨 Visual — Full Architecture (All 4 Strategies)

```
                              Internet / Mobile Clients
                                         │
                         ┌───────────────▼───────────────┐
                         │      CDN (CloudFront)          │ ← Strategy 4
                         │   static + semi-static content │   images, JS, articles
                         └───────────────┬───────────────┘
                                         │ cache miss only
                         ┌───────────────▼───────────────┐
                         │        Load Balancer           │
                         └───────────────┬───────────────┘
                                         │
                    ┌────────────────────▼────────────────────┐
                    │           Application Tier               │
                    │       (Read Router logic inside)        │
                    └──────┬─────────────┬────────────┬───────┘
                           │             │            │
              ┌────────────▼──┐  ┌───────▼──────┐  ┌─▼──────────────┐
              │  Redis Cache  │  │ Read Replicas│  │ Elasticsearch  │
              │  (hot data)   │  │  (SQL reads) │  │ / DynamoDB     │ ← Strategy 3
              │               │  │              │  │ (search / KV)  │
              └───────────────┘  └──────┬───────┘  └───────┬────────┘
                 ↑ Strategy 1           │ replication       │ CDC/Kafka
                                ┌───────▼───────────────────┘
                                │  Postgres PRIMARY          │
                                │  (all writes, source of    │
                                │   truth, reads only for    │
                                │   read-your-own-writes)    │
                                └────────────────────────────┘
                                        ↑ Strategy 2

KEY INVARIANT:
   Write path is always singular (one primary). Read path is plural and distributed.
   Add each read layer only when the previous layer becomes saturated.
   Later layers add complexity — justify before adding.
```

---

## 🔬 Interview Q&A

### Q: "Your cache hit rate is 95% but you're still seeing DB overload. Why?"

> 5% of 10M reads/sec = 500K cache misses/sec hitting your DB — that's enough to overwhelm a Postgres primary (cap: 10–20K QPS). At high absolute volumes, even a 95% hit rate generates DB-saturating miss traffic. Fix: increase TTL to reduce miss rate further, or precompute and warm cache proactively. If miss traffic is from write-heavy keys (frequent updates causing cache invalidation), the problem is actually on the write path — see Scaling Writes pattern.

---

### Q: "A user posts a comment. They immediately refresh and don't see it. What's happening?"

> Read-after-write inconsistency from replica lag. Write went to primary; the immediate read hit a replica that hasn't received the replication yet (lag can be 10ms–2s). Fix: route "read-your-own-writes" to primary for a short window after the user's write (detect via session flag or header). Trade-off: increases primary read load for recently-writing users. Alternative: after writing to DB, also write to cache with the new value — user's next read hits cache and sees their own write.

---

### Q: "Your Redis node crashes. What happens to your system?"

> All reads that were hitting cache now miss and hit DB simultaneously — thundering herd. The DB will saturate within seconds if the cache was absorbing significant load. Redis must be treated as a reliability dependency at scale, not just a performance optimization. Mitigations: (1) Redis Sentinel or Redis Cluster for automatic failover with replicas. (2) Circuit breaker — if cache is unavailable, shed a percentage of read traffic rather than all hitting DB. (3) Cache warming — pre-populate top N hot keys from DB before taking live read traffic after cache restart.

---

### Q: "When would you skip caching and go straight to read replicas?"

> When cache hit rate would be low: (1) Access pattern is highly random — every request hits a different key (ad targeting, fraud scoring). (2) Data is too large to fit in RAM and the working set is not concentrated. (3) Queries are complex SQL (joins, aggregations) — cache would need to store full result sets, invalidation becomes a nightmare. In these cases, read replicas give you full SQL at horizontal scale without the invalidation complexity.

---

### Q: "How do you handle cache invalidation when a product's price changes?"

> Invalidation is the hard problem. Options in order of complexity: (1) TTL expiry — accept up to 60s stale price. Simple, widely used, usually acceptable. (2) Write-through — on price update, simultaneously write to DB and delete/update cache key. Consistent but couples write path to cache. (3) Event-driven invalidation — price change event on Kafka; cache invalidation consumer deletes the key. Decoupled and scalable but adds latency. Most production systems use TTL (60s) for prices because a slightly stale price is acceptable and the simplicity is worth it.

---

### Q: "You have 20 read replicas. The primary crashes. What's the impact?"

> Replicas become read-only islands — they serve reads but can't take writes. Write traffic fails 100%. Read traffic still works from replicas (with potentially stale data as replication stream stops). Recovery: orchestrator (Patroni, AWS RDS Multi-AZ) promotes the least-lagged replica to primary. Async replicas can have data loss = lag at crash time. Key lesson: read replicas solve read scaling; they don't solve write HA. Write HA requires a separate mechanism — synchronous replicas, Multi-AZ, or consensus-based HA (Raft).

---

### Q: "Your Elasticsearch is 10 seconds behind the primary. A user searches for a product they just listed. What do they see?"

> They won't find it for up to 10 seconds. This is expected behavior for an eventually-consistent read store. Acceptable solution: show the user a UI message: "Your listing is being indexed. It may take a few seconds to appear in search." Unacceptable for: financial records, inventory counts. The key point: eventual consistency in read stores is a product decision — the engineering team must surface the lag to product and agree on the UX implications before building.

---

### Q: "When does adding a dedicated read store make sense vs just adding more replicas?"

> Not about scale — about query pattern mismatch. Read replicas give you the same query capability as the primary (full SQL). If you need a DIFFERENT query capability — full-text search, geospatial queries, time-series aggregation — replicas can't help regardless of how many you add. The trigger for a dedicated read store is: "My primary DB fundamentally cannot answer this query efficiently." Scale is secondary to query pattern fit.

---

### Q: "How would you scale reads for a leaderboard serving 10M DAU?"

> Leaderboard reads are an interesting case: data changes frequently (score updates) but the read query is always the same (top-N). Strategy: (1) Redis Sorted Set (ZSET) as the leaderboard store — O(log N) score updates, O(log N) range queries for top-N, all in-memory at microsecond latency. (2) Persist to Postgres asynchronously for durability. (3) Cache the rendered top-100 response with a 5s TTL — most users read the same top-100, not a personalized view. This is a Scaling Reads + Dealing with Contention hybrid: the read path uses Redis (Scaling Reads); the write path uses atomic increments (Contention).

---

### Q: "Product team wants 'X people viewed this item in the last hour.' How do you implement this without killing your DB?"

> Never store view counts with per-view writes to the primary DB — that's a hot write anti-pattern on popular items. Instead: (1) Write view events to Kafka (fire and forget, no DB write on the hot path). (2) A consumer aggregates counts per item per 1-hour window into a Redis counter with 1h TTL. (3) Read path reads from Redis — single O(1) key lookup. The popular product page never touches your primary DB for view counts, regardless of traffic. This pattern appears in Design YouTube, Design Ticketmaster, Design Amazon Product Page.

---

## ⚠️ Anti-patterns

- **Adding read replicas when the problem is N+1 queries.** If your service makes 50 DB queries per API call, adding replicas multiplies your read capacity but doesn't fix the fact that you're making 50 queries. Each replica still gets 50 queries per request. Profile your query patterns first. N+1 is almost always the first fix — it's free and has orders-of-magnitude impact.

- **Caching after a join.** Caching the result of a `JOIN users LEFT JOIN orders ON ...` is fragile: when any user row or order row changes, you must invalidate every cached result that referenced those rows. Cache individual entities (`user:{id}`, `order:{id}`) and do the join in the application layer. Per-entity invalidation is simple; per-result-set invalidation is exponentially complex.

- **Treating cache as source of truth.** If your application logic relies on a key existing in Redis (not as an optimization but as the authoritative record), you've created a durability vulnerability. Redis without persistence (`appendonly no`) loses all data on restart. Redis without replication loses data on node crash. Cache is an optimization layer. Your database is the truth. If they disagree, the DB wins — always.

---

## 🗺️ Problems Map

| Interview Problem | Why Scaling Reads Applies | Primary Strategy |
|---|---|---|
| Design URL Shortener | URL lookup is ~100% reads; write happens once at creation | Redis cache (short URL → destination) |
| Design Twitter / X | Timeline reads >> tweet writes; 10:1 ratio minimum | Redis + read replicas + Elasticsearch |
| Design Product Catalog | Product pages read millions of times; updated rarely | CDN + Redis + read replicas |
| Design Search System | All search queries are reads; index is written by ingestion pipeline | Elasticsearch as dedicated read store |
| Design Metrics Dashboard | Aggregate reads on time-series; writes are constant stream | Cassandra/InfluxDB as read store |
| Design E-commerce PDP | Product detail = same content for all users | CDN + Redis |
| Design Leaderboard | Top-N query is constant; scores update continuously | Redis Sorted Set + TTL-cached response |
| Design YouTube | Video metadata reads >> uploads; view counts are hot-write | Redis + CDN for video + Kafka for counts |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **Caching internals** (eviction policies, TTL, Redis BGSAVE, thundering herd in depth) → `../../Foundations/Performance-and-Scale/03-caching.md`
- **Read replicas, replication lag, failover** → `../../Core-Architecture/Resilience-and-Fault-Tolerance/29-db-replication-failover.md`
- **CQRS pattern** (formalized read/write model separation) → `../../Production-Grade/System-Design-Patterns/31-cqrs-read-write-separation.md`
- **Elasticsearch and inverted index** → `../../Production-Grade/Performance-Optimization/32-elasticsearch-inverted-index.md`
- **CDN and edge caching** → `../../Production-Grade/Performance-Optimization/28-cdn-edge-caching.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Pilot pattern for DeepDive + Reference split. |
| July 2026 | Added Step 0 (index/profile first) to decision sequence. Added 🧠 mental model anchors per strategy. Added cache versioning, request coalescing, probabilistic early refresh to thundering herd section. |
