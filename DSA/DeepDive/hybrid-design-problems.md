# Hybrid Design Problems — DSA + LLD

> For Kapil: problems that **combine a data structure choice with a system design** — the category that caught you in the WEX interview (LRU Cache with TTL). Read mental model first, problems second.

---

## 🎯 Why You're Reading This

In a real interview, "design an LRU Cache" is not a DSA question and not a system design question — it's **both simultaneously**. You need to:

1. Pick the right data structure combination (the DSA half)
2. Design a clean interface with variants (the LLD half)
3. Handle thread-safety and edge cases (the SDE-3 half)

This doc covers the **4 most frequently asked hybrid problems** at depth, plus cross-references for 3 more. The WEX miss (LRU + TTL) is covered in the most detail.

> **Lesson learned the hard way (June 2026):** In the WEX hiring manager round, LRU Cache with TTL was asked cold. Remembered PriorityQueue for TTL (correct), but blanked on DoublyLinkedList for LRU eviction — said HashMap only. Missing the second data structure is the most common failure mode on these problems. That's what this doc fixes.

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts in this doc | Open LeetCode, attempt cold, time-box ~30 min |
| 🟡 **Try After [Section]** | Needs a later section in this doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc | Read problem + editorial for awareness only |

---

## 🧠 Mental Model — The "HashMap + X" Pattern

> **The core insight:** HashMap gives you O(1) lookup by key. In a design problem, O(1) lookup is never enough — you ALSO need to maintain some ordering (by recency, frequency, value, time). HashMap alone cannot do that ordering. So you pair it with a second data structure that does exactly the ordering you need.

Every hybrid design problem decomposes into two questions:

```
Q1: What do I look up?        → HashMap answers this in O(1)
Q2: What do I need to order?  → The second DS answers this
```

The problem title tells you what Q2 requires:

| Problem | What ordering is needed | Second DS |
| --- | --- | --- |
| LRU Cache | Evict the Least Recently Used | DoublyLinkedList |
| LFU Cache | Evict the Least Frequently Used | Frequency buckets (HashMap of LinkedHashSets) |
| Hit Counter | Count hits in last N seconds | Deque (sliding window) or circular array |
| Leaderboard | Top-K players by score | TreeMap (score-sorted, O(log N) add/remove) |
| Rate Limiter | Count requests in sliding window | Deque or AtomicLong + scheduler |
| Consistent Hashing | Find the nearest node clockwise | TreeMap as circular ring |
| Task Scheduler | Run the next due task | PriorityQueue (min-heap by run time) |

### 🎨 Visual — The HashMap + X Shape

```
Every hybrid problem has this anatomy:

 ┌──────────────────────────────────────────────────────┐
 │                                                      │
 │   HashMap<Key, Node/Value>                           │
 │   ─────────────────────────                          │
 │   • O(1) lookup "is this key here?"                  │
 │   • O(1) get/update the node itself                  │
 │                                                      │
 │         ↕  Node is SHARED between both structures   │
 │                                                      │
 │   Second Data Structure                              │
 │   ─────────────────────────                          │
 │   • Maintains the ORDERING you need                  │
 │   • Drives eviction / ranking / windowing            │
 │                                                      │
 └──────────────────────────────────────────────────────┘

The Node is the bridge:
  HashMap points to Node → O(1) access
  Second DS contains Node → O(1) or O(log N) ordering ops

Without the second DS:     With the second DS:
  get()   → O(1) ✅          get()   → O(1) ✅
  put()   → O(1) ✅          put()   → O(1) ✅
  evict() → O(N) ❌          evict() → O(1) ✅
```

**KEY INVARIANT:** The Node/value object is always stored in BOTH structures simultaneously. The HashMap finds it in O(1); the second DS positions it for ordering in O(1) or O(log N). The power is in the shared reference — one update touches both.

---

## 📖 Terminology Table

| Term | Plain-English meaning |
| --- | --- |
| **Eviction policy** | The rule for deciding which entry to remove when the cache is full |
| **LRU** | Least Recently Used — evict whichever entry was accessed longest ago |
| **LFU** | Least Frequently Used — evict whichever entry has the lowest access count |
| **TTL** | Time-To-Live — entries expire after a fixed duration regardless of access |
| **DoublyLinkedList** | A linked list where each node has pointers to both its prev and next neighbors, enabling O(1) removal from any position |
| **Sentinel node** | A dummy head/tail node that always exists, so you never have to handle "list is empty" as a special case |
| **Sliding window** | A time-bounded window that moves forward — old entries fall off the back, new entries enter the front |
| **Circular array** | An array where index is `time % size`, reusing slots as time advances |
| **TreeMap** | Java's sorted Map backed by a red-black tree — O(log N) put/remove, O(log N) first()/last(), ceiling()/floor() |
| **LinkedHashMap** | Java's HashMap that preserves insertion order OR access order — the JDK shortcut for LRU |
| **minFreq** | A pointer tracking the current minimum frequency bucket — critical for O(1) LFU eviction |

---

## 🔹 The 4 Second-DS Archetypes

Quick reference before the problem deep dives.

### Archetype 1 — DoublyLinkedList (for recency ordering)

Use when: you need O(1) move-to-front and O(1) evict-from-tail.

```
head ↔ [most recent] ↔ ... ↔ [least recent] ↔ tail
        ▲ promote here                  ▲ evict here
```

Signature operations:
- `moveToFront(node)` — unlink from current position, relink after head
- `removeLast()` — unlink the node before tail, return it

### Archetype 2 — PriorityQueue / TreeMap (for value/time ordering)

Use when: you need O(log N) min/max retrieval, or ranked ordering.
- `PriorityQueue` — min/max heap, O(log N) add/poll, O(1) peek
- `TreeMap` — sorted map, O(log N) all ops, supports range queries

### Archetype 3 — Deque (for time-window sliding)

Use when: you need a sliding window where old entries expire and new ones are added.
- `ArrayDeque` in Java — O(1) addLast, removeFirst, peekFirst
- Pattern: remove expired entries from front, add new to back, count size

### Archetype 4 — Frequency Buckets (for frequency ordering)

Use when: you need O(1) LFU operations.
- `HashMap<Integer, LinkedHashSet<K>>` — maps frequency → set of keys with that frequency
- Keeps `minFreq` pointer to know which bucket to evict from

---

## 🔬 Problem 1 — LRU Cache (LC 146)

> ⭐ Most important problem in this doc — the one asked at WEX.

### What it is

Design a cache with capacity N. Supports `get(key)` and `put(key, value)`. When putting into a full cache, evict the Least Recently Used entry. Every `get` and `put` counts as a "use."

### DS Combo

`HashMap<Integer, Node>` + `DoublyLinkedList`

- HashMap: O(1) lookup "does key X exist, and where is its node?"
- DoublyLinkedList: O(1) promote-to-front on access, O(1) evict-from-tail

### 🎨 Visual — LRU State After Each Operation

```
Capacity = 3. Operations: put(1,A), put(2,B), put(3,C), get(1), put(4,D)

After put(1,A), put(2,B), put(3,C):
  head ↔ [3,C] ↔ [2,B] ↔ [1,A] ↔ tail
  HashMap: {1→node1, 2→node2, 3→node3}

After get(1):          ← move node1 to front
  head ↔ [1,A] ↔ [3,C] ↔ [2,B] ↔ tail

After put(4,D):        ← cache full, evict tail.prev = [2,B]
  head ↔ [4,D] ↔ [1,A] ↔ [3,C] ↔ tail
  HashMap: {1→node1, 3→node3, 4→node4}   ← key 2 removed
```

**KEY INVARIANT:** The node closest to `head` is the most recently used; the node closest to `tail` is the eviction candidate. Every access promotes the node to head in O(1); every eviction removes from tail in O(1).

### Steps in plain English

1. **Node class** — holds key, value, prev pointer, next pointer.
2. **Sentinel head and tail** — always-present dummies; actual entries live between them. Eliminates null checks.
3. **moveToFront(node)** — unlink the node from wherever it is, relink it right after head.
4. **removeLast()** — unlink the node just before tail, remove it from the HashMap, return it.
5. **get(key)** — if key in map: call moveToFront, return value. Else return -1.
6. **put(key, value)** — if key exists: update value, moveToFront. Else: create node, add to map, insert after head. If size > capacity: removeLast().

```java
class LRUCache {
    // Step 1 — Node holds key+value + DLL pointers
    private static class Node {
        int key;
        int val;
        Node prev;
        Node next;
        Node(int key, int val) {
            this.key = key;
            this.val = val;
        }
    }

    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    // Step 2 — sentinel head and tail (never evicted, never returned)
    private final Node head = new Node(0, 0);
    private final Node tail = new Node(0, 0);

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    // Step 3 — unlink node from current position, re-insert after head
    private void moveToFront(Node node) {
        unlink(node);
        insertAfterHead(node);
    }

    private void unlink(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void insertAfterHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    // Step 4 — remove LRU node (just before tail), delete from map
    private void removeLast() {
        Node lru = tail.prev;
        unlink(lru);
        map.remove(lru.key);
    }

    // Step 5 — get: lookup + promote
    public int get(int key) {
        if (!map.containsKey(key)) {
            return -1;
        }
        Node node = map.get(key);
        moveToFront(node);
        return node.val;
    }

    // Step 6 — put: update or insert, then evict if over capacity
    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            moveToFront(node);
        } else {
            Node node = new Node(key, value);
            map.put(key, node);
            insertAfterHead(node);
            if (map.size() > capacity) {
                removeLast();
            }
        }
    }
}
```

**Time:** get O(1), put O(1), eviction O(1) | **Space:** O(capacity)

> 🧩 **Drill — do this NOW before reading the variants:**
> On paper, draw the DLL state after: `put(1,1)`, `put(2,2)`, `get(1)`, `put(3,3)`.
> What gets evicted when `put(3,3)` is called? (Answer: key 2 — it's the LRU after get(1) promoted key 1.)

---

### Variants — How LRU Gets Asked in Real Interviews

**Variant 1 — LRU with TTL (the WEX question)**

> *"Entries should also expire after a given TTL. An expired entry should not be returned by get()."*

Additional DS: `PriorityQueue<Node>` (min-heap by `expiryTime`).

Strategy:
- Each node also stores `long expiryTime = System.currentTimeMillis() + ttlMs`
- On `get()`: check if `System.currentTimeMillis() > node.expiryTime` → if yes, evict and return -1
- Lazy eviction: don't scan the heap on every get. Only clean up when the heap top is expired OR when you need to evict for capacity.
- On `put()`: add node to heap. Before inserting, drain expired entries from heap top.

```java
// Node gains expiryTime field
private static class Node {
    int key;
    int val;
    Node prev;
    Node next;
    long expiryTime;
    Node(int key, int val, long ttlMs) {
        this.key = key;
        this.val = val;
        this.expiryTime = System.currentTimeMillis() + ttlMs;
    }
}

// min-heap by expiryTime
private final PriorityQueue<Node> expiryHeap =
    new PriorityQueue<>(Comparator.comparingLong(n -> n.expiryTime));

public int get(int key) {
    if (!map.containsKey(key)) {
        return -1;
    }
    Node node = map.get(key);
    // check TTL expiry
    if (System.currentTimeMillis() > node.expiryTime) {
        unlink(node);
        map.remove(key);
        return -1;
    }
    moveToFront(node);
    return node.val;
}
```

**Time:** get O(log N) amortized (heap drain), put O(log N) | **Space:** O(capacity)

---

**Variant 2 — Thread-safe LRU**

> *"Multiple threads will call get() and put() concurrently. Make it safe."*

Two options:

Option A — `synchronized` on the whole cache. Simple, correct, single-lock.

```java
public synchronized int get(int key) { ... }
public synchronized void put(int key, int value) { ... }
```

Option B — `ReentrantReadWriteLock`. Multiple readers can get() concurrently; put() takes exclusive write lock.

```java
private final ReadWriteLock lock = new ReentrantReadWriteLock();

public int get(int key) {
    lock.readLock().lock();
    try {
        // ... but moveToFront is a WRITE — so this needs write lock too
    } finally {
        lock.readLock().unlock();
    }
}
```

**Gotcha:** `get()` causes a structural change (moveToFront). So ReadWriteLock doesn't actually help here — get() needs the write lock. In practice: use `synchronized` or `ConcurrentLinkedHashMap` (Guava) for true concurrent LRU.

Option C — Java shortcut using `LinkedHashMap` (for simple cases, not multi-threaded):

```java
class LRUCache extends LinkedHashMap<Integer, Integer> {
    private final int capacity;

    public LRUCache(int capacity) {
        // true = access-order (most recent at tail)
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    public int get(int key) {
        return super.getOrDefault(key, -1);
    }

    public void put(int key, int value) {
        super.put(key, value);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        return size() > capacity;
    }
}
```

---

**Variant 3 — LRU-K (evict the entry whose K-th most recent access is oldest)**

> *"Standard LRU considers only the last access. LRU-K considers the K-th most recent access time."*

Used in database buffer management (PostgreSQL). Each entry stores a deque of the last K access timestamps. The eviction candidate is the entry with the oldest K-th timestamp.

Tag: 🔴 Reference Only. Know it exists, don't implement cold.

---

**Variant 4 — How they phrase it**

| Phrasing | It's still LRU Cache |
| --- | --- |
| "Design a browser history cache" | LRU, capacity = N pages |
| "Design a database buffer pool" | LRU or LRU-K eviction |
| "Design an API response cache that expires stale data" | LRU + TTL (Variant 1) |
| "Your cache should be safe for concurrent web requests" | Thread-safe LRU (Variant 2) |
| "Implement get and put in O(1)" | The headline constraint that forces DLL |

---

## 🔬 Problem 2 — LFU Cache (LC 460)

> "Design a cache. When full, evict the Least Frequently Used entry. Ties broken by LRU (evict the least recently used among equally-frequent entries)."

### DS Combo

Three HashMaps + `minFreq` pointer:

| Structure | Key | Value | Purpose |
| --- | --- | --- | --- |
| `keyToVal` | key | value | O(1) value lookup |
| `keyToFreq` | key | frequency | O(1) frequency lookup |
| `freqToKeys` | frequency | `LinkedHashSet<Key>` | O(1) get all keys at that frequency (insertion order = LRU tiebreak) |

`minFreq` — integer tracking the current minimum frequency. The eviction candidate is always `freqToKeys.get(minFreq).first()`.

### 🎨 Visual — LFU State After Operations

```
Capacity = 2. put(1,A), put(2,B), get(1), put(3,C)

After put(1,A), put(2,B):
  keyToVal:  {1→A, 2→B}
  keyToFreq: {1→1, 2→1}
  freqToKeys: {1 → [1, 2]}   (insertion order: 1 first, then 2)
  minFreq = 1

After get(1):              ← freq[1] goes 1→2
  keyToFreq: {1→2, 2→1}
  freqToKeys: {1 → [2], 2 → [1]}
  minFreq = 1              ← still 1 (key 2 is at freq 1)

After put(3,C):            ← cache full, evict from freqToKeys[minFreq=1]
  Evict key 2 (first in freq-1 bucket = LRU among freq-1 keys)
  Add key 3 with freq 1
  freqToKeys: {1 → [3], 2 → [1]}
  minFreq = 1              ← reset to 1 because we inserted a new key
```

**KEY INVARIANT:** `freqToKeys` maps each frequency to the set of keys at that frequency, in insertion order (so first = LRU). `minFreq` always points to the bucket containing the eviction candidate. It resets to 1 on every put() of a new key.

### Steps in plain English

1. **On get(key):** increment key's frequency, move key from old freq bucket to new freq bucket, update minFreq if old bucket is now empty and equals minFreq.
2. **On put(key, value):** if key exists, update value and call get() logic to bump frequency. If key is new: evict if full (remove from `freqToKeys[minFreq]`), insert with freq=1, reset minFreq=1.

```java
class LFUCache {
    private final int capacity;
    private int minFreq;
    private final Map<Integer, Integer> keyToVal = new HashMap<>();
    private final Map<Integer, Integer> keyToFreq = new HashMap<>();
    // Step 1 — LinkedHashSet preserves insertion order = LRU tiebreak
    private final Map<Integer, LinkedHashSet<Integer>> freqToKeys = new HashMap<>();

    public LFUCache(int capacity) {
        this.capacity = capacity;
    }

    public int get(int key) {
        if (!keyToVal.containsKey(key)) {
            return -1;
        }
        // Step 1 — bump frequency
        bumpFreq(key);
        return keyToVal.get(key);
    }

    public void put(int key, int value) {
        if (capacity <= 0) {
            return;
        }
        if (keyToVal.containsKey(key)) {
            keyToVal.put(key, value);
            bumpFreq(key);
            return;
        }
        // Step 2 — evict if full
        if (keyToVal.size() >= capacity) {
            evictLFU();
        }
        // Step 2 — insert new key with freq 1
        keyToVal.put(key, value);
        keyToFreq.put(key, 1);
        freqToKeys.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    private void bumpFreq(int key) {
        int freq = keyToFreq.get(key);
        keyToFreq.put(key, freq + 1);
        freqToKeys.get(freq).remove(key);
        if (freqToKeys.get(freq).isEmpty()) {
            freqToKeys.remove(freq);
            if (minFreq == freq) {
                minFreq++;
            }
        }
        freqToKeys.computeIfAbsent(freq + 1, k -> new LinkedHashSet<>()).add(key);
    }

    private void evictLFU() {
        LinkedHashSet<Integer> minFreqKeys = freqToKeys.get(minFreq);
        // first() = insertion order = least recently used among min-freq keys
        int evictKey = minFreqKeys.iterator().next();
        minFreqKeys.remove(evictKey);
        if (minFreqKeys.isEmpty()) {
            freqToKeys.remove(minFreq);
        }
        keyToVal.remove(evictKey);
        keyToFreq.remove(evictKey);
    }
}
```

**Time:** get O(1), put O(1) | **Space:** O(capacity)

### Variants

| Phrasing | It's still LFU |
| --- | --- |
| "Evict the least popular entry" | LFU |
| "TTL + LFU combined" | Add expiryTime to each node + PriorityQueue drain |
| "What if two entries have the same frequency?" | LRU tiebreak — already handled by LinkedHashSet insertion order |

> 🧩 **Try these:**
> - ✅ LC 146 LRU Cache — prerequisite for LFU; do this first
> - 🟡 LC 460 LFU Cache — attempt after fully understanding LFU steps above
> - 🔴 LC 432 All O(1) Data Structure — increment/decrement with O(1) getMaxKey/getMinKey; needs doubly-linked list of buckets

---

## 🔬 Problem 3 — Hit Counter (LC 362)

> "Design a hit counter. `hit(timestamp)` records a hit at that second. `getHits(timestamp)` returns the count of hits in the past 5 minutes (300 seconds). Timestamps arrive in non-decreasing order."

### DS Combo — Option A: Circular Array (O(1) everything)

`int[] times[300]` + `int[] hits[300]`

- Index into array: `timestamp % 300`
- If `times[slot] == timestamp`: increment `hits[slot]`
- If `times[slot] != timestamp`: this slot is from a different second (≥300s ago) — reset it

### 🎨 Visual — Circular Array Slots

```
Array size = 300 (one slot per second in a 5-minute window)

timestamp=1  → slot 1%300=1   → times[1]=1,  hits[1]=1
timestamp=2  → slot 2%300=2   → times[2]=2,  hits[2]=1
timestamp=301 → slot 301%300=1 → times[1] was 1 ≠ 301 → RESET times[1]=301, hits[1]=1

getHits(timestamp=302):
  Scan all 300 slots
  If times[slot] > timestamp - 300:  count += hits[slot]
  (slot 1 has times[1]=301 > 2 → included)
  (slot 2 has times[2]=2 > 2 → NOT included — too old)
```

**KEY INVARIANT:** `times[slot]` acts as a "generation tag." If it matches the current timestamp, the slot belongs to this second. If it doesn't, the slot's data is stale and must be reset. This makes the circular array self-cleaning without an explicit expiry step.

```java
class HitCounter {
    private final int[] times = new int[300];
    private final int[] hits = new int[300];

    public void hit(int timestamp) {
        int slot = timestamp % 300;
        if (times[slot] != timestamp) {
            // Step 1 — stale slot from 300+ seconds ago, reset it
            times[slot] = timestamp;
            hits[slot] = 1;
        } else {
            // Step 2 — same second, increment
            hits[slot]++;
        }
    }

    public int getHits(int timestamp) {
        int total = 0;
        for (int i = 0; i < 300; i++) {
            // Step 3 — only count slots within the last 300 seconds
            if (timestamp - times[i] < 300) {
                total += hits[i];
            }
        }
        return total;
    }
}
```

**Time:** hit O(1), getHits O(300) = O(1) | **Space:** O(300) = O(1)

---

### DS Combo — Option B: Deque (easier to explain, handles variable windows)

`Deque<Integer>` of timestamps (one entry per hit).

- `hit(ts)`: addLast(ts)
- `getHits(ts)`: while peekFirst() <= ts - 300, removeFirst(). Return size().

**Time:** hit O(1), getHits O(N) worst case (N = hits in window) | **Space:** O(N)

Use this when the window size is not fixed (e.g., "last N seconds" where N varies).

### Variants

| Phrasing | It's still Hit Counter |
| --- | --- |
| "Count requests per user in the last minute" | Rate limiter = Hit Counter per key |
| "Multi-granularity: hits per second, minute, hour" | Three circular arrays, one per granularity |
| "Thread-safe hit counter" | `synchronized hit/getHits` or `AtomicIntegerArray` for hits[] |

> 🧩 **Try these:**
> - ✅ LC 362 Design Hit Counter — use circular array template above

---

## 🔬 Problem 4 — Leaderboard (LC 1244)

> "Design a Leaderboard. `addScore(playerId, score)` adds (not replaces) score. `top(K)` returns sum of scores of top K players. `reset(playerId)` resets score to 0."

### DS Combo

`HashMap<Integer, Integer>` (playerId → score) + `TreeMap<Integer, Integer>` (score → count of players with that score)

- HashMap: O(1) lookup of a player's current score
- TreeMap: sorted by score, supports O(log N) add/remove, O(K log N) top-K traversal

### 🎨 Visual — Leaderboard State

```
addScore(1, 73), addScore(2, 56), addScore(3, 39), addScore(4, 51), addScore(5, 4)

HashMap:    {1→73, 2→56, 3→39, 4→51, 5→4}
TreeMap:    {4→1, 39→1, 51→1, 56→1, 73→1}
               ↑ score → count of players with that score

top(3):
  Iterate TreeMap in DESCENDING order (highest scores first)
  Take 73 (count=1, total=73, remaining K=2)
  Take 56 (count=1, total=129, remaining K=1)
  Take 51 (count=1, total=180, remaining K=0)
  Return 180
```

**KEY INVARIANT:** TreeMap is keyed by score (not playerId). This allows O(log N) score insertions and O(K) top-K traversal regardless of total players. When updating a player's score, remove the old score from TreeMap (decrement count), add the new score.

```java
class Leaderboard {
    private final Map<Integer, Integer> playerScore = new HashMap<>();
    // TreeMap: score → number of players with that score
    private final TreeMap<Integer, Integer> scoreCount = new TreeMap<>();

    public void addScore(int playerId, int score) {
        int currentScore = playerScore.getOrDefault(playerId, 0);
        int newScore = currentScore + score;
        playerScore.put(playerId, newScore);

        // Step 1 — remove old score from TreeMap
        if (currentScore > 0) {
            scoreCount.put(currentScore,
                scoreCount.get(currentScore) - 1);
            if (scoreCount.get(currentScore) == 0) {
                scoreCount.remove(currentScore);
            }
        }

        // Step 2 — add new score to TreeMap
        scoreCount.put(newScore,
            scoreCount.getOrDefault(newScore, 0) + 1);
    }

    public int top(int k) {
        int total = 0;
        int remaining = k;
        // Step 3 — iterate descending (highest scores first)
        for (Map.Entry<Integer, Integer> entry :
                scoreCount.descendingMap().entrySet()) {
            int score = entry.getKey();
            int count = entry.getValue();
            int take = Math.min(remaining, count);
            total += score * take;
            remaining -= take;
            if (remaining == 0) {
                break;
            }
        }
        return total;
    }

    public void reset(int playerId) {
        int currentScore = playerScore.getOrDefault(playerId, 0);
        playerScore.remove(playerId);
        if (currentScore > 0) {
            scoreCount.put(currentScore,
                scoreCount.get(currentScore) - 1);
            if (scoreCount.get(currentScore) == 0) {
                scoreCount.remove(currentScore);
            }
        }
    }
}
```

**Time:** addScore O(log N), top O(K log N), reset O(log N) | **Space:** O(N)

### Variants

| Phrasing | It's still Leaderboard |
| --- | --- |
| "Return top K player IDs, not their sum" | Store `TreeMap<Integer, List<Integer>>` (score → list of playerIds) |
| "Score is set, not added" | Simpler: direct put in both maps |
| "Find rank of a player" | `scoreCount.tailMap(score + 1)` counts players above this score |
| "Concurrent leaderboard" | `ConcurrentSkipListMap` instead of TreeMap |

> 🧩 **Try these:**
> - ✅ LC 1244 Design a Leaderboard — template above is nearly the solution
> - 🟡 LC 703 Kth Largest Element in a Stream — min-heap keeps top-K, no TreeMap needed
> - 🔴 LC 295 Find Median from Data Stream — two-heap approach, needs heap deep dive

---

## 🔗 Cross-References (Rate Limiter, Consistent Hashing, Task Scheduler)

These three are also hybrid problems but **live in SystemDesignConcepts** (they have more system design depth than DSA depth):

| Problem | DS Core | System Add-on | Lives in |
| --- | --- | --- | --- |
| **Rate Limiter** | Deque (sliding window) or AtomicLong (token bucket) | Per-user keys, Redis Lua scripts, distributed counting | `SystemDesignConcepts/02-rate-limiting.md` (planned) |
| **Consistent Hashing** | `TreeMap<Long, Node>` as circular ring, `ceilingKey()` for lookup | Virtual nodes, node add/remove, hotspot prevention | `SystemDesignConcepts/05-consistent-hashing.md` (planned) |
| **Task Scheduler** | `PriorityQueue<Task>` sorted by nextRunTime | Delayed retry, recurring jobs, executor design | `SystemDesignConcepts/` (Phase 2 — not yet written) |

---

## ⚠️ Gotchas — Silent Bug Hall of Fame

**Gotcha 1 — LRU: forgetting the `key` field in the DLL Node.**

The DLL Node MUST store the key, not just the value. When you evict the tail node, you remove it from the HashMap using `node.key`. Without the key in the node, you can't do the HashMap removal.

```java
// ❌ wrong — node doesn't know its own key
private static class Node {
    int val;
    Node prev, next;
}
// When removeLast() runs, you can't do map.remove(???)

// ✅ right
private static class Node {
    int key;   // ← mandatory
    int val;
    Node prev, next;
}
```

---

**Gotcha 2 — LRU: moveToFront during put() for existing keys.**

When `put(key, value)` updates an existing key, it must ALSO call moveToFront. Forgetting this means updated keys don't count as "recently used" and get evicted too early.

```java
// ❌ wrong — updates value but doesn't update recency
public void put(int key, int value) {
    if (map.containsKey(key)) {
        map.get(key).val = value;
        // forgot: moveToFront(map.get(key))
    }
}

// ✅ right
public void put(int key, int value) {
    if (map.containsKey(key)) {
        Node node = map.get(key);
        node.val = value;
        moveToFront(node);
    }
}
```

---

**Gotcha 3 — LFU: minFreq reset on new key insertion.**

Whenever you insert a brand new key (not an update), reset `minFreq = 1`. A new key always has frequency 1. If you don't reset, you might evict from the wrong bucket.

```java
// ❌ wrong — doesn't reset minFreq
public void put(int key, int value) {
    // ... insert new key with freq 1 ...
    freqToKeys.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
    // forgot: minFreq = 1;
}

// ✅ right
freqToKeys.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
minFreq = 1;
```

---

**Gotcha 4 — LFU: after bumpFreq, check if the old freq bucket is now empty before updating minFreq.**

```java
// ❌ wrong — blindly increments minFreq
private void bumpFreq(int key) {
    int freq = keyToFreq.get(key);
    freqToKeys.get(freq).remove(key);
    minFreq++;   // WRONG — minFreq only increments if the old bucket is empty
}

// ✅ right
private void bumpFreq(int key) {
    int freq = keyToFreq.get(key);
    keyToFreq.put(key, freq + 1);
    freqToKeys.get(freq).remove(key);
    if (freqToKeys.get(freq).isEmpty()) {
        freqToKeys.remove(freq);
        if (minFreq == freq) {
            minFreq++;   // only bump if the emptied bucket was the minimum
        }
    }
    freqToKeys.computeIfAbsent(freq + 1, k -> new LinkedHashSet<>()).add(key);
}
```

---

**Gotcha 5 — Hit Counter: using `>=` vs `>` for the 300-second window.**

"Hits in the past 300 seconds" including the current second means `timestamp - times[i] < 300`, NOT `<= 300`.

```java
// ❌ wrong — excludes hits from exactly 300s ago (within the window)
if (timestamp - times[i] < 300) { ... }

// ✅ right (depends on problem — re-read: "in the past 5 minutes" = [ts-299, ts])
// if current second counts: timestamp - times[i] <= 299 i.e. < 300
// if problem says "strictly past": timestamp - times[i] < 300
// The LC 362 version: hits in [ts-299, ts], so < 300 is correct
```

---

**Gotcha 6 — Leaderboard: forgetting to handle score=0 when resetting.**

After `reset()`, calling `addScore()` again must not try to remove a score of 0 from the TreeMap (key 0 was never added).

```java
// ✅ guard against removing 0 from TreeMap
public void reset(int playerId) {
    int currentScore = playerScore.getOrDefault(playerId, 0);
    playerScore.remove(playerId);
    if (currentScore > 0) {   // ← guard
        scoreCount.put(currentScore, scoreCount.get(currentScore) - 1);
        if (scoreCount.get(currentScore) == 0) {
            scoreCount.remove(currentScore);
        }
    }
}
```

---

**Gotcha 7 — Thread-safety: get() on LRU is a write operation.**

Beginners try to use ReadWriteLock, giving get() the read lock. But get() calls moveToFront() which mutates the DLL. It needs the WRITE lock.

```java
// ❌ wrong — get() mutates, so readLock is incorrect
public int get(int key) {
    lock.readLock().lock();   // WRONG
    try { ... moveToFront(node); ... }
    finally { lock.readLock().unlock(); }
}

// ✅ right — or just use synchronized for simplicity
public synchronized int get(int key) { ... }
public synchronized void put(int key, int value) { ... }
```

---

## 🗺️ Practice Plan

Time-box each problem to 35 minutes. If stuck after 25 min, read the template above, then re-attempt.

> **Reminder of tags:** ✅ Try Now · 🟡 Try after named prerequisite · 🔴 Reference Only

---

### Tier 1 — Foundational 2 (do these before anything else)

These two problems force you to build the DLL from scratch. Until you can do this from memory, you're not ready for the variants.

1. ✅ **LC 146 LRU Cache** — implement from scratch using the DLL template in this doc
2. ✅ **LC 362 Design Hit Counter** — circular array variant first, deque variant second

---

### Tier 2 — Core Hybrid Problems

3. ✅ **LC 1244 Design a Leaderboard** — HashMap + TreeMap template
4. 🟡 **LC 460 LFU Cache** — attempt after LC 146 is muscle memory; the three-HashMap approach is counter-intuitive without that foundation

---

### Tier 3 — Variants and Extensions

5. 🟡 **LRU Cache with TTL** — add PriorityQueue to LC 146 (variant described in this doc; attempt after Tier 2)
6. 🟡 **LC 703 Kth Largest Element in a Stream** — min-heap keeps a window of top-K; simpler than leaderboard
7. 🟡 **LC 295 Find Median from Data Stream** — two heaps (max-heap lower half, min-heap upper half); attempt after heap deep dive

---

### Tier 4 — Reference Only (multi-pattern / advanced)

8. 🔴 **LC 432 All O(1) Data Structure** — doubly-linked list of frequency buckets with O(1) getMaxKey/getMinKey; hard to design cold; read editorial
9. 🔴 **Consistent Hashing** — see `SystemDesignConcepts/05-consistent-hashing.md` (planned)
10. 🔴 **Rate Limiter** — see `SystemDesignConcepts/02-rate-limiting.md` (planned)

---

### How to use this plan

- **Pace:** 1 problem per session, not 3 in one night
- **When stuck:** at 25 min, re-read the template, not the answer
- **Revision:** after Tier 1-2, redo LC 146 completely from memory (DLL + HashMap, no LinkedHashMap shortcut)
- **Victory criterion:** can implement LRU Cache (DLL variant, not LinkedHashMap) and LRU+TTL from scratch in under 30 minutes without looking at notes

---

## 🧾 TL;DR — One-Page Summary

- **The pattern** = HashMap (O(1) lookup) + second DS (ordering/eviction/windowing)
- **The 4 archetypes of "second DS":** DoublyLinkedList (recency), TreeMap/PriorityQueue (value ordering), Deque (time window), Frequency buckets (frequency ordering)
- **LRU Cache:** HashMap + DLL | get O(1), put O(1) | Node must store key | sentinel head/tail eliminates null checks
- **LRU + TTL:** add PriorityQueue min-heap by expiryTime | get checks expiry before returning | drain heap on put
- **LFU Cache:** 3 HashMaps + minFreq pointer | all O(1) | reset minFreq=1 on new key insert | LinkedHashSet preserves LRU tiebreak
- **Hit Counter:** circular array (O(1) time/space) or Deque (O(N) space, handles variable windows)
- **Leaderboard:** HashMap + TreeMap | addScore O(log N), top O(K log N) | score is the TreeMap key, not playerId
- **Rate Limiter:** → see `SystemDesignConcepts/02-rate-limiting.md`
- **Gotcha 1 (LRU):** Node must store `key` — you need it for HashMap.remove() on eviction
- **Gotcha 2 (LFU):** reset minFreq=1 on every new key insert, nowhere else
- **Gotcha 3 (thread-safety):** LRU's get() is a write operation (calls moveToFront) — it needs the write lock
- **Tier 1 (must master):** LC 146, LC 362
- **Tier 2:** LC 1244, LC 460
- **WEX lesson (June 2026):** answered PriorityQueue for TTL ✅ but missed DoublyLinkedList for LRU ❌. Second DS is always the miss.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | Created. Covers LRU, LFU, Hit Counter, Leaderboard at depth + cross-refs for Rate Limiter, Consistent Hashing, Task Scheduler. Triggered by WEX LRU+TTL interview miss. |
