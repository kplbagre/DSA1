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
    │   ├── Need SUM or COUNT? → Fixed Sliding Window (Pattern 3)
    │   └── Need MAX or MIN of each window? → Monotonic Deque (Pattern 6)
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

**What this solves:** Problems on sorted arrays where you need to find a pair (or triplet) satisfying a sum/condition, or optimize area/volume between two boundary positions. Sorted order makes "move the smaller pointer" correct at every step.

**Recognition cues — reach for this when:**
- Array is **sorted**
- "Find a pair with sum = target"
- "Container with most water" (maximize area between two lines)
- "Trapping rain water" (use two pointers with max-from-left/right)

**Brute force:** Try all pairs (i, j) where i < j. O(n²) time, O(1) space.

**Key insight:** The array is sorted — if the current sum is too large, the right element is the only one that can decrease it; if too small, the left element is the only one that can increase it. Moving the correct pointer eliminates an entire row of pairs in one step.

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

**Complexity (optimal):** O(n) for a single pair; O(n²) for 3Sum (fix one element + O(n) two-pointer sweep). O(1) space.

**🏷️ Problems:** LC 167 (Two Sum II), LC 15 (3Sum), LC 11 (Container With Most Water), LC 42 (Trapping Rain Water), LC 125 (Valid Palindrome).

---

## 🧭 Pattern 2: Same-Direction (Slow/Fast) Pointers

**What this solves:** Problems that require partitioning, deduplication, or reordering an array in-place without extra space. The slow pointer marks the "write boundary" while fast scans ahead for valid elements.

**Recognition cues — reach for this when:**
- "Remove duplicates **in place**"
- "Move zeros to end"
- "Remove element" — in-place, O(1) space
- "Partition" array around a condition

**Brute force:** Copy valid elements into a new array, then copy back (or build a new list). O(n) time, O(n) space — extra array needed.

**Key insight:** Two pointers in the same direction: `fast` reads, `slow` writes. Valid elements leapfrog to the front; invalid elements get overwritten. Achieves in-place modification with O(1) extra space.

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

**Complexity (optimal):** O(n) time, O(1) space — single pass, in-place.

**🏷️ Problems:** LC 26 (Remove Duplicates Sorted), LC 27 (Remove Element), LC 283 (Move Zeroes), LC 80 (Remove Duplicates II — allow 2).

---

## 🧭 Pattern 3: Fixed Sliding Window

**What this solves:** Problems where the subarray/substring size is given (a fixed K), and you need to find the maximum, minimum, or a matching condition across all windows of that exact size.

**Recognition cues — reach for this when:**
- Window size K is **given** in the problem
- "Maximum average of subarray of size K"
- "Find all anagrams" (window = length of pattern)
- "Permutation in string" (window = length of s1)

**Brute force:** For each of the n possible window start positions, compute the sum/count/condition over all K elements. O(n × K) time, O(1) space.

**Key insight:** Adjacent windows share K-1 elements. Add the new right element and remove the outgoing left element — O(1) update instead of O(K) recomputation. One pass through n - K + 1 windows total.

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

**Complexity (optimal):** O(n) time, O(1) space — single pass, constant window state update.

**🏷️ Problems:** LC 643 (Max Average Subarray I), LC 438 (Find All Anagrams — fixed window with freq match), LC 567 (Permutation in String).

---

## 🧭 Pattern 4: Variable Sliding Window ⭐

**What this solves:** Problems asking for the longest or shortest subarray/substring satisfying a condition, where expanding always makes the condition "more violated" and shrinking makes it "less violated" (monotonic property). Does not work with negative numbers (use Prefix Sum + HashMap instead).

**Recognition cues — reach for this when:**
- "Longest substring with at most K distinct characters"
- "Minimum window substring"
- "Longest subarray with sum ≤ K" (positive values)
- Any "longest/shortest subarray satisfying a condition" with monotonic property

**Brute force:** Try all subarrays (i, j), check the condition for each. O(n²) time, O(1) space.

**Key insight:** Right pointer always advances. Left pointer only advances when the window is invalid — each element enters and exits the window at most once. Even with a nested `while`, total work is O(n) amortized.

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

**Complexity (optimal):** O(n) time, O(k) space — where k = distinct elements in the window (alphabet size, or at most n).

**🏷️ Problems:** LC 3 (Longest Substring Without Repeating Characters), LC 424 (Longest Repeating Character Replacement), LC 76 (Minimum Window Substring — for shortest, record inside the `while`), LC 209 (Minimum Size Subarray Sum).

---

## 🧭 Pattern 5: The atMost(K) Trick — Exact K via Subtraction

**What this solves:** Problems asking to count subarrays with exactly K of some property (distinct integers, odd numbers, etc.), where "exactly K" can't be maintained directly in a sliding window but "at most K" can.

**Recognition cues — reach for this when:**
- "Count subarrays with **exactly K** distinct characters"
- "Count subarrays with **exactly K** odd numbers"
- Any "exactly K" on a subarray with a monotonic property

**Brute force:** Try all subarrays (i, j), count those with exactly K of the property. O(n²) time, O(1) space.

**Key insight:** "Exactly K" is hard to maintain as a window grows/shrinks, but "at most K" is easy — just shrink when count exceeds K. The difference `atMost(K) - atMost(K-1)` gives exactly K. Write one helper, call it twice.

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

**Complexity (optimal):** O(n) time, O(k) space — two O(n) sliding window passes; window state is at most k entries.

**🏷️ Problems:** LC 992 (Subarrays with K Different Integers), LC 1248 (Count Number of Nice Subarrays), LC 930 (Binary Subarrays With Sum — also solvable with prefix sum).

---

## 🧭 Pattern 6: Monotonic Deque (Sliding Window Min/Max)

**What this solves:** Given a sliding window of fixed size k, find the maximum (or minimum) value in every window position as it moves across the array. A naive scan of k elements per window is O(nk) — the monotonic deque makes it O(n) by maintaining a decreasing sequence of useful candidates.

**Recognition cues — reach for this when:**
- "Maximum (or minimum) of every sliding window of size k"
- "Sliding window maximum" — the exact phrase
- Fixed-size window where you need an extreme value (not a sum or count)

**Brute force:** For each of the n-k+1 window positions, scan k elements to find the max. O(nk) time, O(1) space. For k=1000 and n=100,000 this is 100 million operations — too slow.

**Key insight:** As the window slides right, most elements from the previous window are still present. A new element can "dominate" (be larger than) everything before it that's still in the window — those earlier elements can never be the window max while the new element is present. A deque (double-ended queue — a data structure that allows O(1) add/remove from both ends) maintains indices in decreasing value order: front = current window max. When a new element arrives: evict from the back anything smaller (they're useless — smaller AND older). When the window moves: evict from the front if that index is now out-of-bounds.

**Steps in plain English:**

1. **Deque stores indices** (not values) in order of decreasing values — `deque.peekFirst()` = index of current window max.
2. **For each new `right` index:** evict from the **back** all indices whose value is ≤ `nums[right]` (they're dominated — can never be max while `nums[right]` is alive).
3. **Evict from the front** if `deque.peekFirst() < right - k + 1` (that index is now outside the window).
4. **Add `right`** to the back.
5. **Record result** once `right >= k - 1` (window is fully formed): `nums[deque.peekFirst()]`.

```java
public int[] maxSlidingWindow(int[] nums, int k) {
    // Deque stores INDICES; front = index of current window max
    // Values in deque are always in DECREASING order (front is largest)
    Deque<Integer> deque = new ArrayDeque<>();
    // 🔄 Fallback: LinkedList<Integer> deque = new LinkedList<>();
    int n = nums.length;
    int[] result = new int[n - k + 1];
    for (int right = 0; right < n; right++) {
        // Step 3 — evict from front: index slid out of the current window
        while (!deque.isEmpty() && deque.peekFirst() < right - k + 1) {
            deque.pollFirst();
        }
        // Step 2 — evict from back: all indices with value <= nums[right] are useless
        // nums[right] is bigger AND newer — it will outlast them in the window
        while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[right]) {
            deque.pollLast();
        }
        // Step 4 — add current index to back
        deque.offerLast(right);
        // Step 5 — record max once window is fully formed
        if (right >= k - 1) {
            result[right - k + 1] = nums[deque.peekFirst()];
        }
    }
    return result;
}
```

### 🎨 Visual — Monotonic Deque Step by Step

```
nums = [1, 3, -1, -3, 5, 3, 6, 7],  k = 3
idx:    0  1   2   3  4  5  6  7

Deque contents (storing INDICES, shown as index→value):

right=0: evict back? nothing. add 0. deque=[0→1]         window not full
right=1: evict back: nums[0]=1 ≤ 3 → pop 0. add 1. deque=[1→3]  not full
right=2: evict back? nums[1]=3 > -1 → keep. add 2. deque=[1→3, 2→-1]
         window full (right=k-1=2): result[0] = nums[deque.front=1] = 3

right=3: evict front? idx 1 ≥ 3-3+1=1 → keep. evict back? -1 > -3 → keep.
         add 3. deque=[1→3, 2→-1, 3→-3]  result[1] = nums[1] = 3

right=4: evict back: -3≤5 → pop 3; -1≤5 → pop 2; 3≤5 → pop 1. deque=[]
         add 4. deque=[4→5]   result[2] = nums[4] = 5

right=5: evict front? idx 4 ≥ 4-3+1=3 → keep. evict back? 5>3 → keep.
         add 5. deque=[4→5, 5→3]  result[3] = nums[4] = 5

right=6: evict back: 3≤6 → pop 5; 5≤6 → pop 4. add 6. deque=[6→6]
         result[4] = nums[6] = 6

right=7: evict back: 6≤7 → pop 6. add 7. deque=[7→7]
         result[5] = nums[7] = 7

Output: [3, 3, 5, 5, 6, 7] ✓

DEQUE INVARIANT:
   Values at stored indices are always in DECREASING order (front = max).
   Front eviction: index left the window (too old).
   Back eviction:  index is dominated — smaller AND older than the new element.
   Every index enters and exits the deque at most once → amortized O(1) per element.
```

**Complexity (optimal):** O(n) time, O(k) space — each of n elements is added/removed from the deque at most once; deque holds at most k indices

**🏷️ Problems:** LC 239 (Sliding Window Maximum), LC 1438 (Longest Subarray with Absolute Diff ≤ Limit).

---

## 🔬 Canonical Problem — LC 3: Longest Substring Without Repeating Characters

> **Problem:** Given a string `s`, find the length of the longest substring without repeating characters.

> **Brute force:** Try all substrings — for each pair (i, j), check if all characters in `s[i..j]` are unique. O(n²) time (O(n³) naively), O(1) space.
> **Key insight:** Right pointer always advances. Left pointer only advances when a duplicate is found. Each character enters and exits the window at most once — O(n) total even with the inner while loop.

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

> **Brute force:** Try all pairs (i, j). O(n²) time, O(1) space.
> **Key insight:** Sorted order — if sum is too large, only the right pointer can reduce it; if too small, only the left can increase it. Converging pointers eliminate all non-matching pairs in one sweep.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 15: 3Sum

> **Problem:** Find all unique triplets in the array that sum to zero. No duplicate triplets in result. `[-1,0,1,2,-1,-4]` → `[[-1,-1,2],[-1,0,1]]`.

> **Brute force:** Try all triples (i, j, k). O(n³) time, O(1) space.
> **Key insight:** Sort the array. Fix one element — the remaining two-sum on the sorted subarray is O(n) with converging pointers. Total: O(n²).
> **Approach:** Sort array. Fix one element, run converging two pointers on the rest. Skip duplicate "fixed" elements and duplicate pointer values.

```java
Arrays.sort(nums);
for (int i = 0; i < nums.length - 2; i++) {
    // Skip duplicate fixed elements to avoid duplicate triplets in result
    if (i > 0 && nums[i] == nums[i - 1]) continue;
    // Fix nums[i], then find two values in [i+1..n-1] that sum to -nums[i]
}
```

**Complexity (optimal):** O(n²) time, O(1) extra space (output not counted).

---

### LC 11: Container With Most Water

> **Problem:** Array of heights. Two lines form a container. Find two lines that form the container holding the most water. `[1,8,6,2,5,4,8,3,7]` → 49.

> **Brute force:** Try all pairs (l, r) — compute `min(h[l], h[r]) × (r - l)` for each. O(n²) time, O(1) space.
> **Key insight:** Move the shorter wall. If you move the taller wall, the height can only stay the same or decrease, and width also decreases — area can only shrink. Moving shorter gives the only chance of finding a larger area.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 26: Remove Duplicates from Sorted Array

> **Problem:** Given sorted array, remove duplicates **in-place** so each element appears once. Return the new length. `[1,1,2]` → `[1,2,_]`, return 2.

> **Brute force:** Copy unique elements into a new array, overwrite the original. O(n) time, O(n) space.
> **Key insight:** Sorted — duplicates are adjacent. `slow` pointer marks the next write position; `fast` finds the next unique value. Unique elements overwrite duplicates in-place with O(1) extra space.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 283: Move Zeroes

> **Problem:** Move all 0s to the end of array while maintaining relative order of non-zero elements. In-place. `[0,1,0,3,12]` → `[1,3,12,0,0]`.

> **Brute force:** Build a new array (non-zeros first, then zeros), copy back. O(n) time, O(n) space.
> **Key insight:** Same-direction two pointers. `slow` marks the write position for non-zeros; `fast` finds them. Swapping brings non-zeros forward while zeros naturally accumulate at the end. O(1) extra space.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 3: Longest Substring Without Repeating Characters

> **Problem:** Given a string, find the length of the longest substring without repeating characters. `"abcabcbb"` → 3 (`"abc"`).

> **Brute force:** Try all substrings, check each for uniqueness with a HashSet. O(n²) time, O(min(n, 26)) space.
> **Key insight:** Right always advances; left only advances when a duplicate enters. Each character is added and removed at most once — O(n) total despite the nested loop.
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

**Complexity (optimal):** O(n) time, O(min(n, 26)) space — set holds at most 26 ASCII letters.

---

### LC 424: Longest Repeating Character Replacement

> **Problem:** Given a string and integer `k`, you can change at most `k` characters. Find the length of the longest substring containing the same letter. `"AABABBA", k=1` → 4.

> **Brute force:** Try all substrings, find the max frequency character in each, check if replacements needed ≤ k. O(n²) time, O(26) space.
> **Key insight:** Window is valid when `windowLength - maxFreq ≤ k` — we only need to replace the non-majority characters. Track the highest character frequency in the window; shrink only when replacements would exceed k.
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

**Complexity (optimal):** O(n) time, O(26) = O(1) space — fixed alphabet frequency array.

---

### LC 76: Minimum Window Substring

> **Problem:** Given strings `s` and `t`, find the minimum window in `s` that contains all characters of `t`. `s="ADOBECODEBANC", t="ABC"` → `"BANC"`.

> **Brute force:** Try all substrings of s, check if each contains all characters of t. O(n² × m) time where m = length of t.
> **Key insight:** Variable window with two frequency maps. Expand right until all required characters are satisfied; then shrink left (recording the minimum) until the window becomes invalid again. "Satisfied" = all t chars appear with sufficient frequency.
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

**Complexity (optimal):** O(n + m) time, O(m) space — where m = distinct chars in t.

---

### LC 992: Subarrays with K Different Integers

> **Problem:** Count subarrays with **exactly** K distinct integers. `[1,2,1,2,3], K=2` → 7 subarrays.

> **Brute force:** Try all subarrays, count distinct integers in each. O(n²) time, O(n) space.
> **Key insight:** "Exactly K" is hard to maintain in a window — adding an element might push past K, removing might drop below. But `atMost(K) - atMost(K-1)` isolates exactly K using two easy sliding windows.
> **Approach:** `exactly(K) = atMost(K) - atMost(K-1)`. Write one `atMost` function with sliding window, call twice.

```java
return atMost(nums, k) - atMost(nums, k - 1);
// atMost: count += right - left + 1 at each valid step
```

**Complexity (optimal):** O(n) time, O(k) space — two linear passes; window map has at most k entries.

---

### LC 42: Trapping Rain Water

> **Problem:** Given array of heights, compute how much water can be trapped after raining. `[0,1,0,2,1,0,1,3,2,1,2,1]` → 6 units.

> **Brute force:** For each position, find the max wall to its left and max wall to its right; water = `min(maxL, maxR) - height[i]`. O(n²) time (naively scanning each side), O(1) space.
> **Key insight:** Process the shorter side first. The water level at any position is determined by the shorter of the two tallest walls seen so far — maintain running maxL and maxR with converging pointers in O(n).
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 125: Valid Palindrome

> **Problem:** Check if a string is a palindrome, considering only alphanumeric characters and ignoring case. Example: `"A man, a plan, a canal: Panama"` → `true`.

> **Brute force:** Extract only alphanumeric chars into a new string, reverse it, compare. O(n) time, O(n) space.
> **Key insight:** Converging pointers compare the first and last alphanumeric characters simultaneously, skipping non-alphanumeric. No extra string needed — O(1) space.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 27: Remove Element

> **Problem:** Remove all occurrences of `val` from array **in-place**, return new length. Order doesn't matter. Example: `nums = [3,2,2,3], val = 3` → `2` (nums now `[2,2,...]`).

> **Brute force:** Build a new array skipping `val`, copy back. O(n) time, O(n) space.
> **Key insight:** `slow` pointer tracks the next safe write position; `fast` skips matching elements. Non-matching elements are compacted to the front in-place — O(1) extra space.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 80: Remove Duplicates from Sorted Array II

> **Problem:** Remove duplicates from sorted array so each element appears **at most twice**. In-place. Example: `nums = [1,1,1,2,2,3]` → `5` (nums `[1,1,2,2,3,...]`).

> **Brute force:** Build a new array allowing at most 2 of each value, copy back. O(n) time, O(n) space.
> **Key insight:** Compare the current element to `nums[slow - 2]` (the element written 2 positions back). If different, the current element is safe to write — it can appear at most twice. Generalises to "at most K" by comparing to `nums[slow - K]`.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 643: Maximum Average Subarray I

> **Problem:** Find the contiguous subarray of length `k` with maximum average. Example: `nums = [1,12,-5,-6,50,3], k = 4` → `12.75` (subarray `[12,-5,-6,50]`).

> **Brute force:** For each of the n-k+1 windows, sum all k elements. O(n × k) time, O(1) space.
> **Key insight:** Adjacent windows share k-1 elements. Add the incoming right element, subtract the outgoing left element — O(1) update per slide instead of O(k) recomputation.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 438: Find All Anagrams in a String

> **Problem:** Find all start indices of anagrams of `p` in `s`. Example: `s = "cbaebabacd", p = "abc"` → `[0, 6]`.

> **Brute force:** For each start index in s, extract a substring of length p.length() and sort it; compare with sorted p. O(n × K log K) time where K = p.length().
> **Key insight:** Fixed window of size p.length(). An `int[26]` frequency array is an O(1) fingerprint of any lowercase window. Add incoming char, remove outgoing char — `Arrays.equals(pFreq, wFreq)` checks in O(26) = O(1).
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

**Complexity (optimal):** O(n) time, O(1) space — `int[26]` is constant size.

---

### LC 567: Permutation in String

> **Problem:** Given `s1` and `s2`, return true if `s2` contains a permutation of `s1`. Example: `s1 = "ab", s2 = "eidbaooo"` → `true`.

> **Brute force:** Generate all permutations of s1 and check if any is a substring of s2. O(m! × n) time — completely impractical.
> **Key insight:** Permutations have the same character frequencies. Slide a fixed window of size s1.length() over s2, compare frequency fingerprints — first match returns true immediately.
> **Approach:** Same as LC 438 but return `true` on first match instead of collecting all indices. Fixed window of size `s1.length()`.

```java
// Same as LC 438 — but return true instead of adding to result
if (Arrays.equals(pFreq, wFreq)) return true;
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 209: Minimum Size Subarray Sum

> **Problem:** Find the smallest subarray with sum ≥ `target`. Return its length, or 0 if none. Example: `nums = [2,3,1,2,4,3], target = 7` → `2` (subarray `[4,3]`).

> **Brute force:** Try all subarrays, track min length with sum ≥ target. O(n²) time, O(1) space.
> **Key insight:** All values are positive — expanding always increases sum, shrinking always decreases it (monotonic). Record the minimum length INSIDE the while loop: the window is valid and we're shrinking it as much as possible.
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

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 1248: Count Number of Nice Subarrays

> **Problem:** Return the count of subarrays with exactly `k` odd numbers. Example: `nums = [1,1,2,1,1], k = 3` → `2`.

> **Brute force:** Try all subarrays, count odd numbers in each. O(n²) time, O(1) space.
> **Key insight:** Map odd → 1, even → 0. Now "exactly k odd numbers" = "subarray sum exactly k." Apply `atMost(k) - atMost(k-1)` — a single helper handles both calls in O(n) total.
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

**Complexity (optimal):** O(n) time, O(1) space — two linear passes, constant extra state.

---

### LC 930: Binary Subarrays With Sum

> **Problem:** Count subarrays with sum exactly equal to `goal` in a binary array. Example: `nums = [1,0,1,0,1], goal = 2` → `4`.

> **Brute force:** Try all subarrays, sum each (binary values so sum = count of 1s). O(n²) time, O(1) space.
> **Key insight:** Binary values are non-negative → "at most goal" has a monotonic sliding window. Apply `atMost(goal) - atMost(goal - 1)` to isolate exactly-goal subarrays in two O(n) passes.
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

**Complexity (optimal):** O(n) time, O(1) space.

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
7. "Maximum of every sliding window of size k" → ___

**Answers:** 1. Converging, 2. Variable Window, 3. Same-Direction (slow/fast), 4. Fixed Window, 5. atMost(K) trick, 6. Variable Window (shortest — record inside while), 7. Monotonic Deque

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
| June 2026 | **Pattern 6 added — Monotonic Deque.** Added sliding window max/min pattern (LC 239). Updated mental model decision tree to route fixed-window max/min to Pattern 6. Updated speed drill with question 7. |
| June 2026 | **Brute Force / Key Insight pass.** Added `**What this solves**`, `**Brute force**`, `**Key insight**` to all 5 pattern blocks and canonical section (LC 3). Added `> **Brute force**`, `> **Key insight**` to all 13 problem bank entries. Added `**Complexity (optimal)**` after every code block. |
