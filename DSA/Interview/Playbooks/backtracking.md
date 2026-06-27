# Backtracking — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to recognize backtracking patterns. If a problem says "generate all," "every combination," or "all possible," it's almost certainly backtracking. Companion to `DSA/DeepDive/backtracking-fundamentals.md` for the underlying recursion-tree mental model.

---

## 🎯 Why You're Reading This

Backtracking problems scare people because the recursion tree looks complex. But 90% of interview backtracking problems are one of 4 templates with minor tweaks. Once you identify which template, the code writes itself. The hard part is recognizing whether you need **subsets vs permutations vs constraint satisfaction** — this file builds that instinct.

After reading this file, you should be able to:
1. Identify which of the 4 backtracking templates a problem needs in under 30 seconds
2. Handle duplicates correctly (the #1 backtracking bug)
3. Know when to sort-and-skip vs use a `used[]` array

---

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `Arrays.sort(arr)` | Sort to group duplicates together (required for skip-duplicates) | Patterns 1, 2 |
| `list.add(element)` / `list.remove(list.size() - 1)` | Add to path / backtrack (undo) | All patterns |
| `new ArrayList<>(path)` | Snapshot the current path into the result (deep copy) | All patterns |
| `result.add(new ArrayList<>(path))` | Save a copy — NOT a reference | All patterns |
| `boolean[] used` | Track which indices are in the current permutation | Pattern 2 |
| `s.substring(start, end)` | Extract substring for partition problems | Pattern 4 |

> **Full reference:** `../Reference/dsa-collections-notes.md`, `../Reference/string-operations-reference.md`

---

## 🧠 The Mental Model — The Backtracking Skeleton

Every backtracking problem follows the same skeleton:

```java
void backtrack(input, start/state, path, result) {
    if (goal reached) {
        result.add(new ArrayList<>(path));   // snapshot!
        return;
    }
    for (choice in available choices from start/state) {
        if (should skip this choice) continue;   // pruning
        path.add(choice);                         // make choice
        backtrack(input, next start/state, path, result);  // recurse
        path.remove(path.size() - 1);             // undo choice
    }
}
```

The **three variables** that change between templates:
1. **How you generate choices** — index-based (subsets/combos) vs swap-based (permutations) vs constraint-based (N-Queens)
2. **When you record a result** — every node (subsets), only at leaf/target (combos, perms), only valid states (constraint)
3. **How you handle duplicates** — sort+skip (subsets/combos) vs used[] array (perms)

### Pattern Recognition — Which Backtracking Template?

```
Problem says...
│
├── "All subsets" / "power set" / "all subsequences"
│   └── Pattern 1: Subsets ⭐
│       (record at EVERY node, not just leaves)
│
├── "All combinations of size K" / "combinations that sum to target"
│   └── Pattern 1 variant: Combinations
│       (record only when path.size() == K or sum == target)
│
├── "All permutations" / "all arrangements"
│   └── Pattern 2: Permutations ⭐
│       (every element must appear exactly once)
│
├── "Place N queens" / "solve sudoku" / "valid configuration"
│   └── Pattern 3: Constraint Satisfaction
│       (choices are positions, prune invalid placements)
│
└── "Partition string into palindromes" / "word break into parts"
    └── Pattern 4: Partitioning ⭐
        (choose where to cut, validate each piece)
```

### 🎨 Visual — Subsets vs Permutations Recursion Tree

```
SUBSETS of [1, 2, 3]:
Each node = choose "include or skip" for the next element

                    []
           ┌────────┴────────┐
         [1]                 []
      ┌───┴───┐          ┌───┴───┐
    [1,2]    [1]        [2]      []
    ┌─┴─┐  ┌─┴─┐     ┌─┴─┐   ┌─┴─┐
 [1,2,3][1,2][1,3][1] [2,3][2] [3] []

Result: all 8 nodes at the bottom level = all subsets
Record at EVERY node (or equivalently, at every leaf if using include/skip)

PERMUTATIONS of [1, 2, 3]:
Each level = pick one element for that position

                    []
           ┌────────┼────────┐
         [1]       [2]      [3]
       ┌──┴──┐   ┌─┴──┐   ┌─┴──┐
     [1,2] [1,3] [2,1][2,3][3,1][3,2]
       │     │     │    │    │    │
    [1,2,3][1,3,2][2,1,3]...etc

Result: only the 6 LEAVES = all permutations
Record only when path.size() == n

KEY INVARIANT:
   Subsets: "start index" moves forward → O(2^n) subsets
   Permutations: "used[] array" tracks which elements are taken → O(n!) permutations
```

---

## 🧭 Pattern 1: Subsets / Combinations ⭐

**What this solves:** Problems that ask for all possible selections from a set — subsets, combinations that sum to a target, combinations of a specific size. Unlike permutations, order doesn't matter; a `start` index prevents revisiting earlier elements so each unique selection appears exactly once.

**Recognition cues — reach for this when:**
- "Generate all subsets" / "power set"
- "All combinations that sum to target"
- "All combinations of size K from N elements"
- "All subsequences" (not substring — subsequence can skip elements)

**Brute force:** Enumerate all 2^n subsets using bitmasks — for each integer 0 to 2^n-1, set bits indicate included elements; check each against the condition. O(n · 2^n) time, O(n) space.

**Key insight:** A `start` index ensures we only consider elements at or after the last chosen position — producing order-independent combinations without a deduplication set. Recording at every node yields subsets; recording only at a condition (size == k, sum == target) yields combinations.

**Steps in plain English:**

1. **Sort the input** — required ONLY if there are duplicates to skip.
2. **Recurse with a start index** — at each level, choose elements from `start` to `n-1`.
3. **Record the path** — for subsets: add at every recursive call. For combinations: add only when condition is met (size = K, sum = target).
4. **Skip duplicates** — if `i > start && nums[i] == nums[i-1]`, skip (prevents duplicate subsets).

```java
// Subsets (no duplicates)
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> result) {
    // Record at every node
    result.add(new ArrayList<>(path));
    for (int i = start; i < nums.length; i++) {
        path.add(nums[i]);
        backtrack(nums, i + 1, path, result);
        path.remove(path.size() - 1);
    }
}
```

```java
// Subsets II (WITH duplicates) — sort + skip
public List<List<Integer>> subsetsWithDup(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> result) {
    result.add(new ArrayList<>(path));
    for (int i = start; i < nums.length; i++) {
        // Skip duplicates: same value at same level
        if (i > start && nums[i] == nums[i - 1]) {
            continue;
        }
        path.add(nums[i]);
        backtrack(nums, i + 1, path, result);
        path.remove(path.size() - 1);
    }
}
```

```java
// Combination Sum (reuse elements allowed)
// Key difference: recurse with i (not i+1) to allow reuse
private void backtrack(int[] candidates, int start, int target, List<Integer> path, List<List<Integer>> result) {
    if (target == 0) {
        result.add(new ArrayList<>(path));
        return;
    }
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > target) {
            break;
        }
        path.add(candidates[i]);
        // i, not i+1 — reuse allowed
        backtrack(candidates, i, target - candidates[i], path, result);
        path.remove(path.size() - 1);
    }
}
```

**Complexity (optimal):** O(n · 2^n) time for subsets — 2^n subsets, each O(n) to copy. O(k · C(n,k)) for size-k combinations. O(n) recursion depth.

**🏷️ Problems:** LC 78 (Subsets), LC 90 (Subsets II), LC 77 (Combinations), LC 39 (Combination Sum), LC 40 (Combination Sum II).

---

## 🧭 Pattern 2: Permutations ⭐

**What this solves:** Problems that ask for all possible orderings of elements — every element appears exactly once in each result. Unlike subsets, [1,2] and [2,1] are distinct. No `start` index is used; instead a `used[]` array tracks which indices are in the current path.

**Recognition cues — reach for this when:**
- "Generate all permutations"
- "All arrangements of N elements"
- "Every possible ordering"
- Every element must appear exactly once in each result

**Brute force:** Generate all n! orderings by repeated removal from the input list — at each step, pick any remaining element and append. O(n · n!) time, O(n) extra space per recursion path.

**Key insight:** A `used[]` boolean array lets every recursion level scan ALL indices (no start index) while skipping those already in the path. This produces every ordering because position matters — any element can fill any slot.

**Steps in plain English:**

1. **Use a `used[]` boolean array** — tracks which indices are currently in the path.
2. **No start index** — every recursion level considers ALL indices (but skips used ones).
3. **Record at leaves** — when `path.size() == nums.length`.
4. **Skip duplicates** — sort first, then `if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue`.

```java
// Permutations (no duplicates)
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, new boolean[nums.length], new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> result) {
    if (path.size() == nums.length) {
        result.add(new ArrayList<>(path));
        return;
    }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) {
            continue;
        }
        used[i] = true;
        path.add(nums[i]);
        backtrack(nums, used, path, result);
        path.remove(path.size() - 1);
        used[i] = false;
    }
}
```

```java
// Permutations II (WITH duplicates) — sort + skip
private void backtrack(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> result) {
    if (path.size() == nums.length) {
        result.add(new ArrayList<>(path));
        return;
    }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) {
            continue;
        }
        // Skip: same value, previous copy not used → would create duplicate permutation
        if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) {
            continue;
        }
        used[i] = true;
        path.add(nums[i]);
        backtrack(nums, used, path, result);
        path.remove(path.size() - 1);
        used[i] = false;
    }
}
```

**Complexity (optimal):** O(n · n!) time — n! permutations, each O(n) to copy. O(n) recursion depth.

### 🎨 Visual — Why `!used[i-1]` Prevents Duplicate Permutations

```
nums = [1, 1, 2] (sorted)
Indices: [0, 1, 2]

Without the skip, we'd generate:
  [nums[0]=1, nums[1]=1, nums[2]=2]  ← using index 0 first
  [nums[1]=1, nums[0]=1, nums[2]=2]  ← using index 1 first — SAME permutation!

The rule: "if nums[i] == nums[i-1] and nums[i-1] is NOT used"
  → means: "there's an identical value earlier that I SKIPPED"
  → "I should have used that one first"
  → skip to prevent duplicate

Think of it as: "among identical values, always use the leftmost available first."

KEY INVARIANT:
   Sort groups duplicates together.
   !used[i-1] catches when a previous identical value was skipped.
   This ensures identical values are always picked in left-to-right order.
```

**🏷️ Problems:** LC 46 (Permutations), LC 47 (Permutations II).

---

## 🧭 Pattern 3: Constraint Satisfaction (N-Queens, Sudoku)

**What this solves:** Problems requiring valid placement on a board or grid where every choice must satisfy global constraints — no two queens attack each other, every Sudoku row/column/box has unique digits. Most branches are pruned immediately, making this tractable despite the large search space.

**Recognition cues — reach for this when:**
- "Place N items on a board with constraints"
- "Solve a puzzle" — Sudoku, crossword
- "Find a valid configuration" — no two items conflict
- Heavy pruning needed — most branches are invalid

**Brute force:** Try all n² possible positions for each piece without checking validity first. O(n^n) time for N-Queens (n choices × n rows), exponentially worse without pruning.

**Key insight:** Validate BEFORE recursing — if a placement violates any constraint, skip the entire subtree. Pruning eliminates huge branches early, reducing effective search space from n^n to much closer to n! in practice.

**Steps in plain English:**

1. **Place row by row** (for N-Queens) or **cell by cell** (for Sudoku).
2. **For each position** — check if placement is valid against all constraints.
3. **If valid** — place, recurse to next row/cell.
4. **If invalid** — skip (prune this branch).
5. **If stuck** — backtrack (undo last placement).

```java
// N-Queens: place one queen per row
public List<List<String>> solveNQueens(int n) {
    List<List<String>> result = new ArrayList<>();
    char[][] board = new char[n][n];
    for (char[] row : board) {
        Arrays.fill(row, '.');
    }
    backtrack(board, 0, result);
    return result;
}

private void backtrack(char[][] board, int row, List<List<String>> result) {
    if (row == board.length) {
        result.add(boardToList(board));
        return;
    }
    for (int col = 0; col < board.length; col++) {
        if (isValid(board, row, col)) {
            board[row][col] = 'Q';
            backtrack(board, row + 1, result);
            board[row][col] = '.';
        }
    }
}

private boolean isValid(char[][] board, int row, int col) {
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
    for (int r = row - 1, c = col + 1; r >= 0 && c < board.length; r--, c++) {
        if (board[r][c] == 'Q') {
            return false;
        }
    }
    return true;
}
```

**Complexity (optimal):** O(n!) time for N-Queens (constraint propagation reduces this significantly in practice). O(n²) board space + O(n) recursion stack.

**🏷️ Problems:** LC 51 (N-Queens), LC 37 (Sudoku Solver), LC 79 (Word Search).

---

## 🧭 Pattern 4: String Partitioning ⭐

**What this solves:** Problems where you split a string into pieces and each piece must satisfy a condition — every piece is a palindrome, a dictionary word, or a valid IP segment. You choose WHERE to cut, not which elements to pick. The `start` index advances to the end of each valid piece.

**Recognition cues — reach for this when:**
- "Partition string into parts where each part satisfies a condition"
- "Palindrome partitioning" — split string into palindromic substrings
- "Word break" — split string into dictionary words
- The key is choosing WHERE to cut

**Brute force:** Try all 2^(n-1) ways to place cuts in the string (cut or no-cut at each position). For each partition, validate all pieces. O(n · 2^n) time, O(n) space.

**Key insight:** At each position, only recurse for valid pieces — invalid prefixes prune entire subtrees. The `start` index advances to `end` of the last valid piece, so we never revisit an already-consumed prefix.

**Steps in plain English:**

1. **At each position** — try all possible cut points (substrings from `start` to `end`).
2. **Validate the piece** — does this substring satisfy the condition (palindrome, dictionary word, etc.)?
3. **If valid** — add to path, recurse from `end`.
4. **At end of string** — record the partition as a result.

```java
// Palindrome Partitioning
public List<List<String>> partition(String s) {
    List<List<String>> result = new ArrayList<>();
    backtrack(s, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(String s, int start, List<String> path, List<List<String>> result) {
    if (start == s.length()) {
        result.add(new ArrayList<>(path));
        return;
    }
    for (int end = start + 1; end <= s.length(); end++) {
        String piece = s.substring(start, end);
        if (isPalindrome(piece)) {
            path.add(piece);
            backtrack(s, end, path, result);
            path.remove(path.size() - 1);
        }
    }
}

private boolean isPalindrome(String s) {
    int lo = 0;
    int hi = s.length() - 1;
    while (lo < hi) {
        if (s.charAt(lo) != s.charAt(hi)) {
            return false;
        }
        lo++;
        hi--;
    }
    return true;
}
```

**Complexity (optimal):** O(n · 2^n) time for palindrome partitioning (worst case: all single chars are palindromes). O(n) recursion depth. Palindrome checks can be reduced to O(1) with DP precomputation.

**🏷️ Problems:** LC 131 (Palindrome Partitioning), LC 93 (Restore IP Addresses), LC 140 (Word Break II).

---

## 🔬 Canonical Problem — LC 78: Subsets

> **Problem:** Given an integer array `nums` of unique elements, return all possible subsets (the power set). The solution must not contain duplicate subsets. Example: `nums = [1,2,3]` → `[[], [1], [2], [3], [1,2], [1,3], [2,3], [1,2,3]]`.

### Step 1 — Read and identify triggers

"All possible subsets" — this is textbook **Pattern 1: Subsets**. No duplicates in input, so no sorting needed.

### Step 2 — Choose the template

Subsets template. Key decisions:
- **Record at every node** (not just leaves) — every intermediate path is also a valid subset.
- **Start index** — at each level, only consider elements AFTER the last chosen one (prevents [1,2] and [2,1] both appearing).
- **No used[] array needed** — start index handles this.

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Start with empty path** — the empty set is a valid subset.
2. **At each recursive call** — snapshot the current path into results.
3. **Loop from `start` to end** — for each element, add it, recurse with `i+1`, then remove it.

```java
class Solution {
    public List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(nums, 0, new ArrayList<>(), result);
        return result;
    }

    private void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> result) {
        // Record at every node — every path is a valid subset
        result.add(new ArrayList<>(path));
        for (int i = start; i < nums.length; i++) {
            path.add(nums[i]);
            backtrack(nums, i + 1, path, result);
            path.remove(path.size() - 1);
        }
    }
}
```

### Step 4 — Verify with example

`nums = [1, 2, 3]`:
- Start: path=[] → add []
- i=0: path=[1] → add [1]
  - i=1: path=[1,2] → add [1,2]
    - i=2: path=[1,2,3] → add [1,2,3], backtrack → [1,2]
  - backtrack → [1]
  - i=2: path=[1,3] → add [1,3], backtrack → [1]
- backtrack → []
- i=1: path=[2] → add [2]
  - i=2: path=[2,3] → add [2,3], backtrack → [2]
- backtrack → []
- i=2: path=[3] → add [3], backtrack → []

Result: `[[], [1], [1,2], [1,2,3], [1,3], [2], [2,3], [3]]` — 8 subsets = 2³ ✅

### Complexity

- **Time:** O(n · 2^n) — 2^n subsets, each takes O(n) to copy
- **Space:** O(n) recursion depth + O(n · 2^n) for the result

---

## ⚡ Problem Bank — Key Twists

---

### LC 78: Subsets

> **Problem:** Given an array of unique integers, return all possible subsets. Example: `nums = [1,2,3]` → `[[], [1], [2], [1,2], [3], [1,3], [2,3], [1,2,3]]`.

> **Brute force:** Enumerate all 2^n subsets via bitmasks; for each integer 0..2^n-1, collect elements whose bit is set. O(n · 2^n) time, O(n) space.
> **Key insight:** The `start` index makes every recursive call only consider elements after the last chosen one — order-independent selection without a deduplication set.
> **Approach:** Pattern 1 baseline — record at every node, loop from `start`, recurse with `i+1`.

```java
// Snapshot current path — every subset (including empty) is valid
result.add(new ArrayList<>(path));
for (int i = start; i < nums.length; i++) {
    path.add(nums[i]);
    // Move start to i+1 so we only consider elements after this one
    backtrack(nums, i + 1, path, result);
    // Undo choice to explore the next branch
    path.remove(path.size() - 1);
}
```

**Complexity (optimal):** O(n · 2^n) time, O(n) recursion depth (excluding output).

---

### LC 90: Subsets II (with duplicates)

> **Problem:** Same as LC 78 but input may contain duplicates. Return all unique subsets. Example: `nums = [1,2,2]` → `[[], [1], [1,2], [1,2,2], [2], [2,2]]`.

> **Brute force:** Generate all 2^n subsets (as in LC 78), collect into a Set to deduplicate. O(n · 2^n) time, O(2^n) extra space for the set.
> **Key insight:** Sort so duplicates are adjacent. Skip `nums[i] == nums[i-1]` when `i > start` — prevents choosing the same value twice at the same recursion level, eliminating duplicates at the source.
> **Approach:** Sort first. Skip `nums[i] == nums[i-1]` when `i > start` (same value at same recursion level = duplicate subset).

```java
// Sort so duplicates are adjacent and skippable
Arrays.sort(nums);
// Inside the loop:
// Skip duplicate values at the same recursion level to avoid duplicate subsets
if (i > start && nums[i] == nums[i - 1]) continue;
```

**Complexity (optimal):** O(n · 2^n) time, O(n) recursion depth (excluding output).

---

### LC 39: Combination Sum

> **Problem:** Find all unique combinations of candidates that sum to `target`. Each number may be used unlimited times. Example: `candidates = [2,3,6,7], target = 7` → `[[2,2,3], [7]]`.

> **Brute force:** Generate all combinations of any length, sum each, keep those equal to target. Exponential combinations without pruning.
> **Key insight:** Recurse with `i` (not `i+1`) to allow reuse of the same element. Sort + prune: once a candidate exceeds the remaining target, all subsequent candidates will too — break early.
> **Approach:** Pattern 1 variant — recurse with `i` (not `i+1`) to allow reuse. Prune: if `candidates[i] > remaining target`, break (requires sorted array).

```java
// Sorted array — once a candidate exceeds remaining target, all after it will too
if (candidates[i] > target) break;
path.add(candidates[i]);
// Recurse with i, not i+1 (reuse allowed)
backtrack(candidates, i, target - candidates[i], path, result);
path.remove(path.size() - 1);
```

**Complexity (optimal):** O(n^(target/min)) time in worst case — target/min levels of recursion, n choices per level. O(target/min) recursion depth.

---

### LC 40: Combination Sum II (no reuse, with duplicates)

> **Problem:** Find all unique combinations that sum to `target`. Each number used at most once. Input may have duplicates. Example: `candidates = [10,1,2,7,6,1,5], target = 8` → `[[1,1,6], [1,2,5], [1,7], [2,6]]`.

> **Brute force:** Generate all subsets (no reuse), sum each, keep those equal to target, deduplicate. O(n · 2^n) time, O(2^n) space.
> **Key insight:** Sort + skip duplicates (`i > start && candidates[i] == candidates[i-1]`) avoids duplicate combinations at the same recursion level. Recurse with `i+1` to prevent element reuse.
> **Approach:** Sort + skip duplicates (`i > start && nums[i] == nums[i-1]`). Recurse with `i+1` (no reuse).

```java
Arrays.sort(candidates);
// Skip duplicates at same level
if (i > start && candidates[i] == candidates[i - 1]) continue;
// Recurse with i+1 (no reuse)
backtrack(candidates, i + 1, target - candidates[i], path, result);
```

**Complexity (optimal):** O(n · 2^n) time, O(n) recursion depth.

---

### LC 46: Permutations

> **Problem:** Given an array of distinct integers, return all permutations. Example: `nums = [1,2,3]` → `[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]`.

> **Brute force:** At each step, pick any remaining element and append — n choices for position 1, n-1 for position 2, etc. This IS the algorithm; there's no slower baseline since all n! orderings must be produced.
> **Key insight:** `used[]` lets every recursion level scan all indices while skipping taken ones. No `start` index — any element can fill any position, producing every ordering.
> **Approach:** Pattern 2 — `used[]` array, loop from 0 every time, record at leaf (`path.size() == n`).

```java
// Skip indices already in the current permutation
if (used[i]) continue;
// Choose: mark index as used and add to path
used[i] = true;
path.add(nums[i]);
backtrack(nums, used, path, result);
// Unchoose: undo for the next branch
path.remove(path.size() - 1);
used[i] = false;
```

**Complexity (optimal):** O(n · n!) time — n! permutations, each O(n) to copy. O(n) recursion depth.

---

### LC 47: Permutations II (with duplicates)

> **Problem:** Given an array that might contain duplicates, return all unique permutations. Example: `nums = [1,1,2]` → `[[1,1,2],[1,2,1],[2,1,1]]`.

> **Brute force:** Generate all n! permutations (LC 46 approach), add each to a Set to deduplicate. O(n · n!) time, O(n!) space for the set.
> **Key insight:** Sort + `!used[i-1]` check forces identical values to be used left-to-right only — `nums[i-1]` being skipped means we would produce a duplicate ordering. Eliminates duplicates at the source, no Set needed.
> **Approach:** Sort + `used[]` + skip rule: `if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue`. Forces identical values to be used in left-to-right order.

```java
// Sort to group identical values together
Arrays.sort(nums);
// Skip: identical value, and the previous one was NOT used (was skipped)
// Forces identical values to be picked in left-to-right order only
if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) continue;
```

**Complexity (optimal):** O(n · n!) time, O(n) recursion depth.

---

### LC 131: Palindrome Partitioning

> **Problem:** Partition a string such that every substring in the partition is a palindrome. Return all possible partitions. Example: `s = "aab"` → `[["a","a","b"], ["aa","b"]]`.

> **Brute force:** Try all 2^(n-1) ways to insert cuts. For each partition, check if every piece is a palindrome. O(n · 2^n) time, O(n) space.
> **Key insight:** Only recurse for palindrome prefixes — invalid prefixes prune entire subtrees. Can precompute a `boolean[][] dp` table for O(1) palindrome checks instead of O(n) each time.
> **Approach:** Pattern 4 — at each position, try every possible cut. If the piece is a palindrome, recurse from the cut point. Record when `start == s.length()`.

```java
// Try every possible cut point from start
for (int end = start + 1; end <= s.length(); end++) {
    String piece = s.substring(start, end);
    // Only recurse if this piece is a valid palindrome
    if (isPalindrome(piece)) {
        path.add(piece);
        // Continue partitioning from end of this piece
        backtrack(s, end, path, result);
        path.remove(path.size() - 1);
    }
}
```

**Complexity (optimal):** O(n · 2^n) time worst case. O(n) recursion depth. With DP palindrome precomputation: O(n²) space, O(1) per check.

---

### LC 51: N-Queens

> **Problem:** Place N queens on an N×N chessboard so that no two queens attack each other. Return all distinct solutions. Example: `n = 4` → 2 solutions.

> **Brute force:** Try placing a queen in every cell of the board without pruning. O(n^n) time — n choices per row × n rows.
> **Key insight:** One queen per row (n options per level, not n²). Validate column + both diagonals above the current row before placing — prunes most branches immediately.
> **Approach:** Pattern 3 — place one queen per row. For each column, check validity (no queen in same column, no queen on either diagonal). Prune invalid placements.

```java
// Validity: check column + both diagonals above current row
// Scan upward in the same column for a conflict
for (int r = 0; r < row; r++) {
    if (board[r][col] == 'Q') return false;
}
// Scan upper-left diagonal for a conflict
for (int r = row-1, c = col-1; r >= 0 && c >= 0; r--, c--) {
    if (board[r][c] == 'Q') return false;
}
// Scan upper-right diagonal for a conflict
for (int r = row-1, c = col+1; r >= 0 && c < n; r--, c++) {
    if (board[r][c] == 'Q') return false;
}
```

**Complexity (optimal):** O(n!) time (pruning makes it O(n!/c) in practice). O(n²) board space + O(n) recursion stack.

---

### LC 79: Word Search

> **Problem:** Given an `m x n` board of characters and a word, return true if the word exists in the grid. Letters are connected horizontally or vertically. Each cell used at most once per word. Example: `board = [["A","B"],["C","D"]], word = "ABDC"` → `true`.

> **Brute force:** For each starting cell, try all paths of length `word.length` without visited tracking — revisit cycles inflate cost to O(m·n · 4^L) with duplication.
> **Key insight:** Overwrite the current cell with '#' before recursing (visited marker), restore on backtrack. This prevents revisiting within one DFS path at O(1) cost per cell — no separate `visited` array needed.
> **Approach:** Pattern 3 variant — DFS from each cell matching word[0]. Mark cell as visited (overwrite with '#'), recurse in 4 directions, restore on backtrack.

```java
// Save cell and mark visited to prevent revisiting during this path
char temp = board[r][c];
board[r][c] = '#';
// Explore all 4 directions looking for the next character
boolean found = dfs(board, word, r+1, c, idx+1)
             || dfs(board, word, r-1, c, idx+1)
             || dfs(board, word, r, c+1, idx+1)
             || dfs(board, word, r, c-1, idx+1);
// Restore cell so other paths can use it
board[r][c] = temp;
return found;
```

**Complexity (optimal):** O(m·n · 4^L) time where L = word length. O(L) recursion depth.

---

### LC 93: Restore IP Addresses

> **Problem:** Given a string of digits, return all valid IP addresses that can be formed. Each segment is 0-255 with no leading zeros. Example: `s = "25525511135"` → `["255.255.11.135", "255.255.111.35"]`.

> **Brute force:** Try all ways to insert 3 dots into the string (C(n-1,3) positions). For each, validate all 4 parts. O(n³) time, O(1) space — correct but wasteful.
> **Key insight:** Each segment is 1-3 digits, and there are exactly 4 segments — at most 3^4 = 81 combinations to try. Prune immediately for leading zeros or value > 255. Depth is fixed at 4.
> **Approach:** Pattern 4 variant — partition string into exactly 4 parts. Each part must be 1-3 digits, value 0-255, no leading zeros. Recurse with segment count.

```java
// Try 1, 2, or 3 digit segments (IP segment can be at most 3 digits)
for (int len = 1; len <= 3 && start + len <= s.length(); len++) {
    String segment = s.substring(start, start + len);
    // Validate: no leading zeros, value 0-255
    if (isValidSegment(segment)) {
        path.add(segment);
        // Advance past this segment and try the next one
        backtrack(s, start + len, path, result);
        path.remove(path.size() - 1);
    }
}
```

**Complexity (optimal):** O(3^4) = O(81) time — fixed depth of 4 segments, each trying lengths 1-3. Effectively O(1) for standard IP addresses.

---

### LC 77: Combinations

> **Problem:** Return all combinations of `k` numbers from `[1, n]`. Example: `n = 4, k = 2` → `[[1,2],[1,3],[1,4],[2,3],[2,4],[3,4]]`.

> **Brute force:** Generate all subsets (LC 78 style over [1..n]), filter those of size k. O(n · 2^n) time — generates far more than needed.
> **Key insight:** Only record when `path.size() == k`. Prune: if `n - i + 1 < k - path.size()`, not enough elements remain to fill the combo — stop the loop early. Reduces work to exactly C(n,k) leaves.
> **Approach:** Subsets variant — same template but only record when `path.size() == k`. Prune: if remaining elements < needed, stop early.

```java
void backtrack(int n, int k, int start, List<Integer> path, List<List<Integer>> result) {
    if (path.size() == k) {
        result.add(new ArrayList<>(path));
        return;
    }
    // Pruning: stop when remaining elements can't fill the combo (k - path.size() still needed)
    for (int i = start; i <= n - (k - path.size()) + 1; i++) {
        path.add(i);
        backtrack(n, k, i + 1, path, result);
        path.remove(path.size() - 1);
    }
}
```

**Complexity (optimal):** O(k · C(n,k)) time — C(n,k) combinations, each O(k) to copy. O(k) recursion depth.

---

### LC 37: Sudoku Solver

> **Problem:** Fill a 9×9 Sudoku board so each row, column, and 3×3 box contains digits 1-9 exactly once. Modify the board in-place.

> **Brute force:** Try all digits 1-9 in every empty cell with no constraint check — O(9^81) time. Essentially every board configuration.
> **Key insight:** Validate before placing — row, column, and 3×3 box checks immediately eliminate most candidates. Return `true` as soon as a solution is found to stop all further recursion.
> **Approach:** Constraint satisfaction. Find next empty cell, try digits 1-9, validate (row, column, box), recurse. If stuck → backtrack. Return true on success to stop further exploration.

```java
// Try placing digits 1-9 in the empty cell
for (char c = '1'; c <= '9'; c++) {
    if (isValid(board, row, col, c)) {
        board[row][col] = c;
        // If placing c leads to a complete solution, stop searching
        if (solve(board)) return true;
        // Undo — this digit didn't lead to a solution
        board[row][col] = '.';
    }
}
// No digit works here — trigger backtracking in the caller
return false;
```

**Complexity (optimal):** O(9^m) time where m = number of empty cells. In practice, constraint propagation reduces this dramatically.

---

### LC 140: Word Break II

> **Problem:** Return all possible sentences from string `s` using words from `wordDict`. Example: `s = "catsanddog", wordDict = ["cat","cats","and","sand","dog"]` → `["cats and dog", "cat sand dog"]`.

> **Brute force:** Try all 2^(n-1) partitions of the string, check if each piece is in the dictionary. O(n · 2^n) time — most partitions are invalid.
> **Key insight:** Recurse only on valid dictionary prefixes — prunes non-word branches early. Memoize: if backtracking from position `i` has been computed, reuse it to avoid exponential re-exploration.
> **Approach:** Partitioning variant. At each position, try every prefix that's in the dictionary. If valid, recurse from the end of that prefix. Memoize to avoid re-exploring same suffix.

```java
void backtrack(String s, int start, Set<String> dict, List<String> path, List<String> result) {
    // All characters consumed — join path words into a sentence
    if (start == s.length()) {
        result.add(String.join(" ", path));
        return;
    }
    // Try every prefix starting at current position
    for (int end = start + 1; end <= s.length(); end++) {
        String word = s.substring(start, end);
        // Only recurse if this prefix is a dictionary word
        if (dict.contains(word)) {
            path.add(word);
            backtrack(s, end, dict, path, result);
            path.remove(path.size() - 1);
        }
    }
}
```

**Complexity (optimal):** O(n³) with memoization — n start positions × n end positions × O(n) substring. O(n) recursion depth.

---

## ⚠️ Interview Gotchas

### Edge cases interviewers probe
- **Empty input** — return `[[]]` for subsets (the empty set), `[]` for permutations
- **Single element** — `[1]` has subsets `[[], [1]]` and permutation `[[1]]`
- **All duplicates** — `[1,1,1]` subsets = `[[], [1], [1,1], [1,1,1]]` — the skip rule handles this
- **Large input** — backtracking is exponential. For n > 20, ask if there's a DP or greedy approach instead

### The "snapshot" bug (most common backtracking mistake)
- ❌ `result.add(path)` — adds a REFERENCE. When path changes later, the result changes too!
- ✅ `result.add(new ArrayList<>(path))` — adds a COPY. The result is frozen at this point.
- This bug produces a result where all entries are identical (the final state of path, usually `[]`).

### The duplicates handling confusion
- **Subsets/Combinations with duplicates:** Sort + `if (i > start && nums[i] == nums[i-1]) continue`
  - `i > start` means "same level, not the first occurrence" — skip it
- **Permutations with duplicates:** Sort + `if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue`
  - `!used[i-1]` means "the previous identical value was NOT used" — skip to enforce left-to-right ordering

### Follow-up questions to expect
- "Can you optimize the palindrome check?" → Precompute a `boolean[][] isPalin` table using DP
- "What's the time complexity?" — Usually O(n · 2^n) for subsets, O(n · n!) for permutations
- "Can you do it iteratively?" — Yes, but backtracking is cleaner and what interviewers expect
- "What if elements can be reused?" — Combination Sum: recurse with `i` instead of `i+1`

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**
For each problem description, name the pattern in under 5 seconds:

1. "Generate all subsets of [1,2,3]" → ___
2. "Generate all permutations of [1,2,3]" → ___
3. "Find all combinations of [2,3,5] that sum to 8" → ___
4. "Partition 'aab' into palindromic substrings" → ___
5. "Place 4 queens on a 4×4 board" → ___

**Part 2 — Write the Template (3 minutes)**
From memory, write the subsets template (Pattern 1 — no duplicates).

**Part 3 — Adapt (3 minutes)**
Modify your subsets template for LC 90 (with duplicates). What two changes are needed?

**Scoring:**
- Part 1: 5/5 correct → ready. Confused subsets with permutations → re-read the visual.
- Part 2: Template correct with `new ArrayList<>(path)` snapshot → ready. Used `result.add(path)` → re-read gotchas.
- Part 3: Two changes: (1) sort, (2) skip `i > start && nums[i] == nums[i-1]` → ready.

---

## 🔗 Cross-References

- **Companion DeepDive:** `../DeepDive/backtracking-fundamentals.md` — recursion tree mental model, pruning strategies
- **Recursion:** `../DeepDive/recursion-fundamentals.md` — the recursion call stack mechanics behind backtracking
- **Trees:** `../Interview/trees-and-bfs-dfs.md` — DFS on trees uses similar recursive structure
- **Strings:** `../Interview/strings.md` — palindrome checking helper reused in Pattern 4

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Backtracking Interview Playbook — 4 patterns (Subsets/Combinations, Permutations, Constraint Satisfaction, Partitioning), canonical walkthrough (LC 78 Subsets), 10 problems with expanded definitions. |
| June 2026 | **Brute Force / Key Insight pass.** Added What this solves, Brute force, Key insight to all 4 pattern blocks. Added > Brute force, > Key insight to all 13 problem bank entries. Added Complexity (optimal) after every code block. |
