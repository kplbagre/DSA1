# Tools & Terms Glossary — Patterns

> **Purpose:** Quick-lookup for tools and terms used across the Patterns notes (DeepDive + Reference) that don't have a full explanation inline. If you encounter a term you can't explain cold, look it up here first.
>
> **How to update:** Tell the AI which term you're unsure about and it will add an entry. Keep entries short — this is a lookup tool, not a study note.
>
> **Scope:** Only terms that appear in the Patterns folder notes (01–09). Deep concept notes (Foundations, Core-Architecture) are in their own folders.

---

## A

### APNs — Apple Push Notification Service
Apple's cloud relay that delivers push notifications to iOS and macOS devices. Your backend sends a push payload to Apple's APNs servers over HTTP/2; Apple delivers it to the device even when the app is not running (unlike WebSockets which require an active connection).

- **Flow:** Backend → APNs → iOS device
- **When used in patterns:** 03 (feed fan-out to mobile), 07 (real-time updates — mobile background)
- **In interview, if asked:** "For mobile push to iOS devices I use APNs — my backend sends the payload to Apple's relay with the device token, and Apple handles delivery to the device. WebSockets can't run in the background on mobile so APNs is the right tool for backgrounded apps."
- **Full note:** `../Core-Architecture/Service-Communication/46-push-notifications-fanout.md`

---

### AWS Step Functions
Amazon's managed state machine service. You define workflow states and transitions in JSON (the ASL language); Step Functions executes them durably — retries failed steps, tracks state, handles timeouts. You don't write retry loops or state tracking yourself.

- **When used in patterns:** 05 (multi-step processes — alternative to Temporal for AWS shops)
- **In interview, if asked:** "Step Functions is AWS's hosted workflow engine. Each step in the state machine can be a Lambda, an ECS task, or an API call. If a step fails, Step Functions retries with the configured backoff. State is stored in the service — your code is stateless."
- **vs Temporal:** Step Functions is AWS-specific and better for Lambda-heavy architectures; Temporal is open-source and language-native (Java/Go/Python SDK workflows)

---

## C

### Cassandra
Wide-column NoSQL database optimized for high write throughput and linear horizontal scalability. Data is partitioned by a partition key; within a partition, rows are sorted by a clustering key. No joins. Eventual consistency by default (tunable with quorum reads/writes).

- **Key strength:** Handles millions of writes/sec; scales by adding nodes (no resharding downtime)
- **Key weakness:** No ad-hoc queries; you design tables around your access patterns (query-first modeling)
- **When used in patterns:** 09 (proximity search — storing driver location history for ML/analytics), 03 (feed storage at Twitter scale)
- **In interview, if asked:** "Cassandra is a wide-column store — partition key routes to the right node, clustering key sorts rows on disk for efficient range scans. I'd use it when I need write-heavy, time-series-style data at large scale — like driver location history or time-ordered events."
- **Full note:** `../Foundations/Data-Fundamentals/06-databases-types-and-selection.md` (contains Cassandra section)

---

## D

### DLQ — Dead Letter Queue
A special queue where messages land after they've failed to process N times (typically 3–5). Instead of silently dropping failed messages or blocking the main queue with retries forever, failed messages are quarantined in the DLQ for human inspection and manual replay.

- **Flow:** Main queue → consumer tries N times → failure → moves to DLQ → ops team inspects
- **When used in patterns:** 06 (long-running tasks — Step 6 in decision sequence)
- **In interview, if asked:** "After N retries I move the message to a dead letter queue. That way the main queue stays healthy and I don't lose the failed job — an operator can inspect it, fix the root cause (bad payload, external service outage), and replay it manually."
- **Key numbers:** Typically 3–5 retry attempts before DLQ; DLQ retention 7–14 days

---

## F

### FCM — Firebase Cloud Messaging
Google's cloud relay for delivering push notifications to Android devices (and can also deliver to iOS via APNs passthrough). Same role as APNs but for Android.

- **Flow:** Backend → FCM → Android device
- **When used in patterns:** 03 (feed fan-out to mobile), 07 (real-time updates — mobile background)
- **In interview, if asked:** "For Android push notifications I use FCM — same concept as APNs for iOS. My backend sends the payload with the device FCM token; Google delivers it to the device via their relay infrastructure."
- **Full note:** `../Core-Architecture/Service-Communication/46-push-notifications-fanout.md`

---

## G

### Geohash
A compact string encoding of a geographic coordinate (lat/lng). The string is a hierarchy: longer strings = smaller area. Strings sharing a prefix belong to the same geographic cell. Nearby locations usually share a prefix, enabling proximity queries using string prefix matching instead of distance math.

- **Precision guide:** Length 6 ≈ 1.2 km cells, Length 7 ≈ 153 m cells, Length 9 ≈ 4.8 m cells
- **Limitation:** Prefix sharing is approximate — two points can be very close but have different prefixes at a cell boundary. Fix: always query 8 neighboring cells + center cell (9 total).
- **When used in patterns:** 09 (proximity search — Strategy 2)
- **In interview, if asked:** "Geohash encodes lat/lng as a string where shared prefix = shared geographic area. I index the geohash column. For a proximity query, I compute the geohash of the search point, find the 8 neighboring cells, and do a WHERE geohash LIKE 'prefix%' for each — then Haversine-filter the candidates for exact distance."

---

## H

### HMAC — Hash-based Message Authentication Code

A cryptographic signature computed by applying a hash function (typically SHA-256) to the message content combined with a shared secret key. Only parties who know the secret key can generate or verify the signature. Used to prove that a message was sent by a known party and has not been tampered with in transit.

```
Sender:   signature = HMAC-SHA256(secret_key, message_body)
          → sends message + X-Signature header

Receiver: expected = HMAC-SHA256(secret_key, message_body)
          → constant-time compare: expected == received?
          → mismatch → reject (tampered or wrong sender)
```

- **Why constant-time comparison matters:** A naive `signature.equals(received)` returns early on the first mismatched byte, leaking timing information an attacker can exploit. Use `MessageDigest.isEqual()` or `hmac.verify()` which always compare all bytes.
- **Replay attack risk:** HMAC alone proves authenticity but not freshness. Include a timestamp in the payload and reject any message older than 5 minutes to prevent replaying a valid signature from a previously intercepted request.
- **When used in patterns:** Webhook delivery (provider signs outgoing POST; receiver verifies before processing); API request signing (AWS Signature Version 4)
- **Full note:** `../Core-Architecture/Service-Communication/53-webhooks.md` (Section 4 — signature verification code)
- **In interview, if asked:** "I use HMAC-SHA256 with a shared secret to verify webhook payloads. The provider signs the request body with our shared secret; I recompute the HMAC on the raw body and compare in constant time. If they match, the payload is authentic. I also check a timestamp header and reject anything older than 5 minutes to prevent replay attacks."

---

## I

### ICE / STUN / TURN (WebRTC)
The three-protocol stack that lets two browsers establish a direct peer-to-peer connection:

- **STUN (Session Traversal Utilities for NAT):** A tiny server that tells each peer what its public IP/port looks like from the outside. Each peer asks a STUN server "what's my external address?" — needed because both clients are behind NAT routers.
- **ICE (Interactive Connectivity Establishment):** The protocol that tries multiple candidate paths (direct, STUN-derived, TURN-relayed) and picks the best one that actually works.
- **TURN (Traversal Using Relays around NAT):** A relay server used as fallback when direct P2P fails (corporate firewalls, symmetric NAT). Traffic routes through the TURN server — adds latency but guarantees connectivity.

```
Alice ──── STUN "what's my IP?" ──── STUN Server
Bob   ──── STUN "what's my IP?" ──── STUN Server
Alice + Bob exchange ICE candidates
           ──── try direct P2P first ────
           ──── fallback: route via TURN ────
```

- **When used in patterns:** 07 (real-time updates — WebRTC Q&A)
- **In interview, if asked:** "WebRTC uses STUN to discover public IPs, ICE to negotiate the best connection path, and TURN as a relay fallback when direct P2P is blocked. My signaling server (WebSocket) only handles the initial handshake — actual media flows directly between peers."
- **Key insight:** Your server only coordinates the handshake. Once connected, video/audio flows peer-to-peer, not through your servers.

---

## K

### Kafka Consumer Group
A named group of consumer processes that jointly consume a Kafka topic. Each partition is assigned to exactly one consumer in the group at a time — so the group processes the topic in parallel (one consumer per partition), and each message is processed by exactly one consumer in the group.

- **Key property:** Two different consumer groups both subscribed to the same topic each get ALL messages independently. This is what enables fan-out: analytics group + main DB group + search group all consuming the same OrderCreated events.
- **When used in patterns:** 02 (scaling writes), 05 (multi-step — each saga step can be a consumer group), 06 (long-running tasks)
- **In interview, if asked:** "Each consumer group gets its own independent copy of every message. If I have 3 services that all need to react to the same event, I create 3 consumer groups — each gets every event, processes at its own pace, with its own offset pointer."

---

### Kafka Offset
A monotonically increasing integer that identifies a message's position within a partition. Each consumer group maintains its own offset per partition — tracking "how far have I consumed." If a consumer crashes, it resumes from its last committed offset.

- **At-least-once delivery:** Consumer commits offset only after successfully processing. If it crashes after processing but before committing, it reprocesses on restart. Messages must be idempotent.
- **At-most-once delivery:** Commit offset immediately on receipt. If processing fails after commit, the message is lost.
- **When used in patterns:** 02 (scaling writes — replay from offset), 06 (long-running tasks)
- **In interview, if asked:** "Kafka offset is a consumer group's bookmark in a partition. I control when to commit — commit after processing for at-least-once (safe but need idempotency), or before processing for at-most-once (risky). For financial data I always commit after processing."

---

### Kafka Partition
The unit of parallelism in Kafka. A topic is divided into N partitions. Each partition is an ordered, append-only log. Messages within a partition maintain order; there is no ordering guarantee across partitions. Partitions enable parallel consumption: N consumers in a group → N partitions → N-way parallelism.

- **Partition key:** You choose which partition a message lands in (hash of user_id, order_id, etc.). Same key → same partition → ordered processing for that key.
- **When used in patterns:** 02 (scaling writes — fan-out to worker partitions), 06 (long-running tasks — parallel job workers)
- **In interview, if asked:** "I partition by user_id or order_id to guarantee ordering for the same entity — all events for user 123 go to the same partition, processed by the same consumer, in order. Cross-user ordering doesn't matter and I get full parallelism."

---

### Kafka Retention
Kafka keeps messages on disk for a configurable period (default 7 days) regardless of whether consumers have read them. Unlike RabbitMQ which deletes messages after ACK, Kafka messages can be replayed.

- **Use cases:** Replay events if a consumer had a bug (reprocess last 7 days); bootstrap a new service by replaying historical events; run multiple consumer groups at different offsets.
- **When used in patterns:** 02 (scaling writes — replay for bug fixes)
- **In interview, if asked:** "Kafka retains messages for 7 days by default. If our analytics pipeline had a bug last Tuesday, I can reset its consumer group offset to Tuesday morning and replay — all events are still on disk. RabbitMQ can't do this since it deletes on ACK."

---

### Kafka Topic
A named stream of events in Kafka. Analogous to a database table but for events. Producers write to a topic; consumer groups read from it. A topic is divided into partitions for parallelism.

- **Naming convention:** Usually reflects the event type: `order.created`, `user.signup`, `payment.processed`
- **When used in patterns:** 02 (scaling writes), 05 (multi-step processes — events between steps), 06 (long-running tasks)
- **In interview, if asked:** "I'd create an `order.created` topic. The payment service, inventory service, and notification service each have their own consumer group subscribed to it — each processes independently without knowing about the others."

---

## P

### PostGIS
A PostgreSQL extension that adds geographic object types (`GEOMETRY`, `GEOGRAPHY`) and spatial indexing (R-tree via GIST index). Enables exact distance queries with `ST_DWithin(location, point, radius_meters)` using true geodesic math, not approximations.

- **When to use:** Static or slowly-changing locations (restaurants, stores); exact distance required; dataset < ~100M points
- **When NOT to use:** Frequently updating objects (drivers) — every location update modifies the spatial index
- **When used in patterns:** 09 (proximity search — Strategy 3, recommended default for restaurants/stores)
- **In interview, if asked:** "For static locations I use PostGIS — store coordinates as GEOGRAPHY type, create a GIST spatial index, and query with ST_DWithin. It gives exact geodesic distance and the R-tree index prunes millions of rows to dozens in microseconds."

---

## R

### Redis GEOADD / GEORADIUS
Redis geo commands that store locations as members of a sorted set with their coordinates encoded as a geohash-based score. `GEOADD` adds/updates a member's position; `GEORADIUS` finds all members within a given distance.

```
GEOADD drivers:available -122.41 37.77 "driver:abc123"   # upsert driver position
GEORADIUS drivers:available -122.41 37.77 5 km ASC COUNT 5  # 5 nearest within 5km
```

- **Under the hood:** Each member's score is a 52-bit integer encoding of its geohash. Sorted by score → nearby members cluster together → range scan finds neighbors efficiently.
- **When used in patterns:** 09 (proximity search — Strategy 4 for moving objects like drivers)
- **In interview, if asked:** "Redis GEOADD is an O(log N) upsert into a sorted set using geohash encoding. Each driver update is a single GEOADD call. GEORADIUS decodes the geohash scores back to lat/lng and computes distances — all in-memory, sub-millisecond."

---

### Redis INCR
Atomic increment of a Redis integer key. Because Redis is single-threaded, `INCR key` is guaranteed to be atomic — no two concurrent calls can read the same value and both increment from it. Used for counters, rate limiter windows, and sequence numbers.

```
INCR page_views:home       # → 1, 2, 3, ... atomically
INCR user:123:reqs:minute  # → rate limiter counter
```

- **Related commands:** `INCRBY key N`, `DECR key`, `DECRBY key N`
- **When used in patterns:** 04 (dealing with contention — Strategy 3, Redis atomic operations for likes/views)
- **In interview, if asked:** "INCR is atomic in Redis because Redis processes one command at a time. There's no way to get a race condition between two concurrent INCRs — they serialize automatically."

---

### Redis Lua Scripts
Server-side scripts that execute atomically in Redis. The script runs as a single Redis command — no other command can interleave between lines. This gives you multi-step atomicity without MULTI/EXEC and without multiple round-trips.

```lua
-- Rate limiter: increment counter AND set TTL in one atomic operation
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
```

- **Why not just MULTI/EXEC?** MULTI/EXEC is a transaction but doesn't support conditional logic inside the block. Lua can read a value and branch based on it — all atomically.
- **When used in patterns:** 04 (dealing with contention — rate limiter, inventory check-and-decrement)
- **In interview, if asked:** "I use Redis Lua for atomic read-modify-write operations that need conditional logic. The script executes as a single Redis command — no interleaving. It's how I implement sliding window rate limiting: increment counter, check if over limit, return result — all in one server round-trip."

---

### Redis MULTI/EXEC
Redis transaction block. Commands between MULTI and EXEC are queued and executed atomically as a batch. No other client's commands can interleave between them. However, unlike Lua scripts, there's no conditional branching — you can't check a value inside the block and decide whether to continue.

```
MULTI
ZREM drivers:available "driver:abc123"
ZADD drivers:on_pickup "driver:abc123"
EXEC
```

- **When used in patterns:** 09 (proximity search — atomically moving a driver between state sorted sets)
- **WATCH command:** Optimistic locking for Redis transactions — WATCH a key before MULTI; if it changes before EXEC, the transaction aborts. Caller retries.
- **In interview, if asked:** "MULTI/EXEC is for atomic batch operations where all-or-nothing is required but you don't need conditional logic inside. Moving a driver from 'available' to 'on_pickup' is a natural MULTI/EXEC — both ZSETs must update together or not at all."

---

### Redis Pub/Sub
See the Q&A section in `DeepDive/07-real-time-updates.md` for the full explanation. Short version:

- **SUBSCRIBE channel** — registers a server as a subscriber; Redis delivers any subsequent PUBLISH on that channel instantly
- **PUBLISH channel message** — delivers payload to all current subscribers; fire-and-forget, no persistence
- **PSUBSCRIBE pattern** — subscribe to all channels matching a glob (e.g., `room:*`)
- **Latency:** < 1ms (in-memory delivery)
- **Critical limitation:** No message persistence. If a subscriber is down at publish time, it misses the message. This is why WebSocket architectures pair Redis Pub/Sub with a DB-backed event log for catch-up.
- **When used in patterns:** 07 (real-time updates — routing messages to the right WebSocket gateway server)

---

### Redis SETNX / SET EX NX (Distributed Lock)
`SETNX key value` — Set if Not Exists. Sets the key only if it doesn't already exist; returns 1 on success, 0 if the key already existed. Combined with `EXPIRE` (or the atomic `SET key value EX seconds NX` form in Redis 2.6+), this implements a distributed lock.

```
SET lock:order_123 "worker-7" EX 30 NX
-- Returns OK → lock acquired (you have 30 seconds)
-- Returns nil → lock held by someone else
```

- **Why EX is mandatory:** Without TTL, a crashed lock holder leaves the lock permanently (deadlock). TTL ensures the lock auto-releases.
- **When used in patterns:** 04 (dealing with contention — mentioned as part of Redis atomic ops), 05 (multi-step — lock during saga compensation)
- **Full note:** `../Foundations/Concurrency-and-Consistency/06-distributed-locking.md`
- **In interview, if asked:** "I use SET key value EX 30 NX — atomically acquires the lock with a 30-second TTL. If the holder crashes, the key expires and the lock releases automatically. For production I use Redlock across multiple Redis nodes to handle Redis node failure."

---

### Redis Sorted Set (ZSET)
A Redis data structure where each member (a string, e.g., a post_id or job_id) has an associated floating-point score. Members are always stored sorted by score. All operations that depend on sort order are O(log N).

**Key commands:**
```
ZADD feed:bob 1719820800 "post_id:789"     # add/update member with score
ZREVRANGE feed:bob 0 49                    # top 50 members (highest score first)
ZREVRANGEBYSCORE feed:bob +inf 0 LIMIT 0 50  # range query by score
ZRANGEBYSCORE delayed_jobs 0 {now}         # jobs due now (score = due timestamp)
ZPOPMAX leaderboard 1                      # atomically remove + return highest-scored member
ZREM set:name member                       # remove member (returns 1 if existed, 0 if not)
ZCARD set:name                             # count of members
```

**Use cases in the patterns notes:**
- **Feed (03):** `feed:userId` ZSET with timestamp as score → chronological feed with O(log N) insert and O(log N) page fetch
- **Rate limiter (04):** ZSET with request timestamp as score → `ZREMRANGEBYSCORE` removes old entries, `ZCARD` gives current count in window
- **Leaderboard (01, 04):** ZSET with score as the sort key → `ZADD` updates rank, `ZREVRANGE` returns top-N
- **Delayed job queue (06):** ZSET with due timestamp as score → `ZRANGEBYSCORE 0 {now}` finds due jobs
- **Priority queue (06):** ZSET with priority as score → `ZPOPMAX` atomically grabs highest-priority job

- **In interview, if asked:** "Redis Sorted Set stores members with numeric scores, kept sorted at all times. ZADD is O(log N) insert/update; ZREVRANGE is O(log N) range retrieval. I use it for feeds (score = timestamp), leaderboards (score = points), delayed queues (score = due epoch), and sliding window rate limiting (score = request timestamp)."
- **Full explanation:** `../Foundations/Data-Fundamentals/43-pagination-cursor-based.md` (has full Redis ZSET section)

---

### Redis TTL / EXPIRE
Every Redis key can have an expiry time. After the TTL elapses, Redis automatically deletes the key. This is the foundation of cache invalidation, session expiry, and ephemeral state management.

```
SET session:abc123 "{...}" EX 3600   # expires in 1 hour (inline TTL)
SET key value
EXPIRE key 3600                       # set TTL after creation
TTL key                               # → seconds remaining (-1 = no TTL, -2 = expired/missing)
PERSIST key                           # → remove TTL (make key permanent)
```

- **When used in patterns:** Everywhere — cache entries (01), rate limiter windows (04), WebSocket connection state (07), driver location entries (09)
- **In interview, if asked:** "I set TTL on every Redis key that shouldn't live forever. For session tokens it's 1 hour; for rate limiter windows it's the window duration; for driver location it's 60 seconds so stale drivers auto-expire. TTL is the simplest cache invalidation strategy."

---

## T

### Temporal (Durable Workflow Engine)
An open-source platform for writing durable, reliable workflows as code. You write a workflow function in Java/Go/Python; Temporal records every step's result. If the process crashes mid-workflow, it replays from the last checkpoint — your workflow function re-runs from the top but skips already-completed steps (their results are replayed from history).

**Core concepts:**
- **Workflow:** A function that orchestrates the business process (not idempotent — runs once logically)
- **Activity:** A single unit of work (DB call, API call) that Temporal retries automatically on failure
- **Signal:** External event sent to a running workflow (e.g., "user cancelled order")
- **Continue-as-New:** Start a new workflow execution with accumulated state to prevent history growing unbounded

```
workflow OrderFlow(order):
    result = await charge_payment(order)    # Temporal retries automatically on failure
    if result == DECLINED:
        return FAILED
    inv = await reserve_inventory(order)
    if inv == OUT_OF_STOCK:
        await refund_payment(order)         # compensating activity
        return FAILED
    await send_confirmation(order)
    return COMPLETED
```

- **When used in patterns:** 05 (multi-step processes — Approach 4, Durable Execution Engines)
- **vs AWS Step Functions:** Temporal is open-source, language-native (workflow code is real Java/Python), good for complex branching. Step Functions is AWS-managed, JSON state machine definition, better for Lambda-heavy shops.
- **In interview, if asked:** "Temporal lets me write the workflow as normal code. If a step fails, Temporal replays the workflow from the start but skips completed steps — their return values are replayed from durable history. I get automatic retries, timeouts, and compensation without managing any state myself."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | **File created.** Initial pass: Redis ZSET, Pub/Sub, INCR, MULTI/EXEC, Lua, SETNX, GEOADD, TTL; APNs, FCM; ICE/STUN/TURN; Kafka topic/consumer group/partition/offset/retention; DLQ; PostGIS; Geohash; Cassandra; Temporal; AWS Step Functions. All terms sourced from Patterns DeepDive/Reference notes 01–09. |
| July 9, 2026 | Added **HMAC** (H section) — cryptographic signature primitive used in webhook verification and API signing. Referenced from `53-webhooks.md`. |
