# Simulation Patterns — Building Blocks for "Just Do What It Says" Problems

> **What this file is:** The mental models, templates, and building blocks for simulation problems — the kind where the problem says *"a robot starts at (0,0) facing north, follow these commands..."* and you just have to translate English into code without any clever algorithm.
>
> **When to read:** Once top-to-bottom (~35 min). Then skim the companion `simulation-patterns-reference.md` before interviews where simulation problems are likely (OA rounds, phone screens).
>
> **Who this is for:** You already know BFS/DFS/DP. This file is about the OTHER kind of problem — the one where the algorithm is trivial but the implementation is where you win or lose.

---

## 🎯 Why This File Exists

> **Lesson learned the hard way (May 2026):** In a 30-minute interview, the problem was a car driving on a grid following commands (turn left, turn right, forward N, with toll calculation). The algorithm was obvious — just simulate step by step. The code was a disaster: `cdir = cdir++` (direction never changed), `==` on strings (nothing matched), `charAt(1) - '0'` (multi-digit numbers truncated), sequential `if` blocks (multiple branches executed). **Every bug was an implementation mistake, not an algorithmic one.** The "easy" problem became impossible because there was no disciplined simulation template.

**The thesis:** Simulation problems don't test your algorithm knowledge. They test whether you have **disciplined building blocks** — direction arrays, command parsers, state trackers, boundary checkers — that you can snap together without thinking. If you don't have these building blocks pre-loaded, you'll reinvent them under pressure and introduce bugs.

---

## 📖 Terminology

| Term | Meaning |
| --- | --- |
| **Simulation problem** | A problem where you model a process step-by-step: read commands, update state, repeat. No "trick" — just translate the English spec into code. |
| **Direction array** | A pre-built array encoding how (row, col) changes for each direction (up, right, down, left). Eliminates the need for separate `if`/`else` blocks per direction. |
| **State** | The set of variables that fully describe the current situation: position, direction, score, flags. If you can serialize the state, you can replay the simulation. |
| **Command dispatch** | Routing each command string (like "TL", "TR", "F5") to the right handler. The `if / else if / else` chain. |
| **Boundary check** | Verifying that a (row, col) position is inside the grid before moving there. |
| **Turn vs Move** | Two separate operations: turning changes direction (no position change), moving changes position (no direction change). Combining them in one block is the #1 simulation bug. |

---

## 🪜 The 7 Building Blocks

Every simulation problem is built from these 7 pieces. Master each one in isolation, and you can snap them together for any problem.

---

### Building Block 1: Direction Array ⭐

**The problem it solves:** You have 4 (or 8) directions. Without a direction array, you write 4 separate `if` blocks to handle up/right/down/left. With a direction array, you write ONE move line that works for all directions.

**Steps in plain English:**

1. **Define the directions** as a 2D array, where index 0 = North, 1 = East, 2 = South, 3 = West (clockwise order).
2. **Track current direction** as an integer index (`cdir`).
3. **Move** by adding `dirs[cdir][0]` to row and `dirs[cdir][1]` to col.
4. **Turn** by changing the index: `+1` for clockwise (right), `+3` for counterclockwise (left), always `% 4`.

```java
// Step 1 — define directions (clockwise: N, E, S, W)
int[][] dirs = {
    {-1, 0},   // 0 = North (row decreases)
    {0, 1},    // 1 = East  (col increases)
    {1, 0},    // 2 = South (row increases)
    {0, -1}    // 3 = West  (col decreases)
};

// Step 2 — track current direction
int cdir = 0;   // start facing North

// Step 3 — move one step in current direction
row += dirs[cdir][0];
col += dirs[cdir][1];

// Step 4 — turn
cdir = (cdir + 1) % 4;   // turn right (clockwise)
cdir = (cdir + 3) % 4;   // turn left  (counterclockwise)
```

### 🎨 Visual — Direction Array Indexing

```
              North (0)
              row - 1, col
                 ↑
                 │
  West (3) ←────●────→ East (1)
  row, col - 1  │       row, col + 1
                 ↓
              South (2)
              row + 1, col

  dirs[0] = {-1,  0}   North: row decreases
  dirs[1] = { 0,  1}   East:  col increases
  dirs[2] = { 1,  0}   South: row increases
  dirs[3] = { 0, -1}   West:  col decreases

  Turn right: (cdir + 1) % 4
      N(0) → E(1) → S(2) → W(3) → N(0)   ✅

  Turn left:  (cdir + 3) % 4
      N(0) → W(3) → S(2) → E(1) → N(0)   ✅
      Why +3? Because (cdir - 1) % 4 gives -1 for cdir=0 in Java.
      +3 is the same as -1 (mod 4), but always non-negative.

  KEY INVARIANT:
     dirs[cdir] always gives the correct (dr, dc) for the current heading.
     Turning is just index arithmetic — no if/else needed.
```

**Why clockwise order matters:** The `+1 = right, +3 = left` trick only works if the directions are in clockwise order (N→E→S→W). If you use a different order, the turn math breaks.

#### 8-directional variant

```java
// For problems that allow diagonal movement (King on chessboard, etc.)
int[][] dirs8 = {
    {-1, 0}, {-1, 1}, {0, 1}, {1, 1},
    {1, 0}, {1, -1}, {0, -1}, {-1, -1}
};
// Clockwise from North: N, NE, E, SE, S, SW, W, NW
// Turn right: (cdir + 1) % 8
// Turn left:  (cdir + 7) % 8
```

#### Common pitfall — using (cdir - 1) % 4

```java
// ❌ Java modulo preserves sign
int cdir = 0;
cdir = (cdir - 1) % 4;    // = -1, not 3

// ✅ add (size - 1) instead of subtracting 1
cdir = (cdir + 3) % 4;    // = 3 ✅
```

**Cross-reference:** Java modulo with negatives is covered in `DSA/Implementation/java-coding-traps.md` — Family 3, Trap 3e.

> 🧩 **Drill — do this NOW before reading further:**
> On a blank notepad (no peeking), write:
> 1. The 4-directional array in clockwise order (N, E, S, W) — exact `int[][]` declaration with values
> 2. One-line expression to turn right from current direction
> 3. One-line expression to turn left from current direction
> 4. Verify: if `cdir = 0` (North), what does turn-left give? What does turn-right give? Do they match your expressions?
>
> Then compare with the code above. If you wrote `(cdir - 1) % 4` for turn left, you fell into the Java modulo trap.

---

### Building Block 2: Boundary Check

**The problem it solves:** Before moving to a new cell, verify it's inside the grid. This is a one-liner you should be able to type without thinking.

```java
private boolean isValid(int r, int c, int rows, int cols) {
    return r >= 0 && r < rows && c >= 0 && c < cols;
}
```

**Usage pattern:**

```java
int nr = row + dirs[cdir][0];
int nc = col + dirs[cdir][1];
if (isValid(nr, nc, grid.length, grid[0].length)) {
    row = nr;
    col = nc;
}
// If not valid: depends on the problem — stop, wrap around, or ignore
```

### 🎨 Visual — Boundary Check

```
    col:  0    1    2    3    4
  row: ┌────┬────┬────┬────┬────┐
    0  │    │    │    │    │    │
       ├────┼────┼────┼────┼────┤
    1  │    │    │  ● │    │    │   ● = current position (1, 2)
       ├────┼────┼────┼────┼────┤
    2  │    │    │    │    │    │
       └────┴────┴────┴────┴────┘

  Valid moves from (1, 2):
    North: (0, 2) ✅  r >= 0
    East:  (1, 3) ✅  c < cols
    South: (2, 2) ✅  r < rows
    West:  (1, 1) ✅  c >= 0

  What if ● is at (0, 4)?
    North: (-1, 4) ❌  r < 0  → out of bounds
    East:  (0, 5)  ❌  c >= cols → out of bounds

  KEY INVARIANT:
     Always compute new position FIRST, check SECOND, update THIRD.
     Never update row/col before validating — that's how you get ArrayIndexOutOfBoundsException.
```

#### Wrap-around variant (toroidal grid)

```java
// Some problems wrap around (e.g., Pac-Man)
int nr = (row + dirs[cdir][0] + rows) % rows;
int nc = (col + dirs[cdir][1] + cols) % cols;
```

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory:
> 1. The `isValid(r, c, rows, cols)` method — complete signature and body (one return statement)
> 2. The 3-line "compute, check, update" pattern: compute new row/col, check with isValid, update if valid
>
> Then compare with the code above. The order must be: compute → check → update. If you wrote update first, re-read the visual.

---

### Building Block 3: Command Parsing

**The problem it solves:** The input comes as a string or array of strings (`"TL"`, `"TR"`, `"F12"`). You need to extract the command type and any numeric argument.

**Steps in plain English:**

1. **Identify command type** — usually the first character or a keyword.
2. **Extract numeric argument** — if the command has a number (like "F12"), parse it with `Integer.parseInt`, NOT `charAt - '0'`.
3. **Dispatch** — route to the right handler using `if / else if / else`.

```java
// Step 1 + 2 — parse command
for (String cmd : commands) {
    if ("TL".equalsIgnoreCase(cmd)) {
        // Step 3a — turn left
        cdir = (cdir + 3) % 4;
    } else if ("TR".equalsIgnoreCase(cmd)) {
        // Step 3b — turn right
        cdir = (cdir + 1) % 4;
    } else {
        // Step 3c — forward: extract distance
        int steps = Integer.parseInt(cmd.substring(1));
        for (int s = 0; s < steps; s++) {
            int nr = row + dirs[cdir][0];
            int nc = col + dirs[cdir][1];
            if (isValid(nr, nc, rows, cols)) {
                row = nr;
                col = nc;
                // process cell (toll, obstacle, etc.)
            }
        }
    }
}
```

**Three rules for clean command parsing:**

| Rule | Why |
| --- | --- |
| **Literal-first `.equals()`** | `"TL".equals(cmd)` — null-safe, no NPE if cmd is null |
| **`Integer.parseInt(substring(...))` for numbers** | `charAt(1) - '0'` only works for single digits — "F12" gives 1, not 12 |
| **`else if` chain, never sequential `if`** | Sequential `if` blocks let multiple branches execute for the same input |

> 🧩 **Drill — do this NOW before reading further:**
> Write from memory the complete command dispatch for `"TL"`, `"TR"`, and `"F{n}"`:
> - Use literal-first `.equalsIgnoreCase()` for string comparison
> - Parse distance from `"F12"` using `parseInt(substring(...))`
> - Move step-by-step in a loop (not one jump)
> - Include boundary check before updating position
>
> Three traps to self-check: Did you use `==` for strings? Did you use `charAt - '0'`? Did you use sequential `if` instead of `else if`? Fix any you caught.

### 🎨 Visual — Parse First, Process Second

```
    Input: ["TL", "F3", "TR", "F1"]

    ┌──────────┐
    │  "TL"    │──► TYPE: turn   │  ACTION: cdir = (cdir + 3) % 4
    ├──────────┤                  │          (no move, no cell processing)
    │  "F3"    │──► TYPE: move   │  ACTION: move 3 steps, one at a time
    │          │    ARG:  3      │          check each cell along the way
    ├──────────┤                  │
    │  "TR"    │──► TYPE: turn   │  ACTION: cdir = (cdir + 1) % 4
    ├──────────┤                  │
    │  "F1"    │──► TYPE: move   │  ACTION: move 1 step
    │          │    ARG:  1      │
    └──────────┘

  KEY INVARIANT:
     Parse the command completely BEFORE processing it.
     Never interleave parsing and state updates — that's where bugs hide.
```

---

### Building Block 4: State Modeling

**The problem it solves:** You need to track everything that changes during the simulation. Miss one variable and the simulation drifts.

**The template — what to declare before the loop:**

```java
// Position
int row = 0, col = 0;

// Direction (index into dirs[])
int cdir = 0;

// Accumulated result (depends on problem)
int toll = 0;
int steps = 0;
boolean[][] visited = new boolean[rows][cols];

// Mark starting cell
visited[row][col] = true;
```

**Mental model — "What is my state?"**

Ask these 4 questions before writing any simulation code:

| Question | Example answer |
| --- | --- |
| **Where am I?** | `row`, `col` |
| **Which way am I facing?** | `cdir` (index into direction array) |
| **What have I accumulated?** | `toll`, `steps`, `visited[][]` |
| **What are the stopping conditions?** | Hit boundary, hit obstacle, commands exhausted |

### 🎨 Visual — State Snapshot at Each Step

```
    Grid 3×3, start (0,0) facing East, commands: ["F2", "TR", "F1"]
    Toll: cell (r,c) costs (r + c)

    Step 0 (initial):
    ┌───┬───┬───┐
    │ ●→│   │   │   state: row=0, col=0, cdir=1(E), toll=0
    ├───┼───┼───┤
    │   │   │   │
    ├───┼───┼───┤
    │   │   │   │
    └───┴───┴───┘

    After "F2" (move 2 steps east):
    ┌───┬───┬───┐
    │ · │ · │ ●→│   move to (0,1): toll += 0+1 = 1
    ├───┼───┼───┤   move to (0,2): toll += 0+2 = 2
    │   │   │   │   state: row=0, col=2, cdir=1(E), toll=3
    ├───┼───┼───┤
    │   │   │   │
    └───┴───┴───┘

    After "TR" (turn right → now facing South):
    ┌───┬───┬───┐
    │ · │ · │ ●↓│   cdir = (1+1) % 4 = 2 (South)
    ├───┼───┼───┤   state: row=0, col=2, cdir=2(S), toll=3
    │   │   │   │   (no position change — turn only)
    ├───┼───┼───┤
    │   │   │   │
    └───┴───┴───┘

    After "F1" (move 1 step south):
    ┌───┬───┬───┐
    │ · │ · │ · │   move to (1,2): toll += 1+2 = 3
    ├───┼───┼───┤   state: row=1, col=2, cdir=2(S), toll=6
    │   │   │ ●↓│
    ├───┼───┼───┤
    │   │   │   │
    └───┴───┴───┘

    Final answer: toll = 6

  KEY INVARIANT:
     Turn changes cdir only. Move changes row/col only.
     Accumulated values update only when you actually move to a new cell.
     These three concerns (turn, move, accumulate) must never be combined.
```

> 🧩 **Drill — do this NOW before reading further:**
> For this problem: *"A robot starts at (0,0) facing North on a 5×5 grid. It receives commands TL/TR/F{n}. Each cell has a score value. Return total score collected."*
>
> Without looking up, list every state variable you'd declare before the command loop. Then answer:
> 1. Where am I?
> 2. Which way am I facing?
> 3. What have I accumulated?
> 4. When do I stop?
>
> Compare your list with the "4 questions" table above.

---

### Building Block 5: The Simulation Loop Skeleton ⭐

This is the master template. Every simulation problem fits this shape.

**Steps in plain English:**

1. **Initialize state** — position, direction, accumulators, visited set.
2. **Loop over commands** — for each command, parse it.
3. **Dispatch** — turn or move based on command type.
4. **Move step-by-step** — for forward commands, move ONE cell at a time (not all at once), checking boundaries and processing each cell.
5. **Return accumulated result**.

```java
public int simulate(int rows, int cols, String[] commands, int[][] grid) {
    // Step 1 — initialize state
    int[][] dirs = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
    int row = 0, col = 0, cdir = 1;   // start facing East
    int result = 0;

    // Step 2 — loop over commands
    for (String cmd : commands) {

        // Step 3 — dispatch
        if ("TL".equalsIgnoreCase(cmd)) {
            cdir = (cdir + 3) % 4;
        } else if ("TR".equalsIgnoreCase(cmd)) {
            cdir = (cdir + 1) % 4;
        } else {
            // Step 4 — move step by step
            int steps = Integer.parseInt(cmd.substring(1));
            for (int s = 0; s < steps; s++) {
                int nr = row + dirs[cdir][0];
                int nc = col + dirs[cdir][1];
                if (!isValid(nr, nc, rows, cols)) {
                    break;   // or continue, depends on problem
                }
                row = nr;
                col = nc;
                result += grid[row][col];   // process cell
            }
        }
    }

    // Step 5 — return
    return result;
}

private boolean isValid(int r, int c, int rows, int cols) {
    return r >= 0 && r < rows && c >= 0 && c < cols;
}
```

**Why step-by-step movement (not jump):** If the command is "F5" and there's a wall at step 3, you must stop at step 2. You also need to process each intermediate cell (toll, visited, etc.). Jumping directly to the final position skips obstacles and intermediate cells.

> 🧩 **Drill — THE BIG ONE. Do this NOW:**
> On a blank notepad, write the **complete simulation skeleton** from memory:
> - Direction array declaration (4-dir, clockwise)
> - State initialization (position, direction, result)
> - Command loop with 3-way dispatch (TL / TR / forward)
> - Step-by-step forward movement with boundary check
> - `isValid()` helper method
>
> **Time yourself. Target: under 3 minutes.**
>
> Then compare with the template above. Check for: `==` on strings, `charAt - '0'`, `(cdir - 1) % 4`, sequential `if`, jumping N steps instead of looping.

---

### Building Block 6: Cell Processing Helpers

**The problem it solves:** Inside the move loop, you often need to do more than just update position — collect tolls, mark visited, check obstacles, change cell state. Extracting these into tiny helpers keeps the main loop clean.

```java
// Toll: add cell value to running total
private int collectToll(int[][] grid, int r, int c) {
    return grid[r][c];
}

// Obstacle check: can we enter this cell?
private boolean isWalkable(int[][] grid, int r, int c) {
    return grid[r][c] != -1;   // -1 = wall
}

// Mark visited
private void markVisited(boolean[][] visited, int r, int c) {
    visited[r][c] = true;
}
```

**Why helpers?** Not because they're complex — because they give each action a **name**. When debugging under pressure, `collectToll(grid, nr, nc)` is instantly readable. `result += grid[nr][nc]` buried inside a 20-line loop is not.

> 🧩 **Drill — do this NOW before reading further:**
> Write two tiny helper methods from memory:
> 1. `isWalkable(grid, r, c)` — returns `true` if cell is not a wall (wall = -1)
> 2. `collectToll(grid, r, c)` — returns the toll value at cell (r, c)
>
> These are trivially simple. The drill isn't about difficulty — it's about **building the habit of extracting helpers** instead of inlining everything.

---

### Building Block 7: Grid Construction & Initialization

**The problem it solves:** Some problems give you the grid. Others make you construct it from descriptions. Either way, initializing the grid correctly is step zero.

```java
// Pre-built grid
int[][] grid = new int[rows][cols];

// Fill with a default value
for (int[] row : grid) {
    Arrays.fill(row, 0);
}

// Place obstacles
grid[1][2] = -1;   // wall

// Toll values
grid[0][1] = 5;    // entering (0,1) costs 5
```

**Coordinate convention (memorize this):**

```
    grid[row][col]

    row = y-axis (increases downward)
    col = x-axis (increases rightward)

    grid[0][0] = top-left corner
    grid[rows-1][cols-1] = bottom-right corner
```

> 🧩 **Drill — do this NOW before reading further:**
> Quick-fire answers (no looking):
> 1. `grid[row][col]` — does row increase upward or downward?
> 2. What cell is `grid[0][0]` — top-left or bottom-left?
> 3. Grid is 3 rows × 5 cols — what is the index of the bottom-right cell?
> 4. North means `row - 1` or `row + 1`?
>
> Answers: downward, top-left, `grid[2][4]`, `row - 1`. If you got any wrong, re-read the coordinate convention box.

---

## 🧭 Common Simulation Problem Types

### Type 1: Robot on a Grid (the canonical simulation)

**Pattern:** Robot starts at position, follows directional commands, return final position or accumulated value.

**LeetCode examples:**
- LC 874 Walking Robot Simulation ✅
- LC 489 Robot Room Cleaner 🟡
- LC 2069 Walking Robot Simulation II 🟡

**Key building blocks:** Direction array + command dispatch + boundary check + step-by-step movement.

### Type 2: Spiral Matrix

**Pattern:** Traverse a grid in spiral order (right → down → left → up → repeat), shrinking the boundaries each revolution.

**LeetCode examples:**
- LC 54 Spiral Matrix ✅
- LC 59 Spiral Matrix II ✅
- LC 885 Spiral Matrix III 🟡

**Steps in plain English:**

1. **Define 4 boundaries** — top, bottom, left, right.
2. **Loop through directions** (right, down, left, up) using the direction array.
3. **After each direction**, shrink the corresponding boundary.
4. **Stop** when boundaries cross or all cells are visited.

```java
public List<Integer> spiralOrder(int[][] matrix) {
    List<Integer> result = new ArrayList<>();
    int top = 0, bottom = matrix.length - 1;
    int left = 0, right = matrix[0].length - 1;

    while (top <= bottom && left <= right) {
        // → right across top row
        for (int c = left; c <= right; c++) {
            result.add(matrix[top][c]);
        }
        top++;

        // ↓ down right column
        for (int r = top; r <= bottom; r++) {
            result.add(matrix[r][right]);
        }
        right--;

        // ← left across bottom row
        if (top <= bottom) {
            for (int c = right; c >= left; c--) {
                result.add(matrix[bottom][c]);
            }
            bottom--;
        }

        // ↑ up left column
        if (left <= right) {
            for (int r = bottom; r >= top; r--) {
                result.add(matrix[r][left]);
            }
            left++;
        }
    }
    return result;
}
```

**The gotcha:** The `if (top <= bottom)` and `if (left <= right)` checks before the 3rd and 4th directions prevent double-counting when the remaining area is a single row or column.

### Type 3: Game of Life / State Evolution

**Pattern:** Every cell updates simultaneously based on neighbor states. You can't update in-place (old state needed for other cells' calculations).

**LeetCode examples:**
- LC 289 Game of Life ✅
- LC 73 Set Matrix Zeroes ✅

**Key insight — "snapshot before update":**

```java
// ❌ Updating in-place corrupts neighbor reads
for each cell:
    count neighbors
    update cell         // other cells now see the NEW value, not the old one

// ✅ Two-pass: count first, then update
// OR: encode both states in the same cell
//     0 → 0: stays dead  → encode as 0
//     1 → 0: was alive, now dead → encode as 2
//     0 → 1: was dead, now alive → encode as 3
//     1 → 1: stays alive → encode as 1
//     Old state: value % 2
//     New state: value / 2... (or just use a copy)
```

### Type 4: Stack-Based Simulation (Asteroid, Calculator)

**Pattern:** Process elements one-by-one, using a stack to handle "collisions" or "operator precedence."

**LeetCode examples:**
- LC 735 Asteroid Collision ✅
- LC 224 Basic Calculator 🟡
- LC 394 Decode String ✅

**Not covered in depth here** — these are more "stack pattern" than "grid simulation." Mentioned for completeness.

### Type 5: Time-Step Simulation (BFS-like)

**Pattern:** Multiple agents act simultaneously each time step. Process ALL agents at the current step before moving to the next step.

**LeetCode examples:**
- LC 994 Rotting Oranges ✅
- LC 286 Walls and Gates ✅
- LC 542 01 Matrix ✅

**Key insight — "level-order = time step":**

```java
// Process all current-step items before any next-step items
int size = queue.size();
for (int i = 0; i < size; i++) {
    int[] cell = queue.poll();
    // process + add next-step neighbors to queue
}
time++;
```

**Cross-reference:** BFS level-order is covered in `DSA/DeepDive/graphs-fundamentals.md`.

---

## 🔬 Worked Walkthrough: Car-Toll Problem

**Problem statement (reconstructed):** A car starts at (0, 0) on a grid, facing a given direction. It receives commands: "TL" (turn left), "TR" (turn right), "F{n}" (move forward n steps). Each cell has a toll value. Return the total toll collected.

**Let's solve this using the 7 building blocks.**

### Step 1 — Identify the building blocks needed

```
Problem says "car on a grid"            → BB1: Direction array
Problem says "TL", "TR", "F{n}"         → BB3: Command parsing
Problem says "toll for each cell"       → BB6: Cell processing (toll)
Problem says "grid boundaries"          → BB2: Boundary check
Combines into                           → BB5: Simulation loop skeleton
```

### Step 2 — Write the state initialization

```java
int[][] dirs = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
int row = 0, col = 0;
int cdir = 1;          // facing East (depends on problem spec)
long toll = 0;         // long — toll values might be large
```

### Step 3 — Write the simulation loop

```java
public long solve(int rows, int cols, int[][] tollGrid, String[] commands) {
    int[][] dirs = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
    int row = 0, col = 0, cdir = 1;
    long toll = 0;

    for (String cmd : commands) {
        if ("TL".equalsIgnoreCase(cmd)) {
            cdir = (cdir + 3) % 4;

        } else if ("TR".equalsIgnoreCase(cmd)) {
            cdir = (cdir + 1) % 4;

        } else {
            // Forward: parse distance
            int steps = Integer.parseInt(cmd.substring(1));
            for (int s = 0; s < steps; s++) {
                int nr = row + dirs[cdir][0];
                int nc = col + dirs[cdir][1];
                if (!isValid(nr, nc, rows, cols)) {
                    break;
                }
                row = nr;
                col = nc;
                toll += tollGrid[row][col];
            }
        }
    }
    return toll;
}

private boolean isValid(int r, int c, int rows, int cols) {
    return r >= 0 && r < rows && c >= 0 && c < cols;
}
```

### Step 4 — Trace through an example

```
Grid 3×3, toll values:
    0  1  2
    1  2  3
    2  3  4

Start: (0,0) facing East
Commands: ["F2", "TR", "F2", "TL", "F1"]

Command "F2": move East 2 steps
    (0,0) → (0,1): toll += 1  → toll = 1
    (0,1) → (0,2): toll += 2  → toll = 3
    State: (0,2), cdir=1(E)

Command "TR": turn right → South
    cdir = (1+1) % 4 = 2(S)
    State: (0,2), cdir=2(S)

Command "F2": move South 2 steps
    (0,2) → (1,2): toll += 3  → toll = 6
    (1,2) → (2,2): toll += 4  → toll = 10
    State: (2,2), cdir=2(S)

Command "TL": turn left → East
    cdir = (2+3) % 4 = 1(E)
    State: (2,2), cdir=1(E)

Command "F1": move East 1 step
    (2,2) → (2,3): INVALID (col 3 >= cols 3)
    break — no movement
    State: (2,2), cdir=1(E)

Answer: toll = 10
```

### Step 5 — Review for traps

| Check | Status |
| --- | --- |
| Using `.equalsIgnoreCase` for string comparison? | ✅ |
| `Integer.parseInt(substring(1))` for distance? | ✅ |
| `else if` chain (not sequential `if`)? | ✅ |
| `(cdir + 3) % 4` for left turn (not `cdir - 1`)? | ✅ |
| Step-by-step movement (not jump to final position)? | ✅ |
| Boundary check BEFORE updating position? | ✅ |
| `long` for accumulated toll? | ✅ |
| Compute `nr`/`nc` first, check, then assign to `row`/`col`? | ✅ |

---

## ⚠️ Simulation Gotchas — Silent Bug Hall of Fame

### Gotcha 1: Combining turn and move in one block

```java
// ❌ Turn and move in the same branch — loses a step
if ("TL".equals(cmd)) {
    cdir = (cdir + 3) % 4;
    row += dirs[cdir][0];     // BUG: turning shouldn't move!
    col += dirs[cdir][1];
}

// ✅ Turn ONLY changes direction. Move is a separate branch.
if ("TL".equals(cmd)) {
    cdir = (cdir + 3) % 4;
    // no movement here
}
```

### Gotcha 2: Moving all steps at once instead of one at a time

```java
// ❌ Jumps to final position — skips obstacles and intermediate tolls
int steps = Integer.parseInt(cmd.substring(1));
row += dirs[cdir][0] * steps;
col += dirs[cdir][1] * steps;

// ✅ Move one step at a time
for (int s = 0; s < steps; s++) {
    int nr = row + dirs[cdir][0];
    int nc = col + dirs[cdir][1];
    if (!isValid(nr, nc, rows, cols)) { break; }
    row = nr;
    col = nc;
    toll += grid[row][col];
}
```

### Gotcha 3: Updating position before checking bounds

```java
// ❌ ArrayIndexOutOfBoundsException
row += dirs[cdir][0];
col += dirs[cdir][1];
if (row < 0 || row >= rows) { row -= dirs[cdir][0]; }  // messy undo

// ✅ Compute new position, check, then update
int nr = row + dirs[cdir][0];
int nc = col + dirs[cdir][1];
if (isValid(nr, nc, rows, cols)) {
    row = nr;
    col = nc;
}
```

### Gotcha 4: Off-by-one on starting cell

```java
// Does the starting cell (0,0) count for toll?
// Read the problem statement carefully!

// If yes:
toll += grid[0][0];   // before the command loop

// If no:
// just start the loop, the first move will add toll
```

### Gotcha 5: Direction mismatch — North means row - 1

```
In a grid:
    row 0 is the TOP
    row increases DOWNWARD

So:
    North = row - 1 (up in the grid)
    South = row + 1 (down in the grid)

This is the OPPOSITE of math coordinates where y increases upward.
```

### Gotcha 6: Forgetting to handle the "no more commands" case

```java
// What if commands array is empty?
// The loop just doesn't execute → return initial state

// What if a forward command has distance 0? ("F0")
// The inner loop runs 0 times → no movement → safe
```

### Gotcha 7: String parsing edge cases

```java
// Command format variations you might encounter:
"F12"      → Integer.parseInt("F12".substring(1))  → 12 ✅
"forward3" → Integer.parseInt("forward3".substring(7)) → 3 ✅
"L"        → turn left, no number needed
"R 5"      → split by space, then parse

// Always clarify the exact format before coding.
// Ask the interviewer: "Is the format always a letter followed by a number?"
```

---

## 🧠 Mental Models — What to Think Before You Code

### Mental Model 1: "What changes, what stays?"

Before writing any code, list what changes on each step:

```
Turn command:  direction CHANGES, position STAYS
Move command:  position CHANGES, direction STAYS
Both commands: accumulator MAY change (toll, visited)
```

If a variable changes when it shouldn't, you've mixed concerns.

### Mental Model 2: "One step at a time"

Every forward command is a loop of single steps. Never jump.

```
"F5" is NOT:  move to (row + 5*dr, col + 5*dc)
"F5" IS:      for 5 iterations: move 1, check, process, repeat
```

### Mental Model 3: "Parse completely, then act"

Read the entire command, extract all information (type + argument), THEN perform the action. Never interleave parsing and state updates.

### Mental Model 4: "Draw the state after each command"

When debugging, don't re-read your code. Instead, draw the grid and trace the state:

```
After command 1: position = ?, direction = ?, toll = ?
After command 2: position = ?, direction = ?, toll = ?
...
```

If the trace diverges from expected output, the divergence point tells you exactly which command handler has the bug.

---

## 🗺️ Practice Plan

### Tier 0 — Direction Array Muscle Memory (30 min)

Write these from scratch on a blank notepad:

1. 4-directional array (N, E, S, W) with turn-left and turn-right
2. 8-directional array with turn formulas
3. `isValid()` boundary check
4. Step-by-step forward movement with boundary check

### Tier 1 — Core Simulation (do these first) ✅

| # | Problem | Key building blocks |
| --- | --- | --- |
| 1 | LC 874 Walking Robot Simulation | Direction array + obstacle set + step-by-step |
| 2 | LC 54 Spiral Matrix | Direction rotation + boundary shrinking |
| 3 | LC 59 Spiral Matrix II | Same as above, filling instead of reading |
| 4 | LC 289 Game of Life | State snapshot + neighbor counting |
| 5 | LC 994 Rotting Oranges | BFS time-step simulation |

### Tier 2 — Intermediate 🟡

| # | Problem | Why it's here |
| --- | --- | --- |
| 6 | LC 885 Spiral Matrix III | Unbounded spiral — direction array + increasing step count |
| 7 | LC 73 Set Matrix Zeroes | In-place state encoding trick |
| 8 | LC 735 Asteroid Collision | Stack-based simulation |
| 9 | LC 394 Decode String | Stack simulation with nested parsing |
| 10 | LC 542 01 Matrix | Multi-source BFS simulation |

### Tier 3 — Advanced 🔴

| # | Problem | Why it's here |
| --- | --- | --- |
| 11 | LC 489 Robot Room Cleaner | Blind simulation + backtracking + direction array |
| 12 | LC 2069 Walking Robot Simulation II | Cycle detection in simulation |
| 13 | LC 224 Basic Calculator | Full expression parser simulation |

---

## 🧾 TL;DR

**The 7 building blocks:**

1. **Direction array** — `int[][] dirs = {{-1,0},{0,1},{1,0},{0,-1}}` in clockwise order. Turn right = `(cdir+1)%4`. Turn left = `(cdir+3)%4`.
2. **Boundary check** — `isValid(r, c, rows, cols)` — compute new position, check, then update.
3. **Command parsing** — `Integer.parseInt(cmd.substring(1))` for numbers. `"literal".equalsIgnoreCase(cmd)` for type. `else if` for dispatch.
4. **State modeling** — position, direction, accumulators. Ask: "where am I, which way, what have I collected, when do I stop?"
5. **Simulation loop skeleton** — init → loop commands → dispatch → step-by-step move → return.
6. **Cell processing helpers** — named methods for toll/obstacle/visited — keep the loop clean.
7. **Grid construction** — `grid[row][col]`, row increases downward, (0,0) = top-left.

**The mantra:** *"Turn changes direction only. Move changes position only. Process each cell individually. Parse before acting."*

**The anti-pattern that killed me:** Combining turn + move in one block, jumping N steps instead of looping 1 at a time, `charAt - '0'` on multi-digit numbers, sequential `if` instead of `else if`.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Java traps (the other half of implementation discipline) | `DSA/Implementation/java-coding-traps.md` |
| Java traps quick reference | `DSA/Implementation/java-coding-traps-reference.md` |
| Simulation patterns quick reference | `DSA/Implementation/simulation-patterns-reference.md` (companion) |
| BFS / level-order traversal | `DSA/DeepDive/graphs-fundamentals.md` |
| Integer overflow / modulo pitfalls | `DSA/DeepDive/integer-overflow-and-limits.md` |
| Morning interview cheatsheet | `DSA/Reference/interview-morning-cheatsheet.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** 7 building blocks (direction array, boundary check, command parsing, state modeling, simulation loop skeleton, cell processing helpers, grid construction). 5 simulation problem types. Full worked walkthrough of car-toll problem. 7 gotchas. 4 mental models. Triggered by the car-toll interview where every bug was an implementation mistake, not an algorithmic one. |
