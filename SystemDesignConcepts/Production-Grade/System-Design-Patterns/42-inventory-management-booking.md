# Inventory Management & Booking Systems

> **Standard followed:** `notes-standards.md`
> **Related concepts:** `../../Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md`, `../../Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md`, `../../Foundations/Concurrency-and-Consistency/04-idempotency.md`

---

## 📖 What is an Inventory Management & Booking System?

**Full form:** Inventory Management & Booking System (Concurrency-safe Stock Reservation and Deduction)

**Simple analogy:** Imagine a popular concert with 500 seats. The box office has a single counter with one agent. Everyone who calls in gets told "seats available" until the agent physically marks a seat as sold. The agent is the serialization point — they ensure no two callers get the same seat. An inventory system is the digital version of that agent: it must guarantee that "1 item left" is only given to exactly 1 buyer, even when 10,000 people click "Buy" simultaneously.

**Core principle:** An inventory management system controls the lifecycle of stock: from available, through a soft reservation (tentative hold), to confirmed (sold) or released (expired). The central problem it solves is overselling — ensuring that a count of N items is only reduced by exactly N successful purchases, regardless of how many concurrent requests arrive.

**Why it matters in system design:** Flash sales, concert tickets, flight seats, and hotel rooms all share this problem. The wrong design — reading count then decrementing without atomicity — silently oversells. The right design appears in every senior system design interview involving e-commerce, ticketing, or booking platforms.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| Oversell | Confirming more orders than available inventory — the core failure mode this pattern prevents | 1,000 users all see "1 item left" and buy; naive system confirms all 1,000 |
| Soft Reserve (Hold) | Atomically moving a unit from AVAILABLE to SOFT_RESERVED with a TTL before payment is confirmed | Ticketmaster holds your seat for 5 minutes while you enter payment details |
| TTL-Based Release | Automatic return of a soft-reserved unit to AVAILABLE when the hold timer expires without confirmation | Airbnb's 10-minute hold expires → listing becomes bookable again |
| Atomic Decrement | A Redis `DECR` or SQL `UPDATE ... SET count = count - 1 WHERE count > 0` that prevents two threads both seeing count=1 | `DECR inventory:item:X` returns 0 to exactly one caller; all others get negative values |
| Two-Step Commit | The reserve-then-confirm lifecycle: first atomically reserve, then confirm on payment success (or release on failure) | Step 1: SOFT_RESERVED on checkout start; Step 2: CONFIRMED on payment success |
| Reservation Pattern | The full lifecycle — AVAILABLE → SOFT_RESERVED (on checkout) → CONFIRMED (on payment) or RELEASED (on TTL/cancel) | Used by Airbnb, BookMyShow, airline booking systems |
| Flash Sale Contention | Extreme concurrent demand for the same item where even a single-key Redis DECR becomes a throughput bottleneck | Walmart Big Billion Day: 50,000 requests/sec on one item; use sharded Redis counters |
| Idempotency Key (booking) | A client-generated UUID sent in the reservation request to prevent duplicate reservations from API retries | Mobile app retries reservation on network timeout; same idempotency key returns existing reservation ID |

---

## 🎯 Why This Matters

The overselling problem causes real financial loss and customer trust damage — confirming 1,000 orders for 1 item requires refunds, compensation, and support costs. It appears in **system design rounds** for any booking, ticketing, or e-commerce problem. Senior engineers are expected to propose the reservation pattern, name the specific mechanism (SELECT FOR UPDATE, Redis DECR, optimistic lock), and explain the TTL-based release strategy — not just say "use a transaction."

---

## 🧠 The Mental Model

Think of a popular restaurant with 10 tables. When you call to reserve, the host puts a sticky note on a table with your name and a 15-minute timer — "Hold for Priya, expires 8:30 PM." The table is no longer available to new callers. If Priya doesn't arrive and confirm by 8:30, the sticky note is removed and the table goes back to available. If Priya arrives, the sticky note becomes a permanent booking in the reservation book.

This is the **reservation pattern** — the exact strategy used by Airbnb, BookMyShow, and flight booking systems. The sticky note is the **soft reserve** (tentative hold with a TTL). The permanent booking entry is the **confirmed reservation**. The timer expiry is the **automatic release**. The critical insight: the host never holds two sticky notes on the same table simultaneously — that's the atomicity requirement.

What goes wrong without this pattern: the host reads "10 tables available," tells 10 callers "yes," and tries to write 10 confirmations — but it's actually the same table being promised multiple times. That's the oversell. The sticky-note step (the atomic "reserve before confirm") is what prevents it.

The key insight is: **you must transition inventory to a reserved state atomically before the user confirms payment** — never just decrement count at payment time.

---

## 🎨 Visual — System Topology & Component Flow

```
FULL SYSTEM TOPOLOGY:
Inventory service sits between the user-facing API and the database

┌───────────┐    ┌───────────┐    ┌──────────────────────────────────┐
│  Client   │───▶│    LB     │───▶│         API Gateway              │
│  Browser  │    │           │    └──────────────┬───────────────────┘
└───────────┘    └───────────┘                   │
                                                 ▼
                                  ┌──────────────────────────────────┐
                                  │      Checkout / Order Service    │
                                  │                                  │
                                  │  1. Reserve inventory (→ Inv.)   │
                                  │  2. Process payment (→ PSP)      │
                                  │  3. Confirm/Release inventory    │
                                  └───────────┬──────────────────────┘
                                              │
                          ┌───────────────────┼─────────────────────┐
                          ▼                   ▼                     ▼
               ┌──────────────────┐  ┌────────────────┐  ┌─────────────────┐
               │ Inventory Service│  │ Payment Service│  │  TTL Expiry Job │
               │                  │  │   (Stripe/PSP) │  │  (releases soft │
               │ - soft reserve   │  │                │  │   reserves)     │
               │ - confirm        │  └────────────────┘  └─────────────────┘
               │ - release        │
               └────────┬─────────┘
                        │
              ┌─────────┴──────────┐
              ▼                    ▼
   ┌───────────────────┐  ┌────────────────────┐
   │  Redis (fast      │  │  PostgreSQL/MySQL   │
   │  atomic DECR,     │  │  (durable source    │
   │  soft reserve     │  │   of truth,         │
   │  cache)           │  │   SELECT FOR UPDATE)│
   └───────────────────┘  └────────────────────┘

KEY INVARIANT:
   Inventory service is the single serialization point.
   No other service reads-then-writes inventory directly.
   Redis DECR provides atomicity at speed; DB is the durable truth.
```

```
COMPONENT DETAIL: Reservation State Machine

                       ┌─────────────┐
                       │  AVAILABLE  │
                       │  (count=N)  │
                       └──────┬──────┘
                              │ User clicks "Buy" / "Reserve"
                              │ [atomic decrement or SELECT FOR UPDATE]
                              ▼
                       ┌─────────────┐
                       │ SOFT-RESERVED│
                       │ (hold + TTL) │◀──── TTL starts (10 min Airbnb,
                       └──────┬──────┘       5 min Ticketmaster)
                              │
               ┌──────────────┼──────────────┐
               │                             │
    User confirms payment            TTL expires (no payment)
    (payment succeeds)               OR user cancels
               │                             │
               ▼                             ▼
        ┌──────────────┐            ┌────────────────┐
        │  CONFIRMED   │            │    RELEASED    │
        │  (inventory  │            │  → back to     │
        │   deducted,  │            │  AVAILABLE     │
        │   order made)│            │  (count+1)     │
        └──────────────┘            └────────────────┘

KEY INVARIANT:
   A unit of inventory exists in exactly ONE state at any time.
   The transition from AVAILABLE → SOFT-RESERVED must be atomic.
   Released inventory returns to the pool — TTL is the safety net
   against holds that are never confirmed.
```

---

## ⚙️ How It Actually Works

**Steps (SELECT FOR UPDATE — Pessimistic Locking approach):**

1. **User requests reservation** — API receives "reserve 1 unit of item X."
2. **Lock the inventory row** — use `SELECT FOR UPDATE` to prevent concurrent reads-then-writes on the same row. All other reservation attempts on this item queue behind this lock.
3. **Check availability** — inside the locked transaction, read the current count. If count > 0, proceed.
4. **Decrement and create reservation record** — atomically decrement count and insert a reservation row with a TTL timestamp.
5. **Commit transaction** — lock releases; next waiter proceeds with the updated count.
6. **User confirms payment** — on payment success, update reservation status to CONFIRMED.
7. **TTL expiry job** — a background job periodically queries expired soft-reserves and returns them to available count.

**Steps (Redis DECR — high-throughput approach):**

1. **Pre-load inventory count into Redis** — `SET inventory:item:X 100` on service startup or item publication.
2. **Atomic decrement** — `DECR inventory:item:X` returns the new value. If the returned value is >= 0, reservation is allowed. If < 0, immediately increment back (`INCR`) and reject.
3. **Write reservation to database** — asynchronously persist the reservation with TTL to the DB (the durable source of truth).
4. **On payment confirmation** — update DB status to CONFIRMED, Redis count stays decremented.
5. **On TTL expiry or cancel** — increment Redis count back (`INCR inventory:item:X`) and update DB status to RELEASED.

### What is SELECT FOR UPDATE, and why does it fit here?

`SELECT FOR UPDATE` is a SQL clause that acquires a row-level exclusive lock as part of a SELECT query. No other transaction can read-for-update or write the same row until this transaction commits. In an inventory context: "I am reading this inventory count and I intend to write it — nobody else may read-to-write until I'm done." In an interview, if asked: "SELECT FOR UPDATE is pessimistic locking at the DB layer — it serializes concurrent reservation attempts on the same item, preventing two transactions from both reading count=1 and both deciding they can reserve it."

### What is Redis DECR, and why does it fit here?

`DECR` is a Redis atomic decrement command that reduces a key's integer value by 1 and returns the result in a single operation. Because Redis is single-threaded, DECR is naturally atomic — no two DECR calls on the same key interleave. This makes it ideal for high-throughput inventory counting where DB-level locking would create a bottleneck. In an interview, if asked: "Redis DECR is an atomic counter operation — it reads and decrements in one step, so 10,000 concurrent requests on the same item will each see a unique decremented value, and only the requests that see a value >= 0 are valid reservations."

```java
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

// --- Strategy 1: SELECT FOR UPDATE (pessimistic, DB-level) ---
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // Step 2 — acquires row-level lock; concurrent calls wait here
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.itemId = :itemId")
    Optional<Inventory> findByItemIdWithLock(@Param("itemId") Long itemId);
}

@Service
public class PessimisticInventoryService {

    private final InventoryRepository inventoryRepo;
    private final ReservationRepository reservationRepo;

    public PessimisticInventoryService(InventoryRepository inventoryRepo,
                                       ReservationRepository reservationRepo) {
        this.inventoryRepo = inventoryRepo;
        this.reservationRepo = reservationRepo;
    }

    // Steps 2-5 — lock row, check, decrement, create reservation, commit
    @Transactional
    public String reserveItem(Long itemId, String userId, String idempotencyKey) {
        // Step 2 — other threads trying to reserve same item wait here
        Inventory inventory = inventoryRepo.findByItemIdWithLock(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemId));

        // Step 3 — count check inside the lock
        if (inventory.getAvailableCount() <= 0) {
            throw new OutOfStockException("Item out of stock: " + itemId);
        }

        // Step 4 — decrement and create reservation with TTL
        inventory.setAvailableCount(inventory.getAvailableCount() - 1);
        inventoryRepo.save(inventory);

        Reservation reservation = new Reservation();
        reservation.setItemId(itemId);
        reservation.setUserId(userId);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setStatus(ReservationStatus.SOFT_RESERVED);
        reservation.setExpiresAt(Instant.now().plusSeconds(600)); // 10-minute TTL
        reservationRepo.save(reservation);

        // Step 5 — transaction commits, lock releases
        return reservation.getId();
    }

    // Step 6 — on payment success, confirm the reservation
    @Transactional
    public void confirmReservation(String reservationId) {
        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Not found"));
        if (reservation.getStatus() != ReservationStatus.SOFT_RESERVED) {
            throw new InvalidStateException("Reservation is not in SOFT_RESERVED state");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepo.save(reservation);
    }

    // Step 7 — TTL expiry job: release expired reservations
    @Transactional
    public void releaseExpiredReservations() {
        List<Reservation> expired = reservationRepo.findByStatusAndExpiresAtBefore(
                ReservationStatus.SOFT_RESERVED, Instant.now());
        for (Reservation r : expired) {
            Inventory inventory = inventoryRepo.findByItemIdWithLock(r.getItemId())
                    .orElseThrow(() -> new ItemNotFoundException("Item not found"));
            inventory.setAvailableCount(inventory.getAvailableCount() + 1);
            inventoryRepo.save(inventory);
            r.setStatus(ReservationStatus.RELEASED);
            reservationRepo.save(r);
        }
    }
}

// --- Strategy 2: Redis DECR (high-throughput, atomic counter) ---
@Service
public class RedisInventoryService {

    private final StringRedisTemplate redis;
    private final ReservationRepository reservationRepo;

    private static final String INVENTORY_KEY_PREFIX = "inventory:item:";

    public RedisInventoryService(StringRedisTemplate redis,
                                 ReservationRepository reservationRepo) {
        this.redis = redis;
        this.reservationRepo = reservationRepo;
    }

    // Steps 1-3 — atomic Redis DECR, then async DB write
    public String reserveItem(Long itemId, String userId, String idempotencyKey) {
        String key = INVENTORY_KEY_PREFIX + itemId;

        // Step 2 — atomic decrement; returns new value
        Long newCount = redis.opsForValue().decrement(key);

        if (newCount == null || newCount < 0) {
            // Step 2 — out of stock; undo the decrement
            redis.opsForValue().increment(key);
            throw new OutOfStockException("Item out of stock: " + itemId);
        }

        // Step 3 — persist reservation to DB asynchronously
        Reservation reservation = new Reservation();
        reservation.setItemId(itemId);
        reservation.setUserId(userId);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setStatus(ReservationStatus.SOFT_RESERVED);
        reservation.setExpiresAt(Instant.now().plusSeconds(600));
        reservationRepo.save(reservation);

        return reservation.getId();
    }

    // Step 5 — on TTL expiry, restore Redis count
    public void releaseReservation(Long itemId, String reservationId) {
        String key = INVENTORY_KEY_PREFIX + itemId;
        // Step 5 — restore the decremented count
        redis.opsForValue().increment(key);
        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Not found"));
        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepo.save(reservation);
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **Airbnb** (listing hold during checkout): When a guest clicks "Reserve," Airbnb soft-reserves the listing for 10 minutes while the guest enters payment details. No other guest can book the same dates. If payment isn't submitted in 10 minutes, the hold auto-releases. This prevents the listing being shown as available mid-checkout while also not permanently blocking inventory during slow payment flows.

- **BookMyShow / Ticketmaster** (seat reservation + TTL): Each selected seat transitions to SOFT_RESERVED the moment a user selects it, with a 5-minute TTL. The seat is visually highlighted as "held" for other users on the seat map. Ticketmaster uses Redis for the fast atomic seat-hold operation and writes to the DB only on payment confirmation — optimizing for the 99% path (user completes payment) while the TTL handles the 1% (user abandons).

- **Amazon** (cart reservation during checkout): Amazon doesn't hard-reserve inventory when you add to cart (too many abandoned carts would lock stock), but does soft-reserve the last item during the 30-minute checkout window when you proceed to payment. The system uses a reservation record with a TTL, allowing the cart-browsing path to be read-only while only the checkout path requires inventory locks.

- **Uber Eats / DoorDash** (real-time item availability): Restaurant items with limited quantities (daily specials, limited portions) use Redis-based atomic DECR on item count. When count hits zero, the item is immediately hidden from the menu across all clients via a cache invalidation. This prevents the "sorry, that item is out of stock" error after ordering — the catalog layer reads from Redis which reflects the decremented count.

- **Walmart** (flash sale inventory — Big Billion Day equivalent): Walmart uses sharded Redis counters for flash sale items — a single item's inventory count is split across N Redis shards, and each DECR hits one shard. This prevents a single Redis key from becoming a throughput bottleneck at 50,000 requests/second. When all shards hit zero, the item is marked out of stock. The DB is the source of truth; Redis is the fast reservation layer that syncs back to DB asynchronously.

- **Airlines** (intentional overbooking): Airlines deliberately oversell by 5-15% because historical data shows that 5-15% of passengers cancel or no-show. Their inventory system sets `available_count = physical_seats * 1.10`. When everyone shows up, the airline pays compensation (vouchers, upgrades, cash). The booking system itself still prevents selling more than `available_count` — the overbooking is a business-level configuration of that count, not a defect in the reservation system.

---

## 🧭 When to Use vs When NOT to Use

| Use the Reservation Pattern when | Do NOT use it when |
|---|---|
| Items are scarce and concurrently demanded (last seat, last room) | Inventory is so deep that oversell is statistically impossible |
| User confirmation step separates "I want it" from "I'm paying" | Items are digital/infinite (e-books, software licenses) |
| A hold must survive across a multi-step checkout flow | Checkout is synchronous and completes in <100ms end-to-end |
| You need TTL-based auto-release for abandoned checkouts | Every user only sees their own isolated inventory |

| Use SELECT FOR UPDATE when | Use Redis DECR when |
|---|---|
| Inventory items are low-volume, high-value (hotel rooms, car rentals) | Flash sales with >10,000 concurrent requests on same item |
| You need strong consistency in a single DB transaction | Throughput matters more than synchronous DB writes |
| Your stack is purely SQL with no Redis dependency | You can tolerate eventual consistency between Redis and DB |

**The common mistake:** Using a message queue to serialize inventory decrements. Message queues (Kafka, RabbitMQ) guarantee ordered delivery and durability, but they do NOT provide the reservation hold — the user still sees "available" while their decrement message sits in the queue. Another user's message ahead in the queue can decrement count to zero before theirs is processed. Queues serialize writes; they don't reserve stock.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Zero overselling — the reservation pattern guarantees at most N reservations for N units. TTL auto-release prevents abandoned checkouts from locking inventory indefinitely. Idempotency keys prevent duplicate reservations from API retries. |
| **You lose** | Inventory appears unavailable during the soft-reserve window — a user might see "out of stock" for an item that another user is holding but hasn't paid for yet. At high load, SELECT FOR UPDATE creates lock contention and queuing delay. Redis DECR adds operational complexity (Redis crash recovery, count sync to DB). |
| **Failure mode** | Redis crash without DB sync → Redis count reloads from stale DB snapshot, allowing oversell for the unsynchronized delta. Fix: write reservation to DB synchronously before returning success, or use Redis persistence (AOF). TTL expiry job failure → soft reserves accumulate without releasing, inventory appears fully booked when it isn't. Fix: TTL expiry job must be monitored and alerted on lag. |

---

## 🔬 Interview Q&As

### Q: "1,000 users all see '1 item left' and click Buy simultaneously. How do you prevent 1,000 orders?"
> The key is atomic reservation. At the DB layer: `SELECT FOR UPDATE` on the inventory row serializes all 1,000 requests — the first one locks the row, decrements count to 0, commits, and releases the lock. The next 999 acquire the lock, read count=0, and return "out of stock." At the Redis layer: `DECR inventory:item:X` is atomic — the 1,000 DECR calls each get a unique decremented value; only the one that gets value=0 is the last valid reservation, and all calls that get a negative value immediately `INCR` back and return "out of stock." Both approaches prevent overselling, but Redis DECR handles higher throughput at the cost of eventual DB consistency.

### Q: "What is the reservation pattern and why do you need a TTL?"
> The reservation pattern is a two-step inventory lifecycle: first, atomically move a unit from AVAILABLE to SOFT_RESERVED when the user begins checkout; second, move it to CONFIRMED on payment success or back to AVAILABLE on failure. The TTL is essential because users abandon checkouts — they get distracted, their browser crashes, they find a cheaper option. Without TTL, a user who started checkout and disappeared would hold that unit forever. The TTL (10 minutes for Airbnb, 5 minutes for Ticketmaster) ensures abandoned holds auto-release and the unit returns to the available pool. The TTL is the safety net for the reservation pattern.

### Q: "Can I just use a message queue to prevent overselling?"
> No — this is a common misconception. A message queue like Kafka serializes the writes (only one DECR message processes at a time), but it doesn't reserve the inventory during the user's checkout window. While 1,000 messages sit in the queue, all 1,000 users see "1 item available" because the count hasn't been decremented yet. The first message to process decrements to 0; the other 999 messages decrement to -999. You'd need to check for negative counts on dequeue and treat them as failures — but by that point, you've already confirmed to 1,000 users that their order is "processing." The reservation (SOFT_RESERVED state) must happen synchronously before the user sees a confirmation screen.

### Q: "How does idempotency interact with the reservation system?"
> A user's mobile app might retry a reservation request due to a network timeout. Without idempotency, the second request creates a second reservation for the same user on the same item — double-booking the user and consuming two units of inventory. With an idempotency key (a unique ID generated client-side and sent in the request header), the reservation service checks if a reservation with that key already exists before processing. If yes, return the existing reservation ID. If no, create a new one. The idempotency key makes the reservation endpoint safe to retry. See `04-idempotency.md` for implementation details — the pattern is the same as payment idempotency.

### Q: "Explain the airline overbooking strategy from a system design perspective."
> Airlines deliberately set `available_count = physical_seat_count * (1 + overbooking_factor)`. Historical cancellation data shows 5-15% of passengers cancel or no-show, so a 100-seat plane is listed with 110 available. The reservation system itself is correct — it never sells more than `available_count`. Overbooking is a business-level configuration of that count, not a bug. When the overbooking assumption is wrong (everyone shows up), the airline pays compensation: vouchers, cash, upgrade. The system design implication is that `available_count` is a settable parameter, and the booking system treats it as authoritative. The overbooking strategy lives in the business rules layer, not the inventory enforcement layer.

### Q: "Walk me through how Airbnb handles the 10-minute listing hold."
> When a guest clicks Reserve, Airbnb's checkout service calls the inventory service with the listing ID, the dates, and a reservation TTL of 10 minutes. The inventory service inserts a SOFT_RESERVED record in the reservations table with an `expires_at` timestamp, and atomically marks those dates as unavailable in the availability calendar (either via `SELECT FOR UPDATE` on the availability row, or a Redis key per date range). The listing now shows as "unavailable" to other guests searching those dates. If the guest doesn't confirm payment within 10 minutes, a background TTL job (or Redis key expiry with a listener) removes the reservation record and the availability calendar reverts to available. On payment success, the reservation transitions to CONFIRMED and the calendar is permanently marked booked.

### Q (Tier 2): "Redis DECR prevents overselling, but what happens if the Redis node crashes between DECR and the DB write?"
> This is the durability gap in the Redis DECR approach. If Redis crashes after a successful DECR but before the reservation is persisted to the DB, the in-memory count decrement is lost. When Redis restarts and reloads from the DB snapshot, it restores the count to a value that doesn't reflect the in-flight reservation — effectively un-decrementing it. Fix with one of three strategies: (1) synchronous DB write before returning success (sacrifices some Redis throughput advantage), (2) enable Redis AOF (Append-Only File) persistence so DECR is durably logged before acknowledgment, (3) use a two-phase approach: DECR in Redis for fast tentative hold, then write to DB; if DB write fails, compensate with INCR in Redis. The tradeoff is between throughput (async DB write) and durability (sync DB write or AOF). In practice, Ticketmaster and Walmart use AOF-enabled Redis clusters with synchronous DB writes for the final confirmation step.

### Q (Tier 2): "Your inventory service has 10 pods. Can two pods simultaneously DECR the same Redis key and both see value=0?"
> No — this is why Redis DECR is safe for this use case. Redis is single-threaded for command processing. Even with 10 pods sending DECR commands concurrently, Redis queues them and processes one at a time. Each DECR atomically reads and decrements the counter and returns the result. The first pod to DECR from count=1 gets back 0 (last valid reservation). Every subsequent pod gets a negative number and must INCR back and reject. The atomic nature of DECR means it's impossible for two pods to both observe value=0 from the same DECR command. This is fundamentally different from two pods doing a non-atomic read-then-write: read count=1, both decide to decrement, both write count=0 — that race is what causes overselling and why Redis DECR (not Redis GET+SET) is the correct tool.

### Q (Tier 2): "You're designing BookMyShow for a concert with 50,000 seats. A show goes on sale at 10:00 AM and 500,000 users try to buy simultaneously. Walk me through your complete design."
> First, separate the availability check from the reservation: serve the seating map from a read replica or CDN-cached snapshot — 500,000 reads don't need to hit the primary DB. Second, for the reservation, use Redis DECR on a per-seat or per-block counter. With 50,000 seats, load all seat counts into Redis on show-open (`SET seat:SHOW-123:A15 1` for each seat). Each reservation attempt fires `DECR seat:SHOW-123:A15` — atomic, no contention across different seats. Third, create the soft-reserve record in the DB asynchronously with a 5-minute TTL. Fourth, on the checkout page, process payment and confirm. Fifth, a TTL expiry job re-increments Redis and releases DB reservations for abandoned checkouts. The key scale decision: contention is per-seat, not per-show — 500,000 concurrent requests spread across 50,000 seats means ~10 requests per seat, which Redis handles trivially. The bottleneck is only if everyone wants the same 10 front-row seats simultaneously — shard those specific hot seats with a virtual seat approach or a queue for the top-100 seats.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "For inventory, I'd soft-reserve atomically on checkout start with a TTL — Redis DECR for high-throughput flash sales, SELECT FOR UPDATE for low-volume high-value items — then confirm on payment success and release on TTL expiry; message queues don't solve overselling because they don't hold stock."

---

## 🔗 Related Concepts

- **`../../Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md`** — SELECT FOR UPDATE is pessimistic locking applied to inventory rows; optimistic locking with version columns is the alternative for lower-contention items
- **`../../Foundations/Concurrency-and-Consistency/41-isolation-levels-dirty-reads.md`** — inventory decrements require SERIALIZABLE or REPEATABLE_READ isolation to prevent phantom reads and non-repeatable read anomalies in the reservation path
- **`../../Foundations/Concurrency-and-Consistency/04-idempotency.md`** — reservation API calls must be idempotent; retries from mobile clients or load balancers must not double-reserve
- **`31-cqrs-read-write-separation.md`** — separating the read path (availability display) from the write path (reservation) is essential at the scale of Ticketmaster or Airbnb

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Designing a Ticketmaster Clone"** — hellointerview.com (https://www.hellointerview.com/learn/system-design/answer-keys/ticketmaster) | Full system design walkthrough of the seat reservation problem — adds the complete end-to-end architecture beyond the inventory mechanics covered here | ~25 min read |
| **"How Airbnb Handles Availability"** — Airbnb Engineering Blog (https://medium.com/airbnb-engineering) | Real implementation details of the listing hold pattern, CDN-cached availability calendars, and how Airbnb handles race conditions at scale | ~20 min read |
| **"Redis Atomic Operations"** — Redis docs (https://redis.io/docs/manual/transactions/) | Deep dive into Redis INCR/DECR atomicity guarantees, MULTI/EXEC transactions, and Lua scripting for multi-step atomic operations — what to use when DECR alone isn't enough | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | File created. Covers overselling problem, reservation pattern (AVAILABLE → SOFT_RESERVED → CONFIRMED/RELEASED), SELECT FOR UPDATE (pessimistic DB), Redis DECR (atomic counter), TTL expiry job, idempotency keys, overbooking strategy, why message queues don't solve overselling. Java code: both SELECT FOR UPDATE and Redis DECR strategies with full reservation lifecycle. 8 Q&As (5 Tier 1 + 3 Tier 2). Companies: Airbnb, BookMyShow/Ticketmaster, Amazon, Uber Eats/DoorDash, Walmart, Airlines. |
