# Database Transaction Isolation Levels

> **Standard followed:** `notes-standards.md`
> **Related concepts:** `01-optimistic-pessimistic-locking.md`, `06-distributed-locking.md`, `04-idempotency.md`

---

## 📖 What is Transaction Isolation?

**Full form:** Database Transaction Isolation Levels (part of the ACID properties — Isolation is the "I")

**Simple analogy:** Imagine a busy bank branch where multiple tellers process transactions simultaneously. Isolation levels decide how much one teller's in-progress work is visible to other tellers. At the lowest level, tellers can see a colleague's sticky note mid-transaction — even before it's official. At the highest level, every teller works in a sealed room and only sees the official ledger.

**Core principle:** When multiple transactions run concurrently, isolation levels control which intermediate states each transaction can observe from others. Lower isolation = more anomalies possible, but higher throughput. Higher isolation = clean reads, but more locking and lower concurrency.

**Why it matters in system design:** The wrong isolation level causes silent data corruption — double-charges, inventory oversells, inconsistent reports — at scale. Every senior engineer must know which level to pick for payments vs. analytics vs. general-purpose web apps.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Isolation Level** | setting that controls how much one concurrent transaction can see from another's uncommitted work | `@Transactional(isolation = Isolation.READ_COMMITTED)` in Spring |
| **Dirty Read** | reading another transaction's uncommitted data; that data may be rolled back → you read something that never existed | T1 writes balance=200, not committed; T2 reads 200; T1 rolls back → T2 acted on phantom data |
| **Non-Repeatable Read** | reading same row twice in one transaction and getting different values because another transaction committed between the two reads | T1 reads balance=100; T2 commits balance=50; T1 reads again → gets 50 (changed!) |
| **Phantom Read** | running same query twice in one transaction and getting different row COUNT because another transaction inserted/deleted rows between the reads | T1 queries `SELECT * WHERE age>18` → 10 rows; T2 inserts 5 new adults; T1 re-queries → 15 rows |
| **READ_UNCOMMITTED** | sees uncommitted writes from other transactions; fastest, dirtiest; never use for financial data | can see a row that gets rolled back 1ms later |
| **READ_COMMITTED** | only sees committed writes; default in most databases (Postgres, Oracle); prevents dirty reads | T2's uncommitted balance change is invisible to T1 |
| **REPEATABLE_READ** | same row always returns same value within one transaction; MySQL default; prevents dirty + non-repeatable reads | T1 reads balance=100 twice → gets 100 both times even if T2 committed a change |
| **SERIALIZABLE** | transactions execute as if they ran one at a time; prevents all anomalies; heaviest locking | prevents phantom reads; use for financial summations and inventory checks |
| **MVCC** | Multi-Version Concurrency Control — DB keeps multiple versions of a row so readers don't block writers | Postgres: readers see a snapshot; writers create a new version; no reader-writer blocking |

---

## 🎯 Why This Matters

The right isolation level is the difference between a correct financial ledger and a double-charged customer. It surfaces in **system design rounds** whenever you discuss databases, transactions, or consistency guarantees — and in **deep-dive rounds** when an interviewer asks "how does your inventory system prevent overselling." Senior engineers are expected to name the specific isolation level and explain the trade-off, not just say "wrap it in a transaction."

---

## 🧠 The Mental Model

Think of a shared whiteboard in a team room. Multiple people are updating it simultaneously — some are mid-sentence, some just erased something, some are writing new lines.

**READ_UNCOMMITTED** is standing in the doorway and reading whatever is on the board — including half-erased words and notes someone is still writing. You might read something that gets erased in 5 seconds.

**READ_COMMITTED** is only reading the board when someone steps back and says "done." You never read mid-write notes. But if you look away and back, the board may have changed — someone else finished their update in between.

**REPEATABLE_READ** is taking a photograph of the board when you walk in. For your entire session, you read from that photograph. The board might change, but you always see your snapshot. However, someone can add a new sticky note in the corner you hadn't photographed.

**SERIALIZABLE** is locking the room when you walk in. Nobody else can write while you're working. When you leave, the room is available. The board you see is exactly what you expect — no new notes, no changes. The cost: everyone else waits outside.

The three read anomalies map to what each level prevents: a **dirty read** is reading the half-erased word (uncommitted data). A **non-repeatable read** is looking away and back to find the sentence changed. A **phantom read** is looking at a list and finding a new row appeared that wasn't there a moment ago.

The key insight is: isolation is a **spectrum of protection vs. throughput** — higher levels protect more but serialize more work.

---

## 🎨 Visual — System Topology & Component Flow

```
FULL SYSTEM TOPOLOGY:
Where isolation levels operate in a typical web service stack

┌───────────┐    ┌───────────┐    ┌────────────────────────┐
│  Client   │───▶│    LB     │───▶│   Service Pods (JVM)   │
│  Browser  │    │           │    │                        │
└───────────┘    └───────────┘    │  @Transactional        │
                                  │  isolation = X         │
                                  │                        │
                                  │  [Isolation negotiated │
                                  │   per transaction here]│
                                  └────────────┬───────────┘
                                               │ JDBC / JPA
                                               ▼
                                  ┌────────────────────────┐
                                  │   Database Engine      │
                                  │   (PostgreSQL/MySQL)   │
                                  │                        │
                                  │  ← Isolation enforced  │
                                  │    here, not in app    │
                                  │                        │
                                  │  MVCC (PG): snapshot   │
                                  │  Lock (MySQL): row/gap │
                                  └────────────────────────┘

KEY INVARIANT:
   Isolation level is a per-transaction setting negotiated
   between the application layer and the database engine.
   The database enforces it — the app only declares intent.
```

```
COMPONENT DETAIL: Isolation Level vs. Read Phenomenon Matrix

  Phenomenon         | READ_UNCOMMITTED | READ_COMMITTED | REPEATABLE_READ | SERIALIZABLE
  -------------------|-----------------|----------------|-----------------|-------------
  Dirty Read         |   POSSIBLE ❌   |  prevented ✅  |  prevented ✅   | prevented ✅
  Non-Repeatable Read|   POSSIBLE ❌   |  POSSIBLE ❌   |  prevented ✅   | prevented ✅
  Phantom Read       |   POSSIBLE ❌   |  POSSIBLE ❌   |  POSSIBLE* ❌   | prevented ✅

  * MySQL InnoDB at REPEATABLE_READ uses gap locks to prevent phantoms.
    PostgreSQL at REPEATABLE_READ uses MVCC — still allows write-skew.

  LEVEL DEFAULTS:
    PostgreSQL  → READ_COMMITTED  (uses MVCC, no read locks)
    MySQL InnoDB→ REPEATABLE_READ (uses row + gap locks)
    Oracle      → READ_COMMITTED
    CockroachDB → SERIALIZABLE only (no weaker levels)

KEY INVARIANT:
   Higher isolation number = more phenomena prevented = less concurrency.
   Pick the lowest level that prevents the anomalies your use case cannot tolerate.
```

---

## ⚙️ How It Actually Works

**Steps (READ phenomena and how each isolation level prevents them):**

1. **Dirty Read** — Transaction A reads a row that Transaction B has modified but not yet committed. If B rolls back, A read data that never officially existed.
2. **Non-Repeatable Read** — Transaction A reads a row. Transaction B commits an update to that row. A reads the same row again and sees different data.
3. **Phantom Read** — Transaction A runs a range query (`WHERE price < 100`). Transaction B inserts a new row matching that range and commits. A re-runs the same query and sees a new row that wasn't there before.
4. **MVCC (Multi-Version Concurrency Control)** — PostgreSQL's mechanism to prevent dirty and non-repeatable reads without locking readers. Every write creates a new row version with a transaction ID timestamp. Readers see the latest committed version as of their transaction start — they never block writers and writers never block readers.
5. **Gap Locks (MySQL InnoDB)** — At REPEATABLE_READ, MySQL locks not just the matched rows but the gaps between index entries. This prevents another transaction from inserting into a range you queried, blocking phantom reads without full serialization.
6. **SERIALIZABLE** — The database uses either 2-phase locking (lock everything) or Serializable Snapshot Isolation (SSI, used by PostgreSQL 9.1+) to detect and abort transactions that would produce non-serializable outcomes.

### What is MVCC, and why does it fit here?

MVCC (Multi-Version Concurrency Control) is PostgreSQL's strategy for isolation without read locks. Every row update creates a new version tagged with the committing transaction's ID. A reading transaction sees all versions committed before it started — its own private snapshot. Readers and writers never block each other. In an interview, if asked: "PostgreSQL implements READ_COMMITTED and REPEATABLE_READ using MVCC snapshots — readers never block writers. The snapshot granularity changes: READ_COMMITTED re-takes a snapshot per statement; REPEATABLE_READ takes one snapshot for the entire transaction."

### What is a Gap Lock, and why does it fit here?

A gap lock in MySQL InnoDB is a lock on the index space *between* two existing index values, not on a row itself. If you query `WHERE id BETWEEN 10 AND 20`, MySQL locks the gap so no new row can be inserted with an ID in that range during your transaction. This prevents phantom reads at REPEATABLE_READ without requiring full serialization. In an interview, if asked: "MySQL at REPEATABLE_READ uses gap locks on index ranges to block phantom inserts — PostgreSQL solves the same problem differently, using MVCC snapshots."

```java
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// --- Spring declarative isolation (most common in production) ---
public class PaymentService {

    // Step 1 — SERIALIZABLE: no dirty, non-repeatable, or phantom reads
    // Use for financial ledgers where correctness > throughput
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void transferFunds(long fromAccount, long toAccount, long amount) {
        Account from = accountRepo.findById(fromAccount)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Account to = accountRepo.findById(toAccount)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (from.getBalance() < amount) {
            throw new InsufficientFundsException("Insufficient balance");
        }
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        accountRepo.save(from);
        accountRepo.save(to);
    }

    // Step 2 — READ_COMMITTED: prevents dirty reads only
    // Sufficient for most web app reads — profile fetch, catalog browsing
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountSummary getAccountSummary(long accountId) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return new AccountSummary(account.getId(), account.getBalance());
    }
}

// --- Manual JDBC: showing a dirty-read scenario with two connections ---
public class IsolationDemo {

    // Step 3 — demonstrates what READ_UNCOMMITTED allows
    // (rarely used in production; shown here to understand the anomaly)
    public void demonstrateDirtyRead(String jdbcUrl) throws Exception {
        Connection writer = DriverManager.getConnection(jdbcUrl);
        Connection reader = DriverManager.getConnection(jdbcUrl);

        // Writer starts updating but does NOT commit yet
        writer.setAutoCommit(false);
        PreparedStatement update = writer.prepareStatement(
                "UPDATE accounts SET balance = balance - 500 WHERE id = 1");
        update.executeUpdate();
        // balance is now 9500 in the writer's in-progress transaction

        // Step 4 — reader at READ_UNCOMMITTED sees the uncommitted 9500
        reader.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        reader.setAutoCommit(false);
        PreparedStatement select = reader.prepareStatement(
                "SELECT balance FROM accounts WHERE id = 1");
        ResultSet rs = select.executeQuery();
        if (rs.next()) {
            long balance = rs.getLong("balance");
            // balance = 9500 — a dirty read of uncommitted data
            System.out.println("Dirty read balance: " + balance);
        }

        // Step 5 — writer rolls back; the 9500 never existed
        writer.rollback();
        // The reader already acted on data that was never committed

        rs.close();
        select.close();
        update.close();
        reader.close();
        writer.close();
    }

    // Step 6 — correct approach: READ_COMMITTED prevents this
    public void correctReadCommitted(String jdbcUrl) throws Exception {
        Connection reader = DriverManager.getConnection(jdbcUrl);
        reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        reader.setAutoCommit(false);
        PreparedStatement select = reader.prepareStatement(
                "SELECT balance FROM accounts WHERE id = 1");
        ResultSet rs = select.executeQuery();
        // Reader only sees committed balance = 10000, not the in-progress 9500
        if (rs.next()) {
            long balance = rs.getLong("balance");
            System.out.println("Clean read balance: " + balance);
        }
        reader.commit();
        rs.close();
        select.close();
        reader.close();
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **PostgreSQL / Supabase** (default READ_COMMITTED with MVCC): PostgreSQL defaults to READ_COMMITTED and uses MVCC snapshots so readers never block writers. Most web apps running on Supabase or Heroku Postgres get dirty-read protection by default with no configuration — but must explicitly upgrade to SERIALIZABLE for financial writes.

- **MySQL InnoDB / PlanetScale** (default REPEATABLE_READ with gap locks): MySQL defaults to REPEATABLE_READ and uses gap locks to block phantom inserts. Rails and Laravel apps on MySQL get stronger isolation than PostgreSQL's default — important to know when debugging "why does this query behave differently on Postgres vs MySQL."

- **Oracle Database** (READ_COMMITTED with row-level MVCC): Oracle only supports READ_COMMITTED and SERIALIZABLE — no REPEATABLE_READ. Large enterprise banking systems (HSBC, JP Morgan Oracle deployments) rely on Oracle's MVCC for consistent reads without locking. Moving such systems to PostgreSQL requires understanding that the behavior is similar but subtly different.

- **CockroachDB** (SERIALIZABLE only): CockroachDB enforces SERIALIZABLE isolation for every transaction — no weaker levels. This means zero dirty reads, phantom reads, or write skew by design. Stripe and Cockroach Labs use this for financial distributed ledgers where silent anomalies are unacceptable. The cost is retries on transaction conflicts (CockroachDB surfaces them as `40001` errors).

- **Stripe** (SERIALIZABLE for ledger transactions): Stripe's core payment ledger uses SERIALIZABLE isolation in their PostgreSQL clusters. Every charge, refund, and payout runs at the highest isolation level to prevent double-charges from concurrent retries. Lower-isolation reads (dashboards, reports) run separately to avoid the throughput penalty on the hot write path.

- **PayPal** (READ_COMMITTED for reads, SERIALIZABLE for debits): PayPal separates their read workload (account summaries, transaction history) from write workload (debit/credit operations). Read replicas run at READ_COMMITTED for throughput; primary debit/credit path runs at SERIALIZABLE. This pattern — isolation tiering — is the production-grade answer for high-volume fintech.

---

## 🧭 When to Use vs When NOT to Use

| Use SERIALIZABLE when | Do NOT use SERIALIZABLE when |
|---|---|
| Financial debits/credits, ledger writes | Read-heavy reporting or analytics queries |
| Inventory decrement for last-item scenarios | User profile reads, catalog browsing |
| Any write where concurrent anomalies cause money loss | Operations that tolerate eventual consistency |
| CockroachDB distributed transactions (it's the only option) | High-throughput writes on hot rows (checkout queues) |

| Use READ_COMMITTED when | Do NOT use READ_COMMITTED when |
|---|---|
| General-purpose web app reads | You need the same row to return identical data in two reads |
| PostgreSQL default — works for most CRUD | Inventory checks followed by inventory decrements in same txn |
| Dashboard reads, user-facing queries | Anything involving a balance check + write in the same transaction |

**The common mistake:** Leaving isolation at the database default (READ_COMMITTED for PostgreSQL, REPEATABLE_READ for MySQL) for all transactions — including financial writes. Default isolation is chosen for throughput, not correctness. Always explicitly set isolation level for any transaction that reads-then-writes a value.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Higher isolation gives serializable correctness — no dirty, non-repeatable, or phantom reads; safe for financial ledgers and inventory decrements |
| **You lose** | Throughput — SERIALIZABLE causes more lock contention, more transaction aborts/retries, and slower concurrent write performance; InnoDB's REPEATABLE_READ takes **gap locks** to prevent phantoms, which blocks concurrent inserts into queried ranges (added contention) |
| **Failure mode** | Using too-low isolation (READ_COMMITTED) for a balance-check-then-debit pattern causes write skew: two transactions both read "balance sufficient," both debit, both commit — resulting in a negative balance. Using too-high isolation (SERIALIZABLE) for analytics queries causes unnecessary lock contention and timeout errors under load. |

---

## 🔬 Interview Q&As

### Q: "What are the four SQL isolation levels and what do they protect against?"
> READ_UNCOMMITTED prevents nothing — you can see uncommitted data from concurrent transactions (dirty reads). READ_COMMITTED prevents dirty reads only — you always see committed data, but a second read of the same row may return different results. REPEATABLE_READ prevents dirty and non-repeatable reads — your snapshot is consistent for the transaction, though new rows can still appear. SERIALIZABLE prevents all three anomalies: dirty reads, non-repeatable reads, and phantom reads — transactions execute as if they were serial.

### Q: "Explain dirty read, non-repeatable read, and phantom read with an example."
> Dirty read: your transaction reads an account balance of $500 that another transaction is in the process of updating but hasn't committed — if that transaction rolls back, you acted on data that never existed. Non-repeatable read: you read a product price as $10 at the start of a transaction, another transaction updates it to $15 and commits, you read it again and get $15 — the same query returned different results within your transaction. Phantom read: you query "all orders where total > $100" and get 5 rows, another transaction inserts a new order matching that condition and commits, you re-run the same query and get 6 rows — a row appeared that wasn't there when you first queried.

### Q: "What isolation level does PostgreSQL use by default, and how does it prevent dirty reads without locks?"
> PostgreSQL defaults to READ_COMMITTED and implements it using MVCC — Multi-Version Concurrency Control. Every row update creates a new version tagged with the committing transaction's ID. Readers see the latest committed version as of their query start; they never see in-progress writes from other transactions. Crucially, readers don't acquire read locks — so writers are never blocked by readers and readers are never blocked by writers. This is why PostgreSQL handles high read concurrency well at its default isolation level.

### Q: "When would you use SERIALIZABLE isolation in a real system?"
> For any operation that reads a value and then writes based on that read — specifically where a concurrent transaction doing the same thing would produce incorrect data. The classic example is a financial transfer: read balance, check sufficiency, debit. At READ_COMMITTED, two concurrent transactions can both read "balance = $1000," both decide it's sufficient for a $900 transfer, and both commit — leaving a -$800 balance. SERIALIZABLE prevents this by detecting the conflict and aborting one transaction. I'd use it for payment ledgers, inventory last-item decrements, and seat-reservation confirmations.

### Q: "How does MySQL REPEATABLE_READ differ from PostgreSQL REPEATABLE_READ?"
> MySQL at REPEATABLE_READ uses gap locks on index ranges to prevent phantom reads — it physically blocks other transactions from inserting into ranges you've queried. PostgreSQL at REPEATABLE_READ uses MVCC snapshots — you see a consistent snapshot from transaction start, so new inserts by concurrent transactions don't appear in your re-queries. MySQL's approach blocks concurrent inserts (more locking); PostgreSQL's approach never blocks but allows write-skew anomalies that SERIALIZABLE would catch. For an interview: they both say "REPEATABLE_READ" but their behavior on phantoms is different, and PostgreSQL's REPEATABLE_READ is susceptible to write-skew in ways MySQL's is not.

### Q: "What is write-skew and which isolation level prevents it?"
> Write-skew is when two concurrent transactions each read an overlapping data set, make decisions based on what they read, and write to different rows — where the combined effect violates an application invariant. Example: a system requires at least one on-call doctor. Two doctors simultaneously check "are there other on-call doctors?" — both see each other and both go off-call. Neither transaction conflicted on the same row, but the result is zero doctors on call. Only SERIALIZABLE isolation prevents write-skew — REPEATABLE_READ does not, even in PostgreSQL, because the transactions wrote different rows.

### Q (Tier 2): "Your PostgreSQL app uses READ_COMMITTED. A user adds items to a cart, we decrement inventory in the same transaction. Is this safe?"
> No — this is a classic **lost update** (read-modify-write race), the anomaly the SQL standard doesn't name but interviewers always probe. The transaction reads inventory count as 1, decides to proceed, but between the read and the decrement another transaction decremented inventory to 0 and committed; if the code writes back a value it computed from the stale read (e.g., `SET inventory = 0`), it clobbers the other transaction's update. The fixes, in order of preference: (a) **atomic write** — `UPDATE ... SET inventory = inventory - 1 WHERE id = ? AND inventory > 0` (no read-then-write at all — the cleanest fix, works even at READ_COMMITTED); (b) **`SELECT FOR UPDATE`** to serialize the read-modify-write on that row; (c) **optimistic lock** with a version column. Note: simply upgrading to REPEATABLE_READ does NOT reliably fix a blind lost update — PostgreSQL RR aborts the second writer with a serialization error (first-updater-wins, so you must retry), and MySQL RR relies on locking reads. READ_COMMITTED is fine for read-only queries but insufficient for read-then-write on shared counters unless you use one of (a)–(c).

### Q (Tier 2): "CockroachDB only supports SERIALIZABLE. Won't that destroy throughput for a high-volume e-commerce system?"
> This is the right concern, but CockroachDB's SERIALIZABLE is implemented with Serializable Snapshot Isolation (SSI), not two-phase locking. SSI lets transactions proceed optimistically without acquiring read locks — it detects conflicts only at commit time and aborts the transaction that would create a non-serializable outcome. In practice, for e-commerce with millions of products, most transactions touch different rows and never conflict — so SSI throughput approaches READ_COMMITTED in the common case. The cost appears only on hot rows (last-item flash sale). The production pattern at Stripe: shard hot inventory rows, keep each shard below the contention threshold, and let SERIALIZABLE handle the rest for free.

### Q (Tier 2): "An interviewer asks: why not just use SERIALIZABLE for everything and stop worrying about isolation levels?"
> Three reasons. First, throughput: SERIALIZABLE causes more transaction aborts under contention — your application must retry aborted transactions, adding latency. Second, deadlock risk: some SERIALIZABLE implementations use 2PL (two-phase locking), which increases deadlock probability under concurrent write patterns. Third, lock contention: range queries at SERIALIZABLE lock index ranges, blocking concurrent inserts on those ranges — a product catalog query can accidentally block new product inserts. The right model is isolation tiering: use SERIALIZABLE only on the write path where correctness is critical (payments, inventory decrements), and READ_COMMITTED for the read path (dashboards, search, catalog). The separation of read and write replicas in most scaled systems naturally enables this.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "For a payment or inventory system I'd use SERIALIZABLE on the write path to prevent write-skew and phantom reads, but READ_COMMITTED on read replicas for dashboards — isolation tiering gives you correctness where it matters and throughput everywhere else."

---

## 🔗 Related Concepts

- **`01-optimistic-pessimistic-locking.md`** — optimistic locking (version-column CAS) is correct at READ_COMMITTED; the atomic conditional UPDATE, not the isolation level, prevents lost updates. Pessimistic `SELECT FOR UPDATE` serializes only the specific rows it locks
- **`06-distributed-locking.md`** — when isolation needs to span multiple services or databases rather than a single DB transaction
- **`04-idempotency.md`** — SERIALIZABLE transactions that abort and retry require idempotent operations to avoid double-applying side effects
- **`../../Production-Grade/System-Design-Patterns/42-inventory-management-booking.md`** — inventory decrements are the canonical use case where isolation level selection directly prevents overselling

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"PostgreSQL Isolation Levels"** — PostgreSQL official docs (https://www.postgresql.org/docs/current/transaction-iso.html) | Exact MVCC snapshot semantics, SSI implementation details, and the specific anomalies PostgreSQL's REPEATABLE_READ still allows | ~15 min read |
| **"Designing Data-Intensive Applications" Chapter 7** — Martin Kleppmann | Deep treatment of write-skew, phantoms, and why even REPEATABLE_READ is insufficient for some use cases — adds the academic foundation | ~40 min read |
| **"Serializable Isolation for Snapshot Databases"** — Cahill et al. (ACM) | The paper behind PostgreSQL's SSI implementation — explains why SSI achieves SERIALIZABLE without the throughput penalty of 2PL | ~25 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | File created. Covers all 4 SQL isolation levels, 3 read phenomena, standard matrix, MVCC (PostgreSQL), gap locks (MySQL InnoDB), Spring @Transactional isolation, JDBC manual isolation demo. 9 Q&As (5 Tier 1 + 3 Tier 2 + 1 worked). Companies: PostgreSQL/Supabase, MySQL/PlanetScale, Oracle, CockroachDB, Stripe, PayPal. |
| Jul 19, 2026 | **Factual fixes.** (1) Relabeled the cart/inventory Q&A anomaly from "non-repeatable read trap" to its correct name — **lost update** (read-modify-write race); added the atomic-write fix as the preferred remedy and the caveat that RR alone doesn't cleanly fix a blind lost update (Postgres RR aborts, MySQL RR locks). (2) Fixed the garbled "REPEATABLE_READ blocks phantom-prevention gap locks" trade-off line. (3) Corrected the cross-reference that repeated the "optimistic locking requires REPEATABLE_READ" myth. |
