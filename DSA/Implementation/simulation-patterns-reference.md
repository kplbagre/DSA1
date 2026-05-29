# Simulation Patterns — Quick Reference

> **When to read:** 5-minute scan before any interview where a simulation/grid problem is likely (OA rounds, phone screens). Copy-paste-ready templates.
>
> **Full explanations:** `DSA/Implementation/simulation-patterns.md` (the deep dive).

---

## ⚡ Direction Array — Copy This First

```java
// 4-directional: N, E, S, W (clockwise)
int[][] dirs = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
int cdir = 0;   // 0=N, 1=E, 2=S, 3=W

// Turn
cdir = (cdir + 1) % 4;   // right
cdir = (cdir + 3) % 4;   // left

// Move
row += dirs[cdir][0];
col += dirs[cdir][1];
```

```java
// 8-directional: N, NE, E, SE, S, SW, W, NW
int[][] dirs8 = {
    {-1, 0}, {-1, 1}, {0, 1}, {1, 1},
    {1, 0}, {1, -1}, {0, -1}, {-1, -1}
};
// Turn right: (cdir + 1) % 8
// Turn left:  (cdir + 7) % 8
```

---

## ⚡ Boundary Check

```java
private boolean isValid(int r, int c, int rows, int cols) {
    return r >= 0 && r < rows && c >= 0 && c < cols;
}
```

**Usage — always compute, check, THEN assign:**

```java
int nr = row + dirs[cdir][0];
int nc = col + dirs[cdir][1];
if (isValid(nr, nc, rows, cols)) {
    row = nr;
    col = nc;
}
```

---

## ⚡ Command Parsing Template

```java
for (String cmd : commands) {
    if ("TL".equalsIgnoreCase(cmd)) {
        cdir = (cdir + 3) % 4;

    } else if ("TR".equalsIgnoreCase(cmd)) {
        cdir = (cdir + 1) % 4;

    } else {
        int steps = Integer.parseInt(cmd.substring(1));
        for (int s = 0; s < steps; s++) {
            int nr = row + dirs[cdir][0];
            int nc = col + dirs[cdir][1];
            if (!isValid(nr, nc, rows, cols)) { break; }
            row = nr;
            col = nc;
            result += grid[row][col];
        }
    }
}
```

**Three rules:**
1. `"literal".equalsIgnoreCase(cmd)` — literal first, null-safe
2. `Integer.parseInt(cmd.substring(1))` — NOT `charAt(1) - '0'`
3. `else if` chain — NOT sequential `if` blocks

---

## ⚡ Full Simulation Skeleton

```java
public int simulate(int rows, int cols, int[][] grid, String[] commands) {
    // 1. Direction array
    int[][] dirs = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};

    // 2. State
    int row = 0, col = 0, cdir = 1;
    long result = 0;

    // 3. Command loop
    for (String cmd : commands) {
        if ("TL".equalsIgnoreCase(cmd)) {
            cdir = (cdir + 3) % 4;
        } else if ("TR".equalsIgnoreCase(cmd)) {
            cdir = (cdir + 1) % 4;
        } else {
            int steps = Integer.parseInt(cmd.substring(1));
            for (int s = 0; s < steps; s++) {
                int nr = row + dirs[cdir][0];
                int nc = col + dirs[cdir][1];
                if (!isValid(nr, nc, rows, cols)) { break; }
                row = nr;
                col = nc;
                result += grid[row][col];
            }
        }
    }
    return (int) result;
}
```

---

## ⚡ Spiral Matrix Template

```java
public List<Integer> spiralOrder(int[][] matrix) {
    List<Integer> res = new ArrayList<>();
    int top = 0, bot = matrix.length - 1;
    int left = 0, right = matrix[0].length - 1;

    while (top <= bot && left <= right) {
        for (int c = left; c <= right; c++) { res.add(matrix[top][c]); }
        top++;
        for (int r = top; r <= bot; r++) { res.add(matrix[r][right]); }
        right--;
        if (top <= bot) {
            for (int c = right; c >= left; c--) { res.add(matrix[bot][c]); }
            bot--;
        }
        if (left <= right) {
            for (int r = bot; r >= top; r--) { res.add(matrix[r][left]); }
            left++;
        }
    }
    return res;
}
```

---

## 🧭 "What Type of Simulation?" — 10-Second Decision

```
"Follow directional commands on a grid"        → Direction array + command dispatch
"Traverse grid in spiral order"                 → Spiral template (shrinking boundaries)
"All cells update simultaneously"               → Snapshot first, then update (Game of Life)
"Process collisions / nested brackets"          → Stack-based simulation
"Spread from multiple sources each time step"   → Multi-source BFS (level-order)
```

---

## ⚠️ Gotchas — Quick Scan

| Gotcha | Fix |
| --- | --- |
| Turn + move in same block | Turn changes `cdir` ONLY. Move changes `row/col` ONLY. |
| Jump N steps at once | Loop 1 step at a time — obstacles + tolls need each intermediate cell |
| Update position before checking bounds | Compute `nr/nc`, check `isValid`, THEN assign `row = nr` |
| `(cdir - 1) % 4` gives -1 | Use `(cdir + 3) % 4` |
| `charAt(1) - '0'` for "F12" | `Integer.parseInt(cmd.substring(1))` |
| Sequential `if` (not `else if`) | Multiple branches execute — use `else if` chain |
| North means row + 1 (wrong!) | North = row - 1 (grid row 0 = top) |
| Starting cell not counted | Read problem spec — add `toll += grid[0][0]` before loop if needed |

---

## 🧠 4 Mental Models (one-liner each)

1. **"What changes, what stays?"** — Turn: direction changes, position stays. Move: position changes, direction stays.
2. **"One step at a time"** — `"F5"` = 5 iterations of move-1, not one jump of 5.
3. **"Parse completely, then act"** — Extract command type + argument BEFORE updating any state.
4. **"Draw the state"** — Debug by tracing position + direction + accumulator after each command.

---

## 🧩 Speed Drill — 5 Minutes (Do Before Every Interview)

On a blank notepad, write ALL of these from memory (no peeking):

1. **Direction array** — `int[][] dirs` with 4 directions in clockwise order, exact values
2. **Turn left + turn right** — one-line expressions each
3. **`isValid()`** — complete method signature and body
4. **Command dispatch** — `if / else if / else` for TL / TR / F{n}, with `parseInt(substring(...))` and step-by-step movement
5. **Full simulation skeleton** — combine all of the above into one complete `simulate()` method

**Time yourself. Target: under 5 minutes for all 5.**

Compare with the templates above. Self-check:
- [ ] Used `.equalsIgnoreCase()` not `==` for strings?
- [ ] Used `parseInt(substring(...))` not `charAt - '0'`?
- [ ] Used `else if` not sequential `if`?
- [ ] Used `(cdir + 3) % 4` not `(cdir - 1) % 4` for turn left?
- [ ] Computed `nr/nc` BEFORE updating `row/col`?
- [ ] Moved step-by-step in a loop, not one jump?

**Scoring:** All 5 correct + all 6 checks passed = ready. Missed any check = re-read that gotcha in the table above.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Full explanations + ASCII visuals | `DSA/Implementation/simulation-patterns.md` |
| Java coding traps (equality, overflow, parsing) | `DSA/Implementation/java-coding-traps.md` |
| Java traps quick reference | `DSA/Implementation/java-coding-traps-reference.md` |
| BFS / level-order | `DSA/DeepDive/graphs-fundamentals.md` |
| Morning cheatsheet | `DSA/Reference/interview-morning-cheatsheet.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Compact reference companion for `simulation-patterns.md`. Direction array, boundary check, command parsing, full simulation skeleton, spiral matrix template, 8 gotchas, 4 mental models. |
| May 2026 | **Speed drill added.** 5-minute pre-interview drill: write all 5 building blocks from memory + 6-point self-check. |
