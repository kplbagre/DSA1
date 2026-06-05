# InMobi LLD + PS — Walk-In Card (Temp File)

> **Full prep material:** `/Users/k0b077v/Documents/Kpl-inv/Inmobi/INTERVIEW_PREP.md`
> **Time budget:** 45 min LLD → 45 min PS → last 5 min rehearse scripts out loud
> **Mindset (from their PDF):** *"Our interviews are conversations with a potential future colleague. Not a mere Q&A session. We learn from you and your methods too."*

---

## 🧠 iDSP Context — Memorize These (drop across ALL rounds)

**What iDSP does in one line:** *"Given an ad request from an exchange, decide in <100ms whether to bid, for which advertiser, and how much — at millions QPS, no defined peak."*

**The 5 engines to name-drop:**
1. **Core Real-Time Bidder** — computes bid value per advertiser objectives
2. **Targeting Engine** — find, filter, score relevant users per campaign
3. **Creatives/Ad-Experience Engine** — select the right ad format
4. **Supply Quality Engine** — filter low-quality supply
5. **Fraud Control Engine** — filter fraudulent/suspicious requests

> All engines are **ML-driven**. "ML works well at scale" — their words.

**Tech stack to name-drop:** Java/Scala, Spring Boot, **Aerospike** (they literally say "we love Aerospike"), SQL for strong consistency, Apache Flink (streaming), Spark (data), AirFlow (orchestrator), MLFlow, PyTorch/TF, Triton/TF-serving, ClickHouse/Trino (OLAP)

**Scale numbers:** millions QPS (not low-end), TBs/hour, hundreds of data/ML jobs daily, <100-200ms response window from exchanges

---

# ROUND 2 — LLD (~11:30 am)

## 📊 What SDE2 Bar Actually Looks Like (from their rubric PDF)

| Area | What they EXPECT from SDE2 | What you're PREPPED to deliver |
|------|---------------------------|-------------------------------|
| Design Patterns | Strategy for allocation, Factory for tickets, recognizes Observer | All 5 patterns with code ✅ (you're above bar) |
| OOP | Clear entity ownership, interfaces like `IParkingStrategy` | Full entity list + service layer ✅ |
| SOLID | Applies SRP, OCP, LSP. Separates FeeCalculator, TicketService, ParkingManager | SOLID table + drop-in line ✅ |
| Extensibility | Supports new vehicle types, pricing rules, notifications. Handles full lot, expired ticket. Logging considered. | 3 extensibility moves + 4 edge cases ✅ |

> **You're prepped above bar.** SDE4 adds Chain of Responsibility for multi-gate + event-driven Observer for external systems. If you drop even one of those, you stand out.

---

## ⏱️ Your Minute-1 Script (say this OUT LOUD before you go in)

> *"Let me restate: we're building a [X]. I'll first list the entities and behaviors, then sketch the class diagram, then pick the patterns, then code. Before that, three quick questions:*
> 1. *Scale — toy demo or production-shaped?*
> 2. *Which 4–5 features are priority?*
> 3. *Any specific extensibility — new type, new pricing model?"*

Then: **Entities (nouns) → Use-cases (verbs) → Interfaces FIRST → Concrete classes → Patterns → Thread-safety → Edge cases → Extensibility**

---

## ⚡ 5 Patterns — One-Liner + When

| # | Pattern | When to use | Code shape |
|---|---------|-------------|------------|
| 1 | **Strategy** | "How" behavior varies (allocation, pricing, matching) | `interface XStrategy { Y doThing(); }` → inject via constructor |
| 2 | **Factory** | Client shouldn't know concrete subclass | `static Vehicle create(Type t) { return switch(t) { ... }; }` |
| 3 | **Observer** | "When X happens, N things react" (exit → bill + log + notify) | `interface ExitObserver { void onExit(Ticket t); }` → list of observers |
| 4 | **Singleton** | Shared expensive read-mostly state (config, logger) | `enum Config { INSTANCE; }` or double-checked locking + volatile |
| 5 | **Chain of Responsibility** | Sequential independent checks (fraud → quality → eligibility) | `abstract class Handler { Handler next; abstract boolean handle(req); }` |

**Drop-in line:** *"I'm extracting FeeCalculator as a separate class — that's SRP — and putting it behind an interface so I can swap a FlatFee for SurgePricing without changing ParkingManager — that's OCP and DIP."*

---

## ⚡ SOLID — The Table

| Letter | Principle | Smell |
|--------|-----------|-------|
| **S** | Single Responsibility — one reason to change | God class does allocation AND billing AND notifications |
| **O** | Open/Closed — extend, don't modify | Adding vehicle type needs `if/else` edits in 6 places |
| **L** | Liskov — subtype works wherever base works | `ElectricCar.park()` throws — breaks base contract |
| **I** | Interface Segregation — small interfaces | `IVehicle` has `recharge()` that `Bicycle` must implement |
| **D** | Dependency Inversion — depend on abstractions | `ParkingManager` directly `new`s `MySQLTicketRepo()` |

---

## 🅿️ Parking Lot Shape (their rubric literally uses this)

**Entities:** `ParkingLot → Floor → ParkingSpot { id, type, occupied, vehicle }` | `Vehicle { plate, type }` → Car, Bike, Truck, EV | `Ticket { id, vehicle, spot, entryTime, exitTime?, fee? }` | Entry/Exit gates

**Services:** `ParkingManager` (orchestrator) | `SpotAllocationStrategy` (i) → NearestAvailable, FirstAvailable | `FeeCalculationStrategy` (i) → HourlyFlat, VehicleTypeBased, Surge | `TicketService` | `PaymentService` (i) → Cash, UPI, Card | `NotificationObserver`

**Pattern map:**

```
Vehicle creation             → Factory
Spot allocation              → Strategy
Fee calculation              → Strategy
Payment method               → Strategy
On-exit (bill + log + notify)→ Observer
Multi-gate checks            → Chain of Responsibility
Config / logger              → Singleton
```

**Thread-safety:** `ParkingSpot.occupied` flip = `AtomicBoolean` or `synchronized`. `TicketService.issueTicket` = critical section. Use `ConcurrentHashMap` for ticket store.

**Extensibility moves:**
- "EV charging? → `ElectricSpot extends ParkingSpot` + `EVChargingObserver`. No edits to `ParkingManager`."
- "Surge pricing? → New `SurgePricingStrategy`, inject via config. Zero change to existing."
- "Valet mode? → New `ValetAllocationStrategy` + `Driver` entity. Composition, not inheritance."

**Edge cases:** Lot full → `Optional.empty()` + `LotFullException` + observer alert. Lost ticket → policy hook. Vehicle exits without entering → audit log + alert. Concurrent last-spot entry → concurrency design handles it.

---

## 🎯 Most Likely iDSP Prompts (recognise the shape)

| Prompt | Maps to | Key patterns |
|--------|---------|-------------|
| **Rate Limiter** (most likely!) | Token bucket / sliding window | Strategy + Factory |
| **LRU / LFU Cache** | Map + DLL | Thread-safety mention |
| **Notification System** | Channel routing | Strategy + Observer + Factory |
| **Logger with levels** | Pipeline | Chain of Responsibility + Singleton |
| **Frequency Cap** | Sliding-window counter | Strategy + TTL eviction |
| **Bid Auction (2nd-price)** | Auction lifecycle | Strategy + Observer |

### If Rate Limiter comes up:

```java
interface RateLimiter { boolean allow(String key); }

class TokenBucketLimiter implements RateLimiter {
    private final int capacity;
    private final double refillPerSec;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean allow(String key) {
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        synchronized (b) {
            b.refill(refillPerSec);
            return b.tryTake();
        }
    }
}

class SlidingWindowLimiter implements RateLimiter { /* ... */ }

class RateLimiterFactory {
    static RateLimiter create(LimiterType t, Config c) { /* ... */ }
}
```

**iDSP hook:** *"On a single bidder this is in-memory. At iDSP scale you'd shard by key with consistent hashing, sync to Redis every second for soft global view, and accept eventual consistency."*

---

## ❌ What InMobi Penalises in LLD

- All logic in one God class
- Hardcoded `if (type == BIKE) fee = 10` → use Strategy!
- No interfaces (kills testability)
- No mention of thread-safety
- No `main()` / driver example
- Skipping "how would you extend this?"

---
---

# ROUND 3 — Problem Solving (after break, ~1 pm)

> **THIS IS NOT SYSTEM DESIGN.** From their PDF: *"a small business problem (not the system design type) that can be solved using a combination of data structures, data modelling and logic."*

## 📊 What SDE2 Bar Actually Looks Like (from their rubric PDF — MySQL→MongoDB example)

| Area | SDE2 bar (what they expect) | What you're prepped to deliver |
|------|---------------------------|-------------------------------|
| **Handling Ambiguity** | Asks basic Qs: "Is downtime truly zero?" "Can we pause writes?" May miss ongoing writes/rollback. | 5+ clarifying Qs including rollback ✅ (above bar) |
| **System Constraints** | Recognizes large dataset. Suggests basic dump+restore. May not account for concurrent traffic. | 3-phase approach with CDC + idempotency ✅ (SDE4-level) |
| **Trade-offs** | Compares SQL dump vs manual script. Struggles to balance correctness/simplicity. | Full trade-off table: CDC vs dual-write vs active-active ✅ (above bar) |

> **You're prepped at SDE4 level for PS.** The SDE2 bar is "basic dump + restore" — you have CDC, Debezium, idempotency, and outbox pattern. Even hitting 60% of your prep will clear the bar.

---

## ⏱️ Your Minute-1 Script

> *"Let me restate — [problem in one sentence]. Before I solve, I want to ask 5 quick things: scale, latency/SLA, consistency requirement, failure tolerance, and scope boundaries. Then I'll walk through brute force, name the bottleneck, and give an optimised approach with trade-offs."*

---

## ⚡ The 8-Step Framework (THIS IS YOUR SPINE)

```
1. RESTATE the problem in one sentence + tiny example
2. CLARIFYING QUESTIONS — at least 4, ideally 6:
   - Scale: "How many users/records/QPS?"
   - Latency: "SLA? Online or async?"
   - Consistency: "Strong or eventual OK?"
   - Failure: "Rollback story? Data loss tolerable?"
   - Lifecycle: "One-time or recurring?"
   - Boundaries: "What's in scope?"
3. STATE ASSUMPTIONS explicitly — "100M rows, 1KB each ≈ 100GB, ~10K writes/sec"
4. BRUTE FORCE — name it, say why it doesn't work at scale
5. BOTTLENECK — memory? CPU? network? locks? single point of failure?
6. OPTIMISED APPROACH — data structures + algorithm + operational pattern
   (batch vs stream, push vs pull, sync vs async)
7. TRADE-OFF TABLE — at least 2 approaches, pros/cons, YOUR PICK + why
8. OPERATIONAL — monitoring, idempotency, retries, rollback
   (THIS is the senior-level finish)
```

---

## ⚡ 8 Senior Concepts to Drop (sprinkle 3–4)

| Concept | One-line to say |
|---------|-----------------|
| **Idempotency** | "I'll make writes idempotent (upsert on deterministic key) so retries are safe." |
| **CDC** | "For ongoing-writes during migration, CDC — Debezium tailing the binlog into Kafka — catches in-flight writes." |
| **Outbox pattern** | "Dual-write is risky. Outbox — write to DB + outbox in one TX, relay publishes — is safer." |
| **Consistent hashing** | "Shard by user_id with consistent hashing — adding nodes reshuffles only a small fraction." |
| **Backpressure** | "If consumers lag, bounded queues + either drop, sample, or apply backpressure upstream." |
| **Bloom filter** | "For 'seen before?' at scale, Bloom filter. For unique counts, HyperLogLog." |
| **Token bucket** | "For rate-limiting: token bucket = bursty tolerance; sliding window = smoother." |
| **Sharded counters** | "Per-advertiser counters at 1M QPS: shard in-process per bidder, sync to Redis every N sec." |

---

## 🔬 Walkthrough A: "Migrate 100M records from MySQL → MongoDB, zero downtime"

**Clarify:** Is MongoDB new source of truth? Read/write load during migration? Truly zero downtime or brief read-only OK? All users or only active? Rollback story?

**Brute force:** `mysqldump` → restore. Fails: loses writes during dump.

**3-phase approach:**
1. **Bulk snapshot** — consistent snapshot at timestamp T → bulk-load into MongoDB
2. **CDC catch-up** — Debezium reads MySQL binlog from before T → Kafka → consumer applies upserts (idempotent!) to MongoDB
3. **Cutover** — once CDC lag < 1s: dual-write → verify counts + checksums → flip reads → keep MySQL as fallback for 1 week

**Trade-offs:** CDC+outbox (safer) vs dual-write from day 1 (simpler but consistency risk). Active-active (most robust but overkill for one-time migration).

**Operational:** `migration_version` per record. Monitor lag, error rate, per-table counts. Rollback = flip reads back; CDC keeps both in sync.

---

## 🔬 Walkthrough B: "At 1M QPS, enforce daily spend cap per advertiser" (iDSP-flavored!)

**Clarify:** Hard or soft cap? How many advertisers (10K vs 10M)? Overshoot 1% OK? Granularity: per-bid, per-win, per-impression?

**Key constraint:** Hot path (<100ms) can't do Redis round-trip per request at 1M QPS.

**Solution:**
- Each bidder keeps **in-process counter** per advertiser (`ConcurrentHashMap<advertiserId, AtomicLong>`)
- **Sync to Redis** every N seconds (write-behind)
- Bidders pull "global spend" from Redis on same interval → re-compute **local budget allowance** = `remaining_budget × bidder_share`
- If local allowance = 0 → stop bidding for that advertiser

**Trade-off:** ~1% overshoot in worst case (lag window × spend rate). Fine for soft cap. Hard cap → synchronous check on win-notify + refund logic.

**Operational:** Alert if single bidder deviates >X% from expected share. Chaos-test Redis down (fall back to last-known budget).

---

## 🗣️ Lines That Signal Seniority (drop these)

✅ *"The bottleneck here is — let me name it before I optimise."*
✅ *"I'd make this idempotent so retries are safe."*
✅ *"This is eventual consistency by design — here's why that's acceptable."*
✅ *"I'd add a metric here — X_total and X_latency_p99 — so we can alert."*
✅ *"Trade-off: I'm choosing X over Y because the cost of being wrong on this axis is higher."*
✅ *"The hot path shouldn't touch a database synchronously."*

## ❌ Do NOT Say

- *"This is easy/trivial"* — silent points off
- *"Whatever you prefer"* — always PICK and JUSTIFY
- *"We did X at Walmart so..."* — lead with THIS problem's context, derive the solution
- *"I don't know that."* → Instead: *"I haven't used X directly — my mental model is [Y] — let me reason from there."*
- Walmart jargon without translation (MCSE, Wakanda, Hollow, KITT)

---

## 🎯 Three iDSP Hooks (drop one per round)

**Latency hook (LLD):** *"At iDSP's scale — millions QPS, <100ms from exchange — the hot path can't touch a database synchronously. Everything pushes to in-memory state (Aerospike, local LRU), async sync to central, eventual consistency."*

**ML hook (PS):** *"Since iDSP is moving all decisioning to ML — bidding, targeting, CTR/CVR prediction, fraud, supply quality — the system needs low-latency online inference (Triton/TF-serving) plus a feature store. I'd design any new component to expose features cleanly."*

**RTB hook (either round):** *"The bidder receives an ad request from the exchange, has <100ms to decide: should I bid? For which advertiser's campaign? At what price? That decision passes through targeting → supply quality → fraud → bid valuation → creative selection — all in that window."*

---

## ❌ What InMobi Penalises in PS

- Jumping to solution without ANY clarifying questions
- Treating it as system design (load balancers, CDN boxes)
- Only one approach, no trade-off discussion
- Ignoring failure modes / rollback / observability
- Vague hand-waving ("we'll just shard it") without naming DS + partition key

## 💬 Questions to Ask the Interviewer (from their PDF: "Ask questions. Gain brownie points.")

- "I read that iDSP is moving all critical decisioning to ML — how do you handle feature store latency in the bidding path?"
- "With Aerospike as the primary KV store, how do you handle schema evolution for targeting data?"
- "How does the A/B framework work for ML model rollouts at your QPS — shadow traffic or canary percentage?"
- "What's the biggest engineering challenge the team is solving right now?"

---

# ⏰ Last 5 Minutes Before Each Round

**Before LLD:** Say the minute-1 script out loud. Think: "entities → interfaces → patterns → thread-safety → extensibility."

**Before PS:** Say the minute-1 script out loud. Think: "restate → clarify → assume → brute → bottleneck → optimize → trade-off → operational."

**Before BOTH:** Say one iDSP context line out loud: *"Millions QPS, <100ms, can't touch DB on hot path, in-memory state, async sync, eventual consistency."*

You've got this. Go solve.
