# Confluent DSA — Design-Coding Problems

> **Format:** Follow `_format.md` in this folder.
>
> **What's in this file:** "Build a component" problems — Confluent's bread and butter. These test design thinking + coding simultaneously. Not pure algorithmic puzzles.

---

## Table of Contents

| # | Problem | Tier | LC # | Status |
|---|---|---|---|---|
| 1 | [KV Store with TTL + Windowed Average](#1-kv-store-with-ttl--windowed-average) | ⭐ Tier 1 | ~LC 981 | [ ] |
| 2 | [Thread-Safe LRU Cache with TTL](#2-thread-safe-lru-cache-with-ttl) | ⭐ Tier 1 | LC 146 variant | [ ] |
| 3 | [Design HashMap](#3-design-hashmap) | Tier 2 | LC 706 | [ ] |
| 4 | [Inverted Index / Document Search](#4-inverted-index--document-search) | Tier 2 | Custom | [ ] |
| 5 | [Tail Command — Last N Lines of Huge File](#5-tail-command--last-n-lines-of-huge-file) | Tier 2 | Custom | [ ] |
| 6 | [Function Signature Matcher](#6-function-signature-matcher-with-variadic-args) | ⭐ Tier 1 | Custom | [ ] |
| 7 | [Notebook Editor](#7-notebook-editor) | Tier 3 | Custom | [ ] |
| 8 | [Restaurant Menu Optimizer](#8-restaurant-menu-optimizer) | Tier 2 | Custom | [ ] |

---

## 1. KV Store with TTL + Windowed Average

### **🎤 How It's Asked:**

> "Design a key-value store where each key has an expiration time. After the TTL expires, `get()` should return null. Also implement `get_average()` that returns the average of all non-expired values."
>
> Alternate framing: "Build a cache with automatic expiry" or "Design a time-based key-value store like Redis with TTL support."

### **Discussion — How to arrive at the solution:**

Start with the simplest KV store — a HashMap. Now add TTL: each entry needs a timestamp. On `get()`, check if `currentTime - insertTime > ttl`. If yes, it's expired — return null and optionally clean it up.

For `get_average()`, the brute force scans all entries, filters expired ones, sums values. That's O(n) per call. Can we do better? If we track a running sum and count, we can update on insert/delete/expire. But lazy expiration means expired keys still sit in the map. Two approaches:

1. **Lazy expiration** — check on `get()`, clean up expired entries periodically
2. **Active expiration** — background thread or sorted structure by expiry time

For the windowed average specifically, a DLL ordered by insertion time lets us evict expired entries from the tail efficiently — **but only when all entries share the same TTL**, so insertion order equals expiry order. If TTLs are per-key, a new entry with a short TTL goes to the head but may expire before older entries at the tail — the tail scan stops too early. See Variants for the fix.

### **Brute Force:**

- HashMap<String, Entry> where Entry = {value, insertTime, ttl}
- `get()`: lookup + check expiry → O(1)
- `get_average()`: iterate all entries, skip expired → O(n)

- **Time:** O(1) for get/put, O(n) for get_average
- **Space:** O(n) — HashMap stores all entries including expired (until cleaned)

### **Key Insight:**

The core trick is maintaining a **running sum and count** of non-expired values. When you insert, add to sum and increment count. When an entry expires, subtract from sum and decrement count. Now `get_average()` is O(1): `sum / count`.

But how do you know WHEN entries expire? You can't check every entry on every call. Use a **DLL ordered by insertion time** — when all entries share the same TTL, insertion order = expiry order, so the tail always holds the earliest-expiring entry. Before any operation, evict entries from the tail while `tail.expiryTime <= currentTime`. This amortizes the cleanup cost.

⚠️ **Hidden assumption:** this tail-scan-and-stop only works because TTLs are uniform. With per-key TTLs, a new short-TTL entry goes to the head but expires before older long-TTL entries at the tail — `evictExpired()` stops too early and stale entries linger in the average. See Variants for the fix.

This is the same DLL + HashMap pattern as LRU Cache, but ordered by **insertion time** instead of **access time** (which equals expiry order when TTLs are uniform).

### **Optimal Solution:**

**Steps in plain English:**

1. **Data structure** — HashMap for O(1) key lookup + DLL for expiry-ordered traversal + running sum/count for O(1) average.
2. **put()** — evict expired entries first, then remove old entry if key exists (subtract from sum), create new entry with expiryTime, add to HashMap + DLL head, add to sum.
3. **get()** — evict expired, lookup in HashMap, return value or null.
4. **getAverage()** — evict expired, return runningSum / activeCount.
5. **evictExpired()** — scan from DLL tail (earliest expiry) removing entries while expiryTime ≤ now, subtract each from sum/count/HashMap.

```java
import java.util.HashMap;

public class TTLKeyValueStore {

    private static class Entry {
        String key;
        int value;
        long expiryTime;
        Entry prev;
        Entry next;

        Entry(String key, int value, long expiryTime) {
            this.key = key;
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    // Step 1 — data structures: HashMap + DLL + running aggregates
    private final HashMap<String, Entry> map = new HashMap<>();
    private final Entry head = new Entry("", 0, Long.MAX_VALUE);
    private final Entry tail = new Entry("", 0, Long.MIN_VALUE);
    private long runningSum = 0;
    private int activeCount = 0;

    public TTLKeyValueStore() {
        head.next = tail;
        tail.prev = head;
    }

    // Step 2 — put: evict expired → remove old if exists → insert new → update sum
    public void put(String key, int value, long ttlMillis) {
        long now = System.currentTimeMillis();
        evictExpired(now);

        // Remove old entry if key exists — subtract from running sum
        if (map.containsKey(key)) {
            Entry old = map.get(key);
            runningSum -= old.value;
            activeCount--;
            removeFromDLL(old);
        }

        // Create new entry with computed expiry time
        Entry entry = new Entry(key, value, now + ttlMillis);
        map.put(key, entry);
        addToHead(entry);
        runningSum += value;
        activeCount++;
    }

    // Step 3 — get: evict expired → lookup → return
    public Integer get(String key) {
        evictExpired(System.currentTimeMillis());
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        return entry.value;
    }

    // Step 4 — getAverage: evict expired → return sum/count in O(1)
    public Double getAverage() {
        evictExpired(System.currentTimeMillis());
        if (activeCount == 0) {
            return 0.0;
        }
        return (double) runningSum / activeCount;
    }

    // Step 5 — evictExpired: scan from tail (earliest expiry), remove while expired
    private void evictExpired(long now) {
        Entry current = tail.prev;
        while (current != head && current.expiryTime <= now) {
            Entry toRemove = current;
            current = current.prev;
            map.remove(toRemove.key);
            removeFromDLL(toRemove);
            runningSum -= toRemove.value;
            activeCount--;
        }
    }

    private void addToHead(Entry entry) {
        entry.next = head.next;
        entry.prev = head;
        head.next.prev = entry;
        head.next = entry;
    }

    private void removeFromDLL(Entry entry) {
        entry.prev.next = entry.next;
        entry.next.prev = entry.prev;
    }
}
```

- **Time:** O(1) amortized for put/get/getAverage — evictExpired is O(k) where k = number of expired entries, but each entry is evicted at most once across all calls
- **Space:** O(n) — HashMap + DLL both store n entries. Running sum/count are O(1) extra.

**Complexity derivation for amortized O(1):** Each entry enters the DLL once (on `put`) and leaves once (on eviction). Across N operations, total eviction work is at most N. So eviction cost spread across N operations = O(1) per operation.

### **🔄 Variants — How they can twist this:**

- "What if TTL is per-key (different TTL for each key)?" → **The DLL approach breaks.** The DLL is ordered by insertion time; `evictExpired()` scans from the tail and stops at the first non-expired entry. This only works when insertion order = expiry order (i.e., uniform TTLs).

  **Failure trace:**
  ```
  T=0: put("A", 1, ttl=10s) → expiryTime=10s, goes to head
  T=1: put("B", 2, ttl=2s)  → expiryTime=3s,  goes to head
  DLL: head ↔ B(exp=3s) ↔ A(exp=10s) ↔ tail

  At T=5s: evictExpired checks tail.prev = A (exp=10s > 5s) → STOPS
           B (expired at T=3s) is never evicted → getAverage() includes B's stale value
  ```

  **Fix: use `TreeMap<Long, List<String>>` as the expiry index instead of the DLL.**

  ```java
  private final TreeMap<Long, List<String>> expiryIndex = new TreeMap<>();

  // In put() — remove stale index entry when updating an existing key
  if (map.containsKey(key)) {
      Entry old = map.get(key);
      expiryIndex.get(old.expiryTime).remove(old.key);
      runningSum -= old.value;
      activeCount--;
  }
  // Add to expiry index keyed by absolute expiryTime
  long expiry = now + ttlMillis;
  expiryIndex.computeIfAbsent(expiry, k -> new ArrayList<>()).add(key);
  map.put(key, new Entry(key, value, expiry));
  runningSum += value;
  activeCount++;

  // In evictExpired() — headMap(now, true) finds ALL entries with expiryTime ≤ now
  // regardless of insertion order
  expiryIndex.headMap(now, true).forEach((expTime, keys) -> {
      for (String k : keys) {
          Entry e = map.remove(k);
          runningSum -= e.value;
          activeCount--;
      }
  });
  expiryIndex.headMap(now, true).clear();
  ```

  **Tradeoff:**

  | Approach | Insert | Evict | TTL constraint |
  |---|---|---|---|
  | DLL (current) | O(1) | O(k) amortized | Uniform TTLs only |
  | TreeMap | O(log n) | O(k log n) | Any per-key TTL ✅ |
  | Min-Heap | O(log n) | O(k log n) | Any per-key TTL, but needs lazy-delete for key updates |

  > Use TreeMap when TTLs are per-key. Mention DLL only if you've explicitly confirmed the interviewer wants uniform TTLs.
- "What if you need `get_max()` instead of `get_average()`?" → Running sum/count doesn't work. Use a `TreeMap<Integer, Integer>` (value → count) for O(log n) max lookup
- "Make it thread-safe" → See `04-concurrency-problems.md` for the thread-safe extension
- "What if you need to update TTL on `get()` (sliding expiry)?" → On get, remove from DLL, update expiryTime, re-insert at head. Same as LRU access reordering.

### **❓ Cross-Questions:**

- **"Why not use a priority queue (min-heap) instead of DLL for expiry ordering?"** → PQ gives O(log n) insert/remove vs DLL's O(1). For a KV store with frequent puts, O(1) matters. PQ also can't remove arbitrary entries efficiently (need index tracking).
- **"What about memory leaks from never calling evictExpired?"** → In production, run a background cleanup thread on a schedule. For the interview, lazy eviction on each operation is sufficient.
- **"What if System.currentTimeMillis() is called in tests?"** → Inject a `Clock` interface. `put(key, value, ttl, clock.now())` makes it testable.

---

## 2. Thread-Safe LRU Cache with TTL

### **🎤 How It's Asked:**

> "Design an LRU Cache. Now make it thread-safe. Now add TTL — entries should expire after a configurable time."
>
> This is always a 2-part question. Part 1 is standard LC 146. Part 2 adds concurrency + TTL.

### **Discussion — How to arrive at the solution:**

**Part 1 (standard LRU):** HashMap<Key, DLLNode> + Doubly Linked List. Already covered in full in your existing LRU Cache note.

**Part 2 (thread-safe + TTL):** Two additions:
1. **Thread safety:** The simplest correct approach is `synchronized` on every public method. For higher throughput, use `ReentrantReadWriteLock` — reads can proceed concurrently, only writes need exclusive access. For even higher throughput, use striped locking (partition keys into N buckets, one lock per bucket).
2. **TTL:** Add `expiryTime` to each DLL node. On `get()`, check if expired — if yes, remove and return null. Same lazy eviction as KV Store above.

### **Recap of existing note (5-line version):**

HashMap gives O(1) key→node lookup. DLL gives O(1) move-to-head (on access) and O(1) evict-from-tail (when full). Sentinel head/tail nodes eliminate null-check edge cases. `get()` = lookup + move to head. `put()` = insert at head, evict tail if over capacity.

**Full implementation:** `LLD/Problems/lru-cache/lru-cache.md`

### **Thread-Safe Extension (the Confluent-specific part):**

**Steps in plain English:**

1. **Choose locking strategy** — `ReentrantReadWriteLock` allows concurrent reads, exclusive writes. Better than `synchronized` for read-heavy cache.
2. **get()** — acquire read lock → lookup in map → check TTL expiry → if expired, release read lock, acquire write lock, re-check (double-check pattern), remove → if valid, return value.
3. **put()** — acquire write lock → if key exists, update value + TTL + move to head → if new, evict LRU if at capacity, then insert at head.
4. **Always release locks in finally** — prevents deadlock on exception.

```java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentLRUCache<K, V> {

    // ... (same DLL + HashMap internals as standard LRU) ...

    // Step 1 — ReadWriteLock: concurrent reads, exclusive writes
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final long defaultTtlMillis;

    // Step 2 — get: read lock → lookup → TTL check → upgrade to write lock if expired
    public V get(K key) {
        rwLock.readLock().lock();
        try {
            Node node = map.get(key);
            if (node == null) {
                return null;
            }
            // Check TTL expiry
            if (System.currentTimeMillis() > node.expiryTime) {
                // Need write lock to remove — must release read lock first
                // (ReadWriteLock does NOT support upgrade from read → write)
                rwLock.readLock().unlock();
                rwLock.writeLock().lock();
                try {
                    // Re-check after acquiring write lock (another thread may have removed it)
                    if (map.containsKey(key)) {
                        removeNode(node);
                        map.remove(key);
                    }
                    return null;
                } finally {
                    // Step 4 — always release in finally
                    rwLock.readLock().lock();
                    rwLock.writeLock().unlock();
                }
            }
            return node.value;
        } finally {
            // Step 4 — always release in finally
            rwLock.readLock().unlock();
        }
    }

    // Step 3 — put: write lock → update or insert → evict LRU if at capacity
    public void put(K key, V value) {
        rwLock.writeLock().lock();
        try {
            if (map.containsKey(key)) {
                // Update existing: new value, refresh TTL, move to head
                Node node = map.get(key);
                node.value = value;
                node.expiryTime = System.currentTimeMillis() + defaultTtlMillis;
                moveToHead(node);
            } else {
                // Insert new: evict LRU if at capacity
                if (map.size() >= capacity) {
                    Node lru = tail.prev;
                    removeNode(lru);
                    map.remove(lru.key);
                }
                Node newNode = new Node(key, value, System.currentTimeMillis() + defaultTtlMillis);
                addToHead(newNode);
                map.put(key, newNode);
            }
        } finally {
            // Step 4 — always release in finally
            rwLock.writeLock().unlock();
        }
    }
}
```

- **Time:** Same O(1) per operation. Lock acquisition adds constant overhead.
- **Space:** O(capacity) — bounded by LRU eviction.

### **🔄 Variants:**

- "Use `synchronized` instead of `ReadWriteLock`" → Simpler but lower throughput. Acceptable if interviewer doesn't push.
- "What about lock-free LRU?" → Extremely complex. Mention `ConcurrentLinkedDeque` + `ConcurrentHashMap` but say it sacrifices strict LRU ordering for approximate LRU.
- "How does Redis handle this?" → Redis is single-threaded — no locking needed. TTL via lazy expiry + probabilistic active expiry (sample 20 random keys, delete expired ones).

### **❓ Cross-Questions:**

- **"Why can't you upgrade from read lock to write lock?"** → `ReentrantReadWriteLock` doesn't support it — if two threads both hold read locks and both try to upgrade to write, deadlock. Must release read first, acquire write, then re-check.
- **"What's the throughput difference between synchronized and ReadWriteLock?"** → For read-heavy workloads (90% reads), ReadWriteLock allows concurrent reads → ~10x throughput. For write-heavy workloads, no benefit (write lock is exclusive either way).
- **"How would you handle expiry without lazy eviction?"** → Background `ScheduledExecutorService` that scans for expired entries every N seconds. Or `DelayQueue<Delayed>` where each entry implements `getDelay()`.

---

## 3. Design HashMap

### **🎤 How It's Asked:**

> "Implement a HashMap from scratch — put(key, value), get(key), remove(key). Don't use any built-in hash table."
>
> Alternate: "Design a hash table that handles collisions."

### **Discussion — How to arrive at the solution:**

The simplest approach: array of fixed size + modulo for index. But what about collisions? Two strategies:
1. **Chaining** (linked list per bucket) — simpler, what Java uses
2. **Open addressing** (linear probing) — better cache locality, what Python uses

For interviews, chaining is easier to code and explain. Start with a fixed-size array. Each slot holds a linked list of (key, value) pairs. Hash the key, mod by array size, traverse the chain.

### **Brute Force:**

- Single giant array with linear scan for every operation → O(n) per operation
- **Time:** O(n) for get/put/remove
- **Space:** O(n)

### **Key Insight:**

Hash function converts the key space into a small index space. `Math.abs(key.hashCode()) % capacity` distributes keys across buckets. With a good hash and load factor < 0.75, each bucket has ~1 entry on average → O(1) amortized.

The `Math.abs()` is critical — `hashCode()` can return negative values. Without it, negative modulo gives a negative array index → `ArrayIndexOutOfBoundsException`.

### **Optimal Solution:**

**Steps in plain English:**

1. **Data structure** — array of buckets, each bucket is a linked list of (key, value) entries. Size tracks total entries.
2. **Hash function** — `Math.abs(key.hashCode()) % capacity` maps any key to a bucket index.
3. **put()** — check load factor → resize if needed → hash to bucket → walk chain: if key exists, update value; if not, prepend new entry to chain head.
4. **get()** — hash to bucket → walk chain → return value if key found, null otherwise.
5. **remove()** — hash to bucket → walk chain with prev pointer → unlink matching entry.
6. **resize()** — double capacity, create new array, rehash ALL existing entries into new buckets.

```java
public class MyHashMap<K, V> {

    private static class Entry<K, V> {
        K key;
        V value;
        Entry<K, V> next;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // Step 1 — bucket array + size tracker
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    @SuppressWarnings("unchecked")
    private Entry<K, V>[] buckets = new Entry[DEFAULT_CAPACITY];
    private int size = 0;

    // Step 2 — hash function: key → bucket index
    private int getBucketIndex(K key) {
        return Math.abs(key.hashCode()) % buckets.length;
    }

    // Step 3 — put: resize check → hash → walk chain → update or prepend
    public void put(K key, V value) {
        if ((float) size / buckets.length >= LOAD_FACTOR) {
            resize();
        }

        int index = getBucketIndex(key);
        Entry<K, V> current = buckets[index];

        // Walk the chain — update if key already exists
        while (current != null) {
            if (current.key.equals(key)) {
                current.value = value;
                return;
            }
            current = current.next;
        }

        // Key not found — prepend new entry to chain head (O(1))
        Entry<K, V> newEntry = new Entry<>(key, value);
        newEntry.next = buckets[index];
        buckets[index] = newEntry;
        size++;
    }

    // Step 4 — get: hash → walk chain → return value or null
    public V get(K key) {
        int index = getBucketIndex(key);
        Entry<K, V> current = buckets[index];

        while (current != null) {
            if (current.key.equals(key)) {
                return current.value;
            }
            current = current.next;
        }
        return null;
    }

    // Step 5 — remove: hash → walk chain with prev pointer → unlink match
    public boolean remove(K key) {
        int index = getBucketIndex(key);
        Entry<K, V> current = buckets[index];
        Entry<K, V> prev = null;

        while (current != null) {
            if (current.key.equals(key)) {
                if (prev == null) {
                    // Removing head of chain
                    buckets[index] = current.next;
                } else {
                    prev.next = current.next;
                }
                size--;
                return true;
            }
            prev = current;
            current = current.next;
        }
        return false;
    }

    // Step 6 — resize: double capacity, rehash all entries
    @SuppressWarnings("unchecked")
    private void resize() {
        Entry<K, V>[] oldBuckets = buckets;
        buckets = new Entry[oldBuckets.length * 2];
        size = 0;

        // Rehash every existing entry into the new, larger array
        for (Entry<K, V> head : oldBuckets) {
            Entry<K, V> current = head;
            while (current != null) {
                put(current.key, current.value);
                current = current.next;
            }
        }
    }
}
```

- **Time:** O(1) amortized for get/put/remove — WHY: with load factor < 0.75, average chain length is ~1. Resize is O(n) but happens only after n/0.75 insertions → amortized O(1) per insert.
- **Space:** O(n) — n entries stored across ~n/0.75 buckets. Each entry is a linked list node (key + value + next pointer).

### **🔄 Variants:**

- "What if I want O(log n) worst case instead of O(n)?" → Java 8 solution: when a chain exceeds 8 entries, convert to a red-black tree. Mention this, don't code it.
- "Implement with open addressing (linear probing)" → On collision, check next slot, then next, etc. Deletion requires "tombstone" markers.
- "Make it thread-safe" → `ConcurrentHashMap` approach: segment locking (Java 7) or CAS on each bucket (Java 8). Don't implement — describe.

### **❓ Cross-Questions:**

- **"Why capacity 16 and load factor 0.75?"** → 16 is a power of 2 (fast modulo via bitwise AND). 0.75 balances memory (lower = more buckets wasted) vs speed (higher = longer chains).
- **"What if `hashCode()` returns `Integer.MIN_VALUE`?"** → `Math.abs(Integer.MIN_VALUE)` returns `Integer.MIN_VALUE` (overflow!). Fix: `(key.hashCode() & 0x7fffffff) % capacity` — bitwise AND masks the sign bit.
- **"Why use `.equals()` not `==` for key comparison?"** → `==` checks reference equality. Two different String objects with same content: `==` is false, `.equals()` is true.

---

## 4. Inverted Index / Document Search

### **🎤 How It's Asked:**

> "Given a set of documents, build a system that supports searching for documents containing a given word. Then extend it to multi-word search (AND — all words must appear)."
>
> Alternate: "Build a simple search engine backend."

### **Discussion — How to arrive at the solution:**

Brute force: for each query, scan every document. Obviously O(docs × words). The insight is to pre-process: build a map from `word → set of document IDs` (inverted index). Then lookup is O(1) per word.

For multi-word AND queries: intersect the document sets for each query word. Smallest set first to minimize intersection cost.

### **Brute Force:**

- For each query word, iterate all documents, check if word exists → O(D × W) where D = total docs, W = avg words per doc
- **Time:** O(D × W) per query
- **Space:** O(1) extra

### **Key Insight:**

Flip the relationship. Instead of "document → words" (forward index), build "word → documents" (inverted index). This is the same inversion that makes HashMap lookups O(1) — pre-compute the answer to the question you'll ask repeatedly.

For phrase search (words in order), store positions: `word → list of (docId, [positions])`. Then check that positions are consecutive.

### **Optimal Solution:**

**Steps in plain English:**

1. **Build index** — for each document, tokenize (split + lowercase), then for every word add the docId to that word's set in the inverted map.
2. **Single search** — lookup word in map → return the set of docIds (O(1)).
3. **Multi-word AND** — collect each word's docId set → short-circuit if any word has zero results → sort sets by size (smallest first) → intersect progressively using `retainAll`.

```java
import java.util.*;

public class InvertedIndex {

    // Step 1 — inverted map: word → set of document IDs
    private final Map<String, Set<Integer>> index = new HashMap<>();
    private final Map<Integer, String> documents = new HashMap<>();

    // Step 1 — build index: tokenize document, add docId to each word's set
    public void addDocument(int docId, String content) {
        documents.put(docId, content);
        String[] words = content.toLowerCase().split("\\s+");
        for (String word : words) {
            index.computeIfAbsent(word, k -> new HashSet<>()).add(docId);
        }
    }

    // Step 2 — single word search: O(1) map lookup
    public Set<Integer> search(String word) {
        return index.getOrDefault(word.toLowerCase(), Collections.emptySet());
    }

    // Step 3 — multi-word AND: collect sets → sort by size → intersect smallest first
    public Set<Integer> searchAll(String[] words) {
        if (words.length == 0) {
            return Collections.emptySet();
        }

        // Collect each word's docId set
        List<Set<Integer>> sets = new ArrayList<>();
        for (String word : words) {
            Set<Integer> docs = index.get(word.toLowerCase());
            if (docs == null || docs.isEmpty()) {
                // Short-circuit: one word has zero results → AND is empty
                return Collections.emptySet();
            }
            sets.add(docs);
        }

        // Sort by size — intersect smallest first to minimize work
        sets.sort(Comparator.comparingInt(Set::size));

        // Progressive intersection: start with smallest, retainAll with next
        Set<Integer> result = new HashSet<>(sets.get(0));
        for (int i = 1; i < sets.size(); i++) {
            result.retainAll(sets.get(i));
            if (result.isEmpty()) {
                return result;
            }
        }
        return result;
    }
}
```

- **Time:** Build index: O(D × W) where D = docs, W = avg words per doc. Single search: O(1). Multi-word AND: O(K × S) where K = number of query words, S = size of smallest matching set — WHY: `retainAll` iterates the smaller set checking membership in the larger.
- **Space:** O(D × W) — every word-document pair is stored once in the inverted index.

### **🔄 Variants:**

- "Support phrase search (words in exact order)" → Store positions: `Map<String, Map<Integer, List<Integer>>>` (word → docId → list of positions). Check consecutive positions.
- "Support OR queries" → Union instead of intersection: `result.addAll(sets.get(i))`
- "Handle very large document sets" → Shard the index by word prefix. Same principle as database partitioning.
- "Add relevance ranking" → TF-IDF: term frequency × inverse document frequency. Rank results by score.

### **❓ Cross-Questions:**

- **"Why intersection from smallest set?"** → If one word appears in 5 docs and another in 50,000, starting with 5 means we check at most 5 membership lookups. Starting with 50,000 would check 50,000.
- **"How does Google scale this?"** → Partitioned inverted index across thousands of machines. Each machine handles a shard of the word space. Query fans out to all shards, results merge.

---

## 5. Tail Command — Last N Lines of Huge File

### **🎤 How It's Asked:**

> "Implement `tail -n 10 huge.log` — read the last 10 lines of a file that's too large to fit in memory."
>
> Alternate: "Read the last N lines of a file efficiently."

### **Discussion — How to arrive at the solution:**

Brute force: read entire file line by line, keep a circular buffer of size N. Works but reads the ENTIRE file — wasteful when the file is 100GB and you need 10 lines.

The insight: start from the END of the file and scan backwards. `RandomAccessFile` (a Java class that lets you jump to any byte position in a file without reading everything before it) supports `seek()` — jump to any byte offset in O(1). Count newlines backwards until you find N of them.

### **Brute Force:**

```java
// Read entire file, keep last N lines in circular buffer
BufferedReader reader = new BufferedReader(new FileReader(path));
String[] buffer = new String[n];
int count = 0;
String line;
while ((line = reader.readLine()) != null) {
    buffer[count % n] = line;
    count++;
}
```

- **Time:** O(F) where F = total file size in bytes — must read entire file
- **Space:** O(N × L) where L = avg line length — only the circular buffer

### **Key Insight:**

You don't need to read the file from the start. `RandomAccessFile.seek(position)` jumps to any byte in O(1). Start at the last byte and scan backwards counting `\n` characters. After finding N newlines, you know exactly where the last N lines start. Then read forward from that position.

This reads only the bytes in the last N lines, not the entire file.

### **Optimal Solution:**

**Steps in plain English:**

1. **Open with RandomAccessFile** — enables seeking to any byte position without reading the whole file.
2. **Handle edge case** — if file is empty, return immediately.
3. **Seek to second-to-last byte** — skip potential trailing `\n` that would give a phantom empty line.
4. **Scan backwards** — move position backwards byte-by-byte, counting `\n` characters until we find N newlines.
5. **Compute start position** — if we found N newlines, start reading from `pos + 1`; if we hit file start, read from 0.
6. **Read forward** — use `readLine()` to collect the last N lines.

```java
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class TailCommand {

    public static List<String> tail(String filePath, int n) throws Exception {
        // Step 1 — open with RandomAccessFile for random byte access
        RandomAccessFile raf = new RandomAccessFile(filePath, "r");
        long fileLength = raf.length();

        // Step 2 — edge case: empty file
        if (fileLength == 0) {
            raf.close();
            return new ArrayList<>();
        }

        int newlineCount = 0;
        // Step 3 — start at second-to-last byte (skip trailing \n)
        long pos = fileLength - 2;

        // Step 4 — scan backwards counting newline characters
        while (pos >= 0) {
            raf.seek(pos);
            byte b = raf.readByte();
            if (b == '\n') {
                newlineCount++;
                if (newlineCount == n) {
                    break;
                }
            }
            pos--;
        }

        // Step 5 — compute start position
        // Found N newlines → start after the Nth newline
        // Hit beginning of file → start from 0 (file has fewer than N lines)
        long startPos = (pos >= 0) ? pos + 1 : 0;
        raf.seek(startPos);

        // Step 6 — read forward line by line
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = raf.readLine()) != null) {
            lines.add(line);
        }

        raf.close();
        return lines;
    }
}
```

- **Time:** O(N × L) where N = number of lines requested, L = average line length — WHY: we scan backwards through at most N lines worth of bytes, then read forward through the same bytes. We never touch the rest of the file.
- **Space:** O(N × L) — stores the N result lines.

### **🔄 Variants:**

- "What if the file is being actively written to (like a log)?" → Use `raf.length()` to get current size at call time. Subsequent writes append after our snapshot — no corruption.
- "What about Windows line endings (`\r\n`)?" → Count `\n` only (same approach). `readLine()` handles `\r\n` automatically.
- "What if N is larger than total lines in the file?" → Our code handles this — `pos` reaches 0, we read the entire file. Return all lines.
- "Can you do this with `BufferedReader` instead?" → Not efficiently — `BufferedReader` only reads forward. You'd need to read the entire file.

### **❓ Cross-Questions:**

- **"Why `pos = fileLength - 2` instead of `fileLength - 1`?"** → Many files end with a trailing `\n`. If the last byte is `\n`, counting it would give us N-1 actual lines. Starting at -2 skips the trailing newline.
- **"What's the time complexity if N = total lines in the file?"** → Degrades to O(F) — we scan the entire file backwards. Same as brute force. But for the common case (N << total lines), this is much faster.
- **"Is `seek()` really O(1)?"** → Yes for local files — it just updates a file pointer offset. For network-mounted files, it may involve a round trip.

---

## 6. Function Signature Matcher with Variadic Args

### **🎤 How It's Asked:**

> "Given a list of function signatures like `foo(int, string)`, `bar(int, int, ...)`, and a function call like `bar(1, 2, 3)`, determine which function matches the call. The `...` means the function accepts any number of additional arguments of the preceding type."
>
> Alternate: "Build a function overload resolver."

### **Discussion — How to arrive at the solution:**

This is a pattern-matching problem disguised as a design problem. The key complexity is the variadic argument (`...`). Without variadics, it's simple: match name + exact parameter count + types. With variadics, you need to check: do the fixed parameters match, and are all extra arguments of the variadic type?

Break it into steps: (1) group functions by name, (2) for each call, find functions with matching name, (3) filter by parameter compatibility (including variadic expansion).

### **Key Insight:**

Separate the matching into two phases:
1. **Exact match** — name + exact parameter count + all types match
2. **Variadic match** — name matches, fixed params match, remaining args all match the variadic type

Prioritize exact match over variadic match. If multiple variadic matches exist, the one with the most fixed parameters wins (most specific match — same rule as Java/C++ overload resolution).

### **Examples — Concrete Walkthroughs:**

Six scenarios, one per logical branch in the matcher. Notation: `register(name, [types], variadic?)`, then `match(name, [argTypes])` → result.

```
Registry setup used in all examples below:
  register("foo", ["int", "string"],          false)   // foo(int, string)        — exact
  register("bar", ["int", "int"],             true)    // bar(int, int, ...)      — 1 fixed int, variadic int
  register("baz", ["int", "string"],          false)   // baz(int, string)        — exact
  register("baz", ["int", "string"],          true)    // baz(int, string, ...)   — variadic string
  register("qux", ["string"],                 true)    // qux(string, ...)        — variadic string
  register("qux", ["string", "string"],       true)    // qux(string, string, ..) — 1 fixed string, variadic string

────────────────────────────────────────────────────────────────────────────────
SCENARIO 1 — Simple exact match
  match("foo", ["int", "string"])
  → candidate foo(int, string): not variadic, size 2 == 2, types match ✓
  → RETURN foo(int, string)   [exact match, returns immediately]

SCENARIO 2 — Variadic with ZERO extra args (minimum call)
  match("bar", ["int"])
  → candidate bar(int, int, ...): variadic, fixedCount=2
    argCount=1 >= fixedCount-1=1 ✓
    fixed check: paramTypes[0]="int" == argTypes[0]="int" ✓
    variadic type: paramTypes[1]="int"
    remaining loop: i from 1 to 0 → no iterations → variadicMatch=true ✓
  → RETURN bar(int, int, ...)   [zero extra variadic args is allowed]

SCENARIO 3 — Variadic with multiple extra args (the normal case)
  match("bar", ["int", "int", "int"])
  → candidate bar(int, int, ...): variadic, fixedCount=2
    argCount=3 >= 1 ✓
    fixed check: paramTypes[0]="int" == argTypes[0]="int" ✓
    variadic type: "int"
    remaining: argTypes[1]="int" ✓, argTypes[2]="int" ✓ → variadicMatch=true ✓
  → RETURN bar(int, int, ...)   [2 extra variadic args]

SCENARIO 4 — Type mismatch on variadic extra → no match
  match("bar", ["int", "string"])
  → candidate bar(int, int, ...): variadic, fixedCount=2
    argCount=2 >= 1 ✓
    fixed check: paramTypes[0]="int" == argTypes[0]="int" ✓
    variadic type: "int"
    remaining: argTypes[1]="string" ≠ "int" → variadicMatch=false ✗
  → RETURN null   [no registered function matches]

SCENARIO 5 — Exact match beats variadic when both names match
  match("baz", ["int", "string"])
  → candidate baz(int, string): not variadic, size 2 == 2, types match ✓
  → RETURN baz(int, string)  [exact wins; variadic baz(int, string,...) never evaluated]

SCENARIO 6 — Most specific variadic wins (most fixed params)
  match("qux", ["string", "string", "string"])
  → candidate qux(string, ...): variadic, fixedCount=1
    argCount=3 >= 0 ✓
    fixed check: none (fixedCount-1=0)
    variadic type: "string"
    remaining: all 3 args = "string" ✓ → bestVariadic = qux(string,...), fixedCount=1
  → candidate qux(string, string, ...): variadic, fixedCount=2
    argCount=3 >= 1 ✓
    fixed check: paramTypes[0]="string" == argTypes[0]="string" ✓
    variadic type: "string"
    remaining: argTypes[1]="string" ✓, argTypes[2]="string" ✓ → variadicMatch=true ✓
    fixedCount=2 > bestVariadic.fixedCount=1 → bestVariadic = qux(string, string,...)
  → RETURN qux(string, string, ...)   [more fixed params = more specific]
────────────────────────────────────────────────────────────────────────────────

KEY INVARIANT:
  Exact match always wins and short-circuits.
  Among variadic matches, highest fixedCount wins (most constrained = most specific).
  A variadic call with ZERO extra args is valid — the variadic part simply matches nothing.
```

### **Optimal Solution:**

**Steps in plain English:**

1. **Registry** — HashMap from function name → list of overloaded signatures. `register()` adds to this map.
2. **match()** — lookup candidates by name → iterate all candidates.
3. **Try exact match first** — if not variadic: check same param count + all types match → return immediately (exact always wins).
4. **Try variadic match** — if variadic: check arg count ≥ fixed params - 1 → verify fixed params match → verify all remaining args match the variadic type.
5. **Pick best variadic** — among variadic matches, prefer the one with most fixed parameters (most specific).

```java
import java.util.*;

public class FunctionMatcher {

    private static class FunctionSignature {
        String name;
        List<String> paramTypes;
        boolean isVariadic;

        FunctionSignature(String name, List<String> paramTypes, boolean isVariadic) {
            this.name = name;
            this.paramTypes = paramTypes;
            this.isVariadic = isVariadic;
        }
    }

    // Step 1 — registry: function name → list of overloaded signatures
    private final Map<String, List<FunctionSignature>> registry = new HashMap<>();

    public void register(String name, List<String> paramTypes, boolean isVariadic) {
        registry.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new FunctionSignature(name, paramTypes, isVariadic));
    }

    // Step 2 — match: lookup candidates by name, iterate all
    public FunctionSignature match(String name, List<String> argTypes) {
        List<FunctionSignature> candidates = registry.get(name);
        if (candidates == null) {
            return null;
        }

        FunctionSignature bestVariadic = null;

        for (FunctionSignature sig : candidates) {
            if (!sig.isVariadic) {
                // Step 3 — exact match: same param count + all types match → return immediately
                if (sig.paramTypes.size() == argTypes.size() && typesMatch(sig.paramTypes, argTypes)) {
                    return sig;
                }
            } else {
                // Step 4 — variadic match: check fixed params, then verify remaining args
                int fixedCount = sig.paramTypes.size();
                if (fixedCount == 0 || argTypes.size() < fixedCount - 1) {
                    continue;
                }

                // Verify fixed params match (all except last = the variadic type)
                boolean fixedMatch = true;
                for (int i = 0; i < fixedCount - 1; i++) {
                    if (!sig.paramTypes.get(i).equals(argTypes.get(i))) {
                        fixedMatch = false;
                        break;
                    }
                }
                if (!fixedMatch) {
                    continue;
                }

                // Verify all remaining args match the variadic type
                String variadicType = sig.paramTypes.get(fixedCount - 1);
                boolean variadicMatch = true;
                for (int i = fixedCount - 1; i < argTypes.size(); i++) {
                    if (!variadicType.equals(argTypes.get(i))) {
                        variadicMatch = false;
                        break;
                    }
                }

                // Step 5 — pick best variadic: most fixed params = most specific
                if (variadicMatch) {
                    if (bestVariadic == null || sig.paramTypes.size() > bestVariadic.paramTypes.size()) {
                        bestVariadic = sig;
                    }
                }
            }
        }
        return bestVariadic;
    }

    private boolean typesMatch(List<String> expected, List<String> actual) {
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                return false;
            }
        }
        return true;
    }
}
```

- **Time:** O(F × P) per match call, where F = number of overloads for that function name, P = max parameter count — WHY: for each candidate signature, we compare up to P parameter types.
- **Space:** O(S × P) where S = total signatures registered — each signature stores its parameter type list.

### **🔄 Variants:**

- "Support type inheritance (int matches number)" → Build a type hierarchy map. Replace `equals()` with `isAssignableFrom()`.
- "Support default parameters" → Match if argCount >= requiredParams and argCount <= totalParams.
- "What if multiple exact matches exist?" → Ambiguity error — return null or throw.

### **❓ Cross-Questions:**

- **"Why prioritize exact match over variadic?"** → Same as Java: `foo(int, int)` is more specific than `foo(int, int...)`. Specificity reduces surprise.
- **"What's the time complexity if there are M overloads with the same name?"** → O(M × P). In practice M < 10 for any function name, so effectively O(P).

---

## 7. Notebook Editor

### **🎤 How It's Asked:**

> "Design a text editor class with these methods: `addText(String text)` — inserts text at cursor, `deleteText(int k)` — deletes k characters left of cursor, `cursorLeft(int k)` — move cursor left k positions, `cursorRight(int k)` — move cursor right k positions. Each cursor method returns the last 10 characters to the left of the cursor."

### **Discussion — How to arrive at the solution:**

Brute force with `StringBuilder` + integer cursor position works: insert at index, delete at index, move index. But `StringBuilder.insert(index, text)` and `StringBuilder.deleteCharAt(index)` are O(n) because they shift characters.

Optimal: use two stacks (or `StringBuilder`s) — "left of cursor" and "right of cursor." Insertion appends to left stack. Deletion pops from left stack. Cursor movement transfers characters between stacks. All operations are O(k) where k = amount moved/deleted, not O(n) where n = total text length.

### **Key Insight:**

Split the text at the cursor into two halves:
- `left` stack: characters to the LEFT of cursor (top of stack = character immediately left of cursor)
- `right` stack: characters to the RIGHT of cursor (top of stack = character immediately right of cursor)

Every operation only touches the boundary between the two stacks. Never shifts the entire text.

### **Optimal Solution:**

**Steps in plain English:**

1. **Data structure** — two StringBuilders acting as stacks: `left` (text before cursor) and `right` (text after cursor). Cursor sits at the boundary.
2. **addText** — append to `left` (insert at cursor position).
3. **deleteText** — shrink `left` by k from the end (delete k chars before cursor).
4. **cursorLeft** — transfer k chars from end of `left` to end of `right` (move cursor left).
5. **cursorRight** — transfer k chars from end of `right` to end of `left` (move cursor right).
6. **getLast10** — return last 10 chars of `left` (the 10 chars immediately before cursor).

```java
public class TextEditor {

    // Step 1 — two stacks: left = before cursor, right = after cursor
    private final StringBuilder left = new StringBuilder();
    private final StringBuilder right = new StringBuilder();

    // Step 2 — addText: append to left (inserts at cursor)
    public void addText(String text) {
        left.append(text);
    }

    // Step 3 — deleteText: shrink left by k from the end
    public int deleteText(int k) {
        int toDelete = Math.min(k, left.length());
        left.setLength(left.length() - toDelete);
        return toDelete;
    }

    // Step 4 — cursorLeft: transfer k chars from left end → right end
    public String cursorLeft(int k) {
        int toMove = Math.min(k, left.length());
        for (int i = 0; i < toMove; i++) {
            right.append(left.charAt(left.length() - 1));
            left.setLength(left.length() - 1);
        }
        return getLast10();
    }

    // Step 5 — cursorRight: transfer k chars from right end → left end
    public String cursorRight(int k) {
        int toMove = Math.min(k, right.length());
        for (int i = 0; i < toMove; i++) {
            left.append(right.charAt(right.length() - 1));
            right.setLength(right.length() - 1);
        }
        return getLast10();
    }

    // Step 6 — getLast10: last 10 chars of left = 10 chars before cursor
    private String getLast10() {
        int start = Math.max(0, left.length() - 10);
        return left.substring(start);
    }
}
```

- **Time:** addText: O(t) where t = text length. deleteText: O(1) — just adjust length. cursorLeft/Right: O(k) where k = positions moved — WHY: each move transfers one character between stacks.
- **Space:** O(n) where n = total text length — split across two StringBuilders.

### **🔄 Variants:**

- "Add undo/redo" → Command pattern: push each operation onto an undo stack. Undo reverses the command.
- "Support bold/italic formatting" → Store (char, Set<Style>) pairs instead of raw chars.

### **❓ Cross-Questions:**

- **"Why two stacks instead of one StringBuilder + cursor index?"** → `StringBuilder.insert(index)` shifts all characters after the index — O(n). Two stacks make insert/delete O(1) at the boundary.

---

## 8. Restaurant Menu Optimizer

### **🎤 How It's Asked:**

> "A restaurant has individual items and value meals (combos). Given a customer's order, find the cheapest way to fulfill it — using individual items, value meals, or a mix."
>
> Alternate: "Given item prices and combo prices, minimize total cost for a given set of items."

### **Discussion — How to arrive at the solution:**

This is a subset optimization problem. For each combo, check: does the customer's order contain all items in the combo? If yes, applying that combo saves money. But combos might overlap — using combo A might prevent using combo B. This makes it a combinatorial search.

If the number of combos is small (≤ 15-20), enumerate all subsets of combos with bitmask. For each subset, check if it covers valid items and compute total cost.

### **Key Insight:**

Think of it as "for each subset of applicable combos, compute the cost, pick the minimum." The number of combos is small in any restaurant scenario. With C combos, there are 2^C subsets. For C ≤ 20, this is ~1M — feasible.

For each combo subset:
1. Mark items covered by chosen combos
2. Add individual prices for uncovered items
3. Track minimum total cost

### **Optimal Solution:**

**Steps in plain English:**

1. **Filter applicable combos** — only keep combos whose items are a subset of the customer's order.
2. **Baseline cost** — compute the cost of buying everything individually (no combos).
3. **Enumerate all combo subsets** — use bitmask from 1 to 2^C - 1. Each bit = "include this combo or not."
4. **For each subset** — compute which items are covered by the chosen combos, sum combo prices.
5. **Add individual prices for uncovered items** — items not in any chosen combo are bought individually.
6. **Track minimum** — compare total cost of each subset against current minimum.

```java
import java.util.*;

public class MenuOptimizer {

    public static int findMinCost(
            Map<String, Integer> itemPrices,
            List<Map.Entry<Set<String>, Integer>> combos,
            Set<String> order) {

        // Step 1 — filter: keep only combos whose items are in the order
        List<Map.Entry<Set<String>, Integer>> applicableCombos = new ArrayList<>();
        for (Map.Entry<Set<String>, Integer> combo : combos) {
            if (order.containsAll(combo.getKey())) {
                applicableCombos.add(combo);
            }
        }

        int numCombos = applicableCombos.size();
        // Step 2 — baseline: all items bought individually
        int minCost = individualCost(itemPrices, order);

        // Step 3 — enumerate all subsets of applicable combos via bitmask
        for (int mask = 1; mask < (1 << numCombos); mask++) {
            Set<String> covered = new HashSet<>();
            int comboCost = 0;

            // Step 4 — for this subset: collect covered items + sum combo prices
            for (int i = 0; i < numCombos; i++) {
                if ((mask & (1 << i)) != 0) {
                    Map.Entry<Set<String>, Integer> combo = applicableCombos.get(i);
                    covered.addAll(combo.getKey());
                    comboCost += combo.getValue();
                }
            }

            // Step 5 — add individual prices for items not covered by any combo
            for (String item : order) {
                if (!covered.contains(item)) {
                    comboCost += itemPrices.get(item);
                }
            }

            // Step 6 — track minimum total cost
            minCost = Math.min(minCost, comboCost);
        }
        return minCost;
    }

    private static int individualCost(Map<String, Integer> prices, Set<String> order) {
        int total = 0;
        for (String item : order) {
            total += prices.get(item);
        }
        return total;
    }
}
```

- **Time:** O(2^C × (C + N)) where C = applicable combos, N = order size — WHY: 2^C subsets, each subset requires iterating C combos to compute covered items + N items for uncovered cost.
- **Space:** O(N) — the `covered` set stores at most N items per iteration.

### **🔄 Variants:**

- "What if each combo can be used multiple times?" → Change from subset enumeration to unbounded knapsack DP.
- "What if order has quantities (2 burgers, 1 fries)?" → Track counts, not just sets. Combos reduce counts.
- "Optimize for very large combo count" → DP on item bitmask if items ≤ 20.

### **❓ Cross-Questions:**

- **"Is 2^C feasible?"** → For C ≤ 20, yes (~1M). Real restaurants have < 10 combos. If C > 25, switch to DP or greedy with pruning.
- **"What's the greedy approach?"** → Sort combos by savings-per-item descending, greedily apply best combo first. Doesn't guarantee optimal but good enough for a follow-up discussion.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | File created. 8 design-coding problems covering Confluent Tier 1-3. |
| Aug 2026 | **Critical fix — Problem 1:** Corrected false claim "per-key TTL already handled." DLL eviction assumes monotonic TTLs (insertion order = expiry order). Added failure trace, TreeMap fix, and tradeoff table. Fixed Key Insight and Discussion to state the assumption explicitly. |
