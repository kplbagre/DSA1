# 🧵 Concurrent Collections Internals — Deep Dive

> After this note you can explain how ConcurrentHashMap achieves lock-free reads, why it prohibits null keys/values, how CopyOnWriteArrayList works (and when it becomes a memory bomb), and when to use BlockingQueue vs ConcurrentLinkedQueue.

---

## 🎯 The Problem This Solves

You wrap a `HashMap` in `Collections.synchronizedMap()`. Every `get()` and `put()` acquires a single global lock. Under 100 concurrent threads, only ONE thread reads or writes at a time — the lock becomes a serial bottleneck. Throughput collapses. You need data structures designed for concurrency from the ground up — not wrappers around single-threaded ones.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **ConcurrentHashMap** | A thread-safe hash map that uses CAS + per-bin `synchronized` (Java 8+). Reads are lock-free. Writes lock only the affected bin, not the entire map. Prohibits null keys and values. |
| **CopyOnWriteArrayList** | A thread-safe list where every mutation (add, set, remove) creates a new copy of the underlying array. Reads are lock-free (read the current snapshot). Ideal for read-heavy, write-rare scenarios (e.g., listener lists). |
| **BlockingQueue** | A queue that blocks on `take()` when empty (consumer waits) and on `put()` when full (producer waits). Foundation of the producer-consumer pattern. Implementations: `ArrayBlockingQueue` (bounded), `LinkedBlockingQueue` (optionally bounded), `PriorityBlockingQueue` (priority-ordered). |
| **ConcurrentLinkedQueue** | A non-blocking, lock-free queue using CAS. `poll()` returns null if empty (doesn't block). Unbounded. Use when blocking is undesirable. |
| **Weakly consistent iterator** | ConcurrentHashMap iterators reflect the state at some point during or since the iterator's creation. They NEVER throw `ConcurrentModificationException`. They may or may not reflect concurrent modifications. |

---

## 🧠 Mental Model

`synchronizedMap` is a library with **one door and one key** — everyone queues to enter. `ConcurrentHashMap` is a library with **16+ doors** (bins) — readers walk in freely, writers lock only their assigned door. Other doors stay open. Under high load, 16 doors is 16x the throughput of 1 door.

`CopyOnWriteArrayList` is a **bulletin board with laminated sheets**. Reading: just look at the sheet (no lock). Writing: photocopy the entire sheet, make your change on the copy, then swap the new sheet for the old one (atomic reference swap). Cheap reads. Expensive writes. Good when 99% of access is reading.

> If you can say "CHM: lock-free reads (volatile node array), per-bin synchronized writes, no nulls; COWAL: copy-on-write snapshot — readers see a consistent snapshot, writers copy the array; BlockingQueue: put blocks when full, take blocks when empty — producer-consumer" without notes, you have concurrent collections.

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// synchronizedMap: single global lock = serial bottleneck
Map<String, Data> cache = Collections.synchronizedMap(new HashMap<>());
// Thread 1: cache.get("key1")   → acquires global lock
// Thread 2: cache.get("key2")   → BLOCKED — waiting for Thread 1's lock
// Even though they're accessing DIFFERENT keys! The lock doesn't know that.
// Under 100 threads: effective parallelism = 1.
// ⚠️ Thread-safe but serial — defeats the purpose of having multiple threads
```

---

### Level 2 — The real mechanism

#### 2.1 — ConcurrentHashMap (Java 8+)

```java
// Tier 1 — Demo: basic ConcurrentHashMap usage
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("orders", 100);
map.get("orders");   // lock-free — volatile read of the bin head
// ✅ Thread-safe — CAS + per-bin synchronized

// Atomic compound operations (not possible with synchronizedMap):
map.putIfAbsent("users", 0);         // atomic: if key absent → put, return null
map.computeIfAbsent("users", k -> expensiveCompute(k));   // compute ONLY if absent
map.merge("orders", 1, Integer::sum);  // atomic: orders = orders + 1 (or put 1 if absent)
```

**Internal structure (Java 8+):**

```
  ConcurrentHashMap internal:
  ┌─────────────────────────────────────────────────────────┐
  │  Node<K,V>[] table   (volatile — visible across threads)│
  │                                                          │
  │  Bin 0: [Node A] → [Node B] → null                      │
  │  Bin 1: null                                             │
  │  Bin 2: [Node C] → null                                  │
  │  Bin 3: [TreeBin] (treeified, same as HashMap)           │
  │  ...                                                     │
  │  Bin 15: [Node D] → null                                 │
  └─────────────────────────────────────────────────────────┘

  READ (get):
  - Volatile read of table[hash & (n-1)] → gets bin head
  - Walk the chain/tree to find the key → NO LOCK acquired
  - Node.val and Node.next are volatile → always see latest
  - Lock-free reads = zero contention for readers

  WRITE (put):
  - CAS on the bin head if bin is empty (fast path — no lock)
  - synchronized(binHead) if bin is occupied (lock ONLY this bin)
  - Other bins remain unlocked → other threads read/write freely
  - Treeification at 8 nodes (same as HashMap)

  RESIZE:
  - Incremental/concurrent — multiple threads help transfer bins
  - Each thread claims a chunk of bins to transfer
  - Reads continue during resize (forwarding nodes redirect)
```

> **What the JVM is actually doing:** `Node.val` and `Node.next` are `volatile` — every read sees the latest write (happens-before via volatile rule). The `table` array itself is `volatile` — resizing publishes the new array atomically. For empty bins, `put()` uses `Unsafe.compareAndSwapObject()` (CAS) to atomically place the first node — no lock needed. For occupied bins, `synchronized(f)` locks only the first node of that bin — O(1) lock granularity. During resize, `ForwardingNode` objects redirect reads to the new table — reads are never blocked by resize.

**Why no null keys or values:**

```java
// CHM prohibits null keys and values. Here's why:
// In a concurrent context:
Integer value = map.get(key);
// If value is null, does it mean:
//   (a) key is absent? or
//   (b) key is present with value null?
// In a single-threaded HashMap: call containsKey() after get() to distinguish.
// In ConcurrentHashMap: between get() and containsKey(), another thread could
// add or remove the key → answer is wrong. There's no way to atomically
// distinguish "absent" from "present with null" in a concurrent map.
// Solution: prohibit null entirely → get()==null ALWAYS means "absent."
```

#### 2.2 — CopyOnWriteArrayList

```java
// Tier 2 — Production: listener list (read-heavy, write-rare)
CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();

// Write: copies entire array (expensive — O(n))
listeners.add(new MyListener());
// Internally: lock → newArray = Arrays.copyOf(current, len+1) → newArray[len] = element → array = newArray → unlock

// Read: no lock, no copy — reads the current snapshot (cheap — O(1) for get)
for (EventListener listener : listeners) {
    listener.onEvent(event);
    // Iterator operates on a SNAPSHOT — concurrent adds/removes don't affect this iteration
    // No ConcurrentModificationException — ever
}
// ✅ Thread-safe — readers see a consistent snapshot; writers copy atomically
```

**When CopyOnWriteArrayList is wrong:**

```java
// ❌ Write-heavy: 10,000 elements, 100 writes/second
// Each write copies 10,000 elements → 1M element copies/second → GC pressure → OOM risk
// Use ConcurrentLinkedDeque, synchronizedList, or redesign

// ✅ Read-heavy: 10,000 elements, 1 write/minute, 10,000 reads/second
// Reads: zero lock overhead. Write: one copy per minute. Perfect.
// Classic use: Spring's list of ApplicationListeners, Servlet filter chains
```

#### 2.3 — BlockingQueue — producer-consumer pattern

```java
// Tier 2 — Production: bounded producer-consumer with ArrayBlockingQueue
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1000);   // bounded capacity

// Producer thread:
public void produce(Task task) throws InterruptedException {
    queue.put(task);   // BLOCKS if queue is full — natural backpressure
    // Producer slows down when consumer can't keep up → no OOM
}

// Consumer thread:
public void consume() throws InterruptedException {
    while (true) {
        Task task = queue.take();   // BLOCKS if queue is empty — waits for work
        process(task);
    }
}
// ✅ Thread-safe — BlockingQueue handles all synchronization internally
```

**Choosing the right BlockingQueue:**

| Implementation | Bounded? | Data structure | Best for |
|---|---|---|---|
| `ArrayBlockingQueue` | Yes (fixed) | Array (circular buffer) | Known capacity, predictable memory |
| `LinkedBlockingQueue` | Optional (`new LBQ(cap)`) | Linked nodes | High throughput (separate put/take locks) |
| `PriorityBlockingQueue` | No (unbounded) | Binary heap | Priority-ordered processing |
| `SynchronousQueue` | Yes (0 capacity) | No internal storage | Direct handoff — put blocks until take |
| `DelayQueue` | No (unbounded) | Heap of Delayed elements | Scheduled/delayed task execution |

#### 2.4 — ConcurrentSkipListMap — concurrent sorted map

```java
// TreeMap is NOT thread-safe. ConcurrentSkipListMap is the concurrent sorted map.
ConcurrentSkipListMap<String, Integer> sortedMap = new ConcurrentSkipListMap<>();
sortedMap.put("banana", 2);
sortedMap.put("apple", 1);
sortedMap.put("cherry", 3);
sortedMap.firstKey();   // "apple" — sorted by natural order
// O(log n) for get/put (skip list, not red-black tree)
// Lock-free reads, CAS-based writes
// ✅ Thread-safe — CAS-based, lock-free reads
```

---

### Level 3 — The subtleties

#### 3.1 — ConcurrentHashMap `size()` is approximate

```java
// size() on ConcurrentHashMap returns an ESTIMATE during concurrent modifications.
// It uses a baseCount + per-cell CounterCell array (similar to LongAdder).
// Accurate when no concurrent writes. Approximate during writes.
// Use mappingCount() (returns long) instead of size() (returns int, capped at MAX_VALUE).
long count = map.mappingCount();   // long — handles >2B entries
```

#### 3.2 — Bulk operations (Java 8)

```java
// ConcurrentHashMap supports parallel bulk operations:
// forEach, search, reduce — all with a parallelism threshold

// Process all entries in parallel if map has > 100 entries:
map.forEach(100, (key, value) -> {
    System.out.println(key + ": " + value);
});

// Search for a value in parallel:
String found = map.search(100, (key, value) -> {
    return value > 1000 ? key : null;   // return non-null to stop
});

// Reduce in parallel:
long sum = map.reduceValuesToLong(100, v -> v, 0L, Long::sum);
```

#### 3.3 — `compute` family — atomic read-modify-write

```java
// computeIfAbsent: compute value ONLY if key is absent (lazy init)
map.computeIfAbsent("config", key -> loadFromDatabase(key));
// If "config" exists → return existing value (loadFromDatabase NOT called)
// If "config" absent → call function, store result, return it
// ENTIRE operation is atomic — no race between check and put

// compute: always compute new value (or remove if function returns null)
map.compute("counter", (key, oldValue) -> oldValue == null ? 1 : oldValue + 1);
// Atomic increment — no CAS retry loop needed

// merge: combine existing value with new value
map.merge("counter", 1, Integer::sum);
// If absent: put("counter", 1). If present: put("counter", old + 1). Atomic.
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "ConcurrentHashMap locks the entire map on writes" | Java 8+ CHM locks ONLY the bin (bucket) head on writes. Empty bins use CAS (no lock at all). Reads are entirely lock-free (volatile node fields). The "segment locking" model was Java 7 — Java 8 replaced it with per-bin synchronized. |
| "`synchronizedMap` is the same as ConcurrentHashMap" | `synchronizedMap` wraps every method in a global `synchronized(mutex)` — one lock for all operations. ConcurrentHashMap uses per-bin locking (writes) and no locking (reads). CHM has dramatically higher throughput under contention. Also: CHM provides atomic `computeIfAbsent`, `merge`, `forEach` — synchronizedMap does not. |
| "CopyOnWriteArrayList is always thread-safe for iteration" | Iteration is safe (snapshot semantics). But the snapshot may be stale — changes made DURING iteration are not visible. If your logic depends on seeing the latest state during iteration, CopyOnWrite is wrong. |
| "BlockingQueue's `offer()` and `add()` are the same" | `add()` throws `IllegalStateException` if the queue is full. `offer()` returns `false`. `put()` BLOCKS until space is available. `offer(timeout)` blocks up to the timeout. Choose based on whether you want exception/boolean/blocking behavior. |
| "`size()` on ConcurrentHashMap is always accurate" | `size()` is approximate during concurrent writes — it uses a distributed counter (CounterCell array). It's exact only when no other thread is modifying the map. For precise size, you'd need to lock the entire map (defeating the purpose). |

---

## 🐞 Production Footguns

---

> **Footgun: check-then-act on ConcurrentHashMap without compute**
> **Cost:** Race condition — duplicate computation
>
> A caching service checked `containsKey()` then called `put()` — two separate operations. Between them, another thread could also check and put — both threads computed the expensive value, wasting resources and potentially overwriting each other.

```java
// ❌ The trap: check-then-act is NOT atomic on CHM
if (!cache.containsKey(key)) {           // Thread 1 checks: absent
    // Thread 2 also checks: absent       // race window
    Data data = expensiveCompute(key);    // BOTH threads compute
    cache.put(key, data);                 // Thread 2 overwrites Thread 1
}

// ✅ The fix: computeIfAbsent is atomic
Data data = cache.computeIfAbsent(key, k -> expensiveCompute(k));
// Only ONE thread computes — others wait for the result
// Entire operation is atomic within the bin lock
```

---

> **Footgun: CopyOnWriteArrayList as a general-purpose list**
> **Cost:** OOM from excessive copying
>
> A message processing service used `CopyOnWriteArrayList` for a work queue with 50,000+ messages. Every `add()` copied the entire 50,000-element array — creating ~2GB of array copies per second. The old arrays piled up faster than GC could collect them → OOM within minutes.

```java
// ❌ The trap: CopyOnWrite for write-heavy workload
CopyOnWriteArrayList<Message> queue = new CopyOnWriteArrayList<>();
while (true) {
    queue.add(receiveMessage());   // copies 50K elements EVERY time
    queue.remove(0);               // copies 50K elements AGAIN
}
// 2 copies × 50K elements × 100 msgs/sec = 10M element copies/sec → OOM

// ✅ The fix: use a concurrent queue for write-heavy
BlockingQueue<Message> queue = new ArrayBlockingQueue<>(50_000);
queue.put(receiveMessage());   // O(1), no copying
queue.take();                  // O(1), blocks when empty
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `hashmap-internals.md` | ConcurrentHashMap's internal structure (bin array, chaining, treeification) mirrors HashMap. Understanding HashMap explains WHY CHM is designed the way it is — it's HashMap + per-bin synchronization + volatile reads. |
| `synchronized-volatile.md` | CHM uses `volatile` for the table array and node fields (lock-free reads) and `synchronized` on bin heads (write exclusivity). Understanding both mechanisms explains CHM's thread-safety model. |
| `java-memory-model.md` | CHM's happens-before guarantees: a `put()` happens-before a subsequent `get()` that sees that value. This is how CHM provides visibility without global locking. |
| `thread-pool-executor.md` (planned — Note #19) | `ThreadPoolExecutor` uses `BlockingQueue` internally for the work queue. The queue type (bounded vs unbounded) directly affects executor behavior (rejection vs OOM). |
| `locks-reentrant-readwrite.md` | `ConcurrentHashMap` is almost always better than `ReadWriteLock` + `HashMap` — it provides finer-grained locking AND atomic compute operations. Don't reinvent CHM with locks. |

---

## 🎙️ Interview Deep Questions

**Q1. How does ConcurrentHashMap achieve thread-safety without locking the entire map?**

> In Java 8+, ConcurrentHashMap uses two techniques: volatile reads for lock-free get() and per-bin synchronized for put(). The `Node[]` table and each `Node.val`/`Node.next` are volatile — reads always see the latest write without acquiring any lock. For writes, CHM synchronizes on the head node of the specific bin being modified — other bins remain unlocked. For empty bins, it uses CAS (compare-and-swap) to place the first node — no lock at all. During resize, `ForwardingNode` objects redirect reads to the new table while transfer is in progress — reads are never blocked. This design gives near-HashMap-level read performance with safe concurrent writes.

**Q2. Why does ConcurrentHashMap prohibit null keys and values?**

> In a concurrent context, `get(key) == null` is ambiguous: does it mean the key is absent, or the key is present with value null? In single-threaded HashMap, you can call `containsKey()` after `get()` to distinguish. In ConcurrentHashMap, another thread can insert or remove the key between your `get()` and `containsKey()` — the answer is unreliable. By prohibiting null, CHM ensures `get(key) == null` ALWAYS means "absent." This eliminates a class of concurrency bugs and makes the API unambiguous.

**Q3. When should you use CopyOnWriteArrayList vs ConcurrentLinkedQueue vs BlockingQueue?**

> `CopyOnWriteArrayList`: read-heavy, write-rare (e.g., listener lists, config lists). Every write copies the entire array — O(n). Reads are lock-free snapshot reads — O(1). Use when writes are infrequent (< 1/second) and the list is small (< 10,000 elements). `ConcurrentLinkedQueue`: non-blocking queue — `poll()` returns null if empty. Use when you don't want to block. Unbounded — risk of OOM if consumer is slower than producer. `BlockingQueue`: `put()` blocks when full, `take()` blocks when empty. Natural backpressure. Use for producer-consumer patterns where you want the producer to slow down when the consumer can't keep up. `ArrayBlockingQueue` for fixed capacity, `LinkedBlockingQueue` for optionally-bounded with separate put/take locks.

**Q4. What is the difference between `computeIfAbsent()` and `putIfAbsent()`?**

> `putIfAbsent(key, value)`: if key is absent, put the value. The value is ALWAYS computed (eagerly) — even if the key is present and the put doesn't happen. Wasteful for expensive values. `computeIfAbsent(key, function)`: if key is absent, call the function to compute the value, then put it. The function is called ONLY if the key is absent (lazily). The ENTIRE operation (check + compute + put) is atomic within the bin lock — no race condition. For caching patterns, `computeIfAbsent` is always preferred — it avoids unnecessary computation and is atomic.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Java's concurrent collections are designed for thread safety from the ground up — not wrappers around single-threaded collections. ConcurrentHashMap: lock-free reads, per-bin write locking. CopyOnWriteArrayList: snapshot reads, copy-on-write mutations. BlockingQueue: blocking put/take for producer-consumer.
>
> **Part 2 — How/Why (30s):** ConcurrentHashMap (Java 8+) uses volatile node fields for lock-free reads and `synchronized` on the bin head for writes — locking only the affected bucket, not the map. Empty bins use CAS (no lock). It prohibits null to eliminate ambiguity in concurrent get(). CopyOnWriteArrayList copies the entire array on every write — readers see a consistent snapshot, never a partially-modified array. BlockingQueue's `put()` blocks when full and `take()` blocks when empty — natural backpressure that prevents producer from overwhelming consumer.
>
> **Part 3 — Gotcha (20s):** Two traps: using `containsKey()` + `put()` on ConcurrentHashMap (not atomic — use `computeIfAbsent()` instead). And using CopyOnWriteArrayList for write-heavy workloads — every write copies the entire array, creating massive GC pressure and OOM risk. Rule: if writes are frequent, CopyOnWrite is the wrong choice.

---

## 🧾 TL;DR

- **ConcurrentHashMap (Java 8+):** volatile reads (lock-free), `synchronized` per-bin writes, CAS for empty bins. No nulls.
- **`computeIfAbsent`** > `containsKey` + `put` — atomic and lazy. Always use for caching patterns.
- **CopyOnWriteArrayList:** copy-on-write array. Reads = snapshot (lock-free). Writes = O(n) copy. Read-heavy only.
- **BlockingQueue:** `put()` blocks when full, `take()` blocks when empty. Natural backpressure. Use `ArrayBlockingQueue` (bounded).
- **`synchronizedMap`** = single global lock = serial bottleneck. Use ConcurrentHashMap instead.
- CHM `size()` is approximate during concurrent writes. Use `mappingCount()` for long return.
- ConcurrentSkipListMap = concurrent sorted map (O(log n), CAS-based, lock-free reads).
- CHM iterators are weakly consistent — no ConcurrentModificationException, may miss concurrent changes.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #18 (Phase 4) of the JavaBackend KB completion roadmap. Staff-level depth: CHM Java 8 internals (volatile Node array, per-bin synchronized, CAS for empty bins, ForwardingNode during resize), null prohibition rationale, compute/merge/computeIfAbsent atomicity, size() approximation (CounterCell), bulk operations (forEach/search/reduce with parallelism threshold), CopyOnWriteArrayList snapshot semantics + write cost, BlockingQueue family comparison table (ABQ/LBQ/PBQ/SQ/DelayQueue), ConcurrentSkipListMap. Two production footguns: check-then-act race on CHM, COWAL OOM from write-heavy usage. |
