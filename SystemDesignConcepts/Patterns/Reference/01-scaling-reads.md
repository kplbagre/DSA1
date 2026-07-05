# Scaling Reads — Quick Reference

> **Read this:** 30 min before an interview involving read-heavy systems.
> **Deep study:** `DeepDive/01-scaling-reads.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **DB is the bottleneck but only on reads** — DB CPU high, read latency high, adding more app servers makes it worse.

Trigger words: "serve 100K reads/sec", "popular product page", "10M DAU", "hot record", "read-heavy".

---

## 🧭 Decision Sequence

```
START: Read latency high, DB CPU saturated

Step 1 → Add Redis cache (cache-aside)
         Works if hit rate > 90%? → Done.
         Hit rate < 80% or data > RAM?

Step 2 → Add read replicas (route reads away from primary)
         Works if query is relational SQL? → Done.
         Query type doesn't match DB schema?

Step 3 → Add dedicated read store
         (Elasticsearch for search, Cassandra for time-series, DynamoDB for KV)
         Global users + static/semi-static content?

Step 4 → Add CDN (edge caching)

IMPORTANT: Steps are additive — mature systems run all 4 simultaneously.
Each layer serves different traffic. Later layers don't replace earlier ones.
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Redis Cache** | Read:write > 5:1, hot data fits in RAM, stale OK | Write-heavy keys, strict consistency, random access |
| **Read Replicas** | Cache hit rate < 80%, data > RAM, need full SQL | Problem is N+1 queries, writes are the bottleneck |
| **Dedicated Read Store** | Query type mismatches DB (search, time-series, KV) | Small dataset, query is standard relational |
| **CDN** | Static/semi-static content, global users, same content for all | Per-user content, highly dynamic, auth per request |

**Key numbers to remember:**
- Postgres primary cap: ~10–20K QPS
- Redis: ~100K–1M ops/sec
- Replication lag: 10ms–2s (async), can grow to minutes under write pressure
- Target cache hit rate: > 90% for meaningful DB reduction
- CDN offload: 80–95% of reads for cacheable content

---

## 🎨 Key Architecture Diagram

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
                │       (Read Router logic inside)         │
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
                            │  (writes + read-your-own-  │
                            │   writes only)             │
                            └────────────────────────────┘
                                    ↑ Strategy 2

KEY INVARIANT:
   Write path is always singular (one primary). Read path is plural and distributed.
   Add each read layer only when the previous layer becomes saturated.
```

---

## 🔬 Interview Q&A

### Q: "Your cache hit rate is 95% but you're still seeing DB overload. Why?"

> 5% of 10M reads/sec = 500K cache misses/sec hitting your DB — that's enough to overwhelm a Postgres primary (cap: 10–20K QPS). At high absolute volumes, even a 95% hit rate generates DB-saturating miss traffic. Fix: increase TTL to reduce miss rate further, or precompute and warm cache proactively. If miss traffic is from write-heavy keys (frequent updates causing cache invalidation), the problem is actually on the write path — see Scaling Writes pattern.

---

### Q: "A user posts a comment. They immediately refresh and don't see it. What's happening?"

> Read-after-write inconsistency from replica lag. Write went to primary; the immediate read hit a replica that hasn't received the replication yet (lag can be 10ms–2s). Fix: route "read-your-own-writes" to primary for a short window after the user's write (detect via session flag or header). Alternative: after writing to DB, also write to cache with the new value — user's next read hits cache and sees their own write.

---

### Q: "Your Redis node crashes. What happens to your system?"

> All reads that were hitting cache now miss and hit DB simultaneously — thundering herd. The DB saturates within seconds. Mitigations: (1) Redis Sentinel or Redis Cluster for automatic failover. (2) Circuit breaker — if cache is unavailable, shed a percentage of read traffic rather than all hitting DB. (3) Cache warming — pre-populate top N hot keys from DB before taking live read traffic after cache restart.

---

### Q: "When would you skip caching and go straight to read replicas?"

> When cache hit rate would be low: (1) Access pattern is highly random — every request hits a different key (ad targeting, fraud scoring). (2) Data is too large to fit in RAM and the working set is not concentrated. (3) Queries are complex SQL (joins, aggregations) — cache would need to store full result sets, invalidation becomes a nightmare. Read replicas give full SQL at horizontal scale without invalidation complexity.

---

### Q: "How do you handle cache invalidation when a product's price changes?"

> Options in order of complexity: (1) TTL expiry — accept up to 60s stale price. Simple, widely used, usually acceptable. (2) Write-through — on price update, simultaneously write to DB and delete/update cache key. (3) Event-driven — price change event on Kafka; cache invalidation consumer deletes the key. Most production systems use TTL (60s) because a slightly stale price is acceptable and the simplicity is worth it.

---

### Q: "You have 20 read replicas. The primary crashes. What's the impact?"

> Replicas become read-only islands — reads work, writes fail 100%. Recovery: orchestrator (Patroni, AWS RDS Multi-AZ) promotes the least-lagged replica to primary. Async replicas can have data loss = lag at crash time. Key lesson: read replicas solve read scaling; they don't solve write HA. Write HA requires a separate mechanism — synchronous replicas, Multi-AZ, or consensus-based HA (Raft).

---

### Q: "Your Elasticsearch is 10 seconds behind the primary. A user searches for a product they just listed. What do they see?"

> They won't find it for up to 10 seconds — expected behavior for an eventually-consistent read store. Acceptable solution: show a UI message: "Your listing is being indexed. It may take a few seconds to appear in search." This is a product decision — the engineering team must surface the lag to product and agree on the UX implications before building.

---

### Q: "When does adding a dedicated read store make sense vs just adding more replicas?"

> Not about scale — about query pattern mismatch. Read replicas give the same query capability as the primary (full SQL). If you need a DIFFERENT query capability — full-text search, geospatial queries, time-series aggregation — replicas can't help regardless of how many you add. The trigger: "My primary DB fundamentally cannot answer this query efficiently." Scale is secondary to query pattern fit.

---

### Q: "How would you scale reads for a leaderboard serving 10M DAU?"

> Redis Sorted Set (ZSET) as the leaderboard store — O(log N) score updates, O(log N) range queries for top-N, all in-memory at microsecond latency. Persist to Postgres asynchronously for durability. Cache the rendered top-100 response with a 5s TTL — most users read the same top-100. This is a Scaling Reads + Dealing with Contention hybrid: the read path uses Redis; the write path uses atomic increments.

---

### Q: "Product team wants 'X people viewed this item in the last hour.' How do you implement this without killing your DB?"

> Never store view counts with per-view writes to the primary DB — that's a hot write anti-pattern. Instead: (1) Write view events to Kafka (fire and forget, no DB write on the hot path). (2) A consumer aggregates counts per item per 1-hour window into a Redis counter with 1h TTL. (3) Read path reads from Redis — single O(1) key lookup. The popular product page never touches your primary DB for view counts, regardless of traffic.

---

## ⚠️ Anti-patterns (don't say these)

- **Adding replicas when the problem is N+1 queries** — fix the query first; replicas just multiply bad queries
- **Caching JOIN results** — cache individual entities (`user:{id}`); JOIN result invalidation is exponentially complex
- **Treating cache as source of truth** — cache is an optimization; DB is always truth; if they disagree, DB wins

---

## 🧩 Common Interview Problems

| Problem | Strategy | Key decision |
|---|---|---|
| Design URL Shortener | Redis cache | Short URL → long URL is pure key-value, 100% reads after creation |
| Design Twitter / X | Cache + Replicas + ES | Timeline reads >> writes; search needs Elasticsearch |
| Design Product Catalog | CDN + Redis | Same content for all users; updated rarely |
| Design Leaderboard | Redis ZSET | `ZADD`/`ZREVRANGE` — O(log N) update + O(log N) top-N query |
| Design YouTube | Redis + CDN + Kafka | Metadata via Redis; video via CDN; view counts via Kafka (not DB writes) |

---

## 🔗 Full notes

`DeepDive/01-scaling-reads.md` — decision playbook, failure mode Q&A, worked examples
