# 50 — Database Indexing

## 📖 What is Database Indexing?

**Full form:** Database Index — a separate data structure maintained by the database engine that maps column values to row locations, enabling fast data retrieval without scanning every row in the table.

**Simple analogy:** The index at the back of a textbook. Without it, to find every mention of "consistent hashing" you'd read every page from page 1 to page 800. With the index, you look up "consistent hashing → pages 42, 187, 394" and jump directly. The book itself hasn't changed — the index is a separate, sorted lookup structure that points back into the real content.

**Core principle:** An index stores a sorted copy of one or more columns alongside a pointer to the actual row on disk. When a query filters on an indexed column, the database binary-searches the index (O(log N)) instead of scanning every row (O(N)). Every INSERT, UPDATE, and DELETE must also update the index — write overhead is the price of read speed.

**Why it matters in system design:** Index design is the most common answer to "this query is slow with 100 million rows." Every schema design conversation eventually becomes an index conversation — wrong column order, missing composite index, or too many indexes on a write-heavy table are the three most common production performance failures.

---

## 🎯 Why This Matters

- **Problem:** At 100M rows, a full table scan takes 8–20 seconds. With the right index on the right columns in the right order, the same query takes 2–5 milliseconds.
- **Interview signal:** Schema design questions always get this follow-up: *"The table has 100M rows and this query is slow. What do you do?"* Interviewers expect a specific answer — not "add an index" but which columns, in what order, and what trade-off you're accepting.
- **Senior expectation:** Know leftmost prefix rule, selectivity, covering indexes, and when the query planner deliberately ignores an index you've added.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **B-tree Index** | The default index type — a self-balancing sorted tree where leaf nodes hold column value + row pointer pairs, linked for range scans. O(log N) lookup. | `CREATE INDEX ON restaurants (city)` — find "Mumbai" in O(log 5M) steps instead of scanning all 5M rows. |
| **Composite Index** | An index on multiple columns, sorted by column 1 first, then column 2 within each column 1 value. Column order is critical — it determines which queries can use the index. | `CREATE INDEX ON restaurants (city, rating)` — city is the leading column; rating is sorted within each city group. |
| **Leftmost Prefix Rule** | A composite index supports an **efficient seek/tree-descent** only when the query filters on its **leading (leftmost) column(s)**. Skipping the leading column means no seek — but the planner may still do a **full index scan** (read the whole index) or, in Postgres, an **index-only scan** when the index is narrower than the heap, which can beat a seq scan. So "no leading column" ≠ "index always ignored"; it means "no fast range seek." | Index `(city, rating)`: `WHERE city='Mumbai'` ✅ seek, `WHERE city='Mumbai' AND rating>4.5` ✅ seek, `WHERE rating>4.5` ⚠️ no seek — full index scan or seq scan depending on the planner's cost estimate. |
| **Selectivity** | How many rows a filter condition eliminates. **High selectivity** = very few rows survive (e.g., `WHERE user_id=123` returns 1 of 50M). **Low selectivity** = most rows survive (e.g., `WHERE is_active=true` returns 45M of 50M). The query planner silently ignores low-selectivity indexes — random I/O across 45M scattered rows costs more than one sequential scan. | `is_active` index ignored (90% rows match). `user_id` index used (1 row matches). Same index presence, opposite behavior. |
| **Covering Index** | An index that contains all columns the query needs — both filter columns (WHERE) and return columns (SELECT). The database reads only the index leaves, never touching actual table rows (Index Only Scan — fastest possible read path). | `CREATE INDEX ON restaurants (city, rating) INCLUDE (name)` — `SELECT name WHERE city='Mumbai' AND rating>4.5` never reads the table. |
| **Full Table Scan (Seq Scan)** | Reading every row in the table sequentially to check each one against the WHERE condition. O(N). Costs 8–20 seconds at 100M rows but uses efficient sequential prefetch I/O — can legitimately beat a bad index. | No index on `city`: scan all 50M rows, check each. The query planner sometimes chooses this deliberately over a low-selectivity index. |
| **Heap Fetch** | After finding matching row pointers in the index, the database must jump to those rows in the actual table (the "heap") to retrieve columns not stored in the index. Each fetch is a random I/O — expensive if data is not in the buffer pool. Covering indexes eliminate heap fetches. | Index returns 500 matching rows; database reads 500 scattered table pages. If pages are not in RAM, each is a disk I/O. Covering index skips this entirely. |

---

## 🧠 The Mental Model

Imagine a library with 5 million books, physically arranged by acquisition date (the primary key — the order books arrived). A patron asks: *"I want all books by Chetan Bhagat published after 2015."*

**Without an index:** The librarian reads every card from book #1 to book #5,000,000 checking author and year. That is a full table scan — sequential, unavoidable, slow.

**With a composite index on (author, year):** The librarian has a separate card catalogue — sorted alphabetically by author, then by year within each author. To answer the query:
1. Binary-search to "Bhagat, Chetan" — O(log 5M) steps, not 5M
2. Within that section, skip to 2015+ entries — already sorted
3. Read only the matching row pointers, walk to those shelves

The catalogue is a separate physical structure. Maintaining it costs effort on every book acquisition (INSERT) or relocation (UPDATE). But reads are dramatically faster.

**Three decisions that determine whether your index actually helps:**

1. **Which column goes first in a composite index** — the catalogue is sorted by author first. "All books by Bhagat" uses it. "All books from 2015" cannot — that would mean scanning the entire unsorted-by-year catalogue. This is the **leftmost prefix rule**.

2. **Selectivity** — how many rows does the index actually eliminate? An index on `is_active` (boolean, 90% rows are true) eliminates almost nothing. The query planner will ignore it and scan the table anyway. High selectivity (user_id, email, order_id) = index is used. Low selectivity (status, country) = query planner may skip it.

3. **Covering index** — if the index itself contains all columns the query needs, the database reads the index leaves and never touches the actual table rows. Like answering "who wrote the most books?" directly from the catalogue — you never walk to a single shelf.

**The key insight is:** The query planner decides whether to use your index — you cannot force it. It uses selectivity estimates from table statistics. An index that passes the selectivity test but violates leftmost prefix rule or has stale statistics will be silently ignored.

---

## 🎨 Visual — Index in Query Execution + B-tree Internal Structure

```
FULL SYSTEM TOPOLOGY — where indexing decisions are made:

Application Layer
┌─────────────────────────────────────────────────────────┐
│  Java Service                                           │
│  restaurantRepo.findByCityAndRatingGreaterThan(...)     │
└────────────────────────┬────────────────────────────────┘
                         │ SQL: WHERE city='Mumbai' AND rating > 4.5
                         ▼
Database Tier — Query Planner
┌─────────────────────────────────────────────────────────┐
│  PostgreSQL Query Planner                               │
│                                                         │
│  Checks pg_statistics → is index selective enough?      │
│                                                         │
│  YES → Index Scan (O log N)   NO → Seq Scan (O N)       │
│         ↓                            ↓                  │
│   ┌─────────────┐            ┌───────────────┐         │
│   │  Index      │            │  Table Pages  │         │
│   │  B-tree     │            │  (full scan)  │         │
│   │  on         │            │               │         │
│   │  (city,     │            │  50M rows ❌  │         │
│   │   rating)   │            └───────────────┘         │
│   └──────┬──────┘                                       │
│          │ row pointers                                 │
│          ▼                                              │
│   ┌──────────────┐                                      │
│   │  Table Pages │  ← heap fetch (unless covering)     │
│   │  (only       │                                      │
│   │   matching   │                                      │
│   │   rows ✅)   │                                      │
│   └──────────────┘                                      │
└─────────────────────────────────────────────────────────┘

KEY INVARIANT:
   The query planner sits between your SQL and the index. It decides
   whether to use the index based on selectivity estimates — not on
   whether the index exists.

───────────────────────────────────────────────────────────────

COMPONENT DETAIL — B-tree Internal Structure + Leftmost Prefix Rule:

B-tree index on (city, rating):

Root Node
┌──────────────────────────────────────┐
│  Mumbai | Delhi | Bangalore | ...    │  ← sorted by city (1st column)
└──────┬──────────┬────────────────────┘
       │          │
  ┌────▼───┐  ┌───▼────┐
  │ Mumbai │  │ Delhi  │  ← leaf node group per city
  │ rating │  │ rating │
  │ sorted │  │ sorted │
  └────┬───┘  └────────┘
       │
  Leaf Nodes (sorted by rating within Mumbai)
  ┌─────────────────────────────────────────────┐
  │ 3.1 → row_ptr_001 │ 3.4 → row_ptr_044 │    │
  │ 3.8 → row_ptr_102 │ 4.1 → row_ptr_209 │    │
  │ 4.5 → row_ptr_310 │ 4.8 → row_ptr_415 │    │
  └─────────────────────────────────────────────┘
       ↑ WHERE rating > 4.5 scans from here → fast ✅

Query pattern vs index support:
  WHERE city = 'Mumbai'                       ✅ uses index (leftmost column)
  WHERE city = 'Mumbai' AND rating > 4.5      ✅ uses both columns in order
  WHERE rating > 4.5                          ❌ leftmost column missing → Seq Scan

Covering index: INCLUDE (name) — index leaf stores name too → no heap fetch needed

KEY INVARIANT:
   Composite index sorted by column 1 first, then column 2 within column 1.
   Skipping the leftmost column breaks the sorted order — the index becomes useless.
```

---

## ⚙️ How It Actually Works

**Three strategies — each fully covered below:**
1. **B-tree index** — the default, handles equality and range queries
2. **Composite index** — multiple columns, order is critical
3. **Covering index** — includes all query columns, eliminates heap fetch

### What is a B-tree, and why does it fit here?

A **B-tree** (Balanced tree — pronounced "B-tree," not "binary tree") is a self-balancing tree where every leaf is at the same depth. Database indexes are typically B+ trees: internal nodes hold keys for navigation; leaf nodes hold key + row pointer pairs, and leaves are linked for range scans. After any INSERT or DELETE, the tree rebalances automatically. In an interview, if asked: *"What data structure does a DB index use?"* — say "B+ tree — sorted keys at leaves, linked for range scans, O(log N) lookup."

### What is EXPLAIN, and why does it fit here?

**EXPLAIN** (or `EXPLAIN ANALYZE` in PostgreSQL) shows the query execution plan the planner chose — including whether it used an index, did a Seq Scan, and the estimated vs actual row counts. In an interview, if asked: *"How do you diagnose a slow query?"* — say "EXPLAIN ANALYZE — look for Seq Scan on large tables and compare estimated vs actual rows; stale statistics show as large discrepancies."

**Steps in plain English:**

1. **Create a composite index on the most-queried columns** — put the highest-selectivity column first.
2. **Run EXPLAIN ANALYZE** — verify the planner chose Index Scan, not Seq Scan.
3. **Add an INCLUDE clause for covering index** — if SELECT fetches columns not in WHERE, include them so the planner can do Index Only Scan.

```sql
-- Step 1: Slow query on 50M-row restaurants table
-- EXPLAIN shows: Seq Scan, cost ~2,000,000 (bad)
SELECT id, name, rating
FROM restaurants
WHERE city = 'Mumbai' AND rating > 4.5
ORDER BY rating DESC;

-- Step 1: Create composite index — city first (high selectivity), rating second
-- Column order: city eliminates ~96% of rows; rating then filters within Mumbai
CREATE INDEX idx_restaurants_city_rating
    ON restaurants (city, rating DESC);

-- Step 2: Verify plan improved
-- Run: EXPLAIN ANALYZE SELECT id, name, rating ...
-- Now shows: Index Scan using idx_restaurants_city_rating, cost ~850 ✅

-- Step 3: Covering index — include 'name' to eliminate heap fetch
-- Old plan: Index Scan → then heap fetch for 'name' column
-- New plan: Index Only Scan → reads everything from index leaves, never touches table
CREATE INDEX idx_restaurants_covering
    ON restaurants (city, rating DESC)
    INCLUDE (name);
-- EXPLAIN now shows: Index Only Scan ✅ (fastest possible)

-- Leftmost prefix rule violation — this query CANNOT use idx_restaurants_city_rating:
-- EXPLAIN shows: Seq Scan (planner skipped the index)
SELECT id, name FROM restaurants WHERE rating > 4.5; -- missing 'city' in WHERE
-- Fix: create a separate index on (rating) for this query pattern
CREATE INDEX idx_restaurants_rating ON restaurants (rating DESC);

-- Partial index — if 90% of restaurants are inactive, index only active ones
CREATE INDEX idx_restaurants_active_city
    ON restaurants (city, rating DESC)
    WHERE status = 'ACTIVE';
-- This index is 10x smaller and faster than a full-table index
```

```java
// Java / Spring Data JPA — verify index is used via native query with EXPLAIN
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // This query benefits from composite index on (city, rating)
    List<Restaurant> findByCityAndRatingGreaterThanOrderByRatingDesc(
        String city,
        double minRating
    );

    // For debugging: run EXPLAIN ANALYZE via native query
    @Query(
        value = "EXPLAIN ANALYZE SELECT id, name, rating FROM restaurants "
              + "WHERE city = :city AND rating > :minRating ORDER BY rating DESC",
        nativeQuery = true
    )
    List<String> explainQuery(
        @Param("city") String city,
        @Param("minRating") double minRating
    );
}
```

---

## 🏢 Real World — Where Companies Use This

- **Swiggy** (restaurant search): Composite index on `(city, cuisine_type, rating DESC)` for "top-rated Chinese in Bangalore." City is leftmost (highest selectivity), cuisine narrows further, rating sorts — all from the index without touching table rows.
- **PhonePe** (transaction history page): Index on `(user_id, created_at DESC)` covering `amount, status` — the "last 50 transactions" query does an Index Only Scan. A user's 500 transactions found in microseconds from 500M total rows.
- **Razorpay** (idempotency key lookup): Unique index on `idempotency_key` — enforces exactly-once at DB level AND enables O(log N) lookup by idempotency key on every payment retry check.
- **BookMyShow** (active event search): Partial index `WHERE status = 'ACTIVE'` on the events table — 95% of events are past. The partial index covers only active events, making it 20× smaller and faster than a full-column index.
- **Amazon** (seller order lookup): Composite index on `(seller_id, order_date DESC)` — enables paginated "My Orders" view. High-selectivity seller_id narrows to one seller; order_date sorts for pagination cursor.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Column appears in WHERE, JOIN, or ORDER BY of slow, frequent queries | Table has fewer than 100K rows (sequential scan is fast enough) |
| Query without index scans > 10% of table rows | Column has very low selectivity (boolean, low-cardinality enum) |
| Multiple WHERE columns are always queried together (use composite) | Table has very high write throughput (each index = extra write cost) |
| Need DB-enforced uniqueness AND fast lookup (unique index) | Column is rarely queried but updated on every row change |
| SELECT fetches only indexed columns (use covering index) | You already have 5+ indexes on the table — re-evaluate vs. denormalize |

**The common mistake:** Creating a separate single-column index for each WHERE column instead of one composite index. `(city)` + `(rating)` as two indexes is worse than `(city, rating)` as one — the planner rarely merges two indexes efficiently, and you pay double write overhead for half the read benefit.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Read queries on indexed columns drop from O(N) to O(log N). Covering indexes eliminate heap fetch — index-only scan is the fastest possible read path. Unique indexes enforce data integrity at DB level without application-layer checks. |
| **You lose** | Every INSERT, UPDATE, DELETE must update all indexes. A table with 6 indexes pays 7 writes per INSERT. Index storage grows — a large table's indexes can exceed the table's own size. Write-heavy tables (event logs, click tracking) suffer measurably with too many indexes. |
| **Failure mode** | Query planner silently ignores your index when: (a) selectivity is too low (index returns > 20–30% of rows — sequential scan with prefetch is cheaper), (b) table statistics are stale (run ANALYZE), (c) you violated leftmost prefix rule. You added the index but EXPLAIN still shows Seq Scan — this is the most common "index didn't help" trap. |

---

## 🔬 Interview Q&As

### Q: "This query on a 100M row table takes 8 seconds. How do you fix it?"

> Run EXPLAIN ANALYZE first — diagnose before prescribing. If it shows Seq Scan, look at the WHERE clause columns. Add a composite index putting the most selective column first. Run EXPLAIN again to confirm the planner switched to Index Scan. If the query also selects additional columns, evaluate a covering index to eliminate the heap fetch.

### Q: "You have a composite index on (city, rating). Why is `WHERE rating > 4.5` still doing a Seq Scan?"

> Composite indexes follow the leftmost prefix rule. The index is physically sorted by city first, then rating within city. A query that skips the leading city column cannot navigate the tree structure — it would have to scan the entire index top-to-bottom, which costs more than a sequential table scan. Fix: add a separate index on (rating) for this specific query pattern, or use a partial index if the range is predictable.

### Q: "You added an index and the query got slower. Why?" *(Tier 2)*

> Three reasons: (1) Low selectivity — if the indexed column is a boolean and 80% of rows are `true`, the index returns 80% of the table. Random I/O to fetch those scattered rows is slower than prefetched sequential scan. The planner chose correctly to ignore the index. (2) Table is small — for tables under ~10K rows, the full table fits in buffer pool and sequential scan is faster than index traversal overhead. (3) Stale statistics — pg_statistics hasn't been updated recently (ANALYZE not run), so the planner's row count estimates are wrong and it picked the wrong plan. Run ANALYZE then EXPLAIN again.

### Q: "What is a covering index and when does it justify the extra storage cost?" *(Tier 2)*

> A covering index includes both the filter columns (in the WHERE clause) and the select columns (in the SELECT list) inside the index itself. This enables an Index Only Scan — the database reads the index leaf nodes and never fetches actual table rows. Worth the extra storage when: the query runs millions of times per day (one heap fetch per result row adds up), the table is large (heap pages are not in buffer pool — each fetch is a disk read), and the SELECT needs only 2–4 columns. The trade-off: updates to included columns also update the index.

### Q: "How many indexes is too many on a table?"

> It depends on the write/read ratio. A read-heavy lookup table (user profiles, product catalog) can carry 6–8 indexes with minimal pain. A write-heavy table (order events, transaction log, audit trail) should have at most 2–3 indexes — each index adds latency to every write. The trigger to audit: if INSERT throughput drops > 20% after adding an index, you've crossed the threshold. Consider partial indexes (only index a subset of rows) or remove indexes on low-selectivity columns.

### Q: "Difference between clustered and non-clustered index?"

> A clustered index determines the physical row storage order in the table — rows are stored on disk in index order. MySQL InnoDB always uses the primary key as the clustered index. There's only one per table. Range queries on the clustered index are extremely fast because rows are physically adjacent on disk. A non-clustered (secondary) index is a separate B-tree with pointers (row IDs) back to the heap. Range queries on a secondary index require random I/O for each pointer — expensive if many rows match.

---

## 🧭 B-tree vs LSM-tree — Two Index Storage Engines

Every DB you name in an interview uses one of two index engines. Know the trade-off:

| | **B-tree** (Postgres, MySQL/InnoDB, Oracle) | **LSM-tree** (Cassandra, RocksDB, ScyllaDB, LevelDB) |
| --- | --- | --- |
| **Write path** | Update-in-place — find the leaf page, write it (random I/O) | Append to an in-memory memtable, flush sorted immutable SSTables (sequential I/O) |
| **Optimized for** | Reads + range scans; balanced workloads | High write throughput |
| **Read cost** | 1 tree descent, O(log N) | May check memtable + several SSTable levels → **read amplification**; mitigated by **Bloom filters** (skip SSTables that definitely don't hold the key) |
| **Write cost** | Random writes, page splits | Fast appends, but background **compaction** merges SSTables (write amplification + I/O) |
| **Deletes** | Remove in place | Write a **tombstone**; space reclaimed only at compaction |

> **Interview line:** "B-tree updates in place — great for reads and ranges, but every write is a random I/O. LSM turns writes into sequential appends + background compaction, so it wins on write-heavy workloads (Cassandra, time-series); the cost is read amplification, which Bloom filters and compaction keep in check." See `08-bloom-filter.md` and `../../Core-Architecture/Database-Core/59-nosql-cassandra-mongo-dynamo.md`.

---

## 🧾 TL;DR

> "A composite index trades write overhead for read speed — column order follows the leftmost prefix rule, covering indexes eliminate heap lookups entirely, and the query planner will silently skip your index if selectivity is too low or statistics are stale."

---

## 🔗 Related Concepts

- **`12-data-modeling.md`** — Schema design decisions that determine which columns need indexes
- **`38-sharding-strategy.md`** — Shard key choice interacts with index design; cross-shard queries cannot use local indexes
- **`06-databases-types-and-selection.md`** — Different engines have different index types: PostgreSQL GiST for geospatial, GIN for full-text; MySQL InnoDB clustered PK

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Use the Index, Luke** — use-the-index-luke.com | Deep visual explainer of B-tree internals, index selectivity, and execution plan interpretation | ~30 min read |
| **EXPLAIN ANALYZE in depth** — PostgreSQL official docs | How to read every line of EXPLAIN ANALYZE output — node types, cost estimates, actual vs estimated rows | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Jul 1, 2026 | Note created. Covers B-tree indexes, composite indexes (leftmost prefix rule), covering indexes, selectivity, EXPLAIN ANALYZE, clustered vs non-clustered. Tier 2 Q&As: "index got slower" + "covering index trade-off." Added Terminology Table (B-tree, composite index, leftmost prefix rule, selectivity, covering index, seq scan, heap fetch) — terms were used in KEY INVARIANTs before being defined. |
| Jul 19, 2026 | **Accuracy fix + gap.** (1) Softened the leftmost-prefix rule — "skip the leading column → always seq scan" overstated it; Postgres can full-index-scan / index-only-scan / skip-scan, so "no leading column" means "no fast seek," not "index ignored." (2) Added a B-tree vs LSM-tree storage-engine comparison (write/read amplification, compaction, tombstones, Bloom filters) — a common SDE-3 probe the file cross-referenced but never explained. |
