# Hybrid Design Problems — Quick Reference

> Scan this before any interview. Deep study → `DSA/DeepDive/hybrid-design-problems.md`.

---

## ⚡ The One Pattern Behind All of These

```
HashMap<Key, Node>       +       Second DS
─────────────────────────────────────────────
O(1) lookup by key           O(1)/O(log N) ordering
```

**Pick the second DS by asking:** *What do I need to maintain beyond lookup?*

| Need | Second DS | Java type |
| --- | --- | --- |
| Evict least recently used | Doubly Linked List | custom `Node` class |
| Evict least frequently used | Frequency buckets | `HashMap<freq, LinkedHashSet<key>>` |
| Count in last N seconds | Sliding window | `ArrayDeque<Integer>` or circular `int[N]` |
| Top-K / rank by value | Sorted map | `TreeMap<score, count>` |
| Next scheduled task | Min-heap by time | `PriorityQueue<Task>` |
| Nearest node clockwise | Sorted ring | `TreeMap<Long, Node>` |

---

## 🔹 LRU Cache — LC 146

| | |
| --- | --- |
| **DS combo** | `HashMap<Key, Node>` + `DoublyLinkedList` |
| **get** | O(1) — lookup + moveToFront |
| **put** | O(1) — insert at head + removeLast if full |
| **Space** | O(capacity) |
| **Java shortcut** | `LinkedHashMap(cap, 0.75f, true)` + `removeEldestEntry` |

**Critical rule:** Node must store `key` (needed for `map.remove()` on eviction).

**Variants and how they're phrased:**

| Phrasing | Variant |
| --- | --- |
| "Expires after N seconds" | LRU + TTL → add `PriorityQueue<Node>` min-heap by expiryTime |
| "Thread-safe for concurrent requests" | `synchronized` on get/put — NOT ReadWriteLock (get mutates) |
| "O(1) get and put" | The headline constraint that forces DLL (not just HashMap) |
| "Browser history / DB buffer pool" | LRU by another name |
| "LRU-K" | Track K most recent access times per key; 🔴 Reference Only |

---

## 🔹 LFU Cache — LC 460

| | |
| --- | --- |
| **DS combo** | `keyToVal` + `keyToFreq` + `freqToKeys` (freq → `LinkedHashSet`) + `minFreq` pointer |
| **get** | O(1) — bump freq, move key between buckets |
| **put** | O(1) — evict from `freqToKeys[minFreq]`, insert with freq=1, reset minFreq=1 |
| **Space** | O(capacity) |

**Critical rule:** reset `minFreq = 1` on every new key insert (not on updates).

**Variants and how they're phrased:**

| Phrasing | Variant |
| --- | --- |
| "Evict least popular entry" | LFU by another name |
| "Tie-break by LRU" | Already handled — `LinkedHashSet` preserves insertion order |
| "TTL + LFU" | Add expiryTime to each node; check on get() |

---

## 🔹 Hit Counter — LC 362

| | |
| --- | --- |
| **DS combo A** | `int[300] times` + `int[300] hits` (circular array) |
| **DS combo B** | `ArrayDeque<Integer>` of timestamps |
| **hit** | O(1) both options |
| **getHits** | O(1) Option A (scan 300 slots) · O(N) Option B |
| **Space** | O(1) Option A · O(N) Option B |

**Use A when:** window is fixed 300s and memory matters.
**Use B when:** window size varies or interviewer asks for "sliding window" explicitly.

**Variants and how they're phrased:**

| Phrasing | Variant |
| --- | --- |
| "Count requests per user in last N seconds" | Rate limiter = Hit Counter keyed by userId |
| "Multi-granularity (sec / min / hour)" | Three circular arrays, one per granularity |
| "Thread-safe hit counter" | `synchronized` or `AtomicIntegerArray` for hits[] |
| "Hits in [ts-299, ts]" | `timestamp - times[i] < 300` (< not <=) |

---

## 🔹 Leaderboard — LC 1244

| | |
| --- | --- |
| **DS combo** | `HashMap<playerId, score>` + `TreeMap<score, count>` |
| **addScore** | O(log N) — remove old score from TreeMap, add new score |
| **top(K)** | O(K log N) — iterate TreeMap descending, accumulate |
| **reset** | O(log N) — remove player from both maps |
| **Space** | O(N) |

**Critical rule:** Guard against removing score=0 from TreeMap (it was never added).

**Variants and how they're phrased:**

| Phrasing | Variant |
| --- | --- |
| "Return top K player IDs" | `TreeMap<score, List<playerId>>` |
| "Find rank of a player" | `scoreCount.tailMap(score + 1)` sums players above |
| "Concurrent leaderboard" | `ConcurrentSkipListMap` instead of TreeMap |
| "Score replaces instead of adds" | Simpler put; same DS combo |

---

## 🔹 Rate Limiter (cross-reference)

→ Full note: `SystemDesignConcepts/02-rate-limiting.md` (planned)

| | |
| --- | --- |
| **Token Bucket** | `AtomicLong tokens` + scheduled refill thread |
| **Sliding Window Log** | `Deque<Long>` of timestamps per key; drain expired front entries |
| **Sliding Window Counter** | Two counters (current + previous window) + interpolation |
| **allow(userId)** | O(1) token bucket · O(N) sliding window log |

---

## 🔹 Consistent Hashing (cross-reference)

→ Full note: `SystemDesignConcepts/05-consistent-hashing.md` (planned)

| | |
| --- | --- |
| **DS combo** | `TreeMap<Long, Node>` as circular hash ring |
| **lookup(key)** | `ring.ceilingKey(hash(key))` → O(log N) |
| **add node** | Insert virtual nodes into TreeMap → O(V log N) |
| **remove node** | Remove virtual nodes from TreeMap → O(V log N) |

---

## 🔹 Task Scheduler / Delayed Job Queue

| | |
| --- | --- |
| **DS combo** | `PriorityQueue<Task>` (min-heap by nextRunTime) + optional `HashMap<id, Task>` |
| **schedule(task)** | O(log N) — heap insert |
| **runNext()** | O(log N) — heap poll; reinsert if recurring (nextRunTime += interval) |
| **cancel(id)** | Lazy deletion — mark as cancelled; skip on poll |

---

## ⚠️ Top Gotchas (scan before interview)

| # | Gotcha | Fix |
| --- | --- | --- |
| 1 | LRU Node missing `key` field | Always store key in Node — needed for map.remove() on eviction |
| 2 | LRU: put() on existing key skips moveToFront | Always moveToFront on both get AND put |
| 3 | LFU: minFreq not reset to 1 on new insert | Reset minFreq=1 on every new key put, never on updates |
| 4 | LFU: minFreq incremented blindly | Only increment minFreq if old bucket is now empty AND minFreq == oldFreq |
| 5 | Thread-safe LRU: using readLock for get() | get() calls moveToFront (DLL mutation) — it needs writeLock or synchronized |
| 6 | Hit Counter: off-by-one on 300-second window | Use `< 300` not `<= 300` for LC 362 (inclusive of current second) |
| 7 | Leaderboard: reset() removes score=0 from TreeMap | Guard: `if (currentScore > 0)` before TreeMap removal |

---

## 🗺️ Problem Priority Order

| Priority | Problem | LC | Why |
| --- | --- | --- | --- |
| ⭐ 1st | LRU Cache | 146 | Most asked; WEX missed it |
| ⭐ 2nd | Hit Counter | 362 | Simple entry point; sliding window pattern |
| ⭐ 3rd | Leaderboard | 1244 | TreeMap + HashMap combo |
| 4th | LFU Cache | 460 | Hardest; needs LRU as foundation |
| 5th | LRU + TTL | (LC 146 variant) | WEX exact ask |
| 6th | Rate Limiter | (SystemDesignConcepts) | After #1-4 solid |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | Created as companion to `DSA/DeepDive/hybrid-design-problems.md`. |
