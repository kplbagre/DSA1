# ☕ Serialization & API Contracts — Deep Dive

> After this note you can configure Jackson for production (ignore unknown fields, handle dates, custom serializers), design backward-compatible API evolution (additive changes only), choose between DTO patterns, and explain why `@JsonProperty` ordering matters for contract stability.

---

## 🎯 The Problem This Solves

Your API returns an `Order` object as JSON. A mobile client parses it. You add a new field `discountApplied`. The client crashes — `UnrecognizedPropertyException`. You rename `customerId` to `userId`. The client sends the old field name. Your `@RequestBody` maps it to `null`. No error, just silent data loss. API evolution without contract discipline breaks every consumer silently.

Jackson is the JSON engine inside Spring Boot. Understanding its configuration, annotation model, and failure modes is what prevents serialization bugs from reaching production.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Serialization** | Converting a Java object to a data format (JSON, XML, protobuf). Jackson's `ObjectMapper.writeValueAsString(obj)` → JSON string. |
| **Deserialization** | Converting data format back to a Java object. Jackson's `ObjectMapper.readValue(json, Order.class)` → Order instance. |
| **ObjectMapper** | Jackson's central class. Thread-safe, reusable. Configures how Java ↔ JSON mapping works. Spring Boot auto-configures one. |
| **DTO (Data Transfer Object)** | An object designed specifically for API input/output. Separates the API contract from the internal domain model. Changes to the domain don't automatically break the API. |
| **Backward compatibility** | A new API version doesn't break existing clients. Additive changes (new optional fields) are backward-compatible. Removals and renames are NOT. |
| **Contract-first** | Define the API contract (OpenAPI/Swagger spec) BEFORE writing code. The spec IS the source of truth. Code is generated from or validated against the spec. |

---

## 🧠 Mental Model

Think of your API response as a **legal contract** with your clients. Every field name, type, and structure is a promise. Adding a new optional field = amending the contract (safe — existing clauses still hold). Removing a field = breaking the contract (clients relying on it crash). Renaming a field = breaking AND adding (old clients crash on the rename, new clients see the new name).

Jackson is the **translator** that converts your Java objects into the contract's language (JSON) and back. If the translator is misconfigured — unknown fields cause exceptions, dates are formatted inconsistently, null fields are included — the contract is unreliable.

> If you can say "Jackson ObjectMapper is thread-safe and reusable; configure FAIL_ON_UNKNOWN_PROPERTIES=false for forward compatibility; use DTOs to decouple internal models from API contracts; additive changes only for backward compat; @JsonProperty for explicit field naming" without notes, you have serialization.

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// ❌ Exposing domain entity directly as API response
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable Long id) {
    return orderRepository.findById(id).orElseThrow();
}

// Problems:
// 1. Internal field added (e.g., internalNotes) → leaked to clients
// 2. JPA lazy proxy serialized → LazyInitializationException or infinite recursion
// 3. Field renamed in domain → API breaks
// 4. Hibernate-specific annotations (@Entity, @Column) leak into API contract
// 5. Circular references (Order → Items → Order) → StackOverflowError
```

---

### Level 2 — The real mechanism

#### 2.1 — Jackson production configuration

```java
// Tier 2 — Production: ObjectMapper configuration
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
            // Don't crash on unknown fields (forward compatibility)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Don't include null fields in output (smaller payloads)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            // Use ISO-8601 for dates (not Unix timestamps)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Register Java 8 date/time module
            .addModule(new JavaTimeModule())
            // Fail on empty beans (misconfigured DTOs)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();
    }
}

// Or via application.yml (Spring Boot auto-configuration):
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: false
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null
```

**Why each setting matters:**

| Setting | Why |
|---|---|
| `FAIL_ON_UNKNOWN_PROPERTIES = false` | Client sends a field you don't know about → ignore it, don't crash. Essential for forward compatibility — new clients can send new fields to old servers. |
| `NON_NULL` inclusion | Don't serialize `null` fields → smaller JSON payloads. `{"name":"Alice"}` instead of `{"name":"Alice","middleName":null,"suffix":null}`. |
| `WRITE_DATES_AS_TIMESTAMPS = false` | Dates as `"2026-09-22T14:30:00Z"` (ISO-8601, human-readable) instead of `1758536600000` (Unix ms, unreadable). |
| `JavaTimeModule` | Support for `LocalDate`, `LocalDateTime`, `Instant`, `ZonedDateTime`. Without it, Java 8 date types fail. |

#### 2.2 — Essential Jackson annotations

```java
public class OrderResponse {

    @JsonProperty("order_id")   // explicit JSON field name — decoupled from Java field name
    private Long id;            // Java: id. JSON: order_id. Renaming Java field doesn't break API.

    @JsonProperty("customer_name")
    private String customerName;

    @JsonFormat(pattern = "yyyy-MM-dd")   // date format in JSON
    private LocalDate orderDate;

    @JsonIgnore   // never serialize this field — internal use only
    private String internalNotes;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)   // skip empty collections
    private List<String> tags;

    // Constructor for deserialization:
    @JsonCreator   // tells Jackson to use this constructor
    public OrderResponse(
        @JsonProperty("order_id") Long id,
        @JsonProperty("customer_name") String customerName
    ) {
        this.id = id;
        this.customerName = customerName;
    }
}

// Records work naturally with Jackson (Java 16+):
public record OrderResponse(
    @JsonProperty("order_id") Long id,
    @JsonProperty("customer_name") String customerName,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate orderDate,
    List<OrderItemResponse> items
) {}
// Jackson auto-detects record components — no @JsonCreator needed
```

#### 2.3 — DTO pattern: decouple API from domain

```java
// DOMAIN ENTITY (internal — JPA, business logic):
@Entity
public class Order {
    @Id @GeneratedValue
    private Long id;
    private String customerName;
    private String status;
    private BigDecimal total;
    private String internalNotes;   // internal — never exposed

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;  // lazy-loaded — can't serialize directly
}

// API DTO (external — what the client sees):
public record OrderResponse(
    @JsonProperty("order_id") Long id,
    @JsonProperty("customer_name") String customerName,
    String status,
    BigDecimal total,
    List<OrderItemResponse> items   // no lazy proxy — already loaded
) {
    // Factory method: domain → DTO
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getCustomerName(),
            order.getStatus(),
            order.getTotal(),
            order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList()
        );
    }
}

public record OrderItemResponse(
    String productName,
    int quantity,
    BigDecimal unitPrice
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
            item.getProductName(),
            item.getQuantity(),
            item.getUnitPrice()
        );
    }
}

// Controller: entity → DTO before returning
@GetMapping("/orders/{id}")
public OrderResponse getOrder(@PathVariable Long id) {
    Order order = orderService.findByIdWithItems(id);
    return OrderResponse.from(order);   // explicit mapping — no accidental field leaks
}
```

**Why DTOs, not entities:**

| Without DTO | With DTO |
|---|---|
| Add internal field → leaked to API | Add internal field → DTO unchanged |
| Rename entity field → API breaks | Rename entity field → DTO mapping updated, API stable |
| Lazy proxy in response → exception | DTO has eagerly loaded data only |
| Circular reference → infinite recursion | DTO is a flat structure — no cycles |
| API evolution = entity evolution (coupled) | API evolution = DTO evolution (independent) |

#### 2.4 — Custom serializer / deserializer

```java
// When annotations aren't enough — full control:
public class MoneySerializer extends JsonSerializer<Money> {
    @Override
    public void serialize(Money money, JsonGenerator gen, SerializerProvider prov)
        throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("amount", money.getAmount());
        gen.writeStringField("currency", money.getCurrency().getCurrencyCode());
        gen.writeEndObject();
    }
}

public class MoneyDeserializer extends JsonDeserializer<Money> {
    @Override
    public Money deserialize(JsonParser p, DeserializationContext ctx)
        throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        BigDecimal amount = node.get("amount").decimalValue();
        Currency currency = Currency.getInstance(node.get("currency").asText());
        return new Money(amount, currency);
    }
}

// Register on the field:
public class OrderResponse {
    @JsonSerialize(using = MoneySerializer.class)
    @JsonDeserialize(using = MoneyDeserializer.class)
    private Money total;
}

// Or register globally on ObjectMapper:
SimpleModule module = new SimpleModule();
module.addSerializer(Money.class, new MoneySerializer());
module.addDeserializer(Money.class, new MoneyDeserializer());
objectMapper.registerModule(module);
```

#### 2.5 — Backward-compatible API evolution

```
  SAFE CHANGES (backward compatible — existing clients don't break):
  ✅ Add a new OPTIONAL field (clients ignore unknown fields if configured)
  ✅ Add a new endpoint
  ✅ Add a new optional query parameter
  ✅ Widen a type (int → long, enum → add new value)
  ✅ Make a required field optional

  BREAKING CHANGES (clients break):
  ❌ Remove a field (clients reading it get null/error)
  ❌ Rename a field (old name → not found)
  ❌ Change a field's type (string → int)
  ❌ Make an optional field required
  ❌ Change URL path structure
  ❌ Narrow a type (remove enum value clients depend on)

  STRATEGY FOR BREAKING CHANGES:
  1. API versioning: /api/v1/orders and /api/v2/orders (coexist)
  2. Deprecation: keep old field + add new field → both present for N months
     @JsonProperty("customer_name")     // old name (deprecated, still sent)
     @JsonProperty("customerName")      // new name (also sent)
  3. Content negotiation: Accept header selects version
     Accept: application/vnd.walmart.v2+json
```

---

### Level 3 — The subtleties

#### 3.1 — Polymorphic serialization

```java
// Serializing/deserializing a class hierarchy:
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmailNotification.class, name = "email"),
    @JsonSubTypes.Type(value = SmsNotification.class, name = "sms"),
    @JsonSubTypes.Type(value = PushNotification.class, name = "push")
})
public abstract class Notification {
    private String message;
}

public class EmailNotification extends Notification {
    private String emailAddress;
}

// JSON output includes "type" discriminator:
// {"type": "email", "message": "Hello", "emailAddress": "alice@test.com"}

// Deserialization: Jackson reads "type" field → creates the right subclass
Notification n = objectMapper.readValue(json, Notification.class);
// n is EmailNotification if type = "email"
```

#### 3.2 — `@JsonView` — different views for different endpoints

```java
// Same DTO, different fields for different APIs:
public class Views {
    public static class Summary {}
    public static class Detail extends Summary {}
}

public class OrderResponse {
    @JsonView(Views.Summary.class)
    private Long id;

    @JsonView(Views.Summary.class)
    private String status;

    @JsonView(Views.Detail.class)   // only in detail view
    private List<OrderItemResponse> items;

    @JsonView(Views.Detail.class)
    private String internalNotes;
}

// Controller:
@GetMapping("/orders")
@JsonView(Views.Summary.class)   // list: only id + status
public List<OrderResponse> listOrders() { ... }

@GetMapping("/orders/{id}")
@JsonView(Views.Detail.class)    // detail: all fields
public OrderResponse getOrder(@PathVariable Long id) { ... }
```

#### 3.3 — Handling `null` vs absent vs empty

```java
// Three different meanings:
// {"name": null}     → field present, value is null
// {}                 → field absent
// {"name": ""}       → field present, value is empty string

// Jackson configuration controls which are serialized:
@JsonInclude(JsonInclude.Include.NON_NULL)     // skip null fields
@JsonInclude(JsonInclude.Include.NON_EMPTY)    // skip null + empty string + empty collections
@JsonInclude(JsonInclude.Include.NON_ABSENT)   // skip null + empty Optional

// For PATCH operations (partial updates):
// You NEED to distinguish "field absent" (don't update) from "field = null" (set to null)
// Jackson's default can't distinguish them — use Optional<T> or a custom deserializer
// Or: accept a Map<String, Object> for PATCH and process explicitly
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Jackson handles Java 8 dates automatically" | Without `JavaTimeModule`, Jackson doesn't know how to serialize `LocalDate`, `LocalDateTime`, `Instant`. You get `InvalidDefinitionException`. Always register `JavaTimeModule` or add `jackson-datatype-jsr310` dependency (Spring Boot includes it but the module must be registered). |
| "Returning entity from controller is fine" | Entities carry JPA proxies (LazyInitializationException), internal fields (security leak), circular references (infinite recursion), and couple API evolution to database schema changes. Always use DTOs. |
| "Adding a field to the response is always safe" | Safe for clients that ignore unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES=false`). But Java clients using strict deserialization (default Jackson) will CRASH on the new field. Always configure clients to ignore unknown properties. |
| "ObjectMapper should be created per request" | `ObjectMapper` is thread-safe and expensive to create (builds caches, resolves modules). Create ONE, reuse everywhere. Spring Boot auto-configures a singleton. Creating per-request = performance cliff + GC pressure. |
| "`@JsonProperty` is optional" | Without `@JsonProperty`, Jackson uses the Java field name. If you refactor the field name, the JSON property name changes → API breaks. `@JsonProperty` decouples them — the JSON name is stable regardless of Java refactoring. |

---

## 🐞 Production Footguns

---

> **Footgun: `FAIL_ON_UNKNOWN_PROPERTIES=true` (default) breaks forward compat**
> **Cost:** Deserialization crash when upstream adds a field
>
> A service consumed JSON from an upstream API. The upstream added a new field `discountRate`. The consuming service's Jackson was configured with the default `FAIL_ON_UNKNOWN_PROPERTIES=true`. Every response from the upstream crashed with `UnrecognizedPropertyException`. The consuming service went down because of a benign upstream change.

```java
// ❌ The trap: default Jackson rejects unknown fields
// upstream JSON: {"orderId": 42, "status": "PENDING", "discountRate": 0.1}
// your DTO has no 'discountRate' field → CRASH

// ✅ The fix: always disable in Spring Boot
spring.jackson.deserialization.fail-on-unknown-properties: false
// Unknown fields are silently ignored — forward compatible
```

---

> **Footgun: Exposing JPA entity with lazy collections**
> **Cost:** LazyInitializationException or infinite recursion
>
> A controller returned a JPA `Order` entity directly. The entity had `@OneToMany(fetch = LAZY) List<OrderItem> items`. Outside the `@Transactional` scope, Jackson tried to serialize `items` → `LazyInitializationException`. When `EAGER` was used instead → `Order` → `OrderItem` → `Order` (bidirectional) → infinite recursion → `StackOverflowError`.

```java
// ❌ The trap: entity as response
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable Long id) {
    return orderRepository.findById(id).orElseThrow();
    // Lazy: LazyInitializationException
    // Eager + bidirectional: StackOverflowError
}

// ✅ The fix: DTO with explicit mapping
@GetMapping("/orders/{id}")
public OrderResponse getOrder(@PathVariable Long id) {
    Order order = orderService.findByIdWithItems(id);
    return OrderResponse.from(order);   // flat DTO — no proxies, no cycles
}
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `../Spring/DeepDive/03-spring-mvc-boot.md` | Spring MVC uses `HttpMessageConverter` (Jackson) to serialize/deserialize `@RequestBody` and `@ResponseBody`. The ObjectMapper configured here IS the converter Spring uses. |
| `../Spring/DeepDive/04-jpa-transactions.md` | Returning JPA entities from controllers causes LazyInitializationException. DTOs (this note) are the fix — explicit mapping from entity to response. |
| `generics-type-erasure.md` | Jackson's `TypeReference<List<User>>()` recovers generic type info at runtime using the super-type-token pattern (from the generics note). Without TypeReference, `readValue(json, List.class)` returns `List<LinkedHashMap>`, not `List<User>`. |
| `testing-strategy.md` | `@JsonTest` slice test validates serialization/deserialization without loading the full context. `@WebMvcTest` tests the full JSON contract (request → response). |

---

## 🎙️ Interview Deep Questions

**Q1. Why should you use DTOs instead of returning JPA entities from controllers?**

> Five reasons: (1) JPA lazy proxies in the response cause `LazyInitializationException` outside `@Transactional`. (2) Internal fields (audit timestamps, internal notes) leak to clients — security risk. (3) Bidirectional JPA relationships (`Order` → `Item` → `Order`) cause infinite recursion during serialization → `StackOverflowError`. (4) Renaming an entity field for a database migration automatically renames the API field — breaking all clients. (5) API evolution is coupled to domain evolution — you can't change one without the other. DTOs decouple the API contract from the internal model — the DTO is a translation layer between your database schema and your API specification.

**Q2. How do you make an API backward compatible when adding or changing fields?**

> Safe changes: add new optional fields (clients that don't know about them ignore them — if `FAIL_ON_UNKNOWN_PROPERTIES=false`), add new endpoints, add optional query parameters. Breaking changes: removing fields, renaming fields, changing types, making optional fields required. For breaking changes: use API versioning (`/api/v1/orders` and `/api/v2/orders` coexist), deprecation period (old field + new field both present for N months, then old removed), or content negotiation (`Accept: application/vnd.walmart.v2+json`). The key principle: additive changes are safe; subtractive and mutative changes break clients.

**Q3. What Jackson configurations should every Spring Boot service set?**

> Four non-negotiable settings: (1) `FAIL_ON_UNKNOWN_PROPERTIES=false` — forward compatibility; new fields from upstream don't crash your deserializer. (2) `WRITE_DATES_AS_TIMESTAMPS=false` — ISO-8601 date strings instead of Unix milliseconds; human-readable and timezone-safe. (3) `JavaTimeModule` registered — supports `LocalDate`, `LocalDateTime`, `Instant`. (4) `NON_NULL` inclusion — don't serialize null fields; smaller payloads, cleaner JSON. Additionally: `@JsonProperty` on every DTO field to decouple Java field names from API contract names — prevents accidental API breaks during refactoring.

**Q4. How does Jackson handle polymorphic types (inheritance hierarchies)?**

> Use `@JsonTypeInfo` on the base class to include a type discriminator in the JSON (e.g., `"type": "email"`). `@JsonSubTypes` lists the concrete classes and their discriminator values. On serialization: Jackson adds the `"type"` field automatically. On deserialization: Jackson reads the `"type"` field, looks up the class mapping, and creates the right subclass. This enables: sending a `List<Notification>` where each element can be EmailNotification, SmsNotification, or PushNotification — the client or server deserializes each to the correct type based on the discriminator.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Jackson is Spring Boot's JSON engine. `ObjectMapper` is thread-safe and reusable. It serializes Java → JSON and deserializes JSON → Java. Configuration determines what gets serialized, how dates are formatted, and whether unknown fields crash or are ignored.
>
> **Part 2 — How/Why (30s):** Always use DTOs — never expose JPA entities (lazy proxy exceptions, internal field leaks, circular reference crashes). Configure Jackson: `FAIL_ON_UNKNOWN_PROPERTIES=false` (forward compatibility), `WRITE_DATES_AS_TIMESTAMPS=false` (ISO-8601), register `JavaTimeModule`, `NON_NULL` inclusion. Use `@JsonProperty` to decouple Java field names from JSON field names — refactoring Java doesn't break the API. For polymorphism, `@JsonTypeInfo` adds a discriminator field. For API evolution: additive changes only. Removals and renames break clients.
>
> **Part 3 — Gotcha (20s):** Two traps: default Jackson rejects unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES=true`) — when an upstream adds a field, your service crashes. Fix: disable it. And returning JPA entities from controllers → `LazyInitializationException` or `StackOverflowError` from bidirectional relationships. Fix: always return DTOs with explicit mapping from the entity.

---

## 🧾 TL;DR

- **ObjectMapper is thread-safe.** Create one, reuse everywhere. Never create per-request.
- **FAIL_ON_UNKNOWN_PROPERTIES=false** — forward compatibility. Non-negotiable.
- **WRITE_DATES_AS_TIMESTAMPS=false** + `JavaTimeModule` — ISO-8601 dates.
- **DTOs, not entities.** Prevents: lazy proxy exceptions, field leaks, circular recursion, coupled evolution.
- **`@JsonProperty("name")`** — decouples JSON field name from Java field name. Prevents accidental API breaks.
- **Backward compat:** additive changes safe. Removals/renames break clients. Use versioning for breaking changes.
- **Custom serializer/deserializer** for complex types (Money, polymorphic hierarchies).
- **`@JsonView`** — different field sets for list vs detail endpoints, same DTO class.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #42 (Phase 6, Tier 2) of the JavaBackend KB completion roadmap. Staff-level depth: Jackson production configuration (4 non-negotiable settings), essential annotations (@JsonProperty, @JsonCreator, @JsonIgnore, @JsonFormat, @JsonInclude), DTO pattern with entity→DTO factory methods, custom serializer/deserializer, backward-compatible API evolution (safe vs breaking changes, versioning strategies), polymorphic serialization (@JsonTypeInfo/@JsonSubTypes), @JsonView for endpoint-specific responses, null vs absent vs empty semantics. Two production footguns: FAIL_ON_UNKNOWN_PROPERTIES crash, JPA entity serialization. |
