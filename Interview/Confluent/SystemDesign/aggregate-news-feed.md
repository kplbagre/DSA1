# Aggregate News Feed — Solution Walkthrough

> **Interview type:** Type 2 — Full System Design
> **Confirmed reports:** 2+ (Jul 2025 PracHub — multiple sessions, same prompt)
> **Prompt variants:** "Design an Aggregate News Feed — ingest articles from publishers, deduplicate, rank" / "Design an RSS/news aggregation pipeline"
> **Bloom filter required:** ✅ Yes — dedup Bloom filter for seen-article fingerprints before store read
> **⚠️ Critical trap:** Candidates who led with ranking/personalization were dinged. **Start with ingestion reliability from unreliable publishers — always.**
> **Tableflow parallel:** Crawler → Kafka → Normalizer → Iceberg IS the Tableflow connector pipeline — not an analogy, the actual architecture.

---

## 🎯 What Is This System?

**In plain English:** A news feed aggregator pulls articles from thousands of publisher sources (RSS feeds, JSON APIs, HTML pages), normalizes them into a common format, deduplicates content that appears from multiple sources, and serves ranked feeds to users — all while reliably handling sources that are slow, intermittent, or return malformed data.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Feedly** | 40M+ users; crawls 40M sources; Kafka-based ingestion pipeline |
| **Google News** | Crawls billions of pages; article clustering and dedup at web scale |
| **Apple News** | Publisher-push RSS/Atom model; editorial ranking layer |
| **Flipboard** | Curated social + publisher feeds; in-app article normalization |
| **Confluent Tableflow** | Kafka topic → Iceberg table connector — architecturally IS this pipeline |

**Core user journey:** A user opens their news app; the feed shows a ranked list of articles from sources they follow, deduplicated so the same story doesn't appear three times from three outlets, with new articles appearing within minutes of publication.

**Why it's hard to build at scale:** Publisher sources are unreliable — they go down, return 429s (Too Many Requests), serve malformed RSS/XML, and sometimes publish the same article multiple times with different URLs. Reliable ingestion from unreliable upstreams, with exactly-once article storage (dedup), is the hard problem — not the ranking.

**Tableflow parallel:** The crawler → Kafka → Normalizer → Iceberg pipeline is architecturally identical to Tableflow's connector path: a Kafka source connector ingests data, a processor normalizes it into a table schema, and Tableflow materializes it as an Iceberg snapshot. Deduplication via content fingerprint = Iceberg's `MERGE INTO` upsert semantics.

---

## 🚀 Section 1 — The One-Sentence Opener

> "Before I start, let me ask a few clarifying questions — 'aggregate news feed' has very different complexity depending on whether you want a polling pipeline from RSS feeds or a real-time push ingestion system, and whether ranking means recency or ML-based personalization."

---

## 🔍 Section 2 — Clarifying Questions Script (Minutes 0–5)

**Q: "Do publishers push articles to us, or do we poll them on a schedule?"**
- Why ask: push vs pull determines the ingestion architecture entirely — push requires an inbound HTTPS receiver; pull requires a distributed crawler fleet with per-source scheduling
- If push (webhook/Atom PubSubHubbub) → simpler ingestion, but we can't control latency; publishers control the timing
- If polling → we control freshness intervals; more complex crawler infrastructure; need per-source crawl scheduling and rate limiting

**Q: "What's the target freshness — how quickly should a published article appear in user feeds?"**
- Why ask: freshness requirement drives polling interval, which drives crawler fleet size and Kafka throughput requirements
- If < 5 minutes → high-frequency polling (every minute per source) = 10K sources × 1 req/min = 167 crawler req/sec
- If < 1 hour → lower frequency; simpler scheduler; fewer crawler instances needed

**Q: "Do we need near-duplicate detection — same story from CNN and BBC — or just exact URL dedup?"**
- Why ask: exact URL dedup is a hash lookup; near-duplicate detection requires SimHash (content fingerprinting), which is more expensive but eliminates "same story, different source" clutter
- If exact only → URL-hash Bloom filter + Postgres unique index; fast and simple
- If near-dup → SimHash fingerprint stored per article; Hamming distance comparison at ingestion; approximately 5× more CPU at normalizer stage

**Q: "Is ranking based on recency only, or do we need personalization per user?"**
- Why ask: recency ranking is a sorted query on `published_at DESC` — trivial; personalization requires per-user signal storage, recommendation models, and precomputed scores — a different system
- If recency only → ranking = `ORDER BY published_at DESC` on subscribed sources; Section 7 stays clean
- If personalized → needs a separate ML ranking service; out of scope for 60 minutes; note it as a future phase

**Q: "How many publishers (sources) are we aggregating, and how many DAU?"**
- Why ask: publisher count drives crawler fleet sizing; DAU drives read scale
- Assume: 10,000 publishers, 100M DAU

**Q: "Do users need to search article content, or is browsing the feed the primary pattern?"**
- Why ask: full-text search requires Elasticsearch or Postgres `tsvector` indexes; feed browsing only requires index on `(source_id, published_at DESC)`
- If search → out of scope for today; note Elasticsearch as Phase 2
- If browsing only → simpler; our design

---

## 📋 Section 3 — Requirements

**Assumptions:** Polling-based crawl (pull), < 5-minute freshness, near-duplicate detection via SimHash, recency-based ranking only (no ML personalization this session), 10K sources, 100M DAU.

**Functional Requirements:**
- System crawls 10,000 publisher sources on a configurable schedule and ingests new articles
- Articles from multiple sources covering the same story are deduplicated (SimHash near-dup)
- Users see a ranked feed of articles from their subscribed sources (recency-ranked, newest first)
- Users can subscribe to / unsubscribe from publisher sources
- New articles appear in user feeds within 5 minutes of publication
- Out of scope: ML personalization, full-text search, user article history/read state

**Non-Functional Requirements:**
- Scale: 10,000 publishers, 100M DAU
- Write throughput: ~18 new articles/sec peak (see Section 4)
- Read throughput: ~70K feed reads/sec peak (see Section 4)
- Freshness latency: P95 < 5 minutes from publication to feed appearance
- Availability: 99.9% SLO for read path; crawl path tolerates up to 1-hour source outage
- Durability: no article loss after successful ingestion; dedup is idempotent (Kafka redelivery must not double-insert)

---

## 🗂️ Section 3.5 — Core Entities

| Entity | What it represents |
|---|---|
| **Source** | A publisher feed endpoint (RSS/Atom URL) — transactional; has crawl state (last_crawled_at, etag, last_modified) |
| **RawArticle** | The unprocessed response from a source crawl — ephemeral; lives in Kafka `raw-articles` topic only; discarded after normalization |
| **Article** | The normalized, deduplicated, stored article — immutable after first write; keyed by `content_hash` for dedup |
| **Subscription** | A user-to-source relationship — client-held state that drives feed query; append-only (delete is a soft-delete) |
| **FeedEntry** | A denormalized article record pre-ranked for a user's feed — ephemeral; either cached in Redis or computed on read |

### 🎨 Visual — Entity Relationships

```
┌──────────────────┐          ┌─────────────────────────┐
│     sources      │ 1 ─────N │        articles         │
│──────────────────│          │─────────────────────────│
│ id  PK           │          │ id  PK                  │
│ name             │          │ source_id  FK ──────────┘
│ feed_url  UNIQUE │          │ url_hash  UNIQUE (dedup) │
│ crawl_interval   │          │ content_hash (SimHash)   │
│ last_crawled_at  │          │ title                    │
│ http_etag        │          │ published_at             │
│ status           │          │ ingested_at              │
└────────┬─────────┘          └─────────────────────────┘
         │
         │ 1
         N
┌────────▼─────────┐
│  subscriptions   │
│──────────────────│
│ id  PK           │
│ user_id  FK      │
│ source_id  FK    │
│ subscribed_at    │
│ is_active        │ ← soft-delete; TRUE-only UNIQUE index
└──────────────────┘

NOTE: RawArticle and FeedEntry are NOT persisted as rows.
  RawArticle  → lives only in Kafka raw-articles topic (ephemeral)
  FeedEntry   → computed on read from articles + subscriptions
               (Stage 3 caches per SOURCE, never per user — see Deep Dive 3)

KEY INVARIANT:
  The feed query crosses all three persisted tables:
  subscriptions → source_id → articles.
  There is NO direct FK from subscriptions to articles.
  The join is always: subscriptions.source_id = articles.source_id.
```

---

## 🔢 Section 4 — Scale Estimation

**Write throughput (article ingestion):**
- Publishers: 10,000
- Articles per publisher per day: ~50 (average across news sites)
- Total new articles/day: 10,000 × 50 = 500,000 articles/day
- Average rate: 500,000 ÷ 86,400 ≈ 5.8 articles/sec
- **Peak (3×): ~18 articles/sec**

**Crawler request rate:**
- Freshness target: < 5 minutes → poll each source every 5 minutes
- Crawler requests: 10,000 sources ÷ 300 seconds = **33 crawler HTTP requests/sec**
- Most requests return HTTP 304 Not Modified (no new article): 33 × 0.8 = 26 "nothing new" responses + 7 responses containing new articles/sec average

**Read throughput (feed reads):**
- DAU: 100M
- Feed refreshes per user per day: ~20 (open app, pull-to-refresh)
- Total feed reads/day: 100M × 20 = 2B reads/day
- Average rate: 2B ÷ 86,400 ≈ 23,000 reads/sec
- **Peak (3×): ~70,000 reads/sec**

**Storage:**
- Per article: ~5KB (title, URL, summary ~1KB, full-text link, metadata)
- Articles/year: 500,000/day × 365 = 182.5M articles
- Hot storage (last 30 days): 15M articles × 5KB = **75GB** — fits in a single Postgres cluster
- Cold storage (full archive): 182.5M articles × 5KB = ~900GB/year → **Iceberg on S3** (object storage, queryable)
- SimHash fingerprints for active dedup window (30 days): 15M × 8 bytes = 120MB in Redis

**Key conclusions:**
- 18 articles/sec write throughput is trivially low — single Postgres handles it; the challenge is ingestion **reliability** and **dedup**, not raw write scale
- 70K reads/sec is the real bottleneck — requires read replicas or caching; a single Postgres primary handles ~5K-10K simple reads/sec
- The 120MB SimHash fingerprint set fits entirely in Redis — no disk lookups for dedup

---

## 🔄 Section 5 — Requirements Variation Table

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "1,000 sources, 10K DAU" | Single Postgres, no Kafka, simple cron crawler | 10K DAU × 20 = 200K reads/day ≈ 2 reads/sec; single DB handles it; Kafka is unwarranted overhead |
| "1M sources, 1B DAU" | Kafka with 100+ partitions, distributed crawler fleet (K8s jobs), Cassandra for article index, Iceberg for analytics | 1M sources ÷ 300s = 3,333 crawler req/sec; 1B × 20 reads/day = 231K reads/sec avg → ~694K peak (3×) — sharding is mandatory |
| "< 1-minute freshness" | Poll every 30 seconds → 10K ÷ 30 = 333 crawler req/sec; more crawler workers; Kafka ingestion latency must be < 10s | Higher polling frequency, not a different architecture; just more crawlers |
| "Exact dedup only (no near-dup)" | Drop SimHash; URL-hash Bloom filter + Postgres unique index is sufficient | Near-dup detection uses ~5× CPU at normalizer; if interviewers relax to URL-hash-only, simpler is better |
| "Personalized ranking" | Add per-user signal store (Cassandra), offline ML model training (Spark/Flink), online scoring service | Recency ranking is a Postgres `ORDER BY`; personalization is a separate ML system — explicitly call it out of scope and add to a future-phases note |
| "Publishers push via webhook" | Replace crawler fleet with an HTTPS receiver service; still use Kafka as buffer; dedup logic unchanged | Push reduces crawler complexity but introduces webhook reliability concern — publishers must retry on our receiver downtime |
| "Multi-region (EU, US, AP)" | Per-region crawler fleet (local to publisher geography); per-region Iceberg table; global article registry in DynamoDB for cross-region dedup | Cross-region dedup requires a global source of truth; within-region feed reads stay local |
| **"Publisher retracts an article after we ingested it"** | Simple path (most news aggregators): treat ingested articles as immutable — publisher retractions are not propagated; silently ignore 404/410 responses on recrawl. If required: add `status VARCHAR(32) DEFAULT 'active'` column to `articles`; crawler sets `status = 'retracted'` when recrawl returns 404/410 on `canonical_url`; feed query adds `WHERE articles.status = 'active'`. Add partial index `(source_id, published_at DESC) WHERE status = 'active'` to keep feed query fast. For downstream Kafka consumers: emit a tombstone (null-value message, key = `article_id`) to the articles compacted topic — consumers detect deletion and remove from their materialized views. | Two choices: (1) immutable-articles (simple, acceptable for most news feeds — retractions are rare); (2) propagated retractions (needed for legal/DMCA compliance). The Kafka tombstone pattern maps directly to Kafka log compaction: a null-value message = tombstone = "this key no longer exists." |

---

## ⭐ Section 6 — API Design

> **MANDATORY RULE:** API contract before HLD. The API defines the system's external surface — the architecture serves these contracts.

### 🧠 How to Derive These Endpoints

**"Users see a ranked feed from subscribed sources"** → retrieve → resource is `feed` (user-scoped, no ID in path — it's the user's feed) → `GET /v1/feed`.
What makes this non-obvious: `GET /v1/feed` should support `If-None-Match: {etag}` → `304 Not Modified` if the feed hasn't changed since the client's last fetch. This is the Confluent API precision point — most candidates return 200 with empty array; the correct response is 304 (the content is genuinely unchanged). Reduces unnecessary payload for mobile clients.

**"Users subscribe to a source"** → create relationship → resource is `subscription` → `POST /v1/subscriptions`.
Returns `201 Created` with the subscription. Idempotent via `ON CONFLICT DO NOTHING` — re-subscribing to a source already subscribed returns `200 OK` with existing subscription (not an error, not 409).

**"Register a new publisher source for crawling"** → admin operation → `POST /v1/sources`.
Returns **`202 Accepted`** — not `201 Created`. Why? The source is registered in the system but no articles have been crawled yet. The resource isn't fully created until the first crawl completes. `202` means "the request has been accepted but processing is not yet complete." If we returned 201, the client might immediately try to read articles from this source and get an empty feed — semantically confusing.

**"Users get their feed with pagination"** → cursor pagination, not offset.
Why cursor? Newly ingested articles shift the result set between pages — if a new article is ingested between page 1 and page 2, offset pagination returns it on both pages (duplicate) or skips one (gap). A cursor on `(published_at DESC, article_id DESC)` is stable because it encodes an absolute position in the sorted article stream.

### Core Endpoints

| Method | Path | Auth | Request Body / Headers | Response | Status Codes |
|---|---|---|---|---|---|
| `GET` | `/v1/feed` | Bearer token | Headers: `If-None-Match: {etag}`, `Accept-Language`; Query: `?cursor=...&limit=20` | `{"articles": [...], "next_cursor": "...", "etag": "abc123"}` | 200 OK, 304 Not Modified, 400 (bad cursor), 429 |
| `GET` | `/v1/articles/{id}` | Bearer token | — | `{"id": "...", "title": "...", "url": "...", "source": {...}, "published_at": "..."}` | 200 OK, 404 Not Found |
| `POST` | `/v1/subscriptions` | Bearer token | `{"source_id": "..."}` | `{"id": "...", "source_id": "...", "subscribed_at": "..."}` | 201 Created, 200 OK (already subscribed), 404 (source not found), 429 |
| `DELETE` | `/v1/subscriptions/{sourceId}` | Bearer token | — | (empty) | 204 No Content, 404 |
| `POST` | `/v1/sources` (admin) | Admin token | `{"name": "...", "feed_url": "...", "crawl_interval_sec": 300}` | `{"id": "...", "name": "...", "status": "pending_first_crawl"}` | 202 Accepted, 400 (invalid feed URL), 409 (source already registered) |

### 🔍 Endpoint Stories

**`GET /v1/feed`** — returns the user's personalized-by-subscription, recency-ranked article feed. The non-obvious detail: `If-None-Match: {etag}` + `304 Not Modified`. The ETag is a hash of the user's feed state (e.g., `SHA-256` of the most-recent article ID in their subscribed sources). If no new articles arrived since the client's last fetch, return `304` — no body, no bandwidth. The client renders the cached feed. At 70K reads/sec, a 30% hit rate on `304` saves 21K full-payload responses/sec. `400` if the cursor is malformed (not just "no results" — a corrupt cursor is a client error).

**`GET /v1/articles/{id}`** — fetch a single article by server-assigned UUID. Returns the normalized article record. `404` if the article was never ingested (bad ID) or if it was purged from hot storage (> 30 days old and moved to cold Iceberg storage — we could argue for `410 Gone` here if we distinguish "existed but purged" from "never existed," but for a news feed, the distinction matters less than for TempMail because content age is expected).

**`POST /v1/subscriptions`** — subscribe to a source. `200 OK` (not `409 Conflict`) if the source is already subscribed: subscribing to something already subscribed is idempotent and not an error. `404` if the `source_id` doesn't exist in our sources registry.

**`DELETE /v1/subscriptions/{sourceId}`** — unsubscribe. Soft-delete: the subscription row is marked inactive, not deleted. Why? For audit (when did user unsubscribe?) and for feed generation: if we use precomputed feed caches, we need to know to invalidate them.

**`POST /v1/sources`** (admin) — registers a new publisher source. Returns **`202 Accepted`** with `status: "pending_first_crawl"` because articles don't exist yet. If the admin immediately calls `GET /v1/feed` for a user subscribed to this source, they'll get an empty result — semantically correct, not an error. `409 Conflict` if a source with the same `feed_url` already exists.

---

## 🏗️ Section 7 — High-Level Architecture

> **Ingestion reliability comes first.** The system is only as good as its ability to handle unreliable publisher sources. HLD opens with the ingestion path.
>
> **Delivery note — build it up, don't draw the finished thing.** Present the Stage 1 single node first, then let the Section 4 numbers — 18 articles/sec, 70K peak reads/sec, 15M active fingerprints — force Stage 2 and Stage 3. The diagram below is the Stage 2 target design; the stage blocks that follow it are what you actually narrate, in order.

### 🎨 Visual — Ingestion Pipeline → Storage → Feed Read Path

```
INGESTION PATH — the hard part (publishers are unreliable)
══════════════════════════════════════════════════════════

 ┌──────────────────────────────────────────────────────┐
 │  10,000 Publisher Feeds (RSS / Atom / JSON)            │
 └────────────────────────┬─────────────────────────────┘
                          │ conditional HTTP:
                          │ If-Modified-Since / If-None-Match
                          ▼
 ┌──────────────────────────────────────────────────────┐
 │  Crawl Scheduler  (1 task per source)                 │
 │──────────────────────────────────────────────────────│
 │   304 Not Modified ─▶ no-op  (~80% of all crawls)     │
 │   429 / 503        ─▶ backoff 30s→30min, re-queue     │
 │   200 OK           ─▶ emit RawArticle downstream      │
 └────────────────────────┬─────────────────────────────┘
                          │ 33 req/sec in, ~7/sec carry content
                          ▼
 ┌──────────────────────────────────────────────────────┐
 │  Kafka: raw-articles · 10 partitions · key=source_id  │
 │  at-least-once → 10 Normalizers max parallelism       │
 └────────────────────────┬─────────────────────────────┘
                          ▼
 ┌──────────────────────────────────────────────────────┐
 │  Normalizer Service (10 instances)                    │
 │──────────────────────────────────────────────────────│
 │  parse feed → canonical Article                       │
 │  url_hash     = SHA-256(canonical_url)                │
 │  content_hash = SimHash(title + first 500 chars)      │
 │  ① ask Bloom: "seen this url_hash before?"            │
 └───────┬──────────────────────────────────────────────┘
         │ ① read-only probe
         ▼
 ┌──────────────────────┐
 │  Redis Bloom Filter  │   17.5MB · 15M fingerprints · 1% FPR
 │  (negative check)    │
 └───┬──────────────┬───┘
     │ ② says NO    │ ③ says MAYBE
     │ definitively │ (new OR false positive)
     │ new          │
     │              ▼
     │      ┌──────────────────────────────────────┐
     │      │ Confirm in Postgres:                  │
     │      │ SELECT 1 FROM articles                │
     │      │ WHERE url_hash = ?                    │
     │      └──────┬────────────────────────┬──────┘
     │             │ row found              │ no row
     │             │ → TRUE duplicate       │ → false
     │             ▼   discard, no write     │   positive
     │      ┌─────────────┐                 │
     │      │  discarded  │                 │
     │      └─────────────┘                 │
     └──────────────┬────────────────────────┘
                    ▼ ④ write (both surviving paths converge)
 ┌──────────────────────────────────────────────────────┐
 │  Postgres: articles                                   │
 │  INSERT ... ON CONFLICT (url_hash) DO NOTHING         │
 │  ← idempotent: THIS is the correctness guarantee,     │
 │    not the Bloom filter                               │
 └───────┬──────────────────────────────┬───────────────┘
         │ ⑤ add url_hash to Bloom       │ ⑥ publish source_id
         ▼                               ▼
 ┌──────────────────────┐    ┌──────────────────────────┐
 │  Redis Bloom Filter  │    │ Redis pub/sub: invalidate │
 │  (now also a writer) │    │ cached feeds of that      │
 │                      │    │ source's subscribers      │
 └──────────────────────┘    └──────────────────────────┘

FEED READ PATH — high throughput, low latency
══════════════════════════════════════════════

 ┌──────────────┐
 │    Client    │
 └──────┬───────┘
        │ GET /v1/feed   (If-None-Match: {etag})
        ▼
 ┌──────────────────────────────────────────────────────┐
 │  API Gateway → Feed Service Pool                      │
 └──────┬───────────────────────────────────────────────┘
        │ ① check Redis feed cache for user_id
        ▼
 ┌──────────────────────────────────────────────────────┐
 │  Redis Feed Cache   (user_id → {etag, items})         │
 └───┬──────────────────┬───────────────────────────┬───┘
     │ etag MATCHES     │ warm HIT                  │ MISS
     │ ▼                │ ▼                         │ or stale
  304 Not            200 + cached feed              ▼
  Modified          (no DB touched)      ┌────────────────────┐
  (no body)                              │ Build feed:         │
                                         │ articles JOIN       │
                                         │ subscriptions       │
                                         │ WHERE user_id = ?   │
                                         │ ORDER BY            │
                                         │  published_at DESC  │
                                         └─────────┬──────────┘
                                                   ▼
                              ┌──────────────────────────────┐
                              │  Postgres Read Replica Pool  │
                              │  7 replicas × ~10K reads/sec │
                              └─────────┬────────────────────┘
                                        │ populate cache, TTL 2min
                                        ▼
                                  200 + feed + ETag

KEY INVARIANTS:
  Ingestion is at-least-once, so dedup must be IDEMPOTENT. The
  ON CONFLICT DO NOTHING is the correctness guarantee; the Bloom
  filter is only a read-optimisation and is allowed to be wrong.
  Note the Bloom filter appears TWICE and the arrows differ: the
  Normalizer READS it (①), the post-insert step WRITES it (⑤).
  A Bloom "NO" is trustworthy (no false negatives) so it skips the
  confirming read entirely; a "MAYBE" always costs one Postgres read.
  Feed reads never touch the write primary — the replica pool absorbs
  70K reads/sec, and a 304 costs zero DB I/O.
```

```
══════════════════════════════════════════════════════
STAGE 1 — Single Node (handles up to ~10K reads/sec)
══════════════════════════════════════════════════════

 INGESTION PATH (write)              READ PATH (the bottleneck)
 ═══════════════════════             ══════════════════════════

 ┌───────────────────────┐           ┌───────────────────────┐
 │ 10K Publisher Feeds   │           │   Mobile / Web        │
 └───────────┬───────────┘           └───────────┬───────────┘
             │ poll every 300s                   │ GET /v1/feed
             ▼ 33 req/sec                        ▼
 ┌───────────────────────┐           ┌───────────────────────┐
 │ Crawl Scheduler       │           │  Feed Service         │
 │ (1 instance, serial)  │           │  (1 instance)         │
 └───────────┬───────────┘           └───────────┬───────────┘
             ▼                                   │
 ┌───────────────────────┐                       │ JOIN
 │ Kafka: raw-articles   │                       │ subscriptions
 │ 3 partitions          │                       │   × articles
 └───────────┬───────────┘                       │ ~20ms warm
             ▼                                   │
 ┌───────────────────────┐                       │
 │ Normalizer            │                       │
 │ (1 instance)          │                       │
 │  parse · SimHash      │                       │
 └───────────┬───────────┘                       │
             │ 18 articles/sec                   │ 10K reads/sec
             └───────────────┬───────────────────┘
                             ▼
 ┌──────────────────────────────────────────────────────────┐
 │            Postgres Primary  (ONE instance)               │
 │   sources · articles · subscriptions                      │
 │   ⚠ reads AND writes share the same CPU + buffer cache    │
 └──────────────────────────────────────────────────────────┘

Writes are trivial: 18 articles/sec. The problem is entirely on the
read side — 10K feed reads/sec against the same primary, and the two
paths contend for one buffer cache.

BREAKING POINT: Stage 1 breaks at ~10K reads/sec
  because the single Postgres primary is serving reads and writes from the
  same CPU and buffer cache, and the failure compounds on itself:
   (a) The feed query is a join (subscriptions × articles) that costs
       ~20ms warm. A 200-connection pool therefore caps throughput at
       200 ÷ 0.020s = 10K reads/sec. As CPU saturates the same query
       slows to ~50ms, and capacity COLLAPSES to 200 ÷ 0.050s = 4K/sec —
       so the ceiling moves down as you approach it.
   (b) The Normalizer's inserts contend with those reads for the same
       buffer cache, so ingestion latency rises exactly when read traffic
       peaks — the 5-minute freshness target slips during peak hours.
  Observable symptom: P99 feed query > 100ms; pg_stat_activity showing
  connections pinned at max with most in "active" state; article
  ingestion lag climbing in lockstep with the read-traffic curve.
  Why Stage 2 is needed: reads must be separated from writes.

══════════════════════════════════════════════════════
STAGE 2 — Read Replica Pool + Feed Cache
           (handles up to ~70K reads/sec)
══════════════════════════════════════════════════════

 INGESTION PATH (write)              READ PATH (now separated)
 ═══════════════════════             ═════════════════════════

 ┌───────────────────────┐           ┌───────────────────────┐
 │ 10K Publisher Feeds   │           │   Mobile / Web        │
 └───────────┬───────────┘           └───────────┬───────────┘
             ▼ 33 req/sec                        ▼ 70K reads/sec
 ┌───────────────────────┐           ┌───────────────────────┐
 │ Crawl Scheduler Pool  │           │  Feed Service Pool    │
 │ 10 workers            │           │  (N stateless)        │
 │ (1 per 1K sources)    │           └───────────┬───────────┘
 └───────────┬───────────┘                       │ ① try cache
             ▼                                   ▼
 ┌───────────────────────┐           ┌───────────────────────┐
 │ Kafka: raw-articles   │           │ Redis Feed Cache      │
 │ 10 partitions         │           │ TTL 2min · 30% hit    │
 └───────────┬───────────┘           └───────────┬───────────┘
             ▼                          ② miss    │  ~49K/sec
 ┌───────────────────────┐              reaches   │  survives
 │ Normalizer Pool       │              replicas  ▼
 │ 10 instances          │           ┌───────────────────────┐
 │ (1 per partition)     │           │  Read Replica Pool    │
 └───────────┬───────────┘           │ ┌────┐┌────┐   ┌────┐ │
             │ 18 articles/sec       │ │ R1 ││ R2 │···│ R7 │ │
             ▼                       │ └──▲─┘└──▲─┘   └──▲─┘ │
 ┌───────────────────────┐           └────┼─────┼────────┼───┘
 │ Postgres PRIMARY      │                │     │        │
 │ writes only           │────────────────┴─────┴────────┘
 │ ~18 articles/sec      │   ③ WAL streaming, fan-out to all 7
 └───────────┬───────────┘
             │ ④ on new article: invalidate affected user feeds
             └──────────────────▶ Redis (pub/sub)

From Section 4: 70K peak reads/sec. 7 replicas × ~10K/sec = 70K capacity.
The Redis cache at a 30% hit rate means only ~49K/sec actually reaches
the replicas — so the replica pool has headroom, which is why Stage 2
holds for the stated requirement.

BREAKING POINT: Stage 2 breaks at ~500K reads/sec because 10K reads/sec
  per replica means 50 read replicas. The exhausted resource is NOT WAL
  throughput — at 18 articles/sec the WAL is ~36KB/sec, which 50 replicas
  stream without noticing. It is that each replica is a FULL COPY of the
  database, provisioned solely to serve reads of data that is ~99.99%
  identical for every user. Fifty full copies is the cost and ops
  ceiling, and each new replica needs a full base backup before it can
  serve traffic, so the pool cannot scale reactively during a spike.
  Observable symptom: read capacity can only be added in ~30-minute
  base-backup increments while p99 feed latency is already breaching;
  storage spend grows linearly with read rate for zero new data.
  Why Stage 3 is needed: stop handing every replica a private copy of
  shared data. Cache the shared articles ONCE and assemble each user's
  feed at read time.

══════════════════════════════════════════════════════
STAGE 3 — Per-Source Cache + Read-Time Merge
           (handles 500K+ reads/sec)
══════════════════════════════════════════════════════

 WRITE SIDE (stays trivial)        READ SIDE (does the work)
 ══════════════════════════        ═════════════════════════

 ┌───────────────────────────┐
 │ Normalizer Pool           │
 └─────────────┬─────────────┘
               ▼ 18 articles/sec
 ┌───────────────────────────┐
 │ Kafka: new-articles       │
 │ key = source_id           │
 └─────────────┬─────────────┘
               ▼
 ┌────────────────────────────────────────┐
 │  Cache Updater consumer group          │
 │────────────────────────────────────────│
 │  ZADD          source:{id}:recent      │
 │  ZREMRANGEBYRANK → keep newest 50      │
 └─────────────┬──────────────────────────┘
               │ 18 writes/sec
               │ FAN-OUT FACTOR = 1, not 50,000
               ▼
 ┌──────────────────────────────────────────────────────────┐
 │  Redis: 10K keys  source:{source_id}:recent  (ZSET)      │
 │──────────────────────────────────────────────────────────│
 │  score = published_at    member = article_id             │
 │  10K sources × 50 articles × ~200B  =  ~100 MB TOTAL     │
 │  ONE copy of each article, shared by ALL its subscribers │
 └──────────────────────────┬───────────────────────────────┘
                            │ pipelined ZREVRANGE
                            │ ~50 keys per request
                            ▼
            ┌─────────────────────────────┐    ┌──────────────┐
            │   Feed Service Pool         │◀───│  Mobile/Web  │
            │─────────────────────────────│    │ GET /v1/feed │
            │  ① read sub list (cached)   │    └──────────────┘
            │  ② pipeline ~50 ZREVRANGE   │     500K+ reads/sec
            │  ③ k-way merge → top 20     │
            │  ④ hydrate from shared      │
            │     article cache           │
            └─────────────────────────────┘

THE TRADE IN ONE LINE:
  Stage 2 gave every replica a private copy of shared data.
  Stage 3 keeps ONE copy of each source's recent articles and merges
  ~50 short pre-sorted lists per request. Write cost does not move.

WHY NOT FAN-OUT-ON-WRITE HERE (this is the probe — get it right):
  The reflex answer is "precompute per-user feeds into Cassandra."
  It is wrong for THIS domain, and saying why scores better than
  reaching for it:
   1. No cheap majority. Twitter fans out on write because the median
      account has ~200 followers and ByteByteGo's canonical version
      caps friends at 5,000 — push is cheap for ~99.9% of writes and
      only celebrities need the pull carve-out. Here a SOURCE is a
      publisher: 10K sources against 100M DAU × ~20 subscriptions is
      ~50K-200K subscribers on the AVERAGE source. Every source is a
      celebrity, so there is no cheap majority for push to exploit.
   2. The data is shared, not per-user. A materialized user_feed row
      is <article_id, user_id> — no per-user content whatsoever. At
      200K subscribers that is 200K ID rows describing ONE article.
      Twitter materializes because each timeline is a unique merge;
      our feed is assembled from a shared pool of 10K sources.
   3. The bet does not pay. Fan-out-on-write pays N writes now to save
      reads later, and only wins if the rows get read. At 100M DAU
      against a much larger registered base, most precomputed feeds
      are never opened — ByteByteGo lists exactly this as a con of the
      push model.
  Cost of the two, side by side:
      fan-out-on-write   900K writes/sec (2.7M with RF=3), ~60 nodes
      per-source cache   18 writes/sec, ~100MB, 3-5 Redis nodes

BE HONEST ABOUT THE READ COST (do not oversell this):
  Read-merge is not free. 70K reads/sec × ~50 sources = ~3.5M Redis
  key lookups/sec, though pipelining collapses that into ~70K round
  trips. The k-way merge is ~2,500 elements down to 20 per request —
  microseconds of CPU on a stateless tier you scale horizontally.
  Roughly 1,000× cheaper than the write path it replaces, not zero.

CEILING OF STAGE 3: ~500K-1M reads/sec, and it breaks on breadth, not
  volume:
   (a) Power users. A user subscribed to 1,000+ sources turns one
       request into 1,000 ZREVRANGEs plus a 50,000-element merge. The
       exhausted resource is per-request fan-out latency, not cluster
       throughput — p99 for those users detaches from the median.
   (b) Redis read fan-out. Past ~1M reads/sec the ~50× key
       amplification saturates the cluster's network and single-thread
       command loop before it saturates memory.
  Observable symptom: p99 feed latency tracking subscription count
  rather than traffic; Redis CPU pinned on a subset of shards holding
  the most-subscribed sources.
  Next moves, in order:
   1. INVERT Twitter's hybrid. Materialize per-user feeds ONLY for the
      few power users (>500 subscriptions), where merge cost finally
      exceeds fan-out cost. Twitter carves out the huge-FOLLOWER case;
      we carve out the huge-SUBSCRIPTION case. The expensive dimension
      is flipped, so the exception flips with it.
   2. Cache the assembled feed per user with a ~2-minute TTL, so the
      repeat pull-to-refresh within a session skips the merge entirely.
   3. Shard Redis by source_id hash so the most-subscribed sources
      spread across shards instead of pinning one.
   4. Add a hot-item tier to the article hydration cache (ByteByteGo's
      Figure 8 split) so breaking-news articles do not stampede one
      shard during a spike.
```

---

## 🔬 Section 8 — Core Component Deep Dives

### Deep Dive 1: Ingestion Reliability — Crawling Unreliable Publishers

**Why this is the most critical component:**
Publishers are the system's only data source. If ingestion is unreliable — articles missed because a source was down, or double-ingested because Kafka redelivered — the user-facing feed is broken. The entire system depends on getting this right before thinking about ranking.

**Publisher failure modes:**

```
FAILURE MODE          RESPONSE
─────────────────────────────────────────────────────────
HTTP 304 Not Modified → no-op, update last_checked_at
HTTP 200, same ETag  → content-hash dedup catches it
HTTP 429 Too Many    → exponential backoff:
Requests               base 30s, max 30min; jitter ±20%
HTTP 503 Unavailable → same as 429
TCP timeout          → retry after 60s; mark source
                       as "degraded" after 5 failures
Malformed XML/RSS    → log parse error, skip this crawl;
                       do NOT kill the worker
Publisher deletes    → 410 Gone on crawl → mark source
feed URL             → "inactive", notify admin
```

**Idempotency requirement (at-least-once Kafka → idempotent insert):**

Kafka delivers messages at-least-once. If the Normalizer processes an article and then crashes before committing the Kafka offset, the same article will be re-delivered. The insert must be idempotent:

```sql
INSERT INTO articles (id, url_hash, title, canonical_url, published_at, source_id, content_hash)
VALUES (gen_random_uuid(), $url_hash, $title, $url, $published_at, $source_id, $content_hash)
ON CONFLICT (url_hash) DO NOTHING;
```

`ON CONFLICT (url_hash) DO NOTHING` — if the article was already ingested (url_hash exists), silently no-op. This is safe because the Normalizer always produces the same `url_hash` for the same URL, regardless of how many times Kafka delivers it.

**Conditional HTTP headers — cache the publisher's ETag:**

The crawler sends `If-Modified-Since` and `If-None-Match` headers using the publisher's last-returned `Last-Modified` and `ETag` values. This means 80% of crawl requests return `304 Not Modified` — zero body, ~100 bytes of headers. At 33 crawler req/sec, 80% 304 rate → actual content download rate = 7 req/sec = 7 × ~10KB = 70KB/sec inbound bandwidth. Trivial.

---

### Deep Dive 2: Deduplication — Bloom Filter + SimHash

**Why this is the most critical correctness component:**
Without dedup, the same story ("Prime Minister resigns") appearing on 50 news sites floods the user's feed. The dedup challenge has two layers: exact duplicates (same URL, re-published or re-crawled) and near-duplicates (same story, different sites, different URLs).

**Two-layer dedup strategy:**

```
Layer 1: EXACT dedup (URL-hash Bloom filter)
  → Bloom filter answers "have we seen this url_hash?"
  → False positives: rare, just means one extra Postgres read
  → False negatives: impossible (zero false negatives by design)
  → ~99% of re-crawls (304 Not Modified) never reach Postgres

Layer 2: NEAR-DUPLICATE dedup (SimHash content fingerprint)
  → SimHash (a locality-sensitive hash — a hash function that maps similar
     documents to similar hash values, so near-identical articles produce
     hashes that are close in Hamming distance) for title + first 500 chars
  → Two articles are near-duplicates if their SimHash values differ by
     ≤ 3 bits (Hamming distance ≤ 3)
  → Stored as 64-bit integer in articles table; indexed for similarity search
```

**Bloom filter sizing for URL-hash dedup:**

```
Active articles in dedup window (30 days):
  n = 500,000 articles/day × 30 days = 15,000,000

Desired FPR:
  p = 0.01 (1% — a false positive = one extra Postgres read, acceptable)

Bit array size:
  m = -n × ln(p) / (ln 2)²
  m = -15,000,000 × ln(0.01) / 0.480
  m = -15,000,000 × (-4.605) / 0.480
  m ≈ 143,906,250 bits ≈ 17.5MB

Number of hash functions:
  k = (m/n) × ln(2) = (9.594) × 0.693 ≈ 7

Total: 17.5MB for 1% FPR across 15M active article fingerprints.
Fits in a single Redis instance — trivially.
```

**What happens on a Bloom false positive (url_hash not in DB but Bloom says "maybe"):**
1. Normalizer sees Bloom = "maybe seen"
2. Queries Postgres: `SELECT 1 FROM articles WHERE url_hash = ? LIMIT 1`
3. Returns no row (it was a false positive)
4. Normalizer proceeds with `INSERT ... ON CONFLICT DO NOTHING`
5. Result: one extra Postgres read (~1ms). At 1% FPR on 18 articles/sec = 0.18 wasted reads/sec. Negligible.

**Bloom filter rebuild strategy:**
Rolling rebuild every 24 hours from the last 30 days of `url_hash` values in Postgres. The rebuild runs in the background; the old filter serves reads until atomically swapped. This purges stale entries (articles older than 30 days leave the active window).

**SimHash near-dup detection implementation sketch:**

```java
public long computeSimHash(String title, String bodyPrefix) {
    // Combined text for fingerprinting
    String text = title + " " + bodyPrefix.substring(0, Math.min(500, bodyPrefix.length()));
    String[] tokens = text.toLowerCase().split("\\s+");

    // v[i] accumulates weighted bit votes across all tokens
    int[] v = new int[64];

    for (String token : tokens) {
        long tokenHash = MurmurHash3.hash64(token);
        for (int i = 0; i < 64; i++) {
            // If bit i of token hash is 1 → vote +1; else vote -1
            if (((tokenHash >> i) & 1L) == 1L) {
                v[i]++;
            } else {
                v[i]--;
            }
        }
    }

    // SimHash fingerprint: bit i = 1 if v[i] > 0, else 0
    long simHash = 0;
    for (int i = 0; i < 64; i++) {
        if (v[i] > 0) {
            simHash |= (1L << i);
        }
    }
    return simHash;
}

// Near-dup check: two articles are near-duplicates if Hamming distance ≤ 3
public boolean isNearDuplicate(long hashA, long hashB) {
    return Long.bitCount(hashA ^ hashB) <= 3;
}
```

---

### Deep Dive 3: Feed Generation — Pull-on-Read vs Fan-out-on-Write

**Why this is the critical scale decision:**
At 100M DAU, how you generate the feed determines whether reads are cheap merges over shared data or expensive precomputation nobody reads.

**The trap:** the reflex answer is "fan-out-on-write, like Twitter." That reflex is *wrong here*, and knowing why is the whole point of this deep dive. Fan-out-on-write is cheap only when the fan-out factor has a **small majority case**. Twitter's median account has ~200 followers; ByteByteGo's canonical news-feed chapter caps friends at **5,000**. Push is cheap for ~99.9% of writes, and celebrities get the pull carve-out.

**Our domain has no such majority.** A *source* is a publisher, not a person: 10K sources against 100M DAU × ~20 subscriptions ≈ **50K–200K subscribers on the average source.** Every source is a celebrity. There is no cheap majority for push to exploit, so the standard hybrid rescues nothing.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Per-source cache + read merge (our choice)** | Fan-out factor = 1; ~100MB total; inactive users cost zero; a mega-source publish is one cache write | ~50 key lookups per request; merge cost grows with subscription count |
| **Fan-out-on-write into `user_feed`** | Read is one partition slice | 900K writes/sec (2.7M at RF=3), ~60 Cassandra nodes; one 10M-subscriber publish stalls the cluster ~11s; most rows never read |
| **Pull-on-read straight from Postgres (Stage 2)** | Simplest; no cache tier | Needs 50 full DB replicas at 500K reads/sec — 50 private copies of shared data |

**Decision for this design target:** per-source cache with read-time merge (Stage 3 in Section 7).

The structural reason, in one line: **a Twitter timeline is a unique merge, so materializing it buys you something; a news feed is assembled from a shared pool of 10K sources, so materializing per-user duplicates shared data.** A `user_feed` row is `<article_id, user_id>` — zero per-user content. At 200K subscribers that is 200K ID rows describing one article.

**When fan-out-on-write would become correct:** if the feed stopped being assemblable from shared per-source lists — i.e. per-user ML ranking, where each user's ordering is unique and expensive to compute. That is explicitly out of scope this session (Section 2 assumption: recency ranking only). Say this out loud; it shows the rejection is conditional, not dogmatic.

**Cache invalidation:** When a new article is ingested, the Normalizer emits the `source_id` to a Redis pub/sub channel. Feed Service instances subscribed to that channel invalidate the cached feed for all users subscribed to that source. This ensures users see new articles within seconds of ingestion, not 2 minutes (the TTL).

---

## 🗄️ Section 9 — Data Model

```sql
-- Publisher sources — one row per news site / RSS feed
CREATE TABLE sources (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    -- The RSS/Atom/JSON feed URL we crawl
    feed_url            VARCHAR(2048) NOT NULL,
    crawl_interval_sec  INTEGER      NOT NULL DEFAULT 300,
    last_crawled_at     TIMESTAMP,
    -- HTTP conditional crawl state (ETag and Last-Modified for If-None-Match / If-Modified-Since)
    http_etag           VARCHAR(512),
    http_last_modified  VARCHAR(128),
    -- 'active' | 'degraded' | 'inactive'
    status              VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- One unique feed URL per source
CREATE UNIQUE INDEX idx_sources_feed_url ON sources (feed_url);
-- Crawl scheduler: "which sources are due for a crawl?"
CREATE INDEX idx_sources_next_crawl ON sources (status, last_crawled_at ASC)
    WHERE status = 'active';

-- Normalized, deduplicated articles
CREATE TABLE articles (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID        NOT NULL REFERENCES sources(id),
    -- SHA-256 of canonical_url — dedup key, covers exact duplicates
    url_hash        CHAR(64)    NOT NULL,
    canonical_url   VARCHAR(2048) NOT NULL,
    title           VARCHAR(512) NOT NULL,
    summary         TEXT,
    -- SimHash (locality-sensitive hash) fingerprint for near-duplicate detection
    content_hash    BIGINT      NOT NULL,
    published_at    TIMESTAMP   NOT NULL,
    ingested_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Primary dedup index — ON CONFLICT (url_hash) DO NOTHING
CREATE UNIQUE INDEX idx_articles_url_hash ON articles (url_hash);
-- Feed query index: user's feed = articles from their sources, newest first
CREATE INDEX idx_articles_feed ON articles (source_id, published_at DESC);
-- Cursor pagination: stable position even with concurrent inserts
CREATE INDEX idx_articles_cursor ON articles (source_id, published_at DESC, id DESC);
-- Near-dup lookup: articles with similar SimHash values
-- Note: full Hamming-distance search requires application-side comparison
-- or a specialized index (pg_similarity extension) — not a standard B-tree
CREATE INDEX idx_articles_content_hash ON articles (content_hash);

-- User-to-source subscriptions
CREATE TABLE subscriptions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    source_id       UUID        NOT NULL REFERENCES sources(id),
    subscribed_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- Soft-delete: unsubscribe sets is_active = false, not a DELETE
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Dedup: one active subscription per user per source
CREATE UNIQUE INDEX idx_subscriptions_user_source
    ON subscriptions (user_id, source_id)
    WHERE is_active = TRUE;
-- Feed generation: "which sources does this user follow?"
CREATE INDEX idx_subscriptions_user ON subscriptions (user_id)
    WHERE is_active = TRUE;
```

**Key Schema Decisions:**
- **`url_hash CHAR(64)` unique index:** This is the dedup key for exact duplicates. `ON CONFLICT (url_hash) DO NOTHING` makes the insert idempotent — safe for Kafka at-least-once redelivery.
- **`content_hash BIGINT` for SimHash:** Stored as a 64-bit integer. Near-dup detection requires Hamming distance ≤ 3 bits — computed at application layer by XOR-then-popcount. B-tree index on `content_hash` supports exact-match lookup; approximate similarity search over 15M rows requires a more specialized approach (LSH indexing or batch offline dedup job).
- **Soft-delete on subscriptions (`is_active = FALSE`):** Needed for feed cache invalidation audit trail and to avoid re-subscribing being treated as a new row.
- **Conditional index on `idx_sources_next_crawl`** (`WHERE status = 'active'`): Crawl scheduler only queries active sources; partial index keeps it small and fast even as inactive sources accumulate.

---

## ⚠️ Section 10 — Trade-offs + Failure Modes

### Trade-off 1: At-Least-Once Ingestion vs Exactly-Once

- **Chose:** At-least-once Kafka delivery + idempotent `ON CONFLICT DO NOTHING` insert (effectively exactly-once at storage layer)
- **Gain:** Kafka at-least-once is the default and requires no distributed transaction coordinator; dedup logic in Postgres handles redelivery silently
- **Lose:** If two different articles from different sources happen to have the same `url_hash` (SHA-256 collision — astronomically unlikely), one is silently dropped. Acceptable trade-off.
- **Failure mode if wrong:**
  - [Technical]: If we use Kafka's `enable.idempotence=true` + transactional producer for exactly-once but forget the Postgres upsert: Kafka guarantees the producer doesn't duplicate, but a Normalizer crash after producing but before committing the DB transaction still causes re-processing from the last committed Kafka offset → duplicate insert without `ON CONFLICT`.
  - [Streaming impact]: Tableflow uses at-least-once Kafka delivery with Iceberg's `MERGE INTO` for dedup (the same pattern). If a Tableflow pipeline loses the at-least-once + idempotent-sink guarantee, Iceberg tables accumulate duplicate rows — downstream analytics over-counts article impressions in A/B test results.

### Trade-off 2: Per-Source Shared Cache vs Fan-out-on-Write for Feed Generation

- **Chose:** Per-source recent-article cache (Redis ZSET, newest 50 per source) with k-way merge at read time — explicitly **rejecting** fan-out-on-write
- **Gain:** Fan-out factor is 1 instead of 50K–200K; ~100MB of cache total instead of ~2TB/day of derived rows; inactive users cost exactly zero; a mega-source publishing costs one `ZADD`, so the head-of-line stall that a 10M-subscriber publish causes in a materialized design cannot happen
- **Lose:** ~50 key lookups per feed request (pipelined into one round trip) plus a ~2,500-element merge on the app tier; a user with 1,000+ subscriptions has p99 latency that tracks their subscription count rather than system load
- **Failure mode if wrong:**
  - [Technical]: If we chose fan-out-on-write: 18 articles/sec × ~50K avg subscribers = 900K feed writes/sec, 2.7M at RF=3 — Postgres (~5K–10K writes/sec) is exceeded by two orders of magnitude, and even Cassandra needs ~60 nodes. Worse, the failure is *silent waste*: most of those rows are for users who never open the app, so we would be buying 60 nodes to precompute feeds nobody reads.
  - [Streaming impact]: Backpressure from fan-out writes blocks Kafka consumer progress — `raw-articles` topic consumer lag grows unbounded. Tableflow observes this when a high-fan-out table update triggers too many downstream materialized view refreshes in a single transaction, stalling the Iceberg snapshot commit.

### Trade-off 3: SimHash Near-Dup Detection vs URL-Only Dedup

- **Chose:** SimHash content fingerprinting (Hamming distance ≤ 3) for near-duplicate detection
- **Gain:** Same story from CNN and BBC appears only once in the user's feed; substantially better UX for breaking news where 50 outlets publish the same story
- **Lose:** SimHash computation adds ~2ms CPU per article at the Normalizer; false dup suppression (Hamming ≤ 3 on genuinely different articles with overlapping phrases) loses some articles — P(false dup) is low but non-zero
- **Failure mode if wrong:**
  - [Technical]: If SimHash threshold is set too aggressively (Hamming ≤ 10 instead of ≤ 3): articles sharing a topic but not identical (two different editorials about the same election) are collapsed into one. Users complain about missing articles; hard to debug because no error log — the article silently wasn't inserted.
  - [Streaming impact]: Tableflow deduplication uses content fingerprints for Iceberg `MERGE INTO` (update matching row vs insert new row). An overly aggressive fingerprint threshold would suppress new Iceberg row versions — the table appears not to update when underlying content changes slightly, breaking any analytics running time-travel queries (`SELECT * AS OF TIMESTAMP T`).

---

## 🌊 Section 11 — Confluent/Tableflow Angle

This is the strongest question in the Confluent bank for architectural domain fit. The ingestion pipeline is not an analogy to Tableflow — it IS the Tableflow connector path.

**The core mapping:**

```
This System                           Tableflow/Kafka Architecture
───────────────────────────────────   ──────────────────────────────────────
Crawl Scheduler emits RawArticle     Kafka Source Connector pulls from
to raw-articles Kafka topic          external system, emits to Kafka topic

Normalizer Service consumes          Tableflow processor consumes Kafka
from Kafka, transforms               topic, transforms via SMT
(RSS → Article schema)               (Schema Message Transformation)

Normalized Article written to        Tableflow writes to Iceberg table
Postgres (hot) + Iceberg (cold)      as a new Iceberg snapshot

Content-hash dedup via               Iceberg MERGE INTO: if article_id
ON CONFLICT (url_hash) DO NOTHING    exists, update; else insert (upsert)

30-day hot window, Iceberg cold      Kafka retention.ms = hot window;
archive                              Iceberg snapshots = queryable archive

Feed sorted by published_at DESC     Iceberg time-travel: SELECT * AS OF
                                     TIMESTAMP gives articles as of any
                                     point in time
```

**The specific Confluent insight to voice:**

> "Tableflow's architecture is this news feed pipeline made generic. A Tableflow connector is the crawler: it ingests from an external source into a Kafka topic. The Tableflow processor is the Normalizer: it transforms and normalizes. The Iceberg table is our article store: immutable snapshots, append-only, queryable by time. The dedup problem — `ON CONFLICT DO NOTHING` in Postgres — is Iceberg's `MERGE INTO` with `WHEN MATCHED THEN UPDATE WHEN NOT MATCHED THEN INSERT`. They're the same idempotent upsert pattern. What I'm designing here is a domain-specific Tableflow pipeline."

**The Kafka retention parallel:**

> "Hot article storage (30-day Postgres) maps directly to Kafka topic retention: `retention.ms = 30 days × 86,400,000 ms`. Articles older than 30 days 'expire' to Iceberg cold storage, exactly as Kafka log segments expire after the retention window and are deleted from the broker. The Iceberg cold store is the equivalent of Kafka's long-term archive tier (Confluent Tiered Storage) — data is gone from the fast path but still queryable."

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "How do you handle a publisher that goes down for 2 hours?"**
> The crawl scheduler marks the source as "degraded" after 5 consecutive failures. Subsequent crawl attempts use exponential backoff: 30s → 60s → 120s → 240s → up to 30 minutes per retry. When the source comes back up, the crawler fetches normally — but we may have missed articles published during the outage (publishers don't guarantee article history in their RSS feeds). We log a `source_recovery_event` and alert an operator if the source was down > 1 hour. The user feed gracefully shows "no new articles from [source] for 2 hours" implicitly — no error, just stale data. This is an acceptable trade-off for a news feed.

**Q: "Why does `POST /v1/sources` return 202 and not 201?"**
> `201 Created` means the resource is fully created and immediately accessible. If we returned 201, a client that immediately called `GET /v1/feed` for a user subscribed to this source would expect articles — but the first crawl hasn't run yet. `202 Accepted` means "I've accepted your request; processing is in progress." The response body includes `status: "pending_first_crawl"` so the client knows why there are no articles yet. Returning 201 here would be semantically misleading and would cause clients to assume immediate article availability.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Walk me through exactly what happens when two Kafka Normalizer instances process the same article simultaneously after a consumer rebalance."**
> During a Kafka consumer group rebalance (e.g., one Normalizer instance crashes and its partitions are reassigned), the newly assigned instance starts from the last committed offset — which may have already been processed by the crashed instance before it failed but after its last commit. Both instances will compute the same `url_hash` for the same article. Both will execute `INSERT ... ON CONFLICT (url_hash) DO NOTHING`. Postgres serializes these at the row level: one INSERT succeeds, the other silently no-ops. Net result: one article row, correct. The idempotency guarantee is at the storage layer, not in Kafka — this is intentional. The Kafka at-least-once + idempotent sink pattern is exactly how Tableflow handles source connector rebalancing.

**Q: "How do you detect that two articles with different URLs are the same story?"**
> SimHash. I compute `SimHash(title + first 500 chars of body)` — a 64-bit fingerprint where similar documents produce similar hashes. Two articles with Hamming distance ≤ 3 bits are flagged as near-duplicates: only the first one ingested is stored; subsequent ones are dropped. I size the threshold at 3 bits because: 0 bits = exact text match (too strict), 10+ bits = too loose (articles sharing only a few words match). 3 bits allows for minor editorial differences (publication time in the lede, slightly different datelines) while catching genuine same-story duplicates. The false-dup rate is about 0.3% at this threshold — three articles out of 1,000 incorrectly collapsed, which is acceptable for a news feed.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "You've designed this as a polling-based crawler. What would change if Confluent was building this as a product feature — a managed news ingestion connector — running on top of their own Kafka infrastructure?"**
> The crawler becomes a managed Kafka Source Connector running in Confluent Cloud. Instead of running my own scheduler, each publisher source becomes a connector configuration: `{"feed_url": "...", "poll_interval_sec": 300, "connector.class": "RSSSourceConnector"}`. The output of each connector is a Kafka topic (`raw-articles.{source_id}`). The Normalizer becomes a Kafka Streams application (or Flink job on Confluent) doing the RSS parsing, SimHash computation, and dedup — exactly what Tableflow's processor does with Schema Message Transformations. The Iceberg cold archive is Tableflow's output: Confluent's own Tableflow connector materializes the normalized articles topic into an Iceberg table on S3. In this framing, I haven't designed a custom pipeline — I've described Confluent's product roadmap.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1: Starting with ranking and personalization** → **Why it's wrong:** Research explicitly dinged candidates who began with ML ranking. The hard problem is reliable ingestion from unreliable publishers, not ranking. → **What to say instead:** "I'll start with ingestion reliability — making sure we reliably get articles from unreliable publisher sources — then cover ranking. Ranking at its core is just `ORDER BY published_at DESC` for recency; personalization is a separate ML system I'll flag as Phase 2."

- **Mistake 2: Returning 201 for `POST /v1/sources`** → **Why it's wrong:** 201 means the resource is immediately accessible. The source is registered but no articles exist yet — the first crawl hasn't run. → **What to say instead:** "`202 Accepted` with `status: 'pending_first_crawl'` in the response body. The resource registration succeeded but the async processing (crawling) hasn't completed."

- **Mistake 3: Using offset pagination for the feed** → **Why it's wrong:** New articles are continuously inserted. Offset shifts between pages: an article ingested between page 1 and page 2 causes the page 2 query to either skip an article (it shifted into page 1's range) or repeat one. → **What to say instead:** "Cursor pagination on `(published_at DESC, article_id DESC)`. The cursor encodes an absolute position — `published_at < cursor_time OR (published_at = cursor_time AND id < cursor_id)`. This is stable even with concurrent inserts."

- **Mistake 4: Not addressing Kafka at-least-once + idempotent sink** → **Why it's wrong:** Kafka delivers at-least-once. If dedup is only in the Bloom filter (probabilistic) and not in the DB (idempotent INSERT), a Normalizer crash + Kafka redelivery creates duplicate article rows. → **What to say instead:** "The storage layer dedup is `ON CONFLICT (url_hash) DO NOTHING` — idempotent by design. Even if Kafka delivers the same article three times, only one row ever exists in the DB. The Bloom filter is a performance optimization (avoids a DB read), not the correctness guarantee."

- **Mistake 5: Choosing fan-out-on-write at Stage 2** → **Why it's wrong:** At 18 articles/sec × 50K avg subscribers/source = 900K feed writes/sec — far exceeds Postgres write capacity. Fan-out-on-write is a Stage 3 optimization for 500K+ reads/sec, not a default. → **What to say instead:** "I'll start with pull-on-read + Redis cache. Fan-out-on-write is the right upgrade when I'm seeing 500K+ reads/sec and cache-miss latency is hurting — I'll call that out explicitly as a Stage 3 transition."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How this design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/sources` returns `202 Accepted` (not 201) because the resource exists but articles aren't crawled yet; `GET /v1/feed` returns `304 Not Modified` on matching `If-None-Match` ETag — reducing 70K read payload to a header-only response for unchanged feeds; cursor pagination on `(published_at DESC, id DESC)` is stable under concurrent inserts |
| **Trade-off Defense** | ✅ | Three defended decisions: at-least-once + idempotent sink (correctness without Kafka transactions), **per-source shared cache over fan-out-on-write** (rejecting the reflex answer because a news feed is assembled from a shared pool while a Twitter timeline is a unique merge — with the condition under which the rejection reverses), SimHash Hamming-3 threshold (balanced dedup vs false-dup risk) — each stated with gain/lose/failure |
| **SQL / Data Modeling** | ✅ | Full DDL with 5 tables, 6 indexes; `ON CONFLICT (url_hash) DO NOTHING` for idempotent insert; partial index on `sources` (`WHERE status = 'active'`) for crawl scheduler efficiency; soft-delete on subscriptions to preserve invalidation audit trail |
| **Distributed Systems** | ✅ | Kafka partition-by-source guarantees ordering within source for dedup; Bloom filter rebuild every 24 hours avoids unbounded memory growth; crawl scheduler uses exponential backoff with jitter to prevent thundering-herd on publisher recovery; Redis pub/sub for feed cache invalidation on new article ingestion |
| **Pipeline Resilience** | ✅ | Ingestion is at-least-once Kafka with idempotent Postgres sink (redelivery = safe no-op); HTTP 304 Not Modified reduces 80% of crawler requests to no-ops; publisher failure → degraded status + exponential backoff (no avalanche); Bloom filter miss → graceful fallback to Postgres read (never a 500) |
| **Concurrency** | ✅ | Two concurrent Normalizer instances processing the same article after Kafka rebalance → `ON CONFLICT (url_hash) DO NOTHING` serializes at Postgres row level — one succeeds, one silently no-ops; cache invalidation via Redis pub/sub is fire-and-forget (eventual, acceptable for 2-minute cache TTL) |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "The aggregate news feed is architecturally an ingestion reliability problem first: publisher sources are unreliable, Kafka delivers at-least-once, and the dedup must therefore be idempotent at the storage layer — `ON CONFLICT (url_hash) DO NOTHING` is the correctness guarantee, not the Bloom filter (which is a read-optimization for the 99% of re-crawls that are exact duplicates). The API precision point is `POST /v1/sources` returning `202 Accepted` not `201 Created`, because the source is registered but articles haven't been crawled yet — 201 would mislead clients into expecting immediate article availability. Near-duplicate detection uses SimHash at Hamming distance ≤ 3 to collapse same-story articles from multiple publishers; this is the same locality-sensitive hashing principle that Iceberg's `MERGE INTO` uses for row-level upserts in Tableflow's table sink. The pipeline itself — crawler → Kafka `raw-articles` topic → Normalizer → Postgres hot + Iceberg cold — IS the Tableflow connector architecture made domain-specific: a Kafka Source Connector, a Streams/SMT processor, and an Iceberg table sink. The trade-off I'd defend first is rejecting fan-out-on-write, which is the reflex answer and is wrong for this domain: Twitter fans out on write because the median account has ~200 followers, so push is cheap for 99.9% of writes and only celebrities need the pull carve-out. Here a source is a publisher — 10K sources against 100M DAU means ~50K–200K subscribers on the *average* source, so every source is a celebrity and there is no cheap majority to exploit. More fundamentally, a `user_feed` row is `<article_id, user_id>` with zero per-user content: a Twitter timeline is a unique merge worth materializing, while a news feed is assembled from a shared pool of 10K sources, so materializing per-user just duplicates shared data 200,000 times and mostly for users who never open the app. So I cache per *source* — 10K ZSETs of the newest 50 articles, ~100MB total — and merge ~50 pre-sorted lists at read time. That is 18 writes/sec instead of 900K. It reverses only if ranking becomes per-user ML, where the feed stops being assemblable from shared lists."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Type 2 Full System Design, 15 sections per `solution-notes-standards.md`. Ingestion-first framing per research (ranking-first candidates were dinged). Covers: conditional HTTP (If-Modified-Since/ETag for 80% 304-rate on crawls), Bloom filter sizing for 15M active fingerprints (17.5MB), SimHash near-dup with Java implementation sketch, idempotent ON CONFLICT insert for Kafka at-least-once safety, 202 vs 201 API precision, 304 Not Modified on feed GET. Section 11 centerpiece: this pipeline IS Tableflow's connector architecture. |
| Aug 2026 | **Section 7 stage transitions tightened; Stage 3 now has a ceiling.** Added a build-it-up delivery note. Stage 3 gained an ASCII diagram of the fan-out path (Kafka `new-articles` keyed by `source_id` → fan-out consumer group → Cassandra `user_feed` partitioned by `user_id`, clustered on `published_at DESC`), a `WHY CASSANDRA EARNS ITS PLACE HERE` block, and a `CEILING OF STAGE 3` naming both ceilings — ~60-node cluster width at 900K writes/sec with RF=3, and head-of-line blocking where one 10M-subscriber source consumes ~11 seconds of whole-cluster capacity and breaches the 5-minute freshness target — plus four ordered next moves (cap partitions at ~500 items, hybrid fan-out for >1M-subscriber sources per Deep Dive 3, lazy fan-out for users inactive > 30 days, raise Kafka partition count and key on `(source_id, subscriber_shard)`). Stage 1→2 and 2→3 breaking points rewritten to name the exhausted resource explicitly: Stage 1 now shows the connection-pool ceiling collapsing from 10K to 4K reads/sec as query time degrades from 20ms to 50ms (the old line read "200 connections × 50ms avg = 10K req/sec", which was arithmetically wrong — 200 ÷ 50ms is 4K); Stage 2 now names single-primary WAL replay throughput, not replica count, as the limit. |
| Aug 2026 | **Stage 3 architecture reversed — fan-out-on-write was the wrong answer for this domain.** Caught by Kapil reading the file: "fan-out on write we use when followers are low, but here we are writing every time for users which are not active." Correct. Two distinct errors were present. (1) **Inverted celebrity logic.** Fan-out-on-write is cheap only when the fan-out factor has a small majority case — Twitter's median account has ~200 followers, and ByteByteGo's canonical news-feed chapter caps friends at 5,000, so push is cheap for ~99.9% of writes with a celebrity carve-out. In an aggregator a *source is a publisher*: 10K sources against 100M DAU × ~20 subscriptions is ~50K–200K subscribers on the **average** source. Every source is a celebrity, so the old "Next moves item 2: exempt the top 0.1%" rescued nothing. (2) **Materializing shared data.** A `user_feed` row is `<article_id, user_id>` with zero per-user content — a Twitter timeline is a unique merge worth materializing, but a news feed is assembled from a shared pool, so per-user materialization duplicates shared data 200,000× and mostly for users who never open the app (ByteByteGo lists this exact con of the push model). Stage 3 replaced with **per-source Redis ZSET cache (10K keys × newest 50 articles ≈ 100MB) + k-way merge at read time** — fan-out factor 1 and 18 writes/sec, against 900K writes/sec and ~60 Cassandra nodes. Added a `WHY NOT FAN-OUT-ON-WRITE HERE` block, an honest `BE HONEST ABOUT THE READ COST` block (~3.5M Redis key lookups/sec pipelined into ~70K round trips — cheaper, not free), and a ceiling that breaks on *breadth* not volume, whose first next-move **inverts Twitter's hybrid**: materialize only for power users with >500 subscriptions, because the expensive dimension is huge-SUBSCRIPTION not huge-FOLLOWER. Deep Dive 3, Trade-off 2, Section 14 and the TL;DR rewritten to match, each carrying the condition under which the rejection reverses (per-user ML ranking). **Also corrected the Stage 2 breaking point**, which had claimed the exhausted resource was "single-primary WAL replay throughput" — not credible at 18 articles/sec (~36KB/sec of WAL, which 50 replicas stream without noticing). The real ceiling is that each replica is a full private copy of data that is ~99.99% identical across users, and new replicas need a full base backup before serving, so the pool cannot scale reactively. |
| Aug 2026 | **All Section 7 diagrams redrawn — same defect class as the tempmail Stage 2 fix.** (1) The main HLD (ingestion + feed read path) was pure ASCII outline: boxes held 5-line bullet lists, so it read as a text document rather than a diagram. Redrawn in box-drawing with the bullets compressed to the branch-relevant lines. (2) **Semantic bug in the dedup branch:** the "Bloom says maybe seen" arrow pointed at `[Redis Bloom]`, but the Bloom probe had *already happened* one box earlier in the Normalizer — that branch must go to Postgres for the confirming read. The Bloom filter now appears twice with deliberately different arrows (Normalizer READS it at ①, post-insert step WRITES it at ⑤), and both surviving paths visibly converge on the single idempotent `ON CONFLICT DO NOTHING` write, which is the actual correctness guarantee. (3) The three stacked boxes `[Redis Bloom]`/`[Check exact]`/`[url_hash in DB]` were one logical step split across three boxes — merged. (4) **Stage 1 had no read path at all**, despite its entire BREAKING POINT being about read contention; it now draws ingestion and feed reads converging on the one primary with the shared-buffer-cache warning marked. (5) Stage 2's replicas hung off the primary in a linear chain implying sequential replication — now drawn as WAL fan-out to all 7, with the Redis cache on the read path and the invalidation pub/sub edge shown. (6) Stage 3 redrawn to make the write-side/read-side cost inversion visible, with a one-line statement of the trade. |
