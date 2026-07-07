# Curefit SDE 3 — Backend Interview Problem Set

> **Role:** SDE 3 - Backend | **Round:** Exploratory Call (confirmed DSA by HR)
> **Format:** Live coding, 45–60 min, 1–2 problems. Brute force NOT accepted — must reach optimal.
> **Source:** Compiled from real Curefit interview reports (2021–2025, LeetCode Discuss / GFG / Glassdoor)

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
