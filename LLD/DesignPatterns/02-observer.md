# Observer Pattern

> **Standard followed:** `LLD/notes-standards.md`
>
> **Why this file exists:** DocuSign R2 (Product Architecture) requires event-driven design. The Subscription Billing API uses Kafka fanout — `payment.succeeded` triggers four independent consumers. That IS Observer. You need to name the pattern, explain it, and draw the connection live in an interview.

---

## 🎯 What Problem Does It Solve?

When one event (payment succeeds) needs to trigger multiple independent reactions (unlock features, generate invoice, send email, log analytics) — hardwiring all those reactions into the payment handler creates a God class. Every new reaction means editing the payment code. Observer (also called Publish-Subscribe) decouples the event source from the event handlers: the source just says "something happened"; each handler decides what to do about it.

---

## 🧠 Mental Model

Think of a **newspaper subscription**. The newspaper (publisher) prints one edition. Every subscriber (observer) gets their copy. The newspaper doesn't know what each subscriber does with it — one reads the sports section, another clips coupons. Adding a new subscriber doesn't change how the newspaper is printed.

In code: `PaymentService` is the newspaper. `EntitlementConsumer`, `InvoiceConsumer`, `NotificationConsumer`, `AnalyticsConsumer` are the subscribers. When payment succeeds, `PaymentService` calls `notify()` — it never imports or calls `EntitlementService` directly.

**The key insight:** The publisher and the subscribers are decoupled at compile time. Neither knows the other's internals. You can add, remove, or replace subscribers without touching the publisher.

---

## 🔌 The Interface Contract

```java
// The subscriber interface — what every observer must implement
public interface PaymentEventListener {

    // Called by the publisher when payment.succeeded fires
    // event carries everything listeners need — amount, customerId, planId
    void onPaymentSucceeded(PaymentSucceededEvent event);
}
```

```java
// The publisher interface — what the event source exposes for wiring
public interface PaymentEventPublisher {

    void subscribe(PaymentEventListener listener);

    void unsubscribe(PaymentEventListener listener);

    // Fires onPaymentSucceeded on every registered listener
    void notifyListeners(PaymentSucceededEvent event);
}
```

---

## ⚙️ Implementation

**Steps in plain English:**

1. **Define the event object** — a value class carrying everything listeners need (immutable, no behaviour).
2. **Define the subscriber interface** — one method, receives the event.
3. **Write each concrete subscriber** — each handles its own concern, no cross-talk.
4. **Write the publisher** — maintains a list of subscribers, fires all of them on event.
5. **Wire at startup** — call `subscribe()` for each listener; the publisher never imports concrete listener types.

```java
// Step 1 — the event: immutable value object, no business logic
public class PaymentSucceededEvent {

    private final String paymentId;
    private final String customerId;
    private final String planId;
    private final long amountCents;
    private final Instant occurredAt;

    public PaymentSucceededEvent(
            String paymentId,
            String customerId,
            String planId,
            long amountCents,
            Instant occurredAt) {
        this.paymentId = paymentId;
        this.customerId = customerId;
        this.planId = planId;
        this.amountCents = amountCents;
        this.occurredAt = occurredAt;
    }

    public String getPaymentId() { return paymentId; }
    public String getCustomerId() { return customerId; }
    public String getPlanId() { return planId; }
    public long getAmountCents() { return amountCents; }
    public Instant getOccurredAt() { return occurredAt; }
}
```

```java
// Step 3 — four concrete subscribers, each with exactly one job

public class EntitlementConsumer implements PaymentEventListener {

    @Override
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        // unlock features for customer on this plan
    }
}

public class InvoiceConsumer implements PaymentEventListener {

    @Override
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        // generate and store invoice record
    }
}

public class NotificationConsumer implements PaymentEventListener {

    @Override
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        // send payment confirmation email
    }
}

public class AnalyticsConsumer implements PaymentEventListener {

    @Override
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        // emit metric to analytics pipeline
    }
}
```

```java
// Step 4 — the publisher: knows nothing about what each listener does
public class PaymentService implements PaymentEventPublisher {

    private final List<PaymentEventListener> listeners = new CopyOnWriteArrayList<>();

    // Step 2 — register
    @Override
    public void subscribe(PaymentEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(PaymentEventListener listener) {
        listeners.remove(listener);
    }

    // Step 4 — fire all listeners after payment is confirmed
    @Override
    public void notifyListeners(PaymentSucceededEvent event) {
        for (PaymentEventListener listener : listeners) {
            listener.onPaymentSucceeded(event);
        }
    }

    public void processPayment(Payment payment) {
        // charge the card (Stripe/Braintree via PaymentGateway interface)
        // persist payment record
        // ...
        // publish the event — SRP: PaymentService doesn't do entitlement or email
        notifyListeners(new PaymentSucceededEvent(
            payment.getId(),
            payment.getCustomerId(),
            payment.getPlanId(),
            payment.getAmountCents(),
            Instant.now()
        ));
    }
}
```

```java
// Step 5 — wiring at startup (Spring @Configuration or main method)
PaymentService paymentService = new PaymentService(gateway);
paymentService.subscribe(new EntitlementConsumer());
paymentService.subscribe(new InvoiceConsumer());
paymentService.subscribe(new NotificationConsumer());
paymentService.subscribe(new AnalyticsConsumer());
// Adding a new consumer = one new class + one subscribe() call — nothing else changes
```

---

## 🏢 Real World Usage

- **Apache Kafka** — the canonical Observer implementation at scale. Producers publish to a topic; any number of consumer groups subscribe independently. `payment.succeeded` is the event; `EntitlementService`, `InvoiceService`, `NotificationService` are separate consumer groups — each processes at its own pace, each has its own offset. DocuSign's commerce backend almost certainly uses Kafka for this exact fanout.
- **Java Swing / AWT** — `button.addActionListener(listener)` is Observer. The button (publisher) calls `actionPerformed()` on every registered `ActionListener`. UI code from the 1990s; the pattern hasn't changed.
- **Spring `ApplicationEventPublisher`** — Spring's built-in Observer. `@EventListener` on any Spring bean method registers it as an observer. `applicationEventPublisher.publishEvent(new PaymentEvent(...))` fans out to all listeners automatically — Spring manages the list.
- **Android `LiveData` / `Flow`** — UI components observe data streams from ViewModel. The ViewModel publishes state changes; Activity/Fragment react. No direct coupling between ViewModel and UI.

---

## 🧭 When to Use vs When NOT to Use

| Use Observer when | Do NOT use when |
|---|---|
| One event needs to trigger multiple independent reactions | There's only one consumer — direct call is simpler |
| Publisher and consumers should be independently deployable | Consumers need a guaranteed order of execution |
| Adding a new consumer should not modify the publisher | You need the publisher to know if a consumer failed |
| Reactions are cross-cutting concerns (email, analytics, billing) | The "fanout" is actually just two methods in the same class |

**The common mistake:** Using Observer when you need synchronous, ordered execution where the publisher must know if step 2 failed before running step 3. Observer doesn't guarantee order or failure propagation. For that, use a chain of responsibility or a transaction script.

---

## 🧩 LLD Problems That Use Observer Pattern

- **Subscription Billing API (DocuSign R2)** — `payment.succeeded` event fans out to `EntitlementConsumer` (unlock features), `InvoiceConsumer` (generate invoice), `NotificationConsumer` (send email), `AnalyticsConsumer` (emit metric). Publisher (`PaymentService`) never imports any consumer — each is wired via `subscribe()`.
- **Parking Lot** — `ParkingLot` publishes `SpotAvailableEvent` when a car exits. `WaitlistConsumer` (notify queued drivers), `DisplayConsumer` (update floor display count), `AnalyticsConsumer` (track occupancy rate) all subscribe. The parking lot doesn't know how many consumers exist.
- **Stock Exchange / Order Book** — Trade executed → `TradeExecutedEvent` fans out to `PortfolioConsumer` (update holdings), `SettlementConsumer` (schedule T+2 settlement), `MarketDataConsumer` (update price feed), `AuditConsumer` (log the trade).
- **Ride Sharing (Cab Booking)** — `RideCompletedEvent` fans out to `PaymentConsumer` (charge rider), `DriverRatingConsumer` (prompt rating), `DriverEarningsConsumer` (credit driver wallet), `SurgeRecalculationConsumer` (re-check demand in the zone).
- **BookMyShow / Ticket Booking** — `BookingConfirmedEvent` fans out to `EmailConsumer` (confirmation email), `SeatLockConsumer` (release held seats for other shows), `RevenueConsumer` (update gross), `RecommendationConsumer` (update user preference model).
- **Logger System** — `LogEvent` is published by the logger core; `FileAppenderObserver`, `ConsoleAppenderObserver`, `CloudWatchObserver` all subscribe. Adding a new log destination = one new observer class.
- **E-commerce Order System** — `OrderPlacedEvent` fans out to `InventoryConsumer` (decrement stock), `FraudConsumer` (run fraud check), `FulfillmentConsumer` (create pick-pack-ship job), `LoyaltyConsumer` (award points).

---

## 🔬 Interview Q&As

### Q: "What is the Observer pattern and when would you use it?"
> Observer decouples a publisher from the set of objects that react to its events. The publisher maintains a list of subscriber interfaces and calls a notification method on each when the event fires. Use it when one event needs to trigger multiple independent reactions and you want to add/remove consumers without modifying the publisher. The canonical real-world example: Kafka topic with multiple consumer groups.

### Q: "How does Observer relate to Kafka in a production system?"
> Kafka IS Observer at infrastructure scale. The producer (publisher) writes an event to a topic. Multiple consumer groups (observers) each read from the topic independently, at their own pace, with their own offset pointer. In-process Observer is the same pattern but synchronous — the publisher calls each listener in a loop. Kafka adds durability, replay, and independent failure domains. For a billing fanout like `payment.succeeded`, Kafka is preferred over in-process Observer because a consumer crash (e.g., `EntitlementService` down) doesn't lose the event — it retries from offset.

### Q: "How is Observer different from the Mediator pattern?"
> In Observer, subscribers know they're subscribing — they call `publisher.subscribe(this)`. The publisher is aware it has listeners; it notifies them. In Mediator, components communicate through a central hub that orchestrates the interaction — neither component knows about the other. Observer is for broadcast ("I fired an event; whoever cares, handle it"). Mediator is for workflows ("Component A finishes; Mediator decides what happens next").

### Q: "What thread-safety consideration does Observer have in a multi-threaded system?"
> The listeners list is shared state. If threads can call `subscribe()` and `unsubscribe()` concurrently with `notifyListeners()`, you get `ConcurrentModificationException` on a plain `ArrayList`. Fix: use `CopyOnWriteArrayList` (reads never block; writes copy the list — safe for low-write-frequency scenarios) or synchronize the `notify` loop. For Kafka consumers, this is moot — Kafka manages concurrency via partition assignment.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"I use Observer to decouple PaymentService from every downstream reaction. When payment.succeeded fires, PaymentService notifies a list of listeners — EntitlementConsumer, InvoiceConsumer, NotificationConsumer, AnalyticsConsumer. Adding a fifth consumer means writing one new class and one subscribe() call. PaymentService never changes. In production, this is Kafka; in the LLD, it's a CopyOnWriteArrayList of listeners."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — Observer pattern anchored on payment.succeeded Kafka fanout. |
