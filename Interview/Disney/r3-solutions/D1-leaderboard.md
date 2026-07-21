# D1 — Design a Global Game Leaderboard

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Structure: clarify (5 min) → estimate (5 min) → API (3 min) → HLD (10 min) → deep dives (12 min) → trade-offs (8 min) → Disney depth (5 min) → Q&A (12 min).
> **Say this out loud** before your interview — don't just read it.

---

## 📚 Prerequisites — Study These First

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **Redis Sorted Sets** | `Core-Architecture/` (Redis patterns) | The entire leaderboard rank layer is a Redis sorted set — know ZADD, ZREVRANK, ZREVRANGE and their time complexity |
| **Caching fundamentals** | `Foundations/Performance-and-Scale/03-caching.md` | Redis is a derived cache; Postgres is the source of truth — understand cache invalidation, AOF vs. snapshot, rebuild-on-crash |
| **Scaling reads** | `Patterns/DeepDive/01-scaling-reads.md` | Rank reads are 10× writes — understand read replica, cache-aside, and when SQL ORDER BY stops working |
| **Sharding / consistent hashing** | `Core-Architecture/` | Stage 3 shards the sorted set across Redis nodes — know consistent hashing and fan-out merge |
| **API design patterns** | `Foundations/` | Pagination (offset vs. cursor), idempotent score submission (same game result re-submitted twice should not double-count) |

---

## 🎯 What Is This System?

**In plain English:** A leaderboard is a ranked list of players sorted by score, updated in near-real-time as game sessions end. Players can see their own rank, the top-K globally, and optionally compare with friends.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Clash of Clans (Supercell)** | Global and clan leaderboards for hundreds of millions of players; score updates every few minutes |
| **Fortnite (Epic Games)** | Season leaderboards for 350M registered accounts; Redis Sorted Set is confirmed in their architecture |
| **Xbox Leaderboards** | Per-achievement, per-game leaderboards — served via Azure Cache for Redis |
| **ESPN Fantasy Sports** | Weekly scoring leaderboards within leagues; rebuilt from DB on rollover |
| **Steam (Valve)** | Per-game leaderboards using ISteamLeaderboards API; partition by region |
| **Disney (Play Disney Parks)** | In-park game leaderboards — Millennium Falcon: Smuggler's Run assigns pilot roles and scores based on in-ride performance |

**Core user journey:** Player finishes a game session → score is submitted → their global rank updates within 1–2 seconds → they open the leaderboard screen and see rank #3,412 out of 10M players, up from #3,890 last game.

**Why it's hard to build at scale:** SQL `SELECT COUNT(*) WHERE score > :myScore` is O(N) — at 10M rows per game, each rank query reads millions of rows. At peak (thousands of reads/sec), Postgres CPU saturates before the first user sees their rank. The answer requires a data structure specifically designed for rank-in-sorted-order in O(log N).

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | **Design a Global Game Leaderboard** — users play different games, each with its own leaderboard; show updated ranking immediately when a game ends |
| **Interview Type** | **Type A — System Design** (Infrastructure: ranking data structure, durability, sharding at scale, time-windowed views) |
| **Confirmed or Likely** | ⭐ Confirmed asked in Disney onsite (multiple reports, 2025–2026) |
| **Concept notes prerequisite** | Redis patterns (ZADD, ZREVRANK, ZREVRANGE), `01-scaling-reads.md`, caching fundamentals |
| **Disney-specific angle** | Disney runs park ride games (Millennium Falcon: Smuggler's Run, Buzz Lightyear, Toy Story Mania) that generate real in-park leaderboards visible on guests' phones via the Play Disney Parks app. The "UX of Victory" — showing rank delta and celebration animation — is uniquely important to Disney's guest experience design. |
| **Time budget** | 5 min clarify → 5 min estimate → 3 min API → 10 min HLD → 12 min deep dive → 8 min trade-offs → 5 min Disney depth → 12 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I start drawing, let me ask a few clarifying questions — especially around scale, whether scores can update after submission, and whether we need time-windowed leaderboards or just all-time rankings, because each of those forks the architecture pretty significantly..."

Then immediately pivot to Section 2.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "How many games are we designing for, and how many daily active users across all games?"**
- Why ask: drives whether a single Redis node is sufficient or we need sharding. 10M DAU on one game is very different from 500M DAU across 1,000 games.
- If 10M DAU, 10 games → single Redis node per game (Stage 2 is sufficient)
- If 500M DAU, 1,000 games → sharded Redis + aggregated top-K cache (Stage 3)

**Q: "Is this a 'highest score ever' leaderboard, or does the score update on every game completion?"**
- Why ask: determines the write semantics. "Highest score ever" → ZADD with GT (only update if new score is greater). "Running total" → ZADD with INCR (add to existing score). These are different Redis operations and different SQL schemas.
- If highest score wins → ZADD GT (no increment); one row per player per game
- If cumulative total → ZADD INCR; row per session + running aggregate

**Q: "Do we need time-windowed leaderboards — daily, weekly, all-time — or just all-time?"**
- Why ask: time windows multiply the number of sorted set keys (one per window per game) and add TTL management.
- If all-time only → one sorted set per game
- If daily + weekly + all-time → three sorted sets per game, with TTL-based expiration on daily/weekly keys

**Q: "Do we need a friends leaderboard in addition to global?"**
- Why ask: friends leaderboard requires social graph traversal (given userId, get their friend list, then look up each friend's rank). This is a separate read path.
- If yes → need social graph store + ZSCORE lookups per friend + client-side sort (or Redis ZINTERSTORE with friends key)
- If no → simpler; skip the social graph

**Q: "What's the latency requirement for seeing rank after a game ends?"**
- Why ask: "within 1 second" is achievable with Redis; "within 10ms" requires Redis on the same datacenter as the game server; "within 5 minutes" allows batch updates.
- If < 2 seconds → synchronous Redis write on game completion
- If batch is OK → async queue, cheaper but staleness acceptable

**Q: "Can the same player submit a score twice for the same game session (retry/duplicate)?"**
- Why ask: idempotency design. If a retry is possible, ZADD GT is naturally idempotent (second submit with same-or-lower score is a no-op). But you need a dedup key to prevent the same session being counted twice if score actually differs across retries.
- If yes → session_id as idempotency key in the scores table

---

## Section 3 — 📋 Requirements (Functional + Non-Functional)

**Functional Requirements:**
- Players can submit a score when a game session ends; their global rank updates within 2 seconds
- Players can view their current rank globally (and optionally their percentile)
- Anyone can view the top-100 global players for any game
- Each game has its own independent leaderboard
- Optional (if asked): time-windowed views — daily and all-time
- Out of scope: friends leaderboard (can discuss as extension), real-time push of rank to player's phone (assume they poll on tab open), cheating detection / score validation

**Non-Functional Requirements:**
- Scale: 10M DAU, 5 game sessions per user per day → 50M score submissions/day; rank views ~50/user/day → 500M reads/day
- Latency: score submission P99 < 100ms; rank query P99 < 50ms
- Availability: 99.9% SLO (~9 hours downtime/year)
- Consistency: eventual — a player's rank shown within 1–2 seconds of game end is acceptable; no strict ordering guarantee across concurrent submissions
- Durability: scores are durable (Postgres is source of truth); the rank view (Redis) is derived and can be rebuilt from Postgres in ~30–60 seconds if Redis crashes

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

| Entity | What it represents |
|---|---|
| **Player** | User identity record — name, display handle, region; long-lived, read-heavy |
| **Game** | A specific game type with its own leaderboard (e.g., "Smuggler's Run", "Space Invaders 2026"); static metadata |
| **Score** | A player's best score for a specific game — append-only, immutable once committed; Postgres is source of truth |
| **GameSession** | A single play-through that generates one score event — ephemeral; used for idempotency (same session cannot submit twice) |
| **Rank** | Derived, ephemeral — a player's position in a game's sorted order; computed from the Score set and cached in Redis sorted set; rebuildable from Score if lost |

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–8)

**Traffic:**
```
DAU:            10M players
Score writes:   10M × 5 sessions/day = 50M writes/day ÷ 86,400 = ~580 writes/sec
                Peak (3×) = 1,750 writes/sec

Rank reads:     10M × 50 rank views/day = 500M reads/day ÷ 86,400 = ~5,800 reads/sec
                Peak (3×) = 17,500 reads/sec

Read:write ratio ≈ 10:1  (read-heavy — rank views dominate)
```

**Storage:**
```
Score record:   ~60 bytes (game_id UUID 16B, user_id UUID 16B, score BIGINT 8B,
                submitted_at TIMESTAMP 8B, session_id UUID 16B, overhead ~10B)
Records/day:    50M × 60 bytes = 3 GB/day
1 year:         ~1.1 TB (manageable in a single Postgres instance with partitioning by month)

Redis sorted set per game:
  10M players × 1 game × (user_id ~16B + score ~8B + skip-list overhead ~32B) = ~560 MB/game
  100 games → 56 GB total in Redis
  → Requires a Redis cluster (6-node cluster at 16 GB/node handles ~100 games)
```

**Key conclusions:**
- At 580 writes/sec, a single Postgres instance handles score writes comfortably (Postgres can sustain ~5K–10K simple INSERTs/sec)
- At 17,500 peak rank reads/sec, Postgres `ORDER BY score` cannot keep up — Redis sorted set at O(log N) is mandatory
- 56 GB total Redis memory for 100 games is well within a standard Redis cluster budget

---

## Section 5 — 🔄 Requirements Variation Table ⭐

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| **"10K users/day"** | Plain SQL — `SELECT COUNT(*)+1 WHERE score > :mine ORDER BY score DESC` | At 10K rows, Postgres index scan on (game_id, score DESC) returns in milliseconds; no Redis needed |
| **"100M users/day"** | Sharded Redis + aggregated top-K cache (Stage 3) | Single Redis node maxes out at ~50M entries per sorted set before OOM; hash-ring sharding across 3–6 nodes with a pre-aggregated top-100 cache |
| **"Strict consistency required"** | Synchronous Postgres write + Redis write in same request, with distributed lock or optimistic compare-and-set | Eventual model risks Redis showing stale rank for 1–2s; strict model doubles write latency (~10ms → ~50ms) |
| **"Eventual consistency OK"** | Redis write is synchronous (fast path); Postgres write is async via outbox relay | Score appears in leaderboard in <100ms; DB confirmation comes in <2s; acceptable for entertainment |
| **"Daily / weekly leaderboards"** | Separate sorted set key per time window per game: `game:{id}:daily:{YYYY-MM-DD}` | ZADD fans out to all applicable keys in one Redis pipeline; daily keys get TTL = 8 days; weekly keys TTL = 35 days |
| **"Friends leaderboard"** | Social graph (Postgres or DynamoDB); ZSCORE per friend + client-side sort | `ZINTERSTORE` works for small friend sets; for >1,000 friends, fetch ZSCORE for each friend individually and sort in application layer |
| **"Single-region Disney park game"** | One Redis cluster per park region, no cross-region synchronization | Park games (Smuggler's Run) only compete within the park; no need for global sync |
| **"Global Disney+ game"** | Multi-region Redis with async replication; global top-K rebuilt from each region's data | Cross-region latency (~100ms) makes synchronous replication impractical; accept eventual global view |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 8–20)

### 🎨 Visual — Architecture Evolution

```
══════════════════════════════════════════════════════════════════
STAGE 1 — Naive SQL  (≤ 100K users per game)
══════════════════════════════════════════════════════════════════

  Client ──────────▶  API Server ──────────▶  PostgreSQL
                                               scores table:
                                               (game_id, user_id, score)

  Score write:
    INSERT INTO scores (game_id, user_id, score)
    ON CONFLICT (game_id, user_id) DO UPDATE SET score = GREATEST(score, excluded.score)

  Rank query:
    SELECT COUNT(*) + 1 FROM scores
    WHERE game_id = :gid AND score > :myScore
    → O(N) index scan on (game_id, score DESC)

BREAKING POINT: Stage 1 breaks at ~100K rows per game (roughly 10K DAU × 10 games/user).
  The COUNT(*) rank query reads millions of index entries under load.
  At 5,800 reads/sec peak, each query takes 200–400ms on 1M rows — Postgres CPU saturates.
  Observable symptom: P99 rank query exceeds 500ms; users see "loading..." spinner after game end.
  Why Stage 2 is needed: a data structure that returns rank in O(log N) without a full scan.


══════════════════════════════════════════════════════════════════
STAGE 2 — Redis Sorted Set  (≤ 50M users per game, ≤ ~50 games)
══════════════════════════════════════════════════════════════════

                  ┌──────────────────────────────────────────────┐
  Client ───────▶ │              API Server                      │
                  └──────────────┬───────────────────────────────┘
                                 │
                    ┌────────────┴─────────────┐
                    ▼                          ▼
          ┌──────────────────┐       ┌──────────────────────┐
          │  Redis Cluster   │       │    PostgreSQL         │
          │  (ranking layer) │       │    (source of truth) │
          │                  │       │                      │
          │  game:{gameId}   │ ◀─── │    scores table      │
          │  sorted set      │  async│    (durable store)   │
          └──────────────────┘  CDC/ └──────────────────────┘
                                relay

  Score write path (synchronous → Redis, async → Postgres):
    1. ZADD game:{gameId} GT score userId      ← O(log N); GT = only update if new score > existing
    2. Publish score event to outbox queue
    3. Background relay writes to Postgres scores table

  Rank query path (all from Redis, O(log N)):
    → ZREVRANK game:{gameId} userId            ← 0-indexed rank, descending (highest score = 0)
       rank_1indexed = ZREVRANK result + 1     ← rank #1 = best player

  Top-K query:
    → ZREVRANGE game:{gameId} 0 99 WITHSCORES  ← top-100 players; O(log N + 100)

  Score lookup (to show player's own score):
    → ZSCORE game:{gameId} userId              ← O(1)

  Redis key design:
    game:{gameId}                    ← all-time leaderboard
    game:{gameId}:daily:{YYYY-MM-DD} ← daily leaderboard (TTL = 8 days)
    game:{gameId}:weekly:{iso-week}  ← weekly leaderboard (TTL = 35 days)

BREAKING POINT: Stage 2 breaks at ~50M users per game × 56 bytes/entry = 2.8 GB per game key.
  A single Redis node (32 GB) starts to OOM with ~10 large games + overhead.
  At 100M+ DAU or 1,000 games, Redis cluster memory is exhausted.
  Observable symptom: Redis evicts sorted set entries → ZREVRANK returns wrong rank (member not found).
  Why Stage 3 is needed: shard the sorted set so each node holds a partition of the user space.


══════════════════════════════════════════════════════════════════
STAGE 3 — Sharded Redis + Pre-Aggregated Top-K  (> 100M DAU)
══════════════════════════════════════════════════════════════════

  Client ───────▶ Score Router (consistent hash on userId)
                      │
           ┌──────────┴────────────────────┐
           ▼          ▼                    ▼
    ┌────────────┐ ┌────────────┐   ┌────────────┐
    │ Redis      │ │ Redis      │ … │ Redis      │
    │ Shard 1    │ │ Shard 2    │   │ Shard K    │
    │ userId:    │ │ userId:    │   │ userId:    │
    │ 0→33M      │ │ 34M→66M   │   │ 67M→100M  │
    └────────────┘ └────────────┘   └────────────┘
           │              │                │
           └──────────────┴───────┬────────┘
                                  ▼
                    ┌─────────────────────────────┐
                    │   Top-K Aggregator Job       │
                    │   (runs every 30 seconds)    │
                    │                              │
                    │ For each shard:              │
                    │   ZREVRANGE [0, 99]          │
                    │ K-way merge top-100          │
                    │ ZADD top-k:{gameId} result   │
                    └─────────────────────────────┘
                                  ▼
                    ┌─────────────────────────────┐
                    │   top-k:{gameId}            │
                    │   (pre-aggregated, 30s old)  │
                    └─────────────────────────────┘

  Per-user rank: hash(userId) → shard → ZREVRANK on that shard → O(log N/K)
  Global top-100: read top-k:{gameId} directly → O(100) — no fan-out on hot path
  Top-K aggregator: fan-out to K shards, ZREVRANGE 100 each, K-way merge → runs off critical path

  Trade-off: top-100 list is at most 30 seconds stale.
  Accept: for entertainment leaderboards, 30-second lag is invisible to guests.

KEY INVARIANT:
   ZREVRANK returns rank in DESCENDING score order.
   ZREVRANK = 0 means the member has the HIGHEST score (rank #1).
   Always add 1 to convert 0-indexed ZREVRANK to human-readable rank.
   ZRANK (ascending) would return rank #1 to the LOWEST scorer — never use for a leaderboard.
```

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 20–35)

### Deep Dive 1: Redis Sorted Set — The Ranking Layer

**Why this is the most critical component:**
The sorted set is the entire leaderboard. If it's wrong (wrong data structure, wrong Redis command, wrong tie-breaking), every rank is wrong. If it crashes without a recovery plan, the leaderboard goes dark.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **SQL ORDER BY + COUNT(*)** | Simple, no extra infra | O(N) per rank query; collapses at 100K+ rows |
| **SQL with precomputed rank column** | Fast reads | Rank must be recomputed on every score change (O(N) update); write-amplification is catastrophic at scale |
| **Redis Sorted Set** | O(log N) ZADD, ZREVRANK, ZREVRANGE; native support for rank-in-sorted-order | In-memory (volatile); requires source-of-truth DB for durability |
| **Elasticsearch** | Full-text + ranking in one; good for complex queries | Per-query rank computation via aggregation is slow; not designed for "give me exact rank of member X" |

**Decision: Redis Sorted Set.**
Because O(log N) rank reads at 17,500 reads/sec peak is the only path that stays within the 50ms P99 SLO. SQL cannot do this without precomputation that itself breaks under write load. The trade-off I'm accepting: Redis is volatile — I need a documented rebuild path from Postgres.

**Implementation sketch — the commands that matter:**

```
Score submission (game ends → new score = 92,500):

  ZADD game:smugglers-run GT 92500 "userId-abc123"

  GT modifier: only updates if 92500 > existing score.
  No GT → ZADD always overwrites, even with a lower score.
  If player submits duplicate (retry): same userId, same-or-lower score → no-op. Idempotent.

  For time-windowed leaderboards (pipeline — all 3 in one round trip):
  ZADD game:smugglers-run GT 92500 "userId-abc123"
  ZADD game:smugglers-run:daily:2026-07-21 GT 92500 "userId-abc123"
  ZADD game:smugglers-run:weekly:2026-W30 GT 92500 "userId-abc123"


Rank query (player opens leaderboard):

  result = ZREVRANK game:smugglers-run "userId-abc123"
  rank = result + 1        ← 0-indexed → 1-indexed

  ZREVRANK: scans from the highest score downward.
  Result = 0 → highest score holder (rank #1).
  Result = nil → player has not submitted a score for this game.


Top-100 display:

  ZREVRANGE game:smugglers-run 0 99 WITHSCORES
  → [(userId-abc, 250000), (userId-xyz, 248000), ...]
  → Fetch display names from Player table (cache with 10-min TTL)


Tie-breaking for equal scores:

  Default Redis behavior: equal scores sorted lexicographically by member name.
  For Disney's "earlier submission wins" tie-break:

  composite_score = raw_score × 1,000,000,000
                    + (season_end_epoch_sec - submit_timestamp_seconds)

  Why seconds not milliseconds?
    The time term must be smaller than one point of score (10^9).
    A millisecond-based term spans ~10^12 (trillions) and can let an early
    submitter outrank a player with a genuinely higher raw score.
    Seconds keeps the maximum time term under 10^8 (< 3 years of a season).

  Safety constraints (verify against game design):
    raw_score            ≤ 1,000,000   (1 million points max)
    time term            < 100,000,000 (< ~3 years in seconds)
    Max composite        ≈ 10^15       (< 2^53 ≈ 9×10^15 → no IEEE-754 precision loss)
    Time term (≤ 10^8)  <  score point (10^9) → score ALWAYS dominates

  Worked example (season_end_epoch_sec = 1,759,000,000):

    Player A: score 92,501 (higher), submitted at 1,753,500,000 sec (later)
    Player B: score 92,500 (lower),  submitted at 1,753,000,000 sec (earlier)

    A composite = 92,501 × 10^9 + (1,759,000,000 − 1,753,500,000)
                = 92,501,000,000,000 + 5,500,000
                = 92,501,005,500,000

    B composite = 92,500 × 10^9 + (1,759,000,000 − 1,753,000,000)
                = 92,500,000,000,000 + 6,000,000
                = 92,500,006,000,000

    A > B → Player A ranks higher. ✓ Higher raw score always wins.

  Tie-break (equal raw score):
    Player C: score 92,500, submitted earlier → larger time term → higher composite → ranks higher
    Player D: score 92,500, submitted later  → smaller time term → lower composite → ranks lower ✓
    Guest who finished first, placed first.
```

**Recovery from Redis crash:**

```
AOF (Append Only File) with everysec durability setting:
  → Syncs write log to disk every ~1 second
  → On crash, at most 1 second of ZADD writes lost
  → AOF is NOT the source of truth — it's a best-effort recovery shortcut

True recovery path (mandatory):
  Rebuild sorted sets from Postgres scores table:

  SELECT user_id, MAX(score) AS best_score
  FROM scores
  WHERE game_id = :gameId
  GROUP BY user_id
  ORDER BY best_score DESC;

  Then ZADD each row back into Redis in batches of 10,000.
  At 10M users: ~30 seconds rebuild time.
  During rebuild: serve stale top-K snapshot; show "Leaderboard refreshing..." badge.
  After rebuild: resume normal ZADD/ZREVRANK path.
```

---

### Deep Dive 2: Durability — Redis as Derived Cache, Postgres as Truth

**Why this is the second most critical component:**
Redis is volatile. A network partition, OOM eviction, or node restart can cause data loss. If there's no well-tested rebuild path, a Redis crash means a permanently corrupted leaderboard — a guest-experience failure at a Disney event.

**The durability design:**

```
Score Submission — Dual Write Pattern:

  Game Server
      │
      ▼
  POST /v1/games/{gameId}/scores
      │
      ├── 1. ZADD game:{gameId} GT score userId     ← Redis (synchronous, fast path)
      │        → Success in ~1ms
      │
      ├── 2. INSERT INTO score_outbox               ← Postgres outbox row (same DB txn)
      │        (game_id, user_id, score, session_id,
      │         status='PENDING', created_at=NOW())
      │
      │    [HTTP 201 returned to client here — Redis rank is already updated]
      │
      └── 3. CDC Relay (async, background):
               Polls score_outbox WHERE status='PENDING'
               → Upserts into canonical scores table
               → Marks outbox row status='PUBLISHED'

Postgres scores table = source of truth:
  - Contains every submitted score, immutable
  - Redis sorted set = derived view over scores table
  - If Redis loses data: rebuild from scores table (see Deep Dive 1)

Why async for Postgres write:
  - Postgres upsert with index maintenance: ~5–10ms
  - Redis ZADD: ~1ms
  - Keeping Postgres write synchronous doubles write latency from ~1ms to ~15ms
  - For a leaderboard, 1–2 second eventual consistency in Postgres is acceptable
  - The guest sees their rank immediately (Redis is synchronous) — Postgres is backup, not hot path

Failure scenario: Redis ZADD succeeds, then process crashes before Postgres outbox write:
  - Session_id is idempotency key: if the game server retries, ZADD GT is a no-op (score unchanged)
  - Outbox row was never written — relay will never pick it up
  - Score is in Redis but not in Postgres: a Redis crash now loses the score permanently
  Mitigation: Use a transactional outbox — write outbox row in same DB transaction as any
  application-level transaction. If the app has no DB transaction, write outbox row immediately
  after Redis ZADD (2-phase commit is overkill for entertainment). Accept the 1-2ms race window.
```

---

## Section 8 — 🌐 API Design

### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

Every endpoint starts from a functional requirement. The move is: **FR → operation → resource → HTTP method → contract.**

**"Players can submit a score when a game session ends"** → write operation → resource is a `score` under a `game` → `POST /v1/games/{gameId}/scores`. Who calls it? The game server (service-to-service, JWT with game-service role). What's the minimum payload? `userId`, `score`, `sessionId`. What's the response? Not just 201 — return `rank` and `delta` immediately, because the game server renders this to the guest. Disney UX: the rank badge appears on the victory screen in < 200ms total — the response must include the rank so we don't need a second round trip.

**"Players can view their current rank"** → read operation → resource is `rank` under `leaderboard` under `game` → `GET /v1/games/{gameId}/leaderboard/me`. Why `/me` not `?userId={userId}`? Auth is via Bearer token — the rank of the calling user, not arbitrary user lookup. Prevents one player enumerating exact ranks of all competitors. Response includes `percentile` — for Disney's "You're in the top 5% of Smuggler's Run pilots!" celebration text.

**"View top-100 globally"** → read, paginated → `GET /v1/games/{gameId}/leaderboard` → limit + offset. The probe: why offset not cursor? Because rank is stable during a single page view and the total count (10M) is known — offset is simpler and adequate. Cursor pagination is needed for feeds where rows are inserted mid-scroll.

---

### Core Endpoints

| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| `POST` | `/v1/games/{gameId}/scores` | Bearer (game-service role) | `{userId, score, sessionId, submittedAt}` | `{rank, previousRank, delta, score}` | 201, 400 (invalid score), 409 (sessionId already submitted) |
| `GET` | `/v1/games/{gameId}/leaderboard` | Public | — `?limit=100&offset=0&window=all-time` | `[{rank, userId, displayName, score}]` | 200 |
| `GET` | `/v1/games/{gameId}/leaderboard/me` | Bearer (player) | — `?window=all-time` | `{rank, score, percentile, previousRank}` | 200, 404 (player has no score) |
| `GET` | `/v1/games/{gameId}/leaderboard/player/{userId}` | Bearer (admin only) | — | `{rank, score, percentile}` | 200, 403, 404 |
| `DELETE` | `/v1/games/{gameId}/scores/{userId}` | Bearer (admin) | — | `{message}` | 200, 404 |

---

### 🔍 Endpoint Stories — Why Each One Exists

**`POST /v1/games/{gameId}/scores`** — Submits a player's score for a game session. The non-obvious part: the response returns `{rank, previousRank, delta}` immediately — `delta = previousRank - rank` (positive = moved up). This supports Disney's "UX of Victory" rank animation on the victory screen without a second API call. `sessionId` (the game session identifier — a unique ID for this specific play-through) is the idempotency key: if the game server retries on timeout, a duplicate POST with the same `sessionId` returns 409 (already recorded) without double-counting. The `score` field is validated server-side against game-specific max score rules (cheat detection: if a Smuggler's Run score exceeds the physical maximum possible in one ride, reject with 400).

**`GET /v1/games/{gameId}/leaderboard`** — Returns the paginated top-K list. Probe: "How do you support daily vs. all-time?" → `?window=all-time|daily|weekly` maps to the Redis sorted set key suffix (`game:{id}`, `game:{id}:daily:{date}`, `game:{id}:weekly:{week}`). Offset-based pagination is fine here because leaderboard position is stable during a single session; no rows are inserted mid-read at the API level (Redis snapshot is consistent per ZREVRANGE call).

**`GET /v1/games/{gameId}/leaderboard/me`** — Returns the calling player's rank without revealing exact ranks of other players (no userId param, auth-gated). Includes `percentile` computed as `(total_players - rank) / total_players × 100` (using Redis ZCARD for total_players — O(1)). Disney celebration copy uses this: "You're in the top 0.03% of Smuggler's Run pilots!" (for rank #3,412 at 10M players: (10M − 3,412) / 10M × 100 = 99.97th percentile → top 0.03%).

**`DELETE /v1/games/{gameId}/scores/{userId}`** — Admin-only endpoint for removing cheaters or test accounts. Calls `ZREM game:{gameId} userId` in Redis and soft-deletes (marks `is_deleted=true`) in Postgres. Idempotent (deleting a non-existent member is a no-op in Redis).

---

## Section 9 — 🗄️ Data Model

### Core Tables (PostgreSQL — source of truth)

```sql
-- Game registry
CREATE TABLE games (
    game_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    max_score    BIGINT,                          -- optional: cheat detection ceiling
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Canonical score store — one row per player per game (best score only)
CREATE TABLE scores (
    game_id      UUID NOT NULL REFERENCES games(game_id),
    user_id      UUID NOT NULL,
    score        BIGINT NOT NULL CHECK (score >= 0),
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    session_id   UUID NOT NULL,                  -- idempotency key: one submission per session
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (game_id, user_id)               -- one best-score row per player per game
);

CREATE INDEX idx_scores_game_score ON scores (game_id, score DESC)
    WHERE is_deleted = FALSE;                    -- partial index: excludes deleted rows

-- Outbox table — drives async Postgres sync from Redis write
CREATE TABLE score_outbox (
    id           BIGSERIAL PRIMARY KEY,
    game_id      UUID NOT NULL,
    user_id      UUID NOT NULL,
    score        BIGINT NOT NULL,
    session_id   UUID NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PUBLISHED | FAILED
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending ON score_outbox (status, created_at)
    WHERE status = 'PENDING';                    -- relay polls this index
```

### Redis Key Design (Derived Layer)

```
Sorted set keys:
  game:{gameId}                    → all-time leaderboard (no TTL)
  game:{gameId}:daily:{YYYY-MM-DD} → daily leaderboard (TTL = 8 days)
  game:{gameId}:weekly:{YYYY-Www}  → weekly leaderboard (TTL = 35 days)

Member:  userId (string — 36-char UUID)
Score:   composite_score (float64):
           raw_score × 1,000,000,000 + (season_end_epoch_sec - submit_timestamp_seconds)
           Time in SECONDS (not ms): keeps time term < 10^8 < one score point (10^9)
           Max composite ≈ 10^15 < 2^53 → no IEEE-754 precision loss
           → Same raw score: earlier submission → larger time term → ranks higher

Auxiliary keys:
  top-k:{gameId}                   → pre-aggregated top-100 (Stage 3 only; refreshed every 30s)
  player:prev-rank:{gameId}:{userId} → previous rank (string, TTL = 5 min; used for delta calculation)
```

### Key Schema Decisions

- **PRIMARY KEY (game_id, user_id)** on scores: enforces one best-score row per player per game. The `ON CONFLICT DO UPDATE SET score = GREATEST(score, excluded.score)` upsert naturally stores only the highest score.
- **Partial index with `WHERE is_deleted = FALSE`**: deleted (cheater) rows are excluded from rank queries without physical deletion; audit trail preserved.
- **Outbox in Postgres, not application memory**: if the API server crashes after ZADD but before Postgres sync, the outbox row is durable — no score is silently lost. If the outbox row was never written (crash between ZADD and INSERT), the score survives in Redis but not Postgres — mitigated by AOF + rebuild.
- **No `session_scores` table for all attempts**: storing only best score per player per game limits the scores table to 10M rows for 10M players — O(1) growth per user, not O(sessions). If session history is needed (analytics), write a separate `session_history` append-only table.

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 35–45)

### Trade-off 1: Redis Sorted Set vs. SQL for Ranking

- **Chose:** Redis Sorted Set as the rank layer; Postgres as source of truth
- **Gain:** O(log N) rank queries; at 10M members, ZREVRANK completes in ~1ms (23 skip-list comparisons). Handles 17,500 reads/sec comfortably on a 3-node Redis cluster.
- **Lose:** Two data stores to maintain; recovery procedure required for Redis crashes; memory cost (~560 MB per game at 10M players).
- **Failure mode if wrong:** [Technical]: If we chose SQL, at 17,500 peak rank reads/sec, Postgres CPU saturates at ~100K rows/game. Each `COUNT(*)` rank query takes 200–400ms under load, causing connection pool exhaustion. [Business]: Guests finish a Smuggler's Run session and the rank screen shows a spinning loader for 5+ seconds — or times out entirely. For a park ride game where the score reveal is part of the in-ride narrative, this directly degrades the guest experience rating. Park operations team sees app complaint spike.

### Trade-off 2: Async Postgres Write vs. Synchronous Dual Write

- **Chose:** Async Postgres write via outbox relay; Redis ZADD is synchronous (on the hot path)
- **Gain:** Score submission API returns in ~5ms (Redis write only). Guest sees rank update immediately after game end — under Disney's 100ms UX target.
- **Lose:** 1–2 second window where Postgres is stale. If Redis crashes during this window and the outbox row was also lost (unlikely but possible in a crash-before-INSERT scenario), the score is not recoverable from Postgres.
- **Failure mode if wrong:** [Technical]: If we chose synchronous dual write (Redis + Postgres in same request), the write path P99 becomes ~50ms (Postgres index maintenance under load). At 1,750 peak writes/sec, connection pool to Postgres saturates, causing API latency to spike. [Business]: Game servers queue up score submissions; players' post-game rank screens stall. During a Disney park event (e.g., "Galaxy's Edge Opening Day"), thousands of concurrent submissions create a write bottleneck — score submissions start timing out, and the leaderboard goes stale for the entire event.

### Trade-off 3: Exact Global Rank vs. Pre-Aggregated Top-K (Stage 3)

- **Chose:** At Stage 2 (≤ 50M users), exact ZREVRANK per user on a single sorted set. At Stage 3 (> 100M), shard + pre-aggregated top-K cache refreshed every 30 seconds.
- **Gain:** Stage 3 delivers top-100 list in O(100) (cached key read) rather than fan-out to 10 shards × 5ms = 50ms latency.
- **Lose:** Top-100 list is 0–30 seconds stale at Stage 3. A player who just overtook rank #1 globally may not appear on the top-100 for up to 30 seconds.
- **Failure mode if wrong:** [Technical]: If we kept live fan-out to all shards on the hot path, each top-100 request fans out to K shards, each doing ZREVRANGE + network round trip. At K=20 shards, fan-out latency = 20 × 5ms = 100ms per top-K request. At 17,500 reads/sec hitting top-100, 20 × 17,500 = 350,000 shard requests/sec — catastrophic for inter-shard network. [Business]: Leaderboard tab becomes slow at the exact moment it's most used — peak event time when every guest wants to check the top scores. Disney's "top pilots" screen lags, degrading the in-park gamification narrative.

---

## Section 11 — 🏰 Disney-Specific Depth

### "UX of Victory" — Rank Delta Animation

Disney's game design philosophy centers on **guest delight, not just information delivery**. The leaderboard response is not just a number — it's a story:

```
Game ends → API response:
{
  "rank": 3412,
  "previousRank": 3890,
  "delta": 478,      ← moved up 478 places
  "score": 92500,
  "percentile": 99.97
}
```

The game client renders this as: rank number counts up to 3412 from 3890, with a "climbed 478 places!" banner. This requires `previousRank` to be stored server-side (in Redis with a 5-minute TTL: `player:prev-rank:{gameId}:{userId}`). Without this, the client has to store the previous rank locally — which breaks across device switches and app restarts.

### Play Disney Parks App — Mobile Constraints

The Play Disney Parks app operates in a theme park environment: spotty LTE, thousands of concurrent guests on the same cell tower. The rank API must:
- Return in < 100ms P99 (user experience threshold for "instant" on mobile)
- Be cacheable for 5 seconds (if a guest taps "refresh" repeatedly, serve the cached rank — don't hammer Redis)
- Degrade gracefully: if Redis is unreachable, serve the last-known rank with a "Leaderboard is updating" indicator rather than a 500 error. Disney guests do not distinguish between "leaderboard down" and "app broken" — both generate park feedback cards.

### Seasonal / Event Leaderboards

Disney runs time-bound events: "Haunted Mansion October Challenge", "Star Wars Day Smuggler's Run Tournament", "Toy Story 30th Anniversary Score Attack." These map directly to time-windowed sorted sets:

```
ZADD game:smugglers-run:event:haunted-mansion-oct-2026 GT composite_score userId
EXPIREAT game:smugglers-run:event:haunted-mansion-oct-2026 <Nov-1-2026-epoch>
```

The event leaderboard key is created when the event starts and TTL is set to event end date. No cleanup job needed — Redis evicts automatically. The "Disney Events" page shows the top-10 from this key.

### For the Disney-Specific Depth prompt from the interviewer:

> "For Disney's Play Disney Parks app, the POST /scores response includes `rank`, `previousRank`, and `delta` to power the in-game rank animation without a second API call — because in a theme park environment with spotty LTE, we budget for one round trip per game session, not two. The rank is read from Redis ZREVRANK in the same request handler, so the total write → rank → response cycle completes in under 5ms on the server side, keeping the end-to-end well under Disney's 100ms mobile UX bar."

---

## Section 12 — 🔬 Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why not just use SQL with ORDER BY?"**
> SQL rank is O(N) — `SELECT COUNT(*) + 1 FROM scores WHERE score > :myScore` reads every row with a higher score, even with an index on (game_id, score DESC). At 10M rows per game and 17,500 reads/sec peak, Postgres CPU saturates. Redis ZREVRANK uses a skip list (the internal data structure — a probabilistic layered linked list that allows O(log N) search without the balance-overhead of a tree) and returns rank in O(log N) — at 10M members, that's roughly 23 comparisons, completing in under 1 millisecond.

### Surface Probe (Tier 1)

**Q: "What Redis command do you use to get a player's rank?"**
> `ZREVRANK game:{gameId} userId` — note the **REV** prefix. `ZREVRANK` returns rank in **descending** score order, so the member with the highest score gets ZREVRANK = 0 (which we convert to rank #1 by adding 1). `ZRANK` (without REV) returns ascending rank — rank #1 would be the **lowest** score, which is backwards for a competitive leaderboard.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "What happens if Redis crashes? How do you recover the leaderboard?"**
> Redis AOF (Append Only File) with `everysec` durability syncs the write log to disk every ~1 second — so at most 1 second of ZADD writes are lost on crash. But AOF alone is not the recovery plan, because: (1) AOF can also be corrupted if the crash is ungraceful, and (2) OOM eviction doesn't write to AOF — evicted members are silently gone. The true recovery path is a rebuild from Postgres: `SELECT user_id, MAX(score) FROM scores WHERE game_id = :gid GROUP BY user_id`, then batch ZADD each row back into Redis in pipelines of 10,000. For 10M users, this takes ~30–60 seconds. During rebuild, serve the last-known stale snapshot from disk (Redis RDB snapshot) with a "Leaderboard refreshing..." banner — don't show a 503.

### Deep Probe (Tier 2)

**Q: "How do you handle ties — two players with the same score?"**
> Redis default: equal scores sort lexicographically by member string. For competitive fairness where "earliest submission wins," I encode a composite score: `composite = raw_score × 1,000,000,000 + (season_end_epoch_sec - submit_timestamp_seconds)`. The key design choice is seconds (not milliseconds): the time term must stay below 10^8 (< ~3 years of any leaderboard season) so that one point of raw score (10^9) always outweighs any submission-time difference — a later submission can never beat a genuinely higher score. Two players with raw score 92,500 — the one who submitted at 10:00:00 AM has a larger inverted-seconds term and a higher composite, so they rank higher. The max composite (~10^15) sits comfortably within IEEE-754 double precision, which is exact to 2^53 ≈ 9×10^15. This is invisible to the guest — they see only their raw score, while the leaderboard ordering uses the composite internally.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "How do you implement time-windowed leaderboards (daily, weekly, all-time) without duplicating all user data?"**
> Use separate sorted set keys per time window: `game:{id}:all-time`, `game:{id}:daily:{YYYY-MM-DD}`, `game:{id}:weekly:{YYYY-Www}`. On score submission, ZADD fans out to all applicable keys in a single Redis pipeline — one round trip for all three writes. Daily keys get TTL = 8 days; weekly keys get TTL = 35 days; Redis auto-expires them, no cleanup job needed. The all-time key has no TTL. The data isn't duplicated — each sorted set stores only (userId, score) pairs (56 bytes each), not full score records. At 10M players across 3 time windows, total Redis memory is 3 × 560 MB = 1.7 GB per game — still well within a 16 GB Redis node budget per game.

### Cross-Concept Probe (Tier 3)

**Q: "How would you add a friends leaderboard?"**
> A friends leaderboard shows rank among a player's social graph rather than globally. The naive approach: fetch friend list from a social graph store (Postgres or DynamoDB), then call `ZSCORE game:{gameId} friendUserId` for each friend to get their score, sort client-side. At ≤ 500 friends, this is 500 Redis ZSCORE calls — use a pipeline, completes in ~2ms. For >1,000 friends: create a temporary sorted set per request (`ZINTERSTORE temp-{sessionId} 1 game:{gameId} WEIGHTS 1`... no — ZINTERSTORE doesn't filter by member list in Redis 6). Better approach: `ZADD temp-{sessionId} score friendId` for each friend's score (pipeline), then `ZREVRANK` on the temp key. TTL = 60 seconds on the temp key. Alternative for very large friend sets: maintain a separate `friends-game:{gameId}:{userId}` sorted set that's updated whenever a friend submits a score (fan-out on write).

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake: Using `ZRANK` instead of `ZREVRANK`** → **Why it's wrong:** `ZRANK` returns rank in ascending score order — the player with the lowest score gets `ZRANK = 0` (rank #1). This is a leaderboard where rank #1 means the worst player, which is backwards. → **What to say:** "I use `ZREVRANK` — the REV means descending, so rank 0 is the highest scorer. Then I add 1 for human-readable rank #1."

- **Mistake: Claiming Redis AOF makes the leaderboard fully durable** → **Why it's wrong:** AOF with `everysec` loses up to 1 second of writes on crash. OOM eviction doesn't write to AOF at all. Redis is still in-memory; the AOF is a recovery shortcut, not a guarantee. → **What to say:** "Redis is a derived cache. Postgres is the source of truth. If Redis crashes, I rebuild from Postgres. AOF reduces the rebuild window from potentially hours to 30 seconds, but it doesn't replace Postgres."

- **Mistake: Using `ZADD` without the `GT` flag, storing only the current score** → **Why it's wrong:** Without `GT`, a player who scores 50,000 then 30,000 has their leaderboard score overwritten to 30,000 — they fall behind players they had beaten. → **What to say:** "I use `ZADD GT` which only updates the member's score if the new score is greater than the existing one. This naturally stores only the personal best without any application-level check."

- **Mistake: Using `SELECT COUNT(*) + 1 WHERE score > :mine` for rank at scale** → **Why it's wrong:** This is O(N) even with an index — at 10M rows, Postgres reads millions of index entries per request. At peak read volume, this saturates CPU in seconds. → **What to say:** "This works at 10K users — that's Stage 1. Beyond that, I move rank queries to Redis ZREVRANK which is O(log N)."

- **Mistake: Placing the database choice (Redis, Postgres) in Section 3.5 Core Entities before scale estimation** → **Why it's wrong:** DB choice must be justified by Section 4 numbers. Saying "Redis" at minute 3 before you've calculated 17,500 reads/sec sounds like pattern-matching, not engineering reasoning. → **What to say:** Name entities and their nature (ephemeral, derived, append-only) in Section 3.5. Justify the specific technology in Section 6 after Section 4 establishes the load numbers.

---

## Section 14 — 🧭 Disney Interview Signals Checklist

| Signal | Relevant? | How your design addresses it |
|---|---|---|
| **Guest-Centric Thinking** | ✅ | POST /scores returns `rank`, `previousRank`, and `delta` in a single response — no second round trip. Guests on the Play Disney Parks app in a spotty LTE theme park environment see their rank badge appear on the victory screen in <100ms without a spinner. During Redis rebuild, a "Leaderboard refreshing..." banner keeps guests informed rather than showing a 500 error. |
| **Technical Depth** | ✅ | Redis Sorted Set skip list delivers O(log N) ZREVRANK at 17,500 peak reads/sec — contrasted explicitly against SQL's O(N) COUNT(*). Composite score encoding `raw_score × 10^9 + (season_end_sec - submit_sec)` uses seconds to keep the time term sub-dominant (< 10^8 < 10^9); max composite ~10^15 fits within IEEE-754 exact range (2^53 ≈ 9×10^15). ZADD GT avoids a separate "fetch current score, compare, update" round trip. |
| **Imagination & Creativity** | ✅ | "UX of Victory" — rank delta animation (previous rank → current rank with climbing count) goes beyond the standard textbook answer. Event leaderboard with auto-expiring sorted set key (EXPIREAT to event end date) is a non-obvious Disney-specific extension. Composite score tie-breaking supports the "earliest submission wins" narrative for park ride games where guests race each other. |
| **Trade-off Clarity** | ✅ | Three named trade-offs with quantified reasoning: (1) Redis vs. SQL — O(log N) vs. O(N) at 17,500 reads/sec peak. (2) Async Postgres write — 5ms vs. 50ms write latency, 1–2s staleness window accepted. (3) Pre-aggregated top-K — 30s staleness accepted to avoid 20-shard fan-out on top-100 reads. Each trade-off names the number that forces the decision. |
| **Scalability** | ✅ | Three-stage evolution with quantified breaking points: Stage 1 → Stage 2 at ~100K rows/game (COUNT* query P99 > 500ms); Stage 2 → Stage 3 at ~50M users/game (Redis node OOM at ~32 GB). At 10M DAU with 580 writes/sec and 17,500 reads/sec peak, Stage 2 single-node Redis cluster handles the full load within a standard 32 GB Redis node budget. |
| **Reliability** | ✅ | Redis recovery path documented: AOF everysec (1s loss window) + Postgres rebuild from `SELECT user_id, MAX(score) GROUP BY user_id` in ~30–60 seconds. Outbox pattern ensures no score is silently lost if API server crashes after Redis ZADD. ZADD GT is idempotent — game server retries are safe. Stale-cache-on-rebuild prevents 503s during recovery. |
| **Communication Clarity** | ✅ | Three-stage architecture presented in sequence: SQL → Redis → Sharded; each stage named with a breaking point number so the interviewer can ask "what if scale doubles?" and get a deterministic answer. API response includes `delta` which tells a non-technical interviewer the guest UX story without requiring them to ask a follow-up. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "The core of a global leaderboard is a Redis Sorted Set per game — ZADD GT to update scores in O(log N), ZREVRANK to get a player's rank in O(log N). SQL ORDER BY rank is O(N) and collapses at 100K+ users. I use ZADD with the GT modifier so only personal bests are stored, and composite score encoding (raw_score × 10^9 + inverted-submission-seconds) breaks ties in favor of earlier submissions while keeping score dominant. Postgres is the source of truth; Redis is a derived cache — if Redis crashes, rebuild in ~30 seconds from a SELECT MAX(score) GROUP BY user_id query. The POST /scores endpoint returns rank and delta in one response so the Disney guest sees their rank badge animate immediately after the game ends, within the Play Disney Parks app's 100ms UX budget."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 21, 2026 | **File created.** Disney R3 onsite — Design a Global Game Leaderboard. Full 15-section solution following Disney r3-solutions/solution-notes-standards.md. Key technical decisions: ZREVRANK (not ZRANK) for descending rank, ZADD GT for personal-best semantics, composite score tie-breaking, outbox relay for async Postgres durability, Postgres-rebuild recovery (not AOF alone), pre-aggregated top-K at Stage 3. Disney-specific: UX of Victory rank delta, Play Disney Parks app LTE constraints, event leaderboards with auto-expiring sorted set keys. |
| Jul 21, 2026 | **Bug fix — composite score formula.** Original formula used milliseconds (`MAX_EPOCH_MS - submit_ms`), which lets the time term span ~10^12 (trillions), allowing an early submitter to outrank a player with a genuinely higher score. Fixed to seconds: `raw_score × 10^9 + (season_end_epoch_sec - submit_timestamp_seconds)`. Seconds keeps the time term under 10^8, so one point of raw score (10^9) always dominates. Added safety constraints (max composite ~10^15 < 2^53) and a worked example proving score-dominates-time. Also fixed the §7 arithmetic example (92,500 × 10^9 = 92,501,000,000,000, not the 17-digit value previously written). Fixed §11 percentile (rank #3,412 at 10M players → 99.97th percentile = "top 0.03%", not 96.6). Updated §8, §9, §12, §14, §15 for consistency. |
