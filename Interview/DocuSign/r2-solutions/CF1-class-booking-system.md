# CF1 — Design a Fitness Class Booking System (Cult.fit)

> **Note:** Stored in `r2-solutions/` per user request. Format adapted for Cure.fit interview context — Sections 11/14 are Cure.fit-specific, not DocuSign-specific. Do NOT update `INDEX.md` or `system-design-questions.md` for this file.
>
> **Read `solution-notes-standards.md` first** for the delivery framework. This file IS an instantiation of DELIVERY-RECIPE.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **Distributed locking** | `Foundations/Concurrency-and-Consistency/06-distributed-locking.md` | Preventing double-booking when two users hit Reserve simultaneously for the last spot — Redis `SET NX PX` distributed lock is the core answer |
| **Optimistic / pessimistic locking** | `Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md` | Know the trade-off: pessimistic locking (SELECT FOR UPDATE) vs optimistic (version column + retry) — and when each is appropriate for high-contention booking |
| **Dealing with contention** | `Patterns/DeepDive/04-dealing-with-contention.md` | Hot slots (7am Saturday yoga) create write contention — know sharded counters, queue-based serialization, and why blind writes fail |
| **Inventory management / booking** | `Production-Grade/System-Design-Patterns/42-inventory-management-booking.md` | The canonical booking pattern: reserve → confirm → release; how to implement it safely with timeouts and cleanup jobs |
| **Isolation levels** (phantom reads, serializable) | `Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md` | Without SERIALIZABLE isolation, two transactions can both read "1 spot available" and both commit — know which isolation level prevents this and at what cost |
| **Idempotency** | `Foundations/Concurrency-and-Consistency/04-idempotency.md` | User taps Reserve twice (double tap, network retry) — idempotency key on the booking operation ensures only one reservation is created |
| **State machines / workflows** | `Production-Grade/System-Design-Patterns/49-state-machines-workflows.md` | Booking lifecycle: Reserved → Confirmed → Cancelled → WaitlistPromoted — valid transitions must be enforced |

---

## 🎯 What Is This System?

**In plain English:** A fitness class booking system lets users browse available class slots (yoga, cycling, HIIT), reserve a spot, and join a waitlist when the class is full. When someone cancels, the next waitlist user is automatically promoted and given a time window to claim the released spot.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Cult.fit (Cure.fit)** | The company you're interviewing at — class booking across 200+ fitness centres in India |
| **Mindbody** | Powers most independent yoga/Pilates/CrossFit studios globally |
| **ClassPass** | Multi-studio subscription — book any class at partner studios |
| **SoulCycle** | Premium spin classes; spots sell out in seconds on weekly release |
| **Equinox app** | Group class reservations with waitlist and 12-hour cancellation window |
| **Barry's Bootcamp** | Treadmill spot selection on a visual class floor map |

**Core user journey:** User sees tomorrow's 7am yoga class has 2 spots left → taps Reserve → receives booking confirmation → if they cancel 2 hours before class, the first waitlist user gets notified and has 15 minutes to claim the released spot.

**Why it's hard to build at scale:** Two users tapping "Reserve" on the last available spot at the same millisecond is a classic write-under-contention race — without a distributed lock or database-level optimistic concurrency control, both succeed and the instructor arrives to find 16 people for 15 spots.

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

| Entity | What it represents |
|---|---|
| **User** | Member with an account — profile, push notification token |
| **Studio** | A Cure.fit fitness center/location — name, address, timezone; reference data, rarely updated |
| **Class** | A scheduled workout session — type, instructor, start time, capacity, status; `capacity` is the critical contended field — the Redis atomic counter is a derived hot-path copy of this value |
| **Booking** | One confirmed seat reservation — links a User to a Class; source of truth for confirmed seats; the Redis seat counter is derived from this |
| **Waitlist** | Ordered queue of users wanting a seat when one is cancelled; durable backup — the Redis ZSET is the hot-path ordered queue used for atomic promotion |

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

## Section 8 — 🌐 API Design (Before HLD)

> **Why here:** Define the external contract before drawing the architecture — the HLD shows how these endpoints are implemented. For Type A, this is concise (3–5 minutes); the architecture is the primary deliverable.

### 🧠 How to Derive These Endpoints

Four FRs, four operations:

"Users browse available classes" → READ operation → resource is a class with filter params → `GET /v1/classes`. The query params fall out from the clarifying questions: `date`, `center_id`, `class_type`. Response is a list with available seat counts (read from Redis cache, not live DB count).

"Users book a seat" → CREATE operation → resource is a booking → `POST /v1/bookings`. Who calls it? Authenticated user (JWT Bearer). What's the minimum they send? `class_id`. What do they get back? `booking_id` + `status` (CONFIRMED or SOFT_RESERVED). The concurrency constraint — "only one user gets the last seat" — doesn't change the endpoint; it changes the implementation (Redis DECR + Lua script under the hood).

"Users cancel a booking" → DELETE operation → resource is a specific booking → `DELETE /v1/bookings/{id}`. Response must convey the cancellation outcome clearly: was it a full cancellation or just a no-show flag? Return the updated booking record so the client can update its UI.

"Users join the waitlist" → two sub-operations (join and leave) → `POST /v1/waitlist` and `DELETE /v1/waitlist/{id}`. Waitlist is a separate resource, not a field on a booking, because waitlist entries have their own lifecycle — they can expire, be promoted, or be abandoned.

Validation check: map each back to a FR. No orphan endpoints. "Waitlist auto-promotion" has no endpoint because it's a server-side event (Kafka consumer), not a client call — correctly has no REST endpoint.

### Core Endpoints

| Method | Path | Request Body / Query Params | Response Body | Status Codes |
|---|---|---|---|---|
| GET | `/v1/classes` | `?date=&center_id=&class_type=` | `[{class_id, name, start_time, instructor, available_seats, status}]` | 200, 400 |
| POST | `/v1/bookings` | `{ "class_id": "..." }` | `{ booking_id, class_id, user_id, status, reserved_until }` | 201, 409, 422, 503 |
| DELETE | `/v1/bookings/{id}` | — | `{ booking_id, status: "CANCELLED", cancelled_at }` | 200, 403, 404 |
| POST | `/v1/waitlist` | `{ "class_id": "..." }` | `{ waitlist_id, class_id, user_id, position, added_at }` | 201, 409 |
| DELETE | `/v1/waitlist/{id}` | — | `{ waitlist_id, status: "REMOVED" }` | 200, 403, 404 |

### 🔍 Endpoint Stories

**`GET /v1/classes`** is the browse endpoint — the interviewer won't probe it hard. What's non-obvious: `available_seats` in the response comes from Redis (the atomic counter), not from a live `COUNT(bookings)` query. If you serve it from DB, every browse request is a full aggregation scan across millions of bookings at peak. Redis serves it in O(1). The stale window is acceptable: if the UI shows "3 seats" and the user gets to the booking step to find "0 seats," the booking fails gracefully with 409 — the browse was eventually consistent, the booking is strongly consistent.

**`POST /v1/bookings`** is the entire interview. Two users call this simultaneously for the last seat — only one must win. Status codes tell the story: `201 Created` means you got it. `409 Conflict` means someone else got there first — seat is gone, here's the option to join the waitlist. `422 Unprocessable Entity` means the class has already started, you're in the wrong time window. `503 Service Unavailable` means the Redis lock could not be acquired after retries — rare, but the client should retry after 2 seconds. The `reserved_until` field in the response covers the soft-reservation window: you have 5 minutes to complete check-in, or the seat is released.

**`DELETE /v1/bookings/{id}`** triggers the waitlist promotion chain. The client doesn't need to know about this — they just call DELETE and get a `CANCELLED` confirmation. Internally, the cancellation publishes a Kafka event that the Waitlist Service consumes to promote the next user. Why return the full cancelled booking in the body instead of `204 No Content`? So the client can show "Your booking for 7 AM Yoga has been cancelled" with the class name — without a second GET call.

**`POST /v1/waitlist`** and **`DELETE /v1/waitlist/{id}`** are symmetric join/leave operations. The `position` field in the POST response is the key probe: how do you know your position? The Waitlist table has an `added_at` timestamp — your position is `COUNT(*) WHERE class_id = ? AND added_at < your_added_at + 1`. This is computed at read time, not stored, so it stays accurate as users ahead of you leave the waitlist.

---

## Section 6 — 🏗️ High-Level Architecture

### Stage 1 — DB-Only (Baseline)

> Start here. Works fine for < 100 concurrent bookings. The single breaking point is lock contention.

```
── Stage 1: DB-Only ──────────────────────────────────────────────────

 ┌─────────────┐
 │ Mobile / Web │
 └──────┬──────┘
        │  HTTPS
 ┌──────▼──────────────────────────────┐
 │            API Gateway              │
 │  JWT auth + rate limit (100/min)    │
 └──────┬──────────────────────────────┘
        │
 ┌──────▼──────────────────────────────┐
 │          Booking Service            │
 │  BEGIN TRANSACTION                  │
 │  SELECT seats_taken FROM classes    │
 │    WHERE id = ? FOR UPDATE          │  ← locks class row
 │  IF seats_taken < capacity:         │
 │    INSERT INTO bookings (...)       │
 │    UPDATE classes                   │
 │      SET seats_taken = seats_taken+1│
 │    COMMIT                           │
 │  ELSE: ROLLBACK → return 409        │
 └──────┬──────────────────────────────┘
        │
 ┌──────▼──────────────────────────────┐
 │           PostgreSQL                │
 │  classes   (capacity, schedule)     │
 │  bookings  (source of truth)        │
 └─────────────────────────────────────┘

BREAKING POINT:
   At 1,000 concurrent bookings/sec all targeting the same class row,
   SELECT FOR UPDATE serializes every thread — 1,000 threads queue behind
   one DB lock. P99 latency spikes from <50ms to seconds. DB connection
   pool exhausted. Booking Service returns 503.
```

**WHICH lock strategy?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Postgres `SELECT FOR UPDATE` | Simple, ACID, no extra infra — correct under low concurrency | Serializes threads; 1,000 concurrent → lock queue → P99 spikes to seconds | ✅ Fine for < 100 concurrent; breaks at 1,000/sec |
| Optimistic locking (version field) | No lock held during processing | High retry rate at 1,000/sec → retry storm → amplifies load | ❌ Retry storm at scale |
| Redis atomic `DECR` | Sub-millisecond, no lock held, no contention | Requires Redis infra; Redis not durable without AOF | ⏳ Introduced in Stage 2 |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/42-inventory-management-booking.md`

---

### Stage 2 — Redis Hot Path + Waitlist

> **Why we evolve:** Stage 1 breaks at 1,000/sec. The fix: move the seat-counter check out of Postgres into Redis, where a single atomic `DECR` handles all concurrency without contention. Waitlist also introduced here.

```
── Stage 2: Redis Hot Path ───────────────────────────────────────────

 ┌─────────────┐
 │ Mobile / Web │
 └──────┬──────┘
        │  HTTPS
 ┌──────▼──────────────────────────────┐
 │            API Gateway              │
 │  JWT auth + rate limit (100/min)    │
 └──────┬──────────────────────────────┘
        │
 ┌──────▼────────────────────────────────────────┐   ┌────────────────────────────────────────┐
 │            Booking Service                    │──▶│               Redis                    │
 │  1. Run Lua script (atomic):                  │◀──│  class:{id}:seats   → INT counter      │
 │     - READ counter                            │   │  class:{id}:reserve:{uid} → TTL 300s   │
 │     - IF > 0: DECR + SETEX reservation        │   │  class:{id}:waitlist → ZSET (timestamp)│
 │     - IF = 0: ZADD waitlist                   │   └────────────────────────────────────────┘
 │  2. If DECR success → INSERT booking to DB    │
 │     ON CONFLICT (user_id, class_id) DO NOTHING│
 └──────┬────────────────────────────────────────┘
        │ writes (only after successful DECR)
 ┌──────▼──────────────────────────────┐
 │           PostgreSQL                │
 │  classes   (capacity, schedule)     │
 │  bookings  (source of truth)        │
 │  waitlist_entries (backup)          │
 └─────────────────────────────────────┘

BREAKING POINT:
   A notification failure (FCM/APNs down) inside the Booking Service
   blocks the booking response. The booking itself succeeded — but the user
   never gets a push notification, and the service may wait on the HTTP call.
   Notifications must be decoupled from the booking critical path.
```

**WHICH waitlist storage?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Postgres table `ORDER BY created_at` | Durable, ACID | Can't atomically pop first entry without a transaction + lock | ⚠️ Not atomic under concurrency |
| Redis List (`LPUSH` / `RPOP`) | O(1) push and pop | No score metadata — can't enforce FIFO ordering or inspect queue position | ⚠️ No ordering guarantee |
| Redis ZSET (score = join timestamp) | `ZPOPMIN` atomically pops earliest entry — FIFO + atomic | Needs Postgres backup for durability across Redis restarts | ✅ Best — atomic FIFO promotion |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/42-inventory-management-booking.md`

---

### Stage 3 — Kafka Async Decoupling (Production)

> **Why we evolve:** Stage 2's Booking Service synchronously calls Notification Service. If FCM is down or slow, booking latency spikes. A notification failure should never fail a booking. Solution: publish to Kafka and return immediately. Notifications and analytics consume asynchronously.

```
── Stage 3: Production ───────────────────────────────────────────────

 ┌─────────────┐
 │ Mobile / Web │
 └──────┬──────┘
        │  HTTPS
 ┌──────▼──────────────────────────────────────────────┐
 │                  API Gateway                        │
 │  JWT auth + rate limit (100 req/min/user)           │
 └──────┬──────────────────────────────────────────────┘
        │
 ┌──────▼──────────────────────────────────────────────┐
 │                  Load Balancer                      │
 └──────────────┬──────────────────────────┬───────────┘
                │                          │
 ┌──────────────▼──────────┐  ┌────────────▼────────────────┐
 │     Booking Service     │  │       Browse Service        │
 │     (write path)        │  │       (read path)           │
 └──┬──────────────────────┘  └────────────┬────────────────┘
    │                                       │ cache-aside
    │      ┌────────────────────────────────▼────────────────┐
    │◀────▶│                  Redis                          │
    │      │  class:{id}:seats        → INT counter          │
    │      │  class:{id}:reserve:{uid}→ TTL 300s             │
    │      │  class:{id}:waitlist     → ZSET (join timestamp) │
    │      │  class:{id}:browse_cache → class detail          │
    │      └─────────────────────────────────────────────────┘
    │ writes (post-DECR)
 ┌──▼──────────────────────────────────────────────────┐
 │                  PostgreSQL (primary)               │
 │  classes   (capacity, schedule)                     │
 │  bookings  (source of truth)                        │
 │  waitlist_entries (durable backup)                  │
 └──┬──────────────────────────────────────────────────┘
    │ publishes events
 ┌──▼──────────────────────────────────────────────────┐
 │                     Kafka                           │
 │  booking.confirmed  → Notification Service (FCM)    │
 │  booking.cancelled  → Waitlist Promotion Job        │
 │  booking.events     → Analytics pipeline            │
 └─────────────────────────────────────────────────────┘

KEY INVARIANT:
   Redis is the hot path — 1,000/sec burst absorbed atomically via Lua DECR.
   PostgreSQL is the source of truth — Redis is cache, Postgres is authoritative.
   Booking Service is STATELESS — any instance handles any request.
   Kafka decouples every async concern from the booking critical path —
   a notification failure, analytics lag, or waitlist promotion delay
   NEVER fails or slows a booking confirmation.
```

**WHICH async transport for notifications and waitlist promotion?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Direct HTTP (synchronous) | Simple, no extra infra | FCM/APNs down → booking response blocked or fails; couples availability to notification delivery | ❌ Couples critical path to external service |
| Redis pub/sub | Low latency | At-most-once delivery — if subscriber is down when event fires, event is permanently lost | ❌ Unreliable for booking confirmations |
| Kafka | At-least-once, durable, replayable; dead-letter queue for failed deliveries | Extra infra; adds ~100ms async latency vs synchronous | ✅ Best — booking always succeeds; notification retried independently |

> 📖 Full: `SystemDesignConcepts/Production-Grade/Infrastructure/19-message-queues-kafka-rabbitmq.md`

---

### Data Flow Walkthrough (say this out loud)

**Happy path — user books a seat (Stage 3):**

1. **Request enters API Gateway** → JWT validated, rate limit checked (100 req/min/user prevents retry storms), routed to Booking Service.

2. **Booking Service runs Redis Lua script (atomic):**
   - Reads `class:{classId}:seats` counter
   - If > 0: `DECR` counter + `SETEX` soft reservation key `class:{classId}:reserve:{userId}` with 300s TTL
   - If = 0: `ZADD class:{classId}:waitlist` with score=currentTimestamp → return WAITLISTED
   - The Lua script executes atomically — no other Redis command can interleave.

3. **If DECR succeeded → write booking to PostgreSQL:**
   ```sql
   INSERT INTO bookings (id, user_id, class_id, status, reserved_at)
   VALUES (?, ?, ?, 'SOFT_RESERVED', NOW())
   ON CONFLICT (user_id, class_id) DO NOTHING;
   ```
   `ON CONFLICT DO NOTHING` is the second safety net — even if the same user somehow sends two concurrent requests and both slip through Redis, only the first INSERT lands. App checks `rowsAffected`; if 0, returns HTTP 409 (already booked).

4. **Booking Service publishes `booking.confirmed` to Kafka** — returns 200 to the client immediately. Notification Service asynchronously sends FCM/APNs push.

5. **Background TTL Expiry Job** (runs every 30s): finds expired soft reservations in Postgres (`status = SOFT_RESERVED AND reserved_at < NOW() - INTERVAL '5 minutes'`). For each: `INCR` Redis counter, `ZPOPMIN` next user from ZSET waitlist, create new soft reservation, publish `waitlist.promoted` Kafka event.

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

## Section 9 — 🗄️ Data Model

> **Say this out loud:** "Redis is the hot path, but Postgres is the source of truth — so the no-oversell invariant has to live in the schema itself, not only in the Lua script. If Redis is flushed and every counter resets to full capacity, the database must still refuse the 16th booking for a 15-seat class."

### Core Tables

```sql
CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    push_token          VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE studios (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(120) NOT NULL,
    city                VARCHAR(80) NOT NULL,
    -- IANA zone name (e.g. 'Asia/Kolkata'); all instants stored in UTC,
    -- converted to this zone only for display
    timezone            VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE classes (
    id                  UUID PRIMARY KEY,
    studio_id           UUID NOT NULL REFERENCES studios(id),
    -- NULL for a one-off class; set for every occurrence of a recurring series
    series_id           UUID,
    class_type          VARCHAR(40) NOT NULL,
    instructor_id       UUID,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    -- immutable once the class is published; the ceiling, never decremented
    capacity            INT NOT NULL,
    -- the mutable counter; Redis class:{id}:seats is a derived copy of
    -- (capacity - seats_taken)
    seats_taken         INT NOT NULL DEFAULT 0,
    status              VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',

    CONSTRAINT chk_class_capacity   CHECK (capacity > 0),
    CONSTRAINT chk_class_window     CHECK (ends_at > starts_at),
    CONSTRAINT chk_class_status     CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    -- THE no-oversell invariant. Any transaction that would push seats_taken
    -- past capacity is rejected by Postgres itself, no matter what Redis says.
    CONSTRAINT chk_no_oversell      CHECK (seats_taken >= 0 AND seats_taken <= capacity)
);

CREATE TABLE bookings (
    id                  UUID PRIMARY KEY,
    class_id            UUID NOT NULL REFERENCES classes(id),
    user_id             UUID NOT NULL REFERENCES users(id),
    status              VARCHAR(16) NOT NULL,
    -- client-supplied; makes a retried POST /v1/bookings a no-op
    idempotency_key     VARCHAR(64),
    -- set when this booking came from a waitlist promotion, for fairness audits
    promoted_from_waitlist BOOLEAN NOT NULL DEFAULT FALSE,

    reserved_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- reserved_at + 5 min; the TTL sweeper's only input
    expires_at          TIMESTAMPTZ NOT NULL,
    confirmed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    -- cancelled inside the 2-hour cutoff → fee applies
    cancelled_late      BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_booking_status CHECK (
        status IN ('SOFT_RESERVED', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_booking_window CHECK (expires_at > reserved_at)
);

CREATE TABLE waitlist_entries (
    id                  UUID PRIMARY KEY,
    class_id            UUID NOT NULL REFERENCES classes(id),
    user_id             UUID NOT NULL REFERENCES users(id),
    -- FIFO score; mirrors the Redis ZSET score
    joined_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    -- which cancellation freed the seat this entry was promoted against
    released_by_booking_id UUID REFERENCES bookings(id),
    promoted_at         TIMESTAMPTZ,
    -- promoted_at + 5 min; if the user does not claim, the seat moves on
    claim_expires_at    TIMESTAMPTZ,

    CONSTRAINT chk_waitlist_status CHECK (
        status IN ('WAITING', 'PROMOTED', 'CLAIMED', 'EXPIRED', 'LEFT'))
);
```

```sql
-- ── Correctness indexes (these are constraints, not performance tuning) ──

-- One live booking per user per class. This is what makes
-- INSERT ... ON CONFLICT DO NOTHING work, and it is the only thing that stops
-- the SAME user double-booking via a double-tap or a network retry.
-- Partial, so a user who cancels can legitimately re-book the same class.
CREATE UNIQUE INDEX uq_bookings_active_user_class
    ON bookings (user_id, class_id)
    WHERE status IN ('SOFT_RESERVED', 'CONFIRMED');

-- One promotion per freed seat. Two concurrent cancellation handlers, or one
-- handler retried after a timeout, cannot both promote a waitlist user against
-- the same released booking — the second INSERT/UPDATE violates this index.
CREATE UNIQUE INDEX uq_waitlist_promotion_per_release
    ON waitlist_entries (released_by_booking_id)
    WHERE released_by_booking_id IS NOT NULL;

-- One waitlist entry per user per class while the entry is live.
CREATE UNIQUE INDEX uq_waitlist_active_user_class
    ON waitlist_entries (class_id, user_id)
    WHERE status IN ('WAITING', 'PROMOTED');

-- ── Access-path indexes ──

-- Browse: "yoga classes at this centre tomorrow" (GET /v1/classes).
CREATE INDEX idx_classes_studio_start
    ON classes (studio_id, starts_at, class_type)
    WHERE status = 'SCHEDULED';

-- Reconciliation job: SELECT class_id, COUNT(*) ... GROUP BY class_id over
-- live bookings only, every 5 minutes.
CREATE INDEX idx_bookings_class_live
    ON bookings (class_id)
    WHERE status IN ('SOFT_RESERVED', 'CONFIRMED');

-- TTL sweeper (every 30s): find soft reservations past expires_at.
-- Partial index keeps it tiny — only unconfirmed rows are ever in it.
CREATE INDEX idx_bookings_expiring
    ON bookings (expires_at)
    WHERE status = 'SOFT_RESERVED';

-- "My bookings" screen.
CREATE INDEX idx_bookings_user_recent
    ON bookings (user_id, reserved_at DESC);

-- Waitlist head + position query, FIFO by join time.
CREATE INDEX idx_waitlist_fifo
    ON waitlist_entries (class_id, joined_at)
    WHERE status = 'WAITING';
```

**The seat claim as one statement — never SELECT-then-INSERT:**

```sql
-- Postgres fallback path (used when Redis is unavailable), and the
-- durable second gate behind the Redis DECR on the normal path.
-- One statement: the row lock is taken BY the UPDATE, and under READ
-- COMMITTED Postgres re-evaluates `seats_taken < capacity` against the
-- freshly committed row version after any concurrent writer releases it.
UPDATE classes
   SET seats_taken = seats_taken + 1
 WHERE id = :class_id
   AND seats_taken < capacity;
-- rowsAffected = 1 → seat claimed, proceed to INSERT the booking
-- rowsAffected = 0 → class is full, return 409 (no lock held, no retry loop)
```

### Key Schema Decisions:

- **`capacity` is immutable, `seats_taken` is the counter — and `chk_no_oversell` is the real answer to this interview.** The naive Stage 1 sketch does `UPDATE classes SET capacity -= 1`, which destroys the ceiling: after 15 bookings `capacity` reads 0 and you can no longer tell an oversold class from a full one, and nothing in the schema forbids `-1`. Splitting the two lets a `CHECK (seats_taken <= capacity)` exist, which is the only overbooking defence that survives a Redis flush, a bad deploy, a manual `psql` session, or a Lua bug. Redis prevents contention; the CHECK constraint prevents oversell. They are different jobs.
- **No `SELECT` before the claim.** `SELECT seats_taken ... ; if (seats_taken < capacity) { UPDATE }` is a time-of-check-to-time-of-use race: both transactions read 14, both write 15, and the class is oversold with neither transaction doing anything visibly wrong. The conditional `UPDATE ... WHERE seats_taken < capacity` collapses check and mutation into one atomic statement, and `rowsAffected` *is* the answer.
- **`uq_bookings_active_user_class` (partial unique):** two distinct problems get conflated in interviews — *overbooking the class* (N+1 people in 15 seats) and *double-booking one user* (the same member holding two seats in the same class from a double-tap). Redis `DECR` solves the first and is completely blind to the second: two rapid taps are two successful decrements. This index is what solves the second, and it's what `ON CONFLICT (user_id, class_id) DO NOTHING` arbitrates against. **Postgres nuance to name out loud:** to make the planner infer a *partial* unique index as the conflict arbiter you must repeat its predicate — `ON CONFLICT (user_id, class_id) WHERE status IN ('SOFT_RESERVED','CONFIRMED') DO NOTHING`. Omit the predicate and Postgres raises "no unique or exclusion constraint matching the ON CONFLICT specification" at runtime, and your second safety net silently never existed.
- **`released_by_booking_id` + `uq_waitlist_promotion_per_release`:** the waitlist promotion race is the failure mode nobody prepares for. One cancellation frees one seat, but the promotion path is triggered by a Kafka event with at-least-once delivery *and* re-driven by the 30-second sweeper — so the same freed seat can be promoted twice, handing one seat to two people. Tying every promotion to the identity of the cancellation that released it, with a unique index on that identity, makes promotion idempotent at the database layer rather than relying on the consumer never being redelivered.
- **`idx_bookings_expiring` is partial on purpose:** the sweeper runs every 30 seconds forever. A plain index on `expires_at` would span every booking ever made (365M rows at 1M/day for a year); restricted to `status = 'SOFT_RESERVED'` it holds only the few hundred rows currently unconfirmed, so the sweep is an index range scan over a hot, cache-resident index instead of a growing one.
- **Recurring series are materialised, not computed.** `series_id` groups occurrences, but every weekly 7 AM yoga slot is its own `classes` row. A booking must reference a concrete occurrence with its own capacity and its own seat counter — an RRULE evaluated at query time has nothing for `bookings.class_id` to point at, no place to hold `seats_taken`, and no way to express "this one Tuesday is cancelled". Cost: 500 centres × 12 classes × 90-day horizon ≈ 540K rows, generated by a nightly job that extends the horizon.
- **SQL vs NoSQL choice: PostgreSQL.** Three reasons, in order. (1) The core requirement is a *multi-row invariant*: claim a seat and insert a booking must be all-or-nothing, and cancel-then-promote touches `bookings`, `classes`, and `waitlist_entries` together. A single-item conditional write (DynamoDB `ConditionExpression`, Cassandra LWT) can guard the counter, but it cannot make the counter and the booking row commit together — you're left hand-rolling a saga for something a `BEGIN`/`COMMIT` does for free. (2) `CHECK` constraints and partial unique indexes are declarative correctness that no application bug can bypass; Cassandra and DynamoDB have neither. (3) Volume does not force the issue: 1M bookings/day at ~500 bytes is 180 GB/year (Section 4), comfortable for one primary with read replicas and monthly partitioning of `bookings` by `reserved_at`. **Redis is not the alternative to Postgres here — it's a contention absorber in front of it.** Treating the Redis counter as the source of truth is the single most common way this design fails (Section 13, Mistake 3).

---

## Section 10 — ⚠️ Trade-offs + Failure Modes

### Trade-off 1: Redis Seat Counter (AP) vs PostgreSQL SELECT FOR UPDATE (CP)

- **Chose:** Redis DECR + Lua script (AP-leaning: available even during network partition, atomic at Redis level)
- **Gain:** Microsecond latency for seat check; zero lock contention; 1,000 concurrent bookings handled without queueing
- **Lose:** Redis is not the durable source of truth; potential for brief inconsistency between Redis counter and Postgres booking count
- **Failure mode if wrong:** If Redis restarted without AOF persistence, counter resets to class capacity even if bookings exist. Postgres shows 15 confirmed bookings but Redis counter = 15 again → new bookings accepted → 30 people in a 15-person class. **Business impact:** Members who paid for a session arrive and cannot be accommodated — for a DocuSign-equivalent signing slot system (notarized transactions where a notary handles N signers per appointment), overbooking means some signers have invalid confirmed appointments, the notary cannot complete all sessions, and the legal ceremonies must be rescheduled, potentially delaying time-sensitive contracts.
- **Mitigation:** Redis AOF persistence + reconciliation job every 5 min; **Redis Sentinel** (a companion process that monitors your Redis master — if the master crashes, Sentinel automatically promotes a replica to master and redirects clients to it; this is how you get Redis high availability without manual intervention) for HA

---

### Trade-off 2: Async Waitlist Promotion vs Synchronous

- **Chose:** Async (Kafka event → background job)
- **Gain:** Booking cancellation returns immediately (200 OK); promoter gets notification within 30s; failure in promotion doesn't fail the cancellation
- **Lose:** ~30s delay between cancellation and waitlisted user receiving notification
- **Failure mode if wrong:** If promotion job crashes permanently, waitlisted users are never promoted. Seats sit empty in Redis. At scale (1,000 cancellations/day), this means empty classes and angry waitlisted users. **Business impact:** A member who waited weeks for a popular class slot gets silently skipped — for a DocuSign notarized signing queue (where a user waits for an available notary slot), a stuck promotion means the signer misses their confirmed window and must reschedule, potentially delaying a time-sensitive contract such as a real estate closing or medical power of attorney.
- **Mitigation:** **Dead letter queue** (a special Kafka topic where messages are parked when a consumer fails to process them after N retries — instead of losing the message or blocking the queue, it gets moved to the DLQ so an engineer can inspect it and replay it later) in Kafka; monitoring alert if waitlist depth grows without promotion events

---

### Trade-off 3: Soft Reservation TTL (5 min) vs Hard Immediate Confirmation

- **Chose:** Soft reservation with TTL
- **Gain:** Prevents "reservation squatting" — users can't hold seats indefinitely; expired seats auto-release
- **Lose:** User has 5 minutes to confirm; if they're in a bad network area, they may lose the seat
- **Failure mode if wrong:** TTL too short (e.g., 30 seconds) → legitimate users lose seats because the confirmation API call took 35 seconds. UX failure. TTL too long (30 min) → squatters hold all seats for a popular class, class appears full, everyone else joins waitlist, class starts with empty seats. **Business impact:** For a DocuSign live signing session (where a limited number of concurrent notary slots are available), a 30-minute hold means a bad actor can reserve all available notary slots and hold them indefinitely — real signers see "no availability" for hours, time-sensitive signings (mortgage closings) are delayed, and the customer escalates to DocuSign's enterprise support team.
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

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "A popular class at 7 AM opens for booking at 7 PM. Ten thousand users hit Book simultaneously. Your API Gateway has rate limiting, but the booking backend still receives a spike. How do you handle the thundering herd without overloading the service?"**
> The Redis DECR absorbs the concurrent writes at microsecond speed — that part is fine. The problem is the *Postgres write storm*: 10,000 concurrent INSERT rows for the waitlist and 50 confirmed bookings all hit the DB at once.
>
> **Virtual waiting room pattern (used by Ticketmaster, BookMyShow):** Instead of accepting the booking request immediately, issue a queue position to each user when they enter the booking flow. Redis sorted set `class:{id}:queue` (score = arrival timestamp) acts as the virtual queue. A release worker drains N positions per second into the actual booking API — for a 50-seat class, you might release 100 users/second into the actual booking flow. Users see their position: "You are #847 in line." The actual booking logic is unchanged; you've added a metered queue in front of it.
>
> **Trade-off:** adds latency (user #1000 waits 10 seconds before the booking API is called). Acceptable for a high-demand class; unacceptable for normal off-peak booking where no queue forms.
>
> **In an interview:** "For a flash-sale style class opening, I'd add a virtual waiting room: issue position tokens, drip users into the booking API at a controlled rate. For normal bookings, no queue — direct to API. The booking logic itself is identical in both paths."

---

**Q: "You said the Browse Service shows available seat counts. A user opens the class page and the counter shows 3 seats left. They take 2 minutes to read the class details, then click Book — but actually the class filled up 90 seconds ago. How do you handle stale seat counts in the UI?"**
> This is unavoidable staleness in any cache-based system. Three strategies:
>
> 1. **Polling (simple, recommended):** Browse Service caches seat count with a 10-second TTL. Client polls `GET /classes/{id}/availability` every 10 seconds. Stale window = up to 10s. At 10,000 users viewing one class, 10,000 × 6 polls/min = 60,000 reads/min — trivially handled by Redis cache. When the user clicks Book, the Redis Lua script is the source of truth: if counter is 0, the booking fails with "no seats available" even if the UI showed 3.
>
> 2. **Server-Sent Events (real-time, complex):** Browse Service holds an open SSE connection per viewer and pushes counter updates when seat count changes. Truly real-time — users see "2 seats left" the moment a booking completes. But: 10,000 concurrent SSE connections require event-loop architecture (Netty, Node, WebFlux). Heavy-weight for Java thread-per-request model.
>
> 3. **Optimistic UI + server validation (best UX):** Show cached seat count (stale OK), but when user clicks Book, immediately call the booking API. If it fails (counter = 0), show "Sorry, those seats just filled — you've been added to the waitlist." The UI optimistically shows counts from polling; the server validates at booking time.
>
> **Recommendation:** option 1 (polling + cache) + option 3 (server validation on click). SSE only if product explicitly requires sub-second real-time updates. The server is always the authority — stale UI display is a UX inconvenience, not a data correctness issue.

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "Your Redis Lua script is atomic. But what if the Redis node holding this class's seat counter goes down right after DECR executes, before SETEX creates the reservation key? What's the actual failure state and how do you recover?"**
> This is the hardest failure mode in this system. After DECR succeeds on Redis node X:
> - Redis counter: decremented (seat looks taken)
> - Redis reservation key: doesn't exist (SETEX never ran)
> - Postgres booking: doesn't exist (booking service never got to that step)
>
> **AOF persistence (the primary mitigation):** Redis AOF (Append Only File) — a durability mode where every write command is appended to a log file on disk. On restart, Redis replays the log to recover its state.

**⚠️ Critical distinction — two AOF fsync modes:**

| Mode | Config | Durability | Throughput | Use for |
|---|---|---|---|---|
| `appendfsync always` | Every write fsynced to disk before ACK | **Zero data loss** — every command is on disk before Redis acknowledges | ~30-50% throughput reduction — fsync is an I/O syscall per write | Finance, legal, compliance systems |
| `appendfsync everysec` (default) | fsync once per second, background | **Up to 1 second of data loss** on crash | Near-native throughput — minimal impact | Class booking systems ✅ |
| `appendfsync no` | OS decides when to flush | Up to OS buffer loss (seconds to minutes) | Fastest | Cache-only, no durability needed ❌ |

**For a booking system: use `appendfsync everysec`.** The booking system is resilient to 1 second of data loss because the reconciliation job (every 5 minutes) will detect and correct any discrepancy. Using `appendfsync always` for a booking counter would add unnecessary I/O overhead to every DECR operation — overkill for a class booking.

**In an interview:** "I'd configure Redis AOF with `appendfsync everysec`. This limits data loss on crash to at most 1 second of writes — tolerable given our reconciliation job runs every 5 minutes and corrects any counter drift. `appendfsync always` would guarantee zero loss but cuts Redis write throughput significantly — not worth it for a booking counter."

On restart, Redis replays the log. If DECR was fsynced before crash (within the 1-second window), counter correctly reflects -1 after restart. If SETEX was not fsynced (crash happened between DECR and SETEX), the counter is -1 but no reservation key exists.
>
> **Reconciliation job (the safety net):** Runs every 5 minutes. Queries Postgres: `SELECT class_id, COUNT(*) FROM bookings WHERE status IN ('SOFT_RESERVED','CONFIRMED') GROUP BY class_id`. Compares that count to Redis counter. If Redis counter < (capacity - confirmed bookings), it means counter was decremented extra — INCR Redis back.
>
> **Why not MULTI/EXEC instead of Lua?** MULTI/EXEC queues commands and executes them as a batch, but doesn't actually make them atomic in the "fails together" sense. If the Redis server crashes mid-EXEC, some commands in the batch may have already written. Lua script is also not immune to this — the atomicity guarantee is "no other Redis command interleaves," not "crash-safe."
>
> **In an interview:** "No Redis solution is fully crash-safe without AOF. AOF + reconciliation is the belt-and-suspenders approach: AOF minimizes data loss to ~1 second; the reconciliation job catches any remaining discrepancies. This is acceptable for a booking system where ~0.01% of bookings in a crash window are affected."

---

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these BEFORE the interview prevents you from making them under stress. Every one of them has been the reason a candidate failed this exact question.

---

- **Mistake 1:** Checking capacity with a `SELECT`, then inserting the booking — `SELECT seats_taken FROM classes WHERE id = ?` followed by `if (seats_taken < capacity) { INSERT INTO bookings ... }`. → **Why it's wrong:** Classic TOCTOU (time-of-check-to-time-of-use) race. Two requests for the 15th seat both read `seats_taken = 14`, both pass the `if`, both insert. Neither transaction did anything individually invalid, no error is raised, and nothing in the logs looks wrong — the instructor just finds 16 people in a 15-person class. Wrapping the two statements in a transaction does **not** fix it: under READ COMMITTED (Postgres's default) two plain `SELECT`s don't block each other. Three things *would* fix it, and you should name which one you mean — `SELECT ... FOR UPDATE` (correct, but serializes every booker behind one row lock: that is precisely Stage 1's breaking point at 1,000/sec), SERIALIZABLE isolation (correct, but now you handle serialization-failure retries on every booking), or the conditional single-statement UPDATE below (correct, and holds the row lock only for the duration of one statement). → **What to say instead:** "I never read-then-write. The claim is a single conditional statement — `UPDATE classes SET seats_taken = seats_taken + 1 WHERE id = ? AND seats_taken < capacity` — and `rowsAffected` is the decision: 1 means I own the seat, 0 means the class is full and I return 409. The check and the mutation are the same atomic operation. On the hot path Redis `DECR` inside a Lua script plays the same role, and the `CHECK (seats_taken <= capacity)` constraint in Postgres is the backstop if Redis is ever wrong."

- **Mistake 2:** Forgetting the waitlist promotion race — treating "on cancellation, free the seat and promote the next person" as two independent steps. → **Why it's wrong:** Two bugs hide here. First, **double-free**: the code in the LLD section originally did `seatCounter.increment(classId)` *and then* `waitlistService.promoteNext(classId)` — the seat is returned to the pool *and* handed to a waitlisted user, so the class ends up one over capacity. Second, **double-promotion**: the promotion is driven by a Kafka `booking.cancelled` event with at-least-once delivery and is also re-driven by the 30-second sweeper, so one freed seat can be promoted to two different users, both of whom receive "your spot is confirmed" push notifications. → **What to say instead:** "Releasing a seat and promoting the waitlist head is one atomic decision, not two: a single Lua script does `ZPOPMIN` and, if a user was popped, transfers the seat directly to them via `SETEX` *without* `INCR`-ing the counter — it only `INCR`s when the waitlist is empty. And because the Kafka consumer can be redelivered, promotion is made idempotent in Postgres too: every promotion records `released_by_booking_id` under a unique index, so the same cancellation can never promote twice."

- **Mistake 3:** Treating the Redis counter as the source of truth. → **Why it's wrong:** Redis is a cache with a durability window. With `appendfsync everysec` you can lose up to a second of writes; with no AOF at all, a restart resets every counter to full capacity while 15 confirmed bookings still sit in Postgres — and the system happily sells all 15 seats a second time. Candidates who say "the seat count lives in Redis" have no answer to "what is the number after a failover?" → **What to say instead:** "Postgres is authoritative; the Redis counter is a derived hot-path copy of `capacity - seats_taken`. Three things follow: counters are rehydrated from Postgres on startup and on cache miss (never initialised to `capacity` blindly), a reconciliation job compares them every 5 minutes and corrects drift, and the `CHECK (seats_taken <= capacity)` constraint means that even a maximally wrong Redis counter degrades into rejected bookings rather than an oversold class."

- **Mistake 4:** Solving overbooking of the *class* and assuming you've also solved double-booking of the *same user*. → **Why it's wrong:** They are different races with different fixes. Redis `DECR` is blind to identity — a user who double-taps Reserve, or whose client retries after a 30-second timeout, produces two successful decrements and two bookings. The class isn't oversold, but that member now holds two of the 15 seats, is charged twice, and one seat is dead inventory that the waitlist can never reach. → **What to say instead:** "Two separate guards. Per-class capacity is the Redis `DECR` plus the CHECK constraint. Per-user uniqueness is a partial unique index on `(user_id, class_id) WHERE status IN ('SOFT_RESERVED','CONFIRMED')`, arbitrated by `INSERT ... ON CONFLICT DO NOTHING` — partial so that a cancel-and-rebook is still legal. The Lua script also checks the existing `reserve:{classId}:{userId}` key first and returns -1 before touching the counter, so the common retry never even reaches the database."

- **Mistake 5:** Designing the happy path only — no answer for the soft reservation that is never confirmed. → **Why it's wrong:** Every abandoned reservation (app backgrounded, payment sheet dismissed, phone dies) permanently removes a seat from a class that then starts half empty while 40 people sit on the waitlist. At a 20% abandonment rate on a 20-seat class, 4 seats/class evaporate. → **What to say instead:** "A reservation is soft with an explicit `expires_at`, and the seat is recovered by a 30-second sweeper over a partial index on `(expires_at) WHERE status = 'SOFT_RESERVED'` — not by Redis key expiry alone, because Redis keyspace notifications are at-most-once and a subscriber restart loses the event permanently. The sweeper marks the row EXPIRED, releases the seat, and promotes the waitlist in the same idempotent path used by cancellation."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

**Note:** This is a Cure.fit question, not a DocuSign domain question. The DocuSign angle: any system that allocates scarce time-bounded slots (notary signing sessions, live signing ceremonies with a limited number of concurrent participants) maps directly to this booking architecture. Name this pivot if asked.

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | Redis Lua script is testable with an embedded Redis (Testcontainers) — run DECR + SETEX atomically, verify both keys exist, verify DECR is atomic under concurrent test threads. Postgres booking inserts testable with an in-memory H2 for unit tests. Reconciliation job is a pure function (Postgres count vs Redis counter → INCR if drift detected) — mockable without live Redis. |
| Usability | ✅ | POST /bookings → {booking_id, status: SOFT_RESERVED, expires_at}; POST /bookings/{id}/confirm → {status: CONFIRMED}. Client knows exactly when the soft reservation expires (expires_at field). For DocuSign: a signer reserving a live notary session slot sees a 5-minute countdown timer on the confirmation screen — the TTL is surfaced in the API response, not hidden. |
| Extensibility | ✅ | New booking constraints (e.g., "max 2 back-to-back classes per user") = new validation rule in BookingService — no schema change. New notification channels = new Kafka consumer. For DocuSign: adding a "waitlist auto-confirm" feature for premium notary sessions = new ZPOPMIN consumer with a different promotion logic, zero changes to the booking hot path. |
| Security | ✅ | Soft reservation TTL prevents indefinite seat squatting — a signer who clicks "book" but never confirms cannot lock out other signers. Rate limiting (API Gateway, 100 req/min per user_id) prevents bot-driven seat monopolization. Booking confirmation requires the original booking_id + user_id match — prevents one user from confirming another user's reservation. |
| Availability | ✅ | Redis Sentinel for automatic failover (< 60s RPO/RTO). If Redis is unavailable, fallback to Postgres SELECT FOR UPDATE (10× slower but correct) — for DocuSign: even during Redis maintenance, notary session bookings continue, with degraded throughput. Kafka decouples waitlist promotion and notifications — their failure never blocks a booking. |
| Scalability | ✅ | Redis DECR is O(1) — at 1,000 bookings/sec peak (Section 4: 2M DAU, 100K concurrent), the Redis hot path handles 1K atomic DECR + SETEX operations/sec without lock contention. Postgres handles only confirmed writes (~200/sec at 20% confirm rate) — well within capacity. For DocuSign: 15K notary sessions/day × 10 slots each = 150K concurrent slot reservations, handled by the same Redis DECR architecture. |
| Observability & Traceability | ✅ | Every booking logs: (user_id, class_id, booking_id, timestamp, status, Redis counter before/after). Alert: Redis counter < 0 → reconciliation needed (overbooking detected). Alert: confirm rate < 50% after 5 minutes → users are abandoning reservations (TTL too short or UX problem). For DocuSign: "how many notary slots were overbooked on June 15?" is a query on booking_events joined with class_capacity — the reconciliation audit trail is in Postgres. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "I'd design Cult.fit's class booking system around a Redis-first seat counter with atomic Lua scripts for the critical hot path, backed by Postgres as durable source of truth. The core insight is that Redis DECR is atomic in Redis's single-threaded model — 1,000 concurrent bookings on a 15-seat yoga class all get serialized at the Redis level with no lock contention. Soft reservations with 5-minute TTL prevent seat squatting, and a background job reconciles expired reservations and auto-promotes the waitlist. Kafka decouples notifications and analytics from the booking critical path — a notification failure never fails a booking. The hardest failure mode is Redis crash post-DECR pre-Postgres-write, mitigated by AOF persistence and a 5-minute reconciliation job. For Cure.fit specifically, I'd add API Gateway rate limiting to absorb the 7 PM booking surge, and late-cancellation fee enforcement at the service layer — 2-hour cutoff is a business rule the backend must own."

---

---

## 🔌 LLD Drill-Down — Class Structure for the Booking Service

> **Trigger:** Interviewer says "Walk me through the class design for the booking service" or "Show me how you'd model the seat reservation" — expected follow-up after the Redis atomic DECR HLD discussion.
>
> **What they're testing:** Whether you understand that the concurrency guarantee lives in Redis (not Java `synchronized`), how soft reservations model as a domain object with state transitions, and how the Postgres + Redis layers interact cleanly.

---

### 🧠 Mental Model Before You Draw

The booking system has two layers of state:
- **Redis** — transient seat counter (`capacity - bookings`), soft reservation TTL keys
- **Postgres** — durable bookings (source of truth)

The class design must reflect this: `BookingService` orchestrates Redis → Postgres in the right order. The critical insight: the `DECR` happens in Redis first. If it succeeds, we write to Postgres. If Postgres write fails, we `INCR` Redis back (compensating action).

**Key insight to say out loud:** "There's no Java `synchronized` here. The atomicity guarantee is in Redis's single-threaded model — the Lua script serializes all DECR operations. My Java class just calls Redis and trusts it."

---

### 🏗️ Class Structure

```
┌────────────────────────────────────────────────────────────┐
│                      BookingController                     │
│  POST /bookings      → BookingService.reserve()            │
│  POST /bookings/{id}/confirm → BookingService.confirm()    │
│  DELETE /bookings/{id}       → BookingService.cancel()     │
└────────────────────────────────────────────────────────────┘
         │ calls
         ▼
┌────────────────────────────────────────────────────────────┐
│                      BookingService                        │
│  - seatCounter: SeatCounter       (wraps Redis)            │
│  - bookingRepo: BookingRepository (wraps Postgres)         │
│  - waitlistService: WaitlistService                        │
│  + reserve(classId, userId): SeatReservation               │
│  + confirm(bookingId, userId): Booking                     │
│  + cancel(bookingId, userId): void                         │
└────────────────────────────────────────────────────────────┘
         │
   ┌─────┴─────────────────────┐
   ▼                           ▼
┌─────────────────┐    ┌─────────────────────────┐
│  SeatCounter    │    │  BookingRepository       │
│  (Redis layer)  │    │  (Postgres layer)        │
│  + decrement()  │    │  + insert(booking)       │
│  + increment()  │    │  + updateStatus(id, st.) │
│  + getCount()   │    │  + findById(id)          │
└─────────────────┘    └─────────────────────────┘

BookingStatus (enum): SOFT_RESERVED, CONFIRMED, CANCELLED, EXPIRED

SeatReservation (domain object — returned on reserve())
  - reservationId: String       ← = bookingId
  - classId:       String
  - userId:        String
  - status:        BookingStatus  (SOFT_RESERVED)
  - expiresAt:     Instant        ← TTL expiry timestamp

Booking (domain object — returned on confirm())
  - bookingId:     String
  - classId:       String
  - userId:        String
  - status:        BookingStatus  (CONFIRMED)
  - confirmedAt:   Instant
```

---

### 🔌 Key Interface

```java
/**
 * Wraps all Redis seat counter operations.
 * BookingService never writes Redis keys directly.
 * Separates Redis concern from booking business logic. SoC.
 */
public interface SeatCounter {

    // Returns remaining count after decrement; -1 if no seats left
    int decrement(String classId);

    // Compensating action — called on Postgres write failure
    void increment(String classId);

    int getAvailableCount(String classId);

    // Sets a soft reservation TTL key for the user-class pair
    void setSoftReservation(String classId, String userId, Duration ttl);

    boolean hasSoftReservation(String classId, String userId);
}
```

---

### 🖊️ Critical Classes — Write These in the Interview

**BookingService — the orchestrator (this is the class the interviewer wants to see):**

```java
public class BookingService {

    private final SeatCounter seatCounter;
    private final BookingRepository bookingRepo;
    private final WaitlistService waitlistService;

    private static final Duration SOFT_RESERVATION_TTL = Duration.ofMinutes(5);

    /**
     * Reserve a seat (soft reservation).
     * Order of operations:
     *   1. DECR Redis counter atomically → if -1, no seats, reject
     *   2. Write SOFT_RESERVED booking to Postgres
     *   3. Set TTL key for auto-expiry
     *   4. If Postgres fails → INCR Redis back (compensating action)
     *
     * No Java synchronized — Redis single-threaded model serializes DECR.
     */
    public SeatReservation reserve(String classId, String userId) {
        // Step 1: Atomic seat claim in Redis
        int remaining = seatCounter.decrement(classId);
        if (remaining < 0) {
            throw new NoSeatsAvailableException("Class " + classId + " is fully booked.");
        }

        // Step 2: Persist to Postgres
        String reservationId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(SOFT_RESERVATION_TTL);

        try {
            bookingRepo.insert(reservationId, classId, userId,
                BookingStatus.SOFT_RESERVED, expiresAt);
        } catch (Exception e) {
            // Compensating action — Postgres write failed, release the seat
            seatCounter.increment(classId);
            throw new BookingException("Failed to persist reservation.", e);
        }

        // Step 3: Set soft reservation TTL key (for auto-expiry lookup)
        seatCounter.setSoftReservation(classId, userId, SOFT_RESERVATION_TTL);

        return new SeatReservation(reservationId, classId, userId,
            BookingStatus.SOFT_RESERVED, expiresAt);
    }

    /**
     * Confirm a soft reservation → payment has been taken.
     * Transitions: SOFT_RESERVED → CONFIRMED
     */
    public Booking confirm(String bookingId, String userId) {
        Booking booking = bookingRepo.findById(bookingId);

        if (booking.getStatus() != BookingStatus.SOFT_RESERVED) {
            throw new InvalidBookingStateException(
                "Cannot confirm booking in state: " + booking.getStatus()
            );
        }
        if (!booking.getUserId().equals(userId)) {
            throw new UnauthorizedException("Booking does not belong to user.");
        }
        if (Instant.now().isAfter(booking.getExpiresAt())) {
            throw new ReservationExpiredException("Soft reservation has expired.");
        }

        bookingRepo.updateStatus(bookingId, BookingStatus.CONFIRMED);
        return bookingRepo.findById(bookingId);
    }

    /**
     * Cancel a booking → release the seat.
     * Late cancellation fee is a business rule enforced here (not in Redis).
     */
    public void cancel(String bookingId, String userId) {
        Booking booking = bookingRepo.findById(bookingId);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            // idempotent — already cancelled
            return;
        }

        // Business rule: late cancellation fee (< 2 hours before class)
        boolean isLateCancellation = isWithinCutoff(booking.getClassStartTime(), Duration.ofHours(2));

        bookingRepo.updateStatus(bookingId, BookingStatus.CANCELLED);

        // ONE atomic Redis operation, not increment() followed by promoteNext().
        // The Lua script does ZPOPMIN and then EITHER hands the freed seat
        // straight to that user via SETEX (no INCR — the seat is transferred),
        // OR, if the waitlist is empty, INCRs the counter back.
        // Calling increment() and promoteNext() separately double-frees the
        // seat: the pool gets it back AND a waitlisted user gets it.
        // bookingId is passed as the release token so a redelivered Kafka
        // event cannot promote against the same freed seat twice.
        waitlistService.releaseSeatAndPromote(booking.getClassId(), bookingId);

        if (isLateCancellation) {
            // Charge late-cancel fee — async, via event/Kafka
            // lateCancellationEventPublisher.publish(bookingId, userId);
        }
    }

    private boolean isWithinCutoff(Instant classStartTime, Duration cutoff) {
        return Instant.now().isAfter(classStartTime.minus(cutoff));
    }
}
```

**RedisSeatCounter — the Redis implementation:**

```java
/**
 * Redis-backed seat counter using Lua script for atomic DECR.
 * Key: seat_count:{classId}
 * Lua atomicity: no other Redis command runs between GET and DECR.
 * This is the distributed concurrency guarantee — not Java synchronized.
 */
public class RedisSeatCounter implements SeatCounter {

    private static final String DECR_LUA =
        "local count = tonumber(redis.call('GET', KEYS[1])) " +
        "if count == nil or count <= 0 then return -1 end " +
        "return redis.call('DECR', KEYS[1])";

    private final RedisClient redisClient;

    @Override
    public int decrement(String classId) {
        String key = "seat_count:" + classId;
        return (int) redisClient.eval(DECR_LUA, key);
    }

    @Override
    public void increment(String classId) {
        redisClient.incr("seat_count:" + classId);
    }

    @Override
    public void setSoftReservation(String classId, String userId, Duration ttl) {
        String key = "soft_reservation:" + classId + ":" + userId;
        redisClient.setex(key, ttl.getSeconds(), "1");
    }

    @Override
    public boolean hasSoftReservation(String classId, String userId) {
        return redisClient.exists("soft_reservation:" + classId + ":" + userId);
    }

    @Override
    public int getAvailableCount(String classId) {
        String val = redisClient.get("seat_count:" + classId);
        return val == null ? 0 : Integer.parseInt(val);
    }
}
```

---

### 🔁 Concurrency — The Core Point

```
1000 users simultaneously hit POST /bookings for a 15-seat yoga class:

Thread 1:  RedisSeatCounter.decrement("yoga-7pm") → Lua runs → count=14 → proceed
Thread 2:  RedisSeatCounter.decrement("yoga-7pm") → Lua runs → count=13 → proceed
...
Thread 15: RedisSeatCounter.decrement("yoga-7pm") → Lua runs → count=0  → proceed
Thread 16: RedisSeatCounter.decrement("yoga-7pm") → Lua runs → count=-1 → REJECT
...
Thread 1000: REJECT

No Java synchronized needed. Redis single-threaded execution model
serializes all 1000 DECR calls. One by one. No race possible.
```

**The only race is Redis crash between DECR and Postgres write:**
- DECR committed (Redis log fsynced), Postgres write fails → `increment()` compensates
- DECR committed, app server crashes before Postgres write → reconciliation job detects: `Redis counter < (capacity - confirmed_bookings)` → `INCR` Redis back
- AOF persistence minimizes this window to <1 second

---

### 🔬 LLD Interview Probes — CF1 Specific

**Q: "Walk me through what happens when 1000 users hit /bookings at 7 PM for a 15-seat class."**
> All 1000 HTTP requests reach the API Gateway. Each one calls `BookingService.reserve()`. Inside, each calls `RedisSeatCounter.decrement()` — a Redis Lua script. Redis executes them one at a time (single-threaded). First 15 decrement from 15 down to 0 and get a valid reservation. Requests 16-1000 get -1 from the Lua script and receive a 409. No overselling. No Java locking.

**Q: "What happens if the Postgres write fails after Redis DECR succeeds?"**
> `reserve()` catches the exception and calls `seatCounter.increment(classId)` — compensating action returns the seat to Redis. The user gets an error response. The seat counter is restored. Another user can now take that seat. This is a mini-saga: DECR is the forward action, INCR is the compensating rollback.

**Q: "A user reserves a seat but never confirms (payment fails). How does the seat get returned?"**
> Two mechanisms: (1) The `soft_reservation:{classId}:{userId}` Redis key has a 5-minute TTL — when it expires, the seat is logically freed. (2) A reconciliation job runs every 5 minutes: `SELECT count(*) FROM bookings WHERE class_id=X AND status='SOFT_RESERVED' AND expires_at < NOW()` — for each expired row, update status to EXPIRED and INCR Redis counter. The reconciliation job is the safety net for any key expiry edge cases.

**Q: "How does the waitlist work at the class design level?"**
> `WaitlistService` wraps a Redis `ZSET` keyed by `waitlist:{classId}`. On join-waitlist: `ZADD waitlist:{classId} {timestamp} {userId}` — timestamp is the score, giving FIFO ordering. On cancellation: `BookingService.cancel()` calls `waitlistService.releaseSeatAndPromote(classId, cancelledBookingId)`, which runs **one** Lua script: `ZPOPMIN waitlist:{classId}` and then either `SETEX` the freed seat directly to that user (a *transfer* — deliberately no `INCR`, because the seat never returns to the public pool) or, if the ZSET was empty, `INCR` the counter. The promoted user gets a booking row with `promoted_from_waitlist = true` and `released_by_booking_id = cancelledBookingId`; the unique index on that column (Section 9) makes the whole promotion idempotent, so a redelivered `booking.cancelled` event is a no-op instead of a second promotion.

**Q: "Why is BookingStatus an enum and not just a String in the DB?"**
> Enums enforce valid state transitions at the Java layer before the DB sees them. `confirm()` checks `booking.getStatus() != BookingStatus.SOFT_RESERVED` — if we used a String, "SoFt_ReSeRvEd" would pass the equals check and corrupt state. The enum also makes `switch` statements exhaustive — adding a new status forces updating every switch that processes bookings. At interview: "I'd store it as a VARCHAR in Postgres — the DB stores strings, Java enforces the enum. No DB migration needed when I add a state."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | **File created.** Type A — System Design, Cult.fit context. Based on: Cure.fit interview reports (ZoomCar variant confirmed), `42-inventory-management-booking.md`, `02-rate-limiting.md`, DELIVERY-RECIPE framework. Covers: 6 clarifying questions with assumed answers, scale estimation (1M bookings/day, 1K/sec peak burst), Redis Lua atomic DECR for double-booking prevention, soft reservation TTL model, waitlist ZSET auto-promotion, background reconciliation job. Three deep dives: reservation atomicity (Lua vs SELECT FOR UPDATE vs optimistic locking), waitlist fairness (ZPOPMIN), TTL expiry recovery (keyspace notifications vs background polling). Trade-offs: Redis AP vs Postgres CP, async waitlist promotion, 5-min TTL calibration. Cure.fit-specific: 7 PM booking surge handling, late-cancellation fee enforcement, instructor cancellation broadcast, waitlist as product signal. |
| Jul 4, 2026 | **Diagram rewrite + 4 new Q&As.** Replaced nested `[Box]` notation inside outer container with fully box-drawing diagram: API Gateway → Load Balancer → Booking/Browse Services → Redis (seat counters + soft reservations + waitlist ZSET) → PostgreSQL → Kafka. Key invariant callout added. New Q&As: (1) **Thundering herd / virtual waiting room** — position token issued at queue entry, Redis ZSET acts as metered queue, release N users/sec into booking API — transforms 10K concurrent hits into controlled drip; (2) **Stale seat counts in Browse UI** — three options (polling + 10s TTL cache, SSE for real-time, optimistic UI + server validation on click); recommendation: polling + server validation, SSE only for sub-second UX requirements; (3) **Redis Lua crash mid-script failure analysis** — DECR committed but SETEX not reached → counter decremented with no reservation key; AOF minimizes data loss to ~1s window; reconciliation job catches discrepancy within 5 minutes; why MULTI/EXEC doesn't help. |
| Jul 4, 2026 | **Section 6 restructured into 3-stage progressive HLD.** Stage 1 (DB-only, SELECT FOR UPDATE) — establishes baseline, identifies lock-contention breaking point at 1,000/sec. Stage 2 (Redis hot path + ZSET waitlist) — moves seat counter to Redis atomic DECR, identifies synchronous notification as next breaking point. Stage 3 (Kafka async decoupling, production) — decouples notifications/waitlist promotion/analytics via Kafka so nothing on the async path can fail a booking. Three decision tables added: lock strategy (SELECT FOR UPDATE vs optimistic vs Redis DECR), waitlist storage (Postgres vs Redis List vs Redis ZSET), async transport (HTTP vs Redis pub/sub vs Kafka). Cross-refs added to `SystemDesignConcepts/` for each table. Verdict alignment verified: all three Section 6 table verdicts match Section 7 deep dive choices (Redis Lua ✅, Redis ZSET ✅, Kafka ✅). |
| Aug 2026 | **Audit pass — Sections 9 and 13 added (both missing entirely) + one real bug fixed.** (1) **Section 9 Data Model created** with actual DDL for `users`, `studios`, `classes`, `bookings`, `waitlist_entries`. The schema now expresses the overbooking invariant rather than delegating it entirely to Redis: `capacity` is immutable and `seats_taken` is the counter (the old Stage 1 sketch's `UPDATE classes SET capacity -= 1` destroys the ceiling, so no CHECK is even expressible), guarded by `CHECK (seats_taken >= 0 AND seats_taken <= capacity)` — the one overbooking defence that survives a Redis flush. Claim is shown as a single conditional `UPDATE ... WHERE seats_taken < capacity` with `rowsAffected` as the decision, never SELECT-then-INSERT. Three correctness indexes: partial unique `(user_id, class_id) WHERE status IN ('SOFT_RESERVED','CONFIRMED')` (same-user double-booking; includes the Postgres nuance that `ON CONFLICT` must repeat a partial index's predicate to infer it as arbiter, or it raises at runtime), unique `released_by_booking_id` (makes waitlist promotion idempotent against at-least-once Kafka redelivery), partial unique waitlist membership. Plus four access-path indexes each justified by the query that needs it, materialised recurring-series rationale, and the SQL-vs-NoSQL decision. (2) **Section 13 Common Mistakes created** — 5 mistakes: SELECT-then-INSERT TOCTOU (including why a transaction alone doesn't fix it under READ COMMITTED), the waitlist promotion double-free/double-promotion race, treating the Redis counter as source of truth, conflating class overbooking with same-user double-booking, and no recovery path for abandoned soft reservations. (3) **Bug fix in the LLD `cancel()` method:** it called `seatCounter.increment()` *and then* `waitlistService.promoteNext()`, which double-frees the seat (returned to the pool **and** handed to a waitlisted user → one over capacity). Replaced with a single atomic `releaseSeatAndPromote(classId, bookingId)`; the corresponding LLD probe answer, which was ambiguous about whether the counter had already been incremented, was made definite. |
| Jul 5, 2026 | **Section 10 business impact + Section 14 added (was missing entirely).** Section 10: added **Business impact:** to all 3 trade-offs — DocuSign notarized transaction overbooking when Redis AP model sacrifices CP guarantee, real estate closing delayed by stuck waitlist promotion (async decoupling failure), mortgage notary closing blocked by session slot squatting (TTL miscalibration). Section 14: new section created from scratch — all 7 dimensions written with Cure.fit/DocuSign notary pivot, including real estate closing time-pressure (Usability), Redis Lua atomicity as double-booking prevention mechanism (Correctness), overbooking incident RCA traceable via Kafka message timestamps (Observability). |
