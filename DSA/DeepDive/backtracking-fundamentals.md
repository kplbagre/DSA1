# Backtracking — Fundamentals (Deep Dive)

> Backtracking is recursion's "exploration mode." You build a partial solution one choice at a time, recurse to extend it, and **undo** the choice when the branch is exhausted so the next branch starts clean. That's the entire idea — but interview problems hide six distinct sub-patterns inside it. This doc teaches all six.

---

## 📋 Section Index

| Section | Topic |
| --- | --- |
| [🎯 Goal](#goal) | What you can do after reading this |
| [🚦 Difficulty Tags](#difficulty-tags) | ✅ 🟡 🔴 ratings explained |
| [📖 Terminology](#terminology) | State, pruning, choice, constraint, candidate |
| [🧠 Universal Backtracking Recipe](#recipe) | The 4-line mental model that covers all sub-patterns |
| [🔑 Decision Framework](#decision-framework) | Take/Not-Take vs For-loop — which to use |
| [🔨 Setup — Phase 1](#setup) | What to prepare before calling backtrack() |
| [🪜 Sub-Pattern 1: Take/Not-Take](#subpattern-1) | Subsets, combination sum II, partition equal subset |
| [🪜 Sub-Pattern 2: For-loop](#subpattern-2) | Combinations, letter combos of phone number |
| [🪜 Sub-Pattern 3: Permutations](#subpattern-3) | All permutations with/without duplicates |
| [🪜 Sub-Pattern 4: Pruning](#subpattern-4) | N-Queens — hard constraint satisfaction |
| [🪜 Sub-Pattern 5: Grid](#subpattern-5) | Word search — 2D DFS with visited tracking |
| [🪜 Sub-Pattern 6: Partitioning](#subpattern-6) | Palindrome partitioning — cut-point decisions |
| [🎨 Style Habits](#style-habits) | Undo discipline, result.add(new ArrayList(current)) |
| [🐞 Common Bugs](#common-bugs) | Forgetting undo, shared list mutation, wrong pruning |
| [🔬 Worked Walkthroughs](#walkthroughs) | Problems traced step by step |
| [🗺️ Practice Plan](#practice-plan) | Tier-by-tier progression |
| [⚠️ Gotchas](#gotchas) | Silent bugs that compile but produce wrong output |
| [🧾 TL;DR](#tldr) | One-page summary for revision day |


---

<a id="goal"></a>
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

<a id="difficulty-tags"></a>
## 🚦 Difficulty Tagging — Read Before You Pick a Problem

Every problem in this doc is tagged so you know whether to attempt it **now** or **wait** until you've covered more material.

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point in the doc | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs concepts from a later section in this same doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc (DP optimization, advanced pruning) | Read the problem and editorial for awareness; don't attempt cold |

> **Same lesson as trees doc:** I (Kapil) burned an hour on LC 124 before I had two-purpose recursion in my head. **Don't attempt 🔴 cold.** The tags exist precisely so you don't repeat that mistake.

---

<a id="terminology"></a>
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

<a id="recipe"></a>
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

### 🎨 Visual — Backtracking Is DFS on a Decision Tree

```
EVERY backtracking problem is a DFS on an implicit DECISION TREE.
The path from the root to a leaf represents one full sequence of choices.
The "undo" step is what lets the algorithm RE-USE the partial after a
sibling branch is explored.


For nums = [1, 2, 3], the take/not-take subsets tree looks like:

                              path = [ ]              ◀── start
                              ├── TAKE 1 ─────┐
                              └── SKIP 1 ─────────┐
                  path = [1]                  path = [ ]
                  ├── TAKE 2 ──┐               ├── TAKE 2 ──┐
                  └── SKIP 2 ──────┐           └── SKIP 2 ──────┐
            path=[1,2]      path=[1]      path=[2]        path=[ ]
            ├── T 3         ├── T 3        ├── T 3         ├── T 3
            └── S 3         └── S 3        └── S 3         └── S 3
              ⋮               ⋮              ⋮               ⋮
         (8 LEAVES = 2³ subsets:
          [1,2,3], [1,2], [1,3], [1], [2,3], [2], [3], [ ])


WHAT EACH ARROW MEANS:
   ── DOWNWARD edge  =  apply(choice)        ← Step 4 in template (TRY)
   ── UPWARD return  =  undo(choice)         ← Step 6 in template (UNDO)


THE UNDO STEP — VISUAL ANIMATION (on path list):

   Before TAKE 1:    path = [ ]
   apply TAKE 1:     path = [1]            ← TRY
   ... recurse, leaf reached, snapshot saved
   undo TAKE 1:      path = [ ]            ← UNDO  ◀── crucial
   apply SKIP 1:     path = [ ]            ← (no mutation for SKIP)
   ... continues exploring with clean slate


WHY THE UNDO IS NON-NEGOTIABLE:

   When the recursion returns from the TAKE branch, the path list
   is SHARED with the SKIP branch about to fire next.  If we don't
   undo, the SKIP branch starts with [1] in path — polluting every
   subset it generates.

   Mental model: "path is a single, mutable highlight pen.  After
   coloring the left branch, lift the pen before coloring the right."
```

---

<a id="decision-framework"></a>
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

<a id="setup"></a>
## 🔨 Setting Up Before You Call `backtrack()` — Phase 1

> **⬛ The choices array in backtracking is usually the raw input itself** — no adjacency-list or TreeNode construction needed. What trips interviewees is forgetting to initialize **auxiliary state** (the `used[]` array, the board, constraint sets) before the first `backtrack()` call, and forgetting to sort when duplicates are present.

---

### Consolidated Setup — What to Initialize Per Sub-Pattern

| Sub-pattern | Initialize before calling `backtrack()` | Sort input first? |
| --- | --- | --- |
| **1. Take / Not-Take** | `results = new ArrayList<>()`, `path = new ArrayList<>()` | No |
| **2. For-loop Pick-Next** | `results`, `path` | Only if duplicates present (LC 40) |
| **3. Permutations** | `results`, `path`, `boolean[] used = new boolean[n]` | Only if duplicates present (LC 47) |
| **4. Constraint-Driven** | `results`, `char[][] board` (init to `'.'`), constraint `Set<Integer>` per dimension | No |
| **5. Grid / 2D** | Grid is the raw input; add `boolean[][] visited` or mark cells in-place | No |
| **6. Cut-points** | `results`, `path` (for partition slices or substrings) | No |

---

### The Wrapper Pattern — Public Sets Up, Private Recurses

Every backtracking problem should use this two-method structure: the **public method owns Phase 1**, the **private method owns Phase 2**.

```java
public List<List<Integer>> solve(int[] nums) {
    // Phase 1 — initialize auxiliary state
    List<List<Integer>> results = new ArrayList<>();
    List<Integer> path = new ArrayList<>();
    // Phase 1 — sort if input has duplicates or pruning requires ordered candidates
    Arrays.sort(nums);
    // Phase 2 — run the algorithm
    backtrack(nums, 0, path, results);
    return results;
}

private void backtrack(int[] nums, int start,
                       List<Integer> path, List<List<Integer>> results) {
    // ... recursive logic
}
```

> **Why this split matters:** passing `results` and `path` as parameters (not class fields) keeps the function pure and thread-safe. The public method is the only caller that ever creates these lists — the private method only reads and mutates them.

---

### The One Case Where Phase 1 Builds a Real Lookup — LC 17 Letter Combinations

The **only** sub-pattern where Phase 1 constructs a genuine lookup structure is when raw digits must be converted to a letter set (LC 17). The `Map<Character, String>` IS the "choice space" — without it the backtracking loop has nothing to iterate over.

**Steps in plain English:**

1. **Phase 1 — build the phone map** once before any recursion.
2. **Phase 1 — guard** the empty-digits case.
3. **Phase 2 — backtrack:** at each digit index, loop over that digit's letters; append, recurse, delete last char (undo).

```java
public List<String> letterCombinations(String digits) {
    // Step 1 — Phase 1: build the lookup (the choice space comes from this map)
    Map<Character, String> phoneMap = new HashMap<>();
    phoneMap.put('2', "abc");
    phoneMap.put('3', "def");
    phoneMap.put('4', "ghi");
    phoneMap.put('5', "jkl");
    phoneMap.put('6', "mno");
    phoneMap.put('7', "pqrs");
    phoneMap.put('8', "tuv");
    phoneMap.put('9', "wxyz");
    List<String> results = new ArrayList<>();
    // Step 2 — guard
    if (digits.isEmpty()) {
        return results;
    }
    // Step 3 — Phase 2: run backtracking
    backtrack(digits, 0, new StringBuilder(), results, phoneMap);
    return results;
}

private void backtrack(String digits, int ind, StringBuilder path,
                       List<String> results, Map<Character, String> phoneMap) {
    if (ind == digits.length()) {
        results.add(path.toString());
        return;
    }
    String letters = phoneMap.get(digits.charAt(ind));
    for (char c : letters.toCharArray()) {
        // TRY
        path.append(c);
        backtrack(digits, ind + 1, path, results, phoneMap);
        // UNDO — StringBuilder.deleteCharAt is the analogue of List.remove(size-1)
        path.deleteCharAt(path.length() - 1);
    }
}
```

> **Key observation:** the `for` loop iterates over `phoneMap.get(digit)` — not over `nums[i]`. The map IS the phase-1 construction that enables the for-loop body.

---

> **⬛ Pre-flight: before writing the `backtrack()` call, answer these 4 setup questions:**
>
> 1. **Which sub-pattern?** → use the Decision Framework table above; pick the matching row in the setup table.
> 2. **Duplicates in input?** → `Arrays.sort()` first; add `if (i > start && arr[i] == arr[i-1]) continue;` inside the for-loop.
> 3. **Need `boolean[] used`?** → permutations only. For-loop with `start` does NOT need one — `start` handles exclusion.
> 4. **Constraint dimensions?** → for N-Queens and similar, allocate one `Set<Integer>` (or `boolean[]`) per constraint dimension (rows, columns, diagonals) before the first call.

---

<a id="subpattern-1"></a>
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

<a id="subpattern-2"></a>
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

<a id="subpattern-3"></a>
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

<a id="subpattern-4"></a>
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

### 🎨 Visual — N-Queens Attack Zones and Pruning Power

```
A queen at (row, col) attacks THREE lines on the board:
   1. The whole column      (col is constant)
   2. The   \   diagonal    (row + col is constant)
   3. The   /   diagonal    (row - col is constant)

For n = 4, placing a queen at (1, 1) carves out these forbidden cells:

         col: 0   1   2   3
        ┌────┬───┬───┬───┐
   r=0  │  X │ X │   │   │      X = attacked
        ├────┼───┼───┼───┤      Q = placed queen
   r=1  │  X │ Q │ X │ X │      . = still available
        ├────┼───┼───┼───┤
   r=2  │  X │ X │ X │   │
        ├────┼───┼───┼───┤
   r=3  │    │ X │   │ X │      ← (3,2) safe   (3,0)(3,3) BLOCKED by diags
        └────┴───┴───┴───┘
                              Only ONE safe cell in row 3 → massive pruning


THE FULL DECISION TREE FOR N=4 (pruned vs unpruned):

   Without pruning: 4 × 4 × 4 × 4 = 256 leaves explored

   With pruning:    < 20 nodes actually visited (most branches die early)


WHY THE TWO DIAGONAL FORMULAS WORK:

   ╲  diagonal (top-left to bottom-right):
                (0,0)  (1,1)  (2,2)  (3,3)
                 0+0    1+1    2+2    3+3       ← row + col is constant
                  0      2      4      6

   ╱  diagonal (top-right to bottom-left):
                (0,3)  (1,2)  (2,1)  (3,0)
                 0-3    1-2    2-1    3-0       ← row - col is constant
                  -3     -1      1      3

   So a boolean[2n-1] indexed by (row + col) tracks all ╲ diagonals,
   and a boolean[2n-1] indexed by (row - col + n - 1) tracks all ╱
   diagonals (the + n - 1 shifts negative values to a valid array index).


PRUNING INVARIANT (the "win" of constraint-driven backtracking):

   Each placed queen kills at most  3n - 2  cells in unexplored rows.
   That's why N-Queens runs in milliseconds for n ≤ 12 even though
   the raw search space is n!  ≈ 479 million for n = 12.
```

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

<a id="subpattern-5"></a>
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

### 🎨 Visual — Word Search Path on the Grid

```
Searching for word = "ABCCED" in:

      0   1   2   3                Path that succeeds:
    ┌───┬───┬───┬───┐
0   │ A │ B │ C │ E │             A(0,0) → B(0,1) → C(0,2)
    ├───┼───┼───┼───┤                                  ↓
1   │ S │ F │ C │ S │             D(1,3) ← E(2,3) ← C(1,2)
    ├───┼───┼───┼───┤
2   │ A │ D │ E │ E │             (CCED takes the right column down)
    └───┴───┴───┴───┘


STEP-BY-STEP WITH SENTINEL '#' AND UNDO:

   Visit (0,0) 'A'  matches word[0]      grid: [#][B][C][E]
   Visit (0,1) 'B'  matches word[1]            [S][F][C][S]
   Visit (0,2) 'C'  matches word[2]            [A][D][E][E]
                                        (cells visited get '#')

   Try (1,2) 'C' — but word[3] is 'C' — match!
   Try (2,3) 'E' — match word[4]
   Try (1,3) 'D'? — board has 'S'. MISMATCH.
   Try (2,2) 'E'? — visited (# now). Skip.
   Try (1,3) is the only path... actually:

   At (1,2) we tried 4 dirs, found (2,2) 'E' matches word[4]
   At (2,2) we try (2,1) 'D' — match word[5] = 'D'      ✅ DONE!


WHAT THE UNDO PREVENTS — BACKTRACK WHEN A BRANCH FAILS:

   Suppose searching for "ABCE" instead — different word.
   At step 'C' (0,2), we branch into (1,2) 'C'.  word[3]='E'? Misses.
   Without UNDO: (1,2) stays '#' forever — future paths can't use it.
   With UNDO:    on return, (1,2) → 'C'. Now we can try (0,3) 'E'
                 from (0,2), and the original 'C' at (1,2) is still
                 available for OTHER starting cells.


GRID-PATH BACKTRACKING INVARIANT:

   After every recursive call returns, the grid MUST look exactly
   like it did before the call.  Sentinel '#' is the only mutation,
   and restoring it on the way out is what makes the algorithm safe
   to call from any starting cell in the outer double-loop.
```

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

<a id="subpattern-6"></a>
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

<a id="style-habits"></a>
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

<a id="common-bugs"></a>
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

<a id="walkthroughs"></a>
## 🔬 Worked Walkthroughs

---

### WW-1 — LC 78 Subsets

Return all 2^n subsets of an integer array with distinct elements.

**Brute force:** Iterate over all 2^n bitmasks from 0 to 2^n − 1; for each mask, include `nums[i]` wherever bit `i` is set. O(n × 2^n) time — which is also the optimal complexity since you must generate all subsets. Bitmask style requires knowing `n` and uses bit manipulation; recursion generalises more easily.

**Intuition bridge:** At each element the only decision is include or exclude. The for-loop style snapshots every prefix of `path` as a valid subset and loops from `start` — one line change (`start` vs 0) is all that separates subsets from permutations.

**Steps in plain English:**

1. **Snapshot on entry** — every state of `path` is a valid subset; record it immediately before branching.
2. **Loop from `start`** — only consider indices ≥ `start` to avoid revisiting earlier elements.
3. **TRY** — add `nums[i]` to `path`.
4. **RECURSE** — call with `i + 1` so inner levels pick strictly later elements.
5. **UNDO** — remove last element before the next iteration.

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> results = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> results) {
    // Step 1 — snapshot every prefix as a valid subset
    results.add(new ArrayList<>(path));
    // Step 2 — loop from start only
    for (int i = start; i < nums.length; i++) {
        // Step 3 — TRY
        path.add(nums[i]);
        // Step 4 — RECURSE with i+1
        backtrack(nums, i + 1, path, results);
        // Step 5 — UNDO
        path.remove(path.size() - 1);
    }
}
```

**Time:** O(n × 2^n). **Space:** O(n) recursion depth.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 90 Subsets II | Duplicates: sort first, skip when `nums[i] == nums[i-1]` and `i > start` | Add `if (i > start && nums[i] == nums[i - 1]) continue;` inside loop |
| LC 39 Combination Sum | Elements reusable, target sum prunes | Pass `i` instead of `i+1`; add `remaining` parameter; return when `remaining == 0` |
| LC 46 Permutations | Order matters — no `start`, loop all, `used[]` tracks current path | Replace `start` with `boolean[] used`; loop from 0; skip `used[i]` |

---

### WW-2 — LC 46 Permutations

Return all permutations of a distinct integer array.

**Brute force:** Generate all n! arrangements by repeatedly choosing from remaining elements, building new arrays at each level. O(n × n!) time — same asymptotic as optimal. The distinction is in the data structure: building new arrays costs O(n) extra per call vs. mutating one `path` list.

**Intuition bridge:** Unlike combinations, order matters — every unused element is a valid next choice at any depth. A `used[]` boolean array replaces the `start` parameter: instead of restricting which indices are valid, we explicitly skip elements already in the current path.

**Steps in plain English:**

1. **Base case** — when `path.size() == nums.length` every element is placed; snapshot and return.
2. **Loop ALL indices** from 0 — no `start`; order is what we're exploring.
3. **Skip used** — if `used[i]` is true, that element is already in the current path.
4. **TRY** — set `used[i] = true` and add `nums[i]` to `path`.
5. **RECURSE**.
6. **UNDO BOTH** — remove from `path` AND reset `used[i] = false`.

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> results = new ArrayList<>();
    boolean[] used = new boolean[nums.length];
    backtrack(nums, used, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> results) {
    // Step 1 — base case: all elements placed
    if (path.size() == nums.length) {
        results.add(new ArrayList<>(path));
        return;
    }
    // Step 2 — loop ALL indices
    for (int i = 0; i < nums.length; i++) {
        // Step 3 — skip already-used elements
        if (used[i]) {
            continue;
        }
        // Step 4 — TRY: two mutations
        used[i] = true;
        path.add(nums[i]);
        // Step 5 — RECURSE
        backtrack(nums, used, path, results);
        // Step 6 — UNDO BOTH mutations
        path.remove(path.size() - 1);
        used[i] = false;
    }
}
```

**Time:** O(n × n!). **Space:** O(n) for `path` + `used` + call stack.

> Two mutations (`used[i] = true` + `path.add(...)`) require two undos — forgetting either creates a silent bug that passes small inputs.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 47 Permutations II | Duplicates: sort + skip `nums[i] == nums[i-1]` when `!used[i-1]` | Add `if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue;` |
| LC 60 Permutation Sequence | Only the k-th permutation needed — prune by counting subtree size | Use factorial counts to skip entire branches, take one path |
| LC 784 Letter Case Permutation | Branch on lowercase (toggle case) vs uppercase — same used-less skeleton | At each index: if letter, recurse with both cases; if digit, recurse once |

---

### WW-3 — LC 39 Combination Sum

Find all combinations of `candidates` (distinct, reusable) that sum exactly to `target`.

**Brute force:** Recursively try all sequences of candidates summing to target. Without bounding, this explores infinite paths; the target bounds depth but exponential branching remains — O(target^n) in the worst case without proper pruning.

**Intuition bridge:** Same for-loop skeleton as WW-1 Subsets — but pass `i` instead of `i+1` to allow re-picking the same element, and carry a `remaining` counter; prune immediately when it goes negative; record a snapshot when it hits zero.

**Steps in plain English:**

1. **Base case — exact match** — when `remaining == 0`, snapshot `path` and return.
2. **Prune — over-budget** — when `remaining < 0`, return immediately.
3. **Loop from `start`** — pass `i` (not `i+1`) in the recursive call to allow reuse.
4. **TRY** — add `candidates[i]` to `path`.
5. **RECURSE** with `remaining - candidates[i]`.
6. **UNDO** — remove last element.

```java
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    List<List<Integer>> results = new ArrayList<>();
    Arrays.sort(candidates);
    backtrack(candidates, 0, target, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] candidates, int start, int remaining,
                       List<Integer> path, List<List<Integer>> results) {
    // Step 1 — exact match: snapshot
    if (remaining == 0) {
        results.add(new ArrayList<>(path));
        return;
    }
    // Step 2 — over-budget: prune (sort enables early exit)
    if (remaining < 0) {
        return;
    }
    // Step 3 — loop from start; pass i (not i+1) to allow reuse
    for (int i = start; i < candidates.length; i++) {
        // Step 4 — TRY
        path.add(candidates[i]);
        // Step 5 — RECURSE: same i for reuse
        backtrack(candidates, i, remaining - candidates[i], path, results);
        // Step 6 — UNDO
        path.remove(path.size() - 1);
    }
}
```

**Time:** O(n^(T/min_c)) where T = target, min_c = smallest candidate. **Space:** O(T/min_c) depth.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 40 Combination Sum II | Each element usable once; duplicates present — sort + skip `nums[i] == nums[i-1]` | Pass `i+1` (no reuse); add `if (i > start && candidates[i] == candidates[i-1]) continue;` |
| LC 216 Combination Sum III | Exactly k numbers summing to n, digits 1-9, no reuse | Add `if (path.size() == k && remaining == 0)` as base; fixed candidate set 1..9 |
| LC 518 Coin Change 2 | Count combinations (not enumerate); DP is O(amount × coins) — far faster | Use `dp[a] += dp[a - coin]` outer-coin/inner-amount loop |

---

### WW-4 — LC 40 Combination Sum II

Find all unique combinations where each number is used once; input may contain duplicates.

**Brute force:** Same backtracking as LC 39 but pass `i+1` (no reuse). Without deduplication, arrays like `[1, 1, 2]` with target 3 produce `[1,2]` twice — once starting from the first 1, once from the second.

**Intuition bridge:** Sort first so duplicates are adjacent. At each loop level, if `candidates[i] == candidates[i-1]` and `i > start`, we are about to make the exact same recursive call we already made in the previous iteration — skip it. The `i > start` guard preserves the first occurrence.

**Steps in plain English:**

1. **Sort the array** — puts duplicates next to each other; enables O(1) skip check.
2. **Base case** — `remaining == 0` → snapshot and return.
3. **Loop from `start`** — pass `i+1` (each element used at most once).
4. **Skip same-value duplicates** — if `i > start && candidates[i] == candidates[i-1]`, `continue`.
5. **Early exit** — if `candidates[i] > remaining`, sorted order guarantees rest are also too large; break.
6. **TRY / RECURSE / UNDO** — standard three-step.

```java
public List<List<Integer>> combinationSum2(int[] candidates, int target) {
    List<List<Integer>> results = new ArrayList<>();
    Arrays.sort(candidates);
    backtrack(candidates, 0, target, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] candidates, int start, int remaining,
                       List<Integer> path, List<List<Integer>> results) {
    // Step 2 — base case
    if (remaining == 0) {
        results.add(new ArrayList<>(path));
        return;
    }
    for (int i = start; i < candidates.length; i++) {
        // Step 4 — skip duplicate at same level
        if (i > start && candidates[i] == candidates[i - 1]) {
            continue;
        }
        // Step 5 — sorted: rest are too large
        if (candidates[i] > remaining) {
            break;
        }
        // Step 6 — TRY / RECURSE / UNDO
        path.add(candidates[i]);
        backtrack(candidates, i + 1, remaining - candidates[i], path, results);
        path.remove(path.size() - 1);
    }
}
```

**Time:** O(2^n) in the worst case; pruning cuts practical runtime significantly. **Space:** O(n) depth.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 90 Subsets II | Same sort + skip-duplicate pattern; no target sum | Remove `remaining` tracking; snapshot on entry, not at base |
| LC 47 Permutations II | Same dedup idea for permutations — sort + skip when `nums[i]==nums[i-1] && !used[i-1]` | Loop from 0 (not start); use `used[]` array; different skip condition |
| LC 39 Combination Sum | Same skeleton; allow reuse and no duplicates | Pass `i` not `i+1`; remove skip check |

---

### WW-5 — LC 22 Generate Parentheses

Generate all well-formed parenthesis strings using exactly n pairs.

**Brute force:** Generate all 2^(2n) binary strings of length 2n (each position is `(` or `)`); filter valid ones by checking balance. O(2^(2n) × n) — most strings are invalid, so almost all work is wasted.

**Intuition bridge:** Never generate invalid states — add a character only when the balance stays valid. Open count can't exceed `n`; close count can't exceed open count. Every leaf at depth 2n is automatically well-formed, so no post-filtering is needed.

**Steps in plain English:**

1. **Base case** — when `path.length() == 2 * n`, the string is complete and valid; add to results.
2. **Add `(` when possible** — if `open < n`, recurse with `open + 1`.
3. **Add `)` when possible** — if `close < open`, recurse with `close + 1`.

```java
public List<String> generateParenthesis(int n) {
    List<String> results = new ArrayList<>();
    backtrack(n, 0, 0, new StringBuilder(), results);
    return results;
}

private void backtrack(int n, int open, int close,
                       StringBuilder path, List<String> results) {
    // Step 1 — base: string is full and automatically valid
    if (path.length() == 2 * n) {
        results.add(path.toString());
        return;
    }
    // Step 2 — add '(' only when open count allows
    if (open < n) {
        path.append('(');
        backtrack(n, open + 1, close, path, results);
        path.deleteCharAt(path.length() - 1);
    }
    // Step 3 — add ')' only when it closes a pending '('
    if (close < open) {
        path.append(')');
        backtrack(n, open, close + 1, path, results);
        path.deleteCharAt(path.length() - 1);
    }
}
```

**Time:** O(4^n / √n) — the n-th Catalan number counts valid strings. **Space:** O(n) depth.

> This is the canonical example of **validity-gated generation** — the constraint check is done before recursing, not after, so the tree is pruned rather than filtered.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 1614 Maximum Nesting Depth | Count max open depth at any point — greedily read the string | No backtracking needed; just track `depth` in one pass |
| LC 32 Longest Valid Parentheses | Find longest valid substring — stack or DP approach | Use stack of indices tracking unmatched `(`; compute gap lengths |
| LC 20 Valid Parentheses | Check if a given string is valid — stack verifier | Push on `(`; pop on `)` checking match; return stack empty at end |

---

### WW-6 — LC 17 Letter Combinations of a Phone Number

Return all letter combinations a digit string could represent using a standard phone keypad.

**Brute force:** Same as optimal — build the cartesian product of the digit-letter sets. O(4^n × n) where n is the number of digits (digits 7 and 9 map to 4 letters each). No smarter approach exists; you must enumerate all combinations.

**Intuition bridge:** Unlike WW-1 (same candidate set at every level), here the candidate set changes per level — determined by the current digit. Look up the letter set for `digits.charAt(index)`, branch on each letter, then recurse with `index + 1`. No `start` or `used[]` needed because each level picks from a completely different pool.

**Steps in plain English:**

1. **Build phone map** — `String[] map = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"}`.
2. **Base case** — when `index == digits.length()`, `path` is a complete combination; add to results.
3. **Look up letters** — `String letters = map[digits.charAt(index) - '0']`.
4. **Loop letters** — for each `ch` in `letters`: TRY, RECURSE with `index + 1`, UNDO.

```java
public List<String> letterCombinations(String digits) {
    List<String> results = new ArrayList<>();
    if (digits.isEmpty()) {
        return results;
    }
    // Step 1 — phone map (indices 0-1 unused)
    String[] map = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
    backtrack(digits, map, 0, new StringBuilder(), results);
    return results;
}

private void backtrack(String digits, String[] map, int index,
                       StringBuilder path, List<String> results) {
    // Step 2 — base case: all digits consumed
    if (index == digits.length()) {
        results.add(path.toString());
        return;
    }
    // Step 3 — look up letters for current digit
    String letters = map[digits.charAt(index) - '0'];
    // Step 4 — branch on each letter
    for (char ch : letters.toCharArray()) {
        path.append(ch);
        backtrack(digits, map, index + 1, path, results);
        path.deleteCharAt(path.length() - 1);
    }
}
```

**Time:** O(4^n × n) worst case. **Space:** O(n) depth.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 784 Letter Case Permutation | Branch per character: letter → two choices (upper/lower); digit → one choice | No lookup map; branch on `Character.isLetter(c)`, recurse both cases |
| LC 131 Palindrome Partitioning | Branch on cut positions (where to end the current partition) — same per-level different choices | Check `isPalindrome(s, start, i)` before recursing; TRY/RECURSE/UNDO on substrings |
| LC 93 Restore IP Addresses | 4-segment structure — at each level pick 1-3 chars; validity gate (0-255, no leading zero) | Loop `end = start+1` to `start+3`; add dot separator; validate segment before recursing |

---

### WW-7 — LC 79 Word Search

Given a 2D character board and a word, return true if the word exists as a connected path (up/down/left/right, no cell reused).

**Brute force:** For every starting cell, try DFS in all directions, tracking visited cells in a separate `boolean[][]`. Mark/unmark as you enter/leave. This IS the optimal algorithm — O(m × n × 4^L) where L = word length. No smarter approach exists for exact string matching on a grid.

**Intuition bridge:** Grid backtracking replaces the `used[]` array with in-place board mutation: temporarily overwrite the cell with a sentinel (`'#'`) to mark it visited, then restore it after the recursive call. One character swap does both mark and unmark.

**Steps in plain English:**

1. **Outer loop** — try starting DFS from every cell `(r, c)` on the board.
2. **DFS base case** — when `index == word.length()`, all characters matched; return true.
3. **Bounds and match check** — if `r` or `c` is out of bounds, or `board[r][c] != word.charAt(index)`, return false.
4. **Mark visited** — overwrite `board[r][c]` with `'#'`.
5. **Recurse in 4 directions** — try `(r±1, c)` and `(r, c±1)` with `index + 1`.
6. **Restore** — write `word.charAt(index)` back to `board[r][c]`.
7. **Return** — true if any direction succeeded.

```java
public boolean exist(char[][] board, String word) {
    int m = board.length;
    int n = board[0].length;
    // Step 1 — try every starting cell
    for (int r = 0; r < m; r++) {
        for (int c = 0; c < n; c++) {
            if (dfs(board, word, r, c, 0)) {
                return true;
            }
        }
    }
    return false;
}

private boolean dfs(char[][] board, String word, int r, int c, int index) {
    // Step 2 — all characters matched
    if (index == word.length()) {
        return true;
    }
    // Step 3 — bounds and character check
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length
            || board[r][c] != word.charAt(index)) {
        return false;
    }
    // Step 4 — mark visited with sentinel
    char saved = board[r][c];
    board[r][c] = '#';
    // Step 5 — recurse 4 directions
    boolean found = dfs(board, word, r + 1, c, index + 1)
            || dfs(board, word, r - 1, c, index + 1)
            || dfs(board, word, r, c + 1, index + 1)
            || dfs(board, word, r, c - 1, index + 1);
    // Step 6 — restore
    board[r][c] = saved;
    // Step 7 — return result
    return found;
}
```

**Time:** O(m × n × 4^L). **Space:** O(L) call stack depth.

> Short-circuit evaluation (`||`) means DFS stops as soon as one direction returns true — no need to explore further.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 212 Word Search II | Search for multiple words simultaneously — Trie prunes dead paths early | Build Trie from word list; DFS checks `trie.children[board[r][c]-'a']` |
| LC 200 Number of Islands | Connected-component flood fill — same mark/restore (or separate `visited[]`) | No target string; flood-fill all '1's, count connected groups |
| LC 490 The Maze | Ball rolls until hitting a wall — DFS with distance tracking | Loop in each direction until wall; mark stopped positions visited |

---

### WW-8 — LC 51 N-Queens

Place n queens on an n×n board so no two queens attack each other; return all valid board configurations.

**Brute force:** Try all n^n column placements (one queen per row, any column); after placing all n queens, scan the entire board for conflicts. O(n^n × n²) — hopelessly slow even for n = 10.

**Intuition bridge:** Place exactly one queen per row (rows are never in conflict). Three boolean arrays give O(1) conflict detection: `cols[c]` for columns, `diag1[r+c]` for `\` diagonals (sum is constant on each diagonal), `diag2[r−c+n−1]` for `/` diagonals (difference is constant). Setting/clearing these three arrays is the only state management needed.

### 🎨 Visual — Diagonal index encoding for n=4

```
Board indices (row, col):

(0,0)(0,1)(0,2)(0,3)      diag1 = r+c:   0 1 2 3
(1,0)(1,1)(1,2)(1,3)                      1 2 3 4
(2,0)(2,1)(2,2)(2,3)                      2 3 4 5
(3,0)(3,1)(3,2)(3,3)                      3 4 5 6

diag2 = r-c+n-1:          3 2 1 0
                           4 3 2 1
                           5 4 3 2
                           6 5 4 3

Any cell on the same \ diagonal shares the SAME r+c value.
Any cell on the same / diagonal shares the SAME r-c+n-1 value.

KEY INVARIANT:
   cols[], diag1[], diag2[] together give O(1) conflict checks
   with no board scanning — three array lookups replace O(n) scan.
```

**Steps in plain English:**

1. **Initialize** — empty board (all `'.'`) + three boolean arrays `cols[n]`, `diag1[2n]`, `diag2[2n]`.
2. **Base case** — when `row == n`, all queens placed; snapshot the board and return.
3. **Loop columns** for the current row.
4. **O(1) validity check** — `if (cols[c] || diag1[row+c] || diag2[row-c+n-1]) continue;`
5. **TRY** — place `'Q'` at `(row, c)` and set all three flags.
6. **RECURSE** to `row + 1`.
7. **UNDO** — restore `'.'` and clear all three flags.

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> results = new ArrayList<>();
    // Step 1 — empty board + constraint arrays
    char[][] board = new char[n][n];
    for (char[] row : board) {
        Arrays.fill(row, '.');
    }
    boolean[] cols = new boolean[n];
    boolean[] diag1 = new boolean[2 * n];
    boolean[] diag2 = new boolean[2 * n];
    backtrack(board, 0, n, cols, diag1, diag2, results);
    return results;
}

private void backtrack(char[][] board, int row, int n,
                       boolean[] cols, boolean[] diag1, boolean[] diag2,
                       List<List<String>> results) {
    // Step 2 — base case: all rows filled
    if (row == n) {
        List<String> snapshot = new ArrayList<>();
        for (char[] r : board) {
            snapshot.add(new String(r));
        }
        results.add(snapshot);
        return;
    }
    // Step 3 — try every column in current row
    for (int c = 0; c < n; c++) {
        // Step 4 — O(1) conflict check
        if (cols[c] || diag1[row + c] || diag2[row - c + n - 1]) {
            continue;
        }
        // Step 5 — TRY: place queen + set 3 flags
        board[row][c] = 'Q';
        cols[c] = true;
        diag1[row + c] = true;
        diag2[row - c + n - 1] = true;
        // Step 6 — RECURSE to next row
        backtrack(board, row + 1, n, cols, diag1, diag2, results);
        // Step 7 — UNDO: 4 mutations, 4 restores
        board[row][c] = '.';
        cols[c] = false;
        diag1[row + c] = false;
        diag2[row - c + n - 1] = false;
    }
}
```

**Time:** O(n!) with constraint pruning (much faster in practice). **Space:** O(n) depth + O(n²) board.

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 52 N-Queens II | Count solutions only — no board needed, just increment a counter at base case | Replace `List<List<String>> results` with `int count`; `count++` at base |
| LC 37 Sudoku Solver | Row/col/box constraint sets instead of diagonal sets; nested row/col scan | Replace diagonal arrays with `rows[r][d]`, `cols[c][d]`, `boxes[b][d]` for digits 1-9 |
| LC 1001 Grid Illumination | Lamp constraints stored in HashSets — same "set/clear on try/undo" idea | Mark lamp row/col/diag in HashSets on place; clear on remove |

---

<a id="practice-plan"></a>
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

<a id="gotchas"></a>
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

<a id="tldr"></a>
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
