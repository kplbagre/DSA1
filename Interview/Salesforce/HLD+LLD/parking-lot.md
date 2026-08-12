# Parking Lot — HLD + LLD Combined Round

---

## 0.  Identity

| | |
|---|---|
| **Problem** | Parking Lot System — allocate spots to vehicles on entry, price and collect on exit, across multi-level lots |
| **Format** | HLD+LLD combined (Salesforce SMTS), 90 min confirmed |
| **Time budget** | 35 min LLD -> 45 min HLD -> 10 min buffer |
| **Frequency rank** | **#5 pick** in `questions-by-frequency.md` — #2 LLD in the archived file, #3 LLD in fresh research. Verbatim (CodingKaro, Oct 2025, MTS I Hyderabad): *"Design a parking lot on a whiteboard and provide a valid code implementation."* Note the ask: **whiteboard design AND working code**, so the coding skeleton matters more here than usual. |
| **Salesforce-specific angle** | Weakest Salesforce tie of the top 7 — it's a generic OOD classic. Best angle: treat each **lot as a tenant** in a SaaS parking platform (multi-operator), which is a legitimate multi-tenancy story. Don't force Platform Events here; forced relevance reads worse than none. |

**Why this problem is deceptively easy:** everyone has a memorized answer, so a memorized answer scores nothing. The differentiators are: (1) the spot-allocation strategy being pluggable, (2) pricing as a first-class strategy rather than an `if`, (3) honest concurrency at the entry gate, and (4) knowing which classes *not* to create.

---

## 1.  Dual-Layer Map

| HLD Box (system view) | LLD Class(es) (code view) | The interface that makes it swappable |
|---|---|---|
| Entry gate / terminal | `EntryGate`, `Ticket` | — |
| Spot allocation | `SpotAllocator` | **`AllocationStrategy`** — nearest, level-fill, random |
| Spot inventory | `ParkingSpot`, `Level`, `ParkingLot` | `SpotRepository` |
| Pricing engine | `PricingService` | **`PricingStrategy`** — hourly, flat, tiered, dynamic |
| Payment | `PaymentProcessor` | **`PaymentMethod`** — card, cash, wallet |
| Exit gate | `ExitGate` | — |
| Availability display | `AvailabilityService`, counters | — (derived/cached) |
| Reservation (extension) | `Reservation` | `ReservationPolicy` |

**The zoom sentence:** *"`SpotAllocator` is a method picking from an in-memory tree in LLD. In HLD it's a service backed by per-level atomic counters in Redis, because at 500 lots and thousands of entries a minute you can't hold the whole inventory in one JVM — and two gates must never hand out the same spot."*

---

## 2.  LLD Half (target: 35 min)

### 2.1  Problem Statement

Design a parking lot system: vehicles enter and are assigned a compatible spot, receive a ticket, and on exit the system computes the fee based on duration and vehicle type, collects payment, and frees the spot.

### 2.2  Requirements

**Functional:**
- Multi-level lot, multiple spot sizes (motorcycle, compact, large, handicapped, EV)
- Assign a compatible spot on entry; issue a ticket
- Compute fee on exit by duration + vehicle/spot type
- Accept payment via multiple methods; release the spot
- Report real-time availability per level and per spot type

**Non-Functional:**
- **Thread-safe** — multiple entry gates allocate concurrently; no two vehicles get the same spot
- **Extensible** — new vehicle type, new pricing scheme, or new allocation policy = one new class
- **Fast allocation** — O(1) or O(log n), not a scan of every spot
- Availability counts must never drift from reality

**Out of scope (say it):** license-plate OCR, physical hardware/sensor integration, valet.

### 2.3  Class Design

#### 2.3.1  Deriving the classes (say this out loud, minutes 2-6)

| # | Requirement | Noun / variation point | Becomes | Why it earns its own type (and what breaks if you inline it) |
|---|---|---|---|---|
| 1 | "vehicles enter" | noun: *vehicle* | **`Vehicle`** (entity) + **`VehicleType`** (enum) | Vehicle has identity (plate). Type is a closed set with no behavior of its own -> enum. **Deliberately NOT a `Car`/`Truck` class hierarchy** — see the follow-ups; inheritance here buys nothing because behavior doesn't vary by vehicle, only *sizing rules* do. |
| 2 | "assigned a compatible spot" | noun: *spot* | **`ParkingSpot`** (entity) + **`SpotType`** (enum) | Spot has identity, location, occupancy state. Type is again a closed enum. |
| 3 | same — "**compatible**" | the *fit rule* between vehicle and spot | **`SpotCompatibility`** (a rule/table, not a class per pair) | A motorcycle fits a motorcycle spot *or* a compact *or* a large. This is a many-to-many rule — encoding it as `if (vehicle == CAR && spot == LARGE)` chains gives you a combinatorial mess. One declarative `Map<VehicleType, List<SpotType>>` in preference order, in exactly one place. |
| 4 | "which spot do we pick?" | the *selection policy* varies | **`AllocationStrategy`** (interface) | **Primary variation point #1.** Nearest-to-entrance, fill-level-by-level, spread-for-EV-chargers — real lots differ. Inlining means changing policy edits the allocator core. |
| 5 | "receive a ticket" | noun: *the parking session* | **`Ticket`** (entity) | The record binding vehicle + spot + entry time. This is what makes pricing and exit possible. It's the aggregate root of a parking session, not a receipt. |
| 6 | "compute fee by duration + type" | the *pricing rule* varies | **`PricingStrategy`** (interface) | **Primary variation point #2.** Hourly, flat day rate, progressive tiers, weekend/event surge. Pricing changes constantly in the real world — it must be the easiest thing to swap. Hardcoded arithmetic in the exit gate means a pricing change is a core-logic deploy. |
| 7 | "accept payment via multiple methods" | the *payment mechanism* varies | **`PaymentMethod`** (interface) | Variation point #3. Card/cash/wallet have genuinely different flows. |
| 8 | "multi-level" | noun: *level*, and the whole *lot* | **`Level`**, **`ParkingLot`** | Composition hierarchy: lot HAS-MANY levels, level HAS-MANY spots. Also the natural unit for availability counters. |
| 9 | "report real-time availability" | verb: *count free spots* | (counters on `Level`, **not** a new class) | **Deliberately not a class.** It's `Map<SpotType, AtomicInteger>` maintained by the level. A `AvailabilityTracker` class would be a wrapper around a map with no behavior of its own. Say this out loud — restraint is a signal. |
| 10 | spot lifecycle | *state* of a spot | **`SpotStatus`** (enum) | `FREE / OCCUPIED / RESERVED / OUT_OF_SERVICE`. Transitions are simple and behavior-free -> enum, not State pattern. |

**One-liner after the table:** *"Three variation points — how we pick a spot, how we price, how we take payment — and everything else is a plain entity or an enum. I'm resisting a `Car`/`Truck` hierarchy and an availability-tracker class because neither would carry behavior."*

#### 2.3.2  Entity fields

```
ParkingLot
  - lotId:   String
  - levels:  List<Level>            <- composition
  - allocator: SpotAllocator
  - pricing:   PricingStrategy

Level
  - levelId:      String
  - floorNumber:  int
  - spots:        Map<String, ParkingSpot>          <- composition
  - freeByType:   Map<SpotType, AtomicInteger>      <- the availability counters

ParkingSpot
  - spotId:       String
  - type:         SpotType
  - status:       SpotStatus
  - distanceToEntrance: int          <- drives the "nearest" allocation strategy
  - currentTicketId:    String       <- null when free

Ticket                               <- the parking session (aggregate root)
  - ticketId:    String
  - plate:       String
  - vehicleType: VehicleType
  - spotId:      String
  - levelId:     String
  - entryTime:   Instant
  - exitTime:    Instant             <- null while parked
  - amountPaid:  BigDecimal          <- BigDecimal, never double, for money
  - status:      TicketStatus

VehicleType (enum): MOTORCYCLE, COMPACT, LARGE, EV, HANDICAPPED
SpotType    (enum): MOTORCYCLE, COMPACT, LARGE, EV_CHARGING, HANDICAPPED
SpotStatus  (enum): FREE, OCCUPIED, RESERVED, OUT_OF_SERVICE
TicketStatus(enum): ACTIVE, PAID, LOST
```

**Say this when you write `amountPaid`:** *"`BigDecimal`, not `double` — floating point can't represent 0.10 exactly and money arithmetic silently accumulates error. This is the kind of thing that shows up as a one-cent discrepancy in reconciliation."*

#### 2.3.3  Relationships — with the composition-vs-aggregation call made explicit

| Relationship | Type | Composition or aggregation — and why that one |
|---|---|---|
| `ParkingLot` — `Level` | **HAS-MANY** -> **composition** | Levels are created with the lot, have no meaning outside it, and are destroyed with it. Filled diamond. A level of a demolished lot is nothing. |
| `Level` — `ParkingSpot` | **HAS-MANY** -> **composition** | Same reasoning one layer down — a spot belongs to exactly one level for its entire existence. |
| `Ticket` — `ParkingSpot` | **references by ID** | Deliberate: the ticket stores `spotId`, not the object. The ticket outlives the parking session (it's a financial record kept for years) while the spot object is live inventory — different lifetimes, so no ownership. |
| `Ticket` — `Vehicle` | **stores the plate + type, not the object** | The system doesn't own vehicles; it observes them. Modelling a persistent `Vehicle` entity implies a vehicle registry nobody asked for. Say this — it's a scope-discipline signal. |
| `ParkingLot` — `PricingStrategy` | **HAS-A** -> **aggregation** | Injected, and typically a shared stateless singleton across lots in the same operator. Not composition — the strategy has independent lifecycle and is reused. |
| `SpotAllocator` — `AllocationStrategy` | **HAS-A** -> **aggregation** | Injected; stateless and shared. |
| `PaymentProcessor` — `PaymentMethod` | **USES** (resolved per transaction) | Looked up per payment and discarded; no state held. |
| `Level` — `Map<SpotType, AtomicInteger>` | **HAS-A** -> **composition** | The counters are created with the level, exclusively owned, and meaningless elsewhere. |

#### 2.3.4  ASCII class diagram — interfaces before implementations, always

```
                            ParkingLot
                            - levels:    List<Level>       <>--- composition
                            - allocator: SpotAllocator
                            - pricing:   PricingStrategy   <>--- aggregation
                            + park(Vehicle): Ticket
                            + unpark(ticketId, PaymentMethod): Receipt
                                     |  uses
        +----------------------------+----------------------------+
        v                            v                            v
  <<interface>>               <<interface>>                <<interface>>
  AllocationStrategy          PricingStrategy              PaymentMethod
  + findSpot(levels,          + calculate(Ticket,          + pay(BigDecimal):
      VehicleType):               Instant exitTime):           PaymentResult
      Optional<ParkingSpot>       BigDecimal                + getType()
        ^                            ^                            ^
        | implements                 | implements                 | implements
  +-----+---------+          +-------+--------+           +-------+-------+
  |               |          |       |        |           |       |       |
Nearest        LevelFill   Hourly  FlatRate  Tiered     Card    Cash   Wallet
Strategy       Strategy    Pricing Pricing   Pricing

                            Level
                            - spots:      Map<String, ParkingSpot>  <>--- composition
                            - freeByType: Map<SpotType, AtomicInteger>
                            + reserveSpot(SpotType): Optional<ParkingSpot>
                            + releaseSpot(spotId): void
                                     |
                                     v
                            ParkingSpot
                            - type:   SpotType
                            - status: SpotStatus
                            - distanceToEntrance: int

                            Ticket  (aggregate root of a session)
                            - spotId, levelId  (by ID, not object)
                            - entryTime: Instant
                            - amountPaid: BigDecimal
```

#### 2.3.5  Follow-ups they will ask after this section — and your answers

| Their question | Your answer (one breath) |
|---|---|
| "Why isn't there a `Car`/`Truck`/`Motorcycle` class hierarchy?" | "Because no behavior varies by vehicle — a truck doesn't park differently, it just needs a bigger spot. Subclasses with no overridden behavior are ceremony. What actually varies is the *sizing rule*, so that lives in a compatibility table. If vehicles later gained real behavior — an EV needing charge negotiation — I'd introduce the hierarchy then." |
| "Composition or aggregation between `Level` and `ParkingSpot`?" | "Composition — spots are created with the level, belong to exactly one level forever, and die with it. Contrast `PricingStrategy` on the lot: that's aggregation, injected and shared across lots." |
| "Why does `Ticket` store `spotId` instead of the `ParkingSpot`?" | "Different lifetimes. The ticket is a financial record kept for years; the spot is live inventory. Holding the object means an archived ticket pins live inventory objects in memory and blurs the aggregate boundary." |
| "How do you find a compatible spot without scanning?" | "Per-level, per-type free counters plus a free-list per type — allocation is a counter check and a pop, so O(1). Scanning every spot is O(n) per entry and gets worse exactly when the lot is busy." |
| "Two cars arrive at two gates at the same instant." | "The allocation must be atomic: either a per-level lock around 'find and mark occupied', or an atomic decrement on the type counter that reserves capacity before picking a specific spot. Find-then-mark without atomicity is the check-then-act race and hands both drivers the same spot." |
| "Isn't `PricingStrategy` overkill for hourly rates?" | "Pricing is the single most volatile rule in a real parking business — weekends, events, EV surcharges, validation discounts. It's the one place I'd insist on a strategy even at small scale, because the alternative is a pricing change touching core code. Contrast the availability counters, where I deliberately *didn't* create a class." |
| "Where does the fee get computed — `Ticket` or the strategy?" | "The strategy. `Ticket` is data: plate, spot, times. Putting `calculateFee()` on it means the entity knows about rate cards and surge rules, and every pricing change edits the entity. Anemic-vs-rich-domain is a real debate, but pricing volatility settles it here." |

### 2.4  Key Interfaces

```java
/** Variation point #1: which free spot do we hand out? */
public interface AllocationStrategy {
    Optional<ParkingSpot> findSpot(List<Level> levels, VehicleType vehicleType);
}
```

```java
/** Variation point #2: the most volatile rule in the system. */
public interface PricingStrategy {
    BigDecimal calculate(Ticket ticket, Instant exitTime);
}
```

```java
/** Variation point #3. */
public interface PaymentMethod {
    PaymentResult pay(BigDecimal amount);
    PaymentType getType();
}
```

```java
/**
 * Compatibility as declarative data, not conditionals.
 * Order matters: prefer the tightest fit so large spots stay free for large vehicles.
 */
public final class SpotCompatibility {
    private static final Map<VehicleType, List<SpotType>> FITS = Map.of(
        VehicleType.MOTORCYCLE, List.of(SpotType.MOTORCYCLE, SpotType.COMPACT, SpotType.LARGE),
        VehicleType.COMPACT,    List.of(SpotType.COMPACT, SpotType.LARGE),
        VehicleType.LARGE,      List.of(SpotType.LARGE),
        VehicleType.EV,         List.of(SpotType.EV_CHARGING, SpotType.COMPACT, SpotType.LARGE),
        VehicleType.HANDICAPPED,List.of(SpotType.HANDICAPPED, SpotType.COMPACT, SpotType.LARGE)
    );

    public static List<SpotType> compatibleTypes(VehicleType type) {
        return FITS.getOrDefault(type, List.of());
    }
}
```

**Say this about the ordering:** *"Preference order is the tightest fit first — a motorcycle takes a motorcycle spot before consuming a large one. Without ordering, a busy lot fills its large spots with motorcycles and then turns away trucks while spots sit empty. That's a real revenue bug, not a style preference."*

### 2.5  Design Decisions

**The question you must be ready for: "How do you allocate a spot without a scan, and without two gates colliding?"** Those two constraints fight each other, and the answer is the design:

| Option | How it works | Pros | Cons | Verdict |
|---|---|---|---|---|
| Scan all spots for the first free compatible one | Linear search | Trivial | O(n) per entry; and the scan-then-mark gap is a race | Rejected — slow *and* wrong |
| `synchronized` on the whole lot | Global monitor | Correct | Every gate in a 5,000-spot lot serializes through one lock; entry throughput collapses at peak | Rejected — correct but unusable |
| **Per-type free-list + atomic counter, locked per level** | Counter reserves capacity, free-list pops a concrete spot | O(1); contention scoped to one level | Slightly more state to keep consistent | **Chosen** |
| Fully lock-free via CAS on a bitmask | Atomic bit flip per spot | No locks at all | Bitmask per level per type is fiddly; harder to read on a whiteboard | Good, but explain the simpler version first |

**Decision:** per-level lock around counter-decrement + free-list pop. Say the granularity point: *"Lock scope is the level, not the lot — cars entering toward different levels don't contend. Same principle as locking per-room in the booking problem: lock exactly the resource whose invariant you're protecting."*

**Why `SpotStatus` is an enum and not a State pattern:** transitions (`FREE -> OCCUPIED -> FREE`) carry no per-state behavior — nothing different happens *because* the spot is occupied beyond it not being allocatable. A State class per status would be four classes that only gate transitions. **Upgrade trigger to name:** *"If reserved spots gained timers, grace periods, and auto-expiry with side effects, that's when State earns its place."*

| Decision | Pattern Chosen | Strongest Alternative Considered | Why the alternative loses |
|---|---|---|---|
| `PricingStrategy` interface | **Strategy** | `calculateFee()` method on `Ticket` | Puts the most volatile business rule inside an entity — every rate change edits the domain object, and you can't run two price schemes (weekday/event) simultaneously |
| Compatibility as a declarative map | **Table-driven rules** | `if/else` chains on (vehicle, spot) pairs | Combinatorial and unreadable at 5 vehicle x 5 spot types; adding a type means auditing every branch |
| No `Vehicle` subclass hierarchy | **Enum + rules table** | `abstract Vehicle` with `Car`/`Truck`/`Motorcycle` | Subclasses with zero overridden behavior are pure ceremony; the real variation is sizing, which is data |
| Per-level lock + counters | **Fine-grained locking** | `synchronized` on the whole lot | Serializes all gates; a 6-gate lot behaves like a 1-gate lot at rush hour |
| `Ticket` as the session aggregate | **Aggregate root** | Mutating spot state as the source of truth | Without a ticket you can't price, audit, or recover from a lost ticket; the spot alone doesn't know when the car arrived |
| `BigDecimal` for money | **Correct primitive** | `double` | Binary floating point can't represent decimal cents; errors accumulate and reconciliation breaks |

### 2.6  Visual — Object Interaction (entry and exit)

```
== ENTRY ==
EntryGate.admit(vehicle)
      |
      +--> allocator.findSpot(levels, vehicle.type)
      |        |
      |        +--> for each compatible SpotType in preference order:
      |               for each level (ordered by strategy):
      |                 ** inside level lock **
      |                   if freeByType[type].get() > 0:
      |                       freeByType[type].decrementAndGet()
      |                       spot = freeList[type].pop()
      |                       spot.status = OCCUPIED
      |                       return spot
      |                 ** release lock **
      |
      +--> if empty -> LotFullException (per compatible type, not the whole lot)
      |
      +--> ticket = new Ticket(plate, spot.id, level.id, now(), ACTIVE)
      +--> ticketRepo.save(ticket)
      +--> open barrier, print ticket
      v
   Ticket

== EXIT ==
ExitGate.process(ticketId, paymentMethod)
      |
      +--> ticket = ticketRepo.findActive(ticketId)   -> else LOST-ticket flow
      |
      +--> fee = pricingStrategy.calculate(ticket, now())
      |        (duration + vehicle/spot type + any surge/validation)
      |
      +--> result = paymentMethod.pay(fee)
      |        |
      |        +-- FAILED  -> keep barrier closed, ticket stays ACTIVE
      |        +-- SUCCESS -> continue
      |
      +--> ** inside level lock **
      |        spot.status = FREE
      |        spot.currentTicketId = null
      |        freeByType[spot.type].incrementAndGet()
      |        freeList[spot.type].push(spot)
      |    ** release **
      |
      +--> ticket.exitTime = now(); ticket.amountPaid = fee; ticket.status = PAID
      +--> ticketRepo.save(ticket)
      +--> open barrier
```

**Narrate this:** *"Payment happens **before** the spot is released. If we free the spot first and payment fails, we've given away inventory for free and the car is still physically there. Order of operations is a business-correctness decision, not just code sequencing."*

### 2.7  Coding Skeleton

The prompt explicitly asked for *"a valid code implementation"* — so write the allocation path properly, including the lock.

```java
// 1. Enums first
public enum VehicleType { MOTORCYCLE, COMPACT, LARGE, EV, HANDICAPPED }
public enum SpotType    { MOTORCYCLE, COMPACT, LARGE, EV_CHARGING, HANDICAPPED }
public enum SpotStatus  { FREE, OCCUPIED, RESERVED, OUT_OF_SERVICE }

// 2. Interface before implementation
public interface AllocationStrategy {
    Optional<ParkingSpot> findSpot(List<Level> levels, VehicleType vehicleType);
}

// 3. Level owns its inventory AND its own lock — contention scoped per level
public class Level {
    private final String levelId;
    private final Map<SpotType, Deque<ParkingSpot>> freeLists;
    private final Map<SpotType, AtomicInteger> freeByType;
    private final ReentrantLock lock = new ReentrantLock();

    /** Atomic: reserve capacity and take a concrete spot in one critical section. */
    public Optional<ParkingSpot> reserveSpot(SpotType type) {
        lock.lock();
        try {
            Deque<ParkingSpot> free = freeLists.get(type);
            if (free == null || free.isEmpty()) {
                return Optional.empty();
            }
            ParkingSpot spot = free.pop();          // O(1)
            spot.setStatus(SpotStatus.OCCUPIED);
            freeByType.get(type).decrementAndGet();
            return Optional.of(spot);
        } finally {
            lock.unlock();
        }
    }

    public void releaseSpot(ParkingSpot spot) {
        lock.lock();
        try {
            spot.setStatus(SpotStatus.FREE);
            spot.setCurrentTicketId(null);
            freeLists.get(spot.getType()).push(spot);
            freeByType.get(spot.getType()).incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    public int available(SpotType type) {
        return freeByType.getOrDefault(type, new AtomicInteger(0)).get();
    }
}

// 4. Strategy implementation — tightest fit first, then nearest level
public class NearestFirstAllocation implements AllocationStrategy {
    @Override
    public Optional<ParkingSpot> findSpot(List<Level> levels, VehicleType vehicleType) {
        for (SpotType type : SpotCompatibility.compatibleTypes(vehicleType)) {
            for (Level level : levels) {                 // pre-sorted by proximity
                Optional<ParkingSpot> spot = level.reserveSpot(type);
                if (spot.isPresent()) {
                    return spot;                          // O(1) per attempt
                }
            }
        }
        return Optional.empty();
    }
}

// 5. Orchestrator — narrate this one live
public class ParkingLot {
    private final List<Level> levels;
    private final AllocationStrategy allocator;
    private final PricingStrategy pricing;
    private final TicketRepository ticketRepo;

    public Ticket park(Vehicle vehicle) {
        ParkingSpot spot = allocator.findSpot(levels, vehicle.getType())
            .orElseThrow(() -> new LotFullException(vehicle.getType()));

        Ticket ticket = Ticket.issue(vehicle.getPlate(), vehicle.getType(),
                                     spot.getSpotId(), spot.getLevelId(), Instant.now());
        spot.setCurrentTicketId(ticket.getTicketId());
        return ticketRepo.save(ticket);
    }

    public Receipt unpark(String ticketId, PaymentMethod method) {
        Ticket ticket = ticketRepo.findActive(ticketId)
            .orElseThrow(() -> new InvalidTicketException(ticketId));

        BigDecimal fee = pricing.calculate(ticket, Instant.now());

        PaymentResult result = method.pay(fee);       // pay BEFORE releasing
        if (!result.isSuccess()) {
            throw new PaymentFailedException(result.getReason());
        }

        findLevel(ticket.getLevelId()).releaseSpot(findSpot(ticket.getSpotId()));

        ticket.complete(Instant.now(), fee);
        ticketRepo.save(ticket);
        return new Receipt(ticket, fee, result.getTransactionId());
    }
}
```

### 2.8  Concurrency — Making It Thread-Safe

| Race | Where | Fix | Why this fix |
|---|---|---|---|
| **Two gates allocate the same spot** | find-then-mark in `findSpot` | `ReentrantLock` **per level**, covering counter-decrement + free-list pop as one critical section | Scoped to the level, so gates heading to different levels never contend. Locking the whole lot would serialize every gate — correct but throughput-destroying |
| **Availability counter drifts from the free list** | two structures updated separately | Both mutated **inside the same lock** | If they're updated outside a shared critical section, the display shows free spots that don't exist and the gate rejects cars while the sign says "20 FREE" |
| **Double exit / ticket replay** | `unpark` called twice | Ticket status guard (`findActive`) + idempotent completion keyed on `ticketId` | Second call finds no ACTIVE ticket; without it, the spot is released twice and the counter over-increments, inventing phantom capacity |
| **Payment succeeds, release fails** | between pay and release | Release in a `finally` / compensating retry; ticket records the payment first | Otherwise the customer paid and the barrier stays shut, or the spot is stuck OCCUPIED forever |

**The counter-drift point is the senior signal here** — most candidates lock the allocation but update the availability counter outside the lock, then can't explain why the display lies at rush hour.

### 2.9  "What Would You Do Differently?"

**I'd separate reservation of *capacity* from selection of a *specific spot*.** Right now the level lock covers both. Under heavy entry load, an atomic `decrementAndGet` on the type counter can reserve capacity lock-free, and only the free-list pop needs the (much shorter) lock — or a `ConcurrentLinkedDeque` removes the lock entirely. It shortens the critical section on the hottest path. **Trade-off:** two-step reservation needs a compensating increment if the pop somehow fails, so the code gets slightly harder to reason about for a throughput win that a small lot doesn't need.

**Second:** I'd model *lost tickets* explicitly rather than treating them as an error. Real lots charge a flat maximum-day rate for a lost ticket, and it's a routine flow — `TicketStatus.LOST` with a `LostTicketPricing` strategy handles it as a first-class case rather than an exception path someone bolts on later.

### 2.10  Interview Q&As (prep-only)

| Q | A |
|---|---|
| "How do you handle reserved/prepaid spots?" | "`SpotStatus.RESERVED` excludes them from the free list, plus a `Reservation` holding spot, window, and holder. The nuance is expiry — a reservation nobody claims must auto-release, which is a scheduled sweep (the Job Scheduler problem again)." |
| "Motorcycle takes a large spot and a truck is turned away — how do you prevent that?" | "Preference ordering in the compatibility table: tightest fit first. Beyond that, reserve a floor of large spots that small vehicles can't consume — the same idea as reserved capacity in a thread pool." |
| "How do you price a vehicle that stayed 3 days?" | "Tiered strategy with a daily cap — hourly up to a day-rate ceiling, then per-day. This is exactly why pricing is a strategy: 'add a daily cap' becomes a new class, not surgery on the exit gate." |
| "Multiple entrances/exits?" | "Levels are already independently locked, so multiple gates work as-is. What changes is allocation policy — 'nearest' must mean nearest *to the gate the vehicle entered*, so the strategy takes the entry point as an input." |
| "What if the system goes down while cars are parked?" | "Tickets are persisted, so state survives. Rebuild in-memory counters from `spots WHERE status='OCCUPIED'` on startup. If the ticket system is unreachable at exit, fall back to a manual flat fee — never trap cars inside; the physical failure mode dominates the software one." |
| "Where would you cache?" | "Availability counts, since displays and apps poll them constantly and slight staleness is harmless. Never cache spot *allocation* — that must read authoritative state inside the lock." |

### 2.11  TL;DR — 30-Second Pitch (LLD)

Three Strategy interfaces carry the variation: `AllocationStrategy` (which spot), `PricingStrategy` (what it costs — the most volatile rule in a real parking business), and `PaymentMethod`. Everything else is plain entities and enums, and I deliberately avoid a `Car`/`Truck` hierarchy since no behavior varies by vehicle — only sizing does, which lives in a declarative compatibility table ordered tightest-fit-first so motorcycles don't consume large spots. Allocation is O(1) via per-type free lists plus counters, and the critical decision is that the counter decrement and the free-list pop happen inside **one per-level lock** — per-level, not per-lot, so gates heading to different levels never contend, and both structures move together so the availability display can't drift from reality.

### 2.12  Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `AllocationStrategy`, `PricingStrategy`, `PaymentMethod` | Three genuine variation points, swappable at runtime |
| **Composite** (structural) | `ParkingLot` -> `Level` -> `ParkingSpot` | Natural containment hierarchy, and the unit of locking |
| **Aggregate Root** | `Ticket` | The parking session; the thing pricing and audit hang off |
| **Table-driven rules** | `SpotCompatibility` | Replaces a combinatorial `if/else` with ordered declarative data |
| **Object Pool** (conceptually) | per-type free lists | O(1) acquire/release of a fixed resource set |
| **Factory** | `Ticket.issue(...)` | Enforces required fields and timestamps at creation |

---

## 3.  HLD Half (target: 45 min)

### 3.1 Clarifying Questions (0-3 min)

| Question | Architectural Fork |
|---|---|
| "One lot or a platform operating many lots?" | One lot -> a single service and one DB; honestly, a monolith is correct. A SaaS platform across 500 lots/multiple operators -> multi-tenancy, per-lot isolation, and a central pricing/reporting plane. **This question decides whether there's an HLD at all.** |
| "Do drivers reserve in advance, or is it drive-up only?" | Drive-up -> allocation is purely at the gate. Advance reservation -> capacity must be held against future intervals, which turns this into the booking problem with time ranges. |
| "Is the gate hardware online-only, or must it work if the network drops?" | Online-only -> simple. **Offline-tolerant -> the gate needs local state and reconciliation**, which is the interesting distributed problem here. |
| "Are payments taken at exit, or also at kiosks/app in advance?" | Exit-only -> one flow. Multi-channel -> the ticket becomes the shared state across gate, kiosk, and app, and needs idempotent payment. |

### 3.2 Requirements

**Functional (5):**
- Vehicle entry with automatic spot allocation and ticket issuance
- Fee computation and multi-channel payment (gate, kiosk, mobile)
- Exit validation and spot release
- Real-time availability per lot/level/type for signage and apps
- Operator reporting: occupancy, revenue, turnover

**Non-Functional (4):**
- Scale: **500 lots x ~1,000 spots = 500K spots**; peak **~2K entries/min** platform-wide (rush hour)
- Gate latency: **< 500ms** entry decision (a driver is physically waiting at a barrier)
- **Availability > correctness at the gate:** never trap cars. Degrade to offline mode rather than fail closed
- Availability counts accurate within a few seconds

### 3.3 Core Entities

| Entity | Nature |
|---|---|
| **Lot / Level / Spot** | transactional — slow-changing inventory, heavily cached |
| **Ticket** | transactional then append-only — mutable while ACTIVE, immutable financial record after |
| **Payment** | append-only — never mutate a payment; corrections are new rows |
| **AvailabilitySnapshot** | derived / ephemeral — reconstructible from spot state |
| **GateEvent** | append-only — entry/exit audit, and the reconciliation source for offline mode |

### 3.4 Scale Estimation

- **Entry throughput:** 2K entries/min = ~33/sec platform-wide. **Tiny.** Say so explicitly — *"this is not a throughput problem; it's a latency-at-the-barrier and availability problem."* Recognizing when scale *isn't* the challenge is itself a signal.
- **Latency budget:** 500ms at the barrier. A cross-region DB round trip (~100ms+) plus payment authorization (~2s for cards) means **payment cannot be inline with barrier opening** — hence pre-authorization or open-then-settle for app users.
- **Storage:** 500 lots x 1,000 spots x ~3 turnovers/day = **1.5M tickets/day** x ~400 bytes = ~600 MB/day -> **~220 GB/year**. Trivially handled with 90-day hot partitions plus archival.
- **Availability reads:** signage + apps polling every ~5s across 500 lots = ~100 reads/sec of a tiny aggregate — pure cache territory, never hitting the ticket store.

### 3.5 Architecture Diagram

#### Stage 1 — Naive: gates call a central service synchronously for everything

```
   [Entry Gate]---+
   [Exit Gate]----+---> HTTPS ---> +---------------------+ ---> +------------+
   [Kiosk]--------+                | Central Parking Svc |      | Postgres   |
   [Mobile App]---+                | allocate / price /  |      | (single)   |
                                   | pay / release       |      +------------+
                                   +---------------------+
```

**BREAKING POINT 1 — network loss traps cars (the availability failure).** If the gate can't reach the service, no ticket is issued and the barrier never opens. A 30-second connectivity blip at rush hour produces a physical queue on a public road, and unlike a web request, **the user cannot retry later — they're stuck in a car in front of a barrier.** This is the defining failure of this system and it's an availability problem, not a scale one.

**BREAKING POINT 2 — payment latency exceeds the human/barrier budget.** Card authorization takes ~2-3 seconds; add network and DB and the exit barrier sits closed for 3-4 seconds per car. At a busy exit that's a queue that never drains during peak.

**BREAKING POINT 3 — one DB for 500 lots couples unrelated failures.** A reporting query for one operator degrades gate latency for every other lot on the platform. Blast radius is the entire portfolio.

**DECISION — where does gate decision authority live?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Central service decides everything | One source of truth; simple | Network partition traps cars; latency at the barrier | Rejected as the only mode |
| Fully autonomous gates, sync later | Never blocks a driver | Two gates can allocate the same spot during a partition; availability drifts | Not alone |
| **Central authority + local fallback with reconciliation** | Correct when connected, never traps cars when not | Reconciliation logic; possible temporary over-allocation | **Chosen** |
| Per-lot edge service (lot-local brain) | Partition-tolerant per lot; low latency | Per-site deployment/ops footprint | **Chosen for large lots** — natural fit |

#### Stage 2 — Per-lot edge service, central control plane, async settlement

```
  ==== LOT SITE (x500) ============================================
  |                                                                |
  |  [Entry Gates] [Exit Gates] [Kiosks]                           |
  |        |            |          |                              |
  |        +------------+----------+                               |
  |                     v                                          |
  |        +--------------------------------+                      |
  |        |   Lot Edge Service             |                      |
  |        |   - authoritative for THIS lot |                      |
  |        |   - in-memory spot inventory   |                      |
  |        |     + local durable store      |                      |
  |        |   - allocates in < 50ms        |                      |
  |        |   - OFFLINE MODE if uplink dies|                      |
  |        +---------------+----------------+                      |
  ==========================|=======================================
                            | async: gate events, tickets, payments
                            v
              +------------------------------+
              |  Kafka: gate-events          |
              +---------------+--------------+
                              v
        +---------------------------------------------+
        |          Central Control Plane              |
        |  +----------------+  +-------------------+  |
        |  | Ticket Service |  | Pricing Service   |  |
        |  | (system of     |  | (rate cards,      |  |
        |  |  record)       |  |  surge, per-op)   |  |
        |  +--------+-------+  +---------+---------+  |
        |           |                    |            |
        |  +--------+-------+  +---------+---------+  |
        |  | Payment Svc    |  | Availability      |  |
        |  | (idempotent,   |  | aggregator ->     |  |
        |  |  PSP-backed)   |  | Redis -> apps     |  |
        |  +----------------+  +-------------------+  |
        +----------------------+----------------------+
                               v
                    +---------------------------+
                    | Postgres (sharded by      |
                    | operator_id)              |
                    | tickets / payments /      |
                    | spots / rate_cards        |
                    +---------------------------+
```

**Why the edge service is the right call here:** a parking lot is a physical site with a hard latency budget and a catastrophic failure mode (blocked traffic). Pushing allocation authority to the site makes the common path fast (<50ms, no WAN) and the failure path survivable. The central plane owns money, pricing, and cross-lot reporting — things that tolerate seconds of delay.

**Offline mode, concretely:** the gate keeps issuing tickets from a pre-allocated local ID range, records events to local durable storage, and opens the barrier. On reconnect it replays events; the central plane deduplicates by ticket ID. **The honest trade to state:** *"During a partition I may over-allocate — the sign says 5 free, two gates each hand out 5. The recovery is physical: attendants and the fact that lots run below capacity most of the time. I'd rather over-allocate occasionally than trap a driver at a barrier."*

**BREAKING POINT (Stage 2) — payment authorization still doesn't fit the barrier budget.** Card auth is ~2-3s; the barrier budget is 500ms. **Mitigations:** (a) app users pre-authorize on approach so exit is a capture, not an auth; (b) for card-at-exit, open the barrier on *auth initiated* and settle async, accepting a small fraud/failure rate as cheaper than a blocked exit lane; (c) kiosk pre-payment before walking to the car, which removes payment from the barrier path entirely — the industry-standard answer.

### 3.6 Deep Dive: Offline Tolerance and Reconciliation (the riskiest component)

**Why this one:** it's the only place where the physical world makes the usual "fail closed for correctness" answer *wrong*. Everywhere else in these problems, correctness beats availability; here, a wrong-but-open barrier beats a correct-but-closed one.

**The state machine at the gate:**

```
   ONLINE  ---- uplink lost ---->  DEGRADED  ---- uplink restored ---->  RECONCILING
      ^                                |                                     |
      |                                | keeps issuing local tickets,        | replay
      |                                | opens barriers, buffers events      | events,
      +---------------------------------------------------------------------+ dedupe
```

**What makes reconciliation safe:**

| Concern | Mechanism |
|---|---|
| Duplicate tickets after replay | Ticket IDs come from a **pre-allocated per-gate range** (gate-scoped UUID prefix), so IDs never collide and replay is idempotent by primary key |
| Payments during offline | Card auth is impossible offline -> record an **unpaid exit** with plate + timestamp and bill later, or take cash. Never hold the car |
| Availability drift | On reconnect, the edge service sends its **full inventory snapshot**, not just deltas — central state is overwritten by the site's view, because the site is authoritative for physical reality |
| Clock skew across gates | Events carry the gate's monotonic sequence number in addition to wall-clock, so ordering survives skew |

**Options considered for offline ticket identity:**

| Option | Pros | Cons |
|---|---|---|
| Central ID generation | Globally unique, simple | Impossible offline — this is the whole failure being solved |
| Random UUIDv4 at the gate | No coordination needed | No ordering; can't detect gaps or lost events |
| **Pre-allocated per-gate ID ranges** | Offline-capable, collision-free, gaps are detectable | Requires periodic range top-up while online |
| Timestamp + gate ID | Human-readable | Collides under clock skew or a fast burst within the same millisecond |

**Decision:** pre-allocated ranges. And the sentence that lands the section: *"The design principle here is inverted from the rest of the system — everywhere else I'd rather reject than be wrong; at the barrier I'd rather be wrong than reject, because the cost of being wrong is a few dollars of unbilled parking and the cost of rejecting is a car blocking a public road."*

### 3.7 Trade-offs

**Trade-off 1: Edge authority per lot vs fully centralized**
- **Chose:** per-lot edge service, central control plane for money and reporting
- **Gain:** <50ms allocation, and a WAN outage degrades to offline instead of stopping the lot
- **Lose:** 500 deployment targets to operate, patch, and monitor; state exists in two places
- **Failure mode if wrong:** fully centralized means one WAN or region incident simultaneously blocks entry at every lot on the platform — a software outage becomes 500 simultaneous physical traffic incidents, with no way for drivers to "retry later."

**Trade-off 2: Open the barrier on auth-initiated vs wait for settlement**
- **Chose:** open on auth-initiated, settle asynchronously
- **Gain:** exit stays within the latency budget; lanes keep moving at peak
- **Lose:** a small fraction of payments later fail, producing revenue leakage to chase
- **Failure mode if wrong:** waiting for full settlement at ~3s/car means the exit lane backs up during the evening rush, cars queue inside the structure, and the lot deadlocks — a far more expensive failure than the leakage.

**Trade-off 3: Eventually-consistent availability display vs strict counts**
- **Chose:** eventually consistent (a few seconds), served from Redis
- **Gain:** signage and apps read a cheap cached aggregate; no load on the authoritative path
- **Lose:** the sign can briefly say "3 free" when it's 1
- **Failure mode if wrong:** making the display strict puts every signage poll and every app refresh on the authoritative allocation path — the cheap, tolerant read workload now contends with the gate decision that has a 500ms hard budget.

### 3.8 API Design

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/lots/{lotId}/entries` | Gate cert (mTLS) | `{plate, vehicleType, gateId}` + `Idempotency-Key` | `{ticketId, spotId, level, issuedAt}` | 201, **409 lot full (by type)**, 503 -> gate goes offline-mode |
| POST | `/v1/tickets/{id}/quote` | Gate/kiosk/app | `{asOf}` | `{amount, currency, breakdown[]}` | 200, 404 |
| POST | `/v1/tickets/{id}/payments` | Gate/kiosk/app | `{method, amount}` + `Idempotency-Key` | `{paymentId, status}` | 201, 402, 409 |
| POST | `/v1/lots/{lotId}/exits` | Gate cert | `{ticketId, gateId}` | `{allowed, reason?}` | 200, 402 (unpaid), 404 |
| GET | `/v1/lots/{lotId}/availability` | public/app | — | `{byType: {...}, asOf}` | 200 |
| POST | `/v1/lots/{lotId}/reconcile` | Gate cert | `{events[], inventorySnapshot}` | `{accepted, duplicates}` | 200 |

**Two derivation notes:**
- **`Idempotency-Key` on entry and payment is mandatory, not optional** — offline replay guarantees these get retried, and a duplicated payment is a customer-visible incident.
- **`503` on entry is a documented contract**, not just an error: it's the signal that tells the gate to switch to offline mode rather than block the driver.

### 3.9 Data Model

```sql
CREATE TABLE lots (
    lot_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operator_id  UUID NOT NULL,                  -- the tenant
    name         VARCHAR(128),
    timezone     VARCHAR(40) NOT NULL,           -- rates are wall-clock local
    total_spots  INTEGER
);

CREATE TABLE spots (
    spot_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id       UUID NOT NULL REFERENCES lots(lot_id),
    level_number SMALLINT NOT NULL,
    label        VARCHAR(16) NOT NULL,           -- "L2-A14"
    spot_type    VARCHAR(16) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'FREE',
    distance_to_entrance SMALLINT,
    UNIQUE (lot_id, level_number, label)
);

-- The hot query: free spots of a type on a level. Partial index keeps it small.
CREATE INDEX idx_free_spots ON spots (lot_id, level_number, spot_type)
    WHERE status = 'FREE';

CREATE TABLE tickets (
    ticket_id    UUID PRIMARY KEY,               -- gate-generated (offline-safe)
    lot_id       UUID NOT NULL,
    operator_id  UUID NOT NULL,                  -- denormalized for sharding
    spot_id      UUID NOT NULL,
    plate        VARCHAR(16) NOT NULL,
    vehicle_type VARCHAR(16) NOT NULL,
    entry_time   TIMESTAMPTZ NOT NULL,
    exit_time    TIMESTAMPTZ,
    amount_due   NUMERIC(10,2),                  -- NUMERIC, never float
    amount_paid  NUMERIC(10,2),
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    entry_gate   VARCHAR(32),
    issued_offline BOOLEAN DEFAULT FALSE,        -- flags rows needing reconciliation
    created_at   TIMESTAMPTZ DEFAULT now(),

    CHECK (exit_time IS NULL OR exit_time >= entry_time)
) PARTITION BY RANGE (entry_time);               -- 90-day hot, older -> S3

-- Find the active ticket for a car (lost-ticket / plate lookup at exit)
CREATE INDEX idx_active_by_plate ON tickets (lot_id, plate)
    WHERE status = 'ACTIVE';
-- One active ticket per spot: catches double-allocation from a partition
CREATE UNIQUE INDEX idx_one_active_per_spot ON tickets (spot_id)
    WHERE status = 'ACTIVE';

CREATE TABLE payments (
    payment_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id    UUID NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    method       VARCHAR(16) NOT NULL,
    psp_ref      VARCHAR(128),
    idem_key     VARCHAR(128) NOT NULL,
    status       VARCHAR(16) NOT NULL,           -- AUTHORIZED | CAPTURED | FAILED
    created_at   TIMESTAMPTZ DEFAULT now(),

    UNIQUE (idem_key)                            -- replay-safe by construction
);

CREATE TABLE rate_cards (
    rate_card_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operator_id  UUID NOT NULL,
    lot_id       UUID,                           -- NULL = operator-wide default
    vehicle_type VARCHAR(16),                    -- NULL = all types
    strategy     VARCHAR(32) NOT NULL,           -- HOURLY | FLAT | TIERED
    config       JSONB NOT NULL,                 -- {"hourly":3.00,"dailyCap":25.00}
    valid_from   TIMESTAMPTZ NOT NULL,
    valid_to     TIMESTAMPTZ,
    priority     SMALLINT DEFAULT 100
);
```

| Decision | Why | What breaks otherwise |
|---|---|---|
| `NUMERIC(10,2)` for money | Exact decimal arithmetic | `float`/`double` can't represent cents exactly; totals drift and reconciliation fails |
| Gate-generated `ticket_id` (no DB default) | Tickets must be issuable offline, and replay must be idempotent by PK | Central ID generation makes offline operation impossible — the core requirement |
| `UNIQUE ... WHERE status='ACTIVE'` on `spot_id` | The DB catches double-allocation caused by a partition | Two active tickets on one spot go unnoticed until two cars meet in the same space |
| `UNIQUE (idem_key)` on payments | Offline replay *will* retry payments | Duplicate charges — the most customer-visible failure this system can produce |
| `issued_offline` flag | Marks rows needing reconciliation/billing follow-up | Offline tickets look identical to normal ones; revenue leakage is invisible |
| Partial index on `status='FREE'` | The allocation query only cares about free spots | The index carries all 500K spots including permanently occupied ones |
| `rate_cards` with `valid_from/to` + `priority` | Pricing is data with a timeline, not code | Every rate change is a deploy, and you can't schedule an event surge in advance |
| `timezone` on the lot | Rates are wall-clock ("evening rate after 6pm") | Rate boundaries shift by an hour twice a year with DST |

### 3.10 Multi-Tenancy Angle (Salesforce framing)

Be honest that the fit is looser here, then make the real point:

> *"This one has the weakest direct Salesforce product analogue of the problems I'd prep — it's a generic OOD classic. The genuine multi-tenancy story is treating it as a SaaS platform for parking **operators**: `operator_id` is the tenant, shard by it, and every rate card, report, and ticket is scoped to it. The Salesforce-shaped lesson that does transfer is **per-tenant configuration as data rather than code** — rate cards with validity windows and priority are exactly the same pattern as org-level configuration driving behavior without a deploy, which is how Salesforce runs 150K orgs on shared infrastructure."*

Don't manufacture a Platform Events tie-in here. Naming the weak fit honestly is better than a forced one.

---

## 4.  Navigation Pivots — THIS Problem

**Opening Protocol (first 2 minutes — verbatim, per `format.md` Section 2):**

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
> *(If no preference:)* "I'll start with LLD since the classic framing is object design — entities, the allocation and pricing strategies, and concurrency at the gate. Then I'll zoom out to a multi-lot platform, where the interesting problem is offline tolerance. I'll flag the transition."

| Interviewer Says | What They Want | Your Move |
|---|---|---|
| "Design the classes" | Standard LLD open | Derivation table -> three strategies -> diagram. Volunteer *why no `Car`/`Truck` hierarchy* before they ask |
| "How do you find a free spot fast?" | Allocation efficiency | Per-type free lists + counters = O(1); explain why scanning fails at exactly the wrong moment |
| "Two cars, two gates, same instant" | Concurrency | Per-**level** lock covering counter + free-list together; explain why per-lot locking is correct but unusable |
| "Now 500 lots" | HLD scale-out | Per-lot edge service + central control plane; note that throughput is small — the real problems are latency at the barrier and partition tolerance |
| "The network to the lot goes down" | **The best question in this problem** | Offline mode: pre-allocated ticket ID ranges, local durable buffer, open the barrier, reconcile on reconnect — and say why "fail closed" is wrong here specifically |
| "Add EV charging spots with per-kWh billing" | Extensibility both levels | LLD: new `SpotType` + a `PricingStrategy` that reads meter data. HLD: new rate-card config, no schema change |
| "Pricing changes every weekend" | Why pricing is a strategy | Rate cards as data with validity windows and priority — schedule changes ahead of time, no deploy |

---

## 5.  TL;DR — Dual-Level Pitch

At the class level this is three Strategy interfaces — `AllocationStrategy`, `PricingStrategy`, and `PaymentMethod` — over plain entities and enums, with a deliberate refusal to build a `Car`/`Truck` hierarchy since no behavior varies by vehicle, only sizing, which lives in a tightest-fit-first compatibility table so motorcycles don't consume the large spots trucks need. Allocation is O(1) through per-type free lists and counters, and the key concurrency decision is that the counter decrement and free-list pop happen inside one **per-level** lock, so gates bound for different levels never contend and the availability display can't drift from the actual inventory. At the system level, throughput is genuinely small (~33 entries/sec across 500 lots) — the real constraints are a 500ms barrier latency budget and the fact that a network partition physically traps cars, so allocation authority is pushed to a per-lot edge service with a central plane owning money and reporting. That drives the defining trade-off: at the barrier, availability beats correctness — gates keep issuing tickets from pre-allocated ID ranges while offline and reconcile on reconnect, accepting occasional over-allocation because a few dollars of unbilled parking is far cheaper than a car blocking a public road.

---

##  Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created — fifth problem in `Interview/Salesforce/HLD+LLD/`. Grounded in CodingKaro Oct 2025 (*"Design a parking lot on a whiteboard and provide a valid code implementation"* — hence the emphasis on working allocation code). Differentiated from `booking-system.md` by focusing on pricing volatility, allocation efficiency, and offline/partition tolerance rather than repeating interval-overlap concurrency. Salesforce fit flagged honestly as the weakest of the set. |
