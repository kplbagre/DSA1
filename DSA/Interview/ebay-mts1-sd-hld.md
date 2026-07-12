# eBay MTS1 — System Design Reference (What to Study)

> **What this file is:** A revision-ready study guide for the eBay MTS1 System Design
> rounds. NOT full worked designs. Each section tells you exactly what concepts to know cold,
> what trade-offs to be fluent in, and what the interviewer will probe — so you can do the
> deep thinking before the room, not during it.
>
> **Raw research source:** `ebay-mts1-research.md` §3 (confirmed reports with source counts).
> **Deep-dive concept files:** `../../SystemDesignConcepts/` (60 tracked notes, fully indexed).
> **LLD round:** `../../../LLD/Interview/ebay-mts1-lld.md`.

---

## 🗺️ Study Map — All SD Questions at a Glance

| # | Question | Round | Sources | What it tests |
|---|----------|-------|---------|---------------|
| 1 | ⭐ Notification Service | R2 | 4 | Event-driven fan-out, retry, idempotency, GDPR |
| 2 | ⭐ HLD of Own Project | R3 Director | 3+ | Depth, trade-offs, failures, metrics |
| 3 | 🔹 Ad Click Event Storage | R2 | 2 | High-write ingest, time-series storage, hot partition |
| 4 | 🔹 Dropbox-like File System | R2 | 2 | Chunking, dedup, sync, CDN, metadata separation |
| 5 | 🔹 Online Flash Sale | R2 | 2 | Atomic inventory, Redis, overselling, idempotency |
| 6 | 🔹 TinyURL / URL Shortener | R2 | 1-2 | KV store, redirect, caching, vanity URLs |

**Priority order for study:** #1 → #2 → #5 → #4 → #3 → #6

---

## ⭐ R2: Design a Notification Service

### 🧠 What the system does

Sellers on eBay need to be notified (email, SMS, push) when an item sells, a bid is placed,
or a return is filed. The service must handle multiple upstream event sources, multiple
delivery channels, and scale to 100M+ notifications per day.

**Why eBay cares:** This is a real system eBay runs at scale. The interviewer has operational
context — they will probe real failure modes, not just component diagrams.

---

### 🧠 Core concepts to know cold

- **Kafka fan-out** (publishing one event so multiple independent consumer groups read it —
  each consumer group represents one notification channel: email, SMS, push) — why Kafka wins
  over RabbitMQ here: multiple consumer groups read the same topic independently; RabbitMQ's
  competing-consumer model distributes to only one consumer per message.
- **User preference store** — opt-in/opt-out per channel per event type. Must be checked
  *before* dispatching, not after.
- **Idempotency key** (a unique key that makes an operation safe to retry without side effects)
  for notifications: `eventId:userId:channel`. Redis `SET NX EX` on this key before sending —
  if already set, skip. This prevents duplicates from at-least-once Kafka delivery.
- **At-least-once delivery + consumer-side dedup** — simpler and sufficient vs exactly-once
  Kafka transactions. The cost of a duplicate notification (a minor annoyance) is far lower
  than the complexity of Kafka transactions.
- **Retry + exponential backoff + DLQ** (Dead Letter Queue — a separate queue where failed
  messages are parked after exhausting retries, for manual inspection or replay): 3 retry
  attempts → exponential delay (1s, 2s, 4s) → DLQ → alert.
- **Third-party provider circuit breakers**: SendGrid goes down → open circuit breaker,
  fallback to SES or enqueue for later. Don't spam retries to a dead provider.
- **Priority queues**: fraud alerts >> bid notifications >> marketing newsletters. Separate
  Kafka topics or priority field on the event.
- **Scale math**: 100M/day = ~1,160/sec steady state. Kafka handles this trivially.
  The bottleneck is the third-party provider's API rate limits — rate-limit per provider,
  not globally.

---

### 🧠 Key design choices + trade-offs

| Decision | Option A | Option B | Right call |
|----------|----------|----------|------------|
| Message broker | **Kafka** | RabbitMQ | Kafka — fan-out to N channels via N consumer groups; replay; event log |
| Delivery guarantee | **At-least-once + idempotency** | Exactly-once (Kafka transactions) | At-least-once — simpler, sufficient; duplicate notification cost is low |
| Retry path | **DLQ after N retries** | Infinite retry | DLQ — prevents hot loops; enables manual replay |
| Template rendering | **Async, separate worker** | Inline in consumer | Async — don't block the Kafka consumer on I/O |
| GDPR opt-out check | **Before dispatch** | After send + suppression | Before — GDPR requires it; never send then suppress |

---

### ⚠️ Confirmed follow-up probes (from reports)

1. "How do you handle notification **deduplication** across channels?" →
   Idempotency key `eventId:userId:channel` in Redis SET NX EX before each send.
   If key already exists → skip → Kafka consumer commits offset.

2. "A 3rd-party SMS provider **goes down** — how do you handle retries without spamming?" →
   Circuit breaker pattern: after N consecutive failures, open the circuit →
   fail fast for X seconds → half-open probe → exponential retry with jitter.

3. "How do you ensure **at-least-once without duplicates**?" →
   At-least-once from Kafka + idempotency key on the sending side. Two-part answer: the broker
   guarantees delivery; the consumer guarantees dedup.

4. "How does it scale to **100M notifications/day**?" →
   Kafka absorbs bursts. Channel workers are stateless → horizontal scale.
   Bottleneck is provider API limits → rate limiter per provider.

---

### 📚 SDC cross-refs

| Concept | File |
|---------|------|
| Kafka vs RabbitMQ — when to use which | `../../SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` |
| Push notifications + fan-out at scale (APNs, FCM) | `../../SystemDesignConcepts/Core-Architecture/Service-Communication/46-push-notifications-fanout.md` |
| Idempotency key pattern (HTTP + Kafka consumer) | `../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md` |
| Retry + exponential backoff + DLQ | `../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md` |
| Circuit breaker state transitions | `../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/20-circuit-breaker-resilience.md` |
| Rate limiting (token bucket, sliding window) | `../../SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md` |

---

## ⭐ R3 Director: HLD of Your Own Project

### 🧠 The format

Director R3 opens with: *"Walk me through a system you built that you're proud of."*
Then drills hard on: Why did you make that trade-off? What went wrong? What would you change?

**This is the highest-signal round for MTS1 → MTS2 differentiation.** The Director already
read your resume before the call. They test depth, not breadth. Candidates who described
systems they *actually built* outperformed those who described a cleaned-up hypothetical.

---

### 🧠 Preparation framework (4 parts, ~8 min total)

**Part 1 — What the system does (2 min)**
- What problem does it solve? Who are the users? What's the scale (req/s, data volume, SLA)?
- Draw the key components on the whiteboard as you speak.

**Part 2 — The key design decisions (3 min)**
- Pick 2-3 decisions that had real trade-offs. For each:
  → What options did you consider? → What did you pick and why? → What did you give up?
- Framing: "I chose X over Y because [concrete reason tied to our scale/constraints]."

**Part 3 — Failures + recovery (2 min)**
- What went wrong in production? (Everyone has something. Saying "nothing went wrong" kills credibility.)
- What was the impact? What did you do? What did you change?

**Part 4 — Metrics + success (1 min)**
- How do you know it works? What do you measure? What does your alerting look like?

---

### ⚠️ Confirmed verbatim questions (from reports)

1. "What was the **biggest failure** in that system and how did you recover?"
2. "If you had to **redesign it today with no constraints**, what would change?"
3. "How did you **measure success** of that system?"
4. "Why did you make **that** trade-off?" (follow-up to any design decision you name)

---

### 🧠 What to prepare before the interview

Read your current project notes fresh before the interview:
**External folder:** `/Users/k0b077v/Documents/Kpl-inv/project-update/` (Walmart project details)
**AI project:** `/Users/k0b077v/aiPnSBackend/prep/` (TransNova — if relevant)

Pick the project that has the richest trade-off story and real failure, not the cleanest one.

---

## 🔹 R2: Ad Click Event Persistent Storage

### 🧠 What the system does

High-write event ingest: millions of clicks per second on eBay ads → stored → queryable by
ad ID + time range, with analytics dashboards and data retention policies.

**The core tension:** Write throughput (append-only, no single point of contention) vs.
read patterns (time-range queries per ad + analytical aggregations). These pull in opposite
directions for storage choice.

---

### 🧠 Core concepts to know cold

- **Kafka for ingest buffering**: click events → Kafka topic (partitioned by `ad_id`) →
  consumer → storage. Kafka absorbs burst writes; decouples ingest speed from storage speed.
- **Deduplication**: client-side retry → same click event arrives twice → idempotency key
  (`clickId` UUID generated client-side). Consumer checks Redis SET NX before writing.
- **Cassandra schema** for operational time-range queries:
  - Partition key: `(ad_id, hour_bucket)` — `hour_bucket = floor(timestamp_ms, 3_600_000)`
  - Clustering key: `timestamp` ASC
  - Why composite partition key: a popular ad with 1B clicks would create a hot row if
    partitioned by `ad_id` alone. `hour_bucket` caps partition size.
- **Cassandra vs ClickHouse** (a columnar OLAP (Online Analytical Processing — optimized for
  aggregation queries across many rows) database):
  - Cassandra: `WHERE ad_id=X AND time >= t1 AND time <= t2` — low-latency per-ad reads.
    Operational SLA (<10ms). Bad for cross-ad aggregations.
  - ClickHouse: `SELECT SUM(clicks) GROUP BY ad_id, date` — analytical, batch-friendly.
    High throughput aggregations. Not designed for single-row lookups.
  - **Right answer:** Cassandra for operational API, ClickHouse (or Spark batch job) for
    analytics/reporting.
- **Hot partition problem**: popular ad → all writes to same Kafka partition → consumer lag.
  Fix: write salting — prefix `ad_id` with a random suffix 0..N, then re-aggregate at read
  time (or at a stream-processing layer).
- **Late-arriving events**: Kafka guarantees order within a partition, not across partitions.
  Events from different sources can arrive out of order. Use a watermark (a threshold
  timestamp below which you stop waiting for late events) + grace period (e.g., 10 min)
  before committing to Cassandra.
- **TTL policies**: `DEFAULT TTL 7776000` (90 days in seconds) on the Cassandra table.
  ClickHouse: `TTL timestamp + INTERVAL 90 DAY` on the column.
- **Roll-up aggregations**: pre-compute hourly/daily summaries into a separate table.
  Avoids scanning millions of raw rows for a dashboard query.

---

### 🧠 Key design choices + trade-offs

| Decision | Option A | Option B | Right call |
|----------|----------|----------|------------|
| Ingest layer | **Kafka** | Direct write to DB | Kafka — buffer, decouple, replay |
| Operational storage | **Cassandra** | DynamoDB / PostgreSQL | Cassandra — wide rows, time-series native, TTL |
| Analytics storage | **ClickHouse** | Cassandra for everything | ClickHouse — columnar, fast aggregations |
| Hot partition | **Salting + re-aggregate** | Larger Kafka partition count | Salting — scales to any hot key |
| Delivery semantics | **At-least-once + dedup** | Exactly-once | At-least-once — simpler, sufficient |

---

### ⚠️ Confirmed follow-up probes (from reports)

1. "What's your **time-series storage choice** and why?" → Cassandra for operational,
   ClickHouse for analytics. Partition key design. Justify the split.
2. "How do you handle **late-arriving events**?" → Watermark + grace period at stream
   processor layer. Cassandra TTL for stale data cleanup.
3. "How do you handle a **popular ad** (10x traffic spike)?" → Hot partition problem.
   Salting. Consumer lag monitoring.
4. "What's your **data retention** strategy?" → TTL at storage layer. Tiered: raw events
   90 days, hourly roll-ups 1 year, daily roll-ups indefinitely.

---

### 📚 SDC cross-refs

| Concept | File |
|---------|------|
| Kafka — event log, partitioning, consumer groups | `../../SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` |
| Hot partition — salting, hot-key caching | `../../SystemDesignConcepts/Core-Architecture/Database-Core/45-hot-partition-problem.md` |
| Sharding strategy — partition key design | `../../SystemDesignConcepts/Core-Architecture/Database-Core/38-sharding-strategy.md` |
| Database types — Cassandra vs ClickHouse vs SQL | `../../SystemDesignConcepts/Core-Architecture/Database-Core/06-databases-types-and-selection.md` |
| Idempotency key (consumer-side dedup) | `../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md` |

---

## 🔹 R2: Dropbox-like File System

### 🧠 What the system does

Users upload files from one device and access them from any device. Files sync automatically
when changed. Scale: millions of users, files up to several GB, devices always online.

**The core insight:** Treat files as a collection of chunks, not monolithic blobs. This
unlocks deduplication, parallel transfer, resume-on-failure, and cheap versioning.

---

### 🧠 Core concepts to know cold

- **Chunking**: client splits file into fixed-size chunks (4MB is typical). Why:
  (a) parallel upload, (b) resume from last successful chunk on failure,
  (c) efficient deduplication — only changed chunks need re-upload.
- **Content-addressed storage** (a storage scheme where the address of data IS its content
  hash — same content = same address, so storage is naturally deduplicated):
  SHA-256(chunk bytes) = chunk_hash = the key in blob store. Same bytes → same hash → stored
  once, referenced many times.
- **Client delta detection**: client maintains a local index `{file_path → [chunk_hash_1, ...]`}.
  On file change → recompute hashes → diff against local index → only changed chunks are new.
- **Upload flow (5 steps):**
  1. Client hashes all chunks locally.
  2. Client asks server: "do you have these chunk hashes?" → server responds with missing list.
  3. Client uploads only missing chunks to blob store (S3/GCS).
  4. Client sends metadata commit: `{file_id, path, version, [chunk_hash_1, ...]}`
  5. Server persists metadata → notifies other devices via Sync Service.
- **Metadata DB** (PostgreSQL): `file_id`, `user_id`, `path`, `version`, `chunk_hashes[]`,
  `created_at`, `updated_at`. Sharded by `user_id` at scale.
- **Blob store** (S3/GCS): immutable, content-addressed. Key = `chunk_hash`.
  Never modified after write — safe to cache aggressively.
- **Sync service**: each client holds a persistent WebSocket or SSE (Server-Sent Events —
  unidirectional push from server to client over HTTP) connection. On metadata change →
  server pushes event → client fetches new chunk list → downloads missing chunks from CDN.
- **CDN for downloads**: content-addressed chunks = perfect CDN cache key (never stale,
  no TTL needed). Popular files served from edge with zero origin hits.
- **Conflict resolution options**:
  - Last-write-wins: simplest, loses data.
  - Fork (create conflict copy): Dropbox's actual behavior. No data loss. User resolves.
  - Operational transform (OT): Google Docs-style concurrent editing. Complex. Overkill for files.
  - **Right answer for interview:** Fork — no data loss, user resolves manually.
- **Cheap versioning**: version N and N-1 share most chunks in the blob store.
  Versioning cost = only the delta chunks + a new metadata row.

---

### 🧠 Key design choices + trade-offs

| Decision | Option A | Option B | Right call |
|----------|----------|----------|------------|
| Chunking size | **Fixed 4MB** | Content-defined (Rabin fingerprinting) | Fixed is simpler; content-defined handles mid-file insertions better — mention as optimization |
| Conflict resolution | **Fork (conflict copy)** | Last-write-wins / OT | Fork — no data loss, matches Dropbox behavior |
| Sync push | **SSE (server-side events)** | WebSocket / long-poll | SSE — unidirectional (server → client) is sufficient; simpler than WebSocket |
| Metadata DB | **PostgreSQL + shard by user_id** | DynamoDB | PostgreSQL for ACID on version commits; shard at 1B+ files |
| CDN cache key | **chunk_hash (content-addressed)** | file_id + timestamp | chunk_hash — never stale, no invalidation needed |

---

### ⚠️ Confirmed follow-up probes (from reports)

1. "How does your **chunking strategy** handle deduplication?" → content-addressed storage:
   SHA-256 hash as chunk ID. Client checks which hashes server already has before uploading.
2. "Two users edit the **same file simultaneously** — what happens?" → Fork. Each write
   creates a new version. Client detects conflict (server has newer version than base) →
   creates conflict copy with timestamp in name.
3. "How do you **sync across devices** efficiently?" → SSE for change notification.
   Client only downloads delta chunks (those whose hashes it doesn't have locally).
4. "How does **CDN** work here?" → Content-addressed chunks have immutable cache keys.
   No TTL, no invalidation. Cache miss goes to S3 origin.

---

### 📚 SDC cross-refs

| Concept | File |
|---------|------|
| Document & blob storage (S3, metadata DB, versioning) | `../../SystemDesignConcepts/Foundations/Data-Fundamentals/14-document-blob-storage.md` |
| CDN — edge caching, TTL, invalidation | `../../SystemDesignConcepts/Production-Grade/Performance-Optimization/28-cdn-edge-caching.md` |
| WebSocket — bidirectional real-time | `../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md` |
| Sharding strategy — partitioning by user_id | `../../SystemDesignConcepts/Core-Architecture/Database-Core/38-sharding-strategy.md` |
| Idempotency (upload retry safety) | `../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md` |

---

## 🔹 R2: Online Flash Sale System

### 🧠 What the system does

100K items go on sale at T=0. 10M users hit "buy" simultaneously. The system must:
(a) prevent overselling, (b) give every user a fair chance, (c) confirm orders reliably.

**eBay context:** eBay runs flash sales (eBay Deals). The interviewer has operational
context and will probe the Redis failure case explicitly.

---

### 🧠 Core concepts to know cold

- **The race condition**: 10M concurrent reads of `inventory = 1` → all see "available" →
  all proceed → 10M orders placed for 1 item. Classic TOCTOU (time-of-check, time-of-use
  — a race condition where the state changes between checking it and acting on it).
- **Redis DECR as the atomic weapon**: `DECR inventory_key` is a single atomic command.
  - If result `≥ 0` → reservation succeeded → proceed to order creation.
  - If result `< 0` → oversold → `INCR inventory_key` (restore) → return SOLD_OUT.
  - The atomicity guarantee: no two clients can both see result `≥ 0` for the last item.
- **Why not DB pessimistic lock** (`SELECT FOR UPDATE`): serializes all requests through
  the DB → DB becomes the bottleneck → 10M req/s crushes it → timeouts cascade.
- **Why not DB optimistic locking** (version column + retry on conflict): high contention
  = high retry rate = retry storm. Degrades under exactly the conditions that matter most.
- **Rate limiting per user**: 1 buy-attempt per user per item per X seconds using Redis
  sliding window. Blocks bots, reduces load on the DECR path.
- **Virtual waiting room** (optional, for fairness): Redis Sorted Set with
  `ZADD score=timestamp member=userId`. Process front of queue. SSE to notify queue position.
  Tradeoff: adds fairness but adds latency. For eBay Deals: probably not needed —
  first-come-first-served with rate limiting is sufficient.
- **Order creation idempotency**: `orderId = UUID hash(userId + itemId + saleId)`.
  `POST /orders` with this ID is safe to retry — Order Service checks if orderId already
  exists before creating.
- **Saga for the distributed transaction** (a pattern for distributed transactions that
  uses a sequence of local transactions with compensating transactions on failure — instead
  of a 2-phase distributed lock):
  1. Redis DECR → success → emit `OrderCreated` event to Kafka.
  2. Order Service consumes → writes to DB → emits `OrderConfirmed`.
  3. If Order Service fails after DECR → compensating `INCR` on Redis via a rollback event.
- **What if Redis goes down** (the interviewer will ask this):
  - Redis AOF persistence with `fsync=everysec` → at most 1 second of counter state lost.
  - Redis Sentinel promotes a replica on primary failure (recovery in seconds).
  - Feature-flag controlled fallback to DB-based `SELECT FOR UPDATE` at much lower throughput.
- **Oversell reconciliation job**: periodic background job compares Redis counter vs
  DB confirmed order count per item. Adjusts Redis if discrepancy (e.g., DECR succeeded
  but Order Service crashed before committing).

---

### 🧠 Key design choices + trade-offs

| Decision | Option A | Option B | Right call |
|----------|----------|----------|------------|
| Inventory check | **Redis DECR (atomic)** | DB pessimistic lock | Redis — 1M+ ops/sec; DB would collapse |
| Oversell prevention | **Redis DECR → compensate on failure** | DB transaction | Redis — speed; compensate via Saga |
| Fairness | **First-come-first-served + rate limit** | Virtual waiting room | FCFS for eBay Deals; waiting room for ticketing systems |
| Order idempotency | **UUID from userId+itemId+saleId** | No idempotency | UUID — retry safety is non-negotiable |
| Redis failure fallback | **DB lock + feature flag** | Redis Cluster only | Feature flag + DB fallback — defense in depth |

---

### ⚠️ Confirmed follow-up probes (from reports)

1. "How do you **prevent overselling**?" → Redis DECR atomic. Walk through the
   result ≥ 0 / < 0 logic. Mention compensating INCR on downstream failure.

2. "**What happens if Redis goes down** right as someone bought the last item?" →
   AOF persistence (at most 1 second lost) + Sentinel failover + DB fallback via feature
   flag. Reconciliation job catches any discrepancy.

3. "How do you ensure the **same user can't buy twice** on retry?" →
   Idempotency key (`userId + itemId + saleId`) → Order Service deduplicates by orderId.

4. "How do you handle **10M users hitting at T=0**?" →
   API Gateway rate limiting per user (1 req/s). Redis handles the burst (single-threaded
   event loop, no lock contention). Kafka absorbs order events. Order Service scales
   horizontally.

---

### 📚 SDC cross-refs

| Concept | File |
|---------|------|
| Redis atomic weapons (DECR, SET NX EX, Lua) | `../../SystemDesignConcepts/Foundations/Performance-and-Scale/54-redis-internals.md` |
| Optimistic vs pessimistic locking | `../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md` |
| Inventory management & booking pattern | `../../SystemDesignConcepts/Production-Grade/System-Design-Patterns/42-inventory-management-booking.md` |
| Saga pattern (compensating transactions) | `../../SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md` |
| Rate limiting (token bucket, sliding window) | `../../SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md` |
| Idempotency (order creation dedup) | `../../SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md` |
| Kafka for order event queue | `../../SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` |

---

## 🔹 R2: TinyURL / URL Shortener

### 🧠 What the system does

Convert a long URL to a short 7-character code (`ebay.com/s/abc1234`). Redirect any request
for the short code to the original URL. Support custom/vanity slugs. Track click analytics.

**Read-heavy system:** redirects (reads) far outnumber URL creations (writes).
Cache aggressively.

---

### 🧠 Core concepts to know cold

- **Short code generation options:**
  - Auto-increment ID + base62 encode (recommended): no collision risk, predictable growth,
    easy to shard by ID range.
  - MD5 hash → take first 7 chars: collision possible (birthday paradox — with 3.5 trillion
    possible codes and billions of URLs, collisions are rare but real). Need detection loop.
  - Random 7-char base62 string: also needs collision check before returning.
  - **Right answer:** auto-increment + base62. Clean, no collision risk.
- **Base62** (an encoding scheme using 62 printable characters: `[0-9a-zA-Z]`):
  7 characters → `62^7 ≈ 3.5 trillion` unique codes. Enough for any realistic URL volume.
- **Security note:** sequential IDs are enumerable — a bot can scan `abc0001` → `abc0002`.
  Mitigate: hash the ID with a secret salt before base62 encoding, or use a shuffled ID space.
- **301 vs 302 redirect:**
  - 301 Permanent: browser caches → future requests bypass the server → no click analytics.
  - 302 Temporary: browser always asks → full click data → more server load.
  - **eBay answer: 302** — eBay needs click tracking for ad revenue and business intelligence.
- **Cache-aside for reads** (a caching pattern where the application checks the cache first;
  on miss, it fetches from DB and populates the cache):
  Check Redis → hit = return URL → miss = query DB → populate Redis (TTL=24h) → return URL.
  For read-heavy traffic, Redis absorbs >99% of requests for popular short codes.
- **Bloom filter** (optional, a probabilistic data structure that can quickly check if an
  element has *never* been seen, with no false negatives): before any cache/DB lookup for an
  unknown shortCode (e.g., a mistyped URL), check the bloom filter first.
  If "definitely not present" → return 404 immediately → saves cache miss + DB hit.
- **Custom/vanity URLs**: user submits desired slug (e.g., `ebay.com/deals`).
  Conflict check: DB unique constraint on `short_code` column.
  Reservation: `INSERT ... ON CONFLICT DO NOTHING` → return the conflicting code if failed.
- **Analytics**: async, never blocking the redirect path.
  Redirect Service emits click event to Kafka → Analytics Service → ClickHouse for queries.
- **Scaling the redirect path**: stateless Redirect Service → many replicas behind LB →
  Redis L1 cache → DynamoDB/Cassandra as source of truth.

---

### 🧠 Key design choices + trade-offs

| Decision | Option A | Option B | Right call |
|----------|----------|----------|------------|
| Short code generation | **Auto-increment + base62** | MD5 hash / random | Auto-increment — no collision, simple, shardable by ID |
| Redirect type | **302 Temporary** | 301 Permanent | 302 — analytics tracking; 301 breaks click data |
| Read caching | **Redis cache-aside** | DB read replicas only | Redis — sub-millisecond; read replicas still have query overhead |
| Long URL dedup | **Same long URL → new code each time** | Reverse-lookup (long→short) | New code each time — simpler; reverse-lookup needs longURL index |
| Analytics | **Async Kafka → ClickHouse** | Sync write on redirect | Async — redirect latency must be <10ms; analytics can lag |

---

### ⚠️ Confirmed follow-up probes (from reports)

1. "How do you handle **vanity URLs** (user-chosen slugs like `ebay.com/deals`)?" →
   Reservation table: attempt INSERT with unique constraint on `short_code`. On conflict →
   return error with the conflicting existing code. Custom codes skip the ID generator.

2. "**301 or 302?** What's the trade-off?" → 302 for analytics. 301 means the browser
   caches and never calls our server again — we lose all click data. Always 302 if
   click tracking matters (it does for eBay).

3. "How do you scale to **billions of URLs**?" → Shard the KV store by shortCode prefix
   (or by ID range if auto-increment). Redis cluster for cache layer. CDN for the 301 case
   (not relevant for 302 since browser doesn't cache).

4. "What if someone tries a **shortCode that doesn't exist**?" → Bloom filter returns
   "definitely not present" → 404 immediately, no DB hit. Without bloom filter → Redis miss →
   DB miss → expensive for a spam/scan attack.

---

### 📚 SDC cross-refs

| Concept | File |
|---------|------|
| Redis internals (DECR, SET NX EX, persistence) | `../../SystemDesignConcepts/Foundations/Performance-and-Scale/54-redis-internals.md` |
| Caching (cache-aside, eviction, stampede) | `../../SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md` |
| Bloom filter | `../../SystemDesignConcepts/Foundations/Data-Fundamentals/08-bloom-filter.md` |
| Sharding strategy | `../../SystemDesignConcepts/Core-Architecture/Database-Core/38-sharding-strategy.md` |
| Kafka for async analytics | `../../SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` |
| Database types — KV stores | `../../SystemDesignConcepts/Core-Architecture/Database-Core/06-databases-types-and-selection.md` |

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Jul 12, 2026 | **File created.** eBay MTS1 System Design reference covering all 6 confirmed SD questions (4 from research, Notification Service + Ad Click Events + Dropbox + Flash Sale + TinyURL + Own Project). Per question: what to know cold, key trade-offs table, confirmed follow-up probes, SDC cross-refs. Replaces the `sd-lld.md` forward-reference in `ebay-mts1-research.md`. |
