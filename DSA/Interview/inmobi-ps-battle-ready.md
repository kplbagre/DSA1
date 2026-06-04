# InMobi Problem Solving — Battle-Ready Guide (Temp File)

> **CRITICAL REMINDER: This is NOT system design.** From their PDF: *"a small business problem (not the system design type) that can be solved using a combination of data structures, data modelling and logic."*
>
> Don't draw boxes for load balancers, CDNs, or microservices. Think: **data structures + algorithms + operational logic at scale.**
>
> **What they evaluate (SDE2 bar from rubric):**
> 1. **Handling Ambiguity** — ask clarifying Qs, navigate the problem space
> 2. **System Constraints** — recognize scale, model solution accordingly
> 3. **Trade-off Analysis** — compare approaches, explain pros/cons, pick and justify
>
> **Problem sources:**
> - A (MySQL→MongoDB) — from their **official rubric PDF**
> - B (Daily spend cap) — **iDSP-flavored** (matches their domain)
> - C (Frequency capping) — **iDSP-flavored** (matches their domain)
> - D (Feature deprecation rollout) — **real InMobi SDE2 question** (LeetCode, 2022)
> - E (Master-slave debugging) — **real InMobi SDE2 question** (LeetCode, 2022)

---

# PART 1 — The 8-Step Framework (Your Spine)

## 🎨 Visual — The Flow You Follow EVERY TIME

```
┌─────────────────────────────────────────────────────────────┐
│            PROBLEM SOLVING FRAMEWORK                         │
│            (Follow this EXACT order every time)              │
│                                                             │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 1: RESTATE (30 sec)            │                   │
│  │ "So the problem is [one sentence]." │                   │
│  │ Give a tiny concrete example.        │                   │
│  └──────────────────┬───────────────────┘                   │
│                     ▼                                       │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 2: CLARIFY (2 min)             │                   │
│  │ Ask 4-6 questions. Categories:       │                   │
│  │  • Scale — how many? how big?        │                   │
│  │  • Latency — online or async?        │                   │
│  │  • Consistency — strong or eventual? │                   │
│  │  • Failure — rollback? data loss OK? │                   │
│  │  • Lifecycle — one-time or recurring?│                   │
│  │  • Boundaries — what's in scope?     │                   │
│  └──────────────────┬───────────────────┘                   │
│                     ▼                                       │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 3: ASSUMPTIONS (30 sec)        │                   │
│  │ "I'll assume 100M rows, 1KB each    │                   │
│  │  ≈ 100GB, ~10K writes/sec ongoing." │                   │
│  │ ALWAYS quantify. Numbers impress.    │                   │
│  └──────────────────┬───────────────────┘                   │
│                     ▼                                       │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 4: BRUTE FORCE (1 min)         │                   │
│  │ Name the naive approach.             │                   │
│  │ Say WHY it fails at scale:           │                   │
│  │ "This takes X hours / blocks writes  │                   │
│  │  / causes downtime — not acceptable."│                   │
│  └──────────────────┬───────────────────┘                   │
│                     ▼                                       │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 5: NAME THE BOTTLENECK (30 sec)│                   │
│  │ ★ THIS IS THE SENIOR MOVE ★         │                   │
│  │ "The bottleneck is [X]. Let me       │                   │
│  │  address that specifically."          │                   │
│  │                                      │                   │
│  │ Common bottlenecks:                  │                   │
│  │  • Single-threaded processing        │                   │
│  │  • Synchronous DB call on hot path   │                   │
│  │  • Network round-trip per request    │                   │
│  │  • Lock contention / single writer   │                   │
│  │  • Full table scan / no index        │                   │
│  │  • Data loss during migration window │                   │
│  └──────────────────┬───────────────────┘                   │
│                     ▼                                       │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 6: OPTIMISED APPROACH (5 min)  │                   │
│  │ Name the DATA STRUCTURES.            │                   │
│  │ Name the ALGORITHM / PATTERN.        │                   │
│  │ Name the OPERATIONAL MODEL:          │                   │
│  │  batch vs stream?                    │                   │
│  │  push vs pull?                       │                   │
│  │  sync vs async?                      │                   │
│  │  in-memory vs persistent?            │                   │
│  └──────────────────┬───────────────────┘                   │
│                     ▼                                       │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 7: TRADE-OFF TABLE (3 min)     │                   │
│  │ ★ MANDATORY — they penalise skipping│                   │
│  │                                      │                   │
│  │ "I see two approaches:               │                   │
│  │  Approach A: [name] — pros / cons    │                   │
│  │  Approach B: [name] — pros / cons    │                   │
│  │  I'd pick [A/B] because [reason]."   │                   │
│  └──────────────────┬───────────────────┘                   │
│                     ▼                                       │
│  ┌──────────────────────────────────────┐                   │
│  │ STEP 8: OPERATIONAL (2 min)         │                   │
│  │ ★ THIS IS WHERE SDE2 BECOMES SDE4 ★ │                   │
│  │                                      │                   │
│  │  • Monitoring — what metrics/alerts? │                   │
│  │  • Idempotency — safe retries?       │                   │
│  │  • Rollback — what if it fails?      │                   │
│  │  • Testing — how to validate?        │                   │
│  └──────────────────────────────────────┘                   │
│                                                             │
│  KEY INVARIANT:                                             │
│    Steps 1-4 anyone can do. Steps 5-8 separate SDE2 from   │
│    SDE1. The interviewer is WAITING for you to reach        │
│    steps 7 and 8. If you skip them, you lose.              │
└─────────────────────────────────────────────────────────────┘
```

## Quick-Fire: Bottleneck → Solution Pattern

| When the bottleneck is... | Solution pattern | One-line to say |
|---|---|---|
| "Data loss during migration" | **CDC (Change Data Capture)** | "Debezium tails the binlog into Kafka — captures every in-flight write" |
| "Sync DB call on hot path" | **In-memory + async sync** | "Keep state in-memory, write-behind to central store every N seconds" |
| "Single writer / lock contention" | **Sharded counters** | "Shard the counter per instance, merge periodically — accept eventual consistency" |
| "Too many network round-trips" | **Batching + local cache** | "Batch N requests per flush, cache reads locally with TTL" |
| "Need to process in order" | **Partitioned queue** | "Partition by key (user_id), each partition processes in order" |
| "Duplicate processing" | **Idempotent writes** | "Upsert on a deterministic key — retries are safe by construction" |
| "Large dataset scan" | **Incremental / cursor-based** | "Process in chunks using a cursor — don't load everything in memory" |
| "Can't afford downtime" | **Blue-green / shadow traffic** | "Run new system in shadow mode, compare outputs, then flip" |

---

# PART 2 — Problem A: MySQL → MongoDB Migration (FROM THEIR RUBRIC)

> **This is literally the example problem in their interview guide PDF.** There is a real chance they ask exactly this or something very close.

## The Full Script — What You Say at Each Step

### Step 1: Restate (30 sec)

> *"So the problem is: we have 100 million user records in MySQL and need to move them to MongoDB with zero downtime — meaning the application keeps serving reads and writes throughout the migration."*

### Step 2: Clarify (2 min)

Say these EXACT questions:

> 1. *"Is MongoDB the new source of truth post-migration, or are we running both in parallel long-term?"*
> 2. *"What's the current read/write load on MySQL? Rough QPS?"*
> 3. *"Is downtime truly zero, or can we accept a brief read-only window?"*
> 4. *"Do all 100M users need migrating, or only active ones?"*
> 5. *"What's the rollback story if MongoDB has issues mid-cutover?"*
> 6. *"Is the schema changing during migration, or is it a 1:1 mapping?"*

### Step 3: Assumptions (30 sec)

> *"I'll assume: MongoDB becomes the new source of truth. 100M rows, avg 1KB each ≈ ~100GB total. Ongoing writes at ~10K/sec. Zero downtime means the app must keep serving throughout. Rollback must be possible for at least one week post-cutover."*

### Step 4: Brute Force (1 min)

> *"The naive approach is `mysqldump` → transform → `mongoimport`. But this fails because: (a) the dump takes hours during which writes continue — those writes are lost, (b) we'd need to stop writes during import — that's downtime, and (c) there's no rollback if something goes wrong."*

> *"The bottleneck is: ongoing writes during the migration window. Any approach must handle in-flight data."*

### Step 5: Name the Bottleneck

> *"The core bottleneck is data loss during the migration window — writes happen to MySQL while we're loading into MongoDB, and those writes get missed. A secondary bottleneck is consistency verification — how do we know every record made it?"*

### Step 6: Optimised Approach (The 3-Phase Solution)

```
┌─────────────────────────────────────────────────────────────┐
│              3-PHASE MIGRATION APPROACH                      │
│                                                             │
│  PHASE 1: BULK SNAPSHOT                                     │
│  ┌──────────────┐    bulk load    ┌──────────────┐          │
│  │    MySQL     │ ──────────────▶ │   MongoDB    │          │
│  │  (snapshot   │  100M records   │  (baseline)  │          │
│  │   at time T) │  ~hours         │              │          │
│  └──────────────┘                 └──────────────┘          │
│                                                             │
│  PHASE 2: CDC CATCH-UP                                      │
│  ┌──────────────┐  binlog  ┌───────┐ upsert ┌──────────┐  │
│  │    MySQL     │ ───────▶ │ Kafka │ ──────▶│ MongoDB  │  │
│  │ (live writes)│ Debezium └───────┘        │(catching │  │
│  └──────────────┘                           │  up)     │  │
│                                             └──────────┘  │
│  ↳ CDC reads binlog from BEFORE time T                     │
│  ↳ Consumer applies as UPSERTS (idempotent!)               │
│  ↳ Catches every write made during + after Phase 1         │
│                                                             │
│  PHASE 3: CUTOVER                                           │
│  Once CDC lag < 1 second:                                   │
│  ┌──────────────┐ dual-write ┌──────────────┐              │
│  │     App      │ ──────────▶│   MongoDB    │ ◄── reads    │
│  │             │ ──────────▶│   MySQL      │ ◄── fallback │
│  └──────────────┘            └──────────────┘              │
│  ↳ Verify: counts, checksums, sample-record diffs          │
│  ↳ Flip reads to MongoDB                                   │
│  ↳ Keep MySQL alive 1 week as rollback                     │
│                                                             │
│  KEY INVARIANT:                                             │
│    CDC starting from BEFORE the snapshot timestamp          │
│    guarantees zero data loss — every write is captured      │
│    either in the snapshot or in the CDC stream.             │
└─────────────────────────────────────────────────────────────┘
```

Say this:

> *"Phase 1 — Bulk snapshot. Take a consistent snapshot of MySQL at timestamp T, maybe using a transaction-isolated replica. Bulk-load into MongoDB. This handles the 100M baseline."*

> *"Phase 2 — CDC catch-up. I'd use Debezium reading the MySQL binlog from BEFORE timestamp T, streamed through Kafka. A consumer applies these to MongoDB as upserts — so it's idempotent. The CDC pipeline catches every change made during and after the bulk load."*

> *"Phase 3 — Cutover. Once CDC lag is under 1 second, switch the app to dual-write or MongoDB-first. Verify counts, checksums, sample-record diffs. Flip reads. Keep MySQL as fallback for one week."*

### Step 7: Trade-Off Table

> *"I considered three approaches:"*

```
┌─────────────────────────┬─────────────────────┬────────────────────┐
│                         │ PROS                │ CONS               │
├─────────────────────────┼─────────────────────┼────────────────────┤
│ A. Dual-write from      │ Simple — app writes │ Consistency risk   │
│    day 1                │ to both DBs         │ if one write fails │
│                         │ No CDC infra needed │ Partial failures   │
│                         │                     │ hard to handle     │
├─────────────────────────┼─────────────────────┼────────────────────┤
│ B. CDC + Bulk snapshot  │ Zero data loss      │ Needs Debezium +   │
│    (MY PICK ★)          │ Idempotent by design│ Kafka infra        │
│                         │ Clean rollback      │ More moving parts  │
│                         │ Battle-tested pattern│                   │
├─────────────────────────┼─────────────────────┼────────────────────┤
│ C. Active-active        │ Most robust         │ Operationally heavy│
│    replication          │ Both DBs always     │ Overkill for       │
│                         │ in sync             │ one-time migration │
└─────────────────────────┴─────────────────────┴────────────────────┘
```

> *"I'd pick B — CDC + bulk snapshot. It's the safest for a one-time migration. Dual-write has consistency risks when one write fails. Active-active is overkill here. CDC gives us zero data loss and idempotent replays for free."*

### Step 8: Operational

> *"For monitoring: every record gets a `migration_version`. I'd track CDC lag, error rate, per-table row counts with a dashboard. Alert if lag exceeds 5 seconds."*

> *"Rollback: flip reads back to MySQL. CDC keeps both in sync so MySQL is always current. Rollback window = one week."*

> *"Verification: count comparison per table, random sample checksums (hash 1000 random records, compare), plus run both systems in shadow mode for a day before flipping reads."*

---

# PART 3 — Problem B: Daily Spend Cap at 1M QPS (iDSP-Flavored)

> **This is the most likely PS problem shape for iDSP.** Budget pacing is their bread and butter.

## The Full Script

### Step 1: Restate

> *"So we need to enforce a daily spend cap per advertiser across our bidding fleet. At 1 million QPS, the bidder must not bid for an advertiser once their daily budget is exhausted."*

### Step 2: Clarify

> 1. *"Hard cap or soft cap — is 1% overshoot acceptable?"*
> 2. *"How many advertisers — 10K or 10M?"*
> 3. *"What's the spend granularity — per-bid, per-win, per-impression?"*
> 4. *"How many bidder instances are running in parallel?"*
> 5. *"Is the budget reset daily at midnight, or rolling 24h?"*
> 6. *"What happens if Redis (or central store) goes down?"*

### Step 3: Assumptions

> *"I'll assume: ~50K active advertisers, soft cap (1% overshoot OK), spend counted on win-notification (not bid), ~100 bidder instances, daily reset at midnight UTC, Redis as central counter store."*

### Step 4: Brute Force

> *"Naive: every bid request hits Redis to check and update the global counter. At 1M QPS across 100 bidders = 1M Redis ops/sec just for budget checks. Redis can handle this technically, but it adds ~2-5ms latency per request on the hot path. At <100ms total budget, spending 5ms on a counter check is too expensive."*

### Step 5: Bottleneck

> *"The bottleneck is synchronous network round-trip to Redis on every bid request. At this QPS and latency budget, we can't afford it."*

### Step 6: Optimised Approach

```
┌─────────────────────────────────────────────────────────────┐
│          SHARDED LOCAL COUNTERS + ASYNC SYNC                 │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ Bidder 1 │  │ Bidder 2 │  │ Bidder N │                  │
│  │          │  │          │  │          │                  │
│  │ local    │  │ local    │  │ local    │                  │
│  │ counters │  │ counters │  │ counters │                  │
│  │ per advt │  │ per advt │  │ per advt │                  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                  │
│       │              │              │                        │
│       │ sync every   │ sync every   │ sync every            │
│       │ N seconds    │ N seconds    │ N seconds             │
│       ▼              ▼              ▼                        │
│  ┌──────────────────────────────────────────┐               │
│  │              Redis (central)              │               │
│  │   advertiser_123:spend = $4,521          │               │
│  │   advertiser_456:spend = $890            │               │
│  └──────────────────────────────────────────┘               │
│                                                             │
│  Each bidder:                                               │
│   1. On bid-win → increment LOCAL counter (O(1), no I/O)   │
│   2. Every N seconds → PUSH local delta to Redis           │
│   3. Every N seconds → PULL global spend from Redis        │
│   4. Compute local_allowance =                              │
│        (budget - global_spend) × (my_share)                 │
│   5. If local_allowance ≤ 0 → STOP bidding for this advt   │
│                                                             │
│  Data Structure on each bidder:                             │
│   ConcurrentHashMap<advertiserId, AtomicLong> localSpend    │
│   ConcurrentHashMap<advertiserId, Long> globalSpendCache    │
│   ConcurrentHashMap<advertiserId, Long> budget              │
│                                                             │
│  KEY INVARIANT:                                             │
│    No Redis call on the hot path. Budget check is           │
│    a pure in-memory comparison. Accuracy trades off         │
│    for latency — ~1% overshoot in worst case.               │
└─────────────────────────────────────────────────────────────┘
```

Say this:

> *"Each bidder keeps an in-process counter per advertiser using ConcurrentHashMap with AtomicLong — zero I/O, zero locks across bidders."*

> *"Every N seconds (say 5s), each bidder pushes its local delta to Redis and pulls the current global spend. Then it re-computes its local budget allowance."*

> *"The budget check on the hot path is pure in-memory: `if (globalSpend + localSpend >= budget) → don't bid`. No network call."*

### Step 7: Trade-Off Table

```
┌─────────────────────────────┬────────────────────┬────────────────────┐
│                             │ PROS               │ CONS               │
├─────────────────────────────┼────────────────────┼────────────────────┤
│ A. Redis check per request  │ Exact count        │ 2-5ms per request  │
│                             │ Zero overshoot     │ Kills latency      │
│                             │                    │ Redis becomes SPOF │
├─────────────────────────────┼────────────────────┼────────────────────┤
│ B. Local counters +         │ Zero hot-path I/O  │ ~1% overshoot      │
│    async sync (MY PICK ★)   │ <1ms budget check  │ (sync lag window)  │
│                             │ Redis down = still │                    │
│                             │ works short-term   │                    │
├─────────────────────────────┼────────────────────┼────────────────────┤
│ C. Kafka event stream       │ Full audit trail   │ Higher latency     │
│    + materialized view      │ Replay capability  │ More infra         │
│                             │                    │ Not real-time      │
└─────────────────────────────┴────────────────────┴────────────────────┘
```

> *"I'd pick B. At iDSP's scale, the hot path can't touch a database synchronously. 1% overshoot on a soft cap is acceptable — the alternative is adding 5ms to every bid, which at our QPS is unacceptable."*

### Step 8: Operational

> *"Monitoring: track `budget_overshoot_percentage` per advertiser. Alert if any single bidder's local spend deviates >5% from its expected share — that suggests uneven traffic distribution."*

> *"Failure mode: if Redis goes down, bidders keep using last-known budget. Degrade gracefully — don't stop bidding entirely. Set a conservative local cap (e.g., 2% of total budget per bidder) as a safety valve."*

> *"Testing: chaos-test Redis failures weekly. Shadow-mode on launch — run the new system alongside the old counter, compare counts for a day."*

---

# PART 4 — Problem C: Frequency Capping at Scale (iDSP-Flavored)

> **"Don't show the same ad to the same user more than N times per hour."** This is a classic iDSP problem that tests data structures + scale thinking.

## The Full Script

### Step 1: Restate

> *"We need to enforce that no user sees the same ad campaign more than N times in a given time window — say, 3 times per hour. This must work at millions of QPS on the bidding path."*

### Step 2: Clarify

> 1. *"Is it per-user-per-campaign, or per-user-per-creative?"*
> 2. *"Hard cap or best-effort?"*
> 3. *"Time window — sliding hour or fixed hour boundary?"*
> 4. *"How many unique users per hour — millions?"*
> 5. *"Can we tolerate slight over-delivery (user sees it 4 times instead of 3)?"*
> 6. *"Where is user identity resolved — device ID, cookie, or probabilistic?"*

### Step 3: Assumptions

> *"Per-user-per-campaign. ~100M unique users/day, ~50K campaigns. Sliding 1-hour window. Soft cap — slight over-delivery OK. User identified by device ID."*

### Step 4: Brute Force

> *"Naive: for each bid request, query a central DB: SELECT COUNT(*) FROM impressions WHERE user_id = X AND campaign_id = Y AND timestamp > now() - 1h. At millions QPS, this is impossible — each query touches potentially many rows, and the write path for recording impressions adds even more load."*

### Step 5: Bottleneck

> *"Same bottleneck as the spend cap — synchronous query on the hot path. Plus, the data volume: 100M users × 50K campaigns = 5 trillion possible (user, campaign) pairs. Can't store all of them."*

### Step 6: Optimised Approach

```
┌─────────────────────────────────────────────────────────────┐
│        FREQUENCY CAP — AEROSPIKE + LOCAL CACHE               │
│                                                             │
│  On each BIDDER:                                            │
│  ┌────────────────────────────────────────┐                 │
│  │ Local LRU Cache (Caffeine/Guava)       │                 │
│  │ Key: (userId, campaignId)              │                 │
│  │ Value: count (int)                     │                 │
│  │ TTL: 1 hour                            │                 │
│  │ Max size: ~10M entries (~200MB)        │                 │
│  └───────────────┬────────────────────────┘                 │
│                  │ cache miss                               │
│                  ▼                                          │
│  ┌────────────────────────────────────────┐                 │
│  │ Aerospike (central, shared across      │                 │
│  │           all bidders)                 │                 │
│  │ Key: "fc:{userId}:{campaignId}"       │                 │
│  │ Value: counter (integer)               │                 │
│  │ TTL: 1 hour (built-in, auto-expires!) │                 │
│  └────────────────────────────────────────┘                 │
│                                                             │
│  FLOW per bid request:                                      │
│   1. Check local cache for (userId, campaignId)             │
│      → cache HIT + count >= N → SKIP (don't bid) [<1ms]    │
│      → cache HIT + count < N  → BID                        │
│      → cache MISS → check Aerospike [~1ms]                  │
│                                                             │
│   2. On win-notification (async, off hot path):             │
│      → increment local cache count                          │
│      → increment Aerospike counter (async fire-and-forget)  │
│                                                             │
│  WHY AEROSPIKE:                                             │
│   • Sub-millisecond reads (SSD-backed, in-memory index)     │
│   • Built-in TTL per record — auto-cleanup, no cron jobs    │
│   • InMobi already uses it ("we love Aerospike")            │
│                                                             │
│  KEY INVARIANT:                                             │
│    Hot path = local cache check only (~0.1ms).              │
│    Aerospike is fallback for cache misses.                  │
│    Write path (increment) is async, off the critical path.  │
└─────────────────────────────────────────────────────────────┘
```

### Step 7: Trade-Off Table

```
┌─────────────────────────────┬────────────────────┬────────────────────┐
│                             │ PROS               │ CONS               │
├─────────────────────────────┼────────────────────┼────────────────────┤
│ A. Central DB per request   │ Exact count        │ Way too slow       │
│    (Redis/Aerospike sync)   │ Globally consistent│ at millions QPS    │
├─────────────────────────────┼────────────────────┼────────────────────┤
│ B. Local cache +            │ Sub-ms hot path    │ Slight over-count  │
│    Aerospike async          │ Auto TTL cleanup   │ across bidders     │
│    (MY PICK ★)              │ Graceful on failure│ (user may see N+1) │
├─────────────────────────────┼────────────────────┼────────────────────┤
│ C. Bloom filter per user    │ Memory efficient   │ False positives    │
│    per campaign             │ No TTL management  │ (block ads that    │
│                             │                    │ should show)       │
└─────────────────────────────┴────────────────────┴────────────────────┘
```

> *"I'd pick B. The local LRU cache handles 90%+ of checks in sub-millisecond. Aerospike gives us cross-bidder consistency for cache misses. TTL handles cleanup automatically. Over-delivery of 1 extra impression is acceptable for a soft cap."*

### Step 8: Operational

> *"Monitoring: track `freq_cap_violations_total` — how often users see >N impressions. Track cache hit rate — if it drops below 80%, increase cache size."*

> *"Failure: if Aerospike is down, local cache still works for ~1 hour (TTL). Degrade to local-only counting — slightly more over-delivery but system stays up."*

> *"Memory sizing: 10M entries × (16 bytes key + 4 bytes count + overhead) ≈ ~200MB per bidder. Acceptable."*

---

# PART 4B — Problem D: Deprecating a Feature & Rolling Out a New One (REAL WEB Q)

> **Source:** InMobi SDE2 interview experience (LeetCode, 2022). Exact prompt: *"How would you deprecate a feature and roll out a new one for existing clients?"*
> This is a **MIGRATION** shape problem — same spine as Problem A, but for feature/API migration instead of database.

## The Full Script

### Step 1: Restate

> *"So we have a feature currently used by N clients. We need to replace it with a new version — deprecate the old one and migrate all clients to the new one, without breaking anyone."*

### Step 2: Clarify

> 1. *"How many clients are on the old feature — tens or thousands?"*
> 2. *"Is this an API change, SDK change, or backend-only change?"*
> 3. *"Can old and new coexist, or is the old feature blocking new infra?"*
> 4. *"What's the timeline — hard deprecation date or gradual?"*
> 5. *"Do clients self-migrate, or do we migrate for them?"*
> 6. *"What's the blast radius if the new feature has a bug — can we rollback?"*

### Step 3: Assumptions

> *"I'll assume: it's an API-level feature used by ~1000 external clients. Old and new can coexist for a migration window. Clients must self-migrate with our support. Timeline: 3 months, with hard sunset after 6 months."*

### Step 4: Brute Force

> *"Naive: announce deprecation date, flip the switch, hope everyone migrated. Fails because: clients who missed the memo break, we get support tickets, revenue impact from broken integrations."*

### Step 5: Bottleneck

> *"The bottleneck is adoption risk — forcing all clients to migrate simultaneously is a coordination nightmare. Also, the old and new features may have subtle behavioral differences that cause silent failures."*

### Step 6: Optimised Approach — 4-Phase Rollout

```
┌─────────────────────────────────────────────────────────────┐
│            4-PHASE FEATURE DEPRECATION                       │
│                                                             │
│  PHASE 1: COEXISTENCE (Month 1)                             │
│  ┌──────────────────────────────────────────┐               │
│  │ Old feature ──▶ works as-is             │               │
│  │ New feature ──▶ available, opt-in       │               │
│  │ Adapter layer: old API calls internally │               │
│  │   routed to new backend (shadow mode)    │               │
│  └──────────────────────────────────────────┘               │
│  ↳ Compare outputs old vs new (shadow traffic)              │
│  ↳ Log divergences but serve old response                   │
│                                                             │
│  PHASE 2: CANARY MIGRATION (Month 2)                        │
│  ┌──────────────────────────────────────────┐               │
│  │ 5% clients ──▶ new feature (canary)     │               │
│  │ 95% clients ──▶ old feature (stable)    │               │
│  │ Feature flag per client_id              │               │
│  └──────────────────────────────────────────┘               │
│  ↳ Monitor: error rate, latency, support tickets            │
│  ↳ Instant rollback: flip flag back                         │
│                                                             │
│  PHASE 3: GRADUAL ROLLOUT (Month 2-3)                       │
│  ┌──────────────────────────────────────────┐               │
│  │ 5% → 25% → 50% → 100% new feature      │               │
│  │ Old feature: deprecated, still works     │               │
│  │ Dashboard: migration % per client        │               │
│  └──────────────────────────────────────────┘               │
│  ↳ Clients not migrated get nudge emails + deadline         │
│  ↳ Provide migration SDK / adapter library                  │
│                                                             │
│  PHASE 4: SUNSET (Month 6)                                  │
│  ┌──────────────────────────────────────────┐               │
│  │ Old feature returns HTTP 410 Gone        │               │
│  │ Traffic to old endpoint → redirect +     │               │
│  │   helpful error message                  │               │
│  │ Old code removed from codebase           │               │
│  └──────────────────────────────────────────┘               │
│                                                             │
│  KEY INVARIANT:                                             │
│    No client breaks silently. Every phase is reversible     │
│    until Phase 4. Feature flags control the rollout,        │
│    not code deploys.                                        │
└─────────────────────────────────────────────────────────────┘
```

Say this:

> *"Phase 1 — coexistence. Both features live side by side. I'd build an adapter that routes old API calls to the new backend in shadow mode. Compare outputs, log divergences, serve old response. This validates correctness without any client impact."*

> *"Phase 2 — canary. Feature-flag 5% of clients to the new feature. Monitor error rate, latency, support tickets. Instant rollback = flip the flag."*

> *"Phase 3 — gradual rollout. Ramp from 5% → 25% → 50% → 100%. Clients who haven't self-migrated get nudge communications and a migration SDK."*

> *"Phase 4 — sunset. After 6 months, old endpoint returns 410 Gone with a helpful error pointing to the new API. Remove old code."*

### Step 7: Trade-Off Table

```
┌──────────────────────────┬─────────────────────┬────────────────────┐
│                          │ PROS                │ CONS               │
├──────────────────────────┼─────────────────────┼────────────────────┤
│ A. Big-bang cutover       │ Simple              │ High blast radius  │
│    (flip switch on date) │ One-time effort     │ Breaks stragglers  │
│                          │                     │ No rollback        │
├──────────────────────────┼─────────────────────┼────────────────────┤
│ B. Feature-flag gradual  │ Reversible at every │ Longer timeline    │
│    rollout (MY PICK ★)   │ stage               │ Two code paths     │
│                          │ Shadow mode catches │ maintained for     │
│                          │ bugs before clients │ months             │
│                          │ see them            │                    │
├──────────────────────────┼─────────────────────┼────────────────────┤
│ C. Versioned API (v1/v2) │ Both live forever   │ Maintenance burden │
│    forever               │ No migration needed │ grows forever      │
│                          │                     │ Tech debt          │
└──────────────────────────┴─────────────────────┴────────────────────┘
```

> *"I'd pick B. The feature-flag approach lets us validate at every step, rollback instantly, and never break a client silently. The cost is maintaining two code paths for 3-6 months — worth it for a safe migration."*

### Step 8: Operational

> *"Monitoring: track `feature_flag_state` per client, `old_api_calls_total` (should trend to zero), error rate delta between old-path and new-path clients. Alert if new-path error rate exceeds old-path by more than 0.1%."*

> *"Rollback: at any phase, flip the feature flag. Keep the adapter layer until Phase 4 so rollback is always instant."*

> *"Communication: automated migration tracker dashboard. Email clients at 25%, 50%, 75% of the deadline. Provide migration guide + SDK + sandbox environment for testing."*

**iDSP hook:** *"At iDSP scale, a feature rollout on the bidding path has to be latency-safe — the adapter layer can't add more than 1ms. I'd pre-compute the feature flag at startup and cache it in-memory, not check a remote config service per request."*

---

# PART 4C — Problem E: Master-Slave Debugging (REAL WEB Q)

> **Source:** InMobi SDE2 interview experience (LeetCode, 2022). Exact prompt: *"Problems in master-slave arrangement for a process. How to debug it? Probable solutions?"*
> This is a **DEBUGGING / OPERATIONAL** shape — not a build-from-scratch problem. Tests your ability to reason about failure modes in distributed systems.

## The Full Script

### Step 1: Restate

> *"We have a process running in a master-slave arrangement — the master delegates work, slaves execute. Something's going wrong. I need to identify common failure modes, how to debug each, and how to fix them."*

### Step 2: Clarify

> 1. *"What kind of process — data replication (like DB), task execution (like worker pool), or leader election?"*
> 2. *"How do master and slaves communicate — sync RPC, async queue, shared storage?"*
> 3. *"What's the symptom — data inconsistency, task failures, split brain, performance degradation?"*
> 4. *"How many slaves — single-digit or hundreds?"*
> 5. *"Is there a health-check mechanism currently?"*

### Step 3: Assumptions

> *"I'll assume: a task-processing master-slave — master assigns tasks, slaves execute and report back. Communication via message queue. ~50 slaves. The symptom is that some tasks are being dropped or executed twice."*

### Step 4: The 5 Common Failure Modes + Debug + Fix

```
┌─────────────────────────────────────────────────────────────┐
│         MASTER-SLAVE FAILURE MODES                           │
│                                                             │
│  FAILURE 1: SPLIT BRAIN                                     │
│  ┌──────────────────────────────────────────┐               │
│  │ Master A: "I'm the master"              │               │
│  │ Master B: "I'm the master"              │               │
│  │ Slaves: confused, serve both             │               │
│  └──────────────────────────────────────────┘               │
│  Debug: Check leader election logs. Is the lease/lock       │
│         expiring? Network partition between masters?         │
│  Fix: Fencing token — each master gets a monotonic          │
│       epoch number. Slaves reject commands from stale       │
│       masters (lower epoch). Use ZooKeeper/etcd for         │
│       reliable leader election with session TTL.            │
│                                                             │
│  FAILURE 2: SLAVE LAG / REPLICATION DELAY                   │
│  ┌──────────────────────────────────────────┐               │
│  │ Master: committed write #1000           │               │
│  │ Slave A: caught up to #1000             │               │
│  │ Slave B: still at #950 ← lagging        │               │
│  └──────────────────────────────────────────┘               │
│  Debug: Track replication_lag metric per slave.             │
│         Check slave I/O thread, network bandwidth,          │
│         slave CPU/disk saturation.                          │
│  Fix: If read-after-write consistency needed → route        │
│       to master or wait for slave to catch up.              │
│       Throttle writes if all slaves lag (backpressure).     │
│                                                             │
│  FAILURE 3: TASK DUPLICATION                                │
│  ┌──────────────────────────────────────────┐               │
│  │ Master assigns task T to Slave A        │               │
│  │ Slave A slow to ACK → master thinks dead │               │
│  │ Master re-assigns T to Slave B           │               │
│  │ Both A and B execute T → DUPLICATE       │               │
│  └──────────────────────────────────────────┘               │
│  Debug: Check ACK timeout vs actual execution time.         │
│         Look for "task reassigned" log pattern.             │
│  Fix: Idempotent task execution — tasks have unique ID,     │
│       result stored in central DB with "completed" flag.    │
│       Before executing, check-and-set. Use lease-based      │
│       ownership: slave must renew lease during execution.   │
│                                                             │
│  FAILURE 4: TASK LOSS (DROPPED WORK)                        │
│  ┌──────────────────────────────────────────┐               │
│  │ Master sends task → slave dies mid-exec  │               │
│  │ No one picks up the task → LOST          │               │
│  └──────────────────────────────────────────┘               │
│  Debug: Compare tasks_assigned vs tasks_completed count.    │
│         Check for tasks in "assigned" state for > timeout.  │
│  Fix: At-least-once delivery — don't ACK until complete.    │
│       Use a "pending" queue: tasks move ASSIGNED → RUNNING  │
│       → COMPLETED. Reaper thread: if RUNNING > timeout,     │
│       move back to PENDING for re-assignment.               │
│                                                             │
│  FAILURE 5: HOT SLAVE / UNEVEN LOAD                        │
│  ┌──────────────────────────────────────────┐               │
│  │ Slave A: 500 tasks (overloaded)         │               │
│  │ Slave B: 10 tasks (idle)                │               │
│  │ Slave C: 50 tasks (normal)              │               │
│  └──────────────────────────────────────────┘               │
│  Debug: Dashboard with tasks_per_slave, CPU/mem per slave.  │
│  Fix: Work-stealing — idle slaves pull from busy slaves'    │
│       queues. Or: master uses round-robin + capacity-aware  │
│       assignment (check slave queue depth before assigning).│
│                                                             │
│  KEY INVARIANT:                                             │
│    Every failure is about one of 3 things:                  │
│    (1) WHO is the master? → Fencing tokens                  │
│    (2) Was the task DONE? → Idempotency + ACK              │
│    (3) Did someone DO it? → Reaper + at-least-once          │
└─────────────────────────────────────────────────────────────┘
```

### Step 7: Trade-Off Table (Debugging Approach)

```
┌──────────────────────────┬─────────────────────┬────────────────────┐
│                          │ PROS                │ CONS               │
├──────────────────────────┼─────────────────────┼────────────────────┤
│ A. Manual log analysis   │ Simple, no infra    │ Doesn't scale      │
│                          │ needed              │ Past ~10 slaves    │
├──────────────────────────┼─────────────────────┼────────────────────┤
│ B. Metrics + alerting    │ Real-time, scales   │ Need dashboards    │
│    (MY PICK ★)           │ Catches issues early│ Instrumentation    │
│                          │ Historical trends   │ effort up front    │
├──────────────────────────┼─────────────────────┼────────────────────┤
│ C. Distributed tracing   │ End-to-end per-task │ Overhead per task  │
│    (Jaeger/Zipkin)       │ visibility          │ More complex       │
└──────────────────────────┴─────────────────────┴────────────────────┘
```

> *"I'd start with B — metrics and alerting. Instrument: `tasks_assigned_total`, `tasks_completed_total`, `replication_lag_seconds`, `slave_queue_depth`. Alert on divergence. For deep debugging specific tasks, add distributed tracing selectively."*

### Step 8: Operational

> *"Key metrics: `task_completion_rate` (should be 100%), `replication_lag_p99`, `reassignment_count` (high = bad timeouts). Alert if completion rate < 99.9% over 5-min window."*

> *"Failure handling: every slave heartbeats to master every 5s. If master misses 3 heartbeats → mark slave as dead → requeue its in-flight tasks. Master itself is protected by leader election with fencing tokens."*

> *"Testing: chaos-test by killing random slaves mid-execution. Verify: no tasks lost, no duplicates in result store (idempotency check), rebalancing happens within 30s."*

**iDSP hook:** *"At iDSP, the bidding fleet is essentially a master-slave arrangement — the orchestrator distributes bid requests across bidder instances. If a bidder dies mid-auction, that bid opportunity is lost (acceptable — the exchange has other bidders). But for batch jobs like model training or data pipelines, task loss is NOT acceptable — that's where the reaper + idempotency pattern becomes critical."*

---

# PART 5 — Senior Concepts Cheat Sheet

## When to Drop Each Concept

Don't dump all 8 randomly. Drop them at the RIGHT moment:

| Concept | Drop it when you're discussing... | Exact line |
|---|---|---|
| **Idempotency** | Write path / retries | *"I'll make writes idempotent — upsert on a deterministic key — so retries are safe."* |
| **CDC** | Data migration / sync | *"Debezium tailing the binlog into Kafka captures every in-flight write."* |
| **Outbox pattern** | Dual-write risks | *"Dual-write is risky. Outbox: write to DB + outbox in one TX, relay publishes."* |
| **Consistent hashing** | Sharding / partitioning | *"Shard by user_id with consistent hashing — adding nodes reshuffles only a fraction."* |
| **Backpressure** | Producer-consumer lag | *"Bounded queues + either drop, sample, or backpressure upstream."* |
| **Bloom filter** | "Have we seen this?" checks | *"Bloom filter cuts the cache miss rate; HyperLogLog for unique counts."* |
| **Token bucket** | Rate limiting | *"Token bucket gives bursty tolerance; sliding window is smoother."* |
| **Sharded counters** | High-QPS counters | *"Shard counter in-process per bidder, sync to Redis every N seconds."* |

## The Decision Framework — Which Concept for Which Problem Shape

```
┌─────────────────────────────────────────────────────────────┐
│        PROBLEM SHAPE → CONCEPT TO REACH FOR                  │
│                                                             │
│  "Move data from A to B without losing writes"              │
│    → CDC + Bulk snapshot + Idempotent writes                │
│                                                             │
│  "Count/track something at millions QPS"                    │
│    → Sharded local counters + Async sync                    │
│                                                             │
│  "Limit something per user/entity"                          │
│    → Token bucket (bursty) or Sliding window (smooth)       │
│    → Local cache + central store with TTL                   │
│                                                             │
│  "Check if we've seen X before"                             │
│    → Bloom filter (approx) or HashSet (exact, more memory)  │
│                                                             │
│  "Process events reliably"                                  │
│    → Kafka + Idempotent consumers + DLQ for failures        │
│                                                             │
│  "Distribute data across nodes"                             │
│    → Consistent hashing (minimal reshuffling)               │
│                                                             │
│  "Write to two systems atomically"                          │
│    → Outbox pattern (NOT dual-write)                        │
└─────────────────────────────────────────────────────────────┘
```

---

# PART 6 — The Minimal Moves That Impress

## Your Minute-by-Minute Schedule

```
Minute 0-2:   RESTATE + CLARIFY
              "Let me restate: [one sentence]. Before I solve,
               5 quick questions: scale, latency, consistency,
               failure mode, scope."

Minute 2-3:   ASSUMPTIONS
              Quantify everything: rows, bytes, QPS, latency.

Minute 3-5:   BRUTE FORCE + BOTTLENECK
              "The naive approach is X. It fails because Y.
               The bottleneck is specifically Z."

Minute 5-12:  OPTIMISED APPROACH
              Name data structures. Draw the flow diagram.
              Explain the operational model (sync vs async,
              batch vs stream).

Minute 12-17: TRADE-OFF TABLE
              "I see approaches A, B, C."
              Pros/cons of each. Pick one. Justify.

Minute 17-20: OPERATIONAL CONCERNS
              Monitoring, rollback, failure modes, testing.

Minute 20+:   HANDLE FOLLOW-UPS
              They'll poke holes. Use the 8-step spine
              to stay structured.
```

## The 6 Lines That Score Maximum Points

| # | When | What to say |
|---|---|---|
| 1 | Before solving | *"The bottleneck here is [X] — let me name it before I optimise."* |
| 2 | When choosing approach | *"Trade-off: I'm choosing X over Y because the cost of being wrong on this axis is higher."* |
| 3 | When discussing consistency | *"This is eventual consistency by design — here's why that's acceptable for this use case."* |
| 4 | When discussing writes | *"I'd make this idempotent so retries are safe."* |
| 5 | When discussing monitoring | *"I'd track X_total and X_latency_p99 here and alert if..."* |
| 6 | When they ask about latency | *"The hot path can't touch a database synchronously at this QPS."* |

## What NOT to Do

| Mistake | Why it kills you |
|---|---|
| Jump to solution without asking questions | They explicitly penalise this |
| Draw load balancers, CDN, API gateway boxes | This is NOT system design — they said it in the PDF |
| Give only one approach | No trade-off table = instant weak signal |
| Skip operational concerns | That's what separates SDE2 from SDE1 |
| Say vague things like "we'll just shard it" | Name the DATA STRUCTURE and PARTITION KEY |
| Use Walmart jargon | Translate: "at my current company" not "at Walmart" |

## ❌ Do NOT Say → ✅ Say Instead

| ❌ | ✅ |
|---|---|
| "This is easy" | *(don't say anything — just solve it)* |
| "Whatever you prefer" | "I'd pick X because [reason]" |
| "I don't know" | "I haven't used X directly — my mental model is [Y], let me reason from there" |
| "We did this at Walmart" | "In a similar system I worked on, we used [approach] because [reason]" |
| "We'll just shard it" | "I'd shard by user_id using consistent hashing into N partitions" |

---

# PART 7 — Recognize the Problem Shape

> If they ask something you haven't seen, DON'T PANIC. Map it to one of these shapes:

```
┌──────────────────────────────────────────────────────────────┐
│     PROBLEM SHAPE RECOGNIZER                                  │
│                                                              │
│  "Move data from X to Y"                                     │
│    → Shape: MIGRATION                                        │
│    → Reach for: CDC, bulk snapshot, idempotent writes        │
│    → Example: Problem A (MySQL → MongoDB)                    │
│                                                              │
│  "Limit/cap/throttle something at scale"                     │
│    → Shape: DISTRIBUTED COUNTER                              │
│    → Reach for: sharded local counters, async sync           │
│    → Examples: Problem B (spend cap), Problem C (freq cap)   │
│                                                              │
│  "Process a stream of events reliably"                       │
│    → Shape: STREAM PROCESSING                                │
│    → Reach for: Kafka, partitioned consumers, DLQ            │
│    → Concepts: ordering, at-least-once, idempotency          │
│                                                              │
│  "Detect/prevent duplicates at scale"                        │
│    → Shape: DEDUPLICATION                                    │
│    → Reach for: Bloom filter (approx), HashSet (exact)       │
│    → Local cache + central store with TTL                    │
│                                                              │
│  "Schedule/delay/retry operations"                           │
│    → Shape: JOB SCHEDULING                                   │
│    → Reach for: priority queue, delayed queue, cron + DB     │
│    → Concepts: idempotency, at-least-once, dead-letter       │
│                                                              │
│  "Aggregate/rank/sort data in real-time"                     │
│    → Shape: REAL-TIME ANALYTICS                              │
│    → Reach for: pre-aggregated counters, OLAP (ClickHouse)  │
│    → Concepts: batch vs stream, pre-compute vs on-demand     │
│                                                              │
│  "Replace/deprecate a feature for existing users"            │
│    → Shape: FEATURE ROLLOUT / API MIGRATION                  │
│    → Reach for: feature flags, shadow traffic, canary %      │
│    → Concepts: adapter pattern, 4-phase rollout              │
│    → Example: Problem D (deprecate feature + roll out new)   │
│                                                              │
│  "Debug failures in a distributed process"                   │
│    → Shape: DISTRIBUTED DEBUGGING / OPERATIONAL              │
│    → Reach for: metrics + alerting, fencing tokens,          │
│      idempotent tasks, reaper threads, at-least-once         │
│    → Think in 3 failure axes: WHO is master? Was task DONE?  │
│      Did someone DO it?                                      │
│    → Example: Problem E (master-slave debugging)             │
│                                                              │
│  UNIVERSAL RESPONSE when you don't recognize the shape:      │
│    1. Restate                                                │
│    2. Ask 5 questions (scale, latency, consistency,          │
│       failure, scope)                                        │
│    3. Brute force → name the bottleneck                      │
│    4. The framework ALWAYS works. Trust it.                   │
└──────────────────────────────────────────────────────────────┘
```

---

> **Bottom line:** You don't need to know every system. You need to:
> 1. Follow the 8-step framework EVERY TIME (it's your spine)
> 2. Name the bottleneck BEFORE optimising (senior signal)
> 3. Give 2+ approaches with trade-offs and PICK one (mandatory)
> 4. Finish with operational concerns (monitoring, rollback, idempotency)
> 5. If lost, fall back to: "scale, latency, consistency, failure, scope"
>
> The framework works for ANY problem. Trust it.
