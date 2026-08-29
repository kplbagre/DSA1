# MCSE — Project Deep-Dive (understand your own system cold)
### The narrative you reason FROM in a project deep-dive — architecture first, then a drillable Q&A

> **What this is:** A confidentiality-scrubbed technical deep-dive of the platform you own, taught so you reason from *architecture*, not memorized answers. Read it until you can **draw the system from memory** and answer the follow-ups in Part 6 cold.
>
> **The three-file set** (read in this order): this file (architecture) → [MCSE-DECISION-LOG.md](MCSE-DECISION-LOG.md) (every "why X not Y" + pushbacks) → [MCSE-FEATURES-AND-FAILURE.md](MCSE-FEATURES-AND-FAILURE.md) (complex features + failure modes). Behavioral stories → [MCSE-interview-stories.md](MCSE-interview-stories.md); pitch scripts → [MCSE-PITCHES-AND-CROSSQS.md](MCSE-PITCHES-AND-CROSSQS.md).
>
> ⚠️ **Confidentiality:** internal service codenames, config-key names, and repo/page IDs are replaced with plain-English concepts. Describe the *concept*, never the internal identifier, in the room.
>
> 🌉 **Jargon rule:** the interviewer doesn't know Walmart. Translate live — "our promise & sourcing engine," "our inventory service," "Netflix's open-source in-memory cache." Never drop an internal acronym without its plain-English gloss.

---

## 🧾 Table of Contents

1. [What MCSE Is (the one-paragraph mental model)](#1--what-mcse-is)
2. [The Request Pipeline — Scatter / Distribute / Gather](#2--the-request-pipeline)
3. [How It's Actually Built — the concurrency mechanics](#3--how-its-actually-built)
4. [The Cache Data Plane — why three layers](#4--the-cache-data-plane)
5. [The Kafka Ingestion Tier — the Salesforce bridge](#5--the-kafka-ingestion-tier)
6. [What I Owned (be specific — "I", not "we")](#6--what-i-owned)
7. [Salesforce Bridges (Signup & ISV / SMTS)](#7--salesforce-bridges)
8. [Interview Q&A — say-this → pushback → answer](#8--interview-qa)
9. [Draw-From-Memory Checklist](#9--draw-from-memory-checklist)

---

## 1. — What MCSE Is

> Every time a shopper views an item or adds it to a cart, something has to decide **where it ships from** and **when it will arrive** — the delivery date on the product page. That's MCSE: Walmart's **promise & sourcing engine**. It runs on the hot path of retail checkout — **~700K requests/min, sub-100ms p95** — across multiple markets (US, Canada, Mexico, Chile) and both Walmart and Sam's Club.

**Why it's hard (the framing that lands):** a *wrong* answer is worse than a *slow* one. Promising a date we can't keep breaks a customer promise — so the system is built so that under stress it returns a conservative, correct fallback rather than a confidently wrong date. Everything downstream (the resilience layers, fail-fast, degradation hierarchy) follows from that one principle.

**The two halves of the system** (this is CQRS — Command Query Responsibility Segregation — at the system level: split the write path from the read path because they have opposite requirements):

```
Domain teams (offers, capacity, carriers, inventory, slots …)   ~18 teams
        │  Kafka events
        ▼
  WRITE SIDE  — ingestion tier  (async, throughput-bound, spiky)
  40+ Kafka listeners → Cassandra + in-memory cache
        │  (data pre-hydrated, read at request time)
        ▼
  READ SIDE  — the sourcing engine  (sync, latency-bound, <100ms p95)
  ~700K rpm · scatter/gather · never calls slow upstreams inline
        │  REST
        ▼
  Upstream promise-aggregation service → Search / Item Page / Cart / Checkout
```

- **Write side (ingestion):** consumes events from ~18 domain teams, lands them into Cassandra + an in-memory cache layer. Millions of records/day. Scales on **Kafka consumer lag**.
- **Read side (sourcing):** on each request, reads that pre-hydrated data (never hits the slow upstreams inline) and computes the best fulfillment option + delivery date in <100ms. Scales on **request RPS + CPU**.

**Why the split matters:** the two sides have utterly different SLOs, so they're separate tiers that scale independently — a bulk ingestion surge can never steal capacity from the customer-facing latency path. (Full reasoning: [MCSE-DECISION-LOG.md #15](MCSE-DECISION-LOG.md#15--readwrite-split).)

---

## 2. — The Request Pipeline

The read path is a **fork-join** (fan out work in parallel, then join the results) over every plausible (item × fulfillment-node) combination, under a tight latency budget.

```
Customer: "when do my 3 items arrive?" (zip 72712)
        │ REST → router (Apache Camel — a routing/integration layer)
        ▼
┌── PRE-SCATTER (~40ms) ───────────────┐  5 independent context fetches, in parallel
│  eligibility · slots · geo · holds · │  (was ~130ms sequential → ~40ms parallel)
│  in-home stores                      │  → Callable + Future on a bounded pool
└──────────────┬───────────────────────┘
               ▼  SourcingContext (enriched, immutable inputs — assembled ONCE)
┌── SCATTER (~5ms) ────────────────────┐  enumerate every combination
│  3 items × ~20 nodes = ~60 combos    │
└──────────────┬───────────────────────┘
               ▼
┌── DISTRIBUTE (~15ms) ────────────────┐  all ~60 solved in parallel
│  CompletableFuture per combo,        │  per-future timeout + .exceptionally() fallback
│  zero-capacity executor = back-press. │  bounded executor as fail-fast back-pressure
└──────────────┬───────────────────────┘
               ▼  ~60 individual results
┌── GATHER (~5ms) ─────────────────────┐  reduce → best option per item
│  thenCombine → multi-objective pick  │  (cost vs speed vs capacity)
└──────────────┬───────────────────────┘
               ▼
   Response: {TV: Dec 4, Phone: Dec 3, Shoes: Dec 3}   total ~65ms
```

**Steps in plain English:**

1. **Pre-scatter** — fetch the 5 context pieces you need before you can score anything (eligibility, delivery slots, geo/nearby nodes, existing inventory holds, in-home stores). None depend on each other → run concurrently. **Assembled once, before fan-out** — previously each parallel branch re-fetched this context; assembling it once and sharing it is a key optimization.
2. **Scatter** — enumerate every (item × candidate node) combination.
3. **Distribute** — solve each combination in parallel, one `CompletableFuture` each, on a bounded executor, each with its own timeout + fallback.
4. **Gather** — `thenCombine` the results as they arrive and pick the best option per item via a multi-objective function (cost, speed, capacity).

**The concurrency model (the senior detail — expanded in §3):**
- **`CompletableFuture`** with **`.exceptionally()` on every future** → one slow/failed downstream can't fail the whole request; it falls back to a conservative pre-computed result.
- **`SynchronousQueue` (zero-capacity)** on the executor → **fail-fast back-pressure**: under overload, reject immediately rather than queue unboundedly (which hides capacity problems and makes latency unpredictable).
- **ThreadLocal context capture** — request context (correlation id, market) rides a ThreadLocal, but `CompletableFuture` pool threads *don't* inherit it, so the context is **explicitly captured before submit and set on the worker thread**. Miss this and every async log loses its correlation id.

---

## 3. — How It's Actually Built

> This section exists so you can answer *"okay, but how is that implemented?"* — the follow-up that separates "I read about this" from "I built this." These are the real mechanics, scrubbed of internal names.

### 3.1 The thread pools (know the shapes and the *why* of each)

| Pool | Core | Max | Queue | Why this queue |
| --- | --- | --- | --- | --- |
| **Orchestrator (Distribute)** | 300 | 800 (runtime-tunable) | **SynchronousQueue** (capacity 0) | Latency-critical fan-out → fail-fast under overload, bounded latency |
| **Pre-Scatter tasks** | 50 | 50 (fixed) | LinkedBlockingQueue(100) | Exactly 5 fixed tasks, no spike risk → a small bounded queue is safe |
| **Gather** | 50 | 100 | SynchronousQueue | Merge phase — same fail-fast rationale |
| **Inventory calls** | 50 | 100 | SynchronousQueue | Bulkhead — isolates a slow inventory service |
| **Date-service calls** | 30 | 60 | SynchronousQueue | Bulkhead — isolates date math |

**The teaching point:** the queue choice is *per pool, by workload*. The Pre-Scatter pool runs a **fixed** 5 tasks with no spike risk, so a small bounded queue is fine. The orchestrator pool faces **unbounded spike** at 700K rpm, so it must fail-fast. Same engineer, opposite choice, because the workloads differ — that contrast is a great thing to volunteer. (Why SynchronousQueue: [MCSE-DECISION-LOG.md #3](MCSE-DECISION-LOG.md#3--synchronousqueue-not-linkedblockingqueue).)

### 3.2 Why each phase uses a different concurrency primitive

```
🎨 Visual — right primitive per phase

  PRE-SCATTER              DISTRIBUTE                    GATHER
  5 fixed tasks            50-100 dynamic tasks          reduce as results arrive
  independent              each can fail/timeout         non-blocking combine
  ────────────             ────────────────────          ──────────────────
  Callable + Future        CompletableFuture             thenCombine
  (block on .get)          (.exceptionally + timeout)    (compose, don't block)

  KEY INVARIANT:
     Match the concurrency primitive to the shape of the work.
     Fixed+simple → Callable/Future. Dynamic+failure-prone → CompletableFuture.
```

Full reasoning for each: [MCSE-DECISION-LOG.md #1](MCSE-DECISION-LOG.md#1--callable--future-in-pre-scatter) and [#2](MCSE-DECISION-LOG.md#2--completablefuture-in-the-orchestrator).

### 3.3 The ThreadLocal capture pattern (the bug juniors ship)

```java
// The problem: CompletableFuture runs the task on a POOL thread that does NOT
// inherit the submitting thread's ThreadLocals → correlation id vanishes.

// Capture context on the CALLING thread, BEFORE submitting:
RequestContext ctx = RequestContext.current();

CompletableFuture.supplyAsync(() -> {
    // Re-establish the captured context on the WORKER thread, first thing:
    RequestContext.set(ctx);
    try {
        return solveCombination(item, node);
    } finally {
        // Always clear — pool threads are reused; a leaked context poisons the next task:
        RequestContext.clear();
    }
}, orchestratorExecutor);
```

**Why the `finally` clear matters:** pool threads are **reused**. If you set a ThreadLocal and don't clear it, the *next* task on that thread inherits the *previous* request's context — cross-request contamination, one of the nastiest production bugs because it's non-deterministic. (This maps directly to Spring's `RequestContextHolder` / SLF4J `MDC` — same pattern, same footgun.)

### 3.4 The multi-objective gather (how "best" is decided)

Gather doesn't just pick the fastest node — it optimizes across **cost, speed, and capacity** simultaneously. Each candidate solution carries a score; the gather reduces to the best per item under the current objective weights (which are runtime-configurable per market). A rejected candidate carries a **typed rejection reason** (not a boolean), so an operator can query "why did node X lose?" instead of guessing.

---

## 4. — The Cache Data Plane

The hot path never calls the slow upstream services inline. It reads from a **3-layer cache**, each layer solving a different problem.

```
🎨 Visual — three layers, three jobs (latency ↑ and coherence ↑ as you go down)

  Layer 1  In-memory immutable snapshots (Hollow — Netflix open-source)
     sub-µs · memory-mapped OFF-HEAP (zero GC) · ~16 caches · batch refresh
     → huge READ-ONLY reference data: transit times, date precompute, pool configs
                              │
  Layer 2  In-process cache (Caffeine)
     µs · pod-local · lazy-fill · short TTL
     → hot, frequently-read, tolerably-stale business signals
                              │
  Layer 3  Distributed cross-pod cache
     ms (network hop) · coherent fleet-wide
     → dynamic operational signals that MUST be identical on every pod right now

  KEY INVARIANT:
     Put each datum in the HIGHEST layer whose staleness it can tolerate.
```

**Why not one cache?** Each layer trades differently:
- **Hollow** — huge datasets at sub-microsecond, **zero GC pressure** (memory-mapped off-heap so a multi-GB dataset creates no garbage), atomically version-swapped, pre-warmed on boot. Cost: batch refresh (minutes of staleness) — fine for slow-changing reference data.
- **Caffeine** — fast pod-local reads for hot keys, lazy-filled, seconds of staleness OK. Cost: pods may briefly differ.
- **Distributed** — when a value *must* be identical across every pod right now, pay the network hop.

### 4.1 How Hollow actually works — heap vs off-heap (understand this or the "in-memory" claim sounds hollow 😉)

A JVM process has **two kinds of memory** — and Hollow uses the one GC can't touch:

```
🎨 Visual — two kinds of "in-memory" in a JVM

  ┌─────────────────────────────────────────────────────┐
  │  JVM Process (running inside a K8s pod / container) │
  │                                                     │
  │  ┌─────────────────────────────────┐                │
  │  │  HEAP  (GC-managed)             │                │
  │  │  ├── Your Java objects          │                │
  │  │  │   (new HashMap(), beans,     │                │
  │  │  │    request/response objects)  │                │
  │  │  ├── GC must SCAN + PAUSE       │                │
  │  │  │   to clean up dead objects   │                │
  │  │  └── Multi-GB heap = long GC    │                │
  │  │      pauses = blown p95         │                │
  │  └─────────────────────────────────┘                │
  │                                                     │
  │  ┌─────────────────────────────────┐                │
  │  │  OFF-HEAP  (OS-managed via mmap)│  ← HOLLOW HERE│
  │  │  ├── Linux kernel maps a FILE   │                │
  │  │  │   directly into the process's│                │
  │  │  │   virtual address space      │                │
  │  │  ├── Java reads it like memory  │                │
  │  │  │   (sub-µs, same as heap)     │                │
  │  │  ├── GC does NOT know it exists │                │
  │  │  │   → ZERO GC pressure         │                │
  │  │  └── OS manages paging (loads   │                │
  │  │      pages from disk into RAM   │                │
  │  │      on demand)                 │                │
  │  └─────────────────────────────────┘                │
  │                                                     │
  │  Pod memory limit (KITT YAML) caps BOTH combined.   │
  │  You must size for: heap + Hollow's mmap footprint. │
  └─────────────────────────────────────────────────────┘

  KEY INVARIANT:
     Both are in RAM. Both are sub-µs reads.
     The difference: GC manages the heap (and pauses to clean it).
     The OS manages mmap (and GC never touches it).
     That's why Hollow can hold gigabytes with zero GC impact.
```

**How `mmap` works in one paragraph:** Linux has a system call called `mmap()` — "take this file on disk and map it into my process's virtual memory as if it were a byte array." The OS loads pages from the file into physical RAM on demand. Your Java code reads it at RAM speed. But because it's not on the JVM heap, the garbage collector **doesn't scan it, doesn't pause for it, doesn't know it exists**. That's the "zero GC pressure" claim — it's not marketing, it's a real OS-level mechanism.

**Do K8s pods have an OS?** Yes — a pod runs a container, and a container has a **shared Linux kernel** (from the host node) and its own minimal filesystem (usually Alpine Linux or Distroless). The `mmap()` call goes through the shared kernel. The pod's `resources.limits.memory` in KITT YAML caps the total memory (heap + off-heap + mmap), so when sizing pods you account for both.

### 4.2 How a snapshot refreshes — zero-downtime hot swap

**No pod restart.** The refresh is a hot swap while serving traffic:

```
🎨 Visual — Hollow refresh (zero-downtime, zero-GC)

  Offline job (Spark on GCP, scheduled by Airflow, every 4-6h)
      │  reads: Cassandra + upstream configs + BigQuery
      │  writes: versioned compressed blob → GCS bucket
      ▼
  Cache-generator service
      │  reads blob from GCS
      │  produces new Hollow snapshot (version N+1)
      │  announces to all MCSE pods: "N+1 is ready"
      ▼
  Every pod (simultaneously, WHILE SERVING 700K rpm):
      1. mmap() the NEW blob file    ← OS maps into address space
      2. Validate snapshot integrity  ← checksum
      3. Swap ONE atomic reference:   cache_ref = newSnapshot;
      4. Done. Next read uses N+1.    ← nanoseconds
      5. Old N drains (in-flight reads finish on old reference)
      6. Old blob unmapped / GC'd     ← no rush, happens naturally

  No restart. No downtime. No GC pause. Traffic never interrupted.

  KEY INVARIANT:
     The swap is a single pointer update (AtomicReference.set()).
     Readers never block — they either see old or new, never partial.
```

**Refresh cadence:** planned transit times every 4-6 hours; ML predictions daily; on-demand when upstream config changes. The staleness between refreshes is the explicit trade-off for sub-µs reads with zero GC.

### 4.3 What data lives WHERE — and why it's in that layer, not another

This is the question that proves you *designed* the cache, not just used it. The rule: **put each datum in the highest (fastest) layer whose staleness it can tolerate.**

| Data | Cache layer | Refresh | Staleness OK? | Why THIS layer |
| --- | --- | --- | --- | --- |
| **Transit times** (carrier × method × zip → days) | Hollow | Batch, 4-6h | ✅ changes every few hours | Huge dataset, read on every request, slow-changing |
| **Predicted transit times** (ML model output) | Hollow | Batch, daily | ✅ model retrains daily | Same — pre-computed, large, read-only |
| **Date/calendar precompute** (holidays, schedules) | Hollow | Batch | ✅ calendars change rarely | Large lookup tables |
| **Pool configs** (which nodes in which capacity pool) | Hollow | Batch | ✅ pool assignments change slowly | Read on every request for node eligibility |
| **Zip → eligible shipping nodes** | Hollow | Batch | ✅ geo mapping changes slowly | Huge geo dataset |
| **Capacity flip states** (node on/off) | Hollow **+ Kafka delta** | Hollow batch + near-real-time Kafka updates | ⚠️ moderate — flips happen daily | **Dual path**: Hollow for bulk, Kafka for urgent flips between refreshes |
| **Store/seller/distributor configs** | Hollow | Batch | ✅ | Reference data, large, slow-changing |
| **Hot seller's config** (read 5000×/min on one pod) | **Caffeine** | Lazy-fill on miss, short TTL (seconds) | ✅ seconds OK | Pod-local hot key; no need for fleet coherence |
| **Weather buffers** (dynamic, real-time) | **Distributed** | Near-real-time | ❌ must be fleet-consistent | Weather changes in real-time; every pod must see the same buffer NOW |
| **Operational signals** (cross-pod coherent state) | **Distributed** | Near-real-time | ❌ stale = incorrect behavior | One pod seeing a stale value = wrong sourcing decision |

**The interviewer question this answers:** *"Give me an example of data in each layer and why it's there, not in another."*

> "Transit times are in Hollow because they're a huge dataset read on every request but only change every few hours — I can tolerate batch staleness for sub-microsecond, zero-GC reads. Weather buffers are in the distributed cache because they change in real-time and every pod must see the same value NOW — I pay the network hop for coherence. And a hot seller config that's looked up thousands of times a second on one pod is in Caffeine — pod-local, microsecond, short TTL, self-expires. Each datum sits in the fastest layer whose staleness tolerance matches its update frequency."

**The trade-off you state explicitly:** eventual consistency (minutes stale on reference data) in exchange for latency — calling the inventory service on every request at 700K rpm is infeasible. You **protect against staleness** with consumer-lag alerts and cache-snapshot-age alerts, and only where correctness truly requires it (reserving inventory) do you call the live service inline. (Full "why not Redis": [MCSE-DECISION-LOG.md #11](MCSE-DECISION-LOG.md#11--hollow-in-memory-snapshots-not-redis); "why three": [#12](MCSE-DECISION-LOG.md#12--three-cache-layers-not-one).)

---

## 5. — The Kafka Ingestion Tier

The write side hydrates those caches from Kafka. **This is your strongest bridge to the Salesforce MQ role — lead the deep-dive here.**

**Shape:** one deployable artifact runs as ~18 separate deployments (a JVM flag selects which listeners each activates), consuming from ~18 domain teams via **40+ Kafka listeners**, landing data in Cassandra + an in-memory snapshot layer. Hot pipelines process **millions of records/day** on 100–200 pods each, holding a **sub-minute end-to-end freshness SLO**. (Why one artifact: [MCSE-DECISION-LOG.md #9](MCSE-DECISION-LOG.md#9--one-jar-18-deployments).)

**The three reliability principles you designed around (in order of damage prevented):**

1. **Never re-throw on a bad record.** An unhandled exception in a Kafka consumer **freezes the partition** — one poison-pill record stops all ingestion behind it. Instead: catch, log with context (offset, partition, payload hash), emit a metric, **commit past the bad offset**. You lose one event; the pipeline keeps running. For cache hydration, a stale-but-running cache beats a frozen one.
2. **Idempotent consumers.** Kafka is at-least-once — on rebalance/restart you *will* reprocess. Loading the same record twice must yield the same state → **idempotent upserts keyed by offer ID**. A design requirement enforced in review, not an accident. (Full: [MCSE-DECISION-LOG.md #7](MCSE-DECISION-LOG.md#7--at-least-once--idempotent--effectively-once).)
3. **Blast-radius isolation.** A consumer shared across two markets once reloaded *both* markets on a single market's trigger — one market's bad message could corrupt the other's cache. Fix: **separate consumer per market**, routed by business unit. Not duplication — containment.

**Back-pressure:** poller threads pull batches and dispatch onto a **bounded processor pool**; when it's full, the poller stops fetching and the system self-throttles. **Offset commit is after processing, not before** — commit-before + crash = permanent data loss; commit-after + crash = safe reprocess *because* consumers are idempotent. Kafka is the durable buffer; ingestion slows rather than dropping events. (The read-vs-write back-pressure contrast is a great answer — [MCSE-FEATURES-AND-FAILURE.md #10](MCSE-FEATURES-AND-FAILURE.md#10--back-pressure-on-both-sides).)

**Error handling chain per topic:** primary topic → error topic (retry with enhanced logging) → dead-letter topic (manual inspect + replay). Three replay modes: offset reset (window replay), dead-letter drain, and a surgical admin replay for specific event IDs — all safe **because** consumers are idempotent.

---

## 6. — What I Owned

Have 3–4 concrete, personally-owned pieces ready. Say **"I"**, not "we":

- **Canada multi-slot delivery (hero feature).** The engine assumed one delivery option per item; Canada needed Express *and* Standard simultaneously — a change to a **core response contract four upstream teams consume**. I designed it **additive + backward-compatible** (new `slots[]` array, old single-slot fields kept populated), refactored the reservation path to one inventory hold per slot with diff-based re-hold, shipped behind a **per-market flag**, and validated with **shadow traffic** (replayed production Canada traffic, diffed responses) before cutover. Zero breakage across the four consumers. (Full walkthrough + edge cases + pushbacks: [MCSE-FEATURES-AND-FAILURE.md #2](MCSE-FEATURES-AND-FAILURE.md#2--canada-multi-slot).)
- **Kafka ingestion tier ownership** (§5) — the reliability / back-pressure / blast-radius design.
- **Production debugging** — non-determinism (HashMap iteration order across pods), a peak-traffic CPU incident (logging anti-pattern), a serialization-overhead latency fix. Details in [MCSE-interview-stories.md](MCSE-interview-stories.md).
- **Event-driven observability (Trace V2)** — migrated tracing from single-blob-per-request to typed event-per-category streamed to a queryable store, via dual-write.

> For each, know: **what you decided, one trade-off, and the prevention/follow-through beat** (the two patterns in [../Craft/SALESFORCE-HM-BEHAVIORAL-DEEPDIVE.md](../Craft/SALESFORCE-HM-BEHAVIORAL-DEEPDIVE.md)).

---

## 7. — Salesforce Bridges

| MCSE reality | Salesforce Signup & ISV mapping |
| --- | --- |
| 40+ Kafka listeners, back-pressure, idempotency, blast-radius isolation | **Message Queue infrastructure** for high-volume signup flows — the JD's #1 product area |
| Multi-market isolation (US/CA/MX/CL), per-market flags, multi-tenant routing | **Multi-tenant SaaS** isolation (an org = a tenant) |
| 700K rpm hot path, fail-fast under overload, capacity-aware | **Org provisioning at scale**, capacity planning |
| Typed traces + metrics + queryable event store | **Production health & observability** (Splunk-equivalent) |
| Cassandra + SQL, query-first modeling, data lifecycle | **Relational DB** work — bridge honestly: relational tuning transfers; Oracle/Postgres specifics I'd ramp on |
| Additive, backward-compatible contract + shadow-diff | **API & integration layer** that won't break consumers |

> Lead with the **MQ/ingestion** story — it maps 1:1 to what the Signup team builds. Bridge the relational-DB gap honestly rather than overclaiming.

---

## 8. — Interview Q&A

> Drill these out loud. First answers are usually fine — the HM round is won or lost on the **follow-up**. Each has the say-this answer and the pushback. Deeper decision-specific pushbacks live in [MCSE-DECISION-LOG.md](MCSE-DECISION-LOG.md); this is the project-level set.

**Q1 — "Give me the 90-second overview of the system you own."**
> "MCSE is Walmart's promise & sourcing engine — on every product-page and cart view it decides where an item ships from and when it'll arrive, at ~700K requests a minute under a sub-100ms p95, across four markets and two brands. It's split into two tiers with opposite requirements: an **ingestion side** that consumes change events from ~18 domain teams over 40+ Kafka listeners and hydrates a Cassandra + in-memory cache layer; and a **sourcing side**, the hot path, that reads that pre-hydrated data — never the slow upstreams inline — and runs a parallel scatter/gather across every item-node combination to pick the best fulfillment option and date. The guiding principle is that a *wrong* promise is worse than a *slow* one, so under stress it degrades to a conservative correct answer rather than a confidently wrong one."
>
> **Pushback — "Why is it that complex? Sounds like a lookup."** → "The lookup is the easy 10%. The complexity is doing it at 700K rpm in under 100ms while the underlying data — inventory, capacity, carrier transit times — is constantly changing and the upstream services are too slow to call inline. So you pre-hydrate everything asynchronously, cache it in three layers by staleness tolerance, and fan out scoring in parallel with per-branch timeouts and fallbacks. The hard part isn't the decision; it's the latency budget, the failure isolation, and keeping the cached data fresh enough to be correct."

**Q2 — "Walk me through what happens to a single request."**
> Draw the §2 diagram: REST in → pre-scatter (5 parallel context fetches, assembled once) → scatter (enumerate item×node combos) → distribute (a CompletableFuture per combo, each with timeout + fallback, on a fail-fast executor) → gather (thenCombine, multi-objective pick) → response. ~65ms total.
>
> **Pushback — "Where's the bottleneck?"** → "Historically pre-scatter — five sequential context fetches were ~130ms alone, over budget. Parallelizing them onto a bounded pool took it to ~40ms. After that, the distribute phase's tail latency: one slow node call could drag the whole request, which is why every branch has its own timeout and the executor fails fast under load instead of queuing."

**Q3 — "How do you keep p95 under 100ms at that volume?"**
> "Four things. One, **never call slow upstreams inline** — everything's pre-hydrated into an in-memory off-heap cache read sub-microsecond, with zero GC pressure. Two, **parallelize** the independent work — pre-scatter fetches and the scatter fan-out both run concurrently. Three, **fail fast** — a zero-capacity executor queue means overload rejects instantly and serves a fallback rather than letting latency balloon. Four, **per-branch timeouts** so one slow node can't drag the request. The theme is: bound the tail, don't just optimize the average."
>
> **Pushback — "What's your actual tail latency story — p99?"** → *[VERIFY your real numbers before the room.]* "The p95 target is sub-100ms; the design specifically protects the tail because at 700K rpm the p99 is thousands of customers a minute. The fail-fast executor and per-branch timeouts exist precisely so the tail doesn't run away — a request that can't finish in budget returns a conservative fallback rather than blowing p99."

**Q4 — "How does the read side see data the write side ingests? Isn't there a consistency gap?"**
> "Yes, deliberately. The two tiers communicate through the data + cache layer, not directly — so there's a freshness lag between an event landing and the read side seeing it. I hold a sub-minute end-to-end freshness SLO on it, with consumer-lag and snapshot-age alerts to bound it. That eventual consistency is the price of decoupling — it's what lets a bulk ingestion surge never starve the customer-facing latency path. For the one place staleness is unacceptable — reserving inventory — the read side calls the live service inline instead of trusting cache."
>
> **Pushback — "So a customer can see a wrong date?"** → "They can see a *slightly stale* input, bounded to under a minute, and 'stale' here means conservative — I'd rather show a date I'm more likely to keep than an aggressive one I might miss. The genuinely correctness-critical step, holding inventory so we don't oversell, is never cached — that's a live call. So the staleness lives only where it's safe."

**Q5 — "Tell me about a time this system failed in production."**
> Pick one from [MCSE-interview-stories.md](MCSE-interview-stories.md) — the peak-traffic CPU incident (a logging anti-pattern under load) or the cross-pod non-determinism (HashMap iteration order). Structure: symptom → root cause → fix → **the prevention beat** (what you changed so the *class* of bug can't recur).
>
> **Pushback — "What did you change so it couldn't happen again?"** → This is the whole point of the story — always have the systemic follow-through, not just the fix. (E.g., "added a load-test gate that would've caught the logging hot-path; made the ordering deterministic and added a cross-pod consistency check.")

**Q6 — "How do you handle a downstream dependency being down?"**
> "Layered. Every external call is wrapped in a **circuit breaker** with a **dedicated thread pool** (bulkhead), so a slow dependency can't starve the others. When the breaker trips, we serve from the **last-known-good in-memory snapshot** — the customer gets a conservative but on-time answer, not an error. The breaker probes for recovery and closes automatically when the service is back. And any feature can be killed instantly via runtime config if needed. Five layers, each catching a failure the others can't." (Detail: [MCSE-FEATURES-AND-FAILURE.md #7](MCSE-FEATURES-AND-FAILURE.md#7--the-5-resilience-layers).)

**Q7 — "What's the hardest bug or failure mode in a system like this?"**
> "Bad config, not crashes. A crash is loud — it spikes latency, trips breakers, fires runbooks. A bad **config** value is silent: every pod healthy, every dashboard green, and the system quietly doing the wrong thing — like a mistyped cutoff time pushing every afternoon order to next-day. Infra alerting can't catch it because the infra is fine. The only reliable detector is **business-metric** alerting — a shifted promise-date distribution or a transactability drop. That realization changed how I think about observability: watch what the business is doing, not just whether the servers are up." (Full: [MCSE-FEATURES-AND-FAILURE.md #9](MCSE-FEATURES-AND-FAILURE.md#9--bad-config-the-scariest-failure-mode).)

**Q8 — "What would you change if you rebuilt it today?"**
> Have a genuine one. Candidates: "Finish the strangler-fig V5 migration so there's one contract, not a V5-envelope-over-V3-internals mapping layer — it's deliberate migration debt but it's real." Or: "Push more of the resilience config into typed, validated schemas so a bad config value is rejected at write time, not caught by a business alert after the fact — close the gap on the scariest failure mode." Or: "Invest earlier in shadow-traffic tooling — it de-risked multi-slot so well I'd want it standard for every contract change."
>
> **Pushback — "Why didn't you do that already?"** → Answer honestly with the trade-off: prioritization against revenue features, or a cost that wasn't justified until the system got bigger. The senior move is owning the reasoning, not pretending everything's perfect.

**Q9 — "How is this like or unlike what we do at Salesforce Signup & ISV?"**
> Use the §7 bridge table. Lead: "The core of your role is message-queue infrastructure for high-volume signup and org-provisioning flows — that's exactly my ingestion tier: 40+ Kafka listeners with back-pressure, idempotency, and blast-radius isolation, feeding a multi-tenant system where each market is effectively a tenant. The pieces that transfer are the reliability engineering and the multi-tenant isolation; the piece I'd ramp on is your specific relational-DB stack, but query-first data modeling and tuning transfer directly."

**Q10 — "What was *your* contribution versus the team's?"**
> Be precise and honest (this is the leveling question — see the HM leveling risk note). "I owned the multi-slot contract design and rollout end-to-end; I owned the ingestion tier's reliability model; I drove these specific production incidents to root cause and built the prevention. The overall engine predates me and is a team effort — I'm not claiming the architecture — but these components are mine, and I can go arbitrarily deep on the decisions and trade-offs in each."

---

## 9. — Draw-From-Memory Checklist

You're ready when you can do these without notes:

- [ ] Draw the write-side / read-side split in 30 seconds, and say *why* they're separate (opposite SLOs, independent scaling).
- [ ] Draw scatter → distribute → gather with the concurrency model (which primitive per phase, and *why* each).
- [ ] Explain the ThreadLocal capture pattern and the reuse/`finally`-clear footgun.
- [ ] Name the 3 cache layers, *why each exists*, and the consistency trade-off + how you bound it.
- [ ] State the 3 Kafka ingestion reliability principles and the offset-commit-after-process invariant.
- [ ] Give 3 things *you* owned, each with a decision + trade-off + prevention beat.
- [ ] Answer Q1–Q10 out loud, including the pushbacks.
- [ ] Say the one-paragraph "what MCSE is" with the "wrong answer worse than slow answer" framing.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 26, 2026 | **File created.** Narrative technical deep-dive distilled + confidentiality-scrubbed from project-update knowledge layer and interview stories. |
| Aug 28, 2026 | **Enriched (no-compression rebuild).** Added ToC; §3 "How It's Actually Built" (thread-pool table, primitive-per-phase, ThreadLocal capture code + footgun, multi-objective gather); expanded cache/ingestion teaching; added §8 drillable Interview Q&A (10 questions, each with pushback). Cross-linked to new [MCSE-DECISION-LOG.md](MCSE-DECISION-LOG.md) and [MCSE-FEATURES-AND-FAILURE.md](MCSE-FEATURES-AND-FAILURE.md) rather than duplicating. |
