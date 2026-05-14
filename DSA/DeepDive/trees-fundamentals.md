# Trees — Fundamentals (Deep Dive)

> A from-scratch guide to binary trees, traversals, recursion intuition, and BST — built for cracking DSA tree problems in interviews. Read top to bottom once. Use the **Reference** doc for daily revision.

---

## 📌 Notion Paste Guide (Read This First)

This file uses many code blocks and ASCII diagrams. To paste cleanly:

1. In Notion, type `/code` and press **Enter** to create a code block first
2. Choose `Java` for code and `Plain Text` for ASCII diagrams
3. **Paste inside the code block** — never at the document level
4. For headings (`##`, `###`), use Notion's **H2 / H3** buttons in the toolbar — Markdown headings won't auto-convert when pasting plain text
5. Tables: select the markdown table, paste, then in Notion press **Convert to Table** if it offers (or recreate manually — Notion's table behavior is finicky)

**Paste section by section** rather than the whole file at once. Each `---` divider is a natural break point.

A fuller Notion guide is at the bottom of this file.

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

### Pattern 2: Bottom-Up DFS (collect from children, return up)

> Each call **returns information** about its subtree. The parent **combines** the children's returns to compute its own answer.

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

> 🧩 **Try these:**
> - ✅ LC 110 Balanced Binary Tree — postorder height + early-termination via sentinel `-1`. **Start here** to get bottom-up muscle memory.
> - 🟡 **Try after LC 110 + the "Building Up to Two-Purpose Recursion" ladder below** — LC 543 Diameter of Binary Tree
> - 🔴 **Reference only — do NOT attempt cold** — LC 124 Maximum Path Sum (full walkthrough + bug list below)
> - 🔴 LC 687 Longest Univalue Path — variant of LC 543 with value matching; do this after LC 543 clicks
> - 🔴 LC 1373 Maximum Sum BST in Binary Tree — combines BST validation + subtree sum + two-purpose recursion. Multi-pattern problem; come back after each individual pattern is solid.

---

### Pattern 3: BFS by Level (level-snapshot trick)

> Use the queue with `size = queue.size()` to process one level at a time. See the BFS section above.

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

### Walkthrough 1: Invert Binary Tree (LC 226)

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

## 📋 Notion Paste Guide (Full Version)

Pasting a long markdown file into Notion can be frustrating. Here's the workflow that actually works:

### Method 1 — Paste section by section (recommended)

1. **Open this file in your editor** (the `.md` file, raw)
2. In Notion, create a new page titled `Trees — Fundamentals (Deep Dive)`
3. **For each section between `---` dividers in this file:**
   - Copy the heading text (e.g., `What Is a Tree?`)
   - In Notion, click `+`, choose **Heading 2** (or `H3` for sub-sections), type the heading
   - Copy the body text (paragraphs only — not the code blocks)
   - Paste below the heading
   - For each code block: in Notion type `/code` → Enter → choose `Java` (or `Plain Text` for ASCII diagrams) → paste the code inside
   - For each table: paste it; if Notion offers "Convert to Table," accept; otherwise recreate using `/table`
4. Repeat per section

This is slower but produces a clean, navigable Notion page.

### Method 2 — Bulk paste then fix

1. Copy the whole file
2. Paste into Notion at document level
3. Notion will preserve **most** Markdown headings, lists, and inline code, but:
   - Code blocks may lose their language highlighting → click each block, set to Java
   - Tables often need to be recreated
   - Triple-backtick fences sometimes appear as plain text → wrap in `/code` blocks
4. Spend 5–10 minutes cleaning up

### Tips

- **Always use `/code` blocks for ASCII diagrams** — otherwise Notion collapses spaces and ruins the alignment
- **Triple backticks (\`\`\`) inside Notion code blocks paste as literal characters** — that's expected
- **Avoid "Markdown import"** in Notion — it strips formatting and is worse than plain paste

### Folder structure on Notion side (optional)

If you want to mirror this file system in Notion:

```
DSA Hub
├── Reference Notes (existing)
│   ├── String Operations
│   ├── HashMap
│   ├── HashSet / TreeSet
│   ├── Lambdas
│   └── Trees — Reference (coming next)
│
└── Deep Dive Notes (new sub-page)
    └── Trees — Fundamentals (paste this file here)
```

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
