# 38 — Database Sharding Strategy

## 📖 What is Database Sharding?

**Full form:** Database Sharding — horizontal partitioning of a database across multiple independent machines (shards), where each shard holds a subset of the total data.

**Simple analogy:** A public library with a single catalog becomes impossible to manage once it holds millions of books. So the city opens five branch libraries, each serving a district: Branch A holds books with spines starting A–E, Branch B holds F–J, and so on. Any librarian can instantly route a patron to the right branch. Sharding does the same: it splits one overloaded database into independent branches, each authoritative for its slice of data.

**Core principle:** A shard key determines which shard owns a given row. Every read and write is routed to exactly one shard (for the key lookup), eliminating the bottleneck of a single machine. The routing layer — a load balancer, a proxy, or application logic — translates a key into a shard address.

**Why it matters in system design:** Single-machine databases hit wall at ~10K writes/sec and ~2 TB of hot data. Sharding is how YouTube, Instagram, and DynamoDB scale to billions of rows without coordinator bottlenecks.

---

## 🎯 Why This Matters

- **Problem:** A single PostgreSQL instance can serve ~10K writes/sec; YouTube receives millions of video metadata writes per minute. No vertical scaling can bridge this gap — only sharding (horizontal split) does.
- **Interview signal:** Any design with "store user data at scale" — Uber, Twitter, Instagram, BookMyShow seat inventory — will require you to choose a shard key and defend it.
- **Senior expectation:** You must discuss hotspot risk, cross-shard query cost, rebalancing when shards fill up, and what happens when the shard key is wrong.

---

## 🧠 The Mental Model

Imagine a city's postal sorting office on a peak holiday. Every parcel in the country arrives at one warehouse — workers are overwhelmed, parcels pile up, and the single conveyor belt breaks. Management builds five regional sorting centers, each handling packages for a geographic zone: North, South, East, West, Central.

**Normal flow with sharding:** A package from Mumbai is stamped "West Zone." A truck drives it directly to the West center. No other center ever touches it. Sorting time drops from hours to minutes because five parallel teams work simultaneously.

**What goes wrong without sharding:** The single warehouse gets so many packages from one celebrity's fan club (all mailing to the same address) that it grinds to a halt. This is the celebrity problem — one shard key value receives all the traffic.

**What good sharding fixes:** A well-chosen zone boundary ensures no single center gets the bulk. If "West" becomes overloaded (Mumbai booms), you split West into West-1 and West-2 — rebalancing — migrating half the West packages to a new center. This is expensive, but planned. The alternative (one center) is catastrophic.

**Four routing strategies** map to the four ways you can assign a package to a center: by range (postcode order), by hash (random but even), by directory (a lookup map), or by geography (actual physical location). Each has a different trade-off between simplicity, hotspot risk, and flexibility.

**The key insight is:** The shard key is a one-time architectural decision — choose it for write distribution and query locality, because rebalancing later is costly and replication won't save you from a bad key choice.

---

## 🎨 Visual — System Topology & Component Flow

```
FULL SYSTEM TOPOLOGY:
Client Tier
┌────────────┐
│  Client    │ (mobile app, web browser)
└─────┬──────┘
      │ HTTPS
      ▼
CDN Tier
┌────────────────┐
│   CDN          │ (CloudFront, Fastly — caches static, passes API calls)
└───────┬────────┘
        │
        ▼
Load Balancer Tier
┌───────────────────────┐
│  Global Load Balancer │ (AWS ALB, Nginx — routes to service pods)
└──────────┬────────────┘
           │
           ▼
Service Tier
┌──────────────────────────────────────────────────────┐
│  Application / API Service Pods (stateless)          │
│                                                      │
│  ┌───────────────────────────────────────────────┐   │
│  │  SHARD ROUTER (embedded in service)           │   │
│  │  Input: shard_key (e.g., user_id=12345)       │   │
│  │  Logic: hash(user_id) % N → shard #3          │   │
│  │  Output: connection to Shard 3                │   │
│  └───────────────────────────────────────────────┘   │
└──────────────────────────────┬───────────────────────┘
                               │ routes to correct shard
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
Cache Tier (per-shard optional)
┌─────────┐         ┌─────────┐         ┌─────────┐
│ Redis 1 │         │ Redis 2 │         │ Redis 3 │
│ shard 1 │         │ shard 2 │         │ shard 3 │
└────┬────┘         └────┬────┘         └────┬────┘
     │                   │                   │
     ▼                   ▼                   ▼
Database Tier
┌──────────┐        ┌──────────┐        ┌──────────┐
│ Shard 1  │        │ Shard 2  │        │ Shard 3  │
│ user_id  │        │ user_id  │        │ user_id  │
│ 1–3M     │        │ 3M–6M    │        │ 6M–9M    │
│ (range)  │        │ (range)  │        │ (range)  │
└──────────┘        └──────────┘        └──────────┘

KEY INVARIANT:
   The shard router lives in the service tier, NOT the database tier.
   Each shard is an independent database instance — no shared storage.
   Cross-shard queries require scatter-gather (query all shards, merge results).


COMPONENT DETAIL — Four Sharding Strategies:

┌──────────────────────────────────────────────────────────────────┐
│ STRATEGY 1: RANGE SHARDING                                       │
│                                                                  │
│  user_id 1 → 1,000,000       ──▶  Shard 1 (postgres-01)         │
│  user_id 1,000,001 → 2,000,000──▶  Shard 2 (postgres-02)        │
│  user_id 2,000,001 → ...     ──▶  Shard 3 (postgres-03)         │
│                                                                  │
│  ✅ Simple routing (compare ranges)                              │
│  ✅ Range scans stay on one shard                                │
│  ❌ Hotspot: new users all land on last shard (monotonic key)    │
│                                                                  │
│ STRATEGY 2: HASH SHARDING (consistent hashing ring)             │
│                                                                  │
│       hash(user_id) % N → shard index                           │
│                                                                  │
│  token 0         token 90        token 180       token 270      │
│  Shard A ────────────────────────────────────────────────────▶  │
│     [virtual nodes distribute load evenly around ring]          │
│                                                                  │
│  ✅ Even distribution; no hotspots for uniform key space        │
│  ❌ Range queries scatter across all shards                     │
│  ❌ Rebalancing requires key remapping when N changes           │
│                                                                  │
│ STRATEGY 3: DIRECTORY SHARDING (lookup table)                   │
│                                                                  │
│  ┌────────────────────────────────────────┐                     │
│  │ Shard Directory (Redis or DB table)    │                     │
│  │  user_id 12345 → shard-07             │                     │
│  │  user_id 99999 → shard-02             │                     │
│  │  tenant "acme" → shard-15            │                     │
│  └────────────────────────────────────────┘                     │
│                                                                  │
│  ✅ Flexible: can migrate hot users to dedicated shard          │
│  ✅ Handles celebrity users explicitly                          │
│  ❌ Directory is a SPOF (single point of failure) if uncached   │
│  ❌ Extra lookup latency (~1ms) per request                     │
│                                                                  │
│ STRATEGY 4: GEO SHARDING                                         │
│                                                                  │
│  Client IP → geo-resolve → region tag                           │
│                                                                  │
│  US users ──▶ us-east-1 DB cluster                              │
│  EU users ──▶ eu-west-1 DB cluster (GDPR compliance)            │
│  APAC users ──▶ ap-southeast-1 DB cluster                       │
│                                                                  │
│  ✅ Low latency (data near user)                                │
│  ✅ Data residency compliance (GDPR, CCPA)                      │
│  ❌ Uneven load (US has 10× EU traffic)                         │
│  ❌ Cross-region queries require replication or federation      │
└──────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Range → hotspots on write-heavy ends.
   Hash → even distribution, scatter-gather for range queries.
   Directory → maximum flexibility, SPOF risk on directory.
   Geo → compliance + latency, uneven traffic across regions.
```

---

## ⚙️ How It Actually Works

### Strategy 1: Range Sharding — Router Logic

**Steps:**
1. Define shard boundaries as sorted ranges of the shard key (e.g., user_id 0–1M = shard 1).
2. On every write or read, compare the key against the boundary list.
3. Return the shard connection for that range.
4. For range scans spanning one boundary (e.g., user_ids 900K–1.1M), query both shards and merge.

```java
import java.util.TreeMap;
import java.util.Map;
import javax.sql.DataSource;

public class RangeShardRouter {
    // TreeMap keys = upper bound of range; value = shard DataSource
    private final TreeMap<Long, DataSource> rangeMap = new TreeMap<>();

    public RangeShardRouter() {
        rangeMap.put(1_000_000L, DataSourceFactory.create("shard-1"));
        rangeMap.put(2_000_000L, DataSourceFactory.create("shard-2"));
        rangeMap.put(3_000_000L, DataSourceFactory.create("shard-3"));
        rangeMap.put(Long.MAX_VALUE, DataSourceFactory.create("shard-4"));
    }

    public DataSource getShardForUserId(long userId) {
        // ceilingEntry returns the smallest key >= userId
        Map.Entry<Long, DataSource> entry = rangeMap.ceilingEntry(userId);
        if (entry == null) {
            throw new IllegalArgumentException("No shard found for userId: " + userId);
        }
        return entry.getValue();
    }
}
```

---

### Strategy 2: Hash Sharding — Consistent Hashing Ring

**Steps:**
1. Place N virtual nodes for each physical shard around a 360-degree hash ring (using MD5 or SHA-1 to map each virtual node to a ring position).
2. On a write: hash the shard key, find the nearest virtual node clockwise, route to that shard.
3. On shard addition: only the keys between the new shard and its predecessor are remapped — not the entire dataset.
4. Virtual nodes (many points per shard on the ring) ensure uniform distribution even when shard count is small.

```java
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.List;

public class ConsistentHashRouter {
    private final SortedMap<Long, String> ring = new TreeMap<>();
    private static final int VIRTUAL_NODES_PER_SHARD = 150;

    public ConsistentHashRouter(List<String> shardIds) {
        for (String shardId : shardIds) {
            addShard(shardId);
        }
    }

    public void addShard(String shardId) {
        for (int i = 0; i < VIRTUAL_NODES_PER_SHARD; i++) {
            long hash = hash(shardId + "-vn-" + i);
            ring.put(hash, shardId);
        }
    }

    public void removeShard(String shardId) {
        for (int i = 0; i < VIRTUAL_NODES_PER_SHARD; i++) {
            long hash = hash(shardId + "-vn-" + i);
            ring.remove(hash);
        }
    }

    public String getShardForKey(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No shards registered");
        }
        long hash = hash(key);
        // Find first shard clockwise from this hash
        SortedMap<Long, String> tailMap = ring.tailMap(hash);
        long targetHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(targetHash);
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes("UTF-8"));
            return ByteBuffer.wrap(digest).getLong();
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }
}
```

---

### Strategy 3: Directory Sharding — Lookup Table with Cache-Aside

**Steps:**
1. Maintain a directory table: `(shard_key → shard_id)` stored in Redis (in-memory store) with a fallback to a relational directory DB.
2. On every request, check Redis first (cache-aside pattern: check cache, if miss fetch from DB, write back to cache).
3. For celebrity users (users whose data generates disproportionate traffic), override their directory entry to point to a dedicated shard.
4. Directory writes are infrequent; directory reads are cached — so latency overhead is minimal after warm-up.

```java
import redis.clients.jedis.Jedis;
import java.util.Optional;

public class DirectoryShardRouter {
    private final Jedis redis;           // Redis client for directory cache
    private final DirectoryRepository db; // Fallback: relational directory table

    public DirectoryShardRouter(Jedis redis, DirectoryRepository db) {
        this.redis = redis;
        this.db = db;
    }

    public String getShardForUser(long userId) {
        String cacheKey = "shard_dir:" + userId;
        // Step 1: check cache first (cache-aside)
        String cachedShardId = redis.get(cacheKey);
        if (cachedShardId != null) {
            return cachedShardId;
        }
        // Step 2: cache miss — query directory DB
        Optional<String> shardId = db.findShardForUser(userId);
        if (shardId.isEmpty()) {
            // Step 3: new user — assign to least-loaded shard
            String newShardId = assignNewUserToShard(userId);
            db.insertShardMapping(userId, newShardId);
            redis.setex(cacheKey, 3600, newShardId); // cache for 1 hour
            return newShardId;
        }
        // Step 4: write back to cache
        redis.setex(cacheKey, 3600, shardId.get());
        return shardId.get();
    }

    public void migrateCelebrityUser(long userId, String dedicatedShardId) {
        // Step 3: override celebrity user's shard assignment
        db.updateShardMapping(userId, dedicatedShardId);
        redis.del("shard_dir:" + userId); // invalidate cache
    }

    private String assignNewUserToShard(long userId) {
        // Assign to least-loaded shard (tracked separately)
        return db.findLeastLoadedShard();
    }
}
```

---

### Strategy 4: Geo Sharding — Region Selection

**Steps:**
1. Resolve the client's region from their IP or account registration country.
2. Map region to a shard cluster (e.g., "EU" → `eu-west-1-db-cluster`).
3. Route all reads and writes for this user to their regional cluster.
4. For cross-region queries (e.g., global analytics), run a scatter-gather across all regional shards and aggregate.

```java
import java.util.Map;
import java.util.HashMap;
import javax.sql.DataSource;

public class GeoShardRouter {
    private final Map<String, DataSource> regionToShard = new HashMap<>();

    public GeoShardRouter() {
        regionToShard.put("US", DataSourceFactory.create("us-east-1-postgres"));
        regionToShard.put("EU", DataSourceFactory.create("eu-west-1-postgres"));
        regionToShard.put("APAC", DataSourceFactory.create("ap-southeast-1-postgres"));
        regionToShard.put("DEFAULT", DataSourceFactory.create("us-east-1-postgres"));
    }

    public DataSource getShardForRegion(String countryCode) {
        String region = resolveRegion(countryCode);
        return regionToShard.getOrDefault(region, regionToShard.get("DEFAULT"));
    }

    private String resolveRegion(String countryCode) {
        if (countryCode == null) {
            return "DEFAULT";
        }
        if (countryCode.equals("US") || countryCode.equals("CA")) {
            return "US";
        }
        if (isEuropeanCountry(countryCode)) {
            return "EU";
        }
        if (isApacCountry(countryCode)) {
            return "APAC";
        }
        return "DEFAULT";
    }

    private boolean isEuropeanCountry(String code) {
        return code.matches("DE|FR|GB|IT|ES|NL|SE|PL|BE|CH|AT|DK|FI|NO");
    }

    private boolean isApacCountry(String code) {
        return code.matches("IN|CN|JP|SG|AU|KR|TH|ID|MY|PH");
    }
}
```

---

### Hotspot Problem — Celebrity User Mitigation

**Steps:**
1. Detect hotspot: monitor write/read QPS (queries per second) per shard; alert if any shard exceeds 3× the average.
2. For read hotspots: add a dedicated cache layer in front of that shard's hot rows.
3. For write hotspots (celebrity with many followers writing fan-out events): shard the celebrity's writes across multiple "sub-shards" using `(celebrity_id, random_suffix)` as a composite key, then merge on read.

```java
import java.util.Random;
import javax.sql.DataSource;

public class CelebrityWriteShardRouter {
    private static final int CELEBRITY_WRITE_SHARDS = 8; // spread writes across 8 sub-shards
    private static final long CELEBRITY_THRESHOLD_FOLLOWERS = 1_000_000L;
    private final Random random = new Random();

    public String getWriteShardKey(long authorId, long followerCount) {
        if (followerCount >= CELEBRITY_THRESHOLD_FOLLOWERS) {
            // Spread celebrity writes across multiple sub-shards
            int suffix = random.nextInt(CELEBRITY_WRITE_SHARDS);
            return authorId + "_" + suffix;
        }
        // Regular user: single shard key
        return String.valueOf(authorId);
    }

    public String[] getReadShardKeys(long authorId, long followerCount) {
        if (followerCount >= CELEBRITY_THRESHOLD_FOLLOWERS) {
            // Fan-out on read: gather from all sub-shards
            String[] keys = new String[CELEBRITY_WRITE_SHARDS];
            for (int i = 0; i < CELEBRITY_WRITE_SHARDS; i++) {
                keys[i] = authorId + "_" + i;
            }
            return keys;
        }
        return new String[]{String.valueOf(authorId)};
    }
}
```

---

### What is Consistent Hashing, and why does it fit here?

**Consistent Hashing** — a hash ring algorithm where adding or removing a shard only remaps the keys that were assigned to that shard, not the entire key space. Without it, changing from 4 shards to 5 requires remapping ~80% of all keys, causing a massive rebalancing storm. With consistent hashing, only ~20% of keys move. In an interview: *"Consistent hashing is the standard choice for hash sharding because it minimizes key migration during rebalancing — only 1/N of keys move when you add a shard."*

### What is Scatter-Gather, and why does it fit here?

**Scatter-Gather** — when a query cannot be satisfied by a single shard (e.g., "find all orders over $100 across all users"), it is broadcast to every shard (scatter), each shard returns its partial results, and the application merges them (gather). In an interview: *"Cross-shard queries require scatter-gather, which adds latency proportional to the number of shards — this is why you choose a shard key that keeps common queries on a single shard."*

---

## 🏢 Real World — Where Companies Use This

- **YouTube (video_id hash sharding):** Video metadata (title, thumbnails, view counts) is sharded by `video_id` using consistent hashing across thousands of MySQL shards. `video_id` has high cardinality and uniform distribution — no celebrity problem because popular videos are cached at CDN, not re-queried every time.
- **Twitter / X (user_id range sharding):** User profile data was range-sharded by `user_id` in early days. Hit severe hotspot problems when large accounts (celebrities) generated fan-out writes. Migrated to a combination of hash sharding + timeline service fan-out to handle celebrity writes explicitly.
- **Instagram (media_id sharding):** Photo metadata sharded by `media_id`. Feed assembly aggregates across user → media mappings stored separately. Chose `media_id` over `user_id` because write distribution is proportional to upload rate, which is more uniform than follower-count distribution.
- **Uber (geo sharding for trip data):** Trip data is geo-sharded by city/region. A trip starting in Mumbai never touches the San Francisco shard. Enables data residency compliance and low-latency local reads. Surge pricing calculations are local to each geo shard.
- **Cassandra (virtual node consistent hashing):** Cassandra's architecture is built on consistent hashing with virtual nodes (vnodes — multiple tokens per physical node). Every write goes to the coordinator, which routes to the replica nodes responsible for that key's token range. Adding a node rebalances only the vnodes assigned to it.
- **DynamoDB (internal hash sharding):** DynamoDB partitions data by partition key (hash sharding). AWS automatically splits a partition when it exceeds 10 GB or 3,000 read capacity units. Developers choose the partition key; DynamoDB manages the shard topology invisibly.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Single DB machine is CPU/IO-bound and vertical scaling is exhausted | Data fits in one machine with room to grow (over-engineering risk) |
| Write throughput exceeds single-node capacity (> ~10K writes/sec) | Transactions span multiple entities with different shard keys (cross-shard transaction hell) |
| Data has natural cardinality in a shard key (user_id, order_id) | Queries require full-table scans or ad-hoc aggregations (scatter-gather penalty) |
| Geo-compliance requires data residency (GDPR, CCPA) | Team lacks operational experience with shard rebalancing and monitoring |
| Read replicas are already maxed and read distribution is uneven | All keys are monotonically increasing (e.g., auto-increment ID with range sharding) |

**The common mistake:** Choosing a low-cardinality shard key (e.g., `status` with values ACTIVE/INACTIVE, or `country` with 10 values). A shard key with only 10 unique values means at most 10 shards — you've sharded across fewer machines than needed, and each shard still carries uneven load proportional to the value distribution.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Linear write throughput scaling (2× shards ≈ 2× write capacity); data locality for shard-local queries; ability to comply with data residency laws (geo sharding); fault isolation (one shard failing affects only its key range). |
| **You lose** | Cross-shard queries become expensive scatter-gather operations; transactions spanning multiple shards require distributed transactions (2PC or saga pattern) with all their complexity; schema changes must be applied to every shard; shard rebalancing is operationally costly (data migration, coordinated cutover). |
| **Failure mode** | A bad shard key (low cardinality, monotonically increasing) causes shard hotspot where one machine absorbs 80%+ of all traffic — worse than no sharding at all because you now have 10× the operational complexity with 0× the performance gain. Celebrity user problem is a write-hotspot variant of this. |

---

## 🔬 Interview Q&As

### Q: "You are designing Instagram at 1 billion users. How do you shard the photos table?"

> Shard by `photo_id` using consistent hashing — not by `user_id`. Reason: sharding by `user_id` causes celebrity hotspots (Kylie Jenner's 100M followers generating fan-out reads). `photo_id` has higher cardinality and more uniform write distribution. Feed assembly is a separate service that joins user → photo mappings. For geo-compliance, add a geo shard layer on top: EU photos live only in EU shards. Cache photo metadata at CDN edge for popular content — preventing the shard from seeing repeated reads for viral photos.

### Q: "What is the celebrity user problem, and how do you solve it?"

> Celebrity problem: one shard key (e.g., user_id of a user with 100M followers) generates write traffic orders of magnitude higher than average. All writes for that user's feed fan-out go to one shard, which saturates. Solutions: (1) **Read path** — cache celebrity content aggressively; most followers read, not write. (2) **Write path** — use directory sharding to route the celebrity to a dedicated shard with higher capacity. (3) **Composite key** — write fan-out events with `(celebrity_id, random_suffix_0_to_7)` spread across 8 sub-shards, gather on read. Twitter does #3 for timeline fan-out. ⭐ **Tier 2 — design**

### Q: "When you add a new shard to your cluster, how do you rebalance without downtime?"

> With consistent hashing: adding a new shard moves only the key range between the new shard and its predecessor on the ring. Procedure: (1) Spin up new shard (cold). (2) Migrate key range in background using double-write: new writes go to both old and new shard. (3) Once migration catches up, flip reads to new shard. (4) Remove migrated keys from old shard. Total downtime: ~0 with double-write. Without consistent hashing (modulo hash), adding one shard changes N to N+1, remapping ~(N / N+1) of all keys — a full-table migration, unavoidable downtime. ⭐ **Tier 2 — operations**

### Q: "A user in your analytics dashboard runs a query: 'total orders by country last 7 days.' How does this work with sharding?"

> This is a scatter-gather query. Your application sends the query to all shards in parallel, each returns its partial count grouped by country, and the application aggregates (SUM) across results. Latency = slowest shard latency (not additive). Cost: every shard does a full scan. At scale this is prohibitively expensive on the OLTP (online transaction processing) shards. The production solution: maintain a separate analytics store (OLAP — online analytical processing — optimized for aggregations) that receives a feed from all shards via CDC (Change Data Capture — streaming database changes to downstream consumers). Route analytics queries to OLAP, never to OLTP shards. ⭐ **Tier 2 — architecture**

### Q: "What's the difference between partitioning and sharding?"

> Partitioning splits a table within a single database instance (e.g., PostgreSQL table partitioning by month). All partitions share the same database engine, storage, and connection pool. Sharding splits data across multiple independent database instances, each with its own engine, storage, and connection pool. Partitioning improves query performance via partition pruning (skipping irrelevant partitions). Sharding improves write throughput by distributing writes across machines. Use partitioning first; add sharding when a single machine is the bottleneck.

### Q: "Describe a scenario where directory sharding is the right choice over consistent hash sharding."

> Multi-tenant SaaS (Software as a Service) — a platform serving thousands of enterprise customers of wildly different sizes. Consistent hash sharding distributes tenants by hash — but a tenant with 10M users ends up on the same shard as ten tenants with 1K users each, overloading that shard. Directory sharding lets you say: "Tenant Walmart gets dedicated shard-42 (high-capacity machine); Tenant startup-X shares shard-7 with 50 other small tenants." No hashing algorithm can do this — it requires explicit routing. The trade-off: the directory itself must be highly available (replicated Redis or a metadata DB with replicas). ⭐ **Tier 2 — design**

### Q: "Why not just use replication instead of sharding for scale?"

> Replication (read replicas) scales reads by adding replicas, but all writes still go to one primary. A primary with 10 read replicas still has one write bottleneck. If your system is write-heavy (log ingestion, order creation, social feed updates), read replicas provide zero relief. Sharding is the only way to scale writes horizontally. Use replication within each shard (each shard has its own primary + replicas) for read scaling and fault tolerance. They compose, not compete.

### Q: "What happens if the Redis directory cache goes down in directory sharding?"

> The shard directory is a critical dependency. If Redis is down and the application falls through to the directory DB, every request hits the DB for a lookup — it can be overloaded quickly (thundering herd — many requests hitting an unprepared backend simultaneously). Mitigations: (1) Cache the directory in application memory (JVM-level cache, TTL 60 seconds) as a second-level fallback. (2) Run Redis in HA (High Availability) mode with replicas (Redis Sentinel or Redis Cluster). (3) Pre-warm the directory cache on startup. (4) Circuit-break the directory: if both Redis and DB are down, serve the last-known mapping from JVM cache (stale but functional). ⭐ **Tier 2 — failure mode**

---

## 🧾 TL;DR

> "Sharding horizontally partitions a database across independent machines using a shard key; hash sharding (consistent hashing ring) gives even distribution at the cost of scatter-gather for range queries; range sharding is simple but causes monotonic-key hotspots; directory sharding is the most flexible for multi-tenant systems but introduces a lookup dependency; the shard key is a one-time architectural choice — wrong choice equals hotspot, right choice equals linear write scale."

---

## 🔗 Related Concepts

- `06-databases-types-and-selection.md` — database type selection precedes sharding strategy choice
- `16-connection-pooling-db-performance.md` — each shard needs its own connection pool, pool sizing changes with sharding
- `34-cap-theorem-consistency-models.md` — cross-shard transactions force CAP trade-offs; sharded systems typically choose AP or CP per shard
- `36-two-phase-commit-vs-saga.md` — transactions spanning shards require 2PC or saga pattern
- `29-db-replication-failover.md` — each shard runs its own primary-replica pair; replication is per-shard

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **ByteByteGo — "Database Sharding"** (YouTube) | Visual walkthrough of consistent hashing ring with shard rebalancing animations | ~10 min |
| **Arpit Bhayani — "Sharding and Consistent Hashing"** (arpitbhayani.me) | Deep technical walkthrough including virtual nodes and rebalancing math | ~25 min read |
| **Amazon DynamoDB Developer Guide — Partition Key Design** (docs.aws.amazon.com) | Real DynamoDB partition key anti-patterns and best practices from the team that built it | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 38. Four sharding strategies (range, hash/consistent-hashing, directory, geo) with full Java implementation for each. Celebrity user problem with composite key write-fan-out mitigation. Cross-shard scatter-gather and analytics OLAP separation. Seven Q&As covering shard key selection, rebalancing, celebrity problem, directory SPOF, sharding vs replication, and cross-shard analytics. |
