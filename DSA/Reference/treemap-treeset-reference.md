# TreeMap & TreeSet — Reference

> **When to reach for these:** Any time you need a sorted collection with O(log n) insert/delete/search AND range queries (`floor`, `ceiling`, `headMap`, `tailMap`). If you don't need ordering, use `HashMap`/`HashSet` instead — they're O(1) vs O(log n).

---

## 🧠 Mental Model — What's Actually Happening Inside

Both `TreeMap` and `TreeSet` are backed by a **Red-Black Tree** (a self-balancing BST — a binary search tree that automatically rebalances itself after every insert/delete so no path is more than 2× longer than any other, keeping height at O(log n) always).

```
Red-Black Tree backing TreeMap {1→"a", 3→"c", 5→"e", 7→"g", 9→"i"}

                5 (BLACK)
               / \
          3 (RED)   7 (RED)
          /   \     /   \
      1(BLK) 4(BLK) 6(BLK) 9(BLK)

KEY INVARIANT:
  Keys are always sorted left < node < right (BST property).
  Tree stays balanced after every insert/delete (Red-Black rules).
  Height = O(log n) always → all operations O(log n) guaranteed.
  In-order traversal of this tree = [1, 3, 4, 5, 6, 7, 9] (sorted).
```

**Why this matters for you:**
- Every `put`, `get`, `remove` = one BST traversal = O(log n)
- `floorKey(6)` = "largest key ≤ 6" = walk the tree = O(log n)
- `keySet()` iteration = in-order traversal = always sorted

> **Mental hook:** `TreeMap` is a `HashMap` that keeps its keys sorted. `TreeSet` is a `TreeMap` where you only care about keys (values are ignored). Same Red-Black tree under both.

---

## 🔹 TreeMap

**Backing structure:** Red-Black Tree | **All ops:** O(log n)

```java
// Creation
TreeMap<Integer, String> map = new TreeMap<>();                      // natural order (ascending)
TreeMap<Integer, String> desc = new TreeMap<>(Comparator.reverseOrder()); // descending
TreeMap<String, Integer> custom = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // custom

// Basic ops
map.put(3, "three");
map.get(3);           // "three"
map.remove(3);
map.containsKey(3);   // false
map.size();
```

### Useful Methods

| Method | Description | Time |
|---|---|---|
| `put(k, v)` | Insert / update | O(log n) |
| `get(k)` | Exact lookup | O(log n) |
| `remove(k)` | Delete by key | O(log n) |
| `containsKey(k)` | Key existence | O(log n) |
| `firstKey()` | Smallest key | O(log n) |
| `lastKey()` | Largest key | O(log n) |
| `floorKey(k)` | Largest key **≤** k (or null) | O(log n) |
| `ceilingKey(k)` | Smallest key **≥** k (or null) | O(log n) |
| `lowerKey(k)` | Largest key **strictly <** k | O(log n) |
| `higherKey(k)` | Smallest key **strictly >** k | O(log n) |
| `headMap(toKey)` | All keys **< toKey** (exclusive) | O(log n) |
| `tailMap(fromKey)` | All keys **≥ fromKey** (inclusive) | O(log n) |
| `subMap(from, to)` | Keys in **[from, to)** | O(log n) |
| `pollFirstEntry()` | Remove + return smallest entry | O(log n) |
| `pollLastEntry()` | Remove + return largest entry | O(log n) |

### 🎨 Visual — Floor / Ceiling / Lower / Higher

```
TreeMap keys: [1, 3, 5, 7, 9]

Query         Result   Explanation
floorKey(5)   → 5      largest key ≤ 5 (exact match counts)
floorKey(4)   → 3      largest key ≤ 4 (no 4 → take 3)
floorKey(0)   → null   no key ≤ 0

ceilingKey(5) → 5      smallest key ≥ 5 (exact match counts)
ceilingKey(4) → 5      smallest key ≥ 4 (no 4 → take 5)
ceilingKey(10)→ null   no key ≥ 10

lowerKey(5)   → 3      strictly < 5 (excludes 5 itself)
higherKey(5)  → 7      strictly > 5 (excludes 5 itself)

Memory trick:
  floor   = ≤ k  (like floor(3.7) = 3, goes DOWN)
  ceiling = ≥ k  (like ceil(3.2) = 4, goes UP)
  lower   = < k  (strictly below, excludes k)
  higher  = > k  (strictly above, excludes k)
```

### DSA Use Cases

- **Time-based floor query** — "find the value at or before timestamp t" → `floorKey(t)`
- **Scheduling / event processing** — sorted by time, process next event → `pollFirstEntry()`
- **Range count / range sum** — count keys in [lo, hi] → `subMap(lo, true, hi, true).size()`

### Common DSA Patterns

**1. Floor Query (Time-Based KV, Versioned Data)** ⭐

> You have timestamped values and need the most recent one at or before a given time. TreeMap keeps entries sorted by timestamp so `floorKey` finds the answer in O(log n).

```java
TreeMap<Integer, String> tsMap = new TreeMap<>();
tsMap.put(1, "v1");
tsMap.put(4, "v4");
tsMap.put(7, "v7");

// Find value at or before timestamp 5
Integer floorTs = tsMap.floorKey(5);
if (floorTs != null) {
    String val = tsMap.get(floorTs);  // "v4"
}
```

**🏷️ Example problems:** Time-Based KV Store (LC #981), Stock Price Fluctuation (LC #2034)

---

**2. Ordered Event Processing / Next Deadline** ⭐

> Process events in time order without manually sorting. TreeMap keeps them sorted; `pollFirstEntry()` always gives the earliest unprocessed event in O(log n).

```java
TreeMap<Integer, String> events = new TreeMap<>();
events.put(100, "signup");
events.put(50, "login");
events.put(200, "logout");

while (!events.isEmpty()) {
    Map.Entry<Integer, String> next = events.pollFirstEntry();
    int time = next.getKey();
    String action = next.getValue();
    // processes: login(50), signup(100), logout(200)
}
```

**🏷️ Example problems:** Meeting Scheduler, TTL KV Store expiry processing

---

**3. Sliding Window with Sorted Order (Count elements in range)** ⭐

> When you need to count or query elements in a sorted window. `subMap` gives a live view of keys in [lo, hi] — `.size()` is O(log n).

```java
TreeMap<Integer, Integer> freq = new TreeMap<>();

// Add element to window
private void add(int val) {
    freq.merge(val, 1, Integer::sum);
}

// Remove element from window
private void remove(int val) {
    freq.merge(val, -1, Integer::sum);
    if (freq.get(val) == 0) {
        freq.remove(val);
    }
}

// Count elements in range [lo, hi]
private int countInRange(int lo, int hi) {
    // subMap(lo, true, hi, true) = keys in [lo, hi] inclusive
    return freq.subMap(lo, true, hi, true)
               .values()
               .stream()
               .mapToInt(Integer::intValue)
               .sum();
}
```

**🏷️ Example problems:** Contains Duplicate III (LC #220), Sliding Window problems with range queries

---

**4. TreeMap as Ordered Frequency Map (Top-K, Sort-by-count)**

> Group items by frequency in sorted order. Outer TreeMap sorts by frequency; inner structure holds the items at each frequency.

```java
TreeMap<Integer, List<String>> freqToWords = new TreeMap<>();

private void addWord(String word, Map<String, Integer> wordFreq) {
    int freq = wordFreq.merge(word, 1, Integer::sum);
    // Remove from old freq bucket
    freqToWords.computeIfAbsent(freq - 1, k -> new ArrayList<>()).remove(word);
    // Add to new freq bucket
    freqToWords.computeIfAbsent(freq, k -> new ArrayList<>()).add(word);
}

// Get top-1 most frequent word
String topWord = freqToWords.lastEntry().getValue().get(0);
```

**🏷️ Example problems:** Top K Frequent Words (LC #692), LFU Cache (LC #460)

---

### Iteration Patterns

```java
TreeMap<Integer, String> map = new TreeMap<>();
map.put(1, "a");
map.put(3, "c");
map.put(5, "e");

// Ascending (natural)
for (Map.Entry<Integer, String> e : map.entrySet()) {
    System.out.println(e.getKey() + " → " + e.getValue());
}

// Descending
for (Map.Entry<Integer, String> e : map.descendingMap().entrySet()) {
    System.out.println(e.getKey() + " → " + e.getValue());
}

// Keys only (ascending)
for (int key : map.keySet()) {
    System.out.println(key);
}

// Range iteration [2, 4]
for (Map.Entry<Integer, String> e : map.subMap(2, true, 4, true).entrySet()) {
    System.out.println(e.getKey());  // prints 3
}
```

---

## 🔹 TreeSet

**Backing structure:** TreeMap internally (TreeSet is just TreeMap where value = dummy object) | **All ops:** O(log n)

> `TreeSet` = sorted `HashSet`. It gives you all the ordering methods of `TreeMap` but for individual elements (no key→value pairs).

```java
// Creation
TreeSet<Integer> set = new TreeSet<>();                          // natural order ascending
TreeSet<Integer> desc = new TreeSet<>(Comparator.reverseOrder()); // descending
TreeSet<String> ci = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

// Basic ops
set.add(5);
set.remove(5);
set.contains(5);
set.size();
```

### Useful Methods

| Method | Description | Time |
|---|---|---|
| `add(e)` | Insert element | O(log n) |
| `remove(e)` | Delete element | O(log n) |
| `contains(e)` | Existence check | O(log n) |
| `first()` | Smallest element | O(log n) |
| `last()` | Largest element | O(log n) |
| `floor(e)` | Largest element **≤** e | O(log n) |
| `ceiling(e)` | Smallest element **≥** e | O(log n) |
| `lower(e)` | Largest element **strictly <** e | O(log n) |
| `higher(e)` | Smallest element **strictly >** e | O(log n) |
| `headSet(toEl)` | All elements **< toEl** | O(log n) |
| `tailSet(fromEl)` | All elements **≥ fromEl** | O(log n) |
| `subSet(from, to)` | Elements in **[from, to)** | O(log n) |
| `pollFirst()` | Remove + return smallest | O(log n) |
| `pollLast()` | Remove + return largest | O(log n) |

### 🎨 Visual — TreeSet vs HashSet

```
HashSet<Integer> {5, 1, 9, 3}
  Iteration order: unpredictable (e.g., 1, 9, 3, 5 — depends on hash)
  floor(4): NOT AVAILABLE — HashSet has no ordering concept

TreeSet<Integer> {5, 1, 9, 3}
  Internal RB-tree:       3
                         / \
                        1   5
                             \
                              9
  Iteration order: always 1, 3, 5, 9  (sorted)
  floor(4): → 3  (largest element ≤ 4)
  ceiling(4): → 5 (smallest element ≥ 4)

KEY INVARIANT:
  TreeSet = sorted, ordered, no duplicates.
  HashSet = unordered, O(1) ops, no duplicates.
  Use TreeSet only when you need floor/ceiling/range queries.
```

### DSA Use Cases

- **Closest value in a set** — `floor(x)` or `ceiling(x)` for nearest neighbor queries
- **Sliding window deduplication** with ordering — maintain sorted unique elements in a window
- **Sweep line / event set** — insert/remove events by coordinate, query neighbors

### Common DSA Patterns

**1. Nearest Neighbor (Closest in Set)** ⭐

> Given a set of numbers, find the closest one to a target. Check both `floor` (closest below) and `ceiling` (closest above), take the nearer one.

```java
TreeSet<Integer> set = new TreeSet<>();
set.add(1);
set.add(5);
set.add(9);

int target = 4;
Integer lo = set.floor(target);    // 1 (largest ≤ 4)
Integer hi = set.ceiling(target);  // 5 (smallest ≥ 4)

int closest;
if (lo == null) {
    closest = hi;
} else if (hi == null) {
    closest = lo;
} else {
    closest = (target - lo <= hi - target) ? lo : hi;
}
// closest = 5
```

**🏷️ Example problems:** Contains Duplicate III (LC #220), Find K Closest Elements

---

**2. Sliding Window with Sorted Elements** ⭐

> Maintain a sorted view of a sliding window. Use TreeSet (or TreeMap for duplicates) to insert/remove elements in O(log n) and query the sorted order at each step.

```java
// Sliding window of size k — check if any two elements differ by ≤ t
public boolean containsNearbyAlmostDuplicate(int[] nums, int k, int t) {
    TreeSet<Long> window = new TreeSet<>();
    for (int i = 0; i < nums.length; i++) {
        long val = (long) nums[i];
        // Floor: largest value ≤ val in window
        Long lo = window.floor(val);
        if (lo != null && val - lo <= t) {
            return true;
        }
        // Ceiling: smallest value ≥ val in window
        Long hi = window.ceiling(val);
        if (hi != null && hi - val <= t) {
            return true;
        }
        window.add(val);
        // Maintain window size k
        if (window.size() > k) {
            window.remove((long) nums[i - k]);
        }
    }
    return false;
}
```

**🏷️ Example problems:** Contains Duplicate III (LC #220)

---

**3. Sweep Line — Coordinate Events**

> Process geometric events (rectangle areas, intervals) in sorted coordinate order. TreeSet keeps events sorted so you always process the next coordinate in O(log n).

```java
TreeSet<int[]> events = new TreeSet<>(Comparator.comparingInt(a -> a[0]));
events.add(new int[]{ 1, 10 });  // [start, end]
events.add(new int[]{ 5, 15 });
events.add(new int[]{ 3, 8 });

// Process in coordinate order
while (!events.isEmpty()) {
    int[] e = events.pollFirst();  // always gets the smallest start
    // process e
}
```

**🏷️ Example problems:** Rectangle Area II (LC #850), Number of Visible People in a Queue (LC #1944)

---

### Iteration Patterns

```java
TreeSet<Integer> set = new TreeSet<>();
set.add(1);
set.add(3);
set.add(5);

// Ascending
for (int x : set) {
    System.out.println(x);  // 1, 3, 5
}

// Descending
for (int x : set.descendingSet()) {
    System.out.println(x);  // 5, 3, 1
}

// Range [2, 4]
for (int x : set.subSet(2, true, 4, true)) {
    System.out.println(x);  // 3
}
```

---

## ⚠️ Gotchas — Silent Bug Hall of Fame

**1. `floor`/`ceiling` return null — never assume non-null**
```java
// ❌ NullPointerException if no key ≤ 5 exists
int result = map.floorKey(5);  // unboxing null → NPE

// ✅ Always null-check
Integer floor = map.floorKey(5);
if (floor != null) {
    String val = map.get(floor);
}
```

**2. `subMap` is exclusive on the upper bound by default**
```java
// ❌ This excludes key=10 (upper bound is exclusive)
map.subMap(1, 10);  // [1, 10) — key 10 NOT included

// ✅ Use the 4-argument form to control inclusivity
map.subMap(1, true, 10, true);  // [1, 10] — both inclusive
```

**3. TreeSet with duplicates — use TreeMap with frequency instead**
```java
// ❌ TreeSet silently drops duplicates
TreeSet<Integer> set = new TreeSet<>();
set.add(3);
set.add(3);
set.size();  // 1, not 2!

// ✅ TreeMap<val, count> handles duplicates
TreeMap<Integer, Integer> freq = new TreeMap<>();
freq.merge(3, 1, Integer::sum);
freq.merge(3, 1, Integer::sum);
freq.get(3);  // 2 ✓
```

**4. Custom comparator must be consistent with `equals`**
```java
// ❌ Comparator that returns 0 for "equal" strings — TreeSet treats them as duplicates
TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
set.add("hello");
set.add("HELLO");
set.size();  // 1 — HELLO treated as duplicate of hello!

// Know this: TreeSet uses comparator.compare(a,b)==0 as equality, NOT .equals()
```

**5. `headMap` / `tailMap` return live views — mutations affect original**
```java
TreeMap<Integer, String> map = new TreeMap<>();
map.put(1, "a");
map.put(3, "c");
map.put(5, "e");

// This is a LIVE VIEW, not a copy
SortedMap<Integer, String> view = map.headMap(4);
view.remove(1);  // removes from the original map!
map.size();  // 2, not 3 — key 1 is gone
```

---

## ⚡ Quick Cheat Sheet

### When to use what

| Need | Use |
|---|---|
| Sorted map + floor/ceiling/range | `TreeMap` |
| Sorted set + floor/ceiling/range | `TreeSet` |
| O(1) map, no ordering needed | `HashMap` |
| O(1) set, no ordering needed | `HashSet` |
| Sorted map with duplicate keys | `TreeMap<K, List<V>>` or `TreeMap<K, Integer>` (freq) |
| Process elements in sorted order (queue) | `TreeMap.pollFirstEntry()` or `TreeSet.pollFirst()` |

### floor vs ceiling vs lower vs higher — one-liner

| Method | Returns | Includes k? |
|---|---|---|
| `floor(k)` | Largest ≤ k | ✅ yes |
| `ceiling(k)` | Smallest ≥ k | ✅ yes |
| `lower(k)` | Largest < k | ❌ no |
| `higher(k)` | Smallest > k | ❌ no |

### Complexity at a glance

| Operation | TreeMap/TreeSet | HashMap/HashSet |
|---|---|---|
| insert / put | O(log n) | O(1) avg |
| delete / remove | O(log n) | O(1) avg |
| search / get | O(log n) | O(1) avg |
| floor / ceiling | O(log n) | ❌ not available |
| min / max | O(log n) | ❌ not available |
| range query | O(log n + result) | ❌ not available |
| in-order iteration | O(n) sorted | O(n) random order |

---

> 🔄 **Changelog**
>
> | Date | Change |
> |---|---|
> | Jul 2026 | Created — TreeMap + TreeSet reference with Red-Black tree mental model, patterns, gotchas |
