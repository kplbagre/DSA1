# Hybrid Design Problems — DSA + LLD

> For Kapil: problems that **combine a data structure choice with a system design** — the category that caught you in the WEX interview (LRU Cache with TTL). Read mental model first, problems second.

---

## 🎯 Why You're Reading This

In a real interview, "design an LRU Cache" is not a DSA question and not a system design question — it's **both simultaneously**. You need to:

1. Pick the right data structure combination (the DSA half)
2. Design a clean interface with variants (the LLD half)
3. Handle thread-safety and edge cases (the SDE-3 half)

This doc covers the **5 most frequently asked hybrid problems** at depth, plus cross-references for 2 more. The WEX miss (LRU + TTL) is covered in the most detail.

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

## 🔬 Problem 5 — Rate Limiter

> Design a per-user API rate limiter. Each user is allowed at most `maxRequests` calls within a `windowMs`-millisecond window. Implement `boolean allowRequest(String userId)` — return `true` if the request is allowed, `false` if throttled.

### What it is

The rate limiter is unique among hybrid problems: **there is no single correct data structure**. Four algorithms solve the same problem with different tradeoffs. Interviews test whether you know all four and can pick the one that fits the given constraints. The most common failure mode: defaulting to one algorithm without mentioning the others.

### DS Combo — Algorithm Picker

| Algorithm | Data Structure | When to pick | Burst behavior |
| --- | --- | --- | --- |
| **Fixed Window Counter** | `Map<String, long[]>` — {count, windowStart} | "Simplest implementation" | Allows 2× burst at window boundary |
| **Sliding Window Log** | `Map<String, Deque<Long>>` — timestamps | "Exact correctness required" | Accurate; O(maxRequests) memory per user |
| **Token Bucket** ⭐ | `Map<String, double[]>` — {tokens, lastRefillMs} | "Real-world default / allow bursts" | Natural burst from accumulated tokens |
| **Sliding Window Counter** | `Map<String, long[]>` — {prev, curr, winStart} | "O(1) memory, approximate is OK" | Weighted estimate, ~5% error |

> **Interview default:** Start with Token Bucket and explain the tradeoff table. If the interviewer says "exact accuracy" → pivot to Sliding Window Log. If they say "simplest" → Fixed Window Counter.

---

### Algorithm 1 — Fixed Window Counter

**DS:** `Map<String, long[]>` where `entry = [requestCount, windowStartMs]`

Divide time into fixed buckets (e.g., one bucket per minute). Count requests in the current bucket. Reset count when the bucket rolls over.

#### 🎨 Visual — Fixed Window Boundary Burst

```
Limit: 3 req / 60s. Window starts at t=0, t=60, t=120 ...

  t=58s: [req1, req2, req3] ← window 1, count=3, full
  t=59s: DENIED

  t=60s: NEW WINDOW → count resets to 0
  t=60s: [req1, req2, req3] ← window 2, all allowed

PROBLEM: 6 requests between t=58s and t=62s — 2× the limit.
Each window sees at most 3, so both pass. The boundary is invisible.

         Window 1            Window 2
  ───────────────────┼──────────────────────
  count:    3         │count:    3
  0s                 60s                120s

KEY INVARIANT:
  If now - windowStart >= windowSize: reset count=0, windowStart=now.
  If count < maxRequests: count++, allow. Else: deny.
```

**Steps in plain English:**

1. **Init** — create entry `[0, now]` if absent.
2. **Roll window** — if `now - windowStart >= windowMs`, reset `count = 0`, update `windowStart = now`.
3. **Check** — if `count < maxRequests`, increment and allow. Else deny.

```java
class FixedWindowRateLimiter {
    // entry = { requestCount, windowStartMs }
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMs;

    public FixedWindowRateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    public boolean allowRequest(String userId) {
        long now = System.currentTimeMillis();
        // Step 1 — create entry if absent
        windows.putIfAbsent(userId, new long[]{ 0, now });
        long[] entry = windows.get(userId);
        // Step 2 — lock per-user entry (not the whole map — too coarse)
        synchronized (entry) {
            // Step 2 — roll window if expired
            if (now - entry[1] >= windowMs) {
                entry[0] = 0;
                entry[1] = now;
            }
            // Step 3 — check count and increment
            if (entry[0] < maxRequests) {
                entry[0]++;
                return true;
            }
            return false;
        }
    }
}
```

**Time:** O(1) | **Space:** O(users)

---

### Algorithm 2 — Sliding Window Log

**DS:** `Map<String, Deque<Long>>` — each user's deque stores exact request timestamps.

Store every request timestamp. On each call, evict timestamps older than `windowMs` from the front of the deque. Count remaining; if `< maxRequests`, allow.

#### 🎨 Visual — Sliding Window Log Eviction

```
Limit: 3 req / 60,000ms. User "alice". Each "|" = 10,000ms.

t=10,000  → log: [10000]               size=1 → ALLOW
t=20,000  → log: [10000, 20000]        size=2 → ALLOW
t=50,000  → log: [10000, 20000, 50000] size=3 → ALLOW
t=60,000  → evict? 60000-10000=50000 < 60000 → no eviction
              log: [10000, 20000, 50000], size=3 → DENIED

t=70,001  → evict: 70001-10000=60001 >= 60000 → remove 10000
              log: [20000, 50000], size=2
              → add 70001 → size=3 → ALLOW

No boundary burst: the window truly slides with wall clock time.
Any 60,000ms interval contains at most 3 requests.

KEY INVARIANT:
  Deque front = oldest timestamp still in scope.
  Evict from front while (now - front) >= windowMs.
  Deque size = exact request count in the current window.
```

**Steps in plain English:**

1. **Init** — create empty `ArrayDeque` if absent.
2. **Evict** — poll from front while `now - front >= windowMs`.
3. **Check** — if `deque.size() < maxRequests`, add `now` to back and allow. Else deny.

```java
class SlidingWindowLogRateLimiter {
    private final Map<String, Deque<Long>> logs = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMs;

    public SlidingWindowLogRateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    public boolean allowRequest(String userId) {
        long now = System.currentTimeMillis();
        // Step 1 — create deque if absent
        logs.putIfAbsent(userId, new ArrayDeque<>());
        Deque<Long> log = logs.get(userId);
        synchronized (log) {
            // Step 2 — evict timestamps outside the window
            while (!log.isEmpty() && now - log.peekFirst() >= windowMs) {
                log.pollFirst();
            }
            // Step 3 — admit if under limit
            if (log.size() < maxRequests) {
                log.addLast(now);
                return true;
            }
            return false;
        }
    }
}
```

**Time:** O(maxRequests) amortized per request (eviction cost) | **Space:** O(users × maxRequests)

---

### Algorithm 3 — Token Bucket ⭐ (Real-World Default)

**DS:** Inner `Bucket` class — `{double tokens, long lastRefillMs}` per user.

Each user has a bucket holding up to `capacity` tokens. Tokens refill continuously at `refillRate` per second — but computed **lazily** on each request based on elapsed time. One request consumes one token.

#### 🎨 Visual — Token Bucket Lazy Refill

```
Capacity = 5, refillRate = 1 token/sec. User "alice".

t=0s:   bucket=[5.0] ← starts full
        5 rapid requests → [4.0] [3.0] [2.0] [1.0] [0.0] — all ALLOWED
        (burst absorbed by accumulated tokens)

t=0.5s: request arrives.
        elapsed=500ms → earned=0.5 tokens → bucket=[0.5]
        0.5 < 1 → DENIED (partial token, not enough)

t=1.0s: request arrives.
        elapsed=500ms → earned=0.5 tokens → bucket=[1.0]
        1.0 >= 1 → consume → bucket=[0.0] → ALLOWED

t=6.0s: 5 seconds of silence.
        elapsed=5000ms → earned=5.0 → bucket=[5.0] (capped at capacity)
        User has full burst capacity again.

KEY INVARIANT:
  On every call (even DENIED): tokens = min(capacity, tokens + elapsed × rate).
  If tokens >= 1.0: consume 1.0 token, allow. Else: deny.
  lastRefillMs always updates — this is the lazy refill mechanism.
```

**Steps in plain English:**

1. **Init** — create `Bucket` with `tokens = capacity`, `lastRefillMs = now`.
2. **Refill** — compute `elapsed = now - lastRefill`, add `elapsed × ratePerMs` to tokens, cap at capacity, update `lastRefillMs`.
3. **Consume** — if `tokens >= 1.0`, decrement by 1.0 and allow. Else deny.

```java
class TokenBucketRateLimiter {
    private static class Bucket {
        double tokens;
        long lastRefillMs;
        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillMs = now;
        }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final double ratePerMs;  // tokens per millisecond

    // capacity = max burst size; refillRatePerSecond = steady-state throughput
    public TokenBucketRateLimiter(int capacity, int refillRatePerSecond) {
        this.capacity = capacity;
        this.ratePerMs = (double) refillRatePerSecond / 1000.0;
    }

    public boolean allowRequest(String userId) {
        long now = System.currentTimeMillis();
        // Step 1 — init bucket at full capacity for new users
        Bucket b = buckets.computeIfAbsent(userId,
            k -> new Bucket(capacity, now));
        synchronized (b) {
            // Step 2 — lazy refill based on elapsed time
            long elapsed = now - b.lastRefillMs;
            b.tokens = Math.min(capacity, b.tokens + elapsed * ratePerMs);
            b.lastRefillMs = now;
            // Step 3 — consume one token if available
            if (b.tokens >= 1.0) {
                b.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
```

**Time:** O(1) | **Space:** O(users)

---

### Algorithm 4 — Sliding Window Counter (Approximation)

**DS:** `Map<String, long[]>` where `entry = [prevCount, currCount, currWindowStartMs]`

Keep two adjacent counters: previous window count and current window count. Estimate the sliding window count using how far through the current window we are. This gives O(1) space at the cost of ~5% approximation error.

**Steps in plain English:**

1. **Init** — create entry `[0, 0, now]` if absent.
2. **Roll window** — if `now >= windowStart + windowMs`, advance: `prevCount = currCount`, reset `currCount = 0`, update `windowStart`.
3. **Estimate** — `elapsed = now - windowStart`. `estimated = prevCount × (1 - elapsed/windowMs) + currCount`.
4. **Check** — if `estimated < maxRequests`, increment `currCount` and allow. Else deny.

```java
class SlidingWindowCounterRateLimiter {
    // entry = { prevWindowCount, currWindowCount, currWindowStartMs }
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMs;

    public SlidingWindowCounterRateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    public boolean allowRequest(String userId) {
        long now = System.currentTimeMillis();
        // Step 1 — init entry if absent
        counters.putIfAbsent(userId, new long[]{ 0, 0, now });
        long[] entry = counters.get(userId);
        synchronized (entry) {
            long prevCount = entry[0];
            long currCount = entry[1];
            long windowStart = entry[2];
            // Step 2 — roll window if current window has expired
            if (now >= windowStart + windowMs) {
                long skipped = (now - windowStart) / windowMs;
                // If exactly one window passed, prev = last curr; otherwise prev = 0 (too stale)
                entry[0] = (skipped == 1) ? currCount : 0;
                entry[1] = 0;
                entry[2] = windowStart + skipped * windowMs;
                prevCount = entry[0];
                currCount = 0;
                windowStart = entry[2];
            }
            // Step 3 — weighted estimate of requests in the last windowMs
            double elapsed = now - windowStart;
            double prevWeight = 1.0 - (elapsed / windowMs);
            long estimated = (long)(prevCount * prevWeight) + currCount;
            // Step 4 — admit if under limit
            if (estimated < maxRequests) {
                entry[1]++;
                return true;
            }
            return false;
        }
    }
}
```

**Time:** O(1) | **Space:** O(users) — O(1) per user vs O(maxRequests) for Sliding Log

**Accuracy:** ~95% (< 5% error at window boundaries). Cloudflare uses this algorithm in production for its API rate limiting layer.

---

### Variants

| Variant | Adaptation |
| --- | --- |
| Rate limit by IP instead of userId | Change map key to client IP — identical logic |
| Rate limit by user + endpoint | Key = `userId + ":" + endpoint` — map handles the rest |
| Distributed rate limiter (multi-node) | Replace in-memory Map with Redis; `INCR` + `EXPIRE` for Fixed Window; Lua script for atomicity |
| Leaky bucket (smoothed output) | Queue incoming requests; drain at constant rate — smooths traffic, no burst; different from Token Bucket |
| Allow a one-time burst above limit | Token Bucket with `capacity > 1-second refill amount` — idle users accumulate extra tokens |

> 🧩 **Try these:**
> - ✅ **Implement Fixed Window + Sliding Log from memory** — no code lookup, 20 min each; test with the boundary burst example above
> - ✅ **Token Bucket from memory** — remember: refill on every call (including DENIED ones), cap at capacity
> - 🟡 **Draw the boundary burst on paper** — explain in one sentence why Fixed Window allows 2× requests, then implement the Sliding Log fix
> - 🔴 **Distributed Redis rate limiter** — needs Redis + Lua knowledge; reference only during DSA prep

---

## 🔗 Cross-References (Consistent Hashing, Task Scheduler)

These two are also hybrid problems but **live in SystemDesignConcepts** (they have more system design depth than DSA depth). Rate Limiter is now fully covered in **Problem 5** above.

| Problem | DS Core | System Add-on | Lives in |
| --- | --- | --- | --- |
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

**Gotcha 7 — Rate Limiter: synchronizing on the whole method vs. per-user.**

Using `synchronized` on the method makes every user's request block on every other user — a global bottleneck. Synchronize on the per-user entry instead.

```java
// ❌ wrong — global lock; all users block each other
public synchronized boolean allowRequest(String userId) { ... }

// ✅ right — lock per-user entry only
public boolean allowRequest(String userId) {
    long[] entry = windows.computeIfAbsent(userId, k -> new long[]{ 0, now });
    synchronized (entry) {
        // check and update entry
    }
}
```

---

**Gotcha 8 — Token Bucket: forgetting to update lastRefillMs on DENIED requests.**

If you skip the refill step when denying, the next allowed request will retroactively earn tokens for all the "quiet" time during the throttle period — causing a sudden burst to be allowed.

```java
// ❌ wrong — only refills on allowed requests
if (b.tokens >= 1.0) {
    b.tokens -= 1.0;
    return true;
}
// skipped: b.lastRefillMs = now on denied path

// ✅ right — always update lastRefillMs, even when denying
b.tokens = Math.min(capacity, b.tokens + elapsed * ratePerMs);
b.lastRefillMs = now;   // ← runs before the if-check, always
if (b.tokens >= 1.0) {
    b.tokens -= 1.0;
    return true;
}
return false;
```

---

**Gotcha 9 — Thread-safety: get() on LRU is a write operation.**

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
10. ✅ **Rate Limiter** — implement all 4 algorithms (Fixed Window, Sliding Log, Token Bucket, Sliding Counter); explain the tradeoff table without notes

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
- **Rate Limiter:** 4 algorithms — Fixed Window (simplest, boundary burst bug), Sliding Log (exact, O(maxReq) memory), Token Bucket ⭐ (real-world default, burst-friendly), Sliding Counter (O(1) approx) | thread safety = per-user `synchronized (entry)`, not global lock
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
| July 2026 | **Added Problem 5 — Rate Limiter.** Covers all 4 algorithms (Fixed Window, Sliding Log, Token Bucket, Sliding Counter) with full Java code, visuals, and tradeoff table. Moved Rate Limiter from cross-ref (planned external file) to first-class problem in this doc. Added Gotchas 7 and 8 for rate limiter-specific bugs. |
