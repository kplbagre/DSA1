# Design a Feedly-like / Podcast Service API

> **Interview Type:** Type 1 — API + Data Model
> **Frequency:** ⭐⭐ Tier 2 — 2 confirmed in-window reports (May 2025 Podcast, Nov 2025 Feedly)
> **Key signal from research:** This IS the API design round — no architecture diagram expected. Entire evaluation is REST contract precision + SQL schema. "Any mistake highlighted as if the world has ended."
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

**Why it's hard to build at scale:** At 10M users each following 50+ sources, generating a personalized feed requires either **fan-out-on-read** (executing the join at query time — the DB computes which episodes a user should see by joining their subscription list against the episodes table on every feed page load; expensive at high cardinality) or **fan-out-on-write** (pre-computing each user's feed when a new episode is published, so the read is a single indexed lookup; expensive at write time when a popular source has millions of subscribers). The right choice depends entirely on the read:write ratio the interviewer gives you.

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
- If ~100M DAU, avg 100 subscriptions → 10B row intersection; need pre-materialized feed
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
- 50K feed reads/sec at ~50 episodes per page = 2.5M episode rows touched/sec at peak — this is the hot path
- Subscribe events: ~500/sec (manageable single-writer Postgres)
- Episode ingestion (from publisher RSS crawlers): low — perhaps 10K new episodes/day system-wide

**Key conclusion:** Feed reads are the bottleneck. A fan-out-on-read query (`SELECT episodes WHERE source_id IN (user's subscription set)`) over 400M subscription rows needs careful indexing or pre-materialization. Start with indexed fan-out-on-read; know the threshold at which you'd switch to fan-out-on-write.

### 🎨 Visual — Fan-out on Read vs Fan-out on Write

```
FAN-OUT ON READ  (default · ≤50M DAU · ≤200 avg subscriptions)
─────────────────────────────────────────────────────────────────
  GET /v1/feed
        │
        ▼
  ① SELECT source_id FROM subscriptions
       WHERE user_id = $user                   ← index scan (user)
        │
        ▼  (returns list of N source_ids)
  ② SELECT e.*, ep.position_sec
       FROM episodes e
       LEFT JOIN episode_progress ep ON ...
       WHERE e.source_id = ANY($source_ids)    ← index scan ×N
         AND e.published_at < $cursor
       ORDER BY published_at DESC LIMIT 20     ← merge + sort

  Cost: O(N subscriptions × index scans). Fine up to ~200 sources/user.

═══════════════════════════════════════════════════════════════════

FAN-OUT ON WRITE  (>50M DAU or >200 avg subscriptions)
─────────────────────────────────────────────────────────────────
  New episode published
        │
        ▼
  Kafka topic "new-episodes"
        │
        ▼
  Fan-out worker
        ├── for each subscriber of that source:
        │     INSERT INTO user_feed
        │       (user_id, episode_id, published_at)   ← 1 row/subscriber
        ▼
  GET /v1/feed
        │
        ▼
  SELECT * FROM user_feed
    WHERE user_id = $user
    ORDER BY published_at DESC LIMIT 20        ← 1 index scan, O(1)

  Cost: O(1) read. Write amplification: 1 episode × 1M subscribers
        = 1M INSERT ops. Use Cassandra for this write pattern.

KEY INVARIANT:
  Switch point ≈ 50M DAU or avg subscriptions > 200.
  Below threshold → read join is cheaper than write amplification.
  Above threshold → 1M INSERT ops once is cheaper than 1M join
  scans per second.
```

---

## 🔄 Section 5 — Requirements Variation Table

| If the interviewer says... | Architecture changes to... | Reasoning |
|---|---|---|
| "1M DAU, 10 subscriptions avg" | Fan-out-on-read with composite indexes | 10M subscription rows + indexed query = fine for this load |
| "100M DAU, 100 subscriptions avg" | Pre-materialized `user_feed` table; fan-out-on-write | 10B row intersection on every read; DB cannot serve it without pre-computation |
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

## 🏗️ Section 7 — Architecture Overview

> **Type 1 note — no architecture diagram is required in the actual interview.** This section is included as study context: understanding the system's data flow makes probe defense faster and more confident.

### 🎨 Visual — System Data Flow

```
USER FLOW (HTTP)                     INGESTION FLOW (background)
────────────────                     ───────────────────────────
[Mobile / Web]                       [RSS Crawler — scheduled job]
      |                                  |  polls each source feed URL
      | REST API                         |  every crawl_interval_sec
      v                                  v
[API Gateway / LB]              [Kafka: new-episodes topic]
      |                                  |  partitioned by source_id
      v                                  v
[Feed / Subscription Service]   [Episode Processor Service]
      |                            - parse RSS/Atom XML
      | reads/writes               - normalize → Episode schema
      v                            - INSERT ON CONFLICT DO NOTHING
[Postgres]                              |
  sources                              v
  subscriptions               [Postgres: episodes table]
  episodes
  episode_progress

GET /v1/feed read path:
  SELECT e.* FROM episodes e
  WHERE e.source_id = ANY(:subscribedSourceIds)   ← fan-out-on-read
    AND e.published_at < :cursor
  ORDER BY e.published_at DESC LIMIT 20

KEY INVARIANT:
  Kafka decouples crawl bursts from storage writes.
  Episode Processor is idempotent (ON CONFLICT DO NOTHING) —
  safe to re-process if Kafka redelivers after a crash.
```

**Each box justified:**
- **RSS Crawler** — background; polls publisher URLs; Kafka decouples crawl rate from DB write rate
- **Kafka** — absorbs burst when many publishers update simultaneously; episode processor can lag without blocking crawls
- **Episode Processor** — parses RSS XML, normalizes to canonical schema, idempotent insert
- **Postgres** — single store at 10M DAU; fan-out-on-read with composite index `(source_id, published_at DESC)` handles feed query

### Stage Transitions

```
════════════════════════════════════════════════════════
STAGE 1 — Direct crawl write, no Kafka (≤1M DAU)
════════════════════════════════════════════════════════
RSS Crawler writes directly to Postgres per crawl request.

BREAKING POINT: Stage 1 breaks at ~1M DAU / ~5K feed reads/sec
  because Postgres primary CPU saturates serving concurrent
  reads (feed queries) + writes (crawler inserts).
  Observable symptom: feed query P99 > 200ms; crawler writes queue.
  Why Stage 2 is needed: reads must be separated from writes.

════════════════════════════════════════════════════════
STAGE 2 — Kafka + read replicas (10M DAU — our target)
════════════════════════════════════════════════════════
Crawler → Kafka → Episode Processor → Postgres Primary (writes only)
                                    → Read Replicas × 3 (feed reads)
Redis feed cache: 2-min TTL, invalidated on new episode ingestion.

From Section 4: 50K feed reads/sec.
3 replicas × ~17K reads/sec each = 51K capacity.
Redis cache (30% hit rate) drops effective replica load to ~35K/sec.

BREAKING POINT: Stage 2 breaks at ~500K reads/sec
  because replica count becomes unmanageable (>50 for replication lag).
  Fix: fan-out-on-write — pre-materialize user_feed table in Cassandra.
  Trigger: avg subscriptions/user > 200 OR DAU > 50M.
  Write amplification: 1 episode × avg 50K subscribers = 50K Cassandra writes.
```

---

## 🔬 Section 8 — Core Component Deep Dives

> **Type 1 note — these won't be presented unprompted in the interview.** They answer the deep probes that WILL come after you present the API and schema.

### Deep Dive 1: Fan-out Strategy (Read vs Write)

**Why this is the most critical probe:**
The interviewer WILL say "now design this for 100M DAU." This is a test of whether you know the switch point.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Fan-out-on-read (our choice ≤50M DAU)** | No write amplification; subscribe = 1 row insert; zero cost for inactive users | Feed query = O(subscriptions × episodes); degrades past 200 avg subscriptions |
| **Fan-out-on-write (>50M DAU)** | Feed read = O(1) single index scan; 500K+ reads/sec | Write amplification: 1 episode × 1M subscribers = 1M writes; hot sources spike DB |

**Decision: Fan-out-on-read at 10M DAU.**

Because 50K reads/sec with 40 avg subscriptions per user and the `(source_id, published_at DESC)` composite index is within read replica capacity. State the threshold explicitly: switch to fan-out-on-write when DAU > 50M **or** avg subscriptions > 200.

```java
// Fan-out-on-read feed query (Phase 1 — our design):
// SELECT e.*, COALESCE(ep.position_sec, 0) AS progress_sec
// FROM episodes e
// LEFT JOIN episode_progress ep
//   ON ep.episode_id = e.id AND ep.user_id = :userId
// WHERE e.source_id = ANY(:subscribedSourceIds)
//   AND (e.published_at, e.id) < (:cursorTime, :cursorId)
// ORDER BY e.published_at DESC, e.id DESC
// LIMIT :pageSize;

// Fan-out-on-write feed query (Phase 2 — at switch threshold):
// SELECT * FROM user_feed
// WHERE user_id = :userId
//   AND (published_at, episode_id) < (:cursorTime, :cursorId)
// ORDER BY published_at DESC LIMIT :pageSize;
// (user_feed populated by background worker on every new episode insert)
```

---

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

**Fan-out-on-read vs. fan-out-on-write decision:** At 10M DAU × 40 subscriptions, the `IN` clause in the feed query spans ~400M rows of subscriptions and indexes into episodes. With the composite index, each source's episodes are fetched as an index scan — manageable. **The switching threshold:** when avg subscriptions per user exceeds ~200 or DAU exceeds 50M, the IN clause becomes too wide and Postgres query planning degrades. At that point, pre-materialize a `user_feed` table (fan-out-on-write on episode insert: for each new episode, insert one row per subscriber). Cassandra is better suited for that write pattern.

---

## ⚠️ Section 10 — Trade-offs + Failure Modes

### Trade-off 1: Fan-out-on-Read vs Fan-out-on-Write

- **Chose:** Fan-out-on-read (live join at query time)
- **Gain:** Simple write path — subscribe is one row insert, no propagation. Storage is minimal (no denormalized fan-out table).
- **Lose:** Feed read query is expensive as subscription count and episode volume grow. Latency degrades when a user follows 500 sources.
- **Failure mode if wrong:** [Technical]: at 50M DAU × 100 avg subscriptions, the `IN` clause spans 5B subscription rows. Postgres query planning degrades — P99 feed reads exceed 2 seconds. Read replicas add capacity but don't fix the fundamental join cost.
  [Streaming impact]: If Tableflow were consuming this feed data (materializing user feeds into Iceberg for analytics), a slow feed query means the Tableflow consumer accumulates lag — the Iceberg table snapshot is hours old, downstream analytics queries see stale data.

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

**2. Subscription events as a compacted Kafka topic.** The subscribe/unsubscribe events can be modeled as a log-compacted topic (covered in `../../../SystemDesignConcepts/Core-Architecture/Service-Communication/60-kafka-internals.md`): key = `(user_id, source_id)`, value = `{subscribed: true/false}`. The latest value per key = the user's current subscription state. A fan-out worker reads this compacted topic and maintains the materialized `user_feed` table. On unsubscribe (value = null tombstone), the worker deletes the user's feed rows for that source's episodes — the compacted topic IS the subscription state store.

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1)
**Q: "Why 409 and not 400 for a duplicate subscribe?"**
> 400 Bad Request means the request itself is malformed — wrong format, missing fields, invalid types. The duplicate subscribe request is perfectly well-formed: valid `source_id`, correct content type, authenticated user. The problem is not the request — it's that it conflicts with the current state of the server (subscription already exists). That's 409 Conflict by definition.

### Deep Probe (Tier 2)
**Q: "How does your feed query scale if a user subscribes to 1,000 sources?"**
> At 1,000 sources per user, the `IN` clause expands to 1,000 `source_id` values. Postgres will use the composite index `(source_id, published_at DESC)` for each value — that's 1,000 index range scans merged and sorted. At that cardinality, query planning time itself becomes significant, and the merge step is expensive. The fix is pre-materialized feeds: on each new episode insert, a background worker writes one row per subscriber to a `user_feed` table. The feed read becomes `SELECT * FROM user_feed WHERE user_id = ? ORDER BY published_at DESC LIMIT 20` — a single index scan. The trade-off: write amplification (1 episode × 1M subscribers = 1M write operations).

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

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/subscriptions` → 201 + `Location` header; duplicate → 409 not 400; unsubscribe wrong-owner → 403 not 404; `PATCH /v1/episodes/{id}/progress` → 200 not 201; cursor pagination on all list endpoints |
| **Trade-off Defense** | ✅ | Fan-out-on-read chosen at 10M DAU; switch point at ~50M DAU or 200+ avg subscriptions; cursor vs offset justified by continuous episode inserts; 409 vs idempotent POST justified by client-state clarity |
| **SQL / Data Modeling** | ✅ | `UNIQUE(user_id, source_id)` produces the 409; `PRIMARY KEY (user_id, episode_id)` on progress enables safe upsert; composite index `(source_id, published_at DESC)` serves the feed query; every FR has a corresponding index or PK |
| **Distributed Systems** | — | Type 1 round — not the primary axis; mention fan-out-on-write + Cassandra as the distributed write path for >50M DAU |
| **Pipeline Resilience** | ✅ | Subscribe events modeled as a Kafka compacted topic → fan-out worker maintains user_feed table; unsubscribe = tombstone → worker deletes feed rows; compacted topic = subscription state store |
| **Concurrency** | ✅ | Concurrent progress updates → last-write-wins via UPSERT; `UNIQUE` constraint serializes duplicate subscribe at DB level; optional `If-Match` for optimistic locking on progress if strict consistency is required |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "This is a Type 1 API + Data Model round. The core design is a `subscriptions` table with a `UNIQUE(user_id, source_id)` constraint — that constraint is what enforces the 409 Conflict on duplicate subscribe and eliminates any race condition at the application layer. The feed API (`GET /v1/feed`) uses cursor-based pagination keyed on `published_at` timestamp because episodes are continuously inserted — offset pagination would produce skips or duplicates at page boundaries. The feed query is fan-out-on-read at this scale (10M DAU × 40 subscriptions): a single `WHERE source_id = ANY(subscribed_ids) ORDER BY published_at DESC` query served by a composite index `(source_id, published_at DESC)` on the episodes table. The switching point to fan-out-on-write is ~50M DAU or >200 avg subscriptions per user, at which point write amplification (1 episode → N subscriber rows) is cheaper than the query cost. For Confluent: the subscribe/unsubscribe event stream maps naturally to a log-compacted Kafka topic — the latest value per `(user_id, source_id)` key is the current subscription state, and Tableflow can materialize this into an Iceberg table for analytics."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Covers Podcast/Feedly API design (Type 1 round). 8 endpoints with full contract, status codes, and stories. SQL DDL for sources, episodes, subscriptions, episode_progress with all indexes. 3 trade-offs with two-layer failure modes. Confluent angle: compacted subscription topic + Tableflow episode materialization. |
