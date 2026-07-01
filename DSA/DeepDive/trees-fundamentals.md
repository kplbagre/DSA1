# Trees — Fundamentals (Deep Dive)

> A from-scratch guide to binary trees, traversals, recursion intuition, and BST — built for cracking DSA tree problems in interviews. Read top to bottom once. Use the **Reference** doc for daily revision.

---

## 🎯 Why You're Reading This (The Goal)

You will see ~3–6 tree problems in any interview loop. They look intimidating because:

- **Trees aren't given as input you can index into** — they arrive as a `TreeNode root`, and you must traverse them
- **Almost every tree solution is recursive** — and recursion is the single hardest mental model in DSA for most people
- **The line between "easy" and "trick" tree problems is thin** — Validate BST is rated Medium but trips most candidates the first time

By the end of this doc, you should be able to:

1. **Look at a `TreeNode root` and immediately picture the recursion** ("I traverse left, do work, traverse right")
2. **Recognize 4 patterns** that cover ~80% of tree interview questions
3. **Know which traversal (pre / in / post / level) fits which problem**
4. **Solve LC 100, 104, 226, 102, 98 from memory** — the foundational five

**Companion file:** `Reference/trees-reference.md` (will be created next) is the compact cheat sheet you live in during practice and revision.

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

Throughout this doc you will see problems suggested in 🧩 **Try these** callouts. Every problem is tagged so you know whether you should attempt it **right now** or **wait** until you've covered more material.

| Tag | Meaning | What you should do |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with the concepts covered up to this point | Open LeetCode, attempt without help, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs concepts from a later section in this same doc | Bookmark it. Come back when you finish the named section. |
| 🔴 **Reference Only** | Needs concepts beyond this doc (DP on trees, prefix sum on tree paths, graph conversion, BFS on grids, serialization, etc.) | **Read the problem statement and any solution discussion only for awareness.** Do **not** attempt cold — you'll burn an hour and learn the wrong lesson. We will return to these in their own deep-dive docs. |

> 💡 **Why this matters (Kapil's lesson, May 2026):** I previously suggested LC 124 Maximum Path Sum during the bottom-up DFS section. It looks like "just a tree problem," but it requires comfort with two-purpose recursion, negative-clipping intuition, and global-vs-return state — concepts that need their own practice ladder first. Attempting it cold cost an hour and felt demoralizing. The honest fix: **only attempt ✅ problems on the first pass; treat 🔴 as bedtime reading until prerequisites are in.**

---

## 🌲 What Is a Tree?

A **tree** is a hierarchical structure where each item has **one parent** (except the top one) and **zero or more children**. Think of a family tree — but with stricter rules: nobody has two parents.

```
              1                ← root (no parent)
            /   \
           2     3             ← children of 1
          / \     \
         4   5     6           ← leaves (no children)
```

The structure above has:
- A **root** node (1) — the entry point
- **Children** (2, 3 are children of 1)
- **Leaves** (4, 5, 6) — nodes with no children
- **Edges** — the lines connecting parent to child

Most DSA problems use a **binary tree** — every node has **at most 2 children**, conventionally called `left` and `right`.

> **Why "binary"?** With at most 2 children per node, you get clean recursive structure: every problem on a tree can be split into "left subtree problem" + "right subtree problem" + "current node work."

---

## 📖 Terminology (Memorize These)

| Term | Definition | Example (using tree above) |
| --- | --- | --- |
| **Root** | Top node, no parent | `1` |
| **Leaf** | Node with no children | `4`, `5`, `6` |
| **Parent** | The node above another | `2` is parent of `4` and `5` |
| **Child** | The node below another | `4`, `5` are children of `2` |
| **Sibling** | Same-parent nodes | `4` and `5` are siblings |
| **Ancestor** | Any node above on the path to root | `1` and `2` are ancestors of `4` |
| **Descendant** | Any node below | `4`, `5` are descendants of `2` |
| **Subtree** | A node + everything below it | The subtree rooted at `2` is `{2, 4, 5}` |
| **Depth** of a node | Edges from root to that node | `4` has depth 2 |
| **Height** of a node | Edges to the deepest leaf below it | Node `2` has height 1, root has height 2 |
| **Level** | All nodes at the same depth | Level 1 = `{2, 3}` |

> **Common confusion:** "Depth" measures from the **top down**, "height" measures from the **bottom up**. In LeetCode, "depth" usually means depth of the whole tree (= height of root).

### 🎨 Visual — Depth vs Height on the Same Tree

```
Same tree, two different measurements:

         (A)                      (A) depth=0,  height=2
        /   \
      (B)   (C)                   (B) depth=1,  height=1     (C) depth=1, height=0
      / \
    (D) (E)                       (D) depth=2,  height=0     (E) depth=2, height=0


DEPTH (measure from root DOWN):       HEIGHT (measure from leaves UP):

   (A) ━━━━━ 0 ━━━━━                     (A) ━━━━━ 2 ━━━━━
   (B) (C) ━ 1 ━                         (B)       1
   (D)(E) ━━ 2 ━                         (C)(D)(E) 0  ← all leaves height 0


   • Depth of A = 0 (A IS the root)         • Height of A = 2 (longest path
   • Depth of D = 2 (A → B → D)                                down to a leaf)
   • Height of tree = height of root        • Height of leaf = 0
     = max depth of any leaf                • Height of empty = -1 (some texts)


   Mnemonic: depth grows DOWN like roots into soil.
             height grows UP like a tree toward the sky.
```

---

## 🛠️ The `TreeNode` Class

LeetCode and most interviews give you this skeleton (you do **not** write it — it's pre-defined):

```java
public class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;

    TreeNode() {}
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}
```

What this means in your head:

- A `TreeNode` is **just an object** with a value and two pointers
- `node.left` and `node.right` are themselves `TreeNode` references — or `null` if no child exists
- `null` means **"no node here"** — checking `if (node == null)` is your bread-and-butter base case

**Building a small tree manually** (you'd do this only in test/debug — never in solutions):

```java
//      1
//     / \
//    2   3
TreeNode root = new TreeNode(1);
root.left = new TreeNode(2);
root.right = new TreeNode(3);
```

> **Mental shift from arrays/lists:** there's no `tree.get(i)` or `tree[i]`. The only way to reach a node is to follow `.left` and `.right` from the root. **All access is by traversal.**

---

## 🔨 Building the Tree — Input Formats

> **⬛ 90% of LeetCode problems hand you `TreeNode root` directly.** This section is for the other 10%: when raw input arrives and you must construct the tree yourself before any algorithm runs. Phase 1 (build the tree) must be complete before Phase 2 (run the algorithm) begins.

---

### Format A — Level-Order Array with Nulls (LeetCode Serialization Format)

This is the format LeetCode uses in its test cases: `[1,2,3,null,null,4,5]`. The array encodes the tree level by level, where `null` means "this child slot is empty." You almost never write the deserializer on LeetCode — the tree is handed to you — but understanding the format lets you trace through examples mentally, and LC 297 (Serialize/Deserialize Binary Tree) asks you to implement it explicitly.

### 🎨 Visual — Level-Order Array Encoding

```
Input array: [1, 2, 3, null, null, 4, 5]
Index:         0  1  2    3     4  5  6

Tree it encodes:
             1          ← index 0 (root)
           /   \
          2     3       ← indices 1, 2
         / \   / \
       null null 4  5   ← indices 3, 4, 5, 6

BFS queue contents during construction:
  Start:  queue = [1]
  Pop 1:  left=vals[1]=2 → enqueue 2;  right=vals[2]=3 → enqueue 3
  Pop 2:  left=vals[3]=null → skip;    right=vals[4]=null → skip
  Pop 3:  left=vals[5]=4 → enqueue 4;  right=vals[6]=5 → enqueue 5

KEY INVARIANT:
  Every non-null node in the queue is a parent waiting for two child slots.
  Null entries advance i but do NOT enter the queue — that's what keeps
  the indices in sync with the tree shape.
```

**Steps in plain English:**

1. **Guard against empty input** — if the array is null or the first element is `"null"`, return null immediately.
2. **Seed the root** — create a `TreeNode` from `vals[0]`, add it to a BFS queue.
3. **BFS loop** — dequeue a parent node; assign `vals[i]` as its left child (if not `"null"`) and `vals[i+1]` as its right child (if not `"null"`); enqueue non-null children; advance `i` by 2.
4. **Return root.**

```java
public TreeNode buildFromLevelOrder(String[] vals) {
    // Step 1 — guard
    if (vals == null || vals.length == 0 || vals[0].equals("null")) {
        return null;
    }
    // Step 2 — seed root
    TreeNode root = new TreeNode(Integer.parseInt(vals[0]));
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    int i = 1;
    // Step 3 — BFS loop: each iteration handles one parent's two child slots
    while (!queue.isEmpty() && i < vals.length) {
        TreeNode parent = queue.poll();
        // Left child slot
        if (!vals[i].equals("null")) {
            parent.left = new TreeNode(Integer.parseInt(vals[i]));
            queue.offer(parent.left);
        }
        i++;
        // Right child slot
        if (i < vals.length && !vals[i].equals("null")) {
            parent.right = new TreeNode(Integer.parseInt(vals[i]));
            queue.offer(parent.right);
        }
        i++;
    }
    // Step 4 — return root
    return root;
}
```

---

### Format B — Parent Array (Contest / Custom Input Format)

Given: `int n` (number of nodes with values `1..n`) and `int[] parent` where `parent[i]` is the parent of node `i`, and `parent[i] == -1` marks the root.

**Steps in plain English:**

1. **Pass 1 — create all nodes:** allocate `TreeNode[] nodes = new TreeNode[n + 1]` (1-indexed), instantiate a `TreeNode` at each index `1..n`.
2. **Pass 2 — wire parent → child:** for each node `i`, if `parent[i] == -1` it is the root; otherwise attach it as the left or right child of `nodes[parent[i]]`.
3. **Return the root node.**

```java
public TreeNode buildFromParentArray(int n, int[] parent) {
    // Step 1 — Pass 1: create all nodes (1-indexed; index 0 is unused)
    TreeNode[] nodes = new TreeNode[n + 1];
    for (int i = 1; i <= n; i++) {
        nodes[i] = new TreeNode(i);
    }
    TreeNode root = null;
    // Step 2 — Pass 2: wire parent → child relationships
    for (int i = 1; i <= n; i++) {
        if (parent[i] == -1) {
            // this node is the root
            root = nodes[i];
        } else {
            TreeNode p = nodes[parent[i]];
            if (p.left == null) {
                p.left = nodes[i];
            } else {
                p.right = nodes[i];
            }
        }
    }
    // Step 3 — return root
    return root;
}
```

> **⚠️ 1-indexed trap:** allocate `n + 1` slots (`new TreeNode[n + 1]`), loop `i = 1..n`, ignore index 0. This is the same trap as graph adjacency list construction — see **`DSA/DeepDive/graphs-fundamentals.md § 🔨 Building the Graph — Input Format Patterns`** → *"1-indexed trap callout"*.

---

### Format C — Edges Array (Rare — treat like a graph first)

When given `int[][] edges` where `edges[i] = [u, v]` means `u` is the parent of `v`: build an adjacency list (exactly like a graph), then run one BFS/DFS from the root to convert the adjacency list into `TreeNode` form. All edges-array construction patterns are in **`DSA/DeepDive/graphs-fundamentals.md § 🔨 Building the Graph`**.

---

> **⬛ Pre-flight: before any tree algorithm, answer these 2 questions:**
>
> 1. **Was the tree given to me as `TreeNode root`?** → skip Phase 1; go straight to the algorithm (DFS / BFS / recursion).
> 2. **Was raw input given (level-order array / parent array / edges)?** → build the `TreeNode` tree first using Format A or B above, THEN run the algorithm.

---

## 🧠 Recursion on Trees — The Mental Model (Most Important Section)

If you struggle with tree problems, the issue is almost always **recursion comprehension**, not trees themselves. Spend extra time here.

### The single big idea

> **Trust the recursion.** When you call `solve(root.left)`, **assume it correctly solves the left subtree** and **returns the right answer for that subtree**. Then combine that result with the right subtree's result and the current node's contribution.

This is sometimes called the **"recursive leap of faith"** — you don't trace the entire call stack in your head. You just trust that the recursive call works and write the **combining logic** at each level.

### A worked example: maximum depth of a binary tree (LC 104)

The depth of a tree is the longest path from the root to any leaf, counted in **nodes**.

```
        1
       / \
      2   3
     /
    4
```

Depth = 3 (path `1 → 2 → 4`).

**The recursive thought process:**

1. **What's the depth of an empty tree?** → 0. (Base case.)
2. **What's the depth of any non-empty tree?** → `1 + max(depth of left subtree, depth of right subtree)`.

That's it. Translate to code:

```java
public int maxDepth(TreeNode root) {
    if (root == null) {
        return 0;                                   // base case
    }
    int leftDepth = maxDepth(root.left);            // trust: returns left subtree's depth
    int rightDepth = maxDepth(root.right);          // trust: returns right subtree's depth
    return 1 + Math.max(leftDepth, rightDepth);     // combine
}
```

### The call stack — what's actually happening

When you call `maxDepth(root)`, Java pushes a stack frame. Inside, it calls `maxDepth(root.left)` — another frame. This continues until we hit `null`, where we return `0` and start unwinding.

Trace for the tree above:

```
maxDepth(1)
  ├─ maxDepth(2)
  │   ├─ maxDepth(4)
  │   │   ├─ maxDepth(null) → 0
  │   │   ├─ maxDepth(null) → 0
  │   │   └─ returns 1 + max(0, 0) = 1
  │   ├─ maxDepth(null) → 0
  │   └─ returns 1 + max(1, 0) = 2
  ├─ maxDepth(3)
  │   ├─ maxDepth(null) → 0
  │   ├─ maxDepth(null) → 0
  │   └─ returns 1 + max(0, 0) = 1
  └─ returns 1 + max(2, 1) = 3
```

Each call returns its subtree's answer. The **caller** combines them. **You only need to write the logic for one node** — Java's call stack handles the rest.

### 🎨 Visual — Call Stack Growing and Unwinding

```
The same trace, but drawn as the JVM's call stack — how frames are
PUSHED on the way down and POPPED on the way up.

Tree:               Step-by-step stack snapshots:

      (1)           t=1  PUSH maxDepth(1)         ┌────────────────┐
     /   \                                        │ maxDepth(1)    │
   (2)   (3)                                      └────────────────┘
   /
  (4)             t=2  PUSH maxDepth(2)           ┌────────────────┐
                                                  │ maxDepth(2)    │  ← top
                                                  │ maxDepth(1)    │
                                                  └────────────────┘

                  t=3  PUSH maxDepth(4)           ┌────────────────┐
                                                  │ maxDepth(4)    │  ← top
                                                  │ maxDepth(2)    │
                                                  │ maxDepth(1)    │
                                                  └────────────────┘

                  t=4  PUSH maxDepth(null)        ┌────────────────┐
                                                  │ maxDepth(null) │  returns 0
                                                  │ maxDepth(4)    │
                                                  │ maxDepth(2)    │
                                                  │ maxDepth(1)    │
                                                  └────────────────┘
                       ── POP, returns 0 ──

                  ...both nulls return 0, then maxDepth(4) returns 1...

                  t=8  POP maxDepth(4) → 1        ┌────────────────┐
                                                  │ maxDepth(2)    │  ← top
                                                  │ maxDepth(1)    │      (knows left=1)
                                                  └────────────────┘

                  ...right child null returns 0, maxDepth(2) returns 2...

                  t=N  POP maxDepth(2) → 2        ┌────────────────┐
                                                  │ maxDepth(1)    │  ← top
                                                  └────────────────┘      (knows left=2)

                  ...right side runs, maxDepth(3) returns 1...

                  t=last  POP maxDepth(1) → 3     ┌────────────────┐
                                                  │      (empty)   │
                                                  └────────────────┘
                                                       Done. Result = 3.


KEY INVARIANTS:

  1. Stack DEPTH at any time  =  current path-length from root.
     This is why an N-shaped (stick) tree blows the recursion stack
     for N ≈ 10,000 in Java — but a balanced tree of 1M nodes is fine
     (depth ≈ 20).

  2. Each frame REMEMBERS its local variables (leftDepth, etc.) until
     its child returns. The "trust the recursion" mental model maps
     directly onto this: when control returns to your frame, the
     subtree's answer is sitting in a local variable. You don't have
     to recompute or look anything up.

  3. The work happens DURING the POP, not during the PUSH (for
     POSTorder logic like maxDepth). For PREorder logic, the work
     happens BEFORE the recursive calls — i.e., during the PUSH.
```

### Base case + recursive case — the universal pattern

Every tree recursive function looks like:

**Steps in plain English:**

1. **Check the base case first** — if the node is `null`, there's nothing to do. Return a "neutral" value (`0`, `null`, `true`, `Integer.MIN_VALUE` — whatever doesn't affect the combine step).
2. **Solve the left subtree** — call yourself on `node.left`. Trust that this returns the correct answer for the left half.
3. **Solve the right subtree** — call yourself on `node.right`. Trust that this returns the correct answer for the right half.
4. **Combine the results** — do whatever work this node needs (sum, max, comparison, swap, etc.) using the current node's value plus the two subtree answers. Return the combined value to whoever called you.

```java
ReturnType solve(TreeNode node) {
    // Step 1 — base case
    if (node == null) {
        return baseValue;
    }

    // Step 2 — solve the left half (trust the recursion)
    ReturnType leftResult = solve(node.left);

    // Step 3 — solve the right half (trust the recursion)
    ReturnType rightResult = solve(node.right);

    // Step 4 — do work + return combined answer
    return combine(node.val, leftResult, rightResult);
}
```

Memorize this skeleton. **90% of tree problems fit this shape.**

> 🧩 **Try these (recursion warmup):**
> - ✅ LC 104 Maximum Depth — the literal warmup
> - ✅ LC 100 Same Tree — parallel recursion intro (covered in Pattern 4)
> - ✅ LC 226 Invert Binary Tree — postorder swap (full walkthrough below)
> - ✅ LC 101 Symmetric Tree — mirror-axis parallel recursion
> - ✅ LC 110 Balanced Binary Tree — postorder height + early termination
>
> All five are solvable with **only the universal skeleton** above. Don't worry about traversals or BFS yet.

---

## 🎨 Style Habits — Build These From Day 1

> Some habits apply to **every problem you write** (even non-tree ones). Others only matter when you encounter specific patterns. **Master the universal ones now**; skim the context-specific ones and revisit them when you hit the pattern.

---

### 🌐 Universal Habits (apply everywhere, every problem — start using today)

---

#### Habit 1 — Always name your recursive results

```java
// ❌ Compact — works for LC 104, but breaks the moment you need the values for anything else
return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));

// ✅ Named — same answer, extensible to LC 110, 543, 124, etc.
int leftDepth = maxDepth(root.left);
int rightDepth = maxDepth(root.right);
return 1 + Math.max(leftDepth, rightDepth);
```

**Why:** the moment you have **"two-purpose recursion"** (update a global AND return to parent — see LC 543 Diameter, LC 124 Max Path Sum), the compact form breaks. You'd have to rewrite. Building this habit on LC 104 means zero refactor later.

**Bonus benefits:**
- Easier to verbalize in interviews ("first I get the left depth, then the right depth, then return 1 plus the max")
- Easier to debug — set a breakpoint or add a log line on any intermediate
- Easier to extend if the interviewer adds a follow-up like *"now also tell me if it's balanced"*

The 2 extra lines are an **investment**. Always pay them.

---

#### Habit 2 — Null check is line 1 of every tree recursion

```java
public ReturnType solve(TreeNode node) {
    if (node == null) {
        return baseValue;     // ALWAYS the first thing
    }
    // ... rest
}
```

**Why:** missing the null base case = `NullPointerException` on the first leaf you hit. Even before you think about the algorithm, write the null check. It's free insurance.

---

#### Habit 3 — Always brace your blocks (no inline `if`)

```java
// ❌ tempting compact form
if (root == null) return 0;

// ✅ braces always
if (root == null) {
    return 0;
}
```

**Why:**
- The moment you add a log/print/breakpoint, you'd have to add braces anyway
- Braces survive copy-paste reformat (Notion, IDE re-indent) cleanly
- Reads better aloud in interviews
- **One style** across all your code — no decision fatigue

---

#### Habit 4 — Verbalize while you write (interview habit)

For every tree solution you write — even alone — narrate the algorithm out loud as you type:

> *"Base case: if node is null, return zero. Recursive case: I get the left subtree's depth, then the right subtree's depth, then return one plus the max."*

**Why:** in real interviews, you have to talk continuously. Practicing alone in silence and then turning on narration during interviews is a recipe for freezing up. Build the dual-track habit (write + speak) from day 1.

---

### 🔧 Context-Specific Habits (will click as you encounter these patterns)

These won't matter on your first 5 tree problems. **Skim them now to recognize the trap, then refer back when you actually hit the pattern.**

---

#### Habit 5 — Use `Integer.compare` (not subtraction) in comparators

> Applies whenever you use `Arrays.sort`, `PriorityQueue`, `TreeSet`, or `TreeMap` with a custom Comparator.

```java
// ❌ overflows when a = Integer.MIN_VALUE, b = positive
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);

// ✅ safe — handles overflow correctly
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
```

**Why:** `a - b` looks fine but silently produces the wrong sign on extreme values. Doesn't show up in easy tests; bites you on adversarial ones.

→ Full explanation in **`DeepDive/integer-overflow-and-limits.md`** (when written).

---

#### Habit 6 — Use `long` bounds for BST validation (LC 98)

> Applies in BST validation and any problem where you pass min/max bounds through recursion.

Using `int` bounds against `Integer.MIN_VALUE` / `MAX_VALUE` test inputs gives wrong answers. **Always use `Long.MIN_VALUE` / `Long.MAX_VALUE`** when passing bounds. See the BST section below.

→ Full explanation in **`DeepDive/integer-overflow-and-limits.md`** (when written).

---

#### Habit 7 — Use `ArrayDeque`, not `LinkedList`, for queues

> Applies in BFS, sliding window with deque, monotonic queue.

```java
Queue<TreeNode> queue = new ArrayDeque<>();   // ✅ default for queues
Queue<TreeNode> queue = new LinkedList<>();   // works, but slower & more memory
```

**Why:** `ArrayDeque` is faster (no per-node allocation, better cache locality). Both implement `Queue`, so the rest of your code is identical. Pick the better one by default.

---

#### Habit 8 — Snapshot `queue.size()` before the BFS inner loop

> Applies in every BFS where you want **level-by-level** results.

```java
while (!queue.isEmpty()) {
    int size = queue.size();              // ✅ FREEZE the count for THIS level
    for (int i = 0; i < size; i++) {      // do NOT use queue.size() here — it grows
        TreeNode node = queue.poll();
        // ... add children
    }
}
```

**Why:** as you `offer` children inside the inner loop, `queue.size()` keeps changing. Capture it once before the loop or you'll mix levels together.

---

> **Quick recap of the 4 universal habits:** name intermediates → null-check first → always brace → verbalize while writing. Those four cover ~90% of habit benefit on your first 20 problems. The other four sharpen as you grow into specific patterns.

---

## 🚶 DFS Traversals — Preorder, Inorder, Postorder

A **traversal** is a complete walk over every node in some order. The three DFS orders differ only in **when you visit the current node** relative to its children.

```
        A
       / \
      B   C
     / \
    D   E
```

| Traversal | Order | Result on tree above |
| --- | --- | --- |
| **Preorder** | Node → Left → Right | `A B D E C` |
| **Inorder** | Left → Node → Right | `D B E A C` |
| **Postorder** | Left → Right → Node | `D E B C A` |

The names tell you when the **current node** is visited: **pre** (before children), **in** (between children), **post** (after children).

### 🎨 Visual — Same Tree, Three Traversals Side-by-Side

```
Sample tree:                  Each node gets numbered with the ORDER
                              in which it's visited:
         (A)
        /   \                 ┌─────────────────────────────────────────┐
      (B)   (C)                │ PREORDER (Node → L → R):                │
      / \                      │                                         │
    (D) (E)                    │       (1)A     visit 1: A               │
                               │       / \      visit 2: B               │
                               │     (2)B (5)C  visit 3: D               │
                               │     / \        visit 4: E               │
                               │  (3)D (4)E     visit 5: C               │
                               │                                         │
                               │  Order:  A → B → D → E → C              │
                               └─────────────────────────────────────────┘

                               ┌─────────────────────────────────────────┐
                               │ INORDER (L → Node → R):                 │
                               │                                         │
                               │       (4)A     visit 1: D               │
                               │       / \      visit 2: B               │
                               │     (2)B (5)C  visit 3: E               │
                               │     / \        visit 4: A               │
                               │  (1)D (3)E     visit 5: C               │
                               │                                         │
                               │  Order:  D → B → E → A → C              │
                               │                                         │
                               │  ⚠️  Inorder on a BST gives SORTED output│
                               └─────────────────────────────────────────┘

                               ┌─────────────────────────────────────────┐
                               │ POSTORDER (L → R → Node):               │
                               │                                         │
                               │       (5)A     visit 1: D               │
                               │       / \      visit 2: E               │
                               │     (3)B (4)C  visit 3: B               │
                               │     / \        visit 4: C               │
                               │  (1)D (2)E     visit 5: A               │
                               │                                         │
                               │  Order:  D → E → B → C → A              │
                               │                                         │
                               │  Leaves first, root LAST. Used when     │
                               │  parent needs info FROM children.       │
                               └─────────────────────────────────────────┘


WHEN TO USE WHICH:

  Preorder  — clone / serialize / "build the tree top-down"
              (you process parent BEFORE children — natural for copying)

  Inorder   — anything BST-related, sorted output, "Kth smallest"
              (only inorder visits values in left-to-right reading order)

  Postorder — heights, diameters, path sums, deletions
              (parent uses what children computed)
```

### Preorder — node first, then children

**Steps in plain English:**

1. **Base case** — if the node is `null`, return (nothing to visit).
2. **Visit (process) the current node** — print, record, copy into a clone, etc. **Do this first**, before touching either child.
3. **Recurse into the left subtree.**
4. **Recurse into the right subtree.**

```java
public void preorder(TreeNode node) {
    // Step 1 — base case
    if (node == null) {
        return;
    }

    // Step 2 — visit current FIRST (the "pre" in preorder)
    System.out.println(node.val);

    // Step 3 — left
    preorder(node.left);

    // Step 4 — right
    preorder(node.right);
}
```

**Use when:** you want to **build / clone / serialize** a tree (you process the parent before the children).

> 🧩 **Try these:**
> - ✅ LC 144 Binary Tree Preorder Traversal — direct application of the preorder template
> - 🔴 LC 297 Serialize and Deserialize Binary Tree — needs string parsing + queue-based deserialize design (own deep dive later)
> - 🔴 LC 105 Construct Binary Tree from Preorder and Inorder — needs preorder + inorder index-mapping pattern (DP on trees flavor)

### Inorder — left, then node, then right

**Steps in plain English:**

1. **Base case** — if the node is `null`, return.
2. **Recurse into the left subtree completely** — finish the entire left side before doing anything with the current node.
3. **Visit (process) the current node** — print, record, etc. (this happens *between* left and right work).
4. **Recurse into the right subtree.**

```java
public void inorder(TreeNode node) {
    // Step 1 — base case
    if (node == null) {
        return;
    }

    // Step 2 — left first
    inorder(node.left);

    // Step 3 — visit current BETWEEN left and right (the "in" in inorder)
    System.out.println(node.val);

    // Step 4 — right
    inorder(node.right);
}
```

**Use when:** you're working with a **BST** and want elements in **sorted order** (this is the magic property of BSTs — see BST section).

> 🧩 **Try these:**
> - ✅ LC 94 Binary Tree Inorder Traversal — direct application
> - 🟡 **Try after the BST section** — LC 230 Kth Smallest in BST (needs BST + early-stop counter)
> - 🟡 **Try after the BST section** — LC 98 Validate BST (the classic trap — explained later)
> - 🔴 LC 173 Binary Search Tree Iterator — needs iterator design with explicit stack (own deep dive)

### Postorder — children first, then node

**Steps in plain English:**

1. **Base case** — if the node is `null`, return.
2. **Recurse into the left subtree completely.**
3. **Recurse into the right subtree completely.**
4. **Visit (process) the current node LAST** — only after both children have been fully processed. This gives you access to information from both subtrees when you act on the current node.

```java
public void postorder(TreeNode node) {
    // Step 1 — base case
    if (node == null) {
        return;
    }

    // Step 2 — left first
    postorder(node.left);

    // Step 3 — right next
    postorder(node.right);

    // Step 4 — visit current LAST (the "post" in postorder)
    System.out.println(node.val);
}
```

**Use when:** you need information from children **before** processing the parent — bottom-up problems like deleting a tree, computing heights, or path sums where each node aggregates from below.

> 🧩 **Try these:**
> - ✅ LC 145 Binary Tree Postorder Traversal — direct application
> - ✅ LC 110 Balanced Binary Tree — postorder height + early termination
> - 🟡 **Try after the Bottom-Up DFS pattern + the "Building Up to Two-Purpose Recursion" ladder** — LC 543 Diameter of Binary Tree
> - 🔴 **Reference only — do NOT attempt cold** — LC 124 Binary Tree Maximum Path Sum (needs negative-clipping intuition + two-purpose return — full walkthrough below explains why this is gold-standard hard)

### Picking the right traversal

| Problem hint | Likely traversal |
| --- | --- |
| "Process parent first / serialize / clone" | Preorder |
| "Sorted output / BST validation / Kth smallest in BST" | Inorder |
| "Aggregate from children / heights / path sums" | Postorder |
| "Level by level / shortest path in unweighted tree" | BFS (level-order) |

---

## 🌊 BFS / Level Order Traversal

DFS dives deep first; **BFS goes wide** — it visits all nodes at depth 0, then all at depth 1, etc.

```
        1                Level 0: [1]
       / \               Level 1: [2, 3]
      2   3              Level 2: [4, 5, 6]
     / \   \
    4   5   6
```

### Why a Queue (not a Stack)

- **Queue (FIFO)** — first in, first out → matches "process the oldest, deepest-discovered node next" → BFS
- **Stack (LIFO)** — last in, first out → matches DFS (you'd implement iterative DFS with a stack)

### The standard BFS template

**Steps in plain English:**

1. **Create the result container** — an outer list of lists (one inner list per level).
2. **Handle the empty-tree edge case** — if `root` is `null`, return the empty result immediately. Skipping this means the rest of the code would crash on `null.val` or loop on an empty queue without doing anything useful.
3. **Create the queue and seed it with the root** — BFS uses a queue (FIFO) so the oldest discovered node is processed next. We push the root in to start. (Use `ArrayDeque` — faster than `LinkedList`.)
4. **Outer loop — keep going until the queue is empty.** The queue empties only after every node in the tree has been processed.
5. **Snapshot the level size BEFORE the inner loop** — `int size = queue.size()` captures exactly how many nodes are on the current level. **Critical:** as we add children inside the inner loop the queue grows, so reading `queue.size()` again would mix levels together.
6. **Create a fresh inner list** — this collects values for the current level only.
7. **Inner loop — process exactly `size` nodes.** Each iteration:
    - **Pop** the front node off the queue.
    - **Record** its value into the current-level list.
    - **Push** its non-null children (left first, then right) to the back of the queue. They become the next level.
8. **Append the completed level list** to the outer result.
9. **Return the result** when the outer loop exits.

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    // Step 1 — result container
    List<List<Integer>> result = new ArrayList<>();

    // Step 2 — empty-tree guard
    if (root == null) {
        return result;
    }

    // Step 3 — queue + seed with root
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);

    // Step 4 — outer loop, one iteration per level
    while (!queue.isEmpty()) {
        // Step 5 — SNAPSHOT current level's size before we start adding children
        int size = queue.size();

        // Step 6 — fresh list for this level
        List<Integer> level = new ArrayList<>();

        // Step 7 — process exactly `size` nodes (this level only)
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();   // pop front
            level.add(node.val);            // record value

            // push children (they become the NEXT level)
            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }

        // Step 8 — append the completed level
        result.add(level);
    }

    // Step 9 — return when queue is fully drained
    return result;
}
```

### 🎨 Visual — BFS Queue Animation (the `size` snapshot in action)

```
Sample tree:                Queue state at each iteration of the OUTER loop.
                            We snapshot `size` BEFORE the inner loop pops,
        (1)                 so we know exactly how many nodes belong to the
       /   \                current level — even as the queue grows from
     (2)   (3)              their children being pushed in.
     / \     \
   (4) (5)   (6)


┌─────────────────────────────────────────────────────────────────────────┐
│ START                                                                   │
│   Queue:  [ 1 ]                                                         │
│   size = 1   ◀── snapshot                                               │
│   Pop 1, push 2, push 3                                                 │
│   Queue:  [ 2 , 3 ]                                                     │
│   level recorded:  [1]                                                  │
├─────────────────────────────────────────────────────────────────────────┤
│ ITERATION 2                                                             │
│   Queue:  [ 2 , 3 ]                                                     │
│   size = 2   ◀── snapshot (DO NOT re-read queue.size() inside loop!)    │
│                                                                         │
│   Pop 2, push 4, push 5                                                 │
│   Queue:  [ 3 , 4 , 5 ]   ◀── grew, but `size` still = 2                │
│   Pop 3,         push 6                                                 │
│   Queue:  [ 4 , 5 , 6 ]                                                 │
│   level recorded:  [2, 3]                                               │
├─────────────────────────────────────────────────────────────────────────┤
│ ITERATION 3                                                             │
│   Queue:  [ 4 , 5 , 6 ]                                                 │
│   size = 3   ◀── snapshot                                               │
│   Pop 4 (no children)                                                   │
│   Pop 5 (no children)                                                   │
│   Pop 6 (no children)                                                   │
│   Queue:  [ ]                                                           │
│   level recorded:  [4, 5, 6]                                            │
├─────────────────────────────────────────────────────────────────────────┤
│ DONE — queue empty, exit outer loop                                     │
│   Result:  [[1], [2, 3], [4, 5, 6]]                                     │
└─────────────────────────────────────────────────────────────────────────┘


KEY INVARIANT (memorize):

   At ANY moment inside the algorithm, the queue contains at most
   TWO adjacent levels — the current level being drained, and the
   next level being filled by their children.

   That's why BFS gives shortest path in unweighted graphs: nodes
   are discovered strictly in order of distance from root.
```

### The level-snapshot trick (very important)

The line `int size = queue.size()` **before the inner loop** is what separates "all nodes mixed together" from "grouped by level." We capture the count of nodes currently in the queue (= one full level), then process exactly that many before reading the next level.

Without this trick, you can only get a flat traversal. With it, you can answer questions like:
- "Largest value in each row" (LC 515)
- "Right side view" (only the last node of each level — LC 199)
- "Zigzag traversal" (alternate L→R and R→L — LC 103)

> **Why `ArrayDeque` and not `LinkedList`?** Both implement `Queue`. `ArrayDeque` is faster (no per-node allocation, better cache locality) and is the modern Java default. Avoid `LinkedList` for queues.

> 🧩 **Try these:**
> - ✅ LC 102 Level Order Traversal — the template above is the answer
> - ✅ LC 107 Level Order II — same as 102, then `Collections.reverse(result)`
> - ✅ LC 103 Zigzag Level Order Traversal — flip the level list every other iteration
> - ✅ LC 199 Right Side View — only push the **last** node of each level into result
> - ✅ LC 515 Largest Value in Each Row — track max per level
> - 🟡 **Try after Pattern 1 (Top-Down DFS)** — LC 116 Populating Next Right Pointers (BFS-with-pointer-rewiring is doable; recursive O(1)-space variant needs more practice)
> - 🔴 LC 994 Rotting Oranges — BFS on a 2D grid, not a tree. Needs a grids deep dive.

---

## 🧭 How to Choose a Pattern (Decision Framework)

> **Why this section exists:** By Day 3 of practice, you know *how* each of the 4 patterns works. The remaining bottleneck is the **30 seconds** between reading a new problem and committing to one pattern. Pick wrong and you waste 15 minutes before the function signature stops fitting. This framework is the triage you run before you write a single line.

The 4 patterns again, named so you can pattern-match them in seconds:

| # | Pattern | Mental tag |
| --- | --- | --- |
| 1 | **Top-Down DFS** | "Carry state DOWN" |
| 2 | **Bottom-Up / Postorder DFS** | "Combine children UP" |
| 3 | **BFS by Level** | "Process row-by-row" |
| 4 | **Parallel DFS** | "Walk two trees together" |

---

### The 4-Question Funnel — Ask in This Order

```
┌─────────────────────────────────────────────────────────────────────┐
│ Q1: Are we comparing TWO trees (or two halves of one)?              │
│     YES ──▶ Pattern 4: Parallel DFS                                 │
│             Signature: boolean check(TreeNode p, TreeNode q)        │
│     NO  ──▶ continue                                                │
├─────────────────────────────────────────────────────────────────────┤
│ Q2: Does the answer depend on LEVEL / DEPTH / ROW / WIDTH?          │
│     YES ──▶ Pattern 3: BFS by Level                                 │
│             Signature: Queue<TreeNode> q; while (!q.isEmpty()) {... │
│     NO  ──▶ continue                                                │
├─────────────────────────────────────────────────────────────────────┤
│ Q3: Does the answer need information FLOWING DOWN from ancestors    │
│     (running sum, current depth, remaining target, ancestor max)?   │
│     YES ──▶ Pattern 1: Top-Down DFS                                 │
│             Signature: void dfs(TreeNode node, <state>)             │
│     NO  ──▶ continue                                                │
├─────────────────────────────────────────────────────────────────────┤
│ Q4: Does the answer ASSEMBLE FROM CHILDREN'S RESULTS                │
│     (height, sum, count, "is X true for this subtree")?             │
│     YES ──▶ Pattern 2: Bottom-Up / Postorder DFS                    │
│             Signature: int helper(TreeNode node)                    │
└─────────────────────────────────────────────────────────────────────┘
```

> **Why this order works:** Q1 and Q2 are the most distinctive — if either keyword shows up (two trees / level), the choice is forced. Q3 vs Q4 is the harder split — they're both single-tree DFS — so we ask them last with the most specific signal (direction of information flow).

---

### Keyword Signals — What Words Trigger Which Pattern

| Words in the problem | Pattern | Example LC |
| --- | --- | --- |
| "level", "row", "depth-X", "right side view", "zigzag", "wide" | **BFS by Level** | LC 102, 103, 107, 199, 515, 1161 |
| "same as", "mirror", "symmetric", "flip equivalent", "subtree of another", "identical" | **Parallel DFS** | LC 100, 101, 572, 951 |
| "root to leaf path", "all paths", "carry running sum/max", "count good nodes", "remaining target", "sum equals target" | **Top-Down DFS** | LC 112, 113, 129, 988, 1448 |
| "height", "depth (of the whole tree)", "balanced", "diameter", "max path sum", "subtree sum", "lowest common ancestor", "longest univalue path" | **Bottom-Up DFS** | LC 104, 110, 226, 236, 543, 687, 124 |

> **Cross-reference:** the LC numbers in the right column line up with the Try-These callouts in each pattern's section below.

---

### When Keywords Are Ambiguous — The 2-Question Diagnostic

If the keyword table doesn't decide it, ask these two questions about your recursive helper:

> **Q1: What does the helper RETURN to its parent?**
> - Nothing meaningful (`void` or always returns the same default) → **Top-Down**
> - A property of the subtree (height, sum, count, boolean) → **Bottom-Up**
>
> **Q2: What does the helper RECEIVE from its parent?**
> - Just the node → **Bottom-Up**
> - Node + extra state (path-so-far, running max, current depth, remaining target) → **Top-Down**

**Both directions carry meaningful information** → it's **two-purpose recursion** (the function returns one thing AND mutates a shared global). See LC 124, LC 543 — and `DeepDive/recursion-fundamentals.md` § 🧬 Stack vs Heap for the Bug 10 fix.

---

### Hybrid Signals — Problems That Combine 2 Patterns

These don't fit cleanly into one bucket. Recognize them and combine techniques.

| Problem | Primary Pattern | Plus | Tell |
| --- | --- | --- | --- |
| LC 124 Max Path Sum | Bottom-Up DFS | + instance field (two-purpose) | "max anywhere in tree" + subtree assembly |
| LC 543 Diameter of Binary Tree | Bottom-Up DFS | + instance field (two-purpose) | "longest path anywhere" + height return |
| LC 687 Longest Univalue Path | Bottom-Up DFS | + instance field + value match | "longest matching-value path" |
| LC 1448 Count Good Nodes | Top-Down DFS | + count return (mini bottom-up) | Carries running max DOWN, sums counts UP |
| LC 199 Right Side View | BFS by Level | (or DFS + depth tracking) | "last node per level" — either approach works |
| LC 116 Next Right Pointer | BFS by Level | (or recursive O(1)-space DFS) | "link nodes within a level" |
| LC 297 Serialize/Deserialize | BFS **or** Top-Down DFS | (designer's choice) | Either pre-order recursion or level-order |

> **Rule for hybrids:** identify the **primary** pattern from the 4-question funnel first. The secondary technique (instance field, count return, extra parameter) layers on top — it doesn't change the primary signature.

---

### The "Pick → Write the Signature First" Drill

Before writing a single line of the body, **commit to the function signature**. The signature alone tells you whether your pattern choice is right — if the signature doesn't match one of the four canonical shapes below, you've picked wrong (or you're solving a hybrid; check the hybrid table).

| Pattern | Canonical Signature | What it returns / does |
| --- | --- | --- |
| **Top-Down DFS** | `void dfs(TreeNode node, <state>)` <br> *(or `void dfs(TreeNode node, int remaining, List<Integer> path, List<List<Integer>> result)`)* | Mutates a passed-in result list / instance field; no useful return |
| **Bottom-Up DFS** | `int helper(TreeNode node)` <br> *(or `long`, `boolean`, custom return type for the subtree property)* | Returns a property of the subtree rooted at `node` |
| **BFS by Level** | `void / int / List<...> solve(TreeNode root) { Queue<TreeNode> q = new ArrayDeque<>(); ... }` | Iterative — no recursion. Uses the size-snapshot trick. |
| **Parallel DFS** | `boolean check(TreeNode p, TreeNode q)` <br> *(or `void check(TreeNode p, TreeNode q)` for mutation problems like LC 226)* | Returns whether two nodes/subtrees match a property |

> **The signature is the contract.** Once it's right, the body is mechanical. If you find yourself stuck mid-body wishing you had a different parameter, **stop and re-triage** — you probably picked the wrong pattern.

---

### Worked Example 1 — LC 199 Right Side View

**Read the problem:** *"Return the values of nodes visible from the right side of a binary tree, one per level."*

| Question | Answer | Pattern hint |
| --- | --- | --- |
| Q1 (two trees)? | No | — |
| Q2 (level/depth)? | **Yes** — "one per level" | **BFS by Level** ✅ |

**Commit to signature:** `List<Integer> rightSideView(TreeNode root) { Queue<TreeNode> q = new ArrayDeque<>(); ... }`

**Body fills in:** at each level, push the **last node's value** (the rightmost) into the result. Done.

> Note: a Top-Down DFS with depth tracking also works (push value when `depth == result.size()`). Either is fine — BFS is the more obvious match.

---

### Worked Example 2 — LC 543 Diameter of Binary Tree

**Read the problem:** *"Length of the longest path between any two nodes (counted in edges)."*

| Question | Answer | Pattern hint |
| --- | --- | --- |
| Q1 (two trees)? | No | — |
| Q2 (level/depth)? | No | — |
| Q3 (info from ancestors)? | No | — |
| Q4 (assemble from children)? | **Yes** — need each child's height to compute the bent path | **Bottom-Up DFS** ✅ |

**Diagnostic check:** helper returns a property of the subtree (height) → Bottom-Up confirmed. But also: we need to update a global with the bent-path candidate (`left + right`). Helper has **two purposes** (return height, update diameter) → **hybrid: Bottom-Up + instance field**.

**Commit to signature:**
```java
private int diameter;

public int diameterOfBinaryTree(TreeNode root) { ... }

private int height(TreeNode node) { ... }       // returns height; updates this.diameter
```

> See `Patterns/max-path-sum-binary-tree-problem.md` for LC 124's identical-shape walkthrough — same hybrid pattern, sum metric instead of edge count.

---

### Worked Example 3 — LC 113 Path Sum II

**Read the problem:** *"Return all root-to-leaf paths where the sum equals targetSum."*

| Question | Answer | Pattern hint |
| --- | --- | --- |
| Q1 (two trees)? | No | — |
| Q2 (level/depth)? | No | — |
| Q3 (info from ancestors)? | **Yes** — running path so far, remaining target | **Top-Down DFS** ✅ |

**Commit to signature:**
```java
private void dfs(TreeNode node, int remaining, List<Integer> path, List<List<Integer>> result)
```

**Body fills in:** add node, recurse with `remaining - node.val`, snapshot at leaf if matched, undo on the way out.

> See `DeepDive/recursion-fundamentals.md` → **Bug 11** for the three-bug compound failure to avoid here.

---

### The Diagnostic Cheat Code

When you're stuck triaging, **write the helper signature out loud** in 3 candidate forms:

```
Candidate A (Top-Down):    void dfs(TreeNode node, ??? state)
Candidate B (Bottom-Up):   ??? helper(TreeNode node)
Candidate C (Parallel):    boolean check(TreeNode p, TreeNode q)
```

Pick the one whose `???` slot is easiest to fill in for *this* problem.

- Can't think of any "state to carry down"? → Not Top-Down.
- Can't think of any "thing to return from a subtree"? → Not Bottom-Up.
- Only one tree in the problem? → Not Parallel.

The candidate left standing is usually the answer.

---

Almost every binary tree problem fits one of these. Recognize the pattern → write the template → fill in the work.

---

### Pattern 1: Top-Down DFS (carry state down)

**When you'll see this pattern:**
- LC 112 Path Sum — carry remaining target down to leaves
- LC 1448 Count Good Nodes — carry running max down to count valid nodes
- LC 113 Path Sum II — carry path down, collect all root-to-leaf paths
- LC 129 Sum Root to Leaf Numbers — carry running digit down, sum at leaves
- Real-world example: Accumulating context as you descend a tree (current level, depth, constraints)

> Pass information **from parent to child** as you recurse. Often used to track depth, current path, or running sums.

**Steps in plain English (template):**

1. **Add a "state" parameter** to the recursion — depth, running sum, ancestor max, current path, etc. The parent computes it and passes it down to the children.
2. **Base case** — if the node is `null`, return (no work to do).
3. **Do work using the carried state** — record into a result, update a global, etc.
4. **Recurse into children with the UPDATED state** — e.g., `depth + 1`, `runningSum + node.val`. Each child gets a fresh copy of the state for its own subtree.

```java
public void dfs(TreeNode node, int depth) {
    // Step 2 — base case
    if (node == null) {
        return;
    }

    // Step 3 — do work using `depth` (the carried state)
    // e.g., result[depth] = max(result[depth], node.val);

    // Step 4 — recurse with UPDATED state
    dfs(node.left, depth + 1);
    dfs(node.right, depth + 1);
}
```

---

**Example use case — Path Sum (LC 112):** *"Does any root-to-leaf path sum to `target`?"* You carry the **remaining target** down.

**Steps in plain English:**

1. **Base case** — if the node is `null`, there's no path here. Return `false`.
2. **Leaf check** — if both children are `null`, this is a leaf. The path completes here. Return `true` if `node.val` exactly matches what's left of the target; otherwise `false`.
3. **Compute the new state** — subtract this node's value from the target. The remaining target is what either subtree must produce.
4. **Recurse into both children with the updated remaining target.** Use `||` so we **short-circuit** as soon as either side finds a valid path.

```java
public boolean hasPathSum(TreeNode node, int target) {
    // Step 1 — base case (no node, no path)
    if (node == null) {
        return false;
    }

    // Step 2 — leaf: check if this node alone closes out the target
    if (node.left == null && node.right == null) {
        return target == node.val;
    }

    // Step 3 — update the state we carry to children
    int remaining = target - node.val;

    // Step 4 — try both subtrees; short-circuit on first match
    return hasPathSum(node.left, remaining)
        || hasPathSum(node.right, remaining);
}
```

> 🧩 **Try these:**
> - ✅ LC 112 Path Sum — the example above is half the answer
> - 🟡 **Try after you're comfortable with LC 112** — LC 1448 Count Good Nodes (carry running max down)
> - 🟡 **Try after LC 112 + you're comfortable building the path list** — LC 113 Path Sum II (collect all root-to-leaf paths; uses `List<Integer>` backtracking)
> - 🟡 **Try after LC 113** — LC 129 Sum Root to Leaf Numbers (carry running number down, sum at leaves)
> - 🔴 LC 988 Smallest String Starting From Leaf — needs string-building + custom comparator on leaf-to-root paths

> 🐞 **LC 113 bug trap — read this BEFORE attempting:**
>
> The natural-feeling first attempt fails in **three compounding ways**: (1) storing `path` as a reference instead of `new ArrayList<>(path)`, (2) trying to "reset" the path via `path = new ArrayList<>();` (rebinds local slot only — caller is blind), (3) forgetting `path.remove(path.size() - 1)` after recursing.
>
> All three are the same root cause: **a `List` parameter is a shared heap object — mutate it, don't reassign it; snapshot when storing; pair every add with a remove.**
>
> Full diagnosis: **`DeepDive/recursion-fundamentals.md` § 🧬 Stack vs Heap → "Mistake B"** and **Bug 11 — Reassigning a List parameter doesn't reset the caller's list**.

---

### Pattern 1 — Pattern Application Gallery

**Most-asked problems using top-down DFS:**

- **LC 112 Path Sum** — Does any root-to-leaf path sum to target?
- **LC 113 Path Sum II** — Find all root-to-leaf paths with target sum
- **LC 1448 Count Good Nodes** — Count nodes where node.val ≥ max ancestor seen
- **LC 129 Sum Root to Leaf Numbers** — Treat paths as numbers, sum all such numbers
- **LC 988 Smallest String Starting From Leaf** — Build leaf-to-root strings, return lexicographically smallest

---

### Pattern 2: Bottom-Up DFS (collect from children, return up)

> Each call **returns information** about its subtree. The parent **combines** the children's returns to compute its own answer.

**When you'll see this pattern:**

Computing metrics that roll up from leaves — height, depth, sums, counts, validity checks. The subtree's answer flows up through returns; the parent combines them into a global answer:

- **LC 110 Balanced Binary Tree** — return height *and* validity; combine subtree heights to check balance constraint
- **LC 543 Diameter of Binary Tree** — return height; compute global diameter as left height + right height
- **LC 124 Maximum Path Sum** — return max-height-ending-at-node; track global max path (may bend at current node)
- **LC 687 Longest Univalue Path** — return longest same-value path ending here; track longest bending through this node
- **LC 1373 Maximum Sum BST in Binary Tree** — return subtree info (BST? sum?); track the max-sum BST seen

**Real-world example:** Your service needs to summarize a tree of data — total cost per subtree, max latency path, whether a compliance rule holds across all descendants. You traverse up, combining child results into your own.

**Steps in plain English (template):**

1. **Base case** — if the node is `null`, return a "neutral" value (`0`, `null`, `Integer.MIN_VALUE`, etc.) that doesn't change the combine step.
2. **Recurse into the left subtree** and store its returned value.
3. **Recurse into the right subtree** and store its returned value.
4. **Combine** — use the current node's value plus the two subtree results to compute *this* subtree's answer, and **return that combined value to the parent**.

```java
public ReturnType dfs(TreeNode node) {
    // Step 1 — base case
    if (node == null) {
        return baseValue;
    }

    // Step 2 — left subtree's answer
    ReturnType leftAns = dfs(node.left);

    // Step 3 — right subtree's answer
    ReturnType rightAns = dfs(node.right);

    // Step 4 — combine and return up
    return combine(node.val, leftAns, rightAns);
}
```

This is the most common pattern. `maxDepth` (above) is bottom-up.

---

**Example use case — Diameter of Binary Tree (LC 543):** *"Length of the longest path between any two nodes."* Path can go through any node — including bending at it.

**Steps in plain English:**

1. **Use an instance field for the global answer** (`diameter`), reset it in the public method (so LeetCode's class reuse doesn't poison the next test case).
2. **Public method** — call the recursive helper, then return the global.
3. **Helper base case** — if the node is `null`, height is `0`.
4. **Recurse to get the height of each subtree.** Trust the recursion.
5. **Update the global with the path-through-this-node candidate** — `left + right` (an edge from this node to the deepest left descendant + an edge to the deepest right descendant = the bent path's edge count).
6. **Return one-sided height to the parent** — the parent can only extend through one child, so we return `1 + max(left, right)`. This is the classic "two-purpose recursion" — global = both sides, return = one side.

```java
private int diameter;

public int diameterOfBinaryTree(TreeNode root) {
    // Step 1 — reset for this run
    diameter = 0;

    // Step 2 — fire the recursion, then return the global
    height(root);
    return diameter;
}

private int height(TreeNode node) {
    // Step 3 — base case
    if (node == null) {
        return 0;
    }

    // Step 4 — heights of both subtrees
    int left = height(node.left);
    int right = height(node.right);

    // Step 5 — update global with bent-path candidate (uses BOTH sides)
    diameter = Math.max(diameter, left + right);

    // Step 6 — return one-sided height to parent
    return 1 + Math.max(left, right);
}
```

> **Two-purpose recursion** is a hallmark: the function **returns one thing** (height) but also **updates a global** (max diameter). This shows up constantly. Variations of this pattern: LC 124 Max Path Sum, LC 543 Diameter, LC 687 Longest Univalue Path.

> 🐞 **The bug trap I keep falling for — read this BEFORE attempting LC 543:**
>
> The natural-feeling first attempt is to pass `int diameter` as a parameter to the helper and reassign it (`diameter = left + right;`). **This is broken.** `int` is a primitive — every recursive call gets its own copy, so the caller's `diameter` stays `0`.
>
> ✅ **The fix — and the only one used in the template above:** hoist `diameter` to an **instance field** on the `Solution` class. Reset it at the top of the public method. The recursive helper updates `this.diameter` directly — every frame writes to the **same** heap-resident `Solution` object, so the change is visible everywhere.
>
> ```java
> // ✅ The pattern (matches the template above)
> private int diameter;                    // instance field — lives on the heap
>
> public int diameterOfBinaryTree(TreeNode root) {
>     diameter = 0;                         // reset for this run
>     height(root);
>     return diameter;
> }
>
> private int height(TreeNode node) {
>     if (node == null) {
>         return 0;
>     }
>     int left = height(node.left);
>     int right = height(node.right);
>     diameter = Math.max(diameter, left + right);   // writes to the Solution instance
>     return 1 + Math.max(left, right);
> }
> ```
>
> Why the instance field works while `int max` parameter doesn't: `this.diameter` lives on the heap (inside the `Solution` object). Every recursive call holds the same `this` reference, so they all reach the same `diameter` slot. Mutation is visible across all frames — exactly like a `List` parameter.
>
> Full diagnosis with stack-frame diagram: **`DeepDive/recursion-fundamentals.md` § 🧬 Stack vs Heap → "Mistake A"** and **Bug 10 — Primitive accumulator parameter doesn't propagate up**.
>
> Same root cause shows up as the List-flavored Bug 11 (LC 113 Path Sum II — see below in Pattern 1 Try-These).

---

### Pattern 2 — Pattern Application Gallery

**Most-asked problems using bottom-up DFS:**

- **LC 110 Balanced Binary Tree** — Check if tree is balanced (height difference ≤ 1 at every node)
- **LC 543 Diameter of Binary Tree** — Find the longest path between any two nodes
- **LC 124 Maximum Path Sum** — Find the path with maximum sum of node values
- **LC 687 Longest Univalue Path** — Find the longest path of consecutive equal values
- **LC 1373 Maximum Sum BST in Binary Tree** — Find the largest BST subtree by sum

---

> 🧩 **Try these:**
> - ✅ LC 110 Balanced Binary Tree — postorder height + early-termination via sentinel `-1`. **Start here** to get bottom-up muscle memory.
> - 🟡 **Try after LC 110 + the "Building Up to Two-Purpose Recursion" ladder below** — LC 543 Diameter of Binary Tree
> - 🔴 **Reference only — do NOT attempt cold** — LC 124 Maximum Path Sum (full walkthrough + bug list below)
> - 🔴 LC 687 Longest Univalue Path — variant of LC 543 with value matching; do this after LC 543 clicks
> - 🔴 LC 1373 Maximum Sum BST in Binary Tree — combines BST validation + subtree sum + two-purpose recursion. Multi-pattern problem; come back after each individual pattern is solid.

---

### Pattern 3: BFS by Level (level-snapshot trick)

> Use the queue with `size = queue.size()` to process one level at a time. See the BFS section above.

**When you'll see this pattern:**

Collecting per-level snapshots — you need all nodes at depth `d` before moving to `d+1`. The queue's `size()` tells you exactly how many nodes belong to this level:

- **LC 102 Level Order Traversal** — collect all nodes at each level into a list
- **LC 103 Zigzag Level Order Traversal** — same, but alternate left-to-right, right-to-left per level
- **LC 199 Right Side View** — pick the last (rightmost) node at each level
- **LC 515 Largest Value in Each Row** — find max node value at each level
- **LC 1161 Maximum Level Sum** — track sum per level, return the level number with max sum

**Real-world example:** Your team processes a batch of work at each "round" — depth 0 one round, depth 1 next. You need to wait for all depth-0 jobs to finish before scheduling depth-1.

---

### Pattern 3 — Pattern Application Gallery

**Most-asked problems using BFS by level:**

- **LC 102 Level Order Traversal** — Collect all nodes at each level into separate lists
- **LC 103 Zigzag Level Order Traversal** — Collect nodes per level, alternating direction each level
- **LC 199 Right Side View** — Return the rightmost node visible from each level
- **LC 515 Largest Value in Each Row** — Return the maximum value at each level
- **LC 1161 Maximum Level Sum** — Return the level number with the maximum node sum

---

> 🧩 **Try these:**
> - ✅ LC 102 Level Order Traversal
> - ✅ LC 103 Zigzag Level Order Traversal
> - ✅ LC 199 Right Side View
> - ✅ LC 515 Largest Value in Each Row
> - ✅ LC 1161 Maximum Level Sum — track sum per level, return the level number with max sum
> - 🟡 LC 116 Populating Next Right Pointers — BFS solution is approachable; constant-space recursive solution is harder, save it

---

### Pattern 4: Tree Comparison / Symmetry (parallel DFS)

> Recurse on **two trees (or two parts of the same tree) in parallel**. The function takes two nodes instead of one.

**When you'll see this pattern:**

Comparing trees, detecting symmetry, or validating shape invariants — walk two pointers side-by-side:

- **LC 100 Same Tree** — exact structural and value match between two trees
- **LC 101 Symmetric Tree** — tree is a mirror of itself (walk left vs right of same tree)
- **LC 572 Subtree of Another Tree** — is one tree a subtree of another? (combine same-tree check with top-down scan)
- **LC 951 Flip Equivalent Binary Trees** — trees equivalent with optional left/right child swaps allowed
- **LC 1367 Linked List in Binary Tree** — check if a linked-list sequence appears as a root-to-leaf path

**Real-world example:** Validate that a tree matches a schema, or detect if two config trees are equivalent despite structural differences.

**Example use case — Same Tree (LC 100):** *"Are two trees structurally identical with the same values?"*

**Steps in plain English:**

1. **Both null base case** — if both nodes are `null`, this position matches. Return `true`.
2. **Mismatched-null base case** — if exactly one is `null` (the other isn't), the trees differ in shape. Return `false`.
3. **Value check** — both nodes exist; if their values differ, the trees aren't the same. Return `false`.
4. **Recurse in parallel** — both left subtrees must match each other, AND both right subtrees must match each other. Use `&&` to short-circuit on the first mismatch.

```java
public boolean isSameTree(TreeNode p, TreeNode q) {
    // Step 1 — both null → match
    if (p == null && q == null) {
        return true;
    }

    // Step 2 — exactly one null → shape mismatch
    if (p == null || q == null) {
        return false;
    }

    // Step 3 — both non-null but values differ
    if (p.val != q.val) {
        return false;
    }

    // Step 4 — recurse: BOTH subtrees must match
    return isSameTree(p.left, q.left)
        && isSameTree(p.right, q.right);
}
```

---

**Example use case — Symmetric Tree (LC 101):** *"Is the tree a mirror of itself around its center?"* The parallel call uses **mirror axes** — left of one is compared to right of the other.

**Steps in plain English:**

1. **Public method** — call the helper with `(root, root)`. We're going to walk two pointers: one drifts to the left side, one drifts to the right side, and they should mirror each other.
2. **Both null base case** — both ends of the mirror are empty → matches.
3. **Mismatched-null base case** — one side has a node where the other doesn't → asymmetric.
4. **Value check + mirrored recursion** — both values must match, AND the **outer** pair (`a.left` vs `b.right`) must mirror, AND the **inner** pair (`a.right` vs `b.left`) must mirror. The swapped recursion is what makes this "mirror" rather than "same."

```java
public boolean isSymmetric(TreeNode root) {
    // Step 1 — start two pointers at the root
    return isMirror(root, root);
}

private boolean isMirror(TreeNode a, TreeNode b) {
    // Step 2 — both null → match
    if (a == null && b == null) {
        return true;
    }

    // Step 3 — exactly one null → not a mirror
    if (a == null || b == null) {
        return false;
    }

    // Step 4 — values match AND outer pair mirrors AND inner pair mirrors
    return a.val == b.val
        && isMirror(a.left, b.right)    // outer: leftmost vs rightmost
        && isMirror(a.right, b.left);   // inner: drifting toward center
}
```

---

### Pattern 4 — Pattern Application Gallery

**Most-asked problems using parallel DFS:**

- **LC 100 Same Tree** — Check if two trees are structurally identical with the same values
- **LC 101 Symmetric Tree** — Check if tree is symmetric (mirror of itself)
- **LC 572 Subtree of Another Tree** — Check if one tree is a subtree of another
- **LC 951 Flip Equivalent Binary Trees** — Check if trees are equivalent with optional left/right flips
- **LC 1367 Linked List in Binary Tree** — Check if a linked list sequence appears as a tree path

---

> 🧩 **Try these:**
> - ✅ LC 100 Same Tree — the example above is the answer
> - ✅ LC 101 Symmetric Tree — mirror-axis variant (full code above)
> - ✅ LC 572 Subtree of Another Tree — combine `isSameTree` (this pattern) with a top-down DFS scan
> - 🟡 **Try after LC 100 + LC 101 click** — LC 951 Flip Equivalent Binary Trees (parallel DFS with optional left/right swap — reads like a riddle the first time)

---

## 🌳 Binary Search Tree (BST)

A BST is a binary tree with one extra rule — the **BST invariant**:

> For every node, **all values in its left subtree are less than its value**, and **all values in its right subtree are greater**.

```
        5
       / \
      3   8
     / \   \
    1   4   9
```

This is a BST. Notice:
- Left subtree of `5` is `{1, 3, 4}` — all `< 5`
- Right subtree of `5` is `{8, 9}` — all `> 5`
- Same property holds for `3` and `8` in their own subtrees

> **The "all values" wording is critical** — it's not enough that just `node.left.val < node.val`. **Every** descendant of `node.left` must be less than `node.val`. This is the trap in LC 98 Validate BST (covered below).

### Why BST? Because **inorder traversal of a BST yields sorted output**.

Trace inorder on the BST above: visit left subtree → root → right subtree.

```
inorder(5):
  inorder(3):
    inorder(1) → prints 1
    print 3
    inorder(4) → prints 4
  print 5
  inorder(8):
    print 8
    inorder(9) → prints 9
```

Output: `1, 3, 4, 5, 8, 9` ✅ sorted.

This single property powers most BST problems:
- **Kth smallest** (LC 230) — do an inorder walk and stop at the K-th visit
- **Validate BST** (LC 98) — inorder values must be strictly increasing
- **Two Sum IV — Input is BST** (LC 653) — inorder gives sorted array → two-pointer

### BST search — O(log n) on a balanced tree

**Steps in plain English:**

1. **Combined base case** — if the node is `null` (target doesn't exist) OR if the current node's value equals the target (we found it), return the current `node` reference. Returning `null` means "not found"; returning a non-null node means "here it is."
2. **Go left when target is smaller** — by the BST invariant, anything `>= node.val` lives on the right, so a smaller target *must* be in the left subtree.
3. **Go right when target is larger** — by the same invariant, a larger target must be in the right subtree.

```java
public TreeNode search(TreeNode root, int target) {
    // Step 1 — base case: not found OR found
    if (root == null || root.val == target) {
        return root;
    }

    // Step 2 — target smaller → go left
    if (target < root.val) {
        return search(root.left, target);
    }

    // Step 3 — target larger → go right
    return search(root.right, target);
}
```

At each step we eliminate **half the tree** (binary search principle, but on a tree).

> ⚠️ Worst case is O(n) when the tree is **unbalanced** (a "stick" tree shaped like a linked list). Self-balancing BSTs (TreeMap, TreeSet) keep this at O(log n) — but in interviews you usually deal with plain BSTs.

> 🧩 **Try these:**
> - ✅ LC 700 Search in a BST — the template above is the answer
> - ✅ LC 938 Range Sum of BST — recurse left if `node.val > low`, right if `node.val < high`, add when in range. Pure BST-property warmup.
> - ✅ LC 270 Closest Value in BST — walk down with a "best-so-far" int, update at each visited node
> - 🟡 **Try after LC 700** — LC 701 Insert into a BST (recurse to where the value belongs, attach a new node)
> - 🔴 LC 450 Delete Node in a BST — pointer surgery with three cases (leaf / one child / two children with inorder successor). Save for after the BST section is comfortable.

### BST validation — the classic trap (LC 98)

**Wrong solution that looks right:**

```java
// ❌ Only checks immediate children — misses deep violations
public boolean isValidBST(TreeNode root) {
    if (root == null) {
        return true;
    }
    if (root.left != null && root.left.val >= root.val) {
        return false;
    }
    if (root.right != null && root.right.val <= root.val) {
        return false;
    }
    return isValidBST(root.left) && isValidBST(root.right);
}
```

This **fails** on:

```
        5
       / \
      1   8
         / \
        3   9        ← 3 is in 5's RIGHT subtree but 3 < 5 — violates BST!
```

The wrong solution only compares each node to its immediate children. It doesn't enforce that **everything** in the right subtree is greater than `5`.

### 🎨 Visual — BST Bounds Propagation (the LC 98 fix)

```
The CORRECT algorithm passes a (min, max) range DOWN to every recursive
call. Each node must satisfy   min < node.val < max.   When we recurse:
   - going LEFT,  the max  TIGHTENS to node.val
   - going RIGHT, the min  TIGHTENS to node.val


The buggy tree from above with bounds annotated at each node:

                  (-∞,  +∞)
                     │
                    (5)            5 in (-∞,  +∞)        ✅
                   /   \
            (-∞, 5)     (5, +∞)
                │           │
               (1)         (8)     1 in (-∞, 5),  8 in (5, +∞)   ✅
                          /   \
                     (5, 8)    (8, +∞)
                       │         │
                      (3)       (9)
                       ▲
                       │
                       └── 3 must be in (5, 8) — but 3 ≤ 5  ❌  CAUGHT!


Compare to the WRONG algorithm (only checks parent vs child):

    5 vs 8: 8 > 5  ✅   ── looks fine locally
    8 vs 3: 3 < 8  ✅   ── also looks fine locally
                            but 3 is in 5's RIGHT subtree, so 3 > 5 fails

  The bounds version catches this because the `min = 5` constraint
  is carried DOWN past node 8 into node 3.


HOW BOUNDS TIGHTEN AS WE WALK DOWN:

   Starting bounds:                    ( -∞ ,  +∞ )
   Go left  through node X:            ( -∞ ,    X )
   Go left  through Y (Y < X):         ( -∞ ,    Y )
   Go right through Z (Z < X, Z > Y):  (  Y ,    X )

   Bounds only get TIGHTER — never looser. That monotonic shrinking
   is what proves the BST invariant globally.
```

**Correct solution — pass valid bounds down:**

**Steps in plain English:**

1. **Public method** — kick off recursion with the widest possible bounds: `(-∞, +∞)`. We use `Long.MIN_VALUE` / `Long.MAX_VALUE` (not `Integer.*`) because LeetCode tests use `Integer.MIN_VALUE` / `Integer.MAX_VALUE` *as actual node values*, and `int` bounds would cause those legitimate nodes to fail the bounds check.
2. **Base case** — `null` subtree is trivially valid. Return `true`.
3. **Bounds check** — every node must be **strictly between** `min` and `max`. If `node.val <= min` or `node.val >= max`, the BST invariant is broken — return `false`.
4. **Recurse with TIGHTENED bounds:**
    - **Left subtree** — its values must be in `(min, node.val)`. So we pass `(min, node.val)` as the new bounds.
    - **Right subtree** — its values must be in `(node.val, max)`. So we pass `(node.val, max)`.
    - Both must succeed (`&&`).

```java
public boolean isValidBST(TreeNode root) {
    // Step 1 — widest possible bounds (long avoids the Integer.MIN/MAX trap)
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    // Step 2 — empty subtree is fine
    if (node == null) {
        return true;
    }

    // Step 3 — strict bounds check
    if (node.val <= min || node.val >= max) {
        return false;
    }

    // Step 4 — recurse with tightened bounds
    return validate(node.left, min, node.val)       // left: max becomes node.val
        && validate(node.right, node.val, max);     // right: min becomes node.val
}
```

> **Why `long` and not `int`?** LeetCode tests with `Integer.MIN_VALUE` and `Integer.MAX_VALUE` as node values. Using `int` bounds means `Integer.MIN_VALUE >= Integer.MIN_VALUE` returns true, which fails legitimately valid trees. Use `long` (or pass `TreeNode` as bound and check via inorder).

**Alternative: inorder-based validation**

**Steps in plain English:**

1. **Use an instance field `prev`** — track the **previous node visited in inorder**. Inorder traversal of a valid BST yields strictly increasing values, so we just need to check each node against the previous one.
   - **Important:** in production LeetCode code, also reset `prev = null` in the public method so the field doesn't carry stale state across test cases.
2. **Base case** — `null` is fine. Return `true`.
3. **Inorder Step 1: recurse left first** — fully process the left subtree. If anything in there fails, propagate `false` up immediately (short-circuit).
4. **Inorder Step 2: visit current node** — compare `node.val` against `prev.val`. If `prev` exists and `prev.val >= node.val`, the inorder sequence is not strictly increasing → not a BST → return `false`. Otherwise update `prev` to the current node so the next inorder visit can compare against it.
5. **Inorder Step 3: recurse right** — return whatever the right subtree's validation says.

```java
private TreeNode prev;

public boolean isValidBST(TreeNode root) {
    // (Production tip: reset `prev = null;` here in real LeetCode submissions.)

    // Step 2 — base case
    if (root == null) {
        return true;
    }

    // Step 3 — inorder: left first; bail out on any failure
    if (!isValidBST(root.left)) {
        return false;
    }

    // Step 4 — visit current: previous inorder value must be strictly smaller
    if (prev != null && prev.val >= root.val) {
        return false;
    }
    prev = root;

    // Step 5 — inorder: right
    return isValidBST(root.right);
}
```

> 🧩 **Try these:**
> - ✅ LC 98 Validate BST — pick **either** the bounds approach or the inorder approach above. Solving once each is great practice.
> - ✅ LC 530 Minimum Absolute Difference in BST — inorder walk, track `prev` node, update min difference. Direct application of the inorder-prev pattern from LC 98 alt solution.
> - ✅ LC 783 Minimum Distance Between BST Nodes — literally identical to LC 530
> - 🔴 LC 99 Recover BST — inorder traversal to find the **two swapped nodes** with O(1) space (Morris traversal). Nontrivial; come back later.

### BST as a sorted set (Java TreeMap / TreeSet)

In Java, **`TreeMap` and `TreeSet` are red-black trees** under the hood — self-balancing BSTs. So if a problem asks for "ordered keys with O(log n) insert/lookup/closest," reach for these instead of building a BST manually:

```java
TreeSet<Integer> set = new TreeSet<>();
set.add(5);
set.floor(7);     // largest element ≤ 7
set.ceiling(3);   // smallest element ≥ 3
```

See `Reference/set-section-updated.md` for the full TreeSet treatment.

---

## 🔬 Worked Walkthroughs

Twelve canonical problems — one per structurally unique shape. Every walkthrough: Problem → Brute Force → Intuition Bridge → Steps + Code → Transfers To.

---

### WW-1 — LC 104 Maximum Depth of Binary Tree

> **Problem:** Given the root of a binary tree, return its maximum depth (number of nodes on the longest root-to-leaf path).

**Brute force:** Generate every root-to-leaf path; measure each path's length; return the maximum. O(n × h) where h = height — visits nodes multiple times.
> **Time:** O(n × h) | **Space:** O(h)

**Intuition bridge — what cracks it open:** Every node must be visited — there's no way to skip. The insight is reuse: `depth(node) = 1 + max(depth(left), depth(right))`. Each subtree's result bubbles up so no node is visited twice. Postorder combine in one pass.

**Steps in plain English:**

1. **Base case** — `null` node has depth 0.
2. **Recurse** into left and right subtrees; trust they return the correct depth.
3. **Combine** — return `1 + max(leftDepth, rightDepth)`.

```java
public int maxDepth(TreeNode root) {
    // Step 1
    if (root == null) {
        return 0;
    }
    // Step 2 — trust recursion
    int leftDepth = maxDepth(root.left);
    int rightDepth = maxDepth(root.right);
    // Step 3 — combine
    return 1 + Math.max(leftDepth, rightDepth);
}
```

**Time:** O(n) | **Space:** O(h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 111 Minimum Depth | Same postorder combine | Min depth = path to nearest leaf — null child ≠ leaf | `if (root.left == null) return 1 + minDepth(root.right)` |
| LC 110 Balanced Binary Tree | Same height computation | Return `-1` sentinel to propagate "unbalanced" upward | `if (Math.abs(l - r) > 1) return -1` |
| LC 559 Max Depth of N-ary Tree | Same `1 + max(children depths)` | Loop over `node.children` instead of two fixed calls | `for (Node c : root.children) max = Math.max(max, maxDepth(c))` |

---

### WW-2 — LC 226 Invert Binary Tree

> **Problem:** Invert a binary tree (mirror it left-to-right). Return the root.

**Brute force:** BFS level by level; for each node swap `.left` and `.right`. O(n) time — same as optimal.
> **Time:** O(n) | **Space:** O(n) queue

**Intuition bridge — what cracks it open:** After inverting both subtrees recursively, all we do at the current node is swap the two returned roots. The postorder pattern: do children first, act at current node last. Trust that each recursive call returns a fully inverted subtree.

**Steps in plain English:**

1. **Base case** — `null` node, return `null`.
2. **Recurse left** — get back a fully inverted left subtree.
3. **Recurse right** — get back a fully inverted right subtree.
4. **Swap** — assign inverted-right to `root.left`, inverted-left to `root.right`.
5. **Return root.**

```java
public TreeNode invertTree(TreeNode root) {
    // Step 1
    if (root == null) {
        return null;
    }
    // Step 2, 3 — recurse; trust each subtree is fully inverted on return
    TreeNode left = invertTree(root.left);
    TreeNode right = invertTree(root.right);
    // Step 4 — swap
    root.left = right;
    root.right = left;
    // Step 5
    return root;
}
```

**Time:** O(n) | **Space:** O(h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 100 Same Tree | Postorder check on two trees simultaneously | Return bool (are they equal?) instead of modifying | `return p.val == q.val && isSameTree(p.left, q.left) && ...` |
| LC 617 Merge Two Binary Trees | Same postorder; two trees simultaneously | Sum vals instead of swap; handle one-null case | `return new TreeNode(t1.val + t2.val, merge(l1,l2), merge(r1,r2))` |
| LC 951 Flip Equivalent Binary Trees | Same mirror concept | Accept if children match either normally OR flipped | Check both `(l1==l2 && r1==r2)` and `(l1==r2 && r1==l2)` |

---

### WW-3 — LC 102 Binary Tree Level Order Traversal

> **Problem:** Return the values of nodes level by level, each level as a separate list.

**Brute force:** DFS with a depth parameter; collect into `Map<Integer, List<Integer>>` keyed by depth; sort keys. O(n log n) due to sort.
> **Time:** O(n log n) | **Space:** O(n)

**Intuition bridge — what cracks it open:** BFS naturally visits nodes level by level. The key trick: before dequeuing the entire current level, snapshot `queue.size()` — that count is exactly how many nodes belong to the current level. Process exactly that many nodes, then snapshot again for the next level.

**Steps in plain English:**

1. **Seed queue** with root (if not null).
2. **While queue not empty:** snapshot `levelSize = queue.size()`.
3. **Dequeue exactly `levelSize` nodes**, collect their values into a sublist, enqueue their non-null children.
4. **Add sublist** to result. Repeat.

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) {
        return result;
    }
    // Step 1
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);

    // Step 2
    while (!queue.isEmpty()) {
        int levelSize = queue.size();
        List<Integer> level = new ArrayList<>();
        // Step 3
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
        // Step 4
        result.add(level);
    }
    return result;
}
```

**Time:** O(n) | **Space:** O(n)

### 🎨 Visual — queue snapshot trick on a 3-level tree

```
Tree:         1
            /   \
           2     3
          / \   / \
         4   5 6   7

Initial queue: [1]

Level 1: snapshot size=1. Poll 1 → level=[1]. Enqueue 2,3.   queue=[2,3]
Level 2: snapshot size=2. Poll 2 → level=[2]. Enqueue 4,5.
                          Poll 3 → level=[2,3]. Enqueue 6,7.  queue=[4,5,6,7]
Level 3: snapshot size=4. Poll all → level=[4,5,6,7].         queue=[]

Result: [[1],[2,3],[4,5,6,7]]

KEY INVARIANT:
  queue.size() at the START of each outer iteration = exactly the number
  of nodes at the current level. The inner for-loop processes that exact
  count, leaving only next-level nodes in the queue.
```

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 107 Level Order II | Exact same BFS | Reverse the result list at the end | `Collections.reverse(result)` |
| LC 103 Zigzag Level Order | Same BFS + level snapshot | Alternate insertion direction per level | `if (level % 2 == 1) Collections.reverse(levelList)` |
| LC 116 Populate Next Right Pointers | Same level-by-level concept | Link each node to its right neighbor within same level | `node.next = i + 1 < levelSize ? queue.peek() : null` |

---

### WW-4 — LC 543 Diameter of Binary Tree

> **Problem:** Return the length of the longest path between any two nodes (path does not need to pass through the root). Length = number of edges.

**Brute force:** For each node, compute `height(left) + height(right)` by running a separate DFS height query. O(n) per node × n nodes = O(n²).
> **Time:** O(n²) | **Space:** O(h)

**Intuition bridge — what cracks it open:** The brute force recomputes heights redundantly. We already have to compute height in postorder anyway — so compute the diameter candidate at the same node where we compute height, in a single pass. The function returns height (what the parent needs) but tracks `maxDiameter` as a side effect (what we actually want). This is the "two-purpose recursion" pattern.

**Steps in plain English:**

1. **Instance field `maxDiameter = 0`** to track the global answer.
2. **`height(node)` helper** — base case returns 0 for null.
3. **At each node:** compute `lh = height(left)`, `rh = height(right)`. Update `maxDiameter = max(maxDiameter, lh + rh)`.
4. **Return** `1 + max(lh, rh)` — the height for the parent to use.

```java
class Solution {
    private int maxDiameter = 0;

    public int diameterOfBinaryTree(TreeNode root) {
        // Step 1 — reset (instance field persists across LeetCode test cases!)
        maxDiameter = 0;
        height(root);
        return maxDiameter;
    }

    private int height(TreeNode node) {
        // Step 2 — base case
        if (node == null) {
            return 0;
        }
        int lh = height(node.left);
        int rh = height(node.right);
        // Step 3 — candidate diameter through this node
        maxDiameter = Math.max(maxDiameter, lh + rh);
        // Step 4 — return height to parent
        return 1 + Math.max(lh, rh);
    }
}
```

**Time:** O(n) | **Space:** O(h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 687 Longest Univalue Path | Two-purpose: return one-sided, track global | Path only counts edges where `node.val == child.val` | `int l = (node.left != null && node.left.val == node.val) ? lh : 0` |
| LC 1372 Longest ZigZag Path | Two-purpose | Track two heights: going-left and going-right | Return `int[]` with both directions; update global at each node |
| LC 124 Binary Tree Max Path Sum | Exact same skeleton | Metric is sum not length; clip negatives with `Math.max(0, ...)` | See WW-12 |

---

### WW-5 — LC 112 Path Sum

> **Problem:** Given a binary tree and `targetSum`, return true if there exists a root-to-leaf path whose node values sum to `targetSum`.

**Brute force:** Generate all root-to-leaf paths (collect into lists), sum each, check if any equals target. O(n × h) — visits each node and rebuilds path.
> **Time:** O(n × h) | **Space:** O(n × h) for all paths

**Intuition bridge — what cracks it open:** We don't need to build paths. Just carry a "remaining" counter downward: subtract the current node's value at each step. At a leaf, if `remaining == 0`, we found a valid path. This top-down carry eliminates path storage entirely.

**Steps in plain English:**

1. **Base case (null)** — return `false` (walked off the tree).
2. **Subtract current value** from remaining: `remaining -= node.val`.
3. **Leaf check** — if both children are null (we're at a leaf) and `remaining == 0`, return `true`.
4. **Recurse** into left OR right — return true if either finds a path.

```java
public boolean hasPathSum(TreeNode root, int targetSum) {
    // Step 1
    if (root == null) {
        return false;
    }
    // Step 2
    int remaining = targetSum - root.val;
    // Step 3 — leaf check
    if (root.left == null && root.right == null) {
        return remaining == 0;
    }
    // Step 4
    return hasPathSum(root.left, remaining) || hasPathSum(root.right, remaining);
}
```

**Time:** O(n) | **Space:** O(h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 113 Path Sum II | Same top-down carry | Collect ALL matching paths (not just return bool) | See WW-6 |
| LC 437 Path Sum III | Similar path counting | Path can start/end at ANY node, not just root-to-leaf | Prefix sum + HashMap (like LC 560) at each DFS node |
| LC 257 Binary Tree Paths | Same root-to-leaf DFS | Collect all path strings, not sum check | `path += "->" + node.val`; record at leaf |

---

### WW-6 — LC 113 Path Sum II

> **Problem:** Return all root-to-leaf paths where the sum of node values equals `targetSum`.

**Brute force:** Same as WW-5 but collect all paths — unavoidable O(n) scan. The question is whether we build path strings or maintain a running list.
> **Time:** O(n × h) | **Space:** O(n × h)

**Intuition bridge — what cracks it open:** Same top-down carry as LC 112, but now we maintain a `path` list as we descend. At a matching leaf, snapshot the current path into results. The critical discipline: **undo the addition after each recursive call** (backtrack) so the path list is clean for sibling branches.

**Steps in plain English:**

1. **Base case (null)** — return.
2. **Add `node.val` to path**, subtract from remaining.
3. **Leaf + remaining == 0** — snapshot `new ArrayList<>(path)` into results.
4. **Recurse** into left and right children.
5. **UNDO** — remove the last element from path (backtrack).

```java
public List<List<Integer>> pathSum(TreeNode root, int targetSum) {
    List<List<Integer>> results = new ArrayList<>();
    dfs(root, targetSum, new ArrayList<>(), results);
    return results;
}

private void dfs(TreeNode node, int remaining, List<Integer> path, List<List<Integer>> results) {
    // Step 1
    if (node == null) {
        return;
    }
    // Step 2
    path.add(node.val);
    remaining -= node.val;
    // Step 3
    if (node.left == null && node.right == null && remaining == 0) {
        results.add(new ArrayList<>(path));
    }
    // Step 4
    dfs(node.left, remaining, path, results);
    dfs(node.right, remaining, path, results);
    // Step 5 — UNDO (backtrack)
    path.remove(path.size() - 1);
}
```

**Time:** O(n × h) | **Space:** O(n × h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 257 Binary Tree Paths | Same path collection + backtrack | Collect path strings, no sum constraint | Build `path` as `StringBuilder`; record `path.toString()` at leaf |
| LC 1022 Sum of Root to Leaf Numbers | Same root-to-leaf DFS | Sum treated as binary number (shift left + add bit) | `remaining = remaining * 2 + node.val` |
| LC 988 Smallest String from Leaf | Same DFS to leaf | Build string by prepending chars; compare at backtrack | Build in reverse; compare at each leaf against global min |

---

### WW-7 — LC 199 Binary Tree Right Side View

> **Problem:** Return the values of nodes visible when looking at the tree from the right side (one value per level — the rightmost node at each depth).

**Brute force:** BFS level order (WW-3 template), take the last element of each level list. O(n). Clean and correct — but uses O(n) queue space.
> **Time:** O(n) | **Space:** O(n)

**Intuition bridge — what cracks it open:** DFS preorder (root → right → left) visits the rightmost node of each depth first. When `depth == result.size()`, we're visiting that depth for the first time — add the value. This avoids the queue entirely and uses O(h) space.

**Steps in plain English:**

1. **DFS preorder** — visit right child before left child.
2. **At each node:** if `depth == result.size()`, this is the first (= rightmost) node at this depth — add to result.
3. **Recurse right** first (so rightmost is always seen first), then left.

```java
public List<Integer> rightSideView(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    dfs(root, 0, result);
    return result;
}

private void dfs(TreeNode node, int depth, List<Integer> result) {
    if (node == null) {
        return;
    }
    // Step 2 — first visit to this depth = rightmost node
    if (depth == result.size()) {
        result.add(node.val);
    }
    // Step 3 — right before left ensures rightmost is seen first
    dfs(node.right, depth + 1, result);
    dfs(node.left, depth + 1, result);
}
```

**Time:** O(n) | **Space:** O(h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 515 Find Largest Value in Each Row | Same DFS depth tracking | Track max per depth, not first-seen | `if (depth == result.size()) result.add(node.val); else result.set(depth, Math.max(...))` |
| LC 623 Add One Row to Tree | Same level-awareness | Modify tree at target depth instead of query | When `depth == d-1`, replace children with new nodes wrapping old subtrees |
| LC 662 Maximum Width of Binary Tree | Same level-based BFS | Track leftmost/rightmost node indices per level | Assign position indices `2*i`, `2*i+1`; width = rightmost - leftmost + 1 |

---

### WW-8 — LC 572 Subtree of Another Tree

> **Problem:** Given trees `root` and `subRoot`, return true if `subRoot` is a subtree of `root` (there exists a node in `root` whose subtree is identical to `subRoot`).

**Brute force:** For every node in `root`, run a full `isSameTree` check. O(m × n) where m = nodes in root, n = nodes in subRoot.
> **Time:** O(m × n) | **Space:** O(h)

**Intuition bridge — what cracks it open:** Build a helper `isSameTree(a, b)` first — it's 3 lines. Then the main function calls `isSameTree(node, subRoot)` at every node in root. Two clean recursive functions, clearly separated. The key insight: the answer is true if the current node matches, OR either subtree contains a match.

**Steps in plain English:**

1. **`isSameTree(a, b)`:** both null → true; one null → false; `a.val != b.val` → false; else check both children recursively.
2. **`isSubtree(root, sub)`:** null root → false; `isSameTree(root, sub)` → return true; else recurse into left and right.

```java
public boolean isSubtree(TreeNode root, TreeNode subRoot) {
    // Step 2
    if (root == null) {
        return false;
    }
    if (isSameTree(root, subRoot)) {
        return true;
    }
    return isSubtree(root.left, subRoot) || isSubtree(root.right, subRoot);
}

private boolean isSameTree(TreeNode a, TreeNode b) {
    // Step 1
    if (a == null && b == null) {
        return true;
    }
    if (a == null || b == null) {
        return false;
    }
    if (a.val != b.val) {
        return false;
    }
    return isSameTree(a.left, b.left) && isSameTree(a.right, b.right);
}
```

**Time:** O(m × n) | **Space:** O(h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 100 Same Tree | `isSameTree` is exactly the solution | No outer traversal needed — compare two complete trees | Just call `isSameTree(p, q)` directly |
| LC 652 Find Duplicate Subtrees | Same "check structural equality" concept | Serialize each subtree; find duplicates via HashMap | `String serial = serialize(node); map.merge(serial, 1, Integer::sum)` |
| LC 1367 Linked List in Binary Tree | Same nested recursion | Match a linked list as a downward path, not a full subtree | `isMatch(listNode, treeNode)` called at every tree node |

---

### WW-9 — LC 236 Lowest Common Ancestor of Binary Tree

> **Problem:** Find the lowest common ancestor (LCA) of nodes `p` and `q` in a binary tree. The LCA is the deepest node that has both `p` and `q` as descendants (a node is a descendant of itself).

**Brute force:** For each node, check if `p` is in its subtree AND `q` is in its subtree — if yes, it's a candidate for LCA. The deepest such node is the answer. O(n²) — each subtree-membership check costs O(n).
> **Time:** O(n²) | **Space:** O(h)

**Intuition bridge — what cracks it open:** The function can carry multiple meanings in its return value: `null` (nothing found here), `p` (found p), `q` (found q), or the LCA itself. When both left AND right return non-null, this node is the LCA. This collapses the two-pass brute force into a single postorder pass — the recursive return IS the signal.

**Steps in plain English:**

1. **Base case** — if `null`, return `null`. If `root == p` or `root == q`, return `root` (signal: found one of them).
2. **Recurse** into left and right; collect the signals.
3. **Both non-null** — `p` and `q` are on different sides; this node IS the LCA. Return `root`.
4. **One non-null** — propagate whichever side found something. Return `left != null ? left : right`.

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    // Step 1
    if (root == null || root == p || root == q) {
        return root;
    }
    // Step 2
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    // Step 3
    if (left != null && right != null) {
        return root;
    }
    // Step 4
    return left != null ? left : right;
}
```

**Time:** O(n) | **Space:** O(h)

### 🎨 Visual — LCA three cases: what bubbles up from each subtree

```
Return value meaning:  null = nothing found  /  p or q = found one  /  <node> = LCA

CASE A — p and q on different sides of a node → that node IS the LCA
                   (3) ◀── LCA
                  /   \
          left=(5)     (1)=right
                / \    / \
              (p) ... ... (q)
  At node 3: left=p (non-null) AND right=q (non-null) → return 3

CASE B — p and q both under one child → deeper node is LCA
                   (3)
                  /   \
          left=(5)     (1)=right
                / \
              (p) (q)
  At node 5: left=p, right=q → both non-null → return 5
  At node 3: left=5 (the LCA), right=null → propagate 5 upward

CASE C — p is an ancestor of q → p IS the LCA
                   (3)
                  /
                (p)   ◀── base case fires here; return p immediately
                / \
               ... (q)
  At node 3: left=p (non-null), right=null → propagate p upward ✓

KEY INVARIANT:
  The function returns exactly one of: null / p / q / the LCA.
  When both children return non-null, the CURRENT node is the meeting point.
  This single-return-value design eliminates any need for an instance field.
```

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 235 LCA of BST | Same 3-case logic | BST property — no need to search both subtrees | `if (p.val < root.val && q.val < root.val) recurse left; else if both > root.val recurse right; else return root` |
| LC 1650 LCA with Parent Pointers | Same "find meeting point" goal | Walk up from both nodes using `.parent` — two-pointer on linked list | `Set<TreeNode> ancestors; walk p up; walk q up until seen` |
| LC 1123 Deepest Leaves LCA | Same LCA pattern | Restrict to deepest leaves only | Track depth; LCA only counts when both subtrees reach max depth |

---

### WW-10 — LC 98 Validate Binary Search Tree

> **Problem:** Given the root of a binary tree, determine if it is a valid BST (left subtree values < root, right subtree values > root, recursively).

**Brute force:** Inorder traversal; collect all values into a list; verify the list is strictly increasing. O(n) time, O(n) space.
> **Time:** O(n) | **Space:** O(n) for the list

**Intuition bridge — what cracks it open:** The inorder approach works but allocates an array. The bounds approach is more direct: each node must lie strictly within a `(min, max)` window inherited from its ancestors. Turning left narrows the upper bound; turning right narrows the lower bound. No array needed — pass bounds as parameters.

**Steps in plain English:**

1. **Helper `validate(node, min, max)`** — root call with `(-∞, +∞)`.
2. **Base case** — null node is valid, return true.
3. **Bounds check** — if `node.val <= min` or `node.val >= max`, invalid.
4. **Recurse** — left with `(min, node.val)`, right with `(node.val, max)`.

```java
public boolean isValidBST(TreeNode root) {
    // Step 1
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    // Step 2
    if (node == null) {
        return true;
    }
    // Step 3 — strict bounds check
    if (node.val <= min || node.val >= max) {
        return false;
    }
    // Step 4 — narrow window for each child
    return validate(node.left, min, node.val) &&
           validate(node.right, node.val, max);
}
```

**Time:** O(n) | **Space:** O(h)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 700 Search in BST | Same BST property navigation | Return the node, not bool | `if (val < root.val) return searchBST(root.left, val); else return searchBST(root.right, val)` |
| LC 701 Insert into BST | Same left/right routing | Attach new node at the right null leaf | `if (root == null) return new TreeNode(val)` |
| LC 669 Trim BST | Same bounds-aware recursion | Remove nodes outside `[low, high]` | `if (root.val < low) return trimBST(root.right, low, high)` |

---

### WW-11 — LC 105 Construct Binary Tree from Preorder and Inorder Traversal

> **Problem:** Given `preorder[]` and `inorder[]` of a tree (no duplicate values), reconstruct and return the binary tree.

**Brute force:** For each preorder element (root of a subtree), scan the full inorder array to find the root's position. Split around it; recurse. O(n²) due to linear scan at each level.
> **Time:** O(n²) | **Space:** O(n)

**Intuition bridge — what cracks it open:** Preorder's first element is always the current subtree's root. In inorder, everything to the left of that root is the left subtree; everything to the right is the right subtree. A HashMap caches each value's inorder index for O(1) lookup — turning O(n²) into O(n).

**Steps in plain English:**

1. **Build `inorderIndex` HashMap** — value → index in inorder array.
2. **Recursive `build(preStart, inStart, inEnd)`:** if range is empty, return null.
3. **Root** = `preorder[preStart]`. Find root's inorder index; compute left subtree size.
4. **Left child** = `build(preStart+1, inStart, rootIdx-1)`.
5. **Right child** = `build(preStart+1+leftSize, rootIdx+1, inEnd)`.

```java
public TreeNode buildTree(int[] preorder, int[] inorder) {
    // Step 1
    Map<Integer, Integer> inorderIndex = new HashMap<>();
    for (int i = 0; i < inorder.length; i++) {
        inorderIndex.put(inorder[i], i);
    }
    return build(preorder, 0, 0, inorder.length - 1, inorderIndex);
}

private TreeNode build(int[] preorder, int preStart, int inStart, int inEnd,
                       Map<Integer, Integer> inorderIndex) {
    // Step 2
    if (inStart > inEnd) {
        return null;
    }
    // Step 3
    int rootVal = preorder[preStart];
    int rootIdx = inorderIndex.get(rootVal);
    int leftSize = rootIdx - inStart;
    TreeNode root = new TreeNode(rootVal);
    // Step 4
    root.left = build(preorder, preStart + 1, inStart, rootIdx - 1, inorderIndex);
    // Step 5
    root.right = build(preorder, preStart + 1 + leftSize, rootIdx + 1, inEnd, inorderIndex);
    return root;
}
```

**Time:** O(n) | **Space:** O(n)

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 106 Build from Postorder + Inorder | Same split logic | Postorder root is the LAST element, not first | `int rootVal = postorder[postEnd]`; recurse accordingly |
| LC 108 Convert Sorted Array to BST | Same recursive halving | Sorted array — middle is root; left/right halves are subtrees | `int mid = (left + right) / 2; root = new TreeNode(nums[mid])` |
| LC 889 Build from Preorder + Postorder | Same structure | Ambiguous (not unique) — left child's root is `preorder[1]` | Find `preorder[preStart+1]` in postorder to get left subtree size |

---

### WW-12 — LC 124 Binary Tree Maximum Path Sum 🔴 Reference Only

> 🔴 **DO NOT attempt cold.** This requires three intuitions built by the ladder below. Attempting without them means writing code that compiles, passes simple cases, and fails on `[-3]` or `[2, -1, -2]`.

> **Problem:** Find the maximum sum of any path in the tree. A path can start and end at any node; it can "bend" at exactly one node (using both left and right children) but cannot revisit any node.

**Brute force:** For each node, consider all paths passing through it — left subtree path + node + right subtree path. Recompute heights for each. O(n²).
> **Time:** O(n²) | **Space:** O(h)

**Intuition bridge — what cracks it open:** Three observations combined: (1) a "bent" path through node uses both children but can't be extended to the parent — so track it as global candidate only; (2) to a parent, only a single-sided extension is useful; (3) a negative subtree is worse than skipping it entirely — clip with `max(0, ...)`. This is WW-4 (Diameter) but with sum instead of length, plus the clipping.

**Steps in plain English:**

1. **Instance field `maxSum = Integer.MIN_VALUE`** — reset in public method (never static, never start at 0).
2. **`gain(node)` helper:** base case returns 0 for null.
3. **Clip children:** `leftGain = max(0, gain(left))`, `rightGain = max(0, gain(right))`.
4. **Update global:** `maxSum = max(maxSum, node.val + leftGain + rightGain)` — the bent path candidate.
5. **Return to parent:** `node.val + max(leftGain, rightGain)` — single-sided extension only.

```java
class Solution {
    private int maxSum;

    public int maxPathSum(TreeNode root) {
        // Step 1 — instance field, reset here
        maxSum = Integer.MIN_VALUE;
        gain(root);
        return maxSum;
    }

    private int gain(TreeNode node) {
        // Step 2
        if (node == null) {
            return 0;
        }
        // Step 3 — clip negatives: a negative subtree is worse than skipping it
        int leftGain = Math.max(0, gain(node.left));
        int rightGain = Math.max(0, gain(node.right));
        // Step 4 — bent path through this node (global candidate only)
        maxSum = Math.max(maxSum, node.val + leftGain + rightGain);
        // Step 5 — single-sided extension returned to parent
        return node.val + Math.max(leftGain, rightGain);
    }
}
```

**Time:** O(n) | **Space:** O(h)

#### 🐞 Three bugs Kapil hit on first attempt (May 2026)

| Bug | Symptom | Fix |
| --- | --- | --- |
| `private static int maxSum = 0` | Wrong on `[-3]`; carries state across LC test cases | Instance field + reset to `Integer.MIN_VALUE` in public method |
| No `max(0, ...)` clipping | Wrong on `[2, -1, -2]` — includes harmful children | `leftGain = Math.max(0, gain(node.left))` |
| Returning `node.val + leftGain + rightGain` to parent | Path revisits node — illegal | Return `node.val + Math.max(leftGain, rightGain)` (one side only) |

#### 🪜 Build-up ladder before attempting LC 124

| Step | Problem | New concept |
| --- | --- | --- |
| 1 | LC 104 Max Depth | Postorder combine |
| 2 | LC 543 Diameter | Two-purpose recursion — return height, track global |
| 3 | LC 687 Longest Univalue Path | Two-purpose + value-matching condition |
| 4 | LC 124 Max Path Sum | Two-purpose + negative clipping |

**Transfers to:**

| Problem | What's identical | ONE thing different | Key line that changes |
| --- | --- | --- | --- |
| LC 543 Diameter | Same two-purpose skeleton | Metric is length (edges), no clipping needed | Remove `max(0, ...)` clips; `lh + rh` replaces `val + lGain + rGain` |
| LC 1245 Longest Path in DAG | Same "bent path" global tracking | DAG not tree — topological order, not recursion | BFS topo sort + DP on `dp[node]` |
| LC 2246 Longest Path with Diff Adjacent Values | Same two-purpose on tree | Only extend child if `child.val != parent.val` | `if (node.children.get(i).val != node.val) extend` |

---

> Swap the left and right children of every node.

**Input:**
```
        4
       / \
      2   7
     /\   /\
    1  3 6  9
```

**Output:**
```
        4
       / \
      7   2
     /\   /\
    9  6 3  1
```

**Solution — postorder + swap:**

**Steps in plain English:**

1. **Base case** — if the node is `null`, return `null` (nothing to invert).
2. **Recurse into left subtree** — trust the recursion to fully invert it. Save the **returned** root of the (now inverted) left subtree into a local variable.
3. **Recurse into right subtree** — same idea: fully inverted, save the returned root.
4. **Swap the children** — assign the (inverted) right subtree to `root.left`, and the (inverted) left subtree to `root.right`. This is the actual inversion at the current level.
5. **Return the (now-fully-inverted) root** to the parent so it can do its own swap.

```java
public TreeNode invertTree(TreeNode root) {
    // Step 1 — base case
    if (root == null) {
        return null;
    }

    // Step 2 — left subtree fully inverted
    TreeNode left = invertTree(root.left);

    // Step 3 — right subtree fully inverted
    TreeNode right = invertTree(root.right);

    // Step 4 — swap them at this level
    root.left = right;
    root.right = left;

    // Step 5 — return the (now-inverted) subtree to the parent
    return root;
}
```

**Why this works:** by the time we swap at node `4`, both subtrees are already inverted. Each call returns the inverted subtree to its parent.

---

### Walkthrough 2: Lowest Common Ancestor of Binary Tree (LC 236)

> Find the deepest node that has both `p` and `q` in its subtree.

**Strategy:** at each node ask, *"is `p` or `q` found in my left? In my right? Or am I one of them?"*

- If both sides have a match → **this node** is the LCA
- If only one side has a match → propagate that match upward
- If neither side has a match → return null

**Steps in plain English:**

1. **Base case** — if the node is `null` (we walked off the tree) OR if it equals `p` or `q` (we found one of the targets), return that node. The returned reference is a "signal" — it carries `null` ("nothing found here"), `p`, `q`, or the LCA upward.
2. **Recurse into both subtrees** — the left call returns whatever it found (a target, the LCA, or `null`). Same for the right call.
3. **Both sides found something → this node is the LCA.** If left returned non-null AND right returned non-null, then `p` and `q` are split between this node's two subtrees — by definition, the **current node** is their lowest common ancestor.
4. **Only one side found something → propagate that finding upward.** If only one of left/right is non-null, we haven't found the LCA yet; just pass the found target (or LCA from below) up so a higher ancestor can use it.

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    // Step 1 — base case: empty OR found p OR found q
    if (root == null || root == p || root == q) {
        return root;
    }

    // Step 2 — search both subtrees
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);

    // Step 3 — both sides reported a hit → THIS node is the LCA
    if (left != null && right != null) {
        return root;
    }

    // Step 4 — propagate whichever side has a finding (or null if neither)
    return left != null ? left : right;
}
```

### 🎨 Visual — LCA Three Cases (what bubbles up from each subtree)

```
The recursive return value carries one of FOUR meanings depending on
what was found in the subtree below:

      null    →  nothing found here
      p       →  this subtree contains p (or this IS p)
      q       →  this subtree contains q (or this IS q)
     <node>   →  this subtree's LCA is <node>; just bubble it up


────────────────────────────────────────────────────────────────────
CASE A — p and q on DIFFERENT sides of a node ⇒ that node IS the LCA
────────────────────────────────────────────────────────────────────

                       (3) ◀── LCA is here
                      /   \
              left = (5)   (1) = right
                    / \    / \
                  (p) ... ... (q)

   At node 3:   left  recursion returns  p   (non-null)
                right recursion returns  q   (non-null)
                BOTH non-null  ⇒  return root  (= 3, the LCA)

   Above node 3, every ancestor sees:
       left =  3   (the LCA we just found, NOT p or q)
       right = null
   So the LCA  3  is propagated upward unchanged.


────────────────────────────────────────────────────────────────────
CASE B — p and q on the SAME side ⇒ deeper node is the LCA
────────────────────────────────────────────────────────────────────

                       (3)
                      /   \
              left = (5)   (1) = right
                    / \
                  (p) (q)        ◀── both under 5

   At node 5:   left  returns  p
                right returns  q
                BOTH non-null  ⇒  node 5 becomes the LCA  ✅

   At node 3:   left  returns  5  (the LCA)
                right returns  null
                One side non-null ⇒ propagate  5  upward.


────────────────────────────────────────────────────────────────────
CASE C — one of p, q IS the ancestor of the other
────────────────────────────────────────────────────────────────────

                       (3)
                      /   \
                    (p)    ...   ◀── p is itself the ancestor
                    / \
                  ... (q)

   At node p:   the BASE CASE fires (root == p) ⇒ return p immediately.
                We do NOT descend further looking for q.
   At node 3:   left  returns  p
                right returns  null
                One side non-null ⇒ propagate  p  upward.
                p IS the LCA — and the algorithm returns it correctly.

   This works because the contract is: "return either p, q, or the LCA."
   When p is an ancestor of q, p satisfies BOTH meanings — and that's fine.
```

**Why this is elegant:** the function returns either `null`, `p`, `q`, or the LCA — and the meaning depends on context. This is a classic "two-purpose recursion."

> 🐞 **Common mistake on LC 236 — read this BEFORE attempting:**
>
> The natural-but-wrong first attempt is to return a **`boolean`** ("did I find p or q in this subtree?") and use an **instance field** as a side-channel to capture the LCA. It *works*, but bloats a 9-line solution into 40+ lines:
>
> ```java
> // ❌ Bloated — returns boolean, side-channel via instance field
> private TreeNode lca;
> // ...
> public boolean find(TreeNode root, TreeNode p, TreeNode q) {
>     if (root == null) return false;
>     boolean isLeft = find(root.left, p, q);
>     boolean isRight = find(root.right, p, q);
>     if (isLeft && isRight) { lca = root; return true; }
>     if (isLeft || isRight) {
>         if (root.val == p.val || root.val == q.val) {
>             lca = root;
>         }
>         return true;
>     }
>     return root.val == p.val || root.val == q.val;
> }
> ```
>
> **Why it bloats:** you're juggling **two signals** (boolean return + side-channel field) when the recursive return can carry everything by itself. The canonical solution overloads the **`TreeNode` return** to mean *"null = nothing found / p or q = found one of them / any other node = the LCA itself."* One signal, no instance field, no helper method, no boolean wrapper.
>
> **Three specific fixes if you wrote the bloated version:**
> 1. **Return `TreeNode`, not `boolean`.** Drop the instance field. The recursive return IS the answer.
> 2. **Compare by reference, not value** — `root == p`, not `root.val == p.val`. LeetCode hands you the node references; use them. Also faster, also robust to value duplicates in variants like LC 1644.
> 3. **No helper method.** The public method itself can be the recursive function — its signature already matches the canonical shape.
>
> **A related confusion — "why do I need null checks if the problem guarantees p and q exist in the tree?"**
>
> The `null` checks in LC 236 aren't about p/q being absent. There are two distinct nulls in play:
>
> | Where | What it means | Still needed? |
> | --- | --- | --- |
> | `if (root == null) return root;` (base case) | Walked off the tree (recursed into a missing child) | ✅ Always — independent of whether p/q exist |
> | `if (left != null && right != null)` | This subtree contained both p and q | ✅ Always — even when p/q exist in the tree, **most subtrees** don't contain either |
> | `return left != null ? left : right;` | Propagate whichever side found something | ✅ Always |
>
> Most subtrees in any tree don't contain p or q. The null returns are how the algorithm tracks that. Removing them breaks the logic — they're load-bearing, not defensive.
>
> **Mental anchor:** *"Can the answer ride on the recursive return value itself?"* — when the answer is a node, the return type is `TreeNode`. When the answer is a number, the return is `int`. The instance-field side-channel (Bug 10 pattern) is only needed when the recursive return and the global answer are **different quantities** (LC 543 returns height, but the answer is diameter; LC 124 returns one-sided sum, but the answer is bent-path sum). LC 236 isn't like that — the recursive return *is* the answer.

> 🧩 **Try these:**
> - ✅ LC 235 LCA of BST — easier than LC 236; just walk down comparing `p.val`/`q.val` to `node.val`. **Start here.**
> - 🟡 **Try after LC 235** — LC 236 LCA of Binary Tree (the walkthrough above is the answer; rewrite it without looking)
> - 🔴 LC 1644 LCA II — handle the case where p or q may not exist in the tree (extra accounting). Variant; do after LC 236 is solid.
> - 🔴 LC 1650 LCA III — each node has a parent pointer; technique flips to "two-pointer in linked-list" style. Different problem; save for later.

---

### Walkthrough 3: Binary Tree Maximum Path Sum (LC 124) 🔴 Reference Only

> 🔴 **READ THIS FIRST — Do NOT attempt cold.** This is the textbook "two-purpose recursion" problem. It looks like a normal tree problem but **silently demands three intuitions** that don't exist in the simpler problems above:
>
> 1. **Negative-clipping intuition** — knowing to do `Math.max(0, recurse(child))` (skip subtrees that hurt you)
> 2. **Path-shape awareness** — understanding that a "bent" path through this node uses **both** children, but the path you can extend to your parent uses **only one** child
> 3. **Global-vs-return separation** — the function must update an outer max **and** return a different (smaller) value to its caller
>
> Without those three, you'll write something that compiles, passes a couple of test cases, and fails on `[-3]` or `[2, -1, -2]`. That's exactly the trap I fell into — it cost me an hour. **Read this section for understanding, but only attempt the LeetCode submission after you've finished the "Building Up to Two-Purpose Recursion" ladder below.**

**Problem:** Find the maximum sum of any path. A "path" is any sequence of connected nodes — it doesn't have to start at root or end at leaf, and it **can bend at exactly one node** (using both that node's left and right subtrees), but a path **cannot revisit a node**.

This is iconic. It's the textbook example of "two-purpose recursion."

```java
class Solution {
    private int maxSum;

    public int maxPathSum(TreeNode root) {
        maxSum = Integer.MIN_VALUE;            // ← reset on every call (LeetCode reuses class!)
        gain(root);
        return maxSum;
    }

    private int gain(TreeNode node) {
        if (node == null) {
            return 0;
        }
        int leftGain = Math.max(0, gain(node.left));   // ignore negative — better to skip
        int rightGain = Math.max(0, gain(node.right));

        int withBend = node.val + leftGain + rightGain;  // path THROUGH this node, using both kids
        maxSum = Math.max(maxSum, withBend);             // candidate for global answer

        return node.val + Math.max(leftGain, rightGain); // to parent: path EXTENDING upward (one side only)
    }
}
```

**Why two purposes?**
- We track the global maximum (`maxSum`) considering paths that **bend at this node** (use both children)
- We return to the parent the best **one-sided** path (a parent can only extend through one child — extending through both would revisit the current node, which paths can't do)

**Why `Math.max(0, ...)` clipping?** A subtree with a negative best-path is pure damage. If `leftGain` is `-5`, including it makes any path through me worse. So we floor every gain at `0` — meaning *"if this subtree only hurts me, pretend it doesn't exist."*

**Why `maxSum` starts at `Integer.MIN_VALUE`, not `0`?** Trees can be entirely negative (e.g., `[-3]`). The answer might be `-3`. Starting at `0` would wrongly return `0` for that input.

---

#### 🐞 Common Bugs in LC 124 (lessons from real attempts)

When I (Kapil) attempted this in May 2026, my solution had **three bugs** — all of which look correct on paper. Watch for these:

**Bug 1 — `static` field that persists across LeetCode test cases**

```java
// ❌ Wrong
class Solution {
    private static int max = 0;     // STATIC + initialized to 0

    public int maxPathSum(TreeNode root) {
        dfs(root);
        return max;
    }
    // ...
}
```

Two failures hidden here:
1. **`static`** means the field lives on the class, not the instance. LeetCode's grader **reuses the same `Solution` class across test cases**, so `max` carries leftover state from the previous test. A clean run on test #1 can poison test #2.
2. **`= 0`** as the initial value silently fails on all-negative trees like `[-3]`, where the correct answer is `-3` but your code returns `0` because every gain gets clipped and `max` was never updated.

**Fix:** use a non-static instance field, **and** reset it at the start of every `maxPathSum` call: `maxSum = Integer.MIN_VALUE;`

**Bug 2 — Always summing both children (no negative clipping)**

```java
// ❌ Wrong
int leftSum = dfs(node.left);
int rightSum = dfs(node.right);
max = Math.max(max, node.val + leftSum + rightSum);  // includes both, even if negative
```

Fails on `[2, -1, -2]`. The optimal path is just `[2]` (sum = 2), but this code computes `2 + (-1) + (-2) = -1` and returns `-1`. The fix is `Math.max(0, dfs(node.left))` — clip the negative subtree before using it.

**Bug 3 — Returning the bent path to the parent**

```java
// ❌ Wrong
return node.val + leftSum + rightSum;     // tries to "give" the bent path upward
```

A path **cannot revisit a node**. If I'm at node `2` and I tell my parent `1` that my best gain is `2 + leftSum + rightSum`, then my parent might extend that into a path `1 → 2 → leftChild` AND `1 → 2 → rightChild` — which goes through `2` twice. Illegal.

**Fix:** the bent path is only ever a **candidate for the global answer**. To the parent, we return only **one-sided** gain: `node.val + Math.max(leftGain, rightGain)`.

> **TL;DR of the three bugs:**
> | Bug | Symptom | Fix |
> | --- | --- | --- |
> | `static int max = 0` | Wrong answer on `[-3]`; flaky across multi-test-case runs | Instance field + reset to `Integer.MIN_VALUE` in the public method |
> | No negative clipping | Wrong on `[2,-1,-2]` | `Math.max(0, gain(child))` |
> | Returning the bent path | Wrong on bent paths through interior nodes | Return one-sided: `node.val + Math.max(leftGain, rightGain)` |

---

#### 🪜 Building Up to Two-Purpose Recursion (the actual study path)

Don't jump to LC 124. Climb this ladder — each step adds one new idea while keeping the rest familiar:

| Step | Problem | New idea introduced | Try-now? |
| --- | --- | --- | --- |
| 1 | **LC 104** Maximum Depth | Bottom-up recursion + combine via `1 + max(L, R)` | ✅ |
| 2 | **LC 110** Balanced Binary Tree | Bottom-up + early-termination sentinel (`-1` means "fail propagating up") | ✅ |
| 3 | **LC 543** Diameter of Binary Tree | **Two-purpose recursion introduced**: function returns height, but also updates a global `diameter`. Path *through this node* uses both kids; height returned to parent uses one side. | 🟡 — try after step 2 clicks |
| 4 | **LC 687** Longest Univalue Path | Same shape as LC 543, but the "path" only counts edges where parent.val == child.val. Reinforces two-purpose recursion + adds a value-matching condition. | 🔴 — try after step 3 is solid |
| 5 | **LC 124** Maximum Path Sum | Same shape as LC 543, but the metric is **sum** (not length), so **negative subtrees can hurt** → introduces `Math.max(0, ...)` clipping. | 🔴 — try after steps 3 + 4 |

> **Pattern progression in plain English:**
> - **LC 104** teaches *"recurse and combine."*
> - **LC 110** teaches *"the return value can also signal failure."*
> - **LC 543** teaches *"a recursion can produce two answers — one I keep, one I send up."*
> - **LC 687** teaches *"the recursion can also depend on parent-child equality."*
> - **LC 124** teaches *"sometimes the better choice is to skip a subtree entirely (clip negatives)."*
>
> If you try LC 124 before step 3, you're trying to invent two-purpose recursion AND negative-clipping in the same hour. It is doable. It is also brutal. Do them one at a time.

---

> 🧩 **Try these (after the ladder above):**
> - 🟡 LC 543 Diameter — **the right introductory two-purpose problem**. Do this before LC 124.
> - 🔴 LC 124 Max Path Sum — only after LC 543 + LC 687
> - 🔴 LC 687 Longest Univalue Path — variant of LC 543 with value-matching condition

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

**Forgetting the null base case** — easiest way to crash with NPE.

```java
// ❌ NPE when root is null
return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));

// ✅ always check first
if (root == null) {
    return 0;
}
```

---

**Using `static` fields in LeetCode solutions** — a subtle but devastating trap on multi-test-case judges.

```java
// ❌ persists across test cases — test #1 leaves state behind, test #2 inherits it
class Solution {
    private static int maxSum = Integer.MIN_VALUE;
    public int maxPathSum(TreeNode root) { ... }
}

// ✅ instance field, AND reset at the top of the public method
class Solution {
    private int maxSum;
    public int maxPathSum(TreeNode root) {
        maxSum = Integer.MIN_VALUE;     // fresh start every call
        ...
    }
}
```

**Why:** LeetCode reuses the same `Solution` class across test cases in a single submission. A `static` field is shared — so a call that sets `maxSum = 50` on test #1 means test #2 starts with `maxSum = 50`, not `Integer.MIN_VALUE`. The fix is to use an **instance field and explicitly reset it** at the top of the public entry-point method.

> **Universal rule:** in LeetCode, default to **instance fields** for "global state during recursion," and **reset them in the public method**. Reach for `static` only if you genuinely need cross-instance state (you won't, in a typical interview problem).

---

**Validating BST with `int` bounds fails on `Integer.MIN_VALUE` / `MAX_VALUE`.** Use `long` bounds, or null-pointer bounds, or the inorder approach. See LC 98 above.

---

**Counting depth in nodes vs edges** — different problems use different conventions.

```java
// LC 104 Maximum Depth — counted in NODES (single root → depth 1)
return 1 + Math.max(left, right);

// Some problems define depth as EDGES (single root → depth 0)
// Read the problem statement carefully
```

---

**Modifying a tree while traversing it** can cause infinite loops or NPE. Compute first, then mutate (or use careful return-value plumbing as in LC 226 invert).

---

**Returning early from recursion missed.** When searching ("does X exist?"), you must propagate `true` upward as soon as it's found. Don't keep searching after finding.

```java
// ❌ wastes time after finding
boolean leftFound = hasPathSum(node.left, target - node.val);
boolean rightFound = hasPathSum(node.right, target - node.val);
return leftFound || rightFound;

// ✅ short-circuits — returns true as soon as either branch returns true
return hasPathSum(node.left, target - node.val)
    || hasPathSum(node.right, target - node.val);
```

> The second form leverages Java's `||` short-circuit — if `left` returns true, `right` is never called. Subtle but matters for very large trees.

---

**Auto-unboxing in Map<TreeNode, Integer> when key not present.**

```java
Map<TreeNode, Integer> depth = new HashMap<>();
int d = depth.get(root);                            // NPE if root not in map
int d = depth.getOrDefault(root, 0);                // safe
```

---

**Confusing preorder, inorder, postorder during interviews.** Memorize the position of `print(node.val)`:
- **Pre** — first
- **In** — middle
- **Post** — last

---

**Using `LinkedList` for BFS queue.** Slower than `ArrayDeque`. Use `ArrayDeque` everywhere.

```java
Queue<TreeNode> queue = new ArrayDeque<>();         // ✅
Queue<TreeNode> queue = new LinkedList<>();         // works but slow
```

---

**Forgetting `level.size()` snapshot in BFS** — when you add children inside the loop, `queue.size()` keeps growing. You must capture it once **before** the inner loop.

```java
// ❌ infinite or wrong levels
while (!queue.isEmpty()) {
    for (int i = 0; i < queue.size(); i++) {        // size grows as we add!
        ...
    }
}

// ✅ snapshot first
while (!queue.isEmpty()) {
    int size = queue.size();
    for (int i = 0; i < size; i++) {
        ...
    }
}
```

---

## 🗺️ Practice Plan — A Progression That Works

Don't try to solve all of these in one sitting. Spread over 2–3 weeks. Do each problem **once on your own with a 25-minute time-box**, then review the optimal solution. **Climb the tiers in order** — each tier assumes you've internalized the one before it.

> **Reminder of tag meanings:** ✅ Try Now · 🟡 Try after the named prerequisite · 🔴 Reference Only (read for awareness, don't attempt cold)

---

### Tier 1 — Foundational 7 (must be muscle memory before anything else)

These are the seven you should be able to solve **from memory in under 15 minutes each.** Drill them. Re-drill them. Don't move past Tier 1 until they feel boring.

1. ✅ **LC 104** Maximum Depth — bottom-up recursion + combine
2. ✅ **LC 100** Same Tree — parallel recursion
3. ✅ **LC 226** Invert Binary Tree — postorder swap
4. ✅ **LC 101** Symmetric Tree — mirror-axis parallel recursion
5. ✅ **LC 110** Balanced Binary Tree — postorder + early-termination sentinel
6. ✅ **LC 102** Level Order Traversal — BFS template + size-snapshot
7. ✅ **LC 112** Path Sum — top-down DFS carrying remaining target

> If you can write any of these without thinking, your **base recursion + BFS muscle is in place**. That's 80% of "easy" tree interviews.

---

### Tier 2 — Direct extensions (each adds one small new idea)

Most of these are one-liner tweaks of Tier 1. Don't be intimidated.

8. ✅ **LC 144** Preorder Traversal (recursive) — order practice
9. ✅ **LC 145** Postorder Traversal (recursive) — order practice
10. ✅ **LC 94** Inorder Traversal (recursive) — order practice + BST primer
11. ✅ **LC 107** Level Order Traversal II — LC 102 then `Collections.reverse`
12. ✅ **LC 199** Right Side View — only the **last** node per level
13. ✅ **LC 515** Largest Value in Each Row — track max per level
14. ✅ **LC 103** Zigzag Level Order — alternate L→R / R→L per level
15. ✅ **LC 1161** Maximum Level Sum — track sum + level number
16. ✅ **LC 572** Subtree of Another Tree — compose `isSameTree` with a top-down scan

---

### Tier 3 — BST core

Do these **after Tier 1+2.** You need the BST invariant in your head before attempting any of these. Read the BST section above twice.

17. ✅ **LC 700** Search in BST — direct application of BST property
18. ✅ **LC 938** Range Sum of BST — BST property + pruning
19. ✅ **LC 270** Closest Value in BST — walk down with best-so-far
20. ✅ **LC 235** LCA of BST — easier than LC 236; use BST property
21. ✅ **LC 98** Validate BST — pick **either** the bounds approach or inorder approach. **The classic trap; do this once each way.**
22. ✅ **LC 530** Min Absolute Difference in BST — inorder + `prev` pointer
23. 🟡 **LC 230** Kth Smallest in BST (after LC 98 inorder version) — inorder + counter
24. 🟡 **LC 701** Insert into BST (after LC 700) — recurse, attach a new node
25. 🟡 **LC 236** LCA of Binary Tree (after LC 235) — propagate match upward

---

### Tier 4 — Top-Down DFS family (carry state down)

26. 🟡 **LC 1448** Count Good Nodes (after LC 112) — carry running max down
27. 🟡 **LC 113** Path Sum II (after LC 1448) — collect all root-to-leaf paths via backtracking
28. 🟡 **LC 129** Sum Root to Leaf Numbers (after LC 113) — carry running number, sum at leaves
29. 🟡 **LC 951** Flip Equivalent Binary Trees (after LC 100 + LC 101) — parallel DFS with optional swap

---

### Tier 5 — Two-purpose recursion ladder (the climb to LC 124)

> **This is the tier you skipped to in May 2026 and got burned.** Climb it in order — do not jump.

30. 🟡 **LC 543** Diameter of Binary Tree — **the right introduction to two-purpose recursion**. Function returns height, but also updates a global `diameter`. Do this **before** anything else in this tier.
31. 🔴 **LC 687** Longest Univalue Path — same shape as LC 543 with parent.val == child.val condition
32. 🔴 **LC 124** Maximum Path Sum — two-purpose recursion + **negative clipping** + global-vs-return separation. **Reference Only on the first pass; attempt only after LC 543 + LC 687 click.** See the walkthrough + Common Bugs section above.

---

### Tier 6 — Reference Only (multi-pattern / advanced — skip on first pass)

These either combine multiple patterns or require concepts beyond this doc. Treat them as **bedtime reading** — open them, study the solution discussion, recognize the shape. Don't time-box-attempt them yet.

33. 🔴 **LC 297** Serialize and Deserialize Binary Tree — needs string parsing + queue-based deserialize design
34. 🔴 **LC 105** Construct Binary Tree from Preorder and Inorder — preorder + inorder + index mapping
35. 🔴 **LC 437** Path Sum III — prefix-sum technique on tree paths (advanced HashMap pattern)
36. 🔴 **LC 863** All Nodes Distance K — convert tree to graph, then BFS
37. 🔴 **LC 1373** Maximum Sum BST in Binary Tree — combines BST validation + subtree sum + two-purpose recursion
38. 🔴 **LC 99** Recover BST — find two swapped nodes via Morris traversal (O(1) space)
39. 🔴 **LC 450** Delete Node in BST — three-case pointer surgery with inorder successor

---

### How to use this plan

- **Pace:** 2–4 problems/day for ~2 weeks gets you through Tiers 1–4. Tier 5 is one problem at a time, with a day of rest between attempts.
- **When stuck:** time-box at 25 minutes. If still stuck, read the editorial, **don't accept-paste — close it and rewrite from understanding.**
- **Revision:** after finishing a tier, redo problems 1–3 from that tier from memory before moving on.
- **The honest victory criterion:** if you can solve **Tiers 1–3 from memory in under 15 minutes each**, you're ready for any easy/medium tree question in interviews. Tier 4–5 are bonus.

> **Lesson learned the hard way (May 2026):** I tried to do "Stretch" problems before completing Tier 5 ladder. LC 124 cost me an hour and 3 wrong submissions. **The tiers exist for a reason — climb them in order.**

---

## 🧾 TL;DR — One-Page Summary

- **Tree** = root + children, accessed only by following `.left` / `.right` pointers
- **Recursion** is the lifeblood of tree problems; trust the recursive call to solve subtrees
- **The universal skeleton:** `if (node == null) return baseValue; recurse left; recurse right; combine and return;`
- **Four traversals:** preorder (node first), inorder (sorted on BST), postorder (children first), level-order (BFS)
- **BFS uses a Queue + size-snapshot trick** to process levels independently
- **Four major patterns:** top-down DFS (carry state), bottom-up DFS (collect from children), BFS by level, parallel DFS (compare two trees)
- **BST invariant:** every node's left subtree is strictly less, right subtree is strictly greater. Inorder of a BST → sorted order.
- **LC 98 trap:** validating BST requires bounds (use `long`), not just immediate-child comparison
- **LeetCode hygiene:** never `static` for problem state; reset instance fields at the top of the public method

### Difficulty discipline

- ✅ **Try Now** = covered, attempt freely · 🟡 **Try After** = needs a section ahead in this doc · 🔴 **Reference Only** = needs concepts beyond this doc; read for awareness, don't attempt cold
- **Tier 1 (Foundational 7) you must master from memory:** LC 104, LC 100, LC 226, LC 101, LC 110, LC 102, LC 112
- **Two-purpose recursion ladder:** LC 543 → LC 687 → LC 124. Don't skip steps. LC 124 is reference-only on the first pass — see the walkthrough's three common bugs before you attempt it.

Once Tier 1 clicks, every tree question is just a remix of patterns 1–4 + a difficulty multiplier.
