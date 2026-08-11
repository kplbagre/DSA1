# JPMorgan — DSA Questions Ranked by Frequency

> **Source:** LeetCode Discuss, Glassdoor, Medium reports (2024–2026)
> **Difficulty:** Mostly Easy-Medium on OA. Medium-Hard in Round 1 DSA round.
> `⭐⭐⭐` = reported 3+ times | `⭐⭐` = 2 times | `⭐` = once but high-signal

---

## 🎯 Online Assessment (OA) — Patterns

The OA is on HackerRank. 60 minutes, 2 questions. **Both must pass all test cases.**
Difficulty: **Easy to Medium**. Java Streams version of a problem is sometimes asked directly in OA.

---

## 🔢 DSA Topics — Ranked by Frequency

### 1️⃣ Arrays & Intervals `⭐⭐⭐` — Most Repeated OA Topic

**Pattern:** Overlap detection, maximum/minimum under constraint.

| Frequency | Problem | LeetCode Equivalent | Key concept |
|---|---|---|---|
| ⭐⭐⭐ | Minimum platforms for a railway station | LC 253 — Meeting Rooms II | Sort + two pointer / min-heap |
| ⭐⭐⭐ | Maximum number of overlapping intervals | LC 56 — Merge Intervals variant | Sort by start, greedy merge |
| ⭐⭐ | Subarray with given sum | LC 560 — Subarray Sum Equals K | Prefix sum + HashMap |
| ⭐⭐ | Find majority element | LC 169 — Majority Element | Boyer-Moore voting — keep a candidate and a count; increment count if same element, decrement if different; the surviving candidate after one pass is the majority element (works because majority appears > n/2 times) |
| ⭐ | Rotate array k positions | LC 189 — Rotate Array | Reverse trick |

**Java OA variant that was literally asked:**
```java
// "Remove all odd numbers, multiply each by constant C, return the sum"
// Must use Java Streams — NOT a loop
int result = Arrays.stream(arr)
    .filter(n -> n % 2 == 0)
    .map(n -> n * C)
    .sum();
```

---

### 2️⃣ Strings & Sliding Window `⭐⭐⭐`

| Frequency | Problem | LeetCode Equivalent | Key concept |
|---|---|---|---|
| ⭐⭐⭐ | Count valid substrings with no adjacent repeating chars, length in [minLen, maxLen] | Custom — Sliding Window | Two-pointer + char frequency |
| ⭐⭐⭐ | Longest substring without repeating characters | LC 3 | HashMap + sliding window |
| ⭐⭐ | Valid anagram / group anagrams | LC 242 / 49 | Sort or frequency array |
| ⭐⭐ | First non-repeating character | LC 387 | LinkedHashMap preserves insertion order — count frequency in one pass, then iterate map in insertion order and return first key with count 1 |
| ⭐ | String compression | LC 443 | In-place pointer |

---

### 3️⃣ Dynamic Programming `⭐⭐⭐`

| Frequency | Problem | LeetCode Equivalent | Key concept |
|---|---|---|---|
| ⭐⭐⭐ | Largest square of 1s in binary matrix | LC 221 — Maximal Square | `dp[i][j] = min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1]) + 1` — means: the largest square ending at `(i,j)` is limited by the smallest square among its top, left, and top-left neighbours |
| ⭐⭐ | Combination Sum II (unique combinations summing to target) | LC 40 | Backtracking + sort + skip duplicates |
| ⭐⭐ | Coin change | LC 322 | Classic unbounded DP |
| ⭐⭐ | Longest common subsequence | LC 1143 | 2D DP |
| ⭐ | Circular dial problem (k dials A-Z, min moves to type string) | Custom BFS/DP | BFS state = (dial positions, string index) |

---

### 4️⃣ HashMap / Hashing `⭐⭐⭐`

| Frequency | Problem | LeetCode Equivalent | Key concept |
|---|---|---|---|
| ⭐⭐⭐ | Two Sum | LC 1 | Complement in map |
| ⭐⭐⭐ | Group strings by anagram | LC 49 | Sorted key → list |
| ⭐⭐ | Longest consecutive sequence | LC 128 | HashSet membership |
| ⭐⭐ | Find duplicates in array | LC 217 | HashSet or XOR |
| ⭐ | LRU Cache | LC 146 | LinkedHashMap `removeEldestEntry` |

> **Note:** JPMC interviewers use HashMap DSA to segue into **HashMap internals** (see `02-core-java-questions.md`). If you solve Two Sum, expect a follow-up: "What's the time complexity of HashMap get?" → then "What happens during a collision?"

---

### 5️⃣ Graphs & Matrix BFS/DFS `⭐⭐`

| Frequency | Problem | LeetCode Equivalent | Key concept |
|---|---|---|---|
| ⭐⭐ | Number of islands | LC 200 | BFS/DFS flood fill |
| ⭐⭐ | Graph connection levels (count connections from level i to next feasible level with 1s) | Custom matrix BFS | BFS layer-by-layer |
| ⭐⭐ | Rotting oranges | LC 994 | Multi-source BFS |
| ⭐ | Course schedule (detect cycle) | LC 207 | Topological sort / DFS color |
| ⭐ | Shortest path in binary matrix | LC 1091 | BFS |

---

### 6️⃣ Trees `⭐⭐`

| Frequency | Problem | LeetCode Equivalent | Key concept |
|---|---|---|---|
| ⭐⭐ | Lowest common ancestor | LC 236 | Recursive — return node if found |
| ⭐⭐ | Level-order traversal | LC 102 | BFS with queue |
| ⭐⭐ | Validate BST | LC 98 | In-order or bounds propagation |
| ⭐ | Serialize / deserialize binary tree | LC 297 | Pre-order + null markers |

---

### 7️⃣ Stack & Queue `⭐⭐`

| Frequency | Problem | LeetCode Equivalent | Key concept |
|---|---|---|---|
| ⭐⭐ | Valid parentheses | LC 20 | Stack push/pop |
| ⭐⭐ | Next greater element | LC 496 | Monotonic stack — maintain a stack of elements in decreasing order; when a larger element arrives, pop everything smaller (they've found their answer) and push the new element |
| ⭐ | Implement queue using stacks | LC 232 | Two stacks, amortized O(1) |

---

### 8️⃣ Sorting & Searching `⭐`

| Frequency | Problem | Key concept |
|---|---|---|
| ⭐⭐ | K-th largest element | LC 215 — QuickSelect (avg O(n), partition around pivot like quicksort but only recurse on one side) or min-heap of size K (O(n log k), more predictable) |
| ⭐⭐ | Sort employees by salary (written in Java) | Comparator / Comparable — Java-specific |
| ⭐ | Binary search on answer (e.g., capacity) | LC 875 Koko Eating Bananas |

---

## 🗺️ OA Pattern Summary (What to Drill First)

```
Priority 1 (OA constants):
  - Interval overlap / Minimum platforms       (arrays + sort)
  - Sliding window substring                   (strings)
  - Java Streams filter-map-reduce             (Java-specific OA Q)

Priority 2 (Round 1 DSA):
  - Maximal Square (DP)
  - Combination Sum II (backtracking)
  - Two Sum + HashMap internals (combo)
  - BFS matrix problems

Priority 3 (less frequent but seen):
  - Circular dial BFS/DP
  - LRU Cache
  - Trees (LCA, level order)
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created from 2024–2026 JPMC interview reports. |
