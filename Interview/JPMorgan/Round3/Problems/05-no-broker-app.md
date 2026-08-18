# No Broker App — JPMC Round 3 (HLD-leaning, LLD + HLD)

> **JPMC context:** Round 3, Jan 2026 Cohort SuperDay (Glassdoor). A marketplace-style
> system: property owners **list** flats; seekers **search** by location + filters and
> **book a visit**. Reported as HLD-focused, but expect the LLD entity/relationship pass first.
>
> **Why this problem is different from the earlier four:** Parking Lot / Movie / Payment /
> Delivery are **write-and-claim** systems (grab a spot, move money, assign a partner). This
> one is a **search-and-browse marketplace** — the dominant load is *reads* (faceted search:
> "2BHK, under ₹30k, near Koramangala, pet-friendly"). The star of the design is a **search
> index**, not a lock. There is still one small hot resource — a **visit slot** — so the
> concurrency story is the same familiar archetype, just smaller.
>
> **The two-sided-marketplace framing to say out loud:**
> *"This is a two-sided marketplace. The **supply side** (owners) writes listings; the
> **demand side** (seekers) does heavy faceted search and books visits. Those two sides have
> opposite workloads — write-light/read-heavy — so I separate the write store (source of
> truth) from a search index (read-optimized), and keep them in sync."*

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

Design a broker-free real-estate rental/sale platform where:

- **Owners** create **listings** (flat details, rent, location, photos, amenities)
- **Seekers** **search** listings by location + rich filters (BHK, budget, furnishing,
  pet-friendly, availability date) and sort by relevance/price/recency
- Seekers **book a property visit** in one of the owner's available time slots
- Owners **confirm/reject** visits; both sides get **notifications**
- A listing moves through a **lifecycle** (DRAFT → ACTIVE → RENTED/SOLD → INACTIVE)
- Scales to millions of listings and a read-heavy search load across a city/country

**The one-line framing to say out loud:**
> *"Two workloads pulling in opposite directions: write-light listing management on the
> supply side, and read-heavy faceted search on the demand side. I split the write store
> (MySQL, source of truth) from a search index (Elasticsearch, read-optimized) and sync them
> via change events. The only real contention is a visit slot — one seeker per slot."*

---

## §2 — ❓ Clarifying Questions

**Scope / MVP**

1. Rentals, resale, or both? *(resale adds price negotiation + heavier verification)*
2. Is booking a **visit** (schedule a viewing) or an actual **rental transaction** (money)?
   *(the report says visit-booking — no payment rails, which simplifies a lot)*
3. Do we need owner/listing verification (KYC, ownership proof) in scope?

**Actors**

4. Actors — property owner, seeker, admin/moderation, notification system?

**Scale**

5. Total active listings? Searches/sec at peak vs listings created/sec?
   *(this confirms read-heavy and sizes the search index)*
6. Geographic spread — one city or country-wide? *(drives geo-search + sharding)*

**Search behavior (the core of this problem)**

7. Which filters must be supported (BHK, budget, furnishing, amenities, availability date,
   distance-from-a-point)? Which are the *common* ones (to index/cache first)?
8. Is search relevance ranking needed, or is sort-by-price/recency enough for MVP?
9. Is slightly stale search acceptable (a just-rented flat showing for a few seconds)?
   *(almost always yes — lets the index lag the write store)*

**Consistency / Correctness**

10. Can two seekers book the same visit slot? *(No — the slot is a hot resource.)*
11. When a listing is marked RENTED, must it vanish from search instantly or is a short lag ok?

**Non-Functional**

12. Search latency budget (<300ms)? Photo storage/CDN expectations?

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

> Rebuild on a whiteboard in ~10 min. Stop at move 7 (~75% visible).
> The stars are the **SearchFilter composition** and the two **state machines**
> (Listing + VisitBooking). Spend your words there.

---

### Move 1 — List Every Domain Noun

Before the board, say: *"Let me separate the nouns the problem gives me directly from the ones that search + booking constraints force me to invent."*

**From the statement directly:** User (Owner / Seeker), Listing, Address/Location, Photo, Amenity, VisitBooking

**Derived from constraints:**
- *"search must support composable filters simultaneously — BHK AND budget AND location AND pet-friendly"* → **SearchFilterSpec** interface (Specification pattern — each filter is an independent predicate, composable with `and()`; adding a new filter criterion means a new class, not an edit to `SearchService`)
- *"results can be sorted by relevance, price, or recency — and that may change per A/B experiment"* → **RankingStrategy** interface (the ranking algorithm varies independently of search execution; swap without touching `SearchService`)
- *"a visit must be scheduled at a specific time with a specific owner"* → **TimeSlot** entity (not just a field on Listing — a Listing has many slots, each with its own `AVAILABLE`/`BOOKED` status and `bookedBy` field)
- *"two seekers can hit 'book visit' on the same slot simultaneously"* → **TimeSlot.bookedBy** is the hot resource (the single field that exactly one seeker can win; `TimeSlot.book()` must be atomic)

*Filter rule:* keep nouns with state or an invariant.
`Notification` → service behavior. `Photo` → a URL field/list on Listing (bytes live in
object storage, not the DB). `Amenity` → an enum/tag set on Listing. `Property` vs `Listing`:
a Listing is a Property *offered* with a price + status — merge into `Listing` for MVP.

**Your board at the end of Move 1:**

```
From statement:  User(Owner/Seeker) · Listing · Address/Location · Photo · Amenity · VisitBooking
Derived:         SearchFilterSpec (interface — composable filter predicates),
                 RankingStrategy (interface — pluggable ranking algorithm),
                 TimeSlot (entity: available slot on a Listing; .bookedBy is the hot resource)
```

---

### Move 2 — Classify: Enums → Value Objects → Entities → Interfaces → Services

```
Board after Move 2:

  ENUMS:         ListingStatus   VisitStatus   BHKType   FurnishingType
  VALUE OBJECTS: Location (lat, lng)   Money (rent)   SearchFilter (criteria)
  ENTITIES:      User   Listing   TimeSlot   VisitBooking
  INTERFACES:    SearchFilterSpec (composable predicate)   RankingStrategy
  SERVICES:      ListingService   SearchService   BookingService   NotificationService
```

---

### Move 3 — Draw the Enums (the two state machines up front)

```
Board after Move 3:

  ┌────────────────────┐  ┌──────────────────────┐  ┌────────────┐ ┌───────────────┐
  │  ListingStatus     │  │  VisitStatus         │  │  BHKType   │ │ FurnishingType│
  │  ────────────────  │  │  ──────────────────  │  │  ────────  │ │  ───────────  │
  │  DRAFT             │  │  REQUESTED           │  │  ONE_BHK   │ │  FULL         │
  │  ACTIVE  ← search  │  │  CONFIRMED           │  │  TWO_BHK   │ │  SEMI         │
  │  RENTED            │  │  COMPLETED           │  │  THREE_BHK │ │  NONE         │
  │  INACTIVE          │  │  CANCELLED           │  │  FOUR_PLUS │ └───────────────┘
  └────────────────────┘  │  REJECTED            │  └────────────┘
                          └──────────────────────┘
```

*Say aloud:* only `ACTIVE` listings appear in search. Flipping to `RENTED` must remove it
from the index — that's a write→index sync event, not a search-time filter (cheaper to keep
the index clean than to filter dead rows on every query).

---

### Move 4 — Draw the Value Objects and TimeSlot

```
Board after Move 4:

  ┌────────────────────┐  ┌────────────────────┐  ┌──────────────────────────────┐
  │  Location  (VO)    │  │  SearchFilter (VO) │  │  TimeSlot                    │
  │  ────────────────  │  │  ────────────────  │  │  ──────────────────────────  │
  │  lat: double       │  │  bhk: Set<BHKType> │  │  slotId: String              │
  │  lng: double       │  │  minRent/maxRent   │  │  listingId: String           │
  │  locality: String  │  │  furnishing        │  │  startTime: Instant          │
  │  (immutable)       │  │  amenities: Set<>  │  │  endTime: Instant            │
  └────────────────────┘  │  center + radiusKm │  │  bookedBy: String ← HOT      │
                          │  availableFrom     │  │  + book(seekerId): boolean   │
                          └────────────────────┘  │  + release(): void           │
                                                  └──────────────────────────────┘
```

---

### Move 5 — Name the (small) Hot Resource + the Guard

```
Board after Move 5 (annotation on TimeSlot):

  ┌──────────────────────────────────────────────────────────┐
  │  TimeSlot                                                │
  │  ──────────────────────────────────────────────────────  │
  │  bookedBy: String       // null = free   ← HOT           │
  │  + book(seekerId): boolean   // synchronized             │
  └──────────────────────────────────────────────────────────┘

  Guard: book() is synchronized on the TimeSlot INSTANCE.
    Two seekers racing for the same slot → one true, one false.
    Cross-JVM: Redis SET slot:{slotId}:lock {seekerId} NX PX 30000
               OR a DB UNIQUE constraint on (slotId) in the bookings table.

  SAME archetype as ParkingSpot / Seat / DeliveryPartner —
  one mutable resource, at most one writer wins. The marketplace is huge,
  but the contention surface is tiny: only visit slots.
```

*SDE-3 signal:* explicitly say *"the search side has no write contention at all — the only
lock in the whole system is on a visit slot, and it's the same hot-resource pattern as the
other problems."* Showing you know **where** contention is (and isn't) is the mark.

---

### Move 6 — Draw User, Listing, VisitBooking

```
Board after Move 6:

  ┌──────────────────────┐   ┌──────────────────────────────────────────┐
  │  User                │   │  Listing                                 │
  │  ──────────────────  │   │  ──────────────────────────────────────  │
  │  userId: String      │   │  listingId: String                       │
  │  name / phone        │   │  ownerId: String                         │
  │  role: OWNER|SEEKER  │   │  bhk: BHKType                            │
  └──────────────────────┘   │  rent: Money                             │
                             │  furnishing: FurnishingType              │
  ┌──────────────────────┐   │  amenities: Set<Amenity>                 │
  │  VisitBooking        │   │  location: Location                      │
  │  ──────────────────  │   │  photoUrls: List<String>                 │
  │  bookingId: String   │   │  availableFrom: LocalDate                │
  │  slotId: String      │   │  status: ListingStatus                   │
  │  listingId: String   │   │  + transition(newStatus): void           │
  │  seekerId: String    │   └──────────────────────────────────────────┘
  │  status: VisitStatus │
  │  + transition(status)│
  └──────────────────────┘
```

---

### Move 7 — Add SearchFilterSpec + RankingStrategy + Services (~75% — stop)

```
Board after Move 7:

  «interface»  SearchFilterSpec        // Composite/Specification pattern
  ─────────────────────────────────────────────────────
  + matches(listing: Listing): boolean
  + and(other: SearchFilterSpec): SearchFilterSpec
        △            △              △
  BhkSpec   RentRangeSpec   GeoRadiusSpec   ...composable predicates

  «interface»  RankingStrategy         // Strategy pattern
  ─────────────────────────────────────────────────────
  + rank(results: List<Listing>, query: SearchQuery): List<Listing>
        △                 △
  RelevanceRanking   PriceRanking   RecencyRanking

  ListingService        SearchService              BookingService
  ────────────────      ─────────────────────      ──────────────────────
  + create(listing)     + search(filter): Page      + requestVisit(slotId, seeker)
  + transition(...)       (queries the index)         (atomic slot claim)
    → emits change       + ranks via strategy         + confirm/reject
      event to index
```

*Explain the seams:*
- `SearchFilterSpec` = Specification/Composite → each filter (BHK, rent, geo, amenities) is
  an independent, composable predicate; new filters plug in without touching search core (OCP).
- `RankingStrategy` = Strategy → swap relevance/price/recency ranking freely / A-B test.
- `ListingService` writes to the source-of-truth DB **and emits a change event** so the
  search index stays in sync — this decoupling is the heart of the HLD.

---

## §3b — 🏗️ LLD — Complete Class Diagram

```
  ┌────────────────────┐  ┌──────────────────────┐  ┌────────────┐ ┌───────────────┐
  │  ListingStatus     │  │  VisitStatus         │  │  BHKType   │ │ FurnishingType│
  │  DRAFT             │  │  REQUESTED           │  │  ONE_BHK   │ │  FULL / SEMI  │
  │  ACTIVE            │  │  CONFIRMED           │  │  TWO_BHK   │ │  NONE         │
  │  RENTED            │  │  COMPLETED           │  │  THREE_BHK │ └───────────────┘
  │  INACTIVE          │  │  CANCELLED / REJECTED│  │  FOUR_PLUS │
  └───────┬────────────┘  └──────────┬───────────┘  └────────────┘
          │ status                    │ status
          ▼                           ▼
  ┌──────────────────────────────────────────┐   ┌──────────────────────┐
  │  Listing                                 │   │  VisitBooking        │
  │  ──────────────────────────────────────  │   │  ──────────────────  │
  │  listingId: String                       │   │  bookingId: String   │
  │  ownerId: String                         │◀──│  listingId: String   │
  │  bhk: BHKType                            │   │  slotId: String      │
  │  rent: Money                             │   │  seekerId: String    │
  │  furnishing: FurnishingType              │   │  status: VisitStatus │
  │  amenities: Set<Amenity>                 │   │  + transition(status)│
  │  location: Location  (VO)                │   └──────────┬───────────┘
  │  photoUrls: List<String>  (→ CDN/S3)     │              │ books
  │  availableFrom: LocalDate                │   ┌──────────▼───────────────────┐
  │  status: ListingStatus                   │   │  TimeSlot                    │
  │  + transition(newStatus): void           │   │  slotId / listingId          │
  └───────────────┬──────────────────────────┘   │  startTime / endTime         │
                  │ owned by                       │  bookedBy: String ← HOT      │
        ┌─────────▼──────────┐                     │  + book(seekerId): boolean   │
        │  User              │                     │  + release(): void           │
        │  userId / role     │                     └──────────────────────────────┘
        └────────────────────┘

  ┌────────────────────┐  ┌────────────────────┐
  │  Location  (VO)    │  │  SearchFilter (VO) │
  │  lat / lng         │  │  bhk / rent range  │
  │  locality          │  │  furnishing        │
  │  (immutable)       │  │  amenities         │
  └────────────────────┘  │  center + radiusKm │
                          │  availableFrom     │
                          └────────────────────┘

  «interface» SearchFilterSpec               «interface» RankingStrategy
  ────────────────────────────────           ────────────────────────────────
  + matches(listing): boolean                 + rank(results, query): List<Listing>
  + and(other): SearchFilterSpec                   △            △
       △          △          △              RelevanceRanking  PriceRanking  RecencyRanking
  BhkSpec  RentRangeSpec  GeoRadiusSpec

  ListingService          SearchService              BookingService
  ────────────────        ─────────────────────      ──────────────────────
  + create(listing)       + search(filter): Page      + requestVisit(slotId, seeker)
  + transition(...)       + rank via strategy          + confirm(bookingId)
    → emit change event                                + reject(bookingId)
```

---

## §4 — 🧭 Design Decisions

| Decision | Why | Rejected alternative |
|---|---|---|
| **Split write store (MySQL) from search index (Elasticsearch)** | Opposite workloads: listing writes are light + need ACID/consistency; search is heavy + needs inverted indexes, faceting, geo, relevance. One store can't be great at both. Sync via change events. | Search directly on MySQL with `LIKE`/multi-column filters — full-table scans, no relevance, no faceting; dies under read load |
| **Specification/Composite pattern for filters (`SearchFilterSpec`)** | Filters combine arbitrarily (BHK AND rent AND geo AND amenities). Each is an independent, testable predicate; new filters plug in without editing search core (OCP). | One giant `if/else` building a query string — unmaintainable; every new filter risks breaking existing ones |
| **Strategy pattern for ranking (`RankingStrategy`)** | Relevance vs price vs recency are swappable/A-B-testable without touching search flow. | Hard-coded sort — no experimentation; product can't tune ranking |
| **`RENTED` removes the listing from the index (event), not a search-time filter** | Keeps the index clean and queries fast; a dead listing shouldn't cost every query a filter check. Eventual (few-seconds) removal is acceptable. | Filter out non-ACTIVE at query time — every search pays to skip dead rows; index bloats with stale data |
| **Visit-slot claim: `synchronized` + Redis `SET NX` / DB `UNIQUE(slotId)`** | The only contention in the system. Same hot-resource archetype; a unique constraint on the booking row is the cheapest durable guarantee. | Global booking lock — needless serialization; there's no contention across *different* slots |
| **Search tolerates a few seconds of staleness** | A just-rented flat lingering for seconds is harmless and lets the index lag the write store — massively simpler + cheaper than synchronous index updates. | Synchronous index write inside the listing transaction — couples the write path to ES availability; slows every listing write |
| **Photos in object storage + CDN, URLs in the row** | Bytes are large and static; the DB stores only URLs. CDN serves images cheaply at the edge. | Photo bytes in the DB — bloats rows, wrecks backups, no edge caching |

---

## §5 — 🔌 Key Interfaces

```java
public interface SearchFilterSpec {

    boolean matches(Listing listing);

    // compose two specs into one (Composite pattern)
    default SearchFilterSpec and(SearchFilterSpec other) {
        return listing -> this.matches(listing) && other.matches(listing);
    }
}
```

```java
public class RentRangeSpec implements SearchFilterSpec {

    private final Money min;
    private final Money max;

    public RentRangeSpec(Money min, Money max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean matches(Listing listing) {
        Money rent = listing.getRent();
        // inclusive range check on the listing's rent
        return rent.gte(min) && rent.lte(max);
    }
}
```

```java
public interface RankingStrategy {

    List<Listing> rank(List<Listing> results, SearchQuery query);
}
```

> **Note for the interview:** in production the `SearchFilterSpec` predicates are *translated
> into an Elasticsearch query* (a bool query with `filter` clauses), not run in Java over
> every listing. The interface models the composition cleanly; the SearchService compiles it
> to an ES query. Say this — it shows you know the pattern is a modeling tool, not the runtime.

---

## §6 — ⚙️ Code — Three Methods

### Method 1 — `TimeSlot.book()` — the (only) hot-resource guard

**Steps in plain English:**

1. **Acquire the slot-level lock** — `synchronized` on `this` (the slot, not the service).
2. **Check availability** — if `bookedBy != null`, someone already took it; return false.
3. **Claim atomically** — set `bookedBy = seekerId` inside the same lock; return true.

```java
public class TimeSlot {

    private String bookedBy;   // null = free

    // Step 1 — lock on THIS slot; different slots lock independently
    public synchronized boolean book(String seekerId) {
        // Step 2 — already taken?
        if (this.bookedBy != null) {
            return false;
        }
        // Step 3 — claim atomically; no window between check and set
        this.bookedBy = seekerId;
        return true;
    }

    public synchronized void release() {
        this.bookedBy = null;
    }
}
```

> **Identical to `ParkingSpot.assignVehicle()` / `DeliveryPartner.assignOrder()`.** Say
> "same hot-resource archetype" — the durable cross-pod version is a `UNIQUE(slot_id)`
> constraint on the `visit_bookings` table (the DB is the referee), or Redis `SET NX`.

---

### Method 2 — `SearchService.search()` — compose filters → query index → rank

**Steps in plain English:**

1. **Build the composite filter spec** from the request criteria (BHK, rent, geo, amenities).
2. **Query the search index** (Elasticsearch) with the compiled filter — never scan MySQL.
3. **Rank** the results via the injected `RankingStrategy`.
4. **Paginate** with a cursor and return the page.

```java
public class SearchService {

    private final SearchIndexClient index;   // Elasticsearch client
    private final RankingStrategy ranking;

    public Page<Listing> search(SearchRequest req) {
        // Step 1 — compose independent predicates into one spec
        SearchFilterSpec spec = new BhkSpec(req.getBhk())
            .and(new RentRangeSpec(req.getMinRent(), req.getMaxRent()))
            .and(new GeoRadiusSpec(req.getCenter(), req.getRadiusKm()))
            .and(new AmenitySpec(req.getAmenities()));

        // Step 2 — compile the spec to an ES query; only ACTIVE listings are indexed
        List<Listing> matches = index.query(spec, req.getCursor(), req.getPageSize());

        // Step 3 — apply the ranking strategy (relevance / price / recency)
        List<Listing> ranked = ranking.rank(matches, req.toQuery());

        // Step 4 — cursor-based page (stable under inserts, O(log N) seek)
        return Page.of(ranked, nextCursor(ranked));
    }
}
```

> **Cursor, not offset.** Offset pagination (`LIMIT 20 OFFSET 10000`) re-scans skipped rows
> and shifts when new listings arrive. A cursor (`search_after` in ES) seeks directly and is
> stable — the SDE-3 pagination answer.

---

### Method 3 — `ListingService.transition()` — write + emit the index-sync event

**Steps in plain English:**

1. **Validate + apply the state transition** on the listing (state machine guard).
2. **Persist to the source-of-truth DB** (MySQL) in a transaction.
3. **Emit a change event** (e.g., `listing.rented`) so the search index updates asynchronously — including *removing* the listing from the index when it leaves ACTIVE.

```java
public class ListingService {

    private final ListingRepository repo;
    private final EventPublisher events;   // → Kafka

    @Transactional
    public void transition(String listingId, ListingStatus newStatus) {
        Listing listing = repo.findById(listingId)
            .orElseThrow(() -> new ListingNotFoundException(listingId));

        // Step 1 — state machine guard (e.g., RENTED → ACTIVE is illegal)
        listing.transition(newStatus);

        // Step 2 — persist to the write store (source of truth)
        repo.save(listing);

        // Step 3 — emit change event so the index stays in sync (async)
        //          ACTIVE → index upsert; RENTED/INACTIVE → index delete
        events.publish(new ListingChangedEvent(listingId, newStatus));
    }
}
```

> **Why emit an event instead of writing to ES here?** Decoupling. If ES is briefly down,
> listing writes still succeed; the indexer catches up from the event log. The write path
> never depends on search-index availability. This is the Change-Data-Capture / outbox idea.

---

## §7 — 🔁 Concurrency

### The only real race — two seekers, one visit slot

```
Two seekers try to book the same slot S for the same flat at the same instant.

Seeker-A (Pod 1)                   Seeker-B (Pod 2)
────────────────────────           ────────────────────────
requestVisit(S, A)                 requestVisit(S, B)
  INSERT visit_bookings            INSERT visit_bookings
    (slot_id = S, seeker = A) ───┐   (slot_id = S, seeker = B)
    UNIQUE(slot_id) → OK         │   UNIQUE(slot_id) → CONSTRAINT VIOLATION
    slot → CONFIRMED/REQUESTED   │   catch → return "slot just taken, pick another"
                                 │
        The DB UNIQUE(slot_id) constraint is the referee. One INSERT wins.
```

**Fix (durable, cross-pod):** a `UNIQUE(slot_id)` constraint on the `visit_bookings` table —
the database serializes the two inserts for free; the loser catches the violation and is
offered another slot. (In-JVM, `synchronized` on the `TimeSlot` covers a single pod; the
unique constraint is what makes it correct across pods — same reasoning as Payment's
idempotency key.)

### Search has NO write contention

Search is pure read against the index — no locks, no races. Concurrency on the search side
is a *scalability* concern (serve many readers), solved by index replicas + caching, not
locking. **Say this distinction** — it mirrors the correctness-vs-throughput point from the
Delivery problem and shows you place locks only where contention actually exists.

### Read-your-writes nuance (nice bonus point)

Because search is eventually consistent (index lags the write store by seconds), an owner who
just created a listing might not see it in search immediately. Fix: serve the owner's *own*
listings from the write store (MySQL) directly on their dashboard, while everyone else hits
the index. Read-your-writes for the author, eventual consistency for the crowd.

---

## §8 — 🧨 Java Depth Probes

| Question | Answer |
|---|---|
| "Two seekers book the same slot — how?" | A `UNIQUE(slot_id)` constraint on `visit_bookings`; concurrent inserts are serialized by the DB, the loser catches the violation and is offered another slot. In-JVM it's `synchronized` on the `TimeSlot`; the unique constraint is the cross-pod referee. Same archetype as Payment's idempotency key. |
| "Why not search directly on MySQL with WHERE clauses?" | Multi-filter + geo + relevance over millions of rows = full scans and no ranking. A search engine (Elasticsearch) has inverted indexes, faceting, geo queries, and relevance scoring built for exactly this read shape. MySQL is the source of truth; ES is the read model. |
| "How do you keep MySQL and Elasticsearch in sync?" | Change-Data-Capture / outbox: listing writes emit a change event (Kafka); an indexer consumes and upserts/deletes in ES. Decouples the write path from ES availability and gives replayability. Accept a few seconds of lag. |
| "Offset vs cursor pagination for search results?" | Cursor (`search_after`). Offset re-scans skipped rows (O(N) deep) and shifts when new listings arrive mid-scroll (duplicates/gaps). A cursor seeks directly (O(log N)) and is stable under concurrent inserts. |
| "How do you compose arbitrary filters cleanly in code?" | Specification/Composite pattern — each filter is a `SearchFilterSpec` predicate with `.and()`; they compose into one spec, which the SearchService compiles into an ES bool query. New filter = new spec class, zero changes to search core (OCP). |
| "The index lags — how does an owner see their new listing immediately?" | Read-your-writes: serve the owner's own listings from MySQL on their dashboard; everyone else reads the eventually-consistent index. Author gets immediacy, the crowd gets scale. |
| "Where do photos live?" | Object storage (S3) behind a CDN; the DB row holds only URLs. Bytes are large/static — CDN edge-caches them; keeping bytes in the DB bloats rows and backups. |

---

## §9 — 🌐 HLD — How to Build This Diagram in the Interview — 3 Phases

### Phase 1 — Numbers First (≈2 min)

```
Scale assumption: country-wide rental marketplace

  Active listings   10M active listings
  Listing writes    50k new/updated per day → 50,000 / 86,400 ≈ 0.6 writes/sec  (tiny)
  Searches          20M searches/day → 20M / 86,400 ≈ 230 searches/sec
                    peak 5× (evenings) ≈ 1,200 searches/sec               ← DOMINANT
  Visit bookings    500k/day → ~6/sec  (tiny contention surface)
  Storage (listings) 10M × ~2 KB metadata ≈ 20 GB  (fits comfortably in MySQL)
  Photos            10M × 8 photos × 300 KB ≈ 24 TB → object storage + CDN, NOT the DB

READ:WRITE RATIO ≈ 1,200 searches/sec : 0.6 writes/sec ≈ 2000 : 1  → extremely read-heavy

Two forces on the architecture:
  (1) Read-heavy faceted search (1,200/sec, filters+geo+relevance) → dedicated search index
  (2) Tiny write + tiny contention (0.6 writes/sec, 6 bookings/sec) → MySQL handles easily
```

---

### Phase 2 — Skeleton: Simplest System That Could Work (≈3 min)

```
── Skeleton: Simplest System That Could Work ──────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Seeker App/Web · Owner App/Web        │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS
   ┌─────────────────────▼──────────────────────────┐
   │  API Gateway  (auth · routing)                 │
   └──────┬──────────────────────────────┬──────────┘
          │                              │
   ┌──────▼────────────────┐   ┌─────────▼──────────────────────────┐
   │  SearchService        │   │  ListingService  (create · status) │
   │  WHERE bhk=? AND       │   │  BookingService  (visit slots)     │
   │  rent BETWEEN ? AND ?  │   │  NotificationService ──▶ Email/SMS │
   │  AND ... (SQL)         │   └─────────────────────┬──────────────┘
   └──────┬────────────────┘                          │
          │                                           │
   ┌──────▼──────────────────────────────────────────▼────────────┐
   │  MySQL  (listings · time_slots · visit_bookings · users)     │
   │  search runs multi-filter WHERE clauses over the listings tbl │
   └───────────────────────────────────────────────────────────────┘

BREAKING POINT — walk this skeleton against the Phase 1 numbers:
  (a) SearchService runs multi-filter + geo WHERE clauses over 10M rows at
      1,200 searches/sec → full/partial table scans; no relevance ranking;
      LIKE '%locality%' can't use an index. Latency blows past the <300ms budget.
  (b) Photos: if bytes sit in MySQL, 24 TB wrecks row size, backups, and cache.
  (c) NotificationService (email/SMS) is synchronous on the booking path → a slow
      provider slows the booking response.
  (d) Deep pagination via LIMIT/OFFSET re-scans skipped rows and shifts results as
      new listings arrive → slow + inconsistent scroll.

══════════════════════════════════════════════════════════════════
```

---

### Phase 3 — Upgrade It: One Fix per Pain Point (≈5 min)

*"This works in dev. Now let me address each breaking point."*

**BREAKING POINT (a) → Elasticsearch as the read model + CDC sync from MySQL**

Index ACTIVE listings in Elasticsearch (inverted indexes, faceting, `geo_distance`,
relevance scoring). SearchService compiles the composed `SearchFilterSpec` into an ES bool
query — sub-100ms even over 10M docs. Keep MySQL as the source of truth; a **CDC/outbox**
pipeline (listing change events → Kafka → indexer) upserts ACTIVE and deletes RENTED/INACTIVE
from the index. Accept a few seconds of lag.

**BREAKING POINT (b) → Photos to object storage + CDN, URLs in the row**

Upload photo bytes to S3; store only URLs on the listing. A CDN edge-caches images near
users. MySQL rows stay small; search returns URLs the client loads from the CDN.

**BREAKING POINT (c) → Kafka for async notifications**

On `VisitBooking.transition()` (REQUESTED/CONFIRMED/REJECTED), emit an event; Notification
Service consumes and sends email/SMS asynchronously. The booking API returns immediately.
At-least-once; dedup on (bookingId, status).

**BREAKING POINT (d) → Cursor pagination + hot-query cache**

Use ES `search_after` cursors (stable, O(log N) seek) instead of `LIMIT/OFFSET`. Cache the
hottest query results (popular localities/filters) in Redis with a short TTL to shave repeat
load off ES during evening peak.

---

```
── Production: All 4 Upgrades Applied ────────────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Seeker App/Web · Owner App/Web        │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS
   ┌─────────────────────▼────────────────────────────────────────┐
   │  API Gateway  (JWT · rate-limit · TLS · routing)            │
   └──────┬────────────────────────────────────────┬─────────────┘
          │                                        │
   ┌──────▼────────────────┐   ┌────────────────────▼────────────────────┐
   │  SearchService        │   │  ListingService                         │
   │  compile spec → ES     │   │  create / transition (state machine)    │
   │  cursor (search_after) │   │   → write MySQL + emit change event     │
   │  rank via strategy     │   │  BookingService                         │
   │  (read-heavy)          │   │   INSERT visit_bookings UNIQUE(slot_id) │
   └──────┬─────────────────┘   └────────────────────┬────────────────────┘
          │ GET hot-query cache                       │ INSERT booking (ACID)
          │ then query ES                             │ write listing + emit event
          ▼                                           ▼
   ┌──────────────────────────────┐    ┌──────────────────────────────────────┐
   │  Redis                       │    │  MySQL  (ACID — source of truth)     │
   │  q:{filterHash} → results    │    │  listings · users                     │
   │    · EX 60   ← SearchSvc     │    │  time_slots                           │
   │  slot:{id}:lock (optional)   │    │  visit_bookings  UNIQUE(slot_id)      │
   │    · PX 30000 ← BookingSvc   │    │      ← ListingSvc / BookingSvc        │
   └──────────────┬───────────────┘    └────────────────────┬─────────────────┘
                  │ cache miss → query                       │ emit listing.changed (CDC/outbox)
   ┌──────────────▼───────────────┐    ┌─────────────────────▼─────────────────┐
   │  Elasticsearch (read model)  │    │  Kafka                                │
   │  ACTIVE listings only         │◀───│  topic listing-events                 │
   │  inverted idx · geo · facets  │    │   └─▶ Indexer (upsert ACTIVE /        │
   │      ← Indexer                │    │        delete RENTED/INACTIVE → ES)   │
   └──────────────────────────────┘    │  topic booking-events                 │
                                       │   ├─▶ NotificationService (email/SMS) │
   ┌──────────────────────────────┐    │   └─▶ AnalyticsService                │
   │  Object Storage (S3) + CDN   │    └────────────────────────────────────────┘
   │  photo bytes; rows hold URLs │
   └──────────────────────────────┘

KEY INVARIANT: MySQL is the single source of truth; Elasticsearch is an
  eventually-consistent read model kept in sync by CDC events (ACTIVE upserted,
  RENTED/INACTIVE deleted), so the 2000:1 read-heavy search load never touches
  the write store. The only write contention in the system is a visit slot,
  guarded by UNIQUE(slot_id) — one seeker per slot. Photos live on a CDN, never
  in the DB.
══════════════════════════════════════════════════════════════════
```

---

## §10 — 🏛️ HLD Decisions

| Component | Why chosen | Rejected + why |
|---|---|---|
| **Elasticsearch as the read model** | Faceted filters + `geo_distance` + relevance over 10M docs at 1,200 searches/sec in <100ms — exactly the read shape ES is built for. | MySQL `WHERE`/`LIKE` search — full scans, no relevance, `LIKE '%x%'` can't index; misses the latency budget |
| **MySQL as source of truth** | Listings/bookings need ACID + a `UNIQUE(slot_id)` constraint; writes are tiny (0.6/sec) so one primary is plenty. | Making ES the source of truth — ES is a search engine, not a durable transactional store; risky for bookings |
| **CDC / outbox → Kafka → Indexer to sync MySQL→ES** | Decouples the write path from ES availability; replayable; a few seconds of lag is acceptable for search. | Synchronous dual-write to ES inside the listing transaction — couples writes to ES uptime; partial-failure inconsistency |
| **Redis hot-query cache** | Evening peak repeats popular locality/filter queries; a short-TTL cache shaves repeat load off ES cheaply. | No cache — ES absorbs every repeated popular query; wasteful at peak |
| **`UNIQUE(slot_id)` for visit booking** | Cheapest durable cross-pod guarantee for the one place contention exists; DB is the referee. | A distributed lock service — overkill for 6 bookings/sec with a tiny contention surface |
| **Photos in S3 + CDN** | 24 TB of static bytes belong at the edge, not in the DB; rows stay tiny. | Bytes in MySQL — bloats rows, wrecks backups, no edge caching |
| **Kafka for notifications** | Email/SMS off the booking critical path; async fan-out; dedup on (bookingId, status). | Synchronous send — slow provider slows the booking response |

---

## §11 — 📡 API Design

### GET /v1/search — faceted listing search (the dominant read path)

```
GET /v1/search?bhk=TWO_BHK&minRent=15000&maxRent=30000
              &lat=12.93&lng=77.62&radiusKm=3
              &furnishing=SEMI&amenities=PARKING,LIFT
              &sort=RELEVANCE&cursor=eyJzYSI6WzE2...&limit=20
Authorization: Bearer {jwt}

200 OK
{
  "results": [
    {
      "listingId": "lst-8821",
      "bhk": "TWO_BHK",
      "rent": { "value": "27000", "currency": "INR" },
      "locality": "Koramangala",
      "furnishing": "SEMI",
      "distanceKm": 1.2,
      "thumbnailUrl": "https://cdn.example.com/lst-8821/1_thumb.jpg"
    }
  ],
  "nextCursor": "eyJzYSI6WzE2Mzk..."     ← cursor pagination, not offset
}
```

---

### POST /v1/listings — owner creates a listing (write; triggers indexing)

```
POST /v1/listings
Authorization: Bearer {owner-jwt}
Content-Type: application/json

{
  "bhk": "TWO_BHK",
  "rent": { "value": "27000", "currency": "INR" },
  "furnishing": "SEMI",
  "amenities": ["PARKING", "LIFT"],
  "location": { "lat": 12.93, "lng": 77.62, "locality": "Koramangala" },
  "availableFrom": "2026-09-01",
  "photoUrls": ["https://cdn.example.com/lst-8821/1.jpg"]
}

201 Created
{ "listingId": "lst-8821", "status": "ACTIVE" }
```

> Response returns as soon as MySQL commits + the change event is emitted. The listing
> appears in search within a few seconds (index lag) — the owner sees it immediately on
> their dashboard via the read-your-writes path (§7).

---

### POST /v1/listings/{id}/visits — seeker books a visit slot (the one write race)

```
POST /v1/listings/lst-8821/visits
X-Idempotency-Key: {uuid}
Authorization: Bearer {seeker-jwt}
Content-Type: application/json

{ "slotId": "slot-3f9", "seekerId": "usr-556" }

201 Created
{ "bookingId": "bkg-77c", "slotId": "slot-3f9", "status": "REQUESTED" }

409 Conflict            ← slot just taken (UNIQUE(slot_id) violation) — pick another slot
```

---

## §12 — 🛤️ Happy + Unhappy Paths

### Happy path — list, search, book a visit

```
1. Owner → POST /listings → ListingService writes to MySQL (status ACTIVE) +
   emits listing.changed → Indexer upserts the doc into Elasticsearch (~seconds).

2. Seeker → GET /search?bhk=TWO_BHK&maxRent=30000&lat=..&lng=..&radiusKm=3 →
   SearchService: Redis hot-query cache miss → compile spec → ES bool query →
   RankingStrategy sorts by relevance → cursor page returned (<300ms).

3. Seeker picks a listing, sees the owner's available TimeSlots.

4. Seeker → POST /listings/{id}/visits {slotId} →
   BookingService: INSERT visit_bookings (UNIQUE(slot_id)) → OK →
   VisitBooking REQUESTED → emit booking.changed.

5. NotificationService (Kafka consumer) → notifies owner "New visit request".

6. Owner confirms → VisitBooking CONFIRMED → emit event → seeker notified.
   After the visit → COMPLETED. If the flat is rented → Listing.transition(RENTED) →
   emit event → Indexer DELETES it from ES → it disappears from search.
```

---

### Unhappy path 1 — two seekers, one slot (the only write race)

```
Seeker-A and Seeker-B both POST the same slot-3f9 at once.

Pod 1: INSERT visit_bookings(slot_id=slot-3f9, A) → UNIQUE ok → 201 REQUESTED.
Pod 2: INSERT visit_bookings(slot_id=slot-3f9, B) → UNIQUE violation
       → catch → 409 "slot just taken" → seeker offered the next free slot.

One booking wins; no double-booked slot; loser gets a graceful retry option.
```

---

### Unhappy path 2 — search index lag (eventual consistency)

```
Owner just created a listing; it's in MySQL but not yet in ES (indexer lag ~seconds).

Effect: a general seeker's search doesn't show it for a few seconds.
Handling:
  a. Acceptable by design — search is eventually consistent.
  b. The OWNER sees it immediately on their dashboard (read-your-writes: dashboard
     reads from MySQL, not ES).
  c. Conversely, a just-RENTED flat may linger in search for a few seconds until the
     delete event is indexed → on click-through, ListingService checks live status in
     MySQL and shows "no longer available" if it flipped.
```

---

### Unhappy path 3 — Elasticsearch is down

```
ES cluster unavailable during a search.

a. Writes are unaffected — MySQL is the source of truth; change events buffer in Kafka.
b. Search degrades gracefully: serve the Redis hot-query cache for popular queries;
   for cache misses, show a "search temporarily degraded" state or a bounded MySQL
   fallback for simple filters (no relevance).
c. When ES recovers, the Indexer replays buffered Kafka events → index catches up.
   No listing data is lost because ES was never the source of truth.
```

---

### Unhappy path 4 — indexer/CDC pipeline stalls

```
The Indexer consumer stops (bug/crash); listing.changed events pile up in Kafka.

a. Search silently serves increasingly stale results (new listings missing,
   rented ones lingering) — detectable via consumer-lag monitoring/alerts.
b. MySQL and Kafka are fine; nothing is lost. On restart, the Indexer resumes from
   its committed offset and drains the backlog → ES converges.
c. Guardrail: alert when consumer lag exceeds N minutes so staleness never grows unbounded.
```

---

## §13 — ⚠️ Fault Tolerance

| Dependency | Timeout | Retry policy | Fallback |
|---|---|---|---|
| **Elasticsearch (search)** | 500ms | 1 retry | Serve Redis hot-query cache; else bounded MySQL fallback for simple filters (no relevance) + "degraded" flag |
| **Redis (query cache)** | 50ms | 1 retry | Skip cache → query ES directly (slightly higher latency, still correct) |
| **MySQL (writes/bookings)** | 5s | 1 retry, new connection | Fail-fast 503; do not confirm a booking unless the row committed; circuit breaker after 5 failures |
| **Kafka / CDC indexer** | n/a (async) | consumer resumes from committed offset; DLQ for poison events | Search goes stale, not wrong; alert on consumer lag > N min; replay on recovery |
| **Object storage / CDN (photos)** | 2s | client retries via CDN | Show a placeholder thumbnail; listing metadata still renders (bytes are non-critical to search) |
| **Notification (email/SMS)** | 5s | 3× consumer retry; DLQ | Notification delayed, not lost; booking state already durable in MySQL |

> **The consistency rule for this system:** MySQL is always right; Elasticsearch is allowed
> to be *stale but never the source of truth*. Every failure mode degrades search freshness,
> never listing/booking correctness. Saying this sentence frames the whole fault story.

---

## §14 — 📐 Q&A — Tier-2 JPMC Probes

**Q: Why introduce Elasticsearch at all — why not just index the right columns in MySQL?**

> B-tree indexes help exact/range predicates, but this workload is *faceted* — arbitrary
> combinations of BHK + rent + furnishing + amenities + `geo_distance` + relevance ranking +
> full-text on locality. MySQL can't index every filter combination, `LIKE '%x%'` can't use
> an index, and it has no relevance scoring. Elasticsearch has inverted indexes, faceting,
> geo queries, and scoring built for exactly this. MySQL stays the source of truth; ES is a
> derived read model. At 2000:1 read:write, specializing the read side is the whole game.

**Q: How stale can search be, and how do you keep it bounded?**

> A few seconds is fine — a just-listed flat appearing a beat late, or a just-rented one
> lingering briefly, is harmless. It's bounded by monitoring the indexer's Kafka consumer
> lag and alerting when it exceeds a threshold (say a minute). For correctness on
> click-through, ListingService re-checks live status in MySQL so a stale "available" flat
> shows "no longer available" if it flipped. Owners get read-your-writes from MySQL so they
> never see their own listing missing.

**Q: A popular locality query gets hammered every evening. What do you do?**

> Cache it. The filter set is deterministic, so I hash the normalized query into a Redis key
> `q:{filterHash}` with a short TTL (say 60s) holding the result page. Repeated identical
> popular queries hit Redis, not ES. Short TTL keeps it fresh enough given search is already
> eventually consistent. ES replicas handle the cache-miss fan-out.

**Q: How do you prevent one seeker double-booking or two seekers colliding on a slot?**

> A `UNIQUE(slot_id)` constraint on `visit_bookings` — the DB serializes concurrent inserts,
> the loser catches the violation and is offered another slot. Idempotency-Key on the request
> makes the seeker's own retry safe (returns the same booking). It's the same hot-resource
> pattern as the other problems, but the contention surface here is tiny — only visit slots,
> ~6/sec — so a DB constraint is more than enough; no distributed lock service needed.

---

## §15 — 🧾 TL;DR

**The one sentence:** *A two-sided marketplace with opposite workloads — write-light listing
management vs read-heavy faceted search — so I split MySQL (source of truth) from
Elasticsearch (eventually-consistent read model) synced by CDC events; the only write
contention is a visit slot, guarded by `UNIQUE(slot_id)`.*

**Entities:** `Listing (state machine) · TimeSlot (bookedBy, the one HOT resource) ·
VisitBooking (state machine) · User · Location (VO)`; `SearchFilterSpec` (Specification/
Composite) + `RankingStrategy` (Strategy).

**Concurrency:**
- Visit-slot race → `UNIQUE(slot_id)` (DB is the referee) / `synchronized` in-JVM. Same
  hot-resource archetype as Parking Lot / Payment / Delivery — tiny contention surface.
- Search side → **no** write contention; scaling is read replicas + caching, not locking.

**HLD shape:**
- `SearchService` → Redis hot-query cache → Elasticsearch (facets + geo + relevance, cursor pagination)
- `ListingService` → MySQL (source of truth) + emit `listing.changed` → CDC/Indexer → ES (upsert ACTIVE / delete RENTED)
- `BookingService` → MySQL `visit_bookings` with `UNIQUE(slot_id)`
- Photos → S3 + CDN (URLs in the row, never bytes in the DB)
- Kafka `booking-events` → NotificationService + AnalyticsService

**SDE-3 signals to surface proactively:**
- Recognize the 2000:1 read:write ratio → specialize the read side (search index), don't force one store to do both.
- MySQL is always right; ES may be stale but is never the source of truth — every failure degrades freshness, never correctness.
- Cursor pagination, not offset; read-your-writes for owners; hot-query cache for peak.
- Locks belong only where contention exists — here that's just the visit slot.

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created. Full 16-section solution for No Broker App — Tier-3 JPMC Round 3 problem (⭐, Jan 2026 Cohort SuperDay). First **search-and-browse marketplace** in the set (vs the write-and-claim archetype of problems 01–04). LLD: Listing + VisitBooking dual state machines, TimeSlot as the single small hot resource, SearchFilterSpec (Specification/Composite pattern), RankingStrategy (Strategy pattern), Location value object. Concurrency: UNIQUE(slot_id) / synchronized for the visit-slot race (same hot-resource archetype, tiny surface) + explicit no-contention-on-search point and read-your-writes nuance. HLD: 3-phase Confluent construction guide; central insight is the ~2000:1 read:write ratio driving a MySQL-source-of-truth + Elasticsearch-read-model split synced by CDC/outbox → Kafka → Indexer; Redis hot-query cache; cursor pagination; photos on S3+CDN; the consistency rule "MySQL always right, ES stale-but-never-source-of-truth." Completes the JPMC Round 3 Problems set (01–05). |
