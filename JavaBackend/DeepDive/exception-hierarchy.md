# ☕ Exception Hierarchy — Deep Dive

> After this note you can draw the Throwable tree from memory, explain the design reasoning behind checked vs unchecked exceptions, write correct try-with-resources with suppressed exceptions, and explain why `@Transactional` ignores checked exceptions by default.

---

## 🎯 The Problem This Solves

Code fails. Networks drop. Files are missing. Users send garbage input. Division by zero happens. The question is not whether failures occur, but how they're communicated. Before structured exception handling, failures were signaled by return codes (`-1` means error), null returns, or global error flags. Every caller had to check the return value — and if even one caller forgot, the error was silently swallowed, propagating corrupt data downstream.

Java's exception mechanism forces failure to be visible: when something goes wrong, an exception object is created and thrown up the call stack until someone handles it. The caller can't accidentally ignore it (for checked exceptions) — the compiler refuses to compile unless the exception is caught or declared. This makes failure visible, traceable, and recoverable.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Throwable** | The root class of Java's exception hierarchy. Everything that can be thrown (`throw`) and caught (`catch`) extends Throwable. Has two direct subclasses: Error and Exception. |
| **Error** | A Throwable subclass representing JVM-level failures that applications should NOT catch or recover from. Examples: `OutOfMemoryError`, `StackOverflowError`, `NoClassDefFoundError`. |
| **Exception** | A Throwable subclass representing conditions that applications CAN recover from. Has two branches: checked exceptions (direct subclasses of Exception) and unchecked exceptions (subclasses of RuntimeException). |
| **Checked exception** | An exception that the compiler forces you to handle — either catch it or declare it with `throws`. Extends `Exception` directly (not via `RuntimeException`). Design intent: "this failure is expected in normal operation; the caller SHOULD handle it." |
| **Unchecked exception (RuntimeException)** | An exception that the compiler does NOT force you to handle. Extends `RuntimeException`. Design intent: "this is a programming error — the code is wrong, not the environment." |
| **try-with-resources** | A `try` statement that automatically calls `close()` on any `AutoCloseable` resource when the block exits — whether normally or via exception. Added in Java 7. |
| **Suppressed exception** | When both the try block AND `close()` throw exceptions, the close exception is attached to the original as a "suppressed" exception via `addSuppressed()`. Neither is lost. |
| **Exception chaining** | Wrapping a caught exception inside a new exception as its `cause`. Preserves the original stack trace while adding context. `new ServiceException("user not found", originalSqlException)`. |

---

## 🧠 Mental Model

Think of Java's exception hierarchy as a **triage system in a hospital**. `Error` is a catastrophic event — the building is on fire (OOM, stack overflow). You evacuate; you don't try to treat patients. `Exception` is a patient you CAN treat. Checked exceptions are scheduled appointments — you know they're coming (file not found, network timeout), and the system forces you to have a plan. Unchecked exceptions (RuntimeException) are coding mistakes — a patient tripping over their own shoelaces (null pointer, array out of bounds). You can't predict every trip, and the compiler doesn't force you to handle each one.

The design philosophy: if a failure is **expected and recoverable** (file might not exist, network might be down), make it checked — force the caller to handle it. If a failure is a **programming error** (null reference, invalid argument), make it unchecked — let it propagate and crash with a stack trace that points to the bug.

> If you can say "Throwable → Error (don't catch) + Exception → RuntimeException (unchecked, programming errors) + checked (expected failures, compiler enforces handling); @Transactional rolls back on unchecked only by default" without notes, you have the hierarchy.

---

## 🎨 Visual — The Exception Hierarchy

```
  java.lang.Throwable
  │
  ├── java.lang.Error                  ← JVM failures — DON'T CATCH
  │   ├── OutOfMemoryError             (heap exhausted)
  │   ├── StackOverflowError           (infinite recursion)
  │   ├── NoClassDefFoundError         (class missing at runtime)
  │   └── AssertionError               (assert statement failed)
  │
  └── java.lang.Exception              ← Application failures — CAN RECOVER
      │
      ├── RuntimeException             ← UNCHECKED — compiler doesn't enforce
      │   ├── NullPointerException     (dereferenced null)
      │   ├── IllegalArgumentException (bad method input)
      │   ├── IllegalStateException    (wrong object state for this operation)
      │   ├── IndexOutOfBoundsException (array/list index invalid)
      │   ├── ClassCastException       (wrong cast)
      │   ├── ArithmeticException      (e.g., division by zero)
      │   ├── UnsupportedOperationException (operation not allowed)
      │   └── ConcurrentModificationException (collection modified during iteration)
      │
      └── (checked exceptions)         ← CHECKED — compiler ENFORCES handling
          ├── IOException              (I/O failure)
          │   └── FileNotFoundException
          ├── SQLException             (database failure)
          ├── ParseException           (date/number parsing failure)
          ├── InterruptedException     (thread interrupted while waiting)
          └── ReflectiveOperationException (reflection failure)

  DECISION RULE:
  ┌─────────────────────────────────────────────────────────┐
  │  "Is this a programming error?"                        │
  │    YES → extend RuntimeException (unchecked)           │
  │    NO  → "Can the caller meaningfully recover?"        │
  │           YES → extend Exception (checked)             │
  │           NO  → extend RuntimeException (unchecked)    │
  └─────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Checked = compiler enforces handling (catch or declare throws).
   Unchecked = compiler does not enforce.
   Error = JVM-level, almost never catch.
   @Transactional rolls back on RuntimeException + Error ONLY.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Tier 1 — Demo: swallowing exceptions silently
public User loadUser(Long id) {
    try {
        return userRepository.findById(id).orElseThrow();
    } catch (Exception e) {
        return null;   // ← the WORST anti-pattern
        // No logging. No rethrow. The error disappears.
        // Caller gets null, doesn't know why.
        // Debugging this in production = hours of guessing.
    }
}
```

Three anti-patterns in one:
1. **Catching `Exception`** — too broad. Catches NullPointerException, ClassCastException — programming errors that should crash, not be silently hidden.
2. **Swallowing** — no logging, no rethrowing. The failure becomes invisible.
3. **Returning null** — pushes the problem to the caller, who now has a NullPointerException waiting to happen, with no stack trace pointing to the real cause.

---

### Level 2 — The real mechanism

#### 2.1 — Checked vs unchecked: the design contract

```java
// CHECKED — compiler forces handling:
public byte[] readFile(String path) throws IOException {   // must declare
    return Files.readAllBytes(Path.of(path));
}

// Caller MUST handle:
try {
    byte[] data = readFile("/config.json");
} catch (IOException e) {
    // Option 1: recover (use default config)
    // Option 2: wrap and rethrow (add context)
    throw new ConfigLoadException("Failed to load config", e);
    // Option 3: declare throws and let caller decide
}

// UNCHECKED — compiler doesn't force handling:
public User getUser(Long id) {
    if (id == null) {
        throw new IllegalArgumentException("id must not be null");
    }
    // No throws clause needed — unchecked exceptions propagate freely
    return repository.findById(id).orElseThrow(
        () -> new UserNotFoundException(id)   // custom unchecked exception
    );
}
```

**Why this split exists:**

| Aspect | Checked | Unchecked |
|---|---|---|
| **Philosophy** | The failure is expected; the caller should have a plan | The failure is a bug; the code is wrong |
| **Examples** | File not found, network timeout, parse error | Null pointer, bad argument, cast error |
| **Compile-time** | Must catch or declare `throws` | No requirement |
| **Who fixes it** | The caller (recovery logic) | The developer (fix the bug) |

#### 2.2 — try-with-resources and AutoCloseable

```java
// Tier 2 — Production: try-with-resources
// Any class implementing AutoCloseable can be used:
try (
    Connection conn = dataSource.getConnection();
    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")
) {
    stmt.setLong(1, userId);
    ResultSet rs = stmt.executeQuery();
    // Resources are closed in REVERSE declaration order:
    // stmt.close() first, then conn.close()
    // Happens automatically when the try block exits — normal or exception
}
// No finally block needed. No risk of forgetting to close.
```

> **What the JVM is actually doing:** The compiler transforms try-with-resources into a try-finally block with null checks and suppressed exception handling. The generated bytecode calls `close()` in a `finally` block, wrapping any exception from `close()` as a suppressed exception on the original. This is NOT syntactic sugar for a simple try-finally — the suppressed exception handling is genuinely new behavior that couldn't be written manually in Java 6.

#### 2.3 — Suppressed exceptions: the deep gotcha

```java
// What happens when BOTH the try block AND close() throw?

// Scenario: database query fails, AND closing the connection also fails
try (Connection conn = dataSource.getConnection()) {
    throw new SQLException("Query failed");   // original exception
    // conn.close() is called automatically → also throws: SQLException("Connection reset")
}

// WITHOUT try-with-resources (manual finally):
// The close() exception REPLACES the original → original is LOST
// This is the #1 reason try-with-resources was created

// WITH try-with-resources:
// The original exception (Query failed) propagates
// The close exception (Connection reset) is SUPPRESSED — attached, not lost
catch (SQLException e) {
    System.out.println(e.getMessage());         // "Query failed" (original)
    Throwable[] suppressed = e.getSuppressed(); // [SQLException("Connection reset")]
    // BOTH exceptions are preserved — full picture for debugging
}
```

#### 2.4 — Exception chaining: adding context without losing the cause

```java
// Tier 2 — Production: exception chaining
public User loadUser(Long id) {
    try {
        return repository.findById(id).orElseThrow();
    } catch (DataAccessException e) {
        // Wrap with business context — preserves original stack trace as cause
        throw new UserLoadException("Failed to load user id=" + id, e);
        // In the log: UserLoadException → caused by → DataAccessException → caused by → SQLException
        // Full chain visible. Every layer's context preserved.
    }
}

// The cause chain is navigable:
catch (UserLoadException e) {
    e.getMessage();          // "Failed to load user id=42"
    e.getCause();            // DataAccessException
    e.getCause().getCause(); // SQLException (root cause)
}

// ❌ NEVER do this — loses the original cause:
catch (DataAccessException e) {
    throw new UserLoadException("Failed to load user");   // cause NOT passed
    // The DataAccessException and its stack trace are GONE
}
```

#### 2.5 — `@Transactional` rollback rules

```java
// @Transactional ONLY rolls back on:
//   - RuntimeException (unchecked) ← default
//   - Error                         ← default
// It does NOT roll back on checked exceptions!

// ❌ Trap: checked exception → NO rollback
@Transactional
public void processPayment() throws PaymentException {
    debitAccount();
    // Throws PaymentException (checked) → transaction COMMITS (not rolled back!)
    // debitAccount() change is PERMANENT — even though the method "failed"
}

// ✅ Fix: explicitly declare rollback for checked exceptions
@Transactional(rollbackFor = PaymentException.class)
public void processPayment() throws PaymentException {
    debitAccount();
    // Now rolls back on PaymentException too
}

// ✅ Alternative: make your exception unchecked
public class PaymentException extends RuntimeException {
    // Now @Transactional rolls back automatically — no rollbackFor needed
}
```

Full explanation of @Transactional rollback behavior is in `../Spring/DeepDive/02-spring-core.md`.

---

### Level 3 — The subtleties

#### 3.1 — `finally` always runs (almost)

```java
// finally runs even with return:
public int demo() {
    try {
        return 1;
    } finally {
        System.out.println("finally runs");   // DOES execute before return
    }
}
// Prints "finally runs", returns 1

// ⚠️ TRAP: return in finally SWALLOWS the exception
public int trap() {
    try {
        throw new RuntimeException("error");
    } finally {
        return 42;   // SWALLOWS the RuntimeException silently — exception is lost
    }
}
// Returns 42. RuntimeException is GONE. No stack trace. No log. Nothing.
// NEVER return from finally.

// When finally does NOT run:
// - System.exit() is called
// - JVM crashes (OutOfMemoryError causing JVM death)
// - Thread is killed by Thread.stop() (deprecated and dangerous)
// - Infinite loop or deadlock in try block (finally never reached)
```

#### 3.2 — Multi-catch and exception hierarchy

```java
// Java 7+ multi-catch:
try {
    parseAndStore(data);
} catch (IOException | ParseException e) {
    // Handle both in one block — e is effectively final
    log.error("Processing failed", e);
    throw new ProcessingException("Failed to process data", e);
}

// ❌ Cannot catch a parent and child in multi-catch:
// catch (Exception | IOException e) {}
// COMPILE ERROR: IOException is already caught by Exception

// Exception hierarchy affects catch order:
try {
    riskyOperation();
} catch (FileNotFoundException e) {
    // Handle specifically — this catch must come FIRST
} catch (IOException e) {
    // Handle other I/O errors — this catch must come AFTER FileNotFoundException
}
// Reversed order → COMPILE ERROR: FileNotFoundException is unreachable
```

#### 3.3 — Creating custom exceptions

```java
// Unchecked — for programming errors or unrecoverable business failures
public class OrderNotFoundException extends RuntimeException {
    private final Long orderId;

    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}

// Checked — for expected, recoverable failures
public class InsufficientFundsException extends Exception {
    private final BigDecimal deficit;

    public InsufficientFundsException(BigDecimal deficit) {
        super("Insufficient funds: need " + deficit + " more");
        this.deficit = deficit;
    }

    public BigDecimal getDeficit() {
        return deficit;
    }
}

// RULE OF THUMB:
// In modern Java (especially Spring), prefer UNCHECKED exceptions.
// Checked exceptions force every caller to handle or propagate — creates
// "throws clause pollution" up the entire call stack.
// Spring itself uses unchecked exceptions almost exclusively
// (DataAccessException, HttpClientErrorException, etc.)
```

#### 3.4 — `@ControllerAdvice`: centralized exception handling

```java
// Tier 3 — Framework: Spring global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(OrderNotFoundException e) {
        return new ErrorResponse("ORDER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalArgumentException e) {
        return new ErrorResponse("BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception e) {
        log.error("Unexpected error", e);   // log FULL stack trace internally
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
        // NEVER expose internal details (class names, SQL, stack traces) to the client
    }
}

record ErrorResponse(String code, String message) {}
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Checked exceptions are better because they force handling" | Checked exceptions force EVERY caller in the chain to either catch or declare `throws` — creating "throws pollution." Modern Java frameworks (Spring, most libraries since 2010) overwhelmingly prefer unchecked exceptions. Checked exceptions are appropriate only when the caller genuinely can and should recover. |
| "`catch (Exception e)` handles everything" | It catches Exception and all subclasses — but NOT Error. `catch (Throwable t)` catches everything, but you should almost never catch Error (OutOfMemoryError, StackOverflowError are unrecoverable). Catching Exception is also too broad — it catches programming errors (NPE, CCE) that should crash, not be handled. |
| "`finally` always runs" | Almost always — but not if `System.exit()` is called, the JVM crashes, the thread is killed, or the try block enters an infinite loop. And returning from `finally` silently swallows any exception from the try block — never return from finally. |
| "Exceptions are expensive, avoid them" | Creating an exception object IS expensive — filling in the stack trace walks the entire call stack (O(stack depth)). But exceptions for truly exceptional conditions are fine. The anti-pattern is using exceptions for control flow (e.g., catching NumberFormatException to check if a string is numeric — use `tryParse` patterns instead). |
| "`@Transactional` rolls back on any exception" | Only on unchecked exceptions (`RuntimeException` + subclasses) and `Error` by default. Checked exceptions do NOT trigger rollback unless you add `rollbackFor = CheckedException.class`. This is the most common `@Transactional` trap. |

---

## 🐞 Production Footguns

---

> **Footgun: Catching Exception broadly**
> **Cost:** Silent bug masking
>
> In an order processing service, a `catch (Exception e)` block was added to prevent request failures from crashing the service. It inadvertently caught `NullPointerException` from a bug in the serialization code — the bug never surfaced in logs because the catch block returned a default response. The order was marked as "processed" with corrupted data. The bug was discovered weeks later during reconciliation.

```java
// ❌ The trap: catching Exception catches programming errors too
try {
    Order order = processOrder(request);
    return ResponseEntity.ok(order);
} catch (Exception e) {
    log.warn("Order processing failed", e);   // NullPointerException logged as "warning"
    return ResponseEntity.ok(defaultOrder);    // corrupted order returned as success
}

// ✅ The fix: catch specific exceptions
try {
    Order order = processOrder(request);
    return ResponseEntity.ok(order);
} catch (OrderValidationException e) {
    return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
} catch (PaymentDeclinedException e) {
    return ResponseEntity.status(402).body(new ErrorResponse(e.getMessage()));
}
// NullPointerException and other programming errors propagate up → 500 → visible in monitoring
```

---

> **Footgun: Logging AND rethrowing**
> **Cost:** Duplicate log entries (noise)
>
> A developer caught an exception, logged it, then rethrew it. The caller also caught it and logged it. The caller's caller also caught it and logged it. The same exception appeared 3 times in the logs — once per catch layer. Under load (1000 req/s × 5% error rate × 3 duplicates), the log volume tripled, disk filled up, and the logging thread became a bottleneck, slowing the application.

```java
// ❌ The trap: log AND rethrow → every layer logs the same exception
public void loadData() {
    try {
        repository.query();
    } catch (DataAccessException e) {
        log.error("Failed to load data", e);   // logged here
        throw e;                                 // AND rethrown
    }
}

// Caller also catches and logs → same exception logged twice
// Caller's caller also catches and logs → three times

// ✅ The fix: either log OR rethrow, never both
// Option A: rethrow (let the top-level handler log it once)
catch (DataAccessException e) {
    throw new ServiceException("Data load failed", e);   // wrap with context, don't log
}

// Option B: handle (log and recover — don't rethrow)
catch (DataAccessException e) {
    log.error("Data load failed, using fallback", e);
    return fallbackData;   // recovered — no rethrow
}
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `../Spring/DeepDive/02-spring-core.md` | `@Transactional` rollback rules depend directly on the exception hierarchy: RuntimeException → rollback, checked Exception → NO rollback (unless `rollbackFor` is set). This is the #1 @Transactional trap. |
| `functional-interfaces.md` | Standard functional interfaces (`Function`, `Predicate`, `Consumer`) do NOT declare checked exceptions. Lambda bodies that throw checked exceptions require either wrapping in unchecked or custom functional interfaces with `throws` clauses. |
| `java-version-evolution.md` | Java 7 added try-with-resources and multi-catch. Java 14 added helpful NullPointerException messages (tells you WHICH variable was null). Java 17 added sealed interfaces for exception hierarchies. |
| `stream-pipeline-internals.md` (planned — Note #8) | Checked exceptions inside `Stream.map()` / `filter()` lambdas are a constant pain — the stream API's functional interfaces don't declare `throws`. Common pattern: wrap checked in unchecked within the lambda, unwrap after `collect()`. |
| `completable-future.md` (planned — Note #11) | CompletableFuture's `exceptionally(Function)` handles exceptions in async chains. `join()` wraps checked exceptions in `CompletionException` (unchecked). Exception propagation in async pipelines follows different rules than synchronous try-catch. |

---

## 🎙️ Interview Deep Questions

**Q1. What is the difference between checked and unchecked exceptions? Why does the split exist?**

> Checked exceptions extend `Exception` directly (not via `RuntimeException`). The compiler forces every caller to either catch them or declare `throws`. Design intent: these represent expected, recoverable failures — file not found, network timeout. The caller SHOULD have a recovery plan. Unchecked exceptions extend `RuntimeException`. The compiler doesn't enforce handling. Design intent: these are programming errors — null dereference, bad cast, invalid argument. The fix is not a catch block, it's fixing the code. The split exists because forcing every caller to handle every possible programming error would make code unreadable — you'd need try-catch around every method call. In practice, modern Java (Spring, Kotlin, Scala) prefers unchecked exceptions for everything, and the checked exception model is considered a Java-specific design experiment that other languages chose not to adopt.

**Q2. What happens when both the try block and `close()` throw in try-with-resources?**

> The original exception (from the try block) propagates. The close exception is attached to it as a suppressed exception via `addSuppressed()`. Neither is lost. You access suppressed exceptions via `e.getSuppressed()`. Before try-with-resources (Java 6), the close exception in a manual `finally` block would REPLACE the original — the original was silently lost. This was the primary motivation for creating try-with-resources: it preserves both exceptions. In practice, this matters when a database query fails AND the connection close also fails (e.g., network is down) — you need to see both failures to diagnose the root cause.

**Q3. Why does `@Transactional` not roll back on checked exceptions by default?**

> Spring follows the EJB convention: checked exceptions represent expected business outcomes (e.g., "insufficient funds"), not system failures. The assumption is that a business outcome — even a negative one — is a valid transaction result that should be committed. Only unexpected failures (RuntimeException, Error) indicate that the transaction is in an invalid state and should be rolled back. In practice, this is the most common `@Transactional` trap — developers throw a checked exception expecting rollback and get a commit instead. The fix: either use `@Transactional(rollbackFor = MyCheckedException.class)` or make your exceptions unchecked (extend `RuntimeException`), which is what Spring itself does for all its exceptions.

**Q4. Why should you never return from a `finally` block?**

> A `return` statement in `finally` silently swallows any exception thrown in the try block. The exception is created, the stack trace is populated, but it's never propagated — the `finally` return takes precedence. There's no log, no error, no indication that an exception occurred. This makes bugs invisible. The same applies to `break` and `continue` in finally blocks — they suppress the exception. Never use flow-control statements (`return`, `break`, `continue`) in `finally`. Use `finally` only for cleanup (closing resources, releasing locks).

**Q5. When should you create a custom exception vs using a standard one like `IllegalArgumentException`?**

> Use standard exceptions (`IllegalArgumentException`, `IllegalStateException`, `UnsupportedOperationException`) when the exception carries no additional information beyond the message. A custom exception is worth creating when you need to attach domain-specific data (e.g., `OrderNotFoundException` with an `orderId` field that monitoring tools can index), when you want callers to catch your exception specifically (not all `IllegalArgumentException`s), or when you're building a library API and want a stable exception hierarchy that callers can depend on. In Spring web APIs, custom exceptions map cleanly to `@ExceptionHandler` methods in `@ControllerAdvice` — each custom exception can map to a specific HTTP status code and error response format.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Java's exception hierarchy: Throwable splits into Error (JVM-level, don't catch) and Exception. Exception splits into RuntimeException (unchecked — programming errors) and checked exceptions (expected failures — compiler enforces handling).
>
> **Part 2 — How/Why (30s):** Checked exceptions force callers to handle expected failures like I/O errors and network timeouts — the compiler won't compile without a catch or throws declaration. Unchecked exceptions represent bugs — NullPointerException, IllegalArgumentException — that should propagate and crash with a stack trace pointing to the code that needs fixing. try-with-resources (Java 7) automatically closes AutoCloseable resources and preserves both the original exception and any close() exception as suppressed — before this, close() exceptions silently replaced the original. `@Transactional` only rolls back on unchecked + Error by default — checked exceptions commit.
>
> **Part 3 — Gotcha (20s):** The top trap is catching `Exception` too broadly — it hides programming errors (NPE, ClassCastException) that should crash visibly. Catch specific exception types. Second trap: logging AND rethrowing — creates duplicate log entries at every catch layer. Either log and recover, or wrap and rethrow — never both.

---

## 🧾 TL;DR

- `Throwable` → `Error` (don't catch) + `Exception` → `RuntimeException` (unchecked) + checked.
- Checked: compiler enforces catch/throws. For expected, recoverable failures (IOException, SQLException).
- Unchecked: no compiler enforcement. For programming errors (NPE, IllegalArgumentException).
- `@Transactional`: rolls back on RuntimeException + Error ONLY. Checked exceptions → commit (trap!).
- try-with-resources: auto-closes `AutoCloseable`. Suppressed exceptions preserve both failures.
- Never return from `finally` — silently swallows the exception.
- Never catch Exception broadly — hides programming errors. Catch specific types.
- Either log OR rethrow — never both. Prevents duplicate log spam.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #7 (Phase 1) of the JavaBackend KB completion roadmap. Covers: full Throwable hierarchy with design reasoning, checked vs unchecked philosophy, try-with-resources + suppressed exceptions, exception chaining (preserving cause), @Transactional rollback rules, finally semantics (return-in-finally trap), multi-catch (Java 7), custom exception patterns, @ControllerAdvice centralized handling. Two production footguns: broad Exception catching masking bugs, log-and-rethrow duplication. |
