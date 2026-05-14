# Binary Tree Maximum Path Sum

> **LeetCode:** [124. Binary Tree Maximum Path Sum](https://leetcode.com/problems/binary-tree-maximum-path-sum/) — Hard
> **Pattern:** Two-purpose recursion (return one thing, update a global) — see `DeepDive/trees-fundamentals.md` § Pattern 2 (Postorder DFS)
> **Uses:** Postorder DFS, negative-clipping, instance-field accumulator

---

## 📌 Problem

Given the `root` of a binary tree, return the **maximum sum** of any **non-empty path** in the tree.

> **Path** = sequence of nodes where each adjacent pair is connected by an edge. A path can start and end at any node, must visit each node at most once, and **does NOT need to pass through the root**. The path can also **bend** at any node (go up the left subtree, through this node, and down into the right subtree).

### Examples

```
Input:  [1, 2, 3]
        1
       / \
      2   3
Output: 6
Explanation: path = 2 → 1 → 3, sum = 6.
```

```
Input:  [-10, 9, 20, null, null, 15, 7]
            -10
           /    \
          9      20
                /  \
               15   7
Output: 42
Explanation: path = 15 → 20 → 7, sum = 42. The path skips the negative root.
```

```
Input:  [-3]
Output: -3
Explanation: A single node is a valid non-empty path.
```

### Constraints (typical)

- `1 ≤ number of nodes ≤ 3 * 10^4`
- `-1000 ≤ Node.val ≤ 1000`
- Path must be **non-empty** (at least one node).

---

## 🧠 Pattern Recognition

> **"Find the best path in a tree that can bend at any node."**
>
> Whenever you see a tree problem where:
> 1. The path can **bend** at any node (go up one side and down the other), AND
> 2. You're **searching the whole tree** for the best such path,
>
> Your first thought should be: **two-purpose recursion**. The recursive function returns **one thing** (the best one-sided path that *could extend upward*), and **updates a global** (the best bent path seen anywhere). Negative subtrees should be **clipped to 0** because a parent can always choose not to include them.

This is the **Two-Purpose Postorder DFS** pattern. See `DeepDive/trees-fundamentals.md` § Pattern 2 for the full framework, and the "Building Up to Two-Purpose Recursion" ladder (LC 104 → LC 110 → LC 543 → LC 687 → LC 124).

---

## ❌ Approach 1: Brute Force — Try Every Node as the "Bend Point"

> **One-line characterization** — *"compute the longest path going down from each node, then for every node try bending through it."*

### Idea

For each node `n`:
1. Compute `maxPathDown(n)` — the maximum sum of a single downward path starting at `n`.
2. For each node, the best "bent" path through `n` is `n.val + maxPathDown(n.left) + maxPathDown(n.right)` (with negatives clipped).
3. Take the global max over all nodes.

This works, but `maxPathDown` is called repeatedly for the same subtrees from different ancestors. Time blows up.

### Code

**Steps in plain English:**

1. **Outer DFS** — visit every node.
2. **For each node, compute `maxPathDown` once for each child** by calling a separate helper that recursively computes the best one-sided path.
3. **Combine** the two child contributions (clipped at 0) with the node's own value to get the best bent path through this node.
4. **Update the global maximum** with that candidate.

```java
class Solution {
    private int max;

    public int maxPathSum(TreeNode root) {
        max = Integer.MIN_VALUE;
        dfs(root);
        return max;
    }

    private void dfs(TreeNode node) {
        if (node == null) {
            return;
        }
        // Step 2 — compute best one-sided path going down each side
        int left = Math.max(0, maxPathDown(node.left));
        int right = Math.max(0, maxPathDown(node.right));

        // Step 3 — best bent path through this node
        int candidate = node.val + left + right;

        // Step 4 — global update
        max = Math.max(max, candidate);

        // Recurse to try every node as the bend point
        dfs(node.left);
        dfs(node.right);
    }

    private int maxPathDown(TreeNode node) {
        if (node == null) {
            return 0;
        }
        int left = Math.max(0, maxPathDown(node.left));
        int right = Math.max(0, maxPathDown(node.right));
        return node.val + Math.max(left, right);
    }
}
```

### Complexity

| | |
| --- | --- |
| Time | **O(n²)** — for each of `n` nodes, `maxPathDown` traverses up to `n` nodes |
| Space | O(h) — recursion stack, h = tree height |

> **Why this approach is wasteful** — every subtree's max-downward-path is recomputed from every ancestor. We can compute it **once** during a single postorder pass.

---

## 🚀 Approach 2: Single-Pass Two-Purpose Recursion (Optimal)

> **One-line characterization** — *"compute everything during one postorder pass: the helper returns the best one-sided path, and updates a global with the best bent path."*

### Idea

Collapse Brute Force's two recursions into one:

1. The helper `gain(node)` returns the **best one-sided downward path** starting at `node` (what a parent could use to extend through this side).
2. **Inside `gain`**, before returning, we also compute the **bent-path-through-this-node** candidate (`left + right + node.val`) and update the global `max`.

This is the canonical **two-purpose recursion**: function returns one thing, mutates a side-effect global.

### Code

**Steps in plain English:**

1. **Declare instance field** `max` for the global best.
2. **Reset `max`** at the top of the public method (LeetCode reuses the `Solution` instance).
3. **Call the helper, discard its return** at the root (we don't use the root's one-sided path).
4. **In the helper, base case:** `null` node contributes 0.
5. **Recurse into both children, clip negatives** with `Math.max(0, ...)` — a parent can always skip a subtree that drags down the sum.
6. **Update global** with the bent-path candidate (uses BOTH sides + this node).
7. **Return one-sided extension** to the parent — `node.val + Math.max(left, right)` (parent can only extend through one side).

```java
class Solution {
    private int max;

    public int maxPathSum(TreeNode root) {
        max = Integer.MIN_VALUE;            // Step 2 — reset for this run
        gain(root);                          // Step 3 — discard root's one-sided return
        return max;
    }

    private int gain(TreeNode node) {
        // Step 4 — base case
        if (node == null) {
            return 0;
        }

        // Step 5 — recurse + clip negatives
        int left = Math.max(0, gain(node.left));
        int right = Math.max(0, gain(node.right));

        // Step 6 — global update: bent path through this node
        max = Math.max(max, node.val + left + right);

        // Step 7 — return one-sided path for parent to extend
        return node.val + Math.max(left, right);
    }
}
```

### Walkthrough

Trace on `[-10, 9, 20, null, null, 15, 7]`:

```
              -10
             /    \
            9      20
                  /  \
                 15   7

Postorder visits: 9, 15, 7, 20, -10

gain(9):
    left = max(0, gain(null)) = 0
    right = max(0, gain(null)) = 0
    max = max(MIN, 9 + 0 + 0) = 9
    return 9 + max(0, 0) = 9

gain(15):
    left = max(0, gain(null)) = 0
    right = max(0, gain(null)) = 0
    max = max(9, 15 + 0 + 0) = 15
    return 15

gain(7):
    similar → max = max(15, 7) = 15
    return 7

gain(20):
    left = max(0, gain(15)) = 15
    right = max(0, gain(7)) = 7
    max = max(15, 20 + 15 + 7) = 42        ◀── the answer
    return 20 + max(15, 7) = 35

gain(-10):
    left = max(0, gain(9)) = 9
    right = max(0, gain(20)) = 35
    max = max(42, -10 + 9 + 35) = max(42, 34) = 42
    return -10 + max(9, 35) = 25

Final: max = 42
```

### Complexity

| | |
| --- | --- |
| Time | **O(n)** — visit each node exactly once |
| Space | O(h) — recursion stack, h = tree height |

> **Why this approach is optimal** — every subtree's max-downward-path is computed exactly once during the postorder pass. The "bent through this node" check is O(1) per node.

---

## 📊 Approach Comparison

| Approach | Time | Space | Notes |
| --- | --- | --- | --- |
| 1. Brute force (DFS + per-node downward-DFS) | **O(n²)** | O(h) | First instinct; works but TLEs on a 30k-node tree |
| 2. Single-pass two-purpose recursion | **O(n)** | O(h) | Standard interview answer |

> **Interview tip:** Skip Approach 1 entirely. Go directly to Approach 2 with a sentence: *"This is two-purpose recursion — helper returns the one-sided path for the parent to extend, and updates a global with the bent-path candidate."* Then write it.

---

## 🐞 Common Bugs in LC 124

### **Bug 1: Initializing `max` to `0` instead of `Integer.MIN_VALUE`**

```java
private int max = 0;           // ❌ wrong for all-negative trees
```

If every node is negative (e.g., `[-3]` or `[-2, -1]`), the answer is the largest (least negative) single node. Starting at `0` returns `0` — wrong.

**Fix:** `max = Integer.MIN_VALUE;` and reset it inside the public method.

---

### **Bug 2: Forgetting to clip negatives**

```java
int left = gain(node.left);            // ❌ may be negative
int right = gain(node.right);
max = Math.max(max, node.val + left + right);
```

If `gain(node.left) = -5`, then `node.val + (-5) + right` is *worse* than just `node.val + right`. A parent would never choose to include a negative subtree.

**Fix:** `int left = Math.max(0, gain(node.left));` — clip negatives. The path that "skips" the negative subtree always exists as an option.

---

### **Bug 3: Returning the bent-path sum instead of the one-sided sum**

```java
return node.val + left + right;        // ❌ wrong — parent can't extend through BOTH sides
```

The function's **return value** is what the parent will use to extend through **one side** of this node. If you return the bent-path sum, the parent thinks it can chain a path that goes through both children — but a path can only enter and exit a node once.

**Fix:** Return `node.val + Math.max(left, right)`. The bent-path candidate is the **global update** value, not the return value.

---

### **Bug 4: Trying to track `max` as a parameter instead of an instance field**

```java
public int maxPathSum(TreeNode root) {
    int max = Integer.MIN_VALUE;
    gain(root, max);
    return max;                        // ❌ always returns MIN_VALUE
}

private int gain(TreeNode node, int max) {        // 🟥 primitive — local-only
    ...
    max = Math.max(max, ...);         // mutates THIS frame's local int
    ...
}
```

`int` is a primitive, passed by value. Reassigning `max` inside `gain` only changes that frame's copy.

**Fix:** Use an instance field (`private int max;`) reset in the public method. Full diagnosis: `DeepDive/recursion-fundamentals.md` → **Bug 10 — Primitive accumulator parameter doesn't propagate up**.

---

### **Bug 5: Forgetting to reset the instance field across LeetCode test cases**

```java
class Solution {
    private int max = Integer.MIN_VALUE;       // 🟥 initialized once, never reset

    public int maxPathSum(TreeNode root) {
        gain(root);
        return max;                            // ❌ second test reuses last run's max
    }
}
```

LeetCode reuses your `Solution` instance across test cases. If test 1 left `max = 42`, test 2 starts with `max = 42` instead of `MIN_VALUE`.

**Fix:** Reset at the top of `maxPathSum`: `max = Integer.MIN_VALUE;`.

---

## 🔁 Variations & Follow-ups

### **1. What if the path must start or end at the root?**

That's a different problem — easier. You don't need the global; just return `node.val + Math.max(0, max(gain(left), gain(right)))` from the root. Bend is no longer allowed at non-root nodes.

### **2. What if you need the actual path, not just the sum?**

Augment the recursion to also return (or store on the side) the actual node sequence. Trickier — you'd typically store start/end nodes globally and reconstruct via parent pointers, or track the path as a list during the bent-update step.

### **3. What if all values are non-negative?**

The problem becomes "longest leaf-to-leaf path sum" (or root-down sum). The negative-clipping at `Math.max(0, ...)` is still a no-op safe operation, so the same code works — but you could simplify by dropping the clip if values are guaranteed `≥ 0`.

### **4. What if the path can only go through edges (not bend at nodes)?**

That's **LC 543 Diameter of Binary Tree** (counts edges, not sum). Same shape: return one-sided height, update global with bent length. The simpler "warm-up" version of LC 124.

### **5. What if you need this for a general graph, not a tree?**

Now you need cycle detection (mark visited), and the "two-sided bend" concept needs reframing — graphs can revisit nodes. This becomes a much harder problem (longest path in a general graph is NP-hard for general weights).

### **6. What if the tree is huge (10^6 nodes)?**

Recursion will stack-overflow for skewed trees. Convert to **iterative postorder** with an explicit stack, but maintain the same two-purpose pattern (track "is this the first or second visit to this node" via a state flag).

### **7. What if the node values can be larger (overflow risk)?**

If `Node.val` could be `±10^9` and the tree has `10^4` nodes, sums can hit `±10^13` — overflows `int`. Use `long` for `max`, the helper's return, and intermediate sums.

---

## 🎯 Key Takeaways

1. **Two-purpose recursion** — function returns the **one-sided** extension for the parent; **mutates a global** with the bent-path candidate. The return and the global update are *different expressions* (`Math.max(left, right) + val` vs `left + right + val`).
2. **Negative-clipping with `Math.max(0, ...)`** is the gateway to elegant postorder code. A parent can always skip a subtree that drags down the sum — represent that "skip" as `0`.
3. **Instance field for the global** is the canonical fix. Reset at the top of the public method. Never pass a primitive accumulator (see Bug 4 + recursion-fundamentals.md Bug 10).
4. **Initialize `max` to `Integer.MIN_VALUE`**, not `0`. The problem says "at least one node" — for all-negative trees the answer can be negative.
5. **The "bend point" is the recursive frame itself.** Every node gets one chance to be the bend point, exactly when its `gain` call combines `left + right + val` into the global update.

---

## 🔗 Related Notes & Problems

### Notes referenced

- `DeepDive/trees-fundamentals.md` → **Pattern 2 (Postorder DFS / Two-Purpose Recursion)** — general framework
- `DeepDive/trees-fundamentals.md` → **Walkthrough 3: Binary Tree Maximum Path Sum (LC 124)** — companion walkthrough
- `DeepDive/recursion-fundamentals.md` → **Bug 10 — Primitive accumulator parameter doesn't propagate up** — why Bug 4 above happens
- `DeepDive/recursion-fundamentals.md` → **🧬 Stack vs Heap → Mistake A** — same root cause, broader framing

### Similar problems (same two-purpose pattern)

- **LC 543 Diameter of Binary Tree** — easier warm-up. Edges instead of sum. **Solve this before LC 124.**
- **LC 687 Longest Univalue Path** — same shape with parent-child value-matching constraint.
- **LC 1373 Maximum Sum BST in Binary Tree** — combines two-purpose recursion + BST validation.
- **LC 1372 Longest ZigZag Path** — two-purpose recursion with a direction flag.

### Adjacent problems (related but different pattern)

- **LC 112 Path Sum** — top-down DFS, not bottom-up. Doesn't need negative clipping or global update.
- **LC 113 Path Sum II** — backtracking with path list. Different beast (carry path down, snapshot at leaf).
- **LC 129 Sum Root to Leaf Numbers** — top-down accumulation, not bottom-up two-purpose.

---

## 🧪 Quick Self-Test

Without looking, can you:

- [ ] State **why** the helper's return value and the global update use *different* expressions?
- [ ] Explain **why `Math.max(0, gain(...))`** is necessary, and what would break without it?
- [ ] Write Approach 2 from scratch in under 5 minutes?
- [ ] Initialize `max` correctly (and explain why `0` is wrong)?
- [ ] Reset the instance field at the right place for LeetCode test reuse?

If yes to all → you've internalized the **two-purpose recursion** pattern. ✅

---

> **Practice order:** LC 543 → LC 687 → LC 124. Don't attempt LC 124 cold without first making LC 543 muscle memory.
