# Foundations Layer

> **Goal:** Build unshakeable mental models of core system design concepts. This layer is the foundation for everything that follows.

**Time commitment:** ~17-22 hours  
**Difficulty:** ⭐⭐⭐ (foundational but requires deep understanding)

---

## 📚 What's in This Layer

### 1️⃣ Concurrency and Consistency
**Problem:** How do you keep data correct when multiple threads/servers access it simultaneously?

- **01-optimistic-pessimistic-locking.md** — Database-level conflict detection vs prevention
- **04-idempotency.md** — Safe to retry without duplicates
- **06-distributed-locking.md** — Synchronization across servers (mutexes at scale)
- **41-isolation-levels-dirty-reads.md** (NEW) — READ_UNCOMMITTED → SERIALIZABLE, dirty/phantom reads, MVCC

**Study order:** 01 → 04 → 06 → 41

---

### 2️⃣ Performance and Scale
**Problem:** How do you handle 1000x more traffic with the same resources?

- **02-rate-limiting.md** — Control request flow (protect backend from overload)
- **03-caching.md** — Store hot data in fast memory (eliminate database hits)
- **05-consistent-hashing.md** — Distribute data across servers fairly (when one fails, only 1/N keys move)
- **09-sharded-counters.md** — Distribute counter increments (avoid thundering herd)

**Study order:** 02 → 03 → 05 → 09

---

### 3️⃣ Data Fundamentals
**Problem:** How do you design APIs, schemas, and data structures that scale?

- **07-cdc-outbox.md** — Capture data changes reliably (dual-write problem)
- **08-bloom-filter.md** — Fast approximate membership testing (does this exist?)
- **11-api-design.md** — Contract between client and server (versioning, idempotency, error codes)
- **12-data-modeling.md** — Relational schema design (normalization, indexes, foreign keys)
- **14-document-blob-storage.md** — Where to store unstructured data
- **15-system-qualities.md** — How to measure a system (availability, latency, throughput)
- **43-pagination-cursor-based.md** (NEW) — Offset vs cursor vs keyset pagination; feed stability under inserts/deletes

**Study order:** 11 → 12 → 07 → 14 → 08 → 15 → 43

---

## 🎯 Learning Path

**Week 1: Concurrency**
- Day 1-2: Optimistic vs Pessimistic Locking (01)
- Day 3: Idempotency (04)
- Day 4: Distributed Locking (06)
- Day 5: Isolation Levels (41)

**Week 2: Performance**
- Day 1: Rate Limiting (02)
- Day 2-3: Caching (03)
- Day 4: Consistent Hashing (05)
- Day 5: Sharded Counters (09)

**Week 3: Data**
- Day 1: API Design (11)
- Day 2: Data Modeling (12)
- Day 3: CDC & Outbox (07)
- Day 4: Document/Blob Storage (14)
- Day 5: Bloom Filters (08) + System Qualities (15) + Pagination (43)

---

## 💡 Key Interview Patterns

After this layer, you should be able to answer:
- "How do you handle concurrent writes to the same row?"
- "Design an API that's safe to retry"
- "How do you scale from 1M to 1B records?"
- "What's the trade-off between consistency and availability?"
- "Design a rate limiter for an API"

---

## 📋 Prerequisites

None. This is the foundation. Start here.

---

## 🔗 Next Steps

Once Foundations is solid (15-20 hours), move to **Core-Architecture** layer.
