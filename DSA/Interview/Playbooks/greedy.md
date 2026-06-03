# Greedy Algorithms — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to recognize greedy patterns. Greedy is the hardest pattern to "template" because each problem has a unique insight — but the recognition cues are consistent. If the problem says "minimum/maximum" and a local decision clearly leads to the global optimum, it's greedy.

---

## 🎯 Why You're Reading This

Greedy problems are tricky because there's no single template (unlike sliding window or binary search). The skill is recognizing that a greedy approach works — and knowing WHICH greedy choice to make. This file gives you the 5 most common greedy families with their recognition cues.

**When is it greedy vs DP?**
- **Greedy:** Making the locally optimal choice at each step leads to the global optimum. No need to consider all possibilities.
- **DP:** You need to try all possibilities and pick the best. Local optimal ≠ global optimal.
- **Rule of thumb:** If a greedy approach gives the wrong answer on a simple example, it's DP. If it works, prove it or trust it for the interview.

After reading this file, you should be able to:
1. Distinguish greedy from DP based on problem structure
2. Recognize the 5 greedy families from problem wording
3. Handle the classic follow-up: "prove your greedy choice is correct"

---

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `Math.max(a, b)` / `Math.min(a, b)` | Track running max/min (reachability, gas) | Patterns 1, 2 |
| `Arrays.sort(arr)` | Sort before greedy scan | Patterns 3, 5 |
| `Arrays.sort(arr, (a, b) -> a[1] - b[1])` | Sort by end (interval scheduling) | Pattern 3 |
| `map.getOrDefault(key, 0)` | Track last occurrence or frequency | Pattern 4 |
| `new TreeMap<>()` | Ordered frequency map for group-matching | Pattern 5 |
| `freq.merge(card, 1, Integer::sum)` | Increment count: if key exists add 1, else set to 1 | Pattern 5 |
| `Integer.compare(a, b)` | Overflow-safe comparison (returns -1, 0, or 1) | LC 452 |

> **Full reference:** `../Reference/dsa-collections-notes.md`, `../Reference/hashmap-section-updated.md`

### 🔄 Lambda & Shorthand Explanations with Fallbacks

**1. `Arrays.sort(intervals, (a, b) -> a[1] - b[1])`** — Sort by end time

**What it means in English:** "Sort the 2D array so intervals with the smallest end value come first. Each element `a` and `b` is an `int[]`. `a[1] - b[1]` returns negative if `a` ends earlier → `a` goes first."

🔄 **Fallback — explicit Comparator:**

```java
Arrays.sort(intervals, new Comparator<int[]>() {
    @Override
    public int compare(int[] a, int[] b) {
        return a[1] - b[1];
    }
});
```

---

**2. `Arrays.sort(points, (a, b) -> Integer.compare(a[1], b[1]))`** — Overflow-safe sort by end

**What it means in English:** "Same as #1, but uses `Integer.compare` instead of subtraction. Returns -1, 0, or 1 — never overflows. Required when values can be near `Integer.MIN_VALUE` or `Integer.MAX_VALUE` (e.g., LC 452 balloon coordinates)."

**Why not just `a[1] - b[1]`?** If `a[1] = Integer.MAX_VALUE` and `b[1] = -1`, then `a[1] - b[1]` wraps to a negative number → wrong sort order. `Integer.compare` is always correct.

🔄 **Fallback — explicit Comparator:**

```java
Arrays.sort(points, new Comparator<int[]>() {
    @Override
    public int compare(int[] a, int[] b) {
        // Overflow-safe: returns -1, 0, or 1 without subtraction
        return Integer.compare(a[1], b[1]);
    }
});
```

---

**3. `new TreeMap<>()`** — Sorted frequency map

**What it means in English:** "A `HashMap` that keeps keys in sorted order (ascending by default). `firstKey()` gives the smallest key in O(log n). Used when you need to process elements in order without a separate sort step."

🔄 **Fallback — regular HashMap + sort keys manually:**

```java
// Instead of TreeMap:
Map<Integer, Integer> freq = new HashMap<>();
// ... populate ...
List<Integer> sortedKeys = new ArrayList<>(freq.keySet());
Collections.sort(sortedKeys);
// Process sortedKeys in order
```

---

**4. `freq.merge(card, 1, Integer::sum)`** — Increment frequency count

**What it means in English:** "If `card` isn't in the map, put `card → 1`. If `card` IS in the map, replace its value with `oldValue + 1` (the `Integer::sum` part means `(oldVal, 1) -> oldVal + 1`)."

🔄 **Fallback — plain if-else:**

```java
if (freq.containsKey(card)) {
    freq.put(card, freq.get(card) + 1);
} else {
    freq.put(card, 1);
}
```

---

## 🧠 The Mental Model — Is This Greedy?

```
"Find the minimum/maximum of something"
│
├── "Can I make a simple local choice that's always optimal?"
│   │
│   ├── YES → Greedy
│   │   │
│   │   ├── "Can I reach the end?" / "Minimum jumps"
│   │   │   └── Pattern 1: Jump / Reachability ⭐
│   │   │
│   │   ├── "Circular route" / "Can I complete a loop?"
│   │   │   └── Pattern 2: Circular Greedy (Gas Station)
│   │   │
│   │   ├── "Maximum non-overlapping" / "Minimum removals"
│   │   │   └── Pattern 3: Interval Scheduling
│   │   │       (See intervals.md Pattern 4 — same algorithm)
│   │   │
│   │   ├── "Partition into groups" / "Last occurrence boundary"
│   │   │   └── Pattern 4: Partition by Boundary ⭐
│   │   │
│   │   └── "Group into consecutive sequences"
│   │       └── Pattern 5: Consecutive Group Matching
│   │
│   └── NO → Probably DP (try all possibilities)
│
└── Still unsure? Try a greedy approach on the examples.
    If it gives the wrong answer → switch to DP.
```

---

## 🧭 Pattern 1: Jump / Reachability ⭐

**Recognition cues — reach for this when:**
- "Can you reach the last index?"
- "Minimum number of jumps to reach the end"
- Each position has a "reach" or "range" you can cover
- The greedy choice: always extend your farthest reachable position

**Steps in plain English (Jump Game I — can you reach?):**

1. **Track `farthest`** — the farthest index reachable so far.
2. **Walk left to right** — at each index `i`, update `farthest = max(farthest, i + nums[i])`.
3. **If `i > farthest`** — you're stuck, return false.
4. **If `farthest >= last index`** — reachable, return true.

```java
public boolean canJump(int[] nums) {
    int farthest = 0;
    for (int i = 0; i < nums.length; i++) {
        if (i > farthest) {
            return false;
        }
        farthest = Math.max(farthest, i + nums[i]);
    }
    return true;
}
```

**Steps in plain English (Jump Game II — minimum jumps):**

1. **Track `farthest`, `currentEnd`, `jumps`.**
2. **Walk left to right** — update `farthest` at each step.
3. **When you reach `currentEnd`** — you must jump. Increment `jumps`, set `currentEnd = farthest`.

```java
public int jump(int[] nums) {
    int jumps = 0;
    int currentEnd = 0;
    int farthest = 0;
    // Don't need to check last index — we're already there
    for (int i = 0; i < nums.length - 1; i++) {
        farthest = Math.max(farthest, i + nums[i]);
        if (i == currentEnd) {
            jumps++;
            currentEnd = farthest;
        }
    }
    return jumps;
}
```

### 🎨 Visual — Jump Game II (Minimum Jumps)

```
nums = [2, 3, 1, 1, 4]
index:  0  1  2  3  4

Jump 1: from index 0, can reach indices 1-2
        farthest from [0..2] = max(0+2, 1+3, 2+1) = 4
        currentEnd = 2 → must jump at index 2

Jump 2: from the best landing in [1..2], can reach up to 4
        farthest = 4 ≥ last index → done!

        0     1     2     3     4
        [=====]           
        jump 1 range      
              [===========]
              jump 2 range (from best of jump 1)

Answer: 2 jumps

KEY INVARIANT:
   At each "jump boundary" (currentEnd), we've already computed
   the farthest we can reach in the NEXT jump. So each jump is optimal.
```

**🏷️ Problems:** LC 55 (Jump Game), LC 45 (Jump Game II).

---

## 🧭 Pattern 2: Circular Greedy (Gas Station)

**Recognition cues — reach for this when:**
- "Circular route" — start somewhere, visit all stations, return to start
- "Enough fuel/resources to complete a loop?"
- Track surplus/deficit as you go around

**The key insight:** If total gas ≥ total cost, a solution MUST exist. The starting point is where the running surplus is at its lowest (or equivalently, where it first becomes non-negative after a reset).

**Steps in plain English:**

1. **Track `totalSurplus` and `currentSurplus`.**
2. **Walk through stations** — at each station, `surplus = gas[i] - cost[i]`.
3. **If `currentSurplus < 0`** — can't start from previous `start`. Reset `start = i + 1`, reset `currentSurplus = 0`.
4. **After full loop** — if `totalSurplus >= 0`, return `start`. Otherwise, return -1.

```java
public int canCompleteCircuit(int[] gas, int[] cost) {
    int totalSurplus = 0;
    int currentSurplus = 0;
    int start = 0;
    for (int i = 0; i < gas.length; i++) {
        int surplus = gas[i] - cost[i];
        totalSurplus += surplus;
        currentSurplus += surplus;
        if (currentSurplus < 0) {
            // Can't start from 'start' — try next station
            start = i + 1;
            currentSurplus = 0;
        }
    }
    return totalSurplus >= 0 ? start : -1;
}
```

**🏷️ Problems:** LC 134 (Gas Station).

---

## 🧭 Pattern 3: Interval Scheduling (Activity Selection)

**Recognition cues — reach for this when:**
- "Maximum number of non-overlapping intervals"
- "Minimum intervals to remove"
- "Minimum arrows / rooms" (see `intervals.md` for full coverage)

**This pattern is fully covered in `../Interview/intervals.md` Pattern 4.** The short version: sort by end time, greedily keep intervals that don't overlap.

```java
// Lambda: "sort intervals by end time (index 1) — earliest-ending first"
Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
// 🔄 Fallback — anonymous Comparator:
//   Arrays.sort(intervals, new Comparator<int[]>() {
//       public int compare(int[] a, int[] b) { return a[1] - b[1]; }
//   });
int count = 1, end = intervals[0][1];
for (int i = 1; i < intervals.length; i++) {
    if (intervals[i][0] >= end) {
        count++;
        end = intervals[i][1];
    }
}
```

**🏷️ Problems:** LC 435 (Non-overlapping Intervals), LC 452 (Minimum Arrows).

---

## 🧭 Pattern 4: Partition by Boundary ⭐

**Recognition cues — reach for this when:**
- "Partition string/array into maximum parts"
- "Each element appears in at most one part"
- "Divide so that no element spans two groups"
- The greedy choice: extend the current partition until all elements in it are fully contained

**Steps in plain English:**

1. **Record last occurrence** of each element.
2. **Scan left to right** — track the farthest `end` any element in the current partition reaches.
3. **When `i == end`** — the current partition is complete. Record its size, start a new partition.

```java
public List<Integer> partitionLabels(String s) {
    int[] lastIndex = new int[26];
    for (int i = 0; i < s.length(); i++) {
        lastIndex[s.charAt(i) - 'a'] = i;
    }
    List<Integer> result = new ArrayList<>();
    int start = 0;
    int end = 0;
    for (int i = 0; i < s.length(); i++) {
        end = Math.max(end, lastIndex[s.charAt(i) - 'a']);
        if (i == end) {
            result.add(end - start + 1);
            start = end + 1;
        }
    }
    return result;
}
```

### 🎨 Visual — Partition Labels

```
s = "ababcbacadefegdehijhklij"

Last occurrence: a=8, b=5, c=7, d=14, e=15, f=11, g=13, h=19, i=22, j=23, k=20, l=21

Scan: i=0, char='a', end=max(0,8)=8
      i=1, char='b', end=max(8,5)=8
      ...
      i=8, char='a', end=8 → i==end! Partition: [0..8] size=9
      i=9, char='d', end=14
      ...
      i=15, char='e', end=15 → i==end! Partition: [9..15] size=7
      i=16, char='h', end=19
      ...
      i=23, char='j', end=23 → i==end! Partition: [16..23] size=8

Result: [9, 7, 8]

KEY INVARIANT:
   When i == end, every character in [start..end] has its last
   occurrence within this range — safe to cut here.
```

**🏷️ Problems:** LC 763 (Partition Labels).

---

## 🧭 Pattern 5: Consecutive Group Matching

**Recognition cues — reach for this when:**
- "Divide into groups of consecutive integers"
- "Hand of straights" / "reorganize into groups of size K"
- Need to form sequences like [1,2,3], [4,5,6] from a bag of numbers

**Steps in plain English:**

1. **Count frequencies** — use a TreeMap (sorted keys).
2. **Start from smallest available number** — form a consecutive group of size K starting there.
3. **Decrement each member's count** — if any member has count 0, impossible.
4. **Repeat until all counts are 0.**

```java
public boolean isNStraightHand(int[] hand, int groupSize) {
    if (hand.length % groupSize != 0) {
        return false;
    }
    // TreeMap: "like HashMap but keys stay sorted — firstKey() gives smallest in O(log n)"
    TreeMap<Integer, Integer> freq = new TreeMap<>();
    // 🔄 Fallback — HashMap + sort keys later:
    //   Map<Integer, Integer> freq = new HashMap<>(); ... then sort keySet()
    for (int card : hand) {
        // Lambda: "if card not in map, put 1. If card IS in map, add 1 to existing value"
        freq.merge(card, 1, Integer::sum);
        // 🔄 Fallback — plain if-else:
        //   if (freq.containsKey(card)) freq.put(card, freq.get(card) + 1);
        //   else freq.put(card, 1);
    }
    while (!freq.isEmpty()) {
        int start = freq.firstKey();
        for (int i = start; i < start + groupSize; i++) {
            if (!freq.containsKey(i)) {
                return false;
            }
            int count = freq.get(i);
            if (count == 1) {
                freq.remove(i);
            } else {
                freq.put(i, count - 1);
            }
        }
    }
    return true;
}
```

**🏷️ Problems:** LC 846 (Hand of Straights), LC 1296 (Divide Array in Sets of K Consecutive Numbers).

---

## 🔬 Canonical Problem — LC 55: Jump Game

> **Problem:** You are given an integer array `nums`. You start at index 0. Each element represents your maximum jump length at that position. Return true if you can reach the last index. Example: `nums = [2,3,1,1,4]` → `true`. `nums = [3,2,1,0,4]` → `false`.

### Step 1 — Read and identify triggers

"Can you reach the last index?" + "each element is a jump length" → **Pattern 1: Jump / Reachability**. The greedy insight: at each position, extend the farthest reachable point. If you ever land on a position beyond your farthest reach, you're stuck.

### Step 2 — Choose the template

Jump reachability template. I need:
- `farthest` = the farthest index I can reach
- At each index `i`: if `i > farthest` → return false. Otherwise, `farthest = max(farthest, i + nums[i])`.

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Initialize `farthest = 0`.**
2. **Walk left to right** — at each index, update farthest reach.
3. **If stuck** (`i > farthest`) — return false.
4. **After loop** — reached the end, return true.

```java
class Solution {
    public boolean canJump(int[] nums) {
        int farthest = 0;
        for (int i = 0; i < nums.length; i++) {
            if (i > farthest) {
                return false;
            }
            farthest = Math.max(farthest, i + nums[i]);
        }
        return true;
    }
}
```

### Step 4 — Verify with example

`nums = [2,3,1,1,4]`:
- i=0: 0 ≤ 0 ✓, farthest = max(0, 0+2) = 2
- i=1: 1 ≤ 2 ✓, farthest = max(2, 1+3) = 4
- i=2: 2 ≤ 4 ✓, farthest = max(4, 2+1) = 4
- i=3: 3 ≤ 4 ✓, farthest = max(4, 3+1) = 4
- i=4: 4 ≤ 4 ✓ → loop ends → return **true** ✅

`nums = [3,2,1,0,4]`:
- i=0: farthest = 3
- i=1: farthest = 3
- i=2: farthest = 3
- i=3: farthest = 3 (3+0=3)
- i=4: 4 > 3 → return **false** ✅

### Complexity

- **Time:** O(n) — single pass
- **Space:** O(1)

---

## ⚡ Problem Bank — Key Twists

---

### LC 55: Jump Game

> **Problem:** Can you reach the last index? Each element is max jump length. Example: `[2,3,1,1,4]` → `true`.

> **Approach:** Track farthest reachable. If `i > farthest` at any point, stuck.

```java
int farthest = 0;
for (int i = 0; i < nums.length; i++) {
    // Past the farthest reachable point — stuck, no way forward
    if (i > farthest) {
        return false;
    }
    // Extend reach: from index i, we can jump up to nums[i] positions
    farthest = Math.max(farthest, i + nums[i]);
}
return true;
```

---

### LC 45: Jump Game II

> **Problem:** Minimum number of jumps to reach the last index. Guaranteed reachable. Example: `[2,3,1,1,4]` → `2`.

> **Approach:** Track `currentEnd` (boundary of current jump) and `farthest` (best of next jump). When `i == currentEnd`, jump.

```java
int jumps = 0, currentEnd = 0, farthest = 0;
// Stop before last index — we only need to REACH it, not jump FROM it
for (int i = 0; i < nums.length - 1; i++) {
    // Track the best landing spot reachable from the current jump window
    farthest = Math.max(farthest, i + nums[i]);
    // Hit the boundary of the current jump — must take the next jump
    if (i == currentEnd) {
        jumps++;
        currentEnd = farthest;
    }
}
return jumps;
```

---

### LC 134: Gas Station

> **Problem:** Circular route with gas stations. At station `i`, get `gas[i]` fuel, cost `cost[i]` to next station. Find the starting station to complete the circuit, or -1. Example: `gas = [1,2,3,4,5], cost = [3,4,5,1,2]` → `3`.

> **Approach:** Pattern 2 — if `totalSurplus >= 0`, solution exists. Start where `currentSurplus` resets (goes negative → start = i+1).

```java
if (currentSurplus < 0) {
    start = i + 1;
    currentSurplus = 0;
}
return totalSurplus >= 0 ? start : -1;
```

---

### LC 763: Partition Labels

> **Problem:** Partition a string so each letter appears in at most one part. Maximize the number of parts. Return the sizes. Example: `"ababcbacadefegdehijhklij"` → `[9,7,8]`.

> **Approach:** Pattern 4 — record last occurrence of each char. Extend current partition end to the farthest last occurrence. Cut when `i == end`.

```java
// Extend partition boundary to cover this char's last occurrence
end = Math.max(end, lastIndex[s.charAt(i) - 'a']);
if (i == end) {
    // All chars in [start..end] have their last occurrence within this range — safe to cut
    result.add(end - start + 1);
    start = end + 1;
}
```

---

### LC 846: Hand of Straights

> **Problem:** Given a hand of cards, can you rearrange into groups of `groupSize` consecutive cards? Example: `hand = [1,2,3,6,2,3,4,7,8], groupSize = 3` → `true` ([1,2,3],[2,3,4],[6,7,8]).

> **Approach:** Pattern 5 — TreeMap for sorted frequencies. Start from smallest, form consecutive groups of size K. If any member missing, return false.

```java
// TreeMap: "sorted map — firstKey() gives smallest available card in O(log n)"
TreeMap<Integer, Integer> freq = new TreeMap<>();
// 🔄 Fallback: HashMap + Collections.sort(new ArrayList<>(freq.keySet()))
// Start from freq.firstKey(), try to form [start, start+groupSize)
```

---

### LC 1296: Divide Array in Sets of K Consecutive Numbers

> **Problem:** Same as LC 846 but with an array of integers instead of cards. Example: `nums = [1,2,3,3,4,4,5,6], k = 4` → `true`.

> **Approach:** Identical to LC 846 — TreeMap + greedy from smallest.

```java
// Same as Hand of Straights — exact same algorithm
```

---

### LC 678: Valid Parenthesis String

> **Problem:** Given a string with `(`, `)`, and `*` (which can be `(`, `)`, or empty), check if it's valid. Example: `"(*)"` → `true`.

> **Approach:** Track range `[lo, hi]` of possible open-paren counts. `(` → both++. `)` → both--. `*` → lo--, hi++. Keep `lo ≥ 0`. If `hi < 0`, invalid.

```java
// lo = minimum possible open parens, hi = maximum possible open parens
int lo = 0, hi = 0;
for (char c : s.toCharArray()) {
    if (c == '(') { lo++; hi++; }
    else if (c == ')') { lo--; hi--; }
    // '*' can be '(' (hi++), ')' (lo--), or empty
    else { lo--; hi++; }
    // No way to have enough open parens — too many close parens
    if (hi < 0) {
        return false;
    }
    // Open count can't go negative — clamp to 0
    lo = Math.max(lo, 0);
}
// Valid only if it's possible to have exactly 0 open parens remaining
return lo == 0;
```

---

### LC 135: Candy

> **Problem:** Children in a line, each has a rating. Give candies so: each child gets ≥1, higher-rated child gets more candy than their neighbor. Minimize total candies. Example: `ratings = [1,0,2]` → `5` (candies = [2,1,2]).

> **Approach:** Two-pass greedy. Left-to-right: if `rating[i] > rating[i-1]`, `candy[i] = candy[i-1]+1`. Right-to-left: if `rating[i] > rating[i+1]`, `candy[i] = max(candy[i], candy[i+1]+1)`.

```java
int[] candy = new int[n];
// Everyone gets at least 1 candy
Arrays.fill(candy, 1);
// Left pass: ensure higher-rated child gets more than left neighbor
for (int i = 1; i < n; i++) {
    if (ratings[i] > ratings[i - 1]) {
        candy[i] = candy[i - 1] + 1;
    }
}
// Right pass: ensure higher-rated child gets more than right neighbor too
for (int i = n - 2; i >= 0; i--) {
    if (ratings[i] > ratings[i + 1]) {
        // Take max to preserve the left-pass constraint
        candy[i] = Math.max(candy[i], candy[i + 1] + 1);
    }
}
```

---

### LC 435: Non-overlapping Intervals

> **Problem:** Find the minimum number of intervals to remove so the rest don't overlap. Example: `[[1,2],[2,3],[3,4],[1,3]]` → `1` (remove `[1,3]`).

> **Approach:** Sort by END time. Greedily keep intervals that don't overlap with the last kept one. Count removals. See `intervals.md` Pattern 4 for full coverage.

```java
// Lambda: "sort intervals by end time (index 1) — earliest-ending first"
Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
// 🔄 Fallback — anonymous Comparator:
//   Arrays.sort(intervals, new Comparator<int[]>() {
//       public int compare(int[] a, int[] b) { return a[1] - b[1]; }
//   });
int removed = 0, prevEnd = Integer.MIN_VALUE;
for (int[] interval : intervals) {
    // No overlap — keep this interval and update the boundary
    if (interval[0] >= prevEnd) {
        prevEnd = interval[1];
    } else {
        // Overlaps with previous — must remove this one
        removed++;
    }
}
```

---

### LC 452: Minimum Number of Arrows to Burst Balloons

> **Problem:** Balloons are `[start, end]`. An arrow at x bursts all balloons where `start ≤ x ≤ end`. Find minimum arrows. Example: `[[10,16],[2,8],[1,6],[7,12]]` → `2`.

> **Approach:** Same as LC 435 — sort by end. Each non-overlapping group needs one arrow. Use `Integer.compare` to avoid overflow.

```java
// Lambda: "sort by end — Integer.compare is overflow-safe (a[1]-b[1] FAILS for extreme values)"
Arrays.sort(points, (a, b) -> Integer.compare(a[1], b[1]));
// 🔄 Fallback — anonymous Comparator:
//   Arrays.sort(points, new Comparator<int[]>() {
//       public int compare(int[] a, int[] b) { return Integer.compare(a[1], b[1]); }
//   });
// ⚠️ Do NOT use a[1] - b[1] here — balloon coordinates can be Integer.MIN_VALUE/MAX_VALUE
// First group needs one arrow; track the end of current group's overlap
int arrows = 1, end = points[0][1];
for (int i = 1; i < points.length; i++) {
    // Balloon starts after current group ends — new group needs a new arrow
    if (points[i][0] > end) {
        arrows++;
        end = points[i][1];
    }
}
```

---

## ⚠️ Interview Gotchas

### Greedy vs DP — the most common mistake
- **LC 55 (Jump Game)** is greedy — you never need to backtrack
- **LC 322 (Coin Change)** looks greedy but is DP — taking the largest coin first doesn't always work (e.g., coins [1,3,4], amount 6: greedy gives 4+1+1=3 coins, optimal is 3+3=2 coins)
- **Rule of thumb:** If the greedy approach gives wrong answers on small examples, it's DP

### Edge cases interviewers probe
- **All zeros** (Jump Game) — `[0]` returns true (already at the end), `[0,1]` returns false
- **Single station** (Gas Station) — always return 0 if `gas[0] >= cost[0]`
- **All same characters** (Partition Labels) — one partition containing everything
- **Cannot form groups** (Hand of Straights) — `hand.length % groupSize != 0` → immediately false

### Follow-up questions to expect
- "Prove your greedy choice is optimal" — be ready to sketch an exchange argument (if I deviate from greedy, the result is no better)
- "Can you do it in O(n) time?" — most greedy solutions are already O(n) after sorting
- "What if the input is a stream?" — greedy often needs the full input (sorting), so streaming is harder

---

## 🧩 Speed Drill — 7 Minutes

**Part 1 — Greedy or DP? (2 minutes)**
For each problem, say "Greedy" or "DP":

1. "Can you reach the last index?" → ___
2. "Minimum coins to make amount" → ___
3. "Minimum jumps to reach end" → ___
4. "Maximum number of non-overlapping intervals" → ___
5. "Can you partition array into equal sum subsets?" → ___

**Part 2 — Write the Template (2 minutes)**
From memory, write the Jump Game I solution.

**Part 3 — Adapt (3 minutes)**
Solve LC 763 (Partition Labels) from memory. Time yourself.

**Scoring:**
- Part 1: Answers: Greedy, DP, Greedy, Greedy, DP. 5/5 → ready. Missed coin change as DP → re-read mental model.
- Part 2: Correct with `farthest` tracking → ready.
- Part 3: Under 3 minutes → ready. Forgot `lastIndex` array → re-read Pattern 4.

---

## 🔗 Cross-References

- **Intervals:** `../Interview/intervals.md` — Pattern 3 here (interval scheduling) is the same as Intervals Pattern 4
- **DP:** `../Interview/dp.md` — for problems where greedy doesn't work, DP is the fallback
- **Arrays & Hashing:** `../Interview/arrays-and-hashing.md` — frequency maps used in Patterns 4, 5
- **Heaps:** `../Interview/heaps.md` — some greedy problems use heaps for efficient "pick the best" (Task Scheduler)

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Greedy Algorithms Interview Playbook — 5 patterns (Jump/Reachability, Circular Greedy, Interval Scheduling, Partition by Boundary, Consecutive Group Matching), canonical walkthrough (LC 55 Jump Game), 9 problems with expanded definitions. |
| May 2026 | **Lambda & Fallback pass.** Added `freq.merge()` and `Integer.compare()` to Essential Methods table. Added 🔄 Lambda section with 4 explanations (sort-by-end lambda, Integer.compare overflow safety, TreeMap, freq.merge). Added inline English comments + 🔄 Fallback at 6 usage points: Pattern 3 sort, Pattern 5 TreeMap + merge, LC 846 TreeMap, LC 435 sort, LC 452 Integer.compare. |
