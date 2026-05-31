# Intervals — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to handle interval problems. Any problem mentioning "start/end times," "overlapping," or "merge intervals" is this file's territory.

---

## 🎯 Why You're Reading This

Interval problems look unique but they're all the same underneath: **sort by start → scan linearly → compare current.end with next.start**. Once you see this, every interval problem becomes a 10-minute solve. This file teaches you to recognize the 4 patterns and their one-line differences.

After reading this file, you should be able to:
1. Recognize interval patterns from problem wording in under 15 seconds
2. Know the sort order (by start? by end? matters!)
3. Handle the merge/overlap/gap logic without off-by-one errors

---

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `Arrays.sort(intervals, (a, b) -> a[0] - b[0])` | Sort intervals by start time | Patterns 1, 2, 3 |
| `Arrays.sort(intervals, (a, b) -> a[1] - b[1])` | Sort intervals by end time | Pattern 3 (greedy) |
| `Math.max(a, b)` / `Math.min(a, b)` | Extend or shrink interval endpoints | Patterns 1, 2, 4 |
| `list.toArray(new int[list.size()][])` | Convert List<int[]> back to `int[][]` | Pattern 1 |
| `PriorityQueue<Integer>` | Min-heap to track earliest end time (meeting rooms) | Pattern 3 |

> **Full reference:** `../Reference/dsa-collections-notes.md`, `../Reference/lambdas-for-dsa-reference.md`

### 🔄 Lambda & Shorthand Explanations with Fallbacks

**1. `Arrays.sort(intervals, (a, b) -> a[0] - b[0])` — Sort by start time**

```java
// What it does:
//   (a, b) -> a[0] - b[0] is a Comparator lambda for int[] arrays
//   a[0] = start of interval a,  b[0] = start of interval b
//   Negative result → a comes first (a starts earlier)
//   Positive result → b comes first
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

// 🔄 Fallback — use Integer.compare (overflow-safe):
Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));

// 🔄 Fallback 2 — use Comparator.comparingInt:
Arrays.sort(intervals, Comparator.comparingInt(a -> a[0]));
```

**2. `Arrays.sort(intervals, (a, b) -> a[1] - b[1])` — Sort by end time**

```java
// Same as above but comparing a[1] (end time) instead of a[0] (start)
// Used in greedy scheduling: keep the interval that finishes earliest
Arrays.sort(intervals, (a, b) -> a[1] - b[1]);

// 🔄 Fallback — overflow-safe:
Arrays.sort(intervals, (a, b) -> Integer.compare(a[1], b[1]));
```

**3. `list.toArray(new int[list.size()][])` — Convert List<int[]> to int[][]**

```java
// What it does:
//   Converts an ArrayList<int[]> to a primitive int[][] array
//   The argument new int[list.size()][] creates the target array shape
//   Java fills it with the list contents
return merged.toArray(new int[merged.size()][]);

// 🔄 Fallback — manual loop:
int[][] result = new int[merged.size()][];
for (int i = 0; i < merged.size(); i++) {
    result[i] = merged.get(i);
}
return result;
```

---

## 🧠 The Mental Model — The Universal Interval Recipe

Almost every interval problem follows this recipe:

1. **Sort** — usually by start time (sometimes by end time for greedy)
2. **Scan left to right** — compare the current interval's end with the next interval's start
3. **Three relationships between two intervals:**

```
Case 1: No overlap        Case 2: Overlap          Case 3: Containment
   [---]   [---]          [-----]                   [----------]
              [---]          [-----]                    [----]
  a.end < b.start         a.end >= b.start          a.end >= b.end
```

### Pattern Recognition — Which Interval Pattern?

```
Problem involves intervals/times
│
├── "Merge all overlapping intervals"
│   └── Pattern 1: Merge Intervals ⭐
│
├── "Insert a new interval into sorted list"
│   └── Pattern 2: Insert Interval
│       (three phases: before, merge, after)
│
├── "How many overlap at the same time?" / "Meeting rooms needed"
│   └── Pattern 3: Overlap Count ⭐
│       (min-heap or sweep line)
│
└── "Minimum intervals to remove for no overlap"
    └── Pattern 4: Greedy Interval Scheduling
        (sort by END, keep the one that finishes earliest)
```

### 🎨 Visual — The Three Overlap Cases

```
Timeline: 0  1  2  3  4  5  6  7  8  9  10

Case 1 — No overlap (a.end < b.start):
  a: [===]
  b:          [===]
  Action: close a, start fresh with b

Case 2 — Partial overlap (a.end >= b.start, a.end < b.end):
  a: [=======]
  b:      [=======]
  Merged: [===========]    → new end = max(a.end, b.end)

Case 3 — Containment (a.end >= b.end):
  a: [=============]
  b:    [=====]
  Merged: [=============]  → a already contains b, no change

KEY INVARIANT:
   After sorting by start, two intervals overlap iff:
   current.end >= next.start
   This single comparison drives ALL interval patterns.
```

---

## 🧭 Pattern 1: Merge Intervals ⭐

**Recognition cues — reach for this when:**
- "Merge all overlapping intervals"
- "Return non-overlapping intervals that cover all ranges"
- Input is a list of intervals, output is a merged list

**Steps in plain English:**

1. **Sort by start time** — so overlapping intervals are adjacent.
2. **Initialize result with first interval.**
3. **For each next interval** — compare with the last interval in result:
   - If overlap (`last.end >= next.start`) → extend: `last.end = max(last.end, next.end)`.
   - If no overlap → add `next` as a new interval in result.

```java
public int[][] merge(int[][] intervals) {
    // (a, b) -> a[0] - b[0]: sort by start time ascending
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    // 🔄 Fallback: Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
    List<int[]> merged = new ArrayList<>();
    merged.add(intervals[0]);
    for (int i = 1; i < intervals.length; i++) {
        int[] last = merged.get(merged.size() - 1);
        int[] curr = intervals[i];
        if (last[1] >= curr[0]) {
            // Overlap — extend
            last[1] = Math.max(last[1], curr[1]);
        } else {
            // No overlap — new interval
            merged.add(curr);
        }
    }
    // toArray: converts List<int[]> to int[][] — the argument sets target array shape
    return merged.toArray(new int[merged.size()][]);
}
```

**🏷️ Problems:** LC 56 (Merge Intervals), LC 986 (Interval List Intersections — see Pattern 4 variant).

---

## 🧭 Pattern 2: Insert Interval

**Recognition cues — reach for this when:**
- "Insert a new interval into a sorted non-overlapping list"
- "Merge with existing intervals if overlapping"
- Input is already sorted and non-overlapping, plus one new interval

**Steps in plain English:**

1. **Phase 1 — Before:** Add all intervals that end BEFORE the new one starts.
2. **Phase 2 — Merge:** Merge all intervals that overlap with the new one (extend new interval's boundaries).
3. **Phase 3 — After:** Add all remaining intervals.

```java
public int[][] insert(int[][] intervals, int[] newInterval) {
    List<int[]> result = new ArrayList<>();
    int i = 0;
    // Phase 1 — intervals that end before newInterval starts
    while (i < intervals.length && intervals[i][1] < newInterval[0]) {
        result.add(intervals[i]);
        i++;
    }
    // Phase 2 — merge overlapping intervals
    while (i < intervals.length && intervals[i][0] <= newInterval[1]) {
        newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
        newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
        i++;
    }
    result.add(newInterval);
    // Phase 3 — remaining intervals
    while (i < intervals.length) {
        result.add(intervals[i]);
        i++;
    }
    return result.toArray(new int[result.size()][]);
}
```

**🏷️ Problems:** LC 57 (Insert Interval).

---

## 🧭 Pattern 3: Overlap Count (Meeting Rooms) ⭐

**Recognition cues — reach for this when:**
- "Minimum number of meeting rooms"
- "Maximum number of overlapping intervals at any point"
- "Can a person attend all meetings?" (simpler: just check ANY overlap)

**Two approaches:**

### Approach A — Min-Heap (Most Common in Interviews)

**Steps in plain English:**

1. **Sort by start time.**
2. **Min-heap holds end times** of currently active meetings.
3. **For each meeting** — if its start ≥ heap's min end, that room is freed (poll).
4. **Add current meeting's end** to the heap.
5. **Answer = max heap size** seen during the scan.

```java
public int minMeetingRooms(int[][] intervals) {
    // (a, b) -> a[0] - b[0]: sort by start time
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    // 🔄 Fallback: Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
    // PriorityQueue<Integer> = default min-heap (earliest end time at top)
    PriorityQueue<Integer> pq = new PriorityQueue<>();
    for (int[] interval : intervals) {
        // If earliest-ending meeting is done, reuse that room
        if (!pq.isEmpty() && pq.peek() <= interval[0]) {
            pq.poll();
        }
        pq.offer(interval[1]);
    }
    return pq.size();
}
```

### Approach B — Sweep Line (Event-Based)

**Steps in plain English:**

1. **Create events** — each interval generates a +1 at start and a -1 at end.
2. **Sort all events** by time (if tie, process -1 before +1).
3. **Sweep through events** — maintain a running count. Max count = answer.

```java
public int minMeetingRooms(int[][] intervals) {
    int n = intervals.length;
    int[] starts = new int[n];
    int[] ends = new int[n];
    for (int i = 0; i < n; i++) {
        starts[i] = intervals[i][0];
        ends[i] = intervals[i][1];
    }
    Arrays.sort(starts);
    Arrays.sort(ends);
    int rooms = 0, maxRooms = 0, endPtr = 0;
    for (int i = 0; i < n; i++) {
        if (starts[i] < ends[endPtr]) {
            rooms++;
        } else {
            endPtr++;
        }
        maxRooms = Math.max(maxRooms, rooms);
    }
    return maxRooms;
}
```

**🏷️ Problems:** LC 252 (Meeting Rooms — just check any overlap), LC 253 (Meeting Rooms II — count).

---

## 🧭 Pattern 4: Greedy Interval Scheduling (Remove Minimum Overlaps)

**Recognition cues — reach for this when:**
- "Minimum number of intervals to remove so the rest don't overlap"
- "Maximum number of non-overlapping intervals"
- "Activity selection problem" — attend the most events possible

**The key insight:** Sort by END time. Always keep the interval that finishes earliest — this leaves the most room for future intervals.

**Steps in plain English:**

1. **Sort by end time** — NOT by start time (this is the greedy choice).
2. **Track the end of the last kept interval.**
3. **For each interval** — if it starts after (or at) the last end, keep it. Otherwise, remove it.

```java
public int eraseOverlapIntervals(int[][] intervals) {
    // (a, b) -> a[1] - b[1]: sort by END time (greedy choice — keep earliest-ending)
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
    // 🔄 Fallback: Arrays.sort(intervals, (a, b) -> Integer.compare(a[1], b[1]));
    int removed = 0;
    int prevEnd = Integer.MIN_VALUE;
    for (int[] interval : intervals) {
        if (interval[0] >= prevEnd) {
            // No overlap — keep this interval
            prevEnd = interval[1];
        } else {
            // Overlap — remove this interval (greedy: keep the earlier-ending one)
            removed++;
        }
    }
    return removed;
}
```

**🏷️ Problems:** LC 435 (Non-overlapping Intervals), LC 452 (Minimum Number of Arrows to Burst Balloons).

---

## 🔬 Canonical Problem — LC 56: Merge Intervals

> **Problem:** Given an array of intervals where `intervals[i] = [start_i, end_i]`, merge all overlapping intervals and return an array of non-overlapping intervals. Example: `intervals = [[1,3],[2,6],[8,10],[15,18]]` → `[[1,6],[8,10],[15,18]]`.

### Step 1 — Read and identify triggers

"Merge all overlapping intervals" — directly triggers **Pattern 1: Merge Intervals**. The words "merge" + "overlapping" are the giveaway.

### Step 2 — Choose the template

Merge Intervals template. Key decisions:
- Sort by start time (so overlapping intervals are adjacent).
- Compare `last.end` with `curr.start` to detect overlap.
- Extend `last.end = max(last.end, curr.end)` on overlap.

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Sort by start time.**
2. **Start with the first interval in the result.**
3. **For each next interval** — if `last.end >= curr.start`, merge by extending `last.end`. Otherwise, start a new interval.

```java
class Solution {
    public int[][] merge(int[][] intervals) {
        // (a, b) -> a[0] - b[0]: sort by start time
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        // 🔄 Fallback: Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        merged.add(intervals[0]);
        for (int i = 1; i < intervals.length; i++) {
            int[] last = merged.get(merged.size() - 1);
            int[] curr = intervals[i];
            if (last[1] >= curr[0]) {
                last[1] = Math.max(last[1], curr[1]);
            } else {
                merged.add(curr);
            }
        }
        return merged.toArray(new int[merged.size()][]);
    }
}
```

### Step 4 — Verify with example

`intervals = [[1,3],[2,6],[8,10],[15,18]]`:
- Sort: already sorted by start → [[1,3],[2,6],[8,10],[15,18]]
- Start: merged = [[1,3]]
- [2,6]: last=[1,3], 3 >= 2 → overlap → last=[1,6]. merged = [[1,6]]
- [8,10]: last=[1,6], 6 < 8 → no overlap → add. merged = [[1,6],[8,10]]
- [15,18]: last=[8,10], 10 < 15 → no overlap → add. merged = [[1,6],[8,10],[15,18]] ✅

### Complexity

- **Time:** O(n log n) — dominated by sorting
- **Space:** O(n) — for the result list (O(log n) for sorting)

---

## ⚡ Problem Bank — Key Twists

---

### LC 56: Merge Intervals

> **Problem:** Merge all overlapping intervals. Example: `[[1,3],[2,6],[8,10],[15,18]]` → `[[1,6],[8,10],[15,18]]`.

> **Approach:** Sort by start. Scan: if `last.end >= curr.start`, extend. Otherwise, new interval.

```java
// Overlap detected — extend the last merged interval's end
if (last[1] >= curr[0]) last[1] = Math.max(last[1], curr[1]);
// No overlap — curr starts a new non-overlapping group
else merged.add(curr);
```

---

### LC 57: Insert Interval

> **Problem:** Insert a new interval into a sorted non-overlapping list, merging if necessary. Example: `intervals = [[1,3],[6,9]], newInterval = [2,5]` → `[[1,5],[6,9]]`.

> **Approach:** Three phases: (1) add all before, (2) merge all overlapping with newInterval, (3) add all after.

```java
// Phase 2 — absorb every interval that overlaps with newInterval
while (i < n && intervals[i][0] <= newInterval[1]) {
    // Expand newInterval to encompass the overlapping interval
    newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
    newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
    i++;
}
// The fully merged newInterval now covers all overlapping ranges
result.add(newInterval);
```

---

### LC 252: Meeting Rooms

> **Problem:** Given an array of meeting time intervals, determine if a person could attend all meetings (no overlaps). Example: `[[0,30],[5,10],[15,20]]` → `false`.

> **Approach:** Sort by start. If any `intervals[i].start < intervals[i-1].end`, there's an overlap → return false.

```java
// (a, b) -> a[0] - b[0]: sort by start time
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
// 🔄 Fallback: Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
for (int i = 1; i < intervals.length; i++) {
    // If this meeting starts before the previous one ends, there's a conflict
    if (intervals[i][0] < intervals[i - 1][1]) return false;
}
return true;
```

---

### LC 253: Meeting Rooms II

> **Problem:** Find the minimum number of meeting rooms required. Example: `[[0,30],[5,10],[15,20]]` → `2`.

> **Approach:** Pattern 3 — sort by start, min-heap of end times. If `peek() <= start`, reuse room (poll). Add current end. Answer = max heap size.

```java
// If the earliest-ending meeting finishes before this one starts, free that room
if (!pq.isEmpty() && pq.peek() <= interval[0]) pq.poll();
// Allocate a room for this meeting (track its end time)
pq.offer(interval[1]);
// Answer: pq.size() at the end = peak number of concurrent meetings
```

---

### LC 435: Non-overlapping Intervals

> **Problem:** Find the minimum number of intervals to remove so the remaining intervals don't overlap. Example: `[[1,2],[2,3],[3,4],[1,3]]` → `1` (remove [1,3]).

> **Approach:** Pattern 4 — sort by END time. Greedily keep intervals that don't overlap with the last kept one. Count removals.

```java
// (a, b) -> a[1] - b[1]: sort by END time (greedy — keep earliest-ending)
Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
// 🔄 Fallback: Arrays.sort(intervals, (a, b) -> Integer.compare(a[1], b[1]));
// Keep if interval[0] >= prevEnd, else remove (increment count)
```

---

### LC 452: Minimum Number of Arrows to Burst Balloons

> **Problem:** Balloons are `[start, end]` on a wall. An arrow at x bursts all balloons where `start ≤ x ≤ end`. Find minimum arrows to burst all balloons. Example: `[[10,16],[2,8],[1,6],[7,12]]` → `2`.

> **Approach:** Same as LC 435 — sort by end. Group overlapping balloons. Each non-overlapping group needs one arrow.

```java
// Integer.compare(a[1], b[1]): overflow-safe sort by end (needed here because values can be Integer.MAX_VALUE)
Arrays.sort(points, (a, b) -> Integer.compare(a[1], b[1]));
// 🔄 Fallback: a[1] - b[1] FAILS here (overflow!) — must use Integer.compare
// First arrow covers the first balloon group
int arrows = 1, end = points[0][1];
for (int i = 1; i < points.length; i++) {
    // This balloon starts after the current arrow's reach — need a new arrow
    if (points[i][0] > end) {
        arrows++;
        end = points[i][1];
    }
}
```

---

### LC 986: Interval List Intersections

> **Problem:** Given two sorted lists of non-overlapping intervals, return their intersection. Example: `A = [[0,2],[5,10]], B = [[1,5],[8,12]]` → `[[1,2],[5,5],[8,10]]`.

> **Approach:** Two pointers (one per list). Intersection exists when `max(a.start, b.start) ≤ min(a.end, b.end)`. Advance the pointer with the smaller end.

```java
// Intersection start = the later of the two starts
int lo = Math.max(A[i][0], B[j][0]);
// Intersection end = the earlier of the two ends
int hi = Math.min(A[i][1], B[j][1]);
// Valid intersection only when start <= end
if (lo <= hi) result.add(new int[]{lo, hi});
// Advance the pointer whose interval ends first (it can't intersect anything else)
if (A[i][1] < B[j][1]) i++;
else j++;
```

---

### LC 1288: Remove Covered Intervals

> **Problem:** Remove intervals that are covered by another interval. Interval `[a,b]` is covered by `[c,d]` if `c ≤ a` and `b ≤ d`. Return the count of remaining intervals. Example: `[[1,4],[3,6],[2,8]]` → `2`.

> **Approach:** Sort by start (ascending), then by end (descending — so the widest interval comes first). Track the farthest end seen. If current end ≤ farthest end, it's covered.

```java
// Sort by start ascending; same start → sort by end DESCENDING (widest interval first)
// a[0] != b[0] ? a[0] - b[0] : b[1] - a[1]
//   Different starts → earlier start first
//   Same start → longer interval first (so shorter one is "covered")
Arrays.sort(intervals, (a, b) -> a[0] != b[0] ? a[0] - b[0] : b[1] - a[1]);
// 🔄 Fallback: Arrays.sort(intervals, (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(b[1], a[1]));
int count = 0, farthestEnd = 0;
for (int[] interval : intervals) {
    // If this interval extends beyond the farthest end, it's NOT covered
    if (interval[1] > farthestEnd) {
        count++;
        farthestEnd = interval[1];
    }
    // Otherwise interval[1] <= farthestEnd → fully covered by a previous interval, skip
}
return count;
```

---

## ⚠️ Interview Gotchas

### Edge cases interviewers probe
- **Empty input** — return empty result (check `intervals.length == 0` before sorting)
- **Single interval** — return as-is (no merging needed)
- **All intervals overlap** — result is one merged interval
- **Touching intervals** — `[1,2]` and `[2,3]`: depends on problem — some say overlap, some say not. Clarify with interviewer! (For LC 56, touching = overlap because `2 >= 2`)

### Sort order matters!
- **Merge / Insert / Meeting Rooms** → sort by START time
- **Greedy scheduling (LC 435, 452)** → sort by END time (this is the critical difference)
- Using the wrong sort order will give wrong results — the code looks right but the greedy proof breaks

### Follow-up questions to expect
- "What if intervals are given as a stream?" → Can't sort upfront, need a balanced BST or segment tree
- "What if intervals have weights?" → Weighted interval scheduling — needs DP, not just greedy
- "Can you do Meeting Rooms II without a heap?" → Yes, sweep-line with two sorted arrays

### Complexity traps
- All these patterns are O(n log n) — dominated by sorting. The scan is O(n). Don't accidentally say O(n) — sorting is the bottleneck.
- Meeting Rooms II with min-heap: O(n log n) for sort + O(n log n) for heap operations = O(n log n) total

---

## 🧩 Speed Drill — 6 Minutes

**Part 1 — Pattern Recognition (1 minute)**
For each problem description, name the pattern:

1. "Merge overlapping intervals" → ___
2. "Minimum meeting rooms needed" → ___
3. "Remove minimum intervals for no overlap" → ___
4. "Insert new interval into sorted list" → ___

**Part 2 — Write the Template (2 minutes)**
From memory, write the Merge Intervals solution (Pattern 1).

**Part 3 — Adapt (3 minutes)**
What changes if the problem asks for "minimum intervals to remove" instead of "merge"? (Answer: sort by END, not START).

**Scoring:**
- Part 1: 4/4 correct → ready.
- Part 2: Template correct with `last[1] = Math.max(last[1], curr[1])` → ready.
- Part 3: Said "sort by end" → ready. Said "sort by start" → re-read Pattern 4.

---

## 🔗 Cross-References

- **Heaps:** `../Interview/heaps.md` — Pattern 3 here uses a min-heap (same as Heap Pattern 2)
- **Arrays & Hashing:** `../Interview/arrays-and-hashing.md` — sorting as a pre-processing step
- **Greedy:** `../Interview/greedy.md` — Pattern 4 here is a greedy algorithm (activity selection)
- **Two Pointers:** `../Interview/two-pointers-and-sliding-window.md` — LC 986 uses two-pointer technique on two sorted lists

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Intervals Interview Playbook — 4 patterns (Merge, Insert, Overlap Count, Greedy Scheduling), canonical walkthrough (LC 56 Merge Intervals), 8 problems with expanded definitions. |
| May 2026 | **Lambda & Fallback pass.** Added 🔄 Lambda section with explanations for `Arrays.sort` comparator lambdas, `list.toArray()`, and complex comparators. Added inline English comments + 🔄 Fallback at all 8 usage points across templates and problem bank. |
