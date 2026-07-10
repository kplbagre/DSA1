# 54 — Redis Internals: Atomicity, Persistence & the Right Command

---

## 📖 What is Redis?

**Full form:** Redis = Remote Dictionary Server

**Simple analogy:** Redis is like a whiteboard in the office — anyone can read or write in milliseconds (everything is in RAM), it gets wiped on a power cut (volatile by default), but you can photograph the board every 60 seconds (RDB snapshot — a periodic point-in-time copy) or log every single marker stroke so you can replay them from the beginning (AOF journal — a running append-only log of every write). The critical detail most people miss: only **one hand** can write on the whiteboard at a time — and that serialization is what makes every Redis command atomic.

**Core principle:** An in-memory key-value store with rich built-in data structures (strings, hashes, lists, sets, sorted sets) and a **single-threaded event loop** that processes one command at a time — giving every command atomicity for free, without application-level locks.

**Why it matters in system design:** Sub-millisecond reads + guaranteed atomic operations make Redis the right tool for distributed coordination (locks, counters, rate limiters, leaderboards) that would require complex locking at the DB layer otherwise. It appears in 8+ prepared problems in this knowledge base.

---

## 🎯 Why This Matters

Redis appears in every interview domain: flash sale inventory, auth token management, distributed locking, rate limiting, WebSocket fan-out, job scheduling. The question is never "should I use Redis?" — it's "which Redis command, and does the interviewer buy that it's safe?" You need the single-threaded argument cold, the five atomic weapons identified, and the persistence trade-off explained when probed.

---

## 🧠 The Mental Model

**The gas station with one pump attendant.**

Picture a busy 24-hour gas station with a single attendant who pumps gas for every car. There is physically only one person: Car 1 pulls up, the attendant checks the tank level, pumps 10 litres, and moves to Car 2. While the attendant is serving Car 1, Car 2 can arrive and join the queue — but it cannot be served simultaneously.

This means the attendant can safely:
- Read the remaining fuel level
- Subtract what's needed
- Record the transaction in the logbook

...all in sequence, with zero risk that two cars each see "20 litres remaining" and both pump 15.

**What breaks in the multi-attendant version:** Hire 10 attendants (10 threads). Two of them check the tank at the same millisecond, both read "5 litres remaining", both approve a 5-litre order, and you pump 10 litres from an empty tank — oversold.

Redis is the gas station with one attendant. Your 10 service pods are cars. The fuel gauge is the inventory counter.

The attendant also keeps a **logbook** — after every transaction, they write the entry down before moving to the next car: "Car 7, 10 litres, tank now at 40." If the station burns down tonight, a new attendant tomorrow can open the logbook, replay every entry, and know exactly how much fuel is left. That logbook is **AOF persistence** — Redis appends every write command to a file so it can replay them on restart.

At the end of each shift, a manager also takes a **photograph of the fuel gauges** — a snapshot of all current levels at that exact moment. Not as detailed as the logbook (it doesn't show individual transactions), but fast to read when you open the next morning. That photograph is **RDB persistence** — a point-in-time snapshot of the whole dataset, taken periodically (default: every 60 seconds).

**The key insight is:** Redis atomicity does not come from locks — it comes from eliminating concurrency entirely at the command-execution layer.

---

## 🎨 Visual — System Topology + Single-Threaded Event Loop

```
FULL SYSTEM TOPOLOGY — where Redis sits in the stack

                    Internet
                       │
                  ┌────▼─────┐
                  │    LB    │
                  └────┬─────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   ┌─────────┐   ┌─────────┐   ┌─────────┐
   │  Pod 1  │   │  Pod 2  │   │  Pod 3  │
   └────┬────┘   └────┬────┘   └────┬────┘
        └──────────┬──┘─────────────┘
                   │   (all pods write
                   │    to ONE Redis)
              ┌────▼──────┐
              │   Redis   │  ← coordination layer
              │(in-memory)│    sub-ms reads/writes
              └────┬──────┘
                   │  (async background sync)
              ┌────▼──────┐
              │    DB     │  ← source of truth
              │(Postgres) │    durable writes
              └───────────┘

Redis sits BETWEEN service pods and the DB.
Pods use Redis for fast coordination; DB holds canonical state.


COMPONENT DETAIL — 10 concurrent DECRs serializing

Pod 1 ─────────────────────────────┐
Pod 2 ──────────────────────────┐  │
Pod 3 ───────────────────────┐  │  │   ... 10 pods simultaneously
Pod 4 ────────────────────┐  │  │  │       DECR inventory:seatA
Pod 5 ─────────────────┐  │  │  │  │
...                     ▼  ▼  ▼  ▼  ▼
                  ┌────────────────────┐
                  │   Network Buffer   │
                  │  [cmd1][cmd2][cmd3]│
                  │  [cmd4]...[cmd10] │  ← queued, not dropped
                  └────────┬───────────┘
                           │
                  ┌────────▼────────────┐
                  │  Single-Threaded    │
                  │  Event Loop         │
                  │                     │
                  │  cmd1: DECR → 9     │  ← Pod 1 gets 9
                  │  cmd2: DECR → 8     │  ← Pod 2 gets 8
                  │  cmd3: DECR → 7     │  ← Pod 3 gets 7
                  │  ...                │
                  │  cmd10: DECR → 0    │  ← Pod 10 gets 0
                  └─────────────────────┘

KEY INVARIANT:
   No two DECR commands execute simultaneously.
   Each sees the result of all previous commands.
   A pod receiving a NEGATIVE value (e.g., −1) is the one that failed —
   it compensates with INCR and returns an out-of-stock error.
   A pod receiving 0 successfully claimed the LAST unit.
   No overselling is possible.
```

---

## ⚙️ How It Actually Works

### The Five Atomic Weapons

**When to reach for which:**

| Weapon | Command | Reach for it when... |
|---|---|---|
| **Decrement** | `DECR` / `DECRBY` | You need atomic inventory reservation — subtract and detect if you went negative |
| **Lock** | `SET key val EX seconds NX` | You need mutual exclusion across pods — only one pod proceeds, TTL prevents deadlock |
| **Script** | `EVAL lua_script` | You need multi-step atomic logic with conditional branching — can't be done with MULTI/EXEC |
| **Expiry** | `EXPIRE` / `SET EX` | You need self-cleaning ephemeral state — tokens, rate limit windows, idempotency keys |
| **Sorted Set** | `ZADD` / `ZRANGEBYSCORE` | You need ordered membership with scores — sliding window counters, delayed queues, leaderboards |

---

#### Weapon 1 — DECR: Atomic Inventory Reservation

**Steps in plain English:**

1. **Decrement first** — call `DECR inventory:<itemId>`. Redis subtracts 1 atomically and returns the new value.
2. **Inspect the return value** — if it is `≥ 0`, the reservation succeeded. If it is `< 0`, the inventory is exhausted; increment back to avoid permanent under-count.
3. **Write to DB after Redis success** — Redis is the gate; DB is the ledger.

```java
// Step 1 — decrement atomically; Redis returns the post-decrement value
long remaining = jedis.decr("inventory:" + itemId);

// Step 2 — check for exhaustion
if (remaining < 0) {
    // Step 2a — compensate so the counter doesn't drift negative forever
    jedis.incr("inventory:" + itemId);
    throw new OutOfStockException("Item " + itemId + " is sold out");
}

// Step 3 — reservation held in Redis; persist to DB asynchronously
orderRepository.save(new Order(userId, itemId));
```

---

#### Weapon 2 — SET NX EX: Distributed Lock + Idempotency Token

**Steps in plain English:**

1. **Attempt lock acquisition** — `SET key value EX ttl NX` sets the key only if it does not exist, with a TTL. Returns `"OK"` on success, `null` if already held.
2. **Do work inside the lock** — only the pod that got `"OK"` proceeds. Others fail fast or retry.
3. **Release explicitly** — `DEL key` when work is done. TTL is the safety net if the pod crashes.

```java
// Step 1 — try to acquire the lock (NX = only if not exists, EX = TTL in seconds)
String acquired = jedis.set(
    "lock:" + resourceId,
    workerId,
    SetParams.setParams().nx().ex(30)
);

// Step 2 — only proceed if this pod won the lock
if (!"OK".equals(acquired)) {
    throw new LockNotAcquiredException("Resource " + resourceId + " is locked");
}

try {
    // do the critical work here — only one pod reaches this line
    performCriticalWork(resourceId);
} finally {
    // Step 3 — release the lock
    jedis.del("lock:" + resourceId);
}
```

> **Gotcha:** `DEL` in the finally block is not atomic with the ownership check — a crashed-pod scenario can cause another pod's lock to be deleted. For production, use a Lua script that checks `GET == workerId` before `DEL`. See `../Concurrency-and-Consistency/06-distributed-locking.md` for the full Redlock pattern.

---

#### Weapon 3 — Lua Script: Multi-Step Atomic Logic

**Steps in plain English:**

1. **Write the script server-side** — the entire script runs as ONE atomic Redis command. No other command can interleave between lines of the script.
2. **KEYS and ARGV** — pass the key names as KEYS array and values as ARGV array (Redis Cluster requires this for slot routing).
3. **Return a result** — the script's return value is received by the caller as a normal command response.

```java
// Lua script: check-and-claim a seat atomically (used in booking systems)
// If current value == "available", set to "claimed"; else return 0
String luaScript =
    "local current = redis.call('GET', KEYS[1]) " +
    "if current == ARGV[1] then " +
    "  redis.call('SET', KEYS[1], ARGV[2]) " +
    "  return 1 " +
    "else " +
    "  return 0 " +
    "end";

// Step 1 — execute the script (runs atomically; no interleaving possible)
Long result = (Long) jedis.eval(
    luaScript,
    1,
    "seat:" + seatId,
    "available",
    "claimed"
);

// Step 2 — interpret the result
if (result == 0L) {
    throw new SeatAlreadyClaimedException("Seat " + seatId + " is taken");
}
```

#### What is a Lua Script, and why does it fit here?

A Lua script (a lightweight scripting language embedded in Redis that runs server-side, like a stored procedure in a database) executes as a single Redis command — nothing can run between its lines. Use it when you need conditional logic (`if current == X, then do Y`) that cannot be expressed as a pipeline or MULTI/EXEC block (which has no branching).

**In an interview, if asked:** "I use a Lua script when I need to read a value, make a decision based on it, and write back — all atomically. MULTI/EXEC can't branch on intermediate values; Lua can. The script runs server-side so there's also no extra network round-trip."

---

#### Weapon 4 — EXPIRE / SET EX: Time-Bounded Ephemeral State

Every Redis key that should not live forever must have a TTL (time-to-live — the number of seconds before Redis automatically deletes the key, cleaning itself up without any cron job). This covers:
- **Auth tokens:** `SET refresh_token:<userId> <tokenValue> EX 86400` — auto-expires in 24 hours
- **Idempotency keys:** `SET idempotency:<key> <response> EX 86400 NX` — stores result for 24 hours, returns stored result on retry
- **Token blacklist on logout:** `SET token:<jti> "revoked" EX <remaining_ttl>` — lives until original token would have expired, then auto-deletes
- **Rate limiter windows:** `EXPIRE rate_limit:<userId>:<window> <window_seconds>` — window auto-resets

```java
// Idempotency: store the response for 24 hours; return early on duplicate requests
String key = "idempotency:" + idempotencyKey;
String existing = jedis.get(key);
if (existing != null) {
    // duplicate request — return the stored result without re-processing
    return deserialize(existing);
}
// new request — process and store result
String result = processPayment(request);
jedis.set(key, serialize(result), SetParams.setParams().ex(86400).nx());
return result;
```

---

#### Weapon 5 — ZADD / ZRANGEBYSCORE: Sorted Set for Sliding Window and Delayed Queues

A Redis Sorted Set (also called a ZSET — a data structure where each member has a floating-point score; members are always kept sorted by score) supports two patterns:

**Pattern A — Sliding window rate limiting** (score = request timestamp):

```java
// Sliding window: allow max 100 requests per 60 seconds
long now = System.currentTimeMillis();
long windowStart = now - 60_000;
String key = "rate_limit:" + userId;

// Step 1 — remove timestamps older than 60 seconds
jedis.zremrangeByScore(key, 0, windowStart);

// Step 2 — count how many requests are still in the window
long count = jedis.zcard(key);
if (count >= 100) {
    throw new RateLimitExceededException("Rate limit exceeded for user " + userId);
}

// Step 3 — record this request
jedis.zadd(key, now, UUID.randomUUID().toString());
jedis.expire(key, 60);
```

**Pattern B — Delayed queue** (score = due epoch timestamp):

```java
// Enqueue: schedule a job to run at dueTime (Unix epoch milliseconds)
jedis.zadd("delayed_jobs", dueTime, jobId);

// Poll: fetch all jobs whose due time has passed
long now = System.currentTimeMillis();
Set<String> dueJobs = jedis.zrangeByScore("delayed_jobs", 0, now);
for (String jobId : dueJobs) {
    // claim atomically before processing (prevent double-execution)
    long removed = jedis.zrem("delayed_jobs", jobId);
    if (removed == 1L) {
        process(jobId);
    }
}
```

#### What is a Redis Sorted Set (ZSET), and why does it fit here?

A Sorted Set (ZSET) stores unique members each paired with a numeric score, always sorted by that score. `ZADD` is O(log N); range queries by score (`ZRANGEBYSCORE`) are O(log N + M) where M is results returned. Score = timestamp enables time-based range queries; score = priority enables priority queues; score = distance enables proximity ranking.

**In an interview, if asked:** "I use Redis Sorted Set when I need sorted membership — the score is whatever I want to sort by. For rate limiting, score = request timestamp; I query all members in the last 60 seconds to count them. For delayed queues, score = due time; I poll for members with score ≤ now."

Full command reference: `../../Patterns/tools-glossary.md` (Redis Sorted Set entry)

---

### RDB vs AOF Persistence

When Redis restarts or crashes, in-memory state is lost. Persistence modes control how much you can recover.

#### What is RDB (Redis Database Snapshot), and why does it fit here?

RDB (a point-in-time snapshot of the entire dataset written to disk) works like the shift manager's photograph of the fuel gauges — taken periodically (every 60 seconds by default), compact, fast to read on startup. Redis forks a child process, serializes all keys to a `.rdb` file, and the parent continues serving commands without pausing.

**In an interview, if asked:** "RDB gives me fast restarts and compact backups — the `.rdb` file is much smaller than an AOF log. The trade-off: I can lose up to 60 seconds of writes if Redis crashes between snapshots. Fine for caches; not fine for inventory counters or lock state."

#### What is AOF (Append-Only File), and why does it fit here?

AOF (a write-ahead log that records every write command before acknowledging it) works like the attendant's logbook — every transaction written down before moving to the next car. On restart, Redis replays every command in the logbook from top to bottom to reconstruct state exactly as it was.

**In an interview, if asked:** "AOF gives me durability — I can lose at most 1 second of data with `appendfsync everysec`. The trade-off: larger file, slower restart, slightly higher write latency. For anything I can't afford to lose — inventory decrements, distributed lock state, idempotency keys — I enable AOF."

| | RDB | AOF |
|---|---|---|
| **Data loss on crash** | Up to last snapshot (default: 60s) | Up to 1 second (`everysec` mode) |
| **Restart speed** | Fast — load binary snapshot | Slow — replay every command |
| **File size** | Small — compressed binary | Large — grows with every write |
| **Best for** | Cache data (loss acceptable) | Counters, locks, idempotency keys |
| **`fsync` option** | N/A | `always` (0 loss) / `everysec` (default) / `no` (OS decides) |

**The production recommendation:** Enable both — RDB for fast restarts, AOF for durability. Redis replays the AOF log on restart (more complete than RDB alone).

---

### Eviction Policies — What Happens When Memory Is Full

When Redis reaches `maxmemory`, it must evict keys or return an error. The eviction policy (the rule Redis uses to pick which key to delete) determines behavior.

| Policy | What it evicts | Use for |
|---|---|---|
| `noeviction` | Returns error — nothing evicted | Never appropriate for high-write systems |
| `allkeys-lru` | Any key, Least Recently Used first | Pure cache — evict coldest items |
| `allkeys-lfu` | Any key, Least Frequently Used first | Skewed access — protects hot keys |
| `volatile-lru` | Only keys with TTL set, LRU first | Mixed: cache keys have TTL; lock/counter keys don't |
| `volatile-ttl` | Only keys with TTL, shortest TTL first | Prioritize keeping longer-lived state |

**Interview answer:** "For a pure Redis cache I use `allkeys-lru` — nothing is sacred, evict coldest. For a Redis instance storing both cache and coordination state (locks, counters) I use `volatile-lru` — cache keys have TTL so they're evictable; lock and counter keys have no TTL so they're protected from eviction."

---

## 🏢 Real World — Where Companies Use This

- **Amazon (flash sale inventory):** `DECR inventory:<itemId>` is the gate for every "Buy Now" click during Prime Day. Redis absorbs millions of concurrent decrements; the negative-value check prevents overselling before the DB write is even attempted.
- **Uber (driver dispatch):** Available drivers stored in a Redis Sorted Set scored by last-activity timestamp. `ZADD drivers:<geohash> <timestamp> <driverID>` + `ZRANGEBYSCORE` gives an ordered list of recently-active drivers in a geographic cell in under 1ms.
- **Twitter (rate limiting):** Sliding window per user implemented with ZADD — score = request epoch, key = `rate_limit:<userId>:<resource>`. `ZREMRANGEBYSCORE` trims stale entries; `ZCARD` counts current window size.
- **Stripe (idempotency):** `SET idempotency:<key> <response> EX 86400 NX` stores the response for 24 hours. A duplicate payment request returns the stored result without re-charging.
- **DocuSign (JWT blacklist on logout):** `SET token:<jti> "revoked" EX <remaining_ttl>` invalidates a session token immediately on logout — before the JWT would naturally expire. Auto-deletes when the original TTL would have elapsed.
- **GitHub (merge serialization):** `SET lock:repo:<id> <workerID> EX 30 NX` prevents two merge jobs from operating on the same repository branch concurrently.

**Your prepared problem → Redis weapon mapping:**

| Prepared Problem | Redis Weapon | Why |
|---|---|---|
| Inventory / booking (42) | `DECR` | Atomic decrement; detect oversell at Redis layer |
| Seat claim (42) | Lua script | Atomic check-and-swap: `available → claimed` |
| Auth — refresh token | `SET EX` | Token stored with TTL; auto-expires |
| Auth — token blacklist | `SET EX` | Revocation lives until original JWT expiry |
| Distributed lock (47) | `SET NX EX` | Mutual exclusion; TTL = auto-release on crash |
| Rate limiter (02) | `ZADD` + `ZRANGEBYSCORE` | Sliding window; or `INCR` + `EXPIRE` for fixed |
| WebSocket fan-out (26) | `PUBLISH` / `SUBSCRIBE` | Inter-pod message delivery for same user's sockets — Pub/Sub not a numbered weapon here; see `../../Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md` and `../../Patterns/tools-glossary.md` for full treatment |
| Delayed jobs (47) | `ZADD` (score = due epoch) | Poll with `ZRANGEBYSCORE 0 <now>` |

---

## 🧭 When to Use vs When NOT to Use

| Use Redis when | Do NOT use Redis when |
|---|---|
| Sub-millisecond latency is required | Dataset exceeds available RAM |
| Atomic counter / flag operations across pods | You need complex joins or ad-hoc queries |
| Distributed coordination (locks, rate limits) | Loss of Redis data = unrecoverable business error (without AOF) |
| Ephemeral state with TTL (sessions, tokens) | You're treating Redis as the only database (no DB behind it) |
| Sorted / ranked access patterns (feeds, leaderboards) | Write throughput is so high that AOF creates I/O bottleneck |

**The common mistake:** Using Redis without configuring persistence and then treating Redis-only state as permanent. Redis + no AOF + no backup DB = a crash loses all counters, locks, and idempotency keys.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Sub-millisecond latency, atomic operations for free (single-threaded), rich data structures (no application-level coordination code), TTL-based automatic cleanup |
| **You lose** | Data size bounded by RAM, single-threaded means no parallelism per shard (mitigated by Redis Cluster sharding), eventual persistence (AOF everysec = 1s loss window), no ACID transactions across multiple keys |
| **Failure mode** | Redis treated as the source of truth with no persistence configured — crash loses all state; or eviction policy misconfigured so coordination keys (locks, counters) get evicted under memory pressure, corrupting distributed state |

---

## 🔬 Interview Q&As

### Tier 1 — Surface

### Q: "Why is `DECR` safe when called from 10 pods simultaneously?"
> Redis processes one command at a time via a single-threaded event loop. All 10 `DECR` calls are queued and executed sequentially — each one sees the result of the previous. There is no way for two pods to read the same value and both decrement from it concurrently. The atomicity is structural, not lock-based.

### Q: "When do you use a Lua script instead of `MULTI/EXEC`?"
> `MULTI/EXEC` executes a batch of commands atomically but cannot branch on intermediate values — you can't read a value inside a MULTI block and decide whether to proceed. Lua scripts run server-side and support full conditional logic. Use Lua when the atomic operation requires a read-then-conditionally-write pattern, like check-and-claim for seat booking.

### Q: "What eviction policy do you set for Redis used as a rate limiter?"
> Rate limiter keys should have a TTL (the window duration). I use `volatile-lru` if the same Redis instance also holds coordination state (locks, counters without TTL). `volatile-lru` evicts only TTL-bearing keys, so lock and counter keys without TTL are never evicted. If Redis is exclusively a cache, `allkeys-lru` is simpler.

### Q: "How does AOF `appendfsync everysec` work?"
> Redis buffers write commands in an OS page buffer. Once per second, it calls `fsync()` to flush the buffer to disk. On crash, you lose at most 1 second of commands. It's a background fsync — write latency is almost the same as no persistence. `appendfsync always` fsyncs after every write (zero data loss, high I/O cost); `appendfsync no` lets the OS flush when it wants (unpredictable loss window).

---

### Tier 2 — Cross / Probe

### Q: "Your Redis node crashes between the `DECR` and the Postgres write. What breaks?"
> The `DECR` was applied in Redis (in-memory), returned success to the pod, and then Redis crashed. If no AOF: on Redis restart, the counter resets to its last snapshot value — the decrement is lost, so the inventory count is too high by 1, creating a phantom available slot. The mitigation: enable AOF with `everysec` so the DECR is durably logged before acknowledgment. Also: the pod should write to DB in the same request after the DECR — if that DB write fails, the pod should `INCR` to compensate before returning the error.

### Q: "Why not use a Postgres `SELECT FOR UPDATE` instead of Redis `SET NX EX` for distributed locking?"
> `SELECT FOR UPDATE` holds a DB row-level lock for the duration of the transaction — it works but ties a DB connection (an expensive resource) to the lock duration. If the lock holder hangs for 10 seconds, that DB connection is held for 10 seconds, and with 100 lock contenders you exhaust the connection pool. Redis `SET NX EX` is a 1ms network call that holds no DB resources — the lock is a key in RAM. It also TTLs itself on holder crash, which a DB transaction lock does not.

### Q: "Your rate limiter key for a user gets evicted under memory pressure before its TTL expires. What happens?"
> The key is gone — the next request for that user sees no key and treats it as a fresh window. The user effectively gets a free reset of their rate limit. Mitigation: use `volatile-lru` eviction so rate limiter keys (which have TTL) are eviction candidates, but size `maxmemory` so the Redis instance is never so full that eviction of active rate limit keys becomes likely. An alternative: move the rate limiter to a dedicated Redis instance with `noeviction` and alarm on memory usage before it fills.

### Q: "Your system has 5 Redis Cluster nodes. Does `DECR inventory:<itemId>` still work the same way?"
> Yes — the key `inventory:<itemId>` hashes to exactly one slot, and that slot is owned by exactly one primary shard. All `DECR` calls for that key land on the same single-threaded event loop on that shard. Redis Cluster doesn't break per-key atomicity. What it prevents: multi-key operations (like a Lua script that reads from two keys on different shards) — those fail unless you use hash tags to force co-location (`{itemId}:inventory` and `{itemId}:lock` on the same shard).

---

## 🧾 TL;DR

> "Redis is safe for distributed coordination not because it uses locks but because its single-threaded event loop serializes all commands — choose your weapon (DECR for inventory, SET NX EX for locks, Lua for conditional atomics, ZADD for sliding windows), enable AOF if you can't afford to lose that state on a crash, and size `maxmemory` with `volatile-lru` so coordination keys without TTL are never evicted."

---

## 🔗 Related Concepts

- **`03-caching.md`** — cache strategies (write-through, write-behind, cache-aside); Redis is the most common cache implementation
- **`../Concurrency-and-Consistency/06-distributed-locking.md`** — Redlock (multi-node Redis locking), full SET NX fencing token pattern
- **`02-rate-limiting.md`** — token bucket and sliding window implementations; the ZADD sliding window code comes from here
- **`09-sharded-counters.md`** — when a single Redis INCR/DECR becomes the hot-key bottleneck and you need sharded counters
- **`../../Patterns/tools-glossary.md`** — command-level definitions for all Redis commands (ZSET, Pub/Sub, SETNX, Lua, INCR, TTL, GEOADD)
- **`../../Production-Grade/System-Design-Patterns/42-inventory-management-booking.md`** — the crash-recovery Q&A (AOF gap) that prompted this file

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — "Redis Internals" (YouTube — Asli Engineering)** | Goes deeper on the event loop, I/O multiplexing, and why single-threading beats multi-threading for Redis's workload | ~20 min |
| **Redis.io — Persistence documentation** (redis.io/docs/management/persistence) | Official RDB/AOF configuration guide — all `appendfsync` options, hybrid persistence, and AOF rewrite | ~10 min read |
| **Redis.io — Transactions (MULTI/EXEC vs Lua)** (redis.io/docs/manual/transactions) | Official comparison of MULTI/EXEC and Lua — when each is appropriate, WATCH optimistic locking | ~8 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | File created. Covers: single-threaded event loop (why atomicity is free), five atomic weapons (DECR, SET NX EX, Lua, EXPIRE, ZADD), RDB vs AOF persistence with fsync modes, eviction policies (allkeys-lru vs volatile-lru), problem → weapon mapping table for 8 prepared problems. Prompted by AOF gap in 42-inventory-management-booking.md Tier 2 Q&A. |
