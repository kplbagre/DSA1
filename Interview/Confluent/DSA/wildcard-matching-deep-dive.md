# LC 44 — Wildcard Matching — Four-Stage DP Deep Dive

> **Companion to:** `03-algo-problems.md` §3 (which shows the final answer in the
> Confluent problem format).
> This file explains WHY the recurrence works and walks all four stages, using
> the same format as `../../../DSA/DeepDive/dp-fundamentals.md` Family 5.

---

## 🎯 Goal

Read this when the tabulation solution in `03-algo-problems.md` §3 makes sense to
run but not to derive. This file covers:

1. Why this is a DP problem and which DP family it belongs to.
2. How to state the subproblem in English before touching code.
3. All four stages: brute recursion → memoization → tabulation → space optimization.
4. ASCII visualizations of the recursion tree and the DP table fill.

---

## 📖 Terminology

| Term | Plain-English meaning |
| --- | --- |
| **prefix-count form** | `solve(i, j)` means "does `s[0..i-1]` match `p[0..j-1]`?" — `i` and `j` are counts of characters **consumed**, not raw indices. Count form makes the base case "both zero = both exhausted = true" and lets tabulation coordinates match recursion arguments directly. |
| **`?`** | Pattern metachar (a character with special meaning in the pattern language) matching exactly ONE character — any character. |
| **`*`** | Pattern metachar matching any SEQUENCE of zero or more characters. |
| **frontier characters** | `s[i-1]` and `p[j-1]` — the "current" characters being compared when `i` chars of `s` and `j` chars of `p` have been consumed. |
| **`Boolean[][]` vs `boolean[][]`** | `Boolean` (capital B) is a boxed Java object. Uninitialized cells are `null` — the "not yet computed" sentinel. Primitive `boolean` defaults to `false`, which collides with a legitimate "this subproblem returns false" answer. Always use `Boolean[][]` for top-down DP memoization. |
| **memoization** | Top-down DP — run the recursion, but cache (store) each subproblem result on first computation. Every subsequent call to the same subproblem returns the cached value in O(1). |
| **tabulation** | Bottom-up DP — fill the answer table iteratively starting from base cases, no recursion. Avoids stack overhead. |
| **rolling rows** | Space optimization technique for 2D DP — keep only two 1D arrays (current row and previous row) instead of the full (m+1)×(n+1) table, reducing space from O(m·n) to O(n). |

---

## 🪜 The problem

Given string `s` and pattern `p`:
- `?` matches any **single** character.
- `*` matches any **sequence** of characters including the empty sequence.
- Return `true` if `p` matches `s` **completely** (every character of `s` must be
  accounted for — this is NOT substring matching).

**Quick examples:**

```
s = "adceb",  p = "*a*b"  → true    (* → "", a → a, * → "dce", b → b)
s = "acdcb",  p = "a*c?b" → false   (last ? matches b, nothing left for b)
s = "",        p = "***"   → true    (all * match empty sequence)
s = "abc",     p = "*b*"   → true    (* → "a", b → b, * → "c")
```

---

## 🧠 Why this is DP — Aditya Verma's identification triplet

| Question | Answer for Wildcard Matching |
| --- | --- |
| **What is changing across recursive calls?** | **Two indices** — `i` (chars consumed from `s`) and `j` (chars consumed from `p`). State is `(i, j)`. Total unique states: `(m+1) × (n+1)`. |
| **What are the choices at each step?** | **If `p[j-1]` is a literal or `?`:** one path — consume both frontier chars → recurse on `(i-1, j-1)`. **If `p[j-1]` is `*`:** two paths — (a) `*` matches empty: skip `*`, stay on `s` → `(i, j-1)`; (b) `*` eats one `s` char: consume `s[i-1]`, keep `*` active → `(i-1, j)`. |
| **What's the smallest valid input?** | `i == 0 AND j == 0` → `true` (both exhausted = full match). `i > 0, j == 0` → `false` (pattern exhausted, string remains). `i == 0, j > 0` → `true` ONLY if all remaining pattern chars are `*`. |

> **Family:** This is Family 5 (LCS / Strings DP) from `dp-fundamentals.md`. The
> state is `(i, j)` indexing two strings simultaneously — same shape as Edit
> Distance (LC 72) and Distinct Subsequences (LC 115). The unique part here is the
> `*` producing a two-way branch instead of the typical "match or mismatch" split.

---

## 🧭 State definition — in English before code

> **`solve(i, j)`** = "Does `s[0..i-1]` completely match `p[0..j-1]`?"
>
> `i` = number of chars consumed from `s` — ranges 0 to m.
> `j` = number of chars consumed from `p` — ranges 0 to n.
>
> **Initial call:** `solve(m, n)` — "does the whole `s` match the whole `p`?"

**Why prefix counts and not raw indices?**

The `03-algo-problems.md` §3 brute force uses suffix indices: `solve(i, j)` means
"does `s[i..]` match `p[j..]`?" with base case `j == p.length()`. That is a valid
standalone recursion but uses a DIFFERENT convention than the tabulation in the same
file, which is prefix-based. When you then try to convert recursion → tabulation
mechanically (the "flip the loops" trick), mismatched conventions cause silent index
bugs. This file uses prefix counts in ALL four stages so every stage shares the same
coordinates.

> **Lesson learned the hard way (Aug 2026):** Mixing suffix-form recursion with
> prefix-form tabulation in the same derivation chain is the #1 source of off-by-one
> bugs in Wildcard/Regex DP. Pick one form at Stage 1 and carry it through Stage 4.

---

## 🧭 The recurrence — in English before code

Given `i` chars of `s` and `j` chars of `p` consumed, look at the frontier chars
`s[i-1]` and `p[j-1]`:

```
─── BASE CASES ──────────────────────────────────────────────────────────────────

  solve(0, 0) = true           both exhausted → full match
  solve(i, 0) = false          (i > 0) pattern gone, string remains → no match
  solve(0, j) = solve(0, j-1)  only if p[j-1] == '*'   (* matches empty string)
             = false           if p[j-1] != '*'

─── RECURSIVE CASES (i > 0, j > 0) ─────────────────────────────────────────────

  CASE A — p[j-1] is a LITERAL char or '?':
    match: (p[j-1] == s[i-1])  OR  (p[j-1] == '?')
    → if match:  solve(i, j) = solve(i-1, j-1)   consume both frontier chars
    → else:      solve(i, j) = false

  CASE B — p[j-1] is '*':
    Option 1 — '*' matches empty: skip '*', do not consume any s char
               → solve(i, j-1)
    Option 2 — '*' eats one more s char: consume s[i-1], keep '*' in place
               → solve(i-1, j)
    solve(i, j) = Option1 OR Option2

─────────────────────────────────────────────────────────────────────────────────
```

**Why does "keep `*` at j" work?** When Option 2 is taken, `j` doesn't change —
the next call still sees `p[j-1] == '*'` and faces the same two options again. This
is how `*` "absorbs" arbitrarily many `s` characters: repeated Option 2 calls
consume them one by one, and the recursion stops eating when it either runs out of
`s` (hits the `i == 0` base case) or switches to Option 1 and moves `j` forward.

---

## 🎨 Visual — decision tree for s = "ab", p = "a*"

```
Initial call: solve(2, 2)
  i=2 → s[0..1] = "ab"
  j=2 → p[0..1] = "a*"
  frontier: s[1]='b', p[1]='*' → CASE B

                    solve(2, 2)
                   p[1]='*' → CASE B
         ┌─────────────────────────────┐
    [opt 1]                       [opt 2]
  '*' matches empty           '*' eats s[1]='b'
  advance p only (j-1)       advance s only (i-1)
         │                            │
    solve(2, 1)                  solve(1, 2)
   frontier:                   frontier:
   s[1]='b', p[0]='a'          s[0]='a', p[1]='*'
   CASE A — MISMATCH            CASE B again
     → false               ┌────────────────────┐
                       [opt 1]              [opt 2]
                       skip '*'           '*' eats 'a'
                            │                  │
                       solve(1, 1)        solve(0, 2)
                      frontier:           i=0, base chain:
                      s[0]='a',p[0]='a'   p[1]='*' → solve(0,1)
                      CASE A — MATCH       p[0]='a'≠'*' → false
                            │               = false
                       solve(0, 0)
                          = true ✓

─── Rollup (leaves → root) ──────────────────────────────────────────────────────

  solve(0, 0) = true
  solve(1, 1) = solve(0, 0)  = true      (MATCH: 'a' == 'a')
  solve(0, 1) = false                    (p[0]='a' ≠ '*', can't match empty)
  solve(0, 2) = solve(0, 1)  = false     (p[1]='*' → delegate to j-1)
  solve(1, 2) = true OR false = TRUE
  solve(2, 1) = false                    (MISMATCH: 'b' ≠ 'a')
  solve(2, 2) = false OR TRUE = TRUE ✓

─── What memoization eliminates ─────────────────────────────────────────────────

  The tree above is small. With a long pattern full of '*' characters
  (e.g., p = "****") and a long string, the same (i, j) state is reached
  from exponentially many paths — different combinations of "skip '*'"
  and "eat one char". Memoization collapses each unique (i, j) to one
  computation regardless of how many paths arrive at it.

KEY INVARIANT:
  '*' creates a TWO-WAY BRANCH at every step:
    ▶ Option 1 (j-1):  '*' matches empty — "stop consuming, advance p"
    ▶ Option 2 (i-1):  '*' eats one s char — "consume s[i-1], stay at j"
  Repeated Option 2 calls let '*' eat any number of s characters.
  Brute force: O(2^(m+n)) — one branch doubles at every '*'.
  With memo: O(m·n) — each unique (i, j) computed once.
```

---

## Stage 1 — Brute Recursion

> O(2^(m+n)) time, O(m+n) space (recursion stack depth)

**Call site:** `solveBrute(s, p, s.length(), p.length())`

```java
private boolean solveBrute(String s, String p, int i, int j) {
    // Base — both exhausted: full match
    if (i == 0 && j == 0) {
        return true;
    }
    // Base — pattern exhausted, string remains: no match
    if (j == 0) {
        return false;
    }
    // Base — string exhausted, pattern remains:
    // true only if all remaining pattern chars are '*'
    if (i == 0) {
        return p.charAt(j - 1) == '*' && solveBrute(s, p, 0, j - 1);
    }

    char pc = p.charAt(j - 1);
    char sc = s.charAt(i - 1);

    // CASE A — literal or '?': single choice, consume both frontier chars
    if (pc == sc || pc == '?') {
        return solveBrute(s, p, i - 1, j - 1);
    }

    // CASE B — '*': two choices
    if (pc == '*') {
        // Option 1: '*' matches empty — skip '*', keep s pointer
        boolean skipStar = solveBrute(s, p, i, j - 1);
        // Option 2: '*' eats one s char — consume s[i-1], keep '*' active
        boolean eatOne = solveBrute(s, p, i - 1, j);
        return skipStar || eatOne;
    }

    // Literal mismatch
    return false;
}
```

**Why O(2^(m+n)):** Each `*` in `p` creates a binary branch (Option 1 vs Option 2).
In the worst case (`p = "****..."` with only stars), the tree has depth m+n and
branches at every node → 2^(m+n) leaves.

---

## Stage 2 — Memoization (top-down DP)

> O(m·n) time, O(m·n) space (table + stack)

**Mechanical change from Stage 1:** add `Boolean[][] memo`, check cache on entry,
store result before returning. The state, transitions, and base cases are unchanged.

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();
    // Boolean (boxed) — null signals "not yet computed"
    // Size (m+1) x (n+1) covers i from 0..m and j from 0..n inclusive
    Boolean[][] memo = new Boolean[m + 1][n + 1];
    return solveMemo(s, p, m, n, memo);
}

private boolean solveMemo(String s, String p, int i, int j, Boolean[][] memo) {
    // Base — both exhausted
    if (i == 0 && j == 0) {
        return true;
    }
    // Base — pattern exhausted
    if (j == 0) {
        return false;
    }
    // Base — string exhausted: true iff remaining p is all '*'
    if (i == 0) {
        return p.charAt(j - 1) == '*' && solveMemo(s, p, 0, j - 1, memo);
    }

    // Cache hit — return stored result (may be true or false)
    if (memo[i][j] != null) {
        return memo[i][j];
    }

    char pc = p.charAt(j - 1);
    char sc = s.charAt(i - 1);
    boolean result;

    // CASE A — literal or '?'
    if (pc == sc || pc == '?') {
        result = solveMemo(s, p, i - 1, j - 1, memo);
    } else if (pc == '*') {
        // CASE B — '*': skip '*' OR eat one s char
        result = solveMemo(s, p, i, j - 1, memo)
              || solveMemo(s, p, i - 1, j, memo);
    } else {
        result = false;
    }

    memo[i][j] = result;
    return result;
}
```

**Why O(m·n):** With the cache, each unique `(i, j)` pair is computed exactly once.
There are `(m+1) × (n+1)` pairs; each does O(1) work (one char comparison + OR).
The recursion stack depth is at most m+n.

---

## Stage 3 — Tabulation (bottom-up DP)

> O(m·n) time, O(m·n) space. No recursion stack.

**Steps in plain English:**

1. **Allocate `dp[m+1][n+1]`** where `dp[i][j]` = does `s[0..i-1]` completely match
   `p[0..j-1]`? All cells default to `false`.
2. **Seed `dp[0][0] = true`** — empty string matches empty pattern.
3. **Fill first row (i = 0, empty s prefix):** `dp[0][j] = dp[0][j-1]` if
   `p[j-1] == '*'`, else leave `false`. A `*` at position j can extend an
   all-stars match to the empty string only if everything to its left also matched.
4. **Fill rows i = 1 to m.** For each row `dp[i][0] = false` (i chars of s, empty
   pattern = no match). For columns j = 1 to n:
   - **CASE A — `p[j-1]` is literal or `?`:** `dp[i][j] = dp[i-1][j-1]` when
     `s[i-1] == p[j-1]` OR `p[j-1] == '?'`, else `false` (mismatch).
   - **CASE B — `p[j-1]` is `*`:** `dp[i][j] = dp[i][j-1]` (skip `*`) OR
     `dp[i-1][j]` (`*` eats `s[i-1]`).
5. **Return `dp[m][n]`.**

### 🎨 Visual — DP table fill for s = "abc", p = "*b*"

```
s = "abc"  (m=3),  p = "*b*"  (n=3)    Expected: true

Pattern chars:  p[0]='*'   p[1]='b'   p[2]='*'

                  j=0      j=1       j=2       j=3
                empty     "*"      "*b"      "*b*"
       ┌────────┬────────┬────────┬────────┬────────┐
 i=0   │   T    │   T    │   F    │   F    │  base row
 ("")  └────────┴────────┴────────┴────────┘
       ┌────────┬────────┬────────┬────────┬────────┐
 i=1   │   F    │   T    │   F    │   F    │
 ("a") └────────┴────────┴────────┴────────┘
       ┌────────┬────────┬────────┬────────┬────────┐
 i=2   │   F    │   T    │   T    │   T    │
 ("ab")└────────┴────────┴────────┴────────┘
       ┌────────┬────────┬────────┬────────┬────────┐
 i=3   │   F    │   T    │   F    │   T    │  ← ANSWER
 ("abc")└───────┴────────┴────────┴────────┘
                                       ↑
                               dp[3][3] = TRUE ✓

─── Notable cells (how the values were derived) ─────────────────────────────────

  BASE ROW (i=0):
    dp[0][1]: p[0]='*', dp[0][0]=T  → T    ('*' extends empty match)
    dp[0][2]: p[1]='b', not '*'     → F    (can't match empty s with literal)
    dp[0][3]: p[2]='*', dp[0][2]=F  → F    ('*' can't rescue a false left)

  ROW i=1 (s[0]='a'):
    dp[1][1]: CASE B: dp[1][0]=F || dp[0][1]=T   → T   ('*' eats 'a')
    dp[1][2]: CASE A: s[0]='a' ≠ p[1]='b'        → F   (mismatch)
    dp[1][3]: CASE B: dp[1][2]=F || dp[0][3]=F   → F

  ROW i=2 (s[1]='b'):
    dp[2][1]: CASE B: dp[2][0]=F || dp[1][1]=T   → T   ('*' eats 'b')
    dp[2][2]: CASE A: s[1]='b' == p[1]='b' → dp[1][1]=T  → T  ◆ MATCH
    dp[2][3]: CASE B: dp[2][2]=T || dp[1][3]=F   → T   ('*' skips empty)

  ROW i=3 (s[2]='c'):
    dp[3][1]: CASE B: dp[3][0]=F || dp[2][1]=T   → T   ('*' eats 'c')
    dp[3][2]: CASE A: s[2]='c' ≠ p[1]='b'        → F   (mismatch)
    dp[3][3]: CASE B: dp[3][2]=F || dp[2][3]=T   → T ✓

─── Cell-type key ───────────────────────────────────────────────────────────────

  CASE A cell (literal/?) :  dp[i][j] reads  dp[i-1][j-1]  (diagonal)
  CASE B cell ('*')       :  dp[i][j] reads  dp[i][j-1]    (left, skip '*')
                                         ||  dp[i-1][j]    (above, eat char)
  BASE row, col 0         :  dp[0][j] = dp[0][j-1] if p[j-1]=='*', else false
                             dp[i][0] = false  (i>0)

KEY INVARIANT:
  A '*' column at position j propagates truth DOWNWARD through the column:
    dp[i][j] = ... || dp[i-1][j]
  If dp[i-1][j] is true, dp[i][j] is true regardless of s[i-1].
  That is how '*' "matches" arbitrarily many s characters:
    truth cascades down the column as long as there is any s char to eat.

  Column j=1 (p[0]='*') in this example:
    dp[0][1] = T  ← base: '*' matches ""
    dp[1][1] = T  ← dp[0][1]=T, '*' eats 'a'
    dp[2][1] = T  ← dp[1][1]=T, '*' eats 'b'
    dp[3][1] = T  ← dp[2][1]=T, '*' eats 'c'
  A LITERAL column can only become true via the diagonal (dp[i-1][j-1]).
  Truth never propagates downward in a literal column.
```

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();

    // Step 1 — allocate; all false by default
    boolean[][] dp = new boolean[m + 1][n + 1];

    // Step 2 — both empty = match
    dp[0][0] = true;

    // Step 3 — first row: empty s vs pattern prefix p[0..j-1]
    for (int j = 1; j <= n; j++) {
        if (p.charAt(j - 1) == '*') {
            dp[0][j] = dp[0][j - 1];
        }
    }

    // Step 4 — fill row by row
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1);
            char sc = s.charAt(i - 1);

            if (pc == sc || pc == '?') {
                // CASE A — match: consume both frontier chars (diagonal read)
                dp[i][j] = dp[i - 1][j - 1];
            } else if (pc == '*') {
                // CASE B — '*': skip '*' (look left) OR eat one s char (look above)
                dp[i][j] = dp[i][j - 1] || dp[i - 1][j];
            }
            // else: literal mismatch — dp[i][j] stays false
        }
    }

    // Step 5 — answer is the bottom-right corner
    return dp[m][n];
}
```

**Time:** O(m·n) — two nested loops; m rows × n columns; O(1) work per cell.
**Space:** O(m·n) — the full DP table.

---

## Stage 4 — Space Optimization (rolling rows)

> O(m·n) time, O(n) space

**What we can shed:** `dp[i][j]` reads only three neighbors:
- `dp[i-1][j-1]` — diagonal (previous row, previous column)
- `dp[i-1][j]` — above (previous row, same column)
- `dp[i][j-1]` — left (current row, previous column, already computed)

→ Only the previous row and the current row are needed simultaneously. Replace the
full 2D table with two 1D arrays: `prev` (row i-1) and `curr` (row i being built).

**Steps in plain English:**

1. **Initialize `prev` as the base row (i = 0):** `prev[0] = true`; fill `prev[j]`
   using the `*` chain rule (same as the Stage 3 first-row fill).
2. **For each row i = 1 to m:** allocate `curr[n+1]`. Set `curr[0] = false`. Fill
   `curr[j]` using the same CASE A / CASE B logic as Stage 3, reading from `prev`
   for the "above" and "diagonal" values.
3. **At end of each row:** set `prev = curr` (roll). The old `prev` is discarded.
4. **Return `prev[n]`** after all m rows.

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();

    // Step 1 — seed prev as the base row (i=0)
    // prev[j] = dp[i-1][j]: does s[0..i-2] match p[0..j-1]?
    boolean[] prev = new boolean[n + 1];
    prev[0] = true;
    for (int j = 1; j <= n; j++) {
        if (p.charAt(j - 1) == '*') {
            prev[j] = prev[j - 1];
        }
    }

    // Step 2 — build each row on top of prev
    for (int i = 1; i <= m; i++) {
        boolean[] curr = new boolean[n + 1];
        // curr[0] = false: i chars of s cannot match empty pattern

        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1);
            char sc = s.charAt(i - 1);

            if (pc == sc || pc == '?') {
                // CASE A — diagonal: prev[j-1] is dp[i-1][j-1]
                curr[j] = prev[j - 1];
            } else if (pc == '*') {
                // CASE B — left: curr[j-1] = dp[i][j-1]
                //          above: prev[j]  = dp[i-1][j]
                curr[j] = curr[j - 1] || prev[j];
            }
        }

        // Step 3 — roll: curr becomes prev for the next iteration
        prev = curr;
    }

    // Step 4 — answer is the last cell of the final rolled row
    return prev[n];
}
```

**Time:** O(m·n) — same loop structure as Stage 3.
**Space:** O(n) — two arrays of length n+1. The full (m+1)×(n+1) table is never
materialized.

> **Trade-off:** You cannot reconstruct WHICH characters `*` matched from a rolling
> solution (there is no table to backtrack through). If an interviewer asks "show me
> the actual match assignment," you need Stage 3's full table. Stage 4 only answers
> the yes/no question.

---

## ⚡ Complexity summary across all four stages

| Stage | Approach | Time | Space | Key trade-off |
| --- | --- | --- | --- | --- |
| 1 — Brute | Recursion | O(2^(m+n)) | O(m+n) stack | Exponential: '*' branches explode |
| 2 — Memo | Top-down DP | O(m·n) | O(m·n) table + O(m+n) stack | `Boolean[][]` with null sentinel |
| 3 — Tabulation | Bottom-up DP | O(m·n) | O(m·n) table | No stack; can reconstruct path |
| 4 — Rolling rows | Optimized bottom-up | O(m·n) | O(n) | Loses match-path reconstruction |

---

## 🧭 How this connects to other Family 5 problems

| Problem | LC | What changes from Wildcard Matching |
| --- | --- | --- |
| **Edit Distance** ⭐ | 72 | THE DP interview problem. Three-choice recurrence: replace, insert, delete. `1 + min(dp[i-1][j-1], dp[i][j-1], dp[i-1][j])` when chars differ. If chars match: `dp[i-1][j-1]` for free. No `*`. |
| **Regular Expression Matching** | 10 | `*` means "zero or more of the PRECEDING char" — `c*` = zero or more `c`s, not any sequence. Pattern advances two positions at a time. Same `(i, j)` state, harder recurrence. |
| **Distinct Subsequences** | 115 | Counting instead of boolean. When chars match: ADD `dp[i-1][j-1] + dp[i-1][j]` (use this char OR skip it — both paths are valid). |
| **LCS (Longest Common Subsequence)** | 1143 | `dp[i][j]` = LCS length, not bool. Match → `1 + dp[i-1][j-1]`. Mismatch → `max(dp[i-1][j], dp[i][j-1])`. The family template. |

---

## 🧩 Practice problems

| Problem | LC | Try when |
| --- | --- | --- |
| Wildcard Matching | 44 | ✅ After reading this file |
| Longest Common Subsequence | 1143 | ✅ Now — the canonical Family 5 warmup |
| Edit Distance ⭐ | 72 | ✅ Now — THE DP interview problem; same state shape |
| Regular Expression Matching | 10 | 🟡 After fully mastering LC 44 — same state, different `*` semantics |
| Distinct Subsequences | 115 | 🟡 After LC 1143 LCS |
| Longest Common Substring | 718 | ✅ Now — quick variation (reset dp on mismatch) |
| Shortest Common Supersequence | 1092 | 🔴 Senior+ / reference only |

---

## 🧾 TL;DR

```
LC 44 = Family 5 (2-string DP).  State (i, j).  O(m×n) time.

Three rules for p[j-1]:
  literal or '?'  →  dp[i][j] = dp[i-1][j-1]          (diagonal)
  '*' skip empty  →  dp[i][j] |= dp[i][j-1]            (left)
  '*' eat one     →  dp[i][j] |= dp[i-1][j]            (above)

Base cases:
  dp[0][0] = true
  dp[0][j] = dp[0][j-1]  iff p[j-1]=='*'
  dp[i][0] = false        (i > 0)

Default to Stage 3. Use Stage 4 when O(n) space is required.
Use Boolean[][] (not boolean[][]) for Stage 2.
Use prefix-count form throughout — never mix suffix recursion with prefix tabulation.
```

**Cross-references:**
- Quick-reference entry: `03-algo-problems.md` §3
- Family 5 full template: `../../../DSA/DeepDive/dp-fundamentals.md` §Family 5
- Edit Distance (closest cousin): `dp-fundamentals.md` Family 5 variants table

---

# LC 10 — Regular Expression Matching — Four-Stage DP Deep Dive

> **Same file as LC 44** because the two problems share the same DP shape and are
> best studied as a pair. Read the LC 44 sections first; this chapter only explains
> what is DIFFERENT.

---

## 🎯 The critical difference from LC 44

In LC 44, `*` is standalone and means "any sequence." In LC 10, `*` is a SUFFIX
modifier: it always attaches to the preceding character as a **pair `x*`**, meaning
"zero or more occurrences of `x`." The `*` character itself never appears alone and
never starts the pattern.

| | LC 44 | LC 10 |
| --- | --- | --- |
| `*` meaning | any sequence of characters | zero or more of the PRECEDING char |
| Unit of consumption | `*` alone (j advances by 1) | the `x*` pair (j advances by 2) |
| "zero match" option | `dp[i][j-1]` (skip `*`, stay on s) | `dp[i][j-2]` (skip the entire `x*` pair) |
| "one more" condition | always (any char) | only if `s[i-1]` matches the preceding char or `.` |
| `.*` | two separate rules | means "any sequence" — same effect as `*` in LC 44 |
| `.` char | no `.` metachar | `.` matches any single character (like `?` in LC 44) |

---

## 🪜 The problem

Given string `s` and pattern `p` where:
- `.` matches any **single** character.
- `*` means "zero or more of the immediately preceding element."
- `p` is guaranteed NOT to start with `*` and NOT to have two consecutive `*`.
- Return `true` if `p` matches `s` **completely**.

**Examples:**

```
s = "aa",    p = "a*"    → true    (a* = two a's)
s = "aa",    p = "a"     → false   (only matches one a)
s = "ab",    p = ".*"    → true    (.* = any sequence of any char)
s = "aab",   p = "c*a*b" → true    (c*=zero c's, a*=two a's, b=b)
s = "mississippi", p = "mis*is*p*." → false
```

---

## 🧠 Identification triplet applied to LC 10

| Question | Answer for LC 10 |
| --- | --- |
| **What is changing across recursive calls?** | Same as LC 44: two indices `(i, j)`. State is `(i, j)`. |
| **What are the choices at each step?** | **If `p[j-1]` is a literal or `.`:** consume both → `(i-1, j-1)`. **If `p[j-1]` is `*`:** (a) zero occurrences of `p[j-2]` — skip the `x*` pair → `(i, j-2)`; (b) one or more — ONLY if `s[i-1]` matches `p[j-2]` (as a literal or via `.`) → `(i-1, j)`. |
| **What's the smallest valid input?** | `(0, 0)` = `true`. `(i, 0)` = `false` for i > 0. `(0, j)` = `true` only if all remaining pattern can produce zero chars — i.e., every char at odd positions (1-indexed) is `*`. |

---

## 🧭 State definition

Same as LC 44:

> **`solve(i, j)`** = "Does `s[0..i-1]` completely match `p[0..j-1]`?"

---

## 🧭 The recurrence — in English before code

```
─── BASE CASES ──────────────────────────────────────────────────────────────────

  solve(0, 0) = true
  solve(i, 0) = false           (i > 0) — string remains, pattern exhausted
  solve(0, j):
    if p[j-1] == '*':  solve(0, j) = solve(0, j-2)   // x* matches zero chars
    else:              solve(0, j) = false

─── RECURSIVE CASES (i > 0, j > 0) ─────────────────────────────────────────────

  CASE A — p[j-1] is a LITERAL or '.':
    match: p[j-1] == s[i-1]  OR  p[j-1] == '.'
    → if match:  solve(i, j) = solve(i-1, j-1)
    → else:      solve(i, j) = false

  CASE B — p[j-1] is '*':
    Let  x = p[j-2]  (the character '*' is modifying — always exists by constraint)
    charMatches = (s[i-1] == x)  OR  (x == '.')
    Option 1 — zero occurrences of x: skip the entire x* pair
               → solve(i, j-2)
    Option 2 — one or more occurrences: only if charMatches
               → charMatches  &&  solve(i-1, j)
    solve(i, j) = Option1 OR Option2

─────────────────────────────────────────────────────────────────────────────────
```

**Understanding `.` in Case A — the "transparent gate" mental model:**

> **Critical distinction:** `.` alone = EXACTLY one character, any character (same as `?` in LC 44).
> `.*` = the `*` adds "zero or more" — `.` and `*` are two separate rules stacked.
> Confusing `.` with `.*` is the #1 source of wrong answers on this problem.

Think of Case A as a single-character gate check at the frontier:

```
  p[j-1] is a literal 'a'  →  named gate:       opens ONLY if s[i-1] == 'a'
  p[j-1] is '.'            →  transparent gate:  opens for ANY single character

  Gate opens  → walk through: consume one char from s AND one from p → solve(i-1, j-1)
  Gate closed → dead end → false
```

Concrete examples of the gate in action:

```
  s[i-1]='b',  p[j-1]='a'  →  named gate, 'b'≠'a'  → CLOSED  → false
  s[i-1]='a',  p[j-1]='a'  →  named gate, 'a'='a'   → OPEN    → solve(i-1, j-1)
  s[i-1]='b',  p[j-1]='.'  →  transparent gate       → OPEN    → solve(i-1, j-1)
  s[i-1]='9',  p[j-1]='.'  →  transparent gate       → OPEN    → solve(i-1, j-1)
  s[i-1]=' ',  p[j-1]='.'  →  transparent gate       → OPEN    → solve(i-1, j-1)
```

**Case A NEVER branches.** It is a single path forward (if gate opens) or immediate false (if closed).
Only Case B (`*`) branches. This asymmetry is what makes `*` the expensive case.

**Why `j-2` for the zero-occurrence option?** When `x*` produces zero characters,
both `x` (at `p[j-2]`) and `*` (at `p[j-1]`) are skipped. The pattern pointer
jumps back two positions, not one. This is the single biggest mechanical difference
from LC 44, where skipping `*` advances `j` by only 1.

**How `.*` becomes "match any sequence":** When `p[j-1]=='*'` and `p[j-2]=='.'`,
Option 2 is `charMatches && solve(i-1, j)` where `charMatches = (x=='.')` = always
true. So `.*` reduces to: skip it entirely (`solve(i, j-2)`) OR eat one char
(`solve(i-1, j)`) — identical to `*` in LC 44.

---

## 🎨 Visual — decision tree for s = "aa", p = "a*"

```
Initial call: solve(2, 2)
  i=2 → s[0..1]="aa",  j=2 → p[0..1]="a*"
  frontier: p[1]='*', preceding p[0]='a'
  s[1]='a' matches p[0]='a' → charMatches = true

                    solve(2, 2)
                  p[1]='*' → CASE B
        ┌─────────────────────────────────┐
   [opt 1]                           [opt 2]
 zero 'a': skip "a*"             one more 'a': eat s[1]
 j-2 → solve(2, 0)               charMatches=T → solve(1, 2)
      ↓                                  ↓
   j=0, i=2>0                    p[1]='*', preceding p[0]='a'
     → false                     s[0]='a' matches p[0]='a' → T
                         ┌─────────────────────────────────┐
                    [opt 1]                           [opt 2]
                  zero 'a': skip "a*"             one more 'a': eat s[0]
                  j-2 → solve(1, 0)              solve(0, 2)
                       ↓                              ↓
                   j=0, i=1>0               i=0, j=2, p[1]='*'
                     → false                base: solve(0, j-2)
                                                = solve(0, 0)
                                                = TRUE ✓

─── Rollup ──────────────────────────────────────────────────────────────────────

  solve(0, 0) = true
  solve(0, 2) = solve(0, 0) = true   (p[1]='*' → jump to j-2=0)
  solve(1, 0) = false
  solve(1, 2) = false OR true = TRUE
  solve(2, 0) = false
  solve(2, 2) = false OR TRUE = TRUE ✓

KEY INVARIANT:
  Every '*' at p[j-1] creates TWO branches, but the branches are ASYMMETRIC:
    ▶ Option 1 (→ j-2):  always valid — skip "x*" entirely regardless of s
    ▶ Option 2 (→ i-1):  conditional — only when s[i-1] matches p[j-2]
  In LC 44, Option 2 was always valid (any char). In LC 10, it requires a match.
  The "j-2 jump" (not j-1) for Option 1 is the mechanical fingerprint of LC 10.
```

---

## 🎨 Visual — decision tree for s = "ab", p = "a." (Case A with '.')

```
─── SUCCESS CASE: s="ab", p="a." ────────────────────────────────────────────────

Initial call: solve(2, 2)
  i=2 → s[0..1]="ab",  j=2 → p[0..1]="a."
  frontier: p[1]='.', s[1]='b'

                    solve(2, 2)
               p[1]='.' → CASE A
         '.' = transparent gate: opens for ANY single char
         s[1]='b' → gate opens ✓ → consume both → solve(1, 1)
                              ↓
                         solve(1, 1)
                    p[0]='a' → CASE A
                    'a' = named gate: opens only for 'a'
                    s[0]='a' → 'a'='a' gate opens ✓ → solve(0, 0)
                                        ↓
                                   solve(0, 0)
                                 base case: both exhausted
                                     → TRUE ✓

─── FAILURE CASE: s="ab", p=".a" ───────────────────────────────────────────────

Initial call: solve(2, 2)
  frontier: p[1]='a' (LITERAL), s[1]='b'

                    solve(2, 2)
               p[1]='a' → CASE A
         'a' = named gate: opens only for 'a'
         s[1]='b' → 'b'≠'a' gate CLOSED ✗ → false

  Note: we never even reach the '.' at p[0].
  Case A is a single path, not a branch — one mismatch ends it immediately.

─── Rollup for success case ─────────────────────────────────────────────────────
  solve(0, 0) = true
  solve(1, 1) = true   p[0]='a', s[0]='a' → literal match → solve(0,0) = true
  solve(2, 2) = true   p[1]='.', s[1]='b' → transparent gate → solve(1,1) = true

KEY INVARIANT for Case A ('.' and literals):
  Case A never branches — it is a SINGLE path forward or an immediate dead end.
  '.' = transparent gate → ALWAYS opens, consumes one s-char.
  literal = named gate   → opens ONLY on exact char match.
  Both gates: if open → exactly one step forward to (i-1, j-1).
  Branching ONLY happens in Case B ('*'). This is what makes '*' the expensive case.
```

---

## Stage 1 — Brute Recursion

> O(2^(m+n)) time, O(m+n) space (stack)

```java
private boolean solveBrute(String s, String p, int i, int j) {
    // Base — both exhausted
    if (i == 0 && j == 0) {
        return true;
    }
    // Base — pattern exhausted, string remains
    if (j == 0) {
        return false;
    }
    // Base — string exhausted, pattern remains
    // true only if every remaining pair is x* (each x* can produce zero chars)
    if (i == 0) {
        return p.charAt(j - 1) == '*' && solveBrute(s, p, 0, j - 2);
    }

    char pc = p.charAt(j - 1);
    char sc = s.charAt(i - 1);

    // CASE A — literal or '.': one choice, consume both chars
    if (pc != '*') {
        if (pc == sc || pc == '.') {
            return solveBrute(s, p, i - 1, j - 1);
        }
        return false;
    }

    // CASE B — '*': two choices
    // p[j-2] is the character '*' is modifying (guaranteed to exist)
    char preceding = p.charAt(j - 2);
    boolean charMatches = (preceding == sc || preceding == '.');

    // Option 1: zero occurrences of preceding — skip the x* pair (j-2)
    boolean zeroOcc = solveBrute(s, p, i, j - 2);
    // Option 2: one more occurrence — only if current s char matches preceding
    boolean oneOrMore = charMatches && solveBrute(s, p, i - 1, j);
    return zeroOcc || oneOrMore;
}
// Call site: solveBrute(s, p, s.length(), p.length())
```

---

## Stage 2 — Memoization (top-down DP)

> O(m·n) time, O(m·n) space

Mechanical add-cache to Stage 1. `Boolean[][]` so `null` = "not yet computed."

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();
    Boolean[][] memo = new Boolean[m + 1][n + 1];
    return solveMemo(s, p, m, n, memo);
}

private boolean solveMemo(String s, String p, int i, int j, Boolean[][] memo) {
    if (i == 0 && j == 0) {
        return true;
    }
    if (j == 0) {
        return false;
    }
    if (i == 0) {
        return p.charAt(j - 1) == '*' && solveMemo(s, p, 0, j - 2, memo);
    }

    if (memo[i][j] != null) {
        return memo[i][j];
    }

    char pc = p.charAt(j - 1);
    char sc = s.charAt(i - 1);
    boolean result;

    if (pc != '*') {
        // CASE A — literal or '.'
        result = (pc == sc || pc == '.') && solveMemo(s, p, i - 1, j - 1, memo);
    } else {
        // CASE B — '*': preceding char is p[j-2]
        char preceding = p.charAt(j - 2);
        boolean charMatches = (preceding == sc || preceding == '.');
        boolean zeroOcc = solveMemo(s, p, i, j - 2, memo);
        boolean oneOrMore = charMatches && solveMemo(s, p, i - 1, j, memo);
        result = zeroOcc || oneOrMore;
    }

    memo[i][j] = result;
    return result;
}
```

---

## Stage 3 — Tabulation (bottom-up DP)

> O(m·n) time, O(m·n) space

**Steps in plain English:**

1. **Allocate `dp[m+1][n+1]`**, all `false`.
2. **Seed `dp[0][0] = true`**.
3. **Fill first row (i = 0, empty s).** Only `x*` pairs can match the empty string:
   `dp[0][j] = dp[0][j-2]` when `p[j-1] == '*'`, else leave `false`.
   Note j starts from 1; when j == 1, `p[0]` can't be `*` (problem constraint).
4. **Fill rows i = 1 to m.** For each cell `(i, j)`:
   - **CASE A — `p[j-1]` is literal or `.`:** `dp[i][j] = dp[i-1][j-1]` if char
     matches (`s[i-1] == p[j-1]` OR `p[j-1] == '.'`), else `false`.
   - **CASE B — `p[j-1]` is `*`:** Let `preceding = p[j-2]`.
     - `zeroOcc = dp[i][j-2]` (skip the `x*` pair in the current row)
     - `charMatches = (preceding == s[i-1]) || (preceding == '.')`
     - `oneOrMore = charMatches && dp[i-1][j]` (eat one char, keep `x*` active)
     - `dp[i][j] = zeroOcc || oneOrMore`
5. **Return `dp[m][n]`.**

### 🎨 Visual — DP table fill for s = "aab", p = "a*b"

```
s = "aab"  (m=3),  p = "a*b"  (n=3)    Expected: true  (a*=two a's, b=b)

Pattern chars:  p[0]='a'     p[1]='*'     p[2]='b'

                   j=0        j=1          j=2          j=3
                 (empty)     ("a")        ("a*")       ("a*b")
       ┌────────┬──────────┬────────────┬────────────┬────────────┐
 i=0   │   T    │    F     │     T      │     F      │   base row
 ("")  │        │ 'a'≠'*'  │ p[1]='*'   │ p[2]='b'   │
       │        │ → F      │→dp[0][0]=T │ ≠'*' → F   │
       ├────────┼──────────┼────────────┼────────────┤
 i=1   │   F    │    T     │     T      │     F      │
 ("a") │        │ CASE A:  │ CASE B:    │ CASE A:    │
       │        │ 'a'=='a' │ zero a's:  │ 'a'≠'b'   │
       │        │→dp[0][0] │  dp[1][0]  │ → F        │
       │        │  = T     │   =F       │            │
       │        │          │ one more:  │            │
       │        │          │ 'a'==p[0]  │            │
       │        │          │ →dp[0][2]  │            │
       │        │          │  =T → T    │            │
       ├────────┼──────────┼────────────┼────────────┤
 i=2   │   F    │    F     │     T      │     F      │
 ("aa")│        │ CASE A:  │ CASE B:    │ CASE A:    │
       │        │ 'a'=='a' │ zero a's:  │ 'a'≠'b'   │
       │        │→dp[1][0] │  dp[2][0]  │ → F        │
       │        │  = F     │   =F       │            │
       │        │          │ one more:  │            │
       │        │          │ 'a'==p[0]  │            │
       │        │          │ →dp[1][2]  │            │
       │        │          │  =T → T    │            │
       ├────────┼──────────┼────────────┼────────────┤
 i=3   │   F    │    F     │     F      │     T      │ ← ANSWER
 ("aab")│       │ 'b'≠'a' │ CASE B:    │ CASE A:    │
       │        │ → F      │ zero a's:  │ 'b'=='b'   │
       │        │          │  dp[3][0]  │ →dp[2][2]  │
       │        │          │   =F       │  = T ✓     │
       │        │          │ one more:  │            │
       │        │          │ 'b'≠p[0]  │            │
       │        │          │  =F → F   │            │
       └────────┴──────────┴────────────┴────────────┘
                                                ↑
                                        dp[3][3] = TRUE ✓

─── Key structural observation ──────────────────────────────────────────────────

  The "a*" column (j=2) stays TRUE for rows 0, 1, 2 because
  each new row's s char is 'a' — the char that '*' is repeating.
  When row 3 arrives with s[2]='b', the one-more branch fails
  (charMatches = 'b'=='a' = false) and zero-more is also false
  (dp[3][0]=F), so the column drops to F.
  The truth then "transfers" one cell right via the CASE A diagonal read
  at dp[3][3] = dp[2][2] = T.

KEY INVARIANT:
  A '*' cell at (i, j) in LC 10 reads TWO cells from the current row:
    dp[i][j-2]  ← zero occurrences: skip the whole "x*" pair
    dp[i-1][j]  ← one more occurrence (conditional on char match)
  Compare LC 44's '*' cell: reads dp[i][j-1] (left by 1, not 2) and dp[i-1][j].
  The j-2 vs j-1 difference is the ONLY mechanical change in the transition.
```

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();

    // Step 1 — allocate; all false by default
    boolean[][] dp = new boolean[m + 1][n + 1];

    // Step 2 — both empty = match
    dp[0][0] = true;

    // Step 3 — first row: x* pairs can match the empty string (produce zero chars)
    for (int j = 1; j <= n; j++) {
        if (p.charAt(j - 1) == '*') {
            // j >= 2 guaranteed since p cannot start with '*'
            dp[0][j] = dp[0][j - 2];
        }
    }

    // Step 4 — fill row by row
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1);
            char sc = s.charAt(i - 1);

            if (pc != '*') {
                // CASE A — literal or '.': consume both frontier chars if they match
                if (pc == sc || pc == '.') {
                    dp[i][j] = dp[i - 1][j - 1];
                }
            } else {
                // CASE B — '*': preceding char is p[j-2] (j >= 2 by constraint)
                char preceding = p.charAt(j - 2);
                boolean charMatches = (preceding == sc || preceding == '.');

                // Option 1: zero occurrences — skip the entire "x*" pair
                boolean zeroOcc = dp[i][j - 2];
                // Option 2: one or more occurrences — eat s[i-1], keep '*' active
                boolean oneOrMore = charMatches && dp[i - 1][j];
                dp[i][j] = zeroOcc || oneOrMore;
            }
        }
    }

    // Step 5 — answer
    return dp[m][n];
}
```

**Time:** O(m·n) — two nested loops; each of the m×n cells is computed in O(1).
**Space:** O(m·n) — the full DP table.

---

## Stage 4 — Space Optimization (rolling rows)

> O(m·n) time, O(n) space

Same two-array rolling pattern as LC 44. Key notes:
- `curr[j-2]` replaces `dp[i][j-2]` (two cells left in the current row being built).
- `prev[j]` replaces `dp[i-1][j]` (same column in the previous row).
- The diagonal `prev[j-1]` covers CASE A.

```java
public boolean isMatch(String s, String p) {
    int m = s.length();
    int n = p.length();

    // Step 1 — seed prev as the base row (i=0)
    boolean[] prev = new boolean[n + 1];
    prev[0] = true;
    for (int j = 1; j <= n; j++) {
        if (p.charAt(j - 1) == '*') {
            prev[j] = prev[j - 2];
        }
    }

    // Step 2 — build each row on top of prev
    for (int i = 1; i <= m; i++) {
        boolean[] curr = new boolean[n + 1];

        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1);
            char sc = s.charAt(i - 1);

            if (pc != '*') {
                // CASE A — diagonal: prev[j-1] is dp[i-1][j-1]
                if (pc == sc || pc == '.') {
                    curr[j] = prev[j - 1];
                }
            } else {
                // CASE B — preceding char is p[j-2]
                char preceding = p.charAt(j - 2);
                boolean charMatches = (preceding == sc || preceding == '.');

                // zero occurrences: curr[j-2] is dp[i][j-2] (already computed)
                boolean zeroOcc = curr[j - 2];
                // one or more: prev[j] is dp[i-1][j]
                boolean oneOrMore = charMatches && prev[j];
                curr[j] = zeroOcc || oneOrMore;
            }
        }

        // Step 3 — roll
        prev = curr;
    }

    return prev[n];
}
```

---

## ⚡ Complexity summary — LC 10

| Stage | Time | Space |
| --- | --- | --- |
| 1 — Brute | O(2^(m+n)) | O(m+n) stack |
| 2 — Memo | O(m·n) | O(m·n) + O(m+n) stack |
| 3 — Tabulation | O(m·n) | O(m·n) |
| 4 — Rolling rows | O(m·n) | O(n) |

---

## 🧾 TL;DR — LC 10 vs LC 44 side by side

```
Both use state (i, j), O(m×n) time, same four stages.
The ONLY recurrence differences:

                      LC 44 (Wildcard)       LC 10 (Regex)
─────────────────────────────────────────────────────────────
Single char any       p[j-1] == '?'          p[j-1] == '.'
Any sequence          p[j-1] == '*'          p[j-2..j-1] == ".*"
'*' zero match        dp[i][j-1]             dp[i][j-2]   ← j-2 not j-1 !!
'*' one-more match    dp[i-1][j]             dp[i-1][j]   (same)
                      (always valid)         (only if s[i-1] matches p[j-2])
─────────────────────────────────────────────────────────────

Base row (i=0):
  LC 44:  dp[0][j] = dp[0][j-1]  iff p[j-1]=='*'
  LC 10:  dp[0][j] = dp[0][j-2]  iff p[j-1]=='*'
```

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 2026 | File created. Full four-stage DP drill for LC 44 Wildcard Matching, following `dp-fundamentals.md` Family 5 format. Prefix-count form used throughout all four stages so each stage shares identical coordinate semantics with the tabulation table. Lesson-learned callout added for the suffix-vs-prefix indexing trap. |
| Aug 2026 | LC 10 Regular Expression Matching added as second chapter. Same four-stage structure; documents the `x*` pair semantics and the j-2 vs j-1 jump as the key mechanical difference from LC 44. |
| Aug 2026 | LC 10 — added "transparent gate" mental model for `.` in Case A, with explicit `.` vs `.*` distinction. Added second decision tree visual using `s="ab", p="a."` to show Case A in isolation (no `*` branching). |
