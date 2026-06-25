# Java Collections Hierarchy & Internal Structures

## 🎯 Why This Matters

Every data structure has an **internal backing structure** — the actual memory layout underneath. That structure determines whether access is O(1) or O(n), why insertion costs what it costs, and why some DSes fail at certain jobs.

This file shows you:
1. The full Java `java.util` hierarchy (what inherits from what)
2. What each DS is backed by (the internal structure)
3. Why that backing explains the complexity
4. Custom DSes you implement in LeetCode (TreeNode, ListNode, etc.)

---

## 🎨 Visual — Java Collections Hierarchy

```
java.util

├─ Collection<E>  (abstract interface, root of most structures)
│  │
│  ├─ List<E>  (ordered, indexed access, allows duplicates)
│  │  ├─ ArrayList<E>
│  │  │  Backed by: Object[] array (dynamic resizing)
│  │  │  Why ArrayList:
│  │  │    - Backed by Object[] → direct index jump, arr[i] = O(1)
│  │  │    - But insert at middle → shift right, O(n)
│  │  │    - When full, creates larger array + copy all, O(n)
│  │  │    - Amortized append (at end): O(1) because copy rare
│  │  │    
│  │  └─ LinkedList<E>
│  │     Backed by: Doubly linked node chain
│  │     Why LinkedList:
│  │       - Head pointer → each node.next → find index i = O(i)
│  │       - But if you have reference to node, insertion = rewire 2 pointers, O(1)
│  │       - No resizing, flexible allocation
│  │       - Only use if you insert/delete at known positions inside (not ends)
│  │
│  └─ Queue<E> / Deque<E>  (FIFO / LIFO / both-ends)
│     ├─ ArrayDeque<E>
│     │  Backed by: Object[] array (circular, not linear)
│     │  Why ArrayDeque:
│     │    - Circular array: wrap-around at boundaries
│     │    - push() to end (stack) / offer() to end (queue): O(1)
│     │    - pop() from end (stack) / poll() from front (queue): O(1)
│     │    - No shifting, circular index logic handles wrapping
│     │    - Memory-efficient, no allocation overhead
│     │    - DEFAULT for Stack and Queue in modern Java
│     │    
│     ├─ LinkedList<E>  (also implements Deque, but slower)
│     │  Same as above, but slower for queue/stack operations
│     │  Use ArrayDeque instead
│     │
│     └─ PriorityQueue<E>
│        Backed by: Object[] array arranged as binary min-heap
│        Why PriorityQueue:
│          - Heap property: parent ≤ children (min-heap)
│          - Store in array, parent at (i-1)/2, children at 2i+1, 2i+2
│          - offer(): add at end + bubble up, O(log n)
│          - poll(): remove root + move last to root + bubble down, O(log n)
│          - peek(): root is always smallest, O(1)
│          - DON'T iterate — heap doesn't maintain sorted order,
│            iteration is O(n) random walk, not sorted
│
└─ Set<E>  (unique elements only)
   ├─ HashSet<E>
   │  Backed by: HashMap<E, Object> (dummy value)
   │  Why HashSet:
   │    - Hash table → hash element → check bucket, O(1) avg
   │    - Contains, add, remove = O(1) average
   │    - No order
   │    - When ~75% full, double size + rehash, O(n) per resize
   │    │
   ├─ LinkedHashSet<E>
   │  Backed by: LinkedHashMap<E, Object>
   │  Why LinkedHashSet:
   │    - Same as HashSet (backed by hash table)
   │    - Plus doubly linked list threading all entries
   │    - Iteration = insertion order
   │    - Contains, add, remove = O(1)
   │    
   └─ TreeSet<E>
      Backed by: TreeMap<E, Object>
      Why TreeSet:
        - Red-black tree (self-balancing BST)
        - Keys always sorted (natural order or custom comparator)
        - Contains, add, remove = O(log n) (tree traversal)
        - Iteration = sorted order
        - Can do range: .subSet(fromElem, toElem)
        - Use when you need sorted unique elements

Map<K, V>  (separate hierarchy, NOT extends Collection)
│
├─ HashMap<K, V>
│  Backed by: Hash table (array of buckets)
│  Bucket contents (Java 8+): single entry OR linked chain OR red-black tree
│  Why HashMap:
│    - Hash key → bucket index, O(1) hash calculation
│    - Within bucket: entry, chain, or tree (if >8 entries collide)
│    - Best case: no collisions, get/put/remove = O(1)
│    - Worst case: all keys collide, O(n) chain/tree search
│    - Load factor ~75%, resize doubles size + rehashes all, O(n)
│    - Amortized O(1) per operation
│    - No guaranteed order
│
├─ LinkedHashMap<K, V>
│  Backed by: HashMap + doubly linked list threading
│  Why LinkedHashMap:
│    - Same O(1) operations as HashMap
│    - Iteration = insertion order (or access order if configured)
│    - Extra memory for linked list pointers
│    - When you need "remember insertion order" + fast lookup
│
└─ TreeMap<K, V>
   Backed by: Red-black tree (self-balancing BST)
   Why TreeMap:
     - Keys always sorted (natural or custom comparator)
     - Get/put/remove = O(log n)
     - Range queries: .subMap(k1, k2), .headMap(k), .tailMap(k)
     - Iteration = sorted key order
     - Use when you need sorted key access or ranges
```

**KEY INSIGHT:** The backing structure (array vs chain vs tree) determines **everything**:
- Array-backed (ArrayList, ArrayDeque, HashMap) → fast random access, slow middle insert
- Chain-backed (LinkedList) → slow access, fast middle insert (if you have position ref)
- Tree-backed (TreeMap, TreeSet) → O(log n) everything, sorted order guaranteed

---

## 🧠 Understanding "Backed By"

**"Backed by" = the internal data structure that actually stores and organizes the data.**

When you write:
```java
ArrayList<Integer> list = new ArrayList<>();
list.add(5);
list.add(10);
list.add(15);
```

What's really happening inside:
```
                    ArrayList object
                           |
                   Internal Object[] array
                           |
                    [5] [10] [15] [  ] [  ]  ← current state
                      0   1   2   3   4
```

**Direct access:** `list.get(2)` = jump directly to index 2 = instant = **O(1)**.

**Insert at middle:** `list.add(1, 99)`:
```
Before:  [5] [10] [15] [  ] [  ]
         0   1    2   3   4

Insert 99 at index 1 means shift [10] and [15] right:

After:   [5] [99] [10] [15] [  ]
         0   1    2    3   4
```
Shift cost = O(n) because you moved n elements.

---

## 🎨 Visual — HashMap Internal Structure

```
HashMap with load factor ~0.75 (75% full)

                    HashMap object
                           |
          Hash table (Object[] array, length 16)
                           |
    ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
    │ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │ 8 │ 9 │10 │11 │12 │13 │14 │15 │
    └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
            ↓           ↓                       ↓
         null      ("alice", 25)           ("bob", 30)
                        ↓
                   ("alice2", 28)  ← collision: hash("alice") == hash("alice2") mod 16
                   

Operation: get("bob")
1. Compute hash("bob") = some large number, say 12847
2. Bucket index = 12847 mod 16 = 9
3. Jump to bucket 9 → find entry ("bob", 30)
4. Cost: hash function O(1) + bucket lookup O(1) = O(1)

Operation: get("alice2") when collision exists
1. Hash("alice2") = 2
2. Bucket 2 contains chain: ("alice", 25) → ("alice2", 28)
3. Must walk chain, check each entry's key
4. Cost: O(collision chain length) = O(1) if few collisions, O(n) if all collide

When load factor > 0.75:
- Create new array of size 32
- Rehash every entry: new_bucket = hash(key) mod 32
- Copy all entries to new buckets
- Cost: O(n) for one resize, but happens rarely (every doubling)
- Amortized: O(1) per insert
```

**Why this matters:**
- Perfect hash function, no collisions → O(1) every operation.
- Pathological hash function, all collide → O(n) search in single bucket.
- Real Java HashMap: good hash, most operations O(1), rare operations O(log n) within bucket (tree-backed buckets in Java 8+).

---

## 🎨 Visual — Binary Heap (PriorityQueue)

```
Min-Heap backed by Object[] array

        Array representation:
        ┌───┬───┬───┬───┬───┬───┬───┐
        │ 1 │ 3 │ 2 │ 7 │ 4 │ 6 │ 5 │
        └───┴───┴───┴───┴───┴───┴───┘
         0   1   2   3   4   5   6

        Tree representation (same data, visualized as tree):
                      1 (root, parent at (i-1)/2, always smallest)
                    /   \
                  3       2
                 / \     / \
               7   4   6   5
              
        Property: parent ≤ children (min-heap)
        So: 1 ≤ 3, 1 ≤ 2, 3 ≤ 7, 3 ≤ 4, 2 ≤ 6, 2 ≤ 5
        
        NOT sorted by order! Iteration is random, not [1, 2, 3, 4, 5, 6, 7]
        Min-heap only guarantees root is smallest.

Operation: offer(0) — insert 0
1. Add 0 at end of array: [1, 3, 2, 7, 4, 6, 5, 0]
2. Bubble up: compare 0 with parent at (7-1)/2 = 3
   - 0 < 5? Yes, swap: [1, 3, 2, 7, 4, 6, 0, 5]
3. Compare 0 with parent at (6-1)/2 = 2
   - 0 < 2? Yes, swap: [1, 3, 0, 7, 4, 6, 2, 5]
4. Compare 0 with parent at (2-1)/2 = 0
   - 0 < 1? Yes, swap: [0, 3, 1, 7, 4, 6, 2, 5]
5. 0 is root, stop.
6. Cost: O(log n) because tree height = log(n)

Operation: poll() — remove and return min (root)
1. Save root (1) to return
2. Move last element (5) to root: [5, 3, 2, 7, 4, 6]
3. Bubble down: compare 5 with children at 2*0+1=1 and 2*0+2=2
   - Children are 3 and 2. Min is 2. 5 > 2? Yes, swap: [2, 3, 5, 7, 4, 6]
4. Compare 5 with children at 2*2+1=5 and 2*2+2=6
   - Child at 5 is 6. 5 < 6? Yes, stop (can't swap smaller child that's greater)
5. Return 1
6. Cost: O(log n)

Operation: peek() — get min without removing
1. Return root (array[0]) = 1
2. Cost: O(1)
```

**Why this matters:**
- PriorityQueue useful when you repeatedly need "give me the smallest element."
- Heap guarantees root smallest (or largest if max-heap), O(1) peek.
- Insert/remove O(log n) because height is log(n).
- NOT for iteration in sorted order. Must poll() n times to get sorted sequence (O(n log n) total).

---

## 🎨 Visual — Red-Black Tree (TreeMap, TreeSet)

```
Red-Black Tree (simplified view, colors omitted for clarity)

TreeMap<Integer, String>
Tree stores keys in sorted order

                      8
                    /   \
                  4       12
                 / \      / \
                2   6   10   14
               /|  |\ /|  |\
              1 3  5 7 9 11 13 15

Properties:
- Binary Search Tree: left < parent < right
- Self-balancing: tree height = O(log n)
  (Actual balance maintained via red-black color rules)
- Sorted iteration: in-order traversal = 1,2,3,...,15

Operation: get(6)
1. Start at root (8)
2. 6 < 8? Go left to 4
3. 6 > 4? Go right to 6
4. Found! Cost: O(log n) tree height

Operation: put(7)
1. Search to find insertion position: O(log n)
2. Insert new node: O(1)
3. Rebalance tree (red-black rotations): O(log n)
4. Total: O(log n)

Operation: subMap(5, 12) — all keys in range [5, 12)
1. Find leftmost key ≥ 5: search O(log n), then traverse right
2. Return all keys until ≥ 12
3. Cost: O(log n + k) where k = number of results in range
```

**Why this matters:**
- TreeMap always sorted. Iteration = sorted order.
- All operations O(log n) due to tree height.
- Perfect for range queries: ".subMap(k1, k2)" = get all keys in range.
- Slower than HashMap on individual lookups (O(log n) vs O(1)), but brings sorted order.

---

## 🧩 Custom DSes in LeetCode

These aren't in `java.util`. You define or LeetCode pre-defines them.

### TreeNode (Binary Tree Node)

```java
// LeetCode pre-defines or you write:
class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;
    TreeNode(int val) { this.val = val; }
}

// Usage:
TreeNode root = new TreeNode(1);
root.left = new TreeNode(2);
root.right = new TreeNode(3);

// Visualization:
//      1
//     / \
//    2   3
```

**Backed by:** Pointers (object references). Each node holds data + left/right pointers.

**Why:**
- Natural hierarchy representation.
- Traversal algorithms (DFS, BFS) navigate via pointers.

---

### ListNode (Singly Linked List Node)

```java
// LeetCode pre-defines or you write:
class ListNode {
    int val;
    ListNode next;
    ListNode(int val) { this.val = val; }
}

// Usage:
ListNode head = new ListNode(1);
head.next = new ListNode(2);
head.next.next = new ListNode(3);

// Visualization: 1 → 2 → 3 → null
//                     ↑
//                  next pointer
```

**Backed by:** Singly linked nodes. Each node holds data + pointer to next node only.

**Why:**
- Chain structure, not array.
- Walk the chain by following next pointers.
- Insertion/deletion at front: O(1) if you have head reference.

---

### Union-Find (Disjoint Set Union)

```java
class UnionFind {
    int[] parent;
    int[] rank;
    
    UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;  // each element is its own parent initially
            rank[i] = 0;
        }
    }
    
    int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);  // path compression
        }
        return parent[x];
    }
    
    void union(int x, int y) {
        int px = find(x);
        int py = find(y);
        if (px == py) return;
        
        // union by rank
        if (rank[px] < rank[py]) {
            parent[px] = py;
        } else if (rank[px] > rank[py]) {
            parent[py] = px;
        } else {
            parent[py] = px;
            rank[px]++;
        }
    }
}
```

**Backed by:** Two arrays: parent[] and rank[].

**Why:**
- Track which component each element belongs to.
- find(x) walks up parent pointers to root. Path compression optimizes future walks.
- union(x, y) merges two components by making one root point to the other.
- Cost: O(α(n)) ≈ O(1) per operation (inverse Ackermann, grows incredibly slow).

---

### Graph Representations

**Adjacency List:**
```java
List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i < n; i++) {
    adj.add(new ArrayList<>());
}
// adj[0].add(1) means edge 0 → 1
// adj[0] = [1, 2, 3] means 0 → 1, 0 → 2, 0 → 3
```

**Backed by:** 2D list. Each row = neighbors of that node.

**Why:**
- Sparse graphs: only store existing edges.
- Space: O(V + E) where V = nodes, E = edges.
- Traversal (DFS/BFS): O(V + E).

**Adjacency Matrix:**
```java
int[][] adj = new int[n][n];
// adj[i][j] = 1 means edge i → j
// adj[i][j] = 0 means no edge

//      0 1 2 3
//   0 [0 1 1 0]  ← node 0 connects to 1, 2
//   1 [0 0 1 0]  ← node 1 connects to 2
//   2 [0 0 0 1]  ← node 2 connects to 3
//   3 [0 0 0 0]  ← node 3 connects to none
```

**Backed by:** 2D array. adj[i][j] = presence of edge.

**Why:**
- Dense graphs: matrix is okay.
- Space: O(V²) always.
- Edge check (is 0 → 1?): O(1) direct lookup.

---

### DoublyLinkedListNode (Bidirectional Linked List)

```java
// You write this (LeetCode doesn't pre-define it):
class DoublyLinkedListNode {
    int val;
    DoublyLinkedListNode prev;
    DoublyLinkedListNode next;
    DoublyLinkedListNode(int val) { this.val = val; }
}

// Usage:
DoublyLinkedListNode head = new DoublyLinkedListNode(1);
DoublyLinkedListNode second = new DoublyLinkedListNode(2);
head.next = second;
second.prev = head;

// Visualization: 1 ↔ 2 ↔ 3 ↔ null
//                 ↑  ↓  ↑  ↓  ↑  ↓
//              prev/next pointers (bidirectional)
```

**Backed by:** Doubly linked nodes. Each node holds data + pointer to both previous and next nodes.

**Why:**
- Allows traversal in both directions (forward and backward).
- Essential for LRU Cache design (access order tracking).
- Deque operations (add/remove from both ends efficiently).
- Without prev pointer, reverse traversal is O(n).

---

### TrieNode (Prefix Tree Node)

```java
// You write this (for autocomplete, spell check, etc.):
class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    boolean isEndOfWord = false;  // marks end of a valid word
}

// Usage (building a trie):
TrieNode root = new TrieNode();
String word = "apple";
TrieNode node = root;
for (char c : word.toCharArray()) {
    node.children.putIfAbsent(c, new TrieNode());
    node = node.children.get(c);
}
node.isEndOfWord = true;

// Visualization:
//       root
//       /
//      a
//      |
//      p
//      |
//      p
//      |
//      l
//      |
//      e (isEndOfWord = true)
```

**Backed by:** Tree of nodes, each node has HashMap<Character, TrieNode> children.

**Why:**
- Prefix search in O(L) where L = word length (independent of total words).
- With HashMap, finding all words starting with "app" = O(L + k) where k = results.
- Without Trie: HashMap would force O(n) scan and O(L) startsWith check per word.
- Space: O(ALPHABET_SIZE × number_of_nodes), memory-efficient for shared prefixes.

---

### Interval (Range Representation)

```java
// You write this (for merge intervals, meeting rooms, etc.):
class Interval implements Comparable<Interval> {
    int start;
    int end;
    Interval(int start, int end) {
        this.start = start;
        this.end = end;
    }
    
    @Override
    public int compareTo(Interval other) {
        return this.start - other.start;  // sort by start time
    }
}

// Usage:
List<Interval> intervals = Arrays.asList(
    new Interval(1, 3),
    new Interval(2, 6),
    new Interval(8, 10)
);
Collections.sort(intervals);  // now sorted by start
```

**Backed by:** Two integer fields (start, end).

**Why:**
- Natural representation for scheduling problems (meeting rooms, task intervals).
- Sorting by start makes merging/overlap detection straightforward.
- Implements Comparable for easy sorting with Collections.sort().
- Without Interval class: would use int[][] and handle indices manually (error-prone).

---

### GraphNode (Object-Oriented Graph Representation)

```java
// You write this (for clone graph, island problems, etc.):
class GraphNode {
    int val;
    List<GraphNode> neighbors = new ArrayList<>();
    GraphNode(int val) { this.val = val; }
}

// Usage:
GraphNode node1 = new GraphNode(1);
GraphNode node2 = new GraphNode(2);
node1.neighbors.add(node2);
node2.neighbors.add(node1);

// Visualization: 1 ↔ 2 ↔ 3
//                ↑___________↓
//  (bidirectional graph with cycles)
```

**Backed by:** Node object with ArrayList<GraphNode> neighbors.

**Why:**
- Object-oriented modeling of graphs (cleaner than adjacency list for some problems).
- Direct reference to neighbor nodes (vs. adjacency list indices).
- Useful for clone graph problems (DFS/BFS creates new nodes, links via neighbors).
- Less memory-efficient than adjacency list for large graphs (reference overhead).

---

### MinStack / MaxStack (Design Pattern)

```java
// Custom stack that tracks min (or max) in O(1):
class MinStack {
    Stack<Integer> stack = new Stack<>();
    Stack<Integer> minStack = new Stack<>();  // parallel stack tracking min
    
    public void push(int val) {
        stack.push(val);
        minStack.push(Math.min(minStack.isEmpty() ? val : minStack.peek(), val));
    }
    
    public void pop() {
        stack.pop();
        minStack.pop();
    }
    
    public int top() {
        return stack.peek();
    }
    
    public int getMin() {
        return minStack.peek();  // O(1)!
    }
}

// Usage:
MinStack ms = new MinStack();
ms.push(5);
ms.push(2);
ms.push(8);
ms.getMin();  // returns 2 in O(1), not O(n)
```

**Backed by:** Two parallel stacks (main stack + min-tracking stack).

**Why:**
- Regular stack.min() would force O(n) scan.
- Parallel minStack maintains minimum at each depth.
- On push: track min at current position.
- On pop: discard the old min, minStack.peek() now gives current min.
- Cost: O(1) all operations, extra O(n) space for parallel stack.

---

## 🔹 Comparing Backings — Why It Matters

| Backing | DS | Access | Insert | Delete | Space | When | Why NOT |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Object[] | ArrayList | O(1) | O(n)* | O(n)* | O(n) | Default list | Middle inserts slow |
| Linked nodes | LinkedList | O(n) | O(1)** | O(1)** | O(n) | Few, specific inserts | Random access slow |
| Doubly linked | DoublyLinkedListNode | O(n) | O(1)** | O(1)** | O(n) | LRU cache, deque | Extra prev pointer |
| Circular Object[] | ArrayDeque | — | O(1) | O(1) | O(n) | Queue/Stack | No random access |
| Binary heap | PriorityQueue | O(1) peek | O(log n) | O(log n) | O(n) | Priority access | Not sorted iteration |
| Hash table | HashMap | O(1)*** | O(1)*** | O(1)*** | O(n) | Key lookup | No order, collisions |
| Red-black tree | TreeMap | O(log n) | O(log n) | O(log n) | O(n) | Sorted keys | Slower than hash |
| Hash table + list | LinkedHashMap | O(1) | O(1) | O(1) | O(n) | Insertion order | Extra memory |
| Linked pointers | LinkedList nodes | O(n) | O(1)** | O(1)** | O(n) | Chain traversal | Can't random-access |
| Trie tree | TrieNode | O(L) | O(L) | O(L) | O(ALPHABET × nodes) | Prefix search | More memory |
| Forest of trees | Union-Find | O(α) | O(α) | — | O(n) | Components | Hard to visualize |
| Binary tree nodes | TreeNode | O(n) | — | — | O(n) | Tree traversal | Not balanced |
| Object graph | GraphNode | O(degree) | O(1) | O(1) | O(V + E) | Clone, island | More memory than adjacency list |
| Two integers | Interval | — | O(1) | O(1) | O(1) | Scheduling | No built-in operations |
| Parallel stacks | MinStack | O(1) | O(1) | O(1) | O(2n) | Track extremes | Extra space for parallel stack |

*At end = O(1). At arbitrary position = O(n).
**If you have reference to position. Otherwise walk = O(n).
***Average. Worst = O(n) if collisions.

---

## 🧾 Remember

The backing structure explains every complexity:
- **Array-backed** → fast random access, slow middle insert
- **Pointer-based** → slow random access, fast middle insert (with reference)
- **Circular array** → both ends fast, no shifting
- **Hash table** → O(1) lookup, no order
- **Red-black tree** → O(log n) everything, sorted order
- **Heap** → O(log n) insert/remove, O(1) peek (min/max), NOT sorted iteration
- **Linked chain** → walk the chain, rewire pointers at insertion point

**Interview mindset:** When choosing a DS, think: "What's inside?" The answer is the backing, and the backing determines your complexity.
