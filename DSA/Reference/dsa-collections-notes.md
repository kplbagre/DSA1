# Java Collections: Set, HashSet, Map, HashMap — DSA Notes

## 🔹 Set (Interface)

A collection that stores **unique elements** (no duplicates), unordered.

**Common Implementations:** `HashSet`, `LinkedHashSet`, `TreeSet`

---

## 🔹 HashSet

Backed by a **HashMap**, gives **O(1)** average for add/remove/contains, no order guarantee.

```java
Set<Integer> set = new HashSet<>();
```

### Useful Methods

| Method | Description | Time |
| --- | --- | --- |
| `add(e)` | Adds element; returns false if duplicate | O(1) |
| `remove(e)` | Removes element | O(1) |
| `contains(e)` | Checks existence — **most used in DSA** | O(1) |
| `size()` | Number of elements | O(1) |
| `isEmpty()` | Checks if empty | O(1) |
| `clear()` | Removes all | O(n) |
| `iterator()` | For traversal | O(n) |

### DSA Use Cases

- Detect duplicates, cycle detection, visited nodes in graphs/BFS/DFS, two-sum lookup.

### Common DSA Patterns

**1. Array → Set (for O(1) lookup)**

> Convert an array into a Set so any "does this element exist?" check becomes O(1) instead of scanning the array each time. Useful whenever you need repeated membership tests.

```java
Set<Integer> set = new HashSet<>();
for (int n : nums) set.add(n);
// or
Set<Integer> set = Arrays.stream(nums).boxed().collect(Collectors.toSet());
```

**2. Detect Duplicates in Array**

> While iterating, try to add each element — if `add()` returns false, the element already exists. Avoids needing a nested loop for duplicate detection.

```java
Set<Integer> seen = new HashSet<>();
for (int n : nums) {
    if (!seen.add(n)) return true;  // add() returns false if duplicate
}
```

**3. Two Sum (Complement Check)**

> For each number, check if its complement (`target - n`) was already seen. Reduces the brute-force O(n²) pair search to a single O(n) pass.

```java
Set<Integer> seen = new HashSet<>();
for (int n : nums) {
    if (seen.contains(target - n)) return true;
    seen.add(n);
}
```

**4. Intersection / Union of Two Arrays**

> Use built-in set operations to combine or compare two collections without writing manual loops. Clean one-liners for common set algebra.

```java
Set<Integer> a = new HashSet<>(List.of(1,2,3));
a.retainAll(List.of(2,3,4));   // intersection → {2,3}
a.addAll(List.of(5,6));        // union
a.removeAll(List.of(2));       // difference
```

**5. Visited Nodes (Graph BFS/DFS)**

> Track which nodes have been processed to avoid revisiting them and getting stuck in cycles. The `add()` return value lets you check-and-mark in one step.

```java
Set<Integer> visited = new HashSet<>();
if (visited.add(node)) { /* process only if newly added */ }
```

---

## 🔹 TreeSet (Bonus)

Sorted Set backed by **Red-Black Tree**, all ops in **O(log n)**.

Useful: `first()`, `last()`, `floor(e)`, `ceiling(e)`, `higher(e)`, `lower(e)`.

---

## 🔹 Map (Interface)

Stores **key-value pairs**, keys are unique.

**Common Implementations:** `HashMap`, `LinkedHashMap`, `TreeMap`

---

## 🔹 HashMap

Backed by **hash table (array + LinkedList/Tree)**, average **O(1)** for put/get/remove.

```java
Map<String, Integer> map = new HashMap<>();
```

### Useful Methods

| Method | Description | Time |
| --- | --- | --- |
| `put(k, v)` | Inserts/updates pair | O(1) |
| `get(k)` | Returns value or null | O(1) |
| `getOrDefault(k, def)` | Returns def if absent — **DSA favorite** | O(1) |
| `containsKey(k)` | Key exists? | O(1) |
| `containsValue(v)` | Value exists? | O(n) |
| `remove(k)` | Removes pair | O(1) |
| `putIfAbsent(k, v)` | Insert only if missing | O(1) |
| `merge(k, v, fn)` | Combine values — **great for frequency maps** | O(1) |
| `compute(k, fn)` | Recompute value for key | O(1) |
| `computeIfAbsent(k, fn)` | Insert computed value if missing — **great for Map of List** | O(1) |
| `keySet()` | All keys | O(n) |
| `values()` | All values | O(n) |
| `entrySet()` | All key-value pairs (best for iteration) | O(n) |

### Common DSA Patterns

**1. Frequency Map from Array / String** ⭐

> Count how many times each element appears by mapping `element → count`. The backbone of problems involving anagrams, character counts, majority elements, top-K, etc.

```java
Map<Integer, Integer> freq = new HashMap<>();
for (int n : nums) freq.merge(n, 1, Integer::sum);
// or
for (int n : nums) freq.put(n, freq.getOrDefault(n, 0) + 1);
```

**2. Two Sum (with Index)**

> Same complement idea as the Set version, but store the **index** as the value so you can return positions, not just confirm existence. One-pass O(n) solution.

```java
Map<Integer, Integer> map = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    if (map.containsKey(target - nums[i]))
        return new int[]{map.get(target - nums[i]), i};
    map.put(nums[i], i);
}
```

**3. Group Anagrams (Map of List)**

> Use a canonical form (sorted string) as the key to bucket similar items together. `computeIfAbsent` lazily initializes the list only when a new key appears.

```java
Map<String, List<String>> groups = new HashMap<>();
for (String s : strs) {
    char[] c = s.toCharArray(); Arrays.sort(c);
    groups.computeIfAbsent(new String(c), k -> new ArrayList<>()).add(s);
}
```

**4. Subarray Sum Equals K (Prefix Sum + Map)**

> Store running prefix sums and their counts; for each new sum, check if `sum - k` was seen before — that means a subarray summing to `k` ends here. Converts an O(n²) problem to O(n).

```java
Map<Integer, Integer> prefix = new HashMap<>();
prefix.put(0, 1);
int sum = 0, count = 0;
for (int n : nums) {
    sum += n;
    count += prefix.getOrDefault(sum - k, 0);
    prefix.merge(sum, 1, Integer::sum);
}
```

**5. First Non-Repeating Character**

> Build a frequency map that **preserves insertion order** using `LinkedHashMap`, then iterate to find the first entry with count 1. Order matters here — a regular HashMap won't work.

```java
Map<Character, Integer> count = new LinkedHashMap<>();  // preserves order
for (char c : s.toCharArray()) count.merge(c, 1, Integer::sum);
for (var e : count.entrySet()) if (e.getValue() == 1) return e.getKey();
```

**6. Sliding Window Character Count**

> Maintain a live count of characters inside the current window — increment when expanding, decrement when shrinking, and remove keys at zero to keep the map clean. Common in "longest substring with K distinct" type problems.

```java
Map<Character, Integer> window = new HashMap<>();
window.merge(c, 1, Integer::sum);              // expand
if (window.merge(c, -1, Integer::sum) == 0)    // shrink + cleanup
    window.remove(c);
```

---

## 🔹 TreeMap (Bonus)

Sorted Map backed by **Red-Black Tree**, **O(log n)** for all ops.

Useful: `firstKey()`, `lastKey()`, `floorKey(k)`, `ceilingKey(k)`, `subMap()`, `headMap()`, `tailMap()`.

### Common DSA Patterns

**1. Closest / Floor / Ceiling Lookup**

> Find the nearest key smaller or larger than a given value in O(log n). Perfect for problems like "find the closest meeting time" or "next greater element with sorted history".

```java
TreeMap<Integer, Integer> tm = new TreeMap<>();
tm.floorKey(x);    // largest key ≤ x
tm.ceilingKey(x);  // smallest key ≥ x
```

**2. Range Sum / Range Count**

> Extract all keys within a given `[low, high]` range as a view of the map. Useful for interval-based queries without rescanning the whole structure.

```java
tm.subMap(low, true, high, true);  // inclusive range
```

---

## 🔹 LinkedHashMap / LinkedHashSet

Maintains **insertion order**, useful for **LRU Cache** problems.

**LRU Cache (1-liner)**

> Enable access-order mode (`true` flag) so most recently accessed entries move to the end, and override `removeEldestEntry` to auto-evict the oldest once capacity is exceeded. Full LRU in 3 lines.

```java
Map<Integer, Integer> lru = new LinkedHashMap<>(cap, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry e) { return size() > cap; }
};
```

---

## ⚡ Quick DSA Cheat Sheet

| Need | Use |
| --- | --- |
| Unique elements + fast lookup | `HashSet` |
| Sorted unique elements | `TreeSet` |
| Key-value fast access | `HashMap` |
| Sorted key-value | `TreeMap` |
| Maintain insertion order | `LinkedHashMap` / `LinkedHashSet` |
| Frequency counting | `HashMap` + `merge` / `getOrDefault` |
| Floor/ceiling queries | `TreeMap` / `TreeSet` |

---

## ⚡ Pattern Cheat Sheet

| Problem Type | Pattern |
| --- | --- |
| Frequency counting | `map.merge(k, 1, Integer::sum)` |
| Complement lookup (Two Sum) | `HashSet` / `HashMap` |
| Group by key | `computeIfAbsent(k, x -> new ArrayList<>())` |
| Prefix sum problems | `HashMap<sum, count>` |
| Visited tracking | `HashSet.add()` returns false if seen |
| Order-preserving freq | `LinkedHashMap` |
| Range / floor / ceil | `TreeMap` |

---

## 🔑 Iteration Patterns

```java
// HashMap
for (Map.Entry<K,V> e : map.entrySet()) {
    e.getKey(); e.getValue();
}

// HashSet
for (T x : set) { ... }
```

---

## ⚠️ Gotchas

- `HashMap` allows **one null key**, `HashSet` allows **one null**.
- `TreeMap` / `TreeSet` **don't allow null keys**.
- Custom objects: override **`equals()` + `hashCode()`** before using in Set/Map.

---

Save this as `dsa-collections-notes.md` and you're set for interviews! 🚀
