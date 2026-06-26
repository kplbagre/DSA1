# Core Architecture Layer

> **Goal:** Learn how to actually BUILD scalable, reliable systems. Apply Foundations knowledge to real architectures.

**Time commitment:** ~27-34 hours (16 concepts)  
**Difficulty:** ⭐⭐⭐⭐ (deeper, system-thinking required)

---

## 📚 What's in This Layer

### 1️⃣ Service Communication
**Problem:** How do multiple services talk to each other at scale?

- **17-load-balancing-algorithms.md** — Distribute requests fairly across servers
- **18-service-discovery-dns.md** — Find healthy service instances dynamically
- **19-message-queues-kafka-rabbitmq.md** — Decouple services (async communication)
- **33-grpc-protocol-buffers.md** — Fast RPC over HTTP/2 with binary serialization

**Study order:** 17 → 18 → 19 → 33

---

### 2️⃣ Resilience and Fault Tolerance
**Problem:** What happens when services fail? How do you survive?

- **10-backpressure.md** — Control flow when downstream is overwhelmed
- **20-circuit-breaker-resilience.md** — Fail fast, prevent cascading failures
- **23-saga-pattern.md** — Distributed transactions without ACID
- **29-db-replication-failover.md** — Data survives server failures
- **35-retry-exponential-backoff-patterns.md** — When and how to retry
- **36-two-phase-commit-vs-saga.md** — Distributed transaction strategies
- **39-bulkheads-resource-isolation.md** (NEW) — Thread pool isolation; prevent resource starvation
- **44-graceful-degradation-fallbacks.md** (NEW) — Serve stale/cached data when system is degraded

**Study order:** 20 → 10 → 35 → 39 → 44 → 23 → 36 → 29

---

### 3️⃣ Distributed Systems
**Problem:** How do multiple servers agree on state? How do you operate globally?

- **34-cap-theorem-consistency-models.md** — Consistency vs Availability vs Partition Tolerance trade-offs
- **21-leader-election-consensus.md** — Only one server decides (Raft basics)
- **37-consensus-algorithms-raft-vs-paxos.md** — When each algorithm applies
- **40-multi-region-geo-failover.md** (NEW) — Active-Active vs Active-Passive, latency-aware routing, failover

**Study order:** 34 → 21 → 37 → 40

---

### 4️⃣ Database Core
**Problem:** How do databases scale and survive failures?

- **06-databases-types-and-selection.md** — SQL vs NoSQL: when each wins
- **16-connection-pooling-db-performance.md** — Reuse connections efficiently
- **22-event-sourcing.md** — Store events instead of state (alternative to CRUD)
- **38-sharding-strategy.md** (NEW) — Range/Hash/Directory/Geo sharding; shard key selection, hotspot prevention

**Study order:** 06 → 16 → 38 → 22

---

## 🎯 Learning Path

**Week 1: Service Communication**
- Day 1-2: Load Balancing (17)
- Day 3: Service Discovery (18)
- Day 4: Message Queues (19)
- Day 5: gRPC (33)

**Week 2: Resilience**
- Day 1: Circuit Breaker (20)
- Day 2: Backpressure (10)
- Day 3: Retry Strategies (35)
- Day 4: Saga Pattern (23)
- Day 5: 2PC vs Saga (36)

**Week 3: Distributed Systems & Databases**
- Day 1: CAP Theorem (34)
- Day 2: Consensus & Leader Election (21)
- Day 3: Raft vs Paxos (37)
- Day 4: Database Selection (06)
- Day 5: Connection Pooling (16)

**Week 4: Advanced Databases & Global Systems**
- Day 1: Sharding Strategy (38)
- Day 2: Event Sourcing (22)
- Day 3: Database Replication (29)
- Day 4: Multi-region & Geo-failover (40)

---

## 💡 Key Interview Patterns

After this layer, you should be able to:
- "Design a payment system that survives failures"
- "How do you handle 100K requests/sec with 50 services?"
- "What happens when service B is down? Service A behavior?"
- "Design database replication across 5 regions"
- "Saga vs 2PC: which do you choose and why?"
- "How do you shard a database for 1B users?"
- "Service A is degraded — what does the user see?"

---

## 📋 Prerequisites

Must complete **Foundations** layer first (concurrency, performance, data fundamentals).

---

## 🔗 Next Steps

Once Core-Architecture is solid (18-22 hours), move to **Production-Grade** layer.
