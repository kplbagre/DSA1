# ☕ Java Pass-by-Value Semantics — Deep Dive

> **What this note answers:** *"When I pass an array / List / object to a method in Java, does the method see the same instance the caller has? Does mutation propagate? What about reassignment? And how do I OPT OUT when I don't want sharing?"*

---

## 🎯 Why You're Reading This (The Goal)

You hit this question while solving **LC 200 Number of Islands** with a separate `boolean[][] visited` array. Every recursive DFS call mutated `visited[r][c] = true`, and the marks persisted across all calls — which is exactly what made the algorithm work. The question was: **is this "pass by reference"?**

The short answer is **no — Java is always pass-by-value.** The longer answer is that for *objects* (which includes arrays), the *value being passed is a reference*, and that subtle distinction is what makes mutations propagate but reassignments not. This note unpacks the full mental model and shows you how to opt out of the sharing behavior when you want **pure pass-by-value semantics** for objects.

**Where this concept bites:**

| Context | The trap |
| --- | --- |
| **DSA** | DP memo arrays, DSU `parent[]`, BFS `visited[]`, graph adjacency-list mutation across recursion |
| **Backend** | DTOs shared across layers, JPA entities mutated post-save, Spring singleton beans with shared state, `@Cacheable` returning the same reference |
| **Concurrency** | Multi-threaded access to a shared mutable object passed via parameter |
| **API design** | Public methods accepting `List<X>` — should they defensive-copy, or trust the caller? |

By the end of this note you should be able to answer the interview question *"Is Java pass-by-value or pass-by-reference?"* in **one precise sentence** and back it up with a code demo.

---

## 📖 Terminology (Memorize These)

| Term | Definition |
| --- | --- |
| **Pass-by-value** | The function receives a **COPY of the argument's value**. Modifying the parameter inside the function does NOT affect the caller's variable. *(This is what Java does, always.)* |
| **Pass-by-reference** | The function receives an **alias for the caller's variable itself**. Reassigning the parameter inside the function WOULD affect the caller's variable. *(Java does NOT have this. C++ does, via `&`. C# does, via `ref`.)* |
| **Reference** | In Java, a value that identifies a heap object. For our purposes: "the address" of an object on the heap. (Java's spec is careful never to say "pointer," but mentally it works like one — minus pointer arithmetic.) |
| **Identity (`==`)** | "Same object on the heap?" Compares references for objects, raw values for primitives. |
| **Equality (`.equals(...)`)** | "Same logical value?" Defined by each class's `equals` override. |
| **Shallow copy** | New top-level container, but inner references still point to the same nested objects. |
| **Deep copy** | Recursively copies every nested object — caller and copy share no heap state. |
| **Defensive copy** | A shallow or deep copy made at a method boundary specifically to prevent the caller and callee from sharing mutable state. |
| **Immutable object** | An object whose state cannot change after construction. `String`, `Integer`, `LocalDate`, records (when their components are themselves immutable). |

---

## 🧠 The One-Sentence Rule

> **"Java passes the VALUE of the reference."**

- For **primitives** (`int`, `double`, `boolean`, `char`, etc.), the value IS the integer / double / boolean itself.
- For **objects** (everything else, including arrays), the value is a **reference** that identifies a heap object.

When you call a method, the parameter receives a **fresh local copy** of that value. For primitives, that's a copy of the number. For objects, that's a copy of the reference — but the reference still points to the SAME heap object as the caller's reference.

### 🎨 Visual — Stack frames and the heap

```
                                  STACK                  HEAP
                          ┌─────────────────────┐
  Caller's frame:         │  nums  ──────────┐  │
  List<Integer> nums      │                  │  │       ┌───────────────────┐
                          │                  ├──┼──────►│  ArrayList object │
  addItem(nums);          │                  │  │       │  contents: [1,2]  │
                          ├─────────────────────┤       └───────────────────┘
  Callee's frame:         │  list  ──────────┘  │              ▲
  void addItem(           │  (separate variable, │              │
       List<Integer> list)│  same reference value│              │
                          │  pointing to the SAME│              │
                          │  heap object)        │              │
                          └─────────────────────┘

  KEY:
  - `nums` and `list` are TWO different stack variables (in two different frames).
  - Both hold the SAME reference value (same arrow tip into the heap).
  - There is ONE underlying ArrayList object on the heap.
  - Mutation via `list.add(99)` modifies that shared heap object — visible to `nums`.
  - Reassignment `list = new ArrayList<>()` makes `list` point elsewhere — caller's
    `nums` is unaffected (still points to the original heap object).
```

> **The single most important takeaway:** the *reference variable* is duplicated; the *object on the heap* is shared.

---

## 🪜 Three Cases — Built Up From Simplest to Trickiest

### Case 1: Primitives — pure value copy, no surprises

```java
public static void increment(int x) {
    x = x + 1;        // modifies the local copy only
}

int a = 5;
increment(a);
System.out.println(a);   // prints 5 — unchanged
```

**Why:** the parameter `x` is a brand-new variable on the callee's stack frame, initialized with a copy of `a`'s value (5). Incrementing `x` doesn't touch `a`. There's no heap object involved.

This is what people *intuitively* mean by "pass by value" — and it's exactly what Java does for primitives.

### Case 2: Immutable objects — looks like value semantics, but technically a reference is passed

```java
public static void rename(String s) {
    s = s + " modified";    // creates a NEW String, reassigns local s
}

String name = "Kapil";
rename(name);
System.out.println(name);   // prints "Kapil" — unchanged
```

**Why:** `s` does receive a copy of the reference — but `String` is immutable, so `s + " modified"` doesn't mutate the original `"Kapil"` heap object; it allocates a **new** String object and points local `s` at it. The caller's `name` still points to the original.

**For immutable types, the practical effect mimics pass-by-value semantics** — even though under the hood, what's passed is a reference. You literally cannot tell the difference because mutation isn't possible.

> **Immutable types in the JDK to memorize:** `String`, all boxed numeric types (`Integer`, `Long`, `Double`, etc.), `Boolean`, `Character`, `BigInteger`, `BigDecimal`, `LocalDate` / `LocalDateTime` / `Instant` (all `java.time.*`), `UUID`, `Optional`, sealed records whose components are themselves immutable.

### Case 3: Mutable objects — the trap

This is where everyone gets bitten. Two sub-cases:

**Case 3a — Mutation propagates ✅**

```java
public static void addItem(List<Integer> list) {
    list.add(42);          // mutates the SHARED heap object
}

List<Integer> nums = new ArrayList<>();
nums.add(1);
addItem(nums);
System.out.println(nums);  // prints [1, 42] — mutation propagated
```

**Why:** `list` and `nums` are two different stack variables holding the SAME reference value. `list.add(42)` invokes a method on the heap object — the same heap object `nums` points to. The caller sees the change.

**Case 3b — Reassignment does NOT propagate ❌**

```java
public static void replaceList(List<Integer> list) {
    list = new ArrayList<>();      // reassigns local — caller unaffected
    list.add(99);
}

List<Integer> nums = new ArrayList<>();
nums.add(1);
replaceList(nums);
System.out.println(nums);          // prints [1] — caller's reference untouched
```

**Why:** `list = new ArrayList<>()` allocates a fresh heap object and points the local `list` at it. The caller's `nums` was never given a way to be reassigned — it still points to the original `[1]` ArrayList.

> **This pair (3a / 3b) is the litmus test that proves Java is pass-by-value, not pass-by-reference.** If Java were truly pass-by-reference, `replaceList` would replace the caller's list too. It doesn't. End of debate.

---

## 🔬 Litmus Tests — Three Demos That Prove the Rule

### Test 1: Mutation propagates

```java
int[] arr = {1, 2, 3};
mutate(arr);
System.out.println(arr[0]);        // 99 — propagated ✅

static void mutate(int[] a) {
    a[0] = 99;
}
```

### Test 2: Reassignment does NOT propagate

```java
int[] arr = {1, 2, 3};
reassign(arr);
System.out.println(arr[0]);        // 1 — NOT propagated ❌

static void reassign(int[] a) {
    a = new int[]{99, 98, 97};     // local-only change
}
```

### Test 3: Swap is impossible in Java — the senior interview question

> **The classic interview prompt:** *"Write a method `swap(int a, int b)` that swaps the values of two variables in the caller."*

```java
// This does NOTHING from the caller's perspective:
public static void swap(int a, int b) {
    int temp = a;
    a = b;
    b = temp;
}

int x = 1, y = 2;
swap(x, y);
System.out.println(x + " " + y);   // "1 2" — caller unchanged
```

**The senior follow-up:** *"What if I use Integer objects instead of int?"*

```java
public static void swap(Integer a, Integer b) {
    Integer temp = a;
    a = b;
    b = temp;
}
// Still does nothing — only local references swapped.
// Plus Integer is immutable anyway, so even mutation wouldn't help.
```

**The correct interview answer:**

> *"You can't write a swap method in Java for two variables in the caller — Java is pass-by-value, so the method only sees copies of the references / values. The closest you can do is mutate two SLOTS of a shared container — e.g., `swap(int[] arr, int i, int j)` — or return a two-element record / array. The language doesn't expose what C++ does with `&` or C# does with `ref`."*

That sentence in an interview signals you understand Java memory semantics deeply.

---

## ⚠️ Common Bugs Hall of Fame

Real bugs that real Java engineers (including future-Kapil) have shipped.

### Bug 1 — "I passed my List to a helper, why did it get mutated?"

```java
public List<User> getActiveUsers() {
    List<User> users = repo.findAll();
    filterByActive(users);                    // ← mutates `users` in place!
    return users;
}

// helper:
private void filterByActive(List<User> users) {
    users.removeIf(u -> !u.isActive());       // mutates the SHARED list
}
```

The caller may not have *expected* `filterByActive` to mutate the input. If `repo.findAll()` is cached, you've now polluted the cache.

**Fix options:** either rename to `filterByActiveInPlace` (make the mutation explicit) or change the helper to return a new list and not mutate.

### Bug 2 — `ConcurrentModificationException` from iteration + mutation

```java
public void removeNegatives(List<Integer> nums) {
    for (Integer n : nums) {
        if (n < 0) {
            nums.remove(n);          // ← throws CME
        }
    }
}
```

The `for-each` loop uses an iterator; mutating the list outside the iterator's `remove()` invalidates it.

**Fix:** use `Iterator.remove()` or `list.removeIf(predicate)`.

### Bug 3 — Returning an internal mutable field (encapsulation leak)

```java
public class OrderService {
    private final List<Order> pending = new ArrayList<>();

    public List<Order> getPending() {
        return pending;              // ← caller can mutate internal state!
    }
}

// Caller can now do:
orderService.getPending().clear();   // wipes the service's state
```

**Fix:** return a defensive copy or an unmodifiable view (see Strategy section below).

### Bug 4 — Mutable object as a HashMap key

```java
Map<List<Integer>, String> map = new HashMap<>();
List<Integer> key = new ArrayList<>(List.of(1, 2));
map.put(key, "first");

key.add(3);                                 // mutate the key after insertion
System.out.println(map.get(key));           // null!  the bucket the entry is in
                                            // was computed from the OLD hashCode
```

**Why:** `HashMap` computes the bucket using `hashCode()` at insertion time. Mutating the key changes its `hashCode()` → the entry is now in the "wrong" bucket → it becomes unreachable.

**Fix:** never use a mutable object as a map key. Use immutable keys (`String`, `Integer`, records, `List.copyOf(...)`).

### Bug 5 — `final` on a parameter doesn't prevent content mutation

```java
public void process(final List<Integer> nums) {
    nums.add(99);                // ← LEGAL — final only prevents reassignment of `nums`
    // nums = new ArrayList<>(); // this WOULD be illegal
}
```

**Lesson:** `final` parameters don't give you immutability. They only prevent reassignment of the local variable. They're a style preference, not a protection mechanism.

---

## 🛡️ Achieving "Pure Pass-by-Value" Semantics — Three Strategies

When you want to *opt out* of sharing — i.e., guarantee that the caller's object can't be affected by the callee — you have three tools.

### Strategy 1: Defensive copy at the boundary

Make a shallow copy of the argument as the first line of your method (or right after receiving a return value from another method). Mutation in your scope no longer affects the caller.

```java
public void process(List<Integer> nums) {
    List<Integer> local = new ArrayList<>(nums);    // ← shallow copy
    local.add(99);                                  // callee mutates LOCAL only
    // caller's `nums` is untouched
}
```

**Cheatsheet for shallow copying common types:**

| Type | Copy idiom |
| --- | --- |
| 1D primitive array | `Arrays.copyOf(arr, arr.length)` or `arr.clone()` |
| 1D object array | `Arrays.copyOf(arr, arr.length)` or `arr.clone()` |
| 2D array | **Each row separately** — `clone()` is shallow; see below |
| `ArrayList<T>` | `new ArrayList<>(orig)` |
| `LinkedList<T>` | `new LinkedList<>(orig)` |
| `HashMap<K, V>` | `new HashMap<>(orig)` |
| `LinkedHashMap<K, V>` | `new LinkedHashMap<>(orig)` |
| `HashSet<T>` | `new HashSet<>(orig)` |
| `TreeSet<T>` / `TreeMap<K, V>` | `new TreeSet<>(orig)` / `new TreeMap<>(orig)` |

> **The 2D array gotcha:** `int[][] copy = arr.clone()` produces a NEW outer array but the rows are still shared! To deep-copy a 2D array:
> ```java
> int[][] deep = new int[arr.length][];
> for (int i = 0; i < arr.length; i++) {
>     deep[i] = arr[i].clone();
> }
> ```
> Same issue applies to `List<List<Integer>>`, `Map<String, List<Integer>>`, and any nested container — `new ArrayList<>(outer)` doesn't copy the inner lists.

### Strategy 2: Deep copy for nested structures

For nested mutable structures, you need recursion. Several approaches:

**(a) Manual recursion** (most reliable, no surprises):

```java
public static List<List<Integer>> deepCopy(List<List<Integer>> orig) {
    List<List<Integer>> copy = new ArrayList<>(orig.size());
    for (List<Integer> inner : orig) {
        copy.add(new ArrayList<>(inner));     // inner elements (Integer) are immutable — shallow is fine
    }
    return copy;
}
```

**(b) Why `Cloneable` / `Object.clone()` is a trap:**

- `clone()` is `protected` on `Object` — you have to override it as `public`
- It throws `CloneNotSupportedException` — a checked exception you have to handle
- It produces a **shallow** copy by default — you have to override to make it deep
- It's marker-interface-driven (`Cloneable`), which Josh Bloch famously called "broken" in *Effective Java*

**Verdict:** avoid `Cloneable`. Use copy constructors (`new Foo(other)`) or static factory methods (`Foo.from(other)`).

**(c) Serialization-based deep copy** (slow, but generic):

```java
// Requires the class to be Serializable.
// Slow — full reflection + I/O — only acceptable for occasional ops.
public static <T extends Serializable> T deepCopyViaSerialization(T orig) throws Exception {
    var baos = new ByteArrayOutputStream();
    try (var oos = new ObjectOutputStream(baos)) {
        oos.writeObject(orig);
    }
    try (var ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
        return (T) ois.readObject();
    }
}
```

**(d) Library-based deep copy** (Jackson, Kryo) — when allowed in your dependency policy. Jackson can serialize-then-deserialize, which produces a deep copy:

```java
// Requires the class to be JSON-serializable.
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(orig);
Foo copy = mapper.readValue(json, Foo.class);
```

### Strategy 3: Immutability — kill the problem at the source

If the object can't be mutated, there's no sharing problem to worry about. This is the most modern and clean approach.

**(a) `final` fields + no setters:**

```java
public final class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }
}
```

**(b) Records (Java 14+) — language-level support for immutable data carriers:**

```java
public record Point(int x, int y) { }

// Generates: constructor, accessors (x(), y()), equals, hashCode, toString.
// All fields are implicitly final. No setters possible.
```

> **Record caveat:** records are *shallowly* immutable. If a component is a mutable type (e.g., `List<Integer>`), the record can't stop you from mutating that list. Defend by accepting and returning `List.copyOf(...)` in the canonical constructor:
> ```java
> public record Order(String id, List<Item> items) {
>     public Order {                            // compact canonical constructor
>         items = List.copyOf(items);           // defensive copy on input
>     }
> }
> ```

**(c) Immutable factory methods (Java 9+):**

```java
List<Integer> nums = List.of(1, 2, 3);        // truly immutable
Set<String> tags = Set.of("a", "b");          // truly immutable
Map<String, Integer> scores = Map.of("a", 1); // truly immutable

nums.add(4);    // throws UnsupportedOperationException at runtime
```

**(d) `Collections.unmodifiableXxx(...)` — VIEWS, not copies:**

```java
List<Integer> raw = new ArrayList<>(List.of(1, 2, 3));
List<Integer> view = Collections.unmodifiableList(raw);

view.add(4);    // throws UnsupportedOperationException ✅
raw.add(4);     // succeeds — and `view` now sees [1, 2, 3, 4]!!  ⚠️
```

> **The unmodifiable view trap:** `Collections.unmodifiableXxx` returns a *view* — it shares the underlying storage with the original. If the original is mutated, the "unmodifiable" view reflects the change. To prevent this, copy first then wrap:
> ```java
> List<Integer> safe = Collections.unmodifiableList(new ArrayList<>(orig));
> // — OR just use List.copyOf(orig) which does both in one call:
> List<Integer> safer = List.copyOf(orig);
> ```

---

## 🧭 Decision Framework — When to Copy vs Share

| Situation | Default | Why |
| --- | --- | --- |
| **DSA recursion (DP memo, DSU parent, BFS visited)** | **Share** | Shared mutable state is the whole point — propagation is desired |
| **Tight inner loops / hot paths** | Share | Defensive copy adds allocation + GC pressure |
| **Public API method accepting a collection** | Defensive copy on input | Protects the method from caller mutating the collection mid-execution |
| **Public API method returning a collection** | Defensive copy (or unmodifiable copy) on output | Protects internal state from caller mutation |
| **DTO crossing a process / network boundary** | Already gets copied via serialization — no defensive copy needed | The boundary handles it |
| **Spring singleton bean holding state** | Immutability is mandatory | Multiple threads concurrently access singletons — shared mutable state = race conditions |
| **JPA entity** | Treat as mutable + tracked by Hibernate | Hibernate's persistence context relies on mutation detection — defensive copy breaks dirty checking |
| **Multi-threaded shared state** | Immutability strongly preferred; else `volatile` / `synchronized` / `java.util.concurrent.atomic` | Pass-by-value-of-reference + threads = visibility & atomicity hazards |

### The "three boundaries" rule

Think of every method as having three boundaries:

1. **Input boundary** — should the method copy what comes in? (Yes if public API, no if internal helper.)
2. **Output boundary** — should the method copy what goes out? (Yes if returning internal state, no if returning a freshly-constructed result.)
3. **Identity boundary** — does this object need a unique identity (mutability), or is it a value (immutability)?

Internal helpers in a tight algorithm: skip all three (just share). Public-facing service methods: copy at #1 and #2, prefer immutable types at #3.

---

## 🔧 Backend Java Idioms (Walmart-relevant)

### 🌱 Spring `@RestController` — what gets copied

When a Spring controller receives a `@RequestBody`:

```java
@PostMapping("/orders")
public OrderResponse create(@RequestBody OrderRequest req) {
    // `req` is freshly deserialized from JSON — it is a brand-new object
    // owned by this request thread. NO need for defensive copy.
}
```

The deserializer (Jackson) already produces a fresh object graph per request. You can mutate it freely without affecting any caller.

### 🌱 Returning collections from a service

```java
@Service
public class OrderService {
    private final List<Order> cache = new ArrayList<>();

    // ❌ BAD — leaks internal state
    public List<Order> getAll() { return cache; }

    // ✅ GOOD — returns an immutable copy
    public List<Order> getAll() { return List.copyOf(cache); }
}
```

For Spring services that hold mutable state, always copy on the way out.

### 🗄️ JPA entity gotcha — mutation IS the persistence contract

```java
@Entity
public class Order {
    @Id private Long id;
    private String status;
    // getters / setters
}

@Service
@Transactional
public class OrderService {
    public void markShipped(Long id) {
        Order order = repo.findById(id).orElseThrow();
        order.setStatus("SHIPPED");
        // ← NO explicit save() call needed. Hibernate dirty-checks the entity
        //   at transaction commit and issues UPDATE for any mutated field.
    }
}
```

**Implication:** if you defensive-copy a JPA entity, Hibernate's dirty-checking can no longer detect changes to your copy — you've broken the persistence contract. **Don't defensive-copy JPA entities inside the same transaction.** Use them as the mutable objects they're designed to be, or convert them to immutable DTOs at the service boundary.

### 🧵 `@Cacheable` — same-reference return is a known footgun

```java
@Cacheable("users")
public User findById(Long id) {
    return repo.findById(id).orElseThrow();
}

// Caller:
User u = service.findById(42L);
u.setStatus("DELETED");         // ⚠️ pollutes the cache!
// Next call to findById(42L) returns the mutated object.
```

`@Cacheable` returns the SAME reference for repeated calls with the same key. If the caller mutates it, every future caller sees the mutation. **Fix:** return immutable DTOs from cacheable methods, never mutable entities.

---

## ⚡ Interview-Safe Phrasing

When the interviewer asks *"Is Java pass-by-value or pass-by-reference?"* — the senior answer is:

> *"Java is always pass-by-value. For object types, the value being passed is a reference — so the caller and callee end up with two variables pointing to the same heap object. Mutations to the object propagate; reassignments of the parameter do not. The classic proof is that you can't write a real swap method in Java — you'd have to mutate two slots of a shared container instead."*

That paragraph signals:
- You know the precise terminology (pass-by-value, reference, heap object)
- You can distinguish mutation from reassignment
- You know the litmus test (swap)
- You know the workaround (shared container slot mutation)

**What to NEVER say in an interview:**

| ❌ Don't say | Why |
| --- | --- |
| *"Java is pass-by-reference for objects."* | Wrong. Java is always pass-by-value. The senior interviewer will mark you down. |
| *"It depends on the type."* | Imprecise. The *mechanism* is the same for every type — only the *value* (primitive vs reference) differs. |
| *"Java passes pointers."* | Java doesn't have pointers (in the C sense). It has references — managed by the GC, with no arithmetic. |

---

## 🐞 Gotchas Hall of Fame (subtle ones worth knowing)

### Gotcha 1 — `final` parameter ≠ immutable parameter

Already covered above, but worth re-flagging because it's an interview question:

```java
public void process(final List<Integer> nums) {
    nums.add(99);            // legal — final blocks reassignment, NOT content mutation
}
```

`final` on a parameter is purely a style / safety preference for the local variable. It does NOT make the underlying object immutable.

### Gotcha 2 — `Collections.unmodifiableXxx` is a view, not a copy

Already covered. The fix is `List.copyOf(orig)`, which copies AND returns an immutable result.

### Gotcha 3 — varargs is an array (mutable)

```java
public static int sum(int... nums) {
    nums[0] = 999;           // legal — nums is an int[]
    return Arrays.stream(nums).sum();
}

int[] arr = {1, 2, 3};
sum(arr);
System.out.println(arr[0]); // 999 — mutated!
```

When you call a varargs method with an explicit array, Java passes the **same array** (no defensive copy). Inside the method, mutating `nums` mutates the caller's array.

**Fix in the callee:** if you don't want this, defensive copy at the top of the method: `nums = nums.clone();`.

### Gotcha 4 — `String.intern()` makes `==` lie (sort of)

```java
String a = "hello";
String b = "hello";
System.out.println(a == b);            // true — string pool, same reference

String c = new String("hello");
System.out.println(a == c);            // false — different heap objects
System.out.println(a == c.intern());   // true — intern() returns the pooled reference
```

Not a pass-by-value bug per se, but related: it's another case where understanding "same reference vs same value" is crucial. Always use `.equals()` for strings; `==` only when you specifically want identity.

---

## 🧩 Practice Scenarios — Test Yourself

Predict the output BEFORE running. Answers at the bottom.

**Scenario A:**

```java
public static void modify(int[] arr) {
    arr[0] = 100;
    arr = new int[]{0, 0, 0};
    arr[0] = 999;
}

int[] x = {1, 2, 3};
modify(x);
System.out.println(x[0]);
```

**Scenario B:**

```java
public static void modify(List<Integer> list) {
    list.add(100);
    list = new ArrayList<>();
    list.add(999);
}

List<Integer> x = new ArrayList<>(List.of(1, 2, 3));
modify(x);
System.out.println(x);
```

**Scenario C:**

```java
public static void modify(Integer i) {
    i = i + 100;
}

Integer x = 5;
modify(x);
System.out.println(x);
```

**Scenario D:**

```java
public record Box(int[] arr) { }

Box b = new Box(new int[]{1, 2, 3});
b.arr()[0] = 999;
System.out.println(b.arr()[0]);
```

---

### Answers

| Scenario | Output | Why |
| --- | --- | --- |
| **A** | `100` | First line mutates the shared heap array (propagates). Second line reassigns local `arr` (does NOT propagate). Third line mutates the new local array (caller never sees it). |
| **B** | `[1, 2, 3, 100]` | Same as A — first call mutates shared list; reassignment is local-only. |
| **C** | `5` | `Integer` is immutable. `i = i + 100` allocates a new boxed Integer and reassigns local `i`. Caller's `x` untouched. |
| **D** | `999` | Records are SHALLOWLY immutable — the `arr` reference field is final, but the array CONTENTS are still mutable. Lesson: records don't deeply protect mutable components. |

If you got all four right, you've internalized the model. If you missed any, re-read the corresponding case in the build-up above.

---

## 🧾 TL;DR

- **Java is always pass-by-value.** Full stop.
- For objects, the *value* that's passed is a **reference** to a heap object.
- **Mutation through the reference propagates** (both caller and callee see it).
- **Reassignment of the parameter does NOT propagate** (caller's reference unchanged).
- **Swap is impossible** in Java for two caller-side variables — the canonical proof.
- To opt out of sharing: **defensive copy** at the boundary, **deep copy** for nested structures, or use **immutable types** (records, `final` fields, `List.of`, `List.copyOf`).
- **Beware:** `Cloneable.clone()` is shallow + broken, `Collections.unmodifiableXxx` is a view not a copy, `final` parameter doesn't block content mutation, records don't deeply protect mutable components, varargs shares the caller's array.
- **Interview phrasing:** *"Java is always pass-by-value. For objects, the value is a reference, so mutations propagate but reassignments don't."*

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Created. First deep dive in the JavaBackend subdomain.** Trigger: the LC 200 attempt in `../../DSA/DeepDive/graphs-fundamentals.md` raised the question *"is `visited[][]` passed by reference?"* Covers the one-sentence rule, three cases (primitives / immutable objects / mutable objects), three litmus tests (mutate / reassign / swap), five common bugs, three opt-out strategies (defensive copy / deep copy / immutability), a decision framework, backend idioms (Spring / JPA / @Cacheable), interview phrasing, four gotchas (final param, unmodifiable views, varargs, intern), and four practice scenarios with answers. Cross-referenced from `graphs-fundamentals.md` Grid Templates section. |
