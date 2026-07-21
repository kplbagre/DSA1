# D2 — Design an Ad Budget Pacing & Impression Counting System

> **Prerequisites:**
> | Concept file | What to load before reading |
> |---|---|
> | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/09-sharded-counters.md` | Hot key problem, Redis sharding |
> | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md` | Token bucket, INCR pattern |
> | `DSA/SystemDesignConcepts/Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md` | AP vs CP, eventual consistency |
> | `DSA/SystemDesignConcepts/Core-Architecture/Messaging/Kafka-streaming.md` | Kafka partitioning, Flink windows |
> | `Interview/Disney/HLD-ad-impression-pacing.md` | Source notes — terminology, architecture |

---

## 🎯 What Is This System? (Section -1 — Pre-Interview Orientation)

**In plain English:** When Disney sells an advertiser a daily budget (e.g., "$14,400 today on ESPN"), two things must happen: (1) ads must be spread evenly across the day so the budget isn't exhausted by 9 AM, and (2) every ad shown must be counted exactly once for billing. These two requirements live in tension — pacing needs fast, approximate consistency; billing needs slow, exact consistency.

**Real-world examples:**
| System / Company | What they built |
|---|---|
| **Disney Ads API / ESPN** | Pacing $14,400 budgets across live sports events at 70M+ impressions/min |
| **Google Ads** | Smooth pacing across campaigns globally at billions of impressions/day |
| **Meta Ads Manager** | Per-campaign impression counters with budget exhaustion gates |
| **The Trade Desk** | RTB-compatible pacing gate with <5ms decision latency |
| **Hulu (Disney-owned)** | CTV ad insertion with per-minute budget windows |

**Core user journey:** An ESPN advertiser sets a $14,400 daily budget at $14.40 CPM; during Monday Night Football, the system spreads ~694 impressions/minute evenly across the broadcast and sends an exact billing report at end-of-day.

**Why it's hard to build at scale:** 70M impressions/minute must be checked against a budget counter in under 5ms each — but any synchronous write to a durable store at that rate blows the 100ms RTB window and the counter becomes a hot key on a single Redis node during live events.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Design an Ad Budget Pacing and Impression Counting system |
| **Interview Type** | Type A — System Design |
| **Confirmed or Likely** | ⭐ Confirmed — Disney Ad Platforms org core problem |
| **Concept notes prerequisite** | Sharded counters, CAP theorem, Kafka+Flink streaming |
| **Disney-specific angle** | Disney+ / ESPN live events create 3-5× impression spikes; over-delivering beats blocking a Super Bowl ad break |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I start drawing, let me ask a few clarifying questions — this problem has two layers with opposing consistency requirements, and I want to make sure I'm optimizing for the right one first."

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "What pacing model should I use — even pacing, smooth pacing, or something more sophisticated like traffic-curve-aware pacing?"**
- Why ask: Determines Budget Controller complexity and how often it runs.
- Even pacing → simple: divide daily budget by 1440 minutes, fixed allowance per minute. One Budget Controller run at midnight.
- Smooth pacing → moderate: recompute per-minute allowance every 60 seconds as `remaining_budget ÷ remaining_minutes`. Controller must run continuously.
- Traffic-curve-aware → complex: ML predicts high-traffic windows (ESPN primetime) and allocates more budget there. Out of scope for a 60-minute interview — assume smooth pacing.

**Q: "Is this for RTB (real-time bidding, sub-100ms) or direct-sold campaigns with a relaxed SLA?"**
- Why ask: RTB forces the pacing gate check into the sub-5ms critical path. Direct-sold allows a 20-50ms synchronous call.
- RTB → Redis-first, in-memory index, no synchronous DB writes in the serving path. This is the harder and more interesting design.
- Direct-sold → can tolerate a Postgres call. Much simpler.
- Assume: RTB — the hard case.

**Q: "What's the acceptable over-delivery tolerance?"**
- Why ask: Determines whether I choose AP or CP for the pacing gate.
- 0% → CP required → Redis must be strongly consistent → distributed lock or Lua script per impression → latency doubles.
- 5% → AP acceptable → Redis INCR without locking, reconcile at end-of-day → correct choice for live entertainment.
- Assume: 5% over-delivery acceptable.

**Q: "How many active campaigns are running at peak?"**
- Why ask: Determines Budget Controller fan-out (pipelined Redis writes), memory for in-JVM index, and whether a single Redis cluster handles all budget keys.
- 1K campaigns → trivially handled by one Budget Controller, one Redis node.
- 100K campaigns → Budget Controller must shard; Redis keys still tiny (< 1 GB total).
- Assume: 10K active campaigns at peak.

**Q: "Is frequency capping in scope — limiting how many times one user sees the same ad?"**
- Why ask: Frequency capping adds per-user Redis keys, which is a separate hot-key problem orthogonal to budget pacing.
- In scope → add `INCR freq:{adId}:{userId}:{day}` check per impression; TTL = 24h.
- Out of scope → skip entirely.
- Assume: out of scope (separate system, same Redis patterns).

**Q: "Single region or global — does Disney+ serve ads in international markets that need to share a budget?"**
- Why ask: Global campaigns require cross-region Redis synchronization or region-partitioned budgets.
- Single-region US → all ad servers write to one Redis cluster in us-east-1. Simple.
- Global → budget must be partitioned by region OR a global budget master replicates limits to regional replicas. Complex.
- Assume: single-region for now; note global extension in Section 5.

---

## Section 3 — 📋 Requirements

**Functional Requirements:**
- Ad server can check whether a campaign has budget remaining for this minute (pacing gate, < 5ms)
- Every served impression is durably recorded for billing (impression counting, exactly-once)
- Budget Controller computes per-minute spend allowance for each campaign and writes it to the pacing gate
- Operations can query current budget status per campaign (near-real-time, < 60s lag)
- Advertiser billing reports reflect accurate end-of-day spend (eventual, strong consistency)
- Out of scope: campaign creation/targeting rules, creative serving, user identity, frequency capping

**Non-Functional Requirements:**
- Scale: 1.17M impressions/sec average, 3.5M/sec peak (live sports); 10K active campaigns
- Latency: pacing gate check P99 < 5ms (fits in 100ms RTB window); budget status query P99 < 200ms
- Availability: 99.99% for pacing gate (blocking live ad breaks = revenue catastrophe)
- Consistency: AP for pacing gate (5% over-delivery acceptable); strong eventual for billing records (no undercounting)
- Durability: every impression event must be recoverable (Kafka retention ≥ 7 days)

---

## Section 3.5 — 🗂️ Core Entities

| Entity | What it represents |
|---|---|
| **Campaign** | An advertiser's budget configuration — daily spend limit, CPM bid, targeting criteria; transactional |
| **Impression Event** | One ad served once to one user — the atomic billing unit; append-only, immutable |
| **Budget Counter** | Running count of impressions served this minute per campaign — ephemeral (Redis); derived from impression events |
| **Minute Allowance** | Per-minute budget cap computed by Budget Controller — ephemeral (Redis, TTL 2 min); derived from campaign budget + remaining time |
| **Aggregated Spend** | Cassandra-aggregated impression cost per campaign per minute — derived; source of truth for billing reports |

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–8)

**Traffic:**
- Impressions/sec (average): 70M/min ÷ 60 = **1,166,667/sec**
- Peak (live sports 3×): **3,500,000 impressions/sec**
- Active campaigns: 10,000
- Budget Controller runs every 60s: 10,000 Redis SET commands (pipelined) per run

**Kafka throughput:**
- Each impression event ≈ 200 bytes
- Average: 1.17M/sec × 200 bytes = **234 MB/sec** → 3 × 80 MB/sec Kafka partitions (comfortable)

**Redis memory for budget keys:**
- Per campaign: 2 keys (`budget_limit:{id}:{min}`, `budget_spent:{id}:{min}`) × 100 bytes
- Total: 10,000 campaigns × 200 bytes = **2 MB** — trivially small; the hot key problem is CPU, not memory

**Cassandra billing storage:**
- Per campaign per minute bucket: ~100 bytes
- Daily: 10,000 campaigns × 1,440 minutes × 100 bytes = **1.44 GB/day** → 526 GB/year → manageable

**Key conclusions:**
- "At 1.17M impressions/sec, any synchronous per-impression DB write saturates a Postgres instance within seconds — the serving path must be fire-and-forget."
- "At 10M impressions/min on one viral campaign (ESPN Super Bowl), a single Redis INCR key receives 167K ops/sec — that's 10× beyond a single Redis keyslot's CPU budget → sharding is required at that scale."
- "Budget Controller processes 10K campaigns in <200ms with pipelined Redis writes — easily fits a 60-second tick."

---

## Section 5 — 🔄 Requirements Variation Table ⭐

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10 campaigns, 10K impressions/day" | Single Postgres table, no Redis; Budget Controller is a cron job updating a `minute_allowance` column | Write volume fits direct DB; Redis overhead not justified |
| "0% over-delivery — exact billing required" | Redis with Lua script (atomic read+increment+compare in one round trip) OR Redlock for distributed lock; latency doubles to ~10ms | CP choice; eliminates the INCR race but kills throughput |
| "100K active campaigns" | Budget Controller must shard across multiple workers (each handles 1K campaigns); Redis keys still < 20 MB total | Bottleneck shifts to Budget Controller fan-out, not Redis memory |
| "Global campaigns — same budget shared across US + EU + APAC" | Region-partitioned budgets (US gets 40%, EU 30%, APAC 30%); regional Redis clusters; no cross-region sync on hot path | Cross-region INCR would add 60-150ms network latency → RTB window blown |
| "No over-delivery at all on live sports (contract SLA)" | Fail-closed for pacing gate: if Redis is unreachable, reject all impressions for that campaign until Redis recovers | Reverses the AP/CP decision; accepted only if contract explicitly requires exact delivery |
| "Store all individual impressions, not just aggregates" | Append-only Cassandra or S3+Parquet for every event; Flink writes individual rows (not aggregates); increases storage 10,000× | Required for fraud detection or user-level attribution; much higher Cassandra write load |
| "Frequency capping: max 3 impressions per user per day" | Per-user Redis keys: `INCR freq:{adId}:{userId}:{date}` with TTL 24h; check before serving; 10M DAU × 10K campaigns = 100B keys (infeasible) — shard by date + user hash or use Bloom filter for cap detection | Hot key problem at user level instead of campaign level; Bloom filter gives probabilistic cap with 1% false positives |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 8–20)

### 🎨 Visual — Three-Stage Architecture

```
══════════════════════════════════════════════════════════════════════
STAGE 1 — Naive: Synchronous DB Write Per Impression (≤ 1K imp/sec)
══════════════════════════════════════════════════════════════════════

Ad Request
    │
    ▼
┌───────────────────────────┐
│  Ad Server                │
│  SELECT active campaigns  │
│  from Postgres            │──── 5ms ────▶ choose campaign
│  INSERT impression row    │──── 10ms ───▶ billing record written
└───────────────────────────┘
    │
    ▼
Response to user

BREAKING POINT: Stage 1 breaks at ~1,000 impressions/sec.
  At 1.17M impressions/sec, 10ms Postgres INSERT × concurrent requests
  = 10ms × 1,170 concurrent writes = connection pool exhaustion in seconds.
  Observable symptom: Postgres connection wait queue grows; RTB P99 > 80ms;
  ad server begins dropping impressions.
  Why Stage 2 is needed: impression recording must be moved off the critical path.


══════════════════════════════════════════════════════════════════════
STAGE 2 — Redis Gate + Kafka Async (≤ 10M impressions/min on any one campaign)
══════════════════════════════════════════════════════════════════════

                 ┌─────────────────────────────────────────────┐
                 │         Budget Controller                    │
                 │         (runs every 60 seconds)             │
                 │                                             │
                 │  For each active campaign:                  │
                 │  remaining_budget = daily_budget            │
                 │                    - actual_spend (Cassandra)│
                 │  remaining_minutes = minutes until midnight │
                 │  minute_allowance_micros =                  │
                 │    remaining_budget_micros / remaining_min  │
                 │                                             │
                 │  Pipelined Redis writes:                    │
                 │  SET budget_limit:{id}:{min}                │
                 │      {minute_allowance_micros} EX 120       │
                 └─────────────────────────────────────────────┘
                                    │ (writes per-min limits)
                                    ▼
Ad Request                 ┌─────────────────┐
    │                      │  Redis Cluster  │
    │                      │                 │
    ▼                      │ budget_limit:   │
┌───────────────────┐      │  {id}:{min}     │
│  Ad Server        │      │   (allowance)   │
│                   │      │                 │
│  1. In-Memory     │      │ budget_spent:   │
│     Campaign      │──────│  {id}:{min}     │
│     Index (JVM)   │ INCR │   (counter)     │
│     (refreshed    │      └─────────────────┘
│      every 30s)   │           │
│                   │           │ counter × cpm_micros
│  2. Gate check:   │◀──────────┘ ≥ limit? → skip campaign
│     INCR counter  │
│     compare spend │
└───────────────────┘
    │ allowed
    ▼
Serve Ad
    │
    ├──▶ Kafka Topic: "ad-impressions"   (async, fire-and-forget)
    │    partitioned by campaignId
    │         │
    │         ▼
    │    ┌──────────────────────────────────────────┐
    │    │  Flink Consumer Group                    │
    │    │  - Tumbling 1-minute windows             │
    │    │  - SUM(cpgCostMicros) per campaignId     │
    │    │  - COUNT(impressions) per campaignId     │
    │    │  - Writes aggregated rows to Cassandra   │
    │    └──────────────────────────────────────────┘
    │                   │
    │                   ▼
    │    ┌──────────────────────────────────────────┐
    │    │  Cassandra: campaign_impressions          │
    │    │  Partition: (campaign_id, date)           │
    │    │  Cluster:   minute_bucket (epoch/60)      │
    │    │  Source of truth for billing              │
    │    └──────────────────────────────────────────┘
    │
    └──▶ Return response to ad server (async Kafka write does not block)

KEY INVARIANT:
   Redis = fast pacing gate (AP, ~0.1ms, approx)
   Kafka → Flink → Cassandra = slow billing truth (eventual, exact)
   Never block ad serving on the billing path.

BREAKING POINT: Stage 2 breaks at ~10M impressions/min on a single campaign.
  A viral ESPN Super Bowl campaign generates 167K INCR ops/sec on one Redis key.
  Redis single-key throughput is ~100K ops/sec before CPU of that key's slot saturates.
  Observable: Redis CPU on hot shard > 90%; INCR latency P99 spikes from 0.1ms → 5ms+;
  pacing gate falls outside the 5ms budget.
  Why Stage 3 is needed: per-campaign Redis key must be sharded.


══════════════════════════════════════════════════════════════════════
STAGE 3 — Sharded Counters + LongAdder Batching (any scale)
══════════════════════════════════════════════════════════════════════

Ad Serve
    │
    ▼
┌──────────────────────────────────────────────────────────────────┐
│  Ad Server JVM                                                   │
│                                                                  │
│  LongAdder impressionCount = counters.get(campaignId)            │
│  impressionCount.increment()   ← in-JVM, zero network, zero CAS │
│                                                                  │
│  Every 10 seconds (background thread):                           │
│    shard = hash(campaignId + timestamp) % 16                     │
│    local_count = impressionCount.sumThenReset()                   │
│    INCRBY budget_spent:{id}:{min}:shard{N} local_count           │
│                                                                  │
│  Gate check (also every 10s, on batch flush):                    │
│    total = SUM(GET budget_spent:{id}:{min}:shard0..shard15)      │
│    estimated_spend = total × cpm_micros_per_impression           │
│    if estimated_spend ≥ minute_budget_micros → pause campaign    │
└──────────────────────────────────────────────────────────────────┘

Redis load reduction:
  Stage 2: 1.17M INCR ops/sec (one per impression)
  Stage 3: 1 INCRBY per campaign per 10s × 10K campaigns
         = 1,000 INCRBY ops/sec — 1,000× less Redis traffic

Trade-off: up to 10 seconds of lag in the pacing gate counter.
  Acceptable: we're not billing from Redis; 10s lag means <1% over-delivery at campaign boundaries.
```

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 20–35)

### Deep Dive 1: Budget Pacing Gate — The CPM Math (and the Bug Every Candidate Makes)

**Why this is the most critical component:**
The pacing gate is checked on every single ad impression. Get the formula wrong and you either block all ads after the first impression (over-blocking) or run the full daily budget in 1 minute (under-blocking). This is the kind of error an Ad Platforms interviewer spots in 10 seconds.

**The CPM unit trap:**

CPM (Cost Per Mille — cost per one thousand impressions) is the billing unit. The gate compares per-impression spend against a per-minute budget. You must convert units.

```
WRONG gate (off by 1000×):
  if spent_impressions × CPM ≥ minute_budget → skip campaign

  At CPM = $14.40 and minute_budget = $10:
  After 1 impression: 1 × 14.40 = 14.40 ≥ 10 → BLOCKED
  Result: campaign serves exactly 1 impression per minute. Catastrophically wrong.


CORRECT gate (work in micros throughout):
  cpm_micros_per_impression = CPM_USD × 1,000,000 ÷ 1,000
                             = $14.40 × 1,000,000 ÷ 1,000
                             = 14,400 micros per impression

  minute_budget_micros = daily_budget_USD ÷ 1,440 × 1,000,000
                       = $14,400 ÷ 1,440 × 1,000,000
                       = $10 × 1,000,000
                       = 10,000,000 micros per minute

  Gate: if spent_impressions × 14,400 ≥ 10,000,000 → skip campaign

  Worked example:
    After 694 impressions: 694 × 14,400 = 9,993,600 micros < 10,000,000 → SERVE ✓
    After 695 impressions: 695 × 14,400 = 10,008,000 micros ≥ 10,000,000 → SKIP ✓
    Correct: ~694 impressions/min at $14.40 CPM = ~$10 spend/min ✓
```

**Full Budget Controller + gate implementation:**

```
Budget Controller (runs every 60 seconds per campaign):

  1. Read actual spend from Cassandra (5-min lag acceptable):
     actual_spend_today_micros = SELECT SUM(total_spend_micros)
                                 FROM campaign_impressions
                                 WHERE campaign_id = :id
                                   AND date = :today

  2. Smooth pacing formula:
     remaining_budget_micros = daily_budget_micros - actual_spend_today_micros
     remaining_minutes       = minutes_until_midnight()
     minute_allowance_micros = remaining_budget_micros / remaining_minutes

  3. Write to Redis (pipelined — all 10K campaigns in one round trip):
     SET budget_limit:{campaignId}:{currentMinute}
         {minute_allowance_micros}
         EX 120    ← expires after 2 minutes (prevents stale key acting as zero)


Ad Server gate check per impression (Stage 2 — single key):

  spent = INCR budget_spent:{campaignId}:{currentMinute}
  limit = GET  budget_limit:{campaignId}:{currentMinute}

  if spent * cpm_micros_per_impression >= limit → skip campaign, try next


Redis TTL correctness — the silent bug:
  If budget_spent key expires mid-minute, INCR creates it at 0 → gate resets.
  Fix: Budget Controller initializes spent key to 0 with TTL when writing the limit:
    SET budget_spent:{id}:{min} 0 EX 120   (only if NOT EXISTS — use SETNX)
  Then ad servers INCR it. TTL is set once, not per impression.
```

**Decision: Redis Sorted Set is NOT used here.**
Unlike the leaderboard (Section D1), ad pacing uses plain Redis string keys with INCR — not sorted sets. The pacing gate does not need ranking; it needs an atomic counter and a threshold check. Two different Redis primitives for two different problems.

---

### Deep Dive 2: Hot Key Problem — Sharded Counters and LongAdder

**Why this is the second most critical:**
During a Disney live event (NBA Finals on ESPN), a single campaign can spike to 10M+ impressions per minute. The Redis INCR on one key becomes the bottleneck for all ad serving on that event.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Single Redis key INCR** | Simplest; exact count per impression | Saturates at ~100K ops/sec per keyslot; hot key crushes Redis CPU |
| **Sharded Redis counters (16 shards)** | Distributes load; 16× throughput | Gate check requires reading all 16 shards (16 GET ops); budget controller does this, not ad server |
| **Java LongAdder in-JVM + batch INCRBY** | Near-zero Redis traffic; no CAS contention | Up to 10-second lag in Redis counter; only reduces Redis load, still needs shards at extreme scale |
| **Approximate counting (HyperLogLog)** | O(1) memory; probabilistic | Billing requires exact counts; HyperLogLog is for cardinality, not sum |

**Decision: LongAdder + sharded counters (Stage 3).**

```java
// In-JVM impression accumulator — one LongAdder per campaign
private final ConcurrentHashMap<String, LongAdder> impressionCounters =
    new ConcurrentHashMap<>();

// Called on every ad serve — in-process, zero network, zero CAS under high contention
public void recordImpression(String campaignId) {
    impressionCounters
        .computeIfAbsent(campaignId, id -> new LongAdder())
        .increment();
}

// Background thread — runs every 10 seconds per campaign
public void flushToRedis(String campaignId, String currentMinute) {
    LongAdder adder = impressionCounters.get(campaignId);
    if (adder == null) {
        return;
    }
    // sumThenReset() is atomic: reads sum AND resets to 0 in one call
    // No window of impressions is double-counted or lost
    long batchCount = adder.sumThenReset();
    if (batchCount == 0) {
        return;
    }
    int shard = Math.abs((campaignId + currentMinute).hashCode() & Integer.MAX_VALUE) % 16;
    String key = "budget_spent:" + campaignId + ":" + currentMinute + ":shard" + shard;
    redisClient.incrBy(key, batchCount);
}
```

**Why LongAdder over AtomicLong:**

```java
// AtomicLong — correct but slow at 1.17M/sec on same counter:
//   increment() → compareAndSet() → many threads spin-retry CAS failures
//   Under 1,000 concurrent threads: ~70% of CAS calls fail and retry → CPU waste

// LongAdder — stripes across N CPU cells (N = number of CPU cores):
//   Under contention: threads write to separate cells (no CAS conflict)
//   sum() merges all cells — not instantaneous but fine for per-10s flush
//   sumThenReset() is the correct method for flush-and-reset: atomic reset
```

---

## Section 8 — 🌐 API Design

### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

**"Ad server must record an impression and check the budget gate"** → two operations in one request (atomicity matters: if you record but don't gate-check, budget overruns; if you gate-check but don't record, billing is wrong) → resource is `impression` under a `campaign` → `POST /v1/campaigns/{campaignId}/impressions`. Response must return `served|budget_exhausted` status so ad server knows whether to emit the creative. `sessionId` is idempotency key (game-server-style retry safety).

**"Operations must monitor pacing health"** → read-only, near-real-time → `GET /v1/campaigns/{campaignId}/pacing`. Returns current-minute impression count and estimated spend vs. allowance. Served from Redis (60s lag max) — fast enough for dashboards; never Cassandra (too slow for this read pattern).

**"Advertiser billing report"** → read-only, eventual consistent → `GET /v1/campaigns/{campaignId}/spend`. Source of truth is Cassandra (5-min lag, EOD finalization). Separate from pacing status by design — mixing the two would create false precision.

---

### Core Endpoints

| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| `POST` | `/v1/campaigns/{campaignId}/impressions` | Bearer (ad-server service role) | `{userId, adId, sessionId, contentType, timestamp, cpmMicros}` | `{status: SERVED\|BUDGET_EXHAUSTED, minuteImpressions, estimatedSpendMicros}` | 201, 429 (budget exhausted this minute), 400 |
| `GET` | `/v1/campaigns/{campaignId}/pacing` | Bearer (ops) | — `?window=current-minute` | `{minuteImpressions, minuteAllowanceMicros, estimatedSpendMicros, pacingStatus}` | 200, 404 |
| `GET` | `/v1/campaigns/{campaignId}/spend` | Bearer (advertiser) | — `?date=2026-07-21` | `{totalImpressions, totalSpendMicros, breakdown: [{minuteBucket, impressions, spendMicros}]}` | 200, 404 |
| `PUT` | `/v1/internal/campaigns/{campaignId}/minute-limit` | Bearer (budget-controller service role) | `{minute, allowanceMicros, ttlSeconds}` | `{success}` | 200, 400 |
| `POST` | `/v1/campaigns/{campaignId}/pause` | Bearer (admin) | `{reason}` | `{status: PAUSED}` | 200, 404 |

---

### 🔍 Endpoint Stories — Why Each One Exists

**`POST /v1/campaigns/{campaignId}/impressions`** — The hot path. Every ad impression in every RTB auction flows through this endpoint. The non-obvious part: the response returns `status: BUDGET_EXHAUSTED` (not a 429 error to the client) because the ad server needs to try the next campaign in the queue — an exhausted campaign is not an error, it's a normal pacing outcome. `cpmMicros` in the request body lets the service compute spend without another lookup (CPM can vary per auction in dynamic bidding). `sessionId` is the idempotency key: if the ad server retries on timeout, the endpoint returns 201 without double-counting (Redis SETNX pattern for dedup key with TTL = 5 minutes).

**`GET /v1/campaigns/{campaignId}/pacing`** — Operations monitoring. Reads from Redis (`budget_spent:` + `budget_limit:` keys), not Cassandra. Returns `pacingStatus: ON_TRACK | AHEAD | BEHIND | EXHAUSTED`. "Ahead" means the campaign is burning faster than the smooth-pacing formula intended — Budget Controller will reduce next-minute allowance automatically. "Behind" means low traffic (late-night, off-peak) — Controller increases next-minute allowance.

**`GET /v1/campaigns/{campaignId}/spend`** — Billing source of truth. Reads from Cassandra `campaign_impressions` table aggregated by minute bucket. Up to 5-minute lag from serving time (Flink aggregation window). Disney's billing team runs end-of-day reconciliation using this endpoint — it is the number on the invoice.

**`PUT /v1/internal/campaigns/{campaignId}/minute-limit`** — Budget Controller's write path. Internal service-to-service only (never exposed externally). Uses a pipeline internally: Budget Controller writes all 10K campaigns in one pipelined batch. The `ttlSeconds` field ensures stale limits auto-expire — if Budget Controller crashes and misses a tick, the key expires and ad server falls back to fail-open (serve all, reconcile later).

---

## Section 9 — 🗄️ Data Model

### Cassandra — Billing Source of Truth

```sql
-- Aggregated impression counts per campaign per minute bucket
-- Written by Flink every 60 seconds per (campaign, minute)
CREATE TABLE campaign_impressions (
    campaign_id     UUID,
    date            DATE,
    minute_bucket   BIGINT,   -- UNIX timestamp floored to minute: epoch / 60 * 60
    impressions     BIGINT,
    total_spend_micros BIGINT,
    PRIMARY KEY ((campaign_id, date), minute_bucket)
) WITH CLUSTERING ORDER BY (minute_bucket ASC);
-- Partition key: (campaign_id, date) — all of a campaign's daily data on one node
-- Clustering key: minute_bucket — fast range scan for "spend from 9 AM to 5 PM"
-- Write pattern: Flink UPSERT every minute per campaign → ~10,000 writes/min (trivial for Cassandra)
-- Read pattern: Budget Controller reads last 5 min per campaign every 60s → 10K reads/min
```

### Redis Key Design (Pacing Gate)

```
Keys per campaign per minute window:

  budget_limit:{campaignId}:{minuteEpoch}
    Type: String (integer in micros)
    Written by: Budget Controller every 60 seconds
    TTL: 120 seconds (2 minutes — survives one missed Controller tick)
    Value: minute_allowance_micros (e.g., 10,000,000 for $10/min)

  budget_spent:{campaignId}:{minuteEpoch}       ← Stage 2 (single key)
    Type: String (integer, impression count)
    Written by: Ad Server (INCR per impression)
    TTL: 120 seconds (set by Budget Controller at window start via SETNX)
    Value: cumulative impressions served this minute

  budget_spent:{campaignId}:{minuteEpoch}:shard{0..15}  ← Stage 3 (sharded)
    Type: String (integer, impression count per shard)
    Written by: Ad Server (INCRBY every 10s per shard from LongAdder batch)
    Total impressions: SUM(shard0..shard15) — read by Budget Controller only

Gate formula (correct):
  estimated_spend_micros = total_impressions × cpm_micros_per_impression
  cpm_micros_per_impression = CPM_USD × 1,000 (e.g., $14.40 → 14,400 micros)
  if estimated_spend_micros ≥ budget_limit_micros → skip campaign

  Example: after 694 impressions → 694 × 14,400 = 9,993,600 < 10,000,000 → SERVE
           after 695 impressions → 695 × 14,400 = 10,008,000 ≥ 10,000,000 → SKIP
```

### Kafka Topic Schema

```
Topic: ad-impressions
  Partitioned by: campaignId (ensures all events for one campaign go to same partition)
  Retention: 7 days (enables re-processing if Flink falls behind)
  Replication factor: 3

  Message schema (Avro):
  {
    "campaignId":   "uuid",
    "adId":         "uuid",
    "userId":       "uuid",
    "sessionId":    "uuid",       // idempotency key for dedup in Flink
    "timestamp":    "long",       // epoch ms
    "contentType":  "string",     // "live-sports" | "streaming" | "display"
    "cpgCostMicros": "long"       // cost of this impression in micros (14400 for $14.40 CPM)
  }
```

### Key Schema Decisions

- **Cassandra partition key on `(campaign_id, date)`**: keeps all daily rows for one campaign on one node — Budget Controller's "daily spend so far" read is a single-partition range scan, not a scatter-gather.
- **Kafka partitioned by campaignId**: ensures Flink's tumbling window aggregation for one campaign sees all events in the same partition — no cross-partition join needed for per-campaign spend aggregation.
- **No per-impression row in Cassandra**: storing 1.17M rows/sec in Cassandra is 7.3 PB/year. Flink aggregates to per-minute rows (1 row per campaign per minute) — 526 GB/year. Kafka retains raw events for 7-day replay.
- **Redis key expiry via TTL on limit key**: if Budget Controller misses a tick, the limit key expires → Budget Controller's next successful run will write a fresh key. The 2-minute TTL is wide enough to survive one missed tick (Controller runs every 60s).

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 35–45)

### Trade-off 1: AP over CP for Pacing Gate (Explicit Decision)

- **Chose:** Availability + Partition tolerance — Redis INCR without distributed lock; Budget Controller reconciles against Cassandra.
- **Gain:** Pacing gate check completes in ~0.1ms. Ad servers can serve independently even if Redis is partitioned. Live sports ad breaks are never blocked.
- **Lose:** Two ad servers behind a partition can both think the campaign has budget remaining, causing up to 5–10% over-delivery during the partition window. End-of-day reconciliation detects this and Disney credits the advertiser for the over-served impressions.
- **Failure mode if wrong:** [Technical]: If we chose CP (Redlock or Lua script for atomic read-compare-write), every gate check requires a distributed lock acquisition → lock contention at 1.17M/sec → lock wait queue builds → pacing gate P99 spikes from 0.1ms to 50ms+ → RTB window breached for every impression. [Business]: Live sports ad breaks (Disney's highest-CPM inventory — Monday Night Football, NBA Finals) fail to serve during peak. A 30-second live sports ad break represents $400K+ of revenue. Over-delivery of 5% ($720 on a $14,400 campaign) is preferable to losing $400K in live ad revenue.

### Trade-off 2: Kafka Async vs. Synchronous Cassandra Write

- **Chose:** Fire-and-forget Kafka publish on ad serve; Flink aggregates asynchronously to Cassandra.
- **Gain:** Impression recording adds 0 ms to the ad serving P99 latency (Kafka publish is non-blocking). At 1.17M impressions/sec, Cassandra cannot absorb this write rate synchronously (single-node Cassandra max ~100K writes/sec; cluster needed).
- **Lose:** 30–60 seconds of lag before Cassandra reflects a served impression. Budget Controller reads stale Cassandra spend → smooth pacing computation is based on slightly old data → possible minute-level over-delivery if spend spikes.
- **Failure mode if wrong:** [Technical]: Synchronous Cassandra write per impression at 1.17M/sec saturates a 10-node Cassandra cluster (each node handling 117K writes/sec vs. ~100K comfortable max). Cassandra write latency climbs from 5ms to 50ms+, blocking ad serve for 50ms per impression. [Business]: Ad server P99 blows the 100ms RTB deadline. Disney loses the RTB auction for every impression where the response exceeds the bid deadline — at $14.40 CPM and 1.17M impressions/sec, this is $1M+ per hour of missed revenue.

### Trade-off 3: LongAdder Batching vs. Per-Impression Redis INCR (Stage 2 → Stage 3)

- **Chose:** LongAdder in-JVM accumulation with 10-second batch flush via INCRBY.
- **Gain:** Redis load drops from 1.17M INCR ops/sec to ~1,000 INCRBY ops/sec (1,000× reduction). Eliminates the hot key problem for most campaigns without sharding.
- **Lose:** Pacing gate counter in Redis is up to 10 seconds stale per ad server. If a campaign is at 99% of its minute budget and 10 ad servers simultaneously decide "budget remaining," all 10 flush at once — possible over-delivery of up to 10× 10-second impressions before gate closes.
- **Failure mode if wrong:** [Technical]: At Stage 2 (per-impression INCR), a viral campaign at 10M impressions/min generates 167K INCR ops/sec on one Redis keyslot. Redis single-threaded command execution on that keyslot saturates at ~100K ops/sec → pacing gate latency climbs from 0.1ms to 5ms+ → RTB window violation for that campaign. [Business]: ESPN Super Bowl ad serving slows for the exact campaign that is highest-value inventory. Advertiser's ad appears late or not at all during peak coverage — direct contract violation and chargeback risk.

---

## Section 11 — 🏰 Disney-Specific Depth

### Live Sports — The Thundering Herd at Ad Break Time

ESPN's live sports inventory is Disney's highest-value ad slot. When a TV timeout is called during an NBA Finals game:
- All streaming clients receive the ad break signal simultaneously
- 3–5M concurrent viewers transition from content to ad within 2 seconds
- Impression rate spikes from baseline to 3.5M/sec in under 5 seconds

The system must handle this **without pre-warming** (the timeout is real-time). The in-memory campaign index (volatile pointer swap) is the shield:

```java
// Background refresher never blocks ad serving
// All ad serving threads read the current pointer without locking
private volatile AdCampaignIndex activeIndex;

// Pointer swap — atomic for reference types on 64-bit JVM (JMM guarantee)
// Old index GC'd once all threads finish their current request
public void refreshIndex() {
    AdCampaignIndex newIndex = campaignService.fetchActiveIndex();  // ~100ms
    this.activeIndex = newIndex;                                     // atomic swap
}
```

During a live ad break, millions of requests read `activeIndex` concurrently — zero lock contention because `volatile` is read-only on the hot path.

### Disney+ / Hulu Premier Nights

When a new Marvel series episode drops on Disney+, the pattern is different:
- Gradual impression ramp (not instant spike)
- CTV (Connected TV) ads at $40+ CPM — 3× the ESPN baseline
- Higher CPM means fewer impressions to exhaust a budget: at $40 CPM → 250 impressions/min per $10 budget

The gate formula handles this transparently:
```
cpm_micros_per_impression = $40.00 × 1,000,000 ÷ 1,000 = 40,000 micros
minute_budget_micros = 10,000,000
Gate trips after: 10,000,000 / 40,000 = 250 impressions/min ✓
```

This is why the gate works in micros and not dollars — the math is identical regardless of CPM tier.

### End-of-Day Reconciliation — Disney's Billing Contract

Advertisers buy Disney inventory with exact delivery guarantees. At midnight:
1. Budget Controller reads Cassandra `SUM(total_spend_micros)` for all campaigns today
2. Compare against contracted daily budget
3. For over-delivery > 5%: credit the advertiser for the excess impressions (Disney absorbs cost)
4. For under-delivery > 5%: issue a "make-good" — serve the undelivered impressions the next day at no additional charge

This is why AP is the correct CAP choice: the reconciliation process is designed to make over-delivery financially safe. Disney's operations team runs this nightly. The Cassandra billing record is the contract artifact — it is immutable once written.

### For the Disney-Specific Depth prompt from the interviewer:

> "For Disney's ESPN live sports inventory — which is the highest-CPM ad slot — the pacing gate must be AP, not CP. A network partition that causes 5% over-delivery costs Disney roughly $720 on a $14,400 campaign, which is fully covered by the end-of-day make-good process. A CP gate that blocks ad serving during a partition costs $400K+ per live sports ad break. The AP decision is not a technical preference — it's a business requirement written into Disney's ad contracts."

---

## Section 12 — 🔬 Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why not update Cassandra directly when an impression is served?"**
> Cassandra write latency is 5–20ms per operation. At 1.17M impressions/sec, a synchronous Cassandra write adds 5–20ms to every ad serve — blowing the 100ms RTB window. We fire a Kafka event instead (non-blocking from the ad server's perspective; Kafka ACK is ~1ms). Flink consumes the Kafka topic and aggregates impressions into Cassandra every 60 seconds per campaign. The billing record is eventually consistent — up to 60 seconds late — which is fine because billing runs at end-of-day, not real-time.

### Surface Probe (Tier 1)

**Q: "What happens if Redis goes down?"**
> For the pacing gate: fail-open. When Redis is unreachable, the ad server serves all eligible campaigns without a budget gate check. Over-delivery accumulates and is detected at end-of-day reconciliation. This is the correct failure mode — stopping all ad serving to protect exact budget compliance would cost orders of magnitude more in lost revenue than the over-delivery. Kafka is the safety net: every impression is published to Kafka regardless of Redis state, so the billing record is always complete.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your pacing gate says `spent × CPM ≥ limit`. Is that correct?"**
> No — that's a CPM unit bug. CPM is cost per **mille** (1,000 impressions), not per impression. `spent × CPM` overstates spend by 1,000×. At $14.40 CPM, that formula blocks after 1 impression (1 × 14.40 = 14.40 > 10) when the correct gate should block at 695 impressions ($10.00 / ($14.40/1,000) ≈ 694). The correct formula works in micros: `spent_impressions × cpm_micros_per_impression ≥ minute_budget_micros`, where `cpm_micros_per_impression = $14.40 × 1,000,000 ÷ 1,000 = 14,400 micros`. This is consistent with the Cassandra billing schema which stores `cpgCostMicros` at the same unit.

### Deep Probe (Tier 2)

**Q: "How do you handle a campaign that burns its entire budget in the first 5 minutes of the day?"**
> Smooth pacing prevents this if the Budget Controller reacts in time. The formula is `remaining_budget ÷ remaining_minutes`. At minute 1, `minute_allowance = $14,400 / 1,440 = $10`. If the first minute over-delivers (e.g., 1,000 impressions at $14.40 CPM = $14.40 instead of $10), the Controller's next tick reduces the allowance: `($14,400 - $14.40) / 1,439 ≈ $9.99/min`. The system self-corrects. Front-loading only becomes catastrophic if the Controller fails entirely — which is why the Budget Controller key has TTL 120s (it expires and ad servers fail-open rather than running stale old limits indefinitely).

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "How would you extend this to global campaigns where a Disney+ advertiser wants to spend $100K across US, EU, and APAC?"**
> Cross-region Redis synchronization at impression-level latency is infeasible — network RTT alone is 60-150ms. The correct approach: partition the global budget by region at campaign setup time based on historical traffic curves. A $100K global campaign might be allocated $50K US, $30K EU, $20K APAC. Each region runs its own Budget Controller and Redis cluster independently. A global budget reconciliation job runs every 5 minutes and can shift allocation between regions if one region is significantly under-delivering (e.g., EU sports event ran shorter than expected). The 5-minute reconciliation lag means cross-region budget drift is bounded at roughly 1–2% of daily budget — within the 5% over-delivery tolerance.

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake: `spent × CPM ≥ limit` in the pacing gate** → **Why it's wrong:** CPM is cost per *mille* (1,000 impressions), not per impression. This formula blocks after 1 impression at any realistic CPM. → **What to say:** "I work in micros: `spent × cpm_micros_per_impression ≥ minute_budget_micros`. At $14.40 CPM: cpm_micros_per_impression = 14,400 micros; minute_budget_micros = 10,000,000 micros → gate trips at 695 impressions."

- **Mistake: Choosing CP (distributed lock) for the pacing gate** → **Why it's wrong:** A distributed lock at 1.17M impressions/sec means lock contention on every gate check. Under any delay, the lock wait queue causes RTB deadline violations. → **What to say:** "I explicitly choose AP. 5% over-delivery is covered by end-of-day make-good. Blocking live sports ad breaks is a contract breach worth orders of magnitude more."

- **Mistake: Synchronous Cassandra write in the ad serving path** → **Why it's wrong:** 5-20ms Cassandra write × 1.17M impressions/sec saturates any Cassandra cluster and blows the RTB window. → **What to say:** "Fire-and-forget Kafka publish. Cassandra gets the data within 60 seconds via Flink. Billing doesn't run in real-time."

- **Mistake: Using `AtomicLong.incrementAndGet()` under 1M+/sec contention** → **Why it's wrong:** CAS spin-retries waste CPU under high contention; `AtomicLong` is not designed for this throughput. → **What to say:** "`LongAdder` stripes across CPU cells — threads write to separate cells under contention, then `sumThenReset()` merges atomically on the 10-second flush cycle."

- **Mistake: Setting Redis TTL per impression with `EXPIRE` after `INCR`** → **Why it's wrong:** `INCR` → `EXPIRE` are two separate commands — not atomic. If the process crashes between them, the key has no TTL and lives forever. The stale count then gates the next minute's impressions incorrectly. → **What to say:** "Budget Controller initializes the spent key via `SETNX key 0 EX 120` before the minute window opens. TTL is set once at key creation, not per impression."

---

## Section 14 — 🧭 Disney Interview Signals Checklist

| Signal | Relevant? | How your design addresses it |
|---|---|---|
| **Guest-Centric Thinking** | ✅ | AP over CP ensures live sports ad breaks (Disney's highest-value inventory) are never blocked. A 5% over-delivery is made whole to the advertiser; blocking a $400K live ad break is not recoverable. The system prioritizes viewer experience (uninterrupted content) and advertiser delivery over exact per-impression accounting on the hot path. |
| **Technical Depth** | ✅ | CPM unit math explicit: `cpm_micros_per_impression = $14.40 × 10^6 ÷ 1,000 = 14,400 micros`; gate trips at 695 impressions/min (not 1). LongAdder vs. AtomicLong distinction under 1.17M/sec contention. Volatile pointer swap for zero-lock index refresh. SETNX for atomic TTL initialization (not INCR + EXPIRE). |
| **Imagination & Creativity** | ✅ | Smooth pacing formula (remaining_budget ÷ remaining_minutes) as a self-correcting controller — not a naive fixed-rate cron. Live sports thundering-herd pattern identified as the Disney-specific scaling event. Reconciliation as a contractual mechanism (make-good) rather than just a technical fallback. |
| **Trade-off Clarity** | ✅ | Three named trade-offs with quantified reasoning: (1) AP over CP — 5% over-delivery (~$720) vs. $400K+ live ad break loss. (2) Kafka async — 0ms ad serve impact vs. 5-20ms synchronous Cassandra path. (3) LongAdder batching — 1,000× Redis load reduction vs. 10s pacing counter lag. Each trade-off names the specific number that forces the decision. |
| **Scalability** | ✅ | Three-stage evolution with quantified breaking points: Stage 1 → Stage 2 at ~1K impressions/sec (Cassandra write latency blows RTB); Stage 2 → Stage 3 at ~10M impressions/min on one campaign (Redis keyslot CPU saturation at 167K INCR ops/sec). Stage 3 handles any scale via LongAdder + sharded counters. |
| **Reliability** | ✅ | Kafka 7-day retention enables full billing replay if Flink falls behind. Budget Controller TTL 120s ensures stale limits expire — ad servers fail-open rather than running on stale data indefinitely. Cassandra immutable append-only — billing record cannot be corrupted by a retried Flink write (UPSERT is idempotent). End-of-day reconciliation is the contractual recovery path. |
| **Communication Clarity** | ✅ | Two-layer problem stated upfront (pacing gate = AP + fast; billing = eventual + exact) — non-technical interviewer can follow the architecture without distributed systems background. CAP theorem presented as a business decision ("blocking live sports beats over-delivery") not just a technical preference. Budget Controller → Redis → Ad Server → Kafka → Flink → Cassandra is a linear story. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "Ad budget pacing is a two-layer problem: a fast AP pacing gate (Redis INCR + per-minute allowance from Budget Controller) and a slow exact billing record (Kafka → Flink → Cassandra). The gate checks `spent_impressions × cpm_micros_per_impression ≥ minute_budget_micros` — note the CPM-to-per-impression conversion or you'll block after 1 impression. At 1.17M impressions/sec, Cassandra cannot absorb synchronous writes — Kafka is fire-and-forget on the hot path. For viral campaigns (ESPN Super Bowl at 10M impressions/min), Redis becomes a hot key — LongAdder batches 10-second counts in JVM and INCRBY-flushes to sharded counters, reducing Redis ops from 1.17M/sec to ~1K/sec. The explicit CAP choice is AP: 5% over-delivery on a $14,400 campaign costs $720 in make-good; blocking a live sports ad break costs $400K+ in lost revenue."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 21, 2026 | **File created.** Disney R3 onsite — Ad Budget Pacing & Impression Counting. Full 15-section solution following Disney r3-solutions/solution-notes-standards.md. Key technical decisions: AP over CP for pacing gate, Kafka+Flink+Cassandra for billing, LongAdder+sharded counters for hot campaigns, smooth pacing formula, explicit CAP reasoning. **Critical fix:** source file `HLD-ad-impression-pacing.md` had CPM unit bug (`spent × CPM ≥ limit` blocks after 1 impression); corrected to micros formula (`spent × cpm_micros_per_impression ≥ minute_budget_micros`); worked example at 694-695 impressions/min verified. Disney-specific: live sports thundering herd, Disney+/Hulu CTV high-CPM tier, end-of-day reconciliation as contract make-good. |
