# Chapter 2 — Spring Core: IoC Container, Dependency Injection, and AOP

> **Track context:** Chapter 2 of 4 in the Spring Foundation DeepDive series (`../spring-10-hour-plan.md`). Covers the heart of the Spring framework — the IoC container that wires your application together, dependency injection that replaces manual `new` calls, bean scopes and lifecycle, and AOP with its proxy model. The `@Transactional` self-call trap — **the interviewer's favorite Spring gotcha** — is covered here in full.

---

## 📖 Prerequisites

You should have absorbed Chapter 1 (`01-web-servlet-foundation.md`):
- A servlet container creates **one servlet instance** and reuses it across all request threads (singleton by default).
- In raw servlet code, you wire dependencies manually inside `init()` — which becomes unsustainable at scale.
- `DispatcherServlet` (Spring's front-door servlet that catches every HTTP request and dispatches it to the right `@Controller` method based on URL + verb) is the one servlet Spring registers; every `@RestController` method is reached through it.

The question Chapter 2 answers: *"Who creates your objects, and how do cross-cutting concerns like transactions and caching attach to your methods?"*

---

## 🧠 Mental model

> **IoC = invert the dependency creation.** You don't create your dependencies; the container creates them for you and hands them in. **DI = the mechanism** — a dependency is *injected* into your class (via constructor, field, or setter) instead of your class instantiating it. **AOP = wrap a method call transparently** — instead of repeating cross-cutting code (logging, transactions, security) in every method, you write it once in an *aspect* and the framework applies it via *proxies*.

Three corollaries:

1. **The ApplicationContext** (Spring's implementation of the IoC container — the registry that holds all your beans, knows their dependencies, and wires them together at startup) **is the object factory.** When you need a `CacheService`, you don't `new CacheService()` — you declare it as a `@Service` and the container gives you the wired, ready-to-use instance.
2. **Beans are singletons by default** (just like raw servlets). The container creates ONE instance per bean definition, wires its dependencies, and hands out the same instance to every caller. Thread safety is your problem.
3. **AOP works via proxies.** When you call `orderService.save()`, you're calling the *proxy's* `save()`, which runs advice (the action that an AOP aspect executes — e.g., "begin a transaction before, commit after"), then delegates to the real method. **Direct calls inside the same class bypass the proxy** — this is the self-call trap.

If you can verbalize those three points without notes, you have Chapter 2.

---

## 🪜 Concept build-up

---

### Part 1 — The problem IoC solves

Recall the raw servlet equivalent from Chapter 1. A 200-endpoint application, each endpoint in its own servlet, each servlet's `init()` manually creating its dependencies:

```java
// Servlet A
public void init() throws ServletException {
    DataSource ds = new HikariDataSource(config);
    OrderRepository repo = new OrderRepository(ds);
    CacheService cache = new RedisCache(redisConfig);
    this.orderService = new OrderService(repo, cache);
}

// Servlet B
public void init() throws ServletException {
    DataSource ds = new HikariDataSource(config);   // ← duplicate
    OrderRepository repo = new OrderRepository(ds);  // ← duplicate
    CacheService cache = new RedisCache(redisConfig); // ← duplicate
    this.inventoryService = new InventoryService(repo, cache);
}

// ... 198 more servlets with the same wiring boilerplate
```

**Problems:**
- O(N×M) wiring code — N servlets × M dependencies each
- No sharing — every servlet creates its own `DataSource`, so you have 200 connection pools instead of 1
- No lifecycle control — who closes the connection pool on shutdown?
- Hard to swap implementations — need to change every `init()`

**The IoC container fixes all of this.** You declare your objects and their relationships; the container builds the graph, manages lifecycles, and hands you pre-wired instances.

```java
// With IoC — you declare, container wires
@Service
public class OrderService {

    private final OrderRepository repo;
    private final CacheService cache;

    // Constructor injection — container calls this with the pre-wired objects
    public OrderService(OrderRepository repo, CacheService cache) {
        this.repo = repo;
        this.cache = cache;
    }
}
```

---

### Part 2 — How Spring discovers beans (component scanning vs explicit `@Bean`)

Spring learns about your beans in two ways.

#### Way 1 — Component scanning (implicit)

You annotate a class with a stereotype annotation (a Spring annotation that marks a class as a bean and carries semantic meaning about its role in the application):

| Annotation | Meaning | Where |
| --- | --- | --- |
| `@Component` | Generic bean — "Spring, manage this object" | Utilities, helpers |
| `@Service` | Business logic layer — identical to `@Component`, semantic only | Service classes |
| `@Repository` | Persistence layer — same as `@Component` + enables persistence exception translation | DAO / repo classes |
| `@Controller` / `@RestController` | Web layer — marks a Spring MVC handler | Controller classes |
| `@Configuration` | Contains `@Bean` factory methods — "this class produces beans" | Config classes |

When your Spring Boot application starts, it scans the classpath rooted at your `@SpringBootApplication` class's package and registers every class annotated with a stereotype as a **bean definition** (a metadata record — "create an instance of class X, with scope Y, wired with dependencies Z").

```java
@SpringBootApplication
// ↑ equivalent to @ComponentScan + @EnableAutoConfiguration + @Configuration
// ComponentScan: "scan every class in this package and sub-packages for stereotypes"
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

#### Way 2 — Explicit `@Bean` methods (explicit)

Inside a `@Configuration` class, any method annotated with `@Bean` is called by the container once; the returned object becomes a bean:

```java
@Configuration
public class AppConfig {

    @Bean
    public DataSource dataSource() {
        // You control construction — useful for third-party classes you can't annotate
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://...");
        ds.setMaximumPoolSize(20);
        return ds;
    }

    @Bean
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate) {
        // Spring injects 'redisTemplate' automatically — it's another bean
        return new RedisCacheService(redisTemplate);
    }
}
```

**When to use each:**
- Your own classes → stereotype annotation (simpler, less boilerplate)
- Third-party classes (e.g., `DataSource`, `RestTemplate`, `ObjectMapper`) → `@Bean` method (you can't add annotations to libraries)

---

### Part 3 — Dependency injection: constructor vs field vs setter

Spring resolves a bean's dependencies at startup. Three injection styles — only one is correct for production code.

#### Constructor injection (✅ preferred)

```java
@Service
public class OrderService {

    private final OrderRepository repo;
    private final CacheService cache;

    // Spring automatically calls this constructor and injects matching beans
    // (If there's exactly one constructor, @Autowired is implicit since Spring 4.3)
    public OrderService(OrderRepository repo, CacheService cache) {
        this.repo = repo;
        this.cache = cache;
    }
}
```

**Why this is the right approach:**
- **Immutability:** dependencies can be `final` — can never accidentally be null or overwritten
- **Testability:** you can instantiate `OrderService` in a unit test with mocks — just call `new OrderService(mockRepo, mockCache)`. No Spring container needed.
- **Fail fast:** if a dependency is missing, the application context fails to start, not at runtime when the first request hits. Container-side circular dependencies also surface here as exceptions.
- **Clarity:** the constructor signature is a contract — it's impossible to not see what `OrderService` needs.

#### Field injection (❌ avoid in production)

```java
@Service
public class OrderService {

    // ❌ AVOID — Spring injects this via reflection after construction
    @Autowired
    private OrderRepository repo;

    @Autowired
    private CacheService cache;
}
```

**Problems:**
- Fields can't be `final` — mutability creep, accidental null assignment possible
- You can't unit-test without a Spring container — `new OrderService()` leaves `repo` and `cache` as `null`
- Dependencies are invisible in the class API — callers can't tell what the class needs

> **Interview gotcha:** field injection still works and you'll see it in legacy code. Knowing *why* it's discouraged matters more than having never seen it.

#### Setter injection (⚠️ use only for optional dependencies)

```java
@Service
public class NotificationService {

    private EmailSender emailSender;

    // Setter injection: the dependency is optional (emailSender may not be configured)
    @Autowired(required = false)
    public void setEmailSender(EmailSender emailSender) {
        this.emailSender = emailSender;
    }
}
```

Setter injection is correct when the dependency is **optional** — the bean can function without it. For mandatory dependencies, use constructor injection.

#### Circular dependencies

When A needs B and B needs A via constructor injection, the container throws a `BeanCurrentlyInCreationException` at startup. **This is intentional.** Circular dependencies usually indicate a design problem — one class is doing too much.

Fixes (in order of preference):
1. **Restructure:** extract the shared logic into a third class C that both A and B depend on.
2. **Use `@Lazy` on one side:** `@Autowired @Lazy private B b;` — the container injects a proxy that resolves B on first use, breaking the circular construction chain.
3. **Change one side to setter injection** — the container constructs A first (without B), then injects B via setter.

---

### Part 4 — Bean scopes

The **scope** (how many instances the container creates, and how long they live) of a bean is declared on the bean definition:

| Scope | Instances | Lifetime | Use case |
| --- | --- | --- | --- |
| **`singleton`** | 1 per `ApplicationContext` | App lifetime | Services, repositories, controllers — anything stateless |
| **`prototype`** | New instance per injection point | Until GC | Stateful helpers, command objects |
| **`request`** | 1 per HTTP request | Duration of one HTTP request | Per-request state in web apps |
| **`session`** | 1 per HTTP session | Duration of user session | Per-user cart, preferences |

```java
// Default (singleton) — no annotation needed
@Service
public class OrderService { }

// Prototype — new instance every time it's injected
@Component
@Scope("prototype")
public class CartBuilder { }

// Request scope — one per HTTP request
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext { }
```

#### The scope mismatch gotcha (⭐ silent bug)

**Scenario:** a singleton service injects a prototype bean.

```java
@Service
// ❌ WRONG — orderService is a singleton, but cartBuilder is supposed to be prototype
public class OrderService {

    @Autowired
    private CartBuilder cartBuilder;  // injected ONCE at OrderService creation time

    public Order placeOrder(List<Item> items) {
        // cartBuilder here is the SAME instance every time — prototype has no effect!
        // Multiple threads will share this CartBuilder
        cartBuilder.addItems(items);
        return cartBuilder.build();
    }
}
```

**Why:** Spring wires the prototype bean **once** when the singleton is created. After that, `cartBuilder` always refers to the same instance — defeating the point of prototype scope.

**Fix — inject `ApplicationContext` and look up the bean dynamically:**

```java
@Service
public class OrderService {

    private final ApplicationContext ctx;

    public OrderService(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public Order placeOrder(List<Item> items) {
        // Gets a FRESH CartBuilder instance on every call
        CartBuilder cartBuilder = ctx.getBean(CartBuilder.class);
        cartBuilder.addItems(items);
        return cartBuilder.build();
    }
}
```

**Or** use `@Lookup` method injection — Spring overrides the method at runtime to return a fresh prototype each time.

---

### Part 5 — Bean lifecycle

The full timeline of one Spring bean, from container bootstrap to shutdown:

```
ApplicationContext starts
      │
      ▼
┌──────────────────────────────────────┐
│ 1. Bean definition registration      │  ← scan @Component / read @Bean methods
│    (metadata only — no instances yet) │
└──────────────────────────────────────┘
      │
      ▼
┌──────────────────────────────────────┐
│ 2. Instantiation                     │  ← calls the constructor (no-arg or injected)
└──────────────────────────────────────┘
      │
      ▼
┌──────────────────────────────────────┐
│ 3. Dependency injection              │  ← sets all @Autowired fields/setters
│    (constructor injection: step 2+3  │
│     are merged — fields in the ctor) │
└──────────────────────────────────────┘
      │
      ▼
┌──────────────────────────────────────┐
│ 4. BeanPostProcessor.postProcessBefore│ ← framework hooks (AOP proxies created here)
└──────────────────────────────────────┘
      │
      ▼
┌──────────────────────────────────────┐
│ 5. @PostConstruct method             │  ← YOUR initialization hook, after all deps injected
└──────────────────────────────────────┘
      │
      ▼
┌──────────────────────────────────────┐
│ 6. BeanPostProcessor.postProcessAfter │ ← more framework hooks
└──────────────────────────────────────┘
      │
      ▼
┌──────────────────────────────────────┐
│ 7. Bean is READY and in the context   │  ← all calls to getBean() return this instance
└──────────────────────────────────────┘
      │
   (application runs)
      │
      ▼
┌──────────────────────────────────────┐
│ 8. @PreDestroy method                │  ← YOUR cleanup hook, on graceful shutdown
└──────────────────────────────────────┘
      │
      ▼
ApplicationContext closed

KEY INVARIANT:
   @PostConstruct runs after ALL dependencies are injected.
   Safe to use injected objects there.
   Constructor body runs before injection — DON'T use @Autowired fields in the constructor.
```

#### `@PostConstruct` vs constructor — the critical distinction

```java
@Service
public class CacheWarmer {

    private final CacheService cache;
    private final List<String> warmKeys;

    public CacheWarmer(CacheService cache) {
        this.cache = cache;
        // ❌ WRONG — tempting but wrong place for initialization that calls injected objects
        // warmKeys = cache.getKeysToWarm();  ← would work because cache IS injected via ctor
        // But for field-injected dependencies (common in legacy), this would NPE
    }

    @PostConstruct
    public void init() {
        // ✅ CORRECT — all dependencies injected by the time this runs
        // Works for both constructor-injected and field-injected dependencies
        warmKeys = cache.getKeysToWarm();
        cache.preload(warmKeys);
    }

    @PreDestroy
    public void cleanup() {
        // ✅ Runs before the bean is removed from the context (graceful shutdown)
        cache.evict(warmKeys);
    }
}
```

**Rule:** initialization logic that calls injected beans → `@PostConstruct`. Resource release → `@PreDestroy`.

---

### Part 6 — AOP: wrapping methods transparently

**AOP** (aspect-oriented programming — a way to inject cross-cutting concerns like logging or transactions into your methods without writing the code inline) exists because some behaviors cut across the whole application:

- **Transaction management:** `begin → run → commit/rollback` wraps every DB-modifying method
- **Caching:** `check cache → if miss, call method → store result` wraps every `@Cacheable` method
- **Security:** `check authorization → if denied, throw → else call method` wraps every `@PreAuthorize` method
- **Logging:** `log start time → call method → log end time + elapsed` wraps every traced method

Without AOP, you'd write these 5-line wrappers in every method. With AOP, you write them once in an **aspect** and Spring applies them via proxies.

#### AOP vocabulary (memorize these four terms)

| Term | Plain English | Code example |
| --- | --- | --- |
| **JoinPoint** | The specific moment of execution where an aspect *could* run — for Spring, always a method call | `orderService.save(order)` |
| **Pointcut** | The expression that *selects* which joinpoints to target | `execution(* com.kapil.service.*.*(..))` — "every method in every service class" |
| **Advice** | The action that runs at the selected joinpoint — the actual cross-cutting code | `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing` |
| **Aspect** | The class that packages a pointcut + advice together | `@Aspect` annotated class |

```java
@Aspect
@Component
public class ExecutionTimeAspect {

    // Pointcut — "every method in a class annotated with @Service"
    @Around("within(@org.springframework.stereotype.Service *)")
    public Object trackTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();

        // Step 1 — before: record start time
        try {
            // Step 2 — call the REAL method
            return pjp.proceed();
        } finally {
            // Step 3 — after: log elapsed time
            long elapsed = System.currentTimeMillis() - start;
            System.out.println(pjp.getSignature().getName() + " took " + elapsed + "ms");
        }
    }
}
```

#### How Spring applies AOP — the proxy model

Spring's AOP is **proxy-based** (not compile-time bytecode manipulation like AspectJ — Spring wraps your bean in a proxy object at runtime, not at compile time). When the container detects that a bean needs AOP advice (because it matches a pointcut), it wraps the bean in a proxy before putting it in the context.

**Two proxy strategies:**

| Strategy | When used | How it works |
| --- | --- | --- |
| **JDK dynamic proxy** | Bean implements at least one interface | JVM creates a synthetic class that implements the same interface(s); each method call is intercepted |
| **CGLIB proxy** | Bean has no interface | Byte Buddy / CGLIB creates a *subclass* of your bean class at runtime; methods are overridden to run advice before/after |

```
Without AOP:
   Your code:     orderService.save(order)
   What happens:  orderService.save(order)  [directly]


With AOP (@Transactional, @Cacheable, etc.):
   Your code:     orderService.save(order)
   What happens:  proxy.save(order)
                    → TransactionInterceptor.invoke()
                        → begin transaction
                        → REAL orderService.save(order)   [the actual method]
                        → commit (or rollback on exception)
```

The proxy is what the container gives you when you inject `@Autowired OrderService orderService`. You're holding a reference to the **proxy**, not the raw bean.

---

### Part 7 — `@Transactional`: the most important Spring annotation to get right

`@Transactional` is Spring's primary AOP application. Understanding it deeply = answering 80% of Spring interview questions.

#### How `@Transactional` works (the proxy unfolded)

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order order) {
        orderRepo.save(order);
        inventoryRepo.deduct(order.getItemId(), order.getQuantity());
    }
}
```

When you call `orderService.placeOrder(order)` from a controller, here's the full execution path:

**Steps in plain English:**

1. **The call hits the proxy** (not `OrderService` directly).
2. **Proxy checks for an active transaction** via `TransactionSynchronizationManager`.
3. **If none exists** (default propagation = `REQUIRED`): the proxy opens a new JDBC connection, calls `connection.setAutoCommit(false)`, and stores it in a `ThreadLocal` (a Java mechanism that gives each thread its own copy of a variable — used here so the transaction is invisible to other threads but visible to all code on the same thread).
4. **The real `OrderService.placeOrder(order)` runs.** Both repo calls get the same connection from the `ThreadLocal` — they're in the same transaction.
5. **If `placeOrder` returns normally**: proxy calls `connection.commit()`.
6. **If `placeOrder` throws a `RuntimeException` or `Error`**: proxy calls `connection.rollback()`.
7. **If `placeOrder` throws a checked exception**: proxy calls `connection.commit()` — **this surprises most people**.

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order order) throws OrderException {
        orderRepo.save(order);
        if (order.getQuantity() > stock) {
            // ❌ WRONG: OrderException is checked — Spring will COMMIT despite this throw
            // The order will be saved even though we "rejected" it
            throw new OrderException("out of stock");
        }
    }
}
```

```java
// ✅ Fix option 1: extend RuntimeException
public class OrderException extends RuntimeException { ... }

// ✅ Fix option 2: explicitly declare rollback-for
@Transactional(rollbackFor = OrderException.class)
public void placeOrder(Order order) throws OrderException { ... }
```

#### Propagation — what happens when one transactional method calls another

**Propagation** (how a transactional method behaves when called from inside an existing transaction — does it join it, start a new one, or refuse?) is the second `@Transactional` attribute you need to know:

| Propagation | Behavior |
| --- | --- |
| `REQUIRED` (default) | Join existing transaction; create new if none. The most common — do what the caller is doing. |
| `REQUIRES_NEW` | Always create a new, separate transaction; suspend the outer one. Use when the inner operation must commit/rollback independently (e.g., audit logging that must persist even if the main transaction rolls back). |
| `SUPPORTS` | Join existing if one exists; run non-transactionally if none. Use for read-only operations that work either way. |
| `NOT_SUPPORTED` | Always run non-transactionally; suspend the outer if any. |
| `MANDATORY` | Must be called from inside an existing transaction; throw if not. |
| `NEVER` | Must NOT be called from inside a transaction; throw if one exists. |
| `NESTED` | Run within a savepoint inside the outer transaction; inner rollback rolls back to the savepoint only. |

```java
@Service
public class AuditService {

    // REQUIRES_NEW — this audit log must persist regardless of what the calling
    // transaction does. If the order transaction rolls back, the audit survives.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderAttempt(String userId, String action) {
        auditRepo.save(new AuditEntry(userId, action, LocalDateTime.now()));
    }
}

@Service
public class OrderService {

    private final AuditService auditService;

    @Transactional
    public void placeOrder(Order order) {
        auditService.logOrderAttempt(order.getUserId(), "PLACE_ORDER"); // new transaction!
        orderRepo.save(order);
        // if this method throws and rolls back, the audit entry is already committed
    }
}
```

#### ⭐ The self-call trap — the most common Spring interview gotcha

**Scenario:** method A in a class calls method B in the same class. Method B is `@Transactional`.

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order order) {
        // ... place the order ...
    }

    // Outer method — NOT annotated
    public void processOrders(List<Order> orders) {
        for (Order o : orders) {
            // ❌ TRAP: this calls placeOrder on 'this', not on the proxy
            // @Transactional on placeOrder has NO EFFECT
            this.placeOrder(o);
        }
    }
}
```

**Why:** when your controller calls `orderService.processOrders(...)`, it calls the proxy. The proxy runs `processOrders` on the REAL object. Inside `processOrders`, the call `this.placeOrder(o)` is a **direct call on the real object** — it bypasses the proxy entirely. Spring's transaction interceptor never runs.

```
Controller → proxy.processOrders()
                 → real.processOrders()
                     → this.placeOrder()     ← direct call, NO proxy involved
```

**Fixes:**

```java
// Fix 1: inject self (inject your own proxy)
// ⚠️ NOTE: On Spring 4.3+, Spring handles circular self-injection without extra config.
// On older versions (< 4.3) you may need @Lazy to break the circular dependency:
//   @Autowired @Lazy private OrderService self;
@Service
public class OrderService {

    @Autowired
    private OrderService self;   // Spring gives you the proxy of yourself

    public void processOrders(List<Order> orders) {
        for (Order o : orders) {
            self.placeOrder(o);  // ✅ goes through the proxy → @Transactional works
        }
    }

    @Transactional
    public void placeOrder(Order order) { ... }
}
```

```java
// Fix 2: move placeOrder to a different service (preferred — cleaner design)
@Service
public class SingleOrderService {

    @Transactional
    public void placeOrder(Order order) { ... }
}

@Service
public class BulkOrderService {

    private final SingleOrderService singleOrderService;

    public BulkOrderService(SingleOrderService singleOrderService) {
        this.singleOrderService = singleOrderService;
    }

    public void processOrders(List<Order> orders) {
        for (Order o : orders) {
            singleOrderService.placeOrder(o); // ✅ goes through proxy
        }
    }
}
```

**The general rule:** any Spring annotation that works via AOP (`@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize`) **only works when called through the proxy**. Direct `this.method()` calls bypass AOP.

#### `@Transactional` on private methods — another silent failure

```java
@Service
public class OrderService {

    // ❌ @Transactional on a private method has NO effect
    // Proxy can't override a private method (JDK proxy: interface-based, can't see it;
    // CGLIB: subclass-based, but private methods can't be overridden in subclasses)
    @Transactional
    private void saveAndNotify(Order order) {
        orderRepo.save(order);
        notifier.send(order.getUserId(), "order placed");
    }
}
```

Fix: make the method `public` (or `protected` for CGLIB) and move it out of the same class if self-call is a concern.

---

## ⚠️ Gotchas — the silent-bug hall of fame

| # | Gotcha | Symptom | Fix |
| --- | --- | --- | --- |
| 1 | Scope mismatch — prototype injected into singleton | Prototype behaves like singleton | Use `ApplicationContext.getBean()` or `@Lookup` |
| 2 | Self-call bypasses proxy | `@Transactional`/`@Cacheable`/`@Async` silently does nothing | Inject self or extract to separate class |
| 3 | `@Transactional` on private method | Transaction doesn't start | Make method `public` |
| 4 | Checked exception doesn't rollback | Data partially saved despite exception | `rollbackFor = MyException.class` or extend `RuntimeException` |
| 5 | `@PostConstruct` calling `@Autowired` field before injection | `NullPointerException` on startup | Move logic to `@PostConstruct` (not constructor) if using field injection |
| 6 | Wrong propagation — inner method doesn't join outer transaction | Two separate commits instead of one atomic unit | `REQUIRED` (default) joins; verify your methods aren't accidentally using `REQUIRES_NEW` |
| 7 | `@Async` self-call (same trap as @Transactional) | Method runs synchronously | Inject self or move to different bean |

---

## 🎨 Visual — the container bootstrap + proxy model

### 🎨 Visual — ApplicationContext bootstrap

```
@SpringBootApplication starts
      │
      ▼
┌────────────────────────────────────────────────────────────────┐
│ Phase 1: Bean Definition Registration                          │
│   Classpath scan finds:                                        │
│     @Service OrderService        ←── registered as "bean def" │
│     @Service CacheService        ←── registered as "bean def" │
│     @Repository OrderRepository  ←── registered as "bean def" │
│     @Configuration AppConfig     ←── registered as "bean def" │
│     @Bean DataSource             ←── registered as "bean def" │
│   (NO instances created yet)                                   │
└────────────────────────────────────────────────────────────────┘
      │
      ▼
┌────────────────────────────────────────────────────────────────┐
│ Phase 2: Instantiation + DI (in dependency order)              │
│   DataSource           ← no deps; create first                 │
│   OrderRepository(ds)  ← needs DataSource; create second       │
│   CacheService()       ← no deps; create                       │
│   OrderService(repo,   ← needs Repo + Cache; create last       │
│                cache)                                          │
└────────────────────────────────────────────────────────────────┘
      │
      ▼
┌────────────────────────────────────────────────────────────────┐
│ Phase 3: BeanPostProcessor — AOP proxy creation                │
│                                                                │
│   OrderService has @Transactional methods →                    │
│   container wraps it in a CGLIB proxy:                         │
│                                                                │
│   ┌──────────────────────────────────────┐                     │
│   │ CGLIB Proxy (subclass of OrderService)│                     │
│   │   ┌──────────────────────────────┐   │                     │
│   │   │ Real OrderService instance   │   │                     │
│   │   └──────────────────────────────┘   │                     │
│   │                                       │                     │
│   │ placeOrder(order):                    │                     │
│   │   → begin TX                         │                     │
│   │   → real.placeOrder(order)           │                     │
│   │   → commit or rollback               │                     │
│   └──────────────────────────────────────┘                     │
│                                                                │
│   Proxy is placed in the context — this is what you inject     │
└────────────────────────────────────────────────────────────────┘
      │
      ▼
┌────────────────────────────────────────────────────────────────┐
│ Phase 4: @PostConstruct methods run                            │
│   CacheWarmer.init() ← cache preloaded, app ready              │
└────────────────────────────────────────────────────────────────┘
      │
      ▼
Application is READY

KEY INVARIANT:
   You always inject / receive the PROXY, not the raw bean.
   @Transactional, @Cacheable, @Async work because the proxy
   intercepts the call before reaching the real method.
   Self-calls (this.method()) bypass the proxy → annotations silently ignored.
```

### 🎨 Visual — self-call trap (the must-memorize diagram)

```
CORRECT PATH (from controller → different bean):

   Controller
       │
       ▼
   OrderService PROXY ← you inject this
       │  proxy.placeOrder()
       │  → begin TX
       ▼
   Real OrderService.placeOrder()   ← TX active here ✅
       │
       ▼
   proxy.commit()


BROKEN PATH (self-call within the same bean):

   OrderService PROXY
       │  proxy.processOrders()
       │  → no AOP (processOrders not annotated)
       ▼
   Real OrderService.processOrders()
       │
       │  this.placeOrder()  ← direct call, bypasses proxy
       ▼
   Real OrderService.placeOrder()   ← NO TX here ❌
       │
       (no commit, no rollback — just runs naked)

KEY INVARIANT:
   Every AOP-annotated method must be called THROUGH the proxy.
   Self-calls (this.xxx()) are direct object method calls.
   Fix: inject self, or move the inner method to a different Spring bean.
```

---

## 🏢 Where you've seen this in your app

**IoC + DI:** every `@Service` in your production codebase uses constructor injection (or legacy field injection). The services you inject into controllers — `InventoryService`, `OrderService`, `CacheRefreshService` — are all beans managed by the ApplicationContext.

**Scope:** almost certainly all singleton, all stateless. If you see a service with instance fields that change between requests, that's a bug waiting to happen.

**@Transactional:** any service that writes to the database. The method that calls `orderRepo.save()` + `inventoryRepo.deduct()` should be transactional — otherwise a failure after the first save leaves the system in an inconsistent state.

**Scope mismatch in the wild:** this often shows up as "cache service only has the entries from the first request." The cache service was prototype-scoped, injected into a singleton — the same "first" instance runs forever.

---

## 🎤 Interview Q&A

### Q1: "What is the Spring IoC container and what problem does it solve?"

> **Model answer:** The IoC container — implemented as `ApplicationContext` in Spring — is an object factory and wiring engine. Without it, every class creates its own dependencies in an `init()` method or constructor, which leads to duplicated wiring code, no sharing of resources like `DataSource`, and no lifecycle management. The container inverts this: you declare what your class needs (via constructor parameters or annotations), and the container creates the dependency graph, instantiates beans in the right order, injects dependencies, manages lifecycles, and gives you a pre-wired, ready-to-use instance. "Inversion of Control" means the container is in charge of object creation, not your code.

### Q2: "Why is constructor injection preferred over field injection?"

> **Model answer:** Three reasons. First, **immutability** — dependencies injected via constructor can be `final`; field-injected dependencies can be reassigned. Second, **testability** — constructor injection means you can `new MyService(mockRepo, mockCache)` in a unit test without a Spring container; field injection requires either a container or reflection tricks. Third, **clarity** — the constructor signature is the explicit contract. With field injection, a class can have hidden dependencies that only become apparent when the Spring context fails to start. Also, constructor injection catches circular dependencies at startup with a clear exception, rather than letting them silently break at runtime.

### Q3: "What is the Spring bean lifecycle? What's the order of @PostConstruct vs constructor vs @Autowired?"

> **Model answer:** For a singleton bean: (1) container calls the **constructor** — dependencies injected via constructor are available here, but `@Autowired` field-injected dependencies are NOT yet set. (2) Container performs **dependency injection** — field and setter injections are applied. (3) **`@PostConstruct` method** runs — by this point all dependencies are available, making this the correct hook for initialization logic that calls other beans. (4) Bean is added to the context and available. (5) On shutdown, `@PreDestroy` runs — for cleanup. The gotcha: if you have field injection and put initialization logic in the constructor, the field-injected objects are null.

### Q4: "How does Spring implement `@Transactional`?"

> **Model answer:** `@Transactional` is implemented via AOP proxies. When the container sees that a bean has `@Transactional` methods, it wraps the bean in a proxy (CGLIB subclass if the bean has no interface; JDK dynamic proxy if it does). When you call a `@Transactional` method, the call first hits the proxy, not the real bean. The proxy — specifically, `TransactionInterceptor` — checks if there's an active transaction on the current thread (via `TransactionSynchronizationManager`). If not (with `REQUIRED` propagation), it opens a JDBC connection, calls `setAutoCommit(false)`, stores the connection in a `ThreadLocal`, then calls the real method. If the method returns normally, the proxy commits. If the method throws a `RuntimeException` or `Error`, the proxy rolls back. Checked exceptions do **not** trigger rollback by default.

### Q5: "Explain the self-call trap with @Transactional."

> **Model answer:** When you call `this.someTransactionalMethod()` from within the same class, you're calling it on the raw object, not the proxy. Since Spring's AOP intercepts calls at the proxy level, the transaction interceptor never runs — `@Transactional` is silently ignored. The most common scenario: an outer non-transactional method calls an inner `@Transactional` method in the same class. Fixes: (1) inject self — `@Autowired private OrderService self;` — Spring injects the proxy of your own bean; calling `self.placeOrder()` goes through the proxy. (2) Extract the inner method to a separate Spring bean. The same trap applies to `@Cacheable`, `@Async`, `@PreAuthorize` — any Spring annotation that works via AOP.

### Q6: "What's the difference between `REQUIRED` and `REQUIRES_NEW` propagation?"

> **Model answer:** `REQUIRED` (the default) joins an existing transaction if one is active on the calling thread; otherwise creates a new one. The inner and outer methods share one transaction — if either throws, the entire unit rolls back. `REQUIRES_NEW` always starts a brand-new, independent transaction — it suspends the outer one for the duration of the inner call. Use `REQUIRES_NEW` when the inner work must commit regardless of what happens to the outer transaction. Classic use case: an audit log that must persist even if the main business operation rolls back. The trade-off: two separate database connections are held concurrently, which can lead to deadlocks if both try to write to the same rows.

### Q7: "What is AOP and how does Spring implement it?"

> **Model answer:** AOP — aspect-oriented programming — separates cross-cutting concerns (logging, security, transaction management) from business logic by letting you declare "run this code around every method that matches this pattern" without modifying the methods themselves. Spring implements AOP via proxies at runtime, not compile-time bytecode manipulation (unlike AspectJ's compile-time weaving). When the container detects that a bean matches a pointcut — e.g., "every method annotated with `@Transactional`" — it creates a proxy that wraps the bean. The proxy intercepts method calls, runs the advice (begin transaction, check auth, etc.), then delegates to the real method. The limitation: proxy-based AOP only intercepts calls that go through the proxy. Internal method calls (`this.method()`) bypass the proxy and bypass all advice.

### Q8: "What bean scopes does Spring support and when would you use each?"

> **Model answer:** Four common scopes. **Singleton** (the default) — one instance per `ApplicationContext`, shared across all callers. Right for stateless services, repositories, controllers. **Prototype** — a new instance is created every time the bean is requested from the container. Right for stateful objects that shouldn't be shared — e.g., a builder or a command object with mutable state. **Request** — one instance per HTTP request. Right for per-request state in web apps. **Session** — one instance per HTTP session. Right for per-user state like a shopping cart. The silent gotcha: injecting a prototype into a singleton. The singleton's constructor (or `@Autowired` field) is wired only once at startup — from then on, the "prototype" bean is the same instance every time. Fix with `ApplicationContext.getBean()` or `@Lookup`.

---

## 🧾 TL;DR — Chapter 2 mental hook

**IoC in one sentence:** instead of `new Dependency()` in your code, you declare your dependencies and the container creates + wires them.

**DI rule of thumb:** constructor injection for mandatory dependencies (use `final`, testable, clear contract); setter injection for optional ones; never field injection in new code.

**Bean scopes:** singleton = shared, stateless (default); prototype = new instance per use; request/session = web-scoped. Scope mismatch (prototype into singleton) → silent singleton behavior.

**Lifecycle order:** constructor → dependency injection → `@PostConstruct` → ready → `@PreDestroy` → gone.

**AOP in one sentence:** Spring wraps beans in proxies; calling a method on the proxy runs advice (transaction, cache, security) before/after the real method.

**@Transactional rules to never forget:**
1. Rolls back on `RuntimeException`/`Error` only — not checked exceptions
2. `REQUIRED` (default) joins existing transaction; `REQUIRES_NEW` creates a separate one
3. **Self-calls bypass the proxy → @Transactional silently does nothing**
4. Private methods → proxy can't intercept → @Transactional silently does nothing

**The one picture to internalize:** every `@Transactional` call goes Controller → Proxy → begin TX → Real Method → commit/rollback. Self-call skips the proxy.

---

## 📚 Cross-references

- **Previous chapter:** `01-web-servlet-foundation.md` — HTTP/TCP/servlet container (why the container exists)
- **Next chapter:** `03-spring-mvc-boot.md` — DispatcherServlet internals, MVC, Spring Boot auto-configuration, properties, profiles
- **JPA / transaction details:** `04-jpa-transactions.md` — how `@Transactional` interacts with Hibernate sessions, lazy loading, and the entity lifecycle
- **Master plan + format standards:** `../spring-10-hour-plan.md`

---

### Changelog

| Date | Change |
| --- | --- |
| 2026-06-10 | Chapter created as part of 4-chapter Spring track restructure. Covers IoC container motivation, component scanning vs @Bean, constructor/field/setter injection, bean scopes + scope mismatch gotcha, bean lifecycle, AOP vocabulary, Spring proxy model (JDK vs CGLIB), @Transactional deep dive (proxy chain, rollback rules, propagation levels, self-call trap, private method trap). 8 interview Q&As. |
