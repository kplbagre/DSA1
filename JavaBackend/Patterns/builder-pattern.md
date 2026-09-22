# 🧩 Builder Pattern — Pattern

> **When to use:** An object has many fields (≥4), some optional, and you want readable construction without telescoping constructors. Builder provides a fluent, self-documenting API: `Order.builder().customerId(42).status(PENDING).build()`.

---

## 🎯 The Problem

```java
// Telescoping constructor — unreadable at the call site
Order order = new Order(42L, "Alice", "PENDING", null, null, LocalDate.now(), true, 0.0);
// Which argument is the status? Which is the discount? What are the nulls?
// 8 parameters → 8! possible constructor combinations if you want optional params
```

---

## 🧠 Why It Exists

The Builder pattern separates **construction** from **representation**. The caller names each field explicitly (`status(PENDING)`) instead of relying on positional arguments. Optional fields have defaults. The built object can be immutable (all fields `final`, no setters). The pattern is self-documenting — the call site reads like a specification.

---

## 🔧 Implementation

### Classic Builder (manual)

```java
public class Order {
    private final Long id;
    private final String customerName;
    private final String status;
    private final LocalDate orderDate;
    private final boolean expedited;
    private final double discount;

    private Order(Builder builder) {
        this.id = builder.id;
        this.customerName = builder.customerName;
        this.status = builder.status;
        this.orderDate = builder.orderDate;
        this.expedited = builder.expedited;
        this.discount = builder.discount;
    }

    // Getters only — no setters. Object is immutable.
    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    // ... rest of getters

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // Required fields
        private Long id;
        private String customerName;

        // Optional fields with defaults
        private String status = "PENDING";
        private LocalDate orderDate = LocalDate.now();
        private boolean expedited = false;
        private double discount = 0.0;

        public Builder id(Long id) {
            this.id = id;
            return this;   // return this → enables fluent chaining
        }

        public Builder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder orderDate(LocalDate orderDate) {
            this.orderDate = orderDate;
            return this;
        }

        public Builder expedited(boolean expedited) {
            this.expedited = expedited;
            return this;
        }

        public Builder discount(double discount) {
            this.discount = discount;
            return this;
        }

        public Order build() {
            // Validate required fields
            if (id == null) {
                throw new IllegalStateException("id is required");
            }
            if (customerName == null || customerName.isBlank()) {
                throw new IllegalStateException("customerName is required");
            }
            return new Order(this);
        }
    }
}

// Usage — self-documenting:
Order order = Order.builder()
    .id(42L)
    .customerName("Alice")
    .status("SHIPPED")
    .expedited(true)
    .build();
// Optional fields (orderDate, discount) use defaults
```

### Builder with Records (Java 16+)

```java
// Records are immutable by default, but have a fixed canonical constructor.
// Builder still useful for records with many components:
public record OrderRequest(
    Long customerId,
    String product,
    int quantity,
    String shippingAddress,
    boolean giftWrap,
    String couponCode
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long customerId;
        private String product;
        private int quantity = 1;
        private String shippingAddress;
        private boolean giftWrap = false;
        private String couponCode = null;

        public Builder customerId(Long id) { this.customerId = id; return this; }
        public Builder product(String p) { this.product = p; return this; }
        public Builder quantity(int q) { this.quantity = q; return this; }
        public Builder shippingAddress(String a) { this.shippingAddress = a; return this; }
        public Builder giftWrap(boolean g) { this.giftWrap = g; return this; }
        public Builder couponCode(String c) { this.couponCode = c; return this; }

        public OrderRequest build() {
            return new OrderRequest(customerId, product, quantity, shippingAddress, giftWrap, couponCode);
        }
    }
}
```

---

## 🏢 Where You See It

| Framework/Library | Example |
|---|---|
| **JDK** | `StringBuilder`, `Stream.builder()`, `HttpRequest.newBuilder()`, `ProcessBuilder` |
| **Spring** | `UriComponentsBuilder`, `MockMvcRequestBuilders`, `WebClient.builder()` |
| **Jackson** | `ObjectMapper.builder()` (Jackson 3.x) |
| **Resilience4j** | `CircuitBreakerConfig.custom().failureRateThreshold(50).build()` |
| **Test data** | Builder pattern for test fixtures — `TestOrder.builder().withDefaults().build()` |

---

## ⚠️ Gotchas

- **Lombok `@Builder`** generates this boilerplate automatically. Preferred in production for DTOs. But Kapil's notes use plain Java (per AGENTS.md) so you SEE what Lombok generates.
- **Thread safety:** the Builder itself is NOT thread-safe. Don't share a Builder across threads. The built object CAN be thread-safe (immutable).
- **Builder ≠ Setter chain:** builders produce IMMUTABLE objects. Setter chains mutate existing objects. Different intent.

---

## 🎙️ Say It in 60 Seconds

> Builder separates construction from representation. Instead of a 8-parameter constructor where you can't tell which argument is which, you call `Order.builder().customerId(42).status("PENDING").build()` — each field is named, optional fields have defaults, and the built object is immutable. The pattern is the most common in Java: `HttpRequest.newBuilder()`, `StringBuilder`, every Resilience4j config. In production, use Lombok's `@Builder` to avoid the boilerplate — it generates the same inner class.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #27 (Phase 5). Classic Builder, Builder with Records, framework examples, Lombok note. |
