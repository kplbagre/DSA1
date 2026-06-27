# Arrays & Hashing — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to connect array/hashing patterns to problems fast. Not for learning arrays from scratch — for that, see `DSA/DeepDive/arrays-fundamentals.md`.

---

## 🎯 Why You're Reading This

You already know what a HashMap is. You already know what a prefix sum is. What you DON'T know yet is: **"I see this problem — which of the 8 patterns do I reach for?"** This file builds that bridge.

After reading this file, you should be able to:
1. Read a problem statement and identify the pattern in under 30 seconds
2. Write the template from memory and adapt it to the specific problem
3. Handle the follow-up questions interviewers always ask

---

## 🔧 Essential Methods — Know These Cold

| Method | What it does | Used in |
| --- | --- | --- |
| `map.put(key, value)` | Insert or overwrite a key-value pair | Patterns 1, 3 |
| `map.get(key)` | Get value (returns `null` if absent) | Patterns 1, 3 |
| `map.getOrDefault(key, 0)` | Get value or default — avoids null checks | Patterns 3, 5 |
| `map.containsKey(key)` | Check if key exists — O(1) | Pattern 1 |
| `map.merge(key, 1, Integer::sum)` | Increment count in one line (see explanation below) | Patterns 3, 5 |
| `map.computeIfAbsent(key, k -> new ArrayList<>())` | Get value or create-and-store if absent (see explanation below) | Pattern 2 |
| `map.entrySet()` | Returns `Set<Map.Entry<K,V>>` — iterate over key-value pairs | Pattern 5 |
| `entry.getKey()` / `entry.getValue()` | Access key or value from a Map.Entry | Pattern 5 |
| `set.add(element)` | Add to set — returns `false` if already present (useful as boolean check) | Pattern 6 |
| `set.contains(element)` | Check membership — O(1) | Pattern 6 |
| `Arrays.sort(arr)` | Sort array — O(n log n) | Patterns 2, 5 |
| `Arrays.equals(arr1, arr2)` | Compare two arrays element-by-element — O(n) | LC 438 |

> **Full reference:** `../Reference/hashmap-section-updated.md`, `../Reference/set-section-updated.md`

### 🔄 Lambda & Shorthand Explanations with Fallbacks

Every complex method below has a **"🔄 Fallback"** — the plain `if-else` equivalent you can always use if you forget the concise version. In an interview, use whichever comes to mind first.

**1. `map.merge(key, 1, Integer::sum)` — Increment a counter**

```java
// What it does:
//   If key is ABSENT  → put(key, 1)            (insert with value 1)
//   If key is PRESENT → put(key, oldValue + 1)  (add 1 to existing)
//
// Integer::sum is shorthand for the lambda (oldVal, newVal) -> oldVal + newVal
// The "1" is the newVal. merge() calls sum(oldVal, 1) = oldVal + 1
map.merge(key, 1, Integer::sum);

// 🔄 Fallback — if you forget merge(), this ALWAYS works:
map.put(key, map.getOrDefault(key, 0) + 1);
```

**2. `map.computeIfAbsent(key, k -> new ArrayList<>())` — Get-or-create**

```java
// What it does:
//   If key is ABSENT  → create new ArrayList, store it, AND return it
//   If key is PRESENT → just return the existing value
//
// k -> new ArrayList<>() is a lambda: given the key k, produce a new list
// The "k" parameter is the key itself (we don't use it, but Java requires it)
// You can chain .add(s) because it returns the list
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);

// 🔄 Fallback — if you forget computeIfAbsent(), this ALWAYS works:
if (!groups.containsKey(key)) {
    groups.put(key, new ArrayList<>());
}
groups.get(key).add(s);
```

**3. `map.getOrDefault(key, 0)` — Safe get with default**

```java
// What it does:
//   If key EXISTS     → returns the value
//   If key is ABSENT  → returns the default (0) instead of null
int count = map.getOrDefault(key, 0);

// 🔄 Fallback — if you forget getOrDefault():
int count = map.containsKey(key) ? map.get(key) : 0;
```

**4. `map.entrySet()` — Iterate key-value pairs**

```java
// What it does:
//   Returns a Set of Map.Entry objects, each holding one key-value pair
//   Use e.getKey() and e.getValue() to access them
for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
    int element = e.getKey();
    int count = e.getValue();
}

// 🔄 Fallback — if you forget entrySet(), iterate keys and look up values:
for (int key : freq.keySet()) {
    int count = freq.get(key);
}
```

**5. `!set.add(x)` — Add-and-check in one shot**

```java
// What it does:
//   set.add(x) returns TRUE if the element was NEW (added successfully)
//   set.add(x) returns FALSE if the element ALREADY EXISTED (not added)
//   So !set.add(x) means "this element was already in the set" = duplicate!
if (!seen.add(x)) return true;

// 🔄 Fallback — if you forget the return-value trick:
if (seen.contains(x)) return true;
seen.add(x);
```

**6. `new PriorityQueue<>((a, b) -> expression)` — Custom comparator**

```java
// What it does:
//   (a, b) -> expression is a Comparator lambda
//   Return NEGATIVE → a comes first (a is "smaller")
//   Return POSITIVE → b comes first (b is "smaller")
//   Return ZERO     → equal priority
//
// Example: min-heap by frequency, ties broken alphabetically
PriorityQueue<String> pq = new PriorityQueue<>(
    (a, b) -> freq.get(a).equals(freq.get(b))
              ? b.compareTo(a)         // same freq → reverse alphabetical
              : freq.get(a) - freq.get(b)  // different freq → lower freq first
);

// 🔄 Fallback — if comparator lambdas confuse you:
// Just sort a list instead of using a heap:
List<String> words = new ArrayList<>(freq.keySet());
Collections.sort(words, (a, b) -> {
    if (freq.get(a).equals(freq.get(b))) {
        return a.compareTo(b);
    }
    return freq.get(b) - freq.get(a);
});
// Then take the first K elements
```

---

## 🧠 The Mental Model — Three Questions That Pick Your Pattern

When you see an array problem, ask these three questions **in order**:

```
Q1: "Does the problem involve a SUBARRAY (contiguous)?"
    │
    ├── YES → Q2: "Is there a SUM or COUNT condition?"
    │          │
    │          ├── YES, exact sum/count → Prefix Sum + HashMap (Pattern 3)
    │          ├── YES, max/min length  → Sliding Window (Pattern 4)
    │          └── YES, max sum value   → Kadane's (Pattern 5)
    │
    └── NO → Q3: "Do I need to find/group/match elements?"
              │
              ├── "Find a pair/target"       → HashMap Lookup (Pattern 1)
              ├── "Group by some property"   → Canonical Key (Pattern 2)
              ├── "Find duplicates/missing"  → HashSet or Sorting (Pattern 6)
              ├── "Frequency / top-K"        → Frequency Map + Heap/Bucket (Pattern 7)
              └── "Sorted array + pair"      → Two Pointers (see two-pointers file)
```

### 🎨 Visual — The Pattern Selection Funnel

```
┌─────────────────────────────────────────────────────┐
│                  ARRAY PROBLEM                       │
│                                                     │
│  Is it about a CONTIGUOUS SUBARRAY?                 │
│         │                          │                │
│        YES                         NO               │
│         │                          │                │
│    ┌────┴────┐               ┌─────┴──────┐        │
│    │ Sum/cnt │               │ Elements   │        │
│    │condition│               │ matching   │        │
│    └────┬────┘               └─────┬──────┘        │
│         │                          │                │
│  ┌──────┼──────┐          ┌────────┼────────┐      │
│  │      │      │          │        │        │      │
│ Exact  Max/   Max        Find    Group   Freq/     │
│ sum=K  min    sum        pair    by key  top-K     │
│  │     len    │           │        │       │       │
│  ▼      ▼     ▼           ▼        ▼       ▼       │
│ Prefix Slide  Kadane    HashMap  Canon   Bucket    │
│ +Hash  Window            Lookup   Key    Sort      │
└─────────────────────────────────────────────────────┘
```

**KEY INVARIANT:** The first question — "contiguous subarray or not?" — splits the problem into two completely different families. Get this wrong and you'll waste 15 minutes going down the wrong path.

---

## 🧭 Pattern 1: HashMap Lookup (Two Sum Family) ⭐

**What this solves:** Problems where you need to find if an element or its complement exists among previously seen values. Typically involves pairs, sums, or indices where a second linear scan would otherwise be needed.

**Recognition cues — reach for this when:**
- "Find two elements that sum to target"
- "Check if complement exists"
- "Return indices (not just true/false)" — can't sort, need original indices

**Brute force:** Try every pair (i, j) where i < j and check if `nums[i] + nums[j] == target`. O(n²) time, O(1) space.

**Key insight:** For any element x, the complement `target - x` is known immediately. Storing seen elements in a HashMap means checking for the complement costs O(1) — one forward pass replaces the nested loop entirely.

**Steps in plain English:**

1. **Walk the array once** — for each element, check if its complement is already in the map.
2. **If found** — return the answer (indices, values, whatever the problem asks).
3. **If not found** — store the current element in the map for future lookups.

```java
public int[] twoSum(int[] nums, int target) {
    // Step 1 — map: value → index (built as we go)
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];

        // Step 2 — check if complement was seen
        if (map.containsKey(complement)) {
            return new int[]{ map.get(complement), i };
        }

        // Step 3 — store current for future lookups
        map.put(nums[i], i);
    }
    return new int[]{};
}
```

**Why "check THEN insert" matters:** If you insert first, `[3, 3]` with target 6 would match an element with itself. Checking first ensures you only match with a *previous* element.

**Complexity (optimal):** O(n) time, O(n) space — single pass; each HashMap operation is O(1) amortized.

**🏷️ Problems:** LC 1 (Two Sum), LC 167 (Two Sum II — sorted, use two pointers instead), LC 15 (3Sum — sort + two pointers), LC 18 (4Sum).

---

## 🧭 Pattern 2: Canonical Key (Group Anagrams Family) ⭐

**What this solves:** Problems that ask you to group elements that are "equivalent" under some transformation — anagrams, same digits, same remainder. Two elements belong to the same group if they produce the same canonical key.

**Recognition cues — reach for this when:**
- "Group elements by some property"
- "Find all anagrams / permutations"
- "Elements that are equivalent under some transformation"

**Brute force:** Compare every pair of elements to check equivalence (e.g., sort both strings and compare). O(n² × K log K) time, O(nK) space — where K = string length.

**Key insight:** Transform each element into a canonical form (e.g., sorted characters), then use that as a HashMap key. All equivalent elements produce the same key and land in the same group — no pairwise comparison needed.

**Steps in plain English:**

1. **Define the key function** — what makes two elements "the same group"?
2. **Build a map** — key → list of original elements.
3. **Return the groups** — `map.values()`.

```java
public List<List<String>> groupAnagrams(String[] strs) {
    // Step 1 — key = sorted characters (anagrams sort to the same string)
    Map<String, List<String>> groups = new HashMap<>();

    for (String s : strs) {
        // Step 2 — compute canonical key
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);

        // Step 3 — add to group
        // computeIfAbsent: if key absent → create new list & store it; if present → return existing
        // k -> new ArrayList<>() is a lambda: given key k, produce a new empty list
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);

        // 🔄 Fallback if you forget computeIfAbsent:
        // if (!groups.containsKey(key)) groups.put(key, new ArrayList<>());
        // groups.get(key).add(s);
    }

    return new ArrayList<>(groups.values());
}
```

**Common key strategies:**

| What to group | Key strategy | Example |
| --- | --- | --- |
| Anagrams | Sorted char array → String | "eat" → "aet" |
| Anagrams (faster) | `int[26]` frequency → `Arrays.toString()` | "eat" → "[1,0,0,0,1,...]" |
| Same digits | Sort digit string | 123, 321 → "123" |
| Equivalent by modulo | `value % k` | Group by remainder |

**Complexity (optimal):** O(n × K log K) time, O(nK) space — sorting each element once dominates; n = count, K = element length.

**🏷️ Problems:** LC 49 (Group Anagrams), LC 242 (Valid Anagram), LC 438 (Find All Anagrams — sliding window variant).

---

## 🧭 Pattern 3: Prefix Sum + HashMap ⭐

**What this solves:** Problems asking for counts or existence of contiguous subarrays with a specific sum (or sum divisible by K). Works with negative numbers — unlike sliding window, which requires a monotonic property.

**Recognition cues — reach for this when:**
- "Count subarrays with sum equal to K"
- "Find subarray with sum divisible by K"
- "Longest subarray with equal 0s and 1s" (convert 0→-1, then sum = 0)

**Brute force:** Try all pairs of indices (i, j) — compute `sum(nums[i..j])` using nested loops. O(n²) time, O(1) space.

**Key insight:** If `prefix[j] - prefix[i] == K`, the subarray `nums[i..j-1]` sums to K. Storing all prefix sums in a HashMap lets us check for `prefix - K` in O(1) at each index — the second scan becomes a single lookup.

**The mental model:** If two prefix sums differ by exactly K, then the subarray between them sums to K. Store all prefix sums in a map; at each index, look up `(currentPrefix - K)`.

### 🎨 Visual — Why Prefix Sum + HashMap Works

```
Array:      [1,   2,  -2,   3]          K = 3
Index:       0    1    2    3

──────────────────────────────────────────────────
WHY DOES PREFIX SUM HAVE (n + 1) VALUES?

prefix[i] = sum of all elements BEFORE index i

   prefix[0] = 0                          (nothing summed yet)
   prefix[1] = 1                          (sum of nums[0])
   prefix[2] = 1 + 2 = 3                 (sum of nums[0..1])
   prefix[3] = 1 + 2 + (-2) = 1          (sum of nums[0..2])
   prefix[4] = 1 + 2 + (-2) + 3 = 4      (sum of nums[0..3])

   Array:     [ 1,   2,  -2,   3 ]
   Prefix:  0    1    3    1    4
            ↑                         ← 5 values for 4 elements
         "empty prefix"

   The extra 0 at the start = "sum of zero elements."
   Without it, you can't find subarrays starting at index 0.
   In code, we don't build this array — {0: 1} in the map
   IS this extra entry.

──────────────────────────────────────────────────
THE CORE TRICK:

   prefix[j] - prefix[i] == K
   means nums[i..j-1] sums to K

Subarray [1, 2] — uses the {0:1} seed:

   Array:     [ 1,   2,  -2,   3 ]
   Prefix:  0    1    3    1    4
            ↑         ↑
         prefix[0]  prefix[2]

   prefix[2] - prefix[0] = 3 - 0 = 3 = K ✓
   → nums[0..1] = [1, 2] sums to 3 ✓

Subarray [2, -2, 3] — multi-element:

   Array:     [ 1,   2,  -2,   3 ]
   Prefix:  0    1    3    1    4
                 ↑              ↑
              prefix[1]      prefix[4]

   prefix[4] - prefix[1] = 4 - 1 = 3 = K ✓
   → nums[1..3] = [2, -2, 3] sums to 3 ✓

Subarray [3] — ALSO found at index 3:

   Array:     [ 1,   2,  -2,   3 ]
   Prefix:  0    1    3    1    4
                           ↑    ↑
                       prefix[3] prefix[4]

   prefix[4] - prefix[3] = 4 - 1 = 3 = K ✓
   → nums[3..3] = [3] sums to 3 ✓

   ★ KEY INSIGHT: subarrays [2,-2,3] and [3] are BOTH found
   at i=3 because prefix - K = 4 - 3 = 1, and prefix sum 1
   appeared TWICE (at positions 1 and 3).

   The map has {1: 2}, so we add 2 to the answer at once.
   This is WHY we store COUNTS, not just seen/not-seen.

──────────────────────────────────────────────────
STEP-BY-STEP HashMap WALK:

   map = {0: 1}    answer = 0

   i=0  prefix = 1    look for 1 - 3 = -2
        NOT in map → answer stays 0
        map: {0:1, 1:1}

   i=1  prefix = 3    look for 3 - 3 = 0
        FOUND with count 1 → answer += 1 → answer = 1
        → subarray [1, 2] ✓
        map: {0:1, 1:1, 3:1}

   i=2  prefix = 1    look for 1 - 3 = -2
        NOT in map → answer stays 1
        map: {0:1, 1:2, 3:1}
                     ↑
                     prefix 1 now has count 2!

   i=3  prefix = 4    look for 4 - 3 = 1
        FOUND with count ★2★ → answer += 2 → answer = 3
        → TWO subarrays ending here:
           nums[1..3] = [2, -2, 3] = 3 ✓  (1st time prefix=1)
           nums[3..3] = [3]        = 3 ✓  (2nd time prefix=1)
        map: {0:1, 1:2, 3:1, 4:1}

   Answer: 3 subarrays

KEY INVARIANT:
   The map answers: "how many earlier positions had prefix sum X?"
   count = 1 → one subarray ending here sums to K.
   count = 2 → TWO subarrays ending here sum to K.
   The {0: 1} seed catches subarrays starting at index 0.
```

**KEY INVARIANT:** `prefix[j] - prefix[i] == K` means `sum(nums[i..j-1]) == K`. The map stores **how many times** each prefix sum has occurred — not just whether it exists. When a prefix sum appears twice, a single lookup finds two subarrays at once. The initial `{0: 1}` entry represents the empty prefix — without it, subarrays starting at index 0 would be invisible.

**Steps in plain English:**

1. **Initialize** — map with `{0: 1}` (empty prefix has sum 0, seen once).
2. **Walk the array** — maintain running prefix sum.
3. **At each index** — check if `(prefix - K)` exists in the map. If yes, add its count to the answer.
4. **Store current prefix** — increment its count in the map.

```java
public int subarraySum(int[] nums, int k) {
    // Step 1 — empty prefix = sum 0, seen once
    Map<Long, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0L, 1);

    long prefix = 0;
    int answer = 0;

    for (int x : nums) {
        // Step 2 — running prefix
        prefix += x;

        // Step 3 — how many previous prefixes differ by exactly K?
        // getOrDefault: returns count if prefix-k exists, else 0 (avoids null)
        answer += prefixCount.getOrDefault(prefix - k, 0);

        // Step 4 — store current prefix
        // merge: if key absent → put(prefix, 1); if present → put(prefix, old + 1)
        // Integer::sum is shorthand for (oldVal, newVal) -> oldVal + newVal
        prefixCount.merge(prefix, 1, Integer::sum);
        // 🔄 Fallback: prefixCount.put(prefix, prefixCount.getOrDefault(prefix, 0) + 1);
    }
    return answer;
}
```

**Complexity (optimal):** O(n) time, O(n) space — single pass; each HashMap operation is O(1).

**🏷️ Problems:** LC 560 (Subarray Sum Equals K), LC 974 (Subarray Sums Divisible by K), LC 525 (Contiguous Array — convert 0→-1), LC 930 (Binary Subarrays With Sum).

---

## 🧭 Pattern 4: Kadane's Algorithm (Max Subarray Family)

**What this solves:** Finding the maximum (or minimum) sum contiguous subarray. Any problem where you must decide at every position whether to extend the current run or restart fresh.

**Recognition cues — reach for this when:**
- "Maximum sum subarray"
- "Maximum product subarray"
- "Best time to buy and sell stock" (Kadane in disguise — track min price)

**Brute force:** Try all subarrays — for each pair (i, j), compute the sum of `nums[i..j]`. O(n²) time, O(1) space.

**Key insight:** At every position, the optimal decision is purely local: if the running sum is negative, it can only hurt future subarrays — restart from the current element. This local greedy choice is always globally optimal.

**The one decision at every index:** "Do I extend the previous subarray, or start fresh from here?"

**Steps in plain English:**

1. **Initialize** — `currentBest = nums[0]`, `globalBest = nums[0]`.
2. **Walk from index 1** — at each index, `currentBest = max(nums[i], currentBest + nums[i])`.
3. **Update global** — `globalBest = max(globalBest, currentBest)`.

```java
public int maxSubArray(int[] nums) {
    // Step 1 — start with first element
    long current = nums[0];
    long best = nums[0];

    for (int i = 1; i < nums.length; i++) {
        // Step 2 — extend or restart?
        current = Math.max(nums[i], current + nums[i]);

        // Step 3 — update global best
        best = Math.max(best, current);
    }
    return (int) best;
}
```

**Variant — Best Time to Buy and Sell Stock (LC 121):**

This is Kadane's in disguise. Instead of "max subarray sum," think "max profit = max(price - minSoFar)."

```java
public int maxProfit(int[] prices) {
    int minPrice = prices[0];
    int maxProfit = 0;
    for (int i = 1; i < prices.length; i++) {
        maxProfit = Math.max(maxProfit, prices[i] - minPrice);
        minPrice = Math.min(minPrice, prices[i]);
    }
    return maxProfit;
}
```

**Complexity (optimal):** O(n) time, O(1) space — single pass, constant extra space.

**🏷️ Problems:** LC 53 (Maximum Subarray), LC 121 (Best Time to Buy and Sell Stock), LC 152 (Maximum Product Subarray — track both max and min).

---

## 🧭 Pattern 5: Frequency Map + Top-K (Sort vs Bucket Sort)

**What this solves:** Problems that require ranking elements by how often they appear, then selecting or returning the top K most/least frequent. Bucket sort variant achieves O(n) when the frequency range is bounded by input size.

**Recognition cues — reach for this when:**
- "Top K frequent elements"
- "K most common"
- "Sort by frequency"

**Brute force:** Count frequencies with a HashMap, then sort all entries by value (frequency) descending, take first K. O(n log n) time, O(n) space.

**Key insight:** Max possible frequency of any element is `nums.length` — bounded. Using frequency as an array index (bucket sort) skips all comparisons, achieving O(n) with no sorting step.

### Approach 1 — Sort by frequency (first intuition) — O(n log n)

**Steps in plain English:**

1. **Count frequencies** — HashMap.
2. **Sort map entries by value** (frequency) — highest first.
3. **Take first K entries** — those are the top-K elements.

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1 — count frequencies
    Map<Integer, Integer> freq = new HashMap<>();
    for (int x : nums) {
        // merge: if x absent → put(x, 1); if present → put(x, old + 1)
        freq.merge(x, 1, Integer::sum);
        // 🔄 Fallback: freq.put(x, freq.getOrDefault(x, 0) + 1);
    }

    // Step 2 — sort entries by frequency (descending)
    List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(freq.entrySet());
    // Lambda: "compare by value (frequency) — higher frequency first"
    entries.sort((a, b) -> b.getValue() - a.getValue());
    // 🔄 Fallback — anonymous Comparator:
    //   entries.sort(new Comparator<Map.Entry<Integer, Integer>>() {
    //       public int compare(Map.Entry<Integer,Integer> a, Map.Entry<Integer,Integer> b) {
    //           return b.getValue() - a.getValue();
    //       }
    //   });

    // Step 3 — take first K
    int[] result = new int[k];
    for (int i = 0; i < k; i++) {
        result[i] = entries.get(i).getKey();
    }
    return result;
}
```

**Time: O(n log n)** — sorting dominates. Simple to code, easy to remember. If interviewer says "O(n log n) is fine," stop here.

---

### Approach 2 — Bucket sort (optimal) — O(n) 🚀

**What is bucket sort?** (if the interviewer asks)

> Normal sorting compares elements against each other → O(n log n) minimum.
> Bucket sort skips comparisons entirely. Instead, it uses the value as an
> **array index**. If you know values fall in a bounded range [0, maxVal],
> you create an array of that size and drop each element into its slot.
> No comparisons → O(n).
>
> Here, frequency is bounded: the max possible frequency of any element is
> `nums.length` (when the entire array is one repeated number). So we can
> use frequency as an index: `buckets[freq]` = list of elements with that frequency.

### 🎨 Visual — Why bucket size = nums.length + 1

```
Example: nums = [1, 1, 1, 2, 2, 3]    k = 2

Step 1 — freq map:  {1: 3, 2: 2, 3: 1}

Step 2 — create buckets. WHY size = nums.length + 1 = 7?

   Worst case: nums = [5, 5, 5, 5, 5, 5]  (all same)
               freq of 5 = 6 = nums.length
               Need buckets[6] to exist → size = 7

   Index:   0      1      2      3      4      5      6
   Bucket: [ ]    [3]    [2]    [1]    [ ]    [ ]    [ ]
             ↑      ↑      ↑      ↑
          freq=0  freq=1  freq=2  freq=3
          (empty) (3 once)(2 twice)(1 three times)

Step 3 — walk RIGHT to LEFT (high freq → low freq):

   buckets[6]: empty → skip
   buckets[5]: empty → skip
   buckets[4]: empty → skip
   buckets[3]: [1]   → result = [1],    idx = 1
   buckets[2]: [2]   → result = [1, 2], idx = 2 = k → DONE!

   Answer: [1, 2]

KEY INSIGHT:
   No sorting needed! We used frequency as an array index.
   Walking right→left automatically gives highest frequency first.
```

**Steps in plain English:**

1. **Count frequencies** — HashMap.
2. **Create buckets** — array of size `nums.length + 1`. Index = frequency. `buckets[i]` = list of elements appearing `i` times.
3. **Walk buckets from high to low** — collect elements until you have K.

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1 — count frequencies
    Map<Integer, Integer> freq = new HashMap<>();
    for (int x : nums) {
        // merge: if x absent → put(x, 1); if present → put(x, old + 1)
        freq.merge(x, 1, Integer::sum);
        // 🔄 Fallback: freq.put(x, freq.getOrDefault(x, 0) + 1);
    }

    // Step 2 — bucket[i] = list of elements with frequency i
    // Size = nums.length + 1 because max possible frequency = nums.length
    // (when ALL elements are the same, e.g., [5,5,5,5,5] → freq = 5 = length)
    List<Integer>[] buckets = new List[nums.length + 1];
    for (int i = 0; i < buckets.length; i++) {
        buckets[i] = new ArrayList<>();
    }
    // entrySet(): gives key-value pairs. e.getKey() = the number, e.getValue() = its frequency
    for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
        buckets[e.getValue()].add(e.getKey());
    }
    // 🔄 Fallback if you forget entrySet:
    // for (int key : freq.keySet()) { buckets[freq.get(key)].add(key); }

    // Step 3 — collect from highest frequency down
    int[] result = new int[k];
    int idx = 0;
    for (int i = buckets.length - 1; i >= 0 && idx < k; i--) {
        for (int val : buckets[i]) {
            if (idx < k) {
                result[idx++] = val;
            }
        }
    }
    return result;
}
```

**Time: O(n)** — no sorting, just array indexing. This is the optimal answer.

### Interview strategy — say both

> "My first instinct is to sort map entries by frequency — O(n log n).
> But since max frequency is bounded by n, I can use bucket sort:
> use frequency as an array index, walk high→low. That's O(n)."

| | Sort by values | Bucket sort |
| --- | --- | --- |
| **Time** | O(n log n) | O(n) |
| **Space** | O(n) | O(n) |
| **Code difficulty** | Easy — 4 lines | Medium — 10 lines |
| **When to use** | Interviewer says "n log n is fine" | Interviewer pushes for optimal |

---

### Generalized: HashMap Sorting Toolkit

**Sort by keys** — when you need alphabetical / numerical order of keys:

```java
// Option A — TreeMap (auto-sorts keys on insertion)
Map<String, Integer> sorted = new TreeMap<>(originalMap);
// TreeMap: a red-black tree that keeps keys in natural sorted order.
// Lookups are O(log n) instead of O(1) — pay that cost only when you NEED sorted keys.

// Option B — Sort the key list manually
List<String> keys = new ArrayList<>(map.keySet());
Collections.sort(keys);
for (String key : keys) {
    // Access in sorted key order
    int value = map.get(key);
}
```

**Sort by values** — when you need to rank by count / frequency / score:

```java
List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
// Sort descending by value
entries.sort((a, b) -> b.getValue() - a.getValue());
// 🔄 Fallback (no lambda):
// entries.sort(new Comparator<Map.Entry<String, Integer>>() {
//     public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
//         return b.getValue() - a.getValue();
//     }
// });

// Collect into LinkedHashMap if you need a sorted map (preserves insertion order)
Map<String, Integer> sortedByValue = new LinkedHashMap<>();
for (Map.Entry<String, Integer> e : entries) {
    sortedByValue.put(e.getKey(), e.getValue());
}
```

**Decision tree — which approach to use:**

```
"Top K / Most frequent / Rank by count"
│
├── Is K close to N (or need ALL sorted)?
│   └── YES → Sort entries by value → O(n log n)
│       Simple, easy to code, fine for most interviews.
│
├── Is K small relative to N? (e.g., top 5 out of 10,000)
│   └── YES → Min-Heap of size K → O(n log k)
│       PriorityQueue keeps only K elements. Smallest freq
│       on top → gets evicted first → heap holds K largest.
│       See heaps.md Pattern 1.
│
└── Interviewer pushes for O(n)?
    └── Bucket Sort → O(n)
        Frequency as array index, no comparisons.
        See Approach 2 above.
```

| Approach | Time | Space | Best when |
| --- | --- | --- | --- |
| Sort entries by value | O(n log n) | O(n) | Simple, K close to N, interviewer says "n log n is fine" |
| Min-Heap of size K | O(n log k) | O(k) | K is small (top 5 out of 10,000) |
| Bucket Sort | O(n) | O(n) | Need guaranteed linear time, interviewer pushes for optimal |
| TreeMap (sort by keys) | O(n log n) | O(n) | Need keys in sorted order (alphabetical, numerical) |

**Complexity (optimal):** O(n) time, O(n) space — bucket sort; frequency as array index eliminates all comparisons.

**🏷️ Problems:** LC 347 (Top K Frequent Elements), LC 451 (Sort Characters By Frequency), LC 692 (Top K Frequent Words — need heap for lexicographic tie-breaking).

---

## 🧭 Pattern 6: HashSet for Existence / Dedup

**What this solves:** Problems asking whether an element has been seen before, finding duplicates, or detecting membership in O(1). Also used for the "longest consecutive sequence" family where only run-starts matter.

**Recognition cues — reach for this when:**
- "Contains duplicate"
- "Find the duplicate"
- "Longest consecutive sequence"
- Any "have I seen this before?" question

**Brute force:** For each element, scan all previous elements to check for a match or duplicate. O(n²) time, O(1) space. For longest consecutive: sort the array, then walk — O(n log n) time, O(1) space.

**Key insight:** HashSet gives O(1) membership checks. For consecutive sequences: only start counting from a run-beginning (`val - 1` not in set), so each number is visited at most twice — total O(n) instead of O(n²).

**The Longest Consecutive Sequence trick (LC 128):**

Don't just check existence — only start counting from the **beginning of a run** (a value where `val - 1` is NOT in the set). This turns O(n²) into O(n).

```java
public int longestConsecutive(int[] nums) {
    Set<Integer> set = new HashSet<>();
    for (int v : nums) {
        set.add(v);
    }

    int best = 0;
    for (int v : set) {
        // Only start counting if v is the START of a sequence
        if (!set.contains(v - 1)) {
            int len = 1;
            int curr = v;
            while (set.contains(curr + 1)) {
                curr++;
                len++;
            }
            best = Math.max(best, len);
        }
    }
    return best;
}
```

**Complexity (optimal):** O(n) time, O(n) space — one pass building the set, one pass checking run-starts.

**🏷️ Problems:** LC 217 (Contains Duplicate), LC 128 (Longest Consecutive Sequence), LC 36 (Valid Sudoku — set per row/col/box).

---

## 🔬 Canonical Problem — LC 560: Subarray Sum Equals K

> **Problem:** Given an integer array `nums` and an integer `k`, return the total number of subarrays whose sum equals `k`. Subarrays are contiguous.

> **Brute force:** Try all pairs (i, j) — compute `sum(nums[i..j])` using nested loops. O(n²) time, O(1) space.
> **Key insight:** Two prefix sums differing by exactly K mean the subarray between them sums to K. Storing prefix sums in a HashMap converts the inner scan to O(1) lookup at each index.

### Step 1 — Read and identify triggers

"The problem says **subarray** (contiguous) and **sum equals K** (exact count). This triggers **Pattern 3: Prefix Sum + HashMap** because I need to count exact-sum subarrays — sliding window won't work here since values can be negative."

### Step 2 — Choose the template

"I'll use Prefix Sum + HashMap. My map stores `prefixSum → count`. At each index, I check how many previous prefix sums equal `currentPrefix - K`."

### Step 3 — Why can't I use Sliding Window?

**Critical decision:** Sliding Window needs a monotonic property — expanding always increases, shrinking always decreases. With negative numbers, expanding can decrease the sum. So the "shrink when too large" logic breaks. **Prefix Sum + HashMap works regardless of negative numbers.**

### Step 4 — Adapt and code

**Steps in plain English:**

1. **Initialize** — `map = {0: 1}`, `prefix = 0`, `count = 0`.
2. **Walk array** — accumulate prefix sum.
3. **Check** — if `prefix - k` is in the map, add that count.
4. **Store** — put current prefix in the map.

```java
public int subarraySum(int[] nums, int k) {
    Map<Long, Integer> map = new HashMap<>();
    map.put(0L, 1);

    long prefix = 0;
    int count = 0;

    for (int x : nums) {
        prefix += x;
        // getOrDefault: returns value or 0 if key absent
        count += map.getOrDefault(prefix - k, 0);
        // merge: increment count for this prefix sum
        // Integer::sum = (old, new) -> old + new
        map.merge(prefix, 1, Integer::sum);
        // 🔄 Fallback: map.put(prefix, map.getOrDefault(prefix, 0) + 1);
    }
    return count;
}
```

### Step 5 — Verify with example

```
nums = [1, 1, 1], k = 2

i=0: prefix=1, check 1-2=-1 → not in map (0). map={0:1, 1:1}
i=1: prefix=2, check 2-2=0  → in map (1).     count=1. map={0:1, 1:1, 2:1}
i=2: prefix=3, check 3-2=1  → in map (1).     count=2. map={0:1, 1:1, 2:1, 3:1}

Answer: 2 ✅ (subarrays [1,1] at indices 0-1 and 1-2)
```

### Complexity

- **Time:** O(n) — one pass, O(1) map operations
- **Space:** O(n) — map stores up to n prefix sums

---

## ⚡ Problem Bank — Expanded

---

### LC 1: Two Sum

> **Problem:** Given an array and a target, return **indices** of two numbers that add up to target. Exactly one solution exists.

> **Brute force:** Try every pair (i, j) where i < j, check if `nums[i] + nums[j] == target`. O(n²) time, O(1) space.
> **Key insight:** For each element, its complement is `target - nums[i]` — known immediately. A HashMap lookup converts the inner scan to O(1).
> **Approach:** HashMap Lookup. Check if `target - nums[i]` is in the map BEFORE inserting (avoids matching element with itself).

```java
// "Does my complement (target - me) already exist in the map?"
if (map.containsKey(target - nums[i])) {
    // Yes → return both indices (the complement's index + current index)
    return new int[]{ map.get(target - nums[i]), i };
}
// No → store myself so a future element can find ME as its complement
map.put(nums[i], i);
```

**Complexity (optimal):** O(n) time, O(n) space.

---

### LC 49: Group Anagrams

> **Problem:** Given array of strings, group anagrams together. `["eat","tea","tan","ate","nat","bat"]` → `[["eat","tea","ate"],["tan","nat"],["bat"]]`.

> **Brute force:** Compare every pair of strings — sort both and compare. O(n² × K log K) time, O(nK) space.
> **Key insight:** Two strings are anagrams iff they sort to the same string. Sort each string once, use the sorted form as a HashMap key — all anagrams land in the same bucket.
> **Approach:** Sort each string's chars → use as HashMap key. Anagrams sort to the same key.

```java
// Sort each word's characters → anagrams produce the same sorted key
// "eat" → "aet", "tea" → "aet", "bat" → "abt"
char[] chars = s.toCharArray();
Arrays.sort(chars);
String key = new String(chars);
// Group words by their sorted key — all anagrams end up in the same list
// computeIfAbsent: if key absent → create list & store; if present → get existing
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
// 🔄 Fallback:
// if (!groups.containsKey(key)) groups.put(key, new ArrayList<>());
// groups.get(key).add(s);
```

**Complexity (optimal):** O(n × K log K) time, O(nK) space — where n = number of strings, K = max string length.

---

### LC 128: Longest Consecutive Sequence

> **Problem:** Given unsorted array, find length of longest consecutive sequence. `[100,4,200,1,3,2]` → `[1,2,3,4]` → length 4. Must be O(n).

> **Brute force:** Sort the array (O(n log n)), then walk to find the longest run of consecutive values. O(n log n) time, O(1) extra space.
> **Key insight:** Load all values into a HashSet. Only start counting from a run-beginning (`val - 1` absent). Each number is visited at most twice — O(n) total without sorting.
> **Approach:** HashSet + only start from run-beginning (`val-1` not in set). Walk forward counting.

```java
// Only start counting from the BEGINNING of a sequence
// If v-1 exists, then v is not the start — skip it (someone else will count this run)
if (!set.contains(v - 1)) {
    int len = 1, curr = v;
    // Walk forward: v, v+1, v+2, ... as long as next number exists
    while (set.contains(curr + 1)) {
        curr++;
        len++;
    }
    best = Math.max(best, len);
}
```

**Complexity (optimal):** O(n) time, O(n) space — one pass to build set, one pass checking run-starts.

---

### LC 217: Contains Duplicate

> **Problem:** Return true if any value appears at least twice in the array.

> **Brute force:** Compare every pair (i, j). O(n²) time, O(1) space.
> **Key insight:** `set.add(x)` returns false if x was already present — detecting duplicate and inserting in one O(1) operation.
> **Approach:** HashSet — `add()` returns false if element already exists.

```java
// !seen.add(x): add returns FALSE if element already existed → duplicate found!
for (int x : nums) { if (!seen.add(x)) return true; }
// 🔄 Fallback: if (seen.contains(x)) return true; seen.add(x);
```

**Complexity (optimal):** O(n) time, O(n) space.

---

### LC 347: Top K Frequent Elements

> **Problem:** Given array and `k`, return the `k` most frequent elements. `[1,1,1,2,2,3], k=2` → `[1,2]`.

> **Brute force:** Count frequencies, sort all entries by value descending, take first K. O(n log n) time, O(n) space.
> **Key insight:** Max frequency ≤ n — bounded range. Use frequency as an array index (bucket sort), walk high→low. No comparisons needed → O(n).
> **Approach 1 (O(n log n)):** Count frequencies → sort entries by value descending → take first K. Simple, works if interviewer is fine with n log n.

> **Approach 2 (O(n)) 🚀:** Count frequencies → bucket sort. `buckets[freq]` = list of elements with that frequency. Walk buckets high→low. See Pattern 5 for full code + visual.

```java
// Approach 1 — sort by frequency
List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(freq.entrySet());
// Lambda: "sort by value descending — most frequent first"
entries.sort((a, b) -> b.getValue() - a.getValue());
// 🔄 Fallback: Comparator with b.getValue() - a.getValue()

// Approach 2 — bucket sort (optimal)
// entrySet: iterate over key-value pairs. getKey() = number, getValue() = frequency
for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
    buckets[e.getValue()].add(e.getKey());
}
// 🔄 Fallback: for (int key : freq.keySet()) buckets[freq.get(key)].add(key);
```

**Complexity (optimal):** O(n) time, O(n) space — bucket sort; no comparison-based sorting.

---

### LC 560: Subarray Sum Equals K

> **Problem:** Count contiguous subarrays whose sum equals `k`. Values can be negative. `[1,1,1], k=2` → 2 subarrays.

> **Brute force:** Try all subarrays (i, j) and compute their sums. O(n²) time, O(1) space.
> **Key insight:** `prefix[j] - prefix[i] == k` means subarray `nums[i..j-1]` sums to k. HashMap lookup for `prefix - k` converts the second scan to O(1).
> **Approach:** Prefix Sum + HashMap. Two prefixes differing by K → subarray between them sums to K. Init map with `{0:1}`.

```java
// Add current element to running prefix sum
prefix += x;
// How many earlier prefixes had value (prefix - k)?
// Each one = a subarray ending HERE that sums to k
count += map.getOrDefault(prefix - k, 0);
// Record this prefix sum so future indices can find it
// merge: if key absent → put(prefix, 1); if present → put(prefix, old + 1)
map.merge(prefix, 1, Integer::sum);
// 🔄 Fallback: map.put(prefix, map.getOrDefault(prefix, 0) + 1);
```

**Complexity (optimal):** O(n) time, O(n) space.

---

### LC 525: Contiguous Array

> **Problem:** Given binary array of 0s and 1s, find max length subarray with **equal** number of 0s and 1s. `[0,1,0]` → 2.

> **Brute force:** Try all subarrays (i, j), count 0s and 1s in each. O(n²) time, O(1) space.
> **Key insight:** Convert 0→-1. "Equal 0s and 1s" becomes "subarray sum = 0." Two identical prefix sums → subarray between them sums to 0. Store first occurrence of each prefix sum to maximize length.
> **Approach:** Convert 0→-1, then "equal 0s and 1s" = "subarray sum = 0." Prefix Sum + HashMap storing `prefix → first index` (want longest, not count).

```java
// Convert 0 → -1 so "equal 0s and 1s" becomes "subarray sum = 0"
prefix += (nums[i] == 0) ? -1 : 1;
// Same prefix seen before? The subarray between then and now sums to 0
// i - map.get(prefix) = length of that subarray
if (map.containsKey(prefix)) {
    best = Math.max(best, i - map.get(prefix));
} else {
    // Store FIRST occurrence only (we want the longest subarray)
    map.put(prefix, i);
}
```

**Complexity (optimal):** O(n) time, O(n) space.

---

### LC 238: Product of Array Except Self

> **Problem:** Return array where `result[i]` = product of all elements except `nums[i]`. No division allowed. O(n).

> **Brute force:** For each index, multiply all other elements. O(n²) time, O(1) space.
> **Key insight:** `result[i]` = (product of everything left of i) × (product of everything right of i). Compute each half in a separate pass — no division needed, O(n) total.
> **Approach:** Two-pass. Left pass builds prefix product. Right pass multiplies by suffix product.

```java
// Left pass: result[i] = product of everything LEFT of index i
result[0] = 1;
for (int i = 1; i < n; i++) {
    result[i] = result[i - 1] * nums[i - 1];
}
// Right pass: multiply each result[i] by product of everything RIGHT of i
int right = 1;
for (int i = n - 2; i >= 0; i--) {
    right *= nums[i + 1];
    // Now result[i] = leftProduct * rightProduct = product except self
    result[i] *= right;
}
```

**Complexity (optimal):** O(n) time, O(1) extra space (output array not counted).

---

### LC 36: Valid Sudoku

> **Problem:** Determine if a 9×9 board is valid. Each row, column, and 3×3 box must contain digits 1-9 without repetition. Only check filled cells.

> **Brute force:** For each filled cell, scan all other cells in the same row, column, and 3×3 box for duplicates. O(81²) = O(1) for fixed grid, but conceptually O(n²) per cell.
> **Key insight:** Encode each constraint (row/col/box + digit) as a unique string. One `set.add()` call per constraint — returns false on duplicates. Single pass over 81 cells with O(1) per cell.
> **Approach:** Single HashSet storing encoded strings. For each filled cell, generate 3 strings (one for row, col, box). If `set.add()` returns false → duplicate → invalid. The trick: `r/3` and `c/3` map any cell to its 3×3 box (integer division: rows 0-2 → box row 0, rows 3-5 → box row 1, rows 6-8 → box row 2).

```java
Set<String> seen = new HashSet<>();
for (int r = 0; r < 9; r++) {
    for (int c = 0; c < 9; c++) {
        char v = board[r][c];
        // Skip empty cells
        if (v == '.') {
            continue;
        }
        // Try to add 3 encoded strings to the set:
        //   "r0-5" means "row 0 already has digit 5"
        //   "c3-5" means "col 3 already has digit 5"
        //   "b0,1-5" means "box (0,1) already has digit 5"
        // set.add() returns FALSE if the string was already there → duplicate!
        if (!seen.add("r" + r + "-" + v)
            || !seen.add("c" + c + "-" + v)
            || !seen.add("b" + r / 3 + "," + c / 3 + "-" + v)) {
            return false;
        }
        // r/3, c/3 maps cell to its 3×3 box:
        //   rows 0-2, cols 0-2 → box (0,0)
        //   rows 0-2, cols 3-5 → box (0,1)
        //   rows 3-5, cols 0-2 → box (1,0) ... etc.
    }
}
return true;
```

**Complexity (optimal):** O(1) time, O(1) space — fixed 9×9 grid, constant iterations.

---

### LC 121: Best Time to Buy and Sell Stock

> **Problem:** Array of stock prices. Buy on one day, sell on a later day. Maximize profit. `[7,1,5,3,6,4]` → buy at 1, sell at 6 → profit 5.

> **Brute force:** Try all pairs (buy day, sell day) where buy < sell. O(n²) time, O(1) space.
> **Key insight:** The best profit from selling on day i is `prices[i] - minPriceSoFar`. Track the running minimum — one pass handles both the buy and sell decisions simultaneously.
> **Approach:** Kadane variant. Track min price so far, compute `price - minPrice` at each step.

```java
// "If I sold today, what profit would I get?" (today's price minus cheapest so far)
maxProfit = Math.max(maxProfit, prices[i] - minPrice);
// Update cheapest price seen so far (potential buy day)
minPrice = Math.min(minPrice, prices[i]);
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 167: Two Sum II — Input Array Is Sorted

> **Problem:** Given a **sorted** array and a target, return indices (1-indexed) of two numbers that add up to target. Exactly one solution. Example: `numbers = [2,7,11,15], target = 9` → `[1,2]`.

> **Brute force:** Try all pairs (i, j) where i < j. O(n²) time, O(1) space.
> **Key insight:** The array is sorted — if sum is too large, the right element must decrease; if too small, the left element must increase. Converging pointers eliminate all non-matching pairs in one pass with O(1) space.
> **Approach:** NOT HashMap — array is sorted, so use **two pointers** (converging). Left at start, right at end. Sum too big → right--. Sum too small → left++. O(1) space. See `two-pointers-and-sliding-window.md` Pattern 1 for full template.

```java
int lo = 0, hi = numbers.length - 1;
while (lo < hi) {
    int sum = numbers[lo] + numbers[hi];
    // Found it → return 1-indexed positions (problem says 1-indexed)
    if (sum == target) {
        return new int[]{lo + 1, hi + 1};
    } else if (sum < target) {
        lo++;     // Sum too small → move left pointer right to increase sum
    } else {
        hi--;     // Sum too big → move right pointer left to decrease sum
    }
}
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 15: 3Sum

> **Problem:** Find all unique triplets `[a, b, c]` in the array such that `a + b + c = 0`. No duplicate triplets. Example: `nums = [-1,0,1,2,-1,-4]` → `[[-1,-1,2],[-1,0,1]]`.

> **Brute force:** Try all triples (i, j, k). O(n³) time, O(1) space.
> **Key insight:** Sort the array. Fix one element — the remaining two-sum on the sorted subarray is solvable in O(n) with converging pointers. Total: O(n²).
> **Approach:** Sort the array. Fix one element, then run two-pointer on the rest. Skip duplicates at both levels. O(n²) time. See `two-pointers-and-sliding-window.md` for full solution.

```java
// Sort first — enables two-pointer AND duplicate skipping
Arrays.sort(nums);
for (int i = 0; i < nums.length - 2; i++) {
    // Skip duplicate values for first element (avoids duplicate triplets)
    // i > 0 check prevents out-of-bounds on first iteration
    if (i > 0 && nums[i] == nums[i - 1]) {
        continue;
    }
    // Fix nums[i], then find two numbers in [i+1..end] that sum to -nums[i]
    int lo = i + 1, hi = nums.length - 1;
    // Two-pointer inner loop (same as Two Sum II on sorted array)
}
```

**Complexity (optimal):** O(n²) time, O(1) extra space (output not counted).

---

### LC 18: 4Sum

> **Problem:** Find all unique quadruplets `[a, b, c, d]` that sum to `target`. Example: `nums = [1,0,-1,0,-2,2], target = 0` → `[[-2,-1,1,2],[-2,0,0,2],[-1,0,0,1]]`.

> **Brute force:** Try all quadruples (i, j, k, l). O(n⁴) time, O(1) space.
> **Key insight:** Same reduction as 3Sum — sort, fix two elements with nested loops, then run converging two-pointer on the rest. Reduces O(n⁴) → O(n³).
> **Approach:** Same idea as 3Sum but with one more outer loop. Sort → fix two elements → two-pointer on the rest. O(n³) time. Skip duplicates at every level.

```java
Arrays.sort(nums);
// Fix first element
for (int i = 0; i < n - 3; i++) {
    // Skip duplicate first element
    if (i > 0 && nums[i] == nums[i - 1]) {
        continue;
    }
    // Fix second element
    for (int j = i + 1; j < n - 2; j++) {
        // Skip duplicate second element (j > i+1 prevents skipping first valid j)
        if (j > i + 1 && nums[j] == nums[j - 1]) {
            continue;
        }
        // Two-pointer on [j+1..end] for remaining sum = target - nums[i] - nums[j]
    }
}
```

**Complexity (optimal):** O(n³) time, O(1) extra space (output not counted).

---

### LC 242: Valid Anagram

> **Problem:** Given two strings `s` and `t`, return true if `t` is an anagram of `s`. Example: `s = "anagram", t = "nagaram"` → `true`.

> **Brute force:** Sort both strings and compare character by character. O(n log n) time, O(n) space.
> **Key insight:** An `int[26]` frequency array is a fixed-size fingerprint of any lowercase string. Increment for s, decrement for t — all zeros at the end means identical frequencies. O(n) time, O(1) space.
> **Approach:** Frequency array `int[26]`. Increment for `s`, decrement for `t`. If all zeros at end → anagram. O(n) time, O(1) space.

```java
// freq[0] = count of 'a', freq[1] = count of 'b', ... freq[25] = count of 'z'
int[] freq = new int[26];
// c - 'a' converts character to index: 'a'→0, 'b'→1, ..., 'z'→25
for (char c : s.toCharArray()) {
    freq[c - 'a']++;    // Increment for each char in s
}
for (char c : t.toCharArray()) {
    freq[c - 'a']--;    // Decrement for each char in t
}
// If all counts are 0, both strings have identical character frequencies → anagram
for (int f : freq) {
    if (f != 0) {
        return false;
    }
}
return true;
```

**Complexity (optimal):** O(n) time, O(1) space — `int[26]` is constant size.

---

### LC 438: Find All Anagrams in a String

> **Problem:** Given strings `s` and `p`, find all start indices of `p`'s anagrams in `s`. Example: `s = "cbaebabacd", p = "abc"` → `[0, 6]`.

> **Brute force:** For every start index in s, extract a substring of length `p.length()`, sort it, compare with sorted p. O(n × K log K) time where K = p.length().
> **Key insight:** A fixed-size sliding window of length `p.length()` maintains an `int[26]` frequency array. Add incoming char, remove outgoing char — O(1) per slide. `Arrays.equals()` on `int[26]` is O(26) = O(1).
> **Approach:** Fixed-size sliding window of length `p.length()`. Maintain frequency array for the window. When window freq matches `p` freq → add start index. See `two-pointers-and-sliding-window.md` for sliding window patterns.

```java
// pFreq = target character counts, wFreq = current window's character counts
int[] pFreq = new int[26], wFreq = new int[26];
// Build target frequency from pattern p
for (char c : p.toCharArray()) {
    pFreq[c - 'a']++;
}
for (int i = 0; i < s.length(); i++) {
    // EXPAND window: add the new rightmost character
    wFreq[s.charAt(i) - 'a']++;
    // SHRINK window: once window exceeds p's length, remove leftmost character
    // s.charAt(i - p.length()) is the char that just fell off the left edge
    if (i >= p.length()) {
        wFreq[s.charAt(i - p.length()) - 'a']--;
    }
    // If window freq matches pattern freq → anagram found!
    // i - p.length() + 1 = start index of this window
    if (Arrays.equals(pFreq, wFreq)) {
        result.add(i - p.length() + 1);
    }
}
```

**Complexity (optimal):** O(n) time, O(1) space — `int[26]` is constant size.

---

### LC 53: Maximum Subarray (Kadane's)

> **Problem:** Find the contiguous subarray with the largest sum. Example: `nums = [-2,1,-3,4,-1,2,1,-5,4]` → `6` (subarray `[4,-1,2,1]`).

> **Brute force:** Try all subarrays — compute the sum of each. O(n²) time, O(1) space.
> **Key insight:** At every position, the locally optimal choice — extend if running sum is positive, restart if negative — is also globally optimal. No need to try all start positions.
> **Approach:** Kadane's algorithm. Track `currentSum` — extend or restart. If `currentSum < 0`, restart from current element. Update `maxSum` at each step.

```java
int maxSum = nums[0], currentSum = nums[0];
for (int i = 1; i < nums.length; i++) {
    // Decision: extend the current subarray OR restart fresh from nums[i]
    // If currentSum + nums[i] < nums[i], the prefix is hurting us → restart
    currentSum = Math.max(nums[i], currentSum + nums[i]);
    // Track the best sum seen across all subarrays
    maxSum = Math.max(maxSum, currentSum);
}
return maxSum;
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 152: Maximum Product Subarray

> **Problem:** Find the contiguous subarray with the largest product. Example: `nums = [2,3,-2,4]` → `6` (subarray `[2,3]`).

> **Brute force:** Try all subarrays, compute the product of each. O(n²) time, O(1) space.
> **Key insight:** A negative number flips the sign — the running minimum can become the maximum after multiplication. Track both `maxProd` and `minProd` simultaneously; swap them when a negative element appears.
> **Approach:** Track both `maxProd` AND `minProd` at each step. A negative number flips min↔max, so you must track both. When `nums[i]` is negative, swap them before multiplying.

```java
// Track BOTH max and min because a negative × negative = positive (min can become max!)
int maxProd = nums[0], minProd = nums[0], result = nums[0];
for (int i = 1; i < nums.length; i++) {
    // Negative number flips the signs: max becomes min, min becomes max
    // Swap BEFORE multiplying so the multiplication uses the correct values
    if (nums[i] < 0) {
        int temp = maxProd;
        maxProd = minProd;
        minProd = temp;
    }
    // Extend or restart (same idea as Kadane's, but for products)
    maxProd = Math.max(nums[i], maxProd * nums[i]);
    minProd = Math.min(nums[i], minProd * nums[i]);
    result = Math.max(result, maxProd);
}
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 974: Subarray Sums Divisible by K

> **Problem:** Return the count of subarrays whose sum is divisible by `k`. Example: `nums = [4,5,0,-2,-3,1], k = 5` → `7`.

> **Brute force:** Try all subarrays (i, j), check if each sum is divisible by k. O(n²) time, O(1) space.
> **Key insight:** `(prefix[j] - prefix[i]) % k == 0` iff `prefix[j] % k == prefix[i] % k`. Two prefix sums with the same remainder → subarray between them is divisible by k.
> **Approach:** Prefix sum + modular arithmetic. Two prefix sums with the same `prefix % k` remainder → the subarray between them is divisible by K. Handle negative mod with `((prefix % k) + k) % k`.

```java
Map<Integer, Integer> map = new HashMap<>();
// Seed: empty prefix has remainder 0, seen once
map.put(0, 1);
int prefix = 0, count = 0;
for (int num : nums) {
    // ((prefix + num) % k + k) % k handles NEGATIVE remainders
    // In Java, -3 % 5 = -3 (not 2). Adding k then re-modding fixes it:
    //   (-3 % 5 + 5) % 5 = (-3 + 5) % 5 = 2 ← correct!
    prefix = ((prefix + num) % k + k) % k;
    // Two prefixes with SAME remainder → subarray between them is divisible by k
    count += map.getOrDefault(prefix, 0);
    // merge: record this remainder. Integer::sum = (old, 1) -> old + 1
    map.merge(prefix, 1, Integer::sum);
}
```

**Complexity (optimal):** O(n) time, O(k) space — remainder map has at most k entries.

---

### LC 930: Binary Subarrays With Sum

> **Problem:** Given a binary array and a target sum, return the count of non-empty subarrays with sum equal to `goal`. Example: `nums = [1,0,1,0,1], goal = 2` → `4`.

> **Brute force:** Try all subarrays of the binary array, count those summing to goal. O(n²) time, O(1) space.
> **Key insight:** Same prefix sum trick as LC 560 — store how many times each prefix sum occurred. `map.get(prefix - goal)` gives the count of valid subarrays ending at the current index.
> **Approach:** Prefix sum + HashMap (same as LC 560). Count prefix sums where `prefix - goal` was seen before.

```java
// Same template as LC 560 — binary array so prefix = running count of 1s
// How many earlier positions had prefix sum = (prefix - goal)?
count += map.getOrDefault(prefix - goal, 0);
// Record current prefix sum
// merge: if key absent → put(prefix, 1); if present → put(prefix, old + 1)
map.merge(prefix, 1, Integer::sum);
// 🔄 Fallback: map.put(prefix, map.getOrDefault(prefix, 0) + 1);
```

**Complexity (optimal):** O(n) time, O(n) space.

---

### LC 451: Sort Characters By Frequency

> **Problem:** Sort a string in decreasing order based on character frequency. Example: `s = "tree"` → `"eert"` or `"eetr"`.

> **Brute force:** Count frequencies, then sort characters by frequency descending (using a sorted list or sort + stable sort). O(n log n) time, O(n) space.
> **Key insight:** Frequency is bounded by string length. Use frequency as a bucket index — place each character in `buckets[freq]`, walk high→low. O(n) without any comparison sort.
> **Approach:** Build frequency map, then bucket sort (bucket index = frequency). Iterate buckets from highest to lowest, appending characters.

```java
Map<Character, Integer> freq = new HashMap<>();
// merge: count each character. Integer::sum = (old, new) -> old + new
for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
// 🔄 Fallback: freq.put(c, freq.getOrDefault(c, 0) + 1);

List<Character>[] buckets = new List[s.length() + 1];
// entrySet: iterate key-value pairs. e.getKey() = char, e.getValue() = frequency
for (var e : freq.entrySet()) {
    if (buckets[e.getValue()] == null) buckets[e.getValue()] = new ArrayList<>();
    buckets[e.getValue()].add(e.getKey());
}
// 🔄 Fallback: for (char key : freq.keySet()) { ... buckets[freq.get(key)].add(key); }
```

**Complexity (optimal):** O(n) time (bucket sort), O(n) space.

---

### LC 692: Top K Frequent Words

> **Problem:** Given an array of words and `k`, return the `k` most frequent words sorted by frequency (highest first). Ties broken by alphabetical order. Example: `words = ["i","love","leetcode","i","love","coding"], k = 2` → `["i","love"]`.

> **Brute force:** Count frequencies, sort all words by frequency descending (ties → alphabetical ascending). O(n log n) time, O(n) space.
> **Key insight:** Min-heap of size K with a custom comparator — lower-frequency words are evicted first; among ties, lexicographically-later words are evicted. Heap always holds exactly the K best candidates.
> **Approach:** Build frequency map. Min-heap of size K ordered by frequency (then reverse alphabetical for ties). See `heaps.md` for full heap patterns.

```java
Map<String, Integer> freq = new HashMap<>();
// merge: count each word. Integer::sum = (old, new) -> old + new
for (String w : words) freq.merge(w, 1, Integer::sum);
// 🔄 Fallback: freq.put(w, freq.getOrDefault(w, 0) + 1);

// Custom comparator lambda for min-heap:
//   (a, b) -> negative means a comes first, positive means b comes first
//   Same freq? → reverse alphabetical (b.compareTo(a)) so worst alpha gets evicted
//   Diff freq? → lower freq first (freq.get(a) - freq.get(b)) so lowest gets evicted
PriorityQueue<String> pq = new PriorityQueue<>(
    (a, b) -> freq.get(a).equals(freq.get(b)) ? b.compareTo(a) : freq.get(a) - freq.get(b)
);

// 🔄 Fallback if comparator lambda confuses you — just sort a list:
// List<String> sorted = new ArrayList<>(freq.keySet());
// Collections.sort(sorted, (a, b) -> freq.get(b) - freq.get(a)); // descending freq
// return sorted.subList(0, k);
```

**Complexity (optimal):** O(n log k) time, O(n + k) space — heap size capped at k.

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty array** — return 0 / empty list / handle gracefully
- **Single element** — does your loop handle `nums.length == 1`?
- **All same elements** — Group Anagrams with all identical strings, Contains Duplicate
- **Negative numbers** — "Why can't you use sliding window for Subarray Sum = K?" (the #1 follow-up)
- **Integer overflow** — prefix sums can exceed int range. Use `long`

### Follow-up questions to expect:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| Two Sum (HashMap) | "What if array is sorted?" | Two pointers — O(1) space |
| Two Sum (HashMap) | "What if there are duplicates?" | HashMap handles it — latest index wins |
| Subarray Sum = K | "What if values are all positive?" | Sliding window works — O(1) space |
| Top K Frequent | "What's the time complexity?" | O(n) bucket sort vs O(n log k) heap |
| Contains Duplicate | "Can you do O(1) space?" | Sort first — O(n log n) time but O(1) space |
| Group Anagrams | "What if strings are very long?" | Use `int[26]` frequency as key instead of sorting |

### Complexity traps:

- **Sorting inside a loop:** `Arrays.sort(chars)` inside the group-anagrams loop is O(K log K) per string. Total: O(N × K log K). The `int[26]` approach is O(N × K).
- **String concatenation as key:** `"" + row + col + val` creates a new String each time — fine for interviews but mention it.
- **`containsValue()` on HashMap is O(n)** — if you need to check values, you need a reverse map.

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

For each problem description, name the pattern:

1. "Find two numbers that sum to target, return indices" → ___
2. "Count subarrays with sum exactly K" → ___
3. "Group strings that are anagrams of each other" → ___
4. "Find the longest consecutive sequence" → ___
5. "Return the K most frequent elements" → ___
6. "Maximum subarray sum" → ___
7. "Is there a subarray with equal 0s and 1s?" → ___
8. "Best time to buy and sell one stock" → ___

**Answers:** 1. HashMap Lookup, 2. Prefix Sum + HashMap, 3. Canonical Key, 4. HashSet (start-of-run), 5. Freq Map + Bucket Sort, 6. Kadane's, 7. Prefix Sum + HashMap (0→-1), 8. Kadane variant (track min price)

**Part 2 — Write the Template (3 minutes)**

From memory, write the Prefix Sum + HashMap template for Subarray Sum = K. Include: map initialization with `{0:1}`, the loop, the check, the store.

**Part 3 — Adapt (3 minutes)**

How would you modify the Subarray Sum = K template for LC 525 (Contiguous Array — find longest subarray with equal 0s and 1s)?

**Hint:** Convert 0 → -1. Now "equal 0s and 1s" becomes "subarray sum = 0." But now you want the **longest** subarray, not the count — so the map stores `prefixSum → first index` (not count).

**Scoring:** Part 1: 8/8 = ready. Part 2: compiled in head = ready. Part 3: got the 0→-1 trick + "first index" twist = you own this pattern.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Arrays deep dive (learn from scratch) | `DSA/DeepDive/arrays-fundamentals.md` |
| Arrays reference (method syntax + all 14 patterns) | `DSA/Reference/arrays-reference.md` |
| HashMap method syntax | `DSA/Reference/hashmap-section-updated.md` |
| HashSet method syntax | `DSA/Reference/set-section-updated.md` |
| Two pointers + sliding window patterns | `DSA/Interview/Playbooks/two-pointers-and-sliding-window.md` |
| Java coding traps (equality, overflow, autoboxing) | `DSA/Implementation/java-coding-traps.md` |
| Integer overflow deep dive | `DSA/DeepDive/integer-overflow-and-limits.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Arrays & Hashing. 6 patterns with recognition cues, canonical walkthrough (LC 560), 10-problem bank, interview gotchas with follow-up table, 8-minute speed drill. |
| June 2026 | **Brute Force / Key Insight pass.** Added `**What this solves**`, `**Brute force**`, `**Key insight**` to all 6 pattern blocks and canonical section. Added `> **Brute force**`, `> **Key insight**` to all 21 problem bank entries. Added `**Complexity (optimal)**` after every code block. |
