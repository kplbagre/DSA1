# JPMorgan SDE-3 — Round 3 (In-Office) Prep Guide

> **Context:** R1 and R2 done remotely — mix of DSA + Java questions. R3 is in-office at JPMC.
> **Format:** 45–60 min, one problem, interviewer drives through LLD → Java depth → HLD in sequence.
> **Bar:** SDE-3 in India (Bangalore/Mumbai). Design quality + Java depth determine level offer.

---

## 🎯 What This Round Actually Is

Round 3 is **not** two separate sessions. It is **one real-world problem** the interviewer drives through two depths:

```
Problem given (e.g., Movie Ticket Booking, Payment System, Delivery App)
       │
       ▼
Phase 1 — LLD (~20 min)
  - What are your entities/classes?
  - What are the relationships? (is-a vs has-a)
  - What design patterns apply here?
  - How do you handle this specific OOP decision?
       │
       ▼
Phase 2 — Java depth probe (embedded throughout, not a separate section)
  - "How would two threads booking the same seat be handled?"
  - "What's your concurrency model for this class?"
  - Pivots to Java internals: synchronized, concurrent collections, virtual threads
       │
       ▼
Phase 3 — HLD (~20 min)
  - Scale this to millions of users — what changes?
  - Draw the components — services, DB, cache, queue
  - APIs — what does the contract look like?
  - DB schema — which tables, which indexes?
  - Where does Kafka / Redis / S3 enter?
```

---

## 🧠 What They Are and Are NOT Asking

| ✅ They WILL ask | ❌ They will NOT ask |
|---|---|
| Draw/describe classes and relationships verbally or on whiteboard | Write a fully working Java implementation |
| Defend OOP choices — "why composition over inheritance here?" | Full class code with getters/setters/constructors |
| Concurrency Q embedded in LLD — "two users book same seat simultaneously" | Full production-ready Spring Boot wiring |
| Scale it — draw components, name services, pick DB, justify the choice | Complete unit test suite |
| HackerRank whiteboard for the HLD diagram | Full error handling code |

---

## 🧭 Confirmed Problems Asked in This Round (SDE-3, 2024–2026)

> `⭐⭐⭐` = 3+ distinct reports | `⭐⭐` = 2 reports | `⭐` = 1 report, high-signal

### 🔴 Tier 1 — Most Frequently Reported (`⭐⭐⭐`)

| Problem | Type | LLD component | HLD component | Notes |
|---|---|---|---|---|
| **Parking Lot** | LLD + HLD | Vehicle, Spot, Ticket, ParkingFloor classes; fee calculation | AI plate-capture service pre-given; design booking, payment, notifications around it | HackerRank drawing tool. Most reported problem across 4+ threads (Bengaluru, Mumbai, 2024–2026) |
| **Payment System** | HLD | Transaction, Account, PaymentGateway, Ledger classes | Requirements → DB schema → estimations → HLD → optimizations → idempotency | Done on HackerRank Code Pair. Covers full arc: requirements to scale. Multiple SuperDay reports |
| **Delivery Partner App** | LLD (pivots to HLD) | Order, Partner, Route, MatchingEngine classes | Matching service, location update stream, notification service | Interviewer gives basic HLD skeleton; candidate improves it and walks end-to-end |

---

### 🟡 Tier 2 — Reported Twice (`⭐⭐`)

| Problem | Type | LLD component | HLD component | Notes |
|---|---|---|---|---|
| **Movie Ticket Booking** | LLD + HLD | Seat, Show, Theatre, Booking classes; state machine (PENDING → CONFIRMED → CANCELLED) | Booking service, Redis seat lock, Kafka for async confirmation, idempotency key | Confirmed Nov 2025 SuperDay + Educative JPMC design blog |
| **Document Upload & Validation Portal** | HLD | Document, ValidationJob, ThirdPartyClient | S3 for file bytes, DB for metadata, async worker fleet (2–3s third-party delay), tracking link returned | Two separate LeetCode threads. Focus: handling async latency gracefully |
| **Google Drive / File Storage (Dropbox variant)** | HLD | File, Folder, Version, SharePermission classes | Load balancer → file service → S3; skeleton given, candidate adds/optimizes for scale | One thread had Google Drive skeleton given upfront. Another reported as "Design Dropbox." Same problem |
| **Shopping Cart** | LLD | Cart, CartItem, Product, Discount, PricingEngine classes | N/A — interviewer focuses on OOP: how Cart owns items, how discounts compose | Blind panelist confirmed. OOP-focused — no distributed systems expected |
| **Elevator System** | LLD | Elevator, Floor, Button, ElevatorController, Request classes; scheduling strategy | N/A — focus on scheduling algorithm (LOOK/SCAN), direction state machine | Blind panelist + prep guides. OOP: Strategy pattern for scheduler |

---

### 🟢 Tier 3 — Reported Once, High-Signal (`⭐`)

| Problem | Type | Key focus | Notes |
|---|---|---|---|
| **No Broker App** (real estate listing) | HLD | Property listing, search by location/filter, booking flow | Jan 2026 JPMC Cohort SuperDay Glassdoor report. Marketplace-style system |
| **URL Shortener** | HLD | Unique ID generation, redirect flow, analytics, rate limiting, caching | Cited in JPMC system design prep source as a canonical example question |
| **API Rate Limiter** | LLD + HLD | RateLimiter, TokenBucket/LeakyBucket classes; Redis for shared counter | One JPMC-specific report. Financial APIs — JPMC uses rate limiting heavily |
| **Stock Calculator / Real-time Trading Dashboard** | HLD | Price feed ingestion, up/down calculation, trader alert | Javarevisited JPMC Java Developer interview article. Financial domain relevance is high |
| **Fraud Detection System** | HLD | Transaction scoring, rules engine, ML flag, manual review queue | Same source as Stock Calculator. JPMC's core domain — likely to grow in frequency |

---

## 🎨 How the Round Arc Usually Goes

### Opening (first 2–3 min)
- Interviewer gives a system prompt: "Let's design a Movie Ticket Booking system."
- They want YOU to drive — ask clarifying questions first.
- **Ask:** What scale? Functional requirements? Any non-functional (latency, consistency)?

### LLD Phase (first ~20 min)
- Start with entity identification: User, Movie, Show, Seat, Booking, Theatre
- Draw class relationships (composition vs inheritance)
- Pick design patterns and justify:
  - Booking → State pattern (PENDING → CONFIRMED → CANCELLED)
  - Seat locking → Strategy pattern (optimistic vs pessimistic)
  - Notification → Observer pattern
- Interviewer will probe: "What if Seat is shared between two classes — how do you model that?"

### Java Pivot (can happen anytime)
- Often triggered when you mention threads, locks, or concurrency
- Common pivot questions:
  - "How do you handle concurrent seat booking in Java?"
  - "Would you use `synchronized` or `ReentrantLock` here and why?"
  - "What about virtual threads (Java 21) for this use case?"
  - "How does `ConcurrentHashMap` help here?"

### HLD Phase (last ~20 min)
- Expand the LLD classes into distributed services
- Name the components: Booking Service, Inventory Service, Notification Service, API Gateway
- DB choice: relational for bookings (strong consistency), Redis for seat lock, Kafka for async events
- API design: idempotency keys on POST /bookings (network retries in financial context)
- Scalability: horizontal scaling, partition by theatre_id in Kafka

### Closing (last 2–3 min)
- Interviewer asks: "What would you improve if you had more time?"
- Say: observability (distributed tracing, correlation IDs), rate limiting at gateway, circuit breakers on external calls

---

## ⚠️ Interviewer Variability — The Honest Truth

**Two SDE-3 candidates with the same JPMC team reported completely different depths:**
- One was done in 25 min (interviewer liked the class design, moved on)
- Another was grilled for 50 min (every decision challenged, Java internals deep dived)

**The pattern:** If your answer is shallow, they push deeper. If they like what they hear early, they move faster.

**Specific risk for you:** You've cleared two Java-heavy DSA rounds. Round 3 interviewers know that. They will skip basics and go straight to the design problem + concurrency/scalability follow-ups. **Be ready for that immediately — no warmup.**

---

## ⚠️ Downleveling Risk

> One candidate was offered SDE-2 instead of SDE-3 because the design round performance didn't meet the SDE-3 bar (and <6 YOE played a role). Design round is the level-setter.

**What separates SDE-3 from SDE-2 in this round:**
- SDE-2: Gives a correct design but needs prompting for scalability, trade-offs
- SDE-3: Proactively identifies bottlenecks, raises trade-offs unprompted, knows when eventual consistency is acceptable

---

## 🔧 Java Topics That Surface During LLD (Prepare These)

These come up mid-design — not as separate questions but embedded in the LLD:

### Concurrency — seat booking example

```java
// "Two users booking the same seat simultaneously — how does your code handle it?"

// Option 1: Optimistic locking (DB level) — preferred for low contention
@Entity
public class Seat {
    @Version
    private int version;
    // JPA throws OptimisticLockException if two txns update same row concurrently
}

// Option 2: Pessimistic locking (application level)
private final ReentrantLock seatLock = new ReentrantLock();

public boolean bookSeat(int seatId, String userId) {
    seatLock.lock();
    try {
        if (!isAvailable(seatId)) {
            return false;
        }
        markBooked(seatId, userId);
        return true;
    } finally {
        seatLock.unlock();
    }
}

// Option 3: Redis distributed lock — for multi-instance (real production answer)
// SET seat:123:lock userId NX PX 30000
// NX = only if not exists, PX = expires in 30s (auto-release if instance dies)
```

### State Machine — Booking lifecycle

```java
// "Walk me through the states of a booking"
public enum BookingStatus {
    PENDING,      // seat held, payment not yet initiated
    PROCESSING,   // payment in progress
    CONFIRMED,    // payment success
    CANCELLED,    // user cancelled or payment failed
    EXPIRED       // held seat released (timeout — no payment in 10 min)
}

// State transitions must be validated — not all transitions are legal
public void transition(BookingStatus newStatus) {
    if (!allowedTransitions.get(currentStatus).contains(newStatus)) {
        throw new IllegalStateException("Invalid transition: " + currentStatus + " → " + newStatus);
    }
    this.currentStatus = newStatus;
}
```

### Design Patterns — "Why Observer here?"

```java
// "When booking is CONFIRMED, you need to: send email, update analytics, release any held alternatives"
// Observer pattern — Booking is the subject, each handler is an observer

public interface BookingObserver {
    void onBookingConfirmed(Booking booking);
}

public class BookingService {
    private final List<BookingObserver> observers = new ArrayList<>();

    public void addObserver(BookingObserver observer) {
        observers.add(observer);
    }

    private void notifyObservers(Booking booking) {
        // In production: this would publish to Kafka, not call directly
        observers.forEach(o -> o.onBookingConfirmed(booking));
    }
}
```

---

## 🌐 HLD Topics That Surface (Prepare These)

### DB + Cache layering

```
Seat availability query path:
  Client → API Gateway → Booking Service
       ├── Redis cache: "is seat 123 in theatre 456 available?"
       │    └── Cache hit: return immediately (low latency)
       └── Cache miss: → MySQL: SELECT status FROM seats WHERE id = 123
            └── Write result back to Redis with TTL 5s
                (short TTL — seat state changes fast)
```

**JPMC follow-up:** *"What's the consistency model here?"*
> Eventually consistent for reads (stale cache possible). Writes go to MySQL primary, cache invalidated. Seat locking uses Redis SET NX (distributed lock) for the critical section — not cache reads.

### Kafka for async

```
Booking CONFIRMED event flow:
  BookingService → Kafka topic: "booking-events"
       ├── EmailService consumer (groupId: email-svc)    → sends confirmation email
       ├── AnalyticsService consumer (groupId: analytics) → updates show metrics
       └── InventoryService consumer (groupId: inventory) → decrements available seats count
```

**Key point:** Services use different groupIds so each gets ALL events independently.

### API idempotency (financial context — JPMC cares about this)

```
POST /v1/bookings
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

// Server stores idempotency key in DB:
// First call:   process booking, store key → 201 Created
// Retry call:   key exists → return same 201, don't double-book
```

---

## 🗺️ What to Practice Before Round 3

### Priority 1 — Design problems (practice these end-to-end, ranked by frequency)

**⭐⭐⭐ Must do — highest frequency:**
1. **Parking Lot** — most reported JPMC problem; practice with the AI plate-capture service as a given component
2. **Payment System** — full arc: requirements → DB schema → estimations → HLD → optimizations
3. **Delivery Partner App** — LLD-first; interviewer gives you a HLD skeleton and asks you to improve it

**⭐⭐ High probability:**

4. **Movie Ticket Booking** — confirmed Nov 2025 SuperDay; state machine + Redis seat lock + Kafka
5. **Document Upload & Validation Portal** — async third-party delay pattern (S3 + async worker fleet + tracking link)
6. **Google Drive / Dropbox** — skeleton given upfront; add observability, versioning, sharing permissions
7. **Shopping Cart** — OOP-only LLD; no distributed systems; focus on Discount composition, CartItem immutability
8. **Elevator System** — OOP LLD; Strategy pattern for scheduler (LOOK/SCAN); direction state machine

**⭐ Know the shape of:**

9. **No Broker App** — marketplace search + booking flow
10. **API Rate Limiter** — Token Bucket in Java + Redis for shared counter across pods
11. **Fraud Detection System** — rules engine + scoring + async manual review queue (JPMC's own domain)

**For each problem, practice this sequence:**
1. Ask 3 clarifying questions (scale, functional requirements, constraints)
2. Name entities/classes in 2 min
3. Draw class relationships (is-a / has-a)
4. Pick 1–2 design patterns and justify
5. Identify 1 concurrency concern and state how you'd handle it in Java
6. Scale to HLD: components, DB choice, cache, async queue
7. Name 1 trade-off you consciously made

### Priority 2 — Java topics to be fluent in (not just know)

- `ReentrantLock` vs `synchronized` — when to use each
- Optimistic vs pessimistic locking — DB-level `@Version` annotation
- `ConcurrentHashMap` internals (CAS for empty buckets, bucket-level sync, no nulls)
- `CompletableFuture.thenCombine` for parallel async calls
- Virtual threads (Java 21) — when they help (I/O-bound), when they don't (CPU-bound)
- Redis SET NX PX — distributed lock pattern

### Priority 3 — HLD vocabulary to use naturally

- **Idempotency key** — on POST /bookings (network retries)
- **Cursor-based pagination** — not offset (O(log N) vs O(N))
- **Consumer group** — partition splitting within same service
- **Circuit breaker** — Resilience4j, states: CLOSED → OPEN → HALF-OPEN
- **Saga pattern** — for distributed transactions across services
- **Read replica** — for read-heavy queries (analytics, seat availability reads)

---

## 📖 Terminology Quick Reference

| Term | Plain English |
|---|---|
| **Bounded context** | A service owns one domain — no shared DB across services |
| **Idempotency** | Same request twice = same result, no side effects from the second call |
| **Optimistic locking** | No lock held; fail at commit time if row was modified by someone else |
| **Pessimistic locking** | Lock held upfront; other threads/processes wait |
| **Saga pattern** | Sequence of local transactions with compensating actions if one fails |
| **Fan-out** | One event → multiple independent consumers each get all messages |
| **Write-through cache** | Write to cache AND DB together; always consistent |
| **Write-behind cache** | Write to cache, async flush to DB; eventually consistent |
| **Shard** | Horizontal partition of DB — each shard holds a subset of data |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created from 2024–2026 JPMC SDE-3 interview reports (LeetCode Discuss, Blind, Glassdoor, 1Point3Acres). Context: R1 and R2 done remotely (DSA + Java). R3 is in-office. |
| Aug 10, 2026 | Problems table expanded after deep research pass (4 additional searches). Added 6 new problems: Shopping Cart, Elevator, No Broker App, URL Shortener, Stock Calculator, Fraud Detection. All problems now ranked by frequency (⭐⭐⭐ / ⭐⭐ / ⭐) with type, LLD/HLD breakdown, and source notes. Priority 1 practice list reordered to match frequency data. |
