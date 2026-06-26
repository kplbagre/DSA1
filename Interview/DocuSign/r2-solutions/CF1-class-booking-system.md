# CF1 — Design a Fitness Class Booking System (Cult.fit)

> **Note:** Stored in `r2-solutions/` per user request. Format adapted for Cure.fit interview context — Sections 11/14 are Cure.fit-specific, not DocuSign-specific. Do NOT update `INDEX.md` or `system-design-questions.md` for this file.
>
> **Read `solution-notes-standards.md` first** for the delivery framework. This file IS an instantiation of DELIVERY-RECIPE.

---

## 🧠 How to Use This File

**This is a 60-minute conversational answer framework** for the most likely Cure.fit design question. The round is described as "open, thoughtful conversation" — follow the 6-step rhythm, don't recite.

**Time budget:**
- Minutes 0–5: Sections 1–2 (Opener + Clarifying questions)
- Minutes 5–10: Sections 3–4 (Requirements + Scale estimation)
- Minutes 10–25: Sections 5–6 (Variation table + HLD)
- Minutes 25–40: Section 7 (Deep dives — pick 2 of 3)
- Minutes 40–48: Section 10 (Trade-offs with failure modes)
- Minutes 48–55: Section 11 (Cure.fit-specific depth)
- Minutes 55–60: Section 12 (Probes — prepared Tier 1/2/3)

---

## 💾 Memory Anchors (Memorize These 6)

1. **"Ask before you design."** — 4–6 clarifying questions before touching the whiteboard.
2. **"Name the nouns."** — Class, Slot, Booking, User, Waitlist — anchor your design to entities.
3. **"Define the boundary."** — The API contract before the internals.
4. **"Trace a request."** — Walk: user clicks Book → what happens at every layer.
5. **"Draw the boxes."** — ASCII HLD before you explain anything.
6. **"Dig where it's risky."** — Reservation atomicity and TTL expiry. That's where this system breaks.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Design a Fitness Class Booking System / Design a Seat Reservation System (ZoomCar variant) |
| **Interview Type** | Type A — System Design |
| **Confirmed or Likely** | 🔶 Likely — Cult.fit's core product; ZoomCar (same pattern) confirmed asked at Cure.fit historically |
| **Concept notes prerequisite** | `42-inventory-management-booking.md`, `02-rate-limiting.md`, `03-caching.md`, `04-idempotency.md`, `19-message-queues-kafka-rabbitmq.md` |
| **Cure.fit-specific angle** | Cult.fit has peak booking surges at 7–9 PM for next-day 6–9 AM classes. Overselling a yoga class of 15 people is a direct customer trust failure. The interviewer wants to see you prevent double-booking under concurrent load — this is the core of Cult.fit's business. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about the class capacity model, whether we need a waitlist, and what the booking window looks like — because those drive whether I need Redis-level atomicity or if a database lock is sufficient."

Then go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Say this out loud after your opener:**
> "I have a few clarifying questions so I design the right system..."

---

**Q: "How many fitness centers and classes are we talking? And what's the typical class capacity?"**
- Why ask: drives storage design and whether Redis per-class counters are needed.
- ~500 centers, 10 classes/day each = 5,000 classes/day, 15 seats avg → 75,000 seats/day
- If 10× larger → sharding or distributed seat counters required

---

**Q: "When can a user book a class — is there an advance booking window? Can they cancel?"**
- Why ask: determines the soft reservation TTL model and cancellation flow.
- Book 24 hours ahead → slot opens at a specific time (surge risk)
- Cancel 2 hours before → released slot + waitlist promotion

---

**Q: "Do we need a waitlist? And if a slot opens up (cancellation), should we auto-promote?"**
- Why ask: waitlist adds a queue + promotion job — significant complexity.
- No waitlist → simpler, reject when full
- Waitlist + auto-promote → Redis sorted set + background job or keyspace notifications

---

**Q: "What's the peak concurrent booking scenario? Think: all users trying to book a popular 7 AM yoga class the moment it opens."**
- Why ask: this defines the concurrency model — Redis DECR vs SELECT FOR UPDATE.
- Light concurrency (< 100/sec) → DB-level pessimistic lock is fine
- Heavy concurrency (1,000/sec) → Redis DECR atomic operations required

---

**Q: "Do we need payment as part of booking, or is it a free-to-book, pay-at-gym model?"**
- Why ask: payment adds distributed transaction complexity (2PC or Saga).
- Pay at gym → booking is simple, no payment saga
- Pay online → need idempotent payment + booking saga

---

**Assumed answers (state these before Section 3):**
- ~500 centers, 10–15 classes/day, 15–20 seats per class
- Advance booking window: up to 48 hours; cancellation up to 2 hours before
- Waitlist required, auto-promotion on cancellation
- Peak: 1,000+ concurrent bookings when a popular slot opens
- Pay at gym (no payment saga in scope today)

---

## Section 3 — 📋 Requirements

**Functional Requirements:**
- Users should be able to browse available classes by date, center, type
- Users should be able to book a seat in a class (prevent double-booking)
- Users should be able to cancel a booking (up to 2 hours before class)
- Waitlisted users should be auto-promoted when a seat is freed
- Users should receive push notification on booking confirmation and waitlist promotion
- Out of scope: payment processing, instructor scheduling, video streaming classes

**Non-Functional Requirements:**
- Scale: ~2M DAU, ~1M bookings/day, 12 bookings/sec average, 1,000/sec peak burst
- Latency: P99 < 500ms for booking confirmation
- Availability: 99.9% SLO (booking must not go down during peak hours)
- Consistency: **strong for seat count** (no overselling), eventual for notifications
- Durability: no booking loss — every confirmed booking persists

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **User** | Member with an account — profile, push notification token | PostgreSQL |
| **Studio** | A Cure.fit fitness center/location — name, address, timezone | PostgreSQL |
| **Class** | A scheduled workout session — type, instructor, start time, capacity, status | PostgreSQL |
| **Booking** | One confirmed seat reservation — links a User to a Class | PostgreSQL |
| **Waitlist** | Ordered queue of users wanting a seat when one is cancelled | PostgreSQL |

**Key relationships:**
- A `Studio` offers many `Classes` per day (one-to-many)
- A `Class` has many `Bookings` up to `capacity` (one-to-many; the seat count is `COUNT(bookings WHERE status=CONFIRMED)`)
- When a `Booking` is cancelled, the first `Waitlist` entry is auto-promoted to a `Booking`
- The critical constraint: `COUNT(confirmed bookings) ≤ Class.capacity` — enforced with a DB-level pessimistic lock or Redis atomic counter

---

## Section 4 — 🔢 Scale Estimation

**Traffic:**
- DAU: 2M users
- Bookings/day: 2M × 0.5 bookings/user = 1M bookings/day
- Average write rate: 1M / 86,400 = **~12 bookings/sec**
- Peak multiplier (burst when class opens): 80× average = **~1,000 bookings/sec**
- Browse/reads: 10× bookings = **~120 reads/sec average, ~10,000/sec peak**

**Class inventory:**
- 500 centers × 12 classes/day × 20 seats = **120,000 seats/day**
- Active classes at any moment: 500 × 2 concurrent = ~1,000 active class counters in Redis

**Storage:**
- Booking record: ~500 bytes
- 1M bookings/day × 500 bytes = 500 MB/day
- 1 year: ~180 GB → fits in single Postgres cluster with archiving

**Key conclusions:**
- **At 1,000 writes/sec peak, a single Postgres table with row-level locking handles ~5,000 TPS** — technically fine, but with ~20 threads fighting for the same row per class, lock contention becomes the bottleneck.
- **Redis DECR is atomic at microsecond scale** — 1,000 concurrent DECR on the same key works without contention.
- **~1,000 active seat counters in Redis at any time** — trivially small, no sharding needed for Redis.

---

## Section 5 — 🔄 Requirements Variation Table ⭐

| If the interviewer says... | Architecture changes to... | Reasoning |
|---|---|---|
| "10K users, 1 center" | Single Postgres DB, `SELECT FOR UPDATE` on class row, no Redis | Lock contention nonexistent; Redis overhead not worth it |
| "100M users, 50K centers" | Redis Cluster sharded by classId, Cassandra for booking history, Kafka for all async | Redis single node tops out; Cassandra handles 100M bookings/day writes |
| "Strict no-oversell, ever" | Redis DECR + Lua script (atomic check-and-decrement), DB as source of truth after | Redis atomic operations = zero race conditions |
| "Eventual consistency OK" | Local seat cache per app server, async sync via Kafka, occasional temporary oversell allowed | 10× faster, but brief oversell possible during network partition |
| "Add payment (pay online)" | **Saga pattern** (a way to handle a multi-step transaction across services without a shared DB lock — each step publishes an event; if any step fails, a compensating event undoes the previous step): Book → Reserve Seat → Charge Payment → Confirm; if payment fails, publish "undo reserve" | Distributed transaction without 2PC; idempotency key required |
| "Multi-city, multi-timezone" | Read replicas per region, write to primary; use UTC for all timestamps, convert at display | Cross-region latency only affects writes to primary |
| "Waitlist not needed" | Drop Redis ZSET waitlist, drop promotion job — simpler, return 409 when full | Eliminates background job complexity |

---

## Section 6 — 🏗️ High-Level Architecture

### 🎨 Visual — ASCII Architecture Diagram

```
                         ┌─────────────────────────────────────────────────────┐
                         │                    Cult.fit Platform                 │
                         │                                                       │
  Mobile / Web  ────────▶│  API Gateway (auth, rate limit 100 req/min/user)     │
                         │         │                                             │
                         │         ▼                                             │
                         │  Load Balancer                                        │
                         │    │         │                                        │
                         │    ▼         ▼                                        │
                         │ [Booking  [Browse                                     │
                         │  Service]  Service]                                   │
                         │    │    \     │                                        │
                         │    │     \    ▼                                        │
                         │    │      ▼  Redis (class schedule cache)             │
                         │    │     Redis                                        │
                         │    │     ├── class:{id}:seats   → INT counter         │
                         │    │     ├── class:{id}:reserve:{userId} → TTL 5min   │
                         │    │     └── class:{id}:waitlist → ZSET (score=time)  │
                         │    │                                                   │
                         │    ▼                                                   │
                         │  PostgreSQL (primary)                                 │
                         │  ├── bookings table (source of truth)                 │
                         │  ├── classes table (capacity, schedule)               │
                         │  └── waitlist_entries table                           │
                         │    │                                                   │
                         │    ▼                                                   │
                         │  Kafka                                                │
                         │  ├── booking.confirmed → Notification Service         │
                         │  ├── booking.cancelled → Waitlist Promotion Job       │
                         │  └── booking.events    → Analytics                   │
                         │                │                                       │
                         │                ▼                                       │
                         │  Notification Service → FCM/APNs (push)              │
                         │  Waitlist Promotion Job (scheduled, every 30s)       │
                         └─────────────────────────────────────────────────────┘
```

### Data Flow Walkthrough (say this out loud)

**Happy path — user books a seat:**

1. **Request enters API Gateway** → JWT validated, rate limit checked (100 req/min/user prevents burst abuse), routed to Booking Service.

2. **Booking Service executes Redis Lua script (atomic):**
   - Reads `class:{classId}:seats` counter
   - If > 0: DECR counter, SETEX soft reservation key `class:{classId}:reserve:{userId}` with 300s TTL
   - If = 0: return WAITLISTED, ZADD `class:{classId}:waitlist` with score=currentTimestamp
   - The Lua script executes atomically — no race condition possible.

3. **If Redis DECR succeeded → write booking to PostgreSQL:**
   ```sql
   INSERT INTO bookings (id, user_id, class_id, status, reserved_at)
   VALUES (?, ?, ?, 'SOFT_RESERVED', NOW())
   ON CONFLICT (user_id, class_id) DO NOTHING;
   ```
   **How this works (concept: PostgreSQL upsert / duplicate guard):**
   - The `bookings` table has a `UNIQUE` constraint on `(user_id, class_id)` — meaning the DB enforces that the same user cannot have two rows for the same class.
   - Normally, trying to INSERT a duplicate would throw a DB error and crash. `ON CONFLICT ... DO NOTHING` tells Postgres: "if this row already exists, skip silently — don't error, don't update, just do nothing."
   - The application then checks `rowsAffected` (every DB driver returns how many rows were actually inserted). If `rowsAffected == 0`, zero rows were inserted → it was a duplicate → the app returns HTTP `409 Conflict` to the client.
   - **In plain English:** The DB is the second safety net. Even if somehow the same user hits Book twice and both requests slip through Redis, only the first INSERT lands. The second insert silently does nothing. App sees 0 rows written → tells the user "you already booked this class."

4. **Booking Service publishes `booking.confirmed` event to Kafka** → Notification Service sends push notification to user.

5. **Background TTL Expiry Job** (runs every 30s): scans for expired soft reservations. For each expired key: increment Redis counter back, pop first user from ZSET waitlist, create new soft reservation for them, publish `waitlist.promoted` event.

**Each box justification:**
- **API Gateway**: centralized auth and rate limiting — keeps Booking Service stateless
- **Redis seat counter**: atomic DECR prevents double-booking without DB lock contention
- **PostgreSQL**: durable source of truth — Redis is cache, Postgres is authoritative
- **Kafka**: decouples notification + analytics from booking critical path — if notification fails, booking is not rolled back
- **Waitlist Promotion Job**: async is OK — 30s delay in waitlist promotion is acceptable; promotes fairness via ZSET time ordering

---

## Section 7 — 🔬 Core Component Deep Dives

### Deep Dive 1: Reservation Atomicity — Preventing Double-Booking ⭐ MOST CRITICAL

**Why this is the riskiest component:** At 1,000 concurrent bookings/sec all hitting the same class (e.g., popular 7 AM yoga), without atomicity, multiple users decrement the same counter from 1 to negative values. Cult.fit oversells a 15-seat class. Customer trust destroyed.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Redis Lua script (DECR + reserve)** | Atomic, microsecond latency, no lock contention | Redis single point of failure; requires Redis Sentinel/Cluster |
| **PostgreSQL SELECT FOR UPDATE** (a SQL clause that locks the selected row until your transaction commits — like putting a "being edited" sign on a file cabinet drawer; other threads wanting the same row must wait in line) | Simple, ACID, no Redis dependency | Row-level lock = serialized access to class row; 1,000 threads queue up, P99 latency spikes to seconds |
| **Optimistic locking (version field)** (read the row + its version number, do your update, but only commit if the version hasn't changed — if someone else changed it first, you retry; no lock held, but high retry rate under contention) | No DB lock held during processing | High retry rate under contention; poor UX (many 409s) |

**Decision: Redis Lua script** for the hot path.

```lua
-- Atomic check-and-decrement in Redis
-- Returns 1 = success, 0 = no seats, -1 = already reserved
local key = KEYS[1]          -- class:{classId}:seats
local reserveKey = KEYS[2]   -- class:{classId}:reserve:{userId}
local userId = ARGV[1]
local ttl = ARGV[2]          -- 300 seconds

-- Already has a soft reservation?
if redis.call('EXISTS', reserveKey) == 1 then
    return -1
end

local seats = tonumber(redis.call('GET', key))
if seats == nil or seats <= 0 then
    return 0
end

-- Decrement and reserve atomically
redis.call('DECR', key)
redis.call('SETEX', reserveKey, tonumber(ttl), userId)
return 1
```

**Trade-off accepted:** If Redis goes down after DECR but before Postgres INSERT, seat counter is decremented but no booking record exists. Mitigation: Redis persistence (**AOF — Append Only File**, a Redis durability mode where every write command is logged to disk immediately, so on restart Redis replays the log and recovers its exact state) + reconciliation job compares Redis counter vs confirmed bookings count in Postgres every 5 minutes.

Full explanation of reservation state machine: **`SystemDesignConcepts/Production-Grade/System-Design-Patterns/42-inventory-management-booking.md`**

---

### Deep Dive 2: Waitlist — Fairness and Auto-Promotion

**Why this is risky:** If waitlist promotion is not atomic, two users can be promoted for the same cancelled seat.

**Design:**

```
Redis ZSET: class:{classId}:waitlist
  score = Unix timestamp when user joined waitlist (FIFO ordering)
  member = userId
```

**Promotion sequence (atomic with Redis MULTI/EXEC or Lua):**

> **What is Redis MULTI/EXEC?** It's Redis's transaction mechanism — MULTI says "start recording commands", you queue up multiple commands, then EXEC fires them all at once as an atomic batch. No other Redis command can sneak in between them. Think of it like wrapping commands in a DB `BEGIN ... COMMIT` block. In practice we use a Lua script instead (same atomicity guarantee, more flexible).
```lua
-- On cancellation: increment seat counter, promote first waitlisted user
local classKey = KEYS[1]       -- class:{classId}:seats
local waitlistKey = KEYS[2]    -- class:{classId}:waitlist
local newReserveKey = KEYS[3]  -- class:{classId}:reserve:{nextUserId}

-- ZPOPMIN: atomically removes AND returns the member with the LOWEST score
-- from the sorted set. Since score = Unix timestamp when user joined,
-- lowest score = earliest join time = FIFO (first in, first out fairness).
local nextUser = redis.call('ZPOPMIN', waitlistKey, 1)
if #nextUser == 0 then
    -- No waitlist — just increment seat
    redis.call('INCR', classKey)
    return nil
end

-- Give them a soft reservation (don't INCR, just transfer the seat)
local userId = nextUser[1]
redis.call('SETEX', newReserveKey, 300, userId)
return userId
```

The `userId` returned → Booking Service creates soft reservation in Postgres, publishes `waitlist.promoted` Kafka event → Notification Service sends push: "Your spot is confirmed! You have 5 minutes to confirm."

**Promotion TTL:** Waitlisted user gets 5 minutes to confirm. If they don't confirm, soft reservation expires, next user is promoted.

---

### Deep Dive 3: TTL Expiry and Seat Recovery

**Problem:** User gets soft reservation (Redis key with 5-min TTL) but never confirms (app crashed, user walked away). Without recovery, that seat is lost until TTL fires.

**Two approaches:**

| Approach | Mechanism | Pro | Con |
|---|---|---|---|
| **Redis keyspace notifications** (a Redis feature where Redis itself publishes a message to a channel whenever a key event occurs — e.g., when a key expires, Redis fires an `expired` event; your app subscribes to that channel and reacts) | Redis publishes `expired` events; subscriber recovers seat | Real-time recovery | Redis pub/sub is at-most-once (fire-and-forget — if your subscriber is down when the event fires, the event is lost forever); missed events lose seats |
| **Background polling job** (chosen) | Cron every 30s: compare active soft reservations vs confirmed bookings | Reliable, auditable | Up to 30s delay before seat recovered |

**Background job logic:**
```sql
-- Find expired soft reservations: reserved > 5 min ago, still SOFT_RESERVED
-- NOW() = current DB timestamp
-- INTERVAL '5 minutes' = PostgreSQL syntax for a 5-minute duration
-- NOW() - INTERVAL '5 minutes' = a timestamp 5 minutes ago
-- reserved_at < that timestamp means: "this booking was reserved MORE than 5 minutes ago"
SELECT id, class_id FROM bookings
WHERE status = 'SOFT_RESERVED'
AND reserved_at < NOW() - INTERVAL '5 minutes';

-- For each row found: mark it EXPIRED (it was never confirmed by the user)
-- Then publish a Kafka event so the seat counter is incremented back in Redis
-- and the next waitlisted user gets promoted
```

Idempotency: each expiry job run is idempotent — re-processing an already-expired booking is a no-op (status already EXPIRED).

---

## Section 10 — ⚠️ Trade-offs + Failure Modes

### Trade-off 1: Redis Seat Counter (AP) vs PostgreSQL SELECT FOR UPDATE (CP)

- **Chose:** Redis DECR + Lua script (AP-leaning: available even during network partition, atomic at Redis level)
- **Gain:** Microsecond latency for seat check; zero lock contention; 1,000 concurrent bookings handled without queueing
- **Lose:** Redis is not the durable source of truth; potential for brief inconsistency between Redis counter and Postgres booking count
- **Failure mode if wrong:** If Redis restarted without AOF persistence, counter resets to class capacity even if bookings exist. Postgres shows 15 confirmed bookings but Redis counter = 15 again → new bookings accepted → 30 people in a 15-person class.
- **Mitigation:** Redis AOF persistence + reconciliation job every 5 min; **Redis Sentinel** (a companion process that monitors your Redis master — if the master crashes, Sentinel automatically promotes a replica to master and redirects clients to it; this is how you get Redis high availability without manual intervention) for HA

---

### Trade-off 2: Async Waitlist Promotion vs Synchronous

- **Chose:** Async (Kafka event → background job)
- **Gain:** Booking cancellation returns immediately (200 OK); promoter gets notification within 30s; failure in promotion doesn't fail the cancellation
- **Lose:** ~30s delay between cancellation and waitlisted user receiving notification
- **Failure mode if wrong:** If promotion job crashes permanently, waitlisted users are never promoted. Seats sit empty in Redis. At scale (1,000 cancellations/day), this means empty classes and angry waitlisted users.
- **Mitigation:** **Dead letter queue** (a special Kafka topic where messages are parked when a consumer fails to process them after N retries — instead of losing the message or blocking the queue, it gets moved to the DLQ so an engineer can inspect it and replay it later) in Kafka; monitoring alert if waitlist depth grows without promotion events

---

### Trade-off 3: Soft Reservation TTL (5 min) vs Hard Immediate Confirmation

- **Chose:** Soft reservation with TTL
- **Gain:** Prevents "reservation squatting" — users can't hold seats indefinitely; expired seats auto-release
- **Lose:** User has 5 minutes to confirm; if they're in a bad network area, they may lose the seat
- **Failure mode if wrong:** TTL too short (e.g., 30 seconds) → legitimate users lose seats because the confirmation API call took 35 seconds. UX failure. TTL too long (30 min) → squatters hold all seats for a popular class, class appears full, everyone else joins waitlist, class starts with empty seats.
- **Calibration:** 5 minutes is standard (Airbnb, BookMyShow, Amazon). Measure P99 confirmation latency in production; tune TTL to be 3× that.

---

## Section 11 — 🏋️ Cure.fit-Specific Depth

**What makes your answer Cult.fit-flavored vs a generic booking system:**

**1. Peak surge handling (7–9 PM booking rush)**

When a popular class opens for booking, thousands of users hit "Book" simultaneously. Generic answers miss this. Your answer must explicitly say:
> "The Redis DECR approach handles the 7 PM booking surge — I'd also add an API Gateway rate limit per user (100 req/min) to prevent bot-like retry storms, and a CDN cache for the class listing page so browse traffic doesn't hit the backend during the surge."

**2. Cancellation policy enforcement at infrastructure level**

Cult.fit charges cancellation fees for no-shows < 2 hours before class. Your system must:
- Record `class_start_time` in the booking
- On cancellation request: check `class_start_time - NOW() < 2 hours` → if yes, flag as late cancellation → trigger fee
- This is a business rule the backend enforces, not the client

**3. Instructor-to-class assignment (probe bait)**

If asked "what if the instructor cancels?" — your answer: broadcast cancellation to all bookers via Kafka → Notification Service → offer equivalent class or refund credit. The booking system itself doesn't own instructor scheduling — that's a separate service with its own domain.

**4. Class popularity and waitlist as product signal**

The waitlist depth per class is a product metric. A class consistently 5× oversubscribed → add a second time slot. Your Kafka `booking.events` stream feeds an analytics pipeline that surfaces this. Senior engineers connect system design to product outcomes.

---

## Section 12 — 🔬 Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)
**Q: "How do you prevent two users from booking the last seat simultaneously?"**
> "Redis Lua script. The script atomically reads the counter and decrements in a single operation — no other command can interleave. If counter is 0, it returns failure without decrementing. This is the critical section — done in Redis, not Postgres, because Redis is single-threaded for command execution."

---

### Deep Probe (Tier 2 — tests real understanding)
**Q: "What happens if Redis crashes right after the DECR but before you write to Postgres?"**
> "The seat counter is decremented in Redis but no booking record exists in Postgres. From Redis's perspective, the seat is taken; from Postgres's perspective, it's available. The reconciliation job (runs every 5 min) compares Redis counter vs confirmed+soft_reserved count in Postgres. If Redis count < Postgres available seats, we INCR Redis back. With AOF persistence, Redis restarts from the last flushed log — worst case, we lose 1 second of operations, not the entire counter."

---

### Cross-Concept Probe (Tier 3 — separates senior candidates)
**Q: "How does your booking system interact with the rate limiter? If I retry the booking 10 times, what happens?"**
> "Three layers prevent this from causing damage. First, the API Gateway rate limiter (concept 02) rejects after 100 req/min per user — retries at application level get throttled. Second, the Redis Lua script checks for an existing soft reservation key before decrementing — if the user already has a reservation, it returns -1 (already reserved) without touching the counter. Third, the Postgres INSERT has `ON CONFLICT (user_id, class_id) DO NOTHING` — even if somehow the Lua script runs twice, the DB write is idempotent. All three layers compose — the system is safe at every level."

---

**Bonus probe (have this ready):**
**Q: "How would you handle a flash sale — 10,000 users simultaneously booking 50 spots?"**
> "The Redis DECR handles 10,000 concurrent requests without contention — 9,950 will get 0 returned and be waitlisted. The actual bottleneck is write throughput to Postgres (9,950 waitlist INSERT rows). I'd batch waitlist inserts via Kafka consumer: 10,000 events → Kafka consumer batches into 100-row Postgres inserts. Keeps DB write rate manageable. Alternatively, write the full waitlist to Redis ZSET first (microsecond speed), then async-flush to Postgres. Redis is the hot path; Postgres is the audit trail."

---

## Section 15 — 🧾 TL;DR Answer Summary

> "I'd design Cult.fit's class booking system around a Redis-first seat counter with atomic Lua scripts for the critical hot path, backed by Postgres as durable source of truth. The core insight is that Redis DECR is atomic in Redis's single-threaded model — 1,000 concurrent bookings on a 15-seat yoga class all get serialized at the Redis level with no lock contention. Soft reservations with 5-minute TTL prevent seat squatting, and a background job reconciles expired reservations and auto-promotes the waitlist. Kafka decouples notifications and analytics from the booking critical path — a notification failure never fails a booking. The hardest failure mode is Redis crash post-DECR pre-Postgres-write, mitigated by AOF persistence and a 5-minute reconciliation job. For Cure.fit specifically, I'd add API Gateway rate limiting to absorb the 7 PM booking surge, and late-cancellation fee enforcement at the service layer — 2-hour cutoff is a business rule the backend must own."
