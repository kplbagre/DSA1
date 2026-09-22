# ⚙️ JVM Memory Areas — Deep Dive

> After this note you can draw the JVM memory layout (heap + stack + Metaspace + code cache), explain what lives in Eden vs Old Gen, what goes in Metaspace vs heap, and diagnose OOM errors by their suffix message.

---

## 🎯 The Problem This Solves

Your Spring Boot service crashes with `OutOfMemoryError: Java heap space`. Another crashes with `OutOfMemoryError: Metaspace`. A third crashes with `StackOverflowError`. These are different failures in different memory regions with different causes and different fixes. Understanding the JVM's memory layout tells you WHERE to look and WHAT to fix.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Heap** | Shared memory where ALL objects live. Divided into Young Generation (Eden + Survivor) and Old Generation. Managed by the garbage collector. Sized with `-Xms` (initial) and `-Xmx` (max). |
| **Young Generation** | The area where new objects are allocated. Subdivided into Eden (initial allocation) and two Survivor spaces (S0, S1). Collected by Minor GC — fast and frequent. |
| **Old Generation (Tenured)** | Objects that survived multiple Minor GCs are promoted here. Collected by Major GC or Full GC — slower, less frequent. Long-lived objects (caches, connection pools, singletons) end up here. |
| **Metaspace** | Native (off-heap) memory storing class metadata: Class objects, method descriptors, constant pools, annotations. Replaced PermGen in Java 8. Grows dynamically (no fixed limit by default, but can be capped with `-XX:MaxMetaspaceSize`). |
| **Thread stack** | Per-thread memory holding method call frames (local variables, operand stack, return address). Default size: ~1 MB per thread (`-Xss` to configure). `StackOverflowError` when recursion is too deep. |
| **Code Cache** | Memory for JIT-compiled native code. When the JIT compiler converts hot bytecode methods to machine code, the result is stored here. Sized with `-XX:ReservedCodeCacheSize`. |
| **Direct memory (off-heap)** | Memory allocated outside the heap via `ByteBuffer.allocateDirect()` or `Unsafe`. Used for NIO buffers. Not managed by GC — must be explicitly freed or relies on `Cleaner`. Sized with `-XX:MaxDirectMemorySize`. |

---

## 🧠 Mental Model

The JVM memory is a **building with distinct floors**:
- **Ground floor (Heap)** — the main workspace. All objects live here. Split into a fast-turnover area (Young Gen — like a reception desk, most visitors leave quickly) and a long-term area (Old Gen — like permanent offices).
- **Basement (Metaspace)** — the building's blueprint archive. Stores class definitions. In native memory, not the main workspace. Grows as you load more classes.
- **Each office (Thread Stack)** — private per-employee workspace. Each method call adds a paper tray (frame) to the stack. Too many trays → the stack collapses (`StackOverflowError`).
- **The machine room (Code Cache)** — where the JIT compiler stores optimized machine code.

> If you can say "Heap: objects (Young = Eden + Survivor, Old = long-lived); Metaspace: class metadata (native memory, replaces PermGen); Stack: per-thread method frames (~1 MB each); OOM heap space = heap full, OOM Metaspace = too many classes loaded, StackOverflow = recursion too deep" without notes, you have JVM memory.

---

## 🎨 Visual — JVM Memory Layout

```
  ┌─────────────────────────────────────────────────────────────┐
  │                        JVM PROCESS                          │
  │                                                             │
  │  ┌─────────────────────────────────────────────────────┐    │
  │  │                    HEAP (-Xms, -Xmx)                │    │
  │  │                                                     │    │
  │  │  ┌──────────────────────────────────────────────┐   │    │
  │  │  │         YOUNG GENERATION                     │   │    │
  │  │  │  ┌────────┐  ┌──────┐  ┌──────┐             │   │    │
  │  │  │  │  Eden  │  │  S0  │  │  S1  │             │   │    │
  │  │  │  │(new obj│  │(from)│  │ (to) │             │   │    │
  │  │  │  │ alloc) │  │      │  │      │             │   │    │
  │  │  │  └────────┘  └──────┘  └──────┘             │   │    │
  │  │  └──────────────────────────────────────────────┘   │    │
  │  │                                                     │    │
  │  │  ┌──────────────────────────────────────────────┐   │    │
  │  │  │         OLD GENERATION (Tenured)             │   │    │
  │  │  │   (objects promoted from Young after N GCs)  │   │    │
  │  │  │   Caches, connection pools, singletons       │   │    │
  │  │  └──────────────────────────────────────────────┘   │    │
  │  └─────────────────────────────────────────────────────┘    │
  │                                                             │
  │  ┌────────────────────┐  ┌───────────────────────────────┐  │
  │  │  METASPACE          │  │  THREAD STACKS               │  │
  │  │  (native memory)    │  │  Thread-1: [frame][frame]... │  │
  │  │  Class objects       │  │  Thread-2: [frame][frame]... │  │
  │  │  Method metadata     │  │  Thread-3: [frame][frame]... │  │
  │  │  Constant pools      │  │  (~1 MB each, -Xss)         │  │
  │  │  (-XX:MaxMetaspace)  │  │                              │  │
  │  └────────────────────┘  └───────────────────────────────┘  │
  │                                                             │
  │  ┌────────────────────┐  ┌───────────────────────────────┐  │
  │  │  CODE CACHE         │  │  DIRECT MEMORY (off-heap)    │  │
  │  │  JIT-compiled native│  │  NIO ByteBuffers             │  │
  │  │  code               │  │  Netty buffers               │  │
  │  │  (-XX:ReservedCode  │  │  (-XX:MaxDirectMemorySize)   │  │
  │  │   CacheSize)        │  │                              │  │
  │  └────────────────────┘  └───────────────────────────────┘  │
  └─────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Heap = GC-managed objects. Metaspace = class metadata (native).
   Stack = per-thread frames. All three can OOM independently
   with DIFFERENT error messages.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// "Just increase -Xmx" — the most common non-fix
// OOM: Java heap space → developer doubles -Xmx from 2G to 4G
// Service runs for 2x longer before OOM again → doubles to 8G
// Treats symptom, not cause. The real cause: a memory leak.

// The OOM message tells you WHERE to look:
// "Java heap space"     → objects filling the heap (leak or undersized)
// "Metaspace"           → too many classes loaded (ClassLoader leak, code generation)
// "GC overhead limit"   → GC running >98% of time, recovering <2% heap (nearly full)
// "unable to create native thread" → OS thread limit hit (too many threads)
// StackOverflowError    → recursion too deep (or method chain too long)
```

---

### Level 2 — The real mechanism

#### 2.1 — Heap: Young Gen + Old Gen

**Object lifecycle through the heap:**

```
  1. new Object()  → allocated in EDEN
                     (pointer bump allocation — extremely fast: O(1))

  2. Eden fills up  → MINOR GC triggered
     - Mark live objects in Eden + S0 (from survivor)
     - Copy survivors to S1 (to survivor) — increment age counter
     - Dead objects in Eden are NOT individually freed —
       the entire Eden space is cleared (pointer reset to start)
     - S0 and S1 swap roles for next GC cycle

  3. Object survives N minor GCs (age reaches threshold, default N=15)
     → PROMOTED to OLD GENERATION

  4. Old Gen fills up → MAJOR GC or FULL GC
     - Mark-sweep-compact the Old Gen
     - Much more expensive — scans more objects, longer pause
```

**Concrete default sizes (JDK 21, server JVM):**

```
  Heap: -Xms = 1/64 of physical RAM. -Xmx = 1/4 of physical RAM.
  Young Gen: ~1/3 of total heap (configurable with -XX:NewRatio)
  Eden:Survivor ratio: 8:1:1 by default (-XX:SurvivorRatio=8)
    For a 4 GB heap: Young = ~1.3 GB, Eden = ~1 GB, each Survivor = ~150 MB
    Old Gen = ~2.7 GB

  Promotion threshold: 15 Minor GCs by default (-XX:MaxTenuringThreshold=15)
  G1: divides heap into regions (1-32 MB each), not contiguous Young/Old areas
```

> **What the JVM is actually doing:** Eden allocation uses **TLAB (Thread-Local Allocation Buffer)** — each thread has its own small buffer within Eden. Allocation = bump a pointer. No synchronization. When a TLAB is full, the thread requests a new one (requires CAS). This makes object allocation in Java almost as fast as stack allocation in C — a pointer increment, not a malloc() call.

#### 2.2 — Metaspace

```java
// Metaspace stores:
// - Class objects (name, superclass, interfaces, fields, methods)
// - Method bytecode and metadata
// - Constant pool (string literals, class references)
// - Annotations
// - Field descriptors and method dispatch tables (vtable/itable)

// Metaspace is NATIVE memory (not heap, not GC-managed in the traditional sense)
// It grows dynamically — no fixed limit by default
// Cap it: -XX:MaxMetaspaceSize=256m

// When Metaspace fills up:
// OutOfMemoryError: Metaspace

// Common causes:
// 1. ClassLoader leak in app server (old classes never GC'd)
// 2. Excessive code generation (Spring proxies, Hibernate proxies, Mockito, groovy scripts)
// 3. Loading thousands of classes (large dependency trees)

// Monitoring:
// jcmd <pid> VM.metaspace   → shows metaspace usage
// JMX: java.lang:type=MemoryPool,name=Metaspace
```

#### 2.3 — Thread Stack

```java
// Each thread gets its own stack — default ~1 MB (-Xss to configure)
// Each method call pushes a FRAME:
// ┌─────────────────────────┐
// │  Stack Frame             │
// │  - Local variables       │  ← primitives stored directly; objects as references
// │  - Operand stack         │  ← working area for bytecode instructions
// │  - Frame data            │  ← return address, exception table pointer
// └─────────────────────────┘

// StackOverflowError = stack is full — too many nested method calls
// Common cause: infinite/deep recursion

public int factorial(int n) {
    return n * factorial(n - 1);   // no base case → infinite recursion → StackOverflow
}
// Each call adds a frame (~50-200 bytes). At 1 MB stack with 200 bytes/frame:
// ~5000 recursive calls before StackOverflow.
// -Xss2m doubles the limit — but that doubles memory per thread.
// 1000 threads × 2 MB = 2 GB just for stacks.
```

#### 2.4 — OOM Error Diagnosis Table

| Error message | Memory area | Common cause | Fix |
|---|---|---|---|
| `Java heap space` | Heap | Memory leak, undersized heap, large cache | Heap dump analysis (jmap), find leak, increase -Xmx |
| `GC overhead limit exceeded` | Heap | GC running >98% of time, recovering <2% | Same as heap space — heap is effectively full |
| `Metaspace` | Metaspace (native) | ClassLoader leak, excessive code generation | Cap with -XX:MaxMetaspaceSize, fix leak |
| `unable to create native thread` | OS | Too many threads (OS limit ~10K-30K) | Reduce thread count, use virtual threads, increase ulimit |
| `Direct buffer memory` | Off-heap | NIO ByteBuffer.allocateDirect() leak | Cap with -XX:MaxDirectMemorySize, fix leak |
| `Requested array size exceeds VM limit` | Heap | Trying to allocate array > ~2.1B elements | Bug in size calculation |
| `StackOverflowError` | Thread stack | Deep/infinite recursion | Fix recursion, increase -Xss (last resort) |

---

### Level 3 — The subtleties

#### 3.1 — Escape analysis and stack allocation

```java
// Normally, all objects are heap-allocated. But the JIT compiler can
// determine if an object NEVER ESCAPES the current method (escape analysis).
// If it doesn't escape: the object can be stack-allocated (or scalar-replaced).

public int sumPoints() {
    Point p = new Point(1, 2);   // JIT detects: p never escapes this method
    return p.x + p.y;            // JIT replaces with: return 1 + 2; (scalar replacement)
    // No heap allocation at all. No GC pressure. Effectively free.
}

// Objects that DO escape (returned, stored in a field, passed to another method)
// cannot be stack-allocated — they MUST go on the heap.

// This is why short-lived local objects in hot loops are often free in practice —
// the JIT eliminates them entirely. Don't manually "optimize" by reusing objects
// unless profiling shows GC pressure. The JIT is better at it than you.
```

#### 3.2 — String pool location

```java
// The string pool (interned strings) lives in the HEAP — not Metaspace.
// Since Java 7, interned strings are regular heap objects — GC-eligible when unreferenced.
// Before Java 7: string pool was in PermGen (fixed size → overflow risk).
// String literals from the constant pool are automatically interned.

// Monitoring: -XX:+PrintStringTableStatistics (prints pool stats on JVM shutdown)
```

#### 3.3 — G1 regions vs traditional generational layout

```java
// Traditional (Serial/Parallel GC):
// Contiguous Young Gen + contiguous Old Gen. Fixed boundary.

// G1 GC (default since Java 9):
// Heap divided into equal-size REGIONS (1-32 MB each).
// Each region is dynamically assigned a role: Eden, Survivor, Old, or Humongous.
// Humongous: objects > 50% of region size. Allocated across multiple contiguous regions.
// G1 collects the regions with the most garbage first ("Garbage-First").
// No fixed Young/Old boundary — regions are reassigned as needed.
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Objects are allocated on the stack in Java" | Almost never. Objects go on the heap. Only primitives and references are on the stack. The JIT can stack-allocate or scalar-replace objects via escape analysis, but this is an optimization, not the default. |
| "Metaspace is part of the heap" | Metaspace is native (off-heap) memory. `-Xmx` does not affect Metaspace. It has its own limit (`-XX:MaxMetaspaceSize`). Heap and Metaspace can OOM independently. |
| "StackOverflowError means the heap is full" | Stack and heap are separate. StackOverflow = thread stack is full (too many nested calls). Heap OOM = too many objects. Different memory areas, different causes, different fixes. |
| "Full GC collects everything" | Full GC collects Young Gen + Old Gen + Metaspace. But it can't fix a memory leak — it collects unreachable objects. If all objects are reachable (leak), Full GC reclaims nothing and the next allocation triggers another Full GC → GC thrashing. |
| "`-Xmx` should be as large as possible" | Larger heap = longer GC pauses (more objects to scan). Set -Xmx to what the app needs + 20-30% headroom. For latency-sensitive services, smaller heap + G1/ZGC is better than huge heap + long pauses. |

---

## 🐞 Production Footguns

---

> **Footgun: Heap dump not configured before OOM**
> **Cost:** Debugging blind — no data from the crash
>
> A production service crashed with `OOM: Java heap space`. No heap dump was taken. The team couldn't reproduce the issue in staging. Without a dump, they couldn't identify the leaking object. The fix was a guess (increase -Xmx). The service crashed again 2 days later.

```java
// ❌ The trap: no heap dump on OOM
// Service crashes → no data → blind debugging

// ✅ The fix: always configure heap dump on OOM (BEFORE the crash)
// JVM flags:
// -XX:+HeapDumpOnOutOfMemoryError
// -XX:HeapDumpPath=/var/dumps/heap.hprof
// When OOM occurs → JVM writes a heap dump → analyze with Eclipse MAT or VisualVM
// Also: -XX:OnOutOfMemoryError="kill -9 %p" → kill the process cleanly after dump
```

---

> **Footgun: Thread count → native thread OOM**
> **Cost:** `OutOfMemoryError: unable to create native thread`
>
> A service spawned a new thread per incoming WebSocket connection. At 15,000 connections: 15,000 threads × 1 MB = 15 GB just for stacks. The OS hit its thread limit (`ulimit -u`). `new Thread()` threw `OutOfMemoryError: unable to create native thread`. The fix: virtual threads (Java 21) or a thread pool with a bounded size.

```java
// ❌ The trap: unbounded thread creation
for (WebSocket ws : connections) {
    new Thread(() -> handleWebSocket(ws)).start();
}
// 15,000 threads × 1 MB stack = 15 GB + OS thread limit

// ✅ The fix: virtual threads or bounded pool
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (WebSocket ws : connections) {
        executor.submit(() -> handleWebSocket(ws));
    }
}
// 15,000 virtual threads ~ a few MB total + ~8 carrier OS threads
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `class-loading.md` | Class metadata loaded by ClassLoaders is stored in Metaspace. ClassLoader leak → classes not GC'd from Metaspace → OOM: Metaspace. |
| `gc-deep-dive.md` (planned — Note #25) | GC operates on the heap regions defined here: Minor GC collects Young Gen, Major GC collects Old Gen. Understanding heap layout is prerequisite for understanding GC behavior. |
| `jit-compilation.md` (planned — Note #26) | JIT-compiled native code is stored in Code Cache. Code Cache exhaustion → JIT stops compiling → performance degradation (falls back to interpreter). |
| `string-internals.md` | String pool lives in the heap (since Java 7). Interned strings are regular heap objects — GC-eligible when unreferenced. |
| `hashmap-internals.md` | Each HashMap Node is a heap object (~32 bytes). A HashMap with 1M entries = 1M Node objects + the array. Understanding heap allocation explains HashMap's memory overhead vs array-based structures. |

---

## 🎙️ Interview Deep Questions

**Q1. Draw the JVM memory layout and explain what lives in each area.**

> The JVM has 5 main memory areas. **Heap** (shared, GC-managed): all objects. Divided into Young Gen (Eden + 2 Survivor spaces) and Old Gen. New objects allocate in Eden; survivors promote to Old after ~15 Minor GCs. **Metaspace** (native memory, replaces PermGen since Java 8): class metadata, method bytecode, constant pools. Grows dynamically, capped with `-XX:MaxMetaspaceSize`. **Thread Stack** (per-thread, ~1 MB): method call frames with local variables and operand stack. **Code Cache**: JIT-compiled native code. **Direct Memory**: off-heap NIO buffers. Each area can fail independently with different OOM messages.

**Q2. What is the difference between `OOM: Java heap space` and `OOM: Metaspace`?**

> `Java heap space`: the heap (where objects live) is full. Common causes: memory leak (objects referenced but never used — usually in caches, maps, or listeners), undersized heap, or a burst of large allocations. Fix: take a heap dump (`-XX:+HeapDumpOnOutOfMemoryError`), analyze with Eclipse MAT, find the dominator tree showing the largest retained objects. `Metaspace`: class metadata area (native memory) is full. Common causes: ClassLoader leak in app servers (classes from old deployments not GC'd), excessive runtime code generation (too many CGLIB proxies, groovy scripts), or loading thousands of unique classes. Fix: cap with `-XX:MaxMetaspaceSize`, fix the ClassLoader leak, reduce dynamic class generation.

**Q3. What is TLAB and why does it make Java object allocation fast?**

> TLAB (Thread-Local Allocation Buffer) is a per-thread chunk of Eden space. Each thread allocates objects by bumping a pointer within its TLAB — no synchronization needed (the buffer is thread-local). When the TLAB is full, the thread requests a new one via CAS (one CAS per TLAB, not per object). This makes Java object allocation nearly as fast as C stack allocation — a pointer increment, not a malloc. On average, 99%+ of allocations happen within TLABs without any lock contention. This is why the advice "don't reuse objects to avoid allocation" is usually wrong in Java — allocation is cheap, and the JIT can even eliminate it via escape analysis.

**Q4. What happens when you get `StackOverflowError`? How is it different from heap OOM?**

> `StackOverflowError` occurs when a thread's call stack exceeds its size limit (default ~1 MB, set with `-Xss`). Each method call adds a frame (~50-200 bytes). Deep or infinite recursion exhausts the stack. It's per-THREAD — one thread's stack overflow doesn't affect others. Heap OOM is per-PROCESS — the shared heap is full, affecting all threads. StackOverflow fix: fix the recursion (add base case, convert to iteration). Increasing `-Xss` is a last resort — it increases memory per thread (1000 threads × 2 MB = 2 GB). Heap OOM fix: find and fix the leak, or increase `-Xmx`.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** JVM memory has 5 areas: Heap (objects — Young Gen + Old Gen), Metaspace (class metadata — native memory), Thread Stack (per-thread call frames), Code Cache (JIT-compiled code), Direct Memory (NIO buffers). Each can OOM independently.
>
> **Part 2 — How/Why (30s):** New objects allocate in Eden (pointer bump via TLAB — fast, no sync). Eden full → Minor GC → survivors go to Survivor space → after 15 GCs, promote to Old Gen. Old Gen full → Major/Full GC (expensive). Metaspace stores class definitions in native memory — grows dynamically, replaces PermGen (Java 8). Thread stacks hold method frames — ~1 MB per thread. TLAB makes allocation nearly free — per-thread buffer, pointer increment, no lock.
>
> **Part 3 — Gotcha (20s):** Always configure `-XX:+HeapDumpOnOutOfMemoryError` BEFORE production — without a dump, heap OOM is blind debugging. And read the OOM suffix: `Java heap space` = objects filling heap (leak), `Metaspace` = classes filling native memory (ClassLoader leak), `unable to create native thread` = too many threads (use virtual threads or pool). Different areas, different causes, different fixes.

---

## 🧾 TL;DR

- **Heap:** Young Gen (Eden + Survivor) + Old Gen. All objects live here. `-Xms` / `-Xmx`.
- **Eden allocation via TLAB** — pointer bump, per-thread, nearly lock-free.
- **Minor GC:** Young Gen only, fast. **Major/Full GC:** Old Gen + everything, slow.
- **Promotion:** object survives 15 Minor GCs → moves to Old Gen.
- **Metaspace:** native memory, class metadata. Replaces PermGen (Java 8). `-XX:MaxMetaspaceSize`.
- **Thread Stack:** ~1 MB per thread. `StackOverflowError` = too deep recursion.
- **OOM diagnosis:** read the suffix — `heap space`, `Metaspace`, `native thread`, `Direct buffer`.
- **Always set** `-XX:+HeapDumpOnOutOfMemoryError` before production.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #24 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: full JVM memory layout (ASCII visual), heap generational model (Eden + Survivor + Old with concrete default sizes), TLAB allocation (pointer bump, per-thread, lock-free), Metaspace internals (native memory, class metadata, PermGen replacement), thread stack frames (local vars + operand stack), OOM diagnosis table (7 error messages with causes and fixes), escape analysis + scalar replacement, G1 region model vs traditional layout, string pool location (heap since Java 7). Two production footguns: no heap dump configured, unbounded thread creation. |
