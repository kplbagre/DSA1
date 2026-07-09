# LRU Cache

> **Standard followed:** `LLD/notes-standards.md`

---

## 🎯 Problem Statement

Design a thread-safe LRU (Least Recently Used — a cache eviction policy that removes the entry that was accessed least recently when the cache is full) Cache with a fixed capacity. On `get`, return the value and promote the key to "most recently used." On `put`, insert or update; if at capacity, evict the least recently used entry first. Both operations must be O(1).

---

## 📖 Requirements

**Functional:**
- `get(key)` — return value if present; return null if absent. Marks key as recently used.
- `put(key, value)` — insert or update. If at capacity, evict the LRU key first.
- Capacity is fixed at construction time.
- O(1) time complexity for both get and put.

**Non-functional:**
- Thread-safe — concurrent reads and writes must not corrupt the cache
- No third-party data structures — implement the doubly linked list manually (interviewers expect this)

---

## 🏗️ Class Design

### 🎨 Visual — Class Structure and Eviction

```
LRUCache<K, V>
  - capacity: int
  - map:  HashMap<K, Node<K,V>>          ← O(1) lookup by key
  - head: Node<K,V>  (dummy sentinel)    ← most-recently-used end
  - tail: Node<K,V>  (dummy sentinel)    ← least-recently-used end
  - lock: ReentrantReadWriteLock

Node<K,V>
  - key: K
  - value: V
  - prev: Node<K,V>
  - next: Node<K,V>

Doubly Linked List state (newest ← head ... tail → oldest):

  [head] ↔ [Node C] ↔ [Node A] ↔ [Node B] ↔ [tail]
   dummy    most recent            least recent  dummy

get("A"):
  → remove A from current position
  → addToFront(A)
  [head] ↔ [Node A] ↔ [Node C] ↔ [Node B] ↔ [tail]

put("D") at capacity=3:
  → evict tail.prev  (Node B — the LRU entry)
  → map.remove(B.key)
  → addToFront(new Node("D", val))
  → map.put("D", node)
  [head] ↔ [Node D] ↔ [Node A] ↔ [Node C] ↔ [tail]

KEY INVARIANT:
   head.next  = most recently used (newest).
   tail.prev  = least recently used (eviction candidate).
   map.size() == DLL node count at all times (always in sync).
```

---

## 🔌 Key Interfaces

```java
/**
 * Generic cache contract.
 * Implementations decide the eviction policy — LRU, LFU, MRU.
 * Decouples the caller from the eviction algorithm.
 */
public interface Cache<K, V> {

    // Returns null if key not present; promotes key to most-recently-used
    V get(K key);

    void put(K key, V value);

    int size();
}
```

---

## 🧭 Design Decisions

| Decision | Why |
|---|---|
| **DLL + HashMap** | HashMap gives O(1) lookup by key. DLL gives O(1) remove-and-reinsert (pointer swap, no traversal). Together they deliver O(1) get and put. Without the DLL, finding a node's neighbours for removal requires O(n) traversal. |
| **Dummy head + tail sentinels** | Eliminates `if (node == head)` / `if (node == tail)` null-checks in every add/remove. All operations follow the same 4-pointer swap regardless of position. DRY, fewer edge-case bugs. |
| **`LinkedHashMap` for production** | `new LinkedHashMap<>(capacity, 0.75f, true)` with `removeEldestEntry` does this in ~10 lines — tested JDK code. For this interview, implement the DLL to demonstrate understanding of the underlying data structure. Mention the LinkedHashMap alternative explicitly. |
| **`ReentrantReadWriteLock`** | `get()` mutates list order (moves node to front), so it needs a write lock — not a read lock. `put()` always needs a write lock. Since both operations need the write lock in this problem, all paths use `writeLock`. ReadWriteLock still signals intent: if a `peek()` operation (no promotion) were added, it would use `readLock`. |
| **No GoF pattern** | This is a data structure problem. The design insight is the DLL+HashMap composition. Forcing a GoF pattern (e.g., "Strategy for eviction") would violate KISS. If multiple eviction policies (LRU, LFU, MRU) were required, Strategy would be the natural addition. |

---

## 🎨 Visual — Object Interaction

```
get("A") — hit path, promote to front:

LRUCache.get("A")
  │  writeLock.lock()           ← write lock because get() mutates DLL order
  │  node = map.get("A")        ← O(1) lookup
  │  if node == null → return null
  │  remove(node)               ← unlink from current position
  │     node.prev.next = node.next
  │     node.next.prev = node.prev
  │  addToFront(node)           ← insert after dummy head
  │     node.next = head.next
  │     node.prev = head
  │     head.next.prev = node
  │     head.next = node
  │  writeLock.unlock()
  └─ return node.value

put("D", val) — at capacity, evict + insert:

LRUCache.put("D", val)
  │  writeLock.lock()
  │  if map.containsKey("D"):
  │    update node.value
  │    remove(node) + addToFront(node)   ← refresh position
  │  else:
  │    if size() == capacity:
  │      lruNode = tail.prev             ← eviction candidate (O(1))
  │      remove(lruNode)
  │      map.remove(lruNode.key)
  │    newNode = new Node<>("D", val)
  │    addToFront(newNode)
  │    map.put("D", newNode)
  │  writeLock.unlock()

KEY INVARIANT:
   Every structural change (move, insert, remove) holds writeLock.
   map and DLL are always mutated together — never one without the other.
```

---

## 🖊️ Coding Skeleton

**Interview coding order — write in this sequence:**

1. **`Node<K,V>`** — key, value, prev, next (a simple inner class or standalone)
2. **`Cache<K,V>`** interface — `get`, `put`, `size`
3. **`LRUCache<K,V>`** fields — capacity, map, head, tail, lock
4. **Constructor** — initialise dummy sentinels: `head.next = tail`, `tail.prev = head`
5. **Private helpers** — `addToFront(Node)` and `remove(Node)` — the 4-pointer swap
6. **`get()`** — map lookup → remove → addToFront → return value
7. **`put()`** — if exists: update + moveToFront; else: evict if full, then add

**The two helpers to memorise — everything else calls these:**

```java
// All add and remove operations reduce to these two methods
private void addToFront(Node<K, V> node) {
    node.next = head.next;
    node.prev = head;
    head.next.prev = node;
    head.next = node;
}

private void remove(Node<K, V> node) {
    node.prev.next = node.next;
    node.next.prev = node.prev;
}
```

**get() and put() skeletons:**

```java
@Override
public V get(K key) {
    lock.writeLock().lock();
    try {
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        remove(node);
        addToFront(node);
        return node.value;
    } finally {
        lock.writeLock().unlock();
    }
}

@Override
public void put(K key, V value) {
    lock.writeLock().lock();
    try {
        if (map.containsKey(key)) {
            Node<K, V> node = map.get(key);
            node.value = value;
            remove(node);
            addToFront(node);
        } else {
            if (map.size() == capacity) {
                Node<K, V> lru = tail.prev;   // eviction candidate
                remove(lru);
                map.remove(lru.key);
            }
            Node<K, V> newNode = new Node<>(key, value);
            addToFront(newNode);
            map.put(key, newNode);
        }
    } finally {
        lock.writeLock().unlock();
    }
}
```

---

## 🔁 Concurrency — Making It Thread-Safe

**Shared mutable state:**

| Field | Problem without lock | Fix |
|---|---|---|
| DLL `prev`/`next` pointers | Two threads calling `addToFront()` simultaneously → interleaved pointer writes → broken list | Write lock covers all structural changes |
| `map` (HashMap) | Two threads `put()` simultaneously → `ConcurrentModificationException` or lost entry | Write lock covers all map mutations |
| `get()` promotes node | Read threads mutating list order during a concurrent `put()` → corruption | `get()` uses write lock — it's a structural mutation, not a pure read |

**Strategy: ReentrantReadWriteLock (write lock for all operations)**

```java
// thread-safe: ReentrantReadWriteLock — write lock on get() and put()
// (get() mutates DLL order, so it cannot use readLock)
public class LRUCache<K, V> implements Cache<K, V> {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public V get(K key) {
        lock.writeLock().lock();    // write lock — get() moves node to front
        try {
            // ... lookup, remove, addToFront
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

**Note on readLock eligibility:** If you add a `peek(key)` that returns the value without promoting recency (no DLL mutation), that operation can use `readLock` — concurrent peeks don't interfere. True LRU `get()` always needs `writeLock`.

---

## 📐 "What Would You Do Differently?"

> *"In production I'd use `LinkedHashMap(capacity, 0.75f, true)` with `removeEldestEntry` returning `size() > capacity` — it's the battle-tested JDK LRU, not hand-rolled pointer logic. For high concurrency I'd look at Caffeine's LRU, which uses a lock-free window-TinyLFU policy and outperforms `synchronized` implementations at scale. I'd also make the eviction policy pluggable — inject an `EvictionPolicy` Strategy so LFU can replace LRU without rewriting the cache shell."*

---

## 🔬 Interview Q&As

### Q: "Why doubly linked list and not singly linked?"
> To remove a node in O(1) you need to update its predecessor's `next` pointer. With a singly linked list, finding the predecessor requires O(n) traversal from the head. With a doubly linked list, `node.prev` gives the predecessor directly — O(1) removal without traversal.

### Q: "Why dummy head and tail sentinels?"
> They eliminate edge-case null checks. Without sentinels, `addToFront()` must check "is the list empty?" and `remove()` must check "is this the head?" With sentinels, the list always has at least two nodes and every operation follows the same 4-pointer code path. DRY — fewer edge-case bugs, simpler to verify correct.

### Q: "What's the time complexity of get and put?"
> Both are O(1). `get`: map lookup O(1), DLL remove O(1) (pointer swap), addToFront O(1). `put`: map insert O(1), eviction of tail.prev O(1), addToFront O(1). The DLL is what makes O(1) eviction possible — without it, finding the LRU entry requires a scan.

### Q: "Can you implement this with LinkedHashMap?"
> Yes — `new LinkedHashMap<>(capacity, 0.75f, true)` with access order true, override `removeEldestEntry` to return `size() > capacity`. In production this is the right answer — JDK-tested, ~10 lines. For this interview I'm implementing the DLL version to show I understand the underlying structure. I'd mention the LinkedHashMap shortcut explicitly to signal I know both.

### Q: "Your get() uses writeLock. Is there a scenario where readLock works?"
> If I add a `peek(key)` that returns the value without promoting recency (no DLL mutation), that uses readLock — multiple threads can peek concurrently without blocking each other. True LRU `get()` always needs writeLock because moving a node to the front is a structural mutation. If the interviewer asks, I'd offer both variants and explain the trade-off.

---

## 🧾 TL;DR — 30-Second Pitch

> *"I use a HashMap for O(1) key lookup and a doubly linked list for O(1) eviction. Dummy head and tail sentinels eliminate edge-case null checks. `get()` looks up the node in the map, unlinks it, and reinserts it after the head — O(1). `put()` adds to the front; if at capacity, evicts `tail.prev` (the LRU node) first — O(1). All operations use `ReentrantReadWriteLock` write lock because `get()` mutates list order. In production I'd use `LinkedHashMap(capacity, 0.75f, true)` with `removeEldestEntry`."*

---

## 🔗 Patterns Used

- **No GoF pattern** — this is a data structure problem. The insight is the DLL + HashMap composition.
- **Java primitives used:** `ReentrantReadWriteLock`, `HashMap`. See **`LLD/java-building-blocks-for-lld.md`** (Concurrency Primitives section).

---

## 🖊️ Full Implementation

> Three classes total. Read top to bottom — interface → Node → LRUCache.

### Cache.java

```java
/**
 * Generic cache contract.
 * Implementations decide the eviction policy — LRU, LFU, MRU.
 */
public interface Cache<K, V> {

    // Returns null if key not present; promotes key to most-recently-used
    V get(K key);

    void put(K key, V value);

    int size();
}
```

### Node.java

```java
/**
 * Doubly linked list node.
 * Stores key so that when we evict tail.prev, we can remove it from the map too.
 */
public class Node<K, V> {

    K key;
    V value;
    Node<K, V> prev;
    Node<K, V> next;

    public Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
}
```

### LRUCache.java

```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU Cache: HashMap (O(1) lookup) + Doubly Linked List (O(1) eviction).
 *
 * thread-safe: ReentrantReadWriteLock — write lock on all operations
 * because get() also mutates DLL order (moves node to front).
 *
 * Dummy head and tail sentinels eliminate edge-case null checks.
 * head.next = MRU (most recently used)
 * tail.prev = LRU (eviction candidate)
 */
public class LRUCache<K, V> implements Cache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;   // dummy sentinel — MRU side
    private final Node<K, V> tail;   // dummy sentinel — LRU side
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public V get(K key) {
        lock.writeLock().lock();   // write lock — get() moves node to front (mutation)
        try {
            Node<K, V> node = map.get(key);
            if (node == null) {
                return null;
            }
            remove(node);
            addToFront(node);
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            if (map.containsKey(key)) {
                Node<K, V> node = map.get(key);
                node.value = value;
                remove(node);
                addToFront(node);
            } else {
                if (map.size() == capacity) {
                    // Evict LRU entry — tail.prev is always the least recently used
                    Node<K, V> lru = tail.prev;
                    remove(lru);
                    map.remove(lru.key);
                }
                Node<K, V> newNode = new Node<>(key, value);
                addToFront(newNode);
                map.put(key, newNode);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Insert node immediately after dummy head (MRU position)
    // Memorise these 4 pointer assignments — everything calls this
    private void addToFront(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    // Unlink node from its current position in the DLL
    private void remove(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 9, 2026 | Canonical note created. All classes in single MD. Status: canonical reference — Kapil has not self-attempted yet. |
