# EPAM Systems — 5-Day Realistic Study Plan
### Java Backend · 5 YOE · 4 hours/day · Interview: Wednesday Sep 23, 2026

> **Your baseline:** Arrays, HashMap, Sets, basic Trees, basic Graphs — Easy problems solved.
> **What EPAM actually tests:** Java internals + Streams coding + Spring traps. NOT hard DSA.
> **Total study:** 5 days × 4 hours = 20 hours. This is enough if you follow the priority order.

---

## 🎯 The Priority Logic

EPAM's 90-min technical round breaks down like this (from research):

```
  60% — Java Core + Spring internals (where rounds are LOST — traps no one warned you about)
  20% — Streams-based coding (your "DSA" round at EPAM — mostly Medium Streams problems)
  20% — Microservices patterns (Circuit Breaker, SAGA, CQRS — you already know Kafka cold)
```

**Your leverage points:**
- ✅ Kafka: already deep (from MCSE). Minimal prep needed.
- ✅ Microservices: you have MCSE as a real project. Just nail the pattern names.
- ✅ Spring: you use it daily. The gaps are the TRAPS — not the basics.
- ⚠️ HashMap internals: you know how to use it. You need to explain HOW it works internally.
- ⚠️ @Transactional: you use it. The traps will catch you if you haven't studied them.
- ⚠️ Streams coding: you know streams. You need to CODE on a whiteboard — no IDE.

---

## 🗺️ 5-Day Plan at a Glance

| Day | Date | Theme | Hours | Key outcome |
| --- | --- | --- | --- | --- |
| **1** | Fri Sep 18 | Java Collections + Streams | 4h | HashMap internals cold + can code Group Anagrams without IDE |
| **2** | Sat Sep 19 | Spring Traps | 4h | @Transactional self-invocation + isolation + bean scope cold |
| **3** | Sun Sep 20 | Design Patterns + SOLID + Microservices | 4h | Singleton (Bill Pugh) + Circuit Breaker states + SAGA types |
| **4** | Mon Sep 21 | DSA Medium + JVM Internals | 4h | Sliding window + rotated binary search + GC types |
| **5** | Tue Sep 22 | Mock + Revision | 4h | All 5 core topics out loud — no notes |
| **6** | Wed Sep 23 | Interview Day | 45 min | Morning-of set only |

---

## 📅 Day 1 — Friday Sep 18 · Java Collections + Streams

> **Theme:** The single highest-frequency topic cluster. Asked in almost every EPAM report. Fix this first.

### Hour 1 — HashMap Internals (read + understand)

Open: `epam-java-technical-research.md` → Section "1. Java Collections / HashMap Internals"

**Master these 6 concepts before moving on:**

1. **What hashing is** — `key.hashCode()` → bucket index → store `(key, value)` pair in that bucket
2. **What collision is** — two keys land in the same bucket → stored as a chain
3. **Java 7: LinkedList chain** → Java 8 change: **chain > 8 nodes → converts to Red-Black Tree** (O(n) → O(log n))
4. **Load factor 0.75** — when 75% of buckets are filled, HashMap doubles and rehashes
5. **Why ConcurrentHashMap uses segment locking** (not full-map lock) → lock stripping
6. **ConcurrentHashMap does NOT allow null keys or values** (HashMap does)

**Say out loud at end of hour:** Explain the Java 8 HashMap change in 30 seconds without notes.

---

### Hour 2 — Java 8 Streams Theory

Open: `epam-java-technical-research.md` → Section "2. Java 8 Streams + Lambda"

**Master these 4 concepts:**

1. **Intermediate vs Terminal** — nothing runs until a terminal is called (lazy pipeline). Intermediate: `filter, map, flatMap, sorted, peek`. Terminal: `collect, forEach, reduce, count, findFirst`.

2. **map() vs flatMap()** — map is 1-to-1. flatMap is 1-to-many (flattens nested streams).

3. **4 functional interfaces** — Predicate (`test()`, used in filter), Function (`apply()`, used in map), Supplier (`get()`, no input), Consumer (`accept()`, no output, side-effect).

4. **Stream is consumed once** — calling a terminal operation again throws `IllegalStateException`.

---

### Hour 3 — Streams Coding Drills (NO IDE — open notepad only)

These are the exact problems EPAM asked in 2024-2025. Code each one from memory:

**Problem 1 — Group Anagrams** (most-reported EPAM coding question)
```
Input: ["eat","tea","tan","ate","nat","bat"]
Output: {sorted-key → list of anagrams}
```
Approach: `stream → groupingBy(word → sorted chars of word)`

**Problem 2 — Word Frequency Count**
```
Input: "the cat sat on the mat the cat"
Output: {the=3, cat=2, sat=1, ...}
```
Approach: `Arrays.stream(sentence.split(" ")) → groupingBy(identity, counting())`

**Problem 3 — Filter + Sort Employees**
```
Get all employees from city "Noida", sorted by name descending
```
Approach: `stream → filter(city.equals("Noida")) → sorted(Comparator.comparing(name).reversed()) → collect`

**Do not look at the solutions.** Write, then check. Repeat the ones you got wrong.

---

### Hour 4 — volatile vs synchronized vs Atomic + CompletableFuture

**volatile — what it guarantees:**
- ✅ Visibility: reads/writes go to main memory, not CPU cache
- ✅ Prevents instruction reordering around that variable
- ❌ Does NOT guarantee atomicity — `i++` on volatile is still a race condition

**synchronized — what it adds on top:**
- ✅ Atomicity (only one thread executes the block at a time)
- ✅ Visibility (flushes memory on exit)
- ❌ Performance cost

**When to use AtomicInteger:** When you need atomic increment/decrement without the full cost of `synchronized`.

**CompletableFuture API** (asked verbatim at EPAM: "difference between supplyAsync and runAsync"):
- `supplyAsync(Supplier<T>)` → returns `CompletableFuture<T>` (has a result)
- `runAsync(Runnable)` → returns `CompletableFuture<Void>` (fire-and-forget)

**JMM happens-before (one line):** Action A happens-before action B means A's writes are guaranteed visible to B.

---

## 📅 Day 2 — Saturday Sep 19 · Spring Traps

> **Theme:** You use Spring daily — but the TRAPS are not visible in normal usage. These are exactly what EPAM asks because they reveal whether you understand the proxy model or just use the annotations.

### Hour 1 — @Transactional: The Three Traps

Open: `epam-java-technical-research.md` → Section "4. Spring @Transactional"

**Trap 1 — Self-invocation (most asked):**

```java
// Method A (no @Transactional) calls Method B (@Transactional) in the SAME class
// @Transactional on B is IGNORED — the call bypasses the proxy
// Fix: move B to a different Spring bean
```

**Trap 2 — Private method:**

```java
// @Transactional on a private method silently does nothing
// Spring proxy can only override PUBLIC methods
// No error thrown — silent failure
```

**Trap 3 — Checked exceptions don't roll back by default:**
```java
// @Transactional rolls back on RuntimeException + Error by default
// Checked exceptions (IOException, etc.) do NOT roll back unless you add:
// @Transactional(rollbackFor = IOException.class)
```

**Say out loud:** "If you call a @Transactional method from within the same class, what happens?" Answer without notes.

---

### Hour 2 — Transaction Propagation + Isolation Levels

**3 propagation types you must know:**

| Propagation | Behavior |
| --- | --- |
| `REQUIRED` (default) | Join existing tx; start new one if none |
| `REQUIRES_NEW` | Always start fresh tx; suspend current one |
| `NESTED` | Run inside a savepoint of current tx |

**Isolation levels — memorize the anomaly each prevents:**

| Level | Prevents |
| --- | --- |
| `READ_UNCOMMITTED` | Nothing — can read dirty, non-repeatable, phantom |
| `READ_COMMITTED` | Dirty reads |
| `REPEATABLE_READ` | Dirty + non-repeatable reads |
| `SERIALIZABLE` | All three — but very slow |

**Quick definitions to keep in head:**
- **Dirty read** — reading a row another transaction hasn't committed yet
- **Non-repeatable read** — reading the same row twice in one tx, getting different values (another tx updated+committed in between)
- **Phantom read** — same query returns different number of rows (another tx inserted+committed in between)

---

### Hour 3 — Bean Scope + Lifecycle

**Default scope:** Singleton — one instance per ApplicationContext.

**The gotcha:** Singleton ≠ thread-safe. Spring creates one instance and all threads share it. If your bean has mutable state (e.g., a class-level list or counter), it WILL have race conditions.

**When to use Prototype:** When the bean must have independent state per injection (e.g., a stateful processor).

**Lifecycle sequence** — say this in order:
```
1. Constructor called
2. Dependencies injected (@Autowired, @Value)
3. @PostConstruct runs (your setup / validation code)
4. Bean is live in context
   ... serving requests ...
5. @PreDestroy runs (your cleanup code)
6. Bean destroyed
```

---

### Hour 4 — Spring Proxy Model + AOP

**How @Transactional (and @Async, @Cacheable) actually works:**

```
You write:      MyService.doWork()
At runtime:     Caller → [CGLIB Proxy of MyService] → MyService.doWork()
                              ↑
                              Proxy opens TX → calls your method → commits/rolls back
```

CGLIB proxy (class-based) overrides your public methods. JDK dynamic proxy works only if your class implements an interface.

**This is why self-invocation breaks it:** When doWork() calls anotherMethod() in the same class, the call goes directly to `this.anotherMethod()` — not through the proxy. The proxy never sees the call.

**AOP terms they may ask:**
- **Aspect** — the cross-cutting concern (logging, transactions, security)
- **Join point** — where the aspect can hook (method execution)
- **Advice** — what the aspect does (Before, After, Around)
- **Pointcut** — the expression that selects which join points

---

## 📅 Day 3 — Sunday Sep 20 · Design Patterns + SOLID + Microservices

> **Theme:** You know microservices from MCSE. The goal today is precise terminology for patterns (Circuit Breaker states, SAGA two flavors) + closing the SOLID/Singleton gap.

### Hour 1 — Singleton + Design Patterns in Spring

**Thread-safe Singleton — Bill Pugh (preferred answer at EPAM):**

```java
public class Singleton {
    private Singleton() {}

    // Inner class loaded lazily by JVM — thread-safe without synchronized
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

Why this works: the JVM guarantees class initialization is thread-safe. `Holder` is not loaded until `getInstance()` is first called — so it's lazy AND thread-safe.

**Design patterns in Spring — what they link to:**

| Pattern | Where in Spring |
| --- | --- |
| Factory | ApplicationContext creates beans |
| Singleton | Default bean scope |
| Proxy | @Transactional, @Async, @Cacheable |
| Template Method | JdbcTemplate, RestTemplate |
| Observer | ApplicationEvent / ApplicationListener |
| Front Controller | DispatcherServlet |
| Strategy | HandlerMapping (picks which controller handles the request) |

---

### Hour 2 — SOLID: LSP and ISP with Code

**Liskov Substitution Principle (LSP)** — asked by name at EPAM:

> "A subclass must be substitutable for its parent without breaking the behavior callers expect."

The classic VIOLATION:

```java
// Rectangle: setWidth and setHeight are independent
class Rectangle {
    protected int width;
    protected int height;

    void setWidth(int w) { this.width = w; }
    void setHeight(int h) { this.height = h; }
    int area() { return width * height; }
}

// Square extends Rectangle but BREAKS the contract:
// Setting width forces height to match (a square must have equal sides)
class Square extends Rectangle {
    @Override
    void setWidth(int w) {
        this.width = w;
        this.height = w;   // ← breaks Rectangle's contract
    }

    @Override
    void setHeight(int h) {
        this.width = h;
        this.height = h;   // ← breaks Rectangle's contract
    }
}

// Code that worked with Rectangle now breaks silently:
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(10);
int area = r.area();   // expected 50, got 100 ← LSP violated
```

**Interface Segregation Principle (ISP) — one line:**
> "Clients should not be forced to implement methods they don't use. Split fat interfaces into smaller focused ones."

---

### Hour 3 — Circuit Breaker + SAGA + CQRS

**Circuit Breaker — 3 states (draw this on paper):**

```
[CLOSED] ──failure threshold crossed──► [OPEN]
    ▲                                       │
    │                                 wait period
    │                                       ▼
 success                             [HALF-OPEN]
    └──── probe request succeeds ◄──────────┘
               (if fails → back to OPEN)
```

- CLOSED = normal, all requests pass through
- OPEN = too many failures, requests fail immediately without calling downstream
- HALF-OPEN = one probe request allowed through to test if downstream recovered

**Circuit Breaker vs Retry (specific question at EPAM):**
> "Retry = transient error (network blip, likely to succeed next attempt). Circuit Breaker = sustained outage (downstream is down, retrying adds load and makes it worse). Combine them: retry 2-3 times, THEN circuit breaker trips."

**SAGA — two flavors:**
- **Choreography**: each service emits events, next service listens. No central coordinator. Failure = compensating event.
- **Orchestration**: a central orchestrator calls each service in sequence. On failure, orchestrator issues compensating calls in reverse order.

**CQRS — one line:**
> "Separate the write model (Command, optimized for writes) from the read model (Query, optimized for reads). Read and write sides can use different stores and scale independently."

---

### Hour 4 — API Gateway + N+1 Problem + SQL

**API Gateway** — what it does:
- Single entry point for all clients
- Handles auth, rate limiting, routing, SSL termination
- Prevents clients from knowing internal service topology

**N+1 Problem (JPA):**

```java
// N+1: load 100 orders → JPA fires 1 query per order for its items
// Total: 1 + 100 = 101 queries

// Fix 1: JOIN FETCH
@Query("SELECT o FROM Order o JOIN FETCH o.items")
List<Order> findAllWithItems();

// Fix 2: @EntityGraph
@EntityGraph(attributePaths = {"items"})
List<Order> findAll();
```

**SQL — RANK() OVER (asked on the spot at EPAM):**

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

## 📅 Day 4 — Monday Sep 21 · DSA Medium + JVM Internals

> **Theme:** EPAM asks 1–2 Medium DSA problems. You have the foundation. Today: close the Medium gap + JVM internals (asked in multiple 2024 reports).

### Hour 1 — Sliding Window Problems (LeetCode practice)

**Problem 1: Longest Substring Without Repeating Characters** (LeetCode 3 — asked Feb 2025 at EPAM)

```
Input: "abcadcbb"
Output: 4   (longest = "abca" without repeating... wait, "abca" has 'a' twice)
Actually: longest = "abcd" or "bcad" = 4 chars
```

**Approach:**

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> map = new HashMap<>();
    int maxLen = 0;
    int left = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);

        // If char was seen and is inside current window → shrink window
        if (map.containsKey(c) && map.get(c) >= left) {
            left = map.get(c) + 1;
        }

        map.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }

    return maxLen;
}
```

**Problem 2: Max in Sliding Window of size K** (reported at EPAM Pune May 2025)

Use a Deque (monotonic decreasing). Solve this on LeetCode 239.

---

### Hour 2 — Binary Search + Linked List

**Search in Rotated Sorted Array** (LeetCode 33 — asked July 2024 EPAM):

```java
public int search(int[] nums, int target) {
    int left = 0;
    int right = nums.length - 1;

    while (left <= right) {
        int mid = left + (right - left) / 2;

        if (nums[mid] == target) {
            return mid;
        }

        // Determine which half is sorted
        if (nums[left] <= nums[mid]) {
            // Left half is sorted
            if (target >= nums[left] && target < nums[mid]) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        } else {
            // Right half is sorted
            if (target > nums[mid] && target <= nums[right]) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
    }

    return -1;
}
```

**Rotate Linked List by K** (LeetCode 61 — asked July 2024 EPAM):
- Find length, connect tail to head (make circular), find new tail at position `len - k % len`, cut.
- Solve this on LeetCode 61.

---

### Hour 3 — JVM Internals (asked Dec 2024 report + July 2024)

**4 Garbage Collector types — know each in one line:**

| GC | One-line description | When to use |
| --- | --- | --- |
| **Serial GC** | Single-threaded GC, stops the world | Small apps, single-core |
| **Parallel GC** | Multi-threaded, throughput-optimized, still stops world | Batch jobs, max throughput needed |
| **CMS (Concurrent Mark Sweep)** | Concurrent marking, minimizes pauses (deprecated Java 9+) | Low latency, old generation |
| **G1 GC** | Divides heap into regions, predictable pause times, default since Java 9 | General purpose, most modern apps |

**Key memory changes by Java version:**

| Version | Key change |
| --- | --- |
| Java 8 | Removed PermGen → replaced with **Metaspace** (native memory, no fixed size) |
| Java 9 | G1GC became default GC. Modular system (JPMS). |
| Java 11 | Local variable type inference with `var` in lambdas. HTTP Client API. ZGC preview. |
| Java 17 | Sealed classes. Pattern matching for instanceof. |
| Java 21 | Virtual threads (Project Loom). Sequenced collections. |

**Immutable class — how to create (asked at EPAM):**

```java
public final class ImmutablePoint {   // final class — cannot be subclassed
    private final int x;              // final fields — cannot be reassigned
    private final int y;

    public ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Getters only — no setters
    public int getX() { return x; }
    public int getY() { return y; }

    // If a field is a mutable object (like List), return a defensive copy:
    // return Collections.unmodifiableList(this.items);
}
```

---

### Hour 4 — TTL Cache + CompletableFuture chaining

**TTL Cache (asked Dec 2024 — the hardest real EPAM coding Q):**
- Two `ConcurrentHashMap`s: one for values, one for timestamps
- On `get()`: check if `now - insertedAt > ttl` → evict lazily and return null
- Full solution is in `epam-java-technical-research.md` → "TTL Cache" section

**CompletableFuture chaining:**

```java
// thenApply: transform the result (like Stream.map — synchronous)
CompletableFuture<String> upper = CompletableFuture
    .supplyAsync(() -> "hello")
    .thenApply(String::toUpperCase);   // "HELLO"

// thenCompose: chain another CompletableFuture (like Stream.flatMap — async)
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> "user-id-123")
    .thenCompose(id -> fetchUserAsync(id));   // avoids nested CompletableFuture<CompletableFuture<>>

// exceptionally: handle failure gracefully
CompletableFuture<String> safe = CompletableFuture
    .supplyAsync(() -> riskyCall())
    .exceptionally(ex -> "fallback-value");
```

---

## 📅 Day 5 — Tuesday Sep 22 · Mock + Revision (Night Before)

> **Theme:** No new topics today. Everything new you introduce today will feel uncertain in the interview. Consolidate what you have.

### Hour 1 — Say It Out Loud (no notes, no IDE)

Do this verbally, out loud, as if the interviewer is asking:

1. "How does HashMap work internally?" — walk through hashing, bucket, collision, Java 8 tree change, load factor 0.75
2. "What is the difference between ConcurrentHashMap and Collections.synchronizedMap?" — segment locking, null rules, performance
3. "Can I put @Transactional on a private method?" — say exactly what happens and why
4. "What happens if method A calls method B (which has @Transactional) in the same class?" — proxy bypass explanation
5. "What does volatile guarantee?" — visibility yes, atomicity no, example of i++ race condition

**If you stumble on any of these → go back to the research file and re-read that section only.**

---

### Hour 2 — Code Without IDE (open notepad only)

Write these out fully, no looking up:

1. **Group Anagrams via streams** — you must be able to write this cold
2. **Longest Substring Without Repeating Characters** — sliding window with HashMap
3. **TTL Cache skeleton** — 2 ConcurrentHashMaps + get() with expiry check

Time yourself: aim for 15 minutes per problem. If you're taking longer, the approach isn't internalized yet.

---

### Hour 3 — Project + Patterns on Paper

**Your project pitch (for "explain your current project architecture"):**
- MCSE is a promise-and-sourcing engine. 700K rpm. Write side (Kafka ingestion) + Read side (scatter/gather pipeline). Three cache layers (Hollow/Caffeine/Distributed). Sub-100ms p95.
- Say this in 90 seconds. Then wait for follow-up.

**On paper, draw:**
1. Circuit Breaker: three boxes (CLOSED → OPEN → HALF-OPEN) with arrows and conditions labeled
2. SAGA Choreography: three services, arrows showing events
3. SAGA Orchestration: one orchestrator box calling three services

---

### Hour 4 — Gotchas + Early Rest

Skim **only** the ⚠️ Gotchas section of `epam-java-technical-research.md`. Read each one once and say "yes, I know this" or revisit if unsure.

**Then stop.** Do not start a new topic. Do not practice more coding. Sleep early.

The one thing that kills performance more than knowledge gaps is poor sleep.

---

## 📅 Day 6 — Wednesday Sep 23 · Interview Day

> **45 minutes max. No new topics.**

### Morning Review Set (15 min read, 30 min say out loud)

**Read once:**
- @Transactional self-invocation trap (1 min)
- HashMap Java 8 LinkedList→Tree change (1 min)
- volatile vs synchronized — the atomicity point (1 min)
- Circuit Breaker 3 states (1 min)

**Say out loud once each:**
- "How does HashMap work internally" — full 60-second answer
- "Give me an example of a Spring @Transactional trap" — the self-invocation one
- Group Anagrams: write the one-liner solution mentally
- "Tell me about your current project" — your MCSE 90-second pitch

**Then stop. Trust the work.**

---

## 🚫 What NOT to Do

- ❌ Don't start studying new topics on Day 5 or Day 6 morning
- ❌ Don't try to cover GC, ClassLoader, Reflection all in one day — pick from the plan
- ❌ Don't read silently and think you've learned it — say it out loud
- ❌ Don't practice DSA Hard problems — EPAM hasn't asked them for 5 YOE Java backend in 2024–2025
- ❌ Don't spend time on Kafka deep-dive — you already know it cold from MCSE

---

## ⭐ The 5 Things That Win the Round

From the research, these are the single highest-leverage things to have cold:

1. **HashMap Java 8 internal change** — almost every report mentions it. One paragraph answer.
2. **@Transactional self-invocation trap** — asked in multiple 2024-2025 reports. 3-line answer.
3. **Group Anagrams via Java 8 streams** — most reported coding question. Code it from memory.
4. **Circuit Breaker 3 states** — say CLOSED/OPEN/HALF-OPEN + conditions out loud once.
5. **Singleton ≠ thread-safe** — one-liner that shows you understand Spring deeply.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Sep 2026 | Study plan created. 5-day, 4h/day, interview Wednesday Sep 23, 2026. Based on EPAM 2024–2025 research findings. |
