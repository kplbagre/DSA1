# Two Pointers & Sliding Window — Reference

> Compact daily-revision file. Read after `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md`. This page is what Kapil scans 5 minutes before an interview.

---

## ⚡ Imports — Write These First on a Blank Notepad

```java
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
```

> These three cover every template in this file. LC 76 (Min Window Substring) also uses `Character` — no import needed, it's `java.lang`.

---

## 🎯 The Mental Model (10 Seconds)

**Two pointers that move only forward.** The window `[left..right]` IS the candidate answer. Each element added once + removed at most once → O(n).

Sliding window works **only when the constraint is monotonic** — adding to the right can flip valid→invalid (and shrinking from the left fixes it), but the sense of "validity" doesn't oscillate.

---

## 🧭 The Four Templates — Pick One

| Trigger Phrase | Template | Key Line |
| --- | --- | --- |
| "Subarray of size **K** with max/min..." | **1 — Fixed Window** | `sum += nums[r]; sum -= nums[r - k]` |
| "**Longest** subarray such that..." | **2 — Longest Valid** | `while (invalid) shrink; ans = max(ans, r - l + 1)` |
| "**Shortest** subarray such that..." | **3 — Shortest Valid** | `while (valid) { ans = min(ans, r - l + 1); shrink; }` |
| "Number of subarrays with **EXACTLY K**..." | **4 — atMost(K) − atMost(K−1)** | `count += r - l + 1` inside atMost |

---

## 🔹 Template 1 — Fixed Window of Size K [L2]

> Build the first window, then slide: add new right, remove old left. Each step is O(1).

```java
long sum = 0;
for (int i = 0; i < k; i++) {
    sum += nums[i];
}
long best = sum;

for (int right = k; right < n; right++) {
    sum += nums[right];
    sum -= nums[right - k];
    best = Math.max(best, sum);
}
```

**🏷️ Example problems:** LC 643 (Max Avg Subarray I), LC 1423 (Max Points from Cards), LC 438 (Find All Anagrams), LC 567 (Permutation in String), LC 1456 (Max Vowels in Substring of Length K).

> **L2 twist (LC 1423):** Pick K cards from either end → equivalent to *minimizing* the contiguous middle window of size n − k.

---

## 🔹 Template 2 — Longest Valid Window [L3, L4, L5, L6, L8] ⭐

> Expand always; whenever invalid, shrink from left until valid; record `r − l + 1`. The workhorse pattern.

```java
int left = 0;
int answer = 0;
for (int right = 0; right < n; right++) {
    addToState(nums[right]);
    while (windowInvalid()) {
        removeFromState(nums[left]);
        left++;
    }
    answer = Math.max(answer, right - left + 1);
}
```

**🏷️ Example problems:**
- LC 3 — Longest Substring Without Repeating (L3) — state: char set / `int[128]`.
- LC 1004 — Max Consecutive Ones III (L4) — state: `int zeros` counter.
- LC 904 — Fruit Into Baskets (L5) — at most 2 distinct.
- LC 340 — At Most K Distinct Chars (L6) — at most K distinct.
- LC 424 — Longest Repeating Character Replacement (L8) — uses `maxFreqEver` trick.

### Variant — Last-Seen Index Jump (LC 3)

> Replace the inner shrink loop with one jump: `left = max(left, lastSeen[c] + 1)`.

```java
int[] lastSeen = new int[128];
Arrays.fill(lastSeen, -1);
int left = 0;
int best = 0;
for (int right = 0; right < s.length(); right++) {
    char c = s.charAt(right);
    if (lastSeen[c] >= left) {
        left = lastSeen[c] + 1;
    }
    lastSeen[c] = right;
    best = Math.max(best, right - left + 1);
}
```

### Special Trick — `maxFreqEver` for LC 424

> Don't decrement `maxFreq` when shrinking. Stale `maxFreq` makes validity stricter, not looser → answer stays correct.

```java
int[] freq = new int[26];
int left = 0;
int maxFreq = 0;
int answer = 0;
for (int right = 0; right < s.length(); right++) {
    freq[s.charAt(right) - 'A']++;
    maxFreq = Math.max(maxFreq, freq[s.charAt(right) - 'A']);
    if (right - left + 1 - maxFreq > k) {
        freq[s.charAt(left) - 'A']--;
        left++;
    }
    answer = Math.max(answer, right - left + 1);
}
```

---

## 🔹 Template 3 — Shortest Valid Window [L12] 🟡

> Expand always; while valid, record THEN shrink. Sentinel `Integer.MAX_VALUE` → convert at return.

```java
int left = 0;
int answer = Integer.MAX_VALUE;
long sum = 0;
for (int right = 0; right < n; right++) {
    sum += nums[right];
    while (sum >= target) {
        answer = Math.min(answer, right - left + 1);
        sum -= nums[left];
        left++;
    }
}
return answer == Integer.MAX_VALUE ? 0 : answer;
```

**🏷️ Example problems:** LC 209 (Min Size Subarray Sum), LC 76 (Min Window Substring — uses `formed` counter), LC 632 (Smallest Range Covering K Lists).

### Special Trick — `formed` Counter for LC 76

> Don't compare two maps every iteration. Maintain `formed` = number of target chars whose freq in window has reached the required amount. Valid iff `formed == required.size()`.

```java
Map<Character, Integer> need = new HashMap<>();
for (char c : t.toCharArray()) {
    need.merge(c, 1, Integer::sum);
}
int required = need.size();

Map<Character, Integer> window = new HashMap<>();
int formed = 0;
int left = 0;
int bestLen = Integer.MAX_VALUE;
int bestLeft = 0;

for (int right = 0; right < s.length(); right++) {
    char c = s.charAt(right);
    window.merge(c, 1, Integer::sum);
    if (need.containsKey(c) && window.get(c).intValue() == need.get(c).intValue()) {
        formed++;
    }
    while (formed == required) {
        if (right - left + 1 < bestLen) {
            bestLen = right - left + 1;
            bestLeft = left;
        }
        char l = s.charAt(left);
        window.merge(l, -1, Integer::sum);
        if (need.containsKey(l) && window.get(l).intValue() < need.get(l).intValue()) {
            formed--;
        }
        left++;
    }
}
return bestLen == Integer.MAX_VALUE ? "" : s.substring(bestLeft, bestLeft + bestLen);
```

---

## 🔹 Template 4 — Count Exactly K [L9, L10, L11] ⭐

> Counting "exactly K" directly = case-explosion. Counting "at most K" = Template 2 + accumulate `r - l + 1`. So: `exactly(K) = atMost(K) - atMost(K-1)`.

```java
public int countExactly(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k - 1);
}

private int atMost(int[] nums, int k) {
    int left = 0;
    int count = 0;
    int distinct = 0;
    Map<Integer, Integer> freq = new HashMap<>();
    for (int right = 0; right < nums.length; right++) {
        if (freq.getOrDefault(nums[right], 0) == 0) {
            distinct++;
        }
        freq.merge(nums[right], 1, Integer::sum);
        while (distinct > k) {
            freq.merge(nums[left], -1, Integer::sum);
            if (freq.get(nums[left]) == 0) {
                distinct--;
                freq.remove(nums[left]);
            }
            left++;
        }
        count += right - left + 1;
    }
    return count;
}
```

**🏷️ Example problems:**
- LC 992 — Subarrays with K Different Integers (L11) — exactly K distinct.
- LC 930 — Binary Subarrays With Sum (L9) — exactly sum = goal (count 1s).
- LC 1248 — Count Number of Nice Subarrays (L10) — exactly K odd numbers.
- LC 2962 — Subarrays Where Max Element Appears ≥ K Times (variant).

> **Key insight:** the `count += right - left + 1` line says *"every subarray ending at `right` with start in `[left..right]` is valid because we've shrunk to the largest at-most-K window ending at `right`."*

---

## 🔹 Pattern 5 — Number-of-Substrings via "Smallest Left That Works" [L7] 🟡

> For each `right`, find the smallest `left` where the window is still valid. All starts in `[0..left-1]` give a valid window ending at `right`. Add `left` to count.

```java
int[] count = new int[3];
int left = 0;
int answer = 0;
for (int right = 0; right < s.length(); right++) {
    count[s.charAt(right) - 'a']++;
    while (count[0] >= 1 && count[1] >= 1 && count[2] >= 1) {
        count[s.charAt(left) - 'a']--;
        left++;
    }
    answer += left;
}
```

**🏷️ Example problems:** LC 1358 (Substrings Containing All Three Chars), LC 2799 (Count Complete Subarrays).

---

## 🔹 Classic Two Pointers — Converging [L1] ✅

> Sorted array, two pointers from ends, move based on comparison to target.

```java
int left = 0;
int right = nums.length - 1;
while (left < right) {
    int sum = nums[left] + nums[right];
    if (sum == target) {
        return new int[]{ left, right };
    }
    if (sum < target) {
        left++;
    } else {
        right--;
    }
}
```

**🏷️ Example problems:** LC 167 (Two Sum II), LC 15 (3Sum), LC 11 (Container With Most Water), LC 42 (Trapping Rain Water), LC 125 (Valid Palindrome).

> **Cross-ref:** Full coverage in `DSA/DeepDive/arrays-fundamentals.md` Pattern 1.

---

## 🔹 Same-Direction Two Pointers [L1] 🟡

> `slow` marks write position; `fast` scans. In-place dedup, partition, removal.

```java
int slow = 0;
for (int fast = 1; fast < nums.length; fast++) {
    if (nums[fast] != nums[slow]) {
        slow++;
        nums[slow] = nums[fast];
    }
}
return slow + 1;
```

**🏷️ Example problems:** LC 26 (Remove Duplicates), LC 27 (Remove Element), LC 283 (Move Zeroes), LC 80 (Remove Duplicates II).

---

## ⚡ State-Container Cheat Sheet

| Window State | Use | Add | Remove |
| --- | --- | --- | --- |
| Running sum | Sum-based windows | `sum += nums[r]` | `sum -= nums[l]` |
| `int[26]` | Lowercase chars | `freq[c-'a']++` | `freq[c-'a']--` |
| `int[128]` | ASCII chars | `freq[c]++` | `freq[c]--` |
| `Map<K, Integer>` | General freq | `map.merge(k, 1, Integer::sum)` | `map.merge(k, -1, Integer::sum)` + cleanup |
| `int distinct` | Number of unique elements | inc when freq 0→1 | dec when freq 1→0 |
| `int zeros` | Count of zeros (K-flip problems) | inc if `nums[r]==0` | dec if `nums[l]==0` |
| `int formed` | "Fully satisfied" target chars | inc when reaches need | dec when drops below need |
| `Deque<Integer>` | Window max/min | append; pop smaller from back | poll front if expired |

---

## ⚡ Template-Picker Decision Tree

```
"Subarray of size K"            → Template 1
"Longest subarray that..."      → Template 2
"Shortest subarray that..."     → Template 3
"Exactly K ..."                 → Template 4 (atMost(K) − atMost(K-1))
"At most K ..."                 → Template 2 / atMost helper only
"Contains all of X..."          → Pattern 5 (smallest-left-that-works)
Pair/triplet on sorted array    → Two Pointers Converging
In-place dedup / partition      → Two Pointers Same-Direction
```

---

## ⚠️ Gotchas (Silent Bugs)

---

**Negatives + sum constraint = NOT sliding window.**

```java
// ❌ LC 862 with negatives — sliding window fails
// ✅ Prefix sum + monotonic deque
```

---

**Freq map polluted with zero entries.**

```java
freq.merge(c, -1, Integer::sum);              // ❌ leaves c → 0
// ✅ decrement, then remove if zero (preserves map.size() as distinct count)
int n = freq.get(c) - 1;
if (n == 0) { freq.remove(c); } else { freq.put(c, n); }
```

---

**`Integer` `==` after autoboxing cache (values > 127).**

```java
if (window.get(c) == need.get(c)) { ... }                // ❌
if (window.get(c).intValue() == need.get(c).intValue()) { ... }    // ✅
```

---

**`int` window sum overflow.**

```java
int sum = 0;        // ❌ n=1e5 × val=1e9 = overflow
long sum = 0;       // ✅
```

---

**Forgot to convert `Integer.MAX_VALUE` sentinel.**

```java
return answer;                                          // ❌
return answer == Integer.MAX_VALUE ? 0 : answer;        // ✅
```

---

**Template 3: shrink before recording.**

```java
while (windowValid()) {
    sum -= nums[left];                                  // ❌ recorded too late
    left++;
    answer = Math.min(answer, right - left + 1);
}

while (windowValid()) {
    answer = Math.min(answer, right - left + 1);        // ✅ record FIRST
    sum -= nums[left];
    left++;
}
```

---

**Off-by-one on length.**

```java
int len = right - left;                                 // ❌
int len = right - left + 1;                             // ✅ inclusive both ends
```

---

**`s.substring(i, j)` in a hot loop.** Allocates new String each time → O(n) per call.

```java
if (isValid(s.substring(i, j))) { ... }                 // ❌ O(n) per call
// ✅ slide a window over the indices, never substring inside the loop
```

---

**HashMap when int[26] would do.**

```java
Map<Character, Integer> freq = new HashMap<>();         // ❌ 3× slower for lowercase
int[] freq = new int[26];                               // ✅
```

---

**Trying to write "exactly K" without the trick.**

```java
// ❌ Custom state-machine for "exactly K" — case explosion
// ✅ atMost(K) − atMost(K - 1). Always.
```

---

## ⚡ Quick Cheat Sheet

| If you need... | Use... | Why |
| --- | --- | --- |
| Subarray of size exactly K | **Template 1** — Fixed Window | O(1) slide update |
| Longest subarray satisfying constraint | **Template 2** — Longest Valid | Expand always, shrink while invalid |
| Shortest subarray satisfying constraint | **Template 3** — Shortest Valid | Expand always, record-then-shrink while valid |
| Count subarrays with EXACTLY K | **Template 4** — `atMost(K) − atMost(K−1)` | Avoid case-analysis |
| Count subarrays with AT MOST K | Template 2 with `count += r − l + 1` | Each valid window contributes `len` subarrays |
| Number of substrings containing all of {a, b, c} | Pattern 5 (smallest-left-that-works), add `left` | Tail-anchored counting |
| Pair sum on sorted array | Two Pointers Converging | Monotone in pointer movement |
| In-place dedup / partition / move zeros | Two Pointers Same-Direction | Slow pointer writes, fast pointer scans |
| Window max/min | Monotonic deque | O(1) amortized for max/min in window |
| Window median / k-th smallest | Two heaps | O(log n) per add/remove |
| Sum constraint with negatives | NOT sliding window | Use prefix sum + monotonic deque |

---

## 🗺️ Practice Plan — At-a-Glance

| Tier | Goal | Top 3 Problems |
| --- | --- | --- |
| **1 — Templates** | Write 4 skeletons from blank file | LC 3, LC 643, LC 209 |
| **2 — Striver warm-up** | Solve in < 15 min cold | LC 1423, LC 1004, LC 904 |
| **3 — Replacement family** | Master `maxFreqEver` trick | LC 424, LC 1358, LC 1208 |
| **4 — Exactly K family** ⭐ | Master `atMost(K) − atMost(K-1)` | LC 992, LC 930, LC 1248 |
| 🎯 **STOP — Medium-Interview Cutoff** 🎯 | | |
| **5 — Stretch 🔴** | Optional | LC 862, LC 239, LC 480 |

---

## 🔗 Cross-References

| Concept | See File |
| --- | --- |
| Full deep dive (templates, walkthroughs, special tricks) | `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md` |
| Two Pointers patterns (converging, same-direction) | `DSA/DeepDive/arrays-fundamentals.md` — Patterns 1 & 2 |
| HashMap idioms (`merge`, `getOrDefault`) | `DSA/Reference/lambdas-for-dsa-reference.md` |
| `long` for sums | `DSA/DeepDive/integer-overflow-and-limits.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Initial version.** Compact reference companion to two-pointers-sliding-window-fundamentals.md. |
