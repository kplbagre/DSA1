# UML for LLD Interviews

> **Read this before any Problem note.** Class diagrams and sequence diagrams appear in every problem note in this folder. You don't need full UML mastery — you need enough to draw quickly on a whiteboard and speak to it confidently.
> **Part of LLD Foundations.** Index + reading order: **../README.md**

---

## 🎯 Why This Matters

In an LLD interview, the interviewer expects you to:
1. **Sketch a class diagram** before coding — to show you think in abstractions
2. **Narrate a sequence diagram** to explain how objects interact at runtime

If you can't read or draw these, you'll code correctly but look junior. The vocabulary ("composition", "implements", "lifeline") is what separates SDE-2 from SDE-3 answers.

---

## 🧠 Part 1 — Class Diagrams

A class diagram shows **what classes exist** and **how they relate to each other**. It's a static picture — no time, no runtime state.

### The Box

Every class/interface is a rectangle with up to three sections:

```
┌───────────────────────────┐
│      <<interface>>        │  ← stereotype label (tells you it's an interface)
│       ParkingSpot         │  ← class/interface name
├───────────────────────────┤
│ - parkedVehicle: Vehicle  │  ← fields (-=private, +=public, #=protected)
├───────────────────────────┤
│ + isAvailable(): boolean  │  ← methods
│ + assignVehicle(v)        │
└───────────────────────────┘
```

**In interview sketches:** You rarely draw all three sections. Just write the name and 2-3 most important methods. That's enough.

---

### The 5 Relationships (learn all 5)

> **Canonical treatment of relationships (composition vs aggregation, IS-A, reference-by-ID) is in `04-relationships.md`. This section covers only how to DRAW them in UML.**

#### 1. Implements (Realization)
**"This class fulfils this interface's contract."**
```
CompactSpot - - - - - - - ▷ ParkingSpot
```
*Dashed line + open arrowhead. Points toward the interface.*

In speech: *"CompactSpot implements ParkingSpot."*

---

#### 2. Extends (Inheritance)
**"This class IS-A more specific version of that class."**
```
ElectricVehicle ─────────▷ Vehicle
```
*Solid line + open arrowhead. Points toward the parent.*

In speech: *"ElectricVehicle extends Vehicle — it's a kind of vehicle."*

---

#### 3. Association (Has-A, independent lifecycle)
**"This class holds a reference to that class. They can exist independently."**
```
ParkingLot ─────────────── FeeStrategy
```
*Plain solid line. The `FeeStrategy` object can be used by many lots — it's not owned.*

In speech: *"ParkingLot has a FeeStrategy — but the strategy can exist independently and be reused."*

---

#### 4. Aggregation (Has-A, independent lifecycle, whole-part)
**"This is a 'collection of' relationship but the parts survive without the whole."**
```
ParkingLot ◇──────────── ParkingFloor
```
*Empty diamond on the owner side.*

In speech: *"ParkingLot aggregates ParkingFloors."*

---

#### 5. Composition (Has-A, dependent lifecycle — strongest)
**"The part CANNOT exist without the whole. If the whole is destroyed, parts are too."**
```
ParkingLot ◆──────────── ParkingFloor
```
*Filled diamond on the owner side.*

In speech: *"ParkingFloor is composed into ParkingLot — floors make no sense without the lot."*

**Aggregation vs Composition cheat:** Ask — "if I delete the parent, do the children also die?" Yes → Composition. No → Aggregation. A `ParkingFloor` without a `ParkingLot` is meaningless → Composition.

---

### Multiplicity — how many?

Written at the end of a line:

| Notation | Meaning |
|---|---|
| `1` | exactly one |
| `*` | zero or more |
| `1..*` | one or more |
| `0..1` | zero or one (optional) |

```
ParkingLot 1 ◆──────────── 1..* ParkingFloor
```
*One lot composes one-or-more floors.*

---

### 🎨 Visual — Parking Lot Class Diagram (full example)

```
┌───────────────────────────────────┐
│           ParkingLot              │
├───────────────────────────────────┤
│ - floors: List<ParkingFloor>      │
│ - feeStrategy: FeeStrategy        │
│ - activeTickets: Map<>            │
├───────────────────────────────────┤
│ + parkVehicle(v, t): Ticket       │
│ + unparkVehicle(id): Ticket       │
└──────┬──────────────────┬─────────┘
       │ 1..*             │ uses
       ◆ (composition)    │ (association)
       │                  ▼
┌──────┴────────┐   ┌─────────────────────┐
│ ParkingFloor  │   │   <<interface>>      │
├───────────────┤   │   FeeStrategy        │
│ - floorNumber │   ├─────────────────────┤
│ - spots: List │   │ + calculate(v, min)  │
├───────────────┤   └──────────┬──────────┘
│+ findSpot()   │              │ implements (- - - ▷)
└───────┬───────┘     ┌────────┴─────────┐
        │ 1..*        │                  │
        ◆ (composition)       HourlyFee  FlatRateFee
        │             │       Strategy   Strategy
┌───────┴──────────┐
│  <<interface>>   │
│  ParkingSpot     │
├──────────────────┤
│ + isAvailable()  │
│ + assignVehicle()│
└──────────────────┘
     ▲ (implements - - -)
     │
┌────┴────────────────────────────┐
│  CompactSpot  LargeSpot  EVSpot │  (each implements ParkingSpot)
└─────────────────────────────────┘

KEY INVARIANT:
   Composition (◆) means delete the ParkingLot → all floors and spots go too.
   Association (plain line) means the FeeStrategy can outlive any particular lot.
```

---

## 🧠 Part 2 — Sequence Diagrams

A sequence diagram shows **what happens at runtime** — which object calls which method, in what order. It's a dynamic picture — time flows **top to bottom**.

### The Elements

```
  Client          ParkingLot       ParkingFloor
    │                 │                 │        ← participants (objects) at top
    │                 │                 │        ← lifelines (vertical dashed lines)
    │ parkVehicle()   │                 │
    │────────────────▶│                 │        ← synchronous call (solid arrow)
    │                 │ findSpot()      │
    │                 │────────────────▶│        ← nested call
    │                 │◀── spot ────────│        ← return (dashed arrow going back)
    │◀── ticket ──────│                 │        ← return to original caller
    │                 │                 │
```

| Element | What it means |
|---|---|
| Box at top | A participant (an object/class taking part in this interaction) |
| Vertical dashed line | Lifeline — the object "exists" while this line runs |
| Solid arrow `─────▶` | Synchronous method call — caller waits for return |
| Dashed arrow `◀─ ─ ─` | Return value or control flowing back |
| Time | Flows **top to bottom** — what's higher happened first |

---

### 🎨 Visual — Parking Lot Sequence Diagram (vehicle entry + exit)

```
Vehicle Entry:

 Client         ParkingLot         ParkingFloor       ParkingSpot
   │    (synchronized)  │                │                  │
   │ parkVehicle(v,t)   │                │                  │
   │───────────────────▶│                │                  │
   │                    │ findAvailableSpot(t)               │
   │                    │───────────────▶│                  │
   │                    │                │ isAvailable()?   │
   │                    │                │─────────────────▶│
   │                    │                │◀─── true ────────│
   │                    │◀─── spot ──────│                  │
   │                    │                │ assignVehicle(v) │
   │                    │────────────────────────────────▶  │
   │                    │ new ParkingTicket(...)             │
   │◀─── ticket ────────│                │                  │

Vehicle Exit:

 Client         ParkingLot          FeeStrategy
   │                 │                   │
   │ unparkVehicle(id)│                  │
   │────────────────▶│                   │
   │                 │ calculate(v, min) │
   │                 │──────────────────▶│
   │                 │◀─── fee ──────────│
   │                 │ spot.removeVehicle()
   │◀── ticket+fee ──│

KEY INVARIANT:
   Time flows top-to-bottom. Entry is synchronized (one lane).
   Exit does not need the full lot lock — only the ticket map access.
```

---

## 🧭 ASCII Shorthand Used in This Repo

Since notes use ASCII art (not image files), here's what each symbol means in context:

| ASCII | UML meaning | When you see it |
|---|---|---|
| `- - - ▷` or `- - -▶` | Implements / Realization | Interface implementations |
| `──────▷` or `──────▶` | Extends or Dependency | Inheritance, method calls |
| `◆──────` | Composition | Owner has filled diamond |
| `◇──────` | Aggregation | Owner has empty diamond |
| `1..*` at line end | Multiplicity | How many of each |
| `▶` on horizontal line | Synchronous call | Sequence diagrams |
| `◀` on dashed line | Return value | Sequence diagram return |
| `│` vertical | Lifeline | Object exists while running |

---

## 🖊️ Interview Drawing Tips

**For class diagrams:**
1. Start with a plain box per entity — name only, no fields yet
2. Draw the relationships first ("this composes that")
3. Add 2-3 key methods to the interfaces only — implementations don't need method lists
4. Say the relationship type out loud while drawing: *"ParkingLot composes ParkingFloor — if the lot goes away, the floors go away too"*

**For sequence diagrams:**
1. List participants left-to-right: Client → Orchestrator → Domain → Infrastructure
2. Draw the happy path first — top to bottom
3. Add the "what if no spot is available" branch verbally, not by drawing a full alt block
4. Every arrow should be a real method name you'd actually code

**What interviewers forgive:** Imperfect UML notation (wrong arrow style, missing multiplicity)

**What interviewers notice:** Not drawing any diagram at all, or drawing boxes with no relationships

---

## 🧾 TL;DR

> *Class diagram = static snapshot of classes and relationships. Sequence diagram = runtime movie of which object calls which method. You need both. The class diagram proves you design top-down. The sequence diagram proves you understand flow. Draw them before coding — every time.*

---

---

## 🎨 Practice — Read These Diagrams

Study these until you can sketch them from memory in 3 minutes. Each covers a different problem from the TODO list.

---

### Problem 1 — BookMyShow (Movie Ticket Booking)

**Class Diagram:**

```
┌──────────────────────────────────────┐
│           BookingService             │
│  - shows: Map<showId, Show>          │
│  - bookings: Map<bookingId, Booking> │
│  - pricingStrategy: PricingStrategy  │
├──────────────────────────────────────┤
│ + bookSeat(userId, showId, seatId)   │
│ + cancelBooking(bookingId)           │
└────────┬─────────────────┬───────────┘
         │ 1..*             │ uses
         ◆ (composition)    │ (association)
         ▼                  ▼
┌────────────────┐  ┌──────────────────────────┐
│     Show       │  │   <<interface>>           │
│  - showId      │  │   PricingStrategy         │
│  - movie       │  ├──────────────────────────┤
│  - startTime   │  │ + calculate(seat, show)  │
│  - screen      │  └──────────┬───────────────┘
└───────┬────────┘              │ implements (- - - ▷)
        │ 1                ┌────┴────────────────┐
        ◆ (composition)    │             │        │
        ▼               PeakHour   Student   Regular
┌───────────────────┐   Pricing   Pricing   Pricing
│      Screen       │
│  - screenId       │   ┌────────────────────────┐
│  - totalSeats     │   │        Booking          │
├───────────────────┤   │  - bookingId            │
│ + getSeat(id)     │   │  - user: User           │
└──────────┬────────┘   │  - seat: Seat           │
           │ 1..*       │  - show: Show           │
           ◆ (composition)  - price: double       │
           ▼            └────────────────────────┘
┌──────────────────┐
│       Seat       │
│  - seatId        │
│  - type: SeatType│  ← enum: GOLD, SILVER, PLATINUM
│  - status: Status│  ← enum: AVAILABLE, BOOKED
├──────────────────┤
│ + isAvailable()  │
│ + book(userId)   │
└──────────────────┘

KEY INVARIANT:
   Screen owns Seats (composition). Seat.book() must be synchronized —
   two users booking the same seat simultaneously is the InMobi problem.
```

**Sequence — Seat Booking:**

```
 User     BookingService (synchronized)   Screen         Seat
   │              │                          │               │
   │ bookSeat()   │                          │               │
   │─────────────▶│  LOCK acquired           │               │
   │              │ getSeat(seatId)          │               │
   │              │─────────────────────────▶│               │
   │              │◀─── seat ───────────────│               │
   │              │                          │ isAvailable() │
   │              │──────────────────────────────────────── ▶│
   │              │◀─── true ───────────────────────────────│
   │              │                          │ book(userId)  │
   │              │──────────────────────────────────────── ▶│
   │              │ pricingStrategy.calculate(seat, show)    │
   │              │ new Booking(user, seat, show, price)     │
   │              │ bookings.put(id, booking)                │
   │              │ LOCK released                            │
   │◀── booking ──│
```

---

### Problem 2 — Elevator System

**Class Diagram:**

```
┌──────────────────────────────────────────┐
│          ElevatorController              │
│  - elevators: List<Elevator>             │
│  - schedulerStrategy: SchedulerStrategy  │
│  - pendingRequests: Queue<ElevatorReq>   │
├──────────────────────────────────────────┤
│ + requestFloor(floor, direction)         │
│ + dispatch()                             │
└────────┬──────────────────┬──────────────┘
         │ 1..*              │ uses
         ◆ (composition)     │ (association)
         ▼                   ▼
┌─────────────────────┐  ┌──────────────────────────┐
│      Elevator       │  │   <<interface>>           │
│  - elevatorId       │  │   SchedulerStrategy       │
│  - currentFloor     │  ├──────────────────────────┤
│  - direction: Enum  │  │ + assignElevator(req,     │
│  - assignedReqs     │  │   elevators): Elevator    │
├─────────────────────┤  └──────────┬───────────────┘
│ + addRequest(req)   │             │ implements (- - - ▷)
│ + moveToNextFloor() │        ┌────┴────────┐
└─────────────────────┘        │             │
                            FCFS          SCAN
                           Strategy      Strategy

┌──────────────────────────────┐
│  <<interface>>               │
│  ElevatorRequest             │
├──────────────────────────────┤
│ + getTargetFloor(): int      │
│ + getPriority(): int         │
└──────────────┬───────────────┘
               │ implements (- - - ▷)
        ┌──────┴──────────┐
        │                 │
┌───────────────┐  ┌──────────────────┐
│InternalRequest│  │ ExternalRequest  │
│(cabin button) │  │ (floor button)   │
│- targetFloor  │  │ - floor          │
└───────────────┘  │ - direction: Enum│
                   └──────────────────┘

KEY INVARIANT:
   ElevatorController holds the scheduler strategy (Strategy pattern).
   InternalRequest vs ExternalRequest are created by Factory.
   ElevatorDirection enum: UP, DOWN, IDLE — with reverse() method.
```

---

### Problem 3 — Splitwise (Expense Sharing)

**Class Diagram:**

```
┌──────────────────────────────┐
│           Group              │
│  - groupId                   │
│  - members: List<User>       │
│  - expenses: List<Expense>   │
├──────────────────────────────┤
│ + addExpense(expense)        │
│ + getBalances(): Map<>       │
└──────────┬───────────────────┘
           │ 1..*
           ◆ (composition)
           ▼
┌────────────────────────────────────────┐
│              Expense                   │
│  - expenseId                           │
│  - totalAmount: double                 │
│  - paidBy: User                        │
│  - splits: List<ExpenseSplit>          │
│  - splitStrategy: SplitStrategy        │
├────────────────────────────────────────┤
│ + split()                              │
└───────────┬────────────────────────────┘
            │                  │ uses
            │ 1..*             │ (association)
            ◆                  ▼
            ▼        ┌────────────────────────────┐
┌────────────────┐   │   <<interface>>            │
│  ExpenseSplit  │   │   SplitStrategy            │
│  - owedBy: User│   ├────────────────────────────┤
│  - amount      │   │ + split(amount,            │
└────────────────┘   │   members): List<Split>    │
                     └───────────────┬────────────┘
                                     │ implements (- - - ▷)
                              ┌──────┼──────────────┐
                              │      │               │
                           Equal   Exact       Percentage
                           Split   Split         Split

┌──────────────────┐
│      User        │
│  - userId        │
│  - name          │
│  - balances: Map │  ← Map<User, Double> — positive = owed to me
└──────────────────┘

KEY INVARIANT:
   Expense holds a SplitStrategy injected at construction (DIP + Strategy).
   Group.getBalances() aggregates all ExpenseSplits across all expenses.
   Debt simplification is a separate class (SRP — separate algorithm).
```

---

### Problem 4 — Rate Limiter (LLD)

**Class Diagram:**

```
┌───────────────────────────────────────────┐
│           RateLimiterService              │
│  - limiters: Map<clientId, RateLimiter>   │
│  - factory: RateLimiterFactory            │
├───────────────────────────────────────────┤
│ + isAllowed(clientId): boolean            │
│ + configure(clientId, config)             │
└─────────────┬─────────────────────────────┘
              │ uses
              ▼
┌─────────────────────────────────┐
│   RateLimiterFactory            │
├─────────────────────────────────┤
│ + create(ClientConfig): Limiter │
└─────────────────────────────────┘
              │ creates
              ▼
┌──────────────────────────────┐
│   <<interface>>              │
│   RateLimiter                │
├──────────────────────────────┤
│ + isAllowed(clientId): bool  │
│ + getRemainingQuota(): int   │
└──────────────┬───────────────┘
               │ implements (- - - ▷)
        ┌──────┼──────────────────┐
        │      │                  │
┌───────────┐ ┌────────────────┐ ┌──────────────────┐
│  Token    │ │    Sliding     │ │     Fixed        │
│  Bucket   │ │    Window      │ │     Window       │
│  Limiter  │ │    Limiter     │ │     Limiter      │
│           │ │                │ │                  │
│ - tokens  │ │ - timestamps   │ │ - requestCount   │
│ - maxTok  │ │   : Deque<Long>│ │ - windowStart    │
│ - refill  │ │ - windowSec    │ │ - maxRequests    │
└───────────┘ └────────────────┘ └──────────────────┘

┌──────────────────────────────┐
│       ClientConfig           │
│  - maxRequests: int          │
│  - windowSeconds: int        │
│  - strategy: String          │  ← "TOKEN_BUCKET", "SLIDING_WINDOW"
└──────────────────────────────┘

KEY INVARIANT:
   Factory creates the right limiter based on ClientConfig.strategy (Factory pattern).
   RateLimiterService holds a Map<clientId, RateLimiter> — each client gets their own limiter.
   SlidingWindowLimiter uses a Deque<Long> of timestamps — evicts timestamps older than windowSeconds.
```

**Sequence — Rate Check:**

```
 APIGateway      RateLimiterService      RateLimiter (e.g. TokenBucket)
     │                   │                        │
     │ isAllowed(client) │                        │
     │──────────────────▶│                        │
     │                   │ limiters.get(clientId) │
     │                   │  = limiter             │
     │                   │ limiter.isAllowed()    │
     │                   │───────────────────────▶│
     │                   │                        │ check tokens > 0
     │                   │                        │ consume 1 token
     │                   │◀─── true / false ──────│
     │◀─── true / false ─│

KEY INVARIANT:
   isAllowed() must be thread-safe — multiple API threads call it concurrently.
   TokenBucketLimiter uses synchronized or AtomicInteger for token count.
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Created — prerequisite for reading Problem notes. Driven by parking-lot.md diagram confusion. |
| June 2026 | Added practice diagrams for BookMyShow, Elevator, Splitwise, Rate Limiter — class + sequence. |
| Aug 2026 | Moved to Foundations/07-uml-for-interviews.md during LLD folder restructure; cross-reference paths updated. |
