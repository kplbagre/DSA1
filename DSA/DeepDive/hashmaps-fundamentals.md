# HashMaps — Fundamentals

> **For interview prep:** Master hash tables, collision handling, and HashMap patterns. You'll solve frequency-counting and complement-lookup problems under pressure.

---

> **Curriculum alignment:** This deep-dive synthesizes:
> - **Striver's Hash Map & Hash Set Series** (8+ videos covering hash function, collisions, open/closed hashing, frequency maps, two-sum, group anagrams, prefix sum, subarray patterns)
> - **LeetCode Problem Editorials** (LC 1 Two Sum, LC 49 Group Anagrams, LC 560 Subarray Sum, LC 347 Top K Frequent Elements, LC 387 First Unique Character)
> - **GeeksforGeeks hash table fundamentals** (hash function design, load factor, rehashing, separate chaining vs linear probing)
>
> **Credit:** Hash collision strategies (chaining, open addressing) from Striver + GeeksforGeeks. Problem-driven examples from LeetCode editorials. Patterns, mental models, and interview context are this doc's contribution.

---

## 🎯 Why You're Reading This

After this deep dive, you will:

- **Understand why HashMap is O(1)** — hash function, buckets, collision handling, load factor, when worst-case O(n) happens
- **Master 6 core patterns** covering ~80% of HashMap DSA problems: frequency maps, complement lookup, canonical form grouping, prefix sum counting, two-pass for order, and custom grouping
- **Recognize problems that demand each pattern** — walkthroughs + gallery show you the decision tree
- **Understand the gap between naive and optimal** — why brute-force fails, how HashMap fixes it
- **Know what breaks your code** — 5 silent bugs that pass compilation

By the end, LC 1 (Two Sum), LC 49 (Group Anagrams), and LC 560 (Subarray Sum) will feel like natural applications, not magic tricks.

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered so far | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs a later section in this doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc | Read problem + editorial for awareness; don't attempt cold |

---

## 🌲 What Is a HashMap?

A **HashMap** is a data structure that stores **key-value pairs** and answers *"what value is stored for this key?"* in **O(1) average time** (not O(log n) like a sorted array, not O(n) like a list).

**Simplest example:**

```java
Map<String, Integer> ages = new HashMap<>();
ages.put("Alice", 25);
ages.get("Alice");  // 25 — instant lookup
```

**Why it matters:** You could iterate a list (O(n)), or binary-search a sorted array (O(log n)), or use TreeMap (O(log n)). HashMap says: *"Hash the key to a bucket index, jump there, find the value in O(1)."*

---

## 📖 Terminology Table

| Term | Meaning | Interview context |
| --- | --- | --- |
| **Hash function** | Converts a key into an array index: `hashCode() % tableSize` | "A good hash spreads keys evenly; a bad one clusters" |
| **Bucket** | A slot in the hash table array (may contain multiple entries if collisions occur) | "Collision = iterate the bucket chain = slow lookup" |
| **Collision** | Two keys hash to the same bucket | "If many keys collide, HashMap becomes O(n) in the chain" |
| **Load factor** | `size / capacity`. When > 0.75, rehash to a larger table | "Rehashing is O(n) but happens rarely → O(1) amortized per insert" |
| **Rehashing** | Creating a larger table and re-inserting all entries | "Old buckets → new bucket indices under new table size" |
| **Chaining** | Collision resolution: store multiple entries in the same bucket as a linked list | "Java's HashMap uses chaining (or red-black trees in Java 8+)" |
| **Canonical form** | A normalized representation (e.g., sorted string) to group equivalent items | "Group anagrams by sorting: 'abc' and 'cab' both → 'abc'" |
| **Prefix sum** | Running total as you iterate: `prefixSum[i] = sum(nums[0..i])` | "Map prefix sums to count subarrays summing to target" |
| **Equals/hashCode contract** | If `.equals()` returns true, both must have the same `.hashCode()` | "Violate this → HashMap breaks → lose data" |

---

## 🧠 Mental Model — Phonebook vs. Hash Table

**Phonebook analogy:**

Imagine a traditional phonebook (sorted by name, binary search O(log n)):
- To find "Alice," open the book to the middle, compare, narrow down. Takes ~log(1M) = 20 comparisons.

**Now imagine a HashMap (hash-based lookup O(1)):**
- Convert "Alice" to a bucket number using a hash function.
- Jump directly to that bucket (like randomly opening the book to a page and there's Alice).
- If bucket is empty or has only one entry, you're done. O(1).
- If bucket has multiple entries (collision), scan the small list. Still O(1) on average because collisions are rare.

**Why collisions are rare:** A good hash function spreads keys evenly across buckets. If you have a 1M-entry table and 1M keys, each bucket has ~1 entry on average. Even if you collide, scanning a chain of 5-10 entries is still faster than iterating 1M items.

**Rehashing when full:** When load factor exceeds 0.75, the table doubles in size, and all keys are re-hashed. This costs O(n) for one resize, but happens rarely (after 1M inserts, you might resize only ~20 times), so amortized cost is O(1) per insert.

### 🎨 Visual — Hash Table Collision & Rehashing

```
Initial HashMap: size=4, capacity=4, load factor=0.75 threshold

┌─────────────┐
│ bucket 0    │ ← empty
│ bucket 1    │ → [("Alice", 25)]
│ bucket 2    │ → [("Charlie", 35)]
│ bucket 3    │ → [("Bob", 30), ("David", 28)] ← collision
└─────────────┘

Load factor = 3/4 = 0.75 (at threshold) → REHASH to size=8

After rehashing (new hash function with % 8):

┌──────────────────┐
│ bucket 0         │ → [("Alice", 25)]
│ bucket 1         │ ← empty
│ bucket 2         │ → [("Charlie", 35)]
│ bucket 3         │ ← empty
│ bucket 4         │ ← empty
│ bucket 5         │ → [("David", 28)]
│ bucket 6         │ ← empty
│ bucket 7         │ → [("Bob", 30)]
└──────────────────┘

Result: Collisions resolved, buckets sparse, faster lookups.
Load factor = 4/8 = 0.5 (healthy again)

KEY INVARIANT: Sparse buckets = fast lookups. Rehashing maintains sparseness.
               Cost of one rehash is O(n), but happens ~log(final size) times.
               Amortized cost per insert = O(1).
```

---

## 🎨 Style Habits — Build These From Day 1

> Some habits apply to **every problem you write**. Others only click when you encounter specific patterns. **Master the universal ones now**; skim context-specific and revisit when you hit the pattern.

---

### 🌐 Universal Habits (apply everywhere)

#### Habit 1 — Use `.getOrDefault()` instead of `.get()` + null-check

**Why:** `.get()` returns `null` if key is absent. `.getOrDefault(key, defaultValue)` eliminates null-checks.

```java
// ❌ Wrong — three lines of noise
if (freq.containsKey(n)) {
    freq.put(n, freq.get(n) + 1);
} else {
    freq.put(n, 1);
}

// ✅ Right — one line
freq.put(n, freq.getOrDefault(n, 0) + 1);

// ✅ Even better — use .merge()
freq.merge(n, 1, Integer::sum);
```

---

#### Habit 2 — Seed your HashMap with base cases

**Why:** Algorithms often depend on an "empty" or "before-start" state. Pre-insert it so the loop doesn't special-case it.

```java
// ❌ Wrong — special case inside loop
Map<Integer, Integer> prefix = new HashMap<>();
int sum = 0, count = 0;
for (int n : nums) {
    sum += n;
    if (sum == k) count++;  // special case: subarray from index 0
    if (prefix.containsKey(sum - k)) count += prefix.get(sum - k);
    prefix.put(sum, prefix.getOrDefault(sum, 0) + 1);
}

// ✅ Right — seed the base case
Map<Integer, Integer> prefix = new HashMap<>();
prefix.put(0, 1);  // base case: empty prefix (sum 0) exists once
int sum = 0, count = 0;
for (int n : nums) {
    sum += n;
    count += prefix.getOrDefault(sum - k, 0);  // uniform logic
    prefix.put(sum, prefix.getOrDefault(sum, 0) + 1);
}
```

---

#### Habit 3 — Iterate `.entrySet()`, not `.keySet()` + `.get()`

**Why:** `.entrySet()` gives you key AND value in one object. `.keySet()` forces a second O(1) lookup per iteration.

```java
// ❌ Wrong — extra lookup inside loop
for (Integer key : map.keySet()) {
    Integer value = map.get(key);  // second lookup
    // use both
}

// ✅ Right — entry has both
for (Map.Entry<Integer, Integer> e : map.entrySet()) {
    Integer key = e.getKey();
    Integer value = e.getValue();
    // use both
}
```

---

#### Habit 4 — Choose between `.put()`, `.putIfAbsent()`, `.computeIfAbsent()` intentionally

**When to use each:**

| Method | Use when | Example |
| --- | --- | --- |
| `.put(k, v)` | Always set the value (overwrite if exists) | `map.put("name", "Alice")` |
| `.putIfAbsent(k, v)` | Only insert if key is missing | Checking: `if (map.putIfAbsent(k, 0) == null) { }` |
| `.computeIfAbsent(k, fn)` | Compute value lazily only if missing | `map.computeIfAbsent(k, key -> new ArrayList<>()).add(item)` |

```java
// ❌ Wrong — overwrites unnecessarily
Map<Integer, List<Integer>> groups = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    int key = nums[i] % 3;
    if (!groups.containsKey(key)) {
        groups.put(key, new ArrayList<>());
    }
    groups.get(key).add(i);
}

// ✅ Right — lazy initialization
Map<Integer, List<Integer>> groups = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    int key = nums[i] % 3;
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
}
```

---

### 🔧 Context-Specific Habits (will click when you encounter these patterns)

#### Habit 5 — Use `int[26]` for fixed domains, HashMap for unbounded keys

**When:** Character frequency counting.

**Why:** `int[26]` is faster (no hashing), uses less memory, no collisions.

```java
// ✅ For lowercase English letters (26 only)
int[] freq = new int[26];
for (char c : s.toCharArray()) {
    freq[c - 'a']++;
}

// ❌ Don't use HashMap for this
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.put(c, freq.getOrDefault(c, 0) + 1);
}

// ✅ Use HashMap only for unbounded domains
Map<Integer, Integer> freq = new HashMap<>();  // arbitrary integers
for (int n : nums) {
    freq.put(n, freq.getOrDefault(n, 0) + 1);
}
```

---

#### Habit 6 — Canonical form to group equivalent items

**When:** Group anagrams, permutations, or items that are "the same" under some transformation.

**Idea:** A canonical form is a unique normalized representation. Two equivalent items always map to the same key.

```java
// Canonical form: sort characters
String s = "listen";
char[] c = s.toCharArray();
Arrays.sort(c);
String key = new String(c);  // "eilnst"
// "listen", "enlist", "silent" all → key "eilnst"

// Use it in grouping
Map<String, List<String>> groups = new HashMap<>();
for (String word : words) {
    char[] c = word.toCharArray();
    Arrays.sort(c);
    String key = new String(c);
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
}
```

---

#### Habit 7 — Prefix sum in HashMap to count subarrays

**When:** "Count subarrays that sum to k" or similar aggregate questions.

**Pattern:** Store prefix sums (not array elements), then ask "did I see a prefix sum that would complete the target?"

```java
// Map prefix sums to count subarrays summing to k
Map<Integer, Integer> prefix = new HashMap<>();
prefix.put(0, 1);  // base case

int sum = 0, count = 0;
for (int n : nums) {
    sum += n;
    // If (current_sum - k) was seen, add those counts
    count += prefix.getOrDefault(sum - k, 0);
    // Record current prefix sum
    prefix.put(sum, prefix.getOrDefault(sum, 0) + 1);
}
return count;
// Rearranged: prefix[i] - prefix[j] = k → prefix[j] = prefix[i] - k
```

---

> **Quick recap of the 4 universal habits:** `.getOrDefault()` eliminates null-checks → seed base cases eliminates special cases → iterate `.entrySet()` avoids second lookup → choose the right insert method for lazy initialization. Those four cover ~90% of HashMap code.

---

## 🧭 Patterns — 6 Core HashMap Techniques

---

### Pattern 1: Frequency Map

**When you'll see this pattern:**

- LC 242 Valid Anagram — are two strings anagrams? (same character frequencies)
- LC 387 First Unique Character — which char appears exactly once?
- LC 169 Majority Element — which element appears > n/2 times?
- LC 451 Sort Characters by Frequency — sort by frequency count

**Problem motivation — concrete example:**

"Given an array of integers, find the element that appears most frequently."

Example: `nums = [1, 2, 2, 3, 3, 3, 4, 4, 4, 4]`  
Output: `4` (appears 4 times)

**Naive approach (and why it fails):**

```java
// Brute: for each element, count occurrences by scanning entire array
// Time: O(n²) — n elements, each requires n scans
// Space: O(1)
// Problem: On LC 347 (n=10⁵) → 10¹⁰ operations → TLE
```

**Why this pattern solves it:**

Build a frequency map in one pass: `O(n)`. Then query the map in `O(1)` per lookup. Total: `O(n)` instead of `O(n²)`.

**Key insight:** "Store counts as you iterate. The map answers 'how many times?' in O(1) per query."

**Steps in plain English:**

1. **Create a HashMap** mapping `element → count`.
2. **Iterate the input**, updating counts with `.getOrDefault()` or `.merge()`.
3. **Query the map** to answer frequency-based questions (max frequency, all elements with count == 1, etc.).

```java
public int findMostFrequent(int[] nums) {
    // Step 1 — create frequency map
    Map<Integer, Integer> freq = new HashMap<>();

    // Step 2 — build the map
    for (int n : nums) {
        freq.merge(n, 1, Integer::sum);
    }

    // Step 3 — query to find max
    int maxFreq = 0;
    int mostFrequent = -1;
    for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
        if (e.getValue() > maxFreq) {
            maxFreq = e.getValue();
            mostFrequent = e.getKey();
        }
    }

    return mostFrequent;
}
```

**Why this works:** One pass to build; one pass to query. No redundant scans.

---

> 🧩 **Drill — do this NOW before reading further:**
> On a blank notepad, write code to check if two strings are anagrams using a frequency map. Don't peek.
>
> Compare with the ✅ version below. Did you build both maps and compare them?

---

### Pattern 2: Complement Lookup (Two Sum Strategy)

**When you'll see this pattern:**

- LC 1 Two Sum — find two numbers that sum to target
- LC 167 Two Sum II (sorted array) — two pointers variant (different pattern)
- LC 15 3Sum — extend two-sum with sorting + two pointers
- LC 454 4Sum II — hash first half, lookup in second half

**Problem motivation — concrete example:**

"Given an array of integers and a target sum, find two elements that add up to target. Return their indices."

Example: `nums = [2, 7, 11, 15]`, `target = 9`  
Output: `[0, 1]` (nums[0] + nums[1] = 2 + 7 = 9)

**Naive approach (and why it fails):**

```java
// Brute: nested loop, check every pair
// Time: O(n²) — for each element, check all others
// Space: O(1)
// Problem: On LC 1 (n=10⁶) → 10¹² operations → TLE
```

**Why this pattern solves it:**

Instead of checking every pair, ask: "For each number, have I already seen its complement?" O(n) pass.

**Key insight:** "Rearrange the goal: if nums[i] + nums[j] = target, then nums[j] = target - nums[i]. Store nums as we go, then look up the complement."

**Steps in plain English:**

1. **Initialize empty HashMap** storing `value → index`.
2. **Iterate through the array**. For each number:
   - Compute the complement: `target - current`.
   - Check if complement is already in the map.
   - If yes, return the pair. If no, store current value.

```java
public int[] twoSum(int[] nums, int target) {
    // Step 1 — initialize map
    Map<Integer, Integer> map = new HashMap<>();

    // Step 2 — iterate and lookup
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];

        // Check if complement was seen
        if (map.containsKey(complement)) {
            return new int[]{ map.get(complement), i };
        }

        // Store current value for future lookups
        map.put(nums[i], i);
    }

    return new int[]{-1, -1};  // no pair found
}
```

**Why this works:** Each number is visited once (O(n)); each map lookup is O(1). Total O(n).

---

> 🧩 **Drill — do this NOW before reading further:**
> On a blank notepad, write code to find if two numbers in an array sum to target (return boolean, not indices). Don't peek.
>
> Compare with ✅ below. Did you use a HashSet? Did you check complement **before** inserting?

---

### Pattern 3: Canonical Form (Group Anagrams)

**When you'll see this pattern:**

- LC 49 Group Anagrams — group words that are anagrams
- LC 205 Isomorphic Strings — strings follow same character mapping
- LC 290 Word Pattern — word sequence matches character pattern
- Anywhere equivalence classes are needed (permutations, transformations)

**Problem motivation — concrete example:**

"Given a list of strings, group together all anagrams. An anagram is a rearrangement of letters."

Example: `strs = ["listen", "silent", "enlist", "hello", "helo"]`  
Output: `[["listen", "silent", "enlist"], ["hello"], ["helo"]]`

**Naive approach (and why it fails):**

```java
// Brute: for each string, compare with all others to check if anagrams
// Time: O(n² * m log m) — n strings, each compared with n others,
//                        each comparison sorts m characters
// Space: O(1)
// Problem: On LC 49 (n=10⁴ strings) → 10⁸ * log comparisons → TLE
```

**Why this pattern solves it:**

Use a canonical form (sorted characters) as the key. All anagrams hash to the same key. Group in O(n * m log m): sort once per string, group in O(1).

**Key insight:** "Two anagrams are 'the same' when sorted. Use sorted form as the key. HashMap groups them automatically."

**Steps in plain English:**

1. **For each string, compute its canonical form** (sort characters).
2. **Use canonical form as the HashMap key**.
3. **Store all strings with the same canonical form in a list**.
4. **Return all lists**.

```java
public List<List<String>> groupAnagrams(String[] strs) {
    // Step 1-2: canonical form as key
    Map<String, List<String>> groups = new HashMap<>();

    for (String s : strs) {
        // Compute canonical form: sort characters
        char[] c = s.toCharArray();
        Arrays.sort(c);
        String key = new String(c);

        // Step 3: store in list under canonical key
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }

    // Step 4: return all lists
    return new ArrayList<>(groups.values());
}
```

**Why this works:** Sorting is O(m log m) per string. Hashing and grouping are O(1) per string. Total: O(n * m log m).

---

> 🧩 **Drill — do this NOW:**
> On a blank notepad, write code to check if two strings are isomorphic using a canonical form approach (hint: character mapping, not sorting). Don't peek.

---

### Pattern 4: Prefix Sum in HashMap

**When you'll see this pattern:**

- LC 560 Subarray Sum Equals K — count subarrays with sum = k
- LC 525 Contiguous Array — count longest subarray with equal 0s and 1s
- LC 974 Subarray Sums Divisible by K — count subarrays divisible by k
- Anywhere cumulative counts matter

**Problem motivation — concrete example:**

"Given an array of integers and a target sum k, count how many contiguous subarrays sum to exactly k."

Example: `nums = [1, 2, 1, 2, 1]`, `k = 3`  
Output: `4` (subarrays: [1,2] at (0,1), [2,1] at (1,2), [1,2] at (2,3), [2,1] at (3,4))

**Naive approach (and why it fails):**

```java
// Brute: for each starting position, compute all ending sums
// Time: O(n²) — n starting positions, each requires n sums
// Space: O(1)
// Problem: On LC 560 (n=10⁴) → 10⁸ operations → TLE
```

**Why this pattern solves it:**

Use prefix sums + HashMap. Instead of checking every subarray, ask: "Has the complement prefix sum appeared?" O(n) pass.

**Key insight:** "subarray_sum[i..j] = prefix[j] - prefix[i]. Rearrange: prefix[i] = prefix[j] - k. Store all prefix sums in a map and count matches."

**Steps in plain English:**

1. **Seed the map** with the empty prefix (sum 0, seen once).
2. **Iterate the array**, computing prefix sum at each step.
3. **For each prefix sum, check if (current_sum - k) was seen**.
4. **If yes, add the count**. Then record the current prefix sum.

```java
public int subarraySumK(int[] nums, int k) {
    // Step 1: seed with empty prefix
    Map<Integer, Integer> prefix = new HashMap<>();
    prefix.put(0, 1);  // empty prefix (sum 0) exists once

    int sum = 0;
    int count = 0;

    // Step 2-4: iterate and count
    for (int n : nums) {
        sum += n;

        // Step 3: check if complement was seen
        count += prefix.getOrDefault(sum - k, 0);

        // Record current prefix
        prefix.put(sum, prefix.getOrDefault(sum, 0) + 1);
    }

    return count;
}
```

**Why this works:** Each prefix sum is computed once (O(n)); each map lookup is O(1). Total O(n).

---

> 🧩 **Drill — do this NOW:**
> On a blank notepad, write code to find the longest subarray with equal 0s and 1s using prefix sums (hint: treat 1 as +1, 0 as -1; find subarrays with sum 0). Don't peek.

---

### Pattern 5: Two-Pass for Order (Restore IP, Reconstruct Itinerary)

**When you'll see this pattern:**

- LC 332 Reconstruct Itinerary — construct a flight path from tickets
- LC 993 Cousins in Binary Tree — find relationships in tree
- Problems where you need to iterate, count, then reconstruct order

**Problem motivation — concrete example:**

"Given a list of flight tickets (from, to), reconstruct the itinerary starting from 'JFK'. You must use every ticket exactly once."

Example: `tickets = [["JFK","SFO"], ["JFK","ATL"], ["SFO","ATL"], ["ATL","JFK"], ["ATL","SFO"]]`  
Output: `["JFK","ATL","JFK","SFO","ATL","SFO"]` (valid path using all tickets)

**Naive approach (and why it fails):**

```java
// Brute: backtrack through all permutations of tickets
// Time: O(n!) — permutations of n tickets
// Space: O(n)
// Problem: On LC 332 (n=10⁴) → 10⁴! permutations → TLE
```

**Why this pattern solves it:**

Build a graph (HashMap mapping source → list of destinations). Use a specific traversal (Hierholzer's algorithm) to find the Eulerian path in O(n) time.

**Key insight:** "Count out-degree for each node. Traverse using DFS, building the path in reverse. HashMap tracks available destinations."

**Steps in plain English:**

1. **Build a graph** (HashMap: source → list of destinations).
2. **For each source, track available destinations** (count how many times each destination can be used).
3. **Use DFS to traverse**, removing used edges as you go.
4. **Build the path in reverse** (Hierholzer's algorithm detail).

(Full code shown in walkthroughs below.)

---

### Pattern 6: Custom Grouping (Word Frequency, Window Frequency)

**When you'll see this pattern:**

- LC 442 Find All Duplicates in Array — find elements appearing > 1 time
- LC 438 Find All Anagrams in a String — sliding window with frequency map
- LC 1400 Construct K Palindrome Strings — group characters by frequency
- Anywhere you need grouping beyond simple counts

**Problem motivation — concrete example:**

"Given a string and a pattern, find all indices where an anagram of the pattern starts in the string."

Example: `s = "abab"`, `p = "ab"`  
Output: `[0, 1, 2]` (anagrams at indices 0, 1, 2)

**Naive approach (and why it fails):**

```java
// Brute: for each window, compare with pattern's character frequencies
// Time: O(n * m) — n windows, each sort/comparison is O(m log m)
// Space: O(m)
// Problem: O(n log m) is acceptable but can optimize further
```

**Why this pattern solves it:**

Use sliding window + frequency map. Maintain pattern frequency, slide window, compare frequencies in O(26) = O(1) (fixed alphabet).

**Key insight:** "Two strings have same character frequencies iff their frequency maps are equal. Use a window to slide and compare maps in O(1) (fixed alphabet size)."

---

## 🔬 Worked Walkthroughs (Deep Dives)

---

### Walkthrough 1: Two Sum (LC 1)

> **Problem:** Given an array of integers and a target integer, return the indices of the two numbers that add up to target. You may assume each input has exactly one solution, and you cannot use the same element twice.

**Steps in plain English:**

1. Create a HashMap to store `value → index`.
2. Iterate through the array.
3. For each number, compute the complement: `target - current`.
4. Check if complement exists in the map.
5. If yes, return both indices. If no, store current value and continue.

```java
class Solution {
    public int[] twoSum(int[] nums, int target) {
        // Step 1 — initialize map
        Map<Integer, Integer> map = new HashMap<>();

        // Step 2-5 — iterate and lookup
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];

            // Step 4 — check if complement was seen
            if (map.containsKey(complement)) {
                return new int[]{ map.get(complement), i };
            }

            // Step 5 — store current value with index
            map.put(nums[i], i);
        }

        // No solution found (shouldn't happen per problem)
        return new int[]{-1, -1};
    }
}
```

**Why this works:** By storing numbers as you go, you check if each number's complement was already seen. This is O(n) instead of O(n²).

**Trace (nums = [2, 7, 11, 15], target = 9):**

```
i=0: nums[0]=2, complement=9-2=7
     map is empty, store {2 → 0}
     
i=1: nums[1]=7, complement=9-7=2
     map contains 2 → return [0, 1]
```

---

### Walkthrough 2: Group Anagrams (LC 49)

> **Problem:** Given an array of strings, group together all anagrams. Return the groups.

**Steps in plain English:**

1. Create a HashMap to store `canonical_form → list_of_anagrams`.
2. For each string, compute its canonical form (sort characters).
3. Add the string to the list under the canonical form.
4. Return all lists.

```java
class Solution {
    public List<List<String>> groupAnagrams(String[] strs) {
        // Step 1 — initialize map
        Map<String, List<String>> groups = new HashMap<>();

        // Step 2-3 — process each string
        for (String s : strs) {
            // Compute canonical form (sorted characters)
            char[] c = s.toCharArray();
            Arrays.sort(c);
            String key = new String(c);

            // Add string to the list under canonical form
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        // Step 4 — return all lists
        return new ArrayList<>(groups.values());
    }
}
```

**Why this works:** Anagrams have identical sorted character sequences. By grouping on sorted form, you avoid comparing every pair.

**Trace (strs = ["listen", "silent", "hello"]):**

```
"listen": sort → "eilnst", store under key "eilnst"
"silent": sort → "eilnst", store under key "eilnst" (same key, anagrams grouped!)
"hello":  sort → "ehllo",  store under key "ehllo"

Result: {
  "eilnst" → ["listen", "silent"],
  "ehllo"  → ["hello"]
}
```

---

### Walkthrough 3: Subarray Sum Equals K (LC 560)

> **Problem:** Given an array of integers and an integer k, return the total number of continuous subarrays whose sum equals k.

**Steps in plain English:**

1. Seed the HashMap with `(0 → 1)` representing the empty prefix.
2. Iterate the array, maintaining running sum.
3. For each position, check if `(current_sum - k)` was seen before.
4. If yes, add the count (those prefix sums complete subarrays summing to k).
5. Record the current prefix sum in the map.

```java
class Solution {
    public int subarraySum(int[] nums, int k) {
        // Step 1 — seed with empty prefix
        Map<Integer, Integer> prefix = new HashMap<>();
        prefix.put(0, 1);

        int sum = 0;
        int count = 0;

        // Step 2-5 — iterate and count
        for (int n : nums) {
            sum += n;

            // Step 3-4 — check if complement was seen
            count += prefix.getOrDefault(sum - k, 0);

            // Step 5 — record current prefix
            prefix.put(sum, prefix.getOrDefault(sum, 0) + 1);
        }

        return count;
    }
}
```

**Why this works:** Instead of checking all subarrays (O(n²)), you use the identity: `subarray_sum[i..j] = prefix[j] - prefix[i]`. Rearranging: `prefix[i] = prefix[j] - k`. You store all prefix sums and count matches in O(n).

**Trace (nums = [1, 2, 1], k = 3):**

```
Initial: prefix = {0 → 1}, sum = 0, count = 0

i=0: n=1
  sum = 0 + 1 = 1
  check: sum - k = 1 - 3 = -2 (not in map, count += 0)
  record: prefix = {0 → 1, 1 → 1}

i=1: n=2
  sum = 1 + 2 = 3
  check: sum - k = 3 - 3 = 0 (in map with count 1, count += 1)
    → found subarray [1, 2] (indices 0-1)
  record: prefix = {0 → 1, 1 → 1, 3 → 1}

i=2: n=1
  sum = 3 + 1 = 4
  check: sum - k = 4 - 3 = 1 (in map with count 1, count += 1)
    → found subarray [2, 1] (indices 1-2)
  record: prefix = {0 → 1, 1 → 1, 3 → 1, 4 → 1}

Return count = 2
```

---

## 🎯 Pattern Application Gallery

> For each pattern, here are 3-4 additional problems showing how to apply the pattern. Read the insight, understand the structure, then try the problem on LeetCode.

---

### Pattern 1: Frequency Map — Gallery

**Problem 1a: LC 242 Valid Anagram**

**Problem:** Check if two strings are anagrams (same characters, same counts).

**The insight:** Build frequency maps for both strings; compare them directly using `.equals()`.

**Structure:**
```java
// Frequency map approach:
Map<Character, Integer> freq1 = new HashMap<>();
for (char c : s1.toCharArray()) {
    freq1.put(c, freq1.getOrDefault(c, 0) + 1);
}
Map<Character, Integer> freq2 = new HashMap<>();
for (char c : s2.toCharArray()) {
    freq2.put(c, freq2.getOrDefault(c, 0) + 1);
}
return freq1.equals(freq2);  // compare maps directly
```

**Time:** O(n + m), **Space:** O(1) (fixed alphabet, max 26 chars)

---

**Problem 1b: LC 387 First Unique Character**

**Problem:** Find the first character in a string that appears exactly once (appears at only one position).

**The insight:** Build frequency map, then iterate string again. Return first char with count == 1.

**Structure:**
```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.put(c, freq.getOrDefault(c, 0) + 1);
}
for (char c : s.toCharArray()) {
    if (freq.get(c) == 1) {
        return s.indexOf(c);  // or track index in first pass
    }
}
return -1;
```

**Time:** O(n), **Space:** O(1) (fixed alphabet)

---

**Problem 1c: LC 169 Majority Element**

**Problem:** Find the element appearing more than n/2 times. Guaranteed to exist.

**The insight:** Build frequency map. Return element with count > n/2.

**Structure:**
```java
Map<Integer, Integer> freq = new HashMap<>();
for (int n : nums) {
    freq.put(n, freq.getOrDefault(n, 0) + 1);
}
int majority = nums.length / 2;
for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
    if (e.getValue() > majority) {
        return e.getKey();
    }
}
```

**Time:** O(n), **Space:** O(n)

---

**Problem 1d: LC 451 Sort Characters by Frequency**

**Problem:** Given a string, sort characters by their frequency (descending). Characters with same frequency can be in any order.

**The insight:** Build frequency map. Sort by frequency (use bucket sort or sort the entries). Reconstruct string.

**Structure:**
```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.put(c, freq.getOrDefault(c, 0) + 1);
}
// Option 1: Use bucket sort (O(n) extra space)
// Option 2: Sort entries by frequency (O(k log k) where k = unique chars)
List<Map.Entry<Character, Integer>> sorted = new ArrayList<>(freq.entrySet());
sorted.sort((a, b) -> b.getValue() - a.getValue());
StringBuilder result = new StringBuilder();
for (Map.Entry<Character, Integer> e : sorted) {
    for (int i = 0; i < e.getValue(); i++) {
        result.append(e.getKey());
    }
}
return result.toString();
```

**Time:** O(n + k log k) where k = unique chars, **Space:** O(k)

---

### Pattern 2: Complement Lookup — Gallery

**Problem 2a: LC 167 Two Sum II (Input Array Is Sorted)**

**Problem:** Find two numbers in a **sorted** array that sum to target. Return 1-indexed positions.

**Naive approach (and why it fails):**
  Brute: nested loop, check every pair
  Time: O(n²), Space: O(1)
  Why it fails: On large arrays (n=10⁶) → 10¹² operations → TLE

**The insight:** Since array is sorted, use two pointers instead of HashMap. This is more efficient (O(1) space).

**Structure (two-pointer variant, not HashMap):**
```java
int left = 0, right = nums.length - 1;
while (left < right) {
    int sum = nums[left] + nums[right];
    if (sum == target) {
        return new int[]{ left + 1, right + 1 };  // 1-indexed
    } else if (sum < target) {
        left++;
    } else {
        right--;
    }
}
return new int[]{-1, -1};
```

**Time:** O(n), **Space:** O(1)

---

**Problem 2b: LC 15 3Sum**

**Problem:** Find all unique triplets in array that sum to 0.

**Naive approach (and why it fails):**
  Brute: three nested loops, check all triplets
  Time: O(n³), Space: O(1)
  Why it fails: On n=3000 → 27B operations → TLE

**The insight:** Sort the array. For each element, use two-sum (via two pointers or HashMap). Skip duplicates.

**Structure (hybrid: sort + HashMap for inner two-sum):**
```java
Arrays.sort(nums);
List<List<Integer>> result = new ArrayList<>();
for (int i = 0; i < nums.length - 2; i++) {
    if (i > 0 && nums[i] == nums[i-1]) continue;  // skip duplicates
    // Two-sum on rest of array
    Map<Integer, Integer> map = new HashMap<>();
    for (int j = i + 1; j < nums.length; j++) {
        int complement = -nums[i] - nums[j];
        if (map.containsKey(complement)) {
            result.add(Arrays.asList(nums[i], complement, nums[j]));
            // Skip duplicate pairs
            while (j + 1 < nums.length && nums[j] == nums[j+1]) j++;
        }
        map.put(nums[j], j);
    }
}
return result;
```

**Time:** O(n² log n) due to sorting + two-sum, **Space:** O(1) if not counting result

---

**Problem 2c: LC 454 4Sum II**

**Problem:** Given 4 arrays, count how many 4-tuples (one from each array) sum to target.

**Naive approach (and why it fails):**
  Brute: four nested loops, check all 4-tuples
  Time: O(n⁴), Space: O(1)
  Why it fails: On n=250 → 250⁴ = 4B operations → TLE

**The insight:** Pair the arrays. Hash all pair sums from arrays 1+2. Count complements in arrays 3+4.

**Structure:**
```java
Map<Integer, Integer> sumMap = new HashMap<>();
// First pass: store all pair sums from nums1 and nums2
for (int i : nums1) {
    for (int j : nums2) {
        int sum = i + j;
        sumMap.put(sum, sumMap.getOrDefault(sum, 0) + 1);
    }
}
// Second pass: count complementary pairs in nums3 and nums4
int count = 0;
for (int k : nums3) {
    for (int l : nums4) {
        int complement = target - k - l;
        if (sumMap.containsKey(complement)) {
            count += sumMap.get(complement);
        }
    }
}
return count;
```

**Time:** O(n²), **Space:** O(n²)

---

### Pattern 3: Canonical Form — Gallery

**Problem 3a: LC 205 Isomorphic Strings**

**Problem:** Two strings are isomorphic if characters in one map to characters in the other 1-to-1.

**The insight:** Use two maps to track char-to-char mapping in both directions. Ensure bijection (one-to-one).

**Structure:**
```java
Map<Character, Character> map1 = new HashMap<>();
Map<Character, Character> map2 = new HashMap<>();
for (int i = 0; i < s.length(); i++) {
    char c1 = s.charAt(i);
    char c2 = t.charAt(i);
    // Check if mapping exists and is consistent
    if (map1.containsKey(c1)) {
        if (map1.get(c1) != c2) return false;
    } else {
        map1.put(c1, c2);
    }
    // Reverse mapping (bijection check)
    if (map2.containsKey(c2)) {
        if (map2.get(c2) != c1) return false;
    } else {
        map2.put(c2, c1);
    }
}
return true;
```

**Time:** O(n), **Space:** O(1) (fixed alphabet size)

---

**Problem 3b: LC 290 Word Pattern**

**Problem:** Given a pattern of chars and a list of words, check if words follow the pattern (bijection).

**The insight:** Same as LC 205, but map chars to words instead of chars to chars.

**Structure:**
```java
String[] words = s.split(" ");
if (pattern.length() != words.length) return false;
Map<Character, String> pToW = new HashMap<>();
Map<String, Character> wToP = new HashMap<>();
for (int i = 0; i < pattern.length(); i++) {
    char p = pattern.charAt(i);
    String w = words[i];
    // Check bijection
    if (pToW.containsKey(p)) {
        if (!pToW.get(p).equals(w)) return false;
    } else {
        pToW.put(p, w);
    }
    if (wToP.containsKey(w)) {
        if (wToP.get(w) != p) return false;
    } else {
        wToP.put(w, p);
    }
}
return true;
```

**Time:** O(n + m) where n = pattern length, m = total word chars, **Space:** O(n)

---

### Pattern 4: Prefix Sum — Gallery

**Problem 4a: LC 525 Contiguous Array**

**Problem:** Find the maximum length subarray with equal count of 0s and 1s.

**Naive approach (and why it fails):**
  Brute: for each subarray, count 0s and 1s
  Time: O(n²), Space: O(1)
  Why it fails: On n=10⁵ → 10¹⁰ operations → TLE

**The insight:** Treat 0 as -1, treat 1 as +1. Find longest subarray with sum = 0 using prefix sum + HashMap.

**Structure:**
```java
Map<Integer, Integer> sumToIndex = new HashMap<>();
sumToIndex.put(0, -1);  // seed: empty prefix at index -1
int sum = 0;
int maxLen = 0;
for (int i = 0; i < nums.length; i++) {
    sum += (nums[i] == 0 ? -1 : 1);  // convert 0 → -1
    if (sumToIndex.containsKey(sum)) {
        // Found a subarray with sum = 0 (equal 0s and 1s)
        maxLen = Math.max(maxLen, i - sumToIndex.get(sum));
    } else {
        sumToIndex.put(sum, i);  // first occurrence of this sum
    }
}
return maxLen;
```

**Time:** O(n), **Space:** O(n)

---

**Problem 4b: LC 974 Subarray Sums Divisible by K**

**Problem:** Count subarrays whose sum is divisible by k.

**Naive approach (and why it fails):**
  Brute: check every subarray's sum
  Time: O(n²), Space: O(1)
  Why it fails: On n=3×10⁴ → 9×10⁸ operations → TLE

**The insight:** Use prefix sum mod k. If two prefix sums have same modulo, their difference is divisible by k.

**Structure:**
```java
Map<Integer, Integer> modCount = new HashMap<>();
modCount.put(0, 1);  // seed: sum 0 has mod 0
int sum = 0;
int count = 0;
for (int n : nums) {
    sum += n;
    int mod = (sum % k + k) % k;  // handle negative mods
    if (modCount.containsKey(mod)) {
        count += modCount.get(mod);
    }
    modCount.put(mod, modCount.getOrDefault(mod, 0) + 1);
}
return count;
```

**Time:** O(n), **Space:** O(k)

---

### Pattern 6: Custom Grouping — Gallery

**Problem 6a: LC 438 Find All Anagrams in a String**

**Problem:** Find all starting indices where an anagram of pattern appears in string s (as a substring).

**The insight:** Build frequency map of pattern. Use sliding window of pattern length. Compare maps at each window position.

**Structure:**
```java
Map<Character, Integer> patternFreq = new HashMap<>();
for (char c : p.toCharArray()) {
    patternFreq.put(c, patternFreq.getOrDefault(c, 0) + 1);
}
Map<Character, Integer> windowFreq = new HashMap<>();
List<Integer> result = new ArrayList<>();
for (int i = 0; i < s.length(); i++) {
    char c = s.charAt(i);
    windowFreq.put(c, windowFreq.getOrDefault(c, 0) + 1);
    // Remove character outside window
    if (i >= p.length()) {
        char old = s.charAt(i - p.length());
        windowFreq.put(old, windowFreq.get(old) - 1);
        if (windowFreq.get(old) == 0) {
            windowFreq.remove(old);
        }
    }
    // Check if window is anagram
    if (patternFreq.equals(windowFreq)) {
        result.add(i - p.length() + 1);
    }
}
return result;
```

**Time:** O(n + m) where n = s.length(), m = p.length(), **Space:** O(1) (fixed alphabet)

---

**Problem 6b: LC 1400 Construct K Palindrome Strings**

**Problem:** Given a string and k, determine if you can construct exactly k palindrome strings using all characters.

**The insight:** Build frequency map. Palindromes need at most 1 char with odd frequency. Count odd frequencies; check if it fits.

**Structure:**
```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.put(c, freq.getOrDefault(c, 0) + 1);
}
// Count characters with odd frequency
int oddCount = 0;
for (int f : freq.values()) {
    if (f % 2 == 1) {
        oddCount++;
    }
}
// Need at least oddCount palindromes, at most s.length()
return oddCount <= k && k <= s.length();
```

**Time:** O(n), **Space:** O(1) (fixed alphabet)

---

## ⚠️ Gotchas — Silent Bug Hall of Fame

---

**Mutable keys in HashMap (most insidious).**

```java
// ❌ Wrong — if key object mutates, hashCode changes, key becomes unfindable
List<Integer> key = Arrays.asList(1, 2, 3);
map.put(key, "value");
key.set(0, 99);  // mutate the key
System.out.println(map.get(key));  // null! key is now lost
```

**Solution:** Use immutable keys (String, Integer, record classes, custom immutable objects). Never mutate a key after inserting it.

---

**Violating the equals/hashCode contract.**

```java
// ❌ Wrong — inconsistent equals and hashCode
class Custom {
    int id;
    
    @Override
    public boolean equals(Object o) {
        return ((Custom) o).id == this.id;
    }
    
    @Override
    public int hashCode() {
        return 42;  // always same hash → all keys collide
    }
}

// Two Custom(1) objects are equal but might hash to same bucket → map breaks
```

**Solution:** If you override `.equals()`, always override `.hashCode()` consistently. Use IDE auto-generate.

---

**Forgetting to seed the map in prefix sum patterns.**

```java
// ❌ Wrong — no base case
Map<Integer, Integer> prefix = new HashMap<>();
int sum = 0, count = 0;
for (int n : nums) {
    sum += n;
    if (sum == k) count++;  // special case: subarray from index 0
    if (prefix.containsKey(sum - k)) count += prefix.get(sum - k);
    prefix.put(sum, prefix.getOrDefault(sum, 0) + 1);
}

// ✅ Right — seed with empty prefix
Map<Integer, Integer> prefix = new HashMap<>();
prefix.put(0, 1);  // empty prefix → no special case
int sum = 0, count = 0;
for (int n : nums) {
    sum += n;
    count += prefix.getOrDefault(sum - k, 0);
    prefix.put(sum, prefix.getOrDefault(sum, 0) + 1);
}
```

---

**Choosing `.get()` + null-check instead of `.getOrDefault()`.**

```java
// ❌ Wrong — redundant, three lines
if (freq.containsKey(n)) {
    freq.put(n, freq.get(n) + 1);
} else {
    freq.put(n, 1);
}

// ✅ Right — one line
freq.put(n, freq.getOrDefault(n, 0) + 1);
```

---

**Iterating `.keySet()` + `.get()` instead of `.entrySet()`.**

```java
// ❌ Wrong — extra lookup inside loop
for (Integer key : map.keySet()) {
    Integer value = map.get(key);  // second O(1) lookup
}

// ✅ Right — one lookup
for (Map.Entry<Integer, Integer> e : map.entrySet()) {
    Integer key = e.getKey();
    Integer value = e.getValue();
}
```

---

## 🗺️ Practice Plan — A Progression That Works

Master HashMap in 4 tiers. **Pace:** 1-2 problems per day. **When stuck:** 25-minute time-box; read editorial if unsolved.

> **Reminder of tags:** ✅ Try Now · 🟡 Try after named prerequisite · 🔴 Reference Only

---

### Tier 1 — Foundational Patterns (must be muscle memory)

Master frequency maps, two sum, and canonical forms. These are the building blocks.

1. ✅ **LC 1 Two Sum** — O(n) time, complement lookup (covered in walkthrough)
2. ✅ **LC 242 Valid Anagram** — O(n) time, frequency map comparison (see gallery)
3. ✅ **LC 49 Group Anagrams** — O(n * m log m) time, canonical form grouping (covered in walkthrough)
4. ✅ **LC 560 Subarray Sum Equals K** — O(n) time, prefix sum counting (covered in walkthrough)

---

### Tier 2 — Frequency Variations (extend Tier 1 with new angles)

Solidify frequency-map intuition with related problems.

1. 🟡 **Try after Tier 1** — **LC 387 First Unique Character** — O(n) time, find first char with count == 1 (see gallery)
2. 🟡 **Try after Tier 1** — **LC 169 Majority Element** — O(n) time, find element appearing > n/2 times (see gallery)
3. 🟡 **Try after Tier 1** — **LC 451 Sort Characters by Frequency** — O(n log n) time, sort by frequency (see gallery)
4. 🟡 **Try after Tier 1** — **LC 347 Top K Frequent Elements** — O(n log k) time, frequency map + min-heap

---

### Tier 3 — Advanced Counting (multi-map, multi-pass)

Combine patterns or use two passes.

1. 🟡 **Try after Tier 2** — **LC 454 4Sum II** — O(n²) time, two HashMaps (one per array pair) (see gallery)
2. 🟡 **Try after Tier 2** — **LC 525 Contiguous Array** — O(n) time, prefix sum with 0 → -1 conversion (see gallery)
3. 🟡 **Try after Tier 2** — **LC 438 Find All Anagrams in a String** — O(n) time, sliding window + frequency map (see gallery)
4. 🔴 **LC 332 Reconstruct Itinerary** — O(n log n) time, graph + Hierholzer's algorithm (complex)

---

### Tier 4 — Reference Only (advanced, multi-concept)

Read editorials; don't attempt cold.

1. 🔴 **LC 974 Subarray Sums Divisible by K** — O(n) time, prefix sum + modular arithmetic (see gallery)
2. 🔴 **LC 290 Word Pattern** — O(n) time, character mapping (two-way bijection) (see gallery)
3. 🔴 **LC 205 Isomorphic Strings** — O(n) time, two-way mapping validation (see gallery)

---

### How to use this plan

- **Pace:** Start with Tier 1. Solve 1-2 per day.
- **When stuck:** 25-minute time-box. If not moving, read editorial (don't copy).
- **Revision:** After finishing a tier, redo problems 1 and 2 from memory.
- **Victory criterion:** You can write LC 1, LC 49, and LC 560 from memory in under 10 minutes each.

---

## 🧾 TL;DR — One-Page Summary

- **HashMap** = key-value pairs, O(1) lookup on average (hash function, buckets, chaining for collisions).
- **Mental model:** Phonebook using hash function to jump directly to bucket instead of binary search.
- **Load factor:** `size / capacity`. When > 0.75, rehash to larger table (O(n) per rehash, rare, so O(1) amortized).
- **6 core patterns:**
  1. **Frequency Map** — count occurrences in one pass, query in O(1)
  2. **Complement Lookup** — rearrange goal, store and check in one pass
  3. **Canonical Form** — normalize equivalent items (sort, modulo, etc.) as key to group
  4. **Prefix Sum** — maintain running sum, use map to count subarrays
  5. **Two-Pass** — first pass counts/builds, second pass reconstructs
  6. **Custom Grouping** — frequency map + window sliding
- **4 universal habits:** `.getOrDefault()` → no null-checks · seed base cases → no special cases · iterate `.entrySet()` → no second lookup · use `.computeIfAbsent()` → lazy initialization
- **5 gotchas:** mutable keys (never mutate after insert) · equals/hashCode contract (must be consistent) · forgetting to seed (prefix sum) · `.get()` + null-check (use `.getOrDefault()`) · iterating `.keySet()` + `.get()` (use `.entrySet()`)
- **Tier 1 you must master:** LC 1 Two Sum, LC 49 Group Anagrams, LC 560 Subarray Sum, LC 242 Valid Anagram
- **Lesson learned:** The gap between naive O(n²) and pattern-based O(n) is huge. HashMap unlocks one-pass solutions.

---

## 🔗 Cross-References

- **For hash function details:** See `DSA/Foundation/java-collections-visual.md` (HashMap backing structure)
- **For Set variants:** See `DSA/DeepDive/sets-fundamentals.md` (HashSet, TreeSet, LinkedHashSet)
- **For similar patterns:** See `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md` (complement patterns with pointers)
- **For prefix sum deep dive:** See `DSA/Interview/arrays-and-hashing.md` (interview playbook with array aggregate patterns)
- **For practice:** See `DSA/Interview/arrays-and-hashing.md` (interview playbook with problem taxonomy)

---

## 📝 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **Complete rewrite with Pattern Application Gallery.** Synthesized Striver's HashMap/HashSet series + LeetCode editorials. Added "When you'll see this pattern" + "Problem motivation" + "Naive approach + complexity" + "Why this pattern solves it" for each of 6 core patterns. Added new "Pattern Application Gallery" section with 3-5 problem sketches per pattern (insight + code structure, not full solutions). 3 complete walkthroughs (LC 1, 49, 560) with traces. Complexity analysis throughout. 4-tier practice plan with ✅/🟡/🔴 tagging. |
