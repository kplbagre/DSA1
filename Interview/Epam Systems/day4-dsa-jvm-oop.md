# Day 4 — DSA Medium · JVM Internals · CompletableFuture · OOP Pillars
### EPAM Interview Prep · 4 Hours + OOP Bonus · Sep 21, 2026

> **Who this is for:** A developer who has solved Easy DSA and knows Java syntax cold, but hasn't recently explained GC types, immutability rules, or OOP gotchas under interview pressure.

> **What you will be able to do after this:** Write Sliding Window and Rotated Binary Search without IDE, explain 4 GC types + when to use each, chain CompletableFutures confidently, design an immutable class, and answer every OOP verbal question EPAM throws — including the static-method trap.

---

## 🧾 Index — Jump to Any Section

| # | Topic | Time |
| --- | --- | --- |
| [1. Sliding Window — Two Problems](#sliding-window) | Longest Substring + Max Window | ~60 min |
| [2. Binary Search + Linked List](#binary-search) | Rotated Array + Rotate by K | ~60 min |
| [3. JVM Internals — GC + Memory + Immutability](#jvm) | 4 GC types, Java version changes, immutable class | ~60 min |
| [4. TTL Cache + CompletableFuture Chaining](#completable-future) | TTL design, thenApply vs thenCompose, thread pool | ~60 min |
| [5. OOP — The 4 Pillars + Interview Gotchas](#oop) | Encapsulation, Inheritance, Polymorphism, Abstraction + traps | bonus |

---

<a id="sliding-window"></a>

## 🔹 1. Sliding Window — Two Problems

### 📖 Terminology

- **Sliding window** — a two-pointer technique where a contiguous subarray or substring "window" is expanded or contracted as we scan, avoiding the need to re-examine every subrange from scratch.
- **Window invariant** — the property that must always hold true for everything inside the window (e.g., "all characters in [left, right] are unique").

---

### 🧠 Mental Model — The Worm

Imagine a worm on a string of characters. The worm has a head (right pointer) and a tail (left pointer). The worm:
- **Grows** (right moves forward) to expand the window
- **Shrinks** (left moves forward) when the window violates the invariant

The worm never moves backward. Total movement: O(n).

---

### 🎨 Visual — Longest Substring Without Repeating Characters

```
  String: a b c b d e

  Step 1: right=0, window=[a],         seen={a:0},   maxLen=1
  Step 2: right=1, window=[a,b],       seen={a:0,b:1}, maxLen=2
  Step 3: right=2, window=[a,b,c],     seen={a:0,b:1,c:2}, maxLen=3
  Step 4: right=3, char='b', SEEN at index 1
          left moves to 1+1=2, window=[c,b],
          seen={...,b:3}, maxLen stays 3
  Step 5: right=4, window=[c,b,d],     maxLen=3
  Step 6: right=5, window=[c,b,d,e],   maxLen=4 ✓

KEY INVARIANT:
   The window [left, right] always contains unique characters.
   When a duplicate is seen, left jumps past the previous occurrence of that char.
   We store the LAST SEEN INDEX (not just a Set) so we can jump left correctly.
```

---

### 🔬 Problem 1 — Longest Substring Without Repeating Characters (LeetCode 3)

**Steps in plain English:**

1. Keep a map from `char → last seen index`. Keep a `left` pointer marking window start.
2. Expand `right` one character at a time.
3. If the character at `right` was already seen **and its last index is inside the current window** (`≥ left`), shrink the window: move `left` to `lastIndex + 1`.
4. Update the character's last seen index.
5. Update max length.

```java
public int lengthOfLongestSubstring(String s) {
    // char → last seen index
    Map<Character, Integer> lastSeen = new HashMap<>();
    int maxLen = 0;
    int left = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);

        // If char is inside current window, shrink window from left
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1;
        }

        lastSeen.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }

    return maxLen;
}
```

**Dry run — "abcbde":**
```
right=0 c='a' → window=[a]       len=1
right=1 c='b' → window=[a,b]     len=2
right=2 c='c' → window=[a,b,c]   len=3
right=3 c='b' → b seen at 1, left→2, window=[c,b]   len=2
right=4 c='d' → window=[c,b,d]   len=3
right=5 c='e' → window=[c,b,d,e] len=4 ✓
```

---

### 🔬 Problem 2 — Minimum Window Substring (LeetCode 76 — EPAM variant)

> "Given strings s and t, find the minimum window in s that contains all characters of t."

**Key idea:** Expand right until window covers all of t. Then shrink left until it no longer does. Track minimum window.

```java
public String minWindow(String s, String t) {
    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) {
        need.merge(c, 1, Integer::sum);
    }

    int have = 0;
    int required = need.size();
    int left = 0;
    int minLen = Integer.MAX_VALUE;
    int minLeft = 0;

    Map<Character, Integer> window = new HashMap<>();

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        window.merge(c, 1, Integer::sum);

        // Check if this character's count in window satisfies the need
        if (need.containsKey(c) && window.get(c).equals(need.get(c))) {
            have++;
        }

        // All required characters satisfied → try shrinking from left
        while (have == required) {
            if (right - left + 1 < minLen) {
                minLen = right - left + 1;
                minLeft = left;
            }

            char leftChar = s.charAt(left);
            window.merge(leftChar, -1, Integer::sum);

            if (need.containsKey(leftChar) && window.get(leftChar) < need.get(leftChar)) {
                have--;
            }
            left++;
        }
    }

    return minLen == Integer.MAX_VALUE ? "" : s.substring(minLeft, minLeft + minLen);
}
```

---

<a id="binary-search"></a>

## 🔹 2. Binary Search + Linked List

### 🔬 Problem 1 — Search in Rotated Sorted Array (LeetCode 33)

**The core insight:** Even after rotation, at least one half of the array is always sorted. Figure out which half is sorted, then decide which half to search.

```
  Example: [4, 5, 6, 7, 0, 1, 2],  target=0

  mid=3, nums[mid]=7
  Left half [4,5,6,7] is sorted (nums[left]=4 ≤ nums[mid]=7)
  target=0 is NOT in [4,7] → search right half
  ...
```

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
                // Target is in the sorted left half
                right = mid - 1;
            } else {
                // Target must be in the right half
                left = mid + 1;
            }
        } else {
            // Right half is sorted
            if (target > nums[mid] && target <= nums[right]) {
                // Target is in the sorted right half
                left = mid + 1;
            } else {
                // Target must be in the left half
                right = mid - 1;
            }
        }
    }

    return -1;
}
```

### 🎨 Visual — Rotated Array Logic

```
  Sorted:  [1, 2, 3, 4, 5, 6, 7]
  Rotated: [4, 5, 6, 7, 0, 1, 2]   ← rotation point between index 3 and 4

  At any mid, one half is ALWAYS a clean sorted subarray:
  If nums[left] ≤ nums[mid]  → LEFT half is sorted
  Else                       → RIGHT half is sorted

  Once you know which half is sorted, you can use a simple range check
  to decide if the target is in that sorted half or the other.

KEY INVARIANT:
   Exactly one of the two halves is sorted at every step.
   Find the sorted half, check if target is inside it, eliminate the other half.
```

---

### 🔬 Problem 2 — Rotate Linked List by K (LeetCode 61)

**Strategy in plain English:**

1. Find length; connect tail to head (make circular).
2. Actual rotation = `k % length` (rotating by length is a no-op).
3. New tail is at position `length - k % length - 1` (0-indexed).
4. New head is `newTail.next`. Cut the link.

```java
public ListNode rotateRight(ListNode head, int k) {
    if (head == null || head.next == null || k == 0) {
        return head;
    }

    // Step 1 — find length and tail
    int length = 1;
    ListNode tail = head;
    while (tail.next != null) {
        tail = tail.next;
        length++;
    }

    // Step 2 — make circular
    tail.next = head;

    // Step 3 — find new tail (length - k%length - 1 steps from head)
    int stepsToNewTail = length - k % length - 1;
    ListNode newTail = head;
    for (int i = 0; i < stepsToNewTail; i++) {
        newTail = newTail.next;
    }

    // Step 4 — cut and return
    ListNode newHead = newTail.next;
    newTail.next = null;

    return newHead;
}
```

---

<a id="jvm"></a>

## 🔹 3. JVM Internals — GC + Memory + Immutability

### 📖 Terminology

- **Stop-the-World (STW) pause** — a GC event where ALL application threads are paused while GC runs. The shorter the pause, the more responsive the app.
- **Throughput** — total work done per unit time (opposite concern from latency — high throughput often means longer GC pauses but more total work).
- **Metaspace** — native memory area (outside Java heap) that replaced PermGen in Java 8, storing class metadata. Unlike PermGen, it is not fixed-size — it grows as needed (bounded by system memory).

---

### 🔬 4 Garbage Collectors — Know Each in One Line

| GC | One-line description | When to use | Java status |
| --- | --- | --- | --- |
| **Serial GC** | Single-threaded, full STW pause | Tiny apps, single-core, embedded | Active (always available) |
| **Parallel GC** | Multi-threaded GC, STW pauses, maximizes throughput | Batch jobs, data pipelines, max throughput needed | Active, was default pre-Java-9 |
| **CMS (Concurrent Mark Sweep)** | Concurrent marking reduces pause time for old generation | ⚠️ Deprecated Java 9, **removed Java 14** — don't present as current | Removed in Java 14 |
| **G1 GC** | Divides heap into regions, predictable pause targets, balances throughput + latency | General-purpose apps — **default since Java 9** | Active (default) |
| **ZGC** | Ultra-low latency (sub-millisecond pauses even on terabyte heaps), concurrent | Latency-critical apps — **production-ready Java 15**, generational mode Java 21 | Active (use this as "modern low-latency GC") |

**⚠️ CMS is REMOVED — EPAM trap:** saying "CMS is available" in a 2026 interview is the GC equivalent of recommending Hystrix.

---

### 🎨 Visual — G1 GC Heap Layout vs Classic Layout

```
  CLASSIC GC HEAP (Serial/Parallel):
  ┌──────────────────────────┬────────────────────┐
  │        YOUNG GEN          │      OLD GEN        │
  │  [Eden] [S0] [S1]        │  (long-lived objs)  │
  └──────────────────────────┴────────────────────┘
  PermGen (pre-Java 8): class metadata, fixed size → OutOfMemoryError if full

  G1 GC HEAP (Java 9+ default):
  ┌────┬────┬────┬────┬────┬────┬────┬────┐
  │ E  │ S  │ O  │ O  │ E  │ H  │ E  │ S  │   E=Eden  S=Survivor
  ├────┼────┼────┼────┼────┼────┼────┼────┤   O=Old   H=Humongous
  │ O  │ O  │ S  │ E  │ O  │ O  │ S  │ E  │
  └────┴────┴────┴────┴────┴────┴────┴────┘
  Equal-sized regions, any region can be any type.
  G1 picks the regions with the most garbage to collect first → "Garbage First"
  Pause target is configurable (-XX:MaxGCPauseMillis=200)

  METASPACE (Java 8+):
  Outside the heap → native memory → grows automatically → no PermGen OOM

KEY INVARIANT:
   G1 → region-based, configurable pause target, default since Java 9.
   CMS → removed Java 14, don't mention it as current.
   ZGC → sub-millisecond pauses, production-ready Java 15, generational Java 21.
```

---

### 🔬 Java Version Changes — The Interview Table

| Version | Key change |
| --- | --- |
| **Java 8** | Lambdas + Streams. PermGen → **Metaspace**. Default methods in interfaces. |
| **Java 9** | **G1GC became default**. Module system (JPMS). Immutable collection factories (`List.of()`). |
| **Java 10** | **`var`** (local variable type inference — JEP 286). |
| **Java 11** | **`var` in lambda params** (JEP 323). `String` new methods (`isBlank`, `strip`, `lines`). ZGC preview. HTTP Client API. |
| **Java 14** | **CMS GC removed** (JEP 363). Switch expressions (standard). Records (preview). |
| **Java 15** | **ZGC production-ready** (JEP 377). Text blocks (standard). |
| **Java 17** | Sealed classes. Pattern matching `instanceof`. Strong encapsulation of JDK internals. LTS release. |
| **Java 21** | **Virtual threads** (Project Loom — JEP 444). **Generational ZGC**. Sequenced collections. Pattern matching in switch. LTS release. |

**⚠️ `var` is Java 10, not Java 11** — `var` in lambda params is Java 11. The study plan conflates these. Say both correctly.

---

### 🔬 Immutable Class — How to Create

Asked at EPAM as a verbal + code question. Know the 5 rules:

```java
// Rule 1: Declare class as final — prevents subclassing that could break immutability
public final class ImmutableOrder {

    // Rule 2: All fields are private and final
    private final Long id;
    private final String customerId;

    // Rule 3: If a field is a mutable object, store a defensive copy (not the original reference)
    private final List<String> items;

    public ImmutableOrder(Long id, String customerId, List<String> items) {
        this.id = id;
        this.customerId = customerId;
        // Defensive copy on the way IN — don't trust the caller's list
        this.items = List.copyOf(items);
    }

    // Rule 4: Only getters, no setters
    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    // Rule 5: If returning a mutable field, return a defensive copy (or unmodifiable view)
    public List<String> getItems() {
        // List.copyOf or Collections.unmodifiableList — caller cannot mutate it
        return Collections.unmodifiableList(items);
    }
}
```

**The 5 rules summarized:**

```
  1. class is final                  (no subclass can add setters)
  2. all fields are private final    (no reassignment)
  3. defensive copy on field IN      (don't store caller's mutable reference)
  4. no setters                      (obvious)
  5. defensive copy on field OUT     (don't expose your internal mutable reference)
```

---

<a id="completable-future"></a>

## 🔹 4. TTL Cache + CompletableFuture Chaining

### 🔬 TTL Cache Design

**TTL cache** (time-to-live cache — a cache where each entry automatically expires after a set duration, ensuring stale data is not served indefinitely) is asked at EPAM as a "design a data structure" question.

**Naive approach (two maps — watch out):** Using two separate maps (`values` and `timestamps`) is not atomic — a `get()` and `put()` on two different maps can be seen inconsistently by different threads.

**Correct approach — one map with a wrapper:**

```java
public class TtlCache<K, V> {

    // Inner class to hold both value and expiry timestamp together (atomic unit)
    private static class Entry<V> {
        final V value;
        final long expiresAt;   // System.currentTimeMillis() at which this entry is dead

        Entry(V value, long ttlMillis) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final ConcurrentHashMap<K, Entry<V>> store = new ConcurrentHashMap<>();
    private final long defaultTtlMillis;

    public TtlCache(long defaultTtlMillis) {
        this.defaultTtlMillis = defaultTtlMillis;
    }

    public void put(K key, V value) {
        store.put(key, new Entry<>(value, defaultTtlMillis));
    }

    public V get(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null) {
            return null;
        }
        // Lazy eviction: check on read, remove if expired
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry.value;
    }
}
```

**Follow-up: lazy eviction leak.** Expired entries that are never read stay in the map forever. Mention these fixes:
- Background cleanup thread that sweeps expired entries periodically
- Or: use **Caffeine** (`expireAfterWrite`) — it handles active eviction efficiently (window TinyLFU policy)

---

### 🔬 CompletableFuture — The Full Mental Map

**CompletableFuture** (a Java 8 class that represents a future result of an async computation and supports chaining, combining, and error handling without blocking) replaced raw `Future` which couldn't be composed.

---

### 🎨 Visual — CompletableFuture Pipeline

```
  supplyAsync  →  thenApply  →  thenApply  →  thenAccept
  (async start)   (sync xform)  (sync xform)   (terminal)

  supplyAsync  →  thenCompose  →  thenApply
  (async start)   (async chain)   (sync xform)

  The difference:
  thenApply:        T → U           (like Stream.map — runs on same thread)
  thenCompose:      T → CF<U>       (like Stream.flatMap — chains async stages)
  thenApplyAsync:   T → U           (runs on a new thread in the pool)
  exceptionally:    Throwable → T   (recovery — like catch block)
  handle:           (T, Throwable) → U (always runs, replaces thenApply + exceptionally)

KEY INVARIANT:
   Use thenCompose when the next step itself returns a CompletableFuture.
   Using thenApply there would give CompletableFuture<CompletableFuture<U>> — nested, unusable.
```

---

### 🔬 CompletableFuture — The 6 Operations You Need Cold

```java
// 1. supplyAsync — start async work that produces a result
//    Default thread pool: ForkJoinPool.commonPool() (shared by the whole JVM)
CompletableFuture<String> userId = CompletableFuture
    .supplyAsync(() -> fetchUserIdFromDb());

// 2. runAsync — start async work with no result (fire-and-forget)
CompletableFuture<Void> audit = CompletableFuture
    .runAsync(() -> auditLog.write("event"));

// 3. thenApply — synchronous transform (runs on same thread that completed the previous stage)
CompletableFuture<User> user = userId
    .thenApply(id -> userService.getUser(id));

// 4. thenApplyAsync — transform but on a NEW thread from the pool
CompletableFuture<User> userAsync = userId
    .thenApplyAsync(id -> userService.getUser(id));

// 5. thenCompose — chain another async operation (avoids nesting)
//    Without thenCompose: CompletableFuture<CompletableFuture<Order>> ← wrong!
//    With thenCompose:    CompletableFuture<Order>                     ← correct
CompletableFuture<Order> order = user
    .thenCompose(u -> orderService.fetchLatestOrder(u.getId()));

// 6. exceptionally — recover from failure (runs if any previous stage threw)
CompletableFuture<Order> safe = order
    .exceptionally(ex -> Order.empty());

// Combining two independent futures:
CompletableFuture<String> both = CompletableFuture
    .allOf(userId, audit)
    .thenApply(v -> "both done");
```

**⚠️ Thread pool trap (EPAM follow-up):**

```java
// Default: ForkJoinPool.commonPool() — shared across all CompletableFutures in the JVM
// If your tasks are long-running or blocking (DB calls, HTTP) this starves other tasks

// Fix: pass a dedicated Executor
ExecutorService pool = Executors.newFixedThreadPool(10);

CompletableFuture.supplyAsync(() -> blockingDbCall(), pool)
    .thenApplyAsync(result -> transform(result), pool);
```

---

<a id="oop"></a>

## 🔹 5. OOP — The 4 Pillars + Interview Gotchas

### 📖 Terminology

- **OOP (Object-Oriented Programming)** — a programming paradigm that organizes software around objects (instances of classes) that combine state and behavior, rather than around functions and procedures.
- **Encapsulation** — bundling data (fields) and the methods that operate on that data inside one class, and controlling access via access modifiers.
- **Inheritance** — a mechanism where a class (subclass) acquires fields and methods of another class (superclass).
- **Polymorphism** — the ability of the same method call or reference to behave differently depending on the actual type of the object at runtime.
- **Abstraction** — hiding implementation details and exposing only what's necessary through interfaces or abstract classes.

---

### 🧠 Mental Model — One Sentence Each

```
  Encapsulation: "I expose what you need, I hide how it works." → private fields + public methods
  Inheritance:   "A Car IS-A Vehicle — it inherits Vehicle's behavior."
  Polymorphism:  "The same `draw()` call behaves differently on Circle vs Square."
  Abstraction:   "You call .start() on any Vehicle; you don't care if it's a petrol or electric engine."
```

---

### 🔬 Encapsulation

```java
// ❌ BAD: fields are public — anyone can corrupt state directly
class BankAccount {
    public double balance;   // can be set to -1000 from anywhere
}

// ✅ GOOD: fields private, access controlled through methods that enforce rules
class BankAccount {
    private double balance;

    public void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit must be positive");
        }
        this.balance += amount;
    }

    public double getBalance() {
        return balance;
    }
}
```

---

### 🔬 Inheritance — IS-A vs HAS-A

```java
// IS-A relationship (inheritance) — Car IS-A Vehicle
class Vehicle {
    private String brand;

    public void start() {
        System.out.println(brand + " starting");
    }
}

class Car extends Vehicle {
    private int doors;

    public void honk() {
        System.out.println("Beep!");
    }
}

// HAS-A relationship (composition) — Car HAS-A Engine
class Car {
    private Engine engine;   // composition, not inheritance

    public void start() {
        engine.ignite();
    }
}

// Prefer composition over inheritance when you can:
// Inheritance creates tight coupling. Composition is more flexible.
```

---

### 🔬 Polymorphism — Two Flavors

**Compile-time polymorphism** (also called **method overloading** — same method name, different parameter list, resolved by the compiler):

```java
class Calculator {
    // Overloaded methods — same name, different signatures
    public int add(int a, int b) {
        return a + b;
    }

    public double add(double a, double b) {
        return a + b;
    }

    public int add(int a, int b, int c) {
        return a + b + c;
    }
}

// ⚠️ CANNOT overload on return type alone — compiler can't distinguish
// public int getValue()    ← compile error: same erased signature
// public double getValue() ← as above
```

**Runtime polymorphism** (also called **method overriding** — subclass provides a different implementation, resolved at runtime by the JVM based on the actual object type):

```java
class Animal {
    public String speak() {
        return "...";
    }
}

class Dog extends Animal {
    @Override
    public String speak() {
        return "Woof!";
    }
}

class Cat extends Animal {
    @Override
    public String speak() {
        return "Meow!";
    }
}

// Same reference type (Animal), different runtime behavior:
Animal a = new Dog();
System.out.println(a.speak());   // prints "Woof!" — runtime dispatch

Animal b = new Cat();
System.out.println(b.speak());   // prints "Meow!"
```

---

### ⚠️ Static Methods — Hidden, NOT Overridden (Classic Trap)

```java
class Parent {
    public static void greet() {
        System.out.println("Hello from Parent");
    }
}

class Child extends Parent {
    // This does NOT override — it HIDES the parent's static method
    public static void greet() {
        System.out.println("Hello from Child");
    }
}

// Resolves at COMPILE TIME (based on reference type), not runtime:
Parent p = new Child();
p.greet();   // prints "Hello from Parent" ← NOT "Hello from Child"!
Child.greet();   // prints "Hello from Child"

// @Override on a static method causes a compile error — confirms it's not overriding.
```

**Why:** Runtime polymorphism (dynamic dispatch) works through the vtable (virtual dispatch table). Static methods don't go through the vtable — they're bound at compile time to the reference type.

---

### 🔬 Abstraction — Abstract Class vs Interface (Post-Java-8)

The "when to use abstract class vs interface" question has changed since Java 8 added **default methods** (methods with a body in interfaces). Know the current answer:

```
  INTERFACE:
  - A contract — "every implementor MUST support these behaviors"
  - All methods public by default (before Java 8), can have default + static methods (Java 8+)
  - No constructor, no instance state (no instance fields)
  - A class can implement MULTIPLE interfaces (solves the multiple-inheritance of type problem)

  ABSTRACT CLASS:
  - A partial implementation — "here's some common behavior, subclasses fill in the rest"
  - CAN have instance state (private fields), constructors, any access modifier
  - A class can extend only ONE abstract class

  POST-JAVA-8 QUESTION: "Why use abstract class if interfaces now have default methods?"
  Answer: when you need:
  1. Instance state (instance fields) — interfaces can't have them
  2. A constructor (e.g., to initialize shared state) — interfaces can't have them
  3. Protected or package-private methods — all interface methods are public

  Example:
```

```java
// Interface (a contract) — what something CAN do
interface Serializable {
    byte[] serialize();
    Object deserialize(byte[] data);
}

// Abstract class (partial implementation) — what something IS with shared behavior
abstract class BaseRepository<T> {

    // Instance state — impossible in an interface
    private final DataSource dataSource;

    // Constructor to initialize shared state
    protected BaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Concrete shared method — all repositories benefit from this
    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Abstract "hook" — each subclass fills this in
    public abstract T findById(Long id);
}

class UserRepository extends BaseRepository<User> {
    public UserRepository(DataSource ds) {
        super(ds);
    }

    @Override
    public User findById(Long id) {
        // Use getConnection() from base class
        ...
    }
}
```

---

### 🎨 Visual — Overloading vs Overriding Side-by-Side

```
  OVERLOADING (Compile-Time / Static Dispatch):
  ┌─────────────────────────────────────────────────────────┐
  │ Same class, same name, DIFFERENT parameters             │
  │ Resolved by COMPILER based on argument types            │
  │ add(int, int) vs add(double, double) → different methods│
  │ Cannot overload on return type alone                    │
  └─────────────────────────────────────────────────────────┘

  OVERRIDING (Runtime / Dynamic Dispatch):
  ┌─────────────────────────────────────────────────────────┐
  │ Subclass, same name, SAME parameters, same return type  │
  │ Resolved at RUNTIME based on actual object type         │
  │ @Override annotation verifies it's a true override      │
  │ Access modifier can ONLY be widened (not narrowed)      │
  │ Static methods CANNOT be overridden (they are hidden)   │
  └─────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Overloading = compiler picks which method. Overriding = JVM picks which method at runtime.
   static = compile-time binding. instance = runtime binding.
```

---

### 🔬 Method Overriding Rules — What's Allowed

```java
class Parent {
    // Can widen access: protected → public ✅
    // Cannot narrow: public → protected ❌ (compile error)
    protected String getData() {
        return "parent";
    }

    // Covariant return type: subclass can return a more specific type ✅
    public Animal createAnimal() {
        return new Animal();
    }
}

class Child extends Parent {

    // ✅ Widened access: protected → public is allowed
    @Override
    public String getData() {
        return "child";
    }

    // ✅ Covariant return type: Dog is a subtype of Animal
    @Override
    public Dog createAnimal() {
        return new Dog();
    }
}
```

---

## 🧾 TL;DR — 14 Things to Say Cold Tomorrow

1. **Sliding window key:** store `char → last seen INDEX` (not just a Set), so left pointer can jump correctly on duplicate
2. **Rotated binary search key:** at each step, one half is always sorted — find it, range-check, eliminate the other half
3. **TTL cache:** one map with a `(value, expiresAt)` wrapper class — two separate maps aren't atomic together
4. **CMS GC is REMOVED** (Java 14) — if asked, say "CMS was deprecated in Java 9 and removed in Java 14; G1 is the default since Java 9, ZGC for low-latency since Java 15"
5. **`var` = Java 10** (local variables); **`var` in lambdas = Java 11**
6. **Virtual threads = Java 21** (Project Loom); **Metaspace replaced PermGen = Java 8**
7. **thenApply vs thenCompose:** thenApply is T→U; thenCompose is T→CF\<U\> (use when next step is itself async, to avoid nesting)
8. **CompletableFuture default thread pool:** `ForkJoinPool.commonPool()` — pass a custom Executor for blocking tasks
9. **Overloading = compile-time** (resolved by compiler based on argument types); **Overriding = runtime** (resolved by JVM based on actual object type)
10. **Static methods are HIDDEN, not overridden** — calling via superclass reference prints superclass result even if you defined it in subclass
11. **Cannot overload on return type alone** — compiler can't distinguish `int getValue()` from `double getValue()`
12. **Covariant return type allowed:** subclass override can return a more specific type (Dog instead of Animal)
13. **Abstract class vs Interface post-Java-8:** use abstract class when you need instance state (fields), constructors, or non-public methods — interfaces can have default methods but no instance state
14. **Immutable class 5 rules:** final class, private final fields, defensive copy in, no setters, defensive copy/view out

---

## 🎯 Interview Q&A — Day 4 Topics

### ⭐ Section A — Asked in EPAM (Reported 2024–2025)

---

**Q1. What are the 4 main GC types in Java? When would you use each?**

> **Serial GC** — single-threaded, stops all application threads for both minor and major GC. Only sensible for tiny single-core apps or embedded systems. `-XX:+UseSerialGC`.
>
> **Parallel GC** — uses multiple threads for GC work, but still stops the world during collection. Maximizes throughput at the cost of longer pauses. Good for batch jobs and data pipelines where raw throughput matters more than individual response latency. Was the default before Java 9.
>
> **G1 GC (Garbage First)** — divides the heap into equal-sized regions. Each region can be Eden, Survivor, Old, or Humongous. G1 collects the regions with the most garbage first (hence the name). Offers predictable pause-time targets (`-XX:MaxGCPauseMillis`). Default GC since Java 9. Best all-around choice for most web applications.
>
> **ZGC** — concurrent GC with sub-millisecond pause times even on terabyte heaps. Almost all GC work happens concurrently while your application runs. Production-ready since Java 15 (JEP 377). Generational ZGC (further improvements) in Java 21. Best choice when you have strict latency requirements.
>
> **⚠️ CMS (Concurrent Mark Sweep)** was deprecated in Java 9 and **removed in Java 14** — never present it as a current option.

---

**Q2. What are the most important Java changes from version 8 to 21?**

> I group them by what actually matters in backend development:
>
> **Java 8** — Lambdas + Streams (most impactful API change ever). `Optional`. Default methods in interfaces. `CompletableFuture`. PermGen replaced with **Metaspace** (native memory, no fixed-size OOM from class loading).
>
> **Java 9** — **G1GC became the default**. Module system (JPMS). Immutable collection factory methods: `List.of()`, `Map.of()`.
>
> **Java 10** — **`var`** (local variable type inference) — `var list = new ArrayList<String>()`.
>
> **Java 11** — **`var` in lambda parameters**. New `String` methods: `isBlank()`, `strip()`, `lines()`. ZGC as experimental preview. Official LTS release.
>
> **Java 14** — **CMS GC removed**. Switch expressions become standard. Records (preview).
>
> **Java 15** — **ZGC production-ready**.
>
> **Java 17** — Sealed classes. Pattern matching `instanceof`. Strong encapsulation of JDK internals. LTS release.
>
> **Java 21** — **Virtual threads (Project Loom)** — lightweight threads that don't consume OS threads, enabling millions of concurrent tasks without thread pool tuning. **Generational ZGC**. Pattern matching in switch. Sequenced collections. LTS release.

---

**Q3. Can you override a static method in Java?**

> No — and this is an important distinction. What looks like overriding a static method is actually **method hiding**, not overriding.
>
> Runtime polymorphism (overriding) works through the vtable — a per-class table of method pointers that the JVM looks up at runtime based on the actual object type. Static methods are resolved at compile time based on the declared reference type — they never go through the vtable.
>
> ```java
> class Parent {
>     public static void greet() { System.out.println("Parent"); }
> }
> class Child extends Parent {
>     public static void greet() { System.out.println("Child"); }   // HIDES, not overrides
> }
>
> Parent p = new Child();
> p.greet();   // prints "Parent" — resolved at compile time from the reference type
> Child.greet();   // prints "Child" — resolved from the declared class
> ```
>
> If you put `@Override` on a static method, the compiler throws an error — which is the confirmation that static methods cannot be overridden.

---

**Q4. What is the difference between an abstract class and an interface? When do you use each?**

> Before Java 8, the rule was simple: interfaces were pure contracts (all abstract, all public methods), abstract classes could have implementations and state.
>
> Since Java 8, interfaces can have `default` methods (methods with bodies) and `static` methods. So the question now is: what can abstract classes do that interfaces still can't?
>
> **Three things only abstract classes can do:**
> 1. **Instance fields** — interfaces have no instance state, only constants (`static final`). If you need shared state (a counter, a logger, a cached connection), you need an abstract class.
> 2. **Constructors** — interfaces have no constructors. If your shared initialization logic needs parameters or needs to run once per instance, you need an abstract class.
> 3. **Non-public methods** — all interface methods are `public`. If you want a `protected` helper that subclasses use but external callers don't, you need an abstract class.
>
> Use an **interface** when you're defining a contract that multiple unrelated classes can fulfill — and especially when multiple inheritance of type is needed (a class can implement many interfaces but extend only one class).
>
> Use an **abstract class** when you're defining a partial implementation with shared state or setup code — like `BaseRepository` with a shared `DataSource` field.

---

**Q5. How do you create an immutable class in Java?**

> Five rules, applied together:
>
> 1. **Declare the class `final`** — prevents subclasses from adding setters or overriding getters to expose mutable state.
> 2. **All fields `private` and `final`** — fields are assigned once in the constructor and never changed.
> 3. **No setters** — obvious.
> 4. **Defensive copy on the way IN** — if a constructor accepts a mutable object (like a `List`), don't store the caller's reference. Copy it. The caller might mutate their list after construction.
> 5. **Defensive copy on the way OUT** — if a getter returns a mutable field, don't expose the internal reference. Return `Collections.unmodifiableList(this.items)` or a copy.
>
> ```java
> public final class Order {
>     private final Long id;
>     private final List<String> items;
>
>     public Order(Long id, List<String> items) {
>         this.id = id;
>         this.items = List.copyOf(items);   // defensive copy IN
>     }
>
>     public Long getId() { return id; }
>
>     public List<String> getItems() {
>         return Collections.unmodifiableList(items);   // defensive copy OUT
>     }
> }
> ```
>
> `String`, `Integer`, and all Java wrapper types are immutable. `LocalDate`, `LocalDateTime` in java.time are immutable. Records (Java 16+) give you immutability by default with less boilerplate.

---

### 🌐 Section B — Commonly Asked (DSA + JVM + OOP)

---

**Q6. What is the difference between method overloading and method overriding?**

> **Overloading** is compile-time (static) polymorphism — same class, same method name, different parameter lists. The compiler picks which method to call based on the argument types at compile time.
>
> **Overriding** is runtime (dynamic) polymorphism — subclass provides a different implementation for an inherited method with the same signature. The JVM picks which implementation to call at runtime based on the actual object type, not the declared reference type.
>
> Key rules for overriding: same name, same parameter list, return type must be the same or a covariant subtype. Access modifier can only be widened (protected → public is OK; public → protected is a compile error). Cannot override `final`, `static`, or `private` methods.
>
> The `@Override` annotation makes the compiler verify you're actually overriding something — if the parent class doesn't have a matching method, you get a compile error. Always use it.

---

**Q7. What is the difference between Stack and Heap in Java memory?**

> The **Stack** stores method frames — local variables, method parameters, and return addresses. Each thread has its own stack. A new frame is pushed when a method is called, popped when it returns. Stack memory is very fast (LIFO pointer movement) and automatically managed. Stack size is limited — deep recursion causes `StackOverflowError`.
>
> The **Heap** is shared across all threads and stores all object instances and arrays. When you do `new SomeClass()`, the object lives on the heap. The heap is managed by the garbage collector.
>
> Primitives declared as local variables (`int x = 5;` inside a method) live on the stack. Object references declared as local variables also live on the stack — but the object they point to lives on the heap. Instance fields of objects always live on the heap (as part of the object).
>
> `OutOfMemoryError` comes from the heap filling up. `StackOverflowError` comes from too many nested method calls.

---

**Q8. What causes `OutOfMemoryError` and how do you diagnose it?**

> Common causes:
> - **Heap exhaustion** — objects are being created faster than GC can collect them, or large objects are being retained (memory leak). Classic leak: holding references in a static collection that never gets cleared.
> - **Metaspace exhaustion** — too many classes loaded (common with dynamic class generation or hot reload in dev). Before Java 8, this was PermGen overflow.
> - **Off-heap exhaustion** — direct ByteBuffers, mapped files, or native memory exhaustion (less common).
>
> Diagnosis steps:
> 1. **Read the OOM message** — it says which space was exhausted: `Java heap space`, `Metaspace`, `GC overhead limit exceeded` (GC spending >98% of time collecting <2% of heap — effectively an OOM).
> 2. **Enable GC logging** (`-Xlog:gc*` in Java 9+) to see GC behavior before the OOM.
> 3. **Take a heap dump** — `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/dump.hprof`. Analyze with Eclipse MAT or IntelliJ's heap analyzer.
> 4. **Look for dominator tree** — MAT shows which objects retain the most memory. Find the root reference keeping everything alive.

---

**Q9. Explain the Sliding Window technique. When do you use it?**

> Sliding Window is a two-pointer technique for problems that involve a contiguous subarray or substring. Instead of checking every possible subarray (O(n²)), you maintain a window `[left, right]` and move it forward — expanding the right side and contracting the left side based on whether the window satisfies a condition. Total complexity: O(n) — each element is visited at most twice (once by right, once by left).
>
> Use it when the problem asks for: longest/shortest subarray satisfying a condition, minimum window containing all of something, max sum subarray of size K, count of subarrays with at most K distinct elements.
>
> The key insight: the problem must have a property where if a window of size N satisfies the condition, you don't need to re-examine the whole window when you expand by one — you just process the new element.
>
> Classic EPAM problem: **Longest Substring Without Repeating Characters**. Store `char → last seen index`. When a duplicate is found inside the window, move `left` to `lastIndex + 1`. Window always maintains the invariant: all characters are unique.

---

**Q10. What is the difference between `==` and `.equals()` in Java?**

> `==` compares **references** — it checks whether two variables point to the exact same object in memory. For primitives (`int`, `char`, etc.), `==` compares the values directly.
>
> `.equals()` compares **logical content** — whatever the class decides "equal" means. `String.equals()` compares character-by-character. `Integer.equals()` compares numeric value. By default (from `Object`), `.equals()` falls back to `==` (reference equality), which is why you must override it for value objects.
>
> The classic trap:
> ```java
> String a = new String("hello");
> String b = new String("hello");
> System.out.println(a == b);       // false — two different objects
> System.out.println(a.equals(b)); // true  — same content
>
> Integer x = 127;
> Integer y = 127;
> System.out.println(x == y);   // true — Integer cache [-128, 127], same cached object
>
> Integer p = 200;
> Integer q = 200;
> System.out.println(p == q);   // false — outside cache range, different objects
> ```
>
> Rule: always use `.equals()` for logical equality of objects. Use `==` only when you intentionally want to check object identity (e.g., checking if two references point to the same cached singleton).

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Sep 2026 | Day 4 notes created. CMS removed Java 14 (not "deprecated Java 9+" as study plan says — corrected). `var` split correctly: Java 10 (local variables) vs Java 11 (lambda params). CompletableFuture thread pool trap (ForkJoinPool.commonPool + custom Executor). Static method hiding vs overriding called out explicitly. Abstract class vs interface post-Java-8 updated. TTL cache: single-map wrapper approach (not two maps). OOP section added per Kapil's request. |
