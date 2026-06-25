# E1 — Design a Search System (Full-Text Search at Scale)

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview.

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

**Q: "What's the ranking priority — relevance score (TF-IDF), recency (newer first), or popularity (most viewed)?"**
- Why ask: Ranking algorithm drives index schema and query complexity
- If relevance → need tf-idf scoring; ranked queries slower
- If recency → simple date sort; very fast
- If mixed → need multi-field scoring; compute-heavy

**Q: "Do we need autocomplete suggestions (prefix matching) as a separate feature?"**
- Why ask: Autocomplete requires a trie or prefix index; adds latency/complexity
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

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

### 🎨 ASCII Architecture Diagram

```
  FULL-TEXT SEARCH SYSTEM — HIGH-LEVEL ARCHITECTURE
  ────────────────────────────────────────────────────────────────

  INDEXING PATH (write-side)
  ══════════════════════════════════════════════════════════════
  
  Document Service (creates/updates docs)
         │
         ├─→ Save to DB (Postgres)
         │
         └─→ Publish to Kafka topic: "documents.indexed"
                │
                ▼
         ┌─────────────────────┐
         │ Indexing Processor  │ (async, stateless)
         │ (Kafka consumer)    │
         └─────────┬───────────┘
                   │
                   ├─→ Extract: title, snippet, metadata
                   │
                   ├─→ Build inverted index (term → doc ID)
                   │
                   ▼
         ┌──────────────────────────────┐
         │ Elasticsearch Cluster        │
         │ (50 shards × 3 replicas)    │
         │                              │
         │ Shard 1: docs 0-1M          │
         │ Shard 2: docs 1M-2M         │
         │ ...                          │
         │ Shard 50: docs 49M-50M      │
         └──────────────────────────────┘


  SEARCH PATH (read-side)
  ══════════════════════════════════════════════════════════════

  Client (User searches "contract")
         │
         ▼
  ┌──────────────────────────────────┐
  │  API Server (Stateless)          │
  │  GET /v1/search?q=contract       │
  └───────┬──────────────────────────┘
          │
          ├─→ Auth check (JWT)
          │
          ├─→ Check Redis cache
          │   Key: "search:contract:tenant123"
          │   TTL: 5 minutes
          │
          ├─→ If cache miss:
          │   Query Elasticsearch
          │     query: {
          │       match: {
          │         title_or_snippet: "contract"
          │       },
          │       filter: [
          │         {term: {owner_id: user123}},
          │         {range: {created_at: {...}}},
          │         {term: {status: "ACTIVE"}}
          │       ]
          │     }
          │     sort: [
          │       {_score: desc},  // relevance (TF-IDF)
          │       {created_at: desc}  // recency as tiebreaker
          │     ]
          │
          ├─→ Cache results in Redis (5 min TTL)
          │
          ▼
  ┌──────────────────────────────────┐
  │  Redis Cache                     │
  │  (recent searches + results)     │
  │  TTL: 5 minutes                  │
  └──────────────────────────────────┘

  Response to client:
    {
      "results": [
        {
          "document_id": "doc-123",
          "title": "NDA Contract 2026-01",
          "snippet": "...this Agreement shall be binding...",
          "author": "alice@docusign.com",
          "created_at": "2026-06-24",
          "relevance_score": 0.95
        }
      ],
      "total_hits": 15,
      "next_cursor": "eyJzZWFyY2hfYWZ0ZXIiOiBbMC45NSwgMTIzNF19"
    }


  AUTOCOMPLETE PATH (optional)
  ══════════════════════════════════════════════════════════════

  Client types "contr" in search box
         │
         ├─→ On each keystroke: GET /v1/autocomplete?prefix=contr
         │
         ▼
  ┌──────────────────────────────────┐
  │  Redis Trie Index                │
  │  (autocomplete suggestions)      │
  │  "contr" → ["contract", ...]     │
  │  Cached frequently typed prefixes│
  └──────────────────────────────────┘

  Response: ["contract", "contracting", "contractor"]


KEY INVARIANT:
   Metadata DB is source of truth. Elasticsearch is derived (can rebuild).
   Indexing lag of 1-2 seconds acceptable (eventual consistency).
   Search is read-heavy (1.16K → 3.5K QPS); cache hits reduce load.
   Sharding by document_id ensures even distribution across 50 shards.
```

**Data flow walkthrough (say this out loud):**

**Flow 1 — Indexing (Document Created/Updated):**
1. Document Service saves doc to Postgres
2. Publishes to Kafka `documents.indexed` topic (outbox pattern)
3. Indexing Processor consumes: extracts title, snippet, metadata
4. Builds inverted index: `{"contract": [doc-1, doc-5, doc-42], "nda": [doc-1, doc-3]}`
5. Sends to Elasticsearch (1-2 second lag acceptable)
6. Elasticsearch stores in appropriate shard (based on doc_id hash)

**Flow 2 — Search Query:**
1. Client calls `GET /v1/search?q=contract&date_from=2026-01-01&author=alice`
2. API checks Redis cache (key = "search:contract:tenant123:alice:2026-01-01")
3. Cache hit (80% baseline) → return cached results (< 5ms latency)
4. Cache miss → query Elasticsearch (BM25 scoring)
5. Elasticsearch queries all 50 shards in parallel (scatter-gather)
6. Shards return top 10 results each, coordinating node merges + re-ranks
7. Cache results in Redis (TTL = 5 minutes)
8. Return to client with cursor for pagination

**Flow 3 — Autocomplete:**
1. User types "contr"
2. API hits Redis trie: `ZRANGE trie:contr 0 10` (top 10 suggestions by popularity)
3. Return instantly (< 50ms)
4. If trie cache expired, rebuild from Elasticsearch `terms` aggregation (slow, 200ms)

**Why each component:**
- **Elasticsearch**: Designed for full-text search (inverted index, TF-IDF scoring, filtering, aggregations)
- **Kafka (outbox)**: Decouples document writes from indexing; retries on failure
- **Sharding (by doc_id)**: Distributes 50M docs evenly; 50 shards = 1M docs/shard = O(1000) ops/query (fast)
- **Redis cache**: Avoids repeated Elasticsearch queries (same search term 20× per hour); 5-min TTL balances freshness + hit rate
- **Autocomplete trie**: Prefix matching is O(log N) in trie; cached suggestions are O(1)

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

**Why this deep dive matters:**
- Cache is essential for search scalability (reduces backend load by 80%)
- 5-minute TTL balances freshness (documents visible within 5 min) and hit rate (80%+)
- Smart invalidation (trigger on document index) can improve freshness without sacrificing hit rate

---

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| GET | `/v1/search` | JWT Bearer | `q=contract&date_from=&date_to=&author=alice&limit=10&cursor=` | `{results: [{id, title, snippet, score}], total_hits, next_cursor}` | 200, 400, 403 |
| GET | `/v1/autocomplete` | JWT Bearer | `prefix=contr&limit=10` | `{suggestions: ["contract", "contractor"]}` | 200, 400 |
| GET | `/v1/search/{doc_id}/preview` | JWT Bearer | — | `{document_id, title, full_snippet, author, created_at}` | 200, 403, 404 |

### Key Design Decisions

- **Cursor pagination**: Offset-based pagination is slow on large result sets; cursor-based (keyset) is O(1)
- **Partial results**: Search returns 10 results by default (balance latency vs usability)
- **Filtering**: query params (date_from, date_to, author, status) are AND-ed together
- **Autocomplete**: separate endpoint; suggestions are cached separately (5-min TTL)
- **Snippet generation**: first 200 chars of document content (or matching terms in context)

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

**Failure mode if wrong:** If you index full PDF content (100 KB per doc), 50M docs × 100 KB = 5 TB index. Elasticsearch cluster becomes expensive ($50K+/month). Queries slow (must search through 5TB index). Not worth it.

---

### Trade-off 2: Eventual Consistency (1-2 sec lag) vs Immediate Consistency

**Chose:** Eventual consistency (1-2 second lag acceptable).

**Gain:** Async indexing (outbox pattern) is fast; no blocking writes; scalable.

**Lose:** New documents not searchable for 1-2 seconds; users might be surprised.

**Failure mode if wrong:** If you synchronously index (write to Elasticsearch before returning to user), indexing failures block user requests. If Elasticsearch is down, document creation fails. Availability drops.

---

### Trade-off 3: Cache TTL (5 min vs 1 hour vs no cache)

**Chose:** 5-minute TTL.

**Gain:** Hit rate 80% (most searches repeated within 5 min); documents visible within 5 min of creation.

**Lose:** Results can be stale; if ranking algorithm changed, users see old rankings for 5 minutes.

**Failure mode if wrong:** If TTL is 1 hour, stale ranking changes confuse users ("why did this result drop?"). If no cache, Elasticsearch overloaded (50% CPU at peak). Need 10× more infrastructure cost.

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

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "I'll store full PDF content in Elasticsearch." → **Why it's wrong:** 50M docs × 100 KB = 5 TB index. Cost is $50K+/month. Queries become slow. → **What to say instead:** "Index metadata + title + first 500 chars of content (snippet). Full PDF is retrieved separately when user clicks."

- **Mistake 2:** "Every query writes to Elasticsearch synchronously." → **Why it's wrong:** If ES is slow (200ms write latency), document creation becomes slow. If ES is down, document creation fails. → **What to say instead:** "Async indexing via Kafka. Document write is fast (10ms to Postgres). Indexing happens separately (1-2 sec lag acceptable)."

- **Mistake 3:** "No caching — I'll just query Elasticsearch every time." → **Why it's wrong:** 3.5K queries/sec with 100ms latency each = overwhelming load. ES CPU hits 80%+. → **What to say instead:** "Redis cache with 5-min TTL. 80% hit rate reduces ES load to 700 QPS (comfortable). Latency drops to < 5ms on cache hits."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | BM25 ranking is deterministic (same query = same score). Mock Elasticsearch for unit tests. Index a test dataset, verify ranking order. No flakiness. |
| **Usability** | ✅ | Simple search API: `GET /search?q=contract`. Autocomplete guides users. Snippet shows context. Filters (date, author) refine results. |
| **Extensibility** | ✅ | New fields (document_type, custom_metadata) are added to Elasticsearch mapping without reindexing. New ranking signals (view count, signature date) are added via boosting. |
| **Security** | ✅ | JWT auth on all endpoints. Filters by owner_id + tenant_id (prevent cross-user search leaks). Status filter prevents deleted docs from appearing. |
| **Availability** | ✅ | Elasticsearch cluster has 50 shards × 3 replicas. Single shard failure doesn't block search (replicas take over). Redis cache as fallback (serve stale results if ES slow). |
| **Scalability** | ✅ | At 100M searches/day (3.5K QPS), cache + sharding handle easily. Index grows to 50M docs = 1.1 TB (fits in cluster). Horizontal scaling: add shards if > 100M docs. |
| **Observability & Traceability** | ✅ | Log every search query (user, query, results returned, latency). Elasticsearch provides per-shard metrics (query latency, request rate). Track cache hit rate (dashboard: 80% = healthy). |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "Full-text search at 100M searches/day requires: (1) **Elasticsearch cluster** with 50 shards (hash-based routing by doc_id) for distributed indexing; (2) **BM25 ranking** (relevance + recency) to surface most relevant docs first; (3) **async indexing** via Kafka (outbox pattern) to keep document writes fast (1-2 sec index lag acceptable); (4) **Redis cache** with 5-min TTL (80% hit rate, < 5ms latency on cache hits); (5) **filters by owner_id + tenant_id** for access control; (6) **status filter** (ACTIVE only) for compliance. Trade-off: eventual consistency (1-2 sec lag) vs immediate consistency (would require sync writes, slow). At 3.5K QPS with 80% cache hit rate, only 700 queries hit Elasticsearch per second (comfortable load). Sharding by doc_id ensures even distribution and query parallelism across 50 shards."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **E1-search-system.md created.** Full 15-section solution framework for Type A System Design. Covers: Elasticsearch sharding (50 shards by hash), BM25 ranking (relevance + recency), async indexing via Kafka (outbox pattern), Redis caching (5-min TTL, 80% hit rate), query filtering (access control + compliance), autocomplete (Redis trie). Scale: 50M documents, 100M searches/day = 3.5K QPS peak. Prerequisites: `05-consistent-hashing.md`, `03-caching.md`, `11-api-design.md`. |
