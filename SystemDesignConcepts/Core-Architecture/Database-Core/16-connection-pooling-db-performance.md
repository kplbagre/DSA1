# Connection Pooling & Database Performance — Fundamentals

---

## 🎯 Why This Matters

Every service talks to a database. Opening a new TCP connection for every query is 10–100ms overhead — unacceptable at 1000+ req/sec. Connection pooling reuses connections, reducing that overhead by 95%. If your service is slow, a maxed-out connection pool is the first suspect.

At SDE 3 level: you're expected to know why HikariCP is the standard for Java, how to size pools, and what happens when they saturate.

---

## 📖 What is Connection Pooling?

**Full form:** Database Connection Pool / Connection Pool Manager

**Simple analogy:** Imagine a restaurant with a phone line to suppliers. Opening a new phone connection (dialing, waiting for answer, authenticating) takes 10 seconds. Instead of hanging up after each order, the restaurant keeps the line open and reuses it for the next 10 orders in a batch. That's pooling — maintaining a **pre-opened set of reusable connections** rather than creating and destroying them on-demand.

**Core principle:** Every time your application needs to query a database, instead of opening a fresh TCP socket, authenticating, and then closing it (expensive), you borrow a pre-warmed connection from a pool of N idle connections. After the query completes, you return the connection to the pool, ready for the next request. This eliminates the 10–100ms connection overhead per query.

**Why it matters in system design:** At scale (1000+ requests/second), connection overhead becomes a throughput killer. A well-tuned pool reduces latency by 95% and allows your service to sustain higher concurrent load with fewer server resources.

---

## 🎨 Visual — System Topology: Connection Pooling in Architecture

```
CLIENT TIER
    │
    ▼
┌──────────────────────────────┐
│    API Service Instance      │
│  (Java application)          │
└──────────────┬───────────────┘
               │
    ┌──────────▼──────────┐
    │ Connection Pooling  │
    │  ┌────────────────┐ │
    │  │ ◯ ◯ ◯ ◯ ◯     │ │ (idle connections)
    │  │ idle=8/10     │ │
    │  └────────────────┘ │
    │  borrow() ↔ return()│
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   Database (MySQL)  │
    │  max_connections:50 │
    └─────────────────────┘

DATA TIER

TOPOLOGY INVARIANT:
   Each app instance maintains its own connection pool.
   Pool acts as a PROXY/GATEKEEPER between app and database.
   If app instances = 10, and pool_size = 20 each → 200 total DB connections
```

---

## 🎨 Visual — Connection Pool Lifecycle (Component Detail)

Think of a connection pool like a valet parking lot at a restaurant:

- **Valets (connections):** Each valet can handle one car (query) at a time. A small lot means fewer valets.
- **Parked cars (idle connections):** Valets don't disappear after parking one car — they stay available for the next guest.
- **Peak dinner rush (high traffic):** If all valets are busy and cars keep arriving, new cars must wait in a queue. If the queue fills up, the restaurant turns away new guests (connection timeout).
- **Idling costs:** Keeping valets on staff costs money even when it's quiet (idle connections keep TCP sockets open, consuming server memory).
- **Timeout on no show:** If a guest never picks up their car (a query hangs indefinitely), eventually the valet abandons the car and moves on (connection timeout/eviction).

**The key insight:** You size the lot based on peak demand (concurrent requests you expect), not total requests. A restaurant with 50 peak guests doesn't need 50 valets — maybe 10–15 is enough if average service time is 2 minutes.

---

## 🎨 Visual — Connection Pool Lifecycle

```
REQUEST ARRIVAL
  │
  ├─→ [Available connection pool]
  │        ┌─────────────┐
  │        │  ◯ ◯ ◯ ◯   │ (idle connections)
  │        │ idle=8/10  │
  │        └─────────────┘
  │             ↓
  │      [Acquire] ← borrow one
  │             ↓
  ├─→ [In-Use Connections]
  │        ┌─────────────┐
  │        │  ● ● ● ◯ ◯ │ (● = in use, ◯ = available)
  │        │ active=3/10│
  │        └─────────────┘
  │             ↓
  │      [Execute Query]
  │             ↓
  │      [Release] ← return to pool
  │             ↓
  └─→ [Available again]
           ◯ back in pool

SATURATION SCENARIO:
  All 10 connections in use → New request must wait
  ┌────────────┐
  │ Queue Full │ Waiting requests
  │  R11 → ⏳  │ timeout after maxWaitMillis
  │  R12 → ⏳  │
  └────────────┘

KEY INVARIANT:
   Pool size = max concurrent connections the service can sustain
   Queue depth = how many requests can wait without failing
   If requests exceed (pool + queue), reject with "too many connections" error
```

---

## ⚙️ How It Actually Works

**Steps:**

1. **Initialize the pool** — create N idle connections (corePoolSize). Keep them warm and ready.
2. **On request arrival** — borrow a connection from the idle list (or queue if none available).
3. **Execute the query** — the borrowed connection runs one query.
4. **Return the connection** — release it back to the idle pool immediately after query completes.
5. **Health check** — periodically validate idle connections (send a ping query) to catch stale ones. Discard if invalid.
6. **Evict old idle connections** — after idleTimeout, close idle connections to free resources.
7. **On saturation** — if all N connections are in use and more requests arrive, queue them. If queue is full, reject with a timeout error.

**Code example — HikariCP configuration (Java):**

```java
public class DataSourceConfig {
    @Bean
    public HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
        config.setUsername("user");
        config.setPassword("pass");

        // Pool sizing (critical)
        config.setMaximumPoolSize(20);           // Step 1 — max concurrent connections
        config.setMinimumIdle(5);                 // Step 1 — keep 5 warm always
        config.setConnectionTimeout(30000);       // Step 7 — wait 30s for available connection

        // Health checks
        config.setConnectionTestQuery("SELECT 1");  // Step 5 — ping to validate
        config.setIdleTimeout(600000);              // Step 6 — close idle after 10 min
        config.setMaxLifetime(1800000);             // Close ANY connection after 30 min

        // Real-world production settings
        config.setLeakDetectionThreshold(60000);   // Warn if connection not returned in 60s
        config.setAutoCommit(true);

        return new HikariDataSource(config);
    }
}

// Using the pool
@Service
public class OrderService {
    @Autowired
    private DataSource dataSource;

    public Order getOrder(Long orderId) {
        try (Connection conn = dataSource.getConnection()) {
            // Step 2 — borrow from pool
            String sql = "SELECT * FROM orders WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setLong(1, orderId);

            // Step 3 — execute
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapToOrder(rs);
            }
            // Step 4 — connection returned automatically (try-with-resources)
        } catch (SQLException e) {
            // If step 7 — queue is full, this throws after 30s
            throw new DataAccessException("DB unavailable", e);
        }
        return null;
    }
}
```

**What is HikariCP, and why does it fit here?**

HikariCP is a lightweight Java connection pooling library optimized for low latency (microsecond-scale connection borrow/return). It's the default in Spring Boot because it's **2–3x faster** than older pools (Commons DBCP) with less overhead. In an interview, if asked: *"HikariCP is the industry standard because it minimizes the latency of acquiring a connection from the pool — critical when you're handling 1000s of concurrent requests."*

---

## 🏢 Real World — Where Companies Use This

- **Razorpay** (payment processing): 100K+ concurrent requests during checkout bursts. Connection pool sized to match peak card-processing throughput. Oversizing wastes memory; undersizing causes checkout timeout failures.
- **Swiggy** (food delivery): Connection saturation during lunch rush (11:30 AM–1:30 PM) — app load spikes 5x. Pool tuned for 99th percentile traffic, not average.
- **BookMyShow** (ticketing): Flash sale (Diwali movie release): 50K users hit "Buy" simultaneously. Connection pool must absorb the spike. Pool metrics exposed in Datadog; alerts fire if queue grows above 80%.
- **Amazon Web Services** (RDS): Publishes "Maximum connections" as a service limit based on instance type (db.t2.micro = 20 max; db.r5.4xlarge = 16K max). If your pool size exceeds this, all connection attempts fail.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Your app connects to a relational database (MySQL, Postgres, Oracle) | Serverless functions with cold-start constraints (use AWS Lambda custom runtime instead) |
| You expect ≥ 100 concurrent requests | Single-threaded scripts or batch jobs (one connection is fine) |
| Connection setup time (TCP handshake + auth) is significant (> 1ms per connection) | You're already using a managed connection service (e.g., AWS RDS Proxy) |
| You need to reuse connections across many requests | Each connection lasts the lifetime of the request (no pooling benefit) |

**The common mistake:** Setting `maxPoolSize = 200` because "more is safer." This wastes memory, increases lock contention in the DB, and can actually slow down query execution. **Size = expected peak concurrent connections, NOT total requests.**

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Reuse eliminates 10–100ms connection setup per query. At 1000 req/sec with 10ms setup saved, that's **10 seconds of total latency removed per second**. Massive. |
| **You lose** | Idle connections consume memory (~1 MB per connection). A 100-connection pool on 100 services = 10 GB of wasted memory if traffic is low. |
| **Failure mode** | Pool saturation: all connections in-use, queue full → new requests timeout → user sees "database unavailable." Cascades. Undersizing the pool = P1 outage. |

---

## 🔬 Interview Q&As

### Q: "Our database is slow. How do you diagnose if it's the connection pool?"

> Check three metrics in this order: (1) pool utilization — if `active_connections ≈ max_pool_size` consistently, the pool is the bottleneck; increase `maxPoolSize`. (2) Queue depth — if requests are queueing for ≥ 100ms waiting for a free connection, the pool is too small. (3) Slow query log on the database — if DB queries are actually slow (not just waiting for connections), the problem is the query/schema, not the pool. Monitoring with Micrometer: `hikaricp.connections.active`, `hikaricp.connections.idle`, `hikaricp.connections.pending` reveal all.

### Q: "How do you choose maxPoolSize?"

> Formula: **connections = ((core_count × 2) + effective_spindle_count)**. For a 4-core CPU with one disk, that's roughly 9 connections. In practice for web services: start at threads (e.g., Tomcat default 200) but pool size much smaller — maybe 10–20 — because threads queue waiting for a free connection. A good starting point: `max_pool_size = (core_count * 2) + 1`. Then monitor and adjust. Under-provision and scale up; over-provision early and debug over-utilization. ⭐ **Tier 2**

### Q: "What happens if I set maxPoolSize = 5000?"

> You'll exhaust memory quickly (each connection ≈ 1 MB). More importantly, the database itself has a `max_connections` limit (MySQL default 151, Postgres default 100). If your app pool exceeds that, you'll get "too many connections" rejections. Additionally, the DB scheduler contends over 5000 locks — worse query performance overall. The pool size is determined by the database's capacity and your app's concurrency model, not by "more is safer." ⭐ **Tier 2 — failure mode**

### Q: "Design a connection pool that handles a 10x traffic spike."

> Use two strategies: (1) **Burst capacity** — set `maxPoolSize` to handle 99th percentile peak (if peak is 5x average, size the pool for 6x to leave headroom). (2) **Queue + backpressure** — when the pool saturates, queue incoming requests. If the queue fills, return HTTP 429 (Too Many Requests) rather than let requests hang. This signals downstream load balancers to shed traffic gracefully. At Swiggy scale: pool size = 50 (for 50 concurrent DB operations), queue size = 200 (buffer against bursts), reject after that. ⭐ **Tier 2 — system design**

### Q: "How does connection pooling interact with prepared statements?"

> Prepared statements are per-connection. When you borrow a connection from the pool, you can prepare statements on it. Ideally, reuse the PreparedStatement object across requests (prepared statements are thread-safe), not create new ones each time. If you create a new PreparedStatement every request, you add parsing overhead. Some pools support **statement caching** — automatically cache compiled statements per connection. In HikariCP + Spring, use `PreparedStatementCache` on the actual DataSource. ⭐ **Tier 2 — optimization**

### Q: "Your app is deployed across 3 regions. Does connection pooling change?"

> Each region has its own database (likely read replicas in other regions, writes to the primary). Each app instance in a region maintains its own connection pool. If region A has 10 app instances × 20-connection pool, that's 200 connections to the region A database — this is expected and correct. If you're accessing remote databases across regions, latency increases (100ms round-trip), so you might use a higher pool size to hide that latency. But primary rule stays: size = concurrent queries you're willing to sustain, not total requests. ⭐ **Tier 2 — distributed systems**

---

## 🧾 TL;DR

> "Connection pooling reuses TCP connections across requests to hide the cost of connection setup. Size the pool to match peak concurrent requests, not total throughput; over-sizing wastes memory and can exceed the database's own connection limit. If your database is slow, check pool saturation metrics first — it's often the culprit."

---

## 🔗 Related Concepts

- **`01-optimistic-pessimistic-locking.md`** — connection pooling pairs with locking strategies; if your pool saturates, your lock contention worsens
- **`12-data-modeling.md`** — schema design affects query time; bad queries = pool exhaustion even with a large pool
- **`06-distributed-locking.md`** — distributed locks may require dedicated "admin" connections outside the normal pool
- **`02-rate-limiting.md`** — rate limiting the API helps prevent pool saturation at the source

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — "Database Connection Pooling"** (YouTube) | Deep dive on HikariCP internals, why it's faster than DBCP, under-the-hood metrics | ~15 min |
| **HikariCP Documentation** — GitHub: brettwooldridge/HikariCP | Official config reference, leak detection, statement caching | ~10 min reference |
| **hellointerview.com — "SQL Performance Tuning"** | Connection pooling in context of database performance bottlenecks (includes indexing, query plans) | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 16. Added mental model (valet lot), HikariCP code example, sizing formula. |
