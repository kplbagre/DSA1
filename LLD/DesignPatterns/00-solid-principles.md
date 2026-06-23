# SOLID Principles

> **Standard followed:** `LLD/notes-standards.md`
>
> **Why this file exists:** DocuSign's official interview guide explicitly lists "SOLID principles" as a key area for the Product Architecture round. You apply SOLID in your billing design — you need to explain all 5 cold, with concrete examples, under drill-down.

---

## S — Single Responsibility Principle

### 🎯 What Problem Does It Solve?

When one class handles too many concerns — billing logic, sending emails, updating entitlements, logging — any change to any one concern risks breaking the others. A test for billing now accidentally tests email. SRP says: **one class, one reason to change.**

### 🧠 Mental Model

Think of a **restaurant kitchen**. The chef cooks. The waiter serves. The cashier charges. If the chef also processed payments and served tables, you'd retrain everyone every time the payment terminal changed. Separate jobs = separate people. In code: separate concerns = separate classes.

### ⚙️ Code Example — Violation vs Fix

```java
// ❌ Violation — BillingService does too much
public class BillingService {

    public void processPayment(Payment payment) {
        // 1. charging logic
        // 2. send confirmation email  ← should not be here
        // 3. update entitlements      ← should not be here
        // 4. log to audit table       ← should not be here
    }
}
```

```java
// ✅ Fix — one class, one job
public class BillingService {
    public void processPayment(Payment payment) {
        // charging logic only
    }
}

public class NotificationService {
    public void sendConfirmation(Payment payment) { /* email only */ }
}

public class EntitlementService {
    public void unlockFeatures(Customer customer, Plan plan) { /* features only */ }
}
```

### 🧭 Violation Signal

When you find yourself adding `if (type == X) sendEmail()` inside a class whose name has nothing to do with email — SRP is being violated.

---

## O — Open/Closed Principle

### 🎯 What Problem Does It Solve?

Adding a new payment provider (Stripe → Braintree) shouldn't mean editing `PaymentService`. The moment you open existing code to add a new feature, you risk breaking features that already work. OCP says: **open for extension (add new class), closed for modification (don't touch existing code).**

### 🧠 Mental Model

Think of a **power strip**. You don't rewire the strip to add a new device — you plug into an existing socket. The strip (your class) doesn't change. The new device (new implementation) is the extension. Strategy pattern is OCP in action.

### ⚙️ Code Example — Violation vs Fix

```java
// ❌ Violation — adding Braintree means modifying this class
public class PaymentService {

    public void charge(Payment payment, String provider) {
        if (provider.equals("stripe")) {
            // stripe logic
        } else if (provider.equals("braintree")) {
            // braintree logic — you opened existing code to add this
        }
    }
}
```

```java
// ✅ Fix — new provider = new class, zero changes to PaymentService
public interface PaymentProvider {
    void charge(Payment payment);
}

public class StripeProvider implements PaymentProvider {
    @Override
    public void charge(Payment payment) { /* stripe logic */ }
}

public class BraintreeProvider implements PaymentProvider {
    @Override
    public void charge(Payment payment) { /* braintree logic */ }
}

public class PaymentService {

    private final PaymentProvider provider;

    public PaymentService(PaymentProvider provider) {
        this.provider = provider;
    }

    public void charge(Payment payment) {
        // this method never changes, regardless of how many providers are added
        provider.charge(payment);
    }
}
```

### 🧭 Violation Signal

Whenever you find yourself adding an `else if` to an existing method to handle a new "type" — OCP is being violated.

---

## L — Liskov Substitution Principle

### 🎯 What Problem Does It Solve?

If class `B` extends class `A`, anywhere you use an `A` you should be able to drop in a `B` without the program breaking. LSP is violated when a subclass throws exceptions the parent doesn't, ignores methods it inherited but doesn't want, or returns values that break the parent's contract.

### 🧠 Mental Model

Think of a **USB standard**. Any USB device should work in any USB port. If a specific USB device requires you to rewrite the port, it's not a proper USB device — it's breaking the contract. Same in code: if a subclass requires the caller to know it's a subclass (to avoid calling certain methods), LSP is broken.

### ⚙️ Code Example — Violation vs Fix

```java
// ❌ Violation — FreeTrialSubscription breaks the payment contract
public class Subscription {
    public void chargePayment() {
        // charge the card
    }
}

public class FreeTrialSubscription extends Subscription {
    @Override
    public void chargePayment() {
        // Free trial — no charging allowed
        throw new UnsupportedOperationException("Cannot charge a free trial!");
        // Anywhere Subscription is expected, FreeTrialSubscription breaks the caller
    }
}
```

```java
// ✅ Fix — restructure the hierarchy so the contract holds
public interface Subscription {
    SubscriptionStatus getStatus();
    Plan getPlan();
}

public interface ChargeableSubscription extends Subscription {
    void chargePayment();
}

// FreeTrialSubscription implements Subscription but NOT ChargeableSubscription
// Callers that need to charge use ChargeableSubscription — no surprise exceptions
public class FreeTrialSubscription implements Subscription {
    @Override
    public SubscriptionStatus getStatus() { return SubscriptionStatus.TRIAL; }

    @Override
    public Plan getPlan() { return Plan.TRIAL; }
}
```

### 🧭 Violation Signal

A subclass overrides a method to throw `UnsupportedOperationException` — almost always an LSP violation. The fix is usually to split the parent into a leaner interface that only promises what all subtypes can deliver.

---

## I — Interface Segregation Principle

### 🎯 What Problem Does It Solve?

A fat interface (10 methods) forces every implementer to stub out methods it doesn't need. If `BillingService` implements an interface that includes `sendSMS()` and `sendPushNotification()` just because those also touch subscriptions, it has to provide empty implementations for things that are not its job. ISP says: **many small, focused interfaces are better than one large, general one.**

### 🧠 Mental Model

Think of a **TV remote**. The remote has buttons for the TV, the cable box, and the streaming device — all on one device. When you want to turn up the TV volume, you shouldn't have to navigate streaming menus that don't apply. ISP gives each concern its own "remote" — its own interface with only the methods relevant to that caller.

### ⚙️ Code Example — Violation vs Fix

```java
// ❌ Violation — forces all implementers to deal with notification methods
public interface SubscriptionManager {
    void createSubscription(Customer customer, Plan plan);
    void cancelSubscription(String subscriptionId);
    void sendRenewalEmail(String subscriptionId);     // ← notification concern
    void sendPaymentFailureSMS(String subscriptionId); // ← notification concern
}

// BillingService is forced to implement SMS/email even though it only does billing
public class BillingService implements SubscriptionManager {
    @Override
    public void sendRenewalEmail(String subscriptionId) {
        // empty stub — BillingService has nothing to do with email
    }
}
```

```java
// ✅ Fix — split into focused interfaces
public interface SubscriptionLifecycle {
    void createSubscription(Customer customer, Plan plan);
    void cancelSubscription(String subscriptionId);
}

public interface SubscriptionNotifier {
    void sendRenewalEmail(String subscriptionId);
    void sendPaymentFailureSMS(String subscriptionId);
}

// BillingService only implements what it actually does
public class BillingService implements SubscriptionLifecycle {
    @Override
    public void createSubscription(Customer customer, Plan plan) { /* billing logic */ }

    @Override
    public void cancelSubscription(String subscriptionId) { /* billing logic */ }
}
```

### 🧭 Violation Signal

Empty method bodies or `throw new UnsupportedOperationException()` in implementations — the interface is forcing something the implementer can't do. Split the interface.

---

## D — Dependency Inversion Principle

### 🎯 What Problem Does It Solve?

When `BillingService` directly instantiates `StripeClient`, it's hardwired to Stripe. Testing requires a live Stripe connection. Swapping to Braintree requires modifying `BillingService`. DIP says: **high-level modules depend on abstractions, not on low-level concretions.**

### 🧠 Mental Model

Think of a **wall socket standard**. Your appliance plugs into a standard 220V/50Hz interface — not directly into the power plant. The appliance (high-level) doesn't care whether the plant burns coal, solar, or nuclear (concretions). The standard interface is the abstraction. You can swap the power plant without rewiring your appliances.

### ⚙️ Code Example — Violation vs Fix

```java
// ❌ Violation — BillingService is hardwired to Stripe
public class BillingService {

    // BillingService creates the concrete dependency itself — cannot swap, cannot test
    private final StripeClient stripeClient = new StripeClient();

    public void charge(Payment payment) {
        stripeClient.charge(payment.getAmount(), payment.getToken());
    }
}
```

```java
// ✅ Fix — depend on the abstraction, inject the concretion
public interface PaymentGateway {
    void charge(long amountCents, String token);
}

public class StripeGateway implements PaymentGateway {
    @Override
    public void charge(long amountCents, String token) { /* stripe logic */ }
}

public class BillingService {

    // depends on the interface — not on Stripe
    private final PaymentGateway gateway;

    public BillingService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public void charge(Payment payment) {
        gateway.charge(payment.getAmountCents(), payment.getToken());
    }
}

// In tests: inject a MockGateway — no Stripe calls
// In prod: inject StripeGateway via Spring @Bean
```

### 🧭 Violation Signal

`new ConcreteClass()` inside a service class. If the high-level class is constructing its own low-level dependencies, DIP is being violated. Fix: inject via constructor.

---

## 🧩 Where SOLID Shows Up in DocuSign R2 Designs

This is your primary anchor when the interviewer says "walk me through how you applied SOLID to your billing API design."

| Principle | Where it appears in the Subscription Billing API |
|---|---|
| **S — Single Responsibility** | `BillingService` only charges. `EntitlementService` only unlocks features. `NotificationService` only sends emails. Each class has one reason to change. |
| **O — Open/Closed** | New payment providers (Braintree, Adyen) are added as new `PaymentGateway` implementations. `BillingService.charge()` never changes — open for extension, closed for modification. |
| **L — Liskov Substitution** | `CreditCardPayment` and `BankTransferPayment` both implement `PaymentProvider`. Either can be injected into `BillingService` and the call site works identically. |
| **I — Interface Segregation** | `SubscriptionLifecycle` (create, cancel) is separate from `SubscriptionNotifier` (email, SMS). `BillingService` only depends on the lifecycle interface — not forced to implement notification methods. |
| **D — Dependency Inversion** | `BillingService` depends on `PaymentGateway` interface (abstraction). Stripe SDK is injected — never `new StripeClient()` inside `BillingService`. Spring `@Bean` wires the concretion. |

---

## 🏢 Real World Usage

- **Spring Framework** — DIP is the entire foundation. Spring's IoC container (the thing that manages beans and injects them) exists to enforce D — your services depend on interfaces, Spring wires the concretions. You see this every time you `@Autowired` an interface.
- **Java Collections API** — ISP and OCP. `List`, `Set`, `Queue` are separate interfaces (ISP). `ArrayList` and `LinkedList` extend the framework without modifying it (OCP). `Comparator` is a Strategy used in sort — a direct OCP application.
- **Stripe / Braintree SDKs** — Real commerce backends wrap these in a `PaymentGateway` interface (D). Every DocuSign commerce engineer who's done this correctly has applied DIP without naming it.
- **Apache Kafka consumer model** — SRP: each consumer service has one job. The `payment.succeeded` topic has three consumers: `EntitlementConsumer`, `InvoiceConsumer`, `NotificationConsumer`. If they were one class, every payment event handler would violate SRP.

---

## 🔬 Interview Q&As

### Q: "Can you walk me through all 5 SOLID principles?"
> **S** — One class, one reason to change. BillingService charges, NotificationService emails — not one class doing both.
> **O** — New payment providers added as new classes, not by editing PaymentService.
> **L** — Subclasses must honour their parent's contract. FreeTrialSubscription shouldn't throw UnsupportedOperationException where a regular Subscription is expected.
> **I** — Many focused interfaces over one fat one. SubscriptionLifecycle ≠ SubscriptionNotifier.
> **D** — Depend on PaymentGateway interface, not StripeClient concretion. Inject via constructor.

### Q: "Which SOLID principle is most commonly violated in large Java codebases?"
> SRP — the "God class." Over time, `OrderService` ends up with 50 methods handling inventory, pricing, fraud, notifications, and fulfillment. Every sprint touches it. Every PR has merge conflicts. The fix is extracting focused services, but by then the tech debt is significant.

### Q: "What's the difference between OCP and DIP? They both sound like they're about not depending on concretions."
> OCP is about evolution — don't modify existing working code to add new behaviour; extend it. DIP is about wiring — your class shouldn't reach out and grab its own dependencies; they should be handed to it. You can satisfy OCP without DIP (e.g., using factory methods internally). DIP usually enables OCP by making the concrete type injectable and therefore swappable.

### Q: "How does SOLID relate to testability?"
> DIP is the direct enabler. When `BillingService` depends on `PaymentGateway` (interface) injected via constructor, tests inject `MockGateway` — no network, no Stripe. Without DIP, every test touching BillingService makes a live payment call. ISP also helps — small interfaces mean small mocks; fat interfaces mean mocks with 10 empty methods.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"SOLID gives me five checks before I sign off on any class: does it have one job, can I extend it without editing it, do subclasses honor their contracts, are its interfaces lean, and does it receive its dependencies rather than creating them? In the billing API, SOLID is why BillingService never contains email code, why adding Braintree doesn't touch existing charge logic, and why every test runs without a live Stripe connection."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — Product Architecture round explicitly tests SOLID. |
