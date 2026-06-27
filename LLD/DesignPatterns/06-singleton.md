# Singleton Pattern

> **Standard followed:** `LLD/notes-standards.md`
>
> **Why this file exists:** "How do you make a Singleton thread-safe?" is a near-universal LLD drill-down question. Every LLD problem has at least one class that should exist exactly once (the parking lot, the booking service, the logger). You need to code all three variants cold and explain the tradeoffs.

---

## 🎯 What Problem Does It Solve?

Some resources should exist exactly once: a database connection pool, a config store, a logger, a cache. Creating multiple instances either wastes resources (50 connection pools instead of 1) or introduces inconsistency (two config stores reading different files). Singleton ensures a class has exactly one instance and provides a global access point to it. The JVM enforces this — not a naming convention.

---

## 🧠 Mental Model

Think of the **one and only Chief Justice of a court**. Any request to "get the Chief Justice" returns the same person — you can't create a second. If the position is vacant, the first request fills it (lazy creation); alternatively, the position is filled the moment the court opens (eager creation). The court's rulebook (the class) says: there is exactly one. Anyone who tries to appoint a second is blocked.

In code: `ParkingLotManager` (or `BookingService`) should be created once. Every component that needs it calls `getInstance()` and gets the same object. `new ParkingLotManager()` is blocked because the constructor is `private`.

---

## 🔌 The Interface Contract

Singleton doesn't use a Java interface — the contract is structural: `private` constructor + `static getInstance()`. There are three variants with different thread-safety tradeoffs. You need all three.

---

## ⚙️ Implementation

### Variant 1 — Eager Initialization (simplest, always thread-safe)

**When to use:** The instance is always needed, creation is cheap, and you don't mind paying the cost at class-load time.

```java
public final class ConfigStore {

    // Created when the class is loaded — JVM class loading is thread-safe
    private static final ConfigStore INSTANCE = new ConfigStore();

    // Private constructor — nobody outside can call new ConfigStore()
    private ConfigStore() {
        // load config from file/env
    }

    public static ConfigStore getInstance() {
        return INSTANCE;
    }

    public String get(String key) {
        // return config value
        return "";
    }
}
```

**Tradeoff:** INSTANCE is created at class load even if nobody ever calls `getInstance()`. For heavy resources (DB pool), this wastes memory if the instance isn't always needed.

---

### Variant 2 — Lazy + Double-Checked Locking (lazy, thread-safe, production standard)

**When to use:** Creation is expensive (DB pool, reading a large config file) and you want to defer it until the first request. This is the pattern you'll write in an interview when asked "make it thread-safe."

```java
public final class DatabaseConnectionPool {

    // volatile: ensures the reference is visible to all threads after assignment
    // without volatile, a thread could see a partially-constructed object
    private static volatile DatabaseConnectionPool instance = null;

    private DatabaseConnectionPool() {
        // expensive: open 10 DB connections
    }

    public static DatabaseConnectionPool getInstance() {
        // First check: avoid locking if instance already exists (fast path)
        if (instance == null) {
            // Second check: only one thread creates the instance
            synchronized (DatabaseConnectionPool.class) {
                if (instance == null) {
                    instance = new DatabaseConnectionPool();
                }
            }
        }
        return instance;
    }
}
```

**Why two `null` checks?**

```
  Thread A                    Thread B
  ────────                    ────────
  if (instance == null)  →    if (instance == null)  → both see null, both enter
  synchronized(lock)     →    synchronized(lock)     → Thread B blocks here
  if (instance == null)  →    (waiting)
  instance = new Pool()  →    ...
  release lock           →    acquires lock
                         →    if (instance == null)  → false now! exits
                         →    return instance        ✓
```

Without the inner check, Thread B would create a second instance after Thread A already created one.

**Why `volatile`?**

Without `volatile`, the JVM (or CPU) can reorder instructions. `instance = new Pool()` is three steps: allocate memory, run constructor, assign reference. Without `volatile`, another thread could see a non-null reference but a half-constructed object (constructor not finished). `volatile` prevents this reordering.

---

### Variant 3 — Enum Singleton (best practice, recommended by Effective Java)

**When to use:** Serialization safety matters, or you want the simplest thread-safe lazy singleton. The JVM guarantees enum instances are created exactly once, thread-safely, and survive serialization/deserialization without creating a second instance.

```java
// The entire pattern — 4 lines
public enum AppLogger {

    // The one instance
    INSTANCE;

    public void log(String message) {
        // write to log
    }
}
```

```java
// Usage
AppLogger.INSTANCE.log("Booking confirmed");
```

**Why Enum wins:**
- JVM creates enum constants exactly once, thread-safely (no synchronization needed)
- Serialization is free — `readResolve()` not needed, deserialization returns the same instance
- Reflection-proof — `Constructor.newInstance()` throws `IllegalArgumentException` for enums
- Two-line implementation

**Why it's not always used:** It looks unusual. In an interview, double-checked locking signals you understand concurrency nuances. Enum signals you know Effective Java Item 3. Both are correct answers. State both, then say which you'd choose and why.

---

### 🎨 Visual — Variant Comparison

```
  EAGER                    LAZY (DCL)               ENUM
  ─────                    ──────────               ────
  Created at class load    Created on first call    Created by JVM at
                                                    class init

  Thread A ─▶ getInstance()  Thread A ─▶ if(null)   Thread A ─▶ INSTANCE
              │                          synchronized             │
              ▼                          │                        ▼
           INSTANCE ✓                    ▼                    INSTANCE ✓
                           Thread B ─▶ if(null)
                           (blocked)   false → return ✓

  Simple  ✅              Lazy ✅           Simple ✅
  Lazy    ❌              Thread-safe ✅    Thread-safe ✅
  Serializable ✅         Serializable ✗*  Serializable ✅
                                           Reflection-safe ✅

KEY INVARIANT:
   Only ONE instance exists in the JVM. Private constructor enforces this.
   volatile is required in DCL to prevent seeing a half-constructed object.
```

---

## 🏢 Real World Usage

- **`Runtime.getRuntime()` (Java stdlib)** — The JVM's `Runtime` is a Singleton (eager). `Runtime.getRuntime()` always returns the same instance. No new Runtime can be created.
- **Spring beans (default scope)** — Every `@Component`, `@Service`, `@Repository` is a Singleton by default in the Spring ApplicationContext. Spring manages lifecycle; you never call `getInstance()` — dependency injection hands you the one instance.
- **HikariCP (JDBC connection pool)** — One `HikariDataSource` per application. Creating multiple pools wastes connections. Applications typically wire this as a Singleton via Spring.
- **`LogManager` / SLF4J `LoggerFactory`** — The logging framework has one central manager; all `Logger` instances are retrieved from it. The manager itself is effectively a Singleton.
- **Feature flag client (LaunchDarkly, GrowthBook)** — Initialising the SDK client is expensive (HTTP connection, config download). Applications create one client on startup, share it everywhere.

---

## 🧭 When to Use vs When NOT to Use

| Use Singleton when | Do NOT use when |
|---|---|
| Exactly one instance makes sense by domain (the parking lot, the config store) | You want it for "convenience" — just pass it as a constructor arg instead |
| Creation is expensive and should happen once (DB pool) | The class holds mutable state that varies per use case |
| The instance is stateless or its state is shared-and-consistent across all callers | You need to test in isolation — Singletons make mocking hard |
| Framework (Spring) doesn't manage lifecycle for you | A DI container (Spring) already handles this — just use `@Bean` |

**The real warning:** Singleton is global mutable state. If multiple threads write to a Singleton, it needs internal synchronization. If tests need to reset state between runs, static instances persist across tests. In modern Spring applications, prefer dependency injection over manual Singleton — it gives you all the benefits with none of the testing pain.

---

## 🧩 LLD Problems That Use Singleton Pattern

- **Parking Lot** — `ParkingLot` is the central orchestrator; one per physical lot. If modelled as a Singleton, all floors, spots, and tickets live in one place. In practice, LLD designs often pass it via constructor rather than static Singleton — both are valid answers.
- **Logger System** — `LogManager.getInstance()` returns the one logger backbone. All appenders (file, console, CloudWatch) are registered with it. The Singleton prevents multiple overlapping log file handles.
- **BookMyShow** — `BookingService` as a Singleton — one booking engine with one in-memory seat inventory. Multiple instances would lead to inconsistent seat counts.
- **Rate Limiter** — `RateLimiter.getInstance()` for a per-application global rate limiter. One counter, one token bucket, one source of truth for how many requests are allowed.
- **Vending Machine** — `VendingMachine` itself — there is one machine, one inventory, one payment processor. Multiple instances of the machine object would model a different scenario.

---

## 🔬 Interview Q&As

### Q: "How do you make Singleton thread-safe?"
> Three options. Eager initialization: create the instance as a `static final` field — JVM class loading is thread-safe, no synchronization needed. Double-checked locking: `volatile` field + two null checks around a `synchronized` block — lazy, safe, efficient after first creation. Enum Singleton: let the JVM guarantee single creation; two lines, reflection-safe, serialization-safe. For most production use, Enum is the cleanest. For interviews, walk through all three to show you understand the tradeoffs.

### Q: "Why is `volatile` needed in double-checked locking?"
> `instance = new Pool()` is three steps at the hardware level: allocate memory, run constructor, assign reference to `instance`. Without `volatile`, the JVM or CPU can reorder these. A thread could see a non-null `instance` (step 3 happened) before the constructor finished (step 2 not done yet). `volatile` adds a memory barrier: writes to `instance` are flushed to main memory and visible to all threads before any subsequent reads. Without it, double-checked locking is broken even if it looks correct.

### Q: "What's wrong with Singleton in a unit-test context?"
> Singletons carry state across test runs. If Test A changes the Singleton's internal state and Test B runs after, Test B starts in a dirty state. There's no standard way to "reset" a Singleton. Solutions: expose a `resetForTesting()` package-private method (ugly), inject via interface so tests can pass a mock (better), or avoid manual Singleton entirely and use Spring's DI (best — Spring creates a new context per test class).

### Q: "Is a Spring `@Service` a Singleton?"
> Yes, by default. Spring creates one instance per ApplicationContext and injects the same instance everywhere it's needed. The difference from manual Singleton: Spring manages lifecycle (creation, injection, destruction) and makes testing easy — you can use `@MockBean` to replace the Singleton with a mock in tests. Manual `static getInstance()` Singletons don't have this luxury.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"Singleton ensures one instance and provides a global access point. Three variants: eager (static final, simplest), double-checked locking (volatile + synchronized, lazy + thread-safe), and enum (JVM-guaranteed, serialization-safe, Effective Java preferred). In production Spring apps, you don't write manual Singletons — @Service beans are singletons managed by the container. In an LLD interview, write DCL and explain why volatile is necessary."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. All three variants: eager, DCL, enum. Visual comparison table. Volatile explanation included. |
