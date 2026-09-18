# EPAM Systems — Java Backend Technical Round · Research Findings
### 90-min Technical Interview · Java Backend Engineer · ~5 YOE

> **Source:** Glassdoor reviews, LeetCode Discuss threads (including one specifically "EPAM Senior Backend Developer | Java | Microservices | 5+ YOE"), Medium interview experiences (Emin YUCE, Coding Odyssey, The Modern Backend), GeeksforGeeks EPAM SSE July 2024, Naukri Code360 candidates, InterviewQuery EPAM guide — compiled September 2026.

> **Round type:** 90-min technical interview (live, conducted in English with senior EPAM engineer). No auto-indent editor — code typed in shared doc. Interviewer can execute code but you cannot.

---

## 🎯 The Round in One Line

EPAM's 90-min technical round is **70% concept depth, 30% coding**. They test whether you know *why* Java and Spring work the way they do — not just that you can use them. "Pleasant technical interview, without default questions about HashMap" is a lie — **HashMap internals are asked in almost every report.**

---

## 🧭 Topic Frequency Map

Frequency scale: ⭐ = occasionally asked · ⭐⭐⭐ = regularly asked · ⭐⭐⭐⭐⭐ = asked in almost every report

| Topic | Frequency | What they actually ask |
| --- | --- | --- |
| **Java Collections / HashMap internals** | ⭐⭐⭐⭐⭐ | How HashMap works, collision handling, Java 8 tree change, ConcurrentHashMap vs synchronizedMap |
| **Java 8 Streams + Lambda** | ⭐⭐⭐⭐⭐ | map vs flatMap, intermediate vs terminal, coding group-by using streams |
| **Spring @Transactional** | ⭐⭐⭐⭐⭐ | Propagation, isolation levels, self-invocation trap, private method trap |
| **Multithreading: volatile, synchronized, atomic** | ⭐⭐⭐⭐⭐ | volatile vs synchronized, ExecutorService, happens-before |
| **Bean scope + lifecycle** | ⭐⭐⭐⭐ | Default scope, when singleton is NOT thread-safe, lifecycle callbacks |
| **SOLID principles** | ⭐⭐⭐⭐ | LSP and ISP most asked — with real code examples |
| **Design patterns** | ⭐⭐⭐⭐ | Singleton, Strategy, Factory — "real-world usage in Spring" |
| **Functional interfaces** | ⭐⭐⭐⭐ | Predicate / Function / Supplier / Consumer — signatures + usage |
| **Microservices patterns** | ⭐⭐⭐⭐ | Circuit Breaker, SAGA, API Gateway — when you use each and trade-offs |
| **Transaction isolation levels** | ⭐⭐⭐⭐ | Read committed, repeatable read, serializable + anomalies each prevents |
| **JPA internals** | ⭐⭐⭐ | N+1 problem, lazy vs eager loading, entity states |
| **SQL window functions** | ⭐⭐⭐ | GROUP BY, RANK(), coding question on the spot |
| **Spring IoC / AOP / proxy model** | ⭐⭐⭐ | How @Transactional actually works under the hood (proxy) |
| **CQRS** | ⭐⭐⭐ | When to use, trade-offs, asked in techno-managerial |
| **Kafka** | ⭐⭐⭐ | Architecture, consumer groups, offset commit, at-least-once |
| **Immutable objects** | ⭐⭐⭐ | How to create truly immutable class in Java |
| **ClassLoader** | ⭐⭐ | Parent delegation model, when you'd write a custom one |
| **CompletableFuture** | ⭐⭐⭐ | supplyAsync vs runAsync, thenApply vs thenCompose |
| **Spring Actuator** | ⭐⭐ | Endpoints you expose + monitor |
| **Caching** | ⭐⭐⭐ | Redis use cases, cache invalidation, when NOT to cache |

---

## 🔬 Topic Deep Dives (with questions verbatim from candidate reports)

---

### 🔹 1. Java Collections — HashMap Internals ⭐⭐⭐⭐⭐

**Questions verbatim from candidates:**
- "How does HashMap work internally?"
- "What happens during a hash collision?"
- "What is the load factor and why 0.75?"
- "What changed in Java 8 with respect to HashMap internal working?"
- "When would you use ConcurrentHashMap vs `Collections.synchronizedMap()`?"
- "Can ConcurrentHashMap store null keys or values?"
- "Implement a HashMap from scratch (no standard library)." ← harder variant

**The Java 8 change they always ask about:**

```
Before Java 8:
Bucket → [LinkedList of entries] ← O(n) worst case for collision chains

After Java 8:
Bucket → [LinkedList] until chain length > 8
       → [Red-Black Tree] when chain length > 8  ← O(log n) worst case
       → back to [LinkedList] when chain falls below 6
```

**The load factor question — scripted answer:**
> "0.75 is a tuned trade-off. Lower load factor (say 0.5) leaves more empty buckets — faster lookups, wastes memory. Higher (say 0.9) saves memory but lengthens collision chains and slows lookups. 0.75 is where the math shows the best average performance."

**ConcurrentHashMap vs synchronizedMap — the key points:**

| | `ConcurrentHashMap` | `Collections.synchronizedMap()` |
| --- | --- | --- |
| Locking strategy | **Java 8+:** CAS on empty bins + `synchronized(bin_head)` on collisions (NOT "16 segments" — that was Java 7) | Whole-map lock |
| Read locking | No lock needed for reads (volatile reads) | Locks the entire map |
| Null keys/values | ❌ Not allowed (NPE thrown) | ✅ Allowed |
| Iteration under modification | Safe (weakly consistent iterator) | `ConcurrentModificationException` |
| When to use | High concurrent reads + writes | Data consistency critical, simpler setup |

**KEY INVARIANT (Java 8+):**
`ConcurrentHashMap` uses CAS (Compare-And-Swap) for writes to empty bins (no lock at all) and `synchronized` on the bin head node for collisions. Reads are lock-free (volatile). The old "16 Segment / lock stripping" model was **Java 7 only** — saying it in a 2026 interview is a red flag.

---

### 🔹 2. Java 8 Streams + Lambda ⭐⭐⭐⭐⭐

**Questions verbatim from candidates:**
- "What are intermediate vs terminal operations? Give examples."
- "What is the difference between `map()` and `flatMap()`?"
- "What is `peek()` used for?"
- "Can a stream be reused after a terminal operation?"
- "What are the built-in functional interfaces in Java 8?"
- "Write: group anagrams from a string array using streams."
- "Write: count word occurrences in a string using streams."
- "Write: filter employees by city, sort by name descending — using streams."
- "Write: get first repeating character in a string using streams + lambda."

**map() vs flatMap() — the answer they want:**

```java
// map(): one-to-one. Each element becomes exactly one output element.
List<Integer> lengths = List.of("hello", "world")
    .stream()
    .map(String::length)      // "hello" → 5, "world" → 5
    .collect(Collectors.toList());
// Output: [5, 5]

// flatMap(): one-to-many. Each element becomes a Stream, then flattened.
List<String> words = List.of(List.of("hello", "world"), List.of("foo", "bar"))
    .stream()
    .flatMap(Collection::stream)   // [[h,w],[f,b]] → [h,w,f,b]
    .collect(Collectors.toList());
// Output: [hello, world, foo, bar]
```

**Intermediate vs Terminal — lazy evaluation is the key point:**

```
Intermediate (lazy — nothing runs until terminal is called):
  filter(), map(), flatMap(), sorted(), distinct(), peek(), limit(), skip()

Terminal (eager — triggers the pipeline to execute):
  collect(), forEach(), reduce(), count(), findFirst(), anyMatch(), toList()
```

> **KEY INVARIANT:** A stream is idle until a terminal operation is called. After a terminal operation, the stream is consumed — calling it again throws `IllegalStateException`.

**The four built-in functional interfaces:**

| Interface | Abstract method | What it does |
| --- | --- | --- |
| `Predicate<T>` | `boolean test(T t)` | Tests a condition (used in `filter()`) |
| `Function<T, R>` | `R apply(T t)` | Transforms T to R (used in `map()`) |
| `Supplier<T>` | `T get()` | Produces a value, no input (like a factory) |
| `Consumer<T>` | `void accept(T t)` | Consumes a value, no return (side-effect) |

**Anagram grouping — the coding question asked verbatim:**

```java
// Given: ["eat","tea","tan","ate","nat","bat"]
// Output: {[a,e,t]=[eat,tea,ate], [a,n,t]=[tan,nat], [a,b,t]=[bat]}

Map<String, List<String>> grouped = Arrays.stream(words)
    .collect(Collectors.groupingBy(word -> {
        // Sort characters to get the canonical anagram key
        char[] chars = word.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }));
```

---

### 🔹 3. Multithreading & Concurrency ⭐⭐⭐⭐⭐

**Questions verbatim from candidates:**
- "What does the `volatile` keyword guarantee?"
- "What is the Java Memory Model (JMM) and happens-before relationship?"
- "What is the difference between `volatile` and `synchronized`?"
- "What is the difference between `supplyAsync` and `runAsync`?" ← asked at EPAM verbatim
- "When would you use `AtomicInteger` vs `synchronized`?"
- "What are ways to create multithreading in Java?"
- "What is `ExecutorService` and why use it over raw `Thread`?"

**volatile vs synchronized — the precise answer:**

```
volatile:
  ✅ Guarantees VISIBILITY (reads/writes go to main memory, not CPU cache)
  ✅ Prevents instruction REORDERING around the variable
  ❌ Does NOT guarantee ATOMICITY (i++ on a volatile int is still NOT thread-safe)

synchronized:
  ✅ Guarantees VISIBILITY (flushes to main memory on exit)
  ✅ Guarantees ATOMICITY (only one thread in the block at a time)
  ❌ Performance cost — only one thread at a time
```

**supplyAsync vs runAsync — asked verbatim at EPAM:**

```java
// supplyAsync: runs a Supplier<T> — returns a CompletableFuture<T>
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> "hello");   // returns a result

// runAsync: runs a Runnable — returns CompletableFuture<Void>
CompletableFuture<Void> voidFuture = CompletableFuture
    .runAsync(() -> System.out.println("fire and forget"));  // no result
```

**happens-before — one-line explanation:**
> "The JMM's happens-before relationship guarantees that if action A happens-before action B, then A's memory writes are visible to B. It's the mechanism that makes `volatile`, `synchronized`, and thread `start()`/`join()` work correctly."

**Ways to create threads — they want all 4:**

```java
// 1. Extend Thread
class MyThread extends Thread {
    public void run() { /* ... */ }
}

// 2. Implement Runnable
Thread t = new Thread(() -> { /* ... */ });

// 3. Implement Callable (can return value + throw checked exception)
ExecutorService es = Executors.newFixedThreadPool(4);
Future<String> result = es.submit(() -> "hello");

// 4. CompletableFuture (modern, composable)
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "hello");
```

---

### 🔹 4. Spring @Transactional ⭐⭐⭐⭐⭐

**Questions verbatim from candidates:**
- "Can `@Transactional` be applied to private methods?" (asked in multiple reports)
- "What are transaction propagation types? Give an example."
- "What is the difference between isolation levels?"
- "What is the self-invocation trap with @Transactional?"
- "How does @Transactional actually work under the hood?"

**How @Transactional works — the proxy model:**

```
Your code calls: service.transfer(...)

What actually happens at runtime:
  Caller → [Spring Proxy (CGLIB/JDK)] → service.transfer()
               ↑
               Proxy intercepts the call
               Opens transaction, calls the method, commits/rolls back

The gotcha — SELF-INVOCATION:
  Method A (no @Transactional) calls Method B (@Transactional) in SAME class
  → The call bypasses the proxy — goes directly to Method B
  → @Transactional on B does NOTHING
  ← Fix: move B to a different bean, or use ApplicationContext.getBean(this.getClass())
```

**The private method trap:**
> "If you put `@Transactional` on a private method, it silently does nothing. Spring creates a CGLIB proxy that overrides public methods — private methods cannot be overridden, so the proxy never intercepts them. No error is thrown. This is a real footgun."

**Propagation types — the 3 you must know:**

| Propagation | Behavior |
| --- | --- |
| `REQUIRED` (default) | Join existing transaction; start new one if none exists |
| `REQUIRES_NEW` | Always start a new transaction; suspend the current one |
| `NESTED` | Run within a nested savepoint of the current transaction |

**Isolation levels vs anomalies:**

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read |
| --- | --- | --- | --- |
| `READ_UNCOMMITTED` | ✅ possible | ✅ possible | ✅ possible |
| `READ_COMMITTED` | ❌ prevented | ✅ possible | ✅ possible |
| `REPEATABLE_READ` | ❌ prevented | ❌ prevented | ✅ possible |
| `SERIALIZABLE` | ❌ prevented | ❌ prevented | ❌ prevented |

---

### 🔹 5. Bean Scope + Lifecycle ⭐⭐⭐⭐

**Questions verbatim from candidates:**
- "What is the default scope of a Spring bean?"
- "What scopes are available in Spring?"
- "Is a singleton bean thread-safe?"
- "What are the lifecycle callbacks for a Spring bean?"
- "What is the scope of a Spring bean in a web application?"

**Bean scopes you must know:**

| Scope | One instance per... | Thread-safe? |
| --- | --- | --- |
| `singleton` (default) | ApplicationContext | ❌ NOT inherently |
| `prototype` | Each injection/request | N/A (new object each time) |
| `request` | HTTP request | ✅ (one per request) |
| `session` | HTTP session | ✅ (one per session) |

**The gotcha they test:** Singleton ≠ thread-safe. A singleton bean with mutable state shared across threads is a bug. If your Spring bean holds state (e.g., a class-level list), it needs synchronization or should be `prototype`-scoped.

**Bean lifecycle — the sequence:**

```
1. Instantiation (constructor called)
2. Dependency injection (@Autowired, @Value filled)
3. @PostConstruct method called (setup, validation)
4. Bean is ready — placed in context, ready for use
   ...serving requests...
5. @PreDestroy method called (cleanup)
6. Bean destroyed (GC-able)
```

---

### 🔹 6. Microservices Patterns ⭐⭐⭐⭐

**Questions verbatim from candidates:**
- "Which microservice patterns do you know? Walk me through which ones you've actually used."
- "Explain the Circuit Breaker pattern and when you'd use it."
- "How do you handle distributed transactions? Have you used SAGA?"
- "What is CQRS and when does it make sense?"
- "What is the difference between Circuit Breaker and Retry?"

**Circuit Breaker — states diagram:**

```
   [CLOSED] ──failure threshold crossed──► [OPEN]
       ▲                                       │
       │                                       │ wait period
       │                                       ▼
   success                               [HALF-OPEN]
       └──── probe request succeeds ◄──────────┘
```

> "CLOSED = everything normal, requests pass through. OPEN = too many failures, requests fail fast without calling the downstream. HALF-OPEN = we let one probe request through to see if downstream recovered. If it succeeds → back to CLOSED. If it fails → back to OPEN."

**Circuit Breaker vs Retry — the question they specifically ask:**
> "Retry is for transient errors — a network blip where the next attempt will likely succeed. Circuit Breaker is for sustained outages — the downstream is down and retrying just adds load. You often combine them: retry first (2-3 times), then circuit breaker trips if retries keep failing."

**SAGA pattern — two flavors:**

```
Choreography SAGA (event-driven):
  Service A → emits "order-created" event
    Service B listens → does work → emits "payment-processed" event
      Service C listens → does work ...
  No central coordinator. Failure triggers compensating events.

Orchestration SAGA:
  Orchestrator calls A, then B, then C.
  On failure: orchestrator issues compensating calls in reverse.
```

**CQRS — one-line:**
> "Separate the model for writing (Command side, optimized for writes) from the model for reading (Query side, optimized for reads). Read and write sides can scale independently, use different stores."

---

### 🔹 7. SOLID Principles ⭐⭐⭐⭐

**Most-asked at EPAM (from multiple candidate reports): LSP and ISP**

**Questions verbatim:**
- "Explain the Liskov Substitution Principle with an example."
- "Explain the Interface Segregation Principle."
- "Where have you violated SOLID in practice and what did you do about it?"

**LSP — the canonical example:**

```java
// VIOLATION: Square IS-A Rectangle — but breaks LSP
class Rectangle {
    void setWidth(int w) { this.width = w; }
    void setHeight(int h) { this.height = h; }
}

class Square extends Rectangle {
    // Square must keep width == height
    void setWidth(int w) {
        super.setWidth(w);
        super.setHeight(w);   // ← breaks Rectangle's contract!
    }
}

// Code that worked with Rectangle now silently breaks with Square:
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(10);
// Expected area: 50. Actual area: 100. LSP violated.
```

**ISP — one-line:**
> "Don't force clients to implement methods they don't use. Break fat interfaces into smaller, role-specific ones — so a class implementing only what it needs."

---

### 🔹 8. Design Patterns ⭐⭐⭐⭐

**Questions verbatim from candidates:**
- "How would you implement a thread-safe Singleton in Java?"
- "Explain the Strategy pattern with a real-world example."
- "What design patterns does Spring use internally?"
- "When would you use Factory vs Builder?"

**Thread-safe Singleton — they want this specific implementation:**

```java
// Double-checked locking with volatile (Java 5+)
public class Singleton {
    // volatile prevents the "partially constructed object" race condition
    private static volatile Singleton INSTANCE;

    private Singleton() {}

    public static Singleton getInstance() {
        // First check: avoid locking if already initialized
        if (INSTANCE == null) {
            synchronized (Singleton.class) {
                // Second check: another thread may have initialized between checks
                if (INSTANCE == null) {
                    INSTANCE = new Singleton();
                }
            }
        }
        return INSTANCE;
    }
}
```

**Preferred alternative (Bill Pugh / Initialization-on-demand holder):**

```java
public class Singleton {
    private Singleton() {}

    // Inner class is loaded lazily by the JVM — thread-safe without synchronized
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

**Design patterns in Spring — what to say:**

| Spring Feature | Pattern Used |
| --- | --- |
| ApplicationContext | Factory + Singleton |
| @Transactional, @Async, @Cacheable | Proxy (CGLIB / JDK dynamic proxy) |
| DispatcherServlet | Front Controller |
| JdbcTemplate | Template Method |
| ApplicationEvent | Observer |
| Spring MVC HandlerMapping | Strategy |

---

### 🔹 9. JPA / Database ⭐⭐⭐

**Questions verbatim from candidates:**
- "What is the N+1 problem and how do you fix it?"
- "What is the difference between lazy and eager loading?"
- "SQL: write a query to rank employees by salary within each department." (asked in at least 2 reports)
- "What is database indexing? When does an index slow things down?"
- "Write a GROUP BY query to count occurrences."

**N+1 problem — the answer they want:**

```java
// N+1: you load 100 Orders, then JPA fires 1 query per Order to load its Items
// Total: 1 + 100 = 101 queries

// FIX 1: JOIN FETCH
@Query("SELECT o FROM Order o JOIN FETCH o.items")
List<Order> findAllWithItems();

// FIX 2: @EntityGraph
@EntityGraph(attributePaths = {"items"})
List<Order> findAll();

// FIX 3: @BatchSize(size = 50) on the collection
@OneToMany
@BatchSize(size = 50)
private List<Item> items;
```

**SQL window function — the coding question:**

```sql
-- Rank employees by salary within each department
SELECT
    name,
    department,
    salary,
    RANK() OVER (
        PARTITION BY department
        ORDER BY salary DESC
    ) AS salary_rank
FROM employees;
```

---

## 🧩 Coding Questions Asked — Full List from Reports

These are real coding problems candidates reported being asked at EPAM:

| Problem | Category | Frequency |
| --- | --- | --- |
| Group anagrams using Java 8 streams | Streams + HashMap | ⭐⭐⭐⭐⭐ |
| Count word occurrences using streams | Streams | ⭐⭐⭐⭐ |
| Filter + sort employees by city using streams | Streams | ⭐⭐⭐⭐ |
| First repeating character using streams | Streams + Set | ⭐⭐⭐ |
| Longest common prefix from string array | String | ⭐⭐⭐ |
| Product of array except itself (no division) | Array | ⭐⭐⭐ |
| Sliding window (max subarray sum, etc.) | Array | ⭐⭐⭐ |
| Array range grouping (0–9, 10–19 buckets) | Streams + GroupBy | ⭐⭐⭐ |
| Palindrome check (small coding task) | String | ⭐⭐ |
| Big Integer multiplication (strings as input) | Math + String | ⭐⭐ |
| SQL: Rank employees by salary per department | SQL | ⭐⭐⭐ |
| Write a POST REST endpoint with validation | Spring | ⭐⭐⭐ |

**Group anagrams — the most-reported coding question:**

```java
public Map<String, List<String>> groupAnagrams(String[] words) {
    return Arrays.stream(words)
        .collect(Collectors.groupingBy(word -> {
            char[] chars = word.toCharArray();
            Arrays.sort(chars);
            return new String(chars);
        }));
}
```

**Product of array except itself:**

```java
public int[] productExceptSelf(int[] nums) {
    int n = nums.length;
    int[] result = new int[n];

    // Step 1: left prefix products
    result[0] = 1;
    for (int i = 1; i < n; i++) {
        result[i] = result[i - 1] * nums[i - 1];
    }

    // Step 2: multiply right suffix products in-place
    int rightProduct = 1;
    for (int i = n - 1; i >= 0; i--) {
        result[i] *= rightProduct;
        rightProduct *= nums[i];
    }

    return result;
}
```

---

## 🎨 Visual — EPAM 90-min Technical Round Flow

```
  ┌─────────────────────────────────────────────────────────────────────┐
  │                  EPAM 90-min Technical Round                        │
  │                                                                     │
  │  [0–15 min]  Intro + project walk-through (your current project)   │
  │                                                                     │
  │  [15–45 min] Concept depth (the bulk of the round):                │
  │    · Core Java: HashMap, ConcurrentHashMap, volatile, JMM          │
  │    · Java 8: Streams, lambdas, functional interfaces               │
  │    · Spring: @Transactional traps, bean scope, proxy model         │
  │    · Multithreading: ExecutorService, CF, synchronized             │
  │                                                                     │
  │  [45–70 min] Coding (live, in shared doc, no IDE):                 │
  │    · Stream-based coding problem (anagram grouping most common)     │
  │    · Sometimes a small SQL question                                 │
  │    · Sometimes a design pattern or API endpoint                     │
  │                                                                     │
  │  [70–85 min] Microservices / System Design (senior candidates):     │
  │    · Circuit breaker, SAGA, CQRS                                    │
  │    · Kafka architecture                                             │
  │    · Draw your current project's microservice architecture         │
  │                                                                     │
  │  [85–90 min] Your questions                                         │
  └─────────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   70% concept depth + 30% coding.
   The round is won or lost on Java internals + Spring traps — not DSA.
```

---

## ⚠️ Gotchas — Things Candidates Said They Got Caught On

1. **@Transactional on private methods** — silently does nothing; no error thrown. They specifically test this.
2. **Singleton bean is NOT thread-safe** — Spring singleton = one instance per context, not thread-safe.
3. **Self-invocation bypasses @Transactional** — calling an @Transactional method from within the same bean skips the proxy.
4. **volatile does NOT guarantee atomicity** — `i++` on a volatile int is still a race condition.
5. **ConcurrentHashMap does not allow null** — `HashMap` allows null keys/values; `ConcurrentHashMap` throws `NullPointerException`.
6. **Stream is consumed after terminal operation** — reusing a stream throws `IllegalStateException`.
7. **Java 8 HashMap change: LinkedList → Tree** — the specific threshold is chain-length > 8 (converts to tree), falls back below 6.

---

## 🗺️ Study Priority for Your 90-min Round

Given what candidates report:

**Drill first (⭐⭐⭐⭐⭐ frequency):**
1. HashMap internals (all of it — hashing, collision, Java 8 tree, load factor 0.75)
2. ConcurrentHashMap vs synchronizedMap — segment locking, null rules, performance
3. Java 8 Streams — map/flatMap, intermediate/terminal, lazy evaluation
4. @Transactional — self-invocation trap, private method trap, propagation, isolation
5. volatile vs synchronized vs atomic — exact guarantees of each

**Drill second (⭐⭐⭐⭐ frequency):**
6. Bean scopes + lifecycle (@PostConstruct, @PreDestroy)
7. SOLID — LSP and ISP with real code examples
8. Singleton pattern (double-checked locking + Bill Pugh)
9. Circuit Breaker + SAGA — when to use each, trade-offs
10. N+1 problem + fix (JOIN FETCH or @EntityGraph)

**Code drills (do at least 2-3 before the round):**
- Group anagrams using Java 8 streams (most commonly reported)
- Filter + sort collection using streams
- SQL: RANK() OVER (PARTITION BY ...)

---

---

## 🧩 DSA — Do They Ask It? Yes. Here Is What (2024–2025 Only)

> **Verdict:** EPAM asks DSA, but it is **not FAANG-style hard DSA**. For 5 YOE senior roles, expect **1–2 Medium LeetCode problems** in the 90-min round, often framed as "solve this using Java 8 Streams." They care more about *clean code + correct approach* than competitive-programming speed.

> **Pattern:** ~76% of tagged EPAM problems are Medium, ~12% Easy, ~12% Hard. But Hard problems are extremely rare in actual reported interviews.

---

### 📊 DSA Frequency by Category (2024–2025 reports only)

| Category | Frequency | What they actually ask |
| --- | --- | --- |
| **Streams-as-DSA** (Java 8 coding) | ⭐⭐⭐⭐⭐ | Group anagrams, word frequency, filter+sort — all via streams |
| **Sliding Window** | ⭐⭐⭐⭐ | Longest substring without repeating chars, max in window of size K |
| **HashMap-based problems** | ⭐⭐⭐⭐ | Anagram grouping, first repeating char, word frequency count |
| **Array manipulation** | ⭐⭐⭐ | Product except self, move zeroes, sort multiples of 5 only |
| **Binary Search** | ⭐⭐⭐ | Search in rotated sorted array |
| **Linked List** | ⭐⭐⭐ | Rotate linked list by K spots |
| **String** | ⭐⭐⭐ | Longest common prefix, longest palindromic substring, Big Integer multiply |
| **Stack** | ⭐⭐ | Valid parentheses |
| **Dynamic Programming** | ⭐⭐ | Partition equal subset sum — reported once (July 2024) |
| **Custom data structure** | ⭐⭐ | TTL-based cache with 2 HashMaps + thread-safety |
| **Graph / BFS** | ⭐ | Number of islands — in older reports; not seen 2024–2025 |

---

### 🔬 Exact Problems Reported — 2024–2025 Only (verbatim from candidates)

#### July 2024 — SSE, 4 YOE, GeeksforGeeks report

- **Partition Equal Subset Sum** — can you divide an array into two subsets with equal sums? (DP, Medium)
- **Search in Rotated Sorted Array** — binary search with rotation handling (Medium)
- 4–5 Streams problems: word frequency, filter+sort

#### July 2024 — Software Engineer A2, LeetCode Discuss (Offer)

- **Word frequency count** — print frequency of each word in a string, using streams only
- **Rotate Linked List by K spots** (Medium)
- **Group Anagrams** (Medium)

**Round 2 of same candidate:**
- **Big Integer Multiplication** — given two numbers as strings, multiply and return as string. Edge case: `"-0"` × anything = `"0"` (Medium-Hard)

#### August 2024 — Senior Backend Developer, 5+ YOE, LeetCode Discuss

- No pure DSA — this round was Java features + architecture deep dive
- Asked to draw current project microservice architecture on draw.io live

#### September 2024 — Sr. SDE, Offer Accepted, LeetCode Discuss

- **Weighted Voting System** — N candidates (IDs 0 to n-1), each voter casts 3 votes. First vote = 3× weight, second = 2×, third = 1×. Tally and rank candidates.

#### December 27, 2024 — SSE, 5 YOE, Bangalore, LeetCode Discuss

- **4 coding questions on Java 8 Streams** (no other DSA)
- **TTL-based cache using 2 HashMaps** — multi-thread accessible, delete entry if timestamp > TTL ← **real system-design coding question**

#### February 2025 — SSE, Bangalore, LeetCode Discuss

- **Longest Substring Without Repeating Characters** — `"abcadcbb"` → 4 (Sliding Window, Medium)
- Stream-based filter: employee IDs divisible by 2, sort ascending then descending

---

### 🎨 Visual — DSA Difficulty vs What EPAM Actually Asks

```
LeetCode Difficulty:
  Easy ──────────── Medium ──────────── Hard
    │                  │                  │
    │  Valid Parens     │  Group Anagrams  │  (Big Int Multiply — rare)
    │  Move Zeroes      │  Sliding Window  │
    │  Longest Prefix   │  Rotated BSarch  │
    │  Two Sum          │  LL Rotate by K  │
    │                   │  Partition Sum   │
    │                   │  TTL Cache       │
    ↓                   ↓                  ↓
  Quick warm-up    Most of the round    Almost never

KEY INVARIANT:
   EPAM is NOT a DSA company. For 5 YOE Java backend:
   1 Medium Streams problem + 1 Medium algorithm problem = typical round.
   Solve them cleanly and explain your approach — speed is secondary.
```

---

### 🔹 LeetCode Company-Tagged Problems — EPAM (as of June 2025)

Source: [liquidslr/leetcode-company-wise-problems](https://github.com/liquidslr/leetcode-company-wise-problems) updated June 2025.

| Problem | Difficulty | Pattern | Priority |
| --- | --- | --- | --- |
| Longest Substring Without Repeating Characters | Medium | Sliding Window | ⭐ |
| Group Anagrams | Medium | HashMap | ⭐ |
| Two Sum | Easy | HashMap | ⭐ |
| Search in Rotated Sorted Array | Medium | Binary Search | ⭐ |
| Valid Parentheses | Easy | Stack | ⭐ |
| Longest Common Prefix | Easy | String | ⭐ |
| Move Zeroes | Easy | Two Pointers | ⭐ |
| Longest Palindromic Substring | Medium | DP / Two Pointers | — |
| Next Permutation | Medium | Array | — |
| Toeplitz Matrix | Easy | Matrix | — |
| Rotate List | Medium | Linked List | ⭐ |
| Partition Equal Subset Sum | Medium | DP | — |

---

### 🔹 TTL Cache — The Most Interesting Coding Q (Dec 2024)

This was the hardest real question reported in 2024-2025. Full problem:

> "Implement a cache using 2 HashMaps that is accessible by multiple threads. Delete any entry whose timestamp has crossed a TTL value."

**Approach:**

```java
import java.util.concurrent.ConcurrentHashMap;

public class TtlCache<K, V> {
    // Map 1: key → value
    private final ConcurrentHashMap<K, V> store = new ConcurrentHashMap<>();

    // Map 2: key → insertion timestamp (ms)
    private final ConcurrentHashMap<K, Long> timestamps = new ConcurrentHashMap<>();

    private final long ttlMillis;

    public TtlCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public void put(K key, V value) {
        store.put(key, value);
        timestamps.put(key, System.currentTimeMillis());
    }

    public V get(K key) {
        // Step 1: check if key exists
        if (!store.containsKey(key)) {
            return null;
        }

        // Step 2: check TTL expiry
        long insertedAt = timestamps.getOrDefault(key, 0L);
        if (System.currentTimeMillis() - insertedAt > ttlMillis) {
            // Expired — evict lazily
            store.remove(key);
            timestamps.remove(key);
            return null;
        }

        return store.get(key);
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        timestamps.forEach((key, insertedAt) -> {
            if (now - insertedAt > ttlMillis) {
                store.remove(key);
                timestamps.remove(key);
            }
        });
    }
}
```

> **What the interviewer is testing:** Thread-safety (why `ConcurrentHashMap`, not `HashMap`), lazy vs eager eviction trade-offs, `System.currentTimeMillis()` race window awareness.

---

### 🔹 Big Integer Multiplication — The Tricky String Q (July 2024)

```java
public String multiply(String num1, String num2) {
    int m = num1.length();
    int n = num2.length();
    int[] pos = new int[m + n];

    // Step 1: multiply digit by digit, store in pos array
    for (int i = m - 1; i >= 0; i--) {
        for (int j = n - 1; j >= 0; j--) {
            int mul = (num1.charAt(i) - '0') * (num2.charAt(j) - '0');
            int p1 = i + j;
            int p2 = i + j + 1;
            int sum = mul + pos[p2];

            pos[p2] = sum % 10;
            pos[p1] += sum / 10;
        }
    }

    // Step 2: build result string, skip leading zeros
    StringBuilder sb = new StringBuilder();
    for (int digit : pos) {
        if (!(sb.length() == 0 && digit == 0)) {
            sb.append(digit);
        }
    }

    // Step 3: handle edge case — empty result means product is 0
    return sb.length() == 0 ? "0" : sb.toString();
}
```

> **The "-0" edge case they specifically mentioned:** If either input is "0" (or "-0" in the variant), the result must be "0", not "-0". The above solution handles this because all digits will be 0 and we return "0".

---

### 🗺️ DSA Prep Priority for Your 90-min Round

**Drill in this order (most likely to appear first):**

1. **Group Anagrams via Streams** — most reported. Know it cold.
2. **Longest Substring Without Repeating Characters** — sliding window with HashMap, appeared Feb 2025.
3. **Word frequency count via Streams** — appeared July 2024. `Collectors.groupingBy(Function.identity(), Collectors.counting())`.
4. **Search in Rotated Sorted Array** — binary search variant, appeared July 2024.
5. **Rotate Linked List by K** — standard LL problem, appeared July 2024.
6. **TTL Cache (2 HashMaps + thread-safety)** — appeared Dec 2024; tests concurrent thinking.
7. **Two Sum** — company-tagged; trivial but make sure you know HashMap approach.
8. **Valid Parentheses** — company-tagged; stack approach, 5-minute problem.

**Skip or deprioritize:**
- Hard DP (rare, almost never in 90-min round for Java backend 5 YOE)
- Graph BFS/DFS (in older reports pre-2024; not seen in 2024-2025 Java backend rounds)
- Bit manipulation (never reported)

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Sep 2026 | Research file created from Glassdoor, LeetCode Discuss, Medium, GeeksforGeeks, InterviewQuery sources. EPAM 90-min Java Backend technical round. |
| Sep 2026 | DSA section added — 2024–2025 only. Exact problems from LeetCode Discuss threads (July 2024, Aug 2024, Sep 2024, Dec 2024, Feb 2025). Company-tagged LeetCode list (June 2025 snapshot). TTL cache + Big Integer solutions included. |
