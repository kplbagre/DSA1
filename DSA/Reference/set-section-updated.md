## **Set (Interface)**

A collection that stores **unique elements** (no duplicates), unordered.

**Common Implementations:** `HashSet`, `LinkedHashSet`, `TreeSet`

---

## **🔹 HashSet**

Backed by a **HashMap**, gives **O(1)** average for add/remove/contains, no order guarantee.

```java
Set<Integer> set = new HashSet<>();
```

### **Useful Methods**

| **Method** | **Description** | **Time** |
| --- | --- | --- |
| `add(e)` | Adds element; returns false if duplicate | O(1) |
| `remove(e)` | Removes element | O(1) |
| `contains(e)` | Checks existence — **most used in DSA** | O(1) |
| `size()` | Number of elements | O(1) |
| `isEmpty()` | Checks if empty | O(1) |
| `clear()` | Removes all | O(n) |
| `iterator()` | For traversal | O(n) |
| `addAll(c)` | Union with collection | O(n) |
| `retainAll(c)` | Intersection with collection | O(n) |
| `removeAll(c)` | Difference with collection | O(n) |

### **DSA Use Cases**

- Detect duplicates, cycle detection, visited nodes in graphs/BFS/DFS, two-sum lookup, longest consecutive sequence, sliding window deduplication.

---

### **Common DSA Patterns**

---

**1. Array → Set (for O(1) lookup)**

> Convert an array into a Set so any "does this element exist?" check becomes O(1) instead of scanning the array each time. Useful whenever you need repeated membership tests.

```java
Set<Integer> set = new HashSet<>();
for (int n : nums) {
    set.add(n);
}

// One-liner using streams
Set<Integer> set2 = Arrays.stream(nums).boxed().collect(Collectors.toSet());
```

**🏷️ Example problems:** Any problem where you need repeated `contains()` checks on an array — preprocessing step for many algorithms.

---

**2. Detect Duplicates in Array**

> While iterating, try to add each element — if `add()` returns false, the element already exists. Avoids needing a nested loop for duplicate detection.

```java
Set<Integer> seen = new HashSet<>();
for (int n : nums) {
    if (!seen.add(n)) {
        return true; // duplicate found
    }
}
return false;
```

**🏷️ Example problems:** Contains Duplicate (LC 217), Happy Number (LC 202 — variation, see #7), Find the Duplicate Number (LC 287 — though Floyd's is preferred)

---

**3. Two Sum (Complement Check)**

> For each number, check if its complement (`target - n`) was already seen. Reduces the brute-force O(n²) pair search to a single O(n) pass.

```java
Set<Integer> seen = new HashSet<>();
for (int n : nums) {
    if (seen.contains(target - n)) {
        return true;
    }
    seen.add(n);
}
return false;
```

**🏷️ Example problems:** Two Sum (boolean variant), 3Sum (LC 15 — combined with sort + two pointers), Pairs With Specific Difference (LC 532 variant)

---

**4. Intersection / Union / Difference of Two Collections**

> Use built-in set operations to combine or compare two collections without writing manual loops. Clean one-liners for common set algebra.

```java
Set<Integer> a = new HashSet<>(List.of(1, 2, 3));

a.retainAll(List.of(2, 3, 4)); // intersection → {2, 3}
a.addAll(List.of(5, 6));       // union
a.removeAll(List.of(2));       // difference

// Non-mutating intersection (preserve original)
Set<Integer> intersection = new HashSet<>(a);
intersection.retainAll(b);
```

**🏷️ Example problems:** Intersection of Two Arrays (LC 349), Intersection of Two Arrays II (LC 350 — needs frequency, use HashMap instead), Find Common Characters (LC 1002)

---

**5. Visited Nodes (Graph BFS/DFS)**

> Track which nodes have been processed to avoid revisiting them and getting stuck in cycles. The `add()` return value lets you check-and-mark in one step.

```java
Set<Integer> visited = new HashSet<>();
Queue<Integer> queue = new ArrayDeque<>();
queue.offer(start);
visited.add(start);

while (!queue.isEmpty()) {
    int node = queue.poll();
    for (int neighbor : graph.get(node)) {
        if (visited.add(neighbor)) { // add returns false if already visited
            queue.offer(neighbor);
        }
    }
}
```

**🏷️ Example problems:** Number of Islands (LC 200 — though boolean grid is also common), Clone Graph (LC 133), Word Ladder (LC 127), Course Schedule (LC 207), Pacific Atlantic Water Flow (LC 417)

---

**6. Longest Consecutive Sequence** ⭐

> Put all numbers in a Set. For each number, only start counting if it's the **start of a sequence** (i.e., `n - 1` is NOT in the set). Then keep extending while `n + 1` is in the set. Achieves O(n) instead of O(n log n) sort approach.

```java
Set<Integer> set = new HashSet<>();
for (int n : nums) {
    set.add(n);
}

int best = 0;
for (int n : set) {
    // Only start counting from sequence starts
    if (!set.contains(n - 1)) {
        int curr = n;
        int len = 1;
        while (set.contains(curr + 1)) {
            curr++;
            len++;
        }
        best = Math.max(best, len);
    }
}
return best;
```

**🏷️ Example problems:** Longest Consecutive Sequence (LC 128) — *the* HashSet flagship problem.

> **Why O(n)?** Each number is visited at most twice — once as a sequence start and once as part of an extension. The `if (!set.contains(n - 1))` check is the key optimization.

---

**7. Cycle Detection — Sequence / Linked List**

> When iterating through a sequence (or linked list), store each visited element in a Set. If you see one again → cycle.

```java
// Happy Number — sum of squares of digits, repeat. If reaches 1 → happy. If cycles → not happy.
Set<Integer> seen = new HashSet<>();
while (n != 1 && seen.add(n)) {
    int sum = 0;
    while (n > 0) {
        int d = n % 10;
        sum += d * d;
        n /= 10;
    }
    n = sum;
}
return n == 1;
```

```java
// Linked List Cycle (HashSet approach — Floyd's is faster but this is intuitive)
Set<ListNode> seen = new HashSet<>();
ListNode curr = head;
while (curr != null) {
    if (!seen.add(curr)) {
        return true;
    }
    curr = curr.next;
}
return false;
```

**🏷️ Example problems:** Happy Number (LC 202), Linked List Cycle (LC 141), Linked List Cycle II (LC 142), Find the Duplicate Number (LC 287 — variation)

---

**8. Sliding Window with Set (No-Duplicates Window)**

> Maintain a window where all elements are unique. Expand right, shrink left when duplicate found. Different from HashMap version because we only care about presence, not count.

```java
// Longest Substring Without Repeating Characters
Set<Character> window = new HashSet<>();
int l = 0;
int best = 0;
for (int r = 0; r < s.length(); r++) {
    while (window.contains(s.charAt(r))) {
        window.remove(s.charAt(l));
        l++;
    }
    window.add(s.charAt(r));
    best = Math.max(best, r - l + 1);
}
return best;
```

**🏷️ Example problems:** Longest Substring Without Repeating Characters (LC 3), Contains Duplicate II (LC 219 — sliding window of size k), Maximum Erasure Value (LC 1695)

---

**9. Path Tracking in DFS (Add on Entry, Remove on Exit)**

> When DFS-ing through a graph and you need to detect cycles **on the current path** (not just any visited node), add to set when entering, remove when backtracking.

```java
// Course Schedule — detect cycle in directed graph
Set<Integer> visiting = new HashSet<>(); // current DFS path
Set<Integer> visited = new HashSet<>();  // fully processed

private boolean hasCycle(int node, Map<Integer, List<Integer>> graph) {
    if (visiting.contains(node)) {
        return true; // cycle on current path
    }
    if (visited.contains(node)) {
        return false; // already proven safe
    }
    visiting.add(node);
    for (int next : graph.getOrDefault(node, List.of())) {
        if (hasCycle(next, graph)) {
            return true;
        }
    }
    visiting.remove(node);
    visited.add(node);
    return false;
}
```

**🏷️ Example problems:** Course Schedule (LC 207), Course Schedule II (LC 210), Find Eventual Safe States (LC 802), Detect Cycles in 2D Grid (LC 1559)

---

**10. Deduplicating Results in Combinatorial Problems**

> When generating combinations/permutations and the output may produce duplicates, use a Set of canonical representations (sorted tuples / strings) to filter.

```java
// 3Sum — alternative to sort + skip approach
Set<List<Integer>> result = new HashSet<>();
Arrays.sort(nums);
for (int i = 0; i < nums.length - 2; i++) {
    int l = i + 1;
    int r = nums.length - 1;
    while (l < r) {
        int sum = nums[i] + nums[l] + nums[r];
        if (sum == 0) {
            result.add(List.of(nums[i], nums[l], nums[r]));
            l++;
            r--;
        } else if (sum < 0) {
            l++;
        } else {
            r--;
        }
    }
}
return new ArrayList<>(result);
```

**🏷️ Example problems:** 3Sum (LC 15), 4Sum (LC 18), Subsets II (LC 90 — with duplicates in input), Permutations II (LC 47)

> ⚠️ Sorting input + skipping duplicates is usually more efficient than this; use Set-dedup only when sorting isn't viable.

---

## **🔹 TreeSet (Sorted Set)**

Sorted Set backed by **Red-Black Tree**, all ops in **O(log n)**.

```java
TreeSet<Integer> ts = new TreeSet<>();
```

### **Useful Methods (Sorted Navigation)**

| **Method** | **Description** | **Time** |
| --- | --- | --- |
| `add(e)` / `remove(e)` / `contains(e)` | Same as Set but O(log n) | O(log n) |
| `first()` / `last()` | Smallest / largest element | O(log n) |
| `floor(e)` | Largest element ≤ e | O(log n) |
| `ceiling(e)` | Smallest element ≥ e | O(log n) |
| `lower(e)` | Largest element < e (strict) | O(log n) |
| `higher(e)` | Smallest element > e (strict) | O(log n) |
| `pollFirst()` / `pollLast()` | Remove and return smallest/largest | O(log n) |
| `subSet(lo, hi)` | View of elements in `[lo, hi)` | O(log n) |
| `headSet(e)` / `tailSet(e)` | Elements `< e` / `≥ e` | O(log n) |

---

### **TreeSet DSA Patterns**

---

**1. Closest Element Lookup (Floor / Ceiling)**

> Find the nearest value to a given target — `floor` for ≤, `ceiling` for ≥. Compare both to get closest absolute difference.

```java
// Contains Duplicate III — does there exist i, j such that |nums[i] - nums[j]| <= t and |i - j| <= k?
TreeSet<Long> window = new TreeSet<>();
for (int i = 0; i < nums.length; i++) {
    Long n = (long) nums[i];
    Long ceil = window.ceiling(n - t);
    if (ceil != null && ceil <= n + t) {
        return true;
    }
    window.add(n);
    if (window.size() > k) {
        window.remove((long) nums[i - k]);
    }
}
return false;
```

**🏷️ Example problems:** Contains Duplicate III (LC 220), My Calendar I (LC 729), My Calendar II (LC 731), Find Right Interval (LC 436)

---

**2. Sliding Window Min/Max with TreeSet**

> Alternative to monotonic deque when you need the running min/max in a window. Slightly slower (O(log k) per op) but simpler to reason about.

```java
TreeSet<int[]> window = new TreeSet<>((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
// store as {value, index} to allow duplicates
for (int r = 0; r < nums.length; r++) {
    window.add(new int[]{ nums[r], r });
    if (r >= k) {
        window.remove(new int[]{ nums[r - k], r - k });
    }
    if (r >= k - 1) {
        int min = window.first()[0];
        int max = window.last()[0];
        // use min / max
    }
}
```

**🏷️ Example problems:** Sliding Window Maximum (LC 239 — though deque is faster), Longest Continuous Subarray With Absolute Diff Limit (LC 1438)

---

**3. Range Query / Interval Operations**

> Use `subSet`, `headSet`, `tailSet` to grab elements in a range without scanning the whole structure.

```java
TreeSet<Integer> ts = new TreeSet<>(List.of(10, 20, 30, 40, 50));
ts.subSet(15, 45);   // [20, 30, 40]
ts.headSet(30);      // [10, 20]
ts.tailSet(30);      // [30, 40, 50]
```

**🏷️ Example problems:** Range queries in interval problems, Calendar booking systems

---

**4. Sorted Insertion + Quick Lookup**

> Maintain a stream of values sorted at all times, with O(log n) insertion. Useful for "online" algorithms where data arrives one at a time.

```java
TreeSet<Integer> sorted = new TreeSet<>();
for (int n : stream) {
    sorted.add(n);
    Integer prev = sorted.lower(n);
    Integer next = sorted.higher(n);
    // process neighbors
}
```

**🏷️ Example problems:** Data Stream as Disjoint Intervals (LC 352), Find Median from Data Stream (LC 295 — usually two heaps, but TreeMap variant exists)

---

## **🔹 LinkedHashSet (Bonus)**

Maintains **insertion order**. Same API as HashSet, slightly slower due to ordering bookkeeping. Useful when iteration order matters but you still want O(1) add/contains.

```java
Set<Integer> ordered = new LinkedHashSet<>();
ordered.add(3);
ordered.add(1);
ordered.add(2);
// iteration: 3, 1, 2 (insertion order)
```

**🏷️ Use cases:** Preserving order while deduplicating, building "first occurrence" lists.

---

### **Iteration Patterns**

```java
// HashSet / TreeSet / LinkedHashSet — same syntax
for (Integer n : set) {
    // use n
}

// Stream
set.stream().filter(n -> n > 10).forEach(System.out::println);
```

---

### **⚠️ Gotchas (Silent Bug Hall of Fame)**

**Null handling differs by implementation.**

```java
new HashSet<>().add(null);   // OK ✅ (one null allowed)
new TreeSet<>().add(null);   // NullPointerException ❌
```

---

**Custom objects need `equals()` + `hashCode()`** — otherwise `contains()` silently returns false.

```java
class Point { int x, y; /* no overrides */ }
Set<Point> set = new HashSet<>();
set.add(new Point(1, 2));
set.contains(new Point(1, 2));   // false ❌ — different object

record Point(int x, int y) {}     // ✅ free equals + hashCode
```

---

**For `TreeSet`, custom objects need `Comparable` or a `Comparator`.**

```java
TreeSet<Point> ts = new TreeSet<>((a, b) -> a.x() - b.x()); // ✅ via Comparator
```

---

**TreeSet Comparator returning 0 = duplicate, silently rejected.** If two elements compare as equal, the second is discarded. Common bug in sliding window problems.

```java
TreeSet<int[]> ts = new TreeSet<>((a, b) -> a[0] - b[0]);
ts.add(new int[]{ 5, 0 });
ts.add(new int[]{ 5, 1 });   // ❌ rejected — same value
ts.size();                   // 1, not 2

// ✅ Add tiebreaker (index/id) so equal values still get added
TreeSet<int[]> ts = new TreeSet<>((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
```

---

**`TreeSet` with duplicate values doesn't work directly** — use the `int[]` tiebreaker pattern above, or use `TreeMap<Value, Count>` instead.

---

**Floor/Ceiling return `null` if no element exists** — always null-check.

```java
TreeSet<Integer> ts = new TreeSet<>(List.of(10, 20));
Integer f = ts.floor(5);     // null — no element ≤ 5
int n = ts.floor(5);         // NullPointerException ❌
if (f != null) { /* use f */ }   // ✅
```

---

**Mutating an element after `add()`** silently breaks lookup — same trap as HashMap keys.

```java
List<Integer> elem = new ArrayList<>(List.of(1, 2));
set.add(elem);
elem.add(3);                 // ❌ hashCode changed
set.contains(elem);          // may return false — element is "lost"
// Treat set elements as immutable after insertion
```

---

**`set.iterator()` on HashSet has no guaranteed order** — don't rely on it for output ordering. Use `LinkedHashSet` if order matters.

---

**Removing while iterating** throws `ConcurrentModificationException`.

```java
// ❌ ConcurrentModificationException
for (Integer n : set) {
    if (n < 0) set.remove(n);
}

// ✅ Use removeIf
set.removeIf(n -> n < 0);
```

---

**Auto-unboxing NPE on null elements.**

```java
Set<Integer> set = new HashSet<>();
set.add(null);
for (int n : set) { /* ... */ }      // NullPointerException ❌
for (Integer n : set) { /* ... */ }  // ✅ — use boxed type
```

---

**TreeSet's comparator must be consistent with `equals`** — if compare says equal but `.equals()` says not equal, the set's behavior becomes undefined.

---

### **⚡ Quick Set Cheat Sheet**

| **Need** | **Use** |
| --- | --- |
| Unique elements + O(1) lookup | `HashSet` |
| Sorted unique elements | `TreeSet` |
| Insertion-order + uniqueness | `LinkedHashSet` |
| Find closest / floor / ceiling | `TreeSet` |
| Cycle detection (sequence/graph) | `HashSet` |
| Visited tracking (BFS/DFS) | `HashSet` |
| Path tracking with backtrack | `HashSet` (add on enter, remove on exit) |
| Longest consecutive sequence | `HashSet` (start-of-sequence trick) |
| Window with no duplicates | `HashSet` (sliding window) |
| Range / interval queries | `TreeSet.subSet / headSet / tailSet` |
