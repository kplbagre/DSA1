# Chapter 4 — JPA, Transactions, and Lazy Loading

> **Track context:** Chapter 4 of 4 in the Spring Foundation DeepDive series (`../spring-10-hour-plan.md`). Covers the persistence layer: JPA as a contract, Hibernate as the implementation, Spring Data JPA as the convenience wrapper, `@Transactional` propagation modes, lazy loading traps, and the N+1 problem.

---

## 📖 Prerequisites

You should have absorbed Chapters 1–3:
- Spring's IoC container creates and wires beans (Chapter 2).
- AOP proxies wrap beans to add `@Transactional` behavior. Self-calls bypass the proxy (Chapter 2).
- DispatcherServlet routes requests to `@RestController` methods (Chapter 3).
- Spring Boot auto-configures `EntityManagerFactory` and `TransactionManager` when `spring-data-jpa` is on the classpath (Chapter 3).

The question Chapter 4 answers: *"How does `@Transactional` manage the persistence context and the database transaction, and what breaks when you access lazy collections outside of it?"*

---

## 🧠 Mental model

> **JPA is the contract** (Java standard — interfaces, annotations, rules). **Hibernate is the implementation** (the code that actually talks to the database). **Spring Data JPA** wraps Hibernate to remove boilerplate — you declare a `JpaRepository<Order, Long>` interface, Spring generates the implementation at startup. You never write SQL for basic CRUD.
>
> **`@Transactional` controls two things simultaneously:** the database transaction (BEGIN → COMMIT/ROLLBACK) AND the persistence context (Hibernate's first-level cache that tracks dirty entities). When the method exits normally, the transaction commits AND dirty entities are flushed to the database. When it exits via RuntimeException, the transaction rolls back.
>
> **Lazy loading works ONLY inside a persistence context** (which lives for the duration of the `@Transactional` method). If you return a lazily-loaded entity from a `@Transactional` method and then access its children outside, the persistence context is closed → `LazyInitializationException`.

Three corollaries:

1. **`@Transactional` on a service method** — persistence context opens at method entry, closes at exit. All lazy loads within the method work. Outside = crash.
2. **N+1 problem** — load 100 orders, then access each order's items → 1 query for orders + 100 queries for items. Fix: `JOIN FETCH` or `@EntityGraph`.
3. **Propagation matters** — `REQUIRED` (default) joins the existing transaction. `REQUIRES_NEW` suspends the existing and creates a fresh one. `NESTED` creates a savepoint.

If you can verbalize those three points without notes, you have Chapter 4.

---

## 🪜 Concept build-up

---

### Part 1 — JPA entities and Spring Data repositories

```java
// Entity — maps to a database table
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();
    // LAZY = items are NOT loaded from DB until you call order.getItems()
    // This is the DEFAULT for @OneToMany and @ManyToMany

    // @ManyToOne defaults to EAGER — this is a common surprise
    // @ManyToOne(fetch = FetchType.LAZY) — always set this explicitly
}

// Repository — Spring Data generates the implementation
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Derived query — Spring parses the method name into SQL
    List<Order> findByStatus(OrderStatus status);

    // Custom JPQL
    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    // Native SQL (when JPQL isn't enough)
    @Query(value = "SELECT * FROM orders WHERE status = ?1", nativeQuery = true)
    List<Order> findByStatusNative(String status);
}
```

> **What the JVM is actually doing:** At startup, Spring Data scans for interfaces extending `JpaRepository`. For each, it creates a JDK dynamic proxy that delegates to `SimpleJpaRepository` — a class Spring provides that has default implementations for `save()`, `findById()`, `findAll()`, `delete()`, etc. Derived queries are parsed from the method name at startup and validated against the entity's metamodel — if you typo a field name (`findByStatuz`), it fails at startup, not at runtime.

---

### Part 2 — `@Transactional` mechanics

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional   // opens persistence context + DB transaction on entry
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setStatus(OrderStatus.PENDING);
        order.setItems(request.items().stream()
            .map(item -> new OrderItem(item.product(), item.quantity(), order))
            .toList());

        return orderRepository.save(order);
        // Hibernate tracks this entity in the persistence context
        // On method exit: flush dirty entities → SQL INSERT → COMMIT
    }

    @Transactional(readOnly = true)   // optimization: no dirty checking, no flush
    public Order getOrder(Long id) {
        return orderRepository.findByIdWithItems(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
```

**What `@Transactional` does behind the proxy:**

```
  External call → OrderService PROXY → real OrderService

  PROXY does:
  1. TransactionManager.begin()          ← DB transaction starts
  2. EntityManager opens (persistence context created)
  3. Delegates to YOUR method            ← your code runs
  4a. Normal exit:
      → EntityManager.flush()            ← dirty entities → SQL
      → TransactionManager.commit()      ← DB transaction commits
  4b. RuntimeException exit:
      → TransactionManager.rollback()    ← DB transaction rolls back
  5. EntityManager closes                ← persistence context closed
     → all managed entities become DETACHED
     → lazy collections on detached entities → LazyInitializationException
```

---

### Part 3 — Transaction propagation

```
  REQUIRED (default):
  ┌─────────────────────────────────────────────────┐
  │  Caller has existing TX?                        │
  │    YES → join it (run in same transaction)      │
  │    NO  → create a new one                       │
  │                                                 │
  │  Use case: most service methods.                │
  │  If the caller rolls back, this rolls back too. │
  └─────────────────────────────────────────────────┘

  REQUIRES_NEW:
  ┌─────────────────────────────────────────────────┐
  │  ALWAYS creates a fresh transaction.            │
  │  If caller has existing TX → SUSPEND it.        │
  │  This TX commits/rolls back INDEPENDENTLY.      │
  │                                                 │
  │  Use case: audit logs — should commit even if   │
  │  the parent operation rolls back.               │
  └─────────────────────────────────────────────────┘

  NESTED:
  ┌─────────────────────────────────────────────────┐
  │  Creates a SAVEPOINT within the existing TX.    │
  │  If nested fails → rollback to savepoint only.  │
  │  Parent TX continues. If parent rolls back →    │
  │  nested is rolled back too (it's part of it).   │
  │                                                 │
  │  Use case: batch processing — one item fails    │
  │  but others continue.                           │
  │  ⚠️ Not supported by all DB drivers.            │
  └─────────────────────────────────────────────────┘
```

```java
// REQUIRES_NEW example: audit log that must persist even on rollback
@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action, Long entityId) {
        auditRepository.save(new AuditEntry(action, entityId, Instant.now()));
        // This commits in its OWN transaction
        // Even if the caller's transaction rolls back, this audit entry persists
    }
}

@Service
public class OrderService {

    @Transactional
    public void processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PROCESSING);

        auditService.logAction("PROCESS", orderId);   // separate TX → commits independently

        if (paymentFails()) {
            throw new PaymentException();   // parent TX rolls back
            // But audit entry ALREADY committed in its own TX
        }
    }
}
```

---

### Part 4 — Lazy loading and LazyInitializationException

```java
// The trap:
@Transactional
public Order getOrder(Long id) {
    return orderRepository.findById(id).orElseThrow();
    // Items are LAZY — not loaded yet
    // Persistence context closes when this method returns
}

// In the controller (OUTSIDE @Transactional):
Order order = orderService.getOrder(42);
List<OrderItem> items = order.getItems();   // BOOM!
// LazyInitializationException: could not initialize proxy — no Session
// The persistence context is closed. Hibernate can't issue the SQL.
```

**Three fixes:**

```java
// Fix 1: JOIN FETCH in the query — load everything in one SQL
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") Long id);
// One query: SELECT o.*, i.* FROM orders o JOIN order_items i ON o.id = i.order_id WHERE o.id = ?
// Items are loaded EAGERLY in this specific query

// Fix 2: @EntityGraph — declarative eager loading per query
@EntityGraph(attributePaths = {"items"})
Optional<Order> findById(Long id);   // overrides the default LAZY fetch

// Fix 3: @Transactional on the controller/caller (less clean but works)
@Transactional(readOnly = true)
@GetMapping("/orders/{id}")
public OrderResponse getOrder(@PathVariable Long id) {
    Order order = orderService.getOrder(id);
    return new OrderResponse(order.getId(), order.getItems().size());
    // Persistence context still open — lazy load works
}
```

---

### Part 5 — The N+1 problem

```
  THE N+1 PROBLEM:

  Query 1: SELECT * FROM orders WHERE status = 'PENDING'
  → Returns 100 orders. Items NOT loaded (LAZY).

  For each order, access order.getItems():
  Query 2:  SELECT * FROM order_items WHERE order_id = 1
  Query 3:  SELECT * FROM order_items WHERE order_id = 2
  ...
  Query 101: SELECT * FROM order_items WHERE order_id = 100

  TOTAL: 1 + 100 = 101 queries. Should be 1 or 2.
```

**Fixes:**

```java
// Fix 1: JOIN FETCH — one query loads everything
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :status")
List<Order> findByStatusWithItems(@Param("status") OrderStatus status);
// 1 query. But: JOIN FETCH + pagination = HHH000104 warning
// (Hibernate loads ALL rows into heap, then paginates in memory — OOM risk)

// Fix 2: @BatchSize — N+1 becomes N/B + 1
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
@BatchSize(size = 50)   // load items in batches of 50 orders
private List<OrderItem> items;
// Instead of 100 individual queries: 2 queries (100/50 = 2 batches)
// SELECT * FROM order_items WHERE order_id IN (1,2,3,...,50)
// SELECT * FROM order_items WHERE order_id IN (51,52,...,100)

// Fix 3: Two-query approach (best for pagination)
// Query 1: fetch order IDs with pagination
// Query 2: fetch orders + items for those IDs with JOIN FETCH
```

**⚠️ `@ManyToOne` is EAGER by default — hidden N+1 source:**

```java
@Entity
public class OrderItem {
    @ManyToOne   // DEFAULT: fetch = FetchType.EAGER
    private Product product;
    // Loading 100 OrderItems → 100 individual queries for Product
    // Even though you didn't ask for Products!
}

// ✅ Fix: always set @ManyToOne to LAZY
@ManyToOne(fetch = FetchType.LAZY)
private Product product;
```

---

## ❌/✅ Common mistakes

```java
// ❌ Mistake 1: @Transactional on private method — proxy can't intercept
@Transactional
private void processInternal() { ... }   // SILENTLY does nothing — no error, no TX

// ✅ Fix: make it public
@Transactional
public void processInternal() { ... }

// ❌ Mistake 2: Self-call bypasses proxy (same trap as Chapter 2)
@Service
public class OrderService {
    @Transactional
    public void processAll() {
        for (Order order : orders) {
            this.processSingle(order);   // SELF-CALL — bypasses proxy
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingle(Order order) { ... }
    // processSingle NEVER gets its own transaction — the proxy is bypassed
}

// ✅ Fix: inject self or move to a different bean
@Autowired private OrderService self;
self.processSingle(order);   // goes through the proxy

// ❌ Mistake 3: Checked exception doesn't roll back
@Transactional
public void transfer() throws InsufficientFundsException {
    debit(amount);
    throw new InsufficientFundsException();   // checked → TX COMMITS — debit is permanent!
}

// ✅ Fix: rollbackFor
@Transactional(rollbackFor = InsufficientFundsException.class)
```

---

## 🎨 Visual — REQUIRED vs REQUIRES_NEW

```
  REQUIRED (default) — joins existing:

  Caller TX ──────────────────────────────────────────
       │                                              │
       │  methodA()                                   │
       │    ├── methodB() [REQUIRED]                  │
       │    │   └── runs in SAME TX                   │
       │    │       if B fails → A+B both roll back   │
       │    └── continues                             │
       │                                              │
  ─────┴──────────────────────── COMMIT or ROLLBACK ──


  REQUIRES_NEW — independent:

  Caller TX ─────────────────── SUSPENDED ─────── RESUMED ───
       │                    │               │          │
       │  methodA()         │  methodB()    │          │
       │    ├── calls B ───►│  [REQ_NEW]    │          │
       │    │               │  own TX       │          │
       │    │               │  COMMIT       │──►       │
       │    └── continues ──┘               │          │
       │                                               │
  ─────┴───────────────────────────── COMMIT or ROLLBACK


KEY INVARIANT:
   REQUIRED: one transaction for the entire call chain.
   REQUIRES_NEW: separate transaction — commits independently.
   Use REQUIRES_NEW when the inner operation MUST persist
   even if the outer operation fails (audit logs, event publishing).
```

---

## 🏢 Where you've seen this in your app

- Every `@Service` with `@Transactional` follows this exact pattern — the proxy manages BEGIN/COMMIT/ROLLBACK around your method.
- `@ManyToOne(fetch = FetchType.LAZY)` on entity relationships prevents loading the entire object graph when only the parent is needed.
- `JOIN FETCH` in custom `@Query` methods eliminates N+1 queries that would otherwise fire 100+ SQL statements per API call.
- `REQUIRES_NEW` on audit/logging services ensures audit trails persist even when the business transaction rolls back.

---

## 🎤 Interview Q&A

**Q1. What is the difference between REQUIRED and REQUIRES_NEW?**

> `REQUIRED` (default): if a transaction already exists, join it. If not, create one. The inner and outer methods share one transaction — if either throws RuntimeException, both roll back. `REQUIRES_NEW`: always create a fresh, independent transaction. If a transaction exists, it's suspended until the new one finishes. The inner transaction commits or rolls back independently of the outer. Use case: audit logging that must persist even if the parent operation rolls back.

**Q2. What is LazyInitializationException and how do you fix it?**

> It occurs when you access a lazily-loaded collection on a JPA entity AFTER the persistence context is closed — typically outside a `@Transactional` method. Hibernate needs an active session to issue the SQL for the lazy load, but the session was closed when the `@Transactional` method returned. Three fixes: `JOIN FETCH` in the query (loads everything in one SQL — best for single-entity lookups), `@EntityGraph` (declarative eager loading per query), or making the caller `@Transactional(readOnly = true)` so the persistence context stays open.

**Q3. What is the N+1 problem? How do you detect and fix it?**

> Loading 100 parent entities, then accessing a lazy child collection on each, fires 1 query for parents + 100 queries for children = 101 queries. This is the N+1 problem. Detect it by enabling Hibernate SQL logging (`spring.jpa.show-sql=true`) or using a tool like `datasource-proxy`. Fix with `JOIN FETCH` (one query gets everything — but breaks pagination), `@BatchSize(size = 50)` (batches child queries — N/50 + 1 instead of N + 1), or a two-query approach (query 1 gets paginated parent IDs, query 2 fetches parents + children for those IDs).

**Q4. Why does `@Transactional` not roll back on checked exceptions?**

> Spring follows the EJB convention: checked exceptions represent expected business outcomes (e.g., "insufficient funds"), not system failures. The assumption is that a business outcome — even negative — is a valid transaction result. Only unexpected failures (RuntimeException, Error) indicate corruption. Fix: add `rollbackFor = MyCheckedException.class`, or make your exceptions unchecked. Spring's own exceptions (`DataAccessException`, etc.) are all unchecked for this reason.

---

## 🧾 TL;DR

- **JPA = contract, Hibernate = implementation, Spring Data = convenience wrapper** (generates repository implementations).
- **`@Transactional`** = DB transaction + persistence context. Opens on method entry, closes on exit.
- **Rollback:** RuntimeException + Error → yes. Checked exception → NO (unless `rollbackFor`).
- **REQUIRED** = join existing TX. **REQUIRES_NEW** = independent TX. **NESTED** = savepoint.
- **LazyInitializationException** = accessing lazy collection outside `@Transactional`. Fix: JOIN FETCH / @EntityGraph.
- **N+1 problem** = 1 + N queries for parent + children. Fix: JOIN FETCH / @BatchSize / two-query.
- **`@ManyToOne` is EAGER by default** — always set `fetch = FetchType.LAZY`.
- **Self-call + private method + checked exception** = the three `@Transactional` traps.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Chapter 4 created as Note #13 (Phase 3) of the JavaBackend KB completion roadmap. Covers Spring plan Day 9: JPA entities, Spring Data JPA (derived queries, @Query, native SQL), @Transactional mechanics (proxy flow, readOnly), propagation (REQUIRED/REQUIRES_NEW/NESTED with ASCII visual), lazy loading + LazyInitializationException (3 fixes), N+1 problem (3 fixes + @ManyToOne EAGER trap), @BatchSize, JOIN FETCH + pagination warning. Follows Spring 8-section arc format. |
