# ⚙️ JVM Troubleshooting Playbook — Deep Dive

> After this note you can diagnose any production JVM issue using a 5-step decision tree: identify the symptom → pick the right tool → collect data → analyze → fix. Covers: thread dumps (deadlocks, thread starvation), heap dumps (memory leaks), GC logs (latency spikes), flame graphs (CPU hotspots), and the exact commands for each.

---

## 🎯 The Problem This Solves

Your service is "slow." Is it: a deadlock? GC thrashing? A thread pool exhausted? A memory leak? A CPU-bound loop? A blocked I/O call? Without a systematic diagnosis workflow, engineers guess — restart the service, increase heap, add threads — and the problem returns. This note is the playbook: symptom → tool → data → diagnosis → fix.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Thread dump** | A snapshot of every thread's state and stack trace at a single point in time. Shows what each thread is doing (running, waiting, blocked). Captures with `jstack` or `jcmd Thread.print`. |
| **Heap dump** | A snapshot of every object on the heap — what objects exist, how much memory each retains, and who references them. Captures with `jmap` or `-XX:+HeapDumpOnOutOfMemoryError`. Analyzed with Eclipse MAT. |
| **GC log** | A record of every GC event: when, how long, how much memory recovered. Enables: detecting GC thrashing, sizing the heap, choosing the right GC. Captured with `-Xlog:gc*`. |
| **Flame graph** | A visualization of CPU time spent in each method. Wide bars = hot methods. Stack depth = call chain. Generated from profiler output (async-profiler, JFR). |
| **async-profiler** | A low-overhead sampling profiler for the JVM. Produces flame graphs. < 1% overhead in production. Can profile CPU, allocation, lock contention. |
| **JFR (JDK Flight Recorder)** | A built-in always-on profiler in the JDK. < 1% overhead. Records: method profiling, GC events, I/O, thread events, exceptions. Analyzed with JDK Mission Control (JMC). |

---

## 🧠 Mental Model

Production troubleshooting is **medical diagnosis**:
- **Symptom:** service is slow → **Triage:** is it CPU, memory, I/O, or threads?
- **Vital signs:** thread dump (who's blocked?), heap dump (what's using memory?), GC log (how often is GC pausing?), CPU profile (what method is burning CPU?)
- **Each tool answers ONE question.** Using the wrong tool wastes time. Thread dump can't find a memory leak. Heap dump can't find a deadlock. GC log can't find a CPU hotspot.

> If you can say "slow service → 4 possible causes (CPU, memory, threads, GC) → thread dump for blocked threads/deadlocks, heap dump for memory leaks, GC log for pause-time issues, flame graph for CPU hotspots — pick the right tool for the symptom" without notes, you have the playbook.

---

## 🎨 Visual — The Troubleshooting Decision Tree

```
  SERVICE IS SLOW OR UNRESPONSIVE
       │
       ├── CPU at 100%?
       │    YES → FLAME GRAPH (async-profiler or JFR)
       │          → find the hot method burning CPU
       │          → common: regex, serialization, tight loop, GC
       │
       ├── CPU normal but latency high?
       │    YES → THREAD DUMP (jstack or jcmd)
       │          → look for BLOCKED/WAITING threads
       │          → common: deadlock, lock contention, pool exhaustion,
       │                     waiting for external service
       │
       ├── Memory growing / OOM?
       │    YES → HEAP DUMP (jmap or -XX:+HeapDumpOnOutOfMemoryError)
       │          → open in Eclipse MAT → Dominator Tree → largest retained objects
       │          → common: cache without eviction, listener leak, ClassLoader leak
       │
       └── Latency spikes every N seconds?
            YES → GC LOG (-Xlog:gc*)
                  → look for long STW pauses, frequent Full GC, GC thrashing
                  → common: heap too small, memory leak filling Old Gen,
                            allocation rate too high

KEY INVARIANT:
   Each symptom maps to ONE primary diagnostic tool.
   Using the right tool first saves hours.
   Collect data BEFORE restarting — a restart destroys the evidence.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```bash
# "Just restart it" — the anti-diagnosis
kubectl delete pod order-service-abc123
# Service recovers. Problem returns in 4 hours. No data collected. No root cause found.
# Repeated 3 times this week.

# "Just increase the heap"
# -Xmx2g → -Xmx4g → -Xmx8g
# Each increase buys time. The leak is still there. GC pauses get LONGER with bigger heap.
```

---

### Level 2 — The real mechanism

#### 2.1 — Thread Dump Analysis (blocked threads, deadlocks)

```bash
# Capture thread dump:
jstack <pid> > thread-dump.txt
# OR:
jcmd <pid> Thread.print > thread-dump.txt
# OR in Kubernetes:
kubectl exec <pod> -- jstack 1 > thread-dump.txt

# Capture 3 dumps 5 seconds apart (shows progression):
for i in 1 2 3; do jstack <pid> > dump-$i.txt; sleep 5; done
```

**Reading the thread dump:**

```
  THREAD STATES:

  RUNNABLE       → actively executing (or ready to execute on CPU)
  BLOCKED        → waiting to enter a synchronized block (another thread holds the monitor)
  WAITING        → waiting indefinitely (Object.wait(), LockSupport.park(), Thread.join())
  TIMED_WAITING  → waiting with timeout (Thread.sleep(), Object.wait(timeout))

  WHAT TO LOOK FOR:

  1. DEADLOCK (jstack auto-detects and prints at the end):
     "Found one Java-level deadlock:"
     Thread A: locked 0x000000076ab12345, waiting for 0x000000076ab67890
     Thread B: locked 0x000000076ab67890, waiting for 0x000000076ab12345
     FIX: enforce lock ordering (always acquire locks in same order)

  2. THREAD POOL EXHAUSTION:
     200 threads in WAITING state at: java.util.concurrent.ThreadPoolExecutor$Worker.run
     → waiting for tasks = pool is idle (not the problem)
     200 threads in BLOCKED or TIMED_WAITING at: com.zaxxer.hikari.pool.HikariPool.getConnection
     → waiting for DB connections = connection pool exhausted
     FIX: increase pool size OR fix slow queries holding connections

  3. LOCK CONTENTION:
     50 threads BLOCKED at the SAME synchronized block:
     - waiting to lock <0x000000076ab12345> (a java.lang.Object)
     - locked by: "order-processor-3" at com.walmart.OrderCache.get(OrderCache.java:42)
     → one thread holds the lock, 50 others wait
     FIX: reduce lock scope, use ConcurrentHashMap, use ReadWriteLock

  4. EXTERNAL CALL HANG:
     Thread in RUNNABLE at: java.net.SocketInputStream.socketRead0(Native Method)
     → blocked on network I/O (HTTP call to external service not responding)
     FIX: add timeout to HTTP client, add circuit breaker
```

#### 2.2 — Heap Dump Analysis (memory leaks)

```bash
# Capture heap dump (live objects only — triggers GC first):
jmap -dump:live,format=b,file=heap.hprof <pid>

# Or automatically on OOM (MUST be configured before the crash):
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/dumps/

# In Kubernetes:
kubectl exec <pod> -- jmap -dump:live,format=b,file=/tmp/heap.hprof 1
kubectl cp <pod>:/tmp/heap.hprof ./heap.hprof

# Quick look without full dump:
jmap -histo <pid> | head -20
# Shows: class name, instance count, total bytes
# Top entries by bytes = likely suspects
```

**Analyzing with Eclipse MAT (Memory Analyzer Tool):**

```
  WORKFLOW:
  1. Open heap.hprof in Eclipse MAT
  2. Click "Leak Suspects Report" → MAT's automated analysis
     → shows: "X instances of ClassY retain Z MB" with GC root path

  3. Open "Dominator Tree" → sorts objects by RETAINED SIZE
     → retained size = memory freed if THIS object is garbage-collected
     → the top entry is the object holding the most memory transitively

  4. Right-click the suspect → "Path to GC Roots" → "exclude weak references"
     → shows: WHY the object is alive (the chain from GC root to the leak)
     → common: static Map → Entry → leaked object

  5. The fix depends on the root path:
     - Static Map without eviction → add TTL/max-size (Caffeine)
     - Listener registered but never deregistered → deregister in @PreDestroy
     - ThreadLocal not removed → add remove() in finally
     - ClassLoader leak → clean up in ServletContextListener.contextDestroyed()
```

#### 2.3 — GC Log Analysis (latency spikes)

```bash
# Enable GC logging:
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m

# Key metrics to extract:
# 1. Pause time per GC event (STW duration)
# 2. GC frequency (how often GC runs)
# 3. Heap usage before/after GC (how much was recovered)
# 4. Ratio of time in GC vs application time
```

**GC log patterns and diagnosis:**

```
  PATTERN 1: Frequent Minor GC, short pauses → NORMAL
  GC(42) Pause Young (Normal) 256M→0B  real=0.01 secs
  → Eden fills quickly, most objects die young. Healthy.

  PATTERN 2: Frequent Full GC, long pauses, little recovery → MEMORY LEAK
  GC(100) Pause Full (Allocation Failure) 3800M→3750M(4096M) real=2.5 secs
  GC(101) Pause Full (Allocation Failure) 3780M→3760M(4096M) real=2.8 secs
  → Full GC recovers only 40-50 MB → almost everything is live → LEAK
  → Old Gen is 97% full and growing → objects are reachable but not needed
  → ACTION: take heap dump, find the leak

  PATTERN 3: Long GC pauses, infrequent → HEAP TOO LARGE for G1
  GC(5) Pause Full 30G→15G(32G) real=12.5 secs
  → 32 GB heap, Full GC takes 12 seconds → STW pause kills latency
  → ACTION: switch to ZGC (sub-ms pauses) or reduce heap + optimize

  PATTERN 4: "GC overhead limit exceeded" → GC THRASHING
  → JVM spending >98% of time in GC, recovering <2% of heap
  → Effectively: the heap is full, GC can't free anything, app barely runs
  → ACTION: same as Pattern 2 — find the leak
```

#### 2.4 — CPU Profiling (flame graphs)

```bash
# async-profiler (best for production — < 1% overhead):
# Download from: https://github.com/async-profiler/async-profiler

# CPU profile for 30 seconds → flame graph:
./asprof -d 30 -f profile.html <pid>
# Opens as an interactive SVG flame graph in browser

# JDK Flight Recorder (built-in, always available):
jcmd <pid> JFR.start settings=profile duration=60s filename=recording.jfr
# Analyze: open recording.jfr in JDK Mission Control (JMC)

# In Kubernetes:
kubectl exec <pod> -- jcmd 1 JFR.start settings=profile duration=30s filename=/tmp/rec.jfr
kubectl cp <pod>:/tmp/rec.jfr ./recording.jfr
```

**Reading a flame graph:**

```
  FLAME GRAPH (bottom = entry point, top = leaf methods):

  ┌──────────────────────────────────────────────────────────┐
  │                    main()                                │  100% of samples
  ├──────────────────────────┬───────────────────────────────┤
  │   handleRequest()        │    gcThread()                 │  60% + 40%
  ├──────────────┬───────────┤                               │
  │ parseJSON()  │ queryDB() │                               │
  │   30%        │   30%     │                               │
  ├──────────────┤           │                               │
  │ regex match  │           │                               │
  │   25%        │           │                               │
  └──────────────┘           │                               │

  READING:
  - Width = percentage of CPU time in that method (including callees)
  - parseJSON() takes 30% of total CPU — and 25% is in regex matching
  - FIX: the regex is the hotspot. Precompile it or replace with indexOf()
  - gcThread() at 40% → GC is consuming 40% of CPU → heap issue

  RULES:
  - Wide bars at the TOP (leaf) = hot methods = optimize these
  - Narrow bars at the TOP = called rarely = ignore
  - gcThread at > 10% = GC is a problem = fix heap/leak first
```

#### 2.5 — The complete toolbox

| Tool | What it shows | When to use | Command |
|---|---|---|---|
| `jstack` | Thread states + stack traces | Blocked threads, deadlocks | `jstack <pid>` |
| `jcmd Thread.print` | Same as jstack (preferred) | Alternative to jstack | `jcmd <pid> Thread.print` |
| `jmap -dump` | Heap dump | Memory leak diagnosis | `jmap -dump:live,format=b,file=heap.hprof <pid>` |
| `jmap -histo` | Class histogram (quick) | Quick memory overview | `jmap -histo <pid> \| head -20` |
| `jcmd VM.native_memory` | Native memory breakdown | Off-heap memory issues | `jcmd <pid> VM.native_memory` (requires `-XX:NativeMemoryTracking=summary`) |
| `jcmd GC.heap_info` | Heap region details | GC tuning | `jcmd <pid> GC.heap_info` |
| `jcmd Compiler.codecache` | JIT code cache usage | Code cache exhaustion | `jcmd <pid> Compiler.codecache` |
| `jcmd JFR.start` | Start Flight Recorder | CPU/memory/I/O profiling | `jcmd <pid> JFR.start duration=60s filename=rec.jfr` |
| async-profiler | Flame graph | CPU hotspot identification | `./asprof -d 30 -f profile.html <pid>` |
| Eclipse MAT | Heap dump analysis | Memory leak root cause | Open .hprof file, Leak Suspects + Dominator Tree |
| GC log | GC events + pause times | Latency spike diagnosis | `-Xlog:gc*:file=gc.log:time,tags` |

---

### Level 3 — The subtleties

#### 3.1 — Collecting evidence BEFORE the restart

```
  GOLDEN RULE: collect data BEFORE restarting.
  A restart destroys: thread state, heap contents, in-memory caches, connection states.
  Once restarted, you can never reproduce the exact state that caused the issue.

  PRODUCTION INCIDENT CHECKLIST:
  1. Take 3 thread dumps 5 seconds apart (shows thread state progression)
  2. Take a heap dump (if memory-related)
  3. Copy GC logs (should already be persisting to disk)
  4. Start a JFR recording (if CPU-related)
  5. Check application metrics (Prometheus/Grafana — request rate, error rate, latency percentiles)
  6. THEN restart if needed for immediate recovery
  7. Analyze the collected data to find root cause
```

#### 3.2 — Diagnosing in Kubernetes (where you can't SSH)

```bash
# Thread dump:
kubectl exec <pod> -- jcmd 1 Thread.print > thread-dump.txt

# Heap dump:
kubectl exec <pod> -- jmap -dump:live,format=b,file=/tmp/heap.hprof 1
kubectl cp <pod>:/tmp/heap.hprof ./heap.hprof

# JFR recording:
kubectl exec <pod> -- jcmd 1 JFR.start duration=30s filename=/tmp/rec.jfr
sleep 35
kubectl cp <pod>:/tmp/rec.jfr ./recording.jfr

# GC logs (if persisted to a volume):
kubectl cp <pod>:/var/log/gc.log ./gc.log

# ⚠️ Heap dump size = heap size. -Xmx4g → 4 GB dump file.
# Ensure the pod has enough ephemeral storage (/tmp).
# Or use: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dumps/
# with a mounted volume for /var/dumps/
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Restarting fixes the problem" | Restarting fixes the SYMPTOM. The root cause (leak, deadlock, slow query) returns. And the restart destroys the evidence you need to diagnose. Collect data first, restart second. |
| "Thread dump shows what's slow" | Thread dump shows what threads are doing AT ONE INSTANT. A method that takes 500ms may not appear if the dump happens during the other 500ms. Take 3 dumps 5 seconds apart — a method that appears in all 3 is consistently slow/blocked. |
| "Heap dump is only useful for OOM" | Heap dumps show memory usage ANYTIME — not just during OOM. If your service uses 3.5 GB of a 4 GB heap, a heap dump shows WHERE the 3.5 GB is going, even without an OOM error. |
| "`-verbose:gc` is sufficient for GC analysis" | `-verbose:gc` shows basic GC events. `-Xlog:gc*` (unified logging, Java 9+) shows: pause duration, heap regions before/after, cause, and age distribution. Always use `-Xlog:gc*` with file rotation. |
| "Profiling slows down the application" | async-profiler and JFR are < 1% overhead. They're designed for production use. The overhead of NOT profiling (guessing, wrong fixes, repeated incidents) is far higher than 1% CPU. |

---

## 🐞 Production Footguns

---

> **Footgun: No heap dump configured before OOM**
> **Cost:** Blind debugging — no data from the crash
>
> Covered in `jvm-memory-areas.md`. The fix: always set `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dumps/` BEFORE production. Without it, OOM is undiagnosable after the restart.

---

> **Footgun: Taking thread dump during healthy state**
> **Cost:** Wasted effort — no useful signal
>
> A developer took a thread dump when the service was responding normally — all threads showed `WAITING` in `ThreadPoolExecutor$Worker.run`. Conclusion: "threads are idle, no problem." The actual problem was intermittent — a 5-second deadlock every 30 minutes. The dump was taken during the 25-second healthy window. Fix: set up continuous monitoring (JFR always-on) or take dumps DURING the incident. In Kubernetes: configure a script that takes a dump when latency exceeds a threshold.

```bash
# Automated thread dump on high latency:
# In a sidecar or monitoring script:
while true; do
    latency=$(curl -s -o /dev/null -w '%{time_total}' http://localhost:8080/health)
    if (( $(echo "$latency > 5.0" | bc -l) )); then
        jcmd 1 Thread.print > /var/dumps/thread-$(date +%s).txt
        jcmd 1 JFR.start duration=30s filename=/var/dumps/jfr-$(date +%s).jfr
    fi
    sleep 10
done
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `jvm-memory-areas.md` | This note explains WHAT each memory area is. The troubleshooting playbook explains HOW to diagnose when each area goes wrong (heap → heap dump, Metaspace → class histogram, stack → thread dump). |
| `gc-deep-dive.md` | GC internals explain WHY GC pauses happen. This note explains HOW to read GC logs and diagnose GC-related performance issues in production. |
| `jit-compilation.md` | Code Cache exhaustion appears as `"CodeCache is full"` in logs + performance degradation. Diagnose with `jcmd Compiler.codecache`. |
| `../Concurrency/locks-reentrant-readwrite.md` | Deadlock theory (4 conditions, prevention strategies). This note shows HOW to detect deadlocks in production (jstack auto-detects and reports them). |
| `../Concurrency/thread-pool-executor.md` | Thread pool exhaustion appears in thread dumps as all threads BLOCKED waiting for a resource (DB connection, lock). Understanding pool sizing explains the root cause. |

---

## 🎙️ Interview Deep Questions

**Q1. Walk me through how you would diagnose a Java service that's running slowly in production.**

> First, identify the symptom type. Check CPU usage: if CPU is at 100%, use async-profiler or JFR to generate a flame graph — find the hot method. If CPU is normal but latency is high, take 3 thread dumps 5 seconds apart — look for threads BLOCKED or WAITING on locks, DB connections, or external calls. If memory is growing or near OOM, take a heap dump and analyze in Eclipse MAT — look at the Dominator Tree for the largest retained objects and trace the GC root path to find why they're alive. If latency spikes periodically, check GC logs — frequent Full GC with little recovery = memory leak. The key: collect data BEFORE restarting. A restart destroys the evidence.

**Q2. How do you detect a deadlock in production?**

> `jstack <pid>` automatically detects Java-level deadlocks and prints them at the end of the thread dump: "Found one Java-level deadlock:" followed by the thread names, the locks they hold, and the locks they're waiting for. For non-deadlock lock contention: look for many threads in `BLOCKED` state all waiting on the same lock object — one thread holds the lock, others queue. Take 3 dumps 5 seconds apart: if the same threads are BLOCKED in all 3 dumps, it's sustained contention, not transient. Fix: reduce synchronized scope, switch to ConcurrentHashMap, use ReadWriteLock, or eliminate the shared mutable state.

**Q3. How do you find a memory leak in a Java application?**

> Step 1: observe that Old Gen usage grows steadily over time (GC log or Prometheus metrics) without Full GC being able to recover it. Step 2: take a heap dump with `jmap -dump:live,format=b,file=heap.hprof <pid>`. Step 3: open in Eclipse MAT, run "Leak Suspects" report. Step 4: examine the Dominator Tree — find the objects retaining the most memory. Step 5: right-click → "Path to GC Roots" (excluding weak references) — this shows the reference chain keeping the object alive. Common root causes: static HashMap/Cache without eviction policy, event listeners registered but never deregistered, ThreadLocals in thread pools not removed. Step 6: fix the root cause (add eviction, deregister listener, call ThreadLocal.remove() in finally).

**Q4. What is a flame graph and how do you read it?**

> A flame graph visualizes where CPU time is spent. The x-axis is NOT time — it's the percentage of samples that include that method. Width = how much CPU time that method (and its callees) consume. The y-axis is the call stack — bottom is the entry point (main), top is the leaf method. Wide bars at the TOP are CPU hotspots — methods that consume significant CPU directly. To diagnose: look for the widest bars at the top of the graph. Common hotspots: regex Pattern.match (use precompiled patterns), JSON serialization (use streaming API), encryption, reflection (cache Method objects). If "GC" threads appear as wide bars (>10% of total width), the problem is memory/GC, not the application code — fix the heap first.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** JVM troubleshooting = matching symptoms to tools. CPU high → flame graph. Latency high → thread dump. Memory growing → heap dump. Periodic spikes → GC log. Each tool answers one question.
>
> **Part 2 — How/Why (30s):** Thread dump (`jstack`): shows every thread's state and stack. Look for BLOCKED threads (lock contention), WAITING threads (pool exhaustion), and jstack's automatic deadlock detection. Heap dump (`jmap`): shows every object on the heap. Open in Eclipse MAT → Dominator Tree → largest retained objects → Path to GC Roots → find why the leak is alive. GC log (`-Xlog:gc*`): look for frequent Full GC with little recovery = memory leak. Flame graph (async-profiler): wide bars at the top = CPU hotspots.
>
> **Part 3 — Gotcha (20s):** Golden rule: collect data BEFORE restarting. A restart destroys thread state, heap contents, and the evidence you need. Take 3 thread dumps 5 seconds apart (shows if threads are consistently stuck). Always have `-XX:+HeapDumpOnOutOfMemoryError` configured before production. async-profiler and JFR are < 1% overhead — safe for production use.

---

## 🧾 TL;DR

- **CPU 100%** → flame graph (async-profiler / JFR). Wide top bars = hotspots.
- **Latency high, CPU normal** → thread dump (jstack). Look for BLOCKED/WAITING.
- **Memory growing / OOM** → heap dump (jmap). Eclipse MAT → Dominator Tree → GC Root Path.
- **Periodic latency spikes** → GC log. Frequent Full GC + little recovery = leak.
- **Always configure BEFORE production:** `-XX:+HeapDumpOnOutOfMemoryError`, `-Xlog:gc*`.
- **Collect data BEFORE restarting.** Restart destroys evidence.
- **3 thread dumps 5s apart** — shows if a thread is consistently stuck, not transiently.
- **async-profiler / JFR:** < 1% overhead. Safe for production. Use them.
- **Eclipse MAT:** Leak Suspects report + Dominator Tree + Path to GC Roots = find the leak.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #40 (Phase 6, Tier 1) of the JavaBackend KB completion roadmap. Staff-level depth: 5-step decision tree (symptom → tool → data → analysis → fix), thread dump analysis (4 thread states, deadlock detection, lock contention, pool exhaustion, external call hang), heap dump analysis (Eclipse MAT workflow — Leak Suspects, Dominator Tree, Path to GC Roots), GC log patterns (4 patterns with diagnosis), flame graph reading (width = CPU%, top bars = hotspots), complete toolbox table (jstack, jmap, jcmd, JFR, async-profiler, MAT), Kubernetes-specific commands, evidence collection checklist. |
