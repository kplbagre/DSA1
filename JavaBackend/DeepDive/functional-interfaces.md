# ☕ Functional Interfaces — Deep Dive

> After this note you can name the 4 core functional interface families (Predicate, Function, Consumer, Supplier), explain why `@FunctionalInterface` exists, and trace how a lambda expression becomes an object at the JVM level.

---

## 🎯 The Problem This Solves

Before Java 8, if you wanted to pass behavior — not data — to a method, you created an anonymous inner class. To sort a list by length, you wrote 5 lines of boilerplate for 1 line of logic. To register a button click handler, you created an entire class for a single method. The ceremony drowned the intent.

```java
// Pre-Java-8: anonymous inner class for a comparator
Collections.sort(names, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return Integer.compare(a.length(), b.length());   // this is the ONLY line that matters
    }
});
```

Five lines of syntax for one line of meaning. Multiply by every callback, filter, comparator, and event handler in a codebase, and the signal-to-noise ratio collapses. Functional interfaces + lambdas solved this: an interface with exactly one abstract method becomes the target type for a lambda expression, collapsing the 5 lines to 1.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Functional interface** | An interface with exactly ONE abstract method. It may have any number of default methods and static methods, but only one abstract. This single-method constraint is what allows a lambda to "fit" the interface. |
| **`@FunctionalInterface`** | An annotation that tells the compiler to enforce the single-abstract-method rule. If someone adds a second abstract method, the compiler errors. Optional but strongly recommended — it's documentation + safety. |
| **Lambda expression** | A concise syntax for creating an instance of a functional interface. `(params) -> body`. It's not a method — it's an expression that evaluates to an object implementing the functional interface. |
| **Method reference** | A shorthand for a lambda that calls an existing method. `String::toUpperCase` instead of `s -> s.toUpperCase()`. Four forms: static (`Math::abs`), instance on a particular object (`myList::add`), instance on an arbitrary object of a type (`String::length`), constructor (`ArrayList::new`). |
| **Target type** | The functional interface that a lambda is being assigned to. The compiler infers the lambda's parameter types and return type from the target type. `Predicate<String> p = s -> s.isEmpty();` — the target type `Predicate<String>` tells the compiler `s` is a `String`. |
| **Effectively final** | A variable that is never reassigned after initialization, even if the `final` keyword is not written. Lambdas can capture effectively-final variables from the enclosing scope — but not mutable ones. |
| **Closure** | A function that captures variables from its enclosing scope. Java lambdas are closures over effectively-final variables — they capture the VALUE of the variable, not a reference to the variable itself. |

---

## 🧠 Mental Model

A functional interface is a **shape** — a single-method contract that says "I accept these inputs and produce this output." A lambda is a **piece of behavior that fits the shape**. The compiler matches the lambda to the shape by checking: does the lambda's parameter list and return type match the interface's single abstract method? If yes, the lambda becomes an instance of that interface.

There are really only 4 shapes in all of Java's functional programming:
- **Test something** → `Predicate<T>` — takes T, returns boolean
- **Transform something** → `Function<T,R>` — takes T, returns R
- **Produce something** → `Supplier<T>` — takes nothing, returns T
- **Consume something** → `Consumer<T>` — takes T, returns nothing

Every other functional interface in the JDK (`UnaryOperator`, `BiFunction`, `BiConsumer`, etc.) is a specialization of these 4 shapes. Learn the 4, and you can derive the rest.

> If you can say "a functional interface has exactly one abstract method; Predicate tests, Function transforms, Consumer consumes, Supplier produces; a lambda is an expression that creates an instance of the matching functional interface" without notes, you have functional interfaces.

---

## 🎨 Visual — The 4 Families

```
  THE 4 CORE SHAPES:

  ┌──────────────────────────────────────────────────────────┐
  │  Predicate<T>          T → boolean                       │
  │  test(T t)             "Does this thing pass a test?"    │
  │  Used in: filter(), removeIf(), anyMatch()               │
  ├──────────────────────────────────────────────────────────┤
  │  Function<T, R>        T → R                             │
  │  apply(T t)            "Transform this thing"            │
  │  Used in: map(), computeIfAbsent(), thenApply()          │
  ├──────────────────────────────────────────────────────────┤
  │  Consumer<T>           T → void                          │
  │  accept(T t)           "Do something with this thing"    │
  │  Used in: forEach(), peek(), ifPresent()                 │
  ├──────────────────────────────────────────────────────────┤
  │  Supplier<T>           () → T                            │
  │  get()                 "Give me a thing"                 │
  │  Used in: orElseGet(), lazy initialization, factory      │
  └──────────────────────────────────────────────────────────┘

  SPECIALIZATIONS (derived from the 4 core shapes):

  UnaryOperator<T>    = Function<T, T>     (input and output same type)
  BinaryOperator<T>   = BiFunction<T,T,T>  (two inputs, same type output)
  BiFunction<T,U,R>   = Function with 2 inputs
  BiConsumer<T,U>     = Consumer with 2 inputs
  BiPredicate<T,U>    = Predicate with 2 inputs

  PRIMITIVE SPECIALIZATIONS (avoid boxing):
  IntPredicate, LongPredicate, DoublePredicate
  IntFunction<R>, IntToLongFunction, IntToDoubleFunction
  IntConsumer, IntSupplier, IntUnaryOperator, IntBinaryOperator
  (same pattern for Long and Double)

KEY INVARIANT:
   Every functional interface in java.util.function is a specialization
   of one of the 4 core shapes. If you know the 4 shapes, you can
   derive the parameter and return type of any specialization.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

Before Java 8, passing behavior meant creating a full interface + anonymous inner class:

```java
// Tier 1 — Demo: pre-Java 8 — anonymous inner class for every behavior
// Filtering a list of strings to keep only non-empty ones
List<String> names = Arrays.asList("Alice", "", "Bob", "", "Charlie");
List<String> nonEmpty = new ArrayList<>();

for (String name : names) {
    // The filter logic is buried inside the loop — not reusable
    if (!name.isEmpty()) {
        nonEmpty.add(name);
    }
}

// If you wanted a reusable filter, you'd create an interface + implementation:
interface StringFilter {
    boolean accept(String s);
}

StringFilter nonEmptyFilter = new StringFilter() {
    @Override
    public boolean accept(String s) {
        return !s.isEmpty();
    }
};

// 7 lines to express "keep non-empty strings"
// And StringFilter only works for String — you'd need IntFilter, OrderFilter, etc.
```

Three problems:
1. **Verbose** — the ceremony (class, override, method signature) drowns the logic.
2. **Not composable** — you can't chain `filter AND sort AND transform` without nesting or utility classes.
3. **Not generic** — each type needs its own filter interface. No reuse.

---

### Level 2 — The real mechanism

#### 2.1 — Functional interfaces solve the reuse problem

Java 8 defined a small set of generic functional interfaces in `java.util.function`:

```java
// Tier 1 — Demo: the 4 core interfaces in action
import java.util.function.*;

// Predicate: T → boolean
Predicate<String> isNonEmpty = s -> !s.isEmpty();
isNonEmpty.test("");        // false
isNonEmpty.test("hello");   // true

// Function: T → R
Function<String, Integer> toLength = s -> s.length();
toLength.apply("hello");    // 5

// Consumer: T → void (side effect)
Consumer<String> printer = s -> System.out.println(s);
printer.accept("hello");    // prints "hello"

// Supplier: () → T (lazy value)
Supplier<List<String>> listFactory = () -> new ArrayList<>();
List<String> freshList = listFactory.get();   // new ArrayList created on demand
// ⚠️ NOT thread-safe — Supplier can be called from multiple threads;
//    the returned list is not synchronized
```

#### 2.2 — Lambdas: the syntax that makes functional interfaces usable

A lambda is an expression that creates an instance of a functional interface. The compiler infers which interface from the target type:

```java
// Tier 1 — Demo: lambda syntax forms
// Full form: (parameters) -> { body with return }
Function<String, Integer> f1 = (String s) -> { return s.length(); };

// Inferred parameter type:
Function<String, Integer> f2 = (s) -> { return s.length(); };

// Single parameter — parentheses optional:
Function<String, Integer> f3 = s -> { return s.length(); };

// Single expression — braces and return optional:
Function<String, Integer> f4 = s -> s.length();

// No parameters:
Supplier<Double> random = () -> Math.random();

// Multiple parameters:
BiFunction<String, String, String> concat = (a, b) -> a + b;
```

**Before Java 8 vs after:**

```java
// Before Java 8 (anonymous inner class):
Collections.sort(names, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return Integer.compare(a.length(), b.length());
    }
});

// Java 8+ (lambda):
names.sort((a, b) -> Integer.compare(a.length(), b.length()));

// Java 8+ (method reference — even shorter):
names.sort(Comparator.comparingInt(String::length));
```

#### 2.3 — Method references: 4 forms

```java
// Form 1 — Static method reference: ClassName::staticMethod
Function<String, Integer> parse = Integer::parseInt;
// Equivalent lambda: s -> Integer.parseInt(s)

// Form 2 — Instance method on a specific object: object::instanceMethod
String prefix = "Mr. ";
Function<String, String> addPrefix = prefix::concat;
// Equivalent lambda: s -> prefix.concat(s)

// Form 3 — Instance method on an arbitrary object of a type: ClassName::instanceMethod
Function<String, String> toUpper = String::toUpperCase;
// Equivalent lambda: s -> s.toUpperCase()
// The first parameter becomes the receiver: String::toUpperCase on the String argument

// Form 4 — Constructor reference: ClassName::new
Supplier<ArrayList<String>> listMaker = ArrayList::new;
// Equivalent lambda: () -> new ArrayList<>()
Function<Integer, ArrayList<String>> sizedList = ArrayList::new;
// Equivalent lambda: size -> new ArrayList<>(size)
// Which constructor is chosen depends on the target type's parameter count
```

#### 2.4 — Composition: chaining functional interfaces

The real power is composition — building complex behavior from simple pieces:

```java
// Tier 2 — Production: composing predicates
Predicate<String> isNonEmpty = s -> !s.isEmpty();
Predicate<String> isShort = s -> s.length() < 5;

// AND — both conditions must pass
Predicate<String> isShortAndNonEmpty = isNonEmpty.and(isShort);

// OR — either condition passes
Predicate<String> isEmptyOrShort = isNonEmpty.negate().or(isShort);

// Used in stream:
List<String> result = names.stream()
    .filter(isShortAndNonEmpty)
    .toList();

// Composing functions:
Function<String, String> trim = String::trim;
Function<String, String> toUpper = String::toUpperCase;

// andThen: apply first, then apply second to the result
Function<String, String> cleanAndUpper = trim.andThen(toUpper);
cleanAndUpper.apply("  hello  ");   // "HELLO"

// compose: apply second first, then apply first (reversed order)
Function<String, String> upperThenTrim = trim.compose(toUpper);
// Same result for this example, but order matters when types differ
```

#### 2.5 — How lambdas work at the JVM level

> **What the JVM is actually doing:** Lambdas are NOT anonymous inner classes. The compiler generates an `invokedynamic` instruction that, on first call, asks the `LambdaMetafactory` to generate a lightweight implementation class at runtime. This class implements the functional interface and delegates to a private synthetic method in the enclosing class (where the lambda body lives). Subsequent calls reuse the same generated class — no object allocation if the lambda captures nothing (stateless lambdas are cached as singletons). This is more efficient than anonymous inner classes: no `.class` file per lambda, no constructor call per usage, and stateless lambdas are allocated zero times after the first call.

```java
// What you write:
Predicate<String> p = s -> s.isEmpty();

// What the compiler generates (simplified):
// 1. A private static method in your class:
private static boolean lambda$0(String s) {
    return s.isEmpty();
}

// 2. An invokedynamic instruction that creates the Predicate:
// invokedynamic #bootstrap(LambdaMetafactory.metafactory,
//     MethodType(Predicate),
//     MethodHandle(lambda$0))

// 3. At runtime, LambdaMetafactory generates a class like:
// final class YourClass$$Lambda$1 implements Predicate<String> {
//     public boolean test(Object s) {
//         return YourClass.lambda$0((String) s);
//     }
// }

// Stateless lambda → singleton instance. Zero allocation after first call.
// Capturing lambda → new instance per call (captures are constructor args).
```

**Anonymous inner class vs lambda — the concrete differences:**

| Aspect | Anonymous inner class | Lambda |
|---|---|---|
| `.class` file generated | Yes — one per anonymous class | No — uses invokedynamic |
| `this` reference | Refers to the anonymous class | Refers to the ENCLOSING class |
| Object allocation | New object every time | Stateless: singleton (zero alloc). Capturing: one alloc. |
| Performance | Slower (classloading + allocation) | Faster (no classloading, potential singleton caching) |

---

### Level 3 — The subtleties

#### 3.1 — Variable capture and effectively final

Lambdas can capture variables from the enclosing scope, but only if the variable is **effectively final** (never reassigned after initialization):

```java
// ✅ Effectively final — works
String prefix = "Hello, ";
Consumer<String> greet = name -> System.out.println(prefix + name);

// ❌ Not effectively final — COMPILE ERROR
String greeting = "Hi";
greeting = "Hello";   // reassignment
// Consumer<String> greet = name -> System.out.println(greeting + name);
// COMPILE ERROR: Variable used in lambda expression should be effectively final
```

**Why this restriction exists:** Java lambdas capture the VALUE of the variable, not a reference to the variable itself. If the variable could change after capture, the lambda and the enclosing scope would see different values — confusing and error-prone. In languages where lambdas capture by reference (like JavaScript), this is a major source of bugs (the classic for-loop closure problem). Java prevents it at compile time.

**Workaround for mutable state:** use a one-element array or `AtomicReference`:

```java
// Tier 1 — Demo: mutable state in lambda (use with caution)
AtomicInteger counter = new AtomicInteger(0);
List.of("a", "b", "c").forEach(s -> counter.incrementAndGet());
// AtomicInteger is effectively final (the REFERENCE doesn't change),
// but its VALUE is mutable via atomic operations.
// ✅ Thread-safe — AtomicInteger uses CAS internally
```

#### 3.2 — `@FunctionalInterface` — why it matters

```java
// Without @FunctionalInterface:
interface Converter {
    String convert(String input);
    // Someone later adds:
    // String format(String input);   // now it has 2 abstract methods — no longer usable as lambda target
    // No compiler error — the interface silently stops being functional
}

// With @FunctionalInterface:
@FunctionalInterface
interface Converter {
    String convert(String input);
    // String format(String input);   // COMPILE ERROR:
    // "Unexpected @FunctionalInterface annotation; Converter is not a functional interface"
}
```

The annotation is optional — any interface with one abstract method IS a functional interface regardless. But `@FunctionalInterface` communicates intent and prevents accidental breakage.

**Important:** default methods and static methods don't count against the one-abstract-method limit:

```java
@FunctionalInterface
interface Converter {
    String convert(String input);                    // abstract (the ONE)

    default String convertAndTrim(String input) {    // default — doesn't count
        return convert(input).trim();
    }

    static Converter identity() {                    // static — doesn't count
        return input -> input;
    }
}
// Still a valid functional interface — exactly one abstract method
```

#### 3.3 — Primitive specializations: avoiding boxing

Generic functional interfaces like `Function<Integer, Integer>` require boxing — wrapping `int` → `Integer` for every call. For hot paths, this creates millions of short-lived `Integer` objects per second, triggering frequent minor GCs.

```java
// ❌ Boxing overhead: Function<Integer, Integer> boxes every int
Function<Integer, Integer> doubler = n -> n * 2;
doubler.apply(5);   // autobox 5 → Integer(5), unbox result → int

// ✅ Primitive specialization: no boxing
IntUnaryOperator doublerPrimitive = n -> n * 2;
doublerPrimitive.applyAsInt(5);   // pure int → int, no boxing

// The full set:
// IntPredicate:       int → boolean
// IntFunction<R>:     int → R
// IntConsumer:        int → void
// IntSupplier:        () → int
// IntUnaryOperator:   int → int
// IntBinaryOperator:  (int, int) → int
// Same pattern for Long and Double
```

#### 3.4 — `this` in lambdas vs anonymous classes

```java
// Tier 1 — Demo: 'this' behaves differently
public class Outer {
    private String name = "outer";

    public void demo() {
        // Anonymous inner class: 'this' refers to the anonymous class
        Runnable anon = new Runnable() {
            @Override
            public void run() {
                // System.out.println(this.name);   // COMPILE ERROR — 'this' is the Runnable, not Outer
                System.out.println(Outer.this.name);   // must qualify
            }
        };

        // Lambda: 'this' refers to the ENCLOSING class (Outer)
        Runnable lambda = () -> {
            System.out.println(this.name);   // "outer" — 'this' is Outer, not the lambda
        };
    }
}
```

This is a concrete behavioral difference, not just syntax sugar. It means lambdas have direct access to the enclosing object's fields and methods via `this` — no qualification needed.

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "A lambda is an anonymous inner class" | Lambdas use `invokedynamic` + `LambdaMetafactory`, not inner classes. No `.class` file per lambda. Stateless lambdas are cached as singletons (zero allocation after first call). `this` in a lambda refers to the enclosing class, not the lambda itself. |
| "You need `@FunctionalInterface` for lambdas to work" | Any interface with exactly one abstract method works as a lambda target — with or without the annotation. `@FunctionalInterface` is documentation + compile-time protection against adding a second abstract method. |
| "Lambdas can modify local variables" | Lambdas can only capture effectively-final variables — variables that are never reassigned. This is because lambdas capture the VALUE, not a reference to the variable. Use `AtomicReference` or a one-element array for mutable state. |
| "`Function` and `UnaryOperator` are different things" | `UnaryOperator<T>` extends `Function<T, T>` — it's just a `Function` where input and output are the same type. Similarly, `BinaryOperator<T>` extends `BiFunction<T, T, T>`. They exist for readability, not functionality. |
| "Primitive specializations are just optimization" | They're a necessity for hot paths. A `Function<Integer, Integer>` called in a tight loop boxes and unboxes every value — creating millions of short-lived `Integer` objects that trigger GC pauses. `IntUnaryOperator` operates on raw `int` with zero boxing. In a high-throughput service, this is the difference between microsecond and millisecond latency. |

---

## 🐞 Production Footguns

---

> **Footgun: Checked exceptions in lambdas**
> **Cost:** Compilation failure / ugly workarounds
>
> The core functional interfaces (`Function`, `Predicate`, `Consumer`, `Supplier`) do NOT declare checked exceptions. If the lambda body throws a checked exception, the code won't compile — forcing developers into verbose try-catch blocks inside the lambda, wrapping checked exceptions in unchecked ones, or creating custom functional interfaces.

```java
// ❌ The trap: checked exception in a lambda
// Function<String, byte[]> encoder = s -> s.getBytes("UTF-8");
// COMPILE ERROR: Unhandled exception: UnsupportedEncodingException
// Function.apply() doesn't declare throws

// ✅ Fix 1: try-catch inside the lambda (verbose but clear)
Function<String, byte[]> encoder = s -> {
    try {
        return s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
        throw new UncheckedIOException(e);   // wrap in unchecked
    }
};

// ✅ Fix 2: use the API that doesn't throw checked (preferred when available)
Function<String, byte[]> encoder2 = s -> s.getBytes(StandardCharsets.UTF_8);
// StandardCharsets.UTF_8 is a Charset object, not a String name
// getBytes(Charset) doesn't throw checked exceptions

// ✅ Fix 3: custom functional interface with throws
@FunctionalInterface
interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
}
```

---

> **Footgun: Side effects in Supplier (lazy != safe)**
> **Cost:** Race condition
>
> A developer used `Supplier<Connection>` for lazy database connection initialization in a Spring singleton. The `Supplier.get()` was called from multiple request threads simultaneously. Each call created a new connection — but the developer assumed "Supplier is lazy, so it's called once." `Supplier` has no caching or synchronization — it's called every time `get()` is invoked.

```java
// ❌ The trap: Supplier called multiple times without caching
@Service
public class DataService {
    // Called on every get() — NOT cached
    private final Supplier<Connection> connectionSupplier = () -> {
        return DriverManager.getConnection(url, user, pass);
    };

    public Data loadData() {
        Connection conn = connectionSupplier.get();   // NEW connection every call
        // At 1000 req/s → 1000 connections/s → connection pool exhausted → crash
    }
}

// ✅ The fix: memoize the supplier (cache the result)
private final Supplier<Connection> connectionSupplier = memoize(() -> {
    return DriverManager.getConnection(url, user, pass);
});

private static <T> Supplier<T> memoize(Supplier<T> delegate) {
    AtomicReference<T> cache = new AtomicReference<>();
    return () -> {
        T value = cache.get();
        if (value == null) {
            value = delegate.get();
            cache.set(value);
        }
        return value;
    };
}
// ⚠️ This simple memoize is NOT perfectly thread-safe (double init possible).
// For production: use Guava's Suppliers.memoize() or a proper lazy singleton pattern.
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `generics-type-erasure.md` | Functional interfaces are generic: `Function<T,R>`, `Predicate<T>`, `Supplier<T>`. The type parameters are erased at runtime. Understanding erasure explains why `Function<String, Integer>` is just `Function<Object, Object>` in bytecode, and why primitive specializations (`IntFunction`) exist — to avoid boxing through erased generic types. |
| `stream-pipeline-internals.md` (planned — Note #8) | The Stream API is built entirely on functional interfaces: `filter(Predicate)`, `map(Function)`, `forEach(Consumer)`, `collect(Supplier, BiConsumer, BiConsumer)`. Streams are the primary consumer of functional interfaces in production Java code. |
| `completable-future.md` (planned — Note #11) | `CompletableFuture.supplyAsync(Supplier)`, `thenApply(Function)`, `thenAccept(Consumer)`, `exceptionally(Function)` — the entire CompletableFuture API is parameterized by functional interfaces. |
| `java-version-evolution.md` (planned — Note #5) | Functional interfaces were added in Java 8 alongside lambdas, method references, and the Stream API — the biggest single-release paradigm shift in Java's history. |
| `../Spring/DeepDive/02-spring-core.md` | Spring uses functional interfaces extensively: `@Bean` methods are Suppliers, `ApplicationRunner` is a Consumer, `WebMvcConfigurer` default methods leverage functional interface composition. Spring Boot 3's functional bean registration uses `Supplier<T>` directly. |

---

## 🎙️ Interview Deep Questions

**Q1. What are the 4 core functional interfaces in `java.util.function`? How do the other interfaces relate to them?**

> The 4 core shapes are: `Predicate<T>` (takes T, returns boolean — used for testing/filtering), `Function<T,R>` (takes T, returns R — used for transformation), `Consumer<T>` (takes T, returns void — used for side effects), and `Supplier<T>` (takes nothing, returns T — used for lazy creation). Every other interface in `java.util.function` is a specialization: `UnaryOperator<T>` extends `Function<T,T>` (same input/output type), `BinaryOperator<T>` extends `BiFunction<T,T,T>`, `BiFunction<T,U,R>` is Function with two inputs, and the `Int/Long/Double` prefixed variants avoid autoboxing. If you know the 4 shapes, you can derive the method name and signature of any specialization.

**Q2. How does a lambda expression work at the JVM level? Is it the same as an anonymous inner class?**

> No. Anonymous inner classes generate a separate `.class` file, create a new object on every instantiation, and bind `this` to the anonymous class itself. Lambdas use `invokedynamic` — the compiler generates a private static or instance method containing the lambda body, and emits an `invokedynamic` instruction that, on first execution, asks `LambdaMetafactory` to generate a lightweight implementation class at runtime. Subsequent calls reuse the same generated class. For stateless lambdas (no captured variables), the runtime caches a singleton instance — zero allocation after the first call. For capturing lambdas, a new object is created per call, with captured values as constructor arguments. This means `this` in a lambda refers to the enclosing class, not the lambda — because the lambda IS a method on the enclosing class, not a separate class.

**Q3. Why can't lambdas capture mutable local variables? What's the design reasoning?**

> Java lambdas capture the VALUE of local variables, not a reference to the variable. If a local `int count = 0` could be incremented inside a lambda, the lambda and the enclosing method would operate on independent copies — the increment in the lambda wouldn't be visible outside it, and vice versa. This would be confusing and bug-prone. Rather than adding true variable capture by reference (which would require allocating local variables on the heap instead of the stack — a significant JVM change with GC implications), Java chose to restrict lambdas to effectively-final variables. You can work around this with `AtomicInteger` or `AtomicReference` — the reference itself is effectively final, but the object it points to is mutable via atomic operations.

**Q4. When should you use primitive specializations like `IntPredicate` instead of `Predicate<Integer>`?**

> Whenever the functional interface is called in a hot loop or high-throughput path. `Predicate<Integer>` autoboxes every `int` to `Integer` (heap allocation) on input and unboxes `Integer` to `int` on output. At 1 million calls per second, that's 1 million `Integer` objects created per second — each ~16 bytes, totaling ~16 MB/s of GC pressure. `IntPredicate` operates directly on raw `int` with zero boxing. The Stream API provides `IntStream`, `LongStream`, `DoubleStream` specifically to avoid boxing in pipelines. Use the object-typed versions (`Predicate<Integer>`) only when the value is already boxed or the call frequency is low.

**Q5. What is `@FunctionalInterface` and when would you NOT put it on an interface?**

> `@FunctionalInterface` is a compile-time annotation that enforces the single-abstract-method rule. If you add a second abstract method, the compiler errors — preventing accidental breakage of lambda compatibility. You should put it on any interface you intend to use as a lambda target. You would NOT put it on interfaces that happen to have one abstract method but aren't designed for lambda use — for example, `AutoCloseable` has one method (`close()`) but it's designed for try-with-resources, not lambda expressions. Adding `@FunctionalInterface` to it would be misleading about its purpose. Also, some interfaces evolve over time — if you expect to add abstract methods later, don't mark it `@FunctionalInterface`.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** A functional interface has exactly one abstract method, making it a target type for lambda expressions. The 4 core shapes: Predicate (test), Function (transform), Consumer (side-effect), Supplier (produce).
>
> **Part 2 — How/Why (30s):** Before Java 8, passing behavior required anonymous inner classes — 5 lines of boilerplate for 1 line of logic. Lambdas collapse this to a single expression. At the JVM level, lambdas use `invokedynamic` and `LambdaMetafactory` instead of generating inner classes — stateless lambdas are cached as singletons with zero allocation after the first call. The composition methods (`and`, `or`, `negate`, `andThen`, `compose`) let you build complex behavior from simple pieces without custom classes.
>
> **Part 3 — Gotcha (20s):** The top two traps: checked exceptions don't work in standard functional interfaces (the `apply()` / `test()` methods don't declare `throws`), forcing ugly try-catch blocks inside lambdas — use APIs that avoid checked exceptions or write custom `ThrowingFunction` interfaces. And `Supplier` has no caching — `get()` is called every time, so using it for "lazy initialization" without memoization creates a new object on every call, not once.

---

## 🧾 TL;DR

- 4 core shapes: `Predicate<T>` (test), `Function<T,R>` (transform), `Consumer<T>` (consume), `Supplier<T>` (produce).
- Everything else in `java.util.function` is a specialization of these 4.
- Lambda = expression that creates an instance of a functional interface. NOT an anonymous inner class.
- JVM: `invokedynamic` + `LambdaMetafactory`. Stateless lambdas = singleton (zero alloc). Capturing = one alloc.
- `this` in a lambda = enclosing class. `this` in anonymous inner class = the inner class.
- Lambdas capture values, not variable references — only effectively-final variables.
- Primitive specializations (`IntPredicate`, `IntFunction`) avoid autoboxing. Use on hot paths.
- `@FunctionalInterface` is optional but recommended — compile-time guard + documentation.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #4 (Phase 1) of the JavaBackend KB completion roadmap. Covers: 4 core functional interface families with method names and usage locations, all specializations mapped to core shapes, lambda syntax forms, method reference 4 forms, composition (and/or/negate/andThen/compose), JVM-level lambda implementation (invokedynamic + LambdaMetafactory vs anonymous inner class), variable capture and effectively-final restriction, `this` binding difference, primitive specializations for boxing avoidance, `@FunctionalInterface` enforcement. Two production footguns: checked exceptions in lambdas, uncached Supplier. |
