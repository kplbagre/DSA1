# Design a Feedly-like / Podcast Service API

> **Interview Type:** Type 1 — API + Data Model
> **Frequency:** ⭐⭐ Tier 2 — 2 confirmed in-window reports (May 2025 Podcast, Nov 2025 Feedly)
> **Key signal from research:** The reported rounds weighted REST contract precision + SQL schema heaviest — "any mistake highlighted as if the world has ended." **Prepare it as a full design anyway:** Sections 7 and 8 are first-class deliverables here, because the interviewer can expand the round at any moment with "now scale this to 100M DAU," and the staged HLD is the answer.
> **Standards file:** `solution-notes-standards.md`
> **API rules reference:** `api-design-cheatsheet.md` (verbs, codes, headers, pagination — not reproduced here)

---

## 🎯 What Is This System?

**In plain English:** A content subscription service where users follow their favourite publishers (podcasts, RSS — Really Simple Syndication: an XML-format web feed that any publisher exposes at a URL; your app polls that URL to get new content instead of you visiting the site — blogs, news sources), and the system aggregates new content from those publishers into a single personalized feed. Think: one inbox for all the things you care about, instead of visiting 20 websites separately.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Feedly** | RSS feed aggregator — subscribe to any RSS source, unified reading experience |
| **Apple Podcasts / Spotify Podcasts** | Subscribe to shows, episodes appear in library as they publish |
| **Google Podcasts (deprecated)** | Subscribe-based podcast feed, synced across devices |
| **Pocket Casts** | Podcast aggregator — subscription + playback progress sync |
| **Substack** | Writer-to-reader subscription; each post appears in subscriber feeds |

**Core user journey:** User searches for "The Changelog" podcast → subscribes → from that moment, every new episode appears in their personal feed automatically → they scroll the feed and tap an episode to listen.

**Why it's hard to build at scale:** At 10M users each following 50+ sources, generating a personalized feed requires either **fan-out-on-read** (executing the join at query time — the DB computes which episodes a user should see by joining their subscription list against the episodes table on every feed page load; expensive at high cardinality) or **fan-out-on-write** (pre-computing each user's feed when a new episode is published, so the read is a single indexed lookup; expensive at write time when a popular source has millions of subscribers). The right choice depends on the read:write ratio *and* on the fan-out distribution — and in this domain there is a third option that beats both. A *source* is a publisher, so the average source has tens of thousands of subscribers and fan-out-on-write never gets a cheap majority case. The answer at scale is to cache each **source's** recent episodes once and merge them per user at read time (Section 7 Stage 3).

**Tableflow parallel:** The Feedly ingestion pipeline (pull from RSS → normalize → fan out to subscriber feeds) is structurally identical to Tableflow's data plane: Kafka consumer → transform → materialized output. A Feedly-style subscription feed is the exact use case Tableflow was built to enable for analytics.

---

## 🚀 Section 1 — The One-Sentence Opener

> "Before I start, let me ask a few clarifying questions — the subscribe/unsubscribe and feed API are straightforward, but the feed generation strategy (fan-out on read vs. write) is a major architectural fork that I want to align on before we go into the contract."

Then immediately Section 2.

---

## 🔍 Section 2 — Clarifying Questions Script (Minutes 0–5)

**Q: "Is this a podcast service, an RSS reader, or a generic content-subscription platform?"**
- Why ask: shapes the core entity model — Episode (with audio_url, duration_sec) vs Article (with body_text) vs generic ContentItem
- If podcast → Episode entity, progress tracking endpoint is in scope
- If RSS/blog → Article entity, read/unread state, no audio
- **Assume for this session:** Podcast service (matches the May 2025 reported question). Interviewer can reframe as Feedly/RSS.

**Q: "Is the feed sorted purely by publish date (reverse-chrono), or does it involve any ranking or personalization?"**
- Why ask: pure chronological = simple cursor pagination; ranking = ML service + score field = much more complex
- If reverse-chrono → feed query is `ORDER BY published_at DESC`; cursor = last episode's timestamp
- If personalization → need a ranking score in the episodes table, a separate recommendation service; out of scope for a Type 1 round
- **Assume:** Reverse-chrono only. Mention ranking as a future extension.

**Q: "What's the expected scale — DAU and how many sources a typical user follows?"**
- Why ask: small scale = fan-out-on-read is fine; large scale = fan-out-on-write (pre-materialized feed table)
- If ~1M DAU (Daily Active Users), avg 20 subscriptions → read query joins 1M × 20 = 20M rows; manageable with indexes
- If ~100M DAU, avg 100 subscriptions → the per-query cost is still O(subscriptions per user): one index lookup for the user's 100 `source_id`s, then ~100 index range scans of ~20 rows each ≈ 2,000 rows read. That does not change with table size — what changes is the *number* of such queries, which is a throughput problem, not a per-query one
- **Assume:** 10M DAU, avg 30–50 subscriptions/user. Fan-out-on-read with proper indexes is the starting point; discuss switch point.

**Q: "Do we need to track listen progress per episode (so users can resume where they left off)?"**
- Why ask: progress tracking = one additional mutable table with high write frequency (every 30 seconds from the player)
- If yes → `episode_progress` table + `PATCH /v1/episodes/{id}/progress` endpoint
- If no → simpler schema; mark it out of scope
- **Assume:** Yes, basic progress tracking is in scope. Worth covering since interviewers probe the data model.

**Q: "Is subscription unique per user–source pair, or can you subscribe multiple times?"**
- Why ask: uniqueness → UNIQUE constraint in DB, 409 Conflict on duplicate subscribe, idempotency-key on POST is insufficient (we want the error, not silent dedup)
- If unique → `UNIQUE(user_id, source_id)` constraint, return 409 on second subscribe
- If not unique → just insert rows, no dedup logic needed
- **Assume:** Unique. One subscription per (user, source).

---

## 📋 Section 3 — Requirements

**Functional Requirements:**
- Users can search for and browse available podcast sources
- Users can subscribe to a source — new episodes appear in their feed automatically
- Users can unsubscribe from a source — its episodes stop appearing in the feed
- Users can list their current subscriptions
- Users can view their personalized feed (reverse-chronological episodes from all subscriptions)
- Users can view episode details for any episode
- Users can update their listen progress on an episode (for resume playback)
- Out of scope: audio streaming/hosting, push notifications, offline download, recommendation ranking, social features (sharing, comments)

**Non-Functional Requirements:**
- Scale: 10M DAU; ~500 subscribe/unsubscribe events/sec (peak); ~50K feed reads/sec
- Latency: Feed read P99 (99th-percentile — 99% of all requests complete within this time; the slowest 1% may exceed it) < 200ms; subscribe/unsubscribe P99 < 150ms
- Availability: 99.9% SLO (Service Level Objective — a measurable internal target; distinct from SLA which is a contractual commitment to customers with financial penalties)
- Consistency: Eventual for feed (new subscription's past episodes appear within a few seconds — not instant)
- Durability: Subscription records must not be lost (ACID writes)

---

## 🗂️ Section 3.5 — Core Entities

| Entity | What it represents |
|---|---|
| **Source** | Immutable once created — a podcast show or RSS publisher; identified by feed URL; has metadata (name, category, artwork) |
| **Episode** | Append-only — a content item published by a Source; immutable after publication; has audio URL, duration, publish timestamp |
| **Subscription** | Transactional — the binding between a User and a Source; created on subscribe, deleted on unsubscribe; unique per (user, source) pair |
| **EpisodeProgress** | Ephemeral-mutable — where a specific user is in a specific episode; updated every ~30 seconds by the player; overwritten on each update |

### 🎨 Visual — Entity Relationships

```
┌──────────────────┐         ┌──────────────────────┐
│     sources      │ 1 ────N │      episodes        │
│──────────────────│         │──────────────────────│
│ id  PK           │         │ id  PK               │
│ name             │         │ source_id  FK ───────┘
│ feed_url  UNIQUE │         │ title                │
│ category         │         │ audio_url            │
└────────┬─────────┘         │ duration_sec         │
         │                   │ published_at         │
         │ 1                 └──────────┬───────────┘
         │                              │
         N                              N (via user_id)
┌────────▼─────────┐         ┌──────────▼───────────┐
│  subscriptions   │         │   episode_progress   │
│──────────────────│         │──────────────────────│
│ id  PK           │         │ user_id   PK, FK     │
│ user_id  FK      │         │ episode_id  PK, FK   │
│ source_id  FK    │         │ position_sec         │
│ subscribed_at    │         │ updated_at           │
│ UNIQUE(user,src) │         └──────────────────────┘
└──────────────────┘

KEY INVARIANT:
  subscriptions = the "follow" graph (users ↔ sources).
  episode_progress = the "listen state" graph (users ↔ episodes).
  The feed query crosses both: "give me episodes FROM sources I follow,
  annotated with how far I've listened."
  No direct FK from subscriptions → episodes; the link is always
  subscriptions.source_id = episodes.source_id.
```

---

## 🔢 Section 4 — Scale Estimation

**Type 1 round — brief math only, no full envelope. Main value is justifying the fan-out trade-off.**

- 10M DAU × 40 avg subscriptions = 400M subscription rows total
- 10K sources against 400M subscriptions = **~40K subscribers on the *average* source** — hold onto this number; it is the one that decides the fan-out argument
- 50K feed reads/sec × the contract's `limit=20` = **1M episode rows returned/sec** at peak — this is the hot path
- Subscribe events: ~500/sec (manageable single-writer Postgres)
- Episode ingestion (from publisher RSS crawlers): low — perhaps 10K new episodes/day system-wide = **~0.12 writes/sec**
- **Progress writes: ~17K writes/sec — the dominant write load in the system.** The player `PATCH`es every 30 seconds (Section 2). At 10M DAU with ~5% concurrently listening at peak, that is 500K listeners × (1/30s) ≈ 17K upserts/sec into `episode_progress`. Every other write in this system is a rounding error next to it.

**Key conclusion:** Feed reads are the bottleneck *on the read side*, and `episode_progress` is the bottleneck *on the write side*. Note which is which — they want different storage.

A fan-out-on-read query (`SELECT episodes WHERE source_id IN (user's subscription set)`) does **not** "scan 400M rows." The `IN` clause holds ~40 values, not 400M: one index lookup returns the user's `source_id` list, then ~40 index range scans of ~20 rows each ≈ **2,000 rows read per feed page**, independent of how large `subscriptions` and `episodes` grow. 400M is table cardinality, which no feed query touches. Start with indexed fan-out-on-read; know the threshold at which you'd switch, and know why that threshold is about *breadth per user*, not *table size*.

**And note which write actually warrants a wide-column store.** The reflex is to reach for Cassandra to hold a materialized `user_feed`. But `user_feed` rows are `<user_id, episode_id, published_at>` — pure duplication of shared data (Section 7 Stage 3). `episode_progress` is the opposite: 17K writes/sec of genuinely per-user content, partitioned naturally by `user_id`, last-write-wins, append-mostly. *That* is the Cassandra-shaped workload here. At 10M DAU it still fits Postgres with a partitioned table and `ON CONFLICT DO UPDATE`, but it is the first table to move if it stops fitting.

### 🎨 Visual — Three Ways to Build the Feed (and which one loses)

```
① FAN-OUT ON READ — straight from Postgres  (Stage 1-2, our target)
───────────────────────────────────────────────────────────────────
  GET /v1/feed
        │
        ▼
  ⓐ SELECT source_id FROM subscriptions
       WHERE user_id = $user                   ← 1 index scan
        │
        ▼  (returns ~40 source_ids — the IN clause is 40 VALUES)
  ⓑ SELECT e.*, ep.position_sec
       FROM episodes e
       LEFT JOIN episode_progress ep ON ...
       WHERE e.source_id = ANY($source_ids)    ← 40 range scans
         AND e.published_at < $cursor            × ~20 rows each
       ORDER BY published_at DESC LIMIT 20     ← merge + sort

  Cost per query: ~2,000 rows read. O(subscriptions per user).
  INDEPENDENT of table size. Breaks on breadth, not on cardinality.

═══════════════════════════════════════════════════════════════════

② FAN-OUT ON WRITE — pre-materialized user_feed   ← REJECTED HERE
───────────────────────────────────────────────────────────────────
  New episode published (0.12/sec)
        │
        ▼
  Kafka "new-episodes" ──▶ Fan-out worker
        │                    ├── for each subscriber of that source:
        │                    │     INSERT INTO user_feed
        │                    │       (user_id, episode_id, pub_at)
        ▼
  GET /v1/feed ──▶ SELECT * FROM user_feed WHERE user_id = $user

  Read is O(1). But: 400M subs / 10K sources = ~40K subscribers on
  the AVERAGE source, so fan-out factor ≈ 40,000 on every write and
  there is no cheap majority case to exploit. And a user_feed row
  carries ZERO per-user content — 40K identical ID rows describing
  ONE episode. See Section 7 Stage 3 for the full argument.

═══════════════════════════════════════════════════════════════════

③ PER-SOURCE CACHE + READ-TIME MERGE   ← the scale answer (Stage 3)
───────────────────────────────────────────────────────────────────
  New episode ──▶ ZADD source:{id}:recent   ← ONE write. Factor = 1.
                  ZREMRANGEBYRANK keep 50

  GET /v1/feed ──▶ pipeline 40 × ZREVRANGE source:{id}:recent
                   k-way merge 40 pre-sorted lists → top 20
                   hydrate metadata + progress_sec

  10K keys × 50 episodes × ~200B = ~100 MB TOTAL, one shared copy.

KEY INVARIANT:
  Fan-out-on-write is cheap only when the fan-out factor has a small
  MAJORITY case (Twitter: median ~200 followers). Here the average
  source has ~40K subscribers — every source is a celebrity, so the
  celebrity carve-out rescues nothing. The real switch is ① → ③,
  driven by read throughput, and it costs 0.12 writes/sec either way.
```

---

## 🔄 Section 5 — Requirements Variation Table

| If the interviewer says... | Architecture changes to... | Reasoning |
|---|---|---|
| "1M DAU, 10 subscriptions avg" | Fan-out-on-read with composite indexes | 10M subscription rows + indexed query = fine for this load |
| "100M DAU, 100 subscriptions avg" | Per-source Redis cache + read-time merge (Section 7 Stage 3) — **not** a pre-materialized `user_feed` | Per-query cost is unchanged (100 index range scans ≈ 2,000 rows); what breaks is *throughput* — you would need ~20–30 full Postgres replicas, each a private copy of data that is ~99.99% identical across users. Cache the 10K shared source lists once and merge at read time. Fan-out-on-write is the wrong fix here because the average source has ~40K subscribers, so there is no cheap majority for push |
| "Feed must appear instantly after subscribe" | Strong consistency read; synchronous backfill of past episodes on subscribe | Async backfill introduces a window where old episodes are missing from feed |
| "Add ranking / personalization" | Scoring service adds `relevance_score` column; feed sorted by score DESC, then date DESC | ML pipeline introduces latency + a separate scoring store |
| "Multi-region (global)" | Source metadata replicated globally; subscription writes go to user's home region; feed queries local replica | Cross-region subscription writes need conflict resolution (user subscribes from two devices simultaneously) |
| "Podcast + RSS + YouTube channels" | Polymorphic `ContentItem` entity with `source_type` enum; Episode schema has optional `audio_url` / `video_url` / `article_body` | Schema evolution; Iceberg's schema evolution handles this cleanly if materialized to a lake |
| **"RSS/blog only (no audio)"** | Rename `Episode` → `Article`; swap `audio_url` + `duration_sec` → `body_text TEXT` + `reading_time_sec INT`; replace `PATCH .../progress` (position_sec) with `POST /v1/articles/{id}/read` returning `{ article_id, is_read, read_at }` (binary toggle, not a position); replace `episode_progress` table with `article_reads (user_id PK, article_id PK, read_at TIMESTAMP)`. **Everything else is unchanged:** subscriptions table, feed query, cursor pagination on `published_at`, fan-out logic, 409 on duplicate subscribe. | Podcast progress = continuous position (resume anywhere). Article state = binary read/unread. The subscription and feed layers are completely content-type-agnostic — only the "what the user did with this item" semantics change. |

---

## ⭐ Section 6 — API Design

> This is the primary deliverable for this round. 20 minutes minimum. Every verb, code, and header is evaluated.
> Reference: `./api-design-cheatsheet.md` for the full verb/code/header tables.

### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

Every endpoint starts from a functional requirement: **FR → operation → resource → HTTP method → contract.**

**"Users can subscribe to a source"** → create operation → resource is `Subscription` (not Source; the Source already exists) → `POST /v1/subscriptions`. Who calls it? Authenticated user. Minimum payload? The `source_id`. What comes back? The created subscription with its ID, so the client can later `DELETE /v1/subscriptions/{id}`. Why return the ID and not just 204? The client needs the subscription ID to unsubscribe by ID — if we don't return it here, the client must later look it up.

**"Unique per user–source pair"** → this constraint shapes the contract: a second `POST` for the same `(user_id, source_id)` must return **409 Conflict**, not 200 OK. The server is enforcing a uniqueness rule that the client violated. Adding an `Idempotency-Key` header would suppress this 409 — we do NOT want that here. We want the client to know it's already subscribed.

**"Users can view their feed"** → read operation → `GET /v1/feed`. Pagination is mandatory: the feed is unbounded and grows indefinitely. Cursor-based (not offset) because episodes are constantly added — offset pagination skips or duplicates items when the list changes between pages. Cursor encodes `published_at` timestamp of the last-seen episode.

**"Users can update listen progress"** → partial update of a specific episode's state for a specific user → `PATCH /v1/episodes/{id}/progress`. It's a partial update (only `position_sec` changes, not the episode record itself). Responds 200 with the updated progress object. Idempotency-Key makes sense here: if the player retries a progress update, re-processing is safe (same `position_sec`), but we want to avoid double-write race conditions in the progress table.

**Validation check:** Every FR maps to at least one endpoint. No orphan endpoints.

---

### Core Endpoints

| Method | Path | Auth | Request Body | Response Body | Status Codes |
|---|---|---|---|---|---|
| `GET` | `/v1/sources` | Bearer | — | `{ sources: [{id, name, category, episode_count}], next_cursor, has_more }` | 200, 401 |
| `GET` | `/v1/sources/{source_id}` | Bearer | — | `{ id, name, feed_url, category, artwork_url, episode_count, subscriber_count }` | 200, 401, 404 |
| `GET` | `/v1/sources/{source_id}/episodes` | Bearer | — | `{ episodes: [{id, title, published_at, duration_sec}], next_cursor, has_more }` | 200, 401, 404 |
| `POST` | `/v1/subscriptions` | Bearer | `{ source_id: "uuid" }` | `{ id, source_id, subscribed_at }` | 201, 400, 401, 404, 409 |
| `DELETE` | `/v1/subscriptions/{subscription_id}` | Bearer | — | — | 204, 401, 403, 404 |
| `GET` | `/v1/subscriptions` | Bearer | — | `{ subscriptions: [{id, source: {id, name}, subscribed_at}], next_cursor, has_more }` | 200, 401 |
| `GET` | `/v1/feed` | Bearer | `?cursor=<opaque>&limit=20` | `{ episodes: [{id, title, source, published_at, duration_sec, progress_sec}], next_cursor, has_more }` | 200, 401 |
| `PATCH` | `/v1/episodes/{episode_id}/progress` | Bearer | `{ position_sec: 1842 }` | `{ episode_id, position_sec, updated_at }` | 200, 400, 401, 404 |

---

### 🔍 Endpoint Stories

**`POST /v1/subscriptions`** — creates the user-to-source binding. Non-obvious: the request body contains `source_id`, not the source details (Source already exists; we're creating the Subscription). Returns **201 Created** with `Location: /v1/subscriptions/{id}`. Returns **404** if `source_id` doesn't exist in our sources table — the client sent a valid UUID format but for a source we don't know about. Returns **409 Conflict** if this `(user_id, source_id)` pair already exists — the user is already subscribed. This is NOT a 400 (the request is well-formed) — it's a state conflict. Directly satisfies FR: "Users can subscribe to a source."

**`DELETE /v1/subscriptions/{subscription_id}`** — deletes the subscription by subscription ID (not source ID). Non-obvious: returns **403 Forbidden** (not 404) if the subscription exists but belongs to a different user — the authenticated user does not own this subscription. Returns **404** if the subscription ID does not exist at all. Returns **204 No Content** on success (nothing to return). Why DELETE by subscription ID rather than `DELETE /v1/sources/{source_id}/subscriptions`? Because the subscription resource has its own identity; deleting by subscription ID is more precise and avoids ambiguity if a user somehow has multiple rows (schema bug).

**`GET /v1/feed`** — returns the authenticated user's reverse-chronological episode feed across all subscriptions. Cursor encodes `published_at` timestamp of the last item received (base64-encoded ISO timestamp). **Why cursor, not offset?** Episodes are constantly inserted. An offset of 20 at page 1 read-time may shift to offset 21 by page 2 read-time if a new episode published between requests — you'd skip the episode at the seam. Cursor is stable: "give me episodes published before this timestamp" is unambiguous regardless of new inserts. Response includes `progress_sec` for each episode (from EpisodeProgress if exists, else 0) — caller needs this to show the resume indicator without a second round-trip.

**`DELETE /v1/subscriptions/{subscription_id}` — 403 vs 404 probe:** "Why 403 and not 404?" — 404 leaks information: it tells a malicious client "this subscription ID doesn't belong to you, but it does exist." 403 Forbidden is the safer choice — the server knows the resource exists but refuses to confirm it belongs to the requesting user. This is a Confluent-level probe point.

**`PATCH /v1/episodes/{episode_id}/progress`** — partial update of a user's playback position. `position_sec` is the only mutable field. Non-obvious: the episode itself is immutable — we're updating the user-specific progress record, not the episode record. If no progress record exists yet, this endpoint creates one (upsert semantics). Returns **400** if `position_sec` is negative or greater than the episode's `duration_sec`. Returns **404** if `episode_id` doesn't exist.

---

## 🏗️ Section 7 — High-Level Architecture

> **Delivery note — build it up, don't draw the finished thing.** Present the architecture the same way you present the contract: start with the simplest system that actually serves `GET /v1/feed` correctly (Stage 1), then let the Section 4 numbers — 10M DAU, 50K feed reads/sec, ~500 subscribe/sec, 400M subscription rows — force each next stage. Opening with Kafka and Cassandra signals a memorized architecture. Opening with one Postgres and naming the exact number where it dies signals you can size a system.

---

### Stage 1 — Single Postgres, Crawler Writes Inline (handles ~1M DAU)

> One service, one database. The RSS crawler inserts episodes synchronously; the feed is a live join. This genuinely works, and it is where you start.

```
── Stage 1: One Service, One Postgres ─────────────────────────────

  [Mobile / Web]                     [RSS Crawler — cron job]
        │                                  │ polls each feed_url
        │ REST                             │ ~10K new episodes/day
        ▼                                  │ INSERT inline (sync)
  ┌──────────────┐                         │
  │ API Gateway  │                         │
  └──────┬───────┘                         │
         ▼                                 ▼
  ┌─────────────────────────────────────────────────────┐
  │      Feed / Subscription Service (1 process)         │
  │─────────────────────────────────────────────────────│
  │  POST /v1/subscriptions → INSERT subscriptions      │
  │  GET  /v1/feed          → live fan-out-on-read join │
  └─────────────────────────┬───────────────────────────┘
                            ▼
  ┌─────────────────────────────────────────────────────┐
  │             Postgres (single primary)                │
  │  sources · episodes · subscriptions ·                │
  │  episode_progress                                    │
  │  INDEX episodes (source_id, published_at DESC)       │
  └─────────────────────────────────────────────────────┘

At 1M DAU: ~5K feed reads/sec, ~50 subscribe/sec, 40M subscription
rows. One primary with that composite index serves all of it.

BREAKING POINT: Stage 1 breaks at ~1M DAU / ~5K feed reads/sec
  because ONE Postgres primary is doing two incompatible jobs, and
  two separate failures show up together:
   (a) Reads and writes compete for the same CPU and buffer cache.
       Each feed page is 40 index scans (one per subscribed source)
       plus a merge sort; at 5K reads/sec that is ~200K index scans
       per second on the same instance the crawler is inserting into.
       CPU saturates and the feed query misses its P99 < 200ms SLO.
   (b) Ingest has no durable landing zone. Note this is NOT a volume
       problem — 10K episodes/day is ~0.12 inserts/sec, and even if
       every publisher clustered into one 60-second window that is
       167 inserts/sec, which Postgres does not notice. The problem
       is DURABILITY: the crawler parses and inserts in one process,
       so a crash mid-batch loses those episodes permanently. There
       is nothing to replay from, and no way to re-run a fixed
       parser over content already fetched — the episode simply
       never appears in anyone's feed, silently.
  Observable symptom: feed query P99 > 200ms while pg CPU pegs at
  100%; missing-episode bug reports that correlate with crawler
  restarts and with parser deploys.
  Why Stage 2 is needed: feed reads must be separated from the write
  path, and episode ingestion needs a durable, replayable buffer.

══════════════════════════════════════════════════════════════════
```

---

### Stage 2 — Kafka Ingestion + Read Replicas + Redis (10M DAU — our target)

> Split the write path from the read path. Kafka absorbs crawl bursts; read replicas absorb the 50K feed reads/sec; Redis shaves the repeat reads. This is the design for the stated requirement.

### 🎨 Visual — System Data Flow

```
── Stage 2: Kafka + Replicas + Cache ──────────────────────────────

 USER FLOW (HTTP)                 INGESTION FLOW (background)
 ────────────────                 ───────────────────────────
 [Mobile / Web]                   [RSS Crawler — scheduled]
       │ 50K feed reads/sec             │ polls each feed_url
       ▼                                ▼
 ┌──────────────┐             ┌───────────────────────────┐
 │ API Gateway  │             │  Kafka: "new-episodes"     │
 └──────┬───────┘             │  partitioned by source_id  │
        ▼                     └─────────────┬─────────────┘
 ┌──────────────────────┐                   ▼
 │  Feed / Subscription │     ┌───────────────────────────┐
 │  Service  (N pods)   │     │   Episode Processor        │
 └───┬─────────────┬────┘     │   parse RSS/Atom XML       │
     │ cache read  │ writes   │   normalize → Episode      │
     ▼             │          │   INSERT ON CONFLICT       │
 ┌────────────┐    │          │     DO NOTHING (idempotent)│
 │   Redis    │    │          └─────────────┬─────────────┘
 │ PER-SOURCE │    │                        │
 │ page cache │    │                        │
 │ 2-min TTL  │    │                        │
 │ ~30% hits  │    │                        │
 └────────────┘    ▼                        ▼
          ┌──────────────────────────────────────────┐
          │        Postgres PRIMARY (writes only)     │
          │  subscriptions · episodes · progress      │
          └──────────────────┬───────────────────────┘
                             │ streaming replication (WAL)
       ┌────────────┬────────┴───┬────────────┐
       ▼            ▼            ▼            ▼
 ┌───────────┐┌───────────┐┌───────────┐┌───────────┐
 │ Replica 1 ││ Replica 2 ││ Replica 3 ││ Replica 4 │
 └───────────┘└───────────┘└───────────┘└───────────┘
      feed reads only · ~17K reads/sec each = 68K capacity

GET /v1/feed read path (served by a replica):
  SELECT e.* FROM episodes e
  WHERE e.source_id = ANY(:subscribedSourceIds)   ← fan-out-on-read
    AND (e.published_at, e.id) < (:cursorTime, :cursorId)
  ORDER BY e.published_at DESC, e.id DESC LIMIT 20

Capacity check against Section 4 — do the N-1 case, not the N case:
  Demand at a 30% cache hit rate = 50,000 × 0.70 = 35,000/sec.
  3 replicas × 17K = 51K looks fine, but losing ONE leaves
  2 × 17K = 34,000 < 35,000 — that design does not survive a
  single replica loss. So provision FOUR: 4 × 17K = 68K, and the
  degraded 3 × 17K = 51K still covers 35,000 with room.
  (The other way to close the gap is asserting a >=32% hit rate,
   but sizing availability off a cache hit rate you do not control
   is worse than paying for one more replica.)

KEY INVARIANT:
  Kafka decouples crawl bursts from storage writes.
  Episode Processor is idempotent (ON CONFLICT DO NOTHING) —
  safe to re-process if Kafka redelivers after a crash.
  Replica count is sized for N-1, not N: a capacity claim that
  assumes every replica is up is a capacity claim for a good day.
```

**Each box justified:**
- **RSS Crawler** — background; polls publisher URLs; publishes rather than inserting, so crawl rate is decoupled from DB write rate
- **Kafka** — absorbs the burst when many publishers update simultaneously, and is the replay log that fixes Stage 1 failure (b): a processor crash re-consumes from the last committed offset instead of losing episodes
- **Episode Processor** — parses RSS XML, normalizes to canonical schema, idempotent insert (safe on redelivery)
- **Read replicas × 4** — the only thing serving `GET /v1/feed`; this is what fixes Stage 1 failure (a). Four, not three, because the capacity check has to hold with one replica down (see the diagram)
- **Redis cache — keyed by `source_id`, not by `user_id`** — `source:{id}:page1` holds that source's newest episodes, 2-min TTL, invalidated by the `source_id` on the `new-episodes` topic. **Key it per user and the invalidation is not implementable:** the event is per *source*, so invalidating per-user caches means enumerating that source's ~40K subscribers on every publish — which is exactly the fan-out this stage exists to avoid. Keying the cache by the same dimension as the invalidation event makes invalidation a single `DEL`. It also foreshadows Stage 3, which is this idea taken all the way: cache per source, assemble per user.

> **If you keep a per-user feed cache anyway** (it is a legitimate choice for absorbing pull-to-refresh, where the same user re-requests page 1 within seconds), then say the limitation out loud: it can only be **TTL-expired, never event-invalidated**. New episodes appear up to 2 minutes late. That is inside the Section 3 "eventual consistency for feed" NFR, so it is defensible — but claiming *both* per-user keys *and* invalidation-on-publish is claiming something you cannot build.

```
WHY KAFKA EARNS ITS PLACE HERE (name this explicitly — it is the probe):
  Not "for scale" — 10K episodes/day is ~0.12 writes/sec, trivially
  small. Kafka is here for DURABILITY and REPLAY on the ingest path:
  the crawler's only job becomes "publish and forget," and any
  processor bug is fixed by resetting the consumer group offset and
  re-processing, because the insert is idempotent on (source_id,
  guid). Without it, a parser bug silently drops episodes forever.

BREAKING POINT: Stage 2 breaks at ~50M DAU / ~250K feed reads/sec,
  and there are two distinct ceilings — say which you hit first:
   (a) Read-replica fan-out stops paying. 250K reads/sec at ~17K per
       replica is ~15 replicas. The exhausted resource is NOT WAL
       throughput — do that arithmetic before you claim it. Episodes
       are 0.12/sec × ~1KB = ~120 B/sec; subscribes at 50M DAU are
       ~2,500/sec × ~100B = ~250 KB/sec. Total WAL ≈ 2 Mbit/sec, and
       15 replicas is 3.75 MB/sec = 30 Mbit/sec against a 10GbE
       NIC's 1,250 MB/sec. Off by ~330×. You also cannot accumulate
       two minutes of lag from a 250 KB/sec stream.
       The resource that IS exhausted is COST and CACHE REDUNDANCY.
       Each replica is a full private copy of the whole database —
       a 400M-row subscriptions table plus a large episodes table —
       provisioned to serve a working set that is ~99.99% identical
       for every user: 10K sources × ~50 recent episodes, a few
       hundred MB. Fifteen full copies of a multi-TB database to
       serve a few hundred MB of hot data is the ceiling, and each
       new replica needs a full base backup before it can serve, so
       the pool cannot scale reactively during a spike.
   (b) Per-query cost grows with subscription count, independent of
       throughput. At >200 avg subscriptions the feed query is 200+
       index scans plus a merge sort per page; the planner flips to a
       bitmap heap scan and P99 breaches 200ms even on idle replicas.
       Adding replicas cannot fix this — the work per query is the
       problem, not the number of queries.
  Observable symptom: storage and instance spend growing linearly
  with read rate for zero new data; read capacity only addable in
  ~30-minute base-backup increments while P99 is already breaching;
  feed P99 > 200ms with replica CPU below 50%; pg_stat_statements
  shows feed query mean time rising linearly with subscription count.
  Why Stage 3 is needed: stop handing every replica a private copy of
  shared data. Cache each source's recent episodes ONCE and assemble
  each user's feed at read time.

══════════════════════════════════════════════════════════════════
```

---

### Stage 3 — Per-Source Cache + Read-Time Merge (50M+ DAU)

> Stop giving every replica a private copy of shared data. Cache each *source's* recent episodes once, in Redis, and assemble each user's page by merging the ~40 short lists they subscribe to. The write side does not move at all; the read side does the work, cheaply.

**Switch trigger (state this number out loud):** read throughput past what a replica pool can serve without absurd copy count — roughly DAU > 50M / ~250K feed reads/sec. Note what is *not* in that trigger: subscription count. Breadth per user is a Stage 3 *ceiling*, not a Stage 3 *trigger* (see below).

**Before the diagram, the four steps in English:**
1. **On publish** — the Cache Updater consumes `new-episodes` and does one `ZADD` into `source:{source_id}:recent`, then trims to the newest 50. One write per episode. That is the whole write path.
2. **On read** — the Feed Service loads the user's ~40 `source_id`s (itself cached), then pipelines ~40 `ZREVRANGE` calls in a single round trip.
3. **Merge** — each of those 40 lists is already sorted by `published_at`, so a k-way merge over ~2,000 elements yields the top 20 without a sort.
4. **Hydrate** — fetch episode metadata for those 20 ids from a shared metadata cache, and `position_sec` from `episode_progress` keyed by `(user_id, episode_id)`. The progress lookup is the *only* genuinely per-user read in the path.

```
── Stage 3: Per-Source Cache + Read-Time Merge ────────────────────

 WRITE SIDE (stays trivial)      READ SIDE (does the work)
 ═════════════════════════       ═════════════════════════

 ┌──────────────────────────┐
 │ RSS Crawler              │
 └────────────┬─────────────┘
              ▼ 0.12 episodes/sec
 ┌──────────────────────────┐
 │ Kafka: "new-episodes"    │
 │ key = source_id          │
 └────────────┬─────────────┘
              ▼
 ┌────────────────────────────────────────┐
 │  Cache Updater consumer group          │
 │────────────────────────────────────────│
 │  ZADD           source:{id}:recent     │
 │  ZREMRANGEBYRANK → keep newest 50      │
 └────────────┬───────────────────────────┘
              │ 0.12 writes/sec
              │ FAN-OUT FACTOR = 1, not 40,000
              ▼
 ┌────────────────────────────────────────────────────────┐
 │  Redis: 10K keys  source:{source_id}:recent  (ZSET)    │
 │────────────────────────────────────────────────────────│
 │  score = published_at     member = episode_id          │
 │  10K sources × 50 episodes × ~200B = ~100 MB TOTAL     │
 │  ONE copy of each episode, shared by ALL subscribers   │
 └───────────────────────────┬────────────────────────────┘
                             │ pipelined ZREVRANGE
                             │ ~40 keys per request
                             ▼
              ┌──────────────────────────────┐   ┌─────────────┐
              │   Feed Service Pool          │◀──│ Mobile/Web  │
              │──────────────────────────────│   │GET /v1/feed │
              │ ① read sub list (cached)     │   └─────────────┘
              │ ② pipeline ~40 ZREVRANGE     │    250K+ reads/sec
              │ ③ k-way merge 40 sorted      │
              │   lists (~2,000) → top 20    │
              │ ④ hydrate metadata +         │
              │   progress_sec for those 20  │
              └───────────────┬──────────────┘
                              ▼
              ┌──────────────────────────────┐
              │ Postgres — sources,          │
              │ subscriptions, episode       │
              │ metadata, episode_progress   │
              │ (off the hot feed path)      │
              └──────────────────────────────┘

THE TRADE IN ONE LINE:
  Stage 2 gave every replica a private copy of shared data.
  Stage 3 keeps ONE copy of each source's recent episodes and merges
  ~40 short pre-sorted lists per request. Write cost does not move.

WHY NOT FAN-OUT-ON-WRITE HERE (this is the probe — get it right):
  The reflex answer is "precompute per-user feeds into Cassandra."
  It is wrong for THIS domain, and saying why scores better than
  reaching for it:
   1. No cheap majority. Twitter fans out on write because the median
      account has ~200 followers, and ByteByteGo's canonical
      news-feed chapter caps friends at 5,000 — push is cheap for
      ~99.9% of writes, and only celebrities need the pull carve-out.
      Here a SOURCE is a publisher, not a person: 400M subscriptions
      / 10K sources = 40-50K subscribers on the AVERAGE source. Every
      source is a celebrity. And the standard rescue — exempt the top
      0.1% — carves out TEN sources out of 10,000, which rescues
      nothing, because source 11 also has 40K subscribers.
   2. The data is shared, not per-user. A user_feed row is
      <user_id, episode_id, published_at> — zero per-user content.
      At 40K subscribers that is 40,000 IDENTICAL ID rows describing
      ONE episode. A Twitter timeline is a unique merge, so
      materializing it buys you something; a podcast feed is
      assembled from a shared pool of 10K source lists, so
      materializing per-user just duplicates shared data 40,000×.
      (Contrast episode_progress: 17K writes/sec of genuinely
      per-user content. THAT is the table that would earn Cassandra.)
   3. The bet does not pay. Fan-out-on-write pays N writes NOW to
      save reads LATER, and only wins if the rows are actually read.
      Most precomputed feeds are never opened — the registered base
      is far larger than DAU. The ceiling text this section replaced
      admitted exactly this: "most of it for users who never open
      the app." That is not a footnote, it is the refutation.
  Cost of the two at the 50M-DAU trigger (~200K subs per avg source):
      fan-out-on-write   ~24K writes/sec (72K at RF=3), ~2B rows/day,
                         ~70TB/year of derived data, Cassandra ring
      per-source cache   0.12 writes/sec, ~100MB, 3-5 Redis nodes

BE HONEST ABOUT THE READ COST (do not oversell this):
  Read-merge is not free. 50K reads/sec × ~40 sources = 2M Redis key
  lookups/sec, though pipelining collapses that into ~50K round
  trips. The k-way merge is ~2,000 elements down to 20 per request —
  microseconds of CPU on a stateless tier you scale horizontally.
  Cheaper by orders of magnitude than the write path it replaces.
  Cheaper, not free.

CEILING OF STAGE 3: ~500K-1M reads/sec, and it breaks on BREADTH,
  not volume:
   (a) Power users. A user subscribed to 1,000+ sources turns one
       request into 1,000 ZREVRANGEs plus a 50,000-element merge.
       The exhausted resource is per-request fan-out latency, not
       cluster throughput — P99 for those users detaches from the
       median while the cluster sits idle.
   (b) Redis read fan-out. Past ~1M reads/sec the ~40× key
       amplification saturates the cluster's network and its
       single-threaded command loop long before it saturates memory.
  Observable symptom: P99 feed latency tracking a user's subscription
  count rather than traffic; Redis CPU pinned on the shards holding
  the most-subscribed sources.
  Next moves, in order:
   1. INVERT Twitter's hybrid. Materialize a per-user feed ONLY for
      the few power users with >500 subscriptions, where merge cost
      finally exceeds fan-out cost. Twitter carves out the huge-
      FOLLOWER case; we carve out the huge-SUBSCRIPTION case. The
      expensive dimension is flipped, so the exception flips with it.
   2. Cache the assembled page per user with a ~2-minute TTL so a
      pull-to-refresh within a session skips the merge entirely.
      TTL-expired only, never event-invalidated — same limitation as
      the Stage 2 note above, for the same reason.
   3. Shard Redis by source_id hash so the most-subscribed sources
      spread across shards instead of pinning one.
   4. Add a hot-episode tier to the metadata hydration cache so a
      newly dropped episode from a huge show does not stampede one
      shard.

WHEN FAN-OUT-ON-WRITE WOULD BECOME CORRECT (say this out loud — it
  shows the rejection is conditional, not dogmatic):
  If ranking stops being reverse-chronological and becomes per-user
  ML scoring, the feed is no longer assemblable from shared
  per-source lists — each user's ordering is unique and expensive to
  compute, so materializing it buys something real. Section 2 rules
  that out for this session ("Assume: Reverse-chrono only"). Name the
  assumption you are leaning on; if the interviewer removes it, the
  answer changes and you should say so before they do.

KEY INVARIANT:
  Fan-out-on-write is only cheap when the fan-out factor has a small
  MAJORITY case. Cache on the dimension the data is SHARED on
  (source), not the dimension it is READ on (user) — otherwise you
  store one copy per reader of something every reader is reading.
```

---

## 🔬 Section 8 — Core Component Deep Dives

> **Delivery note.** Present these after the API and schema, as the natural continuation of Section 7's staging: Deep Dive 1 is the "when do I move to Stage 3?" argument, Deep Dive 2 is the correctness argument behind the feed cursor. Both are first-class deliverables, not fallback material.

### Deep Dive 1: Fan-out Strategy (Read vs Write vs Per-Source Cache)

**Why this is the most critical probe:**
The interviewer WILL say "now design this for 100M DAU." This is a test of whether you know the switch point — *and* whether you reach for the Twitter answer without checking that Twitter's precondition holds.

**The trap:** the reflex answer is "fan-out-on-write, like Twitter." That reflex is *wrong here*, and knowing why is the whole point of this deep dive. Fan-out-on-write is cheap only when the fan-out factor has a **small majority case**. Twitter's median account has ~200 followers; ByteByteGo's canonical news-feed chapter caps friends at **5,000**. Push is cheap for ~99.9% of writes, and celebrities get the pull carve-out.

**Our domain has no such majority.** A *source* is a publisher, not a person: 400M subscriptions across 10K sources is **~40–50K subscribers on the average source**. Every source is a celebrity. The standard rescue — exempt the top 0.1% — carves out **ten** sources out of 10,000 and rescues nothing, because source number 11 also has 40K subscribers.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Fan-out-on-read from Postgres (our choice at 10M DAU)** | No write amplification; subscribe = 1 row insert; inactive users cost exactly zero; ~2,000 rows read per page, independent of table size | Needs a replica per ~17K reads/sec, and every replica is a full private copy of data that is ~99.99% identical across users |
| **Per-source Redis cache + read-time merge (the scale answer, Stage 3)** | Fan-out factor = 1; ~100MB total for all 10K sources; one shared copy of each episode; a mega-source publish is a single `ZADD` | ~40 key lookups per request (pipelined into one round trip); merge cost grows with a user's subscription count |
| **Fan-out-on-write into `user_feed`** | Feed read is one partition slice, O(1) | At the 50M-DAU trigger: ~24K writes/sec (72K at RF=3), ~2B rows/day, ~70TB/year of derived data needing a Cassandra ring — and the rows carry zero per-user content, so it is shared data duplicated ~200,000× and mostly for users who never open the app |

**Decision: fan-out-on-read at 10M DAU; per-source cache + read-time merge above it. Not fan-out-on-write.**

At the stated target, 50K reads/sec with 40 avg subscriptions and the `(source_id, published_at DESC)` composite index is within read-replica capacity — four replicas, sized for N-1. The threshold to state explicitly is **read throughput** (~250K reads/sec / ~50M DAU), and what you move to is **Stage 3's per-source cache**, not a materialized `user_feed`.

The structural reason, in one line: **a Twitter timeline is a unique merge, so materializing it buys you something; a podcast feed is assembled from a shared pool of 10K source lists, so materializing per-user duplicates shared data.** A `user_feed` row is `<user_id, episode_id, published_at>` — zero per-user content. At 40K subscribers that is 40,000 identical ID rows describing one episode.

**Note which table actually is Cassandra-shaped.** Not `user_feed`. `episode_progress` — ~17K writes/sec (Section 4), genuinely per-user content, partitioned naturally by `user_id`, last-write-wins, append-mostly. If you want to say "Cassandra" in this round, say it about progress, not about the feed.

**When fan-out-on-write would become correct:** if the feed stopped being assemblable from shared per-source lists — i.e. per-user ML ranking, where each user's ordering is unique and expensive to compute. Section 2 explicitly rules that out this session ("Assume: Reverse-chrono only"). Say this out loud; it shows the rejection is conditional, not dogmatic.

```java
// Fan-out-on-read feed query (Stage 1-2 — our design at 10M DAU):
// SELECT e.*, COALESCE(ep.position_sec, 0) AS progress_sec
// FROM episodes e
// LEFT JOIN episode_progress ep
//   ON ep.episode_id = e.id AND ep.user_id = :userId
// WHERE e.source_id = ANY(:subscribedSourceIds)   // ~40 VALUES
//   AND (e.published_at, e.id) < (:cursorTime, :cursorId)
// ORDER BY e.published_at DESC, e.id DESC
// LIMIT :pageSize;

// Per-source cache + read-time merge (Stage 3 — the scale answer):
// List<String> sourceIds = subCache.get(userId);          // ~40
// Pipeline p = redis.pipelined();
// for (String s : sourceIds)
//     p.zrevrangeWithScores("source:" + s + ":recent", 0, 19);
// List<List<EpisodeRef>> lists = p.syncAndReturnAll();     // pre-sorted
// List<EpisodeRef> top20 = kWayMerge(lists, 20);           // ~2,000 -> 20
// return hydrate(top20, userId);   // metadata + progress_sec
//
// Write side, in full:  ZADD source:{id}:recent <published_at> <ep_id>
//                       ZREMRANGEBYRANK source:{id}:recent 0 -51
// One episode = one write. Fan-out factor 1, at any subscriber count.
```

### Deep Dive 2: Cursor Pagination on the Feed

**Why this is the most critical correctness probe:**
"Why cursor and not offset?" is the single most common follow-up on any feed question. Be ready to draw the failure scenario.

**Why offset breaks:**

```
Timeline:
  t=0: Client fetches page 1 → OFFSET 0 LIMIT 20 → gets episodes [1..20]
  t=1: New episode published (inserts at position 1 in the sorted feed)
  t=2: Client fetches page 2 → OFFSET 20 LIMIT 20
       What was episode #20 is now episode #21 (new ep shifted everything).
       OFFSET 20 now starts at old episode #21 → episode #20 is SKIPPED forever.

With cursor (published_at, id) < (lastSeen):
  The position is absolute in sorted order.
  New inserts above the cursor don't affect what comes after it.
```

**Cursor encoding:**

```java
// Cursor = base64("2026-08-09T12:00:00Z:uuid-of-last-episode")
// Two-part cursor: timestamp + episode ID as tiebreaker

// WHY the ID tiebreaker?
// A podcast publisher may batch-import 50 episodes with identical published_at.
// Without the ID tiebreaker, published_at alone is ambiguous across calls.
// WITH tiebreaker: (published_at, id) is globally unique and stable.

// Server decode:
// String[] parts = base64decode(cursor).split(":");
// Instant cursorTime = Instant.parse(parts[0]);
// UUID cursorId = UUID.fromString(parts[1]);
// → feed query: WHERE (published_at, id) < (cursorTime, cursorId)
```

---

## 🗄️ Section 9 — Data Model / SQL Schema

### Core Tables

```sql
-- Sources: podcast shows / RSS publishers
CREATE TABLE sources (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255)   NOT NULL,
    feed_url        TEXT           NOT NULL,
    category        VARCHAR(100),
    artwork_url     TEXT,
    episode_count   INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sources_feed_url UNIQUE (feed_url)
);

-- Episodes: content items published by a source
CREATE TABLE episodes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID           NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    title           VARCHAR(500)   NOT NULL,
    description     TEXT,
    audio_url       TEXT,
    duration_sec    INT,
    published_at    TIMESTAMP      NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Composite index: serves "episodes from source X, reverse-chrono"
-- and the fan-out-on-read feed query (IN clause over subscribed source_ids)
CREATE INDEX idx_episodes_source_published
    ON episodes (source_id, published_at DESC);

-- Subscriptions: user-to-source binding
CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL,
    source_id       UUID           NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    subscribed_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscriptions_user_source UNIQUE (user_id, source_id)
);

-- Index: "list all subscriptions for a user" (subscription list page)
CREATE INDEX idx_subscriptions_user
    ON subscriptions (user_id, subscribed_at DESC);

-- Index: "list all subscribers for a source" (subscriber count, fan-out-on-write)
CREATE INDEX idx_subscriptions_source
    ON subscriptions (source_id);

-- Episode progress: user's playback position per episode
CREATE TABLE episode_progress (
    user_id         UUID           NOT NULL,
    episode_id      UUID           NOT NULL REFERENCES episodes(id) ON DELETE CASCADE,
    position_sec    INT            NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, episode_id)
);
```

### Key Schema Decisions

**`UNIQUE(user_id, source_id)` on subscriptions:** This constraint is what produces the **409 Conflict** on duplicate subscribe. The application layer does not need to check before insert — the DB enforces it. The unique constraint also creates an implicit index that serves the "is this user subscribed to this source?" point-lookup.

**Composite index `(source_id, published_at DESC)` on episodes:** This is the most critical index. The fan-out-on-read feed query is:
```sql
SELECT e.*, ep.position_sec
FROM episodes e
LEFT JOIN episode_progress ep ON ep.episode_id = e.id AND ep.user_id = $user_id
WHERE e.source_id = ANY($subscribed_source_ids)
AND e.published_at < $cursor_timestamp
ORDER BY e.published_at DESC
LIMIT 20;
```
The index lets Postgres do an index range scan per source_id without a full table scan. Without it: full seq scan on a potentially billion-row episodes table.

**`PRIMARY KEY (user_id, episode_id)` on episode_progress:** Upsert semantics via `INSERT ... ON CONFLICT (user_id, episode_id) DO UPDATE SET position_sec = $1, updated_at = NOW()`. No separate update query needed; idempotent on retry.

**Fan-out-on-read vs. pre-materialization decision:** At 10M DAU × 40 subscriptions, the `IN` clause in the feed query holds **~40 `source_id` VALUES — not 400M rows.** Be precise about this, because getting it wrong is what drives people to the wrong architecture. One index lookup on `idx_subscriptions_user` returns the user's 40 `source_id`s; then `idx_episodes_source_published` does ~40 range scans of ~20 rows each, for **~2,000 rows read per feed page**. That cost is O(subscriptions per user) and is *independent of table size* — 400M is the cardinality of `subscriptions`, which no feed query ever scans.

**The switching threshold is therefore about read THROUGHPUT, not table size:** past ~250K feed reads/sec you would need ~15 full Postgres replicas, each a private copy of data that is ~99.99% identical for every user. The fix is Section 7's Stage 3 — cache each source's newest ~50 episodes once in Redis and merge ~40 pre-sorted lists at read time. It is **not** a pre-materialized `user_feed` table: with ~40K subscribers on the average source, that would write 40,000 identical `<user_id, episode_id, published_at>` rows per episode, all of which carry zero per-user content. (The separate limit, per user, is breadth: a 1,000-subscription user is 1,000 range scans, and that is what the Stage 3 ceiling addresses.)

---

## ⚠️ Section 10 — Trade-offs + Failure Modes

### Trade-off 1: Shared-Data Assembly (Read Merge) vs Fan-out-on-Write

- **Chose:** Fan-out-on-read at 10M DAU, graduating to a per-source Redis cache with a k-way merge at read time (Section 7 Stage 3) — explicitly **rejecting** fan-out-on-write into a materialized `user_feed`
- **Gain:** Fan-out factor is 1 instead of ~40K; ~100MB of cache for all 10K sources instead of billions of derived rows per day; inactive users cost exactly zero; a mega-show dropping a new episode costs one `ZADD`, so the head-of-line stall a huge-subscriber publish causes in a materialized design cannot happen
- **Lose:** ~40 key lookups per feed request (pipelined into one round trip) plus a ~2,000-element merge on the app tier; a user with 1,000+ subscriptions has a P99 that tracks their subscription count rather than system load
- **Failure mode if wrong:** [Technical]: two failures, and note they are different. (i) *Getting the query cost wrong:* people say "the `IN` clause spans 5B subscription rows at 50M DAU × 100 subs" and panic-migrate. It does not — the `IN` clause holds ~100 VALUES and reads ~2,000 rows, independent of cardinality. The real per-query limit is **breadth**: at 1,000 subscriptions the planner flips to a bitmap heap scan and P99 breaches 200ms on an otherwise idle replica, and no number of replicas fixes it. (ii) *Choosing fan-out-on-write:* at the 50M-DAU trigger it is ~24K writes/sec (72K at RF=3), ~2B rows/day, ~70TB/year — affordable in raw ops, but it is *silent waste*, because the rows carry no per-user content and most belong to users who never open the app. You would buy a Cassandra ring to precompute feeds nobody reads.
  [Streaming impact]: Backpressure from fan-out writes blocks Kafka consumer progress — `new-episodes` consumer lag grows unbounded and freshness slips. Tableflow sees the same shape when one high-fan-out table update triggers too many downstream materialized-view refreshes in a single transaction, stalling the Iceberg snapshot commit. Conversely, if Tableflow is consuming *feed reads* and the read path is slow, the Iceberg snapshot goes hours stale and downstream analytics reads stale data.

### Trade-off 2: Cursor Pagination vs Offset Pagination for Feed

- **Chose:** Cursor-based (opaque `published_at` timestamp encoded in base64)
- **Gain:** Stable pagination — new episodes inserting between pages don't cause skipped or duplicate items. Scales to arbitrary depths without `OFFSET N` full-scan cost.
- **Lose:** Users cannot jump to "page 50." Sequential scrolling only. Cursor token is opaque — clients cannot compute it.
- **Failure mode if wrong:** Offset pagination at depth: `OFFSET 10000 LIMIT 20` on a 50M-row episode table forces Postgres to scan and discard 10,000 rows. At 50K reads/sec each doing deep offset scans, table I/O saturates.
  [Streaming impact]: If feed metadata is cached in a Redis sorted set (score = `published_at`), offset-based pagination breaks when new items insert mid-set — `ZRANGE` with offset returns wrong results under concurrent writes from the episode ingestion pipeline.

### Trade-off 3: Subscription 409 vs Idempotent POST

- **Chose:** Return 409 Conflict on duplicate subscribe (not silent idempotency)
- **Gain:** Client knows it made an error. UI can show "already subscribed." No hidden state.
- **Lose:** Client must handle 409 explicitly. Cannot use an idempotency key to suppress it.
- **Failure mode if wrong:** If we silently returned 200 on duplicate subscribe, a client retry loop (due to a network hiccup on the first subscribe) would not know whether the first call succeeded. The client's retry would also succeed silently — but the subscription count in the UI might already show 1. The 409 makes the contract unambiguous: "your second call did nothing, the subscription already exists."
  [Streaming impact]: In a Kafka-based subscription event pipeline (subscribe events → topic → fan-out worker), silent 200 on duplicates would produce duplicate subscription events — the fan-out worker would try to re-subscribe the user and produce duplicate feed rows.

---

## 🌊 Section 11 — Confluent / Tableflow Angle

Two explicit connections to make in the interview:

**1. Episode ingestion as a Kafka pipeline.** The Feedly use case is exactly what Tableflow enables at the data layer. When a crawler fetches a new RSS episode and writes it to a Kafka topic, Tableflow can materialize that topic into an Iceberg table (`sources_episodes`) in real time. Downstream, a Spark or Flink job reads the Iceberg table to compute personalized feed rankings. This is the "Confluent makes your data streams queryable" value proposition made concrete.

**2. Subscription events as a compacted Kafka topic.** The subscribe/unsubscribe events can be modeled as a log-compacted topic (covered in `../../../SystemDesignConcepts/Core-Architecture/Service-Communication/60-kafka-internals.md`): key = `(user_id, source_id)`, value = `{subscribed: true/false}`. The latest value per key = the user's current subscription state. A Feed Service instance materializes that compacted topic into its local subscription-list cache — so step ① of the Stage 3 read path (fetch the user's ~40 `source_id`s) never touches Postgres. On unsubscribe (value = null tombstone), the source drops out of the user's list and its episodes stop appearing in the merge at the next read; **there are no derived feed rows to delete**, which is one more thing per-source caching buys you that fan-out-on-write does not. The compacted topic IS the subscription state store.

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1)
**Q: "Why 409 and not 400 for a duplicate subscribe?"**
> 400 Bad Request means the request itself is malformed — wrong format, missing fields, invalid types. The duplicate subscribe request is perfectly well-formed: valid `source_id`, correct content type, authenticated user. The problem is not the request — it's that it conflicts with the current state of the server (subscription already exists). That's 409 Conflict by definition.

### Deep Probe (Tier 2)
**Q: "How does your feed query scale if a user subscribes to 1,000 sources?"**
> At 1,000 sources per user, the `IN` clause expands to 1,000 `source_id` VALUES — note it is 1,000 values, not "1,000 × the table." Postgres uses the composite index `(source_id, published_at DESC)` for each, so that's 1,000 index range scans merged and sorted, ~20,000 rows. Query planning time itself becomes significant and the merge step is expensive. This is a **breadth** problem, and it is the one place where per-user materialization genuinely pays — which is why the Stage 3 next-move list **inverts Twitter's hybrid**: materialize a `user_feed` only for the small population of power users with >500 subscriptions, where merge cost finally exceeds fan-out cost. Twitter carves out the huge-FOLLOWER case; here the expensive dimension is huge-SUBSCRIPTION, so the exception flips with it. For the ordinary 40-subscription user, materializing is strictly worse: with ~40K subscribers on the average source, one episode becomes 40,000 identical `<user_id, episode_id, published_at>` rows carrying zero per-user content. The general answer at scale is the per-source Redis cache with a k-way merge at read time — fan-out factor 1.

### Cross-Concept Probe (Tier 3)
**Q: "If two devices simultaneously try to update the same episode's progress, what happens?"**
> The `PRIMARY KEY (user_id, episode_id)` constraint on `episode_progress` means two concurrent `UPSERT` operations serialize at the DB level — last-write-wins. This is acceptable for listen progress: if the user's phone writes position_sec=1842 and their laptop writes position_sec=1800 simultaneously, one will win. For progress tracking, last-write-wins is fine — the worst case is a slightly wrong resume position, not data corruption. To make it stricter, add an `If-Match` header with an ETag on the progress response and a conditional update (`UPDATE ... WHERE position_sec = $old_value`), returning 409 if the condition fails. But for listen progress, last-write-wins is the right trade-off.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1:** Using `DELETE /v1/sources/{id}/subscriptions` to unsubscribe → **Why it's wrong:** the Source resource is not what's being deleted; the Subscription is. Deleting a Source would mean removing the show entirely. The correct resource to delete is the Subscription, identified by its own ID. → **Say instead:** `DELETE /v1/subscriptions/{subscription_id}`.

- **Mistake 2:** Returning 404 when a user tries to unsubscribe from a source they're not subscribed to → **Why it's wrong:** 404 means "not found" — but from whose perspective? The subscription ID provided doesn't exist, which is correct. However, if the client sends a valid subscription ID that belongs to another user, 404 leaks information. Use 403 Forbidden if the subscription exists but belongs to a different user; 404 only if the subscription ID doesn't exist at all. → **Say instead:** "I check: does this subscription ID exist? If no → 404. Does it belong to the authenticated user? If no → 403."

- **Mistake 3:** Using offset pagination for the feed → **Why it's wrong:** episodes are continuously inserted. An offset of 20 at page 1 shifts by the time the user requests page 2 — they'll either miss or duplicate an episode at the seam. → **Say instead:** cursor-based pagination using `published_at` timestamp encoded as an opaque cursor.

- **Mistake 4:** Returning 201 for `PATCH /v1/episodes/{id}/progress` → **Why it's wrong:** 201 is for resource creation (POST). PATCH is an update; the progress record may already exist (we use upsert semantics). 200 OK is correct — the operation succeeded and we're returning the updated resource. → **Say instead:** 200 with the updated progress object.

- **Mistake 5:** Forgetting to index the feed query → **Why it's wrong:** Without `idx_episodes_source_published`, a feed read on a table with billions of episodes is a full table scan. The interviewer WILL ask "what indexes would you add?" → **Say instead:** composite index `(source_id, published_at DESC)` — serves both the fan-out-on-read feed query and the per-source episode list.

- **Mistake 6:** Saying "the `IN` clause spans 400M subscription rows" (or 5B, or 10B) → **Why it's wrong:** it confuses table cardinality with per-query cost. The `IN` clause holds one value per subscription the *user* has — ~40 of them. One index lookup gets that list, then ~40 range scans of ~20 rows each ≈ 2,000 rows read, and that number does not move when the table grows to 5B rows. Say the wrong version and you will talk yourself into pre-materialization for the wrong reason. → **Say instead:** "per-query cost is O(subscriptions per user), ~2,000 rows, independent of table size. What scales badly is the *number* of queries, and separately a single user's *breadth*."

- **Mistake 7:** Reaching for fan-out-on-write "like Twitter" the moment scale is mentioned → **Why it's wrong:** Twitter's precondition does not hold here. Push is cheap for Twitter because the median account has ~200 followers, so ~99.9% of writes are cheap and only celebrities need a carve-out. Here a source is a *publisher*: 400M subscriptions across 10K sources is ~40K subscribers on the **average** source. Every source is a celebrity, and exempting the top 0.1% carves out ten sources out of ten thousand. Worse, a `user_feed` row is `<user_id, episode_id, published_at>` — zero per-user content — so you would be duplicating shared data 40,000× per episode, mostly for users who never open the app. → **Say instead:** "cache per *source* — 10K ZSETs of the newest 50 episodes, ~100MB total — and merge ~40 pre-sorted lists at read time. Fan-out factor 1. It reverses only if ranking becomes per-user ML, where the feed stops being assemblable from shared lists."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/subscriptions` → 201 + `Location` header; duplicate → 409 not 400; unsubscribe wrong-owner → 403 not 404; `PATCH /v1/episodes/{id}/progress` → 200 not 201; cursor pagination on all list endpoints |
| **Trade-off Defense** | ✅ | Fan-out-on-read chosen at 10M DAU, graduating to a **per-source shared cache with read-time merge — explicitly rejecting fan-out-on-write**, because Twitter's precondition (a cheap majority of low-fan-out writes) fails here: 400M subscriptions across 10K sources is ~40K subscribers on the *average* source, and a `user_feed` row carries zero per-user content. Stated with the condition under which the rejection reverses (per-user ML ranking). Also: cursor vs offset justified by continuous episode inserts; 409 vs idempotent POST justified by client-state clarity; replica count sized for N-1, not N |
| **SQL / Data Modeling** | ✅ | `UNIQUE(user_id, source_id)` produces the 409; `PRIMARY KEY (user_id, episode_id)` on progress enables safe upsert; composite index `(source_id, published_at DESC)` serves the feed query; every FR has a corresponding index or PK |
| **Distributed Systems** | — | Type 1 round — not the primary axis; the scale answer is a per-source Redis ZSET tier with a k-way merge on a stateless Feed Service (fan-out factor 1, ~100MB for all 10K sources). If asked where a wide-column store belongs, name `episode_progress` (~17K writes/sec of genuinely per-user data, partitioned by `user_id`), **not** `user_feed` |
| **Pipeline Resilience** | ✅ | Kafka on the ingest path for durability + replay, not for throughput (0.12 episodes/sec); idempotent `ON CONFLICT DO NOTHING` insert survives redelivery; subscribe events as a compacted topic → Feed Service materializes each user's subscription list locally, unsubscribe = tombstone and there are **no derived feed rows to clean up**; Redis cache keyed on `source_id` so the per-source invalidation event is a single `DEL` rather than a 40K-subscriber enumeration |
| **Concurrency** | ✅ | Concurrent progress updates → last-write-wins via UPSERT; `UNIQUE` constraint serializes duplicate subscribe at DB level; optional `If-Match` for optimistic locking on progress if strict consistency is required |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "This is a Type 1 API + Data Model round. The core design is a `subscriptions` table with a `UNIQUE(user_id, source_id)` constraint — that constraint is what enforces the 409 Conflict on duplicate subscribe and eliminates any race condition at the application layer. The feed API (`GET /v1/feed`) uses cursor-based pagination keyed on `published_at` timestamp because episodes are continuously inserted — offset pagination would produce skips or duplicates at page boundaries. The feed query is fan-out-on-read at this scale (10M DAU × 40 subscriptions): a single `WHERE source_id = ANY(subscribed_ids) ORDER BY published_at DESC` query served by a composite index `(source_id, published_at DESC)` on the episodes table. Be precise about what that query costs — the `IN` clause holds ~40 VALUES, not 400M rows; it's one index lookup for the user's source list plus ~40 range scans of ~20 rows, about 2,000 rows read, independent of table size. The trade-off I'd defend hardest is rejecting fan-out-on-write, which is the reflex answer and is wrong for this domain: Twitter fans out on write because the median account has ~200 followers, so push is cheap for 99.9% of writes and only celebrities need the pull carve-out. Here a source is a publisher — 400M subscriptions across 10K sources means ~40K subscribers on the *average* source, so every source is a celebrity and the top-0.1% carve-out is ten sources out of ten thousand. More fundamentally, a `user_feed` row is `<user_id, episode_id, published_at>` with zero per-user content: a Twitter timeline is a unique merge worth materializing, while a podcast feed is assembled from a shared pool of 10K source lists, so materializing per-user duplicates shared data 40,000 times and mostly for users who never open the app. So past ~250K reads/sec I cache per *source* — 10K Redis ZSETs of the newest 50 episodes, ~100MB total — and merge ~40 pre-sorted lists at read time. That is 0.12 writes/sec instead of ~24K. It reverses only if ranking becomes per-user ML, where the feed stops being assemblable from shared lists. The one table that genuinely is Cassandra-shaped here is `episode_progress`, not `user_feed`: the player PATCHes every 30 seconds, which at 10M DAU with ~5% concurrently listening is ~17K writes/sec of real per-user data. For Confluent: the subscribe/unsubscribe event stream maps naturally to a log-compacted Kafka topic — the latest value per `(user_id, source_id)` key is the current subscription state, and Tableflow can materialize this into an Iceberg table for analytics."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Covers Podcast/Feedly API design (Type 1 round). 8 endpoints with full contract, status codes, and stories. SQL DDL for sources, episodes, subscriptions, episode_progress with all indexes. 3 trade-offs with two-layer failure modes. Confluent angle: compacted subscription topic + Tableflow episode materialization. |
| Aug 2026 | **Stage 3 architecture reversed — fan-out-on-write was the wrong answer for this domain, and six arithmetic errors fixed alongside it.** Same defect class as the aggregate-news-feed correction: the Twitter reflex applied where Twitter's precondition fails. (1) **Inverted celebrity logic.** Fan-out-on-write is cheap only when the fan-out factor has a small majority case — Twitter's median account has ~200 followers, ByteByteGo's canonical news-feed chapter caps friends at 5,000, so push is cheap for ~99.9% of writes with a celebrity carve-out. Here a *source is a publisher*: 400M subscriptions / 10K sources = **40–50K subscribers on the AVERAGE source**. Every source is a celebrity, and the old "next move 2: hybrid fan-out for the top ~0.1%" carves out **ten** sources out of 10,000 — it rescues nothing. (2) **Materializing shared data.** A `user_feed` row is `<user_id, episode_id, published_at>` with zero per-user content, so 40K subscribers means 40,000 identical ID rows describing ONE episode. (3) **The bet does not pay** — the old ceiling text admitted "most of it for users who never open the app," which is the refutation, not a footnote. Stage 3 replaced with **per-source Redis ZSET cache (`source:{id}:recent`, 10K keys × newest 50 ≈ 100MB) + k-way merge of ~40 pre-sorted lists at read time** — 0.12 writes/sec and FAN-OUT FACTOR 1, against ~24K writes/sec (72K at RF=3), ~2B rows/day and ~70TB/year of derived data at the 50M-DAU trigger. Added `WHY NOT FAN-OUT-ON-WRITE HERE`, an honest `BE HONEST ABOUT THE READ COST` block (50K reads/sec × 40 sources = 2M Redis lookups/sec pipelined into ~50K round trips — cheaper, not free), a ceiling that breaks on *breadth* not volume whose first next-move **inverts Twitter's hybrid** (materialize only for power users with >500 subscriptions, because the expensive dimension here is huge-SUBSCRIPTION not huge-FOLLOWER), and the reversal condition (per-user ML ranking, ruled out by the Section 2 "reverse-chrono only" assumption). Deep Dive 1, Trade-off 1, Section 4's fan-out visual, Section 5's 100M-DAU row, Section 9's schema-decision note, Section 11's compacted-topic mapping, Section 12's Tier-2 probe, Sections 14 and 15 all rewritten to match. **Six numeric/logical errors also corrected.** (a) *Stage 2 breaking point* claimed "the primary's WAL sender network saturates" past 10–15 replicas — not credible: at the 50M-DAU breaking point WAL is 0.12 episodes/sec × ~1KB = 120 B/sec plus ~2,500 subscribes/sec × ~100B ≈ 250 KB/sec ≈ 2 Mbit/sec; 15 replicas = 3.75 MB/sec = 30 Mbit/sec against a 10GbE NIC's 1,250 MB/sec, off by ~330×. You also cannot accumulate 2 minutes of lag from a 250 KB/sec stream, and "replication lag exceeds the 2-minute cache TTL" compared two unrelated mechanisms. Replaced with the real exhausted resource: **cost and cache-copy redundancy** — N full private copies of a 400M-row `subscriptions` table plus a huge `episodes` table, provisioned to serve a working set of 10K sources × ~50 recent episodes (a few hundred MB), with each new replica needing a full base backup before it can serve. (b) *"IN clause spans N billion rows"* appeared five times (Section 2, Section 4, Section 5, Section 9, Trade-off 1). The `IN` clause spans **~40–100 VALUES**, not billions of rows; per-query cost is O(subscriptions per user) — one index lookup plus ~40 range scans of ~20 rows ≈ 2,000 rows read, independent of table size. All five now match what Section 12's Tier-2 probe already said correctly. (c) *Replica sizing did not survive a replica loss:* 3 × 17K = 51K against demand of 50,000 × 0.70 = 35,000/sec means losing one replica leaves 2 × 17K = 34,000 < 35,000. Raised to **4 replicas (68K)** and the capacity check now does the N-1 case explicitly. (d) *Stage 1 breaking point* blamed "insert spikes" from bursty crawls, which the file itself refutes at the Stage 2 Kafka block (0.12 writes/sec; even compressed into one 60-second window that is 167 inserts/sec). Insert-spike argument deleted; the durability/replay argument stands alone. (e) *Stage 2's Redis cache* was described as keyed per USER but invalidated per SOURCE — not implementable without enumerating that source's ~40K subscribers on every publish, the exact fan-out the stage exists to avoid. Re-keyed on `source_id` (which also foreshadows Stage 3), with the per-user alternative kept as an explicitly TTL-only option. (f) *Section 4* said "~50 episodes per page = 2.5M rows/sec" against an API contract that specifies `limit=20`; corrected to **1M**. (4) **Added `episode_progress` write volume to Section 4**, which was absent from every capacity estimate: the player PATCHes every 30 seconds, so at 10M DAU with ~5% concurrently listening that is 500K × (1/30) ≈ **17K writes/sec — the dominant write load in the system**, and the one genuinely per-user high-volume write. It is what would actually warrant Cassandra here, unlike `user_feed`. |
| Aug 2026 | **Section 7 rebuilt as a full incremental HLD.** Removed the "no architecture diagram required / study context only" hedges from Sections 7 and 8 and the file header — this is now prepared as a full design question. Section 7 restructured into three stage blocks with per-stage ASCII diagrams: Stage 1 (single Postgres, crawler inserts inline, ~1M DAU / 5K feed reads/sec), Stage 2 (Kafka ingest + 3 read replicas + Redis, 10M DAU / 50K reads/sec — the stated target), Stage 3 (fan-out-on-write into a Cassandra `user_feed` table at >50M DAU or >200 avg subscriptions). Each transition now names two concrete failure causes, the exhausted resource, and an observable symptom; added `WHY KAFKA EARNS ITS PLACE HERE`, `WHY CASSANDRA EARNS ITS PLACE HERE`, and a `CEILING OF STAGE 3` with four ordered next moves (cap partitions, hybrid fan-out for mega-sources, lazy fan-out for inactive users, shard the fan-out consumer group). Stage numbers reconciled with Section 4 and Section 8 Deep Dive 1. |
