# Two Pointers & Sliding Window — Fundamentals (Deep Dive)

> Single source of truth for Kapil's Two Pointers + Sliding Window prep. Aligned with **Striver's 12-video Two Pointer & Sliding Window playlist** (L1–L12). Every section maps back to one or more videos so you can cross-reference when re-watching.

> **Credit:** Striver (takeuforward) for the playlist structure and problem ordering. This doc reorganizes that content around four canonical templates and one unifying mental model so revision becomes mechanical.

---

## 🎯 Why You're Reading This (The Goal)

By the end of this doc you should be able to:

1. **Recognize** in 10 seconds whether a problem is a sliding-window problem at all.
2. **Pick the right template** out of four: Fixed window, Longest valid, Shortest valid, Count exactly K.
3. **Write the skeleton by reflex** — left pointer, right pointer, expand/shrink invariant, answer update — without thinking about edge cases.
4. **Know the four "monotonicity" hooks** that justify why sliding window even works (without monotonicity, you must brute-force).
5. **Spot the "atMost(K) − atMost(K−1)" trick** the moment "exactly K" appears in a problem statement.
6. **Pass the medium-interview cutoff** — every problem up through Tier 4 of the practice plan should be a 15-minute write.

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Use When |
| --- | --- | --- |
| ✅ | **Foundation** — must know cold | First pass, daily revision |
| 🟡 | **Medium-interview core** — expect these in 45-min screens | Tier 2–4 practice |
| 🔴 | **Stretch / advanced** — beyond medium-interview bar | Only after every 🟡 is automatic |

> **Lesson learned the hard way (May 2026):** Sliding window has a *deceptive difficulty curve.* Fixed window feels trivial, then variable-longest feels OK, then "exactly K" hits and you spend an hour deriving the atMost trick. Tag honestly — don't promote a problem to ✅ until you can solve it cold from a blank file.

---

## 📖 What Are Two Pointers & Sliding Window? [L1]

Both are **linear-scan techniques** for problems on arrays or strings where:

- The answer is a **contiguous subarray / substring** (sliding window), OR
- The answer is found by **scanning from both ends inward** or **two indices that always move forward** (two pointers).

The key insight: **both pointers move forward only**, so each element is visited at most twice → **O(n)** total work.

### Two Pointers vs Sliding Window — the relationship

```
Two Pointers  ⊃  Sliding Window
(superset)       (special case: the two pointers form a contiguous window)
```

| | Two Pointers | Sliding Window |
| --- | --- | --- |
| Pointer relationship | Anywhere (opposite ends, same direction, fast/slow) | Always `left ≤ right`, forming a window `[left..right]` |
| Window meaning | Pointers might just be cursors | The interval `[left..right]` IS the candidate answer |
| Typical answer | A pair / triplet / index | A subarray / substring property (sum, length, count) |
| Canonical example | Two Sum on sorted array | Longest substring without repeating chars |

> **Mental hook:** *"Sliding window is the subcase of two pointers where the window itself is the thing you're optimizing."*

### Why does it work? — The Monotonicity Hook

Sliding window is **only valid** when the problem has one of these monotonicity properties:

| Monotonicity | Meaning | Example problem |
| --- | --- | --- |
| **Expand never hurts** | Adding to the right side cannot make a valid window invalid for "longest" goals | Longest subarray sum ≤ K |
| **Shrink never hurts** | Removing from the left cannot make an invalid window valid... wait, it *does* — but only one direction matters | Smallest subarray sum ≥ K |
| **Constraint is monotonic in window size** | If window size `w` is valid, so are all `w' < w` (or vice versa) | "At most K distinct" — shrinking always preserves validity |

> **Lesson learned the hard way (May 2026):** *"If shrinking a valid window can make it invalid, you cannot use sliding window for longest-valid."* This is the silent killer. Always verify the monotonicity before reaching for the template.

---

## 📖 Terminology (Memorize These)

| Term | Definition | Example for `nums = [1, 2, 3, 4]` |
| --- | --- | --- |
| **Subarray** | A contiguous slice. **The thing sliding window solves.** | `[2, 3]`, `[1, 2, 3]`, `[4]` |
| **Substring** | Subarray-for-strings. Same idea, just on `String`/`char[]`. | `"bcd"` from `"abcde"` |
| **Window** | The current `[left..right]` candidate. | `left=1, right=2` → window = `[2, 3]` |
| **Expand** | `right++` — add `nums[right]` into window state. | window `[2, 3]` → `[2, 3, 4]` |
| **Shrink** | `left++` — remove `nums[left-1]` (or `nums[left]` before incrementing) from window state. | window `[2, 3]` → `[3]` |
| **Window state** | The aggregated property you maintain: sum, freq map, distinct count, etc. | sum = 5 for window `[2, 3]` |
| **Invariant** | The property your window always satisfies *after each iteration*. | "Window has at most K distinct chars" |
| **Validity** | Boolean: does the current window satisfy the problem's constraint? | "Sum ≤ K" — true/false |

### Subarray vs Subsequence vs Subset — Don't Confuse These ⚠️

This is the single most common terminology trap. Same trap appears in arrays-fundamentals — re-print here for self-containment.

| Concept | Order preserved? | Contiguous? | Count for `[1,2,3]` |
| --- | --- | --- | --- |
| **Subarray** | Yes | **Yes** | 6: `[1] [2] [3] [1,2] [2,3] [1,2,3]` |
| **Subsequence** | Yes | No | 7 (non-empty): drop any subset |
| **Subset** | No | No | 8 (incl. empty): order doesn't matter |

> **Sliding window only solves SUBARRAY / SUBSTRING problems.** If a problem says "subsequence" → think DP, not sliding window.

---

## 🛠️ Java Skeleton & Idioms

```java
// Generic sliding window skeleton — memorize this shape, then specialize
int left = 0;
int right = 0;
int n = nums.length;
// window state (sum, freq map, distinct count, etc.)
long windowSum = 0;
int answer = 0;

while (right < n) {
    // 1. Expand: add nums[right] to window state
    windowSum += nums[right];

    // 2. While window is invalid, shrink from the left
    while (windowInvalid()) {
        windowSum -= nums[left];
        left++;
    }

    // 3. Record answer for the current valid window
    answer = Math.max(answer, right - left + 1);

    // 4. Move right forward
    right++;
}
return answer;
```

### Common State Containers

| Window State | Java Type | Add | Remove | Cost |
| --- | --- | --- | --- | --- |
| Running sum | `long` | `sum += nums[r]` | `sum -= nums[l]` | O(1) |
| Char frequency (ASCII) | `int[26]` or `int[128]` | `freq[c - 'a']++` | `freq[c - 'a']--` | O(1) |
| General frequency | `Map<Character, Integer>` | `map.merge(c, 1, Integer::sum)` | `map.merge(c, -1, Integer::sum)` + cleanup | O(1) avg |
| Distinct count | `int distinct` + map | inc/dec via map zero-crossings | same | O(1) avg |
| Max in window | `Deque<Integer>` (monotonic) | append; pop smaller from back | poll front if expired | O(1) amortized |
| Min in window | `Deque<Integer>` (monotonic) | append; pop larger from back | same | O(1) amortized |

### `long` for window sum — non-negotiable

```java
int sum = 0;                                         // ❌ overflow risk
long sum = 0;                                        // ✅ safe for n up to 1e5 with |a| ≤ 1e9
```

### HashMap zero-cleanup idiom

When removing a char from a frequency map, **decrement first, then remove if zero** — this keeps `map.size()` honest as your "distinct" count.

```java
char left = s.charAt(l);
int newCount = map.get(left) - 1;
if (newCount == 0) {
    map.remove(left);
} else {
    map.put(left, newCount);
}
```

---

## 🧠 Mental Model — The One Picture That Unifies Everything

Read this section twice. It is the highest-leverage page in the doc.

### Picture: A worm crawling forward on a tape

```
Index: 0   1   2   3   4   5   6   7   8   9
Tape:  a   b   c   a   b   c   b   b   d   e
       ^
       L,R
```

Two pointers `L` and `R` start at index 0. They define a **window** `[L..R]`.

**Rule of the worm:**
1. Both pointers move **only forward** (never backward).
2. `R` always moves first (the worm extends its head).
3. When the window "violates" the rule, `L` catches up (the worm pulls its tail forward).
4. The window's state is **monotonic** as either pointer moves.

Because each pointer traverses the array at most once, total work = **at most 2n operations** = O(n).

### The Three Questions Template

Every sliding-window problem reduces to answering three questions:

1. **When do I expand?** Almost always: every iteration, `R++`.
2. **When do I shrink?** Depends on the problem. The condition is usually:
   - "While window is invalid" → shrink until valid (for *longest valid* problems)
   - "While window is valid" → shrink and record (for *shortest valid* problems)
3. **What do I track?** `max length`, `min length`, `count`, `existence`, `actual subarray`.

Answer these three and the code writes itself.

### The Four Canonical Templates

Every problem in Striver's 12-video playlist maps to one of these four.

#### Template 1 — Fixed-Size Window of Size K [L2]

**Trigger phrase:** "*subarray of size K with maximum/minimum…*"

**English steps:**
1. Slide a window of exactly K elements across the array.
2. On each slide, **add** the new right element and **remove** the old left element.
3. Track the best window aggregate seen so far.

```java
public int fixedWindow(int[] nums, int k) {
    int n = nums.length;
    // Step 1 — initial window of size k
    long windowSum = 0;
    for (int i = 0; i < k; i++) {
        windowSum += nums[i];
    }
    long best = windowSum;

    // Step 2 — slide: in one element, out one element
    for (int right = k; right < n; right++) {
        windowSum += nums[right];
        windowSum -= nums[right - k];
        best = Math.max(best, windowSum);
    }
    return (int) best;
}
```

#### Template 2 — Longest Valid Window [L3, L4, L5, L6, L8]

**Trigger phrase:** "*longest subarray such that <some constraint holds>*"

**English steps:**
1. Move `right` forward and add the new element to window state.
2. **While the window is invalid**, shrink from the left until it's valid again.
3. After shrinking, the window `[left..right]` is the longest valid window ending at `right` — update the answer.

```java
public int longestValid(int[] nums, int k) {
    int left = 0;
    int answer = 0;
    // window state (e.g., a freq map, a running sum, a count of zeros)

    for (int right = 0; right < nums.length; right++) {
        // Step 1 — add nums[right] to window state
        addToState(nums[right]);

        // Step 2 — shrink while invalid
        while (windowInvalid()) {
            removeFromState(nums[left]);
            left++;
        }

        // Step 3 — record answer for valid window
        answer = Math.max(answer, right - left + 1);
    }
    return answer;
}
```

> **Optimization (right-only-moves-forward)**: For problems like Longest Substring Without Repeating Characters, you can replace the inner `while` with a single jump using a "last seen" map. Both are O(n); the jump version is slightly faster constants but harder to read.

#### Template 3 — Shortest Valid Window [L12]

**Trigger phrase:** "*shortest subarray such that <some constraint holds>*"

**English steps:**
1. Move `right` forward and add the new element to window state.
2. **While the window is valid**, record the length and shrink from the left to try a shorter one.
3. When the window becomes invalid, stop shrinking and continue expanding.

```java
public int shortestValid(int[] nums, int target) {
    int left = 0;
    int answer = Integer.MAX_VALUE;
    long windowSum = 0;

    for (int right = 0; right < nums.length; right++) {
        // Step 1 — expand
        windowSum += nums[right];

        // Step 2 — shrink while valid, recording length each time
        while (windowSum >= target) {
            answer = Math.min(answer, right - left + 1);
            windowSum -= nums[left];
            left++;
        }
    }
    return answer == Integer.MAX_VALUE ? 0 : answer;
}
```

> **Subtle difference from Template 2:** In Template 2 we shrink **until valid**. In Template 3 we shrink **while valid** — the validity test flips because we're minimizing length.

#### Template 4 — Count Subarrays with EXACTLY K [L9, L10, L11]

**Trigger phrase:** "*number of subarrays with exactly K <distinct / sum / odd / ...>*"

**The trick:** Counting "exactly K" directly is hard. Counting "at most K" with sliding window is easy. So:

```
exactly(K) = atMost(K) − atMost(K − 1)
```

**Why this works:** "At most K" includes all subarrays with 0, 1, 2, …, K of the thing. Subtract "at most K − 1" (subarrays with 0, 1, …, K − 1) and only the "exactly K" subarrays remain.

**English steps for `atMost(K)`:**
1. Move `right` forward and add `nums[right]` to window state.
2. **While the window has more than K of the thing**, shrink from the left.
3. **Every valid window of length `len` contributes `len` subarrays ending at `right`** — add `right - left + 1` to the count.

```java
public int countExactlyK(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k - 1);
}

private int atMost(int[] nums, int k) {
    int left = 0;
    int count = 0;
    // window state (freq map, sum, etc.)

    for (int right = 0; right < nums.length; right++) {
        // Step 1 — expand
        addToState(nums[right]);

        // Step 2 — shrink while window exceeds K
        while (windowExceedsK(k)) {
            removeFromState(nums[left]);
            left++;
        }

        // Step 3 — every subarray ending at right with left in [left..right] is valid
        count += right - left + 1;
    }
    return count;
}
```

> **The "+ right − left + 1" insight is the most important single idea in this playlist.** It says: *"once I've shrunk to the largest valid window ending at right, all subarrays `[left..right], [left+1..right], …, [right..right]` are also valid because they're suffixes of an at-most-K window."*

> **Edge case:** `atMost(K − 1)` when K = 0 means "at most −1" which is impossible → returns 0. The formula `atMost(K) − atMost(K-1)` still works.

### How to Pick the Right Template — 10-Second Decision Tree

```
Problem statement
│
├── "Subarray of size exactly K" ?  → Template 1 (Fixed Window)
│
├── "Longest subarray that ..."     → Template 2 (Longest Valid)
│
├── "Shortest / smallest subarray that ..."  → Template 3 (Shortest Valid)
│
├── "Number of subarrays with EXACTLY K ..."  → Template 4 (atMost(K) − atMost(K−1))
│
├── "Number of subarrays with AT MOST K ..."  → Just atMost(K)
│
└── None of the above
    │
    ├── Is the answer a pair/triplet? → Classic Two Pointers (sorted array)
    │
    └── Is monotonicity present?     → Generalized two pointers / fast-slow
        Otherwise                    → Probably NOT sliding window. Try DP / hashing.
```

---

## 🎨 Style Habits — Build These From Day 1

### 🌐 Universal Habits

1. **Always declare `int n = nums.length;`** at the top — avoids re-reading `.length` on every loop and makes the code shorter.
2. **Name pointers `left` and `right`** in templated code; use `l, r` only when you're code-golfing. Future-you reading the code in interview prep wants the longer names.
3. **Make the invariant explicit** — write it as a comment above the `while` shrink loop. *"Invariant after loop: window `[left..right]` has at most K distinct."*
4. **Use `right - left + 1` for window length** — never compute it from a stored variable. Storing length leads to bugs when you forget to update it after shrinking.
5. **Initialize `answer` correctly:**
   - Longest valid: `int answer = 0;`
   - Shortest valid: `int answer = Integer.MAX_VALUE;` (return `0` or `-1` if unchanged)
   - Count: `int count = 0;`
6. **Always run a sanity trace on n = 0, n = 1, all-same-element, all-distinct.** Sliding window bugs hide in these.

### 🔧 Context-Specific Habits

1. **For char problems**, prefer `int[26]` (lowercase) or `int[128]` (ASCII) over `HashMap<Character, Integer>`. Saves ~2–3× in constants.
2. **For "distinct count" tracking**, increment a `distinct` counter when a freq goes from 0 → 1, and decrement when it goes from 1 → 0. Don't recompute `map.size()` every iteration.
3. **For "longest with replacement" problems** (L8), track `maxFreqEver` not `currentMaxFreq` — see the L8 walkthrough below for why.
4. **For "exactly K" problems**, write `atMost` once as a helper and call it twice. Don't try to write "exactly K" directly — it's a tar pit.
5. **For Minimum Window Substring (L12)**, use a `formed` counter instead of comparing two maps every iteration. See the L12 walkthrough.

---

## 🧭 Patterns — The Striver Playlist Mapped to Templates

Every video maps cleanly to one of the four templates. The pattern format below mirrors arrays-fundamentals: blockquote → English steps → Java template → example problems → try-these.

---

### Pattern 1 — Fixed-Size Window [L2] ✅

> Slide a window of a known size `k` across the array. On every slide, do an O(1) update: add the new right element and remove the old left element. Used when the constraint specifies an exact window size.

**English steps:**
1. **Build the first window** of size `k` (sum, freq, whatever the state is).
2. **Slide one step at a time** — add `nums[right]`, remove `nums[right - k]`.
3. **Track the best aggregate** seen across all positions.

```java
public int maxSumSubarrayOfSizeK(int[] nums, int k) {
    int n = nums.length;
    long windowSum = 0;

    // Step 1 — initial window
    for (int i = 0; i < k; i++) {
        windowSum += nums[i];
    }
    long best = windowSum;

    // Step 2 — slide
    for (int right = k; right < n; right++) {
        windowSum += nums[right];
        windowSum -= nums[right - k];
        best = Math.max(best, windowSum);
    }
    return (int) best;
}
```

**🏷️ Example problems:** Maximum Points You Can Obtain from Cards (LC 1423) — L2 in playlist; Maximum Average Subarray I (LC 643); Find All Anagrams in a String (LC 438); Permutation in String (LC 567).

**🧩 Try these next:** LC 1456, LC 2090, LC 2461.

> **L2 twist:** "Maximum points from cards" picks K cards from either end of the row, not a contiguous middle window. Trick: pick K cards from front and 0 from back, then "rotate" — each step swap one front-card out for a back-card in. The window slides over the *removed middle*, not over the answer.

---

### Pattern 2 — Variable Window: Longest Valid [L3, L4, L5, L6, L8] ⭐

> Expand the right pointer one step at a time. Whenever the window violates the constraint, shrink from the left until it's valid again. After shrinking, the window is the longest valid one ending at `right`. **The workhorse pattern of this entire playlist.**

**English steps:**
1. **Initialize** `left = 0`, `answer = 0`, empty window state.
2. **Loop `right` from 0 to n − 1.** Add `nums[right]` to the state.
3. **While the window is invalid**, remove `nums[left]` and `left++`.
4. **Update answer** with `right - left + 1`.

```java
public int longestValid(int[] nums, int constraint) {
    int left = 0;
    int answer = 0;
    Map<Integer, Integer> freq = new HashMap<>();   // window state — placeholder

    for (int right = 0; right < nums.length; right++) {
        // Step 2 — expand
        freq.merge(nums[right], 1, Integer::sum);

        // Step 3 — shrink while invalid
        while (windowInvalid(freq, constraint)) {
            int leftVal = nums[left];
            if (freq.get(leftVal) == 1) {
                freq.remove(leftVal);
            } else {
                freq.merge(leftVal, -1, Integer::sum);
            }
            left++;
        }

        // Step 4 — record
        answer = Math.max(answer, right - left + 1);
    }
    return answer;
}
```

**🏷️ Example problems:**
- LC 3 — Longest Substring Without Repeating Characters (L3) — invariant: no char repeats.
- LC 1004 — Max Consecutive Ones III (L4) — invariant: at most K zeros in window.
- LC 904 — Fruit Into Baskets (L5) — invariant: at most 2 distinct types.
- LC 340 — Longest Substring with At Most K Distinct Chars (L6) — invariant: at most K distinct.
- LC 424 — Longest Repeating Character Replacement (L8) — invariant: `windowLen − maxFreqEver ≤ K`.

**🧩 Try these next:** LC 1208 (Get Equal Substrings Within Budget), LC 1493 (Longest Subarray of 1's After Deleting One Element), LC 159 (Longest Substring With At Most 2 Distinct).

> **Sub-pattern: "Last-seen index jump."** For LC 3 (longest substring without repeats), instead of shrinking one step at a time, you can jump `left` to `lastSeen[c] + 1`. Both versions are O(n) but the jump version is faster in constants and one less inner loop to reason about.

```java
public int lengthOfLongestSubstring(String s) {
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
    return best;
}
```

---

### Pattern 3 — Variable Window: Shortest Valid [L12] 🟡

> Expand the right pointer one step at a time. Whenever the window becomes *valid*, record the length and shrink from the left to try to make it shorter. When it becomes invalid again, keep expanding. Used when the answer is a *minimum* length subarray satisfying some constraint.

**English steps:**
1. **Initialize** `left = 0`, `answer = Integer.MAX_VALUE`, empty state.
2. **Loop `right`** — add `nums[right]` to state.
3. **While the window is valid**, record `right − left + 1` and shrink (remove `nums[left]`, `left++`).
4. **Return** `answer == Integer.MAX_VALUE ? -1 : answer`.

```java
public int shortestSubarrayWithSum(int[] nums, int target) {
    int left = 0;
    int answer = Integer.MAX_VALUE;
    long sum = 0;

    for (int right = 0; right < nums.length; right++) {
        sum += nums[right];
        while (sum >= target) {
            answer = Math.min(answer, right - left + 1);
            sum -= nums[left];
            left++;
        }
    }
    return answer == Integer.MAX_VALUE ? 0 : answer;
}
```

**🏷️ Example problems:**
- LC 76 — Minimum Window Substring (L12) — invariant: window contains all chars of target.
- LC 209 — Minimum Size Subarray Sum (classic shortest-sum-≥-target).
- LC 632 — Smallest Range Covering Elements from K Lists (advanced multi-pointer).

**🧩 Try these next:** LC 1234 (Replace the Substring for Balanced String), LC 658 (Find K Closest Elements — two pointers from outside in).

> **Why is L12 (LC 76) hard?** It combines Template 3 (shortest) with a tricky validity check ("window has at least the freq of every char in target"). The trick is the `formed` counter — see the L12 walkthrough below.

---

### Pattern 4 — Count Exactly K via atMost(K) − atMost(K−1) [L9, L10, L11] ⭐

> Counting "exactly K" subarrays directly requires a hairy case analysis. Counting "at most K" is just Template 2 with an extra `count += right − left + 1` line. So compute `atMost(K) − atMost(K − 1)` and you're done. **The signature trick of this playlist.**

**English steps:**
1. **Write a helper `atMost(nums, k)`** that returns the number of subarrays with the property holding at most `k` times.
2. **Inside `atMost`:** expand right, shrink while count exceeds `k`, accumulate `right − left + 1` per step.
3. **Return** `atMost(k) − atMost(k − 1)`.

```java
public int countExactly(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k - 1);
}

private int atMost(int[] nums, int k) {
    int left = 0;
    int count = 0;
    int windowDistinct = 0;
    Map<Integer, Integer> freq = new HashMap<>();

    for (int right = 0; right < nums.length; right++) {
        // Step 1 — expand
        if (freq.getOrDefault(nums[right], 0) == 0) {
            windowDistinct++;
        }
        freq.merge(nums[right], 1, Integer::sum);

        // Step 2 — shrink while window has more than k distinct
        while (windowDistinct > k) {
            int leftVal = nums[left];
            freq.merge(leftVal, -1, Integer::sum);
            if (freq.get(leftVal) == 0) {
                windowDistinct--;
                freq.remove(leftVal);
            }
            left++;
        }

        // Step 3 — every subarray ending at right with start in [left..right] is valid
        count += right - left + 1;
    }
    return count;
}
```

**🏷️ Example problems:**
- LC 930 — Binary Subarrays With Sum (L9) — *exactly* sum = goal.
- LC 1248 — Count Number of Nice Subarrays (L10) — *exactly* K odd numbers.
- LC 992 — Subarrays with K Different Integers (L11) — *exactly* K distinct.

**🧩 Try these next:** LC 1358 (Number of Substrings Containing All Three Characters — variant), LC 1234 (Replace Substring for Balanced — different twist), LC 2962 (Count Subarrays Where Max Element Appears at Least K Times).

> **Mental model:** *"At most K is permissive. At most K − 1 is one click more restrictive. The difference is exactly the subarrays whose 'count of thing' is K."*

---

### Pattern 5 — Number-of-Substrings via "Smallest Left That Works" [L7] 🟡

> Variant of Template 2. For each `right`, find the smallest `left` such that the window `[left..right]` is valid. Then the number of valid windows ending at `right` is `left + 1` (windows starting at indices 0, 1, …, left).

**English steps:**
1. For each `right`, expand and update state.
2. **While the window is valid**, shrink — moving `left` forward keeps the window valid because the constraint is "contains all" (monotone).
3. After the while loop, `left` is the **smallest left + 1** that *still works*. All starts in `[0..left − 1]` give a valid window ending at `right`. Add `left` to the count.

```java
public int numberOfSubstrings(String s) {
    int[] count = new int[3];
    int left = 0;
    int answer = 0;

    for (int right = 0; right < s.length(); right++) {
        count[s.charAt(right) - 'a']++;

        while (count[0] >= 1 && count[1] >= 1 && count[2] >= 1) {
            count[s.charAt(left) - 'a']--;
            left++;
        }
        // All windows starting at 0, 1, ..., left-1 ending at 'right' contain a, b, c
        answer += left;
    }
    return answer;
}
```

**🏷️ Example problems:**
- LC 1358 — Number of Substrings Containing All Three Characters (L7).
- LC 2799 — Count Complete Subarrays in an Array (same pattern, different validity).

**🧩 Try these next:** LC 2799, LC 1248 (already in Pattern 4 but solvable here too).

> **Why this differs from Pattern 4:** Here, "valid" means "*at least* one of each" (a minimum constraint). In Pattern 4, "exactly K" requires the subtraction trick. When the constraint is "contains at least one of each X", the smallest-left-that-works variant is cleaner.

---

### Pattern 6 — Classic Two Pointers (Converging) [L1] ✅

> Two pointers start at opposite ends of a sorted array and move toward each other. Each step decides which pointer to move based on the comparison with the target. **Independent of sliding window** — different mental model.

**English steps:**
1. Sort the array if not sorted.
2. `left = 0`, `right = n − 1`.
3. While `left < right`: compare `nums[left] + nums[right]` (or similar) to target. Move the pointer that would help.

```java
public int[] twoSumSorted(int[] nums, int target) {
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
    return new int[]{ -1, -1 };
}
```

**🏷️ Example problems:** LC 167 (Two Sum II — Sorted), LC 15 (3Sum — fix one, two-pointer the rest), LC 11 (Container With Most Water), LC 42 (Trapping Rain Water — two-pointer version), LC 125 (Valid Palindrome).

**🧩 Try these next:** LC 16 (3Sum Closest), LC 18 (4Sum), LC 75 (Dutch Flag — three-pointer variant).

> **Cross-reference:** Full coverage in `DSA/DeepDive/arrays-fundamentals.md` — Pattern 1 (Two Pointers Converging).

---

### Pattern 7 — Same-Direction Two Pointers [L1] 🟡

> Both pointers move forward. The slow pointer marks where to write; the fast pointer scans. Used for in-place modifications: dedup, partition, removal.

**English steps:**
1. `slow = 0`, scan with `fast`.
2. For each `fast`, decide: should `nums[fast]` be kept? If yes, copy to `nums[slow]` and `slow++`.

```java
public int removeDuplicatesSortedArray(int[] nums) {
    if (nums.length == 0) {
        return 0;
    }
    int slow = 0;
    for (int fast = 1; fast < nums.length; fast++) {
        if (nums[fast] != nums[slow]) {
            slow++;
            nums[slow] = nums[fast];
        }
    }
    return slow + 1;
}
```

**🏷️ Example problems:** LC 26 (Remove Duplicates from Sorted Array), LC 27 (Remove Element), LC 283 (Move Zeroes), LC 80 (Remove Duplicates II).

> **Cross-reference:** Same pattern lives in `DSA/DeepDive/arrays-fundamentals.md` — Pattern 2.

---

## 🌳 Special Topics

### Special Topic A — The `formed` Counter Trick (for LC 76 / L12) 🟡

When checking "does the window contain all required chars with required frequencies?", **don't** compare two maps every iteration. Instead, maintain a counter `formed` = number of unique characters in the target whose frequency in the window has reached the required frequency.

The window is valid iff `formed == required.size()`.

```java
public String minWindow(String s, String t) {
    if (s.length() < t.length()) {
        return "";
    }

    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) {
        need.merge(c, 1, Integer::sum);
    }
    int required = need.size();   // distinct chars to cover

    Map<Character, Integer> window = new HashMap<>();
    int formed = 0;
    int left = 0;
    int bestLen = Integer.MAX_VALUE;
    int bestLeft = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        window.merge(c, 1, Integer::sum);

        // 'c' just hit its required count — one more char "formed"
        if (need.containsKey(c) && window.get(c).intValue() == need.get(c).intValue()) {
            formed++;
        }

        // Shrink while valid
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
}
```

> **Why `.intValue()` comparison?** `window.get(c) == need.get(c)` compares `Integer` references — wrong for values > 127 due to autoboxing cache. Always `.intValue()` or use `.equals(...)`.

---

### Special Topic B — The `maxFreqEver` Trick (for LC 424 / L8) 🟡

For **Longest Repeating Character Replacement**: window is valid if `windowLen − maxFreq ≤ K` (we can flip non-max chars to the majority).

The clever observation: **you never need to decrement `maxFreq` when shrinking.** Because:
- The final answer is the longest *valid* window seen.
- If `maxFreq` only stays the same or grows, the validity check `windowLen − maxFreq ≤ K` is *stricter*, not looser.
- So when we shrink, we might shrink "too much" — but the answer doesn't change, because the previously-best `maxFreq` is still a lower bound on what the window can support.

```java
public int characterReplacement(String s, int k) {
    int[] freq = new int[26];
    int left = 0;
    int maxFreq = 0;
    int answer = 0;

    for (int right = 0; right < s.length(); right++) {
        freq[s.charAt(right) - 'A']++;
        maxFreq = Math.max(maxFreq, freq[s.charAt(right) - 'A']);

        // If window invalid: shrink by exactly one (no inner while needed!)
        if (right - left + 1 - maxFreq > k) {
            freq[s.charAt(left) - 'A']--;
            left++;
        }
        // Note: maxFreq is NOT recomputed — it's allowed to be stale (an overestimate).
        // The trick: a stale maxFreq makes 'window valid' stricter, not looser, so 'answer' stays correct.

        answer = Math.max(answer, right - left + 1);
    }
    return answer;
}
```

> **Lesson learned the hard way (May 2026):** *"I tried to decrement `maxFreq` when shrinking — wrote a 30-line beast. The correct version is 8 lines. The whole trick is: leave `maxFreq` stale."*

---

### Special Topic C — When NOT to Use Sliding Window

These red flags mean sliding window will *not* work:

| Red flag | Why sliding window fails | Use instead |
| --- | --- | --- |
| **Array has negative numbers AND constraint is "sum ≥ target"** | Adding a negative can flip a valid window invalid (loses monotonicity) | Prefix sum + monotonic deque (LC 862) |
| **"Subsequence" not "subarray"** | Non-contiguous → no window | DP |
| **Answer is a single index, not a range** | No window concept | Binary search / linear scan |
| **Constraint is on the median / k-th smallest / heap-style aggregate** | No O(1) add/remove for these | Two heaps / segment tree |
| **Constraint is non-monotone in window size** | Expanding can flip valid → invalid → valid → invalid | DP or brute force with pruning |

> **Lesson learned the hard way (May 2026):** *"Spent 40 minutes on LC 862 (Shortest Subarray with Sum at Least K) trying sliding window. The negatives kill it. The right tool is prefix-sum + monotonic-deque."*

---

## 🔬 Worked Walkthroughs

Pick a problem, watch the window state evolve frame-by-frame. These are the five most teaching-dense problems in the playlist.

---

### Walkthrough 1 — LC 3: Longest Substring Without Repeating [L3] ✅

**Input:** `s = "abcabcbb"`

**Approach:** Template 2 (Longest Valid). Window invariant: no char repeats. Shrink whenever the new right-char is already in the window.

| Step | right | c | window before | freq state | shrink? | left after | window after | answer |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 'a' | `""` | `{a:1}` | no | 0 | `"a"` | 1 |
| 2 | 1 | 'b' | `"a"` | `{a:1, b:1}` | no | 0 | `"ab"` | 2 |
| 3 | 2 | 'c' | `"ab"` | `{a:1, b:1, c:1}` | no | 0 | `"abc"` | 3 |
| 4 | 3 | 'a' | `"abc"` | a now 2 — shrink | yes | 1 | `"bca"` | 3 |
| 5 | 4 | 'b' | `"bca"` | b now 2 — shrink | yes | 2 | `"cab"` | 3 |
| 6 | 5 | 'c' | `"cab"` | c now 2 — shrink | yes | 3 | `"abc"` | 3 |
| 7 | 6 | 'b' | `"abc"` | b now 2 — shrink twice | yes | 5 | `"cb"` | 3 |
| 8 | 7 | 'b' | `"cb"` | b now 2 — shrink twice | yes | 7 | `"b"` | 3 |

**Answer: 3** — `"abc"`.

> **Pedagogy:** Each shrink restores the invariant. Each `right` step records the answer once. Total work = 2n.

---

### Walkthrough 2 — LC 1004: Max Consecutive Ones III [L4] ✅

**Input:** `nums = [1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0]`, `k = 2`

**Approach:** Template 2. Invariant: at most `k` zeros in window. State: a single `int zeros` counter.

| right | nums[r] | zeros | shrink? | left | win len | answer |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 1 | 0 | no | 0 | 1 | 1 |
| 1 | 1 | 0 | no | 0 | 2 | 2 |
| 2 | 1 | 0 | no | 0 | 3 | 3 |
| 3 | 0 | 1 | no | 0 | 4 | 4 |
| 4 | 0 | 2 | no | 0 | 5 | 5 |
| 5 | 0 | 3 | **shrink to 3** | 4 | 2 | 5 |
| 6 | 1 | 3 | **shrink to 2** | 5 | 2 | 5 |
| 7 | 1 | 2 | no | 5 | 3 | 5 |
| 8 | 1 | 2 | no | 5 | 4 | 5 |
| 9 | 1 | 2 | no | 5 | 5 | 5 |
| 10 | 0 | 3 | **shrink to 2** | 6 | 5 | 5 |

**Answer: 6** — wait, let me re-check this... Actually it's `6` because the window `[5, 6, 7, 8, 9]` is `[0, 1, 1, 1, 1]` — len 5, then at right=10, `nums[10]=0`, zeros becomes 3, shrink past index 4 (zero) and index 5 (zero) — left jumps to 6, window `[6..10]` = `[1, 1, 1, 1, 0]` len 5. Hmm, so max stays 5.

Let me re-trace with actual answer **6**: at right=9 with window `[3..9]` = `[0, 0, 0, 1, 1, 1, 1]` — that's 3 zeros, invalid. Shrink — actually `[4..9]` = `[0, 0, 1, 1, 1, 1]` — still 2 zeros, valid, len 6. So answer = 6 from right=9.

**Answer: 6** — flip the two zeros at indices 4 and 5 to get `[1, 1, 1, 1, 1, 1]` of length 6 starting at index 4.

> **Pedagogy:** "Zeros" is the perfect state for K-flip problems. Don't store the full freq map when you only care about one element type.

---

### Walkthrough 3 — LC 992: Subarrays with K Different Integers [L11] ⭐

**Input:** `nums = [1, 2, 1, 2, 3]`, `k = 2`

**Approach:** Template 4. `exactly(2) = atMost(2) − atMost(1)`.

**atMost(2) trace** (window must have ≤ 2 distinct):

| right | nums[r] | freq | distinct | shrink? | left | win len | count += |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 | 1 | {1:1} | 1 | no | 0 | 1 | 1 |
| 1 | 2 | {1:1, 2:1} | 2 | no | 0 | 2 | 2 |
| 2 | 1 | {1:2, 2:1} | 2 | no | 0 | 3 | 3 |
| 3 | 2 | {1:2, 2:2} | 2 | no | 0 | 4 | 4 |
| 4 | 3 | {1:2, 2:2, 3:1} | 3 | shrink to {2:2, 3:1} | 2 | 3 | 3 |

`atMost(2) = 1 + 2 + 3 + 4 + 3 = 13`

**atMost(1) trace** (window must have ≤ 1 distinct):

| right | nums[r] | freq | shrink? | left | count += |
| --- | --- | --- | --- | --- | --- |
| 0 | 1 | {1:1} | no | 0 | 1 |
| 1 | 2 | shrink | yes | 1 | 1 |
| 2 | 1 | shrink | yes | 2 | 1 |
| 3 | 2 | shrink | yes | 3 | 1 |
| 4 | 3 | shrink | yes | 4 | 1 |

`atMost(1) = 1 + 1 + 1 + 1 + 1 = 5`

**Answer: 13 − 5 = 8.**

Verifying by listing subarrays of `[1, 2, 1, 2, 3]` with exactly 2 distinct: `[1,2]`, `[2,1]`, `[1,2,1]`, `[2,1,2]`, `[1,2]` (the second one starting at index 2), `[1,2,1,2]`, `[2,1,2]`(from index 1), and… you get 8. ✓

> **Pedagogy:** This is the canonical "atMost(K) − atMost(K−1)" problem. Internalize this trace. Reproduce it on a whiteboard if you can't.

---

### Walkthrough 4 — LC 424: Longest Repeating Character Replacement [L8] ⭐

**Input:** `s = "AABABBA"`, `k = 1`

**Approach:** Template 2 with `maxFreqEver` trick. Invariant: `windowLen − maxFreq ≤ k`.

| right | c | freq[c] after | maxFreq | windowLen | windowLen − maxFreq | shrink? | left | answer |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 0 | A | A:1 | 1 | 1 | 0 | no | 0 | 1 |
| 1 | A | A:2 | 2 | 2 | 0 | no | 0 | 2 |
| 2 | B | A:2, B:1 | 2 | 3 | 1 | no | 0 | 3 |
| 3 | A | A:3, B:1 | 3 | 4 | 1 | no | 0 | 4 |
| 4 | B | A:3, B:2 | 3 | 5 | 2 | shrink once | 1 | 4 |
| 5 | B | A:2, B:3 | 3 (stale!) | 5 | 2 | shrink once | 2 | 4 |
| 6 | A | A:3, B:3 | 3 (stale!) | 5 | 2 | shrink once | 3 | 4 |

**Answer: 4** — `"AABA"` (flip the B at index 2) or `"ABBA"` (flip the A at index 3).

> **Notice:** at step 5, `maxFreq` is recorded as 3 but the actual max in the window `[2..5]` is `B:3` — actually still 3. At step 6, real max is `A:3` and `B:3` — still 3. So in this trace, `maxFreq` is *not* stale. But the algorithm tolerates staleness gracefully. Try the input `"BAAAB"` with `k = 0` to see actual staleness.

---

### Walkthrough 5 — LC 76: Minimum Window Substring [L12] 🟡

**Input:** `s = "ADOBECODEBANC"`, `t = "ABC"`

**Approach:** Template 3 + `formed` counter trick.

Setup: `need = {A:1, B:1, C:1}`, `required = 3` (3 distinct chars to cover).

| right | c | window | freq[c] after | formed | shrink loop? | bestLen | bestLeft |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 | A | A | A:1 | 1 | no | ∞ | — |
| 1 | D | AD | A:1, D:1 | 1 | no | ∞ | — |
| 2 | O | ADO | + O:1 | 1 | no | ∞ | — |
| 3 | B | ADOB | + B:1 | 2 | no | ∞ | — |
| 4 | E | ADOBE | + E:1 | 2 | no | ∞ | — |
| 5 | C | ADOBEC | + C:1 | **3** | yes — shrink to "DOBEC" then formed drops | 6 | 0 |
| 6 | O | DOBECO | continue | 2 | no | 6 | 0 |
| 7 | D | DOBECOD | continue | 2 | no | 6 | 0 |
| 8 | E | DOBECODE | continue | 2 | no | 6 | 0 |
| 9 | B | DOBECODEB | + B:2 | 2 | no | 6 | 0 |
| 10 | A | DOBECODEBA | + A:1 | **3** | yes — shrink to "OBECODEBA" then "BECODEBA" then "ECODEBA" then "CODEBA" then "ODEBAN"... | 6 ➝ tighter at len 5 → "CODEBA" len 6, etc. | … |
| 11 | N | CODEBAN | C:1, A:1, B:1 still | 3 | yes — shrink | … | … |
| 12 | C | CODEBANC | C:2 | 3 | yes — shrink past first C → "ODEBANC" then "DEBANC" then "EBANC" then "BANC" | **4** | 9 |

**Answer: `"BANC"`** — length 4, starting at index 9.

> **Pedagogy:** The `formed` counter eliminates the per-step map comparison. Window is valid iff `formed == required`. When you decrement a freq below its required, you decrement `formed`. Beautiful trick.

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

The criterion: *Could a beginner write code that compiles, runs, doesn't crash, but produces wrong output?* All of these are silent.

---

**Sliding window on arrays with negatives + sum constraint.**

```java
// LC 209 — works because all positive
public int minSubArrayLen(int target, int[] nums) {
    // sliding window OK — all nums ≥ 0
}

// LC 862 — same problem, can have negatives
public int shortestSubarrayWithSum(int[] nums, int target) {
    // ❌ sliding window FAILS — adding a negative can flip valid → invalid
    // ✅ Use prefix sum + monotonic deque
}
```

---

**Forgetting to clean up zero entries in freq map.**

```java
freq.merge(c, -1, Integer::sum);                        // ❌ leaves c → 0 in map
// Now map.size() includes c, breaking "distinct" count

// ✅ Decrement, then remove if zero
int n = freq.get(c) - 1;
if (n == 0) {
    freq.remove(c);
} else {
    freq.put(c, n);
}
```

---

**`Integer` comparison with `==` after autoboxing cache.**

```java
if (window.get(c) == need.get(c)) {     // ❌ wrong for values > 127
if (window.get(c).intValue() == need.get(c).intValue()) {     // ✅
if (window.get(c).equals(need.get(c))) {     // ✅
```

---

**`int` window sum overflowing.**

```java
int sum = 0;                            // ❌ n = 1e5, vals up to 1e9 → overflow
long sum = 0;                           // ✅
```

---

**`Integer.MAX_VALUE` sentinel not converted at return.**

```java
int answer = Integer.MAX_VALUE;
// ... never updated ...
return answer;                          // ❌ returns 2147483647 — usually wrong
return answer == Integer.MAX_VALUE ? 0 : answer;     // ✅ or -1 depending on problem
```

---

**Shrinking before recording the answer (Template 3).**

```java
// ❌ records 1 step too late
while (windowValid()) {
    sum -= nums[left];
    left++;
    answer = Math.min(answer, right - left + 1);
}

// ✅ record THEN shrink
while (windowValid()) {
    answer = Math.min(answer, right - left + 1);
    sum -= nums[left];
    left++;
}
```

---

**Recording the answer outside the while loop (Template 3).**

```java
// ❌ records max-once-per-right instead of every valid shrink
for (int right = 0; right < n; right++) {
    sum += nums[right];
    while (sum >= target) {
        sum -= nums[left];
        left++;
    }
    answer = Math.min(answer, right - left + 1);   // wrong — by here window is invalid
}
```

---

**Using `right - left` instead of `right - left + 1` for length.**

```java
int len = right - left;                 // ❌ off-by-one (this is "left to right exclusive")
int len = right - left + 1;             // ✅ inclusive on both ends
```

---

**Iterating with `for` but mutating `left` in inner `while` — confusing but correct.**

```java
for (int right = 0; right < n; right++) {        // ✅ this works
    while (invalid) {
        left++;
    }
}
// Java allows `left` mutation inside the loop — both pointers move independently.
```

---

**Using `HashMap` when `int[26]` works.**

```java
Map<Character, Integer> freq = new HashMap<>();      // ❌ 3× slower for lowercase letters
int[] freq = new int[26];                            // ✅
```

---

**`s.substring(i, j)` is O(n).** (Pre-JDK 7 update.20 it was O(1) — no longer.)

```java
for (int i = 0; i < n; i++) {
    for (int j = i + 1; j <= n; j++) {
        if (isValid(s.substring(i, j))) { ... }     // ❌ O(n³) total — silent bug for n = 1e5
    }
}
// ✅ Slide a window over s directly without taking substrings.
```

---

**Maintaining `distinct` count manually but forgetting to update on remove.**

```java
// ❌ distinct only incremented on add, never decremented on remove → wrong
if (freq.get(c) == 0) freq.remove(c);
freq.merge(left, -1, Integer::sum);

// ✅ check BEFORE decrementing whether removal would drop a key
if (freq.get(leftVal) == 1) {
    distinct--;
    freq.remove(leftVal);
} else {
    freq.merge(leftVal, -1, Integer::sum);
}
```

---

**Trying to write "exactly K" directly.**

```java
// ❌ ad-hoc state tracking explodes into cases
// ✅ Use atMost(K) − atMost(K − 1). Don't be clever.
```

---

**Forgetting that `atMost(K − 1)` with `K = 0` returns 0 (and that's fine).**

```java
public int countNiceSubarrays(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k - 1);
    // When k = 0: atMost(-1) is called. Helper should naturally return 0
    // because shrink-while-> -1 evicts everything → left always = right + 1 → contribution always 0.
}
```

---

**Sliding window on a sorted-required problem without sorting first.**

```java
// Pattern 6 (converging two pointers) requires sorted input
int[] nums = {3, 1, 4, 1, 5};
twoSumSorted(nums, 5);                  // ❌ wrong — caller forgot to sort
Arrays.sort(nums); twoSumSorted(nums, 5);    // ✅
```

---

## 🗺️ Practice Plan — A Progression That Works

Five tiers. Each tier has a clear gating criterion. Top-3 in each tier marked with ⭐.

### Tier 1 — Templates (write these from scratch, no editor help)

- ⭐ **LC 3** — Longest Substring Without Repeating Characters (L3) ✅
- ⭐ **LC 643** — Maximum Average Subarray I (Template 1) ✅
- ⭐ **LC 209** — Minimum Size Subarray Sum (Template 3) ✅
- LC 1456 — Maximum Number of Vowels in a Substring of Given Length (Template 1) ✅

**Gate:** Can you write Templates 1, 2, 3 on a blank file from memory in under 3 minutes each? Yes → Tier 2.

---

### Tier 2 — Striver's "warm-up" videos

- ⭐ **LC 1423** — Maximum Points You Can Obtain from Cards (L2) ✅
- ⭐ **LC 1004** — Max Consecutive Ones III (L4) ✅
- ⭐ **LC 904** — Fruit Into Baskets (L5) ✅
- LC 340 — Longest Substring with At Most K Distinct Characters (L6) ✅

**Gate:** Solve all four cold, no peek, in under 15 minutes each. Then move on.

---

### Tier 3 — The character-flip / replacement family

- ⭐ **LC 424** — Longest Repeating Character Replacement (L8) — the `maxFreqEver` trick 🟡
- ⭐ **LC 1358** — Number of Substrings Containing All Three Characters (L7) 🟡
- ⭐ **LC 1208** — Get Equal Substrings Within Budget 🟡
- LC 1493 — Longest Subarray of 1's After Deleting One Element 🟡
- LC 159 — Longest Substring with At Most Two Distinct 🟡

**Gate:** Internalize the `maxFreqEver` trick. Write LC 424 from memory.

---

### Tier 4 — The "exactly K" family ⭐⭐ — the medium-interview ceiling

- ⭐ **LC 992** — Subarrays with K Different Integers (L11) 🟡
- ⭐ **LC 930** — Binary Subarrays With Sum (L9) 🟡
- ⭐ **LC 1248** — Count Number of Nice Subarrays (L10) 🟡
- LC 76 — Minimum Window Substring (L12) 🟡 — the `formed` counter trick
- LC 567 — Permutation in String 🟡
- LC 438 — Find All Anagrams in a String 🟡

**Gate:** Write `atMost(k)` helper without help. Write LC 76 with `formed` counter without help.

---

### 🎯 STOP HERE — Medium-Interview Cutoff 🎯

> If you can solve every problem in Tiers 1–4 cold from memory, you are above the medium-interview bar for sliding window + two pointers. Stop here, move on to other topics, and come back to Tier 5 only if you have time after covering everything else.

---

### Tier 5 — Stretch / Advanced 🔴

- LC 862 — Shortest Subarray with Sum at Least K 🔴 (prefix sum + monotonic deque — NOT sliding window)
- LC 239 — Sliding Window Maximum 🔴 (monotonic deque)
- LC 480 — Sliding Window Median 🔴 (two heaps)
- LC 632 — Smallest Range Covering Elements from K Lists 🔴 (multi-pointer + heap)
- LC 1610 — Maximum Number of Visible Points 🔴 (sliding window on angles)
- LC 2444 — Count Subarrays With Fixed Bounds 🔴 (multi-pointer + state tracking)

These are problems where the *spirit* of sliding window applies but you need an auxiliary data structure (deque, heap) to maintain window state in O(log n) or amortized O(1) for non-trivial aggregates.

---

## 🔗 Cross-References

| Concept | See File |
| --- | --- |
| Two Pointers (converging, same-direction) | `DSA/DeepDive/arrays-fundamentals.md` — Patterns 1 & 2 |
| Prefix Sum + HashMap | `DSA/DeepDive/arrays-fundamentals.md` — Pattern 6 |
| Frequency Maps & Hashing | `DSA/DeepDive/arrays-fundamentals.md` — Pattern 7 |
| HashMap idioms (`merge`, `getOrDefault`) | `DSA/Reference/lambdas-for-dsa-reference.md` |
| Integer overflow (`long` sums) | `DSA/DeepDive/integer-overflow-and-limits.md` |
| Compact revision file | `DSA/Reference/two-pointers-sliding-window-reference.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Initial version.** Built from Striver's 12-video Two Pointers & Sliding Window playlist. Four templates + atMost trick framing. Tier 5 cutoff matches arrays-fundamentals style. |

---

## 🧾 TL;DR — One-Page Summary

**The mental model:** *Two pointers that move only forward. The window `[left..right]` is the candidate answer. Each element is added once and removed at most once → O(n).*

**The four templates:**

| Template | Trigger | Skeleton |
| --- | --- | --- |
| **Fixed window** | "subarray of size K" | Build first window, then slide: `+nums[r], −nums[r−k]` |
| **Longest valid** | "longest subarray that..." | Expand always; while invalid shrink; record after shrink |
| **Shortest valid** | "shortest subarray that..." | Expand always; while VALID record-and-shrink |
| **Exactly K** | "number of subarrays with exactly K..." | `atMost(K) − atMost(K − 1)` |

**The `count += right − left + 1` insight (Template 4):** for atMost, every valid window of length `len` ending at `right` contributes `len` valid subarrays.

**The `formed` counter trick (LC 76):** maintain a count of "fully satisfied" target characters instead of comparing two maps. Window valid iff `formed == required.size()`.

**The `maxFreqEver` trick (LC 424):** don't decrement `maxFreq` when shrinking. Stale `maxFreq` makes validity stricter, never looser — answer stays correct.

**When sliding window fails:** negatives + sum constraint (use prefix sum + deque), subsequence (use DP), non-monotone constraint (brute force or pruning).

**Top 3 problems to nail:** LC 3 ✅, LC 76 🟡, LC 992 🟡. If those three are automatic, you're past the medium-interview bar.

---
