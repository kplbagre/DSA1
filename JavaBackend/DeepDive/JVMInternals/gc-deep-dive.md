# ⚙️ Garbage Collection — Deep Dive

> After this note you can explain mark-sweep-compact, why generational GC works (the weak generational hypothesis), compare G1 vs ZGC with concrete pause-time numbers, read GC log output, and know the 5 JVM flags every production service should set.

---

## 🎯 The Problem This Solves

Java has no `free()`. Objects are allocated on the heap and automatically reclaimed when unreachable. But HOW the GC determines reachability, WHEN it runs, and HOW LONG it pauses your application determines whether your service has 5ms p99 latency or 500ms GC spikes. Understanding GC internals is the difference between "my service is slow sometimes" and "my service has predictable latency under load."

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **GC roots** | Starting points for reachability analysis: local variables on active thread stacks, static fields, active threads, JNI references. Anything reachable from a GC root is live; everything else is garbage. |
| **Mark** | Phase 1: traverse the object graph from GC roots, marking every reachable object as "live." |
| **Sweep** | Phase 2: scan the heap, reclaim memory of unmarked (dead) objects. Leaves fragmented free space. |
| **Compact** | Phase 3 (optional): move live objects together, eliminating fragmentation. Enables fast pointer-bump allocation. Expensive — every object moves, every reference updates. |
| **Stop-the-world (STW)** | A GC pause during which ALL application threads are stopped. No code runs. STW pauses cause latency spikes. Modern GCs minimize STW duration. |
| **Concurrent GC** | GC work done while application threads are running. Reduces STW pauses but adds CPU overhead (GC threads compete with application threads). |
| **Minor GC** | Collects Young Gen only. Fast (most young objects are dead). STW pause: typically < 10ms. |
| **Major/Full GC** | Collects Old Gen (Major) or entire heap + Metaspace (Full). Slower. STW pause: tens to hundreds of ms (G1) or sub-ms (ZGC). |
| **Weak generational hypothesis** | The empirical observation: most objects die young. ~95% of objects become garbage before their first GC cycle. This is why generational GC works — collect the young frequently (cheap), the old rarely (expensive). |

---

## 🧠 Mental Model

GC is a **janitor in a building** (the heap). The janitor's job: find rooms nobody uses (unreachable objects) and reclaim them. The building has two floors: ground floor (Young Gen — most visitors leave quickly) and upper floor (Old Gen — long-term tenants). The janitor sweeps the ground floor frequently (Minor GC — fast, most rooms are empty). The upper floor is swept rarely (Major GC — slow, residents are settled).

The janitor traces connections from the entrance (GC roots) through hallways (references). Any room reachable from the entrance is occupied. Rooms with no path from any entrance are vacant — reclaimed.

> If you can say "GC roots are the starting points; mark = trace reachability; sweep = reclaim dead; compact = defragment; Minor GC = young gen (fast, frequent); Full GC = everything (slow, avoid); G1 = region-based, predictable pauses; ZGC = sub-ms pauses regardless of heap size" without notes, you have GC.

---

## 🎨 Visual — GC Algorithm: Mark-Sweep-Compact

```
  PHASE 1 — MARK (trace from GC roots):

  GC Root → A → B → C    ← all marked LIVE
              → D         ← marked LIVE
  E (no path from any root) ← DEAD
  F → G (both unreachable)  ← BOTH DEAD (circular ref doesn't save them)

  PHASE 2 — SWEEP (reclaim dead):

  Before: [A][E][B][F][C][G][D]
  After:  [A][ ][B][ ][C][ ][D]    ← free spaces where E, F, G were
                                      Memory is FRAGMENTED

  PHASE 3 — COMPACT (defragment):

  After:  [A][B][C][D][         ]   ← live objects contiguous
                       ^free ptr     ← next allocation = pointer bump
                                      Fast allocation, no fragmentation

KEY INVARIANT:
   Mark cost ∝ number of LIVE objects (not heap size).
   If most objects are dead (Young Gen), marking is cheap.
   This is why Minor GC is fast — Eden is mostly dead objects.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// "I don't need to understand GC — Java handles it automatically"
// Until:
// - P99 latency spikes to 500ms every 30 seconds → Full GC pauses
// - Service killed by orchestrator → GC thrashing (>90% time in GC)
// - Heap dump shows 2 GB of dead objects in Old Gen → promotion storm
// - GC logs show 50 Full GCs per hour → undersized heap or memory leak

// Without understanding GC:
// "Is this a code bug, a GC problem, or a heap sizing issue?"
// You can't answer without knowing what the GC is doing.
```

---

### Level 2 — The real mechanism

#### 2.1 — GC root types

```
  GC ROOTS (the starting points — if an object is reachable from ANY root, it's live):

  1. LOCAL VARIABLES on active thread stacks
     Every method frame on every running thread → the variables in those frames
     are roots. When the method returns, the frame is popped → those roots disappear.

  2. STATIC FIELDS
     Class-level variables → live as long as the class is loaded.
     This is why static Map/List caches can leak memory — they're permanent roots.

  3. ACTIVE THREADS
     Thread objects themselves are roots.

  4. JNI REFERENCES
     References from native code (C/C++ via JNI).

  5. SYNCHRONIZED MONITORS
     Objects used as monitors in active synchronized blocks.
```

**Why circular references between garbage objects are collected:**

```
  A → B → A  (circular reference)
  Neither A nor B is reachable from ANY GC root.
  Mark phase: starts from roots, never reaches A or B.
  Both are garbage → both collected.
  (This is why Java doesn't need reference counting — mark-and-sweep handles cycles.)
```

#### 2.2 — The 4 GC implementations

| GC | Algorithm | STW pause | Best for | Java default |
|---|---|---|---|---|
| **Serial** | Single-thread mark-copy (Young), mark-sweep-compact (Old) | Long (proportional to heap) | Small apps, single-core | Java 8 client-mode |
| **Parallel** | Multi-thread mark-copy (Young), mark-sweep-compact (Old) | Medium (multiple GC threads, still full STW) | Batch jobs, throughput > latency | Java 8 server-mode |
| **G1** | Region-based, concurrent mark, incremental compact | 10-200ms target (configurable) | General purpose, balanced latency/throughput | Java 9+ default |
| **ZGC** | Concurrent mark + relocate, colored pointers, load barriers | < 1ms (regardless of heap size) | Ultra-low-latency, large heaps (multi-TB) | Production in Java 15, generational in Java 21 |

**CMS (Concurrent Mark-Sweep): REMOVED in Java 14.** Don't reference it in interviews except as history.

#### 2.3 — G1 GC (Garbage-First) — the default

```
  G1 divides the heap into equal-size REGIONS (1-32 MB each).
  Each region is dynamically tagged: Eden, Survivor, Old, or Humongous.

  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
  │ Eden│ Old │ Eden│Surv.│ Old │ Old │ Eden│Humng│
  ├─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┤
  │ Old │Free │ Old │ Eden│Free │ Old │ Old │Humng│
  └─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘

  YOUNG GC (evacuation pause — STW):
  Copies live objects from Eden + Survivor regions to new Survivor regions.
  Dead objects → entire region is reclaimed. Fast because most Eden objects are dead.

  MIXED GC:
  Collects Young regions + some Old regions (the ones with the most garbage).
  "Garbage-First" = prioritize regions with the highest garbage ratio.
  Goal: meet the pause-time target by selecting the right number of regions.

  CONCURRENT MARKING:
  Runs WHILE the application runs (not STW).
  Identifies which Old regions have the most garbage → selected for mixed collection.

  PAUSE-TIME TARGET: -XX:MaxGCPauseMillis=200 (default)
  G1 adjusts the number of regions collected per GC cycle to hit this target.
  Lower target → fewer regions per cycle → more frequent but shorter pauses.
```

#### 2.4 — ZGC — sub-millisecond pauses

```
  ZGC goal: < 1ms pause time regardless of heap size (even multi-terabyte).

  HOW: most GC work is done CONCURRENTLY (while app runs).
  STW pauses only for: root scanning (very brief — proportional to # of GC roots, not heap size).

  KEY TECHNIQUE: colored pointers + load barriers
  - ZGC uses bits in the object pointer to store GC metadata
    (is this pointer remapped? is the object marked? which GC cycle?)
  - On every object access, a load barrier checks the pointer color
  - If the pointer is stale (object was relocated), the barrier updates it
  - This allows ZGC to relocate objects WITHOUT stopping the application

  Java 21: Generational ZGC (default ZGC mode)
  - Adds generational collection to ZGC — Young objects collected more frequently
  - Reduces CPU overhead compared to non-generational ZGC
  - Enable: -XX:+UseZGC -XX:+ZGenerational (default in Java 21)

  WHEN TO USE ZGC:
  - Latency-critical services (< 10ms p99 required)
  - Large heaps (10+ GB) where G1 pauses would be 100+ms
  - Services where GC pauses directly impact user experience
```

#### 2.5 — GC log analysis

```java
// Enable GC logging (Java 9+ unified logging):
// -Xlog:gc*:file=gc.log:time,uptime,level,tags

// Sample G1 GC log entry:
// [2026-09-21T14:30:45.123+0000] GC(42) Pause Young (Normal) (G1 Evacuation Pause)
//   Eden: 256M→0B  Survivors: 32M→48M  Old: 1024M→1010M
//   Heap: 1312M→1058M(2048M)
//   Times: user=0.05 sys=0.01, real=0.02 secs

// Reading this:
// GC(42):          42nd GC event
// Pause Young:     Young generation collection (Minor GC)
// Eden 256M→0B:    Eden cleared (all objects dead or evacuated to Survivor)
// Survivors 32M→48M: some objects promoted to Survivor (aged)
// Old 1024M→1010M:  Old Gen slightly reduced (some dead old objects?)
// Heap 1312M→1058M: total heap reduced by ~250 MB
// real=0.02:        20ms STW pause — good for G1
```

---

### Level 3 — The subtleties

#### 3.1 — The 5 production JVM flags

```bash
# 1. Heap size — set min = max to prevent resize at runtime
-Xms4g -Xmx4g

# 2. GC algorithm choice
-XX:+UseG1GC              # G1 (default Java 9+)
# OR
-XX:+UseZGC               # ZGC for ultra-low latency (Java 15+)

# 3. GC logging — always enabled in production
-Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=5,filesize=100m

# 4. Heap dump on OOM — non-negotiable
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/dumps/

# 5. Metaspace cap — prevent unbounded growth
-XX:MaxMetaspaceSize=256m

# G1-specific tuning:
-XX:MaxGCPauseMillis=200    # target pause time (default 200ms)
-XX:G1HeapRegionSize=4m     # region size (1m to 32m, auto-selected if omitted)
```

#### 3.2 — Memory leak detection

```java
// A Java memory leak = objects that are REACHABLE but no longer NEEDED.
// GC can't help — the objects ARE reachable from GC roots.

// Common leak patterns:
// 1. Static collection that grows without bounds
static Map<String, Session> sessions = new HashMap<>();
// sessions.put() on every login, never removed → grows forever

// 2. Listener registration without deregistration
eventBus.register(listener);   // registered on startup
// never: eventBus.unregister(listener) → listener + everything it references → leaked

// 3. ThreadLocal without remove()
// In thread pool: ThreadLocal set on request 1, not removed,
// still holding reference when thread handles request 2, 3, 4...

// 4. ClassLoader leak (covered in class-loading.md)

// Detection workflow:
// 1. Take heap dump: jmap -dump:live,format=b,file=heap.hprof <pid>
// 2. Open in Eclipse MAT (Memory Analyzer Tool)
// 3. Look at "Dominator Tree" — shows which objects retain the most memory
// 4. Look at "Leak Suspects" report — MAT's automated analysis
// 5. Trace the GC root path: object → who references it → why it's alive
```

#### 3.3 — `System.gc()` — don't call it

```java
// System.gc() is a "suggestion" — the JVM may ignore it.
// Even if honored, it triggers a FULL GC — expensive, STW.
// In production: adds unpredictable latency spikes.
// Disable: -XX:+DisableExplicitGC (makes System.gc() a no-op)

// The ONE exception: before taking a heap dump for analysis.
// Calling System.gc() first collects soft/weak references and gives
// a cleaner picture of truly-reachable objects. But never in production code.
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "GC makes Java slow" | Modern GCs (G1, ZGC) add < 5% CPU overhead. ZGC pauses are < 1ms. Object allocation in Java (TLAB pointer bump) is faster than C's malloc(). GC enables this fast allocation by handling deallocation. |
| "Full GC means the heap is full" | Full GC means the GC is collecting the entire heap. It CAN be triggered by Old Gen filling up, but also by `System.gc()`, Metaspace exhaustion, or the JVM's heuristics. Frequent Full GC = problem. Occasional Full GC = normal. |
| "Circular references cause memory leaks" | Mark-and-sweep traces from GC roots. If A→B→A but neither is reachable from any root, both are garbage. Circular references are collected correctly. Java leaks happen when objects ARE reachable but no longer needed (static maps, unremoved listeners). |
| "Setting `null` helps GC" | Almost never necessary. Local variables go out of scope when the method returns — the reference is removed from the stack. Setting `obj = null` before method exit is redundant. The JIT may even eliminate the null assignment. Exception: long-running methods holding references to large objects — nulling the reference before a long operation allows GC to collect it. |
| "ZGC is always better than G1" | ZGC uses more CPU (concurrent work + load barriers). For throughput-critical batch jobs where pause time doesn't matter, Parallel GC or G1 delivers higher throughput. ZGC is optimal for latency-critical interactive services. Choose based on your priority: latency → ZGC, throughput → Parallel/G1. |

---

## 🐞 Production Footguns

---

> **Footgun: -Xms ≠ -Xmx causes resize pauses**
> **Cost:** Unpredictable GC pauses during heap growth
>
> A service was configured with `-Xms512m -Xmx4g`. Under load, the heap grew from 512 MB toward 4 GB. Each resize required the JVM to allocate new memory and potentially reorganize regions — adding unpredictable pauses during warmup. Setting `-Xms = -Xmx` allocates all heap memory upfront — no resize, no surprise pauses.

```bash
# ❌ The trap: different -Xms and -Xmx
-Xms512m -Xmx4g    # heap grows at runtime → resize pauses

# ✅ The fix: set equal
-Xms4g -Xmx4g      # all memory allocated at startup → no resize
```

---

> **Footgun: Static cache without eviction → Old Gen fills → Full GC storm**
> **Cost:** GC thrashing → service killed
>
> A product catalog service cached every product lookup in a `static HashMap`. After 24 hours, the map held 5 million entries → 2 GB in Old Gen. When Old Gen filled, Full GC ran — but couldn't free anything (all entries reachable from the static root). Full GC ran repeatedly, spending >90% of CPU time → `GC overhead limit exceeded` → service killed.

```java
// ❌ The trap: unbounded static cache
private static final Map<Long, Product> cache = new HashMap<>();

public Product getProduct(Long id) {
    return cache.computeIfAbsent(id, this::loadFromDb);
    // Never evicts. Grows until Old Gen is full.
}

// ✅ The fix: bounded cache with eviction (Caffeine)
private final Cache<Long, Product> cache = Caffeine.newBuilder()
    .maximumSize(100_000)
    .expireAfterWrite(Duration.ofMinutes(30))
    .build();
// Bounded: max 100K entries. Evicts oldest after 30 min.
// Old Gen stays stable. No Full GC storm.
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `jvm-memory-areas.md` | GC operates on the heap regions defined in Note #24: Minor GC collects Young Gen (Eden + Survivor), Full GC collects entire heap + Metaspace. Understanding heap layout is prerequisite for understanding GC behavior. |
| `class-loading.md` | Metaspace GC: classes are unloaded when their ClassLoader is GC'd. Understanding class loading explains Metaspace OOM and ClassLoader leaks. |
| `hashmap-internals.md` | HashMap Nodes are heap objects — 1M entries = 1M Node objects in Old Gen (long-lived). Understanding GC explains why a large static HashMap causes Full GC storms (all entries reachable → GC can't free them → Old Gen fills). |
| `string-internals.md` | String pool lives in the heap (since Java 7). Interned strings are GC-eligible when unreferenced. Understanding GC explains why excessive `intern()` doesn't cause PermGen overflow in modern Java (strings are on the regular heap now). |
| `java-version-evolution.md` | Java 9: G1 default. Java 14: CMS removed. Java 15: ZGC production. Java 21: generational ZGC. The GC evolution is tightly coupled to the Java version timeline. |

---

## 🎙️ Interview Deep Questions

**Q1. How does the garbage collector determine which objects are alive?**

> The GC uses reachability analysis from GC roots. GC roots are: local variables on active thread stacks, static fields, active thread objects, JNI references, and synchronized monitors. The GC traverses the entire object graph from these roots — any object reachable through a chain of references is marked live. Everything else is garbage. This is why circular references between garbage objects are correctly collected — if neither object is reachable from a root, both are dead regardless of their references to each other. The cost of marking is proportional to the number of LIVE objects, not the heap size — which is why Minor GC (mostly dead objects in Eden) is fast.

**Q2. Compare G1 and ZGC. When would you choose each?**

> G1 is the default since Java 9. It divides the heap into regions, collects the most garbage-filled regions first (Garbage-First), and targets a configurable pause time (default 200ms via `-XX:MaxGCPauseMillis`). STW pauses are 10-200ms depending on heap size and configuration. Good for general-purpose workloads that can tolerate occasional 100ms pauses. ZGC targets sub-1ms pauses regardless of heap size — even multi-terabyte heaps. It achieves this with concurrent marking and concurrent relocation using colored pointers and load barriers. The trade-off: ZGC uses more CPU (the load barriers add overhead on every object access). Choose G1 for balanced throughput/latency. Choose ZGC when p99 latency < 10ms is critical and you can afford the CPU overhead (~5-10%).

**Q3. What is a memory leak in Java? How do you detect one?**

> A Java memory leak is an object that is REACHABLE from GC roots but no longer NEEDED by the application. The GC can't collect it because it's technically alive. Common patterns: static collections that grow without bounds (cache without eviction), registered listeners that are never deregistered, ThreadLocals in thread pools that are never removed. Detection: enable GC logging, look for Old Gen growing steadily over time without full GC freeing it. Take a heap dump (`-XX:+HeapDumpOnOutOfMemoryError` or `jmap`), open in Eclipse MAT, examine the Dominator Tree (largest retained objects) and Leak Suspects report, trace the GC root path to find WHY the object is alive.

**Q4. What are the 5 JVM flags every production service should set?**

> 1. `-Xms = -Xmx` (e.g., `-Xms4g -Xmx4g`): prevent heap resizing at runtime — eliminates resize pauses. 2. GC algorithm: `-XX:+UseG1GC` (default) or `-XX:+UseZGC` (latency-critical). 3. GC logging: `-Xlog:gc*:file=gc.log:time,tags` — always on, essential for post-incident analysis. 4. Heap dump on OOM: `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dumps/` — without this, OOM crashes are impossible to debug. 5. Metaspace cap: `-XX:MaxMetaspaceSize=256m` — prevents unbounded native memory growth from ClassLoader leaks. These 5 flags turn blind production crashes into diagnosable incidents.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** GC finds unreachable objects by tracing from GC roots (thread stacks, static fields), marks them, sweeps dead ones, and optionally compacts to prevent fragmentation. Generational: Young Gen collected frequently (cheap), Old Gen rarely (expensive).
>
> **Part 2 — How/Why (30s):** Minor GC collects Young Gen — most objects in Eden are dead, so marking is fast. Objects surviving 15 Minor GCs promote to Old Gen. Full GC collects everything — slow, avoid it. G1 (default Java 9+) divides heap into regions, collects the most garbage-filled first, targets configurable pause time. ZGC (Java 15+) achieves < 1ms pauses via concurrent relocation with colored pointers — object pointers carry GC metadata, load barriers handle relocation transparently.
>
> **Part 3 — Gotcha (20s):** Two traps: static cache without eviction fills Old Gen → Full GC can't free anything → GC thrashing → OOM. Fix: use Caffeine with `maximumSize` + TTL. And always set `-Xms = -Xmx` in production — different values cause heap resize pauses under load. Non-negotiable: `-XX:+HeapDumpOnOutOfMemoryError` — without it, OOM is blind debugging.

---

## 🧾 TL;DR

- **GC roots:** thread stacks, static fields, active threads, JNI refs. Reachable = live. Unreachable = garbage.
- **Mark-sweep-compact:** mark live, sweep dead, compact to defragment. Mark cost ∝ live objects.
- **Generational:** Young Gen (Eden + Survivor) collected frequently (Minor GC, fast). Old Gen collected rarely (Full GC, slow).
- **G1 (default Java 9+):** region-based, targets `-XX:MaxGCPauseMillis=200` (configurable).
- **ZGC (Java 15+):** < 1ms pauses, colored pointers + load barriers, concurrent relocation.
- **CMS: REMOVED Java 14.** Don't mention except as history.
- **5 production flags:** `-Xms=-Xmx`, GC choice, GC logging, heap dump on OOM, Metaspace cap.
- **Memory leak = reachable but unneeded.** Detect: heap dump + Eclipse MAT Dominator Tree.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #25 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: mark-sweep-compact algorithm with cost analysis, GC roots (5 types), weak generational hypothesis, 4 GC implementations comparison table (Serial/Parallel/G1/ZGC), G1 internals (region model, mixed GC, concurrent marking, pause-time target), ZGC internals (colored pointers, load barriers, concurrent relocation, generational Java 21), GC log reading, 5 production JVM flags, memory leak patterns (4 types) + detection workflow (heap dump + MAT), System.gc() → don't call. Two production footguns: -Xms ≠ -Xmx resize pauses, static cache without eviction → GC thrashing. |
