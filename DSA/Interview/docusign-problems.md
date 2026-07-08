# Docusign — SDE 3 / Senior Backend Interview Problem Set

> **Role:** Senior Software Engineer / SDE 3 — Backend
> **Round:** DSA Technical Round (60–90 min, 1–2 problems, occasionally design-flavored)
> **Format:** Live coding. Expect discussion of trade-offs, time/space complexity, and follow-ups.
> **Source:** Friend's actual interview (Multi-source BFS + TTL KV Store confirmed Jul 2026) + company tags (LC Discuss / Glassdoor / Blind 2024–2026)

> ⭐ **Tier 1** = Friend's confirmed OR highest-frequency tag  
> 🔹 **Tier 2** = Strong company tag match  
> 🧩 **Tier 3** = Pattern-derived from confirmed problem families

---

## Table of Contents

1. [In-Memory KV Store with TTL](#1-in-memory-kv-store-with-ttl--design-problem) ⭐ Confirmed friend Q2
2. [Multi-source BFS on Graph](#2-multi-source-bfs-on-graph--polio-delivery-problem) ⭐ Confirmed friend Q1
3. [Sort Colors](#3-sort-colors--lc-75) ⭐
4. [Min Cost Valid Path in Grid](#4-min-cost-valid-path-in-grid--lc-1368) ⭐
5. [House Robber II](#5-house-robber-ii--lc-213) ⭐
6. [House Robber III](#6-house-robber-iii--lc-337) ⭐
7. [Moving Average from Data Stream](#7-moving-average-from-data-stream--lc-346) ⭐
8. [Nested Boxes / Maximum Depth of N-ary Tree](#8-nested-boxes--maximum-depth-of-n-ary-tree) ⭐
9. [Serialize and Deserialize Binary Tree](#9-serialize-and-deserialize-binary-tree--lc-297) ⭐
10. [Insert Delete GetRandom O(1)](#10-insert-delete-getrandom-o1--lc-380) ⭐
11. [Time-Based Key-Value Store](#11-time-based-key-value-store--lc-981) 🔹
12. [LRU Cache](#12-lru-cache--lc-146) 🔹
13. [Combination Sum](#13-combination-sum--lc-39) 🔹
14. [Circular Dependency Detection](#14-circular-dependency-detection--lc-207-variant) 🔹
15. [Build a File System](#15-build-a-file-system--lc-588) 🔹
16. [Longest Substring Without Repeating Characters](#16-longest-substring-without-repeating-characters--lc-3) 🔹
17. [Meeting Rooms II](#17-meeting-rooms-ii--lc-253) 🔹
18. [Clone Graph](#18-clone-graph--lc-133) 🧩
19. [Rotting Oranges](#19-rotting-oranges--lc-994) 🧩
20. [Group Anagrams](#20-group-anagrams--lc-49) 🧩
21. [Find Median from Data Stream](#21-find-median-from-data-stream--lc-295) 🔹
22. [Implement Trie (Prefix Tree)](#22-implement-trie-prefix-tree--lc-208) 🔹
23. [LFU Cache](#23-lfu-cache--lc-460) 🧩

---

---

## 1. In-Memory KV Store with TTL — Design Problem

**Difficulty:** Hard | **Pattern:** Design, HashMap + TreeMap  
**Confirmed in:** Friend's actual Docusign interview (Jul 2026)

> 📖 Full deep dive with drills and gotchas: **`DSA/DeepDive/hybrid-design-problems.md`**

---

### 🎯 Problem Statement

Design an in-memory key-value store that supports the following operations:

- `set(key, value, ttl)` — Store `key → value` with a time-to-live of `ttl` milliseconds. After `ttl` ms, the key is automatically expired and treated as if it never existed.
- `get(key)` — Return value if key exists and is not expired. Return `null` otherwise.
- `delete(key)` — Remove key immediately.
- `countActive()` — Return count of non-expired keys at the current time.

**Constraints:**
- Time must be obtained via `System.currentTimeMillis()` (wall clock).
- Lazy expiry is acceptable (clean up expired entries on access, not on a background thread).
- `countActive()` must be O(log n) or better.

```
Example:
store.set("docSign", "contract", 5000);   // expires in 5 seconds
store.get("docSign");  // returns "contract" (within 5s)
Thread.sleep(6000);
store.get("docSign");  // returns null (expired)
store.countActive();   // returns 0
```

---

### 🧠 Discussion — How to Think About This

This is a **design problem disguised as a data structure problem**. The interviewer wants to see:

1. Can you model expiry time cleanly? → Use an **Entry inner class**, not `Object[]`.
2. Do you know when to use `HashMap` vs `TreeMap`? → Two different query types need two different structures.
3. Do you think about thread safety (even if not asked to implement it)?

**Key insight — why two structures?**
- `get("key")` needs O(1) lookup by key → HashMap
- `countActive()` needs O(log n) range query by time → TreeMap

Neither structure alone serves both. One HashMap alone forces O(n) scan in `countActive`. One TreeMap alone (keyed by expiry) cannot do O(1) key lookup.

**Sync contract:** Every write (`set`, `delete`) must update BOTH structures — miss either one and `countActive()` silently returns wrong numbers.

---

### 🐌 Brute Force Approach

Single `HashMap<String, Entry>` — key maps to an Entry holding value + expiryTime. For `countActive()`, scan all entries — O(n).

**Why Entry class, not two HashMaps or `Object[]`?**
Two HashMaps can go out of sync on delete. `Object[]` has no type safety — a transposed index is a runtime bug, not a compile error. An Entry class costs 5 lines and removes both risks.

```java
// Brute Force — O(n) countActive, O(1) set/get
class KVStoreBrute {

    // Inner class: value and expiry travel together, type-safe
    private static class Entry {
        final String value;
        final long expiryTime;

        Entry(String value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    // Single map — one lookup gives both value and expiry
    private final Map<String, Entry> store = new HashMap<>();

    public void set(String key, String value, long ttlMs) {
        long expiry = System.currentTimeMillis() + ttlMs;
        store.put(key, new Entry(value, expiry));
    }

    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiryTime) {
            // Lazy expiry — remove on access
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    public void delete(String key) {
        store.remove(key);
    }

    // O(n) — must scan every entry to check expiry
    public int countActive() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Entry entry : store.values()) {
            if (entry.expiryTime > now) {
                count++;
            }
        }
        return count;
    }
}
```

**Why is this slow?** `countActive()` scans ALL keys every call — O(n). In a large document-signing platform with millions of sessions, this is unacceptable.

---

### 💡 Idea Behind Optimisation

`countActive()` needs all entries where `expiryTime > now` — this is a **range query on a sorted attribute**. A `TreeMap<Long, Set<String>>` (expiry time → set of keys) lets us call `tailMap(now + 1)` to get only future-expiry buckets in O(log n).

The HashMap stays for O(1) key lookup. The TreeMap is a **secondary index** solely for the range query. Both structures are kept in sync on every write.

### 🎨 Visual — Dual-Structure Layout

```
STRUCTURE 1: HashMap<String, Entry>      STRUCTURE 2: TreeMap<Long, Set<String>>
(primary — answers: value/expiry         (expiry index — answers: how many
 for this key?)                           keys still alive?)

"docA" → Entry{"PDF", 6000ms}           4000ms → {"docB"}
"docB" → Entry{"SIG", 4000ms}           6000ms → {"docA", "docC"}
"docC" → Entry{"ZIP", 6000ms}

now = 5000ms
countActive():
  tailMap(5001ms) → { 6000ms → {"docA","docC"} }
  sum = 2  ← no scan of HashMap needed

KEY INVARIANT:
  HashMap answers key-based queries in O(1).
  TreeMap answers time-range queries in O(log n).
  Every key lives in EXACTLY ONE expiry bucket in the TreeMap at all times.
```

---

### 🚀 Optimal Java Solution

```java
class KVStoreOptimal {

    // ── Inner Entry class — type-safe, no Object[] casting ────────────────
    private static class Entry {
        final String value;
        final long expiryTime;

        Entry(String value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    // ── Structure 1: primary store — O(1) key lookup ──────────────────────
    private final Map<String, Entry> store = new HashMap<>();

    // ── Structure 2: expiry index — O(log n) range query ─────────────────
    private final TreeMap<Long, Set<String>> expiryIndex = new TreeMap<>();

    // ── Helper: shared by set() and delete() ─────────────────────────────
    // Removes key from its expiry bucket; removes the bucket if now empty
    private void removeFromIndex(String key, long expiryTime) {
        Set<String> bucket = expiryIndex.get(expiryTime);
        if (bucket == null) {
            return;
        }
        bucket.remove(key);
        if (bucket.isEmpty()) {
            expiryIndex.remove(expiryTime);
        }
    }

    // ── set ───────────────────────────────────────────────────────────────
    public void set(String key, String value, long ttlMs) {
        long newExpiry = System.currentTimeMillis() + ttlMs;

        // If key already exists, remove it from its OLD expiry bucket first
        // (skip this and you get a ghost entry that inflates countActive)
        Entry existing = store.get(key);
        if (existing != null) {
            removeFromIndex(key, existing.expiryTime);
        }

        // Insert into primary store
        store.put(key, new Entry(value, newExpiry));

        // Insert into expiry index
        expiryIndex.computeIfAbsent(newExpiry, k -> new HashSet<>()).add(key);
    }

    // ── get ───────────────────────────────────────────────────────────────
    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiryTime) {
            // Lazy delete — cleans BOTH structures
            delete(key);
            return null;
        }
        return entry.value;
    }

    // ── delete ────────────────────────────────────────────────────────────
    public void delete(String key) {
        // store.remove returns the entry or null if key was absent
        Entry entry = store.remove(key);
        if (entry != null) {
            removeFromIndex(key, entry.expiryTime);
        }
    }

    // ── countActive — O(log n + k) ────────────────────────────────────────
    public int countActive() {
        long now = System.currentTimeMillis();
        // tailMap(now + 1): all expiry timestamps STRICTLY greater than now
        Map<Long, Set<String>> liveBuckets = expiryIndex.tailMap(now + 1);
        int count = 0;
        for (Set<String> bucket : liveBuckets.values()) {
            count += bucket.size();
        }
        return count;
    }
}
```

---

### ⏱️ Complexity

| Operation | Brute Force | Optimal |
|---|---|---|
| `set` | O(1) | O(log n) |
| `get` | O(1) | O(log n) lazily |
| `delete` | O(1) | O(log n) |
| `countActive` | O(n) | O(log n + k) |
| Space | O(n) | O(n) |

k = number of distinct expiry timestamps still in the future (much smaller than n in practice)

---

### 🔁 Follow-up Questions

**Q1: How would you add thread safety?**  
Use `ConcurrentHashMap` for the primary store and a `ReentrantReadWriteLock` around the `TreeMap` (since `TreeMap` is not thread-safe). Multiple readers don't block each other with a read-write lock.

**Q2: What if TTL must be refreshed on every get (sliding window TTL)?**  
On `get`, if the key is alive, call `set(key, value, originalTtl)` again. This re-inserts with a fresh expiry. Store the original TTL in the Entry class as a third field.

**Q3: How would you implement a background eviction thread?**  
A `ScheduledExecutorService` running every N ms: iterate `expiryIndex.headMap(now)` — all entries with expiry ≤ now — and bulk-delete them. This is eager eviction vs the current lazy approach.

**Q4: How does this map to Docusign's domain?**  
Document signing sessions have a TTL. A signature link expires after N hours. `key = signingURL`, `value = documentId`, `ttl = 24 hours`.

**Q5: Why `tailMap(now + 1)` and not `tailMap(now)`?**  
An entry with `expiryTime == now` satisfies `now >= expiryTime` → it IS expired. `tailMap(now)` would include it. `tailMap(now + 1)` excludes it, staying consistent with the `get` expiry check.

---

---

## 2. Multi-source BFS on Graph — Polio Delivery Problem

**Difficulty:** Hard | **Pattern:** Multi-source BFS, Graph  
**Confirmed in:** Friend's actual Docusign interview (Jul 2026)

---

### 🎯 Problem Statement

You are given a network of cities connected by roads. Some cities are **vaccine distribution centers** (sources). Find the **minimum time for vaccine to reach every city** in the network, where each road takes 1 unit of time to traverse.

Return an array where `result[i]` = minimum time for vaccine to reach city `i`. If a city is unreachable, return `-1` for it.

```
Example:
Cities: 0, 1, 2, 3, 4
Edges: 0-1, 1-2, 2-3, 3-4
Distribution centers (sources): [0, 4]

result = [0, 1, 2, 1, 0]
  city 0 → already a center (time=0)
  city 1 → 1 hop from center 0
  city 2 → 2 hops from center 0 OR 2 hops from center 4 → min = 2
  city 3 → 1 hop from center 4
  city 4 → already a center (time=0)
```

---

### 🧠 Discussion — How to Think About This

**Naive thought:** Run BFS from each source separately, then take minimum for each city. This is O(S × (V + E)) where S = number of sources — wasteful.

**Key insight:** In standard BFS, you start from ONE source. In **multi-source BFS**, you seed the queue with ALL sources simultaneously at time=0. The BFS frontier expands from all sources in parallel — automatically computing the minimum distance to any source for each city.

This works because BFS guarantees shortest path on unweighted graphs. When a city is first visited (from any source), that's the shortest time.

**Docusign mapping:** Cities = servers/clients, Centers = document servers, Edge = network connection. "How fast can signed documents be delivered to all parties?"

---

### 🐌 Brute Force Approach

Run BFS separately from each source. Merge results by taking min.

```java
import java.util.*;

// Brute Force — O(S × (V + E)) time
public int[] bruteForce(int n, int[][] edges, int[] sources) {
    // Build adjacency list
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] edge : edges) {
        adj.get(edge[0]).add(edge[1]);
        adj.get(edge[1]).add(edge[0]);
    }

    int[] result = new int[n];
    Arrays.fill(result, Integer.MAX_VALUE);

    // BFS from each source separately — O(S × (V+E))
    for (int src : sources) {
        int[] dist = bfs(src, n, adj);
        for (int i = 0; i < n; i++) {
            result[i] = Math.min(result[i], dist[i]);
        }
    }

    // Convert MAX_VALUE to -1 for unreachable
    for (int i = 0; i < n; i++) {
        if (result[i] == Integer.MAX_VALUE) {
            result[i] = -1;
        }
    }
    return result;
}

private int[] bfs(int src, int n, List<List<Integer>> adj) {
    int[] dist = new int[n];
    Arrays.fill(dist, -1);
    Queue<Integer> q = new LinkedList<>();
    q.offer(src);
    dist[src] = 0;
    while (!q.isEmpty()) {
        int node = q.poll();
        for (int neighbor : adj.get(node)) {
            if (dist[neighbor] == -1) {
                dist[neighbor] = dist[node] + 1;
                q.offer(neighbor);
            }
        }
    }
    return dist;
}
```

**Why is this slow?** S separate BFS passes, each O(V+E). With 100 sources in a million-node graph, this is 100× too slow.

---

### 💡 Idea Behind Optimisation

Pre-load ALL sources into the BFS queue at level 0. Mark them all as visited with `dist=0`. Now run a single BFS. Since all sources are equidistant "ancestors", the BFS frontier naturally expands outward from all of them simultaneously — the first time any node is reached, it's the minimum distance.

### 🎨 Visual — Multi-source BFS Expansion

```
Graph: 0 — 1 — 2 — 3 — 4
Sources: {0, 4}

TIME 0: Queue = [0, 4]    (seed all sources)
  visited: {0→0, 4→0}

        ★0   1   2   3  ★4
dist:   [0]  .   .   .  [0]

TIME 1: Process 0 → enqueue 1
        Process 4 → enqueue 3
  Queue = [1, 3]

        ★0   1   2   3  ★4
dist:   [0] [1]  .  [1] [0]

TIME 2: Process 1 → enqueue 2 (not yet visited)
        Process 3 → try enqueue 2 (already visited, SKIP)
  Queue = [2]

        ★0   1   2   3  ★4
dist:   [0] [1] [2] [1] [0]   ← DONE

KEY INVARIANT:
  Seeding all sources at time=0 means the BFS wavefront
  races from ALL sources simultaneously.
  First visit to any node = shortest distance from nearest source.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public int[] multiSourceBFS(int n, int[][] edges, int[] sources) {
    // Step 1 — build adjacency list
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] edge : edges) {
        adj.get(edge[0]).add(edge[1]);
        adj.get(edge[1]).add(edge[0]);
    }

    // Step 2 — initialize distances, seed ALL sources at time 0
    int[] dist = new int[n];
    Arrays.fill(dist, -1);

    Queue<Integer> queue = new LinkedList<>();
    for (int src : sources) {
        dist[src] = 0;
        queue.offer(src);
    }

    // Step 3 — single BFS from all sources simultaneously
    while (!queue.isEmpty()) {
        int node = queue.poll();
        for (int neighbor : adj.get(node)) {
            // First time visited = shortest distance
            if (dist[neighbor] == -1) {
                dist[neighbor] = dist[node] + 1;
                queue.offer(neighbor);
            }
        }
    }

    // dist[i] == -1 means unreachable
    return dist;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force (S × BFS) | O(S × (V + E)) | O(V + E) |
| Multi-source BFS | O(V + E) | O(V + E) |

---

### 🔁 Follow-up Questions

**Q1: What if roads have different weights (not just 1 unit)?**  
Use **Dijkstra** with a priority queue instead of BFS. Seed all sources with distance 0 into the min-heap. Same multi-source principle, different data structure.

**Q2: What if the graph is directed?**  
Same algorithm — just build a directed adjacency list (don't add reverse edge). Some cities may remain unreachable.

**Q3: How is this different from LC #994 Rotting Oranges?**  
Rotting Oranges is the exact same algorithm on a grid (2D array) instead of an explicit graph. The neighbors are the 4 cardinal directions instead of an adjacency list.

**Q4: Can you detect if any city is unreachable?**  
After BFS, any node where `dist[i] == -1` is unreachable. Return those indices or a count.

**Q5: What if the number of sources equals the number of nodes?**  
All nodes are already at distance 0. BFS terminates after one pass — effectively O(V + E) with no meaningful work.

---

---

## 3. Sort Colors — LC #75

**Difficulty:** Medium | **Pattern:** Dutch National Flag, Three-pointer  
**Confirmed in:** High-frequency Docusign tag

---

### 🎯 Problem Statement

Given an array `nums` with values `0`, `1`, and `2` representing red, white, and blue, sort the array **in-place** so all 0s come first, then 1s, then 2s.

**Constraint:** You must do this in ONE pass using O(1) extra space.

```
Example:
Input:  [2, 0, 2, 1, 1, 0]
Output: [0, 0, 1, 1, 2, 2]
```

---

### 🧠 Discussion — How to Think About This

Three values, sort in-place, one pass. This screams **Dutch National Flag** (Dijkstra's algorithm).

Maintain three pointers:
- `low` — everything before `low` is 0 (confirmed sorted)
- `mid` — current element being examined
- `high` — everything after `high` is 2 (confirmed sorted)

The region `[low, high]` is the unsorted middle. We shrink it by examining `nums[mid]`.

---

### 🐌 Brute Force Approach

Count 0s, 1s, 2s. Overwrite array.

```java
// Brute Force — O(n) two-pass, O(1) space
public void sortColorsBrute(int[] nums) {
    int count0 = 0;
    int count1 = 0;
    // Count occurrences (first pass)
    for (int num : nums) {
        if (num == 0) {
            count0++;
        } else if (num == 1) {
            count1++;
        }
    }
    // Overwrite (second pass)
    for (int i = 0; i < nums.length; i++) {
        if (i < count0) {
            nums[i] = 0;
        } else if (i < count0 + count1) {
            nums[i] = 1;
        } else {
            nums[i] = 2;
        }
    }
}
```

**Why is this bad?** Two passes. Interview asks for ONE pass.

---

### 💡 Idea Behind Optimisation

Three pointers, three invariants:
- `[0, low)` → all 0s
- `[low, mid)` → all 1s  
- `(high, n-1]` → all 2s
- `[mid, high]` → unknown (shrink this)

Process `nums[mid]`:
- If 0 → swap with `nums[low]`, advance both `low` and `mid`
- If 1 → it's in the right zone, just advance `mid`
- If 2 → swap with `nums[high]`, shrink `high` (don't advance `mid` — the swapped element is unknown)

### 🎨 Visual — Dutch National Flag Pointer Movement

```
          0    1    2    3    4    5    ← indices

Initial: [2,   0,   2,   1,   1,   0]
          ^                        ^
         L/M                       H
         (low=0, mid=0, high=5)

Step 1: nums[mid=0]=2 → swap(idx 0, idx 5) → high--
         [0,   0,   2,   1,   1,   2]
          ^                   ^
         L/M                  H
         (low=0, mid=0, high=4)

Step 2: nums[mid=0]=0 → swap(idx 0, idx 0) no-op → low++, mid++
         [0,   0,   2,   1,   1,   2]
               ^              ^
              L/M              H
              (low=1, mid=1, high=4)

Step 3: nums[mid=1]=0 → swap(idx 1, idx 1) no-op → low++, mid++
         [0,   0,   2,   1,   1,   2]
                    ^         ^
                   L/M        H
                   (low=2, mid=2, high=4)

Step 4: nums[mid=2]=2 → swap(idx 2, idx 4) → high--
         [0,   0,   1,   1,   2,   2]
                    ^    ^
                   L/M   H
                   (low=2, mid=2, high=3)

Step 5: nums[mid=2]=1 → mid++ only → mid=3
Step 6: nums[mid=3]=1 → mid++ only → mid=4
         mid(4) > high(3) → DONE ✓

KEY INVARIANT:
  At all times: [0..low) = 0s, [low..mid) = 1s, (high..n-1] = 2s.
  When mid > high, the entire array is partitioned.
  Never advance mid after swapping with high — the incoming element is unexamined.
```

---

### 🚀 Optimal Java Solution

```java
// Dutch National Flag — O(n) one pass, O(1) space
public void sortColors(int[] nums) {
    int low = 0;
    int mid = 0;
    int high = nums.length - 1;

    while (mid <= high) {
        if (nums[mid] == 0) {
            // Swap with low boundary, advance both
            int temp = nums[low];
            nums[low] = nums[mid];
            nums[mid] = temp;
            low++;
            mid++;
        } else if (nums[mid] == 1) {
            // 1 is in correct zone, just move forward
            mid++;
        } else {
            // nums[mid] == 2: swap with high boundary
            // Do NOT advance mid — swapped element is unexamined
            int temp = nums[mid];
            nums[mid] = nums[high];
            nums[high] = temp;
            high--;
        }
    }
}
```

---

### ⏱️ Complexity

| Approach | Time | Space | Passes |
|---|---|---|---|
| Count + Overwrite | O(n) | O(1) | 2 |
| Dutch National Flag | O(n) | O(1) | 1 |

---

### 🔁 Follow-up Questions

**Q1: Why do we NOT advance `mid` when we swap with `high`?**  
Because `nums[high]` before the swap was unexamined — after swapping it into position `mid`, we don't know if it's 0, 1, or 2. We must re-examine `mid` in the next iteration.

**Q2: Can you extend this to 4 colors?**  
Yes — use 4 pointers and 3 boundaries. Each extra value adds a pointer. Beyond 3 values, a counting sort approach is cleaner.

**Q3: What happens if array is already sorted?**  
`low` catches up to `mid` quickly; `high` never shrinks. O(n) same — no optimization opportunity since we must inspect every element.

**Q4: Is this stable?**  
No. Swapping disrupts relative order of equal elements.

---

---

## 4. Min Cost Valid Path in Grid — LC #1368

**Difficulty:** Hard | **Pattern:** 0-1 BFS (Deque), Grid  
**Confirmed in:** High-frequency Docusign tag (graph/grid problems)

---

### 🎯 Problem Statement

You have an `m × n` grid. Each cell `grid[i][j]` has a sign pointing in one direction:
- `1` = right, `2` = left, `3` = down, `4` = up

You can move in any of the 4 directions from any cell. Moving in the **direction the sign points** costs `0`. Changing direction (moving against or perpendicular to the sign) costs `1` (you change the sign).

Find the **minimum cost** to reach `(m-1, n-1)` from `(0, 0)`.

```
Example:
grid = [[1,1,1,1],[2,0,0,0],[1,1,1,1],[0,0,0,1]]
Output: 3
```

---

### 🧠 Discussion — How to Think About This

Two types of moves:
- Free (cost 0): moving in the direction the sign already points
- Costly (cost 1): moving in any other direction (you pay to change the sign)

Since costs are only 0 or 1, **Dijkstra is overkill** — use **0-1 BFS** with a `Deque` (double-ended queue):
- Cost-0 moves go to the **front** of the deque (like BFS)
- Cost-1 moves go to the **back** (like BFS but delayed by 1 level)

This gives O(m×n) instead of Dijkstra's O(m×n×log(m×n)).

---

### 🐌 Brute Force Approach

Dijkstra with a priority queue.

```java
import java.util.*;

// Dijkstra approach — O(m*n*log(m*n))
public int minCostDijkstra(int[][] grid) {
    int m = grid.length;
    int n = grid[0].length;
    int[][] dist = new int[m][n];
    for (int[] row : dist) {
        Arrays.fill(row, Integer.MAX_VALUE);
    }
    dist[0][0] = 0;

    // Priority queue: [cost, row, col]
    PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
    pq.offer(new int[]{ 0, 0, 0 });

    // Directions: right=1, left=2, down=3, up=4
    int[][] dirs = { {0,1}, {0,-1}, {1,0}, {-1,0} };

    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int cost = curr[0];
        int r = curr[1];
        int c = curr[2];

        if (cost > dist[r][c]) {
            continue;
        }

        for (int d = 0; d < 4; d++) {
            int nr = r + dirs[d][0];
            int nc = c + dirs[d][1];
            if (nr < 0 || nr >= m || nc < 0 || nc >= n) {
                continue;
            }
            // grid stores 1-indexed direction; d+1 maps to our dirs array
            int moveCost = (grid[r][c] == d + 1) ? 0 : 1;
            if (dist[r][c] + moveCost < dist[nr][nc]) {
                dist[nr][nc] = dist[r][c] + moveCost;
                pq.offer(new int[]{ dist[nr][nc], nr, nc });
            }
        }
    }
    return dist[m - 1][n - 1];
}
```

---

### 💡 Idea Behind Optimisation

0-1 BFS: when edge weights are only 0 or 1, a deque replaces the priority queue.
- Free move (cost 0) → `deque.addFirst(neighbor)` — process immediately  
- Costly move (cost 1) → `deque.addLast(neighbor)` — process after current level  

The deque always maintains monotone non-decreasing order of costs — same guarantee as Dijkstra but without the log factor.

### 🎨 Visual — 0-1 BFS Deque Behavior

```
Cell (0,0) sign=1 (RIGHT)

Neighbors:
  RIGHT (0,1): sign matches direction 1 → cost 0 → addFirst  ← FREE
  DOWN  (1,0): sign says RIGHT, we go DOWN → cost 1 → addLast

Deque after processing (0,0):
  FRONT [ (0,1, cost=0) ] [ (1,0, cost=1) ] BACK

Process (0,1) next (from front) — cost is still 0
  No log(n) overhead for ordering!

KEY INVARIANT:
  Cost-0 edges go to front (explored this "level")
  Cost-1 edges go to back  (explored next "level")
  Deque front always has minimum cost element.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public int minCost(int[][] grid) {
    int m = grid.length;
    int n = grid[0].length;

    // Directions: index 0=right, 1=left, 2=down, 3=up
    // grid values:         1=right, 2=left, 3=down, 4=up
    int[][] dirs = { {0,1}, {0,-1}, {1,0}, {-1,0} };

    int[][] dist = new int[m][n];
    for (int[] row : dist) {
        Arrays.fill(row, Integer.MAX_VALUE);
    }
    dist[0][0] = 0;

    // Deque for 0-1 BFS: stores [row, col]
    Deque<int[]> deque = new ArrayDeque<>();
    deque.addFirst(new int[]{ 0, 0 });

    while (!deque.isEmpty()) {
        int[] curr = deque.pollFirst();
        int r = curr[0];
        int c = curr[1];

        for (int d = 0; d < 4; d++) {
            int nr = r + dirs[d][0];
            int nc = c + dirs[d][1];
            if (nr < 0 || nr >= m || nc < 0 || nc >= n) {
                continue;
            }

            // grid[r][c] is 1-indexed; d is 0-indexed → compare with d+1
            int moveCost = (grid[r][c] == d + 1) ? 0 : 1;
            int newDist = dist[r][c] + moveCost;

            if (newDist < dist[nr][nc]) {
                dist[nr][nc] = newDist;
                if (moveCost == 0) {
                    // Free move — push to front
                    deque.addFirst(new int[]{ nr, nc });
                } else {
                    // Costly move — push to back
                    deque.addLast(new int[]{ nr, nc });
                }
            }
        }
    }
    return dist[m - 1][n - 1];
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Dijkstra | O(m×n×log(m×n)) | O(m×n) |
| 0-1 BFS | O(m×n) | O(m×n) |

---

### 🔁 Follow-up Questions

**Q1: When should you use 0-1 BFS vs Dijkstra?**  
0-1 BFS only works when edge weights are strictly 0 or 1. Dijkstra handles arbitrary non-negative weights.

**Q2: Why does addFirst for cost-0 work correctly?**  
Because cost-0 neighbors should be processed at the same "cost level" as the current node — they're no further from the source. addFirst keeps them at the front of the current level.

**Q3: Can edge costs be negative?**  
No. 0-1 BFS and Dijkstra both require non-negative edges. Use Bellman-Ford for negative edges.

---

---

## 5. House Robber II — LC #213

**Difficulty:** Medium | **Pattern:** DP on Circular Array  
**Confirmed in:** High-frequency Docusign tag

---

### 🎯 Problem Statement

Houses are arranged in a **circle**. You cannot rob two adjacent houses. Find the maximum amount you can rob.

```
Example:
Input:  [2, 3, 2]
Output: 3  (rob house 1 only; houses 0 and 2 are adjacent via circle)

Input:  [1, 2, 3, 1]
Output: 4  (rob house 0 and house 2)
```

---

### 🧠 Discussion — How to Think About This

Linear House Robber (LC #198) is straightforward DP. The circle adds one constraint: **you cannot rob both house 0 and house n-1** (they're neighbors).

**Key insight:** Since we can't rob both ends, **exactly one of them will be excluded**. So run House Robber I twice:
1. On houses `[0, n-2]` (exclude last)
2. On houses `[1, n-1]` (exclude first)

Take the max of the two results.

---

### 🐌 Brute Force Approach

Try all subsets (exponential — just mention it, don't code).

The first real improvement is the DP approach — O(n). The "trick" is running it twice.

---

### 💡 Idea Behind Optimisation

The circular constraint reduces to: "rob either the left segment or the right segment — never both endpoints." Two linear DP runs handle this cleanly.

### 🎨 Visual — Two-Run Strategy

```
Houses: [2, 3, 2, 5, 1]  (circular)
         0  1  2  3  4

Circle means: house 0 and house 4 are neighbors.

Run 1: rob [0..3] = [2, 3, 2, 5]  → DP → 7  (rob 0+2+5? No. 3+5=8? No. 2+5=7 ✓)
Run 2: rob [1..4] = [3, 2, 5, 1]  → DP → 8  (rob 3+5=8 ✓)

Answer: max(7, 8) = 8

KEY INVARIANT:
  Either house 0 is skipped (Run 2) OR house n-1 is skipped (Run 1).
  The max of both runs covers all valid non-adjacent subsets.
```

---

### 🚀 Optimal Java Solution

```java
public int rob(int[] nums) {
    int n = nums.length;

    // Edge case: single house
    if (n == 1) {
        return nums[0];
    }

    // Run linear house robber on a subarray [start, end] inclusive
    return Math.max(
        linearRob(nums, 0, n - 2),  // Exclude last house
        linearRob(nums, 1, n - 1)   // Exclude first house
    );
}

private int linearRob(int[] nums, int start, int end) {
    int prev2 = 0;
    int prev1 = 0;

    for (int i = start; i <= end; i++) {
        // Either skip current house (prev1) or rob it (prev2 + nums[i])
        int current = Math.max(prev1, prev2 + nums[i]);
        prev2 = prev1;
        prev1 = current;
    }
    return prev1;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force | O(2^n) | O(n) |
| DP (two runs) | O(n) | O(1) |

---

### 🔁 Follow-up Questions

**Q1: Why does running twice on subarrays correctly handle the circle?**  
Because the only forbidden pair is (0, n-1). Every other non-adjacent subset is valid. One of the two runs will include the optimal solution (the one that naturally avoids the forbidden pair).

**Q2: What if you could rob up to k adjacent houses (not just 1)?**  
The recurrence changes: `dp[i] = max(dp[i-1], dp[i-k-1] + nums[i])`. Still O(n), still two-run for circular.

**Q3: What is `prev2` and `prev1` tracking?**  
`prev1` = best solution ending at the previous house (either robbing it or not). `prev2` = best solution two houses back. This is the space-optimized version of the full DP array.

---

---

## 6. House Robber III — LC #337

**Difficulty:** Medium | **Pattern:** Tree DP, Post-order DFS  
**Confirmed in:** High-frequency Docusign tag (tree problems)

---

### 🎯 Problem Statement

Houses are arranged in a **binary tree**. Adjacent houses (parent-child) cannot both be robbed. Find the maximum you can rob.

```
Example:
     3
    / \
   2   3
    \    \
     3    1

Output: 7  (rob 3 + 3 + 1 = 7, skipping the middle level)
```

---

### 🧠 Discussion — How to Think About This

At each node, two choices: **rob it** (skip children, can use grandchildren) or **skip it** (let each child decide independently). The constraint propagates: what you do at a node determines what choices are available two levels below.

---

### 🐌 Brute Force Approach

Plain recursion — explicitly look two levels down for the "rob" case:

```java
public int rob(TreeNode root) {
    if (root == null) {
        return 0;
    }
    // Choice 1: rob this node — skip children, use grandchildren
    int robThis = root.val;
    if (root.left != null) {
        robThis += rob(root.left.left) + rob(root.left.right);
    }
    if (root.right != null) {
        robThis += rob(root.right.left) + rob(root.right.right);
    }
    // Choice 2: skip this node — take best of direct children
    int skipThis = rob(root.left) + rob(root.right);
    return Math.max(robThis, skipThis);
}
```

**Why O(2^n):** `rob(node)` is called from its parent (skip path) AND from its grandparent (rob path). The same subtree is recomputed exponentially — `rob(left)` fires once from `root`'s skip branch, then fires again when `root`'s grandparent fetches its grandchildren via the rob branch. Overlapping subproblems grow exponentially with tree depth.

---

### 💡 Idea Behind Optimisation

Each DFS call recomputes the same node's answer via different ancestors. **Return both states `[robThis, skipThis]` in one call** — the parent assembles its own pair from children's pairs in O(1). One post-order DFS pass, every node visited exactly once. O(n).

**Return from each DFS call:** a pair `[robThis, skipThis]`:
- `robThis` = max profit when we rob the current node
- `skipThis` = max profit when we skip the current node

---

### 🎨 Visual — Two-State Tree DP

```
     3
    / \
   2   3
    \    \
     3    1

Post-order DFS (bottom-up):

Node 3 (leaf, left-right of 2): return [3, 0]
  robThis=3 (rob it, no children)
  skipThis=0 (skip it, nothing below)

Node 2:
  left child = null → [0, 0]
  right child = [3, 0]
  robThis  = 2 + max(0,0) + max(3,0) = 2 + 0 + 3 = 5  ← skip children
  skipThis = max(0,0) + max(3,0)     = 0 + 3     = 3  ← best of children
  return [5, 3]

Node 1 (leaf, right of right 3): return [1, 0]

Node 3 (right child of root):
  left=null → [0,0], right=[1,0]
  robThis  = 3 + 0 + max(1,0) = 4
  skipThis = max(0,0) + max(1,0) = 1
  return [4, 1]

Root 3:
  left=[5,3], right=[4,1]
  robThis  = 3 + max(0+3) + max(4+1) ... wait:
           = 3 + skipThis(left) + skipThis(right)
           = 3 + 3 + 1 = 7
  skipThis = max(robLeft, skipLeft) + max(robRight, skipRight)
           = max(5,3) + max(4,1)
           = 5 + 4 = 9?

Hmm wait, let me redo:
  robThis  = root.val + skipLeft + skipRight = 3 + 3 + 1 = 7
  skipThis = max(5,3) + max(4,1)             = 5 + 4 = 9

Answer = max(7, 9) = 9? Let me verify:
  skip root(3), rob node(2)=5, rob node(3)=4 → 5+4=9 ✓

KEY INVARIANT:
  Each node returns [rob, skip].
  Parent combines: rob = val + skip(L) + skip(R)
                   skip = max(rob,skip)(L) + max(rob,skip)(R)
```

---

### 🚀 Optimal Java Solution

```java
public int rob(TreeNode root) {
    int[] result = dfs(root);
    // result[0] = max when we rob root
    // result[1] = max when we skip root
    return Math.max(result[0], result[1]);
}

// Returns [robThis, skipThis]
private int[] dfs(TreeNode node) {
    if (node == null) {
        return new int[]{ 0, 0 };
    }

    int[] left = dfs(node.left);
    int[] right = dfs(node.right);

    // Rob current node: must skip both children
    int robCurrent = node.val + left[1] + right[1];

    // Skip current node: take best from each child independently
    int skipCurrent = Math.max(left[0], left[1]) + Math.max(right[0], right[1]);

    return new int[]{ robCurrent, skipCurrent };
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Naive recursive (recompute) | O(2^n) | O(h) |
| Tree DP (pair return) | O(n) | O(h) |

h = height of tree (O(log n) balanced, O(n) worst case)

---

### 🔁 Follow-up Questions

**Q1: Why return a pair instead of memoizing?**  
The pair approach computes both states in one DFS pass — no HashMap needed. Memoizing on the node reference works too but adds overhead.

**Q2: What if the tree is a general N-ary tree?**  
Same logic — `robCurrent = node.val + sum of skipChild for all children`. `skipCurrent = sum of max(rob,skip) for each child`.

**Q3: How does this differ from House Robber I?**  
Robber I is linear (array). Here the "adjacency" constraint follows a tree structure — children, not array neighbors.

---

---

## 7. Moving Average from Data Stream — LC #346

**Difficulty:** Easy | **Pattern:** Sliding Window, Queue  
**Confirmed in:** High-frequency Docusign tag (streaming data)

---

### 🎯 Problem Statement

Design a class that calculates the **moving average** of the last `k` numbers from a data stream.

```
Example:
MovingAverage m = new MovingAverage(3);
m.next(1)  → 1.0           (window: [1])
m.next(10) → 5.5           (window: [1, 10])
m.next(3)  → 4.67          (window: [1, 10, 3])
m.next(5)  → 6.0           (window: [10, 3, 5])
```

---

### 🧠 Discussion — How to Think About This

A sliding window of size `k` over a stream. You need the sum of the window at each step.

### 🐌 Brute Force Approach

Store all elements in an ArrayList. On each `next(val)` call, add the element and then re-sum the last `k` elements → O(k) per call.

```java
class MovingAverage {
    private final int size;
    private final List<Integer> data;

    public MovingAverage(int size) {
        this.size = size;
        this.data = new ArrayList<>();
    }

    public double next(int val) {
        data.add(val);
        int from = Math.max(0, data.size() - size);
        // Re-scan last k elements every call — O(k)
        int sum = 0;
        for (int i = from; i < data.size(); i++) {
            sum += data.get(i);
        }
        return (double) sum / (data.size() - from);
    }
}
```

**Why O(k):** Every call re-scans up to `k` elements. No reuse of previous sum.

### 💡 Idea Behind Optimisation

Between consecutive calls, the window shifts by exactly one element — one element exits (oldest), one enters (newest). The previous sum minus the outgoing element plus the new element gives the new sum in O(1). Use a `Queue` (FIFO) to track the window — when size exceeds `k`, poll the oldest element and subtract it from the running sum.

### 🎨 Visual — Queue as Sliding Window

```
k = 3, stream = [1, 10, 3, 5]

After next(1):  Queue=[1],     sum=1,   avg=1.0
After next(10): Queue=[1,10],  sum=11,  avg=5.5
After next(3):  Queue=[1,10,3],sum=14,  avg=4.67  (full window)
After next(5):  
  → queue.size() == k → poll 1 (oldest), sum -= 1 → sum=13
  → add 5, sum=18
  Queue=[10,3,5], sum=18, avg=6.0

KEY INVARIANT:
  Queue always holds exactly min(count, k) elements.
  Sum is maintained incrementally — no re-scan ever needed.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.LinkedList;
import java.util.Queue;

class MovingAverage {
    private final int size;
    private final Queue<Integer> window;
    private double sum;

    public MovingAverage(int size) {
        this.size = size;
        this.window = new LinkedList<>();
        this.sum = 0.0;
    }

    public double next(int val) {
        // If window is full, remove oldest element
        if (window.size() == size) {
            sum -= window.poll();
        }
        // Add new element
        window.offer(val);
        sum += val;
        return sum / window.size();
    }
}
```

---

### ⏱️ Complexity

| Approach | Time per call | Space |
|---|---|---|
| Naive (re-sum) | O(k) | O(k) |
| Queue + running sum | O(1) | O(k) |

---

### 🔁 Follow-up Questions

**Q1: Can you use an array instead of a Queue?**  
Yes — a circular array of size `k` with a pointer is more cache-friendly. Index = `count % k`. Overwrite the oldest slot each time.

**Q2: What if values are floating point?**  
Same approach. Just use `double` instead of `int` in the queue.

**Q3: What if k = 0?**  
Guard with `if (size == 0) return 0.0;` or throw IllegalArgumentException.

---

---

## 8. Nested Boxes / Maximum Depth of N-ary Tree

**Difficulty:** Medium | **Pattern:** N-ary Tree DFS / BFS  
**Confirmed in:** Docusign-style custom design problem (document hierarchy)

---

### 🎯 Problem Statement

You have nested document folders (or boxes). Each folder can contain subfolders. Find the maximum nesting depth.

This maps to: given an N-ary tree (each node can have any number of children), find the maximum depth.

```
Example:
          root
         / | \
        A  B  C
       / \     \
      D   E     F
                 \
                  G

Output: 4  (root → C → F → G)
```

---

### 🧠 Discussion — How to Think About This

Standard tree traversal problem. Two clean approaches:
1. **Recursive DFS** — depth of node = 1 + max(depth of children)
2. **Iterative BFS** — level-order traversal, count levels

For N-ary trees, the only difference from binary trees is iterating over `node.children` instead of just `left` and `right`.

### 🎨 Visual — Recursive DFS Depth Computation

```
depth(G) = 1
depth(F) = 1 + depth(G) = 2
depth(C) = 1 + max(depth(F)) = 3
depth(D) = 1, depth(E) = 1
depth(A) = 1 + max(depth(D), depth(E)) = 2
depth(B) = 1
depth(root) = 1 + max(depth(A), depth(B), depth(C))
            = 1 + max(2, 1, 3) = 4

KEY INVARIANT:
  depth(node) = 1 + max(depth(child)) for all children.
  Leaf nodes return 1.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

// N-ary TreeNode definition
class Node {
    public int val;
    public List<Node> children;
    public Node(int val) {
        this.val = val;
        this.children = new ArrayList<>();
    }
}

class NestedBoxes {

    // Recursive DFS — O(n) time, O(h) space (call stack)
    public int maxDepth(Node root) {
        if (root == null) {
            return 0;
        }
        int maxChildDepth = 0;
        for (Node child : root.children) {
            maxChildDepth = Math.max(maxChildDepth, maxDepth(child));
        }
        return 1 + maxChildDepth;
    }

    // Iterative BFS — O(n) time, O(w) space (w = max width)
    public int maxDepthBFS(Node root) {
        if (root == null) {
            return 0;
        }
        Queue<Node> queue = new LinkedList<>();
        queue.offer(root);
        int depth = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            depth++;
            // Process all nodes at current level
            for (int i = 0; i < levelSize; i++) {
                Node node = queue.poll();
                for (Node child : node.children) {
                    queue.offer(child);
                }
            }
        }
        return depth;
    }
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Recursive DFS | O(n) | O(h) — call stack |
| Iterative BFS | O(n) | O(w) — queue width |

---

### 🔁 Follow-up Questions

**Q1: When would BFS be preferred over DFS?**  
When the tree is very deep (h ≈ n), DFS risks stack overflow. BFS uses O(w) queue space which is bounded by the tree's width.

**Q2: How do you find the path to the deepest node, not just the depth?**  
Track parent pointers during BFS, or carry the path in DFS and record it when a new max depth is reached.

---

---

## 9. Serialize and Deserialize Binary Tree — LC #297

**Difficulty:** Hard | **Pattern:** Tree Traversal, DFS with markers  
**Confirmed in:** High-frequency Docusign tag

---

### 🎯 Problem Statement

Design algorithms to:
- `serialize(root)` — convert a binary tree to a string
- `deserialize(data)` — reconstruct the tree from that string

```
Example:
     1
    / \
   2   3
      / \
     4   5

serialize → "1,2,null,null,3,4,null,null,5,null,null"
deserialize → rebuilds the original tree
```

---

### 🧠 Discussion — How to Think About This

**Why is it hard?** Knowing the values isn't enough — you need to know the structure (where nulls are). A level-order or pre-order traversal with explicit `null` markers uniquely defines the tree.

**Pre-order approach (recommended for interview):**
- Serialize: pre-order DFS, append `"null"` for missing children, use comma separator.
- Deserialize: reconstruct using the same pre-order — use a `Queue<String>` of tokens. For each token:
  - If `"null"` → return null
  - Else → create node, recurse for left child, recurse for right child

### 🎨 Visual — Pre-order Serialization

```
     1
    / \
   2   3
      / \
     4   5

Pre-order DFS (root → left → right):
Visit 1   → append "1,"
Visit 2   → append "2,"
Visit null → append "null,"   (left of 2)
Visit null → append "null,"   (right of 2)
Visit 3   → append "3,"
Visit 4   → append "4,"
Visit null → append "null,"   (left of 4)
Visit null → append "null,"   (right of 4)
Visit 5   → append "5,"
Visit null → append "null,"
Visit null → append "null,"

Result: "1,2,null,null,3,4,null,null,5,null,null"

Deserialize: pop from queue token by token, same pre-order.
  "1" → root=1, go left
  "2" → left=2, go left
  "null" → left of 2 = null
  "null" → right of 2 = null
  "3" → right of root=3, go left
  "4" → left of 3=4 ...

KEY INVARIANT:
  Pre-order with null markers uniquely encodes structure AND values.
  Deserialization mirrors serialization exactly.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public class Codec {

    // Serialize: pre-order DFS with null markers
    public String serialize(TreeNode root) {
        StringBuilder sb = new StringBuilder();
        serializeHelper(root, sb);
        return sb.toString();
    }

    private void serializeHelper(TreeNode node, StringBuilder sb) {
        if (node == null) {
            sb.append("null,");
            return;
        }
        sb.append(node.val).append(",");
        serializeHelper(node.left, sb);
        serializeHelper(node.right, sb);
    }

    // Deserialize: rebuild from token queue
    public TreeNode deserialize(String data) {
        // WORKAROUND if this line feels hard to recall:
        //   String[] arr = data.split(",");        // step 1: split string → array
        //   List<String> list = Arrays.asList(arr); // step 2: array → List (asList wraps it)
        //   Queue<String> tokens = new LinkedList<>(list); // step 3: List → Queue (LinkedList copy-constructor)
        // The one-liner just chains all three steps. Read it right-to-left: split → asList → LinkedList.
        Queue<String> tokens = new LinkedList<>(Arrays.asList(data.split(",")));
        return deserializeHelper(tokens);
    }

    private TreeNode deserializeHelper(Queue<String> tokens) {
        String token = tokens.poll();
        if ("null".equals(token)) {
            return null;
        }
        TreeNode node = new TreeNode(Integer.parseInt(token));
        // Reconstruct left subtree first (mirrors pre-order)
        node.left = deserializeHelper(tokens);
        node.right = deserializeHelper(tokens);
        return node;
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| Serialize | O(n) | O(n) string + O(h) stack |
| Deserialize | O(n) | O(n) queue + O(h) stack |

---

### 🔁 Follow-up Questions

**Q1: Why pre-order and not in-order?**  
In-order traversal doesn't let you identify the root during deserialization without extra information. Pre-order always starts with the root — easy to reconstruct.

**Q2: Can you use BFS (level-order) instead?**  
Yes — serialize level by level with null markers (like LeetCode's own format). Deserialize level by level using a queue. Same O(n) but the code is slightly more complex.

**Q3: What if node values are negative?**  
`Integer.parseInt` handles negative numbers. No change needed.

**Q4: How would you handle a general N-ary tree?**  
You need to encode the number of children per node. Format: `"val numChildren child1 child2 ..."`. Pre-order still works.

---

---

## 10. Insert Delete GetRandom O(1) — LC #380

**Difficulty:** Medium | **Pattern:** HashMap + ArrayList  
**Confirmed in:** High-frequency Docusign tag

---

### 🎯 Problem Statement

Design a data structure that supports all three operations in **average O(1)** time:
- `insert(val)` — insert an element (if not present). Return true if not already present.
- `remove(val)` — remove an element (if present). Return true if present.
- `getRandom()` — return a random element from the set. Each element must have equal probability.

```
Example:
RandomizedSet rs = new RandomizedSet();
rs.insert(1);   → true
rs.insert(2);   → true
rs.remove(1);   → true
rs.insert(2);   → false (already exists)
rs.getRandom(); → 2 (only element)
```

---

### 🧠 Discussion — How to Think About This

Three operations: `insert`, `remove`, `getRandom`. Each must be O(1) average.

### 🐌 Brute Force Approach

Use only a `HashSet<Integer>`. `insert` and `remove` are O(1). But `getRandom` needs to pick from arbitrary positions — there's no index-based access on a HashSet. The only way is: convert to a list each time and pick randomly → O(n) per `getRandom`.

```java
class RandomizedSet {
    private final Set<Integer> set = new HashSet<>();

    public boolean insert(int val) {
        return set.add(val);
    }

    public boolean remove(int val) {
        return set.remove(val);
    }

    public int getRandom() {
        // O(n) — must convert to list to index randomly
        List<Integer> list = new ArrayList<>(set);
        return list.get(new Random().nextInt(list.size()));
    }
}
```

**Why O(n):** HashSet has no positional index. Random access requires materializing all elements first.

### 💡 Idea Behind Optimisation

Pair a `HashMap<val → index>` with an `ArrayList<val>`. HashMap gives O(1) lookup; ArrayList gives O(1) random access via `list.get(random)`. The only remaining problem is `remove` from the middle of an ArrayList — that's O(n) due to shifting. Fix: **swap the target element with the last element**, then remove the last. Update the HashMap to reflect the swapped element's new index. O(1) total.

### 🎨 Visual — Swap-and-Pop Remove

```
ArrayList: [1, 2, 3, 4, 5]
HashMap:   {1→0, 2→1, 3→2, 4→3, 5→4}

Remove(3):
  idx = map.get(3) = 2
  lastVal = list.get(4) = 5
  list.set(2, 5)      → [1, 2, 5, 4, 5]
  map.put(5, 2)       → {1→0, 2→1, 5→2, 4→3}
  list.remove(4)      → [1, 2, 5, 4]
  map.remove(3)       → {1→0, 2→1, 5→2, 4→3}

getRandom(): Math.random() * list.size() → list.get(that index)

KEY INVARIANT:
  ArrayList provides O(1) random access for getRandom.
  Swap-and-pop avoids O(n) shifting for remove.
  HashMap always maps val → current index in the list.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

class RandomizedSet {
    private final Map<Integer, Integer> indexMap;  // val → index in list
    private final List<Integer> list;
    private final Random rand;

    public RandomizedSet() {
        indexMap = new HashMap<>();
        list = new ArrayList<>();
        rand = new Random();
    }

    public boolean insert(int val) {
        if (indexMap.containsKey(val)) {
            return false;
        }
        // Add to end of list, record index in map
        list.add(val);
        indexMap.put(val, list.size() - 1);
        return true;
    }

    public boolean remove(int val) {
        if (!indexMap.containsKey(val)) {
            return false;
        }
        int idx = indexMap.get(val);
        int lastVal = list.get(list.size() - 1);

        // Swap target with last element
        list.set(idx, lastVal);
        indexMap.put(lastVal, idx);

        // Remove the last element (now a duplicate)
        list.remove(list.size() - 1);
        indexMap.remove(val);
        return true;
    }

    public int getRandom() {
        return list.get(rand.nextInt(list.size()));
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| insert | O(1) avg | O(n) |
| remove | O(1) avg | O(n) |
| getRandom | O(1) | O(n) |

---

### 🔁 Follow-up Questions

**Q1: What is the edge case when removing the last element?**  
When `idx == list.size() - 1`, the swap is a no-op (swapping with itself). The code still works correctly — `list.set(idx, lastVal)` sets the same position, then `list.remove(last)` removes it. Make sure to not call `indexMap.put(lastVal, idx)` before removing when idx == last (or handle it — it's a no-op either way).

**Q2: What if duplicates are allowed (LC #381)?**  
Store `Map<Integer, Set<Integer>>` (val → set of indices) instead of single index. Remove one index from the set arbitrarily.

**Q3: Why is `ArrayList.remove(int index)` O(1) at the last position?**  
Removing the last element doesn't require shifting any elements — Java's ArrayList just decrements the size. Only removes from the middle require shifting.

---

---

## 11. Time-Based Key-Value Store — LC #981

**Difficulty:** Medium | **Pattern:** HashMap + TreeMap + Binary Search  
**Confirmed in:** Strong Docusign tag (versioning, document history)

---

### 🎯 Problem Statement

Design a key-value store that stores multiple values per key, each with a timestamp. Support:
- `set(key, value, timestamp)` — store value at the given timestamp.
- `get(key, timestamp)` — return the value with the largest timestamp ≤ given timestamp. If none, return `""`.

**Guarantee:** All `set` timestamps are strictly increasing.

```
Example:
store.set("foo", "bar", 1);
store.set("foo", "bar2", 4);
store.get("foo", 4) → "bar2"
store.get("foo", 5) → "bar2"  (largest ts ≤ 5 is 4)
store.get("foo", 3) → "bar"   (largest ts ≤ 3 is 1)
store.get("foo", 0) → ""      (no ts ≤ 0 exists)
```

---

### 🧠 Discussion — How to Think About This

Each key maps to a time-series of values. To find the value at or before a given timestamp → **floor query** (largest timestamp ≤ given timestamp).

### 🐌 Brute Force Approach

Use a single map with a typed inner `record` to hold timestamp + value together cleanly. Append in insertion order. For `get`, **linear scan** from the end to find the largest timestamp ≤ given → O(n) per `get`.

```java
class TimeMap {
    // Record keeps timestamp (int) and value (String) together — no type conversion, one map
    private record Entry(int timestamp, String value) {}

    private final Map<String, List<Entry>> store = new HashMap<>();

    public void set(String key, String value, int timestamp) {
        // Timestamps arrive strictly increasing — append keeps list sorted
        store.computeIfAbsent(key, k -> new ArrayList<>())
             .add(new Entry(timestamp, value));
    }

    public String get(String key, int timestamp) {
        if (!store.containsKey(key)) {
            return "";
        }
        List<Entry> entries = store.get(key);
        // O(n) — scan from end for largest timestamp <= given
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).timestamp() <= timestamp) {
                return entries.get(i).value();
            }
        }
        return "";
    }
}
```

**Why O(n):** Linear scan of all entries per `get`. With many `set` calls per key this degrades badly.

> **Wait — can't we binary search since the list is already sorted?**  
> Yes! Since timestamps are guaranteed strictly increasing, the list IS sorted. Binary search gives O(log n) — same as TreeMap. The true brute force is the linear scan above. See the Insight section for the full comparison.

### 💡 Idea Behind Optimisation

**Option A — Binary Search on ArrayList (O(log n), works only because input is sorted):**

Since `set` timestamps are strictly increasing (problem guarantee), the list stays sorted automatically. Binary search finds the floor index in O(log n) without any extra data structure.

```java
// Binary search for largest ts <= timestamp (floor)
// Returns the index of the floor, or -1 if none
private int floorIndex(List<Integer> ts, int timestamp) {
    int lo = 0;
    int hi = ts.size() - 1;
    int result = -1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (ts.get(mid) <= timestamp) {
            result = mid;
            lo = mid + 1;  // try to find a larger valid ts
        } else {
            hi = mid - 1;
        }
    }
    return result;
}
```

**Option B — TreeMap (O(log n), works even if timestamps came out of order):**

`TreeMap` maintains sorted order regardless of insertion order — the Red-Black tree rebalances on every insert. `floorKey(timestamp)` is one method call instead of writing binary search manually.

**Honest comparison for this problem specifically:**

| Approach | set | get | Works if out-of-order? | Code complexity |
|---|---|---|---|---|
| ArrayList + linear scan | O(1) | O(n) | ✅ | simple |
| ArrayList + binary search | O(1) | O(log n) | ❌ (needs sorted input) | medium |
| TreeMap | O(log n) | O(log n) | ✅ always | simplest |

**Why prefer TreeMap in an interview:** Single data structure, one method call (`floorKey`), no custom binary search to write, works even if the problem drops the sorted-input guarantee. Use ArrayList + binary search only if the interviewer specifically asks you to optimize `set` to O(1).

Use `HashMap<String, TreeMap<Integer, String>>`:
- Outer map: key → inner TreeMap
- Inner TreeMap: `timestamp → value`, and `floorKey(timestamp)` gives largest key ≤ timestamp in O(log n).

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

class TimeMap {
    // key → (timestamp → value), sorted by timestamp
    private final Map<String, TreeMap<Integer, String>> store;

    public TimeMap() {
        store = new HashMap<>();
    }

    public void set(String key, String value, int timestamp) {
        store.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
    }

    public String get(String key, int timestamp) {
        if (!store.containsKey(key)) {
            return "";
        }
        TreeMap<Integer, String> treeMap = store.get(key);
        // floorKey: largest key <= timestamp
        Integer floorTs = treeMap.floorKey(timestamp);
        if (floorTs == null) {
            return "";
        }
        return treeMap.get(floorTs);
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| set | O(log n) | O(n) |
| get | O(log n) | O(n) |

n = number of entries per key

---

### 🔁 Follow-up Questions

**Q1: If timestamps are guaranteed sorted, can you use a List instead of TreeMap?**  
Yes — store `List<int[]>` of `[timestamp, value]` pairs. Binary search for floor. O(log n) same complexity, but less overhead than TreeMap.

**Q2: How does `floorKey` work internally?**  
TreeMap is backed by a Red-Black tree. `floorKey(k)` traverses to find the largest key ≤ k — O(log n).

---

---

## 12. LRU Cache — LC #146

**Difficulty:** Medium | **Pattern:** HashMap + Doubly Linked List  
**Confirmed in:** High-frequency Docusign tag (document caching)

---

### 🎯 Problem Statement

Design a Least Recently Used (LRU) cache with capacity `k`:
- `get(key)` — return value if exists, else -1. Mark as recently used.
- `put(key, value)` — insert or update. If capacity is exceeded, evict the LRU entry.

Both operations must be O(1).

---

### 🧠 Discussion — How to Think About This

Need O(1) `get` and `put`, with LRU eviction when over capacity.

### 🐌 Brute Force Approach

Use a `HashMap<key, value>` plus a second `HashMap<key, timestamp>` to track last-access time. On `put` when over capacity, scan all entries to find the one with the smallest timestamp → O(n) eviction.

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, Integer> cache = new HashMap<>();
    private final Map<Integer, Long> lastAccess = new HashMap<>();
    private long time = 0;

    public LRUCache(int capacity) {
        this.capacity = capacity;
    }

    public int get(int key) {
        if (!cache.containsKey(key)) {
            return -1;
        }
        lastAccess.put(key, time++);
        return cache.get(key);
    }

    public void put(int key, int value) {
        if (!cache.containsKey(key) && cache.size() == capacity) {
            // O(n) — find LRU key by scanning timestamps
            int lruKey = Collections.min(
                lastAccess.entrySet(),
                Comparator.comparingLong(Map.Entry::getValue)
            ).getKey();
            cache.remove(lruKey);
            lastAccess.remove(lruKey);
        }
        cache.put(key, value);
        lastAccess.put(key, time++);
    }
}
```

**Why O(n):** To evict, we scan all timestamps to find the minimum — linear in cache size.

### 💡 Idea Behind Optimisation

Instead of scanning to find LRU, maintain order explicitly. A **Doubly Linked List (DLL)** keeps elements in access order — most recent at head, least recent at tail — and supports O(1) insert, delete, and move-to-front. Pair with a **HashMap** `key → Node` for O(1) lookup. On any access, splice the node to the head in O(1). Eviction = remove tail in O(1). Use **sentinel head and tail nodes** to avoid null checks on boundary nodes.

Two structures combined:
1. **HashMap** `key → Node` for O(1) lookup
2. **Doubly Linked List** for O(1) insert/delete and ordering (most recent = head, least recent = tail)

On `get`: move accessed node to head.  
On `put`: insert at head. If over capacity, remove tail.

### 🎨 Visual — DLL + HashMap

```
Capacity = 3, after put(1), put(2), put(3):

HEAD ↔ [3] ↔ [2] ↔ [1] ↔ TAIL
HashMap: {1→node1, 2→node2, 3→node3}

get(1): move node1 to front
HEAD ↔ [1] ↔ [3] ↔ [2] ↔ TAIL

put(4): capacity exceeded, remove TAIL.prev = node2
HEAD ↔ [4] ↔ [1] ↔ [3] ↔ TAIL
HashMap: {1→node1, 3→node3, 4→node4}

KEY INVARIANT:
  Most recently used = closest to HEAD.
  Least recently used = closest to TAIL.
  Evict TAIL.prev when over capacity.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.HashMap;
import java.util.Map;

class LRUCache {
    private class Node {
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
    private final Map<Integer, Node> map;
    private final Node head;  // Sentinel — most recent side
    private final Node tail;  // Sentinel — least recent side

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        // Sentinels eliminate null checks
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        if (!map.containsKey(key)) {
            return -1;
        }
        Node node = map.get(key);
        // Move to front (most recently used)
        remove(node);
        insertFront(node);
        return node.val;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            // Update existing node
            Node node = map.get(key);
            node.val = value;
            remove(node);
            insertFront(node);
        } else {
            Node node = new Node(key, value);
            map.put(key, node);
            insertFront(node);
            // Evict LRU if over capacity
            if (map.size() > capacity) {
                Node lru = tail.prev;
                remove(lru);
                map.remove(lru.key);
            }
        }
    }

    // Remove node from its current position
    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    // Insert node right after head (most recent)
    private void insertFront(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| get | O(1) | O(capacity) |
| put | O(1) | O(capacity) |

---

### 🔁 Follow-up Questions

**Q1: Why not use Java's LinkedHashMap?**  
`LinkedHashMap(capacity, 0.75f, true)` gives LRU behavior out of the box. But interviewers want you to implement from scratch — shows you understand the underlying structure.

**Q2: Why use sentinel nodes?**  
Avoids null checks for head.prev and tail.next — the code for `remove` and `insertFront` is identical for all nodes, including edge cases at boundaries.

**Q3: What changes for LFU (Least Frequently Used) cache?**  
Track access frequency per node. Use a `HashMap<frequency, DoublyLinkedList>` and a `minFreq` pointer. Significantly more complex — O(1) still achievable.

---

---

## 13. Combination Sum — LC #39

**Difficulty:** Medium | **Pattern:** Backtracking  
**Confirmed in:** Strong Docusign tag

---

### 🎯 Problem Statement

Given an array of distinct integers `candidates` and a `target`, find all unique combinations where the chosen numbers sum to target. Numbers may be reused.

```
Example:
candidates = [2, 3, 6, 7], target = 7
Output: [[2,2,3], [7]]
```

---

### 🧠 Discussion — How to Think About This

Classic backtracking: at each step, try including a candidate and recurse. To avoid duplicates, only consider candidates from the current index forward (don't go back).

### 🐌 Brute Force Approach

Backtrack without sorting the array — no early termination possible. When `candidates[i] > remaining`, we can only `continue` (skip this one candidate) rather than `break` (skip all remaining), because unsorted candidates could have smaller values later.

```java
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(candidates, target, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] candidates, int remaining, int start,
                        List<Integer> current, List<List<Integer>> result) {
    if (remaining == 0) {
        result.add(new ArrayList<>(current));
        return;
    }
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > remaining) {
            // Can only skip this one — must still check rest (unsorted array)
            continue;
        }
        current.add(candidates[i]);
        backtrack(candidates, remaining - candidates[i], i, current, result);
        current.remove(current.size() - 1);
    }
}
```

**Why worse:** Without sorting, we can't break early when one candidate exceeds `remaining`. We `continue` past each oversized candidate individually, still visiting every subsequent candidate. Sorting enables a single `break` that prunes all remaining candidates at once.

### 💡 Idea Behind Optimisation

Sort the candidates array first. Then in the loop, when `candidates[i] > remaining`, every subsequent candidate is also > remaining (since the array is sorted) — so `break` instead of `continue`. This prunes entire subtrees of the recursion tree in one instruction. Both versions are exponential in the worst case, but sorting dramatically reduces the constant factor.

**Template:**
1. Base case: `remaining == 0` → add current combination to result.
2. Loop from `start` to end: if `candidates[i] > remaining` → `break` (sorted array, all remaining also too large). Add `candidates[i]`, recurse with `remaining - candidates[i]` and `start = i` (allow reuse), then undo (backtrack).

### 🎨 Visual — Backtracking Tree

```
candidates=[2,3,6,7], target=7

                 []
          /       |      \     \
        [2]      [3]    [6]   [7]✓
       / | \     / \     |
    [2,2] [2,3] [2,6] [3,3] [3,6] [6,6]
    /  \    |
[2,2,2][2,2,3]✓ [2,3,...]
  |
[2,2,2,2] → sum=8 > 7, prune

KEY INVARIANT:
  Only advance start index forward — prevents [3,2] duplicate of [2,3].
  Allow same index i in recursion — enables reuse of same element.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public List<List<Integer>> combinationSum(int[] candidates, int target) {
    List<List<Integer>> result = new ArrayList<>();
    Arrays.sort(candidates);  // Optional: enables early pruning
    backtrack(candidates, target, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(
        int[] candidates,
        int remaining,
        int start,
        List<Integer> current,
        List<List<Integer>> result) {

    if (remaining == 0) {
        result.add(new ArrayList<>(current));
        return;
    }

    for (int i = start; i < candidates.length; i++) {
        // Early pruning: sorted array, if this candidate > remaining, rest also > remaining
        if (candidates[i] > remaining) {
            break;
        }
        current.add(candidates[i]);
        // Pass i (not i+1) to allow reuse of same element
        backtrack(candidates, remaining - candidates[i], i, current, result);
        current.remove(current.size() - 1);  // Backtrack
    }
}
```

---

### ⏱️ Complexity

| | Complexity |
|---|---|
| Time | O(n^(target/min)) — exponential in worst case |
| Space | O(target/min) — recursion depth |

---

### 🔁 Follow-up Questions

**Q1: What changes if each element can only be used once (LC #40)?**  
Pass `i+1` in the recursive call instead of `i`. Also sort and skip duplicates: `if (i > start && candidates[i] == candidates[i-1]) continue;`

**Q2: What if you want the count of combinations, not the combinations themselves?**  
Use DP. `dp[amount] = number of ways to make amount`. Iterate candidates, update dp.

---

---

## 14. Circular Dependency Detection — LC #207 Variant

**Difficulty:** Medium | **Pattern:** Graph DFS, Cycle Detection  
**Confirmed in:** Strong Docusign tag (document workflow dependencies)

---

### 🎯 Problem Statement

Given `n` courses (0 to n-1) and `prerequisites[i] = [a, b]` (must take b before a), determine if you can finish all courses. Return `true` if no cycle exists.

```
Example:
n=2, prerequisites=[[1,0]]      → true  (take 0, then 1)
n=2, prerequisites=[[1,0],[0,1]] → false (circular dependency)
```

---

### 🧠 Discussion — How to Think About This

This is cycle detection on a directed graph.

### 🐌 Brute Force Approach

Run a fresh DFS from every unvisited node using two local arrays (`visited`, `inStack`) re-initialized per outer loop iteration. Each DFS detects whether there's a cycle reachable from that starting node, but shares no processed results with subsequent calls.

```java
public boolean canFinish(int numCourses, int[][] prerequisites) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] pre : prerequisites) {
        adj.get(pre[1]).add(pre[0]);
    }
    // O(V*(V+E)) — separate DFS per node, no shared processed state
    for (int i = 0; i < numCourses; i++) {
        boolean[] inStack = new boolean[numCourses];
        boolean[] visited = new boolean[numCourses];
        if (hasCycleBrute(i, adj, visited, inStack)) {
            return false;
        }
    }
    return true;
}

private boolean hasCycleBrute(int node, List<List<Integer>> adj,
                               boolean[] visited, boolean[] inStack) {
    visited[node] = true;
    inStack[node] = true;
    for (int neighbor : adj.get(node)) {
        if (!visited[neighbor]) {
            if (hasCycleBrute(neighbor, adj, visited, inStack)) {
                return true;
            }
        } else if (inStack[neighbor]) {
            return true;
        }
    }
    inStack[node] = false;
    return false;
}
```

**Why O(V×(V+E)):** Each of the V outer loop iterations starts a fresh DFS that re-explores the same subgraphs already proven cycle-free in previous iterations.

### 💡 Idea Behind Optimisation

Use **3-color DFS** with a single globally shared `color[]` array across all calls:
- **White (0):** unvisited
- **Gray (1):** currently in DFS stack — on the active path
- **Black (2):** fully processed — proven cycle-free

Once a node is colored Black, skip it in all future calls. Each node is processed exactly once across all outer loop iterations → O(V+E) total. Reaching a Gray node = back edge = cycle.

Two approaches:
1. **DFS with coloring** — 3 states: White (unvisited), Gray (in current path), Black (done). If we reach a Gray node, there's a cycle.
2. **Kahn's BFS (Topological Sort)** — track in-degrees. Remove nodes with in-degree 0. If we can remove all n nodes, no cycle.

### 🎨 Visual — DFS 3-Color Cycle Detection

```
0 → 1 → 2
        ↓
        3 → 1  ← CYCLE (gray node reached)

DFS from 0:
  Visit 0 (white→gray)
  Visit 1 (white→gray)
  Visit 2 (white→gray)
  Visit 3 (white→gray)
  Visit 1 → GRAY → CYCLE DETECTED ✗

States:
  White (0) = not yet visited
  Gray  (1) = currently in DFS stack (on path)
  Black (2) = fully processed, safe

KEY INVARIANT:
  Reaching a GRAY node means we found a back-edge = cycle.
  Reaching a BLACK node is safe — already proven cycle-free.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public boolean canFinish(int numCourses, int[][] prerequisites) {
    // Build adjacency list
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] pre : prerequisites) {
        adj.get(pre[1]).add(pre[0]);
    }

    // 0=unvisited, 1=in-stack (gray), 2=done (black)
    int[] color = new int[numCourses];

    for (int i = 0; i < numCourses; i++) {
        if (color[i] == 0) {
            if (hasCycle(i, adj, color)) {
                return false;
            }
        }
    }
    return true;
}

private boolean hasCycle(int node, List<List<Integer>> adj, int[] color) {
    // Mark as in-stack
    color[node] = 1;
    for (int neighbor : adj.get(node)) {
        if (color[neighbor] == 1) {
            // Back edge found — cycle!
            return true;
        }
        if (color[neighbor] == 0) {
            if (hasCycle(neighbor, adj, color)) {
                return true;
            }
        }
    }
    // Mark as fully processed
    color[node] = 2;
    return false;
}
```

---

### ⏱️ Complexity

| | Complexity |
|---|---|
| Time | O(V + E) |
| Space | O(V + E) |

---

### 🔀 Alternative Solution — Kahn's BFS (Topological Sort)

> Instead of tracking DFS call-stack state (gray/black), Kahn's tracks **in-degree** (how many prerequisites a node still needs). Nodes with in-degree 0 are "ready" — no remaining dependencies. Process them BFS-style, reducing neighbors' in-degrees. If you process all n nodes, no cycle. If you get stuck before that, a cycle blocked the remaining nodes.

```java
import java.util.*;

public boolean canFinish(int numCourses, int[][] prerequisites) {
    // Build adjacency list and compute in-degrees
    List<List<Integer>> adj = new ArrayList<>();
    int[] inDegree = new int[numCourses];

    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] pre : prerequisites) {
        // pre[1] → pre[0]: must take pre[1] before pre[0]
        adj.get(pre[1]).add(pre[0]);
        inDegree[pre[0]]++;
    }

    // Seed queue with all nodes that have no prerequisites
    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++) {
        if (inDegree[i] == 0) {
            queue.offer(i);
        }
    }

    int processed = 0;
    while (!queue.isEmpty()) {
        int course = queue.poll();
        processed++;
        // "Take this course" — reduce in-degree of courses that depend on it
        for (int neighbor : adj.get(course)) {
            inDegree[neighbor]--;
            if (inDegree[neighbor] == 0) {
                queue.offer(neighbor);
            }
        }
    }

    // If we processed all courses, no cycle exists
    return processed == numCourses;
}
```

**Why cycle detection works:** Nodes in a cycle can never reach in-degree 0 — they're all waiting on each other. So `processed < numCourses` means some nodes were permanently stuck → cycle exists.

**Bonus — Topological Order (LC #210):** The order in which nodes are polled from the queue IS the topological order. Just collect them into a result array instead of a counter.

```java
// Returns topological order, or empty array if cycle exists
public int[] findOrder(int numCourses, int[][] prerequisites) {
    List<List<Integer>> adj = new ArrayList<>();
    int[] inDegree = new int[numCourses];
    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] pre : prerequisites) {
        adj.get(pre[1]).add(pre[0]);
        inDegree[pre[0]]++;
    }

    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++) {
        if (inDegree[i] == 0) {
            queue.offer(i);
        }
    }

    int[] order = new int[numCourses];
    int idx = 0;
    while (!queue.isEmpty()) {
        int course = queue.poll();
        order[idx++] = course;
        for (int neighbor : adj.get(course)) {
            if (--inDegree[neighbor] == 0) {
                queue.offer(neighbor);
            }
        }
    }
    return idx == numCourses ? order : new int[0];
}
```

---

### 🔁 Follow-up Questions

**Q1: DFS 3-color vs Kahn's — which to use?**  
Both O(V+E). Kahn's naturally produces topological order in poll sequence. DFS needs reverse-of-finish-order. In an interview, code whichever comes to mind first — both are acceptable.

**Q2: What is the topological order (LC #210)?**  
Kahn's: poll order = topological order. DFS: nodes colored Black in reverse finish order = topological order (reverse the result array).

---

---

## 15. Build a File System — LC #588

**Difficulty:** Medium | **Pattern:** Trie / HashMap tree  
**Confirmed in:** Strong Docusign tag (document folder structure)

---

### 🎯 Problem Statement

Design a file system that supports:
- `createPath(path, value)` — create a new path. Return `true` if parent exists and path is new.
- `get(path)` — return value at path, or -1 if not found.

```
Example:
fs.createPath("/leet", 1)       → true
fs.createPath("/leet/code", 2)  → true
fs.get("/leet/code")            → 2
fs.createPath("/c/d", 1)        → false  (/c doesn't exist)
```

---

### 🧠 Discussion — How to Think About This

This is a **Trie** (prefix tree) where each node is a folder. Or equivalently, use a `HashMap<String, Integer>` where keys are full paths — simpler to implement in an interview.

**HashMap approach:** Parse the path, check parent exists, insert.

### 🎨 Visual — Trie vs HashMap

```
HashMap approach:
  paths = {"/": -1}  (root always exists)

createPath("/leet", 1):
  parent = "/" → exists in map ✓
  "/leet" not in map → insert {"/leet": 1} ✓

createPath("/leet/code", 2):
  parent = "/leet" → exists ✓
  insert {"/leet/code": 2} ✓

createPath("/c/d", 1):
  parent = "/c" → NOT in map ✗ → return false

KEY INVARIANT:
  Parent = path.substring(0, path.lastIndexOf('/'))
  Root "/" is pre-seeded so root-level paths work.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.HashMap;
import java.util.Map;

class FileSystem {
    private final Map<String, Integer> paths;

    public FileSystem() {
        paths = new HashMap<>();
        // Root always exists
        paths.put("", -1);
    }

    public boolean createPath(String path, int value) {
        // Find parent path
        int lastSlash = path.lastIndexOf('/');
        String parent = path.substring(0, lastSlash);

        // Parent must exist, path must not already exist
        if (!paths.containsKey(parent) || paths.containsKey(path)) {
            return false;
        }
        paths.put(path, value);
        return true;
    }

    public int get(String path) {
        return paths.getOrDefault(path, -1);
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| createPath | O(L) — L = path length | O(n×L) |
| get | O(L) | O(1) |

---

### 🔁 Follow-up Questions

**Q1: Why seed root as empty string `""`?**  
A path like `/leet` has `lastIndexOf('/')` = 0, so parent = `""`. Seeding `""` makes root-level path creation work without special-casing.

**Q2: How would you list all files under a directory?**  
With HashMap: iterate all keys, check if they start with `prefix + "/"`. O(n). With a Trie: traverse the subtree. O(subtree size).

---

---

## 16. Longest Substring Without Repeating Characters — LC #3

**Difficulty:** Medium | **Pattern:** Sliding Window, HashMap  
**Confirmed in:** High-frequency Docusign tag

---

### 🎯 Problem Statement

Find the length of the longest substring without repeating characters.

```
Example:
"abcabcbb" → 3  ("abc")
"bbbbb"    → 1  ("b")
"pwwkew"   → 3  ("wke")
```

---

### 🧠 Discussion — How to Think About This

Find the longest substring with no repeated characters.

### 🐌 Brute Force Approach

Check all possible substrings using two nested loops. For each starting index, expand rightward using a HashSet to verify uniqueness. Stop when a duplicate is found. O(n²) time.

```java
public int lengthOfLongestSubstring(String s) {
    int maxLen = 0;
    for (int i = 0; i < s.length(); i++) {
        Set<Character> seen = new HashSet<>();
        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (seen.contains(c)) {
                // Duplicate found — stop expanding this substring
                break;
            }
            seen.add(c);
            maxLen = Math.max(maxLen, j - i + 1);
        }
    }
    return maxLen;
}
```

**Why O(n²):** We start a fresh scan from each of the n positions. Inner loop processes up to n characters per starting position. No work is shared between iterations.

### 💡 Idea Behind Optimisation

Instead of restarting from each position, maintain a sliding window `[left, right]`. Expand `right` one step at a time. When a duplicate is found at `right`, advance `left` past the previous occurrence of that character — in O(1) using a `HashMap<Character, Integer>` storing the last-seen index. The window always contains a valid (no-repeat) substring and we never re-scan already-processed characters → O(n).

Use `HashMap<Character, Integer>` mapping each character to its **last seen index**.

### 🎨 Visual — Window Expansion

```
s = "abcabcbb"
     0123456 7

right=0: 'a' → map={a:0}, len=1
right=1: 'b' → map={a:0,b:1}, len=2
right=2: 'c' → map={a:0,b:1,c:2}, len=3
right=3: 'a' → dup! last 'a' at 0. left=max(0,0+1)=1
         map={a:3,b:1,c:2}, window=[1..3], len=3
right=4: 'b' → dup! last 'b' at 1. left=max(1,1+1)=2
         map={a:3,b:4,c:2}, window=[2..4], len=3
right=5: 'c' → dup! left=max(2,2+1)=3
right=6: 'b' → dup! left=max(3,4+1)=5
right=7: 'b' → dup! left=max(5,6+1)=7
         window=[7..7], len=1

max = 3 ✓

KEY INVARIANT:
  left = max(left, lastSeen[char] + 1)
  The max() ensures left never moves backward even if
  the duplicate was before the current window.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.HashMap;
import java.util.Map;

public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int left = 0;
    int maxLen = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);

        // If duplicate seen within current window, shrink left boundary
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1;
        }

        lastSeen.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

---

### ⏱️ Complexity

| | Complexity |
|---|---|
| Time | O(n) |
| Space | O(min(n, charset)) |

---

### 🔁 Follow-up Questions

**Q1: Why `lastSeen.get(c) >= left` in the condition?**  
Without it, a character seen before the current window would incorrectly shrink `left` backward. The `>= left` check ensures we only act on duplicates inside the current window.

**Q2: Can you do this with a fixed-size array instead of HashMap?**  
Yes — `int[128]` for ASCII, initialized to -1. Slightly faster in practice (no hashing).

---

---

## 17. Meeting Rooms II — LC #253

**Difficulty:** Medium | **Pattern:** Greedy, Min-Heap / Sweep Line  
**Confirmed in:** Strong Docusign tag (scheduling)

---

### 🎯 Problem Statement

Given meeting intervals `[start, end]`, find the **minimum number of conference rooms required**.

```
Example:
[[0,30],[5,10],[15,20]]
Output: 2
  (Meeting 1 overlaps with meetings 2 AND 3 can reuse room after meeting 2 ends)
```

---

### 🧠 Discussion — How to Think About This

Find the minimum number of rooms needed to accommodate all meetings with no conflicts.

### 🐌 Brute Force Approach

For each meeting, count how many other meetings overlap with it — the overlap count + 1 gives the rooms required at that meeting's time. Take the maximum across all meetings. O(n²).

```java
public int minMeetingRooms(int[][] intervals) {
    int maxRooms = 0;
    for (int i = 0; i < intervals.length; i++) {
        int rooms = 1;
        for (int j = 0; j < intervals.length; j++) {
            if (i == j) {
                continue;
            }
            // Two meetings overlap if one starts before the other ends
            if (intervals[j][0] < intervals[i][1] && intervals[j][1] > intervals[i][0]) {
                rooms++;
            }
        }
        maxRooms = Math.max(maxRooms, rooms);
    }
    return maxRooms;
}
```

**Why O(n²):** For each of n meetings, we compare it against all other n meetings to count overlaps. Redundant — we recheck the same pairs multiple times.

### 💡 Idea Behind Optimisation

Sort meetings by start time. Use a **min-heap of end times** that always exposes the room freeing up earliest. For each new meeting, check if the earliest-ending room is free (`heap.peek() <= start`). If yes, reuse it (pop old end, push new end). If no, allocate a new room (push end). One pass = O(n log n). No all-pairs comparisons needed.

Sort meetings by start time. Use a **min-heap of end times** to track when rooms free up. For each new meeting:
- If heap is non-empty and `heap.peek() <= meeting.start` → a room freed up → reuse it (pop and push new end time).
- Otherwise → allocate a new room (just push end time).

Heap size = rooms in use = answer.

### 🎨 Visual — Min-Heap Greedy

```
Meetings sorted: [0,30], [5,10], [15,20]

Process [0,30]: heap empty → new room → heap=[30]
Process [5,10]: heap.peek()=30 > 5 → no free room → new room → heap=[10,30]
Process [15,20]: heap.peek()=10 ≤ 15 → room freed → replace → heap=[20,30]

heap.size() = 2 = answer ✓

KEY INVARIANT:
  Min-heap top = earliest room that becomes free.
  If it frees before new meeting starts → reuse it.
  Otherwise → need a new room.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public int minMeetingRooms(int[][] intervals) {
    if (intervals.length == 0) {
        return 0;
    }
    // Sort by start time
    Arrays.sort(intervals, Comparator.comparingInt(a -> a[0]));

    // Min-heap tracking end times of ongoing meetings
    PriorityQueue<Integer> endTimes = new PriorityQueue<>();

    for (int[] meeting : intervals) {
        int start = meeting[0];
        int end = meeting[1];

        // If earliest-ending room is free before this meeting starts, reuse it
        if (!endTimes.isEmpty() && endTimes.peek() <= start) {
            endTimes.poll();
        }
        // Assign room (new or reused) with this meeting's end time
        endTimes.offer(end);
    }
    // Heap size = number of rooms needed
    return endTimes.size();
}
```

---

### ⏱️ Complexity

| | Complexity |
|---|---|
| Time | O(n log n) — sort + heap ops |
| Space | O(n) |

---

### 🔁 Follow-up Questions

**Q1: Is there an O(n log n) approach that doesn't use a heap?**  
Yes — sweep line. Separate start and end arrays, sort both. Walk with two pointers: when start < end → increment rooms; when end ≤ start → decrement (room freed). Same complexity, slightly different intuition.

**Q2: What if you need to return the actual room assignments?**  
Track which meeting is in which room (label rooms). When reusing, assign the same room label.

---

---

## 18. Clone Graph — LC #133

**Difficulty:** Medium | **Pattern:** BFS/DFS + HashMap  
**Confirmed in:** Pattern-derived from multi-source BFS family

---

### 🎯 Problem Statement

Deep clone a connected undirected graph. Each node has a `val` and a list of `neighbors`. Return the clone of the given starting node.

---

### 🧠 Discussion — How to Think About This

Deep clone a connected undirected graph — create new Node objects with the same values and neighbor links, without sharing any references with the original.

### 🐌 Brute Force Approach

Attempt plain DFS without any visited tracking. For each node, create a clone and recurse into each neighbor. The problem: in a graph with cycles, this loops forever — `clone(A)` recurses into `clone(B)`, which recurses back into `clone(A)`, infinitely.

```java
// ⚠️ BROKEN on cyclic graphs — infinite recursion / stack overflow
public Node cloneGraphBroken(Node node) {
    if (node == null) {
        return null;
    }
    Node clone = new Node(node.val);
    for (Node neighbor : node.neighbors) {
        // If graph has a cycle, this never terminates
        clone.neighbors.add(cloneGraphBroken(neighbor));
    }
    return clone;
}
```

**Why it breaks:** In a graph `A — B` (undirected, so B's neighbors include A), cloning A recurses into B, which recurses into A, which recurses into B... → stack overflow.

### 💡 Idea Behind Optimisation

A `HashMap<Node, Node>` tracks original → clone. Before recursing into a neighbor, check the map: if the neighbor is already cloned, return the existing clone immediately — don't recurse again. This breaks cycles: the second time we encounter A, we find it in the map and return in O(1). Each node is cloned exactly once → O(V+E).

### 🎨 Visual — Clone with HashMap

```
Original:  1 — 2
           |   |
           4 — 3

HashMap maps: original_node → cloned_node

BFS from node 1:
  Create clone(1), add to map
  Neighbors of 1: [2, 4]
    Create clone(2), add to map, link clone(1).neighbors → clone(2)
    Create clone(4), add to map, link clone(1).neighbors → clone(4)
  Neighbors of 2: [1 (mapped), 3]
    1 already in map → link clone(2).neighbors → clone(1)
    Create clone(3), add to map
  ...

KEY INVARIANT:
  HashMap.containsKey(node) → already cloned → use existing clone.
  This prevents infinite loops on cycles.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public Node cloneGraph(Node node) {
    if (node == null) {
        return null;
    }
    Map<Node, Node> cloneMap = new HashMap<>();
    Queue<Node> queue = new LinkedList<>();

    // Create first clone, seed BFS
    cloneMap.put(node, new Node(node.val));
    queue.offer(node);

    while (!queue.isEmpty()) {
        Node original = queue.poll();
        for (Node neighbor : original.neighbors) {
            // Clone neighbor if not already cloned
            if (!cloneMap.containsKey(neighbor)) {
                cloneMap.put(neighbor, new Node(neighbor.val));
                queue.offer(neighbor);
            }
            // Wire clone of current → clone of neighbor
            cloneMap.get(original).neighbors.add(cloneMap.get(neighbor));
        }
    }
    return cloneMap.get(node);
}
```

---

### ⏱️ Complexity

| | Complexity |
|---|---|
| Time | O(V + E) |
| Space | O(V) — HashMap + queue |

---

### 🔁 Follow-up Questions

**Q1: What prevents infinite loops in a cyclic graph?**  
The HashMap — we only add a node to the BFS queue the first time it's seen. Subsequent encounters find it already in the map and skip.

**Q2: How does DFS differ from BFS here?**  
DFS uses the call stack instead of an explicit queue. Both work — same time and space complexity.

---

---

## 19. Rotting Oranges — LC #994

**Difficulty:** Medium | **Pattern:** Multi-source BFS on Grid  
**Confirmed in:** Pattern-derived from friend's Q1 (same multi-source BFS pattern)

---

### 🎯 Problem Statement

Grid where `0` = empty, `1` = fresh orange, `2` = rotten orange. Each minute, rotten oranges rot their 4-directional fresh neighbors. Return minimum minutes until all oranges rot, or `-1` if impossible.

```
Example:
[[2,1,1],[1,1,0],[0,1,1]]
Output: 4
```

---

### 🧠 Discussion — How to Think About This

Simulate rot spreading from rotten oranges to fresh neighbors each minute.

### 🐌 Brute Force Approach

Simulate minute-by-minute. In each iteration, scan the **entire grid** to collect rotten oranges, then rot their fresh neighbors. Repeat until no new changes occur. O(minutes × m × n).

```java
public int orangesRotting(int[][] grid) {
    int m = grid.length;
    int n = grid[0].length;
    int minutes = 0;
    boolean changed = true;
    while (changed) {
        changed = false;
        // Collect all currently rotten positions
        List<int[]> rotten = new ArrayList<>();
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c] == 2) {
                    rotten.add(new int[]{ r, c });
                }
            }
        }
        // Rot their fresh neighbors
        int[][] dirs = { {0,1}, {0,-1}, {1,0}, {-1,0} };
        for (int[] cell : rotten) {
            for (int[] dir : dirs) {
                int nr = cell[0] + dir[0];
                int nc = cell[1] + dir[1];
                if (nr >= 0 && nr < m && nc >= 0 && nc < n && grid[nr][nc] == 1) {
                    grid[nr][nc] = 2;
                    changed = true;
                }
            }
        }
        if (changed) {
            minutes++;
        }
    }
    for (int[] row : grid) {
        for (int cell : row) {
            if (cell == 1) {
                return -1;
            }
        }
    }
    return minutes;
}
```

**Why O(minutes × m×n):** Each minute requires a full grid scan to collect rotten oranges. Worst case: a single chain where rot spreads one cell per minute across m×n cells.

### 💡 Idea Behind Optimisation

**Exactly** multi-source BFS (Problem 2) on a grid. Seed the queue with ALL initially rotten oranges simultaneously (level 0). BFS naturally processes all cells at the same "distance" (same minute) together. Each cell is visited at most once → O(m×n) total, regardless of how many minutes the rot takes. No repeated full-grid scans.

### 🎨 Visual — Multi-source BFS on Grid

```
Initial grid:          After 1 min:         After 2 min:
2 1 1                  2 2 1                2 2 2
1 1 0                  2 1 0                2 2 0
0 1 1                  0 1 1                0 1 1

After 3 min:           After 4 min:
2 2 2                  2 2 2
2 2 0                  2 2 0
0 2 1                  0 2 2   ← all rotted

Sources seeded at t=0: all cells with value 2
BFS wave expands outward simultaneously.

KEY INVARIANT:
  Same as multi-source BFS — all initial rotten oranges are equidistant (time=0) sources.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public int orangesRotting(int[][] grid) {
    int m = grid.length;
    int n = grid[0].length;
    Queue<int[]> queue = new LinkedList<>();
    int freshCount = 0;

    // Seed all rotten oranges, count fresh ones
    for (int r = 0; r < m; r++) {
        for (int c = 0; c < n; c++) {
            if (grid[r][c] == 2) {
                queue.offer(new int[]{ r, c });
            } else if (grid[r][c] == 1) {
                freshCount++;
            }
        }
    }

    // No fresh oranges → done
    if (freshCount == 0) {
        return 0;
    }

    int[][] dirs = { {0,1}, {0,-1}, {1,0}, {-1,0} };
    int minutes = 0;

    while (!queue.isEmpty()) {
        int levelSize = queue.size();
        minutes++;
        for (int i = 0; i < levelSize; i++) {
            int[] cell = queue.poll();
            for (int[] dir : dirs) {
                int nr = cell[0] + dir[0];
                int nc = cell[1] + dir[1];
                if (nr >= 0 && nr < m && nc >= 0 && nc < n && grid[nr][nc] == 1) {
                    grid[nr][nc] = 2;
                    freshCount--;
                    queue.offer(new int[]{ nr, nc });
                }
            }
        }
    }
    return freshCount == 0 ? minutes - 1 : -1;
}
```

> ⚠️ Note: `minutes - 1` because we incremented `minutes` at the start of each level, including the last level that processes cells already counted.

---

### ⏱️ Complexity

| | Complexity |
|---|---|
| Time | O(m × n) |
| Space | O(m × n) |

---

### 🔁 Follow-up Questions

**Q1: How does this map to the Polio delivery problem (Problem 2)?**  
Structurally identical — rotten oranges = distribution centers, fresh oranges = unserved cities, minutes = travel time, grid cells = graph nodes.

**Q2: Why do we track `freshCount` separately?**  
To detect impossibility (isolated fresh orange with no rotten neighbor). After BFS, `freshCount > 0` means some fresh orange was unreachable.

---

---

## 20. Group Anagrams — LC #49

**Difficulty:** Medium | **Pattern:** HashMap with canonical key  
**Confirmed in:** Pattern-derived (string grouping)

---

### 🎯 Problem Statement

Given an array of strings, group anagrams together.

```
Example:
["eat","tea","tan","ate","nat","bat"]
→ [["bat"],["nat","tan"],["ate","eat","tea"]]
```

---

### 🧠 Discussion — How to Think About This

Group strings that are anagrams of each other.

### 🐌 Brute Force Approach

For each string not yet grouped, compare it against all remaining strings: sort both and check equality. Build groups by walking through the array. O(n² × L log L).

```java
public List<List<String>> groupAnagrams(String[] strs) {
    boolean[] grouped = new boolean[strs.length];
    List<List<String>> result = new ArrayList<>();

    for (int i = 0; i < strs.length; i++) {
        if (grouped[i]) {
            continue;
        }
        List<String> group = new ArrayList<>();
        group.add(strs[i]);
        grouped[i] = true;
        String sortedI = sorted(strs[i]);
        // Compare strs[i] against all remaining strings — O(n * L log L)
        for (int j = i + 1; j < strs.length; j++) {
            if (!grouped[j] && sorted(strs[j]).equals(sortedI)) {
                group.add(strs[j]);
                grouped[j] = true;
            }
        }
        result.add(group);
    }
    return result;
}

private String sorted(String s) {
    char[] chars = s.toCharArray();
    Arrays.sort(chars);
    return new String(chars);
}
```

**Why O(n² × L log L):** For each of n strings, we compare it against up to n others. Each comparison sorts a string in O(L log L). Pairs are re-examined redundantly.

### 💡 Idea Behind Optimisation

Instead of comparing all pairs, compute a **canonical key** for each string (its sorted characters). All anagrams produce the same key by definition. Use a `HashMap<key, List<String>>` to bucket words by key. One pass through all n strings → O(n × L log L) total. Zero pair comparisons.

Two strings are anagrams if and only if their **sorted characters are identical**. Sort each word → use the sorted form as a HashMap key → group words by key.

Alternative: Use a **character frequency array** `int[26]` as the key (faster for long strings since sorting is O(L log L) but frequency counting is O(L)).

### 🎨 Visual — Canonical Key Grouping

```
"eat" → sort → "aet"
"tea" → sort → "aet"  ← same key → same group
"tan" → sort → "ant"
"ate" → sort → "aet"  ← same group
"nat" → sort → "ant"  ← same group as tan
"bat" → sort → "abt"

HashMap:
"aet" → ["eat", "tea", "ate"]
"ant" → ["tan", "nat"]
"abt" → ["bat"]

KEY INVARIANT:
  Anagram equivalence class = sorted character string.
  One pass through all words → O(n × L log L) total.
```

---

### 🚀 Optimal Java Solution

```java
import java.util.*;

public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();

    for (String s : strs) {
        // Canonical key: sorted characters
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);

        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(groups.values());
}
```

**Alternative — frequency array key (O(L) instead of O(L log L)):**

```java
public List<List<String>> groupAnagramsFast(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();

    for (String s : strs) {
        int[] freq = new int[26];
        for (char c : s.toCharArray()) {
            freq[c - 'a']++;
        }
        // Build key from frequency array: "a2b1..." style
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 26; i++) {
            if (freq[i] > 0) {
                key.append((char) ('a' + i)).append(freq[i]);
            }
        }
        groups.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(groups.values());
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Sort-based key | O(n × L log L) | O(n × L) |
| Frequency key | O(n × L) | O(n × L) |

n = number of strings, L = average string length

---

### 🔁 Follow-up Questions

**Q1: What if strings contain non-lowercase characters?**  
Use a larger frequency array or a `Map<Character, Integer>` for the key.

**Q2: How would you make the groups deterministic in output order?**  
Sort each group by original order (they're already in insertion order). Sort groups by their first element if needed.

**Q3: What's the trade-off between sort-based and frequency-based keys?**  
Sort: simpler code, O(L log L). Frequency: O(L) but more code. For L ≤ 100 (typical), difference is negligible — go with sort in interview.

---

---

---

## 21. Find Median from Data Stream — LC #295

**Difficulty:** Hard | **Pattern:** Two Heaps  
**Confirmed in:** High-frequency senior-level Design tag (Google, Meta, Amazon, Docusign adjacent)

---

### 🎯 Problem Statement

Design a data structure that supports:
- `addNum(int num)` — Add a number from the data stream.
- `findMedian()` — Return the median of all numbers so far.

```
Example:
addNum(1) → median = 1.0
addNum(2) → median = 1.5
addNum(3) → median = 2.0
```

---

### 🧠 Discussion — How to Think About This

The median splits the dataset into two equal halves. Keep the **lower half** in a max-heap and the **upper half** in a min-heap. The tops of both heaps give the median in O(1).

### 🐌 Brute Force Approach

Store all numbers in an ArrayList. On each `findMedian`, sort the list and return the middle element → O(n log n) per call.

```java
class MedianFinder {
    private final List<Integer> data = new ArrayList<>();

    public void addNum(int num) {
        // O(1) append
        data.add(num);
    }

    public double findMedian() {
        // O(n log n) — sort entire list every time
        Collections.sort(data);
        int n = data.size();
        if (n % 2 == 1) {
            return data.get(n / 2);
        }
        return (data.get(n / 2 - 1) + data.get(n / 2)) / 2.0;
    }
}
```

**Why O(n log n):** Every `findMedian` call re-sorts the full list. With n calls, total cost is O(n² log n).

### 💡 Idea Behind Optimisation

The median only depends on the **tops** of the two sorted halves — not the full sorted order. Maintain:
- `maxHeap` — max-heap of lower half (top = largest in lower half)
- `minHeap` — min-heap of upper half (top = smallest in upper half)

Keep them balanced: `|maxHeap.size - minHeap.size| ≤ 1`. Then `findMedian` is O(1) — just peek at the tops. `addNum` is O(log n) — one heap insert.

### 🎨 Visual — Two Heaps Partition

```
Stream: [1, 3, 2]

After addNum(1):
  push 1 → maxHeap=[1], minHeap=[]
  sizes: 1 vs 0, balanced ✓
  median = maxHeap.top = 1.0

After addNum(3):
  push 3 → maxHeap=[3,1]
  maxHeap.top=3 > minHeap empty → move 3 to minHeap
  maxHeap=[1], minHeap=[3]
  sizes: 1 vs 1, balanced ✓
  median = (1 + 3) / 2 = 2.0

After addNum(2):
  push 2 → maxHeap=[2,1]
  maxHeap.top=2 > minHeap.top=3? No (2 < 3), no swap needed
  sizes: 2 vs 1, maxHeap larger ✓
  median = maxHeap.top = 2.0

Lower half   │  Upper half
 max-heap    │  min-heap
 [2, 1]      │  [3, ...]
   top=2     │   top=3
             ↑
          median = 2.0 (from larger heap)

KEY INVARIANT:
  maxHeap.top ≤ minHeap.top always (lower half ≤ upper half).
  |maxHeap.size - minHeap.size| ≤ 1.
  If sizes equal → median = average of both tops.
  If unequal → median = top of the larger heap.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **addNum:** Always push to `maxHeap` first.
2. **Fix order:** If `maxHeap.top > minHeap.top`, move top of `maxHeap` to `minHeap` (wrong partition).
3. **Rebalance sizes:** If one heap is 2+ larger than the other, move its top to the other heap.
4. **findMedian:** If sizes equal → average of both tops. Else → top of larger heap.

```java
import java.util.*;

class MedianFinder {
    // Step 1: max-heap for lower half, min-heap for upper half
    private final PriorityQueue<Integer> maxHeap;
    private final PriorityQueue<Integer> minHeap;

    public MedianFinder() {
        // Max-heap: reverse natural order
        maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        // Min-heap: natural order (default)
        minHeap = new PriorityQueue<>();
    }

    public void addNum(int num) {
        // Step 1: always push to maxHeap first
        maxHeap.offer(num);

        // Step 2: fix partition order — maxHeap.top must not exceed minHeap.top
        if (!minHeap.isEmpty() && maxHeap.peek() > minHeap.peek()) {
            minHeap.offer(maxHeap.poll());
        }

        // Step 3: rebalance sizes — keep size difference at most 1
        if (maxHeap.size() > minHeap.size() + 1) {
            minHeap.offer(maxHeap.poll());
        } else if (minHeap.size() > maxHeap.size() + 1) {
            maxHeap.offer(minHeap.poll());
        }
    }

    public double findMedian() {
        // Step 4: return median from heap tops
        if (maxHeap.size() == minHeap.size()) {
            return (maxHeap.peek() + minHeap.peek()) / 2.0;
        }
        if (maxHeap.size() > minHeap.size()) {
            return maxHeap.peek();
        }
        return minHeap.peek();
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| addNum | O(log n) | O(n) |
| findMedian | O(1) | O(1) |

---

### 🔁 Follow-up Questions

**Q1: What if all numbers are in [0, 100]?**  
Use a frequency array `int[101]`. Track total count. `findMedian` walks the array to find the middle index(es) → O(100) = O(1) effectively.

**Q2: What if the stream has > 50% numbers from [0, 100]?**  
Hybrid: bucket the common range, use heaps for outliers.

**Q3: Why `maxHeap.peek() > minHeap.peek()` and not `>=`?**  
Equal values are fine in either half — no ordering violation. Using `>` correctly allows duplicates.

---

---

## 22. Implement Trie (Prefix Tree) — LC #208

**Difficulty:** Medium | **Pattern:** Trie  
**Confirmed in:** High-frequency senior-level tag across all major companies

---

### 🎯 Problem Statement

Implement a Trie with:
- `insert(word)` — Insert a word.
- `search(word)` — Return `true` if word exists exactly.
- `startsWith(prefix)` — Return `true` if any inserted word starts with this prefix.

```
Example:
trie.insert("apple");
trie.search("apple")   → true
trie.search("app")     → false
trie.startsWith("app") → true
trie.insert("app");
trie.search("app")     → true
```

---

### 🧠 Discussion — How to Think About This

A Trie (prefix tree — a tree where each path from root to a node spells a prefix) stores words character by character. Each node has up to 26 children (one per letter) and an `isEnd` flag marking complete words.

### 🐌 Brute Force Approach

Store all words in a `HashSet`. `insert` = `set.add`. `search` = `set.contains`. But `startsWith` requires scanning all words for a matching prefix → O(n × L) per call.

```java
class Trie {
    private final Set<String> words = new HashSet<>();

    public void insert(String word) {
        words.add(word);
    }

    public boolean search(String word) {
        return words.contains(word);
    }

    public boolean startsWith(String prefix) {
        // O(n * L) — scan every stored word
        for (String word : words) {
            if (word.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
```

**Why O(n × L):** `startsWith` must check every stored word against the prefix. With n words of length L, each `startsWith` call is O(n × L).

### 💡 Idea Behind Optimisation

Store words by their characters in a tree structure — shared prefixes share nodes. `insert`, `search`, and `startsWith` all traverse at most L nodes (word length). `startsWith` just checks if the path exists — no need to scan all words. O(L) per operation regardless of how many words are stored.

### 🎨 Visual — Trie Structure

```
insert("app"), insert("apple"), insert("apply")

root
└── a
    └── p
        └── p [isEnd=true]  ← "app" ends here
            └── l
                ├── e [isEnd=true]  ← "apple"
                └── y [isEnd=true]  ← "apply"

search("app")     → a→p→p → isEnd=true  ✓
search("ap")      → a→p   → isEnd=false ✗
startsWith("app") → a→p→p → node exists ✓
startsWith("xyz") → x not in root.children → false ✗

KEY INVARIANT:
  Each node = one character, shared across all words with that prefix.
  isEnd=true marks a complete word — NOT just a prefix.
  startsWith succeeds if the path exists; search requires isEnd=true at the last node.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Node structure:** Each node has `children[26]` (one slot per letter a–z) and `isEnd` flag.
2. **insert:** Walk characters, create child nodes as needed, set `isEnd=true` at last node.
3. **search:** Walk characters, return false if any node missing; return `node.isEnd` at end.
4. **startsWith:** Same as search but return `true` if path exists (ignore `isEnd`).

```java
class Trie {
    // Step 1: TrieNode with 26 children and isEnd flag
    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isEnd = false;
    }

    private final TrieNode root;

    public Trie() {
        root = new TrieNode();
    }

    public void insert(String word) {
        // Step 2: walk each character, create nodes as needed
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        node.isEnd = true;
    }

    public boolean search(String word) {
        // Step 3: walk characters; return false if path breaks, check isEnd at end
        TrieNode node = traverse(word);
        return node != null && node.isEnd;
    }

    public boolean startsWith(String prefix) {
        // Step 4: path existence is enough — don't check isEnd
        return traverse(prefix) != null;
    }

    // Helper: walk the trie along the given string, return final node or null
    private TrieNode traverse(String s) {
        TrieNode node = root;
        for (char c : s.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                return null;
            }
            node = node.children[idx];
        }
        return node;
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| insert | O(L) | O(L × 26) per word worst case |
| search | O(L) | O(1) |
| startsWith | O(L) | O(1) |

L = length of word/prefix. Total space: O(n × L × 26) — but shared prefixes reduce actual usage.

---

### 🔁 Follow-up Questions

**Q1: Can you implement `delete(word)`?**  
Walk to the end, set `isEnd=false`. Optionally clean up leaf nodes (null out children with no other words below). Needs careful post-order DFS to avoid deleting shared prefixes.

**Q2: How would you find all words with a given prefix?**  
`traverse(prefix)` to reach the prefix node, then DFS from that node collecting all paths where `isEnd=true`.

**Q3: What if the alphabet is larger (e.g., Unicode)?**  
Replace `children[26]` with `HashMap<Character, TrieNode>`. Slightly more memory overhead per node but handles arbitrary characters.

**Q4: `search("app")` returns false even after `insert("apple")`. Why?**  
Because `insert("apple")` only sets `isEnd=true` at the 'e' node. The 'p' node at depth 3 has `isEnd=false` — it's a prefix, not a complete word. This is exactly what `isEnd` guards against.

---

---

## 23. LFU Cache — LC #460

**Difficulty:** Hard | **Pattern:** HashMap + Frequency Map + minFreq pointer  
**Confirmed in:** FAANG-level hard design — follow-up to LRU Cache (#12 in this file)

---

### 🎯 Problem Statement

Design a Least Frequently Used (LFU) cache with capacity `k`:
- `get(key)` — return value if exists, else -1. Increment key's access frequency.
- `put(key, value)` — insert or update. If at capacity, evict the **least frequently used** entry. Among ties (same frequency), evict the **least recently used**.

Both operations must be O(1) average.

```
Example (capacity=2):
put(1,1), put(2,2)
get(1)       → 1   (freq[1]=2, freq[2]=1)
put(3,3)     → evicts key 2 (freq=1, LRU among freq-1 entries)
get(2)       → -1  (evicted)
get(3)       → 3
```

---

### 🧠 Discussion — How to Think About This

LFU is LRU Cache's harder sibling. You need to track both frequency AND recency simultaneously.

### 🐌 Brute Force Approach

HashMap storing `[value, frequency]` per key. On eviction, scan all entries to find minimum frequency, then among ties find the LRU → O(n) eviction.

```java
class LFUCache {
    private final int capacity;
    private final Map<Integer, int[]> cache = new HashMap<>();  // key → [value, freq]
    private final Map<Integer, Long> lastAccess = new HashMap<>();
    private long time = 0;

    public LFUCache(int capacity) {
        this.capacity = capacity;
    }

    public int get(int key) {
        if (!cache.containsKey(key)) {
            return -1;
        }
        cache.get(key)[1]++;
        lastAccess.put(key, time++);
        return cache.get(key)[0];
    }

    public void put(int key, int value) {
        if (capacity <= 0) {
            return;
        }
        if (cache.containsKey(key)) {
            cache.get(key)[0] = value;
            cache.get(key)[1]++;
            lastAccess.put(key, time++);
            return;
        }
        if (cache.size() == capacity) {
            // O(n) — find LFU key, break ties by LRU (smallest lastAccess)
            int evict = cache.entrySet().stream()
                .min(Comparator.comparingInt((Map.Entry<Integer, int[]> e) -> e.getValue()[1])
                    .thenComparingLong(e -> lastAccess.get(e.getKey())))
                .get().getKey();
            cache.remove(evict);
            lastAccess.remove(evict);
        }
        cache.put(key, new int[]{ value, 1 });
        lastAccess.put(key, time++);
    }
}
```

**Why O(n):** Every eviction scans all entries to find the minimum frequency (and LRU among ties).

### 💡 Idea Behind Optimisation

Three structures eliminate the scan:

1. **`keyMap: HashMap<key, [value, freq]>`** — O(1) key lookup
2. **`freqMap: HashMap<freq, LinkedHashSet<key>>`** — per-frequency bucket of keys in insertion order (LinkedHashSet preserves LRU order within same frequency)
3. **`minFreq: int`** — tracks the current minimum frequency

On `get`/`put`: move key from `freqMap[freq]` to `freqMap[freq+1]`. Evict: first element of `freqMap[minFreq]` (LRU within that frequency). `minFreq` resets to 1 on every new insert.

### 🎨 Visual — Three-Structure LFU

```
capacity=2

put(1,1): keyMap={1:[1,f=1]}, freqMap={1:{1}}, minFreq=1
put(2,2): keyMap={1:[1,f=1],2:[2,f=1]}, freqMap={1:{1,2}}, minFreq=1

get(1):  freq(1) 1→2
  freqMap={1:{2}, 2:{1}}, minFreq=1 (freqMap[1] still has key 2)
  → returns 1

put(3,3): cache full → evict LFU
  freqMap[minFreq=1] = {2} → evict key=2 (first = LRU within freq 1)
  keyMap={1:[1,f=2], 3:[3,f=1]}, freqMap={1:{3}, 2:{1}}, minFreq=1

get(2) → -1 (evicted) ✓
get(3): freq(3) 1→2
  freqMap={1:{}, 2:{1,3}}, minFreq→2 (freqMap[1] now empty)
  → returns 3

KEY INVARIANT:
  LinkedHashSet preserves insertion order = LRU ordering within same frequency.
  Evict: freqMap[minFreq].iterator().next() = least frequent + least recently used.
  minFreq always resets to 1 on new key insert (new keys start at freq=1).
  minFreq can only increment when we remove the last key from freqMap[minFreq].
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Data structures:** `keyMap` (key→[value,freq]), `freqMap` (freq→LinkedHashSet of keys), `minFreq` counter.
2. **get:** Look up key, increment its freq, move it between freq buckets, update `minFreq` if old bucket emptied.
3. **put (existing key):** Same as get but also update value.
4. **put (new key):** If at capacity, evict `freqMap[minFreq].first`. Insert new key at freq=1, reset `minFreq=1`.

```java
import java.util.*;

class LFUCache {
    private final int capacity;
    private int minFreq;
    // Step 1: key → [value, freq]
    private final Map<Integer, int[]> keyMap;
    // Step 1: freq → insertion-ordered set of keys (LinkedHashSet = LRU within freq)
    private final Map<Integer, LinkedHashSet<Integer>> freqMap;

    public LFUCache(int capacity) {
        this.capacity = capacity;
        this.minFreq = 0;
        this.keyMap = new HashMap<>();
        this.freqMap = new HashMap<>();
    }

    public int get(int key) {
        if (!keyMap.containsKey(key)) {
            return -1;
        }
        int[] entry = keyMap.get(key);
        // Step 2: increment frequency and move between buckets
        incrementFreq(key, entry);
        return entry[0];
    }

    public void put(int key, int value) {
        if (capacity <= 0) {
            return;
        }
        if (keyMap.containsKey(key)) {
            // Step 3: update value + increment freq
            int[] entry = keyMap.get(key);
            entry[0] = value;
            incrementFreq(key, entry);
        } else {
            // Step 4: evict if at capacity
            if (keyMap.size() == capacity) {
                LinkedHashSet<Integer> minSet = freqMap.get(minFreq);
                int evict = minSet.iterator().next();
                minSet.remove(evict);
                keyMap.remove(evict);
            }
            // Insert new key at freq=1, reset minFreq to 1
            keyMap.put(key, new int[]{ value, 1 });
            freqMap.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
            minFreq = 1;
        }
    }

    private void incrementFreq(int key, int[] entry) {
        int freq = entry[1];
        entry[1]++;
        // Remove from current freq bucket
        LinkedHashSet<Integer> oldSet = freqMap.get(freq);
        oldSet.remove(key);
        // If old bucket emptied and was the minimum, minFreq increases
        if (oldSet.isEmpty() && freq == minFreq) {
            minFreq++;
        }
        // Add to new freq bucket
        freqMap.computeIfAbsent(freq + 1, k -> new LinkedHashSet<>()).add(key);
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| get | O(1) avg | O(capacity) |
| put | O(1) avg | O(capacity) |

`LinkedHashSet` operations (`add`, `remove`, `iterator().next()`) are all O(1) average.

---

### 🔁 Follow-up Questions

**Q1: Why `LinkedHashSet` and not `LinkedList` for the freq buckets?**  
`LinkedList` would need a back-pointer from the key to its node for O(1) removal — extra complexity. `LinkedHashSet` gives O(1) `add`, O(1) `remove(key)`, and O(1) access to the first element (oldest = LRU). Clean trade-off.

**Q2: What's the difference between LRU and LFU eviction?**  
LRU evicts the **most stale** entry (least recently accessed, regardless of frequency). LFU evicts the **least popular** entry (accessed fewest times total). LFU is better for caches where hot items stay hot (e.g., CDN edge caches).

**Q3: How does `minFreq` stay correct after `get` calls?**  
`get` increments an existing key's freq from `f` to `f+1`. If `freqMap[f]` becomes empty and `f == minFreq`, then `minFreq++`. It can only go up by 1 per `get` call — never jump. On `put` of a new key, `minFreq` resets to 1 unconditionally.

**Q4: What if capacity is 0?**  
Guard with `if (capacity <= 0) return;` in `put` — no data is ever stored.

---

---

## 🧾 Pattern Family Summary

| Pattern | Problems in This Set |
|---|---|
| Multi-source BFS | #2 (Polio), #19 (Rotting Oranges) |
| Design: HashMap + TreeMap | #1 (TTL KV), #11 (Time-Based KV) |
| Design: HashMap + DLL | #12 (LRU Cache) |
| Design: HashMap + ArrayList | #10 (Insert/Delete/GetRandom) |
| Tree DP (post-order pair) | #6 (House Robber III), #8 (Nested Boxes) |
| Circular DP | #5 (House Robber II) |
| Sliding Window | #7 (Moving Average), #16 (Longest Substring) |
| Dutch National Flag | #3 (Sort Colors) |
| 0-1 BFS | #4 (Min Cost Grid) |
| Backtracking | #13 (Combination Sum) |
| DFS Cycle Detection | #14 (Course Schedule) |
| Trie / Path HashMap | #15 (File System) |
| Graph Clone | #18 (Clone Graph) |
| Greedy + Heap | #17 (Meeting Rooms II) |
| Canonical HashMap Key | #20 (Group Anagrams) |
| Pre-order Serialization | #9 (Serialize Tree) |
| Two Heaps | #21 (Find Median from Data Stream) |
| Trie (Prefix Tree) | #22 (Implement Trie) |
| Design: freq map + minFreq | #23 (LFU Cache) |

---

> 🌀 Magic applied with Wibey JetBrains Plugin 🪄
