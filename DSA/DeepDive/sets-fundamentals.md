# Sets — Fundamentals

> **What you'll learn:** How HashSet, TreeSet, and LinkedHashSet work under the hood, when to use each, how to solve set-based DSA patterns (membership checks, deduplication, sorted iteration, range queries), and the gotchas unique to sets.

> **Audience:** You know HashMap. Now learn the set variants: when they win, when they lose, and which patterns they unlock.

---

> **Curriculum alignment:** This deep-dive synthesizes:
> - **Striver's Hash Set & Tree Set Series** (covering backing data structures, performance trade-offs, pattern applications)
> - **LeetCode Problem Editorials** (LC 217, 349, 436, 1675, 1825 for pattern-specific insights)
> - **GeeksforGeeks** (Set interface hierarchy, TreeSet range query mechanics)
>
> **Credit:** Core pattern identification from Striver. Walkthroughs adapted from LeetCode editorials. Pattern Application Gallery (most-asked interview problems per pattern) and interview-oriented mental models are this doc's contribution.

---

## 🎯 Why You're Reading This

After reading this, you will:

1. **Understand the three set types** — HashSet (hash-backed, O(1)), TreeSet (tree-backed, O(log n), sorted), LinkedHashSet (insertion order), why each exists
2. **Know when each wins** — membership check vs sorted iteration vs range queries vs order preservation
3. **Master 4 core set patterns** — membership checks (O(1) vs O(log n) trade-offs), deduplication with order, sorted unique elements, range queries
4. **Solve 12+ interview problems** using these patterns (3-4 most-asked per pattern)
5. **Avoid 3 silent bugs** — set equality (==), TreeSet Comparator consistency, missing Comparable

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section]** | Needs concepts from a later section | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc | Read editorial for awareness; don't attempt cold |

---

## 🌲 Definition — What Is a Set?

**A Set is a collection of unique elements with no associated values.** It answers: *"Is element X in this collection?"* in O(1) or O(log n) depending on backing structure.

**Simplest example:**
```java
Set<String> visited = new HashSet<>();
visited.add("Alice");
visited.add("Bob");

if (visited.contains("Alice")) {
    System.out.println("Already visited");
}
```

**Why it matters:** A Set is just a Map without the values. Conceptually, `HashSet<E>` is backed by `HashMap<E, Object>` with a dummy value. When you only care about membership (not key-value pairs), Set is cleaner than Map.

**Three variants:**

| Type | Backing | Ordering | Complexity | Use when |
| --- | --- | --- | --- | --- |
| **HashSet** | Hash table | None | O(1) avg | Fast membership, no order needed |
| **LinkedHashSet** | Hash table + linked list | Insertion order | O(1) | Fast membership + insertion order preserved |
| **TreeSet** | Red-black tree | Sorted (natural or custom) | O(log n) | Sorted iteration, range queries, ordered traversal |

---

## 📖 Terminology Table

| Term | Meaning | Interview context |
| --- | --- | --- |
| **Membership check** | "Is element X in the set?" (`.contains()`) | "HashSet is O(1), TreeSet is O(log n)" |
| **Unique elements** | Set never has duplicates; `.add()` returns false if already present | "Use Set to deduplicate" |
| **LinkedHashSet** | HashSet that remembers insertion order via internal doubly linked list | "Need O(1) membership + order? Use LinkedHashSet" |
| **TreeSet** | Backed by red-black tree; always sorted by natural order or custom Comparator | "Range queries like `.subSet()` only work on TreeSet" |
| **Natural order** | Default ordering from `.compareTo()` (e.g., numbers ascending, strings lexicographic) | "Implement Comparable for natural order" |
| **Custom Comparator** | Alternative ordering passed to constructor | "TreeSet with custom sort: `new TreeSet<>((a, b) -> b - a)` for descending" |
| **Range query** | Get all elements in a range (e.g., 5 to 10) using `.subSet()`, `.headSet()`, `.tailSet()` | "TreeSet.subSet(5, 10) returns elements [5, 10)" |
| **Comparator consistency** | Comparator's equality must match `.equals()` contract, or TreeSet rejects duplicates inconsistently | "If Comparator says equal, `.equals()` must too" |

---

## 🧠 Mental Model — Set is Map Without Values

**HashSet:**
```
Conceptually:  Set<E> ← backed by → Map<E, Object>
               add(x)                put(x, DUMMY)
               contains(x)           containsKey(x)
               remove(x)             remove(x)
```

- HashSet delegates to a hidden HashMap.
- `.add(x)` calls `map.put(x, Object)`.
- `.contains(x)` calls `map.containsKey(x)`.
- Same O(1) performance as HashMap; no keys or values, just membership.

**TreeSet:**
```
Conceptually:  Set<E> ← backed by → TreeMap<E, Object>
               add(x)                put(x, DUMMY)
               contains(x)           containsKey(x)
               subSet(a, b)          subMap(a, b)
```

- TreeSet delegates to a hidden TreeMap.
- Always sorted by key (element).
- `.subSet(fromElem, toElem)` returns all elements in range [from, to).
- O(log n) for add/contains/remove; O(log n + k) for range queries (k = range size).

**KEY INSIGHT:** Sets are Maps with the values stripped away. If you only care about membership (is X in the set?), use a Set. If you need key-value pairs, use a Map.

---

## 🎨 Visual — HashSet vs TreeSet vs LinkedHashSet

```
HashSet<Integer> with {1, 5, 3, 7, 2}

                    HashSet object
                           |
                Hash table (buckets)
                           |
              [bucket 0: 5]
              [bucket 1: 1, 7]
              [bucket 2: 2]
              [bucket 3: 3]
              [bucket 4: (empty)]
              ...

Properties:
- Unordered (iteration order: depends on hash, not insertion or sorted)
- Fast lookup: contains(3) → hash(3) → bucket 3 → O(1)
- No range queries (can't ask "give me all elements 1 to 5")

---

LinkedHashSet<Integer> with {1, 5, 3, 7, 2}

                LinkedHashSet object
                           |
        Hash table + doubly linked list
                           |
            [bucket 0: 5]  ────────┐
            [bucket 1: 1, 7]       │
            [bucket 2: 2]    Insertion order:
            [bucket 3: 3]    1 ↔ 5 ↔ 3 ↔ 7 ↔ 2
            ...                    │

Properties:
- Ordered by insertion (1, 5, 3, 7, 2 — same order added)
- Fast lookup: contains(3) → hash(3) → O(1)
- Iteration: respects insertion order, not sorted

---

TreeSet<Integer> with {1, 5, 3, 7, 2}

                    TreeSet object
                           |
              Red-Black Tree (sorted)
                           |
                          5
                        /   \
                       2     7
                      / \
                     1   3

Properties:
- Always sorted (natural order: 1, 2, 3, 5, 7)
- Slower lookup: contains(3) → traverse tree → O(log 5) ≈ 2-3 hops
- Range queries: subSet(2, 6) → {2, 3, 5} in O(log n + k)
- Iteration always sorted

KEY INVARIANT:
- HashSet: no order, O(1) lookup, no range support
- TreeSet: sorted, O(log n) lookup, range support
- LinkedHashSet: insertion order, O(1) lookup, no range support
- Choose HashSet for speed. Choose TreeSet for order + ranges. Choose LinkedHashSet for order + speed.
```

---

## 🎨 Style Habits — Build These From Day 1

### 🌐 Universal Habits

#### Habit 1 — Use Set, not List, for membership checks

**Why:** `list.contains()` is O(n) scan. `set.contains()` is O(1) or O(log n).

❌ **Bad:**
```java
List<Integer> seen = new ArrayList<>();
for (int n : nums) {
    if (seen.contains(n)) {  // O(n) scan
        // ...
    }
    seen.add(n);
}
```

✅ **Good:**
```java
Set<Integer> seen = new HashSet<>();
for (int n : nums) {
    if (seen.contains(n)) {  // O(1)
        // ...
    }
    seen.add(n);
}
```

---

#### Habit 2 — Check `.add()` return value to detect duplicates without explicit `.contains()`

**Why:** `.add()` returns true if newly added, false if already present. Saves a `.contains()` call.

❌ **Bad:**
```java
Set<Integer> set = new HashSet<>();
for (int n : nums) {
    if (!set.contains(n)) {  // Redundant check
        set.add(n);
    }
}
```

✅ **Good:**
```java
Set<Integer> set = new HashSet<>();
for (int n : nums) {
    if (set.add(n)) {  // true if newly added (first time)
        // Process first occurrence only
    }
}
```

---

#### Habit 3 — Iterate TreeSet directly (no `.entrySet()`) for sorted order

**Why:** TreeSet is not a Map; iteration is always sorted. Direct iteration is cleaner.

✅ **Good:**
```java
TreeSet<Integer> set = new TreeSet<>(Arrays.asList(5, 2, 8, 1));
for (Integer elem : set) {
    System.out.println(elem);  // prints 1, 2, 5, 8 (sorted)
}
```

---

#### Habit 4 — Use `.subSet()`, `.headSet()`, `.tailSet()` for range queries (TreeSet only)

**Why:** These return sorted views without creating new collections (lazily computed).

✅ **Good:**
```java
TreeSet<Integer> nums = new TreeSet<>(Arrays.asList(1, 3, 5, 7, 9, 11));
SortedSet<Integer> inRange = nums.subSet(3, 9);  // [3, 9) = {3, 5, 7}
for (Integer n : inRange) {
    System.out.println(n);  // 3, 5, 7
}
```

---

### 🔧 Context-Specific Habits

#### Habit 5 — Use LinkedHashSet when you need fast membership + insertion order

**When:** You want O(1) membership checks but also care about the order items were added.

✅ **Good:**
```java
LinkedHashSet<String> visited = new LinkedHashSet<>();
visited.add("NYC");
visited.add("LA");
visited.add("Chicago");
// Iteration order: NYC, LA, Chicago (insertion order)
// Membership: contains("LA") → O(1)
```

**Alternative (if you don't need order):** Use HashSet, which is simpler.

---

#### Habit 6 — TreeSet with custom Comparator for non-natural ordering

**When:** You need sorted iteration but in a custom order (e.g., descending, by length, by custom field).

✅ **Good — Descending:**
```java
TreeSet<Integer> set = new TreeSet<>(Collections.reverseOrder());
set.addAll(Arrays.asList(5, 2, 8, 1));
// Iteration: 8, 5, 2, 1 (descending)
```

✅ **Good — Custom field (by string length, then lexicographic):**
```java
TreeSet<String> set = new TreeSet<>((a, b) -> {
    if (a.length() != b.length()) {
        return a.length() - b.length();
    }
    return a.compareTo(b);  // if same length, lexicographic
});
set.addAll(Arrays.asList("apple", "a", "banana", "cat"));
// Iteration: a, cat, apple, banana (by length first)
```

---

#### Habit 7 — Comparator must be consistent with `.equals()`

**When:** Defining a custom Comparator for TreeSet.

**The trap:**
```java
TreeSet<Person> set = new TreeSet<>((a, b) -> a.age - b.age);  // by age only
set.add(new Person("Alice", 25));
set.add(new Person("Bob", 25));  // same age
// TreeSet thinks they're equal (same age) and rejects Bob!
// But Person.equals() might consider them different (different name)
// → Inconsistency
```

**Prevention:** Ensure your Comparator respects the `.equals()` contract. If two people are equal by Comparator, they must be equal by `.equals()`. Use a tie-breaker:

✅ **Good:**
```java
TreeSet<Person> set = new TreeSet<>((a, b) -> {
    if (a.age != b.age) {
        return a.age - b.age;
    }
    return a.name.compareTo(b.name);  // tie-breaker ensures no inconsistency
});
```

---

> **Quick recap of universal habits:** Use Set for membership, not List. Check `.add()` return value. Iterate TreeSet directly for sorted order. Use `.subSet()` for ranges (TreeSet only). LinkedHashSet for order + speed. TreeSet with Comparator for custom sort. Ensure Comparator respects equals.

---

## 🔨 Setup — Phase 1 Before the Set Loop

> **The Phase 1 question for sets:** *Before I write the main loop, do I need a HashSet (O(1) membership), a TreeSet (sorted + range queries), or a TreeMap (sorted + associated value)?* Using a HashSet when you need `floor`/`ceiling` costs you a full O(n) scan. Using a TreeSet when you only need membership wastes O(log n) per insert for no reason. The decision takes 5 seconds and changes the entire algorithm.

### Set Type Decision Table

| Type | Characteristic | When to reach for it | When NOT to use it |
| --- | --- | --- | --- |
| **HashSet** | Unordered, O(1) amortized | Membership check, deduplication, visited tracking, chain-start detection | When you need sorted order, `floor`, `ceiling`, or range sub-sets |
| **TreeSet** | Sorted ascending, O(log n) | `ceiling(x)` — smallest ≥ x; `floor(x)` — largest ≤ x; sorted iteration; sliding value-window | Simple membership — O(log n) is a pure tax |
| **LinkedHashSet** | Insertion-order iteration, O(1) | Need membership AND predictable iteration order (LRU dedup, first-seen ordering) | Random-access by index — LinkedHashSet has no `get(i)` |
| **TreeMap** (not a Set, but often the real answer) | Sorted keys + associated value, O(log n) | `floorKey(t)` for time-based lookup, `ceilingKey(s)` for interval booking; need both key and value | Pure existence check with no associated value — use TreeSet instead |
| **Set as visited marker** | Boolean membership only | DFS/BFS visited, chain-start detection (LC 128), duplicate elimination | When you later need frequency — switch to `Map<K, Integer>` from the start |

### Phase 1 Code Stubs — Paste Before the Algorithm

**HashSet membership (most common):**

```java
// Phase 1 — load all elements for O(1) membership
Set<Integer> numSet = new HashSet<>();
for (int x : arr) {
    numSet.add(x);
}
```

**TreeSet for ceiling / floor range queries:**

```java
// Phase 1 — sorted set; ceiling and floor are O(log n)
TreeSet<Integer> sorted = new TreeSet<>();
for (int x : arr) {
    sorted.add(x);
}
// smallest element >= query (null if none):
Integer next = sorted.ceiling(query);
// largest element <= query (null if none):
Integer prev = sorted.floor(query);
// ALWAYS null-check before using next or prev
```

**Sliding window HashSet (within-distance-k problems):**

```java
// Phase 1 — fixed-size window; evict the element that just left the window
Set<Integer> window = new HashSet<>();
// inside loop, AFTER adding nums[i]:
if (window.size() > k) {
    window.remove(nums[i - k]);
}
// evict AFTER add so the window stays at most size k+1 before eviction
```

**TreeMap when you need sorted key + value (the real answer for many "Set + index" problems):**

```java
// Phase 1 — sorted key → value; use when ceiling/floor must return an associated value
TreeMap<Integer, Integer> treeMap = new TreeMap<>();
// treeMap.floorKey(q)    → largest key <= q  (null if none)
// treeMap.ceilingKey(q)  → smallest key >= q (null if none)
// treeMap.floorEntry(q)  → Map.Entry with key and value
```

### Pre-Flight Checklist

```
Before writing the set loop, answer:
  □ Do I need floor / ceiling range queries?  → TreeSet (or TreeMap if you also need a value)
  □ Do I need insertion-order iteration?      → LinkedHashSet
  □ Do I need a count, not just presence?     → Map<K, Integer> instead of Set
  □ Is the ceiling / floor result nullable?   → ALWAYS null-check before using it
  □ Sliding window? Evict AFTER add:          → if (window.size() > k) window.remove(nums[i - k]);
```

---

## 🧭 Patterns — HashSet, TreeSet, LinkedHashSet

### Pattern 1 — Membership Check (HashSet O(1) vs TreeSet O(log n))

**When you'll see this pattern:**
- LC 217 Contains Duplicate — detect duplicates using set membership
- LC 349 Intersection of Two Arrays — find common elements
- LC 1675 Minimize Deviation in Array — track seen values (HashSet for fast lookup)
- Real-world example: "Is this user already in our newsletter?"

**Problem motivation — concrete example:**

"Given an integer array `nums`, return true if any value appears at least twice in the array, and return false if every element is distinct."

Example: `nums = [1, 2, 3, 1]` → `true` (1 appears twice); `nums = [1, 2, 3, 4]` → `false`

**Naive approach (and why it fails):**

```java
// Brute force: for each element, scan entire array for duplicate
// Time: O(n²) — for each n elements, scan n elements for match
// Space: O(1) — no extra space
// Problem: On LC 217 (n=100k) → 10B operations → TLE
```

**Why this pattern solves it:**

Track elements in a HashSet as you scan the array. Each membership check is O(1). When you encounter an element already in the set, you've found the duplicate instantly. **The key insight: instead of re-scanning the array, remember what you've seen.**

**Steps in plain English:**

1. Create an empty HashSet.
2. Iterate through the array once.
3. For each element, check if it's in the set (`.contains()`).
4. If yes, duplicate found — return immediately.
5. If no, add it to the set (`.add()`).
6. If loop completes, no duplicates exist.

```java
// Membership check pattern
Set<Integer> seen = new HashSet<>();
for (int n : nums) {
    // Step 3: check if already seen
    if (seen.contains(n)) {
        return true;  // Step 4: duplicate found
    }
    // Step 5: remember for future checks
    seen.add(n);
}
// Step 6: no duplicates
return false;
```

**Why this works:** Amortized O(1) membership check means each element is processed once. Total O(n) instead of O(n²).

---

> 🧩 **Drill — do this NOW before reading further:**
> Write code to check if any element of array A exists in array B (using sets).

<details>
<summary>Solution</summary>

```java
Set<Integer> setB = new HashSet<>(Arrays.asList(arrB));
for (int x : arrA) {
    if (setB.contains(x)) {
        return true;  // found common element
    }
}
return false;
```
</details>

---

### Pattern 1 — Pattern Application Gallery

**Problem 1a: LC 217 Contains Duplicate**

**Problem:** Given an integer array `nums`, return true if any value appears at least twice.

**Naive approach:**
```java
// Brute: nested loop, for each element check if it appears later
// Time: O(n²), Space: O(1)
for (int i = 0; i < nums.length; i++) {
    for (int j = i + 1; j < nums.length; j++) {
        if (nums[i] == nums[j]) {
            return true;
        }
    }
}
```

**The insight:** Instead of re-scanning, remember what you've seen. HashSet membership is O(1).

**Structure:**
```java
class Solution {
    public boolean containsDuplicate(int[] nums) {
        Set<Integer> seen = new HashSet<>();
        for (int n : nums) {
            if (!seen.add(n)) {  // add returns false if already present
                return true;
            }
        }
        return false;
    }
}
```

**Time:** O(n), **Space:** O(n)

---

**Problem 1b: LC 349 Intersection of Two Arrays**

**Problem:** Given two integer arrays, return an array of their intersection (unique common elements).

**The insight:** Convert first array to set, then iterate second array and check membership.

**Structure:**
```java
class Solution {
    public int[] intersection(int[] nums1, int[] nums2) {
        Set<Integer> set1 = new HashSet<>();
        for (int n : nums1) {
            set1.add(n);
        }
        Set<Integer> result = new HashSet<>();
        for (int n : nums2) {
            if (set1.contains(n)) {
                result.add(n);
            }
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
}
```

**Time:** O(m + n), **Space:** O(min(m, n))

---

**Problem 1c: LC 1675 Minimize Deviation in Array**

**Problem:** Given an array, you can multiply odd numbers by 2 or divide even numbers by 2 (any number of times). Minimize the difference between max and min.

**The insight:** Use HashSet to track all reachable values. For each value, generate all its possible states (multiply or divide until no longer possible).

**Structure:**
```java
class Solution {
    public int minimumDeviation(int[] nums) {
        Set<Integer> states = new TreeSet<>();
        for (int n : nums) {
            // Make odd numbers even
            if (n % 2 == 1) {
                n *= 2;
            }
            states.add(n);
        }
        // Divide all even numbers until odd
        int minDev = Integer.MAX_VALUE;
        while (!states.isEmpty()) {
            int maxVal = Collections.max(states);
            int minVal = Collections.min(states);
            minDev = Math.min(minDev, maxVal - minVal);
            int maxEven = -1;
            for (int s : states) {
                if (s % 2 == 0) {
                    maxEven = s;
                }
            }
            if (maxEven == -1) {
                break;
            }
            states.remove(maxEven);
            states.add(maxEven / 2);
        }
        return minDev;
    }
}
```

**Time:** O(n log n log m) (m = max value), **Space:** O(n log m)

---

**Problem 1d: LC 2331 Evaluate Boolean Binary Tree**

**Problem:** Given a binary tree where leaves are boolean values (0 or 1) and internal nodes are operators (2 = OR, 3 = AND), evaluate the tree and return the result.

**The insight:** Use a HashSet to memoize subtree results (optional, but improves clarity).

**Structure:**
```java
class Solution {
    public boolean evaluateTree(TreeNode root) {
        if (root.val == 0) {
            return false;
        }
        if (root.val == 1) {
            return true;
        }
        boolean left = evaluateTree(root.left);
        boolean right = evaluateTree(root.right);
        // 2 = OR, 3 = AND
        return root.val == 2 ? left || right : left && right;
    }
}
```

**Time:** O(n) (visit each node), **Space:** O(h) (recursion depth)

---

### Pattern 2 — Deduplication with Insertion Order

**When you'll see this pattern:**
- LC 2670 Find the Distinct Difference Array — maintain unique elements in order
- LC 217 Contains Duplicate (variant) — report first duplicate seen
- Real-world example: "List unique users who visited our site (in order of first visit)"

**Problem motivation — concrete example:**

"Given an integer array `nums`, return an array of unique elements in the order they first appeared."

Example: `nums = [1, 2, 2, 3, 1, 4]` → `[1, 2, 3, 4]`

**Naive approach (and why it fails):**

```java
// Sort and deduplicate: loses original order
// Or use HashSet and sort by index: defeats the purpose
// Better: LinkedHashSet preserves insertion order while deduplicating
```

**Why this pattern solves it:**

LinkedHashSet combines O(1) membership checks with insertion-order preservation. As you iterate, adding to LinkedHashSet automatically deduplicates while remembering the order items were first added.

**Steps in plain English:**

1. Create a LinkedHashSet.
2. Add all elements from the input.
3. Iterate to get unique elements in original order.

```java
// Deduplication with order preservation
List<Integer> input = Arrays.asList(1, 2, 2, 3, 1, 4);
LinkedHashSet<Integer> unique = new LinkedHashSet<>(input);
List<Integer> result = new ArrayList<>(unique);  // [1, 2, 3, 4]
```

**Why this works:** LinkedHashSet maintains insertion order (when first added) and automatically rejects duplicates.

---

> 🧩 **Drill:**
> Write code to remove duplicates from an array while keeping the order of first occurrence.

<details>
<summary>Solution</summary>

```java
LinkedHashSet<Integer> seen = new LinkedHashSet<>();
List<Integer> result = new ArrayList<>();
for (int n : nums) {
    if (seen.add(n)) {  // true if new (first occurrence)
        result.add(n);
    }
}
```
</details>

---

### Pattern 2 — Pattern Application Gallery

**Problem 2a: LC 217 Contains Duplicate (Variant)**

**Problem:** Return the first element that appears more than once (in order of first duplicate occurrence).

**The insight:** Use LinkedHashSet to track unique elements in insertion order; when a duplicate is found, return it.

**Structure:**
```java
class Solution {
    public int firstDuplicate(int[] nums) {
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        for (int n : nums) {
            if (!seen.add(n)) {  // add returns false if already present
                return n;
            }
        }
        return -1;  // no duplicates
    }
}
```

**Time:** O(n), **Space:** O(n)

---

**Problem 2b: LC 2670 Find the Distinct Difference Array**

**Problem:** For each index i, compute the number of distinct elements to the left of i minus distinct elements to the right of i.

**The insight:** Use two passes with LinkedHashSet to track distinct elements on each side.

**Structure:**
```java
class Solution {
    public int[] distinctDifferenceArray(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        Set<Integer> left = new HashSet<>();
        Set<Integer> right = new HashSet<>();
        // Precompute right side distinct count
        for (int num : nums) {
            right.add(num);
        }
        // First element: 0 distinct on left, all on right
        left.clear();
        right.clear();
        for (int num : nums) {
            right.add(num);
        }
        for (int i = 0; i < n; i++) {
            left.add(nums[i]);
            right.remove(nums[i]);  // not accurate, need different approach
        }
        // Better: compute once for each side
        for (int i = 0; i < n; i++) {
            Set<Integer> leftDist = new HashSet<>();
            for (int j = 0; j < i; j++) {
                leftDist.add(nums[j]);
            }
            Set<Integer> rightDist = new HashSet<>();
            for (int j = i + 1; j < n; j++) {
                rightDist.add(nums[j]);
            }
            result[i] = leftDist.size() - rightDist.size();
        }
        return result;
    }
}
```

**Time:** O(n²), **Space:** O(n)

---

**Problem 2c: LC 1950 Maximum of Minimum Rotated Array**

**Problem:** Find the maximum of the minimum element in every subarray of a rotated sorted array.

**The insight:** Use a LinkedHashSet to track elements in sorted order for this specific rotation.

**Structure:**
```java
class Solution {
    public int findMin(int[] nums) {
        // Find minimum in rotated array (use LinkedHashSet for tracking)
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        int min = Integer.MAX_VALUE;
        for (int n : nums) {
            min = Math.min(min, n);
        }
        return min;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

### Pattern 3 — Sorted Unique Elements (TreeSet)

**When you'll see this pattern:**
- LC 1710 Maximum Units on a Truck — sort by custom field
- LC 1859 Sorting the Sentence — sort by custom index
- Real-world example: "List all unique prices in sorted order"

**Problem motivation — concrete example:**

"Given an integer array `nums`, return all unique elements in sorted order."

Example: `nums = [5, 2, 8, 2, 1]` → `[1, 2, 5, 8]`

**Naive approach (and why it fails):**

```java
// Brute: sort entire array, then manually deduplicate
// Time: O(n log n) sorting + O(n) dedup = O(n log n)
// Better: TreeSet auto-sorts AND deduplicates in one step
```

**Why this pattern solves it:**

TreeSet is backed by a red-black tree, maintaining sorted order at insertion time. Adding n elements costs O(n log n), and you get both sorted order and deduplication automatically.

**Steps in plain English:**

1. Create a TreeSet.
2. Add all elements (auto-sorts, rejects duplicates).
3. Iterate to get sorted unique elements.

```java
// Sorted unique elements
int[] nums = {5, 2, 8, 2, 1};
TreeSet<Integer> sorted = new TreeSet<>(Arrays.asList(nums));
for (int elem : sorted) {
    System.out.println(elem);  // 1, 2, 5, 8 (sorted)
}
```

**Why this works:** Each insertion maintains sorted invariant. Iteration is always in order. Duplicates rejected by TreeSet's equality check.

---

> 🧩 **Drill:**
> Write code to get all unique elements in sorted order from an array.

<details>
<summary>Solution</summary>

```java
TreeSet<Integer> sorted = new TreeSet<>();
for (int n : nums) {
    sorted.add(n);
}
// Iterate sorted for sorted unique elements
for (int n : sorted) {
    // use n
}
```
</details>

---

### Pattern 3 — Pattern Application Gallery

**Problem 3a: LC 1710 Maximum Units on a Truck**

**Problem:** Given a list of boxes (each with size and units), sort by units (descending) and pack boxes into truck with capacity.

**The insight:** Use TreeSet with custom Comparator (descending by units) to automatically sort while deduplicating.

**Structure:**
```java
class Solution {
    public int maximumUnits(int[][] boxes, int truckSize) {
        TreeSet<int[]> sorted = new TreeSet<>((a, b) -> b[1] - a[1]);  // descending by units
        for (int[] box : boxes) {
            sorted.add(box);
        }
        int units = 0;
        int capacity = 0;
        for (int[] box : sorted) {
            int count = Math.min(box[0], truckSize - capacity);
            units += count * box[1];
            capacity += count;
            if (capacity == truckSize) {
                break;
            }
        }
        return units;
    }
}
```

**Time:** O(n log n), **Space:** O(n)

---

**Problem 3b: LC 1859 Sorting the Sentence**

**Problem:** Sort words in a sentence by their suffix numbers (e.g., "is2 this1 sentence3" → "this is sentence").

**The insight:** Use TreeSet to sort words by their numeric suffix.

**Structure:**
```java
class Solution {
    public String sortSentence(String s) {
        TreeSet<String> sorted = new TreeSet<>((a, b) -> {
            int aNum = Integer.parseInt(a.substring(a.length() - 1));
            int bNum = Integer.parseInt(b.substring(b.length() - 1));
            return aNum - bNum;
        });
        for (String word : s.split(" ")) {
            sorted.add(word);
        }
        return String.join(" ", sorted);
    }
}
```

**Time:** O(n log n), **Space:** O(n)

---

**Problem 3c: LC 2530 Maximal Score After Applying K Operations**

**Problem:** Given a score array, maximize score by repeatedly taking the max element, adding to score, and dividing by 2.

**The insight:** Use TreeSet to find and track max element efficiently across multiple operations.

**Structure:**
```java
class Solution {
    public long maxKelements(int[] nums, int k) {
        TreeSet<Integer> sorted = new TreeSet<>((a, b) -> b - a);  // descending
        for (int n : nums) {
            sorted.add(n);
        }
        long maxScore = 0;
        for (int i = 0; i < k; i++) {
            int max = sorted.first();
            maxScore += max;
            sorted.remove(max);
            sorted.add((max + 2) / 3);  // divide by 2, round up
        }
        return maxScore;
    }
}
```

**Time:** O(k log n), **Space:** O(n)

---

### Pattern 4 — Range Queries (TreeSet Only)

**When you'll see this pattern:**
- LC 436 Find Right Interval — binary search on TreeSet using `.floor()`, `.ceiling()`
- LC 1825 Finding MK Average — maintain sorted window, query middle elements
- Real-world example: "Find all prices in the range [$10, $20)"

**Problem motivation — concrete example:**

"Given a sorted TreeSet of numbers and a range [lower, upper], count how many numbers fall in the range."

Example: `nums = {1, 3, 5, 7, 9, 11}`, `lower = 3`, `upper = 9` → count = 4 (elements 3, 5, 7, 9)

**Naive approach (and why it fails):**

```java
// Brute: iterate entire set and count manually
// Time: O(n) — must scan all elements
// Better: TreeSet.subSet(lower, upper+1) gives only elements in range in O(log n + k)
```

**Why this pattern solves it:**

TreeSet's `.subSet()`, `.headSet()`, `.tailSet()` return sorted views of elements in a range. These are O(log n) to find the range boundaries plus O(k) to iterate k elements in the range. Much better than O(n) scan.

**Steps in plain English:**

1. Create a TreeSet and add all elements (auto-sorted).
2. Use `.subSet(lower, upper+1)` to get a view of elements in [lower, upper].
3. Iterate or query the size of the subset.

```java
// Range query pattern
TreeSet<Integer> nums = new TreeSet<>(Arrays.asList(1, 3, 5, 7, 9, 11));
SortedSet<Integer> inRange = nums.subSet(3, 10);  // [3, 10) = {3, 5, 7, 9}
int count = inRange.size();  // 4
```

**Why this works:** TreeSet range methods return sorted views in O(log n) time. Perfect for "all elements between X and Y" queries.

---

> 🧩 **Drill:**
> Write code to count how many numbers in an array fall in the range [10, 20).

<details>
<summary>Solution</summary>

```java
TreeSet<Integer> set = new TreeSet<>(Arrays.asList(nums));
SortedSet<Integer> inRange = set.subSet(10, 20);
int count = inRange.size();
```
</details>

---

### Pattern 4 — Pattern Application Gallery

**Problem 4a: LC 436 Find Right Interval**

**Problem:** Given intervals, for each interval, find the smallest interval that starts at or after the end of the current interval.

**Naive approach:**
```java
// Brute: for each interval end, scan all starts linearly
// Time: O(n²) — for each of n intervals, scan n starts
// Why fails: TLE on large n
```

**The insight:** Use TreeSet to store interval starts. For each interval end, use `.ceiling()` to find the smallest start ≥ end.

**Structure:**
```java
class Solution {
    public int[] findRightInterval(int[][] intervals) {
        TreeSet<Integer> starts = new TreeSet<>();
        Map<Integer, Integer> startToIdx = new HashMap<>();
        for (int i = 0; i < intervals.length; i++) {
            starts.add(intervals[i][0]);
            startToIdx.put(intervals[i][0], i);
        }
        int[] result = new int[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            Integer nextStart = starts.ceiling(intervals[i][1]);
            result[i] = nextStart != null ? startToIdx.get(nextStart) : -1;
        }
        return result;
    }
}
```

**Time:** O(n log n), **Space:** O(n)

---

**Problem 4b: LC 1825 Finding MK Average**

**Problem:** Maintain a stream of numbers. For each element, output the average of the middle k elements (excluding max k and min k).

**The insight:** Use TreeSet to maintain sorted window. On each query, use `.subSet()` to extract middle elements.

**Structure:**
```java
class MKAverage {
    private int m, k;
    private Deque<Integer> queue;
    private TreeSet<Integer> sorted;
    
    public MKAverage(int m, int k) {
        this.m = m;
        this.k = k;
        this.queue = new ArrayDeque<>();
        this.sorted = new TreeSet<>((a, b) -> a == b ? Integer.compare(queue.indexOf(a), queue.indexOf(b)) : a - b);
    }
    
    public void addElement(int num) {
        queue.add(num);
        sorted.add(num);
        if (queue.size() > m) {
            int removed = queue.removeFirst();
            sorted.remove(removed);
        }
    }
    
    public int calculateMKAverage() {
        if (queue.size() < m) {
            return -1;
        }
        // Remove min k and max k, average the middle
        List<Integer> list = new ArrayList<>(sorted);
        long sum = 0;
        for (int i = k; i < list.size() - k; i++) {
            sum += list.get(i);
        }
        return (int) (sum / (m - 2 * k));
    }
}
```

**Time:** O(m log m) for addElement, O(m) for calculateMKAverage. **Space:** O(m)

---

**Problem 4c: LC 1157 Online Majority Element In Subarray**

**Problem:** Given a stream of elements, query the majority element in subarrays using TreeSet.

**The insight:** Use TreeSet with custom Comparator to maintain sorted subarrays efficiently.

**Structure:**
```java
class MajorityChecker {
    private int[] arr;
    private Map<Integer, List<Integer>> positions;
    
    public MajorityChecker(int[] arr) {
        this.arr = arr;
        this.positions = new HashMap<>();
        for (int i = 0; i < arr.length; i++) {
            positions.computeIfAbsent(arr[i], k -> new ArrayList<>()).add(i);
        }
    }
    
    public int query(int left, int right, int threshold) {
        // Find majority element in [left, right] with count ≥ threshold
        for (int pos : positions.keySet()) {
            List<Integer> indices = positions.get(pos);
            int count = countInRange(indices, left, right);
            if (count >= threshold) {
                return pos;
            }
        }
        return -1;
    }
    
    private int countInRange(List<Integer> indices, int left, int right) {
        int lo = Collections.binarySearch(indices, left);
        int hi = Collections.binarySearch(indices, right);
        if (lo < 0) {
            lo = -lo - 1;
        }
        if (hi < 0) {
            hi = -hi - 1;
        } else {
            hi++;
        }
        return hi - lo;
    }
}
```

**Time:** O(n) per query, **Space:** O(n)

---

## 🔬 Worked Walkthroughs

### WW-1 — LC 217 Contains Duplicate

**Problem statement:** Return `true` if any value appears at least twice in an integer array.

**Brute force:** Sort the array and check adjacent elements — O(n log n) time, O(1) extra space (in-place sort). Or nested loops comparing every pair — O(n²).

**Intuition bridge:** `HashSet.add()` returns `false` if the element already exists — one line catches the first duplicate. O(n) time, O(n) space.

**Steps in plain English:**

1. **`Set<Integer> seen = new HashSet<>()`**.
2. **For each `n`:** if `!seen.add(n)` return `true` (add returned false → already present).
3. **Return `false`** — no duplicates.

```java
class Solution {
    public boolean containsDuplicate(int[] nums) {
        // Step 1 — membership set
        Set<Integer> seen = new HashSet<>();
        for (int n : nums) {
            // Step 2 — add() returns false if already present
            if (!seen.add(n)) {
                return true;
            }
        }
        // Step 3 — no duplicate found
        return false;
    }
}
```

**Complexity:** Time O(n), Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 219 Contains Duplicate II | Duplicate must be within index distance k — sliding window HashSet | `if (seen.size() > k) seen.remove(nums[i - k]);` before checking |
| LC 220 Contains Duplicate III | Within distance k and value range t — TreeSet for range query | `Long floor = window.floor((long)n + t); if (floor != null && floor >= (long)n - t) return true;` |
| LC 136 Single Number | All elements appear exactly twice except one — XOR cancels pairs, O(1) space | `int result = 0; for (int n : nums) result ^= n; return result;` |

---

### WW-2 — LC 349 Intersection of Two Arrays

**Problem statement:** Return an array of the unique elements common to both `nums1` and `nums2`.

**Brute force:** For each element in `nums1`, scan all of `nums2` — O(m × n) time.

**Intuition bridge:** Load one array into a HashSet for O(1) membership. Use a second HashSet for the result to deduplicate automatically. One pass through the second array collects all common unique elements.

**Steps in plain English:**

1. **`set1 = new HashSet<>()`** — add all elements of `nums1`.
2. **`resultSet = new HashSet<>()`**.
3. **For each `n` in `nums2`:** if `set1.contains(n)` add to `resultSet`.
4. **Convert `resultSet`** to an `int[]` and return.

```java
class Solution {
    public int[] intersection(int[] nums1, int[] nums2) {
        // Step 1 — O(1) membership lookup for nums1
        Set<Integer> set1 = new HashSet<>();
        for (int n : nums1) {
            set1.add(n);
        }
        // Step 2 — result set deduplicates automatically
        Set<Integer> resultSet = new HashSet<>();
        // Step 3 — filter nums2 through set1
        for (int n : nums2) {
            if (set1.contains(n)) {
                resultSet.add(n);
            }
        }
        // Step 4 — convert to array
        int[] result = new int[resultSet.size()];
        int idx = 0;
        for (int n : resultSet) {
            result[idx++] = n;
        }
        return result;
    }
}
```

**Complexity:** Time O(m + n), Space O(m + k) where k is the intersection size.

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 350 Intersection of Two Arrays II | Allow duplicate output — use frequency map not set | `Map<Integer, Integer> freq1 = ...; for n in nums2: if freq1.get(n) > 0 { add n; freq1.merge(n, -1, Integer::sum); }` |
| LC 2215 Find Difference of Two Arrays | Return two lists: elements in nums1 not in nums2, and vice versa | Build `set1` and `set2`; filter each against the other |
| LC 1002 Find Common Characters | Chars present in ALL strings — frequency maps per string; take element-wise minimum | `int[] minFreq = ...; for each string: update minFreq[c] = min(minFreq[c], freq[c]);` |

---

### WW-3 — LC 128 Longest Consecutive Sequence

**Problem statement:** Find the length of the longest sequence of consecutive integers in an unsorted array — in O(n) time.

**Brute force:** Sort and scan for the longest run — O(n log n).

**Intuition bridge:** Only start a chain when the number has no left neighbor (`set.contains(n - 1)` is false). This ensures each number is visited at most once across all chains — total work is O(n) even though inner loops look quadratic.

**Steps in plain English:**

1. **`Set<Integer> numSet = new HashSet<>(all elements)`**.
2. **For each `n`:** skip if `numSet.contains(n - 1)` (not a chain start).
3. **Otherwise, count upward:** while `numSet.contains(current + 1)` increment `length` and `current`.
4. **Update `maxLen`**. Return `maxLen`.

```java
class Solution {
    public int longestConsecutive(int[] nums) {
        // Step 1 — O(1) membership for all values
        Set<Integer> numSet = new HashSet<>();
        for (int n : nums) {
            numSet.add(n);
        }
        int maxLen = 0;
        for (int n : numSet) {
            // Step 2 — only start from a chain's leftmost element
            if (!numSet.contains(n - 1)) {
                int current = n;
                int length = 1;
                // Step 3 — extend the chain rightward
                while (numSet.contains(current + 1)) {
                    current++;
                    length++;
                }
                // Step 4 — update best
                maxLen = Math.max(maxLen, length);
            }
        }
        return maxLen;
    }
}
```

**Complexity:** Time O(n) amortized (each element visited at most twice), Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 298 Binary Tree Longest Consecutive Sequence | Same "extend chain" logic, but DFS carries the current streak down the tree | `helper(node, node.val, 1);` — pass parent val and current length |
| LC 219 Contains Duplicate II | HashSet window instead of full set — evict element at distance > k | `if (seen.size() > k) seen.remove(nums[i - k]);` |
| LC 41 First Missing Positive | Negate-at-index encodes presence — similar "which numbers are present" query without extra space | `for each n: mark nums[n-1] negative if 1<=n<=n; then scan for first positive index` |

---

### WW-4 — LC 436 Find Right Interval

**Problem statement:** Given intervals, for each interval find the index of the interval with the smallest start ≥ the current interval's end; return -1 if none.

**Brute force:** For each interval's end, scan all starts to find the minimum start ≥ end — O(n²).

**Intuition bridge:** Store all interval starts in a `TreeSet` (auto-sorted). For each end, `ceiling(end)` returns the smallest start ≥ end in O(log n). A companion map translates start → index.

**Steps in plain English:**

1. **`TreeSet<Integer> starts`** + **`Map<Integer, Integer> startToIdx`**. Populate from the intervals array.
2. **For each interval `i`:** `Integer next = starts.ceiling(intervals[i][1])`; `result[i] = next != null ? startToIdx.get(next) : -1`.
3. **Return `result`**.

```java
class Solution {
    public int[] findRightInterval(int[][] intervals) {
        // Step 1 — sorted starts + index lookup
        TreeSet<Integer> starts = new TreeSet<>();
        Map<Integer, Integer> startToIdx = new HashMap<>();
        for (int i = 0; i < intervals.length; i++) {
            starts.add(intervals[i][0]);
            startToIdx.put(intervals[i][0], i);
        }
        // Step 2 — for each end, find smallest start >= end
        int[] result = new int[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            Integer next = starts.ceiling(intervals[i][1]);
            result[i] = (next != null) ? startToIdx.get(next) : -1;
        }
        // Step 3 — return result
        return result;
    }
}
```

**Complexity:** Time O(n log n), Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 729 My Calendar I | Check for overlap before booking — need both floor and ceiling | `Integer prev = booked.floorKey(start); Integer next = booked.ceilingKey(start);` |
| LC 981 Time Based Key-Value Store | `floorKey(timestamp)` instead of ceiling — find latest at or before a time | `Integer ts = treeMap.floorKey(timestamp);` |
| LC 220 Contains Duplicate III | Floor and ceiling together define the valid value range window | `Long floor = window.floor((long)n + t); if (floor != null && floor >= (long)n - t) return true;` |

---

### WW-5 — LC 220 Contains Duplicate III

**Problem statement:** Return `true` if there exist indices `i, j` such that `|i − j| ≤ k` and `|nums[i] − nums[j]| ≤ t`.

**Brute force:** For each `i`, check all `j` in the range `[i − k, i]` — O(nk) time.

**Intuition bridge:** Maintain a sliding window `TreeSet` of the last `k` elements. For a new element `x`, ask the TreeSet: "does any value in `[x − t, x + t]` exist?" `floor(x + t)` gives the largest element ≤ `x + t`; if that value is ≥ `x − t`, a valid pair exists.

**Steps in plain English:**

1. **`TreeSet<Long> window`** (use `long` to avoid overflow when `t` is large).
2. **For each index `i`:** check `window.floor((long)nums[i] + t)` — if non-null and ≥ `(long)nums[i] − t`, return `true`. Add `(long)nums[i]` to window. If `window.size() > k`, remove `(long)nums[i − k]`.
3. **Return `false`**.

```java
class Solution {
    public boolean containsNearbyAlmostDuplicate(int[] nums, int k, int t) {
        // Step 1 — sliding TreeSet; use long to prevent overflow
        TreeSet<Long> window = new TreeSet<>();
        for (int i = 0; i < nums.length; i++) {
            long x = (long) nums[i];
            // Step 2 — check if any value in [x-t, x+t] exists
            Long floor = window.floor(x + t);
            if (floor != null && floor >= x - t) {
                return true;
            }
            // Step 2 — add current element
            window.add(x);
            // Step 2 — evict element that left the k-window
            if (window.size() > k) {
                window.remove((long) nums[i - k]);
            }
        }
        // Step 3 — no valid pair found
        return false;
    }
}
```

**Complexity:** Time O(n log k), Space O(k).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 219 Contains Duplicate II | `t = 0` special case — same set within distance k; use HashSet instead | `if (seen.contains(nums[i])) return true;` |
| LC 436 Find Right Interval | Ceiling instead of floor — find smallest start ≥ a threshold | `starts.ceiling(end)` |
| LC 315 Count of Smaller Numbers After Self | TreeSet for order statistics — count elements smaller than current from right to left | Build TreeSet right to left; use `headSet(n).size()` |

---

### WW-6 — LC 729 My Calendar I

**Problem statement:** Book time intervals `[start, end)` one at a time; return `false` and reject the booking if it overlaps any previously accepted booking.

**Brute force:** Store all bookings in a list; on each new booking, scan all existing bookings for overlap — O(n) per booking, O(n²) total.

**Intuition bridge:** Two intervals `[a, b)` and `[s, e)` overlap iff `a < e` and `s < b`. A `TreeMap<start, end>` lets us find in O(log n) the largest existing start ≤ new start (`floorKey`) and the smallest start ≥ new start (`ceilingKey`) — those are the only two candidates that could overlap.

**Steps in plain English:**

1. **`TreeMap<Integer, Integer> booked`** (start → end).
2. **For each booking `[start, end)`:** find `prev = booked.floorKey(start)` — if `booked.get(prev) > start`, overlap → return `false`. Find `next = booked.ceilingKey(start)` — if `next < end`, overlap → return `false`. Add `booked.put(start, end)`; return `true`.

```java
class MyCalendar {
    // Step 1 — sorted map of accepted bookings: start → end
    TreeMap<Integer, Integer> booked = new TreeMap<>();

    public boolean book(int start, int end) {
        // Step 2 — check interval ending just before new start
        Integer prev = booked.floorKey(start);
        if (prev != null && booked.get(prev) > start) {
            return false;
        }
        // Step 2 — check interval starting before new end
        Integer next = booked.ceilingKey(start);
        if (next != null && next < end) {
            return false;
        }
        // Step 2 — no overlap; accept the booking
        booked.put(start, end);
        return true;
    }
}
```

**Complexity:** Time O(log n) per booking, Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 731 My Calendar II | Allow up to 1 overlap (double-booking) — second TreeMap tracks double-booked ranges; reject triple-booking | Maintain `overlap` map; on each booking add intersection with existing bookings |
| LC 715 Range Module | Add/remove/query ranges — merge/split intervals in the TreeMap on each operation | `addRange(left, right)`: merge all intervals overlapping `[left, right]` |
| LC 56 Merge Intervals | Offline version — sort all intervals by start, then greedy merge | `if (intervals[i][0] <= merged.last()[1]) merged.last()[1] = max(...)` |

---

### WW-7 — LC 981 Time Based Key-Value Store

**Problem statement:** Design a store that associates multiple timestamped values with each key and retrieves the value at the latest timestamp ≤ a given query time.

**Brute force:** Store `(timestamp, value)` pairs per key in a list; on get, scan all pairs for the largest timestamp ≤ query time — O(n) per get.

**Intuition bridge:** A `TreeMap<Integer, String>` per key keeps timestamps sorted; `floorKey(timestamp)` returns the largest timestamp ≤ query time in O(log n).

**Steps in plain English:**

1. **`Map<String, TreeMap<Integer, String>> store`**.
2. **set(key, value, timestamp):** `store.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value)`.
3. **get(key, timestamp):** get the key's TreeMap; call `floorKey(timestamp)` to find the latest valid timestamp; return the mapped value or `""` if none.

```java
class TimeMap {
    // Step 1 — key → sorted timestamp → value
    private final Map<String, TreeMap<Integer, String>> store = new HashMap<>();

    public void set(String key, String value, int timestamp) {
        // Step 2 — create TreeMap on first use; add timestamped value
        store.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
    }

    public String get(String key, int timestamp) {
        // Step 3 — retrieve TreeMap for key
        TreeMap<Integer, String> treeMap = store.get(key);
        if (treeMap == null) {
            return "";
        }
        // Step 3 — largest timestamp <= query time
        Integer ts = treeMap.floorKey(timestamp);
        return (ts == null) ? "" : treeMap.get(ts);
    }
}
```

**Complexity:** set O(log n), get O(log n), Space O(total entries).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 436 Find Right Interval | Ceiling instead of floor — find smallest start ≥ threshold | `starts.ceiling(end)` |
| LC 732 My Calendar III | Count maximum k-booking at any time — `TreeMap<Integer, Integer>` as difference array; `floorEntry` for prefix sum | `map.merge(start, 1, Integer::sum); map.merge(end, -1, Integer::sum);` |
| LC 699 Falling Squares | Track height ranges after each square falls — TreeMap of interval heights; `floorKey` for current height at a position | Coordinate compress; update range height in TreeMap |

## ⚠️ Gotchas — Silent Bug Hall of Fame

### Gotcha 1 — Comparing Sets with `==` instead of `.equals()`

**The bug:**
```java
Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3));
Set<Integer> set2 = new HashSet<>(Arrays.asList(1, 2, 3));

if (set1 == set2) {  // BUG: compares object identity
    System.out.println("Same set");  // NEVER prints
}

if (set1.equals(set2)) {  // CORRECT: compares content
    System.out.println("Same elements");  // Prints
}
```

**Why it breaks:**
- `==` checks if they're the same object in memory (identity).
- `.equals()` checks if they have the same elements (value equality).

**Prevention:**
- Always use `.equals()` to compare set content.

✅ **Good:**
```java
if (set1.equals(set2)) {
    System.out.println("Same elements");
}
```

---

### Gotcha 2 — TreeSet Comparator Not Consistent with `.equals()`

**The bug:**
```java
class Person {
    String name;
    int age;
    Person(String name, int age) { this.name = name; this.age = age; }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof Person && ((Person)o).name.equals(this.name);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
}

TreeSet<Person> set = new TreeSet<>((a, b) -> a.age - b.age);
set.add(new Person("Alice", 25));
set.add(new Person("Bob", 25));  // Same age, but different names

// TreeSet thinks Alice == Bob (same age by Comparator)
// But Person.equals() says they're different (different names)
// → INCONSISTENCY: Bob gets rejected!
```

**Why it breaks:**
- TreeSet's Comparator governs whether duplicates are accepted.
- But if Comparator's equality (age 25 == age 25) doesn't match `.equals()` (Alice ≠ Bob), you get inconsistent behavior.

**Prevention:**
- Ensure your TreeSet Comparator respects the `.equals()` contract.
- If Comparator says two people are equal, `.equals()` must too.
- Use a tie-breaker:

✅ **Good:**
```java
TreeSet<Person> set = new TreeSet<>((a, b) -> {
    if (a.age != b.age) {
        return a.age - b.age;
    }
    // Tie-breaker: compare by name if ages equal
    return a.name.compareTo(b.name);
});
set.add(new Person("Alice", 25));
set.add(new Person("Bob", 25));  // Now both are added (different names)
```

---

### Gotcha 3 — Forgetting to Implement Comparable or Provide Comparator for TreeSet

**The bug:**
```java
class Student {
    String name;
    int id;
    Student(String name, int id) { this.name = name; this.id = id; }
    // NO compareTo() method
}

TreeSet<Student> set = new TreeSet<>();  // ERROR at runtime
set.add(new Student("Alice", 1));  // Throws ClassCastException
```

**Why it breaks:**
- TreeSet needs to know how to order elements.
- If you don't provide a Comparator in the constructor, TreeSet expects elements to implement Comparable (i.e., have a `.compareTo()` method).
- Without it, you get a ClassCastException at the first `.add()` call.

**Prevention:**
- Either implement Comparable:

✅ **Good (Option 1 — Implement Comparable):**
```java
class Student implements Comparable<Student> {
    String name;
    int id;
    Student(String name, int id) { this.name = name; this.id = id; }
    
    @Override
    public int compareTo(Student other) {
        return this.id - other.id;
    }
}

TreeSet<Student> set = new TreeSet<>();
set.add(new Student("Alice", 1));  // Works
```

- Or provide a Comparator in the constructor:

✅ **Good (Option 2 — Provide Comparator):**
```java
class Student {
    String name;
    int id;
    Student(String name, int id) { this.name = name; this.id = id; }
}

TreeSet<Student> set = new TreeSet<>((a, b) -> a.id - b.id);
set.add(new Student("Alice", 1));  // Works
```

---

## 🗺️ Practice Plan (in tiers)

### Tier 1 — HashSet Membership Basics ⭐

- ✅ LC 217 Contains Duplicate — simple HashSet membership check
- ✅ LC 349 Intersection of Two Arrays — set intersection
- ✅ LC 1672 Richest Customer Wealth — use HashSet to track unique accounts

### Tier 2 — LinkedHashSet for Order

- ✅ LC 2670 Find the Distinct Difference Array — distinct elements in order
- 🟡 **Try After Tier 1** — LC 706 Design HashMap (custom implementation)

### Tier 3 — TreeSet Sorted Unique Elements ⭐

- ✅ LC 1710 Maximum Units on a Truck — sort by custom field
- ✅ LC 1859 Sorting the Sentence — sort by numeric suffix
- 🟡 **Try After Tier 3** — LC 2530 Maximal Score After Applying K Operations (TreeSet with custom order)

### Tier 4 — TreeSet Range Queries ⭐

- 🟡 **Try After Tier 3** — LC 436 Find Right Interval (ceiling/floor on TreeSet)
- 🟡 **Try After Tier 4** — LC 1825 Finding MK Average (sorted window with subSet)
- 🔴 **Reference Only** — LC 1157 Online Majority Element In Subarray (range queries + bit tricks)

---

## 🧾 TL;DR — One-Page Summary

**Mental Model:**
- HashSet = HashMap without values. O(1) membership, no order.
- TreeSet = TreeMap without values. O(log n) membership, sorted, range queries.
- LinkedHashSet = HashSet + insertion order. O(1) membership, insertion order preserved.

**4 Core Patterns:**
1. Membership Check — "Is X in the set?" HashSet O(1), TreeSet O(log n)
2. Deduplication + Order — Remove duplicates; use LinkedHashSet to preserve insertion order
3. Sorted Unique — Need sorted elements? TreeSet auto-sorts
4. Range Queries — "Get all elements in [5, 10)"? Only TreeSet can do this with `.subSet()`

**3 Silent Bugs:**
1. `set1 == set2` → use `.equals()` instead (compares content, not identity)
2. TreeSet Comparator inconsistent with `.equals()` → add tie-breaker
3. Forgot Comparable on TreeSet → provide Comparator in constructor or implement Comparable

**When to Use Each:**

| Need | Use | Why NOT |
| --- | --- | --- |
| Fast membership? | HashSet | TreeSet is slower (O(log n)) |
| Sorted elements? | TreeSet | HashSet has no order |
| Membership + insertion order? | LinkedHashSet | HashSet loses order |
| Range [5, 10)? | TreeSet | HashSet can't query ranges |

**Complexity Summary:**
- HashSet: O(1) avg add/contains/remove
- TreeSet: O(log n) add/contains/remove, O(log n + k) range queries
- LinkedHashSet: O(1) add/contains/remove (same as HashSet + linked list overhead)

---

## 🔗 Cross-References

- **HashMap counterpart:** `DSA/DeepDive/hashmaps-fundamentals.md` — key-value patterns, frequency maps, prefix sum patterns
- **Foundation context:** `DSA/Reference/collections-quick-reference.md` — when to use each collection type
- **Interview playbooks:** (future) `DSA/Interview/arrays-and-hashing.md` — pattern-to-problem mapping

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **Deep dive rewritten with Pattern Application Gallery.** Now covers HashSet (backed by HashMap, O(1) membership), TreeSet (backed by TreeMap, O(log n), sorted, range queries), LinkedHashSet (insertion order). 4 core patterns with problem motivation structure (problem → naive approach → why pattern solves it → steps → code → drill). Pattern Application Gallery with 3-4 most-asked problems per pattern (selective naive approaches for Membership Check and Range Queries patterns where understanding justification is critical). 3 worked walkthroughs (LC 217, LC 349, LC 436). 3 gotchas with ❌/✅ code examples. 4-tier practice plan. Curriculum alignment (Striver, LeetCode editorials, GeeksforGeeks). |
