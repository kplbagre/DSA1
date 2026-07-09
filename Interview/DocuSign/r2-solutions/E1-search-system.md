# E1 — Design a Search System (Full-Text Search at Scale)

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **Elasticsearch / inverted index** | `Production-Grade/Performance-Optimization/32-elasticsearch-inverted-index.md` | The core data structure — you must be able to explain how the inverted index maps token → [doc_id, position] and why it makes full-text search O(1) per term instead of O(N) table scan |
| **DB sharding strategy** | `Core-Architecture/Database-Core/38-sharding-strategy.md` | Elasticsearch indices are sharded across nodes — know primary shard routing, replica shards for read availability, and rebalancing when nodes are added |
| **Hot partition problem** | `Core-Architecture/Database-Core/45-hot-partition-problem.md` | Trending queries create hot shards — know how to detect and mitigate with query caching, shard rebalancing, and adaptive routing |
| **Scaling reads** | `Patterns/DeepDive/01-scaling-reads.md` | Search is almost exclusively reads — replica shards for horizontal read scaling, query cache for repeated identical queries |
| **Caching fundamentals** | `Foundations/Performance-and-Scale/03-caching.md` | Top-K query result caching in Redis — TTL tuning, cache-aside pattern for search results, when NOT to cache (personalised results) |
| **Database indexing** | `Foundations/Data-Fundamentals/50-database-indexing.md` | Document metadata stored in a relational DB alongside the search index needs proper indexing for filter queries (by date, category, owner) |

---

## 🎯 What Is This System?

**In plain English:** A search system accepts text queries and returns ranked results from an index of documents, products, or web pages — in milliseconds, even when the index contains billions of entries. The ranking must feel relevant (matching intent, not just keywords), and the index must stay up to date as new content is added.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Elasticsearch / OpenSearch** | Apache Lucene-based distributed search engine; powers most enterprise search |
| **Algolia** | SaaS search API — sub-10ms results, tunable relevance, used by Stripe Docs, Twitch |
| **Apache Solr** | Lucene-based; older but widely deployed in enterprise content systems |
| **Amazon product search** | Billions of products; ranking blends relevance + revenue signals |
| **Spotify search** | Songs, albums, artists, podcasts — fuzzy match + personalization |
| **LinkedIn profile search** | 900M professional profiles; faceted filtering + endorsement signals |

**Core user journey:** User types "blue waterproof running shoes size 10" into Amazon search → query is tokenized and matched against the inverted index → top 20 results returned in under 100ms → results are re-ranked by personalization and purchase-probability signals.

**Why it's hard to build at scale:** An inverted index maps every word to every document containing it — at billions of documents this must be sharded across many nodes; relevance ranking (TF-IDF, BM25) is CPU-intensive per query; the index must update in near-real-time as new documents are added; and typo tolerance (fuzzy matching) multiplies the computational cost.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | **Design a Full-Text Search System** (indexed document search with ranking, filters, and autocomplete) |
| **Interview Type** | **Type A — System Design** (Infrastructure: indexing, sharding, ranking, query processing, caching) |
| **Confirmed or Likely** | 🔶 Likely (Common follow-up when designing document storage; fundamental SDE-3 skill) |
| **Concept notes prerequisite** | `05-consistent-hashing.md` (sharding strategy), `03-caching.md` (query result caching), `11-api-design.md` (search API contract) |
| **DocuSign-specific angle** | Search signed contracts by keyword, party name, date range; full audit trail of search queries (compliance); precise ranking (relevance + recency + document status). |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I design, let me clarify the search scope — are we doing full-text search on document content, metadata-only search, or both? And what ranking strategy matters most: relevance, recency, or popularity?"

Then pivot to Section 2.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "Do we search document content (full-text inside PDFs) or just metadata (title, author, created date)?"**
- Why ask: Full-text requires indexing 50M × 500MB = 25 petabytes (infeasible); metadata-only is cheap
- If full-text → need Elasticsearch + OCR for PDFs (expensive); limited to excerpts
- If metadata + title/snippet → simpler; metadata DB + search index

**Q: "What's the ranking priority — relevance score (TF-IDF — Term Frequency × Inverse Document Frequency: how often the term appears in *this* doc, divided by how commonly it appears across *all* docs; rare words that appear often in a doc rank highest), recency (newer first), or popularity (most viewed)?"**
- Why ask: Ranking algorithm drives index schema and query complexity
- If relevance → need tf-idf scoring; ranked queries slower
- If recency → simple date sort; very fast
- If mixed → need multi-field scoring; compute-heavy

**Q: "Do we need autocomplete suggestions (prefix matching) as a separate feature?"**
- Why ask: Autocomplete requires a trie (a tree where each node is one character of a word; traversing "c→o→n→t→r" leads directly to all words starting with "contr"; O(prefix-length) lookup with no wasted comparisons) or prefix index; adds latency/complexity
- If yes → separate trie structure; 10ms latency for 100M suggestions
- If no → simple keyword search only

**Q: "What are the consistency requirements — should search index updates be immediate or eventual?"**
- Why ask: Immediate consistency (sync) requires 2-phase commit; eventual consistency (async) allows lag
- If immediate → Elasticsearch with sync writes; higher latency
- If eventual → queue-based indexing; users see results 1-2 seconds later

**Q: "How many concurrent searches per second, and what's the acceptable P99 latency?"**
- Why ask: Drives sharding, caching, and query optimization strategy

**Q: "Do users need to filter by metadata (author, date range, document type) as part of search?"**
- Why ask: Faceted search adds complexity (multi-dimensional queries)
- If yes → need inverted index per facet; slower but richer
- If no → simple full-text; fast

---

## Section 3 — 📋 Requirements (Functional + Non-Functional)

**Functional Requirements:**
- Users should **search documents by keyword** (full-text on title + snippet, not full PDF content)
- System should **rank results by relevance** (TF-IDF), with recency as tiebreaker
- Users should **filter by metadata** (author, date range, document type, status)
- System should provide **autocomplete suggestions** (prefix matching on keywords)
- System should support **boolean operators** (AND, OR, NOT)
- Out of scope: Fuzzy matching, typo correction (spell-check), full PDF content search, NLP-based meaning

**Non-Functional Requirements:**
- Scale: 10M users, 50M documents (avg 5 docs/user), 100M searches/day (~1.16K searches/sec baseline, 3.5K peak)
- Latency: P99 search results < 500ms (including ranking)
- Autocomplete: P99 < 100ms (must be fast for typing)
- Availability: 99.9% SLO (9 hours downtime/year)
- Consistency: **Eventual consistency acceptable** (search index lag ~1-2 seconds behind document creation)
- Durability: Search index is derived (not source of truth); can be rebuilt from metadata DB
- Index freshness: New/updated documents searchable within 5 seconds

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **Document** (metadata) | Title, snippet, author, type, tenant, created_at — what gets indexed; NOT the file bytes | PostgreSQL (source of truth) |
| **SearchIndex** | Inverted index of all terms across all documents — derived from Document metadata | Elasticsearch (derived, rebuildable) |
| **AutocompleteTrie** | Prefix → top-N completions scored by popularity — e.g., `trie:contr → ["contract", "contractor"]` | Redis sorted sets (cached, TTL-based) |
| **SearchQuery** (analytics) | Log of queries, clicked results, zero-result searches — used for ranking feedback and relevance tuning | Analytics DB / S3 (write-only) |

**Key relationships:**
- `Document` in PostgreSQL is the source of truth; `SearchIndex` in Elasticsearch is a derived copy (eventual consistency ~1-2s lag is acceptable)
- When a `Document` is created or updated → Kafka event → Indexing Processor updates `SearchIndex`
- `AutocompleteTrie` is built from `SearchIndex` term aggregations and cached in Redis — rebuilt on TTL expiry
- **Critical**: if Elasticsearch is corrupted or lost, it can be fully rebuilt by re-indexing all rows from PostgreSQL; PostgreSQL is never replaced by Elasticsearch

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**Traffic:**
- DAU: 10M users
- Searches/day: 100M = ~1.16K searches/sec baseline, 3.5K peak
- Autocomplete queries: assume 20% of searches request prefix suggestions = 232 queries/sec baseline, 700 peak
- Index updates: 10M new documents/day (if DocuSign context) + 5M updates/day = 0.2 writes/sec baseline, 0.6 peak

**Storage (Elasticsearch):**
- Per document index: title (200 bytes) + snippet (2 KB) + metadata (500 bytes) + inverted index (~10× raw text)
- Index size per document: 2 KB raw + 20 KB inverted index = 22 KB per doc
- Total: 50M docs × 22 KB = 1.1 TB index (fits in Elasticsearch cluster)
- With replication (3 copies for HA): 3.3 TB

**Bandwidth:**
- Inbound (indexing): 0.6 writes/sec × 2.2 KB = 1.3 KB/sec (negligible)
- Outbound (search results): 3.5K queries/sec × 5 KB/result page (10 results) = 17.5 MB/sec

**Key conclusions:**
- At 3.5K searches/sec, single Elasticsearch instance handles easily (typical: 10K+ QPS capacity)
- Sharding by document_id (50 shards = 1M docs/shard) ensures even distribution
- Autocomplete (700 QPS) requires dedicated trie index or cached suggestions (Redis)
- Index lag of 1-2 seconds is acceptable (eventual consistency)

---

## Section 5 — 🔄 Requirements Variation Table ⭐ Key Differentiator

| Requirement | Simple (title only) | Complex (full features) | Impact on design |
|---|---|---|---|
| **Search scope** | Metadata only (title, author) | Full-text + filters + boolean | Simple DB query → Elasticsearch inverted index |
| **Ranking** | Recency (sort by date DESC) | TF-IDF relevance + recency | Simple sort → BM25 scoring algorithm + multi-field weight |
| **Autocomplete** | No | Yes (prefix matching) | No → separate trie index or Redis suggestions cache |
| **Consistency** | Immediate (sync writes) | Eventual (async updates) | Sync index writes (slow) → async queue-based indexing (fast) |
| **Scale** | 1M docs, 100 searches/day | 50M docs, 100M searches/day | Single DB instance → Elasticsearch cluster with sharding |
| **Filters** | No faceted search | Full faceted (10+ dimensions) | Simple WHERE clause → multi-field inverted index per facet |

---

## Section 8 — 🌐 API Design (Before HLD)

> **Why here:** Define the external contract before drawing the architecture — the HLD shows how these endpoints are implemented. For Type A, this is concise (3–5 minutes); the architecture is the primary deliverable.

### 🧠 How to Derive These Endpoints

Search has three user-facing operations: search (query), autocomplete (suggest-as-you-type), and preview (expand a result). The derivation is straightforward; the interesting design is in the cursor mechanism and the tenant isolation contract.

"Users search for documents by keywords" → `GET /v1/search?q=contract`. All filters (date range, author, status) are query params, AND-ed together. Response includes `results`, `total_hits`, and `next_cursor` for pagination. The `q` parameter is mandatory — searching with no query returns nothing (prevent full-index scans).

"Users see suggestions while typing" → `GET /v1/autocomplete?prefix=contr`. Separate endpoint because autocomplete is latency-critical (< 50ms) and uses a different data structure (completion suggester in Elasticsearch, not full-text search). Cached aggressively: prefix completions for "docuSign" are the same for all users in the same tenant. 5-minute TTL on Redis — stale suggestions are acceptable.

"User clicks a result to see more details" → `GET /v1/search/{doc_id}/preview`. Returns the full snippet + metadata. Why a separate endpoint instead of returning full content in the search results? Search results may return 10 items — fetching full snippets for all 10 is expensive. Return short snippets in search, full preview on demand.

Cursor design for search: search cursors are NOT the same as DB cursors. In Elasticsearch, cursors use `search_after` — the sort values of the last result (`[score, doc_id]`). The cursor encodes these values as base64 and passes them as `?cursor=` on the next request. Elasticsearch uses them to start the next page from the correct position in the index. Offset-based pagination (`from: 100`) is forbidden in Elasticsearch beyond 10,000 results — `search_after` is the required approach.

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| GET | `/v1/search` | JWT Bearer | `?q=contract&date_from=&date_to=&author=alice&limit=10&cursor=` | `{results: [{id, title, snippet, score}], total_hits, next_cursor}` | 200, 400, 403 |
| GET | `/v1/autocomplete` | JWT Bearer | `?prefix=contr&limit=10` | `{suggestions: ["contract", "contractor"]}` | 200, 400 |
| GET | `/v1/search/{doc_id}/preview` | JWT Bearer | — | `{document_id, title, full_snippet, author, created_at}` | 200, 403, 404 |

### 🔍 Endpoint Stories

**`GET /v1/search`** is 90% of the system. The `tenant_id` claim in the JWT is automatically injected as a mandatory filter in every Elasticsearch query — users cannot search outside their tenant. This is enforced in the Query Builder layer, not in the client request. If a tenant filter were missing, a user could query across all documents. The `total_hits` field is approximate beyond 10,000 results — Elasticsearch uses a sampling heuristic for large counts. For a document management system, "1000+ results" is accurate enough. If exact counts are required, add a Count API backed by Postgres.

**Cursor pagination for search** is a different beast from DB cursor pagination. DB cursors encode `(last_seen_id, last_seen_created_at)`. Elasticsearch cursors encode `search_after` values: the sort values of the last result — typically `[_score, doc_id]`. The `next_cursor` in the response is base64(`[score, doc_id]` of the last result). The next request passes this as `?cursor=` and Elasticsearch picks up from that position. Why not use Elasticsearch's `scroll` API? Scroll is for bulk export, not user-facing pagination — it holds a point-in-time snapshot for minutes (expensive, doesn't scale to millions of simultaneous users).

**`GET /v1/autocomplete`** has a 50ms latency SLO — tighter than the search endpoint (200ms). Implementation: Elasticsearch completion suggester on the `title.autocomplete` field (edge n-gram tokenized). Redis caches `prefix → [suggestions]` with 5-minute TTL. Cache hit rate is high because prefix distribution follows Zipf's law — the top 1,000 prefixes cover 80% of all autocomplete requests. Cache miss falls through to Elasticsearch completion suggester.

**`GET /v1/search/{doc_id}/preview`** does NOT call Elasticsearch — it fetches from Postgres (metadata) and returns a truncated document content from the stored source. Why not Elasticsearch? Because Elasticsearch stores the indexed text for search purposes; the canonical document content (with guaranteed accuracy) is in Postgres. Elasticsearch's stored `_source` can lag behind by reindex time. The probe: "What if the preview content is stale?" Answer: preview fetches directly from Postgres, which is always up-to-date. Only the search index can be stale.

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

### Stage 1 — Postgres Full-Text Search (Baseline)

> Start here. Works for small scale (< 1M documents). Breaking point: at 50M docs + 3,500 QPS, Postgres GIN index saturates and ranking quality is unacceptable.

```
── Stage 1: Postgres Full-Text ───────────────────────────────────────

 ┌──────────────────┐  creates doc  ┌──────────────────────────────────┐
 │  Document Service │──────────────▶│           PostgreSQL              │
 └──────────────────┘               │  documents table                 │
                                    │  tsvector_col GIN index          │
 ┌──────────────────┐  GET /search  │  to_tsvector(title || snippet)   │
 │  Search Service  │──────────────▶│                                  │
 └──────────────────┘               │  SELECT id, title, snippet,      │
       ▲  results                   │    ts_rank(tsv, query) AS score   │
       │                            │  FROM documents                   │
       └────────────────────────────│  WHERE tsv @@ plainto_tsquery(?) │
                                    │    AND owner_id = ?               │
                                    │  ORDER BY score DESC LIMIT 10    │
                                    └──────────────────────────────────┘

BREAKING POINT:
   At 50M documents, the GIN index grows to ~50 GB in memory.
   At 3,500 queries/sec, Postgres CPU saturates — ts_rank is computed
   at query time per row, not pre-indexed.
   No BM25 relevance scoring. No field-level boosting. No autocomplete.
   No horizontal shard-out. Postgres full-text is functional, not a search engine.
```

**WHICH search backend?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Postgres `tsvector` / `tsquery` | Zero extra infra; ACID; co-located with source data | CPU-bound ranking at query time; no BM25; no autocomplete; saturates at 50M docs + 3.5K QPS | ✅ Fine for < 1M docs; breaks at scale |
| Elasticsearch (Lucene engine) | Purpose-built inverted index; BM25 native; sharding + replicas; autocomplete; < 100ms at any scale | Extra infra; eventual consistency (1-2s lag); derived index — not source of truth | ✅ Right choice for Stage 2 |
| Solr / OpenSearch | Same Lucene engine as Elasticsearch; open-source alternatives | Less ecosystem support; same trade-offs as ES | ⚠️ Viable; ES has more enterprise adoption at DocuSign scale |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/05-consistent-hashing.md`

---

### Stage 2 — Elasticsearch + Kafka + Redis (Production)

> **Why we evolve:** Stage 1 breaks at 50M docs + 3.5K QPS. Fix: move full-text search to Elasticsearch (purpose-built), decouple indexing via Kafka (so document creation never blocks on ES), cache search results in Redis (80% hit rate → ES sees only 700 QPS).

```
── Stage 2: Production ───────────────────────────────────────────────

── Indexing Path (write-side) ────────────────────────────────────────

 ┌──────────────────┐
 │  Document Service │
 └──────┬───────────┘
        │  1. Save to Postgres (source of truth)
        │  2. Publish to Kafka: "documents.indexed"
        │
 ┌──────▼───────────────────────────┐
 │           PostgreSQL             │
 │  documents (source of truth)     │
 └──────────────────────────────────┘
        │ Kafka async (1-2s lag)
 ┌──────▼───────────────────────────┐
 │      Indexing Processor          │
 │  (Kafka consumer group,          │
 │   stateless, horizontally scaled)│
 │  - extract title, snippet, meta  │
 │  - bulk API → Elasticsearch      │
 └──────┬───────────────────────────┘
        │ bulk index
 ┌──────▼───────────────────────────────────────────────────┐
 │               Elasticsearch Cluster                      │
 │   50 shards (hash by doc_id) × 3 replicas               │
 │   Shard 0: docs hash % 50 = 0   (1M docs)               │
 │   Shard 1: docs hash % 50 = 1   (1M docs)               │
 │   ...                                                    │
 │   Shard 49: docs hash % 50 = 49  (1M docs)              │
 └──────────────────────────────────────────────────────────┘

── Search Path (read-side) ──────────────────────────────────────────

 ┌────────────┐  GET /v1/search?q=contract  ┌────────────────────────┐
 │   Client   │────────────────────────────▶│    Search Service      │
 └────────────┘                             │  JWT auth + filter     │
       ▲  HTTP 200 results                  └────────────┬───────────┘
       └────────────────────────────────────             │ cache-aside
                                            ┌────────────▼───────────┐
                                            │  Redis (5-min TTL)     │
                                            │  key: hash(q+filters)  │
                                            │  hit  → return < 5ms   │
                                            │  miss → query ES       │
                                            └────────────┬───────────┘
                                                         │ cache miss
                                            ┌────────────▼───────────────────────────┐
                                            │        Elasticsearch Cluster           │
                                            │  scatter query to all 50 shards        │
                                            │  each shard: BM25 rank, return top 10  │
                                            │  coordinating node: merge + re-rank    │
                                            │  latency: ~80-120ms                    │
                                            └────────────────────────────────────────┘

── Autocomplete Path ────────────────────────────────────────────────

 ┌────────────┐  GET /v1/autocomplete?prefix=contr
 │   Client   │────────────────────────────────────────────────────▶ Redis ZSET
 └────────────┘                                                      trie:contr
       ▲  ["contract", "contractor", "contracting"]◀─────────────── ZRANGE 0 9
       └─────────────────────────────────────────────────────────────────────────

KEY INVARIANT:
   PostgreSQL is source of truth — Elasticsearch is a derived index (rebuildable).
   Kafka decouples document writes from indexing — ES failure never fails doc creation.
   Indexing lag of 1-2 seconds is acceptable (eventual consistency).
   Redis absorbs 80% of search queries — Elasticsearch sees only ~700 QPS at peak.
   Scatter-gather across 50 shards runs in parallel — coordinating node merges results.
```

**WHICH indexing transport?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Sync write to Elasticsearch | Document searchable immediately after creation | ES down → document creation fails; ES write latency (50-200ms) added to doc create P99 | ❌ Couples document creation availability to ES |
| Async via Kafka (outbox pattern) | Document creation is fast (10ms to Postgres); ES failure replays from Kafka; backlog processed horizontally | 1-2 second index lag (eventual consistency) | ✅ Best — decoupled availability; lag is acceptable |

> 📖 Full: `SystemDesignConcepts/Production-Grade/Infrastructure/19-message-queues-kafka-rabbitmq.md`

**WHICH sharding strategy?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Shard by doc_id (hash) | Even distribution across all shards; no hotspots; deterministic routing | Query broadcasts to all shards (scatter-gather); merge step adds ~20ms | ✅ Best — even load; ES optimizes scatter-gather with `query_and_fetch` |
| Shard by user_id (range) | Queries hit only 1-2 shards (faster per-query) | Power users get hot shards; rebalancing is complex as users grow | ❌ Uneven distribution creates hotspots |
| Shard by date range (time-based) | Natural for archival — old indices move to cold storage | Complex index lifecycle management; cross-date queries span shards | ⚠️ Good for time-series logs, not general document search |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/05-consistent-hashing.md`

---

### Data Flow Walkthrough (say this out loud)

**Flow 1 — Indexing (document created/updated):**
1. Document Service saves to Postgres (source of truth), publishes `documents.indexed` to Kafka.
2. Indexing Processor (Kafka consumer group) consumes: extracts title, snippet, metadata. Builds inverted index (a lookup table mapping each word → list of document IDs containing it; like the index at the back of a textbook) and sends to Elasticsearch `_bulk` API.
3. Elasticsearch routes to shard: `hash(doc_id) % 50`. Document is searchable within ~1-2 seconds.

**Flow 2 — Search query:**
1. Client `GET /v1/search?q=contract&owner=alice`. Search Service checks Redis cache (key = `hash(query + filters + userId)`).
2. Cache hit (80%) → return results in < 5ms.
3. Cache miss → Elasticsearch scatter-gather: coordinating node broadcasts query to all 50 shards in parallel (scatter-gather — the coordinating node "scatters" the query to every shard simultaneously, collects top-N from each, then "gathers" into the final ranked list). Each shard ranks with BM25. Coordinating node merges 50 × 10 results, re-ranks, returns top 10. ~80-120ms.
4. Cache result in Redis (5-min TTL). Return to client.

**Flow 3 — Autocomplete:**
1. User types "contr" → `GET /v1/autocomplete?prefix=contr`.
2. `ZRANGE trie:contr 0 9` from Redis ZSET (each word scored by popularity). Returns in < 10ms.
3. If key expired: rebuild from Elasticsearch `terms` aggregation (~200ms), repopulate Redis.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

### Deep Dive 1: Elasticsearch Sharding Strategy + Query Routing

**Why this is the most critical component:**
At 50M documents, a single Elasticsearch node (max ~100GB heap) is insufficient. Sharding distributes the index across multiple nodes. Poor shard strategy means some nodes are hot (high CPU/query latency) while others are cold (wasted capacity). At 3.5K queries/sec, even load distribution is critical.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Shard by document_id (hash)** | Even distribution; queries hit all shards equally; no hotspots | Queries must be broadcast to all shards (scatter-gather); merge step adds latency |
| **Option B: Shard by user_id (range)** | Queries hit 1-2 shards only (faster); user's documents stay together | Uneven distribution (power users get hot shards); scaling complex (rebalancing needed) |
| **Option C: Shard by date range (time-based)** | Natural for archival (old indices move to slow storage); new index hot | Complex lifecycle management; queries spanning date ranges hit multiple shards |

**Decision: Option A (shard by document_id hash).**

Because it's predictable (hash function is deterministic) and evenly distributes query load. Scatter-gather latency is acceptable (Elasticsearch optimizes this with query_and_fetch).

**Implementation sketch:**

```java
// Elasticsearch shard routing
// Given 50 shards:
// shard_id = hash(document_id) % 50

@Service
public class SearchService {
    private final RestHighLevelClient elasticsearchClient;

    /**
     * Search with sharding strategy.
     * Query goes to ALL shards; each shard searches its local index.
     * Results are gathered and re-ranked.
     */
    public SearchResults search(String query, SearchFilters filters, String userId, String tenantId) {
        // Build Elasticsearch query
        SearchRequest request = new SearchRequest("documents");  // index name
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // BM25 scoring (Elasticsearch default for relevance)
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("title_or_snippet", query))
            .must(QueryBuilders.termQuery("tenant_id", tenantId))
            .must(QueryBuilders.termQuery("owner_id", userId))
            .filter(QueryBuilders.rangeQuery("created_at")
                .gte(filters.getDateFrom())
                .lte(filters.getDateTo())
            )
            .filter(QueryBuilders.termQuery("status", "ACTIVE"));

        sourceBuilder.query(boolQuery);

        // Sorting: relevance (BM25 score) first, then recency
        sourceBuilder.sort(new ScoreSortBuilder().order(SortOrder.DESC));
        sourceBuilder.sort("created_at", SortOrder.DESC);

        sourceBuilder.from(0).size(10);  // pagination

        request.source(sourceBuilder);

        // Execute search (Elasticsearch handles sharding internally)
        SearchResponse response = elasticsearchClient.search(request, RequestOptions.DEFAULT);

        // Parse results
        List<SearchResult> results = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            results.add(parseSearchHit(hit));
        }

        return new SearchResults(results, response.getHits().getTotalHits().value);
    }

    /**
     * Index a document (called by indexing processor).
     * Elasticsearch automatically routes to shard: hash(doc_id) % 50
     */
    public void indexDocument(String documentId, DocumentIndexPayload payload) throws IOException {
        IndexRequest request = new IndexRequest("documents")
            .id(documentId)  // document_id is the ES doc ID (used for routing)
            .source(
                "title", payload.getTitle(),
                "snippet", payload.getSnippet(),
                "owner_id", payload.getOwnerId(),
                "tenant_id", payload.getTenantId(),
                "created_at", payload.getCreatedAt(),
                "status", payload.getStatus()
            );

        // ES automatically routes based on doc ID hash
        IndexResponse response = elasticsearchClient.index(request, RequestOptions.DEFAULT);
    }
}
```

**Sharding detail:**
- 50 shards (typical: 1 shard per 1M documents)
- 3 replicas (HA: if one replica is down, 2 are still available)
- Total storage: 1.1 TB index × (1 primary + 3 replicas) = 4.4 TB
- Query routing: Elasticsearch uses consistent hashing internally; hash(doc_id) % 50

**Query execution on shards:**
1. Client sends query to coordinating node (any ES node)
2. Coordinating node broadcasts query to all 50 shards in parallel
3. Each shard searches its local index (1M docs) → returns top 10 results
4. Coordinating node merges 50 × 10 = 500 results, re-ranks by score, returns top 10
5. Total latency: shard query (50-100ms) + merge (10-20ms) = ~100-120ms

**Why this deep dive matters:**
- Shard strategy determines query latency and load distribution
- Uneven sharding causes hotspots (some shards get 10× more queries)
- Hash-based sharding is simple and distributes evenly but requires broadcast queries

**⚠️ Production gotcha: Elasticsearch `refresh_interval` — newly indexed docs are invisible**

By default, Elasticsearch refreshes its in-memory index to disk every **1 second** (`refresh_interval = 1s`). A document indexed at T=0 is not searchable until T=1s (after the next refresh cycle).

**Why this matters for DocuSign document search:**
- User uploads a document → indexing pipeline writes to ES
- User immediately searches for the document → 0-1 second window where the document is not found
- User assumes the upload failed → retries → possible duplicate

**Two strategies:**

| Strategy | Config | Effect | Use when |
|---|---|---|---|
| Default refresh (1s) | `"refresh_interval": "1s"` | Documents searchable within ~1s | Most search use cases ✅ |
| Immediate refresh | `?refresh=true` or `?refresh=wait_for` on index API | Document searchable immediately | Real-time indexing requirements |
| Bulk indexing optimization | `"refresh_interval": "30s"` or `-1` (disable) during bulk | Faster bulk load | Initial index build or large batch ingestion |

**The `?refresh=wait_for` option:** When the indexing processor calls the ES index API with `?refresh=wait_for`, ES blocks the response until the next refresh cycle completes — guaranteeing the document is searchable before the API returns. This adds ~1s to the indexing latency but gives you "indexed and immediately searchable" semantics.

```java
// In SearchService.indexDocument():
IndexRequest request = new IndexRequest("documents")
    .id(documentId)
    .source(/* fields */)
    .setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);  // block until searchable

IndexResponse response = elasticsearchClient.index(request, RequestOptions.DEFAULT);
// Document is now searchable — use this for user-facing uploads
```

**Trade-off:** `refresh=wait_for` adds ~1s to P99 indexing latency. For background batch indexing (reindexing, bulk import), always use the default `1s` refresh (or disable it during bulk, re-enable after). Use `wait_for` only on user-triggered uploads where the user immediately searches.

**In an interview:** "Elasticsearch has a 1-second refresh window between indexing and searchability. For user-facing uploads, I'd use `refresh=wait_for` to guarantee the document is searchable before returning 201 to the client. For background bulk indexing, I'd disable refresh during the batch and run a manual refresh at the end."

---

### Deep Dive 2: BM25 Ranking Algorithm + Relevance Scoring

**Why this is the most critical component:**
Search results quality depends on ranking. BM25 is the industry-standard algorithm (used by Elasticsearch, Lucene, Google's earlier search). Understanding it shows you grasp information retrieval fundamentals. At 100M searches/day, a bad ranking drives users away.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: TF-IDF (term frequency-inverse document frequency)** | Simple, well-understood; fast; good baseline | Doesn't account for document length; term position doesn't matter |
| **Option B: BM25 (Okapi BM25)** | Industry standard; accounts for doc length + term saturation; queries return top results; used by Elasticsearch | Slower than TF-IDF (but acceptable); hyperparameters (k1, b) need tuning |
| **Option C: Learning-to-Rank (ML-based)** | Best relevance; can incorporate user feedback | Complex; requires training data; slow; overkill for MVP |

**Decision: Option B (BM25).**

Because it's battle-tested and Elasticsearch uses it by default. You don't customize it unless specific ranking problems emerge.

**BM25 formula (simplified):**

```
score(D, Q) = Σ IDF(qi) * (f(qi, D) * (k1 + 1)) / (f(qi, D) + k1 * (1 - b + b * |D| / avg_len))

Where:
  D = document
  Q = query (list of terms q1, q2, ...)
  f(qi, D) = frequency of query term qi in document D
  IDF(qi) = log((N - n(qi) + 0.5) / (n(qi) + 0.5))
    (higher if term is rare; lower if common)
  |D| = length of document
  avg_len = average document length
  k1 = saturation point (default: 1.2)
  b = length normalization (default: 0.75)
```

**Intuition:**
- Terms that appear in few documents (high IDF) boost score more
- Terms that appear many times in a document boost score, but with diminishing returns (saturation)
- Longer documents are penalized slightly (to avoid bias toward long docs)
- k1 and b are tuned empirically

**Example:**
- Query: "contract nda"
- Doc A: title="NDA Contract" (2 words, both match) → high score
- Doc B: "Here is a contract for an NDA with many terms and conditions" (50 words, both match) → lower score due to length
- Doc C: "contract contract contract contract contract nda" (6 words, repeated) → high score, but with saturation (5th "contract" adds less than 2nd)

**Configuration in Elasticsearch:**

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "match": {
            "title_or_snippet": {
              "query": "contract nda",
              "boost": 2.0  // title matches are 2× more important
            }
          }
        }
      ],
      "filter": [...]
    }
  }
}
```

**Why this deep dive matters:**
- BM25 is the foundation of relevance; understanding it helps debug ranking issues
- Boosting certain fields (title > body) makes queries more focused
- Term frequency saturation prevents single-term spam from dominating results

---

### Deep Dive 3: Cache Strategy + Query Result TTL

**Why this is the most critical component:**
At 3.5K queries/sec, 80% cache hit rate means only 700 queries hit Elasticsearch per second. Without caching, 3.5K queries/sec would overwhelm Elasticsearch (capacity is ~5-10K but with high CPU). Cache is the difference between scaling to 100M searches/day and needing 10× more infrastructure.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: No cache (every query hits ES)** | Simple; always fresh | 3.5K queries/sec → Elasticsearch at 50% CPU at peak; slow (100ms+ latency) |
| **Option B: Query result cache (Redis)** | Huge latency improvement (< 5ms); reduced ES load (80% hit rate); simple to implement | Results can be stale (up to TTL); cache key explosion (millions of unique queries) |
| **Option C: Precomputed suggestions (trie)** | Instant (< 5ms); always available | Only works for autocomplete; doesn't cache ranked results |

**Decision: Option B (query result cache with 5-minute TTL).**

Because 5-minute staleness is acceptable for document search (documents updated hourly on average), and hit rate is 80%+ (same searches repeated frequently).

**Implementation sketch:**

```java
@Service
public class SearchCacheService {
    private final RedisTemplate redis;
    private final SearchService elasticsearchService;

    /**
     * Search with caching layer.
     * Cache key = hash(query + filters + user_id + tenant_id)
     * TTL = 5 minutes (balance freshness + hit rate)
     */
    public SearchResults search(String query, SearchFilters filters, String userId, String tenantId) {
        // Step 1: Compute cache key
        String cacheKey = "search:" + computeHash(query, filters, userId, tenantId);

        // Step 2: Check Redis
        Optional<SearchResults> cached = redis.opsForValue().get(cacheKey, SearchResults.class);
        if (cached.isPresent()) {
            metrics.recordCacheHit();
            return cached.get();
        }

        // Step 3: Cache miss → query Elasticsearch
        metrics.recordCacheMiss();
        SearchResults results = elasticsearchService.search(query, filters, userId, tenantId);

        // Step 4: Cache results (5-minute TTL)
        redis.opsForValue().set(
            cacheKey,
            results,
            Duration.ofMinutes(5)
        );

        return results;
    }

    private String computeHash(String query, SearchFilters filters, String userId, String tenantId) {
        String combined = query + "|" + filters.toString() + "|" + userId + "|" + tenantId;
        return DigestUtils.md5Hex(combined);
    }

    /**
     * Invalidate cache when documents are indexed.
     * Called by indexing processor.
     */
    public void invalidateCacheForDocument(String documentId) {
        // Option 1: Brute-force invalidation (slow but simple)
        // redis.getConnectionFactory().getConnection().flushAll();

        // Option 2: Smart invalidation (invalidate queries containing keywords from doc)
        // For simplicity, set a background job to clear old entries
        // Cache expiry (TTL) handles most invalidations automatically.
    }
}
```

**Cache metrics:**
- Hit rate: 80% (same query repeated 5 times on average within 5 minutes)
- Load reduction: 3.5K queries/sec × 80% = 2.8K queries/sec saved (only 700 hit ES)
- Memory overhead: 100M queries × 5 KB per result page ÷ 80% hit rate = worst case 6.25 GB (fits in single Redis instance)
- Eviction policy: LRU (Least Recently Used) when memory limit reached

**⚠️ Cache hit rate correction — the 80% claim needs context:**

80% cache hit rate is **optimistic and only realistic for specific query patterns**. The actual hit rate depends heavily on query diversity:

| Query type | Real hit rate | Why |
|---|---|---|
| Autocomplete prefixes (e.g., "cont", "contr", "contra") | 80–90% | Zipf distribution — top 1K prefixes cover most traffic |
| Popular common searches ("NDA template", "contract 2026") | 40–60% | Some repetition across tenants |
| **Per-user filtered search (DocuSign context)** | **20–40%** | Each user's search includes `tenant_id + user_id` in the cache key — two users searching the same query never share a cache entry |
| Long-tail queries (unique combinations of terms) | 5–15% | Enormous unique query space |

**For DocuSign's document search (tenant-isolated, user-specific):** the cache key includes `tenant_id` and access control filters. A realistic hit rate is **20–40%**, not 80%. The design is still correct — caching reduces ES load meaningfully — but you shouldn't state 80% to a senior interviewer without the qualification.

**In an interview:** "Cache hit rate varies significantly by query type. For autocomplete prefixes, 80%+ is realistic because most users type the same common prefixes. For full-text search in a multi-tenant system where the cache key is per-user, I'd estimate 20–40% hit rate — still meaningful (reduces ES to 2K–2.8K QPS) but not the 80% figure that applies to public, non-personalized search."

**Why this deep dive matters:**
- Cache is essential for search scalability even at 20-40% hit rate (meaningful reduction)
- 5-minute TTL balances freshness (documents visible within 5 min) and hit rate
- Smart invalidation (trigger on document index) can improve freshness without sacrificing hit rate

---

## Section 9 — 🗄️ Data Model

```sql
-- Elasticsearch mapping (not relational DB, but schema-like structure)
-- Index name: "documents"
-- Shard count: 50 (for 50M documents)
-- Replica count: 3 (for HA)

{
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "standard",  -- tokenizes on whitespace, lowercase
        "fields": {
          "keyword": {"type": "keyword"}  -- for exact matching
        }
      },
      "snippet": {
        "type": "text",
        "analyzer": "standard"
      },
      "owner_id": {
        "type": "keyword"  -- exact match only, no analysis
      },
      "tenant_id": {
        "type": "keyword"
      },
      "author": {
        "type": "keyword"
      },
      "created_at": {
        "type": "date"
      },
      "updated_at": {
        "type": "date"
      },
      "status": {
        "type": "keyword",  -- ACTIVE, DELETED, ARCHIVED
        "enum": ["ACTIVE", "DELETED"]
      },
      "document_type": {
        "type": "keyword"  -- contract, invoice, nda
      }
    }
  }
}

-- Metadata DB (Postgres) — source of truth
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    snippet TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20),
    document_type VARCHAR(50),
    
    INDEX idx_owner_status (owner_id, status),
    INDEX idx_tenant_created (tenant_id, created_at DESC)
);

-- Autocomplete trie (Redis)
-- Key: "trie:{prefix}"
-- Value: sorted set of suggestions with popularity scores
ZADD trie:contr 100 "contract" 80 "contractor" 60 "contracting"
-- Fetch top 10 with score: ZRANGE trie:contr 0 10 WITHSCORES
```

---

## Section 10 — ⚠️ Trade-Offs + Failure Modes (Minutes 45–52)

### Trade-off 1: Full-Text Search vs Metadata-Only

**Chose:** Metadata + title/snippet only (no full PDF content search).

**Gain:** Index size 22 KB per doc (manageable); doesn't require OCR; user can click and read full doc.

**Lose:** Can't search inside PDF content; users must remember title/keywords, not content phrases.

**Failure mode if wrong:** If you index full PDF content (100 KB per doc), 50M docs × 100 KB = 5 TB index. Elasticsearch cluster becomes expensive ($50K+/month). Queries slow (must search through 5TB index). Not worth it. **Business impact:** At 5 TB, full-text search queries degrade from 200ms to 5+ seconds — for DocuSign's eDiscovery use case (a law firm searching all contracts for a specific clause), the search tool becomes too slow for production use, the legal team switches to manual review, and DocuSign loses a key enterprise feature differentiation against competitors like Adobe Sign.

---

### Trade-off 2: Eventual Consistency (1-2 sec lag) vs Immediate Consistency

**Chose:** Eventual consistency (1-2 second lag acceptable).

**Gain:** Async indexing (outbox pattern) is fast; no blocking writes; scalable.

**Lose:** New documents not searchable for 1-2 seconds; users might be surprised.

**Failure mode if wrong:** If you synchronously index (write to Elasticsearch before returning to user), indexing failures block user requests. If Elasticsearch is down, document creation fails. Availability drops. **Business impact:** For DocuSign: if Elasticsearch is unavailable during synchronous indexing, envelope creation fails with a 500 — a sender trying to get a contract out before a fiscal quarter deadline cannot create the envelope at all — the core DocuSign action (send envelope) is broken for the entire duration of any Elasticsearch maintenance window, making search infrastructure a hard dependency on the write path.

---

### Trade-off 3: Cache TTL (5 min vs 1 hour vs no cache)

**Chose:** 5-minute TTL.

**Gain:** Hit rate 80% (most searches repeated within 5 min); documents visible within 5 min of creation.

**Lose:** Results can be stale; if ranking algorithm changed, users see old rankings for 5 minutes.

**Failure mode if wrong:** If TTL is 1 hour, stale ranking changes confuse users ("why did this result drop?"). If no cache, Elasticsearch overloaded (50% CPU at peak). Need 10× more infrastructure cost. **Business impact:** Without cache, a 2× search burst (a law firm running bulk eDiscovery across 100K contracts) pushes Elasticsearch to 100% CPU, all search queries time out, and DocuSign's contract search is completely down during the firm's discovery deadline — a legal crisis that escalates to VP support and risks the enterprise contract renewal.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 52–57)

**Why this question is on the SDE-3 interview:**

Search is fundamental to any document platform. DocuSign customers must find their contracts quickly (often for compliance audits or renegotiations). A senior engineer must understand: how to rank by relevance, how to scale to 50M documents, how to cache efficiently, and how to keep index fresh without sacrificing performance.

**DocuSign-specific angles:**

1. **Legal compliance in search results**: Deleted documents (status = DELETED) must not appear in search results (filter them out with `status: ACTIVE`). Legal holds must still appear (separate permission check).

2. **Multi-party visibility**: A contract with 5 signers must appear in all 5 users' searches, but each user should only see their own contracts + shared ones (filter by owner_id OR shared_with).

3. **Audit trail of searches**: For compliance, log every search query + results returned. This satisfies GDPR "data subject access request" (show what documents were found for this user).

4. **Recency ranking**: Often users want to find recently signed contracts (sort by created_at DESC). Relevance + recency is the right trade-off.

5. **Autocomplete for party names**: When searching for a contract with "Acme Corp," autocomplete should suggest "Acme Corp", "Acme Corporation", "ACME INC" (typo tolerance + synonyms). This requires additional ETL or fuzzy matching.

**Your answer should include:**

> "Search is eventual-consistent (1-2 second lag acceptable). When a document is created, the Document Service publishes to Kafka. An async indexing processor consumes, extracts title + snippet, and indexes to Elasticsearch (50 shards, 3 replicas). Each query is cached in Redis (5-min TTL, 80% hit rate); cache miss hits Elasticsearch (BM25 ranking). Results filter by status = ACTIVE (compliance) and owner_id (access control). Ranking is: BM25 relevance + recency (created_at DESC). At 3.5K QPS with caching, latency is < 100ms."

> "For DocuSign: deleted documents (status = DELETED) are invisible in search. Legal-hold documents are visible to legal teams only (separate RBAC). Every search is logged for audit trail (compliance requirement). Autocomplete suggests party names with typo tolerance (Elasticsearch fuzziness: 1 edit distance)."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 57–60)

### Tier 1 — Surface Probe

**Q: "Why BM25 and not TF-IDF?"**

> BM25 accounts for document length and term saturation. In TF-IDF, a document with the word "contract" 100 times ranks artificially high. BM25 applies diminishing returns: the 1st "contract" is worth 1.0, the 5th is worth 0.8, the 10th is worth 0.5. It prevents spam. Also, Elasticsearch uses BM25 by default, so no extra implementation cost.

### Tier 2 — Deep Probe

**Q: "If Elasticsearch cluster fails (all 50 shards down), what happens to search? How do you recover?"**

> Search is unavailable (return 503). Recovery: (1) Elasticsearch cluster is HA (3 replicas per shard), so full cluster failure is rare; (2) if persistent, rebuild index from Postgres: re-read all 50M documents, re-index to fresh ES cluster (takes ~30 min); (3) during rebuild, search returns 503; (4) after rebuild, search is back online. This is why metadata DB is source of truth, not Elasticsearch.

### Tier 3 — Cross-Concept Probe

**Q: "Your cache has 5-minute TTL. Document ranking algorithm changes, and you want results to reflect new ranking immediately. How do you balance cache freshness with performance?"**

> You can't have both (consistency + performance trade-off). Options: (1) Set TTL to 30 sec (higher freshness, but hit rate drops to 60%, ES load increases); (2) Cache-versioning: include algorithm version in cache key (changes invalidate all old keys, but requires deploying version bump); (3) Smart invalidation: when ranking algo changes, clear Redis cache in background job (1-2 min delay, acceptable for ranking changes). I'd choose (3) — set a background job to `FLUSHDB` Redis after deploying new ranking, staggered over 5 min to avoid thundering herd.

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "If 5 of your 50 shards go down (primary + all replicas), search for documents in those shards returns no results. The user searches for 'contract' and gets 90% of their documents, missing 10%. How do you handle partial results without failing silently?"**
> Elasticsearch includes shard health in every response: `"_shards": {"total": 50, "successful": 45, "failed": 5}`. My API layer checks this field on every response.
>
> If `_shards.failed > 0`:
> 1. Return the partial results (do NOT fail the whole request with a 503 — partial results > blank page)
> 2. Add a warning field to the API response: `"warning": "Results may be incomplete — 5 of 50 index shards are temporarily unavailable"`
> 3. Alert ops team via PagerDuty (shard failure is a P2 incident)
>
> Elasticsearch's default behavior (`allow_partial_search_results: true`) already returns partial results. I make sure my API surfaces the warning rather than hiding it.
>
> **Why not fail with 503?** A user searching for a contract to sign would rather see 45 of 50 results with a warning than a complete failure. Availability of partial results > perfect consistency when stakes are search, not booking or payment.

---

**Q: "How do you prevent user A from seeing user B's documents in search results? Both have contracts that contain the word 'NDA.'"**
> Two-layer defense — Elasticsearch query filter + centralized query builder:
>
> **Layer 1 — Elasticsearch filter clause** (not a query clause): every search query includes a `filter` on `owner_id` and `tenant_id`. Elasticsearch evaluates filters BEFORE scoring — documents not matching the filter never reach the ranking phase:
> ```json
> {
>   "query": {
>     "bool": {
>       "must": [{"match": {"title_or_snippet": "NDA"}}],
>       "filter": [
>         {"term": {"owner_id": "user-A-uuid"}},
>         {"term": {"tenant_id": "tenant-123"}}
>       ]
>     }
>   }
> }
> ```
>
> **Layer 2 — Centralized query builder** (no bypass possible): The filter is not optional code that each controller adds. There's ONE method `SearchQueryBuilder.buildSecureQuery(userId, tenantId, userInput)` that always adds the ownership filter. No controller can call the ES client directly — it must go through this builder. Code review enforces it.
>
> **Shared documents** (user A shares doc with user B): The index includes an `authorized_user_ids` array field. Filter becomes `{"terms": {"authorized_user_ids": ["user-A-uuid"]}}`.
>
> **In an interview:** "The ownership filter is in the ES filter clause (not the scoring query), so it's both fast (cached, doesn't affect BM25 scoring) and mandatory (centralized builder, no bypass). This makes cross-user leakage structurally impossible, not just policy-enforced."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "A DocuSign customer uploads 100,000 documents in one day (bulk import). Your async indexing has a 1-2 second lag per document. How does the indexing service handle the sudden backlog without falling behind permanently?"**
> With 100K documents arriving rapidly, the Kafka topic `documents.indexed` accumulates a backlog. The single-consumer Indexing Processor at 100 docs/sec would take ~17 minutes to clear 100K documents — acceptable. But at 1M documents (enterprise migration), it's 3 hours of lag.
>
> **Horizontal scaling via Kafka consumer groups:** The Indexing Processor is a Kafka consumer group. Multiple instances share the topic's 50 partitions. With 10 Processor instances, each handles 5 partitions → throughput scales 10× to 1,000 docs/sec → 100K document backlog clears in 100 seconds.
>
> **Throttle against Elasticsearch capacity:** Elasticsearch can handle ~5,000 bulk index operations/sec. If Indexing Processors are too aggressive (e.g., 50 instances), they overwhelm ES and cause write rejections. The fix: tune consumer group size to keep ES load at ~70% capacity.
>
> **Bulk API:** Rather than indexing one document per Elasticsearch request (expensive: one HTTP RTT per doc), Indexing Processor batches 100 documents per Elasticsearch `_bulk` API call — 100× fewer HTTP calls, same throughput.
>
> **In an interview:** "I'd scale the Kafka consumer group horizontally to match backlog size. The Indexing Processor is stateless — running 10 instances vs 1 is a single Kubernetes replica count change. Bulk API batching keeps ES overhead low. The key constraint is ES write capacity, not Kafka throughput."

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "I'll store full PDF content in Elasticsearch." → **Why it's wrong:** 50M docs × 100 KB = 5 TB index. Cost is $50K+/month. Queries become slow. → **What to say instead:** "Index metadata + title + first 500 chars of content (snippet). Full PDF is retrieved separately when user clicks."

- **Mistake 2:** "Every query writes to Elasticsearch synchronously." → **Why it's wrong:** If ES is slow (200ms write latency), document creation becomes slow. If ES is down, document creation fails. → **What to say instead:** "Async indexing via Kafka. Document write is fast (10ms to Postgres). Indexing happens separately (1-2 sec lag acceptable)."

- **Mistake 3:** "No caching — I'll just query Elasticsearch every time." → **Why it's wrong:** 3.5K queries/sec with 100ms latency each = overwhelming load. ES CPU hits 80%+. → **What to say instead:** "Redis cache with 5-min TTL. 80% hit rate reduces ES load to 700 QPS (comfortable). Latency drops to < 5ms on cache hits."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | BM25 ranking is deterministic (same query + same index = same score). Test: index a fixed dataset of 10 synthetic envelopes, verify ranking order for known query. Async indexing path testable with mock Kafka producer + mock ES client — no live cluster needed. Cache hit/miss testable with Redis mock. |
| **Usability** | ✅ | GET /search?q=contract&owner_id={id}&filter=status:ACTIVE&limit=10 returns {items, total_hint, latency_ms}. Autocomplete via Redis trie responds in < 5ms. Snippet shows the 150-char context around the matched term. For DocuSign: a legal assistant searching "employment agreement 2024" gets the right envelope ranked first, with a contextual snippet from the title/metadata. |
| **Extensibility** | ✅ | New searchable fields (custom_fields, template_name) added to ES mapping with `dynamic: false` — no reindex needed for new string fields. New ranking signals (days-to-expiry boost, recently viewed boost) added as ES `function_score` parameters with zero API changes. |
| **Security** | ✅ | Every ES query includes a mandatory `filter` clause on (owner_id + tenant_id) via `SearchQueryBuilder.buildSecureQuery()` — for DocuSign: cross-tenant search leaks would expose one customer's contract list to another; the centralized filter makes bypass impossible even if a controller forgets to add it. Status filter (WHERE status = 'ACTIVE') prevents deleted envelopes from appearing in results (soft-delete search consistency). |
| **Availability** | ✅ | ES cluster: 50 shards × 3 replicas — single shard failure triggers automatic replica promotion (< 60s). Redis cache serves stale results during ES degradation (acceptable: stale results > no results for users). At 3.5K QPS with 80% cache hit rate, only 700 queries/sec hit ES — a 2× traffic spike stays within ES capacity. |
| **Scalability** | ✅ | At 50M docs × 22 KB/doc = 1.1 TB index (Section 4), 50 shards distribute the index across the cluster — no single shard exceeds 22 GB. At 3.5K QPS (Section 4: 100M searches/day), Redis cache (80% hit rate) routes only 700 QPS to ES. For DocuSign's eDiscovery use case: a law firm searching across 100K contracts completes in < 200ms via index parallelism across 50 shards. |
| **Observability & Traceability** | ✅ | Every search logs (user_id, tenant_id, query_text, result_count, latency_ms, cache_hit, request_id). Dashboards: ES per-shard latency histogram (P99 < 200ms target), Redis cache hit rate (alert < 75%), indexing lag (alert > 5s). For DocuSign: "what did user X search for before accessing envelope Y?" is answerable in 200ms from the search query log — useful for legal discovery and compliance audits. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "Full-text search at 100M searches/day requires: (1) **Elasticsearch cluster** with 50 shards (hash-based routing by doc_id) for distributed indexing; (2) **BM25 ranking** (relevance + recency) to surface most relevant docs first; (3) **async indexing** via Kafka (outbox pattern) to keep document writes fast (1-2 sec index lag acceptable); (4) **Redis cache** with 5-min TTL (80% hit rate, < 5ms latency on cache hits); (5) **filters by owner_id + tenant_id** for access control; (6) **status filter** (ACTIVE only) for compliance. Trade-off: eventual consistency (1-2 sec lag) vs immediate consistency (would require sync writes, slow). At 3.5K QPS with 80% cache hit rate, only 700 queries hit Elasticsearch per second (comfortable load). Sharding by doc_id ensures even distribution and query parallelism across 50 shards."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **E1-search-system.md created.** Full 15-section solution framework for Type A System Design. Covers: Elasticsearch sharding (50 shards by hash), BM25 ranking (relevance + recency), async indexing via Kafka (outbox pattern), Redis caching (5-min TTL, 80% hit rate), query filtering (access control + compliance), autocomplete (Redis trie). Scale: 50M documents, 100M searches/day = 3.5K QPS peak. Prerequisites: `05-consistent-hashing.md`, `03-caching.md`, `11-api-design.md`. |
| Jul 4, 2026 | **4 new Q&As added to Section 12.** (1) **Partial shard failure — silent vs visible degradation** — check `_shards.failed` in ES response, return partial results + warning field rather than 503, alert ops; availability of partial results > complete failure for search; (2) **Cross-user search access control** — `filter` clause in ES bool query (not scoring query, cached) on `owner_id` + `tenant_id`; centralized `SearchQueryBuilder.buildSecureQuery()` makes filter mandatory — no controller bypasses possible; shared docs via `authorized_user_ids` array field; (3) **Bulk import backlog handling** — Kafka consumer group horizontal scaling (10 instances = 10× throughput); stateless Processor = Kubernetes replica count change; ES `_bulk` API batches 100 docs per HTTP call (100× fewer RTTs); tune consumer group size to keep ES at 70% capacity. |
| Jul 5, 2026 | **Section 6 restructured into 2-stage progressive HLD.** Stage 1 (Postgres full-text, GIN index + tsvector) — shows functional baseline for < 1M docs, identifies breaking point at 50M docs + 3.5K QPS (CPU saturation, no BM25, no autocomplete). Stage 2 (Elasticsearch + Kafka + Redis, production) — Kafka async decouples indexing from document creation; ES 50 shards by doc_id hash; Redis 5-min TTL absorbs 80% of queries. Three decision tables added: search backend (Postgres vs ES vs Solr — ES ✅), indexing transport (sync write vs Kafka async — Kafka ✅), sharding strategy (doc_id vs user_id vs date — doc_id ✅). Verdict alignment verified: all Section 6 table verdicts match Section 7 deep dive choices (doc_id sharding ✅, BM25 ✅, Redis cache-aside ✅). |
| Jul 5, 2026 | **Section 10 business impact + Section 14 DocuSign dimensions pass.** Section 10: added **Business impact:** to all 3 trade-offs — eDiscovery search degrading under load losing enterprise law firm accounts to Adobe Sign (Elasticsearch CPU), synchronous indexing making Elasticsearch a hard dependency on the envelope create write path (async trade-off cost), law firm eDiscovery burst driving 100% CPU with VP escalation during discovery deadline (capacity). Section 14: rewrote all 7 dimension cells — `SearchQueryBuilder.buildSecureQuery()` mandatory tenant filter preventing cross-tenant isolation failure (Security), 3.5K QPS with 80% Redis cache hit rate from Section 4 (Scalability), search query audit trail for compliance officer eDiscovery verification (Observability). |
