# JPMorgan — Core Java Questions Ranked by Frequency

> `⭐⭐⭐` = reported in 3+ interview rounds | `⭐⭐` = 2 rounds | `⭐` = once, high-signal
> **Note:** JPMC is NOT a "define this" interview — follow every answer with "in my project I used this when…"

---

## 🔴 Tier 1 — Asked in Almost Every Round

### Q1 `⭐⭐⭐` — How does HashMap work internally?

**What they expect:**
- `put(key, val)` → `hashCode(key) % buckets.length` → bucket index → store as `Node(hash, key, value, next)`
- **Default capacity 16**, load factor 0.75 → resize at 12 entries
- **Collision** → linked list at same bucket → from Java 8, treeify at threshold 8 (O(n) → O(log n))
- Keys must implement `hashCode()` + `equals()`; best keys are immutable (String, Integer)

**Follow-up:** *"What if two keys have the same hashCode?"*
> They land in the same bucket. Java uses `equals()` to walk the chain and find the exact key. If not found → appended. If found → value overwritten.

**Follow-up:** *"What is a poor hashCode() implementation?"*
> One that always returns a constant → every key collides → HashMap becomes O(n) linked list.

---

### Q2 `⭐⭐⭐` — HashMap vs ConcurrentHashMap vs SynchronizedMap

| | HashMap | ConcurrentHashMap | Collections.synchronizedMap() |
|---|---|---|---|
| Thread-safe | ❌ No | ✅ Yes | ✅ Yes |
| Lock granularity | None | Segment / bucket-level (Java 8+: CAS + sync on individual bin) | Entire object |
| Null keys/values | ✅ Both | ❌ Neither (NPE thrown) | ✅ Both |
| Performance | Fastest single-thread | Fastest multi-thread | Slowest (global lock) |
| Compound ops safe | N/A | ❌ No (putIfAbsent is atomic, iteration is not) | ❌ No |

**Follow-up:** *"Can you safely iterate a ConcurrentHashMap while another thread modifies it?"*
> Yes — it won't throw ConcurrentModificationException (unlike HashMap which uses fail-fast iterators). But you may or may not see the latest writes — the view is weakly consistent.

**Follow-up:** *"SynchronizedMap wraps get() in a lock — why can't I rely on it for compound ops?"*
> `synchronizedMap.putIfAbsent(k, v)` is NOT atomic — two threads can both pass the `get()` null check before either calls `put()`. You'd need `ConcurrentHashMap.putIfAbsent()` which is truly atomic.

---

### Q3 `⭐⭐⭐` — String: `==` vs `.equals()`, String Pool, Immutability

**`==` vs `.equals()`:**
```java
String a = "hello";        // points to String pool
String b = "hello";        // same pool entry — same reference
String c = new String("hello"); // new heap object

a == b        // true  (same pool ref)
a == c        // false (different objects)
a.equals(c)   // true  (same char sequence)
```

**Why is String immutable?**
1. **String pool safety** — multiple references share one object; mutation would corrupt all.
2. **HashMap key safety** — hashCode is cached in String; mutable key would break bucket lookup.
3. **Thread safety** — shared immutable objects need no synchronization.
4. **Security** — class names, file paths, DB URLs passed as Strings can't be altered in-flight.

**Follow-up:** *"How do you create a truly immutable class?"*
```java
public final class ImmutableEmployee {           // (1) final class
    private final String name;                   // (2) final fields
    private final List<String> skills;

    public ImmutableEmployee(String name, List<String> skills) {
        this.name = name;
        this.skills = List.copyOf(skills);       // (3) defensive copy of mutable field
    }

    public String getName() { return name; }
    public List<String> getSkills() { return skills; }  // (4) no setter
}
```

---

### Q4 `⭐⭐⭐` — Sort Employee by salary (written in IDE)

```java
// Comparable — natural ordering (inside the class)
public class Employee implements Comparable<Employee> {
    String name;
    int salary;

    @Override
    public int compareTo(Employee o) {
        // negative = this comes first, 0 = equal, positive = other comes first
        // Integer.compare avoids integer overflow that (this.salary - o.salary) can cause
        return Integer.compare(this.salary, o.salary); // ascending
    }
}

// Comparator — external, flexible
List<Employee> emps = ...;
emps.sort(Comparator.comparingInt(Employee::getSalary));          // ascending
emps.sort(Comparator.comparingInt(Employee::getSalary).reversed()); // descending

// Remove 4th highest (follow-up asked at JPMC)
emps.sort(Comparator.comparingInt(Employee::getSalary).reversed());
emps.remove(3); // 0-indexed → 4th highest
```

**Follow-up:** *"Comparable vs Comparator?"*
> `Comparable` = "I know how to compare myself" — `compareTo` in same class — one natural ordering.
> `Comparator` = "external judge" — separate class or lambda — multiple orderings possible.

---

## 🟡 Tier 2 — Asked Frequently in Technical Round

### Q5 `⭐⭐` — Singleton class (write in IDE)

```java
// Thread-safe Singleton — Double-checked locking
public class Singleton {
    private static volatile Singleton instance;

    private Singleton() {}  // private constructor

    public static Singleton getInstance() {
        if (instance == null) {                      // first check (no lock)
            synchronized (Singleton.class) {
                if (instance == null) {              // second check (with lock)
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

> `volatile` prevents instruction reordering — without it, a partially constructed object could be visible to another thread after the first null-check passes.

**JPMC follow-up:** *"Is enum singleton better?"*
> Yes — `enum` singleton is serialization-safe and reflection-safe (JVM guarantees one instance per enum constant). `private Singleton()` can be broken via reflection; enum cannot.

---

### Q6 `⭐⭐` — Exception Handling: try-with-resources, multi-catch

```java
// try-with-resources — auto-closes Closeable resources
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    // use conn and ps
} catch (SQLException e) {
    // conn and ps are already closed here
}

// multi-catch (Java 7+)
try {
    riskyMethod();
} catch (IOException | SQLException e) {
    log.error("Failed", e);
}
```

**Follow-up:** *"Checked vs Unchecked exceptions?"*
> Checked = compiler forces you to handle (IOException, SQLException) — used for recoverable conditions.
> Unchecked = RuntimeException subclasses (NullPointerException, IllegalArgumentException) — programming errors, don't force callers to handle.

---

### Q7 `⭐⭐` — Generics

```java
// Bounded wildcard — "read-only producer"
// List<? extends Number> accepts List<Integer>, List<Double>, List<Long> — any Number subtype
// You can READ from it (every element is guaranteed to be a Number)
// You CANNOT add to it (compiler doesn't know the exact subtype — adding Integer to a List<Double> would be wrong)
public double sum(List<? extends Number> list) {
    return list.stream().mapToDouble(Number::doubleValue).sum();
}

// Lower bound — "write-only consumer"
// List<? super Integer> accepts List<Integer>, List<Number>, List<Object>
// You CAN add Integer (guaranteed to fit any supertype of Integer)
// You CANNOT read as Integer (compiler only knows it's "some supertype", returns Object)
public void addNumbers(List<? super Integer> list) {
    list.add(1); list.add(2);
}
```

> **PECS:** Producer Extends (read from), Consumer Super (write to) — the rule for choosing wildcard bounds.

---

### Q8 `⭐⭐` — Design Patterns (name + one-liner + code)

JPMC asks you to *explain design patterns with examples from your project*, not recite GoF definitions.

| Pattern | One-liner | When JPMC asks this |
|---|---|---|
| **Singleton** | One instance, global access | Config service, DB connection pool |
| **Factory** | Decouple creation from usage | Creating different payment processors |
| **Builder** | Step-by-step object construction | Building complex request objects |
| **Strategy** | Swap algorithm at runtime | Different sorting strategies, pricing rules |
| **Observer** | Notify dependents on state change | Event-driven systems, Kafka consumers |
| **Decorator** | Add behavior without subclassing | Logging, auth wrapping |

---

### Q9 `⭐⭐` — Java new features — Java 8 → 21

| Version | Key features | JPMC probe |
|---|---|---|
| Java 8 | Streams, Lambdas, Optional, default methods, `CompletableFuture` | "Write a stream pipeline to…" |
| Java 11 | `String::isBlank`, `Files.readString()`, HTTP Client | Rarely asked |
| Java 14–16 | Records, sealed classes, pattern matching `instanceof` | "What are records?" |
| Java 17 | LTS — sealed classes stable, pattern matching | Spring Boot 3 requires 17+ |
| Java 21 | **Virtual Threads** (Project Loom), sequenced collections, record patterns | "What is a Virtual Thread?" |

**Virtual Thread Q (asked directly):**
> Virtual threads are lightweight threads managed by the JVM, not the OS. Unlike platform threads (1:1 mapping to OS threads, ~1 MB stack each), virtual threads are cheap (~few KB) and can scale to millions. They don't replace thread pools for CPU-bound work — they shine for blocking I/O (DB calls, HTTP calls) where you'd otherwise need async/reactive code. Available since Java 21 via `Thread.ofVirtual().start(runnable)`.

---

### Q10 `⭐⭐` — Garbage Collection

**Follow-up that often appears after HashMap question:**
> "How does GC know a HashMap entry can be collected if I clear the map?"

1. GC traces **live references** from GC roots (stack vars, static fields).
2. When map is cleared, `Node` objects lose their only strong reference → eligible for collection.
3. `WeakHashMap` uses `WeakReference` — entries are collected when the key has no other strong reference (useful for caches).

**Generations:**
- **Young Gen** (Eden + Survivor): new objects → Minor GC (fast)
- **Old Gen (Tenured)**: objects surviving multiple minor GCs → Major GC (slow, Stop-the-World)
- **Metaspace**: class metadata (replaced PermGen in Java 8)

---

## 🟢 Tier 3 — Seen Once, Worth Knowing

### Q11 `⭐` — Runnable vs Callable

```java
Runnable r = () -> System.out.println("no return");

Callable<Integer> c = () -> {
    return 42;          // can return a value
    // can also throw checked exceptions — Runnable cannot
};

Future<Integer> f = executor.submit(c);
int result = f.get(); // blocks until done
```

> `Runnable` = fire-and-forget. `Callable` = fire-and-get-result. Use `Callable` when you need the computation result or want to propagate checked exceptions.

---

### Q12 `⭐` — How does `hashCode()` affect HashMap performance?

> If `hashCode()` always returns the same constant → all keys land in bucket 0 → the bucket becomes a linked list (or tree) → `get()`/`put()` degrade from O(1) to O(n) or O(log n). This is why keys should be immutable — if key changes after insertion, the stored hashCode no longer matches the recomputed one → key becomes unreachable.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created from 2024–2026 JPMC interview reports. |
