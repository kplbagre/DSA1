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
- Liveness detection latency: ≤ 500ms from last heartbeat to DOWN declaration (5 slots × 100ms)
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

**Status store read volume:**
- Clients query node status at dashboard refresh or alert evaluation: ~1K reads/sec — Redis serves this at microsecond latency
- Historical query (StatusChangeEvent log): low-frequency; served from Postgres or Iceberg

**Downstream reference:** The 10-partition Kafka topic used for heartbeat ingestion reappears in Section 8 (deep dive on the evaluator design).

---

## 🔄 Section 5 — Requirements Variation Table

| If the interviewer says... | Architecture changes to... | Reasoning |
|---|---|---|
| "Only 1K nodes" | Single-threaded evaluator, Redis ring buffer, no Kafka needed | 10K heartbeats/sec is trivial; Kafka is over-engineering for this scale |
| "1M nodes" | Multiple Kafka consumer groups, sharded Redis, ZooKeeper or etcd for evaluator coordination | 10M heartbeats/sec; Redis memory = 1GB for window state; evaluator must be distributed |
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

**`GET /v1/nodes/{node_id}`** — returns the current declared status (ALIVE / DOWN / UNKNOWN). UNKNOWN means the node has registered but has never sent a heartbeat. Status is read from the Redis status store — P99 < 5ms. Callers use this for dashboard polling and pre-action checks ("is this node alive before I route traffic to it?").

**`GET /v1/nodes/{node_id}/history`** — returns the log of status-change events for a node, time-bounded by `from` and `to` query parameters. The response is cursor-paginated because a long-running node may have thousands of ALIVE↔DOWN transitions. Served from Postgres `status_change_events` table, not Redis.

---

## 🏗️ Section 7 — High-Level Design

### 🎨 Visual — System Architecture

```
                  MONITORED SERVICES
        ┌──────┐    ┌──────┐    ┌──────┐
        │Node A│    │Node B│    │Node C│
        └──┬───┘    └──┬───┘    └──┬───┘
           │  POST /v1/nodes/{id}/heartbeat  (every 100ms)
           └─────────────┬──────────┘

                         │
                         ▼
           ┌─────────────────────────────┐
           │     Heartbeat Receiver       │
           │  (stateless, load-balanced)  │  ← nginx / ELB in front
           └─────────────┬───────────────┘
                         │ publishes heartbeat event
                         │ key = node_id
                         ▼
           ┌─────────────────────────────┐
           │  Kafka Topic: "heartbeats"   │
           │  10 partitions               │
           │  key = node_id (hash-routed) │  ← 100K msgs/sec at scale
           └─────────────┬───────────────┘
                         │ consumed by
                         ▼
           ┌─────────────────────────────┐
           │    Window Evaluator          │
           │  (Kafka consumer group,      │
           │   10 instances)              │
           │  • maintains ring buffer     │
           │    per node_id in Redis      │
           │  • evaluates every 100ms     │
           │  • detects 3 consec gaps     │
           └───────────┬─────────────────┘
                       │ reads/writes
                       ▼
           ┌─────────────────────────────┐
           │  Redis (Status + Ring Buf)   │
           │  node:{id}:status → ALIVE    │  ← served on GET /status
           │  node:{id}:slots  → ring buf │
           └───────────┬─────────────────┘
                       │ on status change
                       ▼
           ┌─────────────────────────────────────┐
           │  Kafka Topic: "node-status-changes"  │
           │  (log-compacted, key = node_id)      │
           └──────────┬──────────────────┬────────┘
                      │                  │
              ┌───────▼──────┐   ┌───────▼──────┐
              │  Alert        │   │  Postgres     │
              │  Dispatcher   │   │  Writer       │
              │  (PagerDuty   │   │  (status_     │
              │   / Slack)    │   │  change_      │
              └──────────────┘   │  events table) │
                                 └───────────────┘

Stage transition thresholds:
  Heartbeat Receiver → Kafka:  sync publish, P99 < 5ms
  Kafka → Evaluator:           consumer lag target < 1 partition × 100ms = 100ms max
  Evaluator → Redis:           write P99 < 5ms (Redis set)
  Evaluator → status-changes topic: only on transition (rare; ~1/hr per node at steady state)
```

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

RING BUFFER ROTATION (every 100ms tick):
  tick N:   [s0] [s1] [s2] [s3] [s4]
  tick N+1: [s1] [s2] [s3] [s4] [s_new]   ← s0 drops off; new slot added at head

KEY INVARIANT:
   Scan the 5 slots for the longest run of consecutive empty slots.
   If max_consecutive_empty ≥ 3: declare DOWN.
   Each slot covers exactly one 100ms window. A slot is "empty" if no heartbeat
   was received with a timestamp falling within that slot's time range.
```

#### Implementation Options

| Option | Storage | Mechanism | Complexity | Notes |
|---|---|---|---|---|
| **A — Redis BITFIELD** | 5-bit ring per node_id in Redis | Each bit = slot is/isn't filled; rotate every 100ms; scan for 3 consecutive 0s | O(1) per heartbeat; O(slots) per evaluation | Most compact; Redis bitfield ops are atomic |
| **B — Redis HASH (timestamps)** | Hash with 5 fields per node_id: `slot_0`..`slot_4` = last heartbeat ts | On heartbeat arrival, set `slot = current_ts`; evaluator checks age of each slot | O(5) per evaluation | Easier to debug (timestamps readable); slightly more memory |
| **C — Redis SORTED SET (event log)** | One sorted set per node_id; score = timestamp; trim to last 500ms | Evaluator counts events per 100ms bucket for the last 500ms | O(log N) per insert; O(5) per eval | Overkill for fixed slots; better for variable-window queries |

**Decision: Option B (Redis HASH with timestamps).** Slots are fixed (5 total); the HASH with 5 fields is readable and debuggable. Each heartbeat arrival atomically sets `slot_{current_slot_index} = timestamp`. The evaluator runs every 100ms, reads all 5 fields, computes slot ages relative to `NOW()`, marks slots older than 100ms as empty, counts max consecutive empty slots.

**Pseudocode (evaluator loop for one node):**
```java
// Evaluator tick — runs every 100ms per consumer-assigned node
public void evaluateNode(String nodeId) {
    long now = System.currentTimeMillis();
    // Step 1: read all 5 slot timestamps from Redis
    Map<String, String> slots = redis.hgetAll("node:" + nodeId + ":slots");
    // Step 2: determine which slots are empty (no heartbeat received in that window)
    boolean[] empty = new boolean[SLOT_COUNT];
    for (int i = 0; i < SLOT_COUNT; i++) {
        String ts = slots.get("slot_" + i);
        // slot is empty if null or older than SLOT_WINDOW_MS (100ms)
        empty[i] = (ts == null || now - Long.parseLong(ts) > SLOT_WINDOW_MS);
    }
    // Step 3: count max consecutive empty slots
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
    // Step 4: transition logic
    if (maxConsecutive >= FAILURE_THRESHOLD) {
        transitionToDown(nodeId);
    } else {
        ensureAlive(nodeId);
    }
}
```

---

### Deep Dive 2: Distributed Coordination — Who Declares DOWN?

**The spine of the question.** A single evaluator process monitoring all nodes is the obvious starting point — and it's a SPOF. If the evaluator crashes, it stops checking, but all nodes appear ALIVE forever (no transitions are published). This is a silent failure in a health-monitoring system — the worst kind.

#### Options Table

| Option | How it works | Gain | Lose |
|---|---|---|---|
| **A — Single evaluator + hot standby** | Primary holds a Redis lock; standby polls lock with TTL; if primary dies, standby acquires lock and takes over | Simple; one Redis lock per node shard; widely understood | Lock TTL defines failover latency (30–60 seconds if lock TTL is 30s); standby must rebuild ring buffer state |
| **B — Partitioned Kafka consumer group** | 10 evaluator instances each own a partition; a node's heartbeats always land in the same partition (key = node_id); each evaluator owns its shard exclusively | Kafka handles partition assignment automatically; if one evaluator dies, Kafka rebalance gives its partitions to survivors within seconds | Rebalance = window state loss for reassigned nodes; must reload from Redis on reassignment |
| **C — Gossip protocol (like Cassandra)** | Each node sends heartbeats to N random monitors; each monitor gossips about nodes it observes; a node is declared DOWN when a quorum of monitors agree it's silent | No SPOF; tolerates monitor failures naturally | Much higher complexity; gossip storm under partition; hard to prove correctness in an interview |

**Decision: Option B (Kafka consumer group).**
- Kafka consumer group assignment is built-in — no custom coordination code
- Heartbeats are keyed by `node_id`: all heartbeats for node_id=X always land on partition_id = hash(X) % 10
- The evaluator for partition 3 owns all nodes whose `node_id` hashes to partition 3
- On evaluator failure, Kafka consumer group rebalances within ~3 seconds (session timeout)
- On rebalance, the new evaluator instance reloads ring buffer state from Redis before starting evaluation
- The window state lives in Redis, not in evaluator memory — rebalance does NOT lose the state

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

**Fix:** After rebalance, the evaluator checks the Redis timestamp for each node's last actual heartbeat (separate from ring buffer). If `now - last_heartbeat_ts < FAILOVER_GRACE_PERIOD (10s)`, treat the gap as a monitor failure (not a node failure) and do not declare DOWN. Only after 10 seconds of silence post-rebalance does the evaluator start issuing DOWN declarations.

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
CREATE TABLE status_change_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id         UUID           NOT NULL REFERENCES nodes(id),
    status          VARCHAR(10)    NOT NULL CHECK (status IN ('ALIVE', 'DOWN', 'UNKNOWN')),
    occurred_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    reason          TEXT
);

-- Index: get history for a node, time-ordered (serves GET /v1/nodes/{id}/history)
CREATE INDEX idx_status_change_node_time
    ON status_change_events (node_id, occurred_at DESC);

-- Index: recent events across all nodes (dashboard "what just changed?")
CREATE INDEX idx_status_change_recent
    ON status_change_events (occurred_at DESC);
```

**Redis schema (not SQL — documented here for completeness):**

```
node:{uuid}:status      → "ALIVE" | "DOWN" | "UNKNOWN"    (STRING, read by GET /status)
node:{uuid}:slots       → HASH with fields slot_0..slot_4  (timestamps of last heartbeat per slot)
node:{uuid}:last_ts     → Unix millis of last heartbeat received (STRING, used for failover grace)
```

**Access pattern coverage:**

| Query | Served by |
|---|---|
| Current status of node X | Redis `node:{id}:status` key — O(1) |
| Ring buffer evaluation for node X | Redis `node:{id}:slots` HASH — O(5) |
| History of node X over last 24h | Postgres `idx_status_change_node_time` |
| "What nodes changed status in the last 5 min?" | Postgres `idx_status_change_recent` |
| List all active nodes | Postgres `idx_nodes_active` |

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
- **Gain:** Built-in partition rebalancing; no custom leader election code; node assignment is deterministic (hash-routed); state is externalized to Redis so rebalance doesn't lose it.
- **Lose:** Rebalance causes a 3–5 second evaluation gap; all nodes in a rebalanced partition appear DOWN during failover unless a grace period is implemented. Adding the grace period adds code complexity.
- **Failure mode (Technical):** During a rolling deploy of the evaluator (all 10 instances restarting in sequence), the partition group rebalances repeatedly. Each rebalance triggers a 10s grace period per partition per rebalance. A real failure during the rolling deploy would be masked for up to `10s × num_rebalances`. Mitigation: blue/green deploy of the evaluator rather than rolling restart.
  [Confluent / Streaming Business Impact]: The evaluator Kafka consumer group is the bridge between the raw heartbeat stream and the status-change topic. If the group lag grows during a rebalance, heartbeat events queue up. Kafka retains them (default retention), so no heartbeats are lost — but the liveness decision is delayed by the lag. A lag of 10,000 messages at 100ms intervals = 1,000 seconds of delayed decisions. Monitor consumer lag on the `heartbeats` topic as the leading health indicator for the evaluator itself.

### Trade-off 3: Log-Compacted Status-Change Topic vs. Event-Sourced History Table

- **Chose:** Hybrid — log-compacted Kafka topic for current state; Postgres append-only table for history
- **Gain:** Downstream consumers (alert dispatcher, traffic router) read from Kafka — they always see the latest status per node_id without querying a DB. The compacted topic IS the status store for streaming consumers. Postgres is the source of truth for historical queries (dashboard, audit).
- **Lose:** Two stores to keep in sync. If the Postgres writer consumer fails, the Postgres table lags behind the Kafka topic — historical queries show stale data while current status is correct.
- **Failure mode (Technical):** Postgres writer consumer fails for 30 minutes. During that time, 30 minutes of status-change events are on the Kafka topic but not in Postgres. Dashboard queries show the last-known status (stale). When the consumer restarts, it replays from the last committed offset — Postgres catches up automatically. No data loss because Kafka retains the events.
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
> Ring buffer state lives in Redis (not in-memory in the evaluator process). On restart, the evaluator reloads from Redis — no state loss. The only gap is the evaluation period during the restart (~3s for Kafka rebalance). We handle this with a grace period: if the last Redis-recorded heartbeat is recent (within 10s), we treat the gap as a monitor fault, not a node fault.

### Deep Probe (Tier 2)
**Q: "What if the evaluator clock drifts by 200ms? The node's heartbeat timestamps say one thing, but the evaluator's `NOW()` says something different."**
> The heartbeat response returns `received_at` from the monitor's clock — this is the timestamp of *arrival*, not of the node's own clock. The ring buffer slots are indexed by *arrival time* at the monitor, not by the node's send time. So node clock drift doesn't affect the evaluation. Monitor clock drift (NTP desync) does matter: if the evaluator's `NOW()` drifts 200ms ahead, it will age-out slots that are only 100ms old. Mitigation: evaluate slot age against the slot's *slot_index* (a logical counter incremented every 100ms tick), not a wall-clock comparison.

### Cross-Concept Probe (Tier 3)
**Q: "How is this different from how Kafka itself knows if a broker is dead?"**
> Kafka uses ZooKeeper (or KRaft) for broker liveness: each broker maintains an ephemeral ZooKeeper node with a session timeout. If the broker's ZK session expires (no heartbeats to ZK for `session.timeout.ms`), ZK deletes the ephemeral node — this is the "broker died" event. The controller watches for ephemeral node deletions. Structurally identical to our system: heartbeats → session TTL → declare down → notify controller. The difference: Kafka uses event-driven notifications (ZK watch callback) rather than a polling evaluator loop; ZK handles consensus on a single flag per broker, while our system handles a sliding window per node. The phi accrual failure detector used between Kafka brokers in the broker-to-broker path is closer to our sliding window: it computes a continuous suspicion score rather than a binary 3-consecutive-slots threshold.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1:** Declaring a node DOWN on the first missed heartbeat → **Why it's wrong:** a single missed heartbeat is a network blip, a GC pause, or a busy CPU — not a node failure. The 3-consecutive-slots rule exists precisely to distinguish transient blips from actual failures. One miss → immediately alert = massive false positive rate in any real distributed system. → **Say instead:** "I evaluate max consecutive empty slots; only 3 or more consecutive empty slots triggers a DOWN transition."

- **Mistake 2:** Storing the ring buffer in evaluator memory (not Redis) → **Why it's wrong:** if the evaluator crashes or the consumer group rebalances, the in-memory ring buffer is lost. The new evaluator starts with an empty buffer — every node appears to have no heartbeats → massive false DOWN storm at the moment of evaluator restart. → **Say instead:** "Ring buffer state is in Redis, keyed by node_id. On rebalance, the new evaluator instance reloads from Redis before starting evaluation."

- **Mistake 3:** Using `DELETE /v1/nodes/{id}/heartbeat` instead of `POST /v1/nodes/{id}/heartbeat` → **Why it's wrong:** a heartbeat is an event being created, not a resource being deleted. The heartbeat is a new occurrence of "I am alive." POST is the correct verb for creating a new event/resource. DELETE means you are removing an existing named resource. → **Say instead:** `POST /v1/nodes/{id}/heartbeat`; the response confirms receipt; idempotency key is optional since heartbeats are fire-and-forget.

- **Mistake 4:** Proposing a Bloom filter to check if a heartbeat came from a known node → **Why it's wrong:** Bloom filters answer "has this element ever been seen?" (set membership) — they are for avoiding expensive lookups when you expect most queries to be misses. Here, checking if a `node_id` is registered is a *point lookup* on a 10K-row table with a unique index — it's already O(1). A Bloom filter buys nothing over a Redis `EXISTS` check and adds false-positive complexity. Bloom filters are the wrong tool for time-window liveness tracking. → **Say instead:** `Redis GET node:{id}:status` — if the key doesn't exist, the node is not registered; return 404.

- **Mistake 5:** Using a single global evaluator process → **Why it's wrong:** SPOF. The evaluator is the most critical component — if it crashes, all nodes appear ALIVE forever. → **Say instead:** "Kafka consumer group with 10 evaluator instances, each owning a partition shard; if one evaluator crashes, Kafka rebalance assigns its partitions to surviving instances within ~3 seconds."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/nodes/{id}/heartbeat` → 200 (not 201, not 204); unknown node_id → 404 (not 200 or 400); heartbeat flood → 429 with `Retry-After`; `GET /v1/nodes/{id}` serves current status; `GET /v1/nodes/{id}/history` cursor-paginated |
| **Trade-off Defense** | ✅ | Push vs pull: push chosen; pull failure mode (monitor partition → false DOWN) explained. Kafka consumer group vs single evaluator: SPOF eliminated; rebalance gap + grace period trade-off articulated. Log-compacted topic vs Postgres: hybrid chosen; Tableflow replacement named. |
| **SQL / Data Modeling** | ✅ | `UNIQUE(name)` on nodes produces 409 on duplicate register; `status_change_events` is append-only (no UPDATE, no DELETE); index `(node_id, occurred_at DESC)` serves history query; partial index `WHERE deregistered_at IS NULL` serves active node list. Redis HASH schema for ring buffer explicitly documented. |
| **Distributed Systems** | ✅ | Spine of the design: "who declares DOWN?" → Kafka consumer group coordination; rebalance gap quantified (3s) and mitigated (grace period + Redis state externalization); failover sequence written out step-by-step. |
| **Pipeline Resilience** | ✅ | Heartbeats survive evaluator crash (Kafka retention); status-change events survive Postgres writer failure (Kafka retention, consumer replays from last offset); log-compacted status topic means downstream consumers always have current state on startup without full replay. |
| **Concurrency** | ✅ | Multiple heartbeats from the same node within one 100ms slot — Redis HSET is atomic: last writer wins, which is correct (slot records the most recent heartbeat in the window). Concurrent evaluator instances do not compete: each owns exclusive partitions via Kafka consumer group assignment — no evaluator evaluates the same node_id as another. |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "This is a push-based health monitoring system where the core algorithm is a 5-slot × 100ms ring buffer stored in Redis per node — each slot records whether a heartbeat arrived in that 100ms window, and 3 or more consecutive empty slots triggers a DOWN declaration. The architectural spine is the distributed coordination question: a single evaluator is a SPOF, so we use a Kafka consumer group where each evaluator instance owns a partition shard of node_ids via key-based hash routing — no custom leader election needed. Ring buffer state lives in Redis, not in evaluator memory, so a consumer group rebalance (3–5 second gap) does not lose window state; we add a 10-second grace period post-rebalance to avoid false DOWN declarations during failover. Status changes are published to a log-compacted Kafka topic keyed by node_id, so any downstream consumer (alert dispatcher, traffic router) always gets the latest declared status per node on startup without replaying all historical heartbeats. The Tableflow angle is direct: replace the hand-rolled Postgres history writer with a Tableflow pipeline that materializes the status-change topic into an Iceberg table, enabling time-travel queries on node health with no bespoke consumer code."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Covers Health Check / wasAlive monitoring (Type 2 full design). 6 endpoints with full contract and status code stories. ASCII HLD diagram with stage transition thresholds. ASCII sliding window ring buffer visualization. Deep dive 1: algorithm + Redis HASH implementation with Java pseudocode. Deep dive 2: Kafka consumer group coordination + failover gap grace period. 3 trade-offs with two-layer failure modes. Confluent angle: compacted status topic + Tableflow Iceberg materialization. |
