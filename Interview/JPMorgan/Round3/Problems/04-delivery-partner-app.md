# Delivery Partner App — JPMC Round 3 (LLD → HLD)

> **JPMC context:** Round 3. Reported pattern: interviewer gives a **basic HLD skeleton
> upfront** and asks you to *improve it and walk it end-to-end*. So your job is not to
> invent from a blank page — it is to spot what the skeleton is missing and fix it out loud.
> LLD-first, then pivots to HLD.
>
> **Why this problem is different from Parking Lot / Payment:** those are guarded by a
> *single hot resource* (a spot, a balance). This one adds a **matching problem** (which
> free partner should get this order?) and a **firehose of location updates** (every
> partner pings GPS every few seconds — that write volume is the thing that breaks naive
> designs). Keep both in your head: **matching correctness** + **location-stream scale.**

---

## Index

| § | Section |
|---|---------|
| §1 | 🎯 Problem Statement |
| §2 | ❓ Clarifying Questions |
| §3a | 🏗️ LLD — Construction Guide (7 Moves) |
| §3b | 🏗️ LLD — Complete Class Diagram |
| §4 | 🧭 Design Decisions |
| §5 | 🔌 Key Interfaces |
| §6 | ⚙️ Code — Three Methods |
| §7 | 🔁 Concurrency |
| §8 | 🧨 Java Depth Probes |
| §9 | 🌐 HLD — 3-Phase Construction Guide |
| §10 | 🏛️ HLD Decisions |
| §11 | 📡 API Design |
| §12 | 🛤️ Happy + Unhappy Paths |
| §13 | ⚠️ Fault Tolerance |
| §14 | 📐 Q&A — Tier-2 JPMC Probes |
| §15 | 🧾 TL;DR |
| §16 | 🔄 Changelog |

---

## §1 — 🎯 Problem Statement

Design the backend for a food/parcel delivery app (Swiggy / DoorDash / Uber Eats style) that:

- Lets a customer **place an order**; the system **matches** it to a nearby available delivery partner
- Ingests **continuous GPS location updates** from every online partner (every few seconds)
- Lets the customer **track the partner live** on a map from pickup to drop-off
- Drives the order through a **lifecycle** (CREATED → MATCHED → PICKED_UP → DELIVERED)
- **Notifies** customer and partner at each state change
- Scales to a city / multi-city fleet — millions of location pings per minute

**The one-line framing to say out loud:**
> *"There are two engines here: a **matching engine** that assigns the best free partner to
> an order, and a **location pipeline** that ingests a firehose of GPS pings and powers both
> matching and live tracking. My design keeps those two concerns separate so the location
> firehose never slows down matching."*

---

## §2 — ❓ Clarifying Questions

**Scope / MVP**

1. Food delivery (restaurant → customer, 3-leg trip) or generic parcel (point A → B)?
   *(food adds a restaurant-prep wait before pickup)*
2. One order per partner at a time, or batched (multiple orders on one trip)?
   *(batching changes the matching algorithm significantly)*
3. Do partners accept/reject an offer, or are they auto-assigned?
   *(accept/reject adds an offer-timeout state)*

**Actors**

4. Actors — customer, delivery partner, restaurant/merchant, dispatch/ops, matching system?

**Scale**

5. How many online partners at peak? Orders per minute at peak?
6. Location ping frequency (every 3s? 5s?) → this drives the write volume, the #1 scale factor.
7. How many customers actively tracking (live map open) at once?

**Consistency / Correctness**

8. Can two orders ever be assigned the same partner? *(No — the partner is a hot resource.)*
9. For the live-track map, is a 3–5s stale location acceptable? *(Almost always yes — this
   lets us relax consistency on the read path.)*

**External Dependencies**

10. Maps/ETA provider (Google Maps Directions API) for route + ETA? Its rate limits/cost?
11. Push provider (FCM/APNs) for notifications?

**Edge Cases**

12. Partner goes offline mid-delivery (dead phone, no signal) — what happens to the order?
13. No partner available in range — do we widen the radius, queue, or reject?

**Non-Functional**

14. Matching latency budget (customer waiting for "partner assigned")? Live-track update cadence?

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

> Rebuild on a whiteboard in ~10 min. Stop at move 7 (~75% visible).
> The stars are the **Order state machine** and the **MatchingStrategy** interface — spend
> your words there.

---

### Move 1 — List Every Domain Noun

Before the board, say: *"Let me separate the nouns the problem gives me directly from the ones that matching + location constraints force me to invent."*

**From the statement directly:** Order, DeliveryPartner, Customer, Restaurant

**Derived from constraints:**
- *"partner is matched to the nearest order — real-time location is what makes matching work"* → **Location** as an immutable value object on `DeliveryPartner` (lat, lng, timestamp — not an entity because every GPS ping is a new immutable point, not an identity you track over time)
- *"two orders dispatched simultaneously must not claim the same partner"* → **PartnerStatus** enum with `AVAILABLE`/`ASSIGNED` as the hot resource state machine (the `assignOrder()` method on `DeliveryPartner` must be the single atomic gate)
- *"the matching algorithm changes — nearest today, surge-aware tomorrow, batched next quarter"* → **MatchingStrategy** interface (pluggable algorithm; swap the strategy without modifying `MatchingEngine`)
- *"we need pickup address + drop address + ETA for live tracking"* → **Route** entity (not just a field on Order — ETA computation logic belongs to Route, not Order)

*Filter rule:* keep nouns that carry state or an invariant.
`Notification`, `MatchingEngine` → service behavior, not data entities.
`ETA` → a field on `Route`, not its own entity.
`Location` (lat, lng, timestamp) → a value object, not an entity with identity.

**Your board at the end of Move 1:**

```
From statement:  Order · DeliveryPartner · Customer · Restaurant
Derived:         Location (value object: lat, lng, timestamp — immutable GPS ping),
                 PartnerStatus enum (AVAILABLE/ASSIGNED — the hot resource state machine),
                 MatchingStrategy (interface — pluggable assignment algorithm),
                 Route (entity: pickup + drop + ETA; owns the ETA computation)
```

---

### Move 2 — Classify: Enums → Value Objects → Entities → Interfaces → Services

```
Board after Move 2:

  ENUMS:         OrderStatus   PartnerStatus   VehicleType
  VALUE OBJECTS: Location (lat, lng, timestamp)
  ENTITIES:      Order   DeliveryPartner   Customer   Restaurant   Route
  INTERFACES:    MatchingStrategy
  SERVICES:      MatchingEngine   LocationService   NotificationService
```

---

### Move 3 — Draw the Enums (the two state machines)

```
Board after Move 3:

  ┌────────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐
  │  OrderStatus           │  │  PartnerStatus       │  │  VehicleType     │
  │  ────────────────────  │  │  ──────────────────  │  │  ─────────────── │
  │  CREATED               │  │  OFFLINE             │  │  BIKE            │
  │  MATCHED               │  │  AVAILABLE   ← free  │  │  SCOOTER         │
  │  PICKED_UP             │  │  ASSIGNED    ← busy  │  │  CAR             │
  │  DELIVERED             │  │  ON_BREAK            │  └──────────────────┘
  │  CANCELLED             │  └──────────────────────┘
  └────────────────────────┘
```

*Say aloud:* two coupled state machines. Assigning an order flips the Order to MATCHED
**and** the Partner from AVAILABLE → ASSIGNED atomically. That coupling is the concurrency
crux — the partner is the hot resource.

---

### Move 4 — Draw the Location VO and DeliveryPartner

```
Board after Move 4:

  ┌────────────────────────┐   ┌──────────────────────────────────────────┐
  │  Location  (VO)        │   │  DeliveryPartner                         │
  │  ────────────────────  │   │  ──────────────────────────────────────  │
  │  lat: double           │   │  partnerId: String                       │
  │  lng: double           │   │  name: String                            │
  │  timestamp: Instant    │   │  vehicleType: VehicleType                │
  │  (immutable)           │   │  status: PartnerStatus   ← HOT           │
  └────────────────────────┘   │  currentLocation: Location               │
                               │  currentOrderId: String  // null = free  │
                               │  + assignOrder(orderId): boolean         │
                               │  + release(): void                       │
                               └──────────────────────────────────────────┘
```

---

### Move 5 — Name the Hot Resource and the Guard

```
Board after Move 5 (annotation on DeliveryPartner):

  ┌──────────────────────────────────────────────────────────┐
  │  DeliveryPartner                                         │
  │  ──────────────────────────────────────────────────────  │
  │  status: PartnerStatus       // AVAILABLE → ASSIGNED ← HOT│
  │  currentOrderId: String      // null = free               │
  │  + assignOrder(orderId): boolean   // synchronized        │
  └──────────────────────────────────────────────────────────┘

  Guard: assignOrder() is synchronized on the partner INSTANCE.
    Two orders racing for the SAME partner → one gets true, one gets false.
    Cross-JVM (multiple matching pods): Redis SET partner:{id}:lock NX PX 15000.

  This is the SAME archetype as ParkingSpot.parkedVehicle and Account.balance —
  a single mutable resource that at most one writer may claim.
```

*SDE-3 signal:* explicitly say "this is the same hot-resource-claim archetype as a parking
spot or a seat — one writer wins, the loser retries another candidate."

---

### Move 6 — Draw Order, Route, Customer

```
Board after Move 6:

  ┌──────────────────────────────────────────┐  ┌────────────────────────────┐
  │  Order                                   │  │  Route                     │
  │  ──────────────────────────────────────  │  │  ────────────────────────  │
  │  orderId: String                         │  │  pickup: Location          │
  │  customerId: String                      │  │  drop: Location            │
  │  restaurantId: String                    │  │  distanceMeters: double    │
  │  partnerId: String     // null until MATCHED│ etaSeconds: long           │
  │  status: OrderStatus                     │  └────────────────────────────┘
  │  route: Route                            │
  │  createdAt: Instant                      │  ┌────────────────────────────┐
  │  + transition(newStatus): void           │  │  Customer                  │
  └──────────────────────────────────────────┘  │  ────────────────────────  │
                                                │  customerId: String        │
                                                │  deliveryLocation: Location │
                                                └────────────────────────────┘
```

---

### Move 7 — Add MatchingStrategy + Services (~75% — stop here)

```
Board after Move 7:

  «interface»
  MatchingStrategy
  ─────────────────────────────────────────────────────
  + selectPartner(order: Order, candidates: List<DeliveryPartner>): Optional<DeliveryPartner>
        △                     △                      △
  NearestPartnerStrategy  BatchedStrategy   SurgeAwareStrategy
  (min distance)          (group orders)    (factor in demand)

  MatchingEngine
  ─────────────────────────────────────────────────────
  strategy: MatchingStrategy          // injected — OCP
  + match(order: Order): Optional<DeliveryPartner>
       // 1. query nearby AVAILABLE partners (geo)
       // 2. strategy.selectPartner(...)
       // 3. attempt atomic assign; retry next candidate on loss

  LocationService                       NotificationService
  ─────────────────────────────         ─────────────────────────────
  + updateLocation(partnerId, loc)       + notify(recipient, event)
  + nearbyAvailable(loc, radius)         (Observer on Order transitions)
```

*Explain the seams:*
- `MatchingStrategy` = Strategy pattern → swap "nearest" for "batched" or "surge-aware"
  without touching `MatchingEngine`. Interviewer loves this — it's the OCP win.
- `LocationService` isolates the GPS firehose from everything else (it's the scale hotspot).
- `NotificationService` = Observer on `Order.transition()` → each state change fans out
  a push without the order logic knowing who's listening.

---

## §3b — 🏗️ LLD — Complete Class Diagram

```
  ┌────────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐
  │  OrderStatus           │  │  PartnerStatus       │  │  VehicleType     │
  │  CREATED               │  │  OFFLINE             │  │  BIKE            │
  │  MATCHED               │  │  AVAILABLE           │  │  SCOOTER         │
  │  PICKED_UP             │  │  ASSIGNED            │  │  CAR             │
  │  DELIVERED             │  │  ON_BREAK            │  └──────────────────┘
  │  CANCELLED             │  └──────────┬───────────┘
  └───────┬────────────────┘             │ status
          │ status                        ▼
          ▼                    ┌──────────────────────────────────────────┐
  ┌──────────────────────────┐│  DeliveryPartner                         │
  │  Order                   ││  ──────────────────────────────────────  │
  │  ────────────────────────││  partnerId: String                       │
  │  orderId: String         ││  vehicleType: VehicleType                │
  │  customerId: String      ││  status: PartnerStatus   ← HOT           │
  │  restaurantId: String    ││  currentLocation: Location               │
  │  partnerId: String  ─────┼┤  currentOrderId: String                  │
  │  status: OrderStatus     ││  + assignOrder(orderId): boolean         │
  │  route: Route            ││  + release(): void                       │
  │  createdAt: Instant      │└──────────────────────────────────────────┘
  │  + transition(status)    │
  └───────┬──────────────────┘   ┌────────────────────────┐  ┌────────────────────┐
          │ has-a                 │  Location  (VO)        │  │  Customer          │
          ▼                       │  lat / lng / timestamp │  │  customerId        │
  ┌────────────────────────┐      │  (immutable)           │  │  deliveryLocation  │
  │  Route                 │      └────────────────────────┘  └────────────────────┘
  │  pickup: Location      │
  │  drop: Location        │      ┌────────────────────────┐
  │  distanceMeters: double│      │  Restaurant            │
  │  etaSeconds: long      │      │  restaurantId          │
  └────────────────────────┘      │  location: Location    │
                                  └────────────────────────┘

  «interface»
  MatchingStrategy
  ──────────────────────────────────────────────────────────────
  + selectPartner(order, candidates: List<DeliveryPartner>): Optional<DeliveryPartner>
        △                     △                      △
  NearestPartnerStrategy   BatchedStrategy    SurgeAwareStrategy

  MatchingEngine                    LocationService              NotificationService
  ──────────────────────────        ─────────────────────        ──────────────────────
  strategy: MatchingStrategy        + updateLocation(id, loc)     + notify(recipient, event)
  + match(order): Optional<...>     + nearbyAvailable(loc, r)     «Observer on Order»
```

---

## §4 — 🧭 Design Decisions

| Decision | Why | Rejected alternative |
|---|---|---|
| **Strategy pattern for matching (`MatchingStrategy`)** | Matching logic changes constantly (nearest → batched → surge-aware). OCP: swap the algorithm without touching `MatchingEngine`. Each strategy is independently testable/A-B-testable. | `if/else` on match mode inside the engine — every new algorithm edits core code; no clean A/B testing |
| **`synchronized` (single-JVM) + Redis `SET NX` (cross-JVM) on partner assign** | Partner is a hot resource — at most one order may claim it. Same archetype as a parking spot. Spot/partner-level granularity means different partners are assigned concurrently. | Global lock on the matching engine — serializes ALL matches citywide; throughput dies |
| **Location updates go to Redis (hot) + a stream, NOT to the primary DB per ping** | A GPS ping every 3s × millions of partners = a firehose. The *current* location is a fast-changing hot value (Redis GEO); durable history streams to Kafka. Writing each ping to MySQL would melt it. | Write every ping to the orders/partners DB — write amplification destroys the DB; 99% of pings are never read individually |
| **Order state machine via `transition()`** | Guards illegal jumps (DELIVERED → CREATED throws). Each transition is the natural hook for notifications (Observer). | Free-form status set — any code sets any status; illegal states corrupt tracking |
| **Live-track reads tolerate 3–5s staleness** | The customer map doesn't need millisecond-fresh GPS. Relaxing read consistency lets us serve tracking from Redis/cache cheaply instead of hammering the write path. | Strong consistency on tracking — needless cost for a feature where "a few seconds old" is invisible to users |
| **`Location` as an immutable value object** | A location reading never mutates; you emit a new one. Immutability makes it safe to pass across threads / cache freely. | Mutable location with setters — shared-mutable-state bugs across the matching and tracking readers |

---

## §5 — 🔌 Key Interfaces

```java
public interface MatchingStrategy {

    Optional<DeliveryPartner> selectPartner(Order order, List<DeliveryPartner> candidates);
}
```

```java
public class NearestPartnerStrategy implements MatchingStrategy {

    @Override
    public Optional<DeliveryPartner> selectPartner(
            Order order,
            List<DeliveryPartner> candidates) {

        Location pickup = order.getRoute().getPickup();

        // pick the candidate with the smallest straight-line distance to pickup
        return candidates.stream()
            .filter(p -> p.getStatus() == PartnerStatus.AVAILABLE)
            .min(Comparator.comparingDouble(
                p -> haversine(p.getCurrentLocation(), pickup)
            ));
    }
}
```

---

## §6 — ⚙️ Code — Three Methods

### Method 1 — `DeliveryPartner.assignOrder()` — the hot-resource guard

**Steps in plain English:**

1. **Acquire the partner-level lock** — `synchronized` on `this` (the partner, not the engine).
2. **Check availability** — if not AVAILABLE, someone already grabbed this partner; return false.
3. **Claim atomically** — flip status to ASSIGNED and set the order id, inside the same lock.

```java
public class DeliveryPartner {

    private PartnerStatus status = PartnerStatus.AVAILABLE;
    private String currentOrderId;

    // Step 1 — lock on THIS partner; different partners lock independently
    public synchronized boolean assignOrder(String orderId) {
        // Step 2 — only an AVAILABLE partner can be claimed
        if (this.status != PartnerStatus.AVAILABLE) {
            return false;
        }
        // Step 3 — claim atomically; no window between check and set
        this.status = PartnerStatus.ASSIGNED;
        this.currentOrderId = orderId;
        return true;
    }

    public synchronized void release() {
        this.status = PartnerStatus.AVAILABLE;
        this.currentOrderId = null;
    }
}
```

> **Same pattern as `ParkingSpot.assignVehicle()`.** If you've told the parking-lot story,
> say "identical archetype" — it shows you see the shared structure across problems, which
> is exactly what SDE-3 pattern-recognition looks like.

---

### Method 2 — `MatchingEngine.match()` — query → strategy → atomic claim → retry

**Steps in plain English:**

1. **Query nearby AVAILABLE partners** from the geo index around the pickup point.
2. **Ask the strategy** to rank/select the best candidate.
3. **Attempt the atomic claim** on that candidate.
4. **On loss (someone else grabbed them), retry** with the next-best candidate.
5. **If no candidate can be claimed**, return empty — caller widens radius or queues.

```java
public class MatchingEngine {

    private final MatchingStrategy strategy;
    private final LocationService locationService;

    public Optional<DeliveryPartner> match(Order order) {
        Location pickup = order.getRoute().getPickup();

        // Step 1 — geo query: AVAILABLE partners within radius of pickup
        List<DeliveryPartner> candidates =
            locationService.nearbyAvailable(pickup, /* radiusMeters */ 3000);

        // loop so a lost race falls through to the next-best partner
        while (!candidates.isEmpty()) {
            // Step 2 — strategy chooses the best candidate
            Optional<DeliveryPartner> chosen =
                strategy.selectPartner(order, candidates);
            if (chosen.isEmpty()) {
                break;
            }
            DeliveryPartner partner = chosen.get();

            // Step 3 — atomic claim (single-JVM guard; add Redis SET NX for multi-pod)
            if (partner.assignOrder(order.getOrderId())) {
                order.setPartnerId(partner.getPartnerId());
                order.transition(OrderStatus.MATCHED);
                return Optional.of(partner);
            }

            // Step 4 — lost the race; drop this partner and try the next best
            candidates.remove(partner);
        }

        // Step 5 — nobody claimable in range
        return Optional.empty();
    }
}
```

> **The retry loop is the whole point.** Under load, two orders often pick the same nearest
> partner. The claim serializes them; the loser doesn't fail the order — it re-ranks the
> remaining candidates and grabs the next-nearest. Graceful degradation, not an error.

---

### Method 3 — `LocationService.updateLocation()` — the firehose handler

**Steps in plain English:**

1. **Update the partner's current location** in the Redis GEO index (fast, overwrites the previous point).
2. **Publish the ping to a Kafka stream** for durable history + downstream consumers (tracking, analytics, ETA).
3. **Do NOT touch the primary DB** on the ping path — that would melt it at firehose volume.

```java
public class LocationService {

    private final GeoRedisClient geo;
    private final KafkaProducer<String, LocationPing> producer;

    public void updateLocation(String partnerId, Location loc) {
        // Step 1 — overwrite current position in the geo index (used by matching)
        geo.geoAdd("partners:online", loc.getLng(), loc.getLat(), partnerId);

        // Step 2 — stream the ping for tracking / history / ETA (async, buffered)
        producer.send(new ProducerRecord<>(
            "partner-locations",
            partnerId,                       // key = partnerId → ordered per partner
            new LocationPing(partnerId, loc)
        ));

        // Step 3 — intentionally NO synchronous DB write here
    }

    public List<DeliveryPartner> nearbyAvailable(Location center, double radiusMeters) {
        // GEOSEARCH returns partnerIds within the radius, nearest first
        return geo.geoSearch("partners:online", center, radiusMeters);
    }
}
```

> **Why key the Kafka message by `partnerId`?** All pings for one partner land on the same
> partition → they stay ordered, so a live-track consumer never shows the partner jumping
> backward in time. Different partners spread across partitions for parallelism.

---

## §7 — 🔁 Concurrency

### Race 1 — two orders, one nearest partner (the assignment race)

```
Two orders created near the same free partner P at the same instant.

Order-A (Pod 1)                    Order-B (Pod 2)
────────────────────────           ────────────────────────
nearbyAvailable() → [P, Q]         nearbyAvailable() → [P, Q]
strategy picks P (nearest)         strategy picks P (nearest)   ← BOTH pick P
P.assignOrder(A)                   P.assignOrder(B)

    synchronized on P ─────────────────────────────────────────┐
    Order-A owns the monitor                                    │
    status == AVAILABLE → assign → true                         │
                                Order-B waits...               │
                                Order-B acquires monitor ───────┘
                                status == ASSIGNED → false
                                → remove P → retry with Q → assigns Q
```

**Single-JVM fix:** `synchronized` on the partner instance. Loser re-ranks and grabs Q.
No dropped order.

**Cross-JVM fix (multiple matching pods):**

```
Before assignOrder() succeeds, the pod must win the Redis lock:

  SET partner:{P}:lock {orderId} NX PX 15000
       │                          │      │
       │                          │      └── auto-release in 15s if pod crashes
       │                          └── only if not already locked
       └── the physical partner

Only the pod that gets OK proceeds to MATCHED. The other re-ranks to Q.
```

**Why PX 15000?** If the winning pod crashes after locking P but before persisting MATCHED,
the lock expires in 15s and P is claimable again — no partner is stuck "ghost-assigned."

### Race 2 — the location firehose is NOT a correctness race, it's a throughput problem

The GPS pings don't need locking — each ping just overwrites the partner's current point
(`geoAdd` is last-writer-wins, which is exactly what "current location" means). The
challenge is *volume*, not *correctness*: solved by writing to Redis + Kafka, never the
primary DB per ping (see §9). **Say this distinction out loud** — knowing which problems
are correctness vs throughput is an SDE-3 signal.

### Why not `synchronized` alone

`synchronized` is single-JVM. Matching runs on many pods; two pods each hold their own
`DeliveryPartner` object copy, so their `synchronized` blocks don't see each other. The
Redis `SET NX` lock is the cross-pod arbiter — same reasoning as parking-lot and payment.

---

## §8 — 🧨 Java Depth Probes

| Question | Answer |
|---|---|
| "Two orders pick the same partner — how does your code handle it?" | `assignOrder()` is `synchronized` on the partner; one wins, the loser's `match()` loop re-ranks and claims the next-nearest. Across pods, a Redis `SET NX PX` lock is the arbiter. Same hot-resource-claim archetype as a parking spot. |
| "You said millions of pings — why not store each in the DB?" | It's a throughput problem, not correctness. Each ping overwrites the current position (last-writer-wins in Redis GEO) and streams to Kafka for history. Per-ping DB writes = write amplification that melts the primary; 99% of pings are never read individually. |
| "Why key the Kafka location topic by `partnerId`?" | Per-partner ordering. Same key → same partition → strictly ordered pings, so a tracking consumer never renders the partner jumping backward. Different partners spread across partitions for parallel throughput. |
| "`ConcurrentHashMap` for the in-memory partner registry?" | Yes for the single-pod cache of partner objects — safe concurrent get/put without a global lock. But it does NOT solve the cross-pod assignment race; that lives in Redis `SET NX`. The map is a local cache, not the source of truth. |
| "Would virtual threads help?" | Yes on the I/O-bound paths — the Maps/ETA API call and push-notification calls are network waits; a virtual thread frees its carrier during the await, so one matching request can fan out ETA lookups cheaply. The geo/matching CPU work gets no benefit. |
| "How do you compute 'nearby' efficiently?" | Redis GEO (`GEOADD`/`GEOSEARCH`), which uses a geohash-indexed sorted set — O(log N + M) to find partners in a radius. A naive scan computing haversine over every online partner is O(N) per order and won't hold at fleet scale. |
| "The Order state machine — enforce it how?" | `transition()` checks an allowed-transitions map and throws on an illegal jump (e.g., DELIVERED → PICKED_UP). Co-locates the rule with the state and gives a single hook to fire notifications (Observer) on each legal change. |

---

## §9 — 🌐 HLD — How to Build This Diagram in the Interview — 3 Phases

> **Remember the JPMC twist:** the interviewer likely draws a rough skeleton first. Treat
> Phase 2 below as "here's the skeleton they'd draw," then Phase 3 is you *improving it* —
> which is exactly what they're testing.

### Phase 1 — Numbers First (≈2 min)

```
Scale assumption: single large metro, expanding to multi-city

  Online partners     100,000 online at peak
  Ping frequency      every 4s → 100,000 / 4 = 25,000 location writes/sec  ← FIREHOSE
  Orders              2M orders/day → peak 3× avg → ~70 orders/sec to match
  Live-track readers  30% of active orders have the map open;
                      say 200,000 concurrent trackers, refresh every 5s
                      → 200,000 / 5 = 40,000 tracking reads/sec
  Storage (locations) 25k/sec × 86,400s × ~50 bytes ≈ 108 GB/day raw pings
                      → stream + downsample; keep 1 point/30s durably, not every ping

DOMINANT FORCE: the 25,000 writes/sec location firehose. Everything else (70 matches/sec)
is small. If the pings hit the primary DB, nothing else survives.

Two forces on the architecture:
  (1) Location firehose (25k writes/sec) → Redis GEO (current) + Kafka (stream), NOT the DB
  (2) Matching + tracking reads          → read from Redis GEO, never scan the fleet
```

---

### Phase 2 — Skeleton: The Simplest System (≈3 min) — "what the interviewer likely drew"

```
── Skeleton: Simplest System That Could Work ──────────────────────

   ┌──────────────────────────────────────────────────┐
   │  Client   Customer App · Partner App              │
   └───────┬──────────────────────────┬────────────────┘
           │ HTTPS (order)            │ HTTPS (GPS ping every 4s)
   ┌───────▼──────────────────────────▼────────────────┐
   │  API Gateway  (auth · routing)                    │
   └──────┬──────────────────────────────┬─────────────┘
          │                              │
   ┌──────▼────────────────┐   ┌─────────▼──────────────────────────┐
   │  OrderService         │   │  LocationService                   │
   │  → MatchingEngine     │   │  updateLocation() per ping          │
   │    (scan all partners)│   │  NotificationService ──▶ Push (FCM) │
   └──────┬────────────────┘   └─────────────────────┬──────────────┘
          │                                          │
   ┌──────▼──────────────────────────────────────────▼────────────┐
   │  MySQL  (orders · partners · locations)                      │
   │  every GPS ping writes a row here                            │
   └───────────────────────────────────────────────────────────────┘

BREAKING POINT — walk this skeleton against the Phase 1 numbers:
  (a) 25,000 GPS pings/sec each INSERT a row into MySQL → write amplification
      melts the DB; order writes and matching starve behind the ping firehose.
  (b) MatchingEngine scans ALL online partners to find "nearby" → O(100,000)
      haversine computations per order at 70 orders/sec; no geo index.
  (c) Live-track (40,000 reads/sec) also hits MySQL for the partner's latest row
      → competes with the same firehose for the connection pool.
  (d) NotificationService (push) is synchronous on the order path → a slow FCM
      call slows matching/response.

══════════════════════════════════════════════════════════════════
```

---

### Phase 3 — Upgrade It: One Fix per Pain Point (≈5 min)

*"Here's how I'd improve the skeleton."* (This sentence is the whole point of the JPMC variant.)

**BREAKING POINT (a) → Location pings go to Redis GEO + Kafka, never the DB per ping**

Each ping does `GEOADD partners:online {lng} {lat} {partnerId}` (overwrites current position,
last-writer-wins) and publishes to Kafka topic `partner-locations` (keyed by partnerId for
per-partner ordering). A downsampling consumer persists ~1 point/30s durably for history.
The primary DB never sees the raw firehose.

**BREAKING POINT (b) → Geo index for matching (`GEOSEARCH`)**

`MatchingEngine` queries `GEOSEARCH partners:online FROMLONLAT {pickup} BYRADIUS 3 km ASC`
→ O(log N + M), returns nearby AVAILABLE partners nearest-first in ~1ms. No fleet scan.
The `SET partner:{id}:lock NX PX 15000` claim (from §7) makes the assignment race safe
across matching pods.

**BREAKING POINT (c) → Live-track reads served from Redis, tolerate 3–5s staleness**

Tracking reads the partner's current point from Redis GEO (or a per-order
`track:{orderId}` cached position pushed by the stream) — 40,000 reads/sec never touch
MySQL. Staleness of a few seconds is invisible on a moving-dot map. Optionally push updates
to the customer app over WebSocket instead of polling.

**BREAKING POINT (d) → Kafka for async notifications (Observer → event)**

On each `Order.transition()`, emit `order.status.changed` to Kafka. NotificationService
consumes and fires the push asynchronously — the order/matching path returns immediately.
At-least-once delivery; the consumer dedups on (orderId, status).

---

```
── Production: All 4 Upgrades Applied ────────────────────────────

   ┌──────────────────────────────────────────────────┐
   │  Client   Customer App · Partner App              │
   └───────┬──────────────────────────┬────────────────┘
           │ HTTPS (order)            │ GPS ping every 4s / WS track
   ┌───────▼──────────────────────────▼────────────────────────────┐
   │  API Gateway  (JWT · rate-limit · TLS · routing)             │
   └──────┬───────────────────────────────────────────┬───────────┘
          │                                           │
   ┌──────▼────────────────────┐   ┌──────────────────▼──────────────────────┐
   │  OrderService             │   │  LocationService                        │
   │  + MatchingEngine         │   │  updateLocation():                      │
   │  1. GEOSEARCH nearby       │   │   1. GEOADD partners:online (current)  │
   │  2. strategy.select        │   │   2. produce → Kafka partner-locations │
   │  3. SET lock NX PX 15000   │   │  TrackService (WS): read track:{order} │
   └──────┬────────────────────┘   └──────────────────┬──────────────────────┘
          │ GEOSEARCH + track reads                    │ GEOADD (current) · SET lock NX
          ▼                                            ▼
   ┌──────────────────────────────────────────────────────────────────────┐
   │  Redis                                                               │
   │  partners:online  → GEO index (lng,lat,partnerId)  ← LocationSvc   │
   │  partner:{id}:lock→ orderId · PX 15000             ← MatchingEng   │
   │  track:{orderId}  → latest partner point · EX 60   ← TrackSvc      │
   └──────────────────────────┬───────────────────────────────────────────┘
                              │ durable writes (matched orders, downsampled locs)
   ┌──────────────────────────▼───────────────────────────────────────────┐
   │  MySQL  (ACID)                                                       │
   │  orders (state machine)                     ← OrderService          │
   │  partners (profile · status of record)      ← OrderService          │
   │  location_history (downsampled ~1/30s)      ← stream consumer       │
   └──────────────────────────────────────────────────────────────────────┘
          ▲ order.status.changed / raw pings
   ┌──────┴─────────────────────────────────────────────────────────────────┐
   │  Kafka                                                                 │
   │  topic partner-locations (key=partnerId) ──▶ TrackConsumer (→track:*)  │
   │                                          └─▶ HistoryConsumer (downsample→DB)│
   │  topic order-events      (key=orderId)   ──▶ NotificationService (push) │
   │                                          └─▶ AnalyticsService           │
   └──────────────────────────────────────────────────────────────────────────┘

KEY INVARIANT: the location firehose (25k writes/sec) is fully absorbed by
  Redis GEO + Kafka and never touches the primary DB — so matching and order
  writes never starve. A partner is claimed by at most one order (SET NX +
  synchronized), and a lost race re-ranks to the next-nearest instead of
  failing the order. Tracking reads tolerate a few seconds of staleness, so
  40k reads/sec are served from Redis, not MySQL.
══════════════════════════════════════════════════════════════════
```

---

## §10 — 🏛️ HLD Decisions

| Component | Why chosen | Rejected + why |
|---|---|---|
| **Redis GEO for current location + matching** | `GEOADD` overwrites the current point (last-writer-wins = "current location"); `GEOSEARCH` is O(log N + M) for nearby queries. Absorbs the 25k writes/sec firehose and powers 1ms matching. | Per-ping MySQL writes + fleet scan — write amplification melts the DB; O(N) haversine scan per order won't hold at 100k partners |
| **Kafka for the location stream** | Durable, ordered-per-partner (key=partnerId) buffer between the firehose and slow consumers (tracking, history, analytics). Decouples ingest rate from processing rate. | Writing pings straight to consumers synchronously — a slow consumer backs up ingest; no replay on consumer failure |
| **Redis `SET NX PX` for partner claim** | Cross-pod arbiter for the assignment race; PX auto-releases a ghost lock if a matching pod crashes. | DB row lock on the partner — holds a connection during matching; contention at peak dispatch |
| **Downsample location history (~1/30s)** | Nobody needs every 4s point forever; 1 point/30s is plenty for dispute/replay and cuts 108 GB/day to a fraction. | Persist every raw ping — storage explosion for data that's read almost never |
| **Kafka for notifications (Observer → event)** | Push (FCM/APNs) latency must not block matching/order response. Async fan-out; dedup on (orderId, status). | Synchronous push on the order path — a slow provider slows every order |
| **Tracking tolerates 3–5s staleness, served from Redis** | A moving-dot map is fine a few seconds stale; lets 40k reads/sec avoid MySQL entirely. | Strongly-consistent tracking from the DB — needless cost + load for an imperceptible freshness gain |

---

## §11 — 📡 API Design

### POST /v1/orders — place an order (triggers matching)

```
POST /v1/orders
X-Idempotency-Key: {uuid}
Authorization: Bearer {jwt}
Content-Type: application/json

{
  "customerId": "cust-123",
  "restaurantId": "rest-77",
  "dropLocation": { "lat": 12.97, "lng": 77.59 }
}

201 Created
{
  "orderId": "ord-7f3a9b",
  "status": "MATCHED",
  "partner": { "partnerId": "prt-42", "name": "Ravi", "etaSeconds": 480 }
}

202 Accepted            ← no partner in range yet; order QUEUED, matching will retry
```

---

### POST /v1/partners/{partnerId}/location — GPS ping (the firehose endpoint)

```
POST /v1/partners/prt-42/location
Authorization: Bearer {partner-jwt}
Content-Type: application/json

{ "lat": 12.9611, "lng": 77.6387, "timestamp": "2026-08-17T09:15:04Z" }

202 Accepted            ← fire-and-forget; goes to Redis GEO + Kafka, not the DB
```

> **Say this:** it returns `202`, not `201` — the ping is accepted for async processing,
> not durably stored on the request path. High-frequency, low-value-individually.

---

### GET /v1/orders/{orderId}/track — live location (or WebSocket)

```
GET /v1/orders/ord-7f3a9b/track
Authorization: Bearer {jwt}

200 OK
{
  "orderId": "ord-7f3a9b",
  "status": "PICKED_UP",
  "partnerLocation": { "lat": 12.9600, "lng": 77.6300 },
  "etaSeconds": 300,
  "asOf": "2026-08-17T09:15:02Z"    ← may be a few seconds stale, by design
}
```

> Preferred at scale: a **WebSocket** channel pushing updates every few seconds instead of
> the client polling — fewer requests, smoother map.

---

## §12 — 🛤️ Happy + Unhappy Paths

### Happy path — order to delivery

```
1. Customer → POST /orders → OrderService creates Order (CREATED), computes Route/ETA.

2. MatchingEngine.match(order):
   a. GEOSEARCH partners:online around pickup → [prt-42, prt-88, ...] nearest-first.
   b. NearestPartnerStrategy picks prt-42.
   c. SET partner:prt-42:lock ord-7f3a9b NX PX 15000 → OK.
   d. prt-42.assignOrder() → true → Order.transition(MATCHED).

3. Emit order-events(MATCHED) → NotificationService pushes "Ravi is on the way" to customer
   and "New order" to partner.

4. Partner app pings GPS every 4s → LocationService → GEOADD + Kafka.
   TrackConsumer updates track:ord-7f3a9b → customer's live map moves.

5. Partner reaches restaurant → PICKED_UP; reaches customer → DELIVERED.
   Each transition emits an order-event → push notification + analytics.

6. On DELIVERED: partner.release() → status AVAILABLE → eligible for the next match.
```

---

### Unhappy path 1 — two orders, one nearest partner (assignment race)

```
Order-A and Order-B both GEOSEARCH and both pick prt-42.

Pod 1: SET partner:prt-42:lock A NX PX 15000 → OK   → A gets prt-42.
Pod 2: SET partner:prt-42:lock B NX PX 15000 → nil  → re-rank → picks prt-88 → OK.

Both orders matched; no partner double-booked; neither order failed.
```

---

### Unhappy path 2 — no partner available in range

```
GEOSEARCH within 3 km → empty (late night / surge).

Fallback ladder:
  a. Widen radius: retry at 5 km, then 8 km (bounded).
  b. Still empty → Order stays CREATED, placed on a retry queue; matching re-runs
     every few seconds as partners come online / free up.
  c. Customer sees "finding a partner…" (202 Accepted). Timeout after N minutes →
     offer cancel/refund.
```

---

### Unhappy path 3 — partner goes offline mid-delivery

```
Assigned partner stops pinging (dead phone / no signal) for > threshold (e.g., 60s).

Detection: a watchdog sees no GEOADD / no Kafka pings for prt-42 beyond threshold.
Action:
  a. Mark partner stale; if order not yet PICKED_UP → release lock, re-match to another partner.
  b. If already PICKED_UP → alert ops (can't just reassign a picked-up order);
     attempt reconnect; customer sees "last known location, updating…".
  c. Lock PX 15000 also auto-expires so a crashed matching pod never ghost-holds the partner.
```

---

### Unhappy path 4 — matching pod crashes right after locking the partner

```
Pod 1: SET partner:prt-42:lock A NX PX 15000 → OK
       [POD CRASHES before Order.transition(MATCHED) persists.]

Outcome:
  Redis lock expires in 15s → prt-42 claimable again.
  Order A still CREATED (never persisted MATCHED) → retry queue re-matches it.
  No partner stuck ASSIGNED; no order lost.
```

---

## §13 — ⚠️ Fault Tolerance

| External call | Timeout | Retry policy | Fallback |
|---|---|---|---|
| **Maps/ETA API (Google Directions)** | 2s | 1 retry | Fall back to haversine straight-line distance × a road-factor for ETA; refine when the API recovers |
| **Push provider (FCM/APNs)** | 5s | 3× via Kafka consumer retry; DLQ | Notification delayed, not lost; order state is already durable in MySQL — no rollback |
| **Redis (GEO / lock / track)** | 50ms | 1 immediate retry | Matching degrades to a bounded DB query on last-known partner locations (slower); tracking shows last-known point |
| **Kafka (location / order events)** | 5s | producer retries; DLQ | Use a DB outbox for order-events so a Kafka outage never loses a state-change notification; raw pings are best-effort (a dropped ping is replaced by the next one 4s later) |
| **MySQL (order/partner writes)** | 5s | 1 retry, new connection | Fail-fast 503 on the order write; do not report MATCHED unless persisted; circuit breaker after 5 consecutive failures |

> **The load-shedding rule:** if the system is overwhelmed, drop *location pings* first
> (the next ping is 4s away and replaces it) — never drop *order state transitions*
> (those are durable and money-adjacent). Knowing which traffic is sheddable is an SDE-3 signal.

---

## §14 — 📐 Q&A — Tier-2 JPMC Probes

**Q: The interviewer's skeleton wrote every GPS ping to the DB. What's wrong and how do you fix it?**

> At 25,000 pings/sec that's a write firehose that melts the primary DB and starves order
> writes and matching for the connection pool. Fix: pings go to Redis GEO (`GEOADD`,
> last-writer-wins = current position) and to a Kafka stream; a downsampling consumer
> persists ~1 point/30s for history. The DB never sees the raw firehose. That single change
> is the biggest improvement to the skeleton.

**Q: How does matching stay fast with 100,000 online partners?**

> Redis GEO. `GEOSEARCH ... BYRADIUS` is O(log N + M) using a geohash-indexed sorted set —
> nearby partners in ~1ms. The naive skeleton scanned all partners computing haversine, which
> is O(N) per order and collapses at fleet scale. The assignment race across matching pods is
> handled by a `SET partner:{id}:lock NX PX` claim, with a re-rank on loss.

**Q: A customer's live map and the partner's real position differ by a few seconds. Is that a bug?**

> No — it's a deliberate consistency relaxation. A moving-dot map is fine 3–5s stale, and
> that lets me serve 40,000 tracking reads/sec from Redis instead of hammering the DB for
> strong consistency nobody can perceive. I'd only tighten it if we needed exact geofencing
> for, say, an arrival trigger — and even then, on the server side, not the map.

**Q: Under overload, what do you shed first?**

> Location pings — the next ping is 4s away and overwrites anyway, so a dropped one is
> self-healing. I never shed order state transitions (CREATED/MATCHED/DELIVERED) — those
> are durable, user-visible, and money-adjacent. Rate-limit pings at the gateway before
> touching anything on the order path.

---

## §15 — 🧾 TL;DR

**The one sentence:** *Two engines — a **matching engine** (assign the best free partner,
guarded like any hot resource) and a **location pipeline** (absorb the GPS firehose in
Redis GEO + Kafka, never the DB) — kept separate so the firehose never slows matching.*

**Entities:** `Order (state machine) · DeliveryPartner (status, HOT) · Route · Location
(VO) · Customer · Restaurant`; `MatchingStrategy` interface (Strategy pattern).

**Concurrency:**
- Assignment race → `synchronized` on the partner + Redis `SET NX PX` cross-pod; loser
  re-ranks to the next-nearest (graceful, not a failed order). Same archetype as parking spot.
- Location firehose → a **throughput** problem, not correctness; `GEOADD` is last-writer-wins.

**HLD shape:**
- `LocationService` → Redis GEO (`GEOADD` current) + Kafka `partner-locations` (key=partnerId); **never the DB per ping**
- `MatchingEngine` → `GEOSEARCH` nearby → strategy → `SET NX` claim → MATCHED
- `TrackService` → Redis `track:{orderId}` (3–5s stale OK), pushed over WebSocket
- MySQL holds orders + partner profiles + **downsampled** location history (~1/30s)
- Kafka `order-events` → NotificationService (push) + AnalyticsService

**SDE-3 signals to surface proactively:**
- Separate correctness (matching) from throughput (location firehose) explicitly.
- The firehose must bypass the primary DB — this is the single biggest fix to the given skeleton.
- Relax tracking consistency (3–5s stale) to kill 40k reads/sec of DB load.
- Under overload, shed pings, never order transitions.

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created. Full 16-section solution for Delivery Partner App — Tier-1 JPMC Round 3 problem (⭐⭐⭐), LLD-first with the interviewer-gives-a-skeleton twist (Phase 2 framed as "what they drew," Phase 3 as "how I improve it"). LLD: Order + Partner dual state machines, Location value object, MatchingStrategy interface (Strategy pattern), MatchingEngine with query→select→atomic-claim→retry loop. Concurrency: synchronized + Redis SET NX for the partner assignment race (same hot-resource archetype as parking spot / payment balance), plus the explicit correctness-vs-throughput distinction for the location firehose. HLD: 3-phase Confluent construction guide; the 25k-pings/sec firehose bypassing the primary DB (Redis GEO + Kafka) is the central insight; downsampled history; tracking served stale from Redis; Kafka Observer→event notifications; load-shedding rule (shed pings, never order transitions). |
