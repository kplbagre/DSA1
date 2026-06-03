# Two Pointers & Sliding Window — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to instantly recognize which two-pointer or sliding-window variant a problem needs. Not for learning the concept — for that, see `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md`.

---

## 🎯 Why You're Reading This

Two Pointers and Sliding Window are the **#1 and #2 most common patterns** in phone screens and OA rounds. The problem is they have 5+ variants that look similar but need different templates. This file teaches you to tell them apart in 10 seconds.

---

## 🔧 Essential Methods — Know These Cold

| Method | What it does | Used in |
| --- | --- | --- |
| `Arrays.sort(arr)` | Sort before converging pointers | Pattern 1 |
| `map.getOrDefault(key, 0)` | Track character frequencies in window | Patterns 4, 5 |
| `map.put(key, value)` | Update frequency after add/remove | Patterns 4, 5 |
| `map.merge(key, 1, Integer::sum)` | Increment frequency in one line (see fallback below) | Patterns 4, 5 |
| `Character.isLetterOrDigit(ch)` | Skip non-alphanumeric chars (palindrome problems) | Pattern 1 |
| `Character.toLowerCase(ch)` | Case-insensitive comparison | Pattern 1 |
| `s.charAt(i)` | Access character at index — O(1) | All patterns |
| `s.length()` | String length for loop bounds | All patterns |

> **Full reference:** `../Reference/string-operations-reference.md`, `../Reference/hashmap-section-updated.md`

### 🔄 Lambda & Shorthand Explanations with Fallbacks

**`map.merge(key, value, Integer::sum)` — Increment or decrement a frequency counter**

```java
// merge: if key absent → put(key, value); if present → put(key, oldValue + value)
// Integer::sum is shorthand for (oldVal, newVal) -> oldVal + newVal
freq.merge(ch, 1, Integer::sum);     // increment by 1
freq.merge(ch, -1, Integer::sum);    // decrement by 1

// 🔄 Fallback — if you forget merge():
freq.put(ch, freq.getOrDefault(ch, 0) + 1);   // increment
freq.put(ch, freq.getOrDefault(ch, 0) - 1);   // decrement
```

**`map.getOrDefault(key, 0)` — Safe get with default**

```java
// What it does:
//   If key EXISTS     → returns the value
//   If key is ABSENT  → returns the default (0) instead of null
int count = map.getOrDefault(key, 0);

// 🔄 Fallback:
int count = map.containsKey(key) ? map.get(key) : 0;
```

---

## 🧠 The Mental Model — Which Variant?

```
"Does the array/string problem involve a SUBARRAY or PAIR?"
│
├── PAIR (two elements)
│   │
│   ├── Array is sorted?
│   │   ├── YES → Converging Pointers (Pattern 1)
│   │   └── NO  → HashMap Lookup (see arrays-and-hashing.md)
│   │
│   └── Partition / reorder in-place?
│       └── YES → Same-Direction (slow/fast) (Pattern 2)
│
└── SUBARRAY (contiguous window)
    │
    ├── Window size is FIXED (given k)?
    │   └── YES → Fixed Sliding Window (Pattern 3)
    │
    └── Window size is VARIABLE?
        │
        ├── ALL values non-negative? (monotonic property holds)
        │   └── YES → Variable Sliding Window (Pattern 4)
        │
        └── Negative values present?
            └── Use Prefix Sum + HashMap instead (see arrays-and-hashing.md)
```

### The Monotonic Property — Why It Matters

Sliding window ONLY works when the window has a **monotonic property**: expanding the window can only make the condition "more violated" (or stay the same), and shrinking can only make it "less violated" (or stay the same).

- ✅ "Sum ≤ K" with positive values — expanding increases sum, shrinking decreases it → monotonic
- ✅ "At most K distinct characters" — expanding can add chars, shrinking can remove → monotonic
- ❌ "Sum = K" with negative values — expanding can increase OR decrease sum → NOT monotonic

**When monotonicity breaks, use Prefix Sum + HashMap instead.**

---

## 🧭 Pattern 1: Converging Pointers ⭐

**Recognition cues — reach for this when:**
- Array is **sorted**
- "Find a pair with sum = target"
- "Container with most water" (maximize area between two lines)
- "Trapping rain water" (use two pointers with max-from-left/right)

**Steps in plain English:**

1. **Place pointers** — `left = 0`, `right = n - 1`.
2. **Compute** — sum/area/whatever.
3. **Decide which to move** — if sum too small, move `left` right; if too large, move `right` left.
4. **Stop** — when `left >= right`.

```java
public int[] twoSumSorted(int[] nums, int target) {
    // Step 1 — pointers at opposite ends
    int left = 0;
    int right = nums.length - 1;

    while (left < right) {
        // Step 2 — compute
        int sum = nums[left] + nums[right];

        // Step 3 — decide
        if (sum == target) {
            return new int[]{ left, right };
        } else if (sum < target) {
            left++;
        } else {
            right--;
        }
    }
    return new int[]{};
}
```

**3Sum (LC 15) — the interviewer favorite:**

Sort the array. Fix one element, then run converging two pointers on the rest. Skip duplicates.

```java
public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();

    for (int i = 0; i < nums.length - 2; i++) {
        // Skip duplicate "fixed" elements
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;
        }

        int left = i + 1;
        int right = nums.length - 1;
        int target = -nums[i];

        while (left < right) {
            int sum = nums[left] + nums[right];
            if (sum == target) {
                result.add(Arrays.asList(nums[i], nums[left], nums[right]));
                // Skip duplicates
                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }
                left++;
                right--;
            } else if (sum < target) {
                left++;
            } else {
                right--;
            }
        }
    }
    return result;
}
```

**🏷️ Problems:** LC 167 (Two Sum II), LC 15 (3Sum), LC 11 (Container With Most Water), LC 42 (Trapping Rain Water), LC 125 (Valid Palindrome).

---

## 🧭 Pattern 2: Same-Direction (Slow/Fast) Pointers

**Recognition cues — reach for this when:**
- "Remove duplicates **in place**"
- "Move zeros to end"
- "Remove element" — in-place, O(1) space
- "Partition" array around a condition

**The roles:** `slow` = write position (where to place the next valid element). `fast` = scanner (reads every element).

**Steps in plain English:**

1. **`slow = 0`** — next position to write.
2. **`fast` scans** — for each element, if it belongs, copy to `slow` and advance both.
3. **Return `slow`** — everything before `slow` is the valid portion.

```java
public int removeDuplicates(int[] nums) {
    // Step 1 — slow marks write position
    int slow = 0;

    // Step 2 — fast scans
    for (int fast = 1; fast < nums.length; fast++) {
        if (nums[fast] != nums[slow]) {
            slow++;
            nums[slow] = nums[fast];
        }
    }

    // Step 3 — valid portion is [0..slow]
    return slow + 1;
}
```

**🏷️ Problems:** LC 26 (Remove Duplicates Sorted), LC 27 (Remove Element), LC 283 (Move Zeroes), LC 80 (Remove Duplicates II — allow 2).

---

## 🧭 Pattern 3: Fixed Sliding Window

**Recognition cues — reach for this when:**
- Window size K is **given** in the problem
- "Maximum average of subarray of size K"
- "Find all anagrams" (window = length of pattern)
- "Permutation in string" (window = length of s1)

**Steps in plain English:**

1. **Build first window** — process elements `[0..k-1]`.
2. **Slide** — for each new right, add it, remove the leftmost (at `right - k`).
3. **Check condition** at each slide.

```java
public double findMaxAverage(int[] nums, int k) {
    // Step 1 — build first window
    long sum = 0;
    for (int i = 0; i < k; i++) {
        sum += nums[i];
    }
    long best = sum;

    // Step 2 — slide: add right, remove left
    for (int right = k; right < nums.length; right++) {
        sum += nums[right];
        sum -= nums[right - k];
        best = Math.max(best, sum);
    }

    return (double) best / k;
}
```

**🏷️ Problems:** LC 643 (Max Average Subarray I), LC 438 (Find All Anagrams — fixed window with freq match), LC 567 (Permutation in String).

---

## 🧭 Pattern 4: Variable Sliding Window ⭐

**Recognition cues — reach for this when:**
- "Longest substring with at most K distinct characters"
- "Minimum window substring"
- "Longest subarray with sum ≤ K" (positive values)
- Any "longest/shortest subarray satisfying a condition" with monotonic property

**The worm metaphor:** The window is a worm. The head (right) always advances. The tail (left) only advances when the window is invalid. The worm can stretch but never fully retract.

### 🎨 Visual — The Worm

```
    left            right
     ↓                ↓
[  . | X  X  X  X  X | .  .  .  ]
      └──────────────┘
        valid window

Step 1: right advances → window might become invalid
Step 2: while invalid, left advances → window shrinks
Step 3: record answer (right - left + 1)

The worm ALWAYS moves forward. Never backward.
```

**KEY INVARIANT:** After the `while` loop, the window `[left..right]` is always valid. Record the answer here.

**Steps in plain English:**

1. **Expand** — move `right` forward, add `nums[right]` to window state.
2. **Shrink** — while window is invalid, remove `nums[left]`, advance `left`.
3. **Record** — `answer = max(answer, right - left + 1)` for longest; `answer = min(...)` for shortest.

```java
public int lengthOfLongestSubstringKDistinct(String s, int k) {
    // State: frequency map
    Map<Character, Integer> freq = new HashMap<>();
    int left = 0;
    int best = 0;

    for (int right = 0; right < s.length(); right++) {
        // Step 1 — expand: add right char
        char c = s.charAt(right);
        // merge: if key absent → put(c, 1); if present → put(c, old + 1)
        freq.merge(c, 1, Integer::sum);
        // 🔄 Fallback: freq.put(c, freq.getOrDefault(c, 0) + 1);

        // Step 2 — shrink: while too many distinct chars
        while (freq.size() > k) {
            char leftChar = s.charAt(left);
            // merge with -1: decrement count. Integer::sum = (old, -1) -> old - 1
            freq.merge(leftChar, -1, Integer::sum);
            // 🔄 Fallback: freq.put(leftChar, freq.get(leftChar) - 1);
            if (freq.get(leftChar) == 0) {
                freq.remove(leftChar);
            }
            left++;
        }

        // Step 3 — record
        best = Math.max(best, right - left + 1);
    }
    return best;
}
```

**🏷️ Problems:** LC 3 (Longest Substring Without Repeating Characters), LC 424 (Longest Repeating Character Replacement), LC 76 (Minimum Window Substring — for shortest, record inside the `while`), LC 209 (Minimum Size Subarray Sum).

---

## 🧭 Pattern 5: The atMost(K) Trick — Exact K via Subtraction

**Recognition cues — reach for this when:**
- "Count subarrays with **exactly K** distinct characters"
- "Count subarrays with **exactly K** odd numbers"
- Any "exactly K" on a subarray with a monotonic property

**The trick:** `exactly(K) = atMost(K) - atMost(K - 1)`. Write one `atMost(K)` function and call it twice.

**Steps in plain English:**

1. **Write `atMost(K)`** — sliding window counting subarrays with at most K of the property.
2. **Inside `atMost`** — at each valid window, add `right - left + 1` (number of subarrays ending at `right`).
3. **Return** `atMost(K) - atMost(K - 1)`.

```java
public int subarraysWithKDistinct(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k - 1);
}

private int atMost(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    int left = 0;
    int count = 0;

    for (int right = 0; right < nums.length; right++) {
        // merge: increment count for nums[right]. Integer::sum = (old, 1) -> old + 1
        freq.merge(nums[right], 1, Integer::sum);
        // 🔄 Fallback: freq.put(nums[right], freq.getOrDefault(nums[right], 0) + 1);

        while (freq.size() > k) {
            // merge with -1: decrement count. Integer::sum = (old, -1) -> old - 1
            freq.merge(nums[left], -1, Integer::sum);
            // 🔄 Fallback: freq.put(nums[left], freq.get(nums[left]) - 1);
            if (freq.get(nums[left]) == 0) {
                freq.remove(nums[left]);
            }
            left++;
        }

        // Key: every subarray ending at 'right' with left in [left..right] is valid
        count += right - left + 1;
    }
    return count;
}
```

**🏷️ Problems:** LC 992 (Subarrays with K Different Integers), LC 1248 (Count Number of Nice Subarrays), LC 930 (Binary Subarrays With Sum — also solvable with prefix sum).

---

## 🔬 Canonical Problem — LC 3: Longest Substring Without Repeating Characters

> **Problem:** Given a string `s`, find the length of the longest substring without repeating characters.

### Step 1 — Read and identify triggers

"The problem says **substring** (contiguous), **longest**, and **without repeating** (at most 0 duplicates). This is **Pattern 4: Variable Sliding Window**. The monotonic property holds: expanding can add duplicates, shrinking can remove them."

### Step 2 — Choose the state

"I need to know if a character is already in the window. A **HashSet** is the simplest: add on expand, remove on shrink."

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Expand** — add `s.charAt(right)` to the set.
2. **Shrink** — while the character is already in the set (duplicate), remove `s.charAt(left)` and advance `left`.
3. **Record** — `best = max(best, right - left + 1)`.

```java
public int lengthOfLongestSubstring(String s) {
    Set<Character> window = new HashSet<>();
    int left = 0;
    int best = 0;

    for (int right = 0; right < s.length(); right++) {
        // Step 2 — shrink while duplicate exists
        while (window.contains(s.charAt(right))) {
            window.remove(s.charAt(left));
            left++;
        }

        // Step 1 — expand
        window.add(s.charAt(right));

        // Step 3 — record
        best = Math.max(best, right - left + 1);
    }
    return best;
}
```

### Step 4 — Verify with example

```
s = "abcabcbb"

right=0 'a': set={a},      best=1
right=1 'b': set={a,b},    best=2
right=2 'c': set={a,b,c},  best=3
right=3 'a': 'a' in set! shrink: remove 'a', left=1. set={b,c,a}, best=3
right=4 'b': 'b' in set! shrink: remove 'b', left=2. set={c,a,b}, best=3
right=5 'c': 'c' in set! shrink: remove 'c', left=3. set={a,b,c}, best=3
right=6 'b': 'b' in set! shrink: remove 'a', left=4. Still in set!
         shrink: remove 'b', left=5. set={c,b}, best=3
right=7 'b': 'b' in set! shrink: remove 'c', left=6. shrink: remove 'b', left=7.
         set={b}, best=3

Answer: 3 ✅ ("abc")
```

### Complexity

- **Time:** O(n) — each character is added and removed from the set at most once
- **Space:** O(min(n, alphabet size)) — the set holds at most 26 lowercase letters (or 128 ASCII)

---

## ⚡ Problem Bank — Expanded

---

### LC 167: Two Sum II — Input Array Is Sorted

> **Problem:** Given a **sorted** array and target, find two numbers that sum to target. Return 1-indexed positions.

> **Approach:** Converging two pointers. Sum too small → move left. Too large → move right. No HashMap needed since sorted.

```java
int left = 0, right = nums.length - 1;
while (left < right) {
    int sum = nums[left] + nums[right];
    // +1 because problem uses 1-indexed positions
    if (sum == target) {
        return new int[]{left + 1, right + 1};
    } else if (sum < target) {
        // Sum too small — need a bigger left value
        left++;
    } else {
        // Sum too large — need a smaller right value
        right--;
    }
}
```

---

### LC 15: 3Sum

> **Problem:** Find all unique triplets in the array that sum to zero. No duplicate triplets in result. `[-1,0,1,2,-1,-4]` → `[[-1,-1,2],[-1,0,1]]`.

> **Approach:** Sort array. Fix one element, run converging two pointers on the rest. Skip duplicate "fixed" elements and duplicate pointer values.

```java
Arrays.sort(nums);
for (int i = 0; i < nums.length - 2; i++) {
    // Skip duplicate fixed elements to avoid duplicate triplets in result
    if (i > 0 && nums[i] == nums[i - 1]) continue;
    // Fix nums[i], then find two values in [i+1..n-1] that sum to -nums[i]
}
```

---

### LC 11: Container With Most Water

> **Problem:** Array of heights. Two lines form a container. Find two lines that form the container holding the most water. `[1,8,6,2,5,4,8,3,7]` → 49.

> **Approach:** Converging pointers. Area = `min(h[l], h[r]) × (r - l)`. Move the SHORTER line — moving the taller can only decrease or maintain area.

```java
// Area = shorter wall * distance between walls
int area = Math.min(height[l], height[r]) * (r - l);
best = Math.max(best, area);
// Always move the shorter wall — moving the taller one can only shrink area
if (height[l] < height[r]) {
    l++;
} else {
    r--;
}
```

---

### LC 26: Remove Duplicates from Sorted Array

> **Problem:** Given sorted array, remove duplicates **in-place** so each element appears once. Return the new length. `[1,1,2]` → `[1,2,_]`, return 2.

> **Approach:** Same-direction. `slow` = write position, `fast` scans. Only write when a new value is found.

```java
int slow = 0;
for (int fast = 1; fast < nums.length; fast++) {
    // New value found — advance write position and copy it there
    if (nums[fast] != nums[slow]) {
        slow++;
        nums[slow] = nums[fast];
    }
}
// Valid portion is [0..slow], so length = slow + 1
return slow + 1;
```

---

### LC 283: Move Zeroes

> **Problem:** Move all 0s to the end of array while maintaining relative order of non-zero elements. In-place. `[0,1,0,3,12]` → `[1,3,12,0,0]`.

> **Approach:** Same-direction swap variant. `slow` marks next non-zero position. When `fast` finds a non-zero, swap with `slow`.

```java
int slow = 0;
for (int fast = 0; fast < nums.length; fast++) {
    if (nums[fast] != 0) {
        // Swap non-zero element to the front; zeros migrate rightward
        int t = nums[slow];
        nums[slow] = nums[fast];
        nums[fast] = t;
        slow++;
    }
}
```

---

### LC 3: Longest Substring Without Repeating Characters

> **Problem:** Given a string, find the length of the longest substring without repeating characters. `"abcabcbb"` → 3 (`"abc"`).

> **Approach:** Variable sliding window with HashSet. Expand right always. Shrink left while duplicate exists.

```java
// Shrink from left until the duplicate is removed
while (window.contains(s.charAt(right))) {
    window.remove(s.charAt(left++));
}
// Expand — add current char now that window has no duplicate
window.add(s.charAt(right));
best = Math.max(best, right - left + 1);
```

---

### LC 424: Longest Repeating Character Replacement

> **Problem:** Given a string and integer `k`, you can change at most `k` characters. Find the length of the longest substring containing the same letter. `"AABABBA", k=1` → 4.

> **Approach:** Variable window. Window is valid when `windowLen - maxFreq ≤ k` (we only need to replace the non-majority characters).

```java
// Expand — count the new character's frequency
freq[s.charAt(right) - 'A']++;
// Track the highest frequency of any single char in the window
maxFreq = Math.max(maxFreq, freq[s.charAt(right) - 'A']);
// windowLen - maxFreq = chars we'd need to replace; shrink if > k
while (right - left + 1 - maxFreq > k) {
    freq[s.charAt(left++) - 'A']--;
}
```

---

### LC 76: Minimum Window Substring

> **Problem:** Given strings `s` and `t`, find the minimum window in `s` that contains all characters of `t`. `s="ADOBECODEBANC", t="ABC"` → `"BANC"`.

> **Approach:** Variable window (shortest). Record answer INSIDE the while loop (window is valid and shrinking). Track required char counts.

```java
// Record INSIDE the while (shortest variant — window is valid and shrinking)
while (valid) {
    if (right - left + 1 < minLen) {
        minLen = right - left + 1;
        start = left;
    }
    // shrink from left — remove leftmost char and re-check validity
}
```

---

### LC 992: Subarrays with K Different Integers

> **Problem:** Count subarrays with **exactly** K distinct integers. `[1,2,1,2,3], K=2` → 7 subarrays.

> **Approach:** `exactly(K) = atMost(K) - atMost(K-1)`. Write one `atMost` function with sliding window, call twice.

```java
return atMost(nums, k) - atMost(nums, k - 1);
// atMost: count += right - left + 1 at each valid step
```

---

### LC 42: Trapping Rain Water

> **Problem:** Given array of heights, compute how much water can be trapped after raining. `[0,1,0,2,1,0,1,3,2,1,2,1]` → 6 units.

> **Approach:** Converging pointers with `maxLeft` and `maxRight`. Water at each position = `min(maxL, maxR) - height[i]`. Process the shorter side first.

```java
// Process the shorter side first — it's the bottleneck for water level
if (height[l] <= height[r]) {
    // Update tallest wall seen from the left
    maxL = Math.max(maxL, height[l]);
    // Water trapped here = tallest left wall minus current height
    water += maxL - height[l];
    l++;
} else {
    maxR = Math.max(maxR, height[r]);
    water += maxR - height[r];
    r--;
}
```

---

### LC 125: Valid Palindrome

> **Problem:** Check if a string is a palindrome, considering only alphanumeric characters and ignoring case. Example: `"A man, a plan, a canal: Panama"` → `true`.

> **Approach:** Converging two pointers. Skip non-alphanumeric chars. Compare `toLowerCase` at each step.

```java
int lo = 0, hi = s.length() - 1;
while (lo < hi) {
    // Skip non-alphanumeric chars from both ends
    while (lo < hi && !Character.isLetterOrDigit(s.charAt(lo))) lo++;
    while (lo < hi && !Character.isLetterOrDigit(s.charAt(hi))) hi--;
    // Case-insensitive comparison of the two alphanumeric chars
    if (Character.toLowerCase(s.charAt(lo)) != Character.toLowerCase(s.charAt(hi))) {
        return false;
    }
    lo++;
    hi--;
}
return true;
```

---

### LC 27: Remove Element

> **Problem:** Remove all occurrences of `val` from array **in-place**, return new length. Order doesn't matter. Example: `nums = [3,2,2,3], val = 3` → `2` (nums now `[2,2,...]`).

> **Approach:** Same-direction two pointers. `slow` = write position, `fast` scans. If `nums[fast] != val`, copy to `slow` and advance both.

```java
int slow = 0;
for (int fast = 0; fast < nums.length; fast++) {
    if (nums[fast] != val) {
        nums[slow] = nums[fast];
        slow++;
    }
}
return slow;
```

---

### LC 80: Remove Duplicates from Sorted Array II

> **Problem:** Remove duplicates from sorted array so each element appears **at most twice**. In-place. Example: `nums = [1,1,1,2,2,3]` → `5` (nums `[1,1,2,2,3,...]`).

> **Approach:** Same-direction. `slow` = write position. Only write `nums[fast]` if it's different from `nums[slow - 2]` (allows at most 2 copies).

```java
int slow = 0;
for (int num : nums) {
    // Allow write if we haven't placed 2 yet, or value differs from 2 positions back
    if (slow < 2 || num != nums[slow - 2]) {
        nums[slow] = num;
        slow++;
    }
}
return slow;
```

---

### LC 643: Maximum Average Subarray I

> **Problem:** Find the contiguous subarray of length `k` with maximum average. Example: `nums = [1,12,-5,-6,50,3], k = 4` → `12.75` (subarray `[12,-5,-6,50]`).

> **Approach:** Fixed-size sliding window. Compute initial sum of first `k` elements. Slide: add right, remove left, track max sum.

```java
// Build first window of size k
int sum = 0;
for (int i = 0; i < k; i++) sum += nums[i];
int maxSum = sum;
// Slide: add incoming right element, subtract outgoing left element
for (int i = k; i < nums.length; i++) {
    sum += nums[i] - nums[i - k];
    maxSum = Math.max(maxSum, sum);
}
return (double) maxSum / k;
```

---

### LC 438: Find All Anagrams in a String

> **Problem:** Find all start indices of anagrams of `p` in `s`. Example: `s = "cbaebabacd", p = "abc"` → `[0, 6]`.

> **Approach:** Fixed window of size `p.length()`. Maintain `int[26]` frequency arrays for window and `p`. When they match → add start index.

```java
int[] pFreq = new int[26], wFreq = new int[26];
// Build target frequency from pattern
for (char c : p.toCharArray()) pFreq[c - 'a']++;
for (int i = 0; i < s.length(); i++) {
    // Expand — add incoming char to window frequency
    wFreq[s.charAt(i) - 'a']++;
    // Shrink — once window exceeds pattern length, remove leftmost char
    if (i >= p.length()) wFreq[s.charAt(i - p.length()) - 'a']--;
    // If frequencies match, the window is an anagram — record its start
    if (Arrays.equals(pFreq, wFreq)) result.add(i - p.length() + 1);
}
```

---

### LC 567: Permutation in String

> **Problem:** Given `s1` and `s2`, return true if `s2` contains a permutation of `s1`. Example: `s1 = "ab", s2 = "eidbaooo"` → `true`.

> **Approach:** Same as LC 438 but return `true` on first match instead of collecting all indices. Fixed window of size `s1.length()`.

```java
// Same as LC 438 — but return true instead of adding to result
if (Arrays.equals(pFreq, wFreq)) return true;
```

---

### LC 209: Minimum Size Subarray Sum

> **Problem:** Find the smallest subarray with sum ≥ `target`. Return its length, or 0 if none. Example: `nums = [2,3,1,2,4,3], target = 7` → `2` (subarray `[4,3]`).

> **Approach:** Variable sliding window. Expand right until sum ≥ target, then shrink left while still ≥ target, tracking min length.

```java
int left = 0, sum = 0, minLen = Integer.MAX_VALUE;
for (int right = 0; right < nums.length; right++) {
    sum += nums[right];
    // Shrink while valid — record INSIDE the while (shortest variant)
    while (sum >= target) {
        minLen = Math.min(minLen, right - left + 1);
        sum -= nums[left++];
    }
}
// MAX_VALUE means no valid window was ever found
return minLen == Integer.MAX_VALUE ? 0 : minLen;
```

---

### LC 1248: Count Number of Nice Subarrays

> **Problem:** Return the count of subarrays with exactly `k` odd numbers. Example: `nums = [1,1,2,1,1], k = 3` → `2`.

> **Approach:** atMost(K) trick. `exactly(K) = atMost(K) - atMost(K-1)`. In `atMost`, count odds in window, shrink if count > k.

```java
// atMost helper — variable window counting odds
int left = 0, odds = 0, count = 0;
for (int right = 0; right < nums.length; right++) {
    if (nums[right] % 2 == 1) odds++;
    // Shrink until we have at most k odds in the window
    while (odds > k) {
        if (nums[left++] % 2 == 1) odds--;
    }
    // Every subarray ending at right with start in [left..right] is valid
    count += right - left + 1;
}
```

---

### LC 930: Binary Subarrays With Sum

> **Problem:** Count subarrays with sum exactly equal to `goal` in a binary array. Example: `nums = [1,0,1,0,1], goal = 2` → `4`.

> **Approach:** atMost(K) trick. `exactly(goal) = atMost(goal) - atMost(goal - 1)`. Same technique as LC 1248.

```java
// atMost helper
int left = 0, sum = 0, count = 0;
for (int right = 0; right < nums.length; right++) {
    sum += nums[right];
    // Shrink until sum is at most goal
    while (sum > goal) sum -= nums[left++];
    // All subarrays [left..right], [left+1..right], ... , [right..right] are valid
    count += right - left + 1;
}
```

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty string/array** — return 0
- **All identical elements** — window shrinks to size 1 immediately
- **K = 0** — "at most 0 distinct" means empty window only
- **Single character string** — answer is 1, make sure your loop handles it

### Follow-up questions:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| LC 3 (longest no repeat) | "Can you optimize with a last-index map?" | Yes — jump `left` directly to `lastIndex[ch] + 1` instead of shrinking one-by-one |
| LC 76 (min window) | "What if we need all minimum windows?" | Track all windows of `minLen` in a list |
| Any sliding window | "Why not use Prefix Sum?" | "Prefix Sum works here too but sliding window is O(1) space" |
| 3Sum | "How do you handle duplicates?" | Skip duplicates for the fixed element AND for both pointers |
| Container With Most Water | "Why move the shorter line?" | Moving the taller line can only decrease or maintain area; moving shorter might increase it |

### Complexity traps:

- **Sliding window is O(n), not O(n²)** — even though there's a nested `while`, each element enters and exits the window at most once → amortized O(n)
- **3Sum is O(n²), not O(n³)** — fix one element (O(n)), two-pointer the rest (O(n)) → O(n²). Mention this explicitly.
- **`s.charAt()` vs `char[]` conversion** — `charAt` is O(1) but repeated calls have overhead. Converting to `char[]` first can be faster in practice. Know both.

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

Name the pattern variant:

1. "Sorted array, find pair summing to target" → ___
2. "Longest substring with at most 2 distinct characters" → ___
3. "Remove duplicates from sorted array in place" → ___
4. "Maximum average subarray of size k" → ___
5. "Count subarrays with exactly K distinct integers" → ___
6. "Minimum window substring containing all chars of t" → ___

**Answers:** 1. Converging, 2. Variable Window, 3. Same-Direction (slow/fast), 4. Fixed Window, 5. atMost(K) trick, 6. Variable Window (shortest — record inside while)

**Part 2 — Write the Template (3 minutes)**

From memory, write the Variable Sliding Window template: expand always, shrink while invalid, record after shrink. Use a `Map<Character, Integer>` as state.

**Part 3 — The Critical Difference (3 minutes)**

What is different about LC 76 (Minimum Window Substring) vs LC 3 (Longest Substring)?

**Answer:** For **longest**, record AFTER the while loop (window is valid, maximize). For **shortest**, record INSIDE the while loop (window is valid and shrinking, minimize). The template structure is the same; only the record placement changes.

**Scoring:** Part 1: 6/6 = ready. Part 2: template compiles in your head = ready. Part 3: got the record-placement difference = you own this.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Two Pointers + Sliding Window deep dive | `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md` |
| Two Pointers + Sliding Window reference (syntax) | `DSA/Reference/two-pointers-sliding-window-reference.md` |
| Prefix Sum + HashMap (when window doesn't work) | `DSA/Interview/Playbooks/arrays-and-hashing.md` — Pattern 3 |
| String operations reference | `DSA/Reference/string-operations-reference.md` |
| Java coding traps | `DSA/Implementation/java-coding-traps.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Two Pointers & Sliding Window. 5 pattern variants with recognition cues, canonical walkthrough (LC 3), 10-problem bank, monotonic property explanation, atMost(K) trick. |
| May 2026 | **Lambda/fallback pass.** Added `merge()` to Essential Methods table. Added 🔄 Lambda section with `merge` and `getOrDefault` explanations + fallbacks. Inline comments + `🔄 Fallback` at all 4 `freq.merge()` usage points in Patterns 4 and 5. |
