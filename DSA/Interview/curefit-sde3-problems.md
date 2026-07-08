# Curefit SDE 3 — Backend Interview Problem Set

> **Role:** SDE 3 - Backend | **Round:** Exploratory Call (confirmed DSA by HR)
> **Format:** Live coding, 45–60 min, 1–2 problems. Brute force NOT accepted — must reach optimal.
> **Source:** Compiled from real Curefit interview reports (2021–2025, LeetCode Discuss / GFG / Glassdoor)

> ⚠️ **Post-Interview Update (Round 1 — Jul 2026):**
> The actual questions asked were NOT from the top 10 list above.
> Q1: Maximize Distance to Closest Person (LC #849) — Gap Scanning pattern
> Q2: Minimize Max Gap after removing K gems — Sliding Window on Diffs pattern
> Both are added below as **Bonus Section** for Round 2 prep.

---

## Table of Contents

1. [Gas Station](#1-gas-station--lc-134)
2. [Trapping Rain Water](#2-trapping-rain-water--lc-42)
3. [Course Schedule II / Topological Sort](#3-course-schedule-ii--lc-210)
4. [Minimum Platforms / Meeting Rooms II](#4-minimum-platforms--meeting-rooms-ii--lc-253)
5. [Subsets / Subset Sum](#5-subsets--subset-sum--lc-78--416)
6. [Number of Islands](#6-number-of-islands--lc-200)
7. [LCA of BST / First Common in Two BSTs](#7-lca-of-bst--first-common-element-in-two-bsts)
8. [Longest Univalue Path in Binary Tree](#8-longest-univalue-path-in-binary-tree--lc-687)
9. [Jump Game II](#9-jump-game-ii--lc-45)
10. [Word Break](#10-word-break--lc-139)

---

### ⚠️ Bonus — Actual Round 1 Questions (Missed Patterns)

11. [Maximize Distance to Closest Person](#11-maximize-distance-to-closest-person--lc-849) ← **Q1 asked in Round 1**
12. [Minimize Max Gap After K Removals](#12-minimize-max-gap-after-k-removals--custom) ← **Q2 asked in Round 1**
13. [Magnetic Force Between Two Balls](#13-magnetic-force-between-two-balls--lc-1552) ← same pattern family as Q2
14. [Split Array Largest Sum](#14-split-array-largest-sum--lc-410) ← same pattern family as Q2

---

---

## 1. Gas Station — LC #134

**Difficulty:** Medium | **Pattern:** Greedy  
**Confirmed in:** Multiple Curefit backend interview reports

---

### 🎯 Problem Statement

There are `n` gas stations arranged in a circle. At station `i`:
- You gain `gas[i]` fuel.
- It costs `cost[i]` fuel to travel to station `i+1`.

You start with an empty tank. Find the starting station index from which you can complete the full circle. If no such station exists, return `-1`.

**Constraints:** Exactly one valid answer exists if it is possible.

```
Example:
gas  = [1, 2, 3, 4, 5]
cost = [3, 4, 5, 1, 2]

Output: 3
Explanation: Start at station 3 (0-indexed).
  Station 3 → 4: tank = 4 - 1 = 3, travel cost 2 → tank = 1
  Station 4 → 0: tank = 1 + 5 = 6, travel cost 2 → tank = 4
  Station 0 → 1: tank = 4 + 1 = 5, travel cost 3 → tank = 2
  Station 1 → 2: tank = 2 + 2 = 4, travel cost 4 → tank = 0
  Station 2 → 3: tank = 0 + 3 = 3, travel cost 5 → tank = -2 ✗... wait
  Actually: start=3 works, verify manually.
```

---

### 🧠 Discussion — How to Think About This

**First, ask yourself:** When is it even possible?

If the total fuel across all stations is less than the total cost, it is **impossible** regardless of where you start. You simply don't have enough gas.

```
total_gain = sum(gas[i] - cost[i])
if total_gain < 0 → return -1
```

**If total_gain >= 0, a solution is guaranteed to exist.** (This is a key mathematical property — trust it and move on.)

Now the question becomes: **where exactly do you start?**

---

### 🐌 Brute Force Approach

Try every station as a starting point. Simulate the full circle from each.

**Steps in plain English:**
1. For each station `i` (outer loop), try starting there with `tank = 0`.
2. Inner loop: simulate travel from `i` → `i+1` → ... → `i-1` (wrapping with `% n`).
3. At each step: `tank += gas[j] - cost[j]`. If `tank < 0` at any point, this start fails.
4. If you complete the full loop with `tank >= 0`, return `i`.
5. If no start works, return `-1`.

```java
// Brute Force — O(n²) time, O(1) space
public int canCompleteCircuit(int[] gas, int[] cost) {
    int n = gas.length;
    for (int start = 0; start < n; start++) {
        int tank = 0;
        boolean valid = true;
        for (int step = 0; step < n; step++) {
            int idx = (start + step) % n;
            tank += gas[idx] - cost[idx];
            if (tank < 0) {
                valid = false;
                break;
            }
        }
        if (valid) {
            return start;
        }
    }
    return -1;
}
```

**Why is this slow?** For each of n starting points, we simulate n steps → O(n²).

---

### 💡 Idea Behind Optimisation

**The key insight:** If you start at station `s` and your tank goes negative at station `k`, then **no station between `s` and `k` can be a valid starting point either**.

Why? Because if you couldn't make it from `s` to `k`, starting at any intermediate station `s+1`, `s+2`, ..., `k` would give you even less cumulative fuel at `k` (you'd skip the positive contributions of stations before your new start).

So instead of retrying from `s+1`, you can **jump your candidate start to `k+1`** and skip everything in between.

**Algorithm:**
- Track `tank` (running fuel from current candidate start).
- Track `total` (sum of all `gas[i] - cost[i]` — to decide if solution exists at all).
- Whenever `tank < 0`, reset: set `start = i + 1`, reset `tank = 0`.
- At the end: if `total >= 0`, return `start`. Else return `-1`.

### 🎨 Visual — Greedy Skip Logic

```
gas  = [1, 2, 3, 4, 5]
cost = [3, 4, 5, 1, 2]
diff = [-2,-2,-2, 3, 3]   ← net[i] = gas[i] - cost[i]

Scan left to right, track running tank:
i=0: tank = -2 → NEGATIVE → start=1, reset tank=0
i=1: tank = -2 → NEGATIVE → start=2, reset tank=0
i=2: tank = -2 → NEGATIVE → start=3, reset tank=0
i=3: tank = +3 → OK
i=4: tank = +6 → OK

End: total = -2-2-2+3+3 = 0 ≥ 0 → solution exists → return start=3 ✅

KEY INVARIANT:
  If tank goes negative at i, every station from [start..i] is invalid.
  Leap candidate start to i+1 — never look back.
```

---

### 🚀 Optimal Java Solution

```java
public int canCompleteCircuit(int[] gas, int[] cost) {
    int tank = 0;
    int total = 0;
    int start = 0;

    for (int i = 0; i < gas.length; i++) {
        int net = gas[i] - cost[i];

        // Running tank from current candidate start
        tank += net;

        // Running sum across all stations (existence check)
        total += net;

        // Current candidate start fails — leap forward
        if (tank < 0) {
            start = i + 1;
            tank = 0;
        }
    }

    // total >= 0 guarantees exactly one valid start exists
    return total >= 0 ? start : -1;
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(n) | Single pass through all stations |
| **Space** | O(1) | Only 3 integer variables |

---

### 🔁 Follow-Up Questions

**Q1: What if there are multiple valid starting points?**
> The problem guarantees uniqueness, but if asked to return all: brute force O(n²) is the only way since you can't skip — each start needs full simulation.

**Q2: Why does the greedy skip work — prove it.**
> If `tank < 0` at station `k` starting from `s`, then for any intermediate start `m` (where `s < m <= k`):  
> `tank_from_m_at_k = tank_from_s_at_k - prefix_sum(s to m-1)`  
> Since `prefix_sum(s to m-1) >= 0` (otherwise we'd have reset at `m-1` earlier), the tank at `k` starting from `m` is ≤ the tank starting from `s` — which is already negative. So `m` cannot work.

**Q3: What if gas and cost arrays can have negative values?**
> The greedy still works — the existence check (`total >= 0`) handles it. Negative fuel is just a heavier cost.

**Q4: Follow-up variant — find the minimum cost to complete the circle (weighted edges)?**
> This becomes a graph shortest path problem → Dijkstra's. Different problem class entirely.

---

---

## 2. Trapping Rain Water — LC #42

**Difficulty:** Hard | **Pattern:** Two Pointers / Prefix-Suffix Arrays  
**Confirmed in:** Exact phrasing — *"collect water between pillars"* — in Curefit interview reports

---

### 🎯 Problem Statement

Given an array `height[]` where each element represents the height of a bar (pillar), compute how much water can be trapped between the bars after it rains.

```
Example:
height = [0, 1, 0, 2, 1, 0, 1, 3, 1, 2, 1, 2]
Output: 6

Visual:
        █
    █   █ █ █ █
  █ █ █ █ █ █ █ █
  0 1 0 2 1 0 1 3 1 2 1 2

Water trapped (shown as ~):
        █
    █~~~█~█~█ █
  █ █~█ █ █ █ █ █
  Total = 6 units
```

---

### 🧠 Discussion — How to Think About This

**Key observation:** The water above position `i` is determined by the **shorter of the tallest bar to its left and the tallest bar to its right**, minus the bar's own height.

```
water[i] = max(0, min(maxLeft[i], maxRight[i]) - height[i])
```

Think of it like a bucket: the water level is capped by whichever side is shorter (it would spill over the shorter wall). If the bar itself is taller than the water level, no water sits there.

---

### 🐌 Brute Force Approach

For each position `i`, scan left to find `maxLeft` and scan right to find `maxRight`.

**Steps in plain English:**
1. For each index `i` from `0` to `n-1`:
2. Scan left from `i` to find the tallest bar: `maxLeft = max(height[0..i])`.
3. Scan right from `i` to find the tallest bar: `maxRight = max(height[i..n-1])`.
4. Water at `i` = `max(0, min(maxLeft, maxRight) - height[i])`.
5. Accumulate total.

```java
// Brute Force — O(n²) time, O(1) space
public int trap(int[] height) {
    int n = height.length;
    int total = 0;

    for (int i = 0; i < n; i++) {
        int maxLeft = 0;
        // Scan left for tallest bar including i itself
        for (int l = 0; l <= i; l++) {
            maxLeft = Math.max(maxLeft, height[l]);
        }

        int maxRight = 0;
        // Scan right for tallest bar including i itself
        for (int r = i; r < n; r++) {
            maxRight = Math.max(maxRight, height[r]);
        }

        total += Math.min(maxLeft, maxRight) - height[i];
    }

    return total;
}
```

**Why is this slow?** For each of n positions, we scan up to n positions → O(n²).

---

### 💡 Idea Behind Optimisation

**Step 1 — Prefix-Suffix arrays (O(n) time, O(n) space):**

Pre-compute `maxLeft[i]` (max height from index 0 to i) and `maxRight[i]` (max height from i to n-1) in two separate passes. Then a third pass applies the formula. This eliminates redundant scanning.

**Step 2 — Two Pointers (O(n) time, O(1) space):**

We can do even better. Notice that `water[i]` only depends on `min(maxLeft, maxRight)`. At any position, we only care about the **shorter side** — the taller side is irrelevant (water spills from the shorter wall).

Two-pointer insight:
- Maintain `left` and `right` pointers starting from both ends.
- Track `maxLeft` and `maxRight` as we move inward.
- If `height[left] < height[right]`: the left side is the bottleneck. Water at `left` = `maxLeft - height[left]`. Move `left` inward.
- Else: the right side is the bottleneck. Water at `right` = `maxRight - height[right]`. Move `right` inward.

We never need to look at the other side because we already know which side is shorter — and that's the only one that matters.

### 🎨 Visual — Two Pointer Logic

```
height = [0, 1, 0, 2, 1, 0, 1, 3, 1, 2, 1, 2]
          L                                   R

Step 1: h[L]=0, h[R]=2 → left is shorter
        maxLeft=0, water at L = max(0, 0-0) = 0
        L++

Step 2: h[L]=1, h[R]=2 → left is shorter
        maxLeft=1, water at L = max(0, 1-1) = 0
        L++

Step 3: h[L]=0, h[R]=2 → left is shorter
        maxLeft=1, water at L = max(0, 1-0) = 1 ← trapped!
        L++

... and so on, always processing the shorter side

KEY INVARIANT:
  When processing the LEFT pointer, maxLeft is accurate.
  We don't need to know maxRight exactly — we know h[right] >= maxLeft
  (because we chose left as the shorter side), so maxRight >= maxLeft.
  The bottleneck is always maxLeft. Process left, move inward.
  Mirror logic for right pointer.
```

---

### 🚀 Optimal Java Solution — Two Pointers

```java
public int trap(int[] height) {
    int left = 0;
    int right = height.length - 1;
    int maxLeft = 0;
    int maxRight = 0;
    int water = 0;

    while (left < right) {
        if (height[left] < height[right]) {
            // Left side is the bottleneck
            if (height[left] >= maxLeft) {
                // New max on left — no water trapped here, update max
                maxLeft = height[left];
            } else {
                // Current bar is shorter than maxLeft — water fills the gap
                water += maxLeft - height[left];
            }
            left++;
        } else {
            // Right side is the bottleneck
            if (height[right] >= maxRight) {
                // New max on right — no water trapped here, update max
                maxRight = height[right];
            } else {
                // Current bar is shorter than maxRight — water fills the gap
                water += maxRight - height[right];
            }
            right--;
        }
    }

    return water;
}
```

**Bonus — Prefix-Suffix solution (easier to explain in an interview, then optimise to two-pointers):**

```java
public int trapPrefixSuffix(int[] height) {
    int n = height.length;
    int[] maxLeft = new int[n];
    int[] maxRight = new int[n];

    // Build maxLeft: tallest bar from 0 to i
    maxLeft[0] = height[0];
    for (int i = 1; i < n; i++) {
        maxLeft[i] = Math.max(maxLeft[i - 1], height[i]);
    }

    // Build maxRight: tallest bar from i to n-1
    maxRight[n - 1] = height[n - 1];
    for (int i = n - 2; i >= 0; i--) {
        maxRight[i] = Math.max(maxRight[i + 1], height[i]);
    }

    // Accumulate water at each position
    int water = 0;
    for (int i = 0; i < n; i++) {
        water += Math.min(maxLeft[i], maxRight[i]) - height[i];
    }

    return water;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force | O(n²) | O(1) |
| Prefix-Suffix Arrays | O(n) | O(n) |
| **Two Pointers (optimal)** | **O(n)** | **O(1)** |

---

### 🔁 Follow-Up Questions

**Q1: What if the bars have widths (not just heights)?**
> Each bar occupies some width. This becomes the **Container With Most Water** variant (LC #11) — same two-pointer pattern applies but formula changes to `width × min(h[l], h[r])`.

**Q2: Can you do this in 3D? (Trapping Rain Water II — LC #407)**
> Yes! Instead of two pointers, use a **min-heap (priority queue)** seeded with all boundary cells. BFS inward: always process the lowest boundary cell, fill water up to its level. O(m×n×log(m×n)) time.

**Q3: Walk me through the two-pointer proof — why is it safe to process only one side?**
> When `height[left] < height[right]`:  
> We know `maxRight >= height[right] > height[left]`.  
> Therefore `min(maxLeft, maxRight) = maxLeft` (since `maxLeft` is currently bounded by `height[left]` which is smaller than the right side).  
> So `water[left] = maxLeft - height[left]` — we don't need to know `maxRight` at all. ✅

**Q4: What if all bars have the same height?**
> `min(maxLeft, maxRight) - height[i] = height - height = 0` for all positions. Answer is 0. Both brute force and optimal handle this correctly.

**Q5: What if input is empty or has fewer than 3 elements?**
> Need at least 3 elements to trap any water. Return 0 for `n < 3`. Add a guard at the start:
> ```java
> if (height == null || height.length < 3) return 0;
> ```

---

---

## 3. Course Schedule II — LC #210

**Difficulty:** Medium | **Pattern:** Topological Sort (BFS — Kahn's Algorithm)
**Confirmed in:** Explicitly mentioned in Curefit backend interview reports as "Topological Sort problem"

---

### 🎯 Problem Statement

There are `numCourses` courses labeled `0` to `numCourses-1`. You are given a list of `prerequisites` where `prerequisites[i] = [a, b]` means you must take course `b` before course `a`.

Return a valid ordering to finish all courses. If impossible (cycle exists), return an empty array.

```
Example:
numCourses = 4
prerequisites = [[1,0],[2,0],[3,1],[3,2]]

Output: [0, 2, 1, 3]  (or [0, 1, 2, 3] — multiple valid answers)

Visual:
  0 → 1 → 3
  0 → 2 → 3
  Must finish 0 before 1 and 2; must finish both before 3.
```

---

### 🧠 Discussion — How to Think About This

**Frame it as a graph problem:** Each course is a node. Each prerequisite `[a, b]` is a directed edge `b → a` (b must come before a).

A valid ordering is a **topological sort** of this DAG (directed acyclic graph — a graph with no cycles). If a cycle exists, no valid ordering exists.

**Key concept — in-degree:** The in-degree of a node is the number of edges pointing *into* it. A course with in-degree 0 has no prerequisites — it can be taken immediately.

---

### 🐌 Brute Force Approach

DFS with visited state tracking. Try all possible orderings, backtrack on conflicts.

- O(V + E) time but complex to implement correctly and explain under pressure.
- The BFS (Kahn's) approach below is cleaner and more intuitive for an interview.

---

### 💡 Idea Behind Optimisation — Kahn's BFS Algorithm

**Steps in plain English:**
1. **Build the graph** — adjacency list (who depends on whom) and an `indegree[]` array (how many prerequisites each course has).
2. **Seed the queue** — add all courses with `indegree == 0` (no prerequisites, safe to take immediately).
3. **Process the queue** — take a course, add it to the result, and for each course that depended on it, decrement their indegree. If any drops to 0, add it to the queue.
4. **Cycle check** — if the result has all `numCourses`, return it. Otherwise a cycle exists — return `[]`.

### 🎨 Visual — Kahn's BFS Walkthrough

```
numCourses=4, prerequisites=[[1,0],[2,0],[3,1],[3,2]]

Graph (b→a means b before a):
  0 → 1
  0 → 2
  1 → 3
  2 → 3

indegree: [0, 1, 1, 2]  ← courses 1,2 need 0; course 3 needs both 1 and 2

Queue: [0]   (only 0 has indegree 0)
Result: []

Step 1: poll 0 → result=[0]
        0's neighbors: 1, 2 → decrement their indegree
        indegree: [-, 0, 0, 2]
        Queue: [1, 2]

Step 2: poll 1 → result=[0,1]
        1's neighbor: 3 → indegree[3] = 1
        Queue: [2]

Step 3: poll 2 → result=[0,1,2]
        2's neighbor: 3 → indegree[3] = 0 → enqueue 3
        Queue: [3]

Step 4: poll 3 → result=[0,1,2,3]
        Queue: []

result.size() == 4 == numCourses → valid! Return [0,1,2,3]

KEY INVARIANT:
  A node enters the queue only when all its prerequisites are processed.
  If result.size() < numCourses at the end, a cycle prevented some nodes
  from ever reaching indegree 0.
```

---

### 🚀 Optimal Java Solution

```java
public int[] findOrder(int numCourses, int[][] prerequisites) {
    // Step 1: Build adjacency list and indegree array
    List<List<Integer>> adj = new ArrayList<>();
    int[] indegree = new int[numCourses];

    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }

    for (int[] pre : prerequisites) {
        int course = pre[0];
        int prereq = pre[1];
        // prereq must come before course
        adj.get(prereq).add(course);
        indegree[course]++;
    }

    // Step 2: Seed queue with all zero-indegree courses
    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++) {
        if (indegree[i] == 0) {
            queue.offer(i);
        }
    }

    // Step 3: BFS — process courses in topological order
    int[] result = new int[numCourses];
    int idx = 0;

    while (!queue.isEmpty()) {
        int course = queue.poll();
        result[idx++] = course;

        for (int next : adj.get(course)) {
            indegree[next]--;
            // All prerequisites of 'next' are now done — safe to take
            if (indegree[next] == 0) {
                queue.offer(next);
            }
        }
    }

    // Step 4: Cycle check — if we couldn't process all courses, cycle exists
    return idx == numCourses ? result : new int[0];
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(V + E) | Visit each node once, each edge once |
| **Space** | O(V + E) | Adjacency list + indegree array + queue |

---

### 🔁 Follow-Up Questions

**Q1: What if we only need to know if a valid ordering exists (not the actual order)?**
> That's LC #207 (Course Schedule). Same algorithm — just return `idx == numCourses` instead of the array.

**Q2: Can you solve this with DFS instead of BFS?**
> Yes. DFS-based topological sort uses three states per node: unvisited (0), in-stack (1), done (2). During DFS, if you visit an in-stack node, you've found a cycle. Post-order push to a stack gives reverse topological order.

**Q3: What if there are multiple valid orderings — how do you return the lexicographically smallest?**
> Replace the `LinkedList` queue with a `PriorityQueue` (min-heap). Same algorithm, but always processes the smallest available course next.

**Q4: How does this extend to real-world build systems (Maven, Gradle)?**
> Dependency resolution in build tools is exactly topological sort. A circular dependency (A depends on B, B depends on A) causes a build failure — same as returning `[]`.

---

---

## 4. Minimum Platforms / Meeting Rooms II — LC #253

**Difficulty:** Medium | **Pattern:** Sort + Two Pointers (Sweep Line)
**Confirmed in:** Described as *"subtle variation of minimum platforms question"* in Curefit reports

---

### 🎯 Problem Statement

Given arrival and departure times of trains at a station, find the **minimum number of platforms** needed so no train has to wait.

*(Meeting Rooms II variant: given intervals `[start, end]`, find the minimum number of meeting rooms required.)*

```
Example:
arrivals   = [900, 940, 950, 1100, 1500, 1800]
departures = [910, 1200, 1120, 1130, 1900, 2000]

Output: 3

At time 950: trains arrived at 900, 940, 950 are all present → need 3 platforms.
```

---

### 🧠 Discussion — How to Think About This

**Think of it as events on a timeline.** Every arrival adds 1 train to the station; every departure removes 1. The answer is the maximum number of trains simultaneously present.

At any point in time:
```
trains_at_station = (arrivals so far) - (departures so far)
```

The maximum value of this across all time points = minimum platforms needed.

---

### 🐌 Brute Force Approach

For every possible time point, count how many trains are currently present.

```java
// O(n²) — for each train, check overlap with every other train
public int minPlatforms(int[] arr, int[] dep) {
    int n = arr.length;
    int maxPlatforms = 0;

    for (int i = 0; i < n; i++) {
        int count = 1;
        for (int j = 0; j < n; j++) {
            if (i != j && arr[i] >= arr[j] && arr[i] <= dep[j]) {
                count++;
            }
        }
        maxPlatforms = Math.max(maxPlatforms, count);
    }

    return maxPlatforms;
}
```

**Why slow?** O(n²) — checking every pair.

---

### 💡 Idea Behind Optimisation

**Sort arrivals and departures independently.** Then use two pointers to scan through time:

- If the next event is an **arrival** (`arr[i] <= dep[j]`): a train just arrived, need one more platform. Increment `i`.
- If the next event is a **departure** (`dep[j] < arr[i]`): a train just left, free up a platform. Increment `j`.
- Track the running platform count and record the maximum.

### 🎨 Visual — Two Pointer Sweep

```
arrivals   = [900, 940, 950, 1100, 1500, 1800]  (sort)
departures = [910, 1120, 1130, 1200, 1900, 2000] (sort)

i=0, j=0, platforms=0
  arr[0]=900 <= dep[0]=910 → arrival first → platforms=1, i=1
  arr[1]=940 <= dep[0]=910? NO → dep[0]=910 departure → platforms=0, j=1
  Wait — 940 > 910 so departure happens first:

Let me redo with correct comparison:

Event stream (sorted):
900(A) 910(D) 940(A) 950(A) 1100(A) 1120(D) 1130(D) 1200(D) 1500(A) 1800(A) 1900(D) 2000(D)

Running count:
+1=1   -1=0   +1=1   +1=2   +1=3   -1=2   -1=1   -1=0   +1=1   +1=2   -1=1   -1=0

MAX = 3 ✅

KEY INVARIANT:
  Sort both arrays. Use two pointers to merge arrival/departure events.
  Arrival event → +1 platform needed.
  Departure event → -1 platform freed.
  Answer = peak count during the sweep.
```

---

### 🚀 Optimal Java Solution

```java
public int minPlatforms(int[] arr, int[] dep) {
    int n = arr.length;

    // Sort arrivals and departures independently
    Arrays.sort(arr);
    Arrays.sort(dep);

    int platforms = 0;
    int maxPlatforms = 0;
    int i = 0;
    int j = 0;

    while (i < n && j < n) {
        if (arr[i] <= dep[j]) {
            // A train arrives before the next one departs — need a platform
            platforms++;
            i++;
        } else {
            // A train departs — free up a platform
            platforms--;
            j++;
        }
        // Track peak simultaneously occupied platforms
        maxPlatforms = Math.max(maxPlatforms, platforms);
    }

    return maxPlatforms;
}
```

**Meeting Rooms II variant (intervals):**

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

    int rooms = 0;
    int maxRooms = 0;
    int i = 0;
    int j = 0;

    while (i < n) {
        if (starts[i] < ends[j]) {
            rooms++;
            i++;
        } else {
            rooms--;
            j++;
        }
        maxRooms = Math.max(maxRooms, rooms);
    }

    return maxRooms;
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(n log n) | Dominated by sorting |
| **Space** | O(1) | Only pointer variables (sorting in-place) |

---

### 🔁 Follow-Up Questions

**Q1: Can you solve this with a min-heap instead?**
> Yes. Sort intervals by start time. Use a min-heap of end times. For each new interval: if `start >= heap.peek()`, a room is freed (poll). Always push `end` to heap. Answer = `heap.size()` at the end. O(n log n) time, O(n) space.

**Q2: What is the difference between `arr[i] <= dep[j]` vs `arr[i] < dep[j]`?**
> `<=` means a train that arrives at the exact same time as another departs needs a new platform (trains are back-to-back, not overlapping). `<` would assume same-time arrival/departure shares a platform. Check the problem statement carefully — it matters.

**Q3: Variation — what if you need to return the actual intervals grouped per platform?**
> Use a min-heap of `(endTime, platformId)` pairs. Assign each interval to the platform that freed up earliest. This is interval graph coloring.

**Q4: What if each train has a weight/priority and you want to minimize total wait time?**
> This becomes a scheduling problem — sort by due date (EDF) or use priority queues weighted by urgency. Different problem class.

---

---

## 5. Subsets / Subset Sum — LC #78 / #416

**Difficulty:** Medium | **Pattern:** Recursion → Memoization → Bottom-Up DP
**Confirmed in:** Curefit explicitly tests the *evolution* — they will ask you to start with brute force and optimize live

---

### 🎯 Problem Statement

**LC #78 — Subsets:** Given an integer array `nums` of unique elements, return all possible subsets (the power set).

**LC #416 — Partition Equal Subset Sum:** Given an integer array `nums`, return `true` if you can partition it into two subsets with equal sum.

```
LC #78 Example:
nums = [1, 2, 3]
Output: [[], [1], [2], [1,2], [3], [1,3], [2,3], [1,2,3]]

LC #416 Example:
nums = [1, 5, 11, 5]
Output: true  → [1, 5, 5] and [11]  (both sum to 11)
```

> **Note:** Curefit specifically asks LC #416 style (can you form a subset with a target sum?) as a DP problem. The evolution they want to see: brute-force recursion → memoization → tabulation.

---

### 🧠 Discussion — How to Think About This (LC #416)

**Reframe:** Can any subset of `nums` sum to `total / 2`? (If total is odd, immediately return false.)

At each element you have a binary choice: **include it** or **exclude it**. This gives a recursion tree of depth `n`.

---

### 🐌 Brute Force — Pure Recursion O(2^n)

```java
// Brute Force — try all 2^n subsets
public boolean canPartition(int[] nums) {
    int total = 0;
    for (int num : nums) {
        total += num;
    }
    // Odd total → impossible to split equally
    if (total % 2 != 0) {
        return false;
    }
    return dfs(nums, 0, total / 2);
}

private boolean dfs(int[] nums, int index, int remaining) {
    // Base cases
    if (remaining == 0) {
        return true;
    }
    if (index >= nums.length || remaining < 0) {
        return false;
    }
    // Choice: include nums[index] OR exclude it
    return dfs(nums, index + 1, remaining - nums[index])
        || dfs(nums, index + 1, remaining);
}
```

**Problem:** Exponential — same `(index, remaining)` pairs computed repeatedly.

---

### 💡 Step 1 Optimisation — Memoization O(n × target)

Cache results for `(index, remaining)` pairs — each unique pair is computed only once.

```java
public boolean canPartition(int[] nums) {
    int total = 0;
    for (int num : nums) {
        total += num;
    }
    if (total % 2 != 0) {
        return false;
    }
    int target = total / 2;
    // memo[i][s] = can we reach sum s using nums[i..]?
    // -1 = unvisited, 0 = false, 1 = true
    int[][] memo = new int[nums.length][target + 1];
    for (int[] row : memo) {
        Arrays.fill(row, -1);
    }
    return dfs(nums, 0, target, memo);
}

private boolean dfs(int[] nums, int index, int remaining, int[][] memo) {
    if (remaining == 0) {
        return true;
    }
    if (index >= nums.length || remaining < 0) {
        return false;
    }
    if (memo[index][remaining] != -1) {
        return memo[index][remaining] == 1;
    }
    boolean result = dfs(nums, index + 1, remaining - nums[index], memo)
                  || dfs(nums, index + 1, remaining, memo);
    memo[index][remaining] = result ? 1 : 0;
    return result;
}
```

---

### 💡 Step 2 Optimisation — Bottom-Up DP O(n × target) time, O(target) space

**The key state:** `dp[s]` = can we form sum `s` using elements seen so far?

### 🎨 Visual — DP Table Evolution

```
nums = [1, 5, 11, 5], target = 11

dp = boolean array of size 12 (indices 0..11)
dp[0] = true always (empty subset sums to 0)

Start: dp = [T, F, F, F, F, F, F, F, F, F, F, F]

Process num=1:
  For s from 11 down to 1:
    dp[s] |= dp[s-1]
  dp = [T, T, F, F, F, F, F, F, F, F, F, F]

Process num=5:
  dp = [T, T, F, F, F, T, T, F, F, F, F, F]

Process num=11:
  dp = [T, T, F, F, F, T, T, F, F, F, F, T]  ← dp[11]=true!

return dp[11] = true ✅

KEY INVARIANT:
  Iterate s from HIGH to LOW to avoid using the same element twice
  (otherwise we'd allow multi-use of the same number — unbounded knapsack).
  Iterating high→low ensures each element is considered at most once.
```

---

### 🚀 Optimal Java Solution — Bottom-Up DP

```java
public boolean canPartition(int[] nums) {
    int total = 0;
    for (int num : nums) {
        total += num;
    }

    // Odd sum — can never split into two equal halves
    if (total % 2 != 0) {
        return false;
    }

    int target = total / 2;
    // dp[s] = true if we can form sum s from a subset of nums seen so far
    boolean[] dp = new boolean[target + 1];
    dp[0] = true;

    for (int num : nums) {
        // Iterate HIGH to LOW to prevent reusing same element
        for (int s = target; s >= num; s--) {
            // Either skip num (dp[s] stays) or include num (dp[s - num])
            dp[s] = dp[s] || dp[s - num];
        }
    }

    return dp[target];
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force Recursion | O(2^n) | O(n) stack |
| Memoization | O(n × target) | O(n × target) |
| **Bottom-Up DP (optimal)** | **O(n × target)** | **O(target)** |

---

### 🔁 Follow-Up Questions

**Q1: Why do we iterate `s` from high to low in the DP loop?**
> Iterating high→low ensures that when we compute `dp[s]`, `dp[s - num]` still reflects the state *before* including `num` in this round. Iterating low→high would mean `dp[s - num]` was already updated this round — effectively allowing `num` to be used multiple times (unbounded knapsack).

**Q2: What if elements can be repeated (duplicates in nums)?**
> Partition Equal Subset Sum allows duplicates by default — the DP handles it correctly since we process by value, not by index.

**Q3: How does this extend to "count the number of subsets with sum k"?**
> Change `boolean[] dp` to `int[] dp` where `dp[s]` = number of ways to form sum `s`. Change `||` to `+`. `dp[target]` gives the count.

**Q4: Unbounded knapsack variant — what if each element can be used unlimited times?**
> Iterate `s` from LOW to HIGH (instead of high to low). This allows reusing the same element.

---

---

## 6. Number of Islands — LC #200

**Difficulty:** Medium | **Pattern:** DFS / BFS on 2D Grid
**Confirmed in:** Core grid traversal pattern confirmed across all Curefit backend reports; custom connectivity variant also confirmed

---

### 🎯 Problem Statement

Given an `m × n` grid of `'1'` (land) and `'0'` (water), return the number of islands. An island is a group of adjacent (up/down/left/right) land cells surrounded by water.

```
Example:
grid = [
  ['1','1','0','0','0'],
  ['1','1','0','0','0'],
  ['0','0','1','0','0'],
  ['0','0','0','1','1']
]

Output: 3

Island 1: top-left 2×2 block
Island 2: center single cell
Island 3: bottom-right two cells
```

---

### 🧠 Discussion — How to Think About This

Each unvisited `'1'` cell is the start of a new island. From there, use DFS/BFS to explore all connected land cells and "sink" them (mark as visited) so they don't get counted again.

The number of times you trigger a fresh DFS/BFS = number of islands.

---

### 🐌 Brute Force — Naive Counting

Check every cell. For each unvisited `'1'`, BFS/DFS to mark its island. This is already O(m×n) — there's no "dumber" approach here. The optimization is about *style*: in-place marking vs. extra visited array.

---

### 💡 Idea — In-Place DFS (Flood Fill)

Instead of maintaining a separate `visited[][]` array, **modify the grid directly** by setting `'1' → '0'` as you visit cells. This sinks the island — visited cells become water so we never count them again.

### 🎨 Visual — DFS Flood Fill

```
Initial:                 After DFS from (0,0):
1 1 0 0 0               0 0 0 0 0
1 1 0 0 0     →         0 0 0 0 0
0 0 1 0 0               0 0 1 0 0
0 0 0 1 1               0 0 0 1 1

island count = 1 (for the top-left group)
Continue scanning → find '1' at (2,2) → DFS sinks it → count=2
Continue → find '1' at (3,3) → DFS sinks it and (3,4) → count=3

KEY INVARIANT:
  Before DFS: cell is '1' (land, unvisited).
  During DFS: set to '0' immediately on entry — prevents revisiting.
  After DFS: the entire connected island is gone. Count it once.
```

---

### 🚀 Optimal Java Solution

```java
public int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) {
        return 0;
    }

    int rows = grid.length;
    int cols = grid[0].length;
    int count = 0;

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                // Found unvisited land — this is a new island
                count++;
                // Sink the entire connected island via DFS
                dfs(grid, r, c, rows, cols);
            }
        }
    }

    return count;
}

private void dfs(char[][] grid, int r, int c, int rows, int cols) {
    // Boundary check OR already water — stop recursion
    if (r < 0 || r >= rows || c < 0 || c >= cols || grid[r][c] == '0') {
        return;
    }

    // Mark as visited by sinking this cell
    grid[r][c] = '0';

    // Explore all 4 directions
    dfs(grid, r + 1, c, rows, cols);
    dfs(grid, r - 1, c, rows, cols);
    dfs(grid, r, c + 1, rows, cols);
    dfs(grid, r, c - 1, rows, cols);
}
```

**BFS variant (iterative — avoids stack overflow on large grids):**

```java
private void bfs(char[][] grid, int r, int c, int rows, int cols) {
    Queue<int[]> queue = new LinkedList<>();
    queue.offer(new int[]{r, c});
    grid[r][c] = '0';

    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        for (int[] d : dirs) {
            int nr = cell[0] + d[0];
            int nc = cell[1] + d[1];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && grid[nr][nc] == '1') {
                grid[nr][nc] = '0';
                queue.offer(new int[]{nr, nc});
            }
        }
    }
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(m × n) | Each cell visited at most once |
| **Space** | O(m × n) | DFS stack depth (worst case — all land). BFS queue also O(m×n). |

---

### 🔁 Follow-Up Questions

**Q1: What if you cannot modify the input grid?**
> Use a `boolean[][] visited` array of the same size. Mark `visited[r][c] = true` instead of setting `grid[r][c] = '0'`.

**Q2: What if diagonal connections also count as connected (8-directional)?**
> Expand the directions array from 4 to 8:
> ```java
> int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
> ```

**Q3: Max Area of Island — LC #695. What changes?**
> Instead of incrementing a counter, accumulate and return the max DFS return value. Each DFS call returns `1 + dfs(neighbors)`.

**Q4: What if the grid is too large and DFS causes stack overflow?**
> Use BFS with an explicit queue (iterative). Avoids recursion stack depth issues.

**Q5: Curefit's custom variant — computers connected within range k?**
> This becomes a sliding window + BFS hybrid. For each unconnected computer, BFS within distance k. The Number of Islands skeleton is the same — only the connectivity rule changes.

---

---

## 7. LCA of BST / First Common Element in Two BSTs

**Difficulty:** Medium | **Pattern:** BST In-Order Traversal + Two-Pointer Merge
**Confirmed in:** Exact problem — *"given two BSTs, print the first common element or -1"* — confirmed in Curefit reports

---

### 🎯 Problem Statement

Given two Binary Search Trees, find the **first (smallest) element that appears in both BSTs**. Return `-1` if no such element exists.

```
BST 1:         BST 2:
    5               10
   / \             /  \
  3   8           2    15
 / \   \         / \
1   4   10      1   4

In-order BST 1: [1, 3, 4, 5, 8, 10]
In-order BST 2: [1, 2, 4, 10, 15]

Common elements: 1, 4, 10
First common = 1
```

---

### 🧠 Discussion — How to Think About This

**Key BST property:** In-order traversal of a BST yields elements in **sorted ascending order**.

So the problem reduces to:
1. Get sorted list from BST 1 via in-order traversal.
2. Get sorted list from BST 2 via in-order traversal.
3. Find the first common element in two sorted lists — classic two-pointer merge.

This is the merge step of merge-sort applied to two sorted sequences.

---

### 🐌 Brute Force Approach

Store all elements of BST 1 in a HashSet, then in-order traverse BST 2 and return the first element found in the set.

```java
// O(m + n) time, O(m) space — not bad, but misses the BST sorted-order insight
public int firstCommon(TreeNode root1, TreeNode root2) {
    Set<Integer> set = new HashSet<>();
    inorder1(root1, set);
    return inorder2(root2, set);
}

private void inorder1(TreeNode node, Set<Integer> set) {
    if (node == null) return;
    inorder1(node.left, set);
    set.add(node.val);
    inorder1(node.right, set);
}

private int inorder2(TreeNode node, Set<Integer> set) {
    if (node == null) return -1;
    // Check left subtree first (smaller values — we want minimum common)
    int left = inorder2(node.left, set);
    if (left != -1) return left;
    if (set.contains(node.val)) return node.val;
    return inorder2(node.right, set);
}
```

**Why not ideal?** Doesn't exploit the sorted property of both BSTs simultaneously.

---

### 💡 Idea Behind Optimisation — Two-Pointer on Sorted Streams

Since both in-order traversals produce sorted sequences, we can merge them with two pointers — exactly like merging two sorted arrays — without storing the full lists.

### 🎨 Visual — Two-Pointer Merge on Sorted In-Orders

```
In-order BST1: [1, 3, 4, 5, 8, 10]
In-order BST2: [1, 2, 4, 10, 15]

Pointer i on BST1, pointer j on BST2:

i=0(1), j=0(1): 1 == 1 → MATCH! Return 1 immediately ✅

If no match at 1:
i=0(1), j=1(2): 1 < 2 → advance i (BST1's turn)
i=1(3), j=1(2): 3 > 2 → advance j (BST2's turn)
i=1(3), j=2(4): 3 < 4 → advance i
i=2(4), j=2(4): 4 == 4 → MATCH! Return 4

KEY INVARIANT:
  Always advance the pointer with the smaller value.
  When both are equal, we found the smallest common element.
```

---

### 🚀 Optimal Java Solution

```java
// Using iterative in-order with stacks — O(m+n) time, O(h1+h2) space
public int findFirstCommon(TreeNode root1, TreeNode root2) {
    Deque<TreeNode> stack1 = new ArrayDeque<>();
    Deque<TreeNode> stack2 = new ArrayDeque<>();

    TreeNode curr1 = root1;
    TreeNode curr2 = root2;

    // Push leftmost path for both trees
    while (curr1 != null) {
        stack1.push(curr1);
        curr1 = curr1.left;
    }
    while (curr2 != null) {
        stack2.push(curr2);
        curr2 = curr2.left;
    }

    // Two-pointer merge on lazy in-order streams
    while (!stack1.isEmpty() && !stack2.isEmpty()) {
        int val1 = stack1.peek().val;
        int val2 = stack2.peek().val;

        if (val1 == val2) {
            // Found the first common element
            return val1;
        } else if (val1 < val2) {
            // Advance BST1's in-order iterator
            TreeNode node = stack1.pop();
            curr1 = node.right;
            while (curr1 != null) {
                stack1.push(curr1);
                curr1 = curr1.left;
            }
        } else {
            // Advance BST2's in-order iterator
            TreeNode node = stack2.pop();
            curr2 = node.right;
            while (curr2 != null) {
                stack2.push(curr2);
                curr2 = curr2.left;
            }
        }
    }

    return -1;
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(m + n) | Each node visited at most once across both trees |
| **Space** | O(h1 + h2) | Stack depth = height of each tree (O(log n) for balanced) |

---

### 🔁 Follow-Up Questions

**Q1: What if you need ALL common elements, not just the first?**
> Don't return on first match — collect all matches in a list. Continue until one stack is empty.

**Q2: What if the trees are regular Binary Trees (not BSTs)?**
> In-order traversal won't be sorted. Fall back to the HashSet approach (store all of tree 1, scan tree 2). Or use intersection of DFS traversal sets.

**Q3: Can you do this without any extra space (O(1))?**
> Use Morris In-Order Traversal on both trees simultaneously. Extremely complex to implement correctly in an interview — mention it as a theoretical optimisation, not a live coding path.

---

---

## 8. Longest Univalue Path in Binary Tree — LC #687

**Difficulty:** Medium | **Pattern:** DFS with global max tracking
**Confirmed in:** Exact problem — *"longest path where each node has same value, path may not pass through root"* — confirmed in Curefit reports

---

### 🎯 Problem Statement

Given the root of a binary tree, return the length of the longest path where each node in the path has the **same value**. The path does not need to pass through the root, and length is measured in **edges** (not nodes).

```
Example:
        5
       / \
      4   5
     / \   \
    1   1   5

Output: 2
Longest univalue path: 5 → 5 → 5 (right side), length = 2 edges
```

---

### 🧠 Discussion — How to Think About This

**This is a tree path problem.** At each node, a univalue path can extend through the left child, the right child, or both (making a V-shape).

Key insight: The function we write returns the **longest single arm** extending from a node downward (because we can only return one direction up to the parent). But at each node we can compute the **full path through that node** (left arm + right arm) and update a global maximum.

---

### 🐌 Brute Force Approach

For every node as a potential path center, DFS outward counting equal-value steps in all directions. O(n²) — redundant computation at each node.

---

### 💡 Idea Behind Optimisation

Single DFS pass. At each node:
1. Compute the longest univalue arm going **left** (only if child value matches current).
2. Compute the longest univalue arm going **right** (only if child value matches current).
3. Update global max with `leftArm + rightArm` (full path through this node).
4. Return `max(leftArm, rightArm)` to the parent (can only extend one direction up).

### 🎨 Visual — DFS Arm Computation

```
        5
       / \
      4   5
     / \   \
    1   1   5

DFS post-order (process children before current node):

At leaf 1 (left of 4): returns 0 (leaf has arm length 0)
At leaf 1 (right of 4): returns 0
At node 4: left child val=1 ≠ 4 → leftArm=0
           right child val=1 ≠ 4 → rightArm=0
           globalMax = max(0, 0+0) = 0
           returns 0 to parent

At leaf 5 (right of right-5): returns 0
At node 5 (right child of root):
           left child = null → leftArm=0
           right child val=5 == 5 → rightArm = 0+1 = 1
           globalMax = max(0, 0+1) = 1
           returns 1 to parent

At root 5: left child val=4 ≠ 5 → leftArm=0
           right child val=5 == 5 → rightArm = 1+1 = 2
           globalMax = max(1, 0+2) = 2 ✅
           returns 2 to parent (irrelevant — root)

KEY INVARIANT:
  The returned arm length = how far you can extend in ONE direction with same value.
  The global max captures the best two-direction path through any node.
  These are two different quantities — don't confuse them.
```

---

### 🚀 Optimal Java Solution

```java
private int globalMax = 0;

public int longestUnivaluePath(TreeNode root) {
    globalMax = 0;
    dfs(root);
    return globalMax;
}

private int dfs(TreeNode node) {
    if (node == null) {
        return 0;
    }

    // Get the longest univalue arm from each child
    int leftArm = dfs(node.left);
    int rightArm = dfs(node.right);

    // Only extend arm if child has the same value as current node
    int extendLeft = (node.left != null && node.left.val == node.val)
                     ? leftArm + 1
                     : 0;
    int extendRight = (node.right != null && node.right.val == node.val)
                      ? rightArm + 1
                      : 0;

    // Full path through this node (V-shape) — update global max
    globalMax = Math.max(globalMax, extendLeft + extendRight);

    // Return only the longer single arm to the parent
    return Math.max(extendLeft, extendRight);
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(n) | Each node visited exactly once |
| **Space** | O(h) | Recursion stack depth = tree height |

---

### 🔁 Follow-Up Questions

**Q1: What if the path must pass through the root?**
> Then `dfs(root)` return value (single arm from root) IS the answer — no need for a global max.

**Q2: What if "same value" is replaced by "values within k of each other"?**
> Change the value equality check to `Math.abs(node.val - node.left.val) <= k`. Same DFS structure.

**Q3: How is this different from Binary Tree Maximum Path Sum (LC #124)?**
> LC #124 maximizes the sum of node values along any path. Here we count edges where adjacent values match. The DFS structure is identical — only the "what we track" changes (count of matching edges vs. sum of values).

---

---

## 9. Jump Game II — LC #45

**Difficulty:** Medium | **Pattern:** Greedy BFS
**Confirmed in:** High frequency at product companies for SDE 2/3; greedy-meets-DP crossover that Curefit tests

---

### 🎯 Problem Statement

Given an array `nums` where `nums[i]` is the maximum jump length from position `i`, return the **minimum number of jumps** to reach the last index. You can always reach the last index.

```
Example:
nums = [2, 3, 1, 1, 4]
Output: 2

Jump 1: From index 0 (max jump 2) → jump to index 1
Jump 2: From index 1 (max jump 3) → jump to index 4 (last)
```

---

### 🧠 Discussion — How to Think About This

**Think in terms of "levels" — like BFS on a graph.** Each "level" represents positions reachable in exactly `k` jumps. The answer is the number of levels you need.

At each position `i`, you can reach positions `i+1` to `i+nums[i]`. The current level ends at `curEnd`. The farthest you can reach from any position in the current level is `farthest`. When you exhaust the current level (`i == curEnd`), you take a jump and the next level extends to `farthest`.

---

### 🐌 Brute Force — DP O(n²)

```java
// dp[i] = minimum jumps to reach index i
public int jump(int[] nums) {
    int n = nums.length;
    int[] dp = new int[n];
    Arrays.fill(dp, Integer.MAX_VALUE);
    dp[0] = 0;

    for (int i = 0; i < n; i++) {
        if (dp[i] == Integer.MAX_VALUE) continue;
        for (int j = 1; j <= nums[i] && i + j < n; j++) {
            dp[i + j] = Math.min(dp[i + j], dp[i] + 1);
        }
    }

    return dp[n - 1];
}
```

O(n²) time — inner loop can be up to n steps per position.

---

### 💡 Idea Behind Optimisation — Greedy Level Sweep

We don't need to track each position individually. We only need:
- `curEnd`: the boundary of the current "BFS level" (reachable in current jumps).
- `farthest`: the farthest position reachable from any index in the current level.
- `jumps`: increment when we cross `curEnd`.

### 🎨 Visual — Greedy Level Sweep

```
nums = [2, 3, 1, 1, 4]
idx:    0  1  2  3  4

Level 0 (0 jumps): can reach index 0
  curEnd=0, farthest=0

Scan i=0: farthest = max(0, 0+2) = 2
  i==curEnd(0) → take jump! jumps=1, curEnd=2

Level 1 (1 jump): can reach indices 1, 2
Scan i=1: farthest = max(2, 1+3) = 4
Scan i=2: farthest = max(4, 2+1) = 4
  i==curEnd(2) → take jump! jumps=2, curEnd=4

Level 2 (2 jumps): curEnd=4 = last index → done!

Answer: 2 jumps ✅

KEY INVARIANT:
  curEnd marks where the current wave ends.
  farthest is the leading edge of the next wave.
  When the scan reaches curEnd, commit to a jump and advance the wave.
```

---

### 🚀 Optimal Java Solution

```java
public int jump(int[] nums) {
    int n = nums.length;
    int jumps = 0;
    int curEnd = 0;
    int farthest = 0;

    // Don't need to process the last index — we just need to reach it
    for (int i = 0; i < n - 1; i++) {
        // Track the farthest we can reach from any position in current level
        farthest = Math.max(farthest, i + nums[i]);

        // Reached the boundary of current level — must jump
        if (i == curEnd) {
            jumps++;
            curEnd = farthest;

            // Early exit if we've already reached or passed the last index
            if (curEnd >= n - 1) {
                break;
            }
        }
    }

    return jumps;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| DP Brute Force | O(n²) | O(n) |
| **Greedy (optimal)** | **O(n)** | **O(1)** |

---

### 🔁 Follow-Up Questions

**Q1: How is this different from Jump Game I (LC #55)?**
> LC #55 only asks *can* you reach the end (boolean). Greedy: track `maxReach`; if `i > maxReach` at any point, return false. No jump counting needed.

**Q2: What if you want to return the actual jump sequence (which indices you jumped to)?**
> Track a `parent[]` array during the DP approach. After computing `dp[]`, backtrack from `n-1` to `0` following the minimum-cost path.

**Q3: What if jump lengths are negative (you can also move backward)?**
> Greedy breaks down. Need BFS or DP with a visited set to avoid infinite loops. The problem becomes a shortest path problem on a directed graph.

---

---

## 10. Word Break — LC #139

**Difficulty:** Medium | **Pattern:** DP + HashSet
**Confirmed in:** String DP — high frequency at product companies for SDE 3 backend; tests DP state definition under live pressure

---

### 🎯 Problem Statement

Given a string `s` and a dictionary of strings `wordDict`, return `true` if `s` can be segmented into one or more space-separated words from `wordDict`.

```
Example 1:
s = "leetcode", wordDict = ["leet", "code"]
Output: true  → "leet" + "code"

Example 2:
s = "applepenapple", wordDict = ["apple", "pen"]
Output: true  → "apple" + "pen" + "apple"

Example 3:
s = "catsandog", wordDict = ["cats", "dog", "sand", "and", "cat"]
Output: false
```

---

### 🧠 Discussion — How to Think About This

**Reframe:** Can we find a sequence of "cuts" in `s` such that every segment is a valid dictionary word?

Try every possible last word. If `s[j..i]` is a valid word AND `s[0..j-1]` can itself be segmented (a sub-problem), then `s[0..i]` can be segmented.

This recursive structure with overlapping subproblems → DP.

---

### 🐌 Brute Force — Recursion O(2^n)

```java
// Try every possible first word, recurse on the rest
public boolean wordBreak(String s, List<String> wordDict) {
    Set<String> dict = new HashSet<>(wordDict);
    return dfs(s, 0, dict);
}

private boolean dfs(String s, int start, Set<String> dict) {
    if (start == s.length()) {
        return true;
    }
    for (int end = start + 1; end <= s.length(); end++) {
        if (dict.contains(s.substring(start, end)) && dfs(s, end, dict)) {
            return true;
        }
    }
    return false;
}
```

Exponential — same `start` index re-computed many times.

---

### 💡 Idea Behind Optimisation — Bottom-Up DP

**State:** `dp[i]` = can the substring `s[0..i-1]` (first `i` characters) be segmented?

**Transition:** `dp[i] = true` if there exists some `j < i` where:
- `dp[j] == true` (first j chars are valid), AND
- `s.substring(j, i)` is in the dictionary.

**Base case:** `dp[0] = true` (empty string is always valid — zero words).

### 🎨 Visual — DP Table for "leetcode"

```
s = "leetcode", dict = {"leet", "code"}

dp = [T, F, F, F, F, F, F, F, F]   (indices 0..8)
      ↑
      dp[0]=true (empty string)

i=1: check j=0→s[0..0]="l" → not in dict. dp[1]=F
i=2: check j=0→"le", j=1→"e" → neither in dict. dp[2]=F
i=3: check j=0→"lee", ... → none. dp[3]=F
i=4: check j=0→"leet" → IN DICT and dp[0]=T → dp[4]=T ✅
i=5: check j=0→"leetc", j=4→"c" (dp[4]=T but "c" not dict) → dp[5]=F
i=6: check j=4→"co" → not in dict. dp[6]=F
i=7: check j=4→"cod" → no. dp[7]=F
i=8: check j=4→"code" → IN DICT and dp[4]=T → dp[8]=T ✅

return dp[8] = true ✅

KEY INVARIANT:
  dp[i]=true means "the first i characters form valid words."
  We build from left to right, each position depending only on earlier ones.
```

---

### 🚀 Optimal Java Solution

```java
public boolean wordBreak(String s, List<String> wordDict) {
    Set<String> dict = new HashSet<>(wordDict);
    int n = s.length();

    // dp[i] = true if s[0..i-1] can be segmented using dict words
    boolean[] dp = new boolean[n + 1];
    dp[0] = true;

    for (int i = 1; i <= n; i++) {
        for (int j = 0; j < i; j++) {
            // If first j chars are valid AND s[j..i-1] is a dict word
            if (dp[j] && dict.contains(s.substring(j, i))) {
                dp[i] = true;
                break;
            }
        }
    }

    return dp[n];
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force Recursion | O(2^n) | O(n) stack |
| Memoization | O(n²) | O(n) |
| **Bottom-Up DP (optimal)** | **O(n²)** | **O(n)** |

> `substring()` in Java is O(k) where k = length of substring — technically makes this O(n³) worst case. Mention this to the interviewer: optimize by checking word lengths or using a Trie.

---

### 🔁 Follow-Up Questions

**Q1: Word Break II (LC #140) — return all possible segmentations.**
> Change `boolean[] dp` to `List<String>[] dp` where each entry stores all valid last words ending at position `i`. Backtrack using these lists to build actual sentences. Complexity: O(n³ + output size).

**Q2: How can you optimize the inner loop?**
> Instead of checking all `j < i`, only check `j = i - maxWordLen` to `i`. If no word in the dictionary is longer than `maxWordLen`, you skip impossible splits. Reduces inner loop from O(n) to O(maxWordLen).

**Q3: What if the dictionary can have infinite words (generated by some grammar)?**
> This becomes language membership in formal language theory — potentially undecidable depending on the grammar type (context-free → CYK algorithm, O(n³)).

**Q4: Why not use a Trie instead of HashSet?**
> Trie avoids repeated `substring()` allocations. Build a Trie from the dictionary, then during the DP inner loop, walk the Trie character by character from position `j`. When you hit a word-end node, that's a valid split point. Keeps it O(n²) without the hidden O(k) substring cost.

---

---

# ⚠️ Bonus Section — Actual Round 1 Questions

> These two patterns were **NOT in the original top 10** but were **actually asked in Round 1 (Jul 2026)**.
> Curefit uses custom array-manipulation variants — not standard LeetCode problems.
> The pattern family: **Gap scanning on sorted/indexed arrays** + **Sliding window min-of-max**.
> Master these before Round 2.

---

## 11. Maximize Distance to Closest Person — LC #849

**Difficulty:** Medium | **Pattern:** Gap Scanning (Greedy)
**Asked in:** Round 1 Q1 — exact problem. Phrasing: *"find the seat with maximum distance to nearest person"*

---

### 🎯 Problem Statement

An array `seats` where `seats[i] = 1` means a person is seated, `seats[i] = 0` means empty. Find the **index** of the empty seat that maximizes the minimum distance to the nearest occupied seat. Return the first such seat on ties.

```
Example:
seats = [1, 0, 0, 0, 1, 0, 1]
Output: 2

At index 2: nearest person is at 0 (dist 2) and 4 (dist 2) → min = 2
At index 1: min(1, 3) = 1
At index 3: min(3, 1) = 1
At index 5: min(1, 1) = 1
→ index 2 has the maximum min-distance of 2 ✅
```

---

### 🧠 Discussion — How to Think About This

Empty seats sit inside **gaps** between occupied seats (or between an edge and the first/last person). The best seat in any gap is at the **midpoint** of that gap.

Three types of gaps:
1. **Left edge gap** — from index 0 to first person. Best seat = index 0, distance = first person's index.
2. **Middle gap** — between two adjacent people at `L` and `R`. Best seat = midpoint, distance = `(R - L) / 2`.
3. **Right edge gap** — from last person to end. Best seat = last index, distance = `n - 1 - lastPerson`.

---

### 🐌 Brute Force — O(n²)

For each empty seat, scan left and right to find the nearest person.

```java
public int maxDistToClosest(int[] seats) {
    int n = seats.length;
    int maxDist = 0;
    int bestSeat = -1;

    for (int i = 0; i < n; i++) {
        if (seats[i] == 0) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MAX_VALUE;
            // Scan left
            for (int l = i - 1; l >= 0; l--) {
                if (seats[l] == 1) {
                    left = i - l;
                    break;
                }
            }
            // Scan right
            for (int r = i + 1; r < n; r++) {
                if (seats[r] == 1) {
                    right = r - i;
                    break;
                }
            }
            int dist = Math.min(left, right);
            if (dist > maxDist) {
                maxDist = dist;
                bestSeat = i;
            }
        }
    }
    return bestSeat;
}
```

---

### 💡 Idea Behind Optimisation — Gap Scanning O(n) / O(1)

Scan once, track `lastPerson`. When you hit a new person:
- If `lastPerson == -1`: left edge gap of size `i` → bestSeat = 0.
- Else: middle gap → midpoint = `lastPerson + (i - lastPerson) / 2`, distance = `(i - lastPerson) / 2`.

After the loop: right edge gap = `n - 1 - lastPerson`.

### 🎨 Visual — Gap Types

```
seats = [1, 0, 0, 0, 1, 0, 1]
         ↑              ↑     ↑
         L              M     R

Left edge gap:  none (first person at index 0)
Middle gap 1:   L=0, R=4 → dist=(4-0)/2=2, midSeat=0+2=2 ← WINNER
Middle gap 2:   L=4, R=6 → dist=(6-4)/2=1, midSeat=4+1=5
Right edge:     lastPerson=6, right=7-1-6=0 → no gain

KEY INVARIANT:
  For middle gap [L..R]: best seat is floor midpoint = L + (R-L)/2.
  For left edge gap [0..R]: best seat is 0, distance = R.
  For right edge gap [L..n-1]: best seat is n-1, distance = n-1-L.
  Process all three; track max distance seen.
```

---

### 🚀 Optimal Java Solution

```java
public int maxDistToClosest(int[] seats) {
    int n = seats.length;
    int maxDist = 0;
    int bestSeat = -1;
    int lastPerson = -1;

    for (int i = 0; i < n; i++) {
        if (seats[i] == 1) {
            if (lastPerson == -1) {
                // Left edge gap — best seat is index 0
                if (i > maxDist) {
                    maxDist = i;
                    bestSeat = 0;
                }
            } else {
                // Middle gap between two people
                int halfGap = (i - lastPerson) / 2;
                int midSeat = lastPerson + halfGap;
                if (halfGap > maxDist) {
                    maxDist = halfGap;
                    bestSeat = midSeat;
                }
            }
            lastPerson = i;
        }
    }

    // Right edge gap — process AFTER the loop (lastPerson is now final)
    int rightGap = n - 1 - lastPerson;
    if (rightGap > maxDist) {
        bestSeat = n - 1;
    }

    return bestSeat;
}
```

> ⚠️ **Bug Kapil made in Round 1:** Right edge check was placed INSIDE the for loop.
> At i=0 (first person), `lastPerson` becomes 0, so `rightGap = n-1-0 = n-1` — massively wrong.
> It must be OUTSIDE the loop — only then does `lastPerson` hold the true last person's index.

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force | O(n²) | O(1) |
| **Gap Scanning (optimal)** | **O(n)** | **O(1)** |

---

### 🔁 Follow-Up Questions

**Q1: What if there are no empty seats?**
> Return -1. Guard: `if (!Arrays.stream(seats).anyMatch(s -> s == 0)) return -1;`

**Q2: What if there's only one person?**
> Both edge cases activate. Left edge from 0 to person, right edge from person to n-1. Return whichever end is farther.

**Q3: What if we want the distance value, not the seat index?**
> Track `maxDist` and return it instead of `bestSeat`.

**Q4: Two-pass prefix-suffix variant — explain it.**
> `left[i]` = distance to nearest person on the left (infinity if none).
> `right[i]` = distance to nearest person on the right (infinity if none).
> `answer = argmax of min(left[i], right[i])` over all empty seats.
> O(n) time, O(n) space — easier to code, good to mention as alternative.

---

---

## 12. Minimize Max Gap After K Removals — Custom

**Difficulty:** Medium-Hard | **Pattern:** Sliding Window Min-of-Max on Diff Array
**Asked in:** Round 1 Q2 — exact problem (gems variant). Phrasing: *"remove K gems from sorted array, minimize largest gap between adjacent remaining gems"*

---

### 🎯 Problem Statement

Given a sorted array `arr` of N elements, remove exactly K elements to minimize the **maximum gap** between any two adjacent remaining elements.

```
Example:
arr = [12, 16, 22, 31, 31, 38], K = 3
Output: 6

Keep 3 gems: [12, 16, 22] → gaps = [4, 6] → max = 6
All other windows of 3 give a larger max gap.
```

---

### 🧠 Discussion — How to Think About This

**Key insight 1 — Diff array:** Gaps between adjacent kept elements are what matter. Compute `diffs[i] = arr[i+1] - arr[i]` for all consecutive pairs.

**Key insight 2 — Contiguous window:** Since the array is sorted, the optimal N-K elements to keep are always a **contiguous subarray**. Why? Skipping any interior element in a sorted array can only widen the gap between its neighbors — never reduce it. So we never benefit from non-contiguous selection.

**Key insight 3 — Sliding window:** We need to find the window of size `N-K` in `arr` (equivalently, a window of `N-K-1` diffs) whose **maximum diff is smallest**.

```
keep = N - K = 6 - 3 = 3
windowSize on diffs = keep - 1 = 2

diffs = [4, 6, 9, 0, 7]

Windows of size 2:
  [4,6]  → max=6   ← corresponds to arr[0..2] = [12,16,22]
  [6,9]  → max=9
  [9,0]  → max=9
  [0,7]  → max=7

Min of maxes = 6 ✅
```

---

### 🐌 Brute Force — Try All C(N, N-K) Subsets

```java
// O(C(N,K) * N) — exponential for large K
// Enumerate all subsets of size N-K, compute max gap, track minimum
// Only mention this — don't code it
```

---

### 💡 Idea Behind Optimisation — Sliding Window O(N × K)

1. Compute diffs array (size N-1).
2. Slide a window of size `keep-1 = N-K-1` over diffs.
3. For each window, compute the max diff.
4. Return the minimum across all window maxes.

### 🎨 Visual — Sliding Window on Diffs

```
arr   = [12, 16, 22, 31, 31, 38]
         ↑────────────↑           window 1: [12,16,22], keep=3
              ↑────────────↑      window 2: [16,22,31]
                   ↑────────────↑ window 3: [22,31,31]
                        ↑────────────↑ window 4: [31,31,38]

diffs = [ 4,   6,   9,   0,   7 ]
        [← w1 →]
             [← w2 →]
                  [← w3 →]
                       [← w4 →]

Window 1 max = max(4,6) = 6   ← minimum!
Window 2 max = max(6,9) = 9
Window 3 max = max(9,0) = 9
Window 4 max = max(0,7) = 7

Answer = 6 ✅

KEY INVARIANT:
  Window of N-K consecutive elements in arr
  = window of N-K-1 consecutive diffs.
  Best window = one with smallest max diff.
```

---

### 🚀 Optimal Java Solution

```java
public static int minimizeMaxGap(int[] arr, int k) {
    int n = arr.length;
    int keep = n - k;

    // Step 1: Compute consecutive diffs of the sorted array
    int[] diffs = new int[n - 1];
    for (int i = 0; i < n - 1; i++) {
        diffs[i] = arr[i + 1] - arr[i];
    }

    // Step 2: Sliding window of size (keep-1) over diffs
    // Find the window with the smallest maximum diff
    int windowSize = keep - 1;
    int minMaxGap = Integer.MAX_VALUE;

    for (int i = 0; i <= diffs.length - windowSize; i++) {
        int windowMax = 0;
        for (int j = i; j < i + windowSize; j++) {
            windowMax = Math.max(windowMax, diffs[j]);
        }
        minMaxGap = Math.min(minMaxGap, windowMax);
    }

    return minMaxGap;
}
```

**O(N) optimal — Sliding Window Max with Monotonic Deque (mention as follow-up):**

```java
public static int minimizeMaxGapOptimal(int[] arr, int k) {
    int n = arr.length;
    int keep = n - k;
    int windowSize = keep - 1;

    int[] diffs = new int[n - 1];
    for (int i = 0; i < n - 1; i++) {
        diffs[i] = arr[i + 1] - arr[i];
    }

    // Monotonic deque — front always holds index of max in current window
    Deque<Integer> deque = new ArrayDeque<>();
    int minMaxGap = Integer.MAX_VALUE;

    for (int i = 0; i < diffs.length; i++) {
        // Remove indices that are out of the current window
        while (!deque.isEmpty() && deque.peekFirst() < i - windowSize + 1) {
            deque.pollFirst();
        }
        // Maintain decreasing order — remove smaller elements from back
        while (!deque.isEmpty() && diffs[deque.peekLast()] <= diffs[i]) {
            deque.pollLast();
        }
        deque.offerLast(i);

        // Window is full — record the max (front of deque)
        if (i >= windowSize - 1) {
            minMaxGap = Math.min(minMaxGap, diffs[deque.peekFirst()]);
        }
    }

    return minMaxGap;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force (all subsets) | O(C(N,K) × N) | O(N) |
| Sliding Window (simple) | O(N × K) | O(N) |
| **Monotonic Deque (optimal)** | **O(N)** | **O(N)** |

---

### 🔁 Follow-Up Questions

**Q1: Prove that the optimal selection is always a contiguous window.**
> In a sorted array, suppose we select non-contiguous elements — i.e., we skip index `m` between kept elements at `i` and `j` (i < m < j). The gap is `arr[j] - arr[i]`.
> If instead we kept `arr[m]` and dropped either `i` or `j`, our gaps would be `arr[m]-arr[i]` and `arr[j]-arr[m]`, both ≤ `arr[j]-arr[i]` (since array is sorted).
> So contiguous is always at least as good. ✅

**Q2: What if K ≥ N-2? (Edge case — the problem says K < N-2 but worth knowing)**
> If we keep only 2 elements, we pick the pair with minimum difference — just the minimum adjacent diff in the entire array.

**Q3: What if the array is NOT sorted?**
> Sort it first (O(N log N)), then apply the same approach. The contiguous window insight only holds for sorted arrays.

**Q4: Binary Search variant — how would you solve this with binary search?**
> Binary search on the answer `g` (max gap). For each `g`, check: can we keep N-K elements such that all consecutive gaps ≤ g? Greedy check: scan the diffs, count the longest run of consecutive diffs all ≤ g. If that run length ≥ N-K-1, feasible. O(N log(maxGap)) time.

---

---

## 13. Magnetic Force Between Two Balls — LC #1552

**Difficulty:** Medium | **Pattern:** Binary Search on Answer + Greedy Check
**Pattern family:** Same as Q2 — "minimize/maximize a gap value" but solved with binary search instead of sliding window

---

### 🎯 Problem Statement

Place `m` balls in `n` baskets (positions given in an array). Maximize the **minimum** magnetic force (distance) between any two balls.

```
Example:
position = [1, 2, 3, 4, 7], m = 3
Output: 3

Place balls at positions 1, 4, 7 → gaps: 3, 3 → minimum = 3
```

---

### 🧠 Discussion

**Why binary search?** The answer (minimum gap) lies in range `[1, max(position) - min(position)]`. For any candidate gap `g`:
- **Feasibility check:** Can we place `m` balls such that every pair is at least `g` apart?
- **Greedy:** Sort positions. Place first ball at `position[0]`. For each subsequent position, place a ball only if it's at least `g` away from the last placed ball. Count how many balls we can place.
- If count ≥ m → gap `g` is feasible → try larger.
- If count < m → gap `g` too large → try smaller.

This is a classic **binary search on the answer** pattern.

---

### 🚀 Java Solution

```java
public int maxDistance(int[] position, int m) {
    Arrays.sort(position);
    int lo = 1;
    int hi = position[position.length - 1] - position[0];
    int result = 1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (canPlace(position, m, mid)) {
            // mid is feasible — try larger gap
            result = mid;
            lo = mid + 1;
        } else {
            // mid too large — reduce
            hi = mid - 1;
        }
    }

    return result;
}

private boolean canPlace(int[] pos, int m, int minGap) {
    int count = 1;
    int lastPlaced = pos[0];

    for (int i = 1; i < pos.length; i++) {
        if (pos[i] - lastPlaced >= minGap) {
            count++;
            lastPlaced = pos[i];
            if (count == m) {
                return true;
            }
        }
    }

    return count >= m;
}
```

---

### ⏱️ Complexity

| | Value |
|---|---|
| **Time** | O(N log N + N log D) where D = max position range |
| **Space** | O(1) |

---

### 🔁 Follow-Up

**Q: How does this relate to the Gems Q2 problem?**
> Q2 (Gems): minimize the maximum gap → sliding window approach works because we keep a fixed count of elements.
> LC #1552: maximize the minimum gap → binary search on answer works because we're optimizing a threshold.
> Both are "optimize a gap value" problems. When count of kept elements is fixed → sliding window. When you need to binary search a threshold → binary search on answer + greedy check.

---

---

## 14. Split Array Largest Sum — LC #410

**Difficulty:** Hard | **Pattern:** Binary Search on Answer + Greedy Check
**Pattern family:** Same binary search on answer — directly related to Q2 and LC #1552

---

### 🎯 Problem Statement

Split array `nums` into `k` non-empty subarrays to **minimize the largest subarray sum**.

```
Example:
nums = [7, 2, 5, 10, 8], k = 2
Output: 18

Split: [7,2,5] and [10,8] → sums 14 and 18 → max = 18
Other splits give larger max.
```

---

### 🧠 Discussion

Same binary search template as LC #1552:
- Binary search on the answer `g` (maximum allowed subarray sum).
- **Feasibility check:** Can we split into ≤ k subarrays each with sum ≤ g?
- **Greedy:** Greedily fill each subarray until adding next element would exceed `g`. Count subarrays needed.

---

### 🚀 Java Solution

```java
public int splitArray(int[] nums, int k) {
    int lo = Arrays.stream(nums).max().getAsInt();
    int hi = Arrays.stream(nums).sum();
    int result = hi;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (canSplit(nums, k, mid)) {
            result = mid;
            hi = mid - 1;
        } else {
            lo = mid + 1;
        }
    }

    return result;
}

private boolean canSplit(int[] nums, int k, int maxSum) {
    int subarrays = 1;
    int currentSum = 0;

    for (int num : nums) {
        if (currentSum + num > maxSum) {
            // Start a new subarray
            subarrays++;
            currentSum = num;
            if (subarrays > k) {
                return false;
            }
        } else {
            currentSum += num;
        }
    }

    return true;
}
```

---

### ⏱️ Complexity

| | Value |
|---|---|
| **Time** | O(N log(sum)) |
| **Space** | O(1) |

---

### 🔁 The Pattern Family — One Template, Many Problems

```
Binary Search on Answer template:

lo = minimum possible answer
hi = maximum possible answer

while (lo <= hi):
    mid = lo + (hi - lo) / 2
    if feasible(mid):
        result = mid
        move boundary toward better answer
    else:
        move boundary away

feasible(mid) = greedy check in O(N)
```

| Problem | Binary Search on | Feasible if |
|---|---|---|
| LC #1552 Magnetic Force | minimum gap | can place m balls with gap ≥ mid |
| LC #410 Split Array | maximum subarray sum | can split into ≤ k parts with sum ≤ mid |
| Gems Q2 (Round 1) | maximum gap | can keep N-K gems with gap ≤ mid |
| LC #875 Koko Eating | eating speed | can eat all bananas in h hours at speed mid |

> **Lesson learned the hard way (Jul 2026):** Curefit's Q2 (Gems) belongs to this binary search family. The sliding window approach also works but binary search on answer is the more generalizable pattern — and the one an interviewer expects you to reach for at SDE 3 level.

---
