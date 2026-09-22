# ⚡ Collections API — Quick Reference

> **Use:** look up which collection to use for a given scenario. Not a deep-dive — see `DeepDive/hashmap-internals.md`, `DeepDive/concurrent-collections.md` for internals.

---

## 🔹 List — Ordered, Indexed, Duplicates Allowed

| Implementation | Backed by | Get by index | Add/Remove | Thread-safe | When to use |
|---|---|---|---|---|---|
| `ArrayList` | Resizable array | O(1) | O(n) mid, O(1) amortized end | ❌ | Default choice. Random access. |
| `LinkedList` | Doubly-linked list | O(n) | O(1) at head/tail | ❌ | Queue/Deque operations. Rarely better than ArrayList. |
| `CopyOnWriteArrayList` | Copy-on-write array | O(1) | O(n) copy per write | ✅ | Read-heavy, write-rare (listener lists). |
| `List.of(...)` | Immutable array | O(1) | ❌ (immutable) | ✅ (immutable) | Constants, config, return values. Java 9+. |
| `Collections.unmodifiableList()` | Wrapper | O(1) | ❌ (throws UOE) | ❌ (underlying can change) | Unmodifiable VIEW of mutable list. |

---

## 🔹 Set — No Duplicates

| Implementation | Backed by | Contains | Add | Ordered? | Thread-safe | When to use |
|---|---|---|---|---|---|---|
| `HashSet` | HashMap | O(1) | O(1) | ❌ | ❌ | Default. Fast lookup. No order. |
| `LinkedHashSet` | LinkedHashMap | O(1) | O(1) | Insertion order | ❌ | Need insertion order + dedup. |
| `TreeSet` | Red-black tree | O(log n) | O(log n) | Sorted (natural/Comparator) | ❌ | Need sorted iteration. |
| `EnumSet` | Bit vector | O(1) | O(1) | Enum declaration order | ❌ | Set of enum constants. Fastest Set. |
| `ConcurrentHashMap.newKeySet()` | CHM | O(1) | O(1) | ❌ | ✅ | Concurrent set. Java 8+. |
| `Set.of(...)` | Immutable | O(1) | ❌ | ❌ | ✅ (immutable) | Constants. Java 9+. |

---

## 🔹 Map — Key-Value Pairs

| Implementation | Contains key | Put | Ordered? | Null key? | Thread-safe | When to use |
|---|---|---|---|---|---|---|
| `HashMap` | O(1) | O(1) | ❌ | ✅ (one) | ❌ | Default. Fast. |
| `LinkedHashMap` | O(1) | O(1) | Insertion or access order | ✅ | ❌ | Need ordered iteration. LRU cache (access order). |
| `TreeMap` | O(log n) | O(log n) | Sorted by key | ❌ | ❌ | Need sorted keys. Range queries. |
| `EnumMap` | O(1) | O(1) | Enum declaration order | ❌ | ❌ | Keys are enum constants. Fastest Map for enums. |
| `ConcurrentHashMap` | O(1) | O(1) | ❌ | ❌ | ✅ | Concurrent access. Production default for shared maps. |
| `Map.of(...)` | O(1) | ❌ | ❌ | ❌ | ✅ (immutable) | Constants. Java 9+. |
| `WeakHashMap` | O(1) | O(1) | ❌ | ✅ | ❌ | Cache with auto-eviction (keys GC'd when no other refs). |

---

## 🔹 Queue / Deque

| Implementation | Bounded? | Blocking? | Thread-safe | When to use |
|---|---|---|---|---|
| `ArrayDeque` | No (grows) | No | ❌ | Stack or queue (faster than LinkedList). |
| `PriorityQueue` | No | No | ❌ | Priority-ordered processing (min-heap). |
| `ArrayBlockingQueue` | Yes (fixed) | Yes | ✅ | Bounded producer-consumer. Predictable memory. |
| `LinkedBlockingQueue` | Optional | Yes | ✅ | Optionally bounded. Higher throughput than ABQ. |
| `PriorityBlockingQueue` | No | Yes | ✅ | Concurrent priority processing. |
| `SynchronousQueue` | 0 (handoff) | Yes | ✅ | Direct handoff — producer blocks until consumer takes. |
| `ConcurrentLinkedQueue` | No | No | ✅ | Non-blocking concurrent queue. poll() returns null if empty. |

---

## 🔹 Decision Flowchart

```
  Need key-value pairs?
    YES → Need thread-safety?
           YES → ConcurrentHashMap
           NO  → Need sorted keys? → TreeMap
                 Need insertion order? → LinkedHashMap
                 Default → HashMap

  Need unique elements (no duplicates)?
    YES → Need sorted? → TreeSet
          Need insertion order? → LinkedHashSet
          Enum values? → EnumSet
          Default → HashSet

  Need ordered elements (duplicates OK)?
    YES → Need index access? → ArrayList
          Need frequent add/remove at ends? → ArrayDeque
          Default → ArrayList

  Need producer-consumer queue?
    YES → Need bounded? → ArrayBlockingQueue
          Need priority? → PriorityBlockingQueue
          Need non-blocking? → ConcurrentLinkedQueue
          Default → LinkedBlockingQueue
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #32 (Phase 5). List/Set/Map/Queue comparison tables with Big-O, thread-safety, and use-case guidance. Decision flowchart. |
