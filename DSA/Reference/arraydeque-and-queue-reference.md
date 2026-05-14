# Java Queues, Stacks & Deques — DSA Notes

> Compact reference for **`ArrayDeque`**, **`Queue`**, **`PriorityQueue`**, and the **`Stack`** legacy class. The 90% answer for FIFO/LIFO/double-ended needs in DSA is **`ArrayDeque`**. Everything else is a special case.

Companion file: **`DeepDive/trees-fundamentals.md`** (BFS template lives there). When you see `Deque<TreeNode> queue = new ArrayDeque<>()` in the BFS template, this is the file that explains why.

---

## 🚦 The Decision Matrix — Which One to Use

| You need... | Use | Why |
| --- | --- | --- |
| **FIFO queue** (BFS, level order, sliding window) | `Deque<E> q = new ArrayDeque<>()` | Fastest, lowest memory overhead. **Default choice.** |
| **LIFO stack** (iterative DFS, monotonic stack, undo) | `Deque<E> stack = new ArrayDeque<>()` | Yes — also ArrayDeque. **Never** use `java.util.Stack`. |
| **Double-ended** (sliding window monotonic deque) | `Deque<E> dq = new ArrayDeque<>()` | Add/remove at both ends in O(1) amortized. |
| **Best-so-far** (k-th largest, Dijkstra, top-K) | `PriorityQueue<E> pq = new PriorityQueue<>()` | O(log n) push/pop, O(1) peek-min. |
| **Index-based access** | `ArrayList<E>` | `ArrayDeque` doesn't support `get(i)`. |
| **Thread-safe queue** | `ConcurrentLinkedDeque` / `LinkedBlockingQueue` | Interview-rare. Mention only if asked. |

> **One-line rule:** If the problem says "BFS", "level order", "iterative DFS", "monotonic queue", or "stack" — reach for `Deque<E> = new ArrayDeque<>()`.

---

## 🌳 Java Collections Hierarchy — Where Deque Fits

Before memorizing methods, see the family tree. This explains **why** `ArrayDeque` can be both a queue and a stack — it implements an interface (`Deque`) that's a superset of `Queue`.

```
                       Iterable<E>
                            │
                       Collection<E>
                      /             \
                     /               \
                List<E>              Queue<E>          ← FIFO contract (offer/poll/peek)
                /    \              /        \
               /      \            /          \
        ArrayList   LinkedList   Deque<E>     PriorityQueue   ← min-heap (best-first)
                       ▲          /    \              ▲
                       │         /      \             │
                       └────────┘        \            │
                                          \           │
                                     ArrayDeque       │
                                          ▲           │
                                          │           │
                                          │           │
                                ✅ default for FIFO    PriorityQueue is a Queue
                                   and LIFO needs     but NOT a Deque
                                                      (sibling of Deque,
                                                       not child)

   LinkedList implements BOTH List<E> AND Deque<E> — flexible but slow due
   to per-node allocation. Prefer ArrayDeque for queue/stack roles.
```

**Three takeaways:**

1. **`Deque` extends `Queue`** — so any `Deque` IS a `Queue`. That's why `Deque<E> q = new ArrayDeque<>()` works in BFS code that types `Queue<E>` operations.
2. **`PriorityQueue` is a `Queue` but NOT a `Deque`** — it has only one end (the min/max), and ordering is by priority, not insertion. Different beast entirely.
3. **`LinkedList` implements both `List` and `Deque`** — flexible but slow due to node allocation. ArrayDeque wins for queue/stack roles.

> **The interface you should usually program to:**
> - For BFS/queue work → `Deque<E>` (gets you FIFO + future flexibility)
> - For stack work → `Deque<E>` (push/pop/peek)
> - For priority work → `PriorityQueue<E>` (no abstraction needed)
> - For list work → `List<E>`

---

## 🔹 ArrayDeque (the workhorse)

Resizable-array implementation of the `Deque` interface. Acts as **either** a queue or a stack depending on which methods you call. Faster than `LinkedList` (no node allocation per element) and faster than legacy `Stack` (no synchronization).

```java
Deque<Integer> q = new ArrayDeque<>();
```

> **Always declare as `Deque<E>`, not `ArrayDeque<E>`** — same habit as `List<E> list = new ArrayList<>()`. Programs to the interface; flexibility for free.

---

### 📐 What Is a Deque? — Visual Mental Model

> **Deque = "Double-Ended QUEue"** (pronounced "deck"). It's a linear sequence where you can add or remove at **either end** in O(1).
>
> Picture a row of train cars where you can attach/detach cars at the front OR the back — but you cannot reach into the middle.

```
                  ┌────────────────── Deque ──────────────────┐
                  │                                           │
                  │  ┌───┐  ┌───┐  ┌───┐  ┌───┐  ┌───┐        │
   front/head ───►│  │ A │  │ B │  │ C │  │ D │  │ E │ ◄─── back/tail
                  │  └───┘  └───┘  └───┘  └───┘  └───┘        │
                  │   ▲                              ▲        │
                  │   │                              │        │
                  │   peekFirst()              peekLast()     │
                  │   pollFirst()              pollLast()     │
                  │   offerFirst(x)            offerLast(x)   │
                  │                                           │
                  └───────────────────────────────────────────┘

       Both ends are O(1). The middle is unreachable in O(1).
```

### How a Deque becomes a Queue (FIFO) or a Stack (LIFO)

The same data structure, three different access patterns — pick the lens that matches your problem:

```
                ┌─────────── QUEUE LENS (FIFO) ─────────────┐
                │                                           │
                │   in: offer()         out: poll()         │
                │       (offerLast)         (pollFirst)     │
                │                                           │
                │           ▼   ┌──┬──┬──┬──┐    ▲          │
                │   ────────────│ A│ B│ C│ D│──────────►    │
                │               └──┴──┴──┴──┘               │
                │   first in (A) is first out (A)           │
                └───────────────────────────────────────────┘

                ┌────────── STACK LENS (LIFO) ──────────────┐
                │                                           │
                │   in: push()          out: pop()          │
                │       (offerFirst)        (pollFirst)     │
                │                                           │
                │           ┌──┐                            │
                │   in/out: │ D│ ◄── last pushed, first out │
                │           ├──┤                            │
                │           │ C│                            │
                │           ├──┤                            │
                │           │ B│                            │
                │           ├──┤                            │
                │           │ A│ ◄── first pushed, last out │
                │           └──┘                            │
                │   Both push and pop touch the head only   │
                └───────────────────────────────────────────┘

                ┌──── DEQUE LENS (true two-ended) ──────────┐
                │   Use when monotonic queue / sliding      │
                │   window: offer at one end, evict at      │
                │   either end depending on conditions.     │
                │                                           │
                │   offerFirst, offerLast,                  │
                │   pollFirst, pollLast — all valid.        │
                └───────────────────────────────────────────┘
```

> **Key intuition:** In Java, `ArrayDeque` is **one** physical structure. The "queue vs stack vs deque" distinction lives in **which methods you call**, not in which class you instantiate.

---

### 🌐 C++ / Python / JS Equivalents (for cross-language mental mapping)

If you've used these in another language, here's what maps to what:

| Java | C++ STL | Python | JavaScript | Notes |
| --- | --- | --- | --- | --- |
| `Deque<E> = new ArrayDeque<>()` | `std::deque<E>` | `collections.deque()` | `Array` (push/pop/shift/unshift) | All four are double-ended, O(1) at both ends. Java's ArrayDeque is closest in spirit to C++'s `std::deque`. |
| `Queue<E> = new ArrayDeque<>()` (FIFO use) | `std::queue<E>` (adapter on `std::deque`) | `collections.deque()` (use `append` + `popleft`) | `Array` (use `push` + `shift`) | C++ `std::queue` is a wrapper over `std::deque`; you can't access internals — only `push/pop/front/back`. |
| `Deque<E> = new ArrayDeque<>()` (LIFO use) | `std::stack<E>` (adapter on `std::deque`) | `list` (use `append` + `pop`) | `Array` (use `push` + `pop`) | Both Java and C++ recommend deque-backed stacks over legacy ones. |
| `PriorityQueue<E>` (min-heap default) | `std::priority_queue<E>` (max-heap default!) | `heapq` (min-heap on a list) | No native heap | **Watch out:** C++ `priority_queue` defaults to MAX-heap; Java defaults to MIN-heap. Opposite signs. |
| `Stack<E>` (legacy, **don't use**) | n/a (only `std::stack`) | n/a | n/a | Java's legacy class is a Java-only mistake. |

**The key C++ idea to carry over:** in C++, `std::deque<T>` is the underlying container that `std::queue<T>` and `std::stack<T>` are built on top of. **Java's `ArrayDeque` plays the same role** — one container, multiple access patterns via the API you choose.

```cpp
// C++ — explicit container choice
std::deque<int> dq;           // double-ended
std::queue<int> q;            // FIFO (deque under the hood)
std::stack<int> st;           // LIFO (deque under the hood)
std::priority_queue<int> pq;  // max-heap (vector under the hood)
```

```java
// Java — one class for all three roles
Deque<Integer> dq = new ArrayDeque<>();    // double-ended
Deque<Integer> q  = new ArrayDeque<>();    // FIFO via offer/poll
Deque<Integer> st = new ArrayDeque<>();    // LIFO via push/pop
PriorityQueue<Integer> pq = new PriorityQueue<>();  // min-heap
```

> **Mental shortcut for C++ refugees:** "Java's `ArrayDeque` is `std::deque`. Whenever you'd use `std::queue` or `std::stack` in C++, use `ArrayDeque` in Java with the matching API."

---

### Useful Methods — Three APIs in One Class

ArrayDeque exposes the **Queue API** (FIFO), the **Stack API** (LIFO), AND the **Deque API** (both ends). Pick the one that matches your mental model and stick with it inside a single problem.

#### Queue API (FIFO — for BFS)

| Method | Description | Time |
| --- | --- | --- |
| `offer(e)` | Add to **tail** | O(1) amortized |
| `poll()` | Remove from **head**, returns null if empty | O(1) |
| `peek()` | Look at **head** without removing, returns null if empty | O(1) |
| `isEmpty()` | True if empty | O(1) |
| `size()` | Number of elements | O(1) |

#### Stack API (LIFO — for iterative DFS)

| Method | Description | Time |
| --- | --- | --- |
| `push(e)` | Add to **head** (top of stack) | O(1) amortized |
| `pop()` | Remove from **head**, throws if empty | O(1) |
| `peek()` | Look at **head** without removing | O(1) |

#### Deque API (both ends — monotonic deque, sliding window)

| Method | Description | Time |
| --- | --- | --- |
| `offerFirst(e)` / `offerLast(e)` | Add to head / tail | O(1) amortized |
| `pollFirst()` / `pollLast()` | Remove from head / tail | O(1) |
| `peekFirst()` / `peekLast()` | Look at head / tail | O(1) |

> **Pick one API per problem.** If you're doing BFS, only use `offer/poll/peek`. If you're doing iterative DFS, only use `push/pop/peek`. Mixing them is legal but causes bugs (`push` adds to head, `offer` adds to tail — easy to mismatch).

---

### DSA Use Cases

- **BFS / level order traversal** on trees and graphs
- **Iterative DFS** when recursion depth would overflow the stack
- **Monotonic queue / sliding window max** (LC 239)
- **Topological sort** (Kahn's algorithm)
- **Backtracking with undo via stack frames** (when converting recursion to iteration)

---

### Common DSA Patterns

**1. BFS Level-Order Traversal (the trees-sprint MVP)** ⭐

> Visit a tree level by level: enqueue the root, then repeatedly poll a node, enqueue its non-null children. Used for level-order, right-side view, zigzag, max level sum, and almost every "by level" problem.

```java
public List<Integer> bfs(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) {
        return result;
    }
    Deque<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        TreeNode node = queue.poll();
        result.add(node.val);
        if (node.left != null) {
            queue.offer(node.left);
        }
        if (node.right != null) {
            queue.offer(node.right);
        }
    }
    return result;
}
```

**🏷️ Example problems:** LC 102 Level Order, LC 199 Right Side View, LC 1161 Max Level Sum.

---

**2. BFS with Level-Snapshot (capture each level as its own list)** ⭐

> Before the inner loop, snapshot `queue.size()` — that's the count of nodes at the current level. The inner loop processes exactly that many, after which `queue` holds only next-level nodes.

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> ans = new ArrayList<>();
    if (root == null) {
        return ans;
    }
    Deque<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        // Snapshot — number of nodes on THIS level
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
        ans.add(level);
    }
    return ans;
}
```

**🏷️ Example problems:** LC 102 Level Order, LC 103 Zigzag, LC 107 Level Order II, LC 199 Right Side View, LC 515 Largest Value in Each Row.

> **Mental hook:** `int levelSize = queue.size();` is THE line that makes "by level" problems easy. Without it, you can't tell where one level ends and the next begins.

---

**3. Iterative DFS (Preorder) Using ArrayDeque as Stack**

> Same logic as recursive DFS, but you control the call stack manually. Push **right** first so **left** is popped first (LIFO order).

```java
public List<Integer> preorder(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) {
        return result;
    }
    Deque<TreeNode> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
        TreeNode node = stack.pop();
        result.add(node.val);
        // Right first, so left is processed first (LIFO)
        if (node.right != null) {
            stack.push(node.right);
        }
        if (node.left != null) {
            stack.push(node.left);
        }
    }
    return result;
}
```

**🏷️ Example problems:** LC 144 Preorder Iterative, LC 589 N-ary Preorder.

> **When to reach for iterative DFS:** when the tree is "stick-shaped" (n = 100,000 left-only chain) and recursion would blow the stack. Otherwise prefer recursive DFS for clarity.

---

# 🛑 STOP HERE for Trees Prep

> **You now know enough to handle every BFS / iterative-DFS problem in the 3-day trees sprint.**
>
> Specifically: you can write the BFS Level-Order template, the BFS with Level-Snapshot template, and Iterative Preorder DFS — which collectively cover Tier 1 problem #5 (LC 102), Tier 2 problems #10–14 (LC 199, 103, 107, 1161, 515), and the iterative versions of LC 144/94/145 if interview asks.
>
> **Read past this marker only AFTER the 3-day sprint** — the rest is for sliding window, k-th largest, Dijkstra, and avoiding legacy-class traps. None of that blocks trees.

---

## 🔹 java.util.Stack — DON'T USE

`java.util.Stack` is a legacy class from Java 1.0. It extends `Vector` (synchronized → slow) and is officially discouraged by the Javadoc itself:

> *"A more complete and consistent set of LIFO stack operations is provided by the **Deque** interface and its implementations, which should be used in preference to this class."* — Java SE docs

```java
// ❌ Slow, legacy, synchronized for no reason
Stack<Integer> stack = new Stack<>();

// ✅ Fast, modern, idiomatic
Deque<Integer> stack = new ArrayDeque<>();
```

If you see `Stack<E>` in interview code or LeetCode editorial — it works, but rewrite it with `ArrayDeque` to signal modern Java fluency.

---

## 🔹 LinkedList — only if forced into the Queue interface

`LinkedList<E>` implements both `List` and `Deque`. You'll occasionally see:

```java
Queue<Integer> q = new LinkedList<>();
```

This works but is **slower than ArrayDeque** because each insertion allocates a node object (more GC pressure, worse cache locality).

| Operation | ArrayDeque | LinkedList |
| --- | --- | --- |
| `offer` / `poll` / `peek` | O(1) **fast** (array indexing) | O(1) (but pointer chasing) |
| Memory per element | One slot in array | Wrapper node + 2 pointers |
| Cache locality | ✅ Excellent | ❌ Poor |

> **Rule:** Replace any `Queue<E> q = new LinkedList<>();` you see with `Deque<E> q = new ArrayDeque<>();`. Same API, faster runtime.

---

## 🔹 PriorityQueue — for "best-so-far" problems

A **min-heap** by default (smallest element at the head). For DSA:

```java
// Min-heap
PriorityQueue<Integer> minHeap = new PriorityQueue<>();

// Max-heap (reverse comparator)
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());

// Custom comparator (sort intervals by start)
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
```

### Useful Methods

| Method | Description | Time |
| --- | --- | --- |
| `offer(e)` / `add(e)` | Insert | O(log n) |
| `poll()` | Remove min | O(log n) |
| `peek()` | View min | O(1) |
| `size()` | Count | O(1) |

### When to use

- **Top-K** problems (LC 215 Kth Largest, LC 347 Top K Frequent)
- **Dijkstra's shortest path**
- **Merge K sorted lists** (LC 23)
- **Median of stream** (two heaps — LC 295)

> **Not for BFS.** PriorityQueue is for "best-first", not "first-in-first-out". If the problem says "shortest in unweighted graph" → BFS with ArrayDeque. If "shortest in weighted graph" → Dijkstra with PriorityQueue.

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

### Gotcha 1 — `ArrayDeque` does not allow `null`

```java
Deque<TreeNode> q = new ArrayDeque<>();
q.offer(null);   // ❌ throws NullPointerException
```

This bites in BFS when you're tempted to enqueue null children. Always null-check before `offer`:

```java
if (node.left != null) {
    queue.offer(node.left);
}
```

`LinkedList` allows nulls, which is one of the few situations it wins on — but the right fix is a null check, not switching implementation.

---

### Gotcha 2 — Mixing Queue API and Stack API in the same loop

```java
Deque<Integer> dq = new ArrayDeque<>();
dq.push(1);    // adds to head
dq.push(2);
dq.poll();     // ❌ this removes from head — but mental model said "FIFO"
```

`push` adds to head, but `poll` ALSO removes from head. Result: behaves like a stack, not a queue. **Pick one API and stay in it for the whole problem.**

| Mental model | Methods |
| --- | --- |
| Queue (FIFO) | `offer` / `poll` / `peek` |
| Stack (LIFO) | `push` / `pop` / `peek` |

---

### Gotcha 3 — `peek()` and `poll()` return `null` on empty; `pop()` throws

```java
Deque<Integer> dq = new ArrayDeque<>();
dq.poll();    // returns null
dq.peek();    // returns null
dq.pop();     // ❌ throws NoSuchElementException
```

Use `pop()` only inside `while (!dq.isEmpty())` loops.

---

### Gotcha 4 — Don't use `Stack<E>` in interviews

Even though `java.util.Stack<E>` works, using it in a system design or coding interview signals you haven't kept up with modern Java. **Always `Deque<E> = new ArrayDeque<>();`** for stack semantics.

---

### Gotcha 5 — Capacity hint is allowed, can cut allocation cost

```java
// Default initial capacity 16, doubles when full
Deque<Integer> dq = new ArrayDeque<>();

// Hint — pre-allocates approximately n slots
Deque<Integer> dq = new ArrayDeque<>(n);
```

If you know `n` (e.g., grid size for BFS), pass it as the constructor arg. Saves a few resize operations on large inputs.

---

## ⚡ Quick Cheat Sheet

### When-to-use lookup card

| Symptom in problem statement | Reach for | Method calls |
| --- | --- | --- |
| "Level by level" / "level order" / "BFS" | `Deque<E> = new ArrayDeque<>()` | `offer / poll / peek` |
| "Process in reverse order" / "stack" / "recursion → iterative" | `Deque<E> = new ArrayDeque<>()` | `push / pop / peek` |
| "Sliding window max/min" / "monotonic" | `Deque<E> = new ArrayDeque<>()` | `offerLast / pollFirst / pollLast / peekFirst` |
| "Top K" / "kth largest" / "best so far" | `PriorityQueue<E>` | `offer / poll / peek` |
| "Shortest path in weighted graph" | `PriorityQueue<int[]>` (Dijkstra) | `offer / poll` |

### One-line creation cheat sheet

```java
Deque<Integer> queue = new ArrayDeque<>();                          // FIFO
Deque<Integer> stack = new ArrayDeque<>();                          // LIFO
Deque<Integer> deque = new ArrayDeque<>();                          // both ends
PriorityQueue<Integer> minHeap = new PriorityQueue<>();             // smallest first
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
PriorityQueue<int[]> pqByFirst = new PriorityQueue<>((a, b) -> a[0] - b[0]);
```

### Top-3 patterns to memorize

1. **BFS Level-Order with Snapshot** ⭐ — `int levelSize = queue.size();` is the magic line
2. **BFS Plain (visit-order only)** ⭐ — when you don't care about levels
3. **Iterative DFS Preorder** — `push right first, then left`

### Mental model

> **`ArrayDeque` is the "Java collection that does the right thing 90% of the time" for FIFO and LIFO needs.** Default to it. Switch only when you need priority order (`PriorityQueue`), index access (`ArrayList`), or thread safety (concurrent classes).
