# Data Structures Overview — The Foundation Layer

## 🎯 Goal of This File

**Read this first when returning after 2+ weeks away from DSA.** This file answers:
- What data structures exist?
- When do you use each one?
- What breaks if you pick the wrong one?
- How does the internal structure explain the complexity?

This is NOT about patterns or algorithms. It's about understanding the **why** — why one DS is fast for one job and slow for another.

---

## 🧠 Mental Model — The Core Idea

Every data structure is a **container with a specific internal structure** (called the **backing structure**). That backing structure is why one DS is O(1) for a task and another is O(n).

**Example:**
- **ArrayList** is backed by a fixed `Object[] array` inside. This means:
  - Direct index access (jump to position 5) = instant, O(1)
  - Insertion in the middle (shift everything after position 2 rightward) = O(n)
  
- **LinkedList** is backed by a chain of nodes, each pointing to the next. This means:
  - You must walk the chain to reach position 5 = O(n)
  - But once you find the spot, rewire the pointers = O(1)

The **backing structure** (array vs linked nodes) explains **everything** about why ArrayList wins at random access and LinkedList wins at middle insertions.

---

## 📖 The 8 Data Structure Categories

### 1️⃣ Linear Structures — Ordered Collections

**What are they?** Containers that store elements in a sequence. You access by position (index).

**The backing structures:**
- **Array (`int[]`, `String[]`)**: Fixed-size chunk of memory. All elements sit next to each other.
  - Access element at index 5? Jump directly to that memory location. O(1).
  - Insert at index 2? Shift everything from index 2 onward one position right. O(n).
  - Size fixed at creation — can't grow.

- **ArrayList**: Wraps an internal `Object[] array` that grows when full (doubles size).
  - Same as Array for access/insertion/deletion.
  - But it auto-grows, so you can add as many elements as memory allows.
  - Cost of growth: occasionally O(n) when array is full and must copy all elements to a larger array. But amortized O(1) per add.

- **LinkedList**: Chain of nodes. Each node holds data + pointer to next node.
  - Access index 5? Walk from node 0 → 1 → 2 → ... → 5. O(n).
  - But insertion at front (if you have a reference to the front node) = rewire one pointer. O(1).
  - Better when you insert/delete frequently in the middle AND you already have a reference to that position.

**Real-world scenario (why this matters):**
You're building a **browser history** (most recent first). User can:
- Browse forward/backward quickly (random access by index) — ArrayList wins. O(1) access.
- Delete any page in history (middle deletion) — but user rarely deletes, mostly accesses ends. ArrayList is still better.

Now shift to **text editor with undo**: User types, types, types (frequent insertions at the end), then undo (frequent deletions at the end). LinkedList could work, but ArrayList is simpler and just as fast here.

**Decision:** Use **ArrayList** 99% of the time. LinkedList only when you must insert/delete at known positions INSIDE the list (not at ends) and you have that position reference already.

---

### 2️⃣ Stack & Queue Structures — LIFO / FIFO Processing

**What are they?** Structures where you remove elements in a **specific order**.
- **Stack (LIFO)**: Last element added is first removed. Like undo/redo, call stack.
- **Queue (FIFO)**: First element added is first removed. Like a checkout line, BFS, task queue.
- **Priority Queue**: Remove in order of priority, not insertion order.

**The backing structures:**
- **Stack in Java**: Don't use `java.util.Stack` (legacy, synchronized, slow). Use **ArrayDeque as stack**.
  - ArrayDeque is backed by a circular `Object[] array`.
  - Circular array: imagine a tape loop. When you push to a full array, instead of resizing, you wrap around to the beginning.
  - push() = add to top = O(1) amortized.
  - pop() = remove from top = O(1).
  - Why circular? Eliminates the need to shift elements. Regular array would require O(n) shifting on each pop if you always remove from the front.

- **Queue in Java**: **ArrayDeque as queue** (or PriorityQueue if you need priority).
  - ArrayDeque handles both ends: addLast() (enqueue), removeFirst() (dequeue).
  - Circular array again. Push on one end, pop from the other, no shifting needed.
  - offer() = enqueue = O(1).
  - poll() = dequeue = O(1).

- **PriorityQueue**: Backed by a **binary min-heap** (an array-based tree structure).
  - A heap is a complete binary tree stored in an array where parent ≤ children (min-heap).
  - offer() = add element + bubble up to restore heap property = O(log n).
  - poll() = remove root + move last element to root + bubble down = O(log n).
  - Why use it? When you need "give me the smallest element fast" repeatedly (Dijkstra, Kth smallest, scheduling).

**Real-world scenario:**
You're building a **Zoom meeting** with "hand raised" feature.
- Teacher sees a queue of students with raised hands (FIFO — first to raise, first called). → Use **Queue (ArrayDeque)**.
- But what if you want "VIP students get priority"? → Use **PriorityQueue** with comparator sorting by VIP status.

Another scenario: **Expression evaluation** (e.g., "3 + 4 * 2").
- Scan left to right. When you see an operator, you need the most recent operand (last one added). → Use **Stack**.

**Decision:**
- FIFO processing → **ArrayDeque as queue**.
- LIFO (undo/call stack/matching brackets) → **ArrayDeque as stack**.
- Need "best element next" by priority → **PriorityQueue**.

---

### 3️⃣ Maps — Key → Value Lookup

**What are they?** Store key-value pairs. You look up by key, get the value instantly.

**The backing structures:**
- **HashMap**: Backed by a **hash table** (array of buckets).
  - How it works: Hash the key (hash function converts key to an integer) → take modulo (bucket index) → look in that bucket.
  - Bucket contains either a single entry OR (in Java 8+) a chain or tree of entries if multiple keys hash to the same bucket (collision).
  - Best case: hash function is perfect, no collisions → O(1) access.
  - Worst case: all keys hash to the same bucket, must scan the chain/tree → O(n).
  - Load factor: when map is ~75% full, it doubles size and rehashes all entries. Cost: O(n), but amortized O(1) per insert.

- **LinkedHashMap**: Same as HashMap but maintains **insertion order** using a doubly linked list threaded through all entries.
  - Iteration order = insertion order (or access order if you construct it that way).
  - Same O(1) access as HashMap, but extra pointers for linking = more memory.

- **TreeMap**: Backed by a **red-black tree** (self-balancing BST).
  - Keys are always sorted.
  - get/put/remove = O(log n) (must traverse tree).
  - Can do range queries: `.subMap(fromKey, toKey)` = all keys in that range.
  - Use when you need sorted order.

**Real-world scenario:**
You're building **autocomplete** for a text editor.
- User types "app", you show all words starting with "app".
- If you store words in HashMap, you can't easily find all words with that prefix (no sorted order). You'd scan all keys, O(n).
- If you store in TreeMap (sorted by word), you do `.subMap("app", "apq")` to get the range. O(log n) to find boundaries + O(k) to return k results.
- But autocomplete is really a **Trie** problem (specialized DS), which is O(k) directly.

Another scenario: **User by ID lookup**.
- You have 1M users. Look up by ID must be instant → HashMap. O(1).
- You also need "list all users created between Jan 1 and Jan 31" → TreeMap with key = creation date. O(log n) to find Jan 1, O(log n) to find Jan 31, O(days) to iterate.

**Decision:**
- Need O(1) lookup by key → **HashMap**.
- Need sorted key order (range queries, iteration in sorted order) → **TreeMap**.
- Need insertion order preserved AND O(1) lookup → **LinkedHashMap**.
- Choosing wrong: HashMap for range queries → force O(n) scan. TreeMap for simple lookups → O(log n) instead of O(1).

---

### 4️⃣ Sets — Unique Elements, No Values

**What are they?** Like maps but only care about keys. Fast membership check (is X in set?).

**The backing structures:**
- **HashSet**: Backed by HashMap (stores each element as a key with dummy value).
  - Contains, add, remove = O(1) average case.
  - No order.

- **LinkedHashSet**: Backed by LinkedHashMap.
  - Contains, add, remove = O(1).
  - Iteration in insertion order.

- **TreeSet**: Backed by TreeMap.
  - Contains, add, remove = O(log n).
  - Iteration in sorted order.
  - Can do range: `.subSet(fromElem, toElem)`.

**Real-world scenario:**
You're building a **spam filter**. You have a set of known spam emails (10k entries).
- Email arrives: is it in the spam set? → HashSet.contains() = O(1). Check instantly.
- If you used a List and called `.contains()`, that's O(n) — scan all 10k emails. Too slow.

Another scenario: **Duplicate detection in stream**.
- Data stream of user IDs. Detect duplicates in real-time.
- As you read ID, check: is ID in my set? If yes, duplicate. If no, add to set.
- HashSet: O(1) check + O(1) add = fast.
- If you used a sorted stream (TreeSet), you pay O(log n) per operation. Still fast, but unnecessary if you don't need sorted order.

**Decision:**
- Need fast membership check, no order → **HashSet**.
- Need insertion order preserved, membership check → **LinkedHashSet**.
- Need sorted unique elements, range queries → **TreeSet**.
- Wrong choice: HashSet where you need sorted order → must collect to list and sort, O(n log n). TreeSet from start → O(log n) per op.

---

### 5️⃣ Trees — Hierarchical Data

**What are they?** Nodes connected by parent-child edges. Root has no parent. Leaves have no children.

**The backing structures:**
- **Binary Tree (TreeNode)**: Each node has at most 2 children (left, right).
  - LeetCode pre-defines TreeNode with value + left/right pointers.
  - No internal array — you manually link nodes.
  
- **Binary Search Tree (BST)**: Binary tree where left subtree keys < node key < right subtree keys.
  - Search: go left if target < node, right if target > node. O(log n) if balanced, O(n) if skewed.
  - Insert: follow same path, create node at leaf.
  - Real BSTs like TreeMap use **red-black trees** (self-balancing) to guarantee O(log n).

**Real-world scenario:**
You're building **file system hierarchy** (folders within folders).
- Root folder has subfolders. Each subfolder is a node with children.
- Trees naturally represent this. Binary trees aren't needed here (folders can have >2 subfolders). General tree (n children per node) is better.

Another scenario: **Expression tree** (parse "3 + 4 * 2").
- Root is the last operator evaluated (+ in this case, due to precedence).
- Left subtree = "3", right subtree = "4 * 2".
- Evaluate recursively: left + right.

Yet another: **Search optimization** using BST.
- You have sorted data (1000 book IDs). Store in BST.
- Linear search: O(n).
- BST search: O(log n).
- But ArrayList with binary search is O(log n) too and simpler. Choose BST only if you're frequently inserting/deleting in sorted data.

**Decision:**
- Need hierarchy visualization → Tree.
- Need frequent search in sorted data that's also changing → TreeMap (red-black tree) or BST.
- Static sorted data → ArrayList + binary search is simpler.
- Need all ancestors of a node? Pointers to parent help. But store them explicitly.

---

### 6️⃣ Graphs — Networks of Connections

**What are they?** Nodes connected by edges. Unlike trees, cycles allowed. Edges can be directed (A → B) or undirected (A ↔ B).

**The backing structures:**
- **Adjacency List** (`List<List<Integer>> adj`):
  - For each node, store list of its neighbors.
  - Space: O(V + E) where V = nodes, E = edges.
  - Traversal (DFS/BFS): O(V + E).
  - Dense graph (many edges): uses less space than matrix.

- **Adjacency Matrix** (`int[][] adj`):
  - For each pair of nodes, mark 1 if edge exists, 0 if not.
  - Space: O(V²) always.
  - Check "is there edge A → B?": O(1).
  - Traversal: O(V²) because you must check all V² cells.
  - Dense graph: might be competitive with list if V is small.

**Real-world scenario:**
You're building **social network**. Nodes = users. Edges = friendships.
- 1M users (nodes), 100M friendships (edges).
- Adjacency matrix: 1M × 1M = 1 trillion cells. Massive memory. Don't do this.
- Adjacency list: store 1M lists, total 2 × 100M entries (each edge stored twice for undirected). ~800 MB. Feasible.
- "Is user A friend with user B?" With adjacency list, check A's neighbors list. If few friends, O(1)–O(k) where k = friends. With matrix, O(1) direct lookup but you paid O(V²) memory upfront.

Another scenario: **Road network** (cities = nodes, roads = edges).
- 500 cities, ~1500 roads (edges).
- Adjacency list: 500 lists, total ~3000 entries. Fast traversal.
- Traversal algorithm (DFS/BFS): O(500 + 1500) = O(2000). Instant.

**Decision:**
- Sparse graph (few edges relative to V²) → **Adjacency list** (always better).
- Dense graph (many edges, close to V²) → Consider matrix, but list usually still wins.
- Frequent "is edge X → Y?" queries AND matrix is memory-feasible → **Adjacency matrix** for O(1) check.

---

### 7️⃣ Specialized Structures — Trie & Union-Find

**What are they?** Optimized for specific problem types.

**Trie (Prefix Tree):**
- Backed by a tree of nodes, each node has 26 children (for English alphabet).
- Each node = a character. Paths from root = words.
- Search/insert/delete by spelling: O(L) where L = word length. No hashing, deterministic.
- Use for: autocomplete, spell check, IP routing.

**Real-world scenario:**
Autocomplete for "app". Build a trie:
```
        root
        /
       a
       |
       p
       |
       p → (end-of-word marker)
        / | \
       l  l  r
       e  e  o
       ... ...
```
Type "app" → traverse root → 'a' → 'p' → 'p' = O(3) = O(L). Then iterate all descendants (other words starting with "app") = O(k) where k = results.

With HashMap: must scan all keys, check startsWith(), O(n).

**Union-Find (Disjoint Set Union):**
- Backed by a forest of trees + path compression + union by rank.
- Two operations: union(A, B) (merge components A and B) and find(A) (which component is A in?).
- union() = O(α(n)) ≈ O(1) amortized, where α = inverse Ackermann (grows so slow it's effectively constant).
- Use for: connected components, cycle detection, Kruskal's MST, checking if graph is bipartite.

**Real-world scenario:**
You're given a graph: nodes = people, edges = friendships. How many friend groups (connected components)?
- DFS/BFS: O(V + E), fine.
- Union-Find: union(A, B) for each edge, then count unique find(X) results. Also O(V + E) but cleaner code, O(1) per edge (amortized).

---

### 8️⃣ Strings & Char Operations — Text Processing

**What are they?** Immutable text (String) and mutable alternatives (StringBuilder, char array).

**Backing structures:**
- **String**: Immutable array of chars. Once created, can't change.
  - Any "modification" (concat, replace) creates a new String.
  - In a loop: `result += c` creates n new Strings. O(n²) total time.
  - Solution: use StringBuilder. Backed by a mutable char array.

- **StringBuilder**: Mutable, array-backed. append() = add to end = amortized O(1).
  - In a loop: O(n) total time.
  - Create, modify 1000 times, toString() = O(n) to convert array back to String.

- **char[]**: Raw character array. Fastest when you need to modify in place (e.g., reverse a string).
  - `str.toCharArray()` = O(n), then modify = O(1) per char, `new String(charArray)` = O(n).

**Real-world scenario:**
You're building a **text editor** that shows real-time character count.
- User types 1000 characters fast.
- Using String: each char added = new String created. 1000 Strings created. O(1000²) = 1M operations. Noticeable lag.
- Using StringBuilder: 1000 appends = O(1000) total. Instant.

Another scenario: **Reverse a string**.
- Input: "hello", output: "olleh".
- String approach: create new String char-by-char in reverse. O(n) and allocates new String.
- char[] approach: toCharArray (O(n)), swap in place (O(n)), toString (O(n)) = O(n) total, in-place modification.

**Decision:**
- String manipulation in loops → **StringBuilder**.
- String in-place modification (reverse, swap) → **char[]**.
- Simple concatenation outside loops → String (readable, not performance-critical).

---

## 🧭 The Master Decision Table — When to Use Each

| **You need...** | **Use this** | **NOT this** | **Why (the backing structure explains it)** |
| --- | --- | --- | --- |
| **Ordered list, random access** | ArrayList | LinkedList | ArrayList backed by array → direct index jump O(1). LinkedList must walk O(n). |
| **Ordered list, frequent middle inserts** | LinkedList* | ArrayList | LinkedList rewires pointers O(1) once you have position. ArrayList shifts O(n). *Only if you have reference to position. |
| **LIFO (undo, call stack)** | ArrayDeque stack | java.util.Stack | ArrayDeque circular array, no shifting. Stack is legacy, synchronized, slow. |
| **FIFO (queue, BFS)** | ArrayDeque queue | LinkedList queue | Both work, but ArrayDeque circular array avoids the allocation overhead. |
| **Priority ordering** | PriorityQueue | sorted array | PQ backed by binary heap O(log n) per insert. Sorted array O(n) insertion sort. |
| **O(1) key lookup** | HashMap | TreeMap | HashMap hash table O(1) avg. TreeMap red-black tree O(log n) every time. |
| **Sorted keys + range queries** | TreeMap | HashMap | TreeMap tree-backed, can do .subMap(). HashMap no order, force O(n) scan. |
| **Preserve insertion order + O(1) lookup** | LinkedHashMap | HashMap | LinkedHashMap threads linked list through entries. HashMap no order. |
| **O(1) membership check** | HashSet | List.contains() | HashSet backed by HashMap. List backed by array, must scan O(n). |
| **Sorted unique elements** | TreeSet | HashSet | TreeSet red-black tree, iteration sorted. HashSet hash table, no order. |
| **Hierarchy (parent-child)** | Tree (TreeNode) | Flat list | Tree structure naturally models hierarchy. List forces artificial indexing. |
| **Search in sorted, changing data** | TreeMap | ArrayList+binarySearch | TreeMap self-balances on insert/delete. ArrayList requires re-sort O(n log n) per insert. |
| **Sparse graph (few edges)** | Adjacency list | Adjacency matrix | List O(V + E) space. Matrix O(V²) space — wasteful when E << V². |
| **Dense graph + frequent edge checks** | Adjacency matrix | Adjacency list | Matrix O(1) edge check. List O(degree) check. But only if E ≈ V² and memory okay. |
| **Prefix matching (autocomplete)** | Trie | HashMap | Trie O(L) per search, all prefixes O(L + k). HashMap O(n) scan + startsWith() check. |
| **Connected components** | Union-Find | DFS/BFS | Both O(V + E), but DSU is cleaner code + O(1) per operation (amortized). |
| **String building in loop** | StringBuilder | String += | StringBuilder amortized O(1) per append. String += creates new String each time, O(n²) total. |
| **In-place char modification** | char[] | String | char[] mutable, modify O(1) per char. String immutable, force new String O(n). |

---

## ⚡ Complexity Quick Reference

| Data Structure | Access | Search | Insert | Delete | Space | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| **Array** | O(1) | O(n) | O(n)* | O(n)* | O(n) | *At arbitrary position. End is O(1). |
| **ArrayList** | O(1) | O(n) | O(n)* | O(n)* | O(n) | Same as array. Amortized O(1) per append. |
| **LinkedList** | O(n) | O(n) | O(1)** | O(1)** | O(n) | **If you have position reference. Walk otherwise O(n). |
| **ArrayDeque (Stack)** | — | — | O(1) | O(1) | O(n) | Push/pop at one end. Circular buffer. |
| **ArrayDeque (Queue)** | — | — | O(1) | O(1) | O(n) | Add/remove at opposite ends. Circular buffer. |
| **PriorityQueue** | O(1) peek | O(n) | O(log n) | O(log n) | O(n) | Binary min-heap. offer/poll. |
| **HashMap** | — | O(1)*** | O(1)*** | O(1)*** | O(n) | ***Average case. Worst O(n) if all collisions. |
| **LinkedHashMap** | — | O(1) | O(1) | O(1) | O(n) | Same as HashMap + insertion order. |
| **TreeMap** | — | O(log n) | O(log n) | O(log n) | O(n) | Red-black tree. Always sorted. |
| **HashSet** | — | O(1)*** | O(1)*** | O(1)*** | O(n) | Backed by HashMap. |
| **LinkedHashSet** | — | O(1) | O(1) | O(1) | O(n) | Backed by LinkedHashMap. |
| **TreeSet** | — | O(log n) | O(log n) | O(log n) | O(n) | Backed by TreeMap. Sorted. |
| **Trie** | — | O(L) | O(L) | O(L) | O(ALPHABET × nodes) | L = word length. Prefix queries O(L + k). |
| **Union-Find** | find() O(α(n)) | — | union() O(α(n)) | — | O(n) | α = inverse Ackermann ≈ const. |

---

## 🧾 Quick Decision Checklist

When facing a problem, ask:

1. **Do you iterate or look up by position?**
   - Iterate: ArrayList, LinkedList (both fine if only iterating).
   - Look up by index 5: ArrayList. LinkedList too slow.

2. **Do you modify often? Where (ends or middle)?**
   - Ends only (queue, stack): ArrayDeque.
   - Middle frequently: LinkedList only if you have position ref.
   - Rarely: ArrayList (simpler).

3. **Do you need key-value or just members?**
   - Key-value: Map (HashMap, TreeMap, LinkedHashMap).
   - Just members: Set (HashSet, TreeSet, LinkedHashSet).

4. **Do you need order?**
   - No order: HashMap, HashSet.
   - Insertion order: LinkedHashMap, LinkedHashSet.
   - Sorted: TreeMap, TreeSet.

5. **Do you need O(1) lookup or O(log n) is okay?**
   - Must be O(1): HashMap, HashSet.
   - O(log n) acceptable: TreeMap, TreeSet.

6. **Is the data structure changing (insertions/deletions)?**
   - Yes, frequently, need sorted: TreeMap (O(log n) per op).
   - No, static: ArrayList + binary search (O(log n) per search).

7. **Is it a graph problem?**
   - Few edges (sparse): Adjacency list.
   - Many edges (dense) + need edge checks: Adjacency matrix.

8. **Is it a string/text problem?**
   - Building strings: StringBuilder.
   - Modifying chars in place: char[].
   - Prefix searches: Trie.

---

## 🔄 Real Interview Example — Putting It Together

**Problem:** "Design a system to detect duplicate emails in a stream of 1 million emails. Each email arrives once. Query: 'is this email new or duplicate?'"

**Wrong approach:** Use ArrayList.
- Check: `emailList.contains(email)` = O(n) per email. Total: O(n²) = 1 trillion operations. Timeout.

**Correct approach:** Use HashSet.
- Check: `emailSet.contains(email)` = O(1) per email. Total: O(n) = 1 million operations. Instant.
- **Why?** HashSet backed by hash table. Hash the email → jump to bucket → check. No scanning needed.

**Another query:** "Give me all unique emails in order they arrived."
- HashSet loses insertion order.
- Use **LinkedHashSet** instead. Same O(1) operations, but iteration = insertion order.

**Another query:** "Give me all unique emails, sorted alphabetically."
- LinkedHashSet loses sorted order.
- Use **TreeSet** instead. O(log n) per insert (slower, but sorted).
- Or use **LinkedHashSet to collect (O(1) per op) + sort() at the end** if n is small enough.

The **backing structure** (hash table vs tree vs linked list) determined the choice each time.

---

## 📚 Next Steps

Once you've internalized these 8 categories and the decision table:

1. Read **`DSA/Foundation/java-collections-visual.md`** — dive deeper into HOW each backing structure works internally
2. Read **`DSA/Foundation/syntax-quick-card.md`** — the creation syntax you'll blank on
3. Pick a Playbook (trees, graphs, hashmaps, etc.) from **`DSA/Interview/`** — apply the theory to real problems

Good luck. The backing structure explains everything. Always ask yourself: "What's storing the data inside?" The answer is the backing structure, and it explains every O(1) vs O(n) trade-off.
