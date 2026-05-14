# Group Anagrams

> **LeetCode:** [49. Group Anagrams](https://leetcode.com/problems/group-anagrams/) — Medium
> **Pattern:** Hashable Key (see HashMap notes #7)
> **Uses:** String sorting, frequency arrays, HashMap of List

---

## 📌 Problem

Given an array of strings `strs`, **group all anagrams together** into sublists. You may return the output in any order.

> An **anagram** is a string that contains the **exact same characters** as another string, but the order of the characters can be different.

### Examples

```
Input:  strs = ["act", "pots", "tops", "cat", "stop", "hat"]
Output: [["hat"], ["act","cat"], ["stop","pots","tops"]]
```

```
Input:  strs = [""]
Output: [[""]]
```

```
Input:  strs = ["a"]
Output: [["a"]]
```

### Constraints (typical)

- `1 ≤ strs.length ≤ 10^4`
- `0 ≤ strs[i].length ≤ 100`
- `strs[i]` consists of lowercase English letters

---

## 🧠 Pattern Recognition

> **"Group items together if they share some hidden equivalence."**
>
> Whenever you see the word *"group"* in a problem, your first thought should be:
> 1. **What property makes two items belong to the same group?** (Here: same character frequencies)
> 2. **Can I express that property as a unique, hashable key?** (Here: sorted string OR frequency array)
> 3. **If yes → HashMap of List, with the key = canonical form.**

This is the **Hashable Key** pattern. See HashMap notes #7 for the general framework.

---

## ❌ Approach 1: List of Frequency Maps + Linear Scan (Brute Force)

> **The instinctive first solution** — track the freq map of each "anagram group" we've seen so far. For each new string, scan through all existing groups to find a match.

### Idea

1. Maintain a `List<HashMap<Character, Integer>>` of seen frequency maps (one per group).
2. Maintain a parallel `List<List<String>>` for the actual groupings.
3. For each new string, build its frequency map, then **scan the list** to find a matching freq map.
4. If found → add the string to the existing group. If not → create a new group.

### Code

```java
public List<List<String>> groupAnagrams(String[] strs) {
    List<Map<Character, Integer>> seenFreqs = new ArrayList<>();
    List<List<String>> groups = new ArrayList<>();

    for (String s : strs) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : s.toCharArray()) {
            freq.merge(c, 1, Integer::sum);
        }

        boolean placed = false;
        for (int i = 0; i < seenFreqs.size(); i++) {
            if (seenFreqs.get(i).equals(freq)) {
                groups.get(i).add(s);
                placed = true;
                break;
            }
        }
        if (!placed) {
            seenFreqs.add(freq);
            List<String> newGroup = new ArrayList<>();
            newGroup.add(s);
            groups.add(newGroup);
        }
    }
    return groups;
}
```

### Complexity

| | |
| --- | --- |
| Time | **O(n² · k)** — for each of n strings, scan up to n previous freq maps, each comparison is O(k) |
| Space | O(n · k) — store one freq map per group |

> **Why it's slow:** the inner linear scan over previously seen maps. Whenever you find yourself scanning a list to match a property, ask: *"Can I make this property a key and look it up in O(1)?"*

---

## ✅ Approach 2: Sorted String as Canonical Key

> **The standard interview answer.** Replace the linear scan with a HashMap lookup. The trick: **sort the characters** of each string — anagrams produce identical sorted strings.

### Idea

```
"act"  → sorted → "act"      ┐
"cat"  → sorted → "act"      ┴── same key → same group

"pots" → sorted → "opst"     ┐
"tops" → sorted → "opst"     ┼── same key → same group
"stop" → sorted → "opst"     ┘

"hat"  → sorted → "aht"      ── unique key
```

Use the sorted string as a HashMap key, with `List<String>` as the value.

### Code

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();

    for (String s : strs) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(groups.values());
}
```

### Walkthrough

```
strs = ["act", "pots", "tops", "cat", "stop", "hat"]

"act"  → key "act"  → groups: { "act": ["act"] }
"pots" → key "opst" → groups: { "act": ["act"], "opst": ["pots"] }
"tops" → key "opst" → groups: { "act": ["act"], "opst": ["pots", "tops"] }
"cat"  → key "act"  → groups: { "act": ["act", "cat"], "opst": ["pots", "tops"] }
"stop" → key "opst" → groups: { "act": ["act", "cat"], "opst": ["pots", "tops", "stop"] }
"hat"  → key "aht"  → groups: { ..., "aht": ["hat"] }

return new ArrayList<>(groups.values()) → [["act","cat"], ["pots","tops","stop"], ["hat"]]
```

### Complexity

| | |
| --- | --- |
| Time | **O(n · k log k)** — n strings, each takes O(k log k) to sort |
| Space | O(n · k) — store all strings in the map |

> **Big improvement:** no more linear scan. Direct O(1) lookup in the HashMap.

---

## 🚀 Approach 3: Frequency Count as Canonical Key (Optimal)

> **The "no sorting needed" optimization.** Sorting costs O(k log k). Can we build a unique key in O(k)?
>
> Yes — encode the **character frequency array** itself as the key.

### Idea

```
"act":  freq = [1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0]   → key
"cat":  freq = [1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0]   → same key ✅

"pots": freq = [0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,1,1,0,0,0,0,0,0]   → key
"tops": freq = [0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,1,1,0,0,0,0,0,0]   → same key ✅
```

Convert the `int[26]` to a String (e.g., via `Arrays.toString`) and use it as the HashMap key.

### Code

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();

    for (String s : strs) {
        int[] count = new int[26];
        for (char c : s.toCharArray()) {
            count[c - 'a']++;
        }
        String key = Arrays.toString(count);
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(groups.values());
}
```

### Why `c - 'a'`?

Maps `'a'..'z'` to indices `0..25`. See String Operations Reference for details.

### Alternative key construction (more compact)

```java
// Build a compact key like "1#0#1#0#..."
StringBuilder sb = new StringBuilder();
for (int n : count) {
    sb.append(n).append('#');
}
String key = sb.toString();
```

> Both work. `Arrays.toString` is shorter; `StringBuilder` is marginally faster.

### Complexity

| | |
| --- | --- |
| Time | **O(n · k)** — n strings, each scanned once in O(k) |
| Space | O(n · k) |

> **This is optimal** — you can't go faster than reading every character of every string.

---

## 📊 Approach Comparison

| Approach | Time | Space | Notes |
| --- | --- | --- | --- |
| 1. List of freq maps + scan | **O(n² · k)** | O(n · k) | Brute force, fails large inputs |
| 2. Sorted string as key | **O(n · k log k)** | O(n · k) | Cleanest code, standard interview answer |
| 3. Frequency array as key | **O(n · k)** | O(n · k) | Optimal, slightly more code |

> **Interview tip:** Start with Approach 2, mention that you can optimize to Approach 3 by using the frequency array as the key. Code Approach 2 unless asked to optimize.

---

## 🔁 Variations & Follow-ups

### **1. What if strings can contain Unicode (not just lowercase a-z)?**
- Approach 3's `int[26]` won't work — switch to `HashMap<Character, Integer>` or `int[128]` for ASCII.
- Approach 2 (sorting) still works without changes.

### **2. What if you need to detect anagrams for two single strings (not group many)?**
- This is **Valid Anagram (LC 242)** — use a single `int[26]` and `++` for s, `--` for t, then check all zeros.

### **3. What if strings can be very long but the array is small?**
- Approach 2's O(k log k) sort dominates. Approach 3 wins big.

### **4. What if you need to group by "anagram + length"?**
- The key already encodes both (sorted string and frequency array both depend on length).

### **5. Group Shifted Strings (LC 249)**
- Same pattern, different canonical key: encode the **differences between consecutive characters** (mod 26).

### **6. Find All Anagrams in a String (LC 438)**
- Different problem — sliding window with frequency match. See HashMap notes #6 (Sliding Window).

---

## 🎯 Key Takeaways

1. **"Group" → think Hashable Key.** Pick a canonical form that's identical for items in the same group.
2. **Sorting** is the easiest canonical key — works for many "different ordering, same elements" problems.
3. **Frequency arrays** are the optimal canonical key for fixed-alphabet strings — convert `int[26]` to string with `Arrays.toString(...)`.
4. **`computeIfAbsent`** is the cleanest way to build a HashMap of List.
5. Recognize the **brute-force trap**: linear scanning a list of "groups so far" is always replaceable by a HashMap lookup.

---

## 🔗 Related Notes & Problems

### Notes referenced
- HashMap notes → **Pattern #7 Hashable Key** (general framework)
- HashMap notes → **Pattern #3 Group Anagrams** (snippet form)
- String Operations Reference → **Sorting strings** + **`c - 'a'` arithmetic** + **Frequency arrays**

### Similar problems (same pattern)
- **Valid Anagram** (LC 242) — single-pair version, `int[26]` with `++`/`--`
- **Group Shifted Strings** (LC 249) — different canonical key (char diffs)
- **Find Resultant Array After Removing Anagrams** (LC 2273)
- **Subdomain Visit Count** (LC 811) — Hashable Key with composite domain string
- **Encode and Decode Strings** (LC 271) — different problem but uses the "build canonical string" mindset

### Adjacent problems (string + frequency)
- **Find All Anagrams in a String** (LC 438) — sliding window
- **Permutation in String** (LC 567) — sliding window
- **Minimum Window Substring** (LC 76) — sliding window
- **Top K Frequent Elements** (LC 347) — frequency map + heap

---

## 🧪 Quick Self-Test

Without looking, can you:
- [ ] State the pattern name?
- [ ] Write Approach 2 (sorted-string key) from scratch in 5 minutes?
- [ ] Explain why Approach 3 is faster than Approach 2?
- [ ] Name the helper method used to build the `Map<K, List<V>>`?
- [ ] Convert the optimal solution to handle Unicode characters?

If yes to all → you've internalized the Hashable Key pattern. ✅
