# Rotting Oranges

> **LeetCode:** [994. Rotting Oranges](https://leetcode.com/problems/rotting-oranges/) — Medium
> **Pattern:** Multi-source level-order BFS (see `../Reference/bfs-dfs-templates-reference.md`)
> **Uses:** `ArrayDeque<int[]>`, in-place grid mutation, direction array, fresh-count termination

---

## 📌 Problem

Given an `R × C` grid where each cell is `0` (empty), `1` (fresh orange), or `2` (rotten orange), every minute each rotten orange rots its 4-directional fresh neighbors. Return the minimum minutes until no fresh orange remains, or `-1` if some fresh orange is unreachable.

**Examples:**

```
Input:  [[2,1,1],
         [1,1,0],
         [0,1,1]]
Output: 4

Input:  [[2,1,1],
         [0,1,1],
         [1,0,1]]
Output: -1     ← bottom-left 1 is unreachable

Input:  [[0,2]]
Output: 0      ← no fresh oranges to rot
```

**Constraints:** `1 ≤ R, C ≤ 10`; cell values are `0`, `1`, or `2`.

---

## 🧠 Pattern Recognition

Think **multi-source BFS** the moment you see:

1. **Grid / graph** with cells changing state over discrete time steps.
2. **Multiple starting points** that propagate *simultaneously* (not sequentially).
3. **"Minimum time / steps until X"** where X is a global termination condition.

The trick: seed the queue with **all** sources at once before starting BFS — that automatically gives every source the same "minute 0" timestamp. One BFS level = one minute.

> Sibling problems with the same pattern: **LC 542 (01 Matrix), LC 1162 (As Far From Land), LC 286 (Walls and Gates).** Recognize one → recognize all four.

---

## ❌ Approach 1: DFS from each rotten orange

The instinct: for each rotten orange, DFS outward marking the minute each fresh cell rots; take the minimum across all sources for each fresh cell; final answer is the max of those minimums.

**Why it's wrong (or at least painful):**
- DFS doesn't naturally explore by distance — you'd compute the wrong minute on the first source visit and have to revisit cells from every source.
- Implicit assumption of "one source at a time" breaks the parallelism the problem states.
- O(R·C × number_of_sources) in the worst case.

**Verdict:** don't.

---

## ✅ Approach 2: Multi-source BFS with `visited[][]` + post-scan

Seed queue with all rotten oranges. Level-order BFS. After BFS, scan the grid — any remaining `1` is unreachable, return `-1`. Otherwise return `level - 1`.

**Steps in plain English:**

1. Scan grid; enqueue every rotten orange; mark `visited`.
2. Level-order BFS: each outer iteration = one minute. Increment `minutes` at end of each level.
3. After BFS, scan for any remaining `1`; if found, return `-1`.
4. Return `minutes - 1` (the loop overshoots by one — the last level processes the final wave but adds nothing).

```java
public int orangesRotting(int[][] grid) {
    int rows = grid.length;
    int cols = grid[0].length;
    Queue<int[]> queue = new ArrayDeque<>();
    boolean[][] visited = new boolean[rows][cols];

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == 2) {
                queue.offer(new int[]{ r, c });
                visited[r][c] = true;
            }
        }
    }

    int minutes = 0;
    int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cur = queue.poll();
            for (int[] d : dirs) {
                int nr = cur[0] + d[0];
                int nc = cur[1] + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) {
                    continue;
                }
                if (grid[nr][nc] == 1 && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    grid[nr][nc] = 2;
                    queue.offer(new int[]{ nr, nc });
                }
            }
        }
        minutes++;
    }

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == 1) {
                return -1;
            }
        }
    }
    return Math.max(minutes - 1, 0);
}
```

**Time:** O(R·C). **Space:** O(R·C) — queue + visited.

**Caveats:**
- `visited[][]` is redundant (we mutate `grid[nr][nc] = 2` already).
- `minutes - 1` accounting is correct but fragile — easy to get wrong under interview pressure.
- Two grid scans (initial + final `-1` check).

---

## 🚀 Approach 3: Multi-source BFS with `fresh` counter (optimal & interview-pristine)

Track `fresh` count during the initial scan, decrement on every rot. Loop guard `fresh > 0` gives early termination AND removes the `minutes - 1` cleverness.

**Steps in plain English:**

1. **Single pass** — enqueue rotten, count fresh.
2. **Trivial case** — if `fresh == 0`, return `0`.
3. **Level BFS guarded by `fresh > 0`** — increment `minutes` first, then process the level, decrementing `fresh` on each rot. Loop exits the moment all fresh are gone OR the queue empties.
4. **Final** — `fresh == 0 ? minutes : -1`.

```java
private static final int[][] DIRS = {
    { -1,  0 },
    {  1,  0 },
    {  0, -1 },
    {  0,  1 }
};

public int orangesRotting(int[][] grid) {
    int rows = grid.length;
    int cols = grid[0].length;
    Queue<int[]> queue = new ArrayDeque<>();
    int fresh = 0;

    // Step 1 — seed queue, count fresh
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == 2) {
                queue.offer(new int[]{ r, c });
            } else if (grid[r][c] == 1) {
                fresh++;
            }
        }
    }

    // Step 2 — nothing to rot
    if (fresh == 0) {
        return 0;
    }

    // Step 3 — guarded level BFS
    int minutes = 0;
    while (!queue.isEmpty() && fresh > 0) {
        minutes++;
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cur = queue.poll();
            for (int[] d : DIRS) {
                int nr = cur[0] + d[0];
                int nc = cur[1] + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) {
                    continue;
                }
                if (grid[nr][nc] != 1) {
                    continue;
                }
                grid[nr][nc] = 2;
                fresh--;
                queue.offer(new int[]{ nr, nc });
            }
        }
    }

    // Step 4 — anything left = unreachable
    return fresh == 0 ? minutes : -1;
}
```

**Time:** O(R·C). **Space:** O(R·C) for the queue worst-case, but **no `visited[][]`**.

---

### 🎨 Visual — multi-source ripple

```
INITIAL                MINUTE 1               MINUTE 2               MINUTE 3
┌─────────┐            ┌─────────┐            ┌─────────┐            ┌─────────┐
│ 2 1 1 . │            │ 2 2 1 . │            │ 2 2 2 . │            │ 2 2 2 . │
│ . 1 1 . │            │ . 1 1 . │            │ . 2 1 . │            │ . 2 2 . │
│ . . 1 2 │            │ . . 1 2 │            │ . . 2 2 │            │ . . 2 2 │
└─────────┘            └─────────┘            └─────────┘            └─────────┘
q={(0,0),(2,3)}        q={(0,1),(2,2)}        q={(0,2),(1,1)}        q={(1,2)}
fresh=5                fresh=3                fresh=1                fresh=0  ← exit

KEY INVARIANT:
   Two sources, two ripples expanding in parallel — one BFS level = one minute.
   The level count equals the worst-case distance from ANY source to ANY fresh cell.
```

---

## 📊 Approach Comparison

| Approach | Time | Space | Pros | Cons |
| --- | --- | --- | --- | --- |
| 1. DFS-from-each-source | O(R·C·S) | O(R·C) recursion | none in practice | wrong direction; cells revisited per source |
| 2. BFS + `visited[][]` + post-scan | O(R·C) | O(R·C) + bool array | conceptually clear, no clever counter | redundant state, `minutes - 1` gotcha, two grid passes |
| 3. BFS + `fresh` counter (✅) | O(R·C) | O(R·C) | early termination, no `-1` trick, single scan | mutates input (confirm OK with interviewer) |

---

## 🔁 Variations & Follow-ups

1. **What if 8-directional rotting (diagonals included)?** → Change `DIRS` to include 4 diagonal offsets. Algorithm unchanged.
2. **What if you can't mutate input?** → Restore the `visited[][]` array (`O(R·C)` extra space). The rest stays.
3. **What if each cell has a "rotting resistance" — takes K minutes to rot?** → Replace BFS with Dijkstra; the queue becomes a min-heap keyed by `minutes_to_rot`. Pattern shifts from BFS to shortest-path-on-weighted-grid.
4. **What if rotten oranges *appear* over time (per-minute spawn list)?** → Inject new sources into the queue at the start of the matching minute level — essentially "delayed multi-source BFS." Useful interview probe to test if you understand WHY seeding all sources at minute 0 works.
5. **Return the *first* minute when more than half the grid is rotten?** → Track running rotted count alongside `fresh`; return when `rotted * 2 > total_oranges`.

---

## 🎯 Key Takeaways

1. **Multi-source BFS is the answer whenever propagation is parallel.** Seeding the queue with all sources at minute 0 is what makes the level counter == minute counter.
2. **Drop `visited[][]` when you can mutate the grid.** Grid mutation IS the visited marker. Saves O(R·C) space and reduces moving parts. Caveat: confirm input mutation is allowed.
3. **`fresh` count beats post-scan `-1` detection.** Decrementing on each rot gives both early termination AND a clean `fresh == 0 ? minutes : -1` final answer.
4. **The level-counter `minutes - 1` overshoot bug is the trap.** Either guard the loop with `fresh > 0` (so you never increment for a wasted final level) or remember to subtract 1.
5. **Pattern recognition pays off for free.** Recognize this and you immediately have LC 542, 1162, 286 — all multi-source-BFS-on-grid problems with cosmetic differences.

---

## 🔗 Related Notes & Problems

- **`../Reference/bfs-dfs-templates-reference.md`** — level-order BFS template; this problem is the multi-source variant of it.
- **`../DeepDive/graphs-fundamentals.md`** — the BFS pond-ripple visualization; this is the *multi-source* ripple.
- **`../Reference/arraydeque-and-queue-reference.md`** — why `ArrayDeque<int[]>` is the right queue type for grid BFS.
- **Sibling LC problems:**
  - LC 542 — 01 Matrix (nearest 0 for each cell; multi-source BFS from all 0s)
  - LC 1162 — As Far From Land (max distance from any land cell; multi-source BFS from all land)
  - LC 286 — Walls and Gates (distance to nearest gate; multi-source BFS from all gates)

---

## 🧪 Quick Self-Test

- [ ] I can explain WHY all rotten oranges go in the queue at the start (not one by one).
- [ ] I know why `visited[][]` is redundant when mutating the grid.
- [ ] I can defend `minutes - 1` OR explain why the `fresh > 0` guard avoids needing it.
- [ ] I can re-derive the answer for `[[0,2]]` (expected: 0) without running code.
- [ ] I can name two sibling problems (LC 542 / 1162 / 286) that use the same pattern.
- [ ] I can describe the change needed for 8-directional propagation without touching the BFS loop.
