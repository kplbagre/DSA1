# Code Style for DSA — Reference

> **Use case:** Scan this file **before** clicking Submit, and **whenever** you finish a solution that "works but feels long." The 14 recipes below convert correct-but-messy code into clean-and-concise code — without changing the algorithm.
>
> **Companion docs:**
> - `DeepDive/recursion-fundamentals.md` § 🧬 Stack vs Heap → for why mutate-vs-reassign matters
> - `DeepDive/recursion-fundamentals.md` → Bug 10 (primitive accumulator) & Bug 11 (list reassignment)
> - `Patterns/max-path-sum-binary-tree-problem.md` → LC 124, the problem that birthed this doc

---

## 🎯 Why You're Reading This

You can write **correct** code and still get dinged in an interview for code that's:

- Verbose where a one-liner exists (`if (x > 0) sum += x;` instead of `sum += Math.max(0, x);`)
- Cluttered with debug prints and unused variables
- Using ternary where `Math.max` would read better
- Repeating computation that could be cached in a local

This doc is the gap between *"my solution passes"* and *"my solution reads like the editorial"*. Run the **Pre-Submit Cleanup Checklist** every time. Memorize the **Refactor Recipes** so the cleaner shape comes out *first* — not as a polish step.

---

## 🚦 Pre-Submit Cleanup Checklist (10 items, 60 seconds)

Run this every single time before clicking Submit, before showing code to an interviewer, before committing.

- [ ] No `System.out.println` / `print` / `console.log` left in
- [ ] No commented-out code (delete it — Git remembers if you ever need it)
- [ ] No unused variables (`int temp = solve(...)` where `temp` is never read)
- [ ] No unused imports (LeetCode-specific: clean it anyway)
- [ ] All `if (x > 0)`-style "ignore-if-negative" replaced with `Math.max(0, x)`
- [ ] Ternary for max-of-two replaced with `Math.max(a, b)`
- [ ] `list.size() == 0` replaced with `list.isEmpty()`
- [ ] Instance fields reset at top of public method (handles LeetCode class reuse)
- [ ] Variable names are short and intent-revealing (not `leftSubtreeSum`, just `left`)
- [ ] Each statement on its own line, every block braced, spaces around operators

---

## 🔹 Refactor Recipes (the 14 transformations)

Each recipe shows ❌ **before** (what you instinctively write) and ✅ **after** (what should ship). The algorithm is identical — only the form changes.

---

### Recipe 1: Negative-clipping ⭐

> Whenever the intent is **"add this if positive, otherwise ignore"**, use `Math.max(0, x)`. It collapses 3-4 lines of `if`-branching into one expression.

```java
// ❌ Before — 4 lines of branching per direction
int leftSum = recurse(node.left);
int rightSum = recurse(node.right);
int total = node.val;
if (leftSum > 0) {
    total = total + leftSum;
}
if (rightSum > 0) {
    total = total + rightSum;
}
```

```java
// ✅ After — clip once, derive
int left = Math.max(0, recurse(node.left));
int right = Math.max(0, recurse(node.right));
int total = node.val + left + right;
```

**🏷️ Example problems:** LC 124 Max Path Sum, LC 543 Diameter, LC 687 Longest Univalue Path, LC 1372 Longest ZigZag Path

---

### Recipe 2: `Math.max` / `Math.min` over ternary ⭐

> Ternary is for choosing between two **unrelated** things. For "max of two numbers" or "min of two numbers", **always** use `Math.max(a, b)` / `Math.min(a, b)`. It reads as intent, not as mechanics.

```java
// ❌ Before
int maxSide = leftDepth > rightDepth ? leftDepth : rightDepth;
int smaller = a < b ? a : b;
```

```java
// ✅ After
int maxSide = Math.max(leftDepth, rightDepth);
int smaller = Math.min(a, b);
```

> For 3-way max: `Math.max(a, Math.max(b, c))`. Yes, it's nested. Still cleaner than a 3-arm ternary.

**🏷️ Example problems:** Every tree-height / diameter / depth problem.

---

### Recipe 3: Drop unused return assignment ⭐

> If you don't use the return value, **don't name it**. The named variable signals "I'll use this later" — a reviewer wastes time looking for where.

```java
// ❌ Before — `temp` is never read
int temp = maxPathSums(root);
return max;
```

```java
// ✅ After
maxPathSums(root);
return max;
```

> Same applies to `void` returns wrapped in print/log calls during debugging — strip them.

**🏷️ Example problems:** Any two-purpose recursion (LC 124, LC 543, LC 687) where the helper's return is discarded at the root.

---

### Recipe 4: Strip debug code before submit ⭐

> `System.out.println`, commented-out experiments, exploratory `if (something) { ... }` blocks — all out. Interviewers (and reviewers) read what's on the page. Noise = signal lost.

```java
// ❌ Before
int leftSum = recurse(node.left);
int rightSum = recurse(node.right);
System.out.println(leftSum + " " + rightSum + " " + node.val);
// System.out.println(node.val);
// int oldway = leftSum + rightSum;
int total = node.val + leftSum + rightSum;
```

```java
// ✅ After
int leftSum = recurse(node.left);
int rightSum = recurse(node.right);
int total = node.val + leftSum + rightSum;
```

> **Habit:** delete debug as you go. Don't promise yourself "I'll clean it up at the end" — you won't.

---

### Recipe 5: Clip-then-derive (kill the tracking-variable swarm)

> When you find yourself with **three or more local variables tracking similar things** (`rootSum`, `leftRootSum`, `rightRootSum`), it's a smell. Compute the canonical clipped values first, then derive everything else from them.

```java
// ❌ Before — 3 tracking variables, conditional mutations
int rootSum = root.val;
int leftRootSum = root.val;
int rightRootSum = root.val;
if (leftSum > 0) {
    rootSum = rootSum + leftSum;
    leftRootSum = root.val + leftSum;
}
if (rightSum > 0) {
    rootSum = rootSum + rightSum;
    rightRootSum = root.val + rightSum;
}
max = Math.max(max, rootSum);
return Math.max(leftRootSum, rightRootSum);
```

```java
// ✅ After — 2 clipped values, derived expressions
int left = Math.max(0, leftSum);
int right = Math.max(0, rightSum);
max = Math.max(max, root.val + left + right);
return root.val + Math.max(left, right);
```

> **The rule:** **front-load** the canonical values. Don't compute the same thing twice with `if` branches.

**🏷️ Example problems:** LC 124, LC 543, LC 687, and any postorder DFS that combines child results.

---

### Recipe 6: `isEmpty()` over `size() == 0`

> Faster to type, faster to read, faster at runtime for some implementations (notably `LinkedList`).

```java
// ❌ Before
if (list.size() == 0) { ... }
if (map.size() > 0) { ... }
if (str.length() == 0) { ... }
```

```java
// ✅ After
if (list.isEmpty()) { ... }
if (!map.isEmpty()) { ... }
if (str.isEmpty()) { ... }
```

---

### Recipe 7: Enhanced for-loop when index isn't needed ⭐

> If you only need the **values** (not the indices), use the enhanced for-loop. Less ceremony, harder to introduce off-by-one bugs.

```java
// ❌ Before — index not used inside the loop
for (int i = 0; i < nums.length; i++) {
    sum = sum + nums[i];
}
```

```java
// ✅ After
for (int num : nums) {
    sum = sum + num;
}
```

> **Keep the indexed form** when you genuinely need `i` (two-pointer, comparing `nums[i]` and `nums[i-1]`, etc.).

---

### Recipe 8: `StringBuilder` over `String +=` in a loop ⭐

> `String` is immutable in Java. Every `+=` creates a new object. In a loop of `n` chars, you get O(n²) work — a silent TLE.

```java
// ❌ Before — O(n²) — TLE on long inputs
String result = "";
for (int i = 0; i < n; i++) {
    result = result + getChar(i);
}
return result;
```

```java
// ✅ After — O(n)
StringBuilder sb = new StringBuilder();
for (int i = 0; i < n; i++) {
    sb.append(getChar(i));
}
return sb.toString();
```

> **Mental check:** if a string is built character-by-character in a loop → it's `StringBuilder`. Always.

**🏷️ Example problems:** LC 415 Add Strings, LC 6 Zigzag Conversion, LC 38 Count and Say.

---

### Recipe 9: Early return over deep nesting

> An early `return` at the top costs one line and saves three levels of indentation below.

```java
// ❌ Before — pyramid of doom
public boolean isValid(TreeNode root) {
    if (root != null) {
        if (root.left != null && root.right != null) {
            if (root.left.val < root.val && root.right.val > root.val) {
                return isValid(root.left) && isValid(root.right);
            }
        }
    }
    return false;
}
```

```java
// ✅ After — guard clauses at the top
public boolean isValid(TreeNode root) {
    if (root == null) {
        return true;
    }
    if (root.left != null && root.left.val >= root.val) {
        return false;
    }
    if (root.right != null && root.right.val <= root.val) {
        return false;
    }
    return isValid(root.left) && isValid(root.right);
}
```

> **Rule of thumb:** if your method has more than 2 levels of nested `if`, hoist a guard clause to the top.

---

### Recipe 10: Boolean expression directly, not `== true`

> A boolean **is** a boolean. Comparing it to `true` is redundant — and `== false` is even worse (use `!cond`).

```java
// ❌ Before
if (visited[node] == true) { ... }
if (isLeaf(node) == false) { ... }
while (queue.isEmpty() == false) { ... }
```

```java
// ✅ After
if (visited[node]) { ... }
if (!isLeaf(node)) { ... }
while (!queue.isEmpty()) { ... }
```

---

### Recipe 11: Compute once, reuse — don't repeat `.length` / `.size()` / `.get(...)`

> When you call the same accessor in the **same statement** more than once, hoist it into a local. Reads better and saves redundant work.

```java
// ❌ Before — calls map.get(key) twice
if (map.get(key) != null && map.get(key) > threshold) { ... }
```

```java
// ✅ After
Integer val = map.get(key);
if (val != null && val > threshold) { ... }
```

```java
// ❌ Before — recomputes path.size() three times in one expression
result.add(path.get(path.size() - 1));
path.remove(path.size() - 1);
```

```java
// ✅ After
int last = path.size() - 1;
result.add(path.get(last));
path.remove(last);
```

> For loop bounds (`for (int i = 0; i < nums.length; i++)`), JIT handles it — leave it.

---

### Recipe 12: Pair add ↔ remove unconditionally in backtracking ⭐

> Every `path.add(x)` must have **exactly one** matching `path.remove(path.size() - 1)` after the recursive calls — **regardless of branch**. Don't bury the remove inside an `if` block.

```java
// ❌ Before — undo only on the recursive branch
path.add(root.val);
if (root.left == null && root.right == null) {
    if (remaining == root.val) {
        result.add(new ArrayList<>(path));
    }
    return;            // 🟥 forgot to remove before returning
}
solve(root.left, remaining - root.val, path, result);
solve(root.right, remaining - root.val, path, result);
path.remove(path.size() - 1);
```

```java
// ✅ After — undo is the last thing the method does, always
path.add(root.val);
if (root.left == null && root.right == null && remaining == root.val) {
    result.add(new ArrayList<>(path));
} else {
    solve(root.left, remaining - root.val, path, result);
    solve(root.right, remaining - root.val, path, result);
}
path.remove(path.size() - 1);
```

> Full diagnosis: `DeepDive/recursion-fundamentals.md` → **Bug 11 — Reassigning a List parameter doesn't reset the caller's list**.

**🏷️ Example problems:** LC 113 Path Sum II, LC 46 Permutations, LC 78 Subsets, LC 39 Combination Sum.

---

### Recipe 13: Reset instance fields at the top of the entry method ⭐

> LeetCode reuses your `Solution` instance across test cases. An instance field that was left at `2` from the last test will start the next test at `2`. Always reset.

```java
// ❌ Before — fails on the second test case
class Solution {
    private int max = Integer.MIN_VALUE;

    public int diameterOfBinaryTree(TreeNode root) {
        depth(root);
        return max;
    }
}
```

```java
// ✅ After — reset at the top
class Solution {
    private int max;

    public int diameterOfBinaryTree(TreeNode root) {
        max = Integer.MIN_VALUE;        // reset every run
        depth(root);
        return max;
    }
}
```

> Full diagnosis: `DeepDive/recursion-fundamentals.md` → **Bug 10 — Primitive accumulator parameter doesn't propagate up**.

**🏷️ Example problems:** LC 124, LC 543, LC 687, LC 1373 — all two-purpose-recursion problems.

---

### Recipe 14: Named constants over magic numbers

> `26` is fine in a 5-line snippet; in a 30-line solution it's a smell. Named constants tell the reader what the number **means**.

```java
// ❌ Before — what's 26? what's 128?
int[] freq = new int[26];
int[] ascii = new int[128];
```

```java
// ✅ After
private static final int ALPHABET = 26;
private static final int ASCII_RANGE = 128;

int[] freq = new int[ALPHABET];
int[] ascii = new int[ASCII_RANGE];
```

> For a single-use number in a short method, `int[] freq = new int[26];` is acceptable. The smell threshold is **"does this number appear more than once, or is its meaning non-obvious?"**

---

## 🔹 Naming Conventions for DSA

The DSA naming rule is **opposite** to enterprise code: **shorter is better** because the scope is tiny and the patterns are universal.

| Context | Use | Avoid |
| --- | --- | --- |
| Two-pointer | `l, r` or `left, right` | `leftPointer, rightPointer` |
| Binary search | `lo, hi, mid` | `lowerBound, upperBound, middle` |
| Sliding window | `start, end` or `l, r` | `windowStart, windowEnd` |
| Array sizes | `n, m` | `arrayLength, matrixHeight` |
| Loop counters | `i, j, k` | `index, innerIndex` |
| Tree traversal | `root, node, curr` | `currentNode, treeRoot` |
| Linked list | `head, tail, curr, prev` | `headNode, currentNode` |
| Tree children | `left, right` | `leftChild, rightChild` |
| Recursive call result | `leftSum, rightSum` or `dl, dr` | `leftSubtreeSum, rightSubtreeSum` |
| Counter/frequency map | `freq, count, cnt` | `frequencyMap, countMap` |
| Visited set | `seen, visited` | `visitedNodes, alreadySeenNodes` |
| Result container | `ans, result, res` | `finalAnswer, outputList` |
| Path / current state | `path, curr, state` | `currentPathSoFar` |
| Best-so-far | `best, max, min` | `bestSoFar, maximumValue` |
| Char arithmetic | `c - 'a'` | `(int)(c) - (int)('a')` |

> **The rule:** if a variable's scope is **under 30 lines**, use a 1–2-letter or single-word name. Save long names for class fields and method signatures.

---

## 🔹 Accumulator Initialization Cheatsheet

| Accumulator type | Initialize to | Why |
| --- | --- | --- |
| Sum | `0` | Additive identity |
| Product | `1` | Multiplicative identity |
| Count | `0` | Starting count |
| Max-so-far | `Integer.MIN_VALUE` | Anything beats it |
| Min-so-far | `Integer.MAX_VALUE` | Anything beats it |
| Max with possible overflow | `Long.MIN_VALUE` | E.g., LC 124 with large negatives |
| Min sum with `int` overflow risk | `Long.MAX_VALUE` | Same reason |
| List-of-results | `new ArrayList<>()` | Empty container |
| Set of visited | `new HashSet<>()` | Empty container |
| Frequency map | `new HashMap<>()` | Empty container |
| String builder | `new StringBuilder()` | Empty buffer |
| Best path / node | `null` | "Not found yet" sentinel |
| Boolean "found" | `false` | OR-accumulator default |
| Boolean "all match" | `true` | AND-accumulator default |

> **The gotcha:** if your problem says "at least one node" or "non-empty", use `Integer.MIN_VALUE` / `Integer.MAX_VALUE` — **not** `0`. Otherwise an all-negative input returns wrongly as `0`. See LC 124 (Max Path Sum).

---

## 🔹 Anti-Patterns Hall of Shame (Real Code I've Written)

### **Anti-pattern 1: The three-variable swarm (from LC 124)**

```java
// ❌ Don't do this
int rootSum = root.val;
int leftRootSum = root.val;
int rightRootSum = root.val;
if (leftSum > 0) { rootSum += leftSum; leftRootSum += leftSum; }
if (rightSum > 0) { rootSum += rightSum; rightRootSum += rightSum; }
```

**Fix:** Recipe 5 — clip once, derive.

---

### **Anti-pattern 2: The unused `temp` (from LC 124)**

```java
// ❌ Don't do this
int temp = maxPathSums(root);
return max;
```

**Fix:** Recipe 3 — drop the assignment, just call the method.

---

### **Anti-pattern 3: Debug prints in submitted code**

```java
// ❌ Don't do this
System.out.println(node.val + " " + leftSum + " " + rightSum);
```

**Fix:** Recipe 4 — strip before submit.

---

### **Anti-pattern 4: `if (x > 0)` instead of `Math.max(0, x)`**

Covered in Recipe 1. This is the **#1 most common** missed optimization in postorder DFS problems.

---

### **Anti-pattern 5: Ternary for max-of-two**

```java
// ❌ Don't do this
return leftRootSum > rightRootSum ? leftRootSum : rightRootSum;
```

**Fix:** Recipe 2 — `Math.max`.

---

## 🔹 Per-Pattern Code Skeletons (the canonical shapes)

Each skeleton below is the **clean form** for its pattern. If your code doesn't look like this after applying recipes, something's still off.

### Skeleton 1: Bottom-up DFS (two-purpose recursion)

```java
class Solution {
    private int max;

    public int solve(TreeNode root) {
        max = Integer.MIN_VALUE;
        helper(root);
        return max;
    }

    private int helper(TreeNode node) {
        if (node == null) {
            return 0;
        }
        int left = Math.max(0, helper(node.left));
        int right = Math.max(0, helper(node.right));
        max = Math.max(max, left + right + node.val);
        return node.val + Math.max(left, right);
    }
}
```

**🏷️ Used by:** LC 124, LC 543, LC 687, LC 1373.

---

### Skeleton 2: BFS by level (size-snapshot trick)

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) {
        return result;
    }

    Deque<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int levelSize = queue.size();
        List<Integer> level = new ArrayList<>();

        for (int i = 0; i < levelSize; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }
        result.add(level);
    }
    return result;
}
```

**🏷️ Used by:** LC 102, LC 103, LC 107, LC 199, LC 515, LC 1161.

---

### Skeleton 3: Backtracking (try → recurse → undo)

```java
private void backtrack(TreeNode node, int remaining, List<Integer> path, List<List<Integer>> result) {
    if (node == null) {
        return;
    }
    path.add(node.val);

    if (node.left == null && node.right == null && remaining == node.val) {
        result.add(new ArrayList<>(path));
    } else {
        backtrack(node.left, remaining - node.val, path, result);
        backtrack(node.right, remaining - node.val, path, result);
    }

    path.remove(path.size() - 1);
}
```

**🏷️ Used by:** LC 113, LC 46, LC 47, LC 78, LC 39, LC 51.

---

### Skeleton 4: Two-pointer (opposite ends)

```java
public int[] twoSum(int[] nums, int target) {
    int l = 0;
    int r = nums.length - 1;

    while (l < r) {
        int sum = nums[l] + nums[r];
        if (sum == target) {
            return new int[]{ l, r };
        }
        if (sum < target) {
            l++;
        } else {
            r--;
        }
    }
    return new int[]{ -1, -1 };
}
```

**🏷️ Used by:** LC 167, LC 11, LC 15 (after sorting), LC 42.

---

### Skeleton 5: Sliding window (variable size)

```java
public int longestSubstring(String s) {
    int l = 0;
    int best = 0;
    Set<Character> seen = new HashSet<>();

    for (int r = 0; r < s.length(); r++) {
        char c = s.charAt(r);
        while (seen.contains(c)) {
            seen.remove(s.charAt(l));
            l++;
        }
        seen.add(c);
        best = Math.max(best, r - l + 1);
    }
    return best;
}
```

**🏷️ Used by:** LC 3, LC 76, LC 209, LC 424, LC 438.

---

### Skeleton 6: Binary search (canonical lo/hi)

```java
public int search(int[] nums, int target) {
    int lo = 0;
    int hi = nums.length - 1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] == target) {
            return mid;
        }
        if (nums[mid] < target) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    return -1;
}
```

**🏷️ Used by:** LC 704, LC 34, LC 33, LC 153, LC 162.

> **The `lo + (hi - lo) / 2` trick** — avoids `int` overflow when `lo + hi` would exceed `Integer.MAX_VALUE`. Use it always; it costs nothing.

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

These compile, run, and produce **wrong** output. Watch for them.

---

**`Math.abs(Integer.MIN_VALUE)` returns `Integer.MIN_VALUE`.**

```java
int x = Math.abs(Integer.MIN_VALUE);     // -2147483648 ❌
long x = Math.abs((long) Integer.MIN_VALUE);   // 2147483648 ✅
```

> Two's-complement has one more negative than positive. The fix: promote to `long` before calling `abs`.

---

**`a + b` overflows for two large `int`s.**

```java
int mid = (lo + hi) / 2;          // overflow when lo+hi > Integer.MAX_VALUE ❌
int mid = lo + (hi - lo) / 2;     // safe ✅
```

---

**`list.remove(int)` removes by index, not by value.**

```java
List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));
list.remove(2);                   // removes index 2 → list is [1, 2] ❌
list.remove(Integer.valueOf(2));  // removes value 2 → list is [1, 3] ✅
```

---

**`String.equals` vs `==` — never use `==` to compare string contents.**

```java
if (str == "hello") { ... }       // reference equality — usually wrong ❌
if (str.equals("hello")) { ... }  // value equality ✅
if ("hello".equals(str)) { ... }  // value equality, null-safe ✅
```

---

**Auto-unboxing NPE on `Map<K, Integer>` lookup.**

```java
int count = freqMap.get(key);                  // NPE if key absent ❌
int count = freqMap.getOrDefault(key, 0);      // safe ✅
```

---

**Instance field carries state across LeetCode test cases.**

```java
class Solution {
    private int max = Integer.MIN_VALUE;       // 🟥 wrong on test 2

    public int solve(TreeNode root) {
        helper(root);
        return max;
    }
}
```

> Fix: reset `max` at the top of `solve(...)`. See Recipe 13.

---

**`Arrays.toString(arr)` vs `arr.toString()` — wildly different output.**

```java
int[] arr = { 1, 2, 3 };
arr.toString();             // "[I@1540e19d" ❌
Arrays.toString(arr);       // "[1, 2, 3]" ✅
```

---

**Mutating a list during iteration → `ConcurrentModificationException`.**

```java
for (Integer x : list) {
    if (x < 0) {
        list.remove(x);              // throws ❌
    }
}
```

> Fix: use an iterator and `it.remove()`, or build a new list.

---

## ⚡ Quick Cheat Sheet

| Smell | Replace with |
| --- | --- |
| `if (x > 0) sum += x;` | `sum += Math.max(0, x);` |
| `a > b ? a : b` | `Math.max(a, b)` |
| `a < b ? a : b` | `Math.min(a, b)` |
| `int temp = solve(...);` (unused) | `solve(...);` |
| `System.out.println(...)` in submitted code | (delete) |
| `list.size() == 0` | `list.isEmpty()` |
| `if (cond == true)` | `if (cond)` |
| `if (cond == false)` | `if (!cond)` |
| `String result = ""; for (...) result += ...;` | `StringBuilder sb = ...; sb.append(...);` |
| Three tracking variables for the same idea | Two clipped values + derive |
| `map.get(k)` called twice in one expression | Hoist to a local |
| Nested `if (a) { if (b) { if (c) { ... } } }` | Guard clauses + early return |
| `26` literal scattered around | `static final int ALPHABET = 26;` |
| `for (int i = 0; i < arr.length; i++) sum += arr[i];` | `for (int n : arr) sum += n;` |
| `private int max = Integer.MIN_VALUE;` (class field, no reset) | Reset in entry method |

---

## 🧾 TL;DR

1. **`Math.max(0, x)`** — the negative-clipping idiom. Use it any time the intent is "add if positive."
2. **`Math.max(a, b)` and `Math.min(a, b)`** — always over ternary for two-number comparison.
3. **Clip-then-derive** — kill the three-variable swarm. Front-load canonical values.
4. **Pre-submit checklist** — 10 items, 60 seconds. Strip prints, drop unused vars, check `isEmpty`.
5. **Reset instance fields** at the top of the entry method. LeetCode reuses `Solution` instances.
6. **Short DSA names** — `l, r, n, m, freq, seen, ans, path, curr, prev`. Long names for class fields only.
7. **Canonical skeletons** for each pattern (DFS, BFS, backtracking, two-pointer, sliding window, binary search). If your code doesn't look like the skeleton after cleanup, something's still off.

> **The mantra:** *"Correct first, then clean. But clean before you submit."*
