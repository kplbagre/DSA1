# ☕ SQL & Database Internals for Java Devs — Deep Dive

> After this note you can read an `EXPLAIN ANALYZE` output, choose the right index type (B-tree vs hash vs composite), diagnose a slow query, tune HikariCP, and explain why your JPA query generates 100 SQL statements instead of 1.

---

## 🎯 The Problem This Solves

A Java developer writes `orderRepository.findByStatus("PENDING")`. The response takes 3 seconds. Is the problem in Java? In JPA? In the SQL? In the index? In the connection pool? Without understanding the layers below JPA — SQL query execution, index selection, connection pooling — you can't diagnose database performance. You throw `-Xmx` at a problem that needs an index, or add an index when the problem is connection pool exhaustion.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Query plan** | The database's execution strategy for a SQL query. Shows: which tables are scanned, which indexes are used, join algorithms, estimated vs actual row counts. |
| **EXPLAIN ANALYZE** | PostgreSQL command that runs the query AND shows the actual execution plan with real timing. `EXPLAIN` shows the plan without running. Always use `ANALYZE` for real data. |
| **Sequential scan (Seq Scan)** | Reads every row in the table. O(n). Fine for small tables. Disaster for 10M-row tables. |
| **Index scan** | Uses an index to find matching rows. O(log n) for B-tree. The goal for most queries on large tables. |
| **B-tree index** | The default index type. Sorted tree structure. Supports: `=`, `<`, `>`, `<=`, `>=`, `BETWEEN`, `LIKE 'prefix%'`, `ORDER BY`, `IS NULL`. |
| **Composite index** | An index on multiple columns: `CREATE INDEX idx ON orders(status, created_at)`. Column ORDER matters — the index is sorted by the first column, then by the second within each first-column group. |
| **Covering index** | An index that contains ALL columns the query needs — the DB reads the index only, never touches the table data (heap). Fastest possible query. |
| **Connection pool** | A cache of reusable database connections. Creating a new TCP connection + authentication per query is expensive (~5-50ms). Pool reuses connections — acquiring from pool is ~0.1ms. |
| **HikariCP** | The default connection pool in Spring Boot. Fastest Java connection pool. Key setting: `maximumPoolSize` (default 10). |

---

## 🧠 Mental Model

Your JPA method → Hibernate generates SQL → SQL goes over a connection from the pool → the database parses the SQL → the query optimizer builds an execution plan → the executor runs the plan (using indexes or scanning tables) → results come back over the connection → Hibernate maps them to Java objects.

Performance can break at ANY layer: JPA generating bad SQL (N+1), the connection pool being exhausted (all connections busy), the query optimizer choosing a sequential scan instead of an index, or the index not existing. Diagnosing requires understanding each layer.

> If you can say "EXPLAIN ANALYZE shows the real execution plan; B-tree index supports range queries and ORDER BY; composite index column order matches WHERE clause order (leftmost prefix rule); HikariCP default pool size is 10; connection exhaustion looks like thread hanging, not an exception" without notes, you have database internals.

---

## 🎨 Visual — Query Execution Path

```
  Java: orderRepository.findByStatus("PENDING")
       │
       ▼
  Hibernate: generates SQL
       SELECT * FROM orders WHERE status = 'PENDING'
       │
       ▼
  HikariCP: acquires a connection from the pool
       (blocks if all connections busy → timeout → exception)
       │
       ▼
  PostgreSQL receives the SQL
       │
       ▼
  PARSER → PLANNER/OPTIMIZER → EXECUTOR
           │
           ▼
  Optimizer decides:
    Option A: Sequential Scan (read ALL rows, filter status)
              Cost: O(n) — 10M rows = 10M reads
    Option B: Index Scan on idx_orders_status
              Cost: O(log n + matches) — find "PENDING" in B-tree, read matching rows
           │
           ▼
  If index exists AND is selective → Index Scan → fast
  If no index OR low selectivity → Seq Scan → slow

KEY INVARIANT:
   The optimizer makes a COST-BASED decision.
   It estimates the cost of each plan and picks the cheapest.
   Wrong statistics → wrong estimate → wrong plan → slow query.
   ANALYZE (the table command, not EXPLAIN ANALYZE) updates statistics.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```sql
-- 10 million orders, no index on status
SELECT * FROM orders WHERE status = 'PENDING';
-- Seq Scan: reads ALL 10M rows, checks status on each → 5 seconds

-- The developer's "fix": add -Xmx (more Java heap)
-- Does nothing — the problem is in the database, not Java.

-- Or: the developer caches everything in a HashMap
-- Masks the problem but introduces stale data + OOM risk.
```

---

### Level 2 — The real mechanism

#### 2.1 — Reading EXPLAIN ANALYZE

```sql
EXPLAIN ANALYZE SELECT * FROM orders WHERE status = 'PENDING';

-- Output:
-- Seq Scan on orders  (cost=0.00..285432.00 rows=50000 width=120)
--                      (actual time=0.015..2145.332 rows=48523 loops=1)
--   Filter: (status = 'PENDING')
--   Rows Removed by Filter: 9951477
-- Planning Time: 0.085 ms
-- Execution Time: 2148.521 ms
```

**Reading the output:**

```
  Seq Scan on orders         ← scan type (Seq Scan = full table scan — BAD for large tables)
  cost=0.00..285432.00       ← estimated cost (startup..total) in arbitrary units
  rows=50000                 ← estimated rows returned (optimizer's guess)
  width=120                  ← average row size in bytes
  actual time=0.015..2145ms  ← REAL time (first row..last row)
  rows=48523                 ← ACTUAL rows returned (compare with estimate)
  Rows Removed by Filter: 9.9M  ← read 10M rows, kept 48K → 99.5% wasted reads
  Execution Time: 2148ms     ← total wall-clock time

  DIAGNOSIS: Seq Scan + Rows Removed by Filter >> rows returned = NEEDS AN INDEX.
```

```sql
-- Add the index:
CREATE INDEX idx_orders_status ON orders(status);

-- Re-run:
EXPLAIN ANALYZE SELECT * FROM orders WHERE status = 'PENDING';

-- Output:
-- Index Scan using idx_orders_status on orders  (cost=0.43..1523.45 rows=50000 width=120)
--                                                (actual time=0.028..12.445 rows=48523 loops=1)
--   Index Cond: (status = 'PENDING')
-- Planning Time: 0.112 ms
-- Execution Time: 15.234 ms

-- 2148ms → 15ms. 143x faster. The index changed the plan from Seq Scan to Index Scan.
```

#### 2.2 — Index types and when to use each

| Index type | Supports | Use when | Don't use when |
|---|---|---|---|
| **B-tree** (default) | `=`, `<`, `>`, `BETWEEN`, `LIKE 'prefix%'`, `ORDER BY`, `IS NULL` | Almost always. Default choice. | Full-text search, array containment |
| **Hash** | `=` only | Exact match lookups only. Slightly faster than B-tree for `=`. | Range queries, sorting, `IS NULL` |
| **GIN** | Array containment, full-text search, JSONB `@>` | JSONB queries, `tsvector` search, array `@>` | Simple scalar column lookups |
| **GiST** | Geometric/geospatial queries, range types, nearest-neighbor | PostGIS, range queries `&&`, `@>` | Simple equality/range on scalars |

**B-tree is the right choice 95% of the time.** Use others only when B-tree can't support the operation.

#### 2.3 — Composite indexes and the leftmost prefix rule

```sql
-- Composite index: CREATE INDEX idx ON orders(status, created_at);
-- The index is sorted by status FIRST, then by created_at WITHIN each status.

-- Think of it like a phone book: sorted by last name, then first name.

-- ✅ Uses the index (matches leftmost prefix):
WHERE status = 'PENDING'                          -- uses status (first column)
WHERE status = 'PENDING' AND created_at > '2026-01-01'  -- uses both columns
WHERE status = 'PENDING' ORDER BY created_at      -- uses both: filter + sort

-- ❌ CANNOT use the index (skips the leftmost column):
WHERE created_at > '2026-01-01'                   -- skips status → Seq Scan
ORDER BY created_at                                -- skips status → sort without index

-- RULE: a composite index on (A, B, C) supports queries on:
-- A, (A,B), (A,B,C) — always starting from the LEFT.
-- NOT: B alone, C alone, (B,C) — the leftmost column must be present.
```

**Column order in composite index matters:**

```sql
-- Query: WHERE status = ? AND customer_id = ?
-- Both columns have equal selectivity.

-- Index (status, customer_id):
-- status has ~5 distinct values → first level narrows to 20% of rows
-- customer_id has ~100K distinct values → second level narrows to 1 row

-- Index (customer_id, status):
-- customer_id narrows to ~100 rows → status narrows to ~1 row
-- This order is BETTER if customer_id is queried alone sometimes.

-- RULE: put the MOST SELECTIVE column first (most distinct values),
-- OR the column most frequently used ALONE in other queries.
```

#### 2.4 — Covering index (index-only scan)

```sql
-- Query: SELECT status, count(*) FROM orders GROUP BY status;
-- With index: CREATE INDEX idx ON orders(status);
-- PostgreSQL must: scan the index (find status values) → go to the table (heap) to
-- check visibility (MVCC — is this row visible to my transaction?).

-- Covering index: includes ALL columns the query needs:
CREATE INDEX idx_covering ON orders(status) INCLUDE (created_at);
-- Now: SELECT status, created_at FROM orders WHERE status = 'PENDING'
-- → Index Only Scan: reads the index, NEVER touches the table.
-- Faster because: no random I/O to the heap. Index is smaller than table.

-- PostgreSQL 11+ supports INCLUDE clause for covering indexes.
```

#### 2.5 — HikariCP connection pool tuning

```yaml
# application.yml — Spring Boot + HikariCP
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/orders
    hikari:
      maximum-pool-size: 10     # max connections to DB (default 10)
      minimum-idle: 10          # keep all connections alive (= max for stable workload)
      connection-timeout: 30000 # 30s — time to wait for a connection from pool
      idle-timeout: 600000      # 10 min — unused connection closed after this
      max-lifetime: 1800000     # 30 min — connection recycled (prevents stale connections)
      leak-detection-threshold: 60000  # 60s — log warning if connection held > 60s
```

**Sizing the pool:**

```
  FORMULA (from HikariCP wiki):
  connections = (core_count × 2) + effective_spindle_count
  
  For SSD (no spindles): connections ≈ core_count × 2 + 1
  
  8-core server → 17 connections.
  
  More connections is NOT better:
  - Each connection is a PostgreSQL backend process (~10 MB RAM)
  - More connections = more lock contention on shared resources
  - More connections = more context switching in the DB
  
  With 100 connections: PostgreSQL spends more time switching between
  connections than executing queries → LOWER throughput than 20.
  
  RULE: start with 10-20. Benchmark. Increase only if connection-timeout
  errors appear AND the DB can handle more connections.
```

**Connection exhaustion symptoms:**

```
  Thread blocked → waiting for connection from pool → connection-timeout after 30s
  → HikariPool-1: Connection is not available, request timed out after 30000ms.
  
  Causes:
  1. Slow queries holding connections too long → fix the query
  2. @Transactional scope too wide → narrow the transaction boundary
  3. Connection leak: acquired but never returned (missing close/commit)
     → enable leak-detection-threshold
  4. Pool too small for the workload → increase (but fix #1-3 first)
```

#### 2.6 — The JPA ↔ SQL mapping traps

```java
// N+1 → covered in Spring/DeepDive/04-jpa-transactions.md
// Here: the "SELECT *" trap

// ❌ JPA default: SELECT * (all columns)
List<Order> orders = orderRepository.findAll();
// Hibernate: SELECT o.id, o.customer_name, o.status, o.created_at, o.blob_data, ... FROM orders
// If 'blob_data' is 100 KB per row × 10,000 rows = 1 GB transferred from DB

// ✅ Projection: select only needed columns
public interface OrderSummary {
    Long getId();
    String getStatus();
}

@Query("SELECT o.id as id, o.status as status FROM Order o WHERE o.status = :status")
List<OrderSummary> findSummaryByStatus(@Param("status") String status);
// Only id + status transferred — 1000x less data

// ✅ DTO projection (record):
@Query("SELECT new com.walmart.OrderDTO(o.id, o.status) FROM Order o WHERE o.status = :status")
List<OrderDTO> findDtoByStatus(@Param("status") String status);
```

---

### Level 3 — The subtleties

#### 3.1 — When the optimizer chooses Seq Scan despite index

```sql
-- If the query returns > ~20-30% of the table, the optimizer CORRECTLY chooses Seq Scan.
-- Why? Index Scan does: index lookup → random I/O to heap for each row.
-- Seq Scan does: sequential I/O through the entire table.
-- Sequential I/O is much faster than random I/O for large result sets.

-- Example: SELECT * FROM orders WHERE status = 'PENDING'
-- If 80% of orders are PENDING → index scan would read 80% of rows via random I/O.
-- Seq Scan reads 100% of rows via sequential I/O → actually faster.

-- This is NOT a bug. The optimizer is correct. Low-selectivity queries don't benefit from indexes.
-- Fix: make the query more selective (add more WHERE conditions) or accept the Seq Scan.
```

#### 3.2 — Index maintenance cost

```sql
-- Every INSERT/UPDATE/DELETE must update EVERY index on the table.
-- 5 indexes on a table → 5 index updates per INSERT.
-- Write-heavy tables: too many indexes = slow inserts.

-- RULE: index columns that appear in WHERE, JOIN, ORDER BY.
-- DON'T index: columns rarely queried, boolean columns (low selectivity),
-- columns in write-heavy tables unless the read benefit justifies write cost.

-- Monitor unused indexes:
SELECT relname, indexrelname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0 AND indexrelname NOT LIKE '%pkey%';
-- idx_scan = 0 → index was never used → candidate for removal
```

#### 3.3 — `spring.jpa.show-sql` is not enough

```yaml
# ❌ show-sql shows the SQL but not parameters or timing
spring.jpa.show-sql: true
# Output: select order0_.id, ... from orders order0_ where order0_.status=?
# Can't see: what value was passed for ?, how long it took, how many rows returned

# ✅ Use datasource-proxy for full SQL logging with parameters + timing
# Or enable Hibernate statistics:
spring.jpa.properties.hibernate.generate_statistics: true
# Shows: query count, fetch count, entity load count, execution time per query
# In production: use Micrometer + Prometheus to track query duration histograms
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Adding an index always makes queries faster" | Indexes speed up reads but slow down writes (every INSERT/UPDATE/DELETE updates the index). Low-selectivity indexes (boolean columns, status with 3 values on 80% of rows) may not be used by the optimizer at all. |
| "More connections = more throughput" | Each connection is a DB process (~10 MB). Too many connections cause lock contention and context switching IN THE DATABASE. 20 well-used connections typically outperform 200 mostly-idle ones. |
| "`SELECT *` is fine if I need all columns" | `SELECT *` fetches ALL columns including BLOBs, large text, and unused fields. For list/summary queries, use projections to fetch only needed columns — 10-100x less data transferred. |
| "Index on (A, B) helps queries filtering on B alone" | Composite indexes follow the leftmost prefix rule. Index on (A, B) helps queries on A, or (A AND B). NOT on B alone. For B alone, create a separate index on (B). |
| "HikariCP pool size should be large for high-traffic services" | HikariCP's recommended formula: `connections = cores × 2 + 1`. A server with 8 cores needs ~17 connections, not 100. Larger pools cause MORE contention inside the database, not less. |

---

## 🐞 Production Footguns

---

> **Footgun: Missing index on foreign key**
> **Cost:** Slow JOINs and cascade deletes
>
> A `order_items` table had a foreign key `order_id` referencing `orders(id)`. PostgreSQL does NOT automatically index foreign keys (MySQL does). Every `DELETE FROM orders WHERE id = ?` triggered a sequential scan on `order_items` to check referential integrity — 10M item rows scanned per delete. Adding `CREATE INDEX idx_items_order ON order_items(order_id)` fixed it.

```sql
-- ❌ The trap: FK without index (PostgreSQL)
ALTER TABLE order_items ADD CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(id);
-- No index created on order_items(order_id) — PostgreSQL doesn't auto-create one
-- Every JOIN and cascade DELETE does a Seq Scan on order_items

-- ✅ The fix: always index foreign keys
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

---

> **Footgun: Connection pool exhaustion from long transactions**
> **Cost:** Service hangs — all request threads blocked waiting for connections
>
> A `@Transactional` method loaded orders, called an external API (500ms), then updated the orders. The DB connection was held for the ENTIRE method duration — including the 500ms API call. With 10 connections and 20 concurrent requests, 10 threads held connections while calling the API. The other 10 threads waited for connections → 30s timeout → `HikariPool: Connection is not available`.

```java
// ❌ The trap: external API call inside @Transactional
@Transactional
public void processOrder(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    PaymentResult payment = paymentApi.charge(order.getTotal());   // 500ms external call
    // DB connection held for 500ms doing NOTHING — waiting for API
    order.setPaymentRef(payment.getRef());
    orderRepository.save(order);
}

// ✅ The fix: narrow the transaction scope
public void processOrder(Long orderId) {
    Order order = findOrder(orderId);                              // quick DB read
    PaymentResult payment = paymentApi.charge(order.getTotal());   // no @Transactional — no connection held
    updateOrderPayment(orderId, payment.getRef());                 // quick DB write
}

@Transactional(readOnly = true)
private Order findOrder(Long orderId) {
    return orderRepository.findById(orderId).orElseThrow();
}

@Transactional
private void updateOrderPayment(Long orderId, String paymentRef) {
    orderRepository.updatePaymentRef(orderId, paymentRef);
}
// Connection held for ~5ms per transaction, not 500ms. Pool can handle 100x more throughput.
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `../Spring/DeepDive/04-jpa-transactions.md` | JPA sits on top of SQL. N+1 problem, LazyInitializationException, @Transactional propagation — all covered there. This note covers the SQL layer BELOW JPA. |
| `../Concurrency/thread-pool-executor.md` | HikariCP is a connection POOL — same concepts as thread pools (core size, max size, timeout, exhaustion). Understanding ThreadPoolExecutor explains why connection pool sizing matters. |
| `../Concurrency/virtual-threads-java21.md` | Virtual threads + HikariCP: virtual threads unmount during DB I/O — carrier is freed. But HikariCP's internal `synchronized` can pin. HikariCP 5.1+ addresses this. |
| `testing-strategy.md` | `@DataJpaTest` + Testcontainers: test real SQL against real PostgreSQL. This note explains WHAT to test (index usage, query performance). Testing note explains HOW to test it. |

---

## 🎙️ Interview Deep Questions

**Q1. How do you diagnose a slow database query in a Java service?**

> Five-step workflow: (1) Enable Hibernate statistics or datasource-proxy to identify WHICH query is slow and how many times it executes (N+1 detection). (2) Run the query in `psql` with `EXPLAIN ANALYZE` to see the actual execution plan — look for Seq Scan on large tables, high "Rows Removed by Filter", actual time vs estimate mismatch. (3) Check if an index exists on the WHERE/JOIN columns — if not, create one and re-run EXPLAIN ANALYZE. (4) Check if the index is being used — the optimizer may choose Seq Scan if the query returns >20-30% of rows (low selectivity). (5) Check connection pool metrics — if `HikariPool.getActiveConnections()` equals max pool size, the problem is pool exhaustion, not the query.

**Q2. Explain the leftmost prefix rule for composite indexes.**

> A composite index on `(A, B, C)` is a B-tree sorted by A first, then B within each A group, then C within each (A, B) group. Queries can use the index if they filter/sort on a leftmost prefix: A alone, (A, B), or (A, B, C). But NOT B alone, C alone, or (B, C) — skipping the leftmost column means the index can't narrow the search. Think of a phone book sorted by (last name, first name): you can look up by last name, or by last name + first name, but not by first name alone. Column order matters: put the most frequently queried column first, or the most selective column first (most distinct values).

**Q3. What is a covering index and when would you use one?**

> A covering index contains ALL columns the query needs in the index itself — using the `INCLUDE` clause in PostgreSQL 11+. When the optimizer detects a covering index, it does an "Index Only Scan" — reading data entirely from the index, never touching the table's heap pages. This eliminates random I/O to the heap, which is the most expensive part of an index scan. Use it for frequently-run read queries (dashboards, reports) on tables where the indexed + included columns are much smaller than the full row. Trade-off: larger index → more write overhead → more disk space.

**Q4. How does HikariCP work and how do you size the pool?**

> HikariCP maintains a pool of reusable database connections. When a thread needs a connection, it borrows one from the pool (fast — ~0.1ms). When done, it returns it. If all connections are in use, the thread waits up to `connection-timeout` (default 30s) before throwing an exception. Pool size formula: `cores × 2 + 1` (from the HikariCP wiki). For 8 cores: ~17 connections. More is NOT better — each connection is a PostgreSQL backend process consuming ~10 MB RAM. Too many connections cause lock contention and context switching inside the database, reducing throughput. Key tuning: `maximum-pool-size`, `connection-timeout`, `leak-detection-threshold` (logs warning if a connection is held longer than N seconds — catches connection leaks).

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Your Java code → Hibernate SQL → connection from HikariCP → PostgreSQL query optimizer → execution plan (index scan or seq scan) → results. Performance can break at any layer. Diagnosis requires understanding each.
>
> **Part 2 — How/Why (30s):** `EXPLAIN ANALYZE` shows the actual execution plan: Seq Scan on a large table = needs an index. B-tree index (default) supports `=`, `<`, `>`, `BETWEEN`, `ORDER BY`. Composite index on (A, B) follows the leftmost prefix rule: helps queries on A or (A, B), NOT B alone. HikariCP pool size = `cores × 2 + 1` — more connections cause MORE contention in the database. Connection exhaustion happens when `@Transactional` holds a connection during slow external calls — narrow the transaction scope.
>
> **Part 3 — Gotcha (20s):** Two traps: PostgreSQL does NOT auto-index foreign keys (MySQL does) — missing FK index causes slow JOINs and cascade deletes. And holding a DB connection during an external API call (inside `@Transactional`) ties up the connection for the API's latency — 500ms × 10 connections = pool exhausted in seconds. Move external calls OUTSIDE the transaction.

---

## 🧾 TL;DR

- **EXPLAIN ANALYZE** shows the real execution plan. Seq Scan + high "Rows Removed" = needs an index.
- **B-tree** = default index. Supports `=`, `<`, `>`, `BETWEEN`, `LIKE 'prefix%'`, `ORDER BY`.
- **Composite index (A, B):** leftmost prefix rule — helps A, (A,B). NOT B alone.
- **Covering index** (`INCLUDE` clause): Index Only Scan — never touches the table. Fastest.
- **Optimizer may CORRECTLY choose Seq Scan** if >20-30% of rows match (sequential I/O > random I/O).
- **HikariCP:** default pool = 10. Formula: `cores × 2 + 1`. More connections ≠ more throughput.
- **Connection exhaustion:** don't hold connections during external API calls. Narrow `@Transactional` scope.
- **PostgreSQL does NOT auto-index foreign keys.** Always create FK indexes manually.
- **Projections** (`SELECT` only needed columns) instead of `SELECT *` — 10-100x less data.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #38 (Phase 6, Tier 1) of the JavaBackend KB completion roadmap. Staff-level depth: EXPLAIN ANALYZE output reading, 4 index types (B-tree/hash/GIN/GiST), composite index leftmost prefix rule with column ordering strategy, covering index (INCLUDE), optimizer cost-based decision (when Seq Scan is correct), HikariCP sizing formula + connection exhaustion diagnosis, JPA SELECT * trap + projection patterns, index maintenance cost + unused index detection, spring.jpa.show-sql limitations. Two production footguns: missing FK index, connection pool exhaustion from long transactions. |
