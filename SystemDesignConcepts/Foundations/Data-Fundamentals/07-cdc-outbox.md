# CDC + Outbox Pattern

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`

---

## 🎯 Why This Matters

Every time you write to a database AND publish an event to Kafka (or send an email, call another service), you have a dual-write problem: two separate operations, no shared transaction, and one can fail after the other succeeds. This causes data inconsistency silently — the database says "order placed" but Kafka never got the event, so the warehouse was never notified. The outbox pattern is the industry-standard fix. It appears in any design question involving event-driven architecture: notification services, saga orchestration, microservice communication, and audit pipelines.

**Which round:** R2 System Design — D3 (Notification Service), any event-driven system design.
**Why senior engineers own this:** Dual-write is the silent killer of event-driven systems. "Just publish to Kafka after the DB commit" is the junior answer. Senior engineers know why that's wrong and what the correct pattern is.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Dual-Write Problem** | performing two separate writes (DB + Kafka) with no shared transaction; one can fail after the other succeeds | DB commit succeeds, Kafka down → event never published, warehouse never notified |
| **Outbox Table** | extra DB table inside the same transaction as the business write; stores pending events to be published | `INSERT INTO orders ... ; INSERT INTO outbox (event='OrderCreated', ...) — same transaction |
| **Outbox Processor (Relay)** | background process that polls the outbox table, publishes events to Kafka, then marks them sent | polls every 100ms; publishes unsent rows; marks `status='SENT'` |
| **CDC (Change Data Capture)** | reads the database's WAL/transaction log directly instead of polling a table; lower latency, no polling overhead | Debezium reads Postgres WAL → streams every INSERT/UPDATE/DELETE to Kafka |
| **Debezium** | open-source CDC connector; reads DB transaction logs and streams changes to Kafka as events | Debezium Postgres connector streams every `orders` row change to `orders.cdc` topic |
| **Idempotent Consumer** | consumer that safely handles duplicate messages — checks if event already processed before acting | consumer checks `processed_event_ids` table before processing; duplicate → skip |
| **Transaction Log Tailing** | CDC strategy where a dedicated process tails the DB WAL instead of the application writing to an outbox | Debezium tails Postgres WAL; no application code change needed |
| **At-Least-Once Delivery** | guarantee that each event is delivered at minimum once; duplicates possible; idempotent consumer is the defense | outbox relay retries on Kafka timeout → same event may publish twice |

---

## 🧠 The Mental Model

Think of a **small business owner with an accounting journal**.

Every time a sale happens, the owner does two things: records it in the shop's ledger (the database) AND mails an invoice to the customer (the Kafka event). If the owner writes in the ledger but the post office is closed, the invoice never goes out. If the invoice goes out first but the pen runs dry mid-ledger-entry, the sale is invoiced but not recorded. Two separate actions = two failure points = inconsistency.

A good accountant solves this with a **journal + reconciliation process**:

1. **The journal (outbox table):** Before anything else, the owner writes the sale in a journal — just a quick draft note. The journal lives in the same notebook as the ledger. Writing the ledger entry AND the journal note are done in ONE stroke of the pen — they either both happen or neither does. This is the outbox table in the same database transaction.

2. **The postal worker (outbox processor):** A dedicated postal worker comes by every few minutes, looks at the journal for unsent notes, mails them to customers, and marks them "sent." If the post office crashes mid-delivery, the notes are still in the journal — the postal worker retries. Nothing is lost.

3. **The customer's filing cabinet (idempotent consumer):** The customer has a filing system. If the same invoice arrives twice (the postal worker retried), they check their filing cabinet: "Did I already process invoice #4521?" If yes, they discard the duplicate. This is the consumer-side idempotency check.

**The key insight is:** Atomicity between the DB write and the event publish is impossible unless they share a transaction. The outbox table lives inside the DB transaction, so you get atomicity for free. The Kafka publish happens outside the transaction, by a separate process, with retries — but the consumer's idempotency guard makes retries safe.

---

## 🎨 Visual — The Dual-Write Problem vs The Outbox Fix

```
  THE DUAL-WRITE PROBLEM (what goes wrong without outbox)
  ─────────────────────────────────────────────────────────────────

  Service                DB                     Kafka
  ───────                ──                     ─────
  INSERT order     ──▶  ✅ committed
  Publish event    ──▶                          ❌ Kafka down / network drop
                                                   Event NEVER sent
  Result: DB has the order. Kafka has no event. Warehouse never notified.
  Silent inconsistency — no error surfaced to the caller. ❌

  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─

  OR:
  Publish event    ──▶                          ✅ Kafka received
  INSERT order     ──▶  ❌ DB crash
                        Order never saved.
  Result: Kafka has an event for an order that doesn't exist in DB. ❌


  THE OUTBOX PATTERN (the fix)
  ─────────────────────────────────────────────────────────────────

  ┌─────────────────────────── ONE TRANSACTION ──────────────────┐
  │  Service                          DB                         │
  │  ───────                          ──                         │
  │  INSERT order              ──▶  orders table ✅              │
  │  INSERT outbox entry       ──▶  outbox table ✅              │
  └──────────────────────────────────────────────────────────────┘
         Both committed atomically OR both rolled back.

  SEPARATELY — Outbox Processor:
  ─────────────────────────────────────────────────────────────────
  Processor           outbox table            Kafka
  ─────────           ────────────            ─────
  SELECT unsent  ◀──  [ {id:1, status:PENDING, event: {...}} ]
  Publish event  ──────────────────────────▶  ✅ published
  UPDATE status  ──▶  status = SENT

  If Kafka is down → processor retries (row stays PENDING)
  If processor crashes after Kafka publish but before UPDATE → retries
  → Consumer gets duplicate → idempotency check on consumer side ✅

  KEY INVARIANT:
     The outbox entry and the DB write share ONE transaction.
     At-least-once delivery is guaranteed — the event will eventually be published.
     Exactly-once is the consumer's responsibility via idempotency.
```

---

## ⚙️ How It Actually Works

### Part 1 — The Dual-Write Problem

```java
// ❌ BAD — dual write: DB commit and Kafka publish are TWO separate operations
@Transactional
public void placeOrder(PlaceOrderRequest req) {
    Order order = orderRepo.save(new Order(req));
    // Transaction commits here. DB has the order.

    // If this fails — Kafka never gets the event.
    // If Kafka is slow — we might retry and publish twice.
    // No way to make these two operations atomic.
    kafkaTemplate.send("order.placed", new OrderPlacedEvent(order.getId()));
}
```

---

### Part 2 — Outbox Table Design

```java
// The outbox table — lives in the SAME database as your domain tables
// CREATE TABLE outbox (
//     id            BIGINT PRIMARY KEY AUTO_INCREMENT,
//     aggregate_id  BIGINT NOT NULL,        -- the business entity ID (order ID, user ID)
//     event_type    VARCHAR(100) NOT NULL,  -- "ORDER_PLACED", "PAYMENT_SUCCEEDED"
//     payload       TEXT NOT NULL,          -- JSON of the event
//     status        ENUM('PENDING','SENT','FAILED') DEFAULT 'PENDING',
//     created_at    TIMESTAMP DEFAULT NOW(),
//     sent_at       TIMESTAMP NULL
// );
// INDEX: (status, created_at) for "select pending events ordered by time"

@Entity
@Table(name = "outbox")
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
```

---

### Part 3 — The Transactional Write (Outbox + Domain in One Transaction)

**Steps:**
1. **Inside one `@Transactional` method:** write the domain object AND the outbox entry.
2. Both INSERT statements go to the same DB. If the transaction rolls back (any reason), both roll back.
3. Return success to the caller — the event will be published asynchronously.

```java
// ✅ GOOD — outbox pattern: both writes in ONE transaction
@Transactional
public void placeOrder(PlaceOrderRequest req) {
    // Step 1: domain write
    Order order = orderRepo.save(new Order(req));

    // Step 1 cont: outbox write — same transaction
    String payload = objectMapper.writeValueAsString(new OrderPlacedEvent(order.getId()));
    OutboxEntry entry = new OutboxEntry(
        order.getId(),
        "ORDER_PLACED",
        payload,
        OutboxStatus.PENDING,
        Instant.now()
    );
    outboxRepo.save(entry);

    // Transaction commits here — BOTH writes committed atomically.
    // Kafka publish happens OUTSIDE this transaction, by the outbox processor.
}
```

---

### Part 4 — Outbox Processor (Polling Approach)

The outbox processor runs as a background job — it polls the outbox table for PENDING entries and publishes them to Kafka.

**Steps:**
1. **SELECT** rows with `status = PENDING` (ordered by `created_at`, small batch).
2. **For each entry:** publish the payload to the appropriate Kafka topic.
3. **On success:** UPDATE `status = SENT`, set `sent_at`.
4. **On Kafka failure:** leave as PENDING — will be retried on next poll.
5. **Handle duplicates:** Kafka publish may succeed but the UPDATE may fail (processor crash). On retry, the event is published twice. Consumer must be idempotent (see `04-idempotency.md`).

```java
@Scheduled(fixedDelay = 1000)  // polls every 1 second
@Transactional
public void processOutbox() {
    // Step 1: fetch a small batch of pending entries
    List<OutboxEntry> pending = outboxRepo.findPendingBatch(10);
    if (pending.isEmpty()) {
        return;
    }

    for (OutboxEntry entry : pending) {
        try {
            // Step 2: publish to Kafka
            kafkaTemplate.send(toTopic(entry.getEventType()), entry.getPayload()).get();

            // Step 3: mark as sent
            entry.setStatus(OutboxStatus.SENT);
            entry.setSentAt(Instant.now());
            outboxRepo.save(entry);
        } catch (Exception e) {
            // Step 4: Kafka publish failed — leave as PENDING, log the error
            log.error("Failed to publish outbox entry {}: {}", entry.getId(), e.getMessage());
        }
    }
}
```

---

### Part 5 — CDC with Debezium (Alternative to Polling)

**The problem with polling:** A polling loop adds DB query load every second. If the batch is large, it can lag. There's also a trade-off between poll frequency (latency) and DB load.

**CDC (Change Data Capture)** is an alternative: instead of polling the outbox table, use a tool that reads the database's internal transaction log and reacts to every INSERT in real time.

### What is Debezium, and why does it fit here?

**Debezium** is an open-source CDC platform — it connects to a database's internal write-ahead log (WAL in Postgres, binlog in MySQL) and streams every INSERT/UPDATE/DELETE as an event to Kafka. It reacts to changes with very low latency (typically low tens of milliseconds end-to-end through the Kafka Connect pipeline) and, crucially, with **zero polling overhead / no extra query load on the DB**.

**In an interview, if asked:** "Debezium is a CDC tool that reads the database's write-ahead log — the same log the DB uses internally for durability — and streams every row-level change to Kafka in near-real time. For the outbox pattern, Debezium watches the outbox table and publishes events automatically the moment they're INSERTed. The real win is no polling and no added DB load; end-to-end latency is typically low tens of milliseconds (not microseconds — it flows through a Connect → Kafka pipeline)."

```
  DEBEZIUM CDC FLOW
  ─────────────────────────────────────────────────────────────────

  DB Transaction Log (WAL / binlog)
          │
          │  Debezium connector reads continuously
          ▼
  Debezium Kafka Connector
          │
          │  Streams change events
          ▼
  Kafka topic: "db.orders.outbox"
          │
          ▼
  Consumer services (notification, warehouse, analytics)

  KEY INVARIANT:
     Debezium reads the log that the DB writes for its OWN durability.
     If the DB committed the row, Debezium will see it — no polling gap.
     Debezium tracks its own offset in the log — restart is safe (resumes from last position).
```

### What is WAL (Write-Ahead Log)?

**WAL** (Write-Ahead Log) is the internal log every relational database writes before committing any change. Every INSERT, UPDATE, DELETE is first written to the WAL sequentially on disk — this is what makes the DB durable (even if it crashes mid-write, the WAL lets it recover). Debezium reads this same log.

**In an interview, if asked:** "The WAL is the database's internal durability log — every write is recorded there first. Debezium is a CDC tool that reads the WAL as an external consumer, converting database mutations into Kafka events. Because the WAL is written atomically with the DB commit, Debezium only sees committed rows — it can't see partial transactions."

---

### Part 6 — At-Least-Once Delivery and Idempotent Consumer

The outbox pattern guarantees **at-least-once delivery** — every event will eventually be published. It does NOT guarantee exactly-once — if the processor crashes after publishing but before marking SENT, the event is published again on retry.

**Consumer responsibility:** every consumer must be **idempotent** — processing the same event twice must produce the same result as processing it once.

```java
// Consumer with idempotency check — see 04-idempotency.md for full detail
@KafkaListener(topics = "order.placed")
@Transactional
public void onOrderPlaced(OrderPlacedEvent event) {
    // Idempotency check — have we processed this event already?
    if (processedEventRepo.existsById(event.getEventId())) {
        log.info("Duplicate event ignored: {}", event.getEventId());
        return;
    }

    // Process the event
    warehouseService.reserveInventory(event.getOrderId());

    // Mark as processed — INSIDE the same transaction as the actual work
    processedEventRepo.save(new ProcessedEvent(event.getEventId(), Instant.now()));
}
```

**The critical rule:** The `processedEventRepo.save()` must be inside the SAME transaction as the actual work. If they're separate and the app crashes between them, the work is done but the dedup record is lost — the event gets processed again on the next delivery.

---

## 🏢 Real World — Where Companies Use This

- **DocuSign** — When a signing is completed, the envelope status changes in the DB. The notification to the API customer (webhook) uses the outbox pattern — `outbox` INSERT is part of the same transaction as `envelope.status = COMPLETED`. The webhook is delivered by a dedicated processor, with retries on failure.
- **Uber** — When a trip is completed, the DB write (trip record) and the event publish (billing, driver rating, analytics) use the outbox pattern. A missed event here means a driver doesn't get paid — critical reliability requirement.
- **Flipkart** — Order placement: `orders` INSERT + `outbox` INSERT in one transaction. The outbox processor fans out to: warehouse (inventory reservation), notifications (email/SMS), analytics (revenue event), loyalty points service.
- **Razorpay** — Payment status change: `payments` UPDATE + `outbox` INSERT. The outbox processor notifies the merchant via webhook with retry logic. Merchant's server must be idempotent — same payment event arriving twice should not credit the order twice.
- **Swiggy** — Restaurant order: `orders` INSERT + `outbox` INSERT. Fan-out to restaurant tablet notification, delivery partner assignment service, analytics pipeline — all from one outbox event via multiple consumer groups.

---

## 🧭 When to Use vs When NOT to Use

| Use the Outbox pattern when | Don't need Outbox when |
|---|---|
| You write to a DB AND publish an event in the same logical operation | The operation only writes to one system |
| Event loss is unacceptable (payment confirmation, order placement) | Eventual delivery is guaranteed by another mechanism |
| You need at-least-once delivery across DB and messaging system | You're using a distributed transaction coordinator (2PC) — rare, expensive |
| Microservices need loose coupling via events | The consumer is in the same service and same DB transaction |

**The common mistake:** Publishing to Kafka inside a `@Transactional` method after the DB write. The `@Transactional` boundary only covers the DB — Kafka is outside the transaction. A Kafka publish failure inside `@Transactional` does NOT roll back the DB write. You get silent inconsistency with no exception visible to the caller.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Guaranteed at-least-once event delivery — no silent loss of events. DB and event publish are atomic from the producer's perspective. Kafka downtime doesn't affect the main business operation. |
| **You lose** | Additional outbox table in every service that publishes events. Outbox processor adds operational complexity (deployment, monitoring, lag alerts). Consumers MUST be idempotent — this is a contract every consumer team must honour. |
| **Failure mode** | Outbox table grows unbounded if the processor is down for a long time. Add a scheduled cleanup job: `DELETE FROM outbox WHERE status = 'SENT' AND sent_at < NOW() - INTERVAL 7 DAYS`. Monitor outbox lag — if PENDING count keeps growing, the processor is stuck. |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "What is the dual-write problem?"
> When a service must write to a database AND publish an event (Kafka/RabbitMQ/webhook), those are two separate operations with no shared transaction. If the DB commits but Kafka publish fails, the event is lost — the downstream system never knows. If Kafka receives the event but the DB crashes, you have an event for a record that doesn't exist. There is no way to make a relational DB and a message broker atomic with a single transaction — that's the dual-write problem.

### Q: "How does the outbox pattern solve dual-write?"
> Instead of publishing to Kafka directly, the service inserts the event payload into an `outbox` table in the same DB transaction as the domain write. Both writes are atomic — either both commit or neither does. A separate outbox processor (polling or CDC-based) reads the outbox table and publishes to Kafka, marking entries as sent. If the processor fails mid-publish, entries remain PENDING and are retried — at-least-once delivery is guaranteed. Consumers must be idempotent to handle the rare duplicate.

### Q: "What is CDC and how does Debezium fit in?"
> CDC (Change Data Capture) is the practice of reading a database's internal write-ahead log to detect row-level changes in real time. Debezium is a CDC platform that connects to MySQL/Postgres WAL and streams every INSERT/UPDATE/DELETE as a Kafka event. For the outbox pattern: instead of a polling job querying the outbox table every second, Debezium watches the outbox table and publishes changes to Kafka the moment they're committed — zero polling, sub-millisecond latency, no extra DB query load.

---

### Tier 2 — Cross / Probe Questions

### Q: "Your outbox processor published the event to Kafka but crashed before marking it SENT. What happens?"
> The entry stays PENDING in the outbox table. On the next processor run, it publishes again — the consumer receives a duplicate. This is expected in the outbox pattern — it guarantees at-least-once, not exactly-once. The consumer must handle it with an idempotency check: store a `processed_events` table keyed by event ID; if the ID is already there, skip processing. The consumer-side idempotency is the contract that makes at-least-once delivery safe. See `04-idempotency.md` for the full implementation.

### Q: "How do you prevent the outbox table from growing forever?"
> Add a cleanup job: `DELETE FROM outbox WHERE status = 'SENT' AND sent_at < NOW() - INTERVAL 7 DAYS`. Run it nightly. Retain 7 days for debugging — if a consumer reports a missing event, you can re-publish from the outbox history. Monitor two things: (1) PENDING count — if it grows, the processor is stuck; alert when PENDING rows are older than 5 minutes. (2) Row count — if SENT rows aren't being cleaned up, add a lag alert on the cleanup job.

### Q: "You're using Debezium for CDC. Your database fails over to a replica. What happens to the Debezium connector?"
> Debezium tracks its position in the WAL as an offset (log sequence number). On primary failover: (1) the new primary starts its own WAL, (2) Debezium must be reconfigured to point to the new primary, (3) it resumes from the stored offset. Modern Debezium setups handle this automatically with connection retry logic. The risk: if there's a WAL gap between the old primary and the new primary (data that was committed on the old primary but not replicated before failover), those changes may be missed. This is a known limitation — for high-durability requirements, use synchronous replication (`synchronous_commit = on` in Postgres) to ensure the replica has all changes before the failover.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"The outbox pattern solves dual-write by inserting the event into an outbox table in the same DB transaction as the domain write — atomicity is free from the DB. A separate processor publishes to Kafka with retries, guaranteeing at-least-once delivery. Consumers use an idempotency check to handle the rare duplicate safely."*

---

## 🔗 Related Concepts

- **`04-idempotency.md`** — The Kafka consumer idempotency check (processed_events table) is the essential complement to the outbox pattern. Outbox guarantees at-least-once; idempotency on the consumer makes at-least-once safe.
- **`03-caching.md`** — Write-behind caching (cache writes, DB writes asynchronously) follows the same principle: decouple the fast write path from the slower durable write.
- **`06-distributed-locking.md`** — If multiple outbox processor instances run simultaneously, they may process the same outbox entry. Either use `SELECT ... FOR UPDATE SKIP LOCKED` to partition work, or use a distributed lock to ensure only one processor runs at a time.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — CDC and Outbox Pattern** (YouTube) | Deep implementation detail, Debezium setup, real production patterns. Search: "Arpit Bhayani outbox pattern" | ~25 min |
| **ByteByteGo — Transactional Outbox** (YouTube) | Visual walkthrough of polling vs CDC approaches. Search: "ByteByteGo outbox pattern" | ~8 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — D3 (Notification Service) and any event-driven system requires reliable event publishing. Covers dual-write problem, outbox table, polling processor, Debezium CDC, at-least-once delivery, idempotent consumer. |
| Jul 19, 2026 | **Factual fix.** Corrected the Debezium latency claim — "microseconds / sub-millisecond" overstated it; realistic end-to-end latency through the Kafka Connect pipeline is low tens of milliseconds. Reframed the true benefit as zero polling / no added DB load rather than microsecond delivery. |
