# ☕ Java 8→21 Evolution — Deep Dive

> After this note you can walk through every major Java release from 8 to 21, explain WHY each feature was added (what pain it solved), and identify which version introduced records, sealed classes, pattern matching, and virtual threads.

---

## 🎯 The Problem This Solves

Java releases used to be monolithic — Java 7 to Java 8 was 3 years. Developers memorized "Java 8 = lambdas." Since Java 9, releases ship every 6 months, and features arrive incrementally as preview → final. A senior engineer who says "I know Java 8" and can't name what changed in 9, 11, 17, or 21 signals they haven't kept up in 8 years. But memorizing release notes is useless — understanding WHY each feature exists (what was painful before) is what makes the knowledge stick and the interview answer compelling.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **LTS (Long-Term Support)** | A Java release that receives security updates for years. LTS versions: 8, 11, 17, 21, 25. Non-LTS versions get updates only until the next release (6 months). Production systems should run on LTS. |
| **Preview feature** | A fully implemented feature shipped for developer feedback before finalization. Must be explicitly enabled (`--enable-preview`). Can change between releases. Example: pattern matching was preview in 14, finalized in 16. |
| **Incubator module** | An API shipped for experimentation (e.g., HTTP Client in Java 9). Lives in `jdk.incubator.*` namespace. May graduate to `java.*` or be removed. |
| **Record** | A compact syntax for immutable data carriers (Java 16). Auto-generates constructor, getters, `equals()`, `hashCode()`, and `toString()` from declared components. |
| **Sealed class** | A class or interface that declares exactly which classes may extend/implement it (Java 17). Enables exhaustive pattern matching. |
| **Pattern matching** | A language feature that combines type checking and casting into a single expression (Java 16 for `instanceof`, Java 21 for `switch`). |
| **Virtual thread** | A lightweight thread managed by the JVM, not the OS (Java 21). Millions can exist simultaneously. Blocking a virtual thread does NOT block the OS thread it's mounted on. |
| **Text block** | A multi-line string literal using triple quotes `"""` (Java 15). Handles indentation automatically. |
| **Module system (JPMS)** | Java Platform Module System (Java 9). Organizes code into modules with explicit dependencies and exports. `module-info.java` declares what a module provides and requires. |

---

## 🧠 Mental Model

Think of Java's evolution as solving one frustration per release, moving from **verbose ceremony** (Java 7-) toward **concise expression of intent** (Java 21). Each feature removes a specific kind of boilerplate: lambdas removed anonymous-inner-class ceremony, `var` removed type-repetition, records removed POJO boilerplate, sealed classes removed the "who extends this" guesswork, pattern matching removed cast-after-instanceof, and virtual threads removed the thread-pool-sizing puzzle for I/O-bound workloads.

The timeline has a clear arc: **Java 8 = functional programming arrives**, **Java 11 = modern baseline**, **Java 17 = data-oriented programming starts** (records + sealed), **Java 21 = concurrency reimagined** (virtual threads).

> If you can say "8 = lambdas + streams, 9 = modules + G1 default, 11 = var in lambdas + HTTP Client, 14 = records preview, 16 = records final + instanceof patterns, 17 = sealed classes, 21 = virtual threads + pattern matching switch" without notes, you have the evolution.

---

## 🎨 Visual — The Timeline

```
  Java Version Timeline (LTS versions marked with ⭐):

  ⭐ Java 8  (2014)  ─── THE BIG BANG
  │  Lambdas, Streams, Optional, java.time, default methods, Metaspace
  │
  │  Java 9  (2017)  ─── MODULARIZATION
  │  JPMS (modules), G1 GC default, JShell, List.of()/Map.of()
  │
  │  Java 10 (2018)  ─── LESS TYPING
  │  var (local variable type inference)
  │
  ⭐ Java 11 (2018)  ─── MODERN BASELINE
  │  var in lambdas, HTTP Client (final), String methods, single-file execution
  │
  │  Java 12-13       (text blocks preview, switch expressions preview)
  │
  │  Java 14 (2020)  ─── RECORDS PREVIEW + CMS REMOVED
  │  Records (preview), helpful NullPointerException messages, CMS GC removed
  │
  │  Java 15 (2020)  ─── TEXT BLOCKS + ZGC PRODUCTION
  │  Text blocks (final), ZGC production-ready, sealed classes (preview)
  │
  │  Java 16 (2021)  ─── RECORDS + PATTERN MATCHING
  │  Records (final), instanceof pattern matching (final)
  │
  ⭐ Java 17 (2021)  ─── SEALED CLASSES
  │  Sealed classes (final), stronger encapsulation of JDK internals
  │
  │  Java 18-20       (pattern matching for switch preview iterations)
  │
  ⭐ Java 21 (2023)  ─── VIRTUAL THREADS
     Virtual threads (final), pattern matching for switch (final),
     sequenced collections, record patterns, string templates (preview)

  ⭐ Java 25 (2025)  ─── NEXT LTS
     (Upcoming — further pattern matching, value objects)

KEY INVARIANT:
   LTS versions (8, 11, 17, 21, 25) are production milestones.
   Non-LTS versions are stepping stones — features preview there,
   finalize in LTS. Choose LTS for production.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

A developer who "knows Java 8" and hasn't tracked changes writes code like this:

```java
// 2014-era Java — works, but unnecessarily verbose in 2024+
HashMap<String, List<String>> groupedData = new HashMap<String, List<String>>();  // no diamond inference
// or:
HashMap<String, List<String>> groupedData = new HashMap<>();  // Java 7 diamond ✓

// Immutable list creation — verbose
List<String> items = Collections.unmodifiableList(Arrays.asList("a", "b", "c"));

// Data carrier — 60 lines for 3 fields
public class UserDTO {
    private final String name;
    private final String email;
    private final int age;

    public UserDTO(String name, String email, int age) {
        this.name = name;
        this.email = email;
        this.age = age;
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public int getAge() { return age; }

    @Override
    public boolean equals(Object o) { /* 10 lines */ }

    @Override
    public int hashCode() { return Objects.hash(name, email, age); }

    @Override
    public String toString() { return "UserDTO{name='" + name + "', ...}"; }
}

// Type checking + casting — two-step
if (shape instanceof Circle) {
    Circle c = (Circle) shape;  // redundant — I just checked it's a Circle
    double area = c.area();
}
```

None of this is wrong. It all compiles and runs. But modern Java eliminates every one of these friction points, and writing the old way signals to an interviewer that you're 8 years behind.

---

### Level 2 — The real mechanism

#### ⭐ Java 8 (2014, LTS) — The Functional Revolution

**What was painful before:** Passing behavior required anonymous inner classes. Date handling used the broken `java.util.Date` and `Calendar` APIs. Interface contracts couldn't evolve without breaking implementors.

**What changed:**

```java
// LAMBDAS + STREAMS — the signature feature
// Before: 5 lines of anonymous class for 1 line of logic
// After:
List<String> sorted = names.stream()
    .filter(n -> !n.isEmpty())
    .sorted(Comparator.comparing(String::length))
    .toList();   // toList() is Java 16; in Java 8: .collect(Collectors.toList())

// OPTIONAL — explicit nullable return type
Optional<User> user = repository.findById(id);
String name = user.map(User::getName).orElse("unknown");
// Before: null checks scattered everywhere, NullPointerException in production

// DEFAULT METHODS — interface evolution without breaking implementors
public interface Collection<E> {
    // Existing method — all implementors already have this
    boolean add(E e);

    // New in Java 8 — existing implementors get this for free
    default Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}

// java.time — replaced the broken Date/Calendar API
LocalDate today = LocalDate.now();
LocalDate nextWeek = today.plusWeeks(1);
// Immutable, thread-safe, clear API — unlike java.util.Date which was mutable and thread-unsafe
```

**JVM change:** PermGen (fixed-size memory for class metadata) → **Metaspace** (uses native memory, grows dynamically). Eliminated the dreaded `java.lang.OutOfMemoryError: PermGen space` that plagued application servers with frequent redeployments.

#### Java 9 (2017) — Modularization

**What was painful before:** classpath hell — any class could access any other class. No encapsulation at the package level. JDK itself was a monolith.

```java
// JPMS — module system
// module-info.java:
module com.walmart.orders {
    requires com.walmart.inventory;   // explicit dependency
    exports com.walmart.orders.api;   // only this package is visible outside
    // com.walmart.orders.internal is HIDDEN — even via reflection
}

// FACTORY METHODS — immutable collections in one line
List<String> list = List.of("a", "b", "c");          // immutable
Set<Integer> set = Set.of(1, 2, 3);                   // immutable
Map<String, Integer> map = Map.of("a", 1, "b", 2);   // immutable
// Before: Collections.unmodifiableList(Arrays.asList("a", "b", "c"))
// ✅ Thread-safe — immutable after creation

// JShell — REPL for Java
// $ jshell
// jshell> 2 + 2
// $1 ==> 4
```

**GC change:** G1 became the default garbage collector (replacing Parallel GC). G1 divides the heap into regions and collects the most garbage-filled regions first — better pause-time predictability.

#### Java 10 (2018) — `var`

**What was painful before:** repeating the type on both sides of an assignment.

```java
// Before Java 10:
Map<String, List<Customer>> customersByCity = new HashMap<String, List<Customer>>();
// The left side duplicates the right side's type information

// Java 10+:
var customersByCity = new HashMap<String, List<Customer>>();
// Compiler infers the type. Works ONLY for local variables with initializers.

// ⚠️ var is NOT dynamic typing — it's compile-time inference
// var x = "hello";   → x is String at compile time, forever
// x = 42;            → COMPILE ERROR — x is a String

// ⚠️ var CANNOT be used for: fields, method parameters, return types, or
//    local variables without initializers (var x; is illegal)
```

#### ⭐ Java 11 (2018, LTS) — Modern Baseline

```java
// var IN LAMBDAS — enables annotations on lambda parameters
list.stream()
    .filter((@NotNull var s) -> !s.isEmpty())   // annotation on lambda param
    .toList();
// Without var, you can't annotate lambda parameters

// HTTP CLIENT (final — was incubator in Java 9)
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .GET()
    .build();
HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
// Before: HttpURLConnection (terrible API) or third-party Apache HttpClient

// STRING METHODS
"  hello  ".strip();         // "hello" — Unicode-aware (unlike trim())
"  hello  ".stripLeading();  // "hello  "
"hello".repeat(3);           // "hellohellohello"
"hello".isBlank();           // false
"".isBlank();                // true
"  \t\n".isBlank();          // true

// SINGLE-FILE EXECUTION
// $ java HelloWorld.java    ← no javac step needed for single-file programs
```

#### Java 14 (2020) — Records Preview + Helpful NPE

```java
// HELPFUL NPE MESSAGES — finally tells you WHAT was null
// Before:  NullPointerException (at SomeClass.java:42)  ← which variable?!
// After:   NullPointerException: Cannot invoke "String.length()"
//          because the return value of "User.getName()" is null
// This alone saves hours of debugging in production log analysis

// RECORDS (preview — final in Java 16)
// See Java 16 section below
```

**GC change:** CMS (Concurrent Mark Sweep) collector **REMOVED** — not just deprecated, completely gone. G1 and ZGC are the replacements.

#### Java 15 (2020) — Text Blocks + ZGC Production

```java
// TEXT BLOCKS — multi-line strings
// Before:
String json = "{\n" +
    "  \"name\": \"Alice\",\n" +
    "  \"age\": 30\n" +
    "}";

// After:
String json = """
    {
      "name": "Alice",
      "age": 30
    }
    """;
// Indentation is relative to the closing """. Leading common whitespace is stripped.
// No more \\n concatenation for SQL, JSON, HTML, or email templates.
```

**GC change:** ZGC became production-ready (no longer experimental). Sub-millisecond pause times regardless of heap size — can handle multi-terabyte heaps.

#### Java 16 (2021) — Records + Pattern Matching for instanceof

```java
// RECORDS — immutable data carriers in one line
public record UserDTO(String name, String email, int age) {}
// Auto-generates: constructor, getters (name(), email(), age()),
// equals(), hashCode(), toString()
// Fields are final — no setters, no mutation
// ✅ Thread-safe — immutable after construction

// Replaces the 60-line POJO from Level 1

// PATTERN MATCHING FOR instanceof
// Before (Java 15 and earlier):
if (shape instanceof Circle) {
    Circle c = (Circle) shape;   // redundant cast
    double area = c.area();
}

// After (Java 16+):
if (shape instanceof Circle c) {   // check + cast in one step
    double area = c.area();        // c is already cast, in scope
}
// 'c' is only in scope where the pattern matched — no accidental use outside
```

#### ⭐ Java 17 (2021, LTS) — Sealed Classes

**What was painful before:** you couldn't control who extends your class. An `abstract class Shape` could be extended by anyone, anywhere — you couldn't write exhaustive switch statements because you couldn't guarantee all subtypes were known.

```java
// SEALED CLASSES — control the type hierarchy
public sealed interface Shape
    permits Circle, Rectangle, Triangle {
    double area();
}

public record Circle(double radius) implements Shape {
    public double area() { return Math.PI * radius * radius; }
}

public record Rectangle(double width, double height) implements Shape {
    public double area() { return width * height; }
}

public record Triangle(double base, double height) implements Shape {
    public double area() { return 0.5 * base * height; }
}

// The compiler KNOWS all possible subtypes → exhaustive switch (Java 21)
```

#### ⭐ Java 21 (2023, LTS) — Virtual Threads + Pattern Matching Switch

**What was painful before:** Platform threads are OS threads — each costs ~1 MB of stack memory. A server with 1000 concurrent connections needs 1000 threads = 1 GB just for stacks. Thread pool sizing became a tuning art: too few threads = low throughput on I/O-bound workloads, too many = excessive memory + context-switching overhead.

```java
// VIRTUAL THREADS — lightweight threads managed by the JVM
// Before (platform thread — OS-managed):
ExecutorService executor = Executors.newFixedThreadPool(200);
// 200 threads × 1 MB = 200 MB stack memory
// If tasks block on I/O, threads sit idle. Need more threads → more memory.

// After (virtual thread — JVM-managed):
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> {
            // This blocks on I/O — but the virtual thread is unmounted
            // from the carrier (OS) thread, which goes to serve another
            // virtual thread. No OS thread sits idle.
            String result = httpClient.send(request, BodyHandlers.ofString()).body();
            return result;
        });
    }
}
// 100,000 concurrent tasks with a handful of OS threads
// No thread pool sizing — create one virtual thread per task

// PATTERN MATCHING FOR SWITCH (final)
String description = switch (shape) {
    case Circle c    -> "Circle with radius " + c.radius();
    case Rectangle r -> "Rectangle " + r.width() + "x" + r.height();
    case Triangle t  -> "Triangle with base " + t.base();
    // No default needed — sealed interface, compiler knows all cases
};

// SEQUENCED COLLECTIONS — first/last access on ordered collections
SequencedCollection<String> list = new ArrayList<>();
list.addFirst("first");
list.addLast("last");
String first = list.getFirst();
String last = list.getLast();
list.reversed();   // reversed view — no copying
// Before: list.get(0) and list.get(list.size() - 1) — verbose and error-prone for Deque
```

> **What the JVM is actually doing with virtual threads:** Virtual threads are implemented using continuations. When a virtual thread blocks (on I/O, sleep, lock), the JVM captures the thread's execution state (a continuation), unmounts it from the carrier OS thread, and schedules another virtual thread on that same carrier. When the I/O completes, the virtual thread is remounted on any available carrier. The carrier thread pool is typically sized to the number of CPU cores (via `ForkJoinPool`). This means 1 million virtual threads can run on 8 OS threads — the OS manages 8 threads, the JVM manages the scheduling of 1 million.

---

### Level 3 — The subtleties

#### 3.1 — var does NOT mean dynamic typing

```java
var x = "hello";   // x is String — FOREVER. Not Object, not dynamic.
// x = 42;         // COMPILE ERROR — String expected

// var CANNOT be used for:
// var field;              // fields — no
// public var getX() {}    // return types — no
// void foo(var x) {}      // method parameters — no
// var x;                  // uninitialized local — no
// var lambda = (s) -> s;  // ambiguous target type — no
```

#### 3.2 — Records are NOT beans

```java
// Records use component accessors, NOT getBean-style getters:
record User(String name, int age) {}
User u = new User("Alice", 30);
u.name();    // ✅ component accessor — not getName()
// u.getName();  // ❌ COMPILE ERROR — no such method

// Records are implicitly final — cannot be extended
// Records cannot extend other classes (they extend java.lang.Record)
// Records CAN implement interfaces
// Records CAN have: static fields, static methods, instance methods, custom constructors
// Records CANNOT have: instance fields beyond the components
```

#### 3.3 — When NOT to use virtual threads

Virtual threads are for I/O-bound workloads (HTTP calls, database queries, file reads). They are NOT for CPU-bound workloads:

```java
// ✅ Good use — I/O bound:
Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
    return httpClient.send(request, BodyHandlers.ofString());
    // Virtual thread unmounts during I/O → carrier thread serves others
});

// ❌ Bad use — CPU bound:
Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
    return fibonacci(1_000_000);
    // CPU-bound → virtual thread never unmounts → occupies carrier thread
    // Same as a platform thread, but with overhead of virtual thread scheduling
});
// For CPU-bound: use ForkJoinPool or platform thread pool
```

**Also avoid:** `synchronized` blocks pin the virtual thread to its carrier. Use `ReentrantLock` instead — it supports virtual thread unmounting.

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "`var` makes Java dynamically typed like JavaScript" | `var` is compile-time type inference — the type is fixed at declaration and never changes. It's the same as Kotlin's `val`/`var` or C#'s `var`. The compiled bytecode is identical to writing the explicit type. |
| "Records replace all POJOs/DTOs" | Records are immutable (no setters), can't extend classes, and generate `equals`/`hashCode` from ALL fields. They're for data carriers. If you need mutability, inheritance, or selective equality, use a class. JPA entities generally can't be records (need mutable fields, no-arg constructor). |
| "Virtual threads replace thread pools" | Virtual threads replace thread pools for I/O-bound workloads. For CPU-bound work, platform thread pools (`ForkJoinPool`) are still optimal — virtual threads add scheduling overhead with no benefit when the thread never blocks. |
| "Java 8 is still fine for production" | Java 8 reached end of public updates in 2019 (Oracle). It lacks: records, sealed classes, text blocks, pattern matching, virtual threads, ZGC, helpful NullPointerException messages, HTTP Client, and 8 years of JVM performance improvements. Every production system should be on Java 17 or 21. |
| "Sealed classes are just `final` classes" | `final` prevents ALL extension. `sealed` allows extension by SPECIFIC listed classes — enabling exhaustive pattern matching while still supporting polymorphism. `sealed` + `permits` gives you a closed type hierarchy that the compiler can reason about. |

---

## 🐞 Production Footguns

---

> **Footgun: `synchronized` pinning virtual threads**
> **Cost:** Performance cliff
>
> A team migrated a Spring Boot service to virtual threads (Java 21) by switching to `Executors.newVirtualThreadPerTaskExecutor()`. Latency initially improved. Under load, it degraded to worse than before. The cause: a legacy `synchronized` block around a database connection pool. `synchronized` PINS the virtual thread to its carrier OS thread — the carrier can't serve other virtual threads while waiting for the lock. With 8 carriers and 8 pinned virtual threads, the server was effectively single-threaded.

```java
// ❌ The trap: synchronized pins virtual threads
private final Object lock = new Object();

public Data fetchData() {
    synchronized (lock) {   // PINS virtual thread to carrier
        return db.query("SELECT ...");   // I/O blocks while pinned
        // Carrier thread is occupied — can't serve other virtual threads
    }
}

// ✅ The fix: use ReentrantLock — supports virtual thread unmounting
private final ReentrantLock lock = new ReentrantLock();

public Data fetchData() {
    lock.lock();
    try {
        return db.query("SELECT ...");   // virtual thread unmounts during I/O
    } finally {
        lock.unlock();
    }
}
```

---

> **Footgun: List.of() immutability surprises**
> **Cost:** UnsupportedOperationException in production
>
> A developer used `List.of("a", "b")` to create a list and later attempted to add elements. `List.of()` returns an immutable list — `add()`, `remove()`, and `set()` throw `UnsupportedOperationException`. The bug appeared only on a code path that conditionally added elements, so it passed testing but crashed in production on specific inputs.

```java
// ❌ The trap: treating List.of() as mutable
List<String> items = List.of("a", "b");
items.add("c");   // UnsupportedOperationException at runtime

// ✅ The fix: use ArrayList if you need mutability
List<String> items = new ArrayList<>(List.of("a", "b"));
items.add("c");   // works
// ⚠️ NOT thread-safe — use CopyOnWriteArrayList or synchronized wrapper for concurrent access
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `functional-interfaces.md` | Java 8 introduced lambdas + functional interfaces as the foundation of the Stream API. Understanding why Java 8 was "the big bang" requires understanding what programming WITHOUT functional interfaces looked like (anonymous inner classes for every behavior). |
| `generics-type-erasure.md` | Records are generic-friendly (`record Pair<A, B>(A first, B second) {}`). `var` infers generic types (`var map = new HashMap<String, List<Integer>>()` — inferred as `HashMap<String, List<Integer>>`). Pattern matching for switch uses type parameters for matching. |
| `../Spring/DeepDive/02-spring-core.md` | Spring Boot 3 requires Java 17+. Spring 6 uses `sealed` interfaces for internal extensibility. Virtual threads (Java 21) integrate with Spring's async request handling — `spring.threads.virtual.enabled=true` in Boot 3.2+. |
| `gc-deep-dive.md` (planned — Note #25) | Java 9: G1 default. Java 14: CMS removed. Java 15: ZGC production-ready. Java 21: generational ZGC. The GC evolution is tightly coupled to the Java version timeline. |
| `virtual-threads-java21.md` (planned — Note #22) | This note covers the what/why of virtual threads at a high level. The planned Note #22 goes deep into the JVM mechanics: continuations, carrier scheduling, pinning detection, structured concurrency. |

---

## 🎙️ Interview Deep Questions

**Q1. What are the LTS versions of Java and why do they matter?**

> The LTS (Long-Term Support) versions are 8, 11, 17, 21, and 25. They receive security updates and bug fixes for years — Oracle provides at least 5 years of support for each. Non-LTS versions (9, 10, 12-16, 18-20) receive updates only until the next release — 6 months. Production systems should run on LTS because you need long-term security patches without being forced to upgrade every 6 months. For new projects in 2026, Java 21 is the correct baseline — it has virtual threads, records, sealed classes, pattern matching, and ZGC, all in their final form.

**Q2. What are records and when would you NOT use them?**

> Records (Java 16) are compact, immutable data carriers. `record User(String name, int age) {}` auto-generates: a canonical constructor, component accessors (`name()`, `age()` — NOT `getName()`), `equals()` comparing all components, `hashCode()` using all components, and `toString()`. You would NOT use records when you need mutability (records have no setters), when you need to extend another class (records implicitly extend `java.lang.Record`), when you need selective equality (records compare ALL components — you can't exclude fields), or for JPA entities (Hibernate requires no-arg constructors and mutable fields for dirty checking). Records are ideal for DTOs, API response objects, value objects, and HashMap keys.

**Q3. Explain virtual threads. When should you use them and when should you NOT?**

> Virtual threads (Java 21) are lightweight threads managed by the JVM, not the OS. A platform thread maps 1:1 to an OS thread and costs ~1 MB of stack memory. Virtual threads are scheduled onto a small pool of carrier (platform) threads — when a virtual thread blocks on I/O, it's unmounted from the carrier, which immediately picks up another virtual thread. This means you can have millions of concurrent tasks with a handful of OS threads. Use them for I/O-bound workloads: HTTP calls, database queries, file operations — anywhere a thread spends most of its time waiting. Do NOT use them for CPU-bound work (the thread never blocks, so unmounting never happens — no benefit). And avoid `synchronized` blocks — they pin the virtual thread to the carrier, defeating the purpose. Use `ReentrantLock` instead.

**Q4. What is the difference between `var` and dynamic typing?**

> `var` is compile-time type inference — the compiler deduces the type from the initializer expression and fixes it permanently. `var x = "hello"` makes `x` a `String` at compile time. `x = 42` is a compile error. The bytecode is identical to `String x = "hello"`. In dynamic typing (Python, JavaScript), `x = "hello"` followed by `x = 42` is legal — the type is checked at runtime. Java's `var` removes the need to write the type name, not the type system itself. It cannot be used for fields, method parameters, or return types — only local variables with initializers.

**Q5. What did Java 9's module system (JPMS) solve? Why do most developers not use it?**

> JPMS solved two problems: classpath hell (any class could access any other class, including JDK internals like `sun.misc.Unsafe`) and the monolithic JDK (every Java app shipped with the entire JDK, even if it used 5% of it). Modules declare explicit dependencies (`requires`) and explicit exports (`exports`) — only exported packages are accessible to other modules. `jlink` can create minimal custom JDK runtimes with only the modules your app needs. Most application developers don't use modules because: their existing codebases predate Java 9 and would require significant refactoring, most frameworks (Spring, Hibernate) work on the classpath, and the module system has a steep learning curve for complex dependency graphs. It IS used internally by the JDK itself and by library authors who want strong encapsulation.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Java releases every 6 months since Java 9, with LTS versions (8, 11, 17, 21) for production. The arc: Java 8 = functional programming, Java 17 = data-oriented programming, Java 21 = lightweight concurrency.
>
> **Part 2 — How/Why (30s):** Java 8 added lambdas, streams, and Optional — eliminating anonymous-inner-class boilerplate. Java 11 added `var` and the HTTP Client. Java 16 added records (one-line immutable DTOs with auto-generated equals/hashCode) and pattern matching for instanceof (check + cast in one step). Java 17 added sealed classes for closed type hierarchies. Java 21 added virtual threads — JVM-managed lightweight threads that unmount from OS threads during I/O, allowing millions of concurrent tasks with a handful of platform threads. Each feature solves a specific pain: records kill POJO boilerplate, sealed classes enable exhaustive switch, virtual threads eliminate thread-pool-sizing puzzles.
>
> **Part 3 — Gotcha (20s):** Two traps: `synchronized` blocks pin virtual threads to their carrier OS thread, defeating the concurrency benefit — use `ReentrantLock` instead. And `List.of()` / `Map.of()` return truly immutable collections — `add()` throws `UnsupportedOperationException` at runtime, not compile time, which surprises developers who expect the old `Arrays.asList()` behavior.

---

## 🧾 TL;DR

- LTS versions: 8, 11, 17, 21, 25. Use LTS for production. Java 21 is the current baseline.
- Java 8: lambdas, streams, Optional, java.time, default methods, Metaspace.
- Java 9: modules (JPMS), G1 default, `List.of()`/`Map.of()`.
- Java 10: `var` (local type inference — NOT dynamic typing).
- Java 11: `var` in lambdas, HTTP Client (final), String methods.
- Java 14: helpful NPE messages, CMS GC removed.
- Java 16: records (final), `instanceof` pattern matching (final).
- Java 17: sealed classes (final), stronger JDK encapsulation.
- Java 21: virtual threads (final), pattern matching switch (final), sequenced collections.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #5 (Phase 1) of the JavaBackend KB completion roadmap. Covers Java 8 through 21 with WHY reasoning for each feature: lambdas (anonymous class boilerplate), var (type repetition), records (POJO boilerplate), sealed classes (open hierarchy problem), pattern matching (redundant casting), virtual threads (thread-per-connection scalability). Includes: PermGen→Metaspace, G1→ZGC GC evolution, CMS removal, JPMS modules, text blocks, sequenced collections. Subtleties: var is NOT dynamic typing, records are NOT beans, virtual threads vs CPU-bound, synchronized pinning. |
