# MCSE — Pitches & Cross-Questions (delivery layer, scrubbed & portable)
### The words you say + the follow-ups you'll get

> **What this is:** the interview *delivery* layer for the core project — the 30-second / 2-minute pitches for each story, plus the cross-question bank and stack-justification (Why-X) answers a senior interviewer will probe. Self-contained and **confidentiality-scrubbed** so it's safe to keep in a pushed repo.
>
> **What this is NOT:** the architecture narrative (→ [MCSE-PROJECT-DEEPDIVE.md](MCSE-PROJECT-DEEPDIVE.md)), the bug/project stories (→ [MCSE-interview-stories.md](MCSE-interview-stories.md)), the ingestion internals (→ [KAFKA-MCSE-INGESTION.md](KAFKA-MCSE-INGESTION.md)), or Kafka-vs-others (→ [KAFKA-VS-MQ-COMPARISON.md](KAFKA-VS-MQ-COMPARISON.md)).
>
> ⚠️ **Scrub rule:** internal service codenames and config-key names are replaced with concepts ("our inventory service," "our runtime config system"). Netflix **Hollow**, **Caffeine**, Kafka/Cassandra/Spark/Azure/K8s are open/standard — safe to name. Scale numbers (700K rpm, sub-100ms p95) are safe. In the room, translate any remaining jargon in real time.
>
> **Source coverage:** distilled from project-update `02` (pitches), `04` (cross-Qs + stack justification), `01` (drive-the-interview), `13` (Why-X framework), `03` (gray areas). Raw originals stay on the local machine.

---

## 🎤 §1 — The Pitches (memorize the 30s; rehearse the 2min out loud)

### Story 0 — Elevator / "Tell me about yourself" (30s)
> "I'm Kapil — ~5 years at Walmart on the same backend platform, currently senior engineer. The platform, internally MCSE, is Walmart's **promise & sourcing engine**: every time someone adds an item to a cart, it decides which warehouse or store ships it and what delivery date the customer sees — ~700K requests/min on the hot path, sub-100ms p95. My work spans three areas: features end-to-end (Canada multi-slot delivery), production firefighting as on-call (a peak-traffic CPU incident), and owning the Kafka ingestion tier that hydrates the platform's caches. Stack is Java, Spring Boot, Kafka, Cassandra, Spark, Kubernetes on Azure."

### Story 1 — The platform (30s → 2min)
**30s:** "Given a cart, MCSE returns the delivery date and fulfillment node for every line item. It's a modular monolith — each request fans out to 50–100 internal evaluations, so we can't afford network hops. Scatter–gather on a thread pool, 16 in-memory caches (Netflix's Hollow) for read latency, writes pushed through Kafka into Cassandra. Sub-100ms p95, multi-region active-active on Azure."

**2min (architecture):**
- **Position:** "Upstream is Search/PDP/Cart/Checkout, calling us through an upstream promise-aggregation service. Downstream we depend on our inventory service, the DC/fulfillment-center capacity engines, and smaller services for transit time and customer geo."
- **Request flow — three phases:** *Pre-scatter* — five parallel I/O calls to assemble context (eligibility, inventory, geo, capacity slots, offer details) via Callable+Future because we need all five before proceeding and want fail-fast. *Orchestrator* — for each line item, generate 50–100 (item × node × method) combinations and evaluate in parallel with CompletableFuture, each with its own timeout + fallback so one slow node can't poison the request. *Gather* — reduce to the best option per item (cost + date + node priority), generate reservations, respond.
- **Why sub-100ms works:** everything in the middle is in-memory — the 16 Hollow caches hold transit matrices, FC configs, geo mappings, pricing; memory-mapped, immutable, batch-refreshed.
- **Data plane:** domain teams publish events to Kafka → a separate ingestion service lands them in Cassandra → a Spark job builds new Hollow snapshots → pods atomically swap the reference. Hot path never blocks on writes.

### Story 2 — Canada multi-slot (hero feature) (30s → 2min)
**30s:** "I led the design + implementation of multi-slot delivery for Canada. The platform returned one slot per item; the business wanted Express (same/next-day) and Standard (2–5 days) in one response, separate prices. I redesigned reservation generation to emit multiple slots per item with separate inventory holds, co-designed a new contract with the upstream team, and shipped behind a feature flag. The hard part wasn't the design — it was migrating without breaking Search, Item, Cart, Checkout, which all consume the same response."

**2min:** problem (one-slot-per-item baked into every structure) → design (additive `slots[]` array keeping old fields populated; reservation generator emits a hold per slot; orchestrator runs two parallel evaluations per item) → hard part (partial confirmation without leaking inventory; re-reservation diff semantics on cart edit; slot price drift surfaced cleanly) → migration (per-market flag, Canada only, shadow-diff before cutover) → trade-off ("the dual-shape response is honest debt; I'd have versioned at the URL, but couldn't coordinate a v2 across four teams under the timeline").

### Story 3 — 100% CPU incident (debugging hero) (30s → 2min)
**30s:** "Peak traffic drove our Canada pods to 100% CPU; p95 went ~80ms → ~400ms, near dropping requests. I led the debug. Root cause: a logging anti-pattern — debug strings built via concatenation even with debug disabled, retaining references to large objects — drove GC into a death spiral that presented as CPU. Two-line fix, but the diagnosis took ~3 hours of thread-dump/heap-dump work. Then I wrote a static-analysis rule to kill the whole class of bug and added a heap-pressure alert."

**2min:** symptom → triage (rule out downstream, deploy, traffic surge → inside the JVM) → investigation (thread dump: threads in GC / logging lock; heap dump: huge retained strings from unguarded `log.debug("..." + obj)`) → root cause (concatenation evaluates even when disabled; string lingers till GC; under load GC can't keep up) → fix (`isDebugEnabled` guard / parameterized logging; CPU 100%→30% on rolling restart) → long-term (static-analysis rule found 40+ instances, 4 on hot paths; heap-pressure alert at 70% old-gen).

### Story 4 — Kafka ingestion tier (scale) (30s → 2min)
**30s:** "I own the upstream ingestion tier — consumes events from ~18 domain teams and lands them in Cassandra so the engine can read them. One artifact deployed ~18 times, each activating a different subset of 40+ Kafka listeners via a startup flag. Hot pipelines run on 120–200 pods, millions of records/day. Two consumer generations co-exist — a classic single-threaded one and a reactive batched V2 added when offer/item throughput outgrew classic."

**2min:** one-JAR-many-deployments (single scan/build, shared modules, one on-call mental model) → threading (poller threads → bounded processor pool = natural back-pressure; drain window on shutdown) → failure handling (per-topic retry topics + overnight dead-letter drain; never re-throw on a bad record — log+metric+commit-past, or you freeze the partition) → observability (per-pipeline lag SLO, sub-minute for hot pipelines; Kafka lag + Cassandra write latency + pool utilization on dashboards with paging).

**Story-pick guide:** most challenging → Story 2 · hard prod issue → Story 3 · high-throughput → Story 4 · general → Story 0+1 then ask which they want deeper.

---

## 🔬 §2 — Cross-Question Bank (answers are 2–4 sentences — give signal, not speeches)

### Architecture & Design
- **Why modular monolith not microservices?** Per-request fan-out is 50–100 evaluations under 100ms; network hops would blow the budget (5ms × 50 = 250ms). Microservices are right for *system-level* boundaries — that's where our upstream/downstream are — not internally.
- **Slow downstream?** Per-dependency thread-pool isolation + per-call timeout shorter than the request budget + circuit breaker (Resilience4j) that opens and falls back to a stale cache or a pessimistic date. Better to promise conservatively than fail.
- **Caching strategy?** Three tiers by access pattern: Hollow (large batch-refreshed reference data, sub-µs), Caffeine (JVM-local, short-TTL, staleness OK), a distributed cache (cross-pod coherent state). Each chosen for its consistency model.
- **Cache vs fetch?** Latency budget + mutation rate. Changes faster than refresh → fetch; slower + read-heavy → cache. Reference data → cache; per-request inventory/capacity → fetch with fallback.
- **Eventual consistency?** Every consumer is safe under stale data. For sourcing, stale = a slightly later (safe) date. For inventory, stale could over-promise (unsafe) → final strongly-consistent check at checkout. Separate the latency-critical path from the correctness-critical path.
- **CAP?** AP on the read path — a pod that loses Cassandra falls back to its in-memory snapshot rather than failing, reconverging via Kafka backfill. Reservation generation is CP — can't double-allocate inventory.
- **Horizontal scale?** JVMs are stateless (caches rebuildable from Kafka/Cassandra or memory-mapped). Add pods; Kafka consumer groups divide partitions. The ceiling is downstream (Cassandra/inventory), not the app tier.
- **Multi-region?** Active-active across two Azure regions; Cassandra multi-DC (LOCAL_QUORUM), Kafka mirroring, stateless pods both sides, DNS split with health probes, sub-minute failover.
- **Redesign differently?** Observability-as-code from day one; standardize on Resilience4j (the Hystrix migration is debt); contract tests with downstream consumers.

### Concurrency & Java
- **Callable+Future vs CompletableFuture?** Callable+Future when I need *all* results (pre-scatter). CompletableFuture when I combine as they arrive / chain non-blocking / want per-task fallback (orchestrator).
- **Why non-blocking orchestrator?** 50–100 tasks; blocking on `get()` serializes them. `thenCombine` + timeout race lets each task race independently — the slow one doesn't delay the rest.
- **Thread-pool sizing?** I/O-bound → 2–4× CPU, tuned empirically; bounded queues always (unbounded masks back-pressure → OOM); separate pool per dependency.
- **volatile vs synchronized vs AtomicReference?** volatile = visibility not atomicity (flags); synchronized = both but blocks; AtomicReference = CAS, non-blocking (reference swaps under contention). Cache updates use an AtomicReference swap, not read-modify-write under lock.
- **Java 17 changes?** Records kill DTO boilerplate; pattern-matching switch cleans event dispatch; sealed classes for closed type hierarchies. Virtual threads prototyped for the I/O fan-out, promising, not yet at scale.
- **Stream vs parallelStream?** Default sequential (readable, predictable context). Parallel only for CPU-bound associative work on large data. For heterogeneous I/O fan-out, CompletableFuture on a managed executor beats `parallelStream()` (which shares the common pool → noisy neighbor).

### Kafka, Messaging, Storage
- **At-least-once delivery?** Producer: `acks=all`, `enable.idempotence=true`, retries. Consumer: process first, commit after (no auto-commit on hot consumers). Dedupe via idempotent upserts keyed on (entity-id, event-id).
- **Poison pill?** Never re-throw (freezes the partition). Catch, log with offset+key, metric, publish to a retry topic, commit-past; a separate retry consumer drains with backoff; after N retries → dead-letter for human review.
- **Cassandra modeling?** Query-first: partition key = what you look up by, clustering = how you sort within. Wide rows are fine when read whole. Avoid: secondary indexes on high-cardinality columns, multi-partition IN, ALLOW FILTERING in prod.
- **Tunable consistency choice?** LOCAL_QUORUM default (strong within region, survives one node loss); ONE for non-critical reads; EACH_QUORUM only for genuinely cross-region critical writes (rare — high latency).
- **Hot partition?** Detect via per-node rate metrics (one node's CPU climbs while peers flat). Fix by re-sharding the key — add a low-cardinality bucket (e.g., `key + (ts % 32)`); time-series keyed only on entity ID → bucket by day.
- **Write → read path?** Producer → Kafka → ingestion validates/transforms → Cassandra → scheduled Spark builds a Hollow snapshot → pods atomically swap the in-memory reference. Minutes for full hydration, seconds for incremental flips via a Kafka delta path. Hot reads never touch Cassandra.

### Production, Ops, Security
- **Deployments?** CI build+test → rolling K8s update with min-pods-available across both regions in parallel; liveness/readiness gate traffic; canary (Flagger) ramps 10%→100% with metric gates; single-click image-tag rollback.
- **Bad config push?** Most damage is config, not code. Runtime config with per-key rollback; every key has a baked-in default (missing key can't take us down); critical kill-switches have a last-known-good cache.
- **Service-to-service auth?** Mid-migration: legacy HMAC-style signed requests with a timestamp window (public keys in registry, private in a vault) → target SPIFFE workload identity with Istio mTLS (cert rotation, identity bound to workload not a secret).
- **Observe an issue end-to-end?** Metrics dashboard to localize the layer → distributed traces to find the slow call → logs at that correlation ID for the reason.
- **Alerting philosophy?** Page on user-visible SLO symptoms, not internal causes; every page has a runbook; every post-mortem yields a code change, an alert change, or a runbook update.

---

## 🧭 §3 — Stack Justification (the Why-X 4-step: problem → constraint → why this → why not alternatives → trade-off)

> ⚠️ These sank a prior round when answered weakly. Never say "DI" or "it's in-memory." Use the 4-step.

**Why Java + Spring Boot?** (1) JVM has the most mature concurrency primitives for the 50–100-task fan-out; (2) the whole data plane (Kafka, Cassandra, Spark, Hollow) is JVM-native — the Spark hydration job forces JVM by itself; (3) Spring gives production hygiene free (health endpoints, graceful shutdown, scheduling, config binding). **Trade-off:** JVM cold-start/footprint rule out serverless — fine for a long-running stateful service; for short-lived workloads I'd consider Go/GraalVM.

**Why Hollow, not Redis?** (1) Redis is a network call (0.5–2ms) × 50–100 lookups = the whole latency budget; Hollow is memory-mapped in-process (nanoseconds). (2) Hollow gives a versioned immutable snapshot of the *whole* dataset — every request sees a stable view; Redis gives per-key consistency (mixed versions mid-request). (3) Zero added infra vs a Redis cluster per region. **When Redis wins:** dataset too big for JVM memory, or write-through of mutating user state.

**Why Kafka, not RabbitMQ/SQS/Kinesis?** (1) log model → one write serves many independent consumers at their own offsets; (2) partition-scale throughput (millions/day, 10× spikes); (3) replay by rewinding offsets when ingestion falls behind; (4) per-partition ordering (partition by offer-ID). **Trade-off:** higher ops overhead — wouldn't pick Kafka for a low-volume notification queue. *(Full comparison: [KAFKA-VS-MQ-COMPARISON.md](KAFKA-VS-MQ-COMPARISON.md).)*

**Why Cassandra, not relational?** (1) linear read scale via masterless ring; (2) query patterns are pre-known and stable — denormalized purpose-built tables, one partition lookup, no JOINs; (3) first-class multi-region active-active (LOCAL_QUORUM). **Trade-off:** query rigidity (new access pattern = new table); **wouldn't pick it for OLTP/ACID** — and we don't; relational stores are used where they fit (config data with complex relations + transactions). *This "where I'd NOT use it" is the senior signal — and answers Salesforce's relational focus.*

**Why Spark for cache hydration, not Flink?** It's a batch job (read all → build snapshot → write); Spark is batch-first, managed on our cloud, and the org standard. **Trade-off:** if we needed sub-minute continuous refresh, Flink's streaming state would win.

**Why Kubernetes, not VMs/serverless?** Per-pipeline pod autoscaling, same image + different config per deployment, canary rollouts. **Not serverless:** cold-start kills <100ms p95, and spin-down loses the in-memory Hollow caches — defeating the whole architecture.

---

## 🚫 §4 — Gray Areas (what NOT to say)

- **Translate jargon in real time.** Never drop an internal codename without its plain-English gloss ("our inventory service," "Netflix's open-source in-memory cache").
- **Don't present Hystrix as modern** — it's deprecated; Resilience4j is the target. Mention the migration as known debt.
- **Don't claim ML expertise on predictive transit time** — you *consume* the model, you didn't train it.
- **Don't list "DI / embedded server" as why you chose Java** — table stakes, sounds junior.
- **Don't say "Java 17" then cite Java 8 features** (or volunteer "our old stuff is Java 8").
- **Don't volunteer confidential internal incidents, outage dates, revenue/GMV, internal config-key names, or Confluence/repo IDs.**
- **Never throw a teammate, team, or "the old code" under the bus** — "a pattern in the codebase," never "someone's bad code."
- **Don't badmouth Walmart** on "why leaving" — forward-looking only (Trust is Salesforce's #1 value).

---

## 🧩 §5 — When You Don't Know (three legitimate moves — never bluff)

1. **Reason from first principles aloud:** "I haven't hit that exact case; the constraint is X, so the design must satisfy Y — probably A (simpler) or B (more robust). I'd validate against the real access pattern before committing."
2. **Bridge to what you know:** "The equivalent in my system was [X] — same shape — so I'd start there and learn the [domain]-specific constraints from the team."
3. **Ask a clarifying question:** "When you say 'reconciliation,' intra-day against an external system, or end-of-day batch? Different problems."

> A confident "I don't know, but here's how I'd approach it" beats a wrong confident answer every time.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 28, 2026 | **File created.** Consolidated + scrubbed delivery layer from project-update `01/02/03/04/13`: pitches (Stories 0–4, 30s/2min), cross-question bank (architecture/concurrency/Kafka-storage/ops), stack-justification (Why-X 4-step for Java/Hollow/Kafka/Cassandra/Spark/K8s), gray-areas, and the "when you don't know" moves. Internal codenames/config-keys scrubbed to concepts; scale numbers retained. Portable/git-safe. |
