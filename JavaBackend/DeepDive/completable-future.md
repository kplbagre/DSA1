# ☕ CompletableFuture — Deep Dive

> After this note you can chain async operations with `thenApply → thenCompose → thenCombine`, explain why `thenApplyAsync` differs from `thenApply`, handle exceptions in async pipelines, and know when to use a custom executor vs the common pool.

---

## 🎯 The Problem This Solves

You need to call 3 microservices to build a response: user service (200ms), order service (300ms), recommendation service (150ms). Sequentially: 650ms. In parallel: ~300ms (the slowest). Java's `Future` (Java 5) supported async execution but had no way to chain, compose, or combine results without blocking with `get()`. Every composition required a blocking call, tying up a thread.

`CompletableFuture` (Java 8) is a `Future` that is also a completion stage — it supports non-blocking chaining (`thenApply`), async composition (`thenCompose`), parallel combination (`thenCombine`), and exception handling (`exceptionally`), all without blocking a thread.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **CompletableFuture\<T\>** | A `Future` that can be explicitly completed, supports chaining of async stages, and runs callbacks when results are available. Think of it as a Promise (JavaScript) for Java. |
| **`supplyAsync(Supplier)`** | Starts an async computation that returns a value. Runs on `ForkJoinPool.commonPool()` by default. |
| **`runAsync(Runnable)`** | Starts an async computation with no return value. Returns `CompletableFuture<Void>`. |
| **`thenApply(Function)`** | Synchronous transformation of the result — like `Stream.map()`. Runs on the same thread that completed the previous stage. |
| **`thenApplyAsync(Function)`** | Async transformation — runs on a pool thread, not the completing thread. |
| **`thenCompose(Function)`** | Async chaining — like `Stream.flatMap()`. The function returns a `CompletableFuture`, and the result is flattened (no nesting). |
| **`thenCombine(other, BiFunction)`** | Combines two independent CompletableFutures when BOTH complete. The BiFunction receives both results. |
| **`exceptionally(Function)`** | Recovery handler — called when the pipeline fails. Returns a fallback value. |
| **`join()`** | Blocking get — waits for completion and returns the result. Throws `CompletionException` (unchecked) on failure. Preferred over `get()` because it doesn't throw checked `ExecutionException`. |

---

## 🧠 Mental Model

Think of `CompletableFuture` as a **conveyor belt for async results**. Each stage is a station on the belt. When a result arrives at a station, the station processes it and sends the output to the next station. `thenApply` is a transformation station (sync). `thenCompose` is a station that calls another factory that has its own conveyor belt (async chain). `thenCombine` merges two conveyor belts into one. `exceptionally` is the quality-control station — it catches defective items and replaces them with defaults.

The belt runs without blocking any thread — stages fire automatically when their input is ready. You only block when you call `join()` to collect the final result.

> If you can say "`supplyAsync` starts async work; `thenApply` = sync map; `thenCompose` = async flatMap; `thenCombine` = parallel merge; `exceptionally` = recovery; default pool is `ForkJoinPool.commonPool()`; pass a custom executor for blocking I/O" without notes, you have CompletableFuture.

---

## 🎨 Visual — Chaining vs Combining

```
  CHAINING (sequential async stages):

  supplyAsync(fetchUser)         
       │ CompletableFuture<User>
       ▼
  thenApply(User::name)          ← sync transform (same thread)
       │ CompletableFuture<String>
       ▼
  thenCompose(name → fetchOrders(name))  ← async chain (new async call)
       │ CompletableFuture<List<Order>>
       ▼
  exceptionally(ex → emptyList())  ← recovery on failure
       │ CompletableFuture<List<Order>>
       ▼
  join()  ← blocking collect (only at the end)


  COMBINING (parallel independent tasks):

  supplyAsync(fetchUser)     supplyAsync(fetchOrders)
       │                          │
       │  CF<User>                │  CF<List<Order>>
       └──────────┬───────────────┘
                  ▼
           thenCombine((user, orders) → buildResponse(user, orders))
                  │
                  │  CF<Response>
                  ▼
               join()

  Both fetch calls run in PARALLEL. thenCombine fires
  only when BOTH complete. Total time ≈ max(fetchUser, fetchOrders).

KEY INVARIANT:
   thenApply = sync map (same thread).
   thenCompose = async flatMap (new async call, flattened).
   thenCombine = parallel merge (both must complete).
   No thread blocked until join().
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// Java 5 Future: can submit async work, but can't chain without blocking
ExecutorService pool = Executors.newFixedThreadPool(4);

Future<User> userFuture = pool.submit(() -> userService.fetch(userId));
User user = userFuture.get();   // BLOCKS the calling thread until done

Future<List<Order>> ordersFuture = pool.submit(() -> orderService.fetch(user.id()));
List<Order> orders = ordersFuture.get();   // BLOCKS again — sequential, not parallel
// Total: fetch time A + fetch time B (sequential blocking)
// Two threads tied up doing nothing but waiting

// ❌ Cannot: chain A → B without blocking between them
// ❌ Cannot: compose results without blocking
// ❌ Cannot: handle exceptions inline
```

---

### Level 2 — The real mechanism

#### 2.1 — Starting async work

```java
// supplyAsync: returns a value
CompletableFuture<User> userCf = CompletableFuture.supplyAsync(
    () -> userService.fetch(userId)
);
// Runs on ForkJoinPool.commonPool() by default
// Returns immediately — does NOT block

// runAsync: no return value (fire-and-forget side effect)
CompletableFuture<Void> logCf = CompletableFuture.runAsync(
    () -> auditLog.record("user lookup", userId)
);

// With custom executor (ALWAYS use for blocking I/O):
ExecutorService ioPool = Executors.newFixedThreadPool(20);
CompletableFuture<User> userCf2 = CompletableFuture.supplyAsync(
    () -> userService.fetch(userId),
    ioPool   // ← dedicated pool for I/O, not the shared commonPool

);
```

#### 2.2 — Chaining: `thenApply` vs `thenCompose`

```java
// thenApply: sync transform (like Stream.map)
// Function<T, R> — transforms the result, returns R directly
CompletableFuture<String> nameCf = userCf
    .thenApply(User::name);
// User → String. Runs on the thread that completed userCf.

// thenCompose: async chain (like Stream.flatMap)
// Function<T, CompletableFuture<R>> — returns another CF, result is flattened
CompletableFuture<List<Order>> ordersCf = userCf
    .thenCompose(user -> CompletableFuture.supplyAsync(
        () -> orderService.fetchOrders(user.id()), ioPool
    ));
// User → CF<List<Order>> → flattened to CF<List<Order>>

// ❌ If you used thenApply here instead:
// CompletableFuture<CompletableFuture<List<Order>>> nested = userCf
//     .thenApply(user -> CompletableFuture.supplyAsync(...));
// Nested! thenCompose flattens this — same as flatMap in streams/Optional
```

**The rule:** if your transformation returns a plain value → `thenApply`. If it returns a `CompletableFuture` → `thenCompose`.

#### 2.3 — `thenApply` vs `thenApplyAsync`

```java
// thenApply: runs on the SAME thread that completed the previous stage
CompletableFuture<String> name = userCf.thenApply(User::name);
// If userCf completed on ForkJoinPool thread 3, thenApply runs on thread 3 too.
// Fast for cheap transforms — no thread switch.

// thenApplyAsync: runs on a POOL thread (commonPool or custom)
CompletableFuture<String> name2 = userCf.thenApplyAsync(User::name);
// Runs on a different ForkJoinPool thread — even if the previous stage is done.
// Use when the transform is expensive and you don't want to block the completing thread.

// thenApplyAsync with custom executor:
CompletableFuture<String> name3 = userCf.thenApplyAsync(User::name, ioPool);
```

#### 2.4 — Combining independent futures

```java
// thenCombine: merge two independent CFs
CompletableFuture<User> userCf = supplyAsync(() -> userService.fetch(userId), ioPool);
CompletableFuture<List<Order>> ordersCf = supplyAsync(() -> orderService.fetch(userId), ioPool);

// Both run in PARALLEL. thenCombine fires when BOTH complete:
CompletableFuture<Response> responseCf = userCf.thenCombine(
    ordersCf,
    (user, orders) -> new Response(user.name(), orders)
);
// Total time ≈ max(userTime, ordersTime), not userTime + ordersTime

// allOf: wait for ALL to complete (returns CF<Void>)
CompletableFuture<Void> all = CompletableFuture.allOf(cf1, cf2, cf3);
all.join();   // blocks until all 3 complete
// Then extract results: cf1.join(), cf2.join(), cf3.join()

// anyOf: returns first to complete
CompletableFuture<Object> first = CompletableFuture.anyOf(cf1, cf2, cf3);
Object result = first.join();   // result of whichever finished first
```

#### 2.5 — Exception handling

```java
// exceptionally: recover from failure (like catch)
CompletableFuture<User> userCf = supplyAsync(() -> userService.fetch(userId))
    .exceptionally(ex -> {
        log.error("User fetch failed", ex);
        return User.anonymous();   // fallback value
    });

// handle: always runs (success OR failure) — like finally
CompletableFuture<String> result = supplyAsync(() -> riskyCall())
    .handle((value, ex) -> {
        if (ex != null) {
            return "fallback";   // failure path
        }
        return value.toUpperCase();   // success path
    });

// whenComplete: observe result without modifying it (side effect — logging)
CompletableFuture<User> userCf2 = supplyAsync(() -> userService.fetch(userId))
    .whenComplete((user, ex) -> {
        if (ex != null) {
            log.error("Failed", ex);
        } else {
            log.info("Fetched user: {}", user.name());
        }
    });
// The original result/exception propagates unchanged — whenComplete is a side channel
```

**Exception propagation:** if an exception occurs at any stage and no `exceptionally` or `handle` catches it, it propagates through the chain. `join()` wraps it in `CompletionException` (unchecked). `get()` wraps it in `ExecutionException` (checked). Prefer `join()` — no need for try-catch of checked exception.

---

### Level 3 — The subtleties

#### 3.1 — The default executor: ForkJoinPool.commonPool()

```java
// supplyAsync() without executor → ForkJoinPool.commonPool()
// This pool is SHARED across the entire JVM:
// - parallel streams use it
// - other CompletableFuture calls use it
// - parallelism = Runtime.getRuntime().availableProcessors() - 1

// If you block I/O on commonPool threads (DB calls, HTTP calls):
// → all shared-pool threads are blocked
// → parallel streams in other parts of the app starve
// → application-wide latency spike
```

**Rule:** ALWAYS pass a custom executor for I/O-bound work:

```java
// Tier 2 — Production: dedicated executor for I/O
ExecutorService ioExecutor = Executors.newFixedThreadPool(20);
// Or Java 21:
ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

CompletableFuture<Data> data = CompletableFuture.supplyAsync(
    () -> db.query("SELECT ..."),
    ioExecutor   // ← never block the commonPool
);
```

#### 3.2 — `join()` vs `get()`

```java
// get(): throws checked InterruptedException + ExecutionException
// Must wrap in try-catch — verbose
try {
    User user = userCf.get();
} catch (InterruptedException | ExecutionException e) {
    throw new RuntimeException(e);
}

// join(): throws unchecked CompletionException
// No try-catch required — cleaner code
User user = userCf.join();   // CompletionException if failed

// get(timeout): with timeout — prevents indefinite blocking
User user = userCf.get(5, TimeUnit.SECONDS);   // TimeoutException if not done in 5s

// Prefer join() in application code. Use get(timeout) when you need a deadline.
```

#### 3.3 — Composing a list of futures

```java
// Pattern: fan out N tasks, collect all results
List<Long> userIds = List.of(1L, 2L, 3L, 4L, 5L);

// Step 1: create a CF for each task
List<CompletableFuture<User>> futures = userIds.stream()
    .map(id -> CompletableFuture.supplyAsync(
        () -> userService.fetch(id), ioExecutor
    ))
    .toList();

// Step 2: combine all CFs into one CF<Void> that completes when ALL are done
CompletableFuture<Void> allDone = CompletableFuture.allOf(
    futures.toArray(CompletableFuture[]::new)
);

// Step 3: when all done, extract results
List<User> users = allDone
    .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)   // safe — all are already complete
        .toList()
    )
    .join();
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "`thenApply` is async" | `thenApply` runs on the same thread that completed the previous stage — it's synchronous relative to the pipeline. Use `thenApplyAsync` for true async (different thread). |
| "`thenApply` and `thenCompose` are interchangeable" | `thenApply` takes `Function<T, R>` — returns a value. `thenCompose` takes `Function<T, CompletableFuture<R>>` — returns a future, which is flattened. Using `thenApply` when the function returns a CF gives `CF<CF<R>>` — nested, not flattened. Same as `map` vs `flatMap` in streams. |
| "CompletableFuture handles threading automatically" | It uses `ForkJoinPool.commonPool()` by default. This pool is shared and small (~CPU count). Blocking I/O on it starves the entire JVM. YOU must choose the right executor for your workload. |
| "`exceptionally` replaces try-catch" | `exceptionally` handles exceptions from upstream stages and provides a fallback value. But it can't rethrow a different exception cleanly (the return type must match T). For rethrow, use `handle()`. |

---

## 🐞 Production Footguns

---

> **Footgun: Blocking I/O on commonPool**
> **Cost:** Application-wide latency spike
>
> A microservice used `CompletableFuture.supplyAsync(() -> dbQuery())` without a custom executor. The database call blocked for 500ms per query. With 7 commonPool threads and 100 concurrent requests, only 7 queries ran at a time — 93 requests queued. Parallel streams in the same JVM for CSV export also starved — export latency went from 2s to 30s.

```java
// ❌ The trap: blocking I/O on commonPool
CompletableFuture.supplyAsync(() -> db.query("SELECT ..."));   // blocks commonPool thread

// ✅ The fix: dedicated executor
ExecutorService dbPool = Executors.newFixedThreadPool(20);
CompletableFuture.supplyAsync(() -> db.query("SELECT ..."), dbPool);
```

---

> **Footgun: Lost exception (no exceptionally/handle)**
> **Cost:** Silent failure
>
> A notification service used `CompletableFuture.runAsync(() -> sendEmail(user))` for fire-and-forget email sending. The email service threw an exception. Nobody called `join()` or attached `exceptionally()`. The exception was silently swallowed — no log, no retry, no alert. Users stopped receiving emails for 3 days before anyone noticed.

```java
// ❌ The trap: fire-and-forget without error handling
CompletableFuture.runAsync(() -> emailService.send(user));
// Exception thrown inside → silently swallowed. No log. No alert.

// ✅ The fix: always attach error handling
CompletableFuture.runAsync(() -> emailService.send(user))
    .exceptionally(ex -> {
        log.error("Email send failed for user {}", user.id(), ex);
        alertService.notify("email-failure", ex);
        return null;
    });
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `functional-interfaces.md` | `supplyAsync(Supplier)`, `thenApply(Function)`, `thenAccept(Consumer)`, `handle(BiFunction)` — CompletableFuture's API is parameterized by the 4 core functional interfaces. |
| `stream-pipeline-internals.md` | `thenApply` ≈ `map`, `thenCompose` ≈ `flatMap`. Both Stream and CompletableFuture share `ForkJoinPool.commonPool()` — blocking I/O in either starves the other. |
| `java-version-evolution.md` | CompletableFuture was added in Java 8. Java 9 added `completeOnTimeout`, `orTimeout`, `copy()`, `minimalCompletionStage()`. Java 21's virtual threads offer an alternative for I/O-bound concurrency. |
| `thread-pool-executor.md` (planned — Note #19) | CompletableFuture's default pool is `ForkJoinPool.commonPool()`. Understanding pool sizing, bounded queues, and rejection policies explains why custom executors are critical for production CompletableFuture usage. |

---

## 🎙️ Interview Deep Questions

**Q1. What is the difference between `thenApply` and `thenCompose`?**

> `thenApply` takes a `Function<T, R>` and returns `CompletableFuture<R>` — it's a synchronous transformation of the result, like `Stream.map()`. `thenCompose` takes a `Function<T, CompletableFuture<R>>` and returns `CompletableFuture<R>` — it flattens the nested future, like `Stream.flatMap()`. If your transformation calls another async service (returning a CompletableFuture), use `thenCompose` to avoid `CompletableFuture<CompletableFuture<R>>` nesting. `thenApply` for `T → R`. `thenCompose` for `T → CF<R>`.

**Q2. What is `ForkJoinPool.commonPool()` and why should you care?**

> It's the default executor for `CompletableFuture.supplyAsync()` and `parallel streams`. Its parallelism is `availableProcessors() - 1` (typically 7 on an 8-core machine). It's shared across the ENTIRE JVM — every library, every framework, every part of your code that uses parallel streams or CompletableFuture without a custom executor shares these 7 threads. If you block any of them on I/O (database, HTTP, file reads), those threads are unavailable for everything else — parallel streams stall, other async pipelines queue up. Always pass a custom executor for I/O-bound work. Reserve the common pool for CPU-bound computation only.

**Q3. How do you handle exceptions in a CompletableFuture chain?**

> Three mechanisms: `exceptionally(Function<Throwable, T>)` provides a fallback value on failure — like a catch block that returns a default. `handle(BiFunction<T, Throwable, R>)` always runs (success or failure) and can transform the result or recover — like a combined try-catch-finally. `whenComplete(BiConsumer<T, Throwable>)` observes the result without modifying it — for logging or metrics. If no handler is attached and the future fails, calling `join()` throws `CompletionException` (unchecked), and `get()` throws `ExecutionException` (checked). Critically, if nobody calls `join()`/`get()` and no handler is attached, the exception is silently swallowed — this is the "fire-and-forget" trap.

**Q4. How do you run N async tasks in parallel and collect all results?**

> Create a `List<CompletableFuture<T>>` — one future per task. Combine them with `CompletableFuture.allOf(futures.toArray(CF[]::new))`, which returns a `CF<Void>` that completes when ALL futures complete. Then use `thenApply` to extract results: `allDone.thenApply(v -> futures.stream().map(CF::join).toList())`. The `join()` calls are safe inside `thenApply` because `allOf` guarantees all futures are already complete. The total time is `max(individual times)`, not the sum — true parallelism. Always use a custom executor with enough threads to match the parallelism you need.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** CompletableFuture is a chainable async primitive. It supports non-blocking composition: `thenApply` (sync map), `thenCompose` (async flatMap), `thenCombine` (parallel merge), `exceptionally` (recovery).
>
> **Part 2 — How/Why (30s):** `supplyAsync` starts async work on `ForkJoinPool.commonPool()` by default. Stages chain without blocking — each fires when its input is ready. `thenApply` transforms on the completing thread (sync). `thenCompose` chains to another async call and flattens the result (avoids CF-in-CF nesting). `thenCombine` merges two independent CFs when both complete — total time is max of the two, not the sum. `allOf` waits for N futures in parallel. Only `join()` at the end blocks.
>
> **Part 3 — Gotcha (20s):** Two traps: default `commonPool` is shared and small — blocking I/O starves the entire JVM; always pass a custom executor for I/O. And fire-and-forget without `exceptionally` silently swallows exceptions — no log, no alert, no retry. Always attach error handling to async pipelines.

---

## 🧾 TL;DR

- `supplyAsync(Supplier)` returns value async. `runAsync(Runnable)` fire-and-forget.
- `thenApply` = sync map (same thread). `thenCompose` = async flatMap (flattens nested CF).
- `thenCombine` = merge two parallel CFs. `allOf` = wait for N CFs.
- `exceptionally` = recovery (fallback value). `handle` = always runs (success or failure).
- Default pool: `ForkJoinPool.commonPool()` — shared, small (~CPU count). NEVER block I/O on it.
- `join()` (unchecked) preferred over `get()` (checked exception). Use `get(timeout)` for deadlines.
- Fire-and-forget without `exceptionally` = silent exception swallowing.
- Java 21 virtual threads are an alternative for I/O-bound parallelism — simpler than CF + executor.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #11 (Phase 2) of the JavaBackend KB completion roadmap. Covers: supplyAsync/runAsync, thenApply vs thenCompose vs thenCombine (map vs flatMap vs merge), thenApply vs thenApplyAsync (thread affinity), exception handling (exceptionally/handle/whenComplete), join vs get, allOf/anyOf fan-out pattern, ForkJoinPool.commonPool sharing risk, custom executor for I/O. Two production footguns: blocking I/O on commonPool, silent exception in fire-and-forget. |
