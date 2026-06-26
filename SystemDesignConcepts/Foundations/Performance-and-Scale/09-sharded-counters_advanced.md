# Sharded Counters — Advanced Patterns

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`
> **Companion to:** `09-sharded-counters.md` — Advanced counter architectures for distributed systems

---

## 🎯 Why This Matters

The core sharded counters note covers the basic pattern: instead of one global counter (bottleneck), split it into N shards and sum them on read. But production systems go further: How do you count events over time (time-series counters for "requests per minute")? What if the system is partitioned and nodes can't reach each other — can they still count correctly (CRDT counters)? What if the number of shards should change dynamically based on write load (adaptive sharding)? How do you estimate cardinality (unique visitors) without storing all visitors (approximate counting)? Senior engineers deploy these patterns when the basic sharding doesn't handle the scale, consistency, or time-series requirements.

---

## 🧠 The Mental Model

Extend the "ticket booth with multiple tellers" analogy from the core note:

You had N tellers, each with a tally (a shard). Problem: if a teller is on vacation, their tally is empty, and you lose accuracy. **CRDT counters** let each teller count independently, and any two tellers can later sync their counts — no centralized coordinator needed. Problem: you want to know "how many visitors in the last minute" not just total. **Time-series counters** break the minute into 10-second buckets; tellers count per bucket. Problem: some minutes are 100x busier than others — you need more tellers in peak hours (adaptive sharding). Problem: you want to count 1 million unique visitors without storing 1 million names. **Approximate counting** estimates "approximately 980,000 unique" without remembering every name.

These are the problems advanced counters solve.

---

## 🎨 Visual — CRDT Counters, Time-Series, Adaptive Sharding

```
CRDT COUNTER (Conflict-Free Replicated Data Type)
════════════════════════════════════════════════════════

Traditional Counter (global):
  Counter = 42
  Three replicas: A, B, C
  A increments to 43. Before sync to B and C, network partition occurs.
  A reaches 50. B and C see 42 (stale).
  When network heals: what is the true count? 50 or something else?

CRDT G-Counter (Grow-Only Counter):
────────────────────────────────────
Each replica has its own shard:
  Replica A shard: [A:50, B:20, C:15] total = 85
  Replica B shard: [A:0, B:20, C:15]  total = 35
  Replica C shard: [A:0, B:0, C:15]    total = 15

No communication → each increments its own shard:
  A does +5 more  → A shard: [A:55, B:20, C:15] (total 90 locally)
  B does +3 more  → B shard: [A:0, B:23, C:15] (total 38 locally)
  C does +7 more  → C shard: [A:0, B:0, C:22] (total 22 locally)

On sync (network heals), they share shards:
  All merge their knowledge:
  Global = [A:55, B:23, C:22] total = 100 ✅

KEY INVARIANT:
   Each replica only increments its own shard.
   The total is the sum of all shards.
   No coordinator needed; any two replicas can sync and reach agreement.


TIME-SERIES COUNTERS (counters bucketed by time)
════════════════════════════════════════════════════

Request rate counter: "how many requests in the last minute?"

Instead of one counter:
  requests:total = 1,000,000

Use a time-bucketed counter (e.g., 10-second buckets):
  requests:minute:0  = 500   (0-10s)
  requests:minute:1  = 550   (10-20s)
  requests:minute:2  = 400   (20-30s)
  ...
  requests:minute:5  = 475   (50-60s)

Total last minute = SUM([500, 550, 400, ...]) = 3,000 ✅

To get "last 5 minutes": SUM(buckets from 5 minutes ago to now) ✅
To get "last hour": different counter with 1-minute buckets

Advantage: Can detect traffic spikes in specific seconds, not just totals.


ADAPTIVE SHARDING
══════════════════════════════════════════════════════════════

Fixed shards (N=10):
  Shard 1: 1000 ops/sec  (hot)
  Shard 2: 100 ops/sec   (cold)
  ...

One shard is a bottleneck. Increase N? Requires reshuffling all keys.

Adaptive sharding:
  Monitor shard load. When shard 1 exceeds 500 ops/sec:
    Split shard 1 into shard 1a and 1b (rehash keys)
    Update router: requests for shard 1 now hash to {1a, 1b}

  When shard 2 drops below 50 ops/sec:
    Merge shard 2 + 3 (rehash keys)

  Result: fewer hot shards, better load balance


APPROXIMATE COUNTING (Cardinality Estimation)
═════════════════════════════════════════════════════════

Exact count: "unique visitors today?"
  Store: set of {visitor IDs} → 1 million unique
  Memory: 1 million * 16 bytes (UUID) = 16 GB ❌

Approximate count (HyperLogLog):
  Store: compact sketch
  Memory: ~2 KB
  Result: "approximately 1,001,230 unique visitors" ±2% ✅

How? HyperLogLog uses the observation:
  If you hash 1 million random values, on average, the longest
  leading zero sequence is log2(1,000,000) ≈ 20 bits.
  Shorter longest zero = fewer unique values.
  Longer longest zero = more unique values.
```

---

## ⚙️ How It Actually Works

### CRDT Counters — Counting Without Coordination

**Problem:** Distributed system with 3 data centers (A, B, C). Each increments a counter independently. When they partition, they disagree. When they rejoin, they need to agree on the final count.

**Solution:** G-Counter (Grow-Only CRDT) — each replica owns a shard; final count is the sum of all shards. Any two replicas can sync without a coordinator.

```java
@Data
public class GCounter {
    // Map: replica ID → shard value
    private final Map<String, Long> shards = new ConcurrentHashMap<>();

    public void increment(String replicaId) {
        shards.compute(replicaId, (k, v) -> v == null ? 1 : v + 1);
    }

    public long value() {
        // Sum all shards
        return shards.values().stream().mapToLong(Long::longValue).sum();
    }

    // Merge with another replica's state
    public void merge(GCounter other) {
        other.shards.forEach((replicaId, otherValue) -> {
            shards.merge(replicaId, otherValue, Math::max);  // Take max of each shard
        });
    }
}

// Usage in distributed system
public class DistributedMetricsService {

    private final GCounter requestCounter = new GCounter();
    private final String replicaId = "replica-" + UUID.randomUUID();

    public void recordRequest() {
        requestCounter.increment(replicaId);
    }

    public long getGlobalRequestCount() {
        return requestCounter.value();
    }

    // On sync with another replica (e.g., B)
    public void syncWithRemote(GCounter remoteCounter) {
        requestCounter.merge(remoteCounter);
        log.info("Synced counter. New total: {}", requestCounter.value());
    }
}
```

**PN-Counter (Increment and Decrement):**

G-Counter only grows. For a true counter (increment and decrement), use PN-Counter: two G-Counters, one for increments, one for decrements.

```java
@Data
public class PNCounter {
    private GCounter increments = new GCounter();
    private GCounter decrements = new GCounter();

    public void increment(String replicaId) {
        increments.increment(replicaId);
    }

    public void decrement(String replicaId) {
        decrements.increment(replicaId);
    }

    public long value() {
        return increments.value() - decrements.value();
    }

    public void merge(PNCounter other) {
        increments.merge(other.increments);
        decrements.merge(other.decrements);
    }
}
```

**In an interview, if asked:** "For distributed counters without a central coordinator, I use CRDT counters (G-Counter for grow-only, PN-Counter for increment/decrement). Each replica owns a shard, increments its own shard, and syncs with others when network heals. The total is the sum of all shards. No locking, no coordinator — any two replicas can merge and reach agreement. This is how distributed databases like Riak track causality."

---

### Time-Series Counters — Bucketed by Time

**Problem:** You want to know "requests per second" for the last minute, but you only track a global counter. You can see the total but not if traffic spiked in second 30.

**Solution:** Bucket time into 10-second (or 1-second) windows. Track a counter for each bucket. On read, sum the recent buckets.

```java
@Service
@Slf4j
public class TimeSeriesCounterService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    // Bucket size in seconds
    private static final int BUCKET_SIZE_SECONDS = 10;

    public void recordRequest(String metricName) {
        // Current bucket: current timestamp rounded down to nearest bucket boundary
        long now = System.currentTimeMillis();
        long bucketStart = (now / (BUCKET_SIZE_SECONDS * 1000)) * (BUCKET_SIZE_SECONDS * 1000);
        
        String bucketKey = metricName + ":bucket:" + bucketStart;
        
        // Increment the bucket
        redisTemplate.opsForValue().increment(bucketKey);
        
        // TTL: keep buckets for 1 hour (360 seconds / 10 = 36 buckets)
        redisTemplate.expire(bucketKey, 3600, TimeUnit.SECONDS);
    }

    public long getCountInLastMinute(String metricName) {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60 * 1000;
        
        long totalCount = 0;

        // Iterate over all buckets in the last minute
        for (long bucketStart = oneMinuteAgo; bucketStart <= now; bucketStart += BUCKET_SIZE_SECONDS * 1000) {
            String bucketKey = metricName + ":bucket:" + bucketStart;
            Object count = redisTemplate.opsForValue().get(bucketKey);
            totalCount += (count != null) ? (Long) count : 0;
        }

        return totalCount;
    }

    public long getCountInLastHour(String metricName) {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3600 * 1000;

        long totalCount = 0;
        for (long bucketStart = oneHourAgo; bucketStart <= now; bucketStart += BUCKET_SIZE_SECONDS * 1000) {
            String bucketKey = metricName + ":bucket:" + bucketStart;
            Object count = redisTemplate.opsForValue().get(bucketKey);
            totalCount += (count != null) ? (Long) count : 0;
        }

        return totalCount;
    }

    // Detect spike: if last bucket has 3x average of previous buckets
    public boolean isSpiking(String metricName) {
        long avgCount = getCountInLastMinute(metricName) / 6;  // 6 buckets in a minute
        long lastBucketCount = getLastBucketCount(metricName);
        return lastBucketCount > avgCount * 3;
    }

    private long getLastBucketCount(String metricName) {
        long now = System.currentTimeMillis();
        long lastBucketStart = (now / (BUCKET_SIZE_SECONDS * 1000)) * (BUCKET_SIZE_SECONDS * 1000);
        String bucketKey = metricName + ":bucket:" + lastBucketStart;
        Object count = redisTemplate.opsForValue().get(bucketKey);
        return (count != null) ? (Long) count : 0;
    }
}
```

**In an interview, if asked:** "I bucket counters by time (e.g., 10-second windows) instead of tracking only a global total. Each bucket is a separate Redis key with a TTL. To get 'requests in the last minute,' I sum the 6 buckets from the last minute. This allows detection of traffic spikes in specific time windows and is more useful for alerting than global counters."

---

### Adaptive Sharding — Dynamic Shard Count Based on Load

**Problem:** You have 10 shards. One shard gets 80% of the traffic (hot shard). The other 9 are idle. You need to split the hot shard into more shards, but reshuffling all keys is expensive.

**Solution:** Monitor shard load. When a shard exceeds a threshold, split it. When a shard is cold, merge it.

```java
@Service
@Slf4j
public class AdaptiveShardingService {

    private final ShardRegistry shardRegistry;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final long SPLIT_THRESHOLD_OPS_PER_SEC = 10000;
    private static final long MERGE_THRESHOLD_OPS_PER_SEC = 100;

    @PostConstruct
    public void start() {
        // Monitor shard load every 30 seconds
        scheduler.scheduleAtFixedRate(this::rebalanceShards, 30, 30, TimeUnit.SECONDS);
    }

    private void rebalanceShards() {
        for (Shard shard : shardRegistry.getAllShards()) {
            long opsPerSec = metricsService.getOpsPerSecond(shard.getId());

            if (opsPerSec > SPLIT_THRESHOLD_OPS_PER_SEC) {
                log.info("Shard {} is hot ({} ops/sec), splitting", shard.getId(), opsPerSec);
                splitShard(shard);
            } else if (opsPerSec < MERGE_THRESHOLD_OPS_PER_SEC && shardRegistry.getShardCount() > 10) {
                // Only merge if we have more than 10 shards (keep minimum parallelism)
                log.info("Shard {} is cold ({} ops/sec), merging with neighbor", shard.getId(), opsPerSec);
                mergeShard(shard);
            }
        }
    }

    private void splitShard(Shard shard) {
        // 1. Create two new shards from one
        Shard newShard1 = new Shard("shard-" + UUID.randomUUID(), shard.getStartKey(), shard.getMidKey());
        Shard newShard2 = new Shard("shard-" + UUID.randomUUID(), shard.getMidKey(), shard.getEndKey());

        // 2. Rehash keys from old shard into new shards
        List<String> keysInShard = shardRegistry.getKeysForShard(shard.getId());
        for (String key : keysInShard) {
            Shard targetShard = (hashKey(key) < shard.getMidKey()) ? newShard1 : newShard2;
            // Move key to new shard (async operation)
            shardRegistry.moveKey(key, shard.getId(), targetShard.getId());
        }

        // 3. Register new shards, deregister old shard
        shardRegistry.addShard(newShard1);
        shardRegistry.addShard(newShard2);
        shardRegistry.removeShard(shard.getId());

        log.info("Split shard {} into {} and {}", shard.getId(), newShard1.getId(), newShard2.getId());
    }

    private void mergeShard(Shard shard) {
        Shard neighbor = shardRegistry.getNeighbor(shard.getId());
        if (neighbor == null) return;

        // 1. Rehash keys from shard into neighbor
        List<String> keysInShard = shardRegistry.getKeysForShard(shard.getId());
        for (String key : keysInShard) {
            shardRegistry.moveKey(key, shard.getId(), neighbor.getId());
        }

        // 2. Deregister the shard
        shardRegistry.removeShard(shard.getId());

        log.info("Merged shard {} into {}", shard.getId(), neighbor.getId());
    }

    private long hashKey(String key) {
        // Consistent hash function
        return Math.abs((long) key.hashCode());
    }
}
```

**In an interview, if asked:** "I use adaptive sharding: monitor load on each shard. If a shard exceeds 10K ops/sec, split it into two shards (rehash keys). If a shard drops below 100 ops/sec, merge it with a neighbor. This avoids the problem of a single hot shard bottlenecking the system. Reshuffling is done asynchronously, so reads/writes continue during rebalancing."

---

## 🏢 Real World — Where Companies Use This

- **Cassandra**: Uses CRDT concepts for anti-entropy repair — replicas sync without a central coordinator.
- **Prometheus**: Time-series metrics with 15-second scrape intervals. Bucketing enables spike detection and alerting.
- **Redis Streams**: Time-series data structure with automatic bucketing and approximate cardinality (HyperLogLog module).
- **DynamoDB**: Adaptive sharding (AWS handles partitioning internally). Users specify provisioned throughput, AWS reshards.
- **Datadog**: Time-series metrics platform. Bucketing at multiple granularities (1s, 10s, 1m, 1h). HyperLogLog for unique user counting.

---

## 🧭 When to Use vs When NOT to Use

| Use advanced counters when | Do NOT use when |
|---|---|
| Distributed system without central coordination (CRDT) | Single, centralized system — basic counter is sufficient |
| Need historical data (traffic per minute) (Time-series) | Only care about current total, not historical trends |
| One shard is a bottleneck under load (Adaptive) | Load is evenly distributed across shards |
| Counting unique items with massive cardinality (Approximate) | Dataset is small enough to store exactly |

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | CRDT: no coordinator, works during partitions. Time-series: detect traffic spikes, better alerting. Adaptive: auto-scales to load. Approximate: 2 KB memory for 1 million unique items. |
| **You lose** | CRDT: eventual consistency (temporary disagreement). Time-series: storage overhead (multiple buckets). Adaptive: complexity of reshuffling keys. Approximate: accuracy loss (±2%). |
| **Failure mode** | CRDT merge produces wrong total if logic is buggy. Time-series bucket boundary issues (request spans two buckets). Adaptive shard split leaves keys orphaned if crashes mid-rebalance. HyperLogLog underestimates if hash function is weak. |

---

## 🔬 Interview Q&As

### Q: "You have 3 data centers that can't talk to each other. Each independently increments a counter. When they sync, what's the correct total?"

> I use CRDT G-Counters: each data center increments its own shard. When they sync, the total is the sum of all shards. Data center A increments its shard to 1000, B to 800, C to 600. On sync, all three see the total as 2400 — no disagreement, no coordinator needed. This is how distributed databases achieve availability under network partitions.

---

### Q: "You track 'requests per second' but only store a global counter. How do you detect traffic spikes?"

> Time-bucketed counters: instead of one global counter, I bucket by 10-second windows. Last minute has 6 buckets. If the last bucket is 3x the average of the previous 5, I flag it as a spike. This gives me second-level granularity for alerting. I can also detect if the spike happened at second 0-10 or second 50-60, enabling root cause analysis.

---

### Q (Tier 2): "One of your 10 shards is handling 80% of the traffic, becoming a bottleneck. You can't afford to stop the system to manually split the shard. How?"

> Adaptive sharding with online reshuffling: I monitor each shard's load. When shard 1 exceeds 10K ops/sec, I create two new shards (1a and 1b) with disjoint key ranges. I then asynchronously rehash keys from shard 1 into 1a and 1b without stopping reads/writes (clients read from shard 1, miss, discover the key was moved to 1a/1b). Once all keys are migrated and 1a/1b are caught up, I deregister shard 1. This is gradual and transparent to the application."

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "For distributed counters without coordination: CRDT (G-Counter). For time-based analytics: bucket by time (10-second windows). For auto-scaling: adaptive sharding (split hot, merge cold). For cardinality estimation: HyperLogLog (~2 KB for 1M unique items)."

---

## 🔗 Related Concepts

- **`09-sharded-counters.md`** — core sharding pattern (split one counter into N shards to reduce contention). This companion extends with CRDT (no coordinator), time-series (buckets), and adaptive (dynamic sharding).
- **`06-distributed-locking.md`** — distributed locks can protect shard rebalancing operations (split, merge) to avoid races.
- **`05-consistent-hashing.md`** — consistent hashing is used in some sharding schemes to route keys to shards without full remapping on shard count changes.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"CRDTs Explained"** — Arpit Bhayani (YouTube: search "Arpit Bhayani CRDT") | Deep dive on G-Counter, PN-Counter, and conflict-free replicated data types. | ~25 min |
| **"Time-Series Databases"** — System Design Primer (https://github.com/donnemartin/system-design-primer#time-series-database) | Bucketing strategies, aggregation, retention policies. | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | Companion file created. Covers: CRDT counters (G-Counter, PN-Counter for coordination-free distributed systems), time-series buckets (spike detection), adaptive sharding (auto-split/merge based on load), approximate counting (HyperLogLog). Real-world patterns from Cassandra/Prometheus/DynamoDB. 3 Q&As (all advanced scenarios). Pairs with core `09-sharded-counters.md`. |
