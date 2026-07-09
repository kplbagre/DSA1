# BookMyShow — Movie Ticket Booking

> **Standard followed:** `LLD/notes-standards.md`

---

## 🎯 Problem Statement

Design a movie ticket booking system. Users browse shows for a movie, pick seats by tier (Gold, Silver, Platinum), and confirm a booking. The system must prevent two users from booking the same seat for the same show simultaneously.

---

## 📖 Requirements

**Functional:**
- List movies and their shows
- Browse available seats for a show, filtered by tier
- Book a seat for a show — returns a `Booking` confirmation
- Cancel a booking — returns the seat to `AVAILABLE`
- Notify interested parties (email, analytics) when a booking is confirmed

**Non-functional:**
- Thread-safe — two users selecting the same seat simultaneously must never result in a double booking
- New seat tier (e.g., `PREMIUM`) = one new class + one factory case, nothing else changes
- Notification channels are pluggable — adding SMS should not touch booking logic

---

## 🏗️ Class Design

### 🎨 Visual — Class Structure

```
┌──────────────────────────────────────────────────────┐
│                    BookMyShow                        │
│  - movies: Map<String, Movie>                        │
│  - shows:  Map<String, Show>                         │
│  + getAvailableSeats(showId, tier): List<Seat>       │
│  + bookSeat(showId, seatId, userId): Booking         │
│  + cancelBooking(bookingId): void                    │
└──────────────────────────────────────────────────────┘
         │ contains                      │ registers
         ▼                               ▼
┌──────────────────────┐    ┌──────────────────────────────┐
│        Show          │    │  <<interface>>               │
│  - showId: String    │    │  BookingObserver              │
│  - movie: Movie      │    │  + onBookingConfirmed(b)     │
│  - seats: Map<>      │    └──────────────┬───────────────┘
│  + bookSeat(id,user) │                   │ implements
│  + cancelSeat(id)    │       EmailNotificationObserver
└──────────┬───────────┘       AnalyticsObserver
           │ 1..*
           ▼
┌──────────────────────┐        SeatFactory
│  <<interface>>       │        + create(SeatTier, id)
│  Seat                │               │ creates
│  + getId()           │               ▼
│  + getStatus()       │    GoldSeat  SilverSeat  PlatinumSeat
│  + getTier()         │    (all implement Seat)
│  + book()            │
│  + cancel()          │
└──────────────────────┘

Booking
  - bookingId: String
  - showId:    String
  - userId:    String
  - seat:      Seat
  - bookedAt:  LocalDateTime

SeatStatus (enum): AVAILABLE, BOOKED
SeatTier   (enum): GOLD, SILVER, PLATINUM

KEY INVARIANT:
   Show.bookSeat() is synchronized — the check (AVAILABLE?) and the act
   (status = BOOKED) are one atomic block. No two threads can double-book
   the same seat. Same race as two users checking out the last item in stock.
```

---

## 🔌 Key Interfaces

```java
/**
 * Contract every seat must fulfil.
 * Implementations carry tier-specific data (price, row prefix).
 */
public interface Seat {

    String getId();

    SeatTier getTier();

    SeatStatus getStatus();

    // Throws IllegalStateException if already BOOKED
    void book();

    void cancel();
}
```

```java
/**
 * Observer notified when a booking is confirmed.
 * Each implementation handles one concern — email, analytics, inventory.
 * Separation of Concerns: adding SMS = one new class, zero booking logic touched.
 */
public interface BookingObserver {

    void onBookingConfirmed(Booking booking);
}
```

---

## 🧭 Design Decisions

| Decision | Why |
|---|---|
| **Observer for notifications** | Email and analytics both react to booking events independently. `Show` fires the event; each observer handles its own concern. Adding SMS = one new class. Open-Closed + SoC. |
| **State for seat lifecycle** | A seat transitions AVAILABLE → BOOKED → AVAILABLE. `book()` and `cancel()` enforce valid transitions and throw on illegal ones. If states get more complex (LOCKED → BOOKED), State pattern is the natural extension point. |
| **Factory for seat creation** | `Show` never calls `new GoldSeat()` directly. `SeatFactory.create(tier, id)` centralises construction. New tier = one new class + one factory case. Open-Closed. |
| **`synchronized` on `Show.bookSeat()`** | Two users booking seat A1 is a check-then-act race — both threads see AVAILABLE, both proceed to `book()`. Per-show `synchronized` makes the check+act atomic. Commerce parallel: identical to two users checking out the last item in stock. |

---

## 🎨 Visual — Object Interaction

```
Two Users Racing for Seat A1 (same show):

User A Thread             Show (synchronized)            Seat A1
    │                            │                           │
    │  bookSeat("A1","userA")    │                           │
    │───────────────────────────▶│  LOCK acquired            │
    │                            │  seat.getStatus()         │
    │                            │──────────────────────────▶│
    │                            │◀── AVAILABLE ─────────────│
    │                            │  seat.book()              │
    │                            │──────────────────────────▶│
    │                            │  status = BOOKED          │
    │                            │  notifyObservers()        │
    │                            │  LOCK released            │
    │◀───── Booking returned ────│                           │

User B Thread (arrives while A holds lock):
    │  bookSeat("A1","userB")    │
    │───────────────────────────▶│  BLOCKED — waiting for lock
    │                            │  ... waits ...
    │                            │  LOCK acquired (A released it)
    │                            │  seat.getStatus()         │
    │                            │──────────────────────────▶│
    │                            │◀── BOOKED ────────────────│
    │◀── IllegalStateException ──│  seat already booked      │

KEY INVARIANT:
   Per-show locking: threads for the same show block each other.
   Threads for different shows run fully concurrently — no shared lock.
```

---

## 🖊️ Coding Skeleton

**Interview coding order — write in this sequence to never get stuck:**

1. **Enums** — `SeatStatus`, `SeatTier` (zero dependencies, write first)
2. **Interfaces** — `Seat`, `BookingObserver` (contracts before implementations)
3. **Domain** — `Booking` (depends only on enums and interfaces)
4. **Concrete seats** — `GoldSeat` in full; stub `SilverSeat`, `PlatinumSeat` as "same pattern"
5. **Factory** — `SeatFactory.create(SeatTier, id)` — one switch, 5 lines
6. **Observers** — `EmailNotificationObserver`, `AnalyticsObserver` (simple, no dependencies)
7. **`Show`** — the critical class: holds seats, `synchronized bookSeat()`, fires observers
8. **`BookMyShow`** — orchestrator: holds movies + shows, delegates to Show

**Show skeleton — the critical class:**

```java
// thread-safe: synchronized on bookSeat and cancelSeat (per-show granularity)
public class Show {

    private final String showId;
    private final Movie movie;
    private final Map<String, Seat> seats;
    private final List<BookingObserver> observers = new ArrayList<>();

    // synchronized — prevents double-booking race condition
    // Commerce parallel: same as "two users checkout the last item in stock"
    public synchronized Booking bookSeat(String seatId, String userId) {
        Seat seat = seats.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("Seat not found: " + seatId);
        }
        seat.book();   // throws IllegalStateException if already BOOKED
        Booking booking = new Booking(
            UUID.randomUUID().toString(), showId, userId, seat, LocalDateTime.now()
        );
        notifyObservers(booking);
        return booking;
    }

    public synchronized void cancelSeat(String seatId) {
        Seat seat = seats.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("Seat not found: " + seatId);
        }
        seat.cancel();
    }

    private void notifyObservers(Booking booking) {
        for (BookingObserver observer : observers) {
            observer.onBookingConfirmed(booking);
        }
    }

    public void registerObserver(BookingObserver observer) {
        observers.add(observer);
    }
}
```

---

## 🔁 Concurrency — Making It Thread-Safe

**Shared mutable state — the fields that get corrupted without locks:**

| Field | Problem without lock | Fix |
|---|---|---|
| `Seat.status` | Thread A and B both read AVAILABLE → both call `book()` → double booking | `synchronized` on `Show.bookSeat()` makes the check+act atomic |
| `Show.observers` list | Multiple threads registering observers while booking fires them | Register all observers at startup (before any booking) — no runtime writes |

**Strategy: per-show synchronized method**

```java
// Thread A (Show1) and Thread B (Show1) → both call bookSeat → one blocks
// Thread C (Show2) calls bookSeat concurrently with A and B — different Show
// object, different lock, no contention
public synchronized Booking bookSeat(String seatId, String userId) { ... }
```

**Upgrade path for high traffic:**
Replace `synchronized` with per-seat CAS using `AtomicReference<SeatStatus>`.
Try `compareAndSet(AVAILABLE, BOOKED)` — if it returns `false`, another thread won, return failure to caller. This eliminates blocking for seats that don't conflict.

---

## 📐 "What Would You Do Differently?"

> *"With more time, I'd add a LOCKED state — seat reserved in cart for 10 minutes, then auto-released by a scheduled job reading a `lockedUntil` timestamp. That's the real production seat-hold pattern (BookMyShow, Ticketmaster). I'd also move from per-show `synchronized` to per-seat optimistic locking with `AtomicReference<SeatStatus>` and CAS — higher throughput, no blocking between non-conflicting seats. At scale (multiple app servers), the shared state moves to the DB — optimistic locking with a `version` column, return 409 on conflict, let the client retry."*

---

## 🔬 Interview Q&As

### Q: "Walk me through how double-booking is prevented."
> Two users clicking seat A1 is a check-then-act race: both threads read `status == AVAILABLE`, both proceed to `book()`. Without synchronization, both succeed — one seat, two bookings. The fix is synchronizing `Show.bookSeat()` — only one thread executes the check+act block at a time. The loser reads `BOOKED` and gets `IllegalStateException`. This is the exact same race as two users checking out the last item in an e-commerce cart.

### Q: "Why Observer for notifications and not call email directly in bookSeat()?"
> If `Show.bookSeat()` calls `emailService.send()` directly, it has two reasons to change: booking logic and notification logic. Separation of Concerns says these are separate responsibilities. Observer decouples them — `Show` fires `onBookingConfirmed(booking)`, observers handle it. Adding SMS = one new class implementing `BookingObserver`, zero booking code touched. Open-Closed.

### Q: "What is the seat lifecycle — how does it transition?"
> Two states: AVAILABLE → BOOKED (via `book()`) → AVAILABLE (via `cancel()`). `book()` on an already-BOOKED seat throws `IllegalStateException`. For production, I'd add LOCKED (seat held in cart, not yet paid) with a `lockedUntil` timestamp — the State pattern handles this cleanly because LOCKED's transition rules don't touch AVAILABLE or BOOKED logic.

### Q: "How do you handle multi-seat booking atomically?"
> Wrap the entire multi-seat loop inside one `synchronized (show)` block. If any seat is unavailable mid-loop, cancel all previously booked seats (compensating rollback) before throwing. This is a mini-saga within a single Show — book-all-or-rollback-all.

### Q: "How would you scale this to 1 million concurrent users?"
> In-memory per-show locking breaks on horizontal scaling — two app servers running `Show` independently would double-book. At scale: distributed lock on `(showId, seatId)` using Redis SETNX with TTL, or optimistic locking in DB with a `version` column on the Seat row. Return 409 on version conflict; the client retries.

---

## 🧾 TL;DR — 30-Second Pitch

> *"I have a `Show` that holds a map of `Seat` objects — GoldSeat, SilverSeat, PlatinumSeat — all behind a `Seat` interface, created by `SeatFactory`. `bookSeat()` is synchronized at the show level: the check-then-act race is the double-booking problem, identical to two users checking out the last item in a cart. On confirmation, an Observer list fires — email and analytics are separate classes, so adding SMS is one new file. Cancellation flips the seat back to AVAILABLE. Scaling beyond one JVM means a distributed lock or DB optimistic locking."*

---

## 🔗 Patterns Used

- **Observer** — `BookingObserver` notifies email + analytics on booking confirmation. See **`LLD/DesignPatterns/02-observer.md`**.
- **Factory** — `SeatFactory.create(tier, id)` creates seat implementations. See **`LLD/DesignPatterns/01-factory-strategy.md`** (Factory section).
- **State** — `SeatStatus` drives the AVAILABLE → BOOKED lifecycle via `book()` / `cancel()`. See **`LLD/DesignPatterns/05-state.md`**.

---

## 🖊️ Full Implementation

> All classes in one place for review. Read top to bottom — enums → interfaces → domain → concrete classes → orchestrator.

### SeatStatus.java

```java
public enum SeatStatus {
    AVAILABLE,
    BOOKED
}
```

### SeatTier.java

```java
public enum SeatTier {
    GOLD,
    SILVER,
    PLATINUM
}
```

### Seat.java

```java
/**
 * Contract every seat must fulfil.
 * Implementations carry tier-specific data (price, row prefix, etc.).
 */
public interface Seat {

    String getId();

    SeatTier getTier();

    SeatStatus getStatus();

    // Throws IllegalStateException if already BOOKED
    void book();

    // Throws IllegalStateException if already AVAILABLE
    void cancel();
}
```

### BookingObserver.java

```java
/**
 * Observer notified when a booking is confirmed.
 * Adding SMS = one new class, zero booking logic touched. Open-Closed.
 */
public interface BookingObserver {

    void onBookingConfirmed(Booking booking);
}
```

### Booking.java

```java
import java.time.LocalDateTime;

public class Booking {

    private final String bookingId;
    private final String showId;
    private final String userId;
    private final Seat seat;
    private final LocalDateTime bookedAt;

    public Booking(String bookingId, String showId, String userId, Seat seat, LocalDateTime bookedAt) {
        this.bookingId = bookingId;
        this.showId = showId;
        this.userId = userId;
        this.seat = seat;
        this.bookedAt = bookedAt;
    }

    public String getBookingId() { return bookingId; }
    public String getShowId()    { return showId; }
    public String getUserId()    { return userId; }
    public Seat getSeat()        { return seat; }
    public LocalDateTime getBookedAt() { return bookedAt; }
}
```

### GoldSeat.java

```java
// SilverSeat and PlatinumSeat are identical — only getTier() differs.
// In production: extract shared logic to an AbstractSeat base class (DRY).
public class GoldSeat implements Seat {

    private final String id;
    private SeatStatus status;

    public GoldSeat(String id) {
        this.id = id;
        this.status = SeatStatus.AVAILABLE;
    }

    @Override
    public String getId() { return id; }

    @Override
    public SeatTier getTier() { return SeatTier.GOLD; }

    @Override
    public SeatStatus getStatus() { return status; }

    @Override
    public void book() {
        if (status == SeatStatus.BOOKED) {
            throw new IllegalStateException("Seat " + id + " is already booked.");
        }
        status = SeatStatus.BOOKED;
    }

    @Override
    public void cancel() {
        if (status == SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat " + id + " is already available.");
        }
        status = SeatStatus.AVAILABLE;
    }
}
```

### SeatFactory.java

```java
/**
 * Factory — Show never calls new GoldSeat() directly.
 * Adding PREMIUM tier = one new class + one case here. Open-Closed.
 */
public class SeatFactory {

    public static Seat create(SeatTier tier, String id) {
        switch (tier) {
            case GOLD:
                return new GoldSeat(id);
            case SILVER:
                return new SilverSeat(id);
            case PLATINUM:
                return new PlatinumSeat(id);
            default:
                throw new IllegalArgumentException("Unknown tier: " + tier);
        }
    }
}
```

### EmailNotificationObserver.java

```java
public class EmailNotificationObserver implements BookingObserver {

    @Override
    public void onBookingConfirmed(Booking booking) {
        // inject EmailClient in production; omit println per code standards
        String recipient = booking.getUserId();
        String subject = "Booking confirmed — seat " + booking.getSeat().getId();
        // emailClient.send(recipient, subject, buildBody(booking));
    }
}
```

### AnalyticsObserver.java

```java
public class AnalyticsObserver implements BookingObserver {

    @Override
    public void onBookingConfirmed(Booking booking) {
        // publish to Kafka or analytics service in production
        // analyticsClient.publish("BOOKING_CONFIRMED", booking);
    }
}
```

### Show.java

```java
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One screening of a movie. Holds the seat map and booking logic.
 *
 * thread-safe: synchronized on bookSeat/cancelSeat (per-show lock).
 * Commerce parallel: "two users booking the last seat" == "two users
 * checking out the last item in stock" — both are check-then-act races.
 */
public class Show {

    private final String showId;
    private final String movieTitle;
    private final Map<String, Seat> seats;          // seatId → Seat
    private final List<BookingObserver> observers;

    public Show(String showId, String movieTitle, Map<String, Seat> seats) {
        this.showId = showId;
        this.movieTitle = movieTitle;
        this.seats = seats;
        this.observers = new ArrayList<>();
    }

    // synchronized — check (AVAILABLE?) + act (book()) is one atomic block
    public synchronized Booking bookSeat(String seatId, String userId) {
        Seat seat = seats.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("Seat not found: " + seatId);
        }
        seat.book();    // throws IllegalStateException if already BOOKED
        Booking booking = new Booking(
            UUID.randomUUID().toString(), showId, userId, seat, LocalDateTime.now()
        );
        notifyObservers(booking);
        return booking;
    }

    public synchronized void cancelSeat(String seatId) {
        Seat seat = seats.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("Seat not found: " + seatId);
        }
        seat.cancel();
    }

    public List<Seat> getAvailableSeats(SeatTier tier) {
        return seats.values().stream()
            .filter(s -> s.getTier() == tier && s.getStatus() == SeatStatus.AVAILABLE)
            .collect(Collectors.toList());
    }

    public void registerObserver(BookingObserver observer) {
        observers.add(observer);
    }

    public String getShowId() { return showId; }

    private void notifyObservers(Booking booking) {
        for (BookingObserver observer : observers) {
            observer.onBookingConfirmed(booking);
        }
    }
}
```

### BookMyShow.java

```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level orchestrator. Delegates all booking logic to Show.
 * Does not contain booking logic itself — Separation of Concerns.
 */
public class BookMyShow {

    private final Map<String, Show> shows = new HashMap<>();

    public void addShow(Show show) {
        shows.put(show.getShowId(), show);
    }

    public List<Seat> getAvailableSeats(String showId, SeatTier tier) {
        return getShow(showId).getAvailableSeats(tier);
    }

    public Booking bookSeat(String showId, String seatId, String userId) {
        return getShow(showId).bookSeat(seatId, userId);
    }

    public void cancelSeat(String showId, String seatId) {
        getShow(showId).cancelSeat(seatId);
    }

    private Show getShow(String showId) {
        Show show = shows.get(showId);
        if (show == null) {
            throw new IllegalArgumentException("Show not found: " + showId);
        }
        return show;
    }
}
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 9, 2026 | Canonical note created. All classes in single MD. Status: canonical reference — Kapil has not self-attempted yet. |
