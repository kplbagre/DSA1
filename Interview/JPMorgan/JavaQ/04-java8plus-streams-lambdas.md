# JPMorgan — Java 8–21 Features: Streams, Lambdas, Optionals

> `⭐⭐⭐` = in OA + multiple rounds | `⭐⭐` = technical round | `⭐` = senior roles
> **Critical:** JPMC OA has directly asked Java Streams as a coding question (not just a concept).

---

## 🔴 Tier 1 — OA + Round 1 Constant

### Q1 `⭐⭐⭐` — Java Streams pipeline (was literally the OA question)

**The exact OA question reported:**
> "Given an int array, remove all odd numbers, multiply each remaining number by a constant C, return the sum — **must use Java Streams**."

```java
int[] arr = {1, 2, 3, 4, 5, 6};
int C = 3;

int result = Arrays.stream(arr)
    .filter(n -> n % 2 == 0)   // keep even numbers
    .map(n -> n * C)            // multiply by C
    .sum();                     // terminal operation

// result = (2+4+6) * 3 = 36
```

**Core pipeline methods to know cold:**

```java
List<Employee> emps = getEmployees();

// filter — keep matching elements
emps.stream().filter(e -> e.getSalary() > 50000)

// map — transform each element
emps.stream().map(Employee::getName)  // Stream<String>

// flatMap — flatten nested collections
// map() here would give Stream<List<String>> — a stream of lists
// flatMap() merges all inner lists into one flat Stream<String>
emps.stream().flatMap(e -> e.getSkills().stream()) // Stream<String> all skills, not Stream<List<String>>

// collect — terminal, gather results
.collect(Collectors.toList())
.collect(Collectors.toSet())
.collect(Collectors.groupingBy(Employee::getDept))  // Map<Dept, List<Emp>>
.collect(Collectors.counting())
.collect(Collectors.joining(", "))

// reduce — fold all elements into one value
// first arg = identity (starting value, returned if stream is empty)
// second arg = accumulator (combines running result with next element)
.reduce(0, Integer::sum)  // 0 + e1 + e2 + e3 + ...

// distinct, sorted, limit, skip
.distinct().sorted(Comparator.comparing(Employee::getSalary)).limit(5)

// anyMatch / allMatch / noneMatch (short-circuit)
emps.stream().anyMatch(e -> e.getSalary() > 100000) // boolean

// findFirst / findAny
emps.stream().filter(...).findFirst() // Optional<Employee>
```

---

### Q2 `⭐⭐⭐` — Stream operations on Employee (common technical round exercise)

```java
// "Sort employees by salary descending and print top 3"
emps.stream()
    .sorted(Comparator.comparingInt(Employee::getSalary).reversed())
    .limit(3)
    .forEach(System.out::println);

// "Find average salary per department"
Map<String, Double> avgByDept = emps.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.averagingInt(Employee::getSalary)
    ));

// "Find employee with max salary"
Optional<Employee> richest = emps.stream()
    .max(Comparator.comparingInt(Employee::getSalary));

richest.ifPresent(e -> System.out.println(e.getName()));

// "Count employees earning > 60K per department"
Map<String, Long> countByDept = emps.stream()
    .filter(e -> e.getSalary() > 60000)
    .collect(Collectors.groupingBy(Employee::getDept, Collectors.counting()));
```

---

### Q3 `⭐⭐⭐` — Optional (avoid NullPointerException)

```java
// WRONG — Optional is a container, not a null check
Optional<User> user = findUser(id);
if (user.isPresent()) {
    return user.get().getEmail(); // this is fine but verbose
}

// RIGHT — use functional style
return findUser(id)
    .map(User::getEmail)
    .orElse("no-email@example.com");  // default value

// orElseThrow — use when null should be a bug
User user = findUser(id)
    .orElseThrow(() -> new UserNotFoundException(id));

// filter on Optional
Optional<User> active = findUser(id)
    .filter(u -> u.isActive());
```

**JPMC follow-up:** *"Should you use Optional as a method parameter?"*
> No — it's verbose and callers can still pass `null` (Optional.of(null) throws NPE). Use `@Nullable`/`@NonNull` annotations or just check in method body. Optional is designed as a return type only.

---

## 🟡 Tier 2 — Technical Round Depth

### Q4 `⭐⭐` — Functional interfaces: Predicate, Function, Supplier, Consumer

```java
// Predicate<T> — test condition, return boolean
Predicate<Integer> isEven = n -> n % 2 == 0;
Predicate<Integer> isPositive = n -> n > 0;
Predicate<Integer> combined = isEven.and(isPositive); // compose

// Function<T, R> — map T to R
Function<String, Integer> length = String::length;
Function<String, String> toUpper = String::toUpperCase;
// andThen: apply toUpper first, then apply the next function to toUpper's result
// compose: apply the inner function first, then toUpper (opposite order of andThen)
Function<String, String> composed = toUpper.andThen(s -> s + "!"); // "hello" → "HELLO" → "HELLO!"

// Supplier<T> — provide a value, no input
Supplier<List<String>> listFactory = ArrayList::new;

// Consumer<T> — accept value, return nothing
Consumer<String> print = System.out::println;
Consumer<String> log = s -> logger.info(s);
Consumer<String> both = print.andThen(log);

// BiFunction<T, U, R>
BiFunction<String, Integer, String> repeat = (s, n) -> s.repeat(n);
```

---

### Q5 `⭐⭐` — Method references

```java
// Static method reference
Function<String, Integer> parseInt = Integer::parseInt;

// Instance method reference (unbound)
Function<String, String> toUpper = String::toUpperCase;

// Instance method reference (bound — specific instance)
String prefix = "Hello";
Predicate<String> startsWith = s -> s.startsWith(prefix);
// same as: prefix::startsWith (bound to the specific prefix object)

// Constructor reference
Supplier<ArrayList<String>> newList = ArrayList::new;
```

---

### Q6 `⭐⭐` — Parallel streams: when to use and when NOT to

```java
// Parallel stream — splits work across ForkJoinPool.commonPool()
long sum = LongStream.rangeClosed(1, 1_000_000)
    .parallel()
    .sum();

// When it helps: CPU-bound, large dataset, no shared mutable state
// When it HURTS:
// (1) Small dataset — thread overhead > gain
// (2) I/O-bound — threads block, no CPU parallelism gained
// (3) Shared mutable state — race conditions (no synchronization in stream ops)
// (4) Ordered operations — ordering with parallel streams is expensive
```

**JPMC probe:** *"Your colleague used `.parallelStream()` on a List of 50 elements for a performance optimization. What do you say?"*
> Likely a pessimization. For 50 elements, the ForkJoin split-and-merge overhead exceeds any gain. Parallel streams are beneficial at 10K+ elements for CPU-bound work. I'd benchmark before accepting the change.

---

### Q7 `⭐⭐` — CompletableFuture (async, non-blocking)

```java
// Run async, chain transformations
CompletableFuture<User> userFuture = CompletableFuture
    .supplyAsync(() -> fetchUserFromDB(id))         // starts async in ForkJoinPool
    .thenApply(user -> enrichWithProfile(user))     // runs on same thread as previous stage (sync hand-off)
    .thenApplyAsync(user -> callExternalApi(user)); // submits to ForkJoinPool again — use when the next step is also slow/blocking

// Combine two independent async calls
// user and orders run in PARALLEL — neither waits for the other
// thenCombine is called only when BOTH are complete, with both results
CompletableFuture<User> user = CompletableFuture.supplyAsync(() -> fetchUser(id));
CompletableFuture<Orders> orders = CompletableFuture.supplyAsync(() -> fetchOrders(id));
CompletableFuture<Response> combined = user.thenCombine(orders, (u, o) -> build(u, o));

// Error handling
// exceptionally: only called if previous stage threw — returns a fallback value, chain continues
// handle: always called (like finally) — receives (result, exception), one of which is null
CompletableFuture.supplyAsync(() -> riskyOp())
    .exceptionally(ex -> fallbackValue)             // recovery — swallows the exception
    .handle((result, ex) -> ex != null ? fallback : result); // inspect both success and failure paths
```

---

### Q8 `⭐` — Virtual Threads (Java 21) — "What is a Virtual Thread?"

> Virtual threads (Project Loom) are JVM-managed lightweight threads. Unlike platform threads (OS-managed, ~1MB stack, expensive context switch), virtual threads have a tiny stack (~few KB) and are multiplexed by the JVM onto a small number of OS carrier threads.

```java
// Create a virtual thread
Thread vt = Thread.ofVirtual().start(() -> {
    // blocking I/O here does NOT block an OS thread
    // JVM unmounts this virtual thread from the carrier and mounts another
    String result = callBlockingApi();
    System.out.println(result);
});

// Virtual thread per task (replaces thread pool for I/O-bound work)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> handleRequest()); // 100K virtual threads: fine
    }
}
```

**When NOT to use virtual threads:**
- CPU-bound work (no blocking) — virtual threads don't help; fixed pool is better
- When you need thread-local variables extensively (they work, but can be heavier than expected with many virtual threads)

---

### Q9 `⭐` — Records (Java 14+)

```java
// Immutable data carrier — replaces DTO boilerplate
public record Employee(String name, int salary) {
    // Compiler auto-generates: constructor, getters, equals, hashCode, toString
}

Employee e = new Employee("Kapil", 150000);
e.name();    // getter (not getName())
e.salary();

// Compact canonical constructor for validation
public record Employee(String name, int salary) {
    public Employee {
        Objects.requireNonNull(name, "name required");
        if (salary < 0) throw new IllegalArgumentException("salary must be >= 0");
    }
}
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created from 2024–2026 JPMC interview reports. |
