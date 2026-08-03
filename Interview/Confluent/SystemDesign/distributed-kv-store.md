# Distributed Key-Value Store — Solution Walkthrough

> **Interview type:** Type 2 — Full System Design
> **Confirmed reports:** 2+ (Jul 2025 PracHub, Oct 2025 1Point3Acres)
> **Prompt variants:** "Design a Globally Distributed, Read-Optimized KV Store" / "Distributed Key-Value Store — focus on distributed architecture"
> **Bloom filter required:** ✅ Yes — absent-key Bloom filters on SSTables; Confluent probed this in Hack2Hire (May 2026)
> **Tableflow parallel:** Log-compacted Kafka topic = distributed KV store (this IS the answer to Section 11)

---

## 🎯 What Is This System?

**In plain English:** A distributed key-value store lets applications save and retrieve arbitrary values by key — like a global dictionary — across many machines in many regions, so no single machine is a bottleneck or single point of failure.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Amazon DynamoDB** | Fully managed, globally replicated KV/document store — the reference design for this question |
| **Apache Cassandra** | Open-source, wide-column store; consistent hashing ring + tunable consistency |
| **Redis Cluster** | In-memory KV; hash-slot sharding; read replicas for read scale |
| **RocksDB** | Embedded LSM-tree KV store used as the storage engine inside Kafka, Cassandra, and Flink |
| **etcd** | Strongly consistent KV store (Raft); used as Kubernetes' cluster state store |

**Core user journey:** A backend service calls `PUT /v1/keys/{key}` to store a value, and later calls `GET /v1/keys/{key}` to retrieve it, from any data center in the world, with low latency.

**Why it's hard to build at scale:** The CAP theorem (Consistency, Availability, Partition tolerance — you can guarantee at most two of the three during a network partition) forces an explicit trade-off: a globally distributed store must choose between returning possibly-stale data quickly (AP system, eventual consistency) or refusing to serve reads during a partition (CP system, strong consistency).

**Tableflow parallel:** A log-compacted Kafka topic (topic retention policy = `cleanup.policy=compact`) IS a distributed KV store — each partition key maps to the latest message value, tombstones (null-value messages) represent deletes, and compaction is the KV store's garbage collection pass. Tableflow's table metadata state is maintained exactly this way.

---

## 🚀 Section 1 — The One-Sentence Opener

> "Before I start, let me ask a few clarifying questions to make sure I'm solving the right version of this problem — 'distributed KV store' can mean anything from Redis Cluster to DynamoDB depending on the consistency model and read/write ratio."

Then pivot immediately to clarifying questions.

---

## 🔍 Section 2 — Clarifying Questions Script (Minutes 0–5)

**Q: "What is the expected read/write ratio? Is this read-heavy, write-heavy, or balanced?"**
- Why ask: this drives the entire optimization strategy — read-heavy shifts investment to read replicas, local caching, and Bloom filters; write-heavy shifts to write batching and LSM compaction tuning.
- If read-heavy (10:1 or more) → read replicas in every region, Redis caching tier, SSTable Bloom filters for absent-key reads, possibly Dynamo-style eventually consistent reads
- If balanced → standard replication factor, no special read optimization

**Q: "What are the latency requirements for reads and writes? Is there a P99 SLO?"**
- Why ask: P99 < 10ms forces in-memory hot path (Redis/Memcached tier); P99 < 100ms allows disk-backed LSM trees; no SLO allows HDD-backed storage
- If P99 < 10ms → reads must be served from memory; eviction policy matters; LSM compaction must be bounded
- If P99 < 100ms → SSD-backed RocksDB-style storage is fine

**Q: "Strict consistency required, or is eventual consistency acceptable?"**
- Why ask: this is the CAP choice; single most important architectural fork
- If strong consistency → Raft/Paxos quorum writes, read-your-own-writes, CP system (etcd model); availability drops during partition
- If eventual consistency → tunable quorum (Dynamo model: W + R > N for strong, W + R ≤ N for eventual), AP system, last-write-wins or vector-clock conflict resolution

**Q: "Single region or globally distributed (multi-region active-active)?"**
- Why ask: multi-region active-active forces cross-region replication and conflict resolution; single-region can use a simpler primary-replica model
- If multi-region → consistent hashing across regions, replication lag, conflict resolution strategy (LWW/vector clocks)
- If single region → consistent hashing within one data center, much simpler

**Q: "Do we need conditional writes — compare-and-swap or optimistic locking?"**
- Why ask: conditional writes require `If-Match`/ETag support on the API, which changes both the contract and the storage layer (need version/CAS column)
- If yes → `PUT /v1/keys/{key}` with `If-Match: {etag}` returning `412 Precondition Failed` on version mismatch — this is the Confluent API precision probe
- If no → simple last-write-wins

**Q: "What are we storing — byte blobs, or do values have a max size we should design for?"**
- Why ask: large values (> 1MB) require streaming reads; small values (< 4KB) are optimal for a KV path
- If large values → store value in S3/object store, store only a reference in the KV index
- If small values (our assumption) → store inline

---

## 📋 Section 3 — Requirements

**Assumptions (state these to the interviewer):** Read-heavy (10:1 read/write), P99 < 50ms reads, eventual consistency acceptable with optional strong read, globally distributed (3 regions: US-East, EU-West, AP-Southeast), conditional writes via ETag, values ≤ 10KB.

**Functional Requirements:**
- Clients can write a value by key: `PUT /v1/keys/{key}`
- Clients can read a value by key: `GET /v1/keys/{key}`
- Clients can delete a key: `DELETE /v1/keys/{key}`
- Clients can write conditionally (compare-and-swap): `PUT /v1/keys/{key}` with `If-Match` header
- Out of scope: range queries, secondary indexes, transaction across multiple keys, TTL per key (can add later)

**Non-Functional Requirements:**
- Scale: 10M DAU, 10K writes/sec peak, 100K reads/sec peak (10:1 ratio)
- Latency: P99 reads < 50ms (same region), P99 writes < 100ms
- Availability: 99.99% SLO — four 9s
- Consistency: eventual by default; optional `Consistency: strong` header on reads for read-your-own-writes
- Durability: no data loss after write acknowledgment; RF=3 per region

---

## 🗂️ Section 3.5 — Core Entities

| Entity | What it represents |
|---|---|
| **Key** | The lookup identifier — client-held, arbitrary string, used as hash ring token |
| **Value** | The blob stored against a key — immutable on write (each write creates a new version) |
| **Version** | The ETag/vector clock for a key-value pair — ephemeral per write, used for CAS |
| **Replica** | A copy of a key-value range owned by one node — append-only in LSM model |
| **Tombstone** | A delete marker for a key — append-only to SSTable, purged during compaction |

---

## 🔢 Section 4 — Scale Estimation

**Traffic:**
- DAU: 10M
- Writes/sec: 10M DAU × ~29 writes/day ÷ 86,400 ≈ 3,333 avg → **10K peak (3×)**
- Reads/sec: 10:1 read/write ratio → **100K reads/sec peak**
- Cross-region replication writes: 10K × 3 regions = 30K replication events/sec cluster-wide

**Storage:**
- Average value size: 2KB
- Writes/day: 10K × 86,400 = 864M write ops → 864M × 2KB = ~1.7TB new data/day
- With RF=3 → 5.1TB physical writes/day across cluster
- 90-day retention: ~150TB total (before compaction; compaction eliminates old versions)
- After compaction (assuming 10 versions/key on average, keeping 1): ~15TB live unique data

**Key conclusions:**
- At 100K reads/sec single-region, a single node (handles ~5-10K reads/sec) can't serve all reads → need 10-20 nodes, OR a caching layer that eliminates most disk reads
- At 10K writes/sec cluster-wide, Kafka-style ingestion (write to WAL immediately, flush to SSTable asynchronously) gives the write throughput without blocking on disk seek
- The 15TB live data across 3 regions (45TB total physical) fits comfortably on 30 nodes × 2TB SSD each

---

## 🔄 Section 5 — Requirements Variation Table

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10K users/day" | Single Postgres node, no consistent hashing, synchronous replication | No need for distribution; single node handles ~5K reads/sec easily |
| "1B users/day, 1M writes/sec" | Consistent hashing ring, 100+ nodes, partial replication, compaction tuning, cross-DC coordination | Each node owns a token range; compaction must run aggressively to keep disk I/O bounded |
| "Strict consistency required" | Raft quorum write (W=N/2+1), quorum read (R=N/2+1), etcd model | W+R > N guarantees read-your-own-writes at the cost of latency and availability during partition |
| "Eventual consistency OK" | W=1 (ack from coordinator), async replication to replicas, tunable R | Latency drops to single-node path; at 10:1 reads, R=1 from local replica is safe if client tolerates staleness |
| "P99 < 5ms reads" | Redis Cluster caching in front of RocksDB; all hot keys served from memory | Disk-backed LSM trees can't hit 5ms P99 under compaction; memory is the only path |
| "Multi-region active-active" | Per-region Dynamo ring + async cross-region replication + LWW conflict resolution | Cross-region synchronous replication latency (100-200ms) violates P99 < 50ms; must go async |
| "Strong read-your-own-writes" | Sticky reads (client routes to last-written node) or quorum read (R=2 of 3) | Even in eventual mode, the writing replica always has the latest value |

---

## ⭐ Section 6 — API Design

> **Reminder from standards:** API contract comes before HLD. These 4 endpoints define the system's external surface — the architecture serves them.

### 🧠 How to Derive These Endpoints

Every endpoint starts from a functional requirement: **FR → operation → resource → HTTP method → contract.**

**"Clients can write a value by key"** → create-or-replace → resource is `key` (namespace: `keys`) → `PUT /v1/keys/{key}`.
Why `PUT` and not `POST`? Because the client names the resource (the key string). `POST` would have the server generate the ID. Confluent will probe this immediately.
What do they get back? The new ETag (version) so the client can use it for subsequent conditional writes.

**"Clients can write conditionally"** → This constraint shapes the contract: the client sends `If-Match: {etag}` in the request header.
If the stored ETag doesn't match → `412 Precondition Failed` (not 409 Conflict — 409 means the resource state is conflicting; 412 means the precondition on a header was false).
If the key doesn't exist yet → `If-None-Match: *` creates it safely (returns 201 on first write, 412 if it unexpectedly exists).
This is the Confluent API precision probe — most candidates return 409; the correct code is 412.

**"Clients can read a value by key"** → retrieve → `GET /v1/keys/{key}`.
What matters: return the ETag as a response header so clients can cache and use for CAS. Add `Cache-Control` header for client-side caching guidance.
Optional strong-consistency read: request header `Consistency: strong` → coordinator does quorum read; default is eventual (local replica).

**"Clients can delete a key"** → remove → `DELETE /v1/keys/{key}`.
What happens in storage? A tombstone is written, not an immediate delete. The tombstone propagates to all replicas. Compaction purges the tombstone. Client sees `204 No Content` immediately after the tombstone is written to one replica.

### Core Endpoints

| Method | Path | Auth | Request Body / Headers | Response | Status Codes |
|---|---|---|---|---|---|
| `PUT` | `/v1/keys/{key}` | Bearer token | Body: `{"value": "..."}` / Optional header: `If-Match: {etag}` | `{"etag": "v3", "key": "...", "created_at": "..."}` | 201 Created (new), 200 OK (update), 412 Precondition Failed (CAS mismatch), 400, 429, 503 |
| `GET` | `/v1/keys/{key}` | Bearer token | Optional header: `Consistency: strong` | `{"key": "...", "value": "...", "etag": "v3", "updated_at": "..."}` | 200 OK, 404 Not Found, 429, 503 |
| `DELETE` | `/v1/keys/{key}` | Bearer token | Optional header: `If-Match: {etag}` | (empty body) | 204 No Content, 404, 412 (CAS mismatch on delete), 429 |
| `GET` | `/v1/keys/{key}/versions` | Bearer token | Query: `?limit=20&cursor=v3` | `{"versions": [...], "next_cursor": "v1"}` | 200 OK, 404, 400 |

### 🔍 Endpoint Stories

**`PUT /v1/keys/{key}`** — writes or updates a value. What makes it non-obvious: why `PUT` and not `POST`? Because the client names the resource (the key string is the client's choice, not server-generated). If the server generated keys, you'd use `POST /v1/keys`. The `If-Match` conditional write path returns **412 Precondition Failed** — not 409 Conflict. 412 means "a precondition in a request header evaluated to false"; 409 means "the request conflicts with the current state." Here the precondition (header) is false → 412. On the first-ever write to a key, the client can use `If-None-Match: *` to ensure they're not overwriting an existing key.

**`GET /v1/keys/{key}`** — retrieves the latest value. What's non-obvious: the response includes `etag` so the client can do a subsequent conditional write without a separate round-trip to fetch the version. The optional `Consistency: strong` header triggers a quorum read (R=2 of 3 replicas agree on the same version) at the cost of higher latency. Default behavior is local-replica read (eventual). Without this header, a client writing with one connection and reading immediately with another may get stale data — acknowledge this proactively.

**`DELETE /v1/keys/{key}`** — logically deletes a key by writing a tombstone to the storage layer. Returns 204 immediately after tombstone is committed to the coordinator's WAL — NOT after all replicas have received it. The key may still appear briefly on replicas that haven't received the tombstone. This is the consistency trade-off: if the client needs guaranteed-gone behavior, use `Consistency: strong` header or synchronous replication. 404 if the key never existed or was already deleted (tombstone was compacted away after full propagation).

**`GET /v1/keys/{key}/versions`** — version history for audit or rollback. Uses cursor pagination on `(updated_at DESC, version DESC)` — offset pagination breaks here because compaction deletes old versions concurrently with reads, shifting offsets unpredictably. Cursor is stable because it encodes an absolute position.

---

## 🏗️ Section 7 — High-Level Architecture

### 🎨 Visual — Three-Tier KV Store: Client → Coordinator → Replica Ring

```
GLOBALLY DISTRIBUTED KV STORE — SINGLE REGION VIEW
(Replicate entire ring to EU-West and AP-Southeast via async cross-region replication)

WRITE PATH                            READ PATH
[Client]                              [Client]
   |                                     |
   | PUT /v1/keys/{key}                  | GET /v1/keys/{key}
   v                                     v
[API Gateway / LB]                   [API Gateway / LB]
   |                                     |
   v                                     v
[Coordinator Service]                [Coordinator Service]
   |                                     |
   | 1. Hash key to token ring           | 1. Hash key → find preferred replica
   | 2. Write to W replicas              | 2. Default: route to closest replica
   | 3. Return ETag on W acks            |    Optional (Consistency:strong):
   |                                     |    quorum read from R replicas
   v                                     v
CONSISTENT HASHING RING (N=5 nodes, RF=3, W=2, R=1 default / R=2 strong)
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   Node A (tokens 0-72)          Node B (tokens 73-144)     │
│   ┌─────────────────┐           ┌─────────────────┐        │
│   │ Coordinator WAL │           │ Coordinator WAL │        │
│   │ Memtable (hot)  │           │ Memtable (hot)  │        │
│   │ SSTable L0,L1,..│           │ SSTable L0,L1,..│        │
│   │ Bloom Filter    │           │ Bloom Filter    │        │
│   └─────────────────┘           └─────────────────┘        │
│                                                             │
│   Node C (tokens 145-216)       Node D (tokens 217-288)    │
│   [same structure]              [same structure]           │
│                                                             │
│   Node E (tokens 289-360)                                  │
│   [same structure]                                         │
│                                                             │
│ KEY PROPERTY: each key is owned by the 3 consecutive nodes │
│ clockwise from its hash position — this is RF=3.           │
└─────────────────────────────────────────────────────────────┘
              |
              v
[Cross-Region Replication Stream]
  US-East → EU-West async (replication lag ~80ms)
  US-East → AP-Southeast async (replication lag ~150ms)

KEY INVARIANTS:
  W=2: write is durable as soon as 2 of 3 replicas acknowledge
  R=1: read is served from the closest replica (eventual)
  R=2: quorum read = read-your-own-writes (strong, but 2× latency)
  W + R > N (N=3): 2+2=4 > 3 → quorum mode guarantees consistency
  W + R ≤ N: 2+1=3 = N → eventual mode for read scale
```

**Data flow walkthrough:**

**Write path:**
1. Client → `PUT /v1/keys/{key}` with bearer token
2. API Gateway → Coordinator Service
3. Coordinator hashes `key` using consistent hashing → finds the 3 owner nodes (primary + 2 replicas)
4. Coordinator writes to the primary node's WAL immediately (< 5ms)
5. Coordinator fans out async to 2 replica nodes; waits for W=2 acknowledgments
6. Once 2 WAL writes confirmed: returns `201 Created` with new ETag to client
7. Each node: WAL → Memtable (in-memory) → SSTable flush when Memtable fills → compaction merges SSTables

**Read path (eventual, default):**
1. Client → `GET /v1/keys/{key}`
2. Coordinator hashes key → routes to closest replica (R=1)
3. Replica checks Bloom filter (fast absent-key check — see Deep Dive 2)
4. If Bloom filter says "maybe": read from Memtable first; if not there, binary search SSTable index
5. Return value + ETag in `< 5ms` (in-memory hit) or `< 50ms` (SSTable read with SSD)

**Read path (strong consistency, `Consistency: strong` header):**
1. Coordinator routes same request to 2 replicas simultaneously
2. Waits for both to respond; returns the value with the higher version number
3. P99 increases to ~100ms (two hops instead of one, plus coordination overhead)

```
═══════════════════════════════════════════════════
STAGE 1 — Single-Region (handles up to ~10K reads/sec)
═══════════════════════════════════════════════════

[Client]
   |
[Coordinator (1 node)]
   |
[3 Storage Nodes — consistent hashing, RF=3]
   |
[Postgres WAL for durability]

BREAKING POINT: Stage 1 breaks at ~10K reads/sec
  because a single coordinator becomes the serialization bottleneck.
  Observable symptom: coordinator CPU saturates at ~8K req/sec;
  P99 reads climb above 50ms SLO.
  Why Stage 2 is needed: coordinator must be horizontally scaled.

═══════════════════════════════════════════════════
STAGE 2 — Coordinator Pool + LSM Storage Nodes
           (handles up to ~100K reads/sec)
═══════════════════════════════════════════════════

[Client]
   |
[API Gateway — routes to any coordinator]
   |
[Coordinator Pool: 10 stateless nodes]
   |  (each coordinator hashes key and routes to correct storage node)
[Consistent Hashing Ring: 5 storage nodes × RF=3]
   |  (LSM tree storage: WAL → Memtable → SSTable; Bloom filter per SSTable)
[Cross-Region Replication]

From Section 4: 100K reads/sec peak. With 10 coordinators × 10K reads/sec/coordinator
= exactly at capacity. This is the right Stage 2 sizing.

BREAKING POINT: Stage 2 breaks at ~500K reads/sec
  because 5 storage nodes each handle ~100K reads/sec max (SSD IOPS ceiling).
  At 500K/5 = 100K reads/sec per node, SSD IOPS saturates for random reads.
  Observable symptom: storage node P99 read latency > 200ms; coordinator
  timeout errors spike.
  Why Stage 3 is needed: add a Redis caching tier in front of storage nodes.

═══════════════════════════════════════════════════
STAGE 3 — Redis Caching Tier + Expanded Ring
           (handles up to ~5M reads/sec — hot keys only)
═══════════════════════════════════════════════════

[Coordinator Pool]
   |
[Redis Cluster — hot key cache, LRU eviction]
   |            \
[miss]           [hit — return in < 1ms]
   |
[Storage Ring: 20 nodes × RF=3]
```

---

## 🔬 Section 8 — Core Component Deep Dives

### Deep Dive 1: Consistent Hashing — Key Distribution

**Why this is the most critical component:**
Without consistent hashing, adding or removing a storage node requires rehashing ALL keys (a full data migration). With consistent hashing, only the keys owned by the affected node's token range need to move. At 15TB of live data, full rehash = hours of downtime; consistent hashing = minutes of targeted migration.

### 🎨 Visual — Consistent Hashing Ring

```
Virtual hash ring (0 → 360):

               0
          Node A (60)
        /             \
 Node E (300)       Node B (120)
        \             /
         Node D (240)
              |
           Node C (180)

Key "user:1234" hashes to 95 → clockwise walk → lands on Node B (120)
Replicas: Node B, Node C, Node D (next 2 clockwise)

ADD new Node F between B and C (hash 150):
  Keys from 120-150 move from Node C to Node F
  All other keys stay put
  → Only 1/5 of Node C's keys migrate, nothing else changes

KEY INVARIANT:
  Adding/removing one node migrates only ~1/N of total data.
  At N=5, this is ~20% of the ring — not 100%.
```

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Consistent hashing with virtual nodes** | Node addition/removal migrates ~1/N data; virtual nodes give even distribution; industry standard (Dynamo, Cassandra) | More complex routing logic; coordinator must maintain ring state |
| **Range-based sharding (Bigtable model)** | Sequential scans are fast; natural geographic affinity; dynamic split/merge | Can't easily shard arbitrary string keys; hotspots on sequential workloads |
| **Modulo sharding (`key % N`)** | Trivially simple | Adding one node rehashes everything — catastrophic at scale |

**Decision: Consistent hashing with virtual nodes (150 virtual nodes per physical node)**

Because the system is globally distributed with dynamic node membership. Virtual nodes ensure even key distribution even if physical nodes have unequal hash positions.

---

### Deep Dive 2: Bloom Filters for Absent-Key Reads

**Why this is the most critical read-path optimization:**
In an LSM-tree (Log-Structured Merge-Tree — a write-optimized storage structure where new writes go to an in-memory buffer first, then flush to immutable sorted files called SSTables (Sorted String Tables)) storage engine, a `GET` for a non-existent key is the worst case: it must check the Memtable, then every SSTable level (L0, L1, L2...) before concluding the key doesn't exist. At 100K reads/sec with a 40% absent-key rate, this is 40K × (number of SSTable levels) disk reads per second — catastrophic for P99.

The Bloom filter (a probabilistic set-membership data structure that answers "definitely not in set" or "probably in set" — zero false negatives, tunable false positive rate) on each SSTable eliminates this: check the filter first; if it says the key is absent, skip the SSTable entirely.

**Sizing math at Stage 2 (100K reads/sec):**

```
Bloom filter per SSTable:
  n = keys per SSTable ≈ 100,000 keys/SSTable (typical 64MB SSTable at 640B avg record)
  p = 0.01 (1% false positive rate)
  m = -n × ln(p) / (ln 2)²
  m = -100,000 × ln(0.01) / 0.480
  m = -100,000 × (-4.605) / 0.480
  m ≈ 959,375 bits ≈ 120KB per SSTable Bloom filter

  With 10 SSTables in L0-L1 active at any time per node:
  Total Bloom memory per node: 10 × 120KB = 1.2MB

  All Bloom filters for 5 nodes: 6MB total — trivially fits in a coordinator's heap
```

**What happens on a false positive (key absent but Bloom says "maybe"):**
1. Coordinator routes to storage node
2. Storage node checks Bloom: positive (false alarm)
3. Reads SSTable index file from SSD: one disk seek (~0.1ms)
4. Does binary search in SSTable: ~3 disk reads (~0.3ms total)
5. Concludes key not found
6. Returns 404 to client

False positive wastes ~0.4ms and 4 disk I/Os. At 1% FPR and 40% absent-key rate → 0.4% of all reads hit this path. At 100K reads/sec: 400 wasted disk reads/sec → negligible.

**Why not a Counting Bloom filter (supports deletes):**
Counting Bloom filter (variant that stores a counter per bit instead of a single bit, allowing decrements on delete — enabling deletion without false negative introduction) uses 4× the memory (4 bits per counter vs 1 bit). In an LSM-tree, deletes are written as tombstones — the key is NOT actually removed from SSTables until compaction. So we don't need filter deletion; we just rebuild the Bloom filter during compaction when SSTables are merged and tombstones are purged.

**Implementation sketch:**

```java
// Per-SSTable Bloom filter — created at flush time, loaded at startup
public class SSTableBloomFilter {

    private final BloomFilter<String> filter;

    // Constructor: called when Memtable flushes to SSTable
    // estimatedKeyCount: number of keys in the Memtable being flushed
    // desiredFpr: false positive rate — 0.01 = 1%
    public SSTableBloomFilter(int estimatedKeyCount, double desiredFpr) {
        this.filter = BloomFilter.create(
            Funnels.stringFunnel(Charset.UTF_8),
            estimatedKeyCount,
            desiredFpr
        );
    }

    // Called for each key as Memtable entries are written to SSTable
    public void addKey(String key) {
        filter.put(key);
    }

    // Called at SSTable read time before any disk I/O
    // Returns false → key DEFINITELY not in this SSTable (skip it)
    // Returns true → key PROBABLY in this SSTable (do the disk read)
    public boolean mightContain(String key) {
        return filter.mightContain(key);
    }

    // Serialize Bloom filter to bytes for storage in SSTable footer
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        filter.writeTo(baos);
        return baos.toByteArray();
    }
}
```

---

### Deep Dive 3: Conditional Writes (Compare-and-Swap with ETag)

**Why this is the most critical correctness component:**
Without conditional writes, two clients writing the same key concurrently get last-write-wins — one write silently disappears. At Confluent's evaluation bar, this is the correctness probe: "What happens if two services try to update the same configuration key simultaneously?"

**ETag (Entity Tag — a version token the server assigns to each stored value, returned in response headers, used by clients to detect if the value has changed since they last read it) mechanics:**

```
Sequence (happy path):
  Client A → GET /v1/keys/config:timeout → Response: ETag: "v3"
  Client B → GET /v1/keys/config:timeout → Response: ETag: "v3"

  Client A → PUT /v1/keys/config:timeout, If-Match: "v3" → 200 OK, ETag: "v4"

  Client B → PUT /v1/keys/config:timeout, If-Match: "v3"
           → 412 Precondition Failed
           → body: { "error": { "code": "PRECONDITION_FAILED",
                                "message": "ETag v3 does not match current v4" } }
           → Client B must re-read (GET), get ETag "v4", then retry PUT with If-Match: "v4"

KEY INVARIANT:
  The storage layer treats a conditional PUT as an atomic CAS (Compare-And-Swap):
  if stored_version == if_match_etag → write new value, bump version → return 200
  if stored_version != if_match_etag → refuse write → return 412
  This must be atomic — coordinated with a single row-level lock or Raft log entry.
```

**Options for conflict resolution:**

| Option | Pros | Cons |
|---|---|---|
| **ETag / CAS (our choice)** | Client-controlled; no silent data loss; maps to HTTP standard | Requires client retry logic; higher write latency on contention |
| **Last-Write-Wins (LWW) with timestamp** | Simple; no retry needed; Cassandra default | Silent data loss if two writes arrive close together; NTP clock skew causes issues |
| **Vector clocks** | Captures causality correctly across concurrent writes | Complex to implement and explain; client must merge conflicts; DynamoDB abandoned it |

**Decision: ETag-based CAS**

Because the Confluent interviewer specifically probed API contract precision — LWW has no API analog, vector clocks are too complex to explain in a 60-minute interview. ETag-based CAS is the right balance of correctness, standard HTTP semantics, and explainability.

---

### Deep Dive 4: Compaction — The Tableflow Parallel

**Why this is the most Confluent-relevant deep dive:**
Compaction (the background process that merges smaller SSTable files into larger ones, purging outdated versions and tombstones) in an LSM-tree is architecturally identical to what Kafka's log compaction does. This is the strongest domain-signal you can send.

### 🎨 Visual — LSM Compaction = Kafka Log Compaction

```
KV STORE: LSM-TREE COMPACTION                KAFKA: LOG COMPACTION
────────────────────────────────────         ────────────────────────────────────

Memtable (in-memory):                        Active segment (in-memory buffer):
  key:user:1  → v3 "charlie"                   offset 100: user:1 → "charlie"
  key:user:2  → v2 "bob" (tombstone)           offset 99:  user:1 → "bob"
  key:user:3  → v1 "alice"                     offset 98:  user:2 → null (tombstone)

Flush to SSTable L0:                         Flush to log segment S1:
  [user:1, v3] [user:2, TOMBSTONE] [user:3]    [user:1: "charlie"] [user:2: null]

Compaction merges L0 + L1:                  Compaction merges old segments:
  Old versions of user:1 (v1, v2) purged       old offsets of user:1 purged
  Tombstone for user:2 purged                  tombstone for user:2 purged
  Only latest value of each key survives       only latest value of each key survives

                     ↕
         THEY ARE THE SAME ALGORITHM

KEY INVARIANT:
  KV store compaction = Kafka log compaction.
  Tombstone in KV store = null-value message in Kafka compacted topic.
  SSTable in KV store = log segment in Kafka.
  The output (one value per key, old versions gone) is identical.
  Tableflow's table metadata state is a Kafka compacted topic —
  it IS a distributed KV store under the hood.
```

---

## 🗄️ Section 9 — Data Model / SQL Schema

For a distributed KV store, the storage engine is NOT a relational DB — it's an LSM-tree backed by SSTables (implemented by RocksDB, a key-value storage library). However, the coordinator metadata (ring membership, version tracking for CAS) uses Postgres.

**Coordinator Metadata Schema (Postgres — single table, tiny):**

```sql
-- Ring membership and CAS version tracking
-- This is coordinator metadata, NOT the value store itself
CREATE TABLE key_versions (
    key             VARCHAR(2048)   NOT NULL,
    region          VARCHAR(32)     NOT NULL,
    current_version VARCHAR(64)     NOT NULL,
    -- wall-clock timestamp for LWW conflict resolution across regions
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    -- which storage node currently owns this key (coordinator routing cache)
    primary_node_id UUID            NOT NULL,
    PRIMARY KEY (key, region)
);

-- Index for routing: find primary node for a given key
CREATE INDEX idx_key_versions_key ON key_versions (key);

-- Index for replication catch-up: "give me everything updated since timestamp X"
CREATE INDEX idx_key_versions_updated_at ON key_versions (region, updated_at DESC);
```

**RocksDB Storage Layer (per-node, not SQL):**

Each storage node runs RocksDB. The logical schema is:

```
key    → { value: bytes, version: string, deleted: bool, updated_at: epoch_ms }
```

Physically stored as:
- **WAL** (Write-Ahead Log — an append-only file where every write is recorded before it touches memory, ensuring durability if the node crashes mid-write): durability guarantee on every write
- **Memtable**: in-memory sorted map, ~64MB per CF (Column Family — RocksDB's namespace for grouping related keys, like a table in RocksDB terms)
- **SSTables** (L0-L6): immutable sorted files on SSD; L0 written directly; L1-L6 populated by compaction
- **Bloom filter** per SSTable: 120KB at 1% FPR (see Deep Dive 2 sizing)

**Key Schema Decisions:**
- **CAS version is a string (not integer):** Allows opaque server-generated tokens; client can't predict/forge the next ETag. Using `UUID` or `SHA-256(value + timestamp)` prevents replay attacks.
- **`key_versions` in Postgres (not in RocksDB):** CAS operations need ACID semantics — the `UPDATE WHERE version = ?` check must be atomic. RocksDB provides a `CompareAndSwap` primitive but it's single-node; multi-region CAS needs coordinator-level serialization via Postgres row lock.
- **Region column in `key_versions`:** Different regions may have different versions temporarily (replication lag). The coordinator for a `Consistency: strong` read does a cross-region version check using this table.
- **`updated_at` index (region, updated_at DESC):** Serves the replication catch-up query pattern: new replica joining the cluster asks "what changed in US-East since T?" — uses this index for an efficient range scan.

---

## ⚠️ Section 10 — Trade-offs + Failure Modes

### Trade-off 1: Eventual Consistency vs Strong Consistency (Default Read Mode)

- **Chose:** Eventual consistency (R=1, local replica) as default; opt-in strong via `Consistency: strong` header
- **Gain:** 10× lower read latency (single replica hop vs quorum read); higher availability during regional network partition — local reads never block
- **Lose:** Read-your-own-writes guarantee requires explicit opt-in; a client writing then immediately reading may see stale data
- **Failure mode if wrong:**
  - [Technical]: If we default to strong reads (R=2): at 100K reads/sec, every read requires coordination across 2 nodes — doubles coordinator network load, pushes P99 from 30ms to ~80ms, violating the 50ms SLO. During a US-East internal network partition, all strong reads block until the partition heals.
  - [Streaming impact]: If Tableflow uses this KV store for table metadata (schema versions, snapshot IDs), blocking reads during a partition means no Iceberg table operations can proceed — Tableflow pipeline pauses entirely. With eventual reads, the pipeline continues using the last-known schema version.

### Trade-off 2: ETag-Based CAS vs Last-Write-Wins

- **Chose:** ETag-based CAS (compare-and-swap) for conditional writes
- **Gain:** Zero silent data loss on concurrent writes to the same key; client always knows when a conflict occurred (412 response); auditable via version history endpoint
- **Lose:** Higher write latency under contention (client must retry); coordinator must serialize CAS operations per key (reduces write throughput for hot keys)
- **Failure mode if wrong:**
  - [Technical]: If we used LWW (last-write-wins by timestamp): at 10K writes/sec with clock skew across nodes (NTP accuracy ±1ms), two writes within 1ms of each other result in one silently dropped. Over 1 day: 10K × 86,400 = 864M writes; at 0.1% collision rate = 864K silently lost values.
  - [Streaming impact]: If this KV store holds Kafka consumer group offsets, a silently overwritten offset causes a consumer to re-read already-processed messages — Iceberg table sink receives duplicate events; downstream analytics tables see double-counted metrics until manual deduplication.

### Trade-off 3: Bloom Filter FPR 1% vs 0.1%

- **Chose:** 1% false positive rate (FPR) per SSTable Bloom filter
- **Gain:** 120KB per SSTable (see Deep Dive 2 math) — all Bloom filters for all SSTables fit in coordinator heap memory; zero disk I/O for Bloom operations
- **Lose:** 1% of absent-key reads waste ~4 disk I/Os on a false positive hit; at 40K absent-key reads/sec (40% of 100K), 400 wasted disk reads/sec
- **Failure mode if wrong:**
  - [Technical]: If FPR = 10% (common mistake: not sizing Bloom correctly): 4,000 wasted disk reads/sec; SSD IOPS budget consumed by false positives; storage node P99 climbs above 50ms SLO as disk queue depth grows
  - [Streaming impact]: Tableflow reads schema registry entries (key = topic:version, value = Avro schema bytes) on every message deserialization. If 10% of reads hit false-positive disk reads, schema resolution latency increases from ~1ms to ~10ms — Tableflow's throughput drops proportionally as every consumer thread blocks on schema fetch

---

## 🌊 Section 11 — Confluent/Tableflow Angle

This is the highest-signal question in the Confluent bank for domain fit. The Tableflow parallel is not peripheral — it IS the design.

**Core parallel:**

> "A log-compacted Kafka topic is architecturally equivalent to a distributed KV store. The `cleanup.policy=compact` configuration tells the Kafka broker to run the same algorithm that LSM compaction runs on SSTables: keep only the latest message per key, purge old versions. A null-value message in a compacted topic is functionally identical to a tombstone in a KV store: it signals 'this key is deleted' and gets purged by the next compaction pass. Tableflow maintains its table metadata state (schema versions, Iceberg snapshot IDs, table-to-topic mappings) as a compacted topic — it is literally running a distributed KV store to manage the control plane."

**Specific Kafka features that map to this design:**

| This Design | Kafka Equivalent |
|---|---|
| LSM compaction (merge SSTables, purge tombstones) | `cleanup.policy=compact` — retain only latest value per key |
| Tombstone (delete marker) | Null-value message — signals key deletion to compaction |
| SSTable (immutable sorted file on disk) | Log segment (immutable append-only file, sealed after `segment.bytes`) |
| Memtable (in-memory write buffer before flush) | Kafka producer batch buffer + page cache before `fsync` |
| Consistent hashing ring | Kafka partition assignment (each partition = a token range; consumer group = coordinator pool) |
| Cross-region async replication | MirrorMaker 2 / Cluster Linking — replicates topic data across Confluent clusters |
| Version history (`GET /v1/keys/{key}/versions`) | Log offset history — Kafka retains full message history until retention expiry |

**Concrete Tableflow signal:**

> "When Tableflow needs to store which Iceberg snapshot ID corresponds to the current committed state of a table, it writes `(table_id → snapshot_id)` to a compacted topic. Consumers reading this topic always see the latest snapshot ID for any table. This is identical to `GET /v1/keys/{table_id}` on a KV store returning the latest snapshot ID. The compacted topic IS the distributed KV store — the schema just happens to be a Kafka topic instead of a REST API."

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why consistent hashing instead of simple modulo sharding?"**
> With modulo sharding (`hash(key) % N`), adding one node changes `N`, forcing every key's assigned node to change. At 15TB of live data, a full rehash takes hours of downtime. With consistent hashing, adding one node moves only 1/N of keys — at N=5, 20% of the ring migrates, and it migrates incrementally while the system stays online. Virtual nodes (150 per physical node) further smooth distribution so one node doesn't get 30% of the keyspace by hash luck.

**Q: "What is your consistency model?"**
> Tunable consistency via Dynamo-style N/R/W quorums. Default: N=3, W=2, R=1 → W+R=3, which equals N, so it's eventual. For `Consistency: strong` reads: R=2, W=2 → W+R=4 > N=3, guaranteeing we'll always read at least one replica that saw the latest write. The CAP corner by default is AP (Availability + Partition tolerance); with `Consistency: strong`, the request opts into CP temporarily.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "What happens during a network partition between US-East and EU-West? Walk me through both read and write behavior."**
> With W=2 and RF=3 per region, a write within US-East still succeeds (quorum within the region). EU-West reads are served locally from EU-West replicas — they may be stale by the replication lag (~80ms) but remain available. Writes that need EU-West coordination (e.g., cross-region CAS) will fail with a timeout. After partition heals, EU-West replicas catch up via the `updated_at` index scan: "give me all keys updated since T" — this is anti-entropy repair. The system is AP during partition, CP after heal.

**Q: "How do you avoid a hot partition — one key getting all the traffic?"**
> Three strategies. First, the coordinator detects hot keys (keys appearing in > 5% of requests in a sliding window) and replicates them to extra nodes, routing reads round-robin across all copies. Second, application-level: encourage clients to shard hot keys (`config:timeout:shard_0`, `:shard_1`, etc.) using key decorators. Third, Redis caching tier at Stage 3 absorbs hot-key reads entirely — the storage layer only sees cache misses, which are inherently distributed.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "You mentioned Kafka compacted topics are essentially a KV store. What does this mean for Tableflow's design — what could go wrong if the compacted topic diverges from the actual Iceberg table state?"**
> If the compacted topic (table metadata KV store) diverges from the Iceberg table state (e.g., a snapshot was committed to Iceberg but the corresponding `(table_id → snapshot_id)` write to the compacted topic failed), a consumer reading the topic gets stale snapshot ID — it reads from an old Iceberg snapshot, missing recently committed data. This is the classic dual-write problem: two systems (Iceberg table file + Kafka metadata topic) must be updated atomically, but they're not in the same transaction boundary. The fix is to write to the compacted topic BEFORE committing to Iceberg (optimistic reservation), then confirm by reading back — or use Kafka transactions to make the compacted topic update atomic with any downstream Tableflow state change.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1: Using `POST` instead of `PUT` for key writes** → **Why it's wrong:** `POST` implies server-generated ID; in a KV store, the client supplies the key name, which is the resource identifier. `PUT /v1/keys/{key}` with the key in the URL is correct. → **What to say instead:** "`PUT` because the client names the resource; I'd only use `POST` if the server generated the key, like an auto-increment ID."

- **Mistake 2: Returning 409 Conflict for a CAS mismatch** → **Why it's wrong:** 409 means the resource state conflicts with the request; 412 means a request header precondition evaluated to false. Here the `If-Match` header (a precondition) is false → 412 Precondition Failed. Confluent probes exactly this code. → **What to say instead:** "412 Precondition Failed — the `If-Match` header is the precondition, and it evaluated to false. 409 would be if I were trying to create a key that already exists."

- **Mistake 3: Proposing only LWW for conflict resolution without discussing CAS** → **Why it's wrong:** LWW silently drops writes in concurrent update scenarios. Confluent evaluates API precision — an interviewer who can't get a "412 when to use it" answer from you will mark you down. → **What to say instead:** "Default behavior is LWW for writes that don't carry `If-Match`. Clients who need correctness under concurrency use ETag-based CAS — GET to fetch ETag, then PUT with `If-Match`."

- **Mistake 4: Skipping Bloom filter discussion for absent-key reads** → **Why it's wrong:** In an LSM-tree system, absent-key reads are the worst case — they fan out across all SSTable levels before returning 404. Bloom filters are the standard solution, and Confluent has probed this in prior rounds (Hack2Hire, May 2026 — consumed the whole round). → **What to say instead:** "Each SSTable has a Bloom filter. Before any disk read, I check the filter. If it returns false for my key, I skip that SSTable entirely — zero disk I/Os for absent keys across 99% of cases."

- **Mistake 5: Saying 'add more Kafka partitions' to solve write scale** → **Why it's wrong:** This is a KV store, not a Kafka use case. More Kafka partitions don't help storage-layer write throughput. The actual solution is expanding the consistent hashing ring (more storage nodes, each owning a smaller token range). → **What to say instead:** "To scale writes I add storage nodes to the consistent hashing ring. Each node owns a smaller token range, so write load distributes. The coordinator pool scales independently."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How this design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `PUT /v1/keys/{key}` uses `PUT` (not `POST`) because client names the resource; `If-Match` conditional write returns **412 Precondition Failed** (not 409); `GET` returns ETag in response header; `Consistency: strong` header triggers quorum read — each choice is deliberate and provably correct |
| **Trade-off Defense** | ✅ | Three defended decisions: eventual-by-default (AP over CP for latency SLO), ETag CAS over LWW (correctness over throughput), 1% FPR Bloom filter (120KB memory cost vs 10% FPR with 10× waste disk I/Os) — each stated as chose/gain/lose/failure |
| **SQL / Data Modeling** | ✅ | `key_versions` DDL with `PRIMARY KEY (key, region)`, index on `(region, updated_at DESC)` for replication catch-up; explicit reasoning for RocksDB (not Postgres) as the value store; WAL + Memtable + SSTable structure explained |
| **Distributed Systems** | ✅ | Consistent hashing with virtual nodes (1/N migration on node change); N=3/W=2/R=1 tunable quorum; CAP analysis for network partition (AP during partition, catch-up via anti-entropy after heal); cross-region async replication with explicit replication lag numbers (80ms EU, 150ms AP) |
| **Pipeline Resilience** | ✅ | Write path: WAL-first guarantees durability before 200 OK; cross-region replication uses async stream not synchronous call (write latency unaffected by remote region); Bloom filter eliminates absent-key disk fan-out that would cascade into storage node overload |
| **Concurrency** | ✅ | ETag CAS handles concurrent writers atomically (coordinator serializes per-key via Postgres row lock); without `If-Match`, concurrent writes resolve via LWW with caller awareness; storage node Memtable is single-writer (WAL serializes appends), SSTables are immutable after flush — no write-write race on disk |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "A read-optimized globally distributed KV store is fundamentally a CAP choice made explicit: I design for AP by default (R=1 local replica, W=2 quorum) with opt-in CP via a `Consistency: strong` request header that triggers quorum reads. The API contract's most important precision point is `PUT /v1/keys/{key}` with `If-Match` header returning 412 (not 409) on a CAS mismatch — the `If-Match` header is the precondition, and a false precondition is 412, not a resource state conflict. The read-optimization mechanism is a Bloom filter per SSTable in the LSM-tree storage engine: at 1% FPR and 120KB per filter, absent-key reads are eliminated without any disk I/O 99% of the time, which is the difference between P99 < 50ms and P99 > 200ms at 100K reads/sec. The Confluent/Tableflow angle is the centerpiece of this answer: log-compacted Kafka topics are architecturally the same as LSM-tree compaction — tombstone = null-value message, SSTable compaction = `cleanup.policy=compact`, and Tableflow's table metadata control plane is literally implemented as a compacted Kafka topic that functions as a distributed KV store. The trade-off I'd defend first is ETag CAS over last-write-wins: at 10K writes/sec with 1ms NTP clock skew, LWW silently drops writes at a rate that Tableflow's schema registry would notice as duplicate or missing schema versions in the Iceberg table sink."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Type 2 Full System Design, 15 sections per `solution-notes-standards.md`. Covers: consistent hashing with virtual nodes, N/R/W tunable quorum, ETag CAS with 412 status, Bloom filter sizing math for LSM absent-key reads, LSM compaction = Kafka log compaction parallel (Section 11 centerpiece). |
