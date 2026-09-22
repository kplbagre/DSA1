# ⚙️ JIT Compilation — Deep Dive

> After this note you can explain the interpretation → C1 → C2 tiered compilation pipeline, what "warmup" means, why a benchmark that runs for 1 second gives wrong results, and what deoptimization is.

---

## 🎯 The Problem This Solves

Java bytecode is platform-independent but slow to interpret. Running every `iadd` instruction through an interpreter loop is 10-50x slower than a native CPU `ADD` instruction. The JIT (Just-In-Time) compiler solves this by identifying "hot" methods (called frequently) and compiling them from bytecode to native machine code AT RUNTIME. The compiled code runs at near-C speed. But the compilation itself takes time (warmup), and the JIT makes speculative optimizations that can be wrong (deoptimization). Understanding JIT explains why Java starts slow and gets fast, why microbenchmarks lie, and why some "slow" code is actually faster than "optimized" code after JIT.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Interpreter** | Executes bytecode instruction-by-instruction. Slow but starts immediately. No compilation overhead. |
| **C1 (Client compiler)** | A fast, lightly-optimizing JIT compiler. Compiles quickly but produces modestly-optimized native code. Used for early warmup. |
| **C2 (Server compiler)** | A slower, heavily-optimizing JIT compiler. Takes longer to compile but produces highly-optimized native code. Used for hot methods after profiling. |
| **Tiered compilation** | The default (Java 8+): methods start interpreted, compile with C1 after a few thousand invocations, then recompile with C2 after profiling data shows they're truly hot. 5 tiers (0-4). |
| **Warmup** | The period after JVM start during which methods are being profiled, compiled, and optimized. Performance improves progressively until the hottest methods are C2-compiled. Typically 30s-5min for a web service. |
| **Invocation counter** | Tracks how many times a method is called. When it exceeds a threshold (default ~10,000 for C2), the method is queued for compilation. |
| **Back-edge counter** | Tracks how many times a loop iterates. Hot loops trigger on-stack replacement (OSR) — the method is compiled while the loop is still running. |
| **On-Stack Replacement (OSR)** | Compiling a method while it's currently executing (typically in a long-running loop). The interpreter is replaced with compiled code mid-method — no need to wait for the method to exit and be called again. |
| **Deoptimization** | Reverting from compiled native code back to the interpreter when a speculative optimization turns out to be wrong. Example: the JIT inlined a virtual method call assuming only one implementation exists, then a new class is loaded → assumption invalid → deoptimize. |
| **Inlining** | The most impactful JIT optimization: replacing a method call with the method body directly at the call site. Eliminates call overhead AND enables further optimizations (constant folding, dead code elimination) on the inlined body. |
| **Escape analysis** | JIT determines if an object escapes the current method/thread. If not: scalar replacement (fields become local variables, no allocation) or stack allocation. |

---

## 🧠 Mental Model

The JIT compiler is a **code optimizer that watches your program run and progressively tunes the critical paths**. Your code starts interpreted (like reading a recipe step-by-step). The JIT notices which methods you call thousands of times (hot spots) and rewrites them as native machine code (memorizing the recipe). The first level (C1) is a quick rewrite with basic optimizations. If the method stays hot, the second level (C2) does a thorough rewrite with aggressive optimizations — inlining, escape analysis, loop unrolling, dead code elimination.

Deoptimization is the JIT admitting it was wrong: "I assumed this virtual method always calls ClassA.method(), so I inlined it. A new ClassB was loaded. My assumption is wrong. Reverting to interpreter until I can recompile with the new information."

> If you can say "bytecode starts interpreted; C1 compiles hot methods quickly; C2 recompiles with heavy optimization; inlining is the most impactful optimization; warmup is the period before C2 kicks in; deoptimization reverts bad speculations to interpreter" without notes, you have JIT.

---

## 🎨 Visual — Tiered Compilation Pipeline

```
  Method "processOrder()" execution over time:

  Time:  0────────1000────────5000──────────10000───────────
         │         │           │              │
  Tier:  │  Tier 0 │  Tier 3   │              │  Tier 4
         │ INTERP. │  C1 full  │              │  C2 full
         │         │  profile  │              │  optimized
         │         │           │              │
  Speed: ▓▓▓▓▓▓▓▓▓████████████████████████████████████████████
         slow      faster      (profiling)    fastest

  TIER 0: Interpreter — runs bytecode directly. Slow but instant start.
  TIER 1: C1 compile, no profiling — fast compile, basic optimizations.
  TIER 2: C1 compile, with invocation counting — gathers profile data.
  TIER 3: C1 compile, full profiling — branch probabilities, type profiles.
  TIER 4: C2 compile — uses profile data for aggressive optimization.
          Inlining, escape analysis, loop unrolling, vectorization.

  The JVM skips tiers when possible:
  - Very simple methods → Tier 1 directly (skip profiling)
  - Very hot methods → Tier 4 quickly (C2 queue jumps)
  - C2 queue is full → stay at Tier 3 (C1 with profiling) longer

KEY INVARIANT:
   Performance IMPROVES over the first 30s-5min of runtime.
   Benchmarks measured during warmup give WRONG results.
   Only steady-state performance (after C2 compilation) represents
   the true speed of the code.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// "Java is slow" — based on benchmarks that run for 1 second
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    process(data);
}
long elapsed = System.nanoTime() - start;
System.out.println("Time: " + elapsed / 1_000_000 + "ms");

// Problems:
// 1. First iterations run INTERPRETED (Tier 0) — 10-50x slower than compiled
// 2. C1 compiles around iteration ~1000 — speed jumps
// 3. C2 compiles around iteration ~10000 — speed jumps again
// 4. You're averaging SLOW warmup iterations with FAST compiled iterations
// 5. The result is meaningless — it doesn't represent steady-state performance

// ✅ Use JMH (Java Microbenchmark Harness) for correct benchmarks:
// JMH handles warmup, fork (separate JVM), iteration count, and statistical analysis.
```

---

### Level 2 — The real mechanism

#### 2.1 — Key JIT optimizations

**Inlining (the most impactful):**

```java
// Before inlining:
public int calculate(int x) {
    return add(x, 5);   // method call overhead: push frame, jump, return, pop
}

private int add(int a, int b) {
    return a + b;
}

// After JIT inlining:
public int calculate(int x) {
    return x + 5;   // add() body pasted directly — no call overhead
    // Further optimization: if x is known → constant folding → return constant
}

// Inlining threshold: methods with bytecode size ≤ 325 bytes are candidates
// -XX:MaxInlineSize=35 → methods ≤ 35 bytes are always inlined (even if cold)
// -XX:FreqInlineSize=325 → hot methods ≤ 325 bytes are inlined
// Deeply nested call chains: inlining depth limit = 9 by default
```

**Escape analysis + scalar replacement:**

```java
// JIT detects: the Point object never escapes this method
public double distance(int x1, int y1, int x2, int y2) {
    Point p = new Point(x2 - x1, y2 - y1);   // object created but doesn't escape
    return Math.sqrt(p.x * p.x + p.y * p.y);
}

// After escape analysis + scalar replacement:
public double distance(int x1, int y1, int x2, int y2) {
    int dx = x2 - x1;   // Point.x → local variable
    int dy = y2 - y1;   // Point.y → local variable
    return Math.sqrt(dx * dx + dy * dy);
}
// No heap allocation. No GC pressure. Effectively free.
```

**Loop optimizations:**

```java
// Loop unrolling: execute multiple iterations per loop step
// Before:
for (int i = 0; i < 1000; i++) { sum += array[i]; }
// After unrolling (conceptual — JIT does this):
for (int i = 0; i < 1000; i += 4) {
    sum += array[i] + array[i+1] + array[i+2] + array[i+3];
}
// Fewer branch instructions, better instruction pipelining

// Loop-invariant code motion:
for (int i = 0; i < list.size(); i++) {   // list.size() called every iteration
    process(list.get(i));
}
// JIT hoists list.size() out of the loop if list isn't modified:
int size = list.size();
for (int i = 0; i < size; i++) { process(list.get(i)); }
```

**Dead code elimination:**

```java
// JIT removes code that can never execute or whose result is never used
if (false) { expensiveOperation(); }   // eliminated
int unused = computeSomething();        // eliminated if 'unused' is never read
```

#### 2.2 — Deoptimization

```java
// The JIT makes SPECULATIVE optimizations based on observed behavior:

// Scenario: processOrder() is always called on ConcreteOrder (observed during profiling)
// JIT speculation: "I'll inline ConcreteOrder.validate() directly — no virtual dispatch needed"
// This is called "devirtualization" — turning virtual call into direct call

// Later: a PremiumOrder class is loaded (also extends Order)
// The JIT's assumption (only ConcreteOrder) is now WRONG.
// JIT detects this → DEOPTIMIZATION:
// 1. Discard the compiled native code for processOrder()
// 2. Revert to interpreter
// 3. Re-profile with the new class hierarchy
// 4. Recompile with both ConcreteOrder and PremiumOrder considered

// Deoptimization is visible in GC/JIT logs:
// -XX:+TraceDeoptimization → logs every deopt event
// Common causes:
// - New class loaded that invalidates class hierarchy assumption
// - Array/null check that was optimistically removed but actually triggers
// - Uncommon trap: a branch that was never taken during profiling is now taken
```

#### 2.3 — Code Cache

```java
// JIT-compiled native code is stored in the Code Cache (off-heap memory).
// Default size: 240 MB (-XX:ReservedCodeCacheSize=240m)

// When Code Cache fills up:
// - JIT stops compiling new methods
// - Performance degrades to interpreter speed for un-compiled methods
// - Warning: "CodeCache is full. Compiler has been disabled."

// Monitor: jcmd <pid> Compiler.codecache → shows usage
// Or JMX: java.lang:type=MemoryPool,name=CodeCache

// Increase if needed:
// -XX:ReservedCodeCacheSize=512m
// Symptoms of Code Cache pressure: sudden performance degradation
// after the service has been running for hours (all cold methods remain interpreted)
```

---

### Level 3 — The subtleties

#### 3.1 — Why microbenchmarks lie

```java
// Problem 1: warmup — first N iterations are interpreted
// Problem 2: dead code elimination — JIT removes your benchmark code
//            if it detects the result is never used
// Problem 3: constant folding — if input is a compile-time constant,
//            JIT replaces the entire computation with the constant result
// Problem 4: JVM fork — one benchmark's JIT compilation state affects the next

// ✅ Use JMH (Java Microbenchmark Harness) — handles ALL of these:
@Benchmark
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)   // run in 2 separate JVM forks
public int benchmarkArraySum(MyState state) {
    return Arrays.stream(state.array).sum();
    // JMH's Blackhole prevents dead code elimination
    // @Warmup ensures C2 is fully compiled before measurement
    // @Fork ensures clean JVM state
}
```

#### 3.2 — AOT compilation (GraalVM Native Image)

```java
// JIT compiles at runtime → warmup period.
// AOT (Ahead-of-Time) compiles at BUILD time → no warmup.

// GraalVM Native Image:
// - Compiles Java bytecode to native executable at build time
// - Startup: milliseconds (vs seconds for JVM)
// - Memory: 50-80% less than JVM (no interpreter, no JIT compiler in memory)
// - Peak performance: typically lower than JIT (no runtime profiling → less optimization)
// - Trade-off: fast startup + low memory vs lower peak throughput

// Use cases: serverless (Lambda — cold start matters), CLI tools, microservices with
// short request lifetimes where warmup cost exceeds service lifetime.
// NOT ideal for long-running services where JIT's profile-guided optimization
// produces faster steady-state code than AOT.
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Java is slow because it's interpreted" | Java is interpreted for the first few thousand invocations. After JIT (C2), hot methods run as native machine code — within 5-10% of equivalent C/C++ code, sometimes faster (JIT uses runtime profiling info that static compilers don't have). |
| "I should write 'optimized' code to help the JIT" | The JIT often produces better code from clean, idiomatic Java than from manually "optimized" code. Creating small methods is better for inlining than writing large monolithic methods. Trust the JIT; write readable code. |
| "My benchmark shows Java is 10x slower than C" | If your benchmark runs for 1 second, most of it is warmup (interpreted code). Run for 30+ seconds, or use JMH with proper warmup iterations. Steady-state JIT-compiled Java is typically within 2x of C for most workloads. |
| "Deoptimization is bad and should be avoided" | Deoptimization is the JIT CORRECTLY handling a changed assumption. It's a brief performance dip while the JIT recompiles with new information. Avoiding deoptimization by writing less polymorphic code is premature optimization — the JIT handles polymorphism well. |
| "AOT is always better than JIT" | AOT has fast startup and low memory but lower peak performance — it can't use runtime profiling to optimize hot paths. JIT has slow startup but higher peak throughput — it knows which branches are actually taken, which types are actually used. Choose based on your workload: short-lived (AOT) vs long-running (JIT). |

---

## 🐞 Production Footguns

---

> **Footgun: Benchmarking during warmup**
> **Cost:** Wrong performance data → wrong architecture decisions
>
> A team benchmarked a new serialization library by running 10,000 iterations and measuring total time. The library appeared 3x slower than the old one. In reality, the new library had more complex code that took longer to JIT-compile — its warmup was longer. After proper JMH benchmarking with 5s warmup iterations, the new library was 20% FASTER at steady state. The team almost rejected a better library based on a warmup artifact.

```java
// ❌ The trap: measuring during warmup
long start = System.nanoTime();
for (int i = 0; i < 10_000; i++) { serialize(data); }
long time = System.nanoTime() - start;   // includes warmup — wrong

// ✅ The fix: JMH with proper warmup
@Benchmark
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public byte[] benchSerialize(MyState state) {
    return serializer.serialize(state.data);
}
```

---

> **Footgun: Code Cache exhaustion**
> **Cost:** Performance degradation after hours of uptime
>
> A large Spring Boot service with 10,000+ classes gradually filled the 240 MB default Code Cache. After 6 hours: `CodeCache is full. Compiler has been disabled.` All uncompiled methods stayed interpreted — latency increased 5x for cold paths. The fix: increase `-XX:ReservedCodeCacheSize=512m`.

```bash
# ❌ The trap: default Code Cache (240 MB) for large applications
# After hours: "CodeCache is full" → JIT disabled → performance cliff

# ✅ The fix: increase Code Cache for large services
-XX:ReservedCodeCacheSize=512m
# Monitor: jcmd <pid> Compiler.codecache
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `class-loading.md` | JIT compilation happens AFTER class loading + interpretation. The JIT's input is the bytecode loaded by the ClassLoader. Deoptimization can be triggered by loading new classes that invalidate JIT assumptions. |
| `jvm-memory-areas.md` | JIT-compiled native code is stored in the Code Cache (separate from heap and Metaspace). Code Cache exhaustion → JIT disabled → performance degradation. |
| `java-memory-model.md` | JIT reordering is constrained by the JMM's happens-before rules. The JIT can reorder instructions for performance, but MUST preserve happens-before relationships (e.g., volatile reads/writes can't be reordered past each other). |
| `synchronized-volatile.md` | Lock coarsening and lock elision are JIT optimizations. The JIT merges adjacent synchronized blocks (coarsening) and eliminates locks on non-escaping objects (elision via escape analysis). |
| `java-version-evolution.md` | Tiered compilation became default in Java 8. GraalVM (AOT alternative) emerged as a Java 11+ option. Java 21's virtual threads interact with JIT — cooperative scheduling points are JIT-aware. |

---

## 🎙️ Interview Deep Questions

**Q1. What is JIT compilation and why doesn't Java just compile everything ahead of time?**

> JIT (Just-In-Time) compiles bytecode to native machine code at runtime. It doesn't compile everything upfront because: (1) startup would be slow — compiling thousands of methods before the first request is wasteful; (2) most methods are cold (called rarely) — compiling them wastes Code Cache and CPU; (3) runtime profiling enables optimizations a static compiler can't do — the JIT knows which branches are taken 99% of the time, which types are actually used, and which methods are hot. Profile-guided optimization produces code that's sometimes FASTER than equivalent C, because the JIT has runtime information the C compiler doesn't.

**Q2. Explain tiered compilation. What are the tiers?**

> Tiered compilation uses two compilers in sequence. Tier 0: pure interpreter — instant start, no compilation overhead. Tier 1-3: C1 (client compiler) — fast compilation, basic optimizations, and profiling instrumentation. C1 gathers invocation counts, branch probabilities, and type profiles. Tier 4: C2 (server compiler) — slow compilation, aggressive optimizations using the profile data from C1 (inlining, escape analysis, loop unrolling, devirtualization). Methods progress through tiers based on invocation count: ~1,000 for C1, ~10,000 for C2. This gives both fast startup (interpreter + C1) and high peak performance (C2).

**Q3. What is deoptimization? Give an example.**

> Deoptimization is the JIT reverting compiled native code back to the interpreter when a speculative optimization is invalidated. Example: the JIT observes that `order.validate()` always dispatches to `ConcreteOrder.validate()` (monomorphic call site). It inlines the method body directly — eliminating virtual dispatch overhead. Later, `PremiumOrder` is loaded (also extends `Order`). The call site is now polymorphic — the JIT's assumption is wrong. It deoptimizes: discards the compiled code, reverts to the interpreter, re-profiles, and recompiles considering both types. Deoptimization is a brief performance dip, not a bug — it's the JIT correctly adapting to new information.

**Q4. What is the warmup period and why does it matter for benchmarks?**

> Warmup is the period after JVM start during which methods are being profiled and compiled — typically 30s-5min for a web service. During warmup, performance improves progressively as hot methods move from interpreter (slow) to C1 (faster) to C2 (fastest). A benchmark that runs during warmup measures a mix of interpreted and partially-compiled code — not representative of steady-state performance. JMH (Java Microbenchmark Harness) handles this with explicit warmup iterations (discarded from measurements), separate JVM forks (clean compilation state), and Blackhole (prevents dead code elimination). Without JMH, microbenchmarks are almost always wrong.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** JIT compiles hot bytecode methods to native machine code at runtime. Tiered compilation: interpreter → C1 (fast compile, basic optimizations) → C2 (slow compile, aggressive optimizations using runtime profiling). Methods get faster as they're compiled to higher tiers.
>
> **Part 2 — How/Why (30s):** Methods start interpreted — instant startup but slow. After ~1000 invocations, C1 compiles with basic optimizations + profiling instrumentation. After ~10000 invocations, C2 recompiles using the profile data — inlining (replace call with body), escape analysis (eliminate allocation), loop unrolling, devirtualization. C2's profile-guided optimization can beat static compilers because it knows runtime type frequencies and branch probabilities. Compiled code goes into the Code Cache (240 MB default).
>
> **Part 3 — Gotcha (20s):** Two traps: benchmarking during warmup gives wrong results — use JMH with warmup iterations, not System.nanoTime() in a loop. And Code Cache exhaustion in large services — after hours of uptime, the 240 MB default fills up, JIT stops compiling, and performance drops. Fix: `-XX:ReservedCodeCacheSize=512m`. Monitor with `jcmd <pid> Compiler.codecache`.

---

## 🧾 TL;DR

- **Tiered compilation (default):** Interpreter (Tier 0) → C1 (Tiers 1-3) → C2 (Tier 4).
- **C1:** fast compile, basic optimizations, gathers profiling data.
- **C2:** slow compile, aggressive optimizations using profile data. Peak performance.
- **Warmup:** 30s-5min for steady state. Benchmarks during warmup are WRONG. Use JMH.
- **Inlining:** most impactful optimization. Small methods inline better. Trust the JIT; write clean code.
- **Escape analysis:** objects that don't escape are scalar-replaced (no allocation).
- **Deoptimization:** JIT reverts to interpreter when a speculation is wrong (new class loaded). Normal.
- **Code Cache:** 240 MB default. Exhaustion → JIT disabled → performance cliff. Monitor + increase.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #26 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: tiered compilation pipeline (5 tiers, interpreter → C1 → C2), key JIT optimizations (inlining thresholds, escape analysis + scalar replacement, loop unrolling, dead code elimination, devirtualization), deoptimization (speculative optimization invalidation), OSR (on-stack replacement for hot loops), Code Cache management, JMH for correct benchmarking, AOT vs JIT trade-offs (GraalVM Native Image), invocation/back-edge counters. Two production footguns: benchmarking during warmup, Code Cache exhaustion. |
