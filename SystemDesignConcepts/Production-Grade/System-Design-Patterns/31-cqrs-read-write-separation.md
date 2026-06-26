# CQRS — Command Query Responsibility Segregation

> CQRS separates read and write operations. Write requests (Commands) update a transactional database. Read requests (Queries) pull from a denormalized read database, synced via events. At SDE 3: you must know when CQRS optimizes for scale, how eventual consistency works, and the trade-off between complexity and performance.

---

## 🎯 Why This Matters

Your database has 1000 concurrent writes (orders) and 100,000 concurrent reads (searches). Single database can't scale both simultaneously. CQRS decouples: write database optimized for fast inserts (normalized schema, ACID), read database optimized for fast queries (denormalized, indexed). Netflix uses CQRS for video catalog (writes: content team updates metadata 100/day; reads: millions of searches/sec). Without CQRS, read replicas would be overwhelmed trying to serve both.

---

## 📖 What is CQRS? (Full Form & Basics)

**CQRS = Command Query Responsibility Segregation**

- **Command** = write operation (create, update, delete)
- **Query** = read operation (fetch, search)
- **Segregation** = separate into two different systems

Instead of: One database handles both reads and writes
CQRS does: One database for writes (Commands), another for reads (Queries)

**Simple analogy:** Restaurant kitchen writes orders to a ledger. A separate analytics team reads from that ledger to generate reports. Don't have chefs also answering "How many orders today?" — ask the analysts instead.

---

## 🧠 The Mental Model

Imagine a restaurant:

**Without CQRS (single kitchen):**
- Chefs take orders (writes).
- Chefs also answer "how many burgers ordered today?" (reads).
- Chefs juggle both, inefficient.

**With CQRS:**
- **Write side (Kitchen):** Chefs take orders, update a ledger (normalized). Each order is one entry.
- **Read side (Analytics board):** A separate clerk maintains a summary board: "Burgers: 150, Steaks: 200, Salads: 75". Board is denormalized (pre-computed), not real-time (eventually consistent: updated every 5 minutes).
- Customers ask clerk (reads from summary board). Chefs handle orders (write to ledger).
- Reads are instant (clerk doesn't compute, just reads board). Writes are fast (ledger is append-only).

**The key insight:** Reads and writes have different optimization goals. CQRS gives each its own database.

---

## 🎨 Visual — CQRS Architecture

### Full System Topology — Write & Read Paths Separated

```
┌─────────────────────────────────────────────────────────────┐
│ CLIENT                                                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Write Request: POST /orders (create order)                 │
│                    ↓                                        │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ WRITE SIDE (Command)                               │   │
│ │ ┌────────────────────────────────────────────────┐  │   │
│ │ │ Order Service (handles business logic)        │  │   │
│ │ │ - Validate order                              │  │   │
│ │ │ - Deduct inventory                            │  │   │
│ │ │ - Charge payment                              │  │   │
│ │ └────────────────────────────────────────────────┘  │   │
│ │ ↓                                                   │   │
│ │ ┌────────────────────────────────────────────────┐  │   │
│ │ │ WRITE DATABASE (normalized, transactional)    │  │   │
│ │ │ - orders table (order details)                │  │   │
│ │ │ - order_items table (items in order)          │  │   │
│ │ │ - fulfillment table (shipping status)         │  │   │
│ │ │ - payments table (payment records)            │  │   │
│ │ │ Schema: normalized, ACID, slow reads          │  │   │
│ │ └────────────────────────────────────────────────┘  │   │
│ │ ↓ (publish event: OrderCreated)                   │   │
│ │ ┌────────────────────────────────────────────────┐  │   │
│ │ │ EVENT BROKER (Kafka / RabbitMQ)                │  │   │
│ │ │ Topic: "order-events"                          │  │   │
│ │ │ Message: {order_id, customer_id, amount, ...} │  │   │
│ │ └────────────────────────────────────────────────┘  │   │
│ └──────────────────────────────────────────────────────┘   │
│                                                             │
│ Read Request: GET /orders/search?status=pending            │
│                    ↓                                        │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ READ SIDE (Query)                                  │   │
│ │ ┌────────────────────────────────────────────────┐  │   │
│ │ │ Search Service (read-only)                     │  │   │
│ │ │ - Complex queries (filters, aggregations)      │  │   │
│ │ └────────────────────────────────────────────────┘  │   │
│ │ ↓                                                   │   │
│ │ ┌────────────────────────────────────────────────┐  │   │
│ │ │ READ DATABASE (denormalized, indexed)          │  │   │
│ │ │ - orders_view (flat, pre-joined)               │  │   │
│ │ │   {order_id, customer_name, items_count,       │  │   │
│ │ │    total_amount, status, shipment_date, ...}   │  │   │
│ │ │ - Index: status, customer_id, created_at       │  │   │
│ │ │ Schema: denormalized, fast reads, eventual     │  │   │
│ │ │ consistency (lags write DB by 100-500ms)       │  │   │
│ │ └────────────────────────────────────────────────┘  │   │
│ └──────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘

SYNCHRONIZATION (Event-driven):
Write DB updates → publishes event → Kafka → Read DB listener → updates read DB
Latency: 100-500ms (eventual consistency)

KEY INVARIANT:
   Write DB is source of truth (normalized, ACID).
   Read DB is derived view (denormalized, eventually consistent).
   Events flow one-way: write → read (no read updates write).
   Reads never block writes (separate databases).
```

### Component Detail — Event Projection & Denormalization

```
WRITE SIDE: Event Store (append-only log of events)
┌─────────────────────────────────┐
│ Event Stream: order-events      │
│ [0] OrderCreated                │
│     {order_id: 123, cust: A}    │
│ [1] PaymentCharged              │
│     {order_id: 123, amt: 500}   │
│ [2] OrderFulfilled              │
│     {order_id: 123, tracking}   │
│ [3] OrderCancelled              │
│     {order_id: 456, reason}     │
│ ...                             │
└─────────────────────────────────┘


READ SIDE: Projections (materialized views from events)

Projection #1: "Orders by status"
┌────────────────────────────────┐
│ SELECT * FROM orders_by_status │
│                                │
│ PENDING:     [123, 124, 125]   │ ← Updated by listening to events
│ FULFILLED:   [122, 121]        │
│ CANCELLED:   [456]             │
└────────────────────────────────┘

Projection #2: "Customer order count"
┌──────────────────────────────────────┐
│ SELECT COUNT(*) FROM orders_per_cust │
│                                      │
│ customer_A: 5 orders (value derived) │
│ customer_B: 3 orders                 │
│ customer_C: 12 orders                │
└──────────────────────────────────────┘

EVENT PROJECTION FLOW:
┌────────────────────────────────┐
│ Event: OrderCreated            │
│ {order_id: 123, cust: A, ...}  │
└────────────────────────────────┘
    ↓ (listener receives event)
┌────────────────────────────────┐
│ Handler: on_order_created()    │
│ 1. Insert into orders_by_status│
│    PENDING: [new 123]          │
│ 2. Increment orders_per_cust   │
│    customer_A: 5 → 6           │
│ 3. Update search_index         │
│    add (123, cust_A)           │
└────────────────────────────────┘

EVENTUAL CONSISTENCY TIMELINE:
Write event: T=0
  Order created in write DB
Event published: T=1ms
Event consumed: T=50ms
  Listener receives event
Projections updated: T=100ms
  Read DB is now consistent
Client queries read DB: T=150ms
  Sees updated data
Eventual consistency latency: 150ms

IDEMPOTENCY (handling duplicate events):
Event: OrderCreated (order_id=123)
Listener processes: increment customer_A count
If event re-delivered (at-least-once guarantee):
  How to avoid counting twice?
  
Solution: Idempotent key
  Store processed_event_ids table
  {event_id, order_id, timestamp}
  When event arrives, check:
    SELECT * FROM processed WHERE event_id = ?
    If exists: skip (already processed)
    If not: process + insert
  Now safe to reprocess without duplicating.

KEY INVARIANT:
   Events are immutable source of truth.
   Projections are derived (can be rebuilt from events).
   If projection is wrong, replay events → rebuild projection.
   Multiple projections from same events (orders_by_status, orders_per_cust, search_index).
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Client sends write request** (POST /orders) to Order Service.
2. **Order Service validates** business logic (inventory, payment).
3. **Order Service publishes event** (OrderCreated) to Kafka.
4. **Kafka acknowledges** the event (durably stored).
5. **Order Service responds** to client (write confirmed).
6. **Event listeners** subscribe to "order-events" topic.
7. **Listeners receive event** and update read DB (insert into orders_by_status view).
8. **Client sends read request** (GET /orders/search) to Search Service.
9. **Search Service queries read DB** (fast denormalized view).
10. **Search Service returns results** instantly (no joins, no computation).

```java
// WRITE SIDE: Order Service (Commands)

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;  // Write DB
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    // Step 1-5 — Write command (create order)
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Step 2 — Validate
        validateInventory(request.getItems());
        validatePayment(request.getPaymentMethod());

        // Step 3 — Create order in write DB (normalized schema)
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setCustomerId(request.getCustomerId());
        order.setStatus(OrderStatus.PENDING);
        
        orderRepository.save(order);  // Write DB

        // Step 3 — Publish event (command succeeded)
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .orderId(order.getId())
            .customerId(request.getCustomerId())
            .amount(request.getTotalAmount())
            .timestamp(System.currentTimeMillis())
            .build();

        // Step 4 — Publish to Kafka (durably stored)
        kafkaTemplate.send("order-events", event.getOrderId(), event);

        // Step 5 — Respond to client (write confirmed)
        return OrderResponse.builder()
            .orderId(order.getId())
            .status("PENDING")
            .build();
    }
}

// READ SIDE: Listeners (Event handlers)

@Component
public class OrderViewUpdater {
    @Autowired
    private OrderViewRepository orderViewRepository;  // Read DB

    // Step 7 — Listen to events and update read DB
    @KafkaListener(topics = "order-events", groupId = "order-view-group")
    public void onOrderCreated(OrderCreatedEvent event) {
        // Step 7 — Create denormalized view
        OrderView view = OrderView.builder()
            .orderId(event.getOrderId())
            .customerId(event.getCustomerId())
            .amount(event.getAmount())
            .status("PENDING")
            .createdAt(event.getTimestamp())
            .build();

        // Step 7 — Insert into read DB (denormalized)
        orderViewRepository.save(view);  // Fast insert (single table)
    }

    @KafkaListener(topics = "order-events", groupId = "order-stats-group")
    public void onOrderCreatedForStats(OrderCreatedEvent event) {
        // Step 7 — Update another projection: customer order count
        OrderStatsView stats = orderStatsRepository.findByCustomerId(event.getCustomerId());
        if (stats == null) {
            stats = new OrderStatsView(event.getCustomerId(), 0);
        }
        stats.incrementOrderCount();  // 1 → 2 → 3
        orderStatsRepository.save(stats);
    }
}

// READ SIDE: Search Service (Queries)

@Service
public class SearchService {
    @Autowired
    private OrderViewRepository orderViewRepository;  // Read DB (denormalized)

    // Step 9-10 — Complex query (fast because denormalized)
    public List<OrderView> searchOrders(SearchCriteria criteria) {
        // Step 9 — Query read DB (no joins, single table)
        return orderViewRepository.findByStatusAndCustomerId(
            criteria.getStatus(),    // PENDING, FULFILLED, etc.
            criteria.getCustomerId()
        );
        // Step 10 — Results returned instantly (indexed)
    }

    // Complex aggregation (impossible in write DB, easy in denormalized read DB)
    public OrderStats getCustomerStats(String customerId) {
        // Step 9 — Query pre-computed stats
        OrderStatsView stats = orderStatsRepository.findByCustomerId(customerId);
        // No computation needed, just return cached stats
        return OrderStats.builder()
            .ordersCount(stats.getOrderCount())
            .totalSpent(stats.getTotalAmount())
            .build();
    }
}

// DATABASE SCHEMAS:

// Write DB (normalized, ACID)
/*
CREATE TABLE orders (
    id VARCHAR PRIMARY KEY,
    customer_id VARCHAR NOT NULL,
    status VARCHAR,
    created_at TIMESTAMP
);

CREATE TABLE order_items (
    id VARCHAR PRIMARY KEY,
    order_id VARCHAR FOREIGN KEY,
    product_id VARCHAR,
    quantity INT,
    price DECIMAL
);

CREATE TABLE payments (
    id VARCHAR PRIMARY KEY,
    order_id VARCHAR FOREIGN KEY,
    amount DECIMAL,
    status VARCHAR (PENDING, COMPLETED, FAILED)
);
-- Normalized: order details, items, payments are separate tables
-- Queries need JOINs (slow for reads)
-- Writes are ACID (no anomalies)
*/

// Read DB (denormalized, eventual consistency)
/*
CREATE TABLE orders_view (
    id VARCHAR PRIMARY KEY,
    order_id VARCHAR,
    customer_id VARCHAR,
    customer_name VARCHAR,
    items_count INT,
    total_amount DECIMAL,
    status VARCHAR,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
    -- Indexes for fast queries
    INDEX idx_status (status),
    INDEX idx_customer (customer_id),
    INDEX idx_created (created_at)
);

-- Flat table: all info in one row (no JOINs)
-- Reads are fast (single table scan + index)
-- Updates are eventual (lag 100-500ms from write)
-- Can rebuild anytime by replaying events
*/
```

### What is Event Sourcing, and why does it fit here?

Event Sourcing is the pattern of **storing state as immutable events** instead of overwriting records. CQRS uses Event Sourcing to feed the read models. In an interview, if asked: *"Event Sourcing stores every state change as an event (OrderCreated, OrderFulfilled, OrderCancelled). Current state = sum of all events. CQRS projects these events into read models (orders_by_status, customer_stats). If a read model becomes corrupted, replay events to rebuild."*

---

## 🏢 Real World — Where Companies Use This

- **Netflix (CQRS for catalog):** Write side: content team updates metadata (normalized DB). Read side: search indexes (Elasticsearch) denormalized by genre, actor, rating. Searches fast (no joins). Updates infrequent (100/day) but reads are millions/sec.
- **Uber (CQRS for ride data):** Write side: ride service creates ride in normalized DB. Read side: driver app displays ride list (flat view: {ride_id, pickup, dropoff, fare}). No expensive queries on hot path.
- **Amazon (CQRS for product catalog):** Write: product team updates product (inventory, price, reviews). Read: search, recommendations, personalization all read denormalized snapshots. Decouples write freshness from read performance.
- **Booking.com (CQRS for availability):** Write: property managers update room availability. Read: search queries denormalized availability per date (no complex joins). Eventual consistency acceptable (100ms lag).

---

## 🧭 When to Use vs When NOT to Use

| Use CQRS when | Do NOT use when |
|---|---|
| Read and write patterns differ drastically | Reads and writes are balanced |
| Complex queries are needed (aggregations, joins) | Simple queries (single table lookups) |
| Many read replicas are needed | One database scales fine |
| Denormalization is acceptable (eventual consistency) | Strict consistency required for ALL reads |
| Read and write performance can be tuned separately | Single database optimization sufficient |

**The common mistake:** Using CQRS everywhere. CQRS adds complexity (eventual consistency, event handling). Only use when read/write patterns truly diverge. For balanced systems, single database is simpler.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Read scalability (denormalized, pre-computed views). Write optimization (normalized, ACID writes). Independent scaling (read replicas independent from writes). Complex queries made fast (no JOINs at read time). Flexibility (multiple projections from same events). |
| **You lose** | Operational complexity (event broker, listeners, projections). Eventual consistency (read lag 100-500ms). Debugging difficulty (state distributed across write DB + multiple read DBs). Event consistency (ensure all listeners processed without missing events). Deployment coordination (write + read models must evolve together). |
| **Failure mode** | Event listener fails → read DB becomes stale. Never catches up even after recovery. Mitigation: idempotent listeners, replay from checkpoint, monitoring lag. Event lost → read DB missing updates. Use durable event broker (Kafka guarantees persistence). Projection bug → wrong data in read DB. Replay events with fixed code to rebuild. |

---

## 🔬 Interview Q&As

### Q: "You update a product price in write DB. User queries read DB 5ms later. User still sees old price. How do you handle?"

> This is eventual consistency. Either: (1) Accept staleness (5-100ms lag is okay for most use cases). (2) For fresh-read requirements, read from write DB (slower but consistent). (3) Invalidate read cache immediately on write, then async update projections. (4) Use read-after-write consistency: user reads from write DB for their own writes, read DB for others. ⭐ **Tier 2 — Consistency trade-offs**

### Q: "You have a bug in your read projection. Wrong counts in customer_stats table. How do you fix?"

> Replay events. CQRS's advantage: if projection is wrong, don't rewrite history (write DB is immutable). Instead: (1) Fix listener code. (2) Clear read DB table (DELETE FROM customer_stats). (3) Replay all events from event store. Listeners re-process, rebuild stats correctly. Takes minutes, no data loss. ⭐ **Tier 2 — Repair/recovery**

### Q: "You have 10 different read projections (orders_by_status, orders_per_customer, customer_stats, monthly_revenue, etc.). Which services consume which events?"

> Each projection is a separate consumer (separate consumer group in Kafka). Order Service publishes one event (OrderCreated). 10 listeners subscribe (one per projection). Each processes independently and updates its table. If a listener fails, others keep going. On recovery, listener catches up from last offset. ⭐ **Tier 2 — Multi-projection design**

### Q: "At-least-once delivery: event re-delivered to listener. How do you prevent double-counting?"

> Idempotent processing. Listener generates idempotency key (derived from event_id or message ID). Before processing: check processed_events table. If already processed, skip. If not, process + insert into processed_events. Now safe to reprocess without side effects. ⭐ **Tier 2 — Idempotency**

---

## 🧾 TL;DR

> "CQRS separates reads from writes: write DB is normalized + ACID, read DB is denormalized. Events published on write, listeners update read models (eventual consistency). Multiple projections from same events. Complex queries made fast (no JOINs)."

---

## 🔗 Related Concepts

- **`22-event-sourcing.md`** — Event Sourcing is foundational to CQRS
- **`19-message-queues-kafka-rabbitmq.md`** — Events published via Kafka/RabbitMQ
- **`04-idempotency.md`** — Listeners must be idempotent (handle duplicate events)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Martin Fowler — CQRS** | Foundational article explaining when CQRS fits, trade-offs, relationship to Event Sourcing | ~20 min read |
| **Axon Framework Documentation** | CQRS implementation framework, event handlers, projections | ~20 min read |
| **Arpit Bhayani — CQRS Explained** (YouTube) | Real-world CQRS patterns, when NOT to use, anti-patterns | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 31. Covered CQRS separation of read/write paths, write-DB (normalized) vs read-DB (denormalized), event-driven projection, eventual consistency, idempotent listeners, multi-projection architecture. |
