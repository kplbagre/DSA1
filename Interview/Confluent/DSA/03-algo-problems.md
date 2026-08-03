# Confluent DSA — Algorithm Problems

> **Format:** Follow `_format.md` in this folder.
>
> **What's in this file:** Pure algorithm problems — backtracking, DP, prefix sums, parsing. These are the "solve this on a whiteboard" problems, not "build a component."

---

## Table of Contents

| # | Problem | Tier | LC # | Difficulty | Status |
|---|---|---|---|---|---|
| 1 | [Valid Sudoku](#1-valid-sudoku) | ⭐ Tier 1 | LC 36 | Medium | [ ] |
| 2 | [Sudoku Solver](#2-sudoku-solver) | ⭐ Tier 1 | LC 37 | Hard | [ ] |
| 3 | [Wildcard Matching](#3-wildcard-matching) | ⭐ Tier 1 | LC 44 | Hard | [ ] |
| 4 | [Regular Expression Matching](#4-regular-expression-matching) | ⭐ Tier 1 | LC 10 | Hard | [ ] |
| 5 | [Number of Atoms](#5-number-of-atoms) | Tier 3 | LC 726 | Hard | [ ] |
| 6 | [Ways to Make a Fair Array](#6-ways-to-make-a-fair-array) | Tier 3 | LC 1664 | Medium | [ ] |
| 7 | [Merge Strings Alternately](#7-merge-strings-alternately) | Tier 3 | LC 1768 | Easy | [ ] |
| 8 | [Identify the Largest Outlier](#8-identify-the-largest-outlier) | Tier 3 | LC 3371 | Medium | [ ] |

---

## 1. Valid Sudoku

### **🎤 How It's Asked:**

> "Given a 9×9 board, determine if it's a valid Sudoku. Only filled cells need to be validated — rows, columns, and 3×3 boxes must not have duplicates."
>
> Often asked as Part 1 before "Now solve it" (LC 37).

### **Discussion — How to arrive at the solution:**

First instinct: for each row, check for duplicates. For each column, check for duplicates. For each 3×3 box, check for duplicates. That's 3 separate passes. Can we do it in one pass?

Yes — use 3 sets of HashSets: one per row (9 sets), one per column (9 sets), one per box (9 sets). Iterate the board once. For each non-empty cell, check all three sets. If any set already contains the value, it's invalid. Otherwise, add the value to all three sets.

The key trick: computing which 3×3 box a cell belongs to. For cell `(r, c)`, box index = `(r / 3) * 3 + (c / 3)`. This maps 9 boxes to indices 0-8.

### **Brute Force:**

- Three separate passes: one for rows, one for columns, one for boxes. Each pass uses a HashSet to detect duplicates.
- **Time:** O(81) = O(1) — board is fixed 9×9 — WHY: three passes over 81 cells = 243 operations, but board size is constant.
- **Space:** O(81) = O(1) — 27 HashSets, each with at most 9 entries.

### **Key Insight:**

You don't need three separate passes. One pass through all 81 cells is enough — maintain 9 row-sets, 9 column-sets, and 9 box-sets simultaneously. For each cell, check and insert into all three. The box index formula `(r / 3) * 3 + (c / 3)` maps every cell to its box in O(1).

Since the board is always 9×9, this problem is technically O(1) time and space. But the approach generalizes to N×N boards where N = k².

### **Optimal Solution:**

**Steps in plain English:**

1. **Create tracking sets** — 9 sets for rows, 9 for columns, 9 for boxes.
2. **Iterate every cell** — skip empty cells (`.`).
3. **Compute box index** — `(r / 3) * 3 + (c / 3)` gives 0-8.
4. **Check all three sets** — if value already in row-set OR col-set OR box-set → invalid.
5. **Add to all three sets** — if not duplicate, record the value.

```java
public boolean isValidSudoku(char[][] board) {
    // Step 1 — tracking sets: rows[i], cols[j], boxes[k]
    Set<Character>[] rows = new HashSet[9];
    Set<Character>[] cols = new HashSet[9];
    Set<Character>[] boxes = new HashSet[9];
    for (int i = 0; i < 9; i++) {
        rows[i] = new HashSet<>();
        cols[i] = new HashSet<>();
        boxes[i] = new HashSet<>();
    }

    // Step 2 — iterate every cell
    for (int r = 0; r < 9; r++) {
        for (int c = 0; c < 9; c++) {
            char val = board[r][c];
            if (val == '.') {
                continue;
            }

            // Step 3 — compute box index
            int boxIdx = (r / 3) * 3 + (c / 3);

            // Step 4 — check all three sets for duplicate
            if (rows[r].contains(val) || cols[c].contains(val) || boxes[boxIdx].contains(val)) {
                return false;
            }

            // Step 5 — add to all three sets
            rows[r].add(val);
            cols[c].add(val);
            boxes[boxIdx].add(val);
        }
    }
    return true;
}
```

- **Time:** O(1) — fixed 81 cells, each processed in O(1) with HashSet. For generalized N×N: O(N²).
- **Space:** O(1) — 27 sets with at most 9 entries each = 243 entries max. For generalized N×N: O(N²).

### **🔄 Variants:**

- "Now solve it" → LC 37 (next problem)
- "What if the board is 16×16?" → Same approach, box size is 4×4, box index = `(r / 4) * 4 + (c / 4)`
- "Use bit manipulation instead of HashSets" → Use `int` as a bitmask: `rows[r] |= (1 << val)`. Check: `(rows[r] & (1 << val)) != 0`. Saves memory.

### **❓ Cross-Questions:**

- **"Why `(r / 3) * 3 + (c / 3)` for box index?"** → Integer division `r / 3` gives the box row (0, 1, 2). Multiplying by 3 and adding `c / 3` gives a unique index 0-8. Box 0 = top-left, box 8 = bottom-right.
- **"Can you validate in a streaming fashion (cells arriving one at a time)?"** → Yes — maintain the same 27 sets persistently. Each new cell is one check + one insert. O(1) per cell.

---

## 2. Sudoku Solver

### **🎤 How It's Asked:**

> "Write a program to solve a Sudoku puzzle by filling the empty cells. Assume a unique solution exists."
>
> Almost always asked as Part 2 after LC 36 (Valid Sudoku).

### **Discussion — How to arrive at the solution:**

This is classic backtracking. Find the first empty cell, try digits 1-9, check if valid (using the same row/col/box check from LC 36), recurse to the next empty cell. If no digit works, backtrack (undo the placement and try the next digit).

The brute force IS the standard approach — backtracking with constraint checking. Optimization comes from better constraint checking (precompute which digits are available per row/col/box using bitmasks) and smarter cell ordering (choose the most constrained cell first — MRV heuristic).

### **Brute Force (which is also the standard approach):**

- For each empty cell, try 1-9, validate, recurse. If stuck, backtrack.
- **Time:** O(9^E) where E = number of empty cells — WHY: at each empty cell, we try up to 9 digits. In practice, constraint propagation prunes most branches, so it's much faster than 9^81.
- **Space:** O(E) — recursion depth = number of empty cells.

### **Key Insight:**

The backtracking template is: find empty cell → try each valid digit → place it → recurse → if recursion fails, undo placement. The "validity check" at each step prunes the search tree massively — you never explore branches that violate Sudoku constraints.

Pre-computing which digits are available per row/col/box using `boolean[9][10]` arrays turns the validity check from "scan the row/col/box" into "array lookup" — O(1) per check. This makes the constant factor much smaller.

### **Optimal Solution:**

**Steps in plain English:**

1. **Pre-compute constraints** — for each row, column, and box, mark which digits are already placed using boolean arrays.
2. **Find first empty cell** — scan the board for `.`.
3. **Try digits 1-9** — for each digit, check if it's valid for this cell's row, column, and box (O(1) lookup).
4. **Place the digit** — update the board and all three constraint arrays.
5. **Recurse** — move to the next empty cell. If recursion returns true, puzzle is solved.
6. **Backtrack** — if no digit works, undo the placement (restore board + constraint arrays) and return false.

```java
public void solveSudoku(char[][] board) {
    // Step 1 — pre-compute constraints
    boolean[][] rowUsed = new boolean[9][10];
    boolean[][] colUsed = new boolean[9][10];
    boolean[][] boxUsed = new boolean[9][10];

    for (int r = 0; r < 9; r++) {
        for (int c = 0; c < 9; c++) {
            if (board[r][c] != '.') {
                int num = board[r][c] - '0';
                rowUsed[r][num] = true;
                colUsed[c][num] = true;
                boxUsed[(r / 3) * 3 + (c / 3)][num] = true;
            }
        }
    }
    solve(board, rowUsed, colUsed, boxUsed);
}

private boolean solve(char[][] board, boolean[][] rowUsed,
                      boolean[][] colUsed, boolean[][] boxUsed) {
    // Step 2 — find first empty cell
    for (int r = 0; r < 9; r++) {
        for (int c = 0; c < 9; c++) {
            if (board[r][c] != '.') {
                continue;
            }
            int boxIdx = (r / 3) * 3 + (c / 3);

            // Step 3 — try digits 1-9
            for (int num = 1; num <= 9; num++) {
                if (rowUsed[r][num] || colUsed[c][num] || boxUsed[boxIdx][num]) {
                    continue;
                }

                // Step 4 — place the digit
                board[r][c] = (char) ('0' + num);
                rowUsed[r][num] = true;
                colUsed[c][num] = true;
                boxUsed[boxIdx][num] = true;

                // Step 5 — recurse to next empty cell
                if (solve(board, rowUsed, colUsed, boxUsed)) {
                    return true;
                }

                // Step 6 — backtrack: undo placement
                board[r][c] = '.';
                rowUsed[r][num] = false;
                colUsed[c][num] = false;
                boxUsed[boxIdx][num] = false;
            }
            // No digit worked for this cell → must backtrack
            return false;
        }
    }
    // No empty cells left → puzzle is solved
    return true;
}
```

- **Time:** O(9^E) worst case where E = empty cells — WHY: each empty cell tries at most 9 digits. In practice, constraint arrays prune most branches. For a standard Sudoku puzzle, this runs in milliseconds.
- **Space:** O(E) for recursion stack + O(270) for the three 9×10 constraint arrays — WHY: max recursion depth = E empty cells. 3 arrays × 9 rows × 10 digits = 270 booleans.

### **🔄 Variants:**

- "Optimize for speed" → MRV (Minimum Remaining Values): pick the empty cell with fewest valid options first. Reduces branching dramatically.
- "Return all solutions" → Don't return true on finding one — record solution and continue. (The problem says unique solution, but interviewer may ask.)
- "Validate before solving" → Run LC 36 first. If board is already invalid, skip solving.

### **❓ Cross-Questions:**

- **"Why boolean arrays instead of HashSets?"** → `boolean[9][10]` is an O(1) array lookup with no hashing overhead. HashSet has boxing (`int` → `Integer`) + hash computation. For a hot inner loop, array is faster.
- **"What if there's no solution?"** → `solve()` returns false from the top level. The board is restored to its original state because every placement is undone on backtrack.
- **"What's the actual runtime for a typical puzzle?"** → A standard newspaper Sudoku has ~30 empty cells. With constraint pruning, the solver typically explores < 1000 nodes. Sub-millisecond.

---

## 3. Wildcard Matching

> **Deep dive:** For the full four-stage DP drill (brute recursion → memoization →
> tabulation → space optimization) with ASCII decision tree and table-fill
> visualization, see **[`wildcard-matching-deep-dive.md`](./wildcard-matching-deep-dive.md)**.
> This section covers the optimal solution; that file covers the derivation.

### **🎤 How It's Asked:**

> "Given a string `s` and a pattern `p` with `?` (matches any single char) and `*` (matches any sequence including empty), return true if they match."
>
> Alternate: "Build a file glob matcher" or "Implement pattern matching for a CLI tool."
>
> **⚠️ Tier 1 at Confluent — "everyone I know was asked this."**

### **Discussion — How to arrive at the solution:**

First instinct: recursion. For each character, if it matches (or pattern is `?`), recurse on the rest. If pattern is `*`, try two branches: `*` matches nothing (skip `*`), or `*` matches one char (consume one char from s, keep `*`).

Why this is slow: the `*` creates two recursive branches at every position. With multiple `*`s, it's exponential. The key observation: many subproblems overlap. `isMatch(s[3..], p[5..])` may be computed from multiple paths. Overlapping subproblems → DP.

### **Brute Force:**

```java
public boolean isMatch(String s, String p, int i, int j) {
    if (j == p.length()) {
        return i == s.length();
    }
    if (p.charAt(j) == '*') {
        // * matches empty OR * matches s[i] and stays
        return isMatch(s, p, i, j + 1)
            || (i < s.length() && isMatch(s, p, i + 1, j));
    }
    if (i < s.length() && (p.charAt(j) == '?' || p.charAt(j) == s.charAt(i))) {
        return isMatch(s, p, i + 1, j + 1);
    }
    return false;
}
```

- **Time:** O(2^(m+n)) — WHY: each `*` creates two branches. With k stars and n chars, worst case is exponential.
- **Space:** O(m + n) — recursion depth.

### **Key Insight:**

This has overlapping subproblems — `isMatch(i, j)` may be called with the same `(i, j)` from multiple paths (e.g., one path where `*` matched 2 chars, another where it matched 0 chars but a later `*` matched 2 chars, both arriving at the same `(i, j)`).

Build a 2D DP table: `dp[i][j]` = "does `s[0..i-1]` match `p[0..j-1]`?"

Three transitions:
- **Literal/`?` match:** `dp[i][j] = dp[i-1][j-1]` (both consumed)
- **`*` matches empty:** `dp[i][j] = dp[i][j-1]` (skip the `*`)
- **`*` matches one more char:** `dp[i][j] = dp[i-1][j]` (consume s[i], `*` stays)

The `*` transitions are `OR` — if either leads to true, the result is true.

### **Optimal Solution:**

**Steps in plain English:**

1. **Create DP table** — `dp[m+1][n+1]` where m = s.length(), n = p.length(). `dp[i][j]` = does s[0..i-1] match p[0..j-1]?
2. **Base case** — `dp[0][0] = true` (empty matches empty). Fill first row: `dp[0][j] = true` only if all p[0..j-1] are `*` (stars can match empty).
3. **Fill table row by row** — for each cell `(i, j)`:
   - If `p[j-1]` is literal or `?`: check `dp[i-1][j-1]` (diagonal).
   - If `p[j-1]` is `*`: check `dp[i][j-1]` (skip star) OR `dp[i-1][j]` (star eats one char).
4. **Return `dp[m][n]`** — does the full string match the full pattern?

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();

    // Step 1 — create DP table
    boolean[][] dp = new boolean[m + 1][n + 1];

    // Step 2 — base cases
    dp[0][0] = true;
    // First row: empty string vs pattern — only leading *s can match empty
    for (int j = 1; j <= n; j++) {
        if (p.charAt(j - 1) == '*') {
            dp[0][j] = dp[0][j - 1];
        }
    }

    // Step 3 — fill table row by row
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1);
            char sc = s.charAt(i - 1);

            if (pc == sc || pc == '?') {
                // Literal or ? match — consume both
                dp[i][j] = dp[i - 1][j - 1];
            } else if (pc == '*') {
                // * matches empty (dp[i][j-1]) OR * eats one char (dp[i-1][j])
                dp[i][j] = dp[i][j - 1] || dp[i - 1][j];
            }
            // else: mismatch, dp[i][j] stays false
        }
    }

    // Step 4 — answer
    return dp[m][n];
}
```

- **Time:** O(m × n) — WHY: DP table has m × n cells, each filled in O(1) with one comparison and one OR.
- **Space:** O(m × n) — the DP table. Can optimize to O(n) with rolling row since each row only depends on the previous row.

### **🔄 Variants:**

- "What if `*` matches at least one character (not zero)?" → Change `dp[i][j-1]` (skip star = zero match) to only `dp[i-1][j]` (star eats one+). Base case changes: `dp[0][j] = false` for any `*`.
- "Case-insensitive matching" → Normalize both strings to lowercase before DP.
- LC 10 (Regular Expression Matching) → Different `*` semantics: "zero or more of preceding character." See next problem.

### **❓ Cross-Questions:**

- **"Can you do this in O(n) space?"** → Yes. Each row depends only on the previous row. Use `prev[]` and `curr[]` arrays, swap after each row. Space: O(n).
- **"Is there a greedy approach?"** → Yes, for wildcard (not regex). Two-pointer approach: track last `*` position and backtrack to it when stuck. O(m × n) worst case but O(m + n) average. Harder to get right in an interview.
- **"What's the worst case for the greedy approach?"** → `s = "aaa...a"`, `p = "*a*a*a..."` — many stars with literals between them. Each star may need to backtrack repeatedly. Still O(m × n) but constant factor is better than DP.

---

## 4. Regular Expression Matching

> **Deep dive:** For the full four-stage DP drill (brute recursion → memoization →
> tabulation → space optimization) with ASCII decision tree and table-fill
> visualization, see **[`wildcard-matching-deep-dive.md`](./wildcard-matching-deep-dive.md)** §LC 10.
> This section covers the optimal solution; that file covers the derivation and the
> side-by-side comparison with LC 44 (Wildcard Matching).

### **🎤 How It's Asked:**

> "Implement regular expression matching with `.` (matches any single char) and `*` (zero or more of the preceding element). The matching should cover the entire string."
>
> Alternate: "Does string `s` match pattern `p` where `a*` means zero or more a's?"
>
> **Critical difference from LC 44:** In wildcard, `*` is standalone. In regex, `*` modifies the preceding character.

### **Discussion — How to arrive at the solution:**

Same DP structure as Wildcard Matching, but the transitions change because `*` in regex means "zero or more of the preceding character."

When we see `*` at `p[j-1]`, it pairs with `p[j-2]` (the preceding char). Two choices:
1. **Zero occurrences** of `p[j-2]` → skip both `p[j-2]` and `*` → `dp[i][j-2]`
2. **One+ occurrences** → `p[j-2]` must match `s[i-1]`, then `*` stays active → `dp[i-1][j]`

### **Key Insight:**

The `*` always comes in a pair with its preceding character. Think of `a*` as a single unit meaning "zero or more a's." When you encounter `*` at position j, you're really processing the two-character unit `p[j-2..j-1]`.

This means:
- `dp[i][j]` when `p[j-1] == '*'`: `dp[i][j-2]` (skip the whole `x*` unit) OR (`p[j-2]` matches `s[i-1]` AND `dp[i-1][j]`).
- The OR between zero and one+ is what gives the "zero or more" semantics.

### **Optimal Solution:**

**Steps in plain English:**

1. **Create DP table** — `dp[m+1][n+1]`. `dp[i][j]` = does s[0..i-1] match p[0..j-1]?
2. **Base case** — `dp[0][0] = true`. First row: `dp[0][j] = true` only if `p[j-1] == '*'` and `dp[0][j-2]` was true (the `x*` unit matches empty).
3. **Fill table** — for each cell:
   - Literal or `.` match → `dp[i-1][j-1]`.
   - `*` → `dp[i][j-2]` (zero of preceding) OR (preceding matches s[i-1] AND `dp[i-1][j]`).
4. **Return `dp[m][n]`**.

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();

    // Step 1 — create DP table
    boolean[][] dp = new boolean[m + 1][n + 1];

    // Step 2 — base cases
    dp[0][0] = true;
    for (int j = 1; j <= n; j++) {
        // x* matches empty string — skip the two-char unit
        if (p.charAt(j - 1) == '*') {
            dp[0][j] = dp[0][j - 2];
        }
    }

    // Step 3 — fill table
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1);
            char sc = s.charAt(i - 1);

            if (pc == sc || pc == '.') {
                // Literal or . match — consume both
                dp[i][j] = dp[i - 1][j - 1];
            } else if (pc == '*') {
                char preceding = p.charAt(j - 2);
                // Zero occurrences of preceding — skip the x* unit
                dp[i][j] = dp[i][j - 2];
                // One+ occurrences — preceding must match s[i-1]
                if (preceding == sc || preceding == '.') {
                    dp[i][j] = dp[i][j] || dp[i - 1][j];
                }
            }
        }
    }

    // Step 4 — answer
    return dp[m][n];
}
```

- **Time:** O(m × n) — WHY: same as Wildcard — DP table with m × n cells, O(1) per cell.
- **Space:** O(m × n) — DP table. Optimizable to O(n) with rolling row.

### **🔄 Variants:**

- "Add `+` (one or more of preceding)" → For `+`, skip the "zero occurrences" branch: only check `dp[i-1][j]` when preceding matches.
- "Add `?` (zero or one of preceding)" → `dp[i][j-2]` (zero) OR `dp[i-1][j-1]` (exactly one, not one+).
- "What about character classes `[a-z]`?" → Replace literal comparison with range check. DP structure unchanged.

### **❓ Cross-Questions:**

- **"Why `dp[i][j-2]` for zero occurrences instead of `dp[i][j-1]`?"** → Because `*` pairs with its preceding char. Skipping the `*` alone (j-1) would leave the preceding char unmatched. You need to skip the entire two-character `x*` unit (j-2).
- **"How does this differ from Wildcard (LC 44)?"** → In wildcard, `*` is standalone (matches any sequence). In regex, `*` modifies the char before it. Wildcard `*` → transitions: `dp[i][j-1]` and `dp[i-1][j]`. Regex `*` → transitions: `dp[i][j-2]` and (if preceding matches) `dp[i-1][j]`.

---

## 5. Number of Atoms

### **🎤 How It's Asked:**

> "Given a chemical formula like `K4(ON(SO3)2)2`, return each element's count in alphabetical order: `K4N2O14S4`."
>
> Alternate: "Parse a nested formula string and return element frequencies."

### **Discussion — How to arrive at the solution:**

The nesting with `()` and multipliers is the key complexity. Each `(` opens a new "scope" and each `)` closes it, multiplying everything inside by the number after `)`.

This screams stack — same pattern as expression evaluation. Push a new frequency map on `(`, pop and multiply on `)`, merge into parent.

### **Key Insight:**

Use a stack of `TreeMap<String, Integer>` (TreeMap for alphabetical ordering). Each entry on the stack is one nesting level's frequency map. On `(`, push a fresh map. On `)`, pop, multiply all values by the trailing number, merge into the map now on top (the parent scope). On element + count, add directly to the top map.

The parsing itself requires careful character classification: uppercase starts an element name, lowercase continues it, digits form a number, `(` and `)` are scope delimiters.

Full walkthrough of this pattern is in [`00-concepts-before-problems.md`](./00-concepts-before-problems.md) §6 (Stack-Based Parsing).

### **Optimal Solution:**

**Steps in plain English:**

1. **Initialize** — stack with one empty TreeMap (the root scope).
2. **Scan characters** — classify each character: uppercase (new element), lowercase (continues element name), digit (count), `(` (open scope), `)` (close scope).
3. **On `(`** — push a fresh empty TreeMap onto the stack.
4. **On `)`** — read multiplier after `)`, multiply every value in top map, pop and merge into parent.
5. **On element** — parse full name (uppercase + lowercase*), parse count (digits, default 1), add to top map.
6. **Build result** — iterate the final TreeMap (already sorted) and format as string.

```java
import java.util.*;

public String countOfAtoms(String formula) {
    Deque<TreeMap<String, Integer>> stack = new ArrayDeque<>();
    // Step 1 — root scope
    stack.push(new TreeMap<>());
    int i = 0;
    int n = formula.length();

    while (i < n) {
        char c = formula.charAt(i);

        if (c == '(') {
            // Step 3 — open new scope
            stack.push(new TreeMap<>());
            i++;
        } else if (c == ')') {
            // Step 4 — close scope: read multiplier, multiply, merge into parent
            i++;
            int multiplier = 0;
            while (i < n && Character.isDigit(formula.charAt(i))) {
                multiplier = multiplier * 10 + (formula.charAt(i) - '0');
                i++;
            }
            if (multiplier == 0) {
                multiplier = 1;
            }
            // Pop current scope and multiply all counts
            TreeMap<String, Integer> inner = stack.pop();
            TreeMap<String, Integer> parent = stack.peek();
            for (Map.Entry<String, Integer> entry : inner.entrySet()) {
                parent.merge(entry.getKey(), entry.getValue() * multiplier, Integer::sum);
            }
        } else {
            // Step 5 — parse element name: uppercase + optional lowercase
            StringBuilder element = new StringBuilder();
            element.append(c);
            i++;
            while (i < n && Character.isLowerCase(formula.charAt(i))) {
                element.append(formula.charAt(i));
                i++;
            }
            // Parse count (default 1)
            int count = 0;
            while (i < n && Character.isDigit(formula.charAt(i))) {
                count = count * 10 + (formula.charAt(i) - '0');
                i++;
            }
            if (count == 0) {
                count = 1;
            }
            // Add to current scope
            stack.peek().merge(element.toString(), count, Integer::sum);
        }
    }

    // Step 6 — build result from final sorted map
    StringBuilder result = new StringBuilder();
    for (Map.Entry<String, Integer> entry : stack.peek().entrySet()) {
        result.append(entry.getKey());
        if (entry.getValue() > 1) {
            result.append(entry.getValue());
        }
    }
    return result.toString();
}
```

- **Time:** O(n) where n = formula length — WHY: each character is processed exactly once. Merge after `)` is O(k) per scope where k = unique elements, but total merge work across all scopes is bounded by O(n).
- **Space:** O(n) — stack depth = nesting level; total entries across all scope maps ≤ n.

### **🔄 Variants:**

- "What if elements can be multi-word (e.g., `Uuo` for Ununoctium)?" → Already handled — we parse uppercase + lowercase*.
- "Handle nested brackets `[]` as well" → Add `[` and `]` to the same logic as `(` and `)`.
- LC 394 (Decode String) → Same push/pop/multiply pattern, but scopes contain strings not frequency maps.

### **❓ Cross-Questions:**

- **"Why TreeMap instead of HashMap?"** → Problem requires alphabetical output. TreeMap keeps keys sorted; HashMap would require a separate sort step.
- **"What if the multiplier is 0?"** → `(H2O)0` → zero copies = remove all elements from inner scope. Our multiply step handles this naturally: all values become 0.

---

## 6. Ways to Make a Fair Array

### **🎤 How It's Asked:**

> "Given an integer array `nums`, return the number of indices you can remove so that the sum of even-indexed elements equals the sum of odd-indexed elements."
>
> Alternate: "How many elements can you delete to make the array 'fair'?"

### **Discussion — How to arrive at the solution:**

Brute force: for each index, remove it, recompute even/odd sums, check if equal. That's O(n) per removal × n indices = O(n²).

Key observation: when you remove index `i`, elements before `i` keep their even/odd positions. But elements after `i` SHIFT — what was even-indexed becomes odd-indexed and vice versa. So we need to know the even-sum and odd-sum of elements AFTER index `i`, but with their parity swapped.

This is a prefix sum problem — precompute prefix sums for even and odd positions separately.

### **Brute Force:**

- For each index i, build new array without nums[i], compute even-sum and odd-sum, check if equal.
- **Time:** O(n²) — WHY: n indices × O(n) to compute sums per removal.
- **Space:** O(n) for the new array.

### **Key Insight:**

After removing index `i`:
- **Left side** (indices 0..i-1): even/odd positions unchanged.
- **Right side** (indices i+1..n-1): even positions become odd and vice versa (everything shifts left by 1).

So: `newEvenSum = leftEvenSum + rightOddSum` and `newOddSum = leftOddSum + rightEvenSum`.

Precompute suffix sums for even and odd positions. Then for each index `i`, compute the four values in O(1).

### **Optimal Solution:**

**Steps in plain English:**

1. **Compute total even-sum and odd-sum** — iterate once, tracking even-indexed and odd-indexed sums.
2. **Iterate with running prefix** — maintain `leftEven` and `leftOdd` as running prefix sums.
3. **For each index `i`** — compute right sums: `rightEven = totalEven - leftEven - (i is even ? nums[i] : 0)`, similarly for odd. Then after removal: `newEven = leftEven + rightOdd`, `newOdd = leftOdd + rightEven`. Check if equal.
4. **Update prefix** — add nums[i] to leftEven or leftOdd depending on parity.

```java
public int waysToMakeFair(int[] nums) {
    int n = nums.length;

    // Step 1 — compute total even-index sum and odd-index sum
    int totalEven = 0;
    int totalOdd = 0;
    for (int i = 0; i < n; i++) {
        if (i % 2 == 0) {
            totalEven += nums[i];
        } else {
            totalOdd += nums[i];
        }
    }

    int count = 0;
    int leftEven = 0;
    int leftOdd = 0;

    // Step 2-3 — iterate each index as candidate for removal
    for (int i = 0; i < n; i++) {
        // Right side sums (excluding nums[i])
        int rightEven;
        int rightOdd;
        if (i % 2 == 0) {
            rightEven = totalEven - leftEven - nums[i];
            rightOdd = totalOdd - leftOdd;
        } else {
            rightEven = totalEven - leftEven;
            rightOdd = totalOdd - leftOdd - nums[i];
        }

        // After removal: right side positions swap parity
        int newEven = leftEven + rightOdd;
        int newOdd = leftOdd + rightEven;

        if (newEven == newOdd) {
            count++;
        }

        // Step 4 — update prefix sums
        if (i % 2 == 0) {
            leftEven += nums[i];
        } else {
            leftOdd += nums[i];
        }
    }
    return count;
}
```

- **Time:** O(n) — WHY: one pass for totals + one pass for checking each index = 2n.
- **Space:** O(1) — only 6 integer variables, no extra arrays.

### **🔄 Variants:**

- "What if you can remove at most K elements?" → DP or sliding window approach. Much harder.
- "What if you want the minimum number of removals?" → Different problem — likely DP.

### **❓ Cross-Questions:**

- **"Why do right-side positions swap parity?"** → Removing one element shifts all subsequent elements left by 1. Index 5 (odd) becomes index 4 (even). Index 6 (even) becomes index 5 (odd). Every element's parity flips.
- **"Can this be negative?"** → Yes — `nums[i]` can be negative. The logic handles it because we're comparing sums, not requiring positivity.

---

## 7. Merge Strings Alternately

### **🎤 How It's Asked:**

> "Given two strings, merge them by alternating characters. If one string is longer, append the remaining characters."
>
> This is a warm-up problem. Expect it as Part 1 of a 2-part coding round, with a harder Part 2.

### **Discussion — How to arrive at the solution:**

Straightforward two-pointer merge. No trick needed — iterate both strings simultaneously, append one char from each alternately. When one is exhausted, append the rest of the other.

### **Key Insight:**

No deep insight — this is a simple two-pointer interleave. The only edge case is unequal lengths. Use one while-loop with `i < word1.length() || i < word2.length()` to handle both cases cleanly.

### **Optimal Solution:**

**Steps in plain English:**

1. **Initialize** — StringBuilder for result, index `i` starting at 0.
2. **Loop** — while `i` is within either string's bounds.
3. **Append from word1** — if `i < word1.length()`, append `word1[i]`.
4. **Append from word2** — if `i < word2.length()`, append `word2[i]`.
5. **Increment i** — move to next position.

```java
public String mergeAlternately(String word1, String word2) {
    StringBuilder result = new StringBuilder();
    int i = 0;

    // Steps 2-5 — alternate characters, handle unequal lengths
    while (i < word1.length() || i < word2.length()) {
        // Step 3 — append from word1 if available
        if (i < word1.length()) {
            result.append(word1.charAt(i));
        }
        // Step 4 — append from word2 if available
        if (i < word2.length()) {
            result.append(word2.charAt(i));
        }
        i++;
    }
    return result.toString();
}
```

- **Time:** O(m + n) — WHY: processes each character exactly once from both strings.
- **Space:** O(m + n) — the result StringBuilder holds all characters.

### **🔄 Variants:**

- "Merge K strings alternately" → Round-robin: index `i` goes 0..maxLen, inner loop over all K strings.
- "Merge in chunks of size K instead of single characters" → Same approach, but append `substring(i, min(i+k, len))`.

### **❓ Cross-Questions:**

- **"Why not two separate pointers?"** → One pointer is simpler and sufficient because we advance both strings at the same rate. Two pointers would be needed if the advancement rates differ.

---

## 8. Identify the Largest Outlier

### **🎤 How It's Asked:**

> "Given an array where exactly one element is an outlier and the rest have a special property (e.g., all others sum to a target, or all others appear in pairs except two), find the largest outlier."
>
> The exact framing varies — read the problem carefully for the specific definition of "outlier."

### **Discussion — How to arrive at the solution:**

The typical LC 3371 framing: the array contains `n` numbers. One is the "sum element" (equals the sum of a subset), one is the "outlier." All others are the subset. We need: `totalSum - outlier - sumElement = sumElement`, meaning `outlier = totalSum - 2 * sumElement`.

So for each potential sumElement, compute the outlier and check if it exists in the array.

### **Key Insight:**

Rearrange the equation: if we remove the outlier, the remaining elements must contain a sumElement that equals the sum of the other remaining elements. This means `totalSum = outlier + 2 * sumElement`.

So: iterate each element as a potential `sumElement`. Compute `outlier = totalSum - 2 * sumElement`. If `outlier` exists in the array (and isn't the same element unless it appears twice), it's valid. Track the maximum valid outlier.

Use a frequency map to handle duplicates correctly.

### **Optimal Solution:**

**Steps in plain English:**

1. **Compute totalSum** — sum of all elements.
2. **Build frequency map** — count occurrences of each value.
3. **For each element as potential sumElement** — compute `outlier = totalSum - 2 * sumElement`.
4. **Validate outlier exists** — check frequency map. If `outlier == sumElement`, need freq ≥ 2 (they can't be the same instance).
5. **Track maximum** — keep the largest valid outlier.

```java
public int getLargestOutlier(int[] nums) {
    // Step 1 — compute total sum
    int totalSum = 0;
    for (int num : nums) {
        totalSum += num;
    }

    // Step 2 — frequency map
    Map<Integer, Integer> freq = new HashMap<>();
    for (int num : nums) {
        freq.merge(num, 1, Integer::sum);
    }

    int maxOutlier = Integer.MIN_VALUE;

    // Step 3 — try each element as the sumElement
    for (int sumElement : nums) {
        // Step 4 — compute and validate outlier
        int outlier = totalSum - 2 * sumElement;

        if (freq.containsKey(outlier)) {
            // If outlier == sumElement, need at least 2 occurrences
            if (outlier == sumElement && freq.get(outlier) < 2) {
                continue;
            }
            // Step 5 — track maximum
            maxOutlier = Math.max(maxOutlier, outlier);
        }
    }
    return maxOutlier;
}
```

- **Time:** O(n) — WHY: one pass for sum, one pass for freq map, one pass to check each element = 3n.
- **Space:** O(n) — frequency map stores at most n entries.

### **🔄 Variants:**

- "Find the smallest outlier instead" → Change `Math.max` to `Math.min`, init to `Integer.MAX_VALUE`.
- "What if there are multiple outliers?" → Return all valid outliers in a list.
- "What if the array has no valid outlier?" → Return -1 or throw.

### **❓ Cross-Questions:**

- **"Why check `freq.get(outlier) < 2` when `outlier == sumElement`?"** → The sumElement and outlier must be different array elements. If they compute to the same value, we need at least two copies — one to serve as sumElement, one as outlier.
- **"Can outlier be negative?"** → Yes — `totalSum - 2 * sumElement` can be negative if sumElement is large. The frequency map handles negatives naturally.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | File created. 8 algorithm problems covering Confluent Tier 1-3. Steps in plain English before every optimal solution. |
