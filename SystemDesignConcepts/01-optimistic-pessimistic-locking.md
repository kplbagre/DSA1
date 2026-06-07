# Optimistic and Pessimistic Locking

> **Standard followed:** `notes-standards.md`
> **Related concepts:** `06-distributed-locking.md`, `04-idempotency.md`

---

## 🎯 Why This Matters

When two threads or two users try to modify the same data at the same time, one of them will corrupt it — unless you have a strategy to control who wins. Optimistic and pessimistic locking are the two fundamental strategies for this at the **database layer**.

This shows up in the **PS round and System Design round** whenever the problem involves booking, inventory, payments, or any shared mutable resource. Interviewers expect a senior engineer to immediately name the strategy and explain when to pick which.

---

## 🧠 The Mental Model

**Optimistic Locking — the "offline document editor" strategy:**

Imagine two colleagues, Priya and Ravi, both download the same report PDF on Friday evening to edit offline. They both work on their changes. Monday morning, both try to save their version back. Whoever saves first wins. The second person gets a message: *"This document was changed while you were working. Please re-read and try again."* Nobody locked anything — they were just optimistic that the other person wouldn't edit the same file.

In databases: you read a row, remember its `version = 7`, do your work, then write with `WHERE version = 7`. If someone else already changed it, the version is now 8, your update hits **0 rows**, and you retry.

**Pessimistic Locking — the "single bathroom key" strategy:**

Some offices have a single physical key for the bathroom. You take the key off the hook, go use the bathroom, come back, hang the key. While you have the key, nobody else can go — they wait outside. It's a physical lock on the resource. You're pessimistic that someone will barge in if you don't lock.

In databases: `SELECT ... FOR UPDATE` grabs a lock on the row. Anyone else trying to read-for-update waits at the door until you `COMMIT`.

**The key insight is:** Optimistic locking is not actually a "lock" — it's a **conflict detection after the fact**. Pessimistic locking is a real lock that **prevents the conflict from happening**.

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

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"I'd use optimistic locking with a version column — whoever writes first wins, the other gets 0 rows affected and retries with fresh data. No locks held, scales well. Switch to pessimistic only when contention is genuinely high, like a flash sale, where retry storms become worse than queuing."*

---

## 🔗 Related Concepts

- **`06-distributed-locking.md`** — when the lock needs to span multiple services, not just one DB row (Redis SETNX, Redlock)
- **`04-idempotency.md`** — the retry in optimistic locking only works if your write operation is idempotent
- **`03-caching.md`** — cache-aside pattern has a similar "read-modify-write" race condition solved by similar techniques
