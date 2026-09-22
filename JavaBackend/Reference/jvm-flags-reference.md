# ⚡ JVM Flags — Quick Reference

> **Use:** look up JVM flags for heap sizing, GC selection, diagnostics, and troubleshooting. See `DeepDive/jvm-memory-areas.md` and `DeepDive/gc-deep-dive.md` for internals.

---

## 🔹 The 5 Non-Negotiable Production Flags

```bash
# 1. Heap: set min = max (prevent runtime resize)
-Xms4g -Xmx4g

# 2. GC algorithm
-XX:+UseG1GC                    # default Java 9+, general purpose
# OR
-XX:+UseZGC                     # ultra-low latency (Java 15+)
# OR
-XX:+UseZGC -XX:+ZGenerational  # generational ZGC (Java 21, recommended)

# 3. GC logging (always on — essential for post-incident analysis)
-Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=5,filesize=100m

# 4. Heap dump on OOM (non-negotiable — blind debugging without it)
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/dumps/

# 5. Metaspace cap (prevent unbounded native memory growth)
-XX:MaxMetaspaceSize=256m
```

---

## 🔹 Heap Sizing

| Flag | Default | What it does |
|---|---|---|
| `-Xms<size>` | 1/64 physical RAM | Initial heap size |
| `-Xmx<size>` | 1/4 physical RAM | Maximum heap size |
| `-Xss<size>` | ~1 MB | Thread stack size |
| `-XX:NewRatio=2` | 2 | Old:Young ratio (2 = Old is 2x Young) |
| `-XX:SurvivorRatio=8` | 8 | Eden:Survivor ratio (8 = Eden is 8x each Survivor) |
| `-XX:MaxMetaspaceSize=<size>` | unlimited | Cap Metaspace (native memory for class metadata) |
| `-XX:MaxDirectMemorySize=<size>` | ~= Xmx | Cap direct (off-heap) ByteBuffer memory |
| `-XX:ReservedCodeCacheSize=<size>` | 240 MB | JIT-compiled native code storage |

**Rule:** always set `-Xms = -Xmx` in production. Different values cause heap resize pauses under load.

---

## 🔹 GC Selection

| Flag | GC | Best for | Java version |
|---|---|---|---|
| `-XX:+UseSerialGC` | Serial | Single-core, small apps, containers with 1 CPU | All |
| `-XX:+UseParallelGC` | Parallel | Batch jobs, throughput > latency | Java 8 default (server) |
| `-XX:+UseG1GC` | G1 | General purpose, balanced latency/throughput | Java 9+ default |
| `-XX:+UseZGC` | ZGC | Ultra-low latency (< 1ms pauses) | Java 15+ (production) |
| `-XX:+UseShenandoahGC` | Shenandoah | Low latency (alternative to ZGC, Red Hat) | Java 12+ (not in Oracle JDK) |

---

## 🔹 G1 GC Tuning

| Flag | Default | What it does |
|---|---|---|
| `-XX:MaxGCPauseMillis=200` | 200 ms | Target pause time. Lower = shorter but more frequent pauses. |
| `-XX:G1HeapRegionSize=<size>` | auto (1-32 MB) | Region size. Larger = fewer regions, less overhead, worse granularity. |
| `-XX:G1NewSizePercent=5` | 5% | Minimum Young Gen as % of heap |
| `-XX:G1MaxNewSizePercent=60` | 60% | Maximum Young Gen as % of heap |
| `-XX:InitiatingHeapOccupancyPercent=45` | 45% | Start concurrent marking when Old Gen reaches this % of heap |
| `-XX:G1MixedGCCountTarget=8` | 8 | Number of mixed GC cycles to collect Old regions (higher = more gradual) |
| `-XX:MaxTenuringThreshold=15` | 15 | GC cycles before Young→Old promotion |

---

## 🔹 ZGC Tuning

| Flag | Default | What it does |
|---|---|---|
| `-XX:+UseZGC` | — | Enable ZGC |
| `-XX:+ZGenerational` | on (Java 21) | Enable generational ZGC (reduces CPU overhead) |
| `-XX:SoftMaxHeapSize=<size>` | = Xmx | Soft limit — ZGC tries to stay below this but can exceed |
| `-XX:ConcGCThreads=<n>` | auto | Number of concurrent GC threads |

**ZGC needs minimal tuning** — set `-Xms/-Xmx` and let it auto-tune. The main lever is heap size.

---

## 🔹 GC Logging (Java 9+ Unified Logging)

```bash
# Full GC logging with rotation:
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m

# Minimal GC logging (just pauses):
-Xlog:gc:file=gc.log:time:filecount=3,filesize=50m

# GC + safepoint logging:
-Xlog:gc*,safepoint:file=gc.log:time,tags

# Print to stdout (containers):
-Xlog:gc*::time,tags

# Pre-Java 9 (legacy — don't use for new projects):
# -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log
```

---

## 🔹 Diagnostics

| Flag | What it does |
|---|---|
| `-XX:+HeapDumpOnOutOfMemoryError` | Write heap dump to disk on OOM |
| `-XX:HeapDumpPath=/path/` | Directory for heap dumps |
| `-XX:OnOutOfMemoryError="cmd"` | Run command on OOM (e.g., `"kill -9 %p"` to kill cleanly) |
| `-XX:+PrintFlagsFinal` | Print ALL JVM flags and their values at startup |
| `-XX:NativeMemoryTracking=summary` | Track native (off-heap) memory. Query: `jcmd <pid> VM.native_memory` |
| `-XX:+UnlockDiagnosticVMOptions` | Enable diagnostic flags (required for some advanced flags) |
| `-XX:+FlightRecorder` | Enable JDK Flight Recorder (always-on profiling, < 1% overhead) |
| `-XX:StartFlightRecording=settings=profile,filename=recording.jfr` | Start recording immediately |

---

## 🔹 Performance / JIT

| Flag | Default | What it does |
|---|---|---|
| `-XX:+TieredCompilation` | on | Enable tiered compilation (interpreter → C1 → C2) |
| `-XX:-TieredCompilation` | — | Disable C1, compile directly with C2 (slower startup, max peak) |
| `-XX:CompileThreshold=10000` | 10000 | Invocations before method is compiled (non-tiered mode) |
| `-XX:MaxInlineSize=35` | 35 bytes | Max bytecode size for always-inlined methods |
| `-XX:FreqInlineSize=325` | 325 bytes | Max bytecode size for frequently-inlined methods |
| `-XX:+PrintCompilation` | off | Print each method as it's compiled |
| `-XX:+TraceDeoptimization` | off | Log deoptimization events |

---

## 🔹 Virtual Threads (Java 21)

| Flag | What it does |
|---|---|
| `-Djdk.tracePinnedThreads=full` | Log virtual thread pinning events (synchronized blocking I/O) |
| `-Djdk.tracePinnedThreads=short` | Log pinning with short stack traces |
| `-Djdk.virtualThreadScheduler.parallelism=<n>` | Override carrier thread count (default: availableProcessors) |
| `-Djdk.virtualThreadScheduler.maxPoolSize=<n>` | Max carrier threads |

---

## 🔹 Container-Specific (Docker/Kubernetes)

```bash
# Java 10+ automatically detects container CPU/memory limits.
# Override if needed:

# Use container CPU limit for availableProcessors():
-XX:ActiveProcessorCount=4       # force 4 CPUs (overrides detection)

# Use container memory limit for heap sizing:
-XX:MaxRAMPercentage=75.0        # use 75% of container memory for heap
-XX:InitialRAMPercentage=75.0    # same for initial size
# Better than -Xmx for containers — adapts to the container's memory limit

# ⚠️ Leave 25% for Metaspace + stacks + native memory + OS.
# Setting MaxRAMPercentage=100 → OOM killed by container runtime.
```

---

## 🔹 Troubleshooting Commands

```bash
# Thread dump (find deadlocks, blocked threads):
jstack <pid>
# OR: kill -3 <pid>   (sends SIGQUIT → thread dump to stdout)

# Heap dump (find memory leaks):
jmap -dump:live,format=b,file=heap.hprof <pid>

# Class histogram (quick memory overview):
jmap -histo <pid> | head -20

# JVM info (flags, memory, etc.):
jcmd <pid> VM.info
jcmd <pid> VM.flags               # all active flags
jcmd <pid> VM.native_memory       # native memory breakdown (needs NMT)
jcmd <pid> GC.heap_info           # heap layout
jcmd <pid> Compiler.codecache     # JIT code cache usage
jcmd <pid> Thread.print           # thread dump (same as jstack)

# Flight Recorder (low-overhead profiling):
jcmd <pid> JFR.start settings=profile duration=60s filename=recording.jfr
jcmd <pid> JFR.stop name=1        # stop and save
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #36 (Phase 5). 5 non-negotiable production flags, heap/GC/JIT/diagnostics/container flag tables, G1 + ZGC tuning, GC logging formats, virtual thread flags, troubleshooting commands (jstack, jmap, jcmd, JFR). |
