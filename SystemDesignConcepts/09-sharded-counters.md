# Sharded Counters

---

## 🎯 Why This Matters

Counting at scale sounds trivial until a single database row becomes the bottleneck for millions of concurrent writes. Every `UPDATE views = views + 1` on a single row acquires a row-level lock — under high load, these queue up and your DB melts. Sharded counters are the fix. This appears in interviews for any system that tracks popularity, inventory, votes, or usage metrics — YouTube, Instagram, e-commerce, rate limiting. A senior engineer is expected to know not just "split the counter" but the exact table design, write routing, read aggregation cost, and when to use Redis INCR instead.

---

## 🧠 The Mental Model

Imagine a supermarket on Black Friday with **one cash register**. Every shopper must queue at that single register. As the store fills up, the queue grows so long that shoppers are waiting 45 minutes — even though the cashier is working at full speed. The register is the bottleneck, not the store.

The manager opens **10 registers**. A guard at the door sends each shopper to a register based on their cart (hash on cart ID or random). Each register now handles 1/10th of the traffic. Shoppers check out in 4 minutes. At the end of the day, the manager adds up all 10 registers' totals to get the day's revenue.

In distributed systems: the "single register" is a single DB row (`UPDATE counter SET value = value + 1 WHERE id = X`). Under lock contention, writers queue up waiting for the row lock. The fix is 10 "shard rows" for the same logical counter — each writer goes to a different shard row, and reads `SUM` all shard rows to get the total.

**The catch:** the manager's end-of-day total is not real-time. While you're processing, the total is spread across 10 registers. Similarly, reading a sharded counter requires aggregation — it's slightly stale compared to the last write. This is **eventual consistency** on reads.

**The key insight is:** Sharded counters trade read simplicity (one row → immediate total) for write scalability (N rows → 1/N lock contention per write). Reads become a SUM query across N shards; writes become contention-free as long as N is large enough relative to write rate.

---

## 🎨 Visual — Single counter vs sharded counter under load

```
SINGLE COUNTER — bottleneck under high write load
══════════════════════════════════════════════════

10,000 concurrent writes/sec
         │
         │ all writers try to UPDATE the same row
         ▼
┌─────────────────────┐
│  counter            │
│  id=video:123       │◄─── write queue builds up (lock contention)
│  views = 5,000,000  │     writers waiting: Thread1, Thread2, ..., ThreadN
└─────────────────────┘
         DB CPU: 100%, P99 latency: 2000ms ❌


SHARDED COUNTER — write load distributed across N shards
══════════════════════════════════════════════════════════

10,000 concurrent writes/sec
         │
         │ hash(request_id) % 10 → pick shard
         ▼
┌──────────┐ ┌──────────┐ ┌──────────┐  ...  ┌──────────┐
│ shard 0  │ │ shard 1  │ │ shard 2  │        │ shard 9  │
│ views=   │ │ views=   │ │ views=   │        │ views=   │
│ 512,000  │ │ 498,000  │ │ 503,000  │        │ 487,000  │
└──────────┘ └──────────┘ └──────────┘        └──────────┘
 ~1,000/sec   ~1,000/sec   ~1,000/sec          ~1,000/sec
                                               (10× less contention per shard) ✅

READ total:
  SELECT SUM(views) FROM counter_shards WHERE counter_id = 'video:123'
  → 512,000 + 498,000 + 503,000 + ... + 487,000 = 5,000,000


KEY INVARIANT:
   Write contention is reduced by factor N (number of shards).
   Reads aggregate across all N shards — slightly more expensive but still O(N).
   N is chosen so (peak write rate / N) < (DB row lock throughput per shard).
```

---

## ⚙️ How It Actually Works

### Strategy 1 — Database Sharded Counter

**Steps in plain English:**

1. **Design the shard table** — one row per (counter_id, shard_id) pair. N shards per logical counter.
2. **Route writes** — pick a shard deterministically or randomly; update only that shard row.
3. **Read the total** — aggregate with `SUM` across all shards for that counter_id.
4. **Initialize shards** — pre-create all N shard rows on first use; avoids insert-vs-update race.

```sql
-- Shard table schema
CREATE TABLE counter_shards (
    counter_id   VARCHAR(255)  NOT NULL,
    shard_id     INT           NOT NULL,
    shard_count  BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (counter_id, shard_id)
);

-- Pre-create 10 shards for a new counter
INSERT INTO counter_shards (counter_id, shard_id, shard_count)
VALUES
  ('video:123', 0, 0),
  ('video:123', 1, 0),
  ('video:123', 2, 0),
  -- ... up to shard 9
  ('video:123', 9, 0);

-- Write: pick a random shard (or hash-based) and increment
-- Java picks shard = ThreadLocalRandom.current().nextInt(10)
UPDATE counter_shards
SET shard_count = shard_count + 1
WHERE counter_id = 'video:123' AND shard_id = ?;  -- ? = random 0-9

-- Read total: aggregate all shards
SELECT SUM(shard_count) AS total_views
FROM counter_shards
WHERE counter_id = 'video:123';
```

```java
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ShardedCounterService {

    private static final int SHARD_COUNT = 10;

    private final CounterShardRepository repo;
    private final JdbcTemplate jdbc;

    public ShardedCounterService(CounterShardRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    // Step 2 — write: pick random shard, increment only that row
    public void increment(String counterId) {
        // Random shard avoids thundering herd on a single shard
        int shardId = ThreadLocalRandom.current().nextInt(SHARD_COUNT);
        jdbc.update(
            "UPDATE counter_shards SET shard_count = shard_count + 1 " +
            "WHERE counter_id = ? AND shard_id = ?",
            counterId,
            shardId
        );
    }

    // Step 3 — read: SUM across all shards
    public long getCount(String counterId) {
        Long total = jdbc.queryForObject(
            "SELECT SUM(shard_count) FROM counter_shards WHERE counter_id = ?",
            Long.class,
            counterId
        );
        return total != null ? total : 0L;
    }
}
```

---

### Strategy 2 — Redis INCR Counter

**Steps in plain English:**

1. **Use Redis INCR** — atomic increment on a single key, no lock contention (Redis is single-threaded per command).
2. **Shard across Redis keys** if needed — `INCR video:123:shard:0` through `video:123:shard:9`.
3. **Sync to DB** periodically — a background job reads all shard keys, writes their totals to the DB, then resets Redis counts.

```java
import redis.clients.jedis.Jedis;

@Service
public class RedisShardedCounter {

    private final Jedis jedis;
    private static final int SHARD_COUNT = 10;

    public RedisShardedCounter(Jedis jedis) {
        this.jedis = jedis;
    }

    // Step 1+2 — INCR on a hashed shard key
    public void increment(String counterId) {
        int shardId = ThreadLocalRandom.current().nextInt(SHARD_COUNT);
        String key = counterId + ":shard:" + shardId;
        // INCR is atomic — no lock needed, Redis handles concurrency internally
        jedis.incr(key);
    }

    // Step 3 — read total across all Redis shards
    public long getCount(String counterId) {
        long total = 0;
        for (int i = 0; i < SHARD_COUNT; i++) {
            String key = counterId + ":shard:" + i;
            String value = jedis.get(key);
            if (value != null) {
                total += Long.parseLong(value);
            }
        }
        return total;
    }

    // Step 3 — background job: flush Redis counts to DB and reset
    @Scheduled(fixedDelay = 5000)
    public void flushToDB() {
        // For each known counter, read Redis shards, write to DB, reset Redis
        // Implementation reads counter IDs from a registry, then calls increment+flush
    }
}
```

---

### What is Redis INCR, and why does it fit here?

**Redis INCR** is an atomic increment command — it reads the integer value stored at a key, increments it by 1, and writes back in a single operation. Because Redis is single-threaded per command execution, two concurrent `INCR` calls on the same key serialize automatically — no lost updates, no lock overhead.

**Why it fits:** A single Redis key can handle ~100,000 INCR/second without any lock contention — compared to a PostgreSQL row lock which saturates at ~5,000-10,000 writes/second under high concurrency. Redis sharded counters push this even further by spreading across N keys.

**In an interview, if asked:** "Redis INCR is atomic because Redis executes each command single-threaded — two concurrent INCRs always produce +2 total, never +1. It's the ideal write primitive for a hot counter because it has no lock table, no row-level locking overhead, and runs entirely in memory at ~100K ops/sec per node."

---

### What is HyperLogLog, and when is it an alternative?

**HyperLogLog** is a probabilistic data structure that estimates the count of **distinct elements** (cardinality) in a set using a fixed ~12 KB of memory regardless of how many unique elements there are. It trades exact accuracy (~2% error) for massive space savings.

**When to use instead of sharded counters:** Sharded counters count **total events** (including duplicates) — views, clicks, transactions. HyperLogLog counts **unique elements** — unique visitors, unique URLs, unique users who liked a post. If you need "how many distinct users watched this video," use HyperLogLog (`PFADD`, `PFCOUNT` in Redis). If you need "how many total views," use a sharded counter.

**In an interview, if asked:** "HyperLogLog estimates distinct-element counts in ~12 KB with ~2% error — Redis supports it natively with PFADD/PFCOUNT. I'd use it for unique visitor counts or unique impressions where approximate is acceptable. For total view counts where I need exact numbers, I'd use a sharded counter instead."

---

## 🏢 Real World — Where Companies Use This

- **YouTube** (video view counts): YouTube's view counter is sharded — the displayed count lags a few seconds behind real-time because it periodically aggregates shards. This is by design — eventual consistency on reads is acceptable for view counts.
- **Instagram / Facebook** (likes and reactions): Like counts on posts are sharded counters aggregated periodically. The occasional "998 likes → 1001 likes" jump you see is the aggregation batch running.
- **Flipkart Big Billion Day** (inventory counter): Each product's remaining inventory is a sharded counter. Writes (purchases) go to a shard; reads (showing "only 3 left") aggregate. Combined with pessimistic locking on the final shard writes.
- **Reddit** (upvote/downvote counts): Vote tallies are sharded — aggregated every N seconds. "Fuzzing" vote counts (deliberately showing ±10%) is Reddit's additional layer to prevent vote manipulation, separate from sharding.
- **Razorpay** (transaction volume metrics): Dashboard showing daily transaction volume uses sharded counters aggregated by a background job — avoids any single counter row being hit by every payment event.
- **Zomato** (restaurant order count): Live "123 orders served" counters on restaurant pages are sharded Redis counters flushed to DB every minute.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Write rate on a single counter exceeds ~5,000/sec (DB) or ~100,000/sec (Redis) | Write rate is low — single counter row is fine under normal load |
| Approximate / eventually consistent count is acceptable (views, likes, ratings) | Exact real-time count is mandatory (financial ledger balance, available seats) |
| The counter is "write-heavy, read-light" — many increments, occasional total reads | Reads are very frequent too — SUM across N shards on every request adds DB load |
| You want to distribute write load horizontally across DB nodes | You need to decrement (returns, cancellations) — sharded counters support it but test shard-level negatives carefully |

**The common mistake:** Setting shard count too low and not testing under peak write load. With N=10 shards at 50,000 writes/sec, each shard still gets 5,000 writes/sec — which is at the DB row-lock limit. N should be sized so per-shard write rate is comfortably below ~2,000/sec for safe headroom.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | N× reduction in write lock contention, horizontal write scalability, avoids DB bottleneck on hot counters |
| **You lose** | Read complexity (SUM across N rows instead of point read), eventual consistency on total (small window between writes and aggregation), more rows in the table (N rows per logical counter instead of 1) |
| **Failure mode** | Shard rows are not pre-initialized — first write to a non-existent shard attempts INSERT while another thread also attempts INSERT for the same shard → unique constraint violation. Fix: pre-create all shard rows at counter creation time, use `INSERT ... ON CONFLICT DO UPDATE` (upsert). |

---

## 🔬 Interview Q&As

### Q: "Why can't you just use a single database counter row for YouTube view counts?"

> At 10,000 concurrent views per second on a popular video, every write does `UPDATE views = views + 1` on the same row. That row requires a row-level lock for each update — they serialize. At ~5,000-10,000 lock acquisitions per second, the DB CPU saturates, write latency spikes, and the entire application slows. It's a single hot row — a classic write bottleneck. Sharding spreads those writes across N rows, each getting 1/N of the contention.

---

### Q: "How many shards should you use? How do you pick N?"

> The rule of thumb: N = ceil(peak_writes_per_second / target_writes_per_shard). For YouTube at 50,000 writes/sec on a popular video, targeting 2,000 writes/sec per shard (safe DB headroom), N = 25. In practice, round up to the nearest power of 2 (32) and add 20% headroom. For Redis-backed counters, N can be much smaller because Redis handles 100K INCR/sec — N=10 is usually sufficient.

---

### Q: "A sharded counter read requires SUM across N rows. Won't that be slow?"

> For N=10-50 shards, `SELECT SUM(shard_count) FROM counter_shards WHERE counter_id = ?` is a single index scan on the composite primary key (counter_id, shard_id) — it reads N rows and aggregates in one round-trip. This is microseconds at N=50. For very high read frequency, cache the aggregated total in Redis with a 1-second TTL — fresh enough for display, zero additional DB load per request.

---

### Q: "What's the difference between a sharded counter and HyperLogLog?"

> A sharded counter counts **total events** — every view increments the count, including repeat views from the same user. HyperLogLog counts **distinct elements** — it tracks unique users who viewed, deduplicated with ~2% error and ~12 KB of memory. Use a sharded counter for "total views" (10M views by 2M unique users = 10M). Use HyperLogLog for "unique viewers" (that same video has 2M unique viewers). In practice, YouTube uses both.

---

### Q (Tier 2): "Your sharded counter is backed by a DB. During a flash sale, 100,000 writes/sec hit the counter for a single product. Even with N=50 shards, at 2,000 writes/sec per shard you're at the limit. What do you do?"

> At this scale, move the hot counter from DB shards to Redis sharded INCR. Redis handles 100,000 INCR/sec on a single key (and far more across shards) entirely in memory. Writes go to Redis, a background job (`@Scheduled` every 5 seconds) aggregates Redis shard totals and flushes to DB, then resets the Redis counters. The DB now receives a few writes per second from the background job instead of 100,000/sec from application threads. Trade-off: up to 5 seconds of potential count loss if Redis crashes before the flush — acceptable for a view counter, not acceptable for a financial balance.

---

### Q (Tier 2): "How do sharded counters interact with rate limiting? Can you use them together?"

> Yes — sharded counters are often the implementation layer beneath a distributed rate limiter. A rate limiter needs to count requests per user per window. A naïve implementation uses a single counter per user per window in Redis — which is fine for most users. But for high-traffic API keys (e.g., a single company making 1M requests/min), the single counter becomes a hot key in Redis. The fix: shard the rate limit counter across N Redis keys — `rl:{userId}:w:{window}:s:{shard}` — and aggregate across shards when checking the limit. The atomicity challenge: you need the aggregate to be consistent enough to enforce the limit. In practice, tolerate ±5% over-limit rather than adding cross-shard distributed locks. See `02-rate-limiting.md` for the sliding window details.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Sharded counters split a single hot-write DB row into N shard rows — each write goes to a random shard (1/N of the contention), and reads do a SUM across all N shards — for extreme write rates (flash sales, viral videos), move shards to Redis INCR which handles 100K ops/sec in memory and flushes to DB on a background schedule."

---

## 🔗 Related Concepts

- **`01-optimistic-pessimistic-locking.md`** — the hot-row bottleneck sharded counters solve is caused by row-level locking; understanding lock contention is the prerequisite
- **`02-rate-limiting.md`** — rate limiters use counters per time window; sharded counters apply when a single user's counter becomes a hot key
- **`03-caching.md`** — aggregate totals from sharded counters are cached in Redis with short TTL to avoid SUM on every read request
- **`06-distributed-locking.md`** — sharded counters avoid the need for distributed locks on counter writes by design — each shard is independently writable

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Sharded Counters"** — Arpit Bhayani (YouTube: "Arpit Bhayani sharded counters") | Deep dive on the hot-write problem mechanics, DB internals of row locking, HyperLogLog comparison | ~30 min |
| **"Counting at Scale"** — ByteByteGo (YouTube: search "ByteByteGo counting at scale") | Visual walkthrough of the bottleneck → shard transition with throughput numbers | ~8 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers: hot-write problem, DB shard table design, Redis INCR sharding, HyperLogLog distinction, sizing formula, background flush pattern. 6 Q&As (4 Tier 1 + 2 Tier 2). |
