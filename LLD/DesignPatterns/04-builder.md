# Builder Pattern

> **Standard followed:** `LLD/notes-standards.md`
>
> **Why this file exists:** BookMyShow (LLD TODO item 4, next problem) needs a `BookingRequest` with userId, showId, seatIds, paymentMethod, promoCode, and optional fields. Without Builder, that constructor is a nightmare. Splitwise's `Expense` has the same problem. This pattern shows up in every problem with a complex domain object.

---

## 🎯 What Problem Does It Solve?

When an object has 4+ constructor parameters — especially a mix of mandatory and optional ones — construction becomes unreadable and dangerous. `new Booking("u1", "s1", null, null, List.of("A1"), "GOLD", true, false)` — what do those `null`s mean? Which boolean is which? The **telescoping constructor problem** (one constructor per combination of optional fields) explodes combinatorially. Builder solves this by letting you set each field by name, in any order, and calling `build()` at the end to get an immutable object.

---

## 🧠 Mental Model

Think of **ordering a subway sandwich**. You tell the counter person step by step: "Sourdough. Double chicken. No onions. Extra jalapeños. Ranch sauce." Each instruction is a separate call. The sandwich is only assembled when you say "wrap it up." Two things to notice:

1. You can skip optional toppings — the counter person handles defaults.
2. If you try to get the sandwich before specifying the bread, you get an error at the counter — not a half-assembled mess handed to the next step.

In code: `BookingRequest.Builder` is the counter person. Each `withXxx(...)` call is one topping. `build()` validates that mandatory fields are set and returns the immutable `BookingRequest`.

---

## 🔌 The Interface Contract

Builder is typically implemented as a **static inner class**, not a separate interface, because the builder is specific to the class it builds. The contract is the fluent API:

```java
// The target: immutable after construction
public final class BookingRequest {

    // Mandatory
    private final String userId;
    private final String showId;
    private final List<String> seatIds;

    // Optional — with defaults
    private final String promoCode;
    private final String paymentMethod;
    private final boolean sendConfirmationEmail;

    // Private constructor — only the Builder can call this
    private BookingRequest(Builder builder) {
        this.userId = builder.userId;
        this.showId = builder.showId;
        this.seatIds = List.copyOf(builder.seatIds);
        this.promoCode = builder.promoCode;
        this.paymentMethod = builder.paymentMethod;
        this.sendConfirmationEmail = builder.sendConfirmationEmail;
    }

    // Getters only — no setters (immutable)
    public String getUserId() { return userId; }
    public String getShowId() { return showId; }
    public List<String> getSeatIds() { return seatIds; }
    public String getPromoCode() { return promoCode; }
    public String getPaymentMethod() { return paymentMethod; }
    public boolean isSendConfirmationEmail() { return sendConfirmationEmail; }

    // The Builder lives here as a static nested class
    public static final class Builder {
        // ... see implementation below
    }
}
```

---

## ⚙️ Implementation

**Steps in plain English:**

1. **Make the target constructor private** — force all callers to go through the Builder.
2. **Write the Builder as a static inner class** — it mirrors the target's fields, with the same mandatory ones in the Builder constructor.
3. **Fluent setter methods** — each `withXxx(value)` sets a field and returns `this` (enables method chaining).
4. **`build()` validates and constructs** — checks mandatory fields are set, then calls the private constructor.
5. **Callers chain** — readable, self-documenting construction with named fields.

```java
// Steps 2-4 — the Builder inner class
public static final class Builder {

    // Step 2 — mirror the target's fields
    private final String userId;
    private final String showId;
    private final List<String> seatIds;
    private String promoCode = null;
    private String paymentMethod = "UPI";
    private boolean sendConfirmationEmail = true;

    // Mandatory fields go in the Builder constructor — can't call build() without them
    public Builder(String userId, String showId, List<String> seatIds) {
        this.userId = userId;
        this.showId = showId;
        this.seatIds = new ArrayList<>(seatIds);
    }

    // Step 3 — fluent setters for optional fields
    public Builder withPromoCode(String promoCode) {
        this.promoCode = promoCode;
        return this;
    }

    public Builder withPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
        return this;
    }

    public Builder withConfirmationEmail(boolean send) {
        this.sendConfirmationEmail = send;
        return this;
    }

    // Step 4 — validate then construct the immutable target
    public BookingRequest build() {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("userId is required");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalStateException("at least one seat must be selected");
        }
        return new BookingRequest(this);
    }
}
```

```java
// Step 5 — usage: reads like a sentence
BookingRequest request = new BookingRequest.Builder("user-42", "show-99", List.of("A1", "A2"))
    .withPromoCode("SUMMER20")
    .withPaymentMethod("CARD")
    .withConfirmationEmail(true)
    .build();
// Compare to: new BookingRequest("user-42", "show-99", List.of("A1","A2"), "SUMMER20", "CARD", true)
// Which of those 6 args is the promo code? You can't tell without the source.
```

### 🎨 Visual — Builder Construction Flow

```
  Caller                      Builder                     BookingRequest
  ──────                      ───────                     ──────────────
  new Builder(                ┌─────────────────────┐
    userId, showId, seatIds)─▶│ userId    = "u-42"  │
                               │ showId    = "s-99"  │
  .withPromoCode("SUMMER20")─▶│ promoCode = "S20"   │
  .withPaymentMethod("CARD")─▶│ payment   = "CARD"  │
  .withConfirmationEmail(true)▶│ email     = true    │
                               └─────────┬───────────┘
  .build() ──────────────────────────────┘
                                         │ validate + new BookingRequest(this)
                                         ▼
                               ┌─────────────────────┐
                               │  BookingRequest      │
                               │  (immutable, final)  │
                               └─────────────────────┘

KEY INVARIANT:
   The target object is only created at build() — after all fields are set
   and validation passes. Once created, no field can change.
```

---

## 🏢 Real World Usage

- **`StringBuilder` (Java stdlib)** — `append()` chains return `this`. `toString()` is the `build()`. The underlying `char[]` is only finalised at `toString()`. The earliest Java Builder.
- **`OkHttpClient.Builder` (Square)** — `new OkHttpClient.Builder().connectTimeout(10, SECONDS).readTimeout(30, SECONDS).addInterceptor(loggingInterceptor).build()`. HTTP client with 20+ config options — Builder makes it readable.
- **`UriComponentsBuilder` (Spring)** — `UriComponentsBuilder.fromHttpUrl(base).path("/api/v1").queryParam("page", 1).build().toUri()`. Building URIs with optional query params is a classic Builder use case.
- **Lombok `@Builder`** — The annotation generates the entire Builder inner class at compile time. Production Java code uses this to avoid boilerplate, but the pattern is identical to what we write by hand.
- **`AlertDialog.Builder` (Android)** — Building a dialog box with optional title, message, icon, buttons, theme. Each `set*()` returns the builder.

---

## 🧭 When to Use vs When NOT to Use

| Use Builder when | Do NOT use when |
|---|---|
| Object has 4+ constructor parameters | 1-3 params — a plain constructor is clearer |
| Mix of mandatory and optional fields | All fields are always required — just use a constructor |
| Object should be immutable after construction | Object mutates frequently post-creation |
| Construction involves validation or defaults | Simple POJO with no validation |
| The same builder logic creates variants (test vs production configs) | The object is a record/DTO with public fields |

**Common mistake:** Using Builder for every class "for future-proofing." If a class has 2 fields, a two-arg constructor is more readable. Builder pays off when parameters start to look like `new Foo(true, false, null, 3, "X", null)`.

---

## 🧩 LLD Problems That Use Builder Pattern

- **BookMyShow** — `BookingRequest.Builder` takes mandatory userId, showId, seatIds in constructor; optional promoCode, paymentMethod, notificationPrefs via fluent setters. The `BookingService` receives a `BookingRequest` — not 7 separate method params.
- **Splitwise** — `Expense.Builder` takes mandatory amount and paidBy; optional description, currency, splitType, participants, notes. Expenses can be created with different optional combinations.
- **Notification System** — `Notification.Builder` with mandatory recipientId and message; optional channel (EMAIL/SMS/PUSH), templateId, retryPolicy, scheduledAt. The builder handles defaults (`channel = EMAIL`, `retryPolicy = 3 attempts`).
- **Rate Limiter Config** — `RateLimiterConfig.Builder` — algorithm (TokenBucket/SlidingWindow), requestsPerSecond, burstCapacity, timeout. Config objects are ideal Builder candidates: many options, set-once, immutable after startup.
- **HTTP Client / External API Call** — Building outbound HTTP requests: URL, headers, body, auth token, timeout, retry — all optional except URL. Builder avoids a 10-arg method signature.

---

## 🔬 Interview Q&As

### Q: "Why use Builder instead of a constructor with all parameters?"
> Two reasons. First, **readability**: `new BookingRequest.Builder(userId, showId, seats).withPromoCode("X").build()` reads as what it is. `new BookingRequest(userId, showId, seats, "X", null, true, false)` is inscrutable — you need the source to decode the 5th argument. Second, **optional fields**: with a plain constructor you either add every combination (telescoping constructor explosion) or accept null for optionals (callers forget what's optional). Builder makes the distinction explicit: mandatory fields go in the Builder constructor, optional ones get fluent setters with defaults.

### Q: "How is Builder different from Factory?"
> **Factory** solves the "which class to instantiate" problem — it decides the type and returns an interface. You don't control the construction details. **Builder** solves the "how to construct this complex object" problem — the type is known, the caller configures every detail step by step. Factory is about polymorphism; Builder is about readable, safe construction of one known type with many options.

### Q: "How do you make Builder thread-safe?"
> The Builder itself is not shared between threads — it's created, configured, and `build()`'d by one thread in a single flow. The resulting object is immutable (no setters), so it IS safe to share across threads once built. If you needed a shared mutable builder (rare), you'd synchronize the setter methods, but that's almost never the right design.

### Q: "What's the difference between Builder and the Fluent Interface pattern?"
> Builder IS a fluent interface — returning `this` from setters enables method chaining, which is what "fluent" means. The Builder pattern adds a specific structural constraint: mandatory fields in the constructor, optional ones as fluent setters, and a terminal `build()` that validates and produces an immutable result. Fluent interface is the coding technique; Builder is the design pattern that uses it.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"Builder separates complex object construction from its representation. When BookingRequest has userId, showId, seatIds, promoCode, paymentMethod, and notification flags — a 6-arg constructor is unreadable and fragile. Builder makes mandatory fields explicit in the Builder constructor, optional fields named fluent setters, and build() validates + returns an immutable object. In production, Lombok's @Builder generates this for free."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. BookMyShow BookingRequest as primary worked example. |
