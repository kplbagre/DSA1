# Saga Pattern — Fundamentals

---

## 🎯 Why This Matters

You have 4 microservices: Order, Payment, Inventory, Delivery. A customer places an order. You need all 4 to succeed — or none at all (if payment fails, don't ship). Traditional ACID transactions don't work across microservices (no distributed locking). Saga pattern coordinates without ACID: publishes events → each service reacts → on failure, compensate (undo). At SDE 3: you must know orchestration (central coordinator) vs choreography (event-driven), and the difference from ACID.

---

## 🧠 The Mental Model

Imagine booking a vacation (flight + hotel):

- **ACID attempt (doesn't work):** Book flight and hotel in one atomic transaction. If one fails, rollback both. But flight company and hotel company are separate services; there's no single "transaction."
- **Saga approach:** You book flight. Flight confirms. Then book hotel. Hotel confirms. All good. But if hotel is booked out, you need to cancel the flight (compensate). Full process: book flight → wait for hotel booking → if hotel fails → cancel flight.

**Orchestration (central coordinator):** You (travel agent) call flight company, wait for confirmation, then call hotel company, etc. You coordinate the steps.

**Choreography (event-driven):** You book flight. Flight company sends "FlightBooked" event. Hotel listening to that event books itself (if available) and sends "HotelBooked." If hotel can't book, it sends "HotelFailed." Flight company listening sees the failure, cancels (sends "FlightCancelled"). No central coordinator.

**The key insight:** Sagas replace ACID with compensating transactions (undo). Each step publishes events. If a step fails, previous steps are compensated (undone in reverse order).

---

## 🎨 Visual — Saga Orchestration vs Choreography

```
SAGA ORCHESTRATION (central coordinator):

Client places order
  ↓
┌─────────────────────────────┐
│ Saga Orchestrator           │
│ (Central State Machine)     │
│ Step 1: Reserve Inventory   │
│ Step 2: Process Payment     │
│ Step 3: Book Delivery       │
└──────────┬────────┬────────┬┘
           │        │        │
     [API Call]  [API Call] [API Call]
           ↓        ↓        ↓
    ┌──────────┐ ┌────────┐ ┌──────────┐
    │ Inventory│ │Payment │ │ Delivery │
    │ Service  │ │Service │ │Service   │
    └──────────┘ └────────┘ └──────────┘

Flow:
1. Orchestrator → Inventory: "Reserve sku:ABC qty:2"
   Inventory: "Reserved. Reservation#123"
   
2. Orchestrator → Payment: "Charge $100"
   Payment: "Charged. TX#456"
   
3. Orchestrator → Delivery: "Schedule delivery"
   Delivery: "Scheduled. Slot#789"

If Step 3 fails (Delivery full):
   Orchestrator initiates rollback:
   - Compensate Step 2: "Refund TX#456"
   - Compensate Step 1: "Cancel Reservation#123"
   
All or nothing: either all succeed or all compensate


SAGA CHOREOGRAPHY (event-driven, no coordinator):

Client places order
  ↓
┌────────────┐
│ Order      │ Publishes "OrderCreated" event
│ Service    │
└────────────┘
       ↓ event published
    ┌──────────────────┐
    │ Kafka/MQ Topic   │
    │ "order-events"   │
    └────┬──────┬──────┘
         │      │
      [Subscriber] [Subscriber]
         │        │
    ┌──────────┐  ┌────────┐
    │Inventory │  │ Payment│
    │ Listens  │  │Listens │
    └─────┬────┘  └───┬────┘
          │           │
    Publishes       Publishes
    "InventoryReserved"  "PaymentProcessed"
          ↓           ↓
         [Topic]     [Topic]
          ↓           ↓
    ┌──────────────────────┐
    │ Delivery Service     │
    │ Listens to both:     │
    │ If both present →    │
    │   "DeliveryScheduled"│
    │ Else → failure event │
    └──────────────────────┘

Flow:
1. Order: "OrderCreated(order#1, items, total)"
2. Inventory listening: reserves, publishes "InventoryReserved(order#1, res#123)"
3. Payment listening: charges, publishes "PaymentProcessed(order#1, tx#456)"
4. Delivery listening to both events: schedules, publishes "DeliveryScheduled(order#1, slot#789)"

If Step 2 fails (inventory empty):
   Inventory publishes "InventoryFailed(order#1, reason)"
   Payment sees it, publishes "PaymentRefunded(order#1, tx#456)"
   Delivery sees it, skips scheduling
   Order publishes "OrderCancelled(order#1)"
   
No central coordinator; each service reacts autonomously


KEY INVARIANTS:
Orchestration: central state machine controls flow (simple to understand, single point of failure)
Choreography: autonomous services react to events (decoupled, harder to debug)
Compensation: explicit undo logic for each step (must be idempotent)
Idempotency: if event replayed, ensure same result (refund twice = bad)
```

---

## ⚙️ How It Actually Works

**Pattern 1: Orchestration (Spring Statemachine or Apache Camel)**

**Steps:**
1. Define saga steps as states in a state machine.
2. On each successful step, transition to the next state.
3. Define compensating actions for each step.
4. If any step fails, traverse backward, executing compensations.

```java
// Saga Orchestration using Spring
@Component
public class OrderSagaOrchestrator {
    enum OrderSagaState {
        PENDING, INVENTORY_RESERVED, PAYMENT_PROCESSED, DELIVERY_SCHEDULED, COMPLETED
    }

    enum OrderSagaEvent {
        START, INVENTORY_OK, PAYMENT_OK, DELIVERY_OK, INVENTORY_FAILED, PAYMENT_FAILED, DELIVERY_FAILED
    }

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private PaymentClient paymentClient;

    @Autowired
    private DeliveryClient deliveryClient;

    // Step 1 — define state machine
    public void startOrderSaga(Order order) {
        OrderSagaState state = OrderSagaState.PENDING;

        try {
            // Step 2 — execute steps
            String reservationId = executeInventoryStep(order);
            state = OrderSagaState.INVENTORY_RESERVED;

            String transactionId = executePaymentStep(order);
            state = OrderSagaState.PAYMENT_PROCESSED;

            String deliverySlot = executeDeliveryStep(order);
            state = OrderSagaState.DELIVERY_SCHEDULED;

            state = OrderSagaState.COMPLETED;
        } catch (Exception e) {
            // Step 4 — compensate (rollback in reverse order)
            compensateSaga(state, order);
        }
    }

    private String executeInventoryStep(Order order) {
        // Step 1 — Inventory
        InventoryReserveRequest request = new InventoryReserveRequest(
            order.getOrderId(), order.getItems()
        );
        InventoryReserveResponse response = inventoryClient.reserve(request);

        if (!response.isSuccess()) {
            throw new InventoryFailedException("Inventory full");
        }
        return response.getReservationId(); // save for compensation
    }

    private String executePaymentStep(Order order) {
        // Step 2 — Payment
        PaymentRequest request = new PaymentRequest(
            order.getOrderId(), order.getTotal(), order.getPaymentInfo()
        );
        PaymentResponse response = paymentClient.charge(request);

        if (!response.isSuccess()) {
            throw new PaymentFailedException("Card declined");
        }
        return response.getTransactionId();
    }

    private String executeDeliveryStep(Order order) {
        // Step 3 — Delivery
        DeliveryScheduleRequest request = new DeliveryScheduleRequest(
            order.getOrderId(), order.getAddress(), order.getItems()
        );
        DeliveryScheduleResponse response = deliveryClient.schedule(request);

        if (!response.isSuccess()) {
            throw new DeliveryFailedException("No slots available");
        }
        return response.getDeliverySlot();
    }

    // Step 4 — compensate based on how far we got
    private void compensateSaga(OrderSagaState failedState, Order order) {
        // Compensation in reverse order
        if (failedState == OrderSagaState.DELIVERY_SCHEDULED) {
            compensateDelivery(order);
        }
        if (failedState == OrderSagaState.PAYMENT_PROCESSED || failedState == OrderSagaState.DELIVERY_SCHEDULED) {
            compensatePayment(order);
        }
        if (failedState == OrderSagaState.INVENTORY_RESERVED || failedState.ordinal() > OrderSagaState.INVENTORY_RESERVED.ordinal()) {
            compensateInventory(order);
        }

        // Mark order as failed
        orderService.markFailed(order.getOrderId(), "Saga compensation executed");
    }

    private void compensateDelivery(Order order) {
        try {
            deliveryClient.cancel(order.getOrderId());
        } catch (Exception e) {
            // Idempotency: if already cancelled, that's fine
            log.warn("Delivery cancellation failed or already done", e);
        }
    }

    private void compensatePayment(Order order) {
        try {
            paymentClient.refund(order.getOrderId());
        } catch (Exception e) {
            // Idempotency: if already refunded, that's fine
            log.warn("Payment refund failed or already done", e);
        }
    }

    private void compensateInventory(Order order) {
        try {
            inventoryClient.cancelReservation(order.getOrderId());
        } catch (Exception e) {
            // Idempotency: if already cancelled, that's fine
            log.warn("Inventory cancellation failed or already done", e);
        }
    }
}

// Service interfaces (HTTP clients)
@Component
public class InventoryClient {
    @Autowired
    private RestTemplate restTemplate;

    public InventoryReserveResponse reserve(InventoryReserveRequest req) {
        return restTemplate.postForObject("http://inventory:8080/reserve", req, InventoryReserveResponse.class);
    }
}
```

---

**Pattern 2: Choreography (Event-Driven, Kafka)**

**Steps:**
1. Order service publishes "OrderCreated" event.
2. Inventory service listens, reserves, publishes "InventoryReserved" or "InventoryFailed."
3. Payment service listens, charges, publishes "PaymentProcessed" or "PaymentFailed."
4. Delivery service listens to successful completion events, schedules.
5. Services listen to failure events and compensate autonomously.

```java
// Choreography — Event-Driven Saga

// 1. Order Service publishes OrderCreated
@Service
public class OrderService {
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void createOrder(Order order) {
        // Persist order
        orderRepository.save(order);

        // Step 1 — publish event
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getOrderId(), order.getItems(), order.getTotal()
        );
        kafkaTemplate.send("order-events", order.getOrderId(), event);
    }
}

// 2. Inventory Service listens and reacts
@Component
public class InventoryEventListener {
    @KafkaListener(topics = "order-events", groupId = "inventory-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            // Step 2 — reserve inventory
            InventoryReservation reservation = inventoryService.reserve(event.getOrderId(), event.getItems());

            // Publish success event
            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                event.getOrderId(), reservation.getId()
            );
            kafkaTemplate.send("order-events", event.getOrderId(), reservedEvent);
        } catch (InventoryException e) {
            // Publish failure event
            InventoryFailedEvent failedEvent = new InventoryFailedEvent(
                event.getOrderId(), e.getMessage()
            );
            kafkaTemplate.send("order-events", event.getOrderId(), failedEvent);
        }
    }
}

// 3. Payment Service listens and reacts
@Component
public class PaymentEventListener {
    @KafkaListener(topics = "order-events", groupId = "payment-group")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        try {
            // Only process if inventory succeeded
            PaymentTransaction tx = paymentService.charge(event.getOrderId(), event.getAmount());

            // Publish success
            PaymentProcessedEvent processedEvent = new PaymentProcessedEvent(
                event.getOrderId(), tx.getId()
            );
            kafkaTemplate.send("order-events", event.getOrderId(), processedEvent);
        } catch (PaymentException e) {
            // Publish failure; this triggers compensation chain
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                event.getOrderId(), e.getMessage()
            );
            kafkaTemplate.send("order-events", event.getOrderId(), failedEvent);
        }
    }

    // Step 5 — listen to failure events and compensate
    @KafkaListener(topics = "order-events", groupId = "payment-group")
    public void handleInventoryFailed(InventoryFailedEvent event) {
        // Don't charge if inventory failed
        // (payload never reaches this payment service)
    }
}

// 4. Delivery Service listens to successful completion
@Component
public class DeliveryEventListener {
    @KafkaListener(topics = "order-events", groupId = "delivery-group")
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        try {
            // Only schedule if payment succeeded
            DeliverySlot slot = deliveryService.schedule(event.getOrderId());

            DeliveryScheduledEvent scheduledEvent = new DeliveryScheduledEvent(
                event.getOrderId(), slot.getId()
            );
            kafkaTemplate.send("order-events", event.getOrderId(), scheduledEvent);
        } catch (DeliveryException e) {
            // Publish failure; triggers compensation
            DeliveryFailedEvent failedEvent = new DeliveryFailedEvent(
                event.getOrderId(), e.getMessage()
            );
            kafkaTemplate.send("order-events", event.getOrderId(), failedEvent);
        }
    }

    // Compensate if delivery fails
    @KafkaListener(topics = "order-events", groupId = "compensation-group")
    public void handleDeliveryFailed(DeliveryFailedEvent event) {
        // Payment service also listening: triggers refund
        // Inventory service also listening: triggers reservation cancellation
    }
}

// Step 5 — Compensation chain (all services listen to failure events)
@Component
public class CompensationOrchestrator {
    @KafkaListener(topics = "order-events", groupId = "compensate-payment")
    public void compensatePaymentOnFailure(DeliveryFailedEvent event) {
        paymentService.refund(event.getOrderId());
        // Publish RefundedEvent
    }

    @KafkaListener(topics = "order-events", groupId = "compensate-inventory")
    public void compensateInventoryOnFailure(PaymentFailedEvent event) {
        inventoryService.cancelReservation(event.getOrderId());
        // Publish ReservationCancelledEvent
    }
}
```

---

**What are Compensating Transactions, Idempotency, and Eventual Consistency, and why do they fit here?**

- **Compensating Transactions:** Undo logic for each saga step. If payment fails, refund is the compensation. In an interview: *"Each step must have a matching undo action; compensation executes if later steps fail."*

- **Idempotency:** Compensation must be safe to run multiple times. If refund event is processed twice, second refund should be a no-op (already refunded). In an interview: *"Sagas are not ACID; network can duplicate events. Idempotency prevents double-charging."*

- **Eventual Consistency:** Saga completes when all steps eventually succeed or all compensations complete. Not immediate. In an interview: *"Sagas trade immediate consistency for availability; eventual consistency across services."*

---

## 🏢 Real World — Where Companies Use This

- **Uber (ride booking):** OrderCreated → Driver assigned → Payment processed → Trip started. If driver cancels before payment, compensation refunds and unassigns.
- **Swiggy (food delivery):** OrderCreated → Restaurant accepts → Delivery assigned → Payment charged. If delivery cancels, payment compensates.
- **Booking.com (reservations):** BookingCreated → Hotel reserved → Payment charged → Confirmation sent. If payment fails, hotel reservation is cancelled.
- **Razorpay (payment processing):** PaymentInitiated → KYC verified → Amount debited → Settlement. If settlement fails, debit is compensated.

---

## 🧭 When to Use vs When NOT to Use

| Use sagas when | Do NOT use when |
|---|---|
| Multiple microservices needed for one business operation | Single service (use ACID transactions) |
| All-or-nothing semantics needed (all succeed or all rollback) | Partial success is acceptable |
| Services are autonomous (can't enforce locks) | Strong consistency required (use distributed locks or saga-less transactions) |
| Compensating actions are well-defined | Compensation logic is complex or unclear |

**The common mistake:** Using sagas for every multi-step process. If the steps are in one service, use ACID transactions. Sagas are expensive (events, listeners, compensation logic). Reserve for genuinely distributed scenarios.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Handles distributed transactions without ACID. Services are decoupled. Failure isolation (one service failure doesn't crash all). |
| **You lose** | Eventual consistency (not immediate). Compensation logic is complex. Debugging is hard (spread across services). Double-charging risk if not idempotent. |
| **Failure mode** | Partial completion (payment processed but delivery fails, compensation refunds). Or stuck compensation (refund endpoint is down, refund never completes). Mitigation: idempotency, retries, monitoring. |

---

## 🔬 Interview Q&As

### Q: "Orchestration vs Choreography — which is better?"

> **Orchestration:** Easier to understand (central state machine). Easier to test (one place to verify the flow). Single point of failure (coordinator crashes = saga stuck). **Choreography:** Decoupled (services don't know about each other). Harder to debug (spread across services, hard to trace). No single failure point. Pick based on team maturity and complexity. Start with orchestration (simpler); move to choreography if services need autonomy. ⭐ **Tier 2 — trade-off**

### Q: "A refund fails during compensation. The payment is now stuck. What do you do?"

> Compensation must be idempotent and retryable. Set up a background job that periodically retries failed compensations (with exponential backoff). Also monitor: if a refund fails, alert ops. Manual intervention: ops calls payment service API directly to refund. In choreography, failed compensation is published as an event; retry listeners pick it up. Both require explicit recovery logic (not automatic). ⭐ **Tier 2 — operational**

### Q: "How do you prevent double-charging in a saga?"

> Idempotency keys. Payment request includes a unique idempotency key (e.g., order_id + "payment"). Payment service returns: "already processed with ID X" if key is seen again. Ensures retry doesn't charge twice. Also: saga needs to track completion state (in database or distributed transaction log). On retry, check if step already completed; if yes, skip it. ⭐ **Tier 2 — correctness**

### Q: "Design a saga for a 5-step e-commerce process: order → inventory → warehouse pick → payment → delivery."

> Use orchestration: central OrderSaga coordinates the 5 steps. Each step is a service call with a timeout (e.g., 30s). If any step fails, compensate in reverse: (1) order created (no undo needed), (2) inventory reserved (compensate: cancel), (3) warehouse picked (compensate: return to shelf), (4) payment charged (compensate: refund), (5) delivery scheduled (compensate: cancel). If step 3 fails, compensate: refund payment, cancel reservation. Orchestrator tracks state to ensure idempotency on retry. ⭐ **Tier 2 — system design**

### Q: "Saga with choreography: how do you ensure all services receive the event?"

> Kafka (or similar) ensures durability: events are stored in topic, replayed to all consumer groups. Each service has its own consumer group; independently tracks offset. If a service is down, it reads from last offset on restart (no events lost). But: eventual consistency — payment succeeds, then service goes down before publishing compensate event; you're stuck. Mitigations: healthchecks (monitor lag), retries, manual intervention dashboard. ⭐ **Tier 2 — distributed systems**

### Q: "Your saga has 10 services. Orchestration becomes a bottleneck (coordinator is slow). What do you do?"

> Hybrid approach: split into sub-sagas. Orchestrator coordinates 3 top-level steps (each step is itself a sub-saga with 3–4 services). Use choreography within each sub-saga (services publish events, react autonomously). Decouples the 10 services into clusters of 3–4. Also: add caching to the orchestrator, use async messaging instead of sync calls, and monitor coordinator latency. ⭐ **Tier 2 — scaling**

---

## 🧾 TL;DR

> "Saga pattern coordinates multi-service transactions without ACID. Orchestration uses a central state machine; choreography uses events. Each step has a compensating action (undo). Trade-off: eventual consistency for availability. Ensure idempotency to prevent double-charging."

---

## 🔗 Related Concepts

- **`19-message-queues-kafka-rabbitmq.md`** — Kafka is the backbone for choreography-based sagas
- **`22-event-sourcing.md`** — Sagas publish events; event sourcing captures all saga steps
- **`04-idempotency.md`** — Idempotency is critical for saga compensation (prevent double refunds)
- **`20-circuit-breaker-resilience.md`** — Sagas pair with circuit breakers (fail fast on service failure, trigger compensation)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — "Saga Pattern"** (YouTube) | Deep dive on orchestration vs choreography, compensation logic, failure scenarios | ~20 min |
| **Chris Richardson — "Pattern: Saga"** (microservices.io) | Canonical reference on saga pattern, trade-offs, implementation patterns | ~25 min read |
| **ByteByteGo — "Distributed Transactions"** (YouTube) | Visual walkthrough of saga vs 2PC, when to use each | ~10 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 23. Covered orchestration vs choreography, compensating transactions, idempotency, eventual consistency. |
