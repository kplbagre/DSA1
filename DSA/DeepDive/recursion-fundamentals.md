# Recursion — Fundamentals (Deep Dive)

> A from-scratch guide to recursion for cracking DSA interview problems in Java. Read this **before** you go deep on trees, graphs, backtracking, or DP — every one of those topics is recursion in disguise.

---

## 🎯 Why You're Reading This (The Goal)

Recursion is the **single most important mental model** in DSA after array indexing. Most interviewees I've watched fail tree, graph, or backtracking problems failed at one specific spot:

> *"I can describe what each recursive call should do — but I can't write it without getting confused about base case, what to return, or what state to mutate."*

This doc fixes that.

By the end you should be able to:

1. **Look at any recursive problem and identify the base case + recursive case in under 60 seconds**
2. **Trace any recursive call's call stack on paper** without losing track
3. **Recognize the 7 recursion patterns** that cover ~95% of interview problems
4. **Convert between iterative and recursive** for any algorithm
5. **Diagnose the 5 most common recursion bugs** (stack overflow, missing base case, mutation across branches, wrong return value, infinite recursion)

**Companion files:**
- `DeepDive/trees-fundamentals.md` — apply recursion to trees
- `DeepDive/integer-overflow-and-limits.md` — overflow traps that bite you in recursive solutions
- `Reference/recursion-reference.md` (will be created next) — the compact cheat sheet for daily revision

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

Every problem in this doc is tagged so you know whether to attempt it **now** or **wait** until you've covered more material.

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point in the doc | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs concepts from a later section in this same doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc (graphs, DP optimization, advanced backtracking pruning) | Read the problem and editorial for awareness; don't attempt cold |

> **Same lesson as trees doc:** I (Kapil) burned an hour on LC 124 before I had two-purpose recursion in my head. **Don't attempt 🔴 cold.** The tags exist precisely so you don't repeat that mistake.

---

## 🌀 What Is Recursion?

A function is **recursive** if it calls itself.

```java
public int factorial(int n) {
    if (n <= 1) {
        return 1;             // base case — stop recursing
    }
    return n * factorial(n - 1);   // recursive case — call self with smaller input
}
```

That's the entire idea. A recursive function:

1. **Defines a problem in terms of a smaller version of itself**
2. Has a **base case** — an input small enough that we can answer it directly without calling ourselves again
3. Has a **recursive case** — we transform the input into something smaller, call ourselves on it, and combine the result

If you remove either the base case or the "smaller" part of the recursive case, you get **infinite recursion** → stack overflow → crash.

### The simplest example: factorial

```
factorial(4)
= 4 × factorial(3)
= 4 × 3 × factorial(2)
= 4 × 3 × 2 × factorial(1)
= 4 × 3 × 2 × 1
= 24
```

Each call **defers** its answer until a smaller call returns. The base case (`n <= 1 → 1`) starts the chain unwinding.

> **Common confusion:** "Where does the answer live during the recursion?" → **In the call stack.** Every call has its own copy of `n`, waiting for its child call to return so it can multiply and pass the answer up. Java's stack is doing the bookkeeping you'd otherwise do yourself with a loop and a counter.

---

## 📖 Terminology (Memorize These)

| Term | Definition | Example |
| --- | --- | --- |
| **Base case** | The input(s) for which we return directly without recursing | `n <= 1` in factorial |
| **Recursive case** | The input(s) where we call ourselves with a smaller/simpler version | `n * factorial(n-1)` |
| **Call stack** | Java's data structure that tracks active function calls. Every recursive call adds a frame; every return pops one. | See diagram below |
| **Stack frame** | One entry on the call stack — holds the local variables and return address for one in-flight call | `factorial(3)` is one frame |
| **Recursion depth** | The maximum number of frames stacked at any moment | `factorial(1000)` → depth 1000 |
| **Stack overflow** | Crash from too many frames (Java default ≈ 5,000–10,000 frames) | Infinite recursion or huge inputs |
| **Tail call** | A recursive call that's the **last operation** in the function (no work after) | `return helper(n-1, acc)` is tail |
| **Memoization** | Caching results of recursive calls to avoid recomputing the same subproblem | Fibonacci with `Map<Integer, Integer>` cache |
| **Backtracking** | A recursion pattern that builds a partial solution, recurses, then **undoes** the build before trying another option | Subsets, permutations, N-queens |
| **Divide and conquer** | Split the input into independent halves, recurse on each, combine | Merge sort, quick sort, binary search |

---

## 🧠 The Recursive Thought Process — Three Questions

When you see a problem and think *"this might be recursive,"* ask three questions in order. Get all three right and the code writes itself.

### Question 1: What's the base case?

> *"What's the smallest input I can answer **without** calling myself?"*

For factorial: `n == 0` or `n == 1` → return `1`.
For "sum of an array": empty array → return `0`.
For "max depth of a tree": `null` node → return `0`.
For "reverse a linked list": `null` or single node → return the node itself.

**The base case is always the easiest part of the problem.** Don't overthink it.

### Question 2: What's the recursive case?

> *"Assuming the recursive call works correctly on a smaller input, how do I use its result?"*

For factorial: assume `factorial(n-1)` returns `(n-1)!`. Then `n!` = `n × factorial(n-1)`.
For sum of array: assume `sum(arr, i+1)` returns the sum of the rest. Then `sum(arr, i)` = `arr[i] + sum(arr, i+1)`.
For max depth: assume `maxDepth(left)` and `maxDepth(right)` work. Then this node's depth = `1 + max(left, right)`.

**This is the leap of faith.** You don't trace the recursion — you trust it.

### Question 3: How do I combine the result(s) with the current step?

> *"What 'work' do I do at the current level using the recursive result(s)?"*

Sometimes it's multiplication (factorial). Sometimes it's addition (sum). Sometimes it's `max` or `min` (depth, diameter). Sometimes it's just passing the result through unchanged (search). Sometimes it's joining strings or appending to a list (path collection).

> **The 3-question template:**
> ```
> ReturnType solve(input) {
>     if (input is base case) {
>         return baseValue;                    // Question 1
>     }
>     ReturnType subResult = solve(smaller(input));   // Question 2 — leap of faith
>     return combine(input, subResult);        // Question 3
> }
> ```

Memorize this skeleton. It is the spine of every recursive function you'll ever write.

---

## 🔄 Parameter-Way vs Functional-Way (Striver's Mental Model)

> **Every recursion problem can be solved in one of two styles.** Understanding both — and knowing when to use each — is the single most freeing realization in early recursion practice.

### The two styles in one picture

| Style | Where the answer lives | Return type | Mental hook |
| --- | --- | --- | --- |
| **Functional-way** | In the **return value** — each call returns its scope's answer; combine and bubble up | Non-`void` (e.g., `int`, `boolean`, `ListNode`) | *"I tell my parent the answer."* |
| **Parameter-way** | In a **parameter** — the running answer is carried down as state; written/printed at the base case | Often `void`; sometimes a side-effect via instance field | *"I keep updating the answer as I go down."* |

### Side-by-side: Sum of first N

**Steps in plain English (Functional-way):**

1. **Base case** — `n == 0` returns `0` directly.
2. **Recurse** — trust `sum(n-1)` returns the sum of `1..n-1`.
3. **Combine** — return `n + sub`.

```java
// Functional-way — answer flows UP through return values
int sum(int n) {
    // Step 1 — base case
    if (n == 0) {
        return 0;
    }
    // Step 2 — leap of faith on smaller input
    int sub = sum(n - 1);
    // Step 3 — combine and bubble up
    return n + sub;
}
```

**Steps in plain English (Parameter-way):**

1. **Public entry** — kick off helper with accumulator `0`.
2. **Base case** — when `n == 0`, the accumulator already holds the answer; print or return it.
3. **Recurse** — pass `n - 1` and `acc + n` down (no work after the call).

```java
// Parameter-way — answer flows DOWN as a parameter
void sumHelper(int n, int acc) {
    // Step 2 — base case: accumulator has the answer
    if (n == 0) {
        System.out.println(acc);
        return;
    }
    // Step 3 — pass updated acc down
    sumHelper(n - 1, acc + n);
}

public void sum(int n) {
    // Step 1 — seed the accumulator
    sumHelper(n, 0);
}
```

### When to choose which

| Use Functional-way when... | Use Parameter-way when... |
| --- | --- |
| The answer composes naturally from sub-answers (sum, max, count, depth) | You need to print **on the way** (during the descent) |
| You want a clean, single-method solution | You're collecting partial state into a list/path |
| The problem says *"return X"* | Backtracking — `path` is a parameter, results is a parameter |
| Tree problems where you bubble heights/sums up | DFS where the "current state" matters at each visit |

> **Striver's key point:** subsequence and backtracking problems are **almost always parameter-way** — the partial `path` you're building is the parameter you carry down. Functional-way is for problems where each scope has its own "answer."

### The same problem, both ways — Print 1 to N

**Parameter-way (most natural here):**

```java
void print1ToN(int i, int n) {
    // Step 1 — base case
    if (i > n) {
        return;
    }
    // Step 2 — print the current value
    System.out.println(i);
    // Step 3 — recurse on the next value
    print1ToN(i + 1, n);
}
// Caller: print1ToN(1, n);
```

**Functional-way (less natural — print after the recursive call):**

```java
void print1ToN(int n) {
    // Step 1 — base case
    if (n < 1) {
        return;
    }
    // Step 2 — recurse FIRST so smaller values print first
    print1ToN(n - 1);
    // Step 3 — print AFTER recursion (post-order)
    System.out.println(n);
}
// Caller: print1ToN(n);
```

> **Mental hook:** the functional-way version prints `1, 2, 3, ..., n` because the prints happen on the **return** path (deepest call returns first, prints `1`, then `2`, etc.). This is exactly the "post-order" idea you'll see in trees.

### Print N to 1 — flip the order

```java
// Parameter-way — print before the recursive call (pre-order style)
void printNTo1(int i, int n) {
    if (i > n) {
        return;
    }
    System.out.println(n - i + 1);
    printNTo1(i + 1, n);
}

// Functional-way — print before recursion
void printNTo1(int n) {
    if (n < 1) {
        return;
    }
    System.out.println(n);
    printNTo1(n - 1);
}
```

> **Pattern:** **Print before the call** = descending order on the way down. **Print after the call** = ascending order on the way up. This is the same pre-order vs post-order distinction that powers tree traversals.

---

## 🧪 Warm-Up Drills (Striver's Lectures 2–4 Equivalents)

A handful of "look mom, no loops" problems to cement the parameter-way habit before you hit subsequences.

### Drill 1 — Reverse an array using two-pointer recursion

**Steps in plain English:**

1. **Base case** — when the two pointers meet or cross (`l >= r`), the array is reversed.
2. **Swap** the elements at positions `l` and `r`.
3. **Recurse** with `l + 1` and `r - 1`.

```java
void reverse(int[] arr, int l, int r) {
    // Step 1 — base case: pointers met or crossed
    if (l >= r) {
        return;
    }
    // Step 2 — swap
    int tmp = arr[l];
    arr[l] = arr[r];
    arr[r] = tmp;
    // Step 3 — recurse inward
    reverse(arr, l + 1, r - 1);
}
// Caller: reverse(arr, 0, arr.length - 1);
```

### Drill 2 — Check palindrome using recursion

**Steps in plain English:**

1. **Base case** — when `i >= n - i - 1` (we've crossed the middle), the string is palindrome → return `true`.
2. **Mismatch check** — compare `s[i]` with `s[n-i-1]`; if they differ, return `false`.
3. **Recurse** on the next pair (`i + 1`).

```java
boolean isPalindrome(String s, int i) {
    int n = s.length();
    // Step 1 — base case: passed the middle
    if (i >= n - i - 1) {
        return true;
    }
    // Step 2 — mismatch
    if (s.charAt(i) != s.charAt(n - i - 1)) {
        return false;
    }
    // Step 3 — recurse on inner pair
    return isPalindrome(s, i + 1);
}
// Caller: isPalindrome(s, 0);
```

### Drill 3 — Reverse a string using a single index

**Steps in plain English:**

1. **Base case** — when `i >= s.length() / 2`, swaps are done.
2. **Swap** the chars at `i` and `length - i - 1`.
3. **Recurse** with `i + 1`.

```java
void reverseString(char[] s, int i) {
    // Step 1 — base case: half done
    if (i >= s.length / 2) {
        return;
    }
    // Step 2 — swap
    char tmp = s[i];
    s[i] = s[s.length - 1 - i];
    s[s.length - 1 - i] = tmp;
    // Step 3 — recurse
    reverseString(s, i + 1);
}
// Caller: reverseString(s, 0);
```

> **Why drill these:** they look pointless ("just use a loop!") but they hard-wire the **parameter-way habit** — passing the index/state down — which is exactly the muscle you'll need for subsequence and backtracking problems.

> 🧩 **Try these:**
> - ✅ LC 344 Reverse String (Drill 3 above is the answer)
> - ✅ LC 125 Valid Palindrome (variant of Drill 2 — strip non-alphanumeric)
> - ✅ Print all numbers 1..N using only recursion
> - ✅ Print all numbers N..1 using only recursion

---

## 🥞 The Call Stack — Visualizing What Java Is Doing

When you call a function, Java pushes a **stack frame**. The frame holds:
- The function's **parameters** (e.g., `n = 4`)
- The function's **local variables**
- The **return address** (where to resume the caller after this call returns)

When a function returns, its frame is **popped** off the stack and its caller resumes.

### Trace: `factorial(4)`

```
Step 1 — call factorial(4):
  STACK (top)
  ┌──────────────┐
  │ factorial(4) │   ← waiting on factorial(3)
  └──────────────┘

Step 2 — factorial(4) calls factorial(3):
  ┌──────────────┐
  │ factorial(3) │   ← waiting on factorial(2)
  ├──────────────┤
  │ factorial(4) │
  └──────────────┘

Step 3 — factorial(3) calls factorial(2):
  ┌──────────────┐
  │ factorial(2) │   ← waiting on factorial(1)
  ├──────────────┤
  │ factorial(3) │
  ├──────────────┤
  │ factorial(4) │
  └──────────────┘

Step 4 — factorial(2) calls factorial(1):
  ┌──────────────┐
  │ factorial(1) │   ← BASE CASE — returns 1, no further calls
  ├──────────────┤
  │ factorial(2) │
  ├──────────────┤
  │ factorial(3) │
  ├──────────────┤
  │ factorial(4) │
  └──────────────┘

Step 5 — factorial(1) returns 1; frame popped; factorial(2) computes 2 × 1 = 2:
  ┌──────────────┐
  │ factorial(2) │   ← about to return 2
  ├──────────────┤
  │ factorial(3) │
  ├──────────────┤
  │ factorial(4) │
  └──────────────┘

Step 6 — factorial(2) returns 2; factorial(3) computes 3 × 2 = 6.
Step 7 — factorial(3) returns 6; factorial(4) computes 4 × 6 = 24.
Step 8 — factorial(4) returns 24 to the original caller.
```

**The key insight:** every recursive call has its own copy of the parameters and local variables. The stack handles all the bookkeeping — you don't need to.

> **When the stack overflows:** Java's default stack size holds roughly 5,000–10,000 frames. If your recursion depth exceeds that, you get `StackOverflowError`. Mitigate by:
> 1. Adding a base case (the most common bug)
> 2. Reducing depth (e.g., balanced recursion vs. linear chain)
> 3. Converting to iteration with an explicit stack
> 4. Increasing JVM stack size with `-Xss` (rarely needed in interviews)

---

## 🧬 Stack vs Heap — How Recursion Shares State Across Frames

> **Lesson learned the hard way (May 2026):** Recursion confuses people most when they don't realize a `List` passed into a recursive call is *the same heap object* across every frame. Every mutation is visible everywhere. Once this clicks, backtracking templates stop feeling like magic.

You already saw the **stack** (call frames). Now meet the **heap** — and the distinction between **primitives** and **object references**.

---

### 💀 The Two Mistakes I Keep Making (Read This First)

> **Why this block exists:** I burned hours on LC 543 (Diameter) and LC 113 (Path Sum II) because of the **same root cause** in two disguises. If you only remember one thing from this entire doc, remember this block.

#### The One Rule

| Type of parameter | Who can see your changes? |
| --- | --- |
| **Primitive** (`int`, `long`, `boolean`, `double`, `char`) | 🟥 **Only the current frame.** Reassign all you want — caller is blind to it. |
| **Object reference** (`List`, `Map`, `int[]`, `TreeNode`, ...) | 🟩 **All frames pointing to that heap object** — IF you **mutate** (`.add`, `.put`, `arr[i] = ...`). |
| **Object reference, but reassigned** (`path = new ArrayList<>();`) | 🟥 **Only the current frame.** Reassigning the parameter swaps the local slot's arrow — the caller's arrow doesn't move. |

> **Mutate vs reassign — the single most important distinction in recursion:**
> - **Mutate** = `path.add(x)`, `map.put(k,v)`, `arr[i] = 5` → reaches through the arrow into the heap object → caller sees it ✅
> - **Reassign** = `path = ...`, `max = ...`, `arr = ...` → rebinds the local slot only → caller does NOT see it ❌

---

#### 🔴 Mistake A — Primitive accumulator that "won't update" (LC 543 Diameter)

```java
// ❌ Always returns 0
public int diameterOfBinaryTree(TreeNode root) {
    int max = 0;
    depth(root, max);
    return max;                        // still 0 — see why below
}

private int depth(TreeNode root, int max) {
    if (root == null) return 0;
    int dl = depth(root.left, max);
    int dr = depth(root.right, max);
    if (dl + dr > max) {
        max = dl + dr;                 // 🟥 reassigns THIS frame's local int only
    }
    return 1 + Math.max(dl, dr);
}
```

**Why it fails:** `max` is an `int` (primitive). Every recursive call gets a **fresh copy** on its own frame. Reassigning `max = ...` rebinds the local slot — the caller's `max` slot is **untouched**. The root returns `0`.

**The picture:**

```
diameterOfBinaryTree's frame:    max = 0   ◀── never changes
  depth(root)'s frame:           max = 0   reassigns to 2 → dies with frame
    depth(root.left)'s frame:    max = 0   reassigns → dies with frame
    depth(root.right)'s frame:   max = 0   reassigns → dies with frame
```

**The fix — hoist `max` to an instance field (the canonical Pattern 7 — Two-Purpose Recursion shape; see ⤴ section "Pattern 7" for the full template + ladder):**

```java
// ✅ The fix — instance field on the Solution class
class Solution {
    private int max = 0;                   // lives on the heap, inside the Solution instance

    public int diameterOfBinaryTree(TreeNode root) {
        max = 0;                           // reset at the top — survives LeetCode's class reuse
        depth(root);
        return max;
    }

    private int depth(TreeNode root) {
        if (root == null) return 0;
        int dl = depth(root.left);
        int dr = depth(root.right);
        max = Math.max(max, dl + dr);      // 🟩 writes to the shared Solution object
        return 1 + Math.max(dl, dr);
    }
}
```

**Why this works:** `this.max` lives **inside the `Solution` instance**, which lives on the **heap**. Every recursive call has the same implicit `this` reference, so all frames read/write the **same** `max` slot. It's the exact same mechanism that makes a shared `List` parameter work — just delivered via the implicit `this` instead of an explicit method parameter.

> **30-second test:** does my recursion need a **shared max/min/count** across all frames? If yes → **never pass a primitive**. Promote it to an **instance field** and reset it at the top of the public method. See full write-up in **Bug 10**.

---

#### 🔴 Mistake B — Reassigning a List parameter "to reset" it (LC 113 Path Sum II)

```java
// ❌ Path leaks across siblings AND results all share one list
private void solve(TreeNode root, int remaining,
                   List<Integer> path, List<List<Integer>> result) {
    if (root == null) return;
    path.add(root.val);

    if (root.left == null && root.right == null && remaining == root.val) {
        result.add(path);                  // 🟥 BUG 1 — stores the reference, not a copy
        path = new ArrayList<>();          // 🟥 BUG 2 — rebinds THIS frame only
        return;
    }

    solve(root.left,  remaining - root.val, path, result);
    solve(root.right, remaining - root.val, path, result);
    // 🟥 BUG 3 — no path.remove(...) → next sibling sees leaked values
}
```

**Three bugs, one root cause:** the code treats the `path` like a private value the helper owns. But `path` is a **shared heap object** — every frame holds an arrow to the same `ArrayList`. So:

1. **`result.add(path)`** — stored arrow. Future mutations corrupt every saved "answer."
2. **`path = new ArrayList<>()`** — only swings *this frame's* arrow to a brand-new empty list. The caller's arrow still points at the old, still-being-used `path`. Caller is blind to this reassignment.
3. **No undo** — when we recurse and return, the parent's `path` still has the child's value tacked on. The next sibling branch starts polluted.

**The picture for Bug 2 specifically:**

```
BEFORE reassignment           AFTER `path = new ArrayList<>();`
─────────────────────         ────────────────────────────────────
caller's path ────▶ [5,4,11]  caller's path ────▶ [5,4,11]   ◀── still here!
helper's path ────▶ [5,4,11]  helper's path ────▶ []         ◀── only this changed
                              (new empty heap object, abandoned at return)
```

**The fix — mutate (don't reassign), snapshot at the leaf, undo unconditionally:**

```java
// ✅ Backtracking gold standard
private void solve(TreeNode root, int remaining,
                   List<Integer> path, List<List<Integer>> result) {
    if (root == null) return;

    path.add(root.val);                      // 🟩 mutate — try

    if (root.left == null && root.right == null && remaining == root.val) {
        result.add(new ArrayList<>(path));   // 🟩 snapshot — store a copy
    } else {
        solve(root.left,  remaining - root.val, path, result);
        solve(root.right, remaining - root.val, path, result);
    }

    path.remove(path.size() - 1);            // 🟩 undo — pair every add with a remove
}
```

> **30-second test:** am I about to write `path = ...` or `list = ...` inside a recursive helper? **STOP.** That's almost always a bug. Instead: mutate the shared list, undo on the way out, and snapshot on store. See full write-up in **Bug 11**.

---

#### 🪞 Side-by-side: same root cause, two disguises

| | LC 543 Diameter (Mistake A) | LC 113 Path Sum II (Mistake B) |
| --- | --- | --- |
| **What I tried to share** | An `int max` | A `List<Integer> path` |
| **What I did wrong** | Passed it as a primitive parameter | Reassigned `path = new ArrayList<>()` |
| **What the caller actually saw** | Caller's `max` still 0 | Caller's `path` still pointing at the mutated original |
| **Why** | Primitives are copied per frame | Reassignment moves the *local* arrow, not the caller's arrow |
| **The fix** | Promote `max` to an **instance field** on `Solution` (it lives on the heap, shared via `this`) | Don't reassign — **mutate** the shared list with `.add` and `.remove` |
| **The rule** | "Need a shared primitive? **Make it an instance field.**" | "Mutate, don't reassign. Always pair add ↔ remove." |

---

### Primitives vs object references — the only rule that matters

| Type passed to a method | What gets copied | Caller sees mutations? |
| --- | --- | --- |
| **Primitive** (`int`, `boolean`, `char`, `double`, ...) | The **value** itself | ❌ No — caller's variable is untouched |
| **Object reference** (`List`, `Map`, `int[]`, `String[]`, custom classes, ...) | The **address** (the arrow, not the box) | ✅ Yes — both caller and callee point to the **same heap object** |

> Java is **always** pass-by-value. The trick is: for objects, the *value being copied is itself a reference*. So both sides end up holding pointers to the same heap object.

### The picture — `subsets()` calling `solve(ans, nums, 0, new ArrayList<>())`

```
              STACK (call frames)                     HEAP (objects)
        ┌──────────────────────────┐
        │  subsets() frame         │
        │   ans ───────────────────┼──────────▶ ┌────────────────┐
        │   (no name for path)     │            │  ArrayList<>() │  ← answer list
        ├──────────────────────────┤            │  [...]         │
        │  solve() frame           │            └────────────────┘
        │   ans ───────────────────┼──────────▶ (same object as above)
        │   nums ──────────────────┼─────┐
        │   idx = 0                │     │      ┌────────────────┐
        │   sol ───────────────────┼─────┼─────▶│  ArrayList<>() │  ← path list
        └──────────────────────────┘     │      │  [...]         │
                                         │      └────────────────┘
                                         │      ┌────────────────┐
                                         └─────▶│  int[]         │  ← nums
                                                │  [1, 2, 3]     │
                                                └────────────────┘
```

Three things to absorb:

1. **`ans`** has a name in `subsets()` AND in `solve()`. Two stack slots, **one** heap object.
2. **The path list** (`new ArrayList<>()` at the call site) has **no name** in `subsets()` — but it absolutely still exists on the heap. Inside `solve()` it's named `sol`. The fact that `subsets()` doesn't keep a local variable for it doesn't matter; `solve()`'s parameter holds the only reference, which is enough to keep the GC away.
3. **`idx = 0`** is a primitive. When `solve()` does `idx + 1` and recurses, the next frame gets a brand-new `idx` slot. The current frame's `idx` doesn't change — that's why depth-tracking by parameter "just works" without manual undo.

### Anonymous `new ArrayList<>()` is identical to a named one

These two snippets compile to **the same bytecode behavior**:

```java
// Style A — anonymous
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> ans = new ArrayList<>();
    solve(ans, nums, 0, new ArrayList<>());
    return ans;
}
```

```java
// Style B — named (some prefer this for clarity)
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> ans = new ArrayList<>();
    List<Integer> path = new ArrayList<>();
    solve(ans, nums, 0, path);
    return ans;
}
```

The path list lives on the heap either way. Style A just doesn't bother giving the caller a handle to it.

### Why mutations propagate without a `return`

Inside `solve()`:

```java
sol.add(nums[idx]);                  // mutates the heap object
solve(ans, nums, idx + 1, sol);      // child frame uses SAME heap object
sol.remove(sol.size() - 1);          // undo — visible to parent and child
```

There's no `return` of the list because **there's nothing to return**. The heap object was never "given back" — it was always shared. The caller can read its current state directly.

This is also why **the undo step is mandatory**: if you don't `sol.remove(...)`, the next sibling branch starts with leftover state from your child's mutations.

### Local primitives are NOT shared — and that's the point

Compare:

```java
// ❌ Trying to track depth via mutation — BUG
void dfs(int depth) {
    depth = depth + 1;            // only mutates THIS frame's depth
    dfs(depth);                   // child gets a copy of new depth
    // depth is unchanged in the caller's view
}
```

vs.

```java
// ✅ Pass the new depth as the next argument — works because each frame has its own copy
void dfs(int depth) {
    dfs(depth + 1);
}
```

Because primitives don't share state across frames, you **don't need** to undo them. `idx + 1` in the recursive call leaves the current frame's `idx` exactly where it was. This is one reason index-driven recursion is so clean — no undo bookkeeping.

### The matching gotcha — `ans.add(path)` vs `ans.add(new ArrayList<>(path))`

The shared-heap behavior is also why the snapshot copy is mandatory at the leaf:

```java
// ❌ Bug — ans now holds the SAME path reference; future mutations corrupt it
ans.add(path);

// ✅ Snapshot — copy the current contents into a new heap object
ans.add(new ArrayList<>(path));
```

Without the snapshot, every entry in `ans` points to the same `path` list. By the time recursion finishes, `path` is empty (everything got undone), so `ans` ends up as `[[], [], [], ...]`.

> Full bug write-up: see **Bug 4 — Storing the reference instead of a snapshot** later in this doc.

### Cheat-sheet table — what to do for each parameter type

| You're passing | Do you need to undo? | Do you need a snapshot when storing? |
| --- | --- | --- |
| `int idx`, `int sum`, `boolean flag` (primitives) | ❌ No | ❌ No (primitives are copied) |
| `List<Integer> path`, `Set<Integer> seen`, `int[] used` (mutable objects) | ✅ Yes — pair every `add`/`set` with a matching `remove`/`unset` | ✅ Yes — store `new ArrayList<>(path)`, not `path` |
| `String s` (immutable) | ❌ No (every concat builds a new object) | ❌ No (the reference itself is fine to store) |
| `int[] arr` you only read | ❌ No | n/a |

### TL;DR

1. **Stack** holds frames (parameters + locals); **heap** holds objects.
2. Passing an object to a recursive call passes its **address** — both frames share the heap object.
3. Mutations to a shared object are visible everywhere → no `return` needed.
4. Primitives are **copied** per frame → no undo needed (and you can't share them anyway).
5. When you store a mutable object in a results list, **snapshot it** (`new ArrayList<>(path)`).
6. Anonymous `new ArrayList<>()` at a call site is no different from a named one — same heap object, same rules.

---

## ✊ The Leap of Faith (Most Important Habit)

The single biggest mental block for new recursors is **trying to trace the entire recursion in your head.** Don't.

> When you write `int leftDepth = maxDepth(root.left);`, **assume it returns the correct depth of the left subtree.** Don't simulate the whole thing. Just **trust the recursion** and write what to do with the result.

This is sometimes called the **"recursive leap of faith."** You write the function as if a smarter friend has already implemented `solve(smaller_input)`, and you just need to wire it up.

### Concrete drill: sum of an array, recursively

```java
public int sum(int[] arr, int i) {
    if (i == arr.length) {
        return 0;                              // Q1: base case — empty suffix
    }
    int restSum = sum(arr, i + 1);             // Q2: trust — restSum = sum of arr[i+1..end]
    return arr[i] + restSum;                   // Q3: combine
}
```

Now look at the recursive line. **You did not need to trace it.** You assumed `sum(arr, i+1)` worked. That's the leap of faith.

The compiler doesn't care if your "faith" was justified — it just runs the code. As long as Q1 and Q3 are correct and Q2 reduces the problem, the recursion works.

---

## 🧭 The 7 Recursion Patterns

Almost every interview recursion problem fits one of these seven shapes. Recognize the pattern → write the template → fill in the work.

---

### Pattern 1: Linear Recursion (one self-call per call)

> The function calls itself **once** with a smaller input. The call stack is a straight line.

**Examples:** factorial, sum of array, reverse a string, reverse a linked list, length of a linked list, search a sorted array (linear), find min/max of array.

**Template — Steps in plain English:**

1. **Base case guard** — if the input is small enough to answer directly, return that answer.
2. **One recursive call** — call ourselves on a strictly smaller input and capture the result in a named variable.
3. **Combine** — perform the work at this level (add, multiply, prepend, etc.) using the sub-result and return.

```java
ReturnType solve(input) {
    // Step 1 — base case guard
    if (isBaseCase(input)) {
        return baseValue;
    }
    // Step 2 — single recursive call on smaller input (leap of faith)
    ReturnType sub = solve(smaller(input));
    // Step 3 — combine current-level work with sub-result and return
    return combine(currentStep, sub);
}
```

**Worked example — Reverse a linked list (LC 206)**

**Steps in plain English:**

1. **Base case** — if `head` is `null` or has no next, the list is already reversed; return it.
2. **Recurse on the tail** — trust that `reverseList(head.next)` returns the new head of the already-reversed remainder.
3. **Flip the local pointer** — make `head.next.next = head` so the node after me now points back to me.
4. **Cut my own next** — set `head.next = null` so I become the new tail (and don't form a cycle).
5. **Propagate the new head** — return the value the recursion already gave us; it's the reversed list's head all the way up.

```java
public ListNode reverseList(ListNode head) {
    // Step 1 — base case: empty or single node
    if (head == null || head.next == null) {
        return head;
    }
    // Step 2 — leap of faith: tail is already reversed
    ListNode reversedTail = reverseList(head.next);
    // Step 3 — flip the next node's pointer back to me
    head.next.next = head;
    // Step 4 — I become the new tail
    head.next = null;
    // Step 5 — propagate the new head up
    return reversedTail;
}
```

**Why this is linear:** one self-call (`reverseList(head.next)`). Depth = length of list.

> 🧩 **Try these:**
> - ✅ LC 509 Fibonacci Number (use a simple recursion first; we'll memoize it later)
> - ✅ LC 344 Reverse String
> - ✅ LC 206 Reverse Linked List (do both iterative AND recursive)
> - ✅ LC 21 Merge Two Sorted Lists (recursive merge — beautiful)
> - 🟡 **Try after Pattern 4** — LC 50 Pow(x, n) (linear works, but divide-and-conquer is the better solution)

---

### Pattern 2: Binary Recursion (two self-calls per call)

> The function calls itself **twice** per invocation. The call stack branches into a tree shape.

**Examples:** Fibonacci (naive), tree traversals (preorder/inorder/postorder), compute height of a tree, "climb stairs" (without memoization), generate all subsets.

**Template — Steps in plain English:**

1. **Base case guard** — if the input is small enough to answer directly, return that answer.
2. **First recursive call** — solve the "left" / "first option" sub-problem; capture the result.
3. **Second recursive call** — solve the "right" / "second option" sub-problem; capture the result.
4. **Combine** — merge the two sub-results with whatever work this level requires and return.

```java
ReturnType solve(input) {
    // Step 1 — base case guard
    if (isBaseCase(input)) {
        return baseValue;
    }
    // Step 2 — first sub-call
    ReturnType left = solve(option1(input));
    // Step 3 — second sub-call
    ReturnType right = solve(option2(input));
    // Step 4 — combine both sub-results and return
    return combine(currentStep, left, right);
}
```

**Worked example — Fibonacci (naive):**

```java
public int fib(int n) {
    if (n <= 1) {
        return n;                             // base: F(0) = 0, F(1) = 1
    }
    return fib(n - 1) + fib(n - 2);           // two self-calls
}
```

**Performance warning:** naive `fib(n)` is **O(2^n)** because the same subproblem (`fib(3)`, `fib(4)`, etc.) gets recomputed many times. We fix this with memoization (Pattern 6).

```
fib(5)
├── fib(4)
│   ├── fib(3)
│   │   ├── fib(2)
│   │   │   ├── fib(1) = 1
│   │   │   └── fib(0) = 0
│   │   └── fib(1) = 1
│   └── fib(2)               ← recomputed!
│       ├── fib(1) = 1
│       └── fib(0) = 0
└── fib(3)                   ← recomputed!
    ├── fib(2)               ← recomputed AGAIN!
    │   ├── fib(1) = 1
    │   └── fib(0) = 0
    └── fib(1) = 1
```

### 🎨 Visual — Why Binary Recursion Explodes (and What Memoization Fixes)

```
The naive fib(5) call tree drawn as a TRUE BRANCHING TREE:

                          fib(5)
                       /          \
                   fib(4)         fib(3)        ◀── fib(3) appears twice
                  /     \         /     \
              fib(3)   fib(2)  fib(2)  fib(1)   ◀── fib(2) appears THREE times
              /   \    /  \    /  \
           fib(2) f(1) f(1)f(0) f(1)f(0)
           /  \
         f(1) f(0)

   TOTAL NODES (= work units):
       fib(5) = 1
       fib(4) = 1   fib(3) = 2   fib(2) = 3   fib(1) = 5   fib(0) = 3
       → 15 calls for an answer  fib(5) = 5

   For fib(50):  ~1.5 BILLION calls  → seconds.
   For fib(80):  ~108 QUADRILLION calls → millennia.   (≈ 1.6 × φ^n)


WITH MEMOIZATION — the same tree with PRUNED branches:

                          fib(5)
                       /          \
                   fib(4)         fib(3)  ← CACHE HIT — return stored value
                  /     \           ✂
              fib(3)   fib(2) ← CACHE HIT — pruned
              /   \      ✂
           fib(2) f(1) ← CACHE HIT
           /  \
         f(1) f(0)

   Every distinct subproblem fib(k) is computed ONCE.
   The tree collapses from O(2^n) nodes → O(n) nodes.
   The crossed-out branches (✂) never execute — the cache returns
   the stored answer in O(1) and the recursion stops there.


THE INVARIANT BEHIND MEMOIZATION:

   If f(x) is a pure function of x (same input → same output, no
   side effects), then computing f(x) once and caching the result
   is mathematically identical to computing it every time — just
   exponentially faster.

   The flip from O(2^n) → O(n) doesn't come from a smarter algorithm.
   It comes from refusing to do the same work twice.
```

> 🧩 **Try these:**
> - ✅ LC 509 Fibonacci Number — write naive AND memoized
> - ✅ LC 70 Climbing Stairs — recurse on (n-1) + (n-2). **Same shape as Fibonacci.**
> - 🟡 **Try after Pattern 6 (memoization)** — LC 746 Min Cost Climbing Stairs

---

### Pattern 3: Backtracking (try → recurse → undo)

> Build a partial solution incrementally. At each step, **try a choice**, recurse to extend it, then **undo the choice** before trying the next option.

This is the pattern that confuses people the most because of the "undo." But it's just a structural rule. We'll build it up in three stages:

- **3.1 — Take/Not-Take** (the foundation): for each element, you have 2 choices — include or skip
- **3.2 — Subsequence Trilogy** (ALL / ONE / COUNT): the same template, three return-value variants
- **3.3 — For-loop "Pick Next"** (the generalization): when there are >2 choices per step

> **For the remaining sub-patterns** (permutations, constraint-driven N-Queens, grid backtracking, cut-points), see the dedicated `DeepDive/backtracking-fundamentals.md`. This section gives you the spine; the backtracking deep dive layers on the variations.

---

#### 3.1 — Take / Not-Take (the foundation)

> **Mental model:** *"For each element, do I **take** it into my answer or **not take** it?"* That's it. Every backtracking problem you'll meet (subsets, permutations with constraints, partitioning) is built on top of this binary decision.

`n` elements × 2 choices each = `2^n` outcomes. That's exactly why subsets of `n` elements is `2^n`.

**Template — Steps in plain English:**

1. **Base case** — when `ind >= n`, every element has been decided; snapshot the current `path` and return.
2. **TAKE branch** — add `arr[ind]` to `path`, recurse with `ind + 1`.
3. **UNDO** — remove `arr[ind]` from `path` (this is the "backtrack").
4. **NOT-TAKE branch** — recurse with `ind + 1` *without* adding anything.

```java
void f(int ind, int[] arr, int n, List<Integer> path, List<List<Integer>> ans) {
    // Step 1 — base case: all elements decided
    if (ind >= n) {
        ans.add(new ArrayList<>(path));
        return;
    }
    // Step 2 — TAKE: include arr[ind]
    path.add(arr[ind]);
    f(ind + 1, arr, n, path, ans);
    // Step 3 — UNDO (backtrack): remove what we just took
    path.remove(path.size() - 1);
    // Step 4 — NOT-TAKE: skip arr[ind]
    f(ind + 1, arr, n, path, ans);
}
```

**Walk through `arr = [1, 2]`** — recursion tree:

```
                      f(0, [])
                     /         \
              TAKE 1            NOT-TAKE 1
               /                       \
          f(1, [1])                  f(1, [])
           /     \                    /      \
      TAKE 2   NOT-TAKE 2        TAKE 2   NOT-TAKE 2
        /         \                /          \
   f(2,[1,2])   f(2,[1])      f(2,[2])     f(2,[])
   ADD [1,2]    ADD [1]        ADD [2]      ADD []
```

Output: `[1,2]`, `[1]`, `[2]`, `[]` → all 4 = 2² subsets ✅

**Walk through `arr = [1, 2, 3]`** — recursion tree:

```
                              f(0, [])
                           /            \
                      TAKE 1             NOT-TAKE 1
                       /                       \
                  f(1, [1])                  f(1, [])
                  /       \                  /       \
              TAKE 2   NOT-TAKE 2        TAKE 2   NOT-TAKE 2
              /           \                /           \
         f(2,[1,2])    f(2,[1])       f(2,[2])     f(2,[])
          /   \         /    \         /    \        /    \
      T 3   NT 3    T 3   NT 3     T 3   NT 3    T 3   NT 3
       |     |       |     |        |     |       |     |
   [1,2,3] [1,2]   [1,3]  [1]    [2,3]  [2]     [3]    []
```

8 leaves = 2³ subsets ✅

**Why the undo is mandatory:** `path` is a **single mutable** `List<Integer>` shared across the entire recursion. After the TAKE branch finishes exploring everything below it, `path` would still have `arr[ind]` at the end. Without removing it before the NOT-TAKE branch, that branch would inherit `arr[ind]` and produce wrong subsets.

> **Mental hook:** *"TAKE → recurse → UNDO → NOT-TAKE → recurse. The undo restores the world before the alternative explores."*

---

#### 3.2 — The Subsequence Trilogy (ALL / ONE / COUNT)

> **Striver's cornerstone lesson.** The exact same take/not-take template solves three different question shapes by changing only the return type and what you do at the base case.

Imagine the problem family **"subsequences with sum K"**. You'll see one of three asks:

| Question shape | Return type | What changes |
| --- | --- | --- |
| **Find ALL** subsequences with sum K | `void` (collect into `ans`) | Append snapshots at base case |
| **Find ONE** (does any exist?) | `boolean` (short-circuit) | Stop the moment one is found |
| **COUNT** subsequences with sum K | `int` | Sum the two recursive calls |

The recursion shape is identical. The only differences are highlighted below.

##### ALL — Print every subsequence with sum K

**Steps in plain English:**

1. **Base case** — when `ind == n`, check if `sum == K`; if yes, snapshot `path` into `ans`.
2. **TAKE** — add `arr[ind]` to `path`, add to `sum`, recurse, then UNDO both.
3. **NOT-TAKE** — recurse with `ind + 1`, no changes to `path` or `sum`.

```java
void allWithSumK(int ind, int n, int[] arr, int K,
                 int sum, List<Integer> path,
                 List<List<Integer>> ans) {
    // Step 1 — base case: all decisions made
    if (ind == n) {
        if (sum == K) {
            ans.add(new ArrayList<>(path));
        }
        return;
    }
    // Step 2 — TAKE arr[ind]
    path.add(arr[ind]);
    sum += arr[ind];
    allWithSumK(ind + 1, n, arr, K, sum, path, ans);
    // UNDO both mutations
    path.remove(path.size() - 1);
    sum -= arr[ind];
    // Step 3 — NOT-TAKE
    allWithSumK(ind + 1, n, arr, K, sum, path, ans);
}
```

##### ONE — Does any subsequence have sum K? (boolean short-circuit)

> **Important:** ONE comes in **two flavors** depending on what the problem asks for:
> - **Flavor A — return `boolean`** ("does any subsequence sum to K?"). `path` is **not needed**.
> - **Flavor B — return the actual subsequence** ("find one subsequence that sums to K and return it"). `path` is needed; only `path` is undone (sum is a primitive — see **🧬 Stack vs Heap**).
>
> Pick the leanest signature for what the problem actually asks. Don't carry `path` if you'll never read it.

**Flavor A — Steps in plain English:**

1. **Base case** — at `ind == n`, return `sum == K`.
2. **TAKE** — try with `arr[ind]` included by passing `sum + arr[ind]`; if the recursive call returns `true`, propagate `true` immediately.
3. **NOT-TAKE** — only tried if TAKE failed; return whatever it produces. No undo because `sum` is a primitive (the current frame's `sum` was never mutated).

```java
boolean oneWithSumK(int ind, int n, int[] arr, int K, int sum) {
    // Step 1 — base case
    if (ind == n) {
        return sum == K;
    }
    // Step 2 — TAKE; short-circuit on success
    if (oneWithSumK(ind + 1, n, arr, K, sum + arr[ind])) {
        return true;
    }
    // Step 3 — NOT-TAKE
    return oneWithSumK(ind + 1, n, arr, K, sum);
}
```

> **The short-circuit `return true` is the key signal of a "find ONE" problem.** You stop exploring the moment you've answered the question.

**Flavor B — Steps in plain English** *(when you need to return the actual subsequence)*:

1. **Base case** — at `ind == n`, if `sum == K`, return a **snapshot** of `path`; else return `null`.
2. **TAKE** — `path.add(arr[ind])`; recurse with `sum + arr[ind]`; if non-null, return it immediately.
3. **UNDO** — `path.remove(path.size() - 1)`. Only `path` is undone (it's a heap object); `sum` is a primitive and was never mutated.
4. **NOT-TAKE** — return whatever the recursive call produces (could be `null` if no subsequence works).

```java
List<Integer> findOneSubseqWithSumK(int ind, int n, int[] arr, int K,
                                     int sum, List<Integer> path) {
    // Step 1 — base case
    if (ind == n) {
        if (sum == K) {
            return new ArrayList<>(path);
        }
        return null;
    }
    // Step 2 — TAKE; short-circuit on success
    path.add(arr[ind]);
    List<Integer> takeResult = findOneSubseqWithSumK(ind + 1, n, arr, K,
                                                      sum + arr[ind], path);
    if (takeResult != null) {
        return takeResult;
    }
    // Step 3 — UNDO (path only — sum is a primitive)
    path.remove(path.size() - 1);
    // Step 4 — NOT-TAKE
    return findOneSubseqWithSumK(ind + 1, n, arr, K, sum, path);
}
```

> **Decision rule:** Read the problem once. Does the expected return type include the subsequence's contents (a list, a string, an array)? Use Flavor B. Just true/false or yes/no? Use Flavor A and drop `path`.

##### COUNT — How many subsequences have sum K

**Steps in plain English:**

1. **Base case** — at `ind == n`, return `1` if `sum == K`, else `0`.
2. **TAKE** — recurse with `arr[ind]` included; capture the count.
3. **NOT-TAKE** — recurse with `arr[ind]` skipped; capture the count.
4. **Combine** — return the sum of both counts.

```java
int countWithSumK(int ind, int n, int[] arr, int K, int sum) {
    // Step 1 — base case
    if (ind == n) {
        return sum == K ? 1 : 0;
    }
    // Step 2 — TAKE
    int withTake = countWithSumK(ind + 1, n, arr, K, sum + arr[ind]);
    // Step 3 — NOT-TAKE
    int withoutTake = countWithSumK(ind + 1, n, arr, K, sum);
    // Step 4 — combine
    return withTake + withoutTake;
}
```

> **The COUNT version doesn't need `path` at all** — we only care about the count, not which subsequences. This is a hint: **if you don't need the values, drop the path parameter.** Less state = less to manage.

##### Trilogy at a glance

| Variant | Return type | `path` needed? | Base case | Recursive case |
| --- | --- | --- | --- | --- |
| ALL | `void` | ✅ Yes — snapshot on every hit | `if (sum == K) ans.add(new ArrayList<>(path))` | TAKE (mutate path) → recurse → UNDO path → NOT-TAKE → recurse |
| ONE — Flavor A | `boolean` | ❌ **No** | `return sum == K` | `if (takeRecurse) return true; return notTakeRecurse` (no mutation, no undo) |
| ONE — Flavor B | `List<Integer>` | ✅ Yes — return snapshot | `return sum == K ? new ArrayList<>(path) : null` | TAKE (mutate path); `if (result != null) return result`; UNDO path; return NOT-TAKE |
| COUNT | `int` | ❌ No | `return sum == K ? 1 : 0` | `return takeRecurse + notTakeRecurse` (sum the calls) |

> **Notice the pattern:** `path` is only carried when the answer must include the subsequence's contents (ALL stores them; ONE-Flavor-B returns one). Pure boolean / pure count never need `path`.
>
> **Notice also:** in every variant, `sum` is a primitive — never mutated, never undone. Pass `sum + arr[ind]` to TAKE; pass `sum` unchanged to NOT-TAKE. See **🧬 Stack vs Heap** earlier in this doc for why.

> **This trilogy is the most interview-tested recursion shape after Fibonacci.** Internalize it once and you've covered ~30% of the recursion-flavored problems on LeetCode.

---

#### 3.3 — For-loop "Pick Next" (the generalization)

> When the choice space at each step is **>2** (e.g., "pick any of `nums[start..end]` next"), take/not-take doesn't fit cleanly. Use a `for` loop over the choices.

**Template — Steps in plain English:**

1. **Termination check** — if the partial is complete (or always, for "every prefix is a valid answer"), snapshot.
2. **Loop over choices** — at this state, every available choice is one branch.
3. **TRY** — apply the chosen option to `path`.
4. **RECURSE** — descend.
5. **UNDO** — remove the choice so the next iteration starts clean.

```java
void backtrack(state, partialResult, results) {
    // Step 1 — terminate / snapshot
    if (isComplete(state)) {
        results.add(snapshot(partialResult));
        return;
    }
    // Step 2 — branch on every available choice
    for (choice in choices(state)) {
        // Step 3 — TRY
        apply(partialResult, choice);
        // Step 4 — RECURSE
        backtrack(state.advance(choice), partialResult, results);
        // Step 5 — UNDO
        undo(partialResult, choice);
    }
}
```

**Worked example — Subsets (LC 78) via for-loop**

**Steps in plain English:**

1. **Public entry** — create results + fresh path, seed the recursion at index 0.
2. **Record current path** — every prefix is a valid subset, so snapshot on entry.
3. **Loop from `start`** — only consider elements at index ≥ `start` to avoid duplicate subsets.
4. **TRY** — append `nums[i]`.
5. **RECURSE** with `i + 1`.
6. **UNDO** — remove last element.

```java
public List<List<Integer>> subsets(int[] nums) {
    // Step 1 — set up containers, seed at index 0
    List<List<Integer>> results = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> results) {
    // Step 2 — every state of path is a valid subset
    results.add(new ArrayList<>(path));
    // Step 3 — only consider elements at index >= start
    for (int i = start; i < nums.length; i++) {
        // Step 4 — TRY
        path.add(nums[i]);
        // Step 5 — RECURSE
        backtrack(nums, i + 1, path, results);
        // Step 6 — UNDO
        path.remove(path.size() - 1);
    }
}
```

**Trace for `nums = [1, 2, 3]`:**

```
path = []        → results: [[]]
  i=0: path=[1]  → results: [[], [1]]
    i=1: path=[1,2]  → results: [[], [1], [1,2]]
      i=2: path=[1,2,3]  → results: [[], [1], [1,2], [1,2,3]]
      undo → path=[1,2]
    undo → path=[1]
    i=2: path=[1,3]  → results: [..., [1,3]]
    undo → path=[1]
  undo → path=[]
  i=1: path=[2]  → results: [..., [2]]
    i=2: path=[2,3]  → results: [..., [2,3]]
    undo → path=[2]
  undo → path=[]
  i=2: path=[3]  → results: [..., [3]]
  undo → path=[]
```

**Why we snapshot with `new ArrayList<>(path)`:** because `path` is mutated continuously, just storing the reference would mean every result in `results` ends up pointing to the same final (empty) list. Always **copy** the path when recording.

##### Take/Not-Take vs For-loop — quick chooser

| Use Take/Not-Take when... | Use For-loop "Pick Next" when... |
| --- | --- |
| The decision is naturally **binary** (include or skip) | The decision is **"which one of many"** (pick a next index) |
| Subsequences (preserves order) | Combinations (no order, no duplicate sets) |
| Counting / boolean variants are needed (Trilogy) | You want all elements ≥ a threshold |
| Canonical: Subsequences with sum K, LC 416 | Canonical: LC 39 Combination Sum, LC 77 Combinations |

> 🧩 **Try these (in order):**
> - ✅ LC 78 Subsets — solve **both** with take/not-take AND for-loop, see they produce the same set
> - ✅ "Print all subsequences" — pure take/not-take (no LC, but Striver's L6)
> - ✅ "Print subsequences with sum K" — Trilogy ALL variant (Striver's L7)
> - ✅ "Print one subsequence with sum K" — Trilogy ONE variant (Striver's L8)
> - ✅ "Count subsequences with sum K" — Trilogy COUNT variant (Striver's L9)
> - 🟡 **After Trilogy** — LC 39 Combination Sum (for-loop with reuse)
> - 🟡 **After LC 39** — LC 40 Combination Sum II (for-loop with skip-duplicates)
> - 🟡 **After LC 40** — LC 22 Generate Parentheses (open/close count constraints)
>
> **For the rest of backtracking** (LC 46 Permutations, LC 51 N-Queens, LC 79 Word Search, LC 131 Palindrome Partitioning, LC 17 Letter Combinations), see **`DeepDive/backtracking-fundamentals.md`**.

---

### Pattern 4: Divide and Conquer

> Split the input into **two (or more) independent halves**, recurse on each, **combine** the results. Often gives O(n log n) algorithms.

**Examples:** merge sort, quick sort, binary search, Pow(x, n), counting inversions.

**Template — Steps in plain English:**

1. **Base case guard** — if the input is small enough to answer directly, return.
2. **Split into independent halves** — define `leftHalf` and `rightHalf` such that solving each separately covers the whole problem.
3. **Recurse on left half** — capture the result.
4. **Recurse on right half** — capture the result.
5. **Merge** — combine the two halves into the final answer using `O(n)` or `O(1)` work.

```java
ReturnType solve(input) {
    // Step 1 — base case
    if (isBaseCase(input)) {
        return baseValue;
    }
    // Step 2 + 3 — recurse on the left half
    ReturnType leftResult = solve(leftHalf(input));
    // Step 4 — recurse on the right half
    ReturnType rightResult = solve(rightHalf(input));
    // Step 5 — merge both halves and return
    return merge(leftResult, rightResult);
}
```

**Worked example — Pow(x, n) in O(log n)**

**Steps in plain English:**

1. **Public entry handles negatives** — promote `n` to `long` (so `Integer.MIN_VALUE` doesn't overflow when negated), invert `x` if `n` is negative, then call the helper with the absolute value.
2. **Helper base case** — `n == 0` returns `1.0`.
3. **Halve the exponent once** — compute `fastPow(x, n / 2)` exactly once and reuse.
4. **Combine for even `n`** — return `half * half`.
5. **Combine for odd `n`** — return `half * half * x` (one extra multiplication for the odd remainder).

```java
public double myPow(double x, int n) {
    // Step 1a — base case at the public level
    if (n == 0) {
        return 1.0;
    }
    // Step 1b — promote to long so Integer.MIN_VALUE negation is safe
    long N = n;
    if (N < 0) {
        x = 1 / x;
        N = -N;
    }
    return fastPow(x, N);
}

private double fastPow(double x, long n) {
    // Step 2 — base case
    if (n == 0) {
        return 1.0;
    }
    // Step 3 — compute half once and reuse
    double half = fastPow(x, n / 2);
    // Step 4 — even exponent
    if (n % 2 == 0) {
        return half * half;
    }
    // Step 5 — odd exponent, multiply one extra x
    return half * half * x;
}
```

**Why this is divide-and-conquer:** instead of `n` recursive calls (linear), we get `log n` because each call halves the exponent. Depth = log n.

> ⚠️ **Why `long N = n`?** Because `n` can be `Integer.MIN_VALUE` (-2147483648), and `-Integer.MIN_VALUE` overflows back to itself. See `DeepDive/integer-overflow-and-limits.md` for the full trap.

> 🐞 **Critical D&C habit — store the half result in a local variable:**
>
> ```java
> double half = fastPow(x, n / 2);   // ✅ ONE call, stored in local
> return half * half;
> ```
>
> NOT:
>
> ```java
> return fastPow(x, n / 2) * fastPow(x, n / 2);   // ❌ TWO calls — TLE
> ```
>
> This single mistake collapses O(log n) to O(n). For `n = 2^31`, that's 2 billion ops vs 31 ops. Full breakdown in **Bug 9 — The two-call trap in divide & conquer**.

**Worked example — Merge Sort (canonical D&C)**

**Steps in plain English:**

1. **Base case** — a range of 1 or 0 elements (`lo >= hi`) is already sorted; return.
2. **Compute overflow-safe midpoint** — `lo + (hi - lo) / 2`, never `(lo + hi) / 2`.
3. **Sort the left half** — recurse on `[lo, mid]`.
4. **Sort the right half** — recurse on `[mid + 1, hi]`.
5. **Merge in place** — two-pointer merge of the two sorted halves into the same array slice.

```java
public int[] mergeSort(int[] arr, int lo, int hi) {
    // Step 1 — base case: 1 or 0 elements
    if (lo >= hi) {
        return arr;
    }
    // Step 2 — overflow-safe midpoint
    int mid = lo + (hi - lo) / 2;
    // Step 3 — sort left half
    mergeSort(arr, lo, mid);
    // Step 4 — sort right half
    mergeSort(arr, mid + 1, hi);
    // Step 5 — merge the two sorted halves
    merge(arr, lo, mid, hi);
    return arr;
}
// merge() is the standard two-pointer combine — see any sorting reference
```

### 🎨 Visual — Merge Sort Split-and-Combine Tree

```
Input array:  [5, 2, 8, 1, 9, 3, 7, 4]

DIVIDE phase — keep splitting until each piece has 1 element:

                  [5, 2, 8, 1, 9, 3, 7, 4]
                     /                \
              [5, 2, 8, 1]         [9, 3, 7, 4]
              /          \         /          \
           [5, 2]      [8, 1]   [9, 3]      [7, 4]
           /  \         /  \     /  \         /  \
         [5] [2]     [8] [1]   [9] [3]     [7] [4]
          ←──────── BASE CASE ────────→
          (a single element is already sorted)


CONQUER (merge) phase — pair up siblings and MERGE them back:

         [5] [2]     [8] [1]   [9] [3]     [7] [4]
           \  /        \  /      \  /        \  /
          [2, 5]     [1, 8]    [3, 9]      [4, 7]
              \        /          \          /
           [1, 2, 5, 8]          [3, 4, 7, 9]
                    \                /
                  [1, 2, 3, 4, 5, 7, 8, 9]    ✅ SORTED


WHY MERGE SORT IS O(n log n):

   Tree HEIGHT  = log₂(n)   ← number of split levels
   Work per LEVEL = O(n)    ← every element is merged exactly once
                              across all merges on that level
   Total = n × log n


THE MERGE STEP (key insight — two sorted halves → one sorted whole):

   left  = [1, 2, 5, 8]     ptr_L = 0
   right = [3, 4, 7, 9]     ptr_R = 0

   Compare left[ptr_L] vs right[ptr_R]; take the smaller.
       1 vs 3 → take 1 from left   result = [1]
       2 vs 3 → take 2 from left   result = [1, 2]
       5 vs 3 → take 3 from right  result = [1, 2, 3]
       5 vs 4 → take 4 from right  result = [1, 2, 3, 4]
       5 vs 7 → take 5 from left   result = [1, 2, 3, 4, 5]
       8 vs 7 → take 7 from right  result = [1, 2, 3, 4, 5, 7]
       8 vs 9 → take 8 from left   result = [1, 2, 3, 4, 5, 7, 8]
       (left exhausted, drain right) result = [1, 2, 3, 4, 5, 7, 8, 9]

   Each merge is O(n_left + n_right) — strictly linear in segment size.


CALL STACK DEPTH DURING MERGE SORT:

   Max depth = log₂(n).  For n = 1,000,000, depth ≈ 20.
   That's why merge sort never blows the call stack the way naive
   recursion on long lists does (a linear-recursion left-fold on
   1M elements would crash at depth ~10k).
```

> **Why `lo + (hi - lo) / 2` instead of `(lo + hi) / 2`?** Overflow safety. See integer-overflow doc.

> 🧩 **Try these:**
> - ✅ LC 50 Pow(x, n) — the example above
> - ✅ LC 704 Binary Search — recursive version is divide-and-conquer
> - 🟡 **After Pattern 4 clicks** — LC 912 Sort an Array (implement merge sort or quick sort)
> - 🔴 LC 215 Kth Largest Element (quickselect — D&C with random pivot)
> - 🔴 LC 493 Reverse Pairs / LC 315 Count of Smaller Numbers After Self (merge sort + counting)

---

### Pattern 5: Tail Recursion / Accumulator Pattern

> Pass a **running result** down the call chain so the final answer is ready when the base case fires. The recursive call is the **last operation** in the function.

**Why this matters:** in languages with tail-call optimization (Scala, OCaml, Scheme), tail-recursive functions don't grow the stack. **Java does NOT optimize tail calls** — but the *style* still helps you reason cleanly and is trivial to convert to a loop.

**Non-tail (regular) factorial:**

```java
public int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);              // ← multiply happens AFTER the call returns
}
```

**Tail-recursive factorial (using accumulator)**

**Steps in plain English:**

1. **Public entry** — kick off the helper with the initial accumulator value (`1` — the multiplicative identity).
2. **Helper base case** — when `n <= 1`, the accumulator already holds the final answer; return it directly.
3. **Recursive case** — call the helper with `n - 1` and the **updated** accumulator (`n * acc`); the call is the **very last** thing this frame does (no work after it returns).

```java
public int factorial(int n) {
    // Step 1 — start with accumulator = 1
    return factorialHelper(n, 1);
}

private int factorialHelper(int n, int acc) {
    // Step 2 — base case: accumulator already has the answer
    if (n <= 1) {
        return acc;
    }
    // Step 3 — tail call: nothing happens after the recursive call returns
    return factorialHelper(n - 1, n * acc);
}
```

**Why this style is a useful interview signal:**
- You explicitly demonstrate understanding of "what state moves down vs. what state moves up"
- Easy to convert to a loop (just turn the recursive call into a `while`)
- Less stack depth pressure on languages that DO optimize it

**When to use accumulator pattern:**
- You're building a result incrementally and want to avoid combining work after each return (e.g., reversing a list, building a sum, counting)
- The compiler-side benefit doesn't apply in Java, but the **clarity** does

> 🧩 **Try these:**
> - ✅ Rewrite `sum(int[] arr)` and `factorial(int n)` in both regular and accumulator style — feel the difference
> - ✅ LC 206 Reverse Linked List — write the iterative version, which is mechanically just an unrolled tail-recursive accumulator

---

### Pattern 6: Memoization (Top-Down DP)

> Cache the result of each recursive call so repeated subproblems are answered in O(1) instead of recomputed.

This is your **bridge from recursion to dynamic programming.** Naive recursion is often exponential because of repeated work; memoization brings it down to polynomial.

**Naive Fibonacci — O(2^n):**

```java
public int fib(int n) {
    if (n <= 1) {
        return n;
    }
    return fib(n - 1) + fib(n - 2);
}
```

**Memoized Fibonacci — O(n)**

**Steps in plain English (HashMap version):**

1. **Base case** — `n <= 1` returns `n` directly (`F(0) = 0`, `F(1) = 1`).
2. **Cache hit** — if `n` is already in `memo`, return the cached answer instantly.
3. **Compute recursively** — call `fib(n-1)` and `fib(n-2)`, sum them.
4. **Cache the answer** — store before returning so future calls are O(1).
5. **Return** the freshly-computed answer.

```java
private Map<Integer, Integer> memo = new HashMap<>();

public int fib(int n) {
    // Step 1 — base case
    if (n <= 1) {
        return n;
    }
    // Step 2 — cache hit
    if (memo.containsKey(n)) {
        return memo.get(n);
    }
    // Step 3 — compute via two recursive calls
    int result = fib(n - 1) + fib(n - 2);
    // Step 4 — cache before returning
    memo.put(n, result);
    // Step 5 — return
    return result;
}
```

Or with an array (faster, no hashing overhead).

**Steps in plain English (array version):**

1. **Public entry** — allocate an `int[n + 1]` and fill with `-1` as the "not computed" sentinel; call the helper.
2. **Helper base case** — `n <= 1` returns `n`.
3. **Cache hit** — if `memo[n] != -1`, return it.
4. **Compute, store, return** — recurse on `n-1` and `n-2`, sum, write to `memo[n]`, return.

```java
public int fib(int n) {
    // Step 1 — allocate cache with sentinel
    int[] memo = new int[n + 1];
    Arrays.fill(memo, -1);
    return fibHelper(n, memo);
}

private int fibHelper(int n, int[] memo) {
    // Step 2 — base case
    if (n <= 1) {
        return n;
    }
    // Step 3 — cache hit
    if (memo[n] != -1) {
        return memo[n];
    }
    // Step 4 — compute, store, return
    memo[n] = fibHelper(n - 1, memo) + fibHelper(n - 2, memo);
    return memo[n];
}
```

**Why memoization works:** the **same subproblem** (`fib(5)`, `fib(6)`, etc.) gets called many times across the recursion tree. With a cache, each subproblem is solved exactly once.

**The transformation rule (memoize any recursion):**

1. Identify what defines a "unique subproblem" — usually the function's parameters
2. Add a cache (`Map`, `Map<String, ...>`, or multi-dim array) keyed on those parameters
3. At the start of the function: if cached, return cached value
4. At the end: store the result before returning

> ⚠️ **Memoize only when subproblems repeat.** If every recursive call has a unique parameter combo (e.g., DFS through a graph with visited set), there's nothing to cache — adding memo is wasted memory.

> 🧩 **Try these:**
> - ✅ LC 509 Fibonacci Number — naive AND memoized (already on your list)
> - ✅ LC 70 Climbing Stairs — same shape as Fibonacci
> - 🟡 **After LC 70** — LC 198 House Robber (memoize dp(i) = max loot from index i onward)
> - 🟡 LC 746 Min Cost Climbing Stairs
> - 🟡 LC 322 Coin Change (memoize on remaining amount)
> - 🔴 LC 300 Longest Increasing Subsequence — needs DP optimization beyond simple memoization
> - 🔴 LC 416 Partition Equal Subset Sum — 2D memoization (index, currentSum)

---

### Pattern 7: Two-Purpose Recursion (Return One Thing, Mutate the Other) ⭐

> The function does **two jobs at once**: it **returns** a value the parent needs to keep computing (e.g., "depth of this subtree", "max one-sided gain"), and as a side effect it **updates a shared answer** (e.g., "diameter so far", "max path sum seen"). The recursive return and the final answer are **two different quantities**.

This is the single most important pattern for **tree problems where the answer is a property of any subtree, not just the root.** It also shows up in linked-list problems and any recursion where you need to "remember the best you've ever seen" while still propagating something else upward.

> **Cross-reference:** this same idea is documented in **`trees-fundamentals.md`** under the **Bottom-Up Two-Pass Aggregation** tree pattern. The recursion-doc copy here is the **algorithm-shape** view; the trees doc is the **problem-shape** view. Read both.

---

#### Why it exists — the moment you can't use plain binary recursion

Plain Binary Recursion (Pattern 2) returns **one combined answer** from each call — `height(root)` returns the tree's height, full stop. That works when the answer at the root is what you want.

But what if the answer is *"the longest path **anywhere** in the tree, not necessarily through the root"*? The root's return value (a height, a depth, a gain) is **not** the answer — the answer might live deep inside, in some subtree you've already left behind. So you need:

| Channel | Carries | Direction |
| --- | --- | --- |
| **Return value** | The thing the parent needs to combine ("depth", "one-sided gain", "longest univalue suffix") | Bottom → up the stack |
| **Shared answer** | The global best seen anywhere ("diameter", "max sum", "longest path") | Written from any frame, read at the end |

The shared answer **cannot** be a plain primitive parameter — primitives are copied per frame, so writes are invisible to the caller. **Cross-reference:** 🧬 Stack vs Heap → "Mistake A" and **Bug 10**.

---

#### Canonical template — Steps in plain English

1. **Declare the instance field** at class scope — initialize it with the "worst possible" answer (`Integer.MIN_VALUE` for a max, `0` for a count or non-negative max, `Integer.MAX_VALUE` for a min).
2. **Reset the field at the top of the public entry method** — LeetCode reuses your `Solution` instance across test cases; without a reset, state from the previous test leaks in.
3. **Call the helper** — usually discard its return at the root if you only care about the field; sometimes the root's return is also useful.
4. **In the helper, base case first** — return the identity value for empty input (`0` for height-style returns).
5. **Recurse on both children** — capture each return in a named variable (`left`, `right`, `dl`, `dr`).
6. **Update the field using the children's values** — `field = Math.max(field, combine(left, right))` is the most common shape.
7. **Return the *parent's-eye-view* value** — what the parent needs from me to keep computing **its** answer (usually the *one-sided* version, not the combined version).
8. **Public method returns the field**, not the helper's return.

```java
class Solution {
    // Step 1 — instance field for the global answer
    private int best = 0;

    public int solve(TreeNode root) {
        // Step 2 — reset for LeetCode's class reuse
        best = 0;
        // Step 3 — helper return often discarded; the field IS the answer
        helper(root);
        // Step 8 — return the field, not the helper's return
        return best;
    }

    private int helper(TreeNode root) {
        // Step 4 — base case: identity for empty input
        if (root == null) {
            return 0;
        }
        // Step 5 — recurse on both children, capture each result
        int left = helper(root.left);
        int right = helper(root.right);
        // Step 6 — update the global best (this frame's contribution)
        best = Math.max(best, combineForGlobal(left, right));
        // Step 7 — return parent's-eye-view (often one-sided)
        return parentView(left, right);
    }
}
```

> **The mantra:** *"Return what the parent needs. Mutate what the whole tree needs."*

---

#### Worked example — LC 543 Diameter of Binary Tree

**Goal:** longest path (in edges) between any two nodes. The path may or may not pass through the root.

**Why plain binary recursion fails:** `depth(root)` returns 1 + max(left, right). The diameter at this node is `dl + dr` — but if you return that, the parent can't use it (the parent needs a *depth*, not a *diameter*). Two different quantities.

**Steps in plain English:**

1. Instance field `max = 0` (smallest possible answer — diameter is non-negative).
2. Reset `max = 0` at the top of the public method.
3. Helper `depth(root)` returns the depth of the subtree rooted at `root` (what the parent needs).
4. Base case: `null` has depth 0.
5. Recurse: `dl = depth(left); dr = depth(right);`.
6. Update global: `max = Math.max(max, dl + dr)` — the diameter passing **through** this node.
7. Return to parent: `1 + Math.max(dl, dr)` — the depth contribution (one-sided).
8. Public method returns `max`.

```java
class Solution {
    // Step 1 — instance field
    private int max = 0;

    public int diameterOfBinaryTree(TreeNode root) {
        // Step 2 — reset
        max = 0;
        // Step 3 — discard the helper return; we want the field
        depth(root);
        // Step 8 — answer is the field
        return max;
    }

    private int depth(TreeNode root) {
        // Step 4 — base case
        if (root == null) {
            return 0;
        }
        // Step 5 — recurse on both children
        int dl = depth(root.left);
        int dr = depth(root.right);
        // Step 6 — update the global best (diameter through this node)
        max = Math.max(max, dl + dr);
        // Step 7 — return the one-sided depth to the parent
        return 1 + Math.max(dl, dr);
    }
}
```

**Tracing the two channels on a tiny tree:**

```
        1
       / \
      2   3
     /
    4
```

| Call | `dl` | `dr` | `max` after this frame | Returned to parent |
| --- | --- | --- | --- | --- |
| `depth(4)` | 0 | 0 | `max(0, 0) = 0` | `1 + 0 = 1` |
| `depth(2)` | 1 | 0 | `max(0, 1) = 1` | `1 + 1 = 2` |
| `depth(3)` | 0 | 0 | `max(1, 0) = 1` | `1 + 0 = 1` |
| `depth(1)` | 2 | 1 | `max(1, 3) = 3` | `1 + 2 = 3` |

Answer: `max = 3` (the path 4 → 2 → 1 → 3). The helper's final return (`3`) is **not** the answer — it's the root's depth.

---

#### Variations on the same skeleton

The template stays the same; only **what you update** and **what you return** changes:

| Problem | What helper RETURNS to parent | What gets WRITTEN to the field |
| --- | --- | --- |
| LC 543 Diameter | `1 + max(dl, dr)` (depth) | `dl + dr` (diameter through this node) |
| LC 124 Max Path Sum | `node.val + max(0, max(gl, gr))` (one-sided gain, clipped at 0) | `node.val + max(0, gl) + max(0, gr)` (bent path through this node) |
| LC 687 Longest Univalue Path | `1 + matching-child-length` (one-sided univalue arm) | `left-arm + right-arm` (full univalue path through this node) |
| LC 1373 Max Sum BST in Binary Tree | `(isBst, min, max, sum)` tuple from each child | `sum` if children form a valid BST |
| LC 110 Balanced Binary Tree | height (or `-1` sentinel for unbalanced) | (no separate field — short-circuit via sentinel) |
| LC 1448 Good Nodes in Binary Tree | nothing (`void`); pass `maxOnPath` *down* instead | `count++` when `node.val >= maxOnPath` |

> **Notice the pattern:** "return one-sided, update with both sides combined" — this shape repeats over and over.

---

#### The negative-clipping idiom (LC 124-specific, but worth knowing)

When the recursive return can be negative and you don't want it to drag the parent down, **clip at zero**:

```java
// Clip a child's contribution to 0 if negative (i.e., "skip this child")
int leftGain = Math.max(0, helper(root.left));
int rightGain = Math.max(0, helper(root.right));
```

This says *"if going through this child only hurts me, pretend the child contributes 0."* Cross-reference: `Reference/code-style-for-dsa-reference.md` → Refactor Recipe 1 (Negative-clipping with `Math.max(0, x)`).

---

#### Side-by-side: Pattern 2 vs Pattern 7

```java
// Pattern 2 — Binary Recursion: answer IS the root's return
public int height(TreeNode root) {
    if (root == null) return 0;
    int l = height(root.left);
    int r = height(root.right);
    return 1 + Math.max(l, r);          // root's return == answer
}
```

```java
// Pattern 7 — Two-Purpose: answer lives in the field, helper returns what parent needs
private int max = 0;
public int diameterOfBinaryTree(TreeNode root) {
    max = 0;
    depth(root);                         // discard the return
    return max;                          // answer is the field
}
private int depth(TreeNode root) {
    if (root == null) return 0;
    int dl = depth(root.left);
    int dr = depth(root.right);
    max = Math.max(max, dl + dr);        // global update
    return 1 + Math.max(dl, dr);         // parent's-eye-view
}
```

> **The single-line distinction:** in Pattern 2 you write `return ...` and you're done. In Pattern 7 you write `max = ...` **and** `return ...` on every recursive level — two effects per call.

---

#### When to reach for Pattern 7 — the 30-second test

Ask yourself these three questions:

1. **Is the answer a property of "any subtree" or "any subpath"** — not just the root? → Yes → likely Pattern 7.
2. **Does the parent need a *different* quantity than the global answer?** → Yes (e.g., parent needs depth, answer needs diameter) → definitely Pattern 7.
3. **Could the answer involve combining values from both children at some node deep in the tree?** → Yes → Pattern 7.

If all three are no, you probably want plain Binary Recursion (Pattern 2). If yes to question 1 alone, double-check whether `boolean` or `void` would suffice — sometimes you don't even need the helper to return anything.

---

#### Common mistakes (cross-references to bugs)

| Mistake | Where it's documented |
| --- | --- |
| Passing the global as `int max` parameter — never updates | **Bug 10** + 🧬 Stack vs Heap → Mistake A |
| Forgetting to reset the field at the top of the public method | Style Habit, also `Reference/code-style-for-dsa-reference.md` Recipe 13 |
| Returning the *combined* value (e.g., `dl + dr`) instead of the *one-sided* value | LC 124 Walkthrough in `trees-fundamentals.md` |
| Using `static` for the field on LeetCode → state leaks across test cases | **Bug 5** |

---

> 🧩 **Try these (in this exact order — Pattern 7 ladder):**
> - ✅ LC 104 Maximum Depth of Binary Tree — plain Pattern 2; build the depth-helper muscle memory first
> - ✅ **LC 543 Diameter of Binary Tree** — the prototypical Pattern 7 problem; nail this before moving on
> - 🟡 LC 687 Longest Univalue Path — same shape as 543 with a small twist (only count edges where values match)
> - 🟡 LC 1448 Good Nodes in Binary Tree — Pattern 7 variant: pass `maxOnPath` **down** as a parameter, mutate the field, return nothing
> - 🔴 **LC 124 Binary Tree Maximum Path Sum** — Pattern 7 with negative-clipping; see full walkthrough in `trees-fundamentals.md` AND `Patterns/max-path-sum-binary-tree-problem.md`
> - 🔴 LC 1373 Max Sum BST in Binary Tree — Pattern 7 + tuple return (multi-value bottom-up info)

> **Mental hook:** *"What does the parent need from me? What does the whole tree need from me? If those are different, I'm in Pattern 7."*

---

## 🎨 Style Habits — Build These From Day 1

> Some habits apply to **every recursion you write.** Others only matter in specific patterns. **Master the universal ones first**, then internalize the context-specific ones as you encounter them.

---

### 🌐 Universal Habits (apply everywhere)

#### Habit 1 — Write the base case first, before anything else

```java
ReturnType solve(input) {
    if (isBaseCase(input)) {
        return baseValue;        // ← LINE 1, ALWAYS
    }
    // ... recursion below
}
```

**Why:** missing or wrong base case = stack overflow or wrong answer. Forcing yourself to write it line 1 means you've thought about it before you've written any other code.

---

#### Habit 2 — Name your recursive results

```java
// ❌ Compact — works but breaks the moment you need the values for anything else
return helper(left) + helper(right);

// ✅ Named — same answer, extensible
int leftAns = helper(left);
int rightAns = helper(right);
return leftAns + rightAns;
```

**Why:** the moment you have **Pattern 7 — Two-Purpose Recursion** (return one thing + update a global) or need to do extra logic on the sub-results, the compact form breaks. Building the named-intermediate habit early means zero refactor later. (See ⤴ Pattern 7 for the full template.)

---

#### Habit 3 — Verify your recursive call is on a *smaller* input

Every recursive call must reduce toward the base case. Specifically check:

```java
solve(arr, i + 1);    // ✅ index moves toward arr.length
solve(node.left);     // ✅ moves to a strict subtree
solve(n - 1);         // ✅ counter decreases toward base
solve(s.substring(1));// ✅ string strictly shorter
```

vs.

```java
solve(arr, i);        // ❌ same input → infinite recursion
solve(node);          // ❌ same node → infinite recursion
solve(n);             // ❌ unchanged → infinite recursion
```

**Why:** the #1 cause of "stack overflow" in interview submissions is forgetting to advance the parameter. Read your own code and ask: *"is this call strictly smaller?"*

---

#### Habit 4 — Trust the recursion (verbalize the leap of faith)

When writing a recursive function, narrate aloud:

> *"I assume `solve(smaller)` returns the correct answer for the smaller input. With that, my job at this level is to..."*

**Why:** this is the antidote to "I keep getting confused tracing the calls." You don't trace; you trust + write the combine logic.

---

#### Habit 5 — Always brace your blocks (no inline `if`)

```java
// ❌
if (n <= 1) return 1;

// ✅
if (n <= 1) {
    return 1;
}
```

**Why:** the same reason as in trees doc — debugging, log statements, copy-paste reformat, interview readability.

---

### 🔧 Context-Specific Habits (won't matter on day 1; bookmark)

#### Habit 6 — In backtracking, always undo every state mutation

```java
path.add(nums[i]);
backtrack(...);
path.remove(path.size() - 1);    // ← REQUIRED
```

If you forget the undo, branches contaminate each other → completely wrong results that look "almost right" on small tests.

---

#### Habit 7 — Snapshot collections when storing them

```java
results.add(new ArrayList<>(path));    // ✅ deep copy
results.add(path);                      // ❌ stores reference; will be empty at end
```

A shared `path` mutated in place means every reference in `results` points to the same (final) state. Snapshot.

---

#### Habit 8 — In divide-and-conquer, use overflow-safe midpoint

```java
int mid = lo + (hi - lo) / 2;     // ✅
int mid = (lo + hi) / 2;          // ❌ overflows when lo + hi > Integer.MAX_VALUE
```

→ Full explanation in `DeepDive/integer-overflow-and-limits.md`.

---

#### Habit 9 — Pass mutable state as parameters, not via globals (when possible)

```java
// ✅ explicit, testable, local
void backtrack(int idx, List<Integer> path, List<List<Integer>> results) { ... }

// ⚠️ implicit, shared, fragile
private List<List<Integer>> results;        // hidden state
void backtrack(int idx, List<Integer> path) { ... }
```

The instance-field version is fine if you **reset it** in the public entry-point method (see the LC 124 walkthrough in trees doc). But for clean reasoning, parameters are better.

---

#### Habit 10 — Don't use `static` fields for problem state on LeetCode

Same trap as in trees doc — `static` persists across test cases on the LeetCode grader. Use **instance fields** and reset in the entry-point method.

---

## 🐞 Common Bugs (Hall of Fame)

### Bug 1 — Missing or wrong base case → stack overflow

```java
// ❌ no base case
public int factorial(int n) {
    return n * factorial(n - 1);   // recurses to n=0, -1, -2, ... forever
}

// ✅
public int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}
```

---

### Bug 2 — Recursive call doesn't reduce the input

```java
// ❌ infinite recursion
public int sum(int[] arr, int i) {
    if (i == arr.length) {
        return 0;
    }
    return arr[i] + sum(arr, i);   // ← never advances i!
}

// ✅
return arr[i] + sum(arr, i + 1);
```

---

### Bug 3 — Forgetting to undo in backtracking

```java
// ❌ branches contaminate each other
path.add(nums[i]);
backtrack(nums, i + 1, path, results);
// (no undo)

// ✅
path.add(nums[i]);
backtrack(nums, i + 1, path, results);
path.remove(path.size() - 1);
```

Symptom: results look like they got bigger and bigger, or contain duplicates that shouldn't exist.

---

### Bug 4 — Storing the reference instead of a snapshot

```java
// ❌ all entries point to the same (final) list
results.add(path);

// ✅
results.add(new ArrayList<>(path));
```

Symptom: at the end, `results` is full of identical (often empty) lists.

---

### Bug 5 — Using `static` for problem state on LeetCode

```java
// ❌ persists across test cases
private static int answer = 0;

// ✅ instance field, reset in entry method
private int answer;
public int solve(...) {
    answer = 0;
    helper(...);
    return answer;
}
```

Symptom: first test case passes; second one fails with "off by N" or weird carryover.

---

### Bug 6 — Wrong return value from recursive case

A subtle one. You return the *combined* result from the recursive case, but the value you return is wrong for what the **parent** expects.

The classic example is **LC 124 Maximum Path Sum** (see trees doc): you must return the **one-sided gain** to the parent, not the bent path through this node. Mistake → illegal paths → wrong answer.

**Defense:** before writing the return statement, ask yourself: *"What does my caller need from me?"* That's what you return. Don't return what's locally most informative if the caller can't use it.

---

### Bug 7 — Memoization key doesn't capture all variables that affect the answer

```java
// ❌ memo on n only, but the answer also depends on `target`
private Map<Integer, Integer> memo = new HashMap<>();

private int dp(int n, int target) {
    if (memo.containsKey(n)) {
        return memo.get(n);              // wrong! same n, different targets → same cached answer
    }
    // ...
}

// ✅ key includes ALL affecting parameters
private Map<String, Integer> memo = new HashMap<>();

private int dp(int n, int target) {
    String key = n + "," + target;
    if (memo.containsKey(key)) {
        return memo.get(key);
    }
    // ...
}

// ✅ better: 2D array for speed
private int[][] memo;          // sized [n+1][target+1] with -1 sentinel
```

> **Rule:** the memo key must be **everything that uniquely identifies a subproblem.** If two calls would compute the same answer, they should hit the same key.

---

### Bug 8 — Stack overflow on deep but legitimate recursion

For a balanced recursion, depth is `log n`, no problem. For a linear recursion (e.g., a "stick" tree, an unbalanced linked list), depth = n, and `n = 100,000` will overflow Java's default stack.

**Mitigations:**
- Convert to iteration with an explicit stack
- Use tail-call style + iterative loop
- Increase JVM stack size with `-Xss64m` (interview rare)

In real interviews, if the problem specifies `n ≤ 10^4`, recursion is fine. If `n ≤ 10^6`, prefer iteration.

---

### Bug 9 — The two-call trap in divide & conquer (TLE killer)

> **Lesson learned the hard way (May 2026):** LC 50 Pow(x, n) gave TLE because the recursive call was made **twice** instead of being computed once and stored. This single mistake collapses a clean O(log n) algorithm to O(n) and is the most common D&C interview bug.

```java
// ❌ TLE — recursion happens TWICE per level
double result = myPow(x, n / 2) * myPow(x, n / 2);
```

```java
// ✅ O(log n) — compute once, reuse
double half = myPow(x, n / 2);
double result = half * half;
```

**Why this matters:**

| Pattern | Recurrence | Complexity | For `n = 2^31` |
| --- | --- | --- | --- |
| `solve(half) * solve(half)` (double-call) | `T(n) = 2·T(n/2) + O(1)` | **O(n)** ❌ | ~2 billion ops → TLE |
| `T sub = solve(half); return combine(sub)` (single-call) | `T(n) = T(n/2) + O(1)` | **O(log n)** ✅ | ~31 ops → instant |

The recursion tree tells the story:

```
DOUBLE-CALL                         SINGLE-CALL
                                    
        myPow(n)                          myPow(n)
        /     \                              |
   myPow(n/2)  myPow(n/2)                myPow(n/2)
   /   \         /   \                       |
 n/4   n/4     n/4   n/4                  myPow(n/4)
 ...   ...     ...   ...                      |
                                          myPow(n/8)
                                              ...
2 children per level → O(n) work     1 child per level → O(log n) work
```

**Why `n` ÷ 2 each time doesn't save you in the double-call version:** even though each call halves `n`, you make *two* such calls, so the work doubles per halving. The two factors cancel out exactly, leaving you with O(n).

**Why memoization doesn't fix it:** you might be tempted to add a HashMap to dedupe the two calls. That works, but:
- You're memoizing to undo a mistake you didn't have to make
- A local HashMap (`Map<...> map = new HashMap<>()` inside the recursive method) **does nothing** because each call creates a fresh empty map (see **🧬 Stack vs Heap**)
- The right fix is just store the half result in a local variable; no map needed

**Where this trap lives — recognize the shape:**

| Problem | Mistake | Fix |
| --- | --- | --- |
| LC 50 Pow(x, n) | `myPow(x, n/2) * myPow(x, n/2)` | `half = myPow(x, n/2); return half * half` |
| Tree height | `Math.max(height(root.left), height(root.left))` (typo, double-counted) | `int lh = height(root.left); int rh = height(root.right); return 1 + max(lh, rh)` |
| Fibonacci (naive) | `fib(n-1) + fib(n-2)` with no memoization | Memoize, OR rewrite iteratively |
| Mirror tree | `isMirror(a.left, b.right) && isMirror(a.left, b.right)` (typo) | Verify both children are referenced |

**The universal D&C habit (lock this in):**

```java
// Always: 1 line to compute, 1 line to combine
T sub = solve(smallerInput);
return combine(sub, currentStep);
```

If you ever write `solve(...) * solve(...)` or `solve(...) + solve(...)` with **identical arguments on both sides**, stop and ask: "should I be storing this in a variable?" The answer is yes 99% of the time.

> **Symptom in interviews:** code passes small examples (n=10), times out on edge tests (n = 2^31). If you see TLE on a divide-and-conquer problem with no obvious mistake, look for duplicated recursive calls **first**.

---

### Bug 10 — Primitive accumulator parameter doesn't propagate up (LC 543 family)

> **Lesson learned the hard way (May 2026):** LC 543 Diameter of Binary Tree returned `0` for every tree because I passed `int max` as a parameter and reassigned it inside the helper. The caller never saw any change. **The plan-time fix lives in Pattern 7 — this entry is the debug-time landing page.**

```java
// ❌ Always returns 0 — primitive param copied per frame; reassignment is local
public int diameterOfBinaryTree(TreeNode root) {
    int max = 0;
    depth(root, max);
    return max;                          // never updated
}

private int depth(TreeNode root, int max) {
    if (root == null) return 0;
    int dl = depth(root.left, max);
    int dr = depth(root.right, max);
    if (dl + dr > max) {
        max = dl + dr;                   // local rebind — invisible to caller
    }
    return 1 + Math.max(dl, dr);
}
```

```java
// ✅ Fix — Pattern 7 (Two-Purpose Recursion): instance field on the heap
class Solution {
    private int max = 0;

    public int diameterOfBinaryTree(TreeNode root) {
        max = 0;                           // reset for LeetCode class reuse
        depth(root);
        return max;
    }

    private int depth(TreeNode root) {
        if (root == null) return 0;
        int dl = depth(root.left);
        int dr = depth(root.right);
        max = Math.max(max, dl + dr);      // writes to shared this.max
        return 1 + Math.max(dl, dr);
    }
}
```

**Why the bug exists (in one line):** `int` is a primitive — each frame gets its own copy, so `max = ...` inside the helper rebinds the helper's local slot, not the caller's.

**The full template, "why instance field works", variations table, negative-clipping idiom, and the ladder of problems all live in ⤴ Pattern 7 (Two-Purpose Recursion).** This entry exists so that when this bug bites you in a debug session, you find your way back to the pattern.

> **The 30-second test:** does my recursion need a **shared max / min / count / best** across all frames? If yes → **never pass a primitive**. Heap-ify it via an instance field (Pattern 7). Cross-references: 🧬 Stack vs Heap → "Mistake A"; `trees-fundamentals.md` → Bottom-Up Two-Pass Aggregation.

> ℹ️ **Alternative fixes** (you'll see them in books, but stick with the instance field for interviews):
> - `int[] holder = {0}` passed as a parameter — works (the array is a heap object), but looks hacky.
> - Return a tuple `class Pair { int height; int max; }` — works but verbose; functional-style codebases only.

---

### Bug 11 — Reassigning a List parameter doesn't reset the caller's list (LC 113 family)

> **Lesson learned the hard way (May 2026):** LC 113 Path Sum II — tried to "reset" the path by writing `path = new ArrayList<>();` after storing a result. The reassignment only changed THIS frame's local slot. The caller still held the original (still-being-mutated) list, and sibling branches got polluted with leaked values. Same root cause as Bug 10 — surfaces here as the List-flavored version.

```java
// ❌ Three compounding bugs
private void solve(TreeNode root, int remaining,
                   List<Integer> path, List<List<Integer>> result) {
    if (root == null) return;
    path.add(root.val);

    if (root.left == null && root.right == null && remaining == root.val) {
        result.add(path);                  // BUG 1 — stored reference, not snapshot
        path = new ArrayList<>();          // BUG 2 — local rebind; caller unaffected
        return;
    }

    solve(root.left,  remaining - root.val, path, result);
    solve(root.right, remaining - root.val, path, result);
    // BUG 3 — missing path.remove(path.size() - 1) → sibling pollution
}
```

**Pull the three bugs apart:**

| Bug | What goes wrong | Fix |
| --- | --- | --- |
| **B1: `result.add(path)`** | Stores the SAME list reference. As recursion continues mutating `path`, every saved entry mutates too. End state: a list of identical leftovers (often all `[]`). | `result.add(new ArrayList<>(path));` — **snapshot** before storing. |
| **B2: `path = new ArrayList<>()`** | Rebinds THIS frame's local slot to a new empty list. Caller's `path` reference is unchanged — caller still sees the polluted original. The new empty list dies abandoned at return. | **Don't reassign.** Mutate the existing list with `.add(...)` and `.remove(...)`. |
| **B3: No undo** | When recursion returns up to a parent, the child's `path.add(...)` is still in place. The next sibling branch starts dirty. | Pair every `path.add(...)` with a matching `path.remove(path.size() - 1)` after the recursive calls — **unconditionally**, regardless of whether the leaf matched. |

```java
// ✅ Backtracking gold standard
private void solve(TreeNode root, int remaining,
                   List<Integer> path, List<List<Integer>> result) {
    if (root == null) return;

    path.add(root.val);                       // try

    if (root.left == null && root.right == null && remaining == root.val) {
        result.add(new ArrayList<>(path));    // ✅ snapshot
    } else {
        solve(root.left,  remaining - root.val, path, result);
        solve(root.right, remaining - root.val, path, result);
    }

    path.remove(path.size() - 1);             // ✅ undo — paired with the add
}
```

**The mental picture for "reassignment doesn't reach the caller":**

```
Initial state (entering helper):
    caller.path  ────▶ ┌───────────────┐
                       │ [5, 4, 11]    │  ◀── one heap object
    helper.path  ────▶ └───────────────┘     (both arrows here)

After helper does `path = new ArrayList<>();`:
    caller.path  ────▶ ┌───────────────┐
                       │ [5, 4, 11]    │  ◀── caller's arrow UNCHANGED
                       └───────────────┘

    helper.path  ────▶ ┌───────────────┐
                       │ []            │  ◀── new heap object, abandoned at return
                       └───────────────┘
```

**Recognize this trap anywhere you see this shape:**

| Symptom | Likely bug |
| --- | --- |
| `result` contains all empty lists or all identical lists | Bug 1 — stored reference, didn't snapshot |
| "Resetting" a List/Map/array inside a recursive helper via `=` | Bug 2 — reassignment is local-only |
| Sibling branches see leaked values from an earlier branch | Bug 3 — missing undo |
| All three at once | LC 113-style three-headed bug |

> **The 30-second test:** am I about to write `path = ...` or `list = ...` or `map = ...` **inside a recursive helper**? **STOP.** That's almost always wrong. Instead: mutate the shared object with `.add` / `.put`, pair every mutation with an undo, and snapshot when storing. Cross-reference: 🧬 Stack vs Heap → "Mistake B", and **Bug 3** (forgetting to undo) + **Bug 4** (storing reference instead of snapshot) which are the building blocks of Bug 11.

---

## 🔁 Iterative ↔ Recursive Conversion

Most recursive functions can be rewritten iteratively. This isn't always better — sometimes recursion is clearer — but knowing both forms is interview-essential.

### Linear recursion → simple loop

**Recursive sum:**
```java
public int sum(int[] arr, int i) {
    if (i == arr.length) return 0;
    return arr[i] + sum(arr, i + 1);
}
```

**Iterative sum:**
```java
public int sum(int[] arr) {
    int total = 0;
    for (int x : arr) {
        total += x;
    }
    return total;
}
```

The recursive version's accumulator (the running sum across calls) becomes the loop variable.

### Tail-recursive → loop (mechanical)

Tail recursion is just an unrolled loop:

```java
// Tail-recursive factorial
private int factorialHelper(int n, int acc) {
    if (n <= 1) return acc;
    return factorialHelper(n - 1, n * acc);
}

// Iterative factorial — same code structure, just loop instead of call
public int factorial(int n) {
    int acc = 1;
    while (n > 1) {
        acc = n * acc;
        n = n - 1;
    }
    return acc;
}
```

### Binary recursion / DFS → explicit stack

For tree DFS or any branching recursion, you can simulate the call stack with a `Deque<>`.

**Steps in plain English:**

1. **Result container + null guard** — empty input means empty output.
2. **Initialize a stack with root** — the explicit stack replaces Java's call stack.
3. **Loop until stack empty** — pop a node, "visit" it (preorder = visit before children).
4. **Push children RIGHT first, LEFT second** — because a stack is LIFO, the left child is popped next, preserving preorder.
5. **Return** — when the stack drains, every node has been visited.

```java
public List<Integer> preorder(TreeNode root) {
    // Step 1 — result container + null guard
    List<Integer> result = new ArrayList<>();
    if (root == null) {
        return result;
    }
    // Step 2 — explicit stack seeded with root
    Deque<TreeNode> stack = new ArrayDeque<>();
    stack.push(root);
    // Step 3 — loop until stack drains
    while (!stack.isEmpty()) {
        TreeNode node = stack.pop();
        result.add(node.val);
        // Step 4 — push right first so left is processed first (LIFO)
        if (node.right != null) {
            stack.push(node.right);
        }
        if (node.left != null) {
            stack.push(node.left);
        }
    }
    // Step 5 — return final preorder list
    return result;
}
```

**Why convert to iteration?**
- To avoid stack overflow on very deep recursion
- For performance (no per-call frame overhead)
- When the interviewer asks "can you do this iteratively?"

---

## 🔬 Worked Walkthroughs

### Walkthrough 1: Climbing Stairs (LC 70)

> You can climb 1 or 2 steps at a time. How many distinct ways to reach step `n`?

**Apply the 3-question template:**

1. **Base case:** `n == 0` (already at top — 1 way: do nothing). `n == 1` (one step — 1 way).
2. **Recursive case:** to reach step `n`, my last move was either +1 (so I came from step n-1) or +2 (so I came from step n-2). Total ways = ways(n-1) + ways(n-2).
3. **Combine:** literally add the two.

**Naive — Steps in plain English:**

1. **Base case** — for `n <= 1`, there is exactly 1 way (do nothing or take 1 step).
2. **Sum the two predecessors** — `climbStairs(n-1) + climbStairs(n-2)`.

```java
// Naive — O(2^n), times out on LC for n > 30
public int climbStairs(int n) {
    // Step 1 — base case
    if (n <= 1) {
        return 1;
    }
    // Step 2 — sum two predecessors
    return climbStairs(n - 1) + climbStairs(n - 2);
}
```

**Memoized — Steps in plain English:**

1. **Allocate cache** — `int[n + 1]` filled with `-1` sentinel.
2. **Helper base case** — `n <= 1` returns 1.
3. **Cache hit** — if `memo[n] != -1`, return it.
4. **Compute, store, return** — sum the two predecessors, write to `memo[n]`, return.

```java
// Memoized — O(n) time, O(n) space
public int climbStairs(int n) {
    // Step 1 — cache with -1 sentinel
    int[] memo = new int[n + 1];
    Arrays.fill(memo, -1);
    return helper(n, memo);
}

private int helper(int n, int[] memo) {
    // Step 2 — base case
    if (n <= 1) {
        return 1;
    }
    // Step 3 — cache hit
    if (memo[n] != -1) {
        return memo[n];
    }
    // Step 4 — compute, store, return
    memo[n] = helper(n - 1, memo) + helper(n - 2, memo);
    return memo[n];
}
```

**The point:** identical structure to Fibonacci. Once you see one, you see all.

---

### Walkthrough 2: Generate All Subsets (LC 78)

> Return all 2^n subsets of an array.

**Apply the 3-question template:**

1. **Base case:** "we've considered every element" → record the current subset.
2. **Recursive case:** at each index `i`, we have **two choices** — include `nums[i]` in the subset or not.
3. **Combine:** the union of (subsets with `nums[i]`) ∪ (subsets without `nums[i]`).

**Two equivalent codings:**

**Style A — for-loop with backtracking (the standard)**

**Steps in plain English:**

1. **Snapshot every state** — every prefix of `path` is a valid subset, so record on entry.
2. **Loop from `start`** — only consider elements ≥ `start` to avoid duplicates.
3. **TRY** — append `nums[i]`.
4. **RECURSE** with `i + 1` (next branch picks strictly later elements).
5. **UNDO** — remove the last element so the next iteration is clean.

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> results = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), results);
    return results;
}

private void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> results) {
    // Step 1 — snapshot current path as a valid subset
    results.add(new ArrayList<>(path));
    // Step 2 — branch on each element from `start` onward
    for (int i = start; i < nums.length; i++) {
        // Step 3 — TRY
        path.add(nums[i]);
        // Step 4 — RECURSE
        backtrack(nums, i + 1, path, results);
        // Step 5 — UNDO
        path.remove(path.size() - 1);
    }
}
```

**Style B — explicit two-choice (include / exclude)**

**Steps in plain English:**

1. **Base case** — when `i == nums.length`, every element has been decided; snapshot `path` and return.
2. **Choice 1 — skip `nums[i]`** — recurse with `i + 1` without modifying `path`.
3. **Choice 2 — include `nums[i]`** — add to `path`, recurse with `i + 1`, then UNDO.

```java
private void backtrack(int[] nums, int i, List<Integer> path, List<List<Integer>> results) {
    // Step 1 — base case: all decisions made
    if (i == nums.length) {
        results.add(new ArrayList<>(path));
        return;
    }
    // Step 2 — Choice 1: skip nums[i]
    backtrack(nums, i + 1, path, results);
    // Step 3 — Choice 2: include nums[i], then undo
    path.add(nums[i]);
    backtrack(nums, i + 1, path, results);
    path.remove(path.size() - 1);
}
```

Both are O(n × 2^n) — there are 2^n subsets and copying each is O(n). Style A is more flexible (extends to combinations and permutations); Style B is more "obviously" two-choice.

> **Pick Style A for interviews** — it's the canonical pattern that generalizes to LC 39, 46, 22, 17, 79.

---

### Walkthrough 3: Merge Two Sorted Lists (LC 21)

> Merge two sorted linked lists and return the sorted merged list.

**Apply the 3-question template:**

1. **Base case:** if either list is empty, return the other.
2. **Recursive case:** the smaller head goes first; recurse on (its tail) and (the other list).
3. **Combine:** the smaller head's `.next` becomes the merged tail.

**Steps in plain English:**

1. **Empty-list short-circuits** — if `l1` is null, the merged list is just `l2` (and vice-versa).
2. **Pick the smaller head** — compare `l1.val` and `l2.val`; the smaller one is the merged list's head.
3. **Recurse on the rest** — call `mergeTwoLists` on (the chosen head's tail, the other list); set the result as the chosen head's `.next`.
4. **Return the chosen head** — its `.next` chain is now the entire merged list.

```java
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    // Step 1 — empty-list short-circuits
    if (l1 == null) {
        return l2;
    }
    if (l2 == null) {
        return l1;
    }
    // Step 2 + 3 — smaller head wins; recurse on its tail + the other list
    if (l1.val <= l2.val) {
        l1.next = mergeTwoLists(l1.next, l2);
        // Step 4 — return chosen head
        return l1;
    } else {
        l2.next = mergeTwoLists(l1, l2.next);
        return l2;
    }
}
```

**Why this is beautiful:** the recursion handles all the pointer juggling that the iterative version requires. Six lines, O(m + n) time, O(m + n) stack space.

> **Iterative version is also valid** — uses a dummy head and a current pointer. Both should be in your toolkit.

---

## 🗺️ Practice Plan — A Progression That Works

Don't try to solve all of these in one sitting. Spread over 1–2 weeks. Time-box each problem at 25 minutes.

> **Reminder of tags:** ✅ Try Now · 🟡 Try after the named prerequisite · 🔴 Reference Only

---

### Tier 1 — Foundational 6 (must be muscle memory)

These six establish the 3-question template and the basic recursion shape. Get them to "boring" before moving on.

1. ✅ **LC 509** Fibonacci Number — naive AND memoized
2. ✅ **LC 70** Climbing Stairs — same shape as Fibonacci
3. ✅ **LC 344** Reverse String — linear recursion warmup
4. ✅ **LC 206** Reverse Linked List — pointer juggling + linear recursion
5. ✅ **LC 21** Merge Two Sorted Lists — recursive merge (beautiful)
6. ✅ **LC 50** Pow(x, n) — divide-and-conquer + integer-overflow trap

> If you can write all six from memory in under 15 minutes each, you have base recursion fluency.

---

### Tier 2 — Backtracking core

Climb in order — each adds one new constraint or twist.

7. ✅ **LC 78** Subsets — the canonical backtracking template
8. 🟡 **LC 46** Permutations (after LC 78) — track which indices are used
9. 🟡 **LC 39** Combination Sum (after LC 46) — allow element reuse
10. 🟡 **LC 22** Generate Parentheses (after LC 39) — open/close count constraints
11. 🟡 **LC 17** Letter Combinations (after LC 22) — digit-to-letters mapping
12. 🟡 **LC 79** Word Search (after LC 17) — grid-based backtracking with visited

---

### Tier 3 — Memoization (top-down DP intro)

13. 🟡 **LC 198** House Robber (after LC 70 memoized)
14. 🟡 **LC 746** Min Cost Climbing Stairs
15. 🟡 **LC 322** Coin Change — memoize on remaining amount

---

### Tier 4 — Divide and Conquer / Search

16. ✅ **LC 704** Binary Search — recursive version
17. 🟡 **LC 912** Sort an Array (implement merge sort or quick sort)
18. 🟡 **LC 162** Find Peak Element — D&C on indices

---

### Tier 5 — Reference Only (multi-pattern / advanced)

19. 🔴 **LC 51** N-Queens — advanced backtracking with diagonal/column constraints
20. 🔴 **LC 37** Sudoku Solver — multi-constraint backtracking
21. 🔴 **LC 215** Kth Largest Element — quickselect (random pivot D&C)
22. 🔴 **LC 300** Longest Increasing Subsequence — needs LIS DP optimization
23. 🔴 **LC 416** Partition Equal Subset Sum — 2D memoization
24. 🔴 **LC 124** Binary Tree Maximum Path Sum — see trees doc; cross-references this pattern

---

### How to use this plan

- **Pace:** 2–3 problems/day for ~10 days clears Tiers 1–3.
- **When stuck:** time-box at 25 minutes. If still stuck, read the editorial, **don't accept-paste** — close it and rewrite from understanding.
- **Revision:** after finishing a tier, redo problems 1–2 from that tier from memory before moving on.
- **Order matters more than speed.** Tier 5 problems are seductive ("LC 51 sounds cool!") but their lessons only land after Tiers 1–4.

> **Lesson learned the hard way (May 2026):** I attempted LC 124 (in the trees doc) before completing the bottom-up DFS + two-purpose recursion ladder. It cost me an hour. **The same risk applies here — climb tiers in order.**

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

**Treating `null` like an empty subtree but forgetting it in linear recursion.** In tree problems, `null` is a base case. In linked list recursion, `null` is too — but new students forget it.

```java
// ❌ NPE on the last call
public ListNode reverseList(ListNode head) {
    ListNode reversed = reverseList(head.next);    // NPE when head == null
    ...
}

// ✅
public ListNode reverseList(ListNode head) {
    if (head == null || head.next == null) return head;
    ...
}
```

---

**Modifying a parameter you'll need later.** In the parameter-passing style of backtracking, you mutate the same `path` object. If you accidentally mutate it again after recording it, the recorded version mutates too — because Java passes the *reference*, not a copy.

```java
// ❌
results.add(path);                            // shared reference
path.add(nums[i]);                            // results.get(0) just changed!

// ✅
results.add(new ArrayList<>(path));           // deep copy
path.add(nums[i]);
```

---

**Confusing "return value" with "side effect."** Recursion can do either, but you have to pick one (or be very clear about both). Common confusion:

```java
// ❌ "I'll just print the answer" — but the function returns nothing
private void helper(...) { ... System.out.println(ans); }
public int solve(...) { helper(...); return ???; }       // nothing to return!

// ✅ either return it, or write it to an instance field
private int answer;
private void helper(...) { ... answer = ans; }
public int solve(...) { answer = 0; helper(...); return answer; }
```

---

**Off-by-one on the base case.** Should it be `n == 0` or `n == 1`? `i == arr.length` or `i == arr.length - 1`?

**The fix:** test mentally with the smallest input. For factorial, `factorial(0) == 1` (0! is 1). For sum of array, `sum([]) == 0`. For tree depth, `depth(null) == 0`. Use the smallest-input check to nail down the base case.

---

**Returning early from one branch but not the other.** Especially in OR-style recursion (e.g., "does any path exist?"), returning `false` from the first branch instead of trying the other is a classic bug.

```java
// ❌ never tries the right child
public boolean hasPath(TreeNode node, int target) {
    if (node == null) return false;
    if (node.val == target) return true;
    return hasPath(node.left, target);          // forgot the right!
}

// ✅
return hasPath(node.left, target) || hasPath(node.right, target);
```

---

**Stack overflow on adversarial input.** A "stick tree" (linked-list shaped) of 10,000 nodes will overflow recursion. The fix is iteration with an explicit stack, or constraint awareness (interview problems usually bound `n ≤ 10^4`).

---

## 🧾 TL;DR — One-Page Summary

- **Recursion** = a function that calls itself on a smaller input
- **Every recursion needs:** (1) a base case to stop, (2) a recursive call on smaller input, (3) work to combine the result
- **The 3 questions:** What's the base case? What does the recursive call return? How do I combine?
- **Leap of faith:** trust that `solve(smaller)` returns the right answer; don't trace the entire stack
- **The call stack** does the bookkeeping for free — you only write the logic for one frame
- **7 patterns:** Linear (one call), Binary (two calls), Backtracking (try/undo), Divide & Conquer (split halves), Tail/Accumulator (carry result down), Memoization (cache subproblem answers), **Two-Purpose** (return one thing + mutate a shared field — the spine of LC 543 / 124 / 687)
- **Memoization** turns most exponential recursions into polynomial — key the cache on **all** parameters that affect the answer
- **Tier 1 (Foundational 6) you must master:** LC 509, 70, 344, 206, 21, 50
- **Don't use `static`** for problem state on LeetCode — use instance fields, reset in entry method
- **Most "stack overflow" bugs** = missing base case or recursive call on the same input
- **Most "wrong answer" bugs in backtracking** = forgot the undo, or stored a reference instead of a snapshot

> **Recursion is the spine of trees, graphs, backtracking, divide-and-conquer, and DP.** The hours you put in here pay back across 60% of your interview prep.
