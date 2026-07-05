# Dealing with Contention — Quick Reference

> **Read this:** 30 min before an interview involving flash sales, booking systems, counters, or rate limiters.
> **Deep study:** `DeepDive/04-dealing-with-contention.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **many concurrent users compete for the same shared resource** — the problem is coordination, not throughput. Adding more app servers makes things worse, not better.

Trigger words: "flash sale", "seat booking", "like counter", "rate limiter", "leaderboard update", "inventory oversell", "two users hit buy simultaneously", "prevent double-booking".

---

## 🧭 Decision Sequence

```
START: Many concurrent writers targeting the same resource

Step 0 → Can the check fit in a single WHERE predicate?
         UPDATE ... SET x = x - 1 WHERE x > 0 AND id = 42?
               → Conditional write. No transaction needed. Try this first.
         Needs multi-step logic (check + charge + reserve)?
               → Needs a transaction. Go to Step 1.

Step 1 → How often do conflicts actually occur?
         Rarely (< 10%)? → Optimistic locking. Simple, no blocking.
         Often (> 50%)?  → Never use optimistic locking. Go to Step 2.

Step 2 → What's the write rate to this resource?
         < 100 writes/sec per resource?
               → Pessimistic locking (SELECT FOR UPDATE). Simple, safe.
         > 100 writes/sec?
               → Pessimistic locking will queue and timeout. Go to Step 3.

Step 3 → Can the operation be expressed atomically?
         Simple counter (increment, decrement, check-and-decrement)?
               → Redis atomic ops (INCR, DECRBY, Lua script).
         Complex business logic (check, allocate, record)?
               → Queue-based serialization (Kafka FIFO per resource key).

Step 4 → Is one Redis key still a bottleneck?
         > 1M writes/sec to same key?
               → Sharded counters (split across N Redis keys, sum on read).

Note: Steps 3 and 4 are alternatives, not sequential.
Choose based on operation complexity, not just write rate.
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Conditional write (UPDATE ... WHERE)** | Simple check fits a WHERE clause, single statement | Multi-step business logic needing a transaction |
| **Optimistic locking (version column)** | Conflicts rare (< 10%), long think-time ops | Flash sales, popular counters — infinite retries |
| **Pessimistic locking (SELECT FOR UPDATE)** | Business-critical, < 100 writes/sec, short lock time | Long operations, thousands of concurrent writers |
| **Redis atomic ops (INCR / Lua)** | High-freq counters, simple check-and-decrement | Complex multi-step business logic, need ACID |
| **Sharded counters** | Very high-write counters (likes, views, global totals) | Exact real-time accuracy required (inventory) |
| **Queue-based serialization** | Complex logic + FIFO fairness required | High throughput needed; throughput = consumer speed |

**Key numbers to remember:**
- SELECT FOR UPDATE throughput: ~20 writes/sec per resource (lock cycle ≈ 50ms)
- Redis atomic throughput: ~100K–1M ops/sec per key
- Sharded counters: N shards = N× throughput, read = MGET all N shards and sum
- Optimistic locking retry rate under high contention: O(N²) — avoid at flash-sale scale

---

## 🎨 Key Architecture Diagram

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

## ⚠️ Anti-patterns (don't say these)

- **Optimistic locking for high-contention resources** — 1000 users competing → O(N²) retry storm; use pessimistic or Redis pre-filter
- **Holding a pessimistic lock across a network call** — `BEGIN → SELECT FOR UPDATE → [external API: 2s] → COMMIT` = connection pool exhausted
- **Not making retries idempotent** — optimistic lock encourages retries; retries without idempotency keys = double-charge, double-decrement

---

## 🧩 Common Interview Problems

| Problem | Strategy | Key decision |
|---|---|---|
| Design BookMyShow / Ticketmaster | SELECT FOR UPDATE at checkout + Redis pre-filter | Two-phase: Redis soft check → DB hard lock |
| Design Flash Sale | Redis Lua atomic decrement as gate | 50K users → Redis rejects 49,900, DB sees 100 |
| Design Rate Limiter | Redis INCR + Lua (sliding window ZSET) | Fixed window: INCR; sliding window: ZSET |
| Design Like / Reaction System | Sharded Redis counters | 64 shards for 500K writes/sec viral post |
| Design Leaderboard (score update) | Redis ZADD (atomic sorted set ops) | O(log N) update + O(log N) rank query |
| Design Wallet / Balance | SELECT FOR UPDATE (correctness critical) | Negative balance prevention = pessimistic |

---

## 🔗 Full notes

`DeepDive/04-dealing-with-contention.md` — strategy deep dives, Redis Lua scripts, deadlock prevention, full failure mode Q&A
