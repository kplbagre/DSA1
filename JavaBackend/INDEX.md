# ☕ JavaBackend Knowledge Base — Master Index

> **36 notes. 5 phases. Staff-level depth.** Navigate by topic group below. Each note follows the standards in `DeepDive/notes-standards-deepdive.md`. Spring track chapters follow the 8-section arc in `Spring/spring-10-hour-plan.md`.

---

## 📁 Folder Structure

```
JavaBackend/
├── INDEX.md                          ← THIS FILE
├── AGENTS.md                        ← Subdomain rules + full roadmap tracker
│
├── DeepDive/                        ← In-depth concept studies
│   ├── notes-standards-deepdive.md  ← Writing standards for all deep-dives
│   │
│   ├── CoreJava/                    ← Phase 1: Language fundamentals (8 notes)
│   │   ├── hashmap-internals.md
│   │   ├── equals-hashcode-contract.md
│   │   ├── generics-type-erasure.md
│   │   ├── functional-interfaces.md
│   │   ├── java-version-evolution.md
│   │   ├── string-internals.md
│   │   ├── exception-hierarchy.md
│   │   └── java-pass-by-value-semantics.md
│   │
│   ├── StreamsFunctional/           ← Phase 2: Streams & functional programming (4 notes)
│   │   ├── stream-pipeline-internals.md
│   │   ├── collectors-deepdive.md
│   │   ├── optional-proper-usage.md
│   │   └── completable-future.md
│   │
│   ├── Concurrency/                 ← Phase 4: Threading & synchronization (8 notes)
│   │   ├── java-memory-model.md
│   │   ├── synchronized-volatile.md
│   │   ├── locks-reentrant-readwrite.md
│   │   ├── concurrent-collections.md
│   │   ├── thread-pool-executor.md
│   │   ├── synchronization-aids.md
│   │   ├── fork-join-parallel-streams.md
│   │   └── virtual-threads-java21.md
│   │
│   └── JVMInternals/                ← Phase 4: JVM mechanics (4 notes)
│       ├── class-loading.md
│       ├── jvm-memory-areas.md
│       ├── gc-deep-dive.md
│       └── jit-compilation.md
│
├── Spring/                          ← Phase 3: Spring track (5 chapters)
│   ├── spring-10-hour-plan.md       ← Master plan + format standards
│   ├── spring-prep-log.md           ← Context preservation
│   └── DeepDive/
│       ├── 01-web-servlet-foundation.md
│       ├── 02-spring-core.md
│       ├── 03-spring-mvc-boot.md
│       ├── 04-jpa-transactions.md
│       └── 05-spring-security-jwt.md
│
├── Patterns/                        ← Phase 5: Design patterns (5 notes)
│   ├── builder-pattern.md
│   ├── factory-patterns.md
│   ├── structural-patterns-proxy-decorator-adapter.md
│   ├── behavioral-strategy-chain.md
│   └── behavioral-observer-events.md
│
└── Reference/                       ← Phase 5: Quick-lookup sheets (5 notes)
    ├── collections-api-reference.md
    ├── stream-operations-reference.md
    ├── concurrency-utilities-reference.md
    ├── spring-annotations-reference.md
    └── jvm-flags-reference.md
```

---

## ☕ Core Java — Language Fundamentals

> **Read order:** HashMap → equals/hashCode → Generics → Functional Interfaces → Java Evolution → String → Exceptions. Each builds on the previous.

| # | Note | One-line summary | Read time |
|---|---|---|---|
| 1 | [HashMap Internals](DeepDive/CoreJava/hashmap-internals.md) | Bucket array, perturbation, treeification (Java 8), resize, power-of-2 capacity | 25 min |
| 2 | [equals() / hashCode() Contract](DeepDive/CoreJava/equals-hashcode-contract.md) | The contract, getClass vs instanceof, JPA entity pattern, mutable key trap | 20 min |
| 3 | [Generics + Type Erasure](DeepDive/CoreJava/generics-type-erasure.md) | Erasure mechanics, PECS wildcards, bridge methods, generic array prohibition | 25 min |
| 4 | [Functional Interfaces](DeepDive/CoreJava/functional-interfaces.md) | 4 core shapes (Predicate/Function/Consumer/Supplier), lambda JVM mechanics, composition | 20 min |
| 5 | [Java 8→21 Evolution](DeepDive/CoreJava/java-version-evolution.md) | WHY each feature was added: lambdas → var → records → sealed → virtual threads | 20 min |
| 6 | [String Internals](DeepDive/CoreJava/string-internals.md) | Immutability (3 reasons), string pool, compact strings (Java 9), hashCode caching | 15 min |
| 7 | [Exception Hierarchy](DeepDive/CoreJava/exception-hierarchy.md) | Checked vs unchecked design reasoning, try-with-resources, suppressed exceptions, @Transactional rollback | 20 min |
| — | [Pass-by-Value Semantics](DeepDive/CoreJava/java-pass-by-value-semantics.md) | Reference value passing, mutation vs reassignment, defensive copies | 15 min |

---

## 🔄 Streams & Functional Programming

> **Read order:** Stream Pipeline → Collectors → Optional → CompletableFuture. Streams first (the pipeline model), then the tools built on it.

| # | Note | One-line summary | Read time |
|---|---|---|---|
| 8 | [Stream Pipeline Internals](DeepDive/StreamsFunctional/stream-pipeline-internals.md) | Lazy evaluation, loop fusion, Spliterator, stateful ops (sorted as barrier), parallel pitfalls | 25 min |
| 9 | [Collectors Deep-Dive](DeepDive/StreamsFunctional/collectors-deepdive.md) | groupingBy + downstream, teeing (Java 12), toMap duplicate trap, custom Collector 4 components | 20 min |
| 10 | [Optional — Proper Usage](DeepDive/StreamsFunctional/optional-proper-usage.md) | Return type only (not fields), orElse vs orElseGet (eager vs lazy), isPresent+get anti-pattern | 15 min |
| 11 | [CompletableFuture](DeepDive/StreamsFunctional/completable-future.md) | thenApply vs thenCompose, thenCombine, exceptionally, commonPool trap, join vs get | 20 min |

---

## 🌱 Spring Framework

> **Read order:** Chapters 01 → 02 → 03 → 04 → 05. Strict sequence — each chapter's mental model depends on the previous.

| # | Chapter | One-line summary | Read time |
|---|---|---|---|
| — | [01 — Web + Servlet Foundation](Spring/DeepDive/01-web-servlet-foundation.md) | HTTP, TCP, servlet container, Servlet API, DispatcherServlet as a servlet | 25 min |
| — | [02 — Spring Core (IoC, DI, AOP)](Spring/DeepDive/02-spring-core.md) | ApplicationContext, bean lifecycle, @PostConstruct, CGLIB proxy, self-call trap | 30 min |
| 12 | [03 — MVC + Boot Auto-Config](Spring/DeepDive/03-spring-mvc-boot.md) | DispatcherServlet flow, @RestController, @SpringBootApplication decomposed, profiles, properties | 25 min |
| 13 | [04 — JPA + Transactions](Spring/DeepDive/04-jpa-transactions.md) | REQUIRED vs REQUIRES_NEW, LazyInitializationException, N+1 (3 fixes), rollback rules | 25 min |
| 14 | [05 — Spring Security + JWT](Spring/DeepDive/05-spring-security-jwt.md) | SecurityFilterChain (Spring 6), JWT structure, HS256 vs RS256, revocation strategies, 401 vs 403 | 20 min |

---

## 🧵 Concurrency — Threading & Synchronization

> **Read order:** JMM → synchronized/volatile → Locks → Concurrent Collections → ThreadPool → Sync Aids → Fork/Join → Virtual Threads. JMM is the foundation; everything else builds on it.

| # | Note | One-line summary | Read time |
|---|---|---|---|
| 15 | [Java Memory Model (JMM)](DeepDive/Concurrency/java-memory-model.md) | Happens-before (6 rules), visibility, CPU cache problem, volatile piggybacking, data races | 25 min |
| 16 | [synchronized + volatile](DeepDive/Concurrency/synchronized-volatile.md) | 3 guarantees of synchronized, monitorenter/monitorexit, CAS, AtomicInteger, LongAdder | 25 min |
| 17 | [Locks (ReentrantLock, ReadWriteLock, StampedLock)](DeepDive/Concurrency/locks-reentrant-readwrite.md) | tryLock, fairness, Condition objects, optimistic reads, deadlock prevention | 25 min |
| 18 | [Concurrent Collections](DeepDive/Concurrency/concurrent-collections.md) | CHM per-bin locking, no-null rationale, CopyOnWriteArrayList, BlockingQueue family | 25 min |
| 19 | [ThreadPoolExecutor](DeepDive/Concurrency/thread-pool-executor.md) | 7 parameters, task flow (core → queue → max → reject), CallerRunsPolicy, pool sizing | 25 min |
| 20 | [Synchronization Aids](DeepDive/Concurrency/synchronization-aids.md) | CountDownLatch (one-shot), CyclicBarrier (reusable), Semaphore (permits), Phaser, AQS | 20 min |
| 21 | [Fork/Join + Parallel Streams](DeepDive/Concurrency/fork-join-parallel-streams.md) | Work-stealing, RecursiveTask, Spliterator quality, 5 anti-patterns for parallel | 20 min |
| 22 | [Virtual Threads (Java 21)](DeepDive/Concurrency/virtual-threads-java21.md) | Carrier/VT relationship, pinning (synchronized), Spring Boot 3.2+ integration, ScopedValue | 20 min |

---

## ⚙️ JVM Internals

> **Read order:** Class Loading → Memory Areas → GC → JIT. Class loading explains what goes into Metaspace. Memory areas explain what GC operates on. JIT explains how bytecode becomes native code.

| # | Note | One-line summary | Read time |
|---|---|---|---|
| 23 | [Class Loading + ClassLoader](DeepDive/JVMInternals/class-loading.md) | 3 phases, parent delegation, class identity (name + ClassLoader), hot-reloading, ClassLoader leaks | 20 min |
| 24 | [JVM Memory Areas](DeepDive/JVMInternals/jvm-memory-areas.md) | Heap (Eden/Survivor/Old), Metaspace, Stack, Code Cache, TLAB, OOM diagnosis table | 20 min |
| 25 | [GC Deep-Dive](DeepDive/JVMInternals/gc-deep-dive.md) | Mark-sweep-compact, GC roots, G1 vs ZGC, GC log reading, memory leak detection workflow | 25 min |
| 26 | [JIT Compilation](DeepDive/JVMInternals/jit-compilation.md) | Tiered compilation (C1→C2), inlining, escape analysis, deoptimization, Code Cache, JMH | 20 min |

---

## 🧩 Design Patterns

> **No strict read order.** Each pattern is self-contained. Read by need.

| # | Note | One-line summary |
|---|---|---|
| 27 | [Builder](Patterns/builder-pattern.md) | Fluent construction for objects with many optional fields. Immutable result. |
| 28 | [Factory + Abstract Factory](Patterns/factory-patterns.md) | Encapsulate `new`. Static factory method, factory method (GoF), abstract factory (families). |
| 29 | [Proxy vs Decorator vs Adapter](Patterns/structural-patterns-proxy-decorator-adapter.md) | Same structure, different intent: control access / add behavior / convert interface. |
| 30 | [Strategy + Chain of Responsibility](Patterns/behavioral-strategy-chain.md) | Swap algorithms (Strategy) vs pipeline of handlers (Chain). |
| 31 | [Observer + Event-Driven](Patterns/behavioral-observer-events.md) | Publisher-subscriber. Spring @EventListener, @TransactionalEventListener(AFTER_COMMIT). |

---

## ⚡ Quick Reference Sheets

> **Lookup tables.** No deep explanations — see the corresponding DeepDive note for internals.

| # | Reference | What to look up |
|---|---|---|
| 32 | [Collections API](Reference/collections-api-reference.md) | Which List/Set/Map/Queue to use. Big-O, thread-safety, decision flowchart. |
| 33 | [Stream Operations](Reference/stream-operations-reference.md) | Intermediate/terminal ops, Collectors table, creation methods, common patterns. |
| 34 | [Concurrency Utilities](Reference/concurrency-utilities-reference.md) | "Which tool for which problem?" decision table, Atomic classes, Lock comparison, happens-before. |
| 35 | [Spring Annotations](Reference/spring-annotations-reference.md) | All common annotations grouped by concern, one-line semantics, gotcha per annotation. |
| 36 | [JVM Flags](Reference/jvm-flags-reference.md) | 5 non-negotiable production flags, GC tuning, diagnostics, container flags, jcmd commands. |

---

## 🆕 Phase 6 — Gap Closure Notes (Staff-Level)

> Added after gap analysis. These fill the holes that Phases 1–5 didn't cover.

| # | Note | One-line summary | Read time |
|---|---|---|---|
| 37 | [Testing Strategy](DeepDive/CoreJava/testing-strategy.md) | Test pyramid, JUnit 5, Mockito, @WebMvcTest vs @DataJpaTest, Testcontainers, WireMock | 25 min |
| 38 | [SQL & Database Internals](DeepDive/CoreJava/sql-database-internals.md) | EXPLAIN ANALYZE, B-tree indexes, composite index ordering, HikariCP, slow query diagnosis | 25 min |
| 39 | [Kafka for Java](DeepDive/StreamsFunctional/kafka-java-patterns.md) | Spring Kafka, @KafkaListener, consumer groups, offsets, DLQ, exactly-once | 25 min |
| 40 | [JVM Troubleshooting Playbook](DeepDive/JVMInternals/jvm-troubleshooting.md) | Thread dump → heap dump → GC log → flame graph decision tree. jstack, jmap, MAT, async-profiler | 25 min |
| 41 | [Resilience4j Patterns](DeepDive/CoreJava/resilience4j-patterns.md) | CircuitBreaker, Retry, RateLimiter, Bulkhead, TimeLimiter with Spring Boot | 20 min |
| 42 | [Serialization & API Contracts](DeepDive/CoreJava/serialization-api-contracts.md) | Jackson config, DTOs vs entities, @JsonProperty, API versioning, backward compat | 20 min |

---

## 📊 Stats

| Metric | Value |
|---|---|
| Total notes | 42 (+ 2 pre-existing Spring chapters + pass-by-value) |
| Deep-dives | 32 |
| Patterns | 5 |
| Reference sheets | 5 |
| Topic groups | 8 (Core Java, Streams, Spring, Concurrency, JVM, Patterns, Reference, Gap Closure) |
| Estimated total read time | ~14.5 hours (full pass) |
| Writing standard | `DeepDive/notes-standards-deepdive.md` (10-section template, 7 standards) |

---

## 🧭 Reading Paths

### "I have 2 hours before an interview" path:
1. `Reference/collections-api-reference.md` (10 min)
2. `Reference/concurrency-utilities-reference.md` (10 min)
3. `Reference/spring-annotations-reference.md` (10 min)
4. Read the **TL;DR** + **Say It in 60 Seconds** sections of: HashMap, equals/hashCode, @Transactional (Chapter 04), CompletableFuture, ThreadPoolExecutor, GC (90 min)

### "I'm a mid-level engineer becoming senior" path:
1. All Core Java notes in order (Phase 1)
2. All Streams notes in order (Phase 2)
3. Spring Chapters 01–05 in order (Phase 3)
4. Then Concurrency + JVM as needed

### "I'm preparing for staff-level / top product company" path:
1. All Core Java + Streams + Spring (Phases 1-3) — foundation
2. All Concurrency notes in order (JMM → synchronized → Locks → CHM → ThreadPool → Virtual Threads)
3. All JVM Internals (Class Loading → Memory → GC → JIT → Troubleshooting Playbook)
4. Phase 6 gap notes: Testing Strategy, SQL Internals, Kafka, Resilience4j, Serialization
5. Patterns + Reference sheets for revision

### "I'm debugging a production issue" path:
- **Start here:** `JVMInternals/jvm-troubleshooting.md` — the decision tree
- OOM → `JVMInternals/jvm-memory-areas.md` + `JVMInternals/gc-deep-dive.md`
- Thread hang / deadlock → `Concurrency/locks-reentrant-readwrite.md` (Appendix: Deadlock Detection)
- Slow under load → `Concurrency/thread-pool-executor.md` + `Concurrency/virtual-threads-java21.md`
- GC pauses → `JVMInternals/gc-deep-dive.md` + `Reference/jvm-flags-reference.md`
- Slow queries → `CoreJava/sql-database-internals.md`
- Cascade failure → `CoreJava/resilience4j-patterns.md`
- Kafka consumer lag → `StreamsFunctional/kafka-java-patterns.md`

### "Immutability" cross-cutting reading path:
> Immutability is covered across 6 notes — not as a standalone topic.
1. `CoreJava/string-internals.md` — why String is immutable (3 reasons: security, HashMap, thread safety)
2. `CoreJava/java-pass-by-value-semantics.md` — defensive copies, unmodifiable collections
3. `CoreJava/equals-hashcode-contract.md` — records auto-generate equals/hashCode (immutable by default), JPA entity pattern
4. `CoreJava/java-version-evolution.md` — records (Java 16), sealed classes (Java 17)
5. `CoreJava/hashmap-internals.md` — mutable key trap (why keys must be immutable)
6. `Concurrency/synchronized-volatile.md` — immutability as thread-safety strategy (no synchronization needed)

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Index created. 36 notes organized into 4 DeepDive subfolders (CoreJava, StreamsFunctional, Concurrency, JVMInternals) + Spring track + Patterns + Reference. Reading paths added for interview prep, career growth, and production debugging. |
| Sep 2026 | Phase 6 gap closure: 6 new notes added (Testing, SQL, Kafka, Troubleshooting, Resilience4j, Serialization). Total: 42 notes. Staff-level reading path added. Immutability cross-cutting path added. Production debugging path expanded with troubleshooting playbook as entry point. |
