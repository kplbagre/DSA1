# Operational Scenario: Scaling an Existing System

> **When this appears in an interview:** Interviewer says "your service is getting hammered — traffic tripled overnight — what do you do?" or "DB connections are maxed out — walk me through it." The keywords are **scaling**, **traffic spike**, or **resource exhaustion**.
> **Patterns used:** Scaling Reads (`../../Patterns/DeepDive/01-scaling-reads.md`), Scaling Writes (`../../Patterns/DeepDive/02-scaling-writes.md`), Feature Flag Gating (`../../Patterns/DeepDive/14-feature-flag-gating.md`) for graceful degradation kill switches.

---

## 🎯 The Situation

Traffic tripled overnight. Pager fires: API error rate is climbing, DB connections are maxed out, CPU is at 90%. You need to stop the bleeding and then make it durable.

**Classic triggers in interviews:**
- "Traffic went 3× — how do you scale your service?"
- "Your DB CPU is at 100% — what's the fix?"
- "Connection pool is exhausted — walk me through diagnosing and fixing it"
- "How would you handle a 10× traffic spike on your current system?"

---

## 🧠 The Decision You Make First

Ask one clarifying question before drawing anything:

> *"Is the bottleneck the application layer, the database layer, or a downstream dependency?"*

| Answer | Where to focus | Risk of getting it wrong |
|---|---|---|
| Application CPU / thread pool saturated | Horizontal scale (add pods), tune thread pool | Adding pods without checking DB connection count can make the DB worse |
| DB connection pool exhausted | Connection pooler, read replicas | Adding replicas without replication lag monitoring causes silent stale reads |
| Downstream dependency slow or rate-limiting | Async offload, circuit breaker, cache the response | Retrying a slow downstream on every request multiplies the load |

**Do not skip the measurement step.** Adding pods when the bottleneck is the DB increases DB connection pressure. Adding a cache when the bottleneck is compute doesn't help. Identify the bottleneck first.

---

## 🎨 Visual — Before and After Scaling

> **Before:** single-instance app connecting directly to a single DB — all reads and writes hitting one machine, connection count limited by that one pod.
> **After:** HPA-managed pod cluster behind a connection pooler, with read replicas for reads and a cache layer for hot data, and an async queue to move expensive work off the critical path.

```
BEFORE
──────
  [Client]
      │
      ▼
  ┌───────┐          ┌──────────────┐
  │ App   │──────────│  DB (Primary)│
  │ Pod   │          │  reads+writes│
  └───────┘          └──────────────┘
  (1 pod, 10 connections, all traffic)


AFTER
─────
  [Client]
      │
      ▼
  [API Gateway / Load Balancer]
      │
      ├──── [App Pod 1]
      ├──── [App Pod 2]   ←── HPA scales based on CPU/RPS
      └──── [App Pod N]
                │
        [Connection Pooler]    ←── PgBouncer (Postgres) / ProxySQL (MySQL)
       (limits total server-side connections regardless of pod count)
           /            \
   ┌──────────┐     ┌──────────────┐
   │  Read    │     │    Primary   │
   │ Replica  │     │  (writes)    │
   └──────────┘     └──────────────┘
       (reads)

   [Cache Layer]    ←── Redis / Memcached
   (hot data served here — no DB hit)

   [Async Queue]    ←── Kafka / SQS
   (expensive work moved off the critical path)

KEY INVARIANT:
   Scale app pods ONLY after verifying that total DB connections
   (pods × pool size) fits within DB max_connections.
   A connection pooler MUST be in place before scaling pods aggressively.
```

---

## ⚠️ PREREQUISITE — Measure First, Then Scale

> **Class 4 (Missing prerequisites):** Every scaling action assumes a specific bottleneck. Scaling the wrong layer makes the situation worse.

**Check these before touching anything:**

| Metric | How to check | What it tells you |
|---|---|---|
| CPU % per pod | K8s metrics / DataDog | Is compute saturated? |
| Thread pool utilization (active / max) | Spring Actuator `/actuator/metrics/tomcat.threads.busy` | Are threads queueing? |
| Connection pool wait time | HikariCP metrics | Is DB connectivity the limit? |
| DB active connections vs max | `SELECT count(*) FROM pg_stat_activity` (Postgres) | Is DB at connection ceiling? |
| DB slow queries | `pg_stat_statements` (Postgres) | Is one bad query causing the problem? |
| Replication lag (if replicas exist) | `SELECT now() - pg_last_xact_replay_timestamp()` | Are replica reads stale? |

---

## 🗂️ The 5-Phase Scaling Playbook

---

### Phase 1 — Measure: Find the Bottleneck (Do This Before Everything Else)

**The rule:** if you can't name which resource is saturated, you cannot pick the right scaling action.

**Saturation is the most overlooked signal.** Latency rises when threads or connections are *queueing*, not when a single request is slow. Check pool fill percentage, not just response time.

```
RESOURCE         METRIC                                  TOOL
────────────────────────────────────────────────────────────────────────
CPU              pod CPU % > 70% sustained               K8s metrics, DataDog
Thread pool      active threads / max-threads > 80%      Spring Actuator
Memory           heap used / max-heap > 80%              JVM metrics
DB connections   active / max_connections > 80%          pg_stat_activity
DB slow queries  queries > 100ms in slow log             pg_stat_statements
Replication lag  seconds behind primary > 1s             pg_last_xact_replay_timestamp
Downstream       p99 latency of dependency calls         Distributed traces
```

**Say in interview:**
> *"Before touching anything, I identify which resource is saturated. The classic mistake is to add more pods when the DB is the bottleneck — more pods means more connections, which makes the DB worse. I check CPU, thread pool utilization, DB connection count, and slow query log first."*

---

### Phase 2 — Application Layer: Horizontal Scale + Pool Tuning

If CPU or thread pool is saturated, scale horizontally. But first — calculate total DB connections.

> ⚠️ **Class 3 (Math without reality check) + Class 4 (Missing prerequisite):** Before scaling pods, check whether total DB connections will exceed DB max_connections. If so, install a connection pooler FIRST.

```
Formula (Postgres example):
  total_connections = pod_count × hikari_pool_size

  Current: 5 pods × 10 connections = 50 connections   ✅ (DB max_connections = 100)
  After scale: 20 pods × 10 connections = 200 connections  ❌ (DB max_connections = 100)

Fix: PgBouncer (Postgres) or ProxySQL (MySQL) in front of DB FIRST.
  20 pods × 10 = 200 client connections → PgBouncer → 20 actual DB connections
```

**K8s horizontal scaling:**

```
# Manual scale (immediate — for incident response):
kubectl scale deployment/<name> --replicas=10

# HPA (automatic — for sustained load):
kubectl autoscale deployment/<name> \
  --cpu-percent=70 \
  --min=3 \
  --max=20
```

**Thread pool and connection pool (Spring Boot + Tomcat):**

```
# application.properties

# Default Tomcat max-threads is 200.
# Increase only if CPU has headroom but threads are queueing.
server.tomcat.max-threads=400

# HikariCP: connection pool per pod.
# Multiply by pod count to get total DB connections.
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=3000
```

> ⚠️ **Class 6 (Threading edge case — Java 21 virtual threads):** If running Java 21 with `spring.threads.virtual.enabled=true`, the thread model changes — blocking I/O no longer pins OS threads. However, `synchronized` blocks DO pin the carrier thread. Do not increase `max-threads` expecting the same behavior as platform threads. The concurrency model is fundamentally different.

**Say in interview:**
> *"If the bottleneck is CPU or thread pool, I scale horizontally — but before I do, I calculate total DB connections: pods × pool size. If that exceeds DB max_connections, I need a connection pooler like PgBouncer in front of the DB first, or scaling pods will make the DB worse."*

---

### Phase 3 — Cache Layer: Serve Hot Data Without Hitting the DB

Cache data that is read far more often than it is written and is expensive to recompute.

**What to cache (ranked by impact):**

```
HIGH impact:
  User profile / session data         (read on every request, changes rarely)
  Product catalog / pricing           (read millions/day, updated infrequently)
  Feature flag values                 (read on every request, updated rarely)
  Computed aggregates                 (expensive SQL, result stable for seconds/minutes)

LOW impact (don't bother):
  High write-rate data                (invalidation cost exceeds read savings)
  Per-user unique data                (cache miss rate too high)
  Data requiring strict consistency   (stale read risk unacceptable — e.g., balances)
```

**TTL strategy:**

```
Short TTL (1–30 seconds):   data that changes often but staleness is tolerable
Long TTL (minutes–hours):   stable data (product catalog, config values)
Event-driven invalidation:  when source record changes, explicitly DELETE cache key
  → Required when reads must reflect writes quickly (inventory count, account status)
  → Write path publishes invalidation event to message queue;
    consumer calls Redis DEL on the affected key
```

> ⚠️ **Class 5 (Failure residue — thundering herd):** If Redis goes down, every request gets a cache miss simultaneously and hits the DB at full traffic. The DB — sized for much lower direct traffic — collapses.
>
> **Mitigation:** circuit breaker on the cache client. If Redis is unavailable, route to DB but cap the rate at the previous DB-direct traffic baseline. Do NOT let all cache misses hit DB unbounded.

> ⚠️ **Class 7 (Cache invalidation timing — stale read window):** User writes at T+0; cache still holds the old value until TTL expires. For write-then-read consistency: either (a) invalidate the cache key synchronously on write before returning 200, or (b) bypass cache for that user's session for a short window after a write.

**Say in interview:**
> *"For caching, I identify what gets read far more than it gets written — user profiles, product catalog, computed totals. I put those in Redis with a TTL appropriate to the staleness tolerance. The two risks I always name: thundering herd — if Redis dies, all traffic hits DB simultaneously, so I use a circuit breaker on the cache client — and stale reads after a write, which I mitigate with event-driven cache invalidation."*

---

### Phase 4 — DB Read Layer: Read Replicas + Replication Lag Handling

When the DB is the bottleneck after caching, separate reads from writes using read replicas.

> ⚠️ **Class 2 (False universality — Postgres vs MySQL):** The details below are for **Postgres**. MySQL uses binlog-based logical replication; Postgres uses WAL-based physical (streaming) replication. Connection pooling tools also differ: PgBouncer is Postgres-specific; ProxySQL is MySQL-native. State which DB you're using before prescribing specifics.

**Check replication lag before routing reads to replica:**

```sql
-- Postgres: run on the replica
SELECT
    now() - pg_last_xact_replay_timestamp() AS replication_lag;
-- If lag > 1s, reads from this replica may be stale by > 1s.
```

**Route reads vs writes in the application:**

```
Option A — application-level routing:
  writeDataSource → primary connection string
  readDataSource  → replica connection string
  App: writes go to writeDataSource; all SELECTs go to readDataSource

Option B — proxy-level routing:
  PgBouncer / HAProxy / RDS Proxy routes by statement type
  App uses a single connection string — proxy handles routing
  → Simpler app code; routing logic in one place
```

> ⚠️ **Class 7 (Replication lag — read-your-own-writes problem):** User writes at T+0. Replication completes at T+500ms. If that same user reads from the replica at T+200ms, they see their write missing.
>
> **Fix:** for operations where the user must see their own write immediately (profile update, order placement), route that read to PRIMARY for a short window — e.g., 2 seconds — after the write. Implement via a per-user "recently wrote" flag in session.

> ⚠️ **Class 5 (Failure residue — replica lag grows under write load):** Under heavy write load, replicas can fall behind by tens of seconds. A replica with 30-second lag will serve 30-second-old data. Monitor lag continuously. If lag exceeds your consistency SLA, automatically route reads back to primary. Serving wrong data is worse than serving slow data.

**Say in interview:**
> *"For DB read scaling, I add read replicas and route all SELECT queries to them. The gotcha is replication lag: if a user writes and immediately reads from the replica, the replica may not have the write yet. I handle read-your-own-writes by routing the post-write read to primary for a short window. And I monitor replica lag — if it spikes past a threshold, I fall back to reading from primary."*

---

### Phase 5 — Async Offload: Move Expensive Work Off the Critical Path

**The rule:** anything that doesn't need to happen before you return a response should be made async.

```
WRONG:
  Request → validate → write DB → send email → generate PDF → return 200
                                  [user blocked on email + PDF: 800ms total]

CORRECT:
  Request → validate → write DB → return 200
                                  ↓ (async, off critical path)
                          publish event to queue
                               ↓
                     consumer: send email
                     consumer: generate PDF → store result → notify user
```

**What to offload:**

```
ALWAYS ASYNC:
  Email / SMS notifications           (user doesn't need to wait for delivery)
  Audit log writes                    (must NOT block user path)
  Report / PDF generation             (heavy compute — return job ID, push notify when done)
  Image / video processing            (upload to S3, process async)
  Analytics / event tracking          (non-critical, can be batch-processed)

KEEP SYNCHRONOUS:
  Payment authorization               (user must know if it succeeded)
  Inventory reservation               (must confirm stock before confirming order)
  Core business validation            (can't return 200 if data is invalid)
```

> ⚠️ **Class 8 (Incomplete change surface):** When making a write path async, verify that ALL downstream consumers of that data can tolerate the eventual consistency window. If a downstream service polls your API immediately after you return 200, but the async worker hasn't run yet, the downstream sees inconsistent state. Audit all consumers before making any write path async.

**Say in interview:**
> *"The final lever is async offload. Anything that doesn't need to block the user's response goes to a queue — email notifications, audit logs, report generation. This dramatically reduces critical-path P99 latency. The risk is eventual consistency: I audit every downstream system that reads my data immediately after a write and confirm they can tolerate the async delay."*

---

## 🧩 Interview Probe Q&As

**"How do you know when to scale horizontally vs vertically?"**
> Horizontal scaling (more pods) works when the bottleneck is stateless compute — CPU, thread pool, memory per request. Vertical scaling (bigger machine) works when the bottleneck is state that's hard to shard — a database that's complex to replicate, significant in-process state. For most web services, horizontal is preferred: cheaper, more resilient to single-instance failure, and automatable with HPA. Vertical hits a ceiling quickly and doesn't improve fault tolerance. I default to horizontal and only consider vertical when horizontal requires too much refactoring.

**"Your DB is the bottleneck. You've added read replicas. What's next if replicas aren't enough?"**
> If read replicas don't solve it, the bottleneck is either writes or the query pattern. Next steps in order: (1) identify the slowest queries via `pg_stat_statements` and add missing indexes — one missing index on a high-traffic query can be a 100× improvement; (2) for write-heavy load, consider write sharding (partition data by key, route writes to different DB instances) or write buffering (batch writes into Kafka, flush to DB asynchronously); (3) for complex aggregate queries, materialize them — compute and store the result, update via event-driven triggers. This moves compute off the query path.

**"What's thundering herd and how do you prevent it?"**
> Thundering herd is when a protection layer (cache, circuit breaker) fails, and all requests simultaneously hit the unprotected system underneath. Classic case: Redis goes down, 10,000 requests/second suddenly hit the DB — the DB, sized for much lower direct traffic, collapses. Prevention: (1) circuit breaker on the cache client — if Redis is down, cap the fallback rate to what the DB can handle; (2) probabilistic early expiration (cache entries expire at a random jitter around the TTL, preventing mass simultaneous expiry); (3) background cache refresh — refresh values before TTL expires so requests never see a miss on hot keys.

**"How do you scale a stateful service?"**
> Externalize the state first — move session data from in-process memory to a shared store (Redis). Once sessions are external, pods become stateless and scale horizontally without sticky sessions. If the state can't be externalized (e.g., a local in-memory cache for performance), accept that it replicates per pod and size accordingly — each pod warms its own cache after startup. Pre-populate from a canonical source on startup (read from Redis or DB into local cache) to reduce the cold-start penalty.

**"What's the risk of routing all reads to replicas?"**
> Replication lag: writes hit primary, replicas receive them asynchronously. During the lag window, reads from replicas return stale data. This breaks read-your-own-writes scenarios — user updates their email and immediately sees the old value. Fix: route post-write reads to primary for a short window. Also: under heavy write load, replica lag can grow significantly. Monitor it continuously. If lag exceeds your consistency SLA, automatically fall back to reading from primary even at the cost of higher primary load. Serving wrong data is worse than serving slow data.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"I use a 5-phase approach. First, I measure — I identify which resource is actually saturated: CPU, thread pool, DB connections, or a downstream dependency. Adding pods when the DB is the bottleneck makes things worse: more pods means more connections. Second, for application-layer saturation, I scale horizontally with HPA and tune thread and connection pool sizes — but I always check first: pods × pool size must fit within DB max_connections. If not, I add a connection pooler like PgBouncer before scaling. Third, for read-heavy load, I add a Redis cache for hot data with appropriate TTL — and I design for thundering herd: if Redis goes down, I don't let all misses hit the DB at once. I use a circuit breaker on the cache client. Fourth, for DB bottleneck, I add read replicas and route SELECT queries to them — but I handle replication lag: reads immediately after a write go to primary. Fifth, I async-offload anything that doesn't need to block the user's response — emails, audit logs, report generation. The prerequisite for all of this is knowing your baseline metrics before the spike — you can't scale intelligently without a starting point."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 12, 2026 | **Note created.** Batch 4 of Operational-Scenarios gap closure. Covers measure-first discipline, connection pooler prerequisite before horizontal scale (Class 4), DB connection math check (Class 3), cache thundering herd circuit breaker (Class 5), replication lag read-your-own-writes routing (Class 7), async offload downstream consistency audit (Class 8). |
