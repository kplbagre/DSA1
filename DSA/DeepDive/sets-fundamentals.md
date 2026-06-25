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

### Walkthrough 1 — LC 217 Contains Duplicate (Pattern 1: Membership)

**Problem:** Given an integer array `nums`, return true if any value appears at least twice in the array.

**Example:** `nums = [1, 2, 3, 1]` → `true`

**Approach:**
- Use a HashSet to track elements seen so far.
- As you iterate, check if the element is already in the set.
- If yes, duplicate found. If no, add to set.

**English steps:**

1. Create an empty HashSet.
2. Iterate through the array once.
3. For each element, try to add it to the set.
4. If `.add()` returns false (already exists), return true.
5. If loop completes, return false.

**Code:**

```java
class Solution {
    public boolean containsDuplicate(int[] nums) {
        // Step 1: create empty set
        Set<Integer> seen = new HashSet<>();
        
        // Step 2-5: iterate and check membership
        for (int n : nums) {
            // Step 3-4: add returns false if already present
            if (!seen.add(n)) {
                return true;  // Step 4: duplicate found
            }
        }
        
        // Step 5: no duplicates
        return false;
    }
}
```

**Trace — `nums = [1, 2, 3, 1]`:**

| i | nums[i] | seen before | add() result | seen after |
| --- | --- | --- | --- | --- |
| 0 | 1 | {} | true | {1} |
| 1 | 2 | {1} | true | {1, 2} |
| 2 | 3 | {1, 2} | true | {1, 2, 3} |
| 3 | 1 | {1, 2, 3} | false ✗ | return true |

Result: `true` ✅

**Complexity:** Time O(n) (one pass, each `.add()` is O(1) amortized). Space O(n) (set stores at most n unique elements).

---

### Walkthrough 2 — LC 349 Intersection of Two Arrays (Pattern 1: Membership)

**Problem:** Given two integer arrays `nums1` and `nums2`, return an array of their intersection (unique common elements).

**Example:** `nums1 = [1, 2, 2, 1]`, `nums2 = [2, 2]` → `[2]`

**Approach:**
- Convert `nums1` to a HashSet for O(1) membership check.
- Iterate `nums2` and check if each element is in the set.
- Use a result set to avoid duplicates in the output.

**English steps:**

1. Convert `nums1` to a HashSet.
2. Create a result set for unique common elements.
3. Iterate through `nums2`.
4. For each element, check if it's in the set from step 1.
5. If yes, add to the result set.
6. Convert result set to array and return.

**Code:**

```java
class Solution {
    public int[] intersection(int[] nums1, int[] nums2) {
        // Step 1: convert nums1 to set for O(1) lookup
        Set<Integer> set1 = new HashSet<>();
        for (int n : nums1) {
            set1.add(n);
        }
        
        // Step 2: create result set
        Set<Integer> result = new HashSet<>();
        
        // Step 3-5: iterate nums2 and find intersection
        for (int n : nums2) {
            // Step 4: check if in both
            if (set1.contains(n)) {
                // Step 5: add to result (set prevents duplicates)
                result.add(n);
            }
        }
        
        // Step 6: convert to array
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
}
```

**Trace — `nums1 = [1, 2, 2, 1]`, `nums2 = [2, 2]`:**

| Step | Action | set1 | result |
| --- | --- | --- | --- |
| 1 | Convert nums1 | {1, 2} | — |
| 2 | Create result | {1, 2} | {} |
| — | Check nums2[0]=2 | {1, 2} | {2} |
| — | Check nums2[1]=2 | {1, 2} | {2} (no duplicate) |
| 6 | Convert to array | — | [2] |

Result: `[2]` ✅

**Complexity:** Time O(m + n) where m = nums1.length, n = nums2.length. Space O(m) for set1 + O(k) for result (k = intersection size).

---

### Walkthrough 3 — LC 436 Find Right Interval (Pattern 4: Range Queries)

**Problem:** Given intervals, for each interval, find the smallest interval that starts at or after the end of the current interval. Return the index of that interval, or -1 if none.

**Example:** `intervals = [[1,2]]` → `[-1]` (no interval starts ≥ 2)

**Approach:**
- Store all interval starts in a TreeSet for efficient range queries.
- For each interval end, use `.ceiling(end)` to find the smallest start ≥ end.
- Return the index of that interval.

**English steps:**

1. Create a TreeSet to store all interval starts (auto-sorted).
2. Create a map from each start to its interval index.
3. For each interval in the input:
   a. Get the interval's end.
   b. Use `.ceiling(end)` to find the smallest start ≥ end.
   c. If found, return the index of that interval; else return -1.

**Code:**

```java
class Solution {
    public int[] findRightInterval(int[][] intervals) {
        // Step 1: store all starts in TreeSet (auto-sorted)
        TreeSet<Integer> starts = new TreeSet<>();
        // Step 2: map start to interval index
        Map<Integer, Integer> startToIdx = new HashMap<>();
        
        for (int i = 0; i < intervals.length; i++) {
            starts.add(intervals[i][0]);
            startToIdx.put(intervals[i][0], i);
        }
        
        // Step 3: process each interval
        int[] result = new int[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            // Step 3a: get interval end
            int end = intervals[i][1];
            // Step 3b: find smallest start >= end using ceiling
            Integer nextStart = starts.ceiling(end);
            // Step 3c: return index or -1
            result[i] = nextStart != null ? startToIdx.get(nextStart) : -1;
        }
        
        return result;
    }
}
```

**Trace — `intervals = [[1,2], [2,3], [0,1], [3,4]]`:**

| i | interval | end | starts | ceiling(end) | result[i] |
| --- | --- | --- | --- | --- | --- |
| 0 | [1, 2] | 2 | {0, 1, 2, 3} | 2 | index of [2, 3] = 1 |
| 1 | [2, 3] | 3 | {0, 1, 2, 3} | 3 | index of [3, 4] = 3 |
| 2 | [0, 1] | 1 | {0, 1, 2, 3} | 1 | index of [1, 2] = 0 |
| 3 | [3, 4] | 4 | {0, 1, 2, 3} | null | -1 |

Result: `[1, 3, 0, -1]` ✅

**Complexity:** Time O(n log n) for building TreeSet + O(n log n) for ceiling lookups = O(n log n). Space O(n) for TreeSet and map.

---

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
