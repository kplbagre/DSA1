# 🧩 Observer + Event-Driven — Pattern

> **When to use:** One object changes state, and N other objects need to react — without the source knowing who they are or how many exist. Decouples the publisher from the subscribers.

---

## 🎯 The Problem

```java
// Tight coupling — OrderService knows about every downstream action:
public void placeOrder(Order order) {
    repository.save(order);
    emailService.sendConfirmation(order);    // OrderService depends on EmailService
    inventoryService.reserve(order);         // OrderService depends on InventoryService
    analyticsService.trackPurchase(order);   // OrderService depends on AnalyticsService
    loyaltyService.addPoints(order);         // Adding a new action = modifying OrderService
}
// OrderService has 4 dependencies for 4 side effects. Adding a 5th = another import, another call.
// OrderService shouldn't know or care about email, analytics, or loyalty.
```

---

## 🧠 Why It Exists

Observer decouples the **publisher** (the thing that changes) from **subscribers** (the things that react). The publisher emits an event. Subscribers register interest. When the event fires, all registered subscribers are notified — the publisher doesn't know who they are. Adding a new subscriber = adding a new class. The publisher doesn't change.

---

## 🔧 Implementations

### 1. Spring Application Events (production standard)

```java
// Step 1: Define the event
public record OrderPlacedEvent(Long orderId, String customerEmail, BigDecimal total) {}

// Step 2: Publish the event
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void placeOrder(Order order) {
        repository.save(order);
        // Publish event — OrderService doesn't know who listens
        eventPublisher.publishEvent(new OrderPlacedEvent(
            order.getId(), order.getCustomerEmail(), order.getTotal()
        ));
    }
}

// Step 3: Subscribe to the event (each listener is independent)
@Component
public class OrderConfirmationEmailListener {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailService.sendConfirmation(event.customerEmail(), event.orderId());
    }
}

@Component
public class InventoryReservationListener {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        inventoryService.reserve(event.orderId());
    }
}

@Component
public class LoyaltyPointsListener {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        loyaltyService.addPoints(event.customerEmail(), event.total());
    }
}

// Adding analytics = new class with @EventListener. OrderService doesn't change.
```

### 2. `@TransactionalEventListener` — fire AFTER commit

```java
// Problem with @EventListener: it fires DURING the transaction.
// If the email send fails → exception → transaction rolls back → order not saved!

// Solution: @TransactionalEventListener fires AFTER the transaction commits:
@Component
public class OrderConfirmationEmailListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Runs ONLY after the transaction that published the event commits.
        // If the transaction rolls back → this never fires.
        // If the email send fails → order is already committed (safe).
        emailService.sendConfirmation(event.customerEmail(), event.orderId());
    }
}

// Other phases:
// AFTER_ROLLBACK — fire only if the transaction rolled back
// AFTER_COMPLETION — fire regardless of commit/rollback
// BEFORE_COMMIT — fire before the transaction commits
```

### 3. Async Event Processing

```java
@Component
public class AnalyticsListener {

    @Async   // runs on a separate thread — doesn't block the publisher
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        analyticsService.trackPurchase(event);   // slow I/O — doesn't delay order response
    }
}

// ⚠️ @Async + @TransactionalEventListener combination:
// @Async makes the listener run on a new thread.
// @TransactionalEventListener(AFTER_COMMIT) fires after commit.
// Combined: fires after commit, on a separate thread — the safest pattern for side effects.
```

### 4. Classic Observer (manual — for non-Spring contexts)

```java
// Observer interface:
public interface OrderObserver {
    void onOrderPlaced(Order order);
}

// Subject (observable):
public class OrderService {
    private final List<OrderObserver> observers = new CopyOnWriteArrayList<>();

    public void addObserver(OrderObserver observer) {
        observers.add(observer);
    }

    public void placeOrder(Order order) {
        repository.save(order);
        // Notify all observers:
        for (OrderObserver observer : observers) {
            observer.onOrderPlaced(order);
        }
    }
}
// ✅ Thread-safe — CopyOnWriteArrayList for concurrent observer registration/notification
```

---

## 🏢 Where You See It

| Framework | Observer usage |
|---|---|
| **Spring** | `ApplicationEvent` + `@EventListener` / `@TransactionalEventListener` |
| **JDK** | `java.util.Observer` (deprecated Java 9 — use listeners instead), `PropertyChangeListener` |
| **Kafka** | Producer publishes to topic → N consumers subscribe. Observer at infrastructure level. |
| **DOM/UI** | `button.addEventListener("click", handler)` — classic observer in frontend |
| **Reactive** | `Flux.subscribe(observer)` — Project Reactor / RxJava are sophisticated observer implementations |

---

## ⚠️ Gotchas

- **Event ordering:** `@EventListener` methods on the SAME event execute in undefined order (unless `@Order` is specified). Don't depend on listener execution order.
- **Error propagation:** if a synchronous listener throws, it propagates to the publisher — potentially rolling back the transaction. Use `@Async` or `@TransactionalEventListener(AFTER_COMMIT)` for non-critical side effects.
- **Memory leak:** manual observer pattern without deregistration → observer objects never GC'd. Spring's `@EventListener` doesn't have this problem (lifecycle managed by the container).

---

## 🎙️ Say It in 60 Seconds

> Observer decouples the source of change from the reactors. Instead of `orderService.save(); emailService.send(); analyticsService.track()` — where OrderService depends on everything — you publish an `OrderPlacedEvent` and N listeners react independently. Adding a new reaction = adding a new `@EventListener` class. The publisher never changes. In Spring, use `@TransactionalEventListener(AFTER_COMMIT)` for side effects (email, analytics) — ensures the listener fires ONLY after the order is committed. Use `@Async` for slow side effects so they don't block the response. Kafka takes this pattern to infrastructure level — publish to a topic, N consumers subscribe across services.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #31 (Phase 5). Spring ApplicationEvent + @EventListener, @TransactionalEventListener (AFTER_COMMIT), @Async events, classic Observer (manual), Kafka as infrastructure-level observer. |
