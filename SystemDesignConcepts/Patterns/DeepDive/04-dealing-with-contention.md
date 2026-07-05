# Pattern Deep Dive: Dealing with Contention

> **Read this when:** You need to understand how to handle many concurrent users competing for the same resource — seats, inventory, counters, rate limit slots — without data corruption, deadlocks, or performance collapse.
> **Pre-interview refresh:** Use `Reference/04-dealing-with-contention.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

Many users are simultaneously trying to modify the **same** resource. Unlike Scaling Writes (where writes are independent), here writes **conflict**:

- Flash sale: 50,000 users hit "Buy Now" for 100 items at 12:00:00 simultaneously
- Seat booking: Two users click the same seat in BookMyShow at the same moment
- Likes counter: 10,000 users like a viral Instagram post in one second
- Rate limiter: 1,000 requests/sec all trying to check and increment the same counter
- Leaderboard: 10,000 players update their scores simultaneously

The root cause: a database row (or any shared resource) can only be safely modified by one transaction at a time. When many transactions fight for the same row, they queue up — and that queue is your latency problem.

Contention is a **coordination problem**, not a throughput problem. Adding more servers doesn't help — they all still fight for the same row. The fix is changing HOW writes are coordinated.

---

## 💡 Core Insight

**Contention exists because multiple writers share a single resource.** There are only two ways to resolve it:

1. **Serialize** — make writers take turns (one wins, others wait or retry)
2. **Distribute** — break the shared resource into N independent pieces so writers rarely conflict

Both approaches have trade-offs. The pattern is choosing the right one based on:
- How often do conflicts actually occur? (frequency)
- What happens when a conflict occurs? (failure semantics)
- How many concurrent writers are there? (scale)

> **KEY INSIGHT:** "If conflicts are rare → detect at commit (optimistic). If conflicts are frequent → prevent with locking (pessimistic). If conflicts are constant → eliminate with distribution (sharding/atomic ops)."

---

## 🗂️ The 6 Strategies (Simplest → Most Extreme)

---

### Strategy 0 — Conditional Writes (Single Atomic Statement)

🧠 **Mental model:** Airline inventory — `UPDATE seats SET status='booked' WHERE id=42 AND status='available'`. One statement, zero overhead. The DB engine handles the race atomically. Check rows affected: 1 = you got it, 0 = someone else did.

The simplest form of contention handling: express the entire check-and-modify as a single SQL statement. The WHERE predicate IS the guard. No version column, no transaction, no lock.

**When to use:**
- The business condition fits in a WHERE clause (`qty > 0`, `status = 'available'`)
- Single-row atomic update, not multi-step business logic
- Try this before reaching for optimistic or pessimistic locking

**Example:**

```sql
-- Decrement inventory only if quantity > 0 — single atomic statement
UPDATE inventory
SET quantity = quantity - 1
WHERE item_id = 42 AND quantity > 0;
-- affected rows = 1 → success, affected rows = 0 → out of stock
```

No race condition: the WHERE predicate and the SET execute atomically inside the DB engine. Another transaction cannot interleave between the check and the decrement.

**Limitation:** Only works when the entire logic fits in one statement. For multi-step operations (check availability → charge payment → reserve) you need a transaction (Strategies 1–2).

---

### Strategy 1 — Optimistic Locking (version column)

🧠 **Mental model:** Google Docs — you edit a document offline. When you sync, it checks if anyone changed the same section. If yes: conflict detected → resolve it. If no: merge succeeds. No locking while you edited, only a version check at commit.

Assume conflicts are rare. Read the row, modify in memory, write back — but only if no one else modified it while you were working.

**When to use:**
- Read-modify-write cycles where conflicts are rare (< 10% of writes conflict)
- Long think-time operations (user edits a document for 5 minutes, then saves)
- Distributed systems where taking a DB lock across a network round-trip is risky

**When NOT to use:**
- Flash sales, popular counters — conflicts are nearly 100%, causing infinite retries
- Operations that cannot be retried (payment already sent)

**How it works:**

**Steps in plain English:**
1. **Read** — Fetch the row and record its current `version` number.
2. **Compute** — Perform business logic in memory (no DB lock held).
3. **Write with guard** — UPDATE with `WHERE version = <version you read>`.
4. **Check result** — 0 rows affected → conflict (someone else wrote first) → retry from step 1. 1 row affected → success.
5. **Version bumped** — DB increments version; next writer must use the new version.

```
DB schema:
  CREATE TABLE inventory (
    item_id    BIGINT PRIMARY KEY,
    quantity   INT NOT NULL,
    version    INT NOT NULL DEFAULT 0
  );

Read:  SELECT quantity, version FROM inventory WHERE item_id = 42;
       → quantity=5, version=7

Modify in memory: quantity = 5 - 1 = 4

Write: UPDATE inventory
       SET quantity = 4, version = 8
       WHERE item_id = 42 AND version = 7;
       → affected rows = 1 → SUCCESS (version matched)
       → affected rows = 0 → CONFLICT (someone else updated first → retry)
```

```
Timeline with 2 concurrent users:

T=0:  User A reads: qty=5, v=7      User B reads: qty=5, v=7
T=1:  User A updates: WHERE v=7 ✅  User B updates: WHERE v=7 ❌ (version is now 8)
T=2:                                 User B retries: reads qty=4, v=8 → succeeds
```

**Cost:** Under high contention, retry rate explodes. If 100 users try simultaneously, 99 of them retry → 99 retries become 98 conflicts → ... → O(N²) total attempts. Don't use optimistic locking for high-contention resources.

**ABA problem:** Process A reads version=7. While A computes, B updates version 7→8→7 (two updates, net zero change). A's `WHERE version=7` succeeds — but A is operating on stale state even though the version matched. Fix: use a strictly monotonically increasing version (never decreases). Standard integer auto-increment is safe; timestamp-based versions are vulnerable to clock skew and can go backwards.

---

### Strategy 2 — Pessimistic Locking (SELECT FOR UPDATE)

🧠 **Mental model:** BookMyShow seat checkout — the moment you click "Confirm seat 23A," the system acquires an exclusive DB lock on that row. No one else can book it until you complete checkout or your 10-minute session expires and the lock is released.

Assume conflicts will happen. Lock the row before reading it. No one else can modify (or even read-for-update) until you release the lock.

**When to use:**
- Business-critical operations where data corruption is unacceptable (financial transactions, seat booking at checkout)
- Short, fast operations (lock is held for < 100ms)
- Moderate concurrency (not thousands of simultaneous writers)

**When NOT to use:**
- Long-running operations (lock held for seconds → everyone else waits)
- High concurrency (thousands of simultaneous writers → lock queue → timeout cascade)
- Microservices that span multiple DB transactions (distributed lock is needed instead)

**How it works:**

**Steps in plain English:**
1. **BEGIN** — Open a database transaction.
2. **Lock** — `SELECT FOR UPDATE` acquires a row-level lock. All other transactions trying the same row block at this line.
3. **Check** — Verify the business condition (is seat still available? is quantity > 0?).
4. **Mutate** — If condition holds, UPDATE the row.
5. **COMMIT** — Lock released. Next waiter in the queue proceeds immediately.

```
BEGIN;
SELECT quantity FROM inventory WHERE item_id = 42 FOR UPDATE;
-- Other transactions trying SELECT FOR UPDATE on item_id=42 block here

-- Check: quantity > 0?
UPDATE inventory SET quantity = quantity - 1 WHERE item_id = 42;
COMMIT;
-- Lock released. Next waiting transaction proceeds.
```

```
Concurrent writers with SELECT FOR UPDATE:

User A ──▶ acquires lock ──▶ checks qty=5 ──▶ decrements ──▶ commits ──▶ releases
User B ──▶ waits for lock ────────────────────────────────────▶ reads qty=4 ──▶ ...
User C ──▶ waits for lock ─────────────────────────────────────────────────────▶ ...

Writers are serialized. No conflicts. But throughput = 1 writer per lock cycle.
Lock cycle time ≈ network RTT + DB processing = 20–50ms
Max throughput ≈ 1 / 50ms = 20 writes/sec per resource.
```

**Deadlock risk:** If Transaction A holds lock on row 1 and waits for row 2, while Transaction B holds row 2 and waits for row 1 — deadlock. DB detects and kills one. Fix: always acquire locks in the same order (sort row IDs ascending before locking).

**Write skew (the subtle cross-row invariant bug):** Two transactions each read a set of rows and make a decision based on the aggregate. Example: two doctors both check "is at least 1 doctor on call?" → both see yes → both go off-call → no one is on call. Neither modified a row the other read — `SELECT FOR UPDATE` on their own rows doesn't prevent this. Fix: use `SERIALIZABLE` isolation level. Postgres detects the read-write dependency between the two transactions and aborts one. Use SERIALIZABLE when you have a cross-row invariant that single-row locking cannot protect.

---

### Strategy 3 — Redis Atomic Operations

🧠 **Mental model:** Instagram like on a viral Reels post — 500K likes/sec. Each like is a Redis INCR (single-threaded, atomic). No DB on the hot path. Like count updated in sub-millisecond. DB synced asynchronously in the background.

Use Redis single-threaded command execution to perform atomic check-and-modify operations without any locking overhead.

**When to use:**
- High-frequency counters (rate limiters, view counts, like counts)
- Simple check-and-decrement (inventory check, rate limit check)
- Operations expressible as: read → modify → write in a single atomic command

**When NOT to use:**
- Complex multi-step business logic (seat booking with payment — too much for Redis)
- Need durable ACID guarantees (Redis AOF gives durability but not ACID transactions)
- Multi-key atomic operations (Lua script works but becomes complex)

**How it works (inventory decrement):**

```
Redis Lua script — executes atomically (no other command runs between steps):

local key = KEYS[1]           -- "inventory:item:42"
local decrement = ARGV[1]     -- "1"

local current = redis.call('GET', key)
if current == false then
    return -1  -- key doesn't exist
end
if tonumber(current) < tonumber(decrement) then
    return -2  -- insufficient inventory
end
return redis.call('DECRBY', key, decrement)  -- decrement and return new value
```

```
                App Server
                    │
          ┌─────────▼──────────┐
          │  Redis (single     │
          │  threaded)         │
          │  DECRBY or Lua     │──▶ atomic: no race condition possible
          │  script            │    Result: new_quantity (or error if < 0)
          └─────────┬──────────┘
                    │
          ┌─────────▼──────────┐
          │  Postgres          │──▶ async persistence
          │  (async sync)      │    (Redis is source for inventory count,
          └────────────────────┘     Postgres is source of truth for orders)
```

**Throughput:** Redis single-threaded executes ~100K–1M commands/sec. For a counter, that's 100K–1M atomic increments/sec — far more than any DB can handle.

---

### Strategy 4 — Sharded Counters

🧠 **Mental model:** YouTube global view count — 1B views/day. A single Redis key `views:video:X` would be a bottleneck at peak. 16 shard keys: each view INCR hits a random shard. Reading the total sums all 16 in one MGET. No single key is a bottleneck.

Distribute a single high-contention counter across N shards. Each write randomly picks one shard and increments it. Reads sum all shards.

**When to use:**
- Very high-write counters where even Redis single-key throughput is a bottleneck
- Global counters: total likes, total views, total orders
- Any counter where exact real-time accuracy is not required (eventual consistency acceptable)

**When NOT to use:**
- Inventory that must be exact (you can't oversell)
- Counters requiring strong consistency (financial totals)

**How it works:**

```
Setup: 16 shards per counter
Key naming: "likes:post:789:shard:{0..15}"

Write:
  shard = random(0, 15)
  INCR "likes:post:789:shard:{shard}"
  → 16 independent keys, writes distributed randomly across them
  → 16x reduction in per-key write rate

Read:
  GET all 16 shard keys → sum them
  → exact total (eventually consistent — reads may miss in-flight increments)
```

```
Without sharding:
  10,000 writes/sec → all hit "likes:post:789" → single key bottleneck

With 16 shards:
  10,000 writes/sec → ~625 writes/sec per shard key
  Read = SUM(shard:0 + shard:1 + ... + shard:15)
  Read cost = 16 GET commands (acceptable; use MGET for one round-trip)
```

---

### Strategy 5 — Queue-Based Serialization

🧠 **Mental model:** Ticketmaster Taylor Swift general sale — 1M users compete for 50K seats. A Kafka partition keyed by event_id serializes all requests FIFO. First in queue = first served. Zero lock contention because only one consumer processes each key.

Route all writes for a shared resource through a FIFO queue. A single consumer processes them one at a time. No concurrent access possible.

**When to use:**
- Resource requires complex check-and-modify that can't be expressed as a single Redis command
- Strict FIFO fairness is required (first-come-first-served for seat allocation)
- Throughput is acceptable at queue processing rate

**When NOT to use:**
- High throughput required (queue serialization caps at queue consumer speed)
- Multiple independent resources (each resource needs its own queue — management overhead)

**How it works:**

```
Flash sale inventory:

All "buy item:42" requests ──▶ Kafka partition (keyed by item_id)
                                        │
                               Single consumer (one per item_id)
                                        │
                               Check inventory → allocate → persist
                                        │
                               Return result to caller (via callback or polling)

Throughput: ~1K–10K allocations/sec per consumer (adequate for most flash sales)
Fairness: exact FIFO — first request in queue is first served
No conflicts: only one writer per resource at any time
```

---

## 🧭 Decision Sequence

```
START: Many concurrent writers targeting the same resource

Step 0 ── Can the check fit in a single WHERE predicate?
          UPDATE ... SET x = x - 1 WHERE x > 0 AND item_id = 42?
                → Conditional write (Strategy 0). No transaction needed. Try first.
          Needs multi-step logic (check + charge + reserve)?
                → Requires a transaction. Go to Step 1.

Step 1 ── How often do conflicts actually occur?
          Rarely (< 10%)?  → Optimistic locking. Simple, no blocking.
          Often (> 50%)?   → Never use optimistic locking. Go to Step 2.

Step 2 ── What's the write rate to this resource?
          < 100 writes/sec per resource?
                → Pessimistic locking (SELECT FOR UPDATE). Simple, safe.
          > 100 writes/sec?
                → Pessimistic locking will queue and timeout. Go to Step 3.

Step 3 ── Can the operation be expressed atomically?
          Simple counter (increment, decrement, check-and-decrement)?
                → Redis atomic ops (INCR, DECRBY, Lua script).
          Complex business logic (check, allocate, record)?
                → Queue-based serialization (Kafka FIFO per resource key).

Step 4 ── Is one Redis key still a bottleneck?
          > 1M writes/sec to same key?
                → Sharded counters (split across N Redis keys, sum on read).

Note: Steps 3 and 4 are alternatives, not sequential.
Choose based on operation complexity, not just write rate.
```

---

## 🎨 Visual — Decision by Resource Type

```
Resource: SEAT INVENTORY (complex, exact, once-only)
Strategy: Pessimistic locking (SELECT FOR UPDATE) at checkout
          + Redis pre-check (soft reserve before DB lock)

   User clicks seat ──▶ Redis check (qty > 0?) ──▶ No → reject fast
                                    │ Yes
                         ──▶ SELECT FOR UPDATE on DB row
                                    │ Lock acquired
                         ──▶ Re-check (qty still > 0?)
                                    │ Yes
                         ──▶ Decrement + create booking
                         ──▶ COMMIT (lock released)

Resource: LIKE COUNTER (simple, high-volume, approximate OK)
Strategy: Redis INCR (atomic) → async persist to Postgres

   User likes post ──▶ Redis INCR "likes:post:789" ──▶ return new count
                                    │ (async, periodic)
                         ──▶ UPDATE posts SET likes = <redis_count>

Resource: FLASH SALE INVENTORY (simple check-and-decrement, very high volume)
Strategy: Redis Lua script (atomic check + decrement)

   User buys item ──▶ Lua: if qty >= 1 then DECRBY 1 else RETURN -1
                         → -1: "Sold out" → reject
                         → new qty: success → create order in DB

Resource: GLOBAL VIEW COUNTER (100K+ writes/sec)
Strategy: Sharded counters (16 shards in Redis)

   View event ──▶ INCR "views:video:X:shard:{random 0-15}"
   Read total  ──▶ MGET all 16 shards → SUM

KEY INVARIANT:
   Match strategy to conflict frequency and operation complexity.
   Over-engineering (queue for a simple counter) adds latency.
   Under-engineering (optimistic lock for a flash sale) causes retry storms.
```

---

## 🔬 Interview Q&A

### Q: "50,000 users hit 'Buy Now' simultaneously for 100 items. Walk me through your design."

> Three layers: (1) Pre-filter with Redis — before hitting any DB, atomically decrement a Redis inventory counter. Lua script: `if qty >= 1 then DECRBY 1 else return SOLD_OUT`. This rejects 49,900 requests at Redis speed (~1ms) without touching the DB. (2) For the 100 who pass Redis check: create order record in DB asynchronously via a queue. (3) Redis counter is a soft limit — reconcile with DB periodically in case of Redis crash. The key insight: don't let 50K users hit SELECT FOR UPDATE simultaneously — your DB connection pool exhausts and the whole system dies.

---

### Q: "Two users book the same seat at exactly the same millisecond. How do you prevent double-booking?"

> Use SELECT FOR UPDATE in a DB transaction. Seat booking at checkout is the canonical pessimistic locking use case: (1) BEGIN transaction. (2) SELECT seat WHERE id=42 AND status='available' FOR UPDATE — this acquires a row lock. Second user hitting this for the same seat ID blocks here. (3) If seat is still available → UPDATE status='reserved', create booking record. COMMIT. Lock released. (4) The blocked second user's lock is now acquired — they see status='reserved' → return "seat already taken." No double booking possible. Trade-off: at checkout time this is correct; at browse time (just showing available seats) you'd use optimistic or no locking.

---

### Q: "What's a deadlock and how do you prevent it in a booking system?"

> Deadlock: Transaction A holds a lock on seat 5 and waits for seat 7. Transaction B holds seat 7 and waits for seat 5. Both wait forever — DB detects and kills one. Prevention: always acquire locks in a consistent order. Sort seat IDs numerically before locking. If all transactions lock seats in ascending order, circular waits are impossible. Also: set lock timeout (`SET lock_timeout = '100ms'`) so a transaction that can't acquire a lock within 100ms fails fast rather than waiting indefinitely, which is better for user experience.

---

### Q: "Your like counter hit 500K likes/sec on a viral post. Redis INCR on one key can't keep up. What do you do?"

> Sharded counters. Instead of one Redis key `likes:post:789`, create 64 shard keys: `likes:post:789:shard:0` through `:shard:63`. Each write randomly picks one shard and INCRs it. 500K writes/sec / 64 shards = ~7.8K writes/sec per shard — well within Redis single-key throughput. Read total: MGET all 64 shard keys, sum them. Cost of reads goes up slightly (64 fetches) but can be batched with MGET in one round-trip. Accuracy is eventual — reads may miss in-flight increments for a few milliseconds, which is acceptable for a like count.

---

### Q: "Explain optimistic vs pessimistic locking. When do you use each?"

> Optimistic: read-modify-write with a version check. Assumes conflicts are rare. No blocking — concurrent reads proceed freely. Fails on commit if version has changed — retry. Best for: infrequent conflicts, long think-time operations (document editing), distributed systems where holding a DB lock across network calls is dangerous. Pessimistic: lock at read time. Assumes conflicts will happen. Blocks all other writers until commit. Best for: high-contention resources where collision is expected (seat booking), operations that cannot be retried safely (single-chance allocation). The heuristic: if more than ~20% of operations would result in a conflict, optimistic locking's retry rate becomes prohibitive — switch to pessimistic.

---

### Q: "How do you implement a distributed rate limiter that's atomic?"

> Redis Lua script — the standard production approach. Script: `local current = redis.call('INCR', key); if current == 1 then redis.call('EXPIRE', key, window_seconds) end; return current`. This atomically increments a per-user-per-window counter and sets TTL on first use. Caller checks: if returned value > limit, reject. Single round-trip per request. Because Redis is single-threaded, INCR is atomic — no two requests can read the same count and both think they're under the limit. For sliding window instead of fixed window: use a Redis Sorted Set (ZSET) — add timestamp of each request as a score, ZREMRANGEBYSCORE to remove old entries, ZCARD to get current count.

---

### Q: "What's the 'thundering herd' in the context of contention (different from caching)?"

> In contention: when a lock is released, all waiters wake up simultaneously and attempt to acquire the lock. Only one succeeds; the rest immediately fail and retry — causing another wave of contention. This is different from the cache thundering herd (where a cache miss triggers simultaneous DB queries). Fix in locking context: queue-based serialization (waiters queue up in FIFO order, next in line gets the lock) rather than all competing simultaneously. Alternatively, introduce random jitter in retry backoff so retries don't synchronize.

---

### Q: "How do you handle inventory in a flash sale if Redis crashes and the soft-reserve count is lost?"

> Redis stores the soft reserve count; Postgres is the source of truth for actual orders. If Redis crashes: (1) Redis counter is lost — all subsequent requests go to DB (fallback mode). (2) On Redis restart, recompute the available count from Postgres: `available = initial_quantity - COUNT(confirmed_orders)` and warm Redis with this value. (3) During the window when Redis is down and before warm-up: route inventory checks to DB with pessimistic locking (slower but correct). The key design: never let Redis failure cause overselling. Redis is an optimization (fast pre-filter); Postgres is the final gate.

---

### Q: "What's the difference between row-level locking and table-level locking?"

> Row-level locking (SELECT FOR UPDATE): locks only the specific rows being modified. Other transactions can modify different rows in the same table simultaneously. Postgres and MySQL both use row-level locking by default for DML. High concurrency on different rows is fine. Table-level locking: locks the entire table. All other modifications block. Used for DDL (ALTER TABLE), bulk operations (TRUNCATE), or explicitly (LOCK TABLE). In interview context: always prefer row-level locking for OLTP. Table-level locks are a performance disaster for concurrent workloads and should only appear in maintenance windows.

---

## ⚠️ Anti-patterns

- **Using optimistic locking for high-contention resources.** If 1,000 users simultaneously try to book the last seat with optimistic locking, 999 of them will fail their version check and retry. Those 999 retries generate another 998 conflicts, and so on — an O(N²) retry storm that increases DB load exactly when you can least afford it. The moment you know a resource will have high simultaneous write contention, pessimistic locking or Redis-based pre-filtering is the correct starting point.

- **Holding a pessimistic lock across a network call.** Pattern to avoid: `BEGIN → SELECT FOR UPDATE → [call external payment API: 2 seconds] → COMMIT`. The DB lock is held for 2+ seconds while waiting for the payment gateway. During those 2 seconds, every other user who tries to access the locked row queues up. At scale, DB connection pool exhausts and your system goes down. Fix: do all external calls before acquiring the lock. Acquire the lock, do only the fast in-DB state change, commit immediately.

- **Ignoring the need for idempotency with retries.** Optimistic locking encourages retries. Retries require idempotency: if "decrement inventory" is retried, the second attempt must not decrement again. Use idempotency keys on the order record: `INSERT INTO orders (idempotency_key, ...) ON CONFLICT (idempotency_key) DO NOTHING`. This makes retries safe and prevents double-decrement or double-charge.

---

## 🗺️ Problems Map

| Interview Problem | Why Contention Applies | Primary Strategy |
|---|---|---|
| Design BookMyShow / Ticketmaster | Multiple users booking same seat simultaneously | SELECT FOR UPDATE at checkout; Redis pre-filter |
| Design Flash Sale (Amazon, Flipkart) | 50K users competing for 100 items | Redis Lua atomic decrement as gate |
| Design Rate Limiter | Every request increments same user's counter | Redis INCR + Lua (sliding window ZSET) |
| Design Like / Reaction System | 100K+ simultaneous likes on viral content | Sharded Redis counters |
| Design Leaderboard (score update) | Many players updating ranks simultaneously | Redis ZADD (atomic sorted set ops) |
| Design Inventory Management | Prevent oversell across warehouse + online | Optimistic locking (low contention) or SELECT FOR UPDATE |
| Design Wallet / Balance | Prevent negative balance under concurrent debits | SELECT FOR UPDATE (correctness critical) |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **Optimistic vs pessimistic locking internals** → `../../Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md`
- **Distributed locking (Redis SETNX, Redlock)** → `../../Foundations/Concurrency-and-Consistency/06-distributed-locking.md`
- **Sharded counters** (full deep-dive) → `../../Foundations/Performance-and-Scale/09-sharded-counters.md`
- **Isolation levels and dirty reads** → `../../Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md`
- **Idempotency** (safe retries) → `../../Foundations/Concurrency-and-Consistency/04-idempotency.md`
- **Inventory and booking patterns** → `../../Production-Grade/System-Design-Patterns/42-inventory-management-booking.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Batch 1 of 8 remaining patterns. |
| July 2026 | Added Strategy 0 (Conditional Writes). Added ABA problem to Strategy 1. Added write skew + SERIALIZABLE note to Strategy 2. Updated decision sequence with Step 0. |
