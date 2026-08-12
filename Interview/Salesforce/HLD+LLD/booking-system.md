# Meeting Room Reservation / Booking System — HLD + LLD Combined Round

---

## 0.  Identity

| | |
|---|---|
| **Problem** | Booking / Reservation System — reserve a limited resource for a time interval without double-booking, under concurrency |
| **Format** | HLD+LLD combined (Salesforce SMTS), 90 min confirmed |
| **Time budget** | 35 min LLD -> 45 min HLD -> 10 min buffer |
| **Frequency rank** | **#4 pick**, and the **#1 HLD cluster** in `questions-by-frequency.md` — the most durable category in the verified data with 5 instances: Cab Booking (Dec 2025), Meeting Room Reservation (Dec 2025), Meeting Scheduler (Oct 2025), Vaccination Slot Booking (2023), Hotel Booking (2023). Meeting Room is the concrete example because CodingKaro captured its full prompt verbatim. |
| **Salesforce-specific angle** | Salesforce Scheduler / Lightning Scheduler is a real product (appointment booking for banking, healthcare). Multi-tenant resource pools + per-org calendars are the natural extension. |

**The verbatim prompt this is built from (CodingKaro, Dec 2025):** *"Design the low-level system for a Meeting Room Reservation Platform... 1. An employee can view all available meeting rooms for a given time interval. 2. An employee can book a meeting room if it is free during the requested time. 3. Ability to cancel an existing meeting. 4. Handle overlapping meeting requests gracefully. 5. Ability to list all meetings scheduled for a given room or employee. 6. Support recurring meetings (optional). Entities Involved: Employee, MeetingRoom, Booking, TimeSlot or Interval. Constraints: No double-booking of the same room at overlapping times. Concurrency: Handle race conditions where two users try to book the same room at the same time."*

**Read that last line again — the prompt names concurrency explicitly.** This problem is a concurrency problem wearing a CRUD costume. If you deliver clean CRUD classes and hand-wave the race, you fail it.

---

## 1.  Dual-Layer Map

| HLD Box (system view) | LLD Class(es) (code view) | The interface that makes it swappable |
|---|---|---|
| Booking API | `BookingService` | — (orchestrator) |
| Availability search | `AvailabilityService`, `TimeSlot` | `AvailabilityStrategy` |
| Overlap detection | `TimeInterval.overlaps()` | — (pure value logic) |
| Conflict prevention / locking | `BookingLockManager` | **`LockStrategy`** — DB constraint vs pessimistic vs distributed lock |
| Persistence | `BookingRepository`, `RoomRepository` | **`BookingRepository`** |
| Recurrence expansion | `RecurrenceRule`, `RecurrenceExpander` | **`RecurrenceRule`** — Strategy |
| Notification on book/cancel | `BookingEventPublisher` | `EventPublisher` (-> the Notification Service problem) |
| Room inventory / attributes | `MeetingRoom`, `RoomFilter` | **`RoomFilter`** — capacity, floor, equipment |

**The zoom sentence:** *"`BookingLockManager` is a `synchronized` block or a DB unique constraint in LLD. In HLD it's the thing that decides whether two API pods in different AZs can double-book the same room — same responsibility, completely different mechanism."*

---

## 2.  LLD Half (target: 35 min)

### 2.1  Problem Statement

Design a system where employees can find available meeting rooms for a time interval, book one, cancel bookings, and list bookings by room or employee — with a hard guarantee that the same room is never double-booked for overlapping intervals, even when two users submit simultaneously.

### 2.2  Requirements

**Functional (from the prompt, verbatim):**
- View all available rooms for a given time interval
- Book a room if free for the requested interval
- Cancel an existing booking
- Handle overlapping requests gracefully
- List all bookings for a given room or employee
- Recurring meetings (optional enhancement)

**Non-Functional:**
- **No double-booking, ever** — this is a correctness invariant, not a best-effort target
- **Thread-safe** — concurrent booking attempts on the same room must serialize correctly
- **Extensible** — new room attribute/filter or new recurrence type = one new class
- Availability queries are far more frequent than bookings (read-heavy)

**Explicitly out of scope:** external calendar sync (Google/Outlook), and room-suggestion ML.

### 2.3  Class Design

#### 2.3.1  Deriving the classes (say this out loud, minutes 2-6)

| # | Requirement | Noun / variation point | Becomes | Why it earns its own type (and what breaks if you inline it) |
|---|---|---|---|---|
| 1 | "for a given time interval" | noun: *interval* | **`TimeInterval`** (value object) | **The most important class in this design, and the one most candidates skip.** Overlap logic (`start < other.end && other.start < end`) must live in exactly one place. Scattering raw `startTime`/`endTime` pairs across services means the off-by-one on boundary equality gets reimplemented — differently — in three places, and one of them will be wrong. Immutable, self-validating (`start < end`). |
| 2 | "book a meeting room" | noun: *room* | **`MeetingRoom`** (entity) | Has identity, attributes (capacity, floor, equipment), and its own lifecycle independent of any booking. |
| 3 | "book a room **if free**" | noun: *the reservation itself* | **`Booking`** (entity) | The association between employee, room, and interval needs identity so it can be cancelled and audited. Not a field on either side — it's a first-class relationship with its own status. |
| 4 | "an employee can..." | noun: *employee* | **`Employee`** (entity) | Referenced, not owned. In a real system this comes from an identity service — model it as a thin reference with an ID, and say that. |
| 5 | "cancel an existing meeting" | *lifecycle* of a booking | **`BookingStatus`** (enum) | `CONFIRMED -> CANCELLED` plus `COMPLETED`. Soft-cancel via status rather than row deletion, because "who cancelled the 3pm standup?" is a real question and deleted rows can't answer it. |
| 6 | "handle overlapping requests gracefully" + "race conditions" (NFR) | the *conflict-prevention mechanism* | **`BookingLockManager`** + **`LockStrategy`** (interface) | The prompt names this explicitly, so it deserves a named component rather than a `synchronized` keyword hidden in the service. Making it an interface is also the HLD pivot: in-JVM lock -> DB constraint -> distributed lock, same seam. |
| 7 | "view all available rooms" | verb: *search availability* | **`AvailabilityService`** | Different reason to change than booking (read path vs write path, and it gets cached/optimized independently). Separating it also stops the read path from accidentally acquiring write locks. |
| 8 | "list bookings for a room **or** employee" | verb: *query by two access patterns* | **`BookingRepository`** (interface) | Two distinct query shapes -> two indexed methods. Naming both up front is what drives the two indexes in the HLD data model. |
| 9 | "support recurring meetings" | the *recurrence rule* varies | **`RecurrenceRule`** (interface) + `DailyRule`, `WeeklyRule`, `NoRecurrence` | Variation point. **The real decision is expansion strategy** — see 2.5; the interface exists so "every 2nd Tuesday" is a new class, not a new `if`. |
| 10 | "capacity, floor, equipment" filters | *filter criteria* compose | **`RoomFilter`** (interface, composable) | Filters combine (capacity >= 8 AND has projector). Composable predicates beat a method with 6 nullable params, which is unreadable at the call site and untestable in combination. |

**One-liner after the table:** *"The center of gravity is `TimeInterval` — overlap logic lives there and only there — and `LockStrategy`, because the prompt explicitly asks about the race condition."*

#### 2.3.2  Entity fields

```
TimeInterval                       <- immutable value object, the workhorse
  - start: Instant
  - end:   Instant
  + overlaps(TimeInterval): boolean
  + contains(Instant): boolean
  + duration(): Duration

MeetingRoom
  - roomId:    String
  - name:      String
  - capacity:  int
  - floor:     int
  - equipment: Set<Equipment>       <- PROJECTOR, VC, WHITEBOARD
  - active:    boolean              <- soft-disable for renovation

Booking
  - bookingId:   String
  - roomId:      String
  - organizerId: String
  - interval:    TimeInterval       <- composition
  - status:      BookingStatus
  - attendees:   Set<String>
  - seriesId:    String             <- non-null if part of a recurring series
  - createdAt:   Instant

BookingStatus (enum): CONFIRMED, CANCELLED, COMPLETED

Equipment (enum): PROJECTOR, VIDEO_CONF, WHITEBOARD, PHONE
```

**Boundary semantics — state this explicitly, it's a classic gotcha:** intervals are **half-open** `[start, end)`. A booking 10:00-11:00 and another 11:00-12:00 do **not** overlap. Without this convention, back-to-back meetings are rejected as conflicts and users hate the system. The overlap test is `start < other.end && other.start < end` — strict inequalities on both sides.

#### 2.3.3  Relationships — with the composition-vs-aggregation call made explicit

| Relationship | Type | Composition or aggregation — and why that one |
|---|---|---|
| `Booking` — `TimeInterval` | **HAS-A** -> **composition** | Created with the booking, dies with it, shared with nothing. A value object embedded in the entity — in SQL it's literally two columns on the booking row, which is the clearest evidence of composition. |
| `Booking` — `MeetingRoom` | **references by ID**, not object composition | Deliberate and worth defending: `Booking` holds `roomId`, not a `MeetingRoom` object. Rooms have independent lifecycle (they exist without bookings, get renovated, get retired) and embedding the object invites accidental deep loads of the whole room graph on every booking query. |
| `Booking` — `Employee` (organizer) | **references by ID** | Same reasoning, stronger: employees come from an external identity system. Owning an `Employee` object here means this service caches identity data it doesn't own and can't keep fresh. |
| `MeetingRoom` — `Set<Equipment>` | **HAS-A** -> **composition** | The set is created with and belongs to the room; enum values are shared JVM-wide but the collection is the room's own. |
| `BookingService` — `BookingLockManager` | **HAS-A** -> **aggregation** | Injected. The lock manager wraps shared infrastructure (a DB connection or Redis client) and is a shared singleton — the service must not own its lifecycle. |
| `BookingService` — `BookingRepository` | **USES** (injected collaborator) | Calls it and forgets it; holds no state on it. |
| `Booking` — `RecurrenceRule` | **HAS-A** -> **aggregation** (on the series, not the instance) | Subtle and worth saying: the rule belongs to the **series**, and individual booking instances reference the series by `seriesId`. Putting the rule on every instance duplicates it N times and makes "edit the whole series" ambiguous. |
| `RoomFilter` — `RoomFilter` | **composite** (self-referential) | `AndFilter` holds child filters — Composite pattern. Aggregation: children are built independently and can be reused across composites. |

#### 2.3.4  ASCII class diagram — interfaces before implementations, always

```
                          BookingService
                          - bookingRepo:  BookingRepository
                          - roomRepo:     RoomRepository
                          - lockManager:  BookingLockManager
                          - availability: AvailabilityService
                          + book(req): Booking
                          + cancel(bookingId): void
                          + listByRoom(roomId, range): List<Booking>
                          + listByEmployee(empId, range): List<Booking>
                                    |  uses
        +---------------------------+---------------------------+
        v                           v                           v
  <<interface>>              <<interface>>              <<interface>>
  BookingRepository          LockStrategy               AvailabilityStrategy
  + save(Booking)            + withLock(key,            + findAvailable(
  + findOverlapping(             action): T                 interval, filter):
      roomId, interval)                                      List<MeetingRoom>
  + findByRoom(...)                ^                          ^
  + findByEmployee(...)            | implements               | implements
        ^                    +-----+---------+          ScanAllRoomsStrategy
        | implements         |               |          IndexedSlotStrategy
  PostgresBookingRepo   DbConstraintLock  RedisDistLock
                        (unique index)   (SET NX PX)
                        JvmLock
                        (per-room monitor)

                          Booking
                          - interval: TimeInterval   <>--- composition
                          - status:   BookingStatus
                          - seriesId: String  ------> BookingSeries
                                                       - rule: RecurrenceRule
                                                               |
                                                               v
                                                        <<interface>>
                                                        RecurrenceRule
                                                        + nextOccurrence(from)
                                                        + expand(range): List<TimeInterval>
                                                               ^
                                                    +----------+----------+
                                                    |          |          |
                                                 Daily      Weekly    NoRecurrence
                                                 Rule        Rule

                          <<interface>>
                          RoomFilter                  <-- Composite pattern
                          + matches(MeetingRoom): boolean
                                    ^
                    +---------------+---------------+
                    |               |               |
            CapacityFilter   EquipmentFilter    AndFilter
                                                (holds List<RoomFilter>)
```

#### 2.3.5  Follow-ups they will ask after this section — and your answers

| Their question | Your answer (one breath) |
|---|---|
| "Why a `TimeInterval` class instead of two timestamp fields?" | "So overlap logic exists in exactly one place. Two raw fields means the comparison gets rewritten wherever it's needed, and the boundary case — is 11:00-12:00 in conflict with 10:00-11:00? — will be answered inconsistently. One class, one tested `overlaps()`, half-open semantics." |
| "Does a booking 10-11 conflict with 11-12?" | "No. Intervals are half-open `[start, end)`, so back-to-back bookings are legal. Strict inequalities both sides: `start < other.end && other.start < end`. If they were closed intervals, every consecutive meeting would falsely conflict." |
| "Why does `Booking` hold `roomId` rather than a `MeetingRoom`?" | "Independent lifecycles and query cost. Rooms exist without bookings and change on their own schedule; embedding the object means every booking list drags room graphs along. ID reference keeps the aggregate boundary tight." |
| "Composition or aggregation for `TimeInterval`?" | "Composition — created with the booking, dies with it, never shared. It's two columns on the booking row in SQL, which is exactly what composition looks like when persisted." |
| "How do you store a recurring meeting — one row or many?" | "That's the real design decision here. I'd store the rule once plus **materialized instances** for a bounded horizon, because conflict detection has to work against concrete intervals. See my reasoning in design decisions — pure rule-only storage makes overlap queries impossible to index." |
| "Two people book the same room at the same instant. Walk me through it." | "Both pass the availability read — that read is worthless for correctness. The write is guarded: acquire a per-room lock, re-check overlap **inside** the lock, insert, release. And a unique constraint underneath as the backstop, because application locks fail during deploys and network partitions." |
| "Isn't `RoomFilter` overkill? Just pass capacity and equipment params." | "With two criteria a params method is fine. It stops being fine at six, where you get a nullable-parameter soup that's untestable in combination. Composite filters are individually testable and combine without a combinatorial explosion of query methods. If the requirement froze at two filters, I'd collapse it." |

### 2.4  Key Interfaces

```java
/**
 * The workhorse value object. Immutable, self-validating, half-open [start, end).
 * Every overlap decision in the system routes through overlaps().
 */
public final class TimeInterval {
    private final Instant start;
    private final Instant end;

    public TimeInterval(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be strictly before end");
        }
        this.start = start;
        this.end = end;
    }

    /** Half-open semantics: [10:00,11:00) does NOT overlap [11:00,12:00). */
    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public Instant getStart() { return start; }
    public Instant getEnd()   { return end; }
}
```

```java
/**
 * The conflict-prevention seam. In-JVM today, distributed tomorrow —
 * this interface is what makes that a config change instead of a rewrite.
 */
public interface LockStrategy {
    <T> T withLock(String key, Duration timeout, Supplier<T> action);
}
```

```java
public interface BookingRepository {
    Booking save(Booking booking);
    /** THE query that must be indexed — see HLD data model. */
    List<Booking> findOverlapping(String roomId, TimeInterval interval);
    List<Booking> findByRoom(String roomId, TimeInterval range);
    List<Booking> findByEmployee(String employeeId, TimeInterval range);
}
```

```java
/** Recurrence as a strategy — "every 2nd Tuesday" becomes a class, not an if. */
public interface RecurrenceRule {
    List<TimeInterval> expand(TimeInterval firstOccurrence, TimeInterval withinRange);
    boolean isRecurring();
}
```

```java
/** Composable predicate — Composite pattern. */
public interface RoomFilter {
    boolean matches(MeetingRoom room);
}
```

### 2.5  Design Decisions

**The question you must be ready for: "How do you actually prevent the double-booking?"** This is the entire problem. The answer has to acknowledge that **the availability check is not a guard**:

> *"Reading availability and then writing is a classic check-then-act race — both requests read 'free', both write, both succeed. The read can never be the guarantee. The guarantee has to be at the write, and I'd use two layers: a lock so conflicting attempts serialize, and a database constraint underneath as the backstop for when the lock layer fails."*

| Option for conflict prevention | How it works | Pros | Cons | Verdict |
|---|---|---|---|---|
| Optimistic (version column, retry) | Read, write with version check, retry on conflict | No locks held; great under low contention | Wrong tool here — there's no single row being updated; the conflict is *between different rows* that don't yet exist | Rejected — optimistic locking guards updates, not insert-conflicts |
| Application-level `synchronized` on roomId | JVM monitor per room | Trivial, zero infra | **Single JVM only** — two API pods double-book instantly. Also a lock leak if an exception escapes | Fine for single-node demo, fails the real system |
| **Pessimistic DB lock** (`SELECT ... FOR UPDATE` on the room row) | Lock the room row, re-check overlap, insert, commit | Correct across all app nodes; DB is the single arbiter | Serializes all bookings for that room; a long transaction blocks others | **Chosen as the primary** |
| **DB exclusion constraint** (Postgres `EXCLUDE USING gist`) | The database itself rejects overlapping ranges | **Impossible to violate — correctness lives in the schema, not the code** | Postgres-specific; error surfaces as a constraint violation to translate | **Chosen as the backstop** |
| Distributed lock (Redis `SET NX PX`) | Cross-node mutex | Works when the DB isn't the coordination point; fast | Lock expiry vs long transaction = split brain; adds a dependency to the write path | Only if bookings span multiple datastores |

**Decision: pessimistic row lock + exclusion constraint together.** Say the reasoning: *"The lock makes conflicts rare and gives a clean error path; the constraint makes double-booking **impossible** even if the lock is bypassed by a bug, a deploy, or a direct DB write. Defense in depth — the constraint is the one I'd never remove, because it's the only layer that can't be defeated by application code being wrong."*

**Recurring meetings — rule-only vs materialized instances:**

| Option | Pros | Cons |
|---|---|---|
| Store rule only, compute occurrences on read | Tiny storage; "edit series" is one update | **Conflict detection becomes impossible to index** — you'd have to expand every recurring series in the system to test one overlap. Also can't cancel a single occurrence |
| Materialize every occurrence forever | Simple queries; conflicts are plain overlap checks | Unbounded rows for "daily forever" |
| **Rule + materialized instances over a rolling horizon** (e.g. 6 months), extended by a background job | Conflict queries stay simple and indexed; single-occurrence cancellation works; storage bounded | Background job required; series edits must fan out to instances |

**Decision:** rule + rolling materialization. The reason to state: *"Conflict detection is the hard requirement and it needs concrete rows to index. Everything else bends around that."*

| Decision | Pattern Chosen | Strongest Alternative Considered | Why the alternative loses |
|---|---|---|---|
| `TimeInterval` value object owning `overlaps()` | **Value Object** | `start`/`end` fields on `Booking` + a util method | Overlap logic gets reimplemented at each call site; the boundary convention drifts and back-to-back bookings break |
| Two-layer conflict prevention | **Pessimistic lock + DB constraint** | Application `synchronized` alone | Single-JVM only; two pods double-book, and the bug is invisible until a customer complains about a stolen room |
| Soft cancel via `status` | **State field** | Hard-delete the row | Destroys audit ("who cancelled it?") and makes recurring-series exceptions unrepresentable |
| Rule + materialized horizon | **Hybrid** | Pure rule-based expansion on read | Overlap detection can't be indexed; every conflict check would expand every series in the system |
| `RoomFilter` composite | **Composite / Specification** | Multi-param query method | Six nullable params is untestable in combination and forces a new method per criteria combination |

### 2.6  Visual — Object Interaction (the booking write path)

```
BookingService.book(employeeId, roomId, interval)
      |
      +--> validate: interval.start < interval.end, duration <= maxAllowed
      |
      +--> lockManager.withLock("room:" + roomId, 5s, () -> {
      |         |
      |         |    ****  EVERYTHING BELOW IS INSIDE THE LOCK  ****
      |         |
      |         +--> bookingRepo.findOverlapping(roomId, interval)
      |         |        SELECT * FROM bookings
      |         |        WHERE room_id = ? AND status = 'CONFIRMED'
      |         |          AND tstzrange(start,end) && tstzrange(?,?)
      |         |
      |         +--> if !empty  -> throw RoomUnavailableException(conflicts)
      |         |
      |         +--> booking = new Booking(roomId, employeeId, interval, CONFIRMED)
      |         +--> bookingRepo.save(booking)
      |                  |
      |                  +-- DB exclusion constraint fires here if the lock
      |                      was somehow bypassed -> translate to the same
      |                      RoomUnavailableException (defense in depth)
      |    })
      |
      +--> eventPublisher.publish(BookingConfirmed(booking))   [outside the lock]
      |         -> hands off to the Notification Service problem
      |
      v
   Booking (CONFIRMED)
```

**Two things to narrate while drawing this:**
1. *"The re-check is **inside** the lock. Checking availability before acquiring the lock is the bug — that's the check-then-act race. The read outside the lock is a UX convenience; the read inside is the correctness check."*
2. *"Publishing the event is **outside** the lock. Holding a lock across a network call to a message broker is how a 5ms lock becomes a 5-second lock and the room becomes unbookable for everyone."*

### 2.7  Coding Skeleton

**Order:** enum -> value object -> interface -> impl -> orchestrator.

```java
// 1. Enum first
public enum BookingStatus { CONFIRMED, CANCELLED, COMPLETED }

// 2. Value object with the overlap rule (written above in 2.4 — reference it,
//    don't rewrite it live; say "as defined earlier")

// 3. Interface before implementation
public interface LockStrategy {
    <T> T withLock(String key, Duration timeout, Supplier<T> action);
}

// 4. Implementation — the DB-backed one, since it's the one that's actually correct
public class DbRowLockStrategy implements LockStrategy {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public <T> T withLock(String key, Duration timeout, Supplier<T> action) {
        // Serializes concurrent bookings for THIS room only; other rooms proceed freely.
        jdbc.queryForObject(
            "SELECT room_id FROM meeting_rooms WHERE room_id = ? FOR UPDATE",
            String.class, key);
        return action.get();          // lock released at transaction commit
    }
}

// 5. Orchestrator — the method you narrate live
public class BookingService {
    private final BookingRepository bookingRepo;
    private final LockStrategy lockStrategy;
    private final BookingEventPublisher eventPublisher;

    public Booking book(String employeeId, String roomId, TimeInterval interval) {
        validateInterval(interval);

        Booking confirmed = lockStrategy.withLock("room:" + roomId, Duration.ofSeconds(5), () -> {
            // Re-check INSIDE the lock — the check-then-act race dies here
            List<Booking> conflicts = bookingRepo.findOverlapping(roomId, interval);
            if (!conflicts.isEmpty()) {
                throw new RoomUnavailableException(roomId, interval, conflicts);
            }
            Booking booking = Booking.create(roomId, employeeId, interval);
            try {
                return bookingRepo.save(booking);
            } catch (DataIntegrityViolationException e) {
                // The exclusion constraint caught what the lock missed.
                // Same exception either way — callers see one consistent contract.
                throw new RoomUnavailableException(roomId, interval, List.of());
            }
        });

        // Outside the lock on purpose — never hold a lock across a network call
        eventPublisher.publish(new BookingConfirmed(confirmed));
        return confirmed;
    }

    public void cancel(String bookingId, String requesterId) {
        Booking booking = bookingRepo.findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.getOrganizerId().equals(requesterId)) {
            throw new UnauthorizedException("only the organizer can cancel");
        }
        booking.setStatus(BookingStatus.CANCELLED);   // soft cancel, preserves audit
        bookingRepo.save(booking);
        eventPublisher.publish(new BookingCancelled(booking));
    }

    private void validateInterval(TimeInterval interval) {
        if (interval.getStart().isBefore(Instant.now())) {
            throw new IllegalArgumentException("cannot book in the past");
        }
    }
}
```

### 2.8  Concurrency — Making It Thread-Safe

The prompt asks for this by name. Cover all three layers.

| Race | Where | Fix | Why this fix |
|---|---|---|---|
| **Check-then-act on availability** (the headline race) | between `findOverlapping()` and `save()` | Re-check **inside** a per-room lock (`SELECT ... FOR UPDATE`) | Serializes only conflicting attempts on the *same room*; bookings for different rooms never contend. Locking globally would serialize the whole company's bookings |
| **Lock bypassed** (deploy, bug, direct SQL) | the write itself | **Postgres exclusion constraint** on `(room_id, tstzrange)` | Correctness enforced by the schema — application code literally cannot violate it. The layer that survives your own bugs |
| **Double-submit / retry** (user double-clicks, client retries) | API layer | Idempotency key on the request; unique index on `(organizer_id, room_id, start, idem_key)` | Prevents an accidental duplicate booking of the *same* slot by the same user, which the overlap check would happily reject as a conflict with itself |
| **Cancel racing with a booking edit** | `Booking.status` | Version column (optimistic) on the booking row | Here optimistic locking *is* right — it's a genuine single-row update, unlike the insert-conflict case |

**Granularity is the senior point to say out loud:** *"Lock key is `room:{roomId}`, not a global lock. Two people booking different rooms must never block each other — a global lock would make the entire company's booking throughput one-at-a-time. The lock scope should be exactly the resource whose invariant you're protecting."*

### 2.9  "What Would You Do Differently?"

**I'd make availability search read from a denormalized slot index rather than scanning bookings.** As written, `findAvailable` for 500 rooms means an overlap query per room, or one big query plus in-memory diffing. A precomputed per-room, per-day slot bitmap (each bit = 15 minutes) turns "is this room free 2-3pm?" into a bitmask AND — orders of magnitude cheaper for the read-heavy path. **Trade-off:** the bitmap must be invalidated on every write, so it's eventual-consistency for the *search* path only; the booking path still reads authoritative rows inside the lock.

**Second:** timezones. `Instant` is right for storage, but "book the room 2-3pm" is wall-clock in the room's local timezone, and a room in London vs Bangalore booked by an organizer in New York involves three. I'd store UTC instants plus the room's `zoneId` and do conversion at the edges — and I'd say out loud that recurring meetings across a DST boundary are where naive implementations break (the 9am standup must stay 9am local, which means the UTC instant shifts).

### 2.10  Interview Q&As (prep-only)

| Q | A |
|---|---|
| "How do you find available rooms efficiently?" | "Invert it: fetch all CONFIRMED bookings overlapping the interval in one indexed query, group by room, and subtract from the room set. One query, not one-per-room. For scale, the slot bitmap from 2.9." |
| "What if a meeting needs to be moved, not cancelled?" | "Modelled as cancel + create in one transaction, under the same room lock, so the slot can't be stolen in between. Exposing it as `PATCH` hides that it's really two conflict-checked operations." |
| "Someone books a room and never shows up." | "Auto-release: require check-in within N minutes or the booking is released. That's a scheduled job — which is exactly the Job Scheduler problem, and worth naming the connection." |
| "How do you handle a room being taken out of service?" | "`active = false` so it's excluded from search, then reconcile existing future bookings — notify organizers and offer alternatives. Never silently delete bookings; the meeting still exists in people's calendars." |
| "Overlapping recurring series?" | "Materialized instances make this ordinary — each instance is conflict-checked independently at materialization time. Partial-conflict policy has to be a product decision: book the non-conflicting occurrences and report the rest, or reject the whole series. I'd default to partial-success with an explicit report." |
| "Why not just let the DB constraint do everything and skip the lock?" | "You could, and it'd be correct — but every conflict becomes an exception thrown after work is done, and under contention you get constraint-violation storms in the logs with no clean way to tell the user *what* conflicts. The lock gives a graceful path with details; the constraint guarantees correctness. Different jobs." |

### 2.11  TL;DR — 30-Second Pitch (LLD)

The center of the design is `TimeInterval`, an immutable value object that owns the half-open overlap rule so `[10,11)` and `[11,12)` correctly don't conflict — that logic exists in exactly one place. `Booking` references room and employee by ID rather than composing them, keeping the aggregate tight. The correctness requirement is double-booking prevention, and the key insight is that the availability read is never the guarantee: the write path acquires a per-room lock (`SELECT ... FOR UPDATE`), **re-checks overlap inside the lock**, then inserts — with a Postgres exclusion constraint on `(room_id, tstzrange)` underneath as a backstop that application bugs cannot defeat. Recurrence is stored as a rule plus materialized instances over a rolling horizon, because conflict detection needs concrete indexed rows.

### 2.12  Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Value Object** | `TimeInterval` | Immutable, self-validating, single home for overlap logic |
| **Strategy** | `LockStrategy`, `RecurrenceRule`, `AvailabilityStrategy` | Swappable mechanisms; `LockStrategy` is the LLD->HLD seam |
| **Composite** | `RoomFilter` / `AndFilter` | Filters combine without a combinatorial method explosion |
| **Repository** | `BookingRepository` | Isolates the two access patterns that drive the indexes |
| **Domain Events** | `BookingConfirmed`, `BookingCancelled` | Decouples notification from booking; published outside the lock |
| **Optimistic locking** | version column on `Booking` updates | Correct tool for single-row edits (unlike the insert-conflict case) |

---

## 3.  HLD Half (target: 45 min)

### 3.1 Clarifying Questions (0-3 min)

| Question | Architectural Fork |
|---|---|
| "Scale — how many rooms and bookings/day? One building or global?" | 50 rooms/one office -> a single Postgres and a JVM lock genuinely suffice. 500K rooms across 150K orgs -> shard by org, and availability search needs its own read model. |
| "Read/write ratio on availability search vs booking?" | Heavily read-skewed (typical: ~100:1) -> justify a denormalized availability cache and accept eventual consistency on *search* while keeping the write path strict. |
| "Must a confirmed booking be strictly consistent, or is 'usually right' acceptable?" | **This is the question that defines the system.** Strict -> single-writer-per-room with a DB constraint. Eventual -> you're accepting double-bookings and need a compensation/apology flow, which is a product decision, not a technical one. |
| "Multi-tenant? Can two orgs share a room pool?" | Isolated -> `org_id` partitioning is straightforward. Shared resources across tenants (co-working spaces) -> cross-tenant conflict checks and a much harder authorization model. |

### 3.2 Requirements

**Functional (5):**
- Search available rooms by interval + attributes (capacity, equipment, floor)
- Book a room for an interval with a hard no-double-booking guarantee
- Cancel / reschedule bookings
- List bookings by room and by employee
- Recurring series with per-occurrence exceptions

**Non-Functional (4):**
- Scale: **150K orgs, ~500K rooms, ~10M bookings/day**, peak **~2K bookings/sec** (Monday 9am spike)
- Correctness: **zero double-bookings** — a correctness invariant, not an SLO
- Search latency: **P99 < 200ms** for availability queries
- Availability: 99.9%; a booking service outage stops meetings from being scheduled company-wide

### 3.3 Core Entities

| Entity | Nature |
|---|---|
| **MeetingRoom** | transactional — slow-changing, heavily cached |
| **Booking** | transactional — the contended write; the invariant lives here |
| **BookingSeries** | transactional — the recurrence rule, one per series |
| **AvailabilitySlot** | derived / ephemeral — denormalized read model, fully reconstructible |
| **BookingEvent** | append-only — audit + downstream notification trigger |

### 3.4 Scale Estimation

- **Write throughput:** 10M bookings/day = ~116/sec average, but bookings cluster hard — Monday 9-10am can carry a large share of the week's scheduling, so design for **~2K writes/sec peak**. Modest in absolute terms; the difficulty is *contention*, not volume.
- **Contention math (the number that matters):** conflicts only occur *per room*. 500K rooms and 2K bookings/sec means average per-room contention is effectively zero — **but popular rooms are the whole problem.** If 1,000 people target the same 20 "good" conference rooms at 9:00am, that's 50 concurrent attempts per room, and each serialized booking transaction taking ~10ms means the last person in that queue waits **~500ms**. That's the real latency risk, and it's why the lock must be per-room and the transaction inside it must be short.
- **Storage:** 10M bookings/day x ~500 bytes = **5 GB/day -> ~1.8 TB/year**. Rooms are trivial (500K x 1KB = 500 MB). Keep 90 days hot, archive older.
- **Search load:** at ~100:1 read ratio that's **~200K availability queries/sec** at peak — far beyond what overlap-scanning the bookings table can serve, which is what forces the read model in Stage 2.

### 3.5 Architecture Diagram

#### Stage 1 — Naive: one service, one DB, availability computed by scanning

```
   Clients (web / mobile / calendar plugins)
        |
        v
  +------------------------+
  |   Booking Service      |
  |   - search: scan all   |
  |     bookings, subtract |
  |   - book: check, then  |
  |     insert (no lock)   |
  +-----------+------------+
              |
              v
     +--------------------+
     |   Postgres         |
     |   bookings         |
     |   meeting_rooms    |
     +--------------------+
```

**BREAKING POINT 1 — double-booking under concurrency (the correctness failure).** Check-then-insert with no lock: two requests 5ms apart both read "free" and both insert. At the Monday-9am spike with ~50 concurrent attempts on a popular room, this isn't theoretical — it happens daily, and the system reports both bookings as successful. **This is the failure the whole design exists to prevent.**

**BREAKING POINT 2 — availability search doesn't scale (the throughput failure).** Computing "which of 500K rooms are free 2-3pm?" by scanning bookings is O(bookings in range). At 200K searches/sec against a table taking 2K writes/sec, read queries saturate the primary and start blocking the writes that actually matter.

**BREAKING POINT 3 — one service, global blast radius.** Search traffic (200K/sec, tolerant of staleness) and booking traffic (2K/sec, requires strict consistency) share a connection pool. A search spike starves the booking path — the cheap, tolerant workload takes down the expensive, critical one.

**DECISION — how is the no-double-booking invariant enforced?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Application check before insert | Trivial | Check-then-act race; wrong on more than one node | Rejected — the default bug |
| App-level distributed lock (Redis) only | Works cross-node; fast | Lock expiry vs transaction duration = split brain; a Redis outage silently disables correctness | Not as the only layer |
| **Per-room DB row lock + exclusion constraint** | Correct across all nodes; the constraint is undefeatable by app bugs | Serializes per room (acceptable — that's the invariant's true scope) | **Chosen** |
| Single-writer per room (partitioned actor/queue) | No locks; naturally serialized | Complex routing/failover; overkill unless contention is extreme | Only if per-room contention became pathological |

#### Stage 2 — CQRS split: strict write path, denormalized read path

```
   Clients
      |
      +-------------------- search (200K/sec) ----------------+
      |                                                        |
      | book / cancel (2K/sec)                                 v
      v                                              +---------------------+
 +----------------------+                            |  Availability       |
 |  Booking Write Svc   |                            |  Read Service       |
 |  1. per-room lock    |                            |  - slot bitmaps     |
 |     SELECT FOR UPDATE|                            |    (15-min bits)    |
 |  2. re-check overlap |                            |  - room attributes  |
 |  3. insert           |                            +----------+----------+
 |  4. emit event       |                                       ^
 +----------+-----------+                                       | read
            |                                                    |
            v                                          +---------------------+
 +--------------------------------------+              |  Redis / read       |
 |  Postgres (sharded by org_id)        |              |  replica cache      |
 |  bookings                            |              |  room:{id}:{date}   |
 |    EXCLUDE USING gist (              |              |    -> 96-bit mask   |
 |      room_id WITH =,                 |              +---------+-----------+
 |      tstzrange(start,end) WITH &&)   |                        ^
 |    <- double-booking IMPOSSIBLE      |                        |
 +------------------+-------------------+                        |
                    |                                            |
                    | CDC / outbox                               |
                    v                                            |
          +--------------------+                                 |
          |  Kafka:            |---------------------------------+
          |  booking-events    |   projector updates slot bitmaps
          +---------+----------+
                    |
                    +-----> Notification Service (see notification-service.md)
                    +-----> Analytics / room-utilization reporting
```

**Why CQRS here specifically:** the two workloads have opposite requirements. Search is 100x the volume and perfectly happy with 1-2 seconds of staleness (a room that just got booked showing as free for a moment is a minor UX annoyance — the booking attempt will fail cleanly). Booking is low-volume and needs absolute consistency. Forcing both through one path means either the search is needlessly expensive or the booking is unsafely relaxed.

**The staleness contract to state out loud:** *"Search results are advisory, not a reservation. The authoritative check happens inside the lock at write time. This is the same model as airline seat maps — the map is a hint, the purchase is the truth."*

**BREAKING POINT (Stage 2) — the popular-room hotspot.** Sharding by `org_id` balances *tenants*, not *rooms*. Within one org, the 20 desirable rooms absorb nearly all contention: 50 concurrent attempts on one room serialize through one row lock at ~10ms each, so the queue drains in ~500ms and the unlucky last user sees a half-second stall — and under a bigger spike, lock-wait timeouts. **Mitigations:** (a) keep the locked transaction minimal (no network calls inside — the event publish is already outside), (b) fail fast with a short lock timeout and return alternative rooms rather than making users wait, (c) for pathological cases, a per-room single-writer queue so attempts are ordered instead of contending.

### 3.6 Deep Dive: The No-Double-Booking Guarantee (the riskiest component)

**Why this one:** every other failure is a degradation; this one is a *correctness* failure that produces two humans walking into the same room. And it's silent — nothing alerts, the DB shows two happy rows.

**Layer 1 — the database exclusion constraint (the layer that cannot be defeated):**

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    room_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&      -- '[)' = half-open
) WHERE (status = 'CONFIRMED');                        -- cancelled rows don't block
```

Three details to point at:
- `'[)'` encodes the **half-open** convention in the schema itself, so 10-11 and 11-12 coexist. The LLD convention and the DB convention agree by construction rather than by hope.
- The partial `WHERE status = 'CONFIRMED'` means cancelled bookings free the slot without deleting history.
- This is a **GiST index**, so it simultaneously accelerates the `findOverlapping` query — one object serving correctness and performance.

**Layer 2 — the application lock:** exists for *ergonomics*, not correctness. It turns a constraint-violation exception into a clean, informative "room busy, here are the conflicts and three alternatives."

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| Constraint only | Simplest; fully correct | Every conflict is an exception after work is done; poor error detail; log noise under contention |
| Lock only | Clean errors | Fails silently across nodes if the lock layer breaks — correctness depends on app code being right |
| **Both (lock + constraint)** | Clean UX **and** an undefeatable floor | Slightly more machinery; conflict path handled in two places |
| Serializable isolation on the whole transaction | Correct without extra objects | Serialization failures under contention need retry logic anyway, and it penalizes every unrelated query in the transaction |

**Decision:** both. The sentence that lands it: *"If I had to delete one, I'd keep the constraint — the lock protects the user experience, the constraint protects the invariant. Only one of those is allowed to fail."*

### 3.7 Trade-offs

**Trade-off 1: CQRS with an eventually-consistent read model vs a single strictly-consistent path**
- **Chose:** CQRS — strict writes, ~1-2s stale search
- **Gain:** search scales independently to 200K/sec without touching the write primary; a search spike can't starve bookings
- **Lose:** a just-booked room can appear free for a second, and a user occasionally hits a clean failure at confirm time
- **Failure mode if wrong:** serving search from the write primary means the Monday-9am search storm consumes the connection pool and **booking latency spikes exactly when booking demand peaks** — the read path takes down the write path it's supposed to support.

**Trade-off 2: Per-room pessimistic lock vs optimistic retry**
- **Chose:** pessimistic per-room lock
- **Gain:** conflicts detected before work is wasted; clean errors with conflict details; predictable behavior under the contention pattern this system actually has (many users, few desirable rooms)
- **Lose:** bookings for the same room serialize; a slow transaction blocks that room's queue
- **Failure mode if wrong:** optimistic retry under 50-way contention on one room means ~50 attempts, ~49 failures, and a retry storm that amplifies load precisely at the spike — the classic optimistic-locking-under-high-contention trap.

**Trade-off 3: Materialized recurring instances vs computing occurrences on read**
- **Chose:** materialize over a rolling 6-month horizon
- **Gain:** conflict detection is an ordinary indexed overlap query; single-occurrence cancellation is a normal row update
- **Lose:** storage multiplies for long series, and series edits fan out to many rows via a background job
- **Failure mode if wrong:** rule-only storage means every conflict check must expand every recurring series in the org to test one interval — an unindexable O(series) computation on the hot booking path, which collapses as soon as recurring meetings are popular.

### 3.8 API Design

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| GET | `/v1/rooms/available` | JWT | `?start=&end=&capacity=&equipment=&floor=` | `{rooms[], asOf}` | 200, 400 |
| POST | `/v1/bookings` | JWT | `{roomId, start, end, attendees[], recurrence?}` + `Idempotency-Key` header | `{bookingId, status}` | 201, **409 (conflict + alternatives)**, 400 |
| DELETE | `/v1/bookings/{id}` | JWT | — | — | 204, 403, 404 |
| PATCH | `/v1/bookings/{id}` | JWT | `{start?, end?, roomId?}` | `{booking}` | 200, 409, 403 |
| GET | `/v1/rooms/{id}/bookings` | JWT | `?from=&to=` | `{bookings[]}` | 200 |
| GET | `/v1/employees/{id}/bookings` | JWT | `?from=&to=` | `{bookings[]}` | 200, 403 |

**Two derivation notes worth saying:**
- **`asOf` on the search response** makes the staleness contract explicit to clients instead of pretending the read model is authoritative.
- **409 returns alternatives**, not just an error. Under contention, the difference between a usable and an infuriating system is whether losing a race gives you three other free rooms in the same response.

### 3.9 Data Model

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE meeting_rooms (
    room_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      UUID NOT NULL,
    name        VARCHAR(128) NOT NULL,
    capacity    SMALLINT NOT NULL,
    floor       SMALLINT,
    building_id UUID,
    equipment   VARCHAR(32)[] DEFAULT '{}',      -- PROJECTOR, VIDEO_CONF, ...
    timezone    VARCHAR(40) NOT NULL,            -- rooms are physical: local tz matters
    active      BOOLEAN DEFAULT TRUE,
    UNIQUE (org_id, name)
);

CREATE INDEX idx_room_search ON meeting_rooms (org_id, capacity, floor)
    WHERE active;

CREATE TABLE booking_series (
    series_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    organizer_id  UUID NOT NULL,
    rule_type     VARCHAR(16) NOT NULL,          -- DAILY | WEEKLY | MONTHLY
    rule_expr     VARCHAR(128),                  -- e.g. "BYDAY=MO,WE,FR"
    series_end    TIMESTAMPTZ,                   -- NULL = open-ended
    materialized_until TIMESTAMPTZ NOT NULL      -- rolling horizon watermark
);

CREATE TABLE bookings (
    booking_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID NOT NULL,
    room_id      UUID NOT NULL REFERENCES meeting_rooms(room_id),
    organizer_id UUID NOT NULL,
    series_id    UUID REFERENCES booking_series(series_id),   -- NULL = one-off
    start_time   TIMESTAMPTZ NOT NULL,
    end_time     TIMESTAMPTZ NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    attendees    UUID[] DEFAULT '{}',
    idem_key     VARCHAR(128),
    version      INTEGER NOT NULL DEFAULT 0,     -- optimistic lock for edits
    created_at   TIMESTAMPTZ DEFAULT now(),

    CHECK (start_time < end_time),

    -- THE invariant. Double-booking is now physically impossible.
    CONSTRAINT no_overlapping_bookings EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED'),

    UNIQUE (organizer_id, idem_key)              -- double-submit protection
);

-- The two named access patterns from the prompt, each indexed.
CREATE INDEX idx_by_room ON bookings (room_id, start_time)
    WHERE status = 'CONFIRMED';
CREATE INDEX idx_by_employee ON bookings (organizer_id, start_time)
    WHERE status = 'CONFIRMED';
```

| Decision | Why | What breaks otherwise |
|---|---|---|
| `EXCLUDE USING gist` with `'[)'` | Makes the core invariant a property of the schema, and encodes half-open semantics so back-to-back bookings are legal | Correctness depends on every code path getting locking right forever — one missed path (a script, a new service, a bug) silently double-books |
| Partial constraint `WHERE status='CONFIRMED'` | Cancelled bookings release the slot without deleting the audit trail | Either cancelled rows keep blocking the room, or you hard-delete and lose history |
| `series_id` + `materialized_until` | Recurring instances are real rows (indexable for conflicts) with a bounded horizon extended by a job | Rule-only storage makes conflict detection unindexable; materialize-forever makes "daily, no end date" unbounded |
| `timezone` on the **room**, not the booking | The room is a physical place; "2pm" means 2pm there. Store UTC instants + room tz | Recurring series break across DST — the 9am standup drifts to 8am or 10am for half the year |
| `version` column | Optimistic locking for *edits* — the right tool for single-row updates | Lost updates when two people edit the same booking's attendee list concurrently |
| `UNIQUE (organizer_id, idem_key)` | A double-clicked "Book" button can't create two bookings | The retry creates a second booking that then conflicts with the first — a self-inflicted 409 |
| Both indexes partial on `CONFIRMED` | The two required queries only ever want live bookings; cancelled rows stay out of the hot index | Index bloats with historical cancellations, slowing the queries that matter |

### 3.10 Salesforce Multi-Tenancy Angle

> *"This maps to Salesforce Scheduler / Lightning Scheduler — appointment booking as a product. I'd put `org_id` on every table and lead every index with it, and shard by `org_id` so a large tenant's booking volume can't affect another's. The subtle multi-tenant point is that the **exclusion constraint is naturally scoped by `room_id`, and rooms belong to exactly one org**, so tenant isolation for the core invariant comes for free — there's no cross-tenant conflict surface unless we introduce shared resources like co-working spaces, which would need cross-org conflict checks and a much harder authorization model."*

Worth adding: **per-org quotas on booking volume and horizon** (e.g. no booking more than 6 months out, max N active recurring series) — this is exactly the governor-limit pattern, and it prevents one org materializing a decade of daily recurring meetings and consuming shared storage.

---

## 4.  Navigation Pivots — THIS Problem

**Opening Protocol (first 2 minutes — verbatim, per `format.md` Section 2):**

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
> *(If no preference:)* "I'll start with LLD — entities, the interval abstraction, and the concurrency control, since the prompt calls out race conditions explicitly. Then I'll zoom out to how this scales across orgs. I'll flag the transition."

| Interviewer Says | What They Want | Your Move |
|---|---|---|
| "Two users book the same room simultaneously" | **The core question** — expect it early and often | Availability read is not a guard; per-room lock + re-check inside + exclusion constraint as the undefeatable floor |
| "How do you detect overlap?" | LLD precision | `start < other.end && other.start < end`, half-open, one method on `TimeInterval`; and `tstzrange && ` in SQL |
| "Now 500K rooms and 200K searches/sec" | HLD scale-out | CQRS split: strict write path + denormalized slot-bitmap read model, with the staleness contract stated |
| "Does a 10-11 booking conflict with 11-12?" | Boundary rigor | "No — half-open intervals, and it's encoded in the DB with `'[)'` so code and schema agree" |
| "Recurring meetings?" | Modelling judgment | Rule + materialized rolling horizon; explain why rule-only kills conflict indexing |
| "One room is wildly popular" | HLD hotspot | Sharding by org doesn't fix per-room contention; short transactions, fail-fast with alternatives, optional per-room single-writer queue |
| "Where does notification fit?" | Cross-problem awareness | Domain event published **outside** the lock -> that's the Notification Service problem; never hold a lock across a broker call |

---

## 5.  TL;DR — Dual-Level Pitch

At the class level everything orbits `TimeInterval`, an immutable value object owning the half-open overlap rule so back-to-back meetings don't falsely conflict, with `Booking` referencing room and employee by ID to keep the aggregate tight. The correctness requirement is no double-booking, and the key insight is that the availability read can never be the guarantee — it's a check-then-act race — so the write path takes a per-room `SELECT ... FOR UPDATE`, re-checks overlap **inside** the lock, and sits on top of a Postgres `EXCLUDE USING gist` constraint that makes overlapping confirmed bookings physically impossible even if the lock is bypassed. At the system level it's CQRS: a strict low-volume write path against sharded Postgres, and a denormalized slot-bitmap read model serving ~200K availability searches/sec with a stated 1-2 second staleness contract, since search is advisory and only the write is authoritative. The bottleneck to name isn't total volume but per-room contention — 50 people racing for the same desirable room at 9am serialize through one lock — so transactions stay short, events publish outside the lock, and conflicts return alternative rooms rather than a bare error.

---

##  Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created — fourth problem in `Interview/Salesforce/HLD+LLD/`. Built from the verbatim CodingKaro Dec 2025 Meeting Room Reservation prompt (which names concurrency/race conditions explicitly). Represents the #1 HLD cluster (5 booking/reservation instances 2023-2025). Follows `solution-notes-standards.md` and matches the derivation-first bar from the prior three files. |
