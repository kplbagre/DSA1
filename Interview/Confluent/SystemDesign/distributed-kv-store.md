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
- Cross-region replication writes: each write is applied locally and shipped to the **other 2** regions → 10K × 2 = **20K replication events/sec** cluster-wide

**Storage:**
- Average value size: 2KB
- **Use the AVERAGE rate for daily volume, not the peak — you cannot run at peak 24/7.** Peak sizes the *fleet*; average sizes the *disk*. Mixing them inflates storage by 3×, and that error cascades into node count, cache sizing, and every downstream conclusion.
- Writes/day: 3,333/sec × 86,400 = **288M write ops** → 288M × 2KB = **576GB new logical data/day**
- With RF=3 → ~1.7TB physical writes/day across a region
- 90-day retention: 576GB × 90 = **~51.8TB logical** (before compaction; compaction eliminates old versions)
- After compaction (assuming 10 versions/key on average, keeping 1): **~5.2TB live unique data**

**Storage — sizing the ring (do this math out loud; it decides the node count):**
- Live logical data after compaction: **5.2TB**
- Each region holds a full copy (multi-region active-active, not sharded across regions) → **5.2TB logical per region**
- RF=3 is *within* a region (each key owned by 3 consecutive ring nodes) → **15.5TB physical per region**
- At 2TB SSD/node: 15.5TB ÷ 2TB = **8-node floor** per region
- LSM compaction needs free space to merge SSTables — at ~20% headroom (leveled compaction): 15.5TB ÷ 0.8 = 19.4TB provisioned → **~10 nodes**, round up for N+1 to **12 nodes per region**
- Global: 12 nodes × 3 regions = **36 storage nodes**, ~46TB physical

**Key conclusions:**
- **Capacity and read throughput are CO-BINDING here — size from both and say both.** Disk says 12 nodes/region. Reads say 100K/sec ÷ ~10K reads/sec/node = 10 nodes/region. The two inputs land within two nodes of each other, so neither one dominates: a 12-node ring delivers 12 × ~10K = **120K reads/sec against a 100K/sec peak — 1.2× headroom, not 3×.** A candidate who sizes only from reads lands at 10 nodes and has zero capacity slack; a candidate who sizes only from disk lands at 8 and is under the read requirement. State both derivations and take the max.
- **Therefore the Redis tier in Stage 3 is justified three ways, and you need all three.** (1) P99 latency — a cache hit is <1ms against a median SSTable read of ~0.5–1ms that tails into tens of ms under compaction and queueing. (2) Hot-key absorption — a cache is the only thing that unpins a key from its 3 owner replicas. (3) **Read headroom** — with only 1.2× slack on the ring, the cache is the cheapest way to buy read capacity; growing the ring for reads alone means buying disk you do not need. Naming all three (and *not* claiming the ring has spare throughput) is the senior signal.
- At 10K writes/sec cluster-wide, WAL-first ingestion (append to WAL immediately, flush to SSTable asynchronously) gives write throughput without blocking on disk seek — 10K × RF=3 ÷ 12 nodes = **~2.5K writes/sec/node**, still comfortable for RocksDB.

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
What happens in storage? A tombstone is written, not an immediate delete. The tombstone propagates to all replicas. Compaction purges the tombstone. A delete is just a write of a tombstone value, so it obeys **the same W=2 rule as `PUT`**: the client sees `204 No Content` once 2 of the 3 owner replicas have the tombstone in their RocksDB WAL — not after 1 replica, and not after all 3.

### Core Endpoints

| Method | Path | Auth | Request Body / Headers | Response | Status Codes |
|---|---|---|---|---|---|
| `PUT` | `/v1/keys/{key}` | Bearer token | Body: `{"value": "..."}` / Optional header: `If-Match: {etag}` | `{"etag": "v3", "key": "...", "created_at": "..."}` | 201 Created (new), 200 OK (update), 412 Precondition Failed (CAS mismatch), 400, 429, 503 |
| `GET` | `/v1/keys/{key}` | Bearer token | Optional header: `Consistency: strong` | `{"key": "...", "value": "...", "etag": "v3", "updated_at": "..."}` | 200 OK, 404 Not Found, 429, 503 |
| `DELETE` | `/v1/keys/{key}` | Bearer token | Optional header: `If-Match: {etag}` | (empty body) | 204 No Content, 404, 412 (CAS mismatch on delete), 429 |
| `GET` | `/v1/keys/{key}/versions` | Bearer token | Query: `?limit=20&cursor=v3` | `{"versions": [...], "next_cursor": "v1"}` | 200 OK, 404, 400 |

> ⚠️ **Be honest about where `/versions` is served from — neither storage layer retains history.** Compaction keeps exactly **1 version per key** (that `÷10` is the basis of the 5.2TB live-data figure in Section 4), and the coordinator metadata holds a single `current_version` per key. So the KV store itself physically cannot answer "give me the last 20 versions." Two defensible answers: **(a) drop the endpoint** and say version history is out of scope, or **(b) serve it from a separate append-only audit topic** — every accepted write also produces a `(key, version, actor, timestamp)` record to a *non-compacted* Kafka topic with its own retention, and `/versions` reads that topic (the natural Confluent answer, and it keeps history off the hot path). What you must NOT do is claim the ring serves it: retaining N versions per key inside the SSTables invalidates the `÷10` compaction assumption and multiplies live data by roughly N.

### 🔍 Endpoint Stories

**`PUT /v1/keys/{key}`** — writes or updates a value. What makes it non-obvious: why `PUT` and not `POST`? Because the client names the resource (the key string is the client's choice, not server-generated). If the server generated keys, you'd use `POST /v1/keys`. The `If-Match` conditional write path returns **412 Precondition Failed** — not 409 Conflict. 412 means "a precondition in a request header evaluated to false"; 409 means "the request conflicts with the current state." Here the precondition (header) is false → 412. On the first-ever write to a key, the client can use `If-None-Match: *` to ensure they're not overwriting an existing key.

**`GET /v1/keys/{key}`** — retrieves the latest value. What's non-obvious: the response includes `etag` so the client can do a subsequent conditional write without a separate round-trip to fetch the version. The optional `Consistency: strong` header triggers a quorum read (R=2 of 3 replicas agree on the same version) at the cost of higher latency. Default behavior is local-replica read (eventual). Without this header, a client writing with one connection and reading immediately with another may get stale data — acknowledge this proactively.

**`DELETE /v1/keys/{key}`** — logically deletes a key by writing a tombstone to the storage layer. Returns 204 once **W=2 of the 3 owner replicas have the tombstone in their own RocksDB WAL** — the coordinator is stateless and has no WAL of its own, so there is nothing there to commit to. It does NOT wait for the third replica, and it does not wait for cross-region propagation. The key may still appear briefly on the third replica and in other regions. This is the consistency trade-off: if the client needs guaranteed-gone behavior, use `Consistency: strong` on the subsequent read (R=2 quorum, which will see the tombstone). 404 if the key never existed or was already deleted (tombstone was compacted away after full propagation).

**`GET /v1/keys/{key}/versions`** — version history for audit or rollback, served from the **append-only audit topic**, not from the ring (see the callout above the endpoint table — the ring keeps one version per key). Uses cursor pagination on `(updated_at DESC, version DESC)` — offset pagination breaks here because the audit topic's own retention expires old records concurrently with reads, shifting offsets unpredictably. Cursor is stable because it encodes an absolute position.

---

## 🏗️ Section 7 — High-Level Architecture

> **Delivery note — build it up, don't draw the finished thing.** The diagram below is the Stage 2 target design, but narrate the stage blocks that follow it in order: one coordinator and 3 nodes first, then let the Section 4 numbers — 100K reads/sec peak, 10K writes/sec, 5.2TB live data — force the coordinator pool, then the cache tier. Adding a Redis tier before you have named the number that makes the disk-sized ring insufficient is the single easiest way to lose this round.

### 🎨 Visual — Three-Tier KV Store: Client → Coordinator → Replica Ring

```
GLOBALLY DISTRIBUTED KV STORE — SINGLE REGION VIEW
(Ring is replicated to EU-West and AP-Southeast via async
 cross-region replication — one full copy per region)

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
CONSISTENT HASHING RING — drawn with N=5 for legibility; the real ring is
12 nodes per region (sized by BOTH disk 15.5TB ÷ 2TB + compaction headroom
AND reads 100K/sec ÷ ~10K per node — the two land two nodes apart).
RF=3, W=2, R=1 default / R=2 strong. The mechanism is identical at any N.
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   Node A (tokens 0-72)          Node B (tokens 73-144)     │
│   ┌─────────────────┐           ┌─────────────────┐        │
│   │ RocksDB WAL     │           │ RocksDB WAL     │        │
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
6. Once 2 WAL writes confirmed: returns the new ETag — **`201 Created` only if the key did not previously exist, `200 OK` if this replaced an existing value** (matching the API table above; the R=2 version read that CAS already performs is what tells the coordinator which one it is)
7. Each node: WAL → Memtable (in-memory) → SSTable flush when Memtable fills → compaction merges SSTables

**Read path (eventual, default):**
1. Client → `GET /v1/keys/{key}`
2. Coordinator hashes key → routes to closest replica (R=1)
3. Replica checks Bloom filter (fast absent-key check — see Deep Dive 2)
4. If Bloom filter says "maybe": read from Memtable first; if not there, binary search SSTable index
5. Return value + ETag in `< 1ms` (Memtable hit) or **`~0.5–1ms` median for an SSTable read on NVMe** (one index seek ~0.1ms + ~3 data reads ~0.3ms, per Deep Dive 2). The **P99 tail** is what reaches tens of ms — not the disk itself, but compaction stalls, L0 file pile-up, and request queueing behind them. Say it this way round: the median SSTable read is already fast; the cache in Stage 3 exists to cut the *tail*, not the median.

**Read path (strong consistency, `Consistency: strong` header):**
1. Coordinator routes same request to 2 replicas simultaneously
2. Waits for both to respond; returns the value with the higher version number
3. P99 increases to ~100ms (two hops instead of one, plus coordination overhead)

```
═══════════════════════════════════════════════════
STAGE 1 — Single-Region (handles up to ~10K reads/sec)
═══════════════════════════════════════════════════

 ┌──────────────┐
 │    Client    │
 └──────┬───────┘
        │ PUT / GET /v1/keys/{key}
        ▼
 ┌──────────────────────────────┐      ┌─────────────────────────┐
 │  Coordinator (1 node)        │─────▶│ Postgres: ring_members  │
 │──────────────────────────────│ ring │ (ring membership ONLY:  │
 │  hash(key) → owner nodes     │ only │  ~tens of rows, off     │
 │  CAS via R=2/W=2 on owners   │      │  every data path)       │
 └──────┬───────────────────────┘      └─────────────────────────┘
        │ ⚠ SPOF: holds ring state
        ▼
 ┌──────────────────────────────────────────────────────────┐
 │   Consistent Hashing Ring — 3 storage nodes, RF=3         │
 │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
 │  │  Node A     │  │  Node B     │  │  Node C     │        │
 │  │─────────────│  │─────────────│  │─────────────│        │
 │  │ RocksDB:    │  │ RocksDB:    │  │ RocksDB:    │        │
 │  │  WAL        │  │  WAL        │  │  WAL        │  ← each │
 │  │  Memtable   │  │  Memtable   │  │  Memtable   │    node │
 │  │  SSTables   │  │  SSTables   │  │  SSTables   │    owns │
 │  │  Bloom/SST  │  │  Bloom/SST  │  │  Bloom/SST  │    its  │
 │  └─────────────┘  └─────────────┘  └─────────────┘   own   │
 │   At RF=3 and N=3, every node holds every key.      durab. │
 └──────────────────────────────────────────────────────────┘

NOTE the durability boundary — a common mix-up: each storage node has
its OWN WAL inside RocksDB, which is what makes a write durable. The
coordinator is stateless and has no WAL at all.
Postgres is NOT in any data path. It holds ring membership only — tens
of rows, read at startup and on membership change. Conditional writes
(CAS) are executed against the key's OWN owner replicas via the R=2/W=2
quorum, because the version lives in the RocksDB record (Section 9).
Putting a per-key row in Postgres would put a single machine on a
10K-writes/sec path — see the Section 9 callout on why that is fatal.

BREAKING POINT: Stage 1 breaks at ~8K reads/sec — under one tenth of the
  100K reads/sec peak from Section 4 — for two reasons:
   (a) The single coordinator is the serialization point for every request.
       Its CPU saturates at ~8K req/sec doing hash lookups plus W=2 fan-out
       and quorum collection, so P99 reads breach the 50ms SLO.
   (b) It is also a SPOF that holds the ring state. If it dies, no client
       can locate any key — the storage nodes are healthy and the data is
       intact, but the cluster is 100% unavailable.
  TWO DIFFERENT CONSTANTS — do not let them blur together:
    coordinator ≈ 8K req/sec   (CPU + network: hash, fan-out, quorum
                                collect; no disk in its path)
    storage node ≈ 10K logical reads/sec (see the Stage 2 derivation —
                                an NVMe-bound number, not a CPU one)
  Observable symptom: coordinator CPU pinned at 100% while all 3 storage
  nodes sit around 30% utilization — at ~8K reads/sec spread over 3 nodes
  with R=1 that is ~2.7K/node against a ~10K/node capacity, so roughly
  a third. The giveaway is the SHAPE (coordinator pegged, storage nodes
  with room to spare), not any one node being idle.
  Why Stage 2 is needed: coordinator must be stateless and horizontally
  scaled, with ring state held outside any single process.

═══════════════════════════════════════════════════
STAGE 2 — Coordinator Pool + LSM Storage Nodes
           (handles up to ~100K reads/sec — see the
            reconciliation note under the diagram)
═══════════════════════════════════════════════════

 ┌──────────────┐
 │    Client    │
 └──────┬───────┘
        ▼
 ┌──────────────────────────────────────────────────────────┐
 │  API Gateway — any coordinator can serve any key          │
 └──────┬───────────────────────────────────────────────────┘
        ├──────────────┬──────────────┬─────── ··· ─────┐
        ▼              ▼              ▼                 ▼
 ┌───────────┐  ┌───────────┐  ┌───────────┐     ┌───────────┐
 │ Coord 1   │  │ Coord 2   │  │ Coord 3   │ ··· │ Coord 14  │
 │ stateless │  │ stateless │  │ stateless │     │ stateless │
 └─────┬─────┘  └─────┬─────┘  └─────┬─────┘     └─────┬─────┘
       │ hash(key) → owners; W=2 writes / R=1 default read
       └──────────────┴──────┬───────┴─────── ··· ─────┘
                             ▼
 ┌──────────────────────────────────────────────────────────┐
 │  Consistent Hashing Ring — 12 storage nodes × RF=3        │
 │  (disk 15.5TB/region ÷ 2TB + headroom = 12; reads         │
 │   100K/sec ÷ ~10K/node = 10 → take the max, 12)           │
 │  ┌────┐┌────┐┌────┐┌────┐┌────┐┌────┐        ┌────┐      │
 │  │ N1 ││ N2 ││ N3 ││ N4 ││ N5 ││ N6 │ ······ │N12 │      │
 │  └────┘└────┘└────┘└────┘└────┘└────┘        └────┘      │
 │  each: RocksDB WAL → Memtable → SSTable + Bloom/SSTable   │
 │  150 virtual nodes each, so token ranges stay even        │
 └──────────────────────────┬───────────────────────────────┘
                            │ async, per-key, no 2PC
        ┌───────────────────┴───────────────────┐
        ▼                                       ▼
 ┌──────────────────┐                  ┌──────────────────┐
 │ EU-West ring     │                  │ AP-Southeast ring│
 │ (12 nodes)       │                  │ (12 nodes)       │
 │ lag ~80ms        │                  │ lag ~150ms       │
 └──────────────────┘                  └──────────────────┘

SIZING — TWO INPUTS, TAKE THE MAX (Section 4). They are close here,
which is exactly why you must show both:
  DISK:  5.2TB logical × RF=3 = 15.5TB/region ÷ 2TB SSD = 8-node floor,
         + ~20% LSM compaction headroom (19.4TB) → ~10, round to 12.
  READS: 100K reads/sec ÷ ~10K reads/sec/node = 10 nodes.
  → 12 nodes per region. Disk wins by two nodes; it does not win by 3×.

  WHERE ~10K reads/sec/node COMES FROM (state it, do not assert it):
  a datacentre NVMe SSD sustains roughly 100K random IOPS. One LOGICAL
  LSM read costs ~5-10 PHYSICAL reads even after Bloom filtering — a
  Memtable miss, then per surviving SSTable an index block plus a data
  block, plus the occasional false positive. 100K IOPS ÷ ~10 physical
  reads per logical read ≈ 10K logical reads/sec/node. Different
  hardware moves this number, and it moves the whole ring with it.

  This is a DIFFERENT constant from the coordinator's ~8K req/sec.
  The coordinator does no disk I/O at all; it is CPU- and network-bound
  on hashing, fan-out and quorum collection. Same order of magnitude,
  completely different mechanism — never quote one for the other.

COORDINATOR COUNT: 100K reads/sec ÷ ~8K req/sec each = 12.5 → 13, and
  13 sized exactly at peak leaves zero redundancy. Against a 99.99% SLO
  you provision N+1, so **14 coordinators**. (An earlier version of this
  file said 10, which quietly borrowed the storage node's 10K constant.)

RECONCILING THE HEADER WITH THE BREAKING POINT — two different limits:
  ~100K reads/sec is the COORDINATOR POOL's ceiling as provisioned
  (14 × 8K = 112K). It is a fleet-size number and you fix it with more
  coordinators, which are stateless and cheap.
  ~120K reads/sec is the RING's ceiling (12 × 10K). It is a hardware
  number and you fix it with more nodes or a cache.
  So Stage 2 as drawn tops out around 100-120K reads/sec, with only
  ~1.2× slack over the stated peak. Say the two numbers separately;
  a single "Stage 2 handles X" hides which knob you would turn.

BREAKING POINT: Stage 2 breaks at ~120K reads/sec, for two causes that
  are genuinely different in kind — one is aggregate capacity, the other
  is distribution. Do not lump them:
   (a) Aggregate IOPS — a real capacity wall. At 120K/12 = 10K reads/sec
       per node, an LSM read that misses the Memtable touches several
       SSTable levels, so each logical read becomes ~5-10 physical random
       reads. NVMe IOPS saturates and the read path stops being
       latency-bound and becomes queue-bound — P99 detaches from P50.
       More nodes DO fix this one.
   (b) Hot-key skew — a distribution problem that more nodes do NOT fix.
       Access is Zipfian: a single hot key is pinned by consistent
       hashing to exactly 3 nodes regardless of ring width. Do the
       arithmetic rather than asserting it (Section 12 has the full
       version): a key's 3 owners supply 3 × 10K = 30K reads/sec, so a
       key must pull 30K reads/sec — 30% of the entire 100K peak — to
       saturate them. Zipf(s≈1) over billions of keys puts the TOP key
       at ~4.5% of traffic, i.e. ~4.5K reads/sec, comfortably under half
       of one owner's capacity. So under the Zipfian assumption, (a)
       arrives FIRST. Skew becomes the binding ceiling only in the
       extreme single-key case — one global config key read by every
       service on every request — and that is precisely the workload a
       KV store like this attracts, which is why it still gets a cache.
  Observable symptom: for (a), P99 AND mean both climbing with per-node
  IOPS uniformly pegged across all 12. For (b), P99 climbing while MEAN
  stays healthy and per-node IOPS shows 3 nodes hot and 9 cold. The two
  dashboards look completely different — that is how you tell them apart.
  Why Stage 3 is needed: buy read headroom (the ring only has 1.2×),
  absorb an extreme hot key that no ring width can spread, and cut the
  P99 TAIL for repeat reads — tens of ms under compaction stalls and
  queueing — down to <1ms. Note it is the tail, not the ~0.5-1ms median
  SSTable read, that the cache is buying you.

═══════════════════════════════════════════════════
STAGE 3 — Redis Caching Tier
  handles ~1.5M reads/sec at an 80% hit rate
  (~5M only at a >=94% hit rate — never quote 5M bare)
  ring stays at 12 for storage; ~600K/sec at 12 nodes,
  ~1.5M/sec once the ring is widened to ~30 for READS
═══════════════════════════════════════════════════

[Client]
 ┌──────────────┐
 │    Client    │
 └──────┬───────┘
        ▼
 ┌──────────────────────────────────────────────────────────┐
 │                     API Gateway                           │
 └──────┬───────────────────────────────────────────────────┘
        ▼
 ┌──────────────────────────────────────────────────────────┐
 │  Coordinator Pool — 14+ stateless                         │
 │──────────────────────────────────────────────────────────│
 │  ① hash(key) → owner nodes            (unchanged)         │
 │  ② Consistency: strong? ──YES──▶ BYPASS cache, R=2 quorum │
 │  ③ otherwise ────────────────────▶ try cache first        │
 └───────┬──────────────────────────────────────┬───────────┘
         │ ③ cached path                        │ ② strong path
         ▼                                      │ (never cached)
 ┌───────────────────────────────────┐          │
 │  Redis Cluster                    │          │
 │  MEMORY: hot set ~10GB (see below)│          │
 │  THROUGHPUT: ~20-30 shards, NOT 6 │          │
 │  LRU eviction · TTL 60s · RF=1    │          │
 └───┬───────────────────────────┬───┘          │
     │ ④ HIT (~80% ASSUMED)      │ ⑤ MISS       │
     │ value + ETag in <1ms      │              │
     ▼                           ▼              ▼
 ┌────────┐        ┌──────────────────────────────────────┐
 │ Client │        │ Ring — 12 nodes × RF=3  (UNCHANGED)   │
 └────────┘        │ LSM: WAL→Memtable→SSTable            │
                   │ ~38KB Bloom/SSTable, ~800MB/node     │
                   └──────────────┬───────────────────────┘
                                  │ async
                   ┌──────────────┴──────────────┐
                   ▼                             ▼
            ┌─────────────┐              ┌─────────────┐
            │ EU ~80ms    │              │ AP ~150ms   │
            └─────────────┘              └─────────────┘

The strong-read path deliberately bypasses the cache entirely — a cache
cannot participate in a quorum, so serving Consistency: strong from Redis
would silently downgrade it to eventual. Draw that branch explicitly;
it is a favourite probe.

WRITE PATH CHANGE (the part candidates forget):
  PUT /v1/keys/{key}
    1. Coordinator writes to W=2 replicas as before
    2. Coordinator DELETES the cache entry — invalidate, never
       write-through — then returns 201 + new ETag
  Why delete instead of update? W=2 of RF=3 means the value is not yet on
  every replica. A write-through cache would serve a value the ring could
  still lose during a node failure + rollback, and a stale cached ETag
  would make the next conditional PUT fail with a spurious 412 (see
  Deep Dive 3). Invalidation makes the ring the only source of ETags.

SIZING THE CACHE — SIZE IT FROM THE TTL, NOT FROM THE KEYSPACE.
  The tempting (and wrong) version: live keys = 5.2TB ÷ 2KB = ~2.6B keys;
  cache "the hot 1%" = 26M keys × 2KB = ~52GB. That number is a fraction
  of the KEYSPACE and it ignores the TTL entirely.
  The TTL is the real constraint. With TTL=60s an entry survives only if
  it is re-read inside 60 seconds, so the number of entries traffic can
  possibly keep warm is bounded by the REQUEST RATE, not the key count:
      warm entries <= hit_rate × request_rate × TTL
      at 100K reads/sec: 100K × 60s = 6M reads per TTL window, and those
      6M reads cannot touch more than 6M distinct keys.
  So at most ~6M entries are ever live. Provisioning 150GB (75M entries)
  would leave >=92% of the memory permanently cold — you would be paying
  for RAM the TTL guarantees you can never fill.
      ~6M entries × 2KB ≈ 12GB, call it ~10GB working set.
  → BY MEMORY that is 1-2 shards. See the throughput sizing below for why
    you still do not get to run 1-2 shards.
  Corollary worth saying out loud: raise the TTL and the cache gets
  bigger AND the staleness window gets longer. TTL is a sizing knob and a
  correctness knob at the same time.

SIZING THE CACHE BY THROUGHPUT — the sizing candidates skip entirely.
  Redis is SINGLE-THREADED per shard for command execution: ~100-200K
  ops/sec/shard, and a 2KB value at 200K ops/sec is 200K × 2KB × 8 bits
  ≈ 3.2 Gbps of NIC per shard. Both are hard per-shard walls.
  Check the 6-shard number against them: 5M reads/sec at an 80% hit rate
  is 4M cache ops/sec ÷ 6 = 667K ops/sec/shard and ~10.7 Gbps/shard.
  Both are impossible — roughly 4× over the CPU wall and 3× over a 10GbE
  NIC. 6 shards was never a throughput answer; it was a memory answer to
  a memory question nobody should have asked.
  → THROUGHPUT sizing: ~200K ops/sec/shard and ~3 Gbps/shard, so
    4M ops/sec needs ~20 shards, and ~25-30 with headroom for skew
    (shards are not evenly loaded under Zipf) and for RF.
  THE POINT: shard count here is set by THROUGHPUT, not memory. You need
  ~20-30 shards no matter how small the hot set is — most of that RAM
  sits unused, and that is fine, because you are buying CPU and NIC.

⚠ RF=1 ON THE CACHE IS AN AVAILABILITY DECISION, NOT A FREE ONE.
  "Cache loss is not data loss" is true and irrelevant here. With RF=1
  and 6 shards, losing one shard dumps 1/6 of the HIT traffic onto a ring
  that has ~1.2× headroom — that is an instant overload, not a graceful
  degradation, and it lands against a 99.99% SLO (52 min/year total
  budget). Either run replicas on the cache shards, or size the ring to
  absorb a shard loss, or accept a documented brownout — but say which.
  This is the argument FOR more, smaller shards: at 30 shards a single
  loss is 3% of hit traffic, not 17%.

⚠ THE 80% HIT RATE IS ASSERTED, NOT DERIVED — flag it yourself.
  Every throughput number in Stage 3 is linear in this one figure, and it
  is the most load-bearing unverified assumption in the design. It
  depends on the actual Zipf exponent, the write rate (every write
  invalidates), and the TTL. Derive it from a measured access-frequency
  histogram before trusting it; in the interview, say "80% is my
  assumption, here is what moves it, and here is the answer at 60%."
  At 60% the ring sees 40% of traffic and Stage 3 tops out near 300K/sec.

STAGE 3 THROUGHPUT (from the above):
  At an 80% hit rate the ring sees 20% of reads. A 12-node ring supports
  ~120K reads/sec, so total = 120K ÷ 0.20 = **~600K reads/sec**, and
  ~1.5M reads/sec only once the ring is widened to ~30 nodes for reads
  alone. Getting to 5M needs a >=94% hit rate (5M × 0.06 = 300K to the
  ring) or a much wider ring. Name the lever; never quote 5M unqualified.

WHY THE RING STILL DOES NOT GROW *FIRST* HERE:
  Careful — the old version of this file said the ring had 3× read
  headroom it never used. That was an artefact of the peak-as-average
  storage error; it does not. The ring has ~1.2×. So the honest argument
  is not "the ring has spare capacity," it is a COST argument:
  a cache shard is far cheaper per read served than a storage node, and
  a storage node bought purely for reads comes with 2TB of disk you do
  not need. You add cache first because it is the cheapest read capacity
  available — and because it is the ONLY thing that helps an extreme
  single-key hotspot, since consistent hashing pins a hot key to exactly
  3 replicas whether the ring has 5 nodes or 500.
  The reasoning to voice: "I reach for the cache before more nodes
  because reads are the cheap thing to buy in RAM and the expensive thing
  to buy in disk — and because more nodes cannot unpin a single hot key.
  But I am not claiming the ring has slack; it has about 20%."

CEILING OF STAGE 3: ~1.5M reads/sec at an 80% hit rate (~5M only at a
  >=94% hit rate), and the cache is what caps it — for two reasons that
  more hardware does not fix:
   (a) Hot-key skew survives the cache. Consistent hashing pins any single
       key to exactly 3 nodes, no matter how wide the ring gets. Those 3
       owners supply 3 × 10K = 30K reads/sec combined, so a key needs 30K
       reads/sec — 30% of the whole 100K peak — to saturate them. Zipf
       alone does not get there (top key ≈ 4.5% of traffic). What gets
       there is the STAMPEDE: when one viral key is evicted by LRU or
       invalidated by a write, every concurrent reader misses at the same
       instant, and that momentary burst is unbounded by Zipf. A single
       key can then saturate 3 of 12 nodes while the other 9 sit idle.
       The cache delayed hot-key skew; it did not remove it.
   (b) Invalidation is not ordered against cross-region replication. From
       Section 4 there are 20K replication events/sec (each write ships to
       the other 2 regions), with 80ms lag to EU and 150ms to AP. A cache
       DELETE in AP can land
       before the replicated write does, so the very next AP read
       repopulates the cache from a replica that has not applied the write
       — pinning a stale value for the full 60s TTL. The staleness window
       becomes TTL + replication lag, not replication lag, which silently
       breaks read-your-own-writes for anyone not sending
       Consistency: strong.
  Observable symptom: Redis keyspace_misses arriving in sharp bursts
  rather than a steady rate; per-node IOPS wildly imbalanced across the
  ring (3 nodes pegged, the other 9 idle); AP-region read-your-writes
  complaints lasting ~60s instead of the expected 150ms.
  Next moves, in order:
   1. Single-flight request coalescing in each coordinator: N concurrent
      misses on the same key collapse into 1 backend read. Kills the
      stampede in software, with zero new hardware — do this first.
   2. Hot-key over-replication: detect the top-K keys and replicate them
      beyond RF=3 to every node (or to a dedicated read-only replica set),
      so no single key is pinned to 3 nodes.
   3. Version-aware cache entries: cache (value, version) and stamp the
      invalidation with the version it supersedes, so a late repopulate
      cannot install an older value over a newer one. Keep routing every
      Consistency: strong read past the cache entirely.
   4. Only then widen the ring further and raise the virtual-node count —
      it is the most expensive move and it does nothing for (a) or (b).
```

---

## 🔬 Section 8 — Core Component Deep Dives

### Deep Dive 1: Consistent Hashing — Key Distribution

**Why this is the most critical component:**
Without consistent hashing, adding or removing a storage node requires rehashing ALL keys (a full data migration). With consistent hashing, only the keys owned by the affected node's token range need to move. At 5.2TB of live data per region (15.5TB physical), full rehash = hours of downtime; consistent hashing = minutes of targeted migration.

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
  Node C owned (120, 180] — a 60-unit range. Node F at 150 takes
  (120, 150], which is HALF of Node C's range.
  Keys from 120-150 move from Node C to Node F
  All other keys stay put
  → Half of Node C's keys migrate — and Node C held 1/6 of the ring
    after the add, so that is ~1/12 of TOTAL data. Nothing else moves.

KEY INVARIANT:
  Adding one node to an N-node ring migrates ~1/(N+1) of total data —
  all of it out of a single neighbour, none of it from anyone else.
  At N=5 that is ~1/6 ≈ 17% of the ring. At this system's real N=12,
  adding a node moves ~1/13 ≈ 8%. Either way: not 100%.
  (This 5-node picture is a teaching diagram. Quote the ~8% figure when
  talking about THIS system — see Section 12.)
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
FIRST, PIN THE RECORD SIZE. Section 4 says the average value is 2KB.
Use that number here too — an earlier version of this file used 640B,
which silently assumed post-compression on-disk records and produced a
key count 3x too high. One value size, everywhere: 2KB.

Bloom filter per SSTable:
  n = keys per SSTable = 64MB SSTable ÷ 2KB avg record ≈ 32,000 keys
  p = 0.01 (1% false positive rate)
  m = -n × ln(p) / (ln 2)²
  m = -32,000 × ln(0.01) / 0.4805
  m = -32,000 × (-4.605) / 0.4805
  m ≈ 306,700 bits ≈ 38KB per SSTable Bloom filter
  (≈ 9.6 bits per key — that ratio is what actually matters; it is
   independent of n, so it is the number to carry around.)

NOW THE REAL RAM BUDGET — a Bloom filter exists for EVERY SSTable,
not just the ones in L0-L1. Every SSTable at every level carries its
own filter in its footer, and they are all resident because the whole
point is to answer before touching disk.

  Physical data per storage node: 15.5TB/region ÷ 12 nodes ≈ 1.3TB
  SSTables per node:              1.3TB ÷ 64MB ≈ 20,000 SSTables
  Bloom memory per node:          20,000 × 38KB ≈ 780MB

  Cross-check the same number from the key side:
  1.3TB ÷ 2KB = 650M keys/node × 9.6 bits ≈ 780MB. Agrees.

  → Budget ~800MB-1GB of RAM per storage node for Bloom filters.

THIS IS STILL THE GOOD ANSWER — that is the entire point of a Bloom
filter: ~800MB of RAM buys you absent-key answers for 1.3TB of disk,
a ~1,700:1 compression of the membership question. But it is a real
line in the node's RAM budget that has to be stated, alongside the
Memtable and the block cache. It is NOT 1.2MB.

WHICH MACHINE HOLDS THEM: the STORAGE node, beside its own SSTables.
Never the coordinator. Filters are per-SSTable footer structures; they
are read by the node that owns the SSTable, at SSTable-read time,
before it issues any disk I/O (see `mightContain` below, and the read
path in Section 7 — "Replica checks Bloom filter"). Shipping them to a
coordinator would mean streaming 780MB per node × 12 nodes = ~9.4GB of
filters into a stateless process that owns no SSTables and would have
to re-fetch them on every ring change. The coordinator routes; the
replica filters.
```

**What happens on a false positive (key absent but Bloom says "maybe"):**
1. Coordinator routes to storage node
2. Storage node checks Bloom: positive (false alarm)
3. Reads SSTable index file from SSD: one disk seek (~0.1ms)
4. Does binary search in SSTable: ~3 disk reads (~0.3ms total)
5. Concludes key not found
6. Returns 404 to client

**The 1% FPR is PER SSTABLE, not per read — this is the step everyone skips.** A single logical read consults roughly 10 SSTables (the ones its key range could live in after Bloom pruning across levels). The probability that *at least one* of them false-positives is `1 − 0.99¹⁰ ≈ 9.6%`, not 1%.

Recompute the waste with that number: at a 40% absent-key rate, 100K reads/sec gives 40K absent-key reads/sec. About 9.6% of those hit at least one false positive → **~3,840 false-positive events/sec**, each costing ~0.4ms and ~4 disk I/Os → **~15,400 wasted disk I/Os/sec** across the ring. Spread over 12 nodes at ~100K IOPS each (1.2M IOPS total), that is ~1.3% of the ring's IOPS budget — still comfortably affordable, but an order of magnitude away from the "400 wasted disk reads/sec, negligible" figure you get by treating 1% as per-read. Quote the per-read number (9.6%), not the per-filter number (1%), whenever you are talking about read amplification.

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
           → Client B must re-read (GET), get ETag "v4", then retry the
             PUT with If-Match: "v4"

KEY INVARIANT:
  The storage layer treats a conditional PUT as an atomic CAS
  (Compare-And-Swap):
  if stored_version == if_match_etag → write new value, bump → 200
  if stored_version != if_match_etag → refuse write        → 412

WHERE THAT ATOMICITY COMES FROM (see Section 9 — NOT from a shared DB):
  The version lives in the RocksDB record on each owner replica.
  The coordinator runs the CAS against the key's OWN 3 owners using
  the quorum that already exists:
    1. R=2 read of the current version from the owner replicas
    2. compare against If-Match; on mismatch, 412 and stop
    3. on match, W=2 write with the bumped version
  Per-replica atomicity is RocksDB's own CompareAndSwap; agreement
  across replicas is the W+R>N quorum. No relational row lock, and
  no single machine on the write path.
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
KV STORE: LSM COMPACTION          KAFKA: LOG COMPACTION
─────────────────────────────     ─────────────────────────────

Memtable (in-memory):             Active segment (buffer):
  user:1 → v3 "charlie"             offset 100: user:1→"charlie"
  user:1 → v2 "bob"                 offset  99: user:1→"bob"
  user:2 → TOMBSTONE                offset  98: user:2→null

Flush to SSTable L0:              Flush to log segment S1:
  [user:1 v3]                       [user:1: "charlie"]
  [user:1 v2]                       [user:1: "bob"]
  [user:2 TOMBSTONE]                [user:2: null]

Compact L0 + L1:                  Compact old segments:
  old versions of user:1 purged     old offsets of user:1 purged
  tombstone for user:2 purged       tombstone for user:2 purged
  → user:1 = "charlie" only         → user:1 = "charlie" only
  → user:2 gone entirely            → user:2 gone entirely

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

### Storage Engine Selection — Why RocksDB? Why Not the Obvious Alternatives?

This question has two storage decisions. Not one.

- **Decision A:** What stores the key-value data on each storage node? → RocksDB
- **Decision B:** What stores ring membership metadata? → Postgres (or etcd — either is defensible)

Both decisions are probed. Asserting "RocksDB because it's an LSM-tree" without rejecting the alternatives is the same gap as saying "consistent hashing" without explaining why not modulo sharding.

---

**Decision A: Storage Engine for Key-Value Data**

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **RocksDB / LSM-tree** ✅ | Write-optimized: every write is a sequential WAL append + in-memory Memtable update — no random disk I/O on the write path. Immutable SSTables: no update-in-place, no page splits, no lock contention between readers and writers. Compaction as GC: old versions and tombstones are purged in the background, not during the write. Bloom filters on every SSTable eliminate disk I/O for absent-key reads. Already the storage engine inside Kafka Streams (RocksDB state backend), Apache Flink, and the embedded engine in Cassandra — the strongest domain signal at Confluent. | Compaction stalls cause P99 spikes when LSM levels pile up (mitigated by leveled compaction tuning). Write amplification: each byte is written once to WAL, once to Memtable, and rewritten during each compaction level — 10-30× write amplification at L6. Absent-key reads require Bloom filters to avoid fan-out across all SSTable levels (the whole reason Deep Dive 2 exists). |
| **B-tree RDBMS (Postgres / MySQL as primary store)** ❌ | ACID transactions out of the box. Familiar operational tooling. Secondary indexes for free. | Update-in-place: every write seeks to the exact page on disk and writes in-place. At 10K writes/sec with RF=3 = 30K disk writes/sec across the ring, all random seeks. NVMe handles ~100K random IOPS total per node — 30K of those would be consumed by writes alone, leaving only 70K for reads, against a 100K/sec peak requirement. B-tree page splits block reads while they rebalance, creating write-induced read stalls. Horizontal sharding requires building a ring on top — you would re-invent what RocksDB gives you at the storage layer. Most damaging: putting a relational DB per-node means each node's write path is ACID-serialized through a single B-tree, which is exactly the single-machine bottleneck this system is designed to avoid. |
| **Apache Cassandra (as the full storage + ring system)** ⚠️ | Uses LSM-tree internally — same Bloom filters, same compaction model. Consistent hashing ring is built-in. Tunable N/W/R quorum is built-in. Production-proven at exactly this workload (Discord, Netflix, Apple). | Taking Cassandra means ceding control over the ring coordinator, replication protocol, and compaction tuning — the three things the interviewer will probe. The design above IS Cassandra's architecture with explicit control over each layer. Answering "just use Cassandra" closes off every Deep Dive in Section 8 and removes all the Confluent-specific signal (Bloom filter sizing, WAL durability boundary, Tableflow compaction parallel). **This is a valid production answer. It is a weak interview answer.** If pressed, say: "Cassandra would work and is my production default. I'm designing from the storage engine up because it lets me explain the compaction-Tableflow parallel explicitly." |
| **Apache HBase / Bigtable (wide-column store)** ❌ | LSM-tree internally. Scales horizontally. Strong for time-series or sparse-column workloads. | Requires HDFS or GCS dependency — operational complexity not justified for a pure KV workload with no range scan requirement (range queries are explicitly out of scope in Section 3). HBase adds a RegionServer layer that mirrors the coordinator pool but with heavier JVM overhead and longer GC pauses under high write load. The LSM benefit is the same as RocksDB; the extra dependency is not. |
| **Redis as primary storage (not caching tier)** ❌ | Sub-millisecond reads. Zero disk I/O. | Memory cost at 5.2TB live data: ~5.2TB of RAM per region. At commodity cloud pricing (~$5–10/GB DRAM), that is $25K–50K/month per region in RAM alone — before redundancy. Redis persistence (AOF/RDB) is asynchronous by default and requires careful `fsync` tuning to avoid data loss on node failure. Redis Cluster sharding is client-aware, not ring-transparent. All of these together make Redis the wrong tool for the primary store at this scale. It is the right tool at the ~10GB hot-set layer in Stage 3, precisely because the working set fits in memory and the cold 5.1TB sits safely on disk in RocksDB. |

**Decision: RocksDB (LSM-tree) as the per-node storage engine.**

Because write amplification on sequential I/O is cheaper than random I/O on a B-tree at 10K
writes/sec. Because Bloom filters on SSTables solve the absent-key problem that would
otherwise make 40K reads/sec (40% absent-key rate at 100K peak) catastrophically expensive.
And because RocksDB is the storage engine inside Kafka Streams' state stores — the compaction
model is the Tableflow parallel that makes this question worth answering carefully.

---

**Decision B: Ring Membership Metadata Store**

Ring membership is the mapping from `(node_id → token positions, region, host, state)`.
It is read at coordinator startup and on membership change events — not per request,
not per key, not on any write or read data path. This is a fundamentally different
access pattern from the key-value store itself.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Postgres** ✅ | ACID — membership changes (JOINING → ACTIVE → LEAVING) must be atomic; two coordinators must never see conflicting ring views simultaneously. Operationally simple: a managed Postgres instance (RDS, Cloud SQL) with a standby replica is one component, not a distributed consensus cluster. The table has tens of rows, so the B-tree index overhead that makes Postgres wrong for the data layer is completely irrelevant here. | Single-node write path — mitigated by the fact that membership changes are rare (node joins/leaves happen minutes apart, not thousands per second). If Postgres is down, coordinators can continue serving requests using the last cached ring view; they cannot add or remove nodes. |
| **etcd or ZooKeeper** ✅ | Purpose-built for distributed configuration and service discovery. Raft consensus (etcd) or ZAB (ZooKeeper) gives linearizable reads and writes across a 3-node quorum — no SPOF. The role Kafka plays with ZooKeeper/KRaft for its broker membership is exactly this use case. | One more system to operate. For a new build, etcd is the production-grade choice. In the interview, either answer is correct — the file explicitly states "etcd/ZooKeeper would be a fine substitute." |
| **In-memory at each coordinator (gossip)** ❌ | No network hop for ring reads. | All coordinators need identical ring state. Gossip convergence takes O(log N) rounds (~100ms for a 14-node pool), meaning coordinators can route to stale ring state during that window. Split-brain on coordinator partition: two coordinators with divergent ring views will route the same key to different owner sets, breaking W=2 quorum semantics. The added complexity (gossip protocol, convergence detection, split-brain resolution) is not justified when ring reads happen at startup only. |
| **Storing ring membership in RocksDB (on the storage nodes)** ❌ | One fewer system. | A coordinator cannot read its own ring membership from a storage node it cannot yet locate — the ring view is needed to locate any node. Bootstrapping deadlock. |

**Decision: Postgres for the interview; etcd for production.**

Postgres for the interview because it is the simplest component that satisfies the
access pattern: tens of rows, ACID, read at startup, one managed instance with a standby.
etcd for production because ring membership changes must be linearizable across all
coordinators simultaneously — a requirement that Raft satisfies natively and that a
Postgres standby only satisfies with additional leader-election logic on top.
Either answer is defensible and both must be explained the same way: "the ring membership
store is NOT on the data path, it holds tens of rows, and it is read at coordinator startup
— its availability requirements are different from the storage ring's 99.99% SLO."

---

For a distributed KV store, the storage engine is NOT a relational DB — it's an LSM-tree backed by SSTables (implemented by RocksDB, a key-value storage library). Postgres holds **ring membership only** — nothing that is per-key.

### ⚠️ The per-key `key_versions` table was the design's own worst bottleneck — here is why it is gone

An earlier version of this design put a `key_versions` row **per key per region** in Postgres and called it "a single table, tiny." Run the numbers from Section 4 and it is neither:

- 2.6B live keys × 3 regions = **7.8B rows**
- at ~135 bytes/row (2KB key column is `VARCHAR` but real keys are short; plus version, timestamp, tuple header) ≈ **1TB of heap**, plus a comparably sized primary-key B-tree
- every single write takes a **row lock on that one Postgres node** — at 10K writes/sec peak, that is a single machine serializing the entire cluster's write path
- and it sat on the **strong-read path** too

That is a single-node RDBMS placed on the critical path of a design whose whole premise is *"no single machine is a bottleneck."* It never appeared in any Stage's breaking-point analysis, which is exactly how a real system ends up with an un-instrumented SPOF against a 99.99% SLO. **If an interviewer finds a single-node Postgres on your 10K-writes/sec path, the distributed-systems axis is over.**

**The fix: the version already lives in the RocksDB record.** Look at the `key → { value, version, deleted, updated_at }` schema below — `version` is right there. So:

- **CAS executes at the coordinator against the key's own owner replicas**, using the W=2 quorum that already exists. The coordinator reads the current version from the owners (R=2 for a conditional write), compares it to `If-Match`, and issues the write only if they match — the same quorum, one extra round trip, zero new infrastructure. RocksDB's own single-node `CompareAndSwap` primitive enforces it per-replica; the quorum makes it agree across replicas.
- **Postgres shrinks to ring membership: tens of rows.** Node IDs, token positions, region, state. That table genuinely *is* tiny, it is read at coordinator startup and on membership change (not per request), and it is not on any data path. This is the role etcd/ZooKeeper plays in Cassandra and Kafka — and either would be a fine substitute here.

**Ring Membership Schema (Postgres — tens of rows, off the data path):**

```sql
-- Ring membership ONLY. Not per-key. Not on the read or write path.
-- Read at coordinator startup and on membership change events.
CREATE TABLE ring_members (
    node_id         UUID            PRIMARY KEY,
    region          VARCHAR(32)     NOT NULL,
    -- one row per PHYSICAL node; its 150 virtual-node token positions
    -- are derived deterministically from node_id, never materialized
    host_port       VARCHAR(256)    NOT NULL,
    -- JOINING / ACTIVE / LEAVING / DOWN — drives rebalance decisions
    state           VARCHAR(16)     NOT NULL,
    joined_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Coordinators subscribe to changes for their own region's ring
CREATE INDEX idx_ring_members_region ON ring_members (region, state);
```

**Three specific things that were wrong with the old table, worth knowing so you don't rebuild them:**

1. **`primary_node_id` should never have existed.** Ownership is a pure function of `hash(key)` and the current ring — that is Deep Dive 1's entire thesis. Materializing it per key means a ring rebalance (add one node) becomes a multi-billion-row `UPDATE`, which is precisely the catastrophe consistent hashing exists to avoid. Compute ownership; never store it.
2. **`CREATE INDEX idx_key_versions_key ON key_versions (key)` was 100% redundant.** `PRIMARY KEY (key, region)` already builds a B-tree with `key` as the leading column, so any lookup or range scan on `key` alone uses it. The extra index bought nothing and cost a second write per insert. Watch for this pattern generally: an index on the leading column(s) of an existing composite key is always dead weight.
3. **A row lock on `(key, region)` does not serialize a multi-region CAS.** The primary key includes `region`, so a lock on `(k, 'us-east')` and a lock on `(k, 'eu-west')` are locks on two *different rows* — they never contend. The old text named a mechanism that cannot deliver the guarantee it claimed. Multi-region CAS needs either a designated home region per key (route all conditional writes for `k` to one region) or a real consensus round; a row lock in a per-region-keyed table is neither.

**RocksDB Storage Layer (per-node, not SQL):**

Each storage node runs RocksDB. The logical schema is:

```
key    → { value: bytes, version: string, deleted: bool, updated_at: epoch_ms }
```

Physically stored as:
- **WAL** (Write-Ahead Log — an append-only file where every write is recorded before it touches memory, ensuring durability if the node crashes mid-write): durability guarantee on every write
- **Memtable**: in-memory sorted map, ~64MB per CF (Column Family — RocksDB's namespace for grouping related keys, like a table in RocksDB terms)
- **SSTables** (L0-L6): immutable sorted files on SSD; L0 written directly; L1-L6 populated by compaction
- **Bloom filter** per SSTable: ~38KB at 1% FPR, ~800MB resident per node across ~20,000 SSTables (see Deep Dive 2 sizing) — held here, on the storage node, never on the coordinator

**Key Schema Decisions:**
- **CAS version is a string (not integer):** Allows opaque server-generated tokens; client can't predict/forge the next ETag. Using `UUID` or `SHA-256(value + timestamp)` prevents replay attacks.
- **The version lives in the RocksDB record, not in Postgres.** It is the `version` field above. CAS is executed by the coordinator against the key's own owner replicas through the existing W=2 quorum: read the current version (R=2), compare with `If-Match`, write only on match. RocksDB's per-node `CompareAndSwap` gives per-replica atomicity; the quorum gives agreement across replicas. No relational database is involved, so no single machine sits on the write path.
- **Strong reads stay LOCAL — R=2 within the region, never cross-region.** A `Consistency: strong` read is a quorum of 2 of the 3 *local* owner replicas, P99 ~100ms (Section 7). A cross-region version check would add an 80-150ms one-way hop (150-300ms round trip to AP), putting a "strong" read at ~300ms — 3× the documented figure and 6× the 50ms eventual-read SLO. Any design where a strong read leaves the region has quietly redefined what the header means.
- **Replication catch-up reads the RocksDB record, not a SQL index.** "What changed in US-East since T?" is answered by scanning each node's own `updated_at` ordering / WAL tail on the storage nodes — anti-entropy is a peer-to-peer repair between replicas (Section 12), not a query against a central table. Keeping repair traffic off any shared component is what lets it run continuously in the background.

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
  - [Technical]: If we used LWW (last-write-wins by timestamp): at 10K writes/sec peak with clock skew across nodes (NTP accuracy ±1ms), two writes within 1ms of each other result in one silently dropped. Over 1 day at the *average* 3,333 writes/sec (not the peak — see Section 4): 288M writes; at 0.1% collision rate = **288K silently lost values per day**.
  - [Streaming impact]: If this KV store holds Kafka consumer group offsets, a silently overwritten offset causes a consumer to re-read already-processed messages — Iceberg table sink receives duplicate events; downstream analytics tables see double-counted metrics until manual deduplication.

### Trade-off 3: Bloom Filter FPR 1% vs 0.1%

- **Chose:** 1% false positive rate (FPR) per SSTable Bloom filter
- **Gain:** ~38KB per SSTable, ~9.6 bits/key (see Deep Dive 2 math) — all of a node's Bloom filters fit in **~800MB of that storage node's own RAM**, covering 1.3TB of SSTables, with zero disk I/O for Bloom operations. (They live on the storage node beside the SSTables, not in a coordinator heap — the coordinator owns no SSTables.)
- **Lose:** 1% FPR is *per SSTable*, and a read consults ~10, so **~9.6% of absent-key reads** hit at least one false positive costing ~4 disk I/Os. At 40K absent-key reads/sec (40% of 100K): ~3,840 false-positive events/sec ≈ ~15,400 wasted disk I/Os/sec, about 1.3% of the ring's 1.2M-IOPS budget
- **Failure mode if wrong:**
  - [Technical]: If FPR = 10% per SSTable (common mistake: not sizing Bloom correctly): the per-read false-positive probability becomes `1 − 0.9¹⁰ ≈ 65%` — two thirds of absent-key reads do wasted disk work. ~26K FP events/sec ≈ ~104K wasted disk I/Os/sec, ~9% of the ring's IOPS budget gone to nothing; storage node P99 climbs above the 50ms SLO as disk queue depth grows. Note how brutally non-linear this is: 10× the per-filter FPR is ~7× the per-read FPR, not 10× — because at 1% you were already compounding across 10 filters
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
| Version history (`GET /v1/keys/{key}/versions`) — served from a separate **non-compacted** audit topic, because the ring itself keeps only 1 version/key | Log offset history — Kafka retains full message history until retention expiry. Note the parallel is exact: you need a *second, non-compacted* topic. A compacted topic has no history either |

**Concrete Tableflow signal:**

> "When Tableflow needs to store which Iceberg snapshot ID corresponds to the current committed state of a table, it writes `(table_id → snapshot_id)` to a compacted topic. Consumers reading this topic always see the latest snapshot ID for any table. This is identical to `GET /v1/keys/{table_id}` on a KV store returning the latest snapshot ID. The compacted topic IS the distributed KV store — the schema just happens to be a Kafka topic instead of a REST API."

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why consistent hashing instead of simple modulo sharding?"**
> With modulo sharding (`hash(key) % N`), adding one node changes `N`, forcing every key's assigned node to change. At 15.5TB physical per region, a full rehash takes hours of downtime. With consistent hashing, adding one node moves only ~1/(N+1) of keys — **at this system's 12 nodes, adding one migrates ~1/13 ≈ 8% of the ring**, and it migrates incrementally while the system stays online. (The 5-node teaching diagram in Deep Dive 1 gives ~17%; quote 8% when talking about this design, not the diagram's number.) Virtual nodes (150 per physical node) further smooth distribution so one node doesn't get 30% of the keyspace by hash luck.

**Q: "What is your consistency model?"**
> Tunable consistency via Dynamo-style N/R/W quorums. Default: N=3, W=2, R=1 → W+R=3, which equals N, so it's eventual. For `Consistency: strong` reads: R=2, W=2 → W+R=4 > N=3, guaranteeing we'll always read at least one replica that saw the latest write. The CAP corner by default is AP (Availability + Partition tolerance); with `Consistency: strong`, the request opts into CP temporarily.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "What happens during a network partition between US-East and EU-West? Walk me through both read and write behavior."**
> With W=2 and RF=3 per region, a write within US-East still succeeds (quorum within the region) — note this is exactly why CAS is executed against the local owner replicas rather than a shared database: a partition cannot take the write path down. EU-West reads are served locally from EU-West replicas — they may be stale by the replication lag (~80ms) but remain available, and a `Consistency: strong` read is still a *local* R=2 quorum, so it also keeps working. Writes that need cross-region coordination (a key with a designated home region in the other partition) fail with a timeout. After the partition heals, EU-West replicas catch up by scanning their own `updated_at` ordering: "give me all keys updated since T" — peer-to-peer anti-entropy repair between replicas, with no central table in the loop. The system is AP during partition, CP after heal.

**Q: "How do you avoid a hot partition — one key getting all the traffic?"**
> **First, do the arithmetic — don't assert that skew arrives first.** A key's 3 owner replicas supply 3 × ~10K = **30K reads/sec** combined, so one key must attract 30K reads/sec — **30% of the entire 100K/sec peak** — before it saturates its owners. Standard Zipf(s≈1) over billions of keys puts the top key at roughly **4.5% of traffic**, i.e. ~4.5K reads/sec: well under half of a *single* owner's capacity. So under the Zipfian assumption this design itself invokes, the hottest key is not close to being the binding constraint; aggregate IOPS hits first.
>
> **Skew becomes the ceiling only in the extreme single-key case** — one global config key or feature flag that literally every service reads on every request, which follows no Zipf curve at all because it is a single point of universal fan-in. That is precisely the workload a KV store like this attracts, and it is the one case a wider ring cannot fix: consistent hashing pins that key to exactly 3 replicas whether the ring has 12 nodes or 1,200.
>
> Given that, three strategies, in the order the arithmetic justifies. First, **cache it** — the Stage 3 Redis tier absorbs the extreme key entirely; the ring sees only misses, plus single-flight coalescing so a stampede collapses to one backend read. Second, **hot-key over-replication**: the coordinator detects keys appearing in > 5% of requests in a sliding window and replicates them beyond RF=3 (to a read-only replica set, or to every node), routing reads round-robin across all copies — this is the only mechanism that actually unpins a key from 3 nodes. Third, **application-level key sharding** (`config:timeout:shard_0`, `:shard_1`), which works but pushes the problem into every client.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "You mentioned Kafka compacted topics are essentially a KV store. What does this mean for Tableflow's design — what could go wrong if the compacted topic diverges from the actual Iceberg table state?"**
> If the compacted topic (table metadata KV store) diverges from the Iceberg table state (e.g., a snapshot was committed to Iceberg but the corresponding `(table_id → snapshot_id)` write to the compacted topic failed), a consumer reading the topic gets stale snapshot ID — it reads from an old Iceberg snapshot, missing recently committed data. This is the classic dual-write problem: two systems (Iceberg table file + Kafka metadata topic) must be updated atomically, but they're not in the same transaction boundary. The fix is to write to the compacted topic BEFORE committing to Iceberg (optimistic reservation), then confirm by reading back — or use Kafka transactions to make the compacted topic update atomic with any downstream Tableflow state change.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1: Using `POST` instead of `PUT` for key writes** → **Why it's wrong:** `POST` implies server-generated ID; in a KV store, the client supplies the key name, which is the resource identifier. `PUT /v1/keys/{key}` with the key in the URL is correct. → **What to say instead:** "`PUT` because the client names the resource; I'd only use `POST` if the server generated the key, like an auto-increment ID."

- **Mistake 2: Returning 409 Conflict for a CAS mismatch** → **Why it's wrong:** 409 means the resource state conflicts with the request; 412 means a request header precondition evaluated to false. Here the `If-Match` header (a precondition) is false → 412 Precondition Failed. Confluent probes exactly this code. → **What to say instead:** "412 Precondition Failed — the `If-Match` header is the precondition, and it evaluated to false. 409 would be if I were trying to create a key that already exists."

- **Mistake 3: Proposing only LWW for conflict resolution without discussing CAS** → **Why it's wrong:** LWW silently drops writes in concurrent update scenarios. Confluent evaluates API precision — an interviewer who can't get a "412 when to use it" answer from you will mark you down. → **What to say instead:** "Default behavior is LWW for writes that don't carry `If-Match`. Clients who need correctness under concurrency use ETag-based CAS — GET to fetch ETag, then PUT with `If-Match`."

- **Mistake 4: Skipping Bloom filter discussion for absent-key reads** → **Why it's wrong:** In an LSM-tree system, absent-key reads are the worst case — they fan out across all SSTable levels before returning 404. Bloom filters are the standard solution, and Confluent has probed this in prior rounds (Hack2Hire, May 2026 — consumed the whole round). → **What to say instead:** "Each SSTable has a Bloom filter, held in RAM on the storage node that owns the SSTable. Before any disk read the node checks the filter; if it returns false, it skips that SSTable entirely. And be precise about the rate: 1% FPR is *per filter*, and a read consults about 10 of them, so ~90% of absent-key reads do zero disk I/O — not 99%. The compounding across filters is the part interviewers check."

- **Mistake 5: Saying 'add more Kafka partitions' to solve write scale** → **Why it's wrong:** This is a KV store, not a Kafka use case. More Kafka partitions don't help storage-layer write throughput. The actual solution is expanding the consistent hashing ring (more storage nodes, each owning a smaller token range). → **What to say instead:** "To scale writes I add storage nodes to the consistent hashing ring. Each node owns a smaller token range, so write load distributes. The coordinator pool scales independently."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How this design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `PUT /v1/keys/{key}` uses `PUT` (not `POST`) because client names the resource; `If-Match` conditional write returns **412 Precondition Failed** (not 409); `GET` returns ETag in response header; `Consistency: strong` header triggers quorum read — each choice is deliberate and provably correct |
| **Trade-off Defense** | ✅ | Three defended decisions: eventual-by-default (AP over CP for latency SLO), ETag CAS over LWW (correctness over throughput), 1% per-filter FPR Bloom (~38KB/SSTable, ~800MB/node, giving ~9.6% per-read FPR across ~10 filters vs ~65% at a 10% per-filter FPR) — each stated as chose/gain/lose/failure |
| **SQL / Data Modeling** | ✅ | `ring_members` DDL — deliberately **tens of rows, off the data path**, because the earlier per-key `key_versions` design was 7.8B rows and a single-node write bottleneck; version moved into the RocksDB record and CAS executed via the existing W=2/R=2 quorum; ownership computed from `hash(key)` + ring rather than materialized per key; explicit reasoning for RocksDB (not Postgres) as the value store; WAL + Memtable + SSTable structure explained |
| **Distributed Systems** | ✅ | Consistent hashing with virtual nodes (~1/(N+1) migration on node change — ~8% at N=12); N=3/W=2/R=1 tunable quorum; CAP analysis for network partition (AP during partition, peer-to-peer anti-entropy after heal, no central table in the loop); cross-region async replication with explicit lag numbers (80ms EU, 150ms AP); ring sized from two independent inputs (disk and read rate) that are shown to be co-binding |
| **Pipeline Resilience** | ✅ | Write path: WAL-first on each storage node guarantees durability before the 201/200 is returned; cross-region replication uses async stream not synchronous call (write latency unaffected by remote region); Bloom filter eliminates absent-key disk fan-out that would cascade into storage node overload; cache RF=1 called out as an availability risk against the 99.99% SLO rather than waved off as "cache loss is not data loss" |
| **Concurrency** | ✅ | ETag CAS handles concurrent writers atomically — the coordinator serializes per key against that key's own owner replicas via the W=2/R=2 quorum (RocksDB `CompareAndSwap` per replica), with no shared database taking a row lock; without `If-Match`, concurrent writes resolve via LWW with caller awareness; storage node Memtable is single-writer (WAL serializes appends), SSTables are immutable after flush — no write-write race on disk |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "A read-optimized globally distributed KV store is fundamentally a CAP choice made explicit: I design for AP by default (R=1 local replica, W=2 quorum) with opt-in CP via a `Consistency: strong` request header that triggers quorum reads. The API contract's most important precision point is `PUT /v1/keys/{key}` with `If-Match` header returning 412 (not 409) on a CAS mismatch — the `If-Match` header is the precondition, and a false precondition is 412, not a resource state conflict. The read-optimization mechanism is a Bloom filter per SSTable in the LSM-tree storage engine, held in RAM on the storage node that owns the SSTable: at a 1% per-filter FPR and ~38KB per filter, that is ~800MB per node covering 1.3TB of SSTables, and it eliminates disk I/O for about 90% of absent-key reads — 90 and not 99, because a read consults roughly 10 filters and `1 − 0.99¹⁰ ≈ 9.6%`. That is the difference between P99 < 50ms and P99 > 200ms at 100K reads/sec. The Confluent/Tableflow angle is the centerpiece of this answer: log-compacted Kafka topics are architecturally the same as LSM-tree compaction — tombstone = null-value message, SSTable compaction = `cleanup.policy=compact`, and Tableflow's table metadata control plane is literally implemented as a compacted Kafka topic that functions as a distributed KV store. The trade-off I'd defend first is ETag CAS over last-write-wins: at 10K writes/sec with 1ms NTP clock skew, LWW silently drops writes at a rate that Tableflow's schema registry would notice as duplicate or missing schema versions in the Iceberg table sink."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Type 2 Full System Design, 15 sections per `solution-notes-standards.md`. Covers: consistent hashing with virtual nodes, N/R/W tunable quorum, ETag CAS with 412 status, Bloom filter sizing math for LSM absent-key reads, LSM compaction = Kafka log compaction parallel (Section 11 centerpiece). |
| Aug 2026 | **Section 9 — Storage Engine Decision Table added.** Section 9 previously asserted RocksDB and Postgres without rejecting alternatives. Added two explicit decision tables matching the format of Deep Dive 1 (Consistent Hashing) and Deep Dive 3 (CAS): **Decision A** (storage engine: RocksDB vs B-tree RDBMS vs Cassandra vs HBase vs Redis-as-primary) with specific rejection reasoning for each — B-tree ruled out by random I/O math at 10K writes/sec, Redis-as-primary by memory cost at 5.2TB scale, Cassandra flagged as valid production answer but weak interview answer; **Decision B** (ring membership: Postgres vs etcd vs in-memory gossip vs RocksDB self-bootstrap) with bootstrapping deadlock noted for the last option. Cassandra is the most important callout: it is architecturally identical to this design and is a valid answer, but choosing it in the interview closes off every Deep Dive probe in Section 8. |
| Aug 2026 | **Section 7 Stage 3 fleshed out to match Stages 1-2, ceiling added.** Stage 3 was 8 lines with no ceiling; it now has a full topology diagram (coordinator pool → Redis Cluster → 20-node ring → cross-region), the write-path change that candidates forget (invalidate-on-write, never write-through, because W=2 of RF=3 plus a stale cached ETag causes spurious 412s per Deep Dive 3), cache sizing derived from Section 4 (7.5B live keys, hot 1% = 150GB = 6 Redis shards; 80% hit rate reduces 500K reads/sec to the 100K/sec the ring already handles), and the ring migration cost via 150 virtual nodes. Added `CEILING OF STAGE 3` naming two things extra hardware cannot fix — hot-key skew surviving the cache because consistent hashing pins any key to exactly 3 nodes, and cache invalidation being unordered against 80ms/150ms cross-region replication so staleness becomes TTL + lag — with four ordered next moves (single-flight coalescing, hot-key over-replication, version-aware invalidation, widen the ring last). Stage 1 and Stage 2 breaking points each given a second distinct cause. Also flagged a node-count contradiction against Section 4, resolved in the next entry. **(The 20-node ring, 7.5B live keys, 150GB/6-shard cache and 500K reads/sec figures quoted in this entry are superseded by the next two entries — read them as a record of what was believed at the time, not as current numbers.)** |
| Aug 2026 | **Node-count contradiction resolved; ring is now disk-sized throughout.** Root cause was an error in Section 4's own storage conclusion: it read "15TB live data across 3 regions (45TB total physical)", computing 45TB as `15TB × 3 regions`. But RF=3 is *within* a region (Section 3 states RF=3 per region; the ring diagram's KEY PROPERTY says each key is owned by the 3 consecutive nodes clockwise). Correct math: 15TB logical **per region** × RF=3 = **45TB physical per region**, and 135TB globally — so 45TB was a per-region figure mislabelled as the global total. Section 4 now derives the ring size explicitly: 45TB ÷ 2TB SSD = 23-node floor, + ~20% LSM compaction headroom → **30 nodes per region** (90 globally). Stage 2 ring corrected 5 → 30 nodes, Stage 3 ring corrected 20 → 30 and explicitly marked UNCHANGED from Stage 2. **The architectural story changed as a result, for the better:** capacity — not read throughput — is the binding constraint, because 30 disk-sized nodes already deliver ~300K reads/sec against a 100K/sec peak (3× headroom). Stage 2's breaking point was therefore rewritten from "SSD IOPS + capacity" to "aggregate IOPS + hot-key skew," and Stage 3's justification changed from raw throughput to P99 latency and hot-key absorption, with a new `WHY THE RING DOES NOT GROW HERE` block making the point that consistent hashing pins a hot key to exactly 3 replicas whether the ring has 5 nodes or 500, so more nodes cannot fix skew — only a cache in front can. Stage 3's headline "5M reads/sec" is now qualified honestly (a 30-node ring supports ~1.5M reads/sec at an 80% hit rate; 5M needs ~94% hit rate or a wider ring — name the lever rather than quoting 5M unqualified). Downstream references reconciled: main HLD ring diagram relabelled as an N=5 illustration with the real 30-node count stated, Bloom-filter total corrected from 6MB (5 nodes) to 36MB (30 nodes), and the CEILING skew example from "3 of 24, other 21 idle" to "3 of 30, other 27 idle". **(Superseded by the next entry: the 15TB / 45TB / 30-node / 90-node / 300K-reads / 3×-headroom figures in this entry all rest on a second, deeper error — Section 4 was multiplying the PEAK write rate by all 86,400 seconds of the day. The `45TB = 15TB × RF=3 per region` correction described here was itself correct; the 15TB input to it was not.)** |
| Aug 2026 | **All Section 7 stage diagrams redrawn, plus two content bugs fixed.** The stage blocks were plain-ASCII vertical `[Box]`→`|`→`[Box]` chains while the file's other visuals used box-drawing. Now consistent. **Bug 1 — durability boundary mislabelled:** Stage 1 drew `[Postgres WAL for durability]` beneath the storage nodes, implying Postgres is in the write-durability path. Per Section 9 each storage node has its OWN WAL inside RocksDB (that is what makes a write durable); Postgres holds only `key_versions` for CAS/ETag serialization. The diagram now shows Postgres as a side-attached metadata store with an explicit NOTE on the durability boundary, since conflating the two invites a damaging follow-up. **Bug 2 — the LSM↔Kafka compaction comparison had mirrored columns that did not actually mirror:** the LSM side read `user:2 → v2 "bob" (tombstone)`, giving a tombstone a value, while the Kafka side had "bob" as an older version of `user:1`. Both columns now show the same three facts (user:1 has two versions, user:2 is a tombstone) and the post-compaction result is stated on both sides. Also: Stage 2 now draws the coordinator fan-out and the three regional rings, and states the two independent sizing inputs (coordinators by read rate, ring by disk); Stage 3 draws the `Consistency: strong` branch explicitly bypassing the cache, because a cache cannot participate in a quorum and serving a strong read from Redis silently downgrades it to eventual. All diagrams verified ≤80 characters. |
| Aug 2026 | **The peak-as-average cascade — Section 4 was 3× too big, and every number downstream of it moved.** Root cause, one line: Section 4 established `3,333 writes/sec avg → 10K peak (3×)` and then computed daily volume as `10K × 86,400`. You cannot run at peak for all 86,400 seconds of a day — that is what the word *peak* means. **Peak sizes the fleet; average sizes the disk.** Corrected cascade: `3,333 × 86,400 = 288M writes/day` (not 864M) → `× 2KB = 576GB/day` (not 1.7TB) → `× 90 days = 51.8TB logical` (not ~150TB) → `÷ 10 versions, keep 1 = 5.2TB live/region` (not 15TB) → `× RF=3 = 15.5TB physical/region` (not 45TB) → `÷ 2TB SSD = 8-node floor` (not 23) → `÷ 0.8 compaction headroom = 19.4TB → ~12 nodes/region` (not 30) → **36 storage nodes globally, ~46TB physical** (not 90 nodes / 135TB). Derived figures moved with it: live keys `5.2TB ÷ 2KB = 2.6B` (not 7.5B), writes/node `10K × 3 ÷ 12 = 2.5K/sec` (not 1K), replication events `10K × 2 other regions = 20K/sec` (not 30K — a write does not replicate to its own region). **The architectural story changed again, and this time it got harder, not easier.** The previous entry's headline — *"capacity, not read throughput, is the binding constraint; 30 nodes deliver 300K reads/sec, 3× the peak"* — no longer holds at any point. At 12 nodes the ring delivers `12 × 10K = 120K reads/sec` against a 100K/sec peak: **1.2× headroom, not 3×.** And 12 nodes is what disk asks for while reads independently ask for `100K ÷ 10K = 10` — the two sizing inputs land two nodes apart, so **capacity and read throughput are now CO-BINDING**. The ring must be sized from both and stated as both; neither derivation alone is defensible. Consequently Stage 3's Redis tier can no longer be justified as "not about throughput, the ring already has headroom" — it is now justified three ways at once: P99 tail latency, hot-key absorption, and **as the only cheap way to buy read headroom**, since a storage node bought for reads drags 2TB of unneeded disk along with it. Six further errors were flushed out while propagating: **(1) Bloom filters** were sized as if only L0-L1 filters exist (`10 × 120KB = 1.2MB/node`) and were said to live "in a coordinator's heap." Every SSTable at every level carries a resident filter, so a node holding `15.5TB ÷ 12 ≈ 1.3TB` across ~20,000 SSTables needs **~800MB of Bloom filters in RAM** — a real budget line, and still the good answer, since ~800MB answers membership for 1.3TB. And they live on the **storage node beside its SSTables**, never shipped to the stateless coordinator, which contradicts nothing less than the file's own read path, `mightContain` comment, and Section 9. The SSTable record size was also reconciled to Section 4's 2KB (it had said 640B), giving ~32K keys/SSTable and ~38KB/filter at 9.6 bits/key. **(2) FPR is per SSTable, not per read** — a read consults ~10, so the per-read false-positive rate is `1 − 0.99¹⁰ ≈ 9.6%`, not 1%: ~3,840 FP events/sec and ~15,400 wasted disk I/Os/sec, not "400 wasted disk reads/sec." Every "99% of cases" claim became "~90%." **(3) Postgres `key_versions` was the design's own worst bottleneck**, described as "a single table, tiny" while actually being `2.6B keys × 3 regions = 7.8B rows ≈ 1TB+`, taking a row lock on every one of 10K writes/sec, sitting on the strong-read path, absent from every breaking-point analysis, and single-node — inside a design whose premise is that no single machine is a bottleneck, against a 99.99% SLO. The version moved into the RocksDB record (where a `version` field already existed), CAS now executes at the coordinator against the key's owner replicas through the existing W=2/R=2 quorum, and Postgres shrank to `ring_members` — tens of rows, off the data path, the etcd/ZooKeeper role. Three mechanism bugs went with it: a row lock on `(key, region)` cannot serialize a multi-region CAS because `(k, us-east)` and `(k, eu-west)` are *different rows*; a cross-region version check on the strong-read path would make a "strong" read ~300ms against the ~100ms local-R=2 figure the rest of the file states; and `primary_node_id` materialized per key turns a ring rebalance into a multi-billion-row UPDATE — the exact catastrophe consistent hashing exists to prevent. Also dropped a redundant index that duplicated the leading column of the primary key. **(4) The Redis tier was sized only by memory.** Two additions: the hot set should be sized from `hit-rate × request-rate × TTL`, not from a fraction of the keyspace — with a 60s TTL and 100K reads/sec, at most 6M entries (~10-12GB) can ever be warm, so the old 150GB provisioning left >92% of it permanently cold; and Redis is **single-threaded per shard** at ~100-200K ops/sec with ~3Gbps/shard of NIC, so 4M ops/sec across 6 shards would need 667K ops/sec and 10.7Gbps per shard — both impossible. Shard count here is set by **throughput (~20-30 shards), not memory**, regardless of how small the hot set is. RF=1 on the cache was also re-framed as an availability decision: losing 1 of 6 shards dumps 17% of hit traffic onto a ring with 1.2× headroom. And the 80% hit rate is now flagged as **asserted, not derived** — the most load-bearing unverified assumption in Stage 3, with the answer at 60% stated alongside. **(5) Hot-key skew was asserted to "arrive long before" aggregate IOPS with no arithmetic.** A key's 3 owners supply `3 × 10K = 30K reads/sec`, so one key must pull 30K reads/sec — **30% of the entire 100K peak** — to saturate them, while Zipf(s≈1) puts the top key at ~4.5% of traffic. So under the file's own Zipfian assumption, aggregate IOPS arrives *first*; skew binds only in the extreme single-key case (a global config key read by every service on every request), which is exactly the workload this store attracts and the one case a wider ring cannot fix. **(6) Latency was overstated on the SSTable path**: `< 50ms` and `~5-50ms` contradicted Deep Dive 2's own `~0.4ms` derivation. The **median** SSTable read is ~0.5-1ms; only the P99 **tail** — compaction stalls, L0 pile-up, queueing — reaches tens of ms. Still a valid case for the cache, but it buys the tail, not the median. Smaller reconciliations: the never-derived `~10K reads/sec/node` constant now shows its work (NVMe ~100K IOPS ÷ ~5-10 physical reads per logical LSM read) and is explicitly separated from the coordinator's ~8K req/sec, which is a CPU/network number for a different machine — at 8K the pool needs 13 and, with N+1 for a 99.99% SLO, **14 coordinators, not 10**; Stage 2's header (~100K, coordinator-pool-limited) and breaking point (~120K, ring-limited) are now reconciled explicitly instead of contradicting each other, and its cause (a) is no longer mislabelled "not aggregate capacity" when it is titled *Aggregate IOPS*; the main HLD ring diagram's per-node box said `Coordinator WAL` (fixed to `RocksDB WAL` — the earlier changelog claimed this was fixed but only Stage 1 was); DELETE had three different durability stories across the file, unified to **W=2 replica WALs** (the coordinator is stateless and has no WAL to commit to); Stage 1's "storage nodes under 20% utilization" corrected to ~33% (`8K ÷ 3 nodes ÷ 10K`); Deep Dive 1's "only 1/5 of Node C's keys migrate" corrected to *half* of Node C's range ≈ 1/12 of the ring, and Section 12's "at N=5, 20% migrates" re-pointed at this system's N=12 → ~8%; and `GET /v1/keys/{key}/versions` was called out as promising history that **neither storage layer retains** (compaction keeps 1 version — that is the basis of the 5.2TB figure — and the metadata held one `current_version`), now explicitly served from a separate append-only, non-compacted audit topic (the natural Confluent answer), with a note that retaining N versions in the ring would invalidate the ÷10 compaction assumption outright. All diagrams re-verified ≤80 columns. |
