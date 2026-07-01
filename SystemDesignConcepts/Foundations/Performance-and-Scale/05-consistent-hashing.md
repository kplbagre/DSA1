# Consistent Hashing

---

## 🎯 Why This Matters

When you distribute data across multiple servers (sharding), you need a rule for which server holds which data. Consistent hashing is that rule — and its defining property is that when you add or remove a server, only a fraction of keys need to move, not all of them. It appears in every interview involving distributed caches, sharded databases, CDN routing, or load balancing. A senior engineer is expected to know why naïve modular hashing breaks and exactly how the ring + virtual nodes fix it.

---

## 📖 What is Consistent Hashing?

**Full form:** Consistent Hashing / Ring-Based Partitioning

**Simple analogy:** Imagine a circular dartboard where servers are pinned around the ring. To find which server owns a key, hash the key, find where it lands on the ring, and walk clockwise until you hit a server. When a server is removed or added, only a few keys need to move (those near the change), not all keys.

**Core principle:** Instead of `key_hash % number_of_servers` (which remaps everything when servers change), consistent hashing places servers and keys on a ring. When a server is added/removed, only ~K/N keys need to move (where K = total keys, N = number of servers), not all K.

**Why it matters in system design:** Enables distributed caching (Redis), sharded databases, and CDNs without massive cache-miss storms when servers are added/removed.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Hash Ring** | conceptual circle of hash values 0–2³²; both servers and keys are placed on it | `hash("user:123") = 190°` on the ring |
| **Clockwise Walk** | to find a key's server, walk clockwise from the key's hash position until hitting a server node | key at 190°; nearest server clockwise is at 210° → key owned by that server |
| **Modular Hashing** | naïve approach: `hash(key) % N`; breaks when N changes because almost all keys remap | N=4 → N=5: ~80% of keys get a new server → cache stampede |
| **Virtual Nodes (vnodes)** | each physical server owns multiple positions on the ring; smooths uneven key distribution | Server A occupies ring positions 45°, 135°, 270° instead of just one |
| **K/N Key Migration** | when a server is added or removed, only ~1/N of total keys need to move | 3 servers → add 1 → only ~25% of keys migrate, not all of them |
| **MurmurHash** | fast, uniform non-cryptographic hash function used for ring placement | `MurmurHash("user:123")` produces a consistent ring position across all nodes |
| **TreeMap (implementation)** | sorted map of ring positions → server; `ceilingKey()` performs the clockwise walk in O(log N) | `TreeMap<Long, Server>` in Java: `treeMap.ceilingKey(hash(key))` |
| **Cache Stampede (rehash)** | when modular hashing remaps all keys at once, all cache misses hit the database simultaneously | adding 1 server with `% N` → 80% cache miss → DB overwhelmed |

---

## 🎨 Visual — System Topology: Consistent Hashing in Architecture

```
CLIENT REQUESTS with keys
    │
    │ GET user:123
    │ GET user:456
    │ GET user:789
    │
    ▼
┌───────────────────────────────┐
│ Consistent Hash Function      │
│ (hash key → ring position)    │
└──────────────┬────────────────┘
               │
               │ user:123 → 190° on ring
               │ user:456 → 240° on ring
               │ user:789 → 310° on ring
               │
               ▼
    ┌──────────────────────────┐
    │ Hash Ring (distributed)  │
    │                          │
    │        Server A (45°)    │ ← Virtual nodes spread load
    │       /    |    \        │
    │ (12°) (89°) (213°)       │
    │                          │
    │        Server B (150°)   │
    │       /    |    \        │
    │ (80°) (220°) (340°)      │
    │                          │
    │        Server C (270°)   │
    │       /    |    \        │
    │ (160°) (260°) (300°)     │
    └──────────────────────────┘
               │
   ┌───────────┼───────────┐
   ▼           ▼           ▼
Server A   Server B   Server C
(Cache)    (Cache)    (Cache)
   │           │           │
   └───────────┴───────────┘
        Data Stored

KEY INVARIANT:
   user:123 (190°) → walk clockwise → Server C (270°)
   user:456 (240°) → walk clockwise → Server C (270°)
   user:789 (310°) → walk clockwise → Server A (45°)
   
   When Server B removed: only keys between 150°-270° move to C
   No full remapping; ~⅓ of keys migrate, not all
```

---

## 🎨 Visual — Ring Structure with Node Add/Remove (Component Detail)

Imagine a **circular dartboard** — a ring — numbered 0 to 360 degrees. Each server gets assigned to a position on this ring by hashing its name or IP: Server A lands at 45°, Server B at 150°, Server C at 270°.

Now, when a request comes in for a key (say, "user:123"), you hash the key — it lands at 190° on the ring. The rule is: **walk clockwise until you hit the first server**. At 190°, you walk clockwise and hit Server C at 270°. That's who owns this key.

**What happens when Server B dies?** Its slice of the ring is uncovered. Keys that previously landed between 45° and 150° (Server B's territory) now walk clockwise past the empty space and land on Server C. Only those keys move — nothing else changes. Server A still owns its keys, Server C still owns its original keys.

**The problem without virtual nodes:** With only 3 real pins on a 360° board, the slices are uneven by chance. Server A might own 30° of the ring, Server B might own 180°, leaving Server C with the other 150°. This means Server B handles 6× more traffic than Server A — a **hot spot**. Virtual nodes fix this: each physical server plants **multiple pins** around the board (e.g., Server A at 12°, 89°, 213°, 331°). Now no single server dominates a large arc, and the load balances naturally.

**The key insight is:** Consistent hashing makes server addition/removal a local operation — only the keys in the departing server's clockwise arc need to move to its neighbour, roughly K/N keys out of K total, not all K.

---

## 🎨 Visual — Ring structure with node add/remove

```
MODULAR HASHING FAILURE (N=3 servers → N=4 servers)
════════════════════════════════════════════════════
key hash % 3:  user:A → 2 → Server C
               user:B → 0 → Server A
               user:C → 1 → Server B

key hash % 4:  user:A → 2 → Server C  (same)
               user:B → 0 → Server A  (same)
               user:C → 3 → Server D  ← MOVED
               user:D → 1 → Server B  ← MOVED (was Server C)

→ Adding ONE server remaps most keys. Mass migration. Cache miss storm.


CONSISTENT HASH RING (N=3 servers, K=keys)
═══════════════════════════════════════════

                    0° / 360°
                    Server A (hash = 45°)
            ↗                          ↘
   Server C                              Server B
   (hash = 270°)                     (hash = 150°)
            ↖                          ↗

Key "user:123" hashes to 190°
→ walk clockwise → first pin is Server C at 270° → belongs to Server C

KEY REMOVAL — Server B removed:
   Keys between 45° and 150° (Server B's arc) walk clockwise
   → now hit Server C at 270°
   → only ~K/3 keys moved. Everything else unchanged. ✅


VIRTUAL NODES — each server gets V pins
════════════════════════════════════════
Physical: Server A, Server B, Server C
Virtual (V=3 each):

   A1(30°)  B1(80°)  A2(130°)  C1(160°)  B2(220°)  A3(260°)  C2(300°)  B3(340°)  C3(20°)

Any key landing between B1(80°) and A2(130°) goes to A2 → Server A
Any key landing between A3(260°) and C2(300°) goes to C2 → Server C

Result: each server owns ~⅓ of the ring regardless of hash collisions.

KEY INVARIANT:
   Only the keys in the removed server's arc (~K/N out of K total) migrate.
   Virtual nodes ensure each server holds roughly equal arc length.
```

---

## ⚙️ How It Actually Works

### Building the Ring

**Steps in plain English:**

1. **Hash each server** onto the ring using a deterministic hash function — produces a position in [0, 2³²) mapped to the ring.
2. **Store sorted positions** in a `TreeMap<Integer, String>` (position → server name) — sorted so we can do clockwise lookup efficiently.
3. **Hash each key** to find its position on the ring.
4. **Walk clockwise** — find the first entry in the TreeMap with position ≥ key's position. If none (key is past the last server), wrap around to the first entry.

```java
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHashRing {

    // Sorted map: ring position → server name
    private final SortedMap<Integer, String> ring = new TreeMap<>();

    // Number of virtual nodes per physical server
    private static final int VIRTUAL_NODES = 150;

    // Step 1 — add a server: hash V virtual nodes onto the ring
    public void addServer(String serverName) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            int position = hash(serverName + "#vnode" + i);
            ring.put(position, serverName);
        }
    }

    // Remove a server: delete all its virtual nodes from the ring
    public void removeServer(String serverName) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            int position = hash(serverName + "#vnode" + i);
            ring.remove(position);
        }
    }

    // Steps 3+4 — route a key: hash → clockwise walk to first server
    public String getServer(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No servers in the ring");
        }
        int keyPosition = hash(key);
        // tailMap returns all entries with position >= keyPosition (clockwise)
        SortedMap<Integer, String> tailMap = ring.tailMap(keyPosition);
        // If key is past the last server, wrap to the first server (ring is circular)
        int serverPosition = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(serverPosition);
    }

    // Step 1/3 — hash function: maps a string to a ring position
    // MurmurHash is commonly used in production — here we approximate with Java's hashCode
    private int hash(String value) {
        // Math.abs to ensure non-negative position on the ring
        return Math.abs(value.hashCode());
    }
}
```

```java
// Usage
public class CacheRouter {

    private final ConsistentHashRing ring = new ConsistentHashRing();

    public CacheRouter() {
        ring.addServer("cache-node-1");
        ring.addServer("cache-node-2");
        ring.addServer("cache-node-3");
    }

    public String routeKey(String cacheKey) {
        // Deterministic: same key always routes to the same server
        return ring.getServer(cacheKey);
    }

    public void scaleOut(String newNodeName) {
        // Only ~K/N keys will reroute to the new node after this
        ring.addServer(newNodeName);
    }
}
```

---

### What is MurmurHash, and why does it fit here?

**MurmurHash** (named for its "multiply and rotate" internal operations) is a fast, non-cryptographic hash function designed specifically for hash-table and ring distribution use cases — it produces extremely uniform bit distributions with minimal collision clusters.

**Why not SHA-256 here?** SHA-256 is designed for security (preimage resistance) and is computationally expensive — ~200ns per call vs ~10ns for MurmurHash. For consistent hashing, you're hashing thousands of keys per second per node — security properties are irrelevant and speed matters.

**In an interview, if asked:** "MurmurHash is the standard hash function for consistent hashing rings because it's fast (~10ns), non-cryptographic, and produces near-perfect uniform distribution across the ring — which is the property we need so no single server gets a disproportionately large arc."

---

### What are Virtual Nodes, and why do they matter?

**Virtual nodes** (vnodes) are multiple ring positions assigned to a single physical server. Instead of one pin per server, each server drives V pins, spreading its "ownership" across V arcs of the ring.

**Why they matter:** With only 3 physical servers on a ring, the arc lengths are determined by random hash values — they might be 10°, 170°, 180°. The server with 170° handles 17× more traffic than the server with 10°. With V=150 virtual nodes per server, the central limit theorem kicks in: each server's total arc length converges to ~1/N of the ring regardless of the specific hash values.

**In an interview, if asked:** "Virtual nodes are V extra hash positions per physical server. Each server ends up owning V small, scattered arcs rather than one large arc — this averages out the load so no single server becomes a hot spot. When a server is removed, its V arcs each move to a different neighbour, spreading the migration load instead of dumping all keys onto one server."

---

## 🏢 Real World — Where Companies Use This

- **Redis Cluster** (sharded cache): Splits the ring into 16,384 hash slots. Consistent hashing determines which slot (and therefore which node) owns each key. When you add a Redis node, only a fraction of slots migrate.
- **Apache Cassandra** (distributed database): Each row's partition key is hashed onto a consistent hash ring. Cassandra's "vnodes" (virtual nodes) were introduced in version 1.2 — exactly this pattern — to fix uneven data distribution on node addition.
- **Akamai CDN** (edge server routing): Consistent hashing routes content requests to the nearest edge server that caches that content. If an edge server goes down, only its cached content needs to be served from the next closest server — not a global cache flush.
- **Amazon DynamoDB** (partitioned key-value store): Internally uses a consistent hash ring across partition servers. The "partition key" in DynamoDB is the key being hashed.
- **Netflix EVCache** (distributed cache layer): Consistent hashing lets Netflix scale its cache tier without invalidating the entire cache — each scale event only moves ~1/N of cached items.
- **Uber** (geospatial data routing): Consistent hashing is used in their internal data routing layer to determine which region or datacenter handles a city's data.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| You need to distribute load/data across N servers and servers join/leave frequently | You have a fixed, unchanging set of servers — standard modular hashing is simpler |
| Cache invalidation cost is high — you can't afford a mass miss on scale events | Keys are few and migration cost is negligible (small dataset) |
| You're building a distributed cache, sharded DB, or CDN routing layer | The data must be stored contiguously (range queries) — consistent hashing randomizes data placement |
| Horizontal scaling is a first-class requirement | You need geographic or latency-aware routing — consistent hashing is topology-blind |

**The common mistake:** Using too few virtual nodes. With V=1 (no vnodes), three servers can easily get 5%/80%/15% of the ring by bad luck. V=150 is the Redis Cluster default and is sufficient for most deployments.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Minimal key migration on server add/remove (only ~K/N keys move), even load distribution with virtual nodes, no centralized coordinator needed |
| **You lose** | Slightly more complex routing logic (TreeMap lookup vs `key % N`), virtual node memory overhead (V positions per server stored in-memory), data is not contiguous so range queries require scatter-gather across all nodes |
| **Failure mode** | Without virtual nodes, a single unlucky hash placement can give one server 80% of the ring. With virtual nodes, if the hash function is non-uniform (poor implementation), some arcs still cluster — always use a high-quality hash function like MurmurHash |

---

## 🔬 Interview Q&As

### Q: "Why is consistent hashing needed? What's wrong with key % N?"

> Modular hashing (`key % N`) maps keys to servers by remainder. Adding one server changes N to N+1, which changes the remainder for almost every key — most of the cache misses or needs migration. In a production cache with 100M entries, this is a catastrophic storm of DB requests. Consistent hashing ensures only ~K/N keys (roughly 1/N of total) move when you add one server — which is the theoretical minimum possible migration.

---

### Q: "Walk me through how a key is routed to a server on a consistent hash ring."

> You hash the key to get a position in [0, 2³²). You then do a clockwise walk on the ring — find the first server position that is ≥ the key's position. If the key is past the last server (end of the ring), wrap around to the first server. In code, a `TreeMap.tailMap(keyPos).firstKey()` does this in O(log N). The server at that position handles the key.

---

### Q: "What are virtual nodes and why do you need them?"

> Virtual nodes are multiple ring positions per physical server. Without them, the arc length each server owns is a random function of its hash value — by bad luck, one server might own 80% of the ring and handle 80% of requests. With V=150 vnodes, each server's total arc length converges to approximately 1/N by the law of large numbers, giving near-equal load distribution regardless of the specific hash values.

---

### Q: "What happens to keys when a server is removed?"

> Only the keys in that server's arc(s) need to migrate — they walk clockwise and land on the next server. With V virtual nodes, those arcs are scattered across the ring, so the migrating keys are spread across all remaining servers rather than piling on one neighbour. Total migration is ~K/N keys — the theoretical minimum for any sharding scheme.

---

### Q (Tier 2): "Consistent hashing doesn't support range queries well. How would you handle a use case that needs both sharding and range queries?"

> Consistent hashing randomizes key placement, so adjacent keys end up on different servers — a range query requires scatter-gathering across all N servers, which is expensive. Two approaches: (1) **Hash on a coarser granularity** — if the range query is always on user_id ranges, shard by `user_id / bucket_size` so adjacent user IDs land on the same shard. (2) **Use a different data store for range-heavy access patterns** — Cassandra uses consistent hashing for partition routing but stores rows within a partition in sorted order, enabling range queries within a partition. If cross-partition ranges are critical, a B-tree indexed RDBMS or a range-partitioned system (HBase, DynamoDB sort key) is the right tool.

---

### Q (Tier 2): "Two requests for the same key arrive at 2 different app servers simultaneously. How does consistent hashing guarantee they route to the same cache server?"

> The hash function is **deterministic and stateless** — given the same key string and the same ring configuration, both app servers compute the same hash value and perform the same clockwise walk, arriving at the same server. There's no coordination needed between app servers. The ring configuration (which servers exist and their virtual node positions) is kept in-memory on every app server and updated via a cluster management layer (e.g., ZooKeeper, Redis Cluster gossip protocol) when servers join or leave. As long as both app servers have the same view of the ring, they route identically.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Consistent hashing places both servers and keys on a circular ring using a hash function — a key's server is found by walking clockwise to the first server pin — so adding or removing a server only moves ~1/N of total keys, and virtual nodes ensure each server owns roughly equal arc lengths to prevent hot spots."

---

## 🔗 Related Concepts

- **`06-distributed-locking.md`** — Redis Cluster uses consistent hashing to shard keys; distributed locking runs on top of this sharded Redis
- **`03-caching.md`** — consistent hashing is the routing layer that decides which cache node holds a given key
- **`08-bloom-filter.md`** — Bloom filters are often used per-shard in consistent hash systems to check if a key exists before a full lookup

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Consistent Hashing"** — Arpit Bhayani (YouTube: "Arpit Bhayani consistent hashing") | Implementation details, mathematical proof of K/N migration, how Cassandra and Redis use it — deepest free explanation available | ~35 min |
| **"Consistent Hashing"** — ByteByteGo (YouTube: "ByteByteGo consistent hashing") | Visual animation of ring + vnode mechanics — good for cementing the mental model | ~8 min |
| **ashishps1/awesome-system-design-resources** | Best curated article on consistent hashing with trade-off tables | https://github.com/ashishps1/awesome-system-design-resources |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers: modular hashing failure, ring construction with TreeMap, clockwise key routing, virtual nodes, MurmurHash explanation, real-world use (Redis Cluster, Cassandra, Akamai). 6 Q&As (4 Tier 1 + 2 Tier 2). |
