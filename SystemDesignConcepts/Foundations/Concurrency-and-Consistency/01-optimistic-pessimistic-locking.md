# Optimistic and Pessimistic Locking

> **Standard followed:** `notes-standards.md`
> **Related concepts:** `06-distributed-locking.md`, `04-idempotency.md`

---

## 🎯 Why This Matters

When two threads or two users try to modify the same data at the same time, one of them will corrupt it — unless you have a strategy to control who wins. Optimistic and pessimistic locking are the two fundamental strategies for this at the **database layer**.

This shows up in the **PS round and System Design round** whenever the problem involves booking, inventory, payments, or any shared mutable resource. Interviewers expect a senior engineer to immediately name the strategy and explain when to pick which.

---

## 📖 What is Optimistic/Pessimistic Locking?

**Full form:** Optimistic Lock / Pessimistic Lock (Concurrency Control Strategies)

**Core principle:** Two strategies for handling concurrent writes to shared data:
- **Optimistic:** Assume conflicts are rare; check for conflicts after the fact (via version column). If conflict detected, retry.
- **Pessimistic:** Assume conflicts will happen; lock the resource BEFORE writing. Others wait for the lock.

**Simple analogy — Optimistic:** Two colleagues edit a shared document offline. Both assume the other won't edit. If both save, the second person gets an error and has to redo their changes on top of the first person's edits.

**Simple analogy — Pessimistic:** A single office bathroom key. Before entering, you take the key off the hook (lock acquired). While you're inside, others wait. After you leave and hang the key back, the next person can enter.

**Why it matters in system design:** Optimistic locking has better throughput when conflicts are rare (many readers/fewer writers). Pessimistic locking is simpler if conflicts are frequent or isolation guarantees are critical.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Optimistic Locking** | no lock acquired; conflict detected after the fact via a version check; retry if conflict | read row (version=7), update → `WHERE version=7`; if version changed → retry |
| **Pessimistic Locking** | lock acquired BEFORE the read; other transactions wait until the lock is released | `SELECT * FROM seats WHERE id=1 FOR UPDATE` — other writers block |
| **Version Column** | integer or timestamp column incremented on every write; optimistic lock mechanism | `ALTER TABLE orders ADD COLUMN version INT DEFAULT 0` |
| **`SELECT FOR UPDATE`** | SQL that reads a row AND places an exclusive lock on it; the pessimistic lock primitive | `SELECT * FROM inventory WHERE id=5 FOR UPDATE` — row locked until `COMMIT` |
| **Lost Update** | two transactions read same value, both modify it, second write overwrites first | T1 reads balance=100, T2 reads balance=100, T1 writes 90, T2 writes 80 → T1's write lost |
| **Conflict** | two concurrent transactions tried to modify the same row; one of them must retry or fail | optimistic: update returns 0 rows (version mismatch) → conflict detected → retry |
| **`@Version` (JPA)** | JPA annotation that auto-manages a version column; framework adds version check to every UPDATE | `@Version Long version;` — Hibernate throws `OptimisticLockException` on stale update |
| **Stale Read** | optimistic: reading a row that has since been updated by another transaction | T1 reads version=7, T2 commits version=8; T1's update will fail (stale) |

---

## 🎨 Visual — System Topology: Locking in Database Architecture

```
APPLICATION SERVERS (multiple threads/processes)
    │
    ├─ Thread A: "Reserve seat"
    │
    ├─ Thread B: "Reserve seat"
    │
    └─ Thread C: "Reserve seat"
             │
             ▼
    ┌──────────────────────────────────┐
    │ Database (shared resource)        │
    │                                  │
    │ Table: SeatInventory             │
    │ ┌──────────────────────────────┐ │
    │ │ bus_id │ seats │ version     │ │
    │ │ B1     │ 30    │ 7           │ │
    │ └──────────────────────────────┘ │
    │                                  │
    │ STRATEGY 1: OPTIMISTIC           │
    │ - No locks acquired              │
    │ - Conflict detected via version  │
    │ - First writer wins (v=8)        │
    │ - Others retry                   │
    │                                  │
    │ STRATEGY 2: PESSIMISTIC          │
    │ - SELECT ... FOR UPDATE          │
    │ - Row locked 🔒                  │
    │ - Others wait (blocked)          │
    │ - Lock released on COMMIT        │
    │                                  │
    └──────────────────────────────────┘

KEY INVARIANT:
   Optimistic = conflict detection + retry
   Pessimistic = prevention via locking
   Both prevent lost updates on concurrent writes
```

---

## 🎨 Visual — Optimistic vs Pessimistic: Concurrent Writer Behavior (Component Detail)

**Optimistic Locking — the "offline document editor" strategy:**

Imagine two colleagues, Priya and Ravi, both download the same report PDF on Friday evening to edit offline. They both work on their changes. Monday morning, both try to save their version back. Whoever saves first wins. The second person gets a message: *"This document was changed while you were working. Please re-read and try again."* Nobody locked anything — they were just optimistic that the other person wouldn't edit the same file.

In databases: you read a row, remember its `version = 7`, do your work, then write with `WHERE version = 7`. If someone else already changed it, the version is now 8, your update hits **0 rows**, and you retry.

**Pessimistic Locking — the "single bathroom key" strategy:**

Some offices have a single physical key for the bathroom. You take the key off the hook, go use the bathroom, come back, hang the key. While you have the key, nobody else can go — they wait outside. It's a physical lock on the resource. You're pessimistic that someone will barge in if you don't lock.

In databases: `SELECT ... FOR UPDATE` grabs a lock on the row. Anyone else trying to read-for-update waits at the door until you `COMMIT`.

**The key insight is:** Optimistic locking is not actually a "lock" — it's a **conflict detection after the fact**. Pessimistic locking is a real lock that **prevents the conflict from happening**.

---

## 📋 Isolation Levels and Their Role

Database isolation levels determine what data concurrent transactions can see from each other. There are three main levels: **READ_COMMITTED**, **REPEATABLE_READ**, and **SERIALIZABLE**.

**READ_COMMITTED** — a transaction only sees committed data from other transactions. The danger: if Thread A reads a value at T1 and again at T3, the value might have changed because another thread committed in between (a "non-repeatable read").

**REPEATABLE_READ** — a transaction sees a consistent snapshot as it was when the transaction started. Any reads of the same row within the transaction always return the same value, even if other transactions commit changes. This is the ideal isolation level for optimistic locking — the version column remains stable throughout the transaction, so the version check at commit time is reliable.

**SERIALIZABLE** — the strongest isolation level. Transactions execute as if they were serial (one after another), with no interleaving. The database prevents not only non-repeatable reads but also **phantom reads** (when a new row matching a query appears because another transaction inserted it after your initial read). Serializable is what pessimistic locking effectively achieves for the locked rows.

**The key connection:** Optimistic locking assumes REPEATABLE_READ isolation — the transaction sees a consistent snapshot, so the version check works reliably. If you used optimistic locking at READ_COMMITTED, you'd introduce lost-update anomalies: you read a value at T1, do work, then find the version changed at T3 for reasons you never observed. Pessimistic locking, by contrast, enforces SERIALIZABLE-like semantics for the locked rows — the lock prevents anyone else from reading or writing until you release it, eliminating non-repeatable reads and phantoms entirely.

**Phantom reads and gap locks:** A phantom read occurs when you query rows matching a condition (e.g., `SELECT * FROM orders WHERE price > 100`), and another transaction inserts a row matching that condition. On a re-read, a new row appears. To prevent this at SERIALIZABLE isolation, databases use **gap locks** (locks on index gaps between rows) in addition to row locks. The database handles this automatically — you don't implement gap locks yourself — but it's valuable to know that pessimistic locking at the highest isolation level prevents phantoms, while optimistic locking at REPEATABLE_READ may allow them in edge cases.

**In an interview, if asked:** "Optimistic locking relies on REPEATABLE_READ isolation so the version column stays stable throughout the transaction. Pessimistic locking effectively upgrades the isolation level to SERIALIZABLE for locked rows by preventing concurrent reads and writes entirely. The choice between the two is not just about conflict frequency, but also about what isolation level the use case requires."

---

## 🎨 Visual — How Each Strategy Handles Two Concurrent Writers

```
OPTIMISTIC LOCKING
══════════════════

Time →  T1          T2          T3           T4          T5

        Thread A reads              Thread A writes
        [seats=30, v=7]             UPDATE seats=20
                                    WHERE v=7
                                    → 1 row updated ✅  A WINS

        Thread B reads              Thread B writes
        [seats=30, v=7]             UPDATE seats=5
                                    WHERE v=7
                                    → 0 rows updated ❌

                                                         B retries:
                                                         reads [seats=20, v=8]
                                                         needs 25 > 20
                                                         → REJECTED cleanly

KEY INVARIANT:
   Only the first writer's WHERE version=N succeeds.
   Every concurrent writer gets 0 rows and must retry with fresh data.


PESSIMISTIC LOCKING
═══════════════════

Thread A: SELECT ... FOR UPDATE ──► ROW LOCKED 🔒
                                        │
Thread B: SELECT ... FOR UPDATE ──► WAITING... ⏳
                                        │
Thread A: reads seats=30                │
Thread A: seats=20, COMMIT ─────────► LOCK RELEASED 🔓
                                        │
                                    Thread B: LOCK ACQUIRED 🔒
                                    Thread B: reads seats=20
                                    Thread B: needs 25 > 20 → REJECTED

KEY INVARIANT:
   Only one thread holds the lock at a time.
   The second thread always reads fresh, post-commit data.
```

---

## ⚙️ How It Actually Works

### Optimistic Locking in Java (Spring Boot + JPA/Hibernate)

**Steps:**
1. **Add `@Version` field** to the entity — Hibernate tracks this automatically.
2. **Read the entity** — Hibernate loads the current version with it.
3. **Modify and save** — Hibernate generates `UPDATE ... WHERE id=? AND version=?` automatically.
4. **Handle the failure** — if version mismatch, Hibernate throws `OptimisticLockException`. Catch and retry.

```java
@Entity
public class SeatInventory {

    @Id
    private Long busId;

    private int availableSeats;

    // Step 1 — @Version tells Hibernate to track this column
    @Version
    private int version;
}
```

```java
@Service
public class BookingService {

    // Step 2-3 — read, modify, save (Hibernate handles the WHERE version=? check)
    @Transactional
    public void bookSeats(Long busId, int count) {
        SeatInventory inventory = repository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        if (inventory.getAvailableSeats() < count) {
            throw new InsufficientSeatsException("Not enough seats");
        }
        inventory.setAvailableSeats(inventory.getAvailableSeats() - count);
        // Hibernate generates: UPDATE seat_inventory SET seats=?, version=8
        //                      WHERE bus_id=? AND version=7
        repository.save(inventory);
    }

    // Step 4 — retry on conflict
    @Retryable(
        retryFor = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50)
    )
    @Transactional
    public void bookSeatsWithRetry(Long busId, int count) {
        bookSeats(busId, count);
    }
}
```

### Pessimistic Locking in Java (Spring Boot + JPA)

**Steps:**
1. **Acquire row-level lock** using `@Lock(PESSIMISTIC_WRITE)` on the repository method.
2. **Read inside a transaction** — the row is now locked for the duration.
3. **Modify and commit** — lock is released when the transaction ends.
4. **Other threads unblock** — they now read the post-commit value.

```java
public interface SeatInventoryRepository extends JpaRepository<SeatInventory, Long> {

    // Step 1 — PESSIMISTIC_WRITE maps to SELECT ... FOR UPDATE
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatInventory s WHERE s.busId = :busId")
    Optional<SeatInventory> findByIdWithLock(@Param("busId") Long busId);
}
```

```java
@Transactional
public void bookSeatsWithPessimisticLock(Long busId, int count) {

    // Step 2 — read with lock; other threads wanting this row will wait here
    SeatInventory inventory = repository.findByIdWithLock(busId)
            .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));

    // Step 3 — modify
    if (inventory.getAvailableSeats() < count) {
        throw new InsufficientSeatsException("Not enough seats");
    }
    inventory.setAvailableSeats(inventory.getAvailableSeats() - count);
    repository.save(inventory);

    // Step 4 — transaction ends here, lock is released automatically
}
```

---

## 🏢 Real World — Where Companies Use This

- **BookMyShow** (movie/event ticket booking): Optimistic locking — millions of seats across thousands of shows. Conflicts on any specific seat are rare. No point locking rows; optimistic CAS on seat status works perfectly.

- **Amazon** (product inventory): Optimistic locking — a product has thousands of units. Two people buying simultaneously is common, but two people trying to buy the *exact last unit* at the *exact same millisecond* is rare. Optimistic with retry handles it cleanly.

- **Flipkart Big Billion Day flash sale** (single item at ₹1): Pessimistic locking — 50,000 users try to buy the same last unit simultaneously. Optimistic locking would cause a thundering herd of retries. Better to queue them with a real lock, serve one, reject the rest cleanly.

- **Razorpay / PhonePe** (payment processing): Pessimistic locking for the ledger debit — you cannot have two threads reading the same wallet balance simultaneously and both thinking they can debit. Bank-grade correctness requires pessimistic.

- **GitHub** (pull request merge conflicts): Optimistic — it tracks file version via commit SHA. When you merge, it checks if the base branch moved. If yes, "conflict detected — rebase and try again." Classic optimistic locking, not on a DB row but on a git tree.

- **InMobi bus ticket problem** (A wants 10, B wants 25, total 30): Optimistic locking on the `seat_inventory` row with a version column. A commits first, B gets 0 rows updated, retries, reads the updated count, gets cleanly rejected.

---

## 🧭 When to Use vs When NOT to Use

| Use Optimistic when | Use Pessimistic when |
|---|---|
| Conflicts are **rare** (most writes will succeed) | Conflicts are **frequent** (many writers on same row) |
| High read throughput, low write contention | Flash sales, limited inventory with high demand |
| Retrying is cheap and quick | Retrying is expensive (e.g., payment side effects) |
| You can tolerate a brief retry loop | You need guaranteed first-in-first-served ordering |
| E-commerce inventory, seat booking, profile updates | Bank transfers, ledger debits, strictly serialized ops |

**The common mistake:** Using pessimistic locking everywhere "just to be safe." It becomes a bottleneck under load — every writer queues up behind one lock. Optimistic scales horizontally; pessimistic does not.

---

## ⚠️ Trade-offs

| | Optimistic | Pessimistic |
|---|---|---|
| **You gain** | No blocking, high throughput, scales horizontally | Guaranteed no conflicts, simple retry-free logic |
| **You lose** | Retry logic complexity, possible retry storms under high load | Throughput under contention, deadlock risk |
| **Failure mode** | Under extreme contention (flash sale), constant version mismatches → retry storms → system appears slow | Two threads locking rows in different order → deadlock → both hang forever |

**Deadlock prevention with pessimistic locks:** Always acquire locks in a **consistent order** (e.g., always lock by ascending row ID). Set a `lock_timeout` so a waiting thread fails fast instead of hanging.

---

## 🔬 Interview Q&As

### Q: "What's the difference between optimistic and pessimistic locking?"
> Optimistic locking assumes conflicts are rare. You read freely, remember the version, and at write time check `WHERE version = N`. If someone else changed it, you get 0 rows updated and retry. No actual lock is held during your work. Pessimistic locking assumes conflicts are likely — you lock the row before reading with `SELECT FOR UPDATE`, so nobody else can modify it until you commit.

### Q: "When would you use optimistic vs pessimistic locking?"
> Optimistic when conflicts are rare and retrying is cheap — most e-commerce inventory, booking systems, profile updates. Pessimistic when conflicts are frequent or retry cost is high — bank transfers, flash sale last-item, anything where you can't afford a retry loop generating partial side effects.

### Q: "What happens when optimistic locking fails in production?"
> You get `OptimisticLockException` from Hibernate, or `0 rows updated` from a raw SQL update. You catch that, re-read the row to get the fresh version and value, then retry. After N retries (typically 3), surface a "resource temporarily unavailable" error to the caller. In Spring, `@Retryable` handles this cleanly without manual loops.

### Q: "How does Hibernate implement optimistic locking?"
> You annotate a field with `@Version`. On every `save()`, Hibernate automatically generates `UPDATE ... SET col=?, version=N+1 WHERE id=? AND version=N`. If the WHERE clause matches 0 rows, Hibernate throws `OptimisticLockException`. You don't write the conflict detection yourself — the framework handles it.

### Q: "What is a deadlock and how do you prevent it with pessimistic locking?"
> A deadlock happens when Thread A holds Lock 1 and waits for Lock 2, while Thread B holds Lock 2 and waits for Lock 1 — both wait forever. With pessimistic locks, prevent it by: always acquiring locks in a consistent order (ascending ID), keeping transactions short, and setting a `lock_timeout` so a waiting thread fails fast rather than hanging indefinitely.

### Q: "The bus booking problem — A wants 10, B wants 25, total 30. What do you use?"
> Optimistic locking with a `version` column on the `seat_inventory` row. Both A and B read `version=7`. A commits first — seats go from 30 to 20, version becomes 8. B's `WHERE version=7` finds 0 rows, B retries. On retry, B reads `seats=20, version=8`. B needs 25 but only 20 remain — cleanly rejected. No locks held, no queuing, correct result.

### Q (Tier 2): "Optimistic locking retries on conflict. But what if the operation has side effects — like sending an email or calling a payment API inside the transaction?"
> This is the subtle trap with optimistic locking: if the critical section includes **non-transactional side effects** (HTTP calls, email sends, Kafka publishes), a retry doubles those effects. You send two emails, charge the card twice. The fix is to move side effects **outside** the retry loop — either write an idempotent version of the side effect (idempotency key on payment API, deduplication on Kafka consumer) or push the side effect to a background job triggered **only after** the DB transaction commits. See `04-idempotency.md` for how idempotency keys pair with retry loops. Rule of thumb: anything inside an optimistic-lock retry must be either pure-DB or idempotent.

### Q (Tier 2): "At what point does optimistic locking become worse than pessimistic, and how would you detect it?"
> When the retry success rate drops below roughly 70% — meaning most attempts fail and retry — you're wasting CPU doing speculative work that gets thrown away. Detect it by tracking `OptimisticLockException` rate in your metrics. If you see a persistent spike on the same entity class during peak traffic (e.g., flash sale on a single inventory row), that's the signal to switch that specific entity to pessimistic locking or to a counter shard strategy. The key word is "specific entity" — most of your inventory rows are fine with optimistic; you switch only the hot one, not the entire system.

### Q (Tier 2): "You said optimistic locking works at REPEATABLE_READ isolation, but what if the database is running at READ_COMMITTED by default?"
> This is a critical gotcha. At READ_COMMITTED, you lose the guarantee that your version number stays stable. Between your initial read of `version=7` and your final write, another transaction could have updated the row to `version=8` — and you'd never see that intermediate change because READ_COMMITTED doesn't give you a consistent snapshot. When your `UPDATE ... WHERE version=7` fails, you'd retry, but this kind of isolation level mismatch can lead to subtle lost-update bugs. Best practice: if you're using optimistic locking, either (a) explicitly set the transaction isolation level to REPEATABLE_READ for the critical section, or (b) use pessimistic locking if your database defaults to READ_COMMITTED and you can't change it. Most modern ORMs (Hibernate) handle this by setting the isolation level automatically when you use `@Version`, but it's good to verify — check your database connection pool settings and transaction defaults to be sure.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"I'd use optimistic locking with a version column — whoever writes first wins, the other gets 0 rows affected and retries with fresh data. No locks held, scales well. Switch to pessimistic only when contention is genuinely high, like a flash sale, where retry storms become worse than queuing."*

---

## 🔗 Related Concepts

- **`06-distributed-locking.md`** — when the lock needs to span multiple services, not just one DB row (Redis SETNX, Redlock)
- **`04-idempotency.md`** — the retry in optimistic locking only works if your write operation is idempotent
- **`03-caching.md`** — cache-aside pattern has a similar "read-modify-write" race condition solved by similar techniques

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Optimistic and Pessimistic Locking"** — Arpit Bhayani (YouTube: "Arpit Bhayani optimistic locking") | Internals of how Postgres and MySQL implement row-level locking — adds depth beyond JPA/Hibernate abstraction | ~25 min |
| **"Handling Concurrency"** — hellointerview.com (https://www.hellointerview.com/learn/system-design/deep-dives/sql) | How locking fits into the broader SQL/NoSQL system design decision — interview-aligned context | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers optimistic locking (@Version, Hibernate), pessimistic locking (SELECT FOR UPDATE, @Lock), deadlock prevention, when to use each. 8 Q&As (4 Tier 1 + 2 Tier 2 + 2 worked examples). |
| June 23, 2026 | Added Section: "Isolation Levels and Their Role" — explains READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE + how each interacts with optimistic vs pessimistic locking. Added new Tier 2 Q&A: "What if the database runs at READ_COMMITTED by default?" — critical gotcha for production systems. Total Q&As now 10 (4 Tier 1 + 3 Tier 2 + 3 worked/advanced). |
