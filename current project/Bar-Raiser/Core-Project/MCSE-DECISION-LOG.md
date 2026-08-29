# MCSE — Decision Log (every "why X, not Y" you must defend)
### The trade-off ammunition for the deep-dive — one decision per section, each with a live pushback

> **What this is:** Every significant technical choice in MCSE written as `Problem → Constraint → Why this → Why-not the alternatives → Trade-off accepted → Pushback & answer`. This is the file you drill so that when the interviewer says *"why not just use Redis / REST / microservices?"* you already have the four-step answer loaded.
>
> **How to use it in the room:** Lead with the **Trade-off** line — that is the senior signal. Anyone can say "I used Kafka." Only someone who *owned* it can say "I used Kafka **and here's the cost I accepted for it.**"
>
> ⚠️ **Confidentiality:** internal service/codename/config-key names are replaced with plain-English concepts. Say the *concept* in the room — the interviewer doesn't know Walmart internals and doesn't need them.
>
> 🌉 **Companion files:** architecture narrative → [MCSE-PROJECT-DEEPDIVE.md](MCSE-PROJECT-DEEPDIVE.md) · features & failure modes → [MCSE-FEATURES-AND-FAILURE.md](MCSE-FEATURES-AND-FAILURE.md) · Kafka-vs-MQ → [KAFKA-VS-MQ-COMPARISON.md](KAFKA-VS-MQ-COMPARISON.md).

---

## 🧾 Table of Contents

**Concurrency & threading**
1. [Callable + Future in Pre-Scatter (not CompletableFuture everywhere)](#1--callable--future-in-pre-scatter)
2. [CompletableFuture in the orchestrator (not Callable/Future)](#2--completablefuture-in-the-orchestrator)
3. [SynchronousQueue (not LinkedBlockingQueue)](#3--synchronousqueue-not-linkedblockingqueue)
4. [Thread-pool isolation / bulkheads (not one shared pool)](#4--thread-pool-isolation-bulkheads)

**Messaging & ingestion**
5. [Kafka (not REST / RabbitMQ / SQS)](#5--kafka-not-rest--rabbitmq--sqs)
6. [Avro + Schema Registry (not JSON)](#6--avro--schema-registry-not-json)
7. [At-least-once + idempotent = effectively-once (not exactly-once)](#7--at-least-once--idempotent--effectively-once)
8. [Consumer-group-per-pipeline + offer-ID partition key](#8--consumer-group-per-pipeline--offer-id-partition-key)
9. [One JAR, 18 deployments (not 18 services, not 1 monolith process)](#9--one-jar-18-deployments)

**Storage & caching**
10. [Cassandra (not an RDBMS) for the offer store](#10--cassandra-not-an-rdbms)
11. [Hollow in-memory snapshots (not Redis) on the hot path](#11--hollow-in-memory-snapshots-not-redis)
12. [Three cache layers (not one)](#12--three-cache-layers-not-one)
13. [Offline ML batch → Hollow (not inline model inference)](#13--offline-ml-batch--hollow)

**Architecture & platform**
14. [Modular monolith (not microservices)](#14--modular-monolith-not-microservices)
15. [Read/write split (not one service doing both)](#15--readwrite-split)
16. [Strangler-fig V3→V5 migration (not a big-bang rewrite)](#16--strangler-fig-v3v5-migration)
17. [Java + Spring (not Go / Node / Python) on the hot path](#17--java--spring-on-the-hot-path)
18. [Resilience4j circuit breakers + 5 resilience layers](#18--resilience4j--5-resilience-layers)
19. [Active-active two Azure regions, parallel deploy](#19--active-active-two-azure-regions)
20. [Everything tunable via runtime config (CCM), not redeploy](#20--everything-tunable-via-runtime-config)

---

## Concurrency & threading

### 1. — Callable + Future in Pre-Scatter

**The 30-second version:** Pre-Scatter runs exactly **5 fixed, independent context fetches** (eligibility, delivery slots, geo/nearby nodes, existing inventory holds, in-home stores). I used plain `Callable` + `Future` on a bounded pool — not the fancier `CompletableFuture`.

| | |
| --- | --- |
| **Problem** | Before I can score anything, I need 5 pieces of context. Fetched sequentially they cost ~130ms; the whole request budget is <100ms. |
| **Constraint** | Fixed, small, known task count. No task depends on another. No fan-out, no chaining. |
| **Why `Callable`/`Future`** | Simplest tool that fits. Submit 5 `Callable`s, block on 5 `Future.get(timeout)`, done. ~130ms → ~40ms. Readable, obvious, easy to reason about. |
| **Why not `CompletableFuture`** | `CompletableFuture` earns its complexity when you need **composition** (chain A→B→C), **timeout-racing**, or **per-task fallback across a large dynamic fan-out**. Pre-Scatter has none of those. Using it here would be complexity for its own sake. |
| **Trade-off accepted** | If a Pre-Scatter fetch fails, the whole request fails fast (no per-task fallback here). That's *acceptable* because these 5 are prerequisites — without eligibility/geo there's nothing to source anyway. |

> **Pushback: "Why not use `CompletableFuture` everywhere for consistency?"**
> "Consistency of *tools* isn't a goal — fit is. Pre-Scatter is 5 fixed independent tasks; `Callable`+`Future` expresses that in the least code. The orchestrator is a 50–100-way dynamic fan-out that needs timeout-racing and per-branch fallback — *there* `CompletableFuture` earns its keep. Using the heavier abstraction where it buys nothing is how you get code that's hard to read for no reliability gain."

---

### 2. — CompletableFuture in the orchestrator

**The 30-second version:** The Distribute phase solves **50–100+ (item × node) combinations in parallel**, each on its own `CompletableFuture`, each with `.exceptionally()` fallback and a per-future timeout.

| | |
| --- | --- |
| **Problem** | Score dozens-to-hundreds of combinations in parallel, under a hard <100ms budget, where *any* single downstream call can be slow or fail. |
| **Constraint** | One slow/failed branch must **not** fail or stall the whole promise. I need timeout-per-branch and fallback-per-branch, composed non-blockingly. |
| **Why `CompletableFuture`** | It gives me exactly three things `Callable`/`Future` can't cheaply: (1) **`.exceptionally()`** per branch → a failed combo returns a conservative pre-computed result instead of throwing; (2) **timeout-racing** via `applyToEither(failAfter(timeout))` → a branch that blows its budget is abandoned; (3) **non-blocking `thenCombine`** in Gather → I reduce results as they arrive instead of blocking on each `.get()`. |
| **Why not `Callable`/`Future`** | `Future.get()` is blocking — to get per-branch timeout+fallback over 100 futures I'd hand-roll the exact machinery `CompletableFuture` already provides, with more bugs. |
| **Trade-off accepted** | More code paths — every branch has a fallback path to maintain and test — and I must **manually propagate ThreadLocal context** onto pool threads (see pushback). That complexity is the price of "one slow node can't break the promise." |

> **Pushback: "Doesn't per-future fallback hide real failures?"**
> "It would if I fell back silently and moved on — so I don't. Every fallback increments a **fallback-rate metric** and the branch's rejection reason is a **typed enum**, queryable in observability. The customer gets a correct-but-conservative answer *and* on-call sees the fallback rate climb. The alternative — failing the whole request when one of 100 nodes is slow — trades a customer-invisible degradation for a customer-visible outage. Wrong trade at retail scale."

> **Pushback: "What breaks with `CompletableFuture` that a junior would miss?"**
> "ThreadLocal context. `CompletableFuture` dispatches to pool threads that **don't inherit** the submitting thread's ThreadLocals — so correlation-id and market context silently vanish on the worker thread, and every async log loses its correlation id. I capture the context *before* submit and set it on the worker inside the task. It's the kind of bug that's invisible in a unit test and agonizing in a 2am production trace."

---

### 3. — SynchronousQueue (not LinkedBlockingQueue)

**The 30-second version:** The orchestrator executor uses a **zero-capacity `SynchronousQueue`**. When all threads are busy, a new task is **rejected immediately** and its branch falls back — instead of piling up in a queue.

```
Core pool: 300 threads   Max pool: 800 (runtime-tunable)   Queue: SynchronousQueue (capacity 0)
```

| | |
| --- | --- |
| **Problem** | Under a traffic spike, the scatter pool saturates. What should happen to task #801? |
| **Constraint** | This is a **latency-sensitive read path** with a hard SLA. A response that arrives *after* the customer's timeout is worthless — worse than a fast degraded one. |
| **Why `SynchronousQueue`** | Capacity 0 means "hand the task directly to a free thread or reject it *now*." On rejection, `exceptionally()` fires and the branch returns its fallback in <5ms. Latency stays **bounded** no matter the load. |
| **Why not `LinkedBlockingQueue`** | A 200-deep queue × ~60–100ms/task = **12–20 seconds** of hidden latency. The customer times out, retries, and the retry adds *more* load → death spiral. An unbounded queue also **masks a capacity problem** — the pool looks "fine" while latency silently explodes. |
| **Trade-off accepted** | Under overload I **reject work rather than attempt all of it**. Some branches get a conservative fallback instead of the optimal node. I chose *bounded latency + visible back-pressure* over *best-effort completeness*. |

> **Pushback: "Rejecting requests sounds worse than making them wait."**
> "For *this* domain, waiting is the worse failure. A promise date that arrives after the page has timed out is a wasted computation and a frustrated customer. A conservative-but-instant date keeps the funnel moving. And rejection is *visible* — it spikes a metric — whereas a deep queue hides the overload until it's a full outage. The queue doesn't remove the load; it just delays the pain and compounds it with retries."

> **Pushback (the senior follow-up): "Would you always fail-fast like this?"**
> "No — it's domain-specific, and saying so is the point. On a **latency-sensitive read path**, fail-fast with a silent conservative fallback is right. In a **financial write path**, I'd keep the bounded-queue principle but flip the fallback: fail-fast with an **explicit error and a retry instruction**, not a silent default — because in payments a silent fallback can mean a wrong balance. Same principle, opposite fallback semantics, chosen by domain."

---

### 4. — Thread-pool isolation (bulkheads)

**The 30-second version:** Each external dependency (inventory service, date service, distributed cache, inventory-query service) gets its **own dedicated thread pool**. A slow dependency can exhaust *its* pool without starving the others.

| Dependency | Pool size | Timeout |
| --- | --- | --- |
| Inventory service | 50–100 | 300ms |
| Date service | 30–60 | 200ms |
| Distributed cache | 10–20 | 50ms |
| Inventory-query / eligibility | 20–30 | — |

| | |
| --- | --- |
| **Problem** | If one downstream (say the inventory service) goes slow, its calls back up. On a **shared** pool, those slow calls consume every thread and *every other* dependency's calls starve too — one slow service takes down the whole engine. |
| **Why isolation** | This is the **bulkhead pattern** (named after ship compartments — a hull breach floods one compartment, not the whole ship). A slow inventory service exhausts only the inventory pool; date-service calls keep flowing on their own pool. |
| **Why not one shared pool** | Simpler to configure, but couples the failure domains — the exact thing you're trying to prevent. |
| **Trade-off accepted** | More pools to size and tune, and total thread count is higher than one shared pool. I pay memory + config complexity for **failure isolation**. |

> **Pushback: "How do you size these pools?"**
> "Little's Law as a starting point — pool size ≈ target throughput × average latency — then tune against real traffic via runtime config so I can adjust without a redeploy. The inventory pool is largest because it's the highest-volume, highest-latency dependency; the cache pool is tiny because reads are microseconds. And every pool fronts a **circuit breaker**, so a fully-exhausted pool trips the breaker and serves fallback instead of endlessly rejecting."

---

## Messaging & ingestion

### 5. — Kafka (not REST / RabbitMQ / SQS)

**The 30-second version:** ~18 domain teams (offers, capacity, carriers, inventory, slots…) publish changes; the ingestion tier consumes them via **40+ Kafka listeners** and hydrates Cassandra + the cache layer. Kafka — not synchronous REST, not a traditional broker.

| | |
| --- | --- |
| **Problem** | Continuously ingest millions of records/day of reference data from ~18 independent upstream teams, durably, with replay, without coupling their uptime to mine. |
| **Why Kafka** | (1) **Durable log** — events persist; if I'm down for an hour I resume from my committed offset, nothing lost. (2) **Replay** — reset offset to reprocess a bad window; a queue that deletes on ack can't do this. (3) **Decoupling** — producers don't know or wait for me. (4) **Partitioned parallelism** — scale consumers with partitions. (5) **Multi-consumer** — several independent consumer groups read the same topic at their own pace. |
| **Why not REST (push)** | Synchronous coupling — every upstream would have to retry against *my* availability, and a spike from them becomes a spike I must absorb in real time with no buffer. |
| **Why not RabbitMQ / SQS** | Both are **queues, not logs** — a message is deleted once acked, so there's no replay and no independent re-consumption. My reliability model *depends* on "reprocess the last 4 hours" during incidents. (Full matrix in [KAFKA-VS-MQ-COMPARISON.md](KAFKA-VS-MQ-COMPARISON.md).) |
| **Trade-off accepted** | Kafka is **heavier to operate** (partitions, consumer groups, offset management, rebalance semantics) than a managed queue, and ordering is only *per-partition*. I accept operational weight for durability + replay + decoupling. |

> **Pushback: "Isn't Kafka overkill? A queue is simpler."**
> "It would be overkill if I only needed at-least-once delivery. But two of my hard requirements are **replay** and **independent re-consumption** — during a bad-data incident I reprocess a time window, and multiple consumer groups read the same offer stream for different purposes. A queue deletes on ack, so it can't do either. The operational weight buys capabilities I actually use in on-call every month, not theoretical ones."

---

### 6. — Avro + Schema Registry (not JSON)

**The 30-second version:** Messages are **Avro** (compact binary) with a central **Schema Registry**, not JSON.

| | |
| --- | --- |
| **Problem** | ~18 teams evolve their event shapes independently. A producer adding a field must not break 40+ consumers. And at millions of records/day, payload size is real money in network + storage. |
| **Why Avro + Registry** | (1) **Schema enforcement** — the registry rejects an incompatible schema at publish time, so a breaking change is caught by the producer, not by my consumer crashing at 2am. (2) **Safe evolution** — backward/forward-compatible field adds are validated centrally. (3) **Compactness** — binary Avro is far smaller than JSON, which matters at this volume. |
| **Why not JSON** | Self-describing but unvalidated — a producer typo or field rename becomes a runtime deserialization failure across every consumer, discovered in production. And JSON is bulkier on the wire. |
| **Trade-off accepted** | Avro is **not human-readable** — you can't `cat` a message to debug it; you need the schema + tooling to decode. I pay debuggability for safety + size. |

> **Pushback: "JSON is easier to debug — why fight it?"**
> "Debuggability is a real cost and I felt it — you can't eyeball an Avro payload. But the failure it prevents is worse: with JSON, an upstream renaming a field ships fine and breaks 40 consumers silently in prod. The Schema Registry moves that failure **left** — the incompatible producer is rejected at publish. I'd rather need a decode tool than run an incident. And OpenObserve logs the decoded record anyway, so the debug gap is small."

---

### 7. — At-least-once + idempotent = effectively-once

**The 30-second version:** Kafka gives **at-least-once** (on rebalance/restart you *will* reprocess). I make consumers **idempotent** via Cassandra upserts, so reprocessing is harmless — the net effect is **effectively-once**, without paying for true exactly-once.

```
🎨 Visual — why "commit after processing" + idempotency is the safe combo

  commit-BEFORE-process, then crash:            commit-AFTER-process, then crash:
  ┌──────────────────────────────┐              ┌──────────────────────────────┐
  │ poll → commit offset ✓        │              │ poll → process → write ✓      │
  │ → crash before write ✗        │              │ → crash before commit ✗       │
  │ → record GONE forever         │              │ → record REPROCESSED on restart│
  │   (silent data loss)          │              │   → idempotent upsert = no-op │
  └──────────────────────────────┘              └──────────────────────────────┘
        ❌ loses data                                  ✅ safe (duplicate is harmless)

  KEY INVARIANT:
     Never commit an offset for a record you haven't durably processed.
     Duplicates are recoverable; lost records are not. Idempotency makes duplicates free.
```

| | |
| --- | --- |
| **Problem** | Exactly-once across a distributed consumer + external DB is expensive and fragile. But I *cannot* lose an offer update (item silently non-transactable) or double-apply one. |
| **Why at-least-once + idempotent** | Commit the offset **after** the Cassandra write succeeds. On crash, the record is reprocessed — and because writes are **idempotent upserts keyed by offer ID**, reprocessing produces the identical row. Same end state as exactly-once, none of the coordination cost. |
| **Why not exactly-once** | Kafka's transactional exactly-once covers Kafka→Kafka; it doesn't extend cleanly to an external Cassandra write, and the two-phase coordination adds latency and failure modes. Idempotency gets me the same guarantee more simply. |
| **Trade-off accepted** | Every consumer's write path **must** be genuinely idempotent — that's a design constraint enforced in code review, not a freebie. Get it wrong (a non-idempotent counter increment) and replay corrupts data. |

> **Pushback: "How do you *know* your consumers are idempotent?"**
> "It's a review gate, not a hope. Every write is an upsert keyed by a stable business key (offer ID + version), never a read-modify-write or blind increment. Because replay is a first-class operation for us — we reprocess windows during incidents — idempotency gets exercised constantly, so a non-idempotent write shows up as a data mismatch fast, not years later."

---

### 8. — Consumer-group-per-pipeline + offer-ID partition key

**The 30-second version:** Each logical pipeline is its **own consumer group** (independent offsets, independent lag, independent scaling). Producers key by **offer ID**, so all events for one offer land on one partition and stay **ordered relative to each other**.

| | |
| --- | --- |
| **Problem** | (a) Different pipelines process at very different rates and must not block each other. (b) Two updates to the *same* offer must apply in order, or a stale update can overwrite a fresh one. |
| **Why per-group** | Independent consumer groups = independent offsets + lag. One slow pipeline doesn't stall another; each scales to its own partition count. |
| **Why offer-ID key** | Kafka guarantees ordering **within a partition**. Keying by offer ID routes all of one offer's events to one partition → they're processed in order. I don't need global ordering (offer A vs offer B don't interact) — only **per-offer** ordering, which the partition key gives me for free. |
| **Trade-off accepted** | A **hot key** (one wildly-updated offer) can skew load onto one partition. Acceptable because offer update rates are broadly even and no single offer dominates. |

> **Pushback: "What if one offer is updated far more than others — hot partition?"**
> "Real risk with key-based partitioning. In practice offer update rates are even enough that no partition dominates, and I monitor per-partition lag to catch skew. If a genuine hot key emerged, the fix is a composite key (offer ID + sub-shard) that preserves per-offer ordering where it matters while spreading load — but I'd only add that complexity once the data showed I needed it, not preemptively."

---

### 9. — One JAR, 18 deployments

**The 30-second version:** All ~18 ingestion deployments are the **same build artifact**. A JVM flag (`runAs`) selects which listeners each deployment activates. One codebase, one Sonar scan, one bug fix — 18 differently-configured runtimes.

```
                 one WAR / build artifact
                          │
        ┌─────────┬───────┼───────┬─────────┐
   runAs=A    runAs=B    runAs=C     runAs=D    … (18 total)
   120-200 pods  40 pods   30 pods    12 pods
   offer topics  transit    bulk jobs  event
                 topics                 stream
```

| | |
| --- | --- |
| **Problem** | 18 pipelines with different topics, pod counts, and JVM tuning — but sharing the same domain model, Avro schemas, and Cassandra write logic. |
| **Why one artifact** | (1) **One bug fix propagates to all 18** — no drift between copies. (2) **One Sonar/security scan**, one mental model, one place to reason about the write path. (3) Differences live in **KITT YAML** (which listeners, pod count, heap, topics), not in forked code. |
| **Why not 18 separate services** | 18 repos = 18 places for the same bug, 18 scans, 18 divergent copies of shared logic. Duplication without benefit — the pipelines aren't independent products, they're one product deployed 18 ways. |
| **Why not one big process running all listeners** | No blast-radius isolation — a poison-pill or memory spike in one pipeline would take down all of them. Separate deployments contain failure. |
| **Trade-off accepted** | The single artifact carries **all** listeners' code even though each deployment runs a subset — a slightly larger artifact and a shared classloader. Trivial cost for zero-drift + isolation. |

> **Pushback: "Isn't shipping code a deployment doesn't run wasteful / a security surface?"**
> "The unused listeners are inert — never wired up unless `runAs` selects them — so there's no runtime surface, just a few extra MB in the artifact. Against that I get a property that matters far more at 18 deployments: a fix or CVE patch lands **once** and every deployment gets it on the next roll. The alternative — 18 copies — is where you find one still vulnerable six months later. Zero-drift beats a lean artifact."

---

## Storage & caching

### 10. — Cassandra (not an RDBMS)

**The 30-second version:** The offer/reference store is **Cassandra**, not a relational DB.

| | |
| --- | --- |
| **Problem** | Millions of writes/day of reference data, read at massive scale across multiple regions, always keyed by a known ID (offer, node, zip). No complex joins in the hot path. |
| **Why Cassandra** | (1) **Write-optimized** — LSM-tree append design eats high write volume. (2) **Linear horizontal scale** — add nodes for throughput. (3) **Multi-region active-active** — masterless replication fits the two-region deployment. (4) **Predictable key-based reads** — my access is always "give me offer X," which is exactly Cassandra's sweet spot. |
| **Why not an RDBMS** | Single-writer scaling ceiling, harder multi-region active-active, and I don't need joins/complex transactions on this path — I'd pay for relational guarantees I don't use. |
| **Trade-off accepted** | **No joins, eventual consistency, query-first data modeling** — I design tables around read patterns and denormalize, rather than normalizing and joining. Give up relational flexibility for write throughput + horizontal scale. |

> **Pushback: "You gave up joins and strong consistency — isn't that risky?"**
> "It would be if the workload needed them, but it doesn't. Every hot-path read is 'fetch by known key,' never an ad-hoc join, so query-first modeling is a fit, not a sacrifice. And correctness-critical steps — reserving inventory — don't rely on Cassandra consistency at all; they call the live inventory service inline. Cassandra holds the high-volume reference data where a few seconds of staleness is fine and horizontal write scale is everything. I also run SQL where relational shape *does* fit — it's the right tool per dataset, not dogma."

---

### 11. — Hollow in-memory snapshots (not Redis)

**The 30-second version:** The hot path reads large reference datasets from **Hollow** — Netflix's open-source in-memory, memory-mapped, read-only dataset cache — at **sub-microsecond** latency with **zero GC pressure**. ~16 such caches. Not Redis.

| | |
| --- | --- |
| **Problem** | At 700K rpm the hot path needs large reference datasets (transit times, date precompute, pool configs, zip→node maps) on **every** request. Even a 1ms network cache hop × that volume is too slow and too much load. |
| **Why Hollow** | (1) **Sub-microsecond reads** — data is in the pod's memory, no network hop. (2) **Zero GC pressure** — stored **off-heap, memory-mapped**, so a multi-GB dataset doesn't create garbage or GC pauses that would blow the p95. (3) **Atomic versioned refresh** — a new snapshot is produced offline and swapped in via a single pointer update; readers never block. (4) **Pre-warmed on boot** — a pod is useful the moment it starts. |
| **Why not Redis** | Redis is a **network** cache — every read is a hop (~ms) and adds load to a shared server at 700K rpm. It also doesn't give the off-heap zero-GC property or the "whole dataset in local memory" access pattern. Redis is great for cross-pod shared *mutable* state — which is exactly what my **third** cache layer uses it for — but wrong for "huge read-only reference data on every request." |
| **Trade-off accepted** | Hollow is **read-only and batch-refreshed** — minutes of staleness between snapshots, and every pod holds a full copy in memory (RAM cost). I accept staleness + memory for latency + zero-GC. |

> **Pushback: "Why not just put it in Redis / a distributed cache?"**
> "Two reasons. Latency: Redis is a network hop per read — at 700K rpm that's both too slow and a thundering-herd on the Redis cluster. And GC: these are multi-GB datasets; on the JVM heap they'd cause GC pauses that violate the p95 outright. Hollow keeps them **off-heap and local**, so reads are sub-microsecond with zero garbage. The cost is staleness — but this is reference data that changes on the order of minutes, and I alert on snapshot age and ingestion lag to bound it. Where I *do* need coherent cross-pod state, I use a distributed cache — right tool per layer."

---

### 12. — Three cache layers (not one)

**The 30-second version:** Hot path reads from a **3-layer** cache, each layer solving a different problem — no single cache does all three jobs well.

```
🎨 Visual — three layers, three jobs

  Layer 1  Hollow (in-memory, off-heap, memory-mapped)
     sub-µs · zero GC · ~16 datasets · batch refresh (minutes stale OK)
     → huge READ-ONLY reference data: transit times, date precompute, pool configs
                              │
  Layer 2  Caffeine (in-process, pod-local)
     µs · lazy-fill · short TTL · per-pod (pods may briefly differ)
     → hot, frequently-read, tolerably-stale business signals
                              │
  Layer 3  Distributed cache (cross-pod, network)
     ms · coherent fleet-wide
     → dynamic operational signals that MUST be identical on every pod right now

  KEY INVARIANT:
     Latency ↑ as you go down; coherence ↑ as you go down.
     Put each datum in the highest layer whose staleness it can tolerate.
```

| | |
| --- | --- |
| **Problem** | Three different data shapes: huge slow-changing reference data; hot pod-local signals; and dynamic signals that must be *identical* across all pods immediately. |
| **Why three** | Each layer trades latency vs coherence differently. Forcing all three shapes into one cache means either paying network latency for data that doesn't need coherence (waste) or serving incoherent data that needed to be fleet-consistent (bug). |
| **Trade-off accepted** | **More moving parts** — three systems to operate, monitor, and reason about, and a mental rule for "which layer does this datum belong in." I pay operational complexity for latency + correctness fit per datum. |

> **Pushback: "Three caches is a lot to operate — why not consolidate?"**
> "Because the data has three genuinely different requirements and one cache can only optimize for one. Reference data wants sub-microsecond local reads and tolerates minutes of staleness → Hollow. Cross-pod operational state must be coherent *now* and tolerates a network hop → distributed cache. Collapsing them means either paying a network hop for data that never needed coherence, or serving stale data where I needed fleet-consistency. The complexity is real, but it's the minimum that fits the requirements — and each layer has its own staleness alert."

---

### 13. — Offline ML batch → Hollow

**The 30-second version:** The predictive delivery-time model runs **offline as a batch job** on the ML team's infra; its output is loaded into a Hollow snapshot and read sub-microsecond on the request path. **No inline model inference.**

| | |
| --- | --- |
| **Problem** | A machine-learned delivery-time prediction improves accuracy — but a model call in the request path adds latency and a new failure mode to a <100ms budget. |
| **Why offline batch → cache** | The model scores every relevant combination **offline**; MCSE just **looks up** the pre-computed prediction in Hollow at request time. The request path stays **deterministic** — no model latency, no model outage on the hot path. |
| **Why not inline inference** | An inline model call adds variable latency + a serving dependency that can time out mid-request; it puts ML serving on my latency budget and my on-call. |
| **Trade-off accepted** | Predictions are **as fresh as the last batch** (hours), not real-time, and there's a pipeline (offline job → GCS → cache generator → Hollow) to operate. I accept batch-freshness for a deterministic, model-outage-proof hot path. |

> **Pushback: "Real-time inference would be more accurate — why batch?"**
> "For delivery-time prediction the inputs move on the order of hours, not seconds, so batch freshness captures essentially all the accuracy. Against that, inline inference would put a variable-latency, separately-failing service directly on a 700K-rpm, sub-100ms path — one model hiccup becomes a customer-facing latency spike. Pre-computing offline and serving from cache keeps the hot path deterministic and takes ML serving off my latency budget entirely. It's the accuracy-vs-reliability trade, and at this scale reliability wins."

---

## Architecture & platform

### 14. — Modular monolith (not microservices)

**The 30-second version:** MCSE's read engine is a **modular monolith** — ~30 Maven modules, one deployable per market — not a swarm of microservices.

| | |
| --- | --- |
| **Problem** | A large domain (eligibility, scatter, date math, capacity, gather) that shares a rich domain model and must execute end-to-end in <100ms. |
| **Why modular monolith** | (1) **Latency** — scatter/gather across dozens of combinations happens **in-process**; as microservices, each phase would be a network hop and the <100ms budget is gone. (2) **Shared domain model** — modules share types directly; no serialization tax between phases. (3) **Cross-module refactoring** — change a shared type in one PR, compiler catches every caller. (4) Clear module boundaries still give separation of concerns without distribution cost. |
| **Why not microservices** | Every module boundary would become a network call — latency, serialization, partial-failure handling, and distributed debugging, all *inside a single request's hot path*. You'd pay the full microservices tax for zero benefit, because these modules aren't independently scaled or independently deployed products. |
| **Trade-off accepted** | The deployable is **large**; a change means redeploying the whole engine (per market). I give up independent per-module deploy/scale for in-process latency + refactorability. Where a component genuinely has a *different* profile (see next two entries), I *do* split it out. |

> **Pushback: "Microservices are the modern default — isn't a monolith legacy?"**
> "The modern default is *fit*, not a topology. My hardest constraint is a sub-100ms scatter/gather across dozens of combinations — doing that in-process is microseconds; doing it across microservices turns every phase into a network hop and blows the budget. I *do* split out components with a genuinely different profile — the date service is CPU-heavy calendar math, so it's separate and scales independently; ingestion is a separate tier entirely. So it's not monolith-vs-microservices dogma — it's a modular monolith on the latency-critical path and separate services where the profile differs. Distribution is a cost you pay when you get something for it."

---

### 15. — Read/write split

**The 30-second version:** Ingestion (**write side**) and sourcing (**read side**) are **separate tiers** with separate repos, separate scaling, separate SLOs. This is CQRS at the system level — Command (write) and Query (read) responsibilities split.

| | |
| --- | --- |
| **Problem** | The write side is async, throughput-bound, spiky (Kafka consumption, millions of records/day). The read side is synchronous, latency-bound, must hold <100ms p95. Utterly different SLOs. |
| **Why split** | Each tier scales on **its own** signal — read side on request RPS + CPU, write side on **Kafka consumer lag**. A write-side spike (bulk offer reload) can't steal capacity from the read hot path, and vice versa. Independent deploy cadence too. |
| **Why not one service** | Coupling a latency-critical read path to a throughput-spiky write path means an ingestion surge degrades customer-facing latency — the exact coupling you must avoid. |
| **Trade-off accepted** | The two sides communicate **through the data + cache layer**, not directly — so there's an eventual-consistency gap (freshness lag) between "event arrived" and "read side sees it." I bound it with a sub-minute freshness SLO + lag alerts. |

> **Pushback: "The read side can serve stale data then?"**
> "Yes, bounded — there's a freshness lag between an event landing and the read side seeing it, and I hold a sub-minute end-to-end SLO on it with consumer-lag and snapshot-age alerts. That staleness is the deliberate price of decoupling: it means a bulk ingestion surge can never starve the customer-facing latency path. And for the one place staleness is unacceptable — reserving inventory — the read side calls the live service inline rather than trusting cached state."

---

### 16. — Strangler-fig V3→V5 migration

**The 30-second version:** MCSE is migrating from a V3 to a V5 architecture **module-by-module** behind a boundary mapper — not a big-bang rewrite. V5 is the new *request envelope*; V3 remains the *internal contract* until each module migrates.

| | |
| --- | --- |
| **Problem** | Modernize a large, always-on, revenue-critical engine (multi-slot support, unified pre-scatter context) without a risky flag-day rewrite. |
| **Why strangler-fig** | New V5 traffic enters through a V5→V3 request mapper; downstream modules still expecting V3 keep working untouched. I peel **one module at a time** onto V5 per release. The V3 core stays stable while V5 grows around it — hence "strangler fig" (the vine that grows around a tree and gradually replaces it). |
| **Why not big-bang rewrite** | A simultaneous cutover of a 700K-rpm engine is a single enormous risk with no incremental validation and no easy rollback. |
| **Trade-off accepted** | For the migration window I maintain **two contracts** (V5 envelope + V3 internals) and a mapping layer between them — extra code and cognitive load — in exchange for zero-flag-day risk and per-module rollback. |

> **Pushback: "Maintaining V3 and V5 at once is tech debt — why not just cut over?"**
> "It's *deliberate, bounded* debt — a mapping layer that exists only during migration and shrinks every release as another module moves to V5. The alternative, a big-bang cutover of a revenue-critical 700K-rpm engine, concentrates all risk into one irreversible moment. Strangler-fig lets me migrate one module, validate it in production, and roll back just that module if it misbehaves. I'll pay temporary dual-contract cost to never bet the whole engine on one deploy."

---

### 17. — Java + Spring on the hot path

**The 30-second version:** The engine is **Java + Spring**, chosen for mature JVM concurrency, JIT-optimized steady-state throughput, and G1 GC control — not Go, Node, or Python.

| | |
| --- | --- |
| **Problem** | A CPU- and concurrency-heavy scatter/gather that fans out to 50–100 parallel tasks per request, running hot for years at 700K rpm. |
| **Why Java/Spring** | (1) **Mature concurrency** — `CompletableFuture`, executors, the `java.util.concurrent` toolkit are exactly the fan-out/timeout/fallback primitives I need. (2) **JIT** — a long-lived server process gets aggressively optimized to near-native at steady state. (3) **GC control** — G1 (and off-heap Hollow) let me tune for low pause under load. (4) **Ecosystem** — Kafka, Cassandra, Hollow, Resilience4j are all first-class on the JVM. |
| **Why not Go/Node/Python** | Node/Python are single-threaded-ish and weaker for CPU-bound parallel fan-out; Python especially (GIL) is wrong for this. Go is a legitimate option (great concurrency) but the team, the ecosystem (Hollow, Kafka/Cassandra tooling), and the existing codebase are JVM — rewriting buys nothing here. |
| **Trade-off accepted** | JVM **memory footprint** and **GC tuning** effort are higher than a Go binary; startup is slower. I accept heavier runtime + tuning for concurrency maturity + ecosystem fit + JIT throughput. |

> **Pushback (the one you flagged): "Why Java and not Python — you used Python in college?"**
> "I did use Python in college and I'm comfortable in it — for scripting, data work, and ML glue it's excellent, which is exactly why the *ML side* of this system is Python. But the hot path is CPU-bound parallel fan-out at 700K rpm, and Python's GIL makes true multi-core parallelism awkward and its interpreted steady-state throughput is well below the JIT-optimized JVM. The right lesson isn't 'Java good, Python bad' — it's match the runtime to the workload: JVM for the concurrent low-latency engine, Python for the offline ML batch. I've deliberately used both in the same system for what each is best at."

---

### 18. — Resilience4j + 5 resilience layers

**The 30-second version:** Circuit breaking uses **Resilience4j** (the maintained library) rather than the deprecated Hystrix, and resilience is layered **five deep** so no single failure is unguarded.

```
Layer 1  Runtime feature flags   → kill any feature instantly, per-market, zero restart
Layer 2  Circuit breakers        → wrap every external call; trip → fast-fail → fallback
Layer 3  Thread-pool bulkheads   → one slow dependency can't starve the others
Layer 4  SynchronousQueue        → fail-fast under overload; bounded latency
Layer 5  In-memory Hollow cache  → survive a Cassandra outage; 99%+ of reads served locally
```

| | |
| --- | --- |
| **Problem** | A hot path with many external dependencies (inventory, date, cache, eligibility) — any of which can be slow or down — must always return *something* within SLA. |
| **Why layered** | Defense in depth: a failure that slips past one layer is caught by the next. A slow inventory service → its bulkhead contains it → its breaker trips → fallback serves last-known-good from Hollow → feature flag can kill the feature entirely if needed. No single point where one failure is unhandled. |
| **Why Resilience4j not Hystrix** | Hystrix is **end-of-life** (no longer actively developed). Resilience4j is the modern, maintained, lightweight, functional-style replacement with the same circuit-breaker/bulkhead/rate-limiter primitives. |
| **Trade-off accepted** | Five layers is **more configuration and more to reason about** than "just add retries." I accept configuration surface for the guarantee that no dependency failure is unguarded. |

> **Pushback: "Five layers sounds like over-engineering."**
> "Each layer catches a failure the others can't. A circuit breaker doesn't help if one slow call has already consumed every thread — that's what the bulkhead prevents. A bulkhead doesn't bound latency under a broad spike — that's the fail-fast queue. None of them help if the whole DB is down — that's the in-memory cache serving last-known-good. They're not redundant; they cover *different* failure modes. At 700K rpm on the checkout path, an unguarded failure mode is a revenue incident, so the config cost is easily justified."

---

### 19. — Active-active two Azure regions

**The 30-second version:** MCSE runs **active-active across two Azure regions**; a global load balancer health-checks both and routes around a regional failure. Deploys go to both regions **in parallel** to avoid version skew.

| | |
| --- | --- |
| **Problem** | A checkout-critical service can't have a regional cloud incident take it fully offline, and can't serve **inconsistent** responses from two regions running different versions. |
| **Why active-active** | Both regions serve live traffic; if one has an incident, the load balancer routes all traffic to the other with no cold-start. Higher availability than active-passive (no failover lag, and the standby isn't idle capacity you're paying for but not using). |
| **Why parallel deploy** | If I deployed region A then region B, for the gap between them the two regions run **different versions** → a customer could get inconsistent promise responses depending on routing. Deploying both simultaneously eliminates the skew window; total deploy time is the same as sequential would be for one region. |
| **Trade-off accepted** | Active-active demands the data layer replicate cross-region (Cassandra masterless handles this) and code tolerate serving from either region — more design constraint than active-passive. Parallel deploy means a bad release hits both regions at once, so I lean hard on **canary + auto-rollback** to catch it in the first 10% ramp. |

> **Pushback: "Parallel deploy to both regions — isn't that riskier? A bad build hits everyone at once."**
> "That's exactly why parallel deploy is paired with progressive canary. A new version ramps 10% → 25% → 50% → 100% **within each region**, with automated analysis of error rate, latency, and CPU at every step and **automatic rollback** if any threshold trips — a bad deploy is caught and reverted in the first ~10–15 minutes at 10% traffic, before it's broadly live. The version-skew risk of *sequential* deploy is subtler and always-present: two regions serving different logic to customers who don't know which they'll hit. I'd rather have one well-guarded ramp than a guaranteed skew window."

---

### 20. — Everything tunable via runtime config

**The 30-second version:** Timeouts, thread-pool sizes, feature flags, guardrail thresholds — essentially all operational behavior — live in **runtime config (CCM)**, changeable in seconds **without a restart or redeploy**, scoped per-market/per-tenant.

| | |
| --- | --- |
| **Problem** | Production needs tuning (a pool too small, a timeout too tight) and features need instant kill-switches — but a redeploy of a 700K-rpm engine takes ~45–60 min and is itself a risk. |
| **Why runtime config** | (1) **Instant response** — widen a pool or flip a flag in seconds during an incident, no deploy. (2) **Percentage + per-market rollout** — enable a feature for 1% → 10% → 100%, or one market only. (3) **Per-key rollback** — a bad config value reverts in ~30s. |
| **Why not redeploy for changes** | A 45–60-min deploy cycle is far too slow for incident response, and shipping a code change just to bump a timeout is heavy and risky. |
| **Trade-off accepted** | Config is **powerful enough to cause an outage** — a wrong value (e.g. a cutoff time typo) silently changes behavior with no error, no latency change, no circuit trip. This is the **scariest failure mode** precisely because it's invisible to infra dashboards. I mitigate with staged config rollout, per-key rollback, and **business-metric alerting** (promise-date distribution, transactability rate) — not just system-metric alerting. (Detail in [MCSE-FEATURES-AND-FAILURE.md](MCSE-FEATURES-AND-FAILURE.md).) |

> **Pushback: "Config that can take down prod without any error sounds dangerous."**
> "It's the sharpest double-edged tool in the system, and I treat it that way. The danger is exactly that a bad config value throws no exception, spikes no latency, trips no breaker — every infra dashboard stays green while the system quietly does the wrong thing. So the mitigation can't be infra alerting; it has to be **business-metric** alerting — if the promise-date distribution suddenly shifts or transactability drops in one market, that's the signal. Combined with staged rollout (1% first) and seconds-fast per-key rollback, the blast radius is small and the recovery is fast. The power to fix prod in seconds is worth it — but only *with* that safety net, not without."

---

## 🗺️ How to drill this file

1. **Cover the right column, read the decision name, say the four-step answer out loud** (Problem → Why → Why-not → Trade-off). If you can't, re-read that row.
2. **Then have someone read you the pushback** and answer cold. The pushback answers are where the HM round is won or lost — first answers are usually fine; it's the *follow-up* that separates levels.
3. **Always land the Trade-off line.** "I used X" is junior. "I used X and accepted cost Y for benefit Z, and here's how I bounded Y" is senior.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 28, 2026 | **File created.** 20 decisions ported and enriched from project-update `WHY_THESE_CHOICES` + knowledge layers 10/11/12, each as Problem→Constraint→Why→Why-not→Trade-off with a scripted pushback + answer. Confidentiality-scrubbed to plain-English concepts. |
