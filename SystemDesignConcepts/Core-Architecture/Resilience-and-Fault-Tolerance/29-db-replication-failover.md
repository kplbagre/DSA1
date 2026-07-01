# Database Replication & Failover — High Availability

> Database replication copies data from a primary (write) database to replicas (read-only). When primary fails, a replica is promoted to primary (failover). At SDE 3: you must know master-slave topology, write-ahead logs (WAL), sync vs async replication, and RPO/RTO trade-offs.

---

## 🎯 Why This Matters

Your database is down. Orders can't be placed. Revenue: $0. Without replication, downtime = data loss + lost revenue. With replication, primary goes down → replica is promoted → downtime: 30 seconds, data loss: minimal. Replication is foundational to 99.99% uptime. In interviews, candidates often say "just backup" — backups recover from disasters; replication provides instant failover. Both are needed.

---

## 📖 What is Database Replication & WAL? (Full Form & Basics)

**Replication** = copying data from primary (main) database to replicas (backup copies).

**WAL = Write-Ahead Log** (also called binlog in MySQL)
- A log file that records EVERY write before it's applied to the database
- Ensures durability (if crash happens, log survives and can replay)
- Used to replicate writes to replicas (send log entries, not entire tables)

**How it works:**
```
Client writes to PRIMARY database
    ↓
Primary records write in WAL ("INSERT INTO users ...")
    ↓
Primary applies write to actual tables
    ↓
Primary sends WAL entries to REPLICA
    ↓
Replica applies same writes locally
    ↓
Both PRIMARY and REPLICA now have same data
```

**Simple analogy:**
- Bank teller makes transaction
- Teller writes in transaction log (WAL): "2pm: withdraw $100 from account 123"
- Teller updates account balance
- At end of day, send log to backup bank
- Backup bank replays: "2pm: withdraw $100 from account 123" → now in sync

If primary crashes, replica has entire log and can replay to catch up.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **WAL (Write-Ahead Log)** | append-only log of every database write, recorded before applying to tables; foundation of durability and replication | `INSERT INTO orders ...` → written to WAL → applied to table → shipped to replicas |
| **Binlog** | MySQL's equivalent of WAL; can be statement-based (SQL text) or row-based (before/after image) | row-based binlog: records exact before/after state of each changed row |
| **Synchronous Replication** | primary waits for replica to confirm write before returning success to client; zero data loss, higher write latency | write takes 5ms (primary) + 15ms (replica ack) = 20ms total |
| **Asynchronous Replication** | primary returns success immediately; replica catches up in the background; faster but risks data loss on crash | primary acks in 5ms; if primary crashes before replica syncs → last writes lost |
| **Replication Lag** | delay between primary committing a write and replica applying it; reads from replica may return stale data | primary writes at T=0; replica applies at T=500ms → 500ms of stale reads possible |
| **RPO (Recovery Point Objective)** | maximum acceptable data loss measured in time | RPO=0 → synchronous replication required; RPO=5min → async replication acceptable |
| **RTO (Recovery Time Objective)** | maximum acceptable downtime duration after a failure | RTO=30s → automated failover required; RTO=4h → manual promotion acceptable |
| **Failover Promotion** | replica is elected and promoted to primary after primary failure; DNS or VIP updated to point to new primary | replica detects 3 missed heartbeats → promotes itself → Route53 DNS updated |
| **Quorum** | majority of replicas must acknowledge a write for it to be considered durable | 3 replicas: quorum=2; write committed only when 2 of 3 confirm receipt |

---

## 🧠 The Mental Model

Imagine a manuscript author with a scribe:

**Without replication:**
- Author writes original manuscript.
- Backups: monthly copy to archive.
- If author's manuscript burns, recovery takes weeks (restore from backup, loses 1 month of work).

**With replication:**
- Author writes original manuscript (primary).
- Scribe continuously copies every page (replica, read-only).
- If author's manuscript burns, scribe's copy is complete and ready to become the new original.
- Downtime: 5 minutes (promote scribe's copy, redirect writes).
- Data loss: 0 (scribe was copying in real-time).

**The key insight:** Replication trades **write latency** (each write must replicate to replicas) for **availability** (instant failover, zero-copy downtime).

---

## 🎨 Visual — Database Replication Architecture

### Full System Topology — Where Replication Sits

```
APPLICATION LAYER (Services)
    ↓ (write: POST /orders)
    ↓ (read: GET /orders)
┌──────────────────────────────────────────────────────────────┐
│ LOAD BALANCER                                                │
│ (routes writes to PRIMARY, reads to PRIMARY/REPLICAS)       │
└──────────────────────────────────────────────────────────────┘
    ↓
    ├─→ (writes)
    │   ↓
    │   ┌──────────────────────────────────────────┐
    │   │ PRIMARY DB (Master)                      │
    │   │ Accepts reads + writes                   │
    │   │ Maintains write-ahead log (WAL)          │
    │   │ ┌────────────────────────────────────┐   │
    │   │ │ User Table | Order Table | Product │   │
    │   │ └────────────────────────────────────┘   │
    │   └──────────────────────────────────────────┘
    │       ↓ (replicate WAL continuously)
    │       ├─→ (async or sync)
    │       ↓
    │   ┌──────────────────────────────────────────┐
    │   │ REPLICA #1 (Read-only)                   │
    │   │ Applies transactions from WAL             │
    │   │ (slightly behind primary)                │
    │   ├──────────────────────────────────────────┤
    │   │ User Table | Order Table | Product │     │
    │   └──────────────────────────────────────────┘
    │       ↓
    │   ┌──────────────────────────────────────────┐
    │   │ REPLICA #2 (Read-only)                   │
    │   │ Same as Replica #1                       │
    │   └──────────────────────────────────────────┘
    │
    └─→ (reads, balanced across PRIMARY + REPLICAS)
        ↓
        Returns data from any replica

REPLICATION FLOW:
┌─────────────────────────────────────┐
│ 1. Client writes: INSERT INTO users │
│ 2. PRIMARY executes transaction     │
│ 3. PRIMARY writes to WAL (binlog)   │
│ 4. PRIMARY responds to client (ACK) │
│ 5. PRIMARY sends WAL to replicas    │
│ 6. REPLICA applies WAL              │
│ 7. REPLICA is now in sync           │
└─────────────────────────────────────┘

FAILOVER (Primary dies):
┌──────────────────────────────┐
│ PRIMARY: crash!              │
│ Load balancer detects down   │
│ (heartbeat timeout)          │
└──────────────────────────────┘
    ↓
┌──────────────────────────────┐
│ Election: which replica       │
│ becomes new primary?          │
│ (most recent WAL offset wins) │
└──────────────────────────────┘
    ↓
┌──────────────────────────────┐
│ REPLICA #1 promoted           │
│ Now read-write               │
│ Accepts writes               │
└──────────────────────────────┘
    ↓
New Primary + REPLICA #2 = new topology

KEY INVARIANT:
   Primary: single point of write.
   Replicas: read-only copies (eventually consistent).
   WAL: write-ahead log captures every transaction.
   Failover: automatic promotion of most-caught-up replica.
```

### Component Detail — Replication Mechanisms

```
SYNCHRONOUS vs ASYNCHRONOUS REPLICATION:

ASYNC (Fast, risky):
┌─────────────────────┐
│ 1. Client writes    │
│ 2. PRIMARY commits  │
│ 3. ACK to client    │
│ 4. (later) Send to  │
│    replicas         │
└─────────────────────┘
    Latency: <1ms (no wait for replicas)
    Risk: If primary crashes before sending WAL to replicas,
          recent writes are lost (RPO = seconds/minutes)

SYNC (Slow, safe):
┌─────────────────────┐
│ 1. Client writes    │
│ 2. PRIMARY commits  │
│ 3. Wait for replicas│
│    to ACK receipt   │
│ 4. ACK to client    │
└─────────────────────┘
    Latency: 50-200ms (wait for replication)
    Safety: If primary crashes, all writes are on replicas (RPO = 0)
    Tradeoff: Slower writes for guaranteed durability

SEMI-SYNC (Balanced):
   Wait for at least 1 replica to ACK, then ACK client.
   Compromise: reasonable latency + strong durability.


WRITE-AHEAD LOG (WAL) — The Replication Engine:
┌────────────────────────────────────────────┐
│ WAL is a sequential, immutable log of      │
│ every transaction written to primary.      │
│                                            │
│ Example WAL entries:                       │
│ [Offset 0] BEGIN                           │
│ [Offset 1] INSERT INTO users ...           │
│ [Offset 2] UPDATE orders SET status='paid' │
│ [Offset 3] COMMIT                          │
│ [Offset 4] BEGIN                           │
│ [Offset 5] DELETE FROM products ...        │
│ [Offset 6] COMMIT                          │
│ ...                                        │
│ [Offset N] <- latest                       │
│                                            │
│ Primary writes entry → commits → sends to replicas
│ Replicas read from their last offset:      │
│   Replica #1: offset=6 (ready for offset 7)
│   Replica #2: offset=4 (behind, will catch up)
│                                            │
│ RPO (Recovery Point Objective):            │
│ If primary crashes now (offset=6),         │
│ Replica #1 can take over with no loss      │
│ (all offsets 0-6 are applied locally).     │
│ Replica #2 will be promoted but has data   │
│ loss (offsets 5-6 missing from disk).      │
└────────────────────────────────────────────┘


RPO vs RTO Trade-offs:

RPO (Recovery Point Objective):
   How much data are you willing to lose?
   RPO = time between last backup and disaster
   Sync replication: RPO = 0 (no data loss)
   Async replication: RPO = 30 sec - 5 min

RTO (Recovery Time Objective):
   How long can system be down?
   RTO = time to recover and resume operations
   Automatic failover: RTO = 30 sec
   Manual failover: RTO = 5-10 min
   Backup restore: RTO = 1-24 hours

Goal: Minimize both RPO and RTO.
Constraint: RPO=0 (sync) increases write latency.

                    RPO  | RTO  | Write Latency
    Async replica:  high | low  | very low
    Sync replica:   low  | low  | high
    Backup only:    high | high | very low
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Client sends write request** to primary (e.g., INSERT order).
2. **Primary executes and commits** the transaction (data is durable on primary's disk).
3. **Primary writes to WAL** (sequential log of the transaction).
4. **Primary responds to client** with ACK (in async mode).
5. **Primary sends WAL to replicas** (via binary log streaming).
6. **Replicas receive WAL entries** and apply them to their local databases.
7. **Replicas acknowledge** receipt (in sync mode, primary waits for this before step 4).
8. **Replication lag** = time delay between primary transaction and replica application.
9. **Replica failure** = replica recovers, re-applies missed WAL entries, catches up.
10. **Primary failure** = most-caught-up replica is promoted; becomes new primary.

```java
// Primary Database Configuration (MySQL with replication)

// my.cnf (MySQL configuration)
/*
[mysqld]
server-id = 1                           # Unique ID for primary
log-bin = mysql-bin                     # Enable binary log (WAL)
binlog-format = ROW                     # Log individual row changes
sync-binlog = 1                         # Sync WAL to disk after each commit
                                        # (1 = sync, 0 = async faster but risky)
*/

// Java: Application writing to primary
@Service
public class OrderService {
    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    // Step 1-4 — Write to primary
    public void createOrder(Order order) {
        try (Connection conn = primaryDataSource.getConnection()) {
            // Step 1 — Begin transaction
            conn.setAutoCommit(false);

            // Step 2 — Execute write
            String sql = "INSERT INTO orders (id, customer_id, amount, status) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, order.getId());
                stmt.setString(2, order.getCustomerId());
                stmt.setDouble(3, order.getAmount());
                stmt.setString(4, "PENDING");
                stmt.executeUpdate();

                // Step 2 — Commit (durable on primary)
                conn.commit();
                // Step 3 — Primary writes to WAL (automatic by MySQL)
                // Step 4 — Primary ACKs client (return)
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

// Step 5-6 — Replica receives and applies WAL (automatic in MySQL)
// Replica configuration:
/*
[mysqld]
server-id = 2                           # Different ID for replica
relay-log = mysql-relay-bin             # Local copy of primary's WAL
relay-log-index = mysql-relay-bin.index
read-only = ON                          # Prevent writes to replica
*/

// Replica automatically:
// 1. Connects to primary (TCP port 3306)
// 2. Receives binary log events (WAL)
// 3. Writes to relay-log locally
// 4. Applies events to local database (SQL thread)
// 5. Updates replication offset

// Application: Read from replicas (load balancing)
@Service
public class OrderQueryService {
    @Autowired
    @Qualifier("replicaDataSource")
    private DataSource replicaDataSource;

    // Read-heavy queries go to replicas
    public List<Order> getCustomerOrders(String customerId) {
        try (Connection conn = replicaDataSource.getConnection()) {
            String sql = "SELECT * FROM orders WHERE customer_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, customerId);
                return parseResults(stmt.executeQuery());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

// Failover Detection & Promotion (using Heartbeat monitor)
@Component
public class ReplicationFailoverManager {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Scheduled(fixedRate = 5000) // Check every 5 seconds
    public void monitorPrimaryHealth() {
        try {
            // Step 9 — Check if primary is alive
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class);
            // Primary is responsive
        } catch (Exception e) {
            // Step 10 — Primary is down
            log.error("Primary database is unreachable. Initiating failover...");

            // Step 10 — Find most-caught-up replica
            String mostCaughtUpReplica = findMostCaughtUpReplica();

            // Step 10 — Promote replica
            promoteReplica(mostCaughtUpReplica);

            // Step 10 — Update application config
            updatePrimaryEndpoint(mostCaughtUpReplica);

            log.info("Failover complete. New primary: {}", mostCaughtUpReplica);
        }
    }

    private String findMostCaughtUpReplica() {
        // Query all replicas: SHOW SLAVE STATUS
        // Find replica with highest Exec_Master_Log_Pos (farthest in WAL)
        List<String> replicas = getReplicaList();
        String mostCaught = null;
        long maxOffset = -1;

        for (String replica : replicas) {
            long offset = getReplicaOffset(replica);
            if (offset > maxOffset) {
                maxOffset = offset;
                mostCaught = replica;
            }
        }

        return mostCaught;
    }

    private void promoteReplica(String replicaHost) {
        // Connect to replica and:
        // 1. STOP SLAVE; (pause replication)
        // 2. SET GLOBAL READ_ONLY = OFF; (allow writes)
        // 3. RESET SLAVE ALL; (clear primary reference)
        // Now it's a standalone primary
    }

    private void updatePrimaryEndpoint(String newPrimaryHost) {
        // Update application config: primaryDataSource now points to newPrimaryHost
        // All writes redirect to new primary
        // Old replicas reconfigure to stream from new primary
    }
}
```

### What is Binlog (Binary Log), and why does it fit here?

Binlog is **MySQL's WAL (write-ahead log)**. Every transaction is logged before being applied, ensuring durability and enabling replication. Replicas subscribe to the binlog and apply transactions in order. In an interview, if asked: *"Binlog is MySQL's transaction log — every INSERT, UPDATE, DELETE is logged before committing. Replicas read from binlog and apply changes locally. Binlog enables point-in-time recovery (restore database to any moment in time) and replication (fanout to multiple replicas)."*

---

## 🏢 Real World — Where Companies Use This

- **Amazon RDS (AWS Managed Replication):** AWS manages primary + replica (same AZ and different AZ). Automatic failover if primary fails. Backup taken from replica (no impact on primary performance).
- **Netflix (Cassandra Ring Replication):** Cassandra replicates data across multiple nodes in a ring. Each key is replicated to N nodes. If one node fails, other N-1 nodes serve reads. No single point of failure.
- **Stripe (PostgreSQL Replication):** Payment database replicated synchronously to multiple replicas. Writes wait for at least one replica ACK (strong consistency). If primary fails, replica is promoted within seconds.
- **Uber (MySQL + MariaDB Replication):** Trip data replicated across multiple regions (primary in US-East, replicas in US-West, Europe). Reads distributed to nearest replica. Regional failover if region fails.
- **Spotify (Cassandra Multi-Region):** User data replicated across data centers (Stockholm, US). Rack-aware replication ensures replicas are on different servers. Handles concurrent failures of multiple nodes.

---

## 🧭 When to Use vs When NOT to Use

| Use replication when | Do NOT use when |
|---|---|
| High availability needed (99.99% uptime) | Single-region, non-critical data (testing DB) |
| Read scalability needed (many read queries) | Tiny datasets, one client |
| You can tolerate replication lag | Real-time consistency required for ALL operations |
| Failover should be automatic | Manual failover acceptable |
| Regional disaster recovery | Single server acceptable |

**The common mistake:** Using async replication and assuming zero data loss. Async means recent writes can be lost in a crash. Use sync or semi-sync for critical data.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | High availability (99.99%+ uptime). Read scalability (distribute reads across replicas). Automatic failover (no manual intervention). Disaster recovery (replicas in different regions). Backup isolation (backup from replica, no performance impact on primary). |
| **You lose** | Write latency increased (sync replication waits for replicas). Replication lag (async replicas slightly behind). Complexity (manage multiple databases, failover logic). Network overhead (continuous WAL streaming). Disk space (each replica stores full copy). |
| **Failure mode** | Primary crashes → automatic failover (RTO ~30s). Replication lag too high → replicas miss recent writes on primary failure (RPO = lag). Network partition → primary and replicas can't communicate (split-brain: both think they're primary). Mitigation: short failover timeout, sync replication for critical data, Zookeeper/Consul for consensus-based failover. |

---

## 🔬 Interview Q&As

### Q: "Your primary and replica are in different data centers (US-East and US-West). Primary crashes. What happens?"

> Replica detects primary is unreachable (TCP timeout). Automatic failover: replica is promoted to primary. If replication was sync, no data loss. If async, you lost writes since last replication (RPO = lag). New primary is now in US-West. To restore high availability, spin up new replica in US-East. Meanwhile, all traffic routes to US-West primary. ⭐ **Tier 2 — Disaster recovery**

### Q: "You have 1 primary + 2 replicas. Replication is synchronous. A write comes in. Should the primary wait for both replicas to ACK, or just one?"

> Just one. If primary waits for both and one is slow, write latency is dominated by the slowest replica (tail latency problem). If primary waits for at least one (quorum), write latency is better. If primary crashes after 1 ACK, new primary is promoted (has the write). The other replica catches up via WAL. ⭐ **Tier 2 — Quorum consensus**

### Q: "Replica is lagging (replication lag = 10 seconds). User reads from replica and sees old data. How do you handle?"

> Option 1: Don't load-balance reads to lagged replicas (check Exec_Master_Log_Pos). Option 2: Read-after-write consistency: client reads from primary for their own writes, replica for others. Option 3: Use eventual consistency (accept staleness, TTL-based refresh). ⭐ **Tier 2 — Consistency**

### Q: "How do you prevent split-brain (both primary and replica think they're primary)?"

> Use Zookeeper or Consul as consensus layer. Before promotion, replica must acquire a distributed lock. If primary recovers, it checks if it still holds lock. If not, it stops accepting writes (self-fences). This prevents two primaries from diverging. ⭐ **Tier 2 — Split-brain prevention**

---

## 🧾 TL;DR

> "Primary database replicates writes via WAL to replicas (read-only). Async replication: fast writes, data loss risk. Sync replication: slow writes, guaranteed durability. Failover: most-caught-up replica promoted to primary. RPO (data loss) vs RTO (downtime) trade-off."

---

## 🔗 Related Concepts

- **`03-caching.md`** — Replicas used for read scaling (distributed cache layer)
- **`25-monitoring-observability-fundamentals.md`** — Monitor replication lag, failover latency
- **`12-data-modeling.md`** — Primary-replica topology affects schema design

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — Database Replication** (YouTube) | WAL mechanics, sync vs async, RPO/RTO, failover election | ~20 min |
| **MongoDB Replication Guide** | Replica sets, heartbeat, automatic failover in MongoDB | ~15 min read |
| **AWS RDS Multi-AZ Documentation** | AWS managed failover, how RDS handles replication | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 29. Covered master-slave replication topology, WAL (write-ahead log), sync vs async mechanisms, RPO/RTO trade-offs, automatic failover detection and promotion, replication lag handling. |
