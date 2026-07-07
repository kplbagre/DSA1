# Two Pointers & Sliding Window — Fundamentals (Deep Dive)

> Single source of truth for Kapil's Two Pointers + Sliding Window prep. Aligned with **Striver's 12-video Two Pointer & Sliding Window playlist** (L1–L12). Every section maps back to one or more videos so you can cross-reference when re-watching.

> **Credit:** Striver (takeuforward) for the playlist structure and problem ordering. This doc reorganizes that content around four canonical templates and one unifying mental model so revision becomes mechanical.

---

## 📋 Section Index

| # | Section | What you get |
|---|---|---|
| 1 | [🎯 Goal](#goal) | What you can do after reading this |
| 2 | [🚦 Difficulty Tags](#tags) | ✅ 🟡 🔴 — how problems are rated |
| 3 | [📖 What Are Two Pointers & Sliding Window?](#what) | Core concept + monotonicity hook |
| 4 | [📖 Terminology](#terminology) | Subarray, window, expand, shrink, invariant |
| 5 | [🔨 Setup — Phase 1](#setup) | Skeleton, edge guards, exact-K frame |
| 6 | [🧠 Mental Model](#mental-model) | Worm picture + 3-questions template + decision table |
| 7 | [🎨 Style Habits](#style) | Naming, long/int, invariant comments |
| 8 | [🧭 Patterns 1–7](#patterns) | All templates with motivation + running trace |
| 9 | [🌳 Special Topics](#special) | maxFreqEver, formed counter, when NOT to use |
| 10 | [🔬 Worked Walkthroughs](#walkthroughs) | WW-1 through WW-11 with visuals |
| 11 | [⚠️ Gotchas](#gotchas) | Silent bugs that compile but produce wrong output |
| 12 | [🗺️ Practice Plan](#practice) | 5 tiers, stop-here cutoff |
| 13 | [🧾 TL;DR](#tldr) | One-page summary for revision day |

---

<a id="goal"></a>
## 🎯 Why You're Reading This (The Goal)

By the end of this doc you should be able to:

1. **Recognize** in 10 seconds whether a problem is a sliding-window problem at all.
2. **Pick the right template** out of four: Fixed window, Longest valid, Shortest valid, Count exactly K.
3. **Write the skeleton by reflex** — left pointer, right pointer, expand/shrink invariant, answer update — without thinking about edge cases.
4. **Know the four "monotonicity" hooks** that justify why sliding window even works (without monotonicity, you must brute-force).
5. **Spot the "atMost(K) − atMost(K−1)" trick** the moment "exactly K" appears in a problem statement.
6. **Pass the medium-interview cutoff** — every problem up through Tier 4 of the practice plan should be a 15-minute write.

---

<a id="tags"></a>
## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Use When |
| --- | --- | --- |
| ✅ | **Foundation** — must know cold | First pass, daily revision |
| 🟡 | **Medium-interview core** — expect these in 45-min screens | Tier 2–4 practice |
| 🔴 | **Stretch / advanced** — beyond medium-interview bar | Only after every 🟡 is automatic |

> **Lesson learned the hard way (May 2026):** Sliding window has a *deceptive difficulty curve.* Fixed window feels trivial, then variable-longest feels OK, then "exactly K" hits and you spend an hour deriving the atMost trick. Tag honestly — don't promote a problem to ✅ until you can solve it cold from a blank file.

---

<a id="what"></a>
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

<a id="terminology"></a>
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

<a id="setup"></a>
## 🔨 Setup — Phase 1 Before the Window Loop

> **The Phase 1 question for two pointers / sliding window:** *Before I write the main loop, how do I initialize the window, which skeleton do I use (fixed vs variable vs converging), and what guards go at the top?* The most common two-pointer bugs are not algorithmic — they're setup failures: wrong pointer starting positions, forgetting the fixed-window pre-population step, or missing the `atMost(k) − atMost(k−1)` frame for exact-K problems.

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

### Window Initialization — `left = 0, right = 0` (universal starting point)

Both pointers start at index 0. `right` expands; `left` shrinks. **Never start with `right = -1`** — that forces a special-case first iteration.

```java
int left = 0;   // ✅ always 0
int right = 0;  // ✅ always 0 — the outer for-loop advances right on every tick
```

The outer loop `for (int right = 0; right < n; right++)` moves `right` forward unconditionally. The inner `while` moves `left` forward only when the window violates the constraint. Both pointers traverse the array at most once → O(n).

---

### Fixed-Window Pre-Population Trap

For a fixed window of size `k`, populate the first window in a **separate loop before the sliding loop** — not inside it.

```java
// ❌ WRONG — tries to handle the first window inside the slide loop
for (int right = 0; right < n; right++) {
    windowSum += nums[right];
    if (right >= k) {                          // ← misses recording the first window
        windowSum -= nums[right - k];
        best = Math.max(best, windowSum);
    }
}

// ✅ CORRECT — Phase 1: build first window; Phase 2: slide
long windowSum = 0;
for (int i = 0; i < k; i++) {                  // Phase 1 — first window (indices 0..k-1)
    windowSum += nums[i];
}
long best = windowSum;                          // record BEFORE sliding
for (int right = k; right < n; right++) {      // Phase 2 — slide one step at a time
    windowSum += nums[right];
    windowSum -= nums[right - k];
    best = Math.max(best, windowSum);
}
```

> **Why the ❌ version breaks:** when `n == k` (the first window IS the only window), the `if (right >= k)` branch never triggers, so `best` stays 0. See full template in `#### Template 1 — Fixed-Size Window` below.

---

### Exact-K Recognition → Two-Call Setup

The instant you read **"number of subarrays with exactly K distinct / K sum / K odd..."** in the problem, stop and write the two-call frame FIRST before implementing anything:

```java
// Step 1 — write this shell immediately on seeing "exactly K":
public int countExactlyK(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k - 1);  // ← write this line FIRST
}

// Step 2 — then implement the helper:
private int atMost(int[] nums, int k) {
    int left = 0;
    int count = 0;
    // ... variable window that counts subarrays with AT MOST k of the thing
    return count;
}
```

**Why this is Phase 1, not an optimization:** a single window for "exactly K" breaks — shrinking may overshoot valid windows because distinct-count is not monotone with shrinking. Writing `atMost(k) - atMost(k-1)` first locks in the correct structure before any loop appears.

**Edge case already handled:** when `k = 0`, `atMost(nums, -1)` returns 0 naturally — the `while` loop can never reduce distinct count below 0, so no subarrays are counted.

---

### Edge Case Guards Before the Loop

Write these at the top of the method, before any loop:

```java
// Guard 1 — fixed window: k larger than input (always add for Template 1)
if (nums.length < k) {
    return 0;
}

// Guard 2 — empty input (only if constraints don't guarantee 1 ≤ n)
if (nums == null || nums.length == 0) {
    return 0;
}
```

> **Rule:** Guard 1 is mandatory for every fixed-window problem. Guard 2 only if LeetCode constraints say `n ≥ 1` is NOT guaranteed (rare — check constraints first). If your loop breaks on a single element, the bug is in the loop logic, not a missing guard.

---

<a id="mental-model"></a>
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

### 🎨 Visual — The Worm Animation (longest substring without repeats)

```
Find LONGEST substring without repeating chars in:   a b c a b c b b

  t=0:   [ a ] b c a b c b b           window: {a}            len=1
          L=R

  t=1:   [ a b ] c a b c b b           window: {a, b}         len=2
          L   R

  t=2:   [ a b c ] a b c b b           window: {a, b, c}      len=3   ← new best
          L     R

  t=3:    a [ b c a ] b c b b          window: {a, b, c}      len=3
              ↑       ↑                shrink: 'a' dupe — drop a from left
              L       R                (worm pulls tail forward)

  t=4:    a b [ c a b ] c b b          window: {a, b, c}      len=3
                ↑     ↑                shrink: 'b' dupe — drop b
                L     R

  t=5:    a b c [ a b c ] b b          window: {a, b, c}      len=3
                  L     R              shrink: 'c' dupe — drop c

  t=6:    a b c a b [ c b ] b          window: {b, c}         len=2
                      L  R             shrink: 'b' dupe again
        → a b c a b c [ b ]            window: {b}            len=1
                        LR

  t=7:    a b c a b c b [ b ]          window: {b}            len=1
                          LR           shrink yet again

  ANSWER: best length = 3


THE WORM RULE IN ACTION:

   ─►  R moves forward EVERY tick.
   ─►  L moves forward ONLY when the window is invalid.

   Both pointers cross every index AT MOST ONCE → O(n).


WHAT THE "MONOTONICITY" CLAIM MEANS:

   While R is fixed, moving L FORWARD can only DECREASE the set of
   elements in the window — never re-introduce a dropped element.
   So once the window becomes valid, we can stop shrinking and trust
   it stays valid until the next expand.  That's the whole trick.
```

### The Three Questions Template

Every sliding-window problem reduces to answering three questions:

1. **When do I expand?** Almost always: every iteration, `R++`.
2. **When do I shrink?** Depends on the problem. The condition is usually:
   - "While window is invalid" → shrink until valid (for *longest valid* problems)
   - "While window is valid" → shrink and record (for *shortest valid* problems)
3. **What do I track?** `max length`, `min length`, `count`, `existence`, `actual subarray`.

Answer these three and the code writes itself.

> **⬛ Before writing the loop — answer these 3 setup questions first:**
> 1. **Which template?** Fixed K / longest valid / shortest valid / count-exactly → pick one before touching the loop.
> 2. **Fixed window (Template 1)?** → pre-populate the first `k` elements in a separate loop BEFORE the sliding loop. See `### Fixed-Window Pre-Population Trap` above.
> 3. **"Exactly K" in the problem?** → write `return atMost(k) - atMost(k-1);` FIRST, then implement `atMost`. See `### Exact-K Recognition → Two-Call Setup` above.

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

#### 🎨 Visual — Why `count += (right − left + 1)` Works

```
Suppose `right` is fixed and `[left..right]` is the LARGEST valid
window ending at `right`.

Every SUFFIX of this window is ALSO a valid window ending at `right`:

    [left, ..., right]            ← length (right - left + 1)
       [left+1, ..., right]        ← length (right - left)
          [left+2, ..., right]      ← length (right - left - 1)
             ⋮
                  [right, right]   ← length 1

That's exactly (right - left + 1) valid subarrays ending at right.


CONCRETE EXAMPLE — atMost(K=2) on  nums = [1, 2, 1, 2, 3]:

  right=0:  window [1]            len=1   count += 1   (total=1)
                                            valid sub-suffixes: [1]

  right=1:  window [1, 2]         len=2   count += 2   (total=3)
                                            sub-suffixes ending at r:
                                            [1,2], [2]

  right=2:  window [1, 2, 1]      len=3   count += 3   (total=6)
                                            [1,2,1], [2,1], [1]

  right=3:  window [1, 2, 1, 2]   len=4   count += 4   (total=10)
                                            [1,2,1,2], [2,1,2], [1,2], [2]

  right=4:  add 3 → 3 distinct → SHRINK
            shrink: drop 1, drop 2, drop 1 → window [2, 3]   len=2
                                            count += 2   (total=12)
                                            [2,3], [3]

  ─►  atMost(2) = 12.


THEN THE MAGIC SUBTRACTION:

  atMost(2)  =  12
  atMost(1)  =   5     ← subarrays with at most 1 distinct
                          ([1], [2], [1], [2], [3] = 5)

  exactly(2) = atMost(2) - atMost(1) = 12 - 5 = 7   ✅


WHY THIS WORKS — INCLUSION/EXCLUSION:

  Let f(k) = #subarrays with at most k distinct.

      f(k)   = (count with 0 distinct) + (1) + (2) + ... + (k)
      f(k-1) = (count with 0 distinct) + (1) + ... + (k-1)
   ─────────────────────────────────────────────────────────
      f(k) - f(k-1) = (count with EXACTLY k distinct)


WHY "EXACTLY" IS HARD DIRECTLY — but "AT MOST" IS EASY:

  "Exactly K distinct" is NOT monotonic.  Expanding the window
  can turn "exactly K-1" into "exactly K" — but also into "exactly
  K+1" — so we can't maintain a single window that stays valid
  while just moving right.

  "At most K distinct" IS monotonic.  Once the window has too many
  distinct, ANY left-shrink can only remove or preserve distinct
  count.  So the standard `while invalid: shrink` loop converges.
  That's the entire reason for the subtraction trick.
```

### How to Pick the Right Template — 10-Second Decision Table

Scan the trigger phrase column first. Match the words in the problem statement. Done.

| Trigger phrase in problem | Pattern | Window behavior | Mini example |
|---|---|---|---|
| "subarray / substring **of size K**" | **Pattern 1** — Fixed Window | always exactly K elements | max sum subarray of size 3 |
| "**longest** subarray / substring **that** ..." | **Pattern 2** — Longest Valid | expand always; shrink **until** valid | longest with ≤ K zeros |
| "**shortest / minimum** subarray that ..." | **Pattern 3** — Shortest Valid | expand always; shrink **while** valid | min-length sum ≥ target |
| "**number of** subarrays with **exactly** K ..." | **Pattern 4** — atMost(K)−atMost(K−1) | run atMost helper **twice** | subarrays with exactly K distinct |
| "**number of** subarrays with **at most** K ..." | **Pattern 4 helper** — atMost(K) only | shrink until valid; `count += right−left+1` | subarrays with ≤ K distinct |
| "find a **pair / triplet**" (sorted input) | **Pattern 6** — Converging two pointers | close in from both ends | two sum on sorted array |
| "**remove / dedup** in-place" | **Pattern 7** — Same-direction two pointers | fast scouts, slow writes | remove duplicates |
| none of the above | ⚠️ Probably NOT sliding window | — | try prefix-sum + hash, or DP |

> **The one thing to memorise about Pattern 3 vs Pattern 2:** the `while` condition flips.
> Pattern 2: `while (invalid) shrink` — hold the window as long as possible.
> Pattern 3: `while (valid) record-then-shrink` — squeeze the window as tight as possible.

---

<a id="style"></a>
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

<a id="patterns"></a>
## 🧭 Patterns — The Striver Playlist Mapped to Templates

Every video maps cleanly to one of the four templates. The pattern format below mirrors arrays-fundamentals: blockquote → English steps → Java template → example problems → try-these.

---

### Pattern 1 — Fixed-Size Window [L2] ✅

> Slide a window of a known size `k` across the array. On every slide, do an O(1) update: add the new right element and remove the old left element. Used when the constraint specifies an exact window size.

**When you'll see this pattern:**

The problem asks for an aggregate over **exactly** K consecutive elements — sum, max, min, or frequency count. Every position gets a new window, so updating incrementally beats recalculating:

- **LC 1423 Maximum Points You Can Obtain from Cards** — pick K cards from either end; equivalent to sliding over removed middle
- **LC 643 Maximum Average Subarray I** — max average over all subarrays of length K
- **LC 438 Find All Anagrams in a String** — fixed-length window looking for a specific char frequency match
- **LC 567 Permutation in String** — window size = length of target string, check frequency match
- **LC 2461 Maximum Sum of Distinct Subarrays With Length K** — track all distinct-value subarrays of size K

**Real-world example:** Rolling metrics over a fixed time window — compute max CPU usage every 60 seconds, aggregate daily transactions (fixed batch), detect anomalies over K-element blocks.

**🧠 Why brute force is O(nK) and how the window beats it:**

Brute force: for every starting index `i`, sum `nums[i..i+k-1]` from scratch — O(K) work per window, O(nK) total.

Fixed-window sliding works because the new window differs from the previous by exactly one element:

> `sum(i+1..i+k) = sum(i..i+k-1) − nums[i] + nums[i+k]`

One subtraction and one addition, regardless of K. So every slide is O(1) → O(n) total.

**The key insight: incremental update. Never recompute what you already have.**

### 🎨 Visual — Running trace: max sum subarray of size K=3

```
Input: nums = [2, 1, 5, 1, 3, 2],  K = 3

Phase 1 — build first window [0..2]:
  windowSum = 2 + 1 + 5 = 8.  best = 8.

Phase 2 — slide:
  right=3: +nums[3]=1, -nums[0]=2.  windowSum = 8+1-2 = 7.  best=8.
  right=4: +nums[4]=3, -nums[1]=1.  windowSum = 7+3-1 = 9.  best=9.  ← new best
  right=5: +nums[5]=2, -nums[2]=5.  windowSum = 9+2-5 = 6.  best=9.

Answer = 9   (window [2..4] = [5, 1, 3])


WHY IT WORKS IN ONE LINE:
  Each slide: add one element on the right, drop one element on the left.
  We never re-sum the K elements in the middle — they carry over for free.

KEY INVARIANT:
  At every step, windowSum = sum of exactly K consecutive elements ending at
  the current right pointer. The window is always exactly size K.
```

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

---

### Pattern 1 — Pattern Application Gallery

**Most-asked problems using fixed-size window:**

- **LC 1423 Maximum Points You Can Obtain from Cards** — Pick K cards from either end and maximize sum
- **LC 643 Maximum Average Subarray I** — Find the maximum average over all subarrays of size K
- **LC 438 Find All Anagrams in a String** — Find all window positions where anagram of target appears
- **LC 567 Permutation in String** — Check if permutation of s1 exists as substring in s2
- **LC 2461 Maximum Sum of Distinct Subarrays With Length K** — Sliding window finding max distinct-value subarray

---

> **L2 twist:** "Maximum points from cards" picks K cards from either end of the row, not a contiguous middle window. Trick: pick K cards from front and 0 from back, then "rotate" — each step swap one front-card out for a back-card in. The window slides over the *removed middle*, not over the answer.

---

### Pattern 2 — Variable Window: Longest Valid [L3, L4, L5, L6, L8] ⭐

> Expand the right pointer one step at a time. Whenever the window violates the constraint, shrink from the left until it's valid again. After shrinking, the window is the longest valid one ending at `right`. **The workhorse pattern of this entire playlist.**

**When you'll see this pattern:**

Finding the **maximum-length subarray/substring** satisfying a constraint where adding more never breaks earlier validity (monotonicity). You want to keep the window as large as possible:

- **LC 3 Longest Substring Without Repeating Characters** — expand right, shrink when char repeats
- **LC 1004 Max Consecutive Ones III** — find longest subarray with at most K zeros
- **LC 904 Fruit Into Baskets** — find longest subarray with at most 2 distinct values
- **LC 340 Longest Substring with At Most K Distinct Characters** — longest with ≤ K distinct chars
- **LC 424 Longest Repeating Character Replacement** — longest where (len − maxFreq) ≤ K (replace cost)

**Real-world example:** Find longest clean data segment without corruption, longest period with acceptable latency, longest prefix matching a pattern.

**🧠 Why brute force is O(n²) and how the window beats it:**

Brute force tries every `(left, right)` pair — for each `right`, restart `left` from 0. That's O(n²) windows.

Sliding window works because the constraint is **monotone in the left direction**:

> If window `[left..right]` is **invalid** (too many distinct chars, too many zeros, etc.),
> then window `[left−1..right]` is **even more invalid** — you've only added one more element.
> There is zero point moving `left` further left for this `right`.

So `left` never resets. Each element is added once (when `right` reaches it) and removed at most once (when `left` passes it). That's at most 2n operations → O(n).

**This is the only reason sliding window exists: the shrink direction is irreversible.**

### 🎨 Visual — Running trace: longest subarray with at most K=1 zero

```
Input: nums = [1, 1, 0, 1, 1, 0, 1],  K = 1 (at most 1 zero allowed)

Window state tracked: count of zeros inside [left..right]

right=0: add 1. zeros=0. window=[1].           len=1.  best=1
right=1: add 1. zeros=0. window=[1,1].         len=2.  best=2
right=2: add 0. zeros=1 ≤ 1. window=[1,1,0].  len=3.  best=3
right=3: add 1. zeros=1. window=[1,1,0,1].     len=4.  best=4
right=4: add 1. zeros=1. window=[1,1,0,1,1].   len=5.  best=5  ← new best
right=5: add 0. zeros=2 > 1. INVALID → SHRINK:
           remove [0]=1 → zeros=2, left=1. still invalid.
           remove [1]=1 → zeros=2, left=2. still invalid.
           remove [2]=0 → zeros=1, left=3. valid again.
         window=[1,1,0],  len=3.              best stays 5.
right=6: add 1. zeros=1. window=[1,1,0,1].     len=4.  best stays 5.

Answer = 5   (the window [0..4] = [1,1,0,1,1])


Why left never went back past index 3:
  Once [0..4] was found (len=5), right moved to index 5 and made it invalid.
  We shrunk left FORWARD (3→4 direction) until valid, NOT backward.
  This is the irreversibility: left only ever moves right.
  Each of the 7 elements was touched at most twice (once by right, once by left).


KEY INVARIANT:
  After the shrink loop, window [left..right] is the LONGEST valid window
  ending exactly at right. No window ending at right with a smaller left
  can be longer — we only shrank as far as we had to.
```

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

---

### Pattern 2 — Pattern Application Gallery

**Most-asked problems using longest-valid window:**

- **LC 3 Longest Substring Without Repeating Characters** — Find longest substring with no duplicate characters
- **LC 1004 Max Consecutive Ones III** — Longest subarray with at most K zeros
- **LC 904 Fruit Into Baskets** — Find longest subarray containing at most 2 distinct values
- **LC 340 Longest Substring with At Most K Distinct Characters** — Longest substring with ≤ K distinct chars
- **LC 424 Longest Repeating Character Replacement** — Longest subarray where replacing ≤ K chars makes all same

---

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

**When you'll see this pattern:**

Finding the **minimum-length subarray/substring** satisfying a constraint. Once valid, shrink to find the tightest solution:

- **LC 76 Minimum Window Substring** — find shortest substring containing all target chars with required frequencies
- **LC 209 Minimum Size Subarray Sum** — find shortest subarray with sum ≥ target
- **LC 632 Smallest Range Covering Elements from K Lists** — find smallest range including at least one element from each list
- **LC 1234 Replace the Substring for Balanced String** — shortest substring to replace to balance string
- **LC 1358 Number of Substrings Containing All Three Characters** — count valid windows (also Pattern 5)

**Real-world example:** Find minimal cache size to serve a query log, shortest token sequence to reach goal, minimum config window for compliance check.

**🧠 Why this is the mirror of Pattern 2 — and where beginners get confused:**

Pattern 2 (longest): shrink while **invalid** → expand until you find the best valid length.
Pattern 3 (shortest): shrink while **valid** → you want the tightest window that still satisfies.

The key shift:

> In Pattern 2, validity is precious — you want to hold onto it as long as possible.
> In Pattern 3, validity is a trigger — the moment the window becomes valid, you greedily shrink to find if a smaller window also satisfies.
> When it goes invalid again, stop shrinking and expand.

Brute force: try every `(left, right)` pair = O(n²). Sliding window: same irreversibility argument as Pattern 2 — if `[left..right]` is valid, `[left+1..right]` might ALSO be valid (and shorter). Shrinking from the left eliminates longer solutions we've already recorded.

### 🎨 Visual — Running trace: shortest subarray with sum ≥ target=7

```
Input: nums = [2, 3, 1, 2, 4, 3],  target = 7

left=0, sum=0, best=∞

right=0: +2. sum=2. Not valid (2 < 7). No shrink.
right=1: +3. sum=5. Not valid. No shrink.
right=2: +1. sum=6. Not valid. No shrink.
right=3: +2. sum=8. VALID (8 ≥ 7). SHRINK:
  record len=3-0+1=4. best=4.
  -nums[0]=2. sum=6. left=1. invalid → stop shrink.
right=4: +4. sum=10. VALID. SHRINK:
  record len=4-1+1=4. best=4.
  -nums[1]=3. sum=7. left=2. still valid.
  record len=4-2+1=3. best=3.  ← new best
  -nums[2]=1. sum=6. left=3. invalid → stop shrink.
right=5: +3. sum=9. VALID. SHRINK:
  record len=5-3+1=3. best=3.
  -nums[3]=2. sum=7. left=4. still valid.
  record len=5-4+1=2. best=2.  ← new best
  -nums[4]=4. sum=3. left=5. invalid → stop shrink.

Answer = 2   (window [4..5] = [4, 3], sum = 7)


SHRINK-WHILE-VALID in action:
  Every time the window becomes valid, we squeeze it from the left
  to see if a smaller window also works. We record BEFORE each shrink.
  The moment it goes invalid we stop — we can't make it valid by shrinking further.

KEY INVARIANT:
  We record the window BEFORE shrinking (not after).
  The inner while fires only when valid — it's the opposite of Pattern 2's while.
  Pattern 2: while(invalid) shrink.  Pattern 3: while(valid) record-then-shrink.
```

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

---

### Pattern 3 — Pattern Application Gallery

**Most-asked problems using shortest-valid window:**

- **LC 76 Minimum Window Substring** — Find shortest substring containing all required characters with correct frequencies
- **LC 209 Minimum Size Subarray Sum** — Find minimum length subarray with sum ≥ target
- **LC 632 Smallest Range Covering Elements from K Lists** — Find smallest range including one element from each list
- **LC 1234 Replace the Substring for Balanced String** — Find shortest substring to replace for balanced L/R counts
- **LC 30 Substring with Concatenation of All Words** — Find all windows where concatenated words appear

---

> **Why is L12 (LC 76) hard?** It combines Template 3 (shortest) with a tricky validity check ("window has at least the freq of every char in target"). The trick is the `formed` counter — see the L12 walkthrough below.

---

### Pattern 4 — Count Exactly K via atMost(K) − atMost(K−1) [L9, L10, L11] ⭐

> Counting "exactly K" subarrays directly requires a hairy case analysis. Counting "at most K" is just Template 2 with an extra `count += right − left + 1` line. So compute `atMost(K) − atMost(K − 1)` and you're done. **The signature trick of this playlist.**

**When you'll see this pattern:**

Counting subarrays where something appears **exactly** K times — direct case analysis is messy, but the atMost subtraction trick is clean:

- **LC 930 Binary Subarrays With Sum** — count subarrays with exactly sum = goal (using atMost on 0/1 values)
- **LC 1248 Count Number of Nice Subarrays** — count subarrays with exactly K odd numbers
- **LC 992 Subarrays with K Different Integers** — count subarrays with exactly K distinct values
- **LC 2962 Count Subarrays Where Max Element Appears at Least K Times** — max appears exactly K times
- **LC 1358 Number of Substrings Containing All Three Characters** — exactly one of each char (also Pattern 5)

**Real-world example:** Count audit logs matching exactly N severity levels, transactions with exactly K distinct vendors, data segments with exactly T tags.

**🧠 Why "exactly K" can't use a single window — and why atMost fixes it:**

Attempt at direct "exactly K": expand right, shrink left whenever distinct count **exceeds** K.
The problem: when you shrink, you might overshoot — you go from K+1 distinct down to K−1 or less, missing the windows that had exactly K.

> "Exactly K" is **not monotone**: having K+1 distinct → shrink once → you might have K−1 distinct (not K).
> There's no stable shrink condition that keeps distinct count pinned at exactly K.

"At most K" IS monotone: once you exceed K, shrinking from the left can only remove or preserve distinct count. The standard `while(distinct > k) left++` always converges — never overshoots below K.

So we decompose: **exactly(K) = atMost(K) − atMost(K−1)**. Pure inclusion-exclusion.

### 🎨 Visual — Why direct "exactly K" fails, and how the subtraction saves it

```
nums = [1, 2, 1, 2, 3],  K = 2 (subarrays with exactly 2 distinct)

Attempt — direct "exactly 2": maintain a window with exactly 2 distinct.
  right=0: [1].    distinct=1. not 2. no record.
  right=1: [1,2].  distinct=2. record! count=1.
  right=2: [1,2,1]. distinct=2. record! count=2.
  right=3: [1,2,1,2]. distinct=2. record! But also [2,1,2],[1,2],[2] should count.
  ...

The problem: when right=4 adds '3', distinct becomes 3.
  We shrink: remove [0]=1. window=[2,1,2,3]. distinct=3. still bad.
  Remove [1]=2. window=[1,2,3]. distinct=3. still bad.
  Remove [2]=1. window=[2,3]. distinct=2. stop.
  But we MISSED subarrays [1,2,3] ending at right=4 — they had 3 distinct,
  not 2, but their sub-windows like [2,3] DO count. Left jumped too far.
  There's no way to systematically recover those.

The fix — atMost subtraction:

  ┌─ THE FORMULA FIRST — read this before the trace ──────────────────┐
  │                                                                    │
  │  count += right - left + 1                                        │
  │                                                                    │
  │  After every shrink, left = earliest valid start for this right.  │
  │  So EVERY start in [left .. right] gives a valid subarray that    │
  │  ends at right. How many starts is that?  right - left + 1.       │
  │                                                                    │
  │  Example: right=3, left=0 → starts 0,1,2,3 → 3-0+1=4 subarrays: │
  │    start=0: [1,2,1,2]                                             │
  │    start=1: [2,1,2]                                               │
  │    start=2: [1,2]                                                 │
  │    start=3: [2]                                                   │
  │                                                                    │
  └────────────────────────────────────────────────────────────────────┘

─────────────────────────────────────────────────────────────────────
  atMost(k=2): count all subarrays with ≤ 2 distinct integers
  State tracked each step: left, freq map, distinct, count += (right-left+1)
─────────────────────────────────────────────────────────────────────

  Initial: left=0, freq={}, distinct=0, count=0

  right=0: add 1. freq={1:1}. distinct=1. no shrink.
    count += 0-0+1=1.  count=1.

  right=1: add 2. freq={1:1,2:1}. distinct=2. no shrink.
    count += 1-0+1=2.  count=3.

  right=2: add 1. freq={1:2,2:1}. distinct=2 (1 was already in map). no shrink.
    count += 2-0+1=3.  count=6.

  right=3: add 2. freq={1:2,2:2}. distinct=2 (2 was already in map). no shrink.
    count += 3-0+1=4.  count=10.

  right=4: add 3. freq={1:2,2:2,3:1}. distinct=3. SHRINK (3 > 2):
      remove [left=0]=1. freq[1]: 2→1 (still in map → distinct stays 3). left=1.
      remove [left=1]=2. freq[2]: 2→1 (still in map → distinct stays 3). left=2.
      remove [left=2]=1. freq[1]: 1→0 → key removed. freq={2:1,3:1}. distinct=2. left=3. stop.
    count += 4-3+1=2.  count=12.

  atMost(2) = 12  ✅

─────────────────────────────────────────────────────────────────────
  atMost(k=1): count all subarrays with ≤ 1 distinct integers
─────────────────────────────────────────────────────────────────────

  right=0: [1].     count+=1.  total=1.
  right=1: add 2. distinct=2 > 1. SHRINK: remove 1. distinct=1. left=1.
           window=[2]. count+=1.  total=2.
  right=2: add 1. distinct=2 > 1. SHRINK: remove 2. distinct=1. left=2.
           window=[1]. count+=1.  total=3.
  right=3: add 2. distinct=2 > 1. SHRINK: remove 1. distinct=1. left=3.
           window=[2]. count+=1.  total=4.
  right=4: add 3. distinct=2 > 1. SHRINK: remove 2. distinct=1. left=4.
           window=[3]. count+=1.  total=5.

  atMost(1) = 5  ✅

─────────────────────────────────────────────────────────────────────

  exactly(2) = atMost(2) − atMost(1) = 12 − 5 = 7  ✅

  Manual verify — subarrays with EXACTLY 2 distinct:
    [1,2], [2,1], [1,2,1], [2,1,2], [1,2,1,2], [1,2], [2,3]  →  7 ✓

KEY INVARIANT:
  "At most K" is monotone (shrinkable). "Exactly K" is not.
  atMost(K) counts all windows with 0..K of the thing.
  atMost(K-1) counts all windows with 0..K-1 of the thing.
  The difference = windows with EXACTLY K of the thing.
```

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

---

### Pattern 4 — Pattern Application Gallery

**Most-asked problems using atMost(K) − atMost(K−1):**

- **LC 930 Binary Subarrays With Sum** — Count subarrays with exactly sum equal to goal
- **LC 1248 Count Number of Nice Subarrays** — Count subarrays with exactly K odd numbers
- **LC 992 Subarrays with K Different Integers** — Count subarrays with exactly K distinct integers
- **LC 2962 Count Subarrays Where Max Element Appears at Least K Times** — Count where max appears exactly K times
- **LC 1358 Number of Substrings Containing All Three Characters** — Count substrings with all three chars (a, b, c)

---

> **Mental model:** *"At most K is permissive. At most K − 1 is one click more restrictive. The difference is exactly the subarrays whose 'count of thing' is K."*

---

### Pattern 5 — Number-of-Substrings via "Smallest Left That Works" [L7] 🟡

> Variant of Template 2. For each `right`, find the smallest `left` such that the window `[left..right]` is valid. Then the number of valid windows ending at `right` is `left + 1` (windows starting at indices 0, 1, …, left).

**When you'll see this pattern:**

Counting valid windows (not just finding one max-length window). For each right position, shrinking the left finds the **smallest valid start**; all windows starting at `[0..left−1]` ending at `right` are valid, so you add `left` to the count:

- **LC 1358 Number of Substrings Containing All Three Characters** — count all substrings with a, b, c present
- **LC 2799 Count Complete Subarrays in an Array** — count subarrays containing all distinct values
- **LC 2090 K Radius Subarray Averages** — related counting variant
- **LC 1248 Count Number of Nice Subarrays** — also solvable with this pattern (though Pattern 4 is cleaner)
- **LC 1234 Replace the Substring for Balanced String** — variant of smallest-left-that-works

**Real-world example:** Count all valid cache configurations, enumerate all compliant log segments, measure coverage of audit windows.

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

---

### Pattern 5 — Pattern Application Gallery

**Most-asked problems using smallest-left-that-works:**

- **LC 1358 Number of Substrings Containing All Three Characters** — Count all substrings containing characters a, b, and c
- **LC 2799 Count Complete Subarrays in an Array** — Count subarrays containing all distinct values from array
- **LC 2090 K Radius Subarray Averages** — Compute averages for subarrays of size 2K+1
- **LC 1248 Count Number of Nice Subarrays** — Count subarrays with exactly K odd numbers
- **LC 395 Longest Substring with At Least K Repeating Characters** — Longest substring where all chars appear ≥ K times

---

> **Why this differs from Pattern 4:** Here, "valid" means "*at least* one of each" (a minimum constraint). In Pattern 4, "exactly K" requires the subtraction trick. When the constraint is "contains at least one of each X", the smallest-left-that-works variant is cleaner.

---

### Pattern 6 — Classic Two Pointers (Converging) [L1] ✅

> Two pointers start at opposite ends of a sorted array and move toward each other. Each step decides which pointer to move based on the comparison with the target. **Independent of sliding window** — different mental model.

**When you'll see this pattern:**

Problems on **sorted arrays** where you find a pair/triplet matching a constraint. Start from both ends and converge by eliminating halves — O(n) after sorting:

- **LC 167 Two Sum II — Input Array Is Sorted** — find two indices summing to target
- **LC 15 3Sum** — find all triplets summing to zero (fix one, two-pointer rest)
- **LC 11 Container With Most Water** — find two lines maximizing area between them
- **LC 42 Trapping Rain Water** — two-pointer version computing trapped water
- **LC 125 Valid Palindrome** — two pointers checking valid alphanumeric palindrome

**Real-world example:** Matching buy/sell orders, finding balanced pairs in a log, detecting symmetric patterns, allocating resources from opposite ends.

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

---

### Pattern 6 — Pattern Application Gallery

**Most-asked problems using converging two pointers:**

- **LC 167 Two Sum II — Input Array Is Sorted** — Find two numbers summing to target in sorted array
- **LC 15 3Sum** — Find all triplets summing to zero
- **LC 11 Container With Most Water** — Find two lines with maximum area between them
- **LC 42 Trapping Rain Water** — Calculate trapped rainwater using height pairs
- **LC 125 Valid Palindrome** — Check if string is valid palindrome ignoring non-alphanumeric chars

---

> **Cross-reference:** Full coverage in `DSA/DeepDive/arrays-fundamentals.md` — Pattern 1 (Two Pointers Converging).

---

### Pattern 7 — Same-Direction Two Pointers [L1] 🟡

> Both pointers move forward. The slow pointer marks where to write; the fast pointer scans. Used for in-place modifications: dedup, partition, removal.

**When you'll see this pattern:**

In-place array mutations — removing, reordering, or filtering elements without extra space. Fast pointer scouts ahead; slow pointer marks the write position:

- **LC 26 Remove Duplicates from Sorted Array** — remove duplicates in-place, return new length
- **LC 27 Remove Element** — remove all occurrences of a value, return new length
- **LC 283 Move Zeroes** — move all zeros to end while maintaining order of non-zeros
- **LC 80 Remove Duplicates II** — allow each element up to 2 times, remove excess
- **LC 75 Sort Colors** — three-pointer partition (Dutch Flag problem)

**Real-world example:** Compact database records in-place, dedup a log stream, filter spam from messages, reorder data to match priority.

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

---

### Pattern 7 — Pattern Application Gallery

**Most-asked problems using same-direction two pointers:**

- **LC 26 Remove Duplicates from Sorted Array** — Remove duplicates from sorted array, return new length
- **LC 27 Remove Element** — Remove all occurrences of a value, return new length
- **LC 283 Move Zeroes** — Move all zeros to end while keeping non-zero order
- **LC 80 Remove Duplicates II** — Allow at most 2 occurrences of each element
- **LC 75 Sort Colors** — Partition array into three sections (0, 1, 2) in-place

---

> **Cross-reference:** Same pattern lives in `DSA/DeepDive/arrays-fundamentals.md` — Pattern 2.

---

<a id="special"></a>
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

<a id="walkthroughs"></a>
## 🔬 Worked Walkthroughs

Eleven canonical problems — one per structurally unique shape. Every walkthrough follows the 5-part format: Problem → Brute Force → Intuition Bridge → Steps + Code → Transfers To.

> **Note:** LC 1004 Max Consecutive Ones III was in an earlier version of this section. It transfers directly from WW-6 (LC 424) — same template, binary values instead of 26 chars. See that Transfers-to table.

---

### WW-1 — LC 167 Two Sum II

> **Problem:** Given a **sorted** array `numbers` and a `target`, return the 1-indexed positions of the two numbers that add up to `target`. Exactly one solution exists.

**Brute force:** Try every pair `(i, j)` with `i < j`, check if `numbers[i] + numbers[j] == target`. O(n²) pairs.
> **Time:** O(n²) | **Space:** O(1)

**Intuition bridge — what cracks it open:** The array is sorted. Start with the widest possible window — left at 0, right at n-1. If the sum is too large, we must decrease it: the only way is to move right leftward (smaller value). If too small, move left rightward. Every step eliminates one element with certainty — no wasted moves. O(n) total.

**Steps in plain English:**

1. **Two pointers:** `left = 0`, `right = n - 1`.
2. **While `left < right`:** compute `sum = numbers[left] + numbers[right]`.
   - If `sum == target`: return `{left+1, right+1}` (1-indexed).
   - If `sum > target`: `right--` (sum too big; make it smaller).
   - If `sum < target`: `left++` (sum too small; make it bigger).

```java
public int[] twoSum(int[] numbers, int target) {
    // Step 1
    int left = 0;
    int right = numbers.length - 1;

    // Step 2
    while (left < right) {
        int sum = numbers[left] + numbers[right];
        if (sum == target) {
            return new int[]{ left + 1, right + 1 };
        } else if (sum > target) {
            right--;
        } else {
            left++;
        }
    }
    // guaranteed to find solution per problem statement
    return new int[]{ -1, -1 };
}
```

**Time:** O(n) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 15 3Sum | Converging two pointers on sorted array | Outer loop fixes one element; skip duplicates | `for (int i = 0; i < n-2; i++) { left = i+1; right = n-1; ... }` |
| LC 16 3Sum Closest | Same converging pointers | Track closest sum instead of exact match | `if (Math.abs(sum - target) < Math.abs(best - target)) best = sum` |
| LC 977 Squares of a Sorted Array | Same two-pointer, same converge direction | Build result from outside-in (largest squares at ends) | `result[pos--] = leftSq > rightSq ? leftSq++ : rightSq--` |

---

### WW-2 — LC 15 3Sum

> **Problem:** Given `nums`, return all unique triplets `[a, b, c]` such that `a + b + c == 0`.

**Brute force:** Try every triple `(i, j, k)` — O(n³). Deduplicate in a set. Still slow.
> **Time:** O(n³) | **Space:** O(n) for dedup set

**Intuition bridge — what cracks it open:** Fix one element `nums[i]` and reduce to Two Sum on the remaining sorted portion — exactly WW-1 with `target = -nums[i]`. Sorting upfront lets us skip duplicates by checking `nums[i] == nums[i-1]` and `nums[left] == nums[left-1]` after a match.

**Steps in plain English:**

1. **Sort `nums`.**
2. **Outer loop** from `i = 0` to `n-3`: skip if `nums[i] == nums[i-1]` (duplicate outer element).
3. **Two-pointer inner loop** on `[i+1, n-1]` with `target = -nums[i]`.
4. On match: record triplet, skip duplicate `left` and `right` values, advance both pointers.

```java
public List<List<Integer>> threeSum(int[] nums) {
    // Step 1
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();
    int n = nums.length;

    // Step 2
    for (int i = 0; i < n - 2; i++) {
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;
        }
        // Step 3 — two-pointer on [i+1, n-1]
        int left = i + 1;
        int right = n - 1;
        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];
            if (sum == 0) {
                // Step 4 — record and skip duplicates
                result.add(Arrays.asList(nums[i], nums[left], nums[right]));
                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }
                left++;
                right--;
            } else if (sum < 0) {
                left++;
            } else {
                right--;
            }
        }
    }
    return result;
}
```

**Time:** O(n²) | **Space:** O(1) output excluded

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 16 3Sum Closest | Same outer + inner two-pointer | Track closest sum, no dedup needed | `if (Math.abs(sum) < Math.abs(best)) best = sum` |
| LC 18 4Sum | Same outer loop + inner two-pointer | Two outer loops fix two elements | `for (int i = ...) for (int j = i+1; ...) { left = j+1; right = n-1; }` |
| LC 259 3Sum Smaller | Same sort + outer + inner | Count pairs where sum < target instead of equal | `result += right - left` when sum < target (all pairs [left..right] work) |

---

### WW-3 — LC 11 Container With Most Water

> **Problem:** Given `height[i]` representing vertical lines, find two lines that together with the x-axis form a container holding the most water.

**Brute force:** Try every pair `(i, j)`, compute `min(height[i], height[j]) × (j - i)`, track the max. O(n²).
> **Time:** O(n²) | **Space:** O(1)

**Intuition bridge — what cracks it open:** Start at the widest possible container (left=0, right=n-1). Moving the taller line inward strictly shrinks width AND keeps the same bottleneck (min height stays ≤ the taller line) — can only hurt. Moving the shorter line inward shrinks width but has a chance of finding a taller line — the only move that could possibly increase area. So always move the pointer on the shorter side.

**Steps in plain English:**

1. **Two pointers:** `left = 0`, `right = n - 1`, `maxArea = 0`.
2. **While `left < right`:** compute `area = min(height[left], height[right]) × (right - left)`; update `maxArea`.
3. **Move the shorter side:** if `height[left] <= height[right]`, `left++`; else `right--`.
4. **Return `maxArea`.**

```java
public int maxArea(int[] height) {
    // Step 1
    int left = 0;
    int right = height.length - 1;
    int maxArea = 0;

    // Step 2, 3
    while (left < right) {
        int area = Math.min(height[left], height[right]) * (right - left);
        maxArea = Math.max(maxArea, area);
        if (height[left] <= height[right]) {
            left++;
        } else {
            right--;
        }
    }
    // Step 4
    return maxArea;
}
```

**Time:** O(n) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 42 Trapping Rain Water | Two-pointer, running max from each side | Sum water at EVERY bar, not just between two lines | `water += Math.min(leftMax, rightMax) - height[mid]` per bar |
| LC 84 Largest Rectangle in Histogram | Maximize area × width | Monotonic stack, not two-pointer | `area = height[stack.pop()] * (right - left - 1)` |

---

### WW-4 — LC 42 Trapping Rain Water

> **Problem:** Given `height[]` representing an elevation map, compute how much water can be trapped after raining.

**Brute force:** For each bar `i`, find `maxLeft[i]` and `maxRight[i]` by scanning left and right. Water at `i = min(maxLeft[i], maxRight[i]) - height[i]`. Two O(n) precomputation arrays, then one pass.
> **Time:** O(n) | **Space:** O(n)

**Intuition bridge — what cracks it open:** We need both `maxLeft` and `maxRight` at every position, but we don't need to precompute them all upfront. Two pointers from each end maintain running maxes. The key insight: water at position `i` is determined by the *smaller* of the two wall maxes. If `leftMax < rightMax`, we know the left side is the bottleneck — process left pointer and advance it, guaranteed we have enough info. Mirror for the right side.

**Steps in plain English:**

1. **Two pointers** `left = 0`, `right = n-1`; `leftMax = 0`, `rightMax = 0`, `water = 0`.
2. **While `left < right`:**
   - If `height[left] <= height[right]`: `leftMax` is the bottleneck. Water at left = `leftMax - height[left]` (if positive). Advance `left`.
   - Else: `rightMax` is the bottleneck. Water at right = `rightMax - height[right]`. Advance `right`.
3. **Return `water`.**

```java
public int trap(int[] height) {
    // Step 1
    int left = 0;
    int right = height.length - 1;
    int leftMax = 0;
    int rightMax = 0;
    int water = 0;

    // Step 2
    while (left < right) {
        if (height[left] <= height[right]) {
            // left side is the bottleneck — we know rightMax >= height[right] >= height[left]
            leftMax = Math.max(leftMax, height[left]);
            water += leftMax - height[left];
            left++;
        } else {
            rightMax = Math.max(rightMax, height[right]);
            water += rightMax - height[right];
            right--;
        }
    }
    // Step 3
    return water;
}
```

**Time:** O(n) | **Space:** O(1)

### 🎨 Visual — two-pointer water accumulation on [0,1,0,2,1,0,1,3,2,1,2,1]

```
height:  0  1  0  2  1  0  1  3  2  1  2  1
         L→                             ←R

At L=0: leftMax=0, rightMax=0, height[L]=0  → water += 0-0=0,  L→1
At L=1: leftMax=1, height[L]=1              → water += 1-1=0,  L→2
At L=2: leftMax=1, height[L]=0              → water += 1-0=1,  L→3
At L=3: leftMax=2, height[L]=2              → water += 2-2=0,  L→4
At L=4: leftMax=2, height[L]=1              → water += 2-1=1,  L→5
At L=5: leftMax=2, height[L]=0              → water += 2-0=2,  L→6
        ... and so on until L meets R

Total water = 6

KEY INVARIANT:
  When height[left] ≤ height[right], leftMax is the true cap on water at left
  because rightMax (≥ height[right] ≥ height[left]) is guaranteed to be larger.
  The smaller side is always safe to process.
```

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 11 Container With Most Water | Two pointers, move shorter side | Only two bars matter, no inner bars | `area = min(h[l], h[r]) * (r - l)` — no accumulation |
| LC 407 Trapping Rain Water II | Same water-at-min-wall concept | 3D grid — use min-heap (BFS from boundary) | `PriorityQueue<int[]> pq = new PriorityQueue<>((a,b) -> a[2]-b[2])` |

---

### WW-5 — LC 3 Longest Substring Without Repeating Characters

> **Problem:** Given string `s`, return the length of the longest substring that contains no repeating characters.

**Brute force:** Check every substring `s[i..j]` for uniqueness (via HashSet). O(n²) substrings × O(n) check = O(n³). With a set reuse trick, O(n²).
> **Time:** O(n²) | **Space:** O(min(n, 26))

**Intuition bridge — what cracks it open:** Keep a window `[left, right]` with no duplicates. When `s[right]` is already in the window, we must shrink from the left until the duplicate is gone — but not one step further. A HashMap tracking each char's last-seen index lets us jump `left` directly past the duplicate instead of shrinking one-by-one.

**Steps in plain English:**

1. **`HashMap<Character, Integer> lastSeen`** to track each char's most recent index.
2. **Scan `right` from 0 to n-1:** if `s[right]` is in the map AND its last index ≥ `left`, jump `left` to `lastSeen.get(s[right]) + 1` (skip past the old occurrence).
3. **Update `lastSeen`** with current index. Record `maxLen = max(maxLen, right - left + 1)`.
4. **Return `maxLen`.**

```java
public int lengthOfLongestSubstring(String s) {
    // Step 1
    Map<Character, Integer> lastSeen = new HashMap<>();
    int maxLen = 0;
    int left = 0;

    // Step 2, 3
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            // jump left past the previous occurrence
            left = lastSeen.get(c) + 1;
        }
        lastSeen.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    // Step 4
    return maxLen;
}
```

**Time:** O(n) | **Space:** O(min(n, charset))

### 🎨 Visual — left pointer jumps on "abcabcbb"

```
s:    a  b  c  a  b  c  b  b
idx:  0  1  2  3  4  5  6  7

right=0: c='a', left=0 → window [a],      len=1
right=1: c='b', left=0 → window [ab],     len=2
right=2: c='c', left=0 → window [abc],    len=3
right=3: c='a', last='a'@0 ≥ left=0 → left jumps to 1
                           window [bca],   len=3
right=4: c='b', last='b'@1 ≥ left=1 → left jumps to 2
                           window [cab],   len=3
right=5: c='c', last='c'@2 ≥ left=2 → left jumps to 3
                           window [abc],   len=3
right=6: c='b', last='b'@4 ≥ left=3 → left jumps to 5
                           window [cb],    len=2
right=7: c='b', last='b'@6 ≥ left=5 → left jumps to 7
                           window [b],     len=1

maxLen = 3

KEY INVARIANT:
  left always sits one step past the most recent duplicate of whatever right just saw.
  The check `lastSeen.get(c) >= left` prevents stale map entries from pulling left backward.
```

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 159 Longest Substring with At Most Two Distinct | Same variable window + shrink | Shrink when distinct count exceeds 2 | `while (freq.size() > 2) { remove freq[s[left]]; left++ }` |
| LC 340 Longest Substring with At Most K Distinct | Same template | Parameterized on K distinct | Replace `> 2` with `> k` |
| LC 424 Longest Repeating Char Replacement | Variable window | Track dominant freq instead of uniqueness | See WW-6 below |

---

### WW-6 — LC 424 Longest Repeating Character Replacement

> **Problem:** Given string `s` and integer `k`, return the length of the longest substring you can get by replacing at most `k` characters to make all chars the same.

**Brute force:** Try every substring, count replacements needed (window size − max frequency in window), check if ≤ k. O(n²) substrings × O(26) per window = O(26n²).
> **Time:** O(26n²) | **Space:** O(26)

**Intuition bridge — what cracks it open:** Replacements needed for a window = `windowLen - maxFreqInWindow`. Instead of shrinking until valid and losing progress, slide (not shrink) the window when it becomes invalid. Track `maxFreqEver` — a global high-water mark that only increases. If `windowLen - maxFreqEver > k`, slide one step right. This preserves the length of the best valid window found so far and never shrinks below it.

**🧠 The slide-not-shrink invariant — read this before looking at the code:**

> **We are searching for the LONGEST window. Once we find a window of size X, any window smaller than X is irrelevant — we've already beaten it.**

When the current window becomes invalid (needs too many replacements), standard Pattern 2 shrinks `left` forward until the window is valid again. That might give us a valid window of size 3 when we already found size 5 — useless.

Instead:
- Window becomes invalid? **Shift the whole window one step right** (slide: `left++` AND `right++`).
- Window is valid and grows? **Let `right` outrun `left`** (the window expands).

The result: **window size is monotonically non-decreasing**. It either stays the same (slide) or grows (expand). Never shrinks. At the end, `s.length() - left` is always the answer because `right - left + 1` reflects the longest valid window ever seen.

The `maxFreqEver` stale-high-water-mark supports this: even if the actual dominant char changes after a slide, `maxFreqEver` stays at (or above) the true max, making the validity check `windowLen - maxFreqEver ≤ k` a safe lower bound on what a valid window of this size requires.

### 🎨 Visual — slide-not-shrink trace on `s = "AABABBA"`, k = 1

```
Replacements needed = windowLen - maxFreq(window)
Window is valid iff replacements ≤ k = 1

s:   A  A  B  A  B  B  A
idx: 0  1  2  3  4  5  6
     freq[A]=0, freq[B]=0, maxFreqEver=0, left=0


right=0: s[0]='A'. freq[A]=1. maxFreqEver=1.
  windowLen=1. replacements=1-1=0 ≤ 1. VALID. No slide.
  best=1.    window=[A]

right=1: s[1]='A'. freq[A]=2. maxFreqEver=2.
  windowLen=2. replacements=2-2=0 ≤ 1. VALID. No slide.
  best=2.    window=[A,A]

right=2: s[2]='B'. freq[B]=1. maxFreqEver=max(2,1)=2.  ← A still dominates
  windowLen=3. replacements=3-2=1 ≤ 1. VALID. No slide.
  best=3.    window=[A,A,B]

right=3: s[3]='A'. freq[A]=3. maxFreqEver=max(2,3)=3.
  windowLen=4. replacements=4-3=1 ≤ 1. VALID. No slide.
  best=4.    window=[A,A,B,A]   ← BEST FOUND: length 4

right=4: s[4]='B'. freq[B]=2. maxFreqEver=max(3,2)=3.  ← A still 3
  windowLen=5. replacements=5-3=2 > 1. INVALID. SLIDE:
    freq[s[left=0]='A']-- → freq[A]=2. left=1.
  windowLen=right-left+1=4-1+1=4. best stays 4.
  window=[A,B,A,B]

right=5: s[5]='B'. freq[B]=3. maxFreqEver=max(3,3)=3.  ← B now ties A
  windowLen=5-1+1=5. replacements=5-3=2 > 1. INVALID. SLIDE:
    freq[s[left=1]='A']-- → freq[A]=1. left=2.
  windowLen=5-2+1=4. best stays 4.
  window=[B,A,B,B]

right=6: s[6]='A'. freq[A]=2. maxFreqEver=max(3,2)=3.  ← B still has 3
  windowLen=6-2+1=5. replacements=5-3=2 > 1. INVALID. SLIDE:
    freq[s[left=2]='B']-- → freq[B]=2. left=3.
  windowLen=6-3+1=4. best stays 4.
  window=[A,B,B,A]

End: s.length()-left = 7-3 = 4.  Answer = 4  ✅
(Valid window: "AABA" at [0..3] — replace B with A → "AAAA")


TRACE SUMMARY — what happened to the window SIZE:

  right: 0   1   2   3   4   5   6
  left:  0   0   0   0   1   2   3
  size:  1   2   3   4   4   4   4   ← never decreased after reaching 4

The window hit size 4 at right=3 and then SLID — keeping size 4 — for
the remainder of the string. It never shrank to 1, 2, or 3.


KEY INVARIANT:
  Window size is MONOTONICALLY NON-DECREASING.
  An invalid window slides one step (same size) rather than shrinking.
  Once we find a valid window of size X, we only accept windows of size > X.
  The final answer = s.length() - left = distance from last left position to end.
```

**Steps in plain English:**

1. **`int[] freq`** (size 26), `maxFreqEver = 0`, `left = 0`.
2. **Scan `right`:** increment `freq[c]`; update `maxFreqEver = max(maxFreqEver, freq[c])`.
3. **If `(right - left + 1) - maxFreqEver > k`:** slide — decrement `freq[s[left]]`, `left++`.
4. **Answer = `right - left + 1`** at the end of the loop (window never shrinks, only slides).

```java
public int characterReplacement(String s, int k) {
    // Step 1
    int[] freq = new int[26];
    int maxFreqEver = 0;
    int left = 0;

    // Step 2, 3
    for (int right = 0; right < s.length(); right++) {
        freq[s.charAt(right) - 'A']++;
        maxFreqEver = Math.max(maxFreqEver, freq[s.charAt(right) - 'A']);

        if ((right - left + 1) - maxFreqEver > k) {
            // slide — don't shrink; just shift the window one step
            freq[s.charAt(left) - 'A']--;
            left++;
        }
    }
    // Step 4 — window is at its maximum valid length
    return s.length() - left;
}
```

**Time:** O(n) | **Space:** O(26) = O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 1004 Max Consecutive Ones III | Exact same slide-not-shrink template | Binary (0/1) — `maxFreqEver` is just count of 1s | `freq[nums[right]]++; maxFreqEver = Math.max(maxFreqEver, freq[1])` |
| LC 2024 Maximize the Confusion | Same template | Maximize window where either T or F can be the dominant char — run twice | Run characterReplacement logic for 'T' and for 'F', take max |
| LC 3 Longest Substring No Repeat | Same variable window | Uniqueness constraint, not replacement count | Uses HashSet/HashMap instead of freq + maxFreq |

---

### WW-7 — LC 567 Permutation in String

> **Problem:** Given strings `s1` and `s2`, return true if any permutation of `s1` is a substring of `s2`.

**Brute force:** Generate all permutations of `s1`, check each against `s2`. O(len(s1)! × len(s2)) — completely impractical.
> **Time:** O(n!) | **Space:** O(n)

**Intuition bridge — what cracks it open:** A permutation of `s1` is a substring of `s2` iff some fixed-length window of `s2` has exactly the same character frequencies as `s1`. Fixed window size = `len(s1)`. Slide it across `s2`, updating one character in/out per step, and compare freq arrays in O(26) each step.

**Steps in plain English:**

1. **Build `need[26]`** from `s1`. **Build `window[26]`** from the first `len(s1)` chars of `s2`.
2. **If `window == need`**, return true immediately.
3. **Slide** from index `len(s1)` to `len(s2) - 1`: add `s2[right]`, remove `s2[right - len(s1)]`, compare arrays.
4. **Return false** if no match found.

```java
public boolean checkInclusion(String s1, String s2) {
    if (s1.length() > s2.length()) {
        return false;
    }
    int[] need = new int[26];
    int[] window = new int[26];
    int k = s1.length();

    // Step 1
    for (int i = 0; i < k; i++) {
        need[s1.charAt(i) - 'a']++;
        window[s2.charAt(i) - 'a']++;
    }
    // Step 2
    if (Arrays.equals(window, need)) {
        return true;
    }
    // Step 3
    for (int right = k; right < s2.length(); right++) {
        window[s2.charAt(right) - 'a']++;
        window[s2.charAt(right - k) - 'a']--;
        if (Arrays.equals(window, need)) {
            return true;
        }
    }
    // Step 4
    return false;
}
```

**Time:** O(26 × n) = O(n) | **Space:** O(26) = O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 438 Find All Anagrams in a String | Exact same fixed-window freq comparison | Collect ALL matching start indices, not just true/false | `if (Arrays.equals(window, need)) result.add(right - k + 1)` |
| LC 76 Minimum Window Substring | Same idea (cover all of s1's chars) | Variable window (not fixed); minimize length | See WW-8 — much harder variant |
| LC 30 Substring with Concatenation | Same fixed total window, freq match | Words as units (len > 1), not single chars | See WW-11 |

---

### WW-8 — LC 76 Minimum Window Substring

> **Problem:** Given strings `s` and `t`, return the minimum window substring of `s` that contains all characters of `t`. Return empty string if none exists.

**Brute force:** Try every substring of `s`; check if it contains all chars of `t`. O(n²) substrings × O(m) check per substring.
> **Time:** O(n²m) | **Space:** O(m)

**Intuition bridge — what cracks it open:** Expand right until the window is valid (covers all of `t`). Then shrink from the left to find the minimum valid window. The `formed` counter tracks how many distinct chars in `t` have been satisfied — avoiding a full map comparison on every step. Shrink until `formed < required`, then expand again.

**Steps in plain English:**

1. **Build `need`** map from `t`. `required = need.size()` (distinct chars needed).
2. **Expand `right`:** increment `windowFreq[c]`; if `windowFreq[c] == need.get(c)`, increment `formed`.
3. **When `formed == required`:** record window if it's the best. Shrink from left — decrement freq of `s[left]`; if it drops below `need`, decrement `formed`. Advance `left`.
4. **Return the best window substring,** or `""` if none found.

```java
public String minWindow(String s, String t) {
    if (s.isEmpty() || t.isEmpty()) {
        return "";
    }
    // Step 1
    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) {
        need.merge(c, 1, Integer::sum);
    }
    int required = need.size();
    Map<Character, Integer> windowFreq = new HashMap<>();
    int formed = 0;
    int left = 0;
    int bestLen = Integer.MAX_VALUE;
    int bestLeft = 0;

    // Step 2
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        windowFreq.merge(c, 1, Integer::sum);
        if (need.containsKey(c) && windowFreq.get(c).equals(need.get(c))) {
            formed++;
        }

        // Step 3 — shrink while valid
        while (formed == required) {
            if (right - left + 1 < bestLen) {
                bestLen = right - left + 1;
                bestLeft = left;
            }
            char lc = s.charAt(left);
            windowFreq.merge(lc, -1, Integer::sum);
            if (need.containsKey(lc) && windowFreq.get(lc) < need.get(lc)) {
                formed--;
            }
            left++;
        }
    }
    // Step 4
    return bestLen == Integer.MAX_VALUE ? "" : s.substring(bestLeft, bestLeft + bestLen);
}
```

**Time:** O(n + m) | **Space:** O(n + m)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 567 Permutation in String | Same coverage check | Fixed window (simpler); freq arrays not maps | `Arrays.equals(window, need)` |
| LC 727 Minimum Window Subsequence | Same minimize-window goal | `t` must appear as a **subsequence** — DP/two-pass | Forward pass finds end; backward pass finds minimal start |
| LC 1234 Replace the Substring for Balanced String | Same shrink-when-valid | Target is balance across 4 chars, not coverage of t | Track excess per char; shrink while all excesses ≤ n/4 |

---

### WW-9 — LC 992 Subarrays with K Different Integers

> **Problem:** Given `nums` and `k`, return the number of subarrays with **exactly** `k` distinct integers.

**Brute force:** Try every subarray `[i..j]`, count distinct with a set, check if equals `k`. O(n²) subarrays × O(n) per check = O(n³); O(n²) with a set maintained incrementally.
> **Time:** O(n²) | **Space:** O(n)

**Intuition bridge — what cracks it open:** "Exactly K" is hard to maintain with a single window — shrinking to fix a violation removes ALL subarrays above K, overshooting. The trick: `exactly(K) = atMost(K) - atMost(K-1)`. atMost(K) counts subarrays with ≤ K distinct, which a standard variable window handles cleanly. Run it twice with different targets.

**Steps in plain English:**

1. **Write helper `atMost(nums, k)`:** variable window, expand right, shrink from left when distinct count exceeds `k`, add `right - left + 1` to count at each step.
2. **Return `atMost(nums, k) - atMost(nums, k-1)`.**

```java
public int subarraysWithKDistinct(int[] nums, int k) {
    // Step 2
    return atMost(nums, k) - atMost(nums, k - 1);
}

private int atMost(int[] nums, int k) {
    // Step 1 — count subarrays with at most k distinct integers
    Map<Integer, Integer> freq = new HashMap<>();
    int count = 0;
    int left = 0;

    for (int right = 0; right < nums.length; right++) {
        freq.merge(nums[right], 1, Integer::sum);

        // shrink until window has ≤ k distinct
        while (freq.size() > k) {
            freq.merge(nums[left], -1, Integer::sum);
            if (freq.get(nums[left]) == 0) {
                freq.remove(nums[left]);
            }
            left++;
        }
        // all subarrays ending at right with left as the earliest valid start
        count += right - left + 1;
    }
    return count;
}
```

**Time:** O(n) | **Space:** O(n)

### 🎨 Visual — atMost(2) on [1,2,1,2,3]: step-by-step pointer trace

```
nums: [1, 2, 1, 2, 3]
idx:   0  1  2  3  4

atMost(k=2) — count all subarrays with ≤ 2 distinct integers:

right=0: add 1. freq={1:1}. distinct=1. left=0. count += 0-0+1=1.  total=1
           valid sub-arrays ending here: [1]

right=1: add 2. freq={1:1,2:1}. distinct=2. left=0. count += 1-0+1=2.  total=3
           valid sub-arrays ending here: [2], [1,2]

right=2: add 1. freq={1:2,2:1}. distinct=2. left=0. count += 2-0+1=3.  total=6
           valid sub-arrays ending here: [1], [2,1], [1,2,1]

right=3: add 2. freq={1:2,2:2}. distinct=2. left=0. count += 3-0+1=4.  total=10
           valid sub-arrays ending here: [2], [1,2], [2,1,2], [1,2,1,2]

right=4: add 3. freq={1:2,2:2,3:1}. distinct=3 > 2. SHRINK:
           remove [0]=1. freq={1:1,2:2,3:1}. distinct=3. left=1. still > 2.
           remove [1]=2. freq={1:1,2:1,3:1}. distinct=3. left=2. still > 2.
           remove [2]=1. freq={2:1,3:1}.      distinct=2. left=3. valid.
         count += 4-3+1=2.  total=12
           valid sub-arrays ending here: [3], [2,3]

  Wait — why not total=13? Let's count per-step: 1+2+3+4+2 = 12.

  Manual enumeration of all subarrays with ≤ 2 distinct in [1,2,1,2,3]:
    Length 1: [1],[2],[1],[2],[3] = 5
    Length 2: [1,2],[2,1],[1,2],[2,3] = 4
    Length 3: [1,2,1],[2,1,2] = 2
    Length 4: [1,2,1,2] = 1
    Length 5: [1,2,1,2,3] = 0 (has 3 distinct)
    Total = 5+4+2+1 = 12  ✅   atMost(2) = 12.

atMost(1) — count all subarrays with ≤ 1 distinct:
  [1],[2],[1],[2],[3] = 5.   atMost(1) = 5.

exactly(2) = atMost(2) − atMost(1) = 12 − 5 = 7  ✅

Manual verify — subarrays with EXACTLY 2 distinct:
  [1,2], [2,1], [1,2,1], [2,1,2], [1,2,1,2], [1,2], [2,3] = 7.  ✓


KEY INVARIANT:
  At each right, count += (right - left + 1) = number of valid subarrays ending here.
  left is always the earliest index that keeps the window valid (≤ k distinct).
  atMost(K) − atMost(K−1) cancels all subarrays with < K distinct, leaving exactly K.
```

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 1248 Count Number of Nice Subarrays | Same exactly-K double-call | Count subarrays with exactly k odd numbers; treat odd=1, even=0 | `freq.merge(nums[right] % 2, 1, Integer::sum)` |
| LC 1358 Number of Substrings Containing All Three Characters | Same atMost idea | Three specific chars (a/b/c) instead of k distinct | `atMost(3) - atMost(2)` but the window definition differs |

---

### WW-10 — LC 209 Minimum Size Subarray Sum

> **Problem:** Given a positive integer `target` and array `nums` of positive integers, return the minimum length of a subarray with sum ≥ `target`. Return 0 if none exists.

**Brute force:** Try every subarray `[i..j]`, sum it, check if ≥ target, track minimum length. O(n²).
> **Time:** O(n²) | **Space:** O(1)

**Intuition bridge — what cracks it open:** All values are positive — adding more elements strictly increases the sum, removing elements strictly decreases it. So: expand right to grow the sum; once sum ≥ target, shrink from the left to minimize the window while keeping the sum valid. The "shrink while valid" strategy works only because positivity guarantees monotonicity.

**Steps in plain English:**

1. **`left = 0`, `sum = 0`, `minLen = Integer.MAX_VALUE`.**
2. **Expand `right`:** add `nums[right]` to `sum`.
3. **While `sum >= target`:** update `minLen = min(minLen, right - left + 1)`. Subtract `nums[left]` from `sum`, advance `left`.
4. **Return `minLen == MAX_VALUE ? 0 : minLen`.**

```java
public int minSubArrayLen(int target, int[] nums) {
    // Step 1
    int left = 0;
    int sum = 0;
    int minLen = Integer.MAX_VALUE;

    // Step 2
    for (int right = 0; right < nums.length; right++) {
        sum += nums[right];

        // Step 3 — shrink while valid
        while (sum >= target) {
            minLen = Math.min(minLen, right - left + 1);
            sum -= nums[left];
            left++;
        }
    }
    // Step 4
    return minLen == Integer.MAX_VALUE ? 0 : minLen;
}
```

**Time:** O(n) | **Space:** O(1)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 76 Minimum Window Substring | Shrink while valid, minimize length | String coverage constraint, not numeric sum | `formed == required` replaces `sum >= target` |
| LC 862 Shortest Subarray with Sum at Least K | Same minimize-length goal | Negative values destroy monotonicity → use deque + prefix sums | `PriorityQueue` or monotonic deque on prefix sums |
| LC 1438 Longest Continuous Subarray with Abs Diff ≤ Limit | Same variable window | Constraint is max-min ≤ limit — needs two monotonic deques | `maxDeque` and `minDeque` track window max/min |

---

### WW-11 — LC 30 Substring with Concatenation of All Words

> **Problem:** Given string `s` and array `words[]` where all words have equal length `wLen`, find all starting indices of substrings in `s` that are a concatenation of all words (in any order).

**Brute force:** Try every starting index, extract `words.length` consecutive chunks of length `wLen`, check if they form a permutation of `words`. O(n × m × wLen) where m = number of words.
> **Time:** O(n × m × wLen) | **Space:** O(m)

**Intuition bridge — what cracks it open:** Unlike LC 567 where the window slides one character at a time, here the fundamental unit is a word (length `wLen`). The total window size is fixed: `m × wLen`. We can slide it one *word* at a time — but we must try all `wLen` starting offsets (0, 1, ..., wLen-1) to cover all possible alignments. This reduces O(n × m) to O(n) total work across all offsets.

**Steps in plain English:**

1. **Build `need`** (frequency map of words). `totalLen = m × wLen`.
2. **For each starting offset `start` from 0 to `wLen - 1`:** run a word-unit sliding window.
3. **Inside the offset loop:** use `left`, `right` (both in word-steps). Expand right: extract word at `s[right × wLen + start .. (right+1) × wLen + start - 1]`; if it's in `need`, add to `window` freq. If not in `need` or its count exceeds `need`, slide `left` past the violating word.
4. **When `right - left + 1 == m`:** record `left × wLen + start` as a valid start index.

```java
public List<Integer> findSubstring(String s, String[] words) {
    List<Integer> result = new ArrayList<>();
    if (s.isEmpty() || words.length == 0) {
        return result;
    }

    int wLen = words[0].length();
    int m = words.length;
    int totalLen = wLen * m;

    // Step 1
    Map<String, Integer> need = new HashMap<>();
    for (String w : words) {
        need.merge(w, 1, Integer::sum);
    }

    // Step 2 — try all wLen starting offsets
    for (int start = 0; start < wLen; start++) {
        Map<String, Integer> window = new HashMap<>();
        int left = start;
        int count = 0;

        // Step 3 — slide in word-sized steps
        for (int right = start; right + wLen <= s.length(); right += wLen) {
            String word = s.substring(right, right + wLen);
            if (need.containsKey(word)) {
                window.merge(word, 1, Integer::sum);
                count++;
                // shrink from left if this word exceeds its needed count
                while (window.get(word) > need.get(word)) {
                    String leftWord = s.substring(left, left + wLen);
                    window.merge(leftWord, -1, Integer::sum);
                    count--;
                    left += wLen;
                }
                // Step 4 — full match
                if (count == m) {
                    result.add(left);
                    String leftWord = s.substring(left, left + wLen);
                    window.merge(leftWord, -1, Integer::sum);
                    count--;
                    left += wLen;
                }
            } else {
                // unknown word — reset window for this offset
                window.clear();
                count = 0;
                left = right + wLen;
            }
        }
    }
    return result;
}
```

**Time:** O(n × wLen) | **Space:** O(m)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 567 Permutation in String | Fixed total window, freq match | Single-char words — slide one char, not one word | `window[s2.charAt(right) - 'a']++; window[s2.charAt(right-k) - 'a']--` |
| LC 438 Find All Anagrams | Same collect-all-valid-starts pattern | Single chars, one offset — no outer offset loop | Remove `for (int start = 0; ...)` wrapper |

---

<a id="gotchas"></a>
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

<a id="practice"></a>
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

<a id="tldr"></a>
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
