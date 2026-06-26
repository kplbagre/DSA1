# Concurrency and Consistency

> **Core Question:** How do you prevent race conditions and keep data correct when multiple threads/services access it?

**Concepts:** 4  
**Time:** ~6 hours  
**Difficulty:** ⭐⭐⭐

---

## 📖 Concepts

1. **01-optimistic-pessimistic-locking.md** (1h)
   - Two competing strategies at database layer
   - When each wins
   - Interview: "Why pessimistic locking at bank, optimistic at cache?"

2. **04-idempotency.md** + 04-idempotency_advanced.md (1.5h)
   - Safe to retry without duplicates
   - Idempotency keys + idempotency tables
   - Interview: "Design a payment API that's safe to retry"

3. **06-distributed-locking.md** (1.5h)
   - Locks across multiple servers
   - TTL + fencing tokens prevent stale locks
   - Interview: "How do you handle booking the last seat across 10 servers?"

4. **41-isolation-levels-dirty-reads.md** (2h) (NEW)
   - READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
   - Dirty reads, phantom reads, non-repeatable reads — which isolation level prevents which
   - Interview: "Your payment system double-charged. Was it a dirty read or lost update?"

---

## 🎯 Study Order

**Sequential:** 01 → 04 → 06 → 41

**Why?**
- Locking is base concept (01)
- Idempotency builds on "what if retry happens?" (04)
- Distributed locking applies locking to real systems (06)
- Isolation levels explain what the database guarantees at the row level (41)

---

## 💡 Key Mental Models

| Concept | Analogy |
|---------|---------|
| Optimistic Locking | Two coworkers edit doc offline; collision detected on save |
| Pessimistic Locking | Single bathroom key; only one person can use at a time |
| Idempotency | Pressing elevator button multiple times = one trip |
| Distributed Lock | Restaurant reservation system across multiple cities |
| Isolation Levels | Restaurant kitchen rule: chef can't read your ticket until the previous order finalizes |

---

## 🔬 Common Interview Questions

- "Design a payment system where retries are safe"
- "How do you book the last seat across distributed servers?"
- "What's the difference between optimistic and pessimistic locking?"
- "Why did our payment system double-charge a user?"
- "What happens if a distributed lock holder crashes?"
- "When would you use SERIALIZABLE vs READ_COMMITTED isolation?"

---

## 📚 Real Companies

- **Stripe**: Optimistic locking on payment ledger; SERIALIZABLE isolation for idempotency checks
- **Razorpay**: Idempotency keys for payment processing; distributed locks for seat booking
- **Uber**: Distributed locks for ride request acceptance; READ_COMMITTED for most data reads
- **CockroachDB**: SERIALIZABLE-only isolation (no weaker levels); prevents all anomalies by design
- **PostgreSQL**: MVCC for READ_COMMITTED (default); gap locks prevent phantom reads at REPEATABLE_READ

---

## ✅ Checkpoint

After this folder, you should be able to:
- ✅ Explain locking strategies and trade-offs
- ✅ Design idempotent APIs
- ✅ Build distributed locks
- ✅ Identify which isolation level prevents dirty reads / phantom reads
- ✅ Diagnose why a payment system might double-charge
