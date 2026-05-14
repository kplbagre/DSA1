# Backtracking — Fundamentals (Deep Dive)

> Backtracking is recursion's "exploration mode." You build a partial solution one choice at a time, recurse to extend it, and **undo** the choice when the branch is exhausted so the next branch starts clean. That's the entire idea — but interview problems hide six distinct sub-patterns inside it. This doc teaches all six.

---

## 🎯 Why You're Reading This (The Goal)

Backtracking is the pattern that confuses people the most because of the "undo." Most interviewees get the structural shape but freeze when they see a problem like *"all permutations"* or *"N-Queens"* because the **choice space changes** between sub-patterns and the templates look different.

This doc fixes that.

By the end you should be able to:

1. **Look at any backtracking problem and identify which of the 6 sub-patterns it is** in under 60 seconds
2. **Apply the matching template** without re-deriving the structure each time
3. **Decide on the return type** (`void` / `boolean` / `int`) based on whether you need ALL / ONE / COUNT
4. **Add pruning** when the brute-force tree is too large
5. **Convert a "naive" backtracking solution to a constraint-driven one** with O(1) validity checks

**Companion files:**
- `DeepDive/recursion-fundamentals.md` — the prerequisite. Specifically, **Pattern 3 (Backtracking)** there has the foundation: TRY → RECURSE → UNDO recipe + Take/Not-Take + Subsequence Trilogy.
- `Reference/recursion-reference.md` (planned) — compact cheat sheet for daily revision.

> **Prerequisite check:** if you haven't read the Take/Not-Take + Subsequence Trilogy section in `recursion-fundamentals.md`, **stop and read that first.** This doc assumes you can write a take/not-take subset solution from memory.

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

Every problem in this doc is tagged so you know whether to attempt it **now** or **wait** until you've covered more material.

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point in the doc | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs concepts from a later section in this same doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc (DP optimization, advanced pruning) | Read the problem and editorial for awareness; don't attempt cold |

> **Same lesson as trees doc:** I (Kapil) burned an hour on LC 124 before I had two-purpose recursion in my head. **Don't attempt 🔴 cold.** The tags exist precisely so you don't repeat that mistake.

---

## 📖 Terminology (Memorize These)

| Term | Definition | Example |
| --- | --- | --- |
| **State** | The current "where am I" — usually an index, a partial path, plus any auxiliary data | `(ind, path, sum)` |
| **Choice** | One option available at the current state | "include `arr[ind]`", "place queen at column `c`" |
| **Choice space** | The full set of choices at this state | `{include, skip}` for take/not-take; `{0..n-1}` for permutations |
| **Partial result** | The mutable object that holds the in-progress answer | `path`, the chess board, the cut sequence |
| **Snapshot** | A deep copy of the partial result, recorded into the answer list | `new ArrayList<>(path)` |
| **Undo** | The mutation that reverses the most recent TRY before the next sibling branch | `path.remove(path.size() - 1)` |
| **Pruning** | Skipping a branch BEFORE recursing because it can't lead to a valid solution | `if (!isValid(...)) continue;` |
| **Visited / used** | A `boolean[]` or `Set` that marks elements no longer available | `used[i] = true` in permutations |
| **Constraint set** | A data structure giving O(1) validity checks (rows / cols / diagonals in N-Queens) | `Set<Integer> usedCols` |

---

## 🧠 The Universal Backtracking Recipe

> **Every backtracking problem follows the same shape.** The only thing that changes between sub-patterns is **what counts as a "choice"** and **what state you carry**.

### The 4 questions to answer before you write code

1. **What's the state?** — what parameters uniquely describe "where I am right now"?
2. **What are the choices?** — for this state, what options can I take next?
3. **When do I record an answer?** — at a leaf? on every visit? when a constraint is met?
4. **What do I undo?** — every mutation made in TRY must be reversed in UNDO.

Answer those four and you've solved the problem.

### The universal template

**Steps in plain English:**

1. **Termination check** — if the partial is complete (or always, for "every prefix is valid"), record / snapshot.
2. **Loop over choices** — every choice is one branch.
3. **Validity check (optional)** — `if (!isValid(state, choice)) continue;` to prune invalid branches.
4. **TRY** — apply the choice to `partial`.
5. **RECURSE** — descend with the advanced state.
6. **UNDO** — reverse the TRY mutation.

```java
void backtrack(state, partial, results) {
    // Step 1 — termination / record
    if (isComplete(state)) {
        results.add(snapshot(partial));
        return;
    }
    // Step 2 — branch on every choice
    for (choice in choices(state)) {
        // Step 3 — prune (optional)
        if (!isValid(state, choice)) {
            continue;
        }
        // Step 4 — TRY
        apply(partial, choice);
        // Step 5 — RECURSE
        backtrack(advance(state, choice), partial, results);
        // Step 6 — UNDO
        undo(partial, choice);
    }
}
```

> **Mental hook:** *"Backtracking = recursion + an undo step. The undo restores the world before the next sibling explores."*

---

## 🔑 The Decision Framework — Which Sub-Pattern Is This?

When you read a backtracking problem, scan for these signal phrases. They map directly to one of the six sub-patterns.

| Sub-pattern | Signal phrase in the problem | Choice space | Canonical LC |
| --- | --- | --- | --- |
| **1. Take/Not-Take** | *"all subsets"*, *"all subsequences"*, *"sum equals K"*, *"count of subsequences"* | Binary: include or skip | LC 78 (subsets), LC 416 |
| **2. For-loop "Pick Next"** | *"all combinations"*, *"no order needed"*, *"no duplicates"*, *"starting from..."* | Indices ≥ `start` | LC 39, LC 40, LC 77 |
| **3. Permutations** | *"all permutations"*, *"all orderings"*, *"order matters"* | Any unused element | LC 46, LC 47 |
| **4. Constraint-driven** | *"valid"*, *"satisfies"*, *"no two same row/col/diag"*, *"place N queens"* | All choices, but pruned by `isValid()` | LC 51, LC 37 |
| **5. Grid / 2D** | *"in a grid"*, *"find a path"*, *"adjacent / 4 directions"*, *"word in board"* | 4 directional moves | LC 79, LC 200 (DFS variant) |
| **6. Cut-points** | *"split string into..."*, *"all valid partitions"*, *"all valid IPs"*, *"cuts"* | Where to make the next cut | LC 131, LC 93 |

### Plus: the return-type variant (orthogonal to the 6 sub-patterns)

| Variant | When the problem asks for... | Return type | What changes |
| --- | --- | --- | --- |
| **ALL** | *"return all..."*, *"list every..."* | `void` (collect into list) | Snapshot at base case |
| **ONE** | *"is there any?"*, *"can we...?"*, *"find any one solution"* | `boolean` (short-circuit on success) | `if (recurse) return true;` |
| **COUNT** | *"how many?"*, *"count the number of..."* | `int` (sum the recursive calls) | `return take + notTake` |

> **Workflow:** identify (sub-pattern, return-variant) → apply matching template → fill in the work.

---

## 🪜 Sub-Pattern 1: Take/Not-Take

> **Already covered in `recursion-fundamentals.md` Pattern 3.1 + 3.2 (Subsequence Trilogy).** This section is a recap + the bridge to the rest of backtracking.

### Recap — the template

**Steps in plain English:**

1. **Base case** — when `ind == n`, every element has been decided; record / count.
2. **TAKE** — include `arr[ind]`, recurse, then UNDO.
3. **NOT-TAKE** — recurse without including.

```java
void f(int ind, int[] arr, int n, List<Integer> path, List<List<Integer>> ans) {
    if (ind == n) {
        ans.add(new ArrayList<>(path));
        return;
    }
    // TAKE
    path.add(arr[ind]);
    f(ind + 1, arr, n, path, ans);
    path.remove(path.size() - 1);
    // NOT-TAKE
    f(ind + 1, arr, n, path, ans);
}
```

### When to choose Take/Not-Take over For-loop

- The decision is **binary** (include / skip)
- Order is preserved (subsequences, NOT combinations)
- You need the **Trilogy variants** (boolean short-circuit, count)
- The problem mentions "subsequence" or "subset"

> 🧩 **Try these (recap from recursion doc):**
> - ✅ LC 78 Subsets — solve via take/not-take
> - ✅ "Print subsequences with sum K" — Trilogy ALL
> - ✅ "Print one subsequence with sum K" — Trilogy ONE
> - ✅ "Count subsequences with sum K" — Trilogy COUNT

---

## 🪜 Sub-Pattern 2: For-loop "Pick Next"

> When at each state you can pick **any of many** next options (not just include/skip), use a `for` loop over the choices. The `start` index parameter prevents revisits and duplicates.

### Mental model

> *"At this state, the next element to add can be any of `nums[start..n-1]`. Try each, recurse, undo."*

### Template — Steps in plain English

1. **Termination check** — snapshot when the partial is complete (or on every visit, depending on problem).
2. **Loop from `start`** — iterate over the remaining candidates.
3. **TRY** — append `nums[i]` to `path`.
4. **RECURSE** with `i + 1` (or `i` if reuse is allowed).
5. **UNDO** — remove last element.

```java
void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> results) {
    // Step 1 — record
    results.add(new ArrayList<>(path));
    // Step 2 — branch on every remaining candidate
    for (int i = start; i < nums.length; i++) {
        // Step 3 — TRY
        path.add(nums[i]);
        // Step 4 — RECURSE (next branch picks elements > i)
        backtrack(nums, i + 1, path, results);
        // Step 5 — UNDO
        path.remove(path.size() - 1);
    }
}
```

### Worked Example — LC 39 Combination Sum

> Given an array of distinct integers `candidates` and a target, return all unique combinations where the chosen numbers sum to target. **Each candidate may be reused.**

**Steps in plain English:**

1. **Public entry** — set up `results` and call backtrack with `start = 0`, empty `path`, full `target`.
2. **Base case (success)** — when `target == 0`, snapshot `path` into `results` and return.
3. **Base case (fail)** — when `target < 0` OR `start == candidates.length`, return without recording.
4. **Loop from `start`** — try every candidate from `start` onward.
5. **TRY** — add `candidates[i]` to path, subtract from target.
6. **RECURSE** with `i` (NOT `i + 1`) — same index allows reuse.
7. **UNDO** — remove last element from path.

```java
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    // Step 1 — set up
    List<List<Integer>> results = new ArrayList<>();
    backtrack(candidates, 0, target, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] cand, int start, int target,
                       List<Integer> path, List<List<Integer>> results) {
    // Step 2 — success base case
    if (target == 0) {
        results.add(new ArrayList<>(path));
        return;
    }
    // Step 3 — fail base case
    if (target < 0) {
        return;
    }
    // Step 4 — branch
    for (int i = start; i < cand.length; i++) {
        // Step 5 — TRY
        path.add(cand[i]);
        // Step 6 — RECURSE with `i` (not `i + 1`) for reuse
        backtrack(cand, i, target - cand[i], path, results);
        // Step 7 — UNDO
        path.remove(path.size() - 1);
    }
}
```

> **Why `start = i` (not `i + 1`)?** Because the problem allows the **same** element to be reused. If the problem said "each element used at most once," we'd use `i + 1`.

### Variant — LC 40 Combination Sum II (skip duplicates)

When the input has duplicates and you must NOT produce duplicate combinations, sort the input and skip same-value siblings:

```java
Arrays.sort(cand);  // sort to make duplicates adjacent
for (int i = start; i < cand.length; i++) {
    // Skip same-value siblings to avoid duplicate combinations
    if (i > start && cand[i] == cand[i - 1]) {
        continue;
    }
    path.add(cand[i]);
    backtrack(cand, i + 1, target - cand[i], path, results);   // i + 1: no reuse
    path.remove(path.size() - 1);
}
```

> **Mental hook for skip-duplicates:** *"At this branching level, only the FIRST occurrence of each value gets to start a branch. Same-valued siblings would produce the same subtree."*

### Take/Not-Take vs For-loop — the formal chooser

| Scenario | Pattern |
| --- | --- |
| Subsets (preserves order, all 2^n) | Take/Not-Take or For-loop, both work |
| Subsequences with sum K (Trilogy) | Take/Not-Take (cleaner) |
| Combinations of size K | For-loop with `start` |
| Combination Sum (reuse allowed) | For-loop with `start = i` |
| Combination Sum II (skip duplicates) | For-loop + sort + skip-sibling check |

> 🧩 **Try these:**
> - ✅ LC 39 Combination Sum (covered above)
> - ✅ LC 77 Combinations — pure for-loop with `start`
> - 🟡 LC 40 Combination Sum II (after LC 39) — skip-duplicates variant
> - 🟡 LC 22 Generate Parentheses (after LC 39) — for-loop with constraints (open/close counts)
> - 🟡 LC 17 Letter Combinations (after LC 22) — for-loop over a digit's letters

---

## 🪜 Sub-Pattern 3: Permutations

> **Why this isn't take/not-take or for-loop with `start`:** permutations need every element in **every position**. That's a fundamentally different choice space — at each level, the choice is *"which element haven't I used yet?"*

### Mental model

> *"Position by position, pick any unused element. Mark it used, recurse, then mark it unused (undo)."*

### Approach A — `boolean[] used` array

**Steps in plain English:**

1. **Base case** — when `path.size() == n`, snapshot and return.
2. **Loop over all indices** — for each `i` from `0` to `n - 1`...
3. **Skip used** — if `used[i]` is `true`, skip this branch.
4. **TRY** — mark `used[i] = true`, append `nums[i]` to `path`.
5. **RECURSE**.
6. **UNDO** — pop `path`, set `used[i] = false`.

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> results = new ArrayList<>();
    boolean[] used = new boolean[nums.length];
    backtrack(nums, used, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] nums, boolean[] used,
                       List<Integer> path, List<List<Integer>> results) {
    // Step 1 — base case: full permutation built
    if (path.size() == nums.length) {
        results.add(new ArrayList<>(path));
        return;
    }
    // Step 2 — loop over ALL indices (not from `start`)
    for (int i = 0; i < nums.length; i++) {
        // Step 3 — skip used
        if (used[i]) {
            continue;
        }
        // Step 4 — TRY
        used[i] = true;
        path.add(nums[i]);
        // Step 5 — RECURSE
        backtrack(nums, used, path, results);
        // Step 6 — UNDO (both mutations)
        path.remove(path.size() - 1);
        used[i] = false;
    }
}
```

### Walk through `nums = [1, 2, 3]`

```
                          backtrack(used=[F,F,F], path=[])
                /                 |                     \
       pick 0 (=1)         pick 1 (=2)             pick 2 (=3)
       used=[T,F,F]        used=[F,T,F]            used=[F,F,T]
       path=[1]            path=[2]                path=[3]
       /        \           /       \              /        \
   pick 1     pick 2     pick 0   pick 2        pick 0   pick 1
   path=[1,2] path=[1,3] path=[2,1] path=[2,3]  path=[3,1] path=[3,2]
     |          |          |         |             |         |
  pick 2     pick 1     pick 2    pick 0         pick 1    pick 0
   [1,2,3]   [1,3,2]    [2,1,3]   [2,3,1]       [3,1,2]   [3,2,1]
```

6 leaves = 3! permutations ✅

### Approach B — Swap-based (no extra `used[]` array)

**Steps in plain English:**

1. **Base case** — when the swap pointer `ind == n`, snapshot the array as a permutation.
2. **Loop from `ind` to `n - 1`** — for each `i`, swap `nums[ind]` with `nums[i]` (this fixes a candidate at position `ind`).
3. **RECURSE** with `ind + 1`.
4. **UNDO** — swap back.

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> results = new ArrayList<>();
    backtrack(nums, 0, results);
    return results;
}

private void backtrack(int[] nums, int ind, List<List<Integer>> results) {
    // Step 1 — base case
    if (ind == nums.length) {
        List<Integer> perm = new ArrayList<>();
        for (int x : nums) {
            perm.add(x);
        }
        results.add(perm);
        return;
    }
    // Step 2 — for each candidate, swap into position `ind`
    for (int i = ind; i < nums.length; i++) {
        // Swap
        swap(nums, ind, i);
        // Step 3 — RECURSE
        backtrack(nums, ind + 1, results);
        // Step 4 — UNDO swap
        swap(nums, ind, i);
    }
}

private void swap(int[] nums, int a, int b) {
    int tmp = nums[a];
    nums[a] = nums[b];
    nums[b] = tmp;
}
```

> **Trade-off:**
> - **Approach A** (used[]) is O(n) extra space but easier to understand and adapt to "with duplicates" (LC 47).
> - **Approach B** (swap) uses O(1) extra space (besides recursion stack) and is in-place. Harder to extend.
>
> **Use A in interviews** unless asked for in-place.

### Variant — LC 47 Permutations II (with duplicates)

Sort + skip same-value siblings, similar to LC 40:

```java
Arrays.sort(nums);
for (int i = 0; i < nums.length; i++) {
    if (used[i]) {
        continue;
    }
    // Skip if previous duplicate hasn't been used (avoids dup permutations)
    if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) {
        continue;
    }
    used[i] = true;
    path.add(nums[i]);
    backtrack(...);
    used[i] = false;
    path.remove(path.size() - 1);
}
```

> 🧩 **Try these:**
> - ✅ LC 46 Permutations — covered above (Approach A)
> - 🟡 LC 47 Permutations II (after LC 46) — sort + skip duplicates
> - 🟡 Permutations of a string with unique chars — substitute `char[]` for `int[]`

---

## 🪜 Sub-Pattern 4: Constraint-Driven (Pruning) — N-Queens

> When the choice space is large but most branches are invalid, **prune before recursing**. The validity check is the entire game.

### Mental model

> *"At each state, before trying a choice, ask: does this choice violate any constraint? If yes, skip it without recursing."*

### Build-up: LC 51 N-Queens — three iterations

We'll build it up: brute → with pruning → with O(1) constraint sets.

#### Iteration 1 — The naive structure (no pruning)

The board is `n×n`. Place one queen per row. For row `r`, try every column `c`.

**Steps in plain English:**

1. **Base case** — when `row == n`, all queens are placed. Snapshot the board.
2. **Loop columns** — for each column `c` in row `row`...
3. **TRY** — place a queen at `(row, c)`.
4. **RECURSE** to `row + 1`.
5. **UNDO** — remove the queen.

This generates `n^n` attempts — most of which are invalid. We need pruning.

#### Iteration 2 — Add `isValid()` pruning

Before placing at `(row, c)`, check: any queen already in column `c`? Any in the diagonals? If yes → skip.

**Steps in plain English:**

1. **Base case** — `row == n`, snapshot.
2. **Loop columns**.
3. **Validity check** — `if (!isSafe(board, row, c)) continue;` — prunes invalid branches.
4. **TRY** — place queen.
5. **RECURSE**.
6. **UNDO**.

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> results = new ArrayList<>();
    char[][] board = new char[n][n];
    for (char[] row : board) {
        Arrays.fill(row, '.');
    }
    backtrack(board, 0, n, results);
    return results;
}

private void backtrack(char[][] board, int row, int n, List<List<String>> results) {
    // Step 1 — base case
    if (row == n) {
        results.add(snapshot(board));
        return;
    }
    // Step 2 — loop columns
    for (int c = 0; c < n; c++) {
        // Step 3 — prune
        if (!isSafe(board, row, c, n)) {
            continue;
        }
        // Step 4 — TRY
        board[row][c] = 'Q';
        // Step 5 — RECURSE
        backtrack(board, row + 1, n, results);
        // Step 6 — UNDO
        board[row][c] = '.';
    }
}

private boolean isSafe(char[][] board, int row, int col, int n) {
    // Check column above
    for (int r = 0; r < row; r++) {
        if (board[r][col] == 'Q') {
            return false;
        }
    }
    // Check upper-left diagonal
    for (int r = row - 1, c = col - 1; r >= 0 && c >= 0; r--, c--) {
        if (board[r][c] == 'Q') {
            return false;
        }
    }
    // Check upper-right diagonal
    for (int r = row - 1, c = col + 1; r >= 0 && c < n; r--, c++) {
        if (board[r][c] == 'Q') {
            return false;
        }
    }
    return true;
}

private List<String> snapshot(char[][] board) {
    List<String> rows = new ArrayList<>();
    for (char[] row : board) {
        rows.add(new String(row));
    }
    return rows;
}
```

`isSafe()` is O(n) per call. Total: O(n!) recursion paths × O(n) check = acceptable for `n ≤ 9`.

#### Iteration 3 — O(1) validity with constraint sets

Replace the three loops in `isSafe()` with three `boolean[]` (or `Set<Integer>`) tracking what's used:

- `cols[c]` — column `c` already has a queen
- `diag1[row + col]` — the `\` diagonal (row + col is constant on it) has a queen
- `diag2[row - col + n - 1]` — the `/` diagonal (row - col is constant on it) has a queen, shifted to be non-negative

**Steps in plain English:**

1. **Base case** — `row == n`, snapshot.
2. **Loop columns**.
3. **O(1) validity** — `if (cols[c] || diag1[row+c] || diag2[row-c+n-1]) continue;`
4. **TRY** — set all three flags + place queen.
5. **RECURSE**.
6. **UNDO** — clear all three flags + remove queen.

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> results = new ArrayList<>();
    char[][] board = new char[n][n];
    for (char[] row : board) {
        Arrays.fill(row, '.');
    }
    boolean[] cols = new boolean[n];
    boolean[] diag1 = new boolean[2 * n - 1];   // row + col
    boolean[] diag2 = new boolean[2 * n - 1];   // row - col + n - 1
    backtrack(board, 0, n, cols, diag1, diag2, results);
    return results;
}

private void backtrack(char[][] board, int row, int n,
                       boolean[] cols, boolean[] diag1, boolean[] diag2,
                       List<List<String>> results) {
    // Step 1 — base case
    if (row == n) {
        results.add(snapshot(board));
        return;
    }
    // Step 2 — loop columns
    for (int c = 0; c < n; c++) {
        int d1 = row + c;
        int d2 = row - c + n - 1;
        // Step 3 — O(1) validity
        if (cols[c] || diag1[d1] || diag2[d2]) {
            continue;
        }
        // Step 4 — TRY
        board[row][c] = 'Q';
        cols[c] = true;
        diag1[d1] = true;
        diag2[d2] = true;
        // Step 5 — RECURSE
        backtrack(board, row + 1, n, cols, diag1, diag2, results);
        // Step 6 — UNDO (all four mutations)
        board[row][c] = '.';
        cols[c] = false;
        diag1[d1] = false;
        diag2[d2] = false;
    }
}
```

> **Why this is the canonical interview solution:** validity is O(1), the recursion tree is heavily pruned, and the structure generalizes to Sudoku (use 9 sets for rows / cols / 3×3 boxes).

> **Mental hook:** *"The constraint sets ARE the state of the partial solution. Adding a queen sets three bits; removing it clears three bits."*

### Diagonal index intuition

```
Board (4x4) — diag1 (row + col is constant):

(0,0)=0  (0,1)=1  (0,2)=2  (0,3)=3
(1,0)=1  (1,1)=2  (1,2)=3  (1,3)=4
(2,0)=2  (2,1)=3  (2,2)=4  (2,3)=5
(3,0)=3  (3,1)=4  (3,2)=5  (3,3)=6

→ 7 distinct diag1 indices = 2n - 1 ✅

diag2 (row - col + n - 1 is constant):

(0,0)=3  (0,1)=2  (0,2)=1  (0,3)=0
(1,0)=4  (1,1)=3  (1,2)=2  (1,3)=1
(2,0)=5  (2,1)=4  (2,2)=3  (2,3)=2
(3,0)=6  (3,1)=5  (3,2)=4  (3,3)=3

→ 7 distinct diag2 indices = 2n - 1 ✅
```

### Why this generalizes — LC 37 Sudoku Solver

Sudoku is the same constraint-driven pattern with **3 sets** (rows, columns, 3×3 boxes) instead of 3 (cols + 2 diagonals):

```java
boolean[][] rows = new boolean[9][10];   // rows[r][digit]
boolean[][] cols = new boolean[9][10];   // cols[c][digit]
boolean[][] boxes = new boolean[9][10];  // boxes[r/3 * 3 + c/3][digit]
```

For each empty cell, try digits 1-9; check `rows[r][d] || cols[c][d] || boxes[b][d]`; if safe, set + recurse + clear.

> 🧩 **Try these:**
> - 🟡 **After O(1) constraint sets click** — LC 51 N-Queens (covered above)
> - 🟡 LC 52 N-Queens II (count only, not enumerate — return `int`)
> - 🔴 LC 37 Sudoku Solver — same pattern, 3 constraint sets, find ONE solution (boolean return)
> - 🔴 M-Coloring Problem — same pattern with adjacency-based pruning

---

## 🪜 Sub-Pattern 5: Grid / 2D Backtracking — Word Search

> When the choice space is **4 directional moves on a grid**, you need a `visited` matrix and a directions array. Don't forget to undo `visited` on the way back.

### Mental model

> *"From this cell, try moving up/down/left/right. Mark the current cell visited, recurse, unmark on the way back."*

### Template — Steps in plain English

1. **Boundary + visited check** — if out of grid OR already visited OR character mismatch, return `false`.
2. **Base case (success)** — if we've matched all required characters, return `true`.
3. **Mark visited** — set `visited[r][c] = true` (or use a sentinel char in the grid).
4. **Try 4 directions** — recurse on `(r±1, c)` and `(r, c±1)`.
5. **Short-circuit on success** — if any direction returns `true`, return `true`.
6. **Unmark visited** — set `visited[r][c] = false` (the UNDO).

### Worked Example — LC 79 Word Search

> Given a 2D `board` of characters and a `word`, return `true` if the word can be constructed from sequentially adjacent cells (horizontal/vertical), without using the same cell twice.

**Steps in plain English:**

1. **Public entry** — for every cell, try starting from it. If any returns `true`, the word exists.
2. **Helper base cases** — if `ind == word.length()`, we matched everything → return `true`. If out of bounds or character mismatch → return `false`.
3. **Mark current cell** — overwrite with a sentinel like `'#'` (avoids needing a separate `visited` array).
4. **Try 4 directions** — recurse with `ind + 1`.
5. **Short-circuit** — if any direction returns `true`, propagate.
6. **Restore the cell** — write the original character back (UNDO).

```java
public boolean exist(char[][] board, String word) {
    // Step 1 — try starting from each cell
    int rows = board.length;
    int cols = board[0].length;
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (dfs(board, word, r, c, 0)) {
                return true;
            }
        }
    }
    return false;
}

private boolean dfs(char[][] board, String word, int r, int c, int ind) {
    // Step 2a — success base case
    if (ind == word.length()) {
        return true;
    }
    // Step 2b — fail base cases (bounds + mismatch)
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length
            || board[r][c] != word.charAt(ind)) {
        return false;
    }
    // Step 3 — mark visited via sentinel
    char saved = board[r][c];
    board[r][c] = '#';
    // Step 4 — try 4 directions
    boolean found = dfs(board, word, r + 1, c, ind + 1)
                 || dfs(board, word, r - 1, c, ind + 1)
                 || dfs(board, word, r, c + 1, ind + 1)
                 || dfs(board, word, r, c - 1, ind + 1);
    // Step 6 — UNDO: restore the cell
    board[r][c] = saved;
    // Step 5 — propagate result
    return found;
}
```

> **The sentinel trick** (overwriting `board[r][c] = '#'`) avoids allocating a separate `boolean[][] visited`. Just remember to restore it on the way back — that's the undo.

### Directions array idiom (cleaner for "4 directions")

When you write the 4 directions explicitly four times, it's fine but verbose. The idiomatic version uses an arrays of `(dr, dc)` deltas:

```java
private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

// inside dfs:
boolean found = false;
for (int[] d : DIRS) {
    if (dfs(board, word, r + d[0], c + d[1], ind + 1)) {
        found = true;
        break;
    }
}
```

> **When to prefer explicit:** small grids, clarity over compactness. **When to prefer DIRS array:** 8 directions (chess knight), repeated grid traversals across many problems.

### Variant — LC 200 Number of Islands (DFS variant)

Same template, just count connected components of `'1'`s. Mark visited cells and recurse on neighbors:

```java
public int numIslands(char[][] grid) {
    int count = 0;
    for (int r = 0; r < grid.length; r++) {
        for (int c = 0; c < grid[0].length; c++) {
            if (grid[r][c] == '1') {
                count++;
                dfs(grid, r, c);   // sink the island
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c) {
    if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] != '1') {
        return;
    }
    grid[r][c] = '0';   // mark visited (mutate the input)
    dfs(grid, r + 1, c);
    dfs(grid, r - 1, c);
    dfs(grid, r, c + 1);
    dfs(grid, r, c - 1);
    // Note: NO undo here — we WANT to permanently mark it visited
}
```

> **Subtle distinction:** Word Search needs the undo because each path is independent. Number of Islands does NOT undo because we're flooding (permanently marking) one connected component.

> 🧩 **Try these:**
> - ✅ LC 79 Word Search (covered above)
> - ✅ LC 200 Number of Islands — DFS without undo
> - 🟡 LC 130 Surrounded Regions (after LC 200) — flood from boundary
> - 🟡 LC 695 Max Area of Island — DFS that returns size
> - 🔴 LC 212 Word Search II — needs Trie + backtracking

---

## 🪜 Sub-Pattern 6: Cut-Points / Partition — Palindrome Partitioning

> When the problem is *"split this string into pieces where each piece satisfies P"*, the **choice at each step is where to make the next cut**, not which element to take.

### Mental model

> *"Starting at index `start`, the next cut can be at any position `end > start`. If `s[start..end]` is valid, recurse on the suffix starting at `end + 1`."*

### Template — Steps in plain English

1. **Base case** — when `start == s.length()`, we've consumed the whole string; snapshot the path of pieces.
2. **Loop over end positions** — for each `end` from `start` to `n - 1`...
3. **Validity check** — is `s[start..end]` a valid piece? If not, skip.
4. **TRY** — append the substring to `path`.
5. **RECURSE** with `start = end + 1`.
6. **UNDO** — remove last substring.

### Worked Example — LC 131 Palindrome Partitioning

> Return all ways to partition `s` such that every substring is a palindrome.

**Steps in plain English:**

1. **Public entry** — set up `results` and call `backtrack(s, 0, ...)`.
2. **Base case** — when `start == s.length()`, snapshot `path` into `results`.
3. **Loop end** — try every `end` from `start` to `n - 1`.
4. **Palindrome check** — `if (!isPalindrome(s, start, end)) continue;`
5. **TRY** — add the substring `s[start..end]` to `path`.
6. **RECURSE** with `start = end + 1`.
7. **UNDO** — remove the substring.

```java
public List<List<String>> partition(String s) {
    // Step 1 — set up
    List<List<String>> results = new ArrayList<>();
    backtrack(s, 0, new ArrayList<>(), results);
    return results;
}

private void backtrack(String s, int start, List<String> path, List<List<String>> results) {
    // Step 2 — base case: consumed entire string
    if (start == s.length()) {
        results.add(new ArrayList<>(path));
        return;
    }
    // Step 3 — loop over possible end positions
    for (int end = start; end < s.length(); end++) {
        // Step 4 — prune non-palindromes
        if (!isPalindrome(s, start, end)) {
            continue;
        }
        // Step 5 — TRY: add the palindromic substring
        path.add(s.substring(start, end + 1));
        // Step 6 — RECURSE on the suffix
        backtrack(s, end + 1, path, results);
        // Step 7 — UNDO
        path.remove(path.size() - 1);
    }
}

private boolean isPalindrome(String s, int l, int r) {
    while (l < r) {
        if (s.charAt(l) != s.charAt(r)) {
            return false;
        }
        l++;
        r--;
    }
    return true;
}
```

### Walk through `s = "aab"`

```
backtrack(start=0, path=[])
  end=0 → "a" palindrome ✅
    backtrack(start=1, path=["a"])
      end=1 → "a" palindrome ✅
        backtrack(start=2, path=["a","a"])
          end=2 → "b" palindrome ✅
            backtrack(start=3, path=["a","a","b"])
              base → record ["a","a","b"]
      end=2 → "ab" NOT palindrome → skip
  end=1 → "aa" palindrome ✅
    backtrack(start=2, path=["aa"])
      end=2 → "b" palindrome ✅
        backtrack(start=3, path=["aa","b"])
          base → record ["aa","b"]
  end=2 → "aab" NOT palindrome → skip

Result: [["a","a","b"], ["aa","b"]]
```

### Variant — LC 93 Restore IP Addresses

Same cut-point pattern, but with constraints:
- Exactly 4 pieces
- Each piece is a number 0-255
- No leading zeros (unless the piece is exactly "0")

**Steps in plain English:**

1. **Base case (success)** — `start == s.length() && partsCount == 4` → join with dots and record.
2. **Base case (fail)** — `partsCount == 4 && start < s.length()` OR `start == s.length() && partsCount < 4` → return.
3. **Loop end** — try cuts of length 1, 2, 3 (`end = start` to `start + 2`).
4. **Validity** — leading-zero check, range 0-255 check.
5. **TRY / RECURSE / UNDO** as usual.

```java
public List<String> restoreIpAddresses(String s) {
    List<String> results = new ArrayList<>();
    backtrack(s, 0, new ArrayList<>(), results);
    return results;
}

private void backtrack(String s, int start, List<String> path, List<String> results) {
    // Step 1 — success
    if (start == s.length() && path.size() == 4) {
        results.add(String.join(".", path));
        return;
    }
    // Step 2 — fail (overshot or undershot)
    if (path.size() == 4 || start == s.length()) {
        return;
    }
    // Step 3 — try lengths 1, 2, 3
    for (int len = 1; len <= 3 && start + len <= s.length(); len++) {
        String piece = s.substring(start, start + len);
        // Step 4 — leading-zero + range check
        if (piece.length() > 1 && piece.charAt(0) == '0') {
            continue;
        }
        if (Integer.parseInt(piece) > 255) {
            continue;
        }
        // Step 5 — TRY
        path.add(piece);
        // Step 6 — RECURSE
        backtrack(s, start + len, path, results);
        // Step 7 — UNDO
        path.remove(path.size() - 1);
    }
}
```

> 🧩 **Try these:**
> - ✅ LC 131 Palindrome Partitioning (covered above)
> - 🟡 LC 93 Restore IP Addresses (after LC 131) — cut-points with constraints
> - 🟡 LC 282 Expression Add Operators — cut-points + arithmetic state (advanced)
> - 🔴 LC 140 Word Break II — cut-points + dictionary lookup (often needs memoization)

---

## 🎨 Style Habits — Build These From Day 1

> Some habits apply to **every backtracking problem**. Others matter only in specific sub-patterns. **Master the universal ones first**, then internalize context-specific ones as you encounter them.

### 🌐 Universal Habits

#### Habit 1 — Always undo every TRY mutation

```java
path.add(x);            // TRY
backtrack(...);         // RECURSE
path.remove(...);       // UNDO ← required
```

If you mutate `n` things, you must undo all `n`. Missing one → branches contaminate each other → wrong-but-plausible-looking output.

#### Habit 2 — Snapshot when recording, never store the reference

```java
// ❌ stores the live reference; results all point to the same final list
results.add(path);

// ✅ deep copy
results.add(new ArrayList<>(path));
```

#### Habit 3 — Return type tells you what kind of problem this is

| Problem says... | Return type |
| --- | --- |
| "return all..." | `void` (collect into list parameter) |
| "find any solution" / "is it possible?" | `boolean` (short-circuit on success) |
| "how many ways?" | `int` (sum the recursive calls) |

Pick before you write code.

#### Habit 4 — Pass mutable state as parameters, not via globals

```java
// ✅ explicit, easy to reason about
void backtrack(int idx, List<Integer> path, List<List<Integer>> results) { ... }

// ⚠️ implicit
private List<List<Integer>> results;
void backtrack(int idx, List<Integer> path) { ... }
```

The instance-field version is fine if you reset it in the public entry point.

#### Habit 5 — Don't use `static` for problem state on LeetCode

Same trap as in trees/recursion docs — `static` persists across test cases on LeetCode's grader. Use **instance fields** and reset in the entry method.

---

### 🔧 Context-Specific Habits

#### Habit 6 — In permutations, undo BOTH `path` and `used[]`

```java
used[i] = true;
path.add(nums[i]);
backtrack(...);
path.remove(path.size() - 1);
used[i] = false;             // ← don't forget this one
```

Two mutations means two undos. Missing the `used[i] = false` makes elements appear "permanently used" after one branch.

#### Habit 7 — In constraint-driven (N-Queens), undo every constraint set

```java
cols[c] = true;
diag1[d1] = true;
diag2[d2] = true;
backtrack(...);
cols[c] = false;             // 3 sets, 3 undos
diag1[d1] = false;
diag2[d2] = false;
```

#### Habit 8 — In grid backtracking, decide upfront: undo or no undo?

```java
// Word Search — UNDO (each path is independent)
board[r][c] = '#';
dfs(...);
board[r][c] = saved;

// Number of Islands — NO UNDO (we're flooding a component)
grid[r][c] = '0';
dfs(...);
// don't restore
```

The decision depends on whether the problem wants you to revisit cells across different branches.

#### Habit 9 — In cut-points, always advance to `end + 1` (or `start + len`)

```java
// ✅ advance past the just-added piece
backtrack(s, end + 1, path, results);

// ❌ infinite recursion (same start)
backtrack(s, start, path, results);
```

#### Habit 10 — Sort + skip-duplicate trick for "no duplicate combinations"

```java
Arrays.sort(nums);
for (int i = start; i < nums.length; i++) {
    if (i > start && nums[i] == nums[i - 1]) {
        continue;  // skip same-value siblings at the same level
    }
    // ... TRY / RECURSE / UNDO
}
```

This converts "Combination Sum" (LC 39) into "Combination Sum II" (LC 40), and "Permutations" (LC 46) into "Permutations II" (LC 47).

---

## 🐞 Common Bugs (Hall of Fame)

### Bug 1 — Forgetting the undo

```java
// ❌
path.add(nums[i]);
backtrack(...);
// (missing path.remove)

// ✅
path.add(nums[i]);
backtrack(...);
path.remove(path.size() - 1);
```

**Symptom:** results contain growing-and-growing partial paths that look "almost right" on small tests.

---

### Bug 2 — Storing the reference instead of a snapshot

```java
// ❌ all entries point to the same (final) list
results.add(path);

// ✅
results.add(new ArrayList<>(path));
```

**Symptom:** at the end, `results` is full of identical (often empty) lists.

> **Why this happens:** the `path` list is **one heap object** shared across every recursive frame. Storing the raw reference means every entry in `results` is the same pointer; once recursion finishes undoing, that single object is empty.
>
> Full conceptual explanation in **`DeepDive/recursion-fundamentals.md` → 🧬 Stack vs Heap — How Recursion Shares State Across Frames**. Read it once if "why are all my answers empty?" feels mysterious.

---

### Bug 3 — Wrong index advance for "reuse vs no-reuse"

```java
// LC 39 — reuse allowed → recurse with i (NOT i + 1)
backtrack(cand, i, target - cand[i], path, results);

// LC 40 — no reuse → recurse with i + 1
backtrack(cand, i + 1, target - cand[i], path, results);
```

**Symptom:** missing combinations (no reuse → only single-use sets) or duplicates (with reuse on a no-reuse problem).

---

### Bug 4 — In permutations, looping from `start` instead of from `0`

```java
// ❌ for permutations — would only generate combinations
for (int i = start; i < nums.length; i++) { ... }

// ✅ for permutations — every position considers every unused element
for (int i = 0; i < nums.length; i++) {
    if (used[i]) continue;
    ...
}
```

**Symptom:** for `[1,2,3]`, you get only `[1,2,3]` (1 result) instead of all 6.

---

### Bug 5 — Forgetting to undo the `visited` cell in grid backtracking

```java
// ❌ mark visited but never restore — cells stay forbidden across branches
board[r][c] = '#';
dfs(...);
// (missing restore)

// ✅
board[r][c] = '#';
dfs(...);
board[r][c] = saved;
```

**Symptom:** Word Search returns `false` for valid words because previously-explored cells remain blocked.

---

### Bug 6 — Pruning too early (before the recursive call is even tried)

```java
// ❌ prune before snapshotting valid prefix
for (int i = 0; i < n; i++) {
    if (someCondition) return;       // wrong — `return` ends the entire call
    ...
}

// ✅ skip just this branch, keep trying others
for (int i = 0; i < n; i++) {
    if (someCondition) continue;     // skip this choice, try next
    ...
}
```

`return` exits the call entirely; `continue` only skips the current iteration. Pick correctly.

---

### Bug 7 — Wrong return propagation in "find ONE" problems

```java
// ❌ ignores the recursive call's result
for (int i = 0; ...) {
    apply(i);
    backtrack(...);                  // result thrown away
    undo(i);
}
return false;                        // always returns false!

// ✅ short-circuit on success
for (int i = 0; ...) {
    apply(i);
    if (backtrack(...)) {
        return true;                 // propagate success
    }
    undo(i);
}
return false;                        // tried everything, none worked
```

**Symptom:** "find ONE" problems always return `false` even when a solution exists.

---

### Bug 8 — Not handling base case before pruning (or vice versa)

```java
// ❌ prune before checking if we've succeeded
for (int i = 0; ...) {
    if (i too far) return;
}
if (start == n) record();            // dead — already returned

// ✅ check the success base case FIRST
if (start == n) {
    record();
    return;
}
for (int i = 0; ...) { ... }
```

Always: **success base case → fail base case → pruned loop → recurse**. In that order.

---

## 🔬 Worked Walkthroughs

### Walkthrough 1: LC 78 Subsets — Two Approaches Side-by-Side

> Show that take/not-take and for-loop both produce all 2^n subsets.

**Approach A — Take/Not-Take:**

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> ans = new ArrayList<>();
    f(0, nums, new ArrayList<>(), ans);
    return ans;
}

private void f(int ind, int[] nums, List<Integer> path, List<List<Integer>> ans) {
    if (ind == nums.length) {
        ans.add(new ArrayList<>(path));
        return;
    }
    // TAKE
    path.add(nums[ind]);
    f(ind + 1, nums, path, ans);
    path.remove(path.size() - 1);
    // NOT-TAKE
    f(ind + 1, nums, path, ans);
}
```

**Approach B — For-loop with `start`:**

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> ans = new ArrayList<>();
    backtrack(0, nums, new ArrayList<>(), ans);
    return ans;
}

private void backtrack(int start, int[] nums, List<Integer> path, List<List<Integer>> ans) {
    ans.add(new ArrayList<>(path));   // every state is a valid subset
    for (int i = start; i < nums.length; i++) {
        path.add(nums[i]);
        backtrack(i + 1, nums, path, ans);
        path.remove(path.size() - 1);
    }
}
```

**Both produce the same `2^n` subsets**, just in different order. Master both.

---

### Walkthrough 2: LC 46 Permutations — Used[] Approach

> All permutations of `[1, 2, 3]` → 6 results.

**Steps in plain English:**

1. **Public entry** — set up `results` and `used` array, kick off recursion.
2. **Base case** — `path.size() == n` → snapshot.
3. **Loop ALL indices** (not from `start`).
4. **Skip used** — `if (used[i]) continue;`
5. **TRY** — set `used[i] = true`, append `nums[i]`.
6. **RECURSE**.
7. **UNDO BOTH** — pop `path`, set `used[i] = false`.

```java
public List<List<Integer>> permute(int[] nums) {
    // Step 1
    List<List<Integer>> results = new ArrayList<>();
    boolean[] used = new boolean[nums.length];
    backtrack(nums, used, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] nums, boolean[] used,
                       List<Integer> path, List<List<Integer>> results) {
    // Step 2 — base case
    if (path.size() == nums.length) {
        results.add(new ArrayList<>(path));
        return;
    }
    // Step 3 — loop ALL
    for (int i = 0; i < nums.length; i++) {
        // Step 4 — skip used
        if (used[i]) {
            continue;
        }
        // Step 5 — TRY
        used[i] = true;
        path.add(nums[i]);
        // Step 6 — RECURSE
        backtrack(nums, used, path, results);
        // Step 7 — UNDO BOTH
        path.remove(path.size() - 1);
        used[i] = false;
    }
}
```

> **Key takeaway:** the `used[]` array IS the partial state. Two mutations (`used[i] = true` AND `path.add(...)`) must be paired with two undos.

---

### Walkthrough 3: LC 51 N-Queens — Full Build-Up

> Place `n` queens on an `n×n` board such that no two attack each other.

**Steps in plain English (final O(1)-validity version):**

1. **Public entry** — initialize empty board (filled with `'.'`) + 3 boolean arrays for cols / diag1 / diag2.
2. **Base case** — `row == n`, all queens placed → snapshot the board into `results`.
3. **Loop columns** for the current `row`.
4. **O(1) validity check** — `if (cols[c] || diag1[row+c] || diag2[row-c+n-1]) continue;`
5. **TRY** — place `'Q'` at `(row, c)` and set the 3 constraint flags.
6. **RECURSE** to `row + 1`.
7. **UNDO** — clear all 4 mutations.

(Full code is in Sub-Pattern 4 above — Iteration 3.)

**Key insight:** the **constraint sets** are the entire state of the partial solution beyond the board itself. Setting/clearing them is what makes pruning O(1).

---

## 🗺️ Practice Plan — Tier-by-Tier

Don't try to solve all of these in one sitting. Spread over 1–2 weeks. Time-box each at 25 minutes.

> **Reminder of tags:** ✅ Try Now · 🟡 Try after the named prerequisite · 🔴 Reference Only

---

### Tier 1 — Take/Not-Take + Subsequence Trilogy (foundation)

**Prerequisite:** read `recursion-fundamentals.md` Pattern 3.1 + 3.2.

1. ✅ **LC 78 Subsets** — both take/not-take AND for-loop
2. ✅ "Print all subsequences with sum K" — Trilogy ALL
3. ✅ "Print one subsequence with sum K" — Trilogy ONE
4. ✅ "Count subsequences with sum K" — Trilogy COUNT
5. 🟡 **LC 416 Partition Equal Subset Sum** — Trilogy ONE variant on subset sum

---

### Tier 2 — For-loop "Pick Next"

6. ✅ **LC 77 Combinations** — pure for-loop with `start`
7. ✅ **LC 39 Combination Sum** — for-loop with reuse (`start = i`)
8. 🟡 **LC 40 Combination Sum II** (after LC 39) — sort + skip-duplicates
9. 🟡 **LC 22 Generate Parentheses** (after LC 39) — for-loop with constraints
10. 🟡 **LC 17 Letter Combinations** (after LC 22) — for-loop over digit's letters

---

### Tier 3 — Permutations

11. ✅ **LC 46 Permutations** — `used[]` approach
12. 🟡 **LC 47 Permutations II** (after LC 46) — sort + skip duplicates trick

---

### Tier 4 — Cut-Points

13. ✅ **LC 131 Palindrome Partitioning** — canonical cut-point
14. 🟡 **LC 93 Restore IP Addresses** (after LC 131) — cut-points with constraints

---

### Tier 5 — Grid / 2D

15. ✅ **LC 79 Word Search** — sentinel-based visited, 4 directions
16. ✅ **LC 200 Number of Islands** — flood DFS without undo
17. 🟡 **LC 130 Surrounded Regions** (after LC 200) — flood from boundary
18. 🟡 **LC 695 Max Area of Island** — DFS that returns size

---

### Tier 6 — Constraint-Driven (Reference Only or after deep practice)

19. 🟡 **LC 51 N-Queens** — only after Tiers 1-5 click. Build up brute → with pruning → with O(1) sets in three drafts
20. 🔴 **LC 52 N-Queens II** — count only (return int)
21. 🔴 **LC 37 Sudoku Solver** — same constraint pattern, 3 sets, find ONE
22. 🔴 **LC 282 Expression Add Operators** — cut-points + arithmetic state
23. 🔴 **LC 212 Word Search II** — Trie + grid backtracking (advanced)

---

### How to use this plan

- **Pace:** 2–3 problems/day for ~10 days clears Tiers 1–4.
- **When stuck:** time-box at 25 minutes. If still stuck, read the editorial, **don't accept-paste** — close it and rewrite from understanding.
- **Revision:** after finishing a tier, redo problem 1 from that tier from memory before moving on.
- **Tier order matters more than speed.** Don't be tempted by LC 51 ("N-Queens looks cool!") before Tiers 1-5. The lessons only land in order.

> **Lesson learned the hard way (May 2026):** I attempted LC 124 (in trees doc) before completing the bottom-up DFS + two-purpose recursion ladder. It cost me an hour. **Same risk applies here — climb tiers in order.**

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

**Forgetting `Arrays.sort()` before skip-duplicate trick.**

```java
// ❌
for (int i = start; i < nums.length; i++) {
    if (i > start && nums[i] == nums[i - 1]) continue;   // doesn't work without sorting
}

// ✅
Arrays.sort(nums);
for (int i = start; i < nums.length; i++) {
    if (i > start && nums[i] == nums[i - 1]) continue;
}
```

The skip-duplicates trick only works on sorted input — duplicates must be adjacent.

---

**Modifying a parameter after recording it.**

```java
// ❌
results.add(path);                            // shared reference
path.add(nums[i]);                            // results.get(0) just changed!

// ✅
results.add(new ArrayList<>(path));           // deep copy
path.add(nums[i]);
```

---

**Confusing `return` with `continue` in pruning.**

```java
// ❌ this exits the entire call after seeing one bad choice
for (int c = 0; c < n; c++) {
    if (!isSafe(...)) return;   // wrong — abandons remaining columns
}

// ✅
for (int c = 0; c < n; c++) {
    if (!isSafe(...)) continue;  // skip this column, try the next
}
```

---

**Off-by-one on the cut-points loop bound.**

```java
// ❌ loop condition allows overshooting the string
for (int end = start; end <= s.length(); end++) {     // out of bounds
    s.substring(start, end + 1);   // crash when end == s.length()
}

// ✅
for (int end = start; end < s.length(); end++) {
    s.substring(start, end + 1);
}
```

---

**For permutations, mistakenly using `start` instead of `0` in the loop.**

```java
// ❌ generates combinations, not permutations
for (int i = start; i < nums.length; i++) { ... }

// ✅
for (int i = 0; i < nums.length; i++) {
    if (used[i]) continue;
    ...
}
```

The whole point of permutations is that every position considers EVERY unused element, not just the ones "after" the previous index.

---

**Passing the recursion's result through a successful branch but forgetting to short-circuit.**

```java
// ❌
for (int i = 0; i < n; i++) {
    backtrack(...);    // result discarded
}
return false;          // always false

// ✅
for (int i = 0; i < n; i++) {
    if (backtrack(...)) return true;
}
return false;
```

For "find ONE" problems, propagate `true` the moment you have it.

---

## 🧾 TL;DR — One-Page Summary

- **Backtracking** = recursion + an undo step that restores state before the next sibling branch
- **The 4 questions** to ask before coding: state, choices, when to record, what to undo
- **6 sub-patterns** distinguished by *what counts as a "choice"*:
  1. **Take/Not-Take** — binary (include/skip)
  2. **For-loop "Pick Next"** — iterate over candidates ≥ `start`
  3. **Permutations** — any unused element, with `boolean[] used` or swap
  4. **Constraint-driven** — all choices but pruned by `isValid()` (N-Queens, Sudoku)
  5. **Grid / 2D** — 4 directional moves, mark visited, undo on the way back
  6. **Cut-points / Partition** — choose where to cut (Palindrome Partitioning, IP)
- **Return-type variants** (orthogonal to sub-patterns): ALL (`void`), ONE (`boolean`), COUNT (`int`)
- **Always undo** every mutation made in TRY — failure to undo = contaminated branches
- **Always snapshot** when recording — `new ArrayList<>(path)`, not `path`
- **Pruning matters** — `if (!isValid(...)) continue;` saves exponential work in N-Queens / Sudoku
- **Tier 1 you must master:** LC 78 (both ways), Subsequence Trilogy ALL/ONE/COUNT
- **Most "wrong answer" bugs** = forgot the undo, stored a reference instead of a snapshot, or used `return` instead of `continue` for pruning

> **Backtracking is the spine of constraint-satisfaction problems.** The hours you put in here pay back across LC's "Hard" tier — N-Queens, Sudoku, Word Search II, and beyond.
