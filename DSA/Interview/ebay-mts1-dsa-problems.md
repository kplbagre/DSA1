# eBay MTS 1 — DSA Interview Problem Set

> **Role:** MTS 1 (Member Technical Staff 1) — Backend
> **Round:** Onsite R1 — 2 problems in 60 min on CodeSignal (provided laptop). Second problem unlocks after first.
> **Format:** Live coding. Must reach working solution. OOP class design expected even in DSA questions.
> **Source:** 30+ searches across LC Discuss, Glassdoor, Blind, 1Point3Acres, CodingKaro, Taro (Jul 2026)

> ⭐ **Tier 1** = Confirmed in 2+ independent reports
> 🔹 **Tier 2** = 1 confirmed report OR strong company-tag corroboration
> 🧩 **Tier 3** = eBay company tag on LC; no specific onsite report — pattern practice, not targeted prep
> ⚠️ **Custom** = No LC # — problem reconstructed from interview reports; approach and assumptions stated explicitly
> *(OA)* in a problem title = CodeSignal Online Assessment round — confirmed from this candidate's own eBay OA (2025); scenario-wrapped, thin-cover format (see Section 23)

---

## Table of Contents

1. [Delete Nth Node from End of List](#1-delete-nth-node-from-end-of-list--lc-19) ⭐
2. [HTML/XML Parser → N-ary Tree](#2-htmlxml-parser--n-ary-tree--custom) ⭐ ⚠️ Custom
3. [Balanced Sum Subarray](#3-balanced-sum-subarray--custom) ⭐ ⚠️ Custom
4. [Binary Tree Subtree Counting](#4-binary-tree-subtree-counting--custom) ⭐ ⚠️ Custom
5. [Weighted Grouping with OOP Design](#5-weighted-grouping-with-oop-design--custom) ⭐ ⚠️ Custom
6. [Number of Islands](#6-number-of-islands--lc-200) ⭐ (cross-linked)
7. [Reverse Pairs](#7-reverse-pairs--lc-493) 🔹
8. [Implement `ls -r` with Unit Tests](#8-implement-ls--r-with-unit-tests--custom) 🔹 ⚠️ Custom
9. [Sieve of Eratosthenes — Count Primes](#9-sieve-of-eratosthenes--count-primes--lc-204) 🔹
10. [🧩 Seen-Once Quick-Reference Index](#10-seen-once-quick-reference-index) *(scan under pressure — full solutions below)*
11. [Move Zeroes — LC 283](#11-move-zeroes--lc-283) 🧩
12. [Best Time to Buy & Sell Stock II — LC 122](#12-best-time-to-buy--sell-stock-ii--lc-122) 🧩
13. [Merge Intervals — LC 56](#13-merge-intervals--lc-56) 🧩
14. [3Sum — LC 15](#14-3sum--lc-15) 🧩
15. [LRU Cache — LC 146](#15-lru-cache--lc-146) 🧩
16. [Merge K Sorted Lists — LC 23](#16-merge-k-sorted-lists--lc-23) 🧩
17. [Top K Frequent Elements — LC 347](#17-top-k-frequent-elements--lc-347) 🧩
18. [Course Schedule — LC 207](#18-course-schedule--lc-207) 🧩
19. [Word Ladder — LC 127](#19-word-ladder--lc-127) 🧩
20. [All Nodes Distance K — LC 863](#20-all-nodes-distance-k--lc-863) 🧩
21. [N-Queens — LC 51](#21-n-queens--lc-51) 🧩
22. [Sudoku Solver — LC 37](#22-sudoku-solver--lc-37) 🧩
23. [How eBay Frames OA Problems](#23-how-ebay-frames-oa-problems) *(Scenario-Strip Guide — read before OA)*
24. [Building Obstacles and Blocks — OA](#24-building-obstacles-and-blocks--oa) ⭐ ⚠️ Custom
25. [Sequential ID Verification Event Queue — OA](#25-sequential-id-verification-event-queue--oa) ⭐ ⚠️ Custom
26. [Longest Contiguous Substring — OA](#26-longest-contiguous-substring--oa) ⭐ ⚠️ Custom
27. [Counting Good Tuples — OA](#27-counting-good-tuples--oa) ⭐ ⚠️ Custom

---

---

## 1. Delete Nth Node from End of List — LC 19

**Difficulty:** Medium | **Pattern:** Two Pointers (Fast + Slow)
**Confirmed in:** LC Discuss eBay BLR MTS1 post + CodingKaro 2025

> **eBay framing used:** *"An append-only transaction log exists. A fraudulent transaction can occur at a known position from the end. Remove it without traversing the log more than once and without knowing the total length."*

---

### 🎯 Problem Statement

Given the head of a singly linked list and an integer `n`, remove the `n`th node from the **end** of the list and return the head.

```
Example:
List: 1 → 2 → 3 → 4 → 5,  n = 2

Output: 1 → 2 → 3 → 5
         (node with value 4 removed — it's 2nd from end)
```

**Constraint (eBay emphasis):** Do it in **one pass** — the interviewer explicitly said the log can be huge; you cannot traverse it twice.

---

### 🧠 Discussion — How to Think About This

**The core problem:** You don't know the length of the list. The nth node from the end, if the list has length L, is at position `L - n` from the head (0-indexed).

Without knowing L, you'd have to traverse twice (once to count L, once to reach `L - n`).

**The one-pass insight:** Use two pointers, `fast` and `slow`, both starting at a dummy node before head. Move `fast` forward by `n+1` steps first. Then move both together until `fast` reaches null. At that point, `slow` is at the node BEFORE the one to delete.

Why `n+1`? Because you want `slow` to stop at the predecessor of the target, not the target itself — you need to relink `slow.next = slow.next.next`.

---

### 🐌 Brute Force — Two Pass O(L) / O(1)

First pass: count list length L. Second pass: reach node at position `L - n - 1` (the predecessor).

```java
// Two-pass — count first, then delete (NOT acceptable at eBay per interview framing)
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;

    // Pass 1: count length
    int length = 0;
    ListNode curr = head;
    while (curr != null) {
        length++;
        curr = curr.next;
    }

    // Pass 2: reach predecessor of target node
    int stepsToTarget = length - n - 1;
    ListNode prev = dummy;
    for (int i = 0; i < stepsToTarget; i++) {
        prev = prev.next;
    }

    // Delete the target
    prev.next = prev.next.next;
    return dummy.next;
}
```

**Why rejected at eBay:** "Without traversing the log more than once" is the stated constraint. Two-pass fails it.

---

### 💡 Idea Behind Optimisation — Gap of N+1

Maintain a fixed gap of `n+1` between `fast` and `slow`. When `fast` reaches null:
- `fast` is at null (1 past the last node)
- `slow` is `n+1` behind null = at position `(L+1) - (n+1) = L - n` from the dummy start
- In 1-indexed list terms, `slow` is at the node BEFORE the nth-from-end node

This lets you relink in one pass.

---

### 🎨 Visual — Two Pointer Gap Logic

```
List: dummy → 1 → 2 → 3 → 4 → 5 → null, n = 2

Step 1: Advance fast by n+1 = 3 steps from dummy:

dummy → 1 → 2 → 3 → 4 → 5 → null
  ↑              ↑
slow           fast
(slow at dummy, fast at node '3')

Step 2: Advance both until fast == null:

dummy → 1 → 2 → 3 → 4 → 5 → null
              ↑              ↑
            slow            fast

slow.next is node '4' (2nd from end = n=2). ✅

Step 3: slow.next = slow.next.next
dummy → 1 → 2 → 3 → 5 → null  (node 4 removed)

KEY INVARIANT:
  fast is always exactly n+1 steps ahead of slow.
  When fast == null, slow is at the predecessor of the nth-from-end node.
  Dummy node handles the edge case where the HEAD itself is deleted (n == L).
```

---

### 🚀 Optimal Java Solution — One Pass

**Steps in plain English:**
1. Create a `dummy` node before head (handles edge case when head is deleted).
2. Set both `fast` and `slow` to `dummy`.
3. Advance `fast` by exactly `n+1` steps.
4. Advance both `fast` and `slow` together until `fast == null`.
5. Delete `slow.next` (the target) by relinking: `slow.next = slow.next.next`.
6. Return `dummy.next`.

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;

    ListNode fast = dummy;
    ListNode slow = dummy;

    // Step 3: advance fast by n+1 steps
    for (int i = 0; i <= n; i++) {
        fast = fast.next;
    }

    // Step 4: advance both until fast reaches null
    while (fast != null) {
        fast = fast.next;
        slow = slow.next;
    }

    // Step 5: slow is now at the PREDECESSOR of the target — delete target
    slow.next = slow.next.next;

    return dummy.next;
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(L) | Single pass through list of length L |
| **Space** | O(1) | Two pointer variables only |

---

### 🔁 Follow-Up Questions + Variants

**Q1: What if n == length of the list? (delete the head)**
> `slow` stays at `dummy`. `slow.next = slow.next.next` removes `head`. `dummy.next` correctly returns the new head. The dummy node exists exactly for this case — without it, you'd need a null check.

**Q2: What if n > length of the list?**
> `fast` would reach null before completing the n+1 advance loop. Add a guard:
> ```java
> for (int i = 0; i <= n; i++) {
>     if (fast == null) {
>         throw new IllegalArgumentException("n exceeds list length");
>     }
>     fast = fast.next;
> }
> ```

**Q3: eBay variant — "the fraudulent transaction is always in the middle third of the log. Find and remove it."**
> This becomes "remove the node at position L/3 from the end." Same two-pointer pattern — advance fast by `L/3 + 1` steps. But now you need to know L first, so it's two passes unless L is given. If L is given: pure one-pass.

**Q4: What if the list is doubly linked? What changes?**
> The deletion itself is O(1) because you have `slow.next.prev`. Everything else is the same — same two-pointer approach to FIND the target.

**Q5: What if you have multiple fraudulent nodes at known positions [n1, n2, n3] from the end?**
> Sort positions descending. Delete one at a time from the end (each delete doesn't affect the positions of the remaining targets since they're all before the deleted one). Three separate passes — or track all predecessors in one pass using multiple "slow" pointers.

---

---

## 2. HTML/XML Parser → N-ary Tree — Custom

**Difficulty:** Medium | **Pattern:** Stack-Based Parsing + OOP Tree Design
**Confirmed in:** LC Discuss eBay MTS1 BLR post + CodingKaro 2025

> ⚠️ **Assumption:** The interviewer does NOT require working code — approach and class design are primary. "Extensions" (addNode, deleteNode, updateData) are design discussion.
> **Input format assumed:** Well-formed XML-like string, e.g. `<root><child>text</child><sibling/></root>`. No attributes. Tags are either opening `<tag>`, closing `</tag>`, or self-closing `<tag/>`.

---

### 🎯 Problem Statement

Given a well-formed HTML/XML string with nested tags, write:
1. A `parseHTML(String xml)` function that constructs an **N-ary tree** from it.
2. (Extension) `addNode(String parentTag, String childTag)` — add a child.
3. (Extension) `deleteNode(String tag)` — remove a node and all its children.
4. (Extension) `updateData(String tag, String data)` — update text content of a node.

```
Example Input:
<root>
  <orders>
    <order/>
    <order/>
  </orders>
  <metadata>
    <version>2.0</version>
  </metadata>
</root>

Expected Tree:
root
├── orders
│   ├── order
│   └── order
└── metadata
    └── version (data="2.0")
```

---

### 🧠 Discussion — How to Think About This

**Start with the class design — this is what the interviewer cares about first.**

An N-ary tree node needs:
- `tag` — the element name
- `data` — text content (empty for structural tags)
- `children` — list of child nodes
- `parent` — back-pointer (needed for deleteNode)

**Parsing strategy — stack-based (the standard for nested structure):**
- Opening tag `<tag>`: create a new node, push onto stack, attach to parent (stack top).
- Closing tag `</tag>`: pop the stack (we're done with this subtree).
- Self-closing `<tag/>`: create a leaf node, attach to parent, don't push (no children expected).
- Text content: attach as `data` on the current stack-top node.

**Why stack?** Nested tags are a LIFO problem — the most recently opened tag is the most recently closed. The call stack of recursion IS a stack. An explicit stack avoids deep recursion issues for large documents.

---

### 🎨 Visual — Stack State During Parse

```
Input: <root><orders><order/></orders></root>

Token stream: <root>  <orders>  <order/>  </orders>  </root>

Stack state:
After <root>   :  [root]
After <orders> :  [root, orders]    (orders added as child of root)
After <order/> :  [root, orders]    (order added as child of orders, NOT pushed — self-closing)
After </orders>:  [root]            (pop orders)
After </root>  :  []                (pop root)

Tree built:
root
└── orders
    └── order

KEY INVARIANT:
  Stack top = "currently open parent" — all parsed tags attach here as children.
  Opening tag → push; Closing tag → pop; Self-closing → attach only (no push/pop).
  Stack empty at end = well-formed input.
```

---

### 🚀 Java — Class Design + Parser

**Steps in plain English:**
1. Define `Node` class with `tag`, `data`, `children`, `parent`.
2. Tokenize the XML string (split into tags and text content).
3. Use a stack to track the "currently open" parent.
4. On opening tag: create node, attach to parent, push.
5. On closing tag: pop.
6. On self-closing: create node, attach to parent, do not push.
7. On text: set as `data` on current stack-top node.

```java
// Node class — the tree structure
public class Node {
    public String tag;
    public String data;
    public Node parent;
    public List<Node> children;

    public Node(String tag) {
        this.tag = tag;
        this.data = "";
        this.children = new ArrayList<>();
    }

    public void addChild(Node child) {
        child.parent = this;
        this.children.add(child);
    }
}

// Parser — stack-based, O(n) where n = input length
public class HtmlParser {
    private Node root;

    public Node parseHTML(String xml) {
        // Split input into tokens — opening, closing, self-closing tags and text
        String[] tokens = xml.trim().split("(?=<)|(?<=>)");

        Deque<Node> stack = new ArrayDeque<>();
        root = null;

        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }

            if (t.startsWith("</")) {
                // Closing tag — pop current node off the stack
                stack.pop();

            } else if (t.startsWith("<") && t.endsWith("/>")) {
                // Self-closing tag — leaf node, no push
                String tagName = t.substring(1, t.length() - 2).trim();
                Node leaf = new Node(tagName);
                if (!stack.isEmpty()) {
                    stack.peek().addChild(leaf);
                }

            } else if (t.startsWith("<")) {
                // Opening tag — create node, push onto stack
                String tagName = t.substring(1, t.length() - 1).trim();
                Node node = new Node(tagName);
                if (stack.isEmpty()) {
                    // First opening tag = root
                    root = node;
                } else {
                    stack.peek().addChild(node);
                }
                stack.push(node);

            } else {
                // Text content — attach to current stack-top node
                if (!stack.isEmpty()) {
                    stack.peek().data = t;
                }
            }
        }

        return root;
    }

    // Extension 1: add child node under a parent tag
    public boolean addNode(String parentTag, String childTag) {
        Node parent = findNode(root, parentTag);
        if (parent == null) {
            return false;
        }
        parent.addChild(new Node(childTag));
        return true;
    }

    // Extension 2: delete node and its entire subtree
    public boolean deleteNode(String tag) {
        Node target = findNode(root, tag);
        if (target == null || target == root) {
            // Cannot delete root or non-existent node
            return false;
        }
        // Remove from parent's children list
        target.parent.children.remove(target);
        target.parent = null;
        return true;
    }

    // Extension 3: update text content of a node
    public boolean updateData(String tag, String newData) {
        Node target = findNode(root, tag);
        if (target == null) {
            return false;
        }
        target.data = newData;
        return true;
    }

    // DFS helper — find first node with given tag
    private Node findNode(Node node, String tag) {
        if (node == null) {
            return null;
        }
        if (node.tag.equals(tag)) {
            return node;
        }
        for (Node child : node.children) {
            Node result = findNode(child, tag);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
```

---

### ⏱️ Complexity

| Operation | Time | Space |
|---|---|---|
| `parseHTML` | O(n) | O(d) — d = max nesting depth (stack) |
| `addNode` | O(n) | O(d) — DFS to find parent |
| `deleteNode` | O(n) | O(d) — DFS to find target |
| `updateData` | O(n) | O(d) — DFS to find target |

> Improvement: use a `Map<String, Node>` index (tag → node) for O(1) lookups on add/delete/update. Trade-off: assumes tag names are unique. For duplicate tags (e.g., multiple `<order>` nodes) — use tag + path as the key, or return all matches.

---

### 🔁 Follow-Up Questions + Variants

**Q1: What if tag names are NOT unique? (Multiple `<order>` nodes)**
> `findNode` should return `List<Node>` instead of a single node. Operations take an optional `path` parameter (e.g., `root/orders/order[0]`) to disambiguate. XPath uses exactly this.

**Q2: What if XML is malformed (unclosed tag)?**
> Stack will be non-empty at the end. Check `!stack.isEmpty()` after parsing and throw `MalformedXMLException`. Or for partial robustness: on encountering a closing tag that doesn't match the stack top, skip and log a warning.

**Q3: What if you need to serialize the tree back to XML?**

**Pattern:** DFS preorder — on entering a node output the opening tag, recurse into children, on exiting output the closing tag. Leaf nodes with data emit `<tag>data</tag>`. Empty leaf nodes emit `<tag/>` (self-closing).

**Steps in plain English:**

1. If the node has children: emit `<tag>`, recurse each child, emit `</tag>`.
2. If the node is a leaf with data: emit `<tag>data</tag>`.
3. If the node is an empty leaf: emit `<tag/>`.

```java
public String serialize(Node root) {
    if (root == null) {
        return "";
    }
    StringBuilder sb = new StringBuilder();
    serializeHelper(root, sb);
    return sb.toString();
}

private void serializeHelper(Node node, StringBuilder sb) {
    if (!node.getChildren().isEmpty()) {
        // Has children: opening tag, recurse, closing tag
        sb.append("<").append(node.getTag()).append(">");
        for (Node child : node.getChildren()) {
            serializeHelper(child, sb);
        }
        sb.append("</").append(node.getTag()).append(">");
    } else if (!node.getData().isEmpty()) {
        // Leaf with text content
        sb.append("<").append(node.getTag()).append(">")
          .append(node.getData())
          .append("</").append(node.getTag()).append(">");
    } else {
        // Empty leaf — self-closing
        sb.append("<").append(node.getTag()).append("/>");
    }
}
```

> **Round-trip test:** `parseHTML(serialize(root))` should produce an equivalent tree — good test to mention in an interview.

**Q4: Slight variant — "parse JSON instead of XML."**
> Stack approach still applies for nested `{}` and `[]`. Opening `{` pushes a map-node, `[` pushes an array-node. Closing `}` or `]` pops. Key-value pairs are added to the current top of stack.

**Q5: How would you make addNode and deleteNode thread-safe?**
> Use `ReadWriteLock` on the tree: reads (find, updateData) take read lock; writes (addNode, deleteNode) take write lock. Alternatively, use a concurrent tree structure (ConcurrentHashMap for the index + lock striping per subtree).

---

---

## 3. Balanced Sum Subarray — Custom

**Difficulty:** Medium | **Pattern:** Prefix Sums + Two-Pointer Enumeration
**Confirmed in:** LC Discuss post #7581760 + CodingKaro 2025

> ⚠️ **Problem reconstructed from reports.** Stated as: *"Find the maximum balanced sum subarray where the sum of the first half equals the sum of the second half (subarray length must be even)."*
> **Assumption:** "Maximum" = maximum LENGTH (not maximum sum). Subarray is contiguous. "Balanced" = first half sum exactly equals second half sum.

---

### 🎯 Problem Statement

Given an integer array `nums`, find the **maximum length contiguous subarray** of **even length** such that the sum of the first half equals the sum of the second half. Return its length (or 0 if none exists).

```
Example 1:
nums = [1, 2, 3, 3, 2, 1, 4, 4]

Valid balanced subarrays:
  [1, 2, 3 | 3, 2, 1] → first=[1,2,3] sum=6, second=[3,2,1] sum=6 ✓ length=6
  [4 | 4]              → first=[4] sum=4, second=[4] sum=4       ✓ length=2
  [1 | 1]              → length=2 ✓ (using indices 0 and 5... wait no — not contiguous)

Maximum balanced length = 6 ✓

Example 2:
nums = [1, 2, 3, 4]

[1 | 2] sum 1 ≠ 2 ✗
[1,2 | 3,4] sum 3 ≠ 7 ✗
[2 | 3] sum 2 ≠ 3 ✗
[2,3 | 4,?] — no 4-element subarray starting at 1 ✗
[3 | 4] sum 3 ≠ 4 ✗

Maximum balanced length = 0

Example 3:
nums = [1, 3, 2, 2, 3, 1]
prefix = [0, 1, 4, 6, 8, 11, 12]

[l=0, r=5] length=6, mid=2:
  first half [0..2] = prefix[3]-prefix[0] = 6
  second half [3..5] = prefix[6]-prefix[3] = 6 ✓ → length 6
```

---

### 🧠 Discussion — How to Think About This

**Key insight:** Precompute prefix sums. For a subarray `[l, r]` of even length `2k`:
- First half: `[l, l+k-1]`, sum = `prefix[l+k] - prefix[l]`
- Second half: `[l+k, r]`, sum = `prefix[r+1] - prefix[l+k]`
- Balance: `prefix[l+k] - prefix[l] == prefix[r+1] - prefix[l+k]`
- Rearranged: `2 * prefix[l+k] == prefix[l] + prefix[r+1]`

This means for every even-length window `[l, r]`, check the prefix sum condition at the midpoint.

**Is there an O(n) solution?** No known O(n) algorithm for this exact problem in the general case. The O(n²) prefix-sum approach is the expected answer at an interview.

---

### 🐌 Brute Force — O(n³) Naive

For every pair (l, r), compute both half sums from scratch:

```java
// O(n³) — not acceptable
public int maxBalancedLength(int[] nums) {
    int n = nums.length;
    int maxLen = 0;

    for (int l = 0; l < n; l++) {
        for (int r = l + 1; r < n; r += 2) {
            // Only check even-length subarrays
            int len = r - l + 1;
            int mid = l + len / 2;
            int firstSum = 0;
            int secondSum = 0;
            for (int i = l; i < mid; i++) {
                firstSum += nums[i];
            }
            for (int i = mid; i <= r; i++) {
                secondSum += nums[i];
            }
            if (firstSum == secondSum) {
                maxLen = Math.max(maxLen, len);
            }
        }
    }
    return maxLen;
}
```

---

### 💡 Idea Behind Optimisation — Prefix Sums O(n²)

Precompute prefix sums to make each half-sum check O(1). Then enumerate all even-length subarrays.

---

### 🎨 Visual — Prefix Sum Check at Midpoint

```
nums   = [1, 2, 3,  3,  2,  1]
indices:  0  1  2   3   4   5
prefix = [0, 1, 3,  6,  9, 11, 12]  (prefix[0]=0, prefix[i]=sum of nums[0..i-1])

Check subarray [l=0, r=5], length=6, k=3, midpoint=3:
  first  = prefix[l+k] - prefix[l]   = prefix[3] - prefix[0] = 6 - 0 = 6
  second = prefix[r+1] - prefix[l+k] = prefix[6] - prefix[3] = 12 - 6 = 6
  6 == 6 ✓ → balanced, length = 6

Check subarray [l=0, r=3], length=4, k=2, midpoint=2:
  first  = prefix[2] - prefix[0] = 3
  second = prefix[4] - prefix[2] = 9 - 3 = 6
  3 ≠ 6 ✗

KEY INVARIANT:
  For even-length subarray [l, r] with split at midpoint p = l + (r-l+1)/2:
  first half sum  = prefix[p] - prefix[l]
  second half sum = prefix[r+1] - prefix[p]
  Balanced IFF these are equal.
  Enumerate all (l, r) where (r-l+1) is even → O(n²) pairs.
```

---

### 🚀 Optimal Java Solution — O(n²) with Prefix Sums

```java
public int maxBalancedLength(int[] nums) {
    int n = nums.length;

    // Step 1: precompute prefix sums
    int[] prefix = new int[n + 1];
    for (int i = 0; i < n; i++) {
        prefix[i + 1] = prefix[i] + nums[i];
    }

    int maxLen = 0;

    // Step 2: enumerate all even-length subarrays
    for (int l = 0; l < n; l++) {
        // Start r at l+1 (minimum length 2) and increment by 2 (keep even length)
        for (int r = l + 1; r < n; r += 2) {
            int len = r - l + 1;
            int p = l + len / 2;
            // p is the start of the second half

            int firstSum = prefix[p] - prefix[l];
            int secondSum = prefix[r + 1] - prefix[p];

            if (firstSum == secondSum) {
                maxLen = Math.max(maxLen, len);
            }
        }
    }

    return maxLen;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Naive Brute Force | O(n³) | O(1) |
| **Prefix Sums (expected)** | **O(n²)** | **O(n)** |

---

### 🔁 Follow-Up Questions + Variants

**Q1 (confirmed follow-up at eBay): "If one adjustable element is allowed, how does your approach change?"**
> The most natural interpretation: you can change ONE element's value to anything. Options:
> **Interpretation A — zero out one element:** For each element at index `k`, set it to 0 and re-run. But this is O(n³) naively.
> **Better approach:** For each even-length subarray [l, r], compute `diff = firstSum - secondSum`. We can zero out any element in either half. If we zero out element at index `i`:
> - If `i` is in first half: new diff = `diff - nums[i]`. We need `diff - nums[i] == 0`, i.e. find `nums[i] == diff` in first half.
> - If `i` is in second half: new diff = `diff + nums[i]`. We need `diff + nums[i] == 0`, i.e. find `nums[i] == -diff` in second half.
> For each subarray, scan both halves in O(k). Total: O(n² * k) = O(n³) — reduces to: find any even-length subarray that can be balanced by zeroing one element.
> **Interview answer:** "The exact complexity depends on the 'adjustable element' definition — whether it means zero-out, replace, or move. Before coding, I'd ask the interviewer to clarify. If 'zero-out one element': for each subarray, I scan both halves for the element that equals the diff — O(n²) subarrays × O(k) scan = O(n³) worst case. I'd state this explicitly and ask if an approximation or simpler definition is intended."

**Q2: What if the subarray length doesn't need to be even? (Just split at midpoint, allow odd length)**
> For odd length `2k+1`, the "middle element" could be split into either half. Define halves as `[l, l+k-1]` and `[l+k+1, r]` (skip the middle). Same prefix sum approach applies. Middle element is excluded from the balance check — enumerate odd-length subarrays similarly.

**Q3: What if "balanced" means the difference is AT MOST K (not exactly 0)?**
> For each even-length subarray, check `|firstSum - secondSum| <= K`. Same O(n²) structure — just change the equality to inequality. Use `Math.abs(firstSum - secondSum) <= K`.

**Q4: What if you want to find the maximum SUM (not length) balanced subarray?**
> For each balanced subarray (where firstSum == secondSum), track `firstSum + secondSum = 2 * firstSum`. Track the max across all balanced subarrays. Same O(n²) enumeration.

---

---

## 4. Binary Tree Subtree Counting — Custom

**Difficulty:** Medium-Hard | **Pattern:** DFS Postorder — Multi-Property Return
**Confirmed in:** LC Discuss post #7581760 + CodingKaro 2025 (paired with Balanced Sum Subarray — asked as R1 Q2)

> ⚠️ **Problem reconstructed from reports.** Stated as: *"Count the number of subtrees where the height difference between left and right subtrees is ≤ 1 AND the total sum of node values is even."*
> **Assumption:** "Subtree" here means every node and its descendants (including the root and individual leaf nodes). Height of a null node = 0 (so a leaf has height 1). A single node has left_height=0, right_height=0 → diff=0 ≤ 1 ✓.
> **Counted at each node:** Is THIS node the root of a qualifying subtree?

---

### 🎯 Problem Statement

Given the root of a binary tree, count the number of nodes `v` such that:
1. `|height(v.left) - height(v.right)| <= 1`, AND
2. `sum of all node values in the subtree rooted at v` is even.

Return the count.

```
Example:
         4
        / \
       3   5
      / \
     1   2

Subtree at node 1 (leaf): left_h=0, right_h=0, diff=0 ≤ 1 ✓ | sum=1 (odd) ✗
Subtree at node 2 (leaf): left_h=0, right_h=0, diff=0 ≤ 1 ✓ | sum=2 (even) ✓ → COUNT
Subtree at node 3: left_h=1, right_h=1, diff=0 ≤ 1 ✓ | sum=1+2+3=6 (even) ✓ → COUNT
Subtree at node 5 (leaf): diff=0 ≤ 1 ✓ | sum=5 (odd) ✗
Subtree at root 4: left_h=2, right_h=1, diff=1 ≤ 1 ✓ | sum=4+3+5+1+2=15 (odd) ✗

Count = 2
```

---

### 🧠 Discussion — How to Think About This

**The key challenge:** For every node, you need BOTH its subtree height AND its subtree sum — two different aggregated properties. Computing these separately would require two DFS passes.

**The insight:** Do it in ONE DFS pass by returning a pair `(height, sum)` from each recursive call.

**Postorder DFS** (process children BEFORE the current node) is natural here:
- Left child returns `(leftHeight, leftSum)`.
- Right child returns `(rightHeight, rightSum)`.
- Current node computes its own height = `1 + max(leftHeight, rightHeight)`.
- Current node computes its own sum = `node.val + leftSum + rightSum`.
- Then checks both conditions and increments counter if both pass.

---

### 🎨 Visual — Single DFS Pass Returning (height, sum)

```
Tree:
         4
        / \
       3   5
      / \
     1   2

DFS postorder — "visit children first, then self":

Leaf 1: return (height=1, sum=1)
  Check: diff=|0-0|=0 ≤ 1 ✓, sum=1 odd ✗ → no count

Leaf 2: return (height=1, sum=2)
  Check: diff=|0-0|=0 ≤ 1 ✓, sum=2 even ✓ → count=1

Node 3 (receives left=(1,1), right=(1,2)):
  height = 1 + max(1,1) = 2
  sum    = 3 + 1 + 2 = 6
  return (height=2, sum=6)
  Check: diff=|1-1|=0 ≤ 1 ✓, sum=6 even ✓ → count=2

Leaf 5: return (height=1, sum=5)
  Check: diff=|0-0|=0 ≤ 1 ✓, sum=5 odd ✗ → no count

Root 4 (receives left=(2,6), right=(1,5)):
  height = 1 + max(2,1) = 3
  sum    = 4 + 6 + 5 = 15
  return (height=3, sum=15)
  Check: diff=|2-1|=1 ≤ 1 ✓, sum=15 odd ✗ → no count

Final count = 2 ✅

KEY INVARIANT:
  Return (height, sum) from every DFS call.
  height = 1 + max(leftH, rightH)
  sum    = node.val + leftSum + rightSum
  The two-condition check happens AT each node using the CHILDREN's returned values.
  No second pass needed — one DFS, one traversal, O(n) time.
```

---

### 🚀 Optimal Java Solution — Single DFS Pass

```java
public class SubtreeCounter {

    private int count = 0;

    public int countQualifyingSubtrees(TreeNode root) {
        count = 0;
        dfs(root);
        return count;
    }

    // Returns int[] { height, sum } for the subtree rooted at node
    private int[] dfs(TreeNode node) {
        if (node == null) {
            // Null node: height=0, sum=0
            return new int[]{0, 0};
        }

        // Postorder: process left and right children first
        int[] left = dfs(node.left);
        int[] right = dfs(node.right);

        int leftHeight = left[0];
        int leftSum = left[1];
        int rightHeight = right[0];
        int rightSum = right[1];

        // Compute this node's height and subtree sum
        int currentHeight = 1 + Math.max(leftHeight, rightHeight);
        int currentSum = node.val + leftSum + rightSum;

        // Check both conditions
        boolean heightBalanced = Math.abs(leftHeight - rightHeight) <= 1;
        boolean sumEven = currentSum % 2 == 0;

        if (heightBalanced && sumEven) {
            count++;
        }

        return new int[]{currentHeight, currentSum};
    }
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(n) | Each node visited exactly once |
| **Space** | O(h) | Recursion stack = tree height h (O(log n) balanced, O(n) skewed) |

---

### 🔁 Follow-Up Questions + Variants

**Q1: What if the conditions were OR instead of AND?**
> Same DFS structure — just change `heightBalanced && sumEven` to `heightBalanced || sumEven`.

**Q2: What if you need to return the list of qualifying subtree roots, not just the count?**
> Add a `List<Integer> result` field. Replace `count++` with `result.add(node.val)` (or `result.add(node)` if you want the actual nodes).

**Q3: What if a THIRD condition is added — "number of nodes in subtree is a Fibonacci number"?**
> Return `int[] { height, sum, nodeCount }` — add `nodeCount = 1 + leftCount + rightCount` to the DFS return. Check Fibonacci membership with a precomputed set. Same single-pass structure. This is the key pattern: any N properties of a subtree can be computed in one DFS by returning an N-element array.

**Q4: What if the tree is very deep (10M nodes) and you're worried about stack overflow?**

**Why recursion breaks:** Java's default call stack is ~512KB. A degenerate tree (like a linked list) has depth equal to node count — 10M recursive calls = `StackOverflowError`. The fix: move the DFS onto the heap using an explicit `Deque`.

**The visited-flag trick for iterative postorder:** Postorder requires processing the node AFTER both its children. With an iterative stack, we simulate this with two visits: first visit pushes children; second visit processes the node itself.

**Steps in plain English:**

1. Push root with `visited=false`.
2. When popped unvisited: mark visited, push back; then push right child and left child (unvisited) so left is processed first.
3. When popped visited: children are done — compute this node's subtree count from already-computed children.

```java
// Iterative postorder DFS to compute subtree counts
public Map<Integer, Integer> subtreeCountIterative(TreeNode root) {
    Map<Integer, Integer> subtreeSize = new HashMap<>();
    if (root == null) {
        return subtreeSize;
    }
    // Use ArrayDeque — stack of (node, visited) pairs
    Deque<Object[]> stack = new ArrayDeque<>();
    stack.push(new Object[]{root, false});
    while (!stack.isEmpty()) {
        Object[] frame = stack.pop();
        TreeNode node = (TreeNode) frame[0];
        boolean visited = (boolean) frame[1];
        if (visited) {
            // Second visit: children already processed — compute this node's count
            int leftSize = (node.left != null) ? subtreeSize.get(node.left) : 0;
            int rightSize = (node.right != null) ? subtreeSize.get(node.right) : 0;
            subtreeSize.put(node, 1 + leftSize + rightSize);
        } else {
            // First visit: push self as visited, then children (unvisited)
            stack.push(new Object[]{node, true});
            // Push right first so left is processed first (stack is LIFO)
            if (node.right != null) {
                stack.push(new Object[]{node.right, false});
            }
            if (node.left != null) {
                stack.push(new Object[]{node.left, false});
            }
        }
    }
    return subtreeSize;
}
```

> **Interview note:** The answer interviewers want for the "10M nodes" probe: *"I'd convert to iterative with an explicit stack — it lives on the heap and can grow with available memory, unlike the JVM call stack which has a fixed ~512KB limit. The algorithm is identical, just the call stack is simulated manually."* You don't need to code the full version — this explanation + sketch usually satisfies the probe. Have the code ready if they push.

**Q5: What if "sum of node values is even" was changed to "subtree sum is divisible by K"?**
> Change `currentSum % 2 == 0` to `currentSum % k == 0`. Same DFS structure. No additional complexity.

---

---

## 5. Weighted Grouping with OOP Design — Custom

**Difficulty:** Medium | **Pattern:** Bucketing + OOP Class Design
**Confirmed in:** LC Discuss eBay BLR SSE post + Glassdoor 2025

> ⚠️ **Problem reconstructed from reports.** Stated as: *"Given groups corresponding to weight ranges (100–200 → Lightweight, 200–300 → Midweight, 400–500 → Heavy), find frequency in each group and return min and max."*
> ⚠️ **Range gap noted:** Source reports a gap at 300–400 (no category named). The code below treats ranges as data-driven (not hardcoded) so the gap is handled naturally — values in 300–400 fall into no bucket.
> **Interviewer expectation:** Solve as a system design problem with proper OOP classes, not as a raw function.

---

### 🎯 Problem Statement

You have a list of items with weight values. You have a set of named weight ranges (buckets). For each bucket, compute:
1. Frequency (how many items fall in this range).
2. Minimum weight in the bucket.
3. Maximum weight in the bucket.

Return a `BucketResult` per bucket.

```
Given buckets:
  "Lightweight" → [100, 200)   (inclusive 100, exclusive 200)
  "Midweight"   → [200, 300)
  "Heavy"       → [400, 500)   ← gap at [300, 400) — no category

Given items: [120, 150, 200, 250, 300, 420, 480, 500]

Expected results:
  Lightweight: freq=2, min=120, max=150  (120, 150)
  Midweight:   freq=2, min=200, max=250  (200, 250)
  Heavy:       freq=2, min=420, max=480  (420, 480)

Items 300 and 500: 300 is in [300,400) gap → uncategorized
                    500 is at boundary of [400,500) → if exclusive upper, uncategorized
```

---

### 🧠 Discussion — How to Think About This

**What the interviewer is really testing:**
1. Can you design proper classes with clear responsibilities?
2. Do you make ranges data-driven (not hardcoded `if weight < 200`)?
3. Do you handle the boundary and gap cases explicitly?

**Class responsibilities:**
- `WeightRange` — represents one bucket: name, low (inclusive), high (exclusive).
- `BucketResult` — output per bucket: name, frequency, min, max.
- `WeightClassifier` — takes a list of ranges and a list of items; produces results.

**Algorithm:** For each item, iterate ranges and check membership. If match found, update that bucket's stats.

**Why not hardcode if-else?** The interviewer will ask "what if a new category is added?" Hardcoded if-else requires source changes. A data-driven list of `WeightRange` objects means you just add a new range — zero code changes.

---

### 🎨 Visual — Bucket Assignment

```
Ranges:
  Lightweight: [100, 200)
  Midweight:   [200, 300)
  GAP:         [300, 400) — no bucket
  Heavy:       [400, 500)

Items:  120   150   200   250   300   420   480   500

         ↓     ↓     ↓     ↓     ↓     ↓     ↓     ↓
       Light Light  Mid   Mid  NONE  Heavy Heavy  NONE
         ✓    ✓      ✓    ✓           ✓     ✓

BucketResult:
  Lightweight: freq=2, min=120, max=150
  Midweight:   freq=2, min=200, max=250
  Heavy:       freq=2, min=420, max=480

KEY INVARIANT:
  Range boundaries are [low, high) — inclusive low, exclusive high.
  Items at exactly high fall into the NEXT bucket (or the gap if no next bucket).
  Never hardcode boundary conditions — make ranges a List<WeightRange>.
  The gap [300, 400) is handled by default — no range matches, item is uncategorized.
```

---

### 🚀 Java — OOP Class Design

**Steps in plain English:**
1. Define `WeightRange` with `name`, `low` (inclusive), `high` (exclusive).
2. Define `BucketResult` with `name`, `frequency`, `min`, `max`.
3. `WeightClassifier` takes `List<WeightRange>` in constructor — data-driven.
4. `classify(List<Integer> items)` iterates items; for each item, finds matching range; updates bucket stats.
5. Return `List<BucketResult>`.

```java
// Range definition — data-driven, not hardcoded
public class WeightRange {
    private final String name;
    private final int low;
    // Exclusive upper bound (120 in [100,200) but 200 goes to next bucket)
    private final int high;

    public WeightRange(String name, int low, int high) {
        this.name = name;
        this.low = low;
        this.high = high;
    }

    public boolean contains(int weight) {
        return weight >= low && weight < high;
    }

    public String getName() {
        return name;
    }
}

// Result container per bucket
public class BucketResult {
    public final String bucketName;
    public int frequency;
    public int min;
    public int max;

    public BucketResult(String bucketName) {
        this.bucketName = bucketName;
        this.frequency = 0;
        // Initialize min/max to sentinel values
        this.min = Integer.MAX_VALUE;
        this.max = Integer.MIN_VALUE;
    }

    public void record(int weight) {
        frequency++;
        min = Math.min(min, weight);
        max = Math.max(max, weight);
    }

    @Override
    public String toString() {
        if (frequency == 0) {
            return bucketName + ": empty";
        }
        return bucketName + ": freq=" + frequency + ", min=" + min + ", max=" + max;
    }
}

// Classifier — data-driven, range-agnostic
public class WeightClassifier {
    private final List<WeightRange> ranges;

    public WeightClassifier(List<WeightRange> ranges) {
        this.ranges = ranges;
    }

    public List<BucketResult> classify(List<Integer> items) {
        // Initialize one BucketResult per range
        Map<String, BucketResult> results = new LinkedHashMap<>();
        for (WeightRange range : ranges) {
            results.put(range.getName(), new BucketResult(range.getName()));
        }

        // Assign each item to its bucket
        for (int item : items) {
            for (WeightRange range : ranges) {
                if (range.contains(item)) {
                    results.get(range.getName()).record(item);
                    // Each item belongs to at most one range (non-overlapping)
                    break;
                }
            }
            // If no range matches: item falls in a gap — silently uncategorized
            // Production code would log: log.warn("No bucket for weight: " + item)
        }

        return new ArrayList<>(results.values());
    }
}

// Usage
public class Main {
    public static void main(String[] args) {
        List<WeightRange> ranges = Arrays.asList(
            new WeightRange("Lightweight", 100, 200),
            new WeightRange("Midweight",   200, 300),
            // Gap: [300, 400) — intentionally no bucket
            new WeightRange("Heavy",       400, 500)
        );

        List<Integer> items = Arrays.asList(120, 150, 200, 250, 300, 420, 480, 500);

        WeightClassifier classifier = new WeightClassifier(ranges);
        List<BucketResult> output = classifier.classify(items);

        for (BucketResult r : output) {
            System.out.println(r);
        }
        // Lightweight: freq=2, min=120, max=150
        // Midweight:   freq=2, min=200, max=250
        // Heavy:       freq=2, min=420, max=480
        // Items 300, 500 → uncategorized (gap + exclusive upper bound)
    }
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(n × r) | n items × r ranges; use interval tree for O(n log r) |
| **Space** | O(r) | One BucketResult per range |

> **Improvement for many ranges:** Sort ranges by `low`. For each item, binary search to find the candidate range. Check if item falls within. O(n log r) time. Trade-off: adds complexity; only matters if `r` is large (hundreds of ranges).

---

### 🔁 Follow-Up Questions + Variants

**Q1: What if ranges can overlap?**
> The `break` in the classify loop must be removed — an item can belong to multiple ranges. Return frequency/min/max per range independently. Ask the interviewer which behavior is expected.

**Q2: What if a new "Ultralight" category [50, 100) is needed?**
> Just add one line to the `ranges` list: `new WeightRange("Ultralight", 50, 100)`. No other code changes. This is the payoff of data-driven design.

**Q3: What if you need to stream items in real-time (not a batch list)?**
> `WeightClassifier` stores `Map<String, BucketResult>` as state. Expose an `update(int item)` method that does the same range lookup and `record()` call. The `classify()` snapshot is replaced by a streaming API.

**Q4: What is the gap [300, 400) — is it intentional?**
> Explicitly ask the interviewer. Options: (a) intentional — items in the gap are discarded; (b) typo — should be Midweight [200, 400); (c) catch-all bucket needed. State your assumption and code accordingly. In the reported eBay interview, the gap was in the stated problem — treating it as intentional (silently uncategorized) is safe.

**Q5: How would you unit test this?**
> Boundary tests: item at `low` (should be IN), item at `high-1` (should be IN), item at `high` (should be OUT). Gap test: item at 350 (should be uncategorized). Empty items list. No matching range for any item.

---

---

## 6. Number of Islands — LC 200

**Difficulty:** Medium | **Pattern:** DFS / BFS Flood Fill on 2D Grid
**Confirmed in:** eBay SDE-3 eBay Live team Round 1 (2025) + LC Discuss + CodingKaro

> 📖 **Full deep dive with all variants is in `curefit-sde3-problems.md` Problem #6** — read that first if this pattern is new.
> This entry is a condensed eBay-specific summary.

---

### eBay-Specific Context

At eBay, Number of Islands was confirmed in a 2025 SDE-3 round. The framing was straightforward LC 200. eBay interviewers are known to follow up with:

**eBay follow-ups observed:**
1. "What if connections are 8-directional (including diagonals)?" → Expand `dirs` from 4 to 8.
2. "What if the grid is a 2D representation of eBay's seller regions and you need to count regions that contain at least one premium seller?" → Track a property during DFS (premium flag on cells) and only count islands where DFS visits at least one premium cell.
3. "How would you scale this to a grid that doesn't fit in memory?" → Streaming partition approach: process row by row using a Union-Find (DSU) structure. Only keep two rows in memory at a time.

---

### Quick Solution

```java
public int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) {
        return 0;
    }
    int count = 0;
    for (int r = 0; r < grid.length; r++) {
        for (int c = 0; c < grid[0].length; c++) {
            if (grid[r][c] == '1') {
                count++;
                dfs(grid, r, c);
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c) {
    if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] != '1') {
        return;
    }
    grid[r][c] = '0';
    dfs(grid, r + 1, c);
    dfs(grid, r - 1, c);
    dfs(grid, r, c + 1);
    dfs(grid, r, c - 1);
}
```

**Complexity:** O(m × n) time, O(m × n) space (DFS stack depth worst case).

> See `curefit-sde3-problems.md#6` for full brute force → BFS variant → 8-directional → Max Area of Island chain.

---

---

## 7. Reverse Pairs — LC 493

**Difficulty:** Hard | **Pattern:** Merge Sort with Modified Merge Step
**Confirmed in:** LC Discuss eBay interview + 1Point3Acres eBay SWE3 Apr 2024

---

### 🎯 Problem Statement

Given an integer array `nums`, return the number of **reverse pairs** where:
- `i < j` AND `nums[i] > 2 * nums[j]`

```
Example 1:
nums = [1, 3, 2, 3, 1]
Output: 2
Pairs: (3, 1) at indices (1,4) since 3 > 2*1=2 ✓
       (3, 1) at indices (3,4) since 3 > 2*1=2 ✓

Example 2:
nums = [2, 4, 3, 5, 1]
Output: 3
Pairs: (2,1): 2>2*1 ✓, (4,1): 4>2 ✓, (5,1): 5>2 ✓
```

---

### 🧠 Discussion — How to Think About This

**Why brute force fails:** O(n²) — check every pair (i, j) where i < j. For n = 50,000, that's 2.5 billion operations.

**Why this is a merge sort problem:** In merge sort, when we merge two sorted halves `[left]` and `[right]`, all elements of `left` have indices BEFORE all elements of `right`. So for any pair `(left[i], right[j])`, the index order `i < j` is already guaranteed. We only need to check the value condition: `left[i] > 2 * right[j]`.

**Two-pointer in the merge step:** For each `left[i]`, use a pointer `k` starting from the beginning of `right`. Advance `k` while `left[i] > 2 * right[k]`. The number of valid `j`s for this `i` = `k` (since right is sorted, once `left[i] <= 2 * right[k]`, all subsequent `right[k']` with `k' >= k` also fail).

**Critical separation:** Count reverse pairs BEFORE sorting/merging. The merge step itself REARRANGES elements, so count first, then merge.

---

### 🎨 Visual — Counting in Merge Step

```
Array: [2, 4, 3, 5, 1]

Merge sort splits → left=[2,3,4], right=[1,5] (after recursive sorts)

Count reverse pairs between left and right:
  left pointer i, right pointer k (for counting only — not the merge pointer)

  i=0, left[0]=2: advance k while 2 > 2*right[k]
    right[0]=1: 2 > 2*1=2? NO (not strictly greater)
    k stays at 0. Count += 0
  i=1, left[1]=3: advance k while 3 > 2*right[k]
    right[0]=1: 3 > 2? YES → k=1
    right[1]=5: 3 > 10? NO → stop
    Count += k=1  (pair: (3,1))
  i=2, left[2]=4: advance k while 4 > 2*right[k]
    k is already 1 (we never reset it — left is sorted, k only moves forward)
    right[1]=5: 4 > 10? NO
    Count += k=1  (pair: (4,1))

Total from this merge = 2
(plus any from recursive calls on left and right halves)

KEY INVARIANT:
  Count BEFORE merging. Merge AFTER counting.
  Both left and right are sorted → two-pointer is valid for counting.
  k never resets between i increments (left is sorted ascending;
  if left[i] > 2*right[k], then left[i+1] >= left[i] > 2*right[k]).
```

---

### 🚀 Optimal Java Solution — Modified Merge Sort

```java
public int reversePairs(int[] nums) {
    if (nums == null || nums.length < 2) {
        return 0;
    }
    return mergeSort(nums, 0, nums.length - 1);
}

private int mergeSort(int[] nums, int left, int right) {
    if (left >= right) {
        return 0;
    }

    int mid = left + (right - left) / 2;

    // Recurse on both halves — count pairs WITHIN each half
    int count = mergeSort(nums, left, mid) + mergeSort(nums, mid + 1, right);

    // Count cross-half reverse pairs (left[i] > 2 * right[j])
    // Both halves are sorted at this point
    int k = mid + 1;
    for (int i = left; i <= mid; i++) {
        // Advance k while nums[i] > 2 * nums[k]
        // Use long to prevent integer overflow: 2 * nums[k] can overflow int
        while (k <= right && nums[i] > 2L * nums[k]) {
            k++;
        }
        // All elements nums[mid+1..k-1] satisfy the condition for this i
        count += (k - (mid + 1));
    }

    // Now merge the two sorted halves
    merge(nums, left, mid, right);

    return count;
}

private void merge(int[] nums, int left, int mid, int right) {
    int[] temp = new int[right - left + 1];
    int i = left;
    int j = mid + 1;
    int idx = 0;

    while (i <= mid && j <= right) {
        if (nums[i] <= nums[j]) {
            temp[idx++] = nums[i++];
        } else {
            temp[idx++] = nums[j++];
        }
    }

    while (i <= mid) {
        temp[idx++] = nums[i++];
    }
    while (j <= right) {
        temp[idx++] = nums[j++];
    }

    // Copy back into the original array
    System.arraycopy(temp, 0, nums, left, temp.length);
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute Force | O(n²) | O(1) |
| **Modified Merge Sort (optimal)** | **O(n log n)** | **O(n)** |

---

### 🔁 Follow-Up Questions + Variants

**Q1: Why use `2L * nums[k]` instead of `2 * nums[k]`?**
> Integer overflow. `nums[k]` can be up to `Integer.MAX_VALUE` = 2^31 - 1 ≈ 2.1 billion. Doubling it overflows 32-bit int. Cast to `long` by using `2L` (L suffix forces long arithmetic).

**Q2: Why count BEFORE merging, not during or after?**
> During merge, elements from left and right are interleaved into sorted order — you lose the information about which elements came from left vs. right. Count while both subarrays are still separate and sorted (before the merge step).

**Q3: How is this different from counting inversions (LC 315)?**
> Counting inversions: `nums[i] > nums[j]` for `i < j` (multiplier of 1).
> Reverse pairs: `nums[i] > 2 * nums[j]` (multiplier of 2).
> Same algorithm structure — only the condition in the counting step changes. For inversions you can also count DURING the merge (when you pick a right element over a left element, all remaining left elements form inversions with it). For reverse pairs with multiplier, the modified pre-merge counting approach is cleaner.

**Q4: What if the condition were `nums[i] > K * nums[j]`?**
> Change `2L * nums[k]` to `(long)K * nums[k]`. Same O(n log n) algorithm for any constant K.

---

---

---

---

## 8. Implement `ls -r` with Unit Tests — Custom

**Difficulty:** Medium | **Pattern:** DFS on N-ary Tree (directory as map)
**Confirmed in:** LC Discuss eBay SWE report + 1Point3Acres eBay MTS1 post

> ⚠️ **Custom problem.** Reported as: *"Given a directory structure represented as a Map, implement a recursive directory listing (like `ls -R`). Write unit tests for it."*
> **Input format assumed:** `Map<String, List<String>>` where keys are directory names, values are lists of contents (files or sub-directory names). Root directory name is given as a separate parameter.
> **Distinctive signal:** "With unit tests" — the interviewer explicitly wanted JUnit test cases. This is unusual for a DSA round — it tests structured thinking, not just algorithm.

---

### 🎯 Problem Statement

Given a `Map<String, List<String>>` representing a file system and a `root` directory name, implement `listRecursive(String root, Map<String, List<String>> fs)` that prints all entries, recursively, with indentation showing nesting depth.

```
Example Input:
root = "/"
fs = {
  "/":    ["/src", "/docs", "config.yml"],
  "/src": ["/src/utils", "Main.java"],
  "/src/utils": ["Helper.java"],
  "/docs": ["readme.md"]
}

Expected Output:
/
  /src/
    /src/utils/
      Helper.java
    Main.java
  /docs/
    readme.md
  config.yml
```

**eBay framing context:** eBay's catalog is hierarchical — categories contain sub-categories recursively. This problem tests whether you can traverse and represent any hierarchical structure, not just file systems.

---

### 🧠 Discussion — How to Think About This

**The structure is a tree, not a graph.** Each directory has exactly one parent (no cycles if well-formed). DFS is the natural traversal — enter a directory, recurse into all sub-directories, then list files.

**What distinguishes directories from files?** An entry is a directory if it is also a key in the map. If it is NOT a key, it is a leaf (file).

**Indentation:** Use a `depth` parameter — each recursive call increments depth. Print `"  ".repeat(depth)` before each entry.

**Unit tests: what to cover?**
1. Empty root (no entries).
2. Single level — root has only files.
3. Multi-level nesting.
4. Deeply nested single chain.
5. Mixed — directories and files at the same level.

---

### 🎨 Visual — DFS State During Traversal

```
fs = { "/": ["/src", "config.yml"], "/src": ["Main.java"] }
root = "/"

DFS call stack:
list("/", depth=0)
  output: "/"
  entry "/src" → IS a key → it's a directory → recurse
    list("/src", depth=1)
      output: "  /src/"
      entry "Main.java" → NOT a key → it's a file
        output: "    Main.java"
    return
  entry "config.yml" → NOT a key → it's a file
    output: "  config.yml"
  return

Full output:
/
  /src/
    Main.java
  config.yml

KEY INVARIANT:
  "Is this entry also a key in the map?" determines directory vs file.
  Depth drives indentation — no global state needed.
  DFS order: recurse into each directory before listing files at the same level.
  (Or list files first — ask interviewer preference. The ordering is a design choice.)
```

---

### 🚀 Java — Implementation + JUnit Tests

**Steps in plain English:**
1. Print root directory name with depth indentation + trailing `/` to signal it's a directory.
2. Get the list of entries under root from the map.
3. For each entry: if it is a key in the map → it is a sub-directory → recurse with `depth+1`; otherwise → it is a file → print it with indentation.

```java
import java.util.*;

public class FileSystemLister {

    // Entry point
    public static void listRecursive(String root, Map<String, List<String>> fs) {
        listHelper(root, fs, 0);
    }

    private static void listHelper(String dir, Map<String, List<String>> fs, int depth) {
        // Step 1: print current directory name with indentation
        String indent = "  ".repeat(depth);
        System.out.println(indent + dir + "/");

        // Step 2: get contents of this directory
        List<String> entries = fs.getOrDefault(dir, Collections.emptyList());

        // Step 3: partition into sub-directories and files; recurse into directories
        List<String> files = new ArrayList<>();
        for (String entry : entries) {
            if (fs.containsKey(entry)) {
                // Entry is a sub-directory — recurse first (DFS)
                listHelper(entry, fs, depth + 1);
            } else {
                // Entry is a file — print after subdirectories
                files.add(entry);
            }
        }

        // Print files at this level after all subdirectory branches
        String fileIndent = "  ".repeat(depth + 1);
        for (String file : files) {
            System.out.println(fileIndent + file);
        }
    }
}
```

---

### JUnit Tests — What the Interviewer Wanted

```java
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class FileSystemListerTest {

    // Helper: capture stdout as a string for assertion
    private String capture(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        action.run();
        System.setOut(original);
        return baos.toString().trim();
    }

    @Test
    public void testEmptyRoot() {
        // Root exists but has no contents
        Map<String, List<String>> fs = new HashMap<>();
        fs.put("/", Collections.emptyList());

        String output = capture(() -> FileSystemLister.listRecursive("/", fs));
        assertEquals("/", output);
    }

    @Test
    public void testSingleLevelFilesOnly() {
        // Root contains only files — no subdirectories
        Map<String, List<String>> fs = new HashMap<>();
        fs.put("/", Arrays.asList("a.txt", "b.txt"));

        String output = capture(() -> FileSystemLister.listRecursive("/", fs));
        String expected = "/\n  a.txt\n  b.txt";
        assertEquals(expected, output);
    }

    @Test
    public void testMultiLevelNesting() {
        // Standard two-level directory tree
        Map<String, List<String>> fs = new LinkedHashMap<>();
        fs.put("/", Arrays.asList("/src", "config.yml"));
        fs.put("/src", Arrays.asList("Main.java"));

        String output = capture(() -> FileSystemLister.listRecursive("/", fs));
        String expected = "/\n  /src/\n    Main.java\n  config.yml";
        assertEquals(expected, output);
    }

    @Test
    public void testDeeplyNestedChain() {
        // Single chain: a → b → c (each containing only one subdirectory)
        Map<String, List<String>> fs = new LinkedHashMap<>();
        fs.put("a", Arrays.asList("b"));
        fs.put("b", Arrays.asList("c"));
        fs.put("c", Arrays.asList("leaf.txt"));

        String output = capture(() -> FileSystemLister.listRecursive("a", fs));
        String expected = "a/\n  b/\n    c/\n      leaf.txt";
        assertEquals(expected, output);
    }

    @Test
    public void testMixedFilesAndDirs() {
        // Directories listed before files (per our implementation)
        Map<String, List<String>> fs = new LinkedHashMap<>();
        fs.put("/", Arrays.asList("readme.md", "/src", "build.gradle", "/docs"));
        fs.put("/src", Arrays.asList("App.java"));
        fs.put("/docs", Arrays.asList("guide.pdf"));

        String output = capture(() -> FileSystemLister.listRecursive("/", fs));
        // Sub-directories are listed first (DFS), then files at root level
        assertTrue(output.contains("/src/"));
        assertTrue(output.contains("/docs/"));
        assertTrue(output.contains("readme.md"));
        assertTrue(output.contains("build.gradle"));
        // Sub-dir content should appear before root-level files
        assertTrue(output.indexOf("App.java") < output.indexOf("readme.md"));
    }
}
```

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(n) | n = total entries across all directories; each entry visited once |
| **Space** | O(d) | d = max depth; recursion call stack |

---

### 🔁 Follow-Up Questions + Variants

**Q1: What if the map contains cycles? (Malformed input)**
> Add a `Set<String> visited`. Before recursing into a directory, check if it has already been visited. If yes, print a warning (`"[cycle detected]"`) and skip. This turns it into a graph traversal problem.

**Q2: What if you want to list files BEFORE sub-directories (reversed order)?**
> Swap the two loops in `listHelper` — print files first, then recurse into directories. Or: make the ordering a constructor parameter `enum Order { DIRS_FIRST, FILES_FIRST }`.

**Q3: What if entries are not prefixed with the parent path? (Just names like "src", "utils")**
> Pass a `currentPath` string into the recursive call. Build the full path as `currentPath + "/" + entry`. Check membership in the map using the full path.

**Q4: What if you want to return a List<String> instead of printing?**
> Replace `System.out.println` with `result.add(...)` where `result` is a `List<String>` passed through the call stack (or returned from each call and merged).

**Q5: eBay extension — "filter and show only files matching a given extension."**
> Add a `String ext` parameter. In the file-printing section: `if (file.endsWith(ext)) { print it; } else { skip; }`. Directories are still traversed even if they don't match — the filter applies only to the leaf files.

---

---

## 9. Sieve of Eratosthenes — Count Primes — LC 204

**Difficulty:** Easy-Medium | **Pattern:** Sieve — Mark Composites Iteratively
**Confirmed in:** eBay Director Round (multiple Glassdoor + Blind 2025 reports)

> **eBay context:** Asked in the Director round as a warm-up problem before "explain your project." The Director expected clean code and a clear explanation of WHY the inner loop starts at `i*i` — that's the probe.

---

### 🎯 Problem Statement

Given an integer `n`, return the count of prime numbers strictly less than `n`.

```
n = 10 → 4   (primes: 2, 3, 5, 7)
n = 0  → 0
n = 1  → 0
n = 2  → 0
```

---

### 🧠 Discussion — How to Think About This

**Naive approach:** For each number up to n, check if it's prime by trial division up to its square root. O(n √n) — too slow for large n.

**Sieve of Eratosthenes — the key idea:** Instead of checking each number independently, *mark off* all multiples of each prime. A number is prime if and only if it was never marked off.

**Why start the inner loop at `i*i`?**
For a prime `i`, all multiples of `i` smaller than `i*i` have already been marked by a smaller prime. For example, when `i=5`: `5×2=10` was already marked by prime 2, `5×3=15` by prime 3, `5×4=20` by prime 2. The first unmarked multiple of 5 is `5×5=25`. So starting at `i*i` avoids redundant work and is O(n log log n) overall.

**Why iterate `i` only up to `√n`?**
Any composite number `c < n` has at least one prime factor ≤ `√n`. So all composites up to n will be marked by primes up to `√n`. No need to check beyond.

---

### 🎨 Visual — Sieve in Action

```
n = 20 — find all primes < 20

Initial: mark all as prime
Indices: 0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19
isPrime: F  F  T  T  T  T  T  T  T  T   T  T  T  T  T  T  T  T  T  T

i=2 (prime): mark multiples starting at 2×2=4: 4,6,8,10,12,14,16,18
Indices: 0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19
isPrime: F  F  T  T  F  T  F  T  F  T   F  T  F  T  F  T  F  T  F  T

i=3 (prime): mark multiples starting at 3×3=9: 9,12,15,18
Indices: 0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19
isPrime: F  F  T  T  F  T  F  T  F  F   F  T  F  T  F  F  F  T  F  T

i=4: isPrime[4]=F → skip (not prime)
i=5 (prime): 5×5=25 > 20 → inner loop doesn't execute

Primes < 20: 2, 3, 5, 7, 11, 13, 17, 19 → count = 8

KEY INVARIANT:
  When outer loop reaches i, ALL multiples of every prime < i
  have already been marked. So isPrime[i]==true means i is prime.
  Inner loop: j = i*i, i*i+i, i*i+2i, ... (skip composites already marked)
```

---

### 🚀 Optimal Java Solution

```java
public int countPrimes(int n) {
    if (n < 2) {
        return 0;
    }

    // Step 1: assume all numbers are prime
    boolean[] isPrime = new boolean[n];
    Arrays.fill(isPrime, true);
    isPrime[0] = false;
    isPrime[1] = false;

    // Step 2: for each prime i up to √n, mark its multiples as composite
    for (int i = 2; (long) i * i < n; i++) {
        if (isPrime[i]) {
            // Step 3: start marking at i*i — everything below already marked
            for (int j = i * i; j < n; j += i) {
                isPrime[j] = false;
            }
        }
    }

    // Step 4: count all remaining primes
    int count = 0;
    for (boolean prime : isPrime) {
        if (prime) {
            count++;
        }
    }
    return count;
}
```

> **Overflow guard:** `(long) i * i < n` — if `i` is large (e.g. 46341 when n ≈ 2^31), `i*i` overflows int. Cast to long before multiplication.

---

### ⏱️ Complexity

| | Value | Reason |
|---|---|---|
| **Time** | O(n log log n) | Each composite is marked O(log log n) times on average — the harmonic series of prime reciprocals |
| **Space** | O(n) | Boolean array of size n |

---

### 🔁 Follow-Up Questions + Variants

**Q1 (Director probe): "Why does the inner loop start at `i*i` and not `2*i`?"**
> All composites `i*k` where `k < i` have already been marked by the prime that divides `k`. The first unmarked multiple of `i` is always `i*i`. Starting at `2*i` does the same work but redundantly. The Director is checking whether you understand the invariant, not just that you memorized the algorithm.

**Q2: "Return the list of primes, not just the count."**
> After the sieve, iterate `isPrime`, collect indices where `isPrime[i] == true` into a `List<Integer>`.

**Q3: "What if n = 10^9? The boolean array is 1 GB."**

**Why the basic sieve fails at large n:** `boolean[10^9]` = 1 byte × 10^9 = ~1 GB — too large for typical memory constraints. The **segmented sieve** keeps only a block of size `√n` in memory at a time.

**How to think about it:** The basic sieve marks composites using small primes ≤ √n. The segmented sieve does the same thing — but instead of a single 10^9-entry array, it processes the range `[0, n]` in chunks of ~`√n` elements. Each chunk only needs a `boolean[√n]` array.

**Steps in plain English:**

1. Run the basic sieve on `[0, √n]` to get all small primes (the "seed" primes).
2. For each segment `[low, high]` where `high - low ≈ √n`:
   a. Create a `boolean[segmentSize]` array, all `false` (= prime).
   b. For each small prime `p`: find the first multiple of `p` ≥ `low`, then mark all multiples of `p` within this segment.
   c. Collect unmarked indices as primes in this segment.

```java
public List<Integer> segmentedSieve(int n) {
    int limit = (int) Math.sqrt(n) + 1;
    // Step 1: sieve on [0, √n] to find seed primes
    boolean[] smallSieve = new boolean[limit + 1];
    Arrays.fill(smallSieve, true);
    smallSieve[0] = smallSieve[1] = false;
    for (int i = 2; i * i <= limit; i++) {
        if (smallSieve[i]) {
            for (int j = i * i; j <= limit; j += i) {
                smallSieve[j] = false;
            }
        }
    }
    List<Integer> smallPrimes = new ArrayList<>();
    for (int i = 2; i <= limit; i++) {
        if (smallSieve[i]) {
            smallPrimes.add(i);
        }
    }
    List<Integer> result = new ArrayList<>(smallPrimes);
    // Step 2: process segments of size ~√n
    int segmentSize = limit;
    for (int low = limit + 1; low <= n; low += segmentSize) {
        int high = Math.min(low + segmentSize - 1, n);
        boolean[] segment = new boolean[high - low + 1];
        Arrays.fill(segment, true); // true = prime candidate
        // Step 2b: mark composites in this segment using seed primes
        for (int p : smallPrimes) {
            // First multiple of p that is >= low
            int start = (int) (Math.ceil((double) low / p) * p);
            if (start == p) {
                // p itself is prime — skip it (only mark composites, not p itself)
                start += p;
            }
            for (int j = start; j <= high; j += p) {
                segment[j - low] = false;
            }
        }
        // Step 2c: collect primes from this segment
        for (int i = 0; i < segment.length; i++) {
            if (segment[i]) {
                result.add(low + i);
            }
        }
    }
    return result;
}
```

> **Memory:** O(√n) for the small prime list + O(√n) for the current segment = O(√n) total instead of O(n). For n=10^9: √n ≈ 31,623 booleans ≈ 32 KB — completely practical. The Director probe "why does this help?" answer: we never need the full array at once — just the small primes and one window.

**Q4: "Is there a formula-based approach (no array)?"**
> Meissel-Lehmer algorithm — counts primes up to N in O(N^(2/3)) time and O(N^(1/3)) space without enumerating all primes. Not interview-relevant, but worth mentioning to show awareness.

**Q5: "Prime check for a single very large number (e.g. 10^15)?"**
> Trial division up to √N — about 31 million divisions for 10^15. Or Miller-Rabin probabilistic primality test — O(k log² N) for k rounds, effectively deterministic for k=20 on 64-bit integers.

---

---

## 10. Seen-Once Quick-Reference Index

> 🧩 **Tier 3 — eBay company tag on LC Discuss, no specific MTS1 onsite report.**
> These appear in eBay's LC company filter but are NOT confirmed in recent onsite reports.
> Full solutions are in Sections 11–22 below. Use this table for 30-second pattern recall under pressure.

| § | LC | Problem | Pattern | One-Line Invariant |
|---|---|---|---|---|
| [11](#11-move-zeroes--lc-283) | 283 | **Move Zeroes** | Two Pointers in-place | `slow` = next write slot; zero-fill tail after one pass |
| [12](#12-best-time-to-buy--sell-stock-ii--lc-122) | 122 | **Stock II** | Greedy | Sum every positive day-over-day delta |
| [13](#13-merge-intervals--lc-56) | 56 | **Merge Intervals** | Sort + Linear Scan | Sort by start; merge when `curr.start ≤ prev.end` |
| [14](#14-3sum--lc-15) | 15 | **3Sum** | Sort + Two Pointers | Fix `i`; two-pointer `[i+1, n-1]`; skip dupes on both sides |
| [15](#15-lru-cache--lc-146) | 146 | **LRU Cache** | HashMap + DLL | Every get/put moves node to tail; evict head.next on overflow |
| [16](#16-merge-k-sorted-lists--lc-23) | 23 | **Merge K Lists** | Min-Heap | Poll min, push its `.next`; heap size never exceeds k |
| [17](#17-top-k-frequent-elements--lc-347) | 347 | **Top K Frequent** | Bucket Sort | `buckets[freq]`; iterate backwards to collect top K |
| [18](#18-course-schedule--lc-207) | 207 | **Course Schedule** | DFS 3-color | GRAY = in-stack; cycle iff you revisit GRAY node |
| [19](#19-word-ladder--lc-127) | 127 | **Word Ladder** | BFS + wildcard map | `h*t → {hit,hot}`; BFS levels = transformation steps |
| [20](#20-all-nodes-distance-k--lc-863) | 863 | **Distance K** | BFS + parent map | DFS to record parents; BFS in 3 directions from target |
| [21](#21-n-queens--lc-51) | 51 | **N-Queens** | Backtracking | cols/diag1(r-c)/diag2(r+c) sets; restore on backtrack |
| [22](#22-sudoku-solver--lc-37) | 37 | **Sudoku Solver** | Backtracking | Try 1–9 per empty cell; row/col/box sets; restore on fail |

---

---

## 11. Move Zeroes — LC 283

**Difficulty:** Easy | **Pattern:** Two Pointers (in-place write) | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report — treat as warm-up / pattern drill.

---

### 🎯 Problem Statement

Given an integer array `nums`, move all `0`s to the end **in-place** while maintaining the relative order of non-zero elements. Minimize the number of operations.

```
Input:  [0, 1, 0, 3, 12]
Output: [1, 3, 12, 0, 0]
```

---

### 🧠 Discussion — How to Think About This

Two classic mistakes:
1. **Creating a new array** — violates in-place constraint.
2. **Swapping every zero with the next non-zero** — works but does more swaps than necessary (and relative order can break if you swap wrong pairs).

The clean mental model: treat `slow` as a **write cursor** for non-zero values. `fast` scans the whole array. Every non-zero value gets copied to `nums[slow]` then `slow` advances. After the scan, every position from `slow` to end is definitionally a zero — fill them.

One subtle point: we copy, not swap. This avoids the "what if slow < fast and we lose a non-zero?" bug that swap-based approaches can hit when the array starts with a zero block.

---

### 🐌 Brute Force

Use a temporary list:

```java
public void moveZeroes(int[] nums) {
    List<Integer> nonZero = new ArrayList<>();
    for (int num : nums) {
        if (num != 0) {
            nonZero.add(num);
        }
    }
    int i = 0;
    for (int val : nonZero) {
        nums[i++] = val;
    }
    while (i < nums.length) {
        nums[i++] = 0;
    }
}
```

O(n) time, O(n) space (the list). Correct but uses extra space.

---

### 💡 Idea Behind Optimisation

Eliminate the extra list. `slow` IS the write cursor — it points to the next position where a non-zero should land. We overwrite directly into the original array. After one pass, `nums[0..slow-1]` holds all non-zeros in order; `nums[slow..n-1]` is garbage. One more sweep sets the tail to 0.

Result: O(n) time, O(1) space.

---

### 🎨 Visual — Two-Pointer Write Pass

```
nums = [0, 1, 0, 3, 12]
        ↑              slow=0, fast=0
        fast=0: nums[0]=0 → skip

        ↑  ↑           slow=0, fast=1
        fast=1: nums[1]=1 (non-zero) → nums[slow]=1 → slow++

[1, 1, 0, 3, 12]
           ↑  ↑         slow=1, fast=2
        fast=2: nums[2]=0 → skip

[1, 1, 0, 3, 12]
           ↑     ↑      slow=1, fast=3
        fast=3: nums[3]=3 (non-zero) → nums[slow]=3 → slow++

[1, 3, 0, 3, 12]
              ↑  ↑      slow=2, fast=4
        fast=4: nums[4]=12 → nums[slow]=12 → slow++

[1, 3, 12, 3, 12]       slow=3 — scan done

Zero-fill from slow=3 to end:
[1, 3, 12, 0, 0]  ✓

KEY INVARIANT:
   nums[0..slow-1] = all non-zeros seen so far, in original order
   nums[slow..fast-1] = territory we've scanned; safe to overwrite
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **`slow` starts at 0** — it is the next position to write a non-zero value.
2. **`fast` scans every element** — if non-zero, copy to `nums[slow]` then advance `slow`.
3. **Zero-fill the tail** — everything from `slow` to `n-1` must be zero.

```java
public void moveZeroes(int[] nums) {
    // Step 1: slow = next write position for non-zero values
    int slow = 0;

    // Step 2: copy non-zeros forward
    for (int fast = 0; fast < nums.length; fast++) {
        if (nums[fast] != 0) {
            nums[slow] = nums[fast];
            slow++;
        }
    }

    // Step 3: zero-fill the tail
    while (slow < nums.length) {
        nums[slow] = 0;
        slow++;
    }
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (temp list) | O(n) | O(n) |
| **Two-pointer in-place** | **O(n)** | **O(1)** |

---

### 🔁 Follow-Up Questions + Variants

**Q1: "Can you do it in one pass without the zero-fill sweep?"**
> Yes — swap instead of copy: when `nums[fast] != 0`, swap `nums[fast]` with `nums[slow]`, then advance both. This works because swapping puts a zero at `fast` (which will be overwritten later) rather than leaving garbage. However, it does one extra write per non-zero vs. the copy approach, so it is NOT strictly fewer writes when the array has many non-zeros.

**Q2: "What if we need to move zeroes to the front instead?"**
> Mirror the algorithm right-to-left. `slow` starts at `n-1` (next write position for non-zeros, filling from the back). `fast` scans from `n-1` to `0`. When `fast` hits a non-zero, write it to `nums[slow--]`. After the scan, fill `nums[0..slow]` with zeros. This preserves the relative order of non-zeros at the tail. Example: `[0,1,0,3,12]` → non-zeros written right-to-left at positions 4,3,2 → `[?,?,1,3,12]` → fill `[0,1]` with zeros → `[0,0,1,3,12]`.

**Q3: "Move all negative numbers to the front, preserving relative order."**
> Same two-pointer pattern. Change the condition: `if (nums[fast] < 0)` triggers the copy. Fill the tail with positives afterward if needed — or track a separate pointer for that.

**Q4: "What if the array is a stream and you can't load it all in memory?"**
> You need buffering. Maintain a queue of pending non-zeros; emit zeros first when the stream ends. O(n) buffer in worst case (all non-zeros arrive before the stream closes).

**Q5: "Why is relative order of non-zeros important in the constraint?"**
> Without the ordering constraint, you could partition in-place with a variant of quicksort's partition step — one swap per zero, O(n) but potentially reorders non-zeros. The ordering constraint rules that out and forces a stable copy approach.

---

---

## 12. Best Time to Buy & Sell Stock II — LC 122

**Difficulty:** Medium | **Pattern:** Greedy | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report — treat as pattern drill.

---

### 🎯 Problem Statement

You are given an integer array `prices` where `prices[i]` is the price of a stock on day `i`. You may buy and sell the stock **multiple times** but can only hold at most **one share** at a time (must sell before buying again). Return the **maximum profit**.

```
Input:  prices = [7, 1, 5, 3, 6, 4]
Output: 7
Explanation: buy day 1 (price=1), sell day 2 (price=5) → profit 4
             buy day 3 (price=3), sell day 4 (price=6) → profit 3
             Total = 7
```

---

### 🧠 Discussion — How to Think About This

The key insight is that the total profit of any sequence of buy-sell transactions equals the sum of positive day-over-day differences in that window. Buying at valley and selling at peak is equivalent to summing every upward day individually.

**Why?** For a buy at day `a` and sell at day `b`:
```
prices[b] - prices[a]
= (prices[b] - prices[b-1]) + (prices[b-1] - prices[b-2]) + ... + (prices[a+1] - prices[a])
```
It telescopes to the sum of all daily deltas in the range. If we only take days with positive delta, we capture the maximum.

This means: **no DP needed**. Greedy gives the exact optimal.

---

### 🐌 Brute Force

Try every pair (buy day, sell day) and recursively solve the subproblem from sell day onward. O(2^n) — exponential, meaningless for interviews.

---

### 💡 Idea Behind Optimisation

Sum every `prices[i] - prices[i-1]` that is positive. That's it.

---

### 🎨 Visual — Greedy Profit Capture

```
prices: [7,  1,  5,  3,  6,  4]
         day: 0   1   2   3   4   5

Daily deltas (prices[i] - prices[i-1]):
         -   -6  +4  -2  +3  -2
                  ↑       ↑
                  take    take   (only positives)

Profit = 4 + 3 = 7 ✓

Visual as a curve:
  7 ●
    \
     ● 1              ← buy here
      \  5 ●
          \  3 ●      ← buy again
              \  6 ●  ← sell
                   \
                    4 ●

Each upward segment we "own" — downward segments we skip.

KEY INVARIANT:
   For any continuous holding window, the profit telescopes to a sum of daily
   deltas. Taking only positive deltas is always at least as good as any
   multi-day window — and never worse.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Iterate from day 1** — compute delta from the previous day.
2. **If delta is positive, add it to profit** — equivalent to buying yesterday and selling today.
3. **Return accumulated profit.**

```java
public int maxProfit(int[] prices) {
    int profit = 0;

    // Step 1 & 2: accumulate every positive delta
    for (int i = 1; i < prices.length; i++) {
        int delta = prices[i] - prices[i - 1];
        if (delta > 0) {
            profit += delta;
        }
    }

    // Step 3: return total
    return profit;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (recursive) | O(2^n) | O(n) stack |
| **Greedy (sum deltas)** | **O(n)** | **O(1)** |

---

### 🔁 Follow-Up Questions + Variants

**Q1: "What if you can hold at most 2 transactions total?" (LC 123)**

**Why the approach changes:** Unlimited transactions = greedy works because every upward move is independent. But with a cap of 2, taking transaction 1 now might block a better transaction 1 later. You need DP to track which transaction you're on.

**Four states — think of your portfolio lifecycle:**

```
hold1  = max profit while holding stock from 1st buy       (haven't sold yet)
cash1  = max profit after selling from 1st transaction     (can start 2nd)
hold2  = max profit while holding stock from 2nd buy       (haven't sold yet)
cash2  = max profit after selling from 2nd transaction     (done)
```

**Steps in plain English:**

1. Initialize `hold1` and `hold2` to `Integer.MIN_VALUE` (haven't bought yet — negative infinity prevents false "profit").
2. For each price: update all 4 states in order — each builds on the previous state.
3. Return `cash2` — the max profit after at most 2 complete transactions.

```java
public int maxProfit(int[] prices) {
    int hold1 = Integer.MIN_VALUE;
    int cash1 = 0;
    int hold2 = Integer.MIN_VALUE;
    int cash2 = 0;
    for (int price : prices) {
        // Order matters: each line uses the PREVIOUS iteration's values (simulate simultaneous update)
        hold1 = Math.max(hold1, -price);            // buy at this price for 1st time
        cash1 = Math.max(cash1, hold1 + price);     // sell 1st holding
        hold2 = Math.max(hold2, cash1 - price);     // buy again after 1st sell
        cash2 = Math.max(cash2, hold2 + price);     // sell 2nd holding
    }
    return cash2;
}
```

> **Why this works for LC 122 too:** if `fee=0` and unlimited transactions, `hold1` and `cash1` alone = greedy. The 2-transaction structure just adds 2 more states.

---

**Q2: "What if each transaction has a fixed fee?" (LC 714)**

**Why greedy breaks:** A single-day gain smaller than the fee is still profitable if you can ride a multi-day rise without paying the fee each day. Example: `prices=[1,3,2,8], fee=2`. Greedy might "sell at 3 (profit 0 after fee)" and "rebuy at 2, sell at 8 (profit 4)" = 4 total. But optimal is "buy at 1, hold through the dip, sell at 8" = 5 total. Greedy's threshold check misses this.

**Two states:**

```
cash  = best profit when NOT holding (available to buy)
held  = best profit when HOLDING (paid the buy price, haven't sold yet)
```

**Steps in plain English:**

1. `cash = max(cash, held + price - fee)` — sell today, pay the fee once.
2. `held = max(held, cash - price)` — buy today using whatever cash we have.
3. Note: compute `cash` BEFORE updating `held` in the same loop iteration. This correctly handles the case where selling and re-buying on the same day is equivalent to doing nothing.

```java
public int maxProfit(int[] prices, int fee) {
    int cash = 0;
    // Bought on day 0: we've "spent" prices[0] already
    int held = -prices[0];
    for (int i = 1; i < prices.length; i++) {
        // Sell today: gain price, pay one fee
        cash = Math.max(cash, held + prices[i] - fee);
        // Buy today: spend price from current cash
        held = Math.max(held, cash - prices[i]);
    }
    return cash;
}
```

> **Key insight:** The fee makes holding through dips worthwhile because you only pay the fee once per round-trip (buy + sell), not per day held. The DP naturally accounts for this — holding increases `held` without touching `cash`.

---

**Q3: "What if there's a cooldown day after each sell?" (LC 309)**

**Why 2 states aren't enough:** After selling, you can't buy the next day. "Not holding" needs to split into: *just sold* (must wait) vs *ready to buy* (cooldown over).

**Three states — a state machine:**

```
held   = holding stock          → tomorrow: can sell
sold   = just sold today        → tomorrow: MUST rest (cooldown)
rest   = not holding, can buy   → tomorrow: can buy OR stay rested
```

```
  [rest] ──buy──▶ [held] ──sell──▶ [sold]
    ▲                                   │
    └──────── next day: cooldown ends ──┘
```

**Steps in plain English:**

1. Save all three previous values before updating (avoid using this iteration's value as input for the same iteration).
2. `held = max(prevHeld, prevRest - price)` — buy only from rest state (not from sold/cooldown).
3. `sold = prevHeld + price` — sell whatever you held.
4. `rest = max(prevRest, prevSold)` — cooldown expires → join rest pool.

```java
public int maxProfit(int[] prices) {
    int held = -prices[0];
    int sold = 0;
    int rest = 0;
    for (int i = 1; i < prices.length; i++) {
        int prevHeld = held;
        int prevSold = sold;
        int prevRest = rest;
        // Buy only from rested state — cannot buy day after selling (cooldown)
        held = Math.max(prevHeld, prevRest - prices[i]);
        // Sell whatever we held
        sold = prevHeld + prices[i];
        // Cooldown expires OR stay rested
        rest = Math.max(prevRest, prevSold);
    }
    // Can't end in "held" — never closed our last position
    return Math.max(sold, rest);
}
```

---

**Q4: "What if you must buy and sell exactly once?" (LC 121)**

**Insight:** For each day, if you sell today, the best profit = `price[today] - min(price[0..today-1])`. Track the running minimum as you scan left to right.

```java
public int maxProfit(int[] prices) {
    int minPrice = Integer.MAX_VALUE;
    int maxProfit = 0;
    for (int price : prices) {
        minPrice = Math.min(minPrice, price);
        maxProfit = Math.max(maxProfit, price - minPrice);
    }
    return maxProfit;
}
```

---

**Q5: "Prove the greedy is optimal — why isn't there a case where skipping an upward delta yields more overall?"**

> Because transactions are independent — you can buy on day `i` and sell on `i+1`, then immediately buy on `i+1` again. No upward delta is ever "used up" by taking an adjacent one. The sum of all positive deltas is an upper bound on any strategy, and the greedy achieves it.

---

**🧭 The Mental Map — All 4 Variants**

| Variant | Why greedy changes | Extra constraint | States |
|---|---|---|---|
| **LC 122** base | Nothing | Unlimited transactions | 0 (pure greedy) |
| **LC 121** buy/sell once | None — simpler scan | Must sell before buying | 1: `minPrice` |
| **LC 714** with fee | Fee creates a threshold — holding may beat frequent selling | Fee per transaction | 2: `cash`, `held` |
| **LC 309** with cooldown | Can't buy immediately after sell | 1-day cooldown | 3: `held`, `sold`, `rest` |
| **LC 123** at most 2 | Cap forces saving transactions for best windows | ≤2 total | 4: `hold1`, `cash1`, `hold2`, `cash2` |

---

---

## 13. Merge Intervals — LC 56

**Difficulty:** Medium | **Pattern:** Sort + Linear Scan | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report — treat as medium-frequency drill.

---

### 🎯 Problem Statement

Given an array of intervals `[start, end]`, merge all overlapping intervals and return the non-overlapping result.

```
Input:  [[1,3],[2,6],[8,10],[15,18]]
Output: [[1,6],[8,10],[15,18]]
Explanation: [1,3] and [2,6] overlap → merged to [1,6]
```

---

### 🧠 Discussion — How to Think About This

Without sorting, overlapping intervals may be scattered anywhere in the array — you'd need to compare every pair: O(n²). After sorting by **start time**, overlapping intervals become **adjacent**. This reduces the problem to one linear pass.

The merge condition: if `curr.start ≤ prev.end`, the intervals overlap (or touch). Then `prev.end = max(prev.end, curr.end)` — we take the larger end because `curr` might be entirely contained within `prev`.

The easy bug: using `prev.end = curr.end` instead of `max`. That breaks when `prev` swallows `curr` (e.g., `[1,10]` merging with `[2,5]`).

---

### 🐌 Brute Force

For each interval, scan every other interval for overlap and merge. Repeat until stable. O(n²) per pass, multiple passes needed. Impractical.

---

### 💡 Idea Behind Optimisation

Sort by start. Keep a "current merged interval" (`last` in the result list). For each next interval: overlap → extend `last.end`; no overlap → push `last` to result and start fresh.

---

### 🎨 Visual — Sort and Merge Pass

```
Input sorted by start:
 [1,3]   [2,6]   [8,10]   [15,18]
   ├──┤
   ├─────┤         ← overlap: 2 ≤ 3 → merge → [1, max(3,6)] = [1,6]
          ├────┤   ← no overlap: 8 > 6 → push [1,6], start [8,10]
                   ├──────┤ ← no overlap: 15 > 10 → push [8,10], start [15,18]

Result: [[1,6],[8,10],[15,18]] ✓

Containment case (easy bug):
 [1, 10]   [2, 5]
   ├──────────┤
      ├──┤         ← overlap: 2 ≤ 10 → prev.end = max(10, 5) = 10 (NOT 5)
Result: [1, 10] ✓  (wrong if you used curr.end directly)

KEY INVARIANT:
   After sorting, if curr.start ≤ last.end, they overlap.
   Always extend with max(last.end, curr.end) — never just curr.end.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Sort intervals by start time.**
2. **Initialize result with the first interval.**
3. **For each subsequent interval:** if it overlaps the last result interval (start ≤ lastEnd), extend `lastEnd`; otherwise push current and move on.
4. **Return result list.**

```java
public int[][] merge(int[][] intervals) {
    // Step 1: sort by start time
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

    // Step 2: initialize result with first interval
    List<int[]> result = new ArrayList<>();
    result.add(intervals[0]);

    // Step 3: scan and merge
    for (int i = 1; i < intervals.length; i++) {
        int[] last = result.get(result.size() - 1);
        int[] curr = intervals[i];

        if (curr[0] <= last[1]) {
            // Overlap: extend the end (take max to handle containment)
            last[1] = Math.max(last[1], curr[1]);
        } else {
            // No overlap: push current as a new interval
            result.add(curr);
        }
    }

    // Step 4: convert to array
    return result.toArray(new int[0][]);
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (pairwise) | O(n²) | O(n) |
| **Sort + linear scan** | **O(n log n)** | **O(n)** output |

---

### 🔁 Follow-Up Questions + Variants

**Q1: "What if intervals are already sorted? Can you skip the sort?"**
> Yes — O(n) instead of O(n log n). In practice, always clarify this assumption with the interviewer; sorted input is a meaningful constraint.

**Q2: "Insert a new interval into an already-merged list." (LC 57)**

**Why it's different from merge intervals:** The input is already sorted and merged — no sort needed. You scan once in three phases:

```
Phase 1: intervals ending BEFORE newInterval starts → no overlap → copy as-is
Phase 2: intervals OVERLAPPING with newInterval → expand newInterval to cover all
Phase 3: remaining intervals → copy as-is
```

**Overlap condition:** `intervals[i][0] <= newInterval[1]` — i.e., the existing interval starts before or when newInterval ends.
**Non-overlap (left):** `intervals[i][1] < newInterval[0]` — the existing interval ends before newInterval starts.

**Steps in plain English:**

1. Copy all intervals that end strictly before `newInterval[0]` (left of gap).
2. Merge all intervals that overlap with `newInterval`: expand both endpoints.
3. Add the merged `newInterval`.
4. Copy all remaining intervals (right of gap).

```java
public int[][] insert(int[][] intervals, int[] newInterval) {
    List<int[]> result = new ArrayList<>();
    int i = 0;
    int n = intervals.length;
    // Phase 1: no overlap — existing interval ends before newInterval starts
    while (i < n && intervals[i][1] < newInterval[0]) {
        result.add(intervals[i]);
        i++;
    }
    // Phase 2: overlap — absorb all overlapping intervals into newInterval
    while (i < n && intervals[i][0] <= newInterval[1]) {
        newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
        newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
        i++;
    }
    // Add the merged interval (possibly expanded across multiple originals)
    result.add(newInterval);
    // Phase 3: no overlap — remaining intervals start after newInterval ends
    while (i < n) {
        result.add(intervals[i]);
        i++;
    }
    return result.toArray(new int[0][]);
}
```

**Q3: "Find the minimum number of meeting rooms required." (LC 253)**

**Insight:** Sort starts and ends separately. Use two pointers — one tracking "when does the next meeting start" and one tracking "when does the earliest-finishing ongoing meeting end." If the next meeting starts before the earliest end, we need a new room. Otherwise, we reuse one.

**Why separate arrays (not pairs):** Sorting by start separately from end lets us compare all start times against all end times without caring which meeting is which. We only care about the counts.

**Steps in plain English:**

1. Extract and sort `starts[]` and `ends[]` independently.
2. `rooms` = total rooms allocated (running count). `endPtr` = index into `ends[]`.
3. For each start time (in order): if a meeting has ended by now (`starts[i] >= ends[endPtr]`), reuse that room (`endPtr++`). Otherwise, allocate a new room (`rooms++`).
4. Return `rooms` — this equals the maximum concurrent meetings at any point.

```java
public int minMeetingRooms(int[][] intervals) {
    int n = intervals.length;
    int[] starts = new int[n];
    int[] ends = new int[n];
    for (int i = 0; i < n; i++) {
        starts[i] = intervals[i][0];
        ends[i] = intervals[i][1];
    }
    Arrays.sort(starts);
    Arrays.sort(ends);
    int rooms = 0;
    int endPtr = 0;
    for (int i = 0; i < n; i++) {
        if (starts[i] >= ends[endPtr]) {
            // A room freed up before this meeting starts — reuse it
            endPtr++;
        } else {
            // No room free — must allocate a new one
            rooms++;
        }
    }
    return rooms;
}
```

> **Trace on `[[0,30],[5,10],[15,20]]`:**
> starts=[0,5,15], ends=[10,20,30]
> i=0: 0≥10? No → rooms=1
> i=1: 5≥10? No → rooms=2
> i=2: 15≥10? Yes → endPtr=1; 15≥20? No → rooms stays 2
> Result: 2 rooms needed ✓

**Q4: "What if intervals represent jobs and you want to maximize the number of non-overlapping jobs?"** (Activity Selection)
> Sort by **end time** (not start). Greedy: take the earliest-ending job that starts after the previous one. O(n log n) sort + O(n) scan.

**Q5: "The input is a stream of intervals — how do you maintain a merged set dynamically?"**
> Use a `TreeMap<Integer, Integer>` keyed by start, valued by end. For each new interval, find overlapping entries via `floorKey` and `ceilingKey`, merge them, and update the map. O(log n) per insert.

---

---

## 14. 3Sum — LC 15

**Difficulty:** Medium | **Pattern:** Sort + Two Pointers | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report — treat as medium-frequency drill.

---

### 🎯 Problem Statement

Given an integer array `nums`, return all **unique** triplets `[nums[i], nums[j], nums[k]]` such that `i != j != k` and `nums[i] + nums[j] + nums[k] == 0`.

```
Input:  nums = [-1, 0, 1, 2, -1, -4]
Output: [[-1,-1,2],[-1,0,1]]
```

---

### 🧠 Discussion — How to Think About This

The naive approach fixes every pair `(i, j)` and binary-searches for the complement — O(n² log n). Better: fix one element with `i`, then reduce to a sorted two-pointer scan for the remaining two. This gives O(n²) with correct deduplication.

**Three duplicate pitfalls — the classic bugs:**
1. **Outer loop:** skip `nums[i]` if `nums[i] == nums[i-1]` (and `i > 0`). Otherwise you process the same fixed value twice.
2. **After finding a match:** skip `left` forward while `nums[left] == nums[left-1]` — same left value would produce duplicate triplets with same `right`.
3. **Same after finding a match:** skip `right` backward while `nums[right] == nums[right+1]`.

All three must happen. Missing any one of them produces duplicates in the output.

---

### 🐌 Brute Force

Three nested loops, then deduplicate using a `Set<List<Integer>>`. O(n³) time. Correct but too slow.

```java
public List<List<Integer>> threeSum(int[] nums) {
    Set<List<Integer>> result = new HashSet<>();
    int n = nums.length;
    for (int i = 0; i < n - 2; i++) {
        for (int j = i + 1; j < n - 1; j++) {
            for (int k = j + 1; k < n; k++) {
                if (nums[i] + nums[j] + nums[k] == 0) {
                    List<Integer> triplet = Arrays.asList(nums[i], nums[j], nums[k]);
                    Collections.sort(triplet);
                    result.add(triplet);
                }
            }
        }
    }
    return new ArrayList<>(result);
}
```

---

### 💡 Idea Behind Optimisation

Sort first. Fix `nums[i]`. The problem becomes: "find two numbers in `nums[i+1..n-1]` that sum to `-nums[i]`." On a sorted array, two-pointer solves this in O(n). We do this for every `i` → O(n²) total.

---

### 🎨 Visual — Fix i, Two-Pointer on the Rest

```
nums = [-4, -1, -1, 0, 1, 2]  (sorted)
         ↑
         i=0: nums[i]=-4, target=4
         L=1, R=5: -1+2=1 < 4 → L++
         L=2, R=5:  0+2=2 < 4 → L++  (skip)
         ... no match

         i=1: nums[i]=-1, target=1
              ↑           ↑
              L=2          R=5: -1+2=1 == 1 → MATCH [-1,-1,2]
              skip L while nums[L]==nums[L+1]: nums[2]=-1 ≠ nums[3]=0 → no skip
              skip R while nums[R]==nums[R-1]: nums[5]=2  ≠ nums[4]=1 → no skip
              both pointers inward: L=3, R=4
              L=3, R=4: 0+1=1 == 1 → MATCH [-1,0,1]
              L=4, R=3 → done (L ≥ R, exit)

         i=2: nums[2]=-1 == nums[1] → SKIP (outer loop dupe!)

         i=3: nums[i]=0, target=0
              L=4, R=5: 1+2=3 > 0 → R--
              L=4, R=4 → done (L < R required)

Result: [[-1,-1,2],[-1,0,1]] ✓

KEY INVARIANT:
   After sorting: nums[L] + nums[R] < target → L++
                 nums[L] + nums[R] > target → R--
                 = target → record; skip dupes on BOTH L and R before next step
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Sort the array.**
2. **Fix `i` from 0 to n-3.** Skip if `nums[i] > 0` (sorted, so no triplet can sum to 0). Skip if `nums[i] == nums[i-1]` (outer dupe).
3. **Two-pointer on `[i+1, n-1]`.** Move `left` / `right` based on sum vs target.
4. **On match:** record triplet, skip duplicate `left` and `right` values, then move both pointers inward.

```java
public List<List<Integer>> threeSum(int[] nums) {
    // Step 1: sort
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();

    // Step 2: fix outer element
    for (int i = 0; i < nums.length - 2; i++) {
        // Early exit: smallest remaining element already positive
        if (nums[i] > 0) {
            break;
        }
        // Skip outer duplicates
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;
        }

        // Step 3: two-pointer scan
        int left = i + 1;
        int right = nums.length - 1;

        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];

            if (sum == 0) {
                // Step 4: record match
                result.add(Arrays.asList(nums[i], nums[left], nums[right]));

                // Skip duplicate left values
                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                // Skip duplicate right values
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }
                // Move both inward for next pair
                left++;
                right--;
            } else if (sum < 0) {
                left++;
            } else {
                right--;
            }
        }
    }

    return result;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (3 loops + set) | O(n³) | O(n) |
| **Sort + two pointers** | **O(n²)** | **O(1)** (output not counted) |

---

### 🔁 Follow-Up Questions + Variants

**Q1: "3Sum Closest — find the triplet with sum closest to a target." (LC 16)**
> Same sort + two-pointer structure. Track `minDiff = abs(sum - target)` and update best triplet whenever `diff < minDiff`. No early-exit on exact match needed (just keep searching).

**Q2: "4Sum — extend to four elements summing to target." (LC 18)**

**The pattern:** 3Sum = fix one + two-pointer. 4Sum = fix two + two-pointer. The duplicate-skipping discipline must apply to BOTH outer loops, not just the outermost.

**Key pitfall:** Use `long` for the sum — four `int` values can overflow `int` range.

**Steps in plain English:**

1. Sort the array.
2. Outer loop fixes `nums[i]`. Skip duplicates (`i > 0 && nums[i] == nums[i-1]`).
3. Inner loop fixes `nums[j]` (starting from `i+1`). Skip duplicates (`j > i+1 && nums[j] == nums[j-1]`).
4. Two-pointer on `[j+1, n-1]` — same as 3Sum's inner scan.

```java
public List<List<Integer>> fourSum(int[] nums, int target) {
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();
    int n = nums.length;
    for (int i = 0; i < n - 3; i++) {
        // Skip duplicates for the first fixed element
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;
        }
        for (int j = i + 1; j < n - 2; j++) {
            // Skip duplicates for the second fixed element
            if (j > i + 1 && nums[j] == nums[j - 1]) {
                continue;
            }
            int left = j + 1;
            int right = n - 1;
            while (left < right) {
                // Use long to avoid int overflow (4 ints can exceed Integer.MAX_VALUE)
                long sum = (long) nums[i] + nums[j] + nums[left] + nums[right];
                if (sum == target) {
                    result.add(Arrays.asList(nums[i], nums[j], nums[left], nums[right]));
                    // Skip duplicate left values
                    while (left < right && nums[left] == nums[left + 1]) {
                        left++;
                    }
                    // Skip duplicate right values
                    while (left < right && nums[right] == nums[right - 1]) {
                        right--;
                    }
                    left++;
                    right--;
                } else if (sum < target) {
                    left++;
                } else {
                    right--;
                }
            }
        }
    }
    return result;
}
```

> **Complexity:** O(n³) — two nested loops O(n²) × two-pointer O(n). Same structure generalizes to K-Sum with recursion (fix outer element, recurse to (K-1)-Sum).

**Q3: "K-Sum in general."**

**The pattern:** every K-Sum reduces to (K-1)-Sum by fixing one element. Base case is 2-Sum (two-pointer). This is a clean recursion that interviewers sometimes ask you to generalize.

**Steps in plain English:**

1. Base case K=2: standard two-pointer on sorted array.
2. Recursive case: sort the array (once, at the top level). Fix `nums[i]`, recurse with `target - nums[i]` and range `[i+1, n-1]`. Skip duplicates for `nums[i]`.

```java
public List<List<Integer>> kSum(int[] nums, int target, int k) {
    Arrays.sort(nums);
    return kSumHelper(nums, target, k, 0);
}

private List<List<Integer>> kSumHelper(int[] nums, long target, int k, int start) {
    List<List<Integer>> result = new ArrayList<>();
    // Base case: two-pointer
    if (k == 2) {
        int left = start;
        int right = nums.length - 1;
        while (left < right) {
            long sum = (long) nums[left] + nums[right];
            if (sum == target) {
                result.add(new ArrayList<>(Arrays.asList(nums[left], nums[right])));
                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }
                left++;
                right--;
            } else if (sum < target) {
                left++;
            } else {
                right--;
            }
        }
        return result;
    }
    // Recursive case: fix nums[i], recurse to (k-1)-Sum
    for (int i = start; i <= nums.length - k; i++) {
        // Skip duplicates for the fixed element
        if (i > start && nums[i] == nums[i - 1]) {
            continue;
        }
        List<List<Integer>> sub = kSumHelper(nums, target - nums[i], k - 1, i + 1);
        for (List<Integer> list : sub) {
            List<Integer> combo = new ArrayList<>();
            combo.add(nums[i]);
            combo.addAll(list);
            result.add(combo);
        }
    }
    return result;
}
```

> **Complexity:** O(n^(K-1)) — K-2 nested loops × O(n) two-pointer scan. For K=3: O(n²). For K=4: O(n³). The sort is O(n log n) — negligible for K≥3.

**Q4: "What if the array has millions of elements — can you do better than O(n²)?"**
> No known sub-O(n²) algorithm for 3Sum in the comparison model. O(n²) is conjectured optimal (3SUM hardness). For approximate or probabilistic solutions, randomized methods exist but aren't interview-relevant.

**Q5: "Why can't you use a HashSet instead of two pointers for the inner search?"**
> You can — O(n²) time, O(n) extra space for the set. It's correct but wastes space and has worse constants. The two-pointer approach uses O(1) extra space and is the "clean" O(n²) answer interviewers expect.

---

## 15. LRU Cache — LC 146

**Difficulty:** Medium | **Pattern:** HashMap + Doubly Linked List | **Confirmed in:** eBay LC company tag (highest-risk 🧩 problem — backend design flavour)
> 🧩 No specific MTS1 onsite report, but this is the most likely of the 12 to be asked given eBay's backend focus.

---

### 🎯 Problem Statement

Design a data structure that follows the **Least Recently Used (LRU) cache** eviction policy. Support `O(1)` `get(key)` and `O(1)` `put(key, value)`. When the cache reaches capacity, evict the **least recently used** entry before inserting.

```
LRUCache cache = new LRUCache(2); // capacity 2
cache.put(1, 1);  // cache: {1=1}
cache.put(2, 2);  // cache: {1=1, 2=2}
cache.get(1);     // returns 1. LRU order now: 2 → 1 (1 most recent)
cache.put(3, 3);  // evict key 2 (LRU). cache: {1=1, 3=3}
cache.get(2);     // returns -1 (not found)
```

---

### 🧠 Discussion — How to Think About This

Two O(1) requirements clash:
- **O(1) lookup by key** → HashMap
- **O(1) evict the LRU element** → need to know which element is "oldest" without scanning → Doubly Linked List (DLL) where head = LRU, tail = MRU

Combining them: `HashMap<key, Node>` where every `Node` is a DLL node. The DLL maintains recency order. On every `get` or `put`, move the accessed node to the tail. On overflow, remove head.next (the LRU node) from both the DLL and the HashMap.

**Interview shortcut (Java):** `LinkedHashMap(capacity, 0.75f, true)` with `removeEldestEntry` override — one-liner answer. But the interviewer will almost certainly say "now implement without LinkedHashMap" → must know raw DLL.

**Critical bug to avoid:** when evicting, you must remove from BOTH the DLL and the HashMap. Forgetting the HashMap removal leaves stale keys that return wrong values on future `get`.

---

### 🐌 Brute Force

Use a `LinkedHashMap` with access-order mode:

```java
public class LRUCache extends LinkedHashMap<Integer, Integer> {
    private final int capacity;

    public LRUCache(int capacity) {
        // true = access-order (most-recently-accessed element moves to tail)
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    public int get(int key) {
        return super.getOrDefault(key, -1);
    }

    public void put(int key, int value) {
        super.put(key, value);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        // LinkedHashMap calls this after every put; return true to evict
        return size() > capacity;
    }
}
```

Correct and O(1). Fails the "no LinkedHashMap" follow-up.

---

### 💡 Idea Behind Optimisation

The raw DLL approach: maintain two sentinel nodes `dummyHead` (← LRU side) and `dummyTail` (← MRU side). All real nodes sit between them. The DLL order = recency order. HashMap maps keys to nodes for O(1) access.

---

### 🎨 Visual — DLL Recency Order

```
Initial state (capacity=2):
  dummyHead ↔ dummyTail

After put(1,1):
  dummyHead ↔ [1|1] ↔ dummyTail
                         ↑ most recent

After put(2,2):
  dummyHead ↔ [1|1] ↔ [2|2] ↔ dummyTail
               LRU            MRU

get(1) → move [1|1] to tail:
  dummyHead ↔ [2|2] ↔ [1|1] ↔ dummyTail
               LRU            MRU

put(3,3) → capacity exceeded → evict dummyHead.next = [2|2]:
  Remove [2|2] from DLL AND from HashMap
  dummyHead ↔ [1|1] ↔ [3|3] ↔ dummyTail
               LRU            MRU

KEY INVARIANT:
   dummyHead.next = LRU (evict this on overflow)
   dummyTail.prev = MRU (insert new/promoted nodes here)
   Every get/put moves the touched node to dummyTail.prev position.
```

---

### 🚀 Optimal Java Solution (Raw DLL — No LinkedHashMap)

**Steps in plain English:**

1. **Define a DLL Node** with `key`, `val`, `prev`, `next`.
2. **Initialize** `dummyHead` and `dummyTail` sentinels; wire them together.
3. **`get(key)`:** look up node in map; if found, move to tail (MRU) and return val; else return -1.
4. **`put(key, val)`:** if key exists, update val and move to tail. If new: create node, add to tail, add to map. If over capacity, remove `dummyHead.next` from both DLL and map.
5. **`moveToTail(node)`:** unlink from current position, insert before `dummyTail`.

```java
class LRUCache {

    private static class Node {
        int key;
        int val;
        Node prev;
        Node next;

        Node(int key, int val) {
            this.key = key;
            this.val = val;
        }
    }

    private final int capacity;
    private final Map<Integer, Node> map;
    private final Node dummyHead;
    private final Node dummyTail;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();

        // Step 2: sentinels simplify edge cases (no null-checks needed)
        dummyHead = new Node(0, 0);
        dummyTail = new Node(0, 0);
        dummyHead.next = dummyTail;
        dummyTail.prev = dummyHead;
    }

    public int get(int key) {
        // Step 3: lookup and promote to MRU
        Node node = map.get(key);
        if (node == null) {
            return -1;
        }
        moveToTail(node);
        return node.val;
    }

    public void put(int key, int value) {
        // Step 4a: update existing key
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            moveToTail(node);
            return;
        }

        // Step 4b: insert new node at tail
        Node newNode = new Node(key, value);
        map.put(key, newNode);
        insertBeforeTail(newNode);

        // Step 4c: evict LRU if over capacity
        if (map.size() > capacity) {
            Node lru = dummyHead.next;
            // Remove from DLL
            removeNode(lru);
            // Remove from map — BOTH must happen or stale keys remain
            map.remove(lru.key);
        }
    }

    // Step 5: unlink node, insert just before dummyTail
    private void moveToTail(Node node) {
        removeNode(node);
        insertBeforeTail(node);
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void insertBeforeTail(Node node) {
        node.prev = dummyTail.prev;
        node.next = dummyTail;
        dummyTail.prev.next = node;
        dummyTail.prev = node;
    }
}
```

---

### ⏱️ Complexity

| Approach | Time (get/put) | Space |
|---|---|---|
| LinkedHashMap shortcut | O(1) amortized | O(capacity) |
| **HashMap + raw DLL** | **O(1)** | **O(capacity)** |

---

### 🔁 Follow-Up Questions + Variants

**Q1: "What if we want LFU (Least Frequently Used) instead of LRU?" (LC 460)**

**Why LRU's DLL doesn't work:** LRU evicts by recency — one ordered DLL is enough. LFU evicts by frequency, with recency as a tiebreaker within the same frequency. You need a DLL *per frequency bucket* plus a way to track the minimum frequency.

**Three data structures:**

```
keyToVal:   key → value                          (lookup value)
keyToFreq:  key → frequency                      (how often accessed)
freqToKeys: freq → LinkedHashSet<key>            (all keys at that freq, ordered by recency)
minFreq:    int                                  (current minimum freq — for eviction)
```

**Why `LinkedHashSet` not `DoublyLinkedList`:** `LinkedHashSet` preserves insertion order (= LRU within a bucket) and gives O(1) add/remove by key. You'd need both for a manual DLL.

**Steps in plain English:**

1. `get(key)`: if key exists, call `incrementFreq(key)` then return value. Otherwise return -1.
2. `put(key, value)`: if key exists, update value + `incrementFreq`. If new: evict if at capacity (remove `minFreq` bucket's first entry), then insert with freq=1, set `minFreq=1`.
3. `incrementFreq(key)`: remove key from freq bucket, add to freq+1 bucket. If freq bucket is now empty and it was `minFreq`, update `minFreq = freq+1`.

```java
class LFUCache {

    private final int capacity;
    private int minFreq;
    private final Map<Integer, Integer> keyToVal;
    private final Map<Integer, Integer> keyToFreq;
    // LinkedHashSet: preserves insertion order → LRU within a frequency bucket
    private final Map<Integer, LinkedHashSet<Integer>> freqToKeys;

    public LFUCache(int capacity) {
        this.capacity = capacity;
        this.minFreq = 0;
        this.keyToVal = new HashMap<>();
        this.keyToFreq = new HashMap<>();
        this.freqToKeys = new HashMap<>();
    }

    public int get(int key) {
        if (!keyToVal.containsKey(key)) {
            return -1;
        }
        incrementFreq(key);
        return keyToVal.get(key);
    }

    public void put(int key, int value) {
        if (capacity <= 0) {
            return;
        }
        if (keyToVal.containsKey(key)) {
            // Existing key: update value, increment frequency
            keyToVal.put(key, value);
            incrementFreq(key);
            return;
        }
        // New key: evict if at capacity
        if (keyToVal.size() >= capacity) {
            evictLFU();
        }
        // Insert with frequency 1
        keyToVal.put(key, value);
        keyToFreq.put(key, 1);
        freqToKeys.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        // A new key always starts at freq=1 — that becomes the new minimum
        minFreq = 1;
    }

    private void incrementFreq(int key) {
        int freq = keyToFreq.get(key);
        int newFreq = freq + 1;
        keyToFreq.put(key, newFreq);
        // Remove from current frequency bucket
        freqToKeys.get(freq).remove(key);
        if (freqToKeys.get(freq).isEmpty()) {
            freqToKeys.remove(freq);
            // If this was the minimum frequency bucket and it's now empty, update minFreq
            if (minFreq == freq) {
                minFreq = newFreq;
            }
        }
        // Add to the next frequency bucket
        freqToKeys.computeIfAbsent(newFreq, k -> new LinkedHashSet<>()).add(key);
    }

    private void evictLFU() {
        // minFreq bucket's FIRST entry = least recently used among least frequent
        LinkedHashSet<Integer> minBucket = freqToKeys.get(minFreq);
        int evictKey = minBucket.iterator().next();
        minBucket.remove(evictKey);
        if (minBucket.isEmpty()) {
            freqToKeys.remove(minFreq);
        }
        keyToVal.remove(evictKey);
        keyToFreq.remove(evictKey);
    }
}
```

> **LRU vs LFU decision in interviews:** LRU is O(1) with one DLL + HashMap. LFU adds O(1) complexity via bucketed DLLs (or LinkedHashSets) per frequency. LFU avoids the "cache pollution" problem (a one-time batch read flushes the useful cache), but is harder to implement. If asked "which would you use?", name the trade-off.

**Q2: "Thread-safe LRU cache — how would you handle concurrent access?"**
> Wrap `get` and `put` in `synchronized` blocks (coarse lock — simple but blocks all reads). For higher throughput: `ReentrantReadWriteLock` (reads concurrent, write exclusive). For production: `ConcurrentHashMap` + striped locking per key segment, but the DLL operations still require a global lock on the order structure.

**Q3: "What if get should NOT update recency (peek-only access)?"**
> Add a `peek(key)` method that looks up the map without calling `moveToTail`. The existing `get` stays as-is (updates recency).

**Q4: "Extend to a TTL-aware cache — evict entries that haven't been accessed in X seconds."**
> Each node stores a `long expiresAt = System.currentTimeMillis() + ttl`. On `get`, if expired, evict and return -1. A background `ScheduledExecutorService` can proactively sweep expired entries. The DLL still handles LRU eviction when capacity is hit.

**Q5: "Why are two sentinel nodes (dummyHead/dummyTail) better than null-terminated lists?"**
> Without sentinels, every `insertBeforeTail` and `removeNode` needs null-checks for head/tail edge cases. Sentinels guarantee `node.prev` and `node.next` are always non-null — no branches, no bugs, cleaner code.

---

---

## 16. Merge K Sorted Lists — LC 23

**Difficulty:** Hard | **Pattern:** Min-Heap (PriorityQueue) | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report.

---

### 🎯 Problem Statement

You are given an array of `k` linked lists, each sorted in ascending order. Merge all of them into one sorted linked list and return it.

```
Input:  [[1→4→5], [1→3→4], [2→6]]
Output: 1→1→2→3→4→4→5→6
```

---

### 🧠 Discussion — How to Think About This

At any point, the next node to add to the result is the minimum among the current heads of all k lists. Scanning all k heads each time = O(k) per node, O(nk) total. Use a min-heap to reduce this to O(log k) per node.

The heap holds at most `k` nodes at any time — one from each list. Poll the minimum, then push that node's `.next` (if it exists). This always keeps exactly the current frontier (one candidate per list) in the heap.

---

### 🐌 Brute Force

Collect all nodes, sort them by value, rebuild the list. O(n log n) where n = total nodes. Correct but doesn't use the fact that lists are already sorted.

---

### 💡 Idea Behind Optimisation

The heap exploits the pre-sorted property of each list. We never need to look beyond the current head of each list — if we know the minimum of all heads, the next candidate is either that node's `.next` or another list's head.

---

### 🎨 Visual — Min-Heap Step by Step

```
Lists: [1→4→5]  [1→3→4]  [2→6]

Initial heap (sorted by val): {1(L1), 1(L2), 2(L3)}

Step 1: poll 1(L1) → result: 1→
        push L1.next = 4
        heap: {1(L2), 2(L3), 4(L1)}

Step 2: poll 1(L2) → result: 1→1→
        push L2.next = 3
        heap: {2(L3), 3(L2), 4(L1)}

Step 3: poll 2(L3) → result: 1→1→2→
        push L3.next = 6
        heap: {3(L2), 4(L1), 6(L3)}

Step 4: poll 3(L2) → result: 1→1→2→3→
        push L2.next = 4
        heap: {4(L1), 4(L2), 6(L3)}

...continues until heap empty

KEY INVARIANT:
   Heap always holds exactly the current "frontier" —
   one candidate per list that still has unprocessed nodes.
   Heap size ≤ k at all times.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Add the head of each non-null list to a min-heap** ordered by `val`.
2. **Poll the minimum node** from the heap; append it to the result.
3. **If the polled node has a `.next`**, push `.next` into the heap.
4. **Repeat until heap is empty.**

```java
public ListNode mergeKLists(ListNode[] lists) {
    // Step 1: min-heap ordered by node value
    PriorityQueue<ListNode> heap = new PriorityQueue<>(
        (a, b) -> a.val - b.val
    );

    for (ListNode head : lists) {
        if (head != null) {
            heap.offer(head);
        }
    }

    // Dummy head simplifies building the result list
    ListNode dummy = new ListNode(0);
    ListNode curr = dummy;

    // Steps 2 & 3: poll minimum, push its next
    while (!heap.isEmpty()) {
        ListNode node = heap.poll();
        curr.next = node;
        curr = curr.next;

        if (node.next != null) {
            heap.offer(node.next);
        }
    }

    return dummy.next;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Collect + sort | O(n log n) | O(n) |
| Sequential merge (merge 2 at a time) | O(nk) | O(1) |
| **Min-Heap** | **O(n log k)** | **O(k)** heap |
| Divide-and-conquer merge | O(n log k) | O(log k) stack |

> n = total nodes across all lists, k = number of lists.

---

### 🔁 Follow-Up Questions + Variants

**Q1: "Why is O(n log k) better than merging sequentially (k-1 merge passes)?"**
> Sequential merge: merge list 1+2 (cost n₁+n₂), then + list 3 (cost n₁+n₂+n₃), ..., total O(nk). Heap processes each of the n nodes exactly once with O(log k) heap ops → O(n log k). For large k (e.g. k=1000), the difference is enormous.

**Q2: "Can you do it with divide-and-conquer without a heap?"**
> Yes — merge pairs of lists, then pairs of merged lists, etc. log k rounds, each O(n). Same asymptotic O(n log k) but constant factors differ. No heap needed — just the two-list merge from LC 21.

**Q3: "What if k is very large — say 10,000 lists?"**
> The heap approach still works — heap of size k = 10,000 is O(log 10,000 ≈ 13) per poll. Very practical. Memory holds only k nodes at once, not all n.

**Q4: "What if each list is an Iterator (lazy / infinite stream)?"**
> Same heap approach but push the first element from each iterator, and on poll push the next element from that same iterator. Works with iterators — no need to load the full list.

**Q5: "Merge K sorted arrays instead of linked lists."**
> Same heap, but each heap entry holds `(value, listIndex, elementIndex)`. On poll, advance `elementIndex` within that list. O(n log k), O(k) heap space.

---

---

## 17. Top K Frequent Elements — LC 347

**Difficulty:** Medium | **Pattern:** Bucket Sort (or Min-Heap) | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report.

---

### 🎯 Problem Statement

Given an integer array `nums` and an integer `k`, return the `k` most frequent elements. The answer is guaranteed to be unique.

```
Input:  nums = [1,1,1,2,2,3], k = 2
Output: [1, 2]
```

---

### 🧠 Discussion — How to Think About This

Two approaches, both O(n) / O(n log n):

**Approach 1 — Min-Heap:** Count frequencies. Maintain a min-heap of size k. For each number, if heap size < k, push. Else if its frequency > heap's minimum frequency, replace. Result: top k elements. O(n log k).

**Approach 2 — Bucket Sort:** Count frequencies (max possible frequency = n). Create `n+1` buckets indexed by frequency. Each bucket is a list of numbers with that frequency. Iterate buckets backwards to collect top k. O(n). This is the optimal answer interviewers want.

The bucket sort beats the heap approach by a log-k factor — always explain why when presenting it.

---

### 🐌 Brute Force

Count frequencies, sort by frequency descending, take first k.

```java
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int num : nums) {
        freq.put(num, freq.getOrDefault(num, 0) + 1);
    }
    List<Integer> keys = new ArrayList<>(freq.keySet());
    keys.sort((a, b) -> freq.get(b) - freq.get(a));
    int[] result = new int[k];
    for (int i = 0; i < k; i++) {
        result[i] = keys.get(i);
    }
    return result;
}
```

O(n log n) — dominated by the sort.

---

### 💡 Idea Behind Optimisation

Frequency is bounded: no element can appear more than `n` times. So we can use the frequency value itself as an index — no sort needed. Buckets give us O(n) time.

---

### 🎨 Visual — Bucket Sort by Frequency

```
nums = [1,1,1,2,2,3], k=2

Step 1 — frequency map:
  {1→3, 2→2, 3→1}

Step 2 — create n+1 = 7 buckets (indexed 0..6):
  bucket[0]: []
  bucket[1]: [3]        ← num 3 appears 1 time
  bucket[2]: [2]        ← num 2 appears 2 times
  bucket[3]: [1]        ← num 1 appears 3 times
  bucket[4]: []
  bucket[5]: []
  bucket[6]: []

Step 3 — iterate backwards (highest freq first), collect k=2 elements:
  bucket[3]: add 1  → result=[1], count=1
  bucket[2]: add 2  → result=[1,2], count=2 → DONE

Output: [1, 2] ✓

KEY INVARIANT:
   Frequency is bounded by n — so freq can be an array index.
   Iterating buckets from n down to 0 gives highest-frequency first.
   No sort needed; O(n) time, O(n) space.
```

---

### 🚀 Optimal Java Solution (Bucket Sort)

**Steps in plain English:**

1. **Count frequencies** using a HashMap.
2. **Create `n+1` buckets** (List arrays indexed by frequency).
3. **Fill buckets:** for each number, add it to `buckets[frequency]`.
4. **Iterate buckets from high to low**, collecting elements until we have k.

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1: count frequencies
    Map<Integer, Integer> freq = new HashMap<>();
    for (int num : nums) {
        freq.put(num, freq.getOrDefault(num, 0) + 1);
    }

    // Step 2: n+1 buckets; index = frequency
    int n = nums.length;
    List<Integer>[] buckets = new List[n + 1];
    for (int i = 0; i <= n; i++) {
        buckets[i] = new ArrayList<>();
    }

    // Step 3: fill buckets
    for (Map.Entry<Integer, Integer> entry : freq.entrySet()) {
        buckets[entry.getValue()].add(entry.getKey());
    }

    // Step 4: collect top k by iterating high-to-low frequency
    int[] result = new int[k];
    int idx = 0;
    for (int f = n; f >= 0 && idx < k; f--) {
        for (int num : buckets[f]) {
            result[idx++] = num;
            if (idx == k) {
                break;
            }
        }
    }

    return result;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Sort by frequency | O(n log n) | O(n) |
| Min-Heap size k | O(n log k) | O(n + k) |
| **Bucket Sort** | **O(n)** | **O(n)** |

---

### 🔧 Min-Heap Approach — O(n log k)

**When the interviewer steers you here:**
- "Space is tight — can you avoid allocating `n` buckets?"
- "What if the elements are strings, not integers?"
- "What if k is 3 but n is 10^9?"

Bucket sort allocates an array of size `n+1` — fine when n is the array length. But if n is huge, or the domain isn't bounded by an integer, that allocation is impractical. A min-heap of size k uses O(k) extra space (after the freq map) and works for any comparable type.

**Mental model:** The heap is a **k-best-candidates filter**. Its root is always the *weakest* member — the element with the lowest frequency among the current top-k candidates. When a new candidate arrives:
- If its frequency beats the root's frequency → evict the root (it's no longer top-k), admit the new candidate.
- If its frequency is ≤ root's frequency → discard; it can't displace anyone.
- After all distinct elements are processed → heap holds exactly the top-k.

### 🎨 Visual — Min-Heap as k-best Filter

```
freq = {1→3, 2→2, 3→1},  k=2
min-heap ordered by frequency (root = lowest freq = evicted first)

offer(num=1, freq=3): heap = [(1,3)]              size 1 ≤ k, no evict
offer(num=2, freq=2): heap = [(2,2),(1,3)]         size 2 = k, no evict
                       ↑ root=(2,2): freq=2 is "weakest" top-k member

offer(num=3, freq=1): push → size 3 > k → evict root
                      poll() removes (3,1) — freq=1 < root freq=2
                      heap = [(2,2),(1,3)]          ✓ size restored to k

Drain: poll (2,2), poll (1,3) → result = [2, 1]
(top-k order within result is arbitrary — both are correct)

KEY INVARIANT:
   Heap root = weakest candidate (lowest freq in current top-k window).
   New element: offer + poll when size > k; evicted element can never be top-k.
   After all distinct elements processed: heap IS the top-k.
```

**Steps in plain English:**

1. **Count frequencies** using a HashMap.
2. **Create a min-heap** (`PriorityQueue`) ordered by frequency ascending — root = lowest-frequency element.
3. **For each distinct element**: push `[num, freq]`. If heap size exceeds k, poll (evict the lowest-frequency candidate — it's provably not in top-k).
4. **Drain the heap** into the result array.

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1: build frequency map
    Map<Integer, Integer> freq = new HashMap<>();
    for (int num : nums) {
        freq.put(num, freq.getOrDefault(num, 0) + 1);
    }

    // Step 2: min-heap of [num, freq] pairs — root = lowest-freq (weakest top-k member)
    PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> a[1] - b[1]);

    // Step 3: push each distinct element; evict weakest when over k
    for (Map.Entry<Integer, Integer> entry : freq.entrySet()) {
        heap.offer(new int[]{entry.getKey(), entry.getValue()});
        if (heap.size() > k) {
            // Evict lowest-frequency element — provably not in top-k
            heap.poll();
        }
    }

    // Step 4: drain heap into result
    int[] result = new int[k];
    int idx = 0;
    while (!heap.isEmpty()) {
        int[] top = heap.poll();
        result[idx] = top[0];
        idx++;
    }
    return result;
}
```

> **Why `a[1] - b[1]` is safe:** Frequencies are always in `[1, n]` (bounded by array length). No overflow risk with int subtraction here. This is one of the few cases where the subtraction shorthand is safe — always verify bounds before using it.

> **Ties at the k-th boundary:** If two elements share the same frequency and one must be the k-th vs (k+1)-th, the heap evicts arbitrarily. LC 347 guarantees this won't happen ("answer is unique"). For problems with tie-breaking rules, you need a custom comparator — see Q2 below.

---

### 🔁 Follow-Up Questions + Variants

**Q1: "What if you must use O(n log k) time but O(1) extra space after counting?"**
> See the `### 🔧 Min-Heap Approach` section above for the full implementation. Space comparison: bucket sort allocates an O(n) bucket array on top of the O(n) freq map. The heap replaces that with an O(k) `PriorityQueue`. Both still need the freq map (unavoidable), so the heap wins when k << n and memory is at a premium.

**Q2: "Top K frequent words — same idea but strings, and ties broken alphabetically." (LC 692)**

**Why the comparator is subtle:** A min-heap evicts its root — the "weakest" candidate — when size > k. For LC 347 (integers, no tie-breaking), weakest = lowest frequency. For LC 692, among words with the same frequency, the *alphabetically later* word is weaker (because alphabetically earlier ranks higher in the result). So the eviction priority is:
- Lower frequency → evicted first
- Equal frequency + alphabetically later → evicted first

**How to read the comparator:** `compare(a, b)` negative → a is evicted first. `b.compareTo(a) > 0` means b comes after a alphabetically → returns positive → b is evicted first. ✓

**Steps in plain English:**

1. Count word frequencies.
2. Min-heap comparator: lower freq = evicted first; among equal freq, alphabetically later = evicted first.
3. Push each distinct word; poll when size > k.
4. Drain heap in reverse (heap gives weakest-first; result wants strongest-first, so prepend).

```java
public List<String> topKFrequent(String[] words, int k) {
    // Step 1: count word frequencies
    Map<String, Integer> freq = new HashMap<>();
    for (String word : words) {
        freq.put(word, freq.getOrDefault(word, 0) + 1);
    }

    // Step 2: min-heap with tie-breaking comparator
    // Eviction order: lower freq first; equal freq → alphabetically later first
    PriorityQueue<String> heap = new PriorityQueue<>((a, b) -> {
        int fa = freq.get(a);
        int fb = freq.get(b);
        if (fa != fb) {
            // Lower frequency is weaker — evicted first
            return fa - fb;
        }
        // Same frequency: alphabetically later is weaker — evicted first
        // b.compareTo(a) > 0 → b is later → positive → b is polled first
        return b.compareTo(a);
    });

    // Step 3: push each distinct word; evict weakest when over k
    for (String word : freq.keySet()) {
        heap.offer(word);
        if (heap.size() > k) {
            heap.poll();
        }
    }

    // Step 4: drain heap — heap gives weakest first; prepend each to reverse into result order
    // LinkedList.add(0, e) is O(1) — preferred over ArrayList which shifts on prepend
    List<String> result = new LinkedList<>();
    while (!heap.isEmpty()) {
        String word = heap.poll();
        result.add(0, word);
    }
    return result;
}
```

> **Trace (k=3):** `words=["the","day","is","an","apple","the","day","the"]`
> Frequencies: `{the→3, day→2, is→1, an→1, apple→1}`
> After processing all 5 distinct words, heap evicts the 2 weakest:
> Among freq=1 words {is, an, apple}: "is" > "apple" > "an" alphabetically → "is" evicted first, then "apple".
> Final heap: {the, day, an}. Drain+prepend: `["the", "day", "an"]` ✓

**Q3: "Find the single element that appears more than n/2 times." (LC 169 — Boyer-Moore)**

**Why it's fundamentally different:** The majority element appears MORE than n/2 times. It can "survive" a process of cancellation — for every non-majority element that cancels it out, there's still a net surplus of majority elements left. This is the Boyer-Moore voting algorithm.

**How to think about it:** Imagine a vote. Every matching element adds a vote; every different element cancels one. The majority element (>n/2 votes) will always be the last candidate standing because it has more votes than all others combined.

**Steps in plain English:**

1. Maintain a `candidate` and a `count`.
2. If `count == 0`: the current candidate is eliminated — adopt the new element as candidate.
3. If the current element matches `candidate`: `count++`. If it doesn't: `count--`.
4. After one pass, `candidate` is the majority element (guaranteed if majority exists).

```java
public int majorityElement(int[] nums) {
    int candidate = nums[0];
    int count = 1;
    for (int i = 1; i < nums.length; i++) {
        if (count == 0) {
            // Previous candidate eliminated — adopt this element
            candidate = nums[i];
            count = 1;
        } else if (nums[i] == candidate) {
            // Same side — strengthen the candidate
            count++;
        } else {
            // Different side — cancel one vote
            count--;
        }
    }
    return candidate;
}
```

> **Gotcha:** Boyer-Moore finds THE candidate but doesn't verify it exists. If the problem says "majority element is guaranteed to exist," return directly. If not guaranteed, do a second pass to count occurrences of `candidate` and verify `> n/2`. O(n) time, O(1) space — no HashMap needed at all.

**Q4: "Top K elements from a data stream — elements keep arriving."**

**Why the static heap breaks for streams:** Java's `PriorityQueue` has no "update priority" operation. When element `5` is in the heap with freq=2 and then arrives again (freq becomes 3), you can't update the existing entry — the old `(5, 2)` entry stays in the heap as a stale ghost. This breaks future polls.

**Lazy deletion (simple, O(n log n) query):** Push a fresh `(num, newFreq)` on every add. On query, skip stale entries (where `heap.peek().freq != currentFreq(num)`). Works but queries degrade to O(n log n) in the worst case.

**Clean streaming approach — TreeMap of frequency buckets (O(log F) add, O(k) query):**
- `freq: Map<Integer, Integer>` — element → current frequency
- `freqBuckets: TreeMap<Integer, Set<Integer>>` — frequency → set of elements at that frequency
- `TreeMap.descendingMap()` iterates from highest to lowest frequency for O(k) top-k query

**Steps in plain English:**

1. **`add(num)`**: look up old freq, remove `num` from old bucket, increment freq, insert into new bucket.
2. **`getTopK()`**: walk `freqBuckets.descendingMap()` collecting elements until k.

```java
class TopKFreqStream {
    private final int k;
    private final Map<Integer, Integer> freq;
    // TreeMap: frequency → set of elements at that frequency
    // descendingMap() iteration gives highest-frequency bucket first
    private final TreeMap<Integer, Set<Integer>> freqBuckets;

    public TopKFreqStream(int k) {
        this.k = k;
        this.freq = new HashMap<>();
        this.freqBuckets = new TreeMap<>();
    }

    public void add(int num) {
        int oldFreq = this.freq.getOrDefault(num, 0);
        int newFreq = oldFreq + 1;
        this.freq.put(num, newFreq);

        // Remove from old frequency bucket
        if (oldFreq > 0) {
            Set<Integer> oldBucket = this.freqBuckets.get(oldFreq);
            oldBucket.remove(num);
            if (oldBucket.isEmpty()) {
                this.freqBuckets.remove(oldFreq);
            }
        }

        // Add to new frequency bucket — create bucket if it doesn't exist yet
        Set<Integer> newBucket = this.freqBuckets.computeIfAbsent(newFreq, f -> new HashSet<>());
        newBucket.add(num);
    }

    public List<Integer> getTopK() {
        List<Integer> result = new ArrayList<>();
        // Iterate highest-frequency bucket first; stop when k elements collected
        for (Map.Entry<Integer, Set<Integer>> entry : this.freqBuckets.descendingMap().entrySet()) {
            for (int num : entry.getValue()) {
                result.add(num);
                if (result.size() == this.k) {
                    return result;
                }
            }
        }
        return result;
    }
}
```

**Complexity:**
- `add()`: O(log F) where F = number of distinct frequencies (≤ n)
- `getTopK()`: O(k) amortized

> **Trade-off vs lazy-deletion heap:** Lazy deletion is simpler to code but `getTopK()` degrades to O(n log n) worst case (must skip all stale entries). The TreeMap approach has cleaner guarantees at the cost of slightly more implementation. In an interview: start with the heap and explain the stale-entry problem, then propose the TreeMap as the production-grade alternative.

**Q5: "Why not use quickselect (partition by frequency) for O(n) average?"**
> You can — quickselect gives O(n) average, O(n²) worst case. Bucket sort gives O(n) worst case with O(n) space. Both are valid O(n) answers; bucket sort is simpler to code correctly in an interview.

---

---

## 18. Course Schedule — LC 207

**Difficulty:** Medium | **Pattern:** DFS Cycle Detection (3-color) / Kahn's BFS | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report.

---

### 🎯 Problem Statement

There are `numCourses` courses (0 to numCourses-1). `prerequisites[i] = [a, b]` means you must take course `b` before course `a`. Return `true` if it is possible to finish all courses (i.e., no circular dependency exists).

```
Input:  numCourses = 2, prerequisites = [[1,0]]
Output: true   (take 0, then 1)

Input:  numCourses = 2, prerequisites = [[1,0],[0,1]]
Output: false  (cycle: 0 requires 1, 1 requires 0)
```

---

### 🧠 Discussion — How to Think About This

This is: **does a directed graph have a cycle?** If yes → impossible to finish (cycle = courses that mutually require each other). If no → a valid order exists.

Two clean approaches:

**DFS 3-color (WHITE / GRAY / BLACK):**
- WHITE (0) = unvisited
- GRAY (1) = currently being explored (on the DFS stack)
- BLACK (2) = fully explored, no cycle through this node
- If DFS reaches a GRAY node, we found a back-edge → cycle detected.

**Kahn's BFS (topological sort):**
- Compute in-degree of every node.
- Push all zero-in-degree nodes to a queue.
- Process queue: for each node, reduce neighbor in-degrees; if any hit 0, push.
- If we process all `numCourses` nodes, no cycle. If fewer, a cycle exists.

DFS is slightly more intuitive for cycle detection. Kahn's produces the actual topological order (needed for LC 210).

---

### 🐌 Brute Force

Try every permutation of courses and check if it satisfies all prerequisites. O(numCourses!) — unusable.

---

### 💡 Idea Behind Optimisation

Model as a directed graph. Cycle detection is O(V + E) with DFS or BFS. The GRAY state is the key: it marks nodes currently on the DFS stack — a back-edge to a GRAY node = cycle.

---

### 🎨 Visual — 3-Color DFS

```
numCourses=4, prerequisites=[[1,0],[2,0],[3,1],[3,2]]

Graph (a ← b means "b required before a"):
  0 → 1 → 3
  0 → 2 → 3

DFS from node 0:
  visit 0: mark GRAY
    visit 1: mark GRAY
      visit 3: mark GRAY
        no unvisited neighbors
      mark 3 BLACK
    mark 1 BLACK
    visit 2: mark GRAY
      visit 3: already BLACK → OK (not GRAY, so no cycle)
    mark 2 BLACK
  mark 0 BLACK

No GRAY revisit → no cycle → return true ✓

Cycle case: prerequisites=[[1,0],[0,1]]
  visit 0: mark GRAY
    visit 1: mark GRAY
      visit 0: GRAY! → cycle detected → return false ✓

KEY INVARIANT:
   GRAY = node is on the current DFS call stack.
   Reaching a GRAY node = back-edge = cycle.
   BLACK = safe; skip without re-exploring.
```

---

### 🚀 Optimal Java Solution (DFS 3-color)

**Steps in plain English:**

1. **Build adjacency list** from prerequisites.
2. **Initialize `state[]` array** to 0 (WHITE) for all nodes.
3. **DFS from every unvisited node.** During DFS: mark current GRAY, recurse on neighbors; if any neighbor is GRAY → cycle → return false. After all neighbors done, mark BLACK.
4. **Return true** if no cycle found.

```java
public boolean canFinish(int numCourses, int[][] prerequisites) {
    // Step 1: build adjacency list
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] pre : prerequisites) {
        // pre[0] depends on pre[1] → edge from pre[1] to pre[0]
        adj.get(pre[1]).add(pre[0]);
    }

    // Step 2: 0=WHITE, 1=GRAY, 2=BLACK
    int[] state = new int[numCourses];

    // Step 3: DFS from every unvisited node
    for (int i = 0; i < numCourses; i++) {
        if (state[i] == 0) {
            if (hasCycle(adj, state, i)) {
                return false;
            }
        }
    }

    // Step 4: no cycle found
    return true;
}

private boolean hasCycle(List<List<Integer>> adj, int[] state, int node) {
    // Mark as currently being explored
    state[node] = 1;

    for (int neighbor : adj.get(node)) {
        if (state[neighbor] == 1) {
            // Back-edge: reached a node still on the stack → cycle
            return true;
        }
        if (state[neighbor] == 0) {
            // Unvisited: recurse
            if (hasCycle(adj, state, neighbor)) {
                return true;
            }
        }
        // state[neighbor] == 2 → already fully explored, safe
    }

    // Fully explored: mark BLACK
    state[node] = 2;
    return false;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (permutations) | O(numCourses!) | O(numCourses) |
| **DFS 3-color** | **O(V + E)** | **O(V + E)** adj + O(V) state |
| **Kahn's BFS** | **O(V + E)** | **O(V + E)** |

> V = numCourses, E = number of prerequisites.

---

### 🔁 Follow-Up Questions + Variants

**Q1: "Return the actual order to take all courses." (LC 210 — Course Schedule II)**

**Why the order matters:** LC 207 only asks "can we finish?" — a boolean. LC 210 asks "in what order?" — the topological sort itself.

**Two approaches — Kahn's BFS is cleaner for this:**

**Approach A — Kahn's BFS (the BFS processing order IS the topological order):**

**Steps in plain English:**

1. Build adjacency list + compute in-degree for each course.
2. Push all zero-in-degree courses to the queue (no prerequisites).
3. BFS: poll a course, add it to `order[]`, reduce neighbors' in-degrees; push newly zero-in-degree neighbors.
4. If we processed all `numCourses` nodes → return `order`. If fewer → cycle → return `new int[0]`.

```java
public int[] findOrder(int numCourses, int[][] prerequisites) {
    int[] inDegree = new int[numCourses];
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) {
        adj.add(new ArrayList<>());
    }
    for (int[] pre : prerequisites) {
        // pre[0] depends on pre[1]: edge pre[1] → pre[0]
        adj.get(pre[1]).add(pre[0]);
        inDegree[pre[0]]++;
    }
    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++) {
        if (inDegree[i] == 0) {
            queue.offer(i);
        }
    }
    int[] order = new int[numCourses];
    int idx = 0;
    while (!queue.isEmpty()) {
        int course = queue.poll();
        // BFS processing order IS the valid topological order
        order[idx++] = course;
        for (int next : adj.get(course)) {
            if (--inDegree[next] == 0) {
                queue.offer(next);
            }
        }
    }
    // If we processed all courses → no cycle → valid order
    return idx == numCourses ? order : new int[0];
}
```

**Approach B — DFS with a stack:** push node to a `Deque` AFTER marking BLACK (post-order). Pop the deque at the end for topological order. Use when you already have the DFS cycle-detection code from LC 207 and just need to extend it.

```java
// Extension of LC 207 DFS — only new parts shown
private boolean dfs(int node, List<List<Integer>> adj, int[] state, Deque<Integer> order) {
    state[node] = 1; // GRAY
    for (int neighbor : adj.get(node)) {
        if (state[neighbor] == 1) {
            return false; // cycle
        }
        if (state[neighbor] == 0 && !dfs(neighbor, adj, state, order)) {
            return false;
        }
    }
    state[node] = 2; // BLACK
    order.push(node); // push AFTER all neighbors — this gives reverse topological order
    return true;
}
// Caller: while (!order.isEmpty()) { result[idx++] = order.pop(); }
```

> **Which to use in an interview:** Kahn's BFS if the problem is purely LC 210. DFS extension if you've already written the LC 207 DFS and the interviewer says "now return the order."

**Q2: "Why does Kahn's BFS fail to detect cycles the same way DFS does?"**
> Kahn's doesn't mark GRAY — instead, cycle detection is implicit: if any nodes are never added to the queue (in-degree never reaches 0), they're in a cycle. The final count of processed nodes < V reveals this.

**Q3: "What if prerequisites form a DAG — can you find the minimum number of semesters to finish all courses?"**
> BFS level-by-level topological sort — each level = one semester. Nodes at level 0 = zero-in-degree. After processing a level, update in-degrees; newly zero-in-degree nodes form the next level.

**Q4: "What if the graph can have multiple edges between the same pair (multigraph)?"**
> Cycle detection still works — just deduplicate the adjacency list if needed. Multiple edges between A→B don't create a cycle; A→B + B→A does.

**Q5: "Can you detect if the graph is a tree (connected + acyclic)?"**
> Undirected: tree iff V-1 edges and connected. Directed: DAG iff no cycles (this problem). For directed trees (arborescences): exactly one root (in-degree 0), all others in-degree 1, plus no cycles.

---

## 19. Word Ladder — LC 127

**Difficulty:** Hard | **Pattern:** BFS + Wildcard Pattern Map | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report.

---

### 🎯 Problem Statement

Given a `beginWord`, `endWord`, and a `wordList`, return the **number of words** in the **shortest transformation sequence** from `beginWord` to `endWord`. Each step changes exactly one character; every intermediate word must be in `wordList`. Return 0 if no such sequence exists.

```
Input:  beginWord="hit", endWord="cog", wordList=["hot","dot","dog","lot","log","cog"]
Output: 5
Sequence: "hit" → "hot" → "dot" → "dog" → "cog"  (5 words = 4 transformations)
```

---

### 🧠 Discussion — How to Think About This

This is **unweighted shortest path on a graph** where:
- Nodes = words
- Edges = pairs of words differing by exactly one character

BFS gives shortest path in O(V + E). The problem: building edges naively is O(n² × L) where n = word count and L = word length — expensive.

**Optimisation:** wildcard pattern map. For each word, generate L patterns by replacing each character with `*`. Map each pattern to the words that match it. E.g., `"h*t" → ["hit", "hot"]`. To find neighbors of a word, generate its patterns and look them up in the map — O(L) per word, O(nL) total to build.

**Off-by-one common bug:** the answer is the number of **words** in the path, including both `beginWord` and `endWord`. BFS level counts transformations (edges), which is 1 less. The code must account for this — either initialize `level = 1` (for the start word) or return `level + 1`.

---

### 🐌 Brute Force

Build full adjacency list: for every pair of words, compare character by character. O(n²L) to build, then BFS O(n + n²) = O(n²). Too slow for large word lists.

---

### 💡 Idea Behind Optimisation

Wildcard patterns collapse O(n²) neighbor search into O(nL) preprocessing + O(L) lookup per word. This is the standard interview-expected approach.

---

### 🎨 Visual — BFS with Wildcard Map

```
Words: ["hot","dot","dog","lot","log","cog"], begin="hit", end="cog"

Wildcard map (partial):
  h*t → [hit, hot]
  *ot → [hot, dot, lot]
  ho* → [hot]
  d*t → [dot]
  *og → [dog, log, cog]
  do* → [dot, dog]
  ...

BFS from "hit":
Level 1: {"hit"}
  patterns of "hit": h*t → neighbors: [hot] (hit already visited)
  frontier: {"hot"}

Level 2: {"hot"}
  patterns of "hot": h*t→[hit,hot] skip, *ot→[dot,lot], ho*→[]
  frontier: {"dot","lot"}

Level 3: {"dot","lot"}
  "dot": d*t→[], *ot→[hot]skip, do*→[dog]
  "lot": l*t→[], *ot→[hot,dot]skip, lo*→[log]
  frontier: {"dog","log"}

Level 4: {"dog","log"}
  "dog": d*g→[], *og→[log,cog], do*→[dot]skip
  "log": l*g→[], *og→[dog,cog]
  frontier: {"cog"}

Level 5: {"cog"} — endWord found!
Return level count: 5 (levels 1 through 5 = 5 words in sequence) ✓

KEY INVARIANT:
   BFS level = number of words processed so far (including beginWord).
   Return the level when endWord is first dequeued — this is the word count.
   Each word is visited at most once; visited set prevents re-expansion.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Build wildcard pattern map** — for each word in wordList, generate L patterns; map each pattern to a list of matching words.
2. **BFS from beginWord.** Initialize queue with `{beginWord}`, visited set, level = 1.
3. **Each BFS level:** for each word, generate its patterns; for each pattern, look up unvisited neighbors. If neighbor = endWord, return `level + 1`. Else add to next level.
4. **Return 0** if BFS exhausts without finding endWord.

```java
public int ladderLength(String beginWord, String endWord, List<String> wordList) {
    // Step 1: build wildcard pattern map
    Map<String, List<String>> patternMap = new HashMap<>();
    for (String word : wordList) {
        for (int i = 0; i < word.length(); i++) {
            // Replace character i with '*'
            String pattern = word.substring(0, i) + "*" + word.substring(i + 1);
            patternMap.computeIfAbsent(pattern, k -> new ArrayList<>()).add(word);
        }
    }

    // Step 2: BFS setup
    Queue<String> queue = new LinkedList<>();
    Set<String> visited = new HashSet<>();
    queue.offer(beginWord);
    visited.add(beginWord);
    int level = 1;

    // Step 3: BFS level by level
    while (!queue.isEmpty()) {
        int size = queue.size();
        level++;

        for (int i = 0; i < size; i++) {
            String word = queue.poll();

            for (int j = 0; j < word.length(); j++) {
                String pattern = word.substring(0, j) + "*" + word.substring(j + 1);

                for (String neighbor : patternMap.getOrDefault(pattern, Collections.emptyList())) {
                    if (neighbor.equals(endWord)) {
                        // Found! level includes the endWord
                        return level;
                    }
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.offer(neighbor);
                    }
                }
            }
        }
    }

    // Step 4: no path found
    return 0;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (full adj list) | O(n²L) | O(n²) |
| **BFS + wildcard map** | **O(nL²)** | **O(nL)** pattern map |

> n = wordList size, L = word length. The nL² comes from: for each of n words, we generate L patterns of length L.

---

### 🔁 Follow-Up Questions + Variants

**Q1: "Return all shortest paths, not just the length." (LC 126 — Word Ladder II)**
> BFS same way but record predecessors instead of just visited state. After BFS, backtrack from endWord using predecessor map to reconstruct all paths. More complex: must allow multiple predecessors per node at the same BFS level.

**Q2: "What if the alphabet is large (Unicode) — you can't try all 26 substitutions?"**
> The wildcard pattern map approach doesn't enumerate the alphabet — it only uses actual words in the wordList. So it's correct regardless of alphabet size.

**Q3: "Bidirectional BFS — how does it improve performance?"**

**Why it's faster:** Standard BFS expands like a ripple from one side — the search space is O(b^d) where b = branching factor, d = depth. Bidirectional BFS runs two simultaneous ripples — one from `beginWord`, one from `endWord`. They meet in the middle, so each only expands to depth d/2 — the total is O(b^(d/2) + b^(d/2)) = O(b^(d/2)), exponentially smaller.

**How to think about it:** Instead of tracking visited nodes, track two sets — `beginSet` (current frontier from `beginWord`) and `endSet` (current frontier from `endWord`). Each iteration: expand the SMALLER frontier (to minimize branching). If any word in the new frontier is in the other frontier → found.

**Steps in plain English:**

1. Initialize `beginSet = {beginWord}`, `endSet = {endWord}`. Both are `HashSet<String>`.
2. Each iteration: if `beginSet` is larger than `endSet`, swap them (always expand the smaller one).
3. Expand `beginSet` by generating all one-character neighbors of each word. If neighbor is in `endSet` → return `level + 1`. If unvisited → add to `nextSet`.
4. Replace `beginSet` with `nextSet`. Increment level.

```java
public int ladderLength(String beginWord, String endWord, List<String> wordList) {
    Set<String> wordSet = new HashSet<>(wordList);
    if (!wordSet.contains(endWord)) {
        return 0;
    }
    Set<String> beginSet = new HashSet<>();
    Set<String> endSet = new HashSet<>();
    beginSet.add(beginWord);
    endSet.add(endWord);
    Set<String> visited = new HashSet<>();
    visited.add(beginWord);
    visited.add(endWord);
    int level = 1;
    while (!beginSet.isEmpty() && !endSet.isEmpty()) {
        // Always expand the smaller frontier to minimize branching
        if (beginSet.size() > endSet.size()) {
            Set<String> temp = beginSet;
            beginSet = endSet;
            endSet = temp;
        }
        Set<String> nextSet = new HashSet<>();
        for (String word : beginSet) {
            char[] chars = word.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                char original = chars[i];
                for (char c = 'a'; c <= 'z'; c++) {
                    if (c == original) {
                        continue;
                    }
                    chars[i] = c;
                    String next = new String(chars);
                    if (endSet.contains(next)) {
                        // Frontiers met — found the shortest path
                        return level + 1;
                    }
                    if (wordSet.contains(next) && !visited.contains(next)) {
                        visited.add(next);
                        nextSet.add(next);
                    }
                    chars[i] = original;
                }
            }
        }
        beginSet = nextSet;
        level++;
    }
    return 0;
}
```

> **Note:** This version tries all 26 substitutions per character (simpler than the wildcard map). For the interview, use whichever approach you coded for the main solution — the bidirectional optimization is orthogonal to the neighbor-finding strategy.

**Q4: "What if word length can vary?"**
> Words of different lengths can never be neighbors (one character change preserves length). So only words of the same length as beginWord matter. Filter wordList by length first.

**Q5: "Count the number of distinct shortest paths."**
> Modify BFS to track the level at which each word was first reached and allow re-visiting words from the same level (but not earlier levels). Count paths on backtracking.

---

---

## 20. All Nodes Distance K — LC 863

**Difficulty:** Medium | **Pattern:** BFS with Parent Pointers | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report.

---

### 🎯 Problem Statement

Given a binary tree, a `target` node, and an integer `k`, return a list of the values of all nodes that are exactly `k` edges away from `target`. Can go up through parents (not just down through children).

```
Input:  root = [3,5,1,6,2,0,8,null,null,7,4], target = 5, k = 2
Output: [7, 4, 1]
        (nodes 7 and 4 are k=2 downward from 5; node 1 is k=2 upward through 3)
```

---

### 🧠 Discussion — How to Think About This

In a regular graph, "all nodes at distance k" = straightforward BFS. In a binary tree, nodes only have child pointers — you can't go upward. The fix: **one DFS pass to build a parent map**, then BFS in all three directions (left child, right child, parent) treating the tree as an undirected graph.

**The critical visited set:** without it, BFS will go parent → child → parent → ... infinitely. The visited set ensures each node is processed at most once.

---

### 🐌 Brute Force

For every node in the tree, compute its distance from `target` by finding the LCA (Lowest Common Ancestor) and using tree path lengths. O(n²) — feasible but ugly.

---

### 💡 Idea Behind Optimisation

Separate the two concerns: (1) add parent pointers via DFS in O(n), (2) run BFS from target treating the tree as a graph in O(n). Each phase is linear.

---

### 🎨 Visual — Parent Map + BFS

```
Tree:           3
              /   \
             5     1
           /  \   / \
          6    2  0   8
              / \
             7   4

target = 5, k = 2

Step 1 — DFS to build parent map:
  parent[5] = 3
  parent[6] = 5
  parent[2] = 5
  parent[1] = 3
  parent[7] = 2
  parent[4] = 2
  parent[0] = 1
  parent[8] = 1

Step 2 — BFS from node 5, treating tree as undirected graph:
  Level 0: {5}              visited={5}
  Level 1: neighbors of 5 = {6 (left), 2 (right), 3 (parent)}
           visited={5,6,2,3}
  Level 2: neighbors of {6,2,3}:
    6: left=null, right=null, parent=5(visited)
    2: left=7, right=4, parent=5(visited)   → add 7, 4
    3: left=5(visited), right=1, parent=null → add 1
  Result at k=2: {7, 4, 1} ✓

KEY INVARIANT:
   Visited set is SHARED across all BFS directions.
   A node once added to visited is never re-expanded —
   this prevents parent→child→parent infinite loops.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **DFS the tree** to populate a `parent` map: `parent[node] = its parent node`.
2. **BFS from `target`** using a queue. Track `visited` set to avoid re-visiting.
3. **Each BFS step** explores left child, right child, and parent (three directions).
4. **After k levels**, return all values currently in the queue.

```java
public List<Integer> distanceK(TreeNode root, TreeNode target, int k) {
    // Step 1: DFS to build parent map
    Map<TreeNode, TreeNode> parent = new HashMap<>();
    buildParentMap(root, null, parent);

    // Step 2: BFS from target
    Queue<TreeNode> queue = new LinkedList<>();
    Set<TreeNode> visited = new HashSet<>();
    queue.offer(target);
    visited.add(target);
    int dist = 0;

    // Step 3: expand k levels
    while (!queue.isEmpty() && dist < k) {
        int size = queue.size();
        dist++;
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();

            // Explore left child
            if (node.left != null && !visited.contains(node.left)) {
                visited.add(node.left);
                queue.offer(node.left);
            }
            // Explore right child
            if (node.right != null && !visited.contains(node.right)) {
                visited.add(node.right);
                queue.offer(node.right);
            }
            // Explore parent (upward direction)
            TreeNode par = parent.get(node);
            if (par != null && !visited.contains(par)) {
                visited.add(par);
                queue.offer(par);
            }
        }
    }

    // Step 4: collect values of all nodes at exactly distance k
    List<Integer> result = new ArrayList<>();
    while (!queue.isEmpty()) {
        result.add(queue.poll().val);
    }
    return result;
}

private void buildParentMap(TreeNode node, TreeNode par, Map<TreeNode, TreeNode> parent) {
    if (node == null) {
        return;
    }
    parent.put(node, par);
    buildParentMap(node.left, node, parent);
    buildParentMap(node.right, node, parent);
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (LCA per node) | O(n²) | O(n) |
| **DFS parent map + BFS** | **O(n)** | **O(n)** parent map + visited |

---

### 🔁 Follow-Up Questions + Variants

**Q1: "What if the tree is not binary — N-ary tree?"**
> Same approach. DFS builds parent map for each child. BFS expands to all children + parent. Code changes: iterate `node.children` instead of `node.left` / `node.right`.

**Q2: "Return nodes at distance ≤ k (not exactly k)."**
> Collect all nodes encountered during the BFS over k levels, not just the frontier after exactly k steps. Adjust the BFS to return all visited nodes (excluding the target itself if desired).

**Q3: "What if we need to run this query multiple times for different targets?"**
> Precompute the parent map once for the tree (O(n)). Then for each query, run BFS in O(n) from the given target. Total: O(n) preprocessing + O(n) per query.

**Q4: "Why can't we just augment the TreeNode class with a parent field?"**
> In an interview you typically can't modify the given class definition. The parent map achieves the same without modification. If you could add a field, the DFS step becomes a single tree traversal that writes `node.parent = parentNode`.

**Q5: "Find the nearest node to target that satisfies a condition (e.g., value > X)."**
> Same BFS from target, but instead of stopping after k levels, stop at the first node where `node.val > X`. Return that node's value.

---

---

## 21. N-Queens — LC 51

**Difficulty:** Hard | **Pattern:** Backtracking + Column / Diagonal Sets | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report.

---

### 🎯 Problem Statement

Place `n` queens on an `n×n` chessboard so that no two queens attack each other (no two share the same row, column, or diagonal). Return **all distinct solutions**.

```
Input:  n = 4
Output: [[".Q..","...Q","Q...","..Q."],
          ["..Q.","Q...","...Q",".Q.."]]
```

---

### 🧠 Discussion — How to Think About This

Place one queen per row (rows are iterated in order — never two queens in the same row by construction). For each row, try every column. A placement is valid iff:
1. No other queen in the same **column** → track `cols` set.
2. No other queen in the same **main diagonal** (top-left to bottom-right) → all cells on the same diagonal share `row - col` → track `diag1` set.
3. No other queen in the same **anti-diagonal** (top-right to bottom-left) → all cells share `row + col` → track `diag2` set.

Three sets give O(1) validity check. After placing a queen, recurse to the next row. **Backtrack:** remove from all three sets before trying the next column in the current row.

**State restore bug:** forgetting to remove from all three sets before the next column attempt. The sets must be in the exact same state before and after exploring a branch.

---

### 🐌 Brute Force

Try all `n^n` placements of n queens (one per row, n choices per row). Check each for validity. O(n^n × n) — impractical.

---

### 💡 Idea Behind Optimisation

Prune immediately. After placing a queen at `(row, col)`, any column in the same column, same main diagonal, or same anti-diagonal is illegal for all future rows. The three sets encode these forbidden constraints in O(1), so invalid branches are cut before recursion.

---

### 🎨 Visual — Diagonal Invariants

```
n=4 board, queens at: (0,1), (1,3), (2,0), (3,2)

         col: 0   1   2   3
   row 0:    .   Q   .   .     row-col= -1, row+col= 1
   row 1:    .   .   .   Q     row-col= -2, row+col= 4
   row 2:    Q   .   .   .     row-col=  2, row+col= 2
   row 3:    .   .   Q   .     row-col=  1, row+col= 5

cols set:  {1, 3, 0, 2}   ← column of each placed queen
diag1 set: {-1,-2, 2, 1}  ← row-col of each queen (main diagonal id)
diag2 set: { 1, 4, 2, 5}  ← row+col of each queen (anti-diagonal id)

Validating (3,2) — the last queen to place:
  col=2: in cols={1,3,0}? No → ok
  diag1 = 3-2=1: in diag1={-1,-2,2}? No → ok
  diag2 = 3+2=5: in diag2={1,4,2}? No → ok → PLACE ✓

  Note: row 2's queen is at col=0 (not col=2); cols set = {1,3,0}, which does NOT
  contain 2. The placement is valid.

KEY INVARIANT:
   Main diagonal id  = row - col  (constant along top-left to bottom-right)
   Anti-diagonal id  = row + col  (constant along top-right to bottom-left)
   Three sets give O(1) validity; backtracking restores them completely.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Recurse row by row.** At each row, try every column.
2. **Check validity:** column not in `cols`; `row-col` not in `diag1`; `row+col` not in `diag2`.
3. **Place queen:** add to all three sets; record in `board[]`.
4. **Recurse to next row.** If we placed in all n rows, record the board as a solution.
5. **Backtrack:** remove from all three sets; reset `board[row]` for next column attempt.

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> result = new ArrayList<>();
    int[] board = new int[n];   // board[row] = column of queen in that row
    Arrays.fill(board, -1);

    Set<Integer> cols = new HashSet<>();
    Set<Integer> diag1 = new HashSet<>();   // row - col
    Set<Integer> diag2 = new HashSet<>();   // row + col

    backtrack(n, 0, board, cols, diag1, diag2, result);
    return result;
}

private void backtrack(
        int n, int row, int[] board,
        Set<Integer> cols, Set<Integer> diag1, Set<Integer> diag2,
        List<List<String>> result) {

    // Step 4a: base case — all rows filled
    if (row == n) {
        result.add(buildBoard(board, n));
        return;
    }

    for (int col = 0; col < n; col++) {
        // Step 2: check validity
        if (cols.contains(col)
                || diag1.contains(row - col)
                || diag2.contains(row + col)) {
            continue;
        }

        // Step 3: place queen
        cols.add(col);
        diag1.add(row - col);
        diag2.add(row + col);
        board[row] = col;

        // Step 4: recurse
        backtrack(n, row + 1, board, cols, diag1, diag2, result);

        // Step 5: backtrack — restore state
        cols.remove(col);
        diag1.remove(row - col);
        diag2.remove(row + col);
        board[row] = -1;
    }
}

private List<String> buildBoard(int[] board, int n) {
    List<String> boardView = new ArrayList<>();
    for (int row = 0; row < n; row++) {
        char[] line = new char[n];
        Arrays.fill(line, '.');
        line[board[row]] = 'Q';
        boardView.add(new String(line));
    }
    return boardView;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (all placements) | O(n^n × n) | O(n²) |
| **Backtracking with 3 sets** | **O(n!)** pruned | **O(n)** sets + stack |

> O(n!) is the upper bound; real execution prunes most branches far earlier.

---

### 🔁 Follow-Up Questions + Variants

**Q1: "Return just the count of distinct solutions, not the boards." (LC 52 — N-Queens II)**
> Same backtracking; replace `result.add(...)` with a `count++`. O(n!) but lighter on memory.

**Q2: "Can you solve N-Queens using bitmask instead of sets?"**

**Why bitmasks:** Instead of `Set<Integer>` for cols and diagonals, use three integers — CPU bitwise ops are faster than hash lookups, and the memory footprint shrinks from O(n) sets to O(1) integers.

**Three bitmasks — one per constraint type:**

```
cols   : bit c is set if column c has a queen
diag1  : bit (c - r + n - 1) is set if the ↘ diagonal through (r, c) has a queen
diag2  : bit (c + r) is set if the ↗ diagonal through (r, c) has a queen
```

**How to check if column c in row r is safe:** `(cols >> c & 1) == 0 && (diag1 >> (c - r + n - 1) & 1) == 0 && (diag2 >> (c + r) & 1) == 0`

**Steps in plain English:**

1. Same backtracking row-by-row structure as the set version.
2. To place a queen at `(row, col)`: `OR` the relevant bits into all three masks.
3. To backtrack: `XOR` the same bits back out (un-places the queen).
4. The "available columns" bitmask can be computed as `~(cols | diag1 | diag2)` — all bits not blocked. Iterate set bits for O(1) per candidate column.

```java
private int n;
private List<List<String>> result;

public List<List<String>> solveNQueens(int n) {
    this.n = n;
    this.result = new ArrayList<>();
    int[] queens = new int[n]; // queens[row] = column
    Arrays.fill(queens, -1);
    // cols, diag1 (↘), diag2 (↗) — all bits 0 = no queens yet
    backtrack(queens, 0, 0, 0, 0);
    return result;
}

private void backtrack(int[] queens, int row, int cols, int diag1, int diag2) {
    if (row == n) {
        result.add(buildBoard(queens));
        return;
    }
    // available: columns not blocked by any constraint (mask to n bits)
    int available = ((1 << n) - 1) & ~(cols | diag1 | diag2);
    while (available != 0) {
        // Isolate the lowest set bit (one valid column)
        int bit = available & (-available);
        available &= available - 1; // clear that bit
        int col = Integer.numberOfTrailingZeros(bit);
        queens[row] = col;
        // Place queen: OR the bit into each mask; diagonals shift by row for the next level
        backtrack(queens, row + 1,
                  cols | bit,
                  (diag1 | bit) << 1,
                  (diag2 | bit) >> 1);
        queens[row] = -1;
        // No explicit undo needed — bitmasks are passed by value (primitives)
    }
}

private List<String> buildBoard(int[] queens) {
    List<String> board = new ArrayList<>();
    for (int row = 0; row < n; row++) {
        char[] rowArr = new char[n];
        Arrays.fill(rowArr, '.');
        rowArr[queens[row]] = 'Q';
        board.add(new String(rowArr));
    }
    return board;
}
```

> **Key trick:** `(diag1 | bit) << 1` — shifting left propagates the diagonal one row down for the ↘ diagonal. `>> 1` for the ↗ diagonal. Bitmasks are primitives — they're passed by value, so there's no explicit backtrack step for the masks themselves. Only `queens[row] = -1` is needed for board reconstruction.
>
> **Speed advantage:** `available & (-available)` isolates the lowest set bit in O(1). The while loop iterates exactly over valid columns, never over blocked ones — unlike the set version which iterates 0..n-1 and checks membership.

**Q3: "Why does placing one queen per row guarantee rows are covered?"**
> We iterate rows 0 to n-1 in the outer recursion, placing exactly one queen per row by construction. The constraint is only on columns and diagonals — rows are implicitly distinct.

**Q4: "For large n (e.g., n=1000), is backtracking still feasible?"**
> No. For large n, heuristic approaches (min-conflicts, simulated annealing) are used. For n≤15, backtracking with pruning is fast enough. For n≤30, bitmask optimizations help. Beyond that, no polynomial-time algorithm is known.

**Q5: "Generalize: place n rooks on n×n board with no two attacking — how many solutions?"**
> Rooks only conflict on rows and columns (not diagonals). The number of valid placements = n! (any permutation of columns, one per row). No backtracking needed — it's just counting permutations.

---

---

## 22. Sudoku Solver — LC 37

**Difficulty:** Hard | **Pattern:** Backtracking + Row / Col / Box Sets | **Confirmed in:** eBay LC company tag
> 🧩 No specific MTS1 onsite report. Hardest of the 12 seen-once problems — only attempt if you have surplus time.

---

### 🎯 Problem Statement

Write a program to solve a Sudoku puzzle by filling the empty cells (`'.'`). Each digit 1–9 must appear exactly once in each row, column, and 3×3 box. The puzzle is guaranteed to have a unique solution.

```
Input:  9×9 board with '.' for empty cells and '1'-'9' for filled
Output: Same board, modified in-place with all cells filled
```

---

### 🧠 Discussion — How to Think About This

For each empty cell, try digits 1–9. A digit is valid iff it doesn't appear in:
1. The cell's **row**
2. The cell's **column**
3. The cell's **3×3 box** (box index = `(row/3) * 3 + col/3`)

Recurse to the next empty cell. If no digit works → **backtrack**: reset the cell to `'.'` and let the caller try the next digit.

**Pre-building the three constraint sets** (one `boolean[9]` per row, per column, per box) is the key efficiency move. Without them, each validity check scans the row/col/box — O(9) per check. With them, O(1) per check. The sets are updated on place and restored on backtrack.

**State restore bug (same as N-Queens):** you must reset `rows[r][d]`, `cols[c][d]`, and `boxes[b][d]` to `false` AND reset `board[r][c]` to `'.'` before trying the next digit.

---

### 🐌 Brute Force

No meaningful brute force — even trying all digit combinations is exponential. The backtracking approach IS the standard solution. The O(9^m) label is the theoretical upper bound (m = number of empty cells), but heavy pruning makes it fast in practice.

---

### 💡 Idea Behind Optimisation

Pre-fill constraint sets from the initial board. Scan cells left-to-right, top-to-bottom. For each empty cell, try valid digits only (O(1) check via sets). Recurse; backtrack on failure. The constraint sets make each validity check O(1) instead of O(9).

---

### 🎨 Visual — Box Index and State Restore

```
9×9 board divided into 3×3 boxes (0-indexed):

  box:  0 | 1 | 2
        ---------
        3 | 4 | 5
        ---------
        6 | 7 | 8

Box index for cell (row, col):
  boxIdx = (row / 3) * 3 + (col / 3)

Example: cell (4, 7) → box = (4/3)*3 + (7/3) = 1*3 + 2 = 5 ✓

Constraint arrays:
  rows[r][d]  = true if digit d is used in row r
  cols[c][d]  = true if digit d is used in col c
  boxes[b][d] = true if digit d is used in box b

On PLACE (row=r, col=c, digit=d, boxIdx=b):
  board[r][c] = (char)('0' + d)
  rows[r][d-1] = true
  cols[c][d-1] = true
  boxes[b][d-1] = true

On BACKTRACK (same r, c, d, b):
  board[r][c] = '.'
  rows[r][d-1] = false   ← must reset ALL three
  cols[c][d-1] = false
  boxes[b][d-1] = false

KEY INVARIANT:
   The three constraint arrays always reflect exactly the digits
   currently placed on the board. Backtracking must mirror every
   placement operation exactly — same arrays, same indices, set to false.
```

---

### 🚀 Optimal Java Solution

**Steps in plain English:**

1. **Pre-fill constraint sets** by scanning the initial board — for each filled cell, mark its digit in `rows`, `cols`, and `boxes`.
2. **`solve(board)`:** scan cells in order; find the first `'.'`.
3. **Try digits 1–9.** For each digit, check all three constraints (O(1)). If valid, place it (update board + all three sets) and recurse.
4. **If recursion returns `true`** → puzzle solved; propagate `true` up.
5. **If no digit works** → backtrack: restore cell to `'.'`, reset all three sets; return `false`.

```java
public void solveSudoku(char[][] board) {
    // Step 1: pre-fill constraint arrays
    boolean[][] rows = new boolean[9][9];
    boolean[][] cols = new boolean[9][9];
    boolean[][] boxes = new boolean[9][9];

    for (int r = 0; r < 9; r++) {
        for (int c = 0; c < 9; c++) {
            if (board[r][c] != '.') {
                int d = board[r][c] - '1';
                int b = (r / 3) * 3 + (c / 3);
                rows[r][d] = true;
                cols[c][d] = true;
                boxes[b][d] = true;
            }
        }
    }

    // Steps 2-5: backtracking solver
    solve(board, rows, cols, boxes);
}

private boolean solve(char[][] board, boolean[][] rows, boolean[][] cols, boolean[][] boxes) {
    // Step 2: find next empty cell
    for (int r = 0; r < 9; r++) {
        for (int c = 0; c < 9; c++) {
            if (board[r][c] != '.') {
                continue;
            }

            int b = (r / 3) * 3 + (c / 3);

            // Step 3: try digits 1-9
            for (int d = 0; d < 9; d++) {
                if (rows[r][d] || cols[c][d] || boxes[b][d]) {
                    continue;
                }

                // Place digit (d+1) at (r, c)
                board[r][c] = (char) ('1' + d);
                rows[r][d] = true;
                cols[c][d] = true;
                boxes[b][d] = true;

                // Step 4: recurse
                if (solve(board, rows, cols, boxes)) {
                    return true;
                }

                // Step 5: backtrack — restore all state
                board[r][c] = '.';
                rows[r][d] = false;
                cols[c][d] = false;
                boxes[b][d] = false;
            }

            // No digit worked for this cell → backtrack to previous cell
            return false;
        }
    }

    // All cells filled — solution found
    return true;
}
```

---

### ⏱️ Complexity

| Approach | Time | Space |
|---|---|---|
| **Backtracking + constraint sets** | **O(9^m)** upper bound, heavily pruned | **O(1)** board is in-place; O(81) for constraint arrays |

> m = number of empty cells. In practice, a well-formed puzzle is solved in microseconds because each constraint eliminates most of the 9 candidates immediately.

---

### 🔁 Follow-Up Questions + Variants

**Q1: "How do you validate a partially filled Sudoku board?" (LC 36)**
> For each filled cell, check its digit against the same three constraint sets. Scan the entire board once, building the sets and detecting conflicts. O(81) = O(1).

**Q2: "What's the most efficient known Sudoku solver?"**
> Donald Knuth's **Dancing Links** (DLX) — an efficient implementation of Algorithm X for exact cover problems. Sudoku maps naturally to exact cover. Not interview-relevant but good to mention to show breadth.

**Q3: "Can you solve Sudoku without backtracking using constraint propagation alone?"**
> For easy/medium puzzles, yes — **naked singles** and **hidden singles** propagation resolves most cells. But hard puzzles require backtracking. Peter Norvig's famous Python solver combines propagation + search.

**Q4: "What is the minimum number of clues required for a unique Sudoku solution?"**
> 17 — proven in 2012. Any Sudoku with 16 or fewer clues either has no solution or multiple solutions. This is a theorem, not a heuristic.

**Q5: "Extend to a 16×16 Sudoku (hexadoku) — what changes?"**
> Grid is 16×16 with 4×4 boxes; digits are 0–15 (or 1–16). Array sizes change from `[9][9]` to `[16][16]`. Box index: `(row/4)*4 + (col/4)`. The algorithm structure is identical.

---

## 23. How eBay Frames OA Problems

*Answers the question: "Does eBay embed algorithms in long scenarios where you need to decode a hidden pattern?"*

**Short answer: partially — but not the way you fear.**

The user concern is understandable: eBay does wrap problems in business language. But the assumption that the algorithm is *buried* in a long narrative is incorrect. Here is what the research actually shows:

| Format | Where used | Story thickness | The real challenge |
|---|---|---|---|
| ICA (Incremental Coding Assessment) | CodeSignal OA | Thick — banking system, one class, 4 levels | Rule comprehension + edge-case handling |
| Standalone algorithm | CodeSignal OA + Onsite | Thin — 2–3 sentences, then examples | Reading examples carefully to extract the exact rule |

**The ICA format** (covered in `ebay-mts1-business-patterns.md`) builds a coherent stateful class over 4 levels. The story is real, but the algorithm is named by the examples — not hidden in the prose.

**The 4 standalone problems below** (Sections 24–27) each have a business wrapper, but the examples spell out the exact rule precisely. No decoding required. The wrapper just names the entities.

**What eBay is NOT:**
- Not a "10-paragraph story with hidden DP" that you must infer from terminology
- Not a company where you need to reverse-engineer business logic to find the algorithm
- The examples always specify the rule; prose names the context

**The one skill you need:** Story-Stripping — peel the business language off to find the data operation underneath.

### 🎨 Visual — Story-Strip Framework

```
eBay OA Problem
      │
      ▼
┌──────────────────────────────────────────────────────┐
│  Business Narrative  (sets the scene — not the rule) │
│  "A building site receives operations..."            │
└──────────────────────────────────────────────────────┘
      │  Strip nouns → entities, verbs → operations
      ▼
┌──────────────────────────────────────────────────────┐
│  Examples  ← SOURCE OF TRUTH for the exact rule      │
│  op=[2,5,2]: does block at 5 with size 2 fit? → '1' │
└──────────────────────────────────────────────────────┘
      │  Identify pattern
      ▼
┌─────────────────────────────────────────────────────────────────┐
│  "Any element in range [lo, hi]?"     → TreeSet + floor()       │
│  "Schedule after current last finish" → Deque of finish times   │
│  "Best run of same element"           → Linear scan, >= update  │
│  "Exactly 2 of 3 match"              → anyPair && !allThree     │
└─────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   The examples are the problem spec — not the narrative.
   If the rule feels ambiguous after reading prose, look at the examples again.
   The story is never the source of truth; the input-output pairs are.
```

**Worked story-strip for Sections 24–27:**

| Section | Business wrapper | Stripped algorithm |
|---|---|---|
| 24 | "Can a block be placed at position x?" | Range query: any element in [x-size, x-1]? → TreeSet + floor() |
| 25 | "Customer leaves if too many waiting" | Queue simulation with capacity check → Deque of finish times |
| 26 | "Best run of same item, rightmost on tie" | Max-length run scan with >= update → linear scan |
| 27 | "Exactly 2 of 3 elements match" | Fixed window, formula: anyPair && !allThree |

---

## 24. Building Obstacles and Blocks — OA

**Difficulty:** Medium | **Pattern:** TreeSet Range Query — `floor()` to check element presence in a range
**Seen in:** Real eBay CodeSignal OA (2025, this candidate) ⚠️ Custom (reconstructed from memory)

> **eBay framing used:** *"A construction site receives operations. Type 1 places an obstacle at position x. Type 2 asks: can a block of size `size` be placed with its right edge at position x (occupying [x-size, x-1]) without hitting any obstacle?"*

---

### 🎯 Problem Statement

Given `operations[][]` where:
- `[1, x]` → add an obstacle at position `x`
- `[2, x, size]` → query: does the block occupying `[x-size, x-1]` avoid all obstacles?

Return a string of '1' (fits) and '0' (blocked) for each Type-2 query, in order.

```
operations = [[1,2],[1,5],[2,5,2],[2,6,3],[2,2,1],[2,3,2]]

After Type-1 ops, obstacles = {2, 5}

[2,5,2]:  block spans [3, 4]  → obstacle? floor(4)=2, 2 >= 3? NO  → '1'
[2,6,3]:  block spans [3, 5]  → obstacle? floor(5)=5, 5 >= 3? YES → '0'
[2,2,1]:  block spans [1, 1]  → obstacle? floor(1)=null           → '1'
[2,3,2]:  block spans [1, 2]  → obstacle? floor(2)=2, 2 >= 1? YES → '0'

Output: "1010"
```

---

### 🧠 Discussion

**The core operation:** for query (x, size), check if any obstacle exists in `[x-size, x-1]`.

**Why TreeSet and not HashMap / array?**

| Structure | Insert | Range-presence check | Verdict |
|---|---|---|---|
| `boolean[]` / array | O(1) | O(size) scan | Too slow if size is large |
| `HashSet<Integer>` | O(1) | O(size) — must check each position | Same problem |
| `TreeSet<Integer>` | O(log n) | O(log n) via `floor()` | ✅ One call, no scan |

**The `floor()` trick:** `floor(rangeEnd)` returns the largest element ≤ rangeEnd. If that element is also ≥ rangeStart, it lies inside the range — obstacle found. One O(log n) call replaces an O(size) loop.

**When would you choose differently?**
- `ConcurrentSkipListSet` instead of `TreeSet`: if multi-threaded access (ask the interviewer)
- Segment tree / BIT: if you also need count-of-obstacles-in-range, not just presence — overkill here

---

### 🎨 Visual — floor() Range Check

```
Obstacles in TreeSet:   2 . . . 5
                        ↑       ↑

Query [2,6,3] → block range [3, 5]:
   rangeEnd   = 5
   floor(5)   = 5   ←── largest obstacle ≤ rangeEnd

   5 ≥ rangeStart(3)?  YES → obstacle inside range → '0'

Query [2,5,2] → block range [3, 4]:
   rangeEnd   = 4
   floor(4)   = 2   ←── largest obstacle ≤ rangeEnd

   2 ≥ rangeStart(3)?  NO → no obstacle in range → '1'

Query [2,2,1] → block range [1, 1]:
   rangeEnd   = 1
   floor(1)   = null ←── no obstacle ≤ 1 exists

   null → no obstacle in range → '1'

KEY INVARIANT:
   floor(rangeEnd) = closest obstacle at or to the left of the range's right edge.
   If it also falls at or to the right of the range's left edge → obstacle in range.
   One O(log n) call replaces an O(size) scan.
```

---

### 🚀 Optimal Solution

**Steps in plain English:**

1. **Maintain a sorted set** of obstacle positions — `TreeSet<Integer>` for O(log n) insert and `floor()`.
2. **For each operation:** if type 1, add position to the TreeSet; if type 2, compute the block's span.
3. **Check range presence:** call `floor(rangeEnd)`. If the result is non-null and ≥ rangeStart, an obstacle is in range — append '0'; else '1'.

```java
static String buildingObstacles(int[][] operations) {
    // Step 1 — sorted set for O(log n) insert and range presence check
    TreeSet<Integer> obstacles = new TreeSet<>();
    StringBuilder result = new StringBuilder();
    for (int[] op : operations) {
        if (op[0] == 1) {
            // Step 2 — type 1: record obstacle position
            obstacles.add(op[1]);
        } else {
            // Step 2 — type 2: block right edge at x, block spans [x-size, x-1]
            int x = op[1];
            int size = op[2];
            int rangeStart = x - size;
            int rangeEnd = x - 1;
            // Step 3 — largest obstacle at or before rangeEnd
            Integer floor = obstacles.floor(rangeEnd);
            if (floor != null && floor >= rangeStart) {
                // obstacle sits inside [rangeStart, rangeEnd] — blocked
                result.append('0');
            } else {
                result.append('1');
            }
        }
    }
    return result.toString();
}
```

**Complexity:**

| | Time | Space |
|---|---|---|
| Per type-1 op | O(log n) | — |
| Per type-2 op | O(log n) | — |
| Overall | O(q log n) — q ops, n obstacles | O(n) |

---

### 🧩 Follow-Up Questions

**Q1: "What if we also need a count of obstacles inside the range, not just presence?"**
> `TreeSet.subSet(rangeStart, true, rangeEnd, true).size()` — O(k) where k = elements in range. For presence alone, `floor()` is faster.

**Q2: "What if obstacle positions can be negative?"**
> TreeSet handles all valid `int` values including negatives. No change needed.

**Q3: "What if we also need to remove obstacles?"**
> `obstacles.remove(x)` — O(log n). TreeSet supports arbitrary removal.

**Q4: "Can we make type-2 queries O(1) for very frequent checks?"**
> Segment tree or BIT over a compressed coordinate space gives O(log C) amortized. Overkill for OA constraints.

**Q5: "What if blocks can also be placed vertically — 2D version?"**
> Model as a 2D boolean grid and use a 2D prefix sum for O(1) rectangle-sum queries after O(n²) preprocessing.

---

## 25. Sequential ID Verification Event Queue — OA

**Difficulty:** Medium | **Pattern:** Deque Simulation — track finish times, expire stale entries, check capacity
**Seen in:** Real eBay CodeSignal OA (2025, this candidate) ⚠️ Custom (reconstructed from memory)

> **eBay framing used:** *"Customers arrive one at a time to have their ID verified. Each check takes `checkDuration` seconds. At most `maxWaiting` customers may wait at once (not counting the one being checked). If a customer arrives when `maxWaiting` are already waiting, they leave immediately. Return each customer's finish time (or arrival time if they left)."*

---

### 🎯 Problem Statement

Given:
- `times[]` — arrival time of each customer (strictly increasing)
- `checkDuration` — seconds one verification takes
- `maxWaiting` — max customers allowed to wait (not counting the one being served)

Return `result[]` where:
- Customer joins queue → `result[i]` = their scheduled finish time
- Customer leaves immediately → `result[i]` = their arrival time `times[i]`

```
times=[4, 400, 450, 500], checkDuration=300, maxWaiting=1000

@t=4:   queue empty → start=4,    finish=304   → result[0]=304
@t=400: 304≤400, queue empty → start=400, finish=700 → result[1]=700
@t=450: queue=[700], 1 in system, waiting=0 → start=700, finish=1000 → result[2]=1000
@t=500: queue=[700,1000], waiting=1 ≤ 1000 → start=1000, finish=1300 → result[3]=1300

Output: [304, 700, 1000, 1300]
```

---

### 🧠 Discussion

**Key invariant:** the Deque stores finish times of ALL scheduled people (being served + waiting). At arrival time T:
- Expire all finish times ≤ T (those people are done — slide them out from the front)
- `waiting = Deque.size() == 0 ? 0 : Deque.size() - 1` (one person is being served; everyone else waits)
- If `waiting > maxWaiting` → person leaves
- Otherwise → chain off the last finish time: `start = peekLast(); finish = start + checkDuration`

**Boundary — `>` not `>=`:** if `maxWaiting=1`, then `waiting=1` is allowed (exactly at the limit). The person leaves only when `waiting > 1`. This means the queue can hold up to `1 (served) + maxWaiting (waiting)` = `maxWaiting + 1` total people at any moment.

**Why Deque and not a simple counter?**
A counter alone can't tell you when the last person finishes — needed to chain the new finish time. The Deque's `peekLast()` gives the correct start for the next person in O(1).

**When would you choose differently?**
- `PriorityQueue<Long>`: needed when there are multiple verification booths (K servers problem) — poll the booth with the earliest finish time
- Simple `int counter + long lastFinish`: works if you don't need to expire by arrival time; the Deque generalizes this cleanly

---

### 🎨 Visual — Deque State Animation

```
checkDuration=300, maxWaiting=1

t=4,   Deque=[]
  expire ≤ 4:  nothing
  size=0 → waiting=0, 0 > 1? NO → join
  start = t = 4,  finish = 304
  Deque=[304]   result[0]=304

t=400, Deque=[304]
  expire ≤ 400: 304 ≤ 400 → poll  →  Deque=[]
  size=0 → waiting=0, 0 > 1? NO → join
  start = 400,  finish = 700
  Deque=[700]   result[1]=700

t=450, Deque=[700]
  expire ≤ 450: 700 > 450 → stop
  size=1 → waiting = 1-1 = 0, 0 > 1? NO → join
  start = peekLast = 700,  finish = 1000
  Deque=[700,1000]   result[2]=1000

t=500, Deque=[700,1000]
  expire ≤ 500: 700 > 500 → stop
  size=2 → waiting = 2-1 = 1, 1 > 1? NO → join
  start = peekLast = 1000,  finish = 1300
  Deque=[700,1000,1300]   result[3]=1300

Hypothetical arrival at t=501, maxWaiting=1:
  Deque=[700,1000,1300]
  expire ≤ 501: 700 > 501 → stop
  size=3 → waiting = 3-1 = 2,  2 > 1? YES → leave
  result = 501 (arrival time, not finish time)

KEY INVARIANT:
   Deque = finish times of all scheduled people (served + waiting).
   size - 1 = number waiting (one is always being served when size > 0).
   New person's start = peekLast() (chain; no idle gaps in service).
   Expire front entries ≤ T on every arrival to keep size accurate.
```

---

### 🚀 Optimal Solution

**Steps in plain English:**

1. **Maintain a Deque of finish times** for all scheduled people.
2. **On each arrival at time T:** expire all entries at the front with finishTime ≤ T.
3. **Count waiting:** if Deque is empty → 0; else `Deque.size() - 1`.
4. **Capacity check:** if `waiting > maxWaiting` → result = arrival time T (person leaves).
5. **Otherwise schedule:** start = `peekLast()` (or T if Deque empty); finish = start + checkDuration; add finish to back; result = finish.

```java
static long[] idVerificationQueue(int[] times, int checkDuration, int maxWaiting) {
    long[] result = new long[times.length];
    // Step 1 — Deque holds finish times of all scheduled people (served + waiting)
    Deque<Long> finishTimes = new ArrayDeque<>();
    for (int i = 0; i < times.length; i++) {
        long t = times[i];
        // Step 2 — expire all finish times ≤ t (those people are done)
        while (!finishTimes.isEmpty() && finishTimes.peekFirst() <= t) {
            finishTimes.pollFirst();
        }
        // Step 3 — waiting = total in system minus the one being served
        int waiting = finishTimes.isEmpty() ? 0 : finishTimes.size() - 1;
        if (waiting > maxWaiting) {
            // Step 4 — capacity exceeded: leave immediately
            result[i] = t;
        } else {
            // Step 5 — chain off the last scheduled finish time
            long startTime = finishTimes.isEmpty() ? t : finishTimes.peekLast();
            long finishTime = startTime + checkDuration;
            finishTimes.addLast(finishTime);
            result[i] = finishTime;
        }
    }
    return result;
}
```

**Complexity:**

| | Time | Space |
|---|---|---|
| Expire loop (total) | O(n) amortized — each entry added and removed once | — |
| Per customer | O(1) amortized | — |
| Overall | O(n) | O(maxWaiting + 1) |

---

### 🧩 Follow-Up Questions

**Q1: "What if customers can arrive simultaneously (not strictly increasing)?"**
> Sort by arrival time first. Simultaneous arrivals are processed in input order; the Deque logic is otherwise unchanged.

**Q2: "What if each customer has a different check duration?"**
> `finishTime = startTime + customer.checkDuration` — each person contributes their own duration. No structural change.

**Q3: "What if maxWaiting is 0?"**
> `waiting=0` is never > 0 when Deque is empty (first person always gets in). Second person: size=1, waiting=0 — still not > 0. Third: size=2, waiting=1 > 0 → leaves. So maxWaiting=0 means at most 2 people in the system at once (1 served + 1 waiting? No — wait, 0 waiting allowed means only 1 total). Let me recheck: size=1 → waiting=0, 0>0 is false → allowed → size=2. size=2 → waiting=1, 1>0 → leaves. So maxWaiting=0 allows exactly 1 waiting person? No — size=2 means 1 served + 1 waiting. The third person sees waiting=1>0 and leaves. So maxWaiting=0 means the queue can hold at most 2 (1 served + 1 waiting... contradicts the spec). Re-read: if `maxWaiting=0`, the first person arrives, Deque empty, waiting=0, 0>0? No → joins. Second arrives, Deque=[finish1], waiting=0, 0>0? No → joins. Third: Deque=[finish1, finish2], waiting=1, 1>0 → leaves. So maxWaiting=0 allows 0 waiting meaning the served person is not counted as waiting. The Deque can grow to size 1 (served only) + 0 (waiting) = 1... but the code allows 2. This is a boundary edge case — clarify with interviewer whether "0 waiting" means the queue capacity is 1 person total.

**Q4: "How would you handle cancellations?"**
> The Deque only stores finish times, not identities. To support cancellation, store `(id, finishTime)` pairs and rebuild the timeline on cancellation. O(n) — non-trivial.

**Q5: "Extend to K parallel verification booths."**
> Replace Deque with `PriorityQueue<Long>` of booth finish times (size K). On arrival at T: poll minimum booth finishTime. If ≤ T, booth free immediately; else chain off that booth. O(log K) per customer. This is the classic K-server scheduling problem.

---

## 26. Longest Contiguous Substring — OA

**Difficulty:** Easy | **Pattern:** Linear scan — two-pointer run detection with `>=` for rightmost tie-break
**Seen in:** Real eBay CodeSignal OA (2025, this candidate) ⚠️ Custom (reconstructed from memory)

> **eBay framing used:** *"Given a source string, find the longest contiguous run of the same character. If two or more runs tie for maximum length, return the rightmost one. Return the character followed by its count as a string."*

---

### 🎯 Problem Statement

Given `source`, return `"<char><count>"` — the character and length of the longest contiguous run. On a tie in length, return the rightmost run.

```
source = "bbaaccdbbaaa"

Runs:  'b'×2 | 'a'×2 | 'c'×2 | 'd'×1 | 'b'×2 | 'a'×3
                                                   ↑ longest

Output: "a3"
```

```
source = "aaabbb"

Runs:  'a'×3 | 'b'×3   ← tied; rightmost = 'b'

Output: "b3"
```

---

### 🧠 Discussion

**The critical implementation detail:** use `>=` (not `>`) when comparing run lengths.

- With `>`: the first run of maximum length is kept (leftmost wins on tie)
- With `>=`: every run of equal-or-greater length overwrites the previous best, so the last run of maximum length wins (rightmost wins on tie)

Since we scan left to right, the `>=` version naturally propagates the best result forward to the rightmost tied run. No extra pass or list needed.

**No data structure beyond two variables** (`bestChar`, `bestLen`) — single pass, O(n) time, O(1) space.

---

### 🎨 Visual — Scan with Tie-Break

```
source = "aaabbb"
          0 1 2 3 4 5

Outer i=0, char='a':
  Inner j: 0→1→2→3 (stops at 'b')
  len = 3 - 0 = 3
  3 >= bestLen(0)?  YES  →  bestChar='a', bestLen=3

Outer i=3, char='b':
  Inner j: 3→4→5→6 (stops at end)
  len = 6 - 3 = 3
  3 >= bestLen(3)?  YES (>= not >)  →  bestChar='b', bestLen=3
          ↑
    rightmost tied run overwrites

Return "b3"

KEY INVARIANT:
   Update with >= so that the LAST run of maximum length wins.
   If you use >, you keep the FIRST run (leftmost). The tie-break rule
   is controlled entirely by this one character in the comparison.
```

---

### 🚀 Optimal Solution

**Steps in plain English:**

1. **Initialize** `bestChar` to the first character, `bestLen` to 0.
2. **Outer loop** with pointer `i`: start of the current run.
3. **Inner loop** with pointer `j`: extend right while `source[j] == source[i]`.
4. **Run length** = `j - i`. **Update best with `>=`:** rightmost tied run wins.
5. **Advance** `i = j` to jump to the start of the next run.

```java
static String longestContiguous(String source) {
    // Step 1 — initialize best (bestLen=0 ensures first run always updates)
    char bestChar = source.charAt(0);
    int bestLen = 0;
    int i = 0;
    while (i < source.length()) {
        char c = source.charAt(i);
        // Step 3 — extend j to the end of this run
        int j = i;
        while (j < source.length() && source.charAt(j) == c) {
            j++;
        }
        // Step 4 — run length; >= keeps the rightmost tied run (not > which keeps leftmost)
        int len = j - i;
        if (len >= bestLen) {
            bestLen = len;
            bestChar = c;
        }
        // Step 5 — jump to start of next run
        i = j;
    }
    return "" + bestChar + bestLen;
}
```

**Complexity:**

| | Time | Space |
|---|---|---|
| Scan | O(n) — each character visited once by `j` | O(1) |

---

### 🧩 Follow-Up Questions

**Q1: "What if the tie-break is leftmost instead of rightmost?"**
> Change `>=` to `>`. The first run of maximum length is kept.

**Q2: "Return all tied runs, not just one."**
> Collect all `(char, length)` pairs in a list. After scan, find max length. Filter and return all runs of that length.

**Q3: "Only consider runs of a specific character."**
> Add `if (c == targetChar)` before the update block.

**Q4: "What if source can be empty?"**
> Guard: `if (source == null || source.isEmpty()) return "";` at the top.

**Q5: "Extend to Unicode strings with multi-byte characters."**
> Iterate by code point: `int[] codePoints = source.codePoints().toArray();`. Replace `charAt` with `codePoints[i]`. The rest of the logic is identical.

---

## 27. Counting Good Tuples — OA

**Difficulty:** Easy | **Pattern:** Fixed window size 3 — boolean formula for "exactly 2 of 3 equal"
**Seen in:** Real eBay CodeSignal OA (2025, this candidate) ⚠️ Custom (reconstructed from memory)

> **eBay framing used:** *"Given an array, count the number of 'good tuples' — contiguous windows of 3 elements where exactly 2 of the 3 are equal (a partial match: not all different, not all the same)."*

---

### 🎯 Problem Statement

Given `a[]`, count contiguous windows of size 3 where exactly 2 of the 3 values are equal.

Window `(x, y, z)` is **good** if: at least one pair is equal, but NOT all three are equal.

```
a = [1, 1, 2, 1, 5, 3, 2, 3]

Window (1,1,2): 1==1 ✅, not all equal → GOOD   count=1
Window (1,2,1): 1==1 (x==z) ✅, not all equal → GOOD   count=2
Window (2,1,5): no pairs → not good
Window (1,5,3): no pairs → not good
Window (5,3,2): no pairs → not good
Window (3,2,3): 3==3 (x==z) ✅, not all equal → GOOD   count=3

Output: 3
```

---

### 🧠 Discussion

**The formula:** `anyPair && !allThree`

- `anyPair` = `x==y || y==z || x==z` — at least one of the three pairs matches
- `allThree` = `x==y && y==z` — transitively: if first and second equal AND second and third equal, all three must be equal

This covers all 5 distinct pattern types correctly:

| Pattern example | anyPair | allThree | Good? |
|---|---|---|---|
| (1,1,2) — first two match | true | false | ✅ |
| (1,2,1) — first and last | true | false | ✅ |
| (2,1,1) — last two match | true | false | ✅ |
| (1,1,1) — all three match | true | true | ❌ |
| (1,2,3) — none match | false | false | ❌ |

No HashMap, no sorting — two boolean expressions per window, O(1) per step.

---

### 🎨 Visual — Sliding Window Walk

```
a = [1,  1,  2,  1,  5,  3,  2,  3]
     i=0  i=1  i=2  i=3  i=4  i=5

Window at i=0: (1,1,2)  anyPair=T allThree=F → count=1
Window at i=1: (1,2,1)  anyPair=T allThree=F → count=2
Window at i=2: (2,1,5)  anyPair=F            → skip
Window at i=3: (1,5,3)  anyPair=F            → skip
Window at i=4: (5,3,2)  anyPair=F            → skip
Window at i=5: (3,2,3)  anyPair=T allThree=F → count=3

Output: 3

Pattern check for (3,2,3):
  x=3, y=2, z=3
  x==y → 3==2? NO
  y==z → 2==3? NO
  x==z → 3==3? YES  →  anyPair=true
  x==y && y==z → false  →  allThree=false
  anyPair && !allThree → true  →  GOOD ✅

KEY INVARIANT:
   "Exactly 2 equal" = anyPair is true AND allThree is false.
   allThree captures the (1,1,1) case that anyPair alone would accept.
   Loop bound: i <= a.length - 3 (inclusive), not i < a.length - 3.
```

---

### 🚀 Optimal Solution

**Steps in plain English:**

1. **Loop i from 0 to `n-3` (inclusive):** each step examines window `(a[i], a[i+1], a[i+2])`.
2. **Compute anyPair:** true if any of the three pairwise comparisons is equal.
3. **Compute allThree:** true if `x==y && y==z` (all three equal by transitivity).
4. **Increment count if** `anyPair && !allThree`.

```java
static int countGoodTuples(int[] a) {
    int count = 0;
    // Step 1 — slide a fixed window of size 3 across the array
    for (int i = 0; i <= a.length - 3; i++) {
        int x = a[i], y = a[i + 1], z = a[i + 2];
        // Step 2 — at least one of the three pairs is equal
        boolean anyPair = (x == y) || (y == z) || (x == z);
        // Step 3 — all three are equal (transitivity: x==y and y==z implies x==z)
        boolean allThree = (x == y && y == z);
        // Step 4 — good tuple: a partial match (not all-same, not all-different)
        if (anyPair && !allThree) {
            count++;
        }
    }
    return count;
}
```

**Complexity:**

| | Time | Space |
|---|---|---|
| Single pass | O(n) | O(1) |

---

### 🧩 Follow-Up Questions

**Q1: "Extend to windows of size k — count tuples with exactly 2 equal values."**
> For each window of size k, count element frequencies with a HashMap. "Exactly 2 equal" means exactly one element has frequency 2, all others frequency 1. Sliding frequency update: O(n) total. O(k) space.

**Q2: "Count tuples where at least 2 values are equal (including all-equal)."**
> Drop `&& !allThree` — just use `anyPair`.

**Q3: "Count tuples where all 3 are different."**
> `!anyPair` — equivalent to `x!=y && y!=z && x!=z`.

**Q4: "Count good tuples in a circular array (wraparound)."**
> Same loop but with `a[(i+1) % n]` and `a[(i+2) % n]` — run for all `n` windows.

**Q5: "What if values are floating-point?"**
> Replace `x == y` with `Double.compare(x, y) == 0` or `Math.abs(x - y) < 1e-9`. Do NOT use `==` on `double` values.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | File created. 6 high-frequency confirmed eBay MTS 1 DSA problems: Delete Nth Node (LC 19), HTML/XML N-ary Tree Parser (Custom), Balanced Sum Subarray (Custom), Binary Tree Subtree Counting (Custom), Weighted Grouping OOP (Custom), Number of Islands (condensed + cross-link), Reverse Pairs (LC 493). Custom problems have explicit assumption statements and hand-traced examples. |
| Jul 11, 2026 | Extended with medium-frequency section: `ls -r` (Custom, with JUnit tests — matches reported ask), Sieve of Eratosthenes (Director round confirmed), and compact 🧩 company-tag reference table (9 patterns, LRU Cache full quick reference). Tier legend updated to add 🧩. |
| Jul 11, 2026 | Added J/K rows to compact table: Move Zeroes (LC 283, two-pointer in-place) and Best Time to Buy & Sell Stock II (LC 122, greedy). Two-line insight block updated. Full 🧩 seen-once list now complete. |
| Jul 11, 2026 | **Major expansion — all 12 seen-once problems upgraded to full treatment (Sections 11–22).** Each section now matches Problems 1–9 structure: Problem Statement, Discussion, Brute Force, Optimisation Idea, ASCII Visual with KEY INVARIANT, Optimal Java Solution with plain-English steps, Complexity table, 5 Follow-Up questions. Section 10 converted to a quick-reference index linking to Sections 11–22. ToC updated to list all 22 sections. |
| Jul 11, 2026 | **Added Sections 23–27: OA problems + scenario-framing guide.** Section 23 answers "does eBay hide algorithms in long narratives?" — short answer: thin cover, not a puzzle; examples are the spec. Sections 24–27 are the 4 standalone CodeSignal OA problems this candidate received (2025): Building Obstacles (TreeSet floor() range query), ID Verification Queue (Deque finish-time simulation), Longest Contiguous Substring (linear scan with >= for rightmost tie-break), Counting Good Tuples (fixed window 3, anyPair && !allThree). All solutions verified against exact expected outputs. Tier legend updated to mark OA round problems. |
| Jul 11, 2026 | **Follow-up sections upgraded across all problems — "how to think + code" for every major variant.** Previously follow-ups were one-liner text. Added full treatment (insight shift + working code) for: §12 all 4 variants (LC 121/123/714/309 — the entire stock problems family); §2 XML serialize; §4 iterative DFS for deep trees; §9 segmented sieve for large N; §13 insert interval (LC 57) + meeting rooms (LC 253); §14 4Sum (LC 18) + K-Sum recursive generalization; §15 LFU cache (LC 460) full implementation; §17 Boyer-Moore voting (LC 169); §18 Course Schedule II — Kahn's BFS + DFS extension; §19 bidirectional BFS; §21 N-Queens bitmask optimization. |
| Jul 11, 2026 | **§17 (LC 347) — min-heap approach added as full second solution.** New `### 🔧 Min-Heap Approach` section with ASCII visual, steps, and complete code (O(n log k), O(k) extra space). Q1 updated from one-liner to space-comparison analysis. Q2 (Top K frequent words LC 692) upgraded with full heap implementation including tie-breaking comparator `b.compareTo(a)` for alphabetical ordering — hand-traced example verifying `["the","day","an"]` output. Q4 (data stream) upgraded with full `TopKFreqStream` class using `TreeMap<Integer, Set<Integer>>` frequency buckets — O(log F) add, O(k) getTopK — plus explanation of why static heap breaks for streams (stale entries) and lazy deletion trade-off. |
