# HashMap — Reference

> Compact revision file for HashMap-centric DSA patterns. Every pattern here is something you'd write on a plain notepad in an interview.

---

## ⚡ Imports — Write These First on a Blank Notepad

```java
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
```

> These cover every pattern in this file. `Integer`, `String`, `Character` are `java.lang` — no import needed.

---

## 🔹 HashMap Basics

Backed by **hash table (array + LinkedList/Tree)**, average **O(1)** for put/get/remove.

```java
Map<String, Integer> map = new HashMap<>();
```

### **Useful Methods**

| **Method** | **Description** | **Time** |
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

---

### **Common DSA Patterns**

---

**1. Frequency Map from Array / String** ⭐

> Count how many times each element appears by mapping `element → count`. The backbone of problems involving anagrams, character counts, majority elements, top-K, etc.

```java
Map<Integer, Integer> freq = new HashMap<>();
for (int n : nums) {
    freq.merge(n, 1, Integer::sum);
}

// Equivalent using getOrDefault
for (int n : nums) {
    freq.put(n, freq.getOrDefault(n, 0) + 1);
}
```

> **Prefer `int[26]` over HashMap when the alphabet is fixed and small** (lowercase English letters):
>
> ```java
> int[] freq = new int[26];
> for (char c : s.toCharArray()) {
>     freq[c - 'a']++;
> }
> ```
>
> Array index is faster than hashing, no boxing, and memory is fixed at 26 ints. Only reach for HashMap when the keys are unbounded (Unicode chars, arbitrary integers, strings, custom objects).

**🏷️ Example problems:** Valid Anagram (LC 242), Top K Frequent Elements (LC 347), Majority Element (LC 169), First Unique Character (LC 387), Sort Characters by Frequency (LC 451), Ransom Note (LC 383)

---

**2. Two Sum (with Index)**

> Same complement idea as the Set version, but store the **index** as the value so you can return positions, not just confirm existence. One-pass O(n) solution.

```java
Map<Integer, Integer> map = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    int complement = target - nums[i];
    if (map.containsKey(complement)) {
        return new int[]{ map.get(complement), i };
    }
    map.put(nums[i], i);
}
```

**🏷️ Example problems:** Two Sum (LC 1), 3Sum (LC 15 — combined with sort + two pointers), 4Sum II (LC 454), Pairs of Songs With Total Durations Divisible by 60 (LC 1010)

---

**3. Group Anagrams (Map of List)**

> Use a canonical form (sorted string) as the key to bucket similar items together. `computeIfAbsent` lazily initializes the list only when a new key appears.

```java
Map<String, List<String>> groups = new HashMap<>();
for (String s : strs) {
    char[] c = s.toCharArray();
    Arrays.sort(c);
    String key = new String(c);
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
}
return new ArrayList<>(groups.values());
```

**🏷️ Example problems:** Group Anagrams (LC 49), Group Shifted Strings (LC 249), Find Resultant Array After Removing Anagrams (LC 2273)

---

**4. Subarray Sum Equals K (Prefix Sum + Map)** ⭐

> **Goal:** count how many contiguous subarrays sum exactly to `k`.

**The key insight (read this slowly).** Define a running **prefix sum** as you walk the array:

```
prefixSum[i] = nums[0] + nums[1] + ... + nums[i]
```

The sum of any subarray `nums[j+1 .. i]` is just the difference between two prefix sums:

```
sum(nums[j+1 .. i]) = prefixSum[i] - prefixSum[j]
```

We want this to equal `k`:

```
prefixSum[i] - prefixSum[j] == k
prefixSum[j] == prefixSum[i] - k     ← this is what we look up in the map
```

So as we walk left-to-right computing the running sum, at each `i` we ask: *"how many earlier prefix sums equal `currentSum - k`?"* Each match represents one valid subarray ending at `i`. Store every prefix sum we've seen (with its count) in a HashMap to answer this in O(1).

**Why the seed `prefix.put(0, 1)`?** It accounts for subarrays that start at index 0 — i.e., when the running sum itself equals `k` (no "earlier prefix" needed to subtract). The "empty prefix" has sum 0 and is conceptually seen once before we start. Without this seed, you'd miss those subarrays.

**Walkthrough — `nums = [1, 2, 3]`, `k = 3`:**

| i | num | sum | sum - k | prefix map (before) | matches | count | prefix map (after) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 | 1 | 1 | -2 | `{0:1}` | 0 | 0 | `{0:1, 1:1}` |
| 1 | 2 | 3 | 0 | `{0:1, 1:1}` | 1 | 1 | `{0:1, 1:1, 3:1}` |
| 2 | 3 | 6 | 3 | `{0:1, 1:1, 3:1}` | 1 | 2 | `{0:1, 1:1, 3:1, 6:1}` |

Two valid subarrays found: `[1, 2]` (matched at i=1 because sum-k=0 was seen) and `[3]` (matched at i=2 because sum-k=3 was seen). ✅

```java
Map<Integer, Integer> prefix = new HashMap<>();
prefix.put(0, 1);                                   // seed: empty prefix has sum 0, seen once
int sum = 0;
int count = 0;
for (int n : nums) {
    sum += n;                                       // running prefix sum
    count += prefix.getOrDefault(sum - k, 0);       // earlier prefixes that complete a subarray summing to k
    prefix.merge(sum, 1, Integer::sum);             // record current prefix sum
}
return count;
```

> Converts an O(n²) brute force (try every `(i, j)` pair) into O(n).

**🏷️ Example problems:** Subarray Sum Equals K (LC 560), Continuous Subarray Sum (LC 523), Contiguous Array (LC 525 — sums of 0/1 problem), Path Sum III (LC 437), Subarray Sums Divisible by K (LC 974)

---

**5. First Non-Repeating Character / Element**

> **Two-pass technique:** count → scan in original order to find the first item with count == 1.

**Best for fixed alphabet (LC 387 — lowercase English letters):** use an `int[26]` frequency array. The natural left-to-right scan in pass 2 walks the original string, so "first" comes for free.

```java
// LC 387 — First Unique Character in a String
public int firstUniqChar(String s) {
    int[] count = new int[26];
    int n = s.length();

    // Pass 1: build the frequency bucket
    for (int i = 0; i < n; i++) {
        int index = s.charAt(i) - 'a';
        count[index]++;
    }

    // Pass 2: scan the original string left-to-right; return the first index with count == 1
    for (int i = 0; i < n; i++) {
        int index = s.charAt(i) - 'a';
        if (count[index] == 1) {
            return i;
        }
    }
    return -1;
}
```

**Why this beats a HashMap here:** array index is faster than hashing, no boxing, fixed memory (104 bytes regardless of input length). And since pass 2 walks the original string, "first" is naturally the leftmost — no insertion-order map needed. The problem also asks for the **index**, which the original-string scan gives you directly.

**Generalized form (unbounded alphabet — Unicode strings, words, arbitrary objects):** use `LinkedHashMap` so iteration order matches insertion order.

```java
// First non-repeating word in a sentence
Map<String, Integer> count = new LinkedHashMap<>();
for (String w : words) {
    count.merge(w, 1, Integer::sum);
}
for (var e : count.entrySet()) {
    if (e.getValue() == 1) {
        return e.getKey();
    }
}
```

> ⚠️ Don't reach for plain `HashMap` here — it doesn't preserve insertion order, so iterating it doesn't give you "first."

**Decision rule:**
- Alphabet fixed and small (e.g., `a-z`)? → `int[26]`
- Alphabet unbounded or keys are not characters? → `LinkedHashMap`

**🏷️ Example problems:** First Unique Character in a String (LC 387 — array form), Find the Difference (LC 389)

---

**6. Sliding Window Character Count**

> Maintain a live count of characters inside the current window — increment when expanding (right pointer moves right), decrement when shrinking (left pointer moves right), and remove keys at zero to keep the map clean. Common in "longest substring with K distinct" type problems.
>
> **Why remove keys at zero?** Most sliding-window problems care about `window.size()` — the number of **distinct** chars currently inside. Leaving a key at 0 would inflate that count and break the logic.

**Long form (no merge — easier to follow first):**

```java
Map<Character, Integer> window = new HashMap<>();
int left = 0;
int right = 0;

// === Expand: include s[right] in the window ===
char rightChar = s.charAt(right);
window.put(rightChar, window.getOrDefault(rightChar, 0) + 1);
right++;

// === Shrink: remove s[left] from the window ===
char leftChar = s.charAt(left);
int newCount = window.get(leftChar) - 1;
if (newCount == 0) {
    window.remove(leftChar);   // last occurrence — drop the key entirely
} else {
    window.put(leftChar, newCount);
}
left++;
```

**Short form (with merge — equivalent, more compact):**

```java
// === Expand: window[c]++ ===
window.merge(c, 1, Integer::sum);

// === Shrink: window[c]--, remove if it hit 0 ===
//   merge returns the NEW value after combining old + delta
//   if that new value is 0, the char has just left the window
if (window.merge(c, -1, Integer::sum) == 0) {
    window.remove(c);
}
```

**Reading the merge calls:**
- `window.merge(c, 1, Integer::sum)` reads as: *"if `c` is missing, put `c -> 1`; otherwise replace the existing count with `oldCount + 1`."*
- `window.merge(c, -1, Integer::sum)` reads as: *"if `c` is missing, put `c -> -1` (won't happen in this pattern because we only shrink chars that were added during expand); otherwise replace with `oldCount + (-1)`."*
- Both forms **return the new value after combining**, so checking `== 0` tells us "this char is now absent from the window."

> **Mental model:** the map is the window's "histogram." Expand = +1, shrink = -1, hit zero = drop the key.

**🏷️ Example problems:** Longest Substring Without Repeating Characters (LC 3), Longest Substring with At Most K Distinct Characters (LC 340), Minimum Window Substring (LC 76), Find All Anagrams in a String (LC 438), Permutation in String (LC 567), Longest Repeating Character Replacement (LC 424)

---

**7. Hashable Key Pattern** ⭐ (Generalization of Group Anagrams)

> When you need to **group items by some property** or **check if a transformed version of an item was seen before**, build a **canonical key** — a unique representation that's identical for items belonging to the same group — and use it as a HashMap key.
>
> The trick is: instead of comparing items pairwise (O(n²)), you compute their canonical form once and let the HashMap do O(1) lookup/grouping.

**Common ways to build a canonical key:**

| Item Type | Canonical Key Strategy |
| --- | --- |
| Strings (anagrams) | Sort the chars → sorted string |
| Strings (anagrams, faster) | Frequency array → `Arrays.toString(int[26])` |
| 2D coordinates (grid) | `r + "," + c` or `r * COLS + c` |
| Set / list of items | Sort and join, or use frozen set's hashCode |
| Custom object | Override `equals()` + `hashCode()`, or use a record |
| Pair of values | `a + "#" + b` (use a unique separator!) |

**Skeleton code:**

```java
Map<String, List<T>> groups = new HashMap<>();
for (T item : items) {
    String key = canonicalKey(item);
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
}
```

**Example A — Group Anagrams (sorted-string key):**

```java
Map<String, List<String>> groups = new HashMap<>();
for (String s : strs) {
    char[] c = s.toCharArray();
    Arrays.sort(c);
    String key = new String(c);
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
}
```

**Example B — Group Anagrams (frequency-array key, faster O(n·k)):**

```java
Map<String, List<String>> groups = new HashMap<>();
for (String s : strs) {
    int[] count = new int[26];
    for (char c : s.toCharArray()) {
        count[c - 'a']++;
    }
    String key = Arrays.toString(count);
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
}
```

**Example C — Group by Domain Visits (composite key):**

```java
// Input: ["9001 discuss.leetcode.com", ...]
Map<String, Integer> visits = new HashMap<>();
for (String entry : input) {
    String[] parts = entry.split(" ");
    int count = Integer.parseInt(parts[0]);
    String domain = parts[1];
    while (!domain.isEmpty()) {
        visits.merge(domain, count, Integer::sum);
        int dot = domain.indexOf('.');
        domain = (dot == -1) ? "" : domain.substring(dot + 1);
    }
}
```

**Example D — Encode 2D coordinates as map key:**

```java
// Useful in grid/island/visited-cell problems
Map<String, Integer> seen = new HashMap<>();
String key = r + "," + c;
seen.put(key, value);
```

**🏷️ Example problems:** Group Anagrams (LC 49), Group Shifted Strings (LC 249), Subdomain Visit Count (LC 811), Encode and Decode TinyURL (LC 535), Bulls and Cows (LC 299), Isomorphic Strings (LC 205 — uses two maps as a "matching key"), Word Pattern (LC 290)

> **Mental model:** "Can I represent this thing as a unique string/number that's the same for all items I want to group together?" If yes → Hashable Key pattern. The HashMap then does the grouping for you in O(1) per item.

---

**8. Last Seen Index Pattern**

> Store `value → most recent index` so you can check **distance** or **recency** in O(1). Different from Two Sum: here you keep **updating** the index every time you see the value again — you only care about the latest position.

```java
// Contains Duplicate II — return true if nums[i] == nums[j] and |i - j| <= k
Map<Integer, Integer> lastSeen = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    if (lastSeen.containsKey(nums[i]) && i - lastSeen.get(nums[i]) <= k) {
        return true;
    }
    lastSeen.put(nums[i], i);
}
return false;
```

**Variant — Sliding Window with Last Index (faster Longest Substring Without Repeating):**

```java
// Jump left pointer directly to (last seen index + 1) instead of shrinking one step at a time
Map<Character, Integer> lastIdx = new HashMap<>();
int l = 0;
int best = 0;
for (int r = 0; r < s.length(); r++) {
    char c = s.charAt(r);
    if (lastIdx.containsKey(c) && lastIdx.get(c) >= l) {
        l = lastIdx.get(c) + 1;
    }
    lastIdx.put(c, r);
    best = Math.max(best, r - l + 1);
}
return best;
```

**🏷️ Example problems:** Contains Duplicate II (LC 219), Longest Substring Without Repeating Characters (LC 3 — optimized version), Maximum Distance Between Same Elements, Repeated DNA Sequences (LC 187 — variant)

---

**9. Bidirectional Mapping (Two Maps)**

> When you need a **1-to-1 mapping in both directions** — every key maps to a unique value AND vice versa. A single map only enforces uniqueness on the key side; you need a second map to enforce it on the value side.

```java
// Isomorphic Strings — "egg" & "add" → true; "foo" & "bar" → false
Map<Character, Character> sToT = new HashMap<>();
Map<Character, Character> tToS = new HashMap<>();
for (int i = 0; i < s.length(); i++) {
    char a = s.charAt(i);
    char b = t.charAt(i);
    if (sToT.containsKey(a) && sToT.get(a) != b) {
        return false;
    }
    if (tToS.containsKey(b) && tToS.get(b) != a) {
        return false;
    }
    sToT.put(a, b);
    tToS.put(b, a);
}
return true;
```

**🏷️ Example problems:** Isomorphic Strings (LC 205), Word Pattern (LC 290), Bulls and Cows (LC 299)

> **Mental hook:** "If a → b, then nothing else can map to b." A single map can't catch the second condition — that's why you need two.

---

**10. Map + Heap (Top K Pattern)**

> Build a frequency map first, then push entries into a **min-heap of size K**. Heap auto-evicts the smallest, leaving the top K largest at the end. Net complexity: O(n log k) instead of O(n log n) full sort.

```java
// Top K Frequent Elements
Map<Integer, Integer> freq = new HashMap<>();
for (int n : nums) {
    freq.merge(n, 1, Integer::sum);
}

// min-heap by frequency: smallest freq at top → poll() removes it when size exceeds k
PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> a[1] - b[1]);
for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
    heap.offer(new int[]{ e.getKey(), e.getValue() });
    if (heap.size() > k) {
        heap.poll();
    }
}

int[] result = new int[k];
for (int i = k - 1; i >= 0; i--) {
    result[i] = heap.poll()[0];
}
return result;
```

**🏷️ Example problems:** Top K Frequent Elements (LC 347), Top K Frequent Words (LC 692), Sort Characters by Frequency (LC 451), K Closest Points to Origin (LC 973 — variant without freq map)

> **Bonus alternative — Bucket Sort:** For Top K Frequent, you can skip the heap entirely. Frequencies are bounded by `n`, so put each value into `buckets[freq]` (array of lists), then walk buckets from high to low. This achieves O(n).

---

### **Iteration Patterns**

```java
// HashMap
for (Map.Entry<K, V> e : map.entrySet()) {
    K k = e.getKey();
    V v = e.getValue();
}

// Just keys or values
for (K k : map.keySet()) { ... }
for (V v : map.values()) { ... }
```

---

### **⚠️ Gotchas (Silent Bug Hall of Fame)**

**Null handling differs by implementation.**

```java
new HashMap<>().put(null, 1);   // OK ✅ (one null key allowed)
new TreeMap<>().put(null, 1);   // NullPointerException ❌
```

---

**Custom objects as keys need `equals()` + `hashCode()`** — use Java `record` for free implementations.

```java
class Point { int x, y; /* no overrides */ }
Map<Point, Integer> m = new HashMap<>();
m.put(new Point(1, 2), 100);
m.get(new Point(1, 2));   // null ❌ — different object, no equals override

record Point(int x, int y) {} // ✅ free equals + hashCode
```

---

**Hashable Key with composite values needs a safe separator** — separator must not appear in either part, or use length-prefix.

```java
String key = a + "#" + b;       // ❌ breaks if a or b contains '#'
String key = a.length() + "#" + a + b.length() + "#" + b;  // ✅ length-prefix
```

---

**Bidirectional mapping needs TWO maps** — checking only one direction silently misses violations. (See pattern #9 for full code.)

---

**Min-heap vs Max-heap for Top K** — counterintuitive: use **min-heap** for top K **largest**.

```java
// Top K largest → min-heap of size K, poll() removes smallest candidate
PriorityQueue<Integer> heap = new PriorityQueue<>(); // min-heap by default
for (int n : nums) {
    heap.offer(n);
    if (heap.size() > k) heap.poll(); // evict smallest
}
// heap now contains the K largest
```

---

**`PriorityQueue` doesn't support O(log n) updates** — if you need to update priorities (e.g., LFU cache), use TreeSet or a custom indexed heap instead.

---

**Auto-unboxing NPE on missing key.**

```java
Map<String, Integer> map = new HashMap<>();
int x = map.get("missing");                 // NullPointerException ❌
int y = map.getOrDefault("missing", 0);     // 0 ✅
Integer z = map.get("missing");             // null — fine if you check
```

---

**Mutating a key after insertion silently breaks lookup.** If hashCode changes, the entry is orphaned in the wrong bucket.

```java
List<Integer> key = new ArrayList<>(List.of(1, 2));
map.put(key, "value");
key.add(3);          // ❌ hashCode changed
map.get(key);        // null — entry still exists but is "lost"
// Rule: use immutable keys (String, Integer, frozen list, record)
```

---

**`map.remove(k)` during iteration** throws `ConcurrentModificationException`.

```java
// ❌ ConcurrentModificationException
for (var e : map.entrySet()) {
    if (e.getValue() == 0) map.remove(e.getKey());
}

// ✅ Use removeIf or iterator.remove()
map.entrySet().removeIf(e -> e.getValue() == 0);
```

---

**`map.values().contains(v)` is O(n)** — looks like a HashMap call but does linear scan.

```java
map.containsKey("k");        // O(1) ✅
map.containsValue("v");      // O(n) ❌ — full scan
map.values().contains("v");  // O(n) — same as above
// If you need value lookup, maintain a reverse map
```

---

**`merge(k, null, fn)` throws NPE** — `merge` doesn't accept null values.

```java
map.merge("k", null, Integer::sum);  // NullPointerException ❌
map.put("k", null);                  // ✅ if you really need null
```

---

**`Arrays.toString(arr)` for array keys** — `arr.toString()` returns memory address and silently fails grouping. (See String Operations Reference for full explanation.)

```java
int[] count = new int[26];
String key = count.toString();          // "[I@7a81197d" ❌
String key = Arrays.toString(count);    // "[1, 0, 1, ...]" ✅
```
