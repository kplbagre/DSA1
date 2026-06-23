# State Pattern

> **Standard followed:** `LLD/notes-standards.md`
>
> **Why this file exists:** DocuSign R2 (Product Architecture) — the Subscription Billing API has an explicit state machine: PENDING → ACTIVE → PAST_DUE → CANCELLED, with UPGRADED and DOWNGRADED transitions. When the interviewer asks "how do you model subscription lifecycle?" the answer is State pattern. You need to name it, sketch the transitions, and show why `if-else` chains break.

---

## 🎯 What Problem Does It Solve?

When an object's behaviour changes depending on its current state — and that object can move through many states — you end up with `if (status == ACTIVE) { ... } else if (status == PAST_DUE) { ... }` scattered across every method. Every new state means auditing every method for a new else-if branch. State pattern encapsulates each state as its own class, where each class implements only the behaviour valid for that state. Invalid transitions throw exceptions at the right layer, not scattered across methods.

---

## 🧠 Mental Model

Think of a **traffic light**. When it's RED, it allows pedestrians to cross and blocks cars. When it's GREEN, it allows cars and blocks pedestrians. When it's YELLOW, it warns both. The light doesn't check "what colour am I currently?" before deciding — each state knows what to do. Swapping a RED state for a FLASHING-RED state only requires adding one new state class; the traffic light controller doesn't change.

In code: `SubscriptionContext` is the traffic light controller. `PendingState`, `ActiveState`, `PastDueState`, `CancelledState` are the colour states. `subscription.charge()` behaves completely differently depending on which state object is currently held — no if-else in sight.

**The key insight:** Instead of checking state in every method, you replace the state object itself. The method calls stay the same; the behaviour changes.

---

## 🔌 The Interface Contract

```java
// The state interface — every concrete state implements this
public interface SubscriptionState {

    // Attempt payment. Active → stay Active (success) or → PastDue (failure).
    void charge(SubscriptionContext context);

    // User cancels. Valid from Active and PastDue. No-op or throws from Cancelled.
    void cancel(SubscriptionContext context);

    // Admin reactivates after payment recovered. Only valid from PastDue.
    void reactivate(SubscriptionContext context);

    // Returns the state label — used in API responses and audit logs
    SubscriptionStatus getStatus();
}
```

---

## ⚙️ Implementation

**Steps in plain English:**

1. **Define the state interface** — all transitions as methods, plus a `getStatus()` getter.
2. **Write each concrete state** — each implements only the transitions valid for that state; invalid transitions throw `IllegalStateException`.
3. **Write the context class** — holds a reference to the current state; delegates all method calls to it.
4. **States swap the context's state** — when a transition fires, the state calls `context.setState(new NextState())`.
5. **Callers only talk to the context** — they never instantiate states directly.

```java
// Step 2 — PENDING state: only valid transition is → ACTIVE on payment
public class PendingState implements SubscriptionState {

    @Override
    public void charge(SubscriptionContext context) {
        // payment succeeded — activate the subscription
        context.setState(new ActiveState());
    }

    @Override
    public void cancel(SubscriptionContext context) {
        throw new IllegalStateException("Cannot cancel a pending subscription — it was never activated.");
    }

    @Override
    public void reactivate(SubscriptionContext context) {
        throw new IllegalStateException("Cannot reactivate a pending subscription.");
    }

    @Override
    public SubscriptionStatus getStatus() {
        return SubscriptionStatus.PENDING;
    }
}
```

```java
// Step 2 — ACTIVE state: charge succeeds (stay), charge fails → PAST_DUE, cancel → CANCELLED
public class ActiveState implements SubscriptionState {

    @Override
    public void charge(SubscriptionContext context) {
        boolean paymentSucceeded = context.getPaymentGateway().charge(
            context.getAmountCents(),
            context.getPaymentToken()
        );
        if (!paymentSucceeded) {
            // payment failed — move to past-due; features remain, but dunning starts
            context.setState(new PastDueState());
        }
        // on success: stay in ActiveState — no state change needed
    }

    @Override
    public void cancel(SubscriptionContext context) {
        context.setState(new CancelledState());
    }

    @Override
    public void reactivate(SubscriptionContext context) {
        throw new IllegalStateException("Subscription is already active.");
    }

    @Override
    public SubscriptionStatus getStatus() {
        return SubscriptionStatus.ACTIVE;
    }
}
```

```java
// Step 2 — PAST_DUE state: retry charge → ACTIVE (recovery), cancel → CANCELLED
public class PastDueState implements SubscriptionState {

    @Override
    public void charge(SubscriptionContext context) {
        boolean paymentSucceeded = context.getPaymentGateway().charge(
            context.getAmountCents(),
            context.getPaymentToken()
        );
        if (paymentSucceeded) {
            // dunning succeeded — back to active
            context.setState(new ActiveState());
        }
        // on failure: stay in PastDueState; dunning job will retry
    }

    @Override
    public void cancel(SubscriptionContext context) {
        context.setState(new CancelledState());
    }

    @Override
    public void reactivate(SubscriptionContext context) {
        // admin manually reactivates — bypasses payment retry
        context.setState(new ActiveState());
    }

    @Override
    public SubscriptionStatus getStatus() {
        return SubscriptionStatus.PAST_DUE;
    }
}
```

```java
// Step 2 — CANCELLED state: terminal — no transitions out
public class CancelledState implements SubscriptionState {

    @Override
    public void charge(SubscriptionContext context) {
        throw new IllegalStateException("Cannot charge a cancelled subscription.");
    }

    @Override
    public void cancel(SubscriptionContext context) {
        throw new IllegalStateException("Subscription is already cancelled.");
    }

    @Override
    public void reactivate(SubscriptionContext context) {
        throw new IllegalStateException("Cannot reactivate a cancelled subscription — create a new one.");
    }

    @Override
    public SubscriptionStatus getStatus() {
        return SubscriptionStatus.CANCELLED;
    }
}
```

```java
// Step 3 — the context: holds current state, delegates all calls to it
public class SubscriptionContext {

    private SubscriptionState currentState;
    private final PaymentGateway paymentGateway;
    private final String paymentToken;
    private final long amountCents;

    public SubscriptionContext(PaymentGateway paymentGateway, String paymentToken, long amountCents) {
        this.paymentGateway = paymentGateway;
        this.paymentToken = paymentToken;
        this.amountCents = amountCents;
        // Step 5 — starts in PENDING
        this.currentState = new PendingState();
    }

    // Step 4 — states call this to transition
    public void setState(SubscriptionState newState) {
        this.currentState = newState;
    }

    // Callers only ever call these three — no if-else, no status checks
    public void charge() {
        currentState.charge(this);
    }

    public void cancel() {
        currentState.cancel(this);
    }

    public void reactivate() {
        currentState.reactivate(this);
    }

    public SubscriptionStatus getStatus() {
        return currentState.getStatus();
    }

    public PaymentGateway getPaymentGateway() { return paymentGateway; }
    public String getPaymentToken() { return paymentToken; }
    public long getAmountCents() { return amountCents; }
}
```

```java
// Usage — callers never see state classes
SubscriptionContext subscription = new SubscriptionContext(gateway, token, 9900L);
subscription.charge();   // PENDING → ACTIVE (payment success)
subscription.charge();   // stays ACTIVE (payment success) OR → PAST_DUE (failure)
subscription.cancel();   // ACTIVE/PAST_DUE → CANCELLED
```

---

### 🎨 Visual — Subscription State Machine

```
                     charge() success
                    ┌─────────────────────────────────────────┐
                    │                                         │
    ┌─────────┐   charge()   ┌──────────┐   charge() fail   ┌──────────┐
    │ PENDING │ ──────────▶  │  ACTIVE  │ ─────────────────▶ │ PAST_DUE │
    └─────────┘   success    └──────────┘                    └──────────┘
                                  │                              │    │
                          cancel()│                      cancel()│    │charge()
                                  ▼                              │    │success
                           ┌───────────┐  ◀─────────────────────┘    │
                           │ CANCELLED │                              │
                           └───────────┘               ┌─────────────┘
                              (terminal)               ▼
                                                  ┌──────────┐
                                            back to│  ACTIVE  │
                                                   └──────────┘

KEY INVARIANT:
   Each state class only contains logic valid for THAT state.
   Invalid transitions throw IllegalStateException — no if-else scattered across callers.
   Terminal states (CANCELLED) throw on ALL transitions.
```

---

## 🏢 Real World Usage

- **DocuSign / Stripe Billing** — subscription lifecycle is a textbook State machine. Stripe's Subscription object has states: `trialing`, `active`, `past_due`, `canceled`, `unpaid`. Each state has specific allowed API calls; others return 4xx errors. The `past_due` dunning retry schedule (3, 5, 7, 14 days) is the `PastDueState` deciding when to retry `charge()`.
- **Order management (Flipkart, Amazon)** — `Order` moves through `PLACED → CONFIRMED → PACKED → SHIPPED → OUT_FOR_DELIVERY → DELIVERED` (or `CANCELLED`, `RETURNED`). Each state allows different operations: cancel is only valid before `SHIPPED`; return is only valid after `DELIVERED`. State pattern ensures a `DELIVERED` order can't be `CANCELLED`.
- **Vending Machine** — `IDLE → WAITING_FOR_PAYMENT → PAYMENT_RECEIVED → DISPENSING → OUT_OF_STOCK`. The machine in `OUT_OF_STOCK` state ignores `insertCoin()` and throws on `selectProduct()` — not because of an if-else in a central method, but because `OutOfStockState.selectProduct()` throws by design.
- **TCP Connection** — the classic State pattern textbook example. A TCP socket moves through `CLOSED → LISTEN → SYN_RECEIVED → ESTABLISHED → FIN_WAIT → TIME_WAIT → CLOSED`. Calling `send()` from `CLOSED` state is illegal — the state handles it, not a global status check.

---

## 🧭 When to Use vs When NOT to Use

| Use State when | Do NOT use when |
|---|---|
| An object has multiple states and behaviour varies per state | The object has only two states (a boolean flag is simpler) |
| Transitions have rules — some are invalid from certain states | All states allow all operations (no invalid transitions) |
| You're adding new states regularly | The state never changes after construction |
| Invalid transitions should be rejected at the right layer | The "state" is just a display label with no behaviour difference |

**The common mistake:** Modeling state with an enum + a giant `switch` in every method. That's the violation pattern State solves. If you have a `switch(status)` in `charge()`, `cancel()`, `reactivate()`, AND `getDisplayText()`, you have 4 places to update every time you add a state. State pattern = one class per state, zero switch statements.

---

## 🧩 LLD Problems That Use State Pattern

- **Subscription Billing API (DocuSign R2)** — `PENDING → ACTIVE → PAST_DUE → CANCELLED`. `SubscriptionContext` delegates `charge()` and `cancel()` to the current state object. Adding a `SUSPENDED` state (for manual admin holds) = one new `SuspendedState` class. Zero changes to existing states or the context.
- **Vending Machine** — `IdleState`, `WaitingForPaymentState`, `PaymentReceivedState`, `DispensingState`, `OutOfStockState`. `insertCoin()` only makes sense from `IdleState`; `dispense()` only from `PaymentReceivedState`. Invalid calls throw instead of silently doing nothing.
- **ATM Machine** — `NoCardState`, `HasCardState`, `HasPinState`, `NoCashState`. Calling `ejectCard()` from `NoCardState` throws. Calling `enterPin()` before inserting a card throws. Each state strictly enforces its valid inputs.
- **Elevator System** — `IdleState`, `MovingUpState`, `MovingDownState`, `DoorOpenState`. When doors are open, `move()` throws. When moving, `openDoor()` throws. State ensures the elevator can't move with doors open — at the logic layer, not via sensor checks scattered across the controller.
- **Traffic Light** — `RedState`, `GreenState`, `YellowState`. `allowCars()` and `allowPedestrians()` behave oppositely per state. A `FlashingRedState` (for late-night intersections) can be added without touching existing states.
- **Order Management (BookMyShow / E-commerce)** — `PlacedState`, `ConfirmedState`, `ShippedState`, `DeliveredState`, `CancelledState`, `ReturnedState`. `cancel()` throws from `ShippedState` onwards. `return()` only works from `DeliveredState`. Business rules enforced at the state layer.
- **Document Workflow (DocuSign itself)** — `DraftState`, `SentState`, `ViewedState`, `SignedState`, `CompletedState`, `DeclinedState`, `VoidedState`. Calling `sign()` from `DraftState` (before sending) throws. The document's legal state machine maps directly to this pattern.

---

## 🔬 Interview Q&As

### Q: "What is the State pattern and why not just use an enum with switch statements?"
> State encapsulates each distinct behaviour mode as a class implementing a shared interface. With an enum + switch, adding a new state means updating every switch block in every method — you open existing, tested code. With State, you add one new class; all other states are untouched. The larger the state machine, the more the switch approach scales badly. State also makes invalid transitions explicit — `CancelledState.charge()` throws by design; you can't forget to add the check.

### Q: "How does the State pattern relate to the subscription lifecycle in a billing system?"
> Subscriptions are a canonical State machine. `PENDING` allows payment to activate; `ACTIVE` allows charge (stays active on success, goes past-due on failure); `PAST_DUE` allows retry charge (recovers to active) or cancel; `CANCELLED` is terminal. Without State, every billing method has `if (status == ACTIVE) { ... } else if (status == PAST_DUE) { ... }` — you're one missed else-if away from charging a cancelled subscription. With State, `CancelledState.charge()` throws immediately — the bug can't happen.

### Q: "How do you add a new state to an existing State machine?"
> You write one new class implementing the state interface. You update only the states from which the new state is reachable — their transition method calls `context.setState(new NewState())`. All other states and the context class are untouched. For example: adding `SUSPENDED` to the subscription machine only requires (1) `SuspendedState` class and (2) updating `ActiveState.suspend()` to transition into it. Zero changes to `PastDueState`, `CancelledState`, or `SubscriptionContext`.

### Q: "What's the difference between State and Strategy? They look similar structurally."
> Both use an interface with multiple concrete implementations injected into a context. The difference is intent and ownership of transitions. **Strategy** is chosen once by the caller — the context doesn't change its strategy; the caller swaps it (`new ParkingLot(new HourlyFeeStrategy())`). **State** transitions itself — the state object calls `context.setState(new NextState())` based on what happens. The context in State is an active participant in its own transition; in Strategy, it's passive. Another way: Strategy is about *how* to do something. State is about *what's valid to do right now*.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"State pattern models the subscription lifecycle: PendingState, ActiveState, PastDueState, CancelledState each implement charge() and cancel() with the behaviour valid for that state. CancelledState.charge() throws by design. Adding a Suspended state means one new class — no existing code changes. Without State, every billing method has a switch on status that grows every sprint."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — State pattern anchored on Subscription billing state machine. |
