# Event Sourcing — Fundamentals

---

## 🎯 Why This Matters

You store an order: `{order_id: 123, status: "shipped"}`. Later, you need to know: when was it shipped? Who approved it? What was the price at order time? With CRUD (current state only), this is lost. Event sourcing stores every change as an immutable event: "OrderCreated", "PaymentApproved", "ShippingInitiated". Replay events → reconstruct state at any point in time. At SDE 3: you must know event sourcing is an alternative to CRUD, when it's valuable (audit trails, temporal queries, debugging), and how it pairs with CDC (not replaces it).

---

## 📖 What is Event Sourcing?

**Full form:** Event Sourcing Pattern / Event Store

**Simple analogy:** Instead of storing a bank account balance (`balance = $500`), store every transaction: "deposit $200", "withdraw $100", "transfer $50". To get the current balance, replay all transactions from the beginning. To know the balance on any past date, replay up to that date. Every action is recorded permanently — perfect for audits.

**Core principle:** In event sourcing, **events (immutable facts) are the source of truth**, not the current state. The current state is derived by replaying all events. Every state change is captured as an event in an append-only log. This provides a complete audit trail and enables temporal queries (state at any point in time).

**Contrast to CRUD:** Traditional databases store only the current state (account balance = $500), losing the history. Event sourcing stores all changes; current state is calculated.

**Why it matters in system design:** Enables audit trails (compliance), debugging (replay events to diagnose bugs), and temporal queries (state at any past point). Powers event-driven architectures and CQRS.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Event** | immutable fact that something happened; never updated or deleted | `OrderCreated`, `PaymentApproved`, `OrderShipped` |
| **Event Store** | append-only database that stores all events in order; the single source of truth | EventStoreDB, Kafka log, or a `events` table with append-only inserts |
| **Aggregate** | domain object whose state is reconstructed by replaying its events | `Order` aggregate: replay `OrderCreated + ItemAdded + PaymentApproved` → current order state |
| **Append-Only Log** | storage structure where records are only added, never updated or deleted | event log row: `(event_id, aggregate_id, type, payload, timestamp)` — no UPDATE, no DELETE |
| **Event Replay** | reconstructing current state by processing all historical events from the beginning | `order.apply(OrderCreated) → apply(ItemAdded) → apply(PaymentApproved)` = current state |
| **Projection** | a read-optimized view built by processing events; updated asynchronously as new events arrive | `OrderSummaryProjection` table: `{order_id, status, total}` updated from event stream |
| **Snapshot** | periodic checkpoint of aggregate state to avoid replaying all events from the beginning | every 100 events, save current state; replay only from latest snapshot |
| **Command** | intent to change state; validated first, then generates an event if valid | `ShipOrder(order_id=123)` command → validates → generates `OrderShipped` event |

---

## 🎨 Visual — System Topology: Event Sourcing in Architecture

```
APPLICATION / SERVICE
    │
    │ Command: "Ship Order #123"
    │
    ▼
┌──────────────────────────┐
│ Domain Logic             │
│ Order Aggregate          │
│ (processes command)      │
└────────┬─────────────────┘
         │
         │ Generate Event
         │ "OrderShipped"
         │
         ▼
┌────────────────────────────────────┐
│ Event Store                        │
│ (immutable append-only log)        │
│ [OrderCreated]                     │
│ [ItemAdded]                        │
│ [PaymentApproved]                  │
│ [OrderShipped] ← new event added   │
└──────────┬───────────────────────┘
           │
           ├─ Event 1 ──→ [Notification Listener] → sends email
           ├─ Event 2 ──→ [Inventory Listener] → updates stock
           └─ Event 3 ──→ [Analytics Listener] → tracks metrics
                         
                ┌──────────────────┐
                │ Derived Views    │
                │ (rebuilt from    │
                │ events)          │
                │ - Current State  │
                │ - Analytics DB   │
                │ - Search Index   │
                └──────────────────┘

REPLAY SCENARIO:
  Service crashes → restart
  Read all events from store → rebuild current state automatically
  No data loss (events are permanent)
```

---

## 🎨 Visual — Event Sourcing vs CRUD (Component Detail)

Imagine a bank ledger:

- **CRUD approach:** Account balance = $500. A transaction happens. Update: balance = $700. Now you've lost the history — when did the $200 arrive? From whom? For what?
- **Event sourcing approach:** Ledger entry (immutable): "$200 deposited from Salary at 2024-01-15". Another entry: "$100 withdrawn for Groceries at 2024-01-16". Current balance = $500 (sum of all entries). But you can see the full history: who touched the account, what changed, when.

**Bonus:** If someone claims "I deposited $50 on 2024-01-10," you check the ledger. No entry? It didn't happen (audit trail). Accounting reconciliation is transparent.

**The key insight:** Events are the source of truth. Current state is derived (computed by replaying events). This is the opposite of CRUD, where current state is the truth and history is lost.

---

## 🎨 Visual — Event Sourcing vs CRUD

```
CRUD APPROACH (current state only):
┌─────────────────────────────────────┐
│ Database Table: Orders              │
├─────────────────────────────────────┤
│ order_id │ status   │ total │ user  │
├──────────┼──────────┼───────┼───────┤
│ 123      │ shipped  │ $100  │ john  │
└─────────────────────────────────────┘
                 ↑
        Current state only
        History is lost


EVENT SOURCING APPROACH (events + replay):
┌──────────────────────────────────────────────────────────┐
│ Event Store (immutable append-only log)                  │
├──────────────────────────────────────────────────────────┤
│ Offset │ Event Type    │ Data              │ Timestamp   │
├────────┼───────────────┼───────────────────┼─────────────┤
│ 0      │ OrderCreated  │ id:123, user:john │ 2024-01-15  │
│ 1      │ ItemAdded     │ sku:ABC, qty:1    │ 2024-01-15  │
│ 2      │ PaymentMade   │ $100, cc****      │ 2024-01-15  │
│ 3      │ ShippingInfo  │ address:...       │ 2024-01-15  │
│ 4      │ OrderShipped  │ carrier:FedEx     │ 2024-01-16  │
└──────────────────────────────────────────────────────────┘
                    ↓
            [Replay all events]
                    ↓
        ┌─────────────────────────┐
        │ Derived State (cache)   │
        │ order_id: 123           │
        │ status: shipped         │
        │ total: $100             │
        │ user: john              │
        └─────────────────────────┘

QUERIES ENABLED BY EVENT SOURCING:

1. Current state: replay all events
2. State at time T: replay events up to T
3. Full history: iterate events
4. "When did status change to shipped?": find OrderShipped event
5. "Why was it shipped?": read the event data
6. "Who approved the payment?": read PaymentMade event
7. Audit trail: all events with timestamps (compliance, debugging)

EVENT REBUILD SCENARIO:
  Developer deletes cache accidentally
  No problem: replay events from event store → rebuild state
  
  Bug found: "orders from yesterday are missing prices"
  Replay events from 1 month ago → diagnose when price field was added
  
  Temporal query: "What was the order status on Jan 15 at 3pm?"
  Replay events up to that timestamp → answer

KEY INVARIANT:
   Events are immutable source of truth
   Current state = event replay (can be cached, can be rebuilt)
   Audit trail is automatic (every event is recorded)
```

---

## ⚙️ How It Actually Works

**Pattern 1: Event Sourcing with Event Store**

**Steps:**
1. On any state change, publish an event (immutable) to the event store.
2. Persist the event to an append-only log (database or event streaming system like Kafka).
3. Also update a view/cache with the new state (for performance).
4. On state reconstruction, replay events from the start.
5. Listeners react to events (publish to other systems, update denormalized views).

```java
// Event Sourcing Domain Model
public class Order {
    private String orderId;
    private String status; // "created", "paid", "shipped", "delivered"
    private BigDecimal totalPrice;
    private List<OrderEvent> events = new ArrayList<>();

    // Constructor from events
    public Order(String orderId) {
        this.orderId = orderId;
        this.status = "created";
        this.totalPrice = BigDecimal.ZERO;
    }

    // Commands → Events
    public void addItem(String sku, int quantity, BigDecimal price) {
        // Step 1 — generate event
        ItemAddedEvent event = new ItemAddedEvent(
            orderId, sku, quantity, price, System.currentTimeMillis()
        );

        // Step 2 — persist to event store
        eventStore.append(event);

        // Step 3 — apply to current state
        applyEvent(event);

        // Step 5 — publish to listeners
        eventPublisher.publish(event);
    }

    public void completePayment(BigDecimal amount) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            orderId, amount, System.currentTimeMillis()
        );
        eventStore.append(event);
        applyEvent(event);
        eventPublisher.publish(event);
    }

    public void shipOrder(String carrier) {
        OrderShippedEvent event = new OrderShippedEvent(
            orderId, carrier, System.currentTimeMillis()
        );
        eventStore.append(event);
        applyEvent(event);
        eventPublisher.publish(event);
    }

    // Step 4 — replay events to rebuild state
    private void applyEvent(OrderEvent event) {
        if (event instanceof ItemAddedEvent) {
            ItemAddedEvent e = (ItemAddedEvent) event;
            totalPrice = totalPrice.add(e.getPrice().multiply(BigDecimal.valueOf(e.getQuantity())));
        } else if (event instanceof PaymentCompletedEvent) {
            status = "paid";
        } else if (event instanceof OrderShippedEvent) {
            status = "shipped";
        }
    }

    public static Order rebuildFromEvents(String orderId) {
        // Step 4 — full rebuild
        Order order = new Order(orderId);
        List<OrderEvent> events = eventStore.getEventsForAggregate(orderId);

        for (OrderEvent event : events) {
            order.applyEvent(event);
        }
        return order;
    }

    // Abstract base for all events
    public abstract static class OrderEvent {
        String orderId;
        long timestamp;

        OrderEvent(String orderId, long timestamp) {
            this.orderId = orderId;
            this.timestamp = timestamp;
        }
    }

    public static class ItemAddedEvent extends OrderEvent {
        String sku;
        int quantity;
        BigDecimal price;

        public ItemAddedEvent(String orderId, String sku, int quantity, BigDecimal price, long timestamp) {
            super(orderId, timestamp);
            this.sku = sku;
            this.quantity = quantity;
            this.price = price;
        }

        // Getters
        public String getSku() { return sku; }
        public int getQuantity() { return quantity; }
        public BigDecimal getPrice() { return price; }
    }

    public static class PaymentCompletedEvent extends OrderEvent {
        BigDecimal amount;

        public PaymentCompletedEvent(String orderId, BigDecimal amount, long timestamp) {
            super(orderId, timestamp);
            this.amount = amount;
        }
    }

    public static class OrderShippedEvent extends OrderEvent {
        String carrier;

        public OrderShippedEvent(String orderId, String carrier, long timestamp) {
            super(orderId, timestamp);
            this.carrier = carrier;
        }
    }
}

// Event Store (simplified)
@Component
public class EventStore {
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void append(Order.OrderEvent event) {
        // Step 2a — persist to database (for querying)
        jdbcTemplate.update(
            "INSERT INTO events (aggregate_id, event_type, event_data, timestamp) VALUES (?, ?, ?, ?)",
            event.orderId,
            event.getClass().getSimpleName(),
            toJson(event),
            event.timestamp
        );

        // Step 2b — also publish to Kafka (for listeners)
        kafkaTemplate.send("order-events", event.orderId, event);
    }

    public List<Order.OrderEvent> getEventsForAggregate(String orderId) {
        // Step 4 — retrieve all events for replay
        return jdbcTemplate.query(
            "SELECT event_data FROM events WHERE aggregate_id = ? ORDER BY timestamp",
            new Object[]{orderId},
            (rs, rowNum) -> fromJson(rs.getString("event_data"), Order.OrderEvent.class)
        );
    }

    private String toJson(Object obj) { /* JSON serialization */ return ""; }
    private <T> T fromJson(String json, Class<T> type) { /* JSON deserialization */ return null; }
}

// Event listeners (Step 5 — react to events)
@Component
public class OrderEventListener {
    @KafkaListener(topics = "order-events")
    public void handleOrderEvent(Order.OrderEvent event) {
        if (event instanceof Order.OrderShippedEvent) {
            Order.OrderShippedEvent e = (Order.OrderShippedEvent) event;
            // Send notification email
            sendShippingEmail(e.orderId, e.carrier);
        }
    }

    private void sendShippingEmail(String orderId, String carrier) {
        // Notification service
    }
}
```

---

**Pattern 2: Temporal Query (state at a point in time)**

```java
public class TemporalOrderQuery {
    @Autowired
    private EventStore eventStore;

    // Get order state at a specific timestamp
    public Order getOrderAtTime(String orderId, long timestamp) {
        Order order = new Order(orderId);

        // Replay only events up to the given timestamp
        List<Order.OrderEvent> events = eventStore.getEventsForAggregate(orderId);
        for (Order.OrderEvent event : events) {
            if (event.timestamp <= timestamp) {
                order.applyEvent(event);
            } else {
                break; // stop replaying
            }
        }

        return order; // state at that moment
    }

    // Use case: "What was the order total on Jan 15?"
    public void exampleTemporalQuery() {
        long jan15Midnight = System.currentTimeMillis(); // substitute with actual timestamp
        Order orderThenState = getOrderAtTime("order-123", jan15Midnight);
        System.out.println("Price on Jan 15: " + orderThenState.totalPrice);
    }
}
```

---

**Pattern 3: Snapshotting — Avoiding Full Replay on Every Load**

The replay-from-event-zero approach works for orders with 50 events. An account with 500,000 transactions would take seconds to rebuild on every request. **Snapshotting** saves the computed state every N events so replay starts from the snapshot, not from the beginning.

```java
// Snapshot: persisted computed state at a known event offset
public class OrderSnapshot {
    private String orderId;
    private String status;
    private BigDecimal totalPrice;
    // Step 1 — which event offset this snapshot was built from
    private long snapshotOffset;

    public OrderSnapshot(String orderId, String status, BigDecimal totalPrice, long offset) {
        this.orderId = orderId;
        this.status = status;
        this.totalPrice = totalPrice;
        this.snapshotOffset = offset;
    }

    // Getters
    public long getSnapshotOffset() { return snapshotOffset; }
    public String getStatus() { return status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
}

@Component
public class SnapshotStore {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Step 2 — Save snapshot every N events
    public void saveSnapshot(OrderSnapshot snapshot) {
        jdbcTemplate.update(
            "INSERT INTO order_snapshots (order_id, status, total_price, snapshot_offset) " +
            "VALUES (?, ?, ?, ?) ON CONFLICT (order_id) DO UPDATE SET " +
            "status = EXCLUDED.status, total_price = EXCLUDED.total_price, " +
            "snapshot_offset = EXCLUDED.snapshot_offset",
            snapshot.getOrderId(),
            snapshot.getStatus(),
            snapshot.getTotalPrice(),
            snapshot.getSnapshotOffset()
        );
    }

    // Step 3 — Load most recent snapshot
    public Optional<OrderSnapshot> loadSnapshot(String orderId) {
        return jdbcTemplate.query(
            "SELECT * FROM order_snapshots WHERE order_id = ?",
            new Object[]{orderId},
            (rs, rowNum) -> new OrderSnapshot(
                rs.getString("order_id"),
                rs.getString("status"),
                rs.getBigDecimal("total_price"),
                rs.getLong("snapshot_offset")
            )
        ).stream().findFirst();
    }
}

public static Order rebuildFromEventsWithSnapshot(String orderId) {
    // Step 3 — Try to load snapshot first
    Optional<OrderSnapshot> snapshot = snapshotStore.loadSnapshot(orderId);

    Order order;
    long startOffset;

    if (snapshot.isPresent()) {
        // Step 3 — Restore state from snapshot (skip old events)
        order = new Order(orderId);
        order.status = snapshot.get().getStatus();
        order.totalPrice = snapshot.get().getTotalPrice();
        startOffset = snapshot.get().getSnapshotOffset() + 1;
    } else {
        // No snapshot: replay from event 0
        order = new Order(orderId);
        startOffset = 0;
    }

    // Step 4 — Replay only events AFTER the snapshot offset
    List<Order.OrderEvent> events = eventStore.getEventsAfterOffset(orderId, startOffset);
    for (Order.OrderEvent event : events) {
        order.applyEvent(event);
    }

    // Step 2 — Save snapshot every 100 events (for next load)
    long totalEvents = startOffset + events.size();
    if (totalEvents % 100 == 0) {
        snapshotStore.saveSnapshot(new OrderSnapshot(
            orderId, order.status, order.totalPrice, totalEvents
        ));
    }

    return order;
}
```

**When to snapshot:** Every 50–500 events depending on entity update frequency and acceptable load latency. For a financial account with millions of transactions, snapshot every 1,000 events; for an order with 20 events, snapshotting is overkill.

---

**Pattern 4: Optimistic Concurrency Control — Preventing Lost Updates on the Event Stream**

The single most common senior interview probe on event sourcing: *"Two commands hit the same aggregate at the same time — how do you stop one from silently clobbering the other?"* A blind `eventStore.append(event)` (as shown in Pattern 1) has a race: two writers both load Order #123 at version 5, both decide to append, and both writes land — producing an inconsistent history where each ignored the other's change.

The fix is **optimistic concurrency control** (OCC — assume conflicts are rare, detect them at write time instead of locking up front): every append carries the **expected version** (the sequence number the writer believes is current). The event store commits the new event *only if* the aggregate's current version still equals the expected version. If another writer got there first, the version won't match, the append is rejected, and the caller reloads and retries.

**Steps in plain English:**

1. **Load with version** — when rebuilding the aggregate, also read its current version (the sequence number of its latest event).
2. **Decide** — run the command's business logic against that loaded state, producing a new event.
3. **Append conditionally** — write the event with `expectedVersion = loadedVersion`. The store enforces "commit only if current version is still `expectedVersion`."
4. **Detect conflict** — if a concurrent writer already advanced the version, the conditional write fails with a concurrency exception.
5. **Retry** — on conflict, reload the aggregate (now at the newer version), re-run the command, and try again (or surface the conflict to the user if the command no longer makes sense).

```java
public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(String message) {
        super(message);
    }
}

@Component
public class VersionedEventStore {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Step 3 — append ONLY if the aggregate is still at expectedVersion.
    // The UNIQUE constraint on (aggregate_id, version) is what makes this atomic:
    // two writers targeting the same version — one INSERT wins, the other violates
    // the constraint and is rejected.
    public void appendWithVersionCheck(String aggregateId, Order.OrderEvent event, long expectedVersion) {
        int rowsInserted = jdbcTemplate.update(
            "INSERT INTO events (aggregate_id, version, event_type, event_data, timestamp) " +
            "SELECT ?, ?, ?, ?, ? " +
            "WHERE NOT EXISTS ( " +
            "    SELECT 1 FROM events WHERE aggregate_id = ? AND version > ? " +
            ")",
            aggregateId,
            expectedVersion + 1,
            event.getClass().getSimpleName(),
            toJson(event),
            event.timestamp,
            aggregateId,
            expectedVersion
        );

        // Step 4 — no row inserted (or unique-constraint violation) means a
        // concurrent writer advanced the version first.
        if (rowsInserted == 0) {
            throw new ConcurrencyException(
                "Aggregate " + aggregateId + " was modified concurrently; expected version " + expectedVersion
            );
        }
    }

    private String toJson(Object obj) {
        return "";
    }
}

@Service
public class OrderCommandService {
    @Autowired
    private VersionedEventStore eventStore;

    private static final int MAX_RETRIES = 3;

    // Steps 1-5 — load, decide, append-with-check, retry on conflict
    public void shipOrder(String orderId, String carrier) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // Step 1 — load current state AND its version
            LoadedOrder loaded = rebuildWithVersion(orderId);

            // Step 2 — business decision against the loaded state
            if ("shipped".equals(loaded.order.getStatus())) {
                // already shipped — command is a no-op, do not append
                return;
            }
            Order.OrderShippedEvent event = new Order.OrderShippedEvent(
                orderId, carrier, System.currentTimeMillis()
            );

            try {
                // Step 3 — conditional append
                eventStore.appendWithVersionCheck(orderId, event, loaded.version);
                return;
            } catch (ConcurrencyException e) {
                // Step 5 — someone else won the race; loop reloads and retries
                if (attempt == MAX_RETRIES - 1) {
                    throw e;
                }
            }
        }
    }

    private LoadedOrder rebuildWithVersion(String orderId) {
        // returns the rebuilt aggregate plus the version of its latest event
        return null;
    }

    private static class LoadedOrder {
        Order order;
        long version;
    }
}
```

**Idempotent projections — the read-side counterpart.** OCC protects the write side; projections need their own guard. When a listener crashes mid-batch and Kafka redelivers, the same event can be processed twice — double-incrementing a counter or double-inserting a row. Make projection updates **idempotent** by tracking the last-processed event version per aggregate in the projection store, and skipping any event whose version is `<=` the last one already applied:

```java
@KafkaListener(topics = "order-events")
public void project(Order.OrderEvent event, long eventVersion) {
    // Skip if this projection already applied this version (redelivery / replay)
    long lastApplied = projectionRepo.getLastAppliedVersion(event.orderId);
    if (eventVersion <= lastApplied) {
        return;
    }
    // Apply the state change AND advance the watermark in one transaction
    projectionRepo.applyAndAdvance(event, eventVersion);
}
```

**Interview phrasing:** *"Appends are guarded by optimistic concurrency — each write carries an expectedVersion, and a UNIQUE constraint on (aggregate_id, version) makes the check atomic. Concurrent writers race for the same version; one wins, the loser gets a ConcurrencyException, reloads, and retries. On the read side, projections dedupe by tracking the last-applied version per aggregate, so redelivered or replayed events are idempotent."*

---

**What is Event Store, Aggregate Root, and Projection, and why do they fit here?**

- **Event Store:** Immutable append-only log of all domain events. Can be a database table or Kafka topic. In an interview: *"Event store is the single source of truth; all state is derived from replaying events."*

- **Aggregate Root:** A domain entity that owns its events (e.g., Order owns ItemAdded, PaymentCompleted). Ensures events are grouped logically. In an interview: *"Aggregate root is the entity boundary; events within an aggregate are transactional."*

- **Projection:** A read model derived from events (e.g., OrderView: id, status, total). Projections can be denormalized for performance. In an interview: *"Projections are views built by listening to events; they're eventually consistent but queryable."*

---

## 🏢 Real World — Where Companies Use This

- **LinkedIn (event-based architecture):** Every profile change (new job, endorsement, skill added) is an event. Replaying events reconstructs the full profile history. Audit trail is automatic.
- **Stripe (payment events):** ChargeCreated, ChargeSucceeded, PayoutPaid are events. Temporal queries: "What was the charge status on July 5?" Replay events up to that date.
- **Booking.com (reservation system):** ReservationCreated, PaymentApproved, CheckinCompleted are events. Allows full history of a booking without data loss.
- **Financial systems:** Every trade, every balance change is an event. Audit compliance is built-in. Regs require full history; event sourcing delivers it.

---

## 🧭 When to Use vs When NOT to Use

| Use event sourcing when | Do NOT use when |
|---|---|
| Audit trail is critical (compliance, legal, debugging) | Simple CRUD app (e-commerce product catalog) |
| Temporal queries needed ("state at time T?") | Write-once, no updates (immutable data) |
| High-frequency state changes (many events per entity) | Few state transitions (CRUD is simpler) |
| Debugging/replay scenarios are valuable (undo, time-travel) | Low operational maturity (adds complexity) |

**The common mistake:** Event sourcing + synchronous immediate consistency. Events are usually eventually consistent (async processing). If you need immediate consistency, use CRUD for current state + events for audit.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Full audit trail (when, who, what). Temporal queries (state at any time). Debugging (replay events to understand what happened). No data loss from overwrites. |
| **You lose** | Complexity (storing, replaying, versioning events). Storage (events take more space than current state). Stale reads (projections are eventually consistent). Event versioning (if event schema changes, replay is tricky). |
| **Failure mode** | Corrupted event (rare, but the chain is broken). Projection lag (if listener crashes, projection falls behind). Query performance (replaying millions of events is slow). Mitigation: cache snapshots (persist state every 100 events, replay from snapshot). |

---

## 🔬 Interview Q&As

### Q: "Event sourcing vs CRUD — when would you use each?"

> **CRUD:** For most applications (simple state, few temporal queries). Write is fast; read is reading current state. **Event sourcing:** For audit-critical systems (compliance, financial) and where temporal queries matter ("what was the price at checkout time?"). Event sourcing is overkill for a product catalog; perfect for a financial ledger or booking system. Hybrid: use CRUD for most tables, event sourcing for high-audit-trail entities (payments, orders). ⭐ **Tier 2 — choice**

### Q: "How does event sourcing handle updates (e.g., correcting an order quantity)?"

> You don't update events (immutable). You publish a new event: ItemQuantityCorrected(orderId, sku, oldQty, newQty). The projection replays: ItemAdded(qty=5) then ItemQuantityCorrected(oldQty=5, newQty=7) → final qty=7. The full history is preserved (compliance loves this). If you need to represent "logical deletes," publish ItemRemoved(sku). Replay shows the evolution. ⭐ **Tier 2 — semantics**

### Q: "Event sourcing + eventual consistency: what if a read hits the projection before it's been updated?"

> Read hits an outdated projection (lag: event published but listener hasn't processed it yet). For non-critical reads, this is acceptable ("customers see order status delayed by 1–2s"). For critical reads (payment confirmation), query the event store directly or wait for the projection to catch up. This is a fundamental trade-off in event sourcing — eventual consistency. Mitigation: read-own-writes pattern (after you publish an event, read from the event store directly, not the projection). ⭐ **Tier 2 — consistency**

### Q: "Your event schema changed (added a field). How do you replay old events?" ⭐

> Old events don't have the new field. Standard solution: **upcasting** — transform the old event into the new shape before applying it to the aggregate. The upcaster sits between the event store and the `applyEvent` call; old events get the missing field set to a safe default, new events pass through unchanged.

```java
// Upcasting: transform old event format → new event format at replay time
public class ItemAddedEventUpcaster {

    // v1: {orderId, sku, qty, price}
    // v2: {orderId, sku, qty, price, supplier} ← new field added

    public Order.ItemAddedEvent upcast(Map<String, Object> rawEvent) {
        String version = (String) rawEvent.getOrDefault("version", "v1");

        String orderId  = (String) rawEvent.get("orderId");
        String sku      = (String) rawEvent.get("sku");
        int qty         = (int) rawEvent.get("qty");
        BigDecimal price = new BigDecimal(rawEvent.get("price").toString());
        long timestamp  = (long) rawEvent.get("timestamp");

        // Step — provide safe default for missing field in old events
        String supplier = version.equals("v2")
            ? (String) rawEvent.get("supplier")
            : "UNKNOWN";

        return new Order.ItemAddedEvent(orderId, sku, qty, price, supplier, timestamp);
    }
}

// Usage in replay
public static Order rebuildFromEvents(String orderId) {
    Order order = new Order(orderId);
    List<Map<String, Object>> rawEvents = eventStore.getRawEventsForAggregate(orderId);
    ItemAddedEventUpcaster upcaster = new ItemAddedEventUpcaster();

    for (Map<String, Object> raw : rawEvents) {
        String eventType = (String) raw.get("eventType");

        Order.OrderEvent event;
        if (eventType.equals("ItemAddedEvent")) {
            // Upcast regardless of version — upcaster handles both
            event = upcaster.upcast(raw);
        } else {
            event = deserialize(raw);
        }

        order.applyEvent(event);
    }
    return order;
}
```

**Interview phrasing:** *"We use upcasters: a small transformation function that converts an old event format to the current schema before applying it to the aggregate. The event store stays immutable — we never rewrite historical events. The upcaster is the adapter layer that handles version differences at replay time."* ⭐ **Tier 2 — operational**

### Q: "How does event sourcing differ from CDC (Change Data Capture)?"

> **CDC:** Captures writes to a database (INSERT/UPDATE/DELETE) and streams them as events (e.g., to Kafka). Source of truth is still the database; events are secondary (for replication or downstream systems). **Event sourcing:** Events ARE the source of truth; the database is derived from replaying events. CDC is "changes from the DB"; event sourcing is "events as the DB." They complement: you might use event sourcing internally, then CDC to publish changes downstream. ⭐ **Tier 2 — comparison**

### Q: "Design a system that supports 'undo' for orders."

> Every change is an event. Undo = retroactively "cancel" an event (publish OrderCancelled event). Replay logic: ignore cancelled events. Example: [OrderCreated, ItemAdded(qty=5), ItemAddedUndo] → final qty=0. Client clicks "undo"; server publishes ItemAddedUndo. Projection recomputes. Full history is preserved (audit trail). This is natural with event sourcing; very hard with CRUD. ⭐ **Tier 2 — system design**

---

## 🧾 TL;DR

> "Event sourcing stores every change as an immutable event instead of overwriting state. Replay events to reconstruct state at any time. Enables full audit trails, temporal queries, and debugging. Trade-off: complexity for historical accuracy and compliance."

---

## 🔗 Related Concepts

- **`07-cdc-outbox.md`** — CDC is similar but reads from database; event sourcing makes events the source of truth
- **`19-message-queues-kafka-rabbitmq.md`** — event store often uses Kafka for durability and listener notification
- **`23-saga-pattern.md`** — sagas publish events; event sourcing captures all saga steps
- **`21-leader-election-consensus.md`** — consensus ensures all replicas agree on events (consistency)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — "Event Sourcing"** (YouTube) | Deep dive on event store design, snapshotting, replay optimization | ~18 min |
| **Event Sourcing — Martin Fowler** (Web) | Seminal article defining event sourcing concepts, trade-offs | ~20 min read |
| **ByteByteGo — "Event-Driven Architecture"** (YouTube) | Visual walkthrough of event sourcing, projections, eventual consistency | ~9 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 22. Covered event store, replay, temporal queries, aggregate root, projections, versioning challenges. |
| July 1, 2026 | Added snapshotting Pattern 3 with full Java implementation (snapshot save/load/resume). Added upcasting code example to schema versioning Q&A. |
| Jul 19, 2026 | **Gap closed — concurrency control.** Added Pattern 4: optimistic concurrency on the event stream (expectedVersion check + UNIQUE (aggregate_id, version) constraint + reload-and-retry loop) and idempotent projections (last-applied-version watermark to dedupe redelivered/replayed events). Fills the common senior probe "two concurrent commands on one aggregate — how do you prevent a lost update?", which the original CRUD-style `append()` did not address. |
