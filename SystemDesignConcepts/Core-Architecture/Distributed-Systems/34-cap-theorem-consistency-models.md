# 34 — CAP Theorem & Consistency Models

## 📖 What is CAP Theorem?

**Full form:** Consistency, Availability, Partition Tolerance theorem

**Simple analogy:** Imagine a bank with two branches and one ledger. When the communication link between branches fails (partition), you must choose: allow both branches to keep serving customers (availability) at risk of contradictory account balances (inconsistency), or shut down one branch to guarantee the ledger stays in sync (consistency). You can't have all three at the same time.

**Core principle:** In a distributed system facing a network partition, you can guarantee at most TWO of these three properties: Consistency (all nodes see the same data), Availability (system responds to requests), Partition Tolerance (survives network failures). Real systems must sacrifice one.

**Why it matters in system design:** Every system design decision (choosing SQL vs NoSQL, synchronous vs asynchronous replication, strong vs eventual consistency) is a CAP trade-off. Understanding which property you're sacrificing for each component drives architecture and SLA decisions.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| Consistency (CAP) | All nodes see the same data at the same time — every read returns the most recent write | Both US and EU regions show the same account balance immediately after a write |
| Availability (CAP) | Every non-failing node responds to requests — no timeouts, no "service unavailable" errors | Both regions keep serving customers even during a network partition |
| Partition Tolerance | The system continues operating when network communication between nodes is lost | The inter-region link fails for 10 seconds; nodes on each side must still decide what to do |
| Network Partition | A network failure that splits nodes into isolated groups that cannot communicate | US-East and EU-West regions lose connectivity for 10 seconds |
| CP System | A system that chooses Consistency over Availability during a partition — writes are rejected to keep all nodes in sync | ZooKeeper, synchronous-replication PostgreSQL — writes fail during partition |
| AP System | A system that chooses Availability over Consistency during a partition — serves potentially stale data but never goes down | Cassandra, DynamoDB — writes accepted in each region; they diverge and sync later |
| Eventual Consistency | A guarantee that all replicas will converge to the same value *eventually*, once the partition heals — no guarantee of when | DynamoDB with async replication: EU may be 200ms behind US but will catch up |
| Strong Consistency | Every read reflects the latest committed write — requires synchronous replication or quorum | `SELECT` on PostgreSQL primary after `COMMIT` always returns the written value |
| PACELC | Extension of CAP: "When Partition → choose A or C; Else (normal ops) → choose Latency or Consistency" | Cassandra is PA/EL (available during partition, low latency normally); sync Postgres is PC/EC |

---

## 🎯 Why This Matters

- **Problem:** Distributed systems at scale WILL experience network partitions. Knowing what you lose in that moment separates SDE 2 from SDE 3.
- **Interview signal:** "When this service fails over, are you sacrificing consistency or availability?" — this question appears in every system design round.
- **Senior expectation:** You should know which CAP property your technology choice provides, and how to design around the one it doesn't.

---

## 🧠 The Mental Model

Imagine a multi-region payment system. Region A (US) and Region B (EU) share one "source of truth" ledger. A network partition splits them for 10 seconds.

**During normal operation (no partition):**
- Every transaction writes to BOTH regions, waits for both to ack. Single source of truth. Consistency achieved.

**When partition hits (A and B can't talk):**
- A customer in US initiates a $100 transfer. Region A must decide: "Do I process this now without Region B's permission, or reject it?"
  - **If you choose AVAILABILITY (CP sacrificed):** Region A says "yes, I'll process it, EU will catch up later." Regions drift. You now accept inconsistency until they rejoin. This is **eventual consistency.**
  - **If you choose CONSISTENCY (AP sacrificed):** Region A says "I can't reach EU, so I won't process this." Customers see "service unavailable." But when partition heals, both regions are guaranteed in sync.

**The key insight:** You cannot freeze time and say "both regions stay synced AND I keep serving requests." One breaks. You pick which.

---

## 🎨 Visual — System Topology & CAP Trade-offs

```
FULL SYSTEM TOPOLOGY:
                    ┌──────────────────────────┐
                    │  Network Partition Risk  │
                    │      (happens in prod)   │
                    └──────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
             ┌──────▼────────┐    ┌─────▼───────┐
             │  Region A     │    │  Region B   │
             │  (US/East)    │    │  (EU/West)  │
             │               │    │             │
             │ Ledger: $1000 │    │ Ledger:$1000│
             │               │    │             │
             │ (write replicas)   │             │
             └──────────────┘     └─────────────┘
                    ▲                    ▲
                    │ replication sync   │
                    └────────────────────┘
                    (breaks during partition)

DURING PARTITION — You Choose:
  ┌─────────────────────────────────┐
  │ CP (Consistency + Partition):    │
  │ - Reject Region A writes         │
  │ - Guaranteed sync when healed    │
  │ - Users see "unavailable"        │
  │ Examples: PostgreSQL + strong    │
  │ replication, ZooKeeper           │
  └─────────────────────────────────┘

  ┌─────────────────────────────────┐
  │ AP (Availability + Partition):   │
  │ - Allow Region A & B both writes │
  │ - Temporary data mismatch        │
  │ - Eventually syncs (seconds)     │
  │ - Users get "maybe" responses    │
  │ Examples: DynamoDB, Cassandra,   │
  │ Riak                             │
  └─────────────────────────────────┘

KEY INVARIANT:
   Network partition WILL occur. The theorem forces a choice:
   - Trade consistency for uptime (AP)
   - Trade availability for correctness (CP)
   - Never achieve all three in a partition
```

---

## ⚙️ How It Actually Works

**Understanding the CAP proof:**

1. **Define the three properties first:**
   - **Consistency (C):** Every read returns the most recent write. All replicas agree on state.
   - **Availability (A):** Every non-failing node responds to requests. No timeouts, no "service unavailable."
   - **Partition Tolerance (P):** System continues operating even when network splits nodes into isolated groups.

2. **The partition scenario:**
   When nodes can't communicate, they are autonomous. Each group must decide independently whether to:
   - Answer requests (availability), risking divergent state
   - Refuse requests (strong consistency), accepting unavailability

3. **Why you can't have all three:**
   - P is mandatory (networks fail). You can't design it away.
   - C and A are mutually exclusive during P.
   - Any system claiming "all three" is either not facing partitions, or trading something else (latency, throughput).

**Code example — PostgreSQL CP vs DynamoDB AP choice:**

```java
// PostgreSQL-style (CP): Synchronous replication
public class CPDataStore {
    private Connection primary;
    private Connection replica;
    
    public void writeTransaction(String key, String value) {
        // Wait for both primary and replica to commit
        // or reject the write
        primary.executeUpdate("INSERT INTO ledger VALUES (?, ?)", key, value);
        replica.executeUpdate("INSERT INTO ledger VALUES (?, ?)", key, value);
        // If replica unreachable → write fails
        // Trade: availability (write rejected)
        // Gain: consistency (both always in sync)
    }
    
    public String read(String key) {
        // Read from primary (guaranteed fresh)
        return primary.query("SELECT value FROM ledger WHERE key = ?", key);
    }
}

// DynamoDB-style (AP): Asynchronous replication
public class APDataStore {
    private ExecutorService replicator;
    private Map<String, String> primaryData = new ConcurrentHashMap<>();
    private Map<String, String> replicaData = new ConcurrentHashMap<>();
    
    public void write(String key, String value) {
        // Write to primary immediately, return success
        primaryData.put(key, value);
        
        // Replicate asynchronously (fire and forget)
        replicator.submit(() -> {
            try {
                Thread.sleep(100); // simulate network latency
                replicaData.put(key, value);
            } catch (Exception e) {
                // Replica down? We already committed to primary
                // Trade: consistency (temporary mismatch)
                // Gain: availability (write always succeeds)
            }
        });
    }
    
    public String read(String key) {
        // Read from primary (might be stale on replica, that's ok)
        String value = primaryData.get(key);
        return value != null ? value : "not found yet";
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **Amazon DynamoDB (AP choice):** During the 2012 AWS US-EAST-1 outage, DynamoDB's multi-region design chose to keep accepting writes in each region rather than block. Regions temporarily diverged, but users never saw "service unavailable." Later synced when partition healed.

- **Google Spanner (AP leaning):** Chose multi-master replication with Paxos consensus. When regions partition, Spanner sacrifices strong consistency temporarily to maintain availability. Designed for "high availability at the cost of strict CAP."

- **PayPal (CP choice):** Payment ledgers MUST be consistent. PayPal's ledger uses strong replication (synchronous writes across data centers). If replication fails, writes are rejected. Consistency guaranteed, but users might see transient "try again" errors.

- **Netflix (AP choice):** Content delivery and recommendations are eventual-consistency friendly. Cassandra (AP) powers Netflix's backend — writes succeed even if replicas are slow or offline. Eventual consistency is acceptable for "viewing history" and "recommendations."

- **LinkedIn (CP for critical, AP for secondary):** LinkedIn uses a hybrid: critical user identity data on strong-consistency PostgreSQL (CP), but social graph and recommendations on eventual-consistency Espresso (AP). Different data, different guarantees.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Designing multi-region replication strategy | Your system is single-region only (CAP doesn't apply, you have all three) |
| Choosing between SQL and NoSQL | You're trying to avoid the choice (you can't — every choice is a CAP trade-off) |
| Explaining why your database fails over the way it does | You think CAP is negotiable. It isn't — networks partition, you must choose |
| Defending a trade-off to your PM ("Why is the EU region down?") | You blame CAP without actually understanding your system's choice |

**The common mistake:** Claiming "we built a system with all three CAP properties" or "we're CAP-agnostic." You're not. You made a trade-off, consciously or not.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Understanding which corner of CAP your system sits in; ability to predict failure modes |
| **You lose** | The ability to claim your system handles everything perfectly. One property is always the weakest link |
| **Failure mode** | If you choose AP (eventual consistency) but rely on strong consistency guarantees in your application logic, you'll lose money on duplicate charges or lost transactions |

---

## 🧭 PACELC — CAP's More Useful Extension

CAP only describes behaviour during a partition. But partitions are rare. **PACELC** (proposed by Daniel Abadi, 2012) extends the model to cover the much more common case — normal operation.

> **P: When there is a Partition** → choose between Availability (A) and Consistency (C)
> **ELC: Else (no partition) in normal operation** → choose between Latency (L) and Consistency (C)

Every distributed database falls into one of four quadrants:

```
                        PARTITION behaviour
                   ┌────────────────┬─────────────────┐
                   │  PA            │  PC             │
                   │  (choose A)    │  (choose C)     │
  NORMAL ops ──────├────────────────┼─────────────────┤
  trade-off        │  PA/EL         │  PC/EC          │
  EL = low latency │                │                 │
  EC = consistency │  Cassandra     │  sync Postgres  │
                   │  DynamoDB      │  ZooKeeper      │
                   │  Riak          │  Spanner        │
                   ├────────────────┼─────────────────┤
                   │  PA/EC         │  PC/EL          │
                   │                │                 │
                   │  MongoDB       │  (rare —        │
                   │  (majority-    │   uncommon      │
                   │   write quorum)│   combination)  │
                   └────────────────┴─────────────────┘

KEY: EL = optimise for Latency during normal ops
     EC = pay latency cost for Consistency during normal ops
```

**Reading the quadrants:**

| System | Partition choice | Normal-ops choice | Why |
|--------|-----------------|-------------------|-----|
| Cassandra / DynamoDB / Riak | A — stay available | L — low latency | Writes succeed immediately; replicate async; reads from nearest replica are fast but possibly stale |
| Sync Postgres / ZooKeeper / Spanner | C — reject writes | C — consistency | Quorum required even in normal ops; reads always fresh; latency is higher |
| MongoDB (majority write concern) | A — stay available | C — consistency | During partition: keep accepting writes. During normal ops: majority-quorum write pays the latency cost for consistency |

**Why PACELC matters for interviews:**

CAP's binary choice (CP or AP) is partition-only — it says nothing about what you pay during the 99.9% of time when no partition is occurring. PACELC captures the real day-to-day trade-off. When a candidate says "we use Cassandra because it's AP," the PACELC follow-up is: "and you're accepting read staleness (EL) even during healthy network operation." When a candidate says "we use synchronous Postgres," the follow-up is: "and you're paying cross-datacenter round-trip latency on every write (EC) even when there's no failure." That's the full picture — not just the disaster scenario.

---

## 🔬 Interview Q&As

### Q: "What does CAP theorem actually say? Aren't all systems consistent, available, and partition-tolerant?"

> CAP says: in a distributed system, during a network partition, you can guarantee at most TWO of Consistency, Availability, Partition Tolerance. Partition Tolerance is mandatory (networks WILL partition). So you choose between Consistency and Availability. Single-region systems can have all three because no partition. But distributed systems must trade one.

### Q: "Our database uses synchronous replication. Isn't that all three CAP properties?"

> Synchronous replication gives you CP (consistency + partition-tolerance), not all three. When the replica is unreachable, writes are rejected. That's sacrificing Availability. When the replica catches up, consistency is guaranteed. You traded Availability for Consistency.

### Q: "So DynamoDB is 'always available' and PostgreSQL is 'always consistent'? Which should we use?"

> Neither claim is absolute. DynamoDB is eventually consistent but highly available (AP). PostgreSQL can be strong-consistency (CP) if configured for synchronous replication, or weak-consistency (AP) if using asynchronous replication. Your choice depends on the data. User profiles? AP is fine. Payment ledgers? You need CP. Same database, different configurations for different data.

### Q: "If we go multi-region, does CAP force us to be eventually consistent?"

> Not necessarily. Multi-region forces you to face CAP decisions. But you can choose CP by requiring both regions to ack every write. That's synchronously replicated PostgreSQL across regions. You sacrifice Availability (writes block if one region is slow). Or choose AP: accept eventual consistency in exchange for high availability across regions. Most companies choose AP at scale because the consistency window is small.

### Q: "During a partition, can we guarantee Consistency while staying Available?"

> No — that's literally what CAP says you can't do. During a partition, you choose: accept that regions diverge (AP), or accept that writes are rejected (CP). You don't get both. After the partition heals, you can merge/reconcile (which is expensive and application-specific).

### Q: "What happens if we add more regions or replicas? Does that change CAP?"

> No. Adding more replicas doesn't change CAP. You still have the same fundamental trade-off. More replicas increase Availability (more nodes can serve), but they make Consistency harder (more nodes to sync). Partition tolerance is the law of physics, not negotiable.

### Q: "How do we know which corner of CAP we're actually in? How do we test for it?"

> Simulate a partition (disable network between regions) and observe the behavior: (1) Do both regions keep accepting writes? → AP. (2) Do writes get rejected in one region? → CP. (3) Do you see inconsistent data across regions? → You're AP. (4) Can you serve requests with stale data? → You're AP. Your monitoring and tests should explicitly check this behavior.

---

### Q: "CAP gives us CP vs AP. Real systems seem to fall on a spectrum. What's PACELC?"

> PACELC extends CAP to cover **normal operations**, not just partitions. The full form: "When there's a Partition, choose A or C. **Else** (no partition), choose **Latency or Consistency**." Cassandra is PA/EL — stays available during partition, and in normal ops favours low latency (reads can be stale). ZooKeeper and sync Postgres are PC/EC — reject writes during partition AND require quorum during normal ops, paying latency for consistency. MongoDB with majority write concern is PA/EC — availability during partition, but consistency during normal ops. The practical insight: "CP vs AP" only tells you the failure-mode dimension. PACELC tells you both dimensions — the full cost of your technology choice.

---

### Q: "What is causal consistency, and when is it stronger than eventual consistency?"

> **Causal consistency** guarantees: if operation A causally precedes operation B (B depends on A — for example, B is a reply to A), then any node that has seen B must also have seen A. It is stronger than eventual consistency (which makes no ordering guarantees) but weaker than strong consistency (which requires all reads to see the latest write). **Example:** User posts a comment (operation A), then replies to it (operation B). In an eventually consistent system, a replica might serve the reply before the original post, leaving a confusing gap. In a causally consistent system that is impossible — seeing B implies seeing A. **Implementation:** vector clocks or logical timestamps travel with each write; a reader rejects a read that would violate causal ordering. **When to use it:** comment threads, collaborative editing (Google Docs), chat systems, any domain where "response requires prior write" matters. Available in: MongoDB with session-level causal consistency (`causally consistent session`), CockroachDB, AWS DynamoDB (conditional writes approximate this). Most plain AP databases do NOT offer causal consistency by default.

---

## 🧾 TL;DR

> "When we partition, we sacrifice either Consistency or Availability — CAP forces the choice. We designed our ledger for Consistency (synchronous replication), so writes can fail temporarily. Our recommendations cache uses Availability (async replication), so slight staleness is acceptable."

---

## 🔗 Related Concepts

- **Distributed Locking (06):** CAP forces decisions about lock consistency in distributed systems
- **Consensus Algorithms (37):** Raft/Paxos are mechanisms to achieve CP (strong consistency) during partitions
- **Event Sourcing (22):** Eventual-consistency pattern that addresses AP implications
- **Database Replication (29):** Replication strategies are how we make CAP trade-offs concrete

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Consistency Models" — Arpit Bhayani** (arpitbhayani.me/blog/consistency-models) | Deep classification of consistency levels (strong, eventual, causal). CAP covers binary choice; this note covers the spectrum. | ~15 min |
| **"A Plain English Introduction to CAP Theorem"** — Evan Knuth (medium.com) | Different angle on the proof; useful if you want to retell it to an interviewer without memorizing. | ~10 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Initial creation. Added CAP fundamentals, CP vs AP system examples, real-world company decisions (DynamoDB, Spanner, PayPal, Netflix, LinkedIn). Two Q&As on multi-region implications. |
| Jul 1, 2026 | Added PACELC section (Abadi 2012) with 2×2 quadrant grid (PA/EL = Cassandra/DynamoDB, PC/EC = sync Postgres/ZooKeeper/Spanner, PA/EC = MongoDB); added two new Q&As: PACELC explanation and causal consistency with vector clock context. |
