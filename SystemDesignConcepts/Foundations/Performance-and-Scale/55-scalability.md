# Scalability

> **Standard followed:** `SystemDesignConcepts/Interview-Resources/Metadata/notes-standards.md`

---

## 📖 What is Scalability?

**Full form:** Scalability (no acronym — the term stands alone)

**Simple analogy:** A highway that can add lanes. A two-lane road handles 2,000 cars/hour. Add two lanes — now 4,000. The road *scaled*. But if the toll booth at the end is still one lane, traffic backs up there instead. Scaling is not adding lanes — it's finding the bottleneck and eliminating it, only to find the next one.

**Core principle:** Scalability is a system's ability to handle increasing load — more users, more data, more requests — without degradation in performance. It is not a single technique; it is a decision framework: identify the current bottleneck, choose the right lever to break it, verify, repeat.

**Why it matters in system design:** Every interview that starts "design X for 10M users" is asking: what levers do you reach for, in what order, and what does each lever cost you?

---

## 🎯 Why This Matters

Scalability is the opening act of every system design interview. "How does this handle 10x traffic?" is not a senior-level question — it is the default. The senior signal is knowing *which* bottleneck to address first, *which* lever to reach for, and *what* you give up by reaching for it.

**Round:** System design (every round). Appears in both "design X" and "deep dive" formats.

**Why senior engineers own this:** Junior engineers vertically scale until it breaks. Senior engineers map the bottleneck first, then choose the cheapest lever that eliminates it without introducing new constraints.

---

## 🧠 The Mental Model

Picture a bucket brigade — a line of people passing buckets of water from a well to a fire. When the fire grows, you can:

1. Give the person at the well a bigger bucket (vertical scaling — upgrade the hardware)
2. Add more people to the line (horizontal scaling — add more instances)
3. Set up a second water tank midway so people don't walk to the well every time (caching)
4. Dig a second well and split the brigade (sharding)
5. Have some people work on other fire tasks while water is being fetched (async processing)

Each option fixes a different *bottleneck*. If the problem is that the bucket is too small, adding more people does nothing. If the problem is too many people competing for one well, adding more people makes it worse. The brigade's total capacity is *always* limited by its slowest, most constrained link.

**The key insight is:** Scaling is not about making everything faster — it is about finding the current bottleneck, breaking it, and then finding the next one. Every optimization exposes a new constraint.

---

## 🎨 Visual — System Topology with Scaling Levers + Bottleneck-to-Lever Map

```
FULL SYSTEM TOPOLOGY — where each lever lives:

     Internet Traffic
           │
           ▼
  ┌─────────────────┐  ◀── CDN (lever 4): serves static assets from
  │      CDN        │      edge nodes near users; reduces origin load
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐  ◀── Load Balancer (lever 3): required to
  │  Load Balancer  │      distribute traffic across pods;
  │ (layer 4 or 7) │      prerequisite for horizontal scaling
  └────────┬────────┘
           │
    ┌──────┴──────┐
    ▼             ▼
 ┌──────┐      ┌──────┐    ◀── Horizontal Scaling (lever 2):
 │ Pod1 │      │ Pod2 │         add/remove pods; pods must be
 │      │ ...  │      │         stateless to scale freely
 └──┬───┘      └──┬───┘
    └──────┬───────┘
           │
           ▼
  ┌─────────────────┐  ◀── Caching (lever 5): Redis or in-process;
  │   Cache Layer   │      reduces DB reads; ~1ms vs ~10ms for DB
  │     (Redis)     │
  └────────┬────────┘
           │
    ┌──────┴──────────┐
    ▼                 ▼
 ┌──────────┐   ┌──────────┐  ◀── Read Replicas (lever 6): split read
 │ Primary  │   │ Replica  │       traffic; primary handles writes only;
 │    DB    │   │    DB    │       replication lag is the cost
 └──────────┘   └──────────┘
      │
      ▼  ◀── Sharding (lever 7): split data across multiple
 ┌──────────┐    primaries when write throughput or dataset
 │ Shard N  │    size exceeds one machine's capacity
 └──────────┘


BOTTLENECK → LEVER DECISION MAP:

  Symptom                        Correct Lever
  ───────────────────────────────────────────────────────
  High CPU / memory on service    Vertical scale (short term)
  pods                            Horizontal scale (long term)

  Slow static asset delivery      CDN — edge caching
  (images, JS, CSS)

  DB read latency high,           Read replicas + caching
  write latency fine              (reduce read pressure on primary)

  DB write latency high,          Sharding — split write load
  reads fine                      across multiple primaries

  Response slow because of        Async processing — offload via
  blocking downstream calls       message queue (Kafka, SQS)
  (email, notification, billing)

  One service hot, others cold    Microservices — independently
                                  scale the hot service only

  Traffic spiky (day/night)       Auto-scaling (K8s HPA, AWS ASG)

KEY INVARIANT:
   Every system's throughput is bounded by its slowest bottleneck.
   Adding capacity at a non-bottleneck layer does not increase
   throughput — it only shifts load to expose the real constraint.
   Fix the bottleneck. Then find the next one.
```

---

## ⚙️ How It Actually Works

The scalability decision process — applied every time an interviewer asks "how would you scale X?"

**Steps:**

1. **Measure to find the bottleneck** — look at P99 latency by layer; where does latency spike first? Which resource (CPU, memory, DB connections, disk IO) saturates first?
2. **Classify the bottleneck** — is it compute-bound, IO-bound, data-bound (volume), or network-bound?
3. **Apply the right lever** — each bottleneck class has a primary lever (see decision map above)
4. **Decouple slow paths** — any synchronous operation that blocks the response but doesn't need to (email, analytics, audit logging) should be offloaded to an async queue
5. **Route reads and writes separately** — reads scale via replicas + cache; writes scale via sharding + write-behind queues

```java
// Read-replica routing — route reads to replica, writes to primary
public class ReadWriteRouter {

    private final DataSource primaryDataSource;
    private final DataSource replicaDataSource;

    // Reads go to replica — do NOT lock the primary
    public Connection getReadConnection() {
        return replicaDataSource.getConnection();
    }

    // Writes always go to primary — consistency guarantee
    public Connection getWriteConnection() {
        return primaryDataSource.getConnection();
    }
}

// Async offload — decouple notification from order creation
// Before: synchronous email blocks order response (250ms latency added)
// After: publish event, email service handles it on its own thread
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        Order order = orderRepository.save(request.toOrder());
        // Offload: email + analytics + audit log published async
        // Response returns immediately — no waiting for email gateway
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));
        return OrderResponse.of(order);
    }
}
```

### What is replication lag, and why does it matter here?

**Replication lag** (the delay between a write landing on the primary and appearing on the replica) is the hidden cost of read replicas. In a read-replica setup, a user who just wrote data might read stale data from the replica 50–200ms later. For reads where staleness is acceptable (product catalog, past orders) this is fine. For reads that must reflect the just-completed write (balance after transfer, seat availability after booking), always route to the primary.

---

## 🏢 Real World — Where Companies Use This

- **Amazon** (product catalog + orders): Static product images and descriptions served via CloudFront CDN. Product catalog reads routed to Aurora read replicas. Order writes go to primary. DynamoDB sharding for order history (billions of records across independent shards by user ID).

- **Netflix** (video streaming): Open Connect CDN handles ~99% of video delivery from edge nodes — the origin servers almost never serve the actual video bytes. Microservices independently scaled — the recommendations service scales separately from the playback service.

- **Instagram** (feed reads): Cassandra for user timelines (horizontally sharded, write-optimized). Memcache + Redis in front of Postgres for feed reads. Async aggregation via background workers — feed generation is not synchronous with the post write.

- **Swiggy** (order processing): Kafka for async order events — payment confirmation, restaurant notification, and delivery partner assignment are all decoupled from the HTTP response. Redis for restaurant availability and ETA caching — reduces DB reads by ~90% during peak hours.

- **Flipkart** (Big Billion Day flash sales): Redis pre-loaded with product inventory counts. All inventory deductions via Redis DECR (atomic, ~1ms). Async DB sync after the fact. CDN for all product images. Auto-scaling of web pods triggered at 70% CPU.

- **WhatsApp** (messaging at 50M+ concurrent): Erlang/BEAM actor model for stateful connection management — each actor is a lightweight process holding connection state. Consistent hashing to route a user's messages to the pod that holds their connection. Horizontal scale within shard boundaries.

---

## 🧭 When to Use vs When NOT to Use

| Lever | Use when | Do NOT use when |
|---|---|---|
| **Vertical scaling** | Single-threaded workloads; stateful services that can't be distributed; quick fix for imminent capacity | Already at hardware ceiling; latency is the problem (not throughput) |
| **Horizontal scaling** | Stateless services; CPU/memory bound; traffic is unpredictable or spiky | Service is stateful and you haven't externalized state; DB bottleneck (adding pods increases DB pressure) |
| **Read replicas** | Read:write ratio > 5:1; read latency is the bottleneck | Workload is write-heavy; strong read-after-write consistency required |
| **Sharding** | Write throughput exceeded single machine's limit; dataset too large for one node | Data fits on one machine; queries frequently span shard boundaries; team lacks sharding expertise |
| **Caching** | Same data read repeatedly; computation is expensive; latency matters | Data changes every request; data must never be stale; write-heavy workload |
| **Async processing** | Operation can complete out-of-band (email, analytics, billing, notifications) | Result of the operation is needed synchronously to complete the request |
| **CDN** | Static assets; geographically distributed users; read-heavy public content | Personalized or dynamic content that cannot be cached; latency for cache miss matters |

**The common mistake:** Adding horizontal pods when the DB is the bottleneck. More pods = more DB connections = more DB contention = slower. The bottleneck moved upstream; you solved the wrong layer.

---

## 🧭 Two Things Interviewers Expect You to Name

**Latency vs throughput — not the same axis.** *Latency* = time for one request (ms). *Throughput* = requests served per second. Scaling **out** raises throughput but does **not** reduce single-request latency — and can even raise **p99** (more nodes = more cross-talk, more chances to hit a slow one, longer tail). "We added servers and it's still slow per request" means your problem was latency, not throughput — scaling out was the wrong lever. Reduce latency with caching, better queries/indexes, co-location, and fewer network hops.

**Scaling has sub-linear, then negative, returns (Amdahl + Universal Scalability Law).** *Amdahl's Law*: the serial fraction of your work caps the max speedup — if 5% is serial, you can never go faster than 20× no matter how many nodes. *Universal Scalability Law (USL)* adds a second penalty: **coherency/coordination cost** (nodes must sync — locks, consensus, cache coherence), which grows with node count and eventually makes adding nodes make things *slower*. This is exactly why "add more pods" hits a wall and why the p99-gets-worse effect above happens. Senior signal: name that horizontal scaling is not free/linear — every added node pays contention (serialization) + coherency (crosstalk) tax.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Higher throughput, lower latency under load, fault tolerance (multiple instances), cost elasticity (scale in when idle) |
| **You lose** | Operational complexity (LBs, health checks, service discovery), consistency guarantees become harder (cache staleness, replication lag, eventual consistency in shards), stateless requirement constrains architecture, cross-shard queries become expensive |
| **Failure mode** | Scaling the wrong layer — adding compute when the bottleneck is DB; adding shards when the bottleneck is a hot key; caching when the bottleneck is write throughput. Each wrong lever wastes time, adds complexity, and sometimes makes the bottleneck worse |

---

## 🔬 Interview Q&As

### Q: "Walk me through how you'd scale this system to 10 million users."
> Don't reach for levers immediately. Start with: "let me understand the bottleneck at each order of magnitude." Below 100K users a well-tuned monolith on a single Postgres instance handles it. 100K–1M, I'd add read replicas and caching in front of the DB. 1M–10M, I'd horizontally scale the service tier, externalize sessions, and consider sharding if write throughput is the bottleneck. The specific levers depend on whether this is read-heavy, write-heavy, or computation-heavy.

### Q: "What's the difference between vertical and horizontal scaling?"
> Vertical scaling replaces the machine with a bigger one — more CPU, RAM, disk. It's simple, requires no architectural changes, but has a hard hardware ceiling and creates a SPOF. Horizontal scaling adds more instances behind a load balancer. It requires stateless services (or externalized session state) but is theoretically unlimited and survivable. In practice: vertical scaling buys you time; horizontal scaling is the long-term answer.

### Q: "When would you add read replicas vs when would you shard?"
> Read replicas solve the *read throughput* problem — they let you fan out reads to multiple nodes while writes go to one primary. Sharding solves the *write throughput* and *data volume* problem — when a single machine can't absorb all writes or store all data. Rule of thumb: exhaust read replicas and caching first (they're simpler), shard only when write throughput or storage capacity is genuinely the bottleneck. Sharding adds enormous operational complexity — cross-shard queries, rebalancing, resharding — that read replicas don't.

### Q: "Your database is the bottleneck. What are your options?"
> Depends on *which* DB bottleneck. If it's read latency: add read replicas + put Redis cache in front. If it's write throughput: shard the DB. If it's connection pool exhaustion (too many simultaneous connections from too many pods): use a connection pooler like PgBouncer. If it's query latency: add indexes, optimize queries, or denormalize hot read paths. I'd diagnose before prescribing — a slow index scan and a full table scan look identical from the outside but have different solutions.

### Q (Tier 2): "You tripled your pod count to handle 3x traffic. Why is your P99 latency actually higher now?"
> Adding pods increases parallelism at the service tier but also increases concurrent DB connections — more pods means more simultaneous queries on the same DB. If the DB is already the bottleneck, 3x pods means 3x DB pressure. The DB either connection-pools and queues the queries (higher latency) or starts throwing connection errors. The fix is not fewer pods — it's a connection pooler (PgBouncer), read replicas to offload reads, or caching to reduce DB hit rate.

### Q (Tier 2): "You horizontally scaled your service tier but latency is still high under peak load. What did you miss?"
> Horizontal scaling only helps if the bottleneck is in the service tier. Three common misses: (1) the DB became the bottleneck — more pods = more concurrent queries; (2) a shared downstream service (internal API, third-party gateway) is now the bottleneck; (3) session state was stored in-pod and requests are now sticky, so the load balancer isn't actually distributing load evenly. I'd profile by tier — CPU and latency at service pods, at cache, at DB — to find where the queue is building up.

### Q (Tier 2): "When does caching fail as a scaling strategy?"
> Caching fails in three scenarios: (1) write-heavy workloads — data changes faster than TTL; cache becomes stale before it can be served; (2) highly personalized data — cache key space explodes (one entry per user per query), hit rate drops to zero; (3) cache invalidation errors — dual-write between DB and cache fails mid-flight; you now serve incorrect data. Caching is most effective when data is read frequently, changed infrequently, and staleness is tolerable.

---

## 🧾 TL;DR

> "Scalability means finding the current bottleneck — compute, data, or IO — and applying the cheapest lever that breaks it: vertical scaling for quick relief, horizontal scaling for long-term throughput, caching and read replicas to relieve DB read pressure, sharding when write volume exceeds one machine, and async for anything that doesn't need to block the response."

---

## 🔗 Related Concepts

- **`03-caching.md`** — caching is lever 5 in the scalability toolkit; covers cache-aside, write-through, TTL decisions
- **`../../Core-Architecture/Database-Core/38-sharding-strategy.md`** — shard key selection, consistent hashing, cross-shard query cost
- **`../../Core-Architecture/Resilience-and-Fault-Tolerance/56-availability.md`** — availability through redundancy; closely related to horizontal scaling
- **`../../Core-Architecture/Resilience-and-Fault-Tolerance/57-spof.md`** — single points of failure introduced by un-scaled single instances
- **`52-numbers-to-know-scale-triggers.md`** — QPS thresholds, storage sizing, when to shard vs when a single node suffices

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Designing Data-Intensive Applications" Ch. 1** — Martin Kleppmann | Deep treatment of partitioning, replication lag, and the scaling-vs-consistency tension — goes far beyond interview level | ~30 min |
| **"System Design Interview Vol. 1" Ch. 1** — Alex Xu | Step-by-step single-server-to-multi-region walkthrough; good for calibrating the "what tier does X apply to?" question | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 10, 2026 | File created. Covers 7 primary scaling levers with bottleneck → lever decision map, read-replica routing + async offload code, real company examples (Amazon, Netflix, Instagram, Swiggy, Flipkart, WhatsApp), and 7 Q&As including 3 Tier 2 probe questions. |
| Jul 19, 2026 | **Gaps closed.** Added an explicit latency-vs-throughput definition (scaling out raises throughput, not per-request latency, and can worsen p99) and named Amdahl's Law + the Universal Scalability Law (serial fraction caps speedup; coherency/coordination cost eventually makes adding nodes slower) — both commonly probed and the file already gestured at them without naming them. |
