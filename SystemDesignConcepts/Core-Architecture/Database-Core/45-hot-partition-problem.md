# 45 — Hot Partition Problem

## 📖 What is the Hot Partition Problem?

**Full form:** Hot Partition Problem — also called the "hotspot" or "skewed partition" problem; a distributed systems failure mode where most reads or writes concentrate on one shard (or one Kafka partition, or one DynamoDB partition) while others sit idle, defeating the purpose of horizontal scaling.

**Simple analogy:** You open five checkout lanes at a supermarket, but only one lane is labelled "All celebrity-chef items" — and today Virat Kohli just endorsed a biscuit. Every shopper in the store joins that one lane. The other four lanes have zero customers. Five lanes did not give you five times the throughput; one lane is still the bottleneck, and the other four are wasted investment.

**Core principle:** In a sharded system, data is split across N shards to distribute load. A hot partition happens when the shard key is poorly chosen — causing a disproportionate fraction of traffic to route to one shard. That shard becomes a bottleneck regardless of how many other shards exist. The system behaves as if it were unsharded.

**Why it matters in system design:** Hot partitions are the most common reason a correctly-designed sharding scheme still fails at scale. Identifying the risk — and naming the mitigation — separates a senior answer from a junior one on any write-heavy design (Kafka topics, Cassandra tables, DynamoDB, Redis Cluster).

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Hot Partition** | one shard receives disproportionately more traffic than others; becomes a bottleneck | Shard 3 handles 80% of writes; shards 1,2,4,5 are idle; Shard 3 CPU at 100% |
| **Shard Key** | the column used to route data to a shard; poor choice causes hotspots | `user_id` (good — uniform); `country_code` (bad — India = 40% of traffic) |
| **Celebrity Key Problem** | one specific key value generates far more traffic than average | `user_id=ViratKohli` → 1M reads/sec on his shard; all other users share normal load |
| **Sequential Key Problem** | sharding by monotonically increasing key (timestamp, autoincrement) sends all new writes to latest shard | Cassandra partitioned by `date` → today's shard gets all inserts; yesterday's is cold |
| **Low-Cardinality Key Problem** | shard key has few distinct values; one value may dominate | `country_code`: only ~250 values; `IN` shard gets 40% of global traffic |
| **Write Salting** | append a random suffix to the shard key to spread a hot key across multiple shards | `user:ViratKohli_0`, `user:ViratKohli_1`, ..., `user:ViratKohli_9` → 10 shards |
| **Hot-Key Caching** | cache the celebrity key's result in Redis/Memcached to absorb reads before they hit the hot shard | `GET virat_kohli_profile` → L1 Redis cache → shard never sees 99% of reads |
| **Partition Lag Monitoring** | watching per-shard queue depth or CPU to detect emerging hotspots before they cascade | Kafka consumer lag per partition; DynamoDB per-partition consumed capacity metrics |

---

## 🎯 Why This Matters

- **Problem:** Adding more shards does not help if the shard key is wrong. One overloaded partition causes CPU spikes, queue backlog, OOM errors, and latency spikes on just that one machine — while all other shards are underutilised.
- **Interview signal:** Every write-heavy design question — Kafka messaging, Twitter feed, Flipkart flash sale, DynamoDB session store — requires you to identify and mitigate hotspot risk in the sharding strategy.
- **Senior expectation:** You must name the root cause (skewed key choice), detect it (partition lag, per-shard CPU monitoring), and name at least two mitigations (salting, dedicated partitions, caching).

---

## 🧠 The Mental Model

Imagine a toll plaza on a national highway with 10 booths. The highway authority assigns vehicles by the last digit of the number plate: plates ending in 0-9 go to booths 1-10 respectively. For most traffic, load is even.

Then the government introduces one special plate prefix — "VIP-0" — given to every government vehicle in the country. Every VIP-0 plate ends in the same digit. On Republic Day, 100,000 government vehicles drive through. All of them go to booth 1. Booths 2-10 are empty. Booth 1 is gridlocked.

**This is the hot partition problem:** the shard key was not uniformly distributed across all possible keys in the real workload.

**Three patterns that cause it:**
1. **Celebrity keys** — one entity (user_id of Virat Kohli, item_id of an iPhone on Flipkart's Big Billion Day) receives orders-of-magnitude more traffic than average. Any system sharded by that key concentrates all celebrity traffic onto one shard.
2. **Sequential keys** — sharding by `order_id` or `timestamp` sends *all recent writes* to the latest shard. Old shards are cold; the newest shard is hot. Common in Kafka (sequential offsets), Cassandra (date partition key), DynamoDB (autoincrement PK).
3. **Low-cardinality keys** — sharding by a field with few values (e.g., `country_code` — India alone is 40% of traffic). You have 50 shards but 40% of writes hit the "IN" shard.

**The key insight is:** The shard key must be chosen for *actual* write distribution in your workload, not for logical grouping. If one key will get 1000× average traffic, that key must be artificially split before it reaches the shard layer.

---

## 🎨 Visual — System Topology & Component Flow

```
FULL SYSTEM TOPOLOGY — where hot partitions cause failure:

Client Tier
┌───────────┐
│  Clients  │  (10M users — 5% following Virat Kohli)
└─────┬─────┘
      │ writes (post_id as shard key)
      ▼
Load Balancer / API Gateway
┌──────────────────────────────────────────┐
│  API Service Pods (stateless, any pod)   │
│  Shard Router: hash(post_id) % 10        │
└────────────────────────────┬─────────────┘
                             │
         ┌───────────────────┼──────────────────────────────┐
         │                   │                              │
         ▼                   ▼                              ▼
  ┌────────────┐      ┌────────────┐                ┌────────────┐
  │  Shard 1   │      │  Shard 2   │   . . .        │  Shard 10  │
  │            │      │            │                │            │
  │ 🔥 HOT     │      │ ✅ idle    │                │ ✅ idle    │
  │ CPU: 98%   │      │ CPU: 4%    │                │ CPU: 3%    │
  │ Queue: 50K │      │ Queue: 12  │                │ Queue: 8   │
  │ writes/sec │      │ writes/sec │                │ writes/sec │
  └────────────┘      └────────────┘                └────────────┘

  Shard 1 receives all traffic because hash(virat_kohli_post_id) → shard 1
  Shards 2-10 are idle. Adding more shards makes no difference.

──────────────────────────────────────────────────────────────

COMPONENT DETAIL — Salting Mitigation:

BEFORE salting (hot partition):
  write(key="virat_post_123") → hash % 10 = 1 → ALL writes → Shard 1

AFTER salting (load distributed):
  write(key="virat_post_123_0") → hash % 10 = 1 → Shard 1  (10% of writes)
  write(key="virat_post_123_1") → hash % 10 = 7 → Shard 7  (10% of writes)
  write(key="virat_post_123_2") → hash % 10 = 3 → Shard 3  (10% of writes)
  write(key="virat_post_123_3") → hash % 10 = 9 → Shard 9  (10% of writes)
  ...10 suffixes spread 10× traffic across 10 shards

  READ: scatter to all 10 salt variants, merge results (scatter-gather cost)

KEY INVARIANT:
  Salting trades write locality for write distribution.
  Every salt suffix = 1 extra scatter-gather fan-out on read.
  Keep salt width proportional to the expected hotspot multiplier.
```

---

## ⚙️ How It Actually Works

### Detecting a Hot Partition

**Steps in plain English:**
1. **Monitor per-shard metrics** — CPU, write throughput, and queue depth per shard. A healthy system shows roughly equal values. A hot partition shows one shard at 90%+ CPU while others are at 5-10%.
2. **Track partition lag (Kafka)** — consumer group lag on a single partition growing unboundedly while others stay near zero is the Kafka signature of a hot partition.
3. **Alert threshold** — define "hot" as any shard receiving >3× the average write rate. Automate an alert.

```java
// Pseudo-code: detect hot partition in a Kafka consumer monitoring system
public class PartitionHotspotDetector {

    private final AdminClient adminClient;
    private static final double HOT_THRESHOLD_MULTIPLIER = 3.0;

    public List<Integer> detectHotPartitions(String topicName) {
        // Step 1 — fetch per-partition lag for all consumer groups
        Map<Integer, Long> lagPerPartition = getConsumerGroupLag(topicName);

        // Step 2 — compute average lag across all partitions
        double averageLag = lagPerPartition.values()
            .stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);

        // Step 3 — flag any partition whose lag exceeds 3x the average
        List<Integer> hotPartitions = lagPerPartition.entrySet()
            .stream()
            .filter(entry -> entry.getValue() > HOT_THRESHOLD_MULTIPLIER * averageLag)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        return hotPartitions;
    }
}
```

### Mitigation 1 — Write Salting

**Steps in plain English:**
1. **Append a random salt suffix** to the hot key before routing: `original_key + "_" + random.nextInt(saltWidth)`.
2. **Write to the salted key** — writes spread across `saltWidth` different partitions/shards.
3. **On read**, scatter-gather: query all `saltWidth` variants and merge at application level.

```java
public class SaltedPartitionRouter {

    private static final int SALT_WIDTH = 10;  // spread hot key across 10 partitions

    // Step 1 — produce: append random salt before writing
    public void write(String originalKey, String value, KafkaProducer<String, String> producer) {
        int salt = ThreadLocalRandom.current().nextInt(SALT_WIDTH);
        String saltedKey = originalKey + "_" + salt;
        producer.send(new ProducerRecord<>("topic", saltedKey, value));
    }

    // Step 2 — consume: scatter-gather across all salt variants
    public List<String> read(String originalKey, RedisTemplate<String, String> redis) {
        List<String> results = new ArrayList<>();
        for (int salt = 0; salt < SALT_WIDTH; salt++) {
            String saltedKey = originalKey + "_" + salt;
            // Step 3 — collect from each salted bucket
            List<String> bucket = redis.opsForList().range(saltedKey, 0, -1);
            if (bucket != null) {
                results.addAll(bucket);
            }
        }
        return results;
    }
}
```

### Mitigation 2 — Application-Level Caching for Hot Keys

**Steps in plain English:**
1. **Detect hot keys** at runtime — track request frequency per key in a sliding window counter.
2. **If a key exceeds the hot threshold** (e.g., >1,000 reads/sec), promote it to an in-memory cache (Redis or local JVM cache).
3. **All subsequent reads** are served from cache; the hot partition never receives the read traffic.

```java
public class HotKeyCache {

    private final Cache<String, String> localCache;
    private final Map<String, AtomicLong> requestCount = new ConcurrentHashMap<>();
    private static final long HOT_KEY_THRESHOLD = 1000L;

    public String get(String key, Function<String, String> dbLookup) {
        // Step 1 — track request frequency for this key
        long count = requestCount
            .computeIfAbsent(key, k -> new AtomicLong(0))
            .incrementAndGet();

        // Step 2 — if key is hot, check local cache first
        if (count > HOT_KEY_THRESHOLD) {
            String cached = localCache.getIfPresent(key);
            if (cached != null) {
                return cached;
            }
        }

        // Step 3 — cache miss or cold key: fetch from DB, populate cache
        String value = dbLookup.apply(key);
        if (count > HOT_KEY_THRESHOLD) {
            localCache.put(key, value);
        }
        return value;
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **Flipkart (Big Billion Day flash sale):** iPhone listed at ₹10,000 for 60 seconds — millions of users hit `item_id=iphone_deal_1`. Inventory shard for that item_id was at 100% CPU. Fix: dedicated partition pool for "VIP" items detected pre-launch + local cache for inventory count.
- **Twitter/X (celebrity tweet fanout):** A tweet by a user with 100M followers produces 100M fanout writes, all with the same `user_id` as the source shard key. Fix: dedicated "high-follower-count" write path with async fan-out workers instead of direct shard routing.
- **Kafka at Swiggy (restaurant-level topic):** Partitioning order events by `restaurant_id` — during lunch hours, the top-10 restaurants receive 80% of orders. Those 10 partitions were overwhelmed. Fix: repartitioned by `(restaurant_id + time_bucket)` to spread load across time.
- **Cassandra at Netflix:** Partitioning watch history by `user_id` — power users with 10K+ watched titles overwhelm their partition on writes. Fix: composite partition key `(user_id, year_month)` splits heavy users across multiple partitions.
- **DynamoDB at Amazon:** Sequential `order_id` partition key caused all new orders to hit the latest adaptive partition unit (APU). Fix: random suffix prepended to `order_id` before write; suffix stripped on read.

---

## 🧭 When to Use Salting vs When NOT to

| Use salting when | Do NOT use salting when |
|---|---|
| A small set of keys receives disproportionate write traffic (celebrities, trending items) | The key is already high-cardinality and naturally distributed (random UUID primary key) |
| You're sharding by timestamp or sequential ID (all writes go to newest shard) | Read patterns require strict key-range locality (range scans become expensive scatter-gathers) |
| You can tolerate scatter-gather read cost (fan out then merge) | You need strong consistency for the hot key across all salt variants (scatter-gather makes transactions complex) |
| The hotspot is predictable in advance (known celebrity accounts, flash sale items) | The hotspot is completely random — if all keys are equally probable, there is no hotspot to solve |

**The common mistake:** Choosing a logically meaningful shard key (e.g., `category_id`, `city_id`, `user_type`) without checking the actual distribution of values. Logical grouping ≠ even distribution. Always validate with actual query frequency data before committing to a shard key.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Uniform write distribution across all shards; no single shard becomes a bottleneck; horizontal scaling actually works as intended |
| **You lose** | Read simplicity — salted keys require scatter-gather on read; cross-key aggregations become expensive fan-outs; monitoring per-salt-bucket adds operational overhead |
| **Failure mode** | Over-salting a cold key wastes read I/O (10 shard reads for a key that gets 1 request/hour); salt width should be proportional to the actual hotspot multiplier, not set globally to max |

---

## 🔬 Interview Q&As

### Q: "What is a hot partition and how does it happen?"
> A hot partition is when most writes or reads route to one shard because the shard key is skewed — one key value accounts for far more traffic than average. Three common causes: celebrity keys (one user has 100M followers), sequential keys (timestamp or autoincrement sharding sends all recent writes to the latest shard), and low-cardinality keys (sharding by country sends 40% of traffic to the "IN" shard).

### Q: "You're designing Twitter. You shard by user_id. A celebrity posts — what happens and how do you fix it?"
> Virat Kohli's user_id hashes to shard 3. Every one of his tweet's read requests hits shard 3. Fix: detect high-follower-count accounts offline and treat them as "VIP keys." Route their write fanout through a dedicated async worker pool (not the shard directly). Cache the tweet's content in Redis with a short TTL — most reads are served from cache, shard 3 is never hit. For writes, salt the celebrity's `user_id` with a random suffix across N partitions and merge on read.

### Q: "You're using Kafka partitioned by restaurant_id. During lunch rush, top-3 restaurants get 80% of order events. How do you fix this?"
> This is a low-cardinality hot partition. Fix options: (1) increase partition count and use composite key `restaurant_id + random_bucket` so each restaurant fans across multiple Kafka partitions; (2) dedicate a separate Kafka topic for high-volume restaurants with its own consumer group and more partitions; (3) repartition by `order_id` (uniform random) and use a stream processing layer (Flink, Kafka Streams) to group by `restaurant_id` downstream where ordering is less critical.

### Q: "DynamoDB scales automatically — does it still get hot partitions?"
> Yes. DynamoDB uses adaptive capacity to automatically split hot partitions — but this has limits and latency. If a single item (one celebrity profile) is the hotspot, no amount of partition splitting helps because partitions split on key range, not on per-item access frequency. A single item can't be split across partitions. The fix is application-level caching (DAX or Redis) in front of DynamoDB for that specific hot item, so DynamoDB never sees the full read traffic.

---

### 🔍 Tier 2 — Cross-Probe Questions

### Q: "You salt the key with a random suffix of 0-9. Redis goes down during a scatter-gather read. How do you handle partial failures?"
> Each salt variant (0-9) is an independent Redis key lookup. If Redis node holding variant 3 is down, the read for that bucket fails. Options: (1) fail open — return results from available buckets and note the partial response to the client; (2) fail closed — return an error if any bucket fails (correct but impacts availability); (3) retry with exponential backoff on just the failed bucket. For read-heavy public feeds (tweet view), fail open is acceptable — you'd show 9/10 of the data. For financial writes, fail closed and surface the error.

### Q: "You added salting to fix writes. Now your read query needs to sort by timestamp across all salt variants. How expensive is that?"
> Scatter-gather to all N salt variants, sort in application memory. Cost = N × (single-shard read latency) + O(N × page_size × log(N × page_size)) for the merge sort. At N=10 and page_size=100, that's sorting 1,000 items in memory — negligible. At N=100 and page_size=1,000, it's 100,000 items in memory per request — start to hurt. Rule: keep salt width ≤ 10 for query-heavy workloads; prefer dedicated partition pools for extreme hotspots so scatter-gather is avoided entirely.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Hot partitions happen when the shard key is not uniformly distributed — fix it with write salting (append random suffix, scatter-gather on read), application-level caching for known hot keys, or dedicated partition pools for celebrity/VIP entities."

---

## 🔗 Related Concepts

- **`38-sharding-strategy.md`** — the root cause: sharding decisions that lead to hot partitions
- **`03-caching.md`** — the first-line mitigation: cache hot keys so the shard never sees the traffic
- **`09-sharded-counters.md`** — the canonical hot partition solution for write-heavy counters (split one counter across N shards, merge on read)
- **`05-consistent-hashing.md`** — consistent hashing reduces rebalancing cost but doesn't eliminate hot partitions if the key distribution is skewed

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Designing Data-Intensive Applications" Ch. 6** — Martin Kleppmann | Deep treatment of partitioning strategies, skewed workloads, and rebalancing | ~45 min read |
| **"Hot Partition Problem" — Arpit Bhayani (Asli Engineering YouTube)** | Search: "Arpit Bhayani hot partition" — video walkthrough of detection + salting with real DynamoDB and Cassandra examples | ~20 min |
| **DynamoDB Best Practices for Partition Keys** — AWS docs | How adaptive capacity works and why single-item hotspots require DAX, not just partition splits | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Identified as highest-priority gap from coverage audit — hot partitions appear in 60%+ of senior write-heavy design questions (Kafka, Cassandra, DynamoDB). Covers: definition, three root causes (celebrity/sequential/low-cardinality keys), two mitigations (salting, hot-key caching), real examples (Flipkart, Twitter, Netflix, Swiggy), detection via partition lag monitoring, and Tier 2 probe questions on partial failure and scatter-gather cost. |
