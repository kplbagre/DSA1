# Elasticsearch — Full-Text Search & Inverted Index

> Elasticsearch is a search engine that uses **inverted indexes** to enable fast full-text queries. Instead of scanning entire documents (slow), Elasticsearch indexes every word and stores which documents contain it, allowing O(1) lookups. At SDE 3: you must know inverted index mechanics, sharding strategy, and when Elasticsearch is overkill.

---

## 🎯 Why This Matters

Google searches "machine learning" and returns 1M results in 100ms. Without indexing, Google would scan billions of documents (hours). Inverted index lets Google instantly find all documents containing "machine" AND "learning". Elasticsearch powers search for Amazon (products), GitHub (code), Slack (messages), ELK stack (logs). In interviews, candidates often think Elasticsearch is "just a database" — you'll explain the inversion and sharding strategy.

---

## 📖 What is Elasticsearch & Inverted Index? (Basics)

**Elasticsearch** = a search engine (like Google for your data) that finds documents instantly using indexes.

**Inverted Index** = a lookup table that maps words to documents (opposite of a normal index).

**Normal index (slow):**
```
Document 1: "Hello world"
Document 2: "World of data"
Document 3: "Hello data"

To search "data": scan all documents one by one → slow
```

**Inverted index (fast):**
```
"hello" → [Document 1, Document 3]
"world" → [Document 1, Document 2]
"data" → [Document 2, Document 3]

To search "data": look up in table → instant O(1)
```

**Simple analogy:** 
- Normal approach: Read every book cover-to-cover looking for a word.
- Inverted index: Book has an index at the back: "word → pages 5, 23, 127". Look up word in index → jump straight to pages.

Elasticsearch pre-builds this inverted index for all documents, making searches instant.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Inverted Index** | lookup table mapping each word → list of documents containing it; enables O(1) word lookup | `"distributed"` → `[doc3, doc7, doc12]` — no scan needed |
| **Document** | the unit indexed in Elasticsearch; equivalent to a row in a DB | `{id: 1, title: "Java Guide", body: "..."}` |
| **Index (Elasticsearch)** | a named collection of documents with a shared mapping; like a database table | `products` index holds all product documents |
| **Shard (Elasticsearch)** | a subset of an index stored on one node; horizontal partition for write and read scale | `products` index split into 5 shards → distributed across 5 nodes |
| **Replica Shard** | copy of a primary shard on a different node; provides read scale and fault tolerance | each primary shard has 1 replica → shard data survives one node failure |
| **Analyzer** | pipeline that processes text before indexing: tokenize, lowercase, remove stop words, stem | `"Running Fast"` → tokenizer → `["running", "fast"]` → stems → `["run", "fast"]` |
| **Relevance Score (BM25)** | numerical score ranking how well a document matches a query; higher = more relevant | query `"java"` → `{doc1: 0.9, doc3: 0.4}` → doc1 ranked first |
| **ELK Stack** | Elasticsearch + Logstash (ingest) + Kibana (visualize); the classic log analytics trio | centralized logging: Logstash collects logs → ES stores/indexes → Kibana dashboards |
| **Near Real-Time (NRT)** | indexed documents are searchable within ~1 second of being written (not instant) | document written at T=0; searchable at T≈1s after segment refresh |

---

## 🧠 The Mental Model

Imagine a library catalog:

**Without inverted index (linear scan):**
- User asks: "Find all books mentioning 'distributed systems'."
- Librarian reads every book cover-to-cover, checking if it mentions the topic.
- With 1M books, this takes hours.

**With inverted index:**
- Librarian maintains an index: "distributed systems" → [Book123, Book456, Book789, ...].
- User asks the same question.
- Librarian looks up the index: instant result (already pre-computed which books cover the topic).
- Librarian can also answer: "distributed systems AND networking" (find books in both lists, intersect).

**The key insight:** Inverted index trades **upfront indexing cost** (slower writes) for **instant query time** (fast reads).

---

## 🎨 Visual — Elasticsearch Architecture

### Full System Topology — Where Elasticsearch Fits

```
APPLICATION SERVICES
    ↓ (write: log events, documents)
    ↓ (read: full-text search)
┌──────────────────────────────────────────────────────┐
│ LOGSTASH / BEATS (Log shippers)                      │
│ Collect logs/events from applications                │
│ Parse (JSON, multiline, regex patterns)              │
│ Enrich (add tags, hostnames, timestamps)             │
└──────────────────────────────────────────────────────┘
    ↓ (bulk index: 1000s events/sec)
┌──────────────────────────────────────────────────────┐
│ ELASTICSEARCH CLUSTER                                │
│ ┌──────────────────────────────────────────────┐    │
│ │ Node 1 (Master eligible)                     │    │
│ │ ┌────────────────────────────────────────┐  │    │
│ │ │ Shard 0 (Primary)                      │  │    │
│ │ │ Inverted index: word → doc IDs         │  │    │
│ │ │ "error" → [1, 5, 23, 102, ...]        │  │    │
│ │ └────────────────────────────────────────┘  │    │
│ ├──────────────────────────────────────────────┤    │
│ │ Shard 1 (Primary)                           │    │
│ │ (stores different document subset)          │    │
│ └──────────────────────────────────────────────┘    │
│                                                      │
│ ┌──────────────────────────────────────────────┐    │
│ │ Node 2 (Replica)                             │    │
│ │ ┌────────────────────────────────────────┐  │    │
│ │ │ Shard 0 (Replica of Node1:Shard0)     │  │    │
│ │ │ Exact copy of inverted index           │  │    │
│ │ └────────────────────────────────────────┘  │    │
│ └──────────────────────────────────────────────┘    │
│                                                      │
│ Cluster state: 2 nodes, 2 shards, 1 replica        │
│ Replication: Node1:Shard0 → Node2:Shard0           │
└──────────────────────────────────────────────────────┘
    ↓ (read: search queries)
┌──────────────────────────────────────────────────────┐
│ SEARCH REQUESTS (Kibana UI or API)                   │
│ Query: GET /_search?q=error&status=critical          │
│ Elasticsearch:                                       │
│  1. Query hits both shards (distributed search)      │
│  2. Each shard uses inverted index (fast lookup)     │
│  3. Results merged, sorted by relevance              │
│  4. Top 10 returned                                  │
└──────────────────────────────────────────────────────┘

KEY INVARIANT:
   Elasticsearch cluster = multiple shards distributed across nodes.
   Each shard = inverted index (word → doc IDs mapping).
   Replicas = copies of shards (redundancy + read scaling).
   Search query hits all shards in parallel, results merged.
```

### Component Detail — Inverted Index & Query Execution

```
INVERTED INDEX STRUCTURE:

Raw documents (write DB):
┌────────────────────────────────────────┐
│ Doc ID: 1                              │
│ Text: "distributed database systems"   │
├────────────────────────────────────────┤
│ Doc ID: 2                              │
│ Text: "distributed systems programming"
├────────────────────────────────────────┤
│ Doc ID: 3                              │
│ Text: "database queries and design"    │
└────────────────────────────────────────┘

Elasticsearch creates INVERTED INDEX:
┌──────────────────────────────────┐
│ Word     → Doc IDs               │
├──────────────────────────────────┤
│ distributed → [1, 2]             │
│ database    → [1, 3]             │
│ systems     → [1, 2]             │
│ programming → [2]                │
│ queries     → [3]                │
│ and         → [3]                │
│ design      → [3]                │
└──────────────────────────────────┘

QUERY: "distributed systems" (AND query)
Step 1: Look up "distributed" in inverted index → [1, 2]
Step 2: Look up "systems" in inverted index → [1, 2]
Step 3: Intersect → [1, 2]
Result: Documents 1 and 2 match (O(n) where n = docs, not O(m*n) where m = terms)

QUERY: "distributed systems" (OR query)
Step 1: Look up "distributed" → [1, 2]
Step 2: Look up "systems" → [1, 2]
Step 3: Union → [1, 2]
Result: Same (both AND and OR match all)

QUERY: "distributed AND NOT programming"
Step 1: "distributed" → [1, 2]
Step 2: "programming" → [2]
Step 3: [1, 2] - [2] = [1]
Result: Document 1 only


SHARDING (Horizontal scaling):

Cluster with 2 shards:
┌──────────────────────────────────┐
│ Shard 0 (Node 1)                 │
│ Documents: 1-500                 │
│ Inverted index: word → [1..500]  │
│ Size: 500MB                      │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│ Shard 1 (Node 2)                 │
│ Documents: 501-1000              │
│ Inverted index: word → [501..1000]
│ Size: 500MB                      │
└──────────────────────────────────┘

Query: "distributed"
Elasticsearch queries BOTH shards:
  Shard 0: word → [1, 2, 23] + [other matches]
  Shard 1: word → [501, 523, 701] + [other matches]
Merge results: [1, 2, 23, 501, 523, 701, ...]
Total latency: max(Shard0, Shard1) + merge (parallel execution)

Without sharding (single shard):
  Query hits 1000MB inverted index (slow)
With sharding (2 shards):
  Query hits 2 × 500MB in parallel (2x faster)


REPLICATION (Redundancy + read scaling):

Primary Shard 0 (Node A):
  Inverted index for docs 1-500
  
Replica Shard 0 (Node B):
  Exact copy of Shard 0
  Read-only (from Elasticsearch client perspective)
  
Write: comes to Primary Shard 0
  → replicated to Replica Shard 0
  → both in sync
  
Read (search query):
  Can hit either Primary or Replica (load balanced)
  If Node A dies, Node B still serves Shard 0 reads

KEY INVARIANT:
   Inverted index enables O(1) word lookup (hash table lookup).
   Sharding distributes index across nodes (parallel query execution).
   Replication provides redundancy (read scaling + failover).
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Document arrives** (log line, product entry, etc.).
2. **Elasticsearch parses and analyzes** text (tokenization, lowercasing, stemming).
3. **Elasticsearch builds inverted index** (word → document IDs).
4. **Index is stored** on disk (sharded and replicated).
5. **Client sends search query** (full-text search or filters).
6. **Elasticsearch hits inverted index** (fast word lookup).
7. **Results are scored** by relevance (TF-IDF, BM25).
8. **Results sorted and returned** to client.

```java
// Elasticsearch: Indexing and Querying

@Configuration
public class ElasticsearchConfig {
    @Bean
    public RestClient restClient() {
        return RestClient.builder(
            new HttpHost("localhost", 9200, "http")
        ).build();
    }
}

// Indexing (Write side)

@Service
public class LogIndexingService {
    @Autowired
    private RestClient restClient;

    // Step 1-4 — Index a log document
    public void indexLog(LogEntry logEntry) throws IOException {
        // Step 1 — Create document
        Map<String, Object> document = Map.of(
            "timestamp", logEntry.getTimestamp(),
            "level", logEntry.getLevel(),        // ERROR, WARN, INFO
            "message", logEntry.getMessage(),    // "Connection timeout to database"
            "service", logEntry.getService(),    // "payment-service"
            "tags", logEntry.getTags()           // ["production", "critical"]
        );

        // Step 2 — Elasticsearch analyzes text
        // "Connection timeout to database" → tokenizes → 
        //   ["connection", "timeout", "to", "database"]
        // → lowercases, removes stopwords, stems
        // → ["connect", "timeout", "database"]

        // Step 3 — Build inverted index (automatic)
        // "connect" → [doc1, doc5, doc12, ...]
        // "timeout" → [doc1, doc7, ...]
        // "database" → [doc1, doc3, doc15, ...]

        // Step 4 — Index document (HTTP PUT)
        String indexName = "logs-" + getDateIndex();  // logs-2026-06-25
        String documentId = UUID.randomUUID().toString();

        Request request = new Request("PUT", "/" + indexName + "/_doc/" + documentId);
        request.setJsonEntity(objectMapper.writeValueAsString(document));

        Response response = restClient.performRequest(request);
        // Document stored in shard, replicated to replica nodes
    }

    private String getDateIndex() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(new Date());
    }
}

// Searching (Read side)

@Service
public class LogSearchService {
    @Autowired
    private RestClient restClient;

    // Step 5-8 — Full-text search
    public SearchResults search(SearchQuery query) throws IOException {
        // Step 5 — Prepare search query
        String searchBody = buildQuery(query);

        // Step 6 — Execute search (hits inverted index)
        Request request = new Request("GET", "/logs-*/_search");
        request.setJsonEntity(searchBody);

        Response response = restClient.performRequest(request);

        // Step 7-8 — Parse and return results
        return parseResults(response);
    }

    // Query: find all ERROR logs from payment-service containing "timeout"
    private String buildQuery(SearchQuery query) {
        Map<String, Object> queryDsl = Map.of(
            "query", Map.of(
                "bool", Map.of(
                    "must", List.of(
                        Map.of("term", Map.of("level", "ERROR")),     // Exact match
                        Map.of("term", Map.of("service", "payment-service")),
                        Map.of("match", Map.of("message", "timeout"))  // Full-text (inverted index)
                    )
                )
            ),
            "size", 10,
            "sort", List.of(Map.of("timestamp", "desc"))
        );

        return objectMapper.writeValueAsString(queryDsl);
    }

    // Step 6 — Query execution (internal to Elasticsearch):
    // 1. Look up "error" in inverted index → [doc1, doc3, doc5, ...]
    // 2. Look up "timeout" in inverted index → [doc1, doc7, ...]
    // 3. Filter by service="payment-service" → [doc1]
    // 4. Score doc1 by relevance (TF-IDF)
    // 5. Sort by timestamp (desc)
    // 6. Return top 10

    private SearchResults parseResults(Response response) {
        // Parse JSON response:
        // {
        //   "hits": {
        //     "total": {"value": 42},
        //     "hits": [
        //       {
        //         "_id": "doc1",
        //         "_score": 5.2,
        //         "_source": {timestamp, level, message, service, tags}
        //       },
        //       ...
        //     ]
        //   }
        // }
        return new SearchResults();  // Simplified
    }
}

// Index Mapping (schema definition)

@Configuration
public class ElasticsearchIndexConfig {
    @Autowired
    private RestClient restClient;

    @PostConstruct
    public void createIndexMapping() throws IOException {
        // Step 4 — Define index mapping (schema for documents)
        String mapping = """
        {
          "settings": {
            "number_of_shards": 2,        # 2 shards (horizontal scale)
            "number_of_replicas": 1       # 1 replica per shard (redundancy)
          },
          "mappings": {
            "properties": {
              "timestamp": {
                "type": "date"            # Sortable, filterable
              },
              "level": {
                "type": "keyword"         # Exact match (no inverted index)
              },
              "message": {
                "type": "text",           # Full-text (inverted index applied)
                "analyzer": "standard"    # Tokenize, lowercase, stem
              },
              "service": {
                "type": "keyword"
              },
              "tags": {
                "type": "keyword"
              }
            }
          }
        }
        """;

        Request request = new Request("PUT", "/logs-template");
        request.setJsonEntity(mapping);
        restClient.performRequest(request);
    }
}
```

### Refresh Interval — Why ES Is Not "Real-Time"

A frequently missed interview gotcha: Elasticsearch does **not** make documents searchable immediately after indexing. By default there is a **1-second refresh interval** — the index is refreshed (made queryable) once per second. Between refreshes, a just-indexed document is not visible in search results.

```
Timeline with default 1s refresh interval:
  t=0ms:   POST /logs/_doc {"message": "timeout error"}  → indexed in-memory segment
  t=0ms:   GET  /logs/_search?q=timeout → 0 results  ← not yet refreshed!
  t=1000ms: ES auto-refreshes index (in-memory → searchable)
  t=1001ms: GET  /logs/_search?q=timeout → 1 result ✅

Force immediate refresh (for testing or critical writes):
  POST /logs/_doc?refresh=true {"message": "timeout error"}
  → synchronously refreshes before returning → document immediately searchable
  → ⚠️ Expensive at scale: forces a full refresh per document write
```

**Configuration options:**

```json
// Index setting: increase refresh interval for bulk-write workloads
// (fewer refreshes = higher write throughput)
PUT /logs/_settings
{
  "index": {
    "refresh_interval": "5s"
  }
}

// Disable refresh entirely during bulk indexing, then re-enable
PUT /logs/_settings { "index": { "refresh_interval": "-1" } }
// ... bulk index millions of docs ...
PUT /logs/_settings { "index": { "refresh_interval": "1s" } }
```

**Interview phrasing:** *"Elasticsearch is near-real-time, not real-time. Documents are searchable after the next refresh cycle — 1 second by default. For log analytics this is fine. If a use case needs immediate visibility, use `?refresh=true` on the write request, but use this sparingly — it's expensive at high write rates. For bulk load scenarios, disable refresh entirely, load, then re-enable."*

### What is TF-IDF / BM25 Scoring, and why does it fit here?

TF-IDF and BM25 are **relevance scoring algorithms**. TF = term frequency (how often does "timeout" appear in this doc?), IDF = inverse document frequency (how rare is "timeout" across all docs?). Documents where rare terms appear highly are ranked first. In an interview, if asked: *"BM25 is Elasticsearch's default scoring algorithm. It combines term frequency (how often keyword appears in doc) and document frequency (how rare is the keyword). Docs with high-frequency, rare keywords rank highest in search results."*

---

## 🏢 Real World — Where Companies Use This

- **Amazon (Product search):** Elasticsearch powers search bar. User types "laptop" → Elasticsearch queries inverted index across millions of products. Sharded by region (US, Europe, Asia). Results returned in <100ms.
- **GitHub (Code search):** Users search code across billions of files. Elasticsearch inverted index indexes all code. Sharding by repository size.
- **Slack (Message search):** Users search chat history. Elasticsearch indexes every message (words, reactions, timestamps). Sharding by workspace/channel.
- **Netflix (Content discovery):** Elasticsearch powers "show recommendations" search. Indexes titles, descriptions, genres, actors. Complex queries combine multiple fields.
- **ELK Stack (Log aggregation):** Elasticsearch + Logstash + Kibana is industry standard for centralized logging. Logstash ships logs, Elasticsearch indexes, Kibana visualizes.

---

## 🧭 When to Use vs When NOT to Use

| Use Elasticsearch when | Do NOT use when |
|---|---|
| Full-text search needed (Google-like) | Simple key-value lookups (Redis sufficient) |
| Complex filtering (multi-field queries) | Single-field queries (SQL index sufficient) |
| Relevance scoring matters (rank by relevance) | Exact match only (database query fine) |
| High write volume (millions of logs/sec) | Occasional writes |
| Distributed search across large datasets | Small dataset (<100GB) on single server |

**The common mistake:** Using Elasticsearch as a primary database.

> **⚠️ Interview anti-pattern — always call this out explicitly:**
> Elasticsearch has no ACID transactions, no foreign keys, no `UPDATE` semantics (updates are delete + reindex internally), and documents can be lost during shard rebalancing if replicas are misconfigured. It is a **search index, not a database**.
>
> **Correct pattern:** Write to primary DB (Postgres/MySQL) first. Sync to Elasticsearch asynchronously (via CDC/Outbox or dual-write). Read from Elasticsearch for search; read from primary DB for authoritative state. If ES goes down, your data is safe in the DB — just search degrades.
>
> In an interview, as soon as you add Elasticsearch to your design, immediately say: *"ES is a secondary search index — the source of truth stays in Postgres. We sync via CDC (Debezium) or the outbox pattern."*

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Fast full-text search (inverted index O(1) lookups). Relevance scoring (rank by match quality). Complex aggregations (faceted search). Distributed queries (parallel shards). Real-time indexing (index and search immediately). |
| **You lose** | Upfront indexing cost (build inverted index for every document). Memory overhead (index can be 10-50% of raw data size). Eventual consistency (updates take milliseconds to propagate to all shards). Operational complexity (cluster management, shard balancing, upgrades). Query language learning curve (Elasticsearch Query DSL). |
| **Failure mode** | Shard becomes corrupted → documents lost (mitigation: replica exists on different node). Inverted index becomes stale → search returns deleted documents (mitigated by soft deletes). Shard imbalance → one node overloaded (use shard routing to balance). Mitigation: replicas prevent data loss, snapshots for backup, monitoring shard sizes. |

---

## 🔬 Interview Q&As

### Q: "You index a document in Elasticsearch and immediately search for it — no results. Why?" ⭐

> Elasticsearch is near-real-time, not real-time. By default there is a 1-second refresh interval — documents are only searchable after the next refresh cycle. Between a write and the next refresh, the document exists in an in-memory segment but is not visible to search queries. Fix: (1) for testing, use `?refresh=true` on the write to force an immediate refresh — but avoid this in production at high write rates (expensive). (2) Increase refresh interval to 5s or more for bulk-write workloads (higher write throughput, slightly stale reads). (3) Accept the 1s lag for log analytics (users don't notice). ⭐ **Tier 1 — almost always probed**

### Q: "Can I use Elasticsearch as my primary database instead of Postgres?" ⭐

> No — and you should explicitly say so in interviews. ES has no ACID transactions, no foreign keys, no true UPDATE (updates are internally delete + reindex), and can lose documents during shard rebalancing if replicas aren't properly configured. It's a search index optimized for read-heavy, full-text workloads. Correct architecture: write to Postgres first (source of truth), sync to ES asynchronously via CDC (Debezium) or outbox pattern for search. If ES goes down, your data is safe; search degrades but the system remains correct. ⭐ **Tier 1 — design principle probe**

### Q: "You have 10M log documents. User searches for 'timeout'. Without Elasticsearch, scanning all 10M documents takes 10 seconds. With Elasticsearch, returns in 100ms. Why?"

> Inverted index. Elasticsearch pre-built: "timeout" → [doc1, doc7, doc23, ...]. Lookup in hash table = O(1). No scanning needed. Write-time cost: when document arrives, Elasticsearch tokenizes and updates inverted index (slower write). Read-time benefit: instant search. ⭐ **Tier 2 — Index mechanics**

### Q: "You have 100GB logs. Inverted index is 50GB. You add a second Elasticsearch node. Index is now split: Node 1 has 25GB, Node 2 has 25GB. Does query latency improve?"

> Yes. Query hits both shards in parallel. If single node took 100ms to query 50GB index, dual nodes take ~60ms (slightly more for merge). Not 2x faster (merge overhead), but significant improvement. Scales up to a point (eventual network becomes bottleneck). ⭐ **Tier 2 — Sharding**

### Q: "Replica shard dies. Elasticsearch detects and re-allocates to another node. Does this impact search latency?"

> Temporarily: during re-allocation, primary shard has no replica (reduced redundancy). After re-allocation, primary and new replica in sync. Search latency: minimal impact (can still search primary shard). But now cluster has no redundancy for this shard — if primary fails, data is lost. ⭐ **Tier 2 — Failover**

---

## 🧾 TL;DR

> "Elasticsearch uses inverted indexes (word → document IDs) for O(1) full-text search. Sharding distributes the index across nodes (parallel query). Near-real-time: documents searchable after the 1-second refresh cycle (use `?refresh=true` for immediate, sparingly). Never use as a primary DB — no ACID, no FK; always write to Postgres first, sync to ES for search via CDC/outbox."

---

## 🔗 Related Concepts

- **`25-monitoring-observability-fundamentals.md`** — Elasticsearch is the storage backend for ELK stack (logs pillar)
- **`03-caching.md`** — Inverted index is cached in memory for speed
- **`32-elasticsearch.md`** → Cross-reference to this file

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Elasticsearch Official Guide** | Mapping, sharding strategy, scaling, index management | ~30 min read |
| **ByteByteGo — "Elasticsearch Explained"** (YouTube) | Inverted index visualization, sharding, replication | ~12 min |
| **ELK Stack Tutorial** | End-to-end Logstash → Elasticsearch → Kibana pipeline | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 32. Covered inverted index mechanics (word → doc IDs O(1) lookup), sharding (parallel query execution), replication (redundancy), TF-IDF/BM25 scoring, ELK stack integration. |
| July 1, 2026 | Added refresh interval section (1s default, `?refresh=true`, bulk-load pattern). Strengthened "ES is not a primary DB" as boxed interview anti-pattern. Added 2 ⭐ Tier 1 Q&As (refresh, primary DB). Updated TL;DR. |
