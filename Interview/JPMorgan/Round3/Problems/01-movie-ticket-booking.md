# Movie Ticket Booking System

> **Standard:** `Interview/JPMorgan/Round3/notes-standards-interview-problems.md`
> **Archetype:** Hot-resource race — seats are the shared mutable resource.
> **Problem #1 of 5.**

---

## 🎯 Problem Statement

Design a movie ticket booking system (think BookMyShow). Users browse shows,
pick seats, pay, and receive a booking confirmation. Multiple users can attempt
to book the same seat simultaneously — preventing double-booking is the core
design challenge.

---

## ❓ Clarifying Questions

**Scale:**
1. How many concurrent users at peak — new release Friday night? 10K? 100K simultaneous bookings?
2. Is this single-city or national? How many shows and theatres?
3. What is the read:write ratio? (Browsing seat availability >> actual booking — I'd estimate 100:1.)
4. Seat inventory per show? Typically 100–500 seats, but millions of concurrent shows nationwide.

**Functional scope:**
5. Does "booking" include payment processing, or do we call a separate payment service?
6. Do we support a seat-hold window — user picks seats, then has 10 minutes to pay — or is it lock-on-click?
7. Are cancellations and refunds in scope, or just the forward booking flow?
8. Can a user book multiple seats in one transaction, or is it one seat at a time?

**Consistency:**
9. Is optimistic seat display acceptable? (Show seat as available, fail gracefully if taken at lock time.) Or must we hide already-contested seats before they're locked?
10. How stale can availability data be for browsing? Can I cache it for 5 seconds? 30 seconds?

**Latency SLA:**
11. What is the acceptable response time for seat browsing? 200ms?
12. What is the acceptable end-to-end booking latency? 2s? 5s?

**Third-party:**
13. Does payment go through an external gateway (Stripe, Razorpay)? What is its timeout SLA?
    *(This is my most critical question — the gateway SLA determines whether payment is synchronous or async with polling.)*

**Failure model:**
14. If payment fails, should we automatically retry, or require the user to re-initiate?

**Compliance:**
15. Any PCI-DSS constraints? Can we store raw payment tokens, or must we tokenize before touching our DB?

> **Assumptions (if interviewer doesn't answer §13):** External gateway, 5s p99, synchronous call with 10s hard timeout. Booking fails if timeout exceeded; user retries.

---

## 🏗️ LLD — Class Diagram

### 📐 How to Build This Diagram in the Interview — Step by Step

> **Why this section exists:** You cannot draw the full diagram below from scratch in 15 minutes — and you don't need to. What you need is a repeatable thought process that lets you reach the critical pieces quickly, explain your reasoning as you go, and survive interruptions without losing the thread. This section teaches that process. Read it first; treat the complete diagram at the bottom as the answer key.

---

#### The 7 moves, in order

---

**Move 1 — List the nouns from the problem statement. Don't draw yet.**

Before touching the board, say: *"Let me identify the core entities first — and separate the nouns the problem gives me from the ones that constraints force me to invent."*

Read the problem: "movie ticket booking — users browse shows, pick seats, pay, get confirmation."

**From the statement directly:** Movie, Theatre, Screen, Seat, Show, User, Booking, Payment

**Derived from constraints:**
- *"user picks seats, then has up to 10 minutes to pay — there is a gap between selection and payment"* → **Seat.heldUntil: Instant** (the checkout-window expiry clock; without this field there is no way to represent "someone is mid-checkout" and no way to auto-release abandoned holds)
- *"failing at seat-lock and failing at payment are different recovery paths — one has charged the gateway, one has not"* → **BookingStatus** as a state machine enum with distinct **SEAT_LOCKED** ≠ **PAYMENT_PENDING** states (not a String field any caller can set freely)
- *"at scale, multiple BookingService pods run on different JVMs — synchronized in one JVM is invisible to another"* → **SeatLockStrategy** interface (single seam that makes the in-JVM lock swappable for Redis SET NX without modifying BookingService — covered in depth in Move 5)

Write them as a flat word list in the corner — not boxes yet.

> **Why say this out loud?** It signals you're reading the domain, not guessing. The "derived" split shows you already know which entities are given and which ones your design forces into existence.

**Your board at the end of Move 1:**

```
From statement:  Movie · Theatre · Screen · Seat · Show · User · Booking · Payment
Derived:         Seat.heldUntil (expiry clock for the checkout window),
                 BookingStatus enum with SEAT_LOCKED ≠ PAYMENT_PENDING,
                 SeatLockStrategy (interface — LLD→HLD swap seam)
```

---

**Move 2 — Classify each noun before drawing it.**

For each noun, ask: **entity** (has an ID, has a lifecycle, has behavior), **enum** (finite named values, no behavior), or **service** (coordinates other objects)?

```
Movie     → entity   (has movieId, has attributes)
Theatre   → entity   (has theatreId, contains screens)
Screen    → entity   (has screenId, contains seats)
Seat      → entity   (has seatId, has status — YOUR HOT RESOURCE)
Show      → entity   (joins Movie + Screen + time)
User      → entity   (has userId)
Booking   → entity   (has bookingId, has status — YOUR STATE MACHINE)
Payment   → entity   (has paymentId — child of Booking)

SeatStatus    → enum  (finite states: AVAILABLE / HELD / BOOKED / CANCELLED)
BookingStatus → enum  (state machine: PENDING → ... → CONFIRMED/FAILED)
SeatType      → enum  (SILVER / GOLD / PLATINUM — drives pricing)
```

Say: *"I'll make `SeatStatus` and `BookingStatus` enums, not String fields — prevents invalid values at compile time and lets me write an exhaustive switch in the transition guard."*

> **Interviewer:** "Why enums and not a String status field?"
> **Answer:** A String lets any code write `booking.setStatus("CONFRMIED")` — a typo that fails silently at runtime. An enum is a compile-time contract. The switch in my `transition()` method gets a compiler warning if I forget to handle a state.

**Your board at the end of Move 2:**

```
Entities:  Movie · Theatre · Screen · Seat · Show · User · Booking · Payment
Enums:     SeatStatus · BookingStatus · SeatType
Services:  BookingService · SeatInventoryService
```

---

**Move 3 — Draw the enums first. They're fast and anchor everything.**

Enums take 30 seconds each. Draw them in the corner before any entity boxes. They're the vocabulary you'll point to for the rest of the session.

**Explain `SeatStatus` carefully.** This is where HELD earns its place:

*"I'm adding a `HELD` state — not just AVAILABLE and BOOKED — because there's a checkout window between seat selection and payment completion. Without HELD, I can't represent 'someone is mid-checkout for this seat.' The system either over-locks (seat appears taken while someone fills a form) or under-locks (two users both reach the payment page for the same seat, one gets a nasty surprise at the end)."*

*"I'll also add `heldUntil: Instant` on the Seat class — not just a boolean `isHeld`. A timestamp lets me auto-expire the hold: a sweeper or Redis TTL compares `now()` against `heldUntil` and releases the seat automatically."*

> **Interviewer:** "Who cleans up expired holds?"
> **LLD answer:** A `ScheduledExecutorService` background sweeper scanning for `heldUntil < now()` and resetting to AVAILABLE.
> **HLD answer:** Redis TTL — the lock key auto-expires, no sweeper needed, crash-safe by design.

**Explain `BookingStatus` as a path, not a list:**

*"PENDING is the start — booking created, nothing locked. SEAT_LOCKED means the seats are acquired. PAYMENT_PENDING means we've called the gateway and are waiting. CONFIRMED and FAILED are terminal. CANCELLED is reachable from CONFIRMED for refunds."*

*"The reason SEAT_LOCKED and PAYMENT_PENDING are separate: different failure modes. Fail at SEAT_LOCKED → seats still available, no gateway call was made. Fail at PAYMENT_PENDING → seats need releasing AND I may need to reconcile with the gateway. Same state, different recovery path."*

> **Interviewer:** "Do you really need 6 states? Can SEAT_LOCKED and PAYMENT_PENDING merge?"
> **Answer:** You could merge them into PROCESSING. But then when a booking is stuck in PROCESSING, ops can't tell if the lock failed (seats are free, no charge) or the payment failed (seats locked, possible charge). One extra enum constant buys enormous diagnostic value.

**Your board at the end of Move 3:**

```
┌──────────────────────────┐    ┌────────────────────────┐
│ «enum» SeatStatus        │    │ «enum» BookingStatus   │
│──────────────────────────│    │────────────────────────│
│ AVAILABLE                │    │ PENDING                │
│ HELD     ← checkout      │    │ SEAT_LOCKED            │
│ BOOKED                   │    │ PAYMENT_PENDING        │
│ CANCELLED                │    │ CONFIRMED              │
└──────────────────────────┘    │ FAILED                 │
                                │ CANCELLED              │
«enum» SeatType                 └────────────────────────┘
SILVER │ GOLD │ PLATINUM
```

---

**Move 4 — Draw entities smallest to largest. Name what each "knows" and can "do."**

Draw in dependency order — no dependencies first, orchestrator last:

```
Seat → Screen → Theatre
Movie (standalone)
Show (joins Movie + Screen)
User (standalone)
Booking (joins User + Show + Seats)
Payment (child of Booking)
```

For each entity, say TWO things out loud:
1. **What it KNOWS** — fields, but only the non-obvious ones. Skip `name`, `createdAt`. Call out the surprising ones.
2. **What it CAN DO** — methods with real logic only. Skip getters/setters.

**For `Seat`:** *"The non-obvious field is `heldUntil: Instant` — that expiry timestamp. And I'm giving Seat its own `holdFor()`, `release()`, and `book()` methods. State transitions on a Seat are atomic operations — I want them inside the class, not scattered across service methods."*

**For `Booking`:** *"The key method is `transition(nextStatus)` — a guarded state machine. No external code can jump a Booking straight from PENDING to CONFIRMED. The check lives here, not in every caller."*

> **Interviewer:** "What if two threads call `transition()` at the same time?"
> **Answer:** `transition()` is `synchronized` on the Booking instance. Thread B blocks until Thread A exits. Thread B then re-evaluates `isValidTransition()` against the state Thread A left — and if the transition is no longer valid, it throws. Correct by construction.

**Your board at the end of Move 4 (Seat + Booking drawn, others implied):**

```
┌──────────────────────────────────┐
│ Seat                             │
│──────────────────────────────────│
│ - seatId: String                 │
│ - type: SeatType                 │
│ - status: SeatStatus             │
│ - heldByUserId: String           │
│ - heldUntil: Instant   ← KEY    │
│──────────────────────────────────│
│ + holdFor(userId, until): void   │
│ + release(): void                │
│ + isAvailable(): boolean         │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│ Booking                          │
│──────────────────────────────────│
│ - bookingId: String              │
│ - status: BookingStatus          │
│ - seats: List<Seat>              │
│ - totalAmount: BigDecimal        │
│ - idempotencyKey: String         │
│──────────────────────────────────│
│ + transition(next): void  ←guard │
└──────────────────────────────────┘
```

---

**Move 5 — Identify variable behavior. Extract interfaces.**

At every place where you could imagine a different implementation being plugged in, draw an interface.

| Variable behavior | Why it varies | Interface name |
|---|---|---|
| How seats get locked | In-JVM (LLD) vs Redis (HLD) vs no-op (test) | `SeatLockStrategy` |
| How price is computed | Standard vs peak vs early-bird — changes without notice | `PricingStrategy` |
| How payment is processed | Stripe in prod, Razorpay in India, mock in test | `PaymentGateway` |

**`SeatLockStrategy` is the most important interface to explain.** This is your LLD→HLD bridge:

*"I'm extracting seat locking as a `SeatLockStrategy` interface: `lock(seatId, userId, ttlSeconds): boolean` and `release(seatId)`. The in-JVM implementation uses `synchronized` and an in-memory map — correct for a single process. But the moment we run two BookingService instances, each has its own JVM heap. `synchronized` in instance A is invisible to instance B — two users on two different servers can both acquire 'the lock' for the same seat. By making it an interface, I swap in a Redis implementation at HLD without touching BookingService at all."*

> **Interviewer:** "How does the Redis implementation work?"
> **Answer:** `SET seat:{seatId}:lock {userId} NX PX 10000` — a single atomic command. NX means only-if-not-exists. PX 10000 means auto-expire in 10 seconds. First caller gets OK; second caller gets nil. No gap between check and set.

**Your board at the end of Move 5:**

```
        «interface»
┌────────────────────────────┐
│   SeatLockStrategy         │
│────────────────────────────│
│ lock(seatId, userId,       │
│   ttlSeconds): boolean     │
│ release(seatId): void      │
└──────────────┬─────────────┘
               ▼ implements
  InMemorySeatLockStrategy    ← synchronized (single JVM)
  RedisDistributedLockStrategy ← SET NX (multi-instance)

        «interface»                 «interface»
┌─────────────────────┐    ┌──────────────────────────┐
│  PricingStrategy    │    │    PaymentGateway         │
│─────────────────────│    │──────────────────────────│
│ calculate(seat,     │    │ charge(bookingId,         │
│   show): BigDecimal │    │   amount,                │
└─────────────────────┘    │   paymentMethodId)        │
  StandardPricing           │   : PaymentResult         │
  PeakPricing               └──────────────────────────┘
  EarlyBirdPricing
```

---

**Move 6 — Add the orchestrating service last. Its dependency list IS your design.**

`BookingService` is last because it depends on everything else being drawn first.

Say: *"I'm injecting all three strategies via constructor — not instantiating them. This means I can swap any of them without modifying BookingService. That's Dependency Inversion: the high-level module depends on abstractions, not concretions."*

> **Interviewer:** "Who decides which implementation gets injected?"
> **Answer:** A factory or Spring's `@Bean` config — a single place. BookingService is unaware. In tests I inject a mock. In prod I inject the Redis-based lock and the real payment gateway.

**Your board at the end of Move 6 (this is your 75% diagram — everything that matters):**

```
┌───────────────────────────────────────────────┐
│ BookingService                                │
│───────────────────────────────────────────────│
│ - lockStrategy: SeatLockStrategy    ← injected│
│ - pricingStrategy: PricingStrategy  ← injected│
│ - paymentGateway: PaymentGateway    ← injected│
│ - activeBookings: ConcurrentHashMap<String,   │
│                               Booking>        │
│───────────────────────────────────────────────│
│ + initiateBooking(...): Booking               │
│ + confirmBooking(bookingId): void             │
│ + cancelBooking(bookingId): void              │
└───────────────────────────────────────────────┘
```

---

**Move 7 — Name the hot resource. Tie everything together in one sentence.**

End your diagram walk-through with this:

*"The hot resource in this system is `Seat.status`. Two users can both read AVAILABLE and both attempt to assign the seat — that's the check-then-act race. My entire locking strategy — `synchronized` at LLD, Redis SET NX at HLD — is designed to make that check-and-assign atomic."*

This sentence: shows you know exactly where the problem is, justifies every locking decision you made, and naturally pivots the conversation into HLD.

**Visual — what the race looks like:**

```
Thread A (User X)           Thread B (User Y)
    │                           │
    ▼                           ▼
read seat.status = AVAILABLE   read seat.status = AVAILABLE
    │                           │
    ▼                           ▼
seat.holdFor(userX, ...)   seat.holdFor(userY, ...)  ← BOTH WIN → double-booking
    │                           │
    ▼                           ▼
   BOOKED                    BOOKED   ← two bookings, one seat

FIX: holdSeats() must be atomic
  LLD → synchronized (single JVM gate)
  HLD → Redis SET NX (distributed atomic CAS)
```

---

#### ⭐ The 75% Rule — What to Draw if Time is Short

```
Priority 1 — must reach these (10 min):
  SeatStatus enum         (with HELD — explain the checkout window)
  BookingStatus enum      (walk the state path, defend SEAT_LOCKED vs PAYMENT_PENDING)
  Seat                    (with heldUntil + holdFor())
  Booking                 (with transition() guard)
  SeatLockStrategy        (interface + "synchronized breaks across JVMs" bridge)
  BookingService          (constructor dependencies only)

Priority 2 — draw if 5+ min remain:
  Show                    (needed to anchor what a Booking targets)
  PricingStrategy         (mention Strategy pattern, skip implementations)
  PaymentGateway          (mention to set up HLD PCI-DSS boundary)

Priority 3 — verbally mention, never draw:
  Theatre / Screen / Movie  → say "Theatre has Screens; each Screen has a Seat grid"
  Payment                   → say "Payment is a child entity of Booking; I'll model it if asked"
```

> The interviewer cares about the HELD state and the SeatLockStrategy interface — those are the SDE-3 signals. Theatre's fields are irrelevant.

---

### ✅ Complete Diagram — What You're Building Toward

```
┌─────────────────────────────────────┐
│ «enum» SeatStatus                   │
│─────────────────────────────────────│
│ AVAILABLE                           │
│ HELD        ← user picked, not paid │
│ BOOKED      ← payment confirmed     │
│ CANCELLED                           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ «enum» BookingStatus                │
│─────────────────────────────────────│
│ PENDING                             │
│ SEAT_LOCKED                         │
│ PAYMENT_PENDING                     │
│ CONFIRMED                           │
│ FAILED                              │
│ CANCELLED                           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ «enum» SeatType                     │
│─────────────────────────────────────│
│ SILVER  │  GOLD  │  PLATINUM        │
└─────────────────────────────────────┘

┌──────────────────────────────────────────┐
│ Seat                                     │
│──────────────────────────────────────────│
│ - seatId: String                         │
│ - row: char                              │
│ - number: int                            │
│ - type: SeatType                         │
│ - status: SeatStatus                     │
│ - heldByUserId: String                   │
│ - heldUntil: Instant     ← expiry clock  │
│──────────────────────────────────────────│
│ + isAvailable(): boolean                 │
│ + holdFor(userId, until: Instant): void  │
│ + release(): void                        │
│ + book(): void                           │
└──────────────────────────────────────────┘

┌─────────────────────────────────────┐     1
│ Screen                              │─────────────────────────┐
│─────────────────────────────────────│                         │
│ - screenId: String                  │                         │ 1..*
│ - name: String                      │                 ┌───────┴────────┐
│ - seats: List<Seat>                 │                 │   Seat         │
└─────────────────────────────────────┘                 └────────────────┘
         ▲ 1..*
┌─────────────────────────────────────┐
│ Theatre                             │
│─────────────────────────────────────│
│ - theatreId: String                 │
│ - name: String                      │
│ - location: String                  │
│ - screens: List<Screen>             │
└─────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ Show                                                 │
│──────────────────────────────────────────────────────│
│ - showId: String                                     │
│ - movie: Movie                                       │
│ - screen: Screen                                     │
│ - startTime: LocalDateTime                           │
│ - seats: Map<String, Seat>   ← seatId → Seat        │
│ - seatPrices: Map<SeatType, BigDecimal>              │
│──────────────────────────────────────────────────────│
│ + getSeat(seatId): Seat                              │
│ + getAvailableSeats(): List<Seat>                    │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ Booking                                              │
│──────────────────────────────────────────────────────│
│ - bookingId: String                                  │
│ - user: User                                         │
│ - show: Show                                         │
│ - seats: List<Seat>                                  │
│ - status: BookingStatus                              │
│ - totalAmount: BigDecimal                            │
│ - idempotencyKey: String                             │
│ - createdAt: Instant                                 │
│──────────────────────────────────────────────────────│
│ + transition(next: BookingStatus): void  ← guarded  │
│ - isValidTransition(from, to): boolean               │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ Payment                                              │
│──────────────────────────────────────────────────────│
│ - paymentId: String                                  │
│ - booking: Booking                                   │
│ - amount: BigDecimal                                 │
│ - status: PaymentStatus  (PENDING/SUCCESS/FAILED)    │
│ - gatewayRef: String     ← gateway's own txId        │
└──────────────────────────────────────────────────────┘

          «interface»                     «interface»
    ┌────────────────────┐        ┌───────────────────────┐
    │  SeatLockStrategy  │        │   PricingStrategy     │
    │────────────────────│        │───────────────────────│
    │ lock(seatId,       │        │ calculate(seat: Seat,  │
    │   userId,          │        │   show: Show)         │
    │   ttlSeconds)      │        │   : BigDecimal        │
    │   : boolean        │        └─────────┬─────────────┘
    │ release(seatId)    │                  ▼ implements
    └────────┬───────────┘         StandardPricing
             ▼ implements          PeakPricing
    InMemorySeatLockStrategy       EarlyBirdPricing
    RedisDistributedLockStrategy

          «interface»
    ┌───────────────────────────┐
    │     PaymentGateway        │
    │───────────────────────────│
    │ charge(bookingId: String, │
    │   amount: BigDecimal,     │
    │   paymentMethodId: String)│
    │   : PaymentResult         │
    └───────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ BookingService                                           │
│──────────────────────────────────────────────────────────│
│ - lockStrategy: SeatLockStrategy                         │
│ - pricingStrategy: PricingStrategy                       │
│ - paymentGateway: PaymentGateway                         │
│ - activeBookings: ConcurrentHashMap<String, Booking>     │
│──────────────────────────────────────────────────────────│
│ + initiateBooking(userId, showId, seatIds,               │
│       paymentMethodId, idempotencyKey): Booking          │
│ + confirmBooking(bookingId): void                        │
│ + cancelBooking(bookingId): void                         │
└──────────────────────────────────────────────────────────┘

KEY INVARIANT:
  No booking transitions to CONFIRMED without two things:
  (1) SeatLockStrategy.lock() returned true for ALL requested seats.
  (2) PaymentGateway.charge() returned SUCCESS.
  Both must hold — failing either leaves the booking FAILED and locks released.
```

---

## 🧭 LLD — Design Decisions

| Decision | Why this | What I rejected and why |
|---|---|---|
| **`SeatLockStrategy` interface (not a concrete lock)** | Lets me swap in-JVM `synchronized` at LLD scale for Redis SET NX at HLD scale — same BookingService code, different strategy injected. Open to extension, closed to modification. | Hardcoded `synchronized` in BookingService — works in a single JVM, but when we add a second BookingService instance (HLD §9), both have separate JVM heaps; the lock means nothing across processes. |
| **`HELD` state on `Seat` with `heldUntil: Instant`** | Models the seat-hold window explicitly. User picks seat → `HELD` for 10 minutes. If they abandon checkout, the hold expires automatically (background sweeper at LLD, Redis TTL at HLD). | Binary AVAILABLE/BOOKED — no way to represent "user is mid-checkout." Without HELD, two users can both be in checkout for the same seat; only one will succeed at payment, but both wasted time filling a form. |
| **`BookingStatus` as a state machine with guarded transitions** | Prevents illegal jumps (PENDING → CONFIRMED without going through PAYMENT_PENDING). The `transition()` method throws on invalid input — the bug is caught at code, not in a prod hotfix. | Plain `status` field with setter — nothing prevents `booking.setStatus(CONFIRMED)` anywhere in the codebase. Testing every call site is impractical. |
| **`PricingStrategy` interface (Strategy pattern)** | Peak vs. standard vs. early-bird pricing are separate algorithms. Each is independently testable and deployable. Adding a new tier = new class, no changes to BookingService (OCP). | Switch-case in BookingService — every new pricing tier requires modifying the service; violates OCP; the service becomes a pricing encyclopedia over time. |
| **Observer for post-booking events** | Email, analytics, and inventory update don't need to block the booking response. At LLD, they're fired async (Observers get notified). At HLD, they're Kafka consumers. The booking API stays fast. | Synchronous call chain inside `confirmBooking()` — if EmailService times out, the whole booking API times out. Wrong coupling of unrelated SLAs. |
| **`SeatInventoryService` separate from `BookingService`** | SRP: seat availability reads are high-QPS and cacheable; booking writes are low-QPS and ACID. Mixing them couples a 100:1 read:write asymmetry into one class, making it impossible to cache or scale reads independently. | One monolithic `BookingService` — read and write concerns merge; can't add a Redis cache layer without the service knowing about it; harder to test. |

---

## 🔌 LLD — Key Interfaces

| Interface | Contract |
|---|---|
| `SeatLockStrategy` | Plug-in seat-holding algorithm. In-JVM impl: `synchronized` check-then-assign. Distributed impl: Redis SET NX. Same interface — BookingService never changes. |
| `PricingStrategy` | Plug-in fee algorithm per seat + show. Implementations: `StandardPricing`, `PeakPricing` (weekends/evenings), `EarlyBirdPricing` (7+ days ahead). |
| `PaymentGateway` | Abstraction over external payment provider (Stripe, Razorpay). Isolates BookingService from vendor SDKs. |

```java
/**
 * Plug-in seat-locking algorithm.
 * InMemorySeatLockStrategy: synchronized check-then-assign (single JVM).
 * RedisDistributedLockStrategy: SET NX + PX TTL (multi-instance HLD).
 * BookingService depends on this interface — never on a concrete impl.
 */
public interface SeatLockStrategy {
    /** Returns true if lock acquired; false if seat already held. */
    boolean lock(String seatId, String userId, int ttlSeconds);
    void release(String seatId);
}

/**
 * Pricing algorithm for a seat within a specific show.
 * Isolated from BookingService — pricing rules change independently of booking logic.
 */
public interface PricingStrategy {
    BigDecimal calculate(Seat seat, Show show);
}

/**
 * Abstraction over external payment gateway (Stripe, Razorpay, etc.).
 * Lets BookingService stay gateway-agnostic and testable with a mock.
 */
public interface PaymentGateway {
    PaymentResult charge(String bookingId, BigDecimal amount, String paymentMethodId);
}
```

---

## ⚙️ LLD — Code to Write

> **Archetype:** Hot-resource race. Write:
> 1. The synchronized seat-hold method (check-then-assign guard)
> 2. The booking state machine transition guard

### Method 1 — `holdSeats()` (in-JVM `InMemorySeatLockStrategy`)

**Steps in plain English:**

1. **Check all seats before touching any** — validate all requested seatIds are AVAILABLE. If even one is not, fail the whole call atomically (no partial holds).
2. **Acquire the method-level lock** — `synchronized` makes steps 1 and 2 atomic within this JVM. No other thread can slip in between the check and the assign.
3. **Transition each seat to HELD** — set `status = HELD`, set `heldUntil = now + ttlSeconds`, set `heldByUserId`.

```java
// Guards against: two threads seeing the same seat as AVAILABLE and both assigning it.
// synchronized makes the check-then-assign pair atomic within a single JVM.
// WHY this breaks at scale: synchronized only means "one thread in this JVM."
// A second BookingService instance has its own heap — the lock is invisible to it.
// Solution at HLD scale: replace with RedisDistributedLockStrategy (SET NX) —
// same interface, distributed atomicity.
public synchronized boolean holdSeats(
        String showId,
        List<String> seatIds,
        String userId,
        int ttlSeconds) {

    Show show = showRepository.findById(showId);

    // Step 1 — check all seats are AVAILABLE before touching any (all-or-nothing)
    for (String seatId : seatIds) {
        Seat seat = show.getSeat(seatId);
        if (!seat.isAvailable()) {
            return false;
        }
    }

    // Step 2 — all available; transition each to HELD with expiry timestamp
    Instant heldUntil = Instant.now().plusSeconds(ttlSeconds);
    for (String seatId : seatIds) {
        Seat seat = show.getSeat(seatId);
        seat.holdFor(userId, heldUntil);
    }
    return true;
}
```

### Method 2 — `Booking.transition()` (state machine guard)

**Steps in plain English:**

1. **Validate the transition** — is `PENDING → SEAT_LOCKED` a legal edge in the state graph? If not, throw immediately.
2. **Apply the transition** — only now update `this.status`.
3. **Synchronized on the booking instance** — prevents two threads simultaneously driving the same booking to conflicting terminal states.

```java
// Guards against: illegal state jumps (CONFIRMED → PENDING) and
// two threads racing to simultaneously confirm and cancel the same booking.
public synchronized void transition(BookingStatus nextStatus) {
    if (!isValidTransition(this.status, nextStatus)) {
        throw new IllegalStateTransitionException(
            "Cannot transition from " + this.status + " to " + nextStatus
        );
    }
    this.status = nextStatus;
}

private boolean isValidTransition(BookingStatus from, BookingStatus to) {
    switch (from) {
        case PENDING:
            return to == BookingStatus.SEAT_LOCKED
                || to == BookingStatus.FAILED;
        case SEAT_LOCKED:
            return to == BookingStatus.PAYMENT_PENDING
                || to == BookingStatus.FAILED;
        case PAYMENT_PENDING:
            return to == BookingStatus.CONFIRMED
                || to == BookingStatus.FAILED;
        case CONFIRMED:
            return to == BookingStatus.CANCELLED;
        case FAILED:
        case CANCELLED:
            return false;
        default:
            return false;
    }
}
```

---

## 🔁 LLD — Concurrency

| Shared field | What breaks without a lock | Fix |
|---|---|---|
| `Seat.status` + `Seat.heldUntil` | Two threads both see `AVAILABLE`, both call `holdFor()` → double-booking | `synchronized` on `holdSeats()` (in-JVM) or Redis SET NX (distributed, §9) |
| `Booking.status` | Two threads simultaneously drive booking to `CONFIRMED` and `CANCELLED` → inconsistent terminal state | `synchronized` on `transition()` (per-booking instance lock) |
| `activeBookings: ConcurrentHashMap` in `BookingService` | Concurrent `put`/`remove` on `HashMap` corrupts internal bucket array | `ConcurrentHashMap<String, Booking>` — CAS on empty buckets, segment-level lock on collisions; no full-table lock |

```java
// The critical section: check-then-assign must be one atomic operation.
// synchronized on holdSeats() serializes all seat-hold attempts through one gate.
// Correct for single-JVM; bottleneck at scale (see Trade-off below).
public synchronized boolean holdSeats(String showId, List<String> seatIds,
                                      String userId, int ttlSeconds) {
    // ... (full body in §6)
}
```

**Trade-off:** `synchronized` on `holdSeats()` means 1000 concurrent bookings queue behind a single lock — throughput = 1 / avg_hold_duration. At scale, replace with Redis SET NX per seatId. Now 1000 bookings for 1000 different seats execute in parallel; contention only occurs for the same seat, which is exactly the right granularity.

---

## 🧨 Java Depth Probes

| What you say in design | What they immediately ask | Your answer |
|---|---|---|
| `"synchronized on holdSeats()"` | "Why not a per-seat lock?" | Per-seat lock risks deadlock if a user books multiple seats: Thread A holds seatA, waits for seatB; Thread B holds seatB, waits for seatA — classic deadlock. A method-level lock is simpler and safe at LLD scale. At HLD, I use Redis SET NX per seat but acquire in a deterministic sorted order to avoid the deadlock there too. |
| `"HELD state with heldUntil Instant"` | "Who releases the hold when it expires?" | At LLD, I need a `ScheduledExecutorService` running a background sweeper every N seconds, scanning for `heldUntil < now` and resetting those seats to AVAILABLE. At HLD, I use Redis TTL — the lock auto-expires, no sweeper needed, no missed releases on crash. This is actually a key reason to move to Redis: crash-safe expiry is built in. |
| `"Redis SET NX for the distributed lock"` | "What if the Redis node crashes mid-lock?" | `SET NX` is always issued with a `PX` TTL (e.g., `PX 10000`). If the Redis node crashes, the lock was never durably stored — effectively never acquired. If it crashes after the SET, the TTL ensures the lock auto-expires in ≤10s. For higher availability, I'd use Redlock: write the lock to 3+ independent Redis nodes; consider it acquired only if a majority (≥2) succeed. |
| `"ConcurrentHashMap for activeBookings"` | "Why not a regular HashMap?" | `HashMap` is not thread-safe — concurrent `put` calls can corrupt its internal bucket array, especially during resize (Java 7 had an infinite loop bug from this). `ConcurrentHashMap` uses CAS operations for empty-bucket writes and striped locks per bucket chain — no full-table lock, far better throughput under concurrent access. |
| `"booking.transition(PAYMENT_PENDING)"` | "What if two threads call transition() simultaneously?" | `transition()` is `synchronized` on the `Booking` instance. Only one thread can execute it at a time. Thread B blocks until Thread A exits. Then Thread B's `isValidTransition()` re-checks the now-updated status from Thread A — and if the state has already moved forward, Thread B's transition is rejected and an exception is thrown. |

---

## 🌐 HLD — How to Build This Diagram in the Interview — 4 Phases

> **Why this guide exists:** You should never walk up to the whiteboard and draw the final diagram cold. The interviewer is watching your reasoning process, not your diagram. The 4-phase approach shows SDE-3 thinking: size the problem → draw the minimum → break it yourself → upgrade it with justification.

---

### 🏗️ Phase 1 — Numbers First (≈2 min)

> **Say out loud:** *"Before I draw anything, let me size the system so I know which parts will be under load."*

Anchor your numbers on what the interviewer gives you, then derive everything else with visible arithmetic.

```
Assume: 5M DAU (India-scale, BookMyShow-style)

─── Seat Availability Reads (the hot path) ───────────────────────
  Browsing: every DAU opens a seat map; average 5 loads/session
    → 5M × 5 = 25M seat-availability reads / day
  Hot release window (blockbuster Friday 8 PM, 30-min rush):
    20% of DAU tries in 30 min = 1M users / 1800s ≈ 556 users/sec
    each triggers 3-5 seat-map loads
    → peak seat-availability reads ≈ 1,700–2,800 req/sec

  KEY OBSERVATION: At peak, 1,700+ requests per second are asking
  for the SAME data — the seat map of one popular show.

─── Booking Writes ───────────────────────────────────────────────
  2% of DAU book tickets: 5M × 0.02 = 100K bookings / day
  Peak: 3× on weekend releases, spread over a 4-hr window
    → peak writes = 100K × 3 / (4 × 3,600s) ≈ 21 writes/sec

─── Storage ──────────────────────────────────────────────────────
  Booking row ≈ 2 KB
    → 100K/day × 365 × 2 KB = ~73 GB/year → MySQL, no sharding
  Active seat data: 1,000 concurrent shows × 500 seats × 1 KB
    → ~500 MB of live seat state → fits in Redis entirely
```

---

### 🏗️ Phase 2 — Skeleton: The Simplest System That Could Work (≈2 min)

> **Say out loud:** *"Let me draw the minimum system first — just the boxes that must exist. No Redis, no Kafka yet."*

Only services that are logically unavoidable go here. No optimizations yet.

```
── Skeleton: Simplest System That Could Work ──────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Mobile App · Web Browser             │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS
   ┌─────────────────────▼──────────────────────────┐
   │  API Gateway  (auth · routing)                 │
   └──────┬──────────────────────────────┬──────────┘
          │                              │
   ┌──────▼────────────────┐   ┌─────────▼──────────────────────────┐
   │  SeatInventoryService │   │  BookingService                    │
   │  BookingQueryService  │   │       └──▶ PaymentService ──▶ Stripe│
   └──────┬────────────────┘   │  NotificationService ──▶ Email     │
          │                    └─────────────────────┬──────────────┘
          │                                          │
   ┌──────▼──────────────────────────────────────────▼────────────┐
   │  MySQL  (shows · seats · bookings · payments — ACID)         │
   └──────────────────────────────────────────────────────────────┘

BREAKING POINT — walk this skeleton against the Phase 1 numbers:
  (a) SeatInventoryService → MySQL at 1,700+ reads/sec, all for the
      same seat map. MySQL runs the same query 1,700 times/sec with
      zero reuse. Also steals connection pool from ACID booking writes.
  (b) Two BookingService instances: synchronized is per-JVM.
      Instance A's lock is invisible to Instance B — double booking.
  (c) NotificationService on the critical path: email retries for
      minutes. If it's slow, the booking API returns 500. Wrong coupling.
  (d) Service crash after payment but before CONFIRMED → phantom charge
      on retry. No idempotency guard.

══════════════════════════════════════════════════════════════════
```

Say this to the interviewer: *"This works in dev. Now let me break it."*

---

### 🏗️ Phase 3 — Upgrade It: One Fix per Pain Point (≈2 min)

> **The 4 BREAKING POINTs from the skeleton diagram each get one fix. Say the number that forced each upgrade.**

> **Say out loud:** *"Now I'll add components — one per pain point — and justify each with the number that forced it."*

```
PAIN POINT 1 → Add Redis Cache (5s TTL, cache-aside pattern)
  Why Redis: single-key lookup O(1). A 5-second cache means the
  seat-map query runs once per 5 seconds per show, regardless of how
  many users are browsing. 1,700 reads/sec becomes ~1 MySQL read
  per 5s per popular show. Also isolates read traffic from the
  ACID write path — MySQL's connection pool stays reserved for bookings.

PAIN POINT 2 → Add Redis SET NX (distributed atomic lock)
  Redis SET seat:{id}:lock {userId} NX PX 10000
    NX  = set only if Not eXists (atomic)
    PX  = TTL in milliseconds (auto-expiry on BookingService crash)
  Why this works: Redis is single-threaded for command execution.
  SET NX across 10 BookingService instances is still atomic.
  Only one caller wins; all others get "lock not acquired" immediately.
  No DB connection held open. Lock auto-expires — no cleanup thread.

PAIN POINT 3 → Add Kafka (topic: booking-events)
  BookingService emits a booking.confirmed event after MySQL COMMIT.
  NotificationService and AnalyticsService become Kafka consumers.
  Booking API response time is now independent of email delivery.
  DLQ (dead-letter queue) on each consumer handles failures without
  affecting the booking. Email can retry for 3 days — booking doesn't care.

PAIN POINT 4 → Add idempotency key on POST /bookings
  Client generates UUID on first attempt; resends same UUID on retry.
  BookingService stores idempotencyKey → bookingId in Redis before
  calling PaymentService. On retry: key found → return existing booking,
  skip payment. Idempotency window: 24 hours (TTL in Redis).
```

---

### ✅ Production Diagram — What You're Building Toward

```
── Production: All 4 Upgrades Applied ────────────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Mobile App · Web Browser             │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS
   ┌─────────────────────▼────────────────────────────────────────┐
   │  API Gateway  (JWT · rate-limit 100 req/s · TLS · routing)  │
   └──────┬────────────────────────────────────────┬─────────────┘
          │                                        │
   ┌──────▼────────────────┐   ┌────────────────────▼────────────────────┐
   │  SeatInventoryService │   │  BookingService                         │
   │  (read-heavy)         │   │  state machine · ACID writes            │
   │                       │   │                                         │
   │  BookingQueryService  │   │  └──▶ PaymentService  (PCI zone)        │
   │  (GET /bookings/{id}) │   │         └──▶ Stripe  (10s timeout)      │
   └──────┬────────────────┘   └────────────────────┬────────────────────┘
          │ GET (cache-aside)                        │ SET seat:{id}:lock
          │ GET (booking status)                     │   {uid} NX PX 10000
          ▼                                          ▼ ACID write · idempotency
   ┌──────────────────────────────────────────────────────────────────────┐
   │  Redis                                                               │
   │  seat:{showId}       → availability map  · EX 5     ← SeatInvSvc   │
   │  booking:{id}:status → booking status    · EX 30    ← QuerySvc      │
   │  seat:{id}:lock      → userId            · PX 10000 ← BookingSvc   │
   │  idem:{key}          → bookingId         · EX 86400 ← BookingSvc   │
   └──────────────────────────────────┬───────────────────────────────────┘
                                      │ cache miss / ACID write
   ┌──────────────────────────────────▼───────────────────────────────────┐
   │  MySQL  (ACID)                                                       │
   │  shows · screens · seats     ← SeatInventoryService                 │
   │  bookings · payments         ← BookingService  (single ACID tx)     │
   └──────────────────────────────────┬───────────────────────────────────┘
                                      │ emit booking.confirmed
   ┌──────────────────────────────────▼───────────────────────────────────┐
   │  Kafka  (topic: booking-events, key = booking_id)                    │
   │  ├──▶ NotificationService   email / SMS · retry via DLQ             │
   │  └──▶ AnalyticsService      funnel + revenue tracking               │
   └──────────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
  Redis is the concurrency fence (SET NX = atomic seat claim across JVMs).
  MySQL is the commit fence (ACID transaction = durable booking record).
  No booking CONFIRMED without both gates passed in sequence.
  Kafka decouples all downstream — email latency never blocks booking.
══════════════════════════════════════════════════════════════════
```

---

## 🏛️ HLD — Decisions

| Component | Why chosen | What I rejected and why |
|---|---|---|
| **Redis for seat lock (SET NX + PX TTL)** | Atomic at the cache layer: no two callers can acquire the same key simultaneously. TTL auto-expires the lock on crash — no cleanup thread, no stuck locks. O(1). | `SELECT FOR UPDATE` DB row lock — holds an open DB connection for the entire lock duration; at 10K concurrent bookings, you'd exhaust the connection pool; and it doesn't auto-release on application crash. |
| **Redis Cache for seat availability** | Seat browsing is ~100× more frequent than booking. A 5-second stale window is acceptable (user is told at lock-time if their selection is taken). Serving from cache keeps the DB healthy for the few writes. | DB read for every seat-availability call — seat availability queries would be 100× more frequent than writes; the DB would be the bottleneck on every Friday-night release. |
| **MySQL for bookings + payments** | Strong consistency required — double-booking means a real person is physically displaced. ACID transactions give us rollback on partial failure. | NoSQL — eventual consistency is not acceptable for seat assignment. A 100ms replication lag could let two bookings commit for the same seat. |
| **PaymentService as a separate microservice** | PCI-DSS compliance boundary: payment-sensitive data (card tokens, gateway credentials) must be isolated from general application services. Allows independent deployment and scaling. | Payment logic inside BookingService — mixes ACID booking state with PCI-sensitive payment processing; can't create an isolated compliance boundary for audit. |
| **Kafka for post-booking fan-out** | Email, SMS, and analytics must not block the booking response. Kafka decouples their latency (email might retry 3× over 5 minutes) from the booking SLA (2s). DLQ handles delivery failures without failing the booking. | Synchronous REST chain: BookingService → EmailService → AnalyticsService — if EmailService is down, the booking API returns 500; wrong coupling of unrelated concerns. |

---

## 📡 HLD — API Design

```http
GET /v1/shows/{showId}/seats
Authorization: Bearer <token>

Response: 200 OK
{
  "showId": "show-123",
  "seats": [
    { "seatId": "A1", "type": "GOLD",   "status": "AVAILABLE", "price": 450 },
    { "seatId": "A2", "type": "GOLD",   "status": "HELD",      "price": 450 },
    { "seatId": "B1", "type": "SILVER", "status": "BOOKED",    "price": 200 }
  ]
}
// Cache-first: Redis (5s TTL), fall through to MySQL on miss.
// Stale "AVAILABLE" resolved at lock time — user shown a clear 409 if beaten.
```

```http
POST /v1/bookings
X-Idempotency-Key: <client-generated-uuid>     ← client generates before sending
Authorization: Bearer <token>
Content-Type: application/json

Request:
{
  "showId":          "show-123",
  "seatIds":         ["A1", "A3"],
  "paymentMethodId": "pm-stripe-tok-xxx"
}

Response: 202 Accepted
{
  "bookingId": "bkng-456",
  "status":    "PENDING",
  "total":     900,
  "pollUrl":   "/v1/bookings/bkng-456"
}

// Idempotency: server writes X-Idempotency-Key → bookingId to Redis (24h TTL)
// BEFORE calling the payment gateway.
// Second call with same key → return the stored response; no second charge.
// Client polls pollUrl every 2s until status is terminal (CONFIRMED or FAILED).
```

```http
GET /v1/bookings/{bookingId}
Authorization: Bearer <token>

Response: 200 OK
{
  "bookingId":          "bkng-456",
  "status":             "CONFIRMED",
  "confirmationNumber": "BMS-2026-78901",
  "seats":              ["A1", "A3"],
  "amount":             900
}
// BookingQueryService checks Redis cache first (30s TTL).
// On cache miss or status = PENDING, falls through to MySQL for latest state.
```

---

## 🛤️ HLD — Happy + Unhappy Paths

### Happy path

1. User browses shows → `GET /v1/shows` — served from Redis (200ms SLA).
2. User selects a show → `GET /v1/shows/{showId}/seats` — Redis cache (5s TTL); MySQL fallback on miss.
3. User selects seats A1, A3 → UI optimistically highlights them as "selecting."
4. User clicks "Book" → `POST /v1/bookings` — client sends idempotency key.
5. BookingService: `SET seat:A1:lock userId NX PX 10000` — success. `SET seat:A3:lock userId NX PX 10000` — success. Both locked.
6. BookingService: `INSERT booking (status=PENDING)` + `UPDATE seat status=HELD` in a single MySQL transaction. Booking transitions: `PENDING → SEAT_LOCKED → PAYMENT_PENDING`.
7. PaymentService.charge() called synchronously (10s hard timeout) → gateway returns SUCCESS.
8. BookingService: `UPDATE booking status=CONFIRMED`, `UPDATE seat status=BOOKED` (single MySQL tx). Redis locks deleted (`DEL seat:A1:lock`, `DEL seat:A3:lock`).
9. Kafka event: `booking.confirmed` → NotificationService (sends email) + AnalyticsService.
10. Response: `200 { bookingId, status: CONFIRMED, confirmationNumber }`.

---

### Unhappy path — seat race (two users, same seat)

```
Users X and Y both click seat A1 within milliseconds of each other.
Both POST /v1/bookings reach BookingService concurrently.

Redis SET NX — atomic CAS operation:
  Thread X: SET seat:A1:lock userX NX PX 10000 → "OK"   ← X wins
  Thread Y: SET seat:A1:lock userY NX PX 10000 → nil    ← Y loses

Y's BookingService: seat lock not acquired →
  Returns 409 Conflict:
  { "error": "SEAT_UNAVAILABLE", "message": "Seat A1 was just taken. Pick another." }

Y's UI: refreshes seat map, highlights remaining available seats.
X continues normally through the happy path.
```

### Unhappy path — payment gateway timeout

```
PaymentService.charge() call exceeds the 10s hard timeout.

BookingService:
  booking.transition(FAILED)
  MySQL UPDATE booking status=FAILED (committed)
  Redis DEL seat:A1:lock, seat:A3:lock
  MySQL UPDATE seat status=AVAILABLE

Idempotency key stored in Redis as FAILED.

User retries → same idempotency key → Redis returns stored FAILED response.
User must start a new booking (new idempotency key) — no double-charge risk.
```

### Unhappy path — service crash mid-booking

```
BookingService crashes after Redis SET NX but before MySQL INSERT.

Redis TTL (PX 10000): seat locks auto-expire in ≤10s.
No booking row was written → DB is clean — no orphaned PENDING booking.

User retries → idempotency key was never committed to Redis (crash was pre-commit)
→ key not found → new booking initiated from scratch safely.

If crash was AFTER Redis idempotency key write but BEFORE MySQL commit:
→ idempotency key exists in Redis but no booking row in DB.
→ Server detects inconsistency on retry; re-runs the booking flow idempotently.
```

### Unhappy path — user abandons checkout (hold expiry)

```
User selects seats, sits on the payment page for 12 minutes, then closes the tab.

Redis TTL expires after 10 minutes (or whichever hold TTL was set).
Seats auto-released: status → AVAILABLE again.
No booking row written → no cleanup required.
Other users can now book those seats normally.
```

---

## 🔧 HLD — Fault Tolerance

| External call | What breaks | What you add |
|---|---|---|
| Payment gateway | Timeout → booking stuck in PAYMENT_PENDING; user doesn't know if charged | 10s hard timeout on PaymentService; booking → FAILED on timeout; idempotency key (stored pre-call) lets client retry without risk of double-charge |
| Email / SMS service | Down → user never gets confirmation | Kafka DLQ — failed NotificationService messages retry 3×, then go to dead-letter queue; email is not on the booking critical path; booking already CONFIRMED |
| Redis seat lock node | Crash between SET NX and MySQL write → seat never auto-released | `PX 10000` TTL on every SET NX — lock auto-expires in ≤10s regardless; for HA use Redlock (majority quorum across 3+ Redis nodes) |
| MySQL write (booking tx) | Partial write → booking row without corresponding seat status update | DB transaction wraps booking INSERT + seat UPDATE as one unit; rollback on any failure; seats remain AVAILABLE; booking row never half-written |
| Redis availability cache | Node crash → all seat availability reads miss → DB suddenly gets 100× load | Cache-aside: miss falls through to DB; circuit breaker triggers if miss rate spikes above threshold; 5s TTL means data auto-refreshes without cache warm-up time |

> *"Once the happy path works, I'd ask myself: what happens if THIS call takes 10× longer or fails permanently? That question drives every row in this table."*

---

## 🔬 Interview Q&As

### Q: "Walk me through the happy path end-to-end."

> User browses shows (Redis cache, 200ms SLA). Selects seats. POSTs to BookingService
> with idempotency key. BookingService acquires Redis locks (SET NX — atomic). INSERTs
> booking row (PENDING). Calls PaymentService (sync, 10s timeout). On success: booking →
> CONFIRMED, locks released, Kafka event → email + analytics. Response: bookingId +
> confirmation number. Total round-trip target: ≤2s.

---

### Q: "How do you prevent two users from booking the same seat?"

> Redis SET NX is atomic — the first caller wins, the second gets `nil`. The key is
> `seat:{seatId}:lock`, value is the userId, TTL is 10s. This is atomicity at the cache
> layer, not the DB layer. DB row lock (`SELECT FOR UPDATE`) would work but holds an open
> DB connection for the entire lock duration — expensive at 10K concurrent users.

---

### Q (Tier 2): "What if the Redis node crashes between SET NX and the MySQL write?"

> The `PX 10000` TTL means the lock auto-expires in at most 10 seconds. No MySQL write
> happened — the DB is clean. The client retries — idempotency key was never committed
> (crash was pre-commit), so the key is not found and a fresh booking is initiated safely.
> For production, Redlock (quorum across 3+ Redis nodes) means a single node crash doesn't
> even affect lock availability — the other nodes still hold the lock.

---

### Q (Tier 2): "Your synchronized holdSeats() — what's the bottleneck at 10K concurrent bookings?"

> Correct — `synchronized` on `holdSeats()` serializes all seat-hold attempts through one
> gate per JVM instance. At 10K concurrent requests, throughput is 1/avg_hold_duration.
> The fix: Redis SET NX per seatId. Now 10K bookings for 10K different seats execute in
> parallel — contention only occurs when two users want the exact same seat, which is the
> correct granularity. The `SeatLockStrategy` interface is specifically designed for this
> swap — BookingService code doesn't change.

---

### Q (Tier 2): "How does your idempotency key survive a crash where the server charged the user but the client never got the response?"

> This is the "phantom charge" problem. My approach: write the idempotency key → bookingId
> mapping to Redis BEFORE calling the payment gateway. If the gateway call succeeds but
> BookingService crashes before sending the response, the client retries with the same
> idempotency key. Redis returns the stored bookingId. The server checks MySQL: if the
> booking row is CONFIRMED (DB write succeeded), it returns CONFIRMED. If PENDING (DB crash
> mid-write), it detects the inconsistency and re-queries the gateway by the booking's
> own gatewayRef to determine the actual charge outcome — and reconciles accordingly.

---

## 🧾 TL;DR — 30-Second Pitch

> "I'll start with the core entities — Show, Screen, Seat, Booking, Payment — and the
> central race condition: multiple users clicking the same seat simultaneously. My key LLD
> decision is the `SeatLockStrategy` interface: the in-JVM implementation uses
> `synchronized` with an explicit `HELD` state and `heldUntil` expiry; at HLD scale this
> swaps for Redis SET NX with a TTL — same interface, distributed atomicity, crash-safe
> expiry built in. The booking is a state machine: PENDING → SEAT_LOCKED →
> PAYMENT_PENDING → CONFIRMED or FAILED — no state can be skipped, no terminal state is
> reversible. The design is synchronous to payment but async for everything downstream
> (email, analytics) via Kafka. Before I go further, my most important question: does
> payment go through an external gateway, and what is its timeout SLA? If the p99 is
> above 3 seconds, I may want an async polling model instead of a synchronous call chain."

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | Note created. Follows `notes-standards-interview-problems.md`. Archetype: hot-resource race. Both LLD (SeatLockStrategy interface, HELD state, state machine transitions) and HLD (Redis SET NX, MySQL ACID, Kafka fan-out, PaymentService PCI boundary) covered. |
