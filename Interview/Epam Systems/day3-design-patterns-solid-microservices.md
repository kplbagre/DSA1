# Day 3 — Design Patterns · SOLID · Microservices Patterns · JPA · SQL
### EPAM Interview Prep · 4 Hours · Sep 20, 2026

> **Who this is for:** A developer who uses Singleton, Streams, and Circuit Breaker in production but hasn't mapped the pattern names to EPAM's vocabulary. Today is about being able to *name and draw* patterns precisely under interview pressure.

> **What you will be able to do after this:** Write the Bill Pugh Singleton and explain why it works, name all 5 SOLID principles with code for the tricky 2 (LSP, DIP), draw Circuit Breaker 3 states with transitions, distinguish SAGA Choreography vs Orchestration, and write RANK/DENSE_RANK SQL.

---

## 🧾 Index — Jump to Any Section

| # | Topic | Time |
| --- | --- | --- |
| [1. Singleton — All Flavors + How to Break It](#singleton) | Bill Pugh, Enum, double-checked locking, reflection/serialization | ~40 min |
| [2. Design Patterns in Spring](#spring-patterns) | Factory, Proxy, Template, Observer, Front Controller, Strategy | ~20 min |
| [3. SOLID — All 5 With Code](#solid) | S, O, L (the violation), I, D (the confusion) | ~40 min |
| [4. Circuit Breaker + Resilience4j](#circuit-breaker) | 3 states, Resilience4j bundles, config | ~30 min |
| [5. SAGA + CQRS](#saga-cqrs) | Choreography vs Orchestration, CQRS (not Event Sourcing) | ~30 min |
| [6. API Gateway + N+1 + JPA Traps](#api-gateway-jpa) | API Gateway role, 3 N+1 fixes + pagination bug, @ManyToOne trap | ~30 min |
| [7. SQL Window Functions](#sql) | ROW\_NUMBER vs RANK vs DENSE\_RANK, 2nd-highest salary | ~30 min |

---

<a id="singleton"></a>

## 🔹 1. Singleton — All Flavors + How to Break It

### 📖 Terminology

- **Singleton pattern** — a creational design pattern (a pattern that deals with object creation) that ensures a class has exactly one instance, and provides a global point of access to it.
- **Lazy initialization** — delay creating the object until it's first needed (saves memory if it's never used).
- **Eager initialization** — create the object when the class loads, not on first use.

---

### 🧠 Mental Model

Imagine a company has exactly one CEO. Every department that needs executive decisions goes through the same CEO. No department creates its own CEO. The CEO is created once, shared everywhere.

That's a Singleton — one instance, shared by all callers.

---

### 🔬 Flavor 1 — Naive (Broken Under Concurrency)

```java
public class DatabasePool {

    private static DatabasePool instance;

    private DatabasePool() {}   // private constructor — prevents "new DatabasePool()"

    // BAD: two threads can both see instance == null simultaneously
    // and both create a new instance → two instances exist → not a singleton!
    public static DatabasePool getInstance() {
        if (instance == null) {
            instance = new DatabasePool();
        }
        return instance;
    }
}
```

---

### 🔬 Flavor 2 — Synchronized (Safe But Slow)

```java
public class DatabasePool {

    private static DatabasePool instance;

    private DatabasePool() {}

    // synchronized: only one thread enters at a time → correct but every
    // call to getInstance() acquires a lock → expensive under load
    public static synchronized DatabasePool getInstance() {
        if (instance == null) {
            instance = new DatabasePool();
        }
        return instance;
    }
}
```

---

### 🔬 Flavor 3 — Double-Checked Locking (Safe + Fast, But Has a Trap)

```java
public class DatabasePool {

    // ⭐ MUST be volatile — without volatile, the JVM can reorder:
    // 1. allocate memory for instance
    // 2. assign reference to instance  ← visible to other threads too early!
    // 3. call constructor to initialize fields
    // A thread reading instance between steps 2 and 3 sees a half-initialized object.
    // volatile prevents reordering → forces full construction before reference is visible.
    private static volatile DatabasePool instance;

    private DatabasePool() {}

    public static DatabasePool getInstance() {
        if (instance == null) {               // First check — no lock, fast path
            synchronized (DatabasePool.class) {
                if (instance == null) {       // Second check — under lock
                    instance = new DatabasePool();
                }
            }
        }
        return instance;
    }
}
```

---

### 🔬 Flavor 4 — Bill Pugh (⭐ Preferred Answer at EPAM)

**Steps in plain English:**

1. Put the instance in a private static inner class (called the Holder).
2. Java guarantees that inner classes are not loaded until they are first accessed.
3. When `getInstance()` is first called, JVM loads `Holder`, which creates the instance.
4. Class loading is thread-safe (guaranteed by JVM spec) — so no `synchronized` needed.

```java
public class DatabasePool {

    private DatabasePool() {}

    // Holder class is loaded lazily — only when getInstance() is first called
    private static class Holder {
        // JVM class initialization is thread-safe — no synchronized needed
        static final DatabasePool INSTANCE = new DatabasePool();
    }

    public static DatabasePool getInstance() {
        return Holder.INSTANCE;   // triggers Holder class loading on first call
    }
}
```

**Why this wins the interview:**
- Lazy (created on first call, not at class load)
- Thread-safe without synchronized
- No volatile needed
- Clean and readable

---

### 🔬 Flavor 5 — Enum Singleton (⭐ Immune to All Breaking Techniques)

```java
public enum AppConfig {

    INSTANCE;   // one value = one instance, created by JVM at class load

    private String dbUrl = "jdbc:postgresql://localhost/mydb";

    public String getDbUrl() {
        return dbUrl;
    }
}

// Usage:
String url = AppConfig.INSTANCE.getDbUrl();
```

---

### ⚠️ How to "Break" a Regular Singleton — The Follow-up

An interviewer who knows their stuff WILL ask this. Three techniques break a Singleton implemented with a private constructor + static field:

**Technique 1 — Reflection:**

```java
// Reflection can access private constructors
Constructor<DatabasePool> constructor =
    DatabasePool.class.getDeclaredConstructor();
constructor.setAccessible(true);   // bypass private access
DatabasePool second = constructor.newInstance();   // second instance!

// Fix: throw exception in the constructor if instance already exists:
private DatabasePool() {
    if (Holder.INSTANCE != null) {
        throw new IllegalStateException("Use getInstance()");
    }
}
```

**Technique 2 — Serialization:**

```java
// Serializing and deserializing creates a NEW object (bypasses constructor)
DatabasePool first = DatabasePool.getInstance();
// serialize to bytes, then deserialize → second instance!

// Fix: add readResolve() — called by deserialization
protected Object readResolve() {
    return Holder.INSTANCE;   // return existing instance instead of new one
}
```

**Technique 3 — Cloning:**

```java
// If the class implements Cloneable, clone() creates a new copy
// Fix: override clone() and throw exception
@Override
protected Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException("Singleton cannot be cloned");
}
```

**Why Enum Singleton is immune:**
- Reflection: JVM prohibits calling `newInstance()` on an Enum constructor — throws `IllegalArgumentException`
- Serialization: Enum serialization by spec returns the existing constant, not a new object
- Cloning: Enum cannot be cloned (Enum doesn't implement Cloneable)

> **Effective Java (Josh Bloch):** "A single-element enum type is the best way to implement a Singleton."

---

<a id="spring-patterns"></a>

## 🔹 2. Design Patterns in Spring

### 📖 Why Spring is a Pattern Library

Spring is not just a framework — it's a collection of well-known design patterns applied at scale. Knowing which pattern each Spring feature uses shows the interviewer you understand the "why," not just the "how."

---

### 📊 Patterns in Spring — Know These Cold

| Pattern | Description | Spring Example |
| --- | --- | --- |
| **Factory** | Object creation is delegated to a factory, not done directly by clients | `ApplicationContext` creates and provides beans |
| **Singleton** | One instance per context | Default bean scope — one `UserService` shared by all |
| **Proxy** | A wrapper intercepts calls to add behavior | CGLIB proxy around `@Transactional`, `@Async`, `@Cacheable` |
| **Template Method** | Base class defines an algorithm skeleton; subclasses fill in steps | `JdbcTemplate`, `RestTemplate`, `KafkaTemplate` |
| **Observer** | When something happens, notify all interested parties | `ApplicationEvent` + `@EventListener` / `ApplicationListener` |
| **Front Controller** | One entry-point receives all requests and dispatches to handlers | `DispatcherServlet` — receives all HTTP requests, routes to controllers |
| **Strategy** | Multiple implementations of the same interface; swap at runtime | `HandlerMapping` — different strategies for routing requests to controllers |
| **Decorator** | Wrap an object to add behavior without changing it | `BeanPostProcessor` — wraps beans to add behavior after creation |

---

### 🔬 Template Method — The Non-Obvious One

**Template Method** (a pattern where a base class defines the algorithm structure and calls abstract "hook" methods that subclasses fill in) is what `JdbcTemplate` implements.

```
  JdbcTemplate.query() (base class controls the algorithm):
  ┌────────────────────────────────────────────────────────────┐
  │ 1. Get connection from pool                                │
  │ 2. Create PreparedStatement                                │
  │ 3. → YOUR CODE: set parameters (RowMapper)                │
  │ 4. Execute query                                           │
  │ 5. → YOUR CODE: map result rows (RowMapper)               │
  │ 6. Close resources                                         │
  └────────────────────────────────────────────────────────────┘
  You supply steps 3 and 5. JdbcTemplate controls the rest.
  This eliminates boilerplate (open/close connection, exception handling).
```

---

<a id="solid"></a>

## 🔹 3. SOLID — All 5 With Code

### 📖 What is SOLID?

SOLID is an acronym for five design principles (rules for writing maintainable object-oriented code) coined by Robert C. Martin ("Uncle Bob"). They're asked at almost every senior Java interview.

---

### 🎨 Visual — SOLID at a Glance

```
  S — Single Responsibility  → one class does one thing
  O — Open/Closed            → open for extension, closed for modification
  L — Liskov Substitution    → subclasses must be substitutable for their parent
  I — Interface Segregation  → don't force classes to implement unused methods
  D — Dependency Inversion   → depend on abstractions, not concretions

  Most asked at EPAM:
  ⭐ L — LSP (has a concrete code violation)
  ⭐ D — DIP (confused with Dependency Injection)
```

---

### 🔬 S — Single Responsibility Principle

> "A class should have only one reason to change."

```java
// ❌ BAD: UserService does too many things
class UserService {
    public User getUser(Long id) { ... }         // user logic
    public void sendEmail(String to, String msg) { ... }   // email logic
    public void generateReport() { ... }          // reporting logic
}
// If email library changes → UserService changes.
// If report format changes → UserService changes.
// Multiple reasons to change → SRP violated.

// ✅ GOOD: each class has one responsibility
class UserService {
    public User getUser(Long id) { ... }
}

class EmailService {
    public void sendEmail(String to, String msg) { ... }
}

class ReportService {
    public void generateReport() { ... }
}
```

---

### 🔬 O — Open/Closed Principle

> "Software entities should be open for extension but closed for modification."

```java
// ❌ BAD: adding a new discount type requires modifying the class
class DiscountCalculator {
    public double calculate(String type, double price) {
        if (type.equals("STUDENT")) {
            return price * 0.8;
        } else if (type.equals("EMPLOYEE")) {
            return price * 0.7;
        }
        // Adding "SENIOR" requires editing this method → OCP violated
        return price;
    }
}

// ✅ GOOD: new discount types added without modifying existing code
interface DiscountStrategy {
    double apply(double price);
}

class StudentDiscount implements DiscountStrategy {
    public double apply(double price) {
        return price * 0.8;
    }
}

class EmployeeDiscount implements DiscountStrategy {
    public double apply(double price) {
        return price * 0.7;
    }
}

// New discount type: just add a new class, don't touch existing code
class SeniorDiscount implements DiscountStrategy {
    public double apply(double price) {
        return price * 0.75;
    }
}
```

---

### 🔬 L — Liskov Substitution Principle (⭐ EPAM Loves This)

> "A subclass must be substitutable for its parent without breaking the behavior callers expect."

The classic violation is **Square extends Rectangle**:

```java
// Rectangle: width and height are independent
class Rectangle {
    protected int width;
    protected int height;

    public void setWidth(int w) {
        this.width = w;
    }

    public void setHeight(int h) {
        this.height = h;
    }

    public int area() {
        return width * height;
    }
}

// Square "is-a" Rectangle mathematically — but BREAKS the contract in code
class Square extends Rectangle {

    @Override
    public void setWidth(int w) {
        // A square must keep width == height — so setting width forces height too
        this.width = w;
        this.height = w;   // ← breaks Rectangle's contract
    }

    @Override
    public void setHeight(int h) {
        this.width = h;
        this.height = h;   // ← breaks Rectangle's contract
    }
}

// Code that works with Rectangle breaks silently with Square:
void resize(Rectangle r) {
    r.setWidth(5);
    r.setHeight(10);
    // Expected: 5 * 10 = 50
    // If r is actually a Square: 10 * 10 = 100 ← silent bug
    assert r.area() == 50;   // FAILS when r is a Square
}
```

**The lesson:** Inheritance should be based on behavior contracts, not just "is-a" relationships in English. Fix: don't extend Rectangle — use a common `Shape` interface or compose instead of inherit.

---

### 🔬 I — Interface Segregation Principle

> "Clients should not be forced to implement methods they don't use. Split fat interfaces into smaller focused ones."

```java
// ❌ BAD: fat interface forces all implementors to provide all methods
interface Worker {
    void work();
    void eat();   // a robot doesn't eat — but forced to implement it!
}

class Robot implements Worker {
    public void work() { ... }
    public void eat() {
        throw new UnsupportedOperationException("Robots don't eat!");   // ISP violated
    }
}

// ✅ GOOD: split into focused interfaces
interface Workable {
    void work();
}

interface Eatable {
    void eat();
}

// Human implements both
class Human implements Workable, Eatable {
    public void work() { ... }
    public void eat() { ... }
}

// Robot implements only what it needs
class Robot implements Workable {
    public void work() { ... }
    // No eat() method — never forced to implement it
}
```

---

### 🔬 D — Dependency Inversion Principle (⭐ Confused With DI)

> "High-level modules should not depend on low-level modules. Both should depend on abstractions."

**Dependency Inversion Principle (DIP)** is a design principle — depend on interfaces, not concrete classes.

**Dependency Injection (DI)** is a technique — a framework provides objects to your class instead of your class creating them with `new`. Spring's `@Autowired` is DI.

**DIP motivates DI, but they are not the same thing.**

```java
// ❌ BAD: high-level class (OrderService) depends on a concrete low-level class
class OrderService {
    // Directly depends on MySQLOrderRepository — concrete class!
    // If we switch to Postgres, we must change OrderService too.
    private MySQLOrderRepository repo = new MySQLOrderRepository();

    public void placeOrder(Order order) {
        repo.save(order);
    }
}

// ✅ GOOD: both depend on an abstraction (interface)
interface OrderRepository {
    void save(Order order);
}

class OrderService {
    // Depends on the abstraction — doesn't care which DB is under it
    private final OrderRepository repo;

    // Dependency Injection: Spring provides the implementation
    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    public void placeOrder(Order order) {
        repo.save(order);
    }
}

// Multiple implementations — OrderService doesn't change when we swap
class MySQLOrderRepository implements OrderRepository { ... }
class PostgresOrderRepository implements OrderRepository { ... }
class InMemoryOrderRepository implements OrderRepository { ... }   // for tests
```

---

<a id="circuit-breaker"></a>

## 🔹 4. Circuit Breaker + Resilience4j

### 📖 Terminology

- **Circuit Breaker** — a pattern that wraps calls to a downstream service and stops forwarding requests when failure rate exceeds a threshold, giving the failing service time to recover. Named after the electrical circuit breaker that cuts power to protect devices.
- **Resilience4j** — the current standard Java library for circuit breakers, retry, rate limiting, and bulkhead patterns (replaced Netflix Hystrix, which went maintenance-only in 2018). ⚠️ Never say Hystrix in a 2026 interview.
- **Bulkhead** — a pattern (from ship bulkheads that prevent one flooded compartment from sinking the whole ship) that isolates failure by limiting the number of concurrent calls to a service.

---

### 🧠 Mental Model — The Electrical Analogy

Your electrical circuit breaker at home trips when it detects dangerous current (too many failures). While tripped (OPEN), no current flows (no calls go to the failing service). After a reset period, you test one circuit (HALF-OPEN). If it works, you restore power (CLOSED). If it fails again, trip again (back to OPEN).

**The counterintuitive naming (people always get this wrong):**
- `CLOSED` = circuit is **complete** = current flows = **healthy** ✅
- `OPEN` = circuit is **broken** = no current flows = **failing** ❌

---

### 🎨 Visual — Circuit Breaker 3 States + Transitions

```
  ┌──────────────┐       failure rate > threshold         ┌──────────────┐
  │              │ ─────────────────────────────────────► │              │
  │   CLOSED     │                                        │    OPEN      │
  │  (healthy)   │                                        │  (failing)   │
  │              │ ◄───────────────────────────────────── │              │
  └──────────────┘      probe request FAILS               └──────────────┘
          ▲                                                       │
          │             wait period expires                       │
          │                    │                                  │
          │                    ▼                                  │
          │             ┌──────────────┐                          │
          │             │              │                          │
          └─────────────│  HALF-OPEN   │ ◄────────────────────────┘
       probe            │   (testing)  │
       request          │              │
       SUCCEEDS         └──────────────┘

  CLOSED (normal traffic):
    → All requests pass through to the downstream service
    → Circuit breaker counts failures
    → If failure rate crosses threshold (e.g., 50% in last 100 calls) → trip to OPEN

  OPEN (fail-fast mode):
    → ALL requests immediately fail with CircuitBreakerOpenException
    → Downstream service is NOT called at all (protects it from load)
    → After a wait period (e.g., 30 seconds) → transition to HALF-OPEN

  HALF-OPEN (probe mode):
    → A limited number of probe requests are allowed through
    → If probes SUCCEED → circuit closes (back to CLOSED)
    → If probes FAIL → circuit opens again (back to OPEN)

KEY INVARIANT:
   CLOSED = healthy (use this, it seems backwards)
   OPEN = broken, fail-fast, don't call the downstream service
   The transition from OPEN to HALF-OPEN is time-based (wait period).
   The transition from HALF-OPEN is probe-result-based.
```

---

### 🔬 Resilience4j — What It Bundles

Resilience4j is a lightweight fault-tolerance library designed for Java 8+. It's the standard in Spring Boot projects (Spring Cloud CircuitBreaker wraps it).

```
  Resilience4j modules:
  ┌───────────────────────────────────────────────────────────┐
  │  CircuitBreaker  → CLOSED/OPEN/HALF-OPEN state machine    │
  │  Retry           → retry N times with backoff             │
  │  RateLimiter     → limit calls per time window            │
  │  Bulkhead        → limit concurrent calls                 │
  │  TimeLimiter     → timeout per call                       │
  └───────────────────────────────────────────────────────────┘
```

**Spring Boot configuration (know the key properties):**

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        failure-rate-threshold: 50          # trip at 50% failure rate
        wait-duration-in-open-state: 30s    # stay OPEN for 30 seconds
        permitted-number-of-calls-in-half-open-state: 5  # probe with 5 calls
        sliding-window-size: 100            # measure last 100 calls

  retry:
    instances:
      paymentService:
        max-attempts: 3
        wait-duration: 500ms
```

**Circuit Breaker vs Retry (asked as a distinction at EPAM):**

```
  Retry → good for: transient failure (network blip, unlikely to repeat)
         → logic: try again immediately or with backoff, likely to succeed

  Circuit Breaker → good for: sustained outage (service is down)
                  → logic: stop trying, fail fast, don't make it worse
                  → adding retry ON TOP of an open circuit wastes resources

  Combine them: retry 2-3 times (transient) THEN circuit breaker trips (sustained)
```

---

<a id="saga-cqrs"></a>

## 🔹 5. SAGA + CQRS

### 📖 Terminology

- **Distributed transaction** — a transaction that spans multiple services or databases. Traditional 2-phase commit (2PC) is not practical in microservices (too tightly coupled, blocks).
- **SAGA** — a pattern for managing distributed transactions across services without 2PC. Each service completes its local transaction and either emits an event (Choreography) or is called by an orchestrator (Orchestration). If a step fails, compensating transactions undo the previous steps.
- **Compensating transaction** — the "undo" action for a completed step. Not a rollback — the original transaction was committed; the compensation is a new transaction that reverses its effect.

---

### 🎨 Visual — SAGA: Choreography vs Orchestration

```
  SAGA — CHOREOGRAPHY (event-driven, no central brain):

  OrderService     PaymentService     InventoryService     ShippingService
      │                  │                  │                    │
      ├── order.placed ──►│                  │                    │
      │                  ├── payment.done ──►│                    │
      │                  │                  ├── items.reserved ──►│
      │                  │                  │                    ├── shipped
      │                  │                  │                    │
  Failure scenario: payment fails
  PaymentService emits payment.failed
  OrderService listens → cancels order (compensating transaction)

  ✅ Pros: decoupled, no single point of failure
  ❌ Cons: hard to track overall flow, complex debugging, event storms

  ─────────────────────────────────────────────────────────────────────────

  SAGA — ORCHESTRATION (central conductor):

                  ┌─────────────────────────┐
                  │   SAGA Orchestrator      │
                  │   (knows the full flow)  │
                  └──┬──────────┬────────────┘
                     │          │
            call 1   │          │   call 2
                     ▼          ▼
            PaymentService   InventoryService
            
  Failure scenario: InventoryService fails
  Orchestrator issues compensating call to PaymentService → reverse payment

  ✅ Pros: flow is visible in one place, easier to audit and debug
  ❌ Cons: orchestrator is a single point of failure; more coupling than choreography

KEY INVARIANT:
   Choreography = services talk to each other via events (async, decoupled)
   Orchestration = one orchestrator calls services in sequence (sync or async, centralized)
   SAGA does NOT use rollbacks — it uses compensating transactions.
```

---

### 🔬 CQRS — What It Is (and What It Is Not)

**CQRS (Command Query Responsibility Segregation)** — a pattern that separates the write side (Commands — things that change state) from the read side (Queries — things that return data). The two sides can use completely different models, databases, and can scale independently.

```
  WITHOUT CQRS:
  Client ──► [Single Service] ──► [Single Database]
             write + read together, same model for both

  WITH CQRS:
  Client ──► [Command Side]  ──► [Write Database]  (optimized for writes)
         │                        (e.g., normalized, RDBMS)
         │
         └─► [Query Side]   ──► [Read Database]   (optimized for reads)
                                 (e.g., denormalized, Elasticsearch, Redis)

  The read database is kept in sync via events or CDC (Change Data Capture —
  a technique that captures and streams every database change as an event).
```

**CQRS ≠ Event Sourcing.** This is a common conflation:

```
  CQRS: separate write model from read model. Period.
        Can use any storage, doesn't require events.

  Event Sourcing: instead of storing the current state, store a log of
                  all events that led to the current state.
                  (e.g., instead of balance=500, store: deposited 200, withdrew 100, deposited 400)

  They pair well but are independent patterns.
  A system can use CQRS without Event Sourcing.
  A system can use Event Sourcing without CQRS.
```

**When to use CQRS:**
- Read traffic far exceeds write traffic (scale them independently)
- Read and write have very different data shape needs
- Complex domain logic (DDD contexts)

**Trade-off:** eventual consistency — the read model may lag slightly behind the write model. This is the main cost.

---

<a id="api-gateway-jpa"></a>

## 🔹 6. API Gateway + N+1 Problem + JPA Traps

### 🔬 API Gateway — What It Does

**API Gateway** (a single-entry-point service that sits in front of all your microservices and handles cross-cutting concerns) is the standard entry point in any microservices architecture. Clients call the gateway; the gateway routes to the right service.

**What it handles:**
```
  Client → [API Gateway] → OrderService
                        → PaymentService
                        → UserService

  The gateway:
  ✅ Authentication / Authorization (verify JWT, check permissions)
  ✅ Rate limiting (100 req/sec per user)
  ✅ SSL termination (HTTPS ends at gateway; internal traffic can be HTTP)
  ✅ Routing (route /orders/* to OrderService, /users/* to UserService)
  ✅ Load balancing (distribute requests across multiple service instances)
  ✅ Request/response transformation
  ✅ Hides internal service topology from clients
```

Common implementations: Spring Cloud Gateway (Java), Kong, NGINX, AWS API Gateway.

---

### 🔬 The N+1 Problem (JPA — Highest-Frequency ORM Question)

**N+1 problem** — a performance issue where loading a list of N parent entities causes N additional queries to load their children. Total: 1 + N queries instead of 1 query with a JOIN.

```java
// The setup — Order has a List<Item> (lazy-loaded by default with @OneToMany)
@Entity
public class Order {
    @Id
    private Long id;

    @OneToMany(mappedBy = "order")   // LAZY by default
    private List<Item> items;
}

// The problem:
List<Order> orders = orderRepo.findAll();   // 1 query: SELECT * FROM orders (gets 100 orders)

for (Order order : orders) {
    // Each access to order.getItems() fires a NEW query!
    System.out.println(order.getItems().size());
    // 100 orders → 100 extra queries
    // Total: 1 + 100 = 101 queries ← N+1 problem
}
```

**⚠️ `@ManyToOne` is EAGER by default (the less obvious N+1 cause):**

```java
@Entity
public class Item {
    @ManyToOne   // ← EAGER by default! Loads Order every time Item is loaded
    private Order order;
}

// Loading 1000 items → 1000 ORDER queries fired automatically
List<Item> items = itemRepo.findAll();   // quiet N+1 — no lazy loading to blame
```

---

### 🔬 3 Fixes for N+1

**Fix 1 — JOIN FETCH (one big query):**

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // JOIN FETCH loads orders AND their items in a single query
    @Query("SELECT o FROM Order o JOIN FETCH o.items")
    List<Order> findAllWithItems();
}

// Generated SQL:
// SELECT o.*, i.* FROM orders o INNER JOIN items i ON o.id = i.order_id
// One query, all data.
```

**⚠️ JOIN FETCH + Pagination = broken — know this for the follow-up:**

```java
// This looks correct but is BROKEN:
@Query("SELECT o FROM Order o JOIN FETCH o.items")
Page<Order> findAllWithItems(Pageable pageable);   // ❌

// Hibernate logs: "HHH000104: firstResult/maxResults specified with collection fetch;
//                 applying in memory!"
// It pulls ALL rows from DB and paginates in Java heap → memory explosion with large datasets.

// Correct fix for pagination + fetching: use a two-query approach
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.id IN :ids")
List<Order> findWithItemsByIds(@Param("ids") List<Long> ids);

// Step 1: paginate IDs only (no JOIN FETCH, no blowup)
@Query("SELECT o.id FROM Order o")
Page<Long> findOrderIds(Pageable pageable);

// Step 2: load those IDs with JOIN FETCH
```

**Fix 2 — @EntityGraph (declarative, cleaner):**

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items"})   // eagerly load items for this query only
    List<Order> findAll();
}
```

**Fix 3 — @BatchSize (subselect batching):**

```java
@Entity
public class Order {
    @Id
    private Long id;

    @OneToMany(mappedBy = "order")
    @BatchSize(size = 25)   // instead of N queries, fires N/25 batch queries
    private List<Item> items;
}

// For 100 orders: instead of 100 queries → fires 4 queries (batch of 25 each)
// Less total queries, no IN-MEMORY pagination risk
```

---

<a id="sql"></a>

## 🔹 7. SQL Window Functions

### 📖 Terminology

- **Window function** — an SQL function that operates on a set of rows related to the current row (its "window"), without collapsing rows the way `GROUP BY` does. You get aggregate-like calculations while keeping individual row detail.
- **PARTITION BY** — divides the result set into partitions (groups). The window function resets for each partition. Like `GROUP BY` but without collapsing rows.
- **ORDER BY (in OVER clause)** — defines the order within each partition for the window function's calculation.

---

### 🎨 Visual — ROW\_NUMBER vs RANK vs DENSE\_RANK

These are the three most asked window functions. The difference is only visible when there are ties.

```
  Employees by salary (Department: Engineering):

  Name    │ Salary │ ROW_NUMBER │ RANK │ DENSE_RANK
  ─────────┼────────┼────────────┼──────┼────────────
  Alice   │ 90000  │     1      │  1   │     1
  Bob     │ 85000  │     2      │  2   │     2
  Carol   │ 85000  │     3      │  2   │     2      ← tie with Bob
  Dave    │ 80000  │     4      │  4   │     3      ← gap in RANK, no gap in DENSE_RANK
  Eve     │ 75000  │     5      │  5   │     4

  ROW_NUMBER:   always unique (1,2,3,4,5) — ties are broken arbitrarily
  RANK:         ties get the same rank, next rank SKIPS (1,2,2,4,5) — "like Olympic medals"
  DENSE_RANK:   ties get the same rank, next rank does NOT skip (1,2,2,3,4) — "no gaps"

KEY INVARIANT:
   For "find Nth highest" problems → use DENSE_RANK.
   RANK has gaps — "2nd highest" with RANK may skip over a salary level if there are ties.
```

---

### 🔬 Problem 1 — Salary Rank Per Department (Asked at EPAM)

```sql
SELECT
    name,
    department,
    salary,
    RANK() OVER (
        PARTITION BY department        -- rank is reset for each department
        ORDER BY salary DESC           -- highest salary gets rank 1
    ) AS salary_rank
FROM employees;
```

---

### 🔬 Problem 2 — 2nd Highest Salary (Stock Interview Question)

```sql
-- Method 1: DENSE_RANK (handles ties correctly)
SELECT name, salary
FROM (
    SELECT
        name,
        salary,
        DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employees
) ranked
WHERE rnk = 2;

-- Why DENSE_RANK and not RANK?
-- If two people share the highest salary, RANK gives them both rank 1
-- and rank 2 is skipped → no row with rank=2 → query returns empty!
-- DENSE_RANK gives both rank 1, and the next person gets rank 2 → correct.

-- Method 2: subquery (simpler, but doesn't handle ties consistently)
SELECT MAX(salary) FROM employees
WHERE salary < (SELECT MAX(salary) FROM employees);
```

---

### 🔬 Problem 3 — Running Total Per Department

```sql
-- SUM() as a window function: running total within each department
SELECT
    name,
    department,
    salary,
    SUM(salary) OVER (
        PARTITION BY department
        ORDER BY name
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_total
FROM employees;

-- "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW" means:
-- for each row, sum from the first row in the partition up to and including this row
```

---

### 🔬 JOINS — Quick Reference (Expect the Interviewer to Draw This)

```
  Table A: [1, 2, 3]    Table B: [2, 3, 4]

  INNER JOIN:            ONLY matching rows       → [2, 3]
  LEFT JOIN:             All A + matching B        → [1, 2, 3]  (1 has NULL for B columns)
  RIGHT JOIN:            Matching A + all B        → [2, 3, 4]  (4 has NULL for A columns)
  FULL OUTER JOIN:       All rows from both        → [1, 2, 3, 4]
  CROSS JOIN:            Every combination         → [1×2, 1×3, 1×4, 2×2, 2×3, ...]
```

---

## 🧾 TL;DR — The 12 Things to Say Cold Tomorrow

1. **Bill Pugh Singleton:** inner static Holder class — lazy, thread-safe (JVM class loading), no synchronized needed
2. **Enum Singleton:** immune to reflection, serialization, and cloning — Effective Java recommends it
3. **Double-checked locking:** the `instance` field MUST be `volatile` (prevents partial construction visibility)
4. **Circuit Breaker states:** CLOSED = healthy, OPEN = fail-fast, HALF-OPEN = probe — CLOSED means good!
5. **Resilience4j, not Hystrix:** Hystrix is in maintenance mode since 2018; Resilience4j bundles CircuitBreaker + Retry + RateLimiter + Bulkhead + TimeLimiter
6. **SAGA Choreography:** services communicate via events — decoupled, no central coordinator
7. **SAGA Orchestration:** central orchestrator directs each step — visible flow, single point of control
8. **CQRS ≠ Event Sourcing:** CQRS separates read/write model; Event Sourcing stores event log instead of state — independent patterns that pair well
9. **N+1 root cause:** `@OneToMany` is LAZY (fine), `@ManyToOne` is EAGER by default (hidden N+1 source)
10. **JOIN FETCH + pagination = broken:** Hibernate pulls all rows into memory (HHH000104) — use @BatchSize or two-query approach
11. **DENSE_RANK for Nth highest:** RANK has gaps at ties, DENSE_RANK does not — use DENSE_RANK when asked for "2nd highest"
12. **DIP ≠ DI:** Dependency Inversion Principle = depend on abstractions; Dependency Injection = Spring wires implementations for you

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Sep 2026 | Day 3 notes created. Resilience4j used (not Hystrix — maintenance mode). Singleton: all 4 flavors, 3 breaking techniques, Enum immunity. NESTED propagation: savepoint-dependent, JpaTransactionManager rejects. JOIN FETCH + pagination broken (HHH000104). @ManyToOne EAGER default added. RANK vs DENSE_RANK vs ROW_NUMBER table. CQRS ≠ Event Sourcing called out explicitly. |
