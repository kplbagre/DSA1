# Day 2 — Spring Traps: @Transactional · Isolation · Bean Scope · Proxy Model
### EPAM Interview Prep · 4 Hours · Sep 19, 2026

> **Who this is for:** A developer who uses `@Transactional` and Spring beans daily but has never needed to explain *why* they sometimes silently fail. All failure modes are invisible in normal usage — which is exactly why EPAM asks about them.

> **What you will be able to do after this:** Name the three @Transactional traps cold, explain all isolation levels with their anomalies, explain why a prototype bean injected into a singleton behaves wrong, draw the proxy call chain on a whiteboard.

---

## 🧾 Index — Jump to Any Section

| # | Topic | Time |
| --- | --- | --- |
| [1. @Transactional — The Three Traps](#transactional-traps) | Self-invocation, private method, checked exception — 3 silent failures | ~45 min |
| [2. Transaction Propagation](#tx-propagation) | REQUIRED, REQUIRES\_NEW, NESTED — what each does, when each breaks | ~30 min |
| [3. Isolation Levels](#isolation-levels) | 4 levels, 3 anomalies, DB-specific reality | ~30 min |
| [4. Bean Scope + Lifecycle](#bean-scope) | Singleton gotchas, prototype trap, lifecycle order | ~35 min |
| [5. Spring Proxy Model + AOP](#proxy-aop) | How CGLIB wraps your beans, why self-invocation bypasses it, AOP terms | ~40 min |

---

<a id="transactional-traps"></a>

## 🔹 1. @Transactional — The Three Traps

### 📖 Terminology

- **Transaction** — a unit of work that either completes entirely (commit) or is undone entirely (rollback). The classic bank transfer: debit account A and credit account B must both succeed or both fail.
- **Spring Proxy** — an auto-generated subclass (or interface implementation) that Spring wraps around your bean. When a caller invokes a method, they hit the proxy first. The proxy runs pre/post logic (open transaction, commit, rollback) and then delegates to your real method.
- **Self-invocation** — when a method in class A calls another method in the same class A directly (via `this.method()`), bypassing the proxy entirely.

---

### 🧠 Mental Model — The Security Desk Analogy

Imagine your `@Service` class is an office with a **security desk at the entrance** (the Spring proxy). Every visitor from outside must pass through the desk. The desk checks credentials, opens a transaction, and escorts them to your method.

But if you (someone already inside) walk from one room to another inside the same office — you never pass through the security desk. The desk has no idea you moved.

**This is why `@Transactional` on a method called from the same class has no effect.**

---

### 🎨 Visual — Normal Call vs Self-Invocation

```
  NORMAL CALL (works correctly):

  CallerClass                  Spring Proxy                  MyService
      │                             │                             │
      ├──── myService.doWork() ────►│                             │
      │                             │ open transaction            │
      │                             ├────── doWork() ────────────►│
      │                             │                             │ do DB stuff
      │                             │◄──── return ────────────────┤
      │                             │ commit / rollback           │
      │◄──── return ────────────────┤                             │


  SELF-INVOCATION (proxy is bypassed):

  MyService (real class, no proxy)
      │
      ├── doWork() {
      │       ...
      │       this.helperMethod()   ← goes DIRECTLY to real class, skips proxy!
      │   }
      │
      └── @Transactional
          helperMethod() {           ← @Transactional here is IGNORED
              ...
          }

  The transaction on helperMethod() never opens.
  No error. No warning. Silent failure.

KEY INVARIANT:
   @Transactional (and @Async, @Cacheable) only works when the call
   comes THROUGH the proxy — i.e., from another bean. Never from this.method().
```

---

### 🔬 Trap 1 — Self-Invocation (Most-Asked at EPAM)

**Steps in plain English:**

1. Spring creates a proxy subclass of your service.
2. Callers from outside the class call methods on the proxy.
3. The proxy applies @Transactional logic, then delegates to your real method.
4. If your real method calls another method in the same class using `this.otherMethod()`, the call goes directly to the real object — the proxy is skipped.
5. Any @Transactional on `otherMethod` is silently ignored.

```java
@Service
public class OrderService {

    // No @Transactional here
    public void placeOrder(Order order) {
        validate(order);
        // Calls saveOrder() on "this" — bypasses the proxy!
        // The @Transactional on saveOrder() is IGNORED
        this.saveOrder(order);
    }

    @Transactional   // ← silently does nothing when called from placeOrder()
    public void saveOrder(Order order) {
        orderRepo.save(order);
    }
}
```

**Fix:** Move `saveOrder()` into a separate Spring bean and inject it.

```java
@Service
public class OrderService {

    private final OrderPersistenceService persistenceService;   // separate bean

    public void placeOrder(Order order) {
        validate(order);
        // Now the call goes through the proxy → @Transactional works
        persistenceService.saveOrder(order);
    }
}

@Service
public class OrderPersistenceService {

    @Transactional   // ← now this works because the call comes from outside
    public void saveOrder(Order order) {
        orderRepo.save(order);
    }
}
```

---

### 🔬 Trap 2 — Private Method

```java
@Service
public class PaymentService {

    @Transactional   // ← silently does nothing
    private void processPayment(Payment p) {
        // This runs WITHOUT a transaction open
        paymentRepo.save(p);
    }
}
```

**Why:** Spring's CGLIB proxy works by generating a **subclass** of your class. A subclass cannot override private methods (private = not visible to subclasses). So the proxy cannot intercept the call.

No error is thrown. The method runs fine — just without a transaction.

**Fix:** Make the method `public` (or at minimum `protected`).

> **Note:** `@Async` and `@Cacheable` have the same limitation — both rely on the proxy, both ignore private methods.

---

### 🔬 Trap 3 — Checked Exceptions Don't Rollback

```java
@Transactional
public void transfer(Account from, Account to, int amount) throws InsufficientFundsException {
    debit(from, amount);
    credit(to, amount);   // if this throws InsufficientFundsException...
    // ← Spring does NOT rollback the debit! The checked exception is ignored.
}
```

**Why:** @Transactional's default rollback policy:
- ✅ Rolls back on `RuntimeException` (unchecked) and `Error`
- ❌ Does NOT roll back on checked exceptions (like `IOException`, `SQLException`, custom checked exceptions)

**This is because Java's exception hierarchy says checked exceptions are "expected business conditions," not bugs. Spring follows this convention.**

**Fix:** Be explicit.

```java
// Option 1: tell Spring which checked exception to rollback on
@Transactional(rollbackFor = InsufficientFundsException.class)
public void transfer(Account from, Account to, int amount) throws InsufficientFundsException {
    ...
}

// Option 2: rollback on ALL throwables
@Transactional(rollbackFor = Exception.class)
public void transfer(...) throws Exception {
    ...
}
```

---

### ✅ Oral Check — Say These Without Notes

1. "What happens if I call a @Transactional method from within the same class?" — proxy bypass, @Transactional ignored, no error
2. "Why does @Transactional on a private method do nothing?" — proxy is a subclass, can't override private methods
3. "I have @Transactional on a method that throws IOException. Does it rollback?" — no, must add `rollbackFor = IOException.class`

---

<a id="tx-propagation"></a>

## 🔹 2. Transaction Propagation

### 📖 What is Propagation?

**Transaction propagation** (the rule that defines what happens to an existing transaction when one @Transactional method calls another @Transactional method) is configured as `@Transactional(propagation = ...)`.

The question it answers: "There's already a transaction running when I get called. Should I join it, create a new one, or refuse to run without one?"

---

### 🎨 Visual — The Three You Must Know

```
  Scenario: MethodA() calls MethodB() (both @Transactional)

  ─────────────────────────────────────────────────────────────────────

  REQUIRED (default):
  "Join the existing transaction. If none exists, start one."

  MethodA opens TX1 ─────────────────────────────────►
                       MethodB JOINS TX1 ──────────────►
                          (same transaction, same commit/rollback)
  ◄──────────────────────────────────────────────────── TX1 commits

  Impact: If MethodB throws, TX1 rolls back — including MethodA's work.

  ─────────────────────────────────────────────────────────────────────

  REQUIRES_NEW:
  "Always start a fresh transaction. SUSPEND the current one."

  MethodA opens TX1 ─────────────────────────────────►
                       TX1 SUSPENDED
                       MethodB opens TX2 (new, independent)
                       TX2 commits or rolls back independently
                       TX1 RESUMED ───────────────────────────►
                          TX1 continues, commits independently

  Impact: MethodB's failure does NOT automatically rollback MethodA.
  Use case: audit logging — write an audit record even if the main tx fails.

  ─────────────────────────────────────────────────────────────────────

  NESTED:
  "Run inside a savepoint of the current transaction."

  MethodA opens TX1 ─────────────────────────────────►
                       SAVEPOINT created
                       MethodB runs inside TX1 ──────────►
                       If MethodB fails → ROLLBACK TO SAVEPOINT
                       MethodA can still decide to commit

  Impact: MethodB can fail and be rolled back without losing MethodA's work.
  ⚠️ JpaTransactionManager does NOT support NESTED (needs savepoints, JPA
      doesn't expose them). Only works with DataSourceTransactionManager.

KEY INVARIANT:
   REQUIRED = one shared fate (same transaction)
   REQUIRES_NEW = two independent fates (separate transactions)
   NESTED = partial rollback (savepoint — only with JDBC, not JPA)
```

---

### 📊 All Propagation Values (Know the Top 3 Cold — Rest for Reference)

| Propagation | Existing TX exists | No TX exists |
| --- | --- | --- |
| `REQUIRED` ⭐ | Join it | Start new |
| `REQUIRES_NEW` ⭐ | Suspend it, start new | Start new |
| `NESTED` ⭐ | Create savepoint inside it | Start new (like REQUIRED) |
| `SUPPORTS` | Join it | Run without TX |
| `NOT_SUPPORTED` | Suspend it, run without TX | Run without TX |
| `MANDATORY` | Join it | Throw exception |
| `NEVER` | Throw exception | Run without TX |

**When asked verbally:** Describe REQUIRED, REQUIRES\_NEW, NESTED. For the others, "I know they exist, but they're rarely used in typical backend work."

---

<a id="isolation-levels"></a>

## 🔹 3. Isolation Levels

### 📖 What is Isolation?

**Transaction isolation** (the degree to which one transaction is protected from the side-effects of other concurrent transactions) is the 'I' in ACID (Atomicity, Consistency, Isolation, Durability — the four properties that guarantee reliable database transactions).

There are three anomalies (bad things that can happen when transactions overlap) and four levels that progressively prevent them.

---

### 📖 The Three Anomalies — Understand These First

**Anomaly 1: Dirty Read**
```
Thread 1: UPDATE salary = 9000 WHERE id = 1   (not committed yet)
Thread 2: SELECT salary WHERE id = 1           → reads 9000  (dirty! uncommitted data)
Thread 1: ROLLBACK                              → salary reverts to 8000
Thread 2: made a decision based on 9000 which never existed
```

**Anomaly 2: Non-Repeatable Read**
```
Thread 1: SELECT salary WHERE id = 1           → reads 8000
Thread 2: UPDATE salary = 9000 WHERE id = 1   → commits
Thread 1: SELECT salary WHERE id = 1           → reads 9000  (different from first read!)
Thread 1: same query, same transaction, different result
```

**Anomaly 3: Phantom Read**
```
Thread 1: SELECT COUNT(*) WHERE dept = 'IT'    → 5 rows
Thread 2: INSERT new IT employee               → commits
Thread 1: SELECT COUNT(*) WHERE dept = 'IT'    → 6 rows  (new "phantom" row appeared)
Thread 1: same WHERE clause, different number of rows
```

**Memory hook:**
- Dirty read = reading someone else's uncommitted mess
- Non-repeatable read = same row, different value (UPDATE in between)
- Phantom read = same query, different row count (INSERT/DELETE in between)

---

### 🎨 Visual — Isolation Levels + What Each Prevents

```
  Increasing isolation level → fewer anomalies → more locking → slower

  Level               │ Dirty Read │ Non-Repeatable │ Phantom Read
  ────────────────────┼────────────┼────────────────┼──────────────
  READ_UNCOMMITTED    │     ✗      │       ✗        │      ✗
  READ_COMMITTED      │     ✓      │       ✗        │      ✗
  REPEATABLE_READ     │     ✓      │       ✓        │      ✗ *
  SERIALIZABLE        │     ✓      │       ✓        │      ✓
  
  ✓ = prevents that anomaly   ✗ = anomaly can still happen
  * = Standard SQL says REPEATABLE_READ allows phantoms.
      BUT MySQL InnoDB and PostgreSQL REPEATABLE_READ both
      prevent phantoms via implementation-specific mechanisms
      (InnoDB: gap locks; Postgres: MVCC snapshot isolation).

  Spring's default: Isolation.DEFAULT = use whatever the DB uses
    → Postgres: READ_COMMITTED (JDBC default for Postgres)
    → MySQL:    REPEATABLE_READ (MySQL's own default)

KEY INVARIANT:
   "Which isolation level does your app need?" — usually READ_COMMITTED
   (Postgres default) is enough for most backends. SERIALIZABLE is only
   needed when you need perfect linearizability (financial critical paths).
```

---

### 🔬 Setting Isolation in Spring

```java
// Set isolation level explicitly
@Transactional(isolation = Isolation.READ_COMMITTED)
public Account getBalance(long accountId) {
    return accountRepo.findById(accountId).orElseThrow();
}

// Default — uses whatever the database is configured to use
@Transactional(isolation = Isolation.DEFAULT)
public void processOrder(Order order) {
    ...
}
```

**Interviewer follow-up you should be ready for:**
> "If you use Isolation.REPEATABLE_READ on Postgres, will it prevent phantom reads?"

**Answer:** Yes — Postgres's REPEATABLE_READ is implemented using MVCC (Multi-Version Concurrency Control — a technique where each transaction sees a consistent snapshot of the data as it was at transaction start, not live rows). A phantom can't appear because the snapshot was taken when the transaction started. The ANSI standard says REPEATABLE_READ allows phantoms, but Postgres goes beyond the standard.

---

<a id="bean-scope"></a>

## 🔹 4. Bean Scope + Lifecycle

### 📖 Terminology

- **Bean** — a Java object that Spring creates, configures, and manages. You declare a bean with `@Component`, `@Service`, `@Repository`, or `@Bean` in a `@Configuration` class. Spring's IoC container owns the lifecycle.
- **IoC (Inversion of Control)** — instead of your code creating objects with `new`, you declare what you need and the framework provides them. Spring's `ApplicationContext` is the IoC container.
- **ApplicationContext** — Spring's container (think: the registry/factory that knows about all your beans and their dependencies).

---

### 🎨 Visual — The 4 Bean Scopes

```
  SINGLETON (default — ⭐ most important to know):
  ┌─────────────────────────────────────────────────────┐
  │                  ApplicationContext                  │
  │                                                     │
  │   [MyService] ←── shared by ALL callers             │
  │                                                     │
  │   Thread 1 ──────────────────────┐                  │
  │   Thread 2 ──────────────────────┼──► [MyService]  │
  │   Thread 3 ──────────────────────┘                  │
  └─────────────────────────────────────────────────────┘
  One instance. All threads share it.
  ⚠️ NOT thread-safe by default. Mutable fields → race conditions.

  ─────────────────────────────────────────────────────────────────

  PROTOTYPE:
  ┌─────────────────────────────────────────────────────┐
  │   Thread 1 ──────────────────────► [MyService #1]   │
  │   Thread 2 ──────────────────────► [MyService #2]   │
  │   Thread 3 ──────────────────────► [MyService #3]   │
  └─────────────────────────────────────────────────────┘
  New instance per injection/request.
  Thread-safe (no sharing). BUT: Spring never calls @PreDestroy on them.

  ─────────────────────────────────────────────────────────────────

  REQUEST (web-scoped):
  One instance per HTTP request. Created when request arrives,
  destroyed when response is sent. Only available in web context.

  SESSION (web-scoped):
  One instance per user's HTTP session. Lives until session expires.

KEY INVARIANT:
   Singleton is the default and covers 95% of use cases.
   The scope only matters when a bean HAS state (mutable fields).
   Stateless beans (no instance fields that change) are always safe as singletons.
```

---

### ⚠️ The Prototype-in-Singleton Trap (Commonly Asked)

**The problem:**

```java
@Component
@Scope("prototype")   // should create a new instance each time
public class TaskProcessor {
    private int taskCount = 0;   // mutable state
    ...
}

@Service
public class TaskService {

    @Autowired
    private TaskProcessor processor;   // ← injected ONCE into a singleton
    // Spring creates TaskProcessor at startup, injects it, and never creates another.
    // The "prototype" scope means nothing here — it's used like a singleton!
}
```

**Why it happens:** Spring resolves all @Autowired dependencies once when it creates the bean. `TaskService` is a singleton — created once. When it's created, `TaskProcessor` is created once and injected. From then on, `taskService.processor` always points to that same instance.

**Fix 1 — `@Lookup`:**

```java
@Service
public class TaskService {

    // @Lookup tells Spring to override this method at runtime
    // and return a new prototype bean each time it's called
    @Lookup
    public TaskProcessor getProcessor() {
        return null;   // Spring replaces this with the actual prototype creation
    }

    public void processTask() {
        TaskProcessor processor = getProcessor();   // gets a fresh instance each time
        processor.process();
    }
}
```

**Fix 2 — `ObjectFactory` (explicit, clear):**

```java
@Service
public class TaskService {

    @Autowired
    private ObjectFactory<TaskProcessor> processorFactory;

    public void processTask() {
        TaskProcessor processor = processorFactory.getObject();   // fresh instance
        processor.process();
    }
}
```

---

### 🔬 Bean Lifecycle — The Full Sequence

**Steps in plain English:**

1. Spring reads `@Component` / `@Bean` declarations.
2. Calls the **constructor** to create the instance.
3. Injects **dependencies** (`@Autowired`, `@Value`).
4. Calls any method annotated with **`@PostConstruct`** — your startup/validation code.
5. Bean is **live** in the ApplicationContext — ready to serve requests.
6. On shutdown, calls any method annotated with **`@PreDestroy`** — your cleanup code.
7. Bean is **destroyed** and garbage collected.

```java
@Service
public class CacheService {

    private Map<String, Object> cache;

    public CacheService() {
        // Step 2: constructor — dependencies NOT yet injected here
        System.out.println("Constructor called");
    }

    @Autowired
    private DataSource dataSource;   // Step 3: injected after constructor

    @PostConstruct
    public void init() {
        // Step 4: safe to use injected deps here
        this.cache = loadCacheFromDB(dataSource);
        System.out.println("Cache warmed up: " + cache.size() + " entries");
    }

    @PreDestroy
    public void cleanup() {
        // Step 6: called on context shutdown
        cache.clear();
        System.out.println("Cache cleared");
    }
}
```

---

### 🎨 Visual — Lifecycle Timeline

```
  ApplicationContext starts
  │
  ├─ Step 1: Scan for @Component / @Bean definitions
  │
  ├─ Step 2: new CacheService()          ← constructor
  │
  ├─ Step 3: @Autowired DataSource injected
  │
  ├─ Step 4: @PostConstruct init() runs
  │                │
  │                └─ ✅ Bean is live and usable
  │
  │         ... application serves requests ...
  │
  ├─ Context.close() called (shutdown)
  │
  ├─ Step 5: @PreDestroy cleanup() runs
  │
  └─ Bean garbage collected

  ⚠️ @PreDestroy is NEVER called on prototype-scoped beans.
     Spring does not track prototypes after handing them out.
     You are responsible for cleanup if your prototype holds resources.

KEY INVARIANT:
   @PostConstruct runs AFTER all @Autowired injections are complete.
   Never try to use an injected dependency in the constructor —
   it's null at that point.
```

---

<a id="proxy-aop"></a>

## 🔹 5. Spring Proxy Model + AOP

### 📖 Terminology

- **CGLIB (Code Generation Library)** — a library that generates subclasses of your class at runtime. Spring uses it to create proxies that can intercept method calls and add behavior (like opening transactions).
- **JDK Dynamic Proxy** — Java's built-in way to create proxies for interfaces. Can only proxy methods declared in an interface.
- **AOP (Aspect-Oriented Programming)** — a way to add behavior (like logging, transactions, security) to methods without putting that code inside the methods. The behavior is declared separately as an "aspect" and applied automatically.

---

### 🧠 Mental Model — The Transparent Wrapper

Imagine wrapping a birthday gift. The gift (your service class) is inside. The wrapping (the CGLIB proxy) looks like the gift from the outside — same shape, same interface — but when you touch the wrapping, it can do things before and after you reach the actual gift.

Spring replaces the bean in its registry with this wrapped version. Your code that `@Autowired`s the service gets the wrapper, not the original.

```
  Without Spring (raw object):
  Caller ──────────────────► MyService.doWork()

  With Spring (proxied):
  Caller ──────────────────► [CGLIB Proxy of MyService]
                                  │  (pre-processing: open TX, check auth, etc.)
                                  ├──────────────────────────────► MyService.doWork()
                                  │                                    │
                                  │◄──────────────── return ──────────┤
                                  │  (post-processing: commit/rollback TX, cache, etc.)
                             return to caller
```

---

### 🎨 Visual — CGLIB Proxy vs JDK Proxy

```
  Your class:
  @Service
  public class UserService {          // concrete class
      @Transactional
      public User getUser(Long id) { ... }
  }

  CGLIB Proxy (Spring Boot default — even with interfaces):
  ┌──────────────────────────────────────────────────────┐
  │  class UserService$$EnhancerBySpringCGLIB$$abc123    │  ← generated subclass
  │    extends UserService {                             │
  │      @Override                                       │
  │      public User getUser(Long id) {                  │
  │          // open transaction                         │
  │          User result = super.getUser(id);            │
  │          // commit / rollback                        │
  │          return result;                              │
  │      }                                               │
  │  }                                                   │
  └──────────────────────────────────────────────────────┘

  JDK Dynamic Proxy (only when class implements an interface AND
  you have proxy-target-class=false in config):
  ┌──────────────────────────────────────────────────────┐
  │  Proxy implements UserRepository {                   │
  │      (wraps actual UserService, delegates calls)     │
  │  }                                                   │
  └──────────────────────────────────────────────────────┘
  Works only for methods declared in the interface.
  Methods NOT in the interface = not proxied.

  ──────────────────────────────────────────────────────────────────────

  Why CGLIB can't proxy private/final methods:
  CGLIB generates a SUBCLASS. Java rules:
    private methods → not visible to subclasses → can't override → can't intercept
    final methods   → can't be overridden       → can't intercept
    final classes   → can't be subclassed       → can't proxy at all

KEY INVARIANT:
   Spring Boot defaults to CGLIB proxy for ALL beans (including ones with interfaces).
   @Transactional, @Async, @Cacheable all require: public + non-final method + called from outside the class.
```

---

### 🔬 Why Self-Invocation Breaks Transactions — The Exact Mechanism

```java
@Service
public class OrderService {

    public void placeOrder(Order order) {
        // "this" here refers to the REAL OrderService object, NOT the proxy
        this.saveToDb(order);   // goes to real object — proxy never intercepted
    }

    @Transactional
    public void saveToDb(Order order) {
        // @Transactional is on this method — but NO transaction is open
        // because the proxy never saw the call to this method
        orderRepo.save(order);   // runs without a transaction!
    }
}

// What Spring put in its registry:
//   "orderService" → OrderService$$CGLIB (the proxy)
//
// When external code calls orderService.placeOrder():
//   → hits the proxy
//   → proxy calls super.placeOrder() on the real object
//   → placeOrder() runs as real object → this.saveToDb() = real object's method
//   → proxy is completely out of the picture
```

---

### 🔬 AOP Terms — What EPAM Asks Verbally

**Aspect** — the cross-cutting concern (the behavior you want to inject). Examples: transaction management, logging, security.

**Join point** — a point in program execution where an aspect can hook in. In Spring AOP, join points are always **method executions** (Spring doesn't support field interception).

**Advice** — what the aspect does at a join point.

```
  @Before    → runs BEFORE the method executes
  @After     → runs AFTER the method (whether success or exception)
  @AfterReturning → runs AFTER successful return
  @AfterThrowing  → runs AFTER exception thrown
  @Around    → wraps the method — can run code before AND after, and control execution
```

**Pointcut** — the expression that selects which join points the advice applies to.

**Weaving** — the process of applying the aspect to the target code. Spring uses runtime weaving (at application startup, via proxies).

---

### 🔬 @Around Advice Example (What Wraps @Transactional Under the Hood)

**Steps in plain English:**

1. Declare an `@Aspect` class.
2. Write a `@Pointcut` expression to select which methods to intercept.
3. Write an `@Around` advice that wraps the target method call (called `joinPoint.proceed()`).

```java
@Aspect
@Component
public class LoggingAspect {

    // Step 2: Pointcut — match all methods in com.example.service package
    @Pointcut("execution(* com.example.service.*.*(..))")
    public void serviceLayer() {}

    // Step 3: Around advice — wrap matched methods with timing
    @Around("serviceLayer()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        // Step 3a: call the actual method (this is where your real code runs)
        Object result = joinPoint.proceed();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println(joinPoint.getSignature() + " took " + elapsed + "ms");

        return result;
    }
}
```

**@Transactional is exactly this pattern internally** — Spring has an `@Around` advice on every `@Transactional` method that opens a transaction before `joinPoint.proceed()` and commits/rolls back after.

---

## 🧾 TL;DR — The 10 Things to Say Cold Tomorrow

1. **Self-invocation trap:** `this.method()` bypasses the CGLIB proxy → @Transactional, @Async, @Cacheable all silently ignored → fix by moving the method to a separate bean
2. **Private method trap:** CGLIB generates a subclass, can't override private methods → @Transactional on private does nothing, no error
3. **Checked exception trap:** @Transactional rolls back only on RuntimeException/Error by default → IOException doesn't rollback → add `rollbackFor = IOException.class`
4. **REQUIRED (default):** joins existing transaction; same commit/rollback fate
5. **REQUIRES\_NEW:** suspends current transaction, starts independent one — failure is independent
6. **NESTED:** savepoint inside the current transaction — works only with JDBC, not JPA/JpaTransactionManager
7. **Isolation.READ\_COMMITTED:** prevents dirty reads, allows non-repeatable and phantom reads — Postgres default
8. **Isolation.REPEATABLE\_READ:** MySQL default — prevents dirty + non-repeatable; MySQL InnoDB and Postgres also prevent phantoms (beyond ANSI spec)
9. **Prototype-in-Singleton trap:** Spring injects prototype once into a singleton → never refreshed → behaves as singleton → fix with `@Lookup` or `ObjectFactory`
10. **@PostConstruct vs constructor:** constructor runs before injection — don't use injected deps in constructor, use @PostConstruct

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Sep 2026 | Day 2 notes created. NESTED propagation clarified (savepoints, JpaTransactionManager rejects it). Isolation levels include DB-specific behavior (MySQL InnoDB + Postgres REPEATABLE_READ block phantoms beyond ANSI standard). Prototype-in-Singleton trap added with @Lookup and ObjectFactory fixes. CGLIB final/private limitation tied to Trap 2. |
