# Trees & BFS/DFS — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to instantly recognize which tree pattern a problem needs. Trees are the **#1 most common interview topic** at Salesforce and most tech companies. This file connects patterns to problems.

---

## 🎯 Why You're Reading This

You already know what DFS and BFS are. What you need is: **"I see this tree problem — do I use top-down DFS, bottom-up DFS, BFS, or something else?"** This file builds that decision instinct.

---

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `node.left` / `node.right` | Access children | All DFS patterns |
| `node.val` | Access node value | All patterns |
| `Queue<TreeNode> queue = new ArrayDeque<>()` | BFS queue (ArrayDeque faster than LinkedList) | Pattern 3 |
| `queue.offer(node)` / `queue.poll()` | Add/remove from BFS queue — O(1) | Pattern 3 |
| `queue.size()` | Snapshot level size before inner loop | Pattern 3 |
| `new ArrayList<>()` / `list.add(element)` | Build level lists or result lists | Patterns 1, 3 |
| `Collections.reverse(list)` | Reverse a level (zigzag traversal) | Pattern 3 |
| `Math.max(a, b)` | Compare heights, diameters, path sums | Patterns 1, 2 |

> **Full reference:** `../Reference/trees-reference.md`, `../Reference/arraydeque-and-queue-reference.md`

---

## 🧠 The Mental Model — Top-Down vs Bottom-Up vs BFS

Every tree problem falls into one of three families:

```
Tree problem
│
├── "Compute something from ROOT down to leaves"
│   └── Top-Down DFS (Pattern 1) — pass info DOWN via parameters
│       Examples: "root-to-leaf path sum", "validate BST"
│
├── "Compute something from LEAVES up to root"
│   └── Bottom-Up DFS (Pattern 2) — return info UP via return values
│       Examples: "height", "diameter", "max path sum"
│
└── "Process level by level"
    └── BFS (Pattern 3) — queue, process one level at a time
        Examples: "level order traversal", "right side view"
```

### 🎨 Visual — The Two DFS Directions

```
        1                   TOP-DOWN               BOTTOM-UP
       / \              (info flows DOWN ↓)     (info flows UP ↑)
      2   3
     / \                 "Am I valid?"           "What's my height?"
    4   5                 passes bounds           children answer first
                         down to children         parent combines answers

  Top-Down:              Bottom-Up:
  solve(node, info)      int solve(node)
    │                        │
    ├─ USE info here         ├─ left = solve(left)
    ├─ solve(left, info')    ├─ right = solve(right)
    └─ solve(right, info')   ├─ USE left, right here
                             └─ return combined result
```

**KEY INVARIANT:** Top-down = "I tell my children what they need to know." Bottom-up = "My children tell me what I need to know." Pick wrong and you'll fight the recursion instead of riding it.

### The Quick Decision:

| Question | If YES → | If NO → |
| --- | --- | --- |
| "Does the answer need info from the parent (bounds, path sum)?" | Top-Down | → next question |
| "Does the answer combine results from left and right subtrees?" | Bottom-Up | → next question |
| "Does the answer depend on level / layer order?" | BFS | → probably Top-Down or Bottom-Up |

---

## 🧭 Pattern 1: Top-Down DFS — Pass Info Down ⭐

**What this solves:** Problems where the answer at a node depends on information coming from the root (a running sum, valid range, or accumulated path). The parent tells each child something it needs to know before the child can compute its answer.

**Recognition cues — reach for this when:**
- "Root-to-leaf path sum"
- "Validate BST" (pass min/max bounds down)
- "All root-to-leaf paths"
- Any problem where the parent tells the child something

**Brute force:** For each node, recompute all necessary context from scratch by re-traversing from the root. O(n²) time for a balanced tree, O(n²) for skewed — every node re-walks its path.

**Key insight:** Pass context as a parameter. The parent computes the child's version of the context in O(1) and passes it down — each node is visited exactly once, reducing total cost to O(n).

**The template:**

```java
// Top-Down: info flows DOWN through parameters
public void solve(TreeNode node, InfoType info) {
    if (node == null) {
        return;
    }

    // USE info at this node
    // (check condition, add to path, etc.)

    // PASS modified info to children
    solve(node.left, modifiedInfo);
    solve(node.right, modifiedInfo);
}
```

### Path Sum (LC 112):

**Steps in plain English:**

1. **Pass remaining sum down** — subtract current node's value.
2. **At leaf** — check if remaining sum is 0.
3. **At non-leaf** — recurse left and right.

```java
public boolean hasPathSum(TreeNode root, int targetSum) {
    if (root == null) {
        return false;
    }

    // At leaf: check if remaining sum matches
    if (root.left == null && root.right == null) {
        return targetSum == root.val;
    }

    // Pass reduced target to children
    return hasPathSum(root.left, targetSum - root.val)
        || hasPathSum(root.right, targetSum - root.val);
}
```

### Validate BST (LC 98):

**The trick:** Pass valid range `(min, max)` down. Each node must be within its range.

```java
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    if (node == null) {
        return true;
    }
    if (node.val <= min || node.val >= max) {
        return false;
    }
    // Left child must be in (min, node.val)
    // Right child must be in (node.val, max)
    return validate(node.left, min, node.val)
        && validate(node.right, node.val, max);
}
```

**Complexity (optimal):** O(n) time, O(h) space — visit every node once; recursion stack depth = tree height h (O(log n) balanced, O(n) skewed).

**🏷️ Problems:** LC 112 (Path Sum), LC 98 (Validate BST), LC 257 (Binary Tree Paths — collect all root-to-leaf), LC 129 (Sum Root to Leaf Numbers).

---

## 🧭 Pattern 2: Bottom-Up DFS — Return Info Up ⭐

**What this solves:** Problems where the answer at a node requires combining results from both left and right subtrees — height, diameter, balance, maximum path. The leaves compute their answers first and pass them upward.

**Recognition cues — reach for this when:**
- "Height / depth of tree"
- "Diameter of tree"
- "Is tree balanced?"
- "Maximum path sum"
- Any problem where you need info from BOTH subtrees to compute the answer at a node

**Brute force:** For each node, call a separate `height()` function on both subtrees to get their heights. O(n²) time — each node triggers two O(n) sub-traversals.

**Key insight:** Ask children first via recursion, then combine at the parent. Each node receives its subtrees' answers as return values — no re-traversal needed. Every node is visited exactly once: O(n).

**The template:**

```java
// Bottom-Up: info flows UP through return values
public int solve(TreeNode node) {
    if (node == null) {
        return baseValue;
    }

    // ASK children first
    int left = solve(node.left);
    int right = solve(node.right);

    // COMBINE results at this node
    return combine(node.val, left, right);
}
```

### Height of Tree (LC 104):

```java
public int maxDepth(TreeNode root) {
    if (root == null) {
        return 0;
    }
    int left = maxDepth(root.left);
    int right = maxDepth(root.right);
    return 1 + Math.max(left, right);
}
```

### Diameter (LC 543) — The Dual-Purpose Pattern:

**The trick:** The function RETURNS height (for parent to use), but UPDATES a global variable with the diameter (the actual answer). The diameter at any node = `leftHeight + rightHeight`.

```java
private int maxDiameter = 0;

public int diameterOfBinaryTree(TreeNode root) {
    maxDiameter = 0;
    height(root);
    return maxDiameter;
}

private int height(TreeNode node) {
    if (node == null) {
        return 0;
    }
    int left = height(node.left);
    int right = height(node.right);

    // Side effect: update diameter
    maxDiameter = Math.max(maxDiameter, left + right);

    // Return: height (for parent)
    return 1 + Math.max(left, right);
}
```

### Is Balanced (LC 110):

Return `-1` to signal "unbalanced." Otherwise return height.

```java
public boolean isBalanced(TreeNode root) {
    return checkHeight(root) != -1;
}

private int checkHeight(TreeNode node) {
    if (node == null) {
        return 0;
    }
    int left = checkHeight(node.left);
    if (left == -1) {
        return -1;
    }
    int right = checkHeight(node.right);
    if (right == -1) {
        return -1;
    }
    if (Math.abs(left - right) > 1) {
        return -1;
    }
    return 1 + Math.max(left, right);
}
```

**Complexity (optimal):** O(n) time, O(h) space — single traversal; recursion stack = tree height.

**🏷️ Problems:** LC 104 (Max Depth), LC 543 (Diameter), LC 110 (Balanced Tree), LC 124 (Max Path Sum — advanced dual-purpose), LC 226 (Invert Tree).

---

## 🧭 Pattern 3: BFS — Level Order Traversal ⭐

**What this solves:** Problems where the answer depends on which level a node is at — level order output, right side view, averages per level, minimum depth, zigzag traversal. DFS visits nodes depth-first and loses level structure; BFS preserves it naturally.

**Recognition cues — reach for this when:**
- "Level order traversal"
- "Right side view"
- "Average of levels"
- "Minimum depth" (BFS finds it FIRST — faster than DFS)
- "Zigzag level order"
- Any problem mentioning "level" or "layer"

**Brute force:** Run DFS and track depth as a parameter; group nodes by depth into a map. O(n) time, O(n) space — works but loses the natural level-at-a-time processing order.

**Key insight:** A queue processes nodes in FIFO order. Snapshot `queue.size()` before the inner loop — this is exactly how many nodes are in the current level. Process that many, then the queue contains only the next level.

**The template:**

**Steps in plain English:**

1. **Initialize queue** with root.
2. **Process level by level** — `int size = queue.size()` tells you how many nodes are in this level.
3. **For each node in the level** — poll, process, offer children.

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) {
        return result;
    }

    // Step 1 — initialize queue
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        // Step 2 — process one level
        int size = queue.size();
        List<Integer> level = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            // Step 3 — poll, process, offer children
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

### Right Side View (LC 199):

Same BFS — just take the **last** element of each level.

```java
public List<Integer> rightSideView(TreeNode root) {
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

**Complexity (optimal):** O(n) time, O(w) space — where w = max width of the tree (worst case O(n) for a perfect binary tree's last level).

**🏷️ Problems:** LC 102 (Level Order), LC 199 (Right Side View), LC 637 (Average of Levels), LC 103 (Zigzag Level Order), LC 111 (Minimum Depth — BFS is optimal).

---

## 🧭 Pattern 4: BST Property — Inorder = Sorted

**What this solves:** Problems on Binary Search Trees where the sorted ordering of values matters — finding the Kth smallest, validating structure, or converting to/from sorted arrays. The BST invariant gives you a sorted sequence for free via inorder traversal.

**Recognition cues — reach for this when:**
- "Kth smallest in BST"
- "Validate BST" (alternative to Pattern 1)
- "Convert BST to sorted list"
- Any BST problem where sorted order matters

**Brute force:** Collect all node values into a list via any traversal, sort the list, return the Kth element. O(n log n) time, O(n) space.

**Key insight:** Inorder traversal (left → root → right) of a BST visits nodes in ascending order — a free sorted sequence without sorting. The Kth node visited is the Kth smallest. O(n) time, O(h) space.

**The key insight:** Inorder traversal (left → root → right) of a BST visits nodes in **ascending order**. So "Kth smallest" = "the Kth node visited during inorder."

```java
private int count = 0;
private int result = 0;

public int kthSmallest(TreeNode root, int k) {
    count = 0;
    result = 0;
    inorder(root, k);
    return result;
}

private void inorder(TreeNode node, int k) {
    if (node == null) {
        return;
    }
    inorder(node.left, k);
    count++;
    if (count == k) {
        result = node.val;
        return;
    }
    inorder(node.right, k);
}
```

**Complexity (optimal):** O(k) time (early exit after k nodes), O(h) space — recursion stack = tree height.

**🏷️ Problems:** LC 230 (Kth Smallest in BST), LC 98 (Validate BST — inorder approach), LC 108 (Sorted Array to BST), LC 235 (LCA of BST — use BST property).

---

## 🧭 Pattern 5: Lowest Common Ancestor (LCA)

**What this solves:** Finding the deepest node that is an ancestor of two given nodes. Used directly in LCA problems and as a sub-step in distance-between-nodes problems.

**Recognition cues — reach for this when:**
- "Find lowest common ancestor"
- "Distance between two nodes" (LCA + depth computation)

**Brute force:** Record the root-to-p path and root-to-q path in two lists, then find the last common node. O(n) time, O(n) space.

**Key insight:** Bottom-up DFS returns a node when it finds p or q. If both left and right subtrees return non-null, the current node is the LCA — p and q split here. Otherwise bubble up whichever side found something.

**The elegant bottom-up approach:**

**Steps in plain English:**

1. **Base case** — if node is null, or node IS p or q, return the node.
2. **Recurse** — search left and right.
3. **Combine** — if both sides found something, THIS node is the LCA. Otherwise return whichever side found something.

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    // Step 1 — base case
    if (root == null || root == p || root == q) {
        return root;
    }

    // Step 2 — search both subtrees
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);

    // Step 3 — combine
    if (left != null && right != null) {
        return root;
    }
    return (left != null) ? left : right;
}
```

**BST variant (LC 235):** Use the BST property — if both p and q are smaller, go left. Both larger, go right. Split point = LCA.

```java
public TreeNode lcaBST(TreeNode root, TreeNode p, TreeNode q) {
    while (root != null) {
        if (p.val < root.val && q.val < root.val) {
            root = root.left;
        } else if (p.val > root.val && q.val > root.val) {
            root = root.right;
        } else {
            return root;
        }
    }
    return null;
}
```

**Complexity (optimal):** O(n) time, O(h) space — single traversal; recursion stack = tree height.

**🏷️ Problems:** LC 236 (LCA of Binary Tree), LC 235 (LCA of BST).

---

## 🔬 Canonical Problem — LC 543: Diameter of Binary Tree

> **Problem:** Given the root of a binary tree, return the length of the diameter (longest path between any two nodes, measured in edges).

> **Brute force:** For each node, compute `height(left) + height(right)` where `height()` is a separate O(n) traversal. Take the maximum across all nodes. O(n²) time, O(h) space.
> **Key insight:** The function that computes height already visits every node — attach a diameter update as a side effect. One traversal computes both height (for the parent) and diameter (the actual answer). O(n) total.

### Step 1 — Read and identify triggers

"The problem asks for the **longest path** in a tree. The path goes through some node and uses its **left height + right height**. I need info from both subtrees → this is **Pattern 2: Bottom-Up DFS**."

### Step 2 — Identify the dual-purpose pattern

"The function needs to RETURN height (parent needs it) but COMPUTE diameter (which I'm actually asked for). This is the **dual-purpose** trick: return one thing, update a global variable with another."

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Base case** — null node has height 0.
2. **Get subtree heights** — recurse left and right.
3. **Update diameter** — `left + right` is the path through this node.
4. **Return height** — `1 + max(left, right)` for parent to use.

```java
private int maxDiameter = 0;

public int diameterOfBinaryTree(TreeNode root) {
    maxDiameter = 0;
    height(root);
    return maxDiameter;
}

private int height(TreeNode node) {
    if (node == null) {
        return 0;
    }

    int left = height(node.left);
    int right = height(node.right);

    // Diameter through this node = left height + right height
    maxDiameter = Math.max(maxDiameter, left + right);

    // Return height for parent
    return 1 + Math.max(left, right);
}
```

### Step 4 — Verify with example

```
        1
       / \
      2   3
     / \
    4   5

height(4) = 0+0 → dia=0, return 1
height(5) = 0+0 → dia=0, return 1
height(2) = left=1, right=1 → dia=max(0, 1+1)=2, return 1+max(1,1)=2
height(3) = 0+0 → dia=max(2,0)=2, return 1
height(1) = left=2, right=1 → dia=max(2, 2+1)=3, return 1+max(2,1)=3

Answer: 3 ✅ (path: 4 → 2 → 1 → 3)
```

### Complexity

- **Time:** O(n) — visit every node once
- **Space:** O(h) — recursion stack, where h = tree height (O(log n) balanced, O(n) skewed)

---

## ⚡ Problem Bank — Expanded

---

### LC 104: Maximum Depth of Binary Tree

> **Problem:** Return the maximum depth (number of nodes along the longest root-to-leaf path). Single node = depth 1.

> **Brute force:** BFS counting levels — not really a "brute force" since it's already O(n), but it requires O(w) queue space vs O(h) recursion stack for DFS.
> **Key insight:** Bottom-up: a leaf has height 1. Every parent's height is `1 + max(left, right)`. Recursion naturally computes this bottom-up in one pass.
> **Approach:** Bottom-up. Height = `1 + max(leftHeight, rightHeight)`. Null → 0.

```java
if (root == null) return 0;
// Height = 1 (this node) + the taller subtree
return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 226: Invert Binary Tree

> **Problem:** Mirror/flip a binary tree. Every left child becomes right child and vice versa, recursively.

> **Brute force:** BFS level by level, swap children at each node. O(n) time, O(w) space — both approaches are O(n), DFS just uses less space on a balanced tree.
> **Key insight:** Post-order (bottom-up) — invert the subtrees first, then swap. Or pre-order (top-down) — swap first, then recurse. Either works; the swap at each node is the only operation needed.
> **Approach:** Bottom-up. Swap left and right children at each node.

```java
// Swap children at this node, then recursively invert each subtree
TreeNode temp = root.left;
root.left = root.right;
root.right = temp;
invertTree(root.left);
invertTree(root.right);
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 543: Diameter of Binary Tree

> **Problem:** Find the length of the longest path between any two nodes (in edges). Path doesn't have to go through root. `[1,2,3,4,5]` → 3 (path 4→2→1→3).

> **Brute force:** For each node, compute `height(left) + height(right)` where `height()` is a separate O(n) traversal. Take the max across all n nodes. O(n²) time, O(h) space.
> **Key insight:** The height function already visits every node — attach a diameter update as a side effect. One pass computes both height (for the parent) and diameter (the actual answer) simultaneously.

> **Approach:** Bottom-up dual-purpose. Return height to parent. Side-effect: update global diameter = `leftHeight + rightHeight`.

```java
// Side effect: path through this node = left height + right height
maxDiameter = Math.max(maxDiameter, left + right);
// Return height (not diameter) — parent needs height to compute its own diameter
return 1 + Math.max(left, right);
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 110: Balanced Binary Tree

> **Problem:** Determine if a binary tree is height-balanced (left and right subtrees differ in height by at most 1, at every node).

> **Brute force:** For each node, call `height(left)` and `height(right)` separately, check `|left - right| > 1`. Recurse on children. O(n²) time — each node triggers two O(n) subtree scans.
> **Key insight:** Return `-1` as a sentinel to signal imbalance. One bottom-up pass simultaneously computes height and propagates the first imbalance upward — no re-traversal needed.

> **Approach:** Bottom-up. Return height normally, but return `-1` to signal "unbalanced." Early exit if child is -1.

```java
// -1 signals "unbalanced" — propagate failure up immediately
if (left == -1 || right == -1) return -1;
// Height difference > 1 at this node — tree is unbalanced
if (Math.abs(left - right) > 1) return -1;
return 1 + Math.max(left, right);
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 102: Binary Tree Level Order Traversal

> **Problem:** Return node values level by level, left to right. `[3,9,20,null,null,15,7]` → `[[3],[9,20],[15,7]]`.

> **Brute force:** DFS tracking depth as a parameter; store nodes in a `Map<Integer, List>` keyed by depth. O(n) time, O(n) space — works, but requires building the full map and ordering by depth key.
> **Key insight:** BFS naturally visits all level-k nodes before any level-(k+1) node. Snapshot `queue.size()` before the inner loop — that is exactly how many nodes belong to the current level.

> **Approach:** BFS. `size = queue.size()` tells you how many nodes are in the current level. Inner for-loop processes one level.

```java
// Snapshot size BEFORE processing — new children go into the NEXT level
int size = queue.size();
List<Integer> level = new ArrayList<>();
for (int i = 0; i < size; i++) {
    TreeNode node = queue.poll();
    level.add(node.val);
    // Enqueue children for the next level's processing
    if (node.left != null) queue.offer(node.left);
    if (node.right != null) queue.offer(node.right);
}
```

**Complexity (optimal):** O(n) time, O(w) space — where w = max width of the tree.

---

### LC 199: Binary Tree Right Side View

> **Problem:** Imagine standing on the right side of the tree. Return the node values you can see (rightmost node at each level). `[1,2,3,null,5,null,4]` → `[1,3,4]`.

> **Brute force:** DFS tracking depth; for each depth, overwrite a `Map<depth, value>` with the current node's value (last write wins = rightmost). O(n) time, O(h) space — works but requires DFS to visit all nodes before the rightmost is known.
> **Key insight:** BFS processes all nodes in a level before the next level. The last node polled in the inner for-loop is always the rightmost at that level — add it directly without any map.

> **Approach:** BFS. Same as level order, but only add the LAST node of each level to result.

```java
// Last node in this level = the one visible from the right side
if (i == size - 1) result.add(node.val);
```

**Complexity (optimal):** O(n) time, O(w) space.

---

### LC 98: Validate Binary Search Tree

> **Problem:** Determine if a binary tree is a valid BST (left < root < right, recursively for all subtrees).

> **Brute force:** Collect all values via inorder traversal into a list, then check if the list is strictly increasing. O(n) time, O(n) space — requires materializing the entire list.
> **Key insight:** Pass valid range `(min, max)` top-down. Every left child inherits the current node as its new upper bound; every right child inherits it as its new lower bound. O(n) time, O(h) space — no list needed.

> **Approach:** Top-down. Pass valid range `(min, max)` down. Each node must be strictly within bounds. Use `long` to handle edge values.

```java
// Node must be strictly within (min, max) — BST requires no duplicates in standard definition
if (node.val <= min || node.val >= max) return false;
// Left subtree inherits current node as upper bound; right inherits it as lower bound
return validate(node.left, min, node.val) && validate(node.right, node.val, max);
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 112: Path Sum

> **Problem:** Given a target sum, determine if there's a root-to-leaf path where node values sum to target. `[5,4,8,11,null,13,4,7,2,...], target=22` → true (5→4→11→2).

> **Brute force:** Enumerate all root-to-leaf paths, sum each path, compare to target. O(n) time, O(n) space — path storage makes it O(h) to O(n) extra space.
> **Key insight:** Pass the remaining target down instead of storing a path. At a leaf, remaining equals the leaf's value if this path sums to target. One pass, O(h) extra space.

> **Approach:** Top-down. Pass remaining target down. At leaf: check if `target == node.val`.

```java
// Leaf node: does the remaining target exactly equal this node's value?
if (root.left == null && root.right == null) return targetSum == root.val;
// Pass the reduced target down — subtract this node's contribution
return hasPathSum(root.left, targetSum - root.val)
    || hasPathSum(root.right, targetSum - root.val);
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 230: Kth Smallest Element in a BST

> **Problem:** Return the Kth smallest value in a BST. `[3,1,4,null,2], k=1` → 1.

> **Brute force:** Collect all BST values into a list via any traversal, sort the list, return the Kth element. O(n log n) time, O(n) space.
> **Key insight:** Inorder traversal visits a BST in ascending order — sorting is free. Count during traversal and stop at K. O(k) time (early exit), O(h) space.

> **Approach:** Inorder traversal of BST visits nodes in ascending order. Count during traversal, stop at K.

```java
// Visit left subtree first — BST inorder visits nodes in ascending order
inorder(node.left, k);
count++;
// Kth node visited = Kth smallest element
if (count == k) { result = node.val; return; }
inorder(node.right, k);
```

**Complexity (optimal):** O(k) time (early exit after k nodes), O(h) space.

---

### LC 236: Lowest Common Ancestor of a Binary Tree

> **Problem:** Given two nodes `p` and `q`, find their lowest common ancestor (the deepest node that has both as descendants). Both nodes are guaranteed to exist.

> **Brute force:** Record root-to-p and root-to-q paths in two lists, find the last common node. O(n) time, O(n) space — path storage required.
> **Key insight:** Bottom-up — return a node when you find p or q. When both subtrees return non-null, the current node is the first place p and q meet (the LCA). No path storage needed.

> **Approach:** Bottom-up. If a node IS p or q, return it. If left and right both return non-null, THIS node is the LCA.

```java
// Search both subtrees for p and q
TreeNode left = lowestCommonAncestor(root.left, p, q);
TreeNode right = lowestCommonAncestor(root.right, p, q);
// Both sides found something — p and q are in different subtrees, so this node is the LCA
if (left != null && right != null) return root;
// Only one side found something — bubble it up
return (left != null) ? left : right;
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 257: Binary Tree Paths

> **Problem:** Return all root-to-leaf paths as strings. Example: tree `[1,2,3,null,5]` → `["1->2->5", "1->3"]`.

> **Brute force:** BFS tracking full path strings in a parallel queue alongside nodes. O(n) time, O(n) space — string concatenation at every level creates many intermediate strings.
> **Key insight:** Top-down DFS passes the current path string as a parameter. At each leaf, the full path is already built. O(n) time, O(h) space for the call stack (excluding output).

> **Approach:** Top-down DFS. Pass the current path string down. At a leaf, add the path to results.

```java
void dfs(TreeNode node, String path, List<String> result) {
    // Leaf — this path is complete, add it to results
    if (node.left == null && node.right == null) { result.add(path); return; }
    // Append child's value to the path string and recurse deeper
    if (node.left != null) dfs(node.left, path + "->" + node.left.val, result);
    if (node.right != null) dfs(node.right, path + "->" + node.right.val, result);
}
```

**Complexity (optimal):** O(n) time, O(h) space (excluding output).

---

### LC 129: Sum Root to Leaf Numbers

> **Problem:** Each root-to-leaf path forms a number (e.g., 1→2→3 = 123). Return the sum of all such numbers. Example: tree `[1,2,3]` → `12 + 13 = 25`.

> **Brute force:** Enumerate all root-to-leaf paths, form each number from the digit sequence, sum them. O(n) time, O(n) space — storing all paths requires O(n) extra space.
> **Key insight:** Pass the running number as a parameter: `num * 10 + node.val`. At each leaf the number is already fully formed. Sum up leaf return values — no paths to store, O(h) extra space.

> **Approach:** Top-down DFS. Pass running number `num * 10 + node.val` down. At a leaf, return the number. Sum across all paths.

```java
int dfs(TreeNode node, int num) {
    if (node == null) return 0;
    // Build the number digit by digit: shift left and append current node's value
    num = num * 10 + node.val;
    // Leaf — this path's number is complete
    if (node.left == null && node.right == null) return num;
    // Sum the numbers formed by all root-to-leaf paths through this node
    return dfs(node.left, num) + dfs(node.right, num);
}
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 124: Binary Tree Maximum Path Sum

> **Problem:** A path can start and end at any node (doesn't have to pass through root). Find the maximum path sum. Nodes can have negative values. Example: tree `[-10,9,20,null,null,15,7]` → `42` (path `15→20→7`).

> **Brute force:** Enumerate all paths (every pair of nodes as start/end), compute path sums. O(n²) time — exponential combinations of paths in the general case.
> **Key insight:** The max path through any node = `left gain + node.val + right gain`, where gains are clamped to 0 (negative subtrees only hurt). Return the single-branch max upward so parents can extend the path in one direction.

> **Approach:** Bottom-up DFS with dual-purpose return. Return the max single-branch gain (for parent's use). Side-effect: update global max with `left + right + node.val` (path through this node). See `trees-and-bfs-dfs.md` Pattern 2.

```java
int maxSum = Integer.MIN_VALUE;
int dfs(TreeNode node) {
    if (node == null) return 0;
    // Clamp to 0: ignore negative subtrees — they'd only reduce the sum
    int left = Math.max(0, dfs(node.left));
    int right = Math.max(0, dfs(node.right));
    // Side effect: path through this node = left + node + right
    maxSum = Math.max(maxSum, left + right + node.val);
    // Return single-branch max gain — parent can only use ONE branch, not both
    return Math.max(left, right) + node.val;
}
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 103: Binary Tree Zigzag Level Order Traversal

> **Problem:** BFS level-by-level, but alternate left-to-right and right-to-left. Example: tree `[3,9,20,null,null,15,7]` → `[[3],[20,9],[15,7]]`.

> **Brute force:** Standard BFS, collect all levels normally, then manually reverse alternate levels at the end. O(n) time, O(n) space — the reversal is O(w) per level but conceptually a post-processing step.
> **Key insight:** Same BFS — call `Collections.reverse()` on odd-indexed levels before adding to result. Or use a `LinkedList` and `addFirst` on odd levels to build the reversed list in-place during traversal.

> **Approach:** Standard BFS level order. On even levels, add left→right. On odd levels, reverse the level list (or use `addFirst`).

```java
// Odd levels go right-to-left — just reverse the list after normal BFS collection
if (level % 2 == 1) Collections.reverse(levelList);
result.add(levelList);
level++;
```

**Complexity (optimal):** O(n) time, O(w) space.

---

### LC 637: Average of Levels in Binary Tree

> **Problem:** Return the average value of nodes on each level. Example: tree `[3,9,20,null,null,15,7]` → `[3.0, 14.5, 11.0]`.

> **Brute force:** DFS collecting all nodes per level into a `Map<Integer, List>`, then iterate the map to compute averages. O(n) time, O(n) space — requires materializing the full map.
> **Key insight:** BFS naturally groups nodes by level. Sum during the inner for-loop and divide by `size` immediately — no map needed, O(w) extra space.

> **Approach:** Standard BFS. At each level, sum all node values and divide by level size.

```java
// Use double for sum to avoid integer overflow on large trees
double sum = 0;
int size = queue.size();
for (int i = 0; i < size; i++) {
    TreeNode node = queue.poll();
    sum += node.val;
    if (node.left != null) queue.offer(node.left);
    if (node.right != null) queue.offer(node.right);
}
// Average = total value of this level / number of nodes in this level
result.add(sum / size);
```

**Complexity (optimal):** O(n) time, O(w) space.

---

### LC 111: Minimum Depth of Binary Tree

> **Problem:** Find the minimum depth (shortest path from root to any leaf). Example: tree `[3,9,20,null,null,15,7]` → `2` (path `3→9`).

> **Brute force:** DFS tracking depth; return the minimum depth seen at any leaf. O(n) time, O(h) space — must visit every node to guarantee finding the shallowest leaf.
> **Key insight:** BFS finds the shallowest leaf FIRST — return immediately when the first leaf is dequeued. No need to traverse the entire tree.

> **Approach:** BFS is best — return the level when you first hit a leaf. DFS works too but BFS guarantees you find the shallowest leaf first.

```java
int depth = 1;
while (!queue.isEmpty()) {
    int size = queue.size();
    for (int i = 0; i < size; i++) {
        TreeNode node = queue.poll();
        // BFS guarantees this is the shallowest leaf — return immediately
        if (node.left == null && node.right == null) return depth;
        if (node.left != null) queue.offer(node.left);
        if (node.right != null) queue.offer(node.right);
    }
    depth++;
}
```

**Complexity (optimal):** O(n) time, O(w) space — BFS may exit early for shallow answers but O(n) worst case.

---

### LC 108: Convert Sorted Array to Binary Search Tree

> **Problem:** Given a sorted array, convert it to a height-balanced BST. Example: `[-10,-3,0,5,9]` → a BST with root `0`.

> **Brute force:** Insert each element of the sorted array into a BST one by one. O(n log n) time average — but produces a skewed tree (right-only chain) because the array is sorted, not balanced.
> **Key insight:** Always pick the middle element as root — the left and right halves are equal-sized (±1), guaranteeing a height-balanced tree. Recurse on both halves. O(n) time.

> **Approach:** Recursion. Pick the middle element as root. Left half → left subtree. Right half → right subtree. Base case: `lo > hi → null`.

```java
TreeNode build(int[] nums, int lo, int hi) {
    if (lo > hi) return null;
    // Pick middle element as root — guarantees height-balanced tree
    int mid = lo + (hi - lo) / 2;
    TreeNode node = new TreeNode(nums[mid]);
    // Left half of sorted array becomes left subtree
    node.left = build(nums, lo, mid - 1);
    // Right half becomes right subtree
    node.right = build(nums, mid + 1, hi);
    return node;
}
```

**Complexity (optimal):** O(n) time, O(h) space.

---

### LC 235: LCA of a Binary Search Tree

> **Problem:** Find the lowest common ancestor in a **BST** (not generic binary tree). Example: BST, `p = 2, q = 8` → LCA = `6`.

> **Brute force:** Use the generic LCA algorithm (Pattern 5) — bottom-up DFS traversing the entire tree. O(n) time, O(h) space.
> **Key insight:** BST property eliminates half the tree at each step. If both p and q are smaller, LCA is in the left subtree; both larger, in the right. The first split point (or match) is the LCA. O(h) time — no full traversal needed.

> **Approach:** Use BST property. If both `p` and `q` are less than root → go left. Both greater → go right. Otherwise, root IS the LCA (split point). No need for full tree traversal — O(h) time.

```java
TreeNode node = root;
while (node != null) {
    // Both targets are smaller — LCA must be in the left subtree
    if (p.val < node.val && q.val < node.val) node = node.left;
    // Both targets are larger — LCA must be in the right subtree
    else if (p.val > node.val && q.val > node.val) node = node.right;
    // Targets split here (or one equals node) — this is the LCA
    else return node;
}
```

**Complexity (optimal):** O(h) time, O(1) space — iterative BST traversal, no recursion stack.

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty tree** (`root == null`) — return 0 / null / empty list
- **Single node** — height 1, diameter 0, level order `[[val]]`
- **Skewed tree** (all left or all right) — depth = n, acts like a linked list
- **Negative values in BST validation** — use `Long.MIN_VALUE`/`Long.MAX_VALUE`, not `Integer`

### Follow-up questions:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| Max Depth | "Iterative solution?" | BFS — depth = number of levels |
| Level Order | "Zigzag traversal?" | Same BFS, reverse every other level |
| Validate BST | "What about duplicate values?" | Depends on BST definition — ask interviewer. Usually strict: left < root < right |
| Diameter | "What if I want the actual path?" | Track parent pointers or find LCA of the two endpoints |
| Invert Tree | "In-place or new tree?" | Both work — in-place just swaps pointers |

### The #1 tree recursion bug:

**Forgetting to reset instance fields between LeetCode test cases.**

```java
// ❌ maxDiameter retains value from previous test case
private int maxDiameter = 0;
public int diameterOfBinaryTree(TreeNode root) {
    height(root);
    return maxDiameter;
}

// ✅ reset at the start of each call
public int diameterOfBinaryTree(TreeNode root) {
    maxDiameter = 0;
    height(root);
    return maxDiameter;
}
```

Full coverage in `DSA/Implementation/java-coding-traps.md` — Family 8 (Scope & State).

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

1. "Maximum depth of binary tree" → ___
2. "Level order traversal" → ___
3. "Validate BST" → ___
4. "Diameter of binary tree" → ___
5. "Right side view" → ___
6. "Kth smallest element in BST" → ___
7. "Path sum (root to leaf)" → ___
8. "Lowest common ancestor" → ___

**Answers:** 1. Bottom-Up, 2. BFS, 3. Top-Down (bounds) or Inorder, 4. Bottom-Up (dual-purpose), 5. BFS (last per level), 6. BST Inorder, 7. Top-Down (pass remaining sum), 8. Bottom-Up

**Part 2 — Write the Template (3 minutes)**

From memory, write the BFS level-order template. Include: queue init, while-not-empty, size-based inner loop, offer children.

**Part 3 — The Dual-Purpose Decision (3 minutes)**

For LC 543 (Diameter), explain in one sentence: what does the function RETURN vs what does it COMPUTE as a side effect? Why can't you just return the diameter directly?

**Answer:** Returns **height** (parent needs it to compute its own diameter). Computes **diameter** as a side effect (global variable). You can't return diameter directly because the parent node needs the HEIGHT of its subtrees, not their diameters.

**Scoring:** Part 1: 8/8 = ready. Part 2: template compiles in head = ready. Part 3: articulated the dual-purpose clearly = you own tree recursion.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Trees deep dive (mental models, all patterns) | `DSA/DeepDive/trees-fundamentals.md` |
| Trees reference (method syntax) | `DSA/Reference/trees-reference.md` |
| BFS/DFS templates reference | `DSA/Reference/bfs-dfs-templates-reference.md` |
| Graphs deep dive (BFS/DFS on graphs) | `DSA/DeepDive/graphs-fundamentals.md` |
| Recursion deep dive | `DSA/DeepDive/recursion-fundamentals.md` |
| Max Path Sum problem deep dive | `DSA/Patterns/max-path-sum-binary-tree-problem.md` |
| Java coding traps (instance field reset) | `DSA/Implementation/java-coding-traps.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Trees & BFS/DFS. 5 patterns: top-down DFS, bottom-up DFS (+ dual-purpose), BFS level-order, BST inorder, LCA. Canonical walkthrough (LC 543 Diameter), 10-problem bank, dual-purpose pattern explanation. |
| June 2026 | **Brute Force / Key Insight pass.** Added What this solves, Brute force, Key insight to all 5 pattern blocks and canonical section (LC 543). Added > Brute force, > Key insight to all 18 problem bank entries. Added Complexity (optimal) after every code block. |
