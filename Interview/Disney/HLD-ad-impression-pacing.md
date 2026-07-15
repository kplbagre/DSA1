# Disney Ad Platforms — HLD: Ad Budget Pacing & Impression Counting

> **Context:** Disney Entertainment and ESPN Technology, Ad Platforms org. Core problem: Disney sells ad campaigns with daily budgets (e.g., $14,400/day). The system must pace spend throughout the day to prevent over-delivery (spending the full budget in 2 hours) while serving 70M+ impressions per minute at sub-100ms latency.
>
> **When this comes up:** If Round 2 goes HLD, this is the most likely ad-tech specific question based on Disney's known infra (Disney Ads API, ESPN streaming ad insertion). Alternatively, the Rate Limiter LLD uses identical Redis INCR patterns — study both together.

---

## 🎯 Goal Statement (Say This to Open)

> *"This is a two-part problem: budget pacing — spread $14,400 evenly across 24 hours so we don't exhaust the budget at 9 AM — and impression counting — reliably count 70M+ impressions per minute to bill advertisers accurately. The tricky part is these two requirements pull in opposite directions: pacing needs soft real-time consistency (AP over CP is fine, minor over-delivery is acceptable), but impression counting for billing needs eventual strong consistency (no undercounting = lost revenue, no overcounting = chargebacks)."*

---

## 🔑 Technology Quick Reference

| Term | Plain-English meaning |
|---|---|
| **RTB** (Real-Time Bidding) | Advertisers compete in an auction per ad impression in <100ms. Highest bid wins and pays; winner's ad is served. |
| **DSP** (Demand Side Platform) | Software that lets advertisers buy ad inventory programmatically across many publishers (Disney, ESPN, Hulu). The "buyer" in RTB. |
| **SSP** (Supply Side Platform) | Disney's software that offers its ad inventory to DSPs. The "seller" in RTB. |
| **CPM** (Cost Per Mille) | Price per 1,000 ad impressions. $14.40 CPM × 1M impressions = $14,400. |
| **Impression** | One ad served once to one user. Billing unit for display/video ads. |
| **Budget Pacing** | Distributing a campaign's daily spend budget across time windows to avoid early exhaustion. |
| **Frequency Cap** | Max number of times a single user sees the same ad per day (e.g., 3× per 24 hours). Prevents annoyance. |
| **Redis INCR** | Atomic Redis command: increment a counter by 1 in a single server-side operation. Cannot race-condition. |
| **Kafka** | Distributed message queue; ad impressions are published here asynchronously, then consumed for billing aggregation. |
| **Flink** | Stateful stream processing engine; reads Kafka impression events, aggregates counts per campaign per minute. |
| **Cassandra** | Wide-column distributed database; stores impression counts time-series (partitioned by campaign + time window). |
| **LongAdder** | Java class that stripes a counter across CPU cells under high contention — reduces CAS failures vs. AtomicLong. |
| **CAP Theorem** | A distributed system can guarantee at most 2 of 3: Consistency, Availability, Partition tolerance. Choose AP or CP, not both. |

---

## 🧠 Mental Model — The Two-Layer Problem

```
LAYER 1 — Pacing Gate (fast path, ~1ms decision)
┌────────────────────────────────────────────────────────────┐
│  Ad Request → Check Redis budget counter → Allow or Reject │
│  Speed: must be sub-5ms to fit in 100ms RTB window         │
│  Accuracy: ±5% acceptable (minor over-delivery preferred   │
│            over blocking a live sports ad break)           │
│  Storage: Redis — atomic INCR, TTL per minute window       │
└────────────────────────────────────────────────────────────┘

LAYER 2 — Impression Counting (async path, eventual strong consistency)
┌────────────────────────────────────────────────────────────┐
│  Ad Served → Kafka → Flink (aggregate) → Cassandra         │
│  Speed: batch aggregation every 5-60 seconds acceptable    │
│  Accuracy: must be exact — this is the billing record       │
│  Storage: Cassandra time-series, immutable once written     │
└────────────────────────────────────────────────────────────┘
```

**KEY INSIGHT:**
> Layer 1 (Redis) is the real-time pacing gate — optimistic, AP, fast.
> Layer 2 (Kafka → Flink → Cassandra) is the source of truth for billing — eventual, strongly consistent, auditable.
> They're intentionally separated. Redis counters are reconciled against Cassandra records at end-of-day.

---

## 📖 Core Concepts

### Budget Pacing Strategies

| Strategy | How it works | Trade-off |
|---|---|---|
| **Even Pacing** | $14,400 / 1440 min = $10/min allowed | Rigid — misses high-value evening hours |
| **Frontloaded Pacing** | Serve more in hours 0-8, less in 8-24 | Risk: exhaust budget early if CTR is high |
| **Smooth Pacing** | Adjust per-minute budget based on remaining budget ÷ remaining time | Best UX; requires per-campaign state update every minute |
| **Target Impression Pacing** | Target X impressions/hour based on historical traffic curves | Requires ML; Disney likely uses this for ESPN primetime |

**For the interview — pick Smooth Pacing:**
> *"I'd use smooth pacing: remaining_budget ÷ remaining_minutes = this minute's allowance. Every minute, a budget controller recomputes the per-minute budget for each active campaign and writes it to Redis. The ad server reads this value — one Redis GET — and increments a counter per impression. When counter × CPM ≥ minute_budget, the campaign is paused for that minute."*

---

## 🧭 Architecture — The 3-Stage Pipeline

### Stage 1 — Ad Serving (Real-Time, <15ms)

```
User watches ESPN ──→ Ad Break triggered
                           │
                           ▼
              ┌────────────────────────┐
              │   In-Memory Ad Index   │
              │  (JVM heap, refreshed  │
              │   every 30 sec)        │
              │  - Active campaigns    │
              │  - Targeting rules     │
              │  - CPM bids            │
              └────────────────────────┘
                           │
              Campaign ID selected (<5ms)
                           │
                           ▼
              ┌────────────────────────┐
              │     Redis Cluster      │
              │  INCR budget:{campId}:{window}    │
              │  EXPIREAT end of window           │
              │  GET check vs. minute budget      │
              └────────────────────────┘
                           │
              Budget OK? ──→ Serve Ad → fire Kafka event (async)
              Budget exceeded? → Skip campaign, try next
```

**Critical: the in-memory index is a volatile pointer swap (explained below)**

### Stage 2 — Kafka Fan-Out (Async, Fire-and-Forget)

```
Ad Served Event:
  { campaignId, adId, userId, deviceType,
    contentType, timestamp, cpgCostMicros }
        │
        ▼
   Kafka Topic: "ad-impressions"
   Partitioned by: campaignId
   (all impressions for same campaign → same partition)
   (maintains time ordering per campaign)
        │
   ┌────┴────────────────────────┐
   │  Flink Consumer Group       │
   │  - Tumbling 1-min windows   │
   │  - SUM(cpgCostMicros)       │
   │  - COUNT(impressions)       │
   │  - GROUP BY campaignId      │
   │  - Writes to Cassandra      │
   └─────────────────────────────┘
```

**Why Kafka, not synchronous DB write:**
> *"Writing to Cassandra synchronously in the ad serving path would add 5-20ms — potentially blowing the 100ms RTB deadline. Kafka is fire-and-forget from the ad server's perspective. The event is durable (replicated across brokers) the moment it's acknowledged. Flink processes it async. We accept up to 5 seconds of impression count lag in Cassandra — billing runs at end-of-day, not real-time."*

### Stage 3 — Cassandra Storage Schema

```
Table: campaign_impressions
Partition key: (campaign_id, date)
Clustering key: minute_bucket (UNIX timestamp, floor to minute)

Example row:
(campaign_id="ESPNQ4", date="2026-07-15", minute=1720123200)
  → impressions_count=12483
  → total_spend_micros=89876540
  → p95_latency_ms=12
```

**Why Cassandra:**
> *"Impression data is write-heavy (70M+ inserts per minute across campaigns), time-series, and needs fast range queries (give me hourly spend for campaign X today). Cassandra's partition key on (campaign_id, date) keeps all of a campaign's data on the same node — fast range scans on the clustering key (minute_bucket). Write throughput is horizontal: add nodes, add capacity."*

---

## ⚠️ Hot Key Problem — Impression Counting at 70M/min

### The Problem

A single popular campaign (Super Bowl ad on ESPN) can generate **millions of impressions per minute**. If all those impressions INCR the same Redis key:

```
INCR budget:espn_superbowl_ad:202607150900
```

That single Redis key receives millions of ops/sec → **hot key** → Redis CPU saturates on that key → increased latency for all ad serving on that Redis node.

### Solution 1 — Redis Sharded Counters

```
# Instead of one key, N sharded keys:
# On ad serve:
shard = hash(impressionId) % NUM_SHARDS   # NUM_SHARDS = 16
INCR budget:espn_superbowl_ad:{window}:shard{shard}

# To read total:
total = SUM(GET budget:espn_superbowl_ad:{window}:shard0 ... shard15)
# This read is done by budget controller every minute — not per ad serve
```

**Trade-off:** Read is now 16 ops instead of 1. Acceptable because budget reads happen every 1 minute (by controller), not per ad serve.

### Solution 2 — Java `LongAdder` for In-JVM Counting

```java
// In-process impression counting for the current minute
// LongAdder stripes across CPU cells — much less CAS contention than AtomicLong
private final ConcurrentHashMap<String, LongAdder> impressionCounters
    = new ConcurrentHashMap<>();

public void recordImpression(String campaignId) {
    impressionCounters
        .computeIfAbsent(campaignId, id -> new LongAdder())
        .increment();
}

public long getAndReset(String campaignId) {
    LongAdder adder = impressionCounters.get(campaignId);
    if (adder == null) {
        return 0;
    }
    // sumThenReset() is atomic: gets sum and resets to 0 in one call
    return adder.sumThenReset();
}
```

**Then flush to Redis every 10 seconds** (not per impression):
> *"Instead of a Redis INCR per impression, I batch in-JVM using `LongAdder`, then do a single `INCRBY count:campaign:window totalCount` every 10 seconds. This reduces Redis ops from 70M/min to 6×campaignCount/min — orders of magnitude less load. The trade-off is up to 10 seconds of lag in the Redis pacing counter — acceptable for pacing (we're not billing from Redis)."*

---

## 🎨 Visual — Budget Controller Flow (Per Minute)

```
Every 60 seconds, Budget Controller runs:
┌───────────────────────────────────────────────────────────┐
│                                                           │
│  For each active campaign:                                │
│                                                           │
│  1. GET current spend from Cassandra (last 5 min lag OK)  │
│     actual_spend_today = SUM(spend_micros WHERE date=today)│
│                                                           │
│  2. Recompute this minute's budget allowance              │
│     remaining_budget = daily_budget - actual_spend_today  │
│     remaining_minutes = minutes until midnight            │
│     minute_allowance = remaining_budget / remaining_minutes│
│                                                           │
│  3. Write to Redis (all campaigns, pipelined)             │
│     SET budget_limit:{campaignId}:{currentMinute}         │
│         {minute_allowance}                                │
│         EX 120   (expires after 2 minutes)                │
│                                                           │
│  TOTAL TIME: <200ms for 10,000 active campaigns           │
│  (pipelined Redis writes, not 10K sequential roundtrips)  │
└───────────────────────────────────────────────────────────┘

Ad Server reading:
  limit = GET budget_limit:{campaignId}:{currentMinute}
  spent = INCR budget_spent:{campaignId}:{currentMinute}
  if spent * CPM > limit → skip campaign
```

**KEY INVARIANT:**
> The budget controller is eventually consistent with actual billing data (5-min Cassandra lag). The ad server's gate (Redis) is an optimistic check — slight over-delivery at window boundaries is acceptable. This is a deliberate AP choice (availability > consistency).

---

## 🧠 CAP Theorem Applied — Why AP, Not CP

**Interviewer:** *"What if two ad servers try to decrement the budget counter at the same time and Redis is partitioned?"*

**Your answer:**

> *"I explicitly choose AP (Availability + Partition tolerance) over CP (Consistency + Partition tolerance) for ad serving. Here's the reasoning:*
>
> *The cost of CP: if Redis rejects writes during a partition, both ad servers must reject the campaign's ads. A Disney live sports event ad break serves millions of dollars of ads — blocking it entirely to maintain exact spend accuracy is a catastrophic business choice.*
>
> *The cost of AP: during a network partition, two ad servers independently think the budget has headroom and both serve ads. We might over-deliver by 5-10%. At end-of-day reconciliation, we detect the over-delivery and credit the advertiser for the overage. Disney absorbs the cost of the over-served impressions.*
>
> *Over-delivery is a known, manageable cost. Under-delivery (blocking ad breaks) is a breach of contract and a revenue catastrophe. AP is the correct choice."*

---

## 🔬 Deep Dive — In-Memory Index with Volatile Pointer Swap

Disney's ad servers keep a full in-memory index of active campaigns for fast targeting (<5ms). The index is refreshed every 30 seconds from a Campaign Service (not blocking live traffic).

```java
// The volatile pointer: visible to all threads without locks
private volatile AdCampaignIndex activeIndex;

// Background refresher — runs every 30 seconds
public void refreshIndex() {
    // Build the new index entirely in background (takes ~100ms)
    // newIndex is a local variable — live traffic reads activeIndex, not newIndex
    AdCampaignIndex newIndex = campaignServiceClient.fetchActiveIndex();

    // Pointer swap is atomic for references on 64-bit JVM
    // After this line, ALL new reads see the new index
    // Old index is GC'd once no threads hold references to it
    this.activeIndex = newIndex;
}

public List<Ad> selectAds(AdRequest request) {
    // Reads the volatile reference — always sees latest pointer
    // No locking required — reads don't interfere with the swap
    AdCampaignIndex index = this.activeIndex;
    return index.match(request);
}
```

**Why `volatile` is sufficient here:**
> *"`volatile` guarantees that all threads see the latest value written to `activeIndex` — no stale cache in CPU registers. It does NOT provide atomicity for compound operations, but a pointer assignment is a single store — it's atomic on 64-bit JVMs by the JMM (Java Memory Model) guarantee. So: background thread writes new pointer, foreground threads always read the current pointer. No `synchronized`, no lock contention, zero impact on ad serving throughput."*

---

## ⚠️ Gotchas — "Silent Bug Hall of Fame"

### 1. AtomicLong vs LongAdder under high contention

```java
// ❌ AtomicLong — correct but slow at 70M/min on same counter
private final AtomicLong counter = new AtomicLong();
// Under heavy load: many threads spin retrying failed CAS → CPU waste

// ✅ LongAdder — under high contention, stripes across CPU cells
private final LongAdder counter = new LongAdder();
counter.increment();     // no CAS failures under contention
counter.sum();           // sums all stripes — not instantaneous snapshot
counter.sumThenReset();  // atomic reset — use for per-minute flush
```

### 2. Redis TTL Race — Expired Key Treated as Zero

```
Ad server does: INCR budget:campaign:{minute}
If the key expired (TTL elapsed) before INCR: Redis creates it at 0, then increments to 1.
Result: pacing gate thinks campaign has spent 1 this minute — never blocks.
```

**Fix:** Always SET with EX (expiry) as part of initialization, not after:
```
// Wrong:
INCR key
EXPIRE key 120

// Correct: budget controller sets key with expiry
SET budget_limit:campaign:{minute} {allowance} EX 120
```

### 3. Kafka Partition Skew

If partitioned by `campaignId` and a viral campaign gets 10M impressions in one minute, that partition has 10× the load of others. **Fix:** partition by `campaignId:shard` where shard = impression hash % 8. Flink consumer merges the 8 sub-partitions per campaign in its aggregation window.

---

## Interview Talking Points Cheatsheet

| Question | Answer |
|---|---|
| How do you prevent over-delivery? | "Redis INCR per impression, per-minute budget from Budget Controller, skip campaign when counter × CPM ≥ allowance" |
| Why not update Cassandra directly per impression? | "5-20ms Cassandra write kills RTB latency budget; Kafka + Flink gives durability without blocking ad serve" |
| What happens if Redis goes down? | "Fail-open: serve all ads, log impressions to Kafka, reconcile at end-of-day; revenue loss from over-delivery < revenue loss from blocking ads" |
| How do you handle a hot campaign at 10M impressions/min? | "Sharded Redis counters: 16 shards per campaign key, Budget Controller reads SUM at reconciliation time" |
| Why Cassandra over PostgreSQL for impressions? | "Write throughput: 70M impressions/min = ~1.2M/sec — Cassandra scales horizontally; Postgres doesn't without complex sharding" |
| CAP choice? | "AP — minor over-delivery acceptable; blocking ad breaks during partition is a contract breach" |
| LongAdder vs AtomicLong? | "LongAdder under high contention: stripes across CPU cells, no CAS spin waste; AtomicLong fine under low contention" |
| How often does Budget Controller run? | "Every 60 seconds; pipelined Redis writes for 10K campaigns takes <200ms; use remaining_budget ÷ remaining_minutes for smooth pacing" |

---

## 🗺️ 60-Minute Interview Delivery Plan

| Minute | What to Cover |
|---|---|
| 0–5 | Restate goal: two-part problem (pacing gate + billing accuracy). Ask: total campaigns count? daily traffic estimate? billing SLA? |
| 5–15 | Draw 3-stage architecture: In-Memory Index → Redis Gate → Kafka Emit |
| 15–25 | Pacing strategy: smooth pacing formula, Budget Controller, Redis INCR pattern |
| 25–35 | Impression counting: Kafka partitioning, Flink aggregation, Cassandra schema |
| 35–45 | Deep dive: hot key problem → LongAdder + sharded counters |
| 45–55 | CAP theorem discussion: explicit AP choice for ad serving, end-of-day reconciliation |
| 55–60 | Failure modes: Redis down (fail-open), Kafka lag (billing delay acceptable), Cassandra lag (reconcile at EOD) |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 15, 2026 | **File created.** Disney Ad Platforms HLD prep. Covers budget pacing (smooth pacing formula, Redis INCR, Budget Controller), impression counting at 70M/min (LongAdder, Kafka fan-out, Flink aggregation, Cassandra schema), hot key problem (sharded counters), volatile pointer swap for in-memory index, CAP theorem AP choice with explicit reasoning. 60-min delivery plan included. |
