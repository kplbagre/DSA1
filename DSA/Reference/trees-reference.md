# Trees — Reference

> Compact daily-revision cheatsheet for binary tree problems. Read **`DeepDive/trees-fundamentals.md`** once for the conceptual walkthrough; live in this file during practice.

**Companion files:**
- **`DeepDive/trees-fundamentals.md`** — full deep dive (4 patterns, decision framework, ASCII diagrams, worked LC 236 callout)
- **`DeepDive/recursion-fundamentals.md`** — Pattern 7 (Two-Purpose Recursion) lives here as a first-class pattern
- **`Patterns/max-path-sum-binary-tree-problem.md`** — LC 124 brute-to-optimal walkthrough
- **`Reference/code-style-for-dsa-reference.md`** — refactor recipes (negative-clip, instance-field-reset, etc.)

---

## 📖 TreeNode (canonical)

LeetCode pre-defines this — you don't write it:

```java
public class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;
    TreeNode() {}
    TreeNode(int val) {
        this.val = val;
    }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}
```

> **Mental hook:** a tree is just a graph with no cycles and one designated root. `null` is the boundary — every recursion should handle `root == null` on line 1.

---

## 🔹 Traversal Cheatsheet

| Order | Visit order | When to use | Time | Space |
| --- | --- | --- | --- | --- |
| **Preorder** | Root → Left → Right | Serialize tree, clone tree, root-first processing | O(n) | O(h) |
| **Inorder** | Left → Root → Right | **BST → sorted output**; LC 94, 98, 230 | O(n) | O(h) |
| **Postorder** | Left → Right → Root | Bottom-up aggregation (delete tree, sum subtree, Pattern 7) | O(n) | O(h) |
| **Level-order (BFS)** | Top to bottom, level by level | Per-level work, shortest path in unweighted tree | O(n) | O(w) |

`h` = height of tree (best O(log n), worst O(n)). `w` = max width.

### Recursive DFS (all three orders share the shape)

```java
void preorder(TreeNode root) {
    if (root == null) {
        return;
    }
    visit(root);
    preorder(root.left);
    preorder(root.right);
}

void inorder(TreeNode root) {
    if (root == null) {
        return;
    }
    inorder(root.left);
    visit(root);
    inorder(root.right);
}

void postorder(TreeNode root) {
    if (root == null) {
        return;
    }
    postorder(root.left);
    postorder(root.right);
    visit(root);
}
```

### Iterative inorder (the one worth memorizing — LC 94)

```java
List<Integer> inorder(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;
    while (curr != null || !stack.isEmpty()) {
        while (curr != null) {
            stack.push(curr);
            curr = curr.left;
        }
        curr = stack.pop();
        result.add(curr.val);
        curr = curr.right;
    }
    return result;
}
```

### BFS — level-order (the workhorse iterative template)

```java
List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) {
        return result;
    }
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        int size = queue.size();
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < size; i++) {
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

> **The `int size = queue.size()` trick is the level boundary** — freeze the size before the inner loop so you only drain the current level's nodes.

---

## 🧭 The 4 Big Patterns

Every tree problem maps to one of these four shapes. Recognize → write the signature → fill in.

---

### Pattern A: Top-Down DFS (info flows DOWN via parameters)

> Each frame computes its answer using info **passed down** from the parent (depth so far, max-on-path so far, running sum, etc.). The recursion does the work as it descends.

```java
void dfs(TreeNode root, int infoFromParent) {
    if (root == null) {
        return;
    }
    int newInfo = combine(infoFromParent, root.val);
    // update result based on (root, newInfo) here
    dfs(root.left, newInfo);
    dfs(root.right, newInfo);
}
```

**🏷️ Example problems:** LC 104 Max Depth (depth passed down), LC 112 Path Sum (remaining target passed down), LC 257 Binary Tree Paths, LC 1448 Good Nodes (max-on-path passed down).

> **Mental hook:** *"I know everything from root to here — what can I compute about THIS node?"*

---

### Pattern B: Bottom-Up Two-Pass Aggregation (info flows UP via return) ⭐

> Each frame asks its children for some sub-info first, then combines + (often) updates a global. **This is Pattern 7 from the recursion notes.** Function returns what the *parent* needs; an instance field captures the *answer*.

**Canonical template — Steps in plain English:**

1. Instance field for the global answer (reset in the public method).
2. Recurse on both children, capture each return.
3. Update the global with both children's contribution.
4. Return the *one-sided* / parent's-eye-view value.

```java
class Solution {
    private int best = 0;

    public int solve(TreeNode root) {
        best = 0;
        helper(root);
        return best;
    }

    private int helper(TreeNode root) {
        if (root == null) {
            return 0;
        }
        int left = helper(root.left);
        int right = helper(root.right);
        best = Math.max(best, combineForGlobal(left, right));
        return parentView(left, right);
    }
}
```

**🏷️ Example problems:** LC 543 Diameter, LC 124 Max Path Sum, LC 687 Longest Univalue Path, LC 110 Balanced Binary Tree, LC 1373 Max Sum BST.

> **Mental hook:** *"What does the parent need from me? What does the whole tree need from me? If those differ → Pattern B."*

> **Cross-references:** Full template + worked LC 543 trace in `DeepDive/recursion-fundamentals.md` → Pattern 7. LC 124 brute-to-optimal in `Patterns/max-path-sum-binary-tree-problem.md`.

---

### Pattern C: BFS Level-Order ⭐

> Process the tree **level by level** using a queue. Use this whenever the problem mentions levels, depth-K nodes, leftmost/rightmost-per-level, or shortest path in an unweighted tree.

```java
List<Integer> rightSideView(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) {
        return result;
    }
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            if (i == size - 1) {
                result.add(node.val);
            }
            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }
    }
    return result;
}
```

**🏷️ Example problems:** LC 102 Level Order, LC 107 Bottom-Up Level Order, LC 199 Right Side View, LC 515 Largest in Each Row, LC 116 Populating Next Right Pointers, LC 637 Average of Levels.

> **Mental hook:** *"The word 'level' or 'depth-K' or 'per row' appeared → BFS."*

---

### Pattern D: Two-Tree Parallel Traversal

> Two recursions advance together in lockstep on two trees. Used for structural-equality, symmetry, merge, or subtree-containment checks.

```java
boolean isSameTree(TreeNode p, TreeNode q) {
    if (p == null && q == null) {
        return true;
    }
    if (p == null || q == null) {
        return false;
    }
    if (p.val != q.val) {
        return false;
    }
    return isSameTree(p.left, q.left) && isSameTree(p.right, q.right);
}
```

**🏷️ Example problems:** LC 100 Same Tree, LC 101 Symmetric Tree (mirrored — recurse on `(left.left, right.right)` AND `(left.right, right.left)`), LC 572 Subtree of Another Tree, LC 617 Merge Two Binary Trees.

> **Mental hook:** *"Two trees → two parameters → null-check the pair first."*

---

## 🎯 Decision Framework — Pick the Right Pattern

The 4-question funnel (full version with worked examples in deep-dive):

```
Q1: Are there TWO trees involved? ────── yes ──► Pattern D (Two-Tree Parallel)
        │
        no
        ▼
Q2: Does the problem mention "level", "depth K", "row", "shortest path"?
        │
        yes ──► Pattern C (BFS Level-Order)
        │
        no
        ▼
Q3: Does each node need info from ROOT-to-HERE (depth, running sum, max-on-path)?
        │
        yes ──► Pattern A (Top-Down DFS)
        │
        no
        ▼
Q4: Does the answer depend on subtree info COMBINED from both children?
        │
        yes ──► Pattern B (Bottom-Up — Pattern 7)
```

### Keyword signals

| Keyword in problem | Pattern |
| --- | --- |
| "level", "row", "depth K", "leftmost/rightmost-in-row" | **C (BFS)** |
| "path from root to leaf", "good nodes", "valid path with constraint" | **A (Top-Down)** |
| "max/longest path anywhere", "subtree property", "diameter" | **B (Bottom-Up — Pattern 7)** |
| "same tree", "symmetric", "merge two trees", "subtree of" | **D (Two-Tree)** |
| "all paths" (collect them) | **A (Top-Down) + Backtracking** |
| "lowest common ancestor" | Special: **overloaded-return DFS** (see LCA sub-pattern below) |

> **Diagnostic cheat code:** if the answer might live in *any subtree*, not just at the root → almost always Pattern B.

---

## 🧩 Common Sub-Patterns

Each sub-pattern is a recurring problem shape worth memorizing.

---

### Sub-Pattern 1: Diameter / Max Path Sum (Pattern B variants)

> Find the longest path **anywhere** in the tree. Always Pattern B with `best = max(best, leftContribution + rightContribution)`.

```java
class Solution {
    private int diameter = 0;

    public int diameterOfBinaryTree(TreeNode root) {
        diameter = 0;
        depth(root);
        return diameter;
    }

    private int depth(TreeNode root) {
        if (root == null) {
            return 0;
        }
        int dl = depth(root.left);
        int dr = depth(root.right);
        diameter = Math.max(diameter, dl + dr);
        return 1 + Math.max(dl, dr);
    }
}
```

**Variants:** LC 124 Max Path Sum uses `Math.max(0, x)` on each child's gain (negative-clipping); returns `node.val + max(left, right)` to parent.

**🏷️ Example problems:** LC 543 Diameter, LC 124 Max Path Sum, LC 687 Longest Univalue Path, LC 1372 Longest ZigZag Path.

---

### Sub-Pattern 2: Path Collection — Backtracking + Snapshot (Pattern A)

> Collect every root-to-leaf path satisfying a condition. The three-bug compound that bites here: forgetting snapshot, reassigning the path list, missing the undo.

```java
public List<List<Integer>> pathSum(TreeNode root, int targetSum) {
    List<List<Integer>> result = new ArrayList<>();
    List<Integer> path = new ArrayList<>();
    dfs(root, targetSum, path, result);
    return result;
}

private void dfs(TreeNode root, int remaining,
                 List<Integer> path, List<List<Integer>> result) {
    if (root == null) {
        return;
    }
    path.add(root.val);
    if (root.left == null && root.right == null && remaining == root.val) {
        result.add(new ArrayList<>(path));
    }
    dfs(root.left, remaining - root.val, path, result);
    dfs(root.right, remaining - root.val, path, result);
    path.remove(path.size() - 1);
}
```

**The three rules — never violate:**

1. `result.add(new ArrayList<>(path))` — **always snapshot** when storing
2. `path.remove(path.size() - 1)` — **always undo** unconditionally at the end
3. **Never reassign** `path = new ArrayList<>()` — local rebind, caller unaffected

**🏷️ Example problems:** LC 113 Path Sum II, LC 257 Binary Tree Paths, LC 437 Path Sum III (with prefix sum), LC 988 Smallest String Starting from Leaf.

---

### Sub-Pattern 3: Lowest Common Ancestor — Overloaded Return ⭐

> The recursive return carries **four meanings**: `null` (neither found below) / `p` (only p below) / `q` (only q below) / **non-null & not p & not q** (this IS the LCA). The function "knows" by combining its two children's returns.

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) {
        return root;
    }
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) {
        return root;
    }
    return left != null ? left : right;
}
```

**Critical clarifications:**

| Check | Why it's needed even when p, q guaranteed present |
| --- | --- |
| `root == null` | Traversal boundary — leaves recurse into `null` children |
| `left != null && right != null` | "Found one target in each subtree" → THIS node is the LCA |
| `left != null ? left : right` | "Only one target found below" → pass it up unchanged |
| `root == p` (reference, not `root.val == p.val`) | Compare by **reference** — value compare breaks on duplicate values |

**🏷️ Example problems:** LC 236 LCA Binary Tree, LC 235 LCA BST (use BST property — O(log n)), LC 1644 LCA where nodes might not exist, LC 1650 LCA with parent pointers.

> **Mental hook:** *"Can the answer ride on the recursive return value itself?"* — if yes (LC 236), use overloaded return. If no (LC 543/124), use Pattern B with instance field.

---

### Sub-Pattern 4: Level-Order Variants (Pattern C)

The base BFS template, with one tweak per problem:

| Problem | Tweak inside the level loop |
| --- | --- |
| LC 102 Level Order | Collect every node val into `level` list |
| LC 107 Bottom-Up Level Order | Same as 102, then `Collections.reverse(result)` (or `result.add(0, level)`) |
| LC 199 Right Side View | `if (i == size - 1) result.add(node.val)` |
| LC 515 Largest in Each Row | Track `max` per level |
| LC 637 Average of Levels | Track `sum` per level, divide by `size` |
| LC 116 / 117 Next Right Pointers | Wire `prev.next = node` while draining each level |
| LC 314 Vertical Order Traversal | Pair node with col index; use `TreeMap<Integer, List<Integer>>` |

---

### Sub-Pattern 5: BST Validate — Min/Max Bounds Passed Down (Pattern A)

> A BST is valid iff every node's value is strictly between the min/max bounds of its allowed range. Pass bounds *down*, not just `parent.val` — a value can violate an ancestor's bound several levels up.

```java
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode root, long min, long max) {
    if (root == null) {
        return true;
    }
    if (root.val <= min || root.val >= max) {
        return false;
    }
    return validate(root.left, min, root.val)
        && validate(root.right, root.val, max);
}
```

> **The bug everyone writes first:** comparing only against `parent.val`. Wrong — a right descendant can still violate the *grandparent's* upper bound.

**🏷️ Example problems:** LC 98 Validate BST, LC 230 Kth Smallest in BST (inorder traversal), LC 235 LCA BST.

---

### Sub-Pattern 6: Construct from Traversals (LC 105, 106)

> Inorder + (Preorder OR Postorder) uniquely identifies a binary tree. The root is the **first** preorder node (or **last** postorder); use a HashMap to find its index in inorder for O(n) construction.

```java
private Map<Integer, Integer> inorderIndex;
private int preorderIdx;

public TreeNode buildTree(int[] preorder, int[] inorder) {
    inorderIndex = new HashMap<>();
    for (int i = 0; i < inorder.length; i++) {
        inorderIndex.put(inorder[i], i);
    }
    preorderIdx = 0;
    return build(preorder, 0, inorder.length - 1);
}

private TreeNode build(int[] preorder, int left, int right) {
    if (left > right) {
        return null;
    }
    int rootVal = preorder[preorderIdx];
    preorderIdx++;
    TreeNode root = new TreeNode(rootVal);
    int mid = inorderIndex.get(rootVal);
    root.left = build(preorder, left, mid - 1);
    root.right = build(preorder, mid + 1, right);
    return root;
}
```

**🏷️ Example problems:** LC 105 Build from Preorder + Inorder, LC 106 Build from Postorder + Inorder (recurse RIGHT before LEFT, decrement `postorderIdx`).

---

### Sub-Pattern 7: Serialize / Deserialize (LC 297)

> Preorder traversal with `"#"` (or `null`) as the null sentinel — uniquely captures the tree structure.

```java
// Serialize
public String serialize(TreeNode root) {
    StringBuilder sb = new StringBuilder();
    buildString(root, sb);
    return sb.toString();
}

private void buildString(TreeNode root, StringBuilder sb) {
    if (root == null) {
        sb.append("#,");
        return;
    }
    sb.append(root.val).append(",");
    buildString(root.left, sb);
    buildString(root.right, sb);
}

// Deserialize
public TreeNode deserialize(String data) {
    Queue<String> tokens = new ArrayDeque<>(Arrays.asList(data.split(",")));
    return buildTree(tokens);
}

private TreeNode buildTree(Queue<String> tokens) {
    String token = tokens.poll();
    if (token.equals("#")) {
        return null;
    }
    TreeNode root = new TreeNode(Integer.parseInt(token));
    root.left = buildTree(tokens);
    root.right = buildTree(tokens);
    return root;
}
```

**🏷️ Example problems:** LC 297 Serialize/Deserialize Binary Tree, LC 449 Serialize/Deserialize BST.

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

**1. LC 543 — Primitive `max` as parameter never updates.**

```java
// ❌ Returns 0 always — primitive copied per frame
private int depth(TreeNode root, int max) {
    max = dl + dr;
    return 1 + Math.max(dl, dr);
}

// ✅ Instance field on heap — shared across frames
private int max = 0;
private int depth(TreeNode root) {
    max = Math.max(max, dl + dr);
    return 1 + Math.max(dl, dr);
}
```

Cross-reference: `recursion-fundamentals.md` → Pattern 7 + Bug 10.

---

**2. LC 113 — Three-bug compound on path collection.**

```java
// ❌ All three at once
result.add(path);                    // stored reference, mutated later
path = new ArrayList<>();            // local rebind, caller still holds original
// (no path.remove() at end)         // missing undo → state leaks to sibling

// ✅ Snapshot + never reassign + undo unconditionally
result.add(new ArrayList<>(path));
// no reassignment of path
path.remove(path.size() - 1);
```

---

**3. LC 124 — Forgetting to clip negative gains.**

```java
// ❌ A negative child can drag the global down
int left = helper(root.left);
int right = helper(root.right);

// ✅ Skip a child if it would hurt
int left = Math.max(0, helper(root.left));
int right = Math.max(0, helper(root.right));
```

Cross-reference: `Reference/code-style-for-dsa-reference.md` → Recipe 1.

---

**4. LC 236 — Reference equality, not value equality.**

```java
// ❌ Breaks on duplicate values
if (root.val == p.val || root.val == q.val) { ... }

// ✅ Compare references — LeetCode guarantees p and q are actual nodes
if (root == p || root == q) { ... }
```

---

**5. LC 116 vs LC 117 — Don't assume perfect tree on 117.**

LC 116 (perfect binary tree) — `node.left.next = node.right` works because both children always exist. LC 117 (any binary tree) — must walk the next-level chain to find the first non-null sibling.

---

**6. `static` fields on LeetCode → state leaks across test cases.**

```java
// ❌ static persists across test cases — wrong answer on 2nd test
private static int count = 0;

// ✅ instance field + reset at top of public method
private int count = 0;
public int solve(TreeNode root) {
    count = 0;
    ...
}
```

Cross-reference: `recursion-fundamentals.md` → Bug 5, `code-style-for-dsa-reference.md` → Recipe 13.

---

**7. Inorder of BST is sorted — don't sort it again.**

A BST's inorder traversal is monotonically increasing. For LC 230 (kth smallest), use inorder; for LC 98 (validate), check each value > previous-inorder.

---

**8. BFS without `int size = queue.size()` loses level boundaries.**

```java
// ❌ Mixes levels into one big bag
while (!queue.isEmpty()) {
    TreeNode node = queue.poll();
    ...
}

// ✅ Freeze the level size first, then drain only that many
while (!queue.isEmpty()) {
    int size = queue.size();
    for (int i = 0; i < size; i++) { ... }
}
```

---

**9. Recursion depth on skewed trees.**

A worst-case skewed tree (essentially a linked list) means recursion depth = n. For n = 100,000+ you'll hit `StackOverflowError`. Iterative BFS or explicit stack DFS sidesteps this.

---

**10. `null` traversal-boundary vs `null` "no LCA found".**

In LC 236, `root == null` is the **traversal boundary** (leaf children are null). `left == null` after recursion means **"no target found in left subtree"** — completely different meaning. Both are load-bearing, not defensive. Full table in `DeepDive/trees-fundamentals.md` → LC 236 callout.

---

## ⚡ Quick Cheat Sheet

| If you need... | Use... |
| --- | --- |
| Process level by level | **Pattern C** (BFS + `int size = queue.size()`) |
| Longest/best path anywhere in tree | **Pattern B** + instance field |
| Each node's relationship to root | **Pattern A** (info passed down via parameter) |
| Compare or merge two trees | **Pattern D** (two-tree parallel) |
| Find LCA | Overloaded-return DFS (LC 236 shape) |
| Validate BST | Pattern A with min/max bounds |
| Sorted output from BST | Inorder traversal |
| Collect every path matching X | Pattern A + backtracking with **snapshot + unconditional undo** |
| Serialize tree | Preorder DFS with `#` sentinel |
| Rebuild tree from traversals | Preorder/Postorder gives root; inorder HashMap gives split point |
| Iterative inorder | Stack + `while (curr != null \|\| !stack.isEmpty())` |
| Top-3 things to memorize | Pattern B template, BFS template, LCA overloaded return |

---

## 🧾 TL;DR — One-Page Summary

1. **`null` check on line 1** of every tree recursion — non-negotiable
2. **Four patterns cover ~95%:** Top-Down (info down), Bottom-Up/Pattern 7 (info up + instance field), BFS Level-Order, Two-Tree Parallel
3. **Pattern B (Bottom-Up)** is the most-tested shape — diameter, max path sum, longest univalue, balanced check
4. **Pattern 7 mantra:** *"return what the parent needs, mutate what the whole tree needs"*
5. **LCA (LC 236)** uses overloaded return — `null` / `p` / `q` / LCA — not a global flag
6. **Compare by reference, not value** when LeetCode hands you actual nodes
7. **`int size = queue.size()`** is the level boundary trick — freeze before draining
8. **Backtracking on trees:** snapshot when storing, undo unconditionally, never reassign the path list
9. **Negative-clipping** (`Math.max(0, x)`) lets LC 124 skip "harmful" subtrees
10. **No `static` fields** for problem state — instance field + reset at top of entry method
11. **Inorder of BST = sorted** — exploits the BST invariant for O(n) sorted output

> **Tree problems are mostly recursion problems wearing a tree-shaped hat.** Master Pattern 7 in the recursion notes and 60% of tree problems collapse into routine work.

---

## 🔹 Cross-References

| Topic | File |
| --- | --- |
| Full tree deep-dive (decision framework, ASCII trees) | `DeepDive/trees-fundamentals.md` |
| Pattern 7 — Two-Purpose Recursion (the engine of Pattern B) | `DeepDive/recursion-fundamentals.md` → Pattern 7 |
| Stack vs Heap — why instance field works | `DeepDive/recursion-fundamentals.md` → 🧬 Stack vs Heap |
| LC 124 brute-to-optimal walkthrough | `Patterns/max-path-sum-binary-tree-problem.md` |
| Negative-clipping, early-return, magic-number recipes | `Reference/code-style-for-dsa-reference.md` |
| HashMap / Set fundamentals for BST adjacents | `Reference/hashmap-section-updated.md`, `Reference/set-section-updated.md` |
