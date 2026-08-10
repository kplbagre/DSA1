# Design a Health Check / "wasAlive" Monitoring System

> **Interview Type:** Type 2 — Full System Design
> **Frequency:** ⭐ Tier 3 — 1 confirmed in-window report (Mar 2026). Novel problem — concrete algorithmic constraint baked in.
> **Key signal from research:** "Design wasAlive() API — 5 time slots of 100ms each. A node is inactive if 3 consecutive slots show no heartbeat. Discuss distributed coordination."
> **Evaluation focus:** Distributed coordination (who declares DOWN?), sliding window algorithm, Kafka-native design.
> **Standards file:** `solution-notes-standards.md`
> **API rules reference:** `./api-design-cheatsheet.md`

---

## 🎯 What Is This System?

**In plain English:** A monitoring system where services "check in" regularly by sending heartbeat pings. The monitor watches those pings and raises an alarm when a service stops checking in long enough to conclude it's dead — not just momentarily slow.

**Real-world examples:**

| System | What they built |
|---|---|
| **Kubernetes liveness probes** | kubelet pings each pod's `/health` endpoint on a schedule; 3 consecutive failures → pod is restarted |
| **AWS Route 53 health checks** | Route 53 polls registered endpoints every 10–30 seconds; N consecutive failures → route traffic to failover |
| **Consul health checks** | Each service sends keepalive; Consul's gossip layer detects silence and marks the service critical |
| **Zookeeper session timeout** | Client sends heartbeat (ZK calls it a "ping") every `tickTime`; leader declares client dead after `sessionTimeout` of silence |
| **Cassandra gossip / phi accrual failure detector** | Each node gossips about neighbors; phi accrual computes a continuous suspicion score from inter-arrival times instead of a binary threshold |

**Why the 3-consecutive-slots rule exists:** A single missed heartbeat could be a network blip or a brief GC pause. Declaring a node dead on one miss produces too many false positives. Three *consecutive* misses means the silence is sustained — it's not a blip, it's a failure.

**The hard problem:** Declaring liveness requires a *decision-maker*. If only one process is watching all nodes, that process itself is a SPOF (a single point of failure — if it dies, all nodes appear alive to the outside world, even when they're not). Distributing the decision is the architectural spine of this question.

**Tableflow parallel:** The wasAlive check maps to Tableflow's own pipeline health model. Tableflow tracks whether a data pipeline (consumer group) is making progress by checking its committed offsets against the latest offsets on a Kafka topic. A pipeline that hasn't committed an offset in N minutes is analogous to a node that hasn't sent a heartbeat in N slots. The "is my pipeline alive?" question in Kafka is the health-check problem applied to streaming consumers.

---

## 🚀 Section 1 — The One-Sentence Opener

> "I want to align on two things before I diagram this: first, whether the heartbeat is push-based (nodes call us) or pull-based (we poll the nodes), because that inverts the architecture; and second, who the decision-maker is when a node is declared down — that's the SPOF question that determines whether this is a simple cron job or a distributed consensus problem."

---

## 🔍 Section 2 — Clarifying Questions Script (Minutes 0–5)

**Q: "Push or pull heartbeat model?"**
- Why ask: determines API direction and failure mode inversion
- **Push (node calls us):** `POST /v1/nodes/{id}/heartbeat`; if the node is down, we see silence; simple and widely used
- **Pull (we call the node):** `GET /v1/nodes/{id}/health`; if OUR network is partitioned from the node, false positives; also: what if the node has no HTTP server? (Kafka brokers don't expose health endpoints)
- **Assume:** Push model. Node sends heartbeat to us every 100ms. More realistic for the problem statement.

**Q: "What scale — how many nodes are being monitored?"**
- Why ask: 1K nodes at 100ms intervals = 10K heartbeats/sec (trivial); 100K nodes = 1M heartbeats/sec (needs partitioned ingestion)
- If ≤10K nodes → single Redis instance with ring buffers is sufficient
- If 100K+ nodes → partition nodes across a Kafka consumer group; each partition handles a shard of nodes
- **Assume:** 10K nodes initially; system should scale to 100K.

**Q: "Who consumes the 'node is down' event? Alert only, or do downstream systems act on it?"**
- Why ask: if only one consumer (PagerDuty alert), a simple callback is fine; if multiple consumers (alert + traffic router + dashboard + audit log), you need an event bus
- **Assume:** Multiple consumers. Use a Kafka topic for status-change events — this is the natural Confluent angle.

**Q: "Is there a re-registration or TTL for nodes, or do registered nodes stay in the system forever?"**
- Why ask: If nodes are ephemeral (auto-scaling groups, Kubernetes pods), they register on start and deregister on stop; stale registrations accumulate otherwise
- **Assume:** Nodes must explicitly register and deregister; stale nodes (no heartbeat in 24h) can be auto-cleaned.

**Q: "Are the 5-slot × 100ms parameters fixed, or configurable per node?"**
- Why ask: configurable means an extra `window_ms` and `threshold_slots` column on the node record; fixed is simpler
- **Assume:** Fixed system-wide for this design. Note that making it configurable is straightforward — add two columns to the `nodes` table.

---

## 📋 Section 3 — Requirements

**Functional Requirements:**
- Nodes register with the monitoring system (providing name, owner contact)
- Registered nodes send heartbeat pings every 100ms
- The system evaluates liveness using a 5-slot × 100ms sliding window
- A node is declared DOWN if 3 or more consecutive slots contain no heartbeat
- Status changes (ALIVE → DOWN, DOWN → ALIVE) are published as events for downstream consumers
- Clients can query the current health status of any registered node
- Clients can query the historical health status of a node (list of status-change events)
- Out of scope: alerting UI, auto-remediation (restart downed nodes), distributed metrics aggregation, multi-region replication of status

**Non-Functional Requirements:**
- Scale: 10K nodes × 100ms interval = 100K heartbeats/sec at steady state; target 100K nodes
- Liveness detection latency: the *algorithm* fires at 300ms of silence (3 empty slots × 100ms), but the end-to-end budget is larger — ≤ 650ms from last heartbeat to the evaluator writing DOWN, ≤ 700ms to alert delivery. The extra ~350ms is ingest + consumer lag + tick granularity + Redis round-trips + dispatch; it is itemised in the latency budget table at the end of Section 7. Do not quote 500ms as the end-to-end number — 500ms is only the width of the window
- Heartbeat ingestion P99 < 20ms (fire-and-forget from the node's perspective)
- Status query P99 < 50ms (clients poll frequently)
- False negative rate (declaring a healthy node DOWN): < 0.1% under normal network conditions
- Availability of the monitoring system itself: 99.9%; it must survive single-node failures without losing the DOWN state of observed nodes

---

## 🗂️ Section 3.5 — Core Entities

| Entity | Nature | What it represents |
|---|---|---|
| **Node** | Transactional | A service instance registered for monitoring; has owner, name, endpoint metadata; mutable (can update contact info) |
| **Heartbeat** | Append-only | An individual ping event from a node at a specific timestamp; retained for the 500ms evaluation window; immutable |
| **NodeStatus** | Ephemeral-mutable | The current declared state (ALIVE / DOWN / UNKNOWN) of a node; overwritten on each state transition |
| **StatusChangeEvent** | Append-only | A record of every ALIVE↔DOWN transition for a node; used for historical queries and downstream alerting; immutable once written |

---

## 🔢 Section 4 — Scale Estimation

**Steady-state write volume:**
- 10K nodes × 1 heartbeat/100ms = **100K heartbeats/sec** — modest for Kafka; easily handled with a single partition per shard of nodes
- Each heartbeat record: ~100 bytes (node_id 16B + timestamp 8B + metadata ~76B) → 100KB/s ingestion throughput — trivial

**Scale-out target (100K nodes):**
- 100K nodes × 10 heartbeats/sec = **1M heartbeats/sec**
- Kafka with 10 partitions (node_id hash-partitioned) handles 1M msg/sec comfortably
- Redis: 100K node_id keys × ~1KB of sliding window state = 100MB — fits in a single Redis instance; negligible memory pressure

**Redis ops/sec — the constraint that actually binds (do not skip this):**

Memory is the easy number and it is misleading. Count *operations* on the heartbeat hot path instead. Each heartbeat costs:

| # | Op | Why it exists |
|---|---|---|
| 1 | `GET node:{id}:reg` (registration cache) | serves the 404-on-unregistered contract |
| 2 | `INCR` rate-limit counter | serves the 429 contract |
| 3 | `EVAL` conditional slot write | the ring-buffer write (see Deep Dive 1) |
| 4 | `SET node:{id}:last_ts` | failover grace-period check — **redundant, see §9** |

That is **~4 Redis ops per heartbeat**, so **~40 ops/sec per node** at the 10 heartbeats/sec rate. A single Redis instance does ~100–200K ops/sec unpipelined and ~1–2M ops/sec when the client pipelines aggressively (which the receivers must). Dividing:

- 1M ops/sec ÷ 40 = **~25K nodes**
- 2M ops/sec ÷ 40 = **~50K nodes**

So a single Redis instance caps this design at **~25K–50K nodes**, not the ~1M the memory figure implies. Dropping the redundant `last_ts` write takes it to 3 ops (~33K–66K nodes) — better, but the same order of magnitude. **Sharding Redis is mandatory to reach the 100K-node target, not an optional "if needed" step.**

**Status store read volume:**
- Clients query node status at dashboard refresh or alert evaluation: ~1K reads/sec — Redis serves this at microsecond latency
- Historical query (StatusChangeEvent log): low-frequency; served from Postgres or Iceberg

**Downstream reference:** The 10-partition Kafka topic used for heartbeat ingestion reappears in Section 8 (deep dive on the evaluator design).

---

## 🔄 Section 5 — Requirements Variation Table

| If the interviewer says... | Architecture changes to... | Reasoning |
|---|---|---|
| "Only 1K nodes" | Single-threaded evaluator, Redis ring buffer, no Kafka needed | 10K heartbeats/sec is trivial; Kafka is over-engineering for this scale |
| "1M nodes" | **More partitions** (not more consumer groups), sharded Redis, ZooKeeper or etcd for cross-shard evaluator coordination | 10M heartbeats/sec; Redis memory = 1GB for window state — but the binding limit is ops/sec, so ~20–40 Redis shards. Note: a second consumer group adds **zero** parallelism — it re-reads the same partitions independently. Evaluator parallelism is capped at the partition count, so scaling out means raising partitions, not adding groups |
| "Pull-based (we poll the node)" | Add `/v1/nodes/{id}/health` endpoint on the *monitored* service; our poller calls it | Inverts fault domain: our network partition = false DOWN; nodes no longer need our SDK |
| "3 consecutive misses with network jitter tolerance" | Phi accrual failure detector instead of fixed-slot ring buffer | Phi accrual is probabilistic: computes suspicion score from inter-arrival time distribution; handles variable heartbeat intervals gracefully |
| "Multi-region" | Each region runs its own evaluator; status-change events replicated via Kafka MirrorMaker | A node can appear DOWN in one region due to a partition, even if alive globally — need quorum across regions |
| "Configurable window per node" | Add `window_slots` and `threshold_slots` columns to `nodes` table; evaluator reads config per node_id on each evaluation | Clean schema extension; no evaluator algorithm change |

---

## ⭐ Section 6 — API Design

**Derivation:** Push model → node must have an endpoint to push to → `POST /v1/nodes/{id}/heartbeat`. Every other endpoint flows from the FRs.

### Core Endpoints

| Method | Path | Auth | Request Body | Response Body | Status Codes |
|---|---|---|---|---|---|
| `POST` | `/v1/nodes` | Admin Bearer | `{ name, owner_email, description? }` | `{ id, name, owner_email, created_at }` | 201, 400, 401, 409 |
| `DELETE` | `/v1/nodes/{node_id}` | Admin Bearer | — | — | 204, 401, 403, 404 |
| `GET` | `/v1/nodes` | Bearer | — | `{ nodes: [{id, name, status, last_heartbeat_at}], next_cursor, has_more }` | 200, 401 |
| `GET` | `/v1/nodes/{node_id}` | Bearer | — | `{ id, name, status, last_heartbeat_at, owner_email }` | 200, 401, 404 |
| `POST` | `/v1/nodes/{node_id}/heartbeat` | Node API Key | `{}` (empty or minimal) | `{ received_at }` | 200, 401, 404, 429 |
| `GET` | `/v1/nodes/{node_id}/history` | Bearer | `?from=<ts>&to=<ts>` | `{ events: [{status, occurred_at, reason}], next_cursor }` | 200, 401, 404 |

---

### Endpoint Stories

**`POST /v1/nodes`** — registers a new node into the monitoring system. Returns **201 Created** with the node's assigned UUID. Returns **409 Conflict** if a node with the same `name` already exists in the system — the admin is trying to re-register an existing service. Returns **400** if `owner_email` is malformed or `name` is empty. The returned `id` is what the node's SDK embeds into every subsequent heartbeat call.

**`POST /v1/nodes/{node_id}/heartbeat`** — the most critical and highest-volume endpoint. Called by the monitored node every 100ms. Returns **200 OK** with `received_at` timestamp. Non-obvious: returns **404** if `node_id` is not registered — this means someone is sending heartbeats for a node that was never registered or was already deregistered. This is a meaningful error: the SDK should log it and alert the owner (the node thinks it's being monitored, but it isn't). Returns **429 Too Many Requests** with `Retry-After: 1` if the node is sending faster than 1 heartbeat per 50ms — runaway heartbeat loops should not overwhelm the receiver. Why 200 (not 204)? We return `received_at` so the node can detect clock skew between itself and the monitor (if `received_at` differs significantly from the node's local clock, the heartbeat timestamp comparison in the evaluator will be unreliable).

> **The 429 is more dangerous than it looks — say this out loud.** Two consequences that are easy to miss:
> 1. **It costs an op on the hot path.** The per-node rate limiter is itself a Redis `INCR` + TTL on every single heartbeat. That is one of the ~4 ops/heartbeat counted in Section 4, and it is charged against the same Redis instance the ring buffer lives on. A rate limiter meant to protect the receiver consumes the resource that actually saturates first.
> 2. **A 429'd heartbeat is a *dropped* heartbeat.** It never reaches the ring buffer, so the slot it would have filled stays empty. If a node is throttled for 300ms — a burst after a GC pause, a retry storm, a misconfigured SDK interval — the limiter manufactures 3 consecutive empty slots and the evaluator declares the node DOWN. **The rate limiter can synthesise the exact failure the system exists to detect.** Mitigations: (a) rate-limit by *rejecting the excess only*, always admitting the first heartbeat in each 100ms slot; (b) or make the limiter fail-open — on limiter error or ambiguity, admit the heartbeat, because a false DOWN is far more expensive than one extra write; (c) emit a `heartbeats_throttled_total{node_id}` metric and treat a non-zero value on a node that later goes DOWN as a suspect signal during incident review.

**`GET /v1/nodes`** — cursor-paginated list of registered nodes. Non-obvious cost: the response carries `status` and `last_heartbeat_at` **per node**, and neither field lives in Postgres — both are Redis-resident. So this endpoint is not one query; it is a Postgres page fetch (`idx_nodes_active`, N rows) **plus an N-key Redis `MGET` fan-out** to hydrate status, plus a second fan-out (or an `HGETALL` per node) for `last_heartbeat_at`. At page size 100 that is ~200 extra Redis ops per list call, charged against the same instance the heartbeat path saturates. Consequences to state: cap `limit` at 100 (a dashboard asking for 10K nodes in one page would issue a 20K-key fan-out); use `MGET`, never a loop of `GET`; and if the list endpoint is hot, serve `status` from the compacted `node-status-changes` topic materialised into a read replica instead of fanning out to Redis.

**`GET /v1/nodes/{node_id}`** — returns the current declared status (ALIVE / DOWN / UNKNOWN). UNKNOWN means the node has registered but has never sent a heartbeat. Status is read from the Redis status store — P99 < 5ms. Callers use this for dashboard polling and pre-action checks ("is this node alive before I route traffic to it?").

**`GET /v1/nodes/{node_id}/history`** — returns the log of status-change events for a node, time-bounded by `from` and `to` query parameters. The response is cursor-paginated because a long-running node may have thousands of ALIVE↔DOWN transitions. Served from Postgres `status_change_events` table, not Redis.

---

## 🏗️ Section 7 — High-Level Design

> **Delivery note — build it up, don't draw the finished thing.** Start with the simplest system that actually satisfies the requirement (Stage 1), then let the scale numbers from Section 4 force each next stage. Saying "single process with an in-memory map handles 1K nodes — here's exactly where it breaks" is a stronger signal than opening with Kafka. The interviewer wants to see that you introduce infrastructure only when a number demands it.

---

### Stage 1 — Single Process, In-Memory (handles ~1K nodes)

> Start here. One service does everything: receives heartbeats, holds the window, evaluates, alerts.

```
── Stage 1: Single Process ────────────────────────────────────────

   ┌──────┐  ┌──────┐  ┌──────┐
   │Node A│  │Node B│  │Node C│      1K nodes × 10/sec = 10K heartbeats/sec
   └──┬───┘  └──┬───┘  └──┬───┘
      │ POST /v1/nodes/{id}/heartbeat (every 100ms)
      └─────────┼─────────┘
                ▼
   ┌────────────────────────────────────────┐
   │        Monitor Service (1 process)     │
   │────────────────────────────────────────│
   │  ConcurrentHashMap<UUID, boolean[5]>   │  ← ring buffer, in heap
   │  ScheduledExecutor every 100ms:        │
   │    for each node → count consec gaps   │
   │    if gaps >= 3 → mark DOWN, alert     │
   └────────────────┬───────────────────────┘
                    │ GET /v1/nodes/{id}  → read from the same map
                    ▼
   ┌────────────────────────────────────────┐
   │  Postgres — nodes, status_change_events│  ← registration + history only
   └────────────────────────────────────────┘

BREAKING POINT: Stage 1 breaks at ~1K nodes / 10K heartbeats/sec
  because the window state lives in ONE JVM's heap. Two independent
  failures arrive together:
   (a) A single process cannot be load-balanced — the moment you add a
       second receiver instance for HTTP capacity, heartbeats for node X
       land on whichever instance the LB picked, so NEITHER instance sees
       a complete window. Node X is declared DOWN while it is healthy.
   (b) Process restart (deploy, OOM, crash) wipes every ring buffer.
       All 1K nodes read as 0 heartbeats → all evaluate to DOWN
       simultaneously → mass false alert storm on every deploy.
  Observable symptom: false DOWN alerts correlated exactly with deploys,
  and node X flapping as the LB spreads its heartbeats across instances.
  NOT the symptom: the in-heap scan itself. 10K nodes x 5 slots is ~50K
  array reads -- tens of microseconds per tick, nowhere near the 100ms
  budget. The scan only becomes expensive at Stage 2, when every slot
  read turns into a network round-trip. What does hurt in one JVM is
  the HTTP thread pool and GC pressure at 10K heartbeats/sec.
  Why Stage 2 is needed: window state must move OUT of process memory.

══════════════════════════════════════════════════════════════════
```

---

### Stage 2 — Shared Redis State + Horizontal Receivers (handles ~10K nodes — our target)

> Move the ring buffer to Redis. Receivers become stateless and horizontally scalable. This is the design for the stated 10K-node requirement.

```
── Stage 2: Stateless Receivers + Redis ───────────────────────────

   ┌──────┐  ┌──────┐  ┌──────┐        10K nodes = 100K heartbeats/sec
   │Node A│  │Node B│  │Node C│
   └──┬───┘  └──┬───┘  └──┬───┘
      └─────────┼─────────┘
                ▼
        ┌───────────────┐
        │ Load Balancer │            ← now safe: receivers hold no state
        └───────┬───────┘
        ┌───────┴────────┬──────────────┐
        ▼                ▼              ▼
  ┌──────────┐    ┌──────────┐   ┌──────────┐
  │ Receiver │    │ Receiver │   │ Receiver │   stateless; N instances
  │    1     │    │    2     │   │    3     │
  └────┬─────┘    └────┬─────┘   └────┬─────┘
       │  EVAL slot_write.lua  (conditional: write only if newer)
       └───────────────┼──────────────┘
                       ▼
       ┌───────────────────────────────────────┐
       │              Redis                    │
       │  node:{id}:slots  → HASH (ring buffer)│  10K × 1KB = 10MB
       │  node:{id}:status → ALIVE / DOWN      │  ← serves GET /status
       └───────────────┬───────────────────────┘
                       ▲
                       │ read window, write status
       ┌───────────────┴───────────────────────┐
       │      Evaluator (single instance)      │
       │  every 100ms: scan nodes, count gaps  │
       │  3 consecutive → transition to DOWN   │
       └───────────────┬───────────────────────┘
                       │ on transition only
                       ▼
       ┌──────────────────┐     ┌──────────────────┐
       │ Alert Dispatcher │     │ Postgres Writer  │
       │ (PagerDuty/Slack)│     │ status_change_   │
       └──────────────────┘     │ events           │
                                └──────────────────┘

WHY THIS FIXES STAGE 1:
  Receiver restart is now harmless — the window is in Redis, not heap.
  Any receiver can accept any node's heartbeat (state is shared).

WHO WRITES THE RING BUFFER AT THIS STAGE: the RECEIVER, inline on the
  request path, via the conditional Lua script. The evaluator only READS
  slots and WRITES status. Say this explicitly — it changes at Stage 3.

BREAKING POINT: Stage 2 breaks at ~25K–50K nodes / 250K–500K hb/sec
  because of THREE ceilings that arrive together — and the one people
  forget is the middle one:
   (a) The single evaluator must scan every node inside one 100ms tick.
       At 50K nodes that is 50K Redis HGETALL round-trips per tick.
       Even pipelined at ~50K ops/sec per connection, one full scan takes
       ~1 second — 10× the tick budget. The evaluator falls permanently
       behind and detection latency degrades from 300ms to seconds.
   (b) The Redis SERVER saturates at the same load — this is co-binding
       with (a), not a later problem. 50K nodes × 10 hb/sec × ~4 ops per
       heartbeat = 2M Redis ops/sec on the write path alone, before the
       evaluator's scan traffic is added. A single instance tops out at
       ~1–2M ops/sec even fully pipelined. So the box is already at its
       ceiling when the evaluator loop blows its budget.
       This is why (a) alone is a misdiagnosis: adding evaluators does
       not help if they all hammer one Redis.
   (c) The single evaluator is a SPOF: if it dies, no node is ever
       declared DOWN. The system fails silently — dashboards keep showing
       ALIVE because nothing is flipping the status. This is the dangerous
       failure mode: a monitoring system that is broken but looks healthy.
  Observable symptom: evaluator loop duration > 100ms tick (emit this as a
  metric); Redis `instantaneous_ops_per_sec` flat-lined at its ceiling with
  rising command latency; status_change_events table goes suspiciously
  quiet.
  Why Stage 3 is needed: evaluation must be partitioned across instances
  WITH Redis sharded underneath them — partitioned evaluators over one
  shared Redis fixes (a) and (c) and leaves (b) exactly where it was.

══════════════════════════════════════════════════════════════════
```

---

### Stage 3 — Kafka Consumer Group for Partitioned Evaluation (100K+ nodes)

> Now Kafka is justified — not as a buffer, but because a **consumer group** gives partitioned evaluation plus automatic failover with no custom leader election.

```
── Stage 3: Kafka-Partitioned Evaluation ──────────────────────────

   10K–100K nodes ──▶ [ LB ] ──▶ Stateless Receivers (N)
                                        │
                                        │ publish, key = node_id
                                        ▼
              ┌──────────────────────────────────────────┐
              │      Kafka topic: "heartbeats"           │
              │      10 partitions, key = node_id        │  1M msg/sec
              │   ┌────┬────┬────┬────┬─────┬──────────┐ │
              │   │ P0 │ P1 │ P2 │ P3 │ ... │   P9     │ │
              │   └─┬──┴─┬──┴─┬──┴─┬──┴─────┴────┬─────┘ │
              └─────┼────┼────┼────┼──────────────┼───────┘
                    ▼    ▼    ▼    ▼              ▼
              ┌─────────────────────────────────────────┐
              │   Evaluator Consumer Group (10 members) │
              │   E0←P0   E1←P1   E2←P2  ...  E9←P9     │
              │   each owns a DISJOINT node subset      │
              └─────────────────┬───────────────────────┘
                                │ ring buffer + status
                                ▼
              ┌─────────────────────────────────────────┐
              │  Redis Cluster — SHARDED by node_id     │
              │  2–4 shards at 100K nodes (MANDATORY)   │
              │  node:{id}:slots / node:{id}:status     │  100K × 1KB = 100MB
              └─────────────────┬───────────────────────┘
                                │ on transition only (~1/hr/node)
                                ▼
              ┌───────────────────────────────────────────────┐
              │  Kafka topic: "node-status-changes"           │
              │  log-compacted, key = node_id                 │
              │  → latest value per key = current node state  │
              └────────┬─────────────────────────┬────────────┘
                       ▼                         ▼
              ┌────────────────┐        ┌──────────────────┐
              │ Alert          │        │ Postgres Writer  │
              │ Dispatcher     │        │ status_change_   │
              └────────────────┘        │ events           │
                                        └──────────────────┘

WHY KAFKA EARNS ITS PLACE HERE (name this explicitly — it is the probe):
  1. Partition-per-consumer = each node_id is evaluated by exactly ONE
     evaluator. No duplicate alerts, no distributed lock, no coordination
     code. Kafka's key-hash routing is the sharding function.
  2. Consumer group rebalance IS the failover. Evaluator E3 dies →
     Kafka reassigns P3 to a surviving member within session.timeout.ms.
     No ZooKeeper, no custom leader election, no split-brain to reason about.
  3. A rebalance is LOSSLESS because of Kafka retention + offset replay —
     NOT because "state is in Redis instead of heap". Say it that way.
     The new owner of P3 resumes from P3's last committed offset and
     re-applies every heartbeat the dead owner had consumed but not yet
     committed. Redis is a materialised view of that replay: convenient
     (no cold replay of 500ms of history on every reassignment) but not
     the durability mechanism. If Redis were flushed, the window could
     still be rebuilt from the topic; if the topic's retention were
     shorter than the window, no amount of Redis would make it lossless.

  4. WHO WRITES THE RING BUFFER CHANGES HERE — state it, it is the
     rebalance-correctness question. At Stage 2 the RECEIVER wrote the
     slots inline. At Stage 3 the receiver only produces to Kafka, and
     the EVALUATOR that owns the partition is the SOLE writer of
     node:{id}:slots for its nodes. That is what makes replay safe:
     one writer per node_id, and the write is idempotent (conditional
     on a newer carried timestamp), so re-applying uncommitted records
     after a rebalance converges to the same slot values. If receivers
     still wrote slots directly here, a rebalance would race the
     receivers and in-flight window state could genuinely be lost.

CEILING OF STAGE 3: ~100K–200K nodes with 2–4 Redis shards, NOT ~1M
  The old ~1M figure was ~20–40× too high: it priced one Redis op per
  heartbeat when the hot path does ~4 (registration check, rate-limit
  INCR, conditional slot write, last_ts write). At ~40 ops/sec/node a
  single instance (~1–2M ops/sec pipelined) carries only ~25K–50K nodes,
  so the 100K target ALREADY needs sharding — which is why Stage 3
  shards Redis rather than only adding evaluators. Two hard limits:
  Redis ops/sec per shard, and the 10-partition topic capping
  parallelism at 10 evaluators.
  Next moves, in order: raise partition count (must be done ahead of time —
  partitions cannot be reduced, and increasing them re-hashes node→partition
  mapping, so over-provision at 100 partitions from the start); add Redis
  shards linearly with node count (~25K–50K nodes per shard, and keep
  node_id as the shard key so an evaluator's nodes cluster onto few
  shards); then consider Flink with keyed state + RocksDB instead of
  Redis, so window state lives local to the operator and is checkpointed
  rather than fetched over the network every tick — which removes the
  per-heartbeat network op entirely and is the real fix past ~500K nodes.
```

---

### Latency Budget (once at Stage 3)

Distinct from stage transitions — this is the per-hop budget inside the final architecture:

| Hop | Budget | Why it matters |
|---|---|---|
| Node → Receiver → Kafka | P99 < 5ms | Synchronous publish; a slow publish backs up the HTTP thread pool |
| Kafka → Evaluator | consumer lag < 100ms (1 tick) | Lag > 1 tick means the window is evaluated on stale data → late detection |
| Evaluator → Redis | P99 < 5ms | Happens 2× per node per tick (read window, write status) |
| Evaluator → status-changes topic | only on transition (~1/hr/node) | Rare by design — this is why the topic is tiny and compaction is cheap |

**Total detection latency — add up every row of the table above, not two of them.** The commonly-quoted ~450ms silently drops the ingest hop and the consumer-lag hop:

| Term | Worst case | Source |
|---|---|---|
| 3 consecutive empty slots must exist | 300ms | the algorithm itself |
| Slot-boundary misalignment (death lands just after a boundary) | +0–100ms | slots are wall-clock aligned, not death-aligned |
| Node → Receiver → Kafka publish | +5ms | row 1 of the table |
| Kafka → Evaluator consumer lag | +100ms | row 2 — budgeted at 1 tick |
| Evaluation tick granularity | +100ms | the evaluator only looks every 100ms |
| Evaluator → Redis (read window + write status) | +10ms | row 3, 2× 5ms |
| Status-change publish + alert dispatch | +50ms | row 4 + dispatcher |

**≈ 565ms typical worst case, ≈ 665ms with worst-case slot-boundary misalignment** from node death to page. The NFR in Section 3 is written as ≤ 650ms to DOWN declaration and ≤ 700ms to alert delivery for exactly this reason — 500ms is the *width of the window*, not the detection latency, and quoting it end-to-end is a number that contradicts its own budget table.

---

## 🔬 Section 8 — Deep Dives

### Deep Dive 1: Sliding Window Evaluation Algorithm

This is the core of the question. The interviewer handed you a concrete constraint: 5 slots × 100ms, 3 consecutive empty = DOWN.

#### 🎨 Visual — Sliding Window Ring Buffer

```
                Sliding Window — 5 slots × 100ms
                ═══════════════════════════════

Timeline (ms):    0     100   200   300   400   500
                  │      │     │     │     │     │
Slot index:      [0]    [1]   [2]   [3]   [4]
Window covers:  ─────────────────────────────────→ (rolling last 500ms)

SCENARIO A — Node ALIVE (all heartbeats received):
  Heartbeat:    ✓      ✓     ✓     ✓     ✓
  Slot state:  [100]  [200] [300] [400] [500]   ← last heartbeat ts in slot
  Max consecutive empty slots: 0   →  ALIVE

SCENARIO B — Node crashed at t=200ms:
  Heartbeat:    ✓      ✓     ✗     ✗     ✗
  Slot state:  [100]  [200] [ — ] [ — ] [ — ]
                               ↑     ↑     ↑
                        3 consecutive empty  →  DECLARE DOWN

SCENARIO C — Transient network blip (not consecutive):
  Heartbeat:    ✓      ✗     ✓     ✗     ✓
  Slot state:  [100]  [ — ] [300] [ — ] [500]
                        ↑           ↑
                    1 empty       1 empty   →  max consecutive = 1  →  ALIVE

RING BUFFER ROTATION (nothing physically shifts — the write head moves):
  slot index for a heartbeat arriving at time t  =  (t / 100) % 5
  tick N   (now=500): head = s0, oldest→newest = s1 s2 s3 s4 s0
  tick N+1 (now=600): head = s1, oldest→newest = s2 s3 s4 s0 s1
  Each physical slot is overwritten exactly ONCE per 500ms revolution.

TWO CONSEQUENCES THE CODE MUST HONOUR (this is where bugs live):
  1. "Empty" is measured against the FULL 500ms window, not 100ms.
     A healthy node's five slots have ages 0/100/200/300/400ms at
     evaluation time. Aging out at 100ms marks 4 of 5 slots empty and
     declares EVERY healthy node DOWN — the algorithm then detects
     nothing but its own bug.
     Correct test:  slot is empty  ⟺  age ≥ SLOT_COUNT × 100ms = 500ms
     This is exact, not a fudge: a slot is rewritten once per
     revolution, so any value younger than 500ms is from THIS
     revolution and any value ≥ 500ms old is last revolution's leftover.
  2. "Consecutive" means consecutive in TIME, not in array index.
     An empty run can wrap the ring (e.g. slots 3, 4, 0 — which a
     plain 0..4 scan reads as runs of 2 and 1, and misses the DOWN).
     Fix: walk the ring starting at the OLDEST slot, index
     (head + 1) % 5, so a linear run-length scan is already correct.

KEY INVARIANT:
   Walk the 5 slots oldest → newest and find the longest run of
   consecutive empty slots. If max_consecutive_empty ≥ 3: declare DOWN.
   Each slot covers exactly one 100ms window and is rewritten once per
   500ms revolution, so a slot is "empty" ⟺ its stored timestamp is
   missing or ≥ 500ms old (left over from the previous revolution).
```

**Trace the three scenarios above through that invariant** (do this in the interview — it is what proves the rule is implemented, not just stated). Evaluate at `now = 500`, so `head = (500 / 100) % 5 = s0` and the oldest→newest walk is `s1 s2 s3 s4 s0`:

| Scenario | Slot values in walk order (age) | Empty pattern | Max run | Verdict |
|---|---|---|---|---|
| **A — healthy** | 100 (400ms), 200 (300ms), 300 (200ms), 400 (100ms), 500 (0ms) | `. . . . .` | 0 | **ALIVE** ✓ |
| **B — crashed at t=200** | 100 (400ms), 200 (300ms), −200 (700ms), −100 (600ms), 0 (500ms) | `. . x x x` | 3 | **DOWN** ✓ |
| **C — blip, not consecutive** | 100 (400ms), −300 (800ms), 300 (200ms), −100 (600ms), 500 (0ms) | `. x . x .` | 1 | **ALIVE** ✓ |

The negative timestamps in B and C are the previous revolution's leftovers — exactly the values the full-window age check is there to reject. Note what the walk order buys you in B: the three empties are physical slots `s3 s4 s0`, which wrap; in index order they look like a run of 2 and a run of 1 and the node is never declared DOWN.

#### Implementation Options

| Option | Storage | Mechanism | Complexity | Notes |
|---|---|---|---|---|
| **A — Redis BITFIELD** | 5-bit ring per node_id in Redis | Each bit = slot is/isn't filled; rotate every 100ms; scan for 3 consecutive 0s | O(1) per heartbeat; O(slots) per evaluation | Most compact; Redis bitfield ops are atomic |
| **B — Redis HASH (timestamps)** | Hash with 5 fields per node_id: `slot_0`..`slot_4` = last heartbeat ts | On heartbeat arrival, set `slot = current_ts`; evaluator checks age of each slot | O(5) per evaluation | Easier to debug (timestamps readable); slightly more memory |
| **C — Redis SORTED SET (event log)** | One sorted set per node_id; score = timestamp; trim to last 500ms | Evaluator counts events per 100ms bucket for the last 500ms | O(log N) per insert; O(5) per eval | Overkill for fixed slots; better for variable-window queries |

**Decision: Option B (Redis HASH with timestamps).** Slots are fixed (5 total); the HASH with 5 fields is readable and debuggable. Each heartbeat arrival sets `slot_{(arrived_at / 100) % 5} = arrived_at`, conditionally (see the write path below). The evaluator runs every 100ms, reads all 5 fields, walks them oldest → newest, marks a slot empty when its timestamp is missing or **≥ 500ms old (the full window, not one slot width)**, and counts the max consecutive empty slots in that walk order.

**Steps in plain English (evaluator, one node):**

1. **Take `now` once** — a single reading for the whole evaluation, so the five age comparisons are mutually consistent.
2. **Read all 5 slots in one round-trip** — `HGETALL` on `node:{id}:slots`. One network op, not five.
3. **Find the head slot and walk oldest → newest** — head is `(now / 100) % 5`; the oldest slot is the one immediately after it. Walking in this order makes a wrapped empty run (slots 3, 4, 0) appear contiguous, so the plain run-length scan in step 4 is correct with no wrap-around logic.
4. **Mark each slot empty against the FULL window** — empty when the timestamp is missing or `now - ts >= SLOT_COUNT * SLOT_WINDOW_MS` (500ms). Not `> SLOT_WINDOW_MS` — a healthy node's oldest slot is legitimately 400ms old.
5. **Count the longest run of consecutive empty slots** in walk order.
6. **Transition** — run ≥ 3 means DOWN, otherwise ensure ALIVE.

**Pseudocode (evaluator loop for one node):**
```java
// Evaluator tick — runs every 100ms per consumer-assigned node
public void evaluateNode(String nodeId) {
    // Step 1 — one clock reading for the whole evaluation
    long now = System.currentTimeMillis();
    // Step 2 — read all 5 slot timestamps from Redis in one round-trip
    Map<String, String> slots = redis.hgetAll("node:" + nodeId + ":slots");
    // Step 3 — walk the ring oldest to newest so a wrapped run stays contiguous
    int head = (int) ((now / SLOT_WINDOW_MS) % SLOT_COUNT);
    long fullWindowMs = (long) SLOT_COUNT * SLOT_WINDOW_MS;
    boolean[] empty = new boolean[SLOT_COUNT];
    for (int pos = 0; pos < SLOT_COUNT; pos++) {
        int slotIndex = (head + 1 + pos) % SLOT_COUNT;
        String ts = slots.get("slot_" + slotIndex);
        // Step 4 — a slot is rewritten once per 500ms revolution, so a value
        // at least one full window old is last revolution's leftover = empty
        empty[pos] = (ts == null || now - Long.parseLong(ts) >= fullWindowMs);
    }
    // Step 5 — count max consecutive empty slots
    int maxConsecutive = 0;
    int current = 0;
    for (boolean isEmpty : empty) {
        if (isEmpty) {
            current++;
            maxConsecutive = Math.max(maxConsecutive, current);
        } else {
            current = 0;
        }
    }
    // Step 6 — transition logic
    if (maxConsecutive >= FAILURE_THRESHOLD) {
        transitionToDown(nodeId);
    } else {
        ensureAlive(nodeId);
    }
}
```

> **Lesson learned the hard way (Aug 2026):** the first version of this file aged slots out at `now - ts > SLOT_WINDOW_MS` (100ms) while the window was 5 × 100ms. A perfectly healthy node has slot ages of 0/100/200/300/400ms at evaluation time, so 3–4 slots were marked empty every tick and **every healthy node transitioned to DOWN**. The lesson generalises: whenever a ring buffer stores absolute timestamps, the staleness bound is the **revolution period** (`slots × slot_width`), never the slot width. Always trace a *healthy* input through the code — a bug that fires on the happy path is invisible if you only trace the failure case.

#### The Write Path — Conditional (Not Last-Writer-Wins)

The obvious write is `HSET node:{id}:slots slot_{i} <ts>` and the obvious defence is "HSET is atomic, last writer wins, that's fine." **It is not fine here.** A single node's heartbeats are spread by the load balancer across N receivers, so two heartbeats from the same node are processed by two different processes concurrently. Under normal network reordering, the heartbeat stamped `t=300` can reach Redis *after* the one stamped `t=400` and overwrite the slot with the older value. The slot now looks 100ms staler than reality, and near the 500ms boundary that flips a filled slot to empty — nudging a healthy node toward a false DOWN. Kafka keying does not save you either: keying by `node_id` guarantees order *within* a partition, but the N receivers produce into that partition concurrently, so the partition's order is arrival-at-broker order, not the node's send order.

**Steps in plain English (conditional slot write):**

1. **Carry the timestamp on the event** — the receiver stamps `arrived_at` once and it travels with the heartbeat, through Kafka, to the writer. Never re-derive it at write time (that is the same class of bug as `DEFAULT NOW()` in §9).
2. **Derive the slot from the carried timestamp**, not from the writer's current clock — so a replayed record lands in the slot it originally belonged to.
3. **Read the slot's existing value and compare** — inside the script, so the read and write are one atomic step.
4. **Write only if strictly newer** — an older or duplicate heartbeat is a no-op, which also makes the write idempotent under Kafka replay after a rebalance.

```lua
-- KEYS[1] = node:{id}:slots
-- ARGV[1] = arrived_at millis (stamped by the receiver, carried on the event)
-- ARGV[2] = SLOT_COUNT (5)
-- ARGV[3] = SLOT_WINDOW_MS (100)
local arrived = tonumber(ARGV[1])
local slotCount = tonumber(ARGV[2])
local slotWidth = tonumber(ARGV[3])
local field = 'slot_' .. tostring(math.floor(arrived / slotWidth) % slotCount)
local prev = redis.call('HGET', KEYS[1], field)
if prev ~= false and tonumber(prev) >= arrived then
    return 0
end
redis.call('HSET', KEYS[1], field, arrived)
return 1
```

Two properties worth naming out loud: the script is a single atomic Redis operation (Redis runs Lua single-threaded, so no other client interleaves between the `HGET` and the `HSET`), and it is **monotonic** — a slot's value never moves backwards. Monotonicity is what makes replay-after-rebalance safe, and it is why "last writer wins" is the wrong default for anything a clock reads.

---

### Deep Dive 2: Distributed Coordination — Who Declares DOWN?

**The spine of the question.** A single evaluator process monitoring all nodes is the obvious starting point — and it's a SPOF. If the evaluator crashes, it stops checking, but all nodes appear ALIVE forever (no transitions are published). This is a silent failure in a health-monitoring system — the worst kind.

#### Options Table

| Option | How it works | Gain | Lose |
|---|---|---|---|
| **A — Single evaluator + hot standby** | Primary holds a Redis lock; standby polls lock with TTL; if primary dies, standby acquires lock and takes over | Simple; one Redis lock per node shard; widely understood | Lock TTL defines failover latency (30–60 seconds if lock TTL is 30s); standby must rebuild ring buffer state |
| **B — Partitioned Kafka consumer group** | 10 evaluator instances each own a partition; a node's heartbeats always land in the same partition (key = node_id); each evaluator owns its shard exclusively | Kafka handles partition assignment automatically; if one evaluator dies, Kafka rebalance gives its partitions to survivors within seconds; retention + offset replay make the handover lossless | Rebalance leaves a 3–5s evaluation gap during which nobody is deciding; the new owner must replay uncommitted records and apply a grace period before it is allowed to declare DOWN |
| **C — Gossip protocol (like Cassandra)** | Each node sends heartbeats to N random monitors; each monitor gossips about nodes it observes; a node is declared DOWN when a quorum of monitors agree it's silent | No SPOF; tolerates monitor failures naturally | Much higher complexity; gossip storm under partition; hard to prove correctness in an interview |

**Decision: Option B (Kafka consumer group).**
- Kafka consumer group assignment is built-in — no custom coordination code
- Heartbeats are keyed by `node_id`: all heartbeats for node_id=X always land on partition_id = hash(X) % 10
- The evaluator for partition 3 owns all nodes whose `node_id` hashes to partition 3
- On evaluator failure, Kafka consumer group rebalances within ~3 seconds (session timeout)
- On rebalance, the new evaluator instance reloads ring buffer state from Redis before starting evaluation
- **Where losslessness actually comes from — say this precisely, it is a favourite follow-up.** A rebalance loses nothing because **Kafka retains the heartbeats and the new owner replays from the partition's last committed offset**. It is *not* because "the state is in Redis instead of the heap." Redis is a materialised view of that replay — it saves the new owner from cold-replaying 500ms of history on every reassignment, and it lets late-joining readers (`GET /v1/nodes/{id}`) see the window without consuming Kafka. Test the two claims against a Redis flush: with retention + replay, the window rebuilds; with "state is in Redis" as the mechanism, it is simply gone. If topic retention were ever shorter than the 500ms window, no amount of Redis would make the handover lossless.
- **Who writes the ring buffer (disambiguate this — it decides whether a rebalance can lose in-flight window state).** At Stage 3 the receiver **only produces to Kafka**; the evaluator that owns the partition is the **sole writer** of `node:{id}:slots` for its nodes. Single-writer-per-node plus the monotonic conditional write means replaying uncommitted records after a rebalance converges to exactly the same slot values. If receivers wrote slots directly at this stage (as they do at Stage 2), the replaying evaluator and the live receivers would both be writing the same keys during the handover, and there would be no offset that describes the window's state

**Failover sequence:**
```
Evaluator-3 crashes at t=0
     │
     ▼  t=3s (Kafka session.timeout.ms)
Kafka rebalances: Evaluator-5 takes over partition 3
     │
     ▼  t=3.1s
Evaluator-5 reloads Redis keys for all node_ids in partition 3
     │
     ▼  t=3.5s
Evaluation resumes with correct window state from Redis
     │
     ▼
No heartbeats received during t=0..3.5s for nodes in partition 3
     │
     ▼
Those nodes have empty slots covering the 3.5s gap → declare DOWN
```
**Problem:** A 3.5-second failover gap causes 3.5s ÷ 100ms = 35 empty slots for all nodes in that partition. Every node in partition 3 would be declared DOWN during failover — false positives at exactly the wrong moment.

**Fix:** After rebalance, the evaluator checks each node's last actual heartbeat — which is simply `max(slot_0 .. slot_4)` from the `HGETALL` it already performs, no extra key and no extra op. If `now - last_heartbeat_ts < FAILOVER_GRACE_PERIOD (10s)`, treat the gap as a monitor failure (not a node failure) and do not declare DOWN. Only after 10 seconds of silence post-rebalance does the evaluator start issuing DOWN declarations.

---

## 🗄️ Section 9 — Data Model / SQL Schema

```sql
-- Registered nodes: the services being monitored
CREATE TABLE nodes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255)   NOT NULL,
    owner_email     VARCHAR(255)   NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    deregistered_at TIMESTAMP,
    CONSTRAINT uq_nodes_name UNIQUE (name)
);

-- Index: list active nodes (exclude deregistered)
CREATE INDEX idx_nodes_active
    ON nodes (created_at DESC)
    WHERE deregistered_at IS NULL;

-- Status change events: append-only log of every ALIVE↔DOWN transition
-- occurred_at has NO DEFAULT on purpose, and there is a dedupe constraint
-- on (node_id, occurred_at, status) — both reasons explained below the DDL
CREATE TABLE status_change_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id         UUID           NOT NULL REFERENCES nodes(id),
    status          VARCHAR(10)    NOT NULL CHECK (status IN ('ALIVE', 'DOWN', 'UNKNOWN')),
    occurred_at     TIMESTAMP      NOT NULL,
    reason          TEXT,
    CONSTRAINT uq_status_change_event UNIQUE (node_id, occurred_at, status)
);

-- Index: get history for a node, time-ordered (serves GET /v1/nodes/{id}/history)
CREATE INDEX idx_status_change_node_time
    ON status_change_events (node_id, occurred_at DESC);

-- Index: recent events across all nodes (dashboard "what just changed?")
CREATE INDEX idx_status_change_recent
    ON status_change_events (occurred_at DESC);
```

**Two schema decisions that look like nitpicks and are not:**

**1. `occurred_at` carries NO `DEFAULT NOW()` — the event must supply it.** The Postgres writer is a Kafka consumer, and Trade-off 3 below describes it failing for 30 minutes and then replaying from its last committed offset. With `DEFAULT NOW()`, every one of those replayed rows is stamped with *catch-up wall time*: 30 minutes of node health history collapses onto a single instant, in ascending-offset order, all within a few seconds of each other. The system's entire output is "when was this node up or down," and the default would silently destroy exactly that — plus the time-travel queries pitched in Section 11 ("what was node-42's status at 14:35?") would answer from a timeline that never happened. Rule to carry into any consumer-written table: **event time travels on the event; only ingest time may be defaulted, and it belongs in a separate `ingested_at` column.**

**2. `UNIQUE (node_id, occurred_at, status)` — because the writer is at-least-once and replays by design.** With `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, every replayed copy of the same transition gets a brand-new primary key, so nothing collides and nothing dedupes — a 30-minute replay after a failure duplicates every event in the overlap between the last commit and the crash. The natural key `(node_id, occurred_at, status)` makes the insert idempotent: write with `INSERT ... ON CONFLICT DO NOTHING`. The alternative, equally acceptable and slightly stronger, is to have the evaluator mint a deterministic `event_id` (UUID v5 over `node_id + occurred_at + status`) at *publish* time and use that as the primary key — same effect, and it survives a schema change to the natural key.

**Redis schema (not SQL — documented here for completeness):**

```
node:{uuid}:status   → STRING "ALIVE" | "DOWN" | "UNKNOWN"
                       cache of the current declared status; the compacted
                       node-status-changes topic is the source of truth
node:{uuid}:slots    → HASH, fields slot_0..slot_4
                       each value = timestamp of that slot's heartbeat
node:{uuid}:reg      → STRING, cache of "registered" | "deregistered"
                       short TTL; Postgres `nodes` is the authority (see
                       Mistake 4 in Section 13) — a cache miss falls
                       through to Postgres, it does NOT mean 404
```

**Dropped key — `node:{uuid}:last_ts`.** An earlier version of this design kept a separate "last heartbeat seen" string for the failover grace-period check. It is pure duplication: `last_ts` is by definition `max(slot_0 .. slot_4)`, and the evaluator already `HGETALL`s all five slots on every tick, so the value is free. Keeping it cost a **second Redis write on every heartbeat** — at 10 heartbeats/sec/node across 100K nodes that is 1M ops/sec of pure redundancy, on the exact resource Section 4 shows to be the binding constraint. Dropping it takes the hot path from ~4 ops to ~3 per heartbeat. Say this out loud in the interview: the instinct to denormalise for read speed is right in Postgres and wrong on a write-saturated Redis.

**Access pattern coverage:**

| Query | Served by |
|---|---|
| Current status of node X | Redis `node:{id}:status` key — O(1) |
| Ring buffer evaluation for node X | Redis `node:{id}:slots` HASH — O(5) |
| Last heartbeat time for node X (failover grace check) | derived: `max(slot_0..slot_4)` from the HASH already read — no extra key, no extra op |
| Is node X registered? (heartbeat 404 check) | Postgres `nodes` = authority; Redis `node:{id}:reg` = cache with TTL; cache miss → read through, never 404 on absence |
| History of node X over last 24h | Postgres `idx_status_change_node_time` |
| "What nodes changed status in the last 5 min?" | Postgres `idx_status_change_recent` |
| List all active nodes (ids + metadata) | Postgres `idx_nodes_active` |
| **List all active nodes *with `status` and `last_heartbeat_at`* (`GET /v1/nodes`)** | **Postgres `idx_nodes_active` for the page of N ids, THEN an N-key Redis `MGET` on `node:{id}:status` plus an N-key pipelined `HGETALL` for `last_heartbeat_at` — those two fields do not exist in Postgres.** A page of 100 nodes = ~200 extra Redis ops. This fan-out is invisible if you read the endpoint table alone, and it must be counted in the Redis op budget. Cap `limit` at 100; use `MGET`/pipelining, never a per-node loop; if the endpoint is hot, serve it from a read model materialised off the compacted `node-status-changes` topic instead |

---

## ⚠️ Section 10 — Trade-offs + Failure Modes

### Trade-off 1: Push (Node Calls Us) vs Pull (We Poll the Node)

- **Chose:** Push model
- **Gain:** Monitor is passive — simpler deployment. Nodes that can't accept inbound connections (inside a VPC with no inbound rules) can still participate. No false positives from monitor-side network issues.
- **Lose:** If the node crashes hard (OOM kill, `kill -9`), the heartbeat SDK thread also dies — clean detection. But if the node is in an infinite loop (CPU saturated, GC frozen), the heartbeat SDK may starve and produce false DOWN — the node is technically running but appears dead.
- **Failure mode (Technical):** A node in a prolonged GC pause (>500ms) stops sending heartbeats. 5 slots elapse with no heartbeat. Monitor declares DOWN. GC unpauses at 600ms — node is actually alive. Monitor sees next heartbeat → transitions back to ALIVE. False DOWN was 100ms. At Confluent scale, this triggers a PagerDuty page for a transient GC — alert fatigue.
  [Confluent / Streaming Business Impact]: If the status-change event is consumed by a traffic router that pulls this node from the load balancer rotation, a 100ms GC pause just removed a healthy node from prod traffic for the alert response time (~5 minutes). Mitigation: require 3 consecutive DOWN evaluations (1.5s total) before publishing a DOWN event to the status-changes Kafka topic.

### Trade-off 2: Kafka Consumer Group for Evaluator Coordination

- **Chose:** Kafka consumer group (Option B above)
- **Gain:** Built-in partition rebalancing; no custom leader election code; node assignment is deterministic (hash-routed); the handover is lossless because Kafka retains the heartbeats and the new owner replays from the last committed offset (Redis holds a materialised view of that replay, it is not the durability mechanism).
- **Lose:** Rebalance causes a 3–5 second evaluation gap; all nodes in a rebalanced partition appear DOWN during failover unless a grace period is implemented. Adding the grace period adds code complexity.
- **Failure mode (Technical):** During a rolling deploy of the evaluator (all 10 instances restarting in sequence), the partition group rebalances repeatedly. Each rebalance triggers a 10s grace period per partition per rebalance. A real failure during the rolling deploy would be masked for up to `10s × num_rebalances`. Mitigation: blue/green deploy of the evaluator rather than rolling restart.
  [Confluent / Streaming Business Impact]: The evaluator Kafka consumer group is the bridge between the raw heartbeat stream and the status-change topic. If the group lag grows during a rebalance, heartbeat events queue up. Kafka retains them (default retention), so no heartbeats are lost — but the liveness decision is delayed by the lag. Convert lag to time using the *aggregate* rate on the partition, not one node's 100ms interval: at 10K nodes the topic carries 100K messages/sec, so a lag of 10,000 messages = **0.1 seconds** of delayed decisions. (The tempting arithmetic — 10,000 × 100ms = 1,000 seconds — is wrong by four orders of magnitude; it prices every message as if it were the same node's next heartbeat.) The number that should alarm you is therefore much smaller than it looks: 10,000 messages of lag already eats the entire 100ms consumer-lag row of the latency budget. Monitor consumer lag on the `heartbeats` topic as the leading health indicator for the evaluator itself.

### Trade-off 3: Log-Compacted Status-Change Topic vs. Event-Sourced History Table

- **Chose:** Hybrid — log-compacted Kafka topic for current state; Postgres append-only table for history
- **Gain:** Downstream consumers (alert dispatcher, traffic router) read from Kafka — they always see the latest status per node_id without querying a DB. The compacted topic IS the status store for streaming consumers. Postgres is the source of truth for historical queries (dashboard, audit).
- **Lose:** *Three* stores hold the same boolean, not two. Current status is materialised in **Redis** (`node:{id}:status`, serving `GET /v1/nodes/{id}`), in the compacted **`node-status-changes` topic** (serving the traffic router and every other streaming consumer), and historically in **Postgres**. The Postgres lag is benign and self-healing. The Redis-vs-topic split is not.
- **Failure mode (Divergence — name this, the file previously only named the Postgres lag):** On a transition the evaluator does two writes: `SET node:{id}:status DOWN` in Redis, and a produce to `node-status-changes`. **No transaction spans them.** If the evaluator crashes between them — or the produce times out and the retry is dropped on shutdown — the two stores disagree *permanently*, because nothing in the design ever re-compares them. Next tick the evaluator sees the node still DOWN, computes "no transition," and writes nothing; the divergence is now invisible and self-perpetuating. The user-visible result is the worst possible outcome for this system: `GET /v1/nodes/{id}` says ALIVE while the traffic router (reading the compacted topic) has pulled the node from rotation, or the exact inverse — traffic routed to a node the API calls dead. A monitoring system whose single output is one boolean is reporting two different values for it.
  **Repair path — pick one and state it, do not leave both stores authoritative:**
  1. **Topic is the sole source of truth; Redis is a rebuilt cache.** The evaluator produces to `node-status-changes` first and *only* the compacted topic is authoritative. A small consumer tails the topic and writes `node:{id}:status`, so Redis is a projection that self-heals on replay and can be rebuilt from scratch after a flush. `GET /v1/nodes/{id}` then reads a store that cannot disagree with the router, because they are fed by the same log. This is the answer that matches the rest of the design and the one to give.
  2. **Re-derive on assignment.** On every partition assignment (and on a periodic sweep), the evaluator recomputes each owned node's status from its Redis ring buffer and re-publishes any status that disagrees with the compacted topic's latest value. Cheaper to bolt on, but it only converges as often as you sweep, and it needs the evaluator to read the compacted topic as well as write it.
  In both cases: **order the writes so the authoritative store is written first**, and make the follower write idempotent. Writing the cache first is the version that can strand a lie.
- **Failure mode (Technical):** Postgres writer consumer fails for 30 minutes. During that time, 30 minutes of status-change events are on the Kafka topic but not in Postgres. Dashboard queries show the last-known status (stale). When the consumer restarts, it replays from the last committed offset — Postgres catches up automatically. No data loss because Kafka retains the events. **This replay is precisely why §9 removes `DEFAULT NOW()` from `occurred_at` and adds `UNIQUE (node_id, occurred_at, status)`:** without the first, the catch-up stamps 30 minutes of history onto one instant; without the second, at-least-once delivery duplicates every event in the overlap window and `gen_random_uuid()` hands each duplicate a fresh primary key so nothing collides. The replay story only holds if the writer is idempotent and the event carries its own time.
  [Confluent / Streaming Business Impact]: This is a textbook use case for Tableflow. If the status-change events are a Kafka topic, Tableflow can materialize them into an Iceberg table in real time — replacing the hand-rolled Postgres writer consumer with a managed pipeline. Iceberg's snapshot-based reads give time-travel queries on node health (e.g., "what was the status of node-42 at 14:35?") for free, without a separate Postgres history table. This is the explicit Confluent value proposition: replace the bespoke consumer-writer with Tableflow.

---

## 🌊 Section 11 — Confluent / Tableflow Angle

**Three concrete connections to make — not generic "use Kafka" advice:**

**1. Heartbeat stream → Kafka topic, key = `node_id`.** The heartbeat receiver publishes each heartbeat as a Kafka message. key = node_id ensures all heartbeats for the same node always land on the same partition — this is what allows the evaluator consumer to process each node's window in strict order without cross-partition coordination. Direct cross-reference to log compaction: `../../../SystemDesignConcepts/Core-Architecture/Service-Communication/60-kafka-internals.md`.

**2. Status-change topic as a log-compacted topic.** The `node-status-changes` Kafka topic has cleanup policy `compact`. Key = node_id. The latest value per key = the node's current declared status. A consumer group that joins late (new dashboard, new alert system) starts from the compacted snapshot and gets the current status of every node without replaying every historical heartbeat. This is exactly log compaction's use case: "current state per key, from an event stream." Cross-reference: `60-kafka-internals.md §Compaction`.

**3. Tableflow as the Postgres writer.** The hand-rolled Postgres consumer (writing StatusChangeEvents) can be replaced by a Tableflow pipeline: topic `node-status-changes` → Iceberg table `node_status_history`. Benefits over Postgres: schema evolution without downtime (Iceberg's hidden partitioning on `occurred_at`); time-travel queries for free; no bespoke consumer code to maintain; Tableflow handles offset management. This is the exact pitch Confluent makes for Tableflow as a Kafka → analytics store bridge.

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1)
**Q: "What happens to the ring buffer state when your evaluator restarts?"**
> Ring buffer state lives in Redis (not in-memory in the evaluator process), so on restart the evaluator reloads rather than cold-starting. Be precise about *why* nothing is lost, though: the guarantee comes from Kafka retaining the heartbeats and the restarted evaluator replaying from its last committed offset — Redis just spares it that replay. The only gap is the evaluation period during the restart (~3s for Kafka rebalance). We handle this with a grace period: if the last Redis-recorded heartbeat is recent (within 10s), we treat the gap as a monitor fault, not a node fault.

### Deep Probe (Tier 2)
**Q: "What if the evaluator clock drifts by 200ms? The node's heartbeat timestamps say one thing, but the evaluator's `NOW()` says something different."**
> First, correct the premise: the node's clock is irrelevant. The receiver stamps `arrived_at` from the **monitor's** clock and that stamp is what the slot stores, so node clock drift cannot affect evaluation at all. The drift that actually matters is **receiver-vs-evaluator skew** — two different hosts, two different clocks, one writing the timestamp and the other supplying `now`. Generic "NTP desync" is the wrong framing; what breaks the algorithm is specifically that `now - ts` mixes two clocks.
>
> Second, quantify the tolerance, because the corrected staleness check has a real budget built in. A slot is empty only at age ≥ 500ms, and a healthy node's *oldest* filled slot is 400ms old. So there is a **full 100ms of slack** — one slot width — before receiver-evaluator skew can misclassify any slot. A 200ms skew does break it (the oldest slot reads as 600ms old and is wrongly marked empty, and 2–3 such slots in a row is a false DOWN); a 20ms skew is harmless. Budget: keep receivers and evaluators NTP-disciplined to ≤ 10ms, alert on `abs(receiver_clock - evaluator_clock)` exceeding 25ms, and note that the *old* 100ms-age check had literally zero skew tolerance — any drift at all flipped a slot.
>
> Third, the fix if you want skew off the table entirely: **use one clock**. Have the Lua write script and the evaluator both derive time from the Redis server (`TIME`), so `ts` and `now` come from the same machine and the difference is a true elapsed interval. Cost: the receiver's `arrived_at` must then be Redis-stamped, which conflicts with carrying the timestamp on the event for replay — so in practice you keep the carried stamp and pay for NTP discipline.
>
> **Correction to a mitigation this file used to give:** "evaluate against `slot_index`, a logical counter, not a wall-clock comparison" does *not* work as stated, and it contradicts the implementation in Deep Dive 1, which is deliberately a wall-clock age comparison. The evaluator still has to convert its own `now` into a current slot index to know which slot is the head — so the same two clocks are still being subtracted, just quantised to 100ms boundaries. Quantising does not remove skew; it rounds it. A genuine logical-clock design would require the *receiver* to assign and carry the slot index and the evaluator to advance its head only on observed heartbeats — at which point a node that is legitimately dead never advances anything, and the detector stops detecting. That is why this design stays on wall-clock ages with an explicit skew budget.

### Cross-Concept Probe (Tier 3)
**Q: "How is this different from how Kafka itself knows if a broker is dead?"**
> Kafka uses ZooKeeper (or KRaft) for broker liveness: each broker maintains an ephemeral ZooKeeper node with a session timeout. If the broker's ZK session expires (no heartbeats to ZK for `session.timeout.ms`), ZK deletes the ephemeral node — this is the "broker died" event. The controller watches for ephemeral node deletions. Structurally identical to our system: heartbeats → session TTL → declare down → notify controller. In KRaft the shape is the same with the ZK dependency removed: brokers send `BrokerHeartbeat` requests to the active controller every `broker.heartbeat.interval.ms` (default 2s), and the controller fences a broker that misses them for `broker.session.timeout.ms` (default 9s) — a heartbeat, a fixed timeout, a binary verdict. Kafka has a *second*, independent liveness notion at the replica level: a follower drops out of the ISR when it fails to fetch up to the leader's log end offset within `replica.lag.time.max.ms` (default 30s). The difference from our design: Kafka uses event-driven notification (ZK watch callback / controller-side session expiry) rather than a polling evaluator loop; ZK or the controller handles consensus on a single flag per broker, while our system evaluates a sliding window per node.
>
> **The contrast to actually draw — and get this right, it is a favourite trap.** **Kafka does not use phi accrual anywhere.** Every liveness decision in Kafka is a *fixed threshold*: ZK `session.timeout.ms`, KRaft `broker.session.timeout.ms`, ISR `replica.lag.time.max.ms`. Phi accrual — the adaptive detector that fits a distribution to inter-arrival times and emits a continuous suspicion score instead of a boolean — is **Cassandra's gossip layer and Akka Cluster**, not Kafka. (This file names it correctly in the Section-0 comparison table; the temptation is to reach for it again here because it sounds sophisticated.) So the honest comparison is the *stronger* one: our 3-consecutive-empty-slots rule is architecturally **the same family as Kafka's** — a fixed, operator-tuned threshold over observed heartbeat silence, chosen for predictability and debuggability over adaptivity. Kafka's parameters are just three orders of magnitude larger (seconds, not milliseconds) because a broker restart is expensive and a false fence is costly. If you want the adaptive contrast, name Cassandra, and note the trade-off honestly: phi accrual auto-tunes to a noisy network but its suspicion threshold is far harder to reason about during an incident, which is exactly why Kafka — and this design — did not choose it.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1:** Declaring a node DOWN on the first missed heartbeat → **Why it's wrong:** a single missed heartbeat is a network blip, a GC pause, or a busy CPU — not a node failure. The 3-consecutive-slots rule exists precisely to distinguish transient blips from actual failures. One miss → immediately alert = massive false positive rate in any real distributed system. → **Say instead:** "I evaluate max consecutive empty slots; only 3 or more consecutive empty slots triggers a DOWN transition."

- **Mistake 2:** Storing the ring buffer in evaluator memory (not Redis) → **Why it's wrong:** if the evaluator crashes or the consumer group rebalances, the in-memory ring buffer is lost. The new evaluator starts with an empty buffer — every node appears to have no heartbeats → massive false DOWN storm at the moment of evaluator restart. → **Say instead:** "Ring buffer state is in Redis, keyed by node_id, so the new owner reloads instead of cold-starting — but the reason the handover is *lossless* is Kafka retention plus replay from the last committed offset, not the fact that Redis is off-heap."

- **Mistake 3:** Using `DELETE /v1/nodes/{id}/heartbeat` instead of `POST /v1/nodes/{id}/heartbeat` → **Why it's wrong:** a heartbeat is an event being created, not a resource being deleted. The heartbeat is a new occurrence of "I am alive." POST is the correct verb for creating a new event/resource. DELETE means you are removing an existing named resource. → **Say instead:** `POST /v1/nodes/{id}/heartbeat`; the response confirms receipt; idempotency key is optional since heartbeats are fire-and-forget.

- **Mistake 4:** Proposing a Bloom filter to check if a heartbeat came from a known node → **Why it's wrong:** Bloom filters answer "has this element ever been seen?" (set membership) — they are for avoiding expensive lookups when you expect most queries to be misses. Here, checking if a `node_id` is registered is a *point lookup* on a 10K-row table with a unique index — it's already O(1). A Bloom filter buys nothing over a Redis lookup and adds false-positive complexity. Bloom filters are the wrong tool for time-window liveness tracking. → **Say instead:** "Postgres `nodes` is the registration authority; Redis `node:{id}:reg` is a TTL'd cache in front of it, and a cache **miss** means *read through to Postgres*, not 404."
  **Do NOT say "if the Redis key doesn't exist, the node is not registered; return 404"** — that makes a cache the authority for a fact it never owned. Registration writes go to Postgres only (`POST /v1/nodes`), and nothing in the design seeds `node:{id}:status` at registration time, so a freshly registered node 404s until its first heartbeat, and a Redis restart or `FLUSHALL` makes **every** node 404 at once. Follow that through: per the endpoint story for `POST /v1/nodes/{id}/heartbeat`, a 404 tells the SDK it is not being monitored and it alerts its owner — so a single cache flush pages every owner in the fleet simultaneously, telling each of them their service is unmonitored, while the monitoring system itself is fine. Correct shape: read `node:{id}:reg`; on hit, answer from the cache; on miss, query Postgres, populate the cache with a short TTL, and only 404 when **Postgres** says the node does not exist or is deregistered. Cache absence is not evidence of absence.

- **Mistake 5:** Using a single global evaluator process → **Why it's wrong:** SPOF. The evaluator is the most critical component — if it crashes, all nodes appear ALIVE forever. → **Say instead:** "Kafka consumer group with 10 evaluator instances, each owning a partition shard; if one evaluator crashes, Kafka rebalance assigns its partitions to surviving instances within ~3 seconds."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/nodes/{id}/heartbeat` → 200 (not 201, not 204); unknown node_id → 404 (not 200 or 400); heartbeat flood → 429 with `Retry-After`; `GET /v1/nodes/{id}` serves current status; `GET /v1/nodes/{id}/history` cursor-paginated |
| **Trade-off Defense** | ✅ | Push vs pull: push chosen; pull failure mode (monitor partition → false DOWN) explained. Kafka consumer group vs single evaluator: SPOF eliminated; rebalance gap + grace period trade-off articulated. Log-compacted topic vs Postgres: hybrid chosen; Tableflow replacement named. |
| **SQL / Data Modeling** | ✅ | `UNIQUE(name)` on nodes produces 409 on duplicate register; `status_change_events` is append-only (no UPDATE, no DELETE); index `(node_id, occurred_at DESC)` serves history query; partial index `WHERE deregistered_at IS NULL` serves active node list. `occurred_at` deliberately has **no** `DEFAULT NOW()` (a replaying consumer would stamp catch-up time and collapse the history it exists to record) and `UNIQUE (node_id, occurred_at, status)` makes the at-least-once writer idempotent. Redis HASH schema for ring buffer explicitly documented, with Postgres — not Redis — as the registration authority. |
| **Distributed Systems** | ✅ | Spine of the design: "who declares DOWN?" → Kafka consumer group coordination; rebalance gap quantified (3s) and mitigated (grace period); sole-writer-per-node stated explicitly; failover sequence written out step-by-step; the Redis-vs-compacted-topic divergence risk (two stores for one boolean, no transaction spanning them) named with a repair path. |
| **Pipeline Resilience** | ✅ | Heartbeats survive evaluator crash (**Kafka retention + offset replay** — this, not "state is in Redis", is the losslessness mechanism); status-change events survive Postgres writer failure (consumer replays from last offset, idempotently); log-compacted status topic means downstream consumers always have current state on startup without full replay. |
| **Concurrency** | ✅ | Multiple heartbeats from the same node within one 100ms slot — **"HSET is atomic, last writer wins" is atomic but NOT correct here.** One node's heartbeats fan out across N receivers via the LB, so two are in flight concurrently; under network reordering the heartbeat stamped `t=300` can land *after* the one stamped `t=400` and overwrite the slot with a staler timestamp, which the age check can then read as empty. Kafka keying does not fix it — key = node_id preserves order *within* a partition, but N receivers produce into that partition concurrently, so partition order is arrival-at-broker order, not send order. Fix: a conditional (compare-and-set) Lua write, `if new > old then HSET`, making the slot monotonic and the write idempotent under replay — see Deep Dive 1 §The Write Path. Concurrent evaluator instances do not compete: each owns exclusive partitions via Kafka consumer group assignment — no evaluator evaluates the same node_id as another. |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "This is a push-based health monitoring system where the core algorithm is a 5-slot × 100ms ring buffer stored in Redis per node — each slot records whether a heartbeat arrived in that 100ms window, and 3 or more consecutive empty slots triggers a DOWN declaration. Two details in that algorithm are where implementations actually break: a slot is 'empty' when its timestamp is at least one *full window* (500ms) old — not one slot width, since a healthy node's oldest slot is legitimately 400ms old — and the empty run must be counted walking the ring oldest-to-newest, because a run that wraps the ring reads as two short runs in index order. The architectural spine is the distributed coordination question: a single evaluator is a SPOF, so we use a Kafka consumer group where each evaluator instance owns a partition shard of node_ids via key-based hash routing — no custom leader election needed. A rebalance (3–5 second gap) is lossless because Kafka retains the heartbeats and the new partition owner replays from the last committed offset — Redis holds a materialised view of that replay, it is not the durability mechanism — and we add a 10-second grace period post-rebalance to avoid false DOWN declarations during failover. Redis throughput, not memory, is the scaling constraint: at ~4 ops per heartbeat a single instance carries only 25K–50K nodes, so sharding Redis is mandatory to hit the 100K-node target. Status changes are published to a log-compacted Kafka topic keyed by node_id, so any downstream consumer (alert dispatcher, traffic router) always gets the latest declared status per node on startup without replaying all historical heartbeats. The Tableflow angle is direct: replace the hand-rolled Postgres history writer with a Tableflow pipeline that materializes the status-change topic into an Iceberg table, enabling time-travel queries on node health with no bespoke consumer code."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Covers Health Check / wasAlive monitoring (Type 2 full design). 6 endpoints with full contract and status code stories. ASCII HLD diagram with stage transition thresholds. ASCII sliding window ring buffer visualization. Deep dive 1: algorithm + Redis HASH implementation with Java pseudocode. Deep dive 2: Kafka consumer group coordination + failover gap grace period. 3 trade-offs with two-layer failure modes. Confluent angle: compacted status topic + Tableflow Iceberg materialization. |
| Aug 2026 | **Section 7 rebuilt as an incremental staged design.** Previously opened directly with the fully-scaled Kafka + Redis + consumer-group architecture — no build-up, no breaking points, and the trailing "Stage transition thresholds" block was mislabelled (those were per-hop latency budgets, not stage transitions). Now: **Stage 1** single process with in-memory `ConcurrentHashMap` ring buffer (~1K nodes) → **Stage 2** stateless receivers + shared Redis window state (~10K nodes, the stated requirement) → **Stage 3** Kafka consumer group for partitioned evaluation (100K+ nodes), each with a quantified BREAKING POINT naming two distinct failure causes plus an observable symptom. Stage 1's two causes: a single process cannot be load-balanced (split window → false DOWN), and restart wipes all buffers (mass false-alert storm on every deploy). Stage 2's two causes: single evaluator cannot scan 50K nodes inside one 100ms tick (~1s per scan, 10× over budget), and it is a silent SPOF (dashboards keep reading ALIVE because nothing flips the status). Added a "WHY KAFKA EARNS ITS PLACE HERE" block (partition-per-consumer = sharding function; rebalance = free failover; Redis-not-heap state is what makes rebalance lossless), a CEILING OF STAGE 3 with ordered next moves (over-provision partitions early since they cannot be reduced → shard Redis → Flink keyed state + RocksDB), and relocated the per-hop latency budget into its own table with the end-to-end detection-latency total (~450ms worst case). |
| Aug 2026 | **Redis key-schema block reflowed** — three lines ran 87–98 characters, past the 80-column ASCII limit in `AGENTS.md`, so they wrapped unpredictably in narrower viewers. Split each key onto a description line beneath it. No semantic change; the Section 7 stage diagrams from the previous entry were already box-drawing and within width. |
| Aug 2026 | **Correctness pass — the core algorithm was broken, plus nine supporting fixes.** ⭐ **The headline bug (Deep Dive 1, `evaluateNode`):** the staleness test read `empty[i] = (ts == null \|\| now - ts > SLOT_WINDOW_MS)` — aging a slot out at **100ms** when the sliding window is SLOT_COUNT(5) × 100ms = **500ms**. A perfectly healthy node heartbeating every 100ms has slot ages of 0/100/200/300/400ms at evaluation time, so 3–4 slots were marked empty on **every** tick, `maxConsecutive >= FAILURE_THRESHOLD(3)` fired, and **every healthy node transitioned to DOWN**. The published algorithm detected nothing but its own bug — and it was invisible because the worked scenarios only ever traced the *failure* case. Fixed to `now - ts >= SLOT_COUNT * SLOT_WINDOW_MS` (the revolution period), which is exact rather than a fudge: each physical slot is rewritten exactly once per 500ms revolution, so a value younger than 500ms is from this revolution and a value ≥ 500ms old is last revolution's leftover. Fixing the threshold alone was not enough — it exposed a second latent bug. The empty run can **wrap the ring** (e.g. slots 3, 4, 0), which the linear 0..4 scan reads as runs of 2 and 1 and never declares DOWN. The loop is now fed by a walk that starts at the oldest slot, `(head + 1) % SLOT_COUNT` where `head = (now / 100) % 5`, so "consecutive in time" and "consecutive in array index" coincide and the max-consecutive-run loop is unchanged. Both fixes verified against the three scenarios in the ASCII visual (A healthy → ALIVE, B crashed → DOWN, C non-consecutive blip → ALIVE) and the trace added to the doc as a table. Prose, visual, and code now agree; a dated lesson-learned callout records the generalisation (a ring buffer's staleness bound is the revolution period, never the slot width) and the meta-lesson (always trace a *healthy* input — a bug that fires on the happy path hides behind failure-case tracing). **Supporting fixes:** (2) **Redis throughput was understated ~20–40×** — the hot path does ~4 ops per heartbeat (registration check, rate-limit `INCR`, slot write, `last_ts` write) = ~40 ops/sec/node, so one instance carries ~25K–50K nodes, not the ~1M the 100MB memory figure implied; Section 4 gains an ops-per-heartbeat table and Stage 3's ceiling was corrected from ~1M nodes to ~100K–200K with 2–4 shards, with Redis sharding restated as **mandatory**, not "if needed". (3) **Stage 2's breaking point** blamed only the evaluator scan; Redis server ops/sec saturates at the same load (50K nodes = 2M ops/sec), so it is now named as a co-binding ceiling, and Stage 3 shards Redis rather than only adding evaluators — otherwise the stage transition does not fix what broke. (4) **The losslessness story was told two incompatible ways.** Settled: a rebalance is lossless because of **Kafka retention + offset replay**, *not* because "state is in Redis instead of heap" (Redis is a materialised view of that replay); and the ring-buffer writer is now stated explicitly — receiver at Stage 2, partition-owning evaluator as **sole writer** at Stage 3, which is what makes replay converge. (5) **Detection-latency total contradicted its own table** — the quoted ~450ms omitted the ingest and consumer-lag rows; corrected to ≈565ms (≈665ms with slot-boundary misalignment) with every term itemised, and the NFR widened to ≤650ms to declaration / ≤700ms to page. (6) **Factual error in the Tier-3 probe:** "the phi accrual failure detector used between Kafka brokers" — **Kafka does not use phi accrual anywhere**; it is Cassandra gossip and Akka Cluster (which this file states correctly in Section 0). Replaced with the stronger, true contrast: Kafka liveness is *entirely* fixed-threshold (ZK `session.timeout.ms`, KRaft `broker.session.timeout.ms`, ISR `replica.lag.time.max.ms`), which is exactly why our 3-consecutive-slot rule is architecturally the same family. (7) **"HSET is atomic, last writer wins, which is correct"** — atomic, but wrong here: one node's heartbeats fan out across N receivers, so reordering lets an older heartbeat overwrite a newer timestamp and the age check reads the slot as empty. Kafka keying does not save it (N receivers produce concurrently into one partition). Replaced with a conditional Lua compare-and-set (`if new > old then HSET`), which also makes the write idempotent under replay. (8) **Schema bugs:** `occurred_at DEFAULT NOW()` removed — the Postgres writer explicitly replays after failure, and the default would stamp 30 minutes of health history onto one catch-up instant, corrupting the exact record the system exists to produce and the time-travel queries pitched in Section 11; `UNIQUE (node_id, occurred_at, status)` added because an at-least-once writer plus `id DEFAULT gen_random_uuid()` gives every duplicate a fresh PK so nothing dedupes; and registration authority moved from Redis to Postgres (Mistake 4 previously advised 404-on-missing-Redis-key, so a `FLUSHALL` would 404 every node and page every owner that their service is unmonitored). (9) **Divergence risk named for the first time:** current status lives in both Redis and the compacted `node-status-changes` topic with no transaction spanning the two writes, so an evaluator crash between them diverges them permanently — `GET /v1/nodes/{id}` and the traffic router then disagree about the one boolean the system produces. Added with a repair path (topic as sole source of truth with Redis rebuilt from it, or re-derive on partition assignment). (10) **Smaller corrections:** the clock-drift mitigation ("evaluate against a logical `slot_index`") contradicted the wall-clock implementation and is retracted with reasoning — the drift that matters is receiver-vs-evaluator skew, and the corrected 500ms check has a built-in 100ms skew budget the old 100ms check did not; consumer groups do not add parallelism beyond partition count (Section 5); an in-heap 10K-node × 5-slot scan is tens of microseconds, not 100ms, so Stage 1's symptom was rewritten; a lag of 10,000 messages is 0.1s at aggregate rate, not 1,000s; the per-node rate limiter both costs a hot-path Redis op and, by dropping a 429'd heartbeat, can manufacture the empty slots that produce a false DOWN; `GET /v1/nodes` needs an N-key Redis `MGET` fan-out for `status`/`last_heartbeat_at` that appeared in no access-pattern table or op budget; and `node:{id}:last_ts` was dropped as a redundant write per heartbeat since it is exactly `max(slot_0..slot_4)` from the `HGETALL` the evaluator already performs. |
